package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tour II, stop 1 — THE MACHINE. "A function is a rule: one number goes in, exactly one comes out."
 *
 * The first stop of the calculus tour has to establish the one thing the next twelve stops all
 * lean on: the roof of this corridor is a FUNCTION, and a function is a machine with an input lane
 * and a single output. So the stop is built as a piece of plant. A ruled lane runs along the floor
 * to one side — that is the domain, and the ticks on it are the evenly spaced x. Beads travel it
 * forward, pass through a gate arch spanning the corridor, and on the far side each one climbs to
 * the height the roof has at its own x and rides along beside the trace. The vertical hairline
 * from a risen bead down to its tick is the whole idea in one stroke: this x, that height.
 *
 * Three things are worth stating about the staging.
 *
 *  - The general rule in this app is that a flat figure goes to one side, because a figure centred
 *    on the rail is one you fly into and never see whole. The lane obeys it (side -1.15). The gate
 *    does not, and must not: a gate is a thing you pass THROUGH, and flying through the aperture at
 *    the moment the stop is alongside is the best half-second the stop has. Its feet sit 2.0 units
 *    from the rail, comfortably inside the 0.8-of-radius line even where the passage starts to
 *    close toward the throat at stop 2.
 *
 *  - The risen beads run beside the trace at exactly its height rather than merging into it. A bead
 *    sitting on the ribbon is a bead you cannot see against the ribbon, and the pairing with its
 *    tick on the floor is the thing that has to stay legible. The crew can say "it joins the curve";
 *    the picture says it by matching the height, one bright dotted line beside one bright ribbon.
 *
 *  - The arms do not catch the held bead, because Tour II's armStops schedule has no entry at this
 *    stop and inventing one here would desynchronise the probes from the script. The machine holds
 *    the bead in its own jaws instead, which says the same thing and stays honest about who is
 *    doing the holding.
 *
 * The roof over these ten units is a gentle rise — about a twentieth of a unit of height per unit
 * along — because that is genuinely what this tour's trace does near its start. The beads are not
 * given an exaggerated ramp to make the point louder; what makes "its own height" readable is the
 * ladder of hairlines under them, whose tops climb visibly even when the slope is shallow.
 */
object SceneMachine : MathScene {

    override val reach = 1.4f
    override val deep = 0.4f

    // ---------------------------------------------------------------- the plant

    /** World units between stops on these rails. Only sets how much of the roof's wave the lane
     *  samples, so a fixed figure is honest: the height itself is read from the trace function. */
    private const val NODE = 16.2f

    private const val LANE_S = -1.15f      // the input lane, off to one side as a flat figure should be
    private const val LANE_U = -0.95f      // and below the rail, so the craft never flies through it
    private const val LANE_A0 = -5.0f      // where a bead enters
    private const val LANE_A1 = 5.0f       // and where it fades out ahead
    private const val LANE_STEP = 2.0f     // the spacing of the ticks: this stop's Δx
    private const val RISE_A = 2.2f        // how far past the gate a bead takes to reach its height

    private const val ARCH_HW = 1.70f      // the gate: a half-ellipse across the passage
    private const val ARCH_BASE = -1.05f
    private const val ARCH_H = 2.25f
    private const val GATE_D = 0.20f       // half the gate's depth, so it has thickness in stereo

    /** The most of the passage radius the roof is allowed to reach — the ambient trace's own lid.
     *  Over these ten units the corridor is still wide and the two never differ; the clamp exists
     *  only so a re-tuned trace, or a tighter stop, can never hang a bead inside the wall. */
    private const val LID = 0.80f

    private const val PERIOD = 26f
    private const val BEAD = 0.085f
    private const val PI = 3.14159265f

    // The demonstration bead's script, as fractions of the loop. It slides in, is caught in
    // mid-rise, is held long enough to be looked at, and then finishes the climb AT THE SAME x —
    // which is the only way "it resumes to exactly the same height" is a checkable claim.
    private const val IN_AT = 0.06f
    private const val IN_END = 0.30f
    private const val HOLD_END = 0.44f
    private const val REL_END = 0.55f
    private const val RIDE_END = 0.66f
    private const val HOLD_A = 1.0f        // caught here: 43% of the way up
    private const val GAP = 0.90f          // how far the second, illegal height sits below the first

    // The bead that would need two heights.
    private const val REJ_AT = 0.72f
    private const val REJ_SPLIT = 0.84f
    private const val REJ_FALL = 0.90f
    private const val REJ_END = 0.96f
    private const val REJ_A = 1.6f

    // ------------------------------------------------------------------ scratch
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val o2 = FloatArray(3)
    private val o3 = FloatArray(3)
    private val o4 = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)
    /** The demonstration bead, resolved once per frame: lane offset, rise fraction, alpha. */
    private val demo = FloatArray(3)

    // ------------------------------------------------------------------ the rule

    /** The roof's height above the rail [a] world units along the corridor from the stop. */
    private fun roofU(kit: SceneKit, base: Float, a: Float): Float {
        val p = base + a / NODE
        val h = kit.traceHeight(p)
        val lid = LID * kit.radius(p)
        return if (h > lid) lid else h
    }

    /** Where a bead sits vertically: on the lane before the gate, climbing to the roof after it. */
    private fun beadU(kit: SceneKit, base: Float, a: Float, rise: Float): Float =
        LANE_U + (roofU(kit, base, a) - LANE_U) * rise

    /** The natural rise of a bead that has simply been carried through: nothing before the gate. */
    private fun freeRise(a: Float): Float = SceneParts.ease(a / RISE_A)

    /** Stream bead [k] of [n], as a lane parameter 0..1. Two traversals per loop, evenly phased. */
    private fun streamT(c: Float, k: Int, n: Int): Float = (c * 2f + k.toFloat() / n) % 1f

    /**
     * The demonstration bead into [demo]: lane offset, rise fraction, alpha. Absent outside its
     * window, which is when the two-valued candidate has the gate to itself.
     */
    private fun resolveDemo(c: Float) {
        demo[2] = 0f
        if (c < IN_AT || c >= RIDE_END) return
        val caught = freeRise(HOLD_A)
        when {
            c < IN_END -> {
                demo[0] = LANE_A0 + (HOLD_A - LANE_A0) * SceneParts.ease((c - IN_AT) / (IN_END - IN_AT))
                demo[1] = freeRise(demo[0])
            }
            c < HOLD_END -> { demo[0] = HOLD_A; demo[1] = caught }
            c < REL_END -> {
                demo[0] = HOLD_A
                demo[1] = caught + (1f - caught) * SceneParts.ease((c - HOLD_END) / (REL_END - HOLD_END))
            }
            else -> {
                demo[0] = HOLD_A + (LANE_A1 - HOLD_A) * ((c - REL_END) / (RIDE_END - REL_END))
                demo[1] = 1f
            }
        }
        demo[2] = SceneParts.step(c, IN_AT, 0.03f) * (1f - SceneParts.step(c, RIDE_END - 0.07f, 0.07f))
    }

    /** The rejected candidate's lane offset. It rides in normally; it is the gate that refuses it. */
    private fun rejectA(c: Float): Float =
        if (c < REJ_SPLIT) LANE_A0 + (REJ_A - LANE_A0) * SceneParts.ease((c - REJ_AT) / (REJ_SPLIT - REJ_AT))
        else REJ_A

    // ----------------------------------------------------------------- the readout

    private fun d2(v: Float): String {
        val n = (abs(v) * 100f + 0.5f).toInt()
        val fr = n % 100
        return (if (v < 0f) "-" else "") + (n / 100) + "." + (fr / 10) + (fr % 10)
    }

    /**
     * The pairing, in the domain's own ruling: x counted in ticks either side of the gate, and the
     * height the machine gives it. The renderer shows this for whichever stop was last departed, so
     * the stop index it needs is the same floor of the progress the HUD itself takes.
     */
    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val base = kit.progress.toInt().coerceAtLeast(0).toFloat()
        if (c >= REJ_SPLIT && c < REJ_END) {
            return "x = " + d2(REJ_A / LANE_STEP) + "   TWO HEIGHTS — REJECTED"
        }
        resolveDemo(c)
        val a = if (demo[2] > 0.01f) demo[0] else 0f
        val held = c >= HOLD_END - 0.14f && c < HOLD_END && demo[2] > 0.01f
        return (if (held) "HELD   " else "ONE IN, ONE OUT   ") +
            "x = " + d2(a / LANE_STEP) + "   f(x) = " + d2(roofU(kit, base, a))
    }

    // -------------------------------------------------------------------- drawing

    /** A point on the gate's half-ellipse, [scale] of full size, [aOff] fore or aft of the plane. */
    private fun archPt(ang: Float, aOff: Float, scale: Float, out: FloatArray) {
        SceneParts.at(g, ARCH_HW * scale * cos(ang), ARCH_BASE + ARCH_H * scale * sin(ang), aOff, out)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Anchored on the rail centre, not off to one side: the gate spans the passage and the lane
        // is offset from here, so both come out of one frame and one set of offsets.
        SceneParts.stage(kit, i.toFloat(), 0f, 0f, f, g)
        val base = i.toFloat()

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val q = kit.quality
        val streamN = if (q == 0) 5 else if (q == 1) 3 else 2
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val rejecting = c >= REJ_SPLIT && c < REJ_END
        // A fast flicker for the illegal bead and the gate that is refusing it. Deliberately faster
        // than anything else in the scene, so "wrong" reads before any label is legible.
        val flick = 0.55f + 0.45f * sin(kit.seconds * 21f)

        // --- the input lane: the domain, ruled -------------------------------------------------
        SceneParts.at(g, LANE_S, LANE_U, LANE_A0, o)
        SceneParts.vec(g, 0f, 0f, LANE_A1 - LANE_A0, du)
        SceneParts.vec(g, 0f, 1f, 0f, dv)
        v = MathMesh.arrow(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.85f, 0.07f
        )
        // Ticks on the multiples of Δx, so one of them lands exactly under the gate: the input the
        // machine is working on at this instant has a mark of its own on the floor.
        for (k in -2..2) {
            val t = k * LANE_STEP
            SceneParts.at(g, LANE_S - 0.13f, LANE_U, t, o)
            SceneParts.at(g, LANE_S + 0.13f, LANE_U, t, o2)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2],
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], if (k == 0) 0.95f else 0.6f)
        }

        // --- the gate ---------------------------------------------------------------------------
        // Two rings and a few struts. One ring reads as a hoop painted on nothing; two rings a hand's
        // breadth apart read as a doorway, and on a stereoscopic display that difference is large.
        val rings = if (q == 0) 2 else 1
        val segs = if (q == 0) 18 else 12
        val gc = if (rejecting) SceneParts.TAKEN else SceneParts.STEEL
        val ga = if (rejecting) 0.55f + 0.45f * flick else 0.80f
        SceneParts.vec(g, ARCH_HW, 0f, 0f, du)
        SceneParts.vec(g, 0f, ARCH_H, 0f, dv)
        for (r in 0 until rings) {
            val aOff = if (rings == 1) 0f else if (r == 0) -GATE_D else GATE_D
            SceneParts.at(g, 0f, ARCH_BASE, aOff, o)
            v = MathMesh.arc(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                1f, 0f, PI, segs, gc[0], gc[1], gc[2], ga
            )
            SceneParts.at(g, -ARCH_HW, ARCH_BASE, aOff, o)
            SceneParts.at(g, ARCH_HW, ARCH_BASE, aOff, o2)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2], gc[0], gc[1], gc[2], ga * 0.7f)
        }
        if (q == 0) {
            for (k in 0..4) {
                val ang = k * (PI * 0.25f)
                archPt(ang, -GATE_D, 1f, o)
                archPt(ang, GATE_D, 1f, o2)
                v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2], gc[0], gc[1], gc[2], ga * 0.6f)
            }
        }

        // --- the aperture ------------------------------------------------------------------------
        // A thin band just inside the arch, in the triangle buffer, so the gate has a mouth rather
        // than an outline. It is also the machine's one channel for saying yes or no: it carries the
        // beat while the plant is running, and goes red the moment the gate refuses something.
        if (q < 2) {
            val nb = if (q == 0) 14 else 10
            val ac = if (rejecting) SceneParts.TAKEN else SceneParts.COOL
            val aa = if (rejecting) 0.55f * flick + 0.35f else 0.20f + 0.16f * kit.beat
            for (k in 0 until nb) {
                val a0 = PI * k / nb
                val a1 = PI * (k + 1) / nb
                archPt(a0, 0f, 0.97f, o)
                archPt(a1, 0f, 0.97f, o2)
                archPt(a1, 0f, 0.86f, o3)
                archPt(a0, 0f, 0.86f, o4)
                tv[0] = MathMesh.vertex(tri, tv[0], o[0], o[1], o[2], ac[0], ac[1], ac[2], aa)
                tv[0] = MathMesh.vertex(tri, tv[0], o2[0], o2[1], o2[2], ac[0], ac[1], ac[2], aa)
                tv[0] = MathMesh.vertex(tri, tv[0], o3[0], o3[1], o3[2], ac[0], ac[1], ac[2], aa)
                tv[0] = MathMesh.vertex(tri, tv[0], o[0], o[1], o[2], ac[0], ac[1], ac[2], aa)
                tv[0] = MathMesh.vertex(tri, tv[0], o3[0], o3[1], o3[2], ac[0], ac[1], ac[2], aa)
                tv[0] = MathMesh.vertex(tri, tv[0], o4[0], o4[1], o4[2], ac[0], ac[1], ac[2], aa)
            }
        }

        // --- the hairlines: one x, one height ----------------------------------------------------
        // The single most important marks in the stop. Everything else is plant; this is the claim.
        if (q < 2) {
            val dash = if (q == 0) 5 else 3
            for (k in 0 until streamN) {
                val lt = streamT(c, k, streamN)
                val a = LANE_A0 + (LANE_A1 - LANE_A0) * lt
                if (a < 0.25f) continue
                val u = beadU(kit, base, a, freeRise(a))
                SceneParts.at(g, LANE_S, u - BEAD, a, o)
                SceneParts.at(g, LANE_S, LANE_U, a, o2)
                v = MathMesh.dashed(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2], dash,
                    SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.42f * MathMesh.taper(lt))
            }
        }

        // --- the bead that is caught -------------------------------------------------------------
        resolveDemo(c)
        if (demo[2] > 0.01f) {
            val a = demo[0]
            val u = beadU(kit, base, a, demo[1])
            if (a > 0.25f && q < 2) {
                SceneParts.at(g, LANE_S, u - BEAD, a, o)
                SceneParts.at(g, LANE_S, LANE_U, a, o2)
                v = MathMesh.dashed(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2], 5,
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.70f * demo[2])
            }
            // While it is held, the height it is going to reach is marked out ahead of it and the
            // remaining climb is drawn. Without that mark, "it resumes to exactly the same height"
            // is something the crew asserts; with it, the viewer checks it.
            if (c >= IN_END && c < REL_END) {
                val target = roofU(kit, base, HOLD_A)
                SceneParts.at(g, LANE_S - 0.28f, target, a, o)
                SceneParts.at(g, LANE_S + 0.28f, target, a, o2)
                val pulse = 0.55f + 0.35f * sin(kit.seconds * 3.2f)
                v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2],
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], pulse)
                SceneParts.at(g, LANE_S, u + BEAD, a, o)
                SceneParts.at(g, LANE_S, target, a, o2)
                v = MathMesh.dashed(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2], 3,
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], 0.45f)
                // The jaws. They flank the bead rather than cap it, so nothing is hidden at exactly
                // the moment the height is the thing being watched.
                if (c < HOLD_END) {
                    val grip = SceneParts.step(c, IN_END - 0.04f, 0.06f) * (1f - SceneParts.step(c, HOLD_END - 0.03f, 0.03f))
                    val reachMix = 0.55f + 0.45f * kit.reach
                    for (s in 0..1) {
                        val side = if (s == 0) -1f else 1f
                        val off = LANE_S + side * (0.42f - 0.18f * grip)
                        SceneParts.at(g, off, u - 0.16f, a, o)
                        SceneParts.at(g, off, u + 0.16f, a, o2)
                        v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2],
                            SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.9f * grip * reachMix)
                        SceneParts.at(g, off, u, a, o)
                        SceneParts.at(g, LANE_S + side * BEAD, u, a, o2)
                        v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2],
                            SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.7f * grip * reachMix)
                    }
                }
            }
        }

        // --- the candidate that needs two heights ------------------------------------------------
        // One x, two outputs, drawn as two beads on ONE vertical. That vertical is the reason the
        // hairlines exist at all: every legal bead has one, and this one has two beads on it.
        if (c >= REJ_AT && c < REJ_END) {
            val a = rejectA(c)
            val top = roofU(kit, base, a)
            val split = if (c < REJ_SPLIT) 0f else SceneParts.step(c, REJ_SPLIT, 0.05f)
            val fall = SceneParts.step(c, REJ_FALL, REJ_END - REJ_FALL)
            val ru = if (c < REJ_SPLIT) beadU(kit, base, a, freeRise(a)) else top
            val uHi = ru + (LANE_U - ru) * fall
            val uLo = (ru - GAP * split) + (LANE_U - (ru - GAP * split)) * fall
            // Cool while it is only a bead being carried in; red the instant the gate finds two
            // heights on it. Colouring it wrong before anything has gone wrong gives the game away.
            if (a > 0.25f && q < 2) {
                val hc = if (split > 0.02f) SceneParts.TAKEN else SceneParts.COOL
                SceneParts.at(g, LANE_S, uLo, a, o)
                SceneParts.at(g, LANE_S, LANE_U, a, o2)
                v = MathMesh.dashed(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2], 5,
                    hc[0], hc[1], hc[2], if (split > 0.02f) 0.75f * flick else 0.42f)
            }
            if (split > 0.02f) {
                SceneParts.at(g, LANE_S, uLo, a, o)
                SceneParts.at(g, LANE_S, uHi, a, o2)
                v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2],
                    SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], flick)
            }
        }

        // --- the ruling's own name, beside the lane ----------------------------------------------
        if (q == 0) {
            SceneParts.at(g, LANE_S - 0.30f, LANE_U, -2f * LANE_STEP, o)
            SceneParts.at(g, LANE_S - 0.30f, LANE_U, -LANE_STEP, o2)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], o2[0], o2[1], o2[2],
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.45f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the beads --------------------------------------------------------------------------
        // One draw call each, so the count is the budget. Five in the stream at full detail is
        // enough to show two or three on the floor and two or three already up on the curve, which
        // is the picture: the same beads, before and after.
        for (k in 0 until streamN) {
            val lt = streamT(c, k, streamN)
            val a = LANE_A0 + (LANE_A1 - LANE_A0) * lt
            val rise = freeRise(a)
            val u = beadU(kit, base, a, rise)
            SceneParts.at(g, LANE_S, u, a, o)
            // Cool on the floor, and the trace's own warm colour once it has been given a height:
            // a bead changes colour by being evaluated.
            val bc = if (rise > 0.5f) SceneParts.HOT else SceneParts.COOL
            kit.ball(o[0], o[1], o[2], BEAD, BEAD, BEAD, bc, SceneParts.CHALK,
                MathMesh.taper(lt), 0f, 0f, 1f, 0f, 0f, 0.5f + 0.6f * rise)
        }

        if (demo[2] > 0.01f) {
            val a = demo[0]
            val u = beadU(kit, base, a, demo[1])
            SceneParts.at(g, LANE_S, u, a, o)
            kit.ball(o[0], o[1], o[2], BEAD * 1.25f, BEAD * 1.25f, BEAD * 1.25f,
                SceneParts.ADDED, SceneParts.HOT, demo[2], 0f, 0f, 1f, 0f, 0f, 1.4f)
            // The seat: one brief lamp where it arrives, so the moment it reaches its height is an
            // event and not merely the end of a movement.
            val seat = SceneParts.step(c, REL_END - 0.03f, 0.03f) * (1f - SceneParts.step(c, REL_END, 0.05f))
            if (seat > 0.02f) {
                kit.ball(o[0], o[1], o[2], 0.10f, 0.10f, 0.10f, SceneParts.HOT, SceneParts.ADDED,
                    seat, 0f, 0f, 1f, 0f, 0f, 3.5f * seat)
            }
        }

        if (c >= REJ_AT && c < REJ_END) {
            val a = rejectA(c)
            val top = roofU(kit, base, a)
            val split = if (c < REJ_SPLIT) 0f else SceneParts.step(c, REJ_SPLIT, 0.05f)
            val fall = SceneParts.step(c, REJ_FALL, REJ_END - REJ_FALL)
            val ru = if (c < REJ_SPLIT) beadU(kit, base, a, freeRise(a)) else top
            val alpha = SceneParts.step(c, REJ_AT, 0.03f) * (1f - fall)
            val hot = if (c < REJ_SPLIT) SceneParts.COOL else SceneParts.TAKEN
            SceneParts.at(g, LANE_S, ru + (LANE_U - ru) * fall, a, o)
            kit.ball(o[0], o[1], o[2], BEAD * 1.1f, BEAD * 1.1f, BEAD * 1.1f, hot, SceneParts.CHALK,
                alpha, 0f, 0f, 1f, 0f, 0f, 1.2f * flick)
            if (split > 0.02f) {
                val lo = ru - GAP * split
                SceneParts.at(g, LANE_S, lo + (LANE_U - lo) * fall, a, o)
                kit.ball(o[0], o[1], o[2], BEAD * 1.1f, BEAD * 1.1f, BEAD * 1.1f,
                    SceneParts.TAKEN, SceneParts.CHALK, alpha * split, 0f, 0f, 1f, 0f, 0f, 1.2f * flick)
            }
        }

        // --- notation ----------------------------------------------------------------------------
        // All of it outboard, beside the thing it names. The HUD owns the top of the eye and the
        // caption box the bottom, so a glyph above or below a figure is a glyph nobody reads.
        SceneParts.at(g, 1.95f, 0.20f, 0f, o)
        kit.text("f", o[0], o[1], o[2], 0.26f, SceneParts.HOT, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f)

        SceneParts.at(g, LANE_S - 0.42f, LANE_U, LANE_A1 - 0.4f, o)
        kit.text("x", o[0], o[1], o[2], 0.21f, SceneParts.COOL, 0.9f, GlyphBoard.Style.MATH, 1f, anchor = 0.5f)

        // Inboard of the risen beads rather than outboard of them. Out there the passage wall is
        // only a little over two and a half units away at roof height and a four-glyph label runs
        // straight into it; in here there is room, and the label sits in the gap between the bead
        // line and the ribbon, which is exactly the gap it is naming.
        SceneParts.at(g, LANE_S + 0.63f, roofU(kit, base, 3.0f), 3.0f, o)
        kit.text("f(x)", o[0], o[1], o[2], 0.21f, SceneParts.HOT, 0.9f, GlyphBoard.Style.MATH, 1f, anchor = 0.5f)

        if (q == 0) {
            SceneParts.at(g, LANE_S - 0.40f, LANE_U, -1.5f * LANE_STEP, o)
            kit.text("Δx", o[0], o[1], o[2], 0.16f, SceneParts.CHALK, 0.8f, GlyphBoard.Style.SMALL, 1f, anchor = 0.5f)
        }

        // The refusal is posted on the gate, under its name, because it is the gate that is refusing
        // — and because eleven glyphs hung beside the split beads would reach into the wall.
        if (rejecting) {
            SceneParts.at(g, 1.95f, -0.30f, 0f, o)
            kit.text("TWO HEIGHTS", o[0], o[1], o[2], 0.17f, SceneParts.TAKEN,
                0.6f + 0.4f * flick, GlyphBoard.Style.PLAIN, 1f, anchor = 0.5f)
        }
    }
}
