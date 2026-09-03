package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tour II, stop 6 — THE STRAIGHT WORLD. "Zoom far enough into a smooth curve and it IS a line —
 * or near enough that I can't tell."
 *
 * The spec asks for a world-inflate ×8, twice, about the ship, so that the TRACE overhead visibly
 * flattens and the tangent strut and the curve become one object. A scene cannot inflate the
 * world — that is the renderer's ladder, and it belongs to the craft, not to a landmark — so this
 * stop does the same thing at arm's length: a LENS hung beside the rail, looking at the tour's own
 * roof curve at the stop, magnified ×1 → ×8 → ×64 about the point directly overhead. What the
 * viewer would have felt in the corridor they instead watch happen inside a frame, and the
 * arithmetic of it is identical.
 *
 * Two things are drawn, and the second one is the whole reason this scene exists.
 *
 *  - THE WINDOW, the large frame. The curve inside it is a live sample of kit.traceHeight around
 *    this stop; the straight cyan strut across it is the tangent there. The strut is NAILED DOWN:
 *    under an isotropic zoom about the point of tangency it lands on exactly the same two corners
 *    of the frame at every magnification, so nothing about it moves and the only thing the eye can
 *    watch is the curve settling onto it. By ×64 they are one line.
 *
 *  - THE REFERENCE PANEL, the small frame to its left, which never changes. It shows six and a
 *    half node units of the same roof curve, bending exactly as much as it always did, with a
 *    bright box marking the sliver the big window is looking at. As the zoom climbs, that box
 *    shrinks to a dot and the two dashed callout lines converge on it. This is the honesty beat
 *    the script speaks out loud, drawn: the curve is not becoming straight, we are looking at less
 *    and less of it, and it is doing what it always did. The pair of red gap markers at the
 *    window's edges say the same thing at the other end — the gap shrinks, it never closes, and
 *    the number for it is on the HUD where a number can actually be read.
 *
 * The loop deliberately ends on the magnified view and springs back at the wrap, so the long rest
 * is on the finished state (eleven seconds of curve-and-strut being one object, which is what the
 * crew talks over) and the recoil into a visible bend is the first thing the next pass shows.
 *
 * One approximation to own up to: the lens samples kit.traceHeight raw, while the ambient trace
 * squeezes the ribbon it draws against the wall wherever the corridor is tighter than the curve.
 * At this stop, radius 3.6 against a roof at about 1.85, the ceiling is nowhere near and the two
 * agree exactly. Hang this scene at a stop where the passage closes and the lens would show the
 * true curve while the roof overhead is the compressed one.
 *
 * On the window's vertical scale: it is chosen once per frame, from the local slope and curvature,
 * so the ×1 view fills the frame at whatever the roof happens to be doing at this stop rather than
 * being tuned to Tour II's particular trace function. That makes the view anisotropic — one scale
 * across, another up — which is a compromise and worth naming as one. It costs nothing here: both
 * scales are multiplied by the SAME magnification, so the residual between curve and strut still
 * falls exactly as 1/m on screen, which is the fact the stop is about. Only the picture's aspect
 * is fixed up front, and it is fixed once and never moves again.
 */
object SceneStraightWorld : MathScene {

    override val reach = 1.4f

    // ---- the loop ---------------------------------------------------------------------------
    private const val PERIOD = 24f
    private const val MAG_STEP = 8f          // one inflate step; two of them, exactly as the spec asks

    // ---- the window -------------------------------------------------------------------------
    // A flat figure centred on the rail is a figure you fly INTO. The whole assembly hangs to one
    // side and measures about 1.9 units across, which at this stop's passage radius of 3.6 leaves
    // it well clear of the wall with the notation still inboard.
    private const val SIDE = -1.20f
    private const val UP = 0.10f
    private const val WIN_S = 0.34f          // window centre, in stage coordinates
    private const val W = 0.62f              // window half-width
    private const val H = 0.62f              // window half-height
    private const val TX = 0.62f             // node units either side of the stop shown at ×1
    private const val KX = W / TX            // world units per node unit across, at ×1

    // ---- the reference panel ------------------------------------------------------------------
    private const val REF_S = -0.66f
    private const val REF_R = 0.24f          // half-size of the little frame
    private const val TR = 3.2f              // node units either side it shows — five zoom levels out
    private const val REF_N = 40

    private const val EPS = 0.045f           // central-difference step for slope and curvature
    private const val AHEAD = 0.004f         // rail-wise separation, so coincident lines do not z-fight

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val tv = IntArray(1)
    // The reference curve's heights, sampled once and scanned for their range before being drawn,
    // so the little panel fills itself whatever the roof is doing. One pass, no second sampling.
    private val refD = FloatArray(REF_N + 1)

    /**
     * The roof height at [p]. Tours II and IV have a trace and this is it; the proving ground has
     * none, and rather than magnify a straight line at zero — which would make this stop show the
     * exact opposite of what it claims — it falls back to a curve of its own so the landmark can
     * still be put in front of your eyes on a bare rail.
     */
    private fun roofAt(kit: SceneKit, p: Float): Float =
        if (kit.hasTrace) kit.traceHeight(p)
        else 1.55f + 0.85f * sin(p * 0.62f) + 0.30f * sin(p * 1.45f + 1.1f)

    /**
     * The magnification exponent over the cycle: 2 at the wrap, springing back to 0, held, then
     * climbing a whole step at a time to 2 again and resting there for the last third. Written as
     * a sum of eased steps so it is continuous across the wrap — a zoom that jumped would read as
     * a dropped frame rather than as a recoil.
     */
    private fun zoomExp(c: Float): Float =
        2f * (1f - SceneParts.step(c, 0.00f, 0.14f)) +
            SceneParts.step(c, 0.26f, 0.16f) + SceneParts.step(c, 0.50f, 0.16f)

    /** Magnification. Geometric, because that is the only way a zoom can be interpolated. */
    private fun zoom(c: Float): Float = MAG_STEP.pow(zoomExp(c))

    /** A short mantissa-and-exponent form. String.format would drag a Formatter in for this. */
    private fun sci(x: Float): String {
        if (x <= 1e-20f) return "0"
        var v = x
        var e = 0
        while (v < 1f && e > -20) { v *= 10f; e-- }
        while (v >= 10f && e < 20) { v /= 10f; e++ }
        var t = (v * 10f + 0.5f).toInt()
        if (t >= 100) { t = 10; e++ }
        return "${t / 10}.${t % 10}e$e"
    }

    /**
     * The magnification, the half-window in node units — which IS this stop's cut, h — and the gap
     * between the curve and its tangent at the window's edge.
     *
     * The gap is the honest half of the stop and it is a number, not a shape, once it is smaller
     * than a pixel: ½|f″(a)|h², falling by a hundredfold for every eightfold zoom and never once
     * reaching zero. Recomputed here from the rail rather than cached out of draw(), so it is
     * right whichever order the HUD and the scene are walked in.
     */
    override fun readout(kit: SceneKit): String {
        val a = kit.progress.toInt().coerceIn(0, max(0, kit.stopCount - 1)).toFloat()
        val d2 = (roofAt(kit, a + EPS) - 2f * roofAt(kit, a) + roofAt(kit, a - EPS)) / (EPS * EPS)
        val m = zoom(SceneParts.cycle(kit.seconds, PERIOD))
        val h = TX / m
        return "ZOOM ×${(m + 0.5f).toInt()}   h ${sci(h)}   GAP ${sci(0.5f * abs(d2) * h * h)}"
    }

    /**
     * One edge marker: a stroke from the tangent to the curve, with a serif at each end so a gap
     * a few pixels tall still reads as a measured distance rather than as a stray scratch.
     *
     * It is drawn at its true length and no minimum is applied. A marker padded up to stay visible
     * would be a lie about exactly the quantity this stop is asking you to trust, so it is allowed
     * to dwindle to nothing; the HUD keeps the number.
     */
    private fun gapMark(line: FloatArray, at: Int, s: Float, u0: Float, u1: Float, q: Int): Int {
        var k = at
        val c = SceneParts.TAKEN
        SceneParts.at(g, s, u0, AHEAD * 2f, o)
        SceneParts.at(g, s, u1, AHEAD * 2f, du)
        k = MathMesh.segment(line, k, o[0], o[1], o[2], du[0], du[1], du[2], c[0], c[1], c[2], 0.95f)
        if (q == 0) {
            val w = 0.045f
            SceneParts.at(g, s - w, u0, AHEAD * 2f, o)
            SceneParts.at(g, s + w, u0, AHEAD * 2f, du)
            k = MathMesh.segment(line, k, o[0], o[1], o[2], du[0], du[1], du[2], c[0], c[1], c[2], 0.75f)
            SceneParts.at(g, s - w, u1, AHEAD * 2f, o)
            SceneParts.at(g, s + w, u1, AHEAD * 2f, du)
            k = MathMesh.segment(line, k, o[0], o[1], o[2], du[0], du[1], du[2], c[0], c[1], c[2], 0.75f)
        }
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val a = i.toFloat()
        SceneParts.stage(kit, a, SIDE, UP, f, g)

        val q = kit.quality
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // Slope and curvature of the tour's OWN roof at this stop, by central difference. The
        // scene owns no curve of its own on purpose: what is under the lens is the corridor the
        // viewer has been flying beneath for five stops, so the strut drawn here is the tangent to
        // the thing they already know, not to a specimen brought along for the demonstration.
        val fm = roofAt(kit, a - EPS)
        val f0 = roofAt(kit, a)
        val fp = roofAt(kit, a + EPS)
        val d1 = (fp - fm) / (2f * EPS)
        val d2 = (fp - 2f * f0 + fm) / (EPS * EPS)

        // The largest vertical scale that still keeps the ×1 view inside the frame, from the
        // quadratic model of the curve over the half-window. Every higher magnification shows a
        // strictly smaller excursion, so this one bound holds for the whole zoom.
        val span = abs(d1) * TX + 0.5f * abs(d2) * TX * TX
        val ky = ((H * 0.92f) / max(span, 0.03f)).coerceIn(KX * 0.25f, KX * 4f)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val m = zoom(c)
        val hw = TX / m                       // half-window in node units: this is h
        val sc = KX * m                       // world units per node unit, across
        val su = ky * m                       // world units per unit of height, up

        // --- the window --------------------------------------------------------------------
        SceneParts.at(g, WIN_S - W, -H, 0f, o)
        SceneParts.vec(g, 2f * W, 0f, 0f, du)
        SceneParts.vec(g, 0f, 2f * H, 0f, dv)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], SceneParts.COOL, 0.50f
        )

        // --- the tangent, which never moves ---------------------------------------------------
        // Under this mapping the strut runs corner to corner at u = ±f′(a)·TX·ky at every single
        // magnification. Fixing it is what turns the zoom into an experiment with one variable.
        val tEnd = d1 * TX * ky
        val cl = SceneParts.ADDED
        SceneParts.at(g, WIN_S - W, -tEnd, -AHEAD, o)
        SceneParts.at(g, WIN_S + W, tEnd, -AHEAD, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], cl[0], cl[1], cl[2], 1f)

        // --- the curve -------------------------------------------------------------------------
        val ns = if (q == 0) 56 else if (q == 1) 28 else 16
        val cc = SceneParts.HOT
        SceneParts.at(g, WIN_S - W, (roofAt(kit, a - hw) - f0) * su, AHEAD, pa)
        for (j in 1..ns) {
            val t = -hw + 2f * hw * j / ns
            SceneParts.at(g, WIN_S + t * sc, (roofAt(kit, a + t) - f0) * su, AHEAD, pb)
            v = MathMesh.segment(
                line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], cc[0], cc[1], cc[2], 1f
            )
            pa[0] = pb[0]; pa[1] = pb[1]; pa[2] = pb[2]
        }

        // --- what is left over at the edges -----------------------------------------------------
        if (q < 2) {
            v = gapMark(line, v, WIN_S + W, tEnd, (roofAt(kit, a + hw) - f0) * su, q)
            v = gapMark(line, v, WIN_S - W, -tEnd, (roofAt(kit, a - hw) - f0) * su, q)
        }

        // --- the reference panel: the same curve, unmagnified, still bent ------------------------
        if (q < 2) {
            val rn = if (q == 0) REF_N else REF_N / 2
            var maxD = 0.05f
            for (j in 0..rn) {
                val t = -TR + 2f * TR * j / rn
                val d = roofAt(kit, a + t) - f0
                refD[j] = d
                if (abs(d) > maxD) maxD = abs(d)
            }
            // The thumbnail's own vertical scale, from the range it actually holds. Exaggerated
            // relative to its width, and unashamedly so — its job is to show that the curve bends,
            // not to be measured off. The window beside it is the honest picture.
            val refKy = (REF_R * 0.78f) / maxD
            val refKx = REF_R / TR

            SceneParts.at(g, REF_S - REF_R, -REF_R, 0f, o)
            SceneParts.vec(g, 2f * REF_R, 0f, 0f, du)
            SceneParts.vec(g, 0f, 2f * REF_R, 0f, dv)
            v = SceneParts.pane(
                kit, line, v, tri, tv, o[0], o[1], o[2],
                du[0], du[1], du[2], dv[0], dv[1], dv[2], SceneParts.STEEL, 0.40f
            )

            SceneParts.at(g, REF_S - REF_R, refD[0] * refKy, AHEAD, pa)
            for (j in 1..rn) {
                val t = -TR + 2f * TR * j / rn
                SceneParts.at(g, REF_S + t * refKx, refD[j] * refKy, AHEAD, pb)
                v = MathMesh.segment(
                    line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], cc[0], cc[1], cc[2], 0.75f
                )
                pa[0] = pb[0]; pa[1] = pb[1]; pa[2] = pb[2]
            }

            // The box marking the sliver under the lens, at its true size, and the two dashed
            // callout lines out to the window's near corners. By ×64 the box is a thousandth of
            // the panel and the callout has collapsed to a point — which is the argument.
            val bw = hw * refKx
            SceneParts.at(g, REF_S - bw, -bw, AHEAD * 2f, o)
            SceneParts.vec(g, 2f * bw, 0f, 0f, du)
            SceneParts.vec(g, 0f, 2f * bw, 0f, dv)
            v = SceneParts.edge(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.ADDED, 0.90f
            )
            if (q == 0) {
                val ck = SceneParts.CHALK
                SceneParts.at(g, REF_S + bw, bw, 0f, o)
                SceneParts.at(g, WIN_S - W, H, 0f, du)
                v = MathMesh.dashed(
                    line, v, o[0], o[1], o[2], du[0], du[1], du[2], 6, ck[0], ck[1], ck[2], 0.30f
                )
                SceneParts.at(g, REF_S + bw, -bw, 0f, o)
                SceneParts.at(g, WIN_S - W, -H, 0f, du)
                v = MathMesh.dashed(
                    line, v, o[0], o[1], o[2], du[0], du[1], du[2], 6, ck[0], ck[1], ck[2], 0.30f
                )
            }
        }

        kit.flushLines(v, 2.4f)
        kit.flushTris(tv[0])

        // --- the point of tangency ----------------------------------------------------------
        // The one place the curve and the strut agree exactly, at every magnification, and the
        // point the whole zoom is about. It sits dead centre and never moves.
        SceneParts.at(g, WIN_S, 0f, AHEAD * 3f, o)
        kit.ball(
            o[0], o[1], o[2], 0.030f, 0.030f, 0.030f, SceneParts.HOT, SceneParts.ADDED,
            1f, glow = 1.4f + kit.beat * 1.6f
        )
        if (q < 2) {
            SceneParts.at(g, REF_S, 0f, AHEAD * 3f, o)
            kit.ball(
                o[0], o[1], o[2], 0.020f, 0.020f, 0.020f, SceneParts.HOT, SceneParts.ADDED,
                0.90f, glow = 0.9f
            )
        }

        // --- notation ---------------------------------------------------------------------
        // Beside the window, never over or under it: the HUD owns the top of the eye and the
        // caption box the bottom, and both labels sit inside the figure's own height.
        val ls = WIN_S + W + 0.08f
        SceneParts.at(g, ls, 0.30f, 0f, o)
        kit.text(
            "f(a+h) ≈ f(a) + f'(a)h", o[0], o[1], o[2], 0.125f, SceneParts.CHALK, 1f,
            GlyphBoard.Style.MATH, 1f, anchor = -0.5f
        )
        if (q == 0) {
            // The ≈ is doing real work and stays for the whole loop: the gap named here is the one
            // the red markers are drawing, and it is second order, which is why it loses so fast.
            SceneParts.at(g, ls, -0.30f, 0f, o)
            kit.text(
                "gap ≈ f''(a)h^2/2", o[0], o[1], o[2], 0.115f, SceneParts.TAKEN, 0.95f,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f
            )
            SceneParts.at(g, REF_S - REF_R - 0.07f, 0f, 0f, o)
            kit.text(
                "f", o[0], o[1], o[2], 0.150f, SceneParts.HOT, 0.85f,
                GlyphBoard.Style.MATH, 1f, anchor = 0.5f
            )
        }
    }
}
