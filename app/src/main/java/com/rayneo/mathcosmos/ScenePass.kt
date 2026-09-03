package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR V, stop 7 — THE PASS. "A place can be a summit one way and a valley the other, and that's a
 * real kind of place."
 *
 * The col of a mountain pass, with the two cuts through it drawn on the ground: one arching OVER,
 * a maximum along that direction, and one dipping UNDER, a minimum along the other. Both tangent
 * needles lie flat, because the gradient here is nothing at all. A ball put down on the col sits
 * perfectly still, and then the arm nudges it — and where it ends up depends entirely on which way
 * it was pushed.
 *
 * THE PASS IS FOUND, NOT WRITTEN DOWN. The country is one fixed terrain callback shared by the
 * whole tour, and the design note's "the terrain becomes a mountain pass" is not something a scene
 * can do — a landmark may stand on the ground, it may not reshape it. So this scene goes looking
 * for a real saddle of the real terrain instead: a coarse lattice of heights around the node, a
 * discrete Hessian at each interior sample, one Newton prediction from every sample whose
 * determinant is negative, and then twelve Newton steps on the gradient from the prediction that
 * lands nearest the stop. Everything after that — the two principal directions, the curvatures,
 * the arm lengths, the four roll-off tracks — falls out of the terrain rather than out of a
 * constant in this file, so if the country is ever retuned the pass moves with it and nothing here
 * needs touching. If the search finds no saddle within range the scene draws nothing, which is the
 * honest outcome: there is no pass here to stand on.
 *
 * The search is a few hundred terrain samples and it runs ONCE, on the first frame the landmark
 * fades up, and is then cached for the life of the process. That is one frame with a fraction of a
 * millisecond of extra work in it, on a device that will not notice; recomputing it per frame
 * would be thirty times a second of the same answer.
 *
 * WHERE IT IS. On the current country the nearest genuine saddle sits about seven units to
 * starboard and seven up-rail of the node, and getting on for three units below the keel — which
 * puts it abeam the craft roughly half a stop before the stop itself, at four or five units, which
 * is where the pass is meant to be looked at. That is a long way outside the
 * passage radius and it is supposed to be: Tour V runs at wall alpha 0.18 precisely so the country
 * can be seen through the tube, and a pass trimmed to fit a four-unit corridor would be a bump.
 *
 * THE TWO ARMS ARE DELIBERATELY DIFFERENT LENGTHS, and this is the one thing about the picture
 * that has to be explained rather than admired. Real saddles are almost never symmetric: this one
 * falls away across the ridge about nineteen times faster than it climbs along it. Drawn to equal
 * lengths, the climb would be a flat line and the picture would say "maximum one way, nothing the
 * other", which is the wrong sentence. So each arm is run out to where the ground has moved a
 * fixed amount — one unit of height — which makes the road arm short and steep and the ridge arm
 * long and gentle, and makes the RATIO of the two lengths the second-derivative test drawn as a
 * shape. No vertical exaggeration anywhere: every height in this scene is the height the terrain
 * callback returns.
 *
 * THE CURTAINS ARE WHAT MAKES IT READ. A curve on a hillside is very hard to see bending at 640 by
 * 480; a curve with a translucent sheet hanging between it and a horizontal reference line is not.
 * So each cut gets a curtain between the flat tangent line and the true ground, hanging DOWN in
 * the colour of a loss on the cut that falls, standing UP in the colour of a gain on the cut that
 * climbs. Two sheets on opposite sides of the same horizontal line is the whole of the stop, and
 * it is the same trick the directional-derivative dial uses for the same reason.
 *
 * THE BALL IS A HEAVY BALL IN SYRUP, and that is an approximation the crew names out loud. It runs
 * down the gradient with no momentum at all — speed proportional to steepness, capped so it does
 * not outrun the eye — so it cannot roll up the far side and it cannot oscillate. That is wrong
 * for a marble and right for the argument, which is about where downhill POINTS, not about
 * dynamics. Nudged across the ridge it is gone in a couple of seconds; nudged along the ridge it
 * creeps back down to the col, hesitates, and only then starts to slip off one flank. Four nudge
 * directions, one per cycle, chosen off the clock, so a viewer who watches two passes sees two
 * different answers to the same push.
 */
object ScenePass : MathScene {

    /** Wide: the pass is best abeam, half a stop before the node, and must be up by then. */
    override val reach = 1.7f

    /** The ridge arm reaches about five units past the node down-rail. */
    override val deep = 0.4f

    private const val PERIOD = 20f

    // ---- the search ---------------------------------------------------------------------------
    private const val SCAN_N = 17          // a 17 x 17 lattice of heights...
    private const val SCAN_STEP = 1.5f     // ...covering twelve units each way around the node
    private const val DH = 0.25f           // central-difference step for the refined jet
    private const val MAX_SEED = 8f        // a Newton prediction further than this is not believed
    private const val MAX_OUT = 14f        // and a saddle further than this from the node is no use

    // ---- the figure ---------------------------------------------------------------------------
    private const val RELIEF = 1.0f        // each arm runs out to one unit of height change
    private const val ROAD_MIN = 2.5f
    private const val ROAD_MAX = 6f
    private const val RIDGE_MIN = 6f
    private const val RIDGE_MAX = 12f
    private const val NCUT = 24            // samples per cut, so twelve to each side of the col
    private const val LIFT = 0.05f         // clear of the ambient country's own mesh
    private const val NEEDLE = 1.6f        // half length of the two solid tangent needles
    private const val TAU = 6.2831853f

    // ---- the ball -------------------------------------------------------------------------------
    private const val STEPS = 28
    private const val KICK = 0.8f          // how far the nudge itself moves it
    private const val RATE = 5f            // steps per unit of slope...
    private const val MAXS = 0.20f         // ...capped, so a steep flank does not blur

    private val fr = FloatArray(12)
    private val gs = FloatArray(12)
    private val jet = FloatArray(5)        // f_x, f_z, f_xx, f_zz, f_xz — build() only
    private val gv = FloatArray(2)         // gradient during the descent walk — build() only
    private val scan = FloatArray(SCAN_N * SCAN_N)
    private val road = FloatArray((NCUT + 1) * 3)      // the cut that falls away, world points
    private val ridge = FloatArray((NCUT + 1) * 3)     // the cut that climbs
    private val track = FloatArray(4 * STEPS * 3)      // four roll-offs, one per nudge direction

    // Everything below is measured once and then constant: the terrain does not move, so neither
    // does the pass. readout() reads these from the UI thread and draw() from the GL thread, which
    // is safe precisely because after build() nothing writes to them again.
    private var built = false
    private var found = false
    private var colX = 0f
    private var colY = 0f
    private var colZ = 0f
    private var mastY = 0f                 // the rail's altitude above the col
    private var nodeX = 0f
    private var nodeZ = 0f
    private var dsx = 1f                   // the road: the axis of negative curvature
    private var dsz = 0f
    private var upx = 0f                   // the ridge: the axis of positive curvature
    private var upz = 1f
    private var kDown = 0f
    private var kUp = 0f
    private var roadL = 0f
    private var ridgeL = 0f
    private var outX = 1f                  // horizontally outboard, node toward col
    private var outZ = 0f
    private var roadEnd = NCUT             // which end of each cut carries its label
    private var ridgeEnd = 0
    private var restLine: String? = null
    private val fell = arrayOfNulls<String>(4)

    // ------------------------------------------------------------------ measuring

    /** Gradient and Hessian by central differences: nine height samples, no allocation. */
    private fun jetAt(kit: SceneKit, x: Float, z: Float, out: FloatArray) {
        val c0 = kit.terrainHeight(x, z)
        val px = kit.terrainHeight(x + DH, z)
        val mx = kit.terrainHeight(x - DH, z)
        val pz = kit.terrainHeight(x, z + DH)
        val mz = kit.terrainHeight(x, z - DH)
        val pp = kit.terrainHeight(x + DH, z + DH)
        val pm = kit.terrainHeight(x + DH, z - DH)
        val mp = kit.terrainHeight(x - DH, z + DH)
        val mm = kit.terrainHeight(x - DH, z - DH)
        out[0] = (px - mx) / (2f * DH)
        out[1] = (pz - mz) / (2f * DH)
        out[2] = (px - 2f * c0 + mx) / (DH * DH)
        out[3] = (pz - 2f * c0 + mz) / (DH * DH)
        out[4] = (pp - pm - mp + mm) / (4f * DH * DH)
    }

    private fun slope(kit: SceneKit, x: Float, z: Float, out: FloatArray) {
        out[0] = (kit.terrainHeight(x + DH, z) - kit.terrainHeight(x - DH, z)) / (2f * DH)
        out[1] = (kit.terrainHeight(x, z + DH) - kit.terrainHeight(x, z - DH)) / (2f * DH)
    }

    /**
     * Find the pass, then build every fixed part of the figure out of it.
     *
     * The lattice differences use a spacing of a unit and a half against a country whose shortest
     * feature is about twenty units across, which is far too coarse to trust as a derivative and
     * exactly good enough to say "there is a saddle somewhere near here" — which is all a seed has
     * to do. The twelve Newton steps afterwards run on a proper quarter-unit central difference
     * and are what the rest of the scene is built from.
     */
    private fun build(kit: SceneKit, i: Int) {
        if (built) return
        built = true

        SceneParts.stage(kit, i.toFloat(), 0f, 0f, fr, gs)
        nodeX = gs[0]; nodeZ = gs[2]
        val railY = gs[1]
        val half = SCAN_N / 2

        for (j in 0 until SCAN_N) {
            val z = nodeZ + (j - half) * SCAN_STEP
            for (k in 0 until SCAN_N) {
                scan[j * SCAN_N + k] = kit.terrainHeight(nodeX + (k - half) * SCAN_STEP, z)
            }
        }

        // --- the seed: the nearest place a saddle is predicted to be ---------------------------
        var bestX = 0f
        var bestZ = 0f
        var bestScore = Float.MAX_VALUE
        val s = SCAN_STEP
        for (j in 1 until SCAN_N - 1) {
            for (k in 1 until SCAN_N - 1) {
                val o = j * SCAN_N + k
                val gx = (scan[o + 1] - scan[o - 1]) / (2f * s)
                val gz = (scan[o + SCAN_N] - scan[o - SCAN_N]) / (2f * s)
                val a = (scan[o + 1] - 2f * scan[o] + scan[o - 1]) / (s * s)
                val c = (scan[o + SCAN_N] - 2f * scan[o] + scan[o - SCAN_N]) / (s * s)
                val b = (scan[o + SCAN_N + 1] - scan[o - SCAN_N + 1] -
                        scan[o + SCAN_N - 1] + scan[o - SCAN_N - 1]) / (4f * s * s)
                val det = a * c - b * b
                // A negative determinant is the whole classification: one way up, one way down.
                if (det >= -1e-6f) continue
                val stepX = -(c * gx - b * gz) / det
                val stepZ = -(-b * gx + a * gz) / det
                if (abs(stepX) > MAX_SEED || abs(stepZ) > MAX_SEED) continue
                val px = nodeX + (k - half) * s + stepX
                val pz = nodeZ + (j - half) * s + stepZ
                val score = (px - nodeX) * (px - nodeX) + (pz - nodeZ) * (pz - nodeZ)
                if (score < bestScore) { bestScore = score; bestX = px; bestZ = pz }
            }
        }
        if (bestScore == Float.MAX_VALUE) return

        // --- the refinement --------------------------------------------------------------------
        var px = bestX
        var pz = bestZ
        for (n in 0 until 12) {
            jetAt(kit, px, pz, jet)
            val det = jet[2] * jet[3] - jet[4] * jet[4]
            if (abs(det) < 1e-7f) break
            val ddx = -(jet[3] * jet[0] - jet[4] * jet[1]) / det
            val ddz = -(-jet[4] * jet[0] + jet[2] * jet[1]) / det
            px += ddx
            pz += ddz
            if (abs(ddx) + abs(ddz) < 1e-4f) break
        }
        jetAt(kit, px, pz, jet)
        val a = jet[2]; val c = jet[3]; val b = jet[4]
        // Three ways to fail, all of them meaning "that is not a pass": the walk did not converge,
        // it converged onto a summit or a bowl, or it wandered out of the country round this stop.
        if (sqrt(jet[0] * jet[0] + jet[1] * jet[1]) > 0.01f) return
        if (a * c - b * b >= 0f) return
        if (abs(px - nodeX) > MAX_OUT || abs(pz - nodeZ) > MAX_OUT) return

        colX = px
        colZ = pz
        colY = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(px, pz)
        mastY = if (railY > colY + 0.6f) railY else colY + 0.6f

        // --- the two directions ------------------------------------------------------------------
        // The principal axes of the Hessian: the one direction you can walk where the ground is
        // purely falling and the one where it is purely climbing. On this country they come out
        // within a degree or two of the world axes, but they are computed rather than assumed,
        // because a terrain with any twist in it would put the road and the ridge off square and
        // the curtains would then be measuring the wrong thing.
        val th = 0.5f * atan2(2f * b, a - c)
        val e1x = cos(th); val e1z = sin(th)
        val l1 = a * e1x * e1x + 2f * b * e1x * e1z + c * e1z * e1z
        val e2x = -e1z; val e2z = e1x
        val l2 = a * e2x * e2x + 2f * b * e2x * e2z + c * e2z * e2z
        if (l1 < l2) {
            dsx = e1x; dsz = e1z; kDown = l1
            upx = e2x; upz = e2z; kUp = l2
        } else {
            dsx = e2x; dsz = e2z; kDown = l2
            upx = e1x; upz = e1z; kUp = l1
        }
        roadL = sqrt(2f * RELIEF / abs(kDown)).coerceIn(ROAD_MIN, ROAD_MAX)
        ridgeL = sqrt(2f * RELIEF / abs(kUp)).coerceIn(RIDGE_MIN, RIDGE_MAX)

        var ox = colX - nodeX
        var oz = colZ - nodeZ
        val ol = sqrt(ox * ox + oz * oz)
        if (ol > 1e-4f) { ox /= ol; oz /= ol } else { ox = 1f; oz = 0f }
        outX = ox; outZ = oz

        // --- the two cuts, sampled on the real ground ---------------------------------------------
        for (k in 0..NCUT) {
            val t = (k.toFloat() / NCUT * 2f - 1f)
            var x = colX + dsx * t * roadL
            var z = colZ + dsz * t * roadL
            road[k * 3] = x
            road[k * 3 + 1] = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(x, z) + LIFT
            road[k * 3 + 2] = z
            x = colX + upx * t * ridgeL
            z = colZ + upz * t * ridgeL
            ridge[k * 3] = x
            ridge[k * 3 + 1] = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(x, z) + LIFT
            ridge[k * 3 + 2] = z
        }
        roadEnd = if (dsx * outX + dsz * outZ >= 0f) NCUT else 0
        ridgeEnd = if (
            (ridge[0] - nodeX) * (ridge[0] - nodeX) + (ridge[2] - nodeZ) * (ridge[2] - nodeZ) <
            (ridge[NCUT * 3] - nodeX) * (ridge[NCUT * 3] - nodeX) +
            (ridge[NCUT * 3 + 2] - nodeZ) * (ridge[NCUT * 3 + 2] - nodeZ)
        ) 0 else NCUT

        // --- the four roll-offs --------------------------------------------------------------------
        val colH = kit.terrainHeight(colX, colZ)
        for (d in 0 until 4) {
            val ax = when (d) { 0 -> dsx; 1 -> -dsx; 2 -> upx; else -> -upx }
            val az = when (d) { 0 -> dsz; 1 -> -dsz; 2 -> upz; else -> -upz }
            var wx = colX + ax * KICK
            var wz = colZ + az * KICK
            for (st in 0 until STEPS) {
                val o = (d * STEPS + st) * 3
                track[o] = wx
                track[o + 1] = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(wx, wz) + LIFT
                track[o + 2] = wz
                slope(kit, wx, wz, gv)
                val gm = sqrt(gv[0] * gv[0] + gv[1] * gv[1])
                if (gm < 1e-6f) continue
                val step = if (RATE * gm < MAXS) RATE * gm else MAXS
                wx -= gv[0] / gm * step
                wz -= gv[1] / gm * step
            }
            val o = (d * STEPS + STEPS - 1) * 3
            val ran = sqrt(
                (track[o] - colX) * (track[o] - colX) + (track[o + 2] - colZ) * (track[o + 2] - colZ)
            )
            val drop = colH - kit.terrainHeight(track[o], track[o + 2])
            fell[d] = "NUDGED %s   RAN %.1f   FELL %.2f".format(
                Locale.US, if (d < 2) "ACROSS" else "ALONG", ran, drop
            )
        }
        restLine = "ACROSS %+.3f   ALONG %+.3f   PRODUCT < 0".format(Locale.US, kDown, kUp)
        found = true
    }

    /**
     * The second-derivative test, which is exactly the kind of thing that belongs on the HUD and
     * not in the world: two curvatures with opposite signs, and the fact that their product is
     * negative. In three dimensions the picture already says "up one way, down the other"; the
     * numbers are here so it can be checked rather than believed. During the roll the line switches
     * to what the nudge actually did, because that is the measurement being taken just then.
     */
    override fun readout(kit: SceneKit): String? {
        if (!found) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        if (c < 0.48f || c > 0.94f) return restLine
        return fell[(kit.seconds / PERIOD).toInt() and 3]
    }

    // ------------------------------------------------------------------ drawing

    /**
     * One segment of a curtain: the sheet between a horizontal datum and the ground under it.
     * Faded to nearly nothing at the datum edge and solid at the ground edge, so it reads as
     * hanging from the curve rather than as a slab standing in the country.
     */
    private fun curtain(
        tri: FloatArray, at: Int,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        datum: Float, col: FloatArray, alpha: Float
    ): Int {
        if ((at + 6) * MathMesh.STRIDE > tri.size) return at
        val r = col[0]; val g = col[1]; val b = col[2]
        val e = alpha * 0.25f
        var k = MathMesh.vertex(tri, at, x0, datum, z0, r, g, b, e)
        k = MathMesh.vertex(tri, k, x0, y0, z0, r, g, b, alpha)
        k = MathMesh.vertex(tri, k, x1, y1, z1, r, g, b, alpha)
        k = MathMesh.vertex(tri, k, x0, datum, z0, r, g, b, e)
        k = MathMesh.vertex(tri, k, x1, y1, z1, r, g, b, alpha)
        k = MathMesh.vertex(tri, k, x1, datum, z1, r, g, b, e)
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No country, no pass: this stop is a place on a landscape and nothing else.
        if (!kit.hasTerrain) return
        build(kit, i)
        if (!found) return

        val q = kit.quality
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val open = SceneParts.step(c, 0.02f, 0.08f)      // the flat needles grow along both axes
        val cutA = SceneParts.step(c, 0.10f, 0.14f)      // the road draws out from the col
        val cutB = SceneParts.step(c, 0.24f, 0.14f)      // then the ridge
        val sheet = SceneParts.step(c, 0.30f, 0.12f)     // the curtains fill in
        val push = SceneParts.step(c, 0.44f, 0.04f)      // the nudge
        val roll = SceneParts.step(c, 0.48f, 0.30f)      // the run-off, then a long look at it
        val clear = SceneParts.step(c, 0.94f, 0.06f)     // ball and track fade, ready to go again
        val live = 1f - clear
        val kick = (kit.seconds / PERIOD).toInt() and 3

        val chalk = SceneParts.CHALK
        val steel = SceneParts.STEEL
        val hot = SceneParts.HOT
        val down = SceneParts.TAKEN                       // the cut that falls: the colour of a loss
        val up = SceneParts.ADDED                         // the cut that climbs
        val work = SceneParts.WORK

        // --- the mast ----------------------------------------------------------------------------
        // A dashed vertical from the col up to the rail's own altitude. The pass is seven units off
        // the rail and getting on for three below it, and without one vertical line in an otherwise
        // entirely horizontal figure there is nothing in the scene that says how far under the keel
        // it lies. Decoration, so it goes first at quality 2.
        if (q < 2) {
            v = MathMesh.dashed(
                line, v, colX, colY, colZ, colX, mastY, colZ, 8,
                chalk[0], chalk[1], chalk[2], 0.18f
            )
        }

        // --- the two horizontal datums ------------------------------------------------------------
        // These are the tangent needles of stop 2, extended the length of each arm so the curtains
        // have something to hang from. Both are dead level, and that IS the stop: at a pass every
        // cut through the place has a horizontal tangent, whichever way the ground goes afterwards.
        val ra = roadL * open
        val ua = ridgeL * open
        v = MathMesh.segment(
            line, v, colX - dsx * ra, colY, colZ - dsz * ra, colX + dsx * ra, colY, colZ + dsz * ra,
            steel[0], steel[1], steel[2], 0.70f
        )
        v = MathMesh.segment(
            line, v, colX - upx * ua, colY, colZ - upz * ua, colX + upx * ua, colY, colZ + upz * ua,
            steel[0], steel[1], steel[2], 0.70f
        )

        // --- the two cuts, and the sheets between them and level ------------------------------------
        // Both grow outward from the col rather than sweeping from one end, because what the viewer
        // has to see is the SPLIT — the same place, the two curves leaving it in opposite senses.
        val half = NCUT / 2
        val stride = if (q == 0) 1 else 2
        val ma = (cutA * half).toInt().coerceIn(0, half)
        val mb = (cutB * half).toInt().coerceIn(0, half)
        var k = half - ma
        while (k < half + ma) {
            val o = k * 3
            val p = (k + stride).coerceAtMost(half + ma) * 3
            if (p == o) break
            v = MathMesh.segment(
                line, v, road[o], road[o + 1], road[o + 2], road[p], road[p + 1], road[p + 2],
                down[0], down[1], down[2], 0.95f
            )
            if (q < 2 && sheet > 0.02f) {
                tv = curtain(
                    tri, tv, road[o], road[o + 1], road[o + 2], road[p], road[p + 1], road[p + 2],
                    colY, down, 0.22f * sheet
                )
            }
            k += stride
        }
        k = half - mb
        while (k < half + mb) {
            val o = k * 3
            val p = (k + stride).coerceAtMost(half + mb) * 3
            if (p == o) break
            v = MathMesh.segment(
                line, v, ridge[o], ridge[o + 1], ridge[o + 2], ridge[p], ridge[p + 1], ridge[p + 2],
                up[0], up[1], up[2], 0.95f
            )
            if (q < 2 && sheet > 0.02f) {
                tv = curtain(
                    tri, tv, ridge[o], ridge[o + 1], ridge[o + 2], ridge[p], ridge[p + 1], ridge[p + 2],
                    colY, up, 0.22f * sheet
                )
            }
            k += stride
        }

        // --- the risers ------------------------------------------------------------------------------
        // Dashed rules from the level line to the ground at a handful of places along each cut. The
        // curtain shows the gap; these let it be counted. Quality 0 only — they are the finest thing
        // in the scene and the first to become a smear.
        if (q == 0 && sheet > 0.1f) {
            var j = half - ma
            while (j <= half + ma) {
                val o = j * 3
                v = MathMesh.dashed(
                    line, v, road[o], colY, road[o + 2], road[o], road[o + 1], road[o + 2], 3,
                    down[0], down[1], down[2], 0.45f * sheet
                )
                j += 4
            }
            j = half - mb
            while (j <= half + mb) {
                val o = j * 3
                v = MathMesh.dashed(
                    line, v, ridge[o], colY, ridge[o + 2], ridge[o], ridge[o + 1], ridge[o + 2], 3,
                    up[0], up[1], up[2], 0.45f * sheet
                )
                j += 4
            }
        }

        // --- the col itself ---------------------------------------------------------------------------
        v = MathMesh.arc(
            line, v, colX, colY + 0.01f, colZ, 1f, 0f, 0f, 0f, 0f, 1f,
            0.45f, 0f, TAU, if (q == 0) 16 else 10, hot[0], hot[1], hot[2], 0.85f
        )

        // --- the nudge, and where it sent the ball ------------------------------------------------------
        val base = kick * STEPS * 3
        var bx = colX
        var by = colY + LIFT
        var bz = colZ
        if (roll > 0.001f) {
            val fk = roll * (STEPS - 1)
            val last = fk.toInt().coerceIn(0, STEPS - 1)
            val nxt = (last + 1).coerceAtMost(STEPS - 1)
            val t = (fk - last).coerceIn(0f, 1f)
            val o = base + last * 3
            val p = base + nxt * 3
            bx = track[o] + (track[p] - track[o]) * t
            by = track[o + 1] + (track[p + 1] - track[o + 1]) * t
            bz = track[o + 2] + (track[p + 2] - track[o + 2]) * t
            // The track it has already made, brightening as it goes, so the route is a record and
            // not a hint at one. Coarser when the governor has stepped us down: a run-off drawn
            // with half the samples is the same run-off.
            val tstep = if (q == 0) 1 else 2
            var j = 0
            while (j + tstep <= last) {
                val s0 = base + j * 3
                val s1 = base + (j + tstep) * 3
                v = MathMesh.segment(
                    line, v, track[s0], track[s0 + 1], track[s0 + 2],
                    track[s1], track[s1 + 1], track[s1 + 2],
                    work[0], work[1], work[2], live * (0.30f + 0.60f * j / STEPS)
                )
                j += tstep
            }
            val tail = base + j * 3
            v = MathMesh.segment(
                line, v, track[tail], track[tail + 1], track[tail + 2], bx, by, bz,
                work[0], work[1], work[2], live * 0.9f
            )
        } else if (push > 0.001f) {
            // The push itself: the ball slides the width of the nudge and no further.
            val o = base
            bx = colX + (track[o] - colX) * push
            by = colY + LIFT + (track[o + 1] - colY - LIFT) * push
            bz = colZ + (track[o + 2] - colZ) * push
        }

        // The impulse. A short arrow at the col in the direction of the shove, alive only while the
        // shove is happening, so the four different nudges are visibly four different nudges.
        if (push > 0.02f && roll < 0.4f) {
            val ax = when (kick) { 0 -> dsx; 1 -> -dsx; 2 -> upx; else -> -upx }
            val az = when (kick) { 0 -> dsz; 1 -> -dsz; 2 -> upz; else -> -upz }
            val fadeOut = (1f - roll * 2.5f).coerceIn(0f, 1f)
            v = MathMesh.arrow(
                line, v, colX - ax * 0.9f, colY + 0.16f, colZ - az * 0.9f,
                ax * 0.8f, 0f, az * 0.8f, 0f, 1f, 0f,
                hot[0], hot[1], hot[2], 0.9f * push * fadeOut
            )
        }

        kit.flushLines(v, 2.2f)
        if (tv > 0) kit.flushTris(tv)

        // --- the solid parts -------------------------------------------------------------------------
        // The two needles as rods rather than lines. They are short, they cross at the col, and they
        // are both exactly level: three facts that read at a glance in stereo and are the reason the
        // stop exists. The rod pair is two draw calls and worth every one of them.
        val nl = NEEDLE * open
        if (open > 0.02f) {
            kit.rod(
                colX - dsx * nl, colY, colZ - dsz * nl, colX + dsx * nl, colY, colZ + dsz * nl,
                0.038f, steel, down, 0.7f
            )
            kit.rod(
                colX - upx * nl, colY, colZ - upz * nl, colX + upx * nl, colY, colZ + upz * nl,
                0.038f, steel, up, 0.7f
            )
        }

        // The ball. Steel with a warm core: a weight that was put down, not a lamp that was lit —
        // the same reading the rolling bead at stop 4 is given, and for the same reason. It fades
        // out at its destination and back in at the col rather than teleporting up the hill once
        // every twenty seconds, which would be the one thing in the scene arguing against the rest.
        kit.ball(
            bx, by + 0.13f, bz, 0.15f, 0.15f, 0.15f, steel, hot,
            live * SceneParts.step(c, 0f, 0.06f),
            0f, 0f, 1f, 0f, 0f, 0.35f + kit.beat * 0.7f, false
        )

        // The arm that does the nudging, drawn only when the craft is close enough for a boom to be
        // a boom rather than a cable across the county. It fades in over the last couple of units of
        // approach so it never snaps into existence.
        if (push > 0.02f && roll < 0.5f) {
            val ddx = colX - kit.shipX
            val ddz = colZ - kit.shipZ
            val dist = sqrt(ddx * ddx + ddz * ddz)
            val armA = ((8f - dist) * 0.5f).coerceIn(0f, 1f) * push * (1f - roll * 2f).coerceIn(0f, 1f)
            if (armA > 0.02f) {
                val ay = kit.shipY - 0.16f
                kit.rod(
                    kit.shipX, ay, kit.shipZ, bx, by + 0.13f, bz,
                    0.024f, steel, SceneParts.LAMP, 0.25f * armA
                )
            }
        }

        // --- notation ----------------------------------------------------------------------------------
        // Outboard of the col and lifted half a unit: on a flat figure looked down into, "beside" is
        // away from the rail and a little up, which keeps the glyphs clear of the telemetry block at
        // the top of the eye and the caption box at the bottom.
        kit.text(
            "∇f = 0", colX + outX * 1.0f, colY + 0.55f, colZ + outZ * 1.0f,
            0.24f, hot, 1f, GlyphBoard.Style.MATH, 1.2f
        )
        // The two words the stop is for, one at the far end of each cut, each held back until its
        // own curve has actually been drawn. A summit one way; a valley the other.
        if (q < 2 && cutA > 0.6f) {
            val o = roadEnd * 3
            kit.text(
                "max", road[o] + dsx * 0.35f, road[o + 1] + 0.30f, road[o + 2] + dsz * 0.35f,
                0.19f, down, 0.95f, GlyphBoard.Style.PLAIN
            )
        }
        if (q < 2 && cutB > 0.6f) {
            val o = ridgeEnd * 3
            kit.text(
                "min", ridge[o] + upx * 0.35f, ridge[o + 1] + 0.30f, ridge[o + 2] + upz * 0.35f,
                0.19f, up, 0.95f, GlyphBoard.Style.PLAIN
            )
        }
        // The test itself, under the claim and smaller, at full detail only. It names what the two
        // curtains have already shown: the curvatures have opposite signs, so their product is
        // negative, so this is a pass and not a peak.
        if (q == 0 && sheet > 0.5f) {
            kit.text(
                "f_{xx} f_{zz} − f_{xz}^2 < 0",
                colX + outX * 1.0f, colY + 0.22f, colZ + outZ * 1.0f,
                0.16f, chalk, 0.85f, GlyphBoard.Style.MATH, 1f
            )
        }
    }
}
