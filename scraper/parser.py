"""Parse weightlifting notes written in the ``Exercise NxN @ weight`` format.

Example note body::

    Squat 5x5 @ 100kg
    Bench Press 3x5 @ 80kg
    - Deadlift 1x5 @ 140 kg
    [ ] Overhead Press 3 x 5 @ 40kg

Each matching line becomes one :class:`ParsedExercise`. Lines that don't match
(dates, headers, blank lines, free-form comments) are ignored.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import List, Optional

LB_TO_KG = 0.45359237

# Leading list / checkbox / bullet markers we tolerate at the start of a line.
_BULLET_RE = re.compile(r"^\s*(?:[-*•·–—]|☐|☑|\[[ xX]?\])\s*")

# Core pattern: <exercise> <sets> x <reps> @ <weight><unit?>
# - exercise name is non-greedy so it stops right before the "NxN" token
# - the separator between sets and reps may be x, X or the unicode ×
# - the unit is optional and defaults to kg
_LINE_RE = re.compile(
    r"""
    ^\s*
    (?P<exercise>.+?)               # exercise name (non-greedy)
    \s+
    (?P<sets>\d+)                   # number of sets
    \s*[x×X]\s*
    (?P<reps>\d+)                   # reps per set
    \s*@\s*
    (?P<weight>\d+(?:[.,]\d+)?)     # weight value (allows . or , decimals)
    \s*
    (?P<unit>kgs?|kilos?|lbs?|pounds?)?   # optional unit
    \b
    """,
    re.VERBOSE | re.IGNORECASE,
)


@dataclass(frozen=True)
class ParsedExercise:
    """One parsed exercise line."""

    exercise: str
    sets: int
    reps: int
    weight_kg: float
    raw_line: str

    @property
    def volume_kg(self) -> float:
        """Tonnage for this line: sets x reps x weight."""
        return self.sets * self.reps * self.weight_kg


def _to_kg(value: float, unit: Optional[str]) -> float:
    """Normalise a weight to kilograms, rounded to 2dp."""
    if unit and unit.lower().startswith(("lb", "pound")):
        return round(value * LB_TO_KG, 2)
    return round(value, 2)


def parse_line(line: str) -> Optional[ParsedExercise]:
    """Parse a single line. Returns ``None`` if it isn't a lift entry."""
    raw = line.rstrip("\n")
    stripped = _BULLET_RE.sub("", raw)
    match = _LINE_RE.match(stripped)
    if not match:
        return None
    weight = float(match.group("weight").replace(",", "."))
    return ParsedExercise(
        exercise=match.group("exercise").strip(),
        sets=int(match.group("sets")),
        reps=int(match.group("reps")),
        weight_kg=_to_kg(weight, match.group("unit")),
        raw_line=raw.strip(),
    )


def parse_note(text: str) -> List[ParsedExercise]:
    """Parse a whole note body, skipping any non-matching lines."""
    out: List[ParsedExercise] = []
    for line in text.splitlines():
        parsed = parse_line(line)
        if parsed is not None:
            out.append(parsed)
    return out
