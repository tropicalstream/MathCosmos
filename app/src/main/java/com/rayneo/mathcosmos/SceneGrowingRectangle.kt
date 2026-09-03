package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin

/**
 * Stop 9 — THE GROWING RECTANGLE. "A rectangle growing on both sides gains two strips and a
 * crumb, and the crumb does not matter."
 *
 * The product rule, as carpentry rather than as a formula to be memorised. A plate u by v is
 * growing on both of its free edges. In one tick dt it gains exactly three pieces and no others:
 * a strip along the top, u long and dv thick; a strip up the side, v tall and du thick; and the
 * little square where the two strips meet, du by dv. Two of those pieces are worth having and the
 * third is not, and the whole stop is spent watching the third lose the race.
 *
 * WHY THE PIECES ARE DRAWN AT TRUE SIZE. The obvious staging is to magnify the corner as dt falls,
 * so the strips hold still on screen and the crumb shrinks away underneath them. It does not work,
 * and it is worth saying why once so nobody tries it again: a geometric zoom scales du and dv
 * together, so it freezes the crumb exactly as hard as it freezes the strips and nothing appears
 * to happen at all. The crumb's disappearance is an AREA statement, not a length one — halve dt
 * and each strip loses half its area, because only its thickness shrinks while its long side is
 * still the whole of u or v, but the crumb loses three quarters, because both of its sides shrink
 * at once. So everything here is drawn at true size and the ladder is only allowed three halvings.
 * At the fourth the strips would be thinner than a line and the picture would be over.
 *
 * The crumb never reaches zero, and it is still there at the end of the loop. We do not fade it
 * out and we do not cross it off: it is too small to matter, which is a different and more honest
 * claim than "it is gone", and it is the claim the crew makes out loud.
 *
 * PLACEMENT. The design has this plate standing across the corridor. It cannot: a flat figure on
 * the rail is one the craft flies into, and at the closest point of the pass a viewer would have a
 * corner of it filling one eye and nothing in the other. So the plate hangs to one side, its fixed
 * corner outboard and its GROWING corner inboard, which keeps the design's real intention — the
 * crumb is the nearest thing in the scene as the craft goes by, close enough to be a solid object
 * in stereo, and it is the thing you are being asked to look at.
 *
 * The plate hangs from the roof: its top edge is set a fixed clearance below f at this stop, so it
 * is a plate suspended in the corridor rather than a diagram floating at an arbitrary height. That
 * is the only use this stop makes of the trace — the product rule is not about this tour's f, and
 * pretending otherwise would be decoration.
 *
 * Numbers live on the HUD. The crumb as a percentage of the whole growth halves at every rung —
 * 12, 6, 3, 2 — and that halving is the measurement this stop is making.
 */
object SceneGrowingRectangle : MathScene {

    // A flagship, and a compact one: fade it up early enough to be watched, but it has no geometry
    // reaching past its own node, so `deep` stays at the default.
    override val reach = 1.5f

    // ---- the figure, in its own units ------------------------------------------------------
    private const val U = 0.66f            // world units per figure unit
    private const val U0 = 1.55f           // the sides at the top of the loop
    private const val V0 = 1.15f
    private const val UG = 0.30f           // and how much each side gains across one loop
    private const val VG = 0.25f
    private const val UDOT = 0.42f         // the rates: du = UDOT * dt, dv = VDOT * dt
    private const val VDOT = 0.34f

    // The fixed corner sits outboard and low, so the figure grows towards the rail and the crumb
    // ends up as the inboard-most thing in the scene.
    private const val FIG_S = -0.86f
    private const val FIG_U = -0.60f
    private const val RIGHT_FIG = FIG_S + (U0 + UG + UDOT) * U      // widest the plate ever gets
    private const val TOP_FIG = FIG_U + (V0 + VG + VDOT) * U        // and tallest

    private const val SIDE = -1.25f
    // Headroom between the plate's top edge and the roof ribbon. It has to clear the growth arrow
    // as well as the top strip, which is why it is nearly half a unit and not the 0.3 it looked
    // like it needed: the arrow stands a fifth of a unit proud of the strip.
    private const val CLEAR = 0.44f
    private const val PERIOD = 26f
    private const val LN2 = 0.6931472f
    private const val TAU = 6.2831855f
    private const val DEPTH = -0.008f      // overlays, a hair towards the approaching craft

    // Scratch. Nothing below allocates; the object holds nothing between frames.
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val a3 = FloatArray(3)
    private val b3 = FloatArray(3)
    private val tv = IntArray(1)
    private val DT_NAMES = arrayOf("1", "1/2", "1/4", "1/8")

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree with the picture --

    private fun uAt(c: Float) = U0 + UG * SceneParts.step(c, 0.02f, 0.62f)
    private fun vAt(c: Float) = V0 + VG * SceneParts.step(c, 0.02f, 0.62f)

    /**
     * The cut at cycle position [c]: one, then three halvings with a pause on each rung. Eased
     * rather than stepped, so the strips are seen to thin rather than found already thinner.
     */
    private fun dtAt(c: Float): Float {
        val fall = SceneParts.step(c, 0.30f, 0.09f) +
            SceneParts.step(c, 0.45f, 0.09f) +
            SceneParts.step(c, 0.60f, 0.09f)
        return exp(-fall * LN2)
    }

    private fun dtLabel(dt: Float): String =
        DT_NAMES[round(-ln(dt) / LN2).toInt().coerceIn(0, 3)]

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val u = uAt(c)
        val v = vAt(c)
        // Until the tick has happened there are no strips and no crumb, and quoting a percentage
        // of a growth that has not occurred yet would be a number the picture has not earned.
        if (SceneParts.step(c, 0.12f, 0.10f) < 0.99f) {
            return String.format(Locale.US, "uv %.2f   BOTH SIDES GROWING", u * v)
        }
        val dt = dtAt(c)
        val du = UDOT * dt
        val dv = VDOT * dt
        val strips = u * dv + v * du
        val crumb = du * dv
        return String.format(
            Locale.US, "dt %s   CRUMB %.1f%% OF THE GROWTH",
            dtLabel(dt), 100f * crumb / (strips + crumb)
        )
    }

    /**
     * One piece of the figure, in figure coordinates: fill and rim. The strips are given a much
     * heavier fill than [SceneParts.pane] would, because they are the pieces that LIGHT UP — at a
     * fifth of a unit thick a faint wash reads as nothing at all.
     */
    private fun piece(
        line: FloatArray, lv: Int, tri: FloatArray,
        s: Float, t: Float, ws: Float, wt: Float,
        c: FloatArray, fillA: Float, edgeA: Float
    ): Int {
        if (ws <= 1e-4f || wt <= 1e-4f) return lv
        SceneParts.at(g, FIG_S + s * U, FIG_U + t * U, 0f, o)
        SceneParts.vec(g, ws * U, 0f, 0f, a3)
        SceneParts.vec(g, 0f, wt * U, 0f, b3)
        tv[0] = SceneParts.fill(
            tri, tv[0], o[0], o[1], o[2], a3[0], a3[1], a3[2], b3[0], b3[1], b3[2], c, fillA
        )
        return SceneParts.edge(
            line, lv, o[0], o[1], o[2], a3[0], a3[1], a3[2], b3[0], b3[1], b3[2], c, edgeA
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()

        // Hang the plate under the roof. The ambient ribbon presses itself against the wall where
        // the passage is tighter than f, and that squeeze is not reproduced here — we only need to
        // be BELOW the ribbon rather than on it, and the clamp covers the case where a tight
        // passage has pushed the roof down onto us.
        val hang = if (!kit.hasTrace) -0.05f else {
            (kit.traceHeight(at) - TOP_FIG - CLEAR).coerceIn(-1.10f, 0.50f)
        }
        SceneParts.stage(kit, at, SIDE, hang, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var lv = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val u = uAt(c)
        val v = vAt(c)
        val dt = dtAt(c)
        val show = SceneParts.step(c, 0.12f, 0.10f)
        val says = SceneParts.step(c, 0.74f, 0.10f)
        // The tick itself: the two edges move out from nothing, which is what makes the strips an
        // event rather than a border the plate always had.
        val du = UDOT * dt * show
        val dv = VDOT * dt * show
        // The crumb's flicker. Fast enough to catch the eye from across the corridor, and it is
        // the only thing in the scene that pulses, so it is never ambiguous which piece is meant.
        val pulse = 0.55f + 0.45f * sin(kit.seconds * 5.2f)

        // --- the plate that is already there ----------------------------------------------------
        // Deliberately not ruled into unit tiles. Tour I counts; this stop does not, and a ruling
        // would invite a viewer to read u and v as whole numbers when the whole point is that they
        // are two quantities changing at their own rates.
        lv = piece(line, lv, tri, 0f, 0f, u, v, SceneParts.COOL, 0.18f, 0.95f)

        // --- and the three pieces one tick buys ---------------------------------------------------
        // Cyan along the top, amber up the side, red in the corner: the design's three colours, and
        // the same three the notation below is written in.
        lv = piece(line, lv, tri, 0f, v, u, dv, SceneParts.ADDED, 0.50f, 1f)
        lv = piece(line, lv, tri, u, 0f, du, v, SceneParts.WORK, 0.50f, 1f)
        lv = piece(line, lv, tri, u, v, du, dv, SceneParts.TAKEN, 0.75f * pulse, pulse)

        // --- the pointer at the crumb --------------------------------------------------------------
        // A ring of FIXED radius round the corner. It is a pointer, not the crumb — it keeps a
        // thing you can no longer really see findable, which matters more with every halving, and
        // because it does not shrink there is never any doubt about which of the two is vanishing.
        if (show > 0.5f && kit.quality < 2) {
            SceneParts.at(g, FIG_S + (u + du * 0.5f) * U, FIG_U + (v + dv * 0.5f) * U, DEPTH, o)
            SceneParts.vec(g, 1f, 0f, 0f, a3)
            SceneParts.vec(g, 0f, 1f, 0f, b3)
            lv = MathMesh.arc(
                line, lv, o[0], o[1], o[2], a3[0], a3[1], a3[2], b3[0], b3[1], b3[2],
                0.125f, 0f, TAU, if (kit.quality == 0) 16 else 8,
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2],
                (0.30f + 0.45f * pulse) * show
            )
        }

        // --- which way the sides are moving ----------------------------------------------------
        // Two short arrows outboard of each strip. Their LENGTH is not du or dv — du and dv are the
        // strips themselves, and drawing them twice at two different scales would be a lie. These
        // only say that both edges are travelling, which is the sentence "growing on both sides".
        if (kit.quality == 0) {
            val lit = 0.35f + 0.25f * kit.beat
            SceneParts.at(g, FIG_S + (u + du) * U + 0.06f, FIG_U + v * U * 0.42f, DEPTH, o)
            SceneParts.vec(g, 0.15f, 0f, 0f, a3)
            lv = MathMesh.arrow(
                line, lv, o[0], o[1], o[2], a3[0], a3[1], a3[2], g[9], g[10], g[11],
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.55f + lit
            )
            SceneParts.at(g, FIG_S + u * U * 0.42f, FIG_U + (v + dv) * U + 0.06f, DEPTH, o)
            SceneParts.vec(g, 0f, 0.15f, 0f, a3)
            lv = MathMesh.arrow(
                line, lv, o[0], o[1], o[2], a3[0], a3[1], a3[2], g[9], g[10], g[11],
                SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.55f + lit
            )
        }

        kit.flushLines(lv, 2.2f)
        kit.flushTris(tv[0])

        // --- notation ---------------------------------------------------------------------------
        // The identity, two lines, hung beside the plate on the inboard side where nothing else is.
        // Its two terms are drawn in the strips' own colours, and that shared colour is the entire
        // join between the picture and the writing: there is no leader line and there does not need
        // to be one. The identity survives at every quality — it is what the stop is for.
        // A glyph drawn at alpha zero still costs a draw call, and for three quarters of the loop
        // there is nothing to say yet, so the whole block is gated rather than faded.
        val hh = 0.135f
        val sx = RIGHT_FIG + 0.16f
        if (says > 0.01f) {
            if (kit.quality < 2) {
                SceneParts.at(g, sx, FIG_U + 0.34f, DEPTH, o)
                kit.text("d(uv) =", o[0], o[1], o[2], hh, SceneParts.HOT, says, anchor = -0.5f)
                var x = sx
                SceneParts.at(g, x, FIG_U + 0.11f, DEPTH, o)
                kit.text("u dv", o[0], o[1], o[2], hh, SceneParts.ADDED, says, anchor = -0.5f)
                x += kit.textWidth("u dv", hh) + hh * 0.4f
                SceneParts.at(g, x, FIG_U + 0.11f, DEPTH, o)
                kit.text("+", o[0], o[1], o[2], hh, SceneParts.HOT, says, anchor = -0.5f)
                x += kit.textWidth("+", hh) + hh * 0.4f
                SceneParts.at(g, x, FIG_U + 0.11f, DEPTH, o)
                kit.text("v du", o[0], o[1], o[2], hh, SceneParts.WORK, says, anchor = -0.5f)
            } else {
                SceneParts.at(g, sx, FIG_U + 0.20f, DEPTH, o)
                kit.text("u dv + v du", o[0], o[1], o[2], hh, SceneParts.HOT, says, anchor = -0.5f)
            }
        }

        // The pieces named where they lie, and the two sides named on their own edges. Secondary
        // notation, so quality 0 only: at a governed frame rate the identity is the one that has
        // to survive, and five more labels on a 640-wide eye is a wall of glyphs.
        if (kit.quality == 0) {
            if (show > 0.01f) {
                SceneParts.at(g, FIG_S - 0.12f, FIG_U + (v + dv * 0.5f) * U, DEPTH, o)
                kit.text("u dv", o[0], o[1], o[2], 0.125f, SceneParts.ADDED, show, anchor = 0.5f)
                SceneParts.at(g, FIG_S + (u + du * 0.5f) * U, FIG_U - 0.17f, DEPTH, o)
                kit.text("v du", o[0], o[1], o[2], 0.125f, SceneParts.WORK, show)
                SceneParts.at(g, FIG_S + (u + du) * U + 0.10f, FIG_U + (v + dv) * U + 0.10f, DEPTH, o)
                kit.text("du dv", o[0], o[1], o[2], 0.115f, SceneParts.TAKEN, show * pulse, anchor = -0.5f)
            }

            SceneParts.at(g, FIG_S + u * U * 0.42f, FIG_U - 0.17f, DEPTH, o)
            kit.text("u", o[0], o[1], o[2], 0.15f, SceneParts.CHALK, 0.85f)
            SceneParts.at(g, FIG_S - 0.13f, FIG_U + v * U * 0.45f, DEPTH, o)
            kit.text("v", o[0], o[1], o[2], 0.15f, SceneParts.CHALK, 0.85f, anchor = 0.5f)
        }
    }
}
