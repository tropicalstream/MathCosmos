package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * THE WHEEL — a ring of light standing across the passage, a bead running round it at a steady
 * rate, and the bead's two shadows unrolled down the corridor as the two waves everybody was made
 * to memorise.
 *
 * The claim of this stop is that sine and cosine are not formulae with graphs attached; they are
 * the two shadows of one point going round a circle, and the graph is the record of a shadow. So
 * the scene is built to make that record LITERAL. The bead goes round. A pen runs away down the
 * rail at a rate fixed to the angle, and behind the pen lies the trace. Nothing is asserted: the
 * wave exists only as far as the bead has actually turned, and when the bead reaches 360° the pen
 * has reached the far end and the whole turn stands there to be looked at.
 *
 * The corridor is the angle axis, which is the one decision the rest of the scene follows from.
 * A full turn drawn at a readable amplitude needs five or six units of length, and the passage is
 * the only direction with that much room — across it there is barely three. It also means the
 * craft flies ALONG θ: you approach the wheel head on, pass through the centre of the ring, and
 * then travel the graph from 0 to 2π at the speed of the ride.
 *
 * Where each wave is anchored is not decoration, it is the argument:
 *
 *  - The sine wave leaves the CENTRE of the ring, because sin 0 = 0. Its height at the wheel end
 *    is the point where the cosine strut lands on the vertical diameter — the bead's height,
 *    marked on the axis — and a dashed line carries exactly that height out to the pen. The
 *    sine graph is therefore not beside the wheel, it is continuous with it.
 *  - The cosine wave leaves the TOP of its own lane, level with the top of the wheel, because
 *    cos 0 = 1. Turning the bead's horizontal shadow into a height is the one step here a viewer
 *    is entitled to doubt, so it is shown rather than claimed: a dim second bead rides a quarter
 *    turn ahead of the first, and its HEIGHT is cos θ, since cos θ = sin(θ + 90°). The dashed
 *    lead runs from that bead's height across to the lane and then out to the cosine pen. The
 *    quarter-turn offset between the two waves is the same quarter turn, drawn twice.
 *
 * Amber is the vertical shadow and everything that measures it; teal is the horizontal shadow.
 * Those two colours are fixed for the whole stop — strut, wave, pen, markers — because a viewer
 * who has lost track of which wave is which has lost the stop.
 *
 * The sweep is linear rather than eased, which is the one place this scene departs from the
 * house habit of easing every move. A constant rate is the entire content of the takeaway: a
 * sine wave is what a steady turn looks like from the side, and an eased bead would draw a wave
 * that is not a sine wave. The rest that every looping scene needs is taken at the END of the
 * turn instead, with the finished picture standing still, which is also when it is worth seeing.
 */
object SceneWheel : MathScene {

    // The wave runs about six units down the corridor, well over a third of the way to the next
    // stop, so the landmark must not be culled the moment its ring is behind the camera.
    override val reach = 1.5f
    override val deep = 0.5f

    // Sixteen seconds of turning, eight of standing still. Long enough that the craft's own pass
    // rarely coincides with the bare moment just after the trace is wiped.
    private const val PERIOD = 24f
    private const val SWEEP_AT = 0.03f
    private const val SWEEP_LEN = 0.63f
    private const val REST_AT = 0.70f
    private const val TWO_PI = 6.2831855f
    private const val PI_F = 3.1415927f
    private const val HALF_PI = 1.5707964f
    private const val DEG = 57.29578f

    // The tour's palette, aliased to the roles it plays here rather than restated, so this stop
    // stays the same amber and teal as the rest of Tour I.
    private val SIN_C = SceneParts.WORK      // the vertical shadow: strut, wave, pen, markers
    private val COS_C = SceneParts.ADDED     // the horizontal shadow, and its lane
    private val RING = SceneParts.CHALK      // the wheel itself, its diameters, its construction
    private val HOT = SceneParts.HOT         // the bead's highlight

    private val f = FloatArray(12)   // rail frame scratch for stage()
    private val g = FloatArray(12)   // the stage: centre, right, up, forward
    private val pt = FloatArray(3)   // a world point
    private val pu = FloatArray(3)   // a second world point, for the ends of a segment
    private val ca = FloatArray(3)   // curve scratch
    private val cb = FloatArray(3)

    /**
     * The angle the wheel is at, from the clock alone, so [readout] and [draw] cannot disagree.
     * Zero for the last third of the cycle: that is the rest, not a stall.
     */
    private fun angleAt(seconds: Float): Float {
        val c = SceneParts.cycle(seconds, PERIOD)
        return TWO_PI * ((c - SWEEP_AT) / SWEEP_LEN).coerceIn(0f, 1f)
    }

    /** Two decimals with a real minus sign, and no "−0.00" at the crossings. */
    private fun value(v: Float): String {
        val w = if (abs(v) < 0.005f) 0f else v
        val s = String.format(Locale.US, "%.2f", w)
        return if (s[0] == '-') "−" + s.substring(1) else s
    }

    override fun readout(kit: SceneKit): String? {
        val t = angleAt(kit.seconds)
        var deg = (t * DEG + 0.5f).toInt()
        if (deg >= 360) deg = 0                     // 2π and 0 are the same place on the wheel
        return "θ ${deg}°   sin ${value(sin(t))}   cos ${value(cos(t))}"
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val pos = i.toFloat()
        SceneParts.stage(kit, pos, 0f, 0f, f, g)

        // A unit circle scaled to the passage: radius about 1.2 at this stop, and whatever fits
        // if the rail is ever re-cut. Everything else in the scene is a multiple of it, so the
        // wave's peak is the wheel's radius by construction and not by a coincidence of numbers.
        val rr = kit.radius(pos) * 0.35f
        val span = rr * 5f                  // rail length of one whole turn
        val axis = span / TWO_PI            // rail units per radian
        val lane = rr * 1.30f               // where the cosine graph's own zero line runs

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val phi = angleAt(kit.seconds)
        val settle = SceneParts.step(c, REST_AT, 0.12f)   // the finished turn brightening
        val cs = cos(phi)
        val sn = sin(phi)
        val drawing = phi > 0.02f

        val buf = kit.lineBuf
        var v = 0

        // ---- the wheel ----------------------------------------------------------------------
        val segs = if (kit.quality == 0) 32 else 20
        v = MathMesh.arc(
            buf, v, g[0], g[1], g[2], g[3], g[4], g[5], g[6], g[7], g[8],
            rr, 0f, TWO_PI, segs, RING[0], RING[1], RING[2], 0.95f
        )
        // The two diameters the struts land on. Dim: they are the paper, not the drawing.
        SceneParts.at(g, -rr, 0f, 0f, pt); SceneParts.at(g, rr, 0f, 0f, pu)
        v = MathMesh.segment(buf, v, pt[0], pt[1], pt[2], pu[0], pu[1], pu[2], RING[0], RING[1], RING[2], 0.28f)
        SceneParts.at(g, 0f, -rr, 0f, pt); SceneParts.at(g, 0f, rr, 0f, pu)
        v = MathMesh.segment(buf, v, pt[0], pt[1], pt[2], pu[0], pu[1], pu[2], RING[0], RING[1], RING[2], 0.28f)

        // The turning arm. Without it the arc below has nothing to be an angle between.
        SceneParts.at(g, rr * cs, rr * sn, 0f, pu)
        v = MathMesh.segment(buf, v, g[0], g[1], g[2], pu[0], pu[1], pu[2], RING[0], RING[1], RING[2], 0.60f)

        // The angle itself, swept from the positive horizontal round to the bead.
        if (kit.quality < 2 && drawing) {
            val an = max(2, (18f * phi / TWO_PI).toInt())
            v = MathMesh.arc(
                buf, v, g[0], g[1], g[2], g[3], g[4], g[5], g[6], g[7], g[8],
                rr * 0.30f, 0f, phi, an, RING[0], RING[1], RING[2], 0.80f
            )
        }

        // ---- the angle axis, twice ------------------------------------------------------------
        // Ruled at π/6 a tick, which is the ring spacing this stop is scaled in on the HUD, so a
        // viewer can count the corridor off in twelfths of a turn rather than trusting the curve.
        val ticks = if (kit.quality == 0) 12 else 4
        val unit = span / ticks
        v = MathMesh.axis(
            buf, v, g[0], g[1], g[2], g[9], g[10], g[11], g[3], g[4], g[5],
            unit, ticks, rr * 0.09f, RING[0], RING[1], RING[2], 0.45f, negative = false
        )
        SceneParts.at(g, lane, 0f, 0f, pt)
        v = MathMesh.axis(
            buf, v, pt[0], pt[1], pt[2], g[9], g[10], g[11], g[3], g[4], g[5],
            unit, ticks, rr * 0.09f, RING[0], RING[1], RING[2], 0.45f, negative = false
        )

        // ---- the two traces ---------------------------------------------------------------
        // Sampled only as far as the bead has turned, at a fixed number of segments per radian,
        // so the trace has the same smoothness at a quarter turn as at a full one.
        if (drawing) {
            val full = when (kit.quality) { 0 -> 96; 1 -> 56; else -> 32 }
            val steps = max(2, (full * phi / TWO_PI).toInt())
            val wave = 0.72f + 0.28f * settle
            v = MathMesh.curve(buf, v, steps, 0f, phi, SIN_C[0], SIN_C[1], SIN_C[2], wave, false, ca, cb) { t, out ->
                SceneParts.at(g, 0f, rr * sin(t), t * axis, out)
            }
            v = MathMesh.curve(buf, v, steps, 0f, phi, COS_C[0], COS_C[1], COS_C[2], wave, false, ca, cb) { t, out ->
                SceneParts.at(g, lane, rr * cos(t), t * axis, out)
            }
        }

        // ---- the struts, and the height being carried out to the pen ------------------------
        // The sine strut drops from the bead to the horizontal diameter; the cosine strut runs
        // across to the vertical diameter, and lands exactly where the sine trace begins.
        val far = phi * axis
        if (kit.quality < 2 && drawing) {
            val dash = if (kit.quality == 0) 12 else 7
            SceneParts.at(g, 0f, rr * sn, 0f, pt)
            SceneParts.at(g, 0f, rr * sn, far, pu)
            v = MathMesh.dashed(buf, v, pt[0], pt[1], pt[2], pu[0], pu[1], pu[2], dash, SIN_C[0], SIN_C[1], SIN_C[2], 0.42f)
            // The quarter-turn bead's height is cos θ; carry it sideways into its own lane and
            // then out to the cosine pen. Two dashed legs, both at constant height, so nothing
            // about the value changes on the way.
            SceneParts.at(g, -rr * sn, rr * cs, 0f, pt)
            SceneParts.at(g, lane, rr * cs, 0f, pu)
            v = MathMesh.dashed(buf, v, pt[0], pt[1], pt[2], pu[0], pu[1], pu[2], 7, COS_C[0], COS_C[1], COS_C[2], 0.36f)
            SceneParts.at(g, lane, rr * cs, far, pt)
            v = MathMesh.dashed(buf, v, pu[0], pu[1], pu[2], pt[0], pt[1], pt[2], dash, COS_C[0], COS_C[1], COS_C[2], 0.42f)
        }
        kit.flushLines(v, 2.2f)

        // The two struts are rods rather than lines on purpose: they are the only things here
        // being MEASURED, and a solid reads as a measuring stick where a line reads as a plot.
        val strut = rr * 0.035f
        SceneParts.at(g, rr * cs, rr * sn, 0f, pt)
        SceneParts.at(g, rr * cs, 0f, 0f, pu)
        kit.rod(pt[0], pt[1], pt[2], pu[0], pu[1], pu[2], strut, SIN_C, HOT, 0.7f)
        SceneParts.at(g, 0f, rr * sn, 0f, pu)
        kit.rod(pt[0], pt[1], pt[2], pu[0], pu[1], pu[2], strut, COS_C, HOT, 0.7f)

        // ---- the beads ----------------------------------------------------------------------
        val br = rr * 0.10f
        kit.ball(
            pt[0], pt[1], pt[2], br, br, br, HOT, SIN_C, 1f,
            glow = 1.1f + 0.7f * kit.beat, small = false
        )
        // The quarter-turn ghost: dim, because it is a construction, not a second bead to follow.
        if (kit.quality < 2) {
            SceneParts.at(g, -rr * sn, rr * cs, 0f, pt)
            kit.ball(pt[0], pt[1], pt[2], br * 0.6f, br * 0.6f, br * 0.6f, COS_C, HOT, 0.55f, glow = 0.5f)
        }
        // The pens: the live end of each trace, where the value is being written down.
        if (drawing) {
            SceneParts.at(g, 0f, rr * sn, far, pt)
            kit.ball(pt[0], pt[1], pt[2], br * 0.7f, br * 0.7f, br * 0.7f, SIN_C, HOT, 1f, glow = 0.9f)
            SceneParts.at(g, lane, rr * cs, far, pt)
            kit.ball(pt[0], pt[1], pt[2], br * 0.7f, br * 0.7f, br * 0.7f, COS_C, HOT, 1f, glow = 0.9f)
        }
        // Quarter-turn markers, dropped as the pen passes them. They are what makes the shift
        // between the waves countable: the amber peaks sit a quarter of a turn short of the teal
        // ones all the way down the corridor, and you can see the gap rather than be told it.
        if (kit.quality < 2) {
            val mr = rr * 0.055f
            val mg = 0.45f + 0.55f * settle
            val stride = if (kit.quality == 0) 1 else 2
            var k = 0
            while (k <= 4) {
                val t = k * HALF_PI
                if (t <= phi + 1e-3f) {
                    val d = t * axis
                    // The sine's own zero at k = 0 is the centre of the ring, already the most
                    // conspicuous point in the scene; a bead there would only sit in the craft's way.
                    if (k > 0) {
                        SceneParts.at(g, 0f, rr * sin(t), d, pt)
                        kit.ball(pt[0], pt[1], pt[2], mr, mr, mr, SIN_C, HOT, 0.9f, glow = mg)
                    }
                    SceneParts.at(g, lane, rr * cos(t), d, pt)
                    kit.ball(pt[0], pt[1], pt[2], mr, mr, mr, COS_C, HOT, 0.9f, glow = mg)
                }
                k += stride
            }
        }

        // ---- notation -----------------------------------------------------------------------
        // Few and large: the display is 640x480 an eye, and every number that wants reading is
        // on the HUD instead. What is named here is only what a colour alone cannot say.
        val glyph = rr * 0.34f
        // The waves name themselves, once the pen has run far enough for the name to have a wave
        // under it. Both at the half-turn mark, side by side, so they can be compared.
        val named = SceneParts.ease((phi - 2.4f) * 0.9f)
        SceneParts.at(g, 0f, rr * 1.30f, PI_F * axis, pt)
        kit.text("sin θ", pt[0], pt[1], pt[2], glyph, SIN_C, named)
        SceneParts.at(g, lane, rr * 1.30f, PI_F * axis, pt)
        kit.text("cos θ", pt[0], pt[1], pt[2], glyph, COS_C, named)

        if (kit.quality == 0) {
            // The angle, on its arc.
            val hc = cos(phi * 0.5f); val hs = sin(phi * 0.5f)
            SceneParts.at(g, rr * 0.48f * hc, rr * 0.48f * hs, 0f, pt)
            kit.text("θ", pt[0], pt[1], pt[2], glyph, RING, (phi * 2f).coerceIn(0f, 1f))
            // The radius is 1. Without this the wheel is some circle and the readout's numbers
            // are lengths in an unnamed unit; with it, they are the sine and cosine themselves.
            SceneParts.at(g, rr * (0.55f * cs - 0.17f * sn), rr * (0.55f * sn + 0.17f * cs), 0f, pt)
            kit.text("1", pt[0], pt[1], pt[2], glyph * 0.72f, RING, 0.75f, GlyphBoard.Style.SMALL)
            // And the corridor is ruled in the angle, not in metres.
            SceneParts.at(g, -rr * 0.42f, -rr * 0.24f, PI_F * axis, pt)
            kit.text("π", pt[0], pt[1], pt[2], glyph * 0.8f, RING, 0.7f, GlyphBoard.Style.SMALL)
            SceneParts.at(g, -rr * 0.42f, -rr * 0.24f, TWO_PI * axis, pt)
            kit.text("2π", pt[0], pt[1], pt[2], glyph * 0.8f, RING, 0.7f, GlyphBoard.Style.SMALL)
        }
    }
}
