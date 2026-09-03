package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tour V, stop 5 — THE CONTOURS. "Walk a contour and you never climb, and the uphill arrow is
 * always dead square to your path."
 *
 * The country and its eight tide marks are [SceneAmbientCountry]'s job and are already on the
 * ground before this scene draws a line. What this stop adds is ONE of those rings picked out and
 * WALKED, with the gradient arrow riding along it — and the arrow is square to the track at every
 * step, through every turn, for as long as anyone cares to watch. The claim is not asserted in
 * notation and then illustrated; the notation only names what the geometry has already done.
 *
 * WHICH RING. The level is not a constant in this file. It is chosen at build time as the ambient's
 * ring nearest the ground directly under the rail at this stop, which on the tour's terrain comes
 * out at +0.3 and puts the track between one and four units to one side of the rail for the whole
 * leg. That is the design's "the ship flies along one contour" taken literally: the rail really
 * does parallel this level set, and the picture is the craft's own track drawn on the ground rather
 * than a diagram parked beside it. The ladder constants below (BASE, STEP, LEVELS) MIRROR the
 * ambient's private ones and must agree with them, or the highlighted ring will be a ring nobody
 * else is drawing and the whole conceit falls apart.
 *
 * WHY IT IS TRACED AND NOT SAMPLED. The kit hands out heights, not derivatives and not level sets,
 * so the curve is marched: step along the contour's tangent, then take a damped Newton step back
 * onto the level along the gradient. That converges to under a ten-thousandth of a unit and it
 * costs nothing, because the terrain is static and the stop does not move — the whole trace, the
 * gradient at every point of it and all fourteen rungs are built ONCE on the first frame and read
 * out of arrays thereafter. draw() makes no call to terrainHeight at all.
 *
 * THE RIGHT ANGLE IS THE REAL ONE. The direction of travel is not taken from the chord between two
 * traced points, which would be square to the gradient only to within the tracing error. It is the
 * gradient itself turned a quarter turn in the ground plane. So the corner mark drawn at the
 * walker's feet is exactly ninety degrees by construction, and a viewer who stares at it for a
 * minute is not slowly being lied to.
 *
 * THE RUNGS. Every seventh point carries a dashed spur running straight uphill to the next ring
 * up: the map-reader's own gesture, measuring contour spacing across the contours. Its length is
 * the crowding, and it is drawn climbing — its far end sits at the higher ring's own height — so a
 * rung is visibly a little ramp between two level lines while the track itself is dead flat.
 * Honest caveat, and the crew says it out loud too: on this stretch the ground is evenly graded,
 * so the rungs breathe by about forty per cent rather than dramatically, and the arrow with them.
 * Where the ray crests before it ever reaches the ring above — which happens at the far end, past
 * about sixteen units out — the rung is dropped rather than faked at some arbitrary length.
 *
 * WHICH WAY THE WALKER GOES. Toward the craft, not away from it. The director parks at a stop for
 * most of its dwell, so a walker who sets off up the track ends the loop as a four-pixel dot with
 * an unreadable corner mark on it. Coming to meet you, it ends the loop nine units out, big enough
 * to read the right angle on, which is the one thing here that has to stay legible at rest.
 *
 * GLYPH SIZE. The tour's usual 0.16-0.26 world heights are calibrated for a figure at arm's length
 * inside a four-unit passage. This figure is on the ground nine to twenty units away, where 0.22
 * would render at about seven pixels. So the heights here are derived from the distance to the
 * label point to hold a constant ANGULAR size, and clamped at both ends.
 */
object SceneContours : MathScene {

    /** Big open-country geometry: it wants to be on the ground well before the craft arrives. */
    override val reach = 1.6f

    /** The track runs some twenty units past the stop, so do not cull it at its own node. */
    override val deep = 1.4f

    // ---- the ambient's level ladder, mirrored -----------------------------------------------
    // These four MUST match SceneAmbientCountry's own. They are private there; duplicating them
    // is the lesser evil against widening the kit for one stop's benefit.
    private const val BASE = -2.1f
    private const val STEP = 0.6f
    private const val LEVELS = 8
    private const val LIFT = 0.06f          // twice the ambient's, so this ring draws proud of it

    // ---- the trace ---------------------------------------------------------------------------
    private const val POINTS = 96
    private const val SEED_AT = 34          // the seed's index: the track runs 11 units back, 20 on
    private const val DS = 0.33f            // world units between traced points
    private const val DIFF = 0.12f          // central-difference half-step for the gradient
    private const val SETTLE = 0.8f         // largest Newton step, so a flat patch cannot fling it

    private const val HAIR_EVERY = 7
    private const val HAIRS = (POINTS + HAIR_EVERY - 1) / HAIR_EVERY
    private const val HAIR_MAX = 6.5f       // give up looking for the ring above beyond this
    private const val HAIR_DS = 0.12f

    // ---- staging -----------------------------------------------------------------------------
    private const val PERIOD = 26f
    private const val WALK_FROM = 90f       // far end of the walk, about eighteen units ahead
    private const val WALK_TO = 60f         // and it rests here, about nine units ahead
    private const val TRAIL = 11f           // points of track behind the walker that still glow
    private const val LABEL_AT = 74         // where the level's own number is painted on the track
    private const val ARROW = 4.6f          // world units per unit of gradient: a stated convention
    private const val RIBBON_A = 0.85f
    private const val CORNER = 0.34f        // side of the right-angle mark
    private const val GLYPH = 0.062f        // glyph height as a fraction of its distance (~3.5 deg)

    // ---- the cache, filled once ---------------------------------------------------------------
    private val px = FloatArray(POINTS)
    private val pz = FloatArray(POINTS)
    private val nx = FloatArray(POINTS)     // unit gradient at that point, x component
    private val nz = FloatArray(POINTS)     // and z
    private val gm = FloatArray(POINTS)     // its magnitude: the steepness
    private val hairLen = FloatArray(HAIRS) // straight-uphill distance to the ring above, or -1
    private var level = 0f
    private var orient = 1f                 // +1 if increasing index runs the way the rail points
    private var levelLabel = ""
    private var built = false
    private var valid = false
    private var builtX = 1e9f
    private var builtZ = 1e9f

    // ---- scratch ------------------------------------------------------------------------------
    private val fr = FloatArray(12)
    private val g2 = FloatArray(2)
    private val p2 = FloatArray(2)

    // ============================================================ the country, read once

    /**
     * The unit uphill direction at a world (x, z) into [out], returning the steepness.
     *
     * Finite differences rather than the terrain's own formula, because a scene is only handed
     * heights: if the tour's landscape is ever rewritten this still tells the truth. A twelfth of
     * a unit is a tenth of the ambient's grid cell and a two-hundredth of the terrain's shortest
     * wavelength, so the truncation error is far below anything the eye could resolve here.
     */
    private fun uphill(kit: SceneKit, x: Float, z: Float, out: FloatArray): Float {
        val gx = (kit.terrainHeight(x + DIFF, z) - kit.terrainHeight(x - DIFF, z)) / (2f * DIFF)
        val gz = (kit.terrainHeight(x, z + DIFF) - kit.terrainHeight(x, z - DIFF)) / (2f * DIFF)
        val m = sqrt(gx * gx + gz * gz)
        if (m < 1e-5f) { out[0] = 1f; out[1] = 0f; return 0f }
        out[0] = gx / m; out[1] = gz / m
        return m
    }

    /** Damped Newton along the gradient: drags [p] onto the [target] level and leaves it there. */
    private fun settle(kit: SceneKit, target: Float, p: FloatArray, iters: Int) {
        for (k in 0 until iters) {
            val d = target - kit.terrainHeight(p[0], p[1])
            if (abs(d) < 1e-4f) return
            val m = uphill(kit, p[0], p[1], g2)
            if (m < 1e-4f) return
            var s = d / m                                   // how far along the unit gradient
            if (s > SETTLE) s = SETTLE else if (s < -SETTLE) s = -SETTLE
            p[0] += g2[0] * s; p[1] += g2[1] * s
        }
    }

    /** Record a traced point and everything the drawing will want to know about it. */
    private fun store(kit: SceneKit, i: Int, x: Float, z: Float) {
        px[i] = x; pz[i] = z
        gm[i] = uphill(kit, x, z, g2)
        nx[i] = g2[0]; nz[i] = g2[1]
    }

    /**
     * Trace the ring, measure its steepness everywhere, and find the rungs. Called from draw() and
     * returns immediately once the answer is in hand; the stop-position check is only there so a
     * jump to a different tour with a different landscape cannot leave a stale curve behind.
     */
    private fun build(kit: SceneKit, i: Int) {
        kit.frame(i.toFloat(), fr)
        val cx = fr[0]; val cz = fr[2]
        if (built && abs(cx - builtX) < 0.25f && abs(cz - builtZ) < 0.25f) return
        built = true; valid = false
        builtX = cx; builtZ = cz

        // --- which ring ---------------------------------------------------------------------
        // The one nearest the ground under the rail, clamped so the ring ABOVE it also exists:
        // the rungs need somewhere to climb to.
        val h0 = kit.terrainHeight(cx, cz)
        var k = ((h0 - BASE) / STEP + 0.5f).toInt()
        if (k < 0) k = 0
        if (k > LEVELS - 2) k = LEVELS - 2
        level = BASE + k * STEP
        levelLabel = String.format(Locale.US, "f = %.1f", level)

        // --- the seed -----------------------------------------------------------------------
        // Straight up (or down) the hill from the rail until the ground reads the chosen level.
        p2[0] = cx; p2[1] = cz
        settle(kit, level, p2, 28)
        if (abs(kit.terrainHeight(p2[0], p2[1]) - level) > 0.02f) return   // no such ring near by
        store(kit, SEED_AT, p2[0], p2[1])

        // --- which way is "forward" ------------------------------------------------------------
        // The tangent is the gradient turned a quarter turn; pick the turn that agrees with the
        // rail's heading, so index increases the way the craft flies and the taper, the trail and
        // the walk can all be written in terms of "ahead".
        orient = if ((-nz[SEED_AT]) * fr[3] + nx[SEED_AT] * fr[5] >= 0f) 1f else -1f

        // --- the march ---------------------------------------------------------------------
        // Step along the tangent, then settle back onto the level. Four Newton steps is more than
        // this ever needs at a third of a unit per step, and it costs nothing done once.
        p2[0] = px[SEED_AT]; p2[1] = pz[SEED_AT]
        for (j in SEED_AT + 1 until POINTS) {
            p2[0] += -nz[j - 1] * orient * DS
            p2[1] += nx[j - 1] * orient * DS
            settle(kit, level, p2, 4)
            store(kit, j, p2[0], p2[1])
        }
        p2[0] = px[SEED_AT]; p2[1] = pz[SEED_AT]
        for (j in SEED_AT - 1 downTo 0) {
            p2[0] -= -nz[j + 1] * orient * DS
            p2[1] -= nx[j + 1] * orient * DS
            settle(kit, level, p2, 4)
            store(kit, j, p2[0], p2[1])
        }

        // --- the rungs -------------------------------------------------------------------------
        // Straight up the local gradient until the ground reads one level higher, then bisect for
        // the crossing. Straight, not a curved path of steepest ascent: what a map reader measures
        // is the gap ACROSS the contours at right angles to them, and a curved spur would also
        // stop looking square to the track a foot after it left it.
        val up = level + STEP
        for (hi in 0 until HAIRS) {
            val j = hi * HAIR_EVERY
            hairLen[hi] = -1f
            if (j >= POINTS) continue
            var s = 0f
            var found = false
            while (s < HAIR_MAX) {
                s += HAIR_DS
                if (kit.terrainHeight(px[j] + nx[j] * s, pz[j] + nz[j] * s) >= up) { found = true; break }
            }
            if (!found) continue
            var lo = s - HAIR_DS; var hi2 = s
            for (b in 0 until 14) {
                val m = (lo + hi2) * 0.5f
                if (kit.terrainHeight(px[j] + nx[j] * m, pz[j] + nz[j] * m) >= up) hi2 = m else lo = m
            }
            hairLen[hi] = hi2
        }
        valid = true
    }

    // ============================================================ the walk

    /** Where the walker is, as a fractional index, at time [seconds]. */
    private fun walkerAt(seconds: Float): Float {
        val c = SceneParts.cycle(seconds, PERIOD)
        val w = ((c - 0.06f) / 0.66f).coerceIn(0f, 1f)
        // Mostly linear with soft ends. A plain smoothstep would have the surveyor sprint through
        // the middle of the walk and creep at both ends, which is not what walking looks like.
        val pace = w * 0.65f + SceneParts.ease(w) * 0.35f
        return WALK_FROM + (WALK_TO - WALK_FROM) * pace
    }

    /**
     * Ground covered, height gained, steepness ignored. The second of those is the stop: it is
     * zero, it stays zero, and it stays zero while the third one is plainly not.
     */
    override fun readout(kit: SceneKit): String? {
        if (!valid) return null
        val idx = walkerAt(kit.seconds)
        val i0 = idx.toInt().coerceIn(0, POINTS - 2)
        val t = idx - i0
        val g = gm[i0] + (gm[i0 + 1] - gm[i0]) * t
        return String.format(
            Locale.US, "WALKED %.1f   CLIMBED 0.00   |∇f| %.2f", (WALK_FROM - idx) * DS, g
        )
    }

    /** Glyph height that holds a constant angular size from wherever the eye happens to be. */
    private fun glyph(kit: SceneKit, x: Float, y: Float, z: Float): Float {
        val dx = x - kit.camX; val dy = y - kit.camY; val dz = z - kit.camZ
        return (sqrt(dx * dx + dy * dy + dz * dz) * GLYPH).coerceIn(0.18f, 0.9f)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // A tour with no landscape has no level sets to walk. Nothing to apologise for; draw none.
        if (!kit.hasTerrain) return
        build(kit, i)
        if (!valid) return

        val q = kit.quality
        val line = kit.lineBuf
        var v = 0
        val y = SceneAmbientCountry.GROUND_Y + level + LIFT
        val yUp = y + STEP

        val idx = walkerAt(kit.seconds)
        val i0 = idx.toInt().coerceIn(0, POINTS - 2)
        val t = idx - i0

        // --- the walker's own frame -----------------------------------------------------------
        val wx = px[i0] + (px[i0 + 1] - px[i0]) * t
        val wz = pz[i0] + (pz[i0 + 1] - pz[i0]) * t
        var ux = nx[i0] + (nx[i0 + 1] - nx[i0]) * t
        var uz = nz[i0] + (nz[i0 + 1] - nz[i0]) * t
        val ul = sqrt(ux * ux + uz * uz)
        if (ul > 1e-4f) { ux /= ul; uz /= ul }
        val mag = gm[i0] + (gm[i0 + 1] - gm[i0]) * t
        // Direction of travel: the uphill direction turned a quarter turn, negated because the
        // walker is coming back down the index. Derived from the gradient, never from the chord —
        // that is what makes the corner mark below a true ninety degrees rather than nearly one.
        val hx = uz * orient
        val hz = -ux * orient

        // --- the ring being walked --------------------------------------------------------------
        // Warm, against the ambient's teal rings and cool grid, and lifted a little further off the
        // mesh: this is the same level set the ambient already draws, so it has to be recognisably
        // the same line and unmistakably the chosen one. (The two are drawn by different methods —
        // this one traced, the ambient's by marching squares across its sample grid — so they part
        // company by a few centimetres between samples. At this range it does not show.)
        val rc = SceneParts.WORK
        val stride = if (q == 0) 1 else 2
        var j = 0
        while (j + stride < POINTS) {
            var a0 = RIBBON_A * MathMesh.taper(j.toFloat() / (POINTS - 1))
            var a1 = RIBBON_A * MathMesh.taper((j + stride).toFloat() / (POINTS - 1))
            // The stretch just walked still glows, so the track reads as a track and not a wire.
            val d0 = j - idx
            if (d0 > 0f && d0 < TRAIL) a0 *= 1f + 1.5f * (1f - d0 / TRAIL)
            val d1 = j + stride - idx
            if (d1 > 0f && d1 < TRAIL) a1 *= 1f + 1.5f * (1f - d1 / TRAIL)
            v = MathMesh.segment(
                line, v, px[j], y, pz[j], px[j + stride], y, pz[j + stride],
                rc[0], rc[1], rc[2], if (a0 > 1f) 1f else a0, if (a1 > 1f) 1f else a1
            )
            j += stride
        }

        // --- the rungs to the ring above ---------------------------------------------------------
        // Dashed, because these are construction lines and not things standing on the ground, and
        // climbing, because their far ends are one level up. Half of them at a thermal step. Warm
        // and dim rather than the palette's steel: steel sits within a hair of the ambient's cool
        // grid and the rungs would read as part of the mesh they are measuring across.
        val hc = SceneParts.WORK_DIM
        var hi = 0
        val hstep = if (q == 0) 1 else 2
        while (hi < HAIRS) {
            val len = hairLen[hi]
            val p = hi * HAIR_EVERY
            if (len > 0f && p < POINTS) {
                val a = 0.55f * MathMesh.taper(p.toFloat() / (POINTS - 1))
                val ex = px[p] + nx[p] * len
                val ez = pz[p] + nz[p] * len
                v = if (q == 0)
                    MathMesh.dashed(line, v, px[p], y, pz[p], ex, yUp, ez, 3, hc[0], hc[1], hc[2], a)
                else
                    MathMesh.segment(line, v, px[p], y, pz[p], ex, yUp, ez, hc[0], hc[1], hc[2], a)
            }
            hi += hstep
        }

        // --- the gradient, at the walker ----------------------------------------------------------
        // Flat on the ground, not up the face of the hill: stop 4 says that out loud and this stop
        // has to agree with it. Its length is the steepness times a scale of our choosing — a slope
        // is a number and has no length of its own, so the scale is a convention, not a measurement.
        val gc = SceneParts.HOT
        val glen = mag * ARROW
        v = MathMesh.arrow(
            line, v, wx, y, wz, ux * glen, 0f, uz * glen, 0f, 1f, 0f,
            gc[0], gc[1], gc[2], 1f
        )

        // --- and the heading it is square to --------------------------------------------------
        val cc = SceneParts.CHALK
        v = MathMesh.arrow(
            line, v, wx, y, wz, hx * 1.05f, 0f, hz * 1.05f, 0f, 1f, 0f,
            cc[0], cc[1], cc[2], 0.9f
        )

        // --- the corner ---------------------------------------------------------------------------
        // The whole stop, in four vertices. It never opens and never closes, all the way round.
        val ax = wx + hx * CORNER; val az = wz + hz * CORNER
        val bx = ax + ux * CORNER; val bz = az + uz * CORNER
        val cx2 = wx + ux * CORNER; val cz2 = wz + uz * CORNER
        v = MathMesh.segment(line, v, ax, y, az, bx, y, bz, gc[0], gc[1], gc[2], 0.95f)
        v = MathMesh.segment(line, v, bx, y, bz, cx2, y, cz2, gc[0], gc[1], gc[2], 0.95f)

        // --- the leader the claim hangs from --------------------------------------------------
        val lift = 1.35f
        if (q == 0) {
            v = MathMesh.segment(
                line, v, wx, y + 0.12f, wz, wx, y + lift, wz,
                cc[0], cc[1], cc[2], 0.16f, 0.36f
            )
        }

        kit.flushLines(v, 2.4f)

        // --- the walker ---------------------------------------------------------------------------
        // One ball, one draw call, and a fixed size: an object that refused to shrink with distance
        // would read as painted on the glass rather than standing on the ground.
        kit.ball(
            wx, y + 0.12f, wz, 0.20f, 0.20f, 0.20f,
            SceneParts.LAMP, SceneParts.HOT, 1f, 0f, 0f, 1f, 0f, 0f, 1.4f
        )

        // --- notation ------------------------------------------------------------------------------
        // Everything sits beside the geometry it names and is lifted half a unit off the ground:
        // the figure is already fifteen to twenty degrees below the horizon and the caption box owns
        // the bottom of the eye, so notation left lying on the dirt would be read through it.
        val nlx = wx + ux * (glen + 0.30f)
        val nlz = wz + uz * (glen + 0.30f)
        val ny = y + 0.55f
        kit.text("∇f", nlx, ny, nlz, glyph(kit, nlx, ny, nlz), gc, 1f, anchor = -0.5f)

        if (q <= 1) {
            // The level, named ON the track and set off downhill so it does not sit among the
            // rungs. Pinned to a fixed point of the curve rather than carried along beside the
            // walker: it is a mark on the ground, like the number printed on a map's contour, and
            // a number that slides about reads as a caption instead.
            val li = LABEL_AT
            val lx = px[li] - nx[li] * 0.45f
            val lz = pz[li] - nz[li] * 0.45f
            val ly = y + 0.45f
            kit.text(levelLabel, lx, ly, lz, glyph(kit, lx, ly, lz) * 0.8f, rc, 0.9f, anchor = -0.5f)
        }

        if (q == 0) {
            val tlx = wx + hx * 1.30f
            val tlz = wz + hz * 1.30f
            kit.text("u", tlx, ny, tlz, glyph(kit, tlx, ny, tlz) * 0.85f, cc, 0.9f, anchor = -0.5f)
            // The sentence the picture has been making for the last twenty seconds: the slope you
            // feel along your own heading is nothing at all. Notation last, and only at full detail.
            val hy = y + lift
            kit.text("∇f · u = 0", wx, hy, wz, glyph(kit, wx, hy, wz) * 0.9f, gc, 1f, rise = 0.5f)
        }
    }
}
