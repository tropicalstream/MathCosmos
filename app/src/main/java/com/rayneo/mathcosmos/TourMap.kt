package com.rayneo.mathcosmos

/**
 * Ambience family of a stop: the synthesized sound bed, the colour of the motes drifting past,
 * how fast they flow, and what kind of object they are.
 *
 * These are the moods of the subject, not places: counting is a room with a metronome in it,
 * a limit is a held breath, a series is a ladder of overtones, a field is moving air with a
 * direction. The audio engine keys its beds off the ordinal, so the ORDER here matters.
 */
enum class Amb {
    /** Whole numbers. Discrete ticking under everything; beads snapped to a unit lattice. */
    COUNT,
    /** The bare plane. Room tone, still air, chalk grains. */
    PLANE,
    /** A function is present. One held tone that bends with the height of the roof. */
    CURVE,
    /** Something is being closed in on. Near silence, a rising hairline whine, dust falling inward. */
    LIMIT,
    /** Slabs being laid and added. A conveyor: one low pulse per slab. */
    SUM,
    /** Terms without end. A harmonic ladder, each new partial a quieter overtone; motes that halve. */
    INFINITE,
    /** Open country. Wind over ground, contour flakes lying flat in level layers. */
    SURFACE,
    /** A field carries you. Moving air with a direction, and arrows that align with it. */
    FIELD,
    /** Being solved. A current you are riding; the drive note drops out. */
    SOLVE,
    /** The view from outside. The sibling app's look-back pad, unchanged. */
    LOOKBACK
}

/**
 * One stop on a rail. Positions are world units (stops ~16 apart down -z); [radius] is the
 * passage radius there; [wall] its colour; [rung] where the stop sits on the tour's own ladder;
 * [scene] the landmark; [amb] the ambience; [mapX]/[mapY] the marker position on the inset map
 * (figure coordinates, 100 x 150) with its label; [scaleLabel] the ladder text for the depth menu.
 */
class TourNode(
    val name: String, val x: Float, val y: Float, val z: Float, val radius: Float, val wall: FloatArray,
    val rung: Double, val scene: Scene, val amb: Amb,
    val mapX: Float, val mapY: Float, val mapLabel: String, val scaleLabel: String,
    /** What the cut is measured in here: "h", "Δx", "δ", "n", "cell". Shown on the HUD. */
    val cutUnit: String = "unit",
    /** How transparent the passage wall is at this stop. Open country is not a pipe. */
    val wallAlpha: Float = 1f,
    /**
     * The ONE sentence the viewer should be able to say after this stop, in their own words.
     *
     * It is not decoration. The stop menu shows it for whichever row is highlighted, which turns
     * the menu into a revision index: run down the tour, try to recall each stop before reading
     * it, and jump straight back to any you cannot. Retrieval and spacing are the two things that
     * actually make any of this last, and this is the only place in the app that supports either.
     */
    val takeaway: String = ""
)

/**
 * A complete tour: its stops, the script asset that narrates it, the ladder breakpoints for the
 * HUD (rail progress -> rung, interpolated between points, mirroring where the script's own step
 * cues land) and the rail positions where the arm probes reach out.
 */
class TourMap(
    val id: Int, val title: String, val subtitle: String, val hudTitle: String, val scriptAsset: String,
    val durationLabel: String,
    val nodes: List<TourNode>, val lengthKeys: FloatArray, val lengthM: DoubleArray, val armStops: FloatArray,
    /**
     * A scene drawn for the WHOLE tour, every frame, in addition to whichever landmark is near.
     *
     * Three tours have a persistent object that is not a stop: the roof curve of tours II-IV, the
     * wake the craft trails through tour III, the open terrain of tours V and VI. Making them
     * ordinary scenes with a tour-wide lifetime, rather than new machinery in the renderer, keeps
     * the whole system to one idea: everything you can see is a MathScene.
     */
    val ambient: Scene? = null,
    /**
     * THE TRACE: the height of the corridor's roof above the rail, as a function of rail progress
     * in node units. In tours II-IV the roof IS the function under discussion, which is what makes
     * the corridor the region under a curve rather than a place the mathematics is displayed in.
     */
    val trace: ((Float) -> Float)? = null,
    /**
     * THE COUNTRY: ground height at a world (x, z), for the tours where the tube goes ghost and
     * the real scenery is a landscape. Sampled into a wireframe once, and queried live by scenes
     * that need to put something ON the ground.
     */
    val terrain: ((Float, Float) -> Float)? = null,
    /**
     * THE FIELD: the vector at a world point, written into the out array as (vx, vy, vz).
     * Tour VI's whole subject, and what the drifting streaks advect along.
     */
    val field: ((Float, Float, Float, FloatArray) -> Unit)? = null
) {
    /**
     * Rail progress beyond which the streamline streaks stop being drawn: 0.7 past the last FIELD
     * stop, or -1 if this tour has none. (The sibling app used the same machinery for airflow in
     * the lung; here it draws the flow lines of a vector field.)
     */
    val streamEnd: Float = nodes.indexOfLast { it.amb == Amb.FIELD }.let { if (it < 0) -1f else it + 0.7f }
}

object Tours {
    private fun rgb(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    // Tour I's palette: slate and cold chalk, warming to amber by the log ruler.
    private val SLATE = rgb(0.20f, 0.24f, 0.40f)
    private val SLATE_DEEP = rgb(0.16f, 0.19f, 0.34f)
    private val CHALK = rgb(0.30f, 0.33f, 0.44f)
    private val WARM = rgb(0.40f, 0.31f, 0.34f)
    private val AMBER = rgb(0.46f, 0.32f, 0.24f)
    private val VOID = rgb(0.34f, 0.24f, 0.38f)

    /**
     * TOUR I — THE SOLID GROUND.
     *
     * Algebra is carpentry: every identity anyone was made to memorise is a shape being
     * rearranged in front of you. Nothing is cut finely here — the cut stays at one whole unit
     * for the entire ride, and that flat rung is the point. Its first fall, in Tour II, is an event.
     */
    val GROUND = TourMap(
        id = 1, title = "THE SOLID GROUND", subtitle = "ALGEBRA IS CARPENTRY  \u00b7  13 STOPS",
        hudTitle = "MATHCOSMOS I \u00b7 THE SOLID GROUND", scriptAsset = "tour1_script.json", durationLabel = "25 MIN",
        nodes = listOf(
            TourNode("THE UNIT", 0.0f, 0.0f, 0f, 3.4f, SLATE, 1.0, Scene.UNIT, Amb.COUNT, 10f, 22f, "ONE", "1 unit", takeaway = "A number is a length I can pick up and carry."),
            TourNode("THE RECTANGLE", 2.2f, 0.3f, -16f, 3.8f, SLATE, 1.0, Scene.RECTANGLE, Amb.COUNT, 23f, 22f, "a(b+c)", "1 unit", takeaway = "Multiplying is making a rectangle, and splitting the rectangle is the distributive law."),
            TourNode("THE COMPLETED SQUARE", -2.4f, -0.2f, -32f, 4.2f, CHALK, 1.0, Scene.COMPLETED_SQUARE, Amb.PLANE, 36f, 22f, "x\u00b2+bx", "1 unit", takeaway = "Every quadratic is a square with a corner missing."),
            TourNode("THE DIFFERENCE OF SQUARES", 2.0f, 0.4f, -48f, 3.8f, CHALK, 1.0, Scene.DIFFERENCE, Amb.PLANE, 49f, 22f, "a\u00b2\u2212b\u00b2", "1 unit", takeaway = "A difference of two squares is a frame, and a frame unrolls into a strip."),
            TourNode("THE RIGHT ANGLE", -2.6f, 0.0f, -64f, 4.0f, CHALK, 1.0, Scene.RIGHT_ANGLE, Amb.PLANE, 62f, 22f, "a\u00b2+b\u00b2=c\u00b2", "1 unit", takeaway = "The square on the long side is exactly the other two poured in."),
            TourNode("THE TWINS", 2.4f, -0.3f, -80f, 4.2f, SLATE_DEEP, 3.0, Scene.TWINS, Amb.COUNT, 75f, 22f, "k\u00b2, k\u00b3", "\u00d73", "copy", takeaway = "Doubling a thing quadruples its skin and multiplies its bulk eightfold."),
            TourNode("THE SLICED CONE", -2.0f, 0.3f, -96f, 4.2f, SLATE_DEEP, 1.0, Scene.CONE, Amb.PLANE, 88f, 22f, "CONICS", "1 unit", takeaway = "Circle, ellipse, parabola and hyperbola are one cone, cut at four angles."),
            TourNode("THE FOCUS", 2.2f, 0.2f, -112f, 3.6f, WARM, 1.0, Scene.FOCUS, Amb.CURVE, 10f, 75f, "LOCUS", "1 unit", takeaway = "Every point on a parabola is the same distance from one point and one line."),
            TourNode("THE WHEEL", -2.4f, -0.2f, -128f, 3.4f, WARM, 1.0, Scene.WHEEL, Amb.CURVE, 28f, 75f, "sin, cos", "\u03c0/6 per ring", "turn", takeaway = "Sine and cosine are the shadow of a point going round a circle."),
            TourNode("THE TURN", 2.0f, 0.3f, -144f, 3.0f, WARM, 1.0, Scene.TURN, Amb.PLANE, 46f, 75f, "\u00d7i", "1 unit", "turn", takeaway = "Multiplying by i is a quarter turn, and nothing else."),
            TourNode("THE STRETCHED RULER", -2.2f, 0.0f, -160f, 3.2f, AMBER, 1.0, Scene.LOG_RULER, Amb.COUNT, 64f, 75f, "log", "\u00d710 per ring", "\u00d710", takeaway = "A log scale turns multiplying into sliding."),
            TourNode("THE DOUBLING", 1.6f, -0.2f, -176f, 3.6f, AMBER, 1.0, Scene.DOUBLING, Amb.CURVE, 82f, 75f, "2\u207f", "1 unit", "step", takeaway = "Anything growing in proportion to itself beats anything growing by adding."),
            TourNode("THE VIEW FROM OUTSIDE", 0.0f, 0.2f, -194f, 9.0f, VOID, 1.0, Scene.OUTSIDE, Amb.LOOKBACK, 50f, 120f, "THE WHOLE TOUR", "the tour", "unit", 0f)
        ),
        // Algebra does not cut. The rung is one whole unit from end to end, and saying so on the
        // HUD for thirty-three minutes is what makes its first fall in Tour II mean something.
        lengthKeys = floatArrayOf(0f, 12f),
        lengthM = doubleArrayOf(1.0, 1.0),
        armStops = floatArrayOf(0.05f, 2.10f, 3.08f, 7.06f, 9.05f)   // the unit rod, the corner, the two edges, the bead, the arrow
    )

    /**
     * THE PROVING GROUND — a three-stop rail for looking at one scene on the glasses before it is
     * written into a tour. Not part of the curriculum; it is simply the fastest way to put a new
     * landmark in front of your eyes, and it costs nothing to keep.
     */
    val PROVING = TourMap(
        id = 9, title = "THE PROVING GROUND", subtitle = "ONE SCENE AT A TIME  \u00b7  3 STOPS",
        hudTitle = "MATHCOSMOS \u00b7 PROVING GROUND", scriptAsset = "tour9_script.json", durationLabel = "3 MIN",
        nodes = listOf(
            TourNode("APPROACH", 0.0f, 0.0f, 0f, 4.2f, SLATE, 1.0, Scene.EMPTY, Amb.PLANE, 50f, 20f, "APPROACH", "\u2014"),
            TourNode("THE PLANE", 1.6f, 0.2f, -16f, 4.0f, SLATE_DEEP, 1.0, Scene.PLANE, Amb.PLANE, 50f, 60f, "THE PLANE", "2 AXES"),
            TourNode("DEPART", 0.0f, 0.0f, -32f, 4.2f, SLATE, 1.0, Scene.EMPTY, Amb.LOOKBACK, 50f, 110f, "DEPART", "\u2014")
        ),
        lengthKeys = floatArrayOf(0f, 2f),
        lengthM = doubleArrayOf(1.0, 1.0),
        armStops = floatArrayOf(1.05f)
    )


    // ---- palettes for tours II-VI --------------------------------------------
    private val INDIGO = rgb(0.14f, 0.16f, 0.34f)
    private val INDIGO_PALE = rgb(0.24f, 0.27f, 0.46f)
    private val WHITEISH = rgb(0.42f, 0.45f, 0.56f)
    private val BRONZE = rgb(0.36f, 0.26f, 0.14f)
    private val GOLD = rgb(0.46f, 0.34f, 0.16f)
    private val GOLD_PALE = rgb(0.52f, 0.42f, 0.24f)
    private val VIOLET = rgb(0.26f, 0.18f, 0.40f)
    private val SILVER = rgb(0.36f, 0.36f, 0.46f)
    private val VIOLET_DEEP = rgb(0.20f, 0.14f, 0.34f)
    private val TEAL = rgb(0.14f, 0.32f, 0.30f)
    private val TEAL_PALE = rgb(0.20f, 0.40f, 0.36f)
    private val TEAL_DEEP = rgb(0.10f, 0.26f, 0.26f)
    private val CYAN = rgb(0.12f, 0.30f, 0.38f)
    private val ORANGE_WARM = rgb(0.38f, 0.26f, 0.16f)
    private val CYAN_DEEP = rgb(0.10f, 0.24f, 0.32f)

    /**
     * TOUR II — THE APPROACH. The corridor's roof is the function, and the passage radius is the
     * tolerance currently in play. This is the tour where the cut ladder falls for the first time.
     */
    val APPROACH = TourMap(
        id = 2, title = "THE APPROACH", subtitle = "HOW STEEP, EXACTLY HERE  \u00b7  13 STOPS",
        hudTitle = "MATHCOSMOS II \u00b7 THE APPROACH", scriptAsset = "tour2_script.json", durationLabel = "33 MIN",
        nodes = listOf(
            TourNode("THE MACHINE", 0.0f, 0.0f, 0f, 3.4f, INDIGO, 1.0, Scene.MACHINE, Amb.CURVE, 10.0f, 25f, "f(x)", "f(x)", "\u0394x", takeaway = "A function is a rule: one number goes in, exactly one comes out."),
            TourNode("THE NARROWING", 2.2f, 0.3f, -16f, 1.6f, INDIGO, 1.0, Scene.NARROWING, Amb.LIMIT, 23.3f, 25f, "\u03b5–\u03b4", "\u03b5–\u03b4", "\u03b4", takeaway = "A limit is a promise: say how close you want the answer, and I say how close to stand."),
            TourNode("THE HOLE", -2.4f, -0.2f, -32f, 2.8f, INDIGO, 1.0, Scene.HOLE, Amb.LIMIT, 36.7f, 25f, "CONTINUITY", "CONTINUITY", "\u03b4", takeaway = "Continuous means I can fly the whole way without lifting off."),
            TourNode("THE CHORD", 2.0f, 0.4f, -48f, 3.2f, INDIGO, 1.0, Scene.CHORD, Amb.CURVE, 50.0f, 25f, "SECANT", "SECANT", "h", takeaway = "Slope is rise over run, and I can measure it with two touches."),
            TourNode("THE CLOSING JAW", -2.6f, 0.0f, -64f, 2.6f, INDIGO, 1.0, Scene.CLOSING_JAW, Amb.LIMIT, 63.3f, 25f, "TANGENT", "TANGENT", "h", takeaway = "Shrink the run until the chord stops moving; where it stops is the curve's own direction."),
            TourNode("THE STRAIGHT WORLD", 2.4f, -0.3f, -80f, 3.6f, INDIGO_PALE, 1.0, Scene.STRAIGHT_WORLD, Amb.LIMIT, 76.7f, 25f, "LOCAL LINE", "LOCAL LINE", "h", takeaway = "Zoom far enough into a smooth curve and I cannot tell it from a line."),
            TourNode("THE FIELD OF SLOPES", -2.0f, 0.3f, -96f, 3.4f, INDIGO_PALE, 1.0, Scene.SLOPE_RIBBON, Amb.CURVE, 90.0f, 25f, "f′(x)", "f′(x)", "h", takeaway = "The steepness is itself a curve, and I can read it off the floor."),
            TourNode("THE HANDOFF", 2.2f, 0.2f, -112f, 3.0f, INDIGO_PALE, 1.0, Scene.HANDOFF, Amb.CURVE, 90.0f, 70f, "CHAIN RULE", "CHAIN RULE", "h", takeaway = "Move twice as fast through the input and everything downstream happens twice as fast."),
            TourNode("THE GROWING RECTANGLE", -2.4f, -0.2f, -128f, 3.8f, INDIGO_PALE, 1.0, Scene.GROWING_RECTANGLE, Amb.CURVE, 76.7f, 70f, "PRODUCT RULE", "PRODUCT RULE", "dt", takeaway = "A rectangle growing on both sides gains two strips and a crumb, and the crumb does not matter."),
            TourNode("THE SELF-SLOPE", 2.0f, 0.3f, -144f, 3.4f, WHITEISH, 1.0, Scene.SELF_SLOPE, Amb.CURVE, 63.3f, 70f, "e", "e", "h", takeaway = "There is one growth curve whose steepness equals its height, and that fixes e."),
            TourNode("THE FLAT SPOT", -2.2f, 0.0f, -160f, 3.6f, WHITEISH, 1.0, Scene.FLAT_SPOT, Amb.CURVE, 50.0f, 70f, "MAXIMA", "MAXIMA", "h", takeaway = "The best point is where the ground stops tilting, but flat does not always mean best."),
            TourNode("THE HUNT", 1.6f, -0.2f, -176f, 3.0f, WHITEISH, 1.0, Scene.HUNT, Amb.SOLVE, 36.7f, 70f, "NEWTON", "NEWTON", "error", takeaway = "Slide down the tangent, guess again, and the guesses collapse onto the answer."),
            TourNode("THE VIEW FROM OUTSIDE", -1.8f, 0.2f, -194f, 9.0f, WHITEISH, 1.0, Scene.OUTSIDE, Amb.LOOKBACK, 23.3f, 70f, "THE WHOLE TOUR", "THE WHOLE TOUR", "h", 0.0f, takeaway = "That whole corridor was the space under one curve."),
        ),
        // The whole shape of this tour is the fall: one whole unit at the machine, a billionth by
        // the time Newton's method has finished with it.
        lengthKeys = floatArrayOf(0f, 3.6f, 4.4f, 5.4f, 6f, 11f, 11.6f, 12f),
        lengthM = doubleArrayOf(1.0, 1.0, 1.0, 1e-2, 1e-4, 1e-4, 1e-9, 1e-9),
        armStops = floatArrayOf(3.05f, 4.05f, 6.05f, 8.05f, 9.05f),
        ambient = Scene.AMBIENT_TRACE,
        trace = { p -> 1.55f + 0.85f * kotlin.math.sin(p * 0.62f) + 0.30f * kotlin.math.sin(p * 1.45f + 1.1f) }
    )

    /**
     * TOUR III — THE ACCUMULATION. The craft trails a wake, and the wake is the integral. The roof
     * curve dives below the rail around the signed-area stop, which is why the trace can go negative.
     */
    val ACCUMULATION = TourMap(
        id = 3, title = "THE ACCUMULATION", subtitle = "WHAT I HAVE SWEPT UP  \u00b7  12 STOPS",
        hudTitle = "MATHCOSMOS III \u00b7 THE ACCUMULATION", scriptAsset = "tour3_script.json", durationLabel = "32 MIN",
        nodes = listOf(
            TourNode("THE WAKE", 0.0f, 0.0f, 0f, 3.4f, BRONZE, 1.0, Scene.WAKE, Amb.SUM, 10.0f, 25f, "AREA", "AREA", "\u0394x", takeaway = "The area under a curve is how much I have swept up so far."),
            TourNode("THE SLABS", 2.2f, 0.3f, -16f, 3.8f, BRONZE, 1.0, Scene.SLABS, Amb.SUM, 23.3f, 25f, "RIEMANN", "RIEMANN", "\u0394x", takeaway = "Cut it into slabs, add them up, and I can see exactly how wrong I am."),
            TourNode("THE THINNING", -2.4f, -0.2f, -32f, 3.2f, BRONZE, 1.0, Scene.THINNING, Amb.SUM, 36.7f, 25f, "THE LIMIT", "THE LIMIT", "\u0394x", takeaway = "Keep halving the slab and the error goes to nothing; what is left is the integral."),
            TourNode("THE SWEEP AND THE HEIGHT", 2.0f, 0.4f, -48f, 3.6f, BRONZE, 1.0, Scene.SWEEP_AND_HEIGHT, Amb.SUM, 50.0f, 25f, "THE THEOREM", "THE THEOREM", "\u0394x", takeaway = "The speed my total grows is exactly the height of the curve above me."),
            TourNode("THE TWO CLOCKS", -2.6f, 0.0f, -64f, 3.2f, GOLD, 1.0, Scene.TWO_CLOCKS, Amb.SUM, 63.3f, 25f, "F(b)\u2212F(a)", "F(b)\u2212F(a)", "\u0394x", takeaway = "With a total-so-far function I never add slabs at all; I subtract two readings."),
            TourNode("THE SIGNED WAKE", 2.4f, -0.3f, -80f, 3.6f, GOLD, 1.0, Scene.SIGNED_WAKE, Amb.SUM, 76.7f, 25f, "SIGNED AREA", "SIGNED AREA", "\u0394x", takeaway = "Below the axis, the sweep pays out instead of taking in."),
            TourNode("THE LATHE", -2.0f, 0.3f, -96f, 4.0f, GOLD, 1.0, Scene.LATHE, Amb.SUM, 90.0f, 25f, "REVOLUTION", "REVOLUTION", "\u0394x", takeaway = "Spin a shape and its area becomes a solid I can fly down the middle of."),
            TourNode("THE STRING", 2.2f, 0.2f, -112f, 3.2f, GOLD, 1.0, Scene.STRING, Amb.SUM, 90.0f, 70f, "ARC LENGTH", "ARC LENGTH", "\u0394x", takeaway = "A curve's length is the sum of tiny straight bits, and the bits are always a little short."),
            TourNode("THE HORN", -2.4f, -0.2f, -128f, 2.2f, GOLD_PALE, 1.0, Scene.HORN, Amb.INFINITE, 76.7f, 70f, "IMPROPER", "IMPROPER", "\u0394x", takeaway = "A thing can be endlessly long and still hold only so much."),
            TourNode("THE RE-RULING", 2.0f, 0.3f, -144f, 3.4f, GOLD_PALE, 1.0, Scene.RE_RULING, Amb.SUM, 63.3f, 70f, "SUBSTITUTION", "SUBSTITUTION", "\u0394x", takeaway = "Renaming the axis does not change how much is there."),
            TourNode("THE PARTS", -2.2f, 0.0f, -160f, 3.8f, GOLD_PALE, 1.0, Scene.PARTS, Amb.SUM, 50.0f, 70f, "BY PARTS", "BY PARTS", "\u0394x", takeaway = "The product rule read backwards: trade a sweep I cannot do for one I can."),
            TourNode("THE VIEW FROM OUTSIDE", 1.6f, -0.2f, -178f, 9.0f, GOLD_PALE, 1.0, Scene.OUTSIDE, Amb.LOOKBACK, 36.7f, 70f, "THE WHOLE TOUR", "THE WHOLE TOUR", "\u0394x", 0.0f, takeaway = "The whole run was one shape, and I have swept it."),
        ),
        lengthKeys = floatArrayOf(0f, 1.6f, 2.4f, 3f, 8f, 8.6f, 11f),
        lengthM = doubleArrayOf(0.5, 0.5, 1e-2, 1e-4, 1e-4, 1e-2, 1e-2),
        armStops = floatArrayOf(3.05f, 4.05f, 7.05f, 10.05f),
        ambient = Scene.AMBIENT_WAKE,
        trace = { p -> 1.2f + 2.0f * kotlin.math.sin(p * 0.75f) }
    )

    /**
     * TOUR IV — THE INFINITE. The passage radius is the remainder: how much is still unaccounted
     * for. It closes around a convergent structure and flares where things diverge.
     */
    val INFINITE = TourMap(
        id = 4, title = "THE INFINITE", subtitle = "ENDLESS PIECES, ONE ANSWER  \u00b7  12 STOPS",
        hudTitle = "MATHCOSMOS IV \u00b7 THE INFINITE", scriptAsset = "tour4_script.json", durationLabel = "30 MIN",
        nodes = listOf(
            TourNode("THE STAIRCASE", 0.0f, 0.0f, 0f, 3.2f, VIOLET, 1.0, Scene.STAIRCASE, Amb.INFINITE, 10.0f, 25f, "SEQUENCES", "SEQUENCES", "n", takeaway = "A sequence converges if it eventually stays inside any tube you name."),
            TourNode("THE TOWER", 2.2f, 0.3f, -16f, 3.6f, VIOLET, 1.0, Scene.TOWER, Amb.INFINITE, 23.3f, 25f, "PARTIAL SUMS", "PARTIAL SUMS", "n", takeaway = "A series is a tower built one brick at a time; the only question is whether it stops growing."),
            TourNode("THE HALVING ROOM", -2.4f, -0.2f, -32f, 2.8f, VIOLET, 1.0, Scene.HALVING_ROOM, Amb.INFINITE, 36.7f, 25f, "GEOMETRIC", "GEOMETRIC", "gap", takeaway = "Halve the remaining distance forever and you fill exactly the room, never more."),
            TourNode("THE SLOW CLIMB", 2.0f, 0.4f, -48f, 3.8f, VIOLET, 1.0, Scene.SLOW_CLIMB, Amb.INFINITE, 50.0f, 25f, "HARMONIC", "HARMONIC", "n", takeaway = "Terms that shrink to nothing can still add up to everything."),
            TourNode("THE TEST", -2.6f, 0.0f, -64f, 3.2f, SILVER, 1.0, Scene.TEST, Amb.INFINITE, 63.3f, 25f, "COMPARISON", "COMPARISON", "n", takeaway = "If my bricks all hide under a tower I already trust, I am safe."),
            TourNode("THE ALTERNATING WALK", 2.4f, -0.3f, -80f, 2.4f, SILVER, 1.0, Scene.ALTERNATING_WALK, Amb.INFINITE, 76.7f, 25f, "ALTERNATING", "ALTERNATING", "error", takeaway = "Step forward, step back a little less, and the next step is my error bar."),
            TourNode("THE MATCHING CURVES", -2.0f, 0.3f, -96f, 3.6f, SILVER, 1.0, Scene.MATCHING_CURVES, Amb.CURVE, 90.0f, 25f, "TAYLOR", "TAYLOR", "order", takeaway = "I can force a polynomial to agree with a curve to any order I like, at one point."),
            TourNode("THE PRICE OF AGREEMENT", 2.2f, 0.2f, -112f, 3.4f, SILVER, 1.0, Scene.PRICE_OF_AGREEMENT, Amb.CURVE, 90.0f, 70f, "REMAINDER", "REMAINDER", "error", takeaway = "The error is not a vague worry; it is a thickness I can see and bound."),
            TourNode("THE EDGE OF THE WORLD", -2.4f, -0.2f, -128f, 3.8f, VIOLET_DEEP, 1.0, Scene.EDGE_OF_WORLD, Amb.LIMIT, 76.7f, 70f, "RADIUS", "RADIUS", "|x\u2212a|", takeaway = "Every one of these series has a distance beyond which it stops meaning anything."),
            TourNode("THE WAVE FROM POWERS", 2.0f, 0.3f, -144f, 3.4f, VIOLET_DEEP, 1.0, Scene.WAVE_FROM_POWERS, Amb.CURVE, 63.3f, 70f, "sin SERIES", "sin SERIES", "order", takeaway = "A wave can be built out of nothing but odd powers, if you use enough of them."),
            TourNode("THE MEETING", -2.2f, 0.0f, -160f, 3.2f, VIOLET_DEEP, 1.0, Scene.MEETING, Amb.INFINITE, 50.0f, 70f, "e^{i\u03b8}", "e^{i\u03b8}", "\u03b8", takeaway = "The exponential and the circle are the same object seen from two sides."),
            TourNode("THE VIEW FROM OUTSIDE", 1.6f, -0.2f, -178f, 9.0f, VIOLET_DEEP, 1.0, Scene.OUTSIDE, Amb.LOOKBACK, 36.7f, 70f, "THE WHOLE TOUR", "THE WHOLE TOUR", "n", 0.0f, takeaway = "Infinitely many pieces, and a definite answer, as long as I stay inside the edge."),
        ),
        lengthKeys = floatArrayOf(0f, 2f, 2.6f, 6f, 7f, 8f, 11f),
        lengthM = doubleArrayOf(1.0, 1.0, 1e-6, 1e-6, 1e-1, 1e-6, 1e-6),
        armStops = floatArrayOf(2.05f, 6.05f, 8.05f, 10.05f),
        ambient = Scene.AMBIENT_TRACE,
        trace = { p -> 1.5f + 0.70f * kotlin.math.sin(p * 0.80f) }
    )

    /**
     * TOUR V — THE OPEN COUNTRY. The tube goes ghost and the real scenery is a landscape whose
     * height is a function of where you stand. The rail is a path through the (x, z) plane.
     */
    val COUNTRY = TourMap(
        id = 5, title = "THE OPEN COUNTRY", subtitle = "A HEIGHT FOR EVERY PLACE  \u00b7  13 STOPS",
        hudTitle = "MATHCOSMOS V \u00b7 THE OPEN COUNTRY", scriptAsset = "tour5_script.json", durationLabel = "33 MIN",
        nodes = listOf(
            TourNode("THE LANDSCAPE", 0.0f, 0.0f, 0f, 4.2f, TEAL, 1.0, Scene.LANDSCAPE, Amb.SURFACE, 10.0f, 25f, "z = f(x,y)", "z = f(x,y)", "cell", 0.3f, takeaway = "One number for every place on the ground is a landscape, and I can fly it."),
            TourNode("THE TWO CUTS", 2.2f, 0.3f, -16f, 4.0f, TEAL, 1.0, Scene.TWO_CUTS, Amb.SURFACE, 23.3f, 25f, "PARTIALS", "PARTIALS", "cell", 0.22f, takeaway = "Hold one direction still and I am back to a single curve I already know."),
            TourNode("THE PLATE", -2.4f, -0.2f, -32f, 4.0f, TEAL, 1.0, Scene.PLATE, Amb.SURFACE, 36.7f, 25f, "TANGENT PLANE", "TANGENT PLANE", "cell", 0.22f, takeaway = "Close enough in, a smooth landscape is a flat sheet resting on two needles."),
            TourNode("THE COMPASS", 2.0f, 0.4f, -48f, 4.2f, TEAL, 1.0, Scene.COMPASS, Amb.SURFACE, 50.0f, 25f, "GRADIENT", "GRADIENT", "cell", 0.18f, takeaway = "The gradient is an arrow lying flat on the ground pointing straight uphill."),
            TourNode("THE CONTOURS", -2.6f, 0.0f, -64f, 4.2f, TEAL, 1.0, Scene.CONTOURS, Amb.SURFACE, 63.3f, 25f, "LEVEL SETS", "LEVEL SETS", "cell", 0.18f, takeaway = "Walk a contour and you never climb, and the uphill arrow is always square to your path."),
            TourNode("THE ANY-DIRECTION", 2.4f, -0.3f, -80f, 4.0f, TEAL_PALE, 1.0, Scene.ANY_DIRECTION, Amb.SURFACE, 76.7f, 25f, "DIRECTIONAL", "DIRECTIONAL", "cell", 0.18f, takeaway = "Whatever heading I pick, the steepness I feel is the gradient's shadow on it."),
            TourNode("THE PASS", -2.0f, 0.3f, -96f, 4.2f, TEAL_PALE, 1.0, Scene.PASS, Amb.SURFACE, 90.0f, 25f, "SADDLE", "SADDLE", "cell", 0.18f, takeaway = "A place can be a summit one way and a valley the other."),
            TourNode("THE TETHER", 2.2f, 0.2f, -112f, 4.0f, TEAL_PALE, 1.0, Scene.TETHER, Amb.SURFACE, 90.0f, 70f, "LAGRANGE", "LAGRANGE", "cell", 0.18f, takeaway = "The best point along a fence is where the fence just grazes a contour."),
            TourNode("THE COLUMN FIELD", -2.4f, -0.2f, -128f, 4.2f, TEAL_PALE, 1.0, Scene.COLUMN_FIELD, Amb.SUM, 76.7f, 70f, "DOUBLE INTEGRAL", "DOUBLE INTEGRAL", "cell", 0.18f, takeaway = "The volume under a landscape is a floor of columns."),
            TourNode("THE ORDER", 2.0f, 0.3f, -144f, 4.0f, TEAL_DEEP, 1.0, Scene.ORDER, Amb.SUM, 63.3f, 70f, "FUBINI", "FUBINI", "cell", 0.18f, takeaway = "Row by row or column by column, I get the same total."),
            TourNode("THE STRETCHED GROUND", -2.2f, 0.0f, -160f, 4.2f, TEAL_DEEP, 1.0, Scene.STRETCHED_GROUND, Amb.SURFACE, 50.0f, 70f, "JACOBIAN", "JACOBIAN", "cell", 0.18f, takeaway = "Bend the grid and every cell changes area by a factor I can measure."),
            TourNode("THE ROUGH PLACE", 1.6f, -0.2f, -176f, 3.8f, TEAL_DEEP, 1.0, Scene.ROUGH_PLACE, Amb.SURFACE, 36.7f, 70f, "NOT SMOOTH", "NOT SMOOTH", "cell", 0.22f, takeaway = "Both slopes can exist and the surface can still be a cliff."),
            TourNode("THE VIEW FROM OUTSIDE", -1.8f, 0.2f, -194f, 9.0f, TEAL_DEEP, 1.0, Scene.OUTSIDE, Amb.LOOKBACK, 23.3f, 70f, "THE WHOLE TOUR", "THE WHOLE TOUR", "cell", 0.0f, takeaway = "That whole country was one function of two numbers."),
        ),
        lengthKeys = floatArrayOf(0f, 2f, 3f, 8f, 9f, 12f),
        lengthM = doubleArrayOf(1.0, 1.0, 0.25, 0.25, 0.06, 0.03),
        armStops = floatArrayOf(1.05f, 2.05f, 3.05f, 6.05f, 7.05f, 10.05f),
        ambient = Scene.AMBIENT_COUNTRY,
        terrain = { x, z -> 1.15f * kotlin.math.sin(x * 0.30f) + 0.95f * kotlin.math.sin(z * 0.085f) + 0.55f * kotlin.math.sin(x * 0.13f + z * 0.05f) }
    )

    /**
     * TOUR VI — THE FIELD AND THE FLOW. Stop steering: the field carries the craft. The tube is a
     * guide-rail only, and the drifting streaks advect along the field being studied.
     */
    val FLOW = TourMap(
        id = 6, title = "THE FIELD AND THE FLOW", subtitle = "WHAT THE RIM DECIDES  \u00b7  13 STOPS",
        hudTitle = "MATHCOSMOS VI \u00b7 THE FIELD AND THE FLOW", scriptAsset = "tour6_script.json", durationLabel = "34 MIN",
        nodes = listOf(
            TourNode("THE ARROW AT EVERY POINT", 0.0f, 0.0f, 0f, 4.2f, CYAN, 1.0, Scene.ARROW_FIELD, Amb.FIELD, 10.0f, 25f, "VECTOR FIELD", "VECTOR FIELD", "probe", 0.15f, takeaway = "A field is an arrow at every place, and I can feel it in the hull."),
            TourNode("THE STREAMLINE", 2.2f, 0.3f, -16f, 4.0f, CYAN, 1.0, Scene.STREAMLINE, Amb.FIELD, 23.3f, 25f, "FLOW LINES", "FLOW LINES", "probe", 0.15f, takeaway = "Let go and the field draws my path for me."),
            TourNode("THE PROBES OUT", -2.4f, -0.2f, -32f, 4.0f, CYAN, 1.0, Scene.PROBES_OUT, Amb.FIELD, 36.7f, 25f, "DIVERGENCE", "DIVERGENCE", "box", 0.15f, takeaway = "Divergence is what a box gains or loses."),
            TourNode("THE PADDLE WHEEL", 2.0f, 0.4f, -48f, 4.0f, CYAN, 1.0, Scene.PADDLE_WHEEL, Amb.FIELD, 50.0f, 25f, "CURL", "CURL", "wheel", 0.15f, takeaway = "If the wheel spins there is curl, and the axis it spins about is the arrow."),
            TourNode("THE DOWNHILL FIELD", -2.6f, 0.0f, -64f, 4.2f, CYAN, 1.0, Scene.DOWNHILL_FIELD, Amb.SURFACE, 63.3f, 25f, "CONSERVATIVE", "CONSERVATIVE", "work", 0.18f, takeaway = "Some fields are a landscape's uphill arrows, and then a round trip costs nothing."),
            TourNode("THE LOOP AND THE SHEET", 2.4f, -0.3f, -80f, 4.0f, ORANGE_WARM, 1.0, Scene.LOOP_AND_SHEET, Amb.FIELD, 76.7f, 25f, "GREEN", "GREEN", "cell", 0.15f, takeaway = "All the spin inside a patch is exactly the push around its edge."),
            TourNode("THE BAG AND ITS SKIN", -2.0f, 0.3f, -96f, 4.2f, ORANGE_WARM, 1.0, Scene.BAG_AND_SKIN, Amb.FIELD, 90.0f, 25f, "DIVERGENCE THM", "DIVERGENCE THM", "box", 0.15f, takeaway = "Everything made inside has to cross the skin to get out."),
            TourNode("THE RIM", 2.2f, 0.2f, -112f, 4.2f, ORANGE_WARM, 1.0, Scene.RIM, Amb.FIELD, 90.0f, 70f, "STOKES", "STOKES", "cell", 0.15f, takeaway = "Stretch the sheet however you like; only the rim decides the answer."),
            TourNode("THE SLOPE FIELD", -2.4f, -0.2f, -128f, 3.8f, ORANGE_WARM, 1.0, Scene.SLOPE_FIELD, Amb.SOLVE, 76.7f, 70f, "ODEs", "ODEs", "h", 0.2f, takeaway = "A differential equation is a field of little slopes, and a solution obeys all of them."),
            TourNode("THE PULL HOME", 2.0f, 0.3f, -144f, 3.6f, CYAN_DEEP, 1.0, Scene.PULL_HOME, Amb.SOLVE, 63.3f, 70f, "STABILITY", "STABILITY", "offset", 0.2f, takeaway = "Some answers pull you in and some throw you out, and the field tells you which."),
            TourNode("THE SPRING AND THE CIRCLE", -2.2f, 0.0f, -160f, 3.8f, CYAN_DEEP, 1.0, Scene.SPRING_AND_CIRCLE, Amb.SOLVE, 50.0f, 70f, "PHASE PLANE", "PHASE PLANE", "t", 0.2f, takeaway = "A swing's position and its speed, taken together, travel in a circle."),
            TourNode("THE SPREADING", 1.6f, -0.2f, -176f, 4.2f, CYAN_DEEP, 1.0, Scene.SPREADING, Amb.SURFACE, 36.7f, 70f, "PDEs", "PDEs", "grid", 0.2f, takeaway = "A rule about neighbours, run forward, is why heat smooths out and waves travel."),
            TourNode("THE VIEW FROM OUTSIDE", -1.8f, 0.2f, -194f, 9.0f, CYAN_DEEP, 1.0, Scene.OUTSIDE, Amb.LOOKBACK, 23.3f, 70f, "THE WHOLE SERIES", "THE WHOLE SERIES", "unit", 0.0f, takeaway = "One rod. One cut. One sum. That is all it ever was."),
        ),
        lengthKeys = floatArrayOf(0f, 2f, 3f, 8f, 9f, 12f),
        lengthM = doubleArrayOf(1.0, 1.0, 0.25, 0.25, 1e-2, 1e-3),
        armStops = floatArrayOf(2.05f, 3.05f, 4.05f, 6.05f, 7.05f),
        ambient = Scene.AMBIENT_FIELD,
        terrain = { x, z -> 1.15f * kotlin.math.sin(x * 0.30f) + 0.95f * kotlin.math.sin(z * 0.085f) + 0.55f * kotlin.math.sin(x * 0.13f + z * 0.05f) },
        // A swirl about the rail plus a steady drift along it: enough curl to spin a paddle wheel,
        // enough divergence near the sources to fill a box, and never zero anywhere the craft flies.
        field = { x, y, z, out ->
            val cx = x
            val cy = y
            out[0] = -0.45f * cy + 0.25f * kotlin.math.sin(z * 0.05f)
            out[1] = 0.45f * cx
            out[2] = -0.80f + 0.20f * kotlin.math.sin(z * 0.033f)
        }
    )

    val ALL = listOf(GROUND, APPROACH, ACCUMULATION, INFINITE, COUNTRY, FLOW, PROVING)
    fun byId(id: Int): TourMap = ALL.firstOrNull { it.id == id } ?: GROUND
}
