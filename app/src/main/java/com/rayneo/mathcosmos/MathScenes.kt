package com.rayneo.mathcosmos

/**
 * Which landmark is drawn at a stop.
 *
 * Every value here is a mathematical OBJECT the craft flies through or alongside, never a topic.
 * "Quadratics" is not a scene; a square with a corner missing, and the corner arriving to fill it,
 * is. If a proposed stop cannot name the thing you would see out of the window, it is not ready
 * to be a stop.
 */
enum class Scene {
    // ---- tour-wide ambients, drawn every frame for a whole tour -------------
    /** The roof curve: the function tours II and IV are about, running the length of the rail. */
    AMBIENT_TRACE,
    /** The roof curve plus the wake the craft trails behind it (tour III). */
    AMBIENT_WAKE,
    /** Open country: the wireframe landscape and its contours (tour V). */
    AMBIENT_COUNTRY,
    /** The vector field filling the space around the ghost tube (tour VI). */
    AMBIENT_FIELD,

    // ---- I, THE SOLID GROUND ----------------------------------------------
    UNIT, RECTANGLE, COMPLETED_SQUARE, DIFFERENCE, RIGHT_ANGLE, TWINS,
    CONE, FOCUS, WHEEL, TURN, LOG_RULER, DOUBLING,

    // ---- II, THE APPROACH --------------------------------------------------
    MACHINE, NARROWING, HOLE, CHORD, CLOSING_JAW, STRAIGHT_WORLD,
    SLOPE_RIBBON, HANDOFF, GROWING_RECTANGLE, SELF_SLOPE, FLAT_SPOT, HUNT,

    // ---- III, THE ACCUMULATION ---------------------------------------------
    WAKE, SLABS, THINNING, SWEEP_AND_HEIGHT, TWO_CLOCKS, SIGNED_WAKE,
    LATHE, STRING, HORN, RE_RULING, PARTS,

    // ---- IV, THE INFINITE ---------------------------------------------------
    STAIRCASE, TOWER, HALVING_ROOM, SLOW_CLIMB, TEST, ALTERNATING_WALK,
    MATCHING_CURVES, PRICE_OF_AGREEMENT, EDGE_OF_WORLD, WAVE_FROM_POWERS, MEETING,

    // ---- V, THE OPEN COUNTRY ------------------------------------------------
    LANDSCAPE, TWO_CUTS, PLATE, COMPASS, CONTOURS, ANY_DIRECTION,
    PASS, TETHER, COLUMN_FIELD, ORDER, STRETCHED_GROUND, ROUGH_PLACE,

    // ---- VI, THE FIELD AND THE FLOW -----------------------------------------
    ARROW_FIELD, STREAMLINE, PROBES_OUT, PADDLE_WHEEL, DOWNHILL_FIELD,
    LOOP_AND_SHEET, BAG_AND_SKIN, RIM, SLOPE_FIELD, PULL_HOME,
    SPRING_AND_CIRCLE, SPREADING,

    /** The closing stop of every tour: the walls go and the ride hangs in the dark as one thread. */
    OUTSIDE,

    // ---- development --------------------------------------------------------
    /** A coordinate plane: axes, ticks, numbers, a grid, and a curve plotted on it. */
    PLANE,
    /** Nothing — the passage alone. A stop that is only talk, or whose scene is not built yet. */
    EMPTY,
}

/**
 * The registry: scene name to the object that draws it.
 *
 * A stop whose scene has no entry simply draws nothing, so a tour can be written, scripted and
 * flown before all of its landmarks exist. That is deliberate: the scripts and the rails for six
 * tours land before sixty-odd scenes can be built, and a half-built tour should still fly.
 */
object MathScenes {
    private val byScene: Map<Scene, MathScene> = buildMap {
        // tour-wide ambients
        put(Scene.AMBIENT_TRACE, SceneAmbientTrace)
        put(Scene.AMBIENT_WAKE, SceneAmbientWake)
        put(Scene.AMBIENT_COUNTRY, SceneAmbientCountry)
        put(Scene.AMBIENT_FIELD, SceneAmbientField)
        // I - THE SOLID GROUND
        put(Scene.UNIT, SceneUnit)
        put(Scene.RECTANGLE, SceneRectangle)
        put(Scene.COMPLETED_SQUARE, SceneCompletedSquare)
        put(Scene.DIFFERENCE, SceneDifference)
        put(Scene.RIGHT_ANGLE, SceneRightAngle)
        put(Scene.TWINS, SceneTwins)
        put(Scene.CONE, SceneCone)
        put(Scene.FOCUS, SceneFocus)
        put(Scene.WHEEL, SceneWheel)
        put(Scene.TURN, SceneTurn)
        put(Scene.LOG_RULER, SceneLogRuler)
        put(Scene.DOUBLING, SceneDoubling)
        // II - THE APPROACH
        put(Scene.MACHINE, SceneMachine)
        put(Scene.NARROWING, SceneNarrowing)
        put(Scene.HOLE, SceneHole)
        put(Scene.CHORD, SceneChord)
        put(Scene.CLOSING_JAW, SceneClosingJaw)
        put(Scene.STRAIGHT_WORLD, SceneStraightWorld)
        put(Scene.SLOPE_RIBBON, SceneSlopeRibbon)
        put(Scene.HANDOFF, SceneHandoff)
        put(Scene.GROWING_RECTANGLE, SceneGrowingRectangle)
        put(Scene.SELF_SLOPE, SceneSelfSlope)
        put(Scene.FLAT_SPOT, SceneFlatSpot)
        put(Scene.HUNT, SceneHunt)
        // III - THE ACCUMULATION
        put(Scene.WAKE, SceneWake)
        put(Scene.SLABS, SceneSlabs)
        put(Scene.THINNING, SceneThinning)
        put(Scene.SWEEP_AND_HEIGHT, SceneSweepAndHeight)
        put(Scene.TWO_CLOCKS, SceneTwoClocks)
        put(Scene.SIGNED_WAKE, SceneSignedWake)
        put(Scene.LATHE, SceneLathe)
        put(Scene.STRING, SceneString)
        put(Scene.HORN, SceneHorn)
        put(Scene.RE_RULING, SceneReRuling)
        put(Scene.PARTS, SceneByParts)
        // IV - THE INFINITE
        put(Scene.STAIRCASE, SceneStaircase)
        put(Scene.TOWER, SceneTower)
        put(Scene.HALVING_ROOM, SceneHalvingRoom)
        put(Scene.SLOW_CLIMB, SceneSlowClimb)
        put(Scene.TEST, SceneTest)
        put(Scene.ALTERNATING_WALK, SceneAlternatingWalk)
        put(Scene.MATCHING_CURVES, SceneMatchingCurves)
        put(Scene.PRICE_OF_AGREEMENT, ScenePriceOfAgreement)
        put(Scene.EDGE_OF_WORLD, SceneEdgeOfWorld)
        put(Scene.WAVE_FROM_POWERS, SceneWaveFromPowers)
        put(Scene.MEETING, SceneMeeting)
        // V - THE OPEN COUNTRY
        put(Scene.LANDSCAPE, SceneLandscape)
        put(Scene.TWO_CUTS, SceneTwoCuts)
        put(Scene.PLATE, ScenePlate)
        put(Scene.COMPASS, SceneCompass)
        put(Scene.CONTOURS, SceneContours)
        put(Scene.ANY_DIRECTION, SceneAnyDirection)
        put(Scene.PASS, ScenePass)
        put(Scene.TETHER, SceneTether)
        put(Scene.COLUMN_FIELD, SceneColumnField)
        put(Scene.ORDER, SceneOrder)
        put(Scene.STRETCHED_GROUND, SceneStretchedGround)
        put(Scene.ROUGH_PLACE, SceneRoughPlace)
        // VI - THE FIELD AND THE FLOW
        put(Scene.ARROW_FIELD, SceneArrowField)
        put(Scene.STREAMLINE, SceneStreamline)
        put(Scene.PROBES_OUT, SceneProbesOut)
        put(Scene.PADDLE_WHEEL, ScenePaddleWheel)
        put(Scene.DOWNHILL_FIELD, SceneDownhillField)
        put(Scene.LOOP_AND_SHEET, SceneLoopAndSheet)
        put(Scene.BAG_AND_SKIN, SceneBagAndSkin)
        put(Scene.RIM, SceneRim)
        put(Scene.SLOPE_FIELD, SceneSlopeField)
        put(Scene.PULL_HOME, ScenePullHome)
        put(Scene.SPRING_AND_CIRCLE, SceneSpringAndCircle)
        put(Scene.SPREADING, SceneSpreading)
        // shared
        put(Scene.OUTSIDE, SceneOutside)
        put(Scene.PLANE, ScenePlane)
    }

    fun of(scene: Scene): MathScene? = byScene[scene]

    /** Which scenes are not built yet — used by the validator and worth knowing during a port. */
    fun missing(): List<Scene> = Scene.entries.filter { it != Scene.EMPTY && it !in byScene }
}
