package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR VI, stop 4 — THE PADDLE WHEEL. "Hold a wheel in the flow; if it spins, there's curl, and
 * the axis it spins about is the arrow."
 *
 * The stop the whole tour is built around, and the one where the picture has to argue against
 * something the viewer already believes. Everybody who meets curl for the first time decides it
 * means "how bendy the arrows are". It does not, and the two little demonstrations hung out to
 * port are here to break that belief rather than to decorate the instrument: a field whose arrows
 * visibly go round in circles and whose wheel does not move at all, next to a field whose arrows
 * are dead straight and parallel and whose wheel spins briskly. Curl is one side of a small wheel
 * being pushed harder than the other side. That correction is the stop.
 *
 * Three things are on screen, in one column to port so a viewer never has to look two ways:
 *
 *   THE INSTRUMENT, low and inboard, riding the craft. A six-bladed wheel on an axle at the end of
 *   a belly boom, hanging in the tour's OWN field. One blade is white, because a rate you cannot
 *   track is not a measurement. The axle rolls right through the cycle, and the wheel's rate goes
 *   with it: full speed one way when the axle lies on the curl, dead still square to it, full
 *   speed the other way when the axle is reversed. That is the hunt, and it is what makes curl an
 *   arrow rather than a number.
 *
 *   THE RING, above: an irrotational vortex, v ∝ 1/r. The arrows curl round, the wheel is CARRIED
 *   round the loop, and its white blade points at the same place the whole way. Being swung round
 *   the circle turns it one way; sitting with its inner blade in faster water than its outer turns
 *   it the other; here the two are exactly equal and cancel. This is the counterexample.
 *
 *   THE SHEAR, below: v = (a + ky, 0). Every arrow straight, every arrow parallel, nothing curved
 *   anywhere — and the wheel goes round hard, because the top blade is in quicker water than the
 *   bottom one.
 *
 * WHAT IS MEASURED AND WHAT IS STAGED, because the two are not the same here and the crew says so
 * out loud. The instrument's answer is genuinely differenced out of [SceneKit.fieldAt]: six field
 * samples round the hub every frame give the full curl vector, and the axle's roll starts ON that
 * measured vector, so if the tour's field is ever retuned the rod re-aims itself and this file is
 * not touched. The two demonstrations are the scene's own flat fields, written down here, and
 * their curls are written down with them rather than differenced — a central difference on a 1/r²
 * field is not exactly zero, and a "no curl" wheel that crept round by a few degrees a minute
 * would say the precise opposite of what it is there to say.
 *
 * The wheels turn about five times faster than the fields would really turn them, in the same way
 * the compass needle at V-4 is five times longer than the gradient. The RATIOS are honest — dead
 * still is dead still, and the shear against the instrument's best axis is right — and the number
 * that can be trusted is in the readout.
 *
 * ON LOOPING. A scene is a pure function of the clock, so the wheel's ANGLE has to be an integral
 * that closes: the axle rolls a half turn, from the curl to square to it to the reverse, its rate
 * is |∇×v|cos(A)/2, and A's schedule is antisymmetric about the middle of the cycle — so the
 * integral of the rate over one cycle is exactly zero and the white blade comes home. The integral
 * itself is a small cumulative table built once, not a loop run every frame.
 */
object ScenePaddleWheel : MathScene {

    /** Wide: the axle needs most of the approach to roll through its half turn. */
    override val reach = 1.6f

    /**
     * Not depth of geometry but depth of ATTACHMENT. The instrument travels with the craft, and
     * the renderer culls a landmark on the distance from its NODE, so a scene that rides the
     * camera would be switched off halfway through its own pass unless it declares itself deep.
     */
    override val deep = 1.7f

    private const val TAU = 6.2831853f
    private const val HALF_TURN = 3.14159265f
    private const val DEG = 57.29578f
    private const val PERIOD = 26f

    // ------------------------------------------------------------------- the instrument
    /**
     * Where the wheel hangs off the craft, and the one set of numbers here that was argued over.
     * The curl of this tour's field points very nearly back along the rail, so the axle at rest
     * points at the eye — and an arrow aimed at you is a dot. Pushing the hub two units to port
     * swings the axle about forty degrees off the line of sight, which is enough to read it as an
     * arrow and still leaves the disc near enough face-on to watch it spin. Down eight tenths and
     * forward under three keeps it out of the telemetry block at the top and the caption box at
     * the bottom without dropping it under the keel where nobody looks.
     */
    private const val HUB_SIDE = -1.95f
    private const val HUB_UP = -0.80f
    private const val HUB_AHEAD = 2.70f
    private const val WHEEL_R = 0.50f
    private const val AXLE = WHEEL_R * 0.90f
    private const val BLADES = 6
    private const val SPIN_GAIN = 5f          // display turns per real turn — see the header
    private const val ARROW_GAIN = 1.6f       // world units of arrow per rad/s of spin
    private const val EPS = 0.22f             // central-difference step for the curl

    /**
     * The second axis of the roll: up, and further to port. Straight up would be the obvious
     * choice and is the wrong one — at the square-on point the axle would lie along the boom that
     * carries it, and the one moment the scene most needs to be legible is the moment it stops.
     */
    private const val PERP_S = -0.66f
    private const val PERP_U = 0.75f

    /** Held at each end of the cycle, so both the on-axis states can be looked at. */
    private const val HOLD = 0.17f
    private const val NS = 64

    // ------------------------------------------------------------------- the demonstrations
    private const val DEMO_SIDE = -2.60f
    private const val RING_UP = 0.70f
    private const val SHEAR_UP = -0.70f
    private const val PATCH_R = 0.68f
    private const val DEMO_WHEEL_R = 0.17f
    private const val DEMO_GAIN = 19f

    private const val RING = 0
    private const val SHEAR = 1

    /** v = K(−q, p)/r². Speed K/r: quick near the middle, slow at the rim, curl zero throughout. */
    private const val RING_K = 0.048f
    private const val RING_R0 = 0.24f
    private const val RING_DR = 0.18f
    private const val RING_ORBIT = RING_R0 + RING_DR    // the wheel rides the middle streamline
    private const val RING_CURL = 0f

    /** v = (a + kq, 0). Straight and parallel everywhere; curl is −k everywhere. */
    private const val SHEAR_BASE = 0.26f
    private const val SHEAR_K = 0.30f
    private const val SHEAR_CURL = -SHEAR_K
    private const val SHEAR_SCALE = 0.70f

    // Rows clear of the hub, columns short enough that the longest arrow stops inside the rim.
    private val SHEAR_Q = floatArrayOf(-0.50f, -0.22f, 0.22f, 0.50f)
    private val SHEAR_P = floatArrayOf(-0.58f, -0.30f, -0.02f, 0.26f)

    // ------------------------------------------------------------------- scratch
    private val f = FloatArray(12)
    private val gs = FloatArray(12)           // the instrument's stage, its origin AT the hub
    private val gn = FloatArray(12)           // the node's stage, which the demonstrations pin to
    private val fp = FloatArray(3)
    private val fm = FloatArray(3)
    private val cw = FloatArray(3)            // the measured curl at the hub
    private val a0 = FloatArray(3)            // ... normalised: the axis the wheel is hunting for
    private val pw = FloatArray(3)            // the roll's second axis, square to a0
    private val ax = FloatArray(3)            // the axle right now
    private val b1 = FloatArray(3)            // the disc's basis, both square to the axle
    private val b2 = FloatArray(3)
    private val pv = FloatArray(2)            // a flat demonstration field's value
    private val tint = FloatArray(3)
    private val tvv = IntArray(1)

    // The HUD builds on the UI thread, not the GL thread, so it gets its own three arrays rather
    // than racing draw() for these. Same reasoning as the compass at V-4.
    private val hp = FloatArray(3)
    private val hm = FloatArray(3)
    private val hc = FloatArray(3)

    /** ∫cos(A(u))du from 0, sampled once. Its last entry is zero, which is why the loop closes. */
    private val cum = FloatArray(NS + 1)
    private var built = false

    // ------------------------------------------------------------------- the roll

    /** The axle's angle off the measured curl: held at 0, a smooth half turn, held at 180°. */
    private fun rollAngle(u: Float): Float =
        HALF_TURN * SceneParts.ease((u - HOLD) / (1f - 2f * HOLD))

    private fun build() {
        if (built) return
        var s = 0f
        cum[0] = 0f
        for (k in 1..NS) {
            s += cos(rollAngle((k - 0.5f) / NS)) / NS
            cum[k] = s
        }
        built = true
    }

    private fun cumAt(c: Float): Float {
        val x = (c * NS).coerceIn(0f, NS.toFloat())
        val k = x.toInt().coerceIn(0, NS - 1)
        return cum[k] + (cum[k + 1] - cum[k]) * (x - k)
    }

    // ------------------------------------------------------------------- measuring

    /**
     * ∇ × v at a world point, by central differences on the tour's own field. Six samples buy all
     * nine partials, and the step is a fifth of a unit — small against the field's features, large
     * enough that the difference is measuring the field rather than the float format.
     */
    private fun curlAt(
        kit: SceneKit, x: Float, y: Float, z: Float,
        p: FloatArray, m: FloatArray, out: FloatArray
    ) {
        val d = 1f / (2f * EPS)
        kit.fieldAt(x + EPS, y, z, p); kit.fieldAt(x - EPS, y, z, m)
        val dvydx = (p[1] - m[1]) * d
        val dvzdx = (p[2] - m[2]) * d
        kit.fieldAt(x, y + EPS, z, p); kit.fieldAt(x, y - EPS, z, m)
        val dvxdy = (p[0] - m[0]) * d
        val dvzdy = (p[2] - m[2]) * d
        kit.fieldAt(x, y, z + EPS, p); kit.fieldAt(x, y, z - EPS, m)
        val dvxdz = (p[0] - m[0]) * d
        val dvydz = (p[1] - m[1]) * d
        out[0] = dvzdy - dvydz
        out[1] = dvxdz - dvzdx
        out[2] = dvydx - dvxdy
    }

    /**
     * The reading, and the reason it is worth a HUD line: the rate is what the whole stop turns
     * on, and a wheel deliberately geared up so it can be SEEN cannot also be read off.
     *
     * Taken at the keel rather than at the hub two units off it — the hub needs [SceneKit.frame],
     * which goes through renderer-owned temporaries, and this tour's curl varies by under a
     * percent over that distance. The rod's schedule starts it on the measured curl, so the
     * component along it is exactly |∇×v| cos A.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasField) return null
        curlAt(kit, kit.shipX, kit.shipY, kit.shipZ, hp, hm, hc)
        val mag = sqrt(hc[0] * hc[0] + hc[1] * hc[1] + hc[2] * hc[2])
        val a = rollAngle(SceneParts.cycle(kit.seconds, PERIOD))
        return "|∇×v| %.2f   ROD %.0f°   SPIN %+.2f".format(
            Locale.US, mag, a * DEG, 0.5f * mag * cos(a)
        )
    }

    // ------------------------------------------------------------------- the flat fields

    /** One of the two staged demonstrations, in its patch's own (p, q) coordinates. */
    private fun field2(mode: Int, p: Float, q: Float, out: FloatArray) {
        if (mode == RING) {
            val r2 = p * p + q * q
            if (r2 < 1e-5f) { out[0] = 0f; out[1] = 0f; return }
            out[0] = -q * RING_K / r2
            out[1] = p * RING_K / r2
        } else {
            out[0] = SHEAR_BASE + SHEAR_K * q
            out[1] = 0f
        }
    }

    // ------------------------------------------------------------------- drawing

    /** The tour's cool-to-warm speed ramp, so a faster arrow is a warmer one, as in the ambient. */
    private fun tintFor(t: Float) {
        val c = SceneParts.COOL
        val w = SceneParts.WORK
        tint[0] = c[0] + (w[0] - c[0]) * t
        tint[1] = c[1] + (w[1] - c[1]) * t
        tint[2] = c[2] + (w[2] - c[2]) * t
    }

    /**
     * A paddle wheel: [BLADES] flat blades in planes CONTAINING the axle, plus the rim.
     *
     * Every other count in this scene is stepped by the thermal governor and this one is not. A
     * blade is not decoration, it is the object — drop two and it is a different instrument — and
     * blades cost buffer vertices rather than draw calls, which is what the budget is about.
     * Seen down the axle the blades collapse to six spokes, which is the view you get when the
     * rate is highest and is the easiest of all to read a rotation from.
     */
    private fun wheelAt(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        cx: Float, cy: Float, cz: Float,
        axx: Float, axy: Float, axz: Float,
        u1x: Float, u1y: Float, u1z: Float,
        u2x: Float, u2y: Float, u2z: Float,
        r: Float, spin: Float, alpha: Float, segs: Int
    ): Int {
        var k = lv
        val rIn = r * 0.26f
        val half = r * 0.30f
        val wx = axx * half * 2f; val wy = axy * half * 2f; val wz = axz * half * 2f
        for (b in 0 until BLADES) {
            val a = spin + b * TAU / BLADES
            val ca = cos(a); val sa = sin(a)
            val dx = u1x * ca + u2x * sa
            val dy = u1y * ca + u2y * sa
            val dz = u1z * ca + u2z * sa
            // One blade is white and stays white. "Keep your eye on the white blade" is the whole
            // method of the ring demonstration, where the thing to be seen is an absence of motion.
            val col = if (b == 0) SceneParts.HOT else SceneParts.STEEL
            val al = if (b == 0) alpha else alpha * 0.70f
            k = SceneParts.pane(
                kit, line, k, tri, tvv,
                cx + dx * rIn - axx * half, cy + dy * rIn - axy * half, cz + dz * rIn - axz * half,
                dx * (r - rIn), dy * (r - rIn), dz * (r - rIn),
                wx, wy, wz, col, al, 0, 0
            )
        }
        val st = SceneParts.STEEL
        return MathMesh.arc(
            line, k, cx, cy, cz, u1x, u1y, u1z, u2x, u2y, u2z,
            r, 0f, TAU, segs, st[0], st[1], st[2], alpha * 0.75f
        )
    }

    /**
     * One flat demonstration: its rim, its streamlines, its arrows and its wheel, all built in the
     * node's stage plane so the whole thing faces the craft on the approach.
     */
    private fun patch(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        mode: Int, cSide: Float, cUp: Float, seconds: Float
    ): Int {
        var k = lv
        val q = kit.quality
        val px = gn[3]; val py = gn[4]; val pz = gn[5]      // the patch's p axis
        val qx = gn[6]; val qy = gn[7]; val qz = gn[8]      // ... and its q axis
        val nx = gn[9]; val ny = gn[10]; val nz = gn[11]    // ... and its normal, the rail forward
        val cx = gn[0] + px * cSide + qx * cUp
        val cy = gn[1] + py * cSide + qy * cUp
        val cz = gn[2] + pz * cSide + qz * cUp
        val segs = if (q == 0) 24 else 12
        val ch = SceneParts.CHALK
        val co = SceneParts.COOL

        // The rim, which frames this as a specimen rather than as part of the corridor.
        k = MathMesh.arc(
            line, k, cx, cy, cz, px, py, pz, qx, qy, qz,
            PATCH_R, 0f, TAU, segs, ch[0], ch[1], ch[2], 0.30f
        )

        var hubP = 0f
        var hubQ = 0f
        if (mode == RING) {
            val rings = if (q == 0) 3 else 2
            val perRing = if (q == 0) 8 else 5
            // The streamlines the arrows are tangent to. Without them "the arrows go round in a
            // circle" is something a viewer has to assemble; with them it is simply true.
            if (q < 2) {
                for (ri in 0 until rings) {
                    k = MathMesh.arc(
                        line, k, cx, cy, cz, px, py, pz, qx, qy, qz,
                        RING_R0 + ri * RING_DR, 0f, TAU, segs, co[0], co[1], co[2], 0.22f
                    )
                }
            }
            for (ri in 0 until rings) {
                val r = RING_R0 + ri * RING_DR
                val t = if (rings > 1) 1f - ri.toFloat() / (rings - 1) else 1f
                tintFor(t)
                for (j in 0 until perRing) {
                    val a = j * TAU / perRing + ri * 0.4f
                    val p = r * cos(a); val qq = r * sin(a)
                    field2(RING, p, qq, pv)
                    k = arrowIn(line, k, cx, cy, cz, p, qq, pv[0], pv[1], 1f,
                        px, py, pz, qx, qy, qz, nx, ny, nz, 0.55f + 0.35f * t)
                }
            }
            // Carried round the loop at the field's own speed — this one is not geared up, because
            // the point is that the wheel travels while the white blade does not turn.
            val orbit = RING_K / (RING_ORBIT * RING_ORBIT) * seconds
            hubP = RING_ORBIT * cos(orbit)
            hubQ = RING_ORBIT * sin(orbit)
        } else {
            val rows = if (q == 0) SHEAR_Q.size else 2
            val cols = if (q == 0) SHEAR_P.size else 2
            if (q < 2) {
                for (j in 0 until rows) {
                    val qq = SHEAR_Q[if (rows == SHEAR_Q.size) j else j * 3]
                    val w = PATCH_R * 0.92f
                    k = MathMesh.segment(
                        line, k,
                        cx - px * w + qx * qq, cy - py * w + qy * qq, cz - pz * w + qz * qq,
                        cx + px * w + qx * qq, cy + py * w + qy * qq, cz + pz * w + qz * qq,
                        co[0], co[1], co[2], 0.18f
                    )
                }
            }
            for (j in 0 until rows) {
                val qq = SHEAR_Q[if (rows == SHEAR_Q.size) j else j * 3]
                // Warm at the top, cool at the bottom: the tint IS the argument here. One side of
                // the wheel is in quicker water than the other, and that is the whole of it.
                val t = (qq - SHEAR_Q[0]) / (SHEAR_Q[SHEAR_Q.size - 1] - SHEAR_Q[0])
                tintFor(t)
                for (ii in 0 until cols) {
                    val p = SHEAR_P[if (cols == SHEAR_P.size) ii else ii * 2]
                    field2(SHEAR, p, qq, pv)
                    k = arrowIn(line, k, cx, cy, cz, p, qq, pv[0], pv[1], SHEAR_SCALE,
                        px, py, pz, qx, qy, qz, nx, ny, nz, 0.55f + 0.35f * t)
                }
            }
        }

        // The wheel. Its rate is the patch's curl, halved — a small wheel turns at half the curl —
        // and geared up so it can be seen. Zero times any gain is still exactly zero.
        val curl = if (mode == RING) RING_CURL else SHEAR_CURL
        val spin = DEMO_GAIN * 0.5f * curl * seconds
        return wheelAt(
            kit, line, k, tri,
            cx + px * hubP + qx * hubQ, cy + py * hubP + qy * hubQ, cz + pz * hubP + qz * hubQ,
            nx, ny, nz, px, py, pz, qx, qy, qz,
            DEMO_WHEEL_R, spin, 1f, if (q == 0) 14 else 8
        )
    }

    /** One arrow of a flat patch, given its foot and its vector in patch coordinates. */
    private fun arrowIn(
        line: FloatArray, at: Int,
        cx: Float, cy: Float, cz: Float, p: Float, q: Float, vp: Float, vq: Float, scale: Float,
        px: Float, py: Float, pz: Float, qx: Float, qy: Float, qz: Float,
        nx: Float, ny: Float, nz: Float, alpha: Float
    ): Int {
        val x = cx + px * p + qx * q
        val y = cy + py * p + qy * q
        val z = cz + pz * p + qz * q
        // Barbs in the patch's own plane, not the plane facing the eye: these arrows are a picture
        // of a flat field and they should stay flat even when the patch is seen at an angle.
        return MathMesh.arrow(
            line, at, x, y, z,
            (px * vp + qx * vq) * scale, (py * vp + qy * vq) * scale, (pz * vp + qz * vq) * scale,
            nx, ny, nz, tint[0], tint[1], tint[2], alpha, 0.38f
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No field callback, no curl to hunt: this stop is meaningless off Tour VI.
        if (!kit.hasField) return
        build()

        val q = kit.quality
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tvv[0] = 0
        val c = SceneParts.cycle(kit.seconds, PERIOD)

        // --- the two frames ------------------------------------------------------------------
        // The demonstrations are pinned to the node and stay where they were put; the instrument
        // rides the craft, so its frame is built from the ship's own position rather than from the
        // rail centre the ship is swaying about.
        SceneParts.stage(kit, i.toFloat(), 0f, 0f, f, gn)
        kit.frame(kit.progress, f)
        gs[3] = f[6]; gs[4] = f[7]; gs[5] = f[8]
        gs[6] = f[9]; gs[7] = f[10]; gs[8] = f[11]
        gs[9] = f[3]; gs[10] = f[4]; gs[11] = f[5]
        gs[0] = kit.shipX + gs[3] * HUB_SIDE + gs[6] * HUB_UP + gs[9] * HUB_AHEAD
        gs[1] = kit.shipY + gs[4] * HUB_SIDE + gs[7] * HUB_UP + gs[10] * HUB_AHEAD
        gs[2] = kit.shipZ + gs[5] * HUB_SIDE + gs[8] * HUB_UP + gs[11] * HUB_AHEAD

        // --- the measurement -------------------------------------------------------------------
        curlAt(kit, gs[0], gs[1], gs[2], fp, fm, cw)
        val mag = sqrt(cw[0] * cw[0] + cw[1] * cw[1] + cw[2] * cw[2])
        if (mag > 1e-5f) {
            a0[0] = cw[0] / mag; a0[1] = cw[1] / mag; a0[2] = cw[2] / mag
        } else {
            // A field with no curl anywhere still gets an instrument, pointed along the rail and
            // sitting dead still, which is the correct reading rather than a missing one.
            a0[0] = gs[9]; a0[1] = gs[10]; a0[2] = gs[11]
        }
        // The roll's second axis: up and to port, with the part along the curl taken back out so
        // the two really are square and the cos(A) rate is the truth rather than nearly it.
        SceneParts.vec(gs, PERP_S, PERP_U, 0f, pw)
        val d = pw[0] * a0[0] + pw[1] * a0[1] + pw[2] * a0[2]
        pw[0] -= a0[0] * d; pw[1] -= a0[1] * d; pw[2] -= a0[2] * d
        val pl = sqrt(pw[0] * pw[0] + pw[1] * pw[1] + pw[2] * pw[2])
        if (pl > 1e-4f) {
            pw[0] /= pl; pw[1] /= pl; pw[2] /= pl
        } else {
            pw[0] = gs[3]; pw[1] = gs[4]; pw[2] = gs[5]
        }

        val roll = rollAngle(c)
        val cr = cos(roll); val sr = sin(roll)
        ax[0] = a0[0] * cr + pw[0] * sr
        ax[1] = a0[1] * cr + pw[1] * sr
        ax[2] = a0[2] * cr + pw[2] * sr
        // The disc's basis. b1 is square to the whole roll plane and so is square to every axle
        // the cycle produces; b2 closes the frame.
        b1[0] = a0[1] * pw[2] - a0[2] * pw[1]
        b1[1] = a0[2] * pw[0] - a0[0] * pw[2]
        b1[2] = a0[0] * pw[1] - a0[1] * pw[0]
        b2[0] = ax[1] * b1[2] - ax[2] * b1[1]
        b2[1] = ax[2] * b1[0] - ax[0] * b1[2]
        b2[2] = ax[0] * b1[1] - ax[1] * b1[0]

        val rate = 0.5f * mag * cr                       // the true rate, rad/s
        val spin = SPIN_GAIN * mag * 0.5f * PERIOD * cumAt(c)

        // --- the instrument ----------------------------------------------------------------------
        v = wheelAt(
            kit, line, v, tri, gs[0], gs[1], gs[2],
            ax[0], ax[1], ax[2], b1[0], b1[1], b1[2], b2[0], b2[1], b2[2],
            WHEEL_R, spin, 1f, if (q == 0) 26 else 14
        )

        // The eye-to-hub vector, so both arrow heads have their barbs in the plane across the line
        // of sight and read from wherever the viewer happens to be looking.
        val ex = gs[0] - kit.camX
        val ey = gs[1] - kit.camY
        val ez = gs[2] - kit.camZ

        // The answer, standing still: the measured curl, drawn at the hub for the whole cycle.
        // The axle swings off it and back onto it, and that is the hunt made into a picture.
        if (q < 2) {
            val ad = SceneParts.ADDED
            val gl = mag * ARROW_GAIN * 0.5f
            v = MathMesh.arrow(
                line, v, gs[0], gs[1], gs[2], a0[0] * gl, a0[1] * gl, a0[2] * gl,
                ex, ey, ez, ad[0], ad[1], ad[2], 0.45f, 0.30f
            )
        }
        // The reading, along the axle and signed: it grows to full length when the rod lies on the
        // arrow above, shrinks to nothing square to it, and comes back pointing the other way.
        // MathMesh.arrow declines to draw a zero-length vector, so "dead still" draws nothing,
        // which is exactly the right amount of arrow for no spin.
        val wl = rate * ARROW_GAIN
        val wk = SceneParts.WORK
        v = MathMesh.arrow(
            line, v, gs[0], gs[1], gs[2], ax[0] * wl, ax[1] * wl, ax[2] * wl,
            ex, ey, ez, wk[0], wk[1], wk[2], 0.95f, 0.30f
        )

        // --- the two demonstrations -----------------------------------------------------------
        v = patch(kit, line, v, tri, RING, DEMO_SIDE, RING_UP, kit.seconds)
        v = patch(kit, line, v, tri, SHEAR, DEMO_SIDE, SHEAR_UP, kit.seconds)

        kit.flushLines(v, 2.3f)
        kit.flushTris(tvv[0])

        // --- the solid parts ---------------------------------------------------------------------
        // The axle as a rod rather than a line: it is the centrepiece, it is what the ship is
        // rolling, and in stereo it should have a body.
        kit.rod(
            gs[0] - ax[0] * AXLE, gs[1] - ax[1] * AXLE, gs[2] - ax[2] * AXLE,
            gs[0] + ax[0] * AXLE, gs[1] + ax[1] * AXLE, gs[2] + ax[2] * AXLE,
            0.040f, SceneParts.STEEL, SceneParts.LAMP, 0.5f + kit.beat * 0.6f
        )

        // The belly boom. The measurement is a thing the ship physically does, at arm's length, in
        // front of you — that is what makes curl felt rather than defined. Thin, because two and a
        // half units of strut across the lower half of the view would compete with the wheel.
        if (kit.reach > 0.03f) {
            val t = kit.reach
            val bx = kit.shipX; val by = kit.shipY - 0.16f; val bz = kit.shipZ
            kit.rod(
                bx, by, bz,
                bx + (gs[0] - bx) * t, by + (gs[1] - by) * t, bz + (gs[2] - bz) * t,
                0.026f, SceneParts.STEEL, SceneParts.LAMP, 0.3f
            )
        }

        // The hub, which is also where the boom lands.
        kit.ball(
            gs[0], gs[1], gs[2], 0.085f, 0.085f, 0.085f,
            SceneParts.LAMP, SceneParts.HOT, 1f, 0f, 0f, 1f, 0f, 0f,
            0.9f + kit.beat * 1.6f
        )

        // --- notation -----------------------------------------------------------------------------
        // Every label is hung along the camera's own right, which is the only offset that is
        // reliably BESIDE a figure rather than above or below it — the telemetry block owns the top
        // of the eye and the caption box the bottom, and these three figures sit in a column.
        val rx = kit.camRightX; val ry = kit.camRightY; val rz = kit.camRightZ
        val gl = 0.20f
        var o = WHEEL_R + 0.26f
        kit.text(
            "∇×v", gs[0] + rx * o, gs[1] + ry * o, gs[2] + rz * o,
            gl * 1.1f, SceneParts.ADDED, 1f, GlyphBoard.Style.MATH, 1.2f, anchor = -0.5f
        )

        o = PATCH_R + 0.16f
        label(kit, "∇×v = 0", DEMO_SIDE, RING_UP, rx, ry, rz, o, gl, SceneParts.COOL, -0.5f)
        label(kit, "∇×v ≠ 0", DEMO_SIDE, SHEAR_UP, rx, ry, rz, o, gl, SceneParts.WORK, -0.5f)

        // What the two fields ARE, on their far sides, at full detail only. The names matter — the
        // whole trap is that one of them looks curly and the other does not.
        if (q == 0) {
            label(kit, "v ∝ 1/r", DEMO_SIDE, RING_UP, rx, ry, rz, -o, gl * 0.78f,
                SceneParts.CHALK, 0.5f)
            label(kit, "v = (a + ky, 0)", DEMO_SIDE, SHEAR_UP, rx, ry, rz, -o, gl * 0.78f,
                SceneParts.CHALK, 0.5f)
        }
    }

    /** A label beside a demonstration patch, offset along the camera's right by [off]. */
    private fun label(
        kit: SceneKit, s: String, cSide: Float, cUp: Float,
        rx: Float, ry: Float, rz: Float, off: Float,
        height: Float, tintCol: FloatArray, anchor: Float
    ) {
        val x = gn[0] + gn[3] * cSide + gn[6] * cUp + rx * off
        val y = gn[1] + gn[4] * cSide + gn[7] * cUp + ry * off
        val z = gn[2] + gn[5] * cSide + gn[8] * cUp + rz * off
        kit.text(s, x, y, z, height, tintCol, 1f, GlyphBoard.Style.MATH, 1.1f, anchor = anchor)
    }
}
