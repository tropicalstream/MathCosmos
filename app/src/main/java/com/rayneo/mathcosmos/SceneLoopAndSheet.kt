package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * TOUR VI, stop 6 — THE LOOP AND THE SHEET. "All the spin inside a patch, added up, is exactly the
 * push around its edge — because everything inside cancels with its neighbour."
 *
 * Green's theorem, shown as its own proof rather than asserted and then illustrated. A flat sheet
 * hangs to port, spanned by a wire loop and tiled into 8 x 8 cells, each with its own little
 * paddle wheel turning in the field. Every cell also carries its OWN circulation: four blades
 * running anticlockwise round its rim. Which means every shared edge inside the sheet carries two
 * blades pointing opposite ways — and those are the same edge, counted twice, once each way.
 *
 * Then the animation that is the entire stop: a front sweeps across the sheet and the internal
 * blades die IN PAIRS. Not one at a time, not fading in place — the two members of a pair slide
 * together onto the edge they share, brighten as they meet, and go out at the same instant. When
 * the front has crossed, nothing is lit but the boundary. The tiles fuse into one region, and the
 * gauge to starboard has not moved a hair through any of it.
 *
 * THE PAIRING IS THE ARGUMENT, so everything is arranged to make a pair unmistakably a pair. Both
 * blades of an internal edge take their cancellation time from the SAME point — the edge's own
 * midpoint — so they can never drift apart by a frame. Each blade is drawn offset into the cell
 * that owns it, and that offset shrinks to zero as it dies, so the two visibly converge rather
 * than merely dimming. And they are coloured by the direction they actually run: teal along the
 * edge's positive sense, red against it. A plus meeting a minus and both going out is a picture
 * every viewer already understands, and here it happens to be a proof.
 *
 * THE WHEELS DO NOT STOP. The spin inside the sheet is still there when the internal bookkeeping
 * is gone; the theorem re-expresses a total, it does not remove anything. A version that quieted
 * the wheels as the pairs cancelled looked tidier and said something false.
 *
 * ON THE RATES. The spec asks for wheels "all turning at their own rates", and each wheel's rate
 * here is measured honestly — the circulation of the field round that cell's own rim, from four
 * edge samples, divided by the cell's area. On Tour VI's field that comes out very nearly uniform,
 * because a swirl about the rail plus a steady drift has essentially constant curl, so the wheels
 * do in fact turn at almost the same rate and this comment is the honest place to say so. What
 * varies visibly is PHASE, scattered by cell index — phase carries no information about the field,
 * and it is there only so that sixty-four identical wheels do not read as a printed texture. A
 * field with varying curl would show the variation without a line of this changing.
 *
 * ON THE ORIENTATION. A surface's normal is a free choice, and Green and Stokes only ask that the
 * boundary be traversed to match it by the right-hand rule. So the sheet picks whichever of its
 * two normals makes the enclosed spin positive, and every blade in the scene reverses with it.
 * That is not a fudge to keep the bar above its zero: it is the convention, made once and applied
 * everywhere, and it is why there is no negative reading to explain here.
 *
 * ON THE TWO BARS. The gauge to starboard is two bars side by side rising to one mark. The port
 * bar is the interior sum, divided into as many slices as there are cells; the starboard bar is
 * the boundary sum, divided into as many slices as there are rim edges. The cell divisions fade
 * as the tiles fuse and the rim divisions brighten, so the same height is re-described from one
 * accounting to the other while its top never moves. The two numbers agree to the last decimal
 * shown, and not by luck: they are summed from the SAME edge samples, so the code performs the
 * identical cancellation the picture does. The bar is self-scaled to this patch, so its height
 * carries no absolute meaning — what carries meaning is that it does not move, and that the two
 * readings land on the same mark. The number itself is on the HUD, where a number belongs.
 *
 * SIZE. Tour VI's walls are ghosts at alpha 0.15 and its scenery is the open medium, so the sheet
 * is three units across out at 2.4 to port and the gauge is 1.35 to starboard — a six-unit spread
 * the craft passes between rather than a diagram on a corridor wall. Sheet and gauge sit on
 * opposite flanks for the reason THE COLUMN FIELD puts its own gauge opposite its hero: two labels
 * in the same column of the eye is what that rule exists to prevent. Both are inside the passage
 * radius of 4.0 even so.
 *
 * BUDGET. One flushLines of about 2500 vertices, one flushTris of about 400, one lit bead running
 * the loop, and three labels: six draw calls. Sixty-four paddle wheels as balls would have been
 * sixty-four.
 */
object SceneLoopAndSheet : MathScene {

    /** A three-unit sheet wants to be standing well before the craft is alongside it. */
    override val reach = 1.5f

    // ---- the sheet, in figure coordinates (the stage centre is the origin) ---------------------
    private const val NMAX = 8             // cells each way at quality 0
    private const val N_LOW = 4            // ... and once the governor steps in
    private const val SPAN = 3.0f
    private const val H = SPAN * 0.5f
    private const val SIDE = -2.4f         // the sheet's centre, to port of the rail
    private const val UP = 0.05f
    private const val INSET = 0.10f        // gap between neighbouring cell faces, as a fraction of a cell

    // ---- the blades ----------------------------------------------------------------------------
    private const val BLADE = 0.52f        // arrow length, as a fraction of a cell
    private const val OFFSET = 0.14f       // how far a blade sits off its shared edge, in cells
    private const val HEAD = 0.36f         // a fat head: these are small and the direction is the point
    private const val WHEEL = 0.26f        // wheel radius, as a fraction of a cell
    private const val SPIN = 3.4f          // radians per second per unit of measured curl

    // ---- the clock -------------------------------------------------------------------------------
    // Seven seconds of the whole tiling lit and turning, ten of the cancellation sweeping across,
    // then nine of nothing but the loop — the longest rest in the leg, because the finished state
    // is what the crew talks over and a viewer arrives at any moment. The last second puts the
    // pairs back so the wrap is a restoration rather than a bang.
    private const val PERIOD = 26f
    private const val SWEEP_AT = 0.26f
    private const val SWEEP_LEN = 0.38f
    private const val BAND = 0.15f         // width of the cancellation front, in sheet fractions
    private const val FUSE_AT = 0.66f
    private const val FUSE_LEN = 0.10f
    private const val RESTORE_AT = 0.955f
    private const val RIM_PERIOD = 6.5f    // one lap of the bead round the loop

    // ---- the gauge, also in figure coordinates ------------------------------------------------
    // s = 3.75 is 1.35 to starboard of the rail, given the sheet's centre is 2.4 to port.
    private const val GAUGE_S = 3.75f
    private const val GAUGE_FOOT = -1.35f
    private const val GAUGE_TALL = 2.05f
    private const val GAUGE_W = 0.15f
    private const val GAUGE_GAP = 0.11f    // half the distance between the two bars

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val vv = FloatArray(3)
    private val tv = IntArray(1)

    // ---- what was measured, cached -------------------------------------------------------------
    // The sheet is welded to the rail frame at a fixed stop and Tour VI's field is a function of
    // place alone, so these never change once found. Keyed by stop and by grid, because the
    // governor picks a different grid and therefore a different partition.
    // eh[j][i] is the field's tangential component along +s on the horizontal edge at row-line j,
    // column i, times the edge's length; ev[j][i] the same along +u on the vertical edge at
    // column-line i, row j. Both are stored at the full-grid stride so one pair of arrays serves
    // either quality.
    private val eh = FloatArray((NMAX + 1) * NMAX)
    private val ev = FloatArray(NMAX * (NMAX + 1))
    private val cc = FloatArray(NMAX * NMAX)
    private var builtFor = -1
    private var orient = 1f
    private var interior = 0f
    private var edgeTotal = 0f
    private var cmax = 1f
    private var vscale = 1f
    /** The pairs actually extinguished last frame, so the HUD can never disagree with the picture. */
    private var shownGone = 0
    private var shownPairs = 1

    /** Teal along an edge's positive sense, red against it: a plus and a minus, about to meet. */
    private fun colOf(d: Float): FloatArray = if (d > 0f) SceneParts.ADDED else SceneParts.TAKEN

    /**
     * Sample the field on every cell edge and add up what it says.
     *
     * The two totals are summed from the same samples, so the internal contributions cancel in the
     * arithmetic exactly as they cancel in the picture — which is the whole reason the readout can
     * show two numbers that agree rather than two that nearly do.
     */
    private fun build(kit: SceneKit, i: Int, nn: Int) {
        val key = i * 16 + nn
        if (builtFor == key) return
        val cell = SPAN / nn

        for (j in 0..nn) {
            val u = -H + j * cell
            for (ii in 0 until nn) {
                SceneParts.at(g, -H + (ii + 0.5f) * cell, u, 0f, o)
                kit.fieldAt(o[0], o[1], o[2], vv)
                eh[j * NMAX + ii] = (vv[0] * g[3] + vv[1] * g[4] + vv[2] * g[5]) * cell
            }
        }
        for (j in 0 until nn) {
            val u = -H + (j + 0.5f) * cell
            for (ii in 0..nn) {
                SceneParts.at(g, -H + ii * cell, u, 0f, o)
                kit.fieldAt(o[0], o[1], o[2], vv)
                ev[j * (NMAX + 1) + ii] = (vv[0] * g[6] + vv[1] * g[7] + vv[2] * g[8]) * cell
            }
        }

        // Each cell's own circulation, anticlockwise in (s, u): bottom +s, right +u, top -s, left -u.
        var tot = 0f
        for (j in 0 until nn) {
            for (ii in 0 until nn) {
                val q = eh[j * NMAX + ii] + ev[j * (NMAX + 1) + ii + 1] -
                    eh[(j + 1) * NMAX + ii] - ev[j * (NMAX + 1) + ii]
                cc[j * NMAX + ii] = q
                tot += q
            }
        }

        // Pick the normal that makes the enclosed spin positive, and turn every sample with it so
        // the drawn blades and the stored bookkeeping can never disagree about which way is round.
        orient = if (tot < 0f) -1f else 1f
        if (orient < 0f) {
            for (k in eh.indices) eh[k] = -eh[k]
            for (k in ev.indices) ev[k] = -ev[k]
            for (k in cc.indices) cc[k] = -cc[k]
            tot = -tot
        }
        interior = tot

        var b = 0f
        for (ii in 0 until nn) b += eh[ii] - eh[nn * NMAX + ii]
        for (j in 0 until nn) b += ev[j * (NMAX + 1) + nn] - ev[j * (NMAX + 1)]
        edgeTotal = b

        var m = 1e-4f
        for (j in 0 until nn) for (ii in 0 until nn) {
            val a = abs(cc[j * NMAX + ii]); if (a > m) m = a
        }
        cmax = m
        vscale = GAUGE_TALL / (if (interior > 0.4f) interior else 0.4f)
        shownPairs = 2 * nn * (nn - 1)
        builtFor = key
    }

    /** Two decimal places with a sign, without allocating a formatter. */
    private fun fmt(v: Float): String {
        val cents = round(abs(v) * 100f).toInt()
        val frac = cents % 100
        return (if (v < -0.004f) "-" else "") + (cents / 100) + "." + (if (frac < 10) "0" else "") + frac
    }

    /**
     * How much of the interior bookkeeping is gone, and the total that has not moved while it went.
     * The count is what the draw loop actually extinguished, not a prediction of it.
     */
    override fun readout(kit: SceneKit): String? {
        if (builtFor < 0) return null
        return if (shownGone < shownPairs) "PAIRS GONE $shownGone/$shownPairs   TOTAL " + fmt(interior)
        else "INSIDE " + fmt(interior) + "   EDGE " + fmt(edgeTotal)
    }

    /**
     * One blade: an arrow of [len] running from the edge midpoint ([sm], [um]) along ([ds], [dvu]),
     * pushed [off] sideways along ([os], [ou]) — always into the cell that owns it, which is what
     * makes a shared edge visibly carry two of them.
     */
    private fun blade(
        line: FloatArray, v: Int,
        sm: Float, um: Float, ds: Float, dvu: Float, os: Float, ou: Float,
        len: Float, off: Float, c: FloatArray, a: Float
    ): Int {
        SceneParts.at(g, sm + os * off - ds * len * 0.5f, um + ou * off - dvu * len * 0.5f, 0f, o)
        SceneParts.vec(g, ds * len, dvu * len, 0f, dv)
        // The barbs are asked to lie in the sheet, so the arrow is handed the sheet's normal — the
        // rail's forward, which is what the stage's third vector is.
        return MathMesh.arrow(
            line, v, o[0], o[1], o[2], dv[0], dv[1], dv[2], g[9], g[10], g[11],
            c[0], c[1], c[2], a, HEAD
        )
    }

    /** A point on the wire loop at [t] of one lap, anticlockwise from the bottom-left corner. */
    private fun rimPoint(t: Float, out: FloatArray) {
        val q = ((t % 1f) + 1f) % 1f * 4f
        val k = q.toInt()
        val r = q - k
        when (k) {
            0 -> SceneParts.at(g, -H + r * SPAN, -H, 0f, out)
            1 -> SceneParts.at(g, H, -H + r * SPAN, 0f, out)
            2 -> SceneParts.at(g, H - r * SPAN, H, 0f, out)
            else -> SceneParts.at(g, -H, H - r * SPAN, 0f, out)
        }
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No field, no circulation to add up. Every Tour VI scene has to be able to say this.
        if (!kit.hasField) return

        val q = kit.quality
        val nn = if (q == 0) NMAX else N_LOW
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)
        build(kit, i, nn)

        val cell = SPAN / nn
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0
        val cap = kit.lineCapacity - 160

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val sweep = SceneParts.step(c, SWEEP_AT, SWEEP_LEN)
        val restore = SceneParts.step(c, RESTORE_AT, 1f - RESTORE_AT)
        val fuse = SceneParts.step(c, FUSE_AT, FUSE_LEN) * (1f - restore)
        // The front starts a little before the near edge and finishes a little past the far one, so
        // the first and last pairs get a proper crossing rather than being clipped by the ends.
        val front = sweep * 1.3f - 0.15f

        // ---- the tiles ---------------------------------------------------------------------------
        // Sixty-four separate faces with a gap between them, and the gap closes as the argument
        // finishes: at the end the faces abut exactly and the sheet is one region bounded by one
        // loop. That fusing is the theorem's conclusion drawn as geometry rather than said.
        val inset = cell * INSET * (1f - fuse)
        val face = cell - inset * 2f
        for (j in 0 until nn) {
            for (ii in 0 until nn) {
                val m = abs(cc[j * NMAX + ii]) / cmax
                // A hair further along the rail than the lines, so a coplanar fill cannot fight the
                // blades drawn on top of it while the craft is still approaching.
                SceneParts.at(g, -H + ii * cell + inset, -H + j * cell + inset, 0.008f, o)
                SceneParts.vec(g, face, 0f, 0f, du)
                SceneParts.vec(g, 0f, face, 0f, dv)
                tv[0] = MathMesh.quad(
                    tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                    SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.07f + 0.09f * m
                )
            }
        }

        // ---- the paddle wheels --------------------------------------------------------------------
        // Three spokes and a blade plate on one end of each, which is enough for the eye to see a
        // direction of turn without six plates per wheel costing another six hundred vertices.
        if (q < 2) {
            val spokes = if (q == 0) 3 else 2
            val plates = if (q == 0) 3 else 0
            val wr = cell * WHEEL
            val heroJ = (nn * 3) / 4
            for (j in 0 until nn) {
                for (ii in 0 until nn) {
                    if (v + (spokes + plates) * 2 > cap) break
                    val cs = -H + (ii + 0.5f) * cell
                    val cu = -H + (j + 0.5f) * cell
                    // The measured curl of the cell: its own circulation over its own area.
                    val rate = cc[j * NMAX + ii] / (cell * cell)
                    val ang = rate * SPIN * kit.seconds + ii * 2.399f + j * 1.117f
                    val hero = ii == nn - 1 && j == heroJ
                    val col = if (hero) SceneParts.HOT else SceneParts.WORK
                    val al = if (hero) 0.95f else 0.68f
                    for (k in 0 until spokes) {
                        val th = ang + k * 1.0471976f
                        val ax = cos(th) * wr
                        val au = sin(th) * wr
                        SceneParts.at(g, cs - ax, cu - au, 0f, o)
                        SceneParts.at(g, cs + ax, cu + au, 0f, du)
                        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                            col[0], col[1], col[2], al)
                        if (k >= plates) continue
                        // the plate, square across the spoke's outer end
                        SceneParts.at(g, cs + ax + au * 0.44f, cu + au - ax * 0.44f, 0f, o)
                        SceneParts.at(g, cs + ax - au * 0.44f, cu + au + ax * 0.44f, 0f, du)
                        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                            col[0], col[1], col[2], al * 0.85f)
                    }
                }
            }
        }

        // ---- the blades on every edge ---------------------------------------------------------------
        // Two loops cover the whole tiling. A horizontal edge at row-line j is the BOTTOM of the cell
        // above it (travelled +s) and the TOP of the cell below (travelled -s); a vertical edge at
        // column-line i is the LEFT of the cell to starboard of it (travelled -u) and the RIGHT of the
        // cell to port (travelled +u). At j = 0 or j = nn, and at i = 0 or i = nn, only one of the two
        // exists — and those survivors are exactly the anticlockwise rim.
        val len = cell * BLADE
        val offd = cell * OFFSET
        // The bead runs the way the rim is actually traversed, so the brightening on the wire and
        // the lit bead further down agree about which way round the push goes.
        val raw = SceneParts.cycle(kit.seconds, RIM_PERIOD)
        val bandPos = if (orient > 0f) raw else 1f - raw
        val rimA = 0.55f + 0.42f * fuse
        var gone = 0

        for (j in 0..nn) {
            val um = -H + j * cell
            for (ii in 0 until nn) {
                if (v + 24 > cap) break
                val sm = -H + (ii + 0.5f) * cell
                if (j == 0 || j == nn) {
                    val sg = if (j == 0) orient else -orient
                    val ou = if (j == 0) 1f else -1f
                    val t = if (j == 0) (ii + 0.5f) / (4f * nn)
                    else (2f * nn + (nn - 1 - ii) + 0.5f) / (4f * nn)
                    v = blade(line, v, sm, um, sg, 0f, 0f, ou, len, offd,
                        SceneParts.HOT, (rimA + rimBead(t, bandPos) * fuse).coerceAtMost(1f))
                } else {
                    val w = ((sm + H) / SPAN) * 0.82f + ((um + H) / SPAN) * 0.18f
                    val live = 1f - ((front - w) / BAND).coerceIn(0f, 1f)
                    val a = if (live > restore) live else restore
                    if (a <= 0.02f) { gone++; continue }
                    // A brief brightening as the two meet, so annihilation reads as an event and
                    // not as a fade. Zero at both ends of the crossing, so the loop stays seamless.
                    val fl = 4f * a * (1f - a) * 0.45f
                    v = blade(line, v, sm, um, orient, 0f, 0f, 1f, len * a, offd * a,
                        colOf(orient), 0.85f * a + fl)
                    v = blade(line, v, sm, um, -orient, 0f, 0f, -1f, len * a, offd * a,
                        colOf(-orient), 0.85f * a + fl)
                }
            }
        }
        for (ii in 0..nn) {
            val sm = -H + ii * cell
            for (j in 0 until nn) {
                if (v + 24 > cap) break
                val um = -H + (j + 0.5f) * cell
                if (ii == 0 || ii == nn) {
                    val sg = if (ii == nn) orient else -orient
                    val os = if (ii == nn) -1f else 1f
                    val t = if (ii == nn) (nn + j + 0.5f) / (4f * nn)
                    else (3f * nn + (nn - 1 - j) + 0.5f) / (4f * nn)
                    v = blade(line, v, sm, um, 0f, sg, os, 0f, len, offd,
                        SceneParts.HOT, (rimA + rimBead(t, bandPos) * fuse).coerceAtMost(1f))
                } else {
                    val w = ((sm + H) / SPAN) * 0.82f + ((um + H) / SPAN) * 0.18f
                    val live = 1f - ((front - w) / BAND).coerceIn(0f, 1f)
                    val a = if (live > restore) live else restore
                    if (a <= 0.02f) { gone++; continue }
                    val fl = 4f * a * (1f - a) * 0.45f
                    v = blade(line, v, sm, um, 0f, orient, -1f, 0f, len * a, offd * a,
                        colOf(orient), 0.85f * a + fl)
                    v = blade(line, v, sm, um, 0f, -orient, 1f, 0f, len * a, offd * a,
                        colOf(-orient), 0.85f * a + fl)
                }
            }
        }
        shownGone = gone

        // ---- the wire loop ----------------------------------------------------------------------
        // The rim as an object in its own right, brightening as it becomes the only thing left.
        val hot = SceneParts.HOT
        val ra = 0.45f + 0.5f * fuse
        SceneParts.at(g, -H, -H, 0f, o)
        SceneParts.at(g, H, -H, 0f, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], hot[0], hot[1], hot[2], ra)
        SceneParts.at(g, H, H, 0f, dv)
        v = MathMesh.segment(line, v, du[0], du[1], du[2], dv[0], dv[1], dv[2], hot[0], hot[1], hot[2], ra)
        SceneParts.at(g, -H, H, 0f, du)
        v = MathMesh.segment(line, v, dv[0], dv[1], dv[2], du[0], du[1], du[2], hot[0], hot[1], hot[2], ra)
        v = MathMesh.segment(line, v, du[0], du[1], du[2], o[0], o[1], o[2], hot[0], hot[1], hot[2], ra)

        // ---- the gauge -----------------------------------------------------------------------------
        // Two bars to one mark: the interior sum sliced by cells to port, the boundary sum sliced by
        // rim edges to starboard. Neither top ever moves; only which slicing is lit.
        val steel = SceneParts.STEEL
        val top = GAUGE_FOOT + interior * vscale
        val aS = GAUGE_S - GAUGE_GAP
        val bS = GAUGE_S + GAUGE_GAP
        SceneParts.at(g, GAUGE_S, GAUGE_FOOT - 0.18f, 0f, o)
        SceneParts.at(g, GAUGE_S, top + 0.30f, 0f, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], steel[0], steel[1], steel[2], 0.34f)
        // zero, and the mark the two readings share
        SceneParts.at(g, aS - GAUGE_W, GAUGE_FOOT, 0f, o)
        SceneParts.at(g, bS + GAUGE_W, GAUGE_FOOT, 0f, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], steel[0], steel[1], steel[2], 0.8f)
        SceneParts.at(g, aS - GAUGE_W * 1.4f, top, 0f, o)
        SceneParts.at(g, bS + GAUGE_W * 1.4f, top, 0f, du)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], hot[0], hot[1], hot[2], 0.75f + 0.25f * fuse)

        val teal = SceneParts.ADDED
        SceneParts.at(g, aS - GAUGE_W * 0.5f, GAUGE_FOOT, 0f, o)
        SceneParts.vec(g, GAUGE_W, 0f, 0f, du)
        SceneParts.vec(g, 0f, top - GAUGE_FOOT, 0f, dv)
        tv[0] = MathMesh.quad(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            teal[0], teal[1], teal[2], 0.40f)
        SceneParts.at(g, bS - GAUGE_W * 0.5f, GAUGE_FOOT, 0f, o)
        tv[0] = MathMesh.quad(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            hot[0], hot[1], hot[2], 0.34f)

        if (q == 0) {
            val cells = nn * nn
            val da = 0.30f * (1f - fuse)
            if (da > 0.02f) {
                for (k in 1 until cells) {
                    val y = GAUGE_FOOT + (top - GAUGE_FOOT) * k / cells
                    SceneParts.at(g, aS - GAUGE_W * 0.5f, y, 0f, o)
                    SceneParts.at(g, aS + GAUGE_W * 0.5f, y, 0f, du)
                    v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                        SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], da)
                }
            }
            val edges = 4 * nn
            val ea = 0.20f + 0.55f * fuse
            for (k in 1 until edges) {
                val y = GAUGE_FOOT + (top - GAUGE_FOOT) * k / edges
                SceneParts.at(g, bS - GAUGE_W * 0.5f, y, 0f, o)
                SceneParts.at(g, bS + GAUGE_W * 0.5f, y, 0f, du)
                v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], ea)
            }
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // ---- the push, going round ------------------------------------------------------------------
        // One lit bead on the wire once the interior is gone: the surviving circulation, doing the
        // only thing it was ever doing. It fades in with the fuse and out with the restore, so it
        // never appears while the sheet is still full of blades it would be competing with.
        if (fuse > 0.05f) {
            rimPoint(bandPos, o)
            kit.ball(o[0], o[1], o[2], 0.075f, 0.075f, 0.075f, hot, teal, fuse,
                0f, 0f, 1f, 0f, 0f, 1.4f + 2.4f * kit.beat)
        }

        if (q >= 2) return

        // ---- notation ----------------------------------------------------------------------------
        // The two readings flank the mark they share, one on each side of the gauge, level with it.
        // There is no equals sign in the scene because the mark between them IS the equals sign, and
        // a viewer who reads the layout has read the theorem.
        SceneParts.at(g, aS - GAUGE_W * 1.4f - 0.14f, top, 0f, o)
        kit.text("∫∫_{sheet}", o[0], o[1], o[2], 0.19f, hot, 1f, GlyphBoard.Style.MATH, 1.1f, anchor = 0.5f)
        SceneParts.at(g, bS + GAUGE_W * 1.4f + 0.14f, top, 0f, o)
        kit.text("∫_{loop}", o[0], o[1], o[2], 0.19f, hot, 1f, GlyphBoard.Style.MATH, 1.1f, anchor = -0.5f)

        if (q > 0) return

        // What the wheels are measuring, named beside the sheet on the rail side, level with the one
        // wheel drawn bright — so the symbol has a specific turning object to point at and does not
        // float over the tiling as a caption. Never above or below the sheet: the telemetry block
        // owns the top of the eye and the caption box the bottom.
        val heroU = -H + ((nn * 3) / 4 + 0.5f) * SPAN / nn
        SceneParts.at(g, H + 0.22f, heroU, 0f, o)
        kit.text("(∇×v)·n", o[0], o[1], o[2], 0.17f, SceneParts.WORK, 0.95f,
            GlyphBoard.Style.MATH, 1f, anchor = -0.5f)
    }

    /** The travelling brightening that runs the rim once the loop is all that is left. */
    private fun rimBead(t: Float, pos: Float): Float {
        var d = abs(t - pos)
        if (d > 0.5f) d = 1f - d
        return (1f - d / 0.14f).coerceIn(0f, 1f) * 0.5f
    }
}
