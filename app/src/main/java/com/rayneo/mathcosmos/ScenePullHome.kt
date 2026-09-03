package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR VI, stop 10 — THE PULL HOME. "Some answers pull you in and some throw you out, and the
 * field tells you which before you solve anything."
 *
 * Two horizontal lines run down the corridor: two values of y at which y' is nought, so a solution
 * that starts on one stays on it forever. That is all an equilibrium is. The question the stop
 * exists to answer is what happens to a solution that starts NEAR one, and the whole apparatus —
 * the slope marks, the family of curves, the two probes and their measuring rungs — is built to
 * make the answer something a viewer watches rather than something they are told.
 *
 * THE EQUATION IS AUTONOMOUS AND THAT IS WHY IT FITS THE CORRIDOR. y' = K(y − a)(y − b) does not
 * mention t, so its slope field is constant along every horizontal, and the corridor's own
 * along-axis can BE t — the same convention THE SLOPE FIELD sets one stop astern and THE SPRING
 * AND THE CIRCLE takes over one stop ahead. Fly forward and time passes; the figure is a graph you
 * travel along rather than one you look at. The two equilibria then run parallel to the rail,
 * which is the only staging that lets a viewer be nudged off one.
 *
 * THE CRAFT CANNOT LEAVE THE RAIL, so it is not the thing that gets nudged. The design has the
 * ship pushed off each line in turn and the hull's own low-passed sway doing the teaching; that
 * sway is the renderer's, driven by the field, and no scene is allowed to touch it. What this file
 * supplies is the geometry and the measured numbers, and it does it with two probes released at
 * the stop, one from each line, displaced by exactly the same δ. The crew narrates the feeling.
 * The picture and the readout have to carry the argument on their own, so they are built to.
 *
 * THE SAME NUDGE TWICE, AND THAT IS THE POINT. Both probes start δ = 0.22 off their line. Linearise
 * and the offsets go as e^{λt} with λ = ∓K(b − a) — the SAME number, once negative and once
 * positive. One probe comes home to a thousandth of where it started and the other is off the top
 * of the figure inside a couple of units of t. The only difference between the two experiments is
 * the sign of one number, and a viewer who leaves with nothing else should leave with that.
 *
 * THE COLOUR IS A THEOREM. A solution of a first-order equation cannot cross an equilibrium — two
 * curves through one point would be one curve — so which side of the upper line a solution starts
 * on is fixed for all time. Every curve is therefore tinted once, at birth, by its seed: mint if
 * it is going to come home, red if it is going to be thrown out. The slope marks are tinted by the
 * same test, so the upper line is drawn as the place the colour changes. That is the basin
 * boundary, and naming it "the separatrix" would add a word and no information.
 *
 * THE FLEEING SOLUTION GENUINELY BLOWS UP. A quadratic right-hand side runs to infinity in finite
 * time, so the upper probe does not wander off — it leaves. The curve stops at the top of the band
 * and a chevron is left where it went, and that is not the renderer running out of room; it is
 * what the equation does. Worth saying, because a curve that stops usually IS a rendering limit.
 *
 * THE THIRD DIMENSION IS DOING WORK. Each equilibrium is drawn as three parallel rails with cross
 * ties rather than one line, so it reads in stereo as a horizontal SHEET. That is not decoration:
 * the equation depends on y and on nothing else, so an equilibrium really is a whole level, and
 * saying so in depth costs six segments. The solution curves and the slope marks stay in the one
 * plane, because a lattice repeated three times over is noise and not depth.
 *
 * THE TOUR'S FIELD IS NOT THIS FIELD, and [SceneKit.fieldAt] is deliberately not called. The
 * corridor's vector field is a swirl about the rail with a drift along it; the slope field here
 * belongs to a one-dimensional differential equation and lives in a plane hung beside the rail.
 * They are two different objects that both get called "a field", and blending them would make it
 * look as though the answer depended on which corridor you happened to be flying down. The ambient
 * scene goes on drawing the real one all around this, as it does all ride.
 *
 * SIZE. Tour VI's wall alpha is 0.15 and the tube is a guide-rail, so the figure is ten units of
 * corridor long and runs a good way past the passage radius at both the top of the band and
 * outboard. It is meant to. A slope field that stopped at the wall would be a diagram of an
 * equation rather than the country the equation describes.
 *
 * BUDGET. One flushLines of about eight hundred vertices, one flushTris of twelve, two lit beads
 * and four labels: nine draw calls.
 */
object ScenePullHome : MathScene {

    /** The two lines should already be running past before the craft is alongside them. */
    override val reach = 1.5f

    /** The figure reaches six units downrange against sixteen-unit node spacing. */
    override val deep = 0.45f

    // ---- the equation ------------------------------------------------------------------------
    // y' = K (y − LOW)(y − HIGH). Below LOW the product is positive and a solution climbs; between
    // the two it is negative and a solution falls; above HIGH it is positive again and a solution
    // runs. So LOW pulls from both sides and HIGH pushes from both, which is the picture the stop
    // wants, and the two linearised rates are ∓K(HIGH − LOW) = ∓0.9 — equal and opposite by
    // construction, so the comparison the crew makes is exact and not merely suggestive.
    private const val LOW = -1.20f
    private const val HIGH = 1.20f
    private const val K = 0.375f
    private const val DELTA = 0.22f

    // ---- the figure --------------------------------------------------------------------------
    // The plane hangs to port. The craft flies alongside its middle rather than through it, which
    // is the whole lesson of every flat figure in this app: one centred on the rail is one you fly
    // INTO, and at the closest point of the pass only a corner is in frame.
    private const val SIDE = -2.40f
    private const val SPREAD = 0.70f       // how far the equilibrium sheets reach either side of it
    private const val T0 = -4.20f          // astern end of the figure, in rail units
    private const val T1 = 6.20f           // and its downrange end
    private const val SPAN = T1 - T0
    private const val YMIN = -2.50f
    private const val YMAX = 2.90f
    /** Where along the figure the probes are released: the stop itself, t = 0. */
    private const val HOME_F = (0f - T0) / SPAN

    // Notation sits OUTBOARD of the figure, past it from the eye. The corridor's inboard side is
    // full of the ambient field's arrows, and a label lost among those is a label nobody reads.
    private const val NOTE_S = SIDE - 0.85f

    // ---- the clock ---------------------------------------------------------------------------
    // Lines, then the slope marks laid down astern to ahead, then time sweeping the curves out of
    // them, then a rest of about six seconds with the finished picture standing: one probe seated
    // on the lower line with nothing left to measure, one long gone off the top. That rest is what
    // a viewer arriving late has to be able to simply look at.
    private const val PERIOD = 26f
    private const val LINES_AT = 0.01f
    private const val LINES_LEN = 0.05f
    private const val LAY_AT = 0.06f
    private const val LAY_LEN = 0.14f
    private const val GROW_AT = 0.22f
    private const val GROW_LEN = 0.52f
    /** The whole scene dims out over the last of the cycle so the reset happens off camera. */
    private const val CLEAR_AT = 0.965f

    // ---- the walk ----------------------------------------------------------------------------
    private const val NC = 5
    private const val STEPS = 52
    private const val STEPS_LOW = 30
    private const val STEPS_MIN = 22

    /**
     * Where each solution is seeded, and how far along the figure it starts.
     *
     * 0 and 1 are the probes: the same displacement off each line, released together at the stop.
     * 2 is the mirror of probe 1 — the same δ on the OTHER side of the upper line, which comes all
     * the way home. Those three are the argument and the governor never drops them. 3 and 4 are
     * the family: one climbing to the lower line from underneath, one falling to it from between,
     * both running the whole length of the figure so the picture is already populated when the
     * craft arrives.
     */
    private val SEED_Y = floatArrayOf(LOW + DELTA, HIGH + DELTA, HIGH - DELTA, -2.40f, 0.55f)
    private val SEED_F = floatArrayOf(HOME_F, HOME_F, HOME_F, 0f, 0f)

    // ---- the lattice -------------------------------------------------------------------------
    private const val NT = 11
    private const val NY = 9
    private const val MARK = 0.44f

    // ---- weights -----------------------------------------------------------------------------
    private const val LIT_HERO = 0.80f
    private const val LIT_FAMILY = 0.40f
    private const val BEAD = 0.075f

    // ---- scratch -----------------------------------------------------------------------------
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val ys = FloatArray(NC * (STEPS + 1))
    private val startAt = IntArray(NC)
    private val endAt = IntArray(NC)

    // ---- what was measured, cached for the HUD -------------------------------------------------
    // The number the HUD reads is the number the last frame actually drew, so the world and the
    // readout can never disagree about how far off the line a probe is.
    private var offHome = DELTA
    private var offAway = DELTA
    private var gone = false

    /** Three decimal places, without allocating a formatter. The small offset needs all three. */
    private fun fmt3(v: Float): String {
        val m = (abs(v) * 1000f + 0.5f).toInt()
        val frac = m % 1000
        val tail = if (frac < 10) "00$frac" else if (frac < 100) "0$frac" else "$frac"
        return (if (v < 0f) "-" else "") + (m / 1000) + "." + tail
    }

    /** The right-hand side. One line, and every other number in this file follows from it. */
    private fun slope(y: Float): Float = K * (y - LOW) * (y - HIGH)

    /**
     * The displacement of each probe from its own line, which is what this stop measures and the
     * one number that has to be READ rather than looked at. It falls from δ to about a thousandth
     * of δ on the stable line over the length of the figure, which is the stop's rung exactly.
     */
    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val grow = SceneParts.step(c, GROW_AT, GROW_LEN)
        if (grow < HOME_F) return "NUDGE δ " + fmt3(DELTA) + "   BOTH LINES"
        return "HOME δ " + fmt3(offHome) + "   AWAY " + (if (gone) "GONE" else "δ " + fmt3(offAway))
    }

    /**
     * Every solution, walked once per frame over the whole figure whether it is drawn yet or not.
     *
     * Walking the lot and revealing them afterwards keeps the geometry still while the front moves
     * over it; a curve integrated only as far as it is shown wriggles as it grows, and the wriggle
     * is the first thing a viewer notices and the last thing this stop wants them looking at.
     *
     * A midpoint step, not Euler. The rate near the upper line is +0.9 per unit of t and the step
     * is a fifth of a unit, so plain Euler would understate the flight by a few per cent — small,
     * but the whole claim here is a comparison between two exponentials and it is cheap to make it
     * an honest one.
     */
    private fun walk(steps: Int, dt: Float) {
        for (c in 0 until NC) {
            val o = c * (STEPS + 1)
            val s0 = (SEED_F[c] * steps + 0.5f).toInt().coerceIn(0, steps)
            var y = SEED_Y[c]
            for (k in 0..s0) ys[o + k] = y
            var end = steps
            var k = s0 + 1
            while (k <= steps) {
                val mid = y + 0.5f * dt * slope(y)
                y += dt * slope(mid)
                var out = false
                if (y >= YMAX) { y = YMAX; out = true } else if (y <= YMIN) { y = YMIN; out = true }
                ys[o + k] = y
                if (out) { end = k; break }
                k++
            }
            // Everything past the end holds the last value: a curve that has left the band is not
            // drawn beyond it, and nothing may read geometry left over from a hotter frame.
            for (j in end + 1..STEPS) ys[o + j] = y
            startAt[c] = s0
            endAt[c] = end
        }
    }

    /** The height of solution [c] at fractional step [head]. */
    private fun yAt(c: Int, head: Float, steps: Int): Float {
        val o = c * (STEPS + 1)
        val hp = head.coerceIn(0f, steps.toFloat())
        var idx = hp.toInt()
        if (idx >= steps) idx = steps - 1
        val fr = hp - idx
        return ys[o + idx] + (ys[o + idx + 1] - ys[o + idx]) * fr
    }

    /** A point in the figure: [s] outboard of the rail, [u] high, [a] along the rail. */
    private fun p(s: Float, u: Float, a: Float, out: FloatArray) = SceneParts.at(g, s, u, a, out)

    /**
     * One equilibrium, as three parallel rails with cross ties — a level, not a line. See the
     * header: the equation depends on y alone, so the whole horizontal sheet is equilibrium and
     * saying so in depth is the one thing a stereoscopic display can do that a textbook cannot.
     */
    private fun sheet(
        line: FloatArray, v0: Int, u: Float, col: FloatArray, alpha: Float, ties: Boolean
    ): Int {
        var v = v0
        for (r in -1..1) {
            val s = SIDE + SPREAD * r
            p(s, u, T0, pa)
            p(s, u, T1, pb)
            val a = if (r == 0) alpha else alpha * 0.45f
            v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                col[0], col[1], col[2], a)
        }
        if (!ties) return v
        for (i in 0..4) {
            val a = T0 + SPAN * i / 4f
            p(SIDE - SPREAD, u, a, pa)
            p(SIDE + SPREAD, u, a, pb)
            v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                col[0], col[1], col[2], alpha * 0.35f)
        }
        return v
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val q = kit.quality
        SceneParts.stage(kit, i.toFloat(), 0f, 0f, f, g)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val dim = if (c > CLEAR_AT) 1f - (c - CLEAR_AT) / (1f - CLEAR_AT) else 1f
        if (dim <= 0.01f) return

        val steps = if (q == 0) STEPS else if (q == 1) STEPS_LOW else STEPS_MIN
        val dt = SPAN / steps
        walk(steps, dt)

        val eq = SceneParts.step(c, LINES_AT, LINES_LEN)
        val lay = SceneParts.step(c, LAY_AT, LAY_LEN)
        val grow = SceneParts.step(c, GROW_AT, GROW_LEN)
        val head = grow * steps

        val line = kit.lineBuf
        var v = 0

        // --- the two basins, as a wash ------------------------------------------------------
        // Everything below the upper line ends on the lower one; everything above it leaves. The
        // wash is faint on purpose — it is a hint at a region, not a wall hung across the corridor
        // — and it is the first thing the governor takes, because the slope marks say the same
        // thing in a way that survives being dim.
        var tv = 0
        if (q < 2 && eq > 0.02f) {
            val tri = kit.triBuf
            SceneParts.vec(g, 0f, 0f, SPAN, du)
            p(SIDE, YMIN, T0, pa)
            SceneParts.vec(g, 0f, HIGH - YMIN, 0f, dv)
            tv = MathMesh.quad(tri, tv, pa[0], pa[1], pa[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.055f * eq * dim)
            p(SIDE, HIGH, T0, pa)
            SceneParts.vec(g, 0f, YMAX - HIGH, 0f, dv)
            tv = MathMesh.quad(tri, tv, pa[0], pa[1], pa[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], 0.070f * eq * dim)
        }

        // --- the slope field ----------------------------------------------------------------
        // Fixed-length marks, direction (1, y') normalised: the standard chalk mark, and the only
        // honest one, because a mark whose LENGTH carried the slope would be unreadable exactly
        // where the slope matters most. They arrive column by column from astern so a viewer sees
        // each one being laid at its own angle rather than a wall of them fading up at once.
        val nt = if (q == 0) NT else if (q == 1) 6 else 5
        val ny = if (q == 0) NY else if (q == 1) 5 else 4
        val half = MARK * 0.5f
        for (ci in 0 until nt) {
            val ca = ((lay * nt) - ci).coerceIn(0f, 1f)
            if (ca <= 0.01f) break
            val a = T0 + SPAN * (ci + 0.5f) / nt
            for (ri in 0 until ny) {
                val u = (YMIN + 0.28f) + (YMAX - YMIN - 0.56f) * ri / (ny - 1f)
                val sl = slope(u)
                val inv = half / sqrt(1f + sl * sl)
                val da = inv
                val du2 = inv * sl
                val col = if (u > HIGH) SceneParts.TAKEN else SceneParts.ADDED
                val w = (0.28f + 0.42f * (abs(sl) / 1.6f).coerceAtMost(1f)) * ca * dim
                p(SIDE, u - du2, a - da, pa)
                p(SIDE, u + du2, a + da, pb)
                v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                    col[0], col[1], col[2], w)
            }
        }

        // --- the two lines --------------------------------------------------------------------
        v = sheet(line, v, LOW, SceneParts.ADDED, 0.92f * eq * dim, q == 0)
        v = sheet(line, v, HIGH, SceneParts.TAKEN, 0.92f * eq * dim, q == 0)

        // --- the solutions ----------------------------------------------------------------------
        // Three at the worst the governor can do, and they are 0, 1 and 2 — the two probes and the
        // mirror of the upper one. Those three ARE the stop; the family is what gets dropped.
        val curves = if (q == 0) NC else if (q == 1) 4 else 3
        for (ci in 0 until curves) {
            val o = ci * (STEPS + 1)
            val col = if (SEED_Y[ci] > HIGH) SceneParts.TAKEN else SceneParts.ADDED
            val lit = if (ci < 3) LIT_HERO else LIT_FAMILY
            var k = startAt[ci]
            while (k < endAt[ci]) {
                val a = (head - k).coerceIn(0f, 1f)
                if (a <= 0.01f) break
                val ta = T0 + SPAN * k / steps
                val tb = T0 + SPAN * (k + 1) / steps
                p(SIDE, ys[o + k], ta, pa)
                p(SIDE, ys[o + k + 1], tb, pb)
                v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                    col[0], col[1], col[2], lit * a * dim)
                k++
            }
        }

        // --- the nudge, left standing as a record ------------------------------------------------
        // Two rungs at t = 0, each exactly δ long, with a caliper tick at each end. They never
        // change, which is the point: the moving rung out ahead is read against them, and a viewer
        // can see that one has shrunk to nothing and the other has run off the top without a single
        // number being quoted. Drawn solid rather than dashed — at a fifth of a unit long a dashed
        // line is three specks, and the codebase's dashes are for things that are not there.
        val released = head >= startAt[0]
        if (released) {
            for (ci in 0 until 2) {
                val base = if (ci == 0) LOW else HIGH
                p(SIDE, base, 0f, pa)
                p(SIDE, SEED_Y[ci], 0f, pb)
                v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                    SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.70f * dim)
                for (e in 0 until 2) {
                    val u = if (e == 0) base else SEED_Y[ci]
                    p(SIDE, u, -0.09f, pa)
                    p(SIDE, u, 0.09f, pb)
                    v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                        SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.70f * dim)
                }
            }
        }

        // --- the two measurements ----------------------------------------------------------------
        val ha = T0 + SPAN * head.coerceIn(0f, steps.toFloat()) / steps
        val yHome = yAt(0, head, steps)
        val yAway = yAt(1, head, steps)
        offHome = abs(yHome - LOW)
        offAway = abs(yAway - HIGH)
        gone = head >= endAt[1] && endAt[1] < steps
        if (released) {
            for (ci in 0 until 2) {
                if (ci == 1 && gone) continue
                val base = if (ci == 0) LOW else HIGH
                val y = if (ci == 0) yHome else yAway
                if (abs(y - base) < 0.02f) continue          // nothing left to measure, so draw none
                p(SIDE, base, ha, pa)
                p(SIDE, y, ha, pb)
                v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                    SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2],
                    (0.55f + 0.30f * sin(kit.seconds * 2.4f)) * dim)
            }
        }

        // --- where the upper probe left ------------------------------------------------------------
        // A chevron at the top edge, at the t it went. Finite-time blow-up, not a clipped curve.
        if (gone) {
            val ea = T0 + SPAN * endAt[1] / steps
            val pulse = (0.50f + 0.40f * sin(kit.seconds * 3f)) * dim
            p(SIDE, YMAX, ea, pa)
            p(SIDE, YMAX - 0.20f, ea - 0.17f, pb)
            v = MathMesh.segment(line, v, pb[0], pb[1], pb[2], pa[0], pa[1], pa[2],
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], pulse)
            p(SIDE, YMAX - 0.20f, ea + 0.17f, pb)
            v = MathMesh.segment(line, v, pb[0], pb[1], pb[2], pa[0], pa[1], pa[2],
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], pulse)
        }

        kit.flushLines(v, 2.4f)
        kit.flushTris(tv)

        // --- the probes ------------------------------------------------------------------------------
        // Two lit beads, and the only two in the scene. They flare as they are let go — the release
        // is the event the crew talks over, and a bead that simply appears is a release nobody saw.
        if (released) {
            val flare = 1f - ((grow - HOME_F) / 0.06f).coerceIn(0f, 1f)
            p(SIDE, yHome, ha, pa)
            kit.ball(pa[0], pa[1], pa[2], BEAD, BEAD, BEAD,
                SceneParts.HOT, SceneParts.ADDED, dim, glow = 1.6f + 3f * flare)
            if (!gone) {
                p(SIDE, yAway, ha, pb)
                kit.ball(pb[0], pb[1], pb[2], BEAD, BEAD, BEAD,
                    SceneParts.HOT, SceneParts.TAKEN, dim, glow = 1.6f + 3f * flare)
            }
        }

        // --- notation ---------------------------------------------------------------------------------
        // Outboard of the figure and at the height of whatever it names, never over or under it:
        // the telemetry block owns the top of the eye and the caption box the bottom, and a label
        // that drifts into either is a label read twice and understood neither time.
        //
        // Everything is set AHEAD of the stop. A figure laid along the rail is read on the approach,
        // because at the closest point of the pass its middle is dead abeam and a label there would
        // need the viewer to look over their own shoulder. The note block is two lines about one
        // anchor rather than two anchors at the same bearing, which at these distances would have
        // projected on top of one another.
        val named = SceneParts.step(c, LINES_AT + 0.03f, 0.08f) * dim
        if (named > 0.02f) {
            p(NOTE_S, 0.05f, 2.60f, pa)
            kit.text("y' = f(y)", pa[0], pa[1], pa[2], 0.22f, SceneParts.HOT, named, rise = 0.85f)
            // The sign of f' at a line is the whole criterion, and it is readable off the marks
            // beside it without solving anything — which is the sentence the stop is named for.
            p(NOTE_S, LOW, 1.40f, pa)
            kit.text("f' < 0", pa[0], pa[1], pa[2], 0.20f, SceneParts.ADDED, named, rise = -0.95f)
            p(NOTE_S, HIGH, 1.40f, pa)
            kit.text("f' > 0", pa[0], pa[1], pa[2], 0.20f, SceneParts.TAKEN, named, rise = 0.95f)
        }
        // The secondary line, and only at full detail: it is the shape of both experiments at once,
        // and it means nothing without the two rungs it is about.
        if (q == 0 && released) {
            p(NOTE_S, 0.05f, 2.60f, pa)
            kit.text("δ(t) = δ_0 e^{λt}", pa[0], pa[1], pa[2], 0.16f,
                SceneParts.COOL, 0.85f * dim, GlyphBoard.Style.SMALL, rise = -0.85f)
        }
    }
}
