# Fitness Dashboard

One Android app that unifies **running & swimming from Garmin** with
**weightlifting from a Google Sheet**, refreshed automatically on a schedule.
Older runs are backfilled once from a **Strava data export**.

```
GitHub Actions (cron, every 6h)          Supabase (Postgres + auto REST)
┌─────────────────────────────┐  upsert  ┌──────────────────────────────┐
│ scraper/ (Python)            │ ───────▶ │ activities  (source=garmin/   │
│  • Garmin API → activities   │          │             strava-export)    │
│  • Sheet CSV → lift notes    │          │ lift_sessions / lift_sets     │
│  • parser → sets/reps/weight │          │ sync_log + YTD views          │
└─────────────────────────────┘          └──────────────┬───────────────┘
   one-time: Strava export ────▲            read (anon key + RLS, HTTPS)
   (tools/import_strava_export) │         ┌──────────────▼───────────────┐
                                          │ android/ (Kotlin + Compose)   │
                                          │ Activities · Stats · Lifting  │
                                          └──────────────────────────────┘
```

## Why this shape?
Cardio comes from **Garmin** going forward (free API, auto-refreshing token);
**Strava's** free bulk export backfills history once (its live API now needs a
paid subscription). Both write to the same `activities` table, tagged by a
`source` column, with Garmin starting the day after the export so they don't
overlap. Lifting is logged in a Google Sheet published as CSV. The scheduled job
does the fetching server-side and the app just reads the results.

## Repo layout
| Path | What |
| --- | --- |
| `scraper/` | Python scraper: Garmin + Sheet → Supabase. `parser.py` turns `Exercise NxN @ weight` lines into sets. |
| `scraper/tests/` | Unit tests (`python -m unittest scraper.tests.test_parser scraper.tests.test_sheets scraper.tests.test_strava_export`). |
| `tools/import_strava_export.py` | One-time backfill of historical runs from a Strava data export. |
| `supabase/schema.sql` | Tables, RLS policies, and year-to-date views. Run once in the Supabase SQL editor. |
| `.github/workflows/sync.yml` | Scheduled scrape (every 6h + manual dispatch). |
| `android/` | Native Kotlin + Jetpack Compose app. Open this folder in Android Studio. |

## Weightlifting log format
A Google Sheet (published as CSV) with a `date,entry` header, one exercise per row:

```
date,entry
2026-06-19,Bench Press 3x5 @ 80kg
2026-06-19,Overhead Press 3x8 @ 40kg
2026-06-19,Tricep Pushdown 3x12 @ 25kg
```

A fully-structured `date,exercise,sets,reps,weight` layout works too. The parser
tolerates bullets/checkboxes, `x`/`×`, `kg`/`lb`, decimals, and trailing
comments. Lines that don't match (free text) are ignored.

## Getting started
See [docs/SETUP.md](docs/SETUP.md) for the full, step-by-step setup (Supabase,
credentials, GitHub secrets, Android Studio).

## Status / known follow-ups
- Offline caching (Room) and pull-to-refresh are easy next additions; v1 fetches
  on open and via a per-screen Refresh button.
- The YTD chart is hand-drawn in Compose to stay dependency-light; swap in a
  charting library later if you want axes/tooltips.
- Bodyweight-only lines (`Pull-ups 3x8`) are skipped because the format requires
  `@ weight`; add a rule in `scraper/parser.py` if you log them.
