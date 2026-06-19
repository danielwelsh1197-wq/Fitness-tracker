"""Tests for the Strava-export parser (offline, no network/DB)."""
import unittest

from scraper.strava_export import parse_csv, parse_date, to_row

# Minimal but representative header: it reproduces Strava's duplicate 'Distance'
# columns (km first, metres second) so we verify DictReader keeps the metres one.
HEADER = (
    "Activity ID,Activity Date,Activity Name,Activity Type,"
    "Elapsed Time,Distance,Moving Time,Distance,Average Speed,"
    "Elevation Gain,Average Heart Rate,Calories"
)
CSV = "\n".join(
    [
        HEADER,
        '18947774483,"Jun 16, 2026, 6:10:37 PM",Evening Run,Run,1816,5.22,1710,5220.6,3.053,28.5,152,399',
        '100,"Dec 30, 2025, 8:00:00 AM",Morning Run,Run,1800,5.00,1700,5000,3.0,10,150,350',
        '200,"Oct 1, 2025, 7:00:00 AM",Lunch Swim,Swim,2000,1.50,1900,1500,0.8,0,140,300',
        '300,"Nov 1, 2025, 9:00:00 AM",Ride home,Ride,3600,20.0,3500,20000,5.5,100,130,600',
    ]
)


class ParseDate(unittest.TestCase):
    def test_to_utc_iso(self):
        assert parse_date("Jun 16, 2026, 6:10:37 PM") == "2026-06-16T18:10:37Z"

    def test_unparseable(self):
        assert parse_date("yesterday") is None
        assert parse_date("") is None
        assert parse_date(None) is None


class ToRow(unittest.TestCase):
    def setUp(self):
        # Pull the metres Distance via DictReader by going through parse_csv.
        self.run = next(r for r in parse_csv(CSV) if r["activity_id"] == 18947774483)

    def test_distance_is_metres_not_km(self):
        assert self.run["distance_m"] == 5220.6   # not 5.22

    def test_core_fields(self):
        r = self.run
        assert r["sport"] == "run"
        assert r["activity_type"] == "Run"
        assert r["name"] == "Evening Run"
        assert r["start_time"] == "2026-06-16T18:10:37Z"
        assert r["duration_s"] == 1710.0           # Moving Time
        assert r["avg_speed_mps"] == 3.053
        assert r["avg_hr"] == 152                   # int, not 152.0
        assert r["calories"] == 399.0
        assert r["elevation_gain_m"] == 28.5
        assert r["source"] == "strava"
        assert isinstance(r["activity_id"], int)

    def test_missing_id_or_date_skipped(self):
        assert to_row({"Activity ID": "", "Activity Date": "Jun 1, 2026, 1:00:00 PM"}) is None
        assert to_row({"Activity ID": "5", "Activity Date": "bad"}) is None


class ParseCsv(unittest.TestCase):
    def test_default_keeps_run_and_swim_only(self):
        sports = sorted(r["sport"] for r in parse_csv(CSV))
        assert sports == ["run", "run", "swim"]      # the Ride is dropped

    def test_all_sports_keeps_ride(self):
        rows = parse_csv(CSV, sports=None)
        assert any(r["sport"] == "other" for r in rows)
        assert len(rows) == 4

    def test_before_cutoff_excludes_current_year(self):
        rows = parse_csv(CSV, before="2026-01-01")
        ids = sorted(r["activity_id"] for r in rows)
        assert ids == [100, 200]                     # 2026 run excluded, 2025 kept


if __name__ == "__main__":
    unittest.main()
