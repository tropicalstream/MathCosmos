package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tour III, stop 10 — THE RE-RULING. "Renaming the axis does not change how much is there — it
 * just changes how hard the sum looks."
 *
 * A ruler hangs beside the rail with the swept area standing on it in eight slabs, and a sealed
 * gauge stands at the outboard end holding the total. The ruler's marks then re-space themselves —
 * spacing du/dx, the log-ruler mechanism of Tour I generalised to any substitution — and the roof
 * over the slabs, re-drawn against the new marks, falls from a lopsided hill into something very
 * nearly level. Every slab changes shape. The gauge does not move.
 *
 * THE SUBSTITUTION IS u = x², ON [0, 1], AND THE INTEGRAND IS ITS OWN ACCOMPLICE. 2x·cos(x²) is a
 * shape you would not want to sum by hand; cos u is a shape you would. That is the entire content
 * of the change of variable, and it is worth choosing an example where the "hard" and the "plain"
 * are visibly different rather than a tidy one where both look the same. Both rulings run 0 to 1,
 * so the ends of the ruler are PINNED and only the interior re-spaces — which is what lets the eye
 * see the marks slide rather than the whole figure grow.
 *
 * HOW THE SLABS ARE BUILT, because this is where a picture of substitution usually cheats. Slab k
 * is not sampled. Its area is taken in closed form — ∫ 2x cos(x²) dx from x₀ to x₁ is sin(x₁²) −
 * sin(x₀²), which is sin(u₁) − sin(u₀), the SAME NUMBER read in either variable — and the slab is
 * then drawn with whatever width the current ruling gives it and the height that number demands.
 * So the conservation is not animated toward, it is structural: at every frame of the morph, at
 * every intermediate ruling that is neither x nor u, each slab's drawn width times its drawn
 * height is exactly the area it has always had. The heights that come out of this are mean heights
 * over each cell, so the slab tops straddle the plotted roof rather than touching it — the same
 * honest Riemann relationship the rest of this tour has been showing.
 *
 * THE GAUGE IS FED FROM THE PICTURE, NOT FROM THE ANSWER. Its fill is the sum of width × height of
 * the panes actually drawn this frame, and [REF] is a fixed hairline across the column at the level
 * a correctly-conserved sweep must reach. Nothing keeps the fill on that line except the geometry
 * being right; if the morph ever stopped conserving area you would watch the fill miss the mark.
 * That is the only way an invariant is worth putting on screen. It is drawn in ADDED, which is this
 * palette's colour for a quantity whose identity must stay visible, so it can be confused with
 * neither the amber area nor the cool ruler.
 *
 * TWO COMPROMISES, both said out loud by the crew. The design asks for the CORRIDOR's own rings to
 * re-space and for the bar to hang in the middle of the passage. The rings belong to the renderer
 * and a scene may not move them, so this is the ruler held up alongside them rather than the walls
 * themselves; and a bar on the rail is a bar you fly into, so the gauge stands at the outboard end
 * of the same instrument, where it is in frame beside the slabs for the whole approach instead of
 * whipping past at the closest point. The invariance is the stop, and the invariance has to be
 * watchable at the same time as the thing that is changing.
 *
 * The cycle re-rules and then un-rules, with a rest at each end. Going back matters: a viewer who
 * only ever sees the hard shape become the plain one can believe the plainness was bought. It was
 * not bought, it was a change of name, and the way to show that is to change the name back.
 */
object SceneReRuling : MathScene {

    /** One compact instrument beside the rail; it wants to be readable across the whole approach. */
    override val reach = 1.4f

    // ------------------------------------------------------------------ the substitution

    private const val N = 8                  // slabs at full quality
    private const val X_MIN = 0.004f         // first roof sample: keeps 0/0 off the left end at m = 1

    // ------------------------------------------------------------------ the loop

    private const val PERIOD = 22f
    private const val OUT_AT = 0.12f
    private const val OUT_LEN = 0.26f
    private const val BACK_AT = 0.66f
    private const val BACK_LEN = 0.16f

    // ------------------------------------------------------------------ where it hangs
    // World span comes out about 1.96 across and 1.0 tall, sitting from 0.27 to 2.24 out on one
    // side of the rail. Furthest corner is 2.51 from the rail centre, inside 0.8 of this stop's
    // 3.4 passage radius, and the whole of it is well under the roof trace at this node.

    private const val SIDE = -1.22f
    private const val UP = 0.14f
    private const val AX0 = -0.62f           // stage s of ruling position 0
    private const val AXW = 1.48f            // stage width of the whole ruling
    private const val HS = 0.72f             // stage height per unit of the integrand

    private const val GS0 = -1.02f           // the gauge column, outboard of the ruler
    private const val GW = 0.17f
    private const val GAUGE_H = 1.00f
    private const val BAR = 1.117f           // stage height per unit of swept area
    private const val REF = 0.94f            // where a conserved sweep must land: BAR × sin 1

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    // ------------------------------------------------------------------ the mathematics

    /** The integrand in x. Peaks at about 1.284 near x = 0.81, which sets the height scale. */
    private fun integrand(x: Float) = 2f * x * cos(x * x)

    /** ∫₀ˣ 2t cos(t²) dt = sin(x²). Closed form, so a slab's area is never a sampled guess. */
    private fun accum(x: Float) = sin(x * x)

    /** Where x sits on the ruler: even spacing at m = 0, du/dx spacing at m = 1. */
    private fun ruleAt(x: Float, m: Float) = x + m * (x * x - x)

    /** d(ruling)/dx — the local stretch, and therefore the divisor that flattens the roof. */
    private fun slope(x: Float, m: Float) = (1f - m) + 2f * m * x

    /** How many slabs at this quality. Eight is the picture; four still makes the argument. */
    private fun slabsFor(quality: Int) = if (quality == 0) N else N / 2

    /**
     * The sum of width × height over the slabs as they are currently drawn. Written as the product
     * rather than cancelled down on purpose: this is a measurement of the figure, not a restatement
     * of the closed form, and it is what drives both the gauge and the readout.
     */
    private fun swept(slabs: Int, m: Float): Float {
        var total = 0f
        for (k in 0 until slabs) {
            val x0 = k.toFloat() / slabs
            val x1 = (k + 1).toFloat() / slabs
            val w = ruleAt(x1, m) - ruleAt(x0, m)
            val h = (accum(x1) - accum(x0)) / w
            total += w * h
        }
        return total
    }

    private fun places4(v: Float): String {
        val t = (v * 10000f + 0.5f).toInt()
        val frac = t % 10000
        val pad = when { frac < 10 -> "000"; frac < 100 -> "00"; frac < 1000 -> "0"; else -> "" }
        return "${t / 10000}.$pad$frac"
    }

    /**
     * Where the ruling is, right now: out to u, a long rest there, back to x, a rest there. The
     * clamp is belt and braces — [ruleAt] is only monotone for m in 0..1, and a ruler whose marks
     * had crossed over would be drawing negative slabs.
     */
    private fun ruling(kit: SceneKit): Float {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return (SceneParts.step(c, OUT_AT, OUT_LEN) - SceneParts.step(c, BACK_AT, BACK_LEN))
            .coerceIn(0f, 1f)
    }

    /**
     * The number that must not change, and the name of the ruler it was read on. Four places
     * because three would let a small drift hide; the whole claim of the stop is that these digits
     * are the same on both rulers and at every instant in between.
     */
    override fun readout(kit: SceneKit): String? {
        val m = ruling(kit)
        return "SWEPT ${places4(swept(slabsFor(kit.quality), m))}   RULER ${if (m > 0.5f) "u" else "x"}"
    }

    /** A point in the figure: [t] is a ruling position in 0..1, [h] a height in integrand units. */
    private fun figure(t: Float, h: Float, ahead: Float, out: FloatArray) {
        SceneParts.at(g, AX0 + t * AXW, h * HS, ahead, out)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val m = ruling(kit)
        val slabs = slabsFor(kit.quality)
        // The one slab that is watched all the way through. It is far enough along the ruler that
        // its width changes a lot, which is the point: wider and shorter, or narrower and taller,
        // and the same amount of area either way.
        val hl = slabs * 5 / 8

        // --- the ruler ------------------------------------------------------------------------
        // The spine, and one mark per partition point. The marks ARE the substitution: everything
        // else in the figure is downstream of where they happen to be standing.
        figure(0f, 0f, 0.002f, o)
        figure(1f, 0f, 0.002f, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.9f)
        for (k in 0..slabs) {
            val t = ruleAt(k.toFloat() / slabs, m)
            figure(t, -0.075f, 0.003f, o)
            figure(t, 0.060f, 0.003f, du)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.95f)
        }

        // --- the slabs ------------------------------------------------------------------------
        // Width from the ruler, height from the area. Neither is sampled off the curve, so the
        // product is invariant by construction and the gauge below has something real to read.
        var total = 0f
        for (k in 0 until slabs) {
            val x0 = k.toFloat() / slabs
            val x1 = (k + 1).toFloat() / slabs
            val p0 = ruleAt(x0, m)
            val w = ruleAt(x1, m) - p0
            val h = (accum(x1) - accum(x0)) / w
            total += w * h
            SceneParts.at(g, AX0 + p0 * AXW, 0f, 0f, o)
            SceneParts.vec(g, w * AXW, 0f, 0f, du)
            SceneParts.vec(g, 0f, h * HS, 0f, dv)
            val col = if (k == hl) SceneParts.WORK else SceneParts.WORK_DIM
            v = SceneParts.pane(
                kit, line, v, tri, tv, o[0], o[1], o[2],
                du[0], du[1], du[2], dv[0], dv[1], dv[2],
                col, if (k == hl) 1f else 0.85f
            )
        }

        // --- the roof, re-drawn against the current ruling ---------------------------------------
        // Plotted from the functions, not from the slab tops: at ruling position p(x) the height is
        // f(x) / p'(x), which is f(x) itself at m = 0 and cos u at m = 1, continuously and exactly.
        // Sampling from X_MIN rather than 0 is what keeps the left end from being 0/0 at m = 1,
        // where the true limit is 1 and a naive evaluation would drop the roof to the floor.
        val samples = if (kit.quality == 0) 22 else 11
        var px = 0f; var py = 0f; var pz = 0f
        for (j in 0..samples) {
            val x = X_MIN + (1f - X_MIN) * j / samples
            val h = integrand(x) / slope(x, m)
            figure(ruleAt(x, m), h, 0.006f, o)
            if (j > 0) v = MathMesh.segment(line, v, px, py, pz, o[0], o[1], o[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.95f)
            px = o[0]; py = o[1]; pz = o[2]
        }

        // --- the gauge -------------------------------------------------------------------------
        // A calibrated column, not the area re-poured: the swept area at this scale would stand
        // five times the height of the frame if it were literally decanted into a bar this narrow.
        // What it reads is honest, what it is is a metaphor, and the crew says so.
        SceneParts.at(g, GS0, 0f, 0f, o)                  // standing on the ruler's own baseline
        SceneParts.vec(g, GW, 0f, 0f, du)
        SceneParts.vec(g, 0f, GAUGE_H, 0f, dv)
        v = SceneParts.edge(line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.CHALK, 0.55f)
        SceneParts.vec(g, 0f, total * BAR, 0f, dv)
        tv[0] = SceneParts.fill(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2],
            dv[0], dv[1], dv[2], SceneParts.ADDED, 0.34f)
        // The fixed hairline. It is drawn from a constant and the fill is drawn from the picture;
        // they meet, and that meeting is the stop.
        SceneParts.at(g, GS0 - 0.07f, REF, 0.004f, o)
        SceneParts.at(g, GS0 + GW + 0.07f, REF, 0.004f, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
            SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 1f)

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // A single lamp on the mark, breathing with the cue track, because the thing a viewer has
        // to be told to watch is the thing that is not doing anything.
        if (kit.quality == 0) {
            SceneParts.at(g, GS0 + GW * 0.5f, REF, 0.02f, o)
            val pulse = 0.45f + 0.35f * kit.beat
            kit.ball(o[0], o[1], o[2], 0.045f, 0.045f, 0.045f, SceneParts.ADDED, SceneParts.HOT,
                pulse, 0f, 0f, 1f, 0f, 0f, 2.2f * pulse)
        }

        // --- notation ---------------------------------------------------------------------------
        // Everything sits beside or on the instrument, never over or under it: the HUD owns the top
        // of the eye and the caption box the bottom, and this figure is already only a unit tall.
        val inU = m > 0.5f
        // The dip at the crossover: a label that swaps its own text is less jarring if it goes dim
        // while it does it.
        val settled = 0.35f + 0.65f * abs(m * 2f - 1f)

        SceneParts.at(g, AX0 + AXW + 0.09f, -0.02f, 0f, o)
        kit.text(if (inU) "u" else "x", o[0], o[1], o[2], 0.19f, SceneParts.COOL, settled)

        SceneParts.at(g, AX0 + AXW * 0.5f, -0.175f, 0f, o)
        val claim = when {
            m < 0.15f -> "∫ 2x cos(x^2) dx"
            m < 0.85f -> "u = x^2"
            else -> "∫ cos u du"
        }
        kit.text(claim, o[0], o[1], o[2], 0.115f, SceneParts.HOT, 0.95f)

        // Outboard of the column, not above it: above is where the wall is at this radius.
        if (kit.quality < 2) {
            SceneParts.at(g, GS0 - 0.05f, REF * 0.5f, 0f, o)
            kit.text("∫", o[0], o[1], o[2], 0.20f, SceneParts.ADDED, 0.95f,
                GlyphBoard.Style.MATH, 1.2f, anchor = 0.5f)
        }

        if (kit.quality == 0) {
            // The width of one watched slab, hung on top of it so the label travels with the tile
            // it names rather than sitting in the crowded strip under the ruler.
            val x0 = hl.toFloat() / slabs
            val x1 = (hl + 1).toFloat() / slabs
            val p0 = ruleAt(x0, m)
            val w = ruleAt(x1, m) - p0
            val h = (accum(x1) - accum(x0)) / w
            // Clear of the slab's own top AND of the plotted roof, which runs above a rising slab's
            // mean height rather than through it.
            figure(p0 + w * 0.5f, h + 0.20f, 0.01f, o)
            kit.text(if (inU) "Δu" else "Δx", o[0], o[1], o[2], 0.115f,
                SceneParts.WORK, settled, GlyphBoard.Style.SMALL)

            // Both rulers run 0 to 1. Only the marks between the ends move, which is exactly the
            // claim, so the ends are worth numbering.
            figure(0f, -0.200f, 0.004f, o)
            kit.text("0", o[0], o[1], o[2], 0.10f, SceneParts.CHALK, 0.7f, GlyphBoard.Style.SMALL)
            figure(1f, -0.200f, 0.004f, o)
            kit.text("1", o[0], o[1], o[2], 0.10f, SceneParts.CHALK, 0.7f, GlyphBoard.Style.SMALL)
        }
    }
}
