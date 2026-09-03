package com.rayneo.mathcosmos

/**
 * Stop 5 — THE RIGHT ANGLE. "The square on the long side is exactly the other two poured in."
 *
 * Pythagoras by conservation of stuff, not by algebra. A 3-4-5 triangle stands in the passage with
 * a square erected on each of its three sides. The two small squares hold nine grains and sixteen
 * grains — one per unit tile, so they can be counted, not taken on trust — and on a beat they
 * dissolve and stream across into the big square, which holds exactly twenty-five and fills with
 * nothing left over and no gap.
 *
 * One grain per tile is the whole design decision here. A dense cloud of a few hundred particles
 * would look more impressive and would prove nothing; twenty-five countable grains let a viewer
 * verify the theorem with their own eyes in the time it takes to say it. This is also why the
 * triangle is 3-4-5 and not a generic one: the picture has to be checkable.
 *
 * The grains are drawn as small filled squares in the triangle buffer rather than as lit spheres —
 * twenty-five spheres would be twenty-five draw calls, and the whole scene has a budget of about
 * thirty.
 */
object SceneRightAngle : MathScene {

    override val reach = 1.5f
    override val focusSide = -1.0f
    override val focusUp = 0.15f
    override val focusRadius = 1.5f

    private const val A = 3
    private const val B = 4
    private const val C = 5
    private const val U = 0.20f
    private const val PERIOD = 28f
    private const val GRAIN = 0.30f        // grain size as a fraction of a tile

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)
    // Where each of the 25 grains starts and ends, in figure coordinates. Filled once.
    private val fromS = FloatArray(25)
    private val fromU = FloatArray(25)
    private val toS = FloatArray(25)
    private val toU = FloatArray(25)
    private var built = false

    private const val OFF_S = -1.6f * U
    private const val OFF_U = -2.0f * U

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val moved = ((c - 0.26f) / 0.40f).coerceIn(0f, 1f)
        return "GRAINS ${(moved * 25).toInt()} / 25   (9 + 16)"
    }

    /**
     * The grain layout. The two small squares' tile centres are the sources; the big square's
     * twenty-five tile centres are the destinations, in the hypotenuse square's own frame.
     */
    private fun build() {
        if (built) return
        var k = 0
        // Square on the short leg: hangs below the s axis, from (0,-3) to (3,0).
        for (j in 0 until A) for (i2 in 0 until A) {
            fromS[k] = i2 + 0.5f; fromU[k] = -(j + 0.5f); k++
        }
        // Square on the long leg: to the left of the u axis, from (-4,0) to (0,4).
        for (j in 0 until B) for (i2 in 0 until B) {
            fromS[k] = -(i2 + 0.5f); fromU[k] = j + 0.5f; k++
        }
        // Square on the hypotenuse: erected outward on the segment (A,0) -> (0,B).
        // Its own axes are d (along the hypotenuse) and nrm (outward), both unit length.
        val dS = (0f - A) / C; val dU = (B - 0f) / C
        val nS = dU; val nU = -dS                  // right-hand normal, which points away from the origin
        k = 0
        for (j in 0 until C) for (i2 in 0 until C) {
            val p = i2 + 0.5f
            val q = j + 0.5f
            toS[k] = A + dS * p + nS * q
            toU[k] = 0f + dU * p + nU * q
            k++
        }
        built = true
    }

    /** A filled grain at a figure-space point. */
    private fun grain(tri: FloatArray, v: Int, s: Float, u: Float, c: FloatArray, alpha: Float): Int {
        val h = GRAIN * 0.5f * U
        SceneParts.at(g, OFF_S + s * U - h, OFF_U + u * U - h, 0f, o)
        SceneParts.vec(g, GRAIN * U, 0f, 0f, du)
        SceneParts.vec(g, 0f, GRAIN * U, 0f, dv)
        return MathMesh.quad(tri, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            c[0], c[1], c[2], alpha)
    }

    private fun square(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        s: Float, u: Float, us: Float, uu: Float, vs: Float, vu: Float,
        c: FloatArray, alpha: Float, nu: Int
    ): Int {
        SceneParts.at(g, OFF_S + s * U, OFF_U + u * U, 0f, o)
        SceneParts.vec(g, us * U, uu * U, 0f, du)
        SceneParts.vec(g, vs * U, vu * U, 0f, dv)
        return SceneParts.pane(kit, line, lv, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha, nu, nu)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build()
        SceneParts.stage(kit, i.toFloat(), -1.0f, 0.15f, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // The pour: each grain leaves a little after the one before, so it streams rather than
        // teleports as a block.
        val pour = ((c - 0.26f) / 0.40f).coerceIn(0f, 1f)

        // --- the three squares --------------------------------------------------------------
        v = square(kit, line, v, tri, 0f, -A.toFloat(), A.toFloat(), 0f, 0f, A.toFloat(),
            SceneParts.WORK, 0.9f, A)
        v = square(kit, line, v, tri, -B.toFloat(), 0f, B.toFloat(), 0f, 0f, B.toFloat(),
            SceneParts.WORK_DIM, 0.9f, B)
        // The hypotenuse square, in its own rotated frame.
        val dS = (0f - A) / C.toFloat(); val dU = (B - 0f) / C.toFloat()
        v = square(kit, line, v, tri, A.toFloat(), 0f,
            dS * C, dU * C, dU * C, -dS * C, SceneParts.ADDED, 0.9f, C)

        // --- the triangle itself, drawn last of the outlines so it reads on top ---------------
        SceneParts.at(g, OFF_S, OFF_U, 0.004f, o)
        SceneParts.at(g, OFF_S + A * U, OFF_U, 0.004f, du)
        SceneParts.at(g, OFF_S, OFF_U + B * U, 0.004f, dv)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
            SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], dv[0], dv[1], dv[2],
            SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f)
        v = MathMesh.segment(line, v, du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f)

        // --- the grains ----------------------------------------------------------------------
        for (k in 0 until 25) {
            // Stagger: grain k starts moving at k/25 of the pour and takes a third of it.
            val t = SceneParts.ease((pour - k / 25f * 0.55f) / 0.42f)
            // A lifted control point, so the stream arcs across rather than sliding through the
            // triangle it is supposed to be flowing around.
            val s0 = fromS[k]; val u0 = fromU[k]
            val s1 = toS[k]; val u1 = toU[k]
            val mid = 4f * t * (1f - t)
            val s = s0 + (s1 - s0) * t + mid * 0.9f
            val u = u0 + (u1 - u0) * t + mid * 0.9f
            val col = if (k < 9) SceneParts.WORK else SceneParts.WORK_DIM
            v = grain(tri, tv[0], s, u, col, 1f).let { tv[0] = it; v }
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- notation ------------------------------------------------------------------------
        val gl = 0.22f
        SceneParts.at(g, OFF_S + A * 0.5f * U, OFF_U - A * 0.5f * U, 0f, o)
        kit.text("9", o[0], o[1], o[2], gl, SceneParts.WORK, 0.9f, GlyphBoard.Style.PLAIN)
        SceneParts.at(g, OFF_S - B * 0.5f * U, OFF_U + B * 0.5f * U, 0f, o)
        kit.text("16", o[0], o[1], o[2], gl, SceneParts.WORK_DIM, 0.9f, GlyphBoard.Style.PLAIN)
        SceneParts.at(g, OFF_S + (A + (dS + dU) * C * 0.5f) * U, OFF_U + ((dU - dS) * C * 0.5f) * U, 0f, o)
        kit.text("25", o[0], o[1], o[2], gl * 1.15f, SceneParts.ADDED, 0.95f, GlyphBoard.Style.PLAIN)

        if (kit.quality == 0) {
            SceneParts.at(g, OFF_S + A * 0.5f * U, OFF_U + 0.16f, 0f, o)
            kit.text("3", o[0], o[1], o[2], gl * 0.8f, SceneParts.HOT, 0.8f, GlyphBoard.Style.SMALL)
            SceneParts.at(g, OFF_S + 0.14f, OFF_U + B * 0.5f * U, 0f, o)
            kit.text("4", o[0], o[1], o[2], gl * 0.8f, SceneParts.HOT, 0.8f, GlyphBoard.Style.SMALL)
            SceneParts.at(g, OFF_S + (A * 0.5f) * U + 0.10f, OFF_U + (B * 0.5f) * U + 0.10f, 0f, o)
            kit.text("5", o[0], o[1], o[2], gl * 0.8f, SceneParts.HOT, 0.8f, GlyphBoard.Style.SMALL)
        }
    }
}
