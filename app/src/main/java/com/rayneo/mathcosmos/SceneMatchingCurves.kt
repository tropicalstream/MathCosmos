package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tour IV, stop 7 — THE MATCHING CURVES. "I can force a polynomial to agree with a curve to any
 * order I like, at one point."
 *
 * The flagship of the tour, and the one stop whose whole content is a picture rather than a number.
 * One bead sits on the corridor's roof — the anchor, the only place any of this is guaranteed —
 * and six ribbons come out of it, one every couple of seconds, in six colours: the flat line that
 * matches only the height, the tangent that matches the direction too, the parabola that matches
 * the bend, then a cubic, a quartic, and a sixth-order ribbon. Each one is built to match ONE MORE
 * THING at the anchor and is told nothing whatever about anywhere else, and each one holds against
 * the real roof over a visibly longer stretch of corridor before it peels off. The peel points
 * march away from the bead in both directions as the orders come out. That march is the stop.
 *
 * Placement is the standing exception, and the same one THE TRACE takes. The general rule in this
 * app is that a flat figure belongs off to one side, because a figure centred on the rail is a
 * figure you fly INTO. These ribbons are not a figure to be read side-on: they are the roof of the
 * corridor being approximated, they must leave the anchor bead ON the trace the ambient scene is
 * already drawing, and they run fore and aft for a couple of stops each way. So they hang overhead
 * on the rail's own axis and the craft passes UNDERNEATH them, which is exactly what the crew say
 * is happening — "quite a thing to sit under".
 *
 * Four decisions worth stating, three of them approximations that the code should own as loudly as
 * the crew do:
 *
 *  - THE ANCHOR IS NOT AT THE NODE. It sits 0.87 of a stop past it, and that offset is load-bearing
 *    rather than decorative. Tour IV's roof is 1.5 + 0.7 sin(0.8p), and at the node itself the roof
 *    is a whisker off its turning point: the slope there is one eightieth of the curvature, so the
 *    tangent would be indistinguishable from the flat line and the second ribbon would say nothing.
 *    At 0.87 past the node the roof is at three quarters of a turn, where height, slope, bend and
 *    every derivative after them are all of comparable size — so no two consecutive orders coincide
 *    and every ribbon visibly improves on the one before. Placed there it is also ahead of you as
 *    you arrive and behind you as you leave, which is what makes the fan open out as you fly under.
 *
 *  - THE DERIVATIVES ARE MEASURED, NOT KNOWN. A scene is handed the roof as a function it can only
 *    evaluate, so the six coefficients come from seven samples of it half a stop apart and the
 *    usual central differences. Equivalently: these ribbons are the truncations of the degree-six
 *    polynomial through those seven samples, which is the Taylor polynomial to within a few percent
 *    on a roof this smooth — a few percent of a quantity already too small to see. It is still an
 *    estimate and it is worth saying so, because the stop's whole claim is that nothing but the
 *    anchor was consulted, and a stencil half a stop wide does peek a little either side.
 *
 *  - THE RIBBONS ARE FANNED ACROSS THE CORRIDOR. Every one of them passes through the anchor and
 *    near it they are all within a finger's width of each other, so drawn honestly on one line they
 *    would be one smeared thread with z-fighting for a centre. Each is therefore offset sideways by
 *    a fixed amount — order zero to port, order six to starboard — which changes nothing about the
 *    heights, which are the mathematics, and turns the near field into a fan of six distinct lines
 *    that in stereo is the best thing in the tour. It is a legibility fiction and nothing more.
 *
 *  - A RIBBON IS CUT SHORTLY AFTER IT PEELS. Not for tidiness: a sinusoidal roof will happily come
 *    back and meet a low-order polynomial again a couple of stops away, purely by accident, and a
 *    flat line that rejoins the roof at the far end tells a flat lie about how far the promise
 *    carries. So each ribbon is drawn until it first parts from the roof by a visible gap — that
 *    point gets a bright cross-tie and a tick down to the roof, the beginnings of the next stop's
 *    remainder — and then given a short run-out during which it fades away. What is left on screen
 *    is six lengths in ascending order, which is the only fact the stop is trying to hand over.
 *
 * The sixth-order ribbon never peels at all inside the corridor we can draw, and that is intended.
 * Order six is right to a few hundredths across the whole visible leg; its promise runs out beyond
 * the far wall, and where exactly is the business of stop 9.
 *
 * One thing the finished picture does NOT do, and it is worth knowing before anyone reports it as
 * a bug: the six lengths do not come out evenly spaced. On this roof they land at roughly 0.3, 1.0,
 * 1.5, 2.4, 2.4 and past-the-end stops from the anchor — the cubic and the quartic let go within a
 * couple of tenths of each other. That is the roof telling the truth rather than the code getting
 * it wrong. The roof is a sine, its series alternates, and consecutive orders therefore bracket the
 * curve from opposite sides: out at two stops the fourth term is already busy cancelling most of
 * what the third term overshot, so buying one more order buys almost nothing there. The march
 * outward is real and it is the point; it is simply not a ruler.
 *
 * Cost: nine rail-frame queries, under six hundred line vertices, and ONE flushLines for the
 * entire nest — plus the anchor's ball and the notation. The ribbons all share their samples, so
 * adding an order costs two vertices a step and no extra frame work.
 */
object SceneMatchingCurves : MathScene {

    // The nest runs two and a half stops each way, so the landmark must fade in early and must not
    // be culled the moment its own node is astern — half the picture is still ahead of the viewer.
    override val reach = 1.8f
    override val deep = 2.6f

    private const val ANCHOR = 0.87f       // stops past the node — see the head comment
    private const val SPAN = 2.6f          // stops of corridor drawn each way
    private const val STENCIL = 0.5f       // half-spacing of the derivative samples, in stops
    private const val PEEL = 0.12f         // gap at which a ribbon counts as having left the roof
    private const val STRAY = 0.75f        // gap at which it is cut outright, wherever it has got to
    private const val RUNOUT = 0.40f       // stops of fading run-out after the peel point
    private const val STEP = 0.10f         // sample spacing along the corridor, in stops
    private const val FAN = 0.18f          // sideways separation between neighbouring ribbons
    private const val TIP = 0.25f          // stops over which a growing ribbon's tip fades in
    private const val MARK = 0.10f         // half-length of a peel cross-tie

    private const val PERIOD = 26f
    private const val EMERGE = 0.06f       // when the first ribbon starts out
    private const val GAP = 0.085f         // one ribbon every 2.2 s — the crew say "a couple"
    private const val GROW = 0.06f         // how long one ribbon takes to reach full length
    private const val CLOSE_AT = 0.93f     // the nest draws back into the bead before looping
    private const val CLOSE_LEN = 0.07f
    private const val CLAIM_AT = 0.56f     // the equation arrives only once the picture is finished

    private const val ST_N = 9             // rail stations across the drawn stretch
    private const val MAX_VERTS = 1500
    private const val TAU = 6.2831855f

    private const val LEG_S = -0.80f       // the legend hangs to port of the fan, never over it
    private const val LEG_TOP = 0.42f
    private const val LEG_DROP = 0.20f
    private const val CLAIM_UP = 0.80f

    private const val N_RIB = 6

    /** The six orders. Not 0..5: the design asks for a jump at the end, and the jump is the point. */
    private val ORDERS = intArrayOf(0, 1, 2, 3, 4, 6)

    /** Which ribbons survive the thermal governor's second step. Nesting still reads with four. */
    private val KEEP_LOW = booleanArrayOf(true, true, true, false, false, true)

    private val NAME = arrayOf("T_0", "T_1", "T_2", "T_3", "T_4", "T_6")
    private const val CLAIM = "T_n^{(k)}(a) = f^{(k)}(a),  k ≤ n"

    // Cold for the ribbon that knows least, hot for the one that knows most: the ramp is doing the
    // same work as the lengths, so a viewer can read the nest by colour alone at a distance.
    private val LEAF = floatArrayOf(0.74f, 0.98f, 0.60f, 1f)
    private val TINT = arrayOf(
        SceneParts.STEEL, SceneParts.COOL, SceneParts.ADDED, LEAF, SceneParts.WORK, SceneParts.HOT
    )

    // ---- scratch. Nothing here survives a frame; nothing here is ever allocated in draw. ----
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val st = FloatArray(ST_N * 12)   // the rail stations, sampled once and interpolated
    private val fr = FloatArray(9)           // one interpolated station: centre, side, up
    private val sam = FloatArray(7)          // roof samples for the derivative stencil
    private val co = FloatArray(7)           // Taylor coefficients c0..c6 at the anchor
    private val prev = FloatArray(N_RIB * 3) // each ribbon's last drawn point
    private val prevA = FloatArray(N_RIB)
    private val live = BooleanArray(N_RIB)
    private val peelAt = FloatArray(N_RIB)   // where each ribbon left the roof, this wing
    private val grown = FloatArray(N_RIB)    // how far out each ribbon has been extruded, in stops
    private val emv = FloatArray(N_RIB)

    /**
     * The anchor's rail position, cached by draw so the HUD line can be measured at the same place
     * the picture is drawn at. Per-frame scratch like everything else above: negative means the
     * landmark has not been drawn yet this run, and there is nothing to report.
     */
    private var anchorP = -1f

    /** How far ribbon [k] is out of the bead: out during the cycle, drawn back in at the end of it. */
    private fun emerge(c: Float, k: Int): Float =
        SceneParts.step(c, EMERGE + k * GAP, GROW) * (1f - SceneParts.step(c, CLOSE_AT, CLOSE_LEN))

    /**
     * The Taylor coefficients of the roof at [a], from seven samples a half-stop apart. The stencils
     * are the standard central ones; taken together they are the derivatives at the centre of the
     * degree-six polynomial through those seven points, which is what makes the six ribbons nested
     * truncations of a single object rather than six unrelated fits.
     */
    private fun coeffs(kit: SceneKit, a: Float) {
        for (j in 0..6) sam[j] = kit.traceHeight(a + (j - 3) * STENCIL)
        val h = STENCIL
        val h2 = h * h
        val d1 = (sam[4] - sam[2]) / (2f * h)
        val d2 = (sam[4] - 2f * sam[3] + sam[2]) / h2
        val d3 = (sam[5] - 2f * sam[4] + 2f * sam[2] - sam[1]) / (2f * h2 * h)
        val d4 = (sam[5] - 4f * sam[4] + 6f * sam[3] - 4f * sam[2] + sam[1]) / (h2 * h2)
        val d5 = (sam[6] - 4f * sam[5] + 5f * sam[4] - 5f * sam[2] + 4f * sam[1] - sam[0]) /
            (2f * h2 * h2 * h)
        val d6 = (sam[6] - 6f * sam[5] + 15f * sam[4] - 20f * sam[3] + 15f * sam[2] -
            6f * sam[1] + sam[0]) / (h2 * h2 * h2)
        co[0] = sam[3]
        co[1] = d1
        co[2] = d2 * 0.5f
        co[3] = d3 / 6f
        co[4] = d4 / 24f
        co[5] = d5 / 120f
        co[6] = d6 / 720f
    }

    /** The order-[order] ribbon's height at [d] stops from the anchor, by Horner. */
    private fun poly(order: Int, d: Float): Float {
        var y = co[order]
        var m = order - 1
        while (m >= 0) { y = y * d + co[m]; m-- }
        return y
    }

    /**
     * The rail frame at fraction [t] across the drawn stretch, blended from the two stations either
     * side. The rail turns by a couple of degrees per stop, so a linear blend across 0.65 of one is
     * exact to well under a pixel — and it keeps the frame queries at nine rather than fifty.
     */
    private fun frameAt(t: Float) {
        val x = (t * (ST_N - 1)).coerceIn(0f, (ST_N - 1).toFloat())
        var j = x.toInt()
        if (j > ST_N - 2) j = ST_N - 2
        val u = x - j
        val p = j * 12
        val q = p + 12
        fr[0] = st[p] + (st[q] - st[p]) * u
        fr[1] = st[p + 1] + (st[q + 1] - st[p + 1]) * u
        fr[2] = st[p + 2] + (st[q + 2] - st[p + 2]) * u
        fr[3] = st[p + 6] + (st[q + 6] - st[p + 6]) * u
        fr[4] = st[p + 7] + (st[q + 7] - st[p + 7]) * u
        fr[5] = st[p + 8] + (st[q + 8] - st[p + 8]) * u
        fr[6] = st[p + 9] + (st[q + 9] - st[p + 9]) * u
        fr[7] = st[p + 10] + (st[q + 10] - st[p + 10]) * u
        fr[8] = st[p + 11] + (st[q + 11] - st[p + 11]) * u
    }

    /** A point [y] above the rail and [lat] across it, in the current interpolated station. */
    private fun point(y: Float, lat: Float, out: FloatArray) {
        out[0] = fr[0] + fr[6] * y + fr[3] * lat
        out[1] = fr[1] + fr[7] * y + fr[4] * lat
        out[2] = fr[2] + fr[8] * y + fr[5] * lat
    }

    /**
     * The peel mark: a cross-tie across the corridor where a ribbon lets go, and a tick from there
     * up or down to the roof it has let go of. The tick is the gap made into an object, which is
     * the whole of the next stop and worth planting here.
     */
    private fun peelMark(line: FloatArray, v: Int, y: Float, roof: Float, lat: Float, c: FloatArray): Int {
        var k = v
        point(y, lat - MARK, o)
        val ax = o[0]; val ay = o[1]; val az = o[2]
        point(y, lat + MARK, o)
        k = MathMesh.segment(line, k, ax, ay, az, o[0], o[1], o[2], c[0], c[1], c[2], 0.95f)
        point(y, lat, o)
        val bx = o[0]; val by = o[1]; val bz = o[2]
        point(roof, lat, o)
        return MathMesh.segment(line, k, bx, by, bz, o[0], o[1], o[2], c[0], c[1], c[2], 0.5f, 0.15f)
    }

    /** One decimal without a formatter: the HUD line costs one small string and no locale. */
    private fun d1(v: Float): String {
        val t = (v * 10f + 0.5f).toInt()
        return "${t / 10}.${t % 10}"
    }

    /**
     * How far the topmost ribbon currently out holds the roof, measured exactly the way the picture
     * measures it — by walking outward until the gap first opens past PEEL. Both wings are walked
     * and the nearer of the two is reported, so the ± is literally true rather than an assumption
     * of symmetry the roof has not agreed to. Numbers that must be READ belong here rather than
     * hung in the corridor, and this distance is the whole content of the stop.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace || anchorP < 0f) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        var top = -1
        for (k in 0 until N_RIB) if (emerge(c, k) > 0.35f) top = k
        if (top < 0) return "ANCHOR SET   6 RIBBONS QUEUED"
        val order = ORDERS[top]
        coeffs(kit, anchorP)
        var d = STEP
        while (d <= SPAN) {
            if (abs(poly(order, d) - kit.traceHeight(anchorP + d)) > PEEL ||
                abs(poly(order, -d) - kit.traceHeight(anchorP - d)) > PEEL
            ) return "ORDER $order   HOLDS ±${d1(d)} STOPS"
            d += STEP
        }
        return "ORDER $order   HOLDS PAST THE LEG"
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // Without a roof curve there is nothing to match, and traceHeight would hand back a flat
        // zero: six ribbons lying on top of each other along the rail, saying nothing at all.
        if (!kit.hasTrace) return

        val a = i + ANCHOR
        anchorP = a
        // Clamped to the rail's own extent. Past either end the spline collapses onto the end node
        // and every sample beyond it would pile up into a spike at one point.
        val lo = max(0f, a - SPAN)
        val hi = min((kit.stopCount - 1).toFloat(), a + SPAN)
        if (hi - lo < 0.6f) return
        val inv = 1f / (hi - lo)

        coeffs(kit, a)
        for (j in 0 until ST_N) {
            kit.frame(lo + (hi - lo) * j / (ST_N - 1f), f)
            System.arraycopy(f, 0, st, j * 12, 12)
        }

        val q = kit.quality
        val step = if (q == 0) STEP else if (q == 1) STEP * 2f else STEP * 3f
        val line = kit.lineBuf
        val cap = min(MAX_VERTS, kit.lineCapacity)
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val hA = co[0]
        var v = 0

        for (k in 0 until N_RIB) {
            emv[k] = emerge(c, k)
            grown[k] = emv[k] * SPAN
        }

        // ---- the six ribbons, both wings, one buffer ---------------------------------------
        for (w in 0 until 2) {
            val dir = if (w == 0) -1f else 1f
            val wing = if (w == 0) a - lo else hi - a
            frameAt((a - lo) * inv)
            for (k in 0 until N_RIB) {
                live[k] = grown[k] > 0.001f && (q < 2 || KEEP_LOW[k])
                peelAt[k] = -1f
                prevA[k] = 0f
                point(hA, FAN * (k - 2.5f), o)
                prev[k * 3] = o[0]; prev[k * 3 + 1] = o[1]; prev[k * 3 + 2] = o[2]
            }
            var j = 1
            while (v + 6 * N_RIB <= cap) {
                val d = dir * j * step
                val ad = j * step
                val p = a + d
                if (p < lo || p > hi) break
                frameAt((p - lo) * inv)
                val roof = kit.traceHeight(p)
                var any = false
                for (k in 0 until N_RIB) {
                    if (!live[k]) continue
                    val lim = min(grown[k], wing)
                    if (ad > lim) { live[k] = false; continue }
                    val y = poly(ORDERS[k], d)
                    val e = abs(y - roof)
                    // Cut outright rather than let a discarded low order dive at the rail.
                    if (e > STRAY) { live[k] = false; continue }
                    val tint = TINT[k]
                    if (peelAt[k] < 0f) {
                        if (e > PEEL) {
                            peelAt[k] = ad
                            if (q < 2) v = peelMark(line, v, y, roof, FAN * (k - 2.5f), tint)
                        }
                    } else if (ad > peelAt[k] + RUNOUT) { live[k] = false; continue }
                    // Full brightness while it is holding, fading away through the run-out, and
                    // fading in again at the growing tip so the extrusion has no hard end.
                    var al = if (peelAt[k] < 0f) 1f
                        else (1f - (ad - peelAt[k]) / RUNOUT).coerceIn(0f, 1f)
                    al *= ((lim - ad) / TIP).coerceIn(0f, 1f) * min(1f, emv[k] * 3f) * 0.92f
                    point(y, FAN * (k - 2.5f), o)
                    val b = k * 3
                    v = MathMesh.segment(
                        line, v, prev[b], prev[b + 1], prev[b + 2], o[0], o[1], o[2],
                        tint[0], tint[1], tint[2], prevA[k], al
                    )
                    prev[b] = o[0]; prev[b + 1] = o[1]; prev[b + 2] = o[2]
                    prevA[k] = al
                    any = true
                }
                if (!any) break
                j++
            }
        }

        // ---- the anchor: the one place any of this is guaranteed ---------------------------
        SceneParts.stage(kit, a, 0f, 0f, f, g)
        SceneParts.at(g, 0f, hA, 0f, o)
        val bx = o[0]; val by = o[1]; val bz = o[2]
        if (q < 2 && v + 60 <= cap) {
            // A halo in the corridor's cross-section, so the bead reads as a place on the roof
            // rather than a bright dot floating somewhere down the tube.
            val rad = 0.20f + 0.03f * kit.beat
            v = MathMesh.arc(
                line, v, bx, by, bz, g[3], g[4], g[5], g[6], g[7], g[8],
                rad, 0f, TAU, if (q == 0) 20 else 12,
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.55f
            )
            // And a dashed drop to the rail: the anchor is a point on the corridor you fly under,
            // not a point in space. Dashed because it is a construction line, not an object.
            SceneParts.at(g, 0f, 0f, 0f, o)
            v = MathMesh.dashed(
                line, v, o[0], o[1], o[2], bx, by, bz, 5,
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], 0.30f
            )
        }

        kit.flushLines(v, 2.4f)

        kit.ball(
            bx, by, bz, 0.075f, 0.075f, 0.075f, SceneParts.HOT, SceneParts.LAMP,
            1f, 0f, 0f, 1f, 0f, 0f, 2.0f + 1.4f * kit.beat
        )

        // ---- notation ----------------------------------------------------------------------
        // The legend hangs to port, clear of the fan: a column of six coloured names is the only
        // thing that ties a ribbon's colour to its order once the ribbons have spread out over two
        // stops of corridor and stopped being nameable one by one.
        if (q == 0) {
            for (k in 0 until N_RIB) {
                val al = min(1f, emv[k] * 2f)
                if (al < 0.03f) continue
                SceneParts.at(g, LEG_S, hA + LEG_TOP - k * LEG_DROP, 0f, o)
                kit.text(NAME[k], o[0], o[1], o[2], 0.17f, TINT[k], al)
            }
            SceneParts.at(g, 0.20f, hA, 0f, o)
            kit.text("a", o[0], o[1], o[2], 0.19f, SceneParts.HOT, 0.9f, GlyphBoard.Style.MATH,
                1f, anchor = -0.5f)
            // The claim arrives last, when the picture has already made it true: every ribbon
            // matches the roof's first n derivatives at the anchor, and nothing else anywhere.
            val cl = SceneParts.step(c, CLAIM_AT, 0.06f) * (1f - SceneParts.step(c, CLOSE_AT, CLOSE_LEN))
            if (cl > 0.03f) {
                SceneParts.at(g, LEG_S, hA + CLAIM_UP, 0f, o)
                kit.text(CLAIM, o[0], o[1], o[2], 0.14f, SceneParts.CHALK, cl)
            }
        }
    }
}
