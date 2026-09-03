package com.rayneo.mathcosmos

import kotlin.math.abs

/**
 * Stop 6 — THE SIGNED WAKE. "Below the axis, the sweep pays out instead of taking in."
 *
 * The corridor already does the teaching here. The roof dives under the rail just past this stop,
 * the craft comes out ABOVE its own curve, and [SceneAmbientWake] turns the sheet behind it from
 * amber to cold blue at the crossing. None of that belongs to this scene and none of it is drawn
 * twice: what a viewer standing at the window can see is that the colour changed, and what they
 * cannot see is what the change is worth. So the landmark is a LEDGER — a small panel hung off to
 * one side that plots the same roof, fills the same two colours under it, and keeps the running
 * total as a line that visibly turns round and comes back down.
 *
 * That falling line is the whole stop. The tour's one sentence for this landmark is about paying
 * out, not about cancelling, and a total that retreats is the only picture that says it.
 *
 * WHAT THE PANEL PLOTS, AND WHERE IT IS HONEST. Three decisions, all of which went the other way
 * first:
 *
 * ONE DIVE, NOT ONE PERIOD. The design calls for a full period of the roof. Drawn to scale that is
 * a large amber hump with a small blue nick in it, because this tour's roof is 1.2 + 2 sin(0.75 p)
 * — a wave with a LIFT on it — and the nick is the only part of the stop anybody came for. The
 * window is therefore 5.4 node units centred on the trough: gold, blue, gold, with the two
 * crossings a little in from each edge and the blue trough owning the middle of the panel. The
 * heights are read from [SceneKit.traceHeight], so the picture is this corridor's actual roof and
 * not a stand-in drawn to flatter the argument.
 *
 * WHICH MEANS THE LOBES ARE NOT EQUAL, AND THE TOTAL DOES NOT COME BACK TO ZERO. It comes back
 * most of the way — the sweep hands back about seven-eighths of what it took — and then climbs
 * again. A sine over a full period integrates to nothing; a sine with a lift on it does not, and
 * every scrap of net area in this corridor is that lift. Saying otherwise would need a different
 * roof, so the archetype is stated as notation, ∫ over a period of a centred wave is zero, and the
 * picture is left telling the truth about the passage the viewer is actually in. The crew say the
 * same thing out loud.
 *
 * TWO QUANTITIES, ONE VERTICAL SCALE. The roof height f and the running total A are different
 * kinds of thing — a length and an area — and putting them in one panel is a liberty. They are at
 * least drawn at the same world units per numeric unit, so neither is being flattered, and the
 * only claim made about A is the direction it moves. Colour keeps them apart: chalk for the roof,
 * because that is the colour the ambient draws the real roof in, and warm white for the total.
 *
 * The tally bars along the bottom are the sizes of the two lobes laid out end to end so they can
 * be compared by length rather than by eye, and the blue one slides up and subtracts itself from
 * the gold exactly the way the rods do at THE TWO CLOCKS one stop back. Re-using that gesture is
 * deliberate: subtraction should look the same everywhere in the tour.
 *
 * The craft's own place in the window is marked by a small chevron on the axis, driven by live
 * rail progress rather than by the loop clock. During the approach it walks across the first half
 * of the panel and crosses into the blue at almost exactly the moment the corridor's roof does.
 */
object SceneSignedWake : MathScene {

    override val reach = 1.4f

    // ------------------------------------------------------------------ the window

    /** The plotted window, in node units either side of this stop: one dive, framed by gold. */
    private const val LO = -1.4f
    private const val HI = 4.0f
    private const val SPAN = HI - LO

    // ------------------------------------------------------------------ the panel

    /**
     * A flat figure centred on the rail is a figure you fly INTO. This one hangs to port, and
     * everything in it — bars, labels and all — stays inside side -2.45, which is comfortably
     * short of 0.8 of this stop's passage radius. Nothing reaches back toward the rail either,
     * because at the closest point of the pass the rail is where the camera is.
     */
    private const val SIDE = -1.25f
    private const val UP = 0.10f

    private const val W = 1.90f            // panel width, world units
    private const val HALF = W * 0.5f
    private const val HS = 0.22f           // world units per unit of roof height, and of total
    private const val TALLY = 0.42f        // world units per unit of area on the tally bars

    private const val GOLD_ROW = -0.28f
    private const val BLUE_ROW = -0.40f
    private const val BAR = 0.075f

    private const val FILL = 0.30f
    private const val PERIOD = 24f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val a3 = FloatArray(3)
    private val b3 = FloatArray(3)
    private val c3 = FloatArray(3)
    private val d3 = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)
    private val tally = FloatArray(2)      // gold, blue — readout's scratch only

    /** Amber above the rail, cold blue below it, exactly as the wake outside is coloured. */
    private fun signCol(h: Float): FloatArray = if (h >= 0f) SceneParts.WORK else SceneParts.COOL

    // ------------------------------------------------------------------ the numbers

    /**
     * The gold and the blue swept between the window's start and a fraction [upTo] across it,
     * into [out]. Trapezoid rule, split where the roof crosses the rail so the two colours get
     * their share of the crossing strip rather than the strip being called one or the other.
     *
     * Duplicated in [draw], which needs the same running values strip by strip as it builds the
     * fill. Two short loops over the same function beat threading a per-frame cache between a
     * scene's two entry points.
     */
    private fun areas(kit: SceneKit, base: Float, upTo: Float, out: FloatArray) {
        out[0] = 0f
        out[1] = 0f
        val run = SPAN * upTo.coerceIn(0f, 1f)
        if (run < 1e-3f) return
        val n = 40
        val dp = run / n
        var prev = kit.traceHeight(base + LO)
        for (k in 1..n) {
            val h = kit.traceHeight(base + LO + dp * k)
            if (prev * h < 0f) {
                val t = prev / (prev - h)
                val first = 0.5f * prev * dp * t
                val second = 0.5f * h * dp * (1f - t)
                if (prev >= 0f) { out[0] += first; out[1] -= second } else { out[1] -= first; out[0] += second }
            } else {
                val a = (prev + h) * 0.5f * dp
                if (a >= 0f) out[0] += a else out[1] -= a
            }
            prev = h
        }
    }

    /** One decimal without a formatter. [v] is never negative by the time it gets here. */
    private fun tenths(v: Float): String {
        val t = (v * 10f + 0.5f).toInt()
        return "${t / 10}.${t % 10}"
    }

    /**
     * The three numbers this stop exists to show, on the HUD where a number is legible. Plain
     * ASCII signs: the telemetry pane is not the GlyphBoard and does not owe us a minus sign.
     */
    override fun readout(kit: SceneKit): String? {
        // The renderer asks whichever stop the craft's progress floors onto, so that is the stop
        // whose window this is.
        val base = kit.progress.toInt().coerceIn(0, kit.stopCount - 1).toFloat()
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val sweep = SceneParts.step(c, 0.05f, 0.50f)
        areas(kit, base, sweep, tally)
        val net = tally[0] - tally[1]
        return "LEDGER +${tenths(tally[0])}  -${tenths(tally[1])}   NET ${if (net < 0f) "-" else "+"}${tenths(abs(net))}"
    }

    // ------------------------------------------------------------------ figure-space drawing

    /** A line in the panel's own coordinates, lifted a hair off the fill so it is not z-fought. */
    private fun seg(
        line: FloatArray, at: Int, s0: Float, u0: Float, s1: Float, u1: Float,
        c: FloatArray, alpha: Float, alpha2: Float = alpha
    ): Int {
        SceneParts.at(g, s0, u0, 0.004f, a3)
        SceneParts.at(g, s1, u1, 0.004f, b3)
        return MathMesh.segment(line, at, a3[0], a3[1], a3[2], b3[0], b3[1], b3[2], c[0], c[1], c[2], alpha, alpha2)
    }

    /** A triangle in panel coordinates: the half-strip either side of a crossing, and the chevron. */
    private fun tri3(
        tri: FloatArray, at: Int, sa: Float, ua: Float, sb: Float, ub: Float, sc: Float, uc: Float,
        c: FloatArray, alpha: Float
    ): Int {
        if ((at + 3) * MathMesh.STRIDE > tri.size) return at
        SceneParts.at(g, sa, ua, 0f, a3)
        SceneParts.at(g, sb, ub, 0f, b3)
        SceneParts.at(g, sc, uc, 0f, c3)
        var k = MathMesh.vertex(tri, at, a3[0], a3[1], a3[2], c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, b3[0], b3[1], b3[2], c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, c3[0], c3[1], c3[2], c[0], c[1], c[2], alpha)
        return k
    }

    /**
     * One strip of fill: the trapezoid between the axis and the roof.
     *
     * [MathMesh.quad] cannot do this — it builds a parallelogram, and the two ends of a strip are
     * only the same height where the roof is flat.
     */
    private fun strip(
        tri: FloatArray, at: Int, s0: Float, u0: Float, s1: Float, u1: Float,
        c: FloatArray, alpha: Float
    ): Int {
        if ((at + 6) * MathMesh.STRIDE > tri.size) return at
        SceneParts.at(g, s0, 0f, 0f, a3)
        SceneParts.at(g, s1, 0f, 0f, b3)
        SceneParts.at(g, s1, u1, 0f, c3)
        SceneParts.at(g, s0, u0, 0f, d3)
        var k = MathMesh.vertex(tri, at, a3[0], a3[1], a3[2], c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, b3[0], b3[1], b3[2], c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, c3[0], c3[1], c3[2], c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, a3[0], a3[1], a3[2], c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, c3[0], c3[1], c3[2], c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, d3[0], d3[1], d3[2], c[0], c[1], c[2], alpha)
        return k
    }

    /** A tally bar lying along the panel's bottom: translucent body, bright rim. */
    private fun tallyBar(
        line: FloatArray, lv: Int, tri: FloatArray, s0: Float, len: Float, row: Float,
        c: FloatArray, alpha: Float
    ): Int {
        if (len < 0.006f) return lv
        SceneParts.at(g, s0, row, 0f, o)
        SceneParts.vec(g, len, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAR, 0f, dv)
        tv[0] = SceneParts.fill(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha * 0.35f)
        return SceneParts.edge(line, lv, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha)
    }

    /** A label at a point in the panel's coordinates. */
    private fun mark(
        kit: SceneKit, s: Float, u: Float, text: String, height: Float, c: FloatArray,
        alpha: Float, style: GlyphBoard.Style = GlyphBoard.Style.MATH, anchor: Float = 0f
    ) {
        SceneParts.at(g, s, u, 0.01f, o)
        kit.text(text, o[0], o[1], o[2], height, c, alpha, style, 1f, anchor)
    }

    // ------------------------------------------------------------------ the landmark

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        if (!kit.hasTrace) return
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val sweep = SceneParts.step(c, 0.05f, 0.50f)
        val subtract = SceneParts.step(c, 0.66f, 0.16f)
        val base = i.toFloat()
        val cursor = -HALF + W * sweep

        // Enough strips that the crossing looks like a point and not a staircase; halved once at
        // quality 1 and again at 2, where the fill is still the thing being read.
        val nSeg = when (kit.quality) { 0 -> 44; 1 -> 22; else -> 14 }
        val dp = SPAN / nSeg

        // --- the axis the sign is measured from ------------------------------------------------
        // Drawn first and drawn all the way across: without it, "the roof went below us" is a
        // claim about a line nobody can see.
        v = seg(line, v, -HALF, 0f, HALF, 0f, SceneParts.CHALK, 0.45f)

        var gold = 0f
        var blue = 0f
        var acc = 0f
        var ps = -HALF
        var ph = kit.traceHeight(base + LO)

        for (k in 1..nSeg) {
            val u = k.toFloat() / nSeg
            val s = -HALF + W * u
            val h = kit.traceHeight(base + LO + SPAN * u)

            // --- the roof, drawn the whole way across whether or not it has been swept ---------
            // The same asymmetry the ambient uses outside: the curve exists ahead of you, the
            // area under it does not, because you have not been there yet.
            v = seg(line, v, ps, ph * HS, s, h * HS, SceneParts.CHALK, 0.95f)

            // --- the fill and the total, only behind the cursor --------------------------------
            if (ps < cursor) {
                val cutT = if (s <= cursor) 1f else ((cursor - ps) / (s - ps)).coerceIn(0f, 1f)
                val se = ps + (s - ps) * cutT
                val he = ph + (h - ph) * cutT
                val dpc = dp * cutT
                val accWas = acc

                if (ph * he < 0f) {
                    // The roof passes through the rail inside this strip. Split it there, so no
                    // single triangle is half amber and half blue and the crossing is a point.
                    val t = ph / (ph - he)
                    val sx = ps + (se - ps) * t
                    tv[0] = tri3(tri, tv[0], ps, 0f, sx, 0f, ps, ph * HS, signCol(ph), FILL)
                    tv[0] = tri3(tri, tv[0], sx, 0f, se, 0f, se, he * HS, signCol(he), FILL)
                    val first = 0.5f * ph * dpc * t
                    val second = 0.5f * he * dpc * (1f - t)
                    if (ph >= 0f) { gold += first; blue -= second } else { blue -= first; gold += second }
                    acc += first + second
                } else if (cutT > 1e-4f) {
                    // Coloured by the sum of the two ends, not by one of them: a strip that starts
                    // exactly on the rail and goes down is a blue strip, and ph alone would call
                    // its flat end amber.
                    tv[0] = strip(tri, tv[0], ps, ph * HS, se, he * HS, signCol(ph + he), FILL)
                    val a = (ph + he) * 0.5f * dpc
                    acc += a
                    if (a >= 0f) gold += a else blue -= a
                }

                // --- the running total ---------------------------------------------------------
                // The line that turns round. Everything else in the panel is here to make this
                // one legible.
                v = seg(line, v, ps, accWas * HS, se, acc * HS, SceneParts.HOT, 0.95f)
            }

            ps = s
            ph = h
        }

        // --- the total as a standing bar at the cursor ------------------------------------------
        // The leading end of the total curve, made into something with a length, so the rise and
        // the retreat are a bar getting shorter and not merely a line sloping down.
        if (sweep > 0.01f) {
            v = seg(line, v, cursor, 0f, cursor, acc * HS, SceneParts.HOT, 0.35f, 0.95f)
        }

        // --- the tallies -------------------------------------------------------------------------
        // Two lengths that can be held against each other. Then the blue one slides up and takes
        // itself out of the gold, and what stays lit is the net — the same subtraction, and the
        // same gesture, as the two rods at the stop before this one.
        val gLen = gold * TALLY
        val bLen = blue * TALLY
        val netLen = (gLen - bLen).coerceAtLeast(0f)
        val goldRow = GOLD_ROW
        val blueRow = BLUE_ROW + (GOLD_ROW - BLUE_ROW) * subtract

        v = tallyBar(line, v, tri, -HALF, netLen, goldRow, SceneParts.WORK, 0.95f)
        v = tallyBar(line, v, tri, -HALF + netLen, gLen - netLen, goldRow, SceneParts.WORK, 0.95f - 0.65f * subtract)
        v = tallyBar(line, v, tri, -HALF + netLen * subtract, bLen, blueRow, SceneParts.COOL, 0.95f)

        // --- where the craft actually is ----------------------------------------------------------
        // Live rail progress, not the loop clock: this is the one mark in the panel that says the
        // diagram and the corridor are the same object.
        val here = (kit.progress - (base + LO)) / SPAN
        if (here > 0.01f && here < 0.99f) {
            val hs = -HALF + W * here
            tv[0] = tri3(tri, tv[0], hs, 0f, hs - 0.035f, 0.075f, hs + 0.035f, 0.075f, SceneParts.STEEL, 0.85f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- notation ------------------------------------------------------------------------------
        // Everything sits inside the panel's own outline or off its port edge. Nothing is hung
        // above or below the figure: the telemetry owns the top of the eye and the caption box the
        // bottom, and a label there is a label nobody reads.
        val hCursor = kit.traceHeight(base + LO + SPAN * sweep)
        val claim = if (sweep < 0.999f) (if (hCursor >= 0f) "f > 0" else "f < 0") else "∫_0^{2π} sin = 0"
        val claimCol = if (sweep < 0.999f) signCol(hCursor) else SceneParts.CHALK
        val claimH = if (sweep < 0.999f) 0.17f else 0.12f
        mark(kit, 0.18f, 0.33f, claim, claimH, claimCol, 1f)

        if (kit.quality < 2) {
            mark(kit, -0.80f, 0.16f, "+", 0.15f, SceneParts.WORK, 0.95f, GlyphBoard.Style.PLAIN)
            mark(kit, 0.00f, -0.085f, "−", 0.14f, SceneParts.COOL, 0.95f, GlyphBoard.Style.PLAIN)
        }

        if (kit.quality == 0) {
            // The two curves named at the edge they start or end on, and the two tallies named
            // beside the bars they measure.
            mark(kit, -HALF - 0.10f, kit.traceHeight(base + LO) * HS, "f", 0.17f, SceneParts.CHALK, 0.9f, anchor = 0.5f)
            mark(kit, -HALF - 0.10f, 0f, "A", 0.17f, SceneParts.HOT, 0.9f, anchor = 0.5f)
            mark(kit, -HALF - 0.08f, goldRow + BAR * 0.5f, "+", 0.12f, SceneParts.WORK, 0.85f, GlyphBoard.Style.PLAIN, 0.5f)
            mark(kit, -HALF - 0.08f, blueRow + BAR * 0.5f, "−", 0.12f, SceneParts.COOL, 0.85f, GlyphBoard.Style.PLAIN, 0.5f)
        }
    }
}
