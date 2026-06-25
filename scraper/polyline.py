"""Encode a GPS track as a Google "encoded polyline" string.

Used to store each run's route compactly in one text column. Tracks are
downsampled first (a heatmap doesn't need every point), keeping the payload to a
few hundred bytes per run. The Android app decodes these to draw the route map.
"""
from __future__ import annotations

import math
from typing import List, Optional, Sequence, Tuple

Point = Tuple[float, float]  # (lat, lng)


def downsample(points: Sequence[Point], target: int = 200) -> List[Point]:
    """Thin a track to roughly ``target`` points, always keeping the last one."""
    pts = list(points)
    if len(pts) <= target:
        return pts
    stride = math.ceil(len(pts) / target)
    out = pts[::stride]
    if out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


def encode(coords: Sequence[Point], precision: int = 5) -> str:
    """Encode (lat, lng) points with the standard Google polyline algorithm."""
    factor = 10 ** precision
    result: List[str] = []
    prev_lat = prev_lng = 0
    for lat, lng in coords:
        lat_i = round(lat * factor)
        lng_i = round(lng * factor)
        for delta in (lat_i - prev_lat, lng_i - prev_lng):
            delta = ~(delta << 1) if delta < 0 else (delta << 1)
            while delta >= 0x20:
                result.append(chr((0x20 | (delta & 0x1F)) + 63))
                delta >>= 5
            result.append(chr(delta + 63))
        prev_lat, prev_lng = lat_i, lng_i
    return "".join(result)


def encode_track(points: Sequence[Point], target: int = 200) -> Optional[str]:
    """Downsample then encode a track; None if there aren't enough points."""
    pts = downsample(points, target)
    return encode(pts) if len(pts) >= 2 else None
