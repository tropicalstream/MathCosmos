package com.rayneo.mathcosmos

import java.util.Locale

/**
 * TOUR V, stop 10 — THE ORDER. "Row by row or column by column, I get the same total."
 *
 * Fubini, and the reason it is a stop at all: written down it is a sentence about swapping two
 * integral signs, which is the sort of claim a reader nods at and never believes. Built out of
 * stuff it is a thing you WATCH: the same floor of columns is harvested twice, once in strips of
 * constant x and once in strips of constant y, and the two totals stand side by side afterwards at
 * exactly the same height. Nobody has to be persuaded of that.
 *
 * Four decisions carry the stop.
 *
 * FIRST, THE FIELD IS WELDED TO WORLD x AND z, NOT TO THE RAIL. The rail frame decides only WHERE
 * the patch is put down; its lattice runs along the world axes, because terrainHeight is a
 * function of world (x, z) and those are the two variables being integrated in turn. A patch that
 * turned with the heading would be sweeping along directions the function has never heard of, and
 * the two orders would stop meaning dy dx and dx dy. (The tour writes the ground coordinates as
 * (x, y); the world's second ground axis is z. Same two numbers, different letter, and the
 * notation below keeps the tour's.)
 *
 * SECOND, THE INTEGRAND IS HEIGHT ABOVE A DATUM LAID UNDER THE PATCH, not the raw terrain. This
 * country runs negative over most of its range, and a signed double integral — columns hanging
 * below the plane, a total that goes down as well as up — is a genuinely different lesson and a
 * worse first one. So the datum is dropped just under the lowest ground in the patch, every column
 * is positive, and both bars only ever grow. That is an honest simplification and the crew says so
 * out loud: this is a volume, and volumes here are all one sign.
 *
 * THIRD, THE PLANE TRAVELS AT CONSTANT SPEED AND THE BAR DOES NOT GROW AT A CONSTANT RATE. That
 * asymmetry is the whole of the inner integral. Where the strip under the plane is fat the bar
 * leaps; where the strip is thin it barely moves; and the division marks left behind are the
 * individual slice contributions, which are visibly DIFFERENT sizes in the two bars even though
 * the two bars end level. Fubini does not say the partial sums match. It says the last one does.
 *
 * FOURTH, THE BARS HANG BESIDE THE RAIL RATHER THAN STANDING ON THE GROUND. Everything else in
 * this tour lives on the country, and it should — but the country here is four units under the
 * keel, and the one thing at this stop that must be read exactly is which of two bars is taller.
 * Read at a thirty-degree depression, from a moving craft, through a waveguide, that comparison is
 * lost. So the bars are instruments: hung at eye level, square to the direction of travel so they
 * are seen face-on for the whole approach, with a dashed tether down to the field they came from.
 * The measurement is on the ground; the dial is where it can be looked at.
 *
 * Budget: one flushLines, one flushTris, two labels always and three more at quality 0, and a
 * single lamp at the instant the second bar arrives. The column field is 8 x 8 at quality 0 and
 * 4 x 4 above it, and the coarse field is the 2 x 2 block MEANS of the fine one — which preserves
 * the volume exactly, so a thermal step changes how chunky the picture is and not one digit of
 * what it reports.
 */
object SceneOrder : MathScene {

    /** Wide: the field is set down well ahead of the node and should be in view on approach. */
    override val reach = 1.6f

    /** The patch sits six and a half units up the rail from the node; do not cull it at the node. */
    override val deep = 0.7f

    // ---- the field ---------------------------------------------------------------------------
    private const val N0 = 8               // cells each way at quality 0
    private const val HALF = 2.75f         // half-width of the patch, world units
    private const val SIDE = -5.5f         // to port, far enough that the rail clears its inner edge
    private const val AHEAD = 6.5f         // and forward, or it is forty degrees under your chin
    private const val CLEAR = 0.15f        // the datum sits this far below the lowest ground in it

    // ---- the two bars ------------------------------------------------------------------------
    private const val BAR_SIDE = -2.3f
    private const val BAR_AHEAD = 5.0f
    private const val BAR_FOOT = -1.9f     // stage-up of the feet: a full bar tops out at rail level
    private const val BAR_MAX = 1.9f
    private const val BAR_W = 0.34f
    private const val BAR_GAP = 0.20f
    private const val BAR_DEPTH = 0.24f
    private const val S_A = -(BAR_W + BAR_GAP) * 0.5f
    private const val S_B = (BAR_W + BAR_GAP) * 0.5f

    // ---- the clock ---------------------------------------------------------------------------
    // Sweep, clear, sweep, and then a long look at two bars of the same height, which is the only
    // frame of this scene anybody actually has to remember.
    private const val PERIOD = 28f
    private const val S1_AT = 0.05f
    private const val S2_AT = 0.42f
    private const val SWEEP_LEN = 0.30f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p2 = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val dw = FloatArray(3)
    private val cc = FloatArray(4)         // one cell's colour, lerped between plain and harvested
    private val tv = IntArray(1)

    // ---- the survey, taken once ----------------------------------------------------------------
    // The patch is a fixed place on a fixed rail over a fixed terrain callback, so it is sampled on
    // the first frame and never again: sixty-four terrain lookups at build time instead of sixty-
    // four every frame for the length of the pass. The cache is therefore bound to the node index
    // it was first drawn for, which is fine because a scene object is one stop's landmark — but it
    // is the reason this file must not be reused for a second stop without clearing [built].
    private var built = false
    private var cx = 0f
    private var cz = 0f
    private var datum = 0f                 // terrain value the columns are measured from
    private var tall = 0f                  // the tallest column, for the sweep plane's height
    private var volTotal = 0f
    private val h8 = FloatArray(N0 * N0)               // heights above the datum, row-major j * N0 + i
    private val h4 = FloatArray((N0 / 2) * (N0 / 2))
    private val rowVol8 = FloatArray(N0)               // one strip of constant x: the inner ∫ f dy
    private val colVol8 = FloatArray(N0)               // one strip of constant y: the inner ∫ f dx
    private val rowVol4 = FloatArray(N0 / 2)
    private val colVol4 = FloatArray(N0 / 2)

    private fun build(kit: SceneKit, i: Int) {
        if (built) return
        kit.pointAt(i.toFloat(), SIDE, 0f, AHEAD, o)
        cx = o[0]; cz = o[2]

        val cell = 2f * HALF / N0
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (j in 0 until N0) {
            val z = cz + (j + 0.5f - N0 * 0.5f) * cell
            for (ii in 0 until N0) {
                val x = cx + (ii + 0.5f - N0 * 0.5f) * cell
                val t = kit.terrainHeight(x, z)
                h8[j * N0 + ii] = t
                if (t < lo) lo = t
                if (t > hi) hi = t
            }
        }
        datum = lo - CLEAR
        tall = hi - datum
        for (k in h8.indices) h8[k] -= datum

        // The two families of strips. rowVol[i] is the whole inner integral over y at that x, which
        // is exactly what one pass of the first plane harvests; colVol[j] is the other order.
        val a = cell * cell
        volTotal = 0f
        for (ii in 0 until N0) {
            var s = 0f
            for (j in 0 until N0) s += h8[j * N0 + ii]
            rowVol8[ii] = s * a
            volTotal += rowVol8[ii]
        }
        for (j in 0 until N0) {
            var s = 0f
            for (ii in 0 until N0) s += h8[j * N0 + ii]
            colVol8[j] = s * a
        }

        // The coarse field: each cell the mean of the 2 x 2 block it stands in. A mean over four
        // cells spread over four times the area is the same volume to the last bit, so the bars at
        // quality 1 are the same bars — chunkier slices of an identical total.
        val n2 = N0 / 2
        for (j in 0 until n2) {
            for (ii in 0 until n2) {
                h4[j * n2 + ii] = (
                    h8[(2 * j) * N0 + 2 * ii] + h8[(2 * j) * N0 + 2 * ii + 1] +
                        h8[(2 * j + 1) * N0 + 2 * ii] + h8[(2 * j + 1) * N0 + 2 * ii + 1]
                    ) * 0.25f
            }
        }
        for (ii in 0 until n2) rowVol4[ii] = rowVol8[2 * ii] + rowVol8[2 * ii + 1]
        for (j in 0 until n2) colVol4[j] = colVol8[2 * j] + colVol8[2 * j + 1]
        built = true
    }

    /** How much of [vols] the plane has taken by [s], counting the strip it is standing in. */
    private fun harvested(vols: FloatArray, nn: Int, s: Float): Float {
        if (s <= 0f) return 0f
        val fk = s * nn
        val k = fk.toInt().coerceAtMost(nn - 1)
        var acc = 0f
        for (m in 0 until k) acc += vols[m]
        return acc + vols[k] * (fk - k).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------ the HUD

    /**
     * The two running totals, which is the one thing here that has to be READ rather than seen.
     * Equal to two decimals at the end of the second sweep, and that equality is the theorem.
     *
     * This runs on the UI thread, so it touches nothing the renderer owns — no frame, no pointAt,
     * only the cached survey and the clock. It always sums at the fine resolution even when the
     * picture has been stepped down, because the sums are identical either way and a HUD digit
     * that flickered with the thermal governor would be worse than useless.
     */
    override fun readout(kit: SceneKit): String? {
        if (!built || volTotal <= 1e-5f) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val sA = ((c - S1_AT) / SWEEP_LEN).coerceIn(0f, 1f)
        val sB = ((c - S2_AT) / SWEEP_LEN).coerceIn(0f, 1f)
        val a = harvested(rowVol8, N0, sA)
        val b = harvested(colVol8, N0, sB)
        val tail = if (sB >= 1f) "   SAME" else ""
        return "ROWS %.1f   COLS %.1f%s".format(Locale.US, a, b, tail)
    }

    // ------------------------------------------------------------------ pieces

    /**
     * One sweep: the travelling plane, the strip of columns standing under it, and the profile
     * along that strip. The filled part of the plane is exactly the strip's cross-section, not the
     * whole rectangle — the bright area IS the inner integral, and a plane lit edge to edge would
     * be a curtain that happens to be moving instead.
     */
    private fun sweep(
        line: FloatArray, lv: Int, tri: FloatArray,
        alongX: Boolean, s: Float, nn: Int, hh: FloatArray, cell: Float,
        x0: Float, z0: Float, baseY: Float, col: FloatArray, q: Int
    ): Int {
        var k = lv
        val span = 2f * HALF
        val p = s * span
        val idx = (s * nn).toInt().coerceIn(0, nn - 1)
        val topY = baseY + tall + 0.45f
        val t0edge = if (alongX) z0 else x0

        // The plane's own rectangle, faint: the unharvested part of the cut has to be visible or
        // the bright strip has nothing to be a fraction of.
        if (q < 2) {
            val ax = if (alongX) x0 + p else x0
            val az = if (alongX) z0 else z0 + p
            val bx = if (alongX) x0 + p else x0 + span
            val bz = if (alongX) z0 + span else z0 + p
            k = MathMesh.segment(line, k, ax, baseY, az, bx, baseY, bz, col[0], col[1], col[2], 0.30f)
            k = MathMesh.segment(line, k, ax, topY, az, bx, topY, bz, col[0], col[1], col[2], 0.30f)
            k = MathMesh.segment(line, k, ax, baseY, az, ax, topY, az, col[0], col[1], col[2], 0.30f)
            k = MathMesh.segment(line, k, bx, baseY, bz, bx, topY, bz, col[0], col[1], col[2], 0.30f)
        }

        var prev = 0f
        for (m in 0 until nn) {
            val h = if (alongX) hh[m * nn + idx] else hh[idx * nn + m]
            val t = t0edge + m * cell
            val ax = if (alongX) x0 + p else t
            val az = if (alongX) t else z0 + p
            val ux = if (alongX) 0f else cell
            val uz = if (alongX) cell else 0f
            tv[0] = MathMesh.quad(
                tri, tv[0], ax, baseY, az, ux, 0f, uz, 0f, h, 0f,
                col[0], col[1], col[2], 0.42f
            )
            if (q < 2) {
                // The stepped profile, which is the same steps the columns have. The area under it
                // is not an approximation OF the harvest, it is the harvest.
                k = MathMesh.segment(
                    line, k, ax, baseY + h, az, ax + ux, baseY + h, az + uz,
                    col[0], col[1], col[2], 0.95f
                )
                k = MathMesh.segment(
                    line, k, ax, baseY + prev, az, ax, baseY + h, az,
                    col[0], col[1], col[2], 0.75f
                )
            }
            prev = h
        }
        if (q < 2) {
            val te = t0edge + nn * cell
            val ax = if (alongX) x0 + p else te
            val az = if (alongX) te else z0 + p
            k = MathMesh.segment(line, k, ax, baseY, az, ax, baseY + prev, az, col[0], col[1], col[2], 0.75f)
        }
        return k
    }

    /**
     * One bar, in the rail's own plane so it is face-on for the whole approach. Filled slab by
     * slab with a division line at every completed strip, so the bar is not a length but a
     * visible sum of the individual contributions that made it.
     */
    private fun bar(
        line: FloatArray, lv: Int, tri: FloatArray,
        sMid: Float, vols: FloatArray, nn: Int, vol: Float, col: FloatArray, alpha: Float
    ): Int {
        var k = lv
        val hTop = (vol / volTotal) * BAR_MAX
        if (hTop < 1e-3f || alpha < 0.02f) return k
        val face = BAR_AHEAD - BAR_DEPTH * 0.5f
        var cum = 0f
        for (m in 0 until nn) {
            val lo = (cum / volTotal) * BAR_MAX
            if (lo >= hTop) break
            cum += vols[m]
            var hi = (cum / volTotal) * BAR_MAX
            val done = hi < hTop - 1e-4f
            if (hi > hTop) hi = hTop
            SceneParts.at(g, sMid - BAR_W * 0.5f, BAR_FOOT + lo, face, o)
            SceneParts.vec(g, BAR_W, 0f, 0f, du)
            SceneParts.vec(g, 0f, hi - lo, 0f, dv)
            tv[0] = MathMesh.quad(
                tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                col[0], col[1], col[2], alpha * (if (m and 1 == 0) 0.36f else 0.22f)
            )
            if (done) {
                SceneParts.at(g, sMid - BAR_W * 0.5f, BAR_FOOT + hi, face, o)
                SceneParts.at(g, sMid + BAR_W * 0.5f, BAR_FOOT + hi, face, p2)
                k = MathMesh.segment(
                    line, k, o[0], o[1], o[2], p2[0], p2[1], p2[2],
                    col[0], col[1], col[2], alpha * 0.7f
                )
            }
        }
        SceneParts.at(g, sMid, BAR_FOOT + hTop * 0.5f, BAR_AHEAD, o)
        SceneParts.vec(g, BAR_W * 0.5f, 0f, 0f, du)
        SceneParts.vec(g, 0f, hTop * 0.5f, 0f, dv)
        SceneParts.vec(g, 0f, 0f, BAR_DEPTH * 0.5f, dw)
        return MathMesh.boxEdges(
            line, k, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            dw[0], dw[1], dw[2], col[0], col[1], col[2], alpha
        )
    }

    // ------------------------------------------------------------------ drawing

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No terrain callback, no country to integrate over: this stop is meaningless off Tour V.
        if (!kit.hasTerrain) return
        build(kit, i)
        if (volTotal <= 1e-5f) return

        val q = kit.quality
        val nn = if (q == 0) N0 else N0 / 2
        val hh = if (q == 0) h8 else h4
        val rv = if (q == 0) rowVol8 else rowVol4
        val cv = if (q == 0) colVol8 else colVol4
        val cell = 2f * HALF / nn
        val x0 = cx - HALF
        val z0 = cz - HALF
        val baseY = SceneAmbientCountry.GROUND_Y + datum

        SceneParts.stage(kit, i.toFloat(), BAR_SIDE, 0f, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val sA = ((c - S1_AT) / SWEEP_LEN).coerceIn(0f, 1f)
        val sB = ((c - S2_AT) / SWEEP_LEN).coerceIn(0f, 1f)
        // The gold wake is cleared before the second plane sets off — that is the "reset" the crew
        // calls — and the blue one only at the very end, so the long look is two bars over a field
        // that has visibly just been spent.
        val tintA = if (sA <= 0f) 0f else 1f - SceneParts.step(c, S1_AT + SWEEP_LEN + 0.01f, 0.05f)
        val tintB = if (sB <= 0f) 0f else 1f - SceneParts.step(c, 0.92f, 0.05f)
        val hold = 1f - SceneParts.step(c, 0.96f, 0.04f)
        val gold = SceneParts.WORK
        val blue = SceneParts.COOL
        val plain = SceneParts.STEEL
        val chalk = SceneParts.CHALK

        val volA = harvested(rv, nn, sA)
        val volB = harvested(cv, nn, sB)
        val harvA = sA * nn
        val harvB = sB * nn

        // --- the datum the columns are measured from --------------------------------------------
        v = MathMesh.segment(line, v, x0, baseY, z0, x0 + 2f * HALF, baseY, z0, chalk[0], chalk[1], chalk[2], 0.30f)
        v = MathMesh.segment(line, v, x0, baseY, z0 + 2f * HALF, x0 + 2f * HALF, baseY, z0 + 2f * HALF, chalk[0], chalk[1], chalk[2], 0.30f)
        v = MathMesh.segment(line, v, x0, baseY, z0, x0, baseY, z0 + 2f * HALF, chalk[0], chalk[1], chalk[2], 0.30f)
        v = MathMesh.segment(line, v, x0 + 2f * HALF, baseY, z0, x0 + 2f * HALF, baseY, z0 + 2f * HALF, chalk[0], chalk[1], chalk[2], 0.30f)

        // --- the column field ---------------------------------------------------------------------
        // Filled tops and bare corner posts rather than solid boxes: sixty-four lit solids would be
        // sixty-four draw calls and would hide the country they are standing on, and the stepped
        // roof of filled tops is what carried this picture at stop 9.
        val posts = when (q) { 0 -> 4; 1 -> 2; else -> 0 }
        for (j in 0 until nn) {
            val z = z0 + j * cell
            for (ii in 0 until nn) {
                val x = x0 + ii * cell
                val h = hh[j * nn + ii]
                val top = baseY + h
                // How much of this cell the plane has already crossed, so the wake advances
                // smoothly through a cell instead of snapping a whole strip at a time.
                var mix = 0f
                var tgt = plain
                if (tintA > 0f) {
                    val f2 = (harvA - ii).coerceIn(0f, 1f)
                    if (f2 > 0f) { mix = f2 * tintA; tgt = gold }
                }
                if (tintB > 0f) {
                    val f2 = (harvB - j).coerceIn(0f, 1f)
                    if (f2 > 0f) { mix = f2 * tintB; tgt = blue }
                }
                cc[0] = plain[0] + (tgt[0] - plain[0]) * mix
                cc[1] = plain[1] + (tgt[1] - plain[1]) * mix
                cc[2] = plain[2] + (tgt[2] - plain[2]) * mix
                val a = 0.18f + 0.30f * mix
                tv[0] = MathMesh.quad(
                    tri, tv[0], x, top, z, cell, 0f, 0f, 0f, 0f, cell,
                    cc[0], cc[1], cc[2], a
                )
                val pa = 0.28f + 0.42f * mix
                if (posts == 4) {
                    v = MathMesh.segment(line, v, x, baseY, z, x, top, z, cc[0], cc[1], cc[2], pa * 0.5f, pa)
                    v = MathMesh.segment(line, v, x + cell, baseY, z, x + cell, top, z, cc[0], cc[1], cc[2], pa * 0.5f, pa)
                    v = MathMesh.segment(line, v, x, baseY, z + cell, x, top, z + cell, cc[0], cc[1], cc[2], pa * 0.5f, pa)
                    v = MathMesh.segment(line, v, x + cell, baseY, z + cell, x + cell, top, z + cell, cc[0], cc[1], cc[2], pa * 0.5f, pa)
                } else if (posts == 2) {
                    v = MathMesh.segment(line, v, x, baseY, z, x, top, z, cc[0], cc[1], cc[2], pa * 0.5f, pa)
                    v = MathMesh.segment(line, v, x + cell, baseY, z + cell, x + cell, top, z + cell, cc[0], cc[1], cc[2], pa * 0.5f, pa)
                }
            }
        }

        // --- the travelling planes ------------------------------------------------------------------
        // Never both at once: the point of the second pass is that it is the SAME field again, and
        // two planes on the floor together would be one crossing pattern rather than two orders.
        if (sA > 0f && sA < 1f) {
            v = sweep(line, v, tri, true, sA, nn, hh, cell, x0, z0, baseY, gold, q)
        } else if (sB > 0f && sB < 1f) {
            v = sweep(line, v, tri, false, sB, nn, hh, cell, x0, z0, baseY, blue, q)
        }

        // --- the two bars -----------------------------------------------------------------------------
        v = bar(line, v, tri, S_A, rv, nn, volA, gold, hold)
        v = bar(line, v, tri, S_B, cv, nn, volB, blue, hold)

        // --- the mark the second bar is climbing toward -------------------------------------------------
        // Dashed while it is still climbing, solid the moment it arrives. Watching a bar rise to a
        // line already drawn at the answer is a far stronger claim than being shown two finished
        // bars and told to compare them.
        if (sA >= 1f && hold > 0.02f) {
            SceneParts.at(g, S_A - BAR_W * 0.75f, BAR_FOOT + BAR_MAX, BAR_AHEAD, o)
            SceneParts.at(g, S_B + BAR_W * 0.75f, BAR_FOOT + BAR_MAX, BAR_AHEAD, p2)
            if (sB >= 1f) {
                v = MathMesh.segment(
                    line, v, o[0], o[1], o[2], p2[0], p2[1], p2[2],
                    chalk[0], chalk[1], chalk[2], hold
                )
            } else {
                v = MathMesh.dashed(
                    line, v, o[0], o[1], o[2], p2[0], p2[1], p2[2], 7,
                    chalk[0], chalk[1], chalk[2], hold * 0.55f
                )
            }
        }

        // --- the tether ---------------------------------------------------------------------------------
        // The dial is beside you and the measurement is on the ground; without this the bars are
        // two objects that happen to be nearby.
        if (q == 0) {
            SceneParts.at(g, 0f, BAR_FOOT - 0.12f, BAR_AHEAD, o)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], cx, baseY, cz, 9, chalk[0], chalk[1], chalk[2], 0.22f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // The click as the second bar comes level. One lamp, brief, in the gap between the two
        // tops. If a viewer looks away for the whole of this stop and back for one second, this is
        // the second it has to be, so it is given a light of its own.
        val land = SceneParts.step(c, 0.72f, 0.07f)
        if (sB >= 1f && land > 0.02f && land < 0.99f) {
            SceneParts.at(g, 0f, BAR_FOOT + BAR_MAX, BAR_AHEAD, o)
            val fl = 1f - land
            kit.ball(
                o[0], o[1], o[2], 0.09f, 0.09f, 0.09f, SceneParts.HOT, chalk,
                fl, 0f, 0f, 1f, 0f, 0f, 3f * fl
            )
        }

        // --- notation ---------------------------------------------------------------------------------------
        // Beside each bar on its own flank, never over the tops: the telemetry block owns the sky
        // here and a label at the top of a full bar would be sitting in it. The order of the two
        // differentials is the entire content of this stop, so it is spelt out on the bars rather
        // than left to the HUD.
        val gl = 0.16f
        SceneParts.at(g, S_A - BAR_W * 0.5f - 0.10f, BAR_FOOT + BAR_MAX * 0.62f, BAR_AHEAD, o)
        kit.text("∫∫f dy dx", o[0], o[1], o[2], gl, gold, hold, GlyphBoard.Style.MATH, 1.1f, anchor = 0.5f)
        SceneParts.at(g, S_B + BAR_W * 0.5f + 0.10f, BAR_FOOT + BAR_MAX * 0.62f, BAR_AHEAD, o)
        kit.text("∫∫f dx dy", o[0], o[1], o[2], gl, blue, hold, GlyphBoard.Style.MATH, 1.1f, anchor = -0.5f)

        // Secondary, so quality 0 only: what the travelling plane is actually computing as it
        // goes, hung off the near end of the cut, and the two ground axes named where the two
        // sweeps run out.
        if (q == 0) {
            if (sA > 0f && sA < 1f) {
                kit.text(
                    "∫f dy", x0 + sA * 2f * HALF, baseY + tall * 0.75f, z0 + 2f * HALF + 0.30f,
                    0.20f, gold, 0.95f, GlyphBoard.Style.MATH, 1.1f, anchor = -0.5f
                )
            } else if (sB > 0f && sB < 1f) {
                kit.text(
                    "∫f dx", x0 + 2f * HALF + 0.30f, baseY + tall * 0.75f, z0 + sB * 2f * HALF,
                    0.20f, blue, 0.95f, GlyphBoard.Style.MATH, 1.1f, anchor = -0.5f
                )
            }
            kit.text("x", x0 + 2f * HALF + 0.34f, baseY + 0.20f, cz, 0.18f, chalk, 0.65f, GlyphBoard.Style.MATH)
            kit.text("y", cx, baseY + 0.20f, z0 + 2f * HALF + 0.34f, 0.18f, chalk, 0.65f, GlyphBoard.Style.MATH)
        }
    }
}
