"""Tests for the neighbourhood picker (offline; no network)."""
import unittest

from scraper.geocode import pick_area, seed_cache, neighbourhood, _key


class PickArea(unittest.TestCase):
    def test_prefers_suburb(self):
        addr = {"suburb": "Belsize Park", "city_district": "London Borough of Camden"}
        assert pick_area(addr) == "Belsize Park"

    def test_falls_through_to_neighbourhood(self):
        assert pick_area({"neighbourhood": "Steele's Village"}) == "Steele's Village"

    def test_strips_london_borough_prefix(self):
        assert pick_area({"city_district": "London Borough of Camden"}) == "Camden"

    def test_order_suburb_beats_quarter(self):
        assert pick_area({"quarter": "Lonesome", "suburb": "Mitcham"}) == "Mitcham"

    def test_empty_returns_none(self):
        assert pick_area({}) is None
        assert pick_area({"road": "High St"}) is None


class CacheSeeding(unittest.TestCase):
    def test_seeded_value_skips_network(self):
        # A seeded coordinate is returned without any HTTP call.
        seed_cache([(51.5514, -0.1599, "Camden Town")])
        assert neighbourhood(51.5514, -0.1599) == "Camden Town"

    def test_rounding_shares_cache_entry(self):
        seed_cache([(51.41770, -0.16158, "Mitcham")])
        # A point ~30 m away rounds to the same key.
        assert neighbourhood(51.41773, -0.16161) == "Mitcham"

    def test_none_coords(self):
        assert neighbourhood(None, None) is None


if __name__ == "__main__":
    unittest.main()
