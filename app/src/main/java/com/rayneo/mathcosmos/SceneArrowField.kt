package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 1 of TOUR VI — THE ARROW AT EVERY POINT. "A field is an arrow at every place, and I can
 * feel it in the hull."
 *
 * WHAT IS LEFT FOR THIS STOP TO DO. [SceneAmbientField] already fills the whole ride with the
 * lattice and the flow threads, and it does the design note's job of making the corridor a medium
 * rather than a pipe. Drawing a second lattice here would say nothing the viewer is not already
 * swimming in. So this stop takes the other half of the sentence: not "everywhere" but "a field is
 * an ARROW AT A POINT" — a rule that you hand a place to and that hands you back a vector. The
 * ambient shows the extent; this shows the operation.
 *
 * THE PROBE AND ITS TRACK. One bright bead leaves the craft to port and walks a closed circuit
 * through the open space beside the rail. At the bead, and only at the bead, the field is read and
 * drawn full size: a lit shaft with a head on it, turning and lengthening as the bead moves,
 * because that arrow is the value of one function at one moving argument. Behind it the circuit
 * fills in — at every station it passes, the point is marked with a small cross and the arrow at
 * THAT point is left standing. By the end of the lap the ring is a hoop of arrows hanging in the
 * air, and the picture has been assembled rather than asserted.
 *
 * THE CROSS AT THE FOOT IS THE WHOLE DISTINCTION. The ambient's arrows float free; every arrow
 * here grows out of a marked point, which is exactly what separates "there are arrows about" from
 * "there is an arrow at each place". It costs two segments per station and it is the reason the
 * stop's arrows do not read as more ambient.
 *
 * TWENTY IS A SAMPLING, AND THE CODE SHOULD SAY SO. The claim is about every point of a continuum;
 * twenty is merely how many we stopped and read. The honest part of the picture is the moving
 * arrow, which is defined at every θ the bead passes through; the standing arrows are its
 * footprints. The crew says this out loud and the geometry agrees — the ring's thread is drawn
 * between the stations, so the path is visibly continuous while the readings on it are not.
 *
 * WHY THE CIRCUIT IS NOT FLAT. A ring drawn in one plane is a ring the eye happily flattens into a
 * picture of a ring, and this is the stop where stereo has to carry an argument: a field lives in
 * a volume. So the circuit warps out of its plane — depth runs as sin 2θ, a saddle, which cannot
 * be read as a flat drawing seen at an angle from any viewpoint. The cost is that the stations are
 * not evenly spaced along the arc (about 1.8 to 1 between the tightest and the loosest); the
 * amplitude is held down to 0.95 so that reads as perspective rather than as a fault.
 *
 * COLOUR AND LENGTH ARE THE AMBIENT'S, DELIBERATELY. The same cool-to-warm ramp over the same
 * 0.70..2.10 speed window, so a viewer comparing a standing arrow with the lattice arrow beside it
 * is comparing like with like. A different ramp here would quietly claim a different field. The
 * arrows are drawn a little longer and a good deal brighter than the ambient's, which is all the
 * separation they need.
 *
 * PLACEMENT. Off to port, centred 2.9 units out, and the hoop's outboard edge breaches the
 * passage wall by about a fifth of a unit. That is intentional and is the tour VI exception: the
 * wall alpha here is 0.15, the tube is a guide-rail, and a field that stopped politely at the wall
 * would be wallpaper. Nothing comes closer than 1.4 units to the rail, so the craft flies past the
 * hoop rather than through it and the whole of it is in frame for the approach.
 *
 * Budget: two flushLines (the trail at 2px, the probe's own arrow at 3.6px), one rod, one ball,
 * and at most three labels.
 */
object SceneArrowField : MathScene {

    override val reach = 1.5f

    // ------------------------------------------------------------------ the circuit
    private const val PERIOD = 24f
    private const val STATIONS = 20          // halved once the governor steps in
    private const val TAU = 6.2831855f

    private const val SIDE = -2.9f           // the hoop's centre, in rail-frame units
    private const val UP = 0.15f
    private const val RS = 1.50f             // across the passage
    private const val RU = 1.15f             // and up it
    private const val RA = 0.95f             // ... and out of its own plane, as sin 2θ

    // The lap, then a long rest with every arrow standing, then the trail clears while the bead
    // is already home — so the wrap is invisible: at c = 1 and at c = 0 the scene is one bead.
    private const val WALK_AT = 0.06f
    private const val WALK_LEN = 0.62f
    private const val REST_AT = WALK_AT + WALK_LEN
    private const val CLEAR_AT = 0.90f
    private const val CLEAR_LEN = 0.09f

    // ------------------------------------------------------------------ the arrows
    // The ambient's ramp, matched on purpose: same field, same reading of it.
    private const val SLOW = 0.70f
    private const val FAST = 2.10f
    private const val ARROW_MIN = 0.24f
    private const val ARROW_SPAN = 0.34f
    private const val HEAD = 0.34f
    private const val TRAIL = 0.78f          // + the glint below comes to exactly 1: no saturation
    private const val TRACK = 0.26f          // the probe's path is a construction line, not a datum
    private const val FOOT = 0.045f          // half-width of the cross marking a point

    private const val BIG_MIN = 0.46f
    private const val BIG_SPAN = 0.46f
    private const val HEAD_BIG = 0.26f
    private const val SHAFT = 0.026f
    private const val BEAD = 0.055f

    private const val LAB_OUT = 0.40f        // the arrow's name, outboard of the hoop
    private const val LAB_IN = 0.30f         // the point's name, inboard of it
    private const val CLAIM_OUT = 0.42f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val fv = FloatArray(3)
    private val tip = FloatArray(3)
    private val prev = FloatArray(3)
    private val first = FloatArray(3)
    // Four components: the lit shader reads base[3] as an alpha multiplier, so a three-component
    // tint would crash the moment it reached kit.rod.
    private val tint = FloatArray(4)

    // readout() and draw() are called independently and in no guaranteed order, and neither may
    // depend on the other having run this frame — so the readout rebuilds the probe's position
    // from scratch, in arrays of its own.
    private val rf = FloatArray(12)
    private val rg = FloatArray(12)
    private val rp = FloatArray(3)
    private val rv = FloatArray(3)

    /** Normalised speed, 0 at [SLOW] and below, 1 at [FAST] and above. */
    private fun norm(s: Float): Float = ((s - SLOW) / (FAST - SLOW)).coerceIn(0f, 1f)

    /** The tour's cool-to-warm ramp at normalised speed [t], into [tint]. */
    private fun tintFor(t: Float) {
        val c = SceneParts.COOL
        val w = SceneParts.WORK
        tint[0] = c[0] + (w[0] - c[0]) * t
        tint[1] = c[1] + (w[1] - c[1]) * t
        tint[2] = c[2] + (w[2] - c[2]) * t
        tint[3] = 1f
    }

    /** The circuit at angle [th], in the stage frame [fr]. */
    private fun ringPoint(fr: FloatArray, th: Float, out: FloatArray) {
        SceneParts.at(fr, RS * cos(th), RU * sin(th), RA * sin(th * 2f), out)
    }

    private fun countFor(quality: Int) = if (quality == 0) STATIONS else STATIONS / 2

    /**
     * What the probe is reading right now, and how much of the circuit it has read. The strength
     * is the number that has to be READ rather than seen — length and colour say "faster out
     * there", but only the HUD can say how much faster.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasField) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val w = SceneParts.step(c, WALK_AT, WALK_LEN)
        val count = countFor(kit.quality)
        // The HUD asks the scene of the stop the craft has DEPARTED, and hands the scene no index
        // of its own, so the stop is the floor of the rail position.
        SceneParts.stage(kit, kit.progress.toInt().coerceAtLeast(0).toFloat(), SIDE, UP, rf, rg)
        ringPoint(rg, w * TAU, rp)
        kit.fieldAt(rp[0], rp[1], rp[2], rv)
        val s = sqrt(rv[0] * rv[0] + rv[1] * rv[1] + rv[2] * rv[2])
        val hold = 1f - SceneParts.step(c, CLEAR_AT, CLEAR_LEN)
        val read = if (hold < 0.5f) 0 else (w * count).toInt().coerceIn(0, count)
        return String.format(Locale.US, "|F| %.2f   ARROWS %d / %d", s, read, count)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // A tour with no field has nothing to point at, and this scene would draw a hoop of
        // zero-length arrows rather than nothing at all.
        if (!kit.hasField) return

        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val line = kit.lineBuf
        var v = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val w = SceneParts.step(c, WALK_AT, WALK_LEN)
        val hold = 1f - SceneParts.step(c, CLEAR_AT, CLEAR_LEN)
        val count = countFor(kit.quality)
        val head = w * count                 // where the probe is, in station units
        val marks = kit.quality < 2          // crosses and track are the first things to go

        // The foot cross is built from the camera's own axes so it stays a cross from wherever the
        // hoop is being looked at — a cross built in the rail frame goes edge-on and vanishes.
        val cx = kit.camRightX * FOOT; val cy = kit.camRightY * FOOT; val cz = kit.camRightZ * FOOT
        val ux = kit.camUpX * FOOT; val uy = kit.camUpY * FOOT; val uz = kit.camUpZ * FOOT

        // ------------------------------------------------------- the stations already read
        for (k in 0 until count) {
            ringPoint(g, k.toFloat() / count * TAU, o)
            if (k == 0) { first[0] = o[0]; first[1] = o[1]; first[2] = o[2] }
            val lit = SceneParts.ease((head - k) / 0.6f) * hold

            // The track is drawn only as far as the probe has actually been, so the hoop is the
            // record of the walk rather than a wire that was there all along.
            if (k > 0 && marks) {
                v = MathMesh.segment(
                    line, v, prev[0], prev[1], prev[2], o[0], o[1], o[2],
                    SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], TRACK * lit
                )
            }
            prev[0] = o[0]; prev[1] = o[1]; prev[2] = o[2]
            if (lit <= 0.02f) continue

            kit.fieldAt(o[0], o[1], o[2], fv)
            val s = sqrt(fv[0] * fv[0] + fv[1] * fv[1] + fv[2] * fv[2])
            if (s < 1e-4f) continue
            val t = norm(s)
            tintFor(t)

            if (marks) {
                v = MathMesh.segment(
                    line, v, o[0] - cx, o[1] - cy, o[2] - cz, o[0] + cx, o[1] + cy, o[2] + cz,
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], lit * 0.60f
                )
                v = MathMesh.segment(
                    line, v, o[0] - ux, o[1] - uy, o[2] - uz, o[0] + ux, o[1] + uy, o[2] + uz,
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], lit * 0.60f
                )
            }

            // A glint as the probe goes by, so a reading looks taken rather than switched on.
            val glint = (1f - abs(head - k) / 1.6f).coerceAtLeast(0f)
            val len = (ARROW_MIN + ARROW_SPAN * t) / s
            // The eye-to-point vector as the arrow's "up": it puts the barbs across the line of
            // sight, which is what stops a hoop of arrows reading as a hoop of sticks.
            v = MathMesh.arrow(
                line, v, o[0], o[1], o[2], fv[0] * len, fv[1] * len, fv[2] * len,
                o[0] - kit.camX, o[1] - kit.camY, o[2] - kit.camZ,
                tint[0], tint[1], tint[2], lit * (TRAIL + 0.22f * glint * glint), HEAD
            )
        }

        // The hoop closes only once the probe is home, which is the moment the claim is finished.
        if (marks) {
            val close = SceneParts.ease((w - 0.94f) / 0.06f) * hold
            if (close > 0.02f) {
                v = MathMesh.segment(
                    line, v, prev[0], prev[1], prev[2], first[0], first[1], first[2],
                    SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], TRACK * close
                )
            }
        }
        kit.flushLines(v, 2f)

        // ------------------------------------------------------------------ the probe
        val th = w * TAU
        ringPoint(g, th, o)
        kit.fieldAt(o[0], o[1], o[2], fv)
        val s = sqrt(fv[0] * fv[0] + fv[1] * fv[1] + fv[2] * fv[2]).coerceAtLeast(1e-4f)
        val t = norm(s)
        tintFor(t)
        val big = (BIG_MIN + BIG_SPAN * t) / s
        tip[0] = o[0] + fv[0] * big
        tip[1] = o[1] + fv[1] * big
        tip[2] = o[2] + fv[2] * big

        // A solid shaft, not a line: this one arrow is the subject of the stop, and a lit cylinder
        // catches the lamp and holds its depth in stereo where a three-pixel line does not. The
        // head is still MathMesh's, drawn in a second pass at a heavier width so it matches the
        // small arrows in kind; its own shaft segment runs down the inside of the rod, where the
        // depth test hides it.
        kit.rod(o[0], o[1], o[2], tip[0], tip[1], tip[2], SHAFT, tint, SceneParts.HOT, 0.8f)
        val b = MathMesh.arrow(
            line, 0, o[0], o[1], o[2], fv[0] * big, fv[1] * big, fv[2] * big,
            o[0] - kit.camX, o[1] - kit.camY, o[2] - kit.camZ,
            tint[0], tint[1], tint[2], 1f, HEAD_BIG
        )
        kit.flushLines(b, 3.6f)

        kit.ball(
            o[0], o[1], o[2], BEAD, BEAD, BEAD, SceneParts.HOT, tint, 1f,
            glow = 1.1f + 2.4f * kit.beat, small = false
        )

        // ------------------------------------------------------------------ notation
        // Everything goes BESIDE the figure. The two probe labels ride the hoop's own radial, one
        // in and one out, so they sit either side of the bead and never cross the ring.
        //
        // F(p) names the one piece of geometry the stop is about and survives quality 1; the point
        // it is a function OF, and the claim tying the two together, are secondary and go at the
        // first step down.
        val cs = cos(th)
        val sn = sin(th)
        val depth = RA * sin(th * 2f)
        if (kit.quality < 2) {
            SceneParts.at(g, (RS + LAB_OUT) * cs, (RU + LAB_OUT) * sn, depth, o)
            kit.text("F(p)", o[0], o[1], o[2], 0.21f, tint, 1f)
        }
        if (kit.quality == 0) {
            SceneParts.at(g, (RS - LAB_IN) * cs, (RU - LAB_IN) * sn, depth, o)
            kit.text("p", o[0], o[1], o[2], 0.17f, SceneParts.CHALK, 0.9f)

            // The claim, at the hoop's port edge and growing further to port, so it never crosses
            // the figure and never lands on the rail the craft is about to fly down.
            SceneParts.at(g, -(RS + CLAIM_OUT), 0f, 0f, o)
            val claim = if (c < REST_AT) "v = F(p)" else "F : (x, y, z) → v"
            kit.text(
                claim, o[0], o[1], o[2], 0.18f, SceneParts.CHALK, 0.95f,
                GlyphBoard.Style.MATH, 1f, anchor = 0.5f
            )
        }
    }
}
