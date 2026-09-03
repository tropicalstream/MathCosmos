package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.sin

/**
 * THE STRETCHED RULER — a slide rule hanging alongside the rail, working.
 *
 * Two rules run down the passage beside the craft, one above the other, each marked 1 to 10 with
 * the marks placed at log10 of their value. The upper one slides forward by the length of its own
 * mark for `a`; then the upper mark for `b` is sitting exactly over the lower mark for `a·b`, and
 * a lit strut is dropped between them to say so.
 *
 * The whole stop rests on one substitution, so it is worth being explicit about what is honest
 * here. Every mark sits at a position φ(v), and the slide moves the upper rule by φ(a). Reading
 * across therefore always lands on the value w with φ(w) = φ(a) + φ(b) — the rule performs an
 * ADDITION OF LENGTHS and nothing else, whatever the numbers painted on it happen to be. Choose
 * φ = log10 and that addition reads as a·b, because log is the one map that turns multiplying into
 * adding. So at the end of each cycle the same two rules straighten out — φ becomes v/10, position
 * proportional to value — and the identical slide is performed again. The lengths still add; only
 * now the answer says a+b. Nothing about the mechanism changed. The stretching is the whole trick,
 * and the last beat is there so you can see precisely what it bought.
 *
 * (When they straighten, a stub of bare rule appears before the "1". That is not a mistake: an
 * even scale must be proportional to the value or the slide would not add at all, so the origin
 * of an even rule is where ZERO lives, and 1 stands a tenth of the way along. On the log rule the
 * origin is 1 itself, because log 1 = 0. That gap is the difference between the two worlds.)
 *
 * The two coloured bars beneath are the same two lengths taken off the rules and laid end to end:
 * bar A is the lower rule's stretch from its origin to `a`, bar B is the upper rule's stretch from
 * its own origin to `b`, and they meet exactly under the answer because the slide put the upper
 * rule's origin on the lower rule's `a`. That is not an illustration of the argument, it IS the
 * argument, and it stays exactly true through the straightening beat as well.
 *
 * BUDGET. This is the most notation-heavy stop in Tour I and it sits near the ceiling at full
 * quality: three rods, two lit beads, one line flush, one triangle flush and twenty-two labels,
 * about twenty-nine calls. Everything else — twenty tick marks, twenty bead collars, two bars,
 * three drop lines — goes through the shared buffers and costs two calls between the lot of them.
 * Note what is NOT halved at quality 1: the ten marks per rule. Uneven spacing with the numbers
 * on it is the landmark; halving that loop would delete the stop to save nothing. What goes
 * instead is the decoration on the marks (the labels, the collars) and then, at quality 2, the
 * bars and their drop lines, leaving the two rules, the slide, the strut and the two lit beads.
 */
object SceneLogRuler : MathScene {

    /** The rules run along the rail, so the landmark is in view early and stays late. */
    override val reach = 1.5f
    override val deep = 0.45f

    // ------------------------------------------------------------- the numbers

    /**
     * log10 of 1..10, indexed by value-1. A table rather than a call: draw() runs thirty times a
     * second and must not compute what it can look up, and these ten numbers will never change.
     */
    private val LOG10 = floatArrayOf(
        0f, 0.30103f, 0.47712f, 0.60206f, 0.69897f,
        0.77815f, 0.84510f, 0.90309f, 0.95424f, 1f
    )

    /** Constant strings: a label built per frame would allocate and defeat the glyph cache. */
    private val MARKS = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")

    /** Two passes per full loop: slide by log 2, then by log 3, with b held at 3 both times so
     *  the only thing that changes between them is how far the rule went. */
    private val A_OF = intArrayOf(2, 3)
    private const val B_IDX = 2                       // b = 3, as an index into the mark tables

    private val PROD = arrayOf("2 × 3 = 6", "3 × 3 = 9")
    private val SUMS = arrayOf("2 + 3 = 5", "3 + 3 = 6")
    private val LOGSUM = arrayOf("0.30 + 0.48 = 0.78", "0.48 + 0.48 = 0.95")
    private val EVENSUM = arrayOf("0.20 + 0.30 = 0.50", "0.30 + 0.30 = 0.60")
    private val READ = arrayOf(
        "2 × 3 = 6   (0.30 + 0.48 = 0.78)",
        "3 × 3 = 9   (0.48 + 0.48 = 0.95)"
    )
    private val READ_EVEN = arrayOf(
        "even rule: 2 + 3 = 5   (0.20 + 0.30 = 0.50)",
        "even rule: 3 + 3 = 6   (0.30 + 0.30 = 0.60)"
    )

    private const val PERIOD = 24f

    // ------------------------------------------------------------- the palette

    private val RULE = floatArrayOf(0.66f, 0.71f, 0.84f, 1f)
    private val RULE_DARK = floatArrayOf(0.20f, 0.23f, 0.32f, 1f)
    private val MARK = floatArrayOf(0.84f, 0.89f, 1.00f, 1f)
    private val LABEL = floatArrayOf(0.88f, 0.92f, 1.00f, 1f)

    // ------------------------------------------------------------- scratch

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val p0 = FloatArray(3)
    private val p1 = FloatArray(3)
    private val e1 = FloatArray(3)
    private val e2 = FloatArray(3)

    /** Line and triangle vertex counts, carried through the helpers the way SceneParts.pane does. */
    private val cnt = IntArray(2)

    // ------------------------------------------------------------- the cycle

    /** How straight the rules are: 0 logarithmic, 1 ruled evenly. Back to 0 before the loop wraps. */
    private fun warpAt(c: Float): Float =
        SceneParts.step(c, 0.76f, 0.07f) - SceneParts.step(c, 0.90f, 0.07f)

    /** Which of the two passes is running. Alternates every cycle, so the full pattern is 48 s. */
    private fun passAt(seconds: Float): Int = (seconds / PERIOD).toInt() and 1

    /**
     * Where the mark for value k+1 sits along the rule, as a distance from its origin.
     * One formula for both worlds: log10 when [warp] is 0, proportional to the value when it is 1.
     */
    private fun phi(k: Int, warp: Float, dec: Float): Float {
        val even = 0.1f * (k + 1)
        return (LOG10[k] + (even - LOG10[k]) * warp) * dec
    }

    override fun readout(kit: SceneKit): String? {
        val pass = passAt(kit.seconds)
        // The HUD names what this pass is doing, including through the rest beat before the slide:
        // it is the caption of the stop, not a running commentary on the animation.
        return if (warpAt(SceneParts.cycle(kit.seconds, PERIOD)) > 0.5f) READ_EVEN[pass] else READ[pass]
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        val rad = kit.radius(at)

        // One decade is a length ALONG the rail, where the passage does not run out; the 0.8-radius
        // budget only binds across it. The assembly hangs a third of a radius to the side so the
        // craft flies past the rule rather than through it, and is lifted a little so its centre of
        // mass — the two rules, not the bars — sits near eye level on the way past.
        val dec = rad * 1.5f
        SceneParts.stage(kit, at, rad * 0.36f, dec * 0.06f, f, g)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val pass = passAt(kit.seconds)
        val aIdx = A_OF[pass] - 1

        // Rest, slide, read, lay the bars out, hold, straighten, rest again.
        val slideT = SceneParts.step(c, 0.10f, 0.20f)
        val readT = SceneParts.step(c, 0.34f, 0.10f)
        val barAT = SceneParts.step(c, 0.46f, 0.08f)
        val barBT = SceneParts.step(c, 0.54f, 0.08f)
        val warp = warpAt(c)

        val gap = dec * 0.078f                        // half the gap between the two rules
        val lowerU = -gap
        val upperU = gap
        val lowerA0 = -dec * 0.55f                    // the lower rule's origin, just behind the stop
        val slide = phi(aIdx, warp, dec) * slideT
        val upperA0 = lowerA0 + slide
        val ansA = upperA0 + phi(B_IDX, warp, dec)    // where b on the upper rule now points

        // Marks are pushed a hair toward the rail — that is where the camera always is, since the
        // craft rides the centreline and the assembly hangs to one side — so a tick or a collar
        // reads as sitting ON the rod rather than half-buried in it and z-fighting.
        val front = -dec * 0.017f

        cnt[0] = 0; cnt[1] = 0

        // ---- the two rules -----------------------------------------------------------------
        // The lower rule's ticks face up and the upper rule's face down, as on a real rule, so the
        // two scales can be compared straight across the slot; the numbers go on the outsides.
        val collars = kit.quality < 1
        rule(kit, dec, lowerA0, lowerU, 1f, front, warp, if (slideT > 0.05f) aIdx else -1, collars)
        rule(kit, dec, upperA0, upperU, -1f, front, warp, if (readT > 0.05f) B_IDX else -1, collars)

        // ---- the two lengths, laid end to end ----------------------------------------------
        val barU = lowerU - dec * 0.150f
        val barH = dec * 0.040f
        if (kit.quality < 2 && barAT > 0.01f) {
            val la = phi(aIdx, warp, dec)
            val lb = phi(B_IDX, warp, dec)
            bar(kit, lowerA0, la * barAT, barU, barH, SceneParts.WORK, barAT)
            if (barBT > 0.01f) bar(kit, lowerA0 + la, lb * barBT, barU, barH, SceneParts.ADDED, barBT)

            // Drops from the rule down to the bar row: where the first length starts, where it
            // ends and the second begins, and — the one that matters — where the total lands.
            drop(kit, lowerA0, lowerU, barU + barH * 0.5f, dec, SceneParts.WORK, barAT * 0.55f)
            drop(kit, lowerA0 + la, lowerU, barU + barH * 0.5f, dec, SceneParts.ADDED, barAT * 0.55f)
            drop(kit, ansA, lowerU, barU + barH * 0.5f, dec, SceneParts.HOT, barBT * 0.95f)
        }

        kit.flushTris(cnt[1])
        kit.flushLines(cnt[0], 2.2f)

        // ---- the answer --------------------------------------------------------------------
        // One strut, two lit beads, at the SAME point along the rail on both rules. That identity
        // of position is the reading; there is nothing else to the instrument.
        if (readT > 0.02f) {
            SceneParts.at(g, front, upperU, ansA, p0)
            SceneParts.at(g, front, lowerU, ansA, p1)
            kit.rod(
                p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
                dec * 0.010f * readT, SceneParts.HOT, SceneParts.WORK, 0.9f
            )
            val pulse = 0.88f + 0.12f * sin(kit.seconds * 2.4f) + 0.30f * kit.beat
            val r = dec * 0.027f * readT * pulse
            kit.ball(p0[0], p0[1], p0[2], r, r, r, SceneParts.HOT, SceneParts.WORK, readT, 0f, 0f, 1f, 0f, 0f, 1.1f)
            kit.ball(p1[0], p1[1], p1[2], r, r, r, SceneParts.HOT, SceneParts.WORK, readT, 0f, 0f, 1f, 0f, 0f, 1.1f)
        }

        // ---- notation ----------------------------------------------------------------------
        // The numbers ON the marks are the point of the stop, so they are the last thing dropped.
        if (kit.quality == 0) {
            marks(kit, dec, lowerA0, lowerU, 1f, front, warp, 0.85f)
            marks(kit, dec, upperA0, upperU, -1f, front, warp, 0.85f)
        }

        // The statement swaps at the halfway point of the straightening, dipping through zero so
        // the change of operation is a visible event rather than a glyph mutating in place.
        val swapA = abs(warp - 0.5f) * 2f
        if (readT > 0.02f) {
            SceneParts.at(g, -dec * 0.09f, 0f, ansA, p0)
            kit.text(
                if (warp < 0.5f) PROD[pass] else SUMS[pass],
                p0[0], p0[1], p0[2], dec * 0.062f, SceneParts.HOT, readT * swapA,
                GlyphBoard.Style.MATH, 1.2f
            )
        }
        if (kit.quality == 0 && barBT > 0.02f) {
            SceneParts.at(g, 0f, barU - dec * 0.075f, lowerA0, p0)
            kit.text(
                if (warp < 0.5f) LOGSUM[pass] else EVENSUM[pass],
                p0[0], p0[1], p0[2], dec * 0.040f, SceneParts.CHALK, barBT * swapA * 0.9f,
                GlyphBoard.Style.PLAIN, 0.9f, anchor = -0.5f
            )
        }
    }

    // ------------------------------------------------------------- the pieces

    /**
     * One rule: the rod, and ten marks on it. [facing] is +1 for a rod whose ticks point up toward
     * its partner, -1 for one whose ticks point down. [hi] brightens a single mark, or -1 for none.
     * The rod runs a little past both ends so that a scale which starts at its origin still reads
     * as painted on a bar rather than as the bar itself.
     */
    private fun rule(
        kit: SceneKit, dec: Float, a0: Float, u: Float, facing: Float, front: Float,
        warp: Float, hi: Int, collars: Boolean
    ) {
        val stub = dec * 0.05f
        SceneParts.at(g, 0f, u, a0 - stub, p0)
        SceneParts.at(g, 0f, u, a0 + dec + stub, p1)
        kit.rod(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], dec * 0.012f, RULE, RULE_DARK, 0.10f)

        val tick = dec * 0.030f
        val bead = dec * 0.019f
        for (k in 0..9) {
            val a = a0 + phi(k, warp, dec)
            // From seven upward the marks are closer together than a numeral is wide, so the odd
            // ones send their tick further out and take a second row of labels. The crowding is
            // not a defect to be designed away — it is what a log scale looks like — but it does
            // have to stay readable at 640x480 through a waveguide.
            val outer = if (k >= 6 && (k and 1) == 0) dec * 0.052f else 0f
            SceneParts.at(g, front, u - facing * (tick * 0.45f + outer), a, p0)
            SceneParts.at(g, front, u + facing * tick, a, p1)
            cnt[0] = MathMesh.segment(
                kit.lineBuf, cnt[0], p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
                MARK[0], MARK[1], MARK[2], if (k == hi) 1f else 0.60f
            )
            if (!collars) continue
            // The bead itself: a small diamond across the rod, cheap enough to have twenty of
            // because it is six vertices in the shared triangle buffer and not a draw call.
            SceneParts.at(g, front, u, a - bead, p0)
            SceneParts.vec(g, 0f, bead, bead, e1)
            SceneParts.vec(g, 0f, -bead, bead, e2)
            cnt[1] = MathMesh.quad(
                kit.triBuf, cnt[1], p0[0], p0[1], p0[2],
                e1[0], e1[1], e1[2], e2[0], e2[1], e2[2],
                MARK[0], MARK[1], MARK[2], if (k == hi) 0.95f else 0.40f
            )
        }
    }

    /**
     * The numerals for one rule, on the side away from the slot.
     *
     * They share the marks' small offset toward the rail, which puts every part of the instrument
     * — rod ticks, collars, numbers — in one plane just in front of the rod, and leaves the
     * construction lines behind it. A drop line that has to pass a numeral therefore reads as
     * being behind it: on an additive display the glyph simply stays the brighter of the two.
     */
    private fun marks(
        kit: SceneKit, dec: Float, a0: Float, u: Float, facing: Float, front: Float,
        warp: Float, alpha: Float
    ) {
        val h = dec * 0.042f
        for (k in 0..9) {
            val row = if (k >= 6 && (k and 1) == 0) 1 else 0
            val off = dec * (0.068f + 0.052f * row)
            SceneParts.at(g, front, u - facing * off, a0 + phi(k, warp, dec), p0)
            kit.text(MARKS[k], p0[0], p0[1], p0[2], h, LABEL, alpha, GlyphBoard.Style.SMALL, 0.9f)
        }
    }

    /** One coloured length, lying along the rail from [a0] for [len], centred on [u]. */
    private fun bar(kit: SceneKit, a0: Float, len: Float, u: Float, h: Float, c: FloatArray, alpha: Float) {
        if (len <= 1e-4f) return
        SceneParts.at(g, 0f, u - h * 0.5f, a0, p0)
        SceneParts.vec(g, 0f, 0f, len, e1)
        SceneParts.vec(g, 0f, h, 0f, e2)
        cnt[1] = SceneParts.fill(
            kit.triBuf, cnt[1], p0[0], p0[1], p0[2],
            e1[0], e1[1], e1[2], e2[0], e2[1], e2[2], c, alpha * 0.30f
        )
        cnt[0] = SceneParts.edge(
            kit.lineBuf, cnt[0], p0[0], p0[1], p0[2],
            e1[0], e1[1], e1[2], e2[0], e2[1], e2[2], c, alpha * 0.90f
        )
    }

    /** A dashed drop from the lower rule down to the bar row, so a bar's end can be read off it. */
    private fun drop(
        kit: SceneKit, a: Float, fromU: Float, toU: Float, dec: Float, c: FloatArray, alpha: Float
    ) {
        SceneParts.at(g, 0f, fromU - dec * 0.030f, a, p0)
        SceneParts.at(g, 0f, toU, a, p1)
        cnt[0] = MathMesh.dashed(
            kit.lineBuf, cnt[0], p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
            5, c[0], c[1], c[2], alpha
        )
    }
}
