"""One-time backfill: load a Strava bulk export's activities into Supabase.

Run once from the repo root, after the Supabase schema exists and
``scraper/.env`` has SUPABASE_URL + SUPABASE_SERVICE_KEY:

    python tools/import_strava_export.py "~/Downloads/Strava export 16-6-26"

By default it imports ALL run/swim activities in the export (the complete
historical record up to the export date). Pair this with the Garmin scraper
starting the day AFTER your latest exported run (set SYNC_START_DATE) so the two
sources never overlap — the importer prints the exact date to use. Limit the
range with --before, or import every sport with --all-sports.

    python tools/import_strava_export.py <export>
    python tools/import_strava_export.py <export> --before 2026-01-01
    python tools/import_strava_export.py <export> --all-sports
"""
from __future__ import annotations

import argparse
import datetime as dt
import os
import sys

# Make `scraper` importable when run as a plain script from the repo root.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv  # noqa: E402

from scraper import db  # noqa: E402
from scraper.strava_export import parse_csv  # noqa: E402

BATCH = 200


def _load_env() -> None:
    """Load scraper/.env explicitly so this works from any cwd."""
    here = os.path.dirname(os.path.abspath(__file__))
    load_dotenv(os.path.join(here, "..", "scraper", ".env"))
    load_dotenv()  # also pick up a repo-root .env / real env vars


def main(argv) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", help="Path to the export folder or activities.csv")
    parser.add_argument(
        "--before",
        default=None,
        help="Import only activities before this YYYY-MM-DD (default: all).",
    )
    parser.add_argument(
        "--all-sports",
        action="store_true",
        help="Import every sport, not just run/swim.",
    )
    args = parser.parse_args(argv)

    path = os.path.expanduser(args.export)
    if os.path.isdir(path):
        path = os.path.join(path, "activities.csv")
    if not os.path.isfile(path):
        sys.exit(f"No activities.csv found at {path}")

    with open(path, encoding="utf-8") as fh:
        rows = parse_csv(
            fh.read(),
            sports=None if args.all_sports else ("run", "swim"),
            before=args.before,
        )
    window = f" before {args.before}" if args.before else ""
    print(f"Parsed {len(rows)} activities{window} from {path}")
    if not rows:
        return 0

    _load_env()
    client = db.get_client()
    written = 0
    for i in range(0, len(rows), BATCH):
        written += db.upsert_activities(client, rows[i : i + BATCH])
    db.log_sync(client, "strava-export", "ok", written)
    print(f"Upserted {written} activities (source=strava).")

    # Tell the user exactly where Garmin should take over so the two sources
    # never overlap (Garmin starts the day after the last imported activity).
    last = max(r["start_time"][:10] for r in rows)
    handover = (dt.date.fromisoformat(last) + dt.timedelta(days=1)).isoformat()
    print(
        f"\nLatest imported activity: {last}."
        f"\n➜ Set SYNC_START_DATE={handover} in scraper/.env and the GitHub secret"
        f"\n  so Garmin only adds runs after the export (no duplicates)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
