package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Tour II, stop 8 — THE HANDOFF. "Move twice as fast through the input and everything downstream
 * happens twice as fast."
 *
 * This is the stop the design document itself flagged as the one it was least sure of, and the
 * reason is worth keeping in the file: the first version was a gear train, and a gear train is a
 * METAPHOR for the chain rule rather than the chain rule. The house rule is that a metaphor gets
 * said out loud, and a stop whose entire content is an admitted metaphor has no business in a tour
 * built on seeing the theorem. So there are no gears here. The theorem is made a fact about the
 * ship's own instruments instead: a gate across the corridor, past which the wall's OUTER ruling
 * is stretched to twice its spacing, and three bars that read what that does.
 *
 * The arrangement, and why it is this way round rather than the other:
 *
 *   - The INNER ruling (u) keeps its spacing the whole way. It has to: the roof is a function of
 *     u, and the roof is the one thing the crew promise has not been touched. If u had been ruled
 *     twice as finely past the gate then the roof's rise per u-ring would have HALVED, the product
 *     would have come out unchanged, and the stop would teach nothing.
 *   - The OUTER ruling (x) is what the gate stretches: past it one x-ring spans two u-rings. That
 *     is countable — one inner mark between outer marks before the gate, two after — and counting
 *     it is the whole proof that the multiplier is 2 and not something asserted in a caption.
 *   - So df/du is continuous across the gate and does not flinch, du/dx jumps from 1 to 2, and
 *     df/dx — the floor ribbon — doubles. One thing changed, and it was not the roof.
 *
 * The three bars settle Engineering's question ("added together, or multiplied?") without anyone
 * having to answer it. The bottom bar is not drawn as a number: it is drawn as du/dx COPIES of the
 * middle bar laid end to end, with a divider where one ends and the next begins. Repetition is the
 * operator. And before the gate, where du/dx is 1, the bottom bar is exactly the same length as the
 * middle one — which is what addition would never give you. That is why the pre-gate stretch earns
 * its screen time rather than being a run-up to the interesting half.
 *
 * Two legibility fictions, both stated because the crew state theirs:
 *
 *   - The ribbon's zero is the rail's own height, as stop 7 left it, but the graph is drawn half a
 *     unit to starboard rather than dead on the rail. A graph plotted on the rail is a graph the
 *     craft flies through, and at the closest point of the pass it would be inside the camera. Only
 *     the plane it is drawn in has moved; every height is the number.
 *   - The bars are magnitudes, with the sign carried by colour, the same way THE CHORD colours a
 *     rise that is really a fall. A signed bar would grow leftwards into its own label.
 *
 * The reading head — the bead that walks the leg, pauses short of the gate, crosses, and runs out
 * — exists because the craft crosses this gate exactly once and a viewer arrives at any moment.
 * The head repeats the crossing every twenty-six seconds so the jump is always about to happen.
 */
object SceneHandoff : MathScene {

    // The gate is half a stop short of the node and the ribbon runs a stop and a half each way, so
    // the landmark must be up before the gate is reached and must not be culled at the node while
    // half of its corridor is still ahead.
    override val reach = 1.8f
    override val deep = 1.6f

    // ---- the corridor ------------------------------------------------------
    private const val PERIOD = 26f
    private const val SPAN = 1.5f          // node units of corridor drawn each way
    private const val RING = 0.5f          // node units per ring of the INNER ruling, everywhere
    private const val GATE_OFF = -0.5f     // where the gate sits, relative to the stop's node
    private const val AFTER = 2f           // rings of u per ring of x, past the gate
    private const val STATIONS = 7         // (2 * SPAN / RING) + 1 — every ruling mark is one
    private const val GATE_K = 2           // the station the gate stands on
    private const val EPS = 0.06f          // half-width of the slope stencil, in node units

    // ---- the derivative ribbon ---------------------------------------------
    private const val S = 1.40f            // world units per unit of rise-per-ring
    private const val RIB_SIDE = 0.50f     // pushed off the rail so it is not flown through
    private const val ARC = 0.95f          // half-angle of a ruling mark, radians about starboard
    private const val R_X = 0.74f          // outer ruling radius, as a fraction of the passage
    private const val R_U = 0.56f          // inner ruling radius
    private const val R_GATE = 0.70f

    // ---- the instrument panel ----------------------------------------------
    private const val SIDE = -1.40f        // to port, clear of the ruling, clear of the rail
    private const val UP = 0.35f
    private const val DATUM = -0.30f       // every bar grows rightwards from here
    private const val ROW_A = 0.30f
    private const val ROW_B = -0.02f
    private const val ROW_C = -0.36f
    private const val BAR_H = 0.11f
    private const val CELL = 0.42f         // panel length of one ring of u, for the top row
    private const val MIN_BAR = 0.012f     // a bar at zero is a sliver, not an absence

    // The ambient trace's own squeeze, copied. See [roof].
    private const val CEIL = 0.80f
    private const val SOFT = 0.28f

    // Scratch. Nothing below allocates.
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val fA = FloatArray(12)        // rail frame at the previous station
    private val fB = FloatArray(12)        // rail frame at the current station
    private val fH = FloatArray(12)        // rail frame under the reading head
    private val qa = FloatArray(3)         // carried point on the bright ribbon
    private val qg = FloatArray(3)         // carried point on the ghost ribbon
    private val q0 = FloatArray(3)         // carried point on the ribbon's zero datum
    private val ra = FloatArray(3)
    private val rg = FloatArray(3)
    private val r0 = FloatArray(3)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    /**
     * The roof height at [p] as the ambient ribbon actually DRAWS it, not as the tour's trace
     * function returns it. SceneAmbientTrace presses the curve against the wall where the passage
     * is tighter than the curve; this stop differentiates the roof, so it has to differentiate the
     * roof a viewer can SEE, or the floor ribbon would disagree with the thing overhead it claims
     * to be about. Deliberately duplicated from there and from THE CHORD, which needs it for the
     * same reason; if the squeeze there ever changes, these change with it.
     */
    private fun roof(kit: SceneKit, p: Float): Float {
        val h = kit.traceHeight(p)
        val lid = CEIL * kit.radius(p)
        val m = abs(h)
        val knee = lid - SOFT
        if (m <= knee) return h
        val over = m - knee
        val pressed = if (over >= 2f * SOFT) lid else m - over * over / (4f * SOFT)
        return if (h < 0f) -pressed else pressed
    }

    /**
     * The roof's rise per ring of the INNER ruling — the middle bar, df/du. A central difference
     * over a stencil an eighth of a ring wide: the roof is two sines and the estimate is exact to
     * a fraction of a percent, which is far finer than a bar eleven centimetres tall can show.
     */
    private fun rise(kit: SceneKit, p: Float): Float =
        RING * (roof(kit, p + EPS) - roof(kit, p - EPS)) / (2f * EPS)

    /** Rings of u per ring of x at [p]: one before the gate, two after. The whole of du/dx. */
    private fun rings(at: Float, p: Float): Float = if (p > at + GATE_OFF) AFTER else 1f

    /**
     * Where the reading head is at cycle position [c]: a run up to the gate, a pause just short of
     * it, a quick crossing — which is where the bars snap and the ribbon steps — a run-out, and a
     * long rest at the far end with the doubled state on show. Shared by draw and readout so the
     * HUD's three numbers and the three bars are one reading and cannot drift apart.
     */
    private fun headAt(at: Float, c: Float): Float {
        val p0 = at - SPAN
        val p1 = at + GATE_OFF - 0.03f
        val p2 = at + GATE_OFF + 0.28f
        val p3 = at + SPAN
        return p0 +
            (p1 - p0) * SceneParts.step(c, 0.05f, 0.26f) +
            (p2 - p1) * SceneParts.step(c, 0.40f, 0.08f) +
            (p3 - p2) * SceneParts.step(c, 0.50f, 0.26f)
    }

    /** Zero at both ends of the drawn stretch, so the corridor's furniture is not cut off square. */
    private fun endFade(at: Float, p: Float): Float =
        MathMesh.taper((p - (at - SPAN)) / (2f * SPAN))

    /** A point [side] across the rail and [up] above it, in frame [fr]. */
    private fun place(fr: FloatArray, side: Float, up: Float, out: FloatArray) {
        out[0] = fr[0] + fr[6] * side + fr[9] * up
        out[1] = fr[1] + fr[7] * side + fr[10] * up
        out[2] = fr[2] + fr[8] * side + fr[11] * up
    }

    /**
     * A point [h] above the ribbon's zero, from the two station frames blended by [u]. The rail
     * turns by a couple of degrees over a node, so a linear blend of two frames half a node apart
     * is exact to well under a pixel — and it keeps the whole leg to seven frame queries rather
     * than forty. The height is passed in rather than computed here so that one call to [rise]
     * serves the ribbon, its ghost, its colour and its datum: on this device six sine evaluations
     * a sample times thirty samples times two eyes is not free.
     */
    private fun ribAt(u: Float, h: Float, out: FloatArray) {
        val cx = fA[0] + (fB[0] - fA[0]) * u
        val cy = fA[1] + (fB[1] - fA[1]) * u
        val cz = fA[2] + (fB[2] - fA[2]) * u
        val sx = fA[6] + (fB[6] - fA[6]) * u
        val sy = fA[7] + (fB[7] - fA[7]) * u
        val sz = fA[8] + (fB[8] - fA[8]) * u
        val ux = fA[9] + (fB[9] - fA[9]) * u
        val uy = fA[10] + (fB[10] - fA[10]) * u
        val uz = fA[11] + (fB[11] - fA[11]) * u
        out[0] = cx + sx * RIB_SIDE + ux * h
        out[1] = cy + sy * RIB_SIDE + uy * h
        out[2] = cz + sz * RIB_SIDE + uz * h
    }

    /** One bar of the panel: a translucent body with a bright rim, growing right from the datum. */
    private fun bar(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        s: Float, u: Float, w: Float, col: FloatArray, alpha: Float
    ): Int {
        SceneParts.at(g, s, u, 0f, o)
        SceneParts.vec(g, if (w < MIN_BAR) MIN_BAR else w, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAR_H, 0f, dv)
        return SceneParts.pane(
            kit, line, lv, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], col, alpha
        )
    }

    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        // The renderer asks the floor stop of the rail for its readout, so the craft is between
        // this stop and the next whenever this line is up, and the floor is our own index.
        val at = floor(kit.progress)
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val p = headAt(at, c)
        val a = rings(at, p)
        val b = rise(kit, p)
        return String.format(Locale.US, "du/dx %.0f  ×  df/du %+.2f  =  %+.2f", a, b, a * b)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Tours I, V and VI have no roof. A derivative ribbon under a curve that is not there is a
        // bright straight line along the floor, which is a lie rather than an empty stop.
        if (!kit.hasTrace) return

        val at = i.toFloat()
        val gate = at + GATE_OFF
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val pHead = headAt(at, c)
        val crossing = SceneParts.step(c, 0.40f, 0.08f) * (1f - SceneParts.step(c, 0.52f, 0.12f))

        val q = kit.quality
        val sub = if (q == 0) 5 else if (q == 1) 3 else 2
        val seg = if (q == 0) 5 else 3
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // ---- the corridor: two rulings, the gate, and the ribbon ---------------------------
        // One walk down the leg. Each station is both a ruling mark and a frame the ribbon is hung
        // from, which is why every mark sits on a multiple of RING and the gate does too.
        //
        // The thermal governor takes segments off each mark, and takes the ghost and the notation.
        // It never takes a MARK: the number of inner marks between two outer ones is the quantity
        // the stop asks you to count, and thinning them to save a hundred vertices would quietly
        // change the mathematics.
        for (k in 0 until STATIONS) {
            val p = at - SPAN + k * RING
            kit.frame(p, fB)
            if (k == 0) {
                System.arraycopy(fB, 0, fA, 0, 12)
                val r0h = S * rise(kit, p)
                ribAt(0f, r0h * rings(at, p), qa)
                ribAt(0f, r0h, qg)
                ribAt(0f, 0f, q0)
            } else {
                // The step. The ribbon does not ramp into its new height — it jumps, at the gate,
                // by exactly the height it already had, and that vertical is the stop in one line.
                if (k - 1 == GATE_K) {
                    ribAt(0f, S * AFTER * rise(kit, gate), ra)
                    v = MathMesh.segment(
                        line, v, qa[0], qa[1], qa[2], ra[0], ra[1], ra[2],
                        SceneParts.LAMP[0], SceneParts.LAMP[1], SceneParts.LAMP[2],
                        if (gate <= pHead) 1f else 0.30f
                    )
                    qa[0] = ra[0]; qa[1] = ra[1]; qa[2] = ra[2]
                }
                for (j in 1..sub) {
                    val u = j.toFloat() / sub
                    val pj = p - RING + RING * u
                    val fj = endFade(at, pj)
                    // Dim ahead of the reading head, full behind it: the head writes the ribbon,
                    // the way the derivative ribbon was written at the stop before this one.
                    val a = fj * (if (pj <= pHead) 1f else 0.30f)
                    val hj = S * rise(kit, pj)
                    val col = if (hj >= 0f) SceneParts.ADDED else SceneParts.TAKEN

                    ribAt(u, hj * rings(at, pj), ra)
                    v = MathMesh.segment(
                        line, v, qa[0], qa[1], qa[2], ra[0], ra[1], ra[2],
                        col[0], col[1], col[2], a
                    )
                    qa[0] = ra[0]; qa[1] = ra[1]; qa[2] = ra[2]

                    // The rate this ring WOULD have read had the wall not been re-ruled. Dashed,
                    // faint, and only past the gate: it turns "it doubled" from an event you had
                    // to be watching for into a gap that is open at every ring afterwards.
                    if (q == 0) {
                        ribAt(u, hj, rg)
                        if (pj > gate) v = MathMesh.dashed(
                            line, v, qg[0], qg[1], qg[2], rg[0], rg[1], rg[2], 1,
                            SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], a * 0.55f
                        )
                        qg[0] = rg[0]; qg[1] = rg[1]; qg[2] = rg[2]
                    }

                    // The ribbon's zero, which is the rail's own height — the line stop 7 asked
                    // you to notice the roof going flat over. Decoration once the governor bites.
                    if (q < 2) {
                        ribAt(u, 0f, r0)
                        v = MathMesh.segment(
                            line, v, q0[0], q0[1], q0[2], r0[0], r0[1], r0[2],
                            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], fj * 0.22f
                        )
                        q0[0] = r0[0]; q0[1] = r0[1]; q0[2] = r0[2]
                    }
                }
            }

            // --- the two rulings, as arcs on the starboard wall -------------------------------
            // Both ladders on one wall and one inboard of the other, because the whole reading is
            // a comparison between them: split across the ceiling and the floor they would be two
            // unrelated patterns rather than one ratio.
            val rad = kit.radius(p)
            // The ribbon may taper to nothing at the ends of the drawn stretch; a ruling mark may
            // not. The outermost marks are the ones a viewer counts on the approach, and a mark
            // faded to zero is a mark that is not there.
            val fade = 0.34f + 0.66f * endFade(at, p)
            v = MathMesh.arc(
                line, v, fB[0], fB[1], fB[2], fB[6], fB[7], fB[8], fB[9], fB[10], fB[11],
                R_U * rad, -ARC, ARC, seg,
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], fade * 0.75f
            )
            if (k <= GATE_K || (k - GATE_K) % 2 == 0) {
                v = MathMesh.arc(
                    line, v, fB[0], fB[1], fB[2], fB[6], fB[7], fB[8], fB[9], fB[10], fB[11],
                    R_X * rad, -ARC, ARC, seg,
                    SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], fade
                )
            }

            // --- the gate ---------------------------------------------------------------------
            // A whole ring rather than an arc: it is the one thing here the craft goes THROUGH,
            // and it should read as a threshold from every angle on the approach.
            if (k == GATE_K) {
                v = MathMesh.arc(
                    line, v, fB[0], fB[1], fB[2], fB[6], fB[7], fB[8], fB[9], fB[10], fB[11],
                    R_GATE * rad, -3.1415927f, 3.1415927f, if (q == 0) 18 else 10,
                    SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2],
                    0.75f + 0.25f * crossing
                )
            }

            System.arraycopy(fB, 0, fA, 0, 12)
        }

        // ---- the panel: du/dx, df/du, and the two of them multiplied -----------------------
        SceneParts.stage(kit, at, SIDE, UP, f, g)
        val a = rings(at, pHead)
        val b = rise(kit, pHead)
        val col = if (b >= 0f) SceneParts.ADDED else SceneParts.TAKEN
        val w = S * abs(b)

        // Top row: du/dx, drawn as that many unit CELLS rather than as a length. It is the one
        // quantity on the panel with no units — a ratio of rings to rings — so it gets a scale of
        // its own and cannot be compared in length with the two below it. What it can be compared
        // with is the bottom row, which is that same count of copies of the middle one.
        val cells = a.toInt()
        for (m in 0 until cells) {
            v = bar(kit, line, v, tri, DATUM + m * CELL, ROW_A, CELL, SceneParts.WORK, 0.95f)
        }
        // Middle row: df/du. It does not flinch at the gate, and that is the point of it.
        v = bar(kit, line, v, tri, DATUM, ROW_B, w, col, 0.95f)
        // Bottom row: df/dx, as du/dx copies of the middle bar laid end to end. The repetition is
        // the operator; nothing here says "multiply" and nothing needs to.
        for (m in 0 until cells) {
            v = bar(kit, line, v, tri, DATUM + m * w, ROW_C, w, col, 0.95f)
            if (m > 0) {
                SceneParts.at(g, DATUM + m * w, ROW_C - 0.04f, 0f, o)
                SceneParts.at(g, DATUM + m * w, ROW_C + BAR_H + 0.04f, 0f, du)
                v = MathMesh.segment(
                    line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                    SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.9f
                )
            }
        }
        // The datum the three bars are read from. Without it they are three floating slabs.
        SceneParts.at(g, DATUM, ROW_C - 0.08f, 0f, o)
        SceneParts.at(g, DATUM, ROW_A + BAR_H + 0.08f, 0f, du)
        v = MathMesh.segment(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.45f
        )

        // ---- the reading head ----------------------------------------------------------------
        kit.frame(pHead, fH)
        val hRoof = roof(kit, pHead)
        place(fH, 0f, hRoof, o)
        place(fH, RIB_SIDE, S * a * b, du)
        v = MathMesh.dashed(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], if (q == 0) 9 else 5,
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.55f
        )

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // The ring being read, up on the roof, and the height it puts on the floor. Two balls, and
        // they are the only two objects in the scene with any mass to them.
        kit.ball(
            o[0], o[1], o[2], 0.075f, 0.075f, 0.075f, SceneParts.HOT, SceneParts.WORK,
            1f, glow = 0.8f + 0.5f * kit.beat
        )
        kit.ball(
            du[0], du[1], du[2], 0.085f, 0.085f, 0.085f, col, SceneParts.HOT,
            1f, glow = 0.9f + 1.4f * crossing
        )

        // ---- notation ---------------------------------------------------------------------
        // Beside the bars, never over them. Numbers are on the HUD, where they are legible; these
        // only say which rate each bar is.
        val gl = 0.16f
        SceneParts.at(g, DATUM - 0.06f, ROW_A + BAR_H * 0.5f, 0f, o)
        kit.text("du/dx", o[0], o[1], o[2], gl, SceneParts.WORK, 1f, anchor = 0.5f)
        SceneParts.at(g, DATUM - 0.06f, ROW_B + BAR_H * 0.5f, 0f, o)
        kit.text("df/du", o[0], o[1], o[2], gl, SceneParts.CHALK, 1f, anchor = 0.5f)
        SceneParts.at(g, DATUM - 0.06f, ROW_C + BAR_H * 0.5f, 0f, o)
        kit.text("df/dx", o[0], o[1], o[2], gl, col, 1f, anchor = 0.5f)

        if (q < 2) {
            // What the gate does, hung inboard of the ring on the port side so it is not lost in
            // the ruling it is talking about.
            kit.frame(gate, fH)
            val rg2 = kit.radius(gate)
            place(fH, -0.55f * rg2, 0.34f * rg2, o)
            kit.text("×2", o[0], o[1], o[2], 0.22f, SceneParts.HOT, 0.85f + 0.15f * crossing)
        }

        if (q == 0) {
            // The two ladders named once each, a stop past the gate where they differ most
            // obviously. Inboard of their own arcs, so they read as labels on the wall.
            val pl = at + 0.5f
            kit.frame(pl, fH)
            val rl = kit.radius(pl)
            place(fH, 0.52f * rl, 0.46f * rl, o)
            kit.text("x", o[0], o[1], o[2], 0.20f, SceneParts.WORK, 0.9f)
            place(fH, 0.42f * rl, -0.30f * rl, o)
            kit.text("u", o[0], o[1], o[2], 0.20f, SceneParts.COOL, 0.9f)
        }
    }
}
