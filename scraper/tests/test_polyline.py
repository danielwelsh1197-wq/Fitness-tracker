"""Tests for the polyline encoder (offline)."""
import unittest

from scraper.polyline import downsample, encode, encode_track


class Encode(unittest.TestCase):
    def test_known_vector(self):
        # The canonical example from Google's polyline documentation.
        coords = [(38.5, -120.2), (40.7, -120.95), (43.252, -126.453)]
        assert encode(coords) == "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    def test_empty(self):
        assert encode([]) == ""


class Downsample(unittest.TestCase):
    def test_keeps_small_tracks(self):
        pts = [(0.0, 0.0), (1.0, 1.0)]
        assert downsample(pts, 200) == pts

    def test_thins_and_keeps_last(self):
        pts = [(float(i), 0.0) for i in range(1000)]
        out = downsample(pts, 100)
        assert len(out) <= 102
        assert out[0] == pts[0]
        assert out[-1] == pts[-1]   # last point preserved


class EncodeTrack(unittest.TestCase):
    def test_none_for_too_few_points(self):
        assert encode_track([(1.0, 1.0)]) is None
        assert encode_track([]) is None

    def test_encodes_when_enough(self):
        enc = encode_track([(51.5, -0.1), (51.6, -0.2)])
        assert isinstance(enc, str) and len(enc) > 0


if __name__ == "__main__":
    unittest.main()
