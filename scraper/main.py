"""Orchestrate a full sync: Garmin activities + weightlifting -> Supabase.

The weightlifting source is chosen by config: if ``SHEET_CSV_URL`` is set, the
published Google Sheet is used; otherwise it falls back to Google Keep.

Run locally with a populated .env, or in CI with secrets exported as env vars:

    python -m scraper.main

Exits non-zero if any source fails, so a CI run is marked red.
"""
from __future__ import annotations

import os
import sys
import traceback

from dotenv import load_dotenv

from scraper import db, garmin_sync


def run_garmin(client) -> bool:
    try:
        rows = garmin_sync.sync()
        written = db.upsert_activities(client, rows)
        db.log_sync(client, "garmin", "ok", written)
        print(f"[garmin] upserted {written} activities")
        return True
    except Exception as exc:  # noqa: BLE001 - log and continue
        traceback.print_exc()
        db.log_sync(client, "garmin", "error", 0, str(exc))
        print(f"[garmin] FAILED: {exc}")
        return False


def run_lifting(client) -> bool:
    source = "sheet" if os.getenv("SHEET_CSV_URL") else "keep"
    try:
        if source == "sheet":
            from scraper import sheets_sync as lift
        else:
            from scraper import keep_sync as lift

        sessions = lift.collect_sessions()
        total_sets = 0
        for session_id, session_row, sets_rows in sessions:
            total_sets += db.upsert_lift_session(
                client,
                session_id=session_id,
                date=session_row["date"],
                title=session_row["title"],
                raw_text=session_row["raw_text"],
                sets_rows=sets_rows,
            )
        db.log_sync(client, source, "ok", len(sessions))
        print(f"[{source}] upserted {len(sessions)} sessions / {total_sets} sets")
        return True
    except Exception as exc:  # noqa: BLE001
        traceback.print_exc()
        db.log_sync(client, source, "error", 0, str(exc))
        print(f"[{source}] FAILED: {exc}")
        return False


def main() -> int:
    load_dotenv()
    client = db.get_client()
    ok_garmin = run_garmin(client)
    ok_lifting = run_lifting(client)
    return 0 if (ok_garmin and ok_lifting) else 1


if __name__ == "__main__":
    sys.exit(main())
