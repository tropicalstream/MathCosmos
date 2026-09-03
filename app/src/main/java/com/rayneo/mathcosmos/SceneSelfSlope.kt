package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.round

/**
 * Tour II, stop 10 — THE SELF-SLOPE. "There is exactly one growth curve whose steepness equals its
 * height, and that fixes the number e."
 *
 * A flagship, and the only stop in the tour where the payoff is an *equality* rather than a limit.
 * Eleven rings across a window. At each ring a rod stands up from the axis to the curve — that is
 * the height — and a second rod stands beside it whose length is the slope there. At base e the
 * two are the same length at every single ring, and a viewer can check that by eye eleven times
 * over. Then the base slides to 2 and every needle falls short of its rod; to 3 and every needle
 * overshoots; then back to e and the pairs lock level again. The number has not been asserted, it
 * has been cornered by a condition.
 *
 * Four decisions worth stating plainly, because three of them are compromises and the crew says so
 * out loud.
 *
 * FIRST, and the big one: **the corridor's roof on this leg is not eˣ, and this window does not
 * pretend it is.** The design asks for the roof TRACE to become the exponential here, but the trace
 * belongs to the tour — it is one ribbon built once in `applyMap`, running the whole rail, and a
 * scene may only draw, never retune it. So unlike THE CLOSING JAW five stops back, there is no
 * dashed leader from this window up to the ribbon: a leader would be a claim that the point up
 * there is the point down here, and it would be false. What the roof DOES do is set the scale:
 * [SceneKit.traceHeight] at this stop gives the ceiling, and the window is sized so that eˣ at the
 * right edge reaches about seven-tenths of it and 3ˣ's needle then just brushes it. The specimen is
 * mounted in the corridor and fitted to it; it is not the corridor.
 *
 * SECOND: **drawing a slope as a vertical length is a metaphor, and it only works because the
 * window has equal scales on both axes.** A slope is a ratio, not a length. One unit of x across
 * this window is exactly one unit of height up it, so "rise per unit run" and "rise" are drawn in
 * the same currency and can be stood side by side — the same gauge convention THE CHORD introduced
 * and THE FIELD OF SLOPES kept. The tangent through the marked ring is drawn dashed at its true
 * angle to keep the needles honest: that dashed line is where the needle lengths come from.
 *
 * THIRD: **3ˣ is a weak contrast and no amount of staging will fix it.** ln 3 is 1.0986, so the
 * needle is only a tenth longer than the rod — a couple of pixels at the short end of the window,
 * and only really visible on the two tallest rings. 2ˣ is generous by comparison at ln 2 = 0.693.
 * So the read is carried three ways at once: the LENGTH carries the magnitude, the COLOUR carries
 * the sign (red short, cyan long, warm white matched), and the number itself is on the HUD via
 * [readout], where the tour puts every number that has to be read.
 *
 * FOURTH: eleven rings, not a hundred. The same reason THE RIGHT ANGLE uses twenty-five countable
 * grains — a dense comb would look more impressive and would prove nothing.
 *
 * There is one extra tell for anyone who looks closely: the dashed tangent meets the axis exactly
 * 1/ln a to the left of the touch point, so its foot lands on the marked tick at x = −1 when and
 * only when the base is e. It drifts left for 2 and right for 3. Free, and it survives being seen
 * from the far end of the approach when the needle lengths do not.
 */
object SceneSelfSlope : MathScene {

    // A flagship worth fading up early, and entirely contained at its own node.
    override val reach = 1.5f

    private const val PERIOD = 26f          // one full sweep of bases, with a five-second rest on e

    // ---- the window, in graph units -----------------------------------------
    private const val X0 = -1.68f           // left edge; e^X0 is about a fifth, still a visible rod
    private const val X1 = 0.72f            // right edge; the tall end, where the contrast lives
    private const val DX = 0.24f
    private const val RINGS = 11
    private const val MARK = 7              // the ring at x = 0, where EVERY base passes through 1
    private const val GAP = 0.10f           // how far right of its rod a needle stands
    private const val WASH = 20             // trapezia in the faint fill under the curve

    // ---- where the window hangs off the rail --------------------------------
    // Off to one side and seated low. A flat figure centred on the rail is one you fly into, and a
    // figure seated ON the rail is one you have to look up at — and the telemetry owns the top of
    // the eye. Dropping the graph's own axis half a unit below the rail puts the middle of the
    // window at about eye level for the whole approach.
    private const val SIDE_C = -1.00f
    private const val UP_C = -0.50f

    private const val E_TOP = 2.0544f       // e^X1: what the scale is fitted to
    private const val TALLEST = 2.376f      // 3^X1 · ln 3: the tallest thing ever drawn, for the fit
    private const val ROOF_FRAC = 0.72f     // how much of the corridor's roof the base-e curve takes

    private const val LN2 = 0.6931472f
    private const val LN3 = 1.0986123f

    // ---- scratch. Nothing below allocates and nothing survives a frame ------
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p = FloatArray(3)
    private val cA = FloatArray(3)          // MathMesh.curve's two rolling samples
    private val cB = FloatArray(3)
    private val needle = FloatArray(4)      // the needle's colour, mixed fresh each frame
    private var kk = 0.62f                  // world units per graph unit; set at the top of draw()

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree ----------------

    /**
     * ln a at [c]. The interpolation is in ln a rather than in a, because ln a IS the quantity on
     * trial: it is exactly the ratio of needle to rod, so a linear-in-ln slide moves the needles
     * evenly and passes through the matched state precisely once each way.
     */
    private fun lnAt(c: Float): Float =
        1f + (LN2 - 1f) * SceneParts.step(c, 0.34f, 0.07f) +
            (LN3 - LN2) * SceneParts.step(c, 0.54f, 0.08f) +
            (1f - LN3) * SceneParts.step(c, 0.76f, 0.07f)

    /** 1 in the middle of a base change, 0 whenever the window is settled. Fades the label out. */
    private fun bump(c: Float, at: Float, len: Float): Float {
        val t = (c - at) / len
        return if (t <= 0f || t >= 1f) 0f else 1f - abs(t * 2f - 1f)
    }

    /** Which base is being claimed: 0 = e, 1 = 2, 2 = 3. Switched at the middle of each change. */
    private fun baseIdx(c: Float): Int = when {
        c < 0.375f -> 0
        c < 0.580f -> 1
        c < 0.795f -> 2
        else -> 0
    }

    /** Three decimals, without a formatter: this runs every frame and String.format does not. */
    private fun n3(v: Float): String = (round(v * 1000f) / 1000f).toString()

    /**
     * The stop's numbers, in the one place the tour allows numbers to be read. Both of them are the
     * same fact said twice — a is the base, ln a is the ratio of needle to rod — and watching them
     * arrive at 2.718 and 1.0 together is the whole stop in two columns.
     */
    override fun readout(kit: SceneKit): String? {
        val k = lnAt(SceneParts.cycle(kit.seconds, PERIOD))
        return "a ${n3(exp(k))}   f′/f ${n3(k)}"
    }

    // ---- drawing in figure coordinates --------------------------------------------------------
    // x runs across the window in graph units, y up it in the SAME graph units, origin at (0, 0)
    // of the plot. Every helper below takes that pair, so no part of this scene has to think about
    // which way the rail happens to be pointing.

    private fun seg(
        line: FloatArray, v: Int, x0: Float, y0: Float, x1: Float, y1: Float,
        c: FloatArray, a: Float, a2: Float = a
    ): Int {
        SceneParts.at(g, x0 * kk, y0 * kk, 0f, o)
        SceneParts.at(g, x1 * kk, y1 * kk, 0f, p)
        return MathMesh.segment(line, v, o[0], o[1], o[2], p[0], p[1], p[2], c[0], c[1], c[2], a, a2)
    }

    private fun dash(
        line: FloatArray, v: Int, x0: Float, y0: Float, x1: Float, y1: Float,
        n: Int, c: FloatArray, a: Float
    ): Int {
        SceneParts.at(g, x0 * kk, y0 * kk, 0f, o)
        SceneParts.at(g, x1 * kk, y1 * kk, 0f, p)
        return MathMesh.dashed(line, v, o[0], o[1], o[2], p[0], p[1], p[2], n, c[0], c[1], c[2], a)
    }

    /**
     * One trapezium of the wash under the curve. MathMesh.quad would give a parallelogram, and the
     * top edge here has to follow the curve, so the six vertices are written out.
     */
    private fun washQuad(
        tri: FloatArray, v: Int, x0: Float, y0: Float, x1: Float, y1: Float, c: FloatArray, a: Float
    ): Int {
        if ((v + 6) * MathMesh.STRIDE > tri.size) return v
        var k = v
        SceneParts.at(g, x0 * kk, 0f, 0f, o)
        val ax = o[0]; val ay = o[1]; val az = o[2]
        SceneParts.at(g, x1 * kk, 0f, 0f, o)
        val bx = o[0]; val by = o[1]; val bz = o[2]
        SceneParts.at(g, x1 * kk, y1 * kk, 0f, o)
        val tx = o[0]; val ty = o[1]; val tz = o[2]
        SceneParts.at(g, x0 * kk, y0 * kk, 0f, p)
        k = MathMesh.vertex(tri, k, ax, ay, az, c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, bx, by, bz, c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, tx, ty, tz, c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, ax, ay, az, c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, tx, ty, tz, c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, p[0], p[1], p[2], c[0], c[1], c[2], a)
        return k
    }

    private fun say(
        kit: SceneKit, s: String, x: Float, y: Float, h: Float, c: FloatArray, a: Float,
        style: GlyphBoard.Style = GlyphBoard.Style.MATH, anchor: Float = -0.5f
    ) {
        SceneParts.at(g, x * kk, y * kk, 0f, o)
        kit.text(s, o[0], o[1], o[2], h, c, a, style, 1f, anchor)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        SceneParts.stage(kit, at, SIDE_C, UP_C, f, g)

        // The scale, taken from the corridor rather than chosen. The base-e curve's right end is
        // fitted to seven-tenths of the roof height at this stop, which on the leg as it stands
        // gives about 0.70 world units per graph unit: a window 1.7 across whose far corner sits
        // 2.5 out, comfortably inside the 0.8 R line of a 3.4-unit passage. The upper clamp is
        // what holds that if the trace is ever raised; the lower one keeps the window readable if
        // the roof is ever brought down.
        val roof = if (kit.hasTrace) abs(kit.traceHeight(at)) else 0f
        val lid = if (roof > 0.6f) roof else 1.30f
        val fit = (lid * ROOF_FRAC - UP_C) / E_TOP
        val cap = (lid * 0.98f - UP_C) / TALLEST       // and the 3ˣ needle still clears the ceiling
        kk = (if (fit < cap) fit else cap).coerceIn(0.42f, 0.72f)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val lnA = lnAt(c)
        val grow = SceneParts.step(c, 0.02f, 0.14f)

        // How matched we are. Sharp on purpose: at ln 3 the ratio is off by a tenth and the needle
        // must NOT read as warm-white, or the one thing this stop is for stops being an equality.
        val d = lnA - 1f
        val m = (1f - abs(d) * 12f).coerceIn(0f, 1f)
        val other = if (d < 0f) SceneParts.TAKEN else SceneParts.ADDED
        needle[0] = other[0] + (SceneParts.HOT[0] - other[0]) * m
        needle[1] = other[1] + (SceneParts.HOT[1] - other[1]) * m
        needle[2] = other[2] + (SceneParts.HOT[2] - other[2]) * m
        needle[3] = 1f

        // --- the axis, its tick at the touch point, and the tick the tangent's foot aims at ----
        v = seg(line, v, X0 - 0.04f, 0f, X1 + 0.28f, 0f, SceneParts.CHALK, 0.55f)
        v = seg(line, v, X1 + 0.28f, 0f, X1 + 0.18f, 0.06f, SceneParts.CHALK, 0.55f)
        v = seg(line, v, X1 + 0.28f, 0f, X1 + 0.18f, -0.06f, SceneParts.CHALK, 0.55f)
        v = seg(line, v, 0f, 0.06f, 0f, -0.10f, SceneParts.CHALK, 0.60f)
        v = seg(line, v, -1f, 0.04f, -1f, -0.12f, SceneParts.CHALK, 0.60f)

        // --- the level every base passes through -----------------------------------------------
        // a^0 = 1 whatever a is, so all three curves are pinned to this one point and only their
        // steepness differs. Worth a line of its own: it is what makes the comparison fair.
        v = dash(line, v, X0, 1f, X1 + 0.16f, 1f, 7, SceneParts.CHALK, 0.32f)

        // --- the curve, written in from the left once per loop -----------------------------------
        val tEnd = X0 + (X1 - X0) * (if (grow > 0.02f) grow else 0.02f)
        v = MathMesh.curve(
            line, v, 44, X0, tEnd,
            SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.95f, false, cA, cB
        ) { t, out ->
            SceneParts.at(g, t * kk, exp(lnA * t) * kk, 0f, out)
        }

        // --- the wash under it, so the shape reads at a glance on a waveguide ---------------------
        if (kit.quality == 0) {
            var j = 0
            while (j < WASH) {
                val xa = X0 + (X1 - X0) * j / WASH
                val xb = X0 + (X1 - X0) * (j + 1) / WASH
                if (xb <= tEnd) {
                    tv = washQuad(tri, tv, xa, exp(lnA * xa), xb, exp(lnA * xb), SceneParts.WORK, 0.09f)
                }
                j++
            }
        }

        // --- the eleven pairs ---------------------------------------------------------------------
        // rod = the height, needle = the steepness, and a faint tick carrying the rod's top across
        // the needle's column so the eye has a level to judge against. Reaching that level, falling
        // short of it, or passing it is a far easier read than comparing two free-standing lengths.
        val stride = if (kit.quality == 0) 1 else 2
        var k = if (kit.quality == 0) 0 else 1        // odd rings at low quality, so MARK survives
        while (k < RINGS) {
            val x = X0 + k * DX
            val gk = SceneParts.ease((grow * 1.35f - k / (RINGS - 1f) * 0.55f) / 0.50f)
            if (gk > 0.01f) {
                val h = exp(lnA * x) * gk
                val s = lnA * h
                val a = if (k == MARK) 1f else 0.85f
                v = seg(line, v, x, 0f, x, h, SceneParts.WORK, a)
                v = seg(line, v, x, h, x + GAP + 0.045f, h, SceneParts.WORK, a * 0.42f)
                v = seg(line, v, x + GAP, 0f, x + GAP, s, needle, a)
            }
            k += stride
        }

        // --- the tangent the needles are measured from ---------------------------------------------
        // Dashed, because it is a construction line and not an object, and because it has to cross
        // the comb of rods without competing with them. Its foot is at −1/ln a, which lands on the
        // marked tick at x = −1 exactly when the base is e.
        if (kit.quality < 2 && grow > 0.9f) {
            val foot = -1f / lnA
            v = dash(line, v, foot, 0f, 0.34f, 1f + 0.34f * lnA, 12, needle, 0.62f)
            v = seg(line, v, foot, -0.09f, foot, 0.09f, needle, 0.85f)
        }

        kit.flushLines(v, 2.3f)
        kit.flushTris(tv)

        // The lamp on the touch point when the pairs are level. One ball, one draw call, and it
        // breathes with the sound cue so the match reads as an event and not as a static state.
        if (m > 0.05f && grow > 0.9f) {
            SceneParts.at(g, 0f, kk, 0f, o)
            kit.ball(
                o[0], o[1], o[2], 0.055f, 0.055f, 0.055f, SceneParts.HOT, SceneParts.ADDED,
                m, 0f, 0f, 1f, 0f, 0f, (2.2f + 1.6f * kit.beat) * m
            )
        }

        // --- notation ------------------------------------------------------------------------------
        // Both labels sit in the empty upper-left of the window's own footprint, where the curve's
        // tail leaves a wedge of nothing. Beside the figure, inside its box: the telemetry owns the
        // top of the eye and the caption box the bottom, and neither of them is negotiable.
        val morph = maxOf(bump(c, 0.34f, 0.07f), maxOf(bump(c, 0.54f, 0.08f), bump(c, 0.76f, 0.07f)))
        val settled = (1f - morph * 1.6f).coerceIn(0f, 1f) * grow
        val b = baseIdx(c)
        say(kit, if (b == 0) "e^x" else if (b == 1) "2^x" else "3^x",
            X0 + 0.10f, 1.92f, 0.24f, SceneParts.WORK, settled)
        say(kit, if (b == 0) "f′ = f" else if (b == 1) "f′ < f" else "f′ > f",
            X0 + 0.10f, 1.36f, 0.20f, needle, settled)

        if (kit.quality == 0) {
            say(kit, "1", X0 - 0.14f, 1f, 0.15f, SceneParts.CHALK, 0.75f, GlyphBoard.Style.PLAIN, 0.5f)
            say(kit, "x", X1 + 0.36f, 0.14f, 0.15f, SceneParts.CHALK, 0.70f)
        }
    }
}
