package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 7 of THE ACCUMULATION — THE LATHE. "Spin a shape and its area becomes a solid I can fly
 * down the middle of."
 *
 * THIS ONE SITS ON THE RAIL, AND THAT IS DELIBERATE. Every other landmark in the series hangs off
 * to port, because a flat figure centred on the rail is a figure you fly INTO and at the closest
 * point of the pass only a corner of it is in frame. That reasoning is about flat figures. A solid
 * of revolution about the rail is the opposite case: flying into it IS the payoff, the axis of
 * revolution is the axis you are already travelling down, and in stereo, being inside a vase you
 * watched yourself build is the only thing this stop has that a diagram on a wall would not. So
 * the axis of the lathe is the rail, and the widest disc is 1.5 units — well inside 0.8 of this
 * stop's passage radius, so the vase never touches the wall it is nested in.
 *
 * WHAT IS BEING SPUN, AND WHERE THE MODEL IS A MODEL. The profile is this corridor's own roof,
 * [SceneKit.traceHeight], over 2.6 node units of rail — the stretch running from just before the
 * roof dives under the rail to well past the bottom of the dive. Two liberties are taken with it
 * and both are stagecraft, not mathematics:
 *
 *   - that 2.6 node units of corridor is COMPRESSED onto eleven world units of rail, because at
 *     true scale the same shape would run from the fifth stop to the eighth and swallow two
 *     neighbouring landmarks;
 *   - the radius is MAGNIFIED so the belly is 1.5 units, because a vase you cannot fit a craft
 *     down the middle of is not a vase you can fly down the middle of.
 *
 * Both are constant factors, so every proportion in the picture is honest, and the numbers on the
 * HUD are reported in the CURVE's own units rather than the model's — the volume of the solid the
 * corridor's roof actually makes, not the volume of the prop.
 *
 * THE SIGN, WHICH IS THE JOKE OF THE STOP. This landmark stands one leg past THE SIGNED WAKE,
 * where the roof went under the rail and the sweep started paying out; the roof is still under
 * the rail here. A lathe does not care. The disc's radius is |f|, its area is πf², and a square
 * cannot be negative — the region below the axis spins into exactly as much solid as the region
 * above it. Better still, the window includes the crossing itself, so the vase has a WAIST: where
 * the curve meets the axis, the solid pinches to a single point on the rail, and the craft flies
 * through the pinch. That waist is the one piece of geometry here that could not have been drawn
 * without knowing what the function does.
 *
 * WIREFRAME, NOT A FILLED SURFACE. [MathMesh] argues for wireframes generally; inside a solid the
 * argument stops being an argument. A translucent shell enclosing the camera is not a vase, it is
 * a fog bank, and it would hide the corridor, the wake and the discs all at once. Rings and ribs
 * read as a surface from inside and let the rest of the tour through.
 *
 * THE DISCS ARE HONEST CYLINDERS. Each is drawn as two face circles of the SAME radius a fixed
 * thickness apart — a right cylinder, which is what the disc method actually assumes, rather than
 * a tapered slice of the true solid that would quietly hide its own error. The gap between the
 * coin stack and the smooth ribs is that error. It is not filled in red the way the slivers are at
 * THE SLABS: at 640x480, inside a wireframe, a red band wrapped round the camera is noise. The
 * size of it goes to the HUD instead, as the disc sum beside the true integral, which is where a
 * number that must be READ belongs.
 *
 * The station frames are computed once and cached. The rail does not move and neither does this
 * landmark, and twenty-seven frame lookups a frame for the life of the pass is a bill the thermal
 * governor should not be asked to pay twice.
 */
object SceneLathe : MathScene {

    /** A vase eleven units long wants to be visible before you are inside it. */
    override val reach = 1.5f

    /** It reaches five and a half world units past the stop — a third of a leg. */
    override val deep = 0.45f

    // ---------------------------------------------------------------- the window
    /** The stretch of corridor being spun, in node units either side of the stop. */
    private const val LO = -1.6f
    private const val HI = 1.0f
    private const val SPAN = HI - LO

    /** Samples along the profile. Every disc count divides it, so no disc face ever falls
     *  between two cached frames. */
    private const val TABLE = 24
    private const val MID = TABLE / 2

    // ---------------------------------------------------------------- the prop
    private const val LENGTH = 11.0f       // the vase's axial extent, world units
    private const val HALF_L = LENGTH * 0.5f
    private const val R_MAX = 1.50f        // world radius of the widest disc
    private const val LABEL_OUT = 2.05f    // where the notation hangs, to port of the axis

    // ---------------------------------------------------------------- the loop
    private const val PERIOD = 26f
    private const val SPIN_AT = 0.07f
    private const val SPIN_LEN = 0.26f
    private const val LAY_AT = 0.35f
    private const val LAY_LEN = 0.27f      // rest from 0.62 — ten seconds to look at the finished vase

    private const val TAU = 6.2831855f
    private const val PI = 3.14159265f

    private val fr = FloatArray(12)
    private val p0 = FloatArray(3)
    private val p1 = FloatArray(3)
    private val p2 = FloatArray(3)
    private val p3 = FloatArray(3)
    private val o = FloatArray(3)
    private val tv = IntArray(1)

    /** Where the loop is: turn fraction, discs laid as a fraction, and which beat. */
    private val ph = FloatArray(3)

    // ---------------------------------------------------------------- what is built once
    /** Per station: centre (0..2), port (3..5), up (6..8). Nine floats, no allocation. */
    private val st = FloatArray((TABLE + 1) * 9)
    private val fv = FloatArray(TABLE + 1)     // the curve's own value, signed
    private val rad = FloatArray(TABLE + 1)    // the prop's radius there, world units
    private var trueV = 0f                     // π ∫ f² dq over the window, curve units
    private var trueA = 0f                     // ∫ |f| dq — the area of the region before it spins
    private var wide = MID                     // the fattest station, where the tool rides
    private var waist = 0                      // the thinnest, where the solid pinches
    private var builtFor = -1

    /**
     * The roof, with a fallback so the lathe is never handed a flat blank. If this object is ever
     * hung on a tour with no trace there is no shape to spin; the fallback is THE ACCUMULATION's
     * own curve, written out.
     */
    private fun roof(kit: SceneKit, p: Float): Float =
        if (kit.hasTrace) kit.traceHeight(p) else 1.2f + 2f * sin(p * 0.75f)

    /**
     * The profile, the station frames and the two totals. Guarded by the stop index rather than a
     * plain flag: the object is a singleton and the guard costs an int compare.
     */
    private fun build(kit: SceneKit, i: Int) {
        if (builtFor == i) return

        // World units per node unit, MEASURED off the rail rather than assumed. Stops in this app
        // happen to sit sixteen units apart, but nothing in the contract promises that and a vase
        // that guessed wrong would sit half off the rail at its ends.
        kit.frame(i - 0.5f, fr)
        val ax = fr[0]; val ay = fr[1]; val az = fr[2]
        kit.frame(i + 0.5f, fr)
        val dx = fr[0] - ax; val dy = fr[1] - ay; val dz = fr[2] - az
        val spacing = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1f)

        var maxAbs = 1e-3f
        for (j in 0..TABLE) {
            val h = roof(kit, i + LO + SPAN * j / TABLE)
            fv[j] = h
            if (abs(h) > maxAbs) maxAbs = abs(h)
        }
        val mag = R_MAX / maxAbs
        for (j in 0..TABLE) rad[j] = abs(fv[j]) * mag

        wide = 0; waist = 0
        for (j in 0..TABLE) {
            if (rad[j] > rad[wide]) wide = j
            if (rad[j] < rad[waist]) waist = j
        }

        // One frame per station, taken once. The rail swings between stops, so a single stage
        // plane would have the far ends of an eleven-unit vase drifting a good unit off the axis
        // the craft is actually flying down — and the whole stop is that the two are the same.
        for (j in 0..TABLE) {
            val a = -HALF_L + LENGTH * j / TABLE
            kit.frame(i + a / spacing, fr)
            val k = j * 9
            st[k] = fr[0]; st[k + 1] = fr[1]; st[k + 2] = fr[2]
            st[k + 3] = -fr[6]; st[k + 4] = -fr[7]; st[k + 5] = -fr[8]   // port, so the seam faces the viewer
            st[k + 6] = fr[9]; st[k + 7] = fr[10]; st[k + 8] = fr[11]
        }

        // Trapezoid on f² and on |f|. Measuring the picture, not proving a theorem — and it agrees
        // exactly with the shape the ribs are drawn from, so the number and the vase are the same
        // object seen two ways.
        val dq = SPAN / TABLE
        var vv = 0f
        var aa = 0f
        for (j in 1..TABLE) {
            vv += (fv[j - 1] * fv[j - 1] + fv[j] * fv[j]) * 0.5f * dq
            aa += (abs(fv[j - 1]) + abs(fv[j])) * 0.5f * dq
        }
        trueV = PI * vv
        trueA = aa
        builtFor = i
    }

    /** How many discs the stack is cut into. A coarser sum is what a coarser sum looks like. */
    private fun discs(quality: Int) = when (quality) { 0 -> 12; 1 -> 8; else -> 6 }

    /** The curve's value at a fractional station index. */
    private fun fAt(x: Float): Float {
        val c = x.coerceIn(0f, TABLE.toFloat())
        val j = c.toInt().coerceAtMost(TABLE - 1)
        return fv[j] + (fv[j + 1] - fv[j]) * (c - j)
    }

    /** The prop's radius at a fractional station index. */
    private fun radAt(x: Float): Float {
        val c = x.coerceIn(0f, TABLE.toFloat())
        val j = c.toInt().coerceAtMost(TABLE - 1)
        return rad[j] + (rad[j + 1] - rad[j]) * (c - j)
    }

    /**
     * π Σ f(mid)² Δq over the first [upTo] discs, in the curve's own units. Midpoint rule, because
     * that is the rule the picture draws: each cylinder takes its radius from the middle of its
     * own slab, and midpoint is neither systematically over nor systematically under.
     */
    private fun discSum(nd: Int, upTo: Int): Float {
        val dq = SPAN / nd
        val per = TABLE.toFloat() / nd
        var s = 0f
        for (k in 0 until upTo) {
            val h = fAt((k + 0.5f) * per)
            s += h * h * dq
        }
        return PI * s
    }

    /**
     * Where the loop is, into [ph]. Both [draw] and [readout] call it in the same frame on the
     * same thread, so the vase and the HUD can never disagree about what is on screen.
     */
    private fun phase(seconds: Float) {
        val c = SceneParts.cycle(seconds, PERIOD)
        when {
            c < SPIN_AT -> set(0f, 0f, 0f)
            c < SPIN_AT + SPIN_LEN -> set(SceneParts.ease((c - SPIN_AT) / SPIN_LEN), 0f, 1f)
            c < LAY_AT -> set(1f, 0f, 2f)
            c < LAY_AT + LAY_LEN -> set(1f, (c - LAY_AT) / LAY_LEN, 3f)
            else -> set(1f, 1f, 4f)
        }
    }

    private fun set(turn: Float, laid: Float, beat: Float) {
        ph[0] = turn.coerceIn(0f, 1f); ph[1] = laid.coerceIn(0f, 1f); ph[2] = beat
    }

    /** Two decimals without a formatter. Neither total is ever negative by the time it gets here. */
    private fun dp2(v: Float): String {
        val t = (v * 100f + 0.5f).toInt()
        val f2 = t % 100
        return "${t / 100}.${if (f2 < 10) "0" else ""}$f2"
    }

    /**
     * The stop's numbers, in the curve's units. An area becomes a volume, and at rest the disc sum
     * stands next to the integral it is only approximately equal to.
     *
     * Plain ASCII: the telemetry pane is not the GlyphBoard and does not owe us an integral sign.
     * The slab width is deliberately NOT printed — the model's Δq is a fifth of a node unit, and
     * saying so beside a cut ladder reading 10^-2 would be two different Δx on one screen.
     */
    override fun readout(kit: SceneKit): String? {
        // The renderer only asks the scene the craft's progress is floored onto, so this is ours.
        val i = kit.progress.toInt().coerceIn(0, kit.stopCount - 1)
        build(kit, i)
        phase(kit.seconds)
        val nd = discs(kit.quality)
        return when {
            ph[2] < 1f -> "REGION  AREA ${dp2(trueA)}"
            ph[2] < 2f -> "TURNED ${(ph[0] * 360f).toInt()} DEG   AREA ${dp2(trueA)}"
            ph[2] < 3f -> "SOLID OF REVOLUTION   TRUE ${dp2(trueV)}"
            ph[2] < 4f -> {
                val k = (ph[1] * nd).toInt().coerceIn(0, nd)
                "VOLUME ${dp2(discSum(nd, k))} / ${dp2(trueV)}   DISCS $k / $nd"
            }
            else -> "VOLUME ${dp2(discSum(nd, nd))}   TRUE ${dp2(trueV)}   DISCS $nd"
        }
    }

    // ------------------------------------------------------------------ drawing in the vase's frame

    /** A point at station [j], [phi] round from port, [r] out from the axis. */
    private fun ptAt(j: Int, phi: Float, r: Float, out: FloatArray) {
        val k = j * 9
        val c = cos(phi) * r
        val s = sin(phi) * r
        out[0] = st[k] + st[k + 3] * c + st[k + 6] * s
        out[1] = st[k + 1] + st[k + 4] * c + st[k + 7] * s
        out[2] = st[k + 2] + st[k + 5] * c + st[k + 8] * s
    }

    /** A circle or an arc round the axis at station [j]. */
    private fun ring(
        line: FloatArray, v: Int, j: Int, r: Float,
        from: Float, to: Float, n: Int, c: FloatArray, a: Float
    ): Int {
        if (r < 0.02f) return v
        val k = j * 9
        return MathMesh.arc(
            line, v, st[k], st[k + 1], st[k + 2],
            st[k + 3], st[k + 4], st[k + 5], st[k + 6], st[k + 7], st[k + 8],
            r, from, to, n, c[0], c[1], c[2], a
        )
    }

    /** The profile curve drawn at one angle: a rib of the surface, or the rim of the region. */
    private fun profile(line: FloatArray, v: Int, phi: Float, stride: Int, c: FloatArray, a: Float): Int {
        var k = v
        ptAt(0, phi, rad[0], p0)
        var j = stride
        while (j <= TABLE) {
            ptAt(j, phi, rad[j], p1)
            k = MathMesh.segment(line, k, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], c[0], c[1], c[2], a)
            p0[0] = p1[0]; p0[1] = p1[1]; p0[2] = p1[2]
            j += stride
        }
        return k
    }

    /** The axis of revolution, dashed, while the region is still flat enough to need one. */
    private fun axisLine(line: FloatArray, v: Int, c: FloatArray, a: Float): Int {
        var k = v
        var j = 1
        while (j < TABLE) {
            val o0 = j * 9
            val o1 = (j + 1) * 9
            k = MathMesh.segment(
                line, k, st[o0], st[o0 + 1], st[o0 + 2], st[o1], st[o1 + 1], st[o1 + 2],
                c[0], c[1], c[2], a
            )
            j += 2
        }
        return k
    }

    /** Axis to rim: the radius, which is the whole content of the disc method. */
    private fun spoke(line: FloatArray, v: Int, j: Int, phi: Float, r: Float, c: FloatArray, a: Float): Int {
        val k = j * 9
        ptAt(j, phi, r, p1)
        return MathMesh.segment(line, v, st[k], st[k + 1], st[k + 2], p1[0], p1[1], p1[2],
            c[0], c[1], c[2], a * 0.5f, a)
    }

    /** One edge of a coin, joining its two faces, so the disc reads as solid and not as two hoops. */
    private fun rimLink(
        line: FloatArray, v: Int, j0: Int, j1: Int, phi: Float, r: Float, c: FloatArray, a: Float
    ): Int {
        ptAt(j0, phi, r, p0)
        ptAt(j1, phi, r, p1)
        return MathMesh.segment(line, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], c[0], c[1], c[2], a)
    }

    /** A general quad from four world points. [MathMesh.quad] builds parallelograms; this is not one. */
    private fun quad4(
        tri: FloatArray, v: Int, a0: FloatArray, b0: FloatArray, c0: FloatArray, d0: FloatArray,
        c: FloatArray, a: Float
    ): Int {
        if ((v + 6) * MathMesh.STRIDE > tri.size) return v
        var k = MathMesh.vertex(tri, v, a0[0], a0[1], a0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, b0[0], b0[1], b0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, c0[0], c0[1], c0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, a0[0], a0[1], a0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, c0[0], c0[1], c0[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(tri, k, d0[0], d0[1], d0[2], c[0], c[1], c[2], a)
        return k
    }

    /** The region between the axis and the curve, filled, at one angle: the thing on the lathe. */
    private fun regionFill(tri: FloatArray, v: Int, phi: Float, c: FloatArray, a: Float): Int {
        var k = v
        ptAt(0, phi, 0f, p0)
        ptAt(0, phi, rad[0], p1)
        for (j in 1..TABLE) {
            ptAt(j, phi, 0f, p2)
            ptAt(j, phi, rad[j], p3)
            k = quad4(tri, k, p0, p2, p3, p1, c, a)
            p0[0] = p2[0]; p0[1] = p2[1]; p0[2] = p2[2]
            p1[0] = p3[0]; p1[1] = p3[1]; p1[2] = p3[2]
        }
        return k
    }

    /** The face of the coin being laid right now, as a fan. Only ever one of these at a time. */
    private fun fan(tri: FloatArray, v: Int, j: Int, r: Float, n: Int, c: FloatArray, a: Float): Int {
        if (r < 0.02f) return v
        val k = j * 9
        p0[0] = st[k]; p0[1] = st[k + 1]; p0[2] = st[k + 2]
        var m = v
        ptAt(j, 0f, r, p1)
        for (e in 1..n) {
            ptAt(j, TAU * e / n, r, p2)
            if ((m + 3) * MathMesh.STRIDE > tri.size) return m
            m = MathMesh.vertex(tri, m, p0[0], p0[1], p0[2], c[0], c[1], c[2], a)
            m = MathMesh.vertex(tri, m, p1[0], p1[1], p1[2], c[0], c[1], c[2], a)
            m = MathMesh.vertex(tri, m, p2[0], p2[1], p2[2], c[0], c[1], c[2], a)
            p1[0] = p2[0]; p1[1] = p2[1]; p1[2] = p2[2]
        }
        return m
    }

    // ------------------------------------------------------------------ the landmark

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build(kit, i)
        phase(kit.seconds)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val q = kit.quality
        val nd = discs(q)
        val seg = when (q) { 0 -> 12; 1 -> 8; else -> 6 }
        val ribs = if (q == 0) 8 else 4
        val stride = when (q) { 0 -> 1; 1 -> 2; else -> 3 }
        val beat = ph[2]
        val theta = ph[0] * TAU
        val laid = ph[1] * nd

        // --- the shape on the lathe -----------------------------------------------------------
        // Before and during the turn: the flat region between the axis and the curve, which is the
        // same region the last six stops have been measuring the area of. The blade carries it
        // round; at quality 0 the seam it started from is left behind, dimmed, so how far it has
        // come is something you can see rather than something you have to have been watching for.
        if (beat < 2f) {
            tv[0] = regionFill(tri, tv[0], theta, SceneParts.WORK, 0.30f)
            if (beat > 0.5f && q == 0) {
                tv[0] = regionFill(tri, tv[0], 0f, SceneParts.WORK_DIM, 0.16f)
                v = profile(line, v, 0f, stride, SceneParts.WORK_DIM, 0.55f)
            }
            v = profile(line, v, theta, stride, SceneParts.HOT, 0.95f)
            v = spoke(line, v, 0, theta, rad[0], SceneParts.HOT, 0.85f)
            v = spoke(line, v, TABLE, theta, rad[TABLE], SceneParts.HOT, 0.85f)
            v = axisLine(line, v, SceneParts.CHALK, 0.28f)
        }

        // --- the surface being generated --------------------------------------------------------
        // Arcs from the seam to the blade at every other station. This is the one moment the solid
        // is genuinely being SWEPT rather than displayed, so it gets the finer sampling.
        if (beat > 0.5f && beat < 2f) {
            val an = (seg * ph[0]).toInt().coerceAtLeast(2)
            var j = 0
            while (j <= TABLE) {
                v = ring(line, v, j, rad[j], 0f, theta, an, SceneParts.WORK, 0.55f)
                j += 2 * stride
            }
        }

        // --- the finished surface, as ribs --------------------------------------------------------
        // Chalk, because chalk is the colour the ambient draws the real roof in and this is that
        // same curve, eight times over. The discs come in amber on top of it, and the gap between
        // the two is the error the HUD is quoting.
        if (beat >= 2f) {
            for (m in 0 until ribs) {
                v = profile(line, v, TAU * m / ribs, stride, SceneParts.CHALK, 0.42f)
            }
        }

        // --- the stack of discs -------------------------------------------------------------------
        if (beat >= 3f) {
            val per = TABLE / nd
            val perF = TABLE.toFloat() / nd
            for (k in 0 until nd) {
                val done = laid - k
                if (done <= 0f) break
                val j0 = k * per
                val j1 = j0 + per
                val r = radAt((k + 0.5f) * perF)
                val fresh = done < 1f
                val col = if (fresh) SceneParts.HOT else SceneParts.WORK
                val a = if (fresh) 0.95f else 0.70f
                // Two faces, the SAME radius on both: a right cylinder, which is the approximation
                // the disc method actually makes. Tapering it to the true surface would be drawing
                // a better answer than the method gives.
                v = ring(line, v, j0, r, 0f, TAU, seg, col, a)
                v = ring(line, v, j1, r, 0f, TAU, seg, col, a)
                if (q == 0) {
                    for (e in 0 until 4) v = rimLink(line, v, j0, j1, TAU * e / 4f, r, col, a * 0.55f)
                }
                if (fresh) tv[0] = fan(tri, tv[0], j1, r, seg, SceneParts.HOT, 0.24f * (1f - done))
            }
        }

        // --- the radius of the coin going in now -----------------------------------------------
        // The spoke IS the claim: this disc's radius is the height of the roof at this station.
        val laying = beat >= 3f && laid < nd
        var spokeJ = 0
        var spokeR = 0f
        if (laying) {
            val k = laid.toInt().coerceIn(0, nd - 1)
            spokeJ = (k + 1) * (TABLE / nd)
            spokeR = radAt((k + 0.5f) * (TABLE.toFloat() / nd))
            v = spoke(line, v, spokeJ, 0f, spokeR, SceneParts.HOT, 1f)
        }

        // Fills first, edges over them: the coin faces and the blade are translucent, and a bright
        // rim laid under its own fill loses about half its contrast on a waveguide.
        kit.flushTris(tv[0])
        kit.flushLines(v, 2.2f)

        // --- one lamp, and never two ---------------------------------------------------------
        // The tool riding the outside of the blade while it turns; the coin's edge while it seats.
        if (beat > 0.5f && beat < 2f) {
            ptAt(wide, theta, rad[wide] + 0.07f, o)
            kit.ball(o[0], o[1], o[2], 0.055f, 0.055f, 0.055f, SceneParts.HOT, SceneParts.WORK,
                1f, 0f, 0f, 1f, 0f, 0f, 0.8f + 1.2f * kit.beat)
        } else if (laying && spokeR > 0.05f) {
            ptAt(spokeJ, 0f, spokeR, o)
            kit.ball(o[0], o[1], o[2], 0.05f, 0.05f, 0.05f, SceneParts.HOT, SceneParts.ADDED,
                1f, 0f, 0f, 1f, 0f, 0f, 1.4f)
        }

        // --- notation ---------------------------------------------------------------------------
        // Beside the vase, out to port at the stop's own station: the HUD owns the top of the eye
        // and the caption box the bottom, so nothing is hung above or below the figure. Two beats
        // only — an area, then the volume that area makes — and the square in the second one is
        // the answer to the sign, since f² does not care which side of the rail f was on.
        val gl = 0.18f
        ptAt(MID, 0f, LABEL_OUT, o)
        val claim = if (beat < 2f) "∫ f dx" else "V = π ∫ f^2 dx"
        kit.text(claim, o[0], o[1], o[2], gl, SceneParts.HOT, 0.95f)

        if (q == 0) {
            if (laying) {
                ptAt(spokeJ, 0f, spokeR + 0.34f, o)
                kit.text("r = f(x)", o[0], o[1], o[2], gl * 0.85f, SceneParts.ADDED, 0.95f)
            } else if (beat >= 4f && rad[waist] < R_MAX * 0.16f) {
                // Only where the curve really does touch the axis inside the window. On a trace
                // that never crosses there is no waist and this would be a lie about the picture.
                ptAt(waist, 0f, 1.30f, o)
                kit.text("f = 0", o[0], o[1], o[2], gl * 0.8f, SceneParts.CHALK, 0.85f,
                    GlyphBoard.Style.SMALL)
            }
        }
    }
}
