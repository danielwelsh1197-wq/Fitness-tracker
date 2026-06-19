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

## ⚠️ Open decision — the cardio (run/swim) source
This is unsettled; don't assume Strava is live.
- Started on **Garmin** (`garminconnect`), then switched to **Strava** because not all runs
  are on Garmin. Strava code (`scraper/strava_sync.py`, `bootstrap_login strava`, the
  `garmin_id`→`activity_id` + `source` column rename) is written but **uncommitted**.
- **Blocker:** Strava's API now requires a paid subscription (from ~June 30 2026). User
  does **not** pay for Strava and is on **Android** (so Apple Health is out).
- Two free paths under consideration:
  1. **Revert to the free Garmin API**, import stray non-Garmin runs into Garmin manually.
  2. **Health Connect on-device** — the Android app reads runs locally (Garmin + other
     apps feed it); no server cardio scraper. Caveat: Garmin backfills only 30 days to
     Health Connect; needs Android 14+; stats computed in-app, not via SQL views.
- Lifting (Sheet → Supabase) is settled and unaffected either way.

## Local env constraints (this machine)
- Python: `~/fitness-dashboard/.venv` (system 3.9 is too old; venv built with 3.14).
  Run tests: `./.venv/bin/python -m unittest scraper.tests.test_parser scraper.tests.test_sheets scraper.tests.test_strava`
- **No JDK / Android SDK here** → the Android app only builds in Android Studio, not from
  this shell. Write Kotlin; the user builds/runs it.
- **`gh` not installed** → GitHub repo/secret steps are manual via the web UI.
- Git remote: `origin` = `github.com/danielwelsh1197-wq/Fitness-tracker` (note the repo
  name differs from the folder name `fitness-dashboard`).

## Conventions
- Only commit when the user explicitly asks. Prefer naming specific files over `git add -A`.
- Keep `scraper/.env` (real secrets) out of git; `.env.example` is the template.
- Parser/data changes need a passing unit test before they're considered done.
