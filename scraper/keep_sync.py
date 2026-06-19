"""Pull weightlifting notes from Google Keep and parse them into sets.

Uses the unofficial ``gkeepapi`` library, authenticating with a master token
(created once via ``bootstrap_login.py``). Only notes carrying the configured
label (default "Workout") are parsed.
"""
from __future__ import annotations

import datetime as dt
import os
from typing import List, Optional, Tuple

import gkeepapi

from scraper.parser import parse_note


def login() -> gkeepapi.Keep:
    """Authenticate to Google Keep with a stored master token."""
    email = os.environ["KEEP_EMAIL"]
    token = os.environ["KEEP_MASTER_TOKEN"]
    keep = gkeepapi.Keep()
    # gkeepapi renamed authenticate() -> resume() across versions; support both.
    if hasattr(keep, "resume"):
        keep.resume(email, token)
    else:  # pragma: no cover - older gkeepapi
        keep.authenticate(email, token)
    keep.sync()
    return keep


def _note_date(note) -> str:
    """Pick a date for the session: edited timestamp, falling back to created."""
    ts = getattr(note, "timestamps", None)
    when = getattr(ts, "edited", None) or getattr(ts, "created", None)
    if isinstance(when, dt.datetime):
        return when.date().isoformat()
    return dt.date.today().isoformat()


def _select_notes(keep: gkeepapi.Keep, label_name: str):
    label = keep.findLabel(label_name)
    if label is not None:
        return list(keep.find(labels=[label]))
    # No such label — fall back to everything (and let the parser filter lines).
    return list(keep.all())


def collect_sessions(keep: Optional[gkeepapi.Keep] = None) -> List[Tuple[str, dict, List[dict]]]:
    """Return ``(note_id, session_row, sets_rows)`` for each note with lift lines."""
    keep = keep or login()
    label_name = os.getenv("KEEP_LABEL", "Workout")
    sessions: List[Tuple[str, dict, List[dict]]] = []

    for note in _select_notes(keep, label_name):
        if getattr(note, "archived", False) or getattr(note, "trashed", False):
            continue
        text = note.text or ""
        exercises = parse_note(text)
        if not exercises:
            continue
        session_row = {
            "date": _note_date(note),
            "title": note.title or "",
            "raw_text": text,
        }
        sets_rows = [
            {
                "exercise": ex.exercise,
                "sets": ex.sets,
                "reps": ex.reps,
                "weight_kg": ex.weight_kg,
                "raw_line": ex.raw_line,
            }
            for ex in exercises
        ]
        sessions.append((note.id, session_row, sets_rows))

    return sessions
