package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.round

/**
 * Tour II, stop 12 — THE HUNT. "Slide down the tangent, guess again, and the guesses collapse
 * onto the answer faster than you'd believe."
 *
 * Newton's method, and the only reason it is worth a stop rather than a formula: the error does
 * not shrink, it collapses. Four struts, and the guess is right to a billionth. The picture has to
 * make that plunge visible, and the honest problem with drawing it is that after the second step
 * there is nothing left to draw — the third strut is shorter than a pixel and the fourth is shorter
 * than an atom. So the geometry carries the first two steps, which are the ones you can see doing
 * the work, and the HUD readout carries the ladder, which is where a number that must be READ
 * belongs. Both are driven off the same clock and the same iterates, so they cannot disagree.
 *
 * Four decisions are worth stating plainly, because three of them are compromises.
 *
 * FIRST, the equation. The design asks for a TRACE that crosses the rail ahead of the craft, and
 * this tour's roof does no such thing: it is a corridor ceiling and it stays over your head for
 * thirty-three minutes. Hunting for f(x) = 0 would mean drawing a curve that is not the tour's
 * curve. So the hunt is for f(x) = c instead — the x at which the roof reaches a given HEIGHT —
 * which is the same equation with the axis moved, and is what the window plots: f − c, whose zero
 * is a real place in the corridor. The target height is read off the roof at a known offset, so
 * the answer is exact by construction and the readout can show a true error rather than a residual.
 *
 * SECOND, the magnified window, inherited wholesale from THE CLOSING JAW and for the same reasons.
 * One node unit of rail is sixteen world units of corridor, so a tangent drawn honestly on the
 * ribbon overhead is a strut receding down the passage and seen end-on. The window plots f in its
 * own units — one node unit across is one unit of height tall — and stands square to the rail off
 * to one side, where the staircase is broadside for the whole approach. A dashed leader runs from
 * the window's zero out to the actual crossing on the ribbon, and a dashed level line lies along
 * the corridor at height c through it, so the answer is a place and not only a point on a graph.
 *
 * THIRD, and this is the one the crew should say out loud: because the window's x axis runs ACROSS
 * the corridor, the failure case's tangent flies off the edge of the PICTURE rather than off down
 * the passage. It is capped and faded rather than followed, and the readout names how far the
 * thrown guess actually lands. Near a flat spot the slope is nearly nothing, and Newton divides by
 * it; that is the whole failure, and it is a division, not a mood.
 *
 * FOURTH, what is NOT here: the ship does not jump forward once per iteration. Moving the craft is
 * the renderer's to drive and a scene may only draw. What this file owns is the staircase.
 *
 * The iterates are computed live from [SceneKit.traceHeight] by central difference — measured, not
 * hand-fitted — so if the roof curve is ever retuned the picture retunes with it. The offsets below
 * were chosen against this tour's roof at this node: start a node and a half past the answer, and
 * the errors fall 9·10⁻¹, 2·10⁻¹, 2·10⁻², 8·10⁻⁵, 1·10⁻⁹. That is the stop.
 */
object SceneHunt : MathScene {

    // A late flagship, faded up early so the collapse is watchable rather than glimpsed. `deep`
    // covers the leader, the level line and the ribbon bead, which lie better than a node ahead of
    // the stop and would otherwise be culled with it.
    override val reach = 1.5f
    override val deep = 1.4f

    // ---- the equation, in node units off this stop -----------------------------------------
    private const val ROOT_OFF = 0.60f     // where the roof reaches c: the answer, by construction
    private const val X0_OFF = 1.50f       // the first guess, deliberately a long way out
    private const val FAIL_OFF = 2.20f     // the second start, just past the roof's turning point
    private const val LEVEL_RUN = 0.70f    // how far the level line runs either side of the answer
    private const val N = 4                // Newton steps drawn

    // ---- the window it is all plotted in ----------------------------------------------------
    private const val LO = -0.60f          // window span, node units either side of the answer
    private const val HI = 1.75f
    private const val K = 0.85f            // world units per node unit AND per unit of height
    private const val SIDE_C = -1.52f      // the answer's place in the stage frame
    private const val UP_C = 0.30f
    private const val FY_TOP = 0.66f       // the window's own bounds, in figure world units
    private const val FY_BOT = -0.32f
    private const val FLING = 0.75f        // how far past the rim the failing tangent is followed

    private const val EPS = 0.002f         // central difference half-step, node units
    private const val MIN_SLOPE = 0.02f    // below this the step is clamped rather than infinite

    // ---- the clock, in fractions of one cycle ------------------------------------------------
    private const val PERIOD = 26f
    private const val STEP_AT = 0.06f      // the first strut leaves the first bead
    private const val STEP_GAP = 0.105f    // one iteration to the next
    private const val STEP_LEN = 0.09f     // how long one iteration takes to draw
    private const val FAIL_AT = 0.62f      // after four seconds of rest on the answer
    private const val FAIL_LEN = 0.10f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p = FloatArray(3)
    private val q = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)
    // Scratch: both are filled at the top of draw and read nowhere else, so this object still
    // carries nothing at all between frames. readout() deliberately keeps off them — it is called
    // from the telemetry string, not from the draw, and the two must not share a buffer.
    private val xs = FloatArray(N + 1)     // the iterates, absolute node units
    private val rev = FloatArray(N)        // how far each iteration has been drawn, 0..1

    // ---- the method itself --------------------------------------------------------------------

    /** f′ at [x], by central difference on the roof the tour is actually about. */
    private fun slope(kit: SceneKit, x: Float): Float =
        (kit.traceHeight(x + EPS) - kit.traceHeight(x - EPS)) / (2f * EPS)

    /**
     * One Newton step from [x] toward f = [level].
     *
     * The slope is clamped away from zero. Not to hide the failure — the failure is drawn, further
     * down, with the true slope and a ray that leaves the frame — but so that a scene dropped at a
     * node whose roof happens to be flat degrades into a short strut rather than into a coordinate
     * of ten thousand and a passage full of stray lines.
     */
    private fun newton(kit: SceneKit, x: Float, level: Float): Float {
        val d = slope(kit, x)
        val s = if (abs(d) < MIN_SLOPE) (if (d < 0f) -MIN_SLOPE else MIN_SLOPE) else d
        return x - (kit.traceHeight(x) - level) / s
    }

    /** How many iterations have finished by [c]. The picture and the HUD both count from here. */
    private fun done(c: Float): Int {
        var k = 0
        for (j in 0 until N) if (c >= STEP_AT + j * STEP_GAP + STEP_LEN) k = j + 1
        return k
    }

    /** One significant figure and an exponent, without a formatter: "2.4e-1". */
    private fun sci(v: Float): String {
        if (!(v > 0f) || v > 1e30f) return "0"
        var m = v
        var e = 0
        while (m >= 10f) { m *= 0.1f; e++ }
        while (m < 1f) { m *= 10f; e-- }
        var d = round(m * 10f) / 10f
        if (d >= 10f) { d = 1f; e++ }
        return "${d}e$e"
    }

    /**
     * The ladder, which is the drama. The HUD asks the scene at floor(progress), so the anchor is
     * rebuilt from progress here rather than from the node index draw() is handed — inside the leg
     * this stop owns, the two agree exactly.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        val a0 = kit.progress.toInt().toFloat()
        val root = a0 + ROOT_OFF
        val level = kit.traceHeight(root)
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        if (c >= FAIL_AT) {
            // The honest failure. The thrown guess is quoted with the TRUE slope, unclamped, since
            // the whole point is how far a division by nearly nothing sends you.
            val xf = a0 + FAIL_OFF
            val d = slope(kit, xf)
            val thrown = if (abs(d) < 1e-6f) 999f else abs((kit.traceHeight(xf) - level) / d)
            return "FLAT START  f′ ${sci(abs(d))}   THROWN ${sci(thrown)} AHEAD"
        }
        val k = done(c)
        var x = a0 + X0_OFF
        for (j in 0 until k) x = newton(kit, x, level)
        return "GUESS $k / $N   ERROR ${sci(abs(x - root))}"
    }

    // ---- drawing in figure coordinates ----------------------------------------------------------
    // x runs across the window (one node unit = K world units), y up it (one unit of height = the
    // same K), origin at the answer. Every helper below takes that pair, so no part of this scene
    // has to think about where the rail happens to be pointing.

    private fun seg(
        line: FloatArray, v: Int, x0: Float, y0: Float, x1: Float, y1: Float,
        c: FloatArray, a: Float, a2: Float = a
    ): Int {
        SceneParts.at(g, x0, y0, 0f, o)
        SceneParts.at(g, x1, y1, 0f, p)
        return MathMesh.segment(line, v, o[0], o[1], o[2], p[0], p[1], p[2], c[0], c[1], c[2], a, a2)
    }

    private fun dash(
        line: FloatArray, v: Int, x0: Float, y0: Float, x1: Float, y1: Float,
        n: Int, c: FloatArray, a: Float
    ): Int {
        SceneParts.at(g, x0, y0, 0f, o)
        SceneParts.at(g, x1, y1, 0f, p)
        return MathMesh.dashed(line, v, o[0], o[1], o[2], p[0], p[1], p[2], n, c[0], c[1], c[2], a)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Without a roof there is no equation to solve: traceHeight would hand back a flat zero,
        // every slope would be zero, and the method would have nothing to bite on.
        if (!kit.hasTrace) return

        SceneParts.stage(kit, i.toFloat(), SIDE_C, UP_C, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        val quality = kit.quality
        var v = 0
        tv[0] = 0

        val a0 = i.toFloat()
        val root = a0 + ROOT_OFF
        val level = kit.traceHeight(root)

        // The iterates, measured fresh every frame off the same curve the ribbon is drawn from.
        xs[0] = a0 + X0_OFF
        for (k in 1..N) xs[k] = newton(kit, xs[k - 1], level)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // Everything the hunt BUILDS is dimmed away over the last two seconds and built again from
        // nothing. A loop that simply cuts back to the start reads as a dropped frame on the
        // glasses; a viewer arriving at the wrap should see the first guess set out again, not the
        // staircase blink out of existence. The curve, its window and the level are exempt — the
        // roof does not blink, and neither does the height we are hunting for.
        val out = 1f - SceneParts.step(c, 0.91f, 0.06f)
        val fail = SceneParts.step(c, FAIL_AT, FAIL_LEN) * out
        for (k in 0 until N) rev[k] = SceneParts.step(c, STEP_AT + k * STEP_GAP, STEP_LEN)
        val settled = rev[N - 1] * out

        // --- the window it is all drawn in ------------------------------------------------------
        // A faint plate and a rim, pushed a couple of centimetres down-corridor so they cannot
        // fight the lines for depth. Without them the staircase hangs in the passage with nothing
        // to say where the picture ends.
        if (quality < 2) {
            SceneParts.at(g, LO * K, FY_BOT, 0.02f, o)
            SceneParts.vec(g, (HI - LO) * K, 0f, 0f, du)
            SceneParts.vec(g, 0f, FY_TOP - FY_BOT, 0f, dv)
            tv[0] = SceneParts.fill(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], SceneParts.COOL, 0.05f)
            v = SceneParts.edge(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], SceneParts.COOL, 0.20f)
        }

        // --- the target height, drawn where it lives ---------------------------------------------
        // A dashed rail-level line at height c, and the leader tying the window's zero to the point
        // on the actual ribbon where the roof comes down to it. These two lines are the whole
        // justification for plotting f − c off to one side: they say "that crossing up there is
        // this zero here", which is the only honest way to magnify anything.
        if (quality == 0) {
            kit.pointAt(root - LEVEL_RUN, 0f, level, 0f, o)
            kit.pointAt(root + LEVEL_RUN, 0f, level, 0f, p)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], p[0], p[1], p[2], 11,
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.30f)
        }
        kit.pointAt(root, 0f, level, 0f, q)
        if (quality < 2) {
            SceneParts.at(g, 0f, 0f, 0f, o)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], q[0], q[1], q[2], 9,
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.26f)
        }

        // --- the axis the guesses land on ---------------------------------------------------------
        // f = c, drawn right across the window. In this picture it plays the part the rail plays in
        // the textbook drawing: the line the tangents are slid down to.
        v = seg(line, v, LO * K, 0f, HI * K, 0f, SceneParts.COOL, 0.55f)

        // --- the drops back up to the curve, and a tick at each landing ---------------------------
        // The vertical leg of the staircase: having landed on the axis at x_{k+1}, the method goes
        // back to the curve to take the next tangent. Drawn dashed, because nothing travels along
        // it — it is a construction line, not an object.
        if (quality < 2) {
            for (k in 0 until N) {
                val d = ((rev[k] - 0.62f) / 0.38f).coerceIn(0f, 1f) * out
                if (d < 0.02f) continue
                val x1 = (xs[k + 1] - root) * K
                val y1 = (kit.traceHeight(xs[k + 1]) - level) * K
                v = dash(line, v, x1, 0f, x1, y1 * d, 4, SceneParts.WORK_DIM, 0.60f * d)
                v = seg(line, v, x1, -0.035f, x1, 0.035f, SceneParts.WORK, 0.75f * d)
            }
        }

        kit.flushTris(tv[0])
        kit.flushLines(v, 1.8f)

        // --- the bright pass ----------------------------------------------------------------------
        // A second flush so the curve and the staircase can be drawn heavier than the scaffolding
        // around them. Two draw calls buys the whole picture a foreground.
        v = 0
        val segs = when (quality) { 0 -> 46; 1 -> 26; else -> 15 }
        var px = LO * K
        var py = (kit.traceHeight(root + LO) - level) * K
        for (j in 1..segs) {
            val x = LO + (HI - LO) * j / segs
            val cx = x * K
            val cy = (kit.traceHeight(root + x) - level) * K
            v = seg(line, v, px, py, cx, cy, SceneParts.HOT, 0.95f)
            px = cx; py = cy
        }

        // The struts. Each one runs from the guess on the curve down to where its tangent meets the
        // axis, and each is left in the picture afterwards, so what you end up looking at is the
        // whole hunt at once: one long stride, one short one, and then two that have already
        // arrived. The third and fourth are drawn honestly and are consequently invisible. That is
        // not a bug in the drawing; it is the theorem.
        for (k in 0 until N) {
            if (rev[k] < 0.02f) continue
            val t = (rev[k] / 0.62f).coerceAtMost(1f)
            val x0 = (xs[k] - root) * K
            val y0 = (kit.traceHeight(xs[k]) - level) * K
            val x1 = (xs[k + 1] - root) * K
            v = seg(line, v, x0, y0, x0 + (x1 - x0) * t, y0 * (1f - t),
                SceneParts.WORK, 0.90f * out)
        }

        // The failure, in the colour of a thing that has gone wrong. The bead sits just past the
        // roof's turning point, where f′ is a twentieth of what it was at the first guess, and its
        // tangent is so nearly level that the axis is seven node units away. The ray is followed a
        // little past the rim and faded to nothing rather than chased: it does not land anywhere in
        // this picture, and the readout says how far away it does land.
        if (fail > 0.02f) {
            val xf = a0 + FAIL_OFF
            val bx = (xf - root) * K
            val by = (kit.traceHeight(xf) - level) * K
            val m = slope(kit, xf)
            val ray = (HI * K + FLING - bx) * fail
            v = seg(line, v, bx, by, bx + ray, by + m * ray, SceneParts.TAKEN, 0.95f * fail, 0f)
        }
        kit.flushLines(v, 2.8f)

        // --- the beads --------------------------------------------------------------------------
        // Only where a bead means something. x_2 is already a fiftieth of a node from the answer and
        // x_3 is closer than the sphere's own radius, so drawing five would be five draw calls to
        // put four of them in the same place.
        SceneParts.at(g, 0f, 0f, 0f, o)
        kit.ball(o[0], o[1], o[2], 0.055f, 0.055f, 0.055f, SceneParts.ADDED, SceneParts.HOT,
            0.35f + 0.65f * settled, 0f, 0f, 1f, 0f, 0f, 1.4f + 2.4f * kit.beat * settled)
        for (k in 0 until (if (quality == 0) 3 else if (quality == 1) 2 else 0)) {
            val a = (if (k == 0) 1f else ((rev[k - 1] - 0.62f) / 0.38f).coerceIn(0f, 1f)) * out
            if (a < 0.03f) continue
            SceneParts.at(g, (xs[k] - root) * K, (kit.traceHeight(xs[k]) - level) * K, 0f, o)
            kit.ball(o[0], o[1], o[2], 0.045f, 0.045f, 0.045f, SceneParts.WORK, SceneParts.HOT,
                a, 0f, 0f, 1f, 0f, 0f, 1.2f)
        }
        if (fail > 0.03f) {
            SceneParts.at(g, (a0 + FAIL_OFF - root) * K,
                (kit.traceHeight(a0 + FAIL_OFF) - level) * K, 0f, o)
            kit.ball(o[0], o[1], o[2], 0.045f, 0.045f, 0.045f, SceneParts.TAKEN, SceneParts.HOT,
                fail, 0f, 0f, 1f, 0f, 0f, 1.6f)
        }
        // The same answer, up on the ribbon the corridor is roofed with. q holds it from the leader
        // above, which is set under a looser quality gate than this bead, so it is always current.
        if (quality == 0) {
            kit.ball(q[0], q[1], q[2], 0.045f, 0.045f, 0.045f, SceneParts.ADDED, SceneParts.COOL,
                0.9f, 0f, 0f, 1f, 0f, 0f, 1.6f)
        }

        // --- notation ------------------------------------------------------------------------------
        // Every label here names a piece of the geometry: the equation the window is a picture of,
        // the line that is f = c, the two landings you can actually tell apart, and the step that
        // made them. The numbers stay on the HUD, where they are legible.
        if (quality < 2) {
            SceneParts.at(g, LO * K + 0.06f, 0.44f, 0f, o)
            kit.text("f(x) = c", o[0], o[1], o[2], 0.17f, SceneParts.HOT, 0.95f,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
            if (settled > 0.05f) {
                SceneParts.at(g, 0.10f, -0.15f, 0f, o)
                kit.text("x_4", o[0], o[1], o[2], 0.15f, SceneParts.ADDED, settled,
                    GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
            }
            if (fail > 0.05f) {
                SceneParts.at(g, (a0 + FAIL_OFF - root) * K - 0.05f,
                    (kit.traceHeight(a0 + FAIL_OFF) - level) * K + 0.11f, 0f, o)
                kit.text("f′ ≈ 0", o[0], o[1], o[2], 0.13f, SceneParts.TAKEN, fail,
                    GlyphBoard.Style.MATH, 1f, anchor = 0.5f)
            }
        }
        if (quality == 0) {
            SceneParts.at(g, LO * K + 0.05f, 0.06f, 0f, o)
            kit.text("c", o[0], o[1], o[2], 0.14f, SceneParts.COOL, 0.8f,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
            // The two landings that are far enough apart to label. x_2 onward sit on top of the
            // answer and would only stack glyphs there.
            SceneParts.at(g, (xs[0] - root) * K, -0.11f, 0f, o)
            kit.text("x_0", o[0], o[1], o[2], 0.13f, SceneParts.WORK, 0.85f * out)
            val a1 = ((rev[0] - 0.62f) / 0.38f).coerceIn(0f, 1f) * out
            if (a1 > 0.03f) {
                SceneParts.at(g, (xs[1] - root) * K - 0.05f, -0.07f, 0f, o)
                kit.text("x_1", o[0], o[1], o[2], 0.13f, SceneParts.WORK, 0.85f * a1,
                    GlyphBoard.Style.MATH, 1f, anchor = 0.5f)
            }
            // The step itself, in the empty quarter under the curve's right shoulder. One line, and
            // it names the strut: the tangent's own run back to the axis.
            SceneParts.at(g, 0.55f * K, -0.21f, 0f, o)
            kit.text("x_{k+1} = x_k − f/f′", o[0], o[1], o[2], 0.13f, SceneParts.CHALK, 0.75f,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        }
    }
}
