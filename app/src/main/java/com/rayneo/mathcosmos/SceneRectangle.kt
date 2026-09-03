package com.rayneo.mathcosmos

/**
 * Stop 2 — THE RECTANGLE. "Multiplying is making a rectangle, and splitting the rectangle is the
 * distributive law."
 *
 * a(b + c) = ab + ac, as one piece of area cut in two. The plate straddles the passage with the
 * cut exactly on the rail, so the craft flies THROUGH the cut as the two halves draw apart — the
 * viewer passes along the line the identity is about.
 *
 * The plate is ruled into unit tiles throughout, because the argument is not that the two sides
 * are equal by rearrangement of symbols; it is that the tiles were never added to or taken away.
 * Ten tiles before, six and four after. A viewer can count them, and at this stage of the tour
 * counting is the only proof anyone should be asked to accept.
 *
 * a = 2, b = 3, c = 2 — small enough to count at a glance in a 640-pixel eye, and deliberately
 * not equal, so nothing about the picture can be mistaken for a special case.
 */
object SceneRectangle : MathScene {

    override val reach = 1.4f
    override val focusSide = -1.15f
    override val focusUp = 0.25f
    override val focusRadius = 1.15f

    private const val A = 2
    private const val B = 3
    private const val C = 2
    private const val U = 0.34f            // world units per unit of the rectangle
    private const val PERIOD = 20f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return if (c < 0.42f) "AREA ${A * (B + C)}" else "AREA ${A * B} + ${A * C} = ${A * (B + C)}"
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        SceneParts.stage(kit, at, -1.15f, 0.25f, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val cut = SceneParts.step(c, 0.22f, 0.16f)      // the cut line sweeps down
        val part = SceneParts.step(c, 0.42f, 0.20f)     // the halves draw apart
        val gap = part * 0.55f

        val h = A * U
        val bottom = -h * 0.5f

        // --- the two pieces ----------------------------------------------------------------
        // Before the cut they are edge to edge and read as one plate; the ruling runs across the
        // join so nothing gives away where it will be cut until the light does.
        SceneParts.vec(g, 1f, 0f, 0f, du)
        SceneParts.vec(g, 0f, 1f, 0f, dv)

        // left piece: a by b, its right edge on the rail, sliding left as they part
        SceneParts.at(g, -B * U - gap, bottom, 0f, o)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0] * B * U, du[1] * B * U, du[2] * B * U,
            dv[0] * h, dv[1] * h, dv[2] * h,
            SceneParts.WORK, 0.95f, B, A
        )
        // right piece: a by c
        SceneParts.at(g, gap, bottom, 0f, o)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0] * C * U, du[1] * C * U, du[2] * C * U,
            dv[0] * h, dv[1] * h, dv[2] * h,
            if (part > 0.02f) SceneParts.ADDED else SceneParts.WORK, 0.95f, C, A
        )

        // --- the cut ------------------------------------------------------------------------
        // A line of light travelling down the join. It exists only while it is doing its work.
        if (cut > 0.001f && part < 0.98f) {
            val top = bottom + h * (1f - cut)
            SceneParts.at(g, 0f, bottom + h, 0f, o)
            SceneParts.at(g, 0f, top, 0f, du)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f)
            SceneParts.vec(g, 1f, 0f, 0f, du)   // restore: du is the plane's right again
        }

        // --- the side that is being multiplied ----------------------------------------------
        // A rod down the left edge marking a, and one along the bottom marking b + c, so the two
        // factors are objects in the scene rather than numbers in a caption.
        SceneParts.at(g, -B * U - gap, bottom, 0f, o)
        SceneParts.at(g, -B * U - gap, bottom + h, 0f, dv)
        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])
        kit.rod(o[0], o[1], o[2], dv[0], dv[1], dv[2], 0.04f, SceneParts.CHALK, SceneParts.LAMP, 0.3f)

        // --- notation -----------------------------------------------------------------------
        val gl = 0.24f
        SceneParts.at(g, -B * U - gap - 0.34f, 0f, 0f, o)
        kit.text("a", o[0], o[1], o[2], gl, SceneParts.CHALK, 1f)

        SceneParts.at(g, -B * U * 0.5f - gap, bottom - 0.30f, 0f, o)
        kit.text("b", o[0], o[1], o[2], gl, SceneParts.WORK, 1f)
        SceneParts.at(g, C * U * 0.5f + gap, bottom - 0.30f, 0f, o)
        kit.text("c", o[0], o[1], o[2], gl, if (part > 0.02f) SceneParts.ADDED else SceneParts.WORK, 1f)

        // The claim, above the plate — and it changes only when the picture has already changed.
        SceneParts.at(g, C * U + gap + 0.30f, 0f, 0f, o)
        if (part < 0.5f) {
            kit.text("a(b + c)", o[0], o[1], o[2], gl * 0.62f, SceneParts.HOT, 1f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        } else {
            kit.text("ab + ac", o[0], o[1], o[2], gl * 0.62f, SceneParts.HOT, 1f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        }
    }
}
