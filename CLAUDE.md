# CLAUDE.md

Context for working on this repo with Claude. Keep it short and current — update it
when decisions change. Full setup lives in `docs/SETUP.md`; user-facing overview in
`README.md`.

## What this is
An Android app that unifies **running/swimming** with **weightlifting**, refreshed on a
schedule. Python scraper (`scraper/`) runs in GitHub Actions (cron, every 6h) and writes
to **Supabase** Postgres; a native **Kotlin + Jetpack Compose** app (`android/`) reads it
via **Retrofit against PostgREST** with the read-only anon key (RLS). Scraper writes with
the Supabase service_role key (CI secret only).

## Repo layout
- `scraper/` — Python. `parser.py` (`Exercise NxN @ weight` → sets, unit-tested),
  `sheets_sync.py` (lifting from a published Google Sheet CSV), `db.py`, `main.py`.
  `keep_sync.py` is a dormant lifting fallback.
- `supabase/schema.sql` — tables + RLS + YTD views. Re-running recreates `activities`
  (lifting tables use `if not exists`, so lifting data survives).
- `.github/workflows/sync.yml` — scheduled scrape.
- `android/` — the app (open in Android Studio).
- `tools/convert_tracker.py` — one-off historical xlsx → CSV converter (already run).

## Cardio (run/swim) source — DECIDED: Garmin forward + one-time Strava export backfill
- **Going forward:** Garmin free API (`scraper/garmin_sync.py`, `bootstrap_login garmin`,
  `GARMIN_TOKENS` secret). Strava's *live* API was rejected — it now needs a paid
  subscription (~June 2026) and the user doesn't pay / is on Android (no Apple Health).
- **History:** one-time backfill from the user's Strava bulk export via
  `tools/import_strava_export.py` (parser in `scraper/strava_export.py`). Loads run/swim
  as `source='strava'`. The export at `~/Downloads/Strava export 16-6-26` has 318 run/swim
  (2024-03 → 2026-06-16).
- **No-overlap rule:** `SYNC_START_DATE=2026-06-17` (day after the export) so Garmin only
  adds newer runs. Set in `sync.yml` and `.env.example`. Importing all + this cutoff =
  no gap, no dup.
- `activities` keys on `activity_id` (+ `source`, `activity_type`); both sources share it.
- There is no live `strava_sync.py` — that approach was removed. Don't reintroduce it.

## Run locations (areas)
- `activities` has `location_name` (neighbourhood) + `start_lat`/`start_lng`. The app groups
  runs by `location_name` in the Stats screen ("Where you run").
- Reverse-geocoding via `scraper/geocode.py` (OSM Nominatim, suburb-level, cached by rounded
  coord, fallback-safe). Garmin sync geocodes new runs in `main.run_garmin` (seeds the cache
  from `db.known_locations` so only new areas hit the network; falls back to Garmin's
  `locationName`).
- History backfill = `tools/backfill_locations.py` (one-time, local): reads each Strava run's
  start point from the export's `.fit.gz`/`.gpx` files (needs `pip install fitparse`), geocodes,
  updates the row. Garmin rows use their own coords. Run with the export dir as the arg.

## Local env constraints (this machine)
- Python: `~/fitness-dashboard/.venv` (system 3.9 is too old; venv built with 3.14).
  Run tests: `./.venv/bin/python -m unittest scraper.tests.test_parser scraper.tests.test_sheets scraper.tests.test_strava_export scraper.tests.test_geocode`
- **No JDK / Android SDK here** → the Android app only builds in Android Studio, not from
  this shell. Write Kotlin; the user builds/runs it.
- **`gh` not installed** → GitHub repo/secret steps are manual via the web UI.
- Git remote: `origin` = `github.com/danielwelsh1197-wq/Fitness-tracker` (note the repo
  name differs from the folder name `fitness-dashboard`).

## Conventions
- Only commit when the user explicitly asks. Prefer naming specific files over `git add -A`.
- Keep `scraper/.env` (real secrets) out of git; `.env.example` is the template.
- Parser/data changes need a passing unit test before they're considered done.
