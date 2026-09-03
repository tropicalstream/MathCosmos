package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * TOUR V, stop 2 — THE TWO CUTS. "Hold one direction still and I am back to a single curve I
 * already know how to handle."
 *
 * The flagship of the tour, and the whole of the partial derivative in one picture. Two translucent
 * cutting planes stand through the craft at the stop — one running along world x, one along world z
 * — and where each meets the country a bright profile curve is drawn on the ground. The curves
 * cross at exactly one place, directly under the keel. The arms go down into both planes, take a
 * tangent needle from each, and bring the pair up to arm's length beside the hull where the two
 * tilts can be compared side by side. That asymmetry, in stereo, at reading distance, is the stop.
 *
 * THE CUTS ARE WELDED TO THE WORLD, NOT TO THE CRAFT. Everything is anchored on the rail frame at
 * the stop and never moves again; the curves are functions of world (x, z) sampled at fixed places
 * on the ground. The ambient country learned this the hard way — geometry that tracks the ship
 * slides underneath you and reads instantly as fake — and a cut whose plane crept along the rail
 * would also be a lie about which point the two numbers belong to.
 *
 * THE CUTS ARE AXIS-ALIGNED, NOT RAIL-ALIGNED. Every other scene in the series builds out of the
 * rail's side and up vectors; this one cannot, because a partial derivative is defined by holding
 * one COORDINATE still, and the coordinates here are world x and world z. So the two planes ignore
 * the rail's heading entirely and the rail frame is used only to place the readable parts — the
 * held needles and the notation — where a viewer's eye already is. (The tour's "y" is world z: the
 * ground is the (x, z) plane and height is world y. The labels say y because the crew and the
 * design say y.)
 *
 * WHAT THE GROUND ACTUALLY DOES HERE. The crew's line is that one cut can be uphill while the
 * other is downhill, and on this terrain that happens elsewhere; under this stop it does not. The
 * x cut climbs at about a third, the y cut is all but dead level — the rail is threading a trough
 * that runs the length of the leg, which is why it is comfortable to fly. That is the same lesson
 * with a different pair of numbers and it is what the code draws, because drawing the dramatic
 * version would mean drawing a landscape that is not the one out of the window.
 *
 * The slopes are taken by central difference off [SceneKit.terrainHeight] rather than by
 * differentiating the tour's formula by hand: the scene then tells the truth about whatever ground
 * the tour hands it, and on a terrain this smooth a step of 0.3 is good to three decimal places.
 *
 * Budget: three buffer flushes (curtains, structure, the two bright profiles), one bead, four rods.
 */
object SceneTwoCuts : MathScene {

    // The figure is fourteen units across the country and reaches seven units back down the rail,
    // so it must fade up early and must not be culled at its own stop.
    override val reach = 1.6f
    override val deep = 0.5f

    // ---- the cuts ------------------------------------------------------------------------------
    private const val SPAN = 7f            // world units of cut either side of the crossing
    private const val N0 = 28              // samples per cut at quality 0
    private const val TOP = 1.15f          // the curtain's top rail, above the rail centre
    private const val DIFF = 0.30f         // central-difference step for the two partials
    private const val FILL = 0.11f         // the veil: any heavier and the country behind it goes
    // The profiles sit a shade proud of the ground for the same reason the ambient's contour rings
    // do: a cut can land on a lattice row of the country's own mesh, and two lines in one plane
    // z-fight into a dashed mess on this hardware.
    private const val LIFT = 0.02f

    // ---- the needles ---------------------------------------------------------------------------
    private const val LEN_GROUND = 2.8f    // long enough to read as tangent to a fourteen-unit curve
    private const val LEN_HELD = 0.95f     // and short enough to hold up in front of a face
    private const val ROD_GROUND = 0.05f
    private const val ROD_HELD = 0.028f
    private const val HOLD_SIDE = -1.15f   // to port, clear of the rail: a figure on the rail is one
    private const val HOLD_AHEAD = 0.5f    // you fly into, and these two are meant to be looked at
    private const val HOLD_UP_X = -0.30f
    private const val HOLD_UP_Y = -0.92f
    private const val ARM_R = 0.026f

    private const val PERIOD = 26f

    private val X_C = SceneParts.WORK      // the x cut: warm, and nothing else in the country is
    private val Y_C = SceneParts.CHALK     // the y cut: chalk, told apart from the blue ground mesh
                                           // by weight and from the teal contour rings by hue

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val rf = FloatArray(12)        // readout's own frame: the HUD may ask out of step
    private val hs = FloatArray(3)         // the rail's side, flattened level — the comparison axis
    private val o = FloatArray(3)
    private val q3 = FloatArray(3)
    private val cen = FloatArray(3)
    private val ndir = FloatArray(3)
    private val foot = FloatArray(3)
    private val tv = IntArray(1)
    // Both cuts run over the same parameter, so one table of offsets serves both; only the two
    // columns of sampled ground height differ.
    private val tt = FloatArray(N0 + 1)
    private val hxA = FloatArray(N0 + 1)
    private val hzB = FloatArray(N0 + 1)

    /** ∂f/∂x at a place on the ground, by central difference. */
    private fun slopeX(kit: SceneKit, x: Float, z: Float): Float =
        (kit.terrainHeight(x + DIFF, z) - kit.terrainHeight(x - DIFF, z)) / (2f * DIFF)

    /** ∂f/∂y — the tour's y, which is world z. */
    private fun slopeZ(kit: SceneKit, x: Float, z: Float): Float =
        (kit.terrainHeight(x, z + DIFF) - kit.terrainHeight(x, z - DIFF)) / (2f * DIFF)

    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTerrain) return null
        // The renderer asks the FLOOR stop's scene, so whenever this line is on the HUD the floor
        // is our own index and the frame there is the crossing.
        kit.frame(kit.progress.toInt().coerceIn(0, kit.stopCount - 1).toFloat(), rf)
        val x0 = rf[0]; val z0 = rf[2]
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return when {
            // One number for every place — the thing the tour opened with, before it is cut.
            c < 0.22f -> String.format(Locale.US, "f %+.2f", kit.terrainHeight(x0, z0))
            c < 0.46f -> String.format(Locale.US, "∂f/∂x %+.2f", slopeX(kit, x0, z0))
            else -> String.format(
                Locale.US, "∂f/∂x %+.2f   ∂f/∂y %+.2f",
                slopeX(kit, x0, z0), slopeZ(kit, x0, z0)
            )
        }
    }

    /**
     * One cutting plane's structure: the translucent curtain hanging between a level top rail and
     * the ground beneath it, plus a few vertical rulings.
     *
     * The curtain is trimmed to the band between the ground and the craft's own height rather than
     * being an infinite sheet — an untrimmed plane fills the eye and hides the country it is
     * supposed to be cutting, and the trimmed band still reads as a plane because its top edge is
     * dead level and its bottom edge is the profile.
     *
     * [edge] is how far out from the crossing the cut has opened, in world units; the alpha ramp at
     * that rim is what makes the plane grow rather than appear.
     */
    private fun curtain(
        kit: SceneKit, alongX: Boolean, x0: Float, z0: Float, topY: Float,
        hy: FloatArray, n: Int, edge: Float, c: FloatArray, lv: Int
    ): Int {
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = lv
        for (k in 0 until n) {
            val a0 = ((edge - abs(tt[k])) * 0.9f).coerceIn(0f, 1f)
            val a1 = ((edge - abs(tt[k + 1])) * 0.9f).coerceIn(0f, 1f)
            if (a0 < 0.02f && a1 < 0.02f) continue
            val gx0 = if (alongX) x0 + tt[k] else x0
            val gz0 = if (alongX) z0 else z0 + tt[k]
            val gx1 = if (alongX) x0 + tt[k + 1] else x0
            val gz1 = if (alongX) z0 else z0 + tt[k + 1]
            // The veil. A trapezoid, not a parallelogram — the top edge is level and the bottom one
            // is the terrain — so it is written vertex by vertex rather than through MathMesh.quad.
            // It is heaviest against the ground, where the cut is, and nearly gone at the top rail.
            if (kit.quality < 2 && tv[0] + 6 <= kit.triCapacity) {
                var w = tv[0]
                w = MathMesh.vertex(tri, w, gx0, hy[k], gz0, c[0], c[1], c[2], a0 * FILL)
                w = MathMesh.vertex(tri, w, gx1, hy[k + 1], gz1, c[0], c[1], c[2], a1 * FILL)
                w = MathMesh.vertex(tri, w, gx1, topY, gz1, c[0], c[1], c[2], a1 * FILL * 0.22f)
                w = MathMesh.vertex(tri, w, gx0, hy[k], gz0, c[0], c[1], c[2], a0 * FILL)
                w = MathMesh.vertex(tri, w, gx1, topY, gz1, c[0], c[1], c[2], a1 * FILL * 0.22f)
                w = MathMesh.vertex(tri, w, gx0, topY, gz0, c[0], c[1], c[2], a0 * FILL * 0.22f)
                tv[0] = w
            }
            v = MathMesh.segment(
                line, v, gx0, topY, gz0, gx1, topY, gz1,
                c[0], c[1], c[2], a0 * 0.30f, a1 * 0.30f
            )
            // Rulings every fourth sample. Edge-on — and one of these planes is always close to
            // edge-on, because the craft flies along it — a bare sheet reads as a line; the rulings
            // are what give it depth in stereo.
            if (kit.quality == 0 && k % 4 == 0) {
                v = MathMesh.segment(
                    line, v, gx0, hy[k], gz0, gx0, topY, gz0,
                    c[0], c[1], c[2], a0 * 0.24f, a0 * 0.05f
                )
            }
        }
        return v
    }

    /** The bright curve itself: where the plane meets the country. Drawn in its own heavy pass. */
    private fun profile(
        kit: SceneKit, alongX: Boolean, x0: Float, z0: Float,
        hy: FloatArray, n: Int, edge: Float, c: FloatArray, lv: Int
    ): Int {
        val line = kit.lineBuf
        var v = lv
        for (k in 0 until n) {
            val a0 = ((edge - abs(tt[k])) * 0.9f).coerceIn(0f, 1f)
            val a1 = ((edge - abs(tt[k + 1])) * 0.9f).coerceIn(0f, 1f)
            if (a0 < 0.02f && a1 < 0.02f) continue
            val gx0 = if (alongX) x0 + tt[k] else x0
            val gz0 = if (alongX) z0 else z0 + tt[k]
            val gx1 = if (alongX) x0 + tt[k + 1] else x0
            val gz1 = if (alongX) z0 else z0 + tt[k + 1]
            v = MathMesh.segment(
                line, v, gx0, hy[k], gz0, gx1, hy[k + 1], gz1,
                c[0], c[1], c[2], a0 * 0.95f, a1 * 0.95f
            )
        }
        return v
    }

    /** The dashed stub left behind on the ground where a needle was lifted from. */
    private fun ghost(
        line: FloatArray, lv: Int, x: Float, y: Float, z: Float,
        axX: Float, axZ: Float, slope: Float, half: Float, c: FloatArray, a: Float
    ): Int {
        val d = sqrt(1f + slope * slope)
        val dx = axX / d * half; val dy = slope / d * half; val dz = axZ / d * half
        return MathMesh.dashed(
            line, lv, x - dx, y - dy, z - dz, x + dx, y + dy, z + dz,
            7, c[0], c[1], c[2], a
        )
    }

    /**
     * Where one needle is this frame, into [cen] and [ndir].
     *
     * The needle starts lying tangent to its own curve, on the ground, pointing along its own
     * coordinate axis. As it comes up to be looked at, its HEADING is swung round to the craft's
     * side so that both needles end up in one plane facing the viewer — otherwise the y needle,
     * which points down the direction of travel, would be seen end-on and read as a dot.
     *
     * The swing turns the heading and nothing else: the horizontal part is renormalised BEFORE the
     * rise is put back, so the tilt — which is the only quantity in the needle — is exactly the
     * slope at every instant of the move. The crew say this out loud: we turned them to face you,
     * we did not change them.
     */
    private fun placeNeedle(
        x0: Float, gy: Float, z0: Float,
        axX: Float, axZ: Float, slope: Float, holdUp: Float, lift: Float
    ) {
        // Pick the end of the side axis that the needle is already nearest, so a needle that starts
        // out almost parallel to it swings a few degrees rather than reversing through nothing.
        val s = if (axX * hs[0] + axZ * hs[2] < 0f) -1f else 1f
        var hx = axX + (hs[0] * s - axX) * lift
        var hz = axZ + (hs[2] * s - axZ) * lift
        var l = sqrt(hx * hx + hz * hz)
        if (l < 1e-4f) { hx = hs[0] * s; hz = hs[2] * s; l = 1f }
        hx /= l; hz /= l
        val d = sqrt(1f + slope * slope)
        ndir[0] = hx / d; ndir[1] = slope / d; ndir[2] = hz / d
        // ...and the needle travels from the crossing up to arm's length beside the hull.
        SceneParts.at(g, HOLD_SIDE, holdUp, HOLD_AHEAD, o)
        cen[0] = x0 + (o[0] - x0) * lift
        cen[1] = gy + (o[1] - gy) * lift
        cen[2] = z0 + (o[2] - z0) * lift
    }

    /**
     * A needle's name, hung off its inboard end and riding with it.
     *
     * It has to be built from the needle's CURRENT centre rather than from where the needle is
     * going to end up, or the notation floats at the hull while the thing it names is still four
     * units down on the ground. The glyph shrinks as the needle comes up, because a world-space
     * glyph height is a world measurement: 0.30 at the crossing and 0.19 at arm's length subtend
     * about the same angle, and 0.19 is what reads cleanly at reading distance.
     *
     * Inboard, never above or below: the telemetry block owns the top quarter of the eye and the
     * caption box the bottom fifth, and there are two of these needles stacked one over the other.
     *
     * [sep] is the one piece of stagecraft here. While both needles are still down on the ground
     * they share a centre — they cross there, that is the point of the stop — so their two names
     * would be written on top of each other. [sep] prises them apart, and is faded out by the lift,
     * because once the pair is up at the hull the needles are already a clear stack apart and the
     * names should sit square on their own ends.
     */
    private fun label(
        s: String, kit: SceneKit, half: Float, lift: Float, up: Float, sep: Float, c: FloatArray
    ) {
        val d = half + 0.22f
        kit.text(
            s, cen[0] + hs[0] * d, cen[1] + ndir[1] * d + sep * (1f - lift), cen[2] + hs[2] * d,
            0.30f - 0.11f * lift, c, 0.95f * up, GlyphBoard.Style.MATH, 1f, anchor = -0.5f
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // A tour with no country has nothing to cut.
        if (!kit.hasTerrain) return

        SceneParts.stage(kit, i.toFloat(), 0f, 0f, f, g)
        val x0 = f[0]; val y0 = f[1]; val z0 = f[2]

        // The comparison axis: the rail's side with its climb taken out. The rail pitches about a
        // degree over a leg, and a tilt measured against a sloping reference would be a degree
        // wrong — invisible here, but the needles are the one thing at this stop that is a
        // measurement, so they are built against true level and the world's own up.
        hs[0] = f[6]; hs[1] = 0f; hs[2] = f[8]
        val hl = sqrt(hs[0] * hs[0] + hs[2] * hs[2])
        if (hl > 1e-4f) { hs[0] /= hl; hs[2] /= hl } else { hs[0] = 1f; hs[2] = 0f }

        val topY = y0 + TOP
        val gy = SceneAmbientCountry.GROUND_Y + LIFT + kit.terrainHeight(x0, z0)
        val sx = slopeX(kit, x0, z0)
        val sz = slopeZ(kit, x0, z0)

        val samples = if (kit.quality == 0) N0 else N0 / 2
        for (k in 0..samples) {
            val t = -SPAN + 2f * SPAN * k / samples
            tt[k] = t
            hxA[k] = SceneAmbientCountry.GROUND_Y + LIFT + kit.terrainHeight(x0 + t, z0)
            hzB[k] = SceneAmbientCountry.GROUND_Y + LIFT + kit.terrainHeight(x0, z0 + t)
        }

        // --- the loop -------------------------------------------------------------------------
        // Cut, cut, take, take, lift, and then a third of the cycle standing still with both
        // needles held, because the comparison is the payload and a viewer arrives at any moment.
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val cutA = SceneParts.step(c, 0.05f, 0.13f)
        val cutB = SceneParts.step(c, 0.20f, 0.13f)
        val takeA = SceneParts.step(c, 0.36f, 0.09f)
        val takeB = SceneParts.step(c, 0.43f, 0.09f)
        val lift = SceneParts.step(c, 0.56f, 0.17f)
        val edgeA = cutA * SPAN
        val edgeB = cutB * SPAN

        val line = kit.lineBuf
        var v = 0
        tv[0] = 0

        // --- the two curtains, and the light structure --------------------------------------
        v = curtain(kit, true, x0, z0, topY, hxA, samples, edgeA, X_C, v)
        v = curtain(kit, false, x0, z0, topY, hzB, samples, edgeB, Y_C, v)

        // The plumb: the craft, and the one place on the ground both numbers are about.
        if (kit.quality < 2) {
            v = MathMesh.dashed(
                line, v, x0, gy, z0, x0, y0, z0, 11,
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.30f
            )
        }

        // What each needle was taken from, left dashed on the curve it came off.
        if (lift > 0.03f && kit.quality < 2) {
            v = ghost(line, v, x0, gy, z0, 1f, 0f, sx, LEN_GROUND * 0.5f, X_C, 0.28f * lift)
            v = ghost(line, v, x0, gy, z0, 0f, 1f, sz, LEN_GROUND * 0.5f, Y_C, 0.28f * lift)
        }

        // Level, through each held needle. Without something to be tilted AGAINST, two sticks at
        // eighteen degrees and two degrees are just two sticks.
        if (lift > 0.5f && kit.quality < 2) {
            val a = (lift - 0.5f) * 2f * 0.34f
            SceneParts.at(g, HOLD_SIDE - 0.62f, HOLD_UP_X, HOLD_AHEAD, o)
            SceneParts.at(g, HOLD_SIDE + 0.62f, HOLD_UP_X, HOLD_AHEAD, q3)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], q3[0], q3[1], q3[2], 8,
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], a)
            SceneParts.at(g, HOLD_SIDE - 0.62f, HOLD_UP_Y, HOLD_AHEAD, o)
            SceneParts.at(g, HOLD_SIDE + 0.62f, HOLD_UP_Y, HOLD_AHEAD, q3)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], q3[0], q3[1], q3[2], 8,
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], a)
        }

        kit.flushLines(v, 1.6f)
        kit.flushTris(tv[0])

        // --- the two curves, heavy ------------------------------------------------------------
        // A second pass rather than a second width on the first: refilling the buffer from zero
        // costs one more draw call and nothing else, and these two lines are the stop.
        v = 0
        v = profile(kit, true, x0, z0, hxA, samples, edgeA, X_C, v)
        v = profile(kit, false, x0, z0, hzB, samples, edgeB, Y_C, v)
        kit.flushLines(v, 2.8f)

        // --- the crossing ------------------------------------------------------------------------
        // One bead, on the ground, where both curves pass. Both arms reach for the same point, and
        // that is not a shortcut: the whole claim of the stop is that the two numbers belong to ONE
        // place, so they had better be taken from one.
        kit.ball(
            x0, gy, z0, 0.085f, 0.085f, 0.085f, SceneParts.HOT, SceneParts.LAMP,
            0.55f + 0.45f * cutA, glow = 0.8f + 0.5f * kit.beat
        )

        // --- the needles, and the arms that hold them ----------------------------------------
        // The arm feet are in the STOP's frame, not at the live hull. This stop is one of the
        // tour's armStops so the probes really are out here, but kit.reach lights the arms rather
        // than driving them: the tableau has to read the same whether a viewer arrives with the
        // probes deployed or stowed, and an arm anchored to a moving hull while its tip holds
        // something fixed would stretch to eight units on the approach.
        val armLit = 0.30f + 0.75f * kit.reach

        if (takeA > 0.01f) {
            placeNeedle(x0, gy, z0, 1f, 0f, sx, HOLD_UP_X, lift)
            val half = (LEN_GROUND + (LEN_HELD - LEN_GROUND) * lift) * 0.5f * takeA
            val r = ROD_GROUND + (ROD_HELD - ROD_GROUND) * lift
            SceneParts.at(g, -0.14f, -0.16f, 0.10f, foot)
            kit.rod(foot[0], foot[1], foot[2], cen[0], cen[1], cen[2], ARM_R,
                SceneParts.STEEL, SceneParts.CHALK, armLit * 0.5f)
            kit.rod(
                cen[0] - ndir[0] * half, cen[1] - ndir[1] * half, cen[2] - ndir[2] * half,
                cen[0] + ndir[0] * half, cen[1] + ndir[1] * half, cen[2] + ndir[2] * half,
                r, X_C, SceneParts.HOT, 0.9f
            )
            label("∂f/∂x", kit, half, lift, takeA, 0.30f, X_C)
        }

        if (takeB > 0.01f) {
            placeNeedle(x0, gy, z0, 0f, 1f, sz, HOLD_UP_Y, lift)
            val half = (LEN_GROUND + (LEN_HELD - LEN_GROUND) * lift) * 0.5f * takeB
            val r = ROD_GROUND + (ROD_HELD - ROD_GROUND) * lift
            SceneParts.at(g, -0.30f, -0.16f, 0.10f, foot)
            kit.rod(foot[0], foot[1], foot[2], cen[0], cen[1], cen[2], ARM_R,
                SceneParts.STEEL, SceneParts.CHALK, armLit * 0.5f)
            kit.rod(
                cen[0] - ndir[0] * half, cen[1] - ndir[1] * half, cen[2] - ndir[2] * half,
                cen[0] + ndir[0] * half, cen[1] + ndir[1] * half, cen[2] + ndir[2] * half,
                r, Y_C, SceneParts.HOT, 0.9f
            )
            label("∂f/∂y", kit, half, lift, takeB, -0.30f, Y_C)
        }

        // --- what each plane is doing ---------------------------------------------------------
        // Hung on the body of each curtain, five units out, so it names the plane rather than
        // crowding the needles. Larger than the notation at the hull because it is four times as
        // far away — a glyph height is a world measurement, not a screen one.
        if (kit.quality == 0) {
            val k = samples * 6 / 7
            if (cutA > 0.8f) {
                val y = (topY + hxA[k]) * 0.5f
                kit.text("y FIXED", x0 + tt[k], y, z0, 0.36f, X_C, 0.55f * cutA,
                    GlyphBoard.Style.SMALL, 0.8f)
            }
            if (cutB > 0.8f) {
                val y = (topY + hzB[k]) * 0.5f
                kit.text("x FIXED", x0, y, z0 + tt[k], 0.36f, Y_C, 0.55f * cutB,
                    GlyphBoard.Style.SMALL, 0.8f)
            }
        }
    }
}
