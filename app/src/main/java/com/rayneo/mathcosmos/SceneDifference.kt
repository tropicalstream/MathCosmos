package com.rayneo.mathcosmos

/**
 * Stop 4 — THE DIFFERENCE OF SQUARES. "a² − b² is a frame, and a frame unrolls into a strip."
 *
 * A square with a smaller square lifted bodily out of one corner. What is left is an L, and an L
 * cut once and rearranged is a plain rectangle whose two edges are (a − b) and (a + b). That is
 * the entire identity, and it is a piece of joinery rather than a manipulation of symbols.
 *
 * a = 4, b = 1, so the pieces are 3×4 and 1×3, the rearranged rectangle is 3×5, and the tile count
 * is 16 − 1 = 15 = 3 × 5 — checkable by eye. The two limbs are deliberately NOT congruent, so the
 * quarter turn one of them makes is a real rotation and not a slide dressed up as one.
 */
object SceneDifference : MathScene {

    override val reach = 1.4f
    override val focusSide = -1.25f
    override val focusUp = 0.15f
    override val focusRadius = 1.15f

    private const val A = 4f
    private const val B = 1f
    private const val U = 0.30f
    private const val PERIOD = 24f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    private const val OFF_S = -(A - B) * 0.5f * U      // the rectangle is (A-B) wide when finished
    private const val OFF_U = -(A + B) * 0.5f * U      // ...and (A+B) tall

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val n = (A * A - B * B).toInt()
        return if (c < 0.46f) "AREA ${(A * A).toInt()} − ${(B * B).toInt()} = $n"
        else "AREA ${(A - B).toInt()} × ${(A + B).toInt()} = $n"
    }

    private fun pieceAt(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        s: Float, u: Float, us: Float, uu: Float, vs: Float, vu: Float,
        c: FloatArray, alpha: Float, nu: Int, nv: Int, liftAlong: Float = 0f
    ): Int {
        SceneParts.at(g, OFF_S + s * U, OFF_U + u * U, liftAlong, o)
        SceneParts.vec(g, us * U, uu * U, 0f, du)
        SceneParts.vec(g, vs * U, vu * U, 0f, dv)
        return SceneParts.pane(
            kit, line, lv, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha, nu, nv
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), -1.25f, 0.15f, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val lift = SceneParts.step(c, 0.18f, 0.16f)
        val cut = SceneParts.step(c, 0.36f, 0.10f)
        val move = SceneParts.step(c, 0.48f, 0.24f)

        // --- the tall limb: (a − b) wide, a tall. It never moves. --------------------------
        v = pieceAt(kit, line, v, tri, 0f, 0f, A - B, 0f, 0f, A,
            SceneParts.WORK, 0.95f, (A - B).toInt(), A.toInt())

        // --- the short limb: b wide, (a − b) tall. It turns a quarter circle and goes on top. -
        // Start: sitting to the right of the tall limb, at the bottom.
        // End:   lying across the top of the tall limb, (a − b) wide and b tall.
        val ang = move * 90f
        val ss = (A - B) + (0f - (A - B)) * move
        val su = 0f + (A - 0f) * move + (A - B) * move   // its corner ends up at the top-left, rotated
        val arc = (1f - kotlin.math.abs(move * 2f - 1f)) * 0.5f
        SceneParts.at(g, OFF_S + ss * U, OFF_U + (0f + (A + B - B) * move) * U, arc * U, o)
        SceneParts.turn(g, B * U, 0f, ang, du)
        SceneParts.turn(g, 0f, (A - B) * U, ang, dv)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.WORK_DIM, 0.95f, 1, (A - B).toInt()
        )

        // --- the square that was taken out -------------------------------------------------
        // It rises and hangs above the work, still glowing, because a subtraction that vanishes
        // off screen is a subtraction the viewer has to take on trust.
        if (lift > 0.001f) {
            val high = lift * 2.2f
            SceneParts.at(g, OFF_S + (A - B) * U, OFF_U + (A - B + high) * U, 0f, o)
            SceneParts.vec(g, B * U, 0f, 0f, du)
            SceneParts.vec(g, 0f, B * U, 0f, dv)
            v = SceneParts.pane(
                kit, line, v, tri, tv, o[0], o[1], o[2],
                du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.TAKEN, 0.95f, 1, 1
            )
        } else {
            // Before the lift, its outline sits in the corner of the whole square.
            v = pieceAt(kit, line, v, tri, A - B, A - B, B, 0f, 0f, B, SceneParts.TAKEN, 0.9f, 1, 1)
        }

        // --- the cut ------------------------------------------------------------------------
        if (cut > 0.02f && move < 0.04f) {
            SceneParts.at(g, OFF_S + (A - B) * U, OFF_U, 0f, o)
            SceneParts.at(g, OFF_S + (A - B) * U, OFF_U + (A - B) * U * cut, 0f, du)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f)
        }

        kit.flushLines(v, 2.3f)
        kit.flushTris(tv[0])

        // --- notation -----------------------------------------------------------------------
        val gl = 0.24f
        if (move > 0.85f) {
            SceneParts.at(g, OFF_S + (A - B) * U * 0.5f, OFF_U - 0.28f, 0f, o)
            kit.text("a − b", o[0], o[1], o[2], gl, SceneParts.WORK, 1f)
            SceneParts.at(g, OFF_S - 0.34f, OFF_U + (A + B) * U * 0.5f, 0f, o)
            kit.text("a + b", o[0], o[1], o[2], gl, SceneParts.WORK, 1f)
        } else {
            SceneParts.at(g, OFF_S + A * U * 0.5f, OFF_U - 0.28f, 0f, o)
            kit.text("a", o[0], o[1], o[2], gl, SceneParts.WORK, 1f)
        }
        if (lift > 0.4f) {
            SceneParts.at(g, OFF_S + (A - B + B * 0.5f) * U, OFF_U + (A - B + lift * 2.2f + B + 0.4f) * U, 0f, o)
            kit.text("b^2", o[0], o[1], o[2], gl * 0.9f, SceneParts.TAKEN, 1f)
        }
        SceneParts.at(g, OFF_S + (A - B) * U + 0.30f, OFF_U + (A + B) * U * 0.5f, 0f, o)
        kit.text(if (move > 0.85f) "(a − b)(a + b)" else "a^2 − b^2",
            o[0], o[1], o[2], gl * 0.62f, SceneParts.HOT, 1f, GlyphBoard.Style.MATH, 1.15f, anchor = -0.5f)
    }
}
