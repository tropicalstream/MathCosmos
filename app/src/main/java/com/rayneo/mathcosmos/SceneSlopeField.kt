package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 9 of TOUR VI — THE SLOPE FIELD. "A differential equation is a field of little slopes, and
 * a solution is a curve that obeys all of them."
 *
 * WHY THIS IS NOT THE AMBIENT'S FIELD. [SceneAmbientField] has been filling the corridor with the
 * tour's vector field for eight stops, and the temptation here is to derive the slope field from
 * it with [SceneKit.fieldAt]. That would be the wrong object. A vector field hands you a speed and
 * a direction; a first-order ODE hands you only a RATIO — dy/dx — and the whole shift this stop
 * makes is from "how fast, and which way, in space" to "how steep, here, on a graph". So the
 * equation is its own thing, written in the code as one line, and the lattice is drawn from it:
 * short dashes, all the SAME length, with no heads on them, because length and sense carry no
 * information in a slope field and a strut that pretended otherwise would be lying. Set against
 * the ambient's ramped, headed, three-dimensional arrows drifting past behind it, the difference
 * is the lesson.
 *
 * THE EQUATION. y' = cos x − y/5. Chosen by flying it: over the six units of corridor the figure
 * occupies, four solutions launched at four heights stay inside the drawn lattice, keep their
 * order, and never come closer than about 0.22 units to one another — close enough that the
 * non-crossing is a claim worth making, far enough that it survives 640x480 per eye. A pure
 * exponential fan would have been prettier for a second and then flown two of the curves through
 * the wall.
 *
 * THE SHEET LIES ALONG THE CORRIDOR, AND IS YAWED. The independent variable has to run in the
 * direction of travel — otherwise "grows a solution curve ahead of itself" means nothing — so the
 * plane is spanned by the rail's forward and its up, not by the usual square-to-the-passage stage.
 * A sheet exactly parallel to the rail is a sheet seen edge-on for the whole approach, so it is
 * yawed 18 degrees with its FAR end swung in toward the rail: the face turns towards the craft
 * coming up behind it, and by the closest point of the pass it is very nearly square to the eye.
 * The near end is out at 2.9 units to port and the far end is 1.1 units clear of the rail, so the
 * craft passes it rather than through it. It breaches the nominal 0.8-of-radius rule at the port
 * end, which is Tour VI's licence: the wall alpha here is 0.15 and the tube is a guide-rail.
 *
 * h IS THE RUNG, SO h HAS TO BE VISIBLE. The curves are integrated by plain Euler at exactly the
 * step the depth ladder is showing, and drawn as what that produces — twenty-four straight hops,
 * each one cross-ticked at its node, so a viewer can count them against the readout's STEP. Euler
 * rather than a better integrator on purpose: the honest picture of "always tangent to the local
 * segment" is a chain of tangents, and a Runge-Kutta curve would be smoother and would quietly
 * stop being the thing the stop is about. The error is real and small at this scale; the crew says
 * as much, and so does this comment.
 *
 * NON-CROSSING IS THE THEOREM, AND IT IS SHOWN THREE WAYS. The four curves are coloured warm to
 * cool from the bottom up, so the colours are an ORDER and a swap would be instantly visible; the
 * tightest approach anywhere along the family is tied with a dashed rule once the curves are
 * complete; and the readout carries the number, plus a crossing count that is actually computed
 * from the table rather than asserted, so a bad integration would confess.
 *
 * Budget: two flushLines (the field at 1.6px, the solutions at 2.8px), four probe beads, two
 * labels. The curves and the closest-approach are integrated once, on the first frame, into a
 * hundred-float table — they do not depend on the clock, only on how much of them is shown.
 */
object SceneSlopeField : MathScene {

    override val reach = 1.6f
    override val deep = 0.25f

    // ------------------------------------------------------------------ the equation
    private const val X0 = -3.0f
    private const val H = 0.25f              // the rung, and the Euler step, and the same number
    private const val STEPS = 24             // ... which carries the figure from -3 to +3
    private const val DECAY = 0.20f

    /** y' = cos x − y/5. One line, deliberately: this is the whole subject of the stop. */
    private fun slope(x: Float, y: Float): Float = cos(x) - DECAY * y

    // ------------------------------------------------------------------ the lattice
    private const val COLS = 13              // pitch 0.5 = 2h, so a hop is half a cell
    private const val ROWS = 9               // pitch 0.45, from -1.8 to +1.8
    private const val COL_PITCH = 0.5f
    private const val ROW_PITCH = 0.45f
    private const val Y_BOT = -1.8f
    private const val Y_TOP = 1.8f
    private const val STRUT = 0.30f          // every dash this long, whatever its angle
    private const val STRUT_A = 0.42f
    private const val FRAME_A = 0.26f

    // ------------------------------------------------------------------ the solutions
    private const val CURVES = 4
    private val START = floatArrayOf(-1.15f, -0.40f, 0.40f, 1.15f)
    // Warm at the bottom, cool at the top. The ramp is the claim: these four never change places.
    private val TINTS = arrayOf(SceneParts.TAKEN, SceneParts.WORK, SceneParts.ADDED, SceneParts.COOL)
    private const val CURVE_A = 0.95f
    private const val NODE_TICK = 0.05f      // the cross-stroke that makes a step countable
    private const val TANGENT = 0.30f        // half-length: exactly twice the lattice's own strut
    private const val BEAD = 0.05f

    // ------------------------------------------------------------------ placement
    private const val SIDE = -2.0f
    private const val UP = 0.05f
    private const val YAW_DEG = 18f
    private val SIN_YAW = sin(YAW_DEG * 0.017453292f)
    private val COS_YAW = cos(YAW_DEG * 0.017453292f)

    // ------------------------------------------------------------------ the loop
    private const val PERIOD = 26f
    private const val WAKE_LEN = 0.05f       // probes fade up out of nothing, so the wrap is clean
    private const val RELEASE_AT = 0.06f
    private const val RELEASE_LEN = 0.56f
    private const val TIE_AT = 0.66f
    private const val TIE_LEN = 0.08f
    private const val CLEAR_AT = 0.90f
    private const val CLEAR_LEN = 0.07f

    // ------------------------------------------------------------------ scratch
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val a3 = FloatArray(3)
    private val b3 = FloatArray(3)
    private val c3 = FloatArray(3)
    private val d3 = FloatArray(3)
    private val tips = FloatArray(CURVES * 3)

    // The integrated family, and what it costs to say the curves never touch. Built once: none of
    // it depends on the clock, only on how much of it is drawn.
    private val ys = FloatArray(CURVES * (STEPS + 1))
    private val runMin = FloatArray(STEPS + 1)
    private var minAt = 0
    private var minLo = 0
    private var crossings = 0
    private var built = false

    private fun build() {
        if (built) return
        for (c in 0 until CURVES) {
            val base = c * (STEPS + 1)
            var y = START[c]
            ys[base] = y
            for (k in 0 until STEPS) {
                y += H * slope(X0 + k * H, y)
                ys[base + k + 1] = y
            }
        }
        // The gap between neighbouring curves, signed and taken low-to-high, so a curve that
        // overtook its neighbour would show up as a negative number rather than be hidden by abs.
        var best = Float.MAX_VALUE
        for (k in 0..STEPS) {
            for (c in 0 until CURVES - 1) {
                val d = ys[(c + 1) * (STEPS + 1) + k] - ys[c * (STEPS + 1) + k]
                if (d <= 0f) crossings++
                if (d < best) { best = d; minAt = k; minLo = c }
            }
            runMin[k] = best
        }
        built = true
    }

    /** A point of the figure, in the sheet's own (x, y), placed in the yawed plane. */
    private fun plot(x: Float, y: Float, out: FloatArray) {
        SceneParts.at(g, x * SIN_YAW, y, x * COS_YAW, out)
    }

    /**
     * What the family is doing, in numbers. The step count is the rung made countable against the
     * cross-ticks on the curves; the closest approach is the theorem, and it is measured off the
     * table rather than claimed.
     */
    override fun readout(kit: SceneKit): String? {
        build()
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val grow = SceneParts.step(c, RELEASE_AT, RELEASE_LEN)
        val k = (grow * STEPS).toInt().coerceIn(0, STEPS)
        return if (grow < 0.999f) {
            String.format(Locale.US, "h %.2f   STEP %d/%d   CLOSEST %.2f", H, k, STEPS, runMin[k])
        } else {
            String.format(Locale.US, "h %.2f   CLOSEST %.2f   CROSSINGS %d", H, runMin[STEPS], crossings)
        }
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build()
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val line = kit.lineBuf
        val q = kit.quality
        var v = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val grow = SceneParts.step(c, RELEASE_AT, RELEASE_LEN)
        // Zero at both ends of the cycle, so the probes' jump back to the launch line happens
        // while nothing is being drawn and the loop has no seam in it.
        val alive = (1f - SceneParts.step(c, CLEAR_AT, CLEAR_LEN)) * SceneParts.step(c, 0f, WAKE_LEN)
        val head = grow * STEPS

        // --- the equation, as a lattice of slopes -------------------------------------------
        // The struts stand for the whole cycle at a fixed brightness. They are not an event: they
        // are the equation, and the equation is there before and after anything is solved.
        val cols = if (q == 0) COLS else COLS / 2 + 1
        val rows = if (q == 0) ROWS else ROWS / 2 + 1
        val colPitch = if (q == 0) COL_PITCH else COL_PITCH * 2f
        val rowPitch = if (q == 0) ROW_PITCH else ROW_PITCH * 2f
        val half = STRUT * 0.5f
        for (ci in 0 until cols) {
            val x = X0 + ci * colPitch
            for (ri in 0 until rows) {
                val y = Y_BOT + ri * rowPitch
                val s = slope(x, y)
                val e = half / sqrt(1f + s * s)
                plot(x - e, y - s * e, a3)
                plot(x + e, y + s * e, b3)
                v = MathMesh.segment(
                    line, v, a3[0], a3[1], a3[2], b3[0], b3[1], b3[2],
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], STRUT_A
                )
            }
        }

        // --- the two lines that make it a graph rather than a swarm --------------------------
        // The launch line is where the four initial conditions are read off; the baseline is the
        // y the equation is measured from. Both dashed, because neither is an object.
        if (q < 2) {
            plot(X0, Y_BOT, a3)
            plot(X0, Y_TOP, b3)
            v = MathMesh.dashed(
                line, v, a3[0], a3[1], a3[2], b3[0], b3[1], b3[2], 9,
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], FRAME_A * 1.6f
            )
            plot(X0, 0f, a3)
            plot(-X0, 0f, b3)
            v = MathMesh.dashed(
                line, v, a3[0], a3[1], a3[2], b3[0], b3[1], b3[2], 22,
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], FRAME_A
            )
        }
        kit.flushLines(v, 1.6f)

        // --- the four solutions ----------------------------------------------------------------
        // Drawn in a second pass at a heavier width: the field is the ground, these are the figure,
        // and one line width is the cheapest way on a waveguide to say which is which.
        v = 0
        val ticks = q == 0
        for (ci in 0 until CURVES) {
            val base = ci * (STEPS + 1)
            val tint = TINTS[ci]
            var tipX = X0
            var tipY = ys[base]
            plot(tipX, tipY, a3)
            for (k in 0 until STEPS) {
                val t = (head - k).coerceIn(0f, 1f)
                if (t <= 0f) break
                val nx = X0 + (k + t) * H
                val ny = ys[base + k] + (ys[base + k + 1] - ys[base + k]) * t
                plot(nx, ny, b3)
                v = MathMesh.segment(
                    line, v, a3[0], a3[1], a3[2], b3[0], b3[1], b3[2],
                    tint[0], tint[1], tint[2], CURVE_A * alive
                )
                a3[0] = b3[0]; a3[1] = b3[1]; a3[2] = b3[2]
                tipX = nx; tipY = ny
                if (t < 1f) break
                // The node between two hops, cross-ticked across the curve so one h can be counted.
                if (ticks) {
                    val s = slope(nx, ny)
                    val e = NODE_TICK / sqrt(1f + s * s)
                    plot(nx - s * e, ny + e, c3)
                    plot(nx + s * e, ny - e, d3)
                    v = MathMesh.segment(
                        line, v, c3[0], c3[1], c3[2], d3[0], d3[1], d3[2],
                        tint[0], tint[1], tint[2], CURVE_A * 0.55f * alive
                    )
                }
            }
            tips[ci * 3] = a3[0]; tips[ci * 3 + 1] = a3[1]; tips[ci * 3 + 2] = a3[2]

            // The segment the probe is obeying RIGHT NOW, drawn over the lattice at double length
            // in the curve's own colour. This is the sentence of the stop in one piece of geometry:
            // the next hop lies along this, because that is what the equation said to do here.
            if (grow > 0.001f && grow < 0.999f) {
                val s = slope(tipX, tipY)
                val e = TANGENT / sqrt(1f + s * s)
                plot(tipX - e, tipY - s * e, c3)
                plot(tipX + e, tipY + s * e, d3)
                v = MathMesh.segment(
                    line, v, c3[0], c3[1], c3[2], d3[0], d3[1], d3[2],
                    tint[0], tint[1], tint[2], alive
                )
            }
        }

        // --- the tightest the family ever gets -------------------------------------------------
        // Tied off once every curve is complete, at the one place along the corridor where two of
        // them come closest. The number is on the HUD; this is only the place it was taken.
        if (q < 2) {
            val tie = SceneParts.step(c, TIE_AT, TIE_LEN) * alive
            if (tie > 0.02f) {
                val x = X0 + minAt * H
                plot(x, ys[minLo * (STEPS + 1) + minAt], a3)
                plot(x, ys[(minLo + 1) * (STEPS + 1) + minAt], b3)
                v = MathMesh.dashed(
                    line, v, a3[0], a3[1], a3[2], b3[0], b3[1], b3[2], 3,
                    SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], tie
                )
            }
        }
        kit.flushLines(v, 2.8f)

        // --- the probes ---------------------------------------------------------------------
        // Four draw calls, and worth them: a lit bead holds its depth in stereo where the head of
        // a two-pixel line does not, and these four are what a viewer follows.
        if (q < 2 && alive > 0.03f) {
            for (ci in 0 until CURVES) {
                kit.ball(
                    tips[ci * 3], tips[ci * 3 + 1], tips[ci * 3 + 2], BEAD, BEAD, BEAD,
                    TINTS[ci], SceneParts.HOT, alive,
                    glow = 1.0f + 2.0f * kit.beat
                )
            }
        }

        // --- notation ---------------------------------------------------------------------
        // Both labels go off the port end of the sheet, level with the field rather than over or
        // under it: the HUD owns the top of the eye and the caption box the bottom, and a label
        // that drifts into either is a label nobody reads.
        if (q < 2) {
            plot(-3.55f, 0.60f, a3)
            val eq = if (q == 0) "y' = cos x − y/5" else "y' = f(x, y)"
            kit.text(eq, a3[0], a3[1], a3[2], 0.20f, SceneParts.CHALK, 0.95f, anchor = 0.5f)
        }
        if (q == 0) {
            // Names the one thing that tells the four curves apart, beside the line they leave from.
            plot(-3.30f, -0.78f, a3)
            kit.text("y_0", a3[0], a3[1], a3[2], 0.17f, SceneParts.STEEL, 0.9f, anchor = 0.5f)
        }
    }
}
