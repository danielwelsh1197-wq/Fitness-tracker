"""Tests for the lift-note parser.

Runs with either ``python -m unittest`` (no install needed) or ``pytest``.
"""
import unittest

from scraper.parser import ParsedExercise, parse_line, parse_note


class ParseLineTests(unittest.TestCase):
    def test_basic(self):
        r = parse_line("Bench Press 3x5 @ 80kg")
        assert r == ParsedExercise("Bench Press", 3, 5, 80.0, "Bench Press 3x5 @ 80kg")

    def test_multiword_exercise_name(self):
        r = parse_line("Romanian Deadlift 4x8 @ 90kg")
        assert r is not None
        assert r.exercise == "Romanian Deadlift"
        assert (r.sets, r.reps, r.weight_kg) == (4, 8, 90.0)

    def test_spaces_around_x_and_unit(self):
        r = parse_line("Squat 5 x 5 @ 100 kg")
        assert r is not None
        assert (r.exercise, r.sets, r.reps, r.weight_kg) == ("Squat", 5, 5, 100.0)

    def test_unicode_times_symbol(self):
        r = parse_line("Deadlift 1×5 @ 140kg")
        assert r is not None
        assert (r.sets, r.reps, r.weight_kg) == (1, 5, 140.0)

    def test_bullet_marker_stripped(self):
        r = parse_line("- Overhead Press 3x5 @ 40kg")
        assert r is not None
        assert r.exercise == "Overhead Press"

    def test_checkbox_marker_stripped(self):
        r = parse_line("[ ] Barbell Row 4x8 @ 60 kg")
        assert r is not None
        assert r.exercise == "Barbell Row"

    def test_pound_conversion(self):
        r = parse_line("Bench 3x5 @ 100 lb")
        assert r is not None
        assert r.weight_kg == 45.36  # 100 * 0.45359237 rounded

    def test_decimal_weight(self):
        r = parse_line("Squat 5x5 @ 102.5kg")
        assert r is not None
        assert r.weight_kg == 102.5

    def test_comma_decimal_weight(self):
        r = parse_line("Squat 5x5 @ 102,5")
        assert r is not None
        assert r.weight_kg == 102.5

    def test_missing_unit_defaults_kg(self):
        r = parse_line("Curl 3x10 @ 20")
        assert r is not None
        assert r.weight_kg == 20.0

    def test_trailing_comment_ignored(self):
        r = parse_line("Squat 5x5 @ 100kg (felt easy)")
        assert r is not None
        assert (r.exercise, r.weight_kg) == ("Squat", 100.0)

    def test_volume_property(self):
        r = parse_line("Squat 5x5 @ 100kg")
        assert r is not None
        assert r.volume_kg == 2500.0

    def test_non_matching_lines_return_none(self):
        for line in ("", "   ", "Monday 12 May", "Felt great today", "Cardio session"):
            assert parse_line(line) is None, line


class ParseNoteTests(unittest.TestCase):
    NOTE = """Push day 18/06
    Bench Press 3x5 @ 80kg
    - Overhead Press 3x8 @ 40kg

    Felt strong
    Tricep Pushdown 3x12 @ 25kg
    """

    def test_counts_only_matching_lines(self):
        results = parse_note(self.NOTE)
        assert len(results) == 3
        assert [r.exercise for r in results] == [
            "Bench Press",
            "Overhead Press",
            "Tricep Pushdown",
        ]

    def test_total_volume(self):
        results = parse_note(self.NOTE)
        total = sum(r.volume_kg for r in results)
        # 3*5*80 + 3*8*40 + 3*12*25 = 1200 + 960 + 900
        assert total == 3060.0

    def test_empty_note(self):
        assert parse_note("") == []


if __name__ == "__main__":
    unittest.main()
