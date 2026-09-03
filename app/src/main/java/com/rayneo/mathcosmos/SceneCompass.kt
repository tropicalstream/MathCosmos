package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR V, stop 4 — THE COMPASS. "The gradient is an arrow lying flat on the ground that points
 * straight uphill, and its length is how steep."
 *
 * The flagship of the tour's first half, and the one stop where the picture has to be more careful
 * than it looks. The gradient is NOT a vector on the hillside. It is a vector in the flat country
 * underneath the hillside — two numbers, one per direction you can walk — and everything that
 * makes it useful later (perpendicular to the contours at stop 5, the shadow that gives every
 * directional derivative at stop 6, the alignment with the fence at stop 8) depends on a viewer
 * having understood that it lies FLAT. So the instrument here is a horizontal card with a needle
 * on it, and the needle's tip has a dashed plumb line running up to the actual ground, which is
 * somewhere else entirely. The crew says this distinction out loud; the geometry says it too.
 *
 * The card TRAVELS with the craft but does not TURN with it. Its position rides along, because the
 * whole point is that the reading is live; its spokes are welded to the ground's own x and z axes,
 * because a compass rose that swung round with the heading would make the needle look still while
 * the world spun, and that is the exact opposite of the reading being taken. The needle turns
 * because the TERRAIN under the ship changes, and the card has to stay put for that to be seen.
 *
 * It is the sibling app's SENTINEL geometry — a direction attached to the ship and continuously
 * recomputed — which is also why this scene overrides [deep]: the renderer culls a landmark by the
 * distance from its NODE to the camera, and a landmark that travels with the camera would be
 * switched off halfway through its own pass. Declaring it deep pushes the cull test far enough
 * ahead that the fade window is what actually ends the scene.
 *
 * The gradient is measured, not written down. Central differences on [SceneKit.terrainHeight] at
 * the ground under the KEEL, every frame, over whatever terrain callback the tour is carrying — so
 * if the landscape is ever retuned the needle follows it without this file being touched. Three
 * honest approximations are worth naming, because the crew names them too.
 *
 * The reading is taken beneath the ship and the dial is MOUNTED a couple of units forward and to
 * port. That is what an instrument is: the sensor is where the measurement means something, the
 * face is where it can be read. A card hung squarely under the keel is a card nobody sees on a
 * pass, and this stop is the flagship.
 *
 * The needle's LENGTH is |∇f| times a constant of about five, because this terrain's real slopes
 * run to about 0.4 and an arrow four tenths of a unit long would read as a dot on a waveguide. The
 * ratio between a steep place and a flat one is exactly right; the absolute scale is not, and the
 * number that can be trusted is in the readout.
 *
 * And the card sits at the ground height directly beneath it rather than on the h = 0 datum plane,
 * which is the true domain — on the datum it would spend most of the tour buried a metre inside a
 * hill. It is the FLATNESS that carries the meaning, not the altitude, so the card is slid up to
 * touch the ground at one point and left horizontal.
 *
 * Beside it, the demonstration. A heavy bead is released at a fixed place on the ground near the
 * node and rolls off along the exact reverse of the gradient, leaving a glowing track, then the
 * cycle resets and it does it again. The bead's release point is world-fixed while the card rides
 * the ship, and that is deliberate: a track that followed the craft would be a smear, not a
 * record. The two are alongside each other at the moment of closest approach, which is when the
 * comparison is meant to be made. Its path is a steepest-descent walk recomputed from scratch each
 * frame — thirty fixed-length steps down the negative gradient — so the scene stays a pure
 * function of the clock and holds no state between frames. Where the ground goes flat the walk
 * stalls and the bead stops, which is not a failure of the integration but the thing itself: a
 * heavy ball comes to rest at the bottom.
 */
object SceneCompass : MathScene {

    /** Wide, because the needle needs travel to be seen swinging. */
    override val reach = 1.6f

    /** Not depth of geometry but depth of ATTACHMENT: the card is wherever the craft is. */
    override val deep = 1.7f

    private const val PERIOD = 22f
    private const val R = 1.30f            // radius of the compass card
    private const val GAIN = 5.0f          // world units of needle per unit of |∇f| — see the header
    private const val EPS = 0.30f          // central-difference step; the terrain's features are ~20 units
    /**
     * How far forward and to port the dial is hung, and the one number here that was argued over.
     *
     * The ground sits about two and a half units under the keel, so the depression angle of the
     * card is set entirely by how far ahead it is: directly underfoot it is forty degrees down —
     * off the bottom of the glasses unless you drop your chin — and it has to go out to five or so
     * before it comes up into the lower part of a forward look. Five it is, with the card and the
     * needle grown to hold their apparent size at that range. The cost is that the belly probe is
     * a boom rather than an arm, which is what it is called below.
     */
    private const val AHEAD = 4.6f
    private const val SIDE = -2.3f
    private const val LIFT = 0.05f         // clear of the ambient mesh, so nothing z-fights
    private const val STEPS = 30
    private const val STEP_LEN = 0.45f
    private const val BEAD_SIDE = -3.6f    // released further out on the same side as the card
    private const val TAU = 6.2831853f

    private val fr = FloatArray(12)
    private val pc = FloatArray(3)         // card centre, world — draw() only
    private val pg = FloatArray(2)         // the keel reading: (∂f/∂x, ∂f/∂z) — draw() only
    private val bg = FloatArray(2)         // gradient at whatever the descent walk is looking at
    private val hg = FloatArray(2)         // the HUD's own copy of the reading — see readout()
    private val o = FloatArray(3)
    private val track = FloatArray(STEPS * 3)

    // ------------------------------------------------------------------ measuring

    /**
     * Central differences on the terrain. Two extra height samples each way buys a derivative
     * worth trusting, and the step is a tenth of the smallest feature this country has, so the
     * difference is measuring the slope rather than the sampling.
     *
     * Nothing here touches the renderer: [SceneKit.terrainHeight] is a plain callback on the
     * tour's own terrain function, which is why this one call is safe to make from the HUD.
     */
    private fun grad(kit: SceneKit, x: Float, z: Float, out: FloatArray) {
        out[0] = (kit.terrainHeight(x + EPS, z) - kit.terrainHeight(x - EPS, z)) / (2f * EPS)
        out[1] = (kit.terrainHeight(x, z + EPS) - kit.terrainHeight(x, z - EPS)) / (2f * EPS)
    }

    /**
     * The numbers behind the needle, and the reason they are worth a HUD line: a direction alone
     * cannot show that the arrow is SHORT in the bowl and long on the ridge shoulder, and the two
     * partials are what stops 2 and 6 spend their whole length talking about.
     *
     * This runs on the UI thread, not the GL thread — the telemetry block is built when Android
     * feels like building it. So it reads the ship's position and the terrain callback and nothing
     * else: no [SceneKit.frame], no [SceneKit.pointAt], and its own scratch array. Both of those
     * kit calls go through renderer-owned temporaries, and racing the draw thread for them to save
     * two floats would be a poor trade.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTerrain) return null
        grad(kit, kit.shipX, kit.shipZ, hg)
        val m = sqrt(hg[0] * hg[0] + hg[1] * hg[1])
        return "|∇f| %.2f   ∂x %+.2f   ∂z %+.2f".format(Locale.US, m, hg[0], hg[1])
    }

    /** Where to hang the dial: forward of the keel and out to port, flat on the ground it finds. */
    private fun place(kit: SceneKit) {
        kit.frame(kit.progress, fr)
        // Only the horizontal part of the heading: the card lies flat however much the rail climbs.
        var hx = fr[3]; var hz = fr[5]
        val hl = sqrt(hx * hx + hz * hz)
        if (hl > 1e-4f) { hx /= hl; hz /= hl } else { hx = 0f; hz = -1f }
        val x = kit.shipX + hx * AHEAD - hz * SIDE
        val z = kit.shipZ + hz * AHEAD + hx * SIDE
        pc[0] = x
        pc[1] = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(x, z)
        pc[2] = z
    }

    // ------------------------------------------------------------------ drawing

    /** A horizontal translucent disc as a triangle fan, faded toward its rim. */
    private fun card(
        tri: FloatArray, at: Int, cx: Float, cy: Float, cz: Float,
        radius: Float, n: Int, c: FloatArray, alpha: Float
    ): Int {
        if ((at + n * 3) * MathMesh.STRIDE > tri.size) return at
        var k = at
        var px = cx + radius; var pz = cz
        for (s in 1..n) {
            val a = s * TAU / n
            val x = cx + cos(a) * radius
            val z = cz + sin(a) * radius
            k = MathMesh.vertex(tri, k, cx, cy, cz, c[0], c[1], c[2], alpha)
            k = MathMesh.vertex(tri, k, px, cy, pz, c[0], c[1], c[2], alpha * 0.15f)
            k = MathMesh.vertex(tri, k, x, cy, z, c[0], c[1], c[2], alpha * 0.15f)
            px = x; pz = z
        }
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No terrain callback, no ground to point up: this stop is meaningless off Tour V.
        if (!kit.hasTerrain) return
        place(kit)
        // The reading is taken under the keel, where it means something, and shown on the dial
        // hung out to port, where it can be seen. Same number the HUD is printing.
        grad(kit, kit.shipX, kit.shipZ, pg)

        val q = kit.quality
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val ground = SceneAmbientCountry.GROUND_Y

        // --- the reading ---------------------------------------------------------------------
        val m = sqrt(pg[0] * pg[0] + pg[1] * pg[1])
        var dx = 1f; var dz = 0f
        if (m > 1e-5f) { dx = pg[0] / m; dz = pg[1] / m }
        // A stub rather than nothing where the ground is flat: a zero-length arrow is invisible,
        // and "there is a gradient here and it is nearly zero" is a different statement from
        // "there is no instrument here".
        val len = (m * GAIN).coerceIn(0.18f, R * 1.8f)
        val px = -dz; val pz = dx                 // in the ground plane, square to the needle
        val cy = pc[1] + LIFT

        // --- the card ------------------------------------------------------------------------
        // Horizontal, and it will cut into the hillside on its uphill side. That is not an
        // artefact to be hidden; it is the picture. The ground plane and the ground are two
        // different surfaces and here they are, crossing.
        if (q < 2) {
            tv = card(tri, tv, pc[0], cy - 0.006f, pc[2], R, if (q == 0) 24 else 14,
                SceneParts.COOL, 0.15f)
        }
        val st = SceneParts.STEEL
        v = MathMesh.arc(
            line, v, pc[0], cy, pc[2], 1f, 0f, 0f, 0f, 0f, 1f,
            R, 0f, TAU, if (q == 0) 32 else 18, st[0], st[1], st[2], 0.75f
        )
        // Spokes on the ground's own axes, not the craft's — the card is a fixed reference and
        // the needle is what moves against it.
        val spokes = if (q == 0) 8 else if (q == 1) 4 else 0
        for (s in 0 until spokes) {
            val a = s * TAU / spokes
            val ux = cos(a); val uz = sin(a)
            v = MathMesh.segment(
                line, v,
                pc[0] + ux * R * 0.82f, cy, pc[2] + uz * R * 0.82f,
                pc[0] + ux * R, cy, pc[2] + uz * R,
                st[0], st[1], st[2], 0.55f
            )
        }

        // --- the needle ------------------------------------------------------------------------
        val tipX = pc[0] + dx * len
        val tipZ = pc[2] + dz * len
        val hh = len * 0.26f
        val hw = hh * 0.44f
        val hot = SceneParts.HOT
        // A filled head as well as the two barbs: at 640 by 480 a pair of hairlines is a smudge,
        // and this is the object the whole stop is about.
        if ((tv + 3) * MathMesh.STRIDE <= tri.size) {
            tv = MathMesh.vertex(tri, tv, tipX, cy, tipZ, hot[0], hot[1], hot[2], 1f)
            tv = MathMesh.vertex(tri, tv, tipX - dx * hh + px * hw, cy, tipZ - dz * hh + pz * hw,
                hot[0], hot[1], hot[2], 0.85f)
            tv = MathMesh.vertex(tri, tv, tipX - dx * hh - px * hw, cy, tipZ - dz * hh - pz * hw,
                hot[0], hot[1], hot[2], 0.85f)
        }
        v = MathMesh.segment(line, v, tipX, cy, tipZ,
            tipX - dx * hh + px * hw, cy, tipZ - dz * hh + pz * hw, hot[0], hot[1], hot[2], 1f)
        v = MathMesh.segment(line, v, tipX, cy, tipZ,
            tipX - dx * hh - px * hw, cy, tipZ - dz * hh - pz * hw, hot[0], hot[1], hot[2], 1f)

        // --- the plumb line, which is the whole argument -----------------------------------------
        // Straight up from the needle's tip to the ground the tip is pointing at. At the card's
        // centre it has zero length, and it lengthens along the needle: the arrow is flat, the
        // country is not, and the gap between them is drawn rather than asserted.
        if (q == 0) {
            val sy = ground + kit.terrainHeight(tipX, tipZ)
            val ch = SceneParts.CHALK
            v = MathMesh.dashed(line, v, tipX, cy, tipZ, tipX, sy, tipZ, 4, ch[0], ch[1], ch[2], 0.45f)
            v = MathMesh.segment(line, v, tipX - 0.13f, sy, tipZ, tipX + 0.13f, sy, tipZ,
                ch[0], ch[1], ch[2], 0.7f)
            v = MathMesh.segment(line, v, tipX, sy, tipZ - 0.13f, tipX, sy, tipZ + 0.13f,
                ch[0], ch[1], ch[2], 0.7f)
        }

        // --- the heavy bead, and where it goes ---------------------------------------------------
        // The release point is a world place, taken from the node's own frame, so it does not
        // travel and the track it leaves stays where it was made.
        kit.pointAt(i.toFloat(), BEAD_SIDE, 0f, 0f, o)
        val rx = o[0]; val rz = o[2]
        val steps = when (q) { 0 -> STEPS; 1 -> STEPS / 2; else -> STEPS / 3 }
        var wx = rx; var wz = rz
        for (s in 0 until steps) {
            track[s * 3] = wx
            track[s * 3 + 1] = ground + kit.terrainHeight(wx, wz) + LIFT
            track[s * 3 + 2] = wz
            grad(kit, wx, wz, bg)
            val gm = sqrt(bg[0] * bg[0] + bg[1] * bg[1])
            // Flat ground: the walk stops advancing and the remaining samples pile up here. That
            // is the bead at rest at the bottom, not a broken integrator.
            if (gm > 1e-4f) {
                wx -= bg[0] / gm * STEP_LEN
                wz -= bg[1] / gm * STEP_LEN
            }
        }

        val rel = SceneParts.step(c, 0.06f, 0.06f)
        val roll = SceneParts.step(c, 0.13f, 0.47f)
        val ta = 1f - SceneParts.step(c, 0.90f, 0.08f)     // the track is left glowing, then cleared
        val tk = SceneParts.TAKEN
        val fk = roll * (steps - 1)
        val last = fk.toInt().coerceIn(0, steps - 1)
        for (s in 0 until last) {
            val a0 = ta * (0.35f + 0.55f * s / steps)
            val a1 = ta * (0.35f + 0.55f * (s + 1) / steps)
            v = MathMesh.segment(
                line, v,
                track[s * 3], track[s * 3 + 1], track[s * 3 + 2],
                track[(s + 1) * 3], track[(s + 1) * 3 + 1], track[(s + 1) * 3 + 2],
                tk[0], tk[1], tk[2], a0, a1
            )
        }
        // Where the bead is now: part-way along the segment it is currently on.
        val f2 = (fk - last).coerceIn(0f, 1f)
        val nx = (last + 1).coerceAtMost(steps - 1)
        val bx = track[last * 3] + (track[nx * 3] - track[last * 3]) * f2
        val by = track[last * 3 + 1] + (track[nx * 3 + 1] - track[last * 3 + 1]) * f2
        val bz = track[last * 3 + 2] + (track[nx * 3 + 2] - track[last * 3 + 2]) * f2
        if (last > 0) {
            v = MathMesh.segment(
                line, v, track[last * 3], track[last * 3 + 1], track[last * 3 + 2], bx, by, bz,
                tk[0], tk[1], tk[2], ta * 0.9f
            )
        }

        // The uphill reading at the bead's OWN place, dim and short. Without it "the opposite
        // direction" is a claim about a needle two units away over different ground; with it, the
        // reversal can be checked where it happens.
        if (q == 0) {
            grad(kit, rx, rz, bg)
            val gm = sqrt(bg[0] * bg[0] + bg[1] * bg[1])
            if (gm > 1e-5f) {
                val ry = ground + kit.terrainHeight(rx, rz) + LIFT
                val wd = SceneParts.WORK_DIM
                v = MathMesh.arrow(
                    line, v, rx, ry, rz,
                    bg[0] / gm * 0.62f, 0f, bg[1] / gm * 0.62f,
                    0f, 1f, 0f, wd[0], wd[1], wd[2], 0.8f
                )
                v = MathMesh.arc(
                    line, v, rx, ry, rz, 1f, 0f, 0f, 0f, 0f, 1f,
                    0.22f, 0f, TAU, 10, tk[0], tk[1], tk[2], 0.6f
                )
            }
        }

        kit.flushLines(v, 2.4f)
        kit.flushTris(tv)

        // --- the solid parts ----------------------------------------------------------------------
        // The needle's shaft as a rod rather than a line: it is the centrepiece and it should have
        // a body in stereo. Stopped just short of the head so the two do not interpenetrate.
        kit.rod(
            pc[0], cy, pc[2],
            pc[0] + dx * (len - hh * 0.75f), cy, pc[2] + dz * (len - hh * 0.75f),
            0.045f, SceneParts.WORK, hot, 0.8f + kit.beat * 0.8f
        )

        // The bead. Heavy is a colour decision as much as a size one: steel with a red core, so it
        // reads as a weight that was put down rather than a light that was switched on. It fades
        // out with its own track at the end of the cycle rather than snapping back to the release
        // point — a bead that teleported up the hill once every twenty-two seconds would be the
        // one thing in the scene arguing against everything else in it.
        kit.ball(
            bx, by + 0.12f + (1f - rel) * 1.4f, bz, 0.14f, 0.14f, 0.14f,
            SceneParts.STEEL, tk, ta, 0f, 0f, 1f, 0f, 0f, 0.5f, false
        )

        // --- the belly drum -----------------------------------------------------------------------
        // The measurement is something the ship physically does. The boom reaches from the keel out
        // and down to the card's centre, and the needle springs from where it lands. Thin, because
        // five units of strut across the middle of the view would compete with the thing it is
        // there to deliver.
        if (kit.reach > 0.03f) {
            val t = kit.reach
            val ax = kit.shipX; val ay = kit.shipY - 0.16f; val az = kit.shipZ
            val ex = ax + (pc[0] - ax) * t
            val ey = ay + (cy - ay) * t
            val ez = az + (pc[2] - az) * t
            kit.rod(ax, ay, az, ex, ey, ez, 0.028f, SceneParts.STEEL, SceneParts.LAMP, 0.3f)
            kit.ball(
                ex, ey, ez, 0.10f, 0.10f, 0.10f,
                SceneParts.LAMP, hot, t, 0f, 0f, 1f, 0f, 0f, 1.2f + kit.beat * 2f
            )
        }

        // --- notation -------------------------------------------------------------------------------
        // Out along the needle's own perpendicular, which is what reads as BESIDE it when you are
        // looking down at a horizontal figure, plus just enough height to lift the glyphs off the
        // card so they neither z-fight it nor drift up into the telemetry block.
        kit.text(
            "∇f", tipX + px * 0.60f, cy + 0.24f, tipZ + pz * 0.60f,
            0.30f, hot, 1f, GlyphBoard.Style.MATH, 1.2f
        )
        // Secondary, so quality 0 only. The minus sign is the entire claim of the demonstration.
        if (q == 0) {
            kit.text(
                "−∇f", bx + px * 0.55f, by + 0.22f, bz + pz * 0.55f,
                0.24f, tk, 0.9f * ta, GlyphBoard.Style.MATH, 1f
            )
        }
    }
}
