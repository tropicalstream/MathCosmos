package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

/**
 * TOUR V's AMBIENT — THE OPEN COUNTRY. Not a stop: the ground.
 *
 * Every other scene in the app is a figure hung beside the rail. This one is the world the rail
 * runs through, drawn every frame for thirty-three minutes, and it is the biggest stereo payoff in
 * the series — a wireframe landscape z = f(x, y) spread out around the craft, with the level sets
 * of that same function lit on it like tide marks. Tour V's whole argument is that one number for
 * every place IS a landscape; this is that sentence made out of lines.
 *
 * Three decisions carry the whole thing, and none of them is obvious:
 *
 * FIRST, THE GRID IS WELDED TO THE GROUND, NOT TO THE CRAFT. The sample positions are snapped to a
 * fixed world lattice — the patch window advances one whole cell at a time as the craft flies —
 * so a grid line is a place on the ground and stays there while you pass it. The obvious
 * implementation, sampling at (craft + offset), makes a mesh that slides along underneath you like
 * a treadmill, and in stereo that reads instantly and horribly as the ground being fake. A new row
 * appears at the patch's rim, where the edge mask has it at nothing, so nothing pops.
 *
 * SECOND, THE PATCH WINDOW IS RAIL-ALIGNED EVEN THOUGH THE GRID IS NOT. The lattice runs along
 * world x and z because terrainHeight is a function of world (x, z) and a grid that turned with
 * the rail would swim; but the alpha mask that fades the patch out is an ellipse in the rail's own
 * horizontal frame, 26 units along the heading by 22 across. So the patch always looks ahead of
 * you and always has a soft rim rather than a border, whichever way the rail is pointing. (At the
 * rail's steepest heading a sliver of that ellipse falls off the sampled rectangle's corner; out
 * there the mask is already down in the low single figures of a per cent, and it does not show.)
 *
 * THIRD, THE LEVELS ARE FIXED HEIGHTS, NOT A SPREAD OF WHAT HAPPENS TO BE IN VIEW. Adapting the
 * eight levels to the local minimum and maximum would keep them prettily spaced and would make
 * every ring a liar: it would move under you. Stops 5 and 8 fly ALONG these contours and the crew
 * talks about them as fixed marks on the ground, so they are computed by marching squares against
 * eight absolute heights and they stay put. The half-step offset (levels at ±0.3, ±0.9, ±1.5,
 * ±2.1 rather than whole multiples) is deliberate too — a level sitting exactly on a saddle value
 * is the one case where marching squares has to guess, and the offset makes that essentially
 * impossible on a smooth terrain.
 *
 * Budget. ONE draw call — a single flushLines, under 2500 vertices at quality 0 — because this is
 * not a landmark that fades past in twenty seconds, it is on screen for the whole tour and shares
 * the frame with whichever stop is alongside. The grid is capped below the contours' share so that
 * if anything is ever truncated it is the mesh and not the level sets. No triangles: a filled
 * surface would hide the landmarks standing on it, and the corridor scenes of the earlier tours
 * already learned that a mesh of glowing lines reads as a surface AND lets the rest of the scene
 * through.
 *
 * Two things this scene deliberately does NOT do.
 *
 * It does not stay inside the passage radius. Every other scene must, or it is buried in the wall;
 * this one is 26 units by 22 in a passage four wide, and it is supposed to be. Tour V drops
 * wallAlpha to 0.18 for exactly this reason — the tube goes ghost so that the country can be seen
 * through it, and a landscape trimmed to the tube would be a corridor with a rug in it.
 *
 * And it carries no notation. A label here would hang in the eye for thirty-three minutes,
 * competing with the telemetry block above it and the caption box below it and with every stop's
 * own labels in between. The country is named by the stops standing on it; it does not need to
 * announce itself. For the same reason there is no readout(): the HUD asks the CURRENT STOP's
 * scene what it is measuring, and an ambient is never any node's scene, so a readout here would be
 * dead code pretending to be wired up.
 */
object SceneAmbientCountry : MathScene {

    /**
     * The world y at which [SceneKit.terrainHeight] reads zero — the datum the country hangs from.
     *
     * The terrain function is written about the origin and swings roughly +/- 2.65, while the rail
     * sits at y ~ 0, so drawn raw the craft would fly through the dirt for half the tour. Dropping
     * the whole country by three units is the honest fix: the RELIEF is untouched (no gain, no
     * flattening, so a slope read off this mesh is the slope the maths says it is), the highest
     * ground under the rail still passes about two thirds of a unit beneath the keel, and the
     * summits five units out to either side come up to within a third of a unit of eye level. That is the "look left, there is a
     * mountain" the tour opens with.
     *
     * It is public because every Tour V and VI scene that puts something ON the ground must add
     * the same constant, or the landmarks and the country will disagree about where the ground is.
     */
    const val GROUND_Y = -3.0f

    // ---- the patch ---------------------------------------------------------------------------
    private const val ALONG = 26f          // world units of country along the heading
    private const val ACROSS = 22f         // and across it
    private const val NA0 = 22             // samples along, at quality 0
    private const val NC0 = 18             // samples across, at quality 0
    private const val AHEAD = 5f           // the patch is pushed this far up the rail: you look forward
    private const val EDGE = 0.34f         // the outer third of the ellipse is the fade band

    // ---- the levels --------------------------------------------------------------------------
    private const val LEVELS = 8
    private const val STEP = 0.6f
    private const val BASE = -2.1f         // level k is BASE + k * STEP
    private const val LIFT = 0.03f         // rings sit just proud of the mesh, so they do not z-fight

    // ---- weights and clocks -------------------------------------------------------------------
    private const val GRID_A = 0.34f
    private const val RING_A = 0.60f
    private const val BUDGET = 2500
    private const val RING_RESERVE = 900   // vertices the grid may not touch
    private const val PERIOD = 24f
    private const val SWEEP = 0.72f        // the survey band crosses in this much of the cycle; then it rests

    private val fr = FloatArray(12)
    private val xs = FloatArray(NC0)
    private val zs = FloatArray(NA0)
    private val h = FloatArray(NA0 * NC0)      // sampled heights, row-major: j * nc + i
    private val al = FloatArray(NA0 * NC0)     // edge mask times the survey band, per sample
    private val ex = FloatArray(4)             // marching-squares crossings, indexed by cell edge
    private val ez = FloatArray(4)
    private val hit = BooleanArray(4)

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // A tour with no terrain callback draws no country. The renderer hands this scene the
        // node and index of whatever stop the craft last passed; the country cares about neither.
        if (!kit.hasTerrain) return

        val q = kit.quality
        val nc = if (q == 0) NC0 else NC0 / 2      // 18 across, or 9
        val na = if (q == 0) NA0 else NA0 / 2      // 22 along, or 11
        val cellC = ACROSS / (nc - 1)
        val cellA = ALONG / (na - 1)
        val halfC = (nc - 1) * 0.5f
        val halfA = (na - 1) * 0.5f

        // --- the frame, flattened -----------------------------------------------------------
        // Only the horizontal part of the rail's forward is wanted: the country is a function of
        // (x, z) and the patch must lie flat however much the rail is climbing.
        kit.frame(kit.progress, fr)
        var hx = fr[3]; var hz = fr[5]
        val hl = sqrt(hx * hx + hz * hz)
        if (hl > 1e-4f) { hx /= hl; hz /= hl } else { hx = 0f; hz = -1f }
        val ccx = kit.shipX + hx * AHEAD
        val ccz = kit.shipZ + hz * AHEAD

        // --- the lattice, snapped ------------------------------------------------------------
        // round() to whole cells is what welds the mesh to the ground; the mask below still uses
        // the craft's true position, so the patch glides and only the geometry is quantised.
        val baseC = round(ccx / cellC)
        val baseA = round(ccz / cellA)
        for (ii in 0 until nc) xs[ii] = (baseC + (ii - halfC)) * cellC
        for (jj in 0 until na) zs[jj] = (baseA + (jj - halfA)) * cellA

        // --- sample the country, and the light on it ------------------------------------------
        // The survey band is the one moving thing in the scene: a soft brightening that walks in
        // from the far rim to the craft over three quarters of the cycle and then leaves the
        // landscape plain to be looked at. It is a light, not a shape — the ground itself never
        // moves, which is the whole point of the snapping above. Dropped entirely at quality 2 by
        // parking it far outside the patch.
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val band = if (q >= 2) -9f else 1f - 2f * (c / SWEEP).coerceIn(0f, 1f)
        val halfAlong = ALONG * 0.5f
        val halfAcross = ACROSS * 0.5f
        for (jj in 0 until na) {
            val z = zs[jj]
            val b = jj * nc
            for (ii in 0 until nc) {
                val x = xs[ii]
                h[b + ii] = kit.terrainHeight(x, z)
                val dx = x - ccx; val dz = z - ccz
                val w = (dx * hx + dz * hz) / halfAlong          // along the heading
                val u = (dz * hx - dx * hz) / halfAcross         // across it
                var a = SceneParts.ease((1f - sqrt(u * u + w * w)) / EDGE)
                val bd = abs(w - band) * 5f
                if (bd < 1f) { val t = 1f - bd; a *= 1f + 1.7f * t * t }
                al[b + ii] = a
            }
        }

        val line = kit.lineBuf
        val cap = if (kit.lineCapacity < BUDGET) kit.lineCapacity else BUDGET
        val gridCap = if (cap > RING_RESERVE + 64) cap - RING_RESERVE else cap
        var v = 0

        // --- the two families of grid lines ---------------------------------------------------
        // Each segment carries its endpoints' own alphas, which is how the patch dissolves at its
        // rim instead of ending in a rectangle. A segment with both ends dark is skipped outright
        // — that is a fifth of the mesh saved, and it is the fifth outside the ellipse.
        val cr = SceneParts.COOL
        for (jj in 0 until na) {
            if (v + 2 > gridCap) break
            val b = jj * nc
            val z = zs[jj]
            for (ii in 0 until nc - 1) {
                val a0 = al[b + ii]; val a1 = al[b + ii + 1]
                if (a0 < 0.02f && a1 < 0.02f) continue
                if (v + 2 > gridCap) break
                v = MathMesh.segment(
                    line, v,
                    xs[ii], GROUND_Y + h[b + ii], z,
                    xs[ii + 1], GROUND_Y + h[b + ii + 1], z,
                    cr[0], cr[1], cr[2], a0 * GRID_A, a1 * GRID_A
                )
            }
        }
        for (ii in 0 until nc) {
            if (v + 2 > gridCap) break
            val x = xs[ii]
            for (jj in 0 until na - 1) {
                val p = jj * nc + ii; val pn = p + nc
                val a0 = al[p]; val a1 = al[pn]
                if (a0 < 0.02f && a1 < 0.02f) continue
                if (v + 2 > gridCap) break
                v = MathMesh.segment(
                    line, v,
                    x, GROUND_Y + h[p], zs[jj],
                    x, GROUND_Y + h[pn], zs[jj + 1],
                    cr[0], cr[1], cr[2], a0 * GRID_A, a1 * GRID_A
                )
            }
        }

        // --- the contours ----------------------------------------------------------------------
        // Marching squares over the same sampled heights — no second pass over the terrain, and no
        // approximation: where a level crosses a cell edge the crossing is found by linear
        // interpolation between the two corner heights, exactly as the mesh drawn above joins them.
        // These are real level sets of the same function the mesh is, which is why stop 5 can fly
        // one and stop 8 can kiss one.
        //
        // Quality 1 and 2 keep every other level rather than rescaling: the four that survive are
        // four of the original eight, in the same places, so a thermal step does not move the
        // ground marks under a viewer who is watching them.
        val rc = SceneParts.ADDED
        val k0 = if (q == 0) 0 else 1
        val kStep = if (q == 0) 1 else 2
        for (jj in 0 until na - 1) {
            if (v + 2 > cap) break
            val b0 = jj * nc; val b1 = b0 + nc
            val z0 = zs[jj]; val z1 = zs[jj + 1]
            for (ii in 0 until nc - 1) {
                if (v + 2 > cap) break
                val ca = (al[b0 + ii] + al[b0 + ii + 1] + al[b1 + ii] + al[b1 + ii + 1]) * 0.25f
                if (ca < 0.04f) continue
                val h00 = h[b0 + ii]; val h10 = h[b0 + ii + 1]
                val h01 = h[b1 + ii]; val h11 = h[b1 + ii + 1]
                var lo = h00; var hi = h00
                if (h10 < lo) lo = h10 else if (h10 > hi) hi = h10
                if (h01 < lo) lo = h01 else if (h01 > hi) hi = h01
                if (h11 < lo) lo = h11 else if (h11 > hi) hi = h11
                val x0 = xs[ii]; val x1 = xs[ii + 1]
                var k = k0
                while (k < LEVELS) {
                    val lv = BASE + k * STEP
                    if (lv > lo && lv < hi) {
                        val a00 = h00 > lv; val a10 = h10 > lv
                        val a01 = h01 > lv; val a11 = h11 > lv
                        var cnt = 0
                        hit[0] = a00 != a10                                   // south edge
                        if (hit[0]) { val t = (lv - h00) / (h10 - h00); ex[0] = x0 + (x1 - x0) * t; ez[0] = z0; cnt++ }
                        hit[1] = a10 != a11                                   // east edge
                        if (hit[1]) { val t = (lv - h10) / (h11 - h10); ex[1] = x1; ez[1] = z0 + (z1 - z0) * t; cnt++ }
                        hit[2] = a01 != a11                                   // north edge
                        if (hit[2]) { val t = (lv - h01) / (h11 - h01); ex[2] = x0 + (x1 - x0) * t; ez[2] = z1; cnt++ }
                        hit[3] = a00 != a01                                   // west edge
                        if (hit[3]) { val t = (lv - h00) / (h01 - h00); ex[3] = x0; ez[3] = z0 + (z1 - z0) * t; cnt++ }
                        val y = GROUND_Y + lv + LIFT
                        // Index contours: every second ring heavier, the map-maker's convention,
                        // and the cheapest way to make eight rings countable at a glance.
                        var a = ca * RING_A
                        if (q == 0 && (k and 1) == 0) a *= 1.4f
                        if (a > 1f) a = 1f
                        if (cnt == 2) {
                            var p = -1; var r = -1
                            for (e in 0 until 4) if (hit[e]) { if (p < 0) p = e else r = e }
                            v = MathMesh.segment(line, v, ex[p], y, ez[p], ex[r], y, ez[r], rc[0], rc[1], rc[2], a)
                        } else if (cnt == 4 && v + 4 <= cap) {
                            // The ambiguous cell: opposite corners agree, so the level cuts it into
                            // two arcs and there are two ways to draw them. The centre's own value
                            // decides which pair of corners the level lets through, which is the
                            // standard resolution and the one that keeps a saddle looking like a
                            // saddle instead of an X.
                            val mid = (h00 + h10 + h01 + h11) * 0.25f
                            if ((mid > lv) == a00) {
                                v = MathMesh.segment(line, v, ex[0], y, ez[0], ex[1], y, ez[1], rc[0], rc[1], rc[2], a)
                                v = MathMesh.segment(line, v, ex[2], y, ez[2], ex[3], y, ez[3], rc[0], rc[1], rc[2], a)
                            } else {
                                v = MathMesh.segment(line, v, ex[0], y, ez[0], ex[3], y, ez[3], rc[0], rc[1], rc[2], a)
                                v = MathMesh.segment(line, v, ex[1], y, ez[1], ex[2], y, ez[2], rc[0], rc[1], rc[2], a)
                            }
                        }
                    }
                    k += kStep
                }
            }
        }

        // One width for grid and rings alike, because they share the one buffer and the one call.
        // They are told apart by colour and weight instead: the mesh is the tour's cool blue at a
        // third alpha, the rings its bright teal at two thirds, with every second ring heavier.
        kit.flushLines(v, 2f)
    }
}
