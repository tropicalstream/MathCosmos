package com.rayneo.mathcosmos

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR VI, stop 11 — THE SPRING AND THE CIRCLE. "A swing's position and its speed, taken together,
 * travel in a circle."
 *
 * The helix returns, and this time it is not a picture of e^{iθ}. A mass on a spring is bolted to
 * the port wall and swings, visibly, along a track. Its position and its speed are read off as a
 * single point — position across, speed up — and that point runs a closed loop. Because Tour VI's
 * along-axis is TIME, the record of the loop is laid down the corridor as a helix, and looking
 * straight down the corridor from the helm the helix is a circle. Then the damper is engaged: the
 * helix tapers, the loop stops closing, and end-on it becomes a spiral into the origin.
 *
 * THE CALLBACK IS DELIBERATE AND THE CODE SHOULD SAY SO. This is IV-11's geometry — same corkscrew,
 * same end-on ring, deliberately the same CHALK — carrying a different subject. There it recorded
 * a point turning at a steady rate because that is what e^{iθ} does; here it records a mass and a
 * spring trading position for speed and back. The crew names the coincidence out loud, and the
 * palette is the argument: a viewer who learned the shape three tours ago gets it back for free.
 *
 * THE ONE CONSTRUCTION THAT DOES THE TEACHING is the vertical tie. The mass and the phase point
 * always share their position coordinate — the phase point sits directly above or below the mass,
 * frame for frame, because its horizontal coordinate IS the mass's displacement and nothing else.
 * So a dashed line dropped from the phase point to the mass crosses the position axis at exactly
 * the mass's position, and its length above that axis is the speed. One dashed segment, and the
 * phase plane stops being a second diagram parked beside a machine and becomes a reading of it.
 *
 * THE CLOSURE IS DRAWN, NOT ASSERTED. Two things a viewer cannot be asked to take on trust: that
 * the loop closes, and that end-on it is a circle. Both are drawn as projections in the honest
 * sense — the same sampled points with one coordinate replaced, never a second curve evaluated
 * independently and parked nearby.
 *
 *   · The shadow. Every helix sample is drawn a second time in the now-plane with its along-axis
 *     coordinate set to zero. Undamped, all three coils land on one another and the shadow is a
 *     single circle. Damped, they no longer coincide and the shadow is a spiral winding in. This
 *     matters because a real eye is never actually on the axis — from off-axis the coils of a helix
 *     nest as rings of different apparent size and never assemble themselves into the promised
 *     circle. So the circle is drawn rather than left to the viewer's optic nerve.
 *
 *   · The closure line. The helix points exactly one, two and three turns back all have the same
 *     phase. Undamped they have the same amplitude too, so they lie on ONE straight line parallel
 *     to the corridor — which is what "the loop closed" means, made visible as a straightness you
 *     can check by eye. Damped, that line bends inward. Three dashed spans, and they carry the
 *     whole difference between the two halves of the stop.
 *
 * WHERE THE DAMPING SHOWS FIRST. The record astern is the past, so engaging the damper cannot
 * retro-shrink it: the point at corridor position a was laid down -a/UPS seconds ago, and it decays
 * only for however much of that time the damper has been running. The taper front therefore starts
 * at the now-plane and sweeps ASTERN at the rate time is laid down, which is both correct and the
 * better picture — you watch the cone open away from you rather than the whole thing shrink at once.
 *
 * PLACEMENT. Tour VI's wall alpha is 0.2 and the tube is a guide-rail, so the rig is allowed out to
 * the wall and takes the invitation: the spring lies low to port, near the wall, and the phase
 * apparatus stands inboard and above it. The corkscrew is off the rail rather than on it for the
 * usual hardware reason — a corridor-length object centred on the rail is one you fly INSIDE, and
 * from inside it is a smear — but its axis stays parallel to the flight path, so the end-on view
 * survives the offset. Everything bright is faded out within arm's reach of the eye.
 *
 * THE POSITION AXIS POINTS OUTBOARD, which looks like a whim and is not. Displacement is measured
 * positive toward the anchor the spring pushes against, so +x is outboard; that also puts every
 * label on the far side of the figure from the rail, and a billboard hung inboard would be a glyph
 * across the pilot's face at the closest point of the pass. Which way the loop appears to turn on
 * screen depends on which way the rail's side vector happens to point at this node; in the figure's
 * own (x, v) axes it turns the standard way, clockwise, because v leads x by a quarter period.
 *
 * NO FIELD IS SAMPLED HERE, and that is not an oversight. This stop's subject is a second-order
 * equation, not the ambient field the rest of the tour is about; the arrow lattice belongs to the
 * ambient and borrowing it would suggest the spring was being pushed by it. The phase plane is of
 * course itself a vector field — the crew says so — but drawing a second field over the loop would
 * cost the loop its legibility, so it is said and not drawn.
 *
 * BUDGET. One flushLines of about six hundred vertices, one flushTris, two lit beads and four
 * labels: eight draw calls at quality 0.
 */
object SceneSpringAndCircle : MathScene {

    /** The corkscrew runs seven units astern, and the end-on view wants to be seen on the way in. */
    override val reach = 1.6f

    // ---- the clock -------------------------------------------------------------------------------
    // Eleven seconds of an undamped spring, which is a steady state and therefore already "finished"
    // whenever a viewer arrives; the damper seats; ten seconds of the cone opening astern; and four
    // seconds at the end with the mass all but still and the spiral wound in, which is the state the
    // crew talks over. The last of the cycle dims out so the reset is not a bang.
    private const val PERIOD = 28f
    private const val PI2 = 6.2831855f
    private const val FADE_IN = 0.035f
    private const val DAMP_AT = 0.44f
    private const val ENGAGE = 0.045f            // the dashpot sliding in and seating
    private const val CLEAR_AT = 0.955f

    // ---- the oscillator ---------------------------------------------------------------------------
    // Nine whole turns per cycle, exactly, so the phase is continuous across the loop join and the
    // only thing the dim-out has to hide is the amplitude coming back.
    private const val OMEGA = PI2 * 9f / PERIOD  // 2.02 rad/s: a swing of about three seconds
    private const val R = 0.80f                  // the undamped amplitude, in world units
    private const val DAMP_K = 0.145f            // e-folding of about seven seconds once engaged

    // ---- the record down the corridor --------------------------------------------------------------
    private const val LEN = 7.0f                 // how far astern the helix is laid
    private const val TURN_LEN = 2.30f           // corridor units per oscillation, so three turns show
    private const val K_A = PI2 / TURN_LEN       // radians of phase per corridor unit
    private const val UPS = OMEGA / K_A          // corridor units per second: how fast time is laid
    private const val INV_UPS = 1f / UPS
    private const val TURNS_BACK = 3             // spans of the closure line

    private const val HN = 120                   // helix samples
    private const val HN_LOW = 60
    private const val HN_MIN = 40

    // ---- the rig -----------------------------------------------------------------------------------
    private const val SIDE = -1.55f              // the phase apparatus, off to port of the rail
    private const val UP = 0.45f
    private const val TRACK_U = -1.25f           // the spring's track, low, under the phase plane
    private const val ANCHOR_S = -1.95f          // and its anchor plate, out by the wall
    private const val TRACK_END = 1.00f
    private const val MASS_R = 0.13f
    private const val COIL_R = 0.10f
    private const val COIL_TURNS = 7f
    private const val NC = 44                    // coil samples
    private const val NC_LOW = 24
    private const val DASH_AHEAD = 0.32f             // the dashpot, offset ahead so it clears the coil

    // ---- the notation's furniture -------------------------------------------------------------------
    private const val TICK = 0.07f
    private const val TIME_S = -1.35f            // the time arrow, outboard of the corkscrew
    private const val TIME_U = -0.30f
    private const val TIME_A0 = -4.40f
    private const val TIME_LEN = 1.20f

    // ---- weights ------------------------------------------------------------------------------------
    private const val HELIX_A = 0.55f
    private const val SHADOW_A = 0.72f
    private const val TIE_A = 0.50f

    // Nothing bright within arm's reach of the eye — the same near distances the tour's ambient uses,
    // so a thread and the arrows around it agree about where the pilot's face is.
    private const val R_NEAR = 1.05f
    private const val R_NEAR_FULL = 1.95f

    // ---- the palette, aliased to the roles it plays ---------------------------------------------------
    private val HELIX_C = SceneParts.CHALK       // the record: IV-11's colour, on purpose
    private val LOOP_C = SceneParts.HOT          // the end-on view, and the closure line
    private val POS_C = SceneParts.ADDED         // position, teal, as in THE WHEEL and THE MEETING
    private val VEL_C = SceneParts.WORK          // and speed, amber, likewise
    private val RIG_C = SceneParts.STEEL         // the machine, which is not the mathematics
    private val DAMP_C = SceneParts.TAKEN        // the damper, in the colour of a debt
    private val TIE_C = SceneParts.COOL

    // ---- scratch ---------------------------------------------------------------------------------------
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val pc = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    /** Two decimal places, without a formatter and without allocating one. */
    private fun fmt(v: Float): String {
        val cents = (v * 100f + 0.5f).toInt()
        val frac = cents % 100
        return "" + (cents / 100) + "." + (if (frac < 10) "0" else "") + frac
    }

    /** How long the damper has been running, in seconds, at cycle phase [c]. */
    private fun damped(c: Float): Float = ((c - DAMP_AT - ENGAGE) * PERIOD).coerceAtLeast(0f)

    /**
     * The amplitude of the record laid down [age] seconds ago, given the damper has been running
     * for [d]. Anything older than the damper still carries the amplitude it was laid with — the
     * past is not rewritten, which is why the taper is a front that sweeps astern.
     */
    private fun ampAt(d: Float, age: Float): Float {
        val t = d - age
        return if (t <= 0f) R else R * exp(-DAMP_K * t)
    }

    /** The near-distance fade at a world point: 0 in the pilot's face, 1 at a readable distance. */
    private fun near(kit: SceneKit, x: Float, y: Float, z: Float): Float {
        val dx = x - kit.camX
        val dy = y - kit.camY
        val dz = z - kit.camZ
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        return ((d - R_NEAR) / (R_NEAR_FULL - R_NEAR)).coerceIn(0f, 1f)
    }

    /**
     * The phase radius, as a fraction of the undamped amplitude, and whether the loop is closing.
     * This is the number the stop is measuring: it is exactly constant while the damper is off and
     * falls once it is on, which is the whole difference between a closed orbit and a spiral. It
     * belongs on the HUD and not in the world — the geometry's job is to be the shape.
     */
    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val d = damped(c)
        val r = ampAt(d, 0f) / R
        return if (d <= 0f) "r " + fmt(r) + "   LOOP CLOSED" else "r " + fmt(r) + "   DECAYING"
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val q = kit.quality
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val dim = (c / FADE_IN).coerceAtMost(1f) *
            (if (c > CLEAR_AT) (1f - c) / (1f - CLEAR_AT) else 1f).coerceIn(0f, 1f)
        if (dim <= 0.01f) return

        val t = c * PERIOD
        val thNow = OMEGA * t
        val dSec = damped(c)
        val seat = SceneParts.step(c, DAMP_AT, ENGAGE)      // the dashpot arriving
        val amp0 = ampAt(dSec, 0f)
        val hn = if (q == 0) HN else if (q == 1) HN_LOW else HN_MIN

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // The now-plane sits at a fixed spot beside the rail, so the shadow's near-fade is one
        // distance rather than one per sample.
        SceneParts.at(g, 0f, 0f, 0f, pc)
        val shadowNear = near(kit, pc[0], pc[1], pc[2])

        // --- the record, and its shadow in the now-plane ------------------------------------------
        // Both are walked in one loop from the same (s, u): the shadow is the SAME point with its
        // along-axis coordinate replaced by zero, never a second curve evaluated on its own. If the
        // two ever disagreed it would be a bug in one line, not two pictures differing in opinion.
        var hx = 0f; var hy = 0f; var hz = 0f
        var sx = 0f; var sy = 0f; var sz = 0f
        var ha = 0f; var sa = 0f
        for (k in 0..hn) {
            val u01 = k.toFloat() / hn
            val a = -LEN * (1f - u01)
            val amp = ampAt(dSec, -a * INV_UPS)
            val th = thNow + K_A * a
            val ps = -amp * cos(th)
            val pu = -amp * sin(th)
            SceneParts.at(g, ps, pu, a, pa)
            SceneParts.at(g, ps, pu, 0f, pb)
            // The oldest end is tapered away rather than snipped: a record that stops with a hard
            // end looks like a thing that was cut, not a thing that runs back out of sight.
            val tail = (u01 * 4.5f).coerceAtMost(1f)
            val alphaH = HELIX_A * tail * near(kit, pa[0], pa[1], pa[2]) * dim
            val alphaS = SHADOW_A * tail * shadowNear * dim
            if (k > 0) {
                v = MathMesh.segment(line, v, hx, hy, hz, pa[0], pa[1], pa[2],
                    HELIX_C[0], HELIX_C[1], HELIX_C[2], ha, alphaH)
                v = MathMesh.segment(line, v, sx, sy, sz, pb[0], pb[1], pb[2],
                    LOOP_C[0], LOOP_C[1], LOOP_C[2], sa, alphaS)
            }
            hx = pa[0]; hy = pa[1]; hz = pa[2]
            sx = pb[0]; sy = pb[1]; sz = pb[2]
            ha = alphaH; sa = alphaS
        }

        // --- the closure line ----------------------------------------------------------------------
        // One turn back is one full period back, so those points share a phase exactly. Undamped
        // they share an amplitude too and the line through them is DEAD STRAIGHT along the corridor:
        // that straightness is what "the loop closes" means, and a viewer can check it by eye
        // against the rail beside it. Once the damper is on it bends inward, turn by turn.
        if (q < 2) {
            val spans = if (q == 0) TURNS_BACK else TURNS_BACK - 1
            val cn = cos(thNow); val sn = sin(thNow)
            for (m in 0 until spans) {
                val a0 = -TURN_LEN * m
                val a1 = -TURN_LEN * (m + 1)
                val r0 = ampAt(dSec, -a0 * INV_UPS)
                val r1 = ampAt(dSec, -a1 * INV_UPS)
                SceneParts.at(g, -r0 * cn, -r0 * sn, a0, pa)
                SceneParts.at(g, -r1 * cn, -r1 * sn, a1, pb)
                v = MathMesh.dashed(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], 7,
                    LOOP_C[0], LOOP_C[1], LOOP_C[2], 0.42f * dim)
            }
        }

        // --- the phase plane's axes ------------------------------------------------------------------
        // The ticks land exactly at the undamped amplitude, so the circle is inscribed in its own
        // axis marks and the amplitude is a thing on the drawing rather than a number.
        SceneParts.at(g, 0f, 0f, 0f, pa)
        SceneParts.vec(g, -1f, 0f, 0f, du)          // +x, outboard, toward the anchor
        SceneParts.vec(g, 0f, 1f, 0f, dv)           // +v, up
        v = MathMesh.axis(line, v, pa[0], pa[1], pa[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            R, 1, TICK, POS_C[0], POS_C[1], POS_C[2], 0.55f * dim)
        v = MathMesh.axis(line, v, pa[0], pa[1], pa[2], dv[0], dv[1], dv[2], du[0], du[1], du[2],
            R, 1, TICK, VEL_C[0], VEL_C[1], VEL_C[2], 0.55f * dim)

        // --- the corridor is time -----------------------------------------------------------------
        // Said with an arrow rather than with a sentence. Everything else in the scene depends on
        // it: the corkscrew is only a record because the axis it is laid along is a clock.
        if (q < 2) {
            SceneParts.at(g, TIME_S, TIME_U, TIME_A0, pa)
            SceneParts.vec(g, 0f, 0f, TIME_LEN, du)
            SceneParts.vec(g, 0f, 1f, 0f, dv)
            v = MathMesh.arrow(line, v, pa[0], pa[1], pa[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], HELIX_C[0], HELIX_C[1], HELIX_C[2], 0.40f * dim)
        }

        // --- the spring rig ---------------------------------------------------------------------------
        val massS = -amp0 * cos(thNow)
        val phaseU = -amp0 * sin(thNow)

        // The track the mass rides on, just under it, dim: a machine part, not a measurement.
        SceneParts.at(g, ANCHOR_S, TRACK_U - 0.17f, 0f, pa)
        SceneParts.at(g, TRACK_END, TRACK_U - 0.17f, 0f, pb)
        v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
            RIG_C[0], RIG_C[1], RIG_C[2], 0.35f * dim)

        // The anchor plate at the wall, spanned by up and along so it faces the mass squarely.
        SceneParts.at(g, ANCHOR_S, TRACK_U - 0.30f, -0.26f, pa)
        SceneParts.vec(g, 0f, 0.60f, 0f, du)
        SceneParts.vec(g, 0f, 0f, 0.52f, dv)
        tv[0] = SceneParts.fill(tri, tv[0], pa[0], pa[1], pa[2], du[0], du[1], du[2],
            dv[0], dv[1], dv[2], RIG_C, 0.20f * dim)
        v = SceneParts.edge(line, v, pa[0], pa[1], pa[2], du[0], du[1], du[2],
            dv[0], dv[1], dv[2], RIG_C, 0.55f * dim)

        // The coil. A real helix about the track's own axis rather than a flat zig-zag, because in
        // stereo a flat spring reads as a saw blade; and its turn count is fixed, so the pitch — the
        // one thing that has to be seen changing — carries the whole compression.
        val coilFrom = ANCHOR_S
        val coilTo = massS - MASS_R
        val nc = if (q == 0) NC else if (q == 1) NC_LOW else 0
        if (nc > 0) {
            var cx = 0f; var cy = 0f; var cz = 0f
            for (k in 0..nc) {
                val u01 = k.toFloat() / nc
                val ang = u01 * COIL_TURNS * PI2
                SceneParts.at(
                    g, coilFrom + (coilTo - coilFrom) * u01,
                    TRACK_U + COIL_R * cos(ang), COIL_R * sin(ang), pa
                )
                if (k > 0) v = MathMesh.segment(line, v, cx, cy, cz, pa[0], pa[1], pa[2],
                    RIG_C[0], RIG_C[1], RIG_C[2], 0.80f * dim)
                cx = pa[0]; cy = pa[1]; cz = pa[2]
            }
        } else {
            SceneParts.at(g, coilFrom, TRACK_U, 0f, pa)
            SceneParts.at(g, coilTo, TRACK_U, 0f, pb)
            v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                RIG_C[0], RIG_C[1], RIG_C[2], 0.70f * dim)
        }

        // --- the damper ----------------------------------------------------------------------------
        // A dashpot in parallel with the spring, offset ahead so it does not sit inside the coil. It
        // slides in from the wall as it seats, and it keeps its own colour for the rest of the cycle,
        // so the reason the loop stopped closing is still an object in the scene and not merely a
        // change in the numbers. It survives the governor at every quality: five segments is nothing,
        // and a spiral with no visible cause is a stop that has lost its second half.
        if (seat > 0.01f) {
            val slide = ANCHOR_S - 0.55f * (1f - seat)
            SceneParts.at(g, slide + 0.12f, TRACK_U - 0.11f, DASH_AHEAD, pa)
            SceneParts.vec(g, 0.56f, 0f, 0f, du)
            SceneParts.vec(g, 0f, 0.22f, 0f, dv)
            tv[0] = SceneParts.fill(tri, tv[0], pa[0], pa[1], pa[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], DAMP_C, 0.22f * seat * dim)
            v = SceneParts.edge(line, v, pa[0], pa[1], pa[2], du[0], du[1], du[2],
                dv[0], dv[1], dv[2], DAMP_C, 0.75f * seat * dim)
            // The piston rod, which lengthens and shortens with the mass: the damper is doing work.
            SceneParts.at(g, slide + 0.68f, TRACK_U, DASH_AHEAD, pa)
            SceneParts.at(g, massS, TRACK_U, DASH_AHEAD, pb)
            v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                DAMP_C[0], DAMP_C[1], DAMP_C[2], 0.70f * seat * dim)
        }

        // --- the tie that makes the phase plane a READING of the machine --------------------------
        // Straight up from the mass to the phase point. They share their position coordinate exactly
        // — the phase point's horizontal coordinate IS the mass's displacement — so the tie is
        // vertical, it crosses the position axis at the displacement, and its length above that
        // crossing is the speed. This is the one line in the scene the stop cannot do without.
        SceneParts.at(g, massS, TRACK_U, 0f, pa)
        SceneParts.at(g, massS, phaseU, 0f, pb)
        v = MathMesh.dashed(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], 8,
            TIE_C[0], TIE_C[1], TIE_C[2], TIE_A * dim)

        kit.flushLines(v, 2.3f)
        if (tv[0] >= 3) kit.flushTris(tv[0])

        // --- the two things that are actually moving -----------------------------------------------
        SceneParts.at(g, massS, TRACK_U, 0f, pa)
        kit.ball(pa[0], pa[1], pa[2], MASS_R, MASS_R, MASS_R, VEL_C, SceneParts.HOT,
            near(kit, pa[0], pa[1], pa[2]) * dim, glow = 0.9f)
        SceneParts.at(g, massS, phaseU, 0f, pb)
        kit.ball(pb[0], pb[1], pb[2], 0.085f, 0.085f, 0.085f, LOOP_C, POS_C,
            shadowNear * dim, glow = 2.0f)

        // --- notation --------------------------------------------------------------------------------
        // All of it outboard of the figure, level with what it names. Inboard is where the rail is,
        // and a billboard hung there is a glyph across the pilot's face at the closest point of the
        // pass; above is where the telemetry lives and below is the caption box.
        SceneParts.at(g, -1.02f, 0.72f, 0.05f, pa)
        kit.text("(x, v)", pa[0], pa[1], pa[2], 0.18f, LOOP_C, 0.95f * dim,
            GlyphBoard.Style.MATH, anchor = 0.5f)

        if (q < 2) {
            SceneParts.at(g, TIME_S - 0.22f, TIME_U, TIME_A0 + TIME_LEN + 0.14f, pa)
            kit.text("t", pa[0], pa[1], pa[2], 0.17f, HELIX_C, 0.85f * dim)
        }

        // The axis names are secondary — they label ticks, and the picture is already saying what
        // they say — so the governor takes them first.
        if (q == 0) {
            SceneParts.at(g, -(R + 0.30f), 0.17f, 0f, pa)
            kit.text("x", pa[0], pa[1], pa[2], 0.16f, POS_C, 0.85f * dim)
            SceneParts.at(g, -0.22f, R + 0.11f, 0f, pa)
            kit.text("v", pa[0], pa[1], pa[2], 0.16f, VEL_C, 0.85f * dim, anchor = 0.5f)
        }
    }
}
