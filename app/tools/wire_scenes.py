#!/usr/bin/env python3
"""
Rewrite the scene registry from whatever scene files actually exist on disk.

Sixty-odd landmarks are written independently, and each one is a separate file. Hand-editing the
registry as they land is exactly the kind of bookkeeping that goes wrong quietly — a stop that
silently draws nothing looks identical to a stop whose scene has a bug. So the registry is
generated: this scans the package for `object SceneX : MathScene`, matches it to its Scene enum
value through the table below, and rewrites the map.

A Scene value with no file yet is simply left out, which is the designed behaviour — the tour
still flies and that stop draws nothing.

    python3 app/tools/wire_scenes.py
"""
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
PKG = HERE.parent / "src" / "main" / "java" / "com" / "rayneo" / "mathcosmos"
REGISTRY = PKG / "MathScenes.kt"

# Scene enum value -> the object that draws it. Grouped by tour, in tour order.
TABLE = [
    ("AMBIENT_TRACE", "SceneAmbientTrace"), ("AMBIENT_WAKE", "SceneAmbientWake"),
    ("AMBIENT_COUNTRY", "SceneAmbientCountry"), ("AMBIENT_FIELD", "SceneAmbientField"),

    ("UNIT", "SceneUnit"), ("RECTANGLE", "SceneRectangle"),
    ("COMPLETED_SQUARE", "SceneCompletedSquare"), ("DIFFERENCE", "SceneDifference"),
    ("RIGHT_ANGLE", "SceneRightAngle"), ("TWINS", "SceneTwins"), ("CONE", "SceneCone"),
    ("FOCUS", "SceneFocus"), ("WHEEL", "SceneWheel"), ("TURN", "SceneTurn"),
    ("LOG_RULER", "SceneLogRuler"), ("DOUBLING", "SceneDoubling"),

    ("MACHINE", "SceneMachine"), ("NARROWING", "SceneNarrowing"), ("HOLE", "SceneHole"),
    ("CHORD", "SceneChord"), ("CLOSING_JAW", "SceneClosingJaw"),
    ("STRAIGHT_WORLD", "SceneStraightWorld"), ("SLOPE_RIBBON", "SceneSlopeRibbon"),
    ("HANDOFF", "SceneHandoff"), ("GROWING_RECTANGLE", "SceneGrowingRectangle"),
    ("SELF_SLOPE", "SceneSelfSlope"), ("FLAT_SPOT", "SceneFlatSpot"), ("HUNT", "SceneHunt"),

    ("WAKE", "SceneWake"), ("SLABS", "SceneSlabs"), ("THINNING", "SceneThinning"),
    ("SWEEP_AND_HEIGHT", "SceneSweepAndHeight"), ("TWO_CLOCKS", "SceneTwoClocks"),
    ("SIGNED_WAKE", "SceneSignedWake"), ("LATHE", "SceneLathe"), ("STRING", "SceneString"),
    ("HORN", "SceneHorn"), ("RE_RULING", "SceneReRuling"), ("PARTS", "SceneByParts"),

    ("STAIRCASE", "SceneStaircase"), ("TOWER", "SceneTower"), ("HALVING_ROOM", "SceneHalvingRoom"),
    ("SLOW_CLIMB", "SceneSlowClimb"), ("TEST", "SceneTest"),
    ("ALTERNATING_WALK", "SceneAlternatingWalk"), ("MATCHING_CURVES", "SceneMatchingCurves"),
    ("PRICE_OF_AGREEMENT", "ScenePriceOfAgreement"), ("EDGE_OF_WORLD", "SceneEdgeOfWorld"),
    ("WAVE_FROM_POWERS", "SceneWaveFromPowers"), ("MEETING", "SceneMeeting"),

    ("LANDSCAPE", "SceneLandscape"), ("TWO_CUTS", "SceneTwoCuts"), ("PLATE", "ScenePlate"),
    ("COMPASS", "SceneCompass"), ("CONTOURS", "SceneContours"),
    ("ANY_DIRECTION", "SceneAnyDirection"), ("PASS", "ScenePass"), ("TETHER", "SceneTether"),
    ("COLUMN_FIELD", "SceneColumnField"), ("ORDER", "SceneOrder"),
    ("STRETCHED_GROUND", "SceneStretchedGround"), ("ROUGH_PLACE", "SceneRoughPlace"),

    ("ARROW_FIELD", "SceneArrowField"), ("STREAMLINE", "SceneStreamline"),
    ("PROBES_OUT", "SceneProbesOut"), ("PADDLE_WHEEL", "ScenePaddleWheel"),
    ("DOWNHILL_FIELD", "SceneDownhillField"), ("LOOP_AND_SHEET", "SceneLoopAndSheet"),
    ("BAG_AND_SKIN", "SceneBagAndSkin"), ("RIM", "SceneRim"), ("SLOPE_FIELD", "SceneSlopeField"),
    ("PULL_HOME", "ScenePullHome"), ("SPRING_AND_CIRCLE", "SceneSpringAndCircle"),
    ("SPREADING", "SceneSpreading"),

    ("OUTSIDE", "SceneOutside"), ("PLANE", "ScenePlane"),
]

HEADINGS = {
    "AMBIENT_TRACE": "tour-wide ambients",
    "UNIT": "I - THE SOLID GROUND",
    "MACHINE": "II - THE APPROACH",
    "WAKE": "III - THE ACCUMULATION",
    "STAIRCASE": "IV - THE INFINITE",
    "LANDSCAPE": "V - THE OPEN COUNTRY",
    "ARROW_FIELD": "VI - THE FIELD AND THE FLOW",
    "OUTSIDE": "shared",
}


def declared():
    """Every `object SceneX : MathScene` that exists in the package."""
    found = set()
    for f in PKG.glob("Scene*.kt"):
        for m in re.finditer(r"^object\s+(\w+)\s*:\s*MathScene\b", f.read_text(), re.M):
            found.add(m.group(1))
    return found


def main():
    have = declared()
    lines, missing = [], []
    for value, obj in TABLE:
        if value in HEADINGS:
            lines.append(f"        // {HEADINGS[value]}")
        if obj in have:
            lines.append(f"        put(Scene.{value}, {obj})")
        else:
            missing.append((value, obj))

    src = REGISTRY.read_text()
    start = src.index("    private val byScene: Map<Scene, MathScene> = buildMap {")
    end = src.index("    }", src.index("put(", start))
    body = "    private val byScene: Map<Scene, MathScene> = buildMap {\n" + "\n".join(lines) + "\n"
    REGISTRY.write_text(src[:start] + body + src[end:])

    print(f"wired {len(TABLE) - len(missing)} / {len(TABLE)} scenes")
    if missing:
        print(f"  not built yet ({len(missing)}): " + ", ".join(v for v, _ in missing))
    return 0


if __name__ == "__main__":
    sys.exit(main())
