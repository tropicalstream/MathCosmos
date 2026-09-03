package com.rayneo.mathcosmos

import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR V, stop 1 — THE LANDSCAPE. "One number for every place on the ground is a landscape, and I
 * can fly it."
 *
 * The tube goes ghost here and the country opens out, and the crew's claim is that a function of
 * two variables IS that country: pick a place, get a number, and the numbers stacked up over the
 * plane are the ground you are flying. The ambient draws the finished landscape — mesh and contour
 * rings — from the first frame. What this stop adds is the ARGUMENT: the craft drops a bead over
 * each of a lattice of places, the bead falls to the flat datum plane and lands ON A PLACE, and
 * then it moves to its HEIGHT. Watch the field of beads finish and their tops are sitting exactly
 * on the ambient's wireframe, because both are asking the same terrainHeight the same question.
 * That agreement is the whole stop, and it is the one thing here that would be worth nothing if it
 * were faked: the beads are not placed on the drawn mesh, they are sampled independently and they
 * land on it.
 *
 * THE TWO-STAGE MOVE IS THE POINT, and it is why a bead does not simply fall to the terrain. Place
 * first, height second: a bead resting on the flat plane is a point of the DOMAIN and nothing more,
 * and the little cross it leaves behind stays there for the rest of the cycle to say so. Only then
 * does the number arrive, as a vertical move away from the plane with a dashed stem left behind
 * measuring it. Domain below, value above, a stem joining them — that is a graph, drawn in the air
 * at the scale of a landscape rather than on a page.
 *
 * Half the country is BELOW the datum, so half the beads sink rather than rise. That is deliberate
 * and it is not a compromise: a bead that only ever rose would be quietly lying about the sign of
 * the function, and stop 7 needs a viewer who already believes the ground can go down.
 *
 * SIZE. Every corridor scene in this app has to stay inside 0.8 of the passage radius or it is
 * buried in the wall. Tour V is the exception the rule was written to allow — wallAlpha is 0.18
 * and the scenery is the open country — so the sampling patch is eight world units square in a
 * passage four wide, and the two beacons that name the high and low ground stand out at six and
 * nine and a half units. Trimming any of that to the tube would give back the corridor the tour
 * has just spent four seconds fading away.
 *
 * BUDGET. Thirty-six beads are thirty-six lit spheres and thirty-six draw calls, which is the
 * whole stop's allowance spent on dots; they are camera-facing diamonds in the triangle buffer
 * instead, one call for all of them, and the same trick the escaping terms of III-9 use. Three
 * lit balls are kept for the things that must read as objects rather than marks: the live sample
 * under the keel, the summit, and the bowl.
 */
object SceneLandscape : MathScene {

    /** The country opens as the walls go; the beads should already be falling when it does. */
    override val reach = 1.6f

    /** The patch runs four world units either side of the stop, about a quarter of a node. */
    override val deep = 0.4f

    // ---- the sampling patch --------------------------------------------------------------------
    private const val CELL = 1.6f          // world units between sample sites
    private const val N0 = 6               // sites per side at quality 0: thirty-six, countable
    private const val NQ = 4               // and sixteen when the governor steps in
    private const val BEAD = 0.085f        // half-height of a bead's diamond
    private const val MARK = 0.17f         // half-length of the cross a landed bead leaves

    // ---- the beacons ---------------------------------------------------------------------------
    private const val R_IN = 5.5f          // the two rings the high and low ground are hunted on
    private const val R_OUT = 8.5f         // both well inside the ambient patch, so a beacon has ground under it

    // ---- the clock -------------------------------------------------------------------------------
    // Fall, sit on the plane, rise, and then eight seconds of standing still. The rest matters more
    // here than in most stops: the finished field of beads over its own flat domain IS the picture
    // the crew is talking about, and a viewer arriving mid-pass has to be able to just look at it.
    private const val PERIOD = 24f
    private const val FALL_AT = 0.04f
    private const val FALL_LEN = 0.12f
    private const val RISE_AT = 0.18f
    private const val RISE_LEN = 0.14f
    private const val SPREAD = 0.34f       // how far apart in the cycle the first and last site go
    private const val CLEAR_AT = 0.94f     // the standing field dims away before the next survey

    private const val LABEL = "z = f(x, y)"

    private val fr = FloatArray(12)
    private val g = FloatArray(12)
    private val hero = FloatArray(3)
    private val tv = IntArray(1)

    /** The raw 0..1 ramp. [SceneParts.step] eases both ends; a falling bead must not ease in. */
    private fun ramp(c: Float, at: Float, len: Float): Float = ((c - at) / len).coerceIn(0f, 1f)

    /**
     * A glyph height that holds its apparent size out to the beacons.
     *
     * Everything else in the app labels a figure at arm's length, where 0.16-0.26 world units is
     * right. The summit is nine units away and a 0.2 label on it is a smudge, so the height is
     * scaled with the distance to the eye and clamped, which keeps SUMMIT reading the same size as
     * the notation hanging beside the patch. It is the one place a world-space height is allowed
     * to be a screen-space decision.
     */
    private fun glyph(kit: SceneKit, x: Float, y: Float, z: Float, want: Float): Float {
        val dx = x - kit.camX; val dy = y - kit.camY; val dz = z - kit.camZ
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        return (want * d / 3.2f).coerceIn(want, want * 3.4f)
    }

    /**
     * The height of the ground directly under the craft, and how much of the patch has been read.
     *
     * The number a viewer is meant to take away from this stop is "the country has a value HERE",
     * and a value is a thing to be read, not looked at — so it lives on the HUD in 2D rather than
     * as notation hung on a bead that is four units below the eye and moving.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTerrain) return null
        val ns = if (kit.quality == 0) N0 else NQ
        val total = ns * ns
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // A site counts as read once its rise is half done. Sites go in raster order, so the count
        // is exactly the number whose key has come up — no estimate, and no second loop.
        val k = (c - RISE_AT - RISE_LEN * 0.5f) / SPREAD
        val done = if (k < 0f) 0 else (k.coerceAtMost(1f) * (total - 1)).toInt() + 1
        val z = kit.terrainHeight(kit.shipX, kit.shipZ)
        val cents = round(if (z < 0f) -z * 100f else z * 100f).toInt()
        val frac = cents % 100
        val txt = (if (cents == 0) "" else if (z < 0f) "-" else "+") +
            (cents / 100) + "." + (if (frac < 10) "0" else "") + frac
        return "z HERE $txt   SAMPLED $done / $total"
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // A tour without a terrain callback has no country to sample, and this scene is nothing
        // but a sampling of it.
        if (!kit.hasTerrain) return

        val q = kit.quality
        val ns = if (q == 0) N0 else NQ
        val total = ns * ns
        val half = (ns - 1) * 0.5f
        val gy = SceneAmbientCountry.GROUND_Y

        // --- the frame ---------------------------------------------------------------------------
        // The stop's world centre comes from the rail, but the patch's own axes are world x and z,
        // not the rail's side and forward. That is the same decision the ambient made and for the
        // same reason: terrainHeight is a function of world (x, z), so a domain grid that turned
        // with the rail would not be the (x, y) plane the crew is naming — it would be a rug laid
        // at an angle across it. The rail is still used for everything that must sit BESIDE the
        // figure, and only its horizontal part, so the notation stays level however the rail climbs.
        SceneParts.stage(kit, i.toFloat(), 0f, 0f, fr, g)
        val cx = g[0]; val cz = g[2]
        var sx = g[3]; var sz = g[5]
        val sl = sqrt(sx * sx + sz * sz)
        if (sl > 1e-4f) { sx /= sl; sz /= sl } else { sx = 1f; sz = 0f }

        // The lattice is snapped to whole cells so a sample site is a PLACE and stays there. The
        // sixteen sites of the stepped-down grid are sixteen of the original thirty-six, in the
        // same places: a thermal step must not move the survey marks under someone watching them.
        val baseX = round(cx / CELL) * CELL
        val baseZ = round(cz / CELL) * CELL
        val x0 = baseX - half * CELL; val x1 = baseX + half * CELL
        val z0 = baseZ - half * CELL; val z1 = baseZ + half * CELL

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val clear = 1f - SceneParts.step(c, CLEAR_AT, 1f - CLEAR_AT)
        val rel = ramp(c, FALL_AT, SPREAD)
        val scanning = c > FALL_AT && c < FALL_AT + SPREAD + FALL_LEN
        val activeRow = (rel * (total - 1)).toInt() / ns

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // --- the high ground and the low --------------------------------------------------------
        // Hunted, not hard-coded. The design calls for a summit off to port and a bowl to
        // starboard; the terrain function is what it is and the rail wanders, so the honest thing
        // is to ask the country where its extremes are on two rings about the stop and mark those.
        // Twenty-four samples of a trig sum costs nothing and it cannot be wrong about which way
        // to look.
        val ndir = if (q == 0) 12 else 8
        var hiH = -1e9f; var hiX = cx; var hiZ = cz
        var loH = 1e9f; var loX = cx; var loZ = cz
        for (m in 0 until ndir) {
            val a = m * (6.2831853f / ndir)
            val ca = cos(a); val sa = sin(a)
            for (ring in 0 until 2) {
                val rr = if (ring == 0) R_IN else R_OUT
                val bx = cx + ca * rr; val bz = cz + sa * rr
                val bh = kit.terrainHeight(bx, bz)
                if (bh > hiH) { hiH = bh; hiX = bx; hiZ = bz }
                if (bh < loH) { loH = bh; loX = bx; loZ = bz }
            }
        }
        // The stop's notation goes on whichever flank the summit is NOT on, so the tour's one
        // persistent label never sits in front of the mountain it is inviting you to look at.
        val lside = if ((hiX - cx) * sx + (hiZ - cz) * sz > 0f) -1f else 1f

        // --- the datum plane -----------------------------------------------------------------------
        // The domain, drawn flat at the height terrainHeight reads zero. It is faint on purpose:
        // it has to be legible as a floor under the beads without competing with the ambient's
        // contour rings, which are also horizontal lines and are the country's own marks. It does
        // not dim with the survey either — the set of places is there whether or not anyone has
        // measured them, and blinking it would say otherwise.
        val ch = SceneParts.CHALK
        for (jj in 0 until ns) {
            val z = baseZ + (jj - half) * CELL
            val rim = jj == 0 || jj == ns - 1
            if (!rim && q > 0) continue
            var a = if (rim) 0.42f else 0.16f
            // The row being surveyed right now lights up, so the raster order is watchable rather
            // than merely present.
            if (scanning && jj == activeRow) a = 0.80f
            v = MathMesh.segment(line, v, x0, gy, z, x1, gy, z, ch[0], ch[1], ch[2], a)
        }
        for (ii in 0 until ns) {
            val x = baseX + (ii - half) * CELL
            val rim = ii == 0 || ii == ns - 1
            if (!rim && q > 0) continue
            val a = if (rim) 0.42f else 0.16f
            v = MathMesh.segment(line, v, x, gy, z0, x, gy, z1, ch[0], ch[1], ch[2], a)
        }

        // --- the beads -----------------------------------------------------------------------------
        val cool = SceneParts.COOL
        val work = SceneParts.WORK
        var heroD2 = 1e9f
        var heroRise = 0f
        for (jj in 0 until ns) {
            val z = baseZ + (jj - half) * CELL
            for (ii in 0 until ns) {
                val x = baseX + (ii - half) * CELL
                // Raster order rather than a diagonal wave: a survey reads rows, and it lets the
                // HUD's count be exact instead of an estimate.
                val key = (jj * ns + ii).toFloat() / (total - 1)
                val fall = ramp(c, FALL_AT + key * SPREAD, FALL_LEN)
                if (fall <= 0f) continue
                val h = kit.terrainHeight(x, z)
                val rise = SceneParts.ease(ramp(c, RISE_AT + key * SPREAD, RISE_LEN))

                var px: Float; var py: Float; var pz: Float
                if (rise <= 0f) {
                    // Dropped from the craft, so the release point drifts with the craft as it
                    // flies — they are being dropped, not spawned. Horizontal travel is linear and
                    // the vertical is squared, which is a real projectile rather than an eased
                    // slide, and reads as weight in stereo.
                    px = kit.shipX + (x - kit.shipX) * fall
                    pz = kit.shipZ + (z - kit.shipZ) * fall
                    py = kit.shipY + (gy - kit.shipY) * fall * fall
                } else {
                    px = x; pz = z; py = gy + h * rise
                }

                // The cross the bead leaves on the plane once it has landed: the PLACE, which
                // stays put while the number departs upward.
                if (fall >= 0.999f && q < 2) {
                    v = MathMesh.segment(line, v, x - MARK, gy, z, x + MARK, gy, z,
                        ch[0], ch[1], ch[2], 0.55f * clear)
                    v = MathMesh.segment(line, v, x, gy, z - MARK, x, gy, z + MARK,
                        ch[0], ch[1], ch[2], 0.55f * clear)
                }

                // The stem: dashed, because it is a measurement and not a thing. Bright while the
                // height is being taken, then down to a third so the standing field is a surface
                // of beads rather than a thicket.
                if (rise > 0.01f && q < 2) {
                    v = MathMesh.dashed(line, v, x, gy, z, x, gy + h * rise, z, 3,
                        work[0], work[1], work[2], (0.26f + 0.55f * (1f - rise)) * clear)
                }

                // A place is cool, a height is warm; the bead changes colour as it acquires one.
                val t = rise
                val br = cool[0] + (work[0] - cool[0]) * t
                val bg = cool[1] + (work[1] - cool[1]) * t
                val bb = cool[2] + (work[2] - cool[2]) * t
                val bsz = BEAD * (1f + 0.45f * (1f - t) * fall)
                tv[0] = MathMesh.quad(
                    tri, tv[0],
                    px - kit.camRightX * bsz, py - kit.camRightY * bsz, pz - kit.camRightZ * bsz,
                    (kit.camRightX + kit.camUpX) * bsz, (kit.camRightY + kit.camUpY) * bsz,
                    (kit.camRightZ + kit.camUpZ) * bsz,
                    (kit.camRightX - kit.camUpX) * bsz, (kit.camRightY - kit.camUpY) * bsz,
                    (kit.camRightZ - kit.camUpZ) * bsz,
                    br, bg, bb, clear
                )

                val dx = x - kit.shipX; val dz = z - kit.shipZ
                val d2 = dx * dx + dz * dz
                if (d2 < heroD2) {
                    heroD2 = d2; heroRise = rise
                    hero[0] = px; hero[1] = py; hero[2] = pz
                }
            }
        }

        // --- the two beacons' stems ----------------------------------------------------------------
        if (q < 2) {
            v = MathMesh.dashed(line, v, hiX, gy, hiZ, hiX, gy + hiH, hiZ, 4,
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.35f)
            v = MathMesh.dashed(line, v, loX, gy, loZ, loX, gy + loH, loZ, 4,
                cool[0], cool[1], cool[2], 0.35f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the live sample under the keel ----------------------------------------------------------
        // One lit ball, on the nearest site to the craft, pulsing with the cue: this is the number
        // the HUD is reading out, and a viewer should be able to see which bead it belongs to.
        if (heroD2 < 1e8f && clear > 0.05f) {
            val r = 0.13f + 0.03f * heroRise
            kit.ball(
                hero[0], hero[1], hero[2], r, r, r, SceneParts.HOT, work, clear,
                0f, 0f, 1f, 0f, 0f, 1.4f + 2.4f * kit.beat
            )
        }

        // --- the summit and the bowl ------------------------------------------------------------------
        if (q < 2) {
            kit.ball(hiX, gy + hiH + 0.12f, hiZ, 0.20f, 0.20f, 0.20f,
                SceneParts.HOT, work, 0.95f, 0f, 0f, 1f, 0f, 0f, 1.2f)
            kit.ball(loX, gy + loH + 0.10f, loZ, 0.16f, 0.16f, 0.16f,
                cool, SceneParts.ADDED, 0.85f, 0f, 0f, 1f, 0f, 0f, 0.7f)
        }

        // --- notation -----------------------------------------------------------------------------
        // The one claim of the stop, hung clear of the patch on the flank away from the summit. It
        // is pushed out by half its own width rather than anchored left or right, because which
        // screen edge "outward" means depends on where the head is pointing and the label must not
        // creep back over the beads when the viewer turns.
        val ly = gy + 2.4f
        var lx = cx + sx * lside * (half * CELL + 1.1f)
        var lz = cz + sz * lside * (half * CELL + 1.1f)
        val lh = glyph(kit, lx, ly, lz, 0.24f)
        val lw = kit.textWidth(LABEL, lh) * 0.5f
        lx += sx * lside * lw; lz += sz * lside * lw
        kit.text(LABEL, lx, ly, lz, lh, SceneParts.HOT, 1f)

        if (q > 0) return

        // Naming the two halves of the picture: the plane is where the places are, the stem is
        // where the number is. Both sit beside what they name, never over it. "(x, y)" goes on the
        // patch's other flank, down on the datum where the places are, so the stop's two labels are
        // never stacked in one column of the eye.
        val gx = cx - sx * lside * (half * CELL + 0.5f)
        val gz = cz - sz * lside * (half * CELL + 0.5f)
        val ph = glyph(kit, gx, gy, gz, 0.18f)
        val pw = kit.textWidth("(x, y)", ph) * 0.5f
        kit.text("(x, y)", gx - sx * lside * pw, gy + 0.12f, gz - sz * lside * pw, ph, ch, 0.85f)

        if (heroD2 < 1e8f && heroRise > 0.25f) {
            val zy = (gy + hero[1]) * 0.5f
            val zx = hero[0] + sx * 0.30f
            val zz = hero[2] + sz * 0.30f
            kit.text("z", zx, zy, zz, glyph(kit, zx, zy, zz, 0.19f), work, 0.95f * clear)
        }

        // The beacons say what they are in words, because "look left, there is a mountain" is the
        // sentence this stop opens the tour with and the mountain should agree in writing.
        val sh = glyph(kit, hiX, gy + hiH, hiZ, 0.17f)
        val sn = sqrt((hiX - cx) * (hiX - cx) + (hiZ - cz) * (hiZ - cz)).coerceAtLeast(1e-3f)
        val so = kit.textWidth("SUMMIT", sh) * 0.5f + 0.3f
        kit.text("SUMMIT", hiX + (hiX - cx) / sn * so, gy + hiH + 0.26f, hiZ + (hiZ - cz) / sn * so,
            sh, SceneParts.HOT, 0.9f, GlyphBoard.Style.PLAIN)

        val bh2 = glyph(kit, loX, gy + loH, loZ, 0.17f)
        val bn = sqrt((loX - cx) * (loX - cx) + (loZ - cz) * (loZ - cz)).coerceAtLeast(1e-3f)
        val bo = kit.textWidth("BOWL", bh2) * 0.5f + 0.3f
        kit.text("BOWL", loX + (loX - cx) / bn * bo, gy + loH + 0.24f, loZ + (loZ - cz) / bn * bo,
            bh2, cool, 0.85f, GlyphBoard.Style.PLAIN)
    }
}
