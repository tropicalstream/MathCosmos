package com.rayneo.mathcosmos

import kotlin.math.log2
import kotlin.math.pow

/**
 * Stop 12 — THE DOUBLING. "Anything that grows in proportion to itself will beat anything that
 * grows by adding, always, eventually."
 *
 * Two beads race down the corridor, one ring at a time. One climbs by a fixed amount every ring.
 * The other doubles. For the first four rings the adder is ahead — genuinely ahead, and it is
 * worth letting the viewer enjoy being wrong about where this is going — and then the doubler
 * leaves the world.
 *
 * The honest problem with this stop is that 2¹⁰ is a thousand and the picture cannot hold both
 * racers at a fixed scale. Rather than quietly rescaling and pretending nothing happened, the
 * vertical ruler is rescaled VISIBLY and its top mark is relabelled as it goes, so what the viewer
 * sees is the graph being repeatedly zoomed out and the adder shrinking toward the floor. That is
 * the true shape of the comparison, and hiding the rescale would be the dishonest choice.
 */
object SceneDoubling : MathScene {

    override val reach = 1.6f
    // The race runs five and a half units along the rail, so this one is framed wide and square on.
    override val focusSide = 0f
    override val focusUp = 0.05f
    override val focusRadius = 3.1f
    override val deep = 0.4f

    private const val RINGS = 10
    private const val STEP = 1f            // the adder's climb per ring
    private const val PERIOD = 26f
    private const val SPAN = 5.5f          // world units along the rail the race runs over
    private const val HIGH = 1.9f          // world units the vertical ruler is tall

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val a = FloatArray(3)
    private val b = FloatArray(3)

    private fun nAt(kit: SceneKit): Float = SceneParts.cycle(kit.seconds, PERIOD) * RINGS

    override fun readout(kit: SceneKit): String? {
        val n = nAt(kit)
        val add = STEP * n
        val dbl = 2f.pow(n)
        return "RING %.0f   ADD %.0f   DOUBLE %.0f".format(java.util.Locale.US, n, add, dbl)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), 0f, -0.9f, f, g)

        val line = kit.lineBuf
        var v = 0

        val now = nAt(kit)
        val dbl = 2f.pow(now)
        val add = STEP * now
        // The ruler always just holds the taller racer, with a floor so the opening is not absurd.
        val top = kotlin.math.max(dbl, 4f)
        val scale = HIGH / top
        val perRing = SPAN / RINGS

        // --- the floor: one tick per ring, which is the thing being counted ------------------
        for (k in 0..RINGS) {
            val s = -SPAN * 0.5f + k * perRing
            SceneParts.at(g, s, 0f, 0f, o)
            SceneParts.at(g, s, if (k.toFloat() <= now) 0.16f else 0.09f, 0f, a)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], a[0], a[1], a[2],
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2],
                if (k.toFloat() <= now) 0.9f else 0.35f)
        }
        SceneParts.at(g, -SPAN * 0.5f, 0f, 0f, o)
        SceneParts.at(g, SPAN * 0.5f, 0f, 0f, a)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], a[0], a[1], a[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.7f)

        // --- the vertical ruler, whose scale is changing under the viewer --------------------
        SceneParts.at(g, -SPAN * 0.5f, 0f, 0f, o)
        SceneParts.at(g, -SPAN * 0.5f, HIGH, 0f, a)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], a[0], a[1], a[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.7f)

        // --- the two tracks -------------------------------------------------------------------
        val steps = if (kit.quality == 0) 48 else 24
        // The adder: a straight line, and it stays a straight line however far out we zoom. That
        // is the point — it does not lose because it slows down, it loses because it never speeds up.
        v = MathMesh.curve(line, v, steps, 0f, now.coerceAtLeast(0.001f),
            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.95f, false, a, b) { t, out ->
            SceneParts.at(g, -SPAN * 0.5f + t * perRing, (STEP * t * scale).coerceAtMost(HIGH), 0f, out)
        }
        v = MathMesh.curve(line, v, steps, 0f, now.coerceAtLeast(0.001f),
            SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.95f, false, a, b) { t, out ->
            SceneParts.at(g, -SPAN * 0.5f + t * perRing, (2f.pow(t) * scale).coerceAtMost(HIGH), 0f, out)
        }
        kit.flushLines(v, 2.6f)

        // --- the racers -------------------------------------------------------------------------
        SceneParts.at(g, -SPAN * 0.5f + now * perRing, (add * scale).coerceAtMost(HIGH), 0f, o)
        kit.ball(o[0], o[1], o[2], 0.085f, 0.085f, 0.085f, SceneParts.COOL, SceneParts.HOT, 1f,
            0f, 0f, 1f, 0f, 0f, 0.8f)
        SceneParts.at(g, -SPAN * 0.5f + now * perRing, (dbl * scale).coerceAtMost(HIGH), 0f, a)
        kit.ball(a[0], a[1], a[2], 0.10f, 0.10f, 0.10f, SceneParts.WORK, SceneParts.HOT, 1f,
            0f, 0f, 1f, 0f, 0f, 1.4f)

        // --- notation -----------------------------------------------------------------------------
        val gl = 0.22f
        // The ruler's top mark is relabelled as the scale changes: this is the zoom, made visible.
        SceneParts.at(g, -SPAN * 0.5f - 0.12f, HIGH, 0f, o)
        kit.text("%.0f".format(java.util.Locale.US, top), o[0], o[1], o[2], gl,
            SceneParts.CHALK, 0.9f, GlyphBoard.Style.PLAIN, 1f, anchor = 0.5f)

        SceneParts.at(g, SPAN * 0.5f + 0.20f, 0.02f, 0f, o)
        kit.text("rings", o[0], o[1], o[2], gl * 0.85f, SceneParts.CHALK, 0.75f, GlyphBoard.Style.SMALL)

        SceneParts.at(g, -SPAN * 0.5f + now * perRing + 0.22f, (add * scale).coerceAtMost(HIGH), 0f, o)
        kit.text("+1", o[0], o[1], o[2], gl * 0.9f, SceneParts.COOL, 0.95f, GlyphBoard.Style.PLAIN, 1f, anchor = -0.5f)
        SceneParts.at(g, -SPAN * 0.5f + now * perRing + 0.22f, (dbl * scale).coerceAtMost(HIGH), 0f, o)
        kit.text("×2", o[0], o[1], o[2], gl * 0.9f, SceneParts.WORK, 0.95f, GlyphBoard.Style.PLAIN, 1.2f, anchor = -0.5f)

        // The crossing is the moment worth naming, and it is named only once it has happened.
        if (dbl > add && now > log2(1f.coerceAtLeast(STEP)) + 0.5f) {
            SceneParts.at(g, 0f, HIGH + 0.34f, 0f, o)
            kit.text("2^n", o[0], o[1], o[2], gl * 1.3f, SceneParts.WORK, 1f, GlyphBoard.Style.MATH, 1.2f)
        }
    }
}
