# Setup

Four stages: **Supabase → scraper → GitHub Actions → Android app**. Budget ~1
hour. Cardio comes from Garmin going forward (step 2a) with a one-time Strava
export backfill for older runs (step 2e).

---

## 1. Supabase (data store)

1. Create a free project at https://supabase.com.
2. Open **SQL Editor**, paste the contents of [`supabase/schema.sql`](../supabase/schema.sql), and run it.
3. In **Project Settings → API**, copy three things:
   - **Project URL** (`https://xxxx.supabase.co`)
   - **anon** public key → used by the Android app (read-only via RLS)
   - **service_role** key → used by the scraper only (keep secret)

---

## 2. Scraper (run locally first to mint tokens)

Requires **Python 3.12+** (garminconnect 0.3.x). Check with `python3 --version`.

```bash
cd fitness-dashboard
python3 -m venv .venv && source .venv/bin/activate
pip install -r scraper/requirements.txt
cp scraper/.env.example scraper/.env     # then edit values
```

Run the tests to confirm the parser works:

```bash
python -m unittest scraper.tests.test_parser
```

### 2a. Garmin token (one-time, handles MFA)
Garmin is the going-forward source for new runs/swims.
```bash
python -m scraper.bootstrap_login garmin
```
Log in when prompted (enter the MFA code if asked). It caches a token under
`~/.garminconnect` (for local runs) and prints a **token string** — copy it for
the `GARMIN_TOKENS` GitHub secret (step 3). `login()` loads that string directly,
so there's nothing to unpack in CI.

### 2b. Create your workout sheet
Make a Google Sheet with a header row. Recommended layout (matches your old Keep
shorthand, one row per exercise):

| date | entry |
| --- | --- |
| 2026-06-19 | Bench Press 3x5 @ 80kg |
| 2026-06-19 | Squat 5x5 @ 100kg |

A fully-structured layout also works: `date, exercise, sets, reps, weight`.
Use `YYYY-MM-DD` dates. This sheet is now where you log workouts going forward.

### 2c. Publish the sheet as CSV
In the sheet: **File → Share → Publish to web → (Entire document) → CSV → Publish**.
Copy the generated URL (ends in `output=csv`) into `SHEET_CSV_URL` in `scraper/.env`.

> ⚠️ Anyone with that link can read the sheet — fine for workout data, but don't
> put anything sensitive in it. For a private alternative, switch to the Sheets
> API with a service account (the `keep_sync.py` Keep path also remains available).

### 2d. Smoke-test the full sync
With `scraper/.env` filled in (Supabase URL + service key, `SHEET_CSV_URL`):
```bash
python -m scraper.main
```
Check the Supabase **Table editor**: `activities`, `lift_sessions`, `lift_sets`
should have rows, and `sync_log` a row with `status = ok`.

### 2e. Backfill old runs from a Strava export (one-time)
Garmin's sync covers the current period; older runs (and any logged in other apps
like Nike Run Club) come from Strava's free data export instead.
1. Request your archive: **Strava → Settings → My Account → Download or Delete
   Your Account → Request your archive**. You'll get an email with a `.zip` —
   unzip it (e.g. to `~/Downloads/Strava export 16-6-26`).
2. Import the run/swim history into Supabase:
   ```bash
   python tools/import_strava_export.py "~/Downloads/Strava export 16-6-26"
   ```
   It loads every run/swim as `source='strava'` and prints the date Garmin should
   take over (the day after your last exported run).
3. Set `SYNC_START_DATE` to that date so Garmin only adds newer runs (no
   duplicates). It's already wired into the workflow as `2026-06-17` and in
   `.env.example`; change both if your export ends on a different day.

### 2f. Tag runs with area + route (one-time)
Powers the Stats screen's "Where you run" (area + pace) and "Your routes"
(heatmap). Garmin tags new runs' areas automatically; this backfills the
historical (Strava) ones from their GPS files.
```bash
pip install fitparse
python tools/backfill_locations.py "~/Downloads/Strava export 16-6-26"
```
For each run it reads the GPS track from the export's `.fit.gz`/`.gpx` files,
reverse-geocodes the start point (OpenStreetMap, ~a minute), and stores a
downsampled route `polyline`. Re-run with `--all` to re-process everything.

---

## 3. GitHub Actions (scheduled scraping)

1. Create a GitHub repo and push this project:
   ```bash
   git init && git add . && git commit -m "Initial commit"
   git branch -M main
   git remote add origin git@github.com:YOU/fitness-dashboard.git
   git push -u origin main
   ```
   (`gh repo create` also works if you install the GitHub CLI.)
2. In the repo: **Settings → Secrets and variables → Actions → New repository secret**, add:
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_KEY`
   - `GARMIN_TOKENS` (the token string from 2a)
   - `SHEET_CSV_URL` (the published-CSV link from 2c)

   (`SYNC_START_DATE` is set in the workflow file, not a secret.)
3. Go to the **Actions** tab, enable workflows, open **sync**, and click
   **Run workflow** to test. It then runs every 6 hours automatically. Verify
   via the run logs and a fresh `sync_log` row.

---

## 4. Android app

1. Open the **`android/`** folder in Android Studio (latest stable). Let it sync
   Gradle — it will create the Gradle wrapper if prompted.
2. Copy `android/local.properties.example` to `android/local.properties` and set:
   ```properties
   SUPABASE_URL=https://YOUR-PROJECT.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   ```
   (`sdk.dir` is filled by Android Studio automatically.)
3. Run on an emulator or device. You should see **Activities**, **Stats** (YTD),
   and **Lifting** tabs populated from Supabase. The Refresh icon re-fetches.

---

## Troubleshooting
- **App shows "Set SUPABASE_URL…"** — `local.properties` is missing the keys; re-check step 4.2 and re-run.
- **Empty screens** — run the scraper (step 2d) or the Action (step 3.3) first; confirm rows exist in Supabase.
- **`GARMIN_TOKENS` empty / Garmin login fails in CI** — the secret is missing or
  the refresh token was revoked (password change / long gap). Re-run
  `python -m scraper.bootstrap_login garmin` and update the `GARMIN_TOKENS` secret.
- **Old runs missing or duplicated** — the Strava-export backfill (2e) and Garmin
  must not overlap. `SYNC_START_DATE` should be the day after your last exported
  run; lower it and you may double-count that day, raise it and you may leave a gap.
- **A recent run is missing** — Garmin only fetches from `SYNC_START_DATE`
  onward; confirm the activity is in Garmin Connect and dated on/after that date.
- **GitHub cron didn't fire on time** — GitHub's scheduler can lag under load; it's best-effort. Use **Run workflow** to force a sync.
- **No lifting data** — confirm the sheet is **published** (not just shared), `SHEET_CSV_URL` ends in `output=csv`, dates are `YYYY-MM-DD`, and `entry` lines match `Exercise NxN @ weight`. Open the URL in a browser; you should see raw CSV.
