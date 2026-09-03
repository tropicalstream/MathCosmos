package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 7 — THE FIELD OF SLOPES. "The steepness is itself a curve, and I can read it off the floor."
 *
 * The previous three stops taught the craft to measure one slope at one place. This one does it
 * everywhere at once and keeps the answers, and the kept answers turn out to be another curve. A
 * second ribbon hangs below the rail — f′ — and the craft flies down the corridor between the two
 * of them. That sandwich is the whole stop: one function above, its steepness below, the same x
 * running along both.
 *
 * A writing head sweeps the leg once per loop. Ahead of it the floor is bare; behind it the
 * derivative ribbon exists, shaded green where the roof is climbing and red where it is falling,
 * with a needle left standing on the roof at every half-node and a dashed plumb line from each
 * needle down to the height it produced. Then the head parks, the finished pair sits there for six
 * seconds, and the ribbon rolls away from the near end so the loop closes without a cut.
 *
 * Three decisions worth stating.
 *
 *  - **The needles are drawn on square paper, and that is a convention, not a projection.** In this
 *    tour one unit of x is one whole node, sixteen world units, while one unit of f is one world
 *    unit of roof height: the corridor is the graph of f stretched sixteen times sideways. A needle
 *    laid honestly along the drawn roof would sit at three degrees off horizontal at the steepest
 *    place on the leg and would read as flat everywhere. So each needle is a short fixed-length
 *    strut whose ANGLE is atan(f′) — the tilt the tangent has on paper with equal scales, exactly
 *    the gauge convention THE CHORD introduced two stops back. The crew says so out loud there and
 *    the code agrees here.
 *
 *  - **The floor ribbon shares the roof's vertical scale exactly** — one world unit per unit of
 *    f′, the same as the roof's one world unit per unit of f — so the two ribbons' heights can be
 *    compared by eye without anyone being told a scale factor. It hangs at 1.30 below the rail
 *    rather than on the actual floor because it has to swing a full unit either way and the wall is
 *    only about 2.7 down; calling it "the floor ribbon" is a small lie of convenience about where
 *    it sits, never about what it says.
 *
 *  - **The slope is measured, not looked up.** Every value on the ribbon is a central difference of
 *    kit.traceHeight over h = 0.04 nodes — the same difference quotient the last three stops built,
 *    just taken small and taken everywhere. The HUD rung here says 10⁻⁴, and at that h a float
 *    subtraction of two heights near two is mostly rounding noise, so 0.04 is what the ribbon
 *    actually uses. It is an approximation of the limit and it is worth knowing it is one.
 *
 * The zero crossings are found by scanning the sampled ribbon for a sign change rather than being
 * written in, because the roof function belongs to the tour and not to this scene: change the trace
 * and the rings move with it. On the leg as it currently stands there is exactly one crossing in
 * view, a minimum of f just past the stop, where the roof levels out and the floor ribbon passes
 * through nothing.
 */
object SceneSlopeRibbon : MathScene {

    // A curve that runs most of a leg, so it must fade in early and must not be culled at its own
    // stop while a third of it is still ahead of the craft.
    override val reach = 2.2f
    override val deep = 2.8f

    // ---- the stretch of corridor this stop owns, in node units --------------
    private const val BACK = 1.2f           // behind the stop
    private const val FWD = 2.6f            // and ahead
    private const val NODES = 6             // rail frames cached: floor(p0) .. +5

    // ---- the two ribbons ----------------------------------------------------
    private const val FLOOR_U = -1.30f      // where f′ = 0 hangs, below the rail
    private const val DSCALE = 1.0f         // world units per unit of f′ — the roof's own scale
    private const val DH = 0.04f            // the h of the central difference, in node units
    private const val NEEDLE = 0.50f        // needle half-length, world units
    private const val PERIOD = 26f

    // The ambient trace's squeeze, repeated. See [roof].
    private const val CEIL = 0.80f
    private const val SOFT = 0.28f

    // ---- scratch. Nothing below allocates, and nothing survives a frame -----
    private val frames = FloatArray(NODES * 12)
    private val fTmp = FloatArray(12)
    private val fr = FloatArray(12)         // the rail frame interpolated at some p
    private val zA = FloatArray(3)          // previous sample, on the zero line
    private val cA = FloatArray(3)          // previous sample, on the ribbon
    private val zB = FloatArray(3)
    private val cB = FloatArray(3)
    private val pA = FloatArray(3)
    private val pB = FloatArray(3)
    private val o = FloatArray(3)
    private val crossP = FloatArray(4)      // zero crossings found in the window
    private var crossN = 0
    private var base = 0                    // the node the frame table starts at

    // ---------------------------------------------------------------- the clock

    /** Where the writing head is, as a fraction of the window. Shared by draw and readout. */
    private fun sweepAt(c: Float): Float = SceneParts.step(c, 0.05f, 0.58f)

    /** Where the near end has rolled away to. Zero for most of the loop. */
    private fun wipeAt(c: Float): Float = SceneParts.step(c, 0.86f, 0.12f)

    // ------------------------------------------------------------ the function

    /**
     * The roof height at [p] as the ambient ribbon actually DRAWS it.
     *
     * SceneAmbientTrace presses the trace against the wall where the passage is tighter than the
     * curve, and that squeeze is private to it. The needles have to stand on the ribbon a viewer
     * can see, so the squeeze is copied here exactly as THE CHORD copies it. On this leg the roof
     * never comes within a third of a unit of the knee, so nothing is actually pressed and the
     * question is invisible — but it would not be if the trace function were ever retuned.
     */
    private fun roof(kit: SceneKit, p: Float): Float {
        val h = kit.traceHeight(p)
        val lid = CEIL * kit.radius(p)
        val m = abs(h)
        val knee = lid - SOFT
        if (m <= knee) return h
        val over = m - knee
        val pressed = if (over >= 2f * SOFT) lid else m - over * over / (4f * SOFT)
        return if (h < 0f) -pressed else pressed
    }

    /**
     * f′ at [p], in units of roof height per node.
     *
     * Taken from the honest trace rather than from [roof]: the derivative on the floor is the
     * derivative of the function, not of the compromise we drew to keep it out of the wall. Where
     * the two differ the needle would be a hair off the ribbon it belongs to, which is the right
     * way round — better a needle that lies slightly about the picture than a ribbon that lies
     * about the mathematics.
     */
    private fun slope(kit: SceneKit, p: Float): Float =
        (kit.traceHeight(p + DH) - kit.traceHeight(p - DH)) / (2f * DH)

    /** Rising green, falling red. The sign is the one thing the fill is there to carry. */
    private fun tintFor(s: Float): FloatArray = if (s >= 0f) SceneParts.ADDED else SceneParts.TAKEN

    // ---------------------------------------------------------------- the rail

    /**
     * The rail frame at [p], blended from the cached whole-node frames into [fr].
     *
     * A stage frame will not do for this stop, and the reason is worth writing down: the rail zigs
     * about two and a half units sideways per leg, so a figure built from one frame is most of a
     * metre outside the corridor by the far end of a four-node stretch. Frames are cached once per
     * whole node at the top of draw — six queries — and linearly blended between, which is what
     * SceneAmbientTrace does with the same rail and is exact to well under a pixel.
     */
    private fun frameAt(p: Float) {
        val t = (p - base).coerceIn(0f, (NODES - 1).toFloat())
        var k = t.toInt()
        if (k > NODES - 2) k = NODES - 2
        val u = t - k
        val a = k * 12
        val b = a + 12
        for (j in 0 until 12) fr[j] = frames[a + j] + (frames[b + j] - frames[a + j]) * u
    }

    /** A point [side] across the current frame and [up] above its rail centre. */
    private fun pt(side: Float, up: Float, out: FloatArray) {
        out[0] = fr[0] + fr[6] * side + fr[9] * up
        out[1] = fr[1] + fr[7] * side + fr[10] * up
        out[2] = fr[2] + fr[8] * side + fr[11] * up
    }

    // --------------------------------------------------------------- the pieces

    /**
     * One interval of the shaded band between the zero line and the ribbon, as a trapezium.
     * Not [MathMesh.quad], which spans a parallelogram: the band's two ends are different heights,
     * and that difference is the picture.
     */
    private fun band(
        tri: FloatArray, v: Int,
        z0: FloatArray, c0: FloatArray, t0: FloatArray, a0: Float,
        z1: FloatArray, c1: FloatArray, t1: FloatArray, a1: Float
    ): Int {
        if ((v + 6) * MathMesh.STRIDE > tri.size) return v
        var k = MathMesh.vertex(tri, v, z0[0], z0[1], z0[2], t0[0], t0[1], t0[2], a0)
        k = MathMesh.vertex(tri, k, z1[0], z1[1], z1[2], t1[0], t1[1], t1[2], a1)
        k = MathMesh.vertex(tri, k, c1[0], c1[1], c1[2], t1[0], t1[1], t1[2], a1)
        k = MathMesh.vertex(tri, k, z0[0], z0[1], z0[2], t0[0], t0[1], t0[2], a0)
        k = MathMesh.vertex(tri, k, c1[0], c1[1], c1[2], t1[0], t1[1], t1[2], a1)
        k = MathMesh.vertex(tri, k, c0[0], c0[1], c0[2], t0[0], t0[1], t0[2], a0)
        return k
    }

    /**
     * A station: the needle standing on the roof at [p], and the plumb line from it down to the
     * height it produced on the floor ribbon. This one call is the whole argument of the stop —
     * the tilt up there and the height down here are the same number twice.
     */
    private fun station(
        kit: SceneKit, line: FloatArray, v: Int, p: Float, alpha: Float, hot: Boolean
    ): Int {
        frameAt(p)
        val s = slope(kit, p)
        val h = roof(kit, p)
        pt(0f, h, pA)
        pt(0f, FLOOR_U + s * DSCALE, pB)

        // The needle: fixed length, tilted by atan(f′) in the plane of forward and up. See the
        // square-paper note in the object comment — this angle is a reading, not a projection.
        val inv = NEEDLE / sqrt(1f + s * s)
        val dx = (fr[3] + fr[9] * s) * inv
        val dy = (fr[4] + fr[10] * s) * inv
        val dz = (fr[5] + fr[11] * s) * inv
        val nc = if (hot) SceneParts.HOT else SceneParts.WORK
        var k = MathMesh.segment(
            line, v, pA[0] - dx, pA[1] - dy, pA[2] - dz, pA[0] + dx, pA[1] + dy, pA[2] + dz,
            nc[0], nc[1], nc[2], alpha
        )

        // The plumb line, in the colour of the sign it is carrying. Dashed, because it is a
        // construction line: nothing is physically hanging there.
        val t = tintFor(s)
        k = MathMesh.dashed(
            line, k, pA[0], pA[1], pA[2], pB[0], pB[1], pB[2], 7,
            t[0], t[1], t[2], alpha * 0.42f
        )

        // And the tick where it lands, across the corridor, so the landing is a place and not just
        // the end of a dotted line.
        k = MathMesh.segment(
            line, k, pB[0] - fr[6] * 0.11f, pB[1] - fr[7] * 0.11f, pB[2] - fr[8] * 0.11f,
            pB[0] + fr[6] * 0.11f, pB[1] + fr[7] * 0.11f, pB[2] + fr[8] * 0.11f,
            t[0], t[1], t[2], alpha
        )
        return k
    }

    // --------------------------------------------------------------- the readout

    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        // The renderer asks the FLOOR stop of the rail, so whenever this line is on screen the
        // craft is between this stop and the next and the floor is our own index.
        val at = floor(kit.progress)
        // Clamped exactly as draw clamps the window, so the number on the HUD is the slope under
        // the writing head and not a slope from a window an end of the rail has shortened.
        val p0 = (at - BACK).coerceAtLeast(0f)
        val p1 = (at + FWD).coerceAtMost(kit.stopCount - 1f)
        val p = p0 + (p1 - p0) * sweepAt(SceneParts.cycle(kit.seconds, PERIOD))
        val s = slope(kit, p)
        val word = if (s > 0.05f) "RISING" else if (s < -0.05f) "FALLING" else "LEVEL"
        return String.format(Locale.US, "f′ %+.2f   ROOF %s", s, word)
    }

    // ------------------------------------------------------------------ the draw

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Tours I, V and VI have no roof curve. The derivative of a function that is not there is a
        // bright straight line along the floor, which is worse than drawing nothing.
        if (!kit.hasTrace) return

        val q = kit.quality
        val at = i.toFloat()
        val p0 = (at - BACK).coerceAtLeast(0f)
        val p1 = (at + FWD).coerceAtMost(kit.stopCount - 1f)
        if (p1 - p0 < 0.8f) return

        // Six frame queries for four nodes of corridor, cached and blended. Every frame query on
        // this renderer builds a small object, so the count matters more than it looks.
        base = floor(p0).toInt().coerceIn(0, (kit.stopCount - NODES).coerceAtLeast(0))
        for (k in 0 until NODES) {
            kit.frame((base + k).coerceAtMost(kit.stopCount - 1).toFloat(), fTmp)
            System.arraycopy(fTmp, 0, frames, k * 12, 12)
        }

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val pen = p0 + (p1 - p0) * sweepAt(c)
        val tail = p0 + (p1 - p0) * wipeAt(c)

        // --- the zero line ------------------------------------------------------------------
        // Drawn across the whole window whether or not the ribbon has reached it, because it is
        // the axis the ribbon is measured against and an axis that arrives with the measurement is
        // no axis at all. Sampled along the rail rather than struck straight: the rail bends.
        val zN = if (q == 0) 22 else 12
        frameAt(p0)
        pt(0f, FLOOR_U, zA)
        for (k in 1..zN) {
            val p = p0 + (p1 - p0) * k / zN
            frameAt(p)
            pt(0f, FLOOR_U, zB)
            v = MathMesh.segment(
                line, v, zA[0], zA[1], zA[2], zB[0], zB[1], zB[2],
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.34f
            )
            zA[0] = zB[0]; zA[1] = zB[1]; zA[2] = zB[2]
        }

        // --- the ribbon, and the crossings found while sampling it ---------------------------
        // The sample grid is fixed to the window, not to the writing head, so the crossings land in
        // the same place every loop and the rings do not jitter as the head passes them.
        val stepP = if (q == 0) 0.09f else if (q == 1) 0.15f else 0.22f
        val nS = ((p1 - p0) / stepP).toInt().coerceIn(10, 60)
        crossN = 0
        var prevS = slope(kit, p0)
        var prevP = p0
        frameAt(p0)
        pt(0f, FLOOR_U, zA)
        pt(0f, FLOOR_U + prevS * DSCALE, cA)
        var prevA = 0f

        for (k in 1..nS) {
            val p = p0 + (p1 - p0) * k / nS
            val s = slope(kit, p)
            if (prevS * s < 0f && crossN < crossP.size) {
                crossP[crossN++] = prevP + (p - prevP) * (prevS / (prevS - s))
            }
            frameAt(p)
            pt(0f, FLOOR_U, zB)
            pt(0f, FLOOR_U + s * DSCALE, cB)

            // Visible only between the rolling tail and the head. A soft edge at the tail so the
            // wipe reads as the record rolling away rather than as a scissor cut.
            val a = if (p > pen || p < tail) 0f else ((p - tail) / 0.30f).coerceIn(0f, 1f)
            if (a > 0.01f || prevA > 0.01f) {
                val t0 = tintFor(prevS)
                val t1 = tintFor(s)
                v = MathMesh.segment(
                    line, v, cA[0], cA[1], cA[2], cB[0], cB[1], cB[2],
                    t1[0], t1[1], t1[2], prevA, a
                )
                // The shaded band. It is what makes the sign readable at a glance: above the line
                // green, below it red, and the crossing is where the colour changes hands.
                if (q < 2) tv = band(tri, tv, zA, cA, t0, prevA * 0.26f, zB, cB, t1, a * 0.26f)
            }
            zA[0] = zB[0]; zA[1] = zB[1]; zA[2] = zB[2]
            cA[0] = cB[0]; cA[1] = cB[1]; cA[2] = cB[2]
            prevS = s; prevP = p; prevA = a
        }

        // --- the standing needles -------------------------------------------------------------
        // Half a node apart at full detail, a whole node when the governor bites, and none at all
        // at quality 2 — the writing head keeps its own needle, so the idea survives the cut.
        if (q < 2) {
            val sStep = if (q == 0) 0.5f else 1f
            var sp = ceil(p0 / sStep) * sStep
            while (sp <= p1) {
                // Written as two comparisons rather than `sp in tail..pen`: a Float range builds a
                // ClosedFloatingPointRange object, and this is inside draw().
                if (sp >= tail && sp <= pen) v = station(kit, line, v, sp, 0.72f, false)
                sp += sStep
            }
        }

        // --- the crossings ----------------------------------------------------------------------
        // A ring in the corridor's cross-section, so it faces the craft on the approach and reads
        // as a place on the rail rather than a mark on a wall.
        if (q < 2) {
            for (k in 0 until crossN) {
                val zx = crossP[k]
                if (zx > pen || zx < tail) continue
                frameAt(zx)
                pt(0f, FLOOR_U, o)
                v = MathMesh.arc(
                    line, v, o[0], o[1], o[2], fr[6], fr[7], fr[8], fr[9], fr[10], fr[11],
                    0.17f, 0f, 6.2831855f, 14,
                    SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.95f
                )
                // Its needle up on the roof, in the bright colour, lying flat. That flatness is the
                // claim: nothing on the floor means nothing tilting overhead.
                v = station(kit, line, v, zx, 1f, true)
            }
        }

        // --- the writing head --------------------------------------------------------------------
        v = station(kit, line, v, pen, 1f, true)

        kit.flushLines(v, 2.2f)
        if (tv > 0) kit.flushTris(tv)

        // --- the two beads at the head ------------------------------------------------------------
        // The contact on the roof and the height it is dropping onto the floor. Two draw calls, and
        // they are the only lit geometry in the stop: everything else earns its place in a buffer.
        val headA = 1f - wipeAt(c)
        frameAt(pen)
        val ps = slope(kit, pen)
        pt(0f, roof(kit, pen), pA)
        pt(0f, FLOOR_U + ps * DSCALE, pB)
        kit.ball(
            pA[0], pA[1], pA[2], 0.075f, 0.075f, 0.075f, SceneParts.HOT, SceneParts.WORK,
            headA, 0f, 0f, 1f, 0f, 0f, 1.6f
        )
        val ht = tintFor(ps)
        kit.ball(
            pB[0], pB[1], pB[2], 0.09f, 0.09f, 0.09f, ht, SceneParts.CHALK,
            headA, 0f, 0f, 1f, 0f, 0f, 2.2f
        )

        // A slow pulse on the first crossing in view, so the eye is taken to it during the rest.
        if (q < 2 && crossN > 0 && crossP[0] <= pen && crossP[0] >= tail) {
            frameAt(crossP[0])
            pt(0f, FLOOR_U, o)
            val pulse = 0.55f + 0.45f * sin(kit.seconds * 2.1f)
            kit.ball(
                o[0], o[1], o[2], 0.07f, 0.07f, 0.07f, SceneParts.HOT, SceneParts.CHALK,
                0.85f, 0f, 0f, 1f, 0f, 0f, 1.4f + 1.8f * pulse
            )
        }

        // --- notation ------------------------------------------------------------------------------
        // Both names sit beside their own ribbon, three quarters of a node ahead of the stop, where
        // the craft passes them close enough to read and where neither is anywhere near the top of
        // the eye or the caption box. Naming both is the point: they are two graphs of one thing.
        val lp = at + 0.75f
        if (lp <= p1) {
            frameAt(lp)
            pt(-1.10f, roof(kit, lp), o)
            kit.text("f(x)", o[0], o[1], o[2], 0.24f, SceneParts.HOT, 1f)
            pt(-1.10f, FLOOR_U + 0.30f, o)
            kit.text("f′(x)", o[0], o[1], o[2], 0.24f, SceneParts.COOL, 1f)
        }

        if (q == 0) {
            // The zero line named once, small, out at the side where it cannot be mistaken for a
            // value on the ribbon.
            val zp = at + 1.35f
            if (zp <= p1) {
                frameAt(zp)
                pt(-0.46f, FLOOR_U, o)
                kit.text("0", o[0], o[1], o[2], 0.15f, SceneParts.COOL, 0.8f, GlyphBoard.Style.SMALL)
            }
            // And the crossing said out loud, once it has been drawn.
            if (crossN > 0 && crossP[0] <= pen && crossP[0] >= tail) {
                frameAt(crossP[0])
                pt(-0.85f, FLOOR_U + 0.36f, o)
                kit.text("f′ = 0", o[0], o[1], o[2], 0.20f, SceneParts.HOT, 1f)
            }
        }
    }
}
