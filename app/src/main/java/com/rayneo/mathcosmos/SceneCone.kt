package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The double cone, lying across the passage with a plane turning slowly through it.
 *
 * This is the stop the whole first tour is built toward, and it only works if the section is
 * COMPUTED. A scene that drew four stock curves and named them would be a slideshow; what makes
 * the claim land is that there is one cone, one plane, one continuous motion, and the bright curve
 * where they meet changes character on its own while you watch. So nothing here is keyframed: the
 * plane's tilt is the only animated number, and the circle, the ellipse, the parabola and the two
 * branches of the hyperbola all fall out of solving the same quadratic at different tilts.
 *
 * THE MATHEMATICS. Work in the stage's own coordinates (s, u, a) — side, up, along the rail — with
 * the cone's axis along u and its apex at the stage centre, so the craft flies through the middle
 * of it. The cone is
 *
 *     s^2 + a^2 = (k u)^2,      k = tan(30 degrees)
 *
 * drawn out to |u| = H, which is what gives it a rim to be clipped against. The cutting plane
 * hinges about the line u = PH, a = 0 — a line along the stage's side vector, horizontal and in
 * the stage plane — so its in-plane basis is
 *
 *     e1 = s,     e2 = cos(t) a + sin(t) u,     through P0 = (0, PH, 0)
 *
 * with t the tilt away from square-on to the axis. A point of the plane at plane coordinates
 * (p, q) is therefore (p, PH + q sin t, q cos t), and substituting into the cone gives
 *
 *     p^2 + A q^2 - 2 k^2 PH sin(t) q - k^2 PH^2 = 0,      A = cos^2(t) - k^2 sin^2(t)
 *
 * which is the section, in the plane's own two coordinates, with no rotation term because the
 * hinge is a symmetry axis of the whole arrangement. Completing the square in q collapses to
 *
 *     p^2 + A (q - B)^2 = C,   B = k^2 PH sin(t) / A,   C = k^2 PH^2 cos^2(t) / A
 *
 * and the sign of A is the entire story: A > 0 is an ellipse with semi-axes kPH cos t / sqrt(A)
 * and kPH cos t / A, A < 0 is a hyperbola with the same two expressions over |A| and a branch on
 * each nappe, and A = 0 — which is tan t = 1/k, the plane parallel to a generator — is the
 * parabola q = (p^2 - k^2 PH^2) / (2 k^2 PH sin t). One formula, three shapes, one sign.
 *
 * The unbounded sections are clipped at the cone's rim rather than run off to infinity, and
 * because u depends on q alone the clip is a single interval in q, solved for exactly and used as
 * the sampling range — so the parabola and the hyperbola arms are sampled densely over the part
 * that is actually visible instead of wasting samples out in the dark.
 *
 * The pane is oblong rather than square on purpose. A plane that reaches the rim of BOTH nappes
 * has to span at least H + PH along its tilt direction, and no amount of wishing makes that fit in
 * a square that also stays inside the passage; the hinge direction stays narrow instead.
 */
object SceneCone : MathScene {

    override val reach = 1.5f
    // The cone stands across the passage with its axis up, near the rail, so it is framed square on.
    override val focusSide = -0.2f
    override val focusUp = 0.78f
    override val focusRadius = 2.5f

    // ---- the arrangement --------------------------------------------------------------------
    private const val SIDE = -0.2f          // the cone's axis, offset across the passage
    private const val PERIOD = 26f          // seconds for one full sweep and back
    private const val H = 1.4f              // half-height of the double cone: where the rim is
    private const val PH = 0.78f            // height of the plane's hinge above the apex
    private const val PANE_P = 1.25f        // pane half-width along the hinge
    private const val PANE_Q = 2.25f        // pane half-length along the tilt
    private const val DEG = 0.017453292f
    private const val TAU = 6.2831855f
    private const val PIF = 3.1415927f

    private const val FIT = 4.2f            // the passage radius these lengths were laid out for
    private const val ALPHA_DEG = 30f       // the cone's half-angle
    private const val CRIT_DEG = 90f - ALPHA_DEG   // where the plane is parallel to a generator

    /**
     * Below this the conic is a parabola for drawing purposes. |A| < 0.010 is within 0.6 degrees
     * of the critical tilt, and over the small piece of curve inside the rim the true conic and
     * the parabola differ by less than the line is wide — but it matters that the SWITCH is this
     * tight, because a wide window would be a lie dressed as a tolerance.
     */
    private const val A_EPS = 0.010f

    private const val KIND_ELLIPSE = 0
    private const val KIND_PARABOLA = 1
    private const val KIND_HYPERBOLA = 2

    private val K = tan(ALPHA_DEG * DEG)
    private val K2 = K * K
    private val RIM = K * H                 // radius of the cone at its rim

    // ---- scratch, all of it preallocated ----------------------------------------------------
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val sa = FloatArray(3)
    private val sb = FloatArray(3)
    private val w0 = FloatArray(3)
    private val w1 = FloatArray(3)
    private val tv = IntArray(1)

    /** The solved conic for this frame: B, aP, aQ, den, sin t, cos t, q window low, q window high. */
    private val con = FloatArray(8)

    /**
     * How much the whole arrangement is shrunk to fit the stop it is standing in. Written once at
     * the top of the frame and read by [put], so it has the same standing as the scratch arrays
     * and is not state the scene carries between frames.
     *
     * Every length above is quoted for a passage of radius [FIT], which is what stop 7 has today.
     * Scaling where stage coordinates become world coordinates, rather than in the constants,
     * means the conic is always solved in the cone's own units — so the tuning that makes the
     * ellipse just kiss the rim survives being flown into a narrower passage untouched.
     */
    private var sc = 1f

    /** A point of the arrangement, in the cone's own coordinates, placed in the world. */
    private fun put(s: Float, u: Float, a: Float, out: FloatArray) =
        SceneParts.at(g, s * sc, u * sc, a * sc, out)

    /**
     * The tilt, in degrees, as a loop: square-on, then a plain ellipse, then exactly the critical
     * angle, then well past it, then back. The dwells are the point — the craft may arrive at any
     * moment, and each of the four names has to be true and still for long enough to be read.
     * The rests land on 0 and on 37 + 23 = 60 exactly, so the circle is a circle and the parabola
     * is a parabola rather than something within a degree of one.
     */
    private fun tilt(seconds: Float): Float {
        val c = SceneParts.cycle(seconds, PERIOD)
        return 37f * SceneParts.step(c, 0.05f, 0.12f) +
            23f * SceneParts.step(c, 0.29f, 0.10f) +
            18f * SceneParts.step(c, 0.51f, 0.09f) -
            78f * SceneParts.step(c, 0.78f, 0.14f)
    }

    override fun readout(kit: SceneKit): String =
        String.format("TILT %.0f\u00b0   CONE %.0f\u00b0", tilt(kit.seconds), ALPHA_DEG)

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        sc = min(1f, n.radius / FIT)
        SceneParts.stage(kit, i.toFloat(), SIDE * sc, 0f, f, g)
        val qy = kit.quality

        val th = tilt(kit.seconds)
        val thr = th * DEG
        val sn = sin(thr)
        val cs = cos(thr)
        val aa = cs * cs - K2 * sn * sn

        // The clip window, in the plane's q coordinate. On the plane u = PH + q sin t, so the
        // cone's rim |u| <= H is a plain interval in q — the one piece of luck in this geometry.
        // The pane's own edge is folded in as well so no strand is ever drawn off the sheet.
        val qHi: Float
        val qLo: Float
        if (sn < 1e-4f) {
            qHi = PANE_Q; qLo = -PANE_Q
        } else {
            qHi = min((H - PH) / sn, PANE_Q)
            qLo = max((-H - PH) / sn, -PANE_Q)
        }

        val kind = solve(aa, sn, cs, qLo, qHi)

        val buf = kit.lineBuf
        tv[0] = 0
        var v = 0

        // ---- the cone ------------------------------------------------------------------------
        // Straight generators from the apex to both rims. A cone IS its generators — every conic
        // section is a statement about which of these lines the plane has managed to cross — so
        // this is the object itself, not decoration, and it survives the thermal step-down.
        val gens = if (qy == 0) 32 else if (qy == 1) 16 else 12
        put(0f, 0f, 0f, w0)
        for (j in 0 until gens) {
            val ang = TAU * j / gens
            val rs = RIM * cos(ang)
            val ra = RIM * sin(ang)
            put(rs, H, ra, w1)
            v = MathMesh.segment(buf, v, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2],
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.10f, 0.40f)
            put(rs, -H, ra, w1)
            v = MathMesh.segment(buf, v, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2],
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.10f, 0.40f)
        }
        // The two rims, which are what the sections get clipped against. Drawing them is honesty:
        // when the hyperbola stops, you can see the edge it stopped at.
        if (qy < 2) {
            val seg = if (qy == 0) 48 else 24
            put(0f, H, 0f, w0)
            v = MathMesh.arc(buf, v, w0[0], w0[1], w0[2], g[3], g[4], g[5], g[9], g[10], g[11],
                RIM * sc, 0f, TAU, seg, SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.55f)
            put(0f, -H, 0f, w0)
            v = MathMesh.arc(buf, v, w0[0], w0[1], w0[2], g[3], g[4], g[5], g[9], g[10], g[11],
                RIM * sc, 0f, TAU, seg, SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.55f)
            // The axis, dashed so it reads as construction rather than as a rod.
            put(0f, -H, 0f, w0)
            put(0f, H, 0f, w1)
            v = MathMesh.dashed(buf, v, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2], 12,
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.30f)
        }

        // ---- the cutting plane ---------------------------------------------------------------
        // e2 is the in-plane direction that carries the tilt; e1 is the hinge and never moves.
        val e2x = g[9] * cs + g[6] * sn
        val e2y = g[10] * cs + g[7] * sn
        val e2z = g[11] * cs + g[8] * sn
        val wp = 2f * PANE_P * sc
        val wq = 2f * PANE_Q * sc
        put(-PANE_P, PH - PANE_Q * sn, -PANE_Q * cs, w0)
        v = SceneParts.pane(
            kit, buf, v, kit.triBuf, tv,
            w0[0], w0[1], w0[2],
            g[3] * wp, g[4] * wp, g[5] * wp,
            e2x * wq, e2y * wq, e2z * wq,
            SceneParts.ADDED, 0.62f, 4, 7
        )
        if (qy < 2) {
            // The hinge itself. Without it the plane looks like it is drifting rather than turning.
            put(-PANE_P, PH, 0f, w0)
            put(PANE_P, PH, 0f, w1)
            v = MathMesh.dashed(buf, v, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2], 10,
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.45f)
        }
        // The half-angle, marked below the apex where the section never goes.
        if (qy == 0) {
            put(0f, 0f, 0f, w0)
            v = MathMesh.arc(buf, v, w0[0], w0[1], w0[2], -g[6], -g[7], -g[8], -g[3], -g[4], -g[5],
                0.55f * sc, 0f, ALPHA_DEG * DEG, 10,
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.55f)
        }

        // ---- the section ----------------------------------------------------------------------
        val ns = if (qy == 0) 128 else if (qy == 1) 72 else 44
        val hot = min(1f, 0.88f + 0.12f * kit.beat)
        v = section(buf, v, kind, ns, qLo, qHi, hot)
        kit.flushTris(tv[0])
        kit.flushLines(v, 2.6f)

        // ---- the solid marks -------------------------------------------------------------------
        put(0f, 0f, 0f, w0)
        val ar = 0.055f * sc
        kit.ball(w0[0], w0[1], w0[2], ar, ar, ar,
            SceneParts.HOT, SceneParts.WORK, 1f, 0f, 0f, 1f, 0f, 0f, 1.1f)
        if (qy < 2) {
            val hr = 0.042f * sc
            put(-PANE_P, PH, 0f, w0)
            kit.ball(w0[0], w0[1], w0[2], hr, hr, hr,
                SceneParts.ADDED, SceneParts.COOL, 0.9f, 0f, 0f, 1f, 0f, 0f, 0.6f)
            put(PANE_P, PH, 0f, w0)
            kit.ball(w0[0], w0[1], w0[2], hr, hr, hr,
                SceneParts.ADDED, SceneParts.COOL, 0.9f, 0f, 0f, 1f, 0f, 0f, 0.6f)
        }

        // ---- notation --------------------------------------------------------------------------
        // One word, set out beyond the rim at the height of the section it names. The window round
        // the critical angle is 1.2 degrees: inside it the drawn curve genuinely is the parabola to
        // within the width of the line, outside it the honest name is ellipse or hyperbola.
        val qMid = when (kind) {
            KIND_PARABOLA -> 0.5f * (-K2 * PH * PH / con[3] + qHi)
            KIND_HYPERBOLA -> con[0] + con[2]
            else -> con[0]
        }
        val name = when {
            abs(th) < 1f -> "CIRCLE"
            abs(th - CRIT_DEG) < 1.2f -> "PARABOLA"
            th < CRIT_DEG -> "ELLIPSE"
            else -> "HYPERBOLA"
        }
        put(RIM + 1.05f, PH + qMid * sn, 0f, w0)
        kit.text(name, w0[0], w0[1], w0[2], 0.36f * sc, SceneParts.HOT, 0.95f,
            GlyphBoard.Style.PLAIN, 1.2f)
        if (qy == 0) {
            put(-0.20f, -0.73f, 0f, w0)
            kit.text("30\u00b0", w0[0], w0[1], w0[2], 0.20f * sc, SceneParts.CHALK, 0.75f,
                GlyphBoard.Style.SMALL, 0.8f, anchor = 0.5f)
        }
    }

    /**
     * Solve the section into [con] and say which kind it is. Everything the drawing needs is the
     * sign of A and four numbers, so this is where the whole stop actually happens.
     */
    private fun solve(aa: Float, sn: Float, cs: Float, qLo: Float, qHi: Float): Int {
        con[3] = 0f
        con[4] = sn; con[5] = cs; con[6] = qLo; con[7] = qHi
        if (abs(aa) < A_EPS) {
            con[0] = 0f; con[1] = 0f; con[2] = 0f
            con[3] = 2f * K2 * PH * sn
            return KIND_PARABOLA
        }
        val m = abs(aa)
        con[0] = K2 * PH * sn / aa          // B, the centre's offset along the tilt
        con[1] = K * PH * cs / sqrt(m)      // semi-axis across the hinge
        con[2] = K * PH * cs / m            // semi-axis along the tilt
        return if (aa > 0f) KIND_ELLIPSE else KIND_HYPERBOLA
    }

    /**
     * The section, as one closed loop, one arc, or two branches. The parameter range is solved
     * from the clip rather than guessed, so an arm that is nine tenths outside the rim still gets
     * all of its samples spent on the tenth you can see.
     */
    private fun section(buf: FloatArray, at: Int, kind: Int, ns: Int, qLo: Float, qHi: Float, alpha: Float): Int {
        var v = at
        val c = SceneParts.HOT
        when (kind) {
            KIND_PARABOLA -> {
                // q grows with p^2, so the rim gives p directly.
                val p2 = qHi * con[3] + K2 * PH * PH
                if (p2 > 1e-6f) {
                    val pm = sqrt(p2)
                    v = strand(buf, v, kind, 1f, -pm, pm, ns, c, alpha)
                }
            }
            KIND_ELLIPSE -> {
                // sin t <= (qHi - B)/aQ is the visible part; its complement is one arc, so the
                // range runs from pi - asin through 2pi + asin and never has to be drawn in pieces.
                val s = (qHi - con[0]) / con[2]
                if (s >= 1f) {
                    v = strand(buf, v, kind, 1f, 0f, TAU, ns, c, alpha)
                } else if (s > -1f) {
                    val a0 = asin(s)
                    v = strand(buf, v, kind, 1f, PIF - a0, TAU + a0, ns, c, alpha)
                }
            }
            else -> {
                // One branch on each nappe. Either may be entirely past its rim, in which case
                // cosh has no solution and the branch is simply not there yet — which is the truth
                // about a cone of finite length, not a fudge.
                val c1 = (qHi - con[0]) / con[2]
                if (c1 > 1f) {
                    val tm = ln(c1 + sqrt(c1 * c1 - 1f))
                    v = strand(buf, v, kind, 1f, -tm, tm, ns, c, alpha)
                }
                val c2 = (con[0] - qLo) / con[2]
                if (c2 > 1f) {
                    val tm = ln(c2 + sqrt(c2 * c2 - 1f))
                    v = strand(buf, v, kind, -1f, -tm, tm, ns, c, alpha)
                }
            }
        }
        return v
    }

    /**
     * One strand, sampled and mapped back into world space through the stage vectors. The clip is
     * re-tested per sample as well as being solved for by the caller: the analytic range is what
     * keeps the sampling dense, the per-sample test is what guarantees that nothing is ever drawn
     * outside the cone or off the sheet when the two disagree in the last decimal place.
     */
    private fun strand(
        buf: FloatArray, at: Int, kind: Int, branch: Float,
        t0: Float, t1: Float, ns: Int, c: FloatArray, alpha: Float
    ): Int {
        var v = at
        var had = false
        for (j in 0..ns) {
            val t = t0 + (t1 - t0) * j / ns
            val p: Float
            val q: Float
            if (kind == KIND_PARABOLA) {
                p = t; q = (t * t - K2 * PH * PH) / con[3]
            } else if (kind == KIND_ELLIPSE) {
                p = con[1] * cos(t); q = con[0] + con[2] * sin(t)
            } else {
                p = con[1] * sinh(t); q = con[0] + branch * con[2] * cosh(t)
            }
            val ok = q >= con[6] && q <= con[7] && p >= -PANE_P && p <= PANE_P
            if (ok) {
                put(p, PH + q * con[4], q * con[5], sb)
                if (had) v = MathMesh.segment(buf, v, sa[0], sa[1], sa[2], sb[0], sb[1], sb[2],
                    c[0], c[1], c[2], alpha)
                sa[0] = sb[0]; sa[1] = sb[1]; sa[2] = sb[2]
            }
            had = ok
        }
        return v
    }
}
