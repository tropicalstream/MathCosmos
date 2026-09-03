package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * THE TRACE — the ambient of Tours II and IV. "The corridor's roof is the function."
 *
 * Not a stop. The renderer draws this every frame wherever the craft happens to be, so it is on
 * screen for roughly an hour of the series and is easily the most-looked-at object in the app.
 * Everything below is bent towards that one fact: it has to be beautiful at a glance and cost
 * almost nothing, because whatever it spends it spends thirty times a second for an hour.
 *
 * It is a glowing ribbon at height f(p) directly over the rail, plus a faint vertical ruling at
 * every whole node. The ruling is the half of the idea people miss. A stripe painted along a tube
 * is decoration; a stripe with the ground ruled up to it is a REGION UNDER A CURVE, and you are
 * inside it. That is what makes the wake of Tour III legible later, and it is why the ticks are
 * here rather than left to the stops that talk about area.
 *
 * Three decisions worth stating:
 *
 *  - It sits at side 0, overhead, not off to one side like a stop's figure. The general rule in
 *    this app is that a flat figure centred on the rail is one you fly into and never see whole —
 *    but the trace is scenery running the length of the corridor rather than a figure to be read,
 *    and it must share one vertical plane with the rail so the ruling, the wake and the solid of
 *    revolution all hang off the same line.
 *
 *  - The rail frame is sampled once per whole node and interpolated between, while f is sampled
 *    every 0.14 units or so. The rail turns by a couple of degrees per node unit, so a linear
 *    blend of two frames a unit apart is exact to well under a pixel, and it drops the frame
 *    queries from well over a hundred per eye to about nineteen. That matters more than it looks:
 *    on this renderer every frame query builds a small object, and a hundred and forty of those
 *    per eye per frame is a collection you can feel.
 *
 *  - One flushLines call for the whole thing, ribbon and ruling together — about 330 vertices at
 *    quality 0 on a thirteen-stop tour, 170 at quality 1, 55 at quality 2. A hard cap of 640
 *    truncates the far end rather than letting an unusually steep trace function run away with the
 *    frame; the far end is faded to nothing there anyway, so the cut would never show.
 *
 * No readout: the HUD's measurement line belongs to whichever stop the craft is at, and the roof
 * height is not a thing anyone is asked to read. No notation either — the trace is named out loud
 * by the crew and labelled by the stops that use it, and a permanent glyph riding overhead for
 * twelve stops would collide with every one of their own labels.
 */
object SceneAmbientTrace : MathScene {

    // The ambient path in the renderer consults neither of these — it draws this object
    // unconditionally at full fade. They are set so that hanging the trace on a proving-ground
    // node, to look at it on the glasses on its own, does not cull it the moment it is passed.
    override val reach = 40f
    override val deep = 21f

    private const val BEHIND_N = 6         // whole nodes of corridor kept astern
    private const val AHEAD_N = 21         // whole nodes drawn ahead
    private const val FINE = 0.14f         // sample spacing alongside the craft, in node units
    private const val SPREAD = 0.085f      // how fast that spacing coarsens with distance
    private const val HALF_W = 0.07f       // half the ribbon's width across the corridor
    private const val CEIL = 0.80f         // most of the passage radius the roof may reach
    private const val SOFT = 0.28f         // blend width of that ceiling
    private const val TAIL = 0.30f         // alpha of the ribbon six nodes astern
    private const val FADE_OUT = 10f       // longest fade-out the far end is allowed
    private const val GLOW_PERIOD = 18f    // seconds for one bloom to travel up the corridor
    private const val TAU = 6.2831855f
    private const val MAX_VERTS = 640

    // Two station frames and the points between them. All scratch; nothing here survives a frame.
    private val fA = FloatArray(12)
    private val fB = FloatArray(12)
    private val qA = FloatArray(3)         // previous sample, inner rail
    private val qB = FloatArray(3)         // previous sample, outer rail
    private val rA = FloatArray(3)
    private val rB = FloatArray(3)

    // Set once at the top of draw and read by the alpha helpers: how far ahead the drawn stretch
    // actually reaches, and how much of its end is spent tapering away. Scratch like the arrays
    // above — this object still holds nothing at all between frames.
    private var far = 0f
    private var taper = 1f

    /**
     * The roof height at [p], with the wall taken into account.
     *
     * The trace IS the function, so pressing it down is a compromise and worth naming as one. But
     * the passage radius in Tour II is the tolerance in play: it closes to 1.6 at the throat while
     * f is still up around 2.7, and a trace plotted honestly there is simply inside an opaque wall
     * and gone — at the one stop that most needs to see it. So the ribbon is squeezed against the
     * wall instead of pushed through it, by a soft minimum on the magnitude. Every wiggle and every
     * turning point survives; only the amplitude is compressed, only where the corridor is tighter
     * than the curve. Signs are preserved, so a roof that dives below the rail — Tour III's signed
     * area — still passes under the floor as it should.
     */
    private fun roof(kit: SceneKit, p: Float): Float {
        val h = kit.traceHeight(p)
        val lid = CEIL * kit.radius(p)
        val m = abs(h)
        val knee = lid - SOFT
        if (m <= knee) return h
        val over = m - knee
        // Quadratic blend: value and slope both continuous at the knee, flat by the lid.
        val pressed = if (over >= 2f * SOFT) lid else m - over * over / (4f * SOFT)
        return if (h < 0f) -pressed else pressed
    }

    /**
     * Bright alongside, tapering to nothing at the far end, dim but never gone astern.
     *
     * The taper is measured back from where the ribbon actually STOPS, not from a fixed distance.
     * These tours are only twelve or thirteen stops long, so from the first leg the drawn stretch
     * is cut short by the end of the rail long before it reaches AHEAD_N — and a ribbon that ends
     * at nine tenths brightness reads as a rendering fault, which is the one thing a permanent
     * object must never do.
     */
    private fun weight(d: Float): Float =
        if (d >= 0f) ((far - d) / taper).coerceIn(0f, 1f)
        else (1f + d * (1f - TAIL) / BEHIND_N).coerceAtLeast(TAIL)

    /**
     * Distance weight times a slow bloom travelling up the corridor. The trace has no finished
     * state to rest on — it is scenery, not a demonstration — so its loop is this light running
     * along it, on an exact 18-second cycle so a viewer arriving at any moment sees the same thing.
     */
    private fun alphaAt(p: Float, prog: Float, phase: Float): Float =
        weight(p - prog) * (0.88f + 0.14f * sin(p * 1.25f - phase))

    /** The two rail points of the ribbon at [p], from the two station frames blended by [u]. */
    private fun pair(kit: SceneKit, p: Float, u: Float, w: Float, outA: FloatArray, outB: FloatArray) {
        val cx = fA[0] + (fB[0] - fA[0]) * u
        val cy = fA[1] + (fB[1] - fA[1]) * u
        val cz = fA[2] + (fB[2] - fA[2]) * u
        val sx = fA[6] + (fB[6] - fA[6]) * u
        val sy = fA[7] + (fB[7] - fA[7]) * u
        val sz = fA[8] + (fB[8] - fA[8]) * u
        val ux = fA[9] + (fB[9] - fA[9]) * u
        val uy = fA[10] + (fB[10] - fA[10]) * u
        val uz = fA[11] + (fB[11] - fA[11]) * u
        val h = roof(kit, p)
        outA[0] = cx + ux * h - sx * w; outA[1] = cy + uy * h - sy * w; outA[2] = cz + uz * h - sz * w
        outB[0] = cx + ux * h + sx * w; outB[1] = cy + uy * h + sy * w; outB[2] = cz + uz * h + sz * w
    }

    /** One rung of the coordinate ruling: the rail, straight up to the roof, and a tie across it. */
    private fun tick(kit: SceneKit, line: FloatArray, v: Int, p: Float, f: FloatArray, w: Float, prog: Float): Int {
        val a = weight(p - prog) * 0.26f
        if (a < 0.02f) return v
        val h = roof(kit, p)
        val tx = f[0] + f[9] * h; val ty = f[1] + f[10] * h; val tz = f[2] + f[11] * h
        val c = SceneParts.COOL
        // Faded out at the rail and bright where it meets the curve, so the ruling reads as hanging
        // FROM the roof. That is the right way round: the height is the thing that exists, and the
        // floor is only where it is being measured from.
        var k = MathMesh.segment(line, v, f[0], f[1], f[2], tx, ty, tz, c[0], c[1], c[2], a * 0.12f, a)
        // A cross-tie at the top, near the craft only. It costs two vertices and it is what stops
        // the ribbon's two rails reading as two unrelated scratches.
        if (w > 0f && abs(p - prog) < 8f) {
            k = MathMesh.segment(
                line, k, tx - f[6] * w, ty - f[7] * w, tz - f[8] * w,
                tx + f[6] * w, ty + f[7] * w, tz + f[8] * w, c[0], c[1], c[2], a * 1.6f
            )
        }
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Tours I, V and VI have no roof curve. Asking for one would return zero everywhere and
        // draw a bright straight line down the middle of the corridor, which is worse than nothing.
        if (!kit.hasTrace) return

        val q = kit.quality
        val twin = q < 2                                   // two rails; one when the governor bites
        val nodeStep = if (q == 0) 1 else 2
        val fine = if (q == 0) FINE else if (q == 1) FINE * 2f else FINE * 2.6f
        val w = if (twin) HALF_W else 0f

        // Clamped to the rail's own extent: outside it the Catmull-Rom collapses onto the end node
        // and every sample beyond would pile up into a spike.
        val prog = kit.progress
        val here = floor(prog).toInt()
        val a0 = max(0, here - BEHIND_N)
        val a1 = min(kit.stopCount - 1, here + AHEAD_N)
        if (a1 - a0 < 1) return
        far = a1 - prog
        // Never longer than the stretch it has to fade over, and never so short that the end reads
        // as a cut. At the last stop, where far collapses to almost nothing, the roof running out
        // just ahead of the craft is the truth: the corridor runs out there too.
        taper = min(FADE_OUT, far * 0.45f).coerceAtLeast(0.4f)

        val line = kit.lineBuf
        val c = SceneParts.HOT
        val cap = min(MAX_VERTS, kit.lineCapacity)
        val phase = SceneParts.cycle(kit.seconds, GLOW_PERIOD) * TAU
        var v = 0

        // The opening sample, so the polyline has a point to start from.
        kit.frame(a0.toFloat(), fA)
        System.arraycopy(fA, 0, fB, 0, 12)
        pair(kit, a0.toFloat(), 0f, w, qA, qB)
        var prevA = alphaAt(a0.toFloat(), prog, phase)
        if (q < 2) v = tick(kit, line, v, a0.toFloat(), fA, w, prog)

        var s = a0
        while (s < a1 && v + 8 <= cap) {
            val e = min(s + nodeStep, a1)
            kit.frame(e.toFloat(), fB)
            val span = (e - s).toFloat()
            // Spacing set once per station from its distance: fine alongside the craft, coarse at
            // the far end where a whole hump of f is a centimetre of screen.
            val stepLen = fine * (1f + SPREAD * abs(0.5f * (s + e) - prog))
            val sub = max(1, (span / stepLen + 0.5f).toInt())
            for (k in 1..sub) {
                if (v + 4 > cap) break
                val u = k.toFloat() / sub
                val p = s + span * u
                pair(kit, p, u, w, rA, rB)
                val a = alphaAt(p, prog, phase)
                v = MathMesh.segment(
                    line, v, qA[0], qA[1], qA[2], rA[0], rA[1], rA[2], c[0], c[1], c[2], prevA, a
                )
                // The far rail is carried dimmer than the near one. Two rails at equal brightness
                // read as a doubled line; a bright edge and a shaded one read as a band with a lit
                // side, which is what a roof looks like.
                if (twin) v = MathMesh.segment(
                    line, v, qB[0], qB[1], qB[2], rB[0], rB[1], rB[2], c[0], c[1], c[2],
                    prevA * 0.72f, a * 0.72f
                )
                qA[0] = rA[0]; qA[1] = rA[1]; qA[2] = rA[2]
                qB[0] = rB[0]; qB[1] = rB[1]; qB[2] = rB[2]
                prevA = a
            }
            System.arraycopy(fB, 0, fA, 0, 12)
            s = e
            if (q < 2 && v + 6 <= cap) v = tick(kit, line, v, s.toFloat(), fA, w, prog)
        }

        // One call for the corridor's whole roof. A little wider when the second rail has been
        // dropped, so the trace does not thin out at exactly the moment the device is struggling.
        kit.flushLines(v, if (twin) 2.2f else 3.0f)
    }
}
