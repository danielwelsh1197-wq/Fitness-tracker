"""One-time backfill: tag each run with a neighbourhood AND its route polyline.

Garmin runs carry coordinates; Strava-export runs don't, so their GPS data is
read from the per-run file in the export's `activities/` folder (`.fit.gz` or
`.gpx`). From each track we derive the start point (reverse-geocoded to a
neighbourhood, cached) and a downsampled encoded polyline for the route map.

Requires `fitparse` for the .fit.gz files:  pip install fitparse

Usage (from the repo root, with scraper/.env populated):
    python tools/backfill_locations.py "~/Downloads/Strava export 16-6-26"
    python tools/backfill_locations.py <export> --all   # re-process every run
"""
from __future__ import annotations

import argparse
import collections
import gzip
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv  # noqa: E402

from scraper import db, geocode, polyline  # noqa: E402

_SEMICIRCLE = 180.0 / 2 ** 31
_TRKPT_RE = re.compile(r'<trkpt\b[^>]*?\blat="([-0-9.]+)"[^>]*?\blon="([-0-9.]+)"', re.I)
_TRKPT_RE_REV = re.compile(r'<trkpt\b[^>]*?\blon="([-0-9.]+)"[^>]*?\blat="([-0-9.]+)"', re.I)


def _load_env() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    load_dotenv(os.path.join(here, "..", "scraper", ".env"))
    load_dotenv()


def _track_from_gpx(path: str):
    text = open(path, encoding="utf-8", errors="ignore").read()
    pts = [(float(a), float(b)) for a, b in _TRKPT_RE.findall(text)]
    if not pts:
        pts = [(float(b), float(a)) for a, b in _TRKPT_RE_REV.findall(text)]
    return pts


def _track_from_fit_gz(path: str):
    from fitparse import FitFile

    with gzip.open(path, "rb") as fh:
        fit = FitFile(fh.read())
    pts = []
    for record in fit.get_messages("record"):
        values = {f.name: f.value for f in record.fields}
        lat, lng = values.get("position_lat"), values.get("position_long")
        if lat is not None and lng is not None:
            pts.append((lat * _SEMICIRCLE, lng * _SEMICIRCLE))
    return pts


def _track_for(row: dict, export_dir: str):
    """Full GPS track for a row's run, from its Strava export file (else [])."""
    filename = (row.get("raw") or {}).get("Filename")
    if not filename:
        return []
    path = os.path.join(export_dir, filename)
    if not os.path.isfile(path):
        return []
    try:
        return _track_from_gpx(path) if path.endswith(".gpx") else _track_from_fit_gz(path)
    except Exception as exc:  # noqa: BLE001
        print(f"  ! couldn't read {filename}: {exc}")
        return []


def _start_for(row: dict, track: list):
    """Start coordinate: existing/Garmin coords, else the first track point."""
    if row.get("start_lat") is not None and row.get("start_lng") is not None:
        return row["start_lat"], row["start_lng"]
    raw = row.get("raw") or {}
    if raw.get("startLatitude") is not None:
        return raw["startLatitude"], raw["startLongitude"]
    return track[0] if track else None


def main(argv) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", help="Path to the Strava export folder")
    parser.add_argument("--all", action="store_true", help="Re-process every run")
    args = parser.parse_args(argv)
    export_dir = os.path.expanduser(args.export)

    _load_env()
    client = db.get_client()
    rows = client.table("activities").select(
        "activity_id,source,location_name,start_lat,start_lng,polyline,raw"
    ).eq("sport", "run").execute().data
    geocode.seed_cache(db.known_locations(client))

    todo = rows if args.all else [r for r in rows if not r.get("location_name") or not r.get("polyline")]
    print(f"{len(rows)} runs total; processing {len(todo)}...")

    located = routed = 0
    areas: collections.Counter = collections.Counter()
    for row in todo:
        track = _track_for(row, export_dir)
        start = _start_for(row, track)
        update: dict = {}
        if start:
            update["start_lat"], update["start_lng"] = start
        if not row.get("location_name") and start:
            area = geocode.neighbourhood(*start)
            if area:
                update["location_name"] = area
                areas[area] += 1
                located += 1
        if not row.get("polyline"):
            enc = polyline.encode_track(track)
            if enc:
                update["polyline"] = enc
                routed += 1
        if update:
            client.table("activities").update(update).eq("activity_id", row["activity_id"]).execute()

    print(f"\nTagged {located} new areas, stored {routed} new routes.")
    if areas:
        print("\nTop areas:")
        for area, count in areas.most_common(15):
            print(f"  {count:3}  {area}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
