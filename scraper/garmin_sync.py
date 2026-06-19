"""Pull running & swimming (and other) activities from Garmin Connect.

Uses the ``garminconnect`` library. Authentication relies on a previously
cached OAuth token (created once by ``bootstrap_login.py``) so this runs
headless in CI. The token auto-refreshes, so a full re-login with the
password + MFA is only needed if the refresh token is ever revoked.
"""
from __future__ import annotations

import datetime as dt
import os
from typing import List, Optional

from garminconnect import Garmin


def _tokenstore() -> str:
    return os.path.expanduser(os.getenv("GARMIN_TOKENSTORE", "~/.garminconnect"))


def login() -> Garmin:
    """Resume a Garmin session from a cached token (no password needed).

    In CI, ``GARMIN_TOKENS`` holds the serialised token string (login() loads it
    directly). Locally, it falls back to the token directory written by
    ``bootstrap_login.py``.
    """
    garmin = Garmin()
    garmin.login(os.getenv("GARMIN_TOKENS") or _tokenstore())
    return garmin


def map_sport(type_key: Optional[str]) -> str:
    """Map a Garmin activityType.typeKey to our coarse sport buckets."""
    key = (type_key or "").lower()
    if "swim" in key:
        return "swim"
    if "run" in key:
        return "run"
    return "other"


def _iso(value: Optional[str]) -> Optional[str]:
    """Garmin returns 'YYYY-MM-DD HH:MM:SS' in GMT; normalise to ISO-8601 UTC."""
    if not value:
        return None
    return value.replace(" ", "T") + "Z"


def _as_int(value) -> Optional[int]:
    """Garmin sends some integer-ish fields (e.g. avg HR) as floats like 137.0."""
    return int(round(value)) if isinstance(value, (int, float)) else None


def _to_row(activity: dict) -> dict:
    type_key = (activity.get("activityType") or {}).get("typeKey")
    return {
        "garmin_id": activity["activityId"],
        "sport": map_sport(type_key),
        "garmin_type": type_key,
        "name": activity.get("activityName"),
        "start_time": _iso(activity.get("startTimeGMT")),
        "distance_m": activity.get("distance"),
        "duration_s": activity.get("duration"),
        "avg_speed_mps": activity.get("averageSpeed"),
        "avg_hr": _as_int(activity.get("averageHR")),
        "calories": activity.get("calories"),
        "elevation_gain_m": activity.get("elevationGain"),
        "raw": activity,
    }


def fetch_activities(garmin: Garmin, start: dt.date, end: dt.date) -> List[dict]:
    """Fetch and normalise activities between two dates (inclusive)."""
    raw = garmin.get_activities_by_date(start.isoformat(), end.isoformat())
    return [_to_row(a) for a in raw]


def sync(garmin: Optional[Garmin] = None) -> List[dict]:
    """Return normalised rows for the current year up to today.

    Fetching the whole year each run keeps year-to-date stats correct and is
    idempotent because rows are upserted on ``garmin_id``. Override the start
    with the ``SYNC_START_DATE`` env var (YYYY-MM-DD) if you want more history.
    """
    garmin = garmin or login()
    today = dt.date.today()
    start_env = os.getenv("SYNC_START_DATE")
    start = dt.date.fromisoformat(start_env) if start_env else dt.date(today.year, 1, 1)
    return fetch_activities(garmin, start, today)
