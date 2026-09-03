package com.rayneo.mathcosmos

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * THE TURN — an arrow that is multiplied by i, four times, and comes home.
 *
 * Every other stop on this tour rearranges area. This one rearranges DIRECTION, and it is the
 * first stop where the thing being demonstrated is a motion rather than a shape: multiplying by i
 * is a quarter turn, and nothing else. Nothing else is the whole claim — the length does not
 * change, the plane does not shear, no second operation is hiding inside it. The only way to show
 * "and nothing else" is to let the viewer watch the same arrow do it four times and arrive back
 * exactly where it started, so the first half of the cycle is precisely that and no more.
 *
 * The arrow in that first half has modulus EXACTLY one, and the axes are ruled so that their tips
 * are the numbers 1 and i. Its head therefore lands on the tick that names it, and the labels
 * "1", "i", "−1", "−i" at the head are literally the number the arrow is, not a caption. This is
 * why the axes stop at one unit instead of running on to two: an axis whose tip is labelled "1"
 * had better end at 1.
 *
 * The second half asks the question the first half provokes — what does multiplying by something
 * that is NOT i do? — and answers it with one product built in front of you: z₁ of modulus 1.2 at
 * 90°, z₂ of modulus 0.8 at 30°, and their product at 120° with modulus 0.96. Both numbers are
 * honest on screen: the product arrow is drawn at 1.2 × 0.8 = 0.96 units of the same ruler the
 * factors are drawn on, and its direction is the sum of the two arguments and not a hand-placed
 * angle. The arrow is stretched from 1 to 1.2 at the start of that half on purpose, because a
 * factor of modulus one only turns; you cannot see multiplication stretch until one of the
 * factors is longer than the unit it is measured against.
 *
 * That the arguments ADD is the part a diagram usually asserts, so here it is performed: the
 * second angle is drawn where it is born, at the origin between 0° and 30°, and then slides
 * bodily up onto the far end of the first angle until it spans 90° to 120°. Two angles laid end
 * to end is what addition of angles IS, and the product arrow springs out along the far edge of
 * the second one. There is deliberately no parallelogram anywhere in this scene: a parallelogram
 * is how complex numbers ADD, and putting one here would teach the wrong picture.
 *
 * The whole figure hangs square across the passage, centred on the rail, so the craft flies
 * through the origin and the turn happens around the viewer rather than on a wall in front of
 * them. Everything is built from the stage frame; nothing assumes the rail is straight or axis
 * aligned. Radius at this stop is 3.0, so one unit of the plane is 0.38 of that and the furthest
 * label sits about 1.7 units out, comfortably clear of the wall.
 */
object SceneTurn : MathScene {

    override val reach = 1.4f

    // ---- the loop -----------------------------------------------------------------------
    // 28 seconds: four quarter turns with about two seconds of rest on each of 1, i, −1, −i, then
    // fourteen seconds to build one product and read it. A viewer arriving mid-cycle sees the
    // second half first and the turns on the way past; both halves stand on their own.
    private const val PERIOD = 28f
    private const val SNAP0 = 0.05f       // the first snap, as a fraction of the cycle
    private const val SNAP_GAP = 0.12f    // and one every 3.4 s after it
    private const val SNAP_LEN = 0.048f   // a snap is 1.3 s: quick, then a settle
    private const val HALF = 0.50f        // where the product half begins

    // ---- the numbers this stop is about -------------------------------------------------
    private const val R1 = 1.2f           // |z_1| once it has been stretched
    private const val R2 = 0.8f           // |z_2|
    private const val ARG2 = 30f          // arg z_2, degrees
    private const val DEG = 0.017453292f

    // The product's modulus is computed as R1 * R2 everywhere it is DRAWN; this literal exists
    // only so the label costs no allocation. If R1 or R2 ever move, move this with them.
    private const val PROD_LABEL = "0.96"
    private const val ARG_LABEL = "120°"

    private val VALUE = arrayOf("1", "i", "−1", "−i")
    private val READ = arrayOf(
        "|z| 1.00   arg 0°", "|z| 1.00   arg 90°", "|z| 1.00   arg 180°", "|z| 1.00   arg 270°"
    )
    private const val READ_STRETCHED = "|z| 1.20   arg 90°"
    private const val READ_PRODUCT = "|z| 0.96   arg 120°"

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val p1 = FloatArray(3)
    private val p2 = FloatArray(3)

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        if (c >= HALF) {
            val b = (c - HALF) / (1f - HALF)
            if (b >= 0.90f) return READ[0]              // folded back home, ready to turn again
            return if (b >= 0.50f) READ_PRODUCT else READ_STRETCHED
        }
        // Which quarter the arrow is resting on: a snap changes the reading at its midpoint.
        var q = 0
        for (k in 0 until 4) if (c > SNAP0 + k * SNAP_GAP + SNAP_LEN * 0.5f) q++
        return READ[q and 3]
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val p = i.toFloat()
        SceneParts.stage(kit, p, 0f, 0f, f, g)
        val ox = g[0]; val oy = g[1]; val oz = g[2]
        val rx = g[3]; val ry = g[4]; val rz = g[5]      // the real axis, along the rail's side
        val ix = g[6]; val iy = g[7]; val iz = g[8]      // the imaginary axis, along its up

        // One world unit per unit of the complex plane. Anticlockwise is side turning toward up,
        // which is anticlockwise as the viewer sees it flying forward down the rail.
        val u = kit.radius(p) * 0.38f
        val c = SceneParts.cycle(kit.seconds, PERIOD)

        // ---- the state of the arrow, this instant ---------------------------------------
        // Four independent snaps summed, so the turn is always a whole number of quarters plus
        // whichever one is in flight. ease(t * 1.7) arrives at about 60% of the window and the
        // damped ring spends the rest of it settling: a quarter turn should look like a decision,
        // not a sweep.
        var turns = 0f
        var ring = 0f
        var idx = 0
        for (k in 0 until 4) {
            val t = ((c - (SNAP0 + k * SNAP_GAP)) / SNAP_LEN).coerceIn(0f, 1f)
            turns += SceneParts.ease(t * 1.7f)
            if (t > 0.6f && t < 1f) ring += sin((t - 0.6f) * 20f) * (1f - t) * 9f
            if (t > 0.5f) idx++
        }
        val frac = turns - floor(turns)
        // 1 at rest, 0 mid-snap: the value label at the head is not smeared across the turn.
        val dip = (1f - 4f * frac * (1f - frac)).coerceIn(0f, 1f)

        val b = ((c - HALF) / (1f - HALF)).coerceIn(0f, 1f)
        val gone = SceneParts.step(b, 0.90f, 0.10f)
        val live = 1f - gone                             // the product half retracts before it loops
        val swing = SceneParts.step(b, 0.02f, 0.10f) * live
        val grow2 = SceneParts.step(b, 0.16f, 0.10f) * live
        val lay = SceneParts.step(b, 0.30f, 0.16f)       // the second angle sliding onto the first
        val reach1 = SceneParts.step(b, 0.46f, 0.07f) * live
        val st = ((b - 0.46f) / 0.10f).coerceIn(0f, 1f)
        val kick = if (st > 0.7f && st < 1f) sin((st - 0.7f) * 24f) * (1f - st) * 0.10f else 0f

        // arg and modulus of the arrow. In the product half the four quarters are a whole turn,
        // so the argument is measured from zero again and the two halves join without a jump.
        val a1 = if (b > 0f) 90f * swing else turns * 90f + ring
        val m1 = 1f + (R1 - 1f) * swing
        val a2 = ARG2 * grow2
        val m2 = R2 * grow2
        val ap = a1 + a2                                 // the SUM, not a placed angle
        val mp = (R1 * R2) * (reach1 + kick)             // the PRODUCT, on the same ruler

        // ---- lines ----------------------------------------------------------------------
        val buf = kit.lineBuf
        var v = 0
        val segs = if (kit.quality == 0) 20 else 10

        // Both axes end at one unit, where their tick, their barb and their label all agree.
        v = MathMesh.axis(
            buf, v, ox, oy, oz, rx, ry, rz, ix, iy, iz, u, 1, u * 0.10f,
            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.5f
        )
        v = MathMesh.axis(
            buf, v, ox, oy, oz, ix, iy, iz, rx, ry, rz, u, 1, u * 0.10f,
            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.5f
        )

        // The quarter being swept, drawn only while it is in flight, so a turn leaves a trail and
        // a rest leaves nothing to read but the number.
        if (kit.quality < 2 && b <= 0f && frac > 0.02f && frac < 0.98f) {
            val q0 = floor(turns) * 90f
            v = MathMesh.arc(
                buf, v, ox, oy, oz, rx, ry, rz, ix, iy, iz, u * 0.30f,
                q0 * DEG, (q0 + frac * 90f) * DEG, if (kit.quality == 0) 8 else 4,
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.8f
            )
        }

        // The two ghost angles. The first stays where it was made; the second is born at the
        // origin spanning its own 30° and then slides up to sit on the end of the first.
        val s2 = a1 * lay
        if (b > 0f && a1 > 1f) {
            v = MathMesh.arc(
                buf, v, ox, oy, oz, rx, ry, rz, ix, iy, iz, u * 0.36f,
                0f, a1 * DEG, segs,
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.8f * live
            )
        }
        if (a2 > 0.5f) {
            v = MathMesh.arc(
                buf, v, ox, oy, oz, rx, ry, rz, ix, iy, iz, u * 0.36f,
                s2 * DEG, (s2 + a2) * DEG, if (kit.quality == 0) 7 else 4,
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.95f * live
            )
            // Hairlines on the second angle's two edges: what is being carried is the SPAN.
            SceneParts.at(g, cos(s2 * DEG) * u * 0.46f, sin(s2 * DEG) * u * 0.46f, 0f, p1)
            v = MathMesh.dashed(
                buf, v, ox, oy, oz, p1[0], p1[1], p1[2], 4,
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.5f * live
            )
            SceneParts.at(g, cos(ap * DEG) * u * 0.46f, sin(ap * DEG) * u * 0.46f, 0f, p1)
            v = MathMesh.dashed(
                buf, v, ox, oy, oz, p1[0], p1[1], p1[2], 4,
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.5f * live
            )
        }
        kit.flushLines(v, 2.5f)

        // ---- the angles as quantities ---------------------------------------------------
        // Filled sectors at full detail only. A hairline arc says "there is an angle here"; a
        // sector says how MUCH angle, which is what has to add up.
        if (kit.quality == 0 && live > 0.01f) {
            var tv = 0
            if (a1 > 1f) tv = sector(kit.triBuf, tv, 0f, a1 * DEG, u * 0.34f, 8, SceneParts.WORK, 0.30f * live)
            if (a2 > 0.5f) tv = sector(kit.triBuf, tv, s2 * DEG, (s2 + a2) * DEG, u * 0.34f, 4, SceneParts.ADDED, 0.42f * live)
            if (tv > 0) kit.flushTris(tv)
        }

        // ---- the arrows -----------------------------------------------------------------
        val br = u * 0.055f
        kit.ball(ox, oy, oz, br, br, br, SceneParts.HOT, SceneParts.WORK, 1f, 0f, 0f, 1f, 0f, 0f, 0.9f)

        val hr = u * 0.075f
        SceneParts.at(g, cos(a1 * DEG) * m1 * u, sin(a1 * DEG) * m1 * u, 0f, p1)
        kit.rod(
            ox, oy, oz,
            ox + (p1[0] - ox) * 0.85f, oy + (p1[1] - oy) * 0.85f, oz + (p1[2] - oz) * 0.85f,
            u * 0.032f, SceneParts.WORK, SceneParts.HOT, 0.35f
        )
        kit.ball(p1[0], p1[1], p1[2], hr, hr, hr, SceneParts.HOT, SceneParts.WORK, 1f, 0f, 0f, 1f, 0f, 0f, 0.85f + kit.beat * 0.4f)

        if (m2 > 0.03f) {
            SceneParts.at(g, cos(a2 * DEG) * m2 * u, sin(a2 * DEG) * m2 * u, 0f, p2)
            kit.rod(
                ox, oy, oz,
                ox + (p2[0] - ox) * 0.85f, oy + (p2[1] - oy) * 0.85f, oz + (p2[2] - oz) * 0.85f,
                u * 0.026f, SceneParts.ADDED, SceneParts.HOT, 0.3f
            )
            kit.ball(p2[0], p2[1], p2[2], hr * 0.8f, hr * 0.8f, hr * 0.8f, SceneParts.ADDED, SceneParts.HOT, live, 0f, 0f, 1f, 0f, 0f, 0.7f)
        }
        if (mp > 0.03f) {
            SceneParts.at(g, cos(ap * DEG) * mp * u, sin(ap * DEG) * mp * u, 0f, p2)
            kit.rod(
                ox, oy, oz,
                ox + (p2[0] - ox) * 0.85f, oy + (p2[1] - oy) * 0.85f, oz + (p2[2] - oz) * 0.85f,
                u * 0.038f, SceneParts.HOT, SceneParts.WORK, 0.6f
            )
            kit.ball(p2[0], p2[1], p2[2], hr * 1.15f, hr * 1.15f, hr * 1.15f, SceneParts.HOT, SceneParts.CHALK, live, 0f, 0f, 1f, 0f, 0f, 1.1f)
        }

        // ---- notation -------------------------------------------------------------------
        val glyph = u * 0.30f

        // The axes name their own tips, which are the numbers 1 and i. Set beside the tip rather
        // than beyond it: the head's own label sits further out along the same ray, and when the
        // arrow IS 1 the two must read as a mark and a value, not as one number printed twice.
        SceneParts.at(g, u * 1.02f, -u * 0.32f, 0f, p2)
        kit.text("1", p2[0], p2[1], p2[2], glyph, SceneParts.COOL, 0.85f)
        SceneParts.at(g, -u * 0.34f, u * 1.02f, 0f, p2)
        kit.text("i", p2[0], p2[1], p2[2], glyph, SceneParts.COOL, 0.85f)

        // What the arrow currently IS, hung just beyond its head along its own direction.
        val lr = (m1 + 0.30f) * u
        SceneParts.at(g, cos(a1 * DEG) * lr, sin(a1 * DEG) * lr, 0f, p2)
        val vAlpha = dip * (1f - swing)
        if (vAlpha > 0.02f) {
            kit.text(VALUE[idx and 3], p2[0], p2[1], p2[2], glyph * 1.15f, SceneParts.HOT, vAlpha)
        }
        if (swing > 0.02f) {
            kit.text("1.2", p2[0], p2[1], p2[2], glyph * 1.15f, SceneParts.WORK, swing)
        }
        // The multiplication itself, named only while it is happening and only at full detail.
        if (kit.quality == 0 && b <= 0f && dip < 0.9f) {
            val ma = (floor(turns) + frac * 0.5f) * 90f
            SceneParts.at(g, cos(ma * DEG) * u * 0.46f, sin(ma * DEG) * u * 0.46f, 0f, p2)
            kit.text("×i", p2[0], p2[1], p2[2], glyph * 0.9f, SceneParts.WORK, (1f - dip) * 0.9f)
        }

        if (grow2 > 0.02f) {
            val l2 = (m2 + 0.28f) * u
            SceneParts.at(g, cos(a2 * DEG) * l2, sin(a2 * DEG) * l2, 0f, p2)
            kit.text("0.8", p2[0], p2[1], p2[2], glyph, SceneParts.ADDED, grow2)
        }
        if (reach1 > 0.02f) {
            val lp = (R1 * R2 + 0.30f) * u
            SceneParts.at(g, cos(ap * DEG) * lp, sin(ap * DEG) * lp, 0f, p2)
            kit.text(PROD_LABEL, p2[0], p2[1], p2[2], glyph * 1.25f, SceneParts.HOT, reach1)
            SceneParts.at(g, cos(ap * 0.5f * DEG) * u * 0.62f, sin(ap * 0.5f * DEG) * u * 0.62f, 0f, p2)
            kit.text(ARG_LABEL, p2[0], p2[1], p2[2], glyph * 1.1f, SceneParts.HOT, reach1)
        }
        // The two summands, so 90 + 30 = 120 is on the glass and not only in the geometry.
        if (kit.quality == 0 && grow2 > 0.02f) {
            SceneParts.at(g, cos(a1 * 0.5f * DEG) * u * 0.24f, sin(a1 * 0.5f * DEG) * u * 0.24f, 0f, p2)
            kit.text("90°", p2[0], p2[1], p2[2], glyph * 0.8f, SceneParts.WORK, 0.8f * live, GlyphBoard.Style.SMALL)
            val mm = (s2 + a2 * 0.5f)
            SceneParts.at(g, cos(mm * DEG) * u * 0.50f, sin(mm * DEG) * u * 0.50f, 0f, p2)
            kit.text("30°", p2[0], p2[1], p2[2], glyph * 0.8f, SceneParts.ADDED, 0.8f * grow2, GlyphBoard.Style.SMALL)
        }
    }

    /**
     * A translucent sector from the origin, dim at the centre and bright at the rim.
     *
     * The angles have to be seen ADDING, and two hairline arcs abutting is a weaker picture than
     * two quantities of area laid against each other — you can see one of these is three times
     * the other without counting degrees. Cheap enough to be worth it: eight triangles.
     */
    private fun sector(
        tri: FloatArray, at: Int, from: Float, to: Float, radius: Float, n: Int,
        c: FloatArray, alpha: Float
    ): Int {
        var k = at
        var j = 0
        while (j < n) {
            val t0 = from + (to - from) * j / n
            val t1 = from + (to - from) * (j + 1) / n
            SceneParts.at(g, cos(t0) * radius, sin(t0) * radius, 0f, p1)
            SceneParts.at(g, cos(t1) * radius, sin(t1) * radius, 0f, p2)
            k = MathMesh.vertex(tri, k, g[0], g[1], g[2], c[0], c[1], c[2], alpha * 0.15f)
            k = MathMesh.vertex(tri, k, p1[0], p1[1], p1[2], c[0], c[1], c[2], alpha)
            k = MathMesh.vertex(tri, k, p2[0], p2[1], p2[2], c[0], c[1], c[2], alpha)
            j++
        }
        return k
    }
}
