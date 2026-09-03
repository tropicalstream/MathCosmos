package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * TOUR VI's AMBIENT — the field itself, everywhere, for the whole ride.
 *
 * Tour VI is the one tour whose subject has no location. A parabola is somewhere; a field is not.
 * So this is not a landmark that fades up as you approach it — it is drawn every frame from the
 * first stop to the last, and its job is to make the corridor stop being a corridor with things in
 * it and start being a medium the craft is immersed in. That is also why the tour sets its wall
 * alpha to 0.15: the tube is a guide-rail, and the arrows are allowed outside it. This scene
 * deliberately ignores the usual "stay inside 0.8 of the passage radius" rule, because a field
 * that stopped at the wall would read as wallpaper printed on the inside of a pipe.
 *
 * THE ONE DECISION THAT MATTERS is the snap. An early version placed the lattice at
 * ship + offset, which is arithmetically simpler and completely wrong: the arrows travelled with
 * the craft, so nothing ever went past, and the whole thing read as an overlay drawn on the
 * canopy. Snapping every arrow to a fixed world grid and letting the craft fly BETWEEN the pins
 * costs one round() per axis and buys the entire effect. A lattice that follows you is a graphic;
 * a lattice you fly through is a place.
 *
 * The cost of snapping is that the set of arrows changes — when the craft crosses a grid plane
 * the whole lattice re-centres, one shell of arrows vanishing behind and another appearing ahead.
 * The fade envelope is therefore built so that an arrow's alpha is EXACTLY zero at the distance
 * where the swap happens (outer = half-span + half-pitch), which makes the swap invisible rather
 * than a flicker across the outer shell. The envelope is a box norm, not a sphere: at quality 1
 * the lattice is only 3x3x3 and its corners are most of it, so culling to a ball would leave
 * seven arrows in a cross.
 *
 * The streamline threads are the same idea one level up. Pins in a board show direction but not
 * flow; six short threads, Euler-stepped through the field, show that the arrows are the tangents
 * of something moving. Each thread's anchor is snapped to its own phase-shifted world grid for the
 * same reason the arrows are, and the thread's geometry is static — what travels is a band of
 * brightness running along it, which loops seamlessly because the band is faded to nothing at
 * both ends by the same taper that stops the thread looking cut off.
 *
 * On the loop discipline: an ambient has no rest state to look at, because it has no finished
 * state — the lattice is complete in every frame, and a viewer arriving at any moment has already
 * seen all of it. The only thing on a clock is the travelling band, and the six threads are phase-
 * offset around the cycle so the field is never entirely dark and never entirely lit.
 *
 * ONE draw call: a single flushLines of roughly 990 vertices at quality 0. No balls, no notation —
 * a label hung in the middle of a tour-long ambient would be competing with every stop's own
 * notation for thirty-four minutes. And no readout: the HUD reads the current STOP's scene, not
 * the ambient's, so anything returned here would never be shown.
 */
object SceneAmbientField : MathScene {

    // ---------------------------------------------------------------- the lattice
    private const val SPAN = 8f              // world units across, each way
    private const val HALF = SPAN * 0.5f
    private const val N_FULL = 5             // 5x5x5 at quality 0
    private const val N_LOW = 3              // 3x3x3 once the governor steps in
    private const val PITCH_REF = 2f         // the quality-0 pitch, which arrow length is sized to

    // Nothing is drawn in the pilot's face: an arrow half a metre from the eye is a bright smear
    // across the middle of the display and tells you nothing about the field.
    private const val R_NEAR = 1.15f
    private const val R_NEAR_FULL = 2.0f

    // Cool where slow, warm where fast. These two are tuned to Tour VI's own field (a swirl about
    // the rail plus a steady drift, so roughly 0.8 to 2.2 in the space the craft occupies); the
    // ramp is clamped at both ends, so a stronger field simply saturates warm rather than breaking.
    private const val SLOW = 0.70f
    private const val FAST = 2.10f

    private const val ARROW_MIN = 0.16f      // world length of an arrow in the slowest region
    private const val ARROW_SPAN = 0.44f     // ... and how much longer it gets in the fastest
    private const val HEAD = 0.42f           // a fatter head than the default: these are small
    private const val ARROW_DIM = 0.36f      // base alpha, before the speed bonus
    private const val ARROW_LIT = 0.34f

    // ---------------------------------------------------------------- the threads
    private const val PERIOD = 22f
    private const val SEEDS = 6
    private const val STEPS = 20
    private const val H = 0.20f              // Euler step: fast field stretches the thread, slow bunches it
    private const val SEED_PITCH = 9f
    private const val SEED_OUT = SEED_PITCH * 0.5f   // zero exactly where an anchor swaps grid cell
    // Deliberately close to SEED_OUT: a gentle ramp left most threads permanently half-lit, which
    // reads as a rendering fault rather than as flow. Steep, and a thread is at full strength for
    // most of its life and only dips as it hands over to the next cell.
    private const val SEED_IN = 3.6f
    private const val BAND = 0.26f           // half-width of the travelling band, as a fraction of the thread
    private const val THREAD_BASE = 0.16f
    private const val BAND_PEAK = 0.70f
    private const val STRAY = 11f            // a thread that leaves the neighbourhood is not worth drawing

    /**
     * Six seed phases in [0, SEED_PITCH). Each is the offset of that thread's own world grid, so
     * anchor = round((ship - phase) / pitch) * pitch + phase — the anchor sits on a fixed lattice
     * of its own and the craft-to-anchor offset stays inside +/- half a pitch on every axis, which
     * is what lets the envelope below be exactly zero at the moment the anchor jumps.
     *
     * World axes, not rail axes. The lattice is world-aligned by construction — that is the whole
     * point of the snap — and with offsets of a few units on each axis the threads end up spread
     * around the craft whichever way the rail happens to be pointing.
     */
    private val SEED = floatArrayOf(
        0.4f, 1.3f, 0.2f,
        3.1f, 5.6f, 2.4f,
        6.2f, 2.9f, 4.8f,
        1.7f, 7.4f, 7.2f,
        7.9f, 4.1f, 1.2f,
        4.5f, 6.8f, 5.9f
    )

    private val fv = FloatArray(3)           // the field vector at the point being drawn
    private val p = FloatArray(3)            // the walker: an anchor, then each step of a thread
    private val q = FloatArray(3)
    private val tint = FloatArray(3)

    /** The tour's cool-to-warm ramp at normalised speed [t], into [tint]. */
    private fun tintFor(t: Float) {
        val c = SceneParts.COOL
        val w = SceneParts.WORK
        tint[0] = c[0] + (w[0] - c[0]) * t
        tint[1] = c[1] + (w[1] - c[1]) * t
        tint[2] = c[2] + (w[2] - c[2]) * t
    }

    /** Normalised speed, 0 at [SLOW] and below, 1 at [FAST] and above. */
    private fun norm(s: Float): Float = ((s - SLOW) / (FAST - SLOW)).coerceIn(0f, 1f)

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Tours without a field get nothing rather than a lattice of zero-length arrows.
        if (!kit.hasField) return

        val line = kit.lineBuf
        var v = 0

        // The ambient stands back where a landmark is being explained. Between stops it is at full
        // strength; within about a third of a stop of one, it drops to half, so the stop's own
        // geometry has the frame to itself. Half, not off — the field never stops being there.
        val off = kit.progress - floor(kit.progress + 0.5f)
        val dim = (0.50f + 0.50f * SceneParts.ease(abs(off) * 2f / 0.7f)) * (1f + 0.35f * kit.beat)

        // ------------------------------------------------------------- the arrows
        val count = if (kit.quality == 0) N_FULL else N_LOW
        val pitch = SPAN / (count - 1)
        val outer = HALF + pitch * 0.5f      // exactly the swap distance: alpha is 0 here
        val inner = HALF - pitch * 0.5f
        val scale = pitch / PITCH_REF        // a coarser lattice gets proportionally longer arrows
        val mid = (count - 1) * 0.5f
        val gx = floor(kit.shipX / pitch + 0.5f) * pitch
        val gy = floor(kit.shipY / pitch + 0.5f) * pitch
        val gz = floor(kit.shipZ / pitch + 0.5f) * pitch

        for (a in 0 until count) {
            val x = gx + (a - mid) * pitch
            val dx = abs(x - kit.shipX)
            for (b in 0 until count) {
                val y = gy + (b - mid) * pitch
                val dy = abs(y - kit.shipY)
                for (c in 0 until count) {
                    val z = gz + (c - mid) * pitch
                    val m = max(dx, max(dy, abs(z - kit.shipZ)))
                    var alpha = ((outer - m) / (outer - inner)).coerceIn(0f, 1f)
                    if (alpha <= 0.01f) continue

                    // Vector from the eye to the arrow. It does double duty: the near fade, and
                    // the "up" handed to MathMesh.arrow, which puts the barbs in the plane
                    // perpendicular to the line of sight so the head reads from wherever you are
                    // looking. Arrow heads that happen to point edge-on at the eye are the reason
                    // an arrow lattice usually looks like a box of sticks.
                    val ex = x - kit.camX
                    val ey = y - kit.camY
                    val ez = z - kit.camZ
                    val d = sqrt(ex * ex + ey * ey + ez * ez)
                    alpha *= ((d - R_NEAR) / (R_NEAR_FULL - R_NEAR)).coerceIn(0f, 1f)
                    if (alpha <= 0.01f) continue

                    kit.fieldAt(x, y, z, fv)
                    val s = sqrt(fv[0] * fv[0] + fv[1] * fv[1] + fv[2] * fv[2])
                    if (s < 1e-4f) continue
                    val t = norm(s)
                    tintFor(t)
                    // Length carries the strength as well as the colour, so the field is legible
                    // in monochrome and at the edge of vision where colour discrimination is poor.
                    val k = (ARROW_MIN + ARROW_SPAN * t) * scale / s
                    v = MathMesh.arrow(
                        line, v, x, y, z, fv[0] * k, fv[1] * k, fv[2] * k,
                        ex, ey, ez,
                        tint[0], tint[1], tint[2],
                        alpha * dim * (ARROW_DIM + ARROW_LIT * t), HEAD
                    )
                }
            }
        }

        // ------------------------------------------------------------- the threads
        // Decoration, in the governor's sense, so they go first at quality 2 — but they are the
        // part that says "flow", so they survive quality 1 at half the count.
        if (kit.quality < 2) {
            val seeds = if (kit.quality == 0) SEEDS else SEEDS / 2
            val cyc = SceneParts.cycle(kit.seconds, PERIOD)
            for (k in 0 until seeds) {
                val ox = SEED[k * 3]
                val oy = SEED[k * 3 + 1]
                val oz = SEED[k * 3 + 2]
                p[0] = floor((kit.shipX - ox) / SEED_PITCH + 0.5f) * SEED_PITCH + ox
                p[1] = floor((kit.shipY - oy) / SEED_PITCH + 0.5f) * SEED_PITCH + oy
                p[2] = floor((kit.shipZ - oz) / SEED_PITCH + 0.5f) * SEED_PITCH + oz

                val m = max(
                    abs(p[0] - kit.shipX),
                    max(abs(p[1] - kit.shipY), abs(p[2] - kit.shipZ))
                )
                var env = ((SEED_OUT - m) / (SEED_OUT - SEED_IN)).coerceIn(0f, 1f)
                val ax = p[0] - kit.camX
                val ay = p[1] - kit.camY
                val az = p[2] - kit.camZ
                val ad = sqrt(ax * ax + ay * ay + az * az)
                env *= ((ad - R_NEAR) / (R_NEAR_FULL - R_NEAR)).coerceIn(0f, 1f)
                if (env <= 0.01f) continue

                val phase = (cyc + k.toFloat() / SEEDS) % 1f
                for (st in 0 until STEPS) {
                    kit.fieldAt(p[0], p[1], p[2], fv)
                    val s = sqrt(fv[0] * fv[0] + fv[1] * fv[1] + fv[2] * fv[2])
                    if (s < 1e-4f) break
                    q[0] = p[0] + fv[0] * H
                    q[1] = p[1] + fv[1] * H
                    q[2] = p[2] + fv[2] * H

                    val u = (st + 0.5f) / STEPS
                    // Wrapped distance to the band, so the band leaving the tip and the band
                    // entering the seed are the same band and the loop has no seam.
                    var dd = abs(u - phase)
                    if (dd > 0.5f) dd = 1f - dd
                    val rise = (1f - dd / BAND).coerceAtLeast(0f)
                    val bump = rise * rise
                    val t = norm(s)
                    tintFor(t)
                    // taper() is doing two jobs: it stops the thread looking snipped off at both
                    // ends, and it holds the band at zero across the wrap point.
                    // Clamped because the beat bonus in dim can carry the band's peak past 1, and
                    // an alpha the shader has to saturate is a beat you cannot see.
                    val al = ((THREAD_BASE + BAND_PEAK * bump) * MathMesh.taper(u) * env * dim)
                        .coerceAtMost(1f)
                    v = MathMesh.segment(
                        line, v, p[0], p[1], p[2], q[0], q[1], q[2],
                        tint[0], tint[1], tint[2], al
                    )

                    val sx = q[0] - kit.shipX
                    val sy = q[1] - kit.shipY
                    val sz = q[2] - kit.shipZ
                    if (sx * sx + sy * sy + sz * sz > STRAY * STRAY) break
                    p[0] = q[0]; p[1] = q[1]; p[2] = q[2]
                }
            }
        }

        kit.flushLines(v, 2f)
    }
}
