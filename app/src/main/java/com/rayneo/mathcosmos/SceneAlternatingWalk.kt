package com.rayneo.mathcosmos

import kotlin.math.cos
import kotlin.math.sin

/**
 * Stop 6 — THE ALTERNATING WALK. "Step forward, step back a little less, and I close in — and the
 * next step is my error bar."
 *
 * 1 − 1/2 + 1/3 − 1/4 + … walked as a physical pacing along an interval. Each term is one swing:
 * forward a whole unit, back half a unit, forward a third, back a quarter. The swings overshoot and
 * undershoot alternately, each one shorter than the last, and the walker closes on ln 2 from both
 * sides at once. Every completed swing is left behind as a hop over the line, so the concertina the
 * design asks for builds up in front of the viewer and its shrinking is the whole argument.
 *
 * The error bound is not stated, it is drawn. Because the swings alternate and shrink, the limit is
 * always trapped BETWEEN the last two partial sums — so the bright band spanning s_n to s_{n+1} is
 * simultaneously the next step's reach and the error bar, one object doing both jobs. It is exactly
 * a_{n+1} wide, and it visibly straddles the limit for the whole walk. That is the alternating
 * series test with nothing left over.
 *
 * Two honest departures from the design, and they are worth naming.
 *
 * First, the spec has the SHIP perform the walk down the rail. A scene cannot move the craft — the
 * rail owns that — so the walk is a figure the craft flies past rather than a ride it takes. The
 * pacing is preserved; the point of view is not.
 *
 * Second, the spec has the passage radius equal the next term at every instant. A scene cannot set
 * the passage radius either, so the collar is DRAWN: one ring round the rail whose radius is the
 * current error bound, tightening as the walk proceeds. The map already narrows this stop to 2.4
 * against its neighbours' 3.2, so the corridor is doing half the work; the ring does the rest and
 * says so.
 *
 * The last beat is Doc's. The same terms in a different order land somewhere else entirely: put two
 * positives to every negative and the walk converges to (3/2)ln 2 instead, which is off the end of
 * the interval it has been pacing. That destination appears at rest as a red ghost with a dashed
 * run out to it, because a viewer who has just watched a walk close on a point will otherwise take
 * the point to be a property of the terms. It is a property of the terms IN THAT ORDER.
 */
object SceneAlternatingWalk : MathScene {

    override val reach = 1.5f
    override val deep = 0.2f

    private const val N = 12               // terms walked in one cycle at full quality
    private const val SPAN = 1.7f          // world units the interval 0..1 is drawn across
    private const val SIDE = -1.00f        // the figure hangs to one side; you fly past it, not into it
    private const val UP = 0.15f
    private const val HOP = 0.45f          // hop height as a fraction of its half-width — see below
    private const val BAND = 0.05f         // half-height of the error band lying on the walk line
    private const val PERIOD = 24f
    private const val START = 0.06f
    private const val LEN = 0.60f          // the walk takes 60% of the cycle; the rest is for looking
    private const val LN2 = 0.6931472f
    private const val REORDER = 1.0397208f // (3/2)ln 2 — the same terms, two positives to a negative
    private const val PI = 3.14159265f
    private const val COLLAR_BACK = -2.2f  // the ring sits off along the rail, not in your face

    // Partial sums, filled once: S[0] = 0, S[k] = S[k-1] + (-1)^(k+1)/k. One spare entry on the end
    // so the error band always has a next partial sum to reach for.
    private val S = FloatArray(N + 2)
    private var built = false

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    // Every string this scene can draw, written out rather than assembled — a scene that builds a
    // label in draw() allocates thirty times a second, and this device reboots when it gets hot.
    private val TERM = arrayOf(
        "+1", "−1/2", "+1/3", "−1/4", "+1/5", "−1/6",
        "+1/7", "−1/8", "+1/9", "−1/10", "+1/11", "−1/12"
    )
    private val CLAIM = arrayOf(
        "1",
        "1 − 1/2",
        "1 − 1/2 + 1/3",
        "1 − 1/2 + 1/3 − 1/4",
        "Σ (−1)^{n+1}/n",
        "Σ (−1)^{n+1}/n = ln 2"
    )

    private fun build() {
        if (built) return
        var s = 0f
        S[0] = 0f
        for (j in 1..N + 1) {
            s += (if (j % 2 == 1) 1f else -1f) / j
            S[j] = s
        }
        built = true
    }

    /** Where a value on the interval lands in the stage's own right-hand direction. */
    private fun sOf(v: Float): Float = -SPAN * 0.5f + v * SPAN

    /** How many terms get walked. Twelve is the picture; the governor takes six. */
    private fun terms(kit: SceneKit): Int = if (kit.quality == 0) N else N / 2

    /** Completed steps at this instant. The fractional part is how far into the next swing we are. */
    private fun stepsDone(kit: SceneKit): Int {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val t = ((c - START) / LEN).coerceIn(0f, 1f) * terms(kit)
        return t.toInt().coerceAtMost(terms(kit))
    }

    private fun swingFrac(kit: SceneKit): Float {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val t = ((c - START) / LEN).coerceIn(0f, 1f) * terms(kit)
        val k = t.toInt()
        return if (k >= terms(kit)) 0f else t - k
    }

    /**
     * The numbers belong here, not in the scene. n, where the walk has got to, and the bound the
     * alternating test hands you for free — which is simply the size of the step not yet taken.
     */
    override fun readout(kit: SceneKit): String? {
        build()
        val k = stepsDone(kit)
        return "n %d   s %.4f   err ≤ %.4f".format(java.util.Locale.US, k, S[k], 1f / (k + 1))
    }

    /**
     * One swing, as a hop over the walk line from S[j-1] to S[j]. [upto] draws only the first
     * fraction of it, which is how the swing in progress grows rather than appearing whole.
     *
     * The hop is a flattened half-circle, not a true one: its WIDTH is the term — that is the
     * honest quantity and it is measured against the interval below it — while its height is
     * squashed to 0.45 of that purely so the finished concertina is wider than it is tall and fits
     * a 640x480 eye. Say it out loud rather than letting a viewer read the height as a value.
     */
    private fun hop(
        line: FloatArray, at: Int, j: Int, upto: Float, c: FloatArray, alpha: Float, segs: Int
    ): Int {
        val v0 = S[j - 1]
        val v1 = S[j]
        val half = (v1 - v0) * 0.5f
        SceneParts.at(g, sOf((v0 + v1) * 0.5f), 0f, 0f, o)
        // The sweep runs from S[j-1] at angle 0 to S[j] at angle pi, so a partial arc grows out of
        // the foot the walker is standing on. The bulge takes the sign of the step, so forward
        // swings arch above the line and backward swings dip below it with no branch.
        SceneParts.vec(g, -half * SPAN, 0f, 0f, du)
        SceneParts.vec(g, 0f, HOP * half * SPAN, 0f, dv)
        return MathMesh.arc(
            line, at, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            1f, 0f, PI * upto, segs, c[0], c[1], c[2], alpha
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build()
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val tn = terms(kit)
        val k = stepsDone(kit)
        val frac = swingFrac(kit)
        val e = SceneParts.ease(frac)
        val done = k >= tn
        val segs = if (kit.quality == 0) 10 else 6

        // --- the interval being paced ---------------------------------------------------------
        // Not an axis: there is no arrow, because nothing here runs off to infinity except the
        // number of terms. It is a floor to walk on, from 0 to 1, and the walk never leaves it.
        SceneParts.at(g, sOf(0f), 0f, 0f, o)
        SceneParts.at(g, sOf(1f), 0f, 0f, du)
        v = MathMesh.segment(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.75f
        )
        for (end in 0..1) {
            SceneParts.at(g, sOf(end.toFloat()), -0.07f, 0f, o)
            SceneParts.at(g, sOf(end.toFloat()), 0.07f, 0f, du)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.8f
            )
        }

        // --- the concertina: every swing already taken, left standing ---------------------------
        // Forward swings amber, back swings cool, so the alternation is legible even in a still
        // frame. They dim slightly once past, which is what makes the live swing findable.
        for (j in 1..k) {
            val col = if (j % 2 == 1) SceneParts.WORK else SceneParts.COOL
            v = hop(line, v, j, 1f, col, 0.62f, segs)
        }
        if (!done && e > 0.01f) {
            val j = k + 1
            val col = if (j % 2 == 1) SceneParts.WORK else SceneParts.COOL
            v = hop(line, v, j, e, col, 1f, segs)
        }

        // --- the error bar, which is also the next step -----------------------------------------
        // s_n and s_{n+1} straddle the limit — that is the whole content of the alternating series
        // test — so one band does both jobs: it is the reach of the step not yet taken, and it is
        // the interval the answer is known to be inside. Its width is a_{n+1} exactly.
        val lo = kotlin.math.min(S[k], S[k + 1])
        val hi = kotlin.math.max(S[k], S[k + 1])
        SceneParts.at(g, sOf(lo), -BAND, 0f, o)
        SceneParts.vec(g, (hi - lo) * SPAN, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAND * 2f, 0f, dv)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.ADDED, 1f
        )

        // --- the limit, marked before the walk gets there ----------------------------------------
        // A hairline through the whole figure rather than a tick on the line: it has to be visible
        // THROUGH the concertina, so that the closing is seen as closing on something.
        SceneParts.at(g, sOf(LN2), -0.24f, 0f, o)
        SceneParts.at(g, sOf(LN2), 0.46f, 0f, du)
        v = MathMesh.dashed(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], 7,
            SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.55f
        )

        // --- the reordered destination ------------------------------------------------------------
        // Only at rest, and only once the walk has visibly landed: the same terms, two positives to
        // every negative, converge to (3/2)ln 2 instead. The dashed run lies along the interval
        // itself and carries straight off its right-hand end, because that is where it goes.
        val tell = done && kit.quality < 2
        if (tell) {
            SceneParts.at(g, sOf(LN2), 0f, 0f, o)
            SceneParts.at(g, sOf(REORDER), 0f, 0f, du)
            v = MathMesh.dashed(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2], 6,
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], 0.75f
            )
        }

        // --- the collar: the remainder, as a radius ------------------------------------------------
        // The tour's convention is that the passage radius IS how much is unaccounted for. A scene
        // cannot set that, so this ring stands in for it: its radius is the current error bound
        // scaled to the passage, and it tightens step by step round the answer. Set back along the
        // rail so it is a throat you approach rather than a hoop about your head.
        if (kit.quality < 2) {
            val rr = (kit.radius(i.toFloat()) * 0.62f / (k + 1)).coerceAtLeast(0.16f)
            val cx = f[0] + f[3] * COLLAR_BACK
            val cy = f[1] + f[4] * COLLAR_BACK
            val cz = f[2] + f[5] * COLLAR_BACK
            v = MathMesh.arc(
                line, v, cx, cy, cz, f[6], f[7], f[8], f[9], f[10], f[11],
                rr, 0f, PI * 2f, if (kit.quality == 0) 24 else 14,
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2],
                0.40f + 0.35f * kit.beat
            )
        }

        kit.flushLines(v, 2.3f)
        kit.flushTris(tv[0])

        // --- the walker ------------------------------------------------------------------------------
        // It rides the hop rather than sliding along the floor. The swing is the term; watching the
        // walker climb over a whole unit and then over a half is the sense in which this is a walk
        // and not a table of numbers.
        if (done) {
            SceneParts.at(g, sOf(S[tn]), 0f, 0f, o)
        } else {
            val j = k + 1
            val half = (S[j] - S[j - 1]) * 0.5f
            val th = PI * e
            SceneParts.at(
                g, sOf((S[j - 1] + S[j]) * 0.5f) - half * SPAN * cos(th),
                HOP * half * SPAN * sin(th), 0f, o
            )
        }
        kit.ball(
            o[0], o[1], o[2], 0.075f, 0.075f, 0.075f, SceneParts.HOT, SceneParts.WORK, 1f,
            0f, 0f, 1f, 0f, 0f, 1.3f
        )

        // The limit itself, once the band has closed enough that a bead does not swamp it.
        if (k >= 2) {
            SceneParts.at(g, sOf(LN2), 0f, 0f, o)
            kit.ball(
                o[0], o[1], o[2], 0.045f, 0.045f, 0.045f, SceneParts.HOT, SceneParts.ADDED, 0.9f,
                0f, 0f, 1f, 0f, 0f, 1.8f
            )
        }
        if (tell) {
            SceneParts.at(g, sOf(REORDER), 0f, 0f, o)
            kit.ball(
                o[0], o[1], o[2], 0.05f, 0.05f, 0.05f, SceneParts.TAKEN, SceneParts.TAKEN, 0.8f,
                0f, 0f, 1f, 0f, 0f, 0.9f
            )
        }

        // --- notation ------------------------------------------------------------------------------
        // Everything sits BESIDE the figure or along its floor. The HUD owns the top of the eye and
        // the caption box owns the bottom, so a label placed over the figure is a label nobody reads.
        val gl = 0.16f

        // The running sum, out to the right at the height of the walk line, arriving one term at a
        // time and only naming ln 2 once the picture has finished making it true.
        if (k >= 1) {
            val idx = if (done) 5 else (k - 1).coerceAtMost(4)
            SceneParts.at(g, sOf(1f) + 0.17f, 0f, 0f, o)
            kit.text(
                CLAIM[idx], o[0], o[1], o[2], gl, SceneParts.HOT, 1f,
                GlyphBoard.Style.MATH, 1.15f, anchor = -0.5f
            )
        }

        // The limit's name, flagged off the top of its own hairline and clear of the first arch.
        if (kit.quality < 2) {
            SceneParts.at(g, sOf(LN2) + 0.07f, 0.50f, 0f, o)
            kit.text(
                "ln 2", o[0], o[1], o[2], gl, SceneParts.HOT, 0.95f,
                GlyphBoard.Style.MATH, 1f, anchor = -0.5f
            )
        }

        // Tick labels and the term being taken are secondary: full detail only.
        if (kit.quality == 0) {
            SceneParts.at(g, sOf(0f), -0.20f, 0f, o)
            kit.text("0", o[0], o[1], o[2], gl * 0.9f, SceneParts.CHALK, 0.8f, GlyphBoard.Style.SMALL)
            SceneParts.at(g, sOf(1f), -0.20f, 0f, o)
            kit.text("1", o[0], o[1], o[2], gl * 0.9f, SceneParts.CHALK, 0.8f, GlyphBoard.Style.SMALL)

            if (!done) {
                val j = k + 1
                val col = if (j % 2 == 1) SceneParts.WORK else SceneParts.COOL
                val half = (S[j] - S[j - 1]) * 0.5f
                // Riding just outside the crest of the swing it names, on whichever side the swing
                // is bulging, so the name never lands inside its own arch.
                SceneParts.at(
                    g, sOf((S[j - 1] + S[j]) * 0.5f),
                    HOP * half * SPAN + (if (half > 0f) 0.09f else -0.19f), 0f, o
                )
                kit.text(TERM[j - 1], o[0], o[1], o[2], gl * 0.9f, col, 0.95f, GlyphBoard.Style.PLAIN)
            }

            if (tell) {
                SceneParts.at(g, sOf(REORDER), -0.24f, 0f, o)
                kit.text(
                    "reorder", o[0], o[1], o[2], gl * 0.85f, SceneParts.TAKEN, 0.85f,
                    GlyphBoard.Style.SMALL
                )
            }
        }
    }
}
