package com.rayneo.mathcosmos

import kotlin.math.pow
import kotlin.math.sin

/**
 * Tour IV, stop 5 — THE TEST. "If my bricks all hide under a tower I already trust, I'm safe."
 *
 * The comparison test, staged as a physical gate. A translucent wedge — the geometric envelope
 * M·r^n, the tower everyone already trusts — is laid over a tower of unknown bricks. Run one:
 * every brick hides under the wedge, the section glows green and the collar ahead closes on the
 * passage. Run two: the bricks are the same for eight terms and then, from term nine, they turn
 * and burst up through the wedge. Those bricks flash red, the collar tries to close, refuses, and
 * the passage flares open again.
 *
 * The ratio test rides underneath as a strip of gauges: one mark per brick, at the height of that
 * brick's ratio to the one before, with a single red line drawn across at 1. The safe run's marks
 * sit under the line — some of them only just, which is the point, because a ratio that wanders up
 * to 0.96 and stays below is still a convergent run. The failing run's marks sit above it.
 *
 * Two decisions worth recording. The second tower's tail GROWS (ratio 1.38 > 1, so its terms do
 * not even go to zero); it would have been easy to draw a tail that merely pokes out of the
 * envelope while still converging, and that picture would be a lie about what has been shown —
 * a failed comparison proves nothing on its own. And the breach is deliberately marginal at term
 * nine and gross by term twelve: the first failure of a test is nearly always a hairline, and a
 * viewer should see how little it takes.
 *
 * The bricks are one pane each in the shared line and triangle buffers, so the whole landmark —
 * twelve bricks, a wedge, a ruled track and a collar round the corridor — costs two draw calls
 * plus a couple of lamps and two labels.
 */
object SceneTest : MathScene {

    override val reach = 1.4f

    // ---- the figure ---------------------------------------------------------------------
    private const val N = 12
    private const val R_GEO = 0.85f          // the trusted envelope's ratio
    private const val BREACH = 9             // the term the second tower turns at
    private const val RATIO_B = 1.38f        // and the ratio it turns to: greater than one
    private const val ENV_S0 = 0.6f
    private const val ENV_S1 = 12.6f

    /**
     * The unknown tower's bricks, as fractions of the envelope. Hand-picked rather than generated:
     * each one must be under the envelope (so the comparison holds) AND its ratio to the one
     * before must stay under 1 (so the ratio gauge agrees with the wedge). A sine would have been
     * shorter to write and would have thrown ratios above 1 on the way up, which reads as the
     * scene contradicting itself.
     */
    private val W = floatArrayOf(
        0.78f, 0.66f, 0.72f, 0.62f, 0.55f, 0.62f, 0.68f, 0.76f, 0.66f, 0.72f, 0.63f, 0.70f
    )

    // A flat figure centred on the rail is a figure you fly INTO. This one hangs to one side,
    // about 1.8 units across, and the craft passes it with a third of a unit of daylight.
    private const val SIDE = -1.25f
    private const val UP = -0.35f
    private const val US = 0.145f            // world units per term along the figure's baseline
    private const val OFF_S = -0.950f        // so the run of terms straddles the stage centre
    private const val HANG = 0.30f           // clearance kept between the crown and the roof

    private const val WEDGE_A = -0.03f       // the wedge sits a hair up-corridor of the bricks,
                                             // so on the approach it is between the eye and them

    private const val TRACK_LO = -0.34f      // the ratio strip, below the baseline
    private const val TRACK_HI = -0.06f
    private const val RATIO_FULL = 1.6f      // ratio at the top of the strip

    private const val PERIOD = 28f
    private const val BUILD = 0.26f          // how much of the cycle a tower takes to stack

    private val CROWN = R_GEO.pow(ENV_S0 - 1f)

    private val f = FloatArray(12)
    private val f2 = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val pq = FloatArray(3)
    private val pd = FloatArray(3)
    private val tv = IntArray(1)

    /** World units per unit of term height, recomputed at the top of every draw from the roof. */
    private var uu = 1.2f

    // ---- the arithmetic -----------------------------------------------------------------

    /** The envelope, sampled continuously so the wedge is a curve and not a staircase. */
    private fun env(s: Float): Float = R_GEO.pow(s - 1f)

    /** Term [m] of the first tower, or of the second — which parts company at [BREACH]. */
    private fun term(m: Int, second: Boolean): Float {
        if (!second || m < BREACH) return env(m.toFloat()) * W[m - 1]
        val last = env((BREACH - 1).toFloat()) * W[BREACH - 2]
        return last * RATIO_B.pow((m - BREACH + 1).toFloat())
    }

    private fun ratio(m: Int, second: Boolean): Float =
        if (m < 2) 0f else term(m, second) / term(m - 1, second)

    /** A point in figure coordinates: [s] terms along, [u] term-heights up, [a] along the rail. */
    private fun pt(s: Float, u: Float, a: Float, out: FloatArray) {
        SceneParts.at(g, OFF_S + s * US, u * uu, a, out)
    }

    // ---- the readout --------------------------------------------------------------------

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val second = c >= 0.47f
        val bs = if (second) 0.50f else 0.04f
        val k = (((c - bs) / BUILD) * N).toInt().coerceIn(0, N)
        return if (!second) {
            if (k < N || c < 0.36f) "n $k/$N   UNDER M r^n"
            else "ALL $N UNDER   CONVERGES"
        } else {
            when {
                k < BREACH -> "n $k/$N   UNDER M r^n"
                k < N || c < 0.80f -> "n $k/$N   RATIO 1.38 > 1"
                else -> "OVER AT n $BREACH   DIVERGES"
            }
        }
    }

    // ---- pieces -------------------------------------------------------------------------

    /** One trapezoid of the wedge. Its top edge is dimmer, so the wedge fades out into its rim. */
    private fun wedgeStrip(
        tri: FloatArray, v: Int, s0: Float, s1: Float,
        cr: Float, cg: Float, cb: Float, alpha: Float
    ): Int {
        if ((v + 6) * MathMesh.STRIDE > tri.size) return v
        pt(s0, 0f, WEDGE_A, pa)
        pt(s1, 0f, WEDGE_A, pb)
        pt(s1, env(s1), WEDGE_A, pq)
        pt(s0, env(s0), WEDGE_A, pd)
        val top = alpha * 0.5f
        var k = MathMesh.vertex(tri, v, pa[0], pa[1], pa[2], cr, cg, cb, alpha)
        k = MathMesh.vertex(tri, k, pb[0], pb[1], pb[2], cr, cg, cb, alpha)
        k = MathMesh.vertex(tri, k, pq[0], pq[1], pq[2], cr, cg, cb, top)
        k = MathMesh.vertex(tri, k, pa[0], pa[1], pa[2], cr, cg, cb, alpha)
        k = MathMesh.vertex(tri, k, pq[0], pq[1], pq[2], cr, cg, cb, top)
        k = MathMesh.vertex(tri, k, pd[0], pd[1], pd[2], cr, cg, cb, top)
        return k
    }

    /** A brick of the tower under test: origin on the baseline, [h] term-heights tall. */
    private fun brick(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        m: Int, h: Float, c: FloatArray, alpha: Float
    ): Int {
        pt(m - 0.4f, 0f, 0f, o)
        SceneParts.vec(g, 0.8f * US, 0f, 0f, du)
        SceneParts.vec(g, 0f, h * uu, 0f, dv)
        return SceneParts.pane(
            kit, line, lv, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val p = i.toFloat()
        SceneParts.stage(kit, p, SIDE, UP, f, g)

        // The figure is scaled so the envelope's crown rides a fixed clearance under the tour's
        // roof curve. The roof is drawn by the ambient scene and it is NOT this series — nothing
        // here is tied to it — but a landmark that grows and shrinks with the corridor it stands
        // in never punches through the ceiling, whatever the trace is doing at this stop.
        val roofU = kit.traceHeight(p) - UP
        uu = ((roofU - HANG) / CROWN).coerceIn(0.85f, 1.30f)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val second = c >= 0.47f
        val bs = if (second) 0.50f else 0.04f
        val safe = if (second) 0f else SceneParts.step(c, 0.33f, 0.06f)
        // The first tower is cleared away before the second is stacked in its place; the swap is
        // a dissolve rather than a cut, so it is obvious that the envelope did not change.
        val hold = if (second) 1f else 1f - SceneParts.step(c, 0.42f, 0.045f)
        val refuse = if (second) SceneParts.step(c, 0.845f, 0.04f) else 0f

        // --- the baseline the terms stand on --------------------------------------------------
        pt(0.3f, 0f, 0f, o)
        pt(13.0f, 0f, 0f, du)
        v = MathMesh.segment(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.55f
        )
        if (kit.quality < 2) {
            for (m in 1..N) {
                pt(m.toFloat(), 0f, 0f, o)
                pt(m.toFloat(), -0.035f, 0f, du)
                v = MathMesh.segment(
                    line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.45f
                )
            }
        }

        // --- the tower under test --------------------------------------------------------------
        // The brick loop is NOT halved at quality 1, though it is a loop of twelve. The whole
        // argument of this stop is which term first pokes out, and at six terms the breach at
        // nine does not exist. What goes instead is the ruling, the gauge stems and the teeth.
        var breached = false
        for (m in 1..N) {
            val t = SceneParts.step(c, bs + BUILD * (m - 1) / N, 0.045f)
            if (t <= 0.002f) continue
            val full = term(m, second)
            val h = full * t
            // A brick counts as poking out the moment any part of it clears the envelope, which
            // is at the envelope's LOWEST point over the brick — its right-hand edge.
            val out = h > env(m + 0.4f)
            if (out) breached = true
            val col = when {
                out -> SceneParts.TAKEN
                safe > 0.5f -> SceneParts.ADDED
                else -> SceneParts.COOL
            }
            val flash = if (out) 0.72f + 0.28f * sin(kit.seconds * 6.5f) else 1f
            v = brick(kit, line, v, tri, m, h, col, 0.92f * hold * flash)

            // The part that is out: drawn again, brighter, from the envelope up to the brick's
            // top. Its bottom is flat where the envelope slopes, so it understates the breach
            // slightly — the honest direction for a picture that is making an accusation.
            if (out) {
                val floorU = env(m + 0.4f)
                pt(m - 0.4f, floorU, -0.004f, o)
                SceneParts.vec(g, 0.8f * US, 0f, 0f, du)
                SceneParts.vec(g, 0f, (h - floorU) * uu, 0f, dv)
                tv[0] = SceneParts.fill(
                    tri, tv[0], o[0], o[1], o[2],
                    du[0], du[1], du[2], dv[0], dv[1], dv[2], SceneParts.TAKEN, 0.42f * flash
                )
            }
        }

        // --- the envelope: the tower we already trust ------------------------------------------
        val segs = if (kit.quality == 0) 26 else if (kit.quality == 1) 13 else 10
        val er: Float; val eg: Float; val eb: Float
        if (safe > 0.5f) { er = SceneParts.ADDED[0]; eg = SceneParts.ADDED[1]; eb = SceneParts.ADDED[2] }
        else { er = SceneParts.STEEL[0]; eg = SceneParts.STEEL[1]; eb = SceneParts.STEEL[2] }
        val eAlpha = 0.16f + 0.10f * safe
        for (k in 0 until segs) {
            val s0 = ENV_S0 + (ENV_S1 - ENV_S0) * k / segs
            val s1 = ENV_S0 + (ENV_S1 - ENV_S0) * (k + 1) / segs
            tv[0] = wedgeStrip(tri, tv[0], s0, s1, er, eg, eb, eAlpha)
            pt(s0, env(s0), WEDGE_A, o)
            pt(s1, env(s1), WEDGE_A, du)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], er, eg, eb, 0.85f)
        }
        // The two ends of the envelope, closed down to the baseline so it reads as a piece of
        // area rather than a stray curve.
        pt(ENV_S0, 0f, WEDGE_A, o); pt(ENV_S0, CROWN, WEDGE_A, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], er, eg, eb, 0.7f)
        pt(ENV_S1, 0f, WEDGE_A, o); pt(ENV_S1, env(ENV_S1), WEDGE_A, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], er, eg, eb, 0.7f)

        // --- the ratio gauges, and the red line at one -----------------------------------------
        if (kit.quality < 2) {
            // The strip lives in world offsets below the baseline, not in term-heights, so it
            // keeps its size when the figure is rescaled to fit under the roof.
            val one = TRACK_LO + (TRACK_HI - TRACK_LO) / RATIO_FULL
            SceneParts.at(g, OFF_S + 0.6f * US, one, 0f, o)
            SceneParts.at(g, OFF_S + 13.0f * US, one, 0f, du)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], 0.75f
            )
            for (m in 2..N) {
                val t = SceneParts.step(c, bs + BUILD * (m - 1) / N, 0.045f)
                if (t < 0.5f) continue
                val q = ratio(m, second)
                val y = TRACK_LO + (TRACK_HI - TRACK_LO) * (q / RATIO_FULL).coerceIn(0f, 1f)
                if (kit.quality == 0) {
                    SceneParts.at(g, OFF_S + m * US, TRACK_LO, 0f, o)
                    SceneParts.at(g, OFF_S + m * US, TRACK_HI, 0f, du)
                    v = MathMesh.segment(
                        line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                        SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.22f
                    )
                }
                val mc = if (q >= 1f) SceneParts.TAKEN else SceneParts.ADDED
                SceneParts.at(g, OFF_S + (m - 0.34f) * US, y, 0f, o)
                SceneParts.at(g, OFF_S + (m + 0.34f) * US, y, 0f, du)
                v = MathMesh.segment(
                    line, v, o[0], o[1], o[2], du[0], du[1], du[2], mc[0], mc[1], mc[2], 0.95f * hold
                )
            }
        }

        // --- the collar: the judgement, as a gate on the passage --------------------------------
        // A little ahead of the stop, so the verdict is a thing the craft then flies through.
        val cp = p + 0.30f
        kit.frame(cp, f2)
        val rad = kit.radius(cp)
        // The cycle ends flared, so it must begin flared too: a collar that snapped back to its
        // resting width on the loop join would be the one moving part in the tour that visibly
        // cheats. It eases in over the first six per cent instead.
        var cf = 0.80f - 0.06f * SceneParts.step(c, 0f, 0.06f)
        if (!second) {
            cf -= 0.30f * safe                                   // it closes on a passing series
            cf += 0.30f * SceneParts.step(c, 0.42f, 0.045f)      // and opens again for the retest
        } else {
            cf -= 0.24f * SceneParts.step(c, 0.79f, 0.045f)      // it tries...
            cf += 0.32f * refuse + 0.025f * refuse * sin(kit.seconds * 11f)   // ...and refuses
        }
        cf = cf.coerceIn(0.40f, 0.80f)
        val cRad = rad * cf
        val ccol = if (refuse > 0.3f) SceneParts.TAKEN else if (safe > 0.4f) SceneParts.ADDED else SceneParts.STEEL
        val cAlpha = 0.45f + 0.45f * kotlin.math.max(safe, refuse)
        val ring = if (kit.quality == 0) 36 else 18
        v = MathMesh.arc(
            line, v, f2[0], f2[1], f2[2], f2[6], f2[7], f2[8], f2[9], f2[10], f2[11],
            cRad, 0f, 6.2831855f, ring, ccol[0], ccol[1], ccol[2], cAlpha
        )
        if (kit.quality == 0) {
            // Teeth pointing in, so a collar that is closing looks like a mechanism and not a hoop.
            for (k in 0 until 8) {
                val ang = k * 0.7853982f
                val cs = kotlin.math.cos(ang); val sn = sin(ang)
                val tooth = 0.13f * rad
                v = MathMesh.segment(
                    line, v,
                    f2[0] + (f2[6] * cs + f2[9] * sn) * cRad,
                    f2[1] + (f2[7] * cs + f2[10] * sn) * cRad,
                    f2[2] + (f2[8] * cs + f2[11] * sn) * cRad,
                    f2[0] + (f2[6] * cs + f2[9] * sn) * (cRad - tooth),
                    f2[1] + (f2[7] * cs + f2[10] * sn) * (cRad - tooth),
                    f2[2] + (f2[8] * cs + f2[11] * sn) * (cRad - tooth),
                    ccol[0], ccol[1], ccol[2], cAlpha * 0.8f
                )
            }
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- lamps ------------------------------------------------------------------------------
        // Where the argument breaks: the point on the envelope that term nine went through.
        if (breached && second && kit.quality < 2) {
            pt(BREACH.toFloat(), env(BREACH.toFloat()), 0f, o)
            val pulse = 0.6f + 0.4f * sin(kit.seconds * 6.5f)
            kit.ball(
                o[0], o[1], o[2], 0.055f, 0.055f, 0.055f,
                SceneParts.TAKEN, SceneParts.HOT, pulse, 0f, 0f, 1f, 0f, 0f, 2.4f * pulse
            )
        }
        // And the latch, when the collar does close.
        val latch = safe * (1f - SceneParts.step(c, 0.44f, 0.04f))
        if (latch > 0.02f) {
            kit.ball(
                f2[0] + f2[9] * cRad, f2[1] + f2[10] * cRad, f2[2] + f2[11] * cRad,
                0.07f, 0.07f, 0.07f, SceneParts.ADDED, SceneParts.HOT, latch,
                0f, 0f, 1f, 0f, 0f, 2.6f * latch
            )
        }

        // --- notation ----------------------------------------------------------------------------
        // Both labels sit in the pocket of empty air the decaying envelope leaves, level with the
        // geometry they name rather than stacked over or under it, where the telemetry and the
        // caption box already are.
        if (kit.quality < 2) {
            pt(4.6f, CROWN * 0.90f, 0f, o)
            kit.text("M r^n", o[0], o[1], o[2], 0.19f, if (safe > 0.5f) SceneParts.ADDED else SceneParts.STEEL,
                0.95f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        }
        if (kit.quality == 0) {
            val one = TRACK_LO + (TRACK_HI - TRACK_LO) / RATIO_FULL
            SceneParts.at(g, OFF_S + 0.35f * US, one, 0f, o)
            kit.text("> 1", o[0], o[1], o[2], 0.15f, SceneParts.TAKEN, 0.9f,
                GlyphBoard.Style.SMALL, 1f, anchor = 0.5f)
        }
    }
}
