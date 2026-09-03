package com.rayneo.mathcosmos

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 6 of TOUR V — THE ANY-DIRECTION. "Whatever heading I pick, the steepness I feel is just the
 * gradient's shadow on that heading."
 *
 * A dial lies flat on the country at the stop. Sixteen spokes lie in it, and from the hub sixteen
 * needles rise or fall, each tilted by the slope you would feel walking that way. Their tips trace
 * one closed loop. The loop is highest exactly over the gradient arrow, crosses the dial plane
 * exactly along the contour direction, and dips lowest exactly downhill — which is the whole of
 * D_u f = ∇f · u, drawn rather than asserted.
 *
 * THE LOOP IS A PLANE SECTION, AND THAT IS THE POINT. Because D_u f is linear in u, the tip
 * heights round the dial are |∇f| cos θ, so the sixteen tips are not a lumpy rosette — they are a
 * perfectly flat tilted ring, a ring cut out of the tangent plane from stop 3. If a viewer reads
 * it as "the needles sweep out the plate we landed earlier", they have read it right. The
 * "cosine rosette" of the design note is what that ring looks like unrolled: one cosine, one
 * maximum, two zeros, one minimum, and nothing else can happen.
 *
 * TWO EXAGGERATIONS, AND THEY ARE THE SAME NUMBER. This ground climbs about a fifth of a unit per
 * unit walked, so a needle drawn at true tilt would be a hair off flat and a gradient arrow drawn
 * at true length would be a stub two hundred millimetres long. Both are magnified by the same
 * fixed gain — the vertical gain on a needle is GAIN·R, the horizontal gain on the flat arrow is
 * ARM, and ARM is DEFINED as GAIN·R so they cannot drift apart. The consequence is worth the
 * arithmetic: the HEIGHT of a needle and the LENGTH of the gradient's shadow underneath it are the
 * same number drawn twice, once vertically and once horizontally, and a viewer can compare them by
 * eye. The gain is fixed rather than normalised, so the picture stays honest about proportion; it
 * is a magnification, not a rescaling, and the crew says so.
 *
 * THE DIAL IS WELDED TO THE GROUND, not carried under the craft. A dial that followed the ship
 * would slide over the country like a treadmill — the mistake SceneAmbientCountry's grid goes to
 * some trouble to avoid — and could never be looked at twice. It sits at the stop, and a faint
 * plumb line drops to it from the rail so it still reads as the ship's own reading of this place.
 * For the same reason the yaw in the design note ("the ship yaws through headings") is done by a
 * bright heading sweeping round the dial rather than by turning the craft: the rail cannot yaw,
 * and a swinging horizon on a head-mounted display is a way to make people ill.
 *
 * PLACEMENT. Flat and to port, three units out and about two below the rail, so it is looked down
 * into on the approach rather than flown through. It reaches five and a half units to port against
 * a passage radius of four, which in this tour is correct: the tube is at wall alpha 0.18 and the
 * country is the scenery.
 *
 * Budget: one flushLines, one flushTris, one rod, two balls and at most four labels.
 */
object SceneAnyDirection : MathScene {

    override val reach = 1.4f

    private const val SIDE = -3.2f              // world units to port of the rail
    private const val R = 2.2f                  // dial radius
    private const val GAIN = 2.6f               // vertical magnification of a needle
    private const val ARM = GAIN * R            // horizontal magnification of the flat arrow
    private const val PERIOD = 24f
    private const val DH = 0.25f                // central-difference step for the gradient
    private const val LOBE = 32                 // samples round the tip loop at quality 0
    private const val READS = 24                // HUD buckets: one every fifteen degrees
    private const val TWO_PI = 6.2831853f

    private const val SWEEP_AT = 0.40f
    private const val SWEEP_LEN = 0.46f

    // The claim, in two beats. Both are compile-time constants, so hanging one of them costs no
    // allocation — a string built in draw() would allocate thirty times a second.
    private const val CLAIM_A = "D_u f"
    private const val CLAIM_B = "D_u f = ∇f · u"

    private val f = FloatArray(12)
    private val g = FloatArray(12)

    // ---- the place, measured once ---------------------------------------------------------
    // The hub never moves, so the terrain under it and the gradient there are constants of the
    // scene. They still have to be MEASURED, through kit.terrainHeight, because a scene is not
    // allowed to know the tour's terrain expression; a central difference at a quarter of a unit
    // is exact to well under a per cent against a country whose shortest wavelength is twenty.
    private var built = false
    private var hubX = 0f
    private var hubY = 0f
    private var hubZ = 0f
    private var railY = 0f
    private var gx = 0f                         // ∇f in the (x, z) domain — a flat vector
    private var gz = 0f
    private var gm = 0f
    private var phi = 0f                        // the uphill bearing
    private var portX = 0f                      // outboard horizontal unit vector, for labels
    private var portZ = 0f
    private val READ = arrayOfNulls<String>(READS)

    /**
     * The heading's turn off uphill, 0..1 of a full circle. Shared by draw() and readout() so the
     * HUD cannot disagree with the picture. It rests at a whole turn, which is uphill again, so
     * the loop closes where it opened and there is no snap back.
     */
    private fun turn(c: Float): Float = SceneParts.ease((c - SWEEP_AT) / SWEEP_LEN)

    /** Hundredths as a decimal, for the HUD. Called only while the string table is being built. */
    private fun dec(v: Float): String {
        val n = round(v * 100f).toInt()
        val a = if (n < 0) -n else n
        val fr = a % 100
        return (if (n < 0) "-" else "") + (a / 100) + "." + (if (fr < 10) "0" else "") + fr
    }

    private fun build(kit: SceneKit, i: Int) {
        if (built) return
        SceneParts.stage(kit, i.toFloat(), SIDE, 0f, f, g)
        // The dial lies in the DOMAIN plane, which is world-horizontal, not in the rail's plane:
        // terrainHeight is a function of world (x, z) and a dial that tilted with the rail would
        // report slopes in a frame the country has never heard of. The rail frame is used for one
        // thing only — deciding which way is outboard — so the dial sits beside the rail whichever
        // way the rail happens to be pointing.
        var sx = f[6]; var sz = f[8]
        val sl = sqrt(sx * sx + sz * sz)
        if (sl > 1e-4f) { sx /= sl; sz /= sl } else { sx = 1f; sz = 0f }
        portX = -sx; portZ = -sz                // SIDE is negative, so outboard is against the side
        hubX = g[0]; hubZ = g[2]; railY = g[1]
        hubY = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(hubX, hubZ)
        gx = (kit.terrainHeight(hubX + DH, hubZ) - kit.terrainHeight(hubX - DH, hubZ)) / (2f * DH)
        gz = (kit.terrainHeight(hubX, hubZ + DH) - kit.terrainHeight(hubX, hubZ - DH)) / (2f * DH)
        gm = sqrt(gx * gx + gz * gz)
        phi = atan2(gz, gx)
        // Every line the HUD can ever show, built here rather than formatted per frame. Fifteen
        // degrees of resolution is plenty: a number that changes every frame cannot be read.
        for (k in 0 until READS) {
            val d = gm * cos(k * TWO_PI / READS)
            READ[k] = "θ ${k * 360 / READS}°   SLOPE ${dec(d)} OF ${dec(gm)}"
        }
        built = true
    }

    /**
     * Nothing until the first draw has measured the place: readout() is handed no stop index and
     * so cannot find the hub on its own, and a HUD line invented from an unmeasured gradient would
     * be a number pretending to be wired up.
     */
    override fun readout(kit: SceneKit): String? {
        if (!built) return null
        var k = round(turn(SceneParts.cycle(kit.seconds, PERIOD)) * READS).toInt() % READS
        if (k < 0) k += READS
        return READ[k]
    }

    /** One flat triangle straight into the triangle buffer, with its own room check. */
    private fun vane(
        tri: FloatArray, at: Int,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        col: FloatArray, alpha: Float
    ): Int {
        if ((at + 3) * MathMesh.STRIDE > tri.size) return at
        var k = MathMesh.vertex(tri, at, ax, ay, az, col[0], col[1], col[2], alpha)
        k = MathMesh.vertex(tri, k, bx, by, bz, col[0], col[1], col[2], alpha)
        k = MathMesh.vertex(tri, k, cx, cy, cz, col[0], col[1], col[2], alpha)
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // A tour with no country has no slopes to take in any direction.
        if (!kit.hasTerrain) return
        build(kit, i)

        val q = kit.quality
        val spokes = if (q == 0) 16 else 8      // both are multiples of four, which is what puts a
        val nl = if (q == 0) LOBE else 16       // spoke exactly uphill and two exactly on the contour
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val grow = SceneParts.step(c, 0.02f, 0.12f)     // the flat dial opens out
        val tilt = SceneParts.step(c, 0.16f, 0.14f)     // the needles lift off flat
        val arrow = SceneParts.step(c, 0.26f, 0.08f)    // the uphill arrow arrives
        val trace = SceneParts.step(c, 0.32f, 0.08f)    // the tip loop is traced round
        val live = SceneParts.step(c, 0.38f, 0.06f)     // the chosen heading lights up
        val t = turn(c)
        val rad = R * grow

        val chalk = SceneParts.CHALK
        val hot = SceneParts.HOT
        val teal = SceneParts.ADDED

        // --- the plumb, so an offset dial still belongs to the ship ---------------------------
        // Dropped at quality 2 with the rest of the decoration; it explains the staging, it is not
        // part of the mathematics.
        if (q < 2) {
            v = MathMesh.dashed(
                line, v, hubX, railY, hubZ, hubX, hubY, hubZ, 8,
                chalk[0], chalk[1], chalk[2], 0.16f
            )
        }

        // --- the dial: the domain plane, drawn dim on purpose ---------------------------------
        // This is the ground PLANE, not the ground surface — the same distinction stop 4 makes out
        // loud about the gradient arrow. It is the flat thing every needle is measured against, so
        // it must be visible and must never compete with them.
        if (grow > 0.001f) {
            v = MathMesh.arc(
                line, v, hubX, hubY, hubZ, 1f, 0f, 0f, 0f, 0f, 1f,
                rad, phi, phi + grow * TWO_PI, if (q == 0) 28 else 14,
                chalk[0], chalk[1], chalk[2], 0.28f
            )
        }

        // --- sixteen spokes, and on each of them a needle --------------------------------------
        // Spoke zero is laid along the gradient rather than along world x, which is what makes the
        // maximum, the two zeros and the minimum land ON spokes instead of between them. The
        // needle and its flat spoke share a hub, so the angle between them IS the slope angle, and
        // the vane filling that angle is what makes the tilt read in stereo at four units.
        for (k in 0 until spokes) {
            val a = phi + k * TWO_PI / spokes
            val ux = cos(a); val uz = sin(a)
            val ex = hubX + ux * rad; val ez = hubZ + uz * rad
            val d = gx * ux + gz * uz
            val col = if (d >= 0f) SceneParts.WORK else SceneParts.TAKEN
            v = MathMesh.segment(line, v, hubX, hubY, hubZ, ex, hubY, ez, chalk[0], chalk[1], chalk[2], 0.30f)
            val ty = hubY + GAIN * rad * d * tilt
            v = MathMesh.segment(line, v, hubX, hubY, hubZ, ex, ty, ez, col[0], col[1], col[2], 0.85f)
            if (q < 2 && tilt > 0.02f) {
                tv = vane(tri, tv, hubX, hubY, hubZ, ex, hubY, ez, ex, ty, ez, col, 0.16f)
            }
        }

        // --- the closed loop the tips trace -----------------------------------------------------
        // Traced from the uphill mark round, so it opens at the maximum. Flat, because D_u f is
        // linear in u: this ring is a ring of the tangent plane, magnified vertically.
        if (trace > 0.001f) {
            val segs = (trace * nl).toInt()
            var px = 0f; var py = 0f; var pz = 0f
            for (j in 0..segs) {
                val a = phi + j * TWO_PI / nl
                val ux = cos(a); val uz = sin(a)
                val d = gx * ux + gz * uz
                val x = hubX + ux * R
                val y = hubY + GAIN * R * d
                val z = hubZ + uz * R
                if (j > 0) v = MathMesh.segment(line, v, px, py, pz, x, y, z, hot[0], hot[1], hot[2], 1f)
                px = x; py = y; pz = z
            }
        }

        // --- the gradient, lying flat, and the contour direction it is square to -----------------
        val cx = cos(phi); val cz = sin(phi)
        val alen = ARM * gm * arrow
        if (arrow > 0.01f) {
            v = MathMesh.arrow(
                line, v, hubX, hubY, hubZ, cx * alen, 0f, cz * alen, 0f, 1f, 0f,
                teal[0], teal[1], teal[2], 0.95f
            )
            // The chord where the loop crosses the dial: the contour through this place, and the
            // two headings along which you climb nothing at all.
            v = MathMesh.dashed(
                line, v, hubX + cz * R, hubY, hubZ - cx * R, hubX - cz * R, hubY, hubZ + cx * R,
                10, teal[0], teal[1], teal[2], 0.45f * arrow
            )
        }

        // --- the heading you have chosen, and the shadow it takes ---------------------------------
        val ha = phi + t * TWO_PI
        val hux = cos(ha); val huz = sin(ha)
        val hd = gx * hux + gz * huz
        val htx = hubX + hux * R; val htz = hubZ + huz * R
        val hty = hubY + GAIN * R * hd
        if (live > 0.01f) {
            v = MathMesh.segment(line, v, hubX, hubY, hubZ, htx, hubY, htz, hot[0], hot[1], hot[2], 0.9f * live)
            // The drop from tip to dial: its length is the needle's rise.
            v = MathMesh.dashed(line, v, htx, hty, htz, htx, hubY, htz, 5, hot[0], hot[1], hot[2], 0.55f * live)
            // And the shadow itself: the gradient arrow's tip dropped square onto this heading.
            // The foot of that perpendicular is at ARM·(∇f·u) from the hub, which is the same
            // length as the drop above it — the identity, twice, at right angles.
            val sx2 = hubX + hux * ARM * hd * arrow
            val sz2 = hubZ + huz * ARM * hd * arrow
            v = MathMesh.dashed(
                line, v, hubX + cx * alen, hubY, hubZ + cz * alen, sx2, hubY, sz2, 6,
                teal[0], teal[1], teal[2], 0.5f * live
            )
            v = MathMesh.segment(line, v, hubX, hubY, hubZ, sx2, hubY, sz2, teal[0], teal[1], teal[2], live)
        }

        // One width for the whole buffer; the dial, the needles, the loop and the construction are
        // told apart by colour and alpha instead, exactly as the country's own mesh and rings are.
        kit.flushLines(v, 2.2f)
        if (tv > 0) kit.flushTris(tv)

        // --- the two solids -----------------------------------------------------------------------
        kit.ball(hubX, hubY, hubZ, 0.075f, 0.075f, 0.075f, chalk, teal, 0.95f)
        if (live > 0.01f) {
            kit.rod(hubX, hubY, hubZ, htx, hty, htz, 0.032f, hot, SceneParts.LAMP, 1.2f * live)
            kit.ball(htx, hty, htz, 0.085f, 0.085f, 0.085f, hot, SceneParts.LAMP, live, 0f, 0f, 1f, 0f, 0f, 2f * live)
        }

        // --- notation, hung outboard so it clears both the HUD block and the caption box ----------
        val lx = hubX + portX * (R + 0.35f)
        val lz = hubZ + portZ * (R + 0.35f)
        kit.text(
            if (live > 0.5f) CLAIM_B else CLAIM_A, lx, hubY + 0.72f, lz, 0.22f,
            hot, 1f, GlyphBoard.Style.MATH, 1.15f, anchor = 0.5f
        )
        if (q < 2 && arrow > 0.4f) {
            kit.text(
                "∇f", hubX + cx * (alen + 0.28f), hubY + 0.12f, hubZ + cz * (alen + 0.28f),
                0.20f, teal, 0.95f
            )
        }
        // Secondary marks, quality 0 only: the zero on the contour heading, and the letter on the
        // live one. Both are gated on their own geometry being up, so neither spends a draw call
        // on a label at zero alpha.
        if (q == 0 && arrow > 0.4f) {
            kit.text(
                "0", hubX + cz * (R + 0.22f), hubY + 0.08f, hubZ - cx * (R + 0.22f),
                0.16f, teal, 0.8f, GlyphBoard.Style.SMALL
            )
        }
        if (q == 0 && live > 0.1f) {
            kit.text(
                "u", hubX + hux * (R + 0.26f), hubY + 0.08f, hubZ + huz * (R + 0.26f),
                0.18f, hot, 0.9f * live
            )
        }
    }
}
