package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.sin

/**
 * Stop 1 of Tour IV — THE STAIRCASE. "A sequence converges if it eventually stays inside any tube
 * you name, however thin."
 *
 * Twenty beads run away down the corridor, one per term, at heights a₁, a₂, a₃ … The tolerance is
 * a slab of light around the limit — two horizontal planes at L ± ε — and the whole stop is one
 * question asked three times: name a band, and I will find you a ring beyond which every bead is
 * inside it. The band tightens, the collar that marks that ring slides further down the corridor,
 * and it never fails to exist. Then the same instrument is turned on a sequence that does NOT
 * converge, and the collar is chased away down the passage by beads that keep leaping out.
 *
 * Two decisions are worth defending.
 *
 * FIRST, the stems hang from the LIMIT, not from an axis at zero. The classic textbook plot draws
 * a_n as a height above the x-axis, and it is the wrong picture for this stop: the quantity the
 * definition talks about is |a_n − L|, and here that quantity is the visible length of the stem.
 * You watch the stems shorten until the band swallows them. Nothing else in the figure has to be
 * measured for the claim to be legible.
 *
 * SECOND, the sequence is a_n = L + (−1)^(n+1)/n, in units where the first term stands one unit
 * off the limit. It alternates, so beads fall on both sides of the band and "eventually inside"
 * is a real condition rather than a monotone slide; and its envelope is exactly 1/n, so the three
 * tolerances 0.30, 0.15, 0.08 have collars at ring 4, ring 7 and ring 13 — numbers a viewer can
 * check by counting beads. A prettier sequence with an uncheckable threshold would be worse.
 *
 * The failing sequence leaps out at n = 8, 13 and 18: it looks convergent between escapes, which
 * is the honest difficulty, and each escape drives the collar further off down the passage instead
 * of merely flashing. Note what the picture cannot do — twenty beads can show three escapes, never
 * "forever". That last step is the crew's word, not the geometry's, and the readout says only what
 * is actually on screen: OUT AGAIN AT n = 18.
 *
 * The figure hangs BELOW the rail. Partly because the telemetry owns the top of the eye, and
 * partly because two horizontal planes seen from dead level are two invisible slivers — the band
 * only reads as a slab when you are looking down onto it.
 */
object SceneStaircase : MathScene {

    /** The run goes away down the corridor, so it wants to be in frame well before the stop. */
    override val reach = 1.5f
    override val deep = 0.5f

    // ------------------------------------------------------------- the figure

    private const val N_MAX = 20
    private const val PERIOD = 30f

    /** Off to one side: a run laid along the rail centre is one the craft threads rather than reads. */
    private const val SIDE = -1.15f
    private const val WIDE = 0.80f          // half-width of the tolerance slab
    private const val A0 = -0.9f            // where bead 1 sits along the rail, just behind the stop
    private const val SPAN = 7.2f           // world units from bead 1 to bead 20
    private const val DA = SPAN / (N_MAX - 1)

    /**
     * The plot's scale, as fractions of the roof height at this stop. The tour's roof curve is the
     * one vertical measure the corridor already has, so the figure is cut from it rather than from
     * a number picked out of the air: the limit line sits a quarter of the roof BELOW the rail and
     * the first term stands a third of the roof clear of it.
     */
    private const val LIM_FRAC = -0.25f
    private const val AMP_FRAC = 0.35f

    /** How far out an escaping term leaps, in figure units. Larger than every tolerance, on purpose. */
    private const val ESC = 0.55f
    /**
     * Which terms leap back out in the failing half. Each one is chosen to sit BEYOND the collar
     * the previous state had earned — 8, then 13, then 18 — so every escape visibly drives the
     * collar further down the corridor: ring 7, then 9, then 14, then 19. An escape that landed
     * behind the collar would flash prettily and prove nothing.
     */
    private val ESCAPE_AT = intArrayOf(8, 13, 18)

    /** The three tolerances, in figure units. Their collars land on rings 4, 7 and 13. */
    private val EPS = floatArrayOf(0.30f, 0.15f, 0.08f)

    private const val BEAD = 0.062f         // half-diagonal of a bead's billboard
    private const val BEAD_OUT = 0.05f      // pushed toward the eye so it never z-fights the slab
    private const val TAU = 6.2831855f

    // ------------------------------------------------------------- scratch

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val q = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    /** Each term's signed offset from the limit, and how far each is currently escaped. */
    private val off = FloatArray(N_MAX + 2)
    private val esc = FloatArray(N_MAX + 2)

    /** Built once. A label assembled in draw() would allocate and defeat the glyph cache. */
    private val RING = Array(N_MAX + 3) { "n = $it" }
    private val NUM = Array(N_MAX + 1) { it.toString() }

    // ------------------------------------------------------------- the cycle

    /**
     * The tolerance now. Wide, then half, then half again, then back to the middle for the failing
     * sequence, then open again before the loop wraps — an additive chain of eased steps, which is
     * how a scene gets five beats out of one clock without any state of its own. It is continuous
     * across the wrap, so a viewer arriving at any moment sees a band that is simply some width.
     */
    private fun epsAt(c: Float): Float =
        EPS[0] +
            (EPS[1] - EPS[0]) * SceneParts.step(c, 0.16f, 0.10f) +
            (EPS[2] - EPS[1]) * SceneParts.step(c, 0.36f, 0.10f) +
            (EPS[1] - EPS[2]) * SceneParts.step(c, 0.60f, 0.06f) +
            (EPS[0] - EPS[1]) * SceneParts.step(c, 0.95f, 0.04f)

    /** The terms, and which of them have currently jumped. Filled by both readout and draw. */
    private fun fillTerms(c: Float) {
        for (m in 0..N_MAX + 1) esc[m] = 0f
        // The three escapes land one after another, each further down the corridor than the last,
        // and all three retract together before the loop closes.
        for (j in ESCAPE_AT.indices) {
            esc[ESCAPE_AT[j]] =
                (SceneParts.step(c, 0.68f + j * 0.08f, 0.04f) - SceneParts.step(c, 0.93f, 0.04f))
                    .coerceIn(0f, 1f)
        }
        for (m in 1..N_MAX + 1) {
            val s = if (m and 1 == 1) 1f else -1f
            val base = 1f / m
            off[m] = s * (base + (ESC - base) * esc[m])
        }
    }

    /** The last term still outside the band, or 0 if none is. This is the definition, run backwards. */
    private fun lastOutside(eps: Float): Int {
        var k = 0
        for (m in 1..N_MAX) if (abs(off[m]) > eps) k = m
        return k
    }

    /**
     * Where the collar hangs, as a real-valued ring index: the place between the last violator and
     * its successor where the sequence's envelope crosses ε. Interpolating rather than snapping to
     * the integer is what makes the collar SLIDE as the band tightens; the number it names still
     * steps, because a ring is a whole number and the claim is about a ring.
     */
    private fun collarPos(k: Int, eps: Float): Float {
        if (k == 0) return 1f
        if (k >= N_MAX) return N_MAX + 0.6f
        val e0 = abs(off[k])
        val e1 = abs(off[k + 1])
        val t = if (e0 > e1 + 1e-5f) ((e0 - eps) / (e0 - e1)).coerceIn(0f, 1f) else 1f
        return k + t
    }

    private fun aOf(x: Float): Float = A0 + (x - 1f) * DA

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        fillTerms(c)
        val eps = epsAt(c)
        // While something is out, the honest reading is the ring it went out at — not a verdict
        // about all the terms we are not drawing.
        var out = 0
        for (j in ESCAPE_AT.indices) if (esc[ESCAPE_AT[j]] > 0.5f) out = ESCAPE_AT[j]
        return if (out > 0) {
            "ε %.2f   OUT AGAIN AT n = %d".format(java.util.Locale.US, eps, out)
        } else {
            "ε %.2f   INSIDE FROM n = %d".format(java.util.Locale.US, eps, lastOutside(eps) + 1)
        }
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        val roof = if (kit.hasTrace) kit.traceHeight(at) else 1.5f
        val amp = AMP_FRAC * roof
        val lim = LIM_FRAC * roof

        // The stage's origin IS the limit line, so every height in this scene is already an error.
        // The run is laid along the stop's own forward axis rather than following the rail node by
        // node: the rail bends about four tenths of a unit over seven, which at this side offset
        // leaves the whole figure inside half the passage radius, and buying that last accuracy
        // would cost twenty frame lookups a frame.
        SceneParts.stage(kit, at, SIDE, lim, f, g)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        fillTerms(c)
        val eps = epsAt(c)
        val hi = eps * amp
        val bad = esc[ESCAPE_AT[0]]
        val k = lastOutside(eps)
        val ring = k + 1
        val cpos = collarPos(k, eps)
        val ca = aOf(cpos)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0
        val ql = kit.quality

        val cool = SceneParts.COOL
        val hot = SceneParts.HOT
        val chalk = SceneParts.CHALK
        val safe = if (bad > 0.4f) SceneParts.TAKEN else SceneParts.ADDED

        // --- the tolerance: two horizontal planes, closing --------------------------------------
        // Drawn as faces rather than as a wire box because the point of the band is that it is a
        // REGION, and because from a camera above them the two faces separate in parallax and the
        // slab reads as having a thickness that visibly shrinks.
        if (ql < 2) {
            SceneParts.vec(g, 2f * WIDE, 0f, 0f, du)
            SceneParts.vec(g, 0f, 0f, SPAN, dv)
            SceneParts.at(g, -WIDE, hi, A0, o)
            tv[0] = MathMesh.quad(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], cool[0], cool[1], cool[2], 0.13f)
            SceneParts.at(g, -WIDE, -hi, A0, o)
            tv[0] = MathMesh.quad(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], cool[0], cool[1], cool[2], 0.10f)
        }

        // The four long edges. These are what carries the band down the corridor once the faces go
        // edge-on with distance, and they are the last thing dropped.
        for (e in 0 until 4) {
            val s = if (e < 2) WIDE else -WIDE
            val u = if (e and 1 == 0) hi else -hi
            SceneParts.at(g, s, u, A0, o)
            SceneParts.at(g, s, u, A0 + SPAN, q)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], q[0], q[1], q[2],
                cool[0], cool[1], cool[2], if (e < 2) 0.85f else 0.45f)
        }

        // --- the limit itself, and a ruling to count the rings by --------------------------------
        SceneParts.at(g, 0f, 0f, A0 - 0.25f, o)
        SceneParts.at(g, 0f, 0f, A0 + SPAN + 0.25f, q)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], q[0], q[1], q[2],
            hot[0], hot[1], hot[2], 0.95f)

        if (ql < 2) {
            for (m in 1..N_MAX) {
                val w = if (m % 5 == 0) 0.16f else 0.06f
                val a = A0 + (m - 1) * DA
                SceneParts.at(g, -w, 0f, a, o)
                SceneParts.at(g, w, 0f, a, q)
                v = MathMesh.segment(line, v, o[0], o[1], o[2], q[0], q[1], q[2],
                    chalk[0], chalk[1], chalk[2], if (m % 5 == 0) 0.75f else 0.40f)
            }
        }

        // --- the terms ---------------------------------------------------------------------------
        // Twenty stems and twenty beads, and NOT halved at quality 1. Halving this loop would
        // delete the landmark to save one line and one triangle flush between the lot of them;
        // what goes under load is the slab, the ruling and the numbering, which cost draw calls.
        val flash = 0.55f + 0.45f * sin(kit.seconds * 7f)
        for (m in 1..N_MAX) {
            val u = off[m] * amp
            val a = A0 + (m - 1) * DA
            val leaping = esc[m] > 0.02f
            val inside = abs(off[m]) <= eps
            val col = when {
                leaping -> SceneParts.TAKEN
                inside -> SceneParts.ADDED
                else -> SceneParts.WORK
            }
            val al = if (leaping) 0.55f + 0.45f * flash * esc[m] else 0.95f

            // The stem: its length is |a_n − L|, which is the only quantity the definition names.
            SceneParts.at(g, 0f, 0f, a, o)
            SceneParts.at(g, 0f, u, a, q)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], q[0], q[1], q[2],
                col[0], col[1], col[2], al * 0.35f, al * 0.85f)

            // The bead: a camera-facing diamond in the triangle buffer. Twenty lit spheres would be
            // twenty draw calls and the stop has a budget of about thirty for everything.
            SceneParts.at(g, BEAD_OUT, u, a, q)
            val h = BEAD * (1f + 0.5f * esc[m])
            tv[0] = MathMesh.quad(
                tri, tv[0],
                q[0] - kit.camRightX * h, q[1] - kit.camRightY * h, q[2] - kit.camRightZ * h,
                (kit.camRightX + kit.camUpX) * h, (kit.camRightY + kit.camUpY) * h,
                (kit.camRightZ + kit.camUpZ) * h,
                (kit.camRightX - kit.camUpX) * h, (kit.camRightY - kit.camUpY) * h,
                (kit.camRightZ - kit.camUpZ) * h,
                col[0], col[1], col[2], al
            )
        }

        // --- the collar ---------------------------------------------------------------------------
        // A ring of THIS corridor, at the rail, at the place where containment begins. Its radius is
        // taken from the stop's own passage rather than from the rail somewhere down the run: the
        // kit does not say how many world units make a node unit, and a scene should not guess.
        val segs = if (ql == 0) 24 else if (ql == 1) 16 else 12
        SceneParts.at(g, -SIDE, -lim, ca, o)
        SceneParts.vec(g, 1f, 0f, 0f, du)
        SceneParts.vec(g, 0f, 1f, 0f, dv)
        v = MathMesh.arc(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            kit.radius(at) * 0.84f, 0f, TAU, segs,
            safe[0], safe[1], safe[2],
            (0.60f + 0.30f * kit.beat) * (if (bad > 0.4f) flash else 1f)
        )

        kit.flushLines(v, 2.3f)
        kit.flushTris(tv[0])

        // The first bead inside the band, as a real lit object: the one the claim is about.
        if (ring in 1..N_MAX && bad < 0.4f) {
            SceneParts.at(g, BEAD_OUT, off[ring] * amp, A0 + (ring - 1) * DA, o)
            kit.ball(o[0], o[1], o[2], 0.075f, 0.075f, 0.075f, SceneParts.ADDED, hot, 1f,
                0f, 0f, 1f, 0f, 0f, 1.6f)
        }
        // And the term that has just leapt back out, when one has.
        if (ql < 2) {
            var out = 0
            for (j in ESCAPE_AT.indices) if (esc[ESCAPE_AT[j]] > 0.35f) out = ESCAPE_AT[j]
            if (out > 0) {
                SceneParts.at(g, BEAD_OUT, off[out] * amp, A0 + (out - 1) * DA, o)
                kit.ball(o[0], o[1], o[2], 0.085f, 0.085f, 0.085f, SceneParts.TAKEN, hot,
                    0.85f, 0f, 0f, 1f, 0f, 0f, 2.2f * flash)
            }
        }

        // --- notation ------------------------------------------------------------------------------
        // Everything sits beside the run or at its two ends. Nothing is stacked above the figure:
        // the telemetry owns the top of the eye and the caption box the bottom.
        val gl = 0.19f

        // The limit is named where the beads have collapsed onto it, at the far end.
        SceneParts.at(g, 0f, 0f, A0 + SPAN + 0.34f, o)
        kit.text("L", o[0], o[1], o[2], gl * 1.1f, hot, 1f, GlyphBoard.Style.MATH, 1.2f, anchor = -0.5f)

        // The collar names its ring, held clear of the slab on the side the craft passes.
        SceneParts.at(g, WIDE - 0.06f, hi + 0.17f, ca, o)
        kit.text(RING[ring.coerceIn(0, N_MAX + 2)], o[0], o[1], o[2], gl, safe, 1f,
            GlyphBoard.Style.PLAIN, 1.2f, anchor = -0.5f)

        if (ql == 0) {
            // The two band labels are pushed a fixed distance clear of the edges rather than sat on
            // them, so they stay apart however thin the band gets — which it does, to a tenth.
            SceneParts.at(g, 0f, hi + 0.13f, A0 - 0.30f, o)
            kit.text("ε", o[0], o[1], o[2], gl, cool, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = 0.5f)
            SceneParts.at(g, 0f, -hi - 0.13f, A0 - 0.30f, o)
            kit.text("ε", o[0], o[1], o[2], gl, cool, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = 0.5f)

            // The claim, at the head of the run, naming exactly what the beads and the slab do.
            SceneParts.at(g, 0f, hi + 0.36f, A0 - 0.30f, o)
            kit.text("|a_n − L| < ε", o[0], o[1], o[2], gl * 0.95f, hot, 0.95f,
                GlyphBoard.Style.MATH, 1.1f, anchor = 0.5f)

            // Every fifth ring numbered, under the slab, so the collar's ring can be counted to.
            for (m in 5..N_MAX step 5) {
                SceneParts.at(g, WIDE + 0.10f, -hi - 0.15f, A0 + (m - 1) * DA, o)
                kit.text(NUM[m], o[0], o[1], o[2], gl * 0.8f, chalk, 0.7f,
                    GlyphBoard.Style.SMALL, 1f, anchor = -0.5f)
            }
        }
    }
}
