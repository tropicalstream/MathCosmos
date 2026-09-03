package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Stop 11 — THE MEETING. "The exponential and the circle are the same object seen from two sides."
 *
 * The last landmark of Tour IV, and the one the whole series has been walking towards. A helix
 * stands the length of the corridor: a point turning at a steady rate while it travels at a steady
 * rate. Two flat light-planes are set against it, a floor under it and a wall outboard of it, and
 * the helix's shadow on the floor is a cosine wave while its shadow on the wall is a sine wave.
 * Look down the corridor and the same helix is a circle. One object, three views, and the identity
 * e^{iθ} = cos θ + i sin θ is the sentence that records the fact.
 *
 * This is a PROJECTION and not an analogy, and the code is written so that it cannot quietly become
 * one. Each shadow point is the helix point with a single coordinate replaced by its plane's
 * constant — the other two are shared, unaltered, sample for sample. Nothing is evaluated twice as
 * an independent graph and parked next to a spiral. If the wave and the helix ever disagreed it
 * would be a bug in one line, not a difference of opinion between two pictures.
 *
 * Where the planes sit is part of the argument. Each is exactly one radius out, so the helix
 * TOUCHES its own shadow once per turn — at the top of the sine, at the port edge of the cosine —
 * which is the moment the projection is degenerate and object and record honestly coincide. The two
 * planes also share their long lower edge, so they read as the corner of a room rather than as two
 * rectangles floating near each other.
 *
 * Two things worth saying out loud, because the crew says them out loud.
 *
 * First, the end-on circle. From a real eye at a real distance, off the axis, the coils of a helix
 * do NOT stack into one clean circle — they nest as rings of slightly different apparent size, and
 * from a point actually on the axis they would nest even harder. So the circle is not left to the
 * viewer's eye to assemble: the ring at the near end is DRAWN, in the helix's own colour, as the
 * projection it is, with a rim bead at the current angle and a dashed tie back to the helix bead
 * that runs along the corridor and along nothing else. The two beads differ by a translation down
 * θ and by nothing at all besides. That is the claim, and it is drawn rather than implied.
 *
 * Second, the radius. The helix is 0.85 world units across the axis, not 1 — this is the unit
 * circle drawn big enough to see from the rail, which is why the readout carries the actual cosine
 * and sine and the geometry carries only their shape.
 *
 * The sweep runs at a constant rate rather than eased, exactly as THE WHEEL does in Tour I: a
 * steady turn is the entire content of the picture, and an eased bead would draw a wave that is not
 * a sine wave. The rest every looping scene needs is taken at the end, with the record complete.
 *
 * Amber is the vertical shadow and teal the horizontal one — the same two colours THE WHEEL used
 * for the same two shadows, twelve stops and three tours ago. A viewer who learned them there gets
 * them back here for free, and that is the point of the stop.
 */
object SceneMeeting : MathScene {

    // The figure runs seven units down the corridor, so it must not be culled the moment its near
    // ring is behind the camera.
    override val reach = 1.5f
    override val deep = 0.3f

    private const val PERIOD = 26f
    private const val SWEEP_AT = 0.04f
    private const val SWEEP_LEN = 0.62f      // sixteen seconds turning, nine standing finished

    private const val TWO_PI = 6.2831855f
    private const val TURNS = 2.25f
    private const val THETA_MAX = TWO_PI * TURNS
    private const val HALF = 3.5f            // half the figure's length along the rail
    private const val PITCH = (HALF * 2f) / THETA_MAX   // corridor units per radian
    private const val R = 0.85f              // the unit circle, drawn big

    // The figure hangs to port. A helix centred on the rail is one the craft flies through, and at
    // the closest point of the pass a corridor-length object seen from inside it is a smear. Off to
    // one side the axis is still parallel to the flight path — so the end-on view survives — and
    // the whole of it stays in frame from the approach to the departure.
    private const val SIDE = -1.15f
    private const val ROOF_FRAC = 0.24f
    private const val DROP = 0.68f

    // The palette, aliased to the roles it plays, so this stop is the same amber and teal as the
    // wheel it is quoting.
    private val SIN_C = SceneParts.WORK       // the vertical shadow: the wall wave and its comb
    private val COS_C = SceneParts.ADDED      // the horizontal shadow: the floor wave and its comb
    private val HELIX = SceneParts.CHALK      // the object, and the ring, because they are one thing
    private val PLANE_C = SceneParts.STEEL    // the two light-planes
    private val HOT = SceneParts.HOT          // the bead, the spoke, the identity

    private val f = FloatArray(12)   // rail frame scratch for stage()
    private val g = FloatArray(12)   // the stage: centre, right, up, forward
    private val o = FloatArray(3)    // a world point
    private val q = FloatArray(3)    // a second world point, for the far end of a segment
    private val du = FloatArray(3)   // spanning vectors for the planes
    private val dv = FloatArray(3)
    private val ca = FloatArray(3)   // curve scratch
    private val cb = FloatArray(3)
    private val tv = IntArray(1)

    /** How far the turn has got, in radians. Linear on purpose — see the note above. */
    private fun theta(kit: SceneKit): Float {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return ((c - SWEEP_AT) / SWEEP_LEN).coerceIn(0f, 1f) * THETA_MAX
    }

    /**
     * The two numbers being read off the shadows, in 2D where they are legible. The stop's cut unit
     * is θ, so the angle leads; the cosine and sine follow it with their signs kept, because half
     * the content of this stop is that both of them go negative and the helix does not care.
     */
    override fun readout(kit: SceneKit): String? {
        val t = theta(kit)
        return "θ %.2f   cos θ %+.2f   sin θ %+.2f".format(Locale.US, t, cos(t), sin(t))
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Tour IV's roof is the tour's own function, so the figure hangs on a fraction of it rather
        // than at a fixed height and rides up and down with the ceiling. The drop matters more than
        // the fraction: the floor plane has to stay well BELOW the eye, because a floor seen
        // edge-on casts no readable shadow and that plane is half the stop.
        val up = (ROOF_FRAC * kit.traceHeight(i.toFloat()) - DROP).coerceIn(-0.75f, 0.20f)
        SceneParts.stage(kit, i.toFloat(), SIDE, up, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val th = theta(kit)
        val drawn = th > 0.02f
        val cs = cos(th)
        val sn = sin(th)
        val aNow = -HALF + th * PITCH
        val full = when (kit.quality) { 0 -> 96; 1 -> 52; else -> 30 }
        val segs = max(2, (full * th / THETA_MAX).toInt())

        // --- the two light-planes -------------------------------------------------------------
        // Both start from the same corner and run the length of the figure, so their shared lower
        // edge is a single line down the corridor. One radius out each, which is what lets the
        // helix meet its own shadow once a turn instead of hovering above it forever.
        SceneParts.at(g, -R, -R, -HALF, o)
        SceneParts.vec(g, 0f, 0f, HALF * 2f, dv)
        SceneParts.vec(g, R * 2f, 0f, 0f, du)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], PLANE_C, 0.40f, 2, 9
        )
        SceneParts.vec(g, 0f, R * 2f, 0f, du)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], PLANE_C, 0.40f, 2, 9
        )

        // --- the helix ---------------------------------------------------------------------------
        // The OBJECT is there whether or not anything has been recorded yet, so the whole helix
        // stands at half brightness from the first frame of the cycle and only the stretch the bead
        // has already turned through lights up. The shadows below get the opposite treatment: they
        // do not exist until they have been cast.
        v = MathMesh.curve(
            line, v, full, 0f, THETA_MAX, HELIX[0], HELIX[1], HELIX[2], 0.40f, false, ca, cb
        ) { t, out -> SceneParts.at(g, R * cos(t), R * sin(t), -HALF + t * PITCH, out) }
        if (drawn) {
            v = MathMesh.curve(
                line, v, segs, 0f, th, HELIX[0], HELIX[1], HELIX[2], 0.95f, false, ca, cb
            ) { t, out -> SceneParts.at(g, R * cos(t), R * sin(t), -HALF + t * PITCH, out) }

            // --- the two shadows, written live ---------------------------------------------------
            // Each of these is the helix line with ONE coordinate overwritten by its plane's
            // constant. Same parameter, same samples, same everything else. That is what makes it a
            // projection rather than two graphs drawn near a spiral.
            v = MathMesh.curve(
                line, v, segs, 0f, th, COS_C[0], COS_C[1], COS_C[2], 0.95f, false, ca, cb
            ) { t, out -> SceneParts.at(g, R * cos(t), -R, -HALF + t * PITCH, out) }
            v = MathMesh.curve(
                line, v, segs, 0f, th, SIN_C[0], SIN_C[1], SIN_C[2], 0.95f, false, ca, cb
            ) { t, out -> SceneParts.at(g, -R, R * sin(t), -HALF + t * PITCH, out) }
        }

        // --- the projectors ------------------------------------------------------------------------
        // One live pair does not show that EVERY point of the helix drops onto the wave underneath
        // it; a rung left behind every eighth of a turn does. They are dim on purpose — they are
        // scaffolding, and the waves are the finding.
        if (drawn && kit.quality < 2) {
            val combs = if (kit.quality == 0) 18 else 9
            for (k in 0 until combs) {
                val t = (k + 0.5f) / combs * THETA_MAX
                if (t > th) break
                val a = -HALF + t * PITCH
                val ct = cos(t)
                val st = sin(t)
                SceneParts.at(g, R * ct, R * st, a, o)
                SceneParts.at(g, R * ct, -R, a, q)
                v = MathMesh.segment(
                    line, v, o[0], o[1], o[2], q[0], q[1], q[2], COS_C[0], COS_C[1], COS_C[2], 0.22f
                )
                SceneParts.at(g, -R, R * st, a, q)
                v = MathMesh.segment(
                    line, v, o[0], o[1], o[2], q[0], q[1], q[2], SIN_C[0], SIN_C[1], SIN_C[2], 0.22f
                )
            }
        }
        if (drawn) {
            SceneParts.at(g, R * cs, R * sn, aNow, o)
            SceneParts.at(g, R * cs, -R, aNow, q)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], q[0], q[1], q[2], COS_C[0], COS_C[1], COS_C[2], 0.85f
            )
            SceneParts.at(g, -R, R * sn, aNow, q)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], q[0], q[1], q[2], SIN_C[0], SIN_C[1], SIN_C[2], 0.85f
            )
        }

        // --- the wheel, returned ---------------------------------------------------------------------
        // The helix seen end-on IS a circle, so the ring is drawn in the helix's own colour: same
        // object, one projection further. It stands in the plane the craft is flying towards, which
        // means on the approach you look THROUGH the hoop and the coils nest inside it — the stop's
        // whole claim, available before a word of it is said.
        SceneParts.at(g, 0f, 0f, -HALF, o)
        v = MathMesh.arc(
            line, v, o[0], o[1], o[2], g[3], g[4], g[5], g[6], g[7], g[8],
            R, 0f, TWO_PI, if (kit.quality == 0) 40 else 20,
            HELIX[0], HELIX[1], HELIX[2], 0.80f + 0.20f * kit.beat
        )
        SceneParts.at(g, R * cs, R * sn, -HALF, q)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], q[0], q[1], q[2], HOT[0], HOT[1], HOT[2], 0.85f)

        // The real and imaginary axes of that plane, each in the colour of the wave it becomes.
        // The craft flies past the ring's starboard side, so both crosshairs are cut short of it.
        if (kit.quality == 0) {
            SceneParts.at(g, -R * 1.15f, 0f, -HALF, o)
            SceneParts.at(g, R * 0.62f, 0f, -HALF, q)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], q[0], q[1], q[2], COS_C[0], COS_C[1], COS_C[2], 0.35f
            )
            SceneParts.at(g, 0f, -R * 1.15f, -HALF, o)
            SceneParts.at(g, 0f, R * 1.15f, -HALF, q)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], q[0], q[1], q[2], SIN_C[0], SIN_C[1], SIN_C[2], 0.35f
            )
        }

        // The tie between the two views: it runs along the corridor and along nothing else, because
        // the bead and its rim bead differ by a translation down θ and by nothing whatever besides.
        // The arm probes reach out at this stop, so the tie brightens as they come out — it is the
        // line the crew points at.
        if (drawn && kit.quality < 2) {
            SceneParts.at(g, R * cs, R * sn, aNow, o)
            SceneParts.at(g, R * cs, R * sn, -HALF, q)
            v = MathMesh.dashed(
                line, v, o[0], o[1], o[2], q[0], q[1], q[2], if (kit.quality == 0) 14 else 8,
                HOT[0], HOT[1], HOT[2], 0.28f + 0.34f * kit.reach
            )
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the four beads -----------------------------------------------------------------------
        // The turning point, its two shadows, and the same point seen end-on. Four lit spheres is
        // four draw calls and they are the four things a viewer has to be able to find instantly.
        SceneParts.at(g, R * cs, R * sn, aNow, o)
        kit.ball(
            o[0], o[1], o[2], 0.075f, 0.075f, 0.075f, HOT, HELIX, 1f,
            0f, 0f, 1f, 0f, 0f, 1.3f + 0.6f * kit.beat
        )
        SceneParts.at(g, R * cs, -R, aNow, o)
        kit.ball(o[0], o[1], o[2], 0.05f, 0.05f, 0.05f, COS_C, COS_C, 0.95f, 0f, 0f, 1f, 0f, 0f, 1.0f)
        SceneParts.at(g, -R, R * sn, aNow, o)
        kit.ball(o[0], o[1], o[2], 0.05f, 0.05f, 0.05f, SIN_C, SIN_C, 0.95f, 0f, 0f, 1f, 0f, 0f, 1.0f)
        SceneParts.at(g, R * cs, R * sn, -HALF, o)
        kit.ball(
            o[0], o[1], o[2], 0.06f, 0.06f, 0.06f, HOT, HELIX, 1f,
            0f, 0f, 1f, 0f, 0f, 1.1f + 0.5f * kit.reach
        )

        // --- notation ---------------------------------------------------------------------------------
        // The figure runs the length of the corridor, so at the moment of the pass it lies as a
        // horizontal band across the eye. Its two ENDS are the only clear ground there is: the
        // telemetry owns the top of the frame and the caption box the bottom, and a name hung over
        // the waves is a name nobody reads.
        val gl = 0.20f

        // Each wave, named where it runs out, at its own lane's height.
        SceneParts.at(g, R * cos(THETA_MAX), -R, HALF + 0.45f, o)
        kit.text("cos θ", o[0], o[1], o[2], gl, COS_C, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
        SceneParts.at(g, -R, R * sin(THETA_MAX), HALF + 0.45f, o)
        kit.text("sin θ", o[0], o[1], o[2], gl, SIN_C, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)

        // The point going round, named beside the wheel at the near end — the end the craft meets
        // first, and the view in which it is a circle and nothing else.
        SceneParts.at(g, -R * 1.60f, 0.30f, -HALF, o)
        kit.text("e^{iθ}", o[0], o[1], o[2], gl * 0.9f, HOT, 0.95f, GlyphBoard.Style.MATH, 1.1f)

        // The identity itself, on the axis past the far end, and only once the picture has already
        // made it true: the shadows have to exist before their names are worth anything.
        if (th > THETA_MAX * 0.45f) {
            SceneParts.at(g, 0f, 0f, HALF + 0.95f, o)
            kit.text(
                "e^{iθ} = cos θ + i sin θ", o[0], o[1], o[2], gl * 0.85f, HOT, 1f,
                GlyphBoard.Style.MATH, 1.15f
            )
        }

        // Which axis is which. Secondary, so full detail only, and both hung on the port half of
        // the ring because the starboard half is where the rail goes past.
        if (kit.quality == 0) {
            SceneParts.at(g, -R * 1.24f, -0.26f, -HALF, o)
            kit.text("Re", o[0], o[1], o[2], gl * 0.7f, COS_C, 0.75f, GlyphBoard.Style.SMALL)
            SceneParts.at(g, -0.26f, R * 1.26f, -HALF, o)
            kit.text("Im", o[0], o[1], o[2], gl * 0.7f, SIN_C, 0.75f, GlyphBoard.Style.SMALL)
        }
    }
}
