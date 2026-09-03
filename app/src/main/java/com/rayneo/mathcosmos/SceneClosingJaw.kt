package com.rayneo.mathcosmos

import kotlin.math.pow
import kotlin.math.round

/**
 * Tour II, stop 5 — THE CLOSING JAW. "Shrink the run until the chord stops moving; where it stops
 * is the curve's own direction."
 *
 * The flagship of the tour, and the one stop that has to survive being watched twice. THE CHORD
 * put the Caliper's jaws on the roof and measured one slope. Here the jaw closes — h, h/2, h/4,
 * h/8, h/16 — and every secant it leaves behind stays in the picture as a ghost, so the answer is
 * not asserted at the end but arrives as the visible limit of a fan of lines that stop moving.
 *
 * Two decisions about the staging are worth stating plainly, because both are compromises and the
 * crew says so out loud.
 *
 * FIRST: this is a magnified window onto the roof, turned to face the craft, not the roof itself.
 * The corridor's x axis is rail progress, and one node unit of it is sixteen world units of
 * corridor; the roof's height is in world units. So the real ribbon overhead is about sixteen
 * times flatter than the function it plots, and a chord drawn honestly on it would be a
 * nineteen-unit strut receding to a point down the passage, with the rotation between one secant
 * and the next far below a pixel. Worse, you fly along that axis, so you would see the whole fan
 * end-on. The window here plots f in its OWN units — one node unit across is one unit of height
 * tall, uniformly, so every angle in the window is a true angle of f — and it stands square to the
 * rail off to one side, where the fan is broadside for the whole approach. A dashed leader runs
 * from the window's contact point to a bead sitting on the actual ribbon at the same x, which is
 * the only honest way to say "that point up there is this point here".
 *
 * SECOND: the window plots the raw [SceneKit.traceHeight], not the squeezed roof the ambient
 * ribbon draws. SceneAmbientTrace presses the ribbon down where the passage narrows, so it is not
 * buried in the wall; at this anchor the press is about a hundredth of a unit and invisible, but
 * the press VARIES as the passage opens out past this stop, and reproducing it here would add a
 * spurious upward bend to the curve and rob the tangent of most of its slope. A stop about the
 * derivative cannot afford a curve whose shape is partly the corridor's. So: f, plotted straight.
 *
 * What is NOT here: the ship-shortening and the world-inflate the design asks for. The jaw span is
 * the ladder rung, and the ladder and the inflate are the renderer's to drive, not a scene's — a
 * scene may only draw. What this file owns is the visible half: the jaw, the fan and the settling.
 *
 * The tangent is taken by central difference on the same function the roof is drawn from, at two
 * thousandths of a node either side, so the final line is measured rather than hand-fitted. It is
 * drawn in the colour of the curve, touching it, because at the end of this stop they are the
 * same claim.
 */
object SceneClosingJaw : MathScene {

    // A flagship: worth fading up early so the fan is watchable for most of the leg rather than
    // flashing past. `deep` covers the leader and the ribbon bead, which sit a third of a stop
    // ahead of the node and would otherwise be culled with it.
    override val reach = 1.5f
    override val deep = 0.5f

    private const val PERIOD = 24f          // one closing, with a seven-second rest on the answer
    private const val ANCHOR = 0.35f        // node units past the stop: where the tangent is taken
    private const val LO = -0.20f           // window span, node units either side of the anchor
    private const val HI = 1.20f
    private const val K = 1.10f             // world units per node unit AND per unit of height
    private const val SIDE_C = -1.55f       // the anchor's place in the stage frame
    private const val UP_C = 0.22f
    private const val FY_TOP = 0.42f        // the window's own bounds, in figure world units
    private const val FY_BOT = -1.00f
    private const val BEAM = 0.30f          // how far above the anchor the caliper's beam rides
    private const val EPS = 0.002f          // central difference half-step, node units

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p = FloatArray(3)
    private val q = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)
    // The four halvings, eased. Scratch: filled at the top of draw and read nowhere else, so this
    // object still carries nothing at all between frames.
    private val step4 = FloatArray(4)

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree with the picture --

    /** How many halvings have happened by [c], as a smooth number: 0 at the clamp, 4 at the rest. */
    private fun expo(c: Float): Float =
        SceneParts.step(c, 0.14f, 0.09f) + SceneParts.step(c, 0.26f, 0.09f) +
            SceneParts.step(c, 0.38f, 0.09f) + SceneParts.step(c, 0.50f, 0.09f)

    /** The jaw span. Halving in the exponent rather than in h keeps the close smooth all the way. */
    private fun jaw(c: Float): Float = 0.5f.pow(expo(c))

    /** Three decimals, without a formatter: this runs every frame and String.format does not. */
    private fun n3(v: Float): String = (round(v * 1000f) / 1000f).toString()

    /**
     * The stop's measured numbers. The HUD asks the scene at floor(progress), so the anchor is
     * rebuilt from progress here rather than from the node index draw() is handed — inside the leg
     * this stop owns, the two agree exactly.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        val a = kit.progress.toInt() + ANCHOR
        val fa = kit.traceHeight(a)
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val h = jaw(c)
        return if (c < 0.62f) "h ${n3(h)}   CHORD ${n3((kit.traceHeight(a + h) - fa) / h)}"
        else "h → 0   f′(a) = ${n3((kit.traceHeight(a + EPS) - kit.traceHeight(a - EPS)) / (2f * EPS))}"
    }

    // ---- drawing in figure coordinates ----------------------------------------------------------
    // fx runs across the window (one node unit of x = K world units), fy up it (one unit of height
    // = the same K), origin at the contact point. Every helper below takes that pair, so no part of
    // this scene ever has to think about where the rail is pointing.

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

    /**
     * A line of slope [m] through the contact point, drawn right across the window.
     *
     * The chords are extended to full lines on purpose. A fan of five short chords all starting at
     * the same point reads as five chords; a fan of five LINES reads as a pencil of directions
     * closing on one, which is the thing this stop is about.
     */
    private fun fanLine(line: FloatArray, v: Int, m: Float, c: FloatArray, a: Float): Int =
        seg(line, v, LO * K, LO * K * m, HI * K, HI * K * m, c, a)

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Without a roof there is no function to take a chord of, and traceHeight would hand back
        // a flat zero — a fan of five identical horizontal lines, which teaches the opposite.
        if (!kit.hasTrace) return

        SceneParts.stage(kit, i.toFloat(), SIDE_C, UP_C, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        val quality = kit.quality
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // Everything the demonstration BUILDS is dimmed away over the last two seconds and rebuilt
        // from nothing. A loop that simply cuts back to the start reads as a dropped frame on the
        // glasses, and a viewer who arrives at the wrap sees the fan blink out of existence rather
        // than the jaw opening again. The curve and its window are exempt: the roof does not blink.
        val out = 1f - SceneParts.step(c, 0.92f, 0.06f)
        val clamp = SceneParts.step(c, 0.02f, 0.06f) * out
        val tanA = SceneParts.step(c, 0.60f, 0.10f) * out
        step4[0] = SceneParts.step(c, 0.14f, 0.09f)
        step4[1] = SceneParts.step(c, 0.26f, 0.09f)
        step4[2] = SceneParts.step(c, 0.38f, 0.09f)
        step4[3] = SceneParts.step(c, 0.50f, 0.09f)

        val ap = i + ANCHOR
        val fa = kit.traceHeight(ap)
        val h = jaw(c)
        val hK = h * K
        val dfK = (kit.traceHeight(ap + h) - fa) * K
        val m = dfK / hK
        val fp = (kit.traceHeight(ap + EPS) - kit.traceHeight(ap - EPS)) / (2f * EPS)

        // --- the window it is all drawn in ----------------------------------------------------
        // A faint plate and a rim. Without them the fan hangs in the corridor with nothing to say
        // where the picture ends; with them it reads as a magnifier held up to the roof. Pushed a
        // couple of centimetres down-corridor so it cannot fight the lines for depth.
        if (quality < 2) {
            SceneParts.at(g, LO * K, FY_BOT, 0.02f, o)
            SceneParts.vec(g, (HI - LO) * K, 0f, 0f, du)
            SceneParts.vec(g, 0f, FY_TOP - FY_BOT, 0f, dv)
            tv[0] = SceneParts.fill(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], SceneParts.COOL, 0.05f)
            v = SceneParts.edge(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], SceneParts.COOL, 0.20f)
        }

        // --- the leader to the real roof --------------------------------------------------------
        // The one line in the scene that leaves the window: from the contact point out to the same
        // x on the ribbon overhead. It is the whole justification for the magnification.
        if (quality < 2) {
            kit.pointAt(ap, 0f, kit.traceHeight(ap), 0f, q)
            SceneParts.at(g, 0f, 0f, 0f, o)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], q[0], q[1], q[2], 9,
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.26f)
        }

        // --- the fan of chords already left behind ---------------------------------------------
        // Ghost k is the secant at h = 2^-k. It fades in as its own halving begins — that is the
        // moment it stops being the live measurement — and dims a little further as later ones
        // pile up, but never to nothing: the whole point is that all five are on screen together
        // at the end, visibly stacking onto one line.
        for (k in 0 until 4) {
            if (step4[k] < 0.02f) continue
            var later = 0f
            for (j in k + 1 until 4) later += step4[j]
            if (k < 3) later /= (3 - k).toFloat()
            val hk = 0.5f.pow(k)
            val mk = (kit.traceHeight(ap + hk) - fa) / hk
            v = fanLine(line, v, mk, SceneParts.WORK, 0.50f * step4[k] * (1f - 0.42f * later) * out)
        }

        // --- the live secant, and the run and rise it is measured from --------------------------
        v = fanLine(line, v, m, SceneParts.WORK, 0.72f * clamp)
        if (quality < 2) {
            v = dash(line, v, 0f, 0f, hK, 0f, 5, SceneParts.WORK_DIM, 0.65f * clamp)
            v = dash(line, v, hK, 0f, hK, dfK, 3, SceneParts.ADDED, 0.75f * clamp)
        }

        // --- the caliper ------------------------------------------------------------------------
        // The jaw span IS h: the beam shortens as the ladder falls, and the two arms ride down it
        // onto the curve. It brightens when the craft's own probes reach out, which they do at this
        // stop — the Caliper is the ship, and this is the one stop where you watch it measure.
        if (quality < 2) {
            val top = BEAM + (1f - clamp) * 0.55f
            val ca = clamp * (0.55f + 0.45f * kit.reach)
            v = seg(line, v, 0f, top, hK, top, SceneParts.STEEL, ca)
            v = seg(line, v, 0f, top, 0f, 0f, SceneParts.STEEL, ca * 0.5f, ca)
            v = seg(line, v, hK, top, hK, dfK, SceneParts.STEEL, ca * 0.5f, ca)
            v = seg(line, v, -0.05f, 0f, 0.05f, 0f, SceneParts.STEEL, ca)
            v = seg(line, v, hK - 0.05f, dfK, hK + 0.05f, dfK, SceneParts.STEEL, ca)
        }

        kit.flushTris(tv[0])
        kit.flushLines(v, 1.8f)

        // --- the bright pass: the curve, the live chord, and the answer --------------------------
        // A second flush rather than one, so the curve and the tangent can be drawn heavier than
        // the scaffolding around them. Two draw calls buys the whole picture a foreground.
        v = 0
        val segs = when (quality) { 0 -> 44; 1 -> 24; else -> 14 }
        var px = LO * K
        var py = (kit.traceHeight(ap + LO) - fa) * K
        for (j in 1..segs) {
            val x = LO + (HI - LO) * j / segs
            val cx = x * K
            val cy = (kit.traceHeight(ap + x) - fa) * K
            v = seg(line, v, px, py, cx, cy, SceneParts.HOT, 0.95f)
            px = cx; py = cy
        }
        // The chord itself, brighter than its own extended line, so which two points are being
        // touched stays obvious even when the jaw is nearly shut.
        v = seg(line, v, 0f, 0f, hK, dfK, SceneParts.WORK, clamp)
        // And the limit, in the colour of the curve, touching it.
        if (tanA > 0.01f) v = fanLine(line, v, fp, SceneParts.HOT, tanA)
        kit.flushLines(v, 2.8f)

        // --- the contacts -----------------------------------------------------------------------
        SceneParts.at(g, 0f, 0f, 0f, o)
        kit.ball(o[0], o[1], o[2], 0.05f, 0.05f, 0.05f, SceneParts.HOT, SceneParts.WORK,
            1f, 0f, 0f, 1f, 0f, 0f, 1.2f + 2.2f * kit.beat)
        SceneParts.at(g, hK, dfK, 0f, o)
        kit.ball(o[0], o[1], o[2], 0.04f, 0.04f, 0.04f, SceneParts.ADDED, SceneParts.HOT,
            clamp, 0f, 0f, 1f, 0f, 0f, 1.4f)
        // The same x, up on the ribbon the corridor is roofed with. q holds it from the leader
        // above, which is drawn under a looser quality gate than this bead, so it is always set
        // by the time we get here.
        if (quality == 0) {
            kit.ball(q[0], q[1], q[2], 0.045f, 0.045f, 0.045f, SceneParts.HOT, SceneParts.COOL,
                0.9f, 0f, 0f, 1f, 0f, 0f, 1.6f)
        }

        // --- notation ---------------------------------------------------------------------------
        // Two labels, and both name a piece of the geometry rather than restating it. The numbers
        // live on the HUD, where they are legible; h leaves the picture as soon as the jaw is
        // narrower than its own label, which is somewhere around a quarter.
        if (quality == 0) {
            val hA = clamp * ((h - 0.10f) / 0.15f).coerceIn(0f, 1f)
            if (hA > 0.02f) {
                SceneParts.at(g, hK * 0.5f, 0.09f, 0f, o)
                kit.text("h", o[0], o[1], o[2], 0.14f, SceneParts.WORK_DIM, hA)
            }
            // One glyph on the far end of the leader, naming the ribbon the window is a window
            // onto. The ambient trace carries no notation of its own — it runs the length of the
            // tour and would collide with every stop — so this stop lends it a name for a moment.
            kit.pointAt(ap, 0.30f, kit.traceHeight(ap), 0f, o)
            kit.text("f", o[0], o[1], o[2], 0.20f, SceneParts.HOT, 0.75f)
        }
        if (quality < 2 && tanA > 0.02f) {
            SceneParts.at(g, 0.22f, 0.30f, 0f, o)
            kit.text("f′(a)", o[0], o[1], o[2], 0.19f, SceneParts.HOT, tanA,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        }
    }
}
