"""Tests for the Google Sheets CSV parser (runs offline, no network)."""
import unittest

from scraper.sheets_sync import parse_csv


class LayoutAEntryColumn(unittest.TestCase):
    CSV = (
        "date,entry\n"
        "2026-06-01,Bench Press 3x5 @ 80kg\n"
        "2026-06-01,Overhead Press 3x8 @ 40kg\n"
        "2026-06-03,Squat 5x5 @ 100kg\n"
        ",orphan row with no date\n"
        "2026-06-03,just a comment not a lift\n"
    )

    def test_groups_into_sessions_by_date(self):
        sessions = parse_csv(self.CSV)
        ids = [s[0] for s in sessions]
        assert ids == ["sheet-2026-06-01", "sheet-2026-06-03"]

    def test_session_sets_and_skips_non_lifts(self):
        sessions = dict((s[0], s) for s in parse_csv(self.CSV))
        june1 = sessions["sheet-2026-06-01"][2]
        june3 = sessions["sheet-2026-06-03"][2]
        assert [r["exercise"] for r in june1] == ["Bench Press", "Overhead Press"]
        # the "just a comment" row is skipped, leaving only the squat
        assert [r["exercise"] for r in june3] == ["Squat"]

    def test_raw_text_joined(self):
        sessions = dict((s[0], s) for s in parse_csv(self.CSV))
        assert sessions["sheet-2026-06-01"][1]["raw_text"].count("\n") == 1


class LayoutBStructuredColumns(unittest.TestCase):
    CSV = (
        "date,exercise,sets,reps,weight\n"
        "2026-06-01,Bench Press,3,5,80\n"
        "2026-06-01,Squat,5,5,100\n"
        "2026-06-02,Deadlift,1,5,140\n"
    )

    def test_parses_structured_rows(self):
        sessions = dict((s[0], s) for s in parse_csv(self.CSV))
        assert set(sessions) == {"sheet-2026-06-01", "sheet-2026-06-02"}
        june1 = sessions["sheet-2026-06-01"][2]
        assert june1[0] == {
            "exercise": "Bench Press",
            "sets": 3,
            "reps": 5,
            "weight_kg": 80.0,
            "raw_line": "Bench Press 3x5 @ 80.0kg",
        }

    def test_total_volume(self):
        sessions = parse_csv(self.CSV)
        total = sum(
            r["sets"] * r["reps"] * r["weight_kg"]
            for _, _, sets_rows in sessions
            for r in sets_rows
        )
        # 3*5*80 + 5*5*100 + 1*5*140 = 1200 + 2500 + 700
        assert total == 4400.0


class DateFormats(unittest.TestCase):
    def test_us_slash_format(self):
        sessions = parse_csv("date,entry\n6/1/2026,Squat 5x5 @ 100kg\n")
        assert sessions[0][0] == "sheet-2026-06-01"

    def test_unparseable_date_skipped(self):
        assert parse_csv("date,entry\nyesterday,Squat 5x5 @ 100kg\n") == []

    def test_empty_csv(self):
        assert parse_csv("date,entry\n") == []


if __name__ == "__main__":
    unittest.main()
