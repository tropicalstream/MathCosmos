package com.rayneo.mathcosmos

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Stop 3 — THE HOLE. "Continuous means I can fly the whole way without lifting off."
 *
 * Two faults in one road, side by side, so they can be compared rather than remembered. A stretch
 * of the corridor's roof is brought down beside the rail and laid as a brick road: one brick is
 * MISSING, and further along the road STEPS. The arms reach in and try to bridge each in turn.
 * Over the hole their two tips converge on a single point and meet — one value repairs the road.
 * Over the step they reach past each other in different planes at different heights and never
 * touch, because no single value repairs a broken road. That is the whole of removable versus
 * jump, and it is a thing you can see with your hands.
 *
 * Both faults are on ONE road on purpose. The tour's script says "further along", and a first
 * draft put the two defects at two rail positions — but then you can only ever hold one of them
 * in view, and the stop is entirely about the difference between them. Laid on the same road they
 * are 0.7 units apart and the comparison is free.
 *
 * WHY BRICKS. The roof curve overhead is a smooth ribbon and the ambient scene owns it — this
 * scene must never draw it. The road is a separate object: the same function, sampled and laid in
 * courses. Bricks buy two things a smooth curve cannot. A missing brick is a countable absence
 * exactly one brick wide, rather than a suspiciously thin line; and a road made of pieces is a
 * thing you drive along, so "I cannot fly the whole way without lifting off" is about travel and
 * not about a graph. This is a metaphor and the crew says so: the roof is not really made of
 * bricks, it is being SAMPLED, and the bricks are the samples.
 *
 * The heights come from [SceneKit.traceHeight] so the road really is this corridor's roof, then
 * they are normalised into a fixed band. That scaling is the ordinary honesty of any plot — the
 * vertical is stretched to fill the paper — and it is also what keeps the scene legible if the
 * trace function is ever re-cut, or if the stop is hung on the proving ground where there is no
 * roof at all and a flat fallback curve stands in. The numbers that must be READ go to the HUD
 * via [readout] in the roof's own units, unscaled, where nothing has been done to them.
 *
 * The tour's armStops do not include this stop, so kit.reach is zero here and the scene brings its
 * own pair of fingers. They are drawn as two rods from a shoulder up near the rail, which is where
 * the Caliper's arms come from, and they read as the same gesture.
 */
object SceneHole : MathScene {

    override val reach = 1.5f

    // ------------------------------------------------------------- the road
    // The stop's passage radius is 2.8, so the budget is 2.24 from the rail. The far end of the
    // road sits at side -2.00 and 0.55 below, which is 2.07 out: comfortably clear of the wall,
    // and the near end stops 0.50 short of the rail so the craft does not fly through it.
    private const val SIDE = -1.25f
    private const val UP = -0.28f          // the road hangs below the rail, so you look down on it
    private const val HW = 0.75f           // half the road's width: a figure 1.5 across
    private const val DEPTH = 0.09f        // half the road's depth along the rail
    private const val THICK = 0.10f        // how far a brick hangs below the road surface
    private const val BAND = 0.34f         // the vertical span the roof's shape is scaled into
    private const val SPAN = 3.0f          // node units of corridor this road stands for
    private const val MORTAR = 0.20f       // fraction of a brick pitch left as joint

    /**
     * The step, in the ROOF's own height units rather than world units. Fixing it here rather
     * than on screen is what lets [readout] name it without knowing anything about the frame's
     * scaling, and it keeps the number on the HUD in the same units as the corridor's own roof.
     */
    private const val JUMP_F = 0.70f

    private const val X_HOLE = 0.30f       // where along the road the brick is out
    private const val X_CUT = 0.66f        // where along the road it breaks
    private const val MAX_B = 16

    // ------------------------------------------------------------- the loop
    // Reach, meet, seat, withdraw; reach, miss, withdraw; then five seconds of rest with both
    // faults sitting there to be looked at. A viewer arriving mid-pass sees the comparison either
    // way round, which is why neither half is allowed to be the one that only happens first.
    private const val PERIOD = 26f
    private const val A_REACH = 0.06f
    private const val A_SEAT = 0.32f
    private const val A_OUT = 0.44f
    private const val B_REACH = 0.54f
    private const val B_FAIL = 0.68f
    private const val B_OUT = 0.78f
    private const val TAU = 6.2831855f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val pA = FloatArray(3)
    private val pB = FloatArray(3)
    private val tv = IntArray(1)

    /** The roof height at each brick centre — raw on the way in, world height on the way out. */
    private val hs = FloatArray(MAX_B)

    // Built once at class-init rather than formatted per frame: the step never changes.
    private val JUMP_LINE = "STEP %.2f   NO δ SMALL ENOUGH".format(java.util.Locale.US, JUMP_F)

    /**
     * What this stop measures is not a height, it is whether one value would mend the road.
     *
     * Deliberately free of any number that depends on where the craft is, so it needs neither the
     * stop index nor any state carried out of [draw]: the scene stays a pure function of the clock.
     */
    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return when {
            c < A_SEAT + 0.08f -> "lim EXISTS   f(a) MISSING"
            c < 0.50f -> "f(a) := lim   ROAD WHOLE"
            c < B_OUT + 0.06f -> JUMP_LINE
            else -> "HOLE: ONE BRICK   STEP: NO REPAIR"
        }
    }

    /** [t] of the way from [a] to [b], into [out]. */
    private fun lerp3(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
        out[0] = a[0] + (b[0] - a[0]) * t
        out[1] = a[1] + (b[1] - a[1]) * t
        out[2] = a[2] + (b[2] - a[2]) * t
    }

    /**
     * One brick: the face you see, and the top you drive on. Two quads, no draw call of its own —
     * every brick in the road goes into the same triangle buffer and leaves in one flush.
     */
    private fun brick(tri: FloatArray, s0: Float, w: Float, u: Float, c: FloatArray, a: Float, top: Boolean) {
        SceneParts.at(g, s0, u - THICK, -DEPTH, o)
        SceneParts.vec(g, w, 0f, 0f, du)
        SceneParts.vec(g, 0f, THICK, 0f, dv)
        tv[0] = SceneParts.fill(tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], c, a)
        if (!top) return
        // The top face is what turns a bar chart into a road, and it is the first thing the
        // governor takes: at quality 2 the faces alone still read, from below, as a kerb.
        SceneParts.at(g, s0, u, -DEPTH, o)
        SceneParts.vec(g, 0f, 0f, 2f * DEPTH, dv)
        tv[0] = SceneParts.fill(
            tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            c, (a * 1.4f).coerceAtMost(1f)
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        SceneParts.stage(kit, at, SIDE, UP, f, g)

        val q = kit.quality
        // Twelve courses, nine when the governor bites. This is a loop over BUFFER WRITES and not
        // over draw calls — the whole road is one flush either way — so it is trimmed for the
        // vertex count and not for the call count, and it is trimmed gently: the missing brick has
        // to stay one countable brick wide at every quality.
        val nb = if (q == 0) 12 else if (q == 1) 10 else 9
        val hIdx = (X_HOLE * nb).toInt()
        val jIdx = (X_CUT * nb).toInt().coerceAtLeast(1)
        val pitch = 2f * HW / nb
        val bw = pitch * (1f - MORTAR)

        // --- the roof, sampled ----------------------------------------------------------------
        // Six node units of corridor squeezed into a metre and a half. Wide, because over one or
        // two nodes this tour's roof is nearly level and a level road has no shape to break.
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (k in 0 until nb) {
            val x = (k + 0.5f) / nb
            val p = at + (x - 0.5f) * 2f * SPAN
            // The fallback is for the proving ground and for any tour with no roof: without it the
            // road would be dead flat and the two faults would look like the only content, which
            // is exactly the misreading the stop cannot afford.
            val h = if (kit.hasTrace) kit.traceHeight(p) else 1.5f + 0.60f * sin(p * 0.7f)
            hs[k] = h
            if (h < lo) lo = h
            if (h > hi) hi = h
        }
        val fSpan = (hi - lo).coerceAtLeast(0.05f)
        // The step in world units. Clamped, because on a trace that happens to be nearly flat over
        // this window the scaling would blow a 0.7 rise up into a cliff taller than the figure.
        val jumpW = (JUMP_F / fSpan * BAND).coerceIn(0.14f, 0.26f)
        for (k in 0 until nb) {
            hs[k] = (hs[k] - lo) / fSpan * BAND - BAND * 0.5f + (if (k >= jIdx) jumpW else 0f)
        }

        val sHole = -HW + (hIdx + 0.5f) * pitch
        val sH0 = -HW + hIdx * pitch + pitch * MORTAR * 0.5f
        val uH = hs[hIdx]
        val cutS = -HW + jIdx * pitch
        val uL = hs[jIdx - 1]
        val uR = hs[jIdx]
        val uHi = max(uL, uR)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val reachA = SceneParts.step(c, A_REACH, 0.14f)
        val outA = SceneParts.step(c, A_OUT, 0.06f)
        val armA = reachA * (1f - outA)
        val filled = SceneParts.step(c, A_SEAT, 0.08f) * (1f - outA)
        val reachB = SceneParts.step(c, B_REACH, 0.14f)
        val failB = SceneParts.step(c, B_FAIL, 0.08f)
        val outB = SceneParts.step(c, B_OUT, 0.06f)
        val armB = reachB * (1f - outB)
        // The strain the break is under, which is only ever while something is pushing on it. The
        // flash has to die with the arms rather than with the cycle: left on failB alone it would
        // still be lit at the end of the loop and drop in one frame as the clock wrapped.
        val strain = failB * (1f - outB)

        // --- the road ---------------------------------------------------------------------------
        // Alternating warm and dim-warm: at this pitch the joint alone is not enough to tell one
        // course from the next, and the bricks have to stay countable for the gap to mean anything.
        val hot = SceneParts.HOT
        for (k in 0 until nb) {
            if (k == hIdx) continue
            val s0 = -HW + k * pitch + pitch * MORTAR * 0.5f
            val u = hs[k]
            brick(tri, s0, bw, u, if (k and 1 == 0) SceneParts.WORK else SceneParts.WORK_DIM, 0.50f, q < 2)
            SceneParts.at(g, s0, u, -DEPTH, o)
            SceneParts.at(g, s0 + bw, u, -DEPTH, du)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], du[0], du[1], du[2], hot[0], hot[1], hot[2], 0.80f)
        }

        // --- the brick that is not there ----------------------------------------------------------
        // Drawn as a wire outline in the colour of a piece that was ADDED, kept from Tour I, so
        // that when it does seat the mend stays visibly a mend and not part of the original road.
        if (filled > 0.02f) brick(tri, sH0, bw, uH, SceneParts.ADDED, 0.55f * filled, q < 2)
        SceneParts.at(g, sH0, uH - THICK, -DEPTH, o)
        SceneParts.vec(g, bw, 0f, 0f, du)
        SceneParts.vec(g, 0f, THICK, 0f, dv)
        v = SceneParts.edge(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.ADDED, (0.30f + 0.14f * sin(kit.seconds * 1.7f) + filled * 0.55f).coerceIn(0f, 0.95f)
        )

        // --- the hollow ring round the absence -------------------------------------------------------
        // The open circle of every textbook picture of this, and the one piece of that convention
        // worth keeping: it says the point is named and empty, not merely unbuilt.
        val ring = if (q == 0) 16 else if (q == 1) 10 else 6
        SceneParts.at(g, sHole, uH, -DEPTH - 0.02f, o)
        SceneParts.vec(g, 1f, 0f, 0f, du)
        SceneParts.vec(g, 0f, 1f, 0f, dv)
        val tk = SceneParts.TAKEN
        v = MathMesh.arc(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            pitch * 0.85f, 0f, TAU, ring, tk[0], tk[1], tk[2],
            (0.55f + 0.28f * sin(kit.seconds * 2.2f) - filled * 0.45f).coerceIn(0f, 1f)
        )

        // --- the break ---------------------------------------------------------------------------------
        // A sheer face across the joint, given the road's full depth so it is a wall and not a line.
        // It brightens as the arms fail on it, which is the only thing in this half that changes.
        SceneParts.at(g, cutS, min(uL, uR) - THICK, -DEPTH, o)
        SceneParts.vec(g, 0f, uHi - min(uL, uR) + THICK, 0f, du)
        SceneParts.vec(g, 0f, 0f, 2f * DEPTH, dv)
        tv[0] = SceneParts.fill(
            tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.TAKEN, 0.38f + 0.26f * strain
        )
        v = SceneParts.edge(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.TAKEN, 0.70f + 0.28f * strain
        )

        // --- the two one-sided limits ----------------------------------------------------------------------
        // Each height carried a little way past the break as a dashed construction line. Two lines
        // that arrive at the same x and do not meet IS the jump; the wall between them is only how
        // it feels from inside the corridor.
        if (q < 2) {
            SceneParts.at(g, cutS, uL, -DEPTH - 0.02f, o)
            SceneParts.at(g, cutS + pitch * 1.5f, uL, -DEPTH - 0.02f, du)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], du[0], du[1], du[2], 4, tk[0], tk[1], tk[2], 0.50f)
            SceneParts.at(g, cutS, uR, -DEPTH - 0.02f, o)
            SceneParts.at(g, cutS - pitch * 1.5f, uR, -DEPTH - 0.02f, du)
            v = MathMesh.dashed(line, v, o[0], o[1], o[2], du[0], du[1], du[2], 4, tk[0], tk[1], tk[2], 0.50f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the limit's own value ------------------------------------------------------------------------
        // One bead hanging in the empty slot at exactly the height the two one-sided limits agree
        // on. Faint while it is only a limit; solid at the moment the arms seat it as a value.
        SceneParts.at(g, sHole, uH, -DEPTH - 0.03f, o)
        val bead = 0.026f + 0.006f * filled
        kit.ball(
            o[0], o[1], o[2], bead, bead, bead, SceneParts.ADDED, SceneParts.HOT,
            0.45f + 0.55f * filled, 0f, 0f, 1f, 0f, 0f, 1.4f + 2.2f * filled + kit.beat * 0.8f
        )

        // --- the arms -----------------------------------------------------------------------------------------
        // Over the hole: two tips, one destination. Over the step: two tips at two heights in two
        // planes, pushed past each other so that in stereo you can see the near one slide in front
        // of the far one and no amount of reaching brings them together.
        val steel = SceneParts.STEEL
        if (armA > 0.02f) {
            SceneParts.at(g, sHole, uH, -DEPTH - 0.05f, pB)
            SceneParts.at(g, sHole - 0.34f, uH + 0.50f, -0.60f, pA)
            lerp3(pA, pB, armA, o)
            kit.rod(pA[0], pA[1], pA[2], o[0], o[1], o[2], 0.015f, steel, SceneParts.CHALK, 0.25f)
            SceneParts.at(g, sHole + 0.34f, uH + 0.50f, -0.60f, pA)
            lerp3(pA, pB, armA, o)
            kit.rod(pA[0], pA[1], pA[2], o[0], o[1], o[2], 0.015f, steel, SceneParts.CHALK, 0.25f)
        }
        if (armB > 0.02f) {
            val over = 0.05f + 0.11f * failB
            SceneParts.at(g, cutS - 0.34f, uHi + 0.50f, -0.62f, pA)
            SceneParts.at(g, cutS + over, uL, -0.34f, pB)
            lerp3(pA, pB, armB, o)
            kit.rod(pA[0], pA[1], pA[2], o[0], o[1], o[2], 0.015f, steel, SceneParts.CHALK, 0.25f + strain * 0.5f)
            SceneParts.at(g, cutS + 0.34f, uHi + 0.50f, -0.62f, pA)
            SceneParts.at(g, cutS - over, uR, -0.16f, pB)
            lerp3(pA, pB, armB, o)
            kit.rod(pA[0], pA[1], pA[2], o[0], o[1], o[2], 0.015f, steel, SceneParts.CHALK, 0.25f + strain * 0.5f)
        }

        // --- notation -------------------------------------------------------------------------------------------
        // Two labels, and both of them are the same three symbols with one character changed. That
        // is the entire distinction the stop teaches, so the notation is allowed to carry it.
        //
        // They hang over their own faults rather than off the ends of the road. A label beside a
        // figure 1.5 wide would run another half-metre outboard and finish inside the wall, and the
        // reason labels are kept off the top and bottom of the eye — the telemetry block and the
        // caption box — does not bite here: this road sits BELOW the rail, so the space just above
        // it is the middle of the frame, which is the best place on this display to put anything.
        //
        // Both survive the governor. These are not tick labels: with them gone the picture is two
        // holes in a road and no reason to think they are different kinds of hole, which is the
        // stop. Two cached glyph textures are not what makes this device warm.
        val gl = 0.16f
        SceneParts.at(g, sHole - 0.06f, uH + 0.19f, -DEPTH - 0.03f, o)
        kit.text(
            if (filled > 0.5f) "f(a) = L" else "L^− = L^+",
            o[0], o[1], o[2], gl, SceneParts.ADDED, 0.95f
        )
        SceneParts.at(g, cutS + 0.04f, uHi + 0.26f, -DEPTH - 0.03f, o)
        kit.text("L^− ≠ L^+", o[0], o[1], o[2], gl, SceneParts.TAKEN, 0.95f)
    }
}
