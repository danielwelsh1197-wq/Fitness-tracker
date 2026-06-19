"""Read weightlifting data from a Google Sheet published as CSV.

Set ``SHEET_CSV_URL`` to the sheet's *File → Share → Publish to web → CSV* link.
No Google auth is needed (the sheet is public-via-link). Two column layouts work:

  A) date, entry              entry = "Exercise NxN @ weight" (reuses parser.py)
  B) date, exercise, sets, reps, weight

One lifting session is created per distinct date.
"""
from __future__ import annotations

import csv
import datetime as dt
import io
import os
from typing import Dict, List, Optional, Tuple

import requests

from scraper.parser import parse_line

# Date string formats accepted in the sheet's `date` column (ISO recommended).
_DATE_FORMATS = ("%Y-%m-%d", "%Y/%m/%d", "%m/%d/%Y", "%d/%m/%Y")
# Header names treated as a free-text "Exercise NxN @ weight" column (layout A).
_ENTRY_KEYS = ("entry", "note", "notes", "raw", "workout")

Session = Tuple[str, dict, List[dict]]


def _norm(name: str) -> str:
    return (name or "").strip().lower().replace(" ", "_")


def _parse_date(value: str) -> Optional[str]:
    v = (value or "").strip()
    if not v:
        return None
    for fmt in _DATE_FORMATS:
        try:
            return dt.datetime.strptime(v, fmt).date().isoformat()
        except ValueError:
            continue
    try:  # already ISO-ish, possibly with a time component
        return dt.date.fromisoformat(v[:10]).isoformat()
    except ValueError:
        return None


def _row_to_set(row: Dict[str, str]) -> Optional[dict]:
    """Map one normalised CSV row to a lift-set dict, or None if not a lift."""
    # Layout A: a free-text entry column -> reuse the tested parser.
    for key in _ENTRY_KEYS:
        if row.get(key, "").strip():
            parsed = parse_line(row[key])
            if not parsed:
                return None
            return {
                "exercise": parsed.exercise,
                "sets": parsed.sets,
                "reps": parsed.reps,
                "weight_kg": parsed.weight_kg,
                "raw_line": parsed.raw_line,
            }

    # Layout B: structured columns.
    exercise = row.get("exercise", "").strip()
    if not exercise:
        return None
    try:
        sets = int(float(row.get("sets", "")))
        reps = int(float(row.get("reps", "")))
        weight = float(str(row.get("weight") or row.get("weight_kg") or "").replace(",", "."))
    except (ValueError, TypeError):
        return None
    return {
        "exercise": exercise,
        "sets": sets,
        "reps": reps,
        "weight_kg": round(weight, 2),
        "raw_line": f"{exercise} {sets}x{reps} @ {weight}kg",
    }


def parse_csv(text: str) -> List[Session]:
    """Parse CSV text into ``(session_id, session_row, sets_rows)`` per date."""
    reader = csv.DictReader(io.StringIO(text))
    by_date: Dict[str, List[dict]] = {}
    raw_by_date: Dict[str, List[str]] = {}

    for raw_row in reader:
        row = {_norm(k): (v or "").strip() for k, v in raw_row.items() if k}
        date = _parse_date(row.get("date", ""))
        if not date:
            continue
        lift_set = _row_to_set(row)
        if not lift_set:
            continue
        by_date.setdefault(date, []).append(lift_set)
        raw_by_date.setdefault(date, []).append(lift_set["raw_line"])

    sessions: List[Session] = []
    for date in sorted(by_date):
        session_row = {
            "date": date,
            "title": f"Workout {date}",
            "raw_text": "\n".join(raw_by_date[date]),
        }
        sessions.append((f"sheet-{date}", session_row, by_date[date]))
    return sessions


def collect_sessions() -> List[Session]:
    """Fetch the published CSV and parse it into sessions."""
    url = os.environ["SHEET_CSV_URL"]
    resp = requests.get(url, timeout=30)
    resp.raise_for_status()
    return parse_csv(resp.text)
