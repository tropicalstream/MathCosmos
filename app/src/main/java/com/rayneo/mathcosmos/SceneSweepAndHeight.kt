package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Stop 4 — THE SWEEP AND THE HEIGHT. "The speed my total grows is exactly the height of the curve
 * above me."
 *
 * The Fundamental Theorem, and the flagship of the tour. The whole stop is one claim — that two
 * quantities measured in different places, in what look like different units, are the same number —
 * and the only way to make a claim like that land is to measure it repeatedly and let the viewer
 * get bored of being right. So the craft plants ELEVEN matched pairs down the leg and leaves them
 * standing: a rod cut to the roof height f(x), and beside it a needle cut to the tilt the floor
 * ribbon A(x) has at that same place. A short bridge joins their two tops. Eleven bridges, all
 * level, receding down the passage. Nobody says the theorem out loud in the geometry; the
 * colonnade says it.
 *
 * What is drawn here and what is not. The roof curve and the wake sheet behind the craft belong to
 * [SceneAmbientWake] and are never touched by this scene — the wake is the tour's persistent
 * mechanic and drawing a second one would double its alpha. This scene owns the floor ribbon, the
 * eleven pairs, the measuring head, and four labels.
 *
 * Five decisions worth stating, because each of them went the other way first.
 *
 *  - **The ribbon is teal, not gold.** A(x) is the READING of the wake, not the wake. Drawn in the
 *    wake's own amber a metre below it, the two merge into one object and a viewer reasonably
 *    concludes the ribbon is just more wake spilling onto the deck. A different colour costs
 *    nothing and keeps "the stuff" and "the number for the stuff" apart, which is the distinction
 *    the whole stop turns on.
 *
 *  - **A is integrated, not looked up.** [aTab] is a cumulative trapezoid of [SceneKit.traceHeight]
 *    across the window, rebuilt every frame from the gate at a. The needle is then a genuine
 *    central difference of that table over a gauge of [GAUGE] nodes. Yes, differentiating an
 *    integral gives the integrand back — that IS the theorem, and it would be dishonest to draw the
 *    agreement by feeding f into both sticks and calling it a measurement. The two sticks come from
 *    two different computations and land on the same length, which is the only version of this
 *    picture worth building.
 *
 *  - **The gauge is 0.14 nodes, not the 10⁻⁴ on the HUD ladder.** A forward difference at 10⁻⁴ of
 *    two floats near five is mostly rounding noise, exactly as THE FIELD OF SLOPES says of its own
 *    ribbon. At 0.14 the difference between the needle and the rod is f″·G²/6, under four
 *    thousandths of a world unit on this leg — a hair over a hundredth of the shortest rod, and
 *    invisible. The bridge is level to within the gauge, which is the truth and not quite the same
 *    sentence as "the bridge is level".
 *
 *  - **The tilt strut on the ribbon is square-paper, and that is a convention.** One unit of x here
 *    is a whole node, sixteen world units, while one unit of A is [ASCALE] of a world unit. A strut
 *    laid honestly along the drawn ribbon would sit under a degree off horizontal everywhere and
 *    read as flat. So the strut's ANGLE is atan(A′) drawn on paper with equal scales — the same
 *    gauge convention THE CHORD introduced in Tour II and THE FIELD OF SLOPES repeated. What IS
 *    eyeballable without any convention is the correlation: the ribbon climbs steeply where the
 *    rods are tall, flattens as they shorten, and goes level at exactly the station where the rod
 *    vanishes.
 *
 *  - **No squeeze.** Tour II's scenes have to copy SceneAmbientTrace's press-against-the-wall so
 *    their needles stand on the ribbon a viewer can see. Tour III's ambient refuses to clamp — the
 *    corridor's roof IS the function — so the rod is cut to raw traceHeight and meets the drawn
 *    roof exactly. The cost is that the first station or two stand where the roof itself grazes the
 *    passage wall around node two. A rod clamped to stay indoors would be a rod cut to a different
 *    number, so it grazes with the roof and the comment says so rather than the code hiding it.
 *
 * The last station sits just past where f crosses the rail, so its rod and needle are both very
 * nearly nothing and the ribbon beside them goes level: the flat spot, arrived at rather than
 * staged. The station after it would be negative, which is THE SIGNED WAKE's subject two stops on;
 * a rod that has gone below the rail is drawn in the debt colour and left to speak for itself.
 */
object SceneSweepAndHeight : MathScene {

    // The ribbon runs most of a leg. It must fade up early and must not be culled at its own node
    // while two thirds of the colonnade is still ahead of the craft.
    override val reach = 2.0f
    override val deep = 2.6f

    // ---- the stretch of corridor this stop owns, in node units --------------
    private const val BACK = 1.2f          // the gate at a sits this far behind the stop
    private const val FWD = 2.4f           // and the record runs this far ahead
    private const val NODES = 6            // rail frames cached: floor(p0) .. +5

    // ---- the floor ribbon ---------------------------------------------------
    private const val FLOOR_U = -1.95f     // where A = 0 hangs, below the rail
    private const val ASCALE = 0.18f       // world units per unit of A
    private const val RIB_W = 0.16f        // half-width of the strip, across the corridor
    private const val GAUGE = 0.14f        // half-width of the difference the needle is read over
    private const val TILT_LEN = 0.30f     // half-length of the square-paper tilt strut

    // ---- the colonnade ------------------------------------------------------
    private const val STATIONS = 11        // "they measure it eleven times in a row"
    private const val ST_BACK = 0.6f       // the first pair stands this far behind the stop
    private const val ROD_S = -1.05f       // the rod's side offset — port, clear of the wake sheet
    private const val NEEDLE_S = -1.42f    // and the needle's, one jaw's width outboard of it
    private const val SLIVER = 0.09f       // the slab the wake gains at the head, in node units

    private const val PERIOD = 24f
    private const val NA = 64              // cumulative-integral table resolution at full detail

    // ---- scratch. Nothing below allocates, and nothing survives a frame -----
    private val frames = FloatArray(NODES * 12)
    private val fTmp = FloatArray(12)
    private val fr = FloatArray(12)        // the rail frame interpolated at some p
    private val aTab = FloatArray(NA + 1)
    private var aN = NA
    private var aP0 = 0f
    private var aStep = 1f
    private var aP1 = 1f
    private var base = 0
    private val sA = FloatArray(3)         // the pair: rod foot, rod tip, needle foot, needle tip
    private val sB = FloatArray(3)
    private val sC = FloatArray(3)
    private val sD = FloatArray(3)
    private val e0L = FloatArray(3)        // the ribbon strip's four corners, step by step
    private val e0R = FloatArray(3)
    private val e1L = FloatArray(3)
    private val e1R = FloatArray(3)
    private val hA = FloatArray(3)         // the head: rail and roof at each end of the sliver
    private val hB = FloatArray(3)
    private val hC = FloatArray(3)
    private val hD = FloatArray(3)
    private val o = FloatArray(3)

    // ---------------------------------------------------------------- the clock

    /** Where the measuring head is, as a fraction of the window. Shared by draw and readout. */
    private fun penAt(c: Float): Float = SceneParts.step(c, 0.05f, 0.43f)

    /** Where the near end has rolled away to. Zero for six sevenths of the loop. */
    private fun tailAt(c: Float): Float = SceneParts.step(c, 0.88f, 0.12f)

    // ------------------------------------------------------------ the function

    /**
     * The running total from the gate up to [p], read off the table.
     *
     * Linear between table entries. A is smooth and the table is fine — sixty-four steps across
     * three and a half nodes — so the interpolation is worth well under a thousandth of a unit,
     * which matters only because [dAt] divides a difference of two of these by a small run.
     */
    private fun aAt(p: Float): Float {
        val t = ((p - aP0) / aStep).coerceIn(0f, aN.toFloat())
        var k = t.toInt()
        if (k >= aN) k = aN - 1
        val u = t - k
        return aTab[k] + (aTab[k + 1] - aTab[k]) * u
    }

    /**
     * A′ at [p]: rise over run on the drawn ribbon, over a gauge of ±[GAUGE] nodes.
     *
     * Clamped to the window at both ends rather than allowed to reach past the gate, and the run is
     * recomputed from the clamp, so the first and last pairs read a narrower gauge instead of
     * reading a stretch of ribbon that is not there.
     */
    private fun dAt(p: Float): Float {
        val lo = (p - GAUGE).coerceAtLeast(aP0)
        val hi = (p + GAUGE).coerceAtMost(aP1)
        val run = hi - lo
        if (run < 1e-4f) return 0f
        return (aAt(hi) - aAt(lo)) / run
    }

    /** Rising gold, fallen-through-the-rail red. The sign is next stop's subject; here it is a hint. */
    private fun pairCol(h: Float): FloatArray = if (h >= 0f) SceneParts.HOT else SceneParts.TAKEN

    // ---------------------------------------------------------------- the rail

    /**
     * The rail frame at [p], blended from the cached whole-node frames into [fr].
     *
     * A single stage frame will not do for a figure three and a half nodes long: the rail swings
     * something like two and a half units sideways per leg, so a colonnade built from one frame
     * would be most of a metre inside the wall by the far end. Six frames cached at the top of
     * draw and linearly blended, which is what the ambient does with the same rail.
     */
    private fun frameAt(p: Float) {
        val t = (p - base).coerceIn(0f, (NODES - 1).toFloat())
        var k = t.toInt()
        if (k > NODES - 2) k = NODES - 2
        val u = t - k
        val a = k * 12
        val b = a + 12
        for (j in 0 until 12) fr[j] = frames[a + j] + (frames[b + j] - frames[a + j]) * u
    }

    /** A point [side] across the current frame and [up] above its rail centre. */
    private fun pt(side: Float, up: Float, out: FloatArray) {
        out[0] = fr[0] + fr[6] * side + fr[9] * up
        out[1] = fr[1] + fr[7] * side + fr[10] * up
        out[2] = fr[2] + fr[8] * side + fr[11] * up
    }

    // --------------------------------------------------------------- the pieces

    /**
     * One step of the ribbon strip, as a trapezoid between two pairs of edge points.
     *
     * [MathMesh.quad] spans a parallelogram and the two ends of a climbing ribbon are at different
     * heights, so this is written out. The alpha is carried per end so the strip can fade in behind
     * the rolling tail instead of starting at a cliff.
     */
    private fun strip(
        tri: FloatArray, v: Int,
        l0: FloatArray, r0: FloatArray, l1: FloatArray, r1: FloatArray,
        c: FloatArray, a0: Float, a1: Float
    ): Int {
        if ((v + 6) * MathMesh.STRIDE > tri.size) return v
        var k = MathMesh.vertex(tri, v, l0[0], l0[1], l0[2], c[0], c[1], c[2], a0)
        k = MathMesh.vertex(tri, k, r0[0], r0[1], r0[2], c[0], c[1], c[2], a0)
        k = MathMesh.vertex(tri, k, r1[0], r1[1], r1[2], c[0], c[1], c[2], a1)
        k = MathMesh.vertex(tri, k, l0[0], l0[1], l0[2], c[0], c[1], c[2], a0)
        k = MathMesh.vertex(tri, k, r1[0], r1[1], r1[2], c[0], c[1], c[2], a1)
        k = MathMesh.vertex(tri, k, l1[0], l1[1], l1[2], c[0], c[1], c[2], a1)
        return k
    }

    /**
     * One matched pair at [p]: the rod cut to the roof, the needle cut to the ribbon's tilt, the
     * bridge across their tops, and the bar joining their feet on the rail.
     *
     * The bridge is the whole scene in two vertices. Everything else here is scaffolding for it —
     * the feet bar so the two sticks read as one instrument rather than two coincidences, the tie
     * out to the roof so the rod is visibly cut FROM something, the plumb down to the ribbon so the
     * needle is visibly read FROM something.
     */
    private fun station(
        kit: SceneKit, line: FloatArray, v: Int, p: Float,
        grow: Float, alpha: Float, ties: Boolean
    ): Int {
        frameAt(p)
        val h = kit.traceHeight(p) * grow
        val d = dAt(p) * grow
        val c = pairCol(h)
        var k = v

        pt(ROD_S, 0f, sA)
        pt(ROD_S, h, sB)
        pt(NEEDLE_S, 0f, sC)
        pt(NEEDLE_S, d, sD)

        // The two sticks, then the bridge across their tops. Same colour, as the design insists:
        // a viewer who has to work out that two colours mean the same kind of thing has been given
        // a puzzle instead of a measurement.
        k = MathMesh.segment(line, k, sA[0], sA[1], sA[2], sB[0], sB[1], sB[2], c[0], c[1], c[2], alpha)
        k = MathMesh.segment(line, k, sC[0], sC[1], sC[2], sD[0], sD[1], sD[2], c[0], c[1], c[2], alpha)
        k = MathMesh.segment(line, k, sB[0], sB[1], sB[2], sD[0], sD[1], sD[2], c[0], c[1], c[2], alpha)
        k = MathMesh.segment(
            line, k, sA[0], sA[1], sA[2], sC[0], sC[1], sC[2],
            SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], alpha * 0.6f
        )

        if (!ties) return k

        // The tie: rod tip across to the roof it was cut from, at the same height.
        pt(0f, h, o)
        k = MathMesh.dashed(
            line, k, sB[0], sB[1], sB[2], o[0], o[1], o[2], 5,
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], alpha * 0.34f
        )
        // The plumb: needle foot down to the ribbon whose tilt it is carrying.
        pt(0f, FLOOR_U + aAt(p) * ASCALE, o)
        k = MathMesh.dashed(
            line, k, sC[0], sC[1], sC[2], o[0], o[1], o[2], 6,
            SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], alpha * 0.30f
        )
        return k
    }

    // --------------------------------------------------------------- the readout

    /** Two decimals without a formatter, so the HUD line costs one string and not a Locale lookup. */
    private fun two(v: Float): String {
        val t = (abs(v) * 100f + 0.5f).toInt()
        val sign = if (v < 0f && t > 0) "-" else ""
        val frac = t % 100
        return "$sign${t / 100}.${if (frac < 10) "0" else ""}$frac"
    }

    /**
     * The three numbers, and the second and third of them are the point: f and A′ print the same
     * two digits, frame after frame, all the way down the leg.
     *
     * Integrated here from scratch rather than out of [aTab], because readout is called whether or
     * not draw ran this frame and a HUD line that depends on a table someone else filled is a HUD
     * line that eventually reads zero. Midpoint rule both times. The gauge value is the mean height
     * over the gauge window, which is what a central difference of the integral IS; the odd
     * rounding boundary will show a hundredth of daylight between the two, and that hundredth is
     * the finite gauge rather than a flaw in the theorem.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        // The renderer asks the FLOOR stop of the rail, so whenever this line is on screen the
        // craft is between this stop and the next and the floor is our own index.
        val at = floor(kit.progress)
        val p0 = (at - BACK).coerceAtLeast(0f)
        val p1 = (at + FWD).coerceAtMost(kit.stopCount - 1f)
        if (p1 - p0 < 0.5f) return null
        val pen = p0 + (p1 - p0) * penAt(SceneParts.cycle(kit.seconds, PERIOD))

        var a = 0f
        val span = pen - p0
        if (span > 1e-3f) {
            val d = span / 40
            for (k in 0 until 40) a += kit.traceHeight(p0 + (k + 0.5f) * d) * d
        }

        val lo = (pen - GAUGE).coerceAtLeast(p0)
        val hi = (pen + GAUGE).coerceAtMost(p1)
        val run = hi - lo
        var g = 0f
        if (run > 1e-4f) {
            val d = run / 8
            for (k in 0 until 8) g += kit.traceHeight(lo + (k + 0.5f) * d) * d
            g /= run
        }
        return "A ${two(a)}   f ${two(kit.traceHeight(pen))}   A′ ${two(g)}"
    }

    // ------------------------------------------------------------------ the draw

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Tours I, V and VI have no roof curve. With no f there is nothing to accumulate and the
        // ribbon would be a straight bright line along the deck claiming to be a total.
        if (!kit.hasTrace) return

        val q = kit.quality
        val at = i.toFloat()
        val p0 = (at - BACK).coerceAtLeast(0f)
        val p1 = (at + FWD).coerceAtMost(kit.stopCount - 1f)
        if (p1 - p0 < 1f) return

        // Six frame queries for three and a half nodes of corridor. Every query on this renderer
        // builds a small object, so the count matters more than it looks.
        base = floor(p0).toInt().coerceIn(0, (kit.stopCount - NODES).coerceAtLeast(0))
        for (k in 0 until NODES) {
            kit.frame((base + k).coerceAtMost(kit.stopCount - 1).toFloat(), fTmp)
            System.arraycopy(fTmp, 0, frames, k * 12, 12)
        }

        // --- the running total, integrated from the gate ------------------------------------
        aP0 = p0
        aP1 = p1
        aN = if (q == 0) NA else if (q == 1) 32 else 24
        aStep = (p1 - p0) / aN
        aTab[0] = 0f
        var acc = 0f
        var prevH = kit.traceHeight(p0)
        for (k in 1..aN) {
            val h = kit.traceHeight(p0 + aStep * k)
            acc += (prevH + h) * 0.5f * aStep
            aTab[k] = acc
            prevH = h
        }

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val pen = p0 + (p1 - p0) * penAt(c)
        val tail = p0 + (p1 - p0) * tailAt(c)

        // --- the baseline the ribbon is measured from -----------------------------------------
        // Drawn across the whole window whether or not the ribbon has reached it. A ribbon whose
        // zero arrives with it is a ribbon with no zero, and the height of A is the only thing on
        // the deck that means anything. Sampled along the rail rather than struck straight.
        val zN = if (q == 0) 20 else 12
        frameAt(p0)
        pt(0f, FLOOR_U, e0L)
        for (k in 1..zN) {
            frameAt(p0 + (p1 - p0) * k / zN)
            pt(0f, FLOOR_U, e1L)
            v = MathMesh.segment(
                line, v, e0L[0], e0L[1], e0L[2], e1L[0], e1L[1], e1L[2],
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.30f
            )
            e0L[0] = e1L[0]; e0L[1] = e1L[1]; e0L[2] = e1L[2]
        }

        // --- the ribbon A(x) ------------------------------------------------------------------
        // A flat strip laid across the deck rather than a curve drawn in a vertical plane: a strip
        // has a surface, and a surface that is climbing looks like it is climbing from any seat in
        // the craft. Its edges carry the bright line; its face carries a low fill, so it reads as
        // something laid down and not as an area under anything. A is a height here, not an area,
        // and a filled band beneath it would quietly promise a second integral.
        val nR = if (q == 0) 44 else if (q == 1) 26 else 16
        frameAt(p0)
        pt(-RIB_W, FLOOR_U + aAt(p0) * ASCALE, e0L)
        pt(RIB_W, FLOOR_U + aAt(p0) * ASCALE, e0R)
        var prevA = 0f
        for (k in 1..nR) {
            val p = p0 + (p1 - p0) * k / nR
            val u = FLOOR_U + aAt(p) * ASCALE
            frameAt(p)
            pt(-RIB_W, u, e1L)
            pt(RIB_W, u, e1R)
            // Visible only between the rolling tail and the head, with a soft edge at the tail so
            // the wipe reads as the record rolling away rather than as a scissor cut.
            val a = if (p > pen || p < tail) 0f else ((p - tail) / 0.28f).coerceIn(0f, 1f)
            if (a > 0.01f || prevA > 0.01f) {
                v = MathMesh.segment(
                    line, v, e0L[0], e0L[1], e0L[2], e1L[0], e1L[1], e1L[2],
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], prevA * 0.9f, a * 0.9f
                )
                v = MathMesh.segment(
                    line, v, e0R[0], e0R[1], e0R[2], e1R[0], e1R[1], e1R[2],
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], prevA * 0.9f, a * 0.9f
                )
                if (q < 2) tv = strip(tri, tv, e0L, e0R, e1L, e1R, SceneParts.ADDED, prevA * 0.30f, a * 0.30f)
            }
            e0L[0] = e1L[0]; e0L[1] = e1L[1]; e0L[2] = e1L[2]
            e0R[0] = e1R[0]; e0R[1] = e1R[1]; e0R[2] = e1R[2]
            prevA = a
        }

        // --- the gate at a --------------------------------------------------------------------
        // Where the total is zero and the wake behind the craft is being measured from. A ring in
        // the corridor's cross-section, so it reads as a place on the rail and not a mark on a wall.
        if (q < 2 && tail <= p0 + 0.02f) {
            frameAt(p0)
            pt(0f, FLOOR_U, o)
            v = MathMesh.arc(
                line, v, o[0], o[1], o[2], fr[6], fr[7], fr[8], fr[9], fr[10], fr[11],
                0.20f, 0f, 6.2831855f, 14,
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.75f
            )
        }

        // --- the colonnade ---------------------------------------------------------------------
        // Eleven pairs at full detail, six when the governor bites, spanning the same stretch
        // either way so the picture is the same picture with fewer measurements in it.
        val nSt = if (q == 0) STATIONS else 6
        val s0 = (at - ST_BACK).coerceAtLeast(p0)
        val dSt = (p1 - s0) / (nSt - 1)
        // The lock: once the record is complete and resting, a bright wave runs down the colonnade
        // from the gate to the far end. Eleven bridges lighting in turn is the crew's "and it was
        // the same at the last ring, and the one before" said in geometry instead of in words.
        val wave = (c - 0.56f) / 0.24f * (nSt + 2f) - 1f
        for (k in 0 until nSt) {
            val sp = s0 + dSt * k
            if (sp < tail || sp > pen) continue
            val grow = SceneParts.ease((pen - sp) / 0.20f)
            val boost = (1f - abs(wave - k)).coerceAtLeast(0f)
            // The pair the head has just finished with stays hot for a fifth of a node, so the eye
            // is taken to the measurement while it is being made.
            val fresh = pen - sp < 0.20f
            val alpha = if (fresh) 1f else 0.68f + 0.32f * boost
            v = station(kit, line, v, sp, grow, alpha, q == 0)
        }

        // --- the head: the sliver, and the tilt it produces ---------------------------------------
        // Doc's argument, drawn. Go one sliver of rail forward: the wake gains one slab, as tall as
        // the roof and as thin as the sliver. That gain over that run is a tilt, and the tilt is
        // what the needle is carrying. The slab is filled in the wake's own amber because it IS a
        // piece of wake — the one thing in this scene that belongs to the ambient's quantity.
        //
        // It TRAILS the head rather than leading it, for two reasons. It is the slab just swept,
        // and the wake is by definition what is behind you; and a leading slab would have to be
        // clipped or the sweep stopped short of the last pair, either of which costs the eleventh
        // measurement to buy a decoration.
        frameAt(pen)
        pt(0f, 0f, hA)
        pt(0f, kit.traceHeight(pen), hB)
        val penB = (pen - SLIVER).coerceAtLeast(p0)
        frameAt(penB)
        pt(0f, 0f, hC)
        pt(0f, kit.traceHeight(penB), hD)
        val hot = 0.55f + 0.35f * kit.beat
        if (q < 2) tv = strip(tri, tv, hC, hD, hA, hB, SceneParts.WORK, 0.55f, 0.55f)
        v = MathMesh.segment(
            line, v, hA[0], hA[1], hA[2], hB[0], hB[1], hB[2],
            SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], hot, hot * 0.7f
        )
        v = MathMesh.segment(
            line, v, hC[0], hC[1], hC[2], hD[0], hD[1], hD[2],
            SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], hot, hot * 0.7f
        )

        // The tilt strut, lying on the ribbon at the head. Fixed length, angle atan(A′) — square
        // paper, as the object comment admits, and the same convention as THE FIELD OF SLOPES.
        frameAt(pen)
        val dPen = dAt(pen)
        pt(0f, FLOOR_U + aAt(pen) * ASCALE, o)
        val inv = TILT_LEN / sqrt(1f + dPen * dPen)
        val tx = (fr[3] + fr[9] * dPen) * inv
        val ty = (fr[4] + fr[10] * dPen) * inv
        val tz = (fr[5] + fr[11] * dPen) * inv
        v = MathMesh.segment(
            line, v, o[0] - tx, o[1] - ty, o[2] - tz, o[0] + tx, o[1] + ty, o[2] + tz,
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.95f
        )

        kit.flushLines(v, 2.4f)
        if (tv > 0) kit.flushTris(tv)

        // --- two beads, and they are the only lit geometry in the stop ------------------------
        // The contact on the roof, and the pen laying the ribbon underneath it. Everything else
        // earns its place in a buffer; these two are what the eye follows down the leg.
        val live = 1f - tailAt(c)
        pt(0f, FLOOR_U + aAt(pen) * ASCALE, o)
        kit.ball(
            o[0], o[1], o[2], 0.095f, 0.095f, 0.095f, SceneParts.ADDED, SceneParts.CHALK,
            live, 0f, 0f, 1f, 0f, 0f, 2.2f
        )
        kit.ball(
            hB[0], hB[1], hB[2], 0.075f, 0.075f, 0.075f, SceneParts.HOT, SceneParts.WORK,
            live, 0f, 0f, 1f, 0f, 0f, 1.6f + 1.4f * kit.beat
        )

        // --- notation ---------------------------------------------------------------------------
        // Three names, all of them beside their own object and none of them above or below it: the
        // HUD owns the top of the eye and the caption box the bottom, and a label that drifts into
        // either is a label nobody reads. The two curves are named to starboard, on the empty side
        // of the corridor; the theorem is named to port, outboard of the colonnade where the
        // sticks cannot walk through it.
        // Each name waits for the thing it names. The roof belongs to the ambient and is always
        // there, so f(x) is unconditional; A(x) and the theorem itself only appear once the head
        // has laid ribbon past them, and both go out with the wipe. A label standing over a stretch
        // of empty deck is a label that has to be disbelieved before it can be believed again.
        val lp = (at + 0.55f).coerceAtMost(p1)
        frameAt(lp)
        pt(0.85f, kit.traceHeight(lp), o)
        kit.text("f(x)", o[0], o[1], o[2], 0.24f, SceneParts.CHALK, 1f)
        if (pen >= lp && lp >= tail) {
            pt(0.85f, FLOOR_U + aAt(lp) * ASCALE, o)
            kit.text("A(x)", o[0], o[1], o[2], 0.24f, SceneParts.ADDED, live)
        }

        val tp = (at + 0.9f).coerceAtMost(p1)
        if (pen >= tp && tp >= tail) {
            frameAt(tp)
            pt(-2.10f, kit.traceHeight(tp) * 0.5f + 0.30f, o)
            kit.text("A′ = f", o[0], o[1], o[2], 0.26f, SceneParts.HOT, live)
        }

        if (q == 0 && tail <= p0 + 0.02f) {
            // The gate named once, small, out at the side where it cannot be read as a value on
            // the ribbon.
            frameAt(p0)
            pt(-0.52f, FLOOR_U, o)
            kit.text("a", o[0], o[1], o[2], 0.16f, SceneParts.COOL, 0.85f, GlyphBoard.Style.SMALL)
        }
    }
}
