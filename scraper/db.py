"""Supabase access for the scraper.

Uses the **service-role** key (full write access) — this module must only ever
run server-side (locally or in GitHub Actions), never inside the app.
"""
from __future__ import annotations

import os
from typing import List, Optional

from supabase import Client, create_client


def get_client() -> Client:
    """Create a Supabase client from environment variables."""
    url = os.environ["SUPABASE_URL"]
    key = os.environ["SUPABASE_SERVICE_KEY"]
    return create_client(url, key)


def upsert_activities(client: Client, rows: List[dict]) -> int:
    """Insert/update activities keyed by ``garmin_id``. Returns rows written."""
    if not rows:
        return 0
    client.table("activities").upsert(rows, on_conflict="garmin_id").execute()
    return len(rows)


def upsert_lift_session(
    client: Client,
    session_id: str,
    date: str,
    title: str,
    raw_text: str,
    sets_rows: List[dict],
) -> int:
    """Upsert one lifting session and replace its sets (idempotent re-parse).

    ``session_id`` is the Google Keep note id, used as the primary key so that
    re-parsing the same note overwrites rather than duplicates.
    Returns the number of set rows written.
    """
    client.table("lift_sessions").upsert(
        {
            "id": session_id,
            "date": date,
            "title": title,
            "raw_text": raw_text,
        },
        on_conflict="id",
    ).execute()

    # Replace sets so edits to a note are reflected exactly.
    client.table("lift_sets").delete().eq("session_id", session_id).execute()
    if sets_rows:
        for row in sets_rows:
            row["session_id"] = session_id
        client.table("lift_sets").insert(sets_rows).execute()
    return len(sets_rows)


def log_sync(
    client: Client,
    source: str,
    status: str,
    items_added: int = 0,
    error: Optional[str] = None,
) -> None:
    """Record a sync attempt in ``sync_log``."""
    client.table("sync_log").insert(
        {
            "source": source,
            "status": status,
            "items_added": items_added,
            "error": (error or "")[:2000] or None,
        }
    ).execute()
