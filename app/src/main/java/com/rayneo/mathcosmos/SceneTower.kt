package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.pow

/**
 * Stop 2 — THE TOWER. "A series is a tower built one brick at a time, and the only question is
 * whether it stops growing."
 *
 * Two towers stand side by side and are fed the same way: one brick per beat, each brick as tall
 * as its term. The left tower is geometric — 1/2, 1/4, 1/8 — and it visibly runs out of room under
 * a bright beam at height 1, the gap between its top and the beam halving until you cannot see it.
 * The right tower is harmonic, and it is the whole point of the stop: its bricks shrink too, they
 * shrink to nothing exactly as the left one's do, and the tower does not stop. It goes through the
 * beam at the third brick and is still climbing when the corridor's roof arrives.
 *
 * The right tower starts at 1/2 rather than 1, so its FIRST brick is identical to the left one's.
 * That is the image the stop is built around: two towers that begin the same, laid at the same
 * rate, one of which has an answer and one of which does not. Dropping the leading 1 cannot change
 * whether a series converges, so nothing is given away by starting there — but it must be said out
 * loud, and the crew does say it, so the code says it here too.
 *
 * Nothing about the bricks above the beam is drawn differently. There is no colour change, no
 * alarm: term 400 of the harmonic series is an ordinary small number just like term 4, and the
 * unsettling part is precisely that nothing goes wrong anywhere. The arrow at the top is a promise
 * rather than a drawn sum — the tower's next doubling would cost roughly SQUARING the number of
 * bricks (H_n grows like ln n), which is more bricks than this or any other display has, and that
 * cost is itself the reason the climb looks like it has stopped when it has not.
 *
 * Placement: the design asks for one tower left and one right of the rail. Built that way the
 * craft flies BETWEEN them and, at the closest point of the pass, sees neither — so the pair is
 * kept together off to port instead, left and right of each other. The comparison is unharmed and
 * both towers stay in frame for the whole approach, which is when a stop like this is actually
 * read.
 *
 * Stacking is driven by the cycle clock, not by rail progress. The script talks about one ring per
 * term, but a viewer arrives at an arbitrary moment and must see the towers built from nothing
 * regardless; a scene tied to the ship's position would be half-finished for anyone who came late.
 */
object SceneTower : MathScene {

    override val reach = 1.4f

    // How many terms each tower gets. Twelve is the thermal fallback rather than half a picture:
    // by n = 12 the geometric tower is within 0.0003 of its limit and the harmonic one is already
    // at 1.93, so both towers have finished making their point — there is simply less of the long
    // dull climb, which is the part that costs bricks and says the least per brick.
    private const val N_FULL = 24
    private const val N_LOW = 12

    private const val PERIOD = 26f
    private const val BUILD_AT = 0.05f
    private const val BUILD_LEN = 0.68f     // the rest of the cycle is rest, to look at the pair

    private const val SIDE = -1.20f         // the pair sits to port; the craft passes alongside it
    private const val U = 0.72f             // world units per 1.0 of series value
    private const val BASE = -0.62f         // where both towers stand, relative to the rail
    private const val CEIL_U = BASE + U     // the beam at height 1: the geometric tower's answer
    private const val TWO_U = BASE + 2f * U

    private const val HW = 0.22f            // brick half-width
    private const val GS = -0.46f           // geometric tower centre, in stage coordinates
    private const val HS = 0.46f            // harmonic tower centre
    private const val SPAN = 0.86f          // half-length of the plinth and the beam
    private const val DEPTH = 0.15f         // brick half-depth along the rail
    private const val DEEP = 0.24f          // beams are deeper, so they pass in front of the towers

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val dw = FloatArray(3)
    private val tv = IntArray(1)

    /** Two decimals without a formatter, so the HUD line costs one small string and no locale. */
    private fun d2(v: Float): String {
        val t = (v * 100f + 0.5f).toInt()
        val fr = t % 100
        return "${t / 100}." + (if (fr < 10) "0$fr" else "$fr")
    }

    /** How many bricks are down at this instant, as a float. Accelerating: the late terms are a
     *  blur of slivers on purpose, because that blur IS what "it keeps going" feels like. */
    private fun laid(kit: SceneKit, n: Int): Float {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val t = ((c - BUILD_AT) / BUILD_LEN).coerceIn(0f, 1f)
        return n * t.pow(1.35f)
    }

    override fun readout(kit: SceneKit): String? {
        val n = laid(kit, if (kit.quality == 0) N_FULL else N_LOW).toInt()
        if (n < 1) return "n 0   BOTH TOWERS EMPTY"
        var h = 0f
        for (j in 0 until n) h += 1f / (j + 2f)
        // The geometric tower's shortfall is exactly 2^-n, which is worth reading as an exponent
        // rather than as a decimal that flattens to 0.00 four bricks in.
        return "n $n   GAP 2^-$n   TOWER ${d2(h)}"
    }

    /**
     * One brick: a front face square to the oncoming craft, and — at full quality — the top face
     * and its rim. The top face is what stops a stack of flat panes reading as a stack of flat
     * panes; without it the towers look painted on the wall rather than standing in the corridor.
     */
    private fun slab(
        line: FloatArray, lv: Int, tri: FloatArray,
        s0: Float, u0: Float, w: Float, h: Float, d: Float,
        c: FloatArray, alpha: Float, solid: Boolean
    ): Int {
        SceneParts.at(g, s0, u0, -d, o)
        SceneParts.vec(g, w, 0f, 0f, du)
        SceneParts.vec(g, 0f, h, 0f, dv)
        tv[0] = MathMesh.quad(
            tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            c[0], c[1], c[2], alpha * 0.26f
        )
        var k = SceneParts.edge(line, lv, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha)
        if (!solid) return k
        SceneParts.at(g, s0, u0 + h, -d, o)
        SceneParts.vec(g, 0f, 0f, d * 2f, dw)
        tv[0] = MathMesh.quad(
            tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dw[0], dw[1], dw[2],
            c[0], c[1], c[2], alpha * 0.16f
        )
        val a = alpha * 0.55f
        k = MathMesh.segment(line, k, o[0], o[1], o[2], o[0] + dw[0], o[1] + dw[1], o[2] + dw[2], c[0], c[1], c[2], a)
        k = MathMesh.segment(
            line, k, o[0] + du[0], o[1] + du[1], o[2] + du[2],
            o[0] + du[0] + dw[0], o[1] + du[1] + dw[1], o[2] + du[2] + dw[2], c[0], c[1], c[2], a
        )
        k = MathMesh.segment(
            line, k, o[0] + dw[0], o[1] + dw[1], o[2] + dw[2],
            o[0] + du[0] + dw[0], o[1] + du[1] + dw[1], o[2] + du[2] + dw[2], c[0], c[1], c[2], a
        )
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), SIDE, 0f, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val terms = if (kit.quality == 0) N_FULL else N_LOW
        val solid = kit.quality == 0
        val nf = laid(kit, terms)

        // --- the floor both towers are laid on ------------------------------------------------
        v = slab(line, v, tri, -SPAN, BASE - 0.045f, SPAN * 2f, 0.045f, DEEP,
            SceneParts.STEEL, 0.55f, solid)

        // --- the two towers --------------------------------------------------------------------
        // Both are fed from the same index, so the bricks land in pairs and the towers can be read
        // against each other rung by rung. `shown` follows the eased heights so the top markers
        // travel with the falling brick instead of jumping ahead of it.
        var gSeat = 0f; var hSeat = 0f
        var gShown = 0f; var hShown = 0f
        var gTerm = 0.5f
        for (j in 0 until terms) {
            val p = (nf - j).coerceIn(0f, 1f)
            if (p <= 0f) break
            val e = SceneParts.ease(p)
            val drop = (1f - e) * 0.55f          // still falling, in series units
            val hTerm = 1f / (j + 2f)
            v = slab(line, v, tri, GS - HW, BASE + (gSeat + drop) * U, HW * 2f, gTerm * U, DEPTH,
                SceneParts.ADDED, e * 0.95f, solid)
            v = slab(line, v, tri, HS - HW, BASE + (hSeat + drop) * U, HW * 2f, hTerm * U, DEPTH,
                SceneParts.WORK, e * 0.95f, solid)
            gSeat += gTerm; hSeat += hTerm
            gShown += gTerm * e; hShown += hTerm * e
            gTerm *= 0.5f
        }
        val gTopU = BASE + gShown * U
        val hTopU = BASE + hShown * U

        // --- the beam at 1 -----------------------------------------------------------------------
        // Deeper than the bricks, so it stands in front of the harmonic tower and the crossing is
        // unmistakable. Kept as a beam rather than a sheet: a horizontal plane at roughly eye
        // height is edge-on for the whole approach, which is exactly when this line must be read.
        v = slab(line, v, tri, -SPAN, CEIL_U - 0.022f, SPAN * 2f, 0.044f, DEEP,
            SceneParts.HOT, 0.9f, solid)

        // --- the shrinking gap ---------------------------------------------------------------
        // Visible for four or five bricks and then gone, which is the honest picture: the tower
        // does not creep up to the beam, it runs out of room. The number lives on the HUD.
        val gapU = CEIL_U - 0.022f - gTopU
        if (nf > 0.5f && gapU > 0.018f) {
            SceneParts.at(g, GS, gTopU, -DEPTH - 0.03f, o)
            SceneParts.at(g, GS, CEIL_U - 0.022f, -DEPTH - 0.03f, du)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], du[0], du[1], du[2], 4,
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.95f)
        }

        // --- a second gradation at 2, so the long climb has something to be measured against ----
        if (kit.quality == 0) {
            SceneParts.at(g, -SPAN, TWO_U, -DEEP, o)
            SceneParts.at(g, SPAN, TWO_U, -DEEP, du)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], du[0], du[1], du[2], 9,
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.55f)
        }

        // --- and where it is going -------------------------------------------------------------
        // The arrow ends at the tour's own roof curve, which is the nearest thing in this corridor
        // to a limit on how tall anything may be. The tower is not drawn past it because the terms
        // that would take it there number in the hundreds; the arrow is a claim, not a sum, and
        // the crew says as much.
        val roof = kit.traceHeight(i.toFloat())
        val fromU = hTopU + 0.12f
        if (nf > 2.5f && roof - 0.10f > fromU + 0.18f) {
            SceneParts.at(g, HS, fromU, -DEPTH, o)
            SceneParts.vec(g, 0f, roof - 0.10f - fromU, 0f, dv)
            SceneParts.vec(g, 0f, 0f, 1f, dw)      // barbs in the plane of the figure, not edge-on
            v = MathMesh.arrow(
                line, v, o[0], o[1], o[2], dv[0], dv[1], dv[2], dw[0], dw[1], dw[2],
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.75f, 0.22f
            )
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the two tops ------------------------------------------------------------------------
        // One bead each, riding the falling brick. The left one settles under the beam and stays
        // there for the rest of the cycle; the right one never settles, and that is the stop.
        val pulse = 0.052f + 0.018f * kit.beat
        if (nf > 0.2f) {
            SceneParts.at(g, GS, gTopU, -DEPTH, o)
            kit.ball(o[0], o[1], o[2], pulse, pulse, pulse, SceneParts.ADDED, SceneParts.HOT,
                0.95f, 0f, 0f, 1f, 0f, 0f, 1.1f)
            SceneParts.at(g, HS, hTopU, -DEPTH, o)
            kit.ball(o[0], o[1], o[2], pulse, pulse, pulse, SceneParts.WORK, SceneParts.HOT,
                0.95f, 0f, 0f, 1f, 0f, 0f, 1.1f)
        }

        // The moment the harmonic tower goes through the answer the other one converged to. Brief,
        // and deliberately the only piece of alarm in the scene.
        if (kit.quality < 2) {
            val cross = (1f - abs(hShown - 1f) * 7f).coerceIn(0f, 1f)
            if (cross > 0.02f) {
                SceneParts.at(g, HS, CEIL_U, -DEPTH, o)
                kit.ball(o[0], o[1], o[2], 0.09f, 0.09f, 0.09f, SceneParts.HOT, SceneParts.WORK,
                    cross, 0f, 0f, 1f, 0f, 0f, 3.2f * cross)
            }
        }

        // --- notation ------------------------------------------------------------------------
        // Beside each tower and low down, clear of the telemetry at the top of the eye and of the
        // caption box at the bottom. The notation names the towers; the towers do the arguing.
        val gl = 0.19f
        SceneParts.at(g, GS - HW - 0.10f, BASE + 0.15f, -DEPTH, o)
        kit.text("Σ 2^{-n}", o[0], o[1], o[2], gl, SceneParts.ADDED, 1f,
            GlyphBoard.Style.MATH, 1f, 0.5f)
        SceneParts.at(g, HS + HW + 0.10f, BASE + 0.15f, -DEPTH, o)
        kit.text("Σ 1/n", o[0], o[1], o[2], gl, SceneParts.WORK, 1f,
            GlyphBoard.Style.MATH, 1f, -0.5f)

        SceneParts.at(g, -SPAN - 0.09f, CEIL_U, -DEEP, o)
        kit.text("1", o[0], o[1], o[2], gl * 0.95f, SceneParts.HOT, 1f,
            GlyphBoard.Style.PLAIN, 1f, 0.5f)

        if (kit.quality == 0) {
            SceneParts.at(g, SPAN + 0.09f, TWO_U, -DEEP, o)
            kit.text("2", o[0], o[1], o[2], gl * 0.85f, SceneParts.CHALK, 0.7f,
                GlyphBoard.Style.SMALL, 1f, -0.5f)
            if (nf > 2.5f && roof - 0.10f > fromU + 0.18f) {
                SceneParts.at(g, HS + 0.13f, (fromU + roof - 0.10f) * 0.5f, -DEPTH, o)
                kit.text("n → ∞", o[0], o[1], o[2], gl * 0.85f, SceneParts.WORK, 0.8f,
                    GlyphBoard.Style.SMALL, 1f, -0.5f)
            }
        }
    }
}
