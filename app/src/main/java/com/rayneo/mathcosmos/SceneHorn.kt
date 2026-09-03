package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 9 of THE ACCUMULATION — THE HORN. "A thing can be endlessly long and still hold only so
 * much."
 *
 * The improper integral, and the pair that makes it a real question rather than a definition:
 * ∫₁^∞ dx/x² settles on a number and ∫₁^∞ dx/x does not, and nothing you can see about the two
 * corridors tells you which is which. Only how fast they narrow.
 *
 * A MODEL CORRIDOR, TO PORT, AND NOT THE CORRIDOR ITSELF. The spec asks for the passage's own
 * radius to become 1/x. A scene cannot do that — [SceneKit.radius] belongs to the renderer and the
 * TourMap, and this stop's node already carries the tightest radius on the tour, 2.2 against the
 * 3.2–4.0 of its neighbours, so the ambient half of the job is done for us and the craft really is
 * squeezing as it arrives. What is left is the half that has to be WATCHED, and it is not the
 * narrowing: it is the total stalling while the narrowing continues. That wants a gauge standing
 * next to the horn where both are in one glance, so both hang off to port together. THE LATHE, two
 * stops back, already spends the fly-inside-a-solid card; spending it twice in one leg would blunt
 * both.
 *
 * THE RULER, AND IT IS A LOG ONE, WHICH IS THE WHOLE IDEA. Stations are laid out in equal steps of
 * ln x, so the wall rings are EVENLY SPACED and the model looks like an ordinary corridor — which is
 * the point, since the stop's claim is that you cannot tell the two cases apart by looking at the
 * corridor. A first draft used the ruler u = 1/x, which is prettier mathematics (it is the
 * substitution that makes the integral proper, and it draws Gabriel's horn as a plain cone) and
 * quite wrong for this stop: on it the swept total is exactly the fraction of the drawn length
 * covered, so the bar and the corridor run out together and there is no paradox left to watch.
 * The log ruler pulls them apart, and both totals then say something out loud:
 *
 *   - 1 − 1/x, the convergent one, is 0.875 by the halfway ring and 0.983 at the last. The front
 *     crosses the ENTIRE second half of the corridor and the bar moves by a hair. That gap, between
 *     half a corridor and a hair, is the stop.
 *   - ln x, the divergent one, is exactly proportional to the drawn length. On a ruler where the
 *     corridor looks uniform, that bar climbs at a constant rate and does not stop — it is through
 *     the ceiling a quarter of the way along and still going at the end.
 *
 * WHERE THE MODEL IS A MODEL, said plainly because the crew says it out loud. The horn is 6.4 world
 * units long and stands for one without an end; the drawing stops at x = 60, the horn does not.
 * Nobody should read "endlessly long" off the extent. It is read off the taper: the radius falls
 * geometrically while the ring spacing stays constant, so looking down the axis — which is very
 * nearly how the horn is seen for the whole approach — the rings appear to crowd toward a vanishing
 * point they never reach. And the bar's last hairline under the ceiling never closes, because
 * 1 − 1/x never gets there. That is a limit drawn honestly, not a stall.
 *
 * THE GAUGE'S SCALE IS COMPRESSED ABOVE THE CEILING, and only above it. Below, it is linear and the
 * ceiling is the number 1. Above, ln 60 is four and a bar four times the track would be through the
 * passage wall, so the overrun is squashed onto a fixed strip that asymptotes without ever settling.
 * It keeps moving; that is the only property of it that means anything, and the chevrons streaming
 * off the top say the rest.
 *
 * THE FRONT MOVES AT A CONSTANT RATE down the drawn corridor — a plain linear ramp, not the eased
 * one every other landmark in this app uses for its moves. Easing would put a slow-down in the
 * picture at exactly the moment the stop wants you to believe the slow-down is the arithmetic.
 * Everything the bar does here is the integral and nothing is the animation.
 *
 * No [SceneKit.traceHeight] here, deliberately. Every other stop on this leg integrates the roof;
 * this one integrates the corridor's own cross-section, so the roof curve is somebody else's line
 * and the ambient scene is welcome to it.
 *
 * The station frames are taken once per stop and cached. Twenty-one rail lookups a frame, twice
 * over for two eyes, is not a bill to hand the thermal governor thirty times a second for an answer
 * that cannot change.
 */
object SceneHorn : MathScene {

    /** A corridor that runs off into the distance wants to be there before you are level with it. */
    override val reach = 1.5f

    /** Six and a half world units past the stop — a bit over a third of a leg. */
    override val deep = 0.45f

    // ------------------------------------------------------------------ the horn
    private const val SIDE = -0.85f        // to port, so the craft passes the mouth rather than entering it
    private const val UP = 0.24f           // lifted, to leave room for the notation beneath
    private const val LEN = 6.4f           // drawn length, world units
    private const val R0 = 0.46f           // mouth radius; the widest thing here, 1.10 out from the rail
    private const val XR = 60f             // the largest x the drawing reaches. The horn does not stop there
    private const val NT = 20              // stations along it, evenly spaced in ln x
    private val LN_XR = ln(XR)

    // ------------------------------------------------------------------ the gauge
    private const val GX = -0.58f          // stage s: outboard of the mouth
    private const val GA = -0.50f          // stage a: half a unit back, so it never fouls the mouth rim
    private const val GW = 0.055f          // half the bar's width
    private const val GB = -0.62f          // the foot of the track
    private const val GH = 0.84f           // track height, and the height of the number 1
    private const val OVER = 0.15f         // the compressed strip above the ceiling

    // ------------------------------------------------------------------ the notation
    private const val LBL_S = 0.16f
    private const val LBL_A = -0.35f
    private const val LBL_U1 = -0.72f
    private const val LBL_U2 = -0.93f

    // ------------------------------------------------------------------ the loop
    private const val PERIOD = 30f
    private const val XSW = 8f             // where the sweep pauses: halfway down, and 87.5% of the way up

    private const val TAU = 6.2831855f

    private val fr = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p0 = FloatArray(3)
    private val p1 = FloatArray(3)
    private val p2 = FloatArray(3)
    private val tv = IntArray(1)

    /** Per station: centre (0..2), the rail's side (3..5), the rail's up (6..8). */
    private val st = FloatArray((NT + 1) * 9)

    /** 1/x at each station — the ruler's own coordinate, and the radius's base. */
    private val inv = FloatArray(NT + 1)

    /** The radius at each station for the exponent currently on show. Refilled every frame. */
    private val rad = FloatArray(NT + 1)

    /** One station interpolated to a fractional index, for the sweep front. */
    private val fs = FloatArray(9)

    private var builtFor = -1

    /**
     * Station frames and the ruler. Guarded on the stop index rather than a flag, so a tour change
     * that reuses this object cannot serve frames belonging to another rail.
     */
    private fun build(kit: SceneKit, i: Int) {
        if (builtFor == i) return

        // World units per node unit, MEASURED off the rail. Stops happen to sit sixteen apart on
        // every tour in the app, but nothing in the contract promises it and a horn that guessed
        // wrong would put its throat in the wall of the next stop.
        kit.frame(i - 0.5f, fr)
        val ax = fr[0]; val ay = fr[1]; val az = fr[2]
        kit.frame(i + 0.5f, fr)
        val dx = fr[0] - ax; val dy = fr[1] - ay; val dz = fr[2] - az
        val spacing = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1f)

        for (j in 0..NT) {
            // x geometric from 1 to XR. The spacing is therefore constant and the radius falls
            // geometrically, which is what makes the taper read as distance rather than as a cone.
            inv[j] = XR.pow(-j.toFloat() / NT)         // = 1/x
            val a = LEN * j / NT                       // equal steps of ln x: an ordinary-looking corridor
            // The horn follows the rail rather than a straight line from the mouth: the passage
            // swings the better part of a unit over six, and a straight horn would drift into it.
            kit.frame(i + a / spacing, fr)
            val k = j * 9
            st[k] = fr[0] + fr[6] * SIDE + fr[9] * UP
            st[k + 1] = fr[1] + fr[7] * SIDE + fr[10] * UP
            st[k + 2] = fr[2] + fr[8] * SIDE + fr[11] * UP
            st[k + 3] = fr[6]; st[k + 4] = fr[7]; st[k + 5] = fr[8]
            st[k + 6] = fr[9]; st[k + 7] = fr[10]; st[k + 8] = fr[11]
        }
        builtFor = i
    }

    // ------------------------------------------------------------------ the arithmetic

    /**
     * The exponent on show: r = R0 · x^(−q). One for the convergent horn, a half for the divergent
     * one, and a real morph between them rather than a cut, because the stop's claim is that the
     * two corridors look alike and only the rate of narrowing differs — which is a thing you can
     * only see if you watch one become the other.
     */
    private fun qAt(c: Float): Float = when {
        c < 0.66f -> 1f
        c < 0.94f -> 1f - 0.5f * SceneParts.step(c, 0.66f, 0.16f)
        else -> 0.5f + 0.5f * SceneParts.step(c, 0.945f, 0.045f)
    }

    /** A straight ramp, 0 to 1. Not [SceneParts.ease] — see the note on the front's constant rate. */
    private fun ramp(c: Float, at: Float, len: Float): Float = ((c - at) / len).coerceIn(0f, 1f)

    /**
     * Where the sweep front has reached, as x, in five beats over half a minute:
     *
     *   0.04 → 0.26  out to x = 8, the halfway ring. The bar takes 87.5% of its ceiling doing it.
     *   0.26 → 0.38  held. Half the corridor is lit and half is still dark ahead of the front.
     *   0.38 → 0.58  out to the last ring. The front crosses that whole dark half; the bar adds a
     *                tenth of its own width. This is the beat the stop exists for.
     *   0.58 → 0.94  held at the end while the profile is switched under it and the bar goes
     *                through the ceiling.
     *   0.94 → 0.99  the recoil, geometric back to x = 1, so the loop closes with no jump in either
     *                the picture or the number.
     *
     * x is geometric in the ramp throughout, which on a log ruler is a constant drawn speed.
     */
    private fun frontX(c: Float): Float = when {
        c < 0.04f -> 1f
        c < 0.38f -> XSW.pow(ramp(c, 0.04f, 0.22f))
        c < 0.94f -> XSW * (XR / XSW).pow(ramp(c, 0.38f, 0.20f))
        else -> XR.pow(1f - SceneParts.step(c, 0.94f, 0.05f))
    }

    /**
     * ∫₁^X x^(−2q) dx, in closed form: the cross-section is π r² and r = R0·x^(−q), so this is the
     * volume swept, up to the constant that scales the prop. At q = 1 it is 1 − 1/X, which has a
     * ceiling of one; at q = 1/2 it is ln X, which is exactly proportional to the drawn length and
     * has no ceiling at all. The logarithm is the removable singularity of the same expression, so
     * it is taken by hand near s = 0 rather than letting the cancellation eat the answer.
     */
    private fun total(x: Float, q: Float): Float {
        val u = (1f / x).coerceIn(1e-7f, 1f)
        val s = 1f - 2f * q
        return if (abs(s) < 1e-3f) -ln(u) else (u.pow(-s) - 1f) / s
    }

    /** The bar's height for a total of [v]: linear to the ceiling, compressed and endless above it. */
    private fun barU(v: Float): Float =
        if (v <= 1f) GH * v.coerceAtLeast(0f)
        else GH + OVER * (1f - 1f / v)

    /** Which station a given x falls at, as a fractional index — and, on a log ruler, its position. */
    private fun stationOf(x: Float): Float =
        (NT * ln(x.coerceAtLeast(1f)) / LN_XR).coerceIn(0f, NT.toFloat())

    // ------------------------------------------------------------------ drawing in the horn's frame

    /** A point at station [j], [phi] round from the rail's side, [r] out from the axis. */
    private fun ptAt(j: Int, phi: Float, r: Float, out: FloatArray) {
        val k = j * 9
        val c = cos(phi) * r
        val s = sin(phi) * r
        out[0] = st[k] + st[k + 3] * c + st[k + 6] * s
        out[1] = st[k + 1] + st[k + 4] * c + st[k + 7] * s
        out[2] = st[k + 2] + st[k + 5] * c + st[k + 8] * s
    }

    /**
     * The same at a fractional station, into [fs]. The basis is lerped rather than re-orthogonalised:
     * adjacent stations differ by under a degree of rail swing, and the ellipse that costs is
     * thinner than a line width.
     */
    private fun stationF(jf: Float) {
        val j0 = jf.toInt().coerceIn(0, NT - 1)
        val w = (jf - j0).coerceIn(0f, 1f)
        val a = j0 * 9
        for (k in 0 until 9) fs[k] = st[a + k] + (st[a + 9 + k] - st[a + k]) * w
    }

    /** A wall ring at station [j]. Rings finer than a line width are dropped, not drawn as dots. */
    private fun ring(line: FloatArray, v: Int, j: Int, r: Float, n: Int, c: FloatArray, a: Float): Int {
        if (r < 0.010f) return v
        val k = j * 9
        return MathMesh.arc(
            line, v, st[k], st[k + 1], st[k + 2],
            st[k + 3], st[k + 4], st[k + 5], st[k + 6], st[k + 7], st[k + 8],
            r, 0f, TAU, n, c[0], c[1], c[2], a
        )
    }

    /** One longitudinal ruling of the wall: the profile itself, which is the function on show. */
    private fun rib(line: FloatArray, v: Int, phi: Float, stride: Int, c: FloatArray, a: Float): Int {
        var k = v
        ptAt(0, phi, rad[0], p0)
        var j = stride
        while (j <= NT) {
            ptAt(j, phi, rad[j], p1)
            k = MathMesh.segment(
                line, k, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], c[0], c[1], c[2], a
            )
            p0[0] = p1[0]; p0[1] = p1[1]; p0[2] = p1[2]
            j += stride
        }
        return k
    }

    /**
     * A translucent disc across the horn at station [j]: one slice of what has been swept. Discs
     * rather than a shell, and for once not because a shell would hide things — from the rail the
     * horn is seen very nearly end-on for the whole approach, and nested discs read as depth in
     * exactly that view while a shell would read as a cone of fog. Stacked at a low alpha they also
     * do the right thing down the far end, where perspective piles them into one bright point.
     */
    private fun disc(tri: FloatArray, v: Int, j: Int, r: Float, n: Int, c: FloatArray, a: Float): Int {
        if (r < 0.015f) return v
        val k = j * 9
        var m = v
        ptAt(j, 0f, r, p1)
        for (e in 1..n) {
            if ((m + 3) * MathMesh.STRIDE > tri.size) return m
            ptAt(j, TAU * e / n, r, p2)
            m = MathMesh.vertex(tri, m, st[k], st[k + 1], st[k + 2], c[0], c[1], c[2], a)
            m = MathMesh.vertex(tri, m, p1[0], p1[1], p1[2], c[0], c[1], c[2], a)
            m = MathMesh.vertex(tri, m, p2[0], p2[1], p2[2], c[0], c[1], c[2], a)
            p1[0] = p2[0]; p1[1] = p2[1]; p1[2] = p2[2]
        }
        return m
    }

    /**
     * A point [mult] station-lengths past the last ring, on the axis. The drawing has to stop
     * somewhere and the horn does not, so what stops is marked as a cut rather than as an end: a
     * dashed tail runs on out of the frame and the ∞ hangs at the end of THAT, where it is a
     * direction of travel and not a claim about the last ring drawn.
     */
    private fun beyond(mult: Float, out: FloatArray) {
        val a = (NT - 1) * 9
        val b = NT * 9
        out[0] = st[b] + (st[b] - st[a]) * mult
        out[1] = st[b + 1] + (st[b + 1] - st[a + 1]) * mult
        out[2] = st[b + 2] + (st[b + 2] - st[a + 2]) * mult
    }

    /** A stroke in the gauge's plane, which is the stop's own stage. */
    private fun stroke(
        line: FloatArray, at: Int, s0: Float, u0: Float, s1: Float, u1: Float, c: FloatArray, a: Float
    ): Int {
        SceneParts.at(g, s0, u0, GA, p0)
        SceneParts.at(g, s1, u1, GA, p1)
        return MathMesh.segment(line, at, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], c[0], c[1], c[2], a)
    }

    /** An upright patch of the gauge. */
    private fun patch(tri: FloatArray, at: Int, sA: Float, uA: Float, sB: Float, uB: Float, c: FloatArray, a: Float): Int {
        SceneParts.at(g, sA, uA, GA, p0)
        SceneParts.vec(g, sB - sA, 0f, 0f, p1)
        SceneParts.vec(g, 0f, uB - uA, 0f, p2)
        return SceneParts.fill(tri, at, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], c, a)
    }

    /** The track the bar runs up: a corner and two spans, which is what SceneParts.edge wants. */
    private fun track(line: FloatArray, at: Int, c: FloatArray, a: Float): Int {
        SceneParts.at(g, GX - GW, GB, GA, p0)
        SceneParts.vec(g, GW * 2f, 0f, 0f, p1)
        SceneParts.vec(g, 0f, GH, 0f, p2)
        return SceneParts.edge(line, at, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], c, a)
    }

    // ------------------------------------------------------------------ the numbers on the HUD

    /** Three decimals without a formatter. The total is never negative by the time it gets here. */
    private fun dp3(v: Float): String {
        val t = (v * 1000f + 0.5f).toInt().coerceAtLeast(0)
        val f3 = t % 1000
        return "${t / 1000}." + (if (f3 < 10) "00$f3" else if (f3 < 100) "0$f3" else "$f3")
    }

    /** x, at one decimal while that says anything and whole once it does not. */
    private fun xs(v: Float): String {
        if (v >= 100f) return v.toInt().toString()
        val t = (v * 10f + 0.5f).toInt()
        return "${t / 10}.${t % 10}"
    }

    /**
     * The two numbers the stop is measuring, and an arrow saying which way they are going. Plain
     * enough for the telemetry pane, which is not the GlyphBoard: the ∫ is the one character worth
     * spending, because without it the reader has no idea what the second number is.
     */
    override fun readout(kit: SceneKit): String {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val q = qAt(c)
        val x = frontX(c)
        val v = total(x, q)
        return "x " + xs(x) + "   ∫ " + dp3(v) + (if (q > 0.75f) "  → 1" else "  ↑ ∞")
    }

    // ------------------------------------------------------------------ the landmark

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build(kit, i)
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, fr, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val qual = kit.quality
        val seg = when (qual) { 0 -> 12; 1 -> 8; else -> 6 }
        val ringStride = when (qual) { 0 -> 1; 1 -> 2; else -> 3 }
        val discStride = when (qual) { 0 -> 2; 1 -> 3; else -> 4 }
        val ribs = when (qual) { 0 -> 4; 1 -> 2; else -> 0 }

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val q = qAt(c)
        val x = frontX(c)
        val jf = stationOf(x)
        val vTotal = total(x, q)
        val diverging = q < 0.75f

        // The profile, at whatever exponent is on show. One pow per station and nothing else in the
        // frame recomputes it: the rings, the ribs and the discs all read this array.
        for (j in 0..NT) rad[j] = R0 * inv[j].pow(q)

        // --- the wall of the horn ---------------------------------------------------------------
        // Rings the whole way, evenly spaced, dim ahead of the front and bright behind it. That one
        // difference in alpha is what makes "half the corridor is still to come" a thing you can see
        // at the hold, and it is the half of the picture the gauge is being weighed against.
        var j = 0
        while (j <= NT) {
            val lead = if (j.toFloat() > jf) 0.42f else 0.85f
            v = ring(line, v, j, rad[j], seg, SceneParts.CHALK, lead)
            j += ringStride
        }
        for (m in 0 until ribs) {
            v = rib(line, v, TAU * m / ribs, 1, SceneParts.CHALK, 0.40f)
        }

        // The cut, marked as a cut. Two stations' worth of dashed axis running on past the last ring.
        if (qual < 2) {
            beyond(0f, p0)
            beyond(2f, p1)
            v = MathMesh.dashed(
                line, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 4,
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.35f
            )
        }

        // --- what has been swept ------------------------------------------------------------------
        // Amber, the colour the ambient wake is already filling the corridor behind the craft with,
        // because this is the same quantity measured on a model of its own. Faded in over the first
        // station: at x = 1 nothing has been swept yet, and a full disc sitting in the mouth before
        // the front has left it would be claiming otherwise.
        if (jf > 0.04f) {
            val da = 0.13f * min(jf, 1f)
            val jWhole = jf.toInt()
            var d = 0
            while (d <= jWhole) {
                tv[0] = disc(tri, tv[0], d, rad[d], seg, SceneParts.WORK, da)
                d += discStride
            }
        }

        // --- the front ------------------------------------------------------------------------------
        // A bright ring at the fractional station rather than the nearest whole one: the stations are
        // a third of a unit apart and the front would visibly tick between them otherwise, which at
        // this stop would look like the sweep advancing in steps it is not taking.
        stationF(jf)
        val rFront = R0 * (1f / x).pow(q)
        if (rFront > 0.012f) {
            v = MathMesh.arc(
                line, v, fs[0], fs[1], fs[2], fs[3], fs[4], fs[5], fs[6], fs[7], fs[8],
                rFront, 0f, TAU, seg,
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.95f
            )
        }

        // --- the gauge -------------------------------------------------------------------------------
        val top = GB + GH
        val fill = barU(vTotal)
        val lit = min(fill, GH)
        v = track(line, v, SceneParts.STEEL, 0.45f)
        // Ticks, quarters of the way to the ceiling. Secondary notation, so full detail only.
        if (qual == 0) {
            for (k in 1..3) {
                val u = GB + GH * k / 4f
                v = stroke(line, v, GX - GW - 0.035f, u, GX - GW, u, SceneParts.STEEL, 0.40f)
            }
        }
        // The total, in two pieces: the part inside the ceiling, and the part that should not exist.
        tv[0] = patch(tri, tv[0], GX - GW, GB, GX + GW, GB + lit, SceneParts.WORK, 0.55f)
        v = stroke(line, v, GX - GW, GB + lit, GX + GW, GB + lit, SceneParts.WORK, 0.95f)
        if (fill > GH) {
            tv[0] = patch(tri, tv[0], GX - GW, top, GX + GW, GB + fill, SceneParts.ADDED, 0.55f)
            v = stroke(line, v, GX - GW, GB + fill, GX + GW, GB + fill, SceneParts.ADDED, 0.95f)
        }
        // The ceiling. Intact while the total is under it; broken, and in the colour of a debt, once
        // it is not — the plate is the claim "this is all there will ever be", and it is now false.
        if (fill <= GH) {
            v = stroke(line, v, GX - GW - 0.07f, top, GX + GW + 0.07f, top, SceneParts.CHALK, 0.90f)
        } else {
            v = stroke(line, v, GX - GW - 0.09f, top + 0.02f, GX - GW - 0.005f, top, SceneParts.TAKEN, 0.90f)
            v = stroke(line, v, GX + GW + 0.005f, top, GX + GW + 0.09f, top + 0.02f, SceneParts.TAKEN, 0.90f)
        }
        // And the overrun leaving: chevrons streaming off the top, on their own fast loop, because a
        // bar on a compressed scale barely moves and "does not stop" has to be visible as motion.
        if (fill > GH && qual < 2) {
            val drift = SceneParts.cycle(kit.seconds, 1.4f)
            for (k in 0 until 3) {
                val r = (drift + k / 3f) % 1f
                val u = top + OVER + 0.03f + r * 0.12f
                val a = 0.85f * (1f - r) * (1f - r)
                v = stroke(line, v, GX - 0.07f, u - 0.045f, GX, u, SceneParts.ADDED, a)
                v = stroke(line, v, GX, u, GX + 0.07f, u - 0.045f, SceneParts.ADDED, a)
            }
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- two lamps, and never three ------------------------------------------------------------
        // One rides the sweep front. One sits on the throat, which is the only part of this picture
        // that is not a place: it is where the rest of the corridor went.
        stationF(jf)
        kit.ball(
            fs[0], fs[1], fs[2], 0.05f, 0.05f, 0.05f, SceneParts.HOT, SceneParts.WORK,
            1f, 0f, 0f, 1f, 0f, 0f, 1.1f + 1.2f * kit.beat
        )
        if (qual < 2) {
            val k = NT * 9
            val pinch = 0.030f + 0.010f * sin(kit.seconds * 2.4f)
            kit.ball(
                st[k], st[k + 1], st[k + 2], pinch, pinch, pinch,
                if (diverging) SceneParts.ADDED else SceneParts.CHALK, SceneParts.HOT,
                0.9f, 0f, 0f, 1f, 0f, 0f, 1.6f
            )
        }

        // --- notation ---------------------------------------------------------------------------------
        // Beside the instrument and beneath the horn's mouth, never over the top of it: the HUD owns
        // the top quarter of the eye and the caption box the bottom fifth, and both lines sit in the
        // band between. The profile NAMES the wall it is drawn on; the claim is only ever the second
        // line, and only ever after the picture has already made it.
        SceneParts.at(g, LBL_S, LBL_U1, LBL_A, o)
        kit.text(
            if (diverging) "r = 1/√x" else "r = 1/x",
            o[0], o[1], o[2], 0.17f,
            if (diverging) SceneParts.ADDED else SceneParts.CHALK, 0.95f
        )

        SceneParts.at(g, LBL_S, LBL_U2, LBL_A, o)
        kit.text(
            if (diverging) "∫_1^∞ dx/x → ∞" else "∫_1^∞ dx/x^2 = 1",
            o[0], o[1], o[2], 0.145f,
            if (diverging) SceneParts.TAKEN else SceneParts.HOT, 0.95f
        )

        if (qual == 0) {
            // The ceiling's own value, on the inboard side of the track so it reads into the gap
            // between the gauge and the horn rather than out toward the wall.
            SceneParts.at(g, GX + GW + 0.10f, top, GA, o)
            kit.text("1", o[0], o[1], o[2], 0.13f, SceneParts.CHALK, 0.85f, GlyphBoard.Style.SMALL,
                1f, anchor = -0.5f)
            // And where the corridor is going, at the end of the dashed tail rather than on the last
            // ring: the last ring is at x = 60 and saying ∞ there would be the one lie this stop
            // cannot afford.
            beyond(2.4f, o)
            kit.text("∞", o[0], o[1], o[2], 0.14f, SceneParts.CHALK, 0.80f, GlyphBoard.Style.SMALL)
        }
    }
}
