"""Reverse-geocode a coordinate to a neighbourhood name via OpenStreetMap Nominatim.

Used by the Garmin sync (new runs) and the one-time location backfill. Results
are cached by rounded coordinate, so repeated start points (e.g. home) cost a
single request, and we pause between live requests to respect Nominatim's usage
policy. Any failure returns None so callers can fall back gracefully.
"""
from __future__ import annotations

import time
from typing import Iterable, Optional, Tuple

import requests

_URL = "https://nominatim.openstreetmap.org/reverse"
_HEADERS = {"User-Agent": "fitness-dashboard/1.0 (personal run tracker)"}
# Neighbourhood-level first (recognisable area names), then coarser fallbacks.
_FIELDS = ("suburb", "neighbourhood", "quarter", "city_district", "town", "village", "city")
_PRECISION = 3  # ~110 m: runs from the same place share one cache entry / lookup
_MIN_INTERVAL = 1.1  # seconds between live requests (Nominatim policy)

_cache: dict[Tuple[float, float], Optional[str]] = {}
_last_call = 0.0


def _key(lat: float, lng: float) -> Tuple[float, float]:
    return (round(lat, _PRECISION), round(lng, _PRECISION))


def pick_area(address: dict) -> Optional[str]:
    """Choose a neighbourhood-level name from a Nominatim address dict."""
    for field in _FIELDS:
        value = address.get(field)
        if value:
            return value.replace("London Borough of ", "").strip()
    return None


def seed_cache(triples: Iterable[Tuple[Optional[float], Optional[float], Optional[str]]]) -> None:
    """Pre-load known (lat, lng) -> name pairs so they skip the network."""
    for lat, lng, name in triples:
        if lat is not None and lng is not None and name:
            _cache.setdefault(_key(lat, lng), name)


def neighbourhood(lat: Optional[float], lng: Optional[float]) -> Optional[str]:
    """Return a neighbourhood-level area name, or None if it can't be resolved."""
    if lat is None or lng is None:
        return None
    key = _key(lat, lng)
    if key in _cache:
        return _cache[key]
    name = _live_lookup(lat, lng)
    _cache[key] = name
    return name


def _live_lookup(lat: float, lng: float) -> Optional[str]:
    global _last_call
    wait = _MIN_INTERVAL - (time.monotonic() - _last_call)
    if wait > 0:
        time.sleep(wait)
    try:
        resp = requests.get(
            _URL,
            params={"lat": lat, "lon": lng, "format": "jsonv2", "zoom": 16, "addressdetails": 1},
            headers=_HEADERS,
            timeout=20,
        )
        _last_call = time.monotonic()
        resp.raise_for_status()
        return pick_area(resp.json().get("address", {}))
    except Exception:  # noqa: BLE001 - network/parse failure -> caller falls back
        _last_call = time.monotonic()
        return None
