package com.rayneo.mathcosmos

import kotlin.math.sqrt

/**
 * Stop 8 — THE STRING. "A curve's length is the sum of tiny straight bits, and the bits are
 * always a little short."
 *
 * Arc length, and the one thing about it that is easy to say and hard to believe: you cannot
 * measure a curve directly. You can only lay straight pieces along it and add them up, and every
 * straight piece cuts a corner, so the answer you get is always too small. Halve the pieces and
 * the shortfall does not halve — it quarters — which is why four halvings is enough to convince
 * anybody and why nobody ever sees the last of the error, because by then it is thinner than the
 * line it is drawn with.
 *
 * WHY THIS IS A PANEL AND NOT THE CORRIDOR ITSELF. The design asks for a cord laid along the
 * TRACE with the chords unrolled on the floor beneath it, and the honest reading of that is a
 * panel hung to port, exactly as [SceneSignedWake] one stop back plots the same roof. The reason
 * is arithmetic: stops are sixteen world units apart, so one node unit of the corridor is sixteen
 * units long while the roof only ever rises a couple of units above the rail. A cord strung along
 * the real roof would be a cord along something almost perfectly straight — its polygon would fall
 * short by a few thousandths of one percent, and the whole stop would be invisible. So the panel
 * plots the roof AS THE MATHEMATICS SEES IT: node units across, height units up, and the same
 * number of world units per unit in both directions. That last part is not decoration. Arc length
 * is a metric property; stretch one axis against the other and the cord you draw is no longer the
 * cord you are measuring. This panel is the one figure in the tour that has to be isotropic.
 *
 * The heights come from [SceneKit.traceHeight], so it is this corridor's actual roof and not a
 * shape chosen to flatter the argument — and the window is framed around the trough just astern of
 * the stop, where the roof bends hardest and a coarse polygon has the most corner to cut.
 *
 * FOUR HALVINGS, NOT THREE. The design says halve three times, starting from a cord of two
 * chords. Drawn to scale, a two-chord polygon of this roof is already within four percent, and
 * four percent of the rod is a gap you can argue about. So the loop opens one step earlier, with
 * a SINGLE straight bit end to end — sixteen percent short, a red block you cannot miss — and
 * halves from there. The first picture has to be unmissable or none of the later ones mean
 * anything.
 *
 * HOW THE HALVING IS ANIMATED. The finer polygon is always what is drawn; its NEW joints start at
 * the midpoints of the chords they are splitting and rise onto the curve as the step eases. At the
 * beginning of a step the fine polygon is therefore exactly the coarse one, and at the end it is
 * exactly the fine one, with no snap in between — so what a viewer sees is the cord being pulled
 * up onto the curve, which is precisely what refining a partition does.
 *
 * The rod below is built from the SAME chord lengths the cord is drawn with, summed piece by
 * piece, with a tick where each piece ends. Nothing about the rod is computed independently; it
 * cannot disagree with the cord, because it is the cord unrolled.
 *
 * The chalk outline the rod grows inside is the true length, and it is honest about what it is:
 * the same polygon taken to sixty-four chords. There is no closed form for the arc length of
 * 1.2 + 2 sin(0.75 x), and pretending otherwise would mean the "truth" in this picture came from
 * somewhere the picture cannot show. It is the limit of the construction on screen, computed once.
 */
object SceneString : MathScene {

    override val reach = 1.4f

    // ------------------------------------------------------------------ the window

    /**
     * The plotted window in node units either side of the stop. It opens astern of the trough at
     * p ≈ 6.28 and runs up the rising flank, so the cord has one clear bend near its left end and
     * a long steepening climb to its right — a shape whose corners are worth cutting.
     */
    private const val LO = -1.6f
    private const val HI = 1.4f
    private const val SPAN = HI - LO

    // ------------------------------------------------------------------ the panel

    /**
     * To port, and nothing reaches back toward the rail: at the closest point of the pass the rail
     * is where the camera is, and a figure straddling it is a figure you fly into. The whole thing
     * — cord, rod, labels — lives between side -2.2 and -0.6, comfortably inside 0.8 of this
     * stop's passage radius of 3.2.
     */
    private const val SIDE = -1.40f
    private const val UP = 0.16f

    /** World units per unit of the plot, THE SAME both ways. See the note above. */
    private const val SC = 0.36f

    private const val CURVE_U = 0.20f      // the curve's centre height in panel coordinates
    private const val ROD_U = -0.40f       // the measuring rod's centre line
    private const val BAR = 0.09f          // the rod's thickness
    private const val KNOT = 0.016f        // half-width of a joint mark

    private const val PERIOD = 26f
    private const val FINE = 64            // chords the "true" length is taken at
    private const val MAXJ = 17            // joints for the finest drawn polygon, 16 chords

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val a3 = FloatArray(3)
    private val b3 = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)
    private val jh = FloatArray(MAXJ)      // joint heights, draw's copy
    private val jr = FloatArray(MAXJ)      // joint heights, readout's own, so the two never race

    // ------------------------------------------------------------------ the measured window

    // Memoised, because the roof does not change and forty extra traceHeight calls a frame to
    // re-derive a constant is forty calls this device would rather spend elsewhere. Keyed on the
    // stop AND on one probe height, so a different tour with a different roof re-measures itself
    // rather than inheriting these numbers.
    private var memoBase = Float.NaN
    private var memoProbe = Float.NaN
    private var trueLen = 0f               // arc length over the window, in plot units
    private var midH = 0f                  // the window's mid height, so the panel frames itself
    private var originS = 0f               // panel s of the window's start, and of the rod's tail

    private fun measure(kit: SceneKit, base: Float) {
        val probe = kit.traceHeight(base + LO + SPAN * 0.5f)
        if (base == memoBase && probe == memoProbe) return
        val dx = SPAN / FINE
        var ph = kit.traceHeight(base + LO)
        var lo = ph
        var hi = ph
        var sum = 0f
        for (k in 1..FINE) {
            val h = kit.traceHeight(base + LO + dx * k)
            val dy = h - ph
            sum += sqrt(dx * dx + dy * dy)
            if (h < lo) lo = h
            if (h > hi) hi = h
            ph = h
        }
        trueLen = sum
        midH = (lo + hi) * 0.5f
        // The rod is centred on the panel, which puts its far end — the target — in the same place
        // for the whole loop. A rod that grew from a fixed tail would drag its target about with it.
        originS = -trueLen * SC * 0.5f
        memoBase = base
        memoProbe = probe
    }

    /** Panel height of a roof height. */
    private fun uOf(h: Float) = (h - midH) * SC + CURVE_U

    /** Panel s of a point [dp] node units into the window. */
    private fun sOf(dp: Float) = originS + dp * SC

    // ------------------------------------------------------------------ the polygon

    /**
     * The four halvings as one continuous number, 0..4. Each step eases over seven percent of the
     * cycle and then holds, so every partition is still on screen long enough to be looked at, and
     * the finished cord holds for the last third — the rest a looping landmark owes a viewer who
     * arrived halfway through.
     */
    private fun level(c: Float): Float =
        SceneParts.step(c, 0.10f, 0.07f) + SceneParts.step(c, 0.24f, 0.07f) +
            SceneParts.step(c, 0.38f, 0.07f) + SceneParts.step(c, 0.52f, 0.07f)

    /**
     * The joint heights of the [n]-chord polygon into [out]. Even joints sit on the curve; odd
     * joints — the ones this step is adding — start on the chord they split and are drawn [t] of
     * the way up to the curve. At t = 0 this is exactly the polygon of half as many chords.
     */
    private fun joints(kit: SceneKit, base: Float, n: Int, t: Float, out: FloatArray) {
        var k = 0
        while (k <= n) {
            out[k] = kit.traceHeight(base + LO + SPAN * k / n)
            k += 2
        }
        k = 1
        while (k < n) {
            val mid = (out[k - 1] + out[k + 1]) * 0.5f
            val on = kit.traceHeight(base + LO + SPAN * k / n)
            out[k] = mid + (on - mid) * t
            k += 2
        }
    }

    /** The sum of the drawn chords, in plot units: the rod's length, and the whole claim. */
    private fun polySum(n: Int, j: FloatArray): Float {
        val dx = SPAN / n
        var s = 0f
        for (k in 0 until n) {
            val dy = j[k + 1] - j[k]
            s += sqrt(dx * dx + dy * dy)
        }
        return s
    }

    // ------------------------------------------------------------------ the numbers

    /** Three decimals without a formatter; [v] is never negative by the time it arrives. */
    private fun three(v: Float): String {
        val t = (v * 1000f + 0.5f).toInt().coerceAtLeast(0)
        val fr = t % 1000
        val pad = if (fr < 10) "00" else if (fr < 100) "0" else ""
        return "${t / 1000}.$pad$fr"
    }

    /**
     * The shortfall, on the HUD, because after the second halving it is a number and no longer a
     * picture — a gap two pixels wide is not evidence of anything. Plain ASCII: the telemetry pane
     * is not the GlyphBoard.
     *
     * The polygon is recomputed here rather than cached out of [draw]: two short loops over the
     * same function are cheaper and far less fragile than threading a per-frame value between a
     * scene's two entry points, and [SceneSignedWake] made the same call for the same reason.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        val base = kit.progress.toInt().coerceIn(0, kit.stopCount - 1).toFloat()
        measure(kit, base)
        var lvl = level(SceneParts.cycle(kit.seconds, PERIOD))
        if (kit.quality > 0) lvl = lvl.coerceAtMost(3f)
        val stage = lvl.toInt().coerceIn(0, 3)
        val nc = 2 shl stage
        joints(kit, base, nc, (lvl - stage).coerceIn(0f, 1f), jr)
        val len = polySum(nc, jr)
        return "CHORDS $nc   L ${three(len)}   SHORT ${three(trueLen - len)}"
    }

    // ------------------------------------------------------------------ figure-space drawing

    /** A line in panel coordinates, lifted a hair off the fills so it is not z-fought. */
    private fun seg(
        line: FloatArray, at: Int, s0: Float, u0: Float, s1: Float, u1: Float,
        c: FloatArray, alpha: Float, alpha2: Float = alpha
    ): Int {
        SceneParts.at(g, s0, u0, 0.004f, a3)
        SceneParts.at(g, s1, u1, 0.004f, b3)
        return MathMesh.segment(line, at, a3[0], a3[1], a3[2], b3[0], b3[1], b3[2], c[0], c[1], c[2], alpha, alpha2)
    }

    /** The body of a length lying on the rod's row, from [s0] running [len] to starboard. */
    private fun rowFill(tri: FloatArray, at: Int, s0: Float, len: Float, c: FloatArray, alpha: Float): Int {
        if (len < 0.002f) return at
        SceneParts.at(g, s0, ROD_U - BAR * 0.5f, 0f, o)
        SceneParts.vec(g, len, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAR, 0f, dv)
        return SceneParts.fill(tri, at, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha)
    }

    /** The rim of the same. */
    private fun rowEdge(line: FloatArray, at: Int, s0: Float, len: Float, c: FloatArray, alpha: Float): Int {
        if (len < 0.002f) return at
        SceneParts.at(g, s0, ROD_U - BAR * 0.5f, 0.004f, o)
        SceneParts.vec(g, len, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAR, 0f, dv)
        return SceneParts.edge(line, at, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha)
    }

    /**
     * A joint of the cord, as a small filled square in the triangle buffer. Seventeen lit spheres
     * would be seventeen draw calls and the whole scene has about thirty; seventeen quads are none.
     */
    private fun knot(tri: FloatArray, at: Int, s: Float, u: Float, c: FloatArray, alpha: Float): Int {
        SceneParts.at(g, s - KNOT, u - KNOT, 0.002f, o)
        SceneParts.vec(g, KNOT * 2f, 0f, 0f, du)
        SceneParts.vec(g, 0f, KNOT * 2f, 0f, dv)
        return MathMesh.quad(tri, at, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            c[0], c[1], c[2], alpha)
    }

    /** A label at a point in panel coordinates. */
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
        val base = i.toFloat()
        SceneParts.stage(kit, base, SIDE, UP, f, g)
        measure(kit, base)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // At any reduced quality the last halving is dropped rather than played out at half the
        // joints: sixteen chords whose refinement cannot be seen is sixteen chords of heat.
        var lvl = level(c)
        if (kit.quality > 0) lvl = lvl.coerceAtMost(3f)
        val stage = lvl.toInt().coerceIn(0, 3)
        val t = (lvl - stage).coerceIn(0f, 1f)
        val nc = 2 shl stage
        joints(kit, base, nc, t, jh)

        val dxc = SPAN / nc
        val polyW = polySum(nc, jh) * SC
        val trueW = trueLen * SC
        val endS = originS + polyW
        val trueS = originS + trueW

        // --- the target ------------------------------------------------------------------------
        // Drawn first and drawn full length, so the rod is always visibly growing INSIDE something
        // it has not reached. Without it, a rod that gets longer is just a rod that gets longer.
        v = rowEdge(line, v, originS, trueW, SceneParts.CHALK, 0.35f)

        // --- what the polygon is missing ---------------------------------------------------------
        // The debt keeps its own colour, the way the borrowed corner does back in Tour I: a viewer
        // who looks away and back can still see which part of the length was never counted.
        if (trueS - endS > 0.002f) {
            tv[0] = rowFill(tri, tv[0], endS, trueS - endS, SceneParts.TAKEN, 0.40f)
        }

        // --- the curve ---------------------------------------------------------------------------
        val ns = when (kit.quality) { 0 -> 40; 1 -> 20; else -> 12 }
        var ps = originS
        var pu = uOf(kit.traceHeight(base + LO))
        for (k in 1..ns) {
            val u = k.toFloat() / ns
            val s = originS + SPAN * u * SC
            val h = uOf(kit.traceHeight(base + LO + SPAN * u))
            v = seg(line, v, ps, pu, s, h, SceneParts.CHALK, 0.55f)
            ps = s
            pu = h
        }

        // --- the window, dropped onto the rod's row -----------------------------------------------
        // Two faint verticals off the ends of the plotted stretch. The left one lands on the rod's
        // tail and the right one lands well short of its head, which is the fact the whole stop is
        // built on: the curve is longer than the ground it covers.
        v = seg(line, v, originS, ROD_U + BAR * 0.5f, originS, uOf(jh[0]), SceneParts.CHALK, 0.08f, 0.30f)
        val hiS = sOf(SPAN)
        v = seg(line, v, hiS, ROD_U + BAR * 0.5f, hiS, uOf(jh[nc]), SceneParts.CHALK, 0.08f, 0.30f)

        // --- which piece of rod is which chord -----------------------------------------------------
        // Only while the chords are still few enough to be told apart. Past four pieces these lines
        // are a thicket, and by then the correspondence has been made and does not need making again.
        if (kit.quality == 0 && nc <= 4) {
            var acc = 0f
            for (k in 0 until nc) {
                val dy = jh[k + 1] - jh[k]
                val l = sqrt(dxc * dxc + dy * dy) * SC
                val cs = sOf(dxc * k + dxc * 0.5f)
                val cu = (uOf(jh[k]) + uOf(jh[k + 1])) * 0.5f
                v = seg(line, v, cs, cu, originS + acc + l * 0.5f, ROD_U + BAR * 0.5f,
                    SceneParts.WORK, 0.05f, 0.35f)
                acc += l
            }
        }

        // --- the cord ------------------------------------------------------------------------------
        for (k in 0 until nc) {
            v = seg(line, v, sOf(dxc * k), uOf(jh[k]), sOf(dxc * (k + 1)), uOf(jh[k + 1]),
                SceneParts.WORK, 1f)
        }

        // --- the rod: the same chords, laid end to end -----------------------------------------------
        tv[0] = rowFill(tri, tv[0], originS, polyW, SceneParts.WORK, 0.30f)
        v = rowEdge(line, v, originS, polyW, SceneParts.WORK, 0.95f)
        if (kit.quality < 2) {
            var acc = 0f
            for (k in 0 until nc) {
                val dy = jh[k + 1] - jh[k]
                acc += sqrt(dxc * dxc + dy * dy) * SC
                if (k < nc - 1) {
                    v = seg(line, v, originS + acc, ROD_U - BAR * 0.5f, originS + acc, ROD_U + BAR * 0.5f,
                        SceneParts.WORK, 0.5f)
                }
            }
        }
        // The mark the rod is trying to reach.
        v = seg(line, v, trueS, ROD_U - BAR * 0.9f, trueS, ROD_U + BAR * 0.9f, SceneParts.CHALK, 0.9f)

        // --- the joints ---------------------------------------------------------------------------
        if (kit.quality < 2) {
            for (k in 0..nc) {
                tv[0] = knot(tri, tv[0], sOf(dxc * k), uOf(jh[k]), SceneParts.HOT, 0.9f)
            }
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // One lamp, at the head of the rod, because the head of the rod is the number. It pulses
        // with the cue so the halvings land on the sound rather than beside it.
        SceneParts.at(g, endS, ROD_U, 0.01f, o)
        kit.ball(
            o[0], o[1], o[2], 0.035f, 0.035f, 0.035f, SceneParts.HOT, SceneParts.WORK,
            0.9f, 0f, 0f, 1f, 0f, 0f, 1.6f + 2.2f * kit.beat
        )

        // --- notation --------------------------------------------------------------------------------
        // All of it inside the panel's own footprint or off its port edge. Nothing above and nothing
        // below: the telemetry owns the top of the eye and the caption box the bottom, and a label in
        // either is a label nobody reads.
        //
        // Three beats, each naming a different object, in that object's colour: the length of one
        // straight bit, then the inequality that is the honest part, then the limit the whole
        // construction is reaching for. The inequality is written strict, not ≤, because this roof
        // genuinely bends everywhere in the window and the polygon is genuinely always shorter.
        // It hangs in the panel's empty top-left corner, where the cord is at its lowest — beside
        // the figure, not stacked over it.
        when {
            c < 0.12f -> mark(kit, originS + 0.02f, 0.56f, "√(Δx^2 + Δy^2)", 0.145f,
                SceneParts.WORK, 1f, anchor = -0.5f)
            c < 0.70f -> mark(kit, originS + 0.02f, 0.56f, "Σ Δs < L", 0.19f,
                SceneParts.HOT, 1f, anchor = -0.5f)
            else -> mark(kit, originS + 0.02f, 0.56f, "L = ∫√(1+y'^2) dx", 0.13f,
                SceneParts.CHALK, 1f, anchor = -0.5f)
        }

        if (kit.quality == 0) {
            // The two ends of the rod named for what they are: a sum, and a length.
            mark(kit, originS - 0.05f, ROD_U, "Σ", 0.17f, SceneParts.WORK, 0.95f, anchor = 0.5f)
            mark(kit, trueS, ROD_U + 0.15f, "L", 0.16f, SceneParts.CHALK, 0.9f)
        }

        if (kit.quality == 0 && nc <= 4) {
            // One chord named, on the steepest piece, where the corner being cut is widest.
            val cs = sOf(SPAN - dxc * 0.5f)
            val cu = (uOf(jh[nc - 1]) + uOf(jh[nc])) * 0.5f
            mark(kit, cs - 0.09f, cu + 0.05f, "Δs", 0.15f, SceneParts.WORK, 0.9f, anchor = 0.5f)
        }
    }
}
