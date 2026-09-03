package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The coordinate plane, hung across the passage: two ruled axes with numbered ticks, a faint
 * grid behind them, a curve plotted on it, and a bead running along the curve dropping dashed
 * lines to each axis so you can read its two coordinates off the rulers.
 *
 * This is the first scene of the project and deliberately the plainest one. Everything the other
 * scenes need is proved here: a curve sampled in world space through the rail frame, notation
 * that stays readable through the waveguides, ticks that line up with the numbers beside them,
 * and a moving element that makes the picture a thing happening rather than a diagram.
 *
 * The plane stands UPRIGHT in the passage, square to the rail, so the craft flies through the
 * origin: x runs to the viewer's right along the frame's side vector, y up along its up vector.
 * The graph is therefore not a picture on a wall — the viewer is inside it, at (0, 0).
 */
object ScenePlane : MathScene {

    override val reach = 1.6f
    override val focusSide = 0f
    override val focusUp = 0f
    override val focusRadius = 1.7f

    private val AXIS = floatArrayOf(0.62f, 0.72f, 0.95f, 1f)
    private val GRID = floatArrayOf(0.24f, 0.30f, 0.48f, 1f)
    private val CURVE = floatArrayOf(1f, 0.72f, 0.34f, 1f)
    private val BEAD = floatArrayOf(1f, 0.86f, 0.55f, 1f)
    private val BEAD_HOT = floatArrayOf(1f, 0.98f, 0.86f, 1f)
    private val LABEL = floatArrayOf(0.86f, 0.90f, 1f, 1f)

    private val f = FloatArray(12)
    private val a = FloatArray(3)
    private val b = FloatArray(3)

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        kit.frame(i.toFloat(), f)
        val cx = f[0]; val cy = f[1]; val cz = f[2]
        val sx = f[6]; val sy = f[7]; val sz = f[8]      // plane x
        val ux = f[9]; val uy = f[10]; val uz = f[11]    // plane y

        // One world unit per graph unit, scaled so five units each way just clears the passage.
        val unit = kit.radius(i.toFloat()) * 0.30f
        val ticks = if (kit.quality == 0) 5 else 4

        val buf = kit.lineBuf
        var v = 0

        // The grid first, so the axes and the curve draw over it.
        if (kit.quality < 2) {
            v = MathMesh.grid(
                buf, v, cx, cy, cz,
                sx * unit, sy * unit, sz * unit,
                ux * unit, uy * unit, uz * unit,
                ticks, GRID[0], GRID[1], GRID[2], 0.30f
            )
        }
        // x axis, ticked along y; y axis, ticked along x.
        v = MathMesh.axis(
            buf, v, cx, cy, cz, sx, sy, sz, ux, uy, uz,
            unit, ticks, unit * 0.13f, AXIS[0], AXIS[1], AXIS[2], 0.95f
        )
        v = MathMesh.axis(
            buf, v, cx, cy, cz, ux, uy, uz, sx, sy, sz,
            unit, ticks, unit * 0.13f, AXIS[0], AXIS[1], AXIS[2], 0.95f
        )

        // The curve. A parabola: the first shape in mathematics that is not a straight line, and
        // the one every later idea keeps coming back to.
        val n0 = if (kit.quality == 0) 96 else 48
        val lo = -ticks.toFloat()
        val hi = ticks.toFloat()
        v = MathMesh.curve(buf, v, n0, lo, hi, CURVE[0], CURVE[1], CURVE[2], 0.95f, false, a, b) { t, out ->
            val yy = (t * t * 0.28f).coerceAtMost(hi)
            out[0] = cx + sx * t * unit + ux * yy * unit
            out[1] = cy + sy * t * unit + uy * yy * unit
            out[2] = cz + sz * t * unit + uz * yy * unit
        }

        // The bead: a point that exists at one x at a time, so the curve reads as a rule being
        // obeyed rather than a shape that was always there.
        val sweep = ((kit.seconds * 0.13f) % 1f)
        val bx = lo + (hi - lo) * sweep
        val by = (bx * bx * 0.28f).coerceAtMost(hi)
        val px = cx + sx * bx * unit + ux * by * unit
        val py = cy + sy * bx * unit + uy * by * unit
        val pz = cz + sz * bx * unit + uz * by * unit

        // Dashed drops to each axis — the two numbers that ARE the point.
        v = MathMesh.dashed(
            buf, v, px, py, pz,
            cx + sx * bx * unit, cy + sy * bx * unit, cz + sz * bx * unit,
            8, BEAD[0], BEAD[1], BEAD[2], 0.55f
        )
        v = MathMesh.dashed(
            buf, v, px, py, pz,
            cx + ux * by * unit, cy + uy * by * unit, cz + uz * by * unit,
            8, BEAD[0], BEAD[1], BEAD[2], 0.55f
        )
        kit.flushLines(v, 2.5f)

        val pulse = 0.55f + 0.45f * sin(kit.seconds * 2.2f)
        val r = unit * 0.11f
        kit.ball(px, py, pz, r, r, r, BEAD_HOT, BEAD, 1f, 0f, 0f, 1f, 0f, 0f, 0.6f + 0.8f * pulse)

        // ---- notation ---------------------------------------------------------------------
        // Tick numbers sit just outside their ticks, on both rulers.
        val glyph = unit * 0.30f
        if (kit.quality == 0) {
            for (k in -ticks..ticks) {
                if (k == 0) continue
                val t = k.toFloat()
                kit.text(
                    k.toString(),
                    cx + sx * t * unit - ux * unit * 0.30f,
                    cy + sy * t * unit - uy * unit * 0.30f,
                    cz + sz * t * unit - uz * unit * 0.30f,
                    glyph * 0.8f, LABEL, 0.75f, GlyphBoard.Style.SMALL, 0.9f
                )
                kit.text(
                    k.toString(),
                    cx + ux * t * unit - sx * unit * 0.34f,
                    cy + uy * t * unit - sy * unit * 0.34f,
                    cz + uz * t * unit - sz * unit * 0.34f,
                    glyph * 0.8f, LABEL, 0.75f, GlyphBoard.Style.SMALL, 0.9f
                )
            }
        }
        // The axes name themselves at their tips.
        val tip = (ticks + 0.55f)
        kit.text("x", cx + sx * tip * unit, cy + sy * tip * unit, cz + sz * tip * unit, glyph, AXIS, 0.95f)
        kit.text("y", cx + ux * tip * unit, cy + uy * tip * unit, cz + uz * tip * unit, glyph, AXIS, 0.95f)

        // The rule the curve is obeying, set beside the curve's upper arm.
        val eqX = hi * 0.72f
        val eqY = (eqX * eqX * 0.28f)
        kit.text(
            "y = x^2",
            cx + sx * eqX * unit + ux * eqY * unit + sx * unit * 0.5f,
            cy + sy * eqX * unit + uy * eqY * unit + sy * unit * 0.5f,
            cz + sz * eqX * unit + uz * eqY * unit + sz * unit * 0.5f,
            glyph * 1.35f, CURVE, 0.95f, GlyphBoard.Style.MATH, 1.15f, anchor = -0.5f
        )
        // And the bead says what it currently is.
        val bxs = String.format("%.1f", bx)
        val bys = String.format("%.1f", by)
        kit.text(
            "($bxs, $bys)", px, py, pz, glyph * 0.95f, BEAD_HOT, 0.9f,
            GlyphBoard.Style.PLAIN, 1.1f, rise = 0.85f
        )
    }
}
