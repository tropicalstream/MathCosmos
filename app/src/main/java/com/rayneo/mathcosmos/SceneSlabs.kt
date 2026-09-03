package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tour III, stop 2 — THE SLABS. "Cut it into slabs, add them up, and I can see exactly how wrong
 * I am."
 *
 * Eight slabs stand under the roof, four each side of the aisle, and the craft flies between them.
 * Each slab is as tall as the roof above its own LEFT edge, so every one of them falls short of the
 * curve across the rest of its width, and the sliver it fails to reach is filled in red. Then the
 * rule changes to right endpoints: every top slides up to the roof above its RIGHT edge, and the
 * same red wedges reappear on the other side of the curve — now area the sum has claimed and does
 * not have. Under-estimate, over-estimate, and the truth caught between them on a gauge at the
 * mouth of the arcade.
 *
 * Five decisions, three of them compromises, and the crew says all three out loud.
 *
 * FIRST, AND THE ONLY REAL LIE: **the row is a three-quarter-height scale model of the roof.**
 * [SceneKit.traceHeight] runs to about 3.05 at the far end of this span and the passage radius
 * there is about 3.4, so the roof itself is already at nine-tenths of the wall. A full-height slab
 * standing 1.55 out to the side would have its outboard top corner well outside the tube and would
 * simply be buried. So every height in this scene is multiplied by [SCALE] before it is drawn. It
 * is a uniform scaling of one axis: the shape, the staircase, the wedges and every ratio between
 * them survive it exactly, and the sums quoted on the HUD are computed from the true trace, not
 * from the drawing. What does NOT survive is the claim that a slab top touches the roof you can see
 * running down the middle of the corridor — it does not, and there is deliberately no leader drawn
 * between the two, because a leader would assert an identity that is false. Each row plots its own
 * copy of the same function in its own plane, and the slabs touch THAT.
 *
 * SECOND: **the wedge is computed as the region between two heights, never as "above" or "below".**
 * A sub-quad is built from min(top, f) to max(top, f) at each end. That costs nothing, it makes the
 * left-to-right switch a single continuous interpolation of the top level rather than two states
 * with a cut between them, and it means the picture is still correct on a span where the roof is
 * not monotone — which this one is, but the next tour's is not everywhere.
 *
 * Worth saying, because it is the whole of the next stop: for a monotone f the eight wedges tile a
 * single rectangle Δx wide and f(b) − f(a) tall. That is why R − L is exactly Δx·(f(b) − f(a)),
 * why halving Δx halves the error, and why THE THINNING can promise that the red goes to nothing.
 *
 * THIRD: **the bracket gauge is a magnified window and is not to scale with the arcade.** L, the
 * truth and R differ by about three parts in a hundred; a bar drawn at the arcade's own scale would
 * put the whole argument inside two centimetres. So the gauge shows only the interval [L, R],
 * blown up until the answer sitting inside it is a thing you can look at. The numbers themselves
 * are on the HUD in [readout], which is where this app puts every number that has to be read.
 *
 * FOURTH: **two rows, not one.** Rule of thumb in this codebase is that a flat figure goes to one
 * side, because a figure on the rail is one you fly into. An arcade is the exception the rule was
 * never about: the aisle is empty, the diagram is on both walls, and flying through the error is
 * the point of the stop. The design asked for the rows at |s| = 2.4 and 3.0; they are at 1.10 and
 * 1.55 instead, for the same reason the heights are scaled — at 3.0 out, a slab of any useful
 * height is inside the wall. The aisle is still 2.2 across, which is wide at this scale.
 *
 * FIFTH: at quality 1 the scene drops the RIGHT-hand row rather than dropping slabs. Halving a
 * loop of eight is the usual answer, but here the eight is the mathematics — n = 8 is on the HUD
 * and in the crew's script — and the second row is a stereo luxury. Halving the thing that is
 * decoration and keeping the thing that is content is the whole of the quality policy.
 */
object SceneSlabs : MathScene {

    /** Faded up early: the arcade starts two-thirds of a stop before the node and is worth seeing
     *  whole on the approach, which is the only view where all eight slabs are in one frame. */
    override val reach = 1.6f
    override val deep = 0.7f

    // ---- the partition ------------------------------------------------------
    private const val N = 8                 // slabs. The rung says n = 8; it means it.
    private const val SUB = 6               // curve and wedge samples per slab at full quality
    private const val DP = 0.15f            // node units per slab — about 2.4 world units of rail
    private const val SPAN = N * DP
    private const val HALF = SPAN * 0.5f
    /** The picture's own x unit per slab. Chosen so the ladder's Δx = 0.5 is literally true of the
     *  drawing: the span is eight cuts of a half unit each, x running 0 to 4. */
    private const val DX = 0.5f

    // ---- where the arcade hangs --------------------------------------------
    private const val SCALE = 0.72f         // drawn height per unit of trace height. See above.
    private const val BASE = -0.35f         // the diagram's axis, seated just below the rail
    private const val SIDE_IN = 1.10f       // the face you see from the aisle
    private const val SIDE_OUT = 1.55f      // the far face; the slab's thickness is the difference

    // ---- the loop -----------------------------------------------------------
    private const val PERIOD = 24f
    private const val RISE_AT = 0.03f
    private const val RISE_LEN = 0.09f
    private const val RISE_STAG = 0.013f    // slab k starts this much later than slab k-1
    private const val ERR_AT = 0.26f
    private const val ERR_LEN = 0.10f
    private const val SHIFT_AT = 0.50f
    private const val SHIFT_LEN = 0.16f
    // 0.66 to 1.00 is rest: eight seconds of the finished over-estimate to look at, which is what
    // a viewer who arrived halfway through the switch needs.

    // ---- the gauge, on the right-hand side at the mouth ----------------------
    private const val G_AT = -0.06f         // node units before the first slab
    private const val G_LOW = -0.20f
    private const val G_HIGH = 1.25f
    private const val G_OUT = 0.34f         // how far outboard the L and R bars reach
    private const val G_TRUE = 0.46f        // the answer's tick is longer, and starts inboard

    // ---- scratch. Nothing below allocates and nothing survives a frame -------
    private val f = FloatArray(12)
    private val st = FloatArray(12)
    /**
     * One [SceneParts.stage] frame per sample, twelve floats each, laid end to end.
     *
     * Every point in this scene is [SceneParts.at] against one of these, and there are a few
     * hundred of them across two rows; copying twelve floats back out into a working frame per
     * vertex was measurable, and a rail frame costs three spline evaluations to rebuild. So the
     * frames are computed once per sample per frame and indexed in place by [pt], which is
     * [SceneParts.at] written against a slice.
     */
    private val fr = FloatArray((N * SUB + 1) * 12)
    private val hs = FloatArray(N * SUB + 1)     // trace height at each sample, true units

    private val qa = FloatArray(3)
    private val qb = FloatArray(3)
    private val qc = FloatArray(3)
    private val qd = FloatArray(3)
    private val qe = FloatArray(3)
    private val qf = FloatArray(3)
    private val w0 = FloatArray(3)
    private val w1 = FloatArray(3)

    // ---- what the stop is measuring, cached ---------------------------------
    // The trace is a fixed function of the tour, so the three sums never change once the stop is
    // known. Recomputing Simpson's rule thirty times a second to print a constant would be silly.
    private var sumL = 0f
    private var sumR = 0f
    private var truth = 0f
    private var builtAt = -1

    private fun build(kit: SceneKit, i: Int) {
        if (builtAt == i || !kit.hasTrace) return
        val lo = i - HALF
        var acc = 0f
        for (k in 0 until N) acc += kit.traceHeight(lo + DP * k)
        sumL = acc * DX
        sumR = (acc - kit.traceHeight(lo) + kit.traceHeight(lo + SPAN)) * DX
        // The truth by Simpson over 64 intervals. Overkill for a sine and cheap enough once.
        val m = 64
        val d = SPAN / m
        var s = kit.traceHeight(lo) + kit.traceHeight(lo + SPAN)
        for (k in 1 until m) s += kit.traceHeight(lo + d * k) * (if ((k and 1) == 1) 4f else 2f)
        truth = s * d / 3f * (DX / DP)
        builtAt = i
    }

    /** Two decimals without a formatter, which would allocate one per frame. */
    private fun hundredths(v: Float): String {
        val t = (v * 100f + 0.5f).toInt()
        val frac = t % 100
        return "${t / 100}.${if (frac < 10) "0" else ""}$frac"
    }

    /**
     * The running total and how far it is from the answer. This is the stop: the sum is a number
     * you watch move from below the truth to above it, and never land on it.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        // The renderer only asks the nearest stop for its readout, so rounding the rail position
        // gives this scene's own node. Only used if draw() has not run yet this session.
        if (builtAt < 0) build(kit, kit.progress.roundToInt())
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val cur = sumL + (sumR - sumL) * SceneParts.step(c, SHIFT_AT, SHIFT_LEN)
        val e = cur - truth
        return "Δx ${hundredths(DX)}  n $N   SUM ${hundredths(cur)}   ERR ${if (e < 0f) "-" else "+"}${hundredths(abs(e))}"
    }

    /** A point in a row's plane: sample [j] along the rail, [s] out to the side (signed by the
     *  caller), [y] in TRUE trace units — the scaling to drawn height happens here and only here. */
    private fun pt(j: Int, s: Float, y: Float, out: FloatArray) {
        val b = j * 12
        val u = BASE + y * SCALE
        out[0] = fr[b] + fr[b + 3] * s + fr[b + 6] * u
        out[1] = fr[b + 1] + fr[b + 4] * s + fr[b + 7] * u
        out[2] = fr[b + 2] + fr[b + 5] * s + fr[b + 8] * u
    }

    private fun seg(out: FloatArray, at: Int, a: FloatArray, b: FloatArray, col: FloatArray, alpha: Float): Int =
        MathMesh.segment(out, at, a[0], a[1], a[2], b[0], b[1], b[2], col[0], col[1], col[2], alpha)

    /**
     * Two triangles across four arbitrary corners, wound a-b-c / a-c-d.
     *
     * [MathMesh.quad] takes a corner and two spanning vectors and so can only build a
     * parallelogram. Every quad in this scene has its two ends at different heights — that is what
     * a slab under a rising curve IS — so they are written out by hand, the same way the wake's
     * panels are.
     */
    private fun panel(
        out: FloatArray, at: Int,
        a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray,
        col: FloatArray, alpha: Float
    ): Int {
        if ((at + 6) * MathMesh.STRIDE > out.size) return at
        var k = MathMesh.vertex(out, at, a[0], a[1], a[2], col[0], col[1], col[2], alpha)
        k = MathMesh.vertex(out, k, b[0], b[1], b[2], col[0], col[1], col[2], alpha)
        k = MathMesh.vertex(out, k, c[0], c[1], c[2], col[0], col[1], col[2], alpha)
        k = MathMesh.vertex(out, k, a[0], a[1], a[2], col[0], col[1], col[2], alpha)
        k = MathMesh.vertex(out, k, c[0], c[1], c[2], col[0], col[1], col[2], alpha)
        k = MathMesh.vertex(out, k, d[0], d[1], d[2], col[0], col[1], col[2], alpha)
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        if (!kit.hasTrace) return
        build(kit, i)

        val sub = when (kit.quality) { 0 -> SUB; 1 -> 3; else -> 2 }
        val m = N * sub
        val d = SPAN / m
        val lo = i - HALF

        // One rail frame and one roof height per sample, shared by both rows.
        for (j in 0..m) {
            val p = lo + d * j
            SceneParts.stage(kit, p, 0f, 0f, f, st)
            System.arraycopy(st, 0, fr, j * 12, 12)
            hs[j] = kit.traceHeight(p)
        }

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tvv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val showErr = SceneParts.step(c, ERR_AT, ERR_LEN)
        val shift = SceneParts.step(c, SHIFT_AT, SHIFT_LEN)
        // The conveyor under this ambience is one pulse per slab; the rims answer it.
        val pulse = 0.85f + 0.15f * kit.beat
        val rows = if (kit.quality == 0) 2 else 1
        val solid = kit.quality < 2          // the slab's thickness: the top cap and the far edges

        for (row in 0 until rows) {
            val sgn = if (row == 0) -1f else 1f
            val sIn = sgn * SIDE_IN
            val sOut = sgn * SIDE_OUT

            // --- this row's copy of the roof, plotted in its own plane -------------------
            pt(0, sIn, hs[0], w0)
            for (j in 1..m) {
                pt(j, sIn, hs[j], w1)
                v = seg(line, v, w0, w1, SceneParts.HOT, 0.95f)
                w0[0] = w1[0]; w0[1] = w1[1]; w0[2] = w1[2]
            }

            // --- the slabs ----------------------------------------------------------------
            for (k in 0 until N) {
                val j0 = k * sub
                val j1 = j0 + sub
                val grow = SceneParts.ease((c - RISE_AT - k * RISE_STAG) / RISE_LEN)
                if (grow < 0.002f) continue
                // The one interpolation that does the whole left-to-right switch.
                val top = (hs[j0] + (hs[j1] - hs[j0]) * shift) * grow

                pt(j0, sIn, 0f, qa)
                pt(j1, sIn, 0f, qb)
                pt(j1, sIn, top, qc)
                pt(j0, sIn, top, qd)
                tvv = panel(tri, tvv, qa, qb, qc, qd, SceneParts.WORK, 0.20f)
                v = seg(line, v, qa, qb, SceneParts.STEEL, 0.50f)          // the axis, slab by slab
                v = seg(line, v, qb, qc, SceneParts.WORK, 0.90f * pulse)
                v = seg(line, v, qc, qd, SceneParts.WORK, 0.95f * pulse)   // the top: the estimate
                v = seg(line, v, qd, qa, SceneParts.WORK, 0.90f * pulse)

                if (solid) {
                    // Enough of the far side to read as a slab rather than a pane: the top cap,
                    // and the two outboard verticals that carry it down to the floor.
                    pt(j0, sOut, top, qe)
                    pt(j1, sOut, top, qf)
                    v = seg(line, v, qc, qf, SceneParts.WORK, 0.45f)
                    v = seg(line, v, qf, qe, SceneParts.WORK, 0.45f)
                    v = seg(line, v, qe, qd, SceneParts.WORK, 0.45f)
                    pt(j0, sOut, 0f, qa)
                    pt(j1, sOut, 0f, qb)
                    v = seg(line, v, qe, qa, SceneParts.WORK, 0.35f)
                    v = seg(line, v, qf, qb, SceneParts.WORK, 0.35f)
                }

                // --- the red: everything between this slab's top and the curve --------------
                if (showErr > 0.01f) {
                    for (t in 0 until sub) {
                        val ja = j0 + t
                        val fa = hs[ja]
                        val fb = hs[ja + 1]
                        val loA = if (top < fa) top else fa
                        val hiA = if (top < fa) fa else top
                        val loB = if (top < fb) top else fb
                        val hiB = if (top < fb) fb else top
                        pt(ja, sIn, loA, qa)
                        pt(ja + 1, sIn, loB, qb)
                        pt(ja + 1, sIn, hiB, qc)
                        pt(ja, sIn, hiA, qd)
                        tvv = panel(tri, tvv, qa, qb, qc, qd, SceneParts.TAKEN, 0.55f * showErr)
                    }
                }
            }
        }

        // --- the width of one cut, along the floor of the left row ------------------------
        // A bracket rather than a pair of ticks, because a tick on a floor that is itself a line
        // reads as part of the floor. Dropped in the RAIL's up, not the world's: the rail rolls.
        if (kit.quality == 0) {
            val drop = -0.17f                       // trace units, about 0.12 of a world unit
            pt(0, -SIDE_IN, 0f, qa)
            pt(sub, -SIDE_IN, 0f, qb)
            pt(0, -SIDE_IN, drop, qc)
            pt(sub, -SIDE_IN, drop, qd)
            v = seg(line, v, qa, qc, SceneParts.STEEL, 0.55f)
            v = seg(line, v, qc, qd, SceneParts.STEEL, 0.55f)
            v = seg(line, v, qd, qb, SceneParts.STEEL, 0.55f)
        }

        // --- the bracket: two bars with the answer between them ---------------------------
        // Right-hand side, at the mouth of the arcade, so it is readable through the whole
        // approach and out of the way once the craft is in the aisle. Its own frame: it sits
        // ahead of the first sample and there is no cached frame there. Everything hung at the
        // mouth — the gauge, the bead, the claim — is placed against this one, so it is built
        // whatever the quality, before anything decides not to draw.
        SceneParts.stage(kit, lo + G_AT, 0f, 0f, f, st)
        // A strictly POSITIVE spread, not a non-zero one: L below and R above is only the right
        // picture where the roof rises across the span, which is the only reason the two sums
        // bracket anything. On a falling or flat span the gauge would be a lie, so it is not drawn.
        val spread = sumR - sumL
        val gauge = kit.quality < 2 && spread > 1e-4f
        var ft = 0.5f
        if (gauge) {
            ft = ((truth - sumL) / spread).coerceIn(0f, 1f)
            SceneParts.at(st, SIDE_IN, G_LOW, 0f, qa)
            SceneParts.at(st, SIDE_IN, G_HIGH, 0f, qb)
            v = seg(line, v, qa, qb, SceneParts.STEEL, 0.45f)
            // L at the bottom, R at the top: the two sums, in the colour of the slabs they came
            // from, because that is what they are the total of.
            SceneParts.at(st, SIDE_IN + G_OUT, G_LOW, 0f, qc)
            v = seg(line, v, qa, qc, SceneParts.WORK, 0.95f)
            SceneParts.at(st, SIDE_IN + G_OUT, G_HIGH, 0f, qd)
            v = seg(line, v, qb, qd, SceneParts.WORK, 0.95f)
            // And the answer, in the one colour nothing else in this scene uses.
            val uT = G_LOW + (G_HIGH - G_LOW) * ft
            SceneParts.at(st, SIDE_IN - 0.08f, uT, 0f, qe)
            SceneParts.at(st, SIDE_IN + G_TRUE, uT, 0f, qf)
            v = seg(line, v, qe, qf, SceneParts.ADDED, 0.80f + 0.20f * kit.beat)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tvv)

        // --- the running total, riding the gauge ------------------------------------------
        if (gauge && showErr > 0.3f) {
            SceneParts.at(st, SIDE_IN, G_LOW + (G_HIGH - G_LOW) * shift, 0f, qa)
            kit.ball(qa[0], qa[1], qa[2], 0.055f, 0.055f, 0.055f,
                SceneParts.HOT, SceneParts.WORK, 1f, 0f, 0f, 1f, 0f, 0f, 2.2f)
        }

        // --- notation ----------------------------------------------------------------------
        // Beside the arcade, never over it: the telemetry owns the top of the eye and the caption
        // box the bottom, so everything here sits within half a unit of the rail's own height.
        val gl = 0.20f
        SceneParts.at(st, -1.35f, 0.95f, 0f, qa)
        val claim = when {
            c < ERR_AT -> "Σ f(x_k) Δx"
            c < SHIFT_AT + SHIFT_LEN * 0.5f -> "L_8 < ∫f"
            else -> "L_8 < ∫f < R_8"
        }
        kit.text(claim, qa[0], qa[1], qa[2], gl, SceneParts.HOT, 1f, GlyphBoard.Style.MATH, 1.1f)

        if (gauge && kit.quality == 0) {
            SceneParts.at(st, SIDE_IN - 0.16f, G_LOW, 0f, qa)
            kit.text("L_8", qa[0], qa[1], qa[2], gl * 0.8f, SceneParts.WORK, 0.9f,
                GlyphBoard.Style.MATH, 1f, anchor = 0.5f)
            SceneParts.at(st, SIDE_IN - 0.16f, G_HIGH, 0f, qa)
            kit.text("R_8", qa[0], qa[1], qa[2], gl * 0.8f, SceneParts.WORK, 0.9f,
                GlyphBoard.Style.MATH, 1f, anchor = 0.5f)
            SceneParts.at(st, SIDE_IN - 0.22f, G_LOW + (G_HIGH - G_LOW) * ft, 0f, qa)
            kit.text("∫f", qa[0], qa[1], qa[2], gl * 0.85f, SceneParts.ADDED, 1f,
                GlyphBoard.Style.MATH, 1.2f, anchor = 0.5f)
        }

        if (kit.quality == 0) {
            pt(0, -(SIDE_IN + 0.50f), 0f, qa)
            kit.text("Δx", qa[0], qa[1], qa[2], gl * 0.8f, SceneParts.STEEL, 0.85f,
                GlyphBoard.Style.MATH, 0.8f, anchor = 0.5f, rise = -0.6f)
        }
    }
}
