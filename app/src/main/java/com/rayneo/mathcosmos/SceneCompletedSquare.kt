package com.rayneo.mathcosmos

/**
 * Stop 3 — THE COMPLETED SQUARE. "Every quadratic is a square with a corner missing."
 *
 * The flagship of Tour I, and the reason the tour exists: the quadratic formula is not a thing to
 * memorise, it is the written record of this carpentry. x² + bx is a square with a strip stuck to
 * one edge. Cut the strip in half, swing one half round to the top, and what you have is almost a
 * bigger square — short by exactly one corner, (b/2)². Put that corner in and the shape is a
 * perfect square, (x + b/2)². Take the same corner back out of the total and the identity is
 * finished: x² + bx = (x + b/2)² − b²/4.
 *
 * Everything about the staging serves the missing corner. The corner arrives in its OWN colour and
 * keeps it, so the debt stays visible for the rest of the stop — a viewer who looks away and back
 * can still see which piece was not there to begin with. The pieces are ruled into unit tiles, so
 * 9 + 6 stays 15 and the completed square is visibly 16.
 *
 * x = 3, b = 2. Chosen so b/2 is a whole tile and the arithmetic can be checked by counting.
 */
object SceneCompletedSquare : MathScene {

    override val reach = 1.5f
    override val focusSide = SIDE
    override val focusUp = 0.25f
    override val focusRadius = 1.3f

    private const val X = 3f
    private const val B = 2f
    private const val HALF = B * 0.5f
    private const val U = 0.34f            // world units per unit of the figure
    private const val PERIOD = 26f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    // The figure is centred on the rail: the finished square runs 0..(X+HALF) in both directions,
    // so the whole thing is shifted back by half of that and the craft flies through its middle.
    /**
     * The figure hangs to one side of the rail rather than across it. A flat drawing centred on
     * the rail is a drawing you fly INTO: at the closest point of the pass the craft is inside it
     * and only a corner is in frame. Off to one side, and about a third smaller than it first
     * wanted to be, the whole square is visible for the whole of the approach.
     */
    private const val SIDE = -1.35f
    private const val OFF = -(X + HALF) * 0.5f * U

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val whole = ((X + HALF) * (X + HALF)).toInt()
        val start = (X * X + B * X).toInt()
        return when {
            c < 0.55f -> "AREA $start"
            c < 0.72f -> "AREA $start   SHORT ${(HALF * HALF).toInt()}"
            else -> "AREA $whole   BORROWED ${(HALF * HALF).toInt()}"
        }
    }

    /** A pane in figure coordinates: the origin and both spans are given in tiles, not world units. */
    private fun piece(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        s: Float, u: Float, us: Float, uu: Float, vs: Float, vu: Float,
        c: FloatArray, alpha: Float, nu: Int, nv: Int
    ): Int {
        SceneParts.at(g, OFF + s * U, OFF + u * U, 0f, o)
        SceneParts.vec(g, us * U, uu * U, 0f, du)
        SceneParts.vec(g, vs * U, vu * U, 0f, dv)
        return SceneParts.pane(
            kit, line, lv, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha, nu, nv
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), SIDE, 0.25f, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val split = SceneParts.step(c, 0.20f, 0.12f)
        val swing = SceneParts.step(c, 0.34f, 0.22f)
        val drop = SceneParts.step(c, 0.58f, 0.14f)
        val seated = SceneParts.step(c, 0.70f, 0.06f)

        // --- x squared ----------------------------------------------------------------------
        v = piece(kit, line, v, tri, 0f, 0f, X, 0f, 0f, X, SceneParts.WORK, 0.95f, X.toInt(), X.toInt())

        // --- the half of the strip that never moves -----------------------------------------
        v = piece(kit, line, v, tri, X, 0f, HALF, 0f, 0f, X, SceneParts.WORK_DIM, 0.95f, 1, X.toInt())

        // --- the half that swings up and over ------------------------------------------------
        // It starts on the right edge, outboard of its twin, and lands along the top. Its spans
        // turn a quarter circle while its corner travels; interpolating both is what makes the
        // move read as a swing about the corner rather than a slide.
        val ang = swing * 90f
        val ss = (X + HALF) + (X - (X + HALF)) * swing        // corner: (X+HALF, 0) -> (X, X)
        val su = 0f + (X - 0f) * swing
        // A slight lift through the middle of the swing, so it clears the piece it is passing.
        val lift = (1f - kotlin.math.abs(swing * 2f - 1f)) * 0.45f
        SceneParts.at(g, OFF + ss * U, OFF + su * U, lift * U, o)
        SceneParts.turn(g, HALF * U, 0f, ang, du)
        SceneParts.turn(g, 0f, X * U, ang, dv)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.WORK_DIM, 0.95f, 1, X.toInt()
        )

        // --- the cut that made two halves of one strip ---------------------------------------
        if (split > 0.02f && swing < 0.04f) {
            SceneParts.at(g, OFF + (X + HALF) * U, OFF, 0f, o)
            SceneParts.at(g, OFF + (X + HALF) * U, OFF + X * U * split, 0f, du)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f)
        }

        // --- the corner that is missing --------------------------------------------------------
        // While the L is open, its empty corner is outlined in the colour of a debt, so the gap is
        // an object in the scene and not merely an absence.
        if (swing > 0.9f && drop < 0.98f) {
            SceneParts.at(g, OFF + X * U, OFF + X * U, 0f, o)
            SceneParts.vec(g, HALF * U, 0f, 0f, du)
            SceneParts.vec(g, 0f, HALF * U, 0f, dv)
            v = SceneParts.edge(line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.TAKEN, 0.55f + 0.35f * kotlin.math.sin(kit.seconds * 3f))
        }

        // --- and the corner arriving -----------------------------------------------------------
        if (drop > 0.001f) {
            val high = (1f - drop) * 2.6f
            SceneParts.at(g, OFF + X * U, OFF + X * U + high * U, 0f, o)
            SceneParts.vec(g, HALF * U, 0f, 0f, du)
            SceneParts.vec(g, 0f, HALF * U, 0f, dv)
            v = SceneParts.pane(
                kit, line, v, tri, tv, o[0], o[1], o[2],
                du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.ADDED, 0.95f, 1, 1
            )
        }

        kit.flushLines(v, 2.4f)
        kit.flushTris(tv[0])

        // The flash as it seats. One lamp at the corner, bright and brief.
        if (seated > 0.02f && seated < 0.98f) {
            SceneParts.at(g, OFF + (X + HALF * 0.5f) * U, OFF + (X + HALF * 0.5f) * U, 0f, o)
            val fl = 1f - seated
            kit.ball(o[0], o[1], o[2], 0.10f, 0.10f, 0.10f, SceneParts.HOT, SceneParts.ADDED,
                fl, 0f, 0f, 1f, 0f, 0f, 3f * fl)
        }

        // --- notation ----------------------------------------------------------------------
        val gl = 0.26f
        SceneParts.at(g, OFF + X * U * 0.5f, OFF - 0.30f, 0f, o)
        kit.text("x", o[0], o[1], o[2], gl, SceneParts.WORK, 1f)
        SceneParts.at(g, OFF - 0.32f, OFF + X * U * 0.5f, 0f, o)
        kit.text("x", o[0], o[1], o[2], gl, SceneParts.WORK, 1f)

        if (drop > 0.6f) {
            SceneParts.at(g, OFF + (X + HALF) * U + 0.30f, OFF + X * U, 0f, o)
            kit.text("b/2", o[0], o[1], o[2], gl * 0.85f, SceneParts.ADDED, 1f)
        }

        // The claim above the figure, in three beats, each one arriving only after the picture
        // has already made it true.
        SceneParts.at(g, OFF + (X + HALF) * U + 0.30f, OFF + (X + HALF) * U * 0.5f, 0f, o)
        val claim = when {
            c < 0.34f -> "x^2 + bx"
            c < 0.70f -> "x^2 + bx"
            else -> "(x + b/2)^2 − b^2/4"
        }
        kit.text(claim, o[0], o[1], o[2], gl * 0.62f, SceneParts.HOT, 1f, GlyphBoard.Style.MATH, 1.15f, anchor = -0.5f)
    }
}
