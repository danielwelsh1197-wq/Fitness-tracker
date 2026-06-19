"""Parse a Strava bulk-export ``activities.csv`` into activity rows.

This is the one-time **historical backfill** path: Strava's free data export is
now the only no-cost way to recover full run/swim history (the live API requires
a paid subscription). Going forward, new activities come from Garmin instead.

Quirks of Strava's export handled here:
  - The header has DUPLICATE column names. ``csv.DictReader`` keeps the LAST
    column for a duplicate name, which conveniently gives us Distance in METRES
    (the second 'Distance' column) rather than the first one in km.
  - 'Activity Date' looks like 'Jun 16, 2026, 6:10:37 PM'. It carries no
    timezone, so we treat it as UTC for consistency with Garmin's GMT times.
"""
from __future__ import annotations

import csv
import datetime as dt
from typing import List, Optional

DATE_FMT = "%b %d, %Y, %I:%M:%S %p"  # e.g. 'Jun 16, 2026, 6:10:37 PM'


def map_sport(activity_type: Optional[str]) -> str:
    """Map a Strava 'Activity Type' (Run, TrailRun, Swim, Ride…) to our buckets."""
    key = (activity_type or "").lower()
    if "swim" in key:
        return "swim"
    if "run" in key:
        return "run"
    return "other"


def _f(value) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _as_int(value) -> Optional[int]:
    f = _f(value)
    return int(round(f)) if f is not None else None


def parse_date(value: Optional[str]) -> Optional[str]:
    """'Jun 16, 2026, 6:10:37 PM' -> '2026-06-16T18:10:37Z' (treated as UTC)."""
    if not value:
        return None
    try:
        parsed = dt.datetime.strptime(value.strip(), DATE_FMT)
    except ValueError:
        return None
    return parsed.replace(microsecond=0).isoformat() + "Z"


def to_row(record: dict) -> Optional[dict]:
    """Map one ``activities.csv`` row (a DictReader dict) to an activities row.

    Returns None if the row has no id or no parseable date.
    """
    activity_id = (record.get("Activity ID") or "").strip()
    start = parse_date(record.get("Activity Date"))
    if not activity_id or not start:
        return None
    activity_type = record.get("Activity Type") or None
    return {
        "activity_id": int(activity_id),
        "sport": map_sport(activity_type),
        "activity_type": activity_type,
        "name": record.get("Activity Name") or None,
        "start_time": start,
        "distance_m": _f(record.get("Distance")),          # metres (2nd Distance col)
        "duration_s": _f(record.get("Moving Time")),
        "avg_speed_mps": _f(record.get("Average Speed")),
        "avg_hr": _as_int(record.get("Average Heart Rate")),
        "calories": _f(record.get("Calories")),
        "elevation_gain_m": _f(record.get("Elevation Gain")),
        "source": "strava",
        # Keep only the populated export fields so `raw` stays small but useful.
        "raw": {k: v for k, v in record.items() if v not in (None, "")},
    }


def parse_csv(
    text: str,
    sports=("run", "swim"),
    before: Optional[str] = None,
) -> List[dict]:
    """Parse ``activities.csv`` text into activity rows.

    ``sports``  — keep only these sport buckets (default run + swim); pass None
                  to keep everything.
    ``before``  — an ISO date 'YYYY-MM-DD'; keep only activities strictly before
                  it. Used to stop at the date Garmin takes over, avoiding
                  duplicate rows for the same run.
    """
    rows: List[dict] = []
    for record in csv.DictReader(text.splitlines()):
        row = to_row(record)
        if not row:
            continue
        if sports is not None and row["sport"] not in sports:
            continue
        if before is not None and row["start_time"][:10] >= before:
            continue
        rows.append(row)
    return rows
