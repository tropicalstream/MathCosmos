package com.rayneo.mathcosmos

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Stop 1 of THE ACCUMULATION — THE WAKE. "The area under a curve is just how much I have swept up
 * so far."
 *
 * The corridor is already doing most of the teaching here. [SceneAmbientWake] runs for the whole
 * tour: it draws the roof curve ahead and behind, and it fills the sheet from rail to roof ONLY
 * behind the craft. Fly through it and you have flown through the definition — full astern, empty
 * ahead. So this landmark deliberately draws neither the roof nor the wake. Duplicating them at
 * one stop would put two sheets in the same corridor at slightly different alphas and make the
 * ambient look broken.
 *
 * What the ambient cannot do is let you see the whole of the thing at once, or answer the question
 * the stop actually turns on. So beside the rail hangs a GAUGE: a small plot of this tour's own
 * roof over the first four node units, with a cursor sweeping across it, the region behind the
 * cursor filled amber, and a column beside it holding the running total.
 *
 * It is a model of the corridor, and it should be said out loud that it is one. The curve in the
 * gauge is literally kit.traceHeight over 0..4 — the same shape that is overhead, so a viewer can
 * look up and check — but the cursor is not the craft and its clock is not the craft's clock. The
 * gauge is a rehearsal of the flight, run twice while you are parked beside it.
 *
 * TWICE, because of the last line of the brief: "the ship slows and the wake still grows — because
 * it is area, not speed." That is not something a single sweep can show. Run one crosses at a
 * steady rate and its total is marked on the column. Run two tears across the low ground and then
 * crawls over the hump — where it crawls, the column climbs FASTEST, because the roof is high — and
 * it lands on the same mark. Same ground, same total, wildly different speeds. The speed is on the
 * HUD in [readout] as a multiplier, dropping to about a fifth while the total keeps rising, which
 * is where a number that must be read belongs.
 *
 * The rewind between the two runs is stagecraft and nothing else: the corridor does not un-sweep
 * itself. It is there because the scene has to loop and a viewer arriving mid-cycle must be able to
 * work out what they are looking at within one pass. The mark, once set, survives the rewind.
 *
 * The window 0..4 is chosen so the roof stays above the rail for the whole of it. This tour's trace
 * dives under the rail later on, and signed area is stop 6's payoff; giving it away in the first
 * gauge would spoil it.
 */
object SceneWake : MathScene {

    override val reach = 1.5f

    // ---------------------------------------------------------------- staging
    // Off to one side, and about two units across. A flat figure centred on the rail is a figure
    // you fly INTO — at closest approach only a corner of it is in frame.
    private const val SIDE = -1.40f
    private const val UP_OFF = 0.10f

    /** How much of the corridor ahead the gauge plots, in node units. */
    private const val SPAN = 4f

    private const val X0 = -0.82f          // left end of the gauge's x axis, in stage units
    private const val W = 1.45f            // its width
    private const val BASE_U = -0.34f      // the axis line's height in stage units
    private const val PEAK_U = 0.48f       // where the tallest point of the roof is drawn
    private const val BAR_S = 0.86f        // left edge of the running-total column
    private const val BAR_W = 0.15f
    private const val BAR_FULL = 0.72f     // the column's height when the whole window is swept

    // ---------------------------------------------------------------- the loop
    private const val PERIOD = 26f
    private const val P1_AT = 0.06f
    private const val P1_LEN = 0.34f
    private const val HOLD1 = 0.48f        // pass one is finished and marked from 0.40
    private const val REW_LEN = 0.065f
    private const val P2_AT = 0.56f
    private const val P2_LEN = 0.34f
    /** How uneven the second pass is: speed runs from 1+K down to 1−K and back. */
    private const val K = 0.85f
    private const val TAU = 6.2831855f

    /** Samples across the window. Everything else is a stride over this table. */
    private const val TABLE = 64

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val q0 = FloatArray(3)
    private val q1 = FloatArray(3)
    private val q2 = FloatArray(3)
    private val q3 = FloatArray(3)
    private val tv = IntArray(1)

    // The roof sampled once, and the area swept up to each sample. Built on the first draw rather
    // than declared, so the gauge plots whatever function the tour is actually flying under.
    private val hs = FloatArray(TABLE + 1)
    private val cum = FloatArray(TABLE + 1)
    private var total = 1f
    private var vs = 0.16f                 // world units per unit of roof height
    private var built = false

    /** x, speed, which run (0 = rewinding), whether the mark is set. Rewritten, never allocated. */
    private val ph = FloatArray(4)

    /**
     * The roof, with a fallback so the gauge is never blank. If this object is ever hung on a tour
     * with no trace there is nothing to plot, and an empty landmark reads as a bug; the fallback is
     * THE ACCUMULATION's own curve, written out.
     */
    private fun roof(kit: SceneKit, p: Float): Float =
        if (kit.hasTrace) kit.traceHeight(p) else 1.2f + 2f * sin(p * 0.75f)

    private fun build(kit: SceneKit) {
        if (built) return
        var maxH = 0.001f
        for (k in 0..TABLE) {
            val h = roof(kit, SPAN * k / TABLE)
            hs[k] = h
            if (h > maxH) maxH = h
        }
        // Trapezoid rule. It is measuring a picture, not proving a theorem, and it has the virtue
        // of agreeing exactly with the trapezoids the fill is drawn from — the number on the HUD is
        // the area of the shape on the gauge, not a better estimate of it.
        val d = SPAN / TABLE
        cum[0] = 0f
        for (k in 1..TABLE) cum[k] = cum[k - 1] + (hs[k - 1] + hs[k]) * 0.5f * d
        total = if (cum[TABLE] > 0.001f) cum[TABLE] else 0.001f
        vs = PEAK_U / maxH
        built = true
    }

    /** Area swept up to [x01] of the window, by lerping the cumulative table. */
    private fun areaAt(x01: Float): Float {
        val xs = x01.coerceIn(0f, 1f) * TABLE
        val i = floor(xs).toInt().coerceIn(0, TABLE)
        if (i >= TABLE) return cum[TABLE]
        return cum[i] + (cum[i + 1] - cum[i]) * (xs - i)
    }

    /** Roof height at [x01] of the window, in world units above the axis. */
    private fun heightAt(x01: Float): Float {
        val xs = x01.coerceIn(0f, 1f) * TABLE
        val i = floor(xs).toInt().coerceIn(0, TABLE)
        if (i >= TABLE) return hs[TABLE] * vs
        return (hs[i] + (hs[i + 1] - hs[i]) * (xs - i)) * vs
    }

    /**
     * Where the cursor is and how fast it is going, into [ph]. Both [draw] and [readout] call it,
     * on the same thread and in the same frame, so they can never disagree about what is on screen.
     */
    private fun phase(seconds: Float) {
        val c = SceneParts.cycle(seconds, PERIOD)
        when {
            c < P1_AT -> set(0f, 0f, 1f, 0f)
            c < P1_AT + P1_LEN -> set((c - P1_AT) / P1_LEN, 1f, 1f, 0f)
            c < HOLD1 -> set(1f, 0f, 1f, 1f)
            c < HOLD1 + REW_LEN -> set(1f - SceneParts.ease((c - HOLD1) / REW_LEN), 0f, 0f, 1f)
            c < P2_AT -> set(0f, 0f, 2f, 1f)
            c < P2_AT + P2_LEN -> {
                // x(t) = t + K sin(2πt)/2π. It starts and ends exactly on the window, so the two
                // runs cover the same ground; its derivative 1 + K cos(2πt) is the whole point.
                val t = (c - P2_AT) / P2_LEN
                set(t + K * sin(TAU * t) / TAU, 1f + K * cos(TAU * t), 2f, 1f)
            }
            else -> set(1f, 0f, 2f, 1f)
        }
    }

    private fun set(x: Float, sp: Float, run: Float, mark: Float) {
        ph[0] = x.coerceIn(0f, 1f); ph[1] = sp; ph[2] = run; ph[3] = mark
    }

    /** One decimal without a formatter. */
    private fun tenths(v: Float): String {
        val t = (v * 10f + 0.5f).toInt()
        return "${t / 10}.${t % 10}"
    }

    override fun readout(kit: SceneKit): String? {
        build(kit)
        phase(kit.seconds)
        val a = areaAt(ph[0])
        return when {
            // The line the stop lives or dies on: speed falling while the total keeps climbing.
            ph[1] > 0.01f -> "SWEPT ${tenths(a)} / ${tenths(total)}   SPEED x${tenths(ph[1])}"
            ph[0] > 0.99f && ph[3] > 0.5f && ph[2] > 1.5f -> "SWEPT ${tenths(total)}   SAME MARK"
            ph[0] > 0.99f && ph[3] > 0.5f -> "SWEPT ${tenths(total)}   MARKED"
            else -> "SWEPT ${tenths(a)} / ${tenths(total)}"
        }
    }

    /**
     * One step of the swept region: the trapezoid axis-left, axis-right, roof-right, roof-left.
     * [MathMesh.quad] cannot do this — it builds a parallelogram, and the two ends of a step are
     * only the same height where the roof is flat.
     */
    private fun slab(tri: FloatArray, v: Int, s0: Float, h0: Float, s1: Float, h1: Float,
                     c: FloatArray, a: Float): Int {
        if ((v + 6) * MathMesh.STRIDE > tri.size) return v
        SceneParts.at(g, s0, BASE_U, 0f, q0)
        SceneParts.at(g, s1, BASE_U, 0f, q1)
        SceneParts.at(g, s1, BASE_U + h1, 0f, q2)
        SceneParts.at(g, s0, BASE_U + h0, 0f, q3)
        var k = MathMesh.vertex(tri, v, q0[0], q0[1], q0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q1[0], q1[1], q1[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q2[0], q2[1], q2[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q0[0], q0[1], q0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q2[0], q2[1], q2[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q3[0], q3[1], q3[2], c[0], c[1], c[2], a)
        return k
    }

    /** A segment given both ends in stage coordinates. */
    private fun seg(line: FloatArray, v: Int, s0: Float, u0: Float, s1: Float, u1: Float,
                    c: FloatArray, a: Float): Int {
        SceneParts.at(g, s0, u0, 0f, q0)
        SceneParts.at(g, s1, u1, 0f, q1)
        return MathMesh.segment(line, v, q0[0], q0[1], q0[2], q1[0], q1[1], q1[2],
            c[0], c[1], c[2], a)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build(kit)
        phase(kit.seconds)
        SceneParts.stage(kit, i.toFloat(), SIDE, UP_OFF, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val x = ph[0]
        val swept = areaAt(x)
        val xs = x * TABLE
        val sCur = X0 + W * x
        val hCur = heightAt(x)
        // The stride halves at quality 1 and quarters at 2. The gauge gets blockier, which is
        // honest — a coarser sum is what a coarser sum looks like — and it stays readable.
        val stp = when (kit.quality) { 0 -> 1; 1 -> 2; else -> 4 }

        // --- the region already swept -------------------------------------------------------
        // Behind the cursor only. Ahead of it there is nothing at all, which is the one fact the
        // whole tour rests on.
        var k = 0
        while (k + stp <= xs) {
            tv[0] = slab(tri, tv[0],
                X0 + W * k / TABLE, hs[k] * vs,
                X0 + W * (k + stp) / TABLE, hs[k + stp] * vs,
                SceneParts.WORK, 0.30f)
            k += stp
        }
        // The part-step the cursor is standing in, so the fill's edge is exactly under the emitter
        // rather than snapping forward one sample at a time.
        if (xs > k + 0.002f) {
            tv[0] = slab(tri, tv[0], X0 + W * k / TABLE, hs[k] * vs, sCur, hCur,
                SceneParts.WORK, 0.30f)
        }

        // --- the running total, as a column ---------------------------------------------------
        val barH = BAR_FULL * (swept / total).coerceIn(0f, 1f)
        if (barH > 0.001f) {
            SceneParts.at(g, BAR_S, BASE_U, 0f, o)
            SceneParts.vec(g, BAR_W, 0f, 0f, du)
            SceneParts.vec(g, 0f, barH, 0f, dv)
            v = SceneParts.pane(kit, line, v, tri, tv, o[0], o[1], o[2],
                du[0], du[1], du[2], dv[0], dv[1], dv[2], SceneParts.WORK, 0.95f)
        }
        // The empty vessel, so the column is read as a fraction of something and not as a bar that
        // happens to be that tall.
        SceneParts.at(g, BAR_S, BASE_U, 0f, o)
        SceneParts.vec(g, BAR_W, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAR_FULL, 0f, dv)
        v = SceneParts.edge(line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.CHALK, 0.28f)

        // --- the axis the sweep runs along ------------------------------------------------------
        v = seg(line, v, X0, BASE_U, X0 + W, BASE_U, SceneParts.CHALK, 0.38f)
        if (kit.quality < 2) {
            // A tick per node unit of corridor, so the gauge's x is the corridor's x and not an
            // abstract axis that happens to be nearby.
            for (t in 1 until SPAN.toInt()) {
                val st = X0 + W * t / SPAN
                v = seg(line, v, st, BASE_U - 0.035f, st, BASE_U + 0.035f, SceneParts.CHALK, 0.30f)
            }
        }

        // --- the roof, plotted small -------------------------------------------------------------
        // Drawn its whole length, swept or not: the roof exists whether or not you have flown under
        // it. Only the fill knows about the cursor.
        var kk = 0
        while (kk + stp <= TABLE) {
            v = seg(line, v,
                X0 + W * kk / TABLE, BASE_U + hs[kk] * vs,
                X0 + W * (kk + stp) / TABLE, BASE_U + hs[kk + stp] * vs,
                SceneParts.CHALK, 0.85f)
            kk += stp
        }

        // --- ribs, so the depth of the wake can be COMPARED rather than admired ------------------
        // Eight of them, evenly spaced, behind the cursor only. Where the roof is high the rib is
        // long, and that is the whole of "where the roof is high, the wake is deep".
        if (kit.quality == 0) {
            for (r in 1..8) {
                val rk = r * TABLE / 8
                if (rk > xs) break
                v = seg(line, v, X0 + W * rk / TABLE, BASE_U,
                    X0 + W * rk / TABLE, BASE_U + hs[rk] * vs, SceneParts.WORK, 0.32f)
            }
        }

        // --- the emitter ---------------------------------------------------------------------
        // The bright edge being laid down right now, answering the sound cue exactly as the
        // ambient's does at the craft itself.
        val hot = 0.55f + 0.40f * kit.beat
        v = seg(line, v, sCur, BASE_U, sCur, BASE_U + hCur, SceneParts.HOT, hot)

        // --- the mark left by the first run ------------------------------------------------------
        if (ph[3] > 0.5f) {
            SceneParts.at(g, BAR_S - 0.06f, BASE_U + BAR_FULL, 0f, o)
            SceneParts.at(g, BAR_S + BAR_W + 0.06f, BASE_U + BAR_FULL, 0f, du)
            v = if (kit.quality < 2) {
                MathMesh.dashed(line, v, o[0], o[1], o[2], du[0], du[1], du[2], 5,
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.85f)
            } else {
                MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.85f)
            }
        }

        kit.flushTris(tv[0])
        kit.flushLines(v, 2.2f)

        // --- the craft, riding the axis -----------------------------------------------------------
        // Its glow tracks its speed and nothing else does. On the second run it dims to a crawl
        // over the hump while the column beside it climbs at its fastest, which is the argument.
        SceneParts.at(g, sCur, BASE_U, 0f, o)
        kit.ball(o[0], o[1], o[2], 0.045f, 0.045f, 0.045f, SceneParts.HOT, SceneParts.WORK,
            1f, 0f, 0f, 1f, 0f, 0f, 0.4f + 1.4f * ph[1])

        // The flash as the second run lands on the first run's mark.
        val land = SceneParts.step(SceneParts.cycle(kit.seconds, PERIOD), 0.90f, 0.05f)
        if (land > 0.02f && land < 0.98f) {
            SceneParts.at(g, BAR_S + BAR_W * 0.5f, BASE_U + BAR_FULL, 0f, o)
            val fl = 1f - land
            kit.ball(o[0], o[1], o[2], 0.075f, 0.075f, 0.075f, SceneParts.ADDED, SceneParts.HOT,
                fl, 0f, 0f, 1f, 0f, 0f, 3f * fl)
        }

        // --- notation ------------------------------------------------------------------------
        // Both labels sit inboard of the figure and to one side of it. The HUD owns the top of the
        // eye and the caption box the bottom, so nothing here is hung above or below the drawing.
        val gl = 0.19f
        SceneParts.at(g, X0 - 0.08f, BASE_U + hs[0] * vs, 0f, o)
        kit.text("f", o[0], o[1], o[2], gl, SceneParts.CHALK, 0.95f, GlyphBoard.Style.MATH,
            1f, anchor = 0.5f)

        // The column named for what it holds. It caps the column rather than floating beside it,
        // because the gap between the plot and the column is narrower than the string.
        SceneParts.at(g, BAR_S + BAR_W * 0.5f, BASE_U + BAR_FULL + 0.14f, 0f, o)
        kit.text("∫_0^x f", o[0], o[1], o[2], gl, SceneParts.WORK, 0.95f)

        if (kit.quality == 0) {
            SceneParts.at(g, sCur, BASE_U - 0.13f, 0f, o)
            kit.text("x", o[0], o[1], o[2], gl * 0.8f, SceneParts.HOT, 0.85f, GlyphBoard.Style.SMALL)
        }
    }
}
