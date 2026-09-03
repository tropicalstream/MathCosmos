package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Stop 4 — THE CHORD. "Slope is rise over run, and I can measure it with two touches."
 *
 * The Caliper puts one arm up to the roof at x and another at x + h, clamps both, and draws a
 * bright strut between the two contact points. That strut is the whole of average rate of change.
 * No limit is taken here: h stays at one whole unit — this stop's rung — and the next stop is the
 * one that shrinks it. Everything else in the scene exists so that the strut's tilt can be READ.
 *
 * The stop is built as two objects, and the reason is a scale problem worth stating plainly. In
 * this tour the input runs ALONG the rail: one unit of x is one node, sixteen world units, while
 * one unit of f is one world unit of roof height. The corridor is therefore the graph of f drawn
 * on paper stretched sixteen times horizontally, and a secant across a whole leg of it is a beam
 * that climbs about an eighth of a unit over sixteen — true, and unreadable as a slope. So the
 * measurement is drawn twice: once where it actually happens, up under the roof, and once beside
 * the rail as a GAUGE, at a scale where a unit of run and a unit of rise are the same length. The
 * gauge is not a projection of the strut and the crew does not pretend it is; it is the same two
 * numbers drawn again on square paper.
 *
 * The gauge keeps equal scales on its two legs even though that makes a thin wedge rather than the
 * tidy triangle a textbook draws — the roof is near its crest over this leg, so a run of one buys a
 * rise of about an eighth. The moment the vertical is exaggerated the drawn angle stops being the
 * number on the HUD, and this stop's entire content is that the picture and the number are one
 * measurement. A shallow slope drawn honestly is a shallow slope, and the creep at the end of the
 * loop is there to show it changing rather than to make it dramatic.
 *
 * Placement follows the usual rule where it can. The caliper itself stands on the corridor's centre
 * line because that is where the roof is: it is a thing you fly under and through, like the trace,
 * not a flat figure you fly into. The gauge IS a flat figure, so it hangs to one side and is under
 * two units across. The arms stand a little off the rail and lean inward, which keeps the craft
 * from flying through one and makes them read as arms rather than as two more of the ambient
 * trace's ruling ticks.
 */
object SceneChord : MathScene {

    // The far touch sits about three quarters of a node ahead of the stop, so the whole chord is
    // in front of the craft for the entire approach and is only half behind it at the pass.
    override val reach = 1.6f
    override val deep = 0.9f

    // ---- the measurement ----------------------------------------------------
    private const val H = 1f                // the run, in node units: the tour's rung here is h = 1
    private const val BASE = -0.90f         // where the near touch starts, relative to the stop
    private const val SLIDE = 0.65f         // how far the clamped caliper creeps forward in a loop
    private const val PERIOD = 24f
    private const val ARM_SIDE = -0.55f     // the arms stand this far off the rail and lean in
    private const val ARM_R = 0.05f
    private const val TIP_R = 0.075f

    // ---- the gauge, the readable copy at the side ---------------------------
    private const val SIDE = -1.35f
    private const val UP = 0.30f
    private const val RUN = 1.70f           // world units per unit of x AND per unit of f
    private const val GS = -0.85f           // the gauge's corner, in the stage plane
    private const val GU = -0.05f

    // The ambient trace's own squeeze, repeated here. See [roof].
    private const val CEIL = 0.80f
    private const val SOFT = 0.28f

    // Scratch. Nothing below allocates.
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val fN = FloatArray(12)         // rail frame at the near touch
    private val fF = FloatArray(12)         // rail frame at the far touch
    private val cN = FloatArray(3)          // the near contact, on the trace
    private val cF = FloatArray(3)          // the far contact
    private val lvl = FloatArray(3)         // level with the near contact, under the far one
    private val footN = FloatArray(3)
    private val footF = FloatArray(3)
    private val tip = FloatArray(3)
    private val a3 = FloatArray(3)          // the gauge's three corners
    private val b3 = FloatArray(3)
    private val c3 = FloatArray(3)
    private val o = FloatArray(3)

    /**
     * The roof height at [p] as the ambient ribbon actually DRAWS it, not as the tour's trace
     * function honestly returns it.
     *
     * SceneAmbientTrace presses the ribbon against the wall where the passage is tighter than the
     * curve, by a soft minimum on the magnitude, so that a trace two and a half units high survives
     * a corridor that has funnelled to 1.6. That squeeze is private to it, and this is a deliberate
     * copy: the two contact points have to land on the ribbon a viewer can see, and a chord whose
     * ends float a hand's breadth above the roof is a bug you cannot argue with. If the squeeze
     * there ever changes, this changes with it — there is nothing clever to do about that, and a
     * duplicated eight lines is cheaper than widening the kit for one stop.
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

    /** A point [up] above the rail centre of frame [fr], pushed [side] across it. */
    private fun place(fr: FloatArray, side: Float, up: Float, out: FloatArray) {
        out[0] = fr[0] + fr[6] * side + fr[9] * up
        out[1] = fr[1] + fr[7] * side + fr[10] * up
        out[2] = fr[2] + fr[8] * side + fr[11] * up
    }

    /** [t] of the way from [p] to [q]. How an arm extends and a strut draws itself in. */
    private fun lerp3(p: FloatArray, q: FloatArray, t: Float, out: FloatArray) {
        out[0] = p[0] + (q[0] - p[0]) * t
        out[1] = p[1] + (q[1] - p[1]) * t
        out[2] = p[2] + (q[2] - p[2]) * t
    }

    /**
     * Where the near touch is at cycle position [c]. Shared by draw and readout, so the HUD's
     * number and the strut's tilt are the same measurement and cannot drift apart.
     */
    private fun nearX(at: Float, c: Float): Float = at + BASE + SLIDE * SceneParts.step(c, 0.62f, 0.16f)

    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        // The renderer asks the FLOOR stop of the rail for its readout, so the craft is between
        // this stop and the next whenever this line is on screen, and the floor is our own index.
        val at = floor(kit.progress)
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // Nothing has been measured until both arms are down. Saying so is better than showing a
        // number the picture has not earned yet.
        if (SceneParts.step(c, 0.14f, 0.12f) < 0.99f) return "TWO TOUCHES:  x  AND  x + h"
        val x0 = nearX(at, c)
        val rise = roof(kit, x0 + H) - roof(kit, x0)
        return String.format(Locale.US, "Δf %.2f / h %.2f = %.2f", rise, H, rise / H)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Tours I, V and VI have no roof. A chord across a curve that is not there is a bright
        // strut lying along the rail, which is worse than an empty stop.
        if (!kit.hasTrace) return

        val at = i.toFloat()
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val armN = SceneParts.step(c, 0.05f, 0.12f)
        val armF = SceneParts.step(c, 0.14f, 0.12f)     // staggered: two touches, one then the other
        val sec = SceneParts.step(c, 0.29f, 0.10f)
        val runT = SceneParts.step(c, 0.41f, 0.07f)
        val riseT = SceneParts.step(c, 0.49f, 0.07f)
        val hypT = SceneParts.step(c, 0.55f, 0.08f)

        // Two frame queries for the whole scene. Each one builds a small object in the renderer,
        // and this stop is on screen for the length of a leg either side of itself.
        val x0 = nearX(at, c)
        val x1 = x0 + H
        kit.frame(x0, fN)
        kit.frame(x1, fF)
        val h0 = roof(kit, x0)
        val h1 = roof(kit, x1)
        val rise = h1 - h0
        // A rise can be a fall, and if it ever is here the debt colour says so without a caption.
        val riseCol = if (rise >= 0f) SceneParts.ADDED else SceneParts.TAKEN

        place(fN, 0f, h0, cN)
        place(fF, 0f, h1, cF)
        // The corner of the true triangle: level with the near contact, under the far one. Level
        // over a bending rail is not a straight line, so this is the far frame's own height h0 and
        // the run is drawn as the chord between the two. The rail turns a couple of degrees over a
        // node and the difference is well under a pixel.
        place(fF, 0f, h0, lvl)
        place(fN, ARM_SIDE, 0f, footN)
        place(fF, ARM_SIDE, 0f, footF)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0

        // --- the arms, and the clamp ---------------------------------------------------------
        // This stop is one of the tour's armStops, so the craft's own probes are out here; their
        // reach lights the arms rather than driving them, because the loop has to read the same
        // whether the viewer arrives with the probes out or stowed.
        val lit = 0.5f + 0.8f * kit.reach
        lerp3(footN, cN, armN, tip)
        kit.rod(footN[0], footN[1], footN[2], tip[0], tip[1], tip[2], ARM_R,
            SceneParts.STEEL, SceneParts.CHALK, lit * 0.6f)
        val flashN = armN * (1f - SceneParts.step(c, 0.17f, 0.10f))
        kit.ball(tip[0], tip[1], tip[2], TIP_R, TIP_R, TIP_R, SceneParts.HOT, SceneParts.WORK,
            1f, glow = 0.6f + 1.8f * flashN + 0.4f * kit.beat)

        lerp3(footF, cF, armF, tip)
        kit.rod(footF[0], footF[1], footF[2], tip[0], tip[1], tip[2], ARM_R,
            SceneParts.STEEL, SceneParts.CHALK, lit * 0.6f)
        val flashF = armF * (1f - SceneParts.step(c, 0.26f, 0.10f))
        kit.ball(tip[0], tip[1], tip[2], TIP_R, TIP_R, TIP_R, SceneParts.HOT, SceneParts.WORK,
            1f, glow = 0.6f + 1.8f * flashF + 0.4f * kit.beat)

        // --- the secant ------------------------------------------------------------------------
        // A rod, not a line: it is the one object at this stop with any mass to it, and it is
        // sixteen units long, so perspective does the tapering for free.
        if (sec > 0.02f) {
            lerp3(cN, cF, sec, tip)
            kit.rod(cN[0], cN[1], cN[2], tip[0], tip[1], tip[2], 0.038f,
                SceneParts.LAMP, SceneParts.HOT, 1.2f)
        }

        // --- the true triangle, hanging under the secant ---------------------------------------
        // Dashed for the run, because it is a construction line and not a thing that is there;
        // solid for the rise, because the rise is the measurement. The same two steps drive the
        // gauge below, so the two triangles build in lockstep and read as one act.
        if (runT > 0.02f) {
            lerp3(cN, lvl, runT, tip)
            v = MathMesh.dashed(line, v, cN[0], cN[1], cN[2], tip[0], tip[1], tip[2],
                if (kit.quality == 0) 12 else 6,
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.85f)
        }
        if (riseT > 0.02f) {
            lerp3(lvl, cF, riseT, tip)
            v = MathMesh.segment(line, v, lvl[0], lvl[1], lvl[2], tip[0], tip[1], tip[2],
                riseCol[0], riseCol[1], riseCol[2], 1f)
        }

        // --- the gauge ---------------------------------------------------------------------------
        SceneParts.stage(kit, at, SIDE, UP, f, g)
        val riseW = RUN / H * rise                  // equal scale on both legs: the angle IS the number
        SceneParts.at(g, GS, GU, 0f, a3)
        SceneParts.at(g, GS + RUN, GU, 0f, b3)
        SceneParts.at(g, GS + RUN, GU + riseW, 0f, c3)

        lerp3(a3, b3, runT, tip)
        v = MathMesh.segment(line, v, a3[0], a3[1], a3[2], tip[0], tip[1], tip[2],
            SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 1f)
        if (riseT > 0.02f) {
            lerp3(b3, c3, riseT, tip)
            v = MathMesh.segment(line, v, b3[0], b3[1], b3[2], tip[0], tip[1], tip[2],
                riseCol[0], riseCol[1], riseCol[2], 1f)
        }
        if (hypT > 0.02f) {
            // The gauge's hypotenuse is drawn in the secant's own colour. That shared colour is the
            // only thing tying the two objects together; a dashed leader line across ten units of
            // corridor was tried and it is clutter.
            SceneParts.at(g, GS + RUN * hypT, GU + riseW * hypT, 0f, tip)
            v = MathMesh.segment(line, v, a3[0], a3[1], a3[2], tip[0], tip[1], tip[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f)
        }

        if (kit.quality < 2) {
            // The gauge's dimension marks: two end ticks under the run, which are what say that the
            // run is one whole h and not some length that happened to fit, and a level cap at the
            // top of the rise, which is what makes an eighth of a unit legible at all.
            SceneParts.at(g, GS, GU - 0.09f, 0f, o)
            v = MathMesh.segment(line, v, a3[0], a3[1], a3[2], o[0], o[1], o[2],
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.7f)
            SceneParts.at(g, GS + RUN, GU - 0.09f, 0f, o)
            v = MathMesh.segment(line, v, b3[0], b3[1], b3[2], o[0], o[1], o[2],
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.7f)
            if (riseT > 0.5f) {
                SceneParts.at(g, GS + RUN - 0.20f, GU + riseW, 0f, o)
                v = MathMesh.segment(line, v, c3[0], c3[1], c3[2], o[0], o[1], o[2],
                    riseCol[0], riseCol[1], riseCol[2], 0.55f)
            }
        }

        kit.flushLines(v, 2.2f)

        // The wedge's face, faint. It is what turns three struts into a triangle at a glance.
        if (kit.quality < 2 && hypT > 0.02f && abs(riseW) > 0.004f) {
            var t = MathMesh.vertex(tri, 0, a3[0], a3[1], a3[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.16f)
            t = MathMesh.vertex(tri, t, b3[0], b3[1], b3[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.16f)
            t = MathMesh.vertex(tri, t, c3[0], c3[1], c3[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.16f)
            kit.flushTris(t)
        }

        // --- notation ------------------------------------------------------------------------
        // The quotient names the wedge; the two legs name themselves. Numbers live on the HUD.
        SceneParts.at(g, GS + RUN * 0.42f, GU + riseW * 0.42f + 0.19f, 0f, o)
        kit.text("Δf / h", o[0], o[1], o[2], 0.17f, SceneParts.HOT, 1f)

        if (kit.quality < 2) {
            SceneParts.at(g, GS + RUN * 0.5f, GU - 0.20f, 0f, o)
            kit.text("h", o[0], o[1], o[2], 0.16f, SceneParts.WORK, 1f)
            SceneParts.at(g, GS + RUN + 0.10f, GU + riseW * 0.5f, 0f, o)
            kit.text("Δf", o[0], o[1], o[2], 0.16f, riseCol, 1f, anchor = -0.5f)
        }

        // The two touches, named where they happen. Anchored so the glyphs hang outboard of the
        // arms rather than over the corridor the craft is about to fly down.
        if (kit.quality == 0) {
            place(fN, -0.30f, h0 + 0.16f, o)
            kit.text("x", o[0], o[1], o[2], 0.20f, SceneParts.CHALK, 0.9f, anchor = 0.5f)
            place(fF, -0.30f, h1 + 0.16f, o)
            kit.text("x + h", o[0], o[1], o[2], 0.20f, SceneParts.CHALK, 0.9f, anchor = 0.5f)
        }
    }
}
