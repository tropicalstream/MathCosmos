package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * THE FOCUS — a point, a line, and the beads that stay the same distance from both.
 *
 * A parabola is normally introduced as y = x^2, which explains nothing: it says the curve is a
 * table of squares, and leaves you with no idea why anyone would care. The honest definition is
 * a rule about distances — pick a point F and a line d, and collect every point that is exactly
 * as far from F as it is from d — and that rule is a picture, not a formula, so it belongs out of
 * the window rather than in the narration.
 *
 * So the stop draws the rule and not the equation. Twelve beads sit on the curve, and every one
 * of them carries TWO struts: one slanting up to the focus, one dropping square to the directrix.
 * The pair is drawn in one hue, both struts the same colour, because the only thing a viewer has
 * to notice is that the two are the same length — at the vertex where both are short, and out at
 * the arms where both are long and the slanted one has swung almost parallel to the drop. A
 * travelling brightener lights each pair in turn so the equality reads as a rhythm rather than a
 * static diagram.
 *
 * Why the picture is honest, and not two lengths fudged to match: the curve is sampled as
 * y = y_v + x^2/(4p) with the focus set p above the vertex and the directrix p below it. Then the
 * drop to the directrix is y - (y_v - p) = p + x^2/(4p), and the distance to the focus is
 * sqrt(x^2 + (x^2/(4p) - p)^2) = sqrt(x^4/(16p^2) + x^2/2 + p^2), which is the same p + x^2/(4p).
 * Both struts are drawn to their real endpoints and both labels are measured from the bead's drawn
 * position with an actual square root, so if the geometry ever drifted the two numbers would
 * disagree on screen rather than quietly agree in the code.
 *
 * The thirteenth bead is dragged along the curve by the arm, its two struts stretching together,
 * and then once per cycle it is pulled straight DOWN off the curve. Down rather than up, and that
 * is not an aesthetic choice: pulling down shortens the drop to the directrix by exactly the pull
 * while lengthening the reach to the focus, so the two numbers run APART in opposite directions
 * and the struts visibly swap which is longer. Pulled upward they would both grow at nearly the
 * same rate near the rest point and the counterexample would read as a wobble. Off the curve both
 * struts go red. A rule that nothing could break is not a rule, and this is the only part of the
 * stop that proves the other twelve beads were earning their place.
 *
 * Composition: the whole construction is lifted so the craft threads the gap between the directrix
 * below and the vertex above. Nothing solid comes nearer than about half a unit — the only things
 * crossing the rail are the twelve vertical drop struts and the dashed axis, which are lines and
 * cost a viewer nothing to fly through.
 */
object SceneFocus : MathScene {

    override val reach = 1.4f

    // ---- the construction, in graph units ---------------------------------------------------
    // Everything below is multiplied by u0 (a quarter of the passage radius) to reach world
    // units, so the stop keeps its proportions if the passage is ever re-cut wider or narrower.
    private const val P = 1.15f              // focal length: vertex to focus, and vertex to directrix
    private const val Y_V = -0.55f           // the vertex
    private const val Y_F = Y_V + P          // the focus, p above it
    private const val Y_D = Y_V - P          // the directrix, p below it
    private const val X = 2.35f              // how far each arm of the curve runs
    private const val X_D = 2.60f            // the directrix overruns the arms, so it reads as a LINE
    /** Lift for the whole stage: puts the directrix 0.55 below the rail and the vertex 0.60 above. */
    private const val RISE = -Y_D - 0.55f

    private const val N = 12
    private const val PERIOD = 24f           // long enough that the drag is a crawl, not a twitch
    private const val DRAG_A = -2.16f        // the drag starts out on the left arm
    private const val DRAG_B = 1.88f         // and rests here, clear of the passage wall
    private const val OFF_DROP = 0.85f       // how far off the curve the counterexample is pulled

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val ca = FloatArray(3)
    private val cb = FloatArray(3)
    private val fo = FloatArray(3)           // the focus, in world; every strut ends here
    private val pt = FloatArray(3)
    private val qq = FloatArray(3)
    private val dp = FloatArray(3)           // the dragged bead, in world
    private val mp = FloatArray(3)

    // Rewritten in place every bead — a preallocated colour is not an allocation.
    private val hue = FloatArray(4)
    private val dragBase = FloatArray(4)

    // ---- the cycle, shared by draw() and readout() so the HUD cannot disagree with the scene --

    private fun phase(kit: SceneKit): Float = SceneParts.cycle(kit.seconds, PERIOD)

    /** Where the arm has dragged the thirteenth bead to, in graph x. */
    private fun dragX(c: Float): Float = DRAG_A + (DRAG_B - DRAG_A) * SceneParts.step(c, 0.02f, 0.50f)

    /** 0 on the curve, 1 fully pulled off it: up between 0.58 and 0.68, back down from 0.80. */
    private fun offAmount(c: Float): Float =
        SceneParts.step(c, 0.58f, 0.10f) - SceneParts.step(c, 0.80f, 0.10f)

    /** The parabola itself. The one line of arithmetic the whole stop rests on. */
    private fun curveY(x: Float): Float = Y_V + x * x / (4f * P)

    /** A graph point placed in the stage plane. Everything in the scene goes through here. */
    private fun place(x: Float, y: Float, u0: Float, out: FloatArray) =
        SceneParts.at(g, x * u0, y * u0, 0f, out)

    /**
     * The twelve hues, as a cosine wheel. The floor of 0.16 matters: a fully saturated blue or
     * violet all but vanishes through the waveguides, and a pair that cannot be seen cannot be
     * compared with the pair beside it, which is the entire job of the colour here.
     */
    private fun hueOf(t: Float, bright: Float, out: FloatArray) {
        val a = t * 6.2831853f
        out[0] = (0.58f + 0.42f * cos(a)) * bright
        out[1] = (0.58f + 0.42f * cos(a - 2.0943952f)) * bright
        out[2] = (0.58f + 0.42f * cos(a - 4.1887903f)) * bright
        out[3] = 1f
    }

    override fun readout(kit: SceneKit): String? {
        val c = phase(kit)
        val x = dragX(c)
        val y = curveY(x) - offAmount(c) * OFF_DROP
        val toF = sqrt(x * x + (y - Y_F) * (y - Y_F))
        val toD = y - Y_D
        return String.format(Locale.US, "TO FOCUS %.1f   TO LINE %.1f", toF, toD)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        val u0 = kit.radius(at) * 0.25f
        // The stage stands square to the rail and is lifted by RISE, so the craft passes through
        // the empty band between the directrix and the vertex rather than through the beads.
        SceneParts.stage(kit, at, 0f, RISE * u0, f, g)

        val c = phase(kit)
        val dx = dragX(c)
        val off = offAmount(c)
        val dy = curveY(dx) - off * OFF_DROP
        place(0f, Y_F, u0, fo)
        place(dx, dy, u0, dp)

        val q = kit.quality
        val buf = kit.lineBuf
        var v = 0

        // ---- the twelve pairs, thin ---------------------------------------------------------
        // One loop, because kit.ball never touches the line buffer: the bead and its two struts
        // are built together and the hue is computed once for all three.
        val count = if (q == 0) N else if (q == 1) N / 2 else 0
        val bead = u0 * 0.075f
        for (k in 0 until count) {
            val x = -X + 2f * X * (k + 0.5f) / count
            place(x, curveY(x), u0, pt)
            place(x, Y_D, u0, qq)
            // The brightener rides on the dragged bead: the arm lights each pair as it passes.
            val lit0 = (1f - abs(x - dx) / (X * 0.45f)).coerceIn(0f, 1f)
            val lit = lit0 * lit0
            hueOf(k.toFloat() / count, 0.55f + 0.45f * lit, hue)
            val al = 0.42f + 0.52f * lit
            // Strut one: to the focus. Strut two: square down to the directrix. Same colour,
            // and — this is the stop — the same length.
            v = MathMesh.segment(
                buf, v, pt[0], pt[1], pt[2], fo[0], fo[1], fo[2],
                hue[0], hue[1], hue[2], al
            )
            v = MathMesh.segment(
                buf, v, pt[0], pt[1], pt[2], qq[0], qq[1], qq[2],
                hue[0], hue[1], hue[2], al
            )
            kit.ball(
                pt[0], pt[1], pt[2], bead, bead, bead, hue, SceneParts.HOT,
                1f, 0f, 0f, 1f, 0f, 0f, 0.35f + 0.9f * lit
            )
        }

        // The axis of the parabola, dashed from the focus down through the vertex to the
        // directrix: it shows at a glance that the vertex sits halfway between the two, which is
        // the one bead of the twelve you can check by eye without measuring anything.
        if (q == 0) {
            place(0f, Y_D, u0, qq)
            v = MathMesh.dashed(
                buf, v, fo[0], fo[1], fo[2], qq[0], qq[1], qq[2], 9,
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.28f
            )
        }

        // The arm's tether, faint at the hull and bright where it holds the bead. kit.reach is
        // zero unless the crew are actually reaching, so the scene simply has no tether when the
        // craft is not touching it.
        if (kit.reach > 0.01f) {
            v = MathMesh.segment(
                buf, v, kit.shipX, kit.shipY, kit.shipZ, dp[0], dp[1], dp[2],
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2],
                0.02f, 0.55f * kit.reach
            )
        }
        kit.flushLines(v, 2.0f)

        // ---- the curve, the line, and the dragged pair, thick -------------------------------
        // A second pass rather than a second width on the first: the line buffer is uploaded at
        // flush time, so refilling it from zero costs one more draw call and nothing else, and
        // the two things a viewer must not lose track of get to be twice as heavy as the rest.
        v = 0
        place(-X_D, Y_D, u0, pt)
        place(X_D, Y_D, u0, qq)
        v = MathMesh.segment(
            buf, v, pt[0], pt[1], pt[2], qq[0], qq[1], qq[2],
            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.95f
        )
        val nSeg = if (q == 0) 96 else 48
        v = MathMesh.curve(
            buf, v, nSeg, -X, X,
            SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.95f, false, ca, cb
        ) { t, out -> SceneParts.at(g, t * u0, curveY(t) * u0, 0f, out) }

        // The dragged bead's own two struts. Off the curve they go red and unequal; that is the
        // whole reason the other twelve are worth looking at.
        for (j in 0 until 4) {
            dragBase[j] = SceneParts.HOT[j] + (SceneParts.TAKEN[j] - SceneParts.HOT[j]) * off
        }
        place(dx, Y_D, u0, qq)
        v = MathMesh.segment(
            buf, v, dp[0], dp[1], dp[2], fo[0], fo[1], fo[2],
            dragBase[0], dragBase[1], dragBase[2], 1f
        )
        v = MathMesh.segment(
            buf, v, dp[0], dp[1], dp[2], qq[0], qq[1], qq[2],
            dragBase[0], dragBase[1], dragBase[2], 1f
        )
        // A square corner at the foot, so "drops square to the line" is something you can see
        // rather than something the narration asserts.
        if (q == 0) {
            val t = u0 * 0.11f
            val sgn = if (dx < 0f) -t else t
            val ax = g[3] * sgn; val ay = g[4] * sgn; val az = g[5] * sgn
            val ux = g[6] * t; val uy = g[7] * t; val uz = g[8] * t
            v = MathMesh.segment(
                buf, v, qq[0] + ax, qq[1] + ay, qq[2] + az,
                qq[0] + ax + ux, qq[1] + ay + uy, qq[2] + az + uz,
                dragBase[0], dragBase[1], dragBase[2], 0.8f
            )
            v = MathMesh.segment(
                buf, v, qq[0] + ux, qq[1] + uy, qq[2] + uz,
                qq[0] + ax + ux, qq[1] + ay + uy, qq[2] + az + uz,
                dragBase[0], dragBase[1], dragBase[2], 0.8f
            )
        }
        kit.flushLines(v, 3.2f)

        // ---- the two beads that are not on the curve by accident ----------------------------
        val fr = u0 * 0.13f
        kit.ball(
            fo[0], fo[1], fo[2], fr, fr, fr, SceneParts.HOT, SceneParts.LAMP,
            1f, 0f, 0f, 1f, 0f, 0f, 0.9f + 0.7f * kit.beat, false
        )
        val dr = u0 * 0.10f
        kit.ball(
            dp[0], dp[1], dp[2], dr, dr, dr, dragBase, SceneParts.HOT,
            1f, 0f, 0f, 1f, 0f, 0f, 1.1f
        )

        // ---- notation -----------------------------------------------------------------------
        // Only the two lengths, set on the two struts they measure. Numbers are what settle an
        // argument about whether two lines are the same length; the geometry is what makes anyone
        // ask. The equation of the curve is deliberately absent — it is not what this stop is for.
        val glyph = u0 * 0.30f
        if (q < 2) {
            val toF = sqrt(dx * dx + (dy - Y_F) * (dy - Y_F))
            val toD = dy - Y_D
            mid(dp, fo, mp)
            kit.text(
                String.format(Locale.US, "%.1f", toF), mp[0], mp[1], mp[2],
                glyph * 1.15f, dragBase, 0.95f, GlyphBoard.Style.PLAIN, 1.2f, rise = 0.6f
            )
            mid(dp, qq, mp)
            kit.text(
                String.format(Locale.US, "%.1f", toD), mp[0], mp[1], mp[2],
                glyph * 1.15f, dragBase, 0.95f, GlyphBoard.Style.PLAIN, 1.2f,
                anchor = -0.5f, rise = 0.1f
            )
        }
        // Naming the two things the rule is about. Secondary, so it goes first when the governor
        // steps the scene down.
        if (q == 0) {
            kit.text(
                "F", fo[0], fo[1], fo[2], glyph * 1.1f, SceneParts.HOT, 0.95f,
                GlyphBoard.Style.MATH, 1.2f, rise = 0.95f
            )
            place(X_D * 0.66f, Y_D, u0, pt)
            kit.text(
                "directrix", pt[0], pt[1], pt[2], glyph * 0.8f, SceneParts.COOL, 0.8f,
                GlyphBoard.Style.SMALL, 0.9f, rise = -0.95f
            )
        }
    }

    /** Midpoint of a strut, where its label sits. */
    private fun mid(a: FloatArray, b: FloatArray, out: FloatArray) {
        out[0] = (a[0] + b[0]) * 0.5f
        out[1] = (a[1] + b[1]) * 0.5f
        out[2] = (a[2] + b[2]) * 0.5f
    }
}
