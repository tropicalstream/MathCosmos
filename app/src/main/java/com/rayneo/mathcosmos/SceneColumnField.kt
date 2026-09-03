package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

/**
 * TOUR V, stop 9 — THE COLUMN FIELD. "The volume under a landscape is a floor of columns, and I
 * can fly through them."
 *
 * The double integral, built the only way it is ever honestly built: chop the ground into cells,
 * stand a square column on each one whose height is the function there, and add up what the
 * columns hold. A twelve-unit patch of the country is partitioned, a column rises on every cell to
 * meet the terrain, and the tops of those columns make a stepped version of the surface the
 * ambient is already drawing overhead. Then the grid halves. Twice. The steps shrink, the stepped
 * roof settles onto the real one, and the running volume closes on a number.
 *
 * THE HALVING IS A SPLIT, NOT A REDRAW, and that decision is most of the stop. When the grid
 * refines, each new column starts at its PARENT's height and moves to its own — so one column
 * visibly becomes four, and the four fan apart by exactly the amount the coarse cell was lying by.
 * Clearing the field and rebuilding it at the finer spacing would show the same two pictures and
 * none of the argument; the fanning IS the error being paid off, and it is watchable.
 *
 * SIGNED, AND IT SAYS SO. Half of Tour V's country is below the datum plane where terrainHeight
 * reads zero, and a column over low ground therefore hangs DOWN from the datum rather than
 * standing on it. Those columns are drawn in the tour's colour for a debt and they SUBTRACT from
 * the total, which is why the coarse estimate here starts out negative and climbs. Drawing every
 * column upward from the lowest ground in sight would give a prettier floor and would be
 * measuring a different function. The crew says this out loud; so does the geometry.
 *
 * THE GAUGE IS A HEIGHT, NOT A DIAL. The running volume stands as a bar on the datum beside the
 * craft, zero at the datum, growing upward — so "more volume" is up, in the same direction and
 * against the same floor as the columns being added. Each stage leaves a faint tick behind at the
 * value it reached, and a pale reference line marks a far finer sum of the same patch. Three
 * ticks, each gap a quarter of the one before, converging on that line: that is the whole meaning
 * of the word, and it costs six line segments. The number itself is on the HUD, where a number
 * belongs.
 *
 * The reference line is a 24 x 24 midpoint sum, not the exact integral — nothing here computes an
 * exact integral, and the line is drawn pale precisely because it is one more estimate, just a
 * much better one. Saying otherwise in a tour about approximation would be poor manners.
 *
 * SIZE. This is one of the two tours the size rule was relaxed for. The passage radius here is
 * 4.2 and the wall alpha is 0.18; the patch is twelve world units square, so the craft passes over
 * and among the columns rather than looking at a diagram of them. Trimming the field to the tube
 * would turn the domain of an integral into an ornament on a corridor wall. The craft does not
 * quite thread the trunks — the rail runs above the country everywhere on this leg, and the
 * tallest column tops pass about three quarters of a unit under the keel — so this is a canopy
 * skimmed rather than a wood walked through, and the trunks are lit by nearness for that reason.
 *
 * BUDGET. Two buffer flushes, one lit ball and three labels: about six draw calls. At quality 0
 * the finest stage is 144 columns, which is 144 filled tops in the triangle buffer and 144 top
 * outlines plus their corner trunks in the line buffer — a little over half of each buffer, in one
 * call each. A ball per column would have been 144 draw calls and the same picture.
 */
object SceneColumnField : MathScene {

    /** The field is large and slow to build; it should be standing before the craft is on it. */
    override val reach = 1.6f

    /** Six units of patch each way, against sixteen-unit node spacing. */
    override val deep = 0.45f

    // ---- the patch ---------------------------------------------------------------------------
    private const val SPAN = 12f           // world units of country, both ways, at every stage
    private const val B0 = 3               // coarse grid at quality 0: halves to 6, then 12
    private const val B1 = 2               // and at quality 1: 2, then 4, then 8
    private const val INSET = 0.055f       // gap between neighbouring columns, as a fraction of a cell

    // ---- the clock ---------------------------------------------------------------------------
    // Three stages, each about four seconds of movement and a couple standing, and then nearly
    // nine seconds of the finished fine field doing nothing at all. That last rest is the longest
    // in the tour on purpose: the settled picture — a stepped roof lying on the real surface with
    // the gauge parked against its reference line — is what the crew talks over, and a viewer
    // arriving late has to be able to simply look at it. The whole field then dims out over the
    // last second so that the collapse back to a flat domain happens off-camera; a hundred and
    // forty-four columns dropping to the floor in one frame is a bang, not a loop.
    private const val PERIOD = 28f
    private const val S0 = 0.03f
    private const val S1 = 0.26f
    private const val S2 = 0.50f
    private const val MOVE = 0.08f         // how long one column takes to reach its height
    private const val SPREAD = 0.07f       // raster stagger across the whole field
    private const val CLEAR_AT = 0.96f

    // ---- the gauge ---------------------------------------------------------------------------
    private const val GAUGE_OUT = 1.75f    // world units to one flank of the rail
    private const val GAUGE_W = 0.17f
    private const val GAUGE_TALL = 2.35f   // world height the reference value is scaled to
    private const val HERO_OUT = 2.6f      // the one column that gets named, on the other flank

    // ---- weights -------------------------------------------------------------------------------
    private const val TOP_FILL = 0.20f
    private const val TOP_EDGE = 0.85f
    private const val TRUNK_A = 0.50f
    private const val TRUNK_NEAR = 3.2f    // trunks are full within this of the craft
    private const val TRUNK_FAR = 8.5f     // and gone by this
    private const val RESERVE = 96         // line vertices the columns may not touch

    private val fr = FloatArray(12)
    private val g = FloatArray(12)
    private val hero = FloatArray(3)
    private val tv = IntArray(1)

    // ---- what was measured, cached ---------------------------------------------------------------
    // The patch is welded to a fixed world lattice at a fixed stop, so the three stage totals and
    // the reference never change once found. They are keyed by stop and by coarse grid, because
    // the thermal governor picks a different grid and therefore a different partition.
    private var builtFor = -1
    private val stageVol = FloatArray(3)
    private var refVol = 0f
    private var vscale = 1f
    private var px = 0f                    // patch origin, world x
    private var pz = 0f
    /** The volume of the columns actually drawn last frame — what the HUD reads out. */
    private var shownVol = 0f

    /** Which refinement stage the cycle is in, and how far the current move has got. */
    private fun stageOf(c: Float, maxStage: Int): Int {
        val raw = if (c < S1) 0 else if (c < S2) 1 else 2
        return if (raw > maxStage) maxStage else raw
    }

    private fun startOf(stage: Int): Float = if (stage == 0) S0 else if (stage == 1) S1 else S2

    /** A midpoint Riemann sum over the patch at [n] by [n] cells. No allocation, no state. */
    private fun sum(kit: SceneKit, n: Int): Float {
        val cell = SPAN / n
        var t = 0f
        for (j in 0 until n) {
            val z = pz + (j + 0.5f) * cell
            for (i in 0 until n) t += kit.terrainHeight(px + (i + 0.5f) * cell, z)
        }
        return t * cell * cell
    }

    /**
     * Find the patch and measure it, once. The origin is snapped to whole COARSE cells so that the
     * partition is welded to the ground rather than to the craft, and so the three grids nest
     * exactly — a fine column is always wholly inside one coarse column, which is what lets the
     * refinement read as a split.
     */
    private fun build(kit: SceneKit, i: Int, base: Int, cx: Float, cz: Float) {
        val key = i * 8 + base
        if (builtFor == key) return
        val coarse = SPAN / base
        px = round(cx / coarse) * coarse - SPAN * 0.5f
        pz = round(cz / coarse) * coarse - SPAN * 0.5f
        stageVol[0] = sum(kit, base)
        stageVol[1] = sum(kit, base * 2)
        stageVol[2] = sum(kit, base * 4)
        refVol = sum(kit, 24)
        // The gauge scales itself to whatever this patch happens to hold, so the bar is legible
        // without a constant tuned to one terrain function. The floor stops a nearly flat patch
        // from turning a rounding error into a two-metre bar.
        val mag = if (abs(refVol) > 1.2f) abs(refVol) else 1.2f
        vscale = GAUGE_TALL / mag
        builtFor = key
    }

    /** Two decimal places with a sign, without allocating a formatter. */
    private fun fmt(v: Float): String {
        val cents = round(abs(v) * 100f).toInt()
        val frac = cents % 100
        return (if (v < -0.004f) "-" else "") + (cents / 100) + "." + (if (frac < 10) "0" else "") + frac
    }

    /**
     * The running volume and how finely it was taken.
     *
     * It is the sum of the columns ACTUALLY DRAWN, accumulated in the draw loop rather than
     * recomputed here, so the number on the HUD and the bar in the world can never disagree — and
     * so it rises with the field instead of jumping to the answer while the columns are still
     * climbing. One frame stale at worst, which at thirty frames a second is nothing.
     */
    override fun readout(kit: SceneKit): String? {
        if (builtFor < 0) return null
        val q = kit.quality
        val base = if (q == 0) B0 else B1
        val n = base shl stageOf(SceneParts.cycle(kit.seconds, PERIOD), if (q >= 2) 1 else 2)
        return "VOLUME " + fmt(shownVol) + "   CELLS " + (n * n)
    }

    /** A glyph height that holds its apparent size out across the patch. */
    private fun glyph(kit: SceneKit, x: Float, y: Float, z: Float, want: Float): Float {
        val dx = x - kit.camX; val dy = y - kit.camY; val dz = z - kit.camZ
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        return (want * d / 3.2f).coerceIn(want, want * 2.8f)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No country, no columns to stand on it. Every Tour V scene has to be able to say this.
        if (!kit.hasTerrain) return

        val q = kit.quality
        val base = if (q == 0) B0 else B1
        val gy = SceneAmbientCountry.GROUND_Y

        // --- the frame --------------------------------------------------------------------------
        // The partition runs along world x and z, not along the rail: terrainHeight is a function
        // of world (x, z), and a grid turned to the rail would be a rug thrown at an angle across
        // the domain rather than the domain's own ruling. The rail is used only to place the two
        // things that must sit BESIDE the field — the gauge and the named column — and only its
        // horizontal part, so they stay upright however the rail is climbing.
        SceneParts.stage(kit, i.toFloat(), 0f, 0f, fr, g)
        val cx = g[0]; val cz = g[2]
        var sx = g[3]; var sz = g[5]
        val sl = sqrt(sx * sx + sz * sz)
        if (sl > 1e-4f) { sx /= sl; sz /= sl } else { sx = 1f; sz = 0f }

        build(kit, i, base, cx, cz)

        // The gauge goes on whichever flank has the LOWER ground, so it stands clear of the tall
        // columns instead of being swallowed by them. Both flanks are fixed points at a fixed
        // stop, so this is decided once and never flickers.
        val gside = if (kit.terrainHeight(cx + sx * GAUGE_OUT, cz + sz * GAUGE_OUT) <
            kit.terrainHeight(cx - sx * GAUGE_OUT, cz - sz * GAUGE_OUT)) 1f else -1f

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val maxStage = if (q >= 2) 1 else 2
        val stage = stageOf(c, maxStage)
        val start = startOf(stage)
        val nn = base shl stage
        val cell = SPAN / nn
        val parentCell = cell * 2f
        val inset = cell * INSET
        // The split flash: the outlines brighten for the length of the move, so the eye is taken
        // to the columns fanning apart rather than having to find them.
        val flash = 1f - SceneParts.step(c, start, MOVE + SPREAD)
        // The survey clears before it is taken again, so nobody sees the field fall over.
        val clear = 1f - SceneParts.step(c, CLEAR_AT, 1f - CLEAR_AT)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0
        val cap = kit.lineCapacity - RESERVE

        // --- the partition on the datum -----------------------------------------------------------
        // The domain, ruled at the current spacing. This is the dA of the integral and it is drawn
        // on the flat plane where the country reads zero, under everything: places below, values
        // above, exactly the arrangement stop 1 set up. It halves with the columns, which is the
        // cheapest possible statement of what "halve the grid" means.
        if (q < 2) {
            val ch = SceneParts.CHALK
            val x1 = px + SPAN; val z1 = pz + SPAN
            for (k in 0..nn) {
                val t = k * cell
                val rim = k == 0 || k == nn
                val a = if (rim) 0.45f else 0.13f
                v = MathMesh.segment(line, v, px, gy, pz + t, x1, gy, pz + t, ch[0], ch[1], ch[2], a)
                v = MathMesh.segment(line, v, px + t, gy, pz, px + t, gy, z1, ch[0], ch[1], ch[2], a)
            }
        }

        // --- the hero cell ---------------------------------------------------------------------
        // One column is named, on the flank away from the gauge, so the tour's two labels are never
        // in the same column of the eye. It is picked by place rather than by "nearest the craft":
        // a hero that changes cell as the craft moves would move its own label, and the nearest
        // column is usually the one directly under the keel and out of frame.
        val hx = cx - sx * gside * HERO_OUT
        val hz = cz - sz * gside * HERO_OUT
        val hi = ((hx - px) / cell).toInt().coerceIn(0, nn - 1)
        val hj = ((hz - pz) / cell).toInt().coerceIn(0, nn - 1)
        var heroTop = gy
        var heroFound = false

        // --- the columns -------------------------------------------------------------------------
        val warm = SceneParts.WORK
        val cold = SceneParts.TAKEN
        val total = nn * nn
        var vol = 0f
        for (j in 0 until nn) {
            val z0 = pz + j * cell
            val zm = z0 + cell * 0.5f
            for (ii in 0 until nn) {
                val x0 = px + ii * cell
                val xm = x0 + cell * 0.5f
                val h = kit.terrainHeight(xm, zm)

                // Where the column starts its move. At the first stage that is the datum — the
                // column rises out of the flat domain. At every later stage it is the height its
                // PARENT was standing at, so a coarse column splits into four that then fan to
                // their own heights: the refinement is drawn as a correction, which is what it is.
                val key = (j * nn + ii).toFloat() / (total - 1)
                val grow = SceneParts.step(c, start + key * SPREAD, MOVE)
                val h0 = if (stage == 0) 0f
                else if (grow > 0.999f) h
                else kit.terrainHeight(
                    px + ((ii shr 1) + 0.5f) * parentCell,
                    pz + ((j shr 1) + 0.5f) * parentCell
                )
                val hn = h0 + (h - h0) * grow
                val yTop = gy + hn
                vol += hn

                val up = hn >= 0f
                val col = if (up) warm else cold
                val isHero = ii == hi && j == hj
                if (isHero) { hero[0] = xm; hero[1] = yTop; hero[2] = zm; heroTop = yTop; heroFound = true }

                // The top face: the one thing that has to read as a surface, so it is filled. The
                // inset gap is what keeps a hundred and forty-four of them looking like a floor of
                // separate columns rather than one continuous stepped sheet — and it is the gap the
                // craft is nominally flying between.
                tv[0] = MathMesh.quad(
                    tri, tv[0],
                    x0 + inset, yTop, z0 + inset,
                    cell - inset * 2f, 0f, 0f,
                    0f, 0f, cell - inset * 2f,
                    col[0], col[1], col[2], TOP_FILL * clear * (if (isHero) 2.2f else 1f)
                )

                if (v + 8 > cap) continue
                val ea = (TOP_EDGE * clear * (0.6f + 0.4f * flash) * (if (isHero) 1.15f else 1f)).coerceAtMost(1f)
                val ax = x0 + inset; val bx = x0 + cell - inset
                val az = z0 + inset; val bz = z0 + cell - inset
                val ec = if (isHero) SceneParts.HOT else col
                v = MathMesh.segment(line, v, ax, yTop, az, bx, yTop, az, ec[0], ec[1], ec[2], ea)
                v = MathMesh.segment(line, v, bx, yTop, az, bx, yTop, bz, ec[0], ec[1], ec[2], ea)
                v = MathMesh.segment(line, v, bx, yTop, bz, ax, yTop, bz, ec[0], ec[1], ec[2], ea)
                v = MathMesh.segment(line, v, ax, yTop, bz, ax, yTop, az, ec[0], ec[1], ec[2], ea)

                // The trunks, lit by nearness. Every column in a twelve-unit patch drawn with four
                // solid corner posts is five hundred and seventy-six vertical lines on a 640 by 480
                // eye, which is a thicket and not a forest; fading them out with distance leaves the
                // columns the craft is actually passing among as solid objects and lets the far half
                // of the field be the stepped roof it is meant to be.
                if (q >= 2) continue
                val dx = xm - kit.shipX; val dz = zm - kit.shipZ
                val d = sqrt(dx * dx + dz * dz)
                val ta = TRUNK_A * clear * ((TRUNK_FAR - d) / (TRUNK_FAR - TRUNK_NEAR)).coerceIn(0f, 1f)
                if (ta < 0.04f || v + 8 > cap) continue
                val posts = if (q == 0) 4 else 2
                for (p in 0 until posts) {
                    // At quality 1 only the diagonal pair, which still gives a column a near edge
                    // and a far one and so still reads as a box in stereo.
                    val e = if (posts == 4) p else p * 2
                    val tx = if (e == 0 || e == 3) ax else bx
                    val tz = if (e == 0 || e == 1) az else bz
                    v = MathMesh.segment(line, v, tx, gy, tz, tx, yTop, tz,
                        col[0], col[1], col[2], ta * 0.35f, ta)
                }
            }
        }
        vol *= cell * cell
        shownVol = vol

        // --- the gauge --------------------------------------------------------------------------
        // A bar standing on the datum, zero at the datum, growing upward: more volume is up, in the
        // same direction and off the same floor as the columns being counted. Its face is spanned by
        // the rail's side and world up, so it is square-on to a craft coming up the rail.
        val bx0 = cx + sx * gside * GAUGE_OUT
        val bz0 = cz + sz * gside * GAUGE_OUT
        val steel = SceneParts.STEEL
        val teal = SceneParts.ADDED
        val hot = SceneParts.HOT
        val top = gy + vol * vscale
        // The spine, run to whichever end the bar has reached, plus a little headroom.
        val spineHi = gy + (if (refVol > 0f) refVol else 0f) * vscale + 0.35f
        val spineLo = gy + (if (refVol < 0f) refVol else 0f) * vscale - 0.35f
        v = MathMesh.segment(line, v, bx0, spineLo, bz0, bx0, spineHi, bz0, steel[0], steel[1], steel[2], 0.40f)
        // Zero, on the datum, drawn heavier than the rest: the bar hanging BELOW it at the coarse
        // stage is the negative ground being paid for, and the mark it hangs from must be plain.
        v = MathMesh.segment(line, v,
            bx0 - sx * GAUGE_W, gy, bz0 - sz * GAUGE_W,
            bx0 + sx * GAUGE_W, gy, bz0 + sz * GAUGE_W, steel[0], steel[1], steel[2], 0.85f)
        // The finer sum, as a pale line the stages close on.
        val ry = gy + refVol * vscale
        v = MathMesh.dashed(line, v,
            bx0 - sx * GAUGE_W * 1.5f, ry, bz0 - sz * GAUGE_W * 1.5f,
            bx0 + sx * GAUGE_W * 1.5f, ry, bz0 + sz * GAUGE_W * 1.5f, 3, hot[0], hot[1], hot[2], 0.8f)
        // What each stage reached, left behind. Three ticks crowding toward that line is the whole
        // word "converges", and it is why the ticks outlive the stage that made them.
        for (k in 0..stage) {
            val ty = gy + stageVol[k] * vscale
            val a = if (k == stage) 0.75f else 0.34f
            v = MathMesh.segment(line, v,
                bx0 - sx * GAUGE_W * 1.15f, ty, bz0 - sz * GAUGE_W * 1.15f,
                bx0 + sx * GAUGE_W * 1.15f, ty, bz0 + sz * GAUGE_W * 1.15f, teal[0], teal[1], teal[2], a)
        }
        // The bar itself.
        tv[0] = MathMesh.quad(
            tri, tv[0],
            bx0 - sx * GAUGE_W * 0.5f, gy, bz0 - sz * GAUGE_W * 0.5f,
            sx * GAUGE_W, 0f, sz * GAUGE_W,
            0f, top - gy, 0f,
            teal[0], teal[1], teal[2], 0.42f * clear
        )

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the sample point --------------------------------------------------------------------
        // One lit ball on the named column's top face, at the cell's MIDPOINT — the single place
        // where the function was asked for a number, and the reason that column is the height it
        // is. It pulses with the cue because the counting is what the bed is ticking.
        if (heroFound && clear > 0.05f) {
            kit.ball(
                hero[0], hero[1], hero[2], 0.10f, 0.10f, 0.10f, hot, warm, clear,
                0f, 0f, 1f, 0f, 0f, 1.2f + 2.2f * kit.beat
            )
        }

        if (q >= 2) return

        // --- notation ------------------------------------------------------------------------------
        // Two claims, on opposite flanks, each level with and beside the geometry it names, never
        // stacked over or under it — the telemetry block owns the top of the eye and the caption
        // box the bottom. The gauge is the whole integral; the named column is one term of the sum
        // it is made of. Everything numeric is on the HUD.
        //
        // The integral sign sits level with the REFERENCE line rather than at the top of the
        // spine: that height is the value the whole instrument is converging on, so the symbol
        // names the mark it belongs to, and it lands near the middle of the eye instead of riding
        // up under the telemetry.
        val gl = glyph(kit, bx0, ry, bz0, 0.22f)
        val gw = kit.textWidth("∫∫ f dA", gl) * 0.5f + 0.18f
        kit.text("∫∫ f dA", bx0 + sx * gside * gw, ry, bz0 + sz * gside * gw, gl, hot, 1f)

        if (heroFound) {
            val hl = glyph(kit, hero[0], heroTop, hero[2], 0.20f)
            val hw = kit.textWidth("f ΔA", hl) * 0.5f + 0.16f
            kit.text("f ΔA", hero[0] - sx * gside * hw, heroTop + hl * 0.55f, hero[2] - sz * gside * hw,
                hl, warm, clear)
        }

        if (q > 0) return

        // The cell, named down on the datum where the cells are, so the two halves of a column's
        // volume — a height and a patch of ground — are labelled in the two places they live.
        // It is walked a cell along the rail as well as out, because it and the column's own label
        // are on the same flank and three units apart vertically, and two labels in one column of
        // the eye is the thing this app's layout rule exists to prevent. The along-rail direction
        // is taken as the horizontal perpendicular of the side vector rather than from the frame's
        // forward, which is the same line and saves normalising a second vector.
        val cx0 = px + (hi + 0.5f) * cell
        val cz0 = pz + (hj + 0.5f) * cell
        val dl = glyph(kit, cx0, gy, cz0, 0.16f)
        val dw = kit.textWidth("ΔA", dl) * 0.5f + cell * 0.5f + 0.14f
        kit.text("ΔA",
            cx0 - sx * gside * dw - sz * cell * 0.6f, gy + 0.10f,
            cz0 - sz * gside * dw + sx * cell * 0.6f, dl,
            SceneParts.CHALK, 0.85f, GlyphBoard.Style.MATH)
    }
}
