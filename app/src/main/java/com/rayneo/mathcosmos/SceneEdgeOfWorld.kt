package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Tour IV, stop 9 — THE EDGE OF THE WORLD. "Every one of these series has a distance beyond which
 * it stops meaning anything at all."
 *
 * The radius of convergence, and the reason DESIGN.md files this stop under the hard parts: the
 * easy case teaches a falsehood by omission and the honest case has its explanation somewhere you
 * cannot see. So both are flown, in that order, and the second is the reveal rather than the thing
 * we quietly leave out.
 *
 * Act one is 1/(1−x). A wall of light stands on the real axis at x = 1, the curve rockets up its
 * face, and four partial sums — orders 2, 4, 6 and 8 — hug the curve inside the wall and fly apart
 * outside it. Nobody needs this explained. The interval of convergence then grows out of the
 * anchor and locks when it touches the wall: R is the distance to the trouble.
 *
 * Act two swaps in 1/(1+x²). The wall goes out, the corridor is calm, the curve is a quiet bump
 * that is finite and smooth for every real x there is — and the same four partial sums tear apart
 * at exactly the same distance. Nothing on the line accounts for it. That is the whole stop.
 *
 * Act three is the answer, and it is a change of axis rather than a change of course. The graph
 * fades, taking the vertical axis with it, and a new axis grows out of the picture plane along the
 * corridor: the imaginary direction. Two poles light up at ±i, and the interval of convergence
 * OPENS — two arcs sweeping out of its ends until they close on the poles — while the whole plane
 * tips to face the eye. The interval you had was the shadow of a disc, and the disc's radius was
 * set by a pair of points that were never on your road.
 *
 * Four decisions worth recording.
 *
 *  - The vertical axis is deliberately re-used: it means f(x) in acts one and two and Im(x) in act
 *    three. That is a real risk of a lie, so the handover is made an event — the value axis is
 *    fully extinguished, and only a beat later does a differently coloured axis grow out of the
 *    picture plane and rotate into the vertical. The alternative, a third spatial axis held
 *    permanently out of plane, foreshortens to nothing on a 640x480 waveguide, which is the one
 *    failure this stop cannot afford.
 *
 *  - The excursion into the imaginary direction is the picture turning, not the craft leaving the
 *    rail. A scene may not steer, and it should not want to: the lesson is that the limiting thing
 *    lay off the plane you were reading, and a plane that visibly rotates out from under its own
 *    axis says that at least as plainly as a detour would.
 *
 *  - The brief asks for a sphere of light around the anchor. The region of convergence is a DISC
 *    in the complex plane, not a ball, so the sphere is drawn only as the faint halo the poles
 *    cast; the bright, filled, rimmed object is the disc, because the disc is what is true.
 *
 *  - There is a second wall at corridor scale — a ring across the passage in act one, gone in act
 *    two — so the stop is a place and not only a diagram. It is not to the figure's scale and is
 *    not meant to be measured against it; it is the same event said twice, once in the mathematics
 *    and once out of the window.
 *
 * A quiet honesty the picture makes for free: in act one there is a wall at +1 and nothing at all
 * at −1, yet the series dies at both. The crew do not stop on it, but a viewer who notices has
 * already worked out where act three is going.
 */
object SceneEdgeOfWorld : MathScene {

    override val reach = 1.4f
    override val deep = 0.2f

    // ---- the figure ----------------------------------------------------------------------
    // Off to port, about 2.2 units across. A flat figure centred on the rail is a figure you fly
    // INTO: at the closest point of the pass only a corner of it would be in frame.
    private const val SIDE = -1.25f
    private const val UP = 0.10f

    private const val U = 0.60f            // world units per unit of x — and so the disc's radius
    private const val RE_LO = -1.85f
    private const val RE_HI = 1.85f
    private const val CEIL_V = 2.4f        // where the plot is cut off, in units of value
    private const val HANG = 0.30f         // clearance kept between the crown and the roof
    private const val OUT_FADE = 0.62f     // how far past the edge a torn ribbon survives
    private const val TIP0 = 8f            // the imaginary axis is born just off the picture plane
    private const val PERIOD = 30f
    private const val TAU = 6.2831855f
    private const val PI = 3.1415927f
    private const val HALF_PI = 1.5707964f

    // The clock. Act one to 0.41, act two to 0.66, the reveal after it, and three seconds of rest
    // at the end with everything held, because a viewer arrives at any moment in the loop.
    private const val RIB_A = 0.100f       // when act one's orders start arriving
    private const val RIB_B = 0.440f       // and act two's
    private const val SWAP = 0.410f

    /** The four orders drawn at once. Degree in x, so both series are labelled the same way. */
    private val ORD = intArrayOf(2, 4, 6, 8)

    private val f = FloatArray(12)
    private val f2 = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val uR = FloatArray(3)         // unit real direction: the stage's own right
    private val uV = FloatArray(3)         // unit value direction: the stage's own up
    private val uF = FloatArray(3)         // unit rail-forward, kept only for the halo rings
    private val uI = FloatArray(3)         // unit imaginary direction: out of the picture plane
    private val prev = FloatArray(5)       // [0] the true curve, [1..4] the four partial sums
    private val cur = FloatArray(5)
    private val tv = IntArray(1)

    /** World units per unit of value, refitted every frame so the crown clears the roof. */
    private var vs = 0.45f

    // ---- the arithmetic ------------------------------------------------------------------

    /**
     * The function and its four partial sums at [x], in one pass.
     *
     * Both series are geometric in disguise, so both partial sums are closed form and there is no
     * loop over terms: S_N(x) = (1 − x^{N+1})/(1 − x) for the first, and Σ(−x²)^j, summed to M,
     * is (1 − q^{M+1})/(1 + x²) for the second. The powers are stepped by one multiply apiece
     * rather than four calls to pow — this runs seventy-odd times per eye per frame, and pow is
     * not free on this device.
     */
    private fun sample(x: Float, mode: Int, out: FloatArray) {
        if (mode == 0) {
            val d = 1f - x
            val near = abs(d) < 1e-3f
            out[0] = if (near) CEIL_V * 8f else 1f / d
            val x2 = x * x
            var pw = x * x2                                   // x^3
            for (k in 0 until 4) {
                // At the pole itself the partial sum is finite and equal to N+1; it is the
                // FUNCTION that blows up there, and the picture must not blur the two.
                out[k + 1] = if (near) (ORD[k] + 1).toFloat() else (1f - pw) / d
                pw *= x2                                      // x^5, x^7, x^9
            }
        } else {
            val den = 1f + x * x
            out[0] = 1f / den
            val q = -x * x
            var pw = q * q                                    // q^2
            for (k in 0 until 4) {
                out[k + 1] = (1f - pw) / den
                pw *= q                                       // q^3, q^4, q^5
            }
        }
    }

    /** A point in the graph register: [re] along the real axis, [v] up the value axis. */
    private fun ptV(re: Float, v: Float, out: FloatArray) {
        val a = re * U
        val b = v * vs
        out[0] = g[0] + uR[0] * a + uV[0] * b
        out[1] = g[1] + uR[1] * a + uV[1] * b
        out[2] = g[2] + uR[2] * a + uV[2] * b
    }

    /** A point in the complex register: [re] real, [im] imaginary, both in units of x. */
    private fun ptC(re: Float, im: Float, out: FloatArray) {
        val a = re * U
        val b = im * U
        out[0] = g[0] + uR[0] * a + uI[0] * b
        out[1] = g[1] + uR[1] * a + uI[1] * b
        out[2] = g[2] + uR[2] * a + uI[2] * b
    }

    // ---- the readout ----------------------------------------------------------------------

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return when {
            c < 0.30f -> "1/(1−x)   ORDER ${orderShown(c, RIB_A)}"
            c < SWAP -> "R 1.00   POLE AT x = 1"
            c < 0.52f -> "1/(1+x^2)   ORDER ${orderShown(c, RIB_B)}"
            c < 0.70f -> "R 1.00   NOTHING ON THE LINE"
            c < 0.82f -> "R 1.00   POLES AT ±i"
            else -> "R 1.00 = |±i|   DISC, NOT INTERVAL"
        }
    }

    /** Which order has arrived by now — the same schedule the ribbons are faded in on. */
    private fun orderShown(c: Float, from: Float): Int =
        ORD[((c - from) / 0.045f).toInt().coerceIn(0, 3)]

    // ---- pieces ---------------------------------------------------------------------------

    /** The shaded region of convergence, as a fan from the anchor out to the rim. */
    private fun discFill(tri: FloatArray, v: Int, segs: Int, alpha: Float): Int {
        var k = v
        val col = SceneParts.ADDED
        ptC(1f, 0f, pa)
        for (s in 1..segs) {
            val ang = TAU * s / segs
            ptC(cos(ang), sin(ang), pb)
            if ((k + 3) * MathMesh.STRIDE > tri.size) return k
            k = MathMesh.vertex(tri, k, g[0], g[1], g[2], col[0], col[1], col[2], alpha)
            k = MathMesh.vertex(tri, k, pa[0], pa[1], pa[2], col[0], col[1], col[2], alpha * 0.35f)
            k = MathMesh.vertex(tri, k, pb[0], pb[1], pb[2], col[0], col[1], col[2], alpha * 0.35f)
            pa[0] = pb[0]; pa[1] = pb[1]; pa[2] = pb[2]
        }
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val p = i.toFloat()
        SceneParts.stage(kit, p, SIDE, UP, f, g)

        // The value axis is refitted under the tour's roof curve, exactly as the neighbouring
        // stops do. The roof is drawn by the ambient scene and it is NOT this function — nothing
        // here is tied to it — but a landmark that keeps a fixed clearance under the ceiling it
        // stands in never punches through it, whatever the trace is doing at stop 9.
        val roofU = kit.traceHeight(p) - UP
        vs = ((roofU - HANG) / CEIL_V).coerceIn(0.30f, 0.56f)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)

        val mode = if (c < SWAP) 0 else 1
        // Two dips in the whole landmark's brightness: one where the function is exchanged, so the
        // swap is an event rather than a glitch, and one at the seam of the loop, so a scene that
        // ends with a disc and begins with a bare axis does not snap between them.
        val swapDip = 1f - 0.85f * ((0.045f - abs(c - SWAP)).coerceAtLeast(0f) / 0.045f)
        val seam = (c / 0.028f).coerceAtMost(1f) * ((1f - c) / 0.028f).coerceAtMost(1f)
        val soft = swapDip * seam

        val ribFrom = if (mode == 0) RIB_A else RIB_B
        val lock = SceneParts.step(c, 0.305f, 0.055f)            // the interval reaching ±1
        val tore = if (mode == 0) 0f else SceneParts.step(c, 0.575f, 0.05f)
        val graph = 1f - SceneParts.step(c, 0.660f, 0.055f)      // the whole graph register going
        val rev = SceneParts.step(c, 0.700f, 0.075f)             // the imaginary axis growing
        val tip = SceneParts.step(c, 0.790f, 0.100f)             // and the plane turning to face us
        val open = SceneParts.step(c, 0.800f, 0.095f)            // the interval opening into a disc
        val wall = if (mode == 0) SceneParts.step(c, 0.075f, 0.05f) else 0f

        // The four directions the landmark is built from. The imaginary one is born a few degrees
        // out of the picture plane — enough for stereo to say "not here" — and swings up into the
        // vertical once the value axis it is replacing has gone dark.
        val th = (TIP0 + (90f - TIP0) * tip) * 0.017453292f
        SceneParts.vec(g, 1f, 0f, 0f, uR)
        SceneParts.vec(g, 0f, 1f, 0f, uV)
        SceneParts.vec(g, 0f, 0f, 1f, uF)
        SceneParts.vec(g, 0f, sin(th), cos(th), uI)

        val samp = if (kit.quality == 0) 72 else if (kit.quality == 1) 44 else 30
        val ribStep = if (kit.quality >= 2) 3 else 1        // at q2, only orders 2 and 8

        // --- the real axis: the road, and the one thing on screen for the whole cycle -----------
        ptV(RE_LO, 0f, o)
        ptV(RE_HI, 0f, du)
        v = MathMesh.segment(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.75f * seam
        )
        if (kit.quality < 2) {
            for (q in 0 until 2) {
                val t = if (q == 0) -1f else 1f
                ptV(t, 0f, o)
                SceneParts.at(g, t * U, -0.055f, 0f, du)
                v = MathMesh.segment(
                    line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.6f * seam
                )
            }
        }

        // --- the value axis, while it still means anything ---------------------------------------
        if (graph > 0.02f) {
            ptV(0f, -0.25f, o)
            ptV(0f, CEIL_V, du)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.45f * graph * soft
            )
        }

        // --- the curve and its four partial sums --------------------------------------------------
        // One pass, five polylines. Inside |x| < 1 the sums lie along the curve and are drawn
        // solid; outside it their alpha decays and every third segment is dropped, so a ribbon
        // that has left the function does not merely wander off, it comes apart.
        if (graph > 0.02f) {
            var first = true
            var px = RE_LO
            for (si in 0..samp) {
                val x = RE_LO + (RE_HI - RE_LO) * si / samp
                sample(x, mode, cur)
                if (!first) {
                    val over = (abs(x) - 1f).coerceAtLeast(0f)
                    val outside = over > 0.001f
                    val tear = (1f - over / OUT_FADE).coerceIn(0f, 1f)
                    val shred = !outside || (si % 3 != 0)

                    // the function itself
                    val a0 = prev[0].coerceIn(-CEIL_V, CEIL_V)
                    val a1 = cur[0].coerceIn(-CEIL_V, CEIL_V)
                    if (abs(a0) < CEIL_V || abs(a1) < CEIL_V) {
                        ptV(px, a0, pa); ptV(x, a1, pb)
                        v = MathMesh.segment(
                            line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                            SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2],
                            0.95f * graph * soft
                        )
                    }

                    // and the four approximations laid over it
                    var k = 0
                    while (k < 4) {
                        val born = SceneParts.step(c, ribFrom + 0.045f * k, 0.035f)
                        if (born > 0.02f && shred) {
                            val b0 = prev[k + 1].coerceIn(-CEIL_V, CEIL_V)
                            val b1 = cur[k + 1].coerceIn(-CEIL_V, CEIL_V)
                            // A ribbon that has flown clean off the plot must not draw a long flat
                            // line along the ceiling on its way back.
                            if (abs(b0) < CEIL_V || abs(b1) < CEIL_V) {
                                ptV(px, b0, pa); ptV(x, b1, pb)
                                val col = if (outside) SceneParts.TAKEN else SceneParts.COOL
                                val al = (0.35f + 0.16f * k) * born * graph * soft *
                                    (if (outside) tear else 1f)
                                v = MathMesh.segment(
                                    line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                                    col[0], col[1], col[2], al
                                )
                            }
                        }
                        k += ribStep
                    }
                }
                System.arraycopy(cur, 0, prev, 0, 5)
                px = x
                first = false
            }
        }

        // --- the wall of light at x = 1 -------------------------------------------------------------
        // Act one only. A sheet standing ON the real axis, at figure scale; the corridor-scale
        // version of the same event is the ring below, and only one of the two is the mathematics.
        if (wall > 0.02f) {
            val flicker = 0.80f + 0.20f * sin(kit.seconds * 14f)
            ptV(1f, -0.30f, o)
            SceneParts.vec(g, 0.055f, 0f, 0f, du)
            SceneParts.vec(g, 0f, (CEIL_V + 0.3f) * vs, 0f, dv)
            tv[0] = SceneParts.fill(
                tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.HOT, 0.55f * wall * flicker * soft
            )
            v = SceneParts.edge(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.TAKEN, 0.95f * wall * flicker * soft
            )
        }

        // --- the two edges, which exist in both acts --------------------------------------------
        // Ticks on the real axis at ±1, where all four ribbons part company with the function. In
        // act two they are the only evidence there is, and they are exactly where the disc's rim
        // will later cross — which is what makes the closing picture an explanation and not a
        // second, prettier assertion.
        val gateH = 0.55f - 0.34f * rev
        for (q in 0 until 2) {
            val t = if (q == 0) -1f else 1f
            val flare = if (t > 0f && mode == 0) 0f else tore
            val al = (0.5f + 0.45f * flare) * (0.35f + 0.65f * max(graph, rev)) * soft
            val col = if (flare > 0.4f) SceneParts.TAKEN else SceneParts.ADDED
            ptV(t, -0.22f, o)
            ptV(t, gateH, du)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2], col[0], col[1], col[2], al
            )
        }

        // --- the interval of convergence, and the disc it turns out to be --------------------------
        // The bar is drawn from act one onward: R is a length on the road long before anyone knows
        // why it is THAT length. When the reveal comes the bar does not move — the two arcs simply
        // sweep out of its ends until they close on the poles.
        if (lock > 0.01f) {
            ptC(-lock, 0f, o)
            ptC(lock, 0f, du)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.9f * soft
            )
        }
        if (open > 0.005f) {
            val phi = HALF_PI * open
            val arcs = if (kit.quality == 0) 22 else if (kit.quality == 1) 14 else 9
            v = MathMesh.arc(
                line, v, g[0], g[1], g[2], uR[0], uR[1], uR[2], uI[0], uI[1], uI[2],
                U, -phi, phi, arcs,
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.85f * soft
            )
            v = MathMesh.arc(
                line, v, g[0], g[1], g[2], uR[0], uR[1], uR[2], uI[0], uI[1], uI[2],
                U, PI - phi, PI + phi, arcs,
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.85f * soft
            )
            // The region, shaded only once its rim has very nearly closed.
            val fillA = 0.16f * open * open * open * soft
            if (fillA > 0.004f) tv[0] = discFill(tri, tv[0], if (kit.quality == 0) 28 else 16, fillA)
        }

        // The halo. The brief asks for a sphere of light around the anchor; the region of
        // convergence is a disc, so the sphere is light and not mathematics — two faint rings in
        // planes the disc never rotates into, dropped entirely when the governor steps us down.
        if (kit.quality == 0 && rev > 0.4f) {
            val ha = 0.15f * rev * soft
            v = MathMesh.arc(
                line, v, g[0], g[1], g[2], uR[0], uR[1], uR[2], uF[0], uF[1], uF[2],
                U, 0f, TAU, 20, SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], ha
            )
            v = MathMesh.arc(
                line, v, g[0], g[1], g[2], uV[0], uV[1], uV[2], uF[0], uF[1], uF[2],
                U, 0f, TAU, 20, SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], ha
            )
        }

        // --- the imaginary axis --------------------------------------------------------------------
        if (rev > 0.02f) {
            val ext = 1.55f * rev
            ptC(0f, -ext, o)
            ptC(0f, ext, du)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.8f * rev * soft
            )
            if (kit.quality < 2) {
                for (q in 0 until 2) {
                    val t = if (q == 0) -1f else 1f
                    ptC(-0.05f, t, o)
                    ptC(0.05f, t, du)
                    v = MathMesh.segment(
                        line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                        SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.7f * rev * soft
                    )
                }
            }
        }

        // --- the wall at corridor scale ---------------------------------------------------------
        // Act one puts a ring of light across the passage a little ahead; act two takes it away and
        // leaves the corridor calm, which is the beat the whole stop turns on.
        if (kit.quality < 2 && wall > 0.02f) {
            val cp = p + 0.10f
            kit.frame(cp, f2)
            val rad = kit.radius(cp) * 0.78f
            val ring = if (kit.quality == 0) 32 else 18
            val flick = 0.75f + 0.25f * sin(kit.seconds * 9f)
            v = MathMesh.arc(
                line, v, f2[0], f2[1], f2[2], f2[6], f2[7], f2[8], f2[9], f2[10], f2[11],
                rad, 0f, TAU, ring,
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2],
                0.7f * wall * flick * soft
            )
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- lamps --------------------------------------------------------------------------------
        // The anchor: the point both series are expanded about, and the centre of the interval and
        // of the disc alike. The only lamp on screen for the whole cycle.
        kit.ball(
            g[0], g[1], g[2], 0.045f, 0.045f, 0.045f,
            SceneParts.HOT, SceneParts.ADDED, 0.95f * seam, 0f, 0f, 1f, 0f, 0f, 1.6f
        )
        // The pole on the road, act one.
        if (wall > 0.1f) {
            val pulse = 0.65f + 0.35f * sin(kit.seconds * 7f)
            ptV(1f, 0f, o)
            kit.ball(
                o[0], o[1], o[2], 0.07f, 0.07f, 0.07f,
                SceneParts.TAKEN, SceneParts.HOT, wall * pulse * soft,
                0f, 0f, 1f, 0f, 0f, 3.2f * pulse
            )
        }
        // And the pair that were never on it. They arrive as lamps rather than as marks because a
        // lamp reads at any orientation, and for the first few seconds these two are nearly
        // edge-on — which is precisely the point being made about them.
        if (rev > 0.03f && kit.quality < 2) {
            val pulse = 0.7f + 0.3f * sin(kit.seconds * 5f)
            for (q in 0 until 2) {
                val t = if (q == 0) -1f else 1f
                ptC(0f, t, o)
                val s = 0.05f + 0.025f * rev
                kit.ball(
                    o[0], o[1], o[2], s, s, s,
                    SceneParts.TAKEN, SceneParts.HOT, rev * pulse * soft,
                    0f, 0f, 1f, 0f, 0f, 3.0f * rev * pulse
                )
            }
        }

        // --- notation -------------------------------------------------------------------------------
        // Everything sits BESIDE the figure or hugs the axis inside it. The telemetry owns the top
        // quarter of the eye and the caption box the bottom fifth, so nothing here is stacked over
        // or under the drawing.
        ptV(RE_LO, 1.30f, o)
        kit.text(
            if (mode == 0) "1/(1−x)" else "1/(1+x^2)",
            o[0], o[1], o[2], 0.19f,
            SceneParts.WORK, 0.95f * soft, GlyphBoard.Style.MATH, 1f, anchor = -0.5f
        )

        if (lock > 0.5f) {
            SceneParts.at(g, -0.55f * U, -0.34f, 0f, o)
            kit.text(
                "R = 1", o[0], o[1], o[2], 0.17f,
                SceneParts.ADDED, 0.95f * soft, GlyphBoard.Style.MATH, 1f
            )
        }

        if (kit.quality == 0) {
            SceneParts.at(g, U, -0.17f, 0f, o)
            kit.text("1", o[0], o[1], o[2], 0.14f, SceneParts.CHALK, 0.8f * seam, GlyphBoard.Style.SMALL)
            SceneParts.at(g, -U, -0.17f, 0f, o)
            kit.text("−1", o[0], o[1], o[2], 0.14f, SceneParts.CHALK, 0.8f * seam, GlyphBoard.Style.SMALL)
        }

        if (rev > 0.4f && kit.quality < 2) {
            ptC(0.22f, 1f, o)
            kit.text(
                "i", o[0], o[1], o[2], 0.17f, SceneParts.TAKEN, rev * soft,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f
            )
            ptC(0.22f, -1f, o)
            kit.text(
                "−i", o[0], o[1], o[2], 0.17f, SceneParts.TAKEN, rev * soft,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f
            )
        }
    }
}
