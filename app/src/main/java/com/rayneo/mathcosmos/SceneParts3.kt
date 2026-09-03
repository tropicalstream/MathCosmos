package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.round

/**
 * Stop 11 of TOUR III — THE PARTS. "The product rule read backwards: I traded a sweep I couldn't
 * do for one I could."
 *
 * Integration by parts as bookkeeping, not as a formula with a mnemonic attached. A rectangle
 * u by v stands beside the rail. A curve runs corner to corner across its inside, and it divides
 * the rectangle into exactly two pieces and no others: everything UNDER the curve, which is a
 * stack of upright columns of height v and width du — the terms of ∫ v du — and everything LEFT
 * of the curve, which is a stack of flat shelves of width u and height dv — the terms of ∫ u dv.
 * Two pieces, one rectangle. That is the whole identity, and it is a picture before it is algebra.
 *
 * WHY THE CUT IS A STAIRCASE AND NOT THE CURVE. If the two pieces are separated by the smooth
 * curve they are still exactly complementary, and the picture proves nothing you can check. Cut
 * along a staircase that follows the curve through an 8 by 8 grid instead and both pieces become
 * unions of whole cells: 22 cells under, 42 cells left of, 64 in the rectangle. A viewer can COUNT
 * that, in the time it takes the crew to say it, and the sum is exact by construction rather than
 * exact to two decimals. It is the same argument as the grains in Tour I's right angle, and it is
 * the only kind of argument this app makes.
 *
 * The staircase treads sit at the curve's MIDPOINT height in each column, which is why 22 cells is
 * so close to the true 21.33: the midpoint rule's overshoot on the way up cancels its undershoot
 * on the way down. Say it plainly, though — the staircase is an approximation of v = u², each
 * shelf count is within one cell of its integral, and the thing that is EXACT here is not either
 * count but the fact that they add to 64. The trade is exact; the two sums are not, and the crew
 * says so.
 *
 * The curve is the figure's own, v = u², and deliberately not this tour's roof. By parts is a
 * relation between u and v; borrowing the roof would make it look as though the identity depended
 * on which corridor you happened to be flying down, and it does not. The roof is left to the
 * ambient scene, where it belongs.
 *
 * PLACEMENT. The design has the rectangle standing across the corridor. It cannot: a flat figure
 * on the rail is a figure you fly INTO, and at the closest point of the pass a viewer gets one
 * corner in one eye and nothing in the other. So it hangs to one side with its ORIGIN corner
 * outboard and its far corner — the (u, v) corner, the one the design wants the craft parked at —
 * inboard, nearest the rail. That keeps the design's real intention: the corner is the closest
 * thing in the scene as the craft goes by. It is also one of this tour's arm stops (10.05), so the
 * probes are out here, and the corner is what they are offered.
 *
 * The figure sits at a fixed height beside the rail rather than hung from the roof. At this stop
 * the trace is over three units up, and a figure hung from it would spend the whole pass behind
 * the telemetry block at the top of the eye.
 *
 * The two colours are the growing rectangle's own, from II-9: cyan for u dv, amber for v du. These
 * are literally the same two pieces read backwards, and a viewer who saw that stop should recognise
 * them without being told.
 */
object SceneByParts : MathScene {

    // Compact, beside the rail, nothing reaching past its own node — so `deep` stays at zero and
    // only the approach fade is widened, to give the loop time to be caught mid-cycle.
    override val reach = 1.5f

    // ---- the grid the figure is counted in --------------------------------------------------
    private const val N = 8

    // ---- the figure, in world units ----------------------------------------------------------
    private const val W = 1.15f            // the rectangle's width  (u = 1)
    private const val H = 1.02f            // and its height         (v = 1)
    private const val FIG_S = -0.50f       // its origin corner, in the stage's own coordinates
    private const val FIG_U = -0.52f
    private const val SIDE = -0.95f        // and the stage, off to one side of the rail
    private const val UP = 0.08f
    private const val DEPTH = -0.006f      // overlays, a hair back towards the approaching craft

    /** Where the notation column's right edge sits: just outboard of the rectangle's left edge. */
    private const val NOTE_S = FIG_S - 0.11f

    /**
     * How far a line of notation may run outboard before it would be inside the wall. The stop's
     * passage radius is 3.8 and nothing may sit past 0.8 of it, so from NOTE_S there is a little
     * under a unit and a half to play with. The lines are laid out from measured glyph widths
     * rather than guessed ones, and if a set ever comes back wider than expected the height is
     * scaled down to fit instead of the sentence disappearing into the wall — a slightly small
     * identity is legible and half an identity is not.
     */
    private const val NOTE_MAX = 1.35f

    // How far the two shelves separate. Given in figure units, but the vertical one is scaled by
    // the figure's own aspect so the two pieces travel the same WORLD distance apart — otherwise
    // the gap looks wider along one diagonal than the other and the split reads as a shear.
    private const val GAP_S = 0.11f
    private const val GAP_U = GAP_S * (W / H)

    private const val PERIOD = 28f

    /** What "we cannot sweep that one" looks like on a waveguide: present, outlined, dead. */
    private val DARK = floatArrayOf(0.18f, 0.17f, 0.22f, 1f)

    // ---- the staircase, worked out once ------------------------------------------------------
    // cut[k]  — the tread height of column k, in cells.
    // wide[j] — how many cells of row j lie LEFT of the staircase, which is the same as counting
    //           the columns whose tread is at or below that row. The two are complements by
    //           construction: sum(cut) + sum(wide) = N*N however the curve runs, which is why the
    //           rectangle always closes and the identity is not a coincidence of this curve.
    private val cut = IntArray(N)
    private val wide = IntArray(N)
    private var below = 0
    private var above = 0

    // Every string this scene can ever show, built once. Two of them are single numbers, and it
    // would be very easy to write them as "$below" at the point of use — which would allocate a
    // String thirty times a second for the whole pass.
    private val TXT_WHOLE: String
    private val TXT_SPLIT: String
    private val TXT_TRADE: String
    private val TXT_BELOW: String
    private val TXT_ABOVE: String

    init {
        var k = 0
        while (k < N) {
            val mid = (k + 0.5f) / N
            cut[k] = round(mid * mid * N).toInt().coerceIn(0, N)
            below += cut[k]
            k++
        }
        var j = 0
        while (j < N) {
            var m = 0
            var t = 0
            while (t < N) { if (cut[t] <= j) m++; t++ }
            wide[j] = m
            above += m
            j++
        }
        val whole = N * N
        TXT_WHOLE = "uv = $whole CELLS"
        TXT_SPLIT = "$below + $above = $whole"
        TXT_TRADE = "$above = $whole − $below"
        TXT_BELOW = below.toString()
        TXT_ABOVE = above.toString()
    }

    // ---- scratch. Nothing below allocates; the object holds nothing between frames. -----------
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p2 = FloatArray(3)
    private val a3 = FloatArray(3)
    private val b3 = FloatArray(3)
    private val tv = IntArray(1)
    private val mix = FloatArray(4)        // the shelf that dims, lerped towards DARK
    private val rim = FloatArray(4)        // the corner rectangle, lerped towards HOT as it matches

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree with the picture --

    private fun cutInAt(c: Float) = SceneParts.step(c, 0.10f, 0.16f)
    private fun darkAt(c: Float) = SceneParts.step(c, 0.74f, 0.08f)

    /**
     * The counts, which is what this stop is measuring. All three strings are built once, because
     * the geometry never changes — only which of them is true right now does.
     */
    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return when {
            cutInAt(c) < 0.6f -> TXT_WHOLE
            darkAt(c) < 0.6f -> TXT_SPLIT
            else -> TXT_TRADE
        }
    }

    // ---- drawing in figure coordinates: u and v both run 0..1 --------------------------------

    /** One segment, both ends given in figure coordinates. */
    private fun seg(
        line: FloatArray, lv: Int,
        s0: Float, u0: Float, s1: Float, u1: Float,
        c: FloatArray, alpha: Float
    ): Int {
        if (alpha <= 0.01f) return lv
        SceneParts.at(g, FIG_S + s0 * W, FIG_U + u0 * H, DEPTH, o)
        SceneParts.at(g, FIG_S + s1 * W, FIG_U + u1 * H, DEPTH, p2)
        return MathMesh.segment(line, lv, o[0], o[1], o[2], p2[0], p2[1], p2[2], c[0], c[1], c[2], alpha)
    }

    /** One filled cell-bar: a column of the lower shelf, or a shelf of the upper one. */
    private fun bar(
        tri: FloatArray, tvv: Int,
        s0: Float, u0: Float, ws: Float, wu: Float,
        c: FloatArray, alpha: Float
    ): Int {
        if (ws <= 1e-4f || wu <= 1e-4f || alpha <= 0.01f) return tvv
        SceneParts.at(g, FIG_S + s0 * W, FIG_U + u0 * H, 0f, o)
        SceneParts.vec(g, ws * W, 0f, 0f, a3)
        SceneParts.vec(g, 0f, wu * H, 0f, b3)
        return MathMesh.quad(
            tri, tvv, o[0], o[1], o[2], a3[0], a3[1], a3[2], b3[0], b3[1], b3[2],
            c[0], c[1], c[2], alpha
        )
    }

    /**
     * The cut itself, from the origin corner up to the far edge, offset by ([ds], [du]).
     * [grow] sweeps it in from the left the first time: the cut is an EVENT, and a cut that simply
     * fades up is a cut nobody saw being made.
     */
    private fun staircase(
        line: FloatArray, lv0: Int, ds: Float, du: Float,
        c: FloatArray, alpha: Float, grow: Float
    ): Int {
        var lv = lv0
        val inv = 1f / N
        val edge = grow * N
        var prev = 0
        var k = 0
        while (k < N && k < edge) {
            val h = cut[k] * inv
            if (cut[k] != prev) {
                lv = seg(line, lv, ds + k * inv, du + prev * inv, ds + k * inv, du + h, c, alpha)
            }
            val stop = if (edge < k + 1) edge * inv else (k + 1) * inv
            lv = seg(line, lv, ds + k * inv, du + h, ds + stop, du + h, c, alpha)
            prev = cut[k]
            k++
        }
        return lv
    }

    /**
     * Everything under the cut: the columns of ∫ v du. [ruled] draws the internal cell lines, which
     * is what makes the count checkable and the first thing to go when the governor steps in.
     */
    private fun lowerShelf(
        line: FloatArray, lv0: Int, tri: FloatArray, ds: Float, du: Float,
        c: FloatArray, fillA: Float, edgeA: Float, ruled: Boolean, grow: Float
    ): Int {
        var lv = lv0
        val inv = 1f / N
        var k = 0
        while (k < N) {
            if (cut[k] > 0) tv[0] = bar(tri, tv[0], ds + k * inv, du, inv, cut[k] * inv, c, fillA)
            k++
        }
        lv = seg(line, lv, ds, du, ds + 1f, du, c, edgeA)
        lv = seg(line, lv, ds + 1f, du, ds + 1f, du + cut[N - 1] * inv, c, edgeA)
        lv = staircase(line, lv, ds, du, c, edgeA, grow)
        if (ruled) {
            val faint = edgeA * 0.34f
            k = 1
            while (k < N) {
                // The vertical grid line at u = k, up to where the tread on its left stands.
                if (cut[k - 1] > 0) {
                    lv = seg(line, lv, ds + k * inv, du, ds + k * inv, du + cut[k - 1] * inv, c, faint)
                }
                k++
            }
            var j = 1
            while (j < N) {
                // The horizontal at v = j, from where the staircase crosses it out to the far edge.
                if (wide[j] < N) {
                    lv = seg(line, lv, ds + wide[j] * inv, du + j * inv, ds + 1f, du + j * inv, c, faint)
                }
                j++
            }
        }
        return lv
    }

    /** Everything left of the cut: the shelves of ∫ u dv, each one u wide and one cell tall. */
    private fun upperShelf(
        line: FloatArray, lv0: Int, tri: FloatArray, ds: Float, du: Float,
        c: FloatArray, fillA: Float, edgeA: Float, ruled: Boolean, grow: Float
    ): Int {
        var lv = lv0
        val inv = 1f / N
        var j = 0
        while (j < N) {
            if (wide[j] > 0) tv[0] = bar(tri, tv[0], ds, du + j * inv, wide[j] * inv, inv, c, fillA)
            j++
        }
        lv = seg(line, lv, ds, du, ds, du + 1f, c, edgeA)
        lv = seg(line, lv, ds, du + 1f, ds + 1f, du + 1f, c, edgeA)
        lv = seg(line, lv, ds + 1f, du + cut[N - 1] * inv, ds + 1f, du + 1f, c, edgeA)
        lv = staircase(line, lv, ds, du, c, edgeA, grow)
        if (ruled) {
            val faint = edgeA * 0.34f
            var k = 1
            while (k < N) {
                if (cut[k] < N) {
                    lv = seg(line, lv, ds + k * inv, du + cut[k] * inv, ds + k * inv, du + 1f, c, faint)
                }
                k++
            }
            j = 1
            while (j < N) {
                if (wide[j] > 0) {
                    lv = seg(line, lv, ds, du + j * inv, ds + wide[j] * inv, du + j * inv, c, faint)
                }
                j++
            }
        }
        return lv
    }

    /** One right-anchored line of notation, in up to three separately coloured pieces. */
    private fun says(
        kit: SceneKit, u: Float, h: Float, alpha: Float,
        a: String, ca: FloatArray, b: String, cb: FloatArray, d: String, cd: FloatArray
    ) {
        if (alpha <= 0.02f) return
        // A glyph's width is exactly its aspect times its height, so one measurement at the asked-for
        // height gives the fitted height too: shrink by the overrun and every piece shrinks with it.
        val wide0 = kit.textWidth(a, h) + kit.textWidth(d, h) +
            (if (b.isEmpty()) 0f else kit.textWidth(b, h)) + h * 0.30f * (if (b.isEmpty()) 1 else 2)
        val hh = if (wide0 > NOTE_MAX) h * NOTE_MAX / wide0 else h
        val k = hh / h
        val gap = hh * 0.30f
        val wa = kit.textWidth(a, hh)
        val wb = if (b.isEmpty()) 0f else kit.textWidth(b, hh)
        var x = NOTE_S - wide0 * k
        SceneParts.at(g, x, u, DEPTH, o)
        kit.text(a, o[0], o[1], o[2], hh, ca, alpha, anchor = -0.5f)
        x += wa + gap
        if (b.isNotEmpty()) {
            SceneParts.at(g, x, u, DEPTH, o)
            kit.text(b, o[0], o[1], o[2], hh, cb, alpha, anchor = -0.5f)
            x += wb + gap
        }
        SceneParts.at(g, x, u, DEPTH, o)
        kit.text(d, o[0], o[1], o[2], hh, cd, alpha, anchor = -0.5f)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var lv = 0
        tv[0] = 0
        val q = kit.quality
        val inv = 1f / N

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val cutIn = cutInAt(c)                             // the staircase sweeps in
        val open = SceneParts.step(c, 0.32f, 0.14f)        // the two shelves lift apart
        val close = SceneParts.step(c, 0.54f, 0.10f)       // and come back
        val apart = open * (1f - close)
        val seated = SceneParts.step(c, 0.64f, 0.05f)
        val flash = seated * (1f - seated) * 4f            // the corner rectangle agreeing, briefly
        val dark = darkAt(c)                               // one shelf goes out
        val told = SceneParts.step(c, 0.66f, 0.06f)        // the identity is written
        val swap = SceneParts.step(c, 0.82f, 0.06f)        // and rearranged into the trade
        // A short lights-down before the wrap, so the loop restarts as a fade rather than a cut.
        val lights = 1f - SceneParts.step(c, 0.955f, 0.045f)

        val ds = GAP_S * apart
        val du = GAP_U * apart

        // The shelf that is about to be given up keeps its own colour until it is given up, and
        // then keeps its shape. Both matter: a piece that vanished would be a piece we cheated
        // away, and the point of the trade is that it is still sitting there, unsweepable.
        var ci = 0
        while (ci < 3) {
            mix[ci] = SceneParts.ADDED[ci] + (DARK[ci] - SceneParts.ADDED[ci]) * dark
            rim[ci] = SceneParts.CHALK[ci] + (SceneParts.HOT[ci] - SceneParts.CHALK[ci]) * flash
            ci++
        }
        mix[3] = 1f
        rim[3] = 1f

        // --- the rectangle before it is cut -------------------------------------------------
        val whole = (1f - cutIn) * lights
        if (whole > 0.02f) {
            tv[0] = bar(tri, tv[0], 0f, 0f, 1f, 1f, SceneParts.CHALK, 0.14f * whole)
            if (q == 0) {
                var k = 1
                while (k < N) {
                    lv = seg(line, lv, k * inv, 0f, k * inv, 1f, SceneParts.CHALK, 0.20f * whole)
                    lv = seg(line, lv, 0f, k * inv, 1f, k * inv, SceneParts.CHALK, 0.20f * whole)
                    k++
                }
            }
        }

        // --- the two shelves ----------------------------------------------------------------
        val ruled = q == 0
        lv = lowerShelf(
            line, lv, tri, ds, -du, SceneParts.WORK,
            (0.20f + 0.14f * dark) * cutIn * lights, 0.95f * cutIn * lights, ruled, cutIn
        )
        lv = upperShelf(
            line, lv, tri, -ds, du, mix,
            0.20f * (1f - 0.55f * dark) * cutIn * lights,
            0.95f * (1f - 0.40f * dark) * cutIn * lights, ruled, cutIn
        )

        // --- the corner rectangle they are measured against ----------------------------------
        // It never moves and it is never cut. When the shelves seat back into it the rim flares,
        // and that flare is the whole of "their union matches": no bar, no number, just the fact
        // that the two pieces fill the frame they came out of.
        val rimA = (0.30f + 0.60f * flash) * lights
        lv = seg(line, lv, 0f, 0f, 1f, 0f, rim, rimA)
        lv = seg(line, lv, 1f, 0f, 1f, 1f, rim, rimA)
        lv = seg(line, lv, 1f, 1f, 0f, 1f, rim, rimA)
        lv = seg(line, lv, 0f, 1f, 0f, 0f, rim, rimA)

        // --- the curve the staircase is counting towards --------------------------------------
        // It stays with the corner rectangle when the shelves leave, so the pieces are visibly
        // made of cells while the thing they approximate stays whole and where it was.
        if (q < 2) {
            val steps = if (q == 0) 24 else 12
            var t = 0
            var pu = 0f
            var pv = 0f
            while (t < steps) {
                val u1 = (t + 1).toFloat() / steps
                val v1 = u1 * u1
                lv = seg(line, lv, pu, pv, u1, v1, SceneParts.HOT, 0.50f * lights)
                pu = u1; pv = v1
                t++
            }
        }

        kit.flushLines(lv, 2.2f)
        kit.flushTris(tv[0])

        // --- the corner the craft hangs at ------------------------------------------------------
        SceneParts.at(g, FIG_S + W, FIG_U + H, DEPTH, o)
        kit.ball(
            o[0], o[1], o[2], 0.055f, 0.055f, 0.055f,
            SceneParts.HOT, SceneParts.WORK, lights,
            glow = 1.2f + 0.8f * kit.beat, small = true
        )
        // This is an arm stop, so the probes are out. A short strut from the corner in towards the
        // rail: the figure is being held, not merely displayed alongside.
        if (kit.reach > 0.02f && q < 2) {
            SceneParts.at(g, FIG_S + W + 0.34f * kit.reach, FIG_U + H, DEPTH, p2)
            kit.rod(
                o[0], o[1], o[2], p2[0], p2[1], p2[2], 0.013f,
                SceneParts.STEEL, SceneParts.HOT, 0.35f
            )
        }

        // --- what each shelf is worth ------------------------------------------------------------
        // The counts, on the pieces themselves, in the pieces' own colours. That shared colour is
        // the only join between the picture and the identity beside it, and it is enough.
        if (q < 2) {
            val hh = 0.17f
            SceneParts.at(g, FIG_S + (0.80f + ds) * W, FIG_U + (0.14f - du) * H, DEPTH, o)
            kit.text(TXT_BELOW, o[0], o[1], o[2], hh, SceneParts.WORK, cutIn * lights, GlyphBoard.Style.PLAIN)
            SceneParts.at(g, FIG_S + (0.25f - ds) * W, FIG_U + (0.70f + du) * H, DEPTH, o)
            kit.text(TXT_ABOVE, o[0], o[1], o[2], hh, mix, cutIn * lights * (1f - 0.35f * dark), GlyphBoard.Style.PLAIN)
        }

        // --- the identity, and then the trade -----------------------------------------------------
        // One form at a time: the alpha dips through zero at the swap, so the rearrangement is a
        // beat rather than two overlapping lines of glyphs on a 640-wide eye.
        val nAlpha = told * abs(swap * 2f - 1f) * lights
        val gl = 0.16f
        val u1 = 0.62f * H + FIG_U
        val u2 = 0.40f * H + FIG_U
        if (swap < 0.5f) {
            // uv = ∫ u dv + ∫ v du. What the picture has just shown: two pieces, one rectangle.
            if (q < 2) {
                says(kit, u1, gl, nAlpha, "uv", SceneParts.CHALK, "=", SceneParts.HOT, "∫ u dv", mix)
                says(kit, u2, gl, nAlpha, "+", SceneParts.HOT, "", SceneParts.HOT, "∫ v du", SceneParts.WORK)
            } else {
                plain(kit, u1, gl, nAlpha, "uv = ∫ u dv")
                plain(kit, u2, gl, nAlpha, "+ ∫ v du")
            }
        } else {
            // ∫ u dv = uv − ∫ v du. The same sentence with the unsweepable piece made the subject.
            if (q < 2) {
                says(kit, u1, gl, nAlpha, "∫ u dv", mix, "=", SceneParts.HOT, "uv", SceneParts.CHALK)
                says(kit, u2, gl, nAlpha, "−", SceneParts.HOT, "", SceneParts.HOT, "∫ v du", SceneParts.WORK)
            } else {
                plain(kit, u1, gl, nAlpha, "∫ u dv = uv")
                plain(kit, u2, gl, nAlpha, "− ∫ v du")
            }
        }

        // --- which axis is which ------------------------------------------------------------------
        // Secondary, so quality 0 only. The identity is the line that has to survive a step-down.
        if (q == 0) {
            SceneParts.at(g, FIG_S + 0.5f * W, FIG_U - 0.15f, DEPTH, o)
            kit.text("u", o[0], o[1], o[2], 0.15f, SceneParts.CHALK, 0.85f * lights)
            SceneParts.at(g, FIG_S - 0.10f, FIG_U + 0.95f * H, DEPTH, o)
            kit.text("v", o[0], o[1], o[2], 0.15f, SceneParts.CHALK, 0.85f * lights, anchor = 0.5f)
        }
    }

    /** The stepped-down notation: one call, one colour, same words. */
    private fun plain(kit: SceneKit, u: Float, h: Float, alpha: Float, s: String) {
        if (alpha <= 0.02f) return
        val w = kit.textWidth(s, h)
        val hh = if (w > NOTE_MAX) h * NOTE_MAX / w else h
        SceneParts.at(g, NOTE_S, u, DEPTH, o)
        kit.text(s, o[0], o[1], o[2], hh, SceneParts.HOT, alpha, anchor = 0.5f)
    }
}
