package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tour III, stop 3 — THE THINNING. "Keep halving the slab and the red goes to nothing; what is
 * left is the integral."
 *
 * The spec asks for the slabs of the stop before, halved four times, with each halving paired with
 * a world-inflate so the slabs keep the same APPARENT width while their true width halves. A scene
 * cannot inflate the world — that ladder belongs to the craft, and on this leg the renderer is
 * already doing it (TourMap's lengthM steps 0.5 → 10⁻² → 10⁻⁴ between node 1.6 and node 3, which
 * is exactly the stretch this stop sits in). So the corridor does the real inflate and this
 * landmark does the watchable one: a LOUPE hung beside the rail, looking at the tour's own roof
 * curve, halving its window on a loop. The corridor's ladder happens once and a viewer who blinks
 * has missed it; the loupe happens every twenty-six seconds, which is what a railed tour needs.
 *
 * Two panels, and the second one is the honest half.
 *
 *  - THE LOUPE, above. Four slabs, and there are ALWAYS four: they never move, never change width
 *    on screen, and the only thing the eye can watch is the curve above them settling down onto
 *    their tops and the red draining out from between. That stillness is the whole trick — it is
 *    what makes "the same picture, at half the width" a thing you can see rather than a thing you
 *    are told. The vertical scale is nailed down for the life of the stop while the horizontal one
 *    inflates, so the picture is anisotropic. Owning up to it: the slabs' apparent height is
 *    therefore honest and their apparent width is a lie by exactly the magnification, which is the
 *    lie the spec asked for. The staircase of four different heights flattening into one flat top
 *    is local linearity, drawn.
 *
 *  - THE BRACKET, below. Two bars over a FIXED interval — the whole two node units the loupe
 *    started from — the under-estimate and the over-estimate at the same Δx. Their right-hand ends
 *    close on one another and the red between them halves every time the loupe halves. Without
 *    this panel the stop would be cheating: shrinking the window shrinks the area under it too,
 *    and a viewer would be right to ask whether the red vanished because the sum got better or
 *    because there was less of everything. The bars answer that. They never shrink.
 *
 * The bracket width is exact and costs two samples. For any f whatsoever the right-endpoint sum
 * minus the left-endpoint sum telescopes to Δx·(f(b) − f(a)) — every interior term cancels — so
 * the gap between the two bars is known in closed form and halves precisely when Δx does. Only the
 * value the bars are closing ON needs the curve integrated, and that is a property of f, not of
 * the frame, so it is taken once per stop and remembered. The one approximation left is that the
 * bars are drawn centred on that value rather than at their own exact heights: the trapezoid sits
 * off the truth by O(Δx²) where the bracket is O(Δx), which here is under a tenth of a pixel at
 * the widest slab and falls away from there.
 *
 * Four halvings are watched one at a time, and then eight more run past at speed so the stop lands
 * on the tour's own rung of Δx = 10⁻⁴ rather than stopping short of it and asserting the rest.
 * Nothing here is asserted; the run-on is the same arithmetic, done faster than the eye follows.
 *
 * The craft's own length shrinking with Δx is the renderer's job and is deliberately not attempted
 * here — a landmark that tried to redraw the ship would be reaching outside its contract.
 */
object SceneThinning : MathScene {

    override val reach = 1.45f

    // ---- the loop -----------------------------------------------------------------------------
    private const val PERIOD = 26f
    private const val T0 = 0.10f             // when the first halving starts
    private const val SLOT = 0.115f          // one halving, moving for the first 55% and held after
    private const val SHOWN = 4              // halvings you actually watch
    private const val RUNON = 8f             // and eight more at speed: 0.5 · 2⁻¹² ≈ 10⁻⁴
    private const val MAXE = SHOWN + RUNON
    private const val RUN_AT = 0.70f
    private const val RUN_LEN = 0.08f

    // ---- the figure ---------------------------------------------------------------------------
    // Off to one side and about 1.7 units across. A flat figure centred on the rail is one you fly
    // INTO: at the closest point of the pass only a corner of it is in frame. At this stop's
    // passage radius of 3.2 the far corner sits 2.3 out, comfortably inside the wall.
    private const val SIDE = -1.30f
    private const val UP = 0.06f
    private const val HW = 0.85f             // half-width of both panels
    private const val BASE = 0.06f           // the loupe's zero line
    private const val TOP = 0.80f            // world height the tallest sample reaches
    private const val N = 4                  // slabs on show — this is the constant of the stop
    private const val SUB = 4                // curve samples per slab at full quality
    private const val W0 = 2.0f              // node units the loupe shows before any halving
    private const val DX0 = W0 / N           // = 0.5, which is the tour's own opening rung
    private const val CENTRE = -1.2f         // the zoom's fixed point, in node units from the stop

    private const val BAR_U = -0.38f         // the bracket's centre line
    private const val BAR_H = 0.075f         // one bar's thickness
    private const val BAR_GAP = 0.055f       // half the separation between the two bars
    private const val TRACK = 1.05f          // longest a bar may be, leaving the notation room

    private const val AHEAD = 0.003f         // rail-wise nudge so the slivers do not fight the slabs

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

    /** The loupe's curve samples, refilled each frame. N·SUB + 1 of them at full quality. */
    private val hs = FloatArray(N * SUB + 1)

    /** Magnification tags, one per whole halving. Built as literals so draw() never makes a string. */
    private val MAGS = arrayOf(
        "×1", "×2", "×4", "×8", "×16", "×32", "×64",
        "×128", "×256", "×512", "×1024", "×2048", "×4096"
    )

    // What the curve is, rather than what the frame is: taken once and remembered. kit.traceHeight
    // reaches a scene through a generic lambda, which boxes its argument and its result, so a
    // seventeen-sample scan every frame of every eye is a lot of short-lived Floats for an answer
    // that cannot change. Keyed on the tour as well as the stop so switching tours cannot serve a
    // stale integral.
    private var keyTour: String? = null
    private var keyStop = Int.MIN_VALUE
    private var hiF = 1f                     // largest |f| over the opening window: the vertical scale
    private var fA = 0f                      // f at the fixed interval's left end
    private var fB = 0f                      // and its right end — these two give the bracket exactly
    private var truth = 1f                   // the integral over that interval, by trapezoid

    /**
     * The roof at [p]. Tour III has one and this is it. A tour without a trace has no function to
     * take the limit of, and rather than halve slabs under a flat line at zero — which would show
     * the exact opposite of what the stop claims — it falls back to Tour III's own curve so the
     * landmark can still be put in front of your eyes on a bare rail.
     */
    private fun roofAt(kit: SceneKit, p: Float): Float =
        if (kit.hasTrace) kit.traceHeight(p) else 1.2f + 2.0f * sin(p * 0.75f)

    /** The curve's own numbers at this stop, sampled once. */
    private fun prime(kit: SceneKit, stop: Int) {
        if (keyStop == stop && keyTour == kit.tourTitle) return
        val a = stop + CENTRE - W0 * 0.5f
        val steps = N * SUB
        var prev = roofAt(kit, a)
        var hi = max(0.2f, abs(prev))
        var sum = 0f
        fA = prev
        for (j in 1..steps) {
            val h = roofAt(kit, a + W0 * j / steps)
            sum += (prev + h) * 0.5f
            prev = h
            if (abs(h) > hi) hi = abs(h)
        }
        fB = prev
        truth = sum * (W0 / steps)
        hiF = hi
        keyStop = stop
        keyTour = kit.tourTitle
    }

    /**
     * How many halvings deep we are, as a continuous exponent. Four of them one at a time with a
     * hold after each, a pause on ×16 long enough to be looked at, then the run-on to 10⁻⁴, then
     * the rest. The recoil at the wrap is eased over the exponent rather than over the width,
     * because a zoom is a geometric quantity and interpolating it any other way reads as a dropped
     * frame rather than as the loupe pulling back out.
     */
    private fun zoomExp(c: Float): Float {
        val r = (c - T0) / SLOT
        if (r < 0f) return MAXE * (1f - SceneParts.step(c, 0.004f, 0.072f))
        if (r >= SHOWN) return SHOWN + RUNON * SceneParts.step(c, RUN_AT, RUN_LEN)
        val k = floor(r)
        return k + SceneParts.ease((r - k) / 0.55f)
    }

    /** Fixed to a thousandth while that says anything, and an exponent once it does not. */
    private fun num(v: Float): String {
        if (v <= 1e-9f) return "0"
        if (v >= 0.001f) {
            val t = (v * 1000f + 0.5f).toInt()
            val fr = t % 1000
            return "${t / 1000}." + (if (fr < 10) "00$fr" else if (fr < 100) "0$fr" else "$fr")
        }
        var m = v
        var e = 0
        while (m < 1f && e > -12) { m *= 10f; e-- }
        val d = (m * 10f + 0.5f).toInt().coerceIn(10, 99)
        return "${d / 10}.${d % 10}e$e"
    }

    /**
     * The two numbers the stop is actually measuring. The bracket is exact for any f — it is
     * Δx·(f(b) − f(a)), the telescoped difference of the two endpoint sums — so this is a
     * measurement and not an estimate, and it is the one number that has to be READ rather than
     * looked at. Rebuilt from progress rather than from whatever draw() last saw, so it is right
     * whichever order the HUD and the scene are walked in.
     */
    override fun readout(kit: SceneKit): String {
        val stop = kit.progress.toInt().coerceIn(0, max(0, kit.stopCount - 1))
        prime(kit, stop)
        val dx = DX0 * 2f.pow(-zoomExp(SceneParts.cycle(kit.seconds, PERIOD)))
        return "Δx ${num(dx)}   BRACKET ${num(abs(dx * (fB - fA)))}"
    }

    /** A stroke between two points of the figure. */
    private fun stroke(
        line: FloatArray, at: Int, s0: Float, u0: Float, s1: Float, u1: Float,
        ahead: Float, c: FloatArray, a: Float
    ): Int {
        SceneParts.at(g, s0, u0, ahead, o)
        SceneParts.at(g, s1, u1, ahead, du)
        return MathMesh.segment(line, at, o[0], o[1], o[2], du[0], du[1], du[2], c[0], c[1], c[2], a)
    }

    /** An upright rectangle of the figure: translucent face, optional bright rim. */
    private fun box(
        line: FloatArray, lv: Int, tri: FloatArray,
        sA: Float, uA: Float, sB: Float, uB: Float,
        c: FloatArray, fillA: Float, rimA: Float
    ): Int {
        SceneParts.at(g, sA, uA, 0f, o)
        SceneParts.vec(g, sB - sA, 0f, 0f, du)
        SceneParts.vec(g, 0f, uB - uA, 0f, dv)
        tv[0] = SceneParts.fill(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, fillA)
        return if (rimA > 0f) SceneParts.edge(
            line, lv, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, rimA
        ) else lv
    }

    /**
     * One trapezoid of the sliver a slab fails to reach: flat along the slab's top, following the
     * curve along the other edge. Not a parallelogram, so it cannot go through MathMesh.quad.
     * Written to take either sign, because a slab under a falling stretch of curve overshoots and
     * the red belongs below its top rather than above it.
     */
    private fun band(
        tri: FloatArray, at: Int,
        sA: Float, flatA: Float, curveA: Float,
        sB: Float, flatB: Float, curveB: Float,
        c: FloatArray, a: Float
    ): Int {
        if ((at + 6) * MathMesh.STRIDE > tri.size) return at
        SceneParts.at(g, sA, flatA, AHEAD, q0)
        SceneParts.at(g, sB, flatB, AHEAD, q1)
        SceneParts.at(g, sB, curveB, AHEAD, q2)
        SceneParts.at(g, sA, curveA, AHEAD, q3)
        var k = MathMesh.vertex(tri, at, q0[0], q0[1], q0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q1[0], q1[1], q1[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q2[0], q2[1], q2[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q0[0], q0[1], q0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q2[0], q2[1], q2[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, q3[0], q3[1], q3[2], c[0], c[1], c[2], a)
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        prime(kit, i)
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val q = kit.quality
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val e = zoomExp(c)
        val shrink = 2f.pow(-e)
        val dx = DX0 * shrink
        val win = W0 * shrink
        val winA = i + CENTRE - win * 0.5f
        val fused = c > 0.755f

        // The curve, sampled across whatever the window has become. Four samples per slab is
        // plenty: past the first halving the thing is very nearly a straight line, which is the
        // point of the stop.
        val sub = if (q == 0) SUB else SUB / 2
        val m = N * sub
        for (j in 0..m) hs[j] = roofAt(kit, winA + win * j / m)

        // Fixed for the life of the stop, from the widest window. If this tracked the current
        // window the slabs would grow as they thinned and the stillness the stop depends on would
        // be gone.
        val vs = TOP / max(hiF, 0.2f)
        val wSlab = 2f * HW / N

        // --- the loupe's frame ------------------------------------------------------------------
        v = stroke(line, v, -HW, BASE, HW, BASE, AHEAD, SceneParts.CHALK, 0.50f)
        if (q < 2) {
            v = stroke(line, v, -HW, BASE, -HW, BASE + TOP, AHEAD, SceneParts.CHALK, 0.30f)
            v = stroke(line, v, HW, BASE, HW, BASE + TOP, AHEAD, SceneParts.CHALK, 0.30f)
        }

        // --- the slabs, and the red they do not reach --------------------------------------------
        for (k in 0 until N) {
            val s0 = -HW + wSlab * k
            val flat = BASE + hs[k * sub] * vs
            v = box(line, v, tri, s0, BASE, s0 + wSlab, flat, SceneParts.WORK, 0.24f, 0.90f)
            // The sliver is allowed to dwindle to nothing on its own. No floor is put under it and
            // no extra fade is applied: what thins is the geometry, because that is the claim.
            for (j in 0 until sub) {
                val sA = s0 + wSlab * j / sub
                val sB = s0 + wSlab * (j + 1) / sub
                tv[0] = band(
                    tri, tv[0],
                    sA, flat, BASE + hs[k * sub + j] * vs,
                    sB, flat, BASE + hs[k * sub + j + 1] * vs,
                    SceneParts.TAKEN, 0.60f
                )
            }
        }

        // --- the curve itself ---------------------------------------------------------------------
        for (j in 0 until m) {
            v = stroke(
                line, v,
                -HW + 2f * HW * j / m, BASE + hs[j] * vs,
                -HW + 2f * HW * (j + 1) / m, BASE + hs[j + 1] * vs,
                AHEAD * 2f, SceneParts.HOT, 0.95f
            )
        }

        // --- the inflate, made visible ------------------------------------------------------------
        // Two guides that start on the middle half of the frame — the part about to become the
        // whole of it — and sweep out to the edges as the halving happens. Without them the loupe
        // would simply be a curve that flattens for no stated reason.
        if (q < 2) {
            val r = (c - T0) / SLOT
            if (r > 0f && r < SHOWN) {
                val frac = ((r - floor(r)) / 0.55f).coerceIn(0f, 1f)
                val gs = HW * (0.5f + 0.5f * frac)
                val ga = 0.80f * (1f - abs(2f * frac - 1f))
                v = stroke(line, v, -gs, BASE, -gs, BASE + TOP, AHEAD * 3f, SceneParts.ADDED, ga)
                v = stroke(line, v, gs, BASE, gs, BASE + TOP, AHEAD * 3f, SceneParts.ADDED, ga)
            }
        }

        // --- the width being halved, braced under the first slab ----------------------------------
        if (q == 0) {
            v = stroke(line, v, -HW, BASE - 0.04f, -HW, BASE - 0.07f, AHEAD, SceneParts.CHALK, 0.55f)
            v = stroke(line, v, -HW + wSlab, BASE - 0.04f, -HW + wSlab, BASE - 0.07f, AHEAD, SceneParts.CHALK, 0.55f)
            v = stroke(line, v, -HW, BASE - 0.055f, -HW + wSlab, BASE - 0.055f, AHEAD, SceneParts.CHALK, 0.55f)
        }

        // --- the bracket ---------------------------------------------------------------------------
        // The bars are over the fixed two units the loupe opened on, and they do not move with the
        // zoom. Scaled so the widest over-estimate of the whole loop just fits the track, which
        // means the scale is a property of the curve and not of a number tuned to Tour III.
        val gap0 = abs(DX0 * (fB - fA))
        val gap = abs(dx * (fB - fA))
        val bs = (TRACK * 0.94f) / max(abs(truth) + gap0 * 0.5f, 0.05f)
        val sZero = -HW
        val sLow = sZero + (truth - gap * 0.5f) * bs
        val sHigh = sZero + (truth + gap * 0.5f) * bs
        val sTruth = sZero + truth * bs

        val upA = BAR_U + BAR_GAP
        val upB = upA + BAR_H
        val loB = BAR_U - BAR_GAP
        val loA = loB - BAR_H

        // The over-estimate: solid to where the under-estimate ends, then the bracket in red.
        v = box(line, v, tri, sZero, upA, sLow, upB, SceneParts.COOL, 0.30f, 0.85f)
        v = box(line, v, tri, sLow, upA, sHigh, upB, SceneParts.TAKEN, 0.55f, 0.85f)
        // The under-estimate.
        v = box(line, v, tri, sZero, loA, sLow, loB, SceneParts.COOL, 0.30f, 0.85f)
        // The answer both of them are closing on, drawn through the red so it can be seen sitting
        // inside the bracket rather than merely near it.
        if (q < 2) {
            v = stroke(line, v, sTruth, loA - 0.03f, sTruth, upB + 0.03f, AHEAD * 3f, SceneParts.HOT, 0.90f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // The two bars becoming one. One lamp, brief, at the end they meet at.
        val flash = SceneParts.step(c, 0.745f, 0.05f) * (1f - SceneParts.step(c, 0.80f, 0.10f))
        if (flash > 0.02f) {
            SceneParts.at(g, sHigh, BAR_U, 0f, o)
            kit.ball(
                o[0], o[1], o[2], 0.075f, 0.075f, 0.075f, SceneParts.HOT, SceneParts.ADDED,
                flash, 0f, 0f, 1f, 0f, 0f, 3f * flash
            )
        }

        // --- notation --------------------------------------------------------------------------------
        // Beside the figure, never over or under it: the HUD owns the top of the eye and the caption
        // box the bottom, and everything here sits in the band between them.
        if (q < 2) {
            SceneParts.at(g, -HW + 0.08f, BASE + TOP - 0.10f, 0f, o)
            kit.text("f", o[0], o[1], o[2], 0.16f, SceneParts.HOT, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        }

        // The one line that is the stop: a sum, until it is not.
        SceneParts.at(g, 0.30f, BAR_U, 0f, o)
        if (fused) {
            kit.text("∫ f dx", o[0], o[1], o[2], 0.17f, SceneParts.HOT, 1f, GlyphBoard.Style.MATH, 1.15f, anchor = -0.5f)
        } else {
            kit.text("Σ f Δx", o[0], o[1], o[2], 0.17f, SceneParts.COOL, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        }

        if (q == 0) {
            SceneParts.at(g, -HW + wSlab * 0.5f, BASE - 0.17f, 0f, o)
            kit.text("Δx", o[0], o[1], o[2], 0.13f, SceneParts.CHALK, 0.80f, GlyphBoard.Style.SMALL)
            SceneParts.at(g, HW, BASE - 0.17f, 0f, o)
            kit.text(
                MAGS[e.toInt().coerceIn(0, MAGS.size - 1)], o[0], o[1], o[2], 0.13f,
                SceneParts.ADDED, 0.80f, GlyphBoard.Style.SMALL, 1f, anchor = 0.5f
            )
        }
    }
}
