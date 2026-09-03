package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.sin

/**
 * Stop 2 — THE NARROWING. "Tell me how close you want the answer, and I'll tell you how close
 * to stand."
 *
 * The flagship of Tour II and the stop the whole tour is built to earn: ε–δ, physically. Somebody
 * names a tolerance ε on the answer; the corridor answers with a tolerance δ on where you may
 * stand. Then ε halves and the whole apparatus tightens, three times, and the throat gets tight
 * enough that you flinch. Nothing here is a caption about limits — the promise is demanded, met,
 * and visibly kept, three times a cycle.
 *
 * There are two halves to it, and the reason they are two halves is worth setting down, because
 * the obvious single-object version does not work on this rail.
 *
 * THE GATE, on the rail. Two horizontal light-plates across the corridor, above and below the
 * craft's own line, closing on it as ε shrinks; the craft threads the gap and the gap IS 2ε. The
 * metaphor is explicit and the crew says it out loud: the rail stands in for the limiting value L,
 * so "flying between the plates" is "landing inside the ε band". The passage's own funnel — 3.4
 * out on the leg, 1.6 at this node — is the renderer's, drawn from the node radii, and this scene
 * deliberately does not rebuild it. It is already the best δ in the app.
 *
 * THE WINDOW, off to one side. A magnified graph of f near a, with the ε band, the δ window and
 * the curve coloured by whether the promise is being kept.
 *
 * Why a separate magnified graph, when the roof curve is right there overhead? Two reasons, both
 * of them arithmetic rather than taste.
 *
 *   The scales are not commensurate. A node unit of x is sixteen world units of corridor, and f
 *   does its interesting bending over roughly one node unit. An honest δ ring on the rail would
 *   therefore stand twelve world units astern — out of the fade, in a stretch of corridor with a
 *   different radius, and not in the same shot as anything it is meant to bracket. The picture has
 *   to be compressed along x before it can be a picture at all, and once it is compressed it is a
 *   diagram in its own frame, so it may as well be a good one.
 *
 *   The ribbon overhead is not f here. SceneAmbientTrace squeezes the trace against the ceiling
 *   wherever the corridor is tighter than the curve, and at this node — the tightest on the tour,
 *   which is the point of the node — it is squeezed flat: what you see riding the roof through the
 *   throat is 0.8 of the passage radius, not f. That compromise is the right one for the ambient
 *   (a trace plotted honestly here would simply be inside an opaque wall), but ε–δ measured
 *   against it would be a demonstration about the ceiling. So the window re-plots f honestly from
 *   kit.traceHeight and says nothing about the ribbon; the ribbon is left to the ambient, which
 *   owns it.
 *
 * δ is SCANNED, not authored. Every frame the scene walks outward from a in steps of 0.05 node
 * units and takes the largest window over which |f(x) − L| stays under ε. Hard-coding the three
 * answers would have been cheaper and would have been a lie the first time anyone re-cut the
 * trace; scanning costs about forty evaluations of two sines and means the picture is true of
 * whatever function the tour is actually carrying. It is also what makes the scene survive being
 * dropped on the proving ground, where there may be no trace at all and a local stand-in is used.
 *
 * The vertical scale of the window is taken from f's own swing across the window, so ε is quoted
 * as a fraction of that swing rather than in the trace's arbitrary units — 0.60, then 0.30, then
 * 0.15. Halving, which is the thing to see. The numbers themselves go to the HUD via [readout],
 * where they are legible; the 3D carries only the four names ε, δ, L and a.
 */
object SceneNarrowing : MathScene {

    override val reach = 1.5f

    // ---- the loop ------------------------------------------------------------------------
    private const val PERIOD = 27f         // three rounds of eight, then three seconds to look
    private const val ROUND_T = 8f
    private const val ROUNDS = 3

    /** The three tolerances, as fractions of f's own swing across the window. Halving. */
    private val EPS_F = floatArrayOf(0.60f, 0.30f, 0.15f)

    // ---- the window ----------------------------------------------------------------------
    private const val R = 1.05f            // half-width of the window, in node units of x
    private const val STEP = 0.05f         // the δ scan's stride, in node units
    private const val W = 0.42f            // half-width of the drawn figure, in world units
    private const val H = 0.34f            // half-height of the drawn figure
    private const val SIDE = -0.82f        // the passage radius here is 1.6; the figure's far
    private const val UP = 0.06f           // corner sits at 1.24, just inside the safe 1.28

    // ---- the gate ------------------------------------------------------------------------
    // Astern of the stop, so the craft threads it BEFORE it draws alongside the window, and so
    // the plates never overlap the figure in depth however the rail is turning.
    private const val GATE_A0 = -3.0f
    private const val GATE_A1 = -1.25f
    private const val GATE_S = 0.85f       // half-span across the corridor
    private const val GAP_MAX = 0.86f      // the plates at their widest, either side of the rail
    private const val GAP_MIN = 0.26f

    private val f = FloatArray(12)
    private val g = FloatArray(12)          // the window's frame, offset to one side
    private val gr = FloatArray(12)         // the rail's own frame, for the gate
    private val o = FloatArray(3)
    private val q = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    // The scan's answers, memoised on the stop they were taken at. The scene is still a pure
    // function of the kit and the clock — δ(ε) is a property of f, not of the frame — but
    // kit.traceHeight reaches the tour through a generic lambda, which boxes its argument and its
    // result, so a hundred and forty scan steps per eye per frame is a hundred and forty pairs of
    // short-lived Floats. Taking the scan once per stop drops that to the curve's own samples.
    // Keyed on the tour identity as well as the node, so switching tours cannot serve a stale δ.
    private var keyTour: String? = null
    private var keyA = Float.NaN
    private var tunedVs = 0f
    private val tunedDel = FloatArray(ROUNDS)

    /**
     * f at [p]. On a tour with no roof curve there is nothing to take a limit of, so a small
     * stand-in is used rather than drawing a flat line and calling it a demonstration.
     */
    private fun fx(kit: SceneKit, p: Float): Float =
        if (kit.hasTrace) kit.traceHeight(p) else 1.60f + 0.55f * sin(p * 0.90f)

    /**
     * How far f swings from L across the window, with a little margin. This is the figure's
     * vertical scale, so the curve always fills its box and never climbs out of the corridor,
     * whatever the trace happens to be doing at this stop.
     */
    private fun spread(kit: SceneKit, a: Float, l: Float): Float {
        var m = 0f
        for (k in -6..6) {
            val d = abs(fx(kit, a + k * (R / 6f)) - l)
            if (d > m) m = d
        }
        return (m * 1.12f).coerceAtLeast(0.10f)
    }

    /**
     * The largest δ that answers [eps]: walk outward from [a] until one side or the other leaves
     * the band, and keep the last step that did not. Coarse on purpose — a δ resolved finer than
     * a twentieth of the window would be a distinction nobody can see at this size.
     */
    private fun deltaFor(kit: SceneKit, a: Float, l: Float, eps: Float): Float {
        var best = STEP
        var d = STEP
        while (d <= R) {
            if (abs(fx(kit, a - d) - l) >= eps || abs(fx(kit, a + d) - l) >= eps) break
            best = d
            d += STEP
        }
        return best.coerceAtMost(R)
    }

    /** Take the scan, unless it has already been taken for this stop of this tour. */
    private fun tune(kit: SceneKit, a: Float) {
        val t = kit.tourTitle
        if (a == keyA && t === keyTour) return
        keyTour = t; keyA = a
        val l = fx(kit, a)
        tunedVs = spread(kit, a, l)
        for (r in 0 until ROUNDS) tunedDel[r] = deltaFor(kit, a, l, EPS_F[r] * tunedVs)
    }

    /** A point of the window in its normalised coordinates: [sx] and [uy] both run -1..1. */
    private fun node(sx: Float, uy: Float, out: FloatArray) =
        SceneParts.at(g, sx * W, uy * H, 0f, out)

    /** Which round the cycle is in, and how far through it. Packed to keep draw and readout agreed. */
    private fun roundOf(seconds: Float): Int =
        ((SceneParts.cycle(seconds, PERIOD) * PERIOD) / ROUND_T).toInt().coerceIn(0, ROUNDS - 1)

    private fun phaseOf(seconds: Float): Float {
        val t = SceneParts.cycle(seconds, PERIOD) * PERIOD
        return ((t - roundOf(seconds) * ROUND_T) / ROUND_T).coerceIn(0f, 1f)
    }

    /**
     * Both tolerances and whether the promise is currently being kept. The window's ε and δ are
     * quoted as fractions of the window itself, which is the only unit either of them has that
     * means anything to a viewer: what matters is that both halve, together, three times.
     */
    override fun readout(kit: SceneKit): String? {
        tune(kit, kit.progress.toInt().toFloat())
        val r = roundOf(kit.seconds)
        val u = phaseOf(kit.seconds)
        val epsN = lerp(if (r == 0) 1f else EPS_F[r - 1], EPS_F[r], SceneParts.step(u, 0.06f, 0.20f))
        val dPrev = if (r == 0) R else tunedDel[r - 1]
        val dNow = tunedDel[r]
        val dShown = lerp(dPrev, dNow, SceneParts.step(u, 0.34f, 0.28f))
        val held = dShown <= dNow + 0.5f * STEP
        return "ε %.2f  →  δ %.2f   %s".format(
            java.util.Locale.US, epsN, dShown / R, if (held) "PROMISE KEPT" else "TOO WIDE"
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val a = i.toFloat()
        SceneParts.stage(kit, a, SIDE, UP, f, g)
        SceneParts.stage(kit, a, 0f, 0f, f, gr)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val quality = kit.quality
        tune(kit, a)
        val l = fx(kit, a)
        val vs = tunedVs

        // --- the promise, and the answer to it -------------------------------------------------
        // ε closes first and the answer is late on purpose: for a beat the band is tighter than
        // the window can honour, the curve's ends go red, and only then do the walls come in.
        // That gap between the demand and the answer is the whole definition, in time.
        val r = roundOf(kit.seconds)
        val u = phaseOf(kit.seconds)
        val epsN = lerp(if (r == 0) 1f else EPS_F[r - 1], EPS_F[r], SceneParts.step(u, 0.06f, 0.20f))
        val dPrev = if (r == 0) R else tunedDel[r - 1]
        val delN = lerp(dPrev, tunedDel[r], SceneParts.step(u, 0.34f, 0.28f)) / R

        // --- the gate on the rail ---------------------------------------------------------------
        // Two plates, closing on the craft's own line. This is ε made into something you fly
        // between; the rail standing in for L is a metaphor, and the crew names it as one.
        val gap = GAP_MIN + (GAP_MAX - GAP_MIN) * epsN
        for (s in 0 until 2) {
            val h = if (s == 0) gap else -gap
            SceneParts.at(gr, -GATE_S, h, GATE_A0, o)
            SceneParts.vec(gr, GATE_S * 2f, 0f, 0f, du)
            SceneParts.vec(gr, 0f, 0f, GATE_A1 - GATE_A0, dv)
            v = SceneParts.pane(
                kit, line, v, tri, tv, o[0], o[1], o[2],
                du[0], du[1], du[2], dv[0], dv[1], dv[2], SceneParts.COOL, 0.72f
            )
        }

        // --- the window's own frame -------------------------------------------------------------
        node(-1f, -1f, o)
        SceneParts.vec(g, W * 2f, 0f, 0f, du)
        SceneParts.vec(g, 0f, H * 2f, 0f, dv)
        v = SceneParts.edge(line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.CHALK, 0.30f)

        // --- the ε band --------------------------------------------------------------------------
        node(-1f, -epsN, o)
        SceneParts.vec(g, W * 2f, 0f, 0f, du)
        SceneParts.vec(g, 0f, epsN * 2f * H, 0f, dv)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], SceneParts.COOL, 0.85f
        )

        // --- the δ window ------------------------------------------------------------------------
        node(-delN, -1f, o)
        SceneParts.vec(g, delN * 2f * W, 0f, 0f, du)
        SceneParts.vec(g, 0f, H * 2f, 0f, dv)
        v = SceneParts.pane(
            kit, line, v, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], SceneParts.WORK, 0.85f
        )

        // --- the two construction lines that fix L and a -------------------------------------------
        if (quality < 2) {
            node(-1f, 0f, o); node(1f, 0f, q)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], q[0], q[1], q[2], 9,
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.45f)
            node(0f, -1f, o); node(0f, 1f, q)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], q[0], q[1], q[2], 7,
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.45f)
        }

        // --- f itself -------------------------------------------------------------------------------
        // One polyline, coloured segment by segment by the two tests the definition makes: is this
        // x inside the window, and if it is, did its height land inside the band. Red only ever
        // appears where the promise is actually broken, which is why it means something when it does.
        val ns = when (quality) { 0 -> 44; 1 -> 26; else -> 16 }
        val hot = SceneParts.HOT
        val bad = SceneParts.TAKEN
        val pulse = 0.72f + 0.28f * sin(kit.seconds * 7f)
        var sxPrev = -1f
        var uyPrev = ((fx(kit, a - R) - l) / vs).coerceIn(-1.1f, 1.1f)
        node(sxPrev, uyPrev, q)
        for (k in 1..ns) {
            val sx = -1f + 2f * k / ns
            val uy = ((fx(kit, a + sx * R) - l) / vs).coerceIn(-1.1f, 1.1f)
            node(sx, uy, o)
            val mSx = (sxPrev + sx) * 0.5f
            val mUy = (uyPrev + uy) * 0.5f
            val inside = abs(mSx) <= delN
            val ok = abs(mUy) < epsN
            val c = if (inside && !ok) bad else hot
            val al = if (!inside) 0.24f else if (ok) 1f else pulse
            v = MathMesh.segment(line, v, q[0], q[1], q[2], o[0], o[1], o[2], c[0], c[1], c[2], al)
            q[0] = o[0]; q[1] = o[1]; q[2] = o[2]
            sxPrev = sx; uyPrev = uy
        }

        // --- the roving x ------------------------------------------------------------------------
        // "For every x in the window" is a quantifier, and a quantifier is not a picture until
        // something actually walks the window. One bead does, back and forth, with a hair dropped
        // to the L line so its miss is a length rather than an opinion.
        var beadUy = 0f
        var beadSx = 0f
        var beadOk = true
        if (quality < 2) {
            val sw = SceneParts.cycle(kit.seconds, 6.5f)
            val tri2 = 1f - abs(sw * 2f - 1f)
            beadSx = (-1f + 2f * SceneParts.ease(tri2)) * delN
            beadUy = ((fx(kit, a + beadSx * R) - l) / vs).coerceIn(-1.1f, 1.1f)
            beadOk = abs(beadUy) < epsN
            node(beadSx, beadUy, o)
            node(beadSx, 0f, q)
            val c = if (beadOk) SceneParts.ADDED else bad
            v = MathMesh.segment(line, v, o[0], o[1], o[2], q[0], q[1], q[2], c[0], c[1], c[2], 0.9f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the point everything is closing on ----------------------------------------------------
        node(0f, 0f, o)
        kit.ball(o[0], o[1], o[2], 0.034f, 0.034f, 0.034f, SceneParts.HOT, SceneParts.ADDED,
            1f, 0f, 0f, 1f, 0f, 0f, 1.5f + kit.beat)
        if (quality < 2) {
            node(beadSx, beadUy, o)
            val c = if (beadOk) SceneParts.ADDED else SceneParts.TAKEN
            kit.ball(o[0], o[1], o[2], 0.024f, 0.024f, 0.024f, c, SceneParts.HOT,
                1f, 0f, 0f, 1f, 0f, 0f, 1.2f)
        }

        // --- notation --------------------------------------------------------------------------------
        // Four names and nothing else. The numbers are on the HUD, where they are legible; up here
        // the glyphs only say which piece of the drawing is which.
        val gl = 0.17f
        node(1.14f, epsN * 0.5f, o)
        kit.text("ε", o[0], o[1], o[2], gl, SceneParts.COOL, 1f, GlyphBoard.Style.MATH, 1f, -0.5f)
        // δ goes INSIDE the window, hung on its right wall and riding inward with it. Beside or
        // within, never under: the caption box owns the bottom fifth of the eye, and at the
        // closest point of the pass a label slung below this figure would be sitting in it.
        // The heights are chosen so that at the TIGHTEST δ, when the window has come in as far as
        // it ever does, this glyph and the one naming a are still a clear glyph-height apart.
        node(delN - 0.05f, -0.45f, o)
        kit.text("δ", o[0], o[1], o[2], gl, SceneParts.WORK, 1f, GlyphBoard.Style.MATH, 1f, 0.5f)
        if (quality < 2) {
            node(-0.92f, 0.24f, o)
            kit.text("L", o[0], o[1], o[2], gl * 0.9f, SceneParts.CHALK, 0.85f, GlyphBoard.Style.MATH, 1f, -0.5f)
        }
        if (quality == 0) {
            node(0.08f, -0.98f, o)
            kit.text("a", o[0], o[1], o[2], gl * 0.8f, SceneParts.CHALK, 0.85f, GlyphBoard.Style.MATH, 1f, -0.5f)
        }
    }
}
