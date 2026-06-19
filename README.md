# Fitness Dashboard

One Android app that unifies **running & swimming from Garmin** with
**weightlifting from Google Keep**, refreshed automatically on a schedule.

```
GitHub Actions (cron, every 6h)          Supabase (Postgres + auto REST)
┌─────────────────────────────┐  upsert  ┌──────────────────────────────┐
│ scraper/ (Python)            │ ───────▶ │ activities                    │
│  • garminconnect → activities│          │ lift_sessions / lift_sets     │
│  • gkeepapi → lift notes     │          │ sync_log + YTD views          │
│  • parser → sets/reps/weight │          └──────────────┬───────────────┘
└─────────────────────────────┘             read (anon key + RLS, HTTPS)
                                          ┌──────────────▼───────────────┐
                                          │ android/ (Kotlin + Compose)   │
                                          │ Activities · Stats · Lifting  │
                                          └──────────────────────────────┘
```

## Why this shape?
Garmin's official API needs business approval and Google Keep has no public API,
so the reliable path is the unofficial Python libraries `garminconnect` and
`gkeepapi`. Those need stored credentials running on a schedule — impractical on
the phone — so a small scheduled job in the cloud does the scraping and the app
just reads the results.

## Repo layout
| Path | What |
| --- | --- |
| `scraper/` | Python scraper: Garmin + Keep → Supabase. `parser.py` turns `Exercise NxN @ weight` notes into sets. |
| `scraper/tests/` | Parser unit tests (`python -m unittest scraper.tests.test_parser`). |
| `supabase/schema.sql` | Tables, RLS policies, and year-to-date views. Run once in the Supabase SQL editor. |
| `.github/workflows/sync.yml` | Scheduled scrape (every 6h + manual dispatch). |
| `android/` | Native Kotlin + Jetpack Compose app. Open this folder in Android Studio. |

## Weightlifting note format
Notes labelled **Workout** in Google Keep, one exercise per line:

```
Push day
Bench Press 3x5 @ 80kg
Overhead Press 3x8 @ 40kg
Tricep Pushdown 3x12 @ 25kg
```

The parser tolerates bullets/checkboxes, `x`/`×`, `kg`/`lb`, decimals, and
trailing comments. Lines that don't match (dates, free text) are ignored.

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
