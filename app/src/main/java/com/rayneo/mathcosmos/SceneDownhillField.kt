package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR VI, stop 5 — THE DOWNHILL FIELD. "Some fields are just a landscape's uphill arrows, and
 * then a round trip costs exactly nothing."
 *
 * Tour V's country comes back as a ghost, a lattice of arrows lies flat on it all pointing uphill,
 * and a probe is sent round a closed circuit while a work meter fills and empties and comes home
 * to zero. Then round a completely different circuit from the same gate, and home to zero again.
 * Then the crew switches on the shear from stop 4, the landscape stutters and fails to draw, the
 * probe flies the FIRST circuit again — and the meter climbs the whole way round and stays up.
 *
 * THE ONE DECISION THAT CARRIES THE STOP is that the meter does not integrate anything while the
 * field is a gradient. It reads the bead's HEIGHT above the gate. That is not a shortcut, it is
 * the theorem: the work done against an uphill field between two places is the difference in
 * height between them, so a bar driven by the height cannot fail to come home, for the same
 * reason the mathematics cannot. The shear branch has no such luck — there is no height to read,
 * so that one has to be integrated, segment by segment, and it is the ONLY branch in this file
 * that adds anything up. The asymmetry in the code is the asymmetry in the subject, and if you
 * ever find yourself tempted to unify the two paths, that is the moment the stop stops teaching.
 *
 * Both loops leave from and return to the SAME gate. Two different routes home from one place is
 * what path independence actually says, and it is much harder to see if the two circuits are also
 * in two different parts of the country.
 *
 * WHY THE SHEAR IS DEFINED HERE and not taken from [SceneKit.fieldAt]. The tour's own field is a
 * swirl about the RAIL — its curl is about 0.9 pointing along the passage — so a circuit lying
 * flat on the country slices almost none of it: the honest number for this loop is a circulation
 * of about 1.0 buried inside a running total that swings past 12 on the way round, because the
 * tour field also has a strong drift along z. A bar that thrashes to full scale and lands 8 per
 * cent off zero teaches nothing. The shear the crew names — all arrows dead straight and parallel,
 * only their LENGTH changing across the flow, exactly the counterexample from THE PADDLE WHEEL —
 * gives a total that climbs monotonically from nothing to about 2.5 and never once comes back.
 * A surplus a viewer cannot see would be a worse lie than a field written down in this file.
 * The ambient scene goes on drawing the tour's real field around all of this, as it does all ride.
 *
 * The shear has no vertical component, so the fact that the circuit rides up and down over the
 * hills contributes exactly nothing to its total. The surplus is honest two-dimensional
 * circulation and not an artefact of the warp.
 *
 * WHAT IS BAKED. The country does not move and neither do the circuits, so the first frame samples
 * every terrain height, both loops, all three work tables and the whole arrow lattice, and every
 * frame after that is arithmetic on arrays. On a headset that reboots when it gets warm, three
 * hundred trigonometric terrain evaluations a frame is not a style question.
 *
 * TWO HONEST APPROXIMATIONS, both of which the crew says out loud.
 *
 * The uphill arrows are drawn HORIZONTAL, at the ground height under their own base. The gradient
 * is a vector in the flat domain underneath the hillside, not a vector on it, so an arrow's tip
 * often ends up buried in the slope ahead of it or hanging over the slope behind. THE COMPASS
 * draws the same crossing deliberately, with a plumb line, and this stop inherits the convention.
 *
 * And the craft cannot leave the rail, so it does not fly the loop — it puts a probe on the ground
 * and watches. The arm reaches out at this stop (the tour's armStops has 4.05 for exactly this),
 * so the boom is drawn when it does.
 *
 * The notation avoids the closed-contour integral sign. No scene in the app has yet trusted that
 * glyph to whatever font the device rasterises labels with, and a tofu box where the whole claim
 * should be is not a risk worth taking for one character; a subscripted integral says the same
 * thing in symbols the tour has already proved it can draw.
 *
 * This scene extends well past the passage wall, and is supposed to. Tour VI drops its wall alpha
 * to 0.18 so the tube is a guide-rail and the country outside it can be seen through it.
 *
 * Budget: one flushLines of about 1100 vertices, one flushTris, one bead, one boom, three labels.
 */
object SceneDownhillField : MathScene {

    /** A twelve-unit patch of country needs to be there before you are alongside it. */
    override val reach = 1.7f

    /** The patch runs six units up the rail past its own node; do not cull it at the origin. */
    override val deep = 0.6f

    private const val TAU = 6.2831853f
    private const val PERIOD = 30f

    // ---- where the figure stands ---------------------------------------------------------------
    private const val SIDE_OUT = -5.0f      // the circuit's centre, out to port of the rail
    private const val PATCH = 6.4f          // radius of the visible clearing of country
    private const val EDGE = 0.32f          // the outer third of it is the fade band
    private const val NS = 13               // terrain samples across, at quality 0
    private const val GHOST = 0.30f         // the country is a ghost here; the field is the subject
    private const val LIFT = 0.05f          // clear of the mesh, so nothing z-fights

    // ---- the uphill arrows ---------------------------------------------------------------------
    private const val AN = 5                // 5 x 5 at quality 0, 3 x 3 once the governor steps in
    private const val ASPAN = 4.0f
    private const val EPS = 0.30f           // central-difference step, a tenth of the smallest hill
    private const val GAIN = 2.6f           // world units of arrow per unit of |∇f|
    private const val ARROW_MIN = 0.22f     // a stub where the ground is flat, rather than nothing
    private const val ARROW_MAX = 1.25f

    // ---- the shear that is not a gradient -------------------------------------------------------
    private const val SHEAR_C = 0.12f       // strength per unit across the flow
    private const val SHEAR_GAIN = 1.9f     // drawn length per unit of field, as above

    // ---- the two circuits -------------------------------------------------------------------------
    private const val LN = 48
    private const val R_A = 2.6f
    private const val LOBE2 = 0.34f         // both lobe terms vanish at t = 0 and t = 2π, so the
    private const val LOBE3 = 0.14f         // wandering circuit leaves from the same gate as the circle

    // ---- the work meter ---------------------------------------------------------------------------
    private const val MAST_SIDE = -2.75f    // beside the gate: the bar is at zero when the probe is here
    private const val MAST_AHEAD = 0.6f
    private const val MAST_FOOT = 0.30f
    private const val MAST_H = 2.5f
    private const val BAR_MAX = MAST_H * 0.5f
    private const val WORK_FULL = 2.8f      // work at full deflection
    private const val BAR_SCALE = BAR_MAX / WORK_FULL
    private const val BAR_W = 0.11f

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree with the picture ----
    private const val LAP_A = 0.05f
    private const val LAP_B = 0.34f
    private const val LAP_LEN = 0.22f
    private const val SWITCH_AT = 0.64f
    private const val SWITCH_LEN = 0.05f
    private const val LAP_S = 0.69f
    private const val LAP_S_LEN = 0.20f

    /** 0 = the circle under the gradient, 1 = the ramble under it, 2 = the circle under the shear. */
    private fun mode(c: Float): Int = if (c < 0.30f) 0 else if (c < SWITCH_AT) 1 else 2

    /** How far round the current circuit the probe is, 0..1. Rests at 1 so the answer can be read. */
    private fun lap(c: Float): Float = when {
        c < 0.30f -> ((c - LAP_A) / LAP_LEN).coerceIn(0f, 1f)
        c < SWITCH_AT -> ((c - LAP_B) / LAP_LEN).coerceIn(0f, 1f)
        else -> ((c - LAP_S) / LAP_S_LEN).coerceIn(0f, 1f)
    }

    // ---- everything below is filled once, on the first frame ---------------------------------------
    private var built = false
    private var cX = 0f
    private var cZ = 0f
    private var sX = 1f                     // horizontal side unit vector of the rail here
    private var sZ = 0f
    private var hX = 0f                     // horizontal heading unit vector
    private var hZ = -1f
    private var loY = 0f                    // lowest ground in the clearing, for the colour ramp
    private var spanY = 1f
    private var mastX = 0f
    private var mastZ = 0f
    private var mastY = 0f                  // foot of the mast
    private var zeroY = 0f                  // the meter's zero line
    private var claimX = 0f
    private var claimY = 0f
    private var claimZ = 0f
    private var wX = 0f
    private var wZ = 0f
    private var labX = 0f
    private var labY = 0f
    private var labZ = 0f

    private val fr = FloatArray(12)
    private val tint = FloatArray(3)
    private val gx = FloatArray(NS * NS)
    private val gy = FloatArray(NS * NS)
    private val gz = FloatArray(NS * NS)
    private val ga = FloatArray(NS * NS)    // rim mask, zero at the edge of the clearing
    private val arX = FloatArray(AN * AN)
    private val arY = FloatArray(AN * AN)
    private val arZ = FloatArray(AN * AN)
    private val uhX = FloatArray(AN * AN)   // the uphill arrow, already scaled to world length
    private val uhZ = FloatArray(AN * AN)
    private val shX = FloatArray(AN * AN)   // and the shear arrow at the same place
    private val shZ = FloatArray(AN * AN)
    private val pa = FloatArray((LN + 1) * 3)
    private val pb = FloatArray((LN + 1) * 3)
    private val wa = FloatArray(LN + 1)     // work round the circle: read off the height
    private val wb = FloatArray(LN + 1)     // work round the ramble: read off the height
    private val sa = FloatArray(LN + 1)     // work round the circle under the shear: integrated

    /**
     * Where the figure stands, and every number that can be worked out before the ride starts.
     *
     * The clearing and both circuits are laid out in the RAIL's horizontal side and heading, not
     * in world x and z — rule seven, and it costs nothing here because the patch never moves, so
     * none of the swimming that forces the tour-wide country mesh onto a world lattice can happen.
     * But the terrain is sampled, and the gradient differenced, in world x and z, because that is
     * the domain [SceneKit.terrainHeight] is a function of and a slope measured in any other frame
     * would be a slope the country has never heard of.
     */
    private fun build(kit: SceneKit, i: Int) {
        if (built) return
        kit.frame(i.toFloat(), fr)
        var ax = fr[6]; var az = fr[8]
        var l = sqrt(ax * ax + az * az)
        if (l > 1e-4f) { ax /= l; az /= l } else { ax = 1f; az = 0f }
        sX = ax; sZ = az
        var bx = fr[3]; var bz = fr[5]
        l = sqrt(bx * bx + bz * bz)
        if (l > 1e-4f) { bx /= l; bz /= l } else { bx = 0f; bz = -1f }
        hX = bx; hZ = bz
        cX = fr[0] + sX * SIDE_OUT
        cZ = fr[2] + sZ * SIDE_OUT
        val ground = SceneAmbientCountry.GROUND_Y

        // ---- the clearing ---------------------------------------------------------------------
        var lo = 1e9f
        var hi = -1e9f
        for (j in 0 until NS) {
            for (k in 0 until NS) {
                val a = -PATCH + 2f * PATCH * k / (NS - 1)
                val b = -PATCH + 2f * PATCH * j / (NS - 1)
                val x = cX + sX * a + hX * b
                val z = cZ + sZ * a + hZ * b
                val idx = j * NS + k
                gx[idx] = x
                gz[idx] = z
                gy[idx] = ground + kit.terrainHeight(x, z)
                // A round clearing rather than a square one: a rectangle of ground ending in a
                // corner announces that the country is a texture with an edge.
                val rn = sqrt(a * a + b * b) / PATCH
                ga[idx] = ((1f - rn) / EDGE).coerceIn(0f, 1f)
                if (ga[idx] > 0.01f) {
                    if (gy[idx] < lo) lo = gy[idx]
                    if (gy[idx] > hi) hi = gy[idx]
                }
            }
        }
        loY = lo
        spanY = if (hi - lo > 1e-3f) hi - lo else 1f

        // ---- the arrows -----------------------------------------------------------------------
        for (j in 0 until AN) {
            for (k in 0 until AN) {
                val a = -ASPAN + 2f * ASPAN * k / (AN - 1)
                val b = -ASPAN + 2f * ASPAN * j / (AN - 1)
                val x = cX + sX * a + hX * b
                val z = cZ + sZ * a + hZ * b
                val idx = j * AN + k
                arX[idx] = x
                arZ[idx] = z
                arY[idx] = ground + kit.terrainHeight(x, z) + LIFT
                val dfx = (kit.terrainHeight(x + EPS, z) - kit.terrainHeight(x - EPS, z)) / (2f * EPS)
                val dfz = (kit.terrainHeight(x, z + EPS) - kit.terrainHeight(x, z - EPS)) / (2f * EPS)
                val m = sqrt(dfx * dfx + dfz * dfz)
                val len = (GAIN * m).coerceIn(ARROW_MIN, ARROW_MAX)
                if (m > 1e-5f) {
                    uhX[idx] = dfx / m * len
                    uhZ[idx] = dfz / m * len
                }
                // The shear: along the side axis, strength growing with distance across the flow,
                // reversing through the middle. Straight, parallel, and not the gradient of
                // anything — which is the entire point of switching it on.
                val f = -SHEAR_C * b * SHEAR_GAIN
                shX[idx] = sX * f
                shZ[idx] = sZ * f
            }
        }

        // ---- the two circuits, both leaving from the gate at t = 0 ------------------------------
        for (k in 0..LN) {
            val t = TAU * k / LN
            val ct = cos(t)
            val st = sin(t)
            seat(pa, k, R_A * ct, R_A * st, ground, kit)
            val r = R_A * (1f + LOBE2 * sin(2f * t) + LOBE3 * sin(3f * t))
            seat(pb, k, r * ct, r * st, ground, kit)
        }
        // The last point is COPIED from the first rather than evaluated at t = 2π. A tenth of a
        // millimetre of floating-point drift would leave the circuit with a hairline gap in it and
        // the work table ending at 0.0000001 instead of nothing at all, and this stop is entirely
        // about a quantity that comes home exactly.
        for (d in 0 until 3) {
            pa[LN * 3 + d] = pa[d]
            pb[LN * 3 + d] = pb[d]
        }

        // ---- the work tables ---------------------------------------------------------------------
        // Under a gradient field the work is the height gained, so these two tables are the bead's
        // own altitude above the gate and nothing else. LIFT cancels in the subtraction.
        for (k in 0..LN) {
            wa[k] = pa[k * 3 + 1] - pa[1]
            wb[k] = pb[k * 3 + 1] - pb[1]
        }
        // The shear has no potential to read, so this one is a line integral: midpoint rule, one
        // term per segment of the circuit. It is the only sum in the file.
        sa[0] = 0f
        for (k in 0 until LN) {
            val x0 = pa[k * 3]; val z0 = pa[k * 3 + 2]
            val x1 = pa[(k + 1) * 3]; val z1 = pa[(k + 1) * 3 + 2]
            val mx = (x0 + x1) * 0.5f - cX
            val mz = (z0 + z1) * 0.5f - cZ
            val b = mx * hX + mz * hZ                 // how far across the flow the midpoint is
            val f = -SHEAR_C * b
            sa[k + 1] = sa[k] + (sX * f) * (x1 - x0) + (sZ * f) * (z1 - z0)
        }

        // ---- the instrument and its labels -----------------------------------------------------
        mastX = fr[0] + sX * MAST_SIDE + hX * MAST_AHEAD
        mastZ = fr[2] + sZ * MAST_SIDE + hZ * MAST_AHEAD
        mastY = ground + kit.terrainHeight(mastX, mastZ) + MAST_FOOT
        zeroY = mastY + MAST_H * 0.5f
        // Notation goes BESIDE things: the meter's name inboard of the mast, the claim outboard of
        // it over the open ground inside the circuit, and the field's name past the far rim where
        // there is nothing behind it. Nothing above, nothing below — the telemetry block owns the
        // top of the eye and the caption box owns the bottom.
        wX = mastX + sX * 0.40f
        wZ = mastZ + sZ * 0.40f
        claimX = mastX - sX * 1.45f
        claimZ = mastZ - sZ * 1.45f
        claimY = zeroY + 0.62f
        labX = fr[0] - sX * 9.2f
        labZ = fr[2] - sZ * 9.2f
        labY = ground + kit.terrainHeight(labX, labZ) + 0.55f
        built = true
    }

    /** One circuit point, from its place in the figure's own flat coordinates. */
    private fun seat(p: FloatArray, k: Int, a: Float, b: Float, ground: Float, kit: SceneKit) {
        val x = cX + sX * a + hX * b
        val z = cZ + sZ * a + hZ * b
        p[k * 3] = x
        p[k * 3 + 1] = ground + kit.terrainHeight(x, z) + LIFT
        p[k * 3 + 2] = z
    }

    /** The meter's reading part-way round, interpolated between samples of the work table. */
    private fun workAt(m: Int, u: Float): Float {
        val f = u * LN
        val k = f.toInt().coerceIn(0, LN - 1)
        val t = (f - k).coerceIn(0f, 1f)
        val arr = if (m == 0) wa else if (m == 1) wb else sa
        return arr[k] + (arr[k + 1] - arr[k]) * t
    }

    /**
     * The number the whole stop turns on, where it can actually be read.
     *
     * Nothing until the first draw has surveyed the ground: readout() is handed no stop index and
     * cannot place the circuit on its own. Afterwards it reads the baked tables and the clock, and
     * touches no renderer temporary — it runs on the UI thread whenever Android feels like
     * rebuilding the telemetry block, not on the GL thread.
     *
     * The closed-loop total is withheld until the probe is home. Printing the answer while the lap
     * is still running would be the HUD spoiling the demonstration the scene is in the middle of.
     */
    override fun readout(kit: SceneKit): String? {
        if (!built) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val m = mode(c)
        val u = lap(c)
        if (u < 1f) return "WORK %+.2f".format(Locale.US, workAt(m, u))
        return if (m == 2) "ROUND TRIP %+.2f   SURPLUS".format(Locale.US, sa[LN])
        else "ROUND TRIP %+.2f   HOME".format(Locale.US, workAt(m, 1f))
    }

    /** The country's cool-to-warm ramp at normalised height [t], into [tint]. */
    private fun ramp(t: Float) {
        val a = SceneParts.COOL
        val b = SceneParts.HOT
        val u = t.coerceIn(0f, 1f)
        tint[0] = a[0] + (b[0] - a[0]) * u
        tint[1] = a[1] + (b[1] - a[1]) * u
        tint[2] = a[2] + (b[2] - a[2]) * u
    }

    /** A run of circuit, from sample [from] to sample [to]. */
    private fun path(
        line: FloatArray, at: Int, p: FloatArray, from: Int, to: Int,
        col: FloatArray, alpha: Float
    ): Int {
        var k = at
        for (s in from until to) {
            k = MathMesh.segment(
                line, k,
                p[s * 3], p[s * 3 + 1], p[s * 3 + 2],
                p[(s + 1) * 3], p[(s + 1) * 3 + 1], p[(s + 1) * 3 + 2],
                col[0], col[1], col[2], alpha
            )
        }
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No country, no landscape whose uphill arrows these are: this stop is meaningless without
        // a terrain callback, and drawing the circuits alone would be a claim with nothing under it.
        if (!kit.hasTerrain) return
        build(kit, i)

        val q = kit.quality
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val m = mode(c)
        val u = lap(c)
        val sw = ((c - SWITCH_AT) / SWITCH_LEN).coerceIn(0f, 1f)
        val home = u >= 1f
        val crx = kit.camRightX; val cry = kit.camRightY; val crz = kit.camRightZ

        // --- the country, and its failure ------------------------------------------------------
        // Nothing has actually gone wrong when this stutters. There is no potential for a field
        // with circulation, so there is no landscape to draw, and a surface that cannot hold
        // itself together is how that reads without a paragraph of narration. It comes back at the
        // top of the cycle by growing rather than snapping, so the loop has no seam.
        var ghost = GHOST * SceneParts.ease(c / 0.04f)
        if (m == 2) {
            val s = sin(kit.seconds * 43.7f) * sin(kit.seconds * 11.3f)
            ghost *= (1f - sw) * (if (s > 0.10f) 1f else 0.12f)
        }
        if (ghost > 0.01f) {
            // Plain while loops rather than `0 until NS step sp`: a stepped range in a for header
            // is an IntProgression, and whether the compiler folds it away is not something to bet
            // a per-frame allocation on thirty times a second.
            val sp = if (q == 0) 1 else 2
            var j = 0
            while (j < NS) {
                var k = 0
                while (k < NS) {
                    val idx = j * NS + k
                    val a0 = ga[idx]
                    if (a0 >= 0.02f) {
                        if (k + sp < NS) {
                            val id2 = idx + sp
                            val a1 = ga[id2]
                            if (a1 > 0.02f) {
                                ramp(((gy[idx] + gy[id2]) * 0.5f - loY) / spanY)
                                v = MathMesh.segment(
                                    line, v, gx[idx], gy[idx], gz[idx], gx[id2], gy[id2], gz[id2],
                                    tint[0], tint[1], tint[2], a0 * ghost, a1 * ghost
                                )
                            }
                        }
                        if (j + sp < NS) {
                            val id2 = idx + sp * NS
                            val a1 = ga[id2]
                            if (a1 > 0.02f) {
                                ramp(((gy[idx] + gy[id2]) * 0.5f - loY) / spanY)
                                v = MathMesh.segment(
                                    line, v, gx[idx], gy[idx], gz[idx], gx[id2], gy[id2], gz[id2],
                                    tint[0], tint[1], tint[2], a0 * ghost, a1 * ghost
                                )
                            }
                        }
                    }
                    k += sp
                }
                j += sp
            }
        }

        // --- the arrows, and the swap ----------------------------------------------------------
        // Both sets are drawn through the switch window, crossing over, at the same lattice points:
        // one field is being taken off these places and another put on them, and a cut would read
        // as the scene being rebuilt rather than as the crew turning a dial.
        val upA = (1f - sw) * 0.85f
        val shA = sw * 0.90f
        // The barbs are handed world up, which puts them in the plane of the arrow and the
        // vertical — and since every arrow here is horizontal, that plane IS the ground plane, so
        // the heads read as flat arrowheads seen from above rather than as flags standing up.
        val asp = if (q == 0) 1 else 2
        val warm = SceneParts.WORK
        val cold = SceneParts.TAKEN
        var aj = 0
        while (aj < AN) {
            var ak = 0
            while (ak < AN) {
                val idx = aj * AN + ak
                if (upA > 0.02f) {
                    v = MathMesh.arrow(
                        line, v, arX[idx], arY[idx], arZ[idx], uhX[idx], 0f, uhZ[idx],
                        0f, 1f, 0f, warm[0], warm[1], warm[2], upA, 0.36f
                    )
                }
                if (shA > 0.02f) {
                    v = MathMesh.arrow(
                        line, v, arX[idx], arY[idx], arZ[idx], shX[idx], 0f, shZ[idx],
                        0f, 1f, 0f, cold[0], cold[1], cold[2], shA, 0.36f
                    )
                }
                ak += asp
            }
            aj += asp
        }

        // --- the two circuits -------------------------------------------------------------------
        // The one not being flown stays faintly lit. "Two different routes home from the same gate"
        // is a comparison, and a comparison needs both of its halves on screen at once.
        val act = if (m == 1) pb else pa
        val idle = if (m == 1) pa else pb
        val steel = SceneParts.STEEL
        if (q < 2) v = path(line, v, idle, 0, LN, steel, 0.16f)
        v = path(line, v, act, 0, LN, SceneParts.CHALK, 0.40f)

        // --- how far the probe has got ----------------------------------------------------------
        val trail = if (m == 2) SceneParts.TAKEN else SceneParts.ADDED
        val fk = u * LN
        val last = fk.toInt().coerceIn(0, LN)
        v = path(line, v, act, 0, last, trail, 0.95f)
        val t2 = (fk - last).coerceIn(0f, 1f)
        val nx = (last + 1).coerceAtMost(LN)
        val bx = act[last * 3] + (act[nx * 3] - act[last * 3]) * t2
        val by = act[last * 3 + 1] + (act[nx * 3 + 1] - act[last * 3 + 1]) * t2
        val bz = act[last * 3 + 2] + (act[nx * 3 + 2] - act[last * 3 + 2]) * t2
        if (last < LN) {
            v = MathMesh.segment(
                line, v, act[last * 3], act[last * 3 + 1], act[last * 3 + 2], bx, by, bz,
                trail[0], trail[1], trail[2], 0.95f
            )
        }

        // --- the height gained, drawn where it is gained ------------------------------------------
        // While the field is a gradient this stick and the meter's bar are the SAME quantity, drawn
        // twice in two places: the probe's altitude above the gate. When the probe is home the
        // stick has no length, which is the theorem with the arithmetic taken out. Under the shear
        // there is no such stick, because there is no height that would do.
        if (m < 2) {
            val datum = act[1]
            val ad = SceneParts.ADDED
            v = MathMesh.segment(line, v, bx, by, bz, bx, datum, bz, ad[0], ad[1], ad[2], 0.85f, 0.30f)
            v = MathMesh.segment(
                line, v, bx - sX * 0.13f, datum, bz - sZ * 0.13f,
                bx + sX * 0.13f, datum, bz + sZ * 0.13f, ad[0], ad[1], ad[2], 0.55f
            )
        }

        // --- the work meter ------------------------------------------------------------------------
        // The mast stands beside the gate, so the bar is at zero exactly when the probe is beside it.
        val w = workAt(m, u)
        v = MathMesh.segment(
            line, v, mastX, mastY, mastZ, mastX, mastY + MAST_H, mastZ,
            steel[0], steel[1], steel[2], 0.55f
        )
        // Ticks and the zero line are billboarded along the camera's right, so the meter reads from
        // the approach down the rail AND from beside it at the pass; a scale in a fixed plane is
        // edge-on for half of every fly-by.
        v = MathMesh.segment(
            line, v, mastX - crx * 0.20f, zeroY - cry * 0.20f, mastZ - crz * 0.20f,
            mastX + crx * 0.20f, zeroY + cry * 0.20f, mastZ + crz * 0.20f,
            steel[0], steel[1], steel[2], 0.9f
        )
        if (q == 0) {
            for (t in -2..2) {
                if (t == 0) continue
                val ty = zeroY + t * BAR_SCALE
                v = MathMesh.segment(
                    line, v, mastX - crx * 0.09f, ty - cry * 0.09f, mastZ - crz * 0.09f,
                    mastX + crx * 0.09f, ty + cry * 0.09f, mastZ + crz * 0.09f,
                    steel[0], steel[1], steel[2], 0.40f
                )
            }
        }
        // The home mark: teal when the round trip cost nothing, red when it did not.
        val ring = if (!home) steel else if (m == 2) SceneParts.TAKEN else SceneParts.ADDED
        val pulse = if (home) 0.55f + 0.45f * sin(kit.seconds * 3f) else 0.35f
        // Spanned by the camera's own right and up, so it stays a circle rather than the sheared
        // ellipse you get from pairing a rolled camera axis with world up.
        v = MathMesh.arc(
            line, v, mastX, zeroY, mastZ, crx, cry, crz, kit.camUpX, kit.camUpY, kit.camUpZ,
            0.22f, 0f, TAU, if (q == 0) 16 else 10, ring[0], ring[1], ring[2], pulse
        )

        kit.flushLines(v, 2.3f)

        // The bar itself, billboarded to the same axis as its scale.
        val bar = if (m == 2) SceneParts.TAKEN else if (home) SceneParts.ADDED else SceneParts.WORK
        val dy = (w * BAR_SCALE).coerceIn(-BAR_MAX, BAR_MAX)
        tv = MathMesh.quad(
            tri, tv,
            mastX - crx * BAR_W, zeroY - cry * BAR_W, mastZ - crz * BAR_W,
            crx * 2f * BAR_W, cry * 2f * BAR_W, crz * 2f * BAR_W,
            0f, dy, 0f,
            bar[0], bar[1], bar[2], 0.55f
        )
        kit.flushTris(tv)

        // --- the probe, and the arm that put it there -----------------------------------------------
        kit.ball(
            bx, by + 0.10f, bz, 0.13f, 0.13f, 0.13f,
            SceneParts.LAMP, SceneParts.HOT, 1f, 0f, 0f, 1f, 0f, 0f, 1.2f + kit.beat * 1.5f
        )
        if (kit.reach > 0.03f) {
            val t = kit.reach
            val ox = kit.shipX; val oy = kit.shipY - 0.16f; val oz = kit.shipZ
            kit.rod(
                ox, oy, oz,
                ox + (pa[0] - ox) * t, oy + (pa[1] - oy) * t, oz + (pa[2] - oz) * t,
                0.026f, steel, SceneParts.LAMP, 0.3f
            )
        }

        // --- notation ---------------------------------------------------------------------------------
        // The claim arrives only after the picture has already made it true: while a lap is running
        // it states the RULE the meter is obeying, and only once the probe is home does it state
        // the result. Under the shear the rule line is an integral, because that is what it took.
        val claim = when {
            m == 2 && sw < 1f -> "F ≠ ∇f"
            m == 2 && home -> "∫_{loop} F · dr ≠ 0"
            m == 2 -> "W = ∫ F · dr"
            home -> "∫_{loop} ∇f · dr = 0"
            else -> "W = f − f_0"
        }
        val ink = if (m == 2) SceneParts.TAKEN else SceneParts.HOT
        kit.text(claim, claimX, claimY, claimZ, 0.20f, ink, 1f, GlyphBoard.Style.MATH, 1.15f)
        if (q < 2) {
            kit.text("W", wX, zeroY + 0.04f, wZ, 0.18f, steel, 0.9f)
        }
        // Secondary: what the arrows ARE. Quality 0 only.
        if (q == 0) {
            kit.text(
                if (sw > 0.5f) "F" else "∇f", labX, labY, labZ, 0.22f,
                if (sw > 0.5f) cold else warm, 0.9f, GlyphBoard.Style.MATH, 1.1f
            )
        }
    }
}
