"""One-time backfill: tag each run with a neighbourhood from its GPS start point.

Garmin runs already carry coordinates; Strava-export runs don't, so their start
point is read from the per-run GPS file in the export's `activities/` folder
(`.fit.gz` or `.gpx`). Each start coordinate is reverse-geocoded to a
neighbourhood (cached, so repeated start points cost one lookup).

Requires `fitparse` for the .fit.gz files:  pip install fitparse

Usage (from the repo root, with scraper/.env populated):
    python tools/backfill_locations.py "~/Downloads/Strava export 16-6-26"
    python tools/backfill_locations.py <export> --all   # re-geocode every run
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

from scraper import db, geocode  # noqa: E402

_SEMICIRCLE = 180.0 / 2 ** 31
_GPX_RE = re.compile(r'<trkpt[^>]*\blat="([-0-9.]+)"[^>]*\blon="([-0-9.]+)"', re.I)
_GPX_RE_REV = re.compile(r'<trkpt[^>]*\blon="([-0-9.]+)"[^>]*\blat="([-0-9.]+)"', re.I)


def _load_env() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    load_dotenv(os.path.join(here, "..", "scraper", ".env"))
    load_dotenv()


def _from_gpx(path: str):
    text = open(path, encoding="utf-8", errors="ignore").read()
    m = _GPX_RE.search(text)
    if m:
        return float(m.group(1)), float(m.group(2))
    m = _GPX_RE_REV.search(text)
    return (float(m.group(2)), float(m.group(1))) if m else None


def _from_fit_gz(path: str):
    from fitparse import FitFile

    with gzip.open(path, "rb") as fh:
        fit = FitFile(fh.read())
    for record in fit.get_messages("record"):
        values = {f.name: f.value for f in record.fields}
        lat, lng = values.get("position_lat"), values.get("position_long")
        if lat is not None and lng is not None:
            return lat * _SEMICIRCLE, lng * _SEMICIRCLE
    return None


def _coords_for(row: dict, export_dir: str):
    """Start (lat, lng) for a row: from its own coords, Garmin raw, or GPS file."""
    if row.get("start_lat") is not None and row.get("start_lng") is not None:
        return row["start_lat"], row["start_lng"]
    raw = row.get("raw") or {}
    if raw.get("startLatitude") is not None:
        return raw["startLatitude"], raw["startLongitude"]
    filename = raw.get("Filename")
    if not filename:
        return None
    path = os.path.join(export_dir, filename)
    if not os.path.isfile(path):
        return None
    try:
        return _from_gpx(path) if path.endswith(".gpx") else _from_fit_gz(path)
    except Exception as exc:  # noqa: BLE001
        print(f"  ! couldn't read {filename}: {exc}")
        return None


def main(argv) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", help="Path to the Strava export folder")
    parser.add_argument("--all", action="store_true", help="Re-geocode every run, not just unlocated ones")
    args = parser.parse_args(argv)
    export_dir = os.path.expanduser(args.export)

    _load_env()
    client = db.get_client()
    rows = client.table("activities").select(
        "activity_id,source,location_name,start_lat,start_lng,raw"
    ).eq("sport", "run").execute().data
    geocode.seed_cache(db.known_locations(client))

    todo = rows if args.all else [r for r in rows if not r.get("location_name")]
    print(f"{len(rows)} runs total; locating {len(todo)}...")

    located, no_coords, no_area = 0, 0, 0
    areas: collections.Counter = collections.Counter()
    for row in todo:
        coords = _coords_for(row, export_dir)
        if not coords:
            no_coords += 1
            continue
        lat, lng = coords
        area = geocode.neighbourhood(lat, lng)
        update = {"start_lat": lat, "start_lng": lng}
        if area:
            update["location_name"] = area
            areas[area] += 1
            located += 1
        else:
            no_area += 1
        client.table("activities").update(update).eq("activity_id", row["activity_id"]).execute()

    print(f"\nLocated {located} runs. ({no_coords} had no GPS, {no_area} couldn't be named.)")
    if areas:
        print("\nTop areas:")
        for area, count in areas.most_common(15):
            print(f"  {count:3}  {area}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
