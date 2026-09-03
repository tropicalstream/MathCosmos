package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR VI, stop 8 — THE RIM. "Stretch the sheet however you like; only the rim decides the answer."
 *
 * Stokes' theorem, and the flagship of the whole series. A rigid wire loop hangs to port. A film
 * spans it, and over half a minute that film is pulled out into a dome, bagged out into a long
 * drooping sock, and flattened back to a disc. Every paddle wheel on the skin changes speed; a good
 * many change direction; the skin's own area gets on for three times what it started at. The
 * total does not move. Then the film fades and the loop is left alone with the push running round
 * it, which is the sentence the crew ends on: survey the skin for an hour, or fly the rim once.
 *
 * THE RIM IS FIXED BY THE PARAMETRISATION, NOT BY THE ANIMATION. The surface is
 * r(u, θ) = (R u cos θ, R u sin θ, 0) + B · p(u), with p(1) = 0 for every shape in the morph. The
 * boundary is therefore the same circle in every frame as an algebraic fact rather than as
 * something the animator remembered to keep still — which is the right way round, because the whole
 * claim of the stop is that the boundary is what the answer depends on. B is the displacement of
 * the film's centre and p its profile; the morph is a lerp of four numbers between three keys, and
 * that is the entire deformation.
 *
 * WHY THE TOTAL IS MEASURED ON THE RIM AND NOT ON THE SKIN. It would be neater theatre to sum
 * (∇×F)·n over the surface cells and show that sum standing still. It would also be a lie: a
 * midpoint sum over thirty-two cells of a sock wanders by a few percent as the cells re-shape, the
 * bar would visibly twitch, and a viewer would learn the opposite of the theorem. So the bar shows
 * ∮F·dr round the rim — which is the right-hand side of Stokes, is what the crew actually flies,
 * and is constant for the plain reason that the rim it is computed on never moves. The surveyed
 * sum over the skin is drawn too, at quality 0 only, as a thin pip across the same bar, and it
 * does tremble. That tremble is the lattice, not the theorem, and it is worth showing: it is the
 * difference between the hour of surveying and the one lap.
 *
 * THIS TOUR'S FIELD MAKES THE CLAIM EXACTLY TRUE, WHICH IS A GIFT. Tour VI's field is a swirl about
 * the rail plus a drift along it, so its curl is very nearly the uniform vector (0, ~0.01, 0.9).
 * The flux of a uniform vector through any surface spanning a fixed loop is identically the same
 * number. Nothing here is being fudged into agreement; the two measurements agree because they
 * cannot do anything else.
 *
 * THE WHEELS ARE MEASURED, NOT DECORATED. Each one takes four field samples on a small square in
 * the local tangent plane, and its spin is that circulation divided by the square's area — the
 * normal component of the curl, obtained the way a paddle wheel obtains it. Warm wheels turn one
 * way and teal ones the other, and on the sock's flanks, where the skin has folded past
 * perpendicular, whole patches change colour. That reversal is the honest half of the picture: the
 * inside is not merely rearranged, some of it changes sign, and the total still does not care.
 *
 * THE SPIN PHASE IS TIED TO THE LOOP CLOCK, DELIBERATELY. A wheel's angle wants to be the integral
 * of its rate, and no scene here may keep state between frames. Using rate × kit.seconds instead
 * is worse than it looks: seconds is not actually wrapped, so by twenty minutes in, a rate that
 * changes by a fifth per second during a morph adds a hundred radians a second of spurious spin.
 * So the phase runs on the loop's own clock, 0..PERIOD, and the seam where that clock resets is
 * hidden inside the beat where the film is faded out to nothing. Nothing is visible at the wrap,
 * so nothing jumps at it.
 *
 * COUNT AND HONESTY. The design note asks for hundreds of wheels appearing and vanishing. Two dozen
 * is what a 640x480 waveguide and a thirty-call budget will actually carry, and two dozen wheels
 * that visibly swell where the skin is stretched and reverse where it folds says the same thing
 * more legibly than a haze of hundreds would. It is a sampling of a continuum and the crew says so.
 *
 * PLACEMENT. Off to port, centred 1.88 units out, hoop radius 0.92, its aperture facing down the
 * rail so the loop is looked THROUGH on the approach. The design note has the ship fly through the
 * loop; that is the one instruction here not taken literally, because a hoop centred on the rail is
 * a hoop you are inside at the closest point of the pass, with only a corner of it in frame and the
 * film across your face. The sock reaching down to within 1.9 units of the hull is the part of "past
 * the ship" that survives, and it is the part that reads. Nothing in the scene comes nearer the rail
 * than the loop's inboard edge at 0.96, and nothing goes further out than a gauge label at 3.9,
 * against a passage radius of 4.2 — wide for a landmark, but this is a Tour VI stop and the wall
 * here is a ghost at alpha 0.15.
 *
 * BUDGET. One flushTris (the film and the two bars), two flushLines (skin and wheels at 2px, then
 * the rim and the gauges at 3.4px, so the wire that never moves is visibly the heaviest thing in
 * the scene), one ball for the bead running the rim, and at most five labels. Nine draw calls.
 */
object SceneRim : MathScene {

    /** Big, and the morph should be well under way before the craft is alongside. */
    override val reach = 1.6f

    private const val TAU = 6.2831855f

    // ------------------------------------------------------------------ the loop clock
    // Fade in as a disc, hold, out to the dome, hold, out to the sock, hold, back to the disc,
    // rest, fade the film away, and finish on the rim alone. The design note asks for eight seconds
    // a leg; five is what fits beside three holds and a punch line inside a thirty-second pass, and
    // five still reads as "being pulled" rather than as a cut.
    private const val PERIOD = 30f
    private const val UP_LEN = 0.05f       // the film arrives out of nothing
    private const val M1_AT = 0.12f        // disc -> dome
    private const val M1_LEN = 0.18f
    private const val M2_AT = 0.38f        // dome -> sock
    private const val M2_LEN = 0.18f
    private const val M3_AT = 0.62f        // sock -> disc
    private const val M3_LEN = 0.16f
    private const val DOWN_AT = 0.84f      // and away, leaving the rim
    private const val DOWN_LEN = 0.05f
    private const val LAPS = 5             // whole laps of the bead per cycle, so it does not jump at the wrap

    // ------------------------------------------------------------------ the figure
    private const val R = 0.92f            // the wire loop's radius
    private const val SIDE = -1.88f        // ... and where it hangs, in rail-frame units
    private const val UP = 0.12f

    // The three shapes, as (across, up, along-rail, throat) displacements of the film's centre.
    // The fourth number bends the profile from (1-u²) toward (1-u²)², which is what turns a wide
    // cone into a bag with a neck — the difference between a dome pulled out and a sock hanging.
    //
    // These were tuned against the area gauge, not by eye. Any profile with p(0) = 1 and p(1) = 0
    // has its steepest slope out near the rim, where the cells are biggest, so area is dominated by
    // the outermost ring and a deep dome very nearly matches a sock for area — which would leave
    // the gauge doing nothing between the second and third leg. The dome is pulled back to 1.25 and
    // the sock deepened until the three states read 1.00, 2.04 and 2.87 times the flat disc, which
    // is a third of the bar between each pair.
    private val DISC = floatArrayOf(0f, 0f, 0f, 0f)
    private val DOME = floatArrayOf(0.00f, 0.05f, -1.25f, 0.12f)
    private val SOCK = floatArrayOf(0.62f, -1.55f, -1.35f, 0.80f)

    // ------------------------------------------------------------------ the skin
    private const val MESH_U = 5           // radial cells of the drawn wireframe
    private const val MESH_V = 14          // ... and around
    private const val MESH_U_MAX = 5
    private const val SKIN = 0.30f         // the wire's alpha at full strength
    private const val FILM = 0.085f        // and the film's, which only has to say "this is a sheet"

    // ------------------------------------------------------------------ the wheels
    // The measuring lattice is fixed at 4 x 8 whatever the governor is doing, so that the numbers on
    // the gauges never change when the drawing detail does. Which of those cells gets a wheel drawn
    // on it is what quality decides. The innermost ring is measured but never drawn: at u = 0.125
    // eight wheels would sit on top of one another.
    private const val CELL_U = 4
    private const val CELL_V = 8
    private const val PROBE = 0.085f       // half-side of the square the circulation is taken round
    private const val SPIN = 3.0f          // radians of turn per unit of curl per second of loop clock
    private const val SPOKES = 3
    private const val WHEEL = 0.072f
    private const val CURL_REF = 1.0f      // where the colour ramp saturates

    // ------------------------------------------------------------------ the gauges
    private const val BAR_BASE = -0.95f
    private const val BAR_H = 1.85f
    private const val BAR_W = 0.075f
    private const val BAR_A_S = -3.02f     // the skin's area
    private const val BAR_T_S = -3.30f     // and the total, which is the one that does not move
    private const val AREA_FULL = 3.2f     // area ratios above this saturate the bar
    private const val TOTAL_FULL = 4.0f
    private const val HORN = 0.14f         // the reference mark is wider than the bar it sits on

    private const val RIM_SAMPLES = 24     // fixed at every quality: the total must not shift with detail
    private const val ARROWS = 8
    private const val ARROW_SCALE = 0.28f
    private const val ARROW_OUT = 1.13f    // ... drawn just outside the wire, so it is not the wire

    // ------------------------------------------------------------------ scratch
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val sh = FloatArray(4)
    private val o = FloatArray(3)
    private val pa = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val nrm = FloatArray(3)
    private val e1 = FloatArray(3)
    private val e2 = FloatArray(3)
    private val fv = FloatArray(3)
    private val rowA = FloatArray((MESH_U_MAX + 1) * 3)
    private val rowB = FloatArray((MESH_U_MAX + 1) * 3)
    private val tv = IntArray(1)
    // Four components: the lit shader reads base[3] as an alpha multiplier, so a three-component
    // tint would crash the moment it reached kit.ball.
    private val tint = FloatArray(4)

    // What the last drawn frame actually measured, for the HUD. Same arrangement the other Tour VI
    // scenes use: the number the readout prints is the number the geometry drew, so the two can
    // never disagree. draw() runs once per eye with identical state, so this is stable.
    private var areaRatio = 1f
    private var totalRim = 0f
    private var measured = false

    /** Two decimal places, without allocating a formatter. */
    private fun fmt(v: Float): String {
        val cents = (abs(v) * 100f + 0.5f).toInt()
        val frac = cents % 100
        return (if (v < -0.005f) "-" else "") + (cents / 100) + "." + (if (frac < 10) "0" else "") + frac
    }

    /**
     * The skin's area against the flat disc's, and the push round the rim. Those two numbers side
     * by side ARE the stop: one of them is doing all the moving and it is not the one that decides
     * the answer.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasField || !measured) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val tag = if (c >= DOWN_AT + DOWN_LEN) "   RIM ONLY" else ""
        return "SKIN x" + fmt(areaRatio) + "   TOTAL " + fmt(abs(totalRim)) + tag
    }

    // -------------------------------------------------------------------- the shape

    /** The film's four shape numbers at loop phase [c], into [sh]. */
    private fun shapeAt(c: Float) {
        val a = SceneParts.step(c, M1_AT, M1_LEN)
        val b = SceneParts.step(c, M2_AT, M2_LEN)
        val d = SceneParts.step(c, M3_AT, M3_LEN)
        for (k in 0 until 4) {
            var s = DISC[k] + (DOME[k] - DISC[k]) * a
            s += (SOCK[k] - s) * b
            s += (DISC[k] - s) * d
            sh[k] = s
        }
    }

    /** The profile: 1 at the centre, exactly 0 at the rim, whatever the shape. */
    private fun profile(u: Float): Float {
        val p1 = 1f - u * u
        return p1 * (1f - sh[3] + sh[3] * p1)
    }

    /** A point of the film at radial [u] and angular [v] (both 0..1), in world space. */
    private fun surf(u: Float, v: Float, out: FloatArray) {
        val th = v * TAU
        val p = profile(u)
        SceneParts.at(g, R * u * cos(th) + sh[0] * p, R * u * sin(th) + sh[1] * p, sh[2] * p, out)
    }

    /** The two tangents there, as world vectors: [outU] along the radius, [outV] around. */
    private fun tangents(u: Float, v: Float, outU: FloatArray, outV: FloatArray) {
        val th = v * TAU
        val cs = cos(th)
        val sn = sin(th)
        val n = sh[3]
        val dp = -2f * u * ((1f - n) + 2f * n * (1f - u * u))
        SceneParts.vec(g, R * cs + sh[0] * dp, R * sn + sh[1] * dp, sh[2] * dp, outU)
        SceneParts.vec(g, -R * u * sn * TAU, R * u * cs * TAU, 0f, outV)
    }

    private fun cross(a: FloatArray, b: FloatArray, out: FloatArray) {
        out[0] = a[1] * b[2] - a[2] * b[1]
        out[1] = a[2] * b[0] - a[0] * b[2]
        out[2] = a[0] * b[1] - a[1] * b[0]
    }

    private fun norm(a: FloatArray): Float = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])

    private fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    /**
     * The normal component of the curl at a point, taken the way a paddle wheel takes it: the
     * circulation round a small square in the ([e1], [e2]) plane, divided by that square's area.
     * Four field samples, one per side, each read at the side's midpoint.
     */
    private fun curlAt(kit: SceneKit, px: Float, py: Float, pz: Float): Float {
        var s = 0f
        kit.fieldAt(px - e2[0] * PROBE, py - e2[1] * PROBE, pz - e2[2] * PROBE, fv)
        s += dot(fv, e1)
        kit.fieldAt(px + e1[0] * PROBE, py + e1[1] * PROBE, pz + e1[2] * PROBE, fv)
        s += dot(fv, e2)
        kit.fieldAt(px + e2[0] * PROBE, py + e2[1] * PROBE, pz + e2[2] * PROBE, fv)
        s -= dot(fv, e1)
        kit.fieldAt(px - e1[0] * PROBE, py - e1[1] * PROBE, pz - e1[2] * PROBE, fv)
        s -= dot(fv, e2)
        // circulation = 2·PROBE·s, area = 4·PROBE², so the two constants collapse to this.
        return s / (2f * PROBE)
    }

    /** Two triangles across one cell of the film, between row [ra] and row [rb] at offsets i0, i1. */
    private fun face(
        out: FloatArray, at: Int, ra: FloatArray, rb: FloatArray, i0: Int, i1: Int,
        cr: Float, cg: Float, cb: Float, al: Float
    ): Int {
        if ((at + 6) * MathMesh.STRIDE > out.size) return at
        var k = MathMesh.vertex(out, at, ra[i0], ra[i0 + 1], ra[i0 + 2], cr, cg, cb, al)
        k = MathMesh.vertex(out, k, ra[i1], ra[i1 + 1], ra[i1 + 2], cr, cg, cb, al)
        k = MathMesh.vertex(out, k, rb[i1], rb[i1 + 1], rb[i1 + 2], cr, cg, cb, al)
        k = MathMesh.vertex(out, k, ra[i0], ra[i0 + 1], ra[i0 + 2], cr, cg, cb, al)
        k = MathMesh.vertex(out, k, rb[i1], rb[i1 + 1], rb[i1 + 2], cr, cg, cb, al)
        k = MathMesh.vertex(out, k, rb[i0], rb[i0 + 1], rb[i0 + 2], cr, cg, cb, al)
        return k
    }

    /** One vertical gauge: an empty frame, a fill to [h] of full, and a bright cap. */
    private fun gauge(
        kit: SceneKit, line: FloatArray, lv: Int, s: Float, h: Float, c: FloatArray, alpha: Float
    ): Int {
        var v = lv
        SceneParts.at(g, s - BAR_W, BAR_BASE, 0f, o)
        SceneParts.vec(g, BAR_W * 2f, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAR_H, 0f, dv)
        v = SceneParts.edge(line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.STEEL, 0.26f * alpha)
        val fh = (h * BAR_H).coerceIn(0f, BAR_H)
        SceneParts.vec(g, 0f, fh, 0f, dv)
        tv[0] = SceneParts.fill(kit.triBuf, tv[0], o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], c, 0.34f * alpha)
        SceneParts.at(g, s - BAR_W, BAR_BASE + fh, 0f, o)
        SceneParts.at(g, s + BAR_W, BAR_BASE + fh, 0f, pa)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], pa[0], pa[1], pa[2],
            c[0], c[1], c[2], 0.95f * alpha)
        return v
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No field, no curl, no circulation: there is nothing for this stop to weigh.
        if (!kit.hasField) return

        val q = kit.quality
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        shapeAt(c)
        // The film's strength. It is exactly zero at c = 0 and again from DOWN_AT + DOWN_LEN to the
        // end of the cycle, which is what makes the loop seamless AND hides the spin clock's reset.
        val film = SceneParts.step(c, 0f, UP_LEN) * (1f - SceneParts.step(c, DOWN_AT, DOWN_LEN))
        val tSpin = c * PERIOD

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val mu = if (q == 0) MESH_U else if (q == 1) 4 else 3
        val mv = if (q == 0) MESH_V else if (q == 1) 10 else 8

        // --- the film ------------------------------------------------------------------------
        // Wire plus a very faint fill. A wireframe alone reads as a net rather than a sheet, and a
        // sheet is what has to be believed here; a solid one would hide the wheels on its far side
        // and the rim behind it, in a passage four units wide.
        if (film > 0.01f) {
            for (j in 0..mv) {
                val vv = j.toFloat() / mv
                for (k in 0..mu) {
                    val uu = k.toFloat() / mu
                    surf(uu, vv, o)
                    rowB[k * 3] = o[0]; rowB[k * 3 + 1] = o[1]; rowB[k * 3 + 2] = o[2]
                    if (k > 0) {
                        v = MathMesh.segment(
                            line, v, rowB[(k - 1) * 3], rowB[(k - 1) * 3 + 1], rowB[(k - 1) * 3 + 2],
                            rowB[k * 3], rowB[k * 3 + 1], rowB[k * 3 + 2],
                            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], SKIN * film
                        )
                    }
                    if (j > 0) {
                        v = MathMesh.segment(
                            line, v, rowA[k * 3], rowA[k * 3 + 1], rowA[k * 3 + 2],
                            rowB[k * 3], rowB[k * 3 + 1], rowB[k * 3 + 2],
                            SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], SKIN * film
                        )
                        if (k > 0 && q < 2) {
                            tv[0] = face(
                                tri, tv[0], rowA, rowB, (k - 1) * 3, k * 3,
                                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], FILM * film
                            )
                        }
                    }
                }
                System.arraycopy(rowB, 0, rowA, 0, (mu + 1) * 3)
            }
        }

        // --- the wheels, and the survey ---------------------------------------------------------
        // One pass over the fixed measuring lattice. Area is analytic and costs nothing; the curl is
        // four field samples and is taken only where it is going to be used.
        var area = 0f
        var surveyed = 0f
        val dA = (1f / CELL_U) * (1f / CELL_V)
        val wantSurvey = q == 0 && film > 0.05f
        for (ci in 0 until CELL_U) {
            val uu = (ci + 0.5f) / CELL_U
            for (cj in 0 until CELL_V) {
                val vv = (cj + 0.5f) / CELL_V
                tangents(uu, vv, du, dv)
                cross(du, dv, nrm)
                val nl = norm(nrm)
                area += nl * dA
                if (nl < 1e-5f) continue

                // Draw a wheel here? The inner ring is measured but never drawn — eight wheels on a
                // circle of radius 0.11 would be one blur.
                val drawn = ci > 0 && film > 0.02f && when {
                    q == 0 -> true
                    q == 1 -> cj % 2 == 0
                    else -> cj % 2 == 0 && ci % 2 == 1
                }
                if (!drawn && !wantSurvey) continue

                surf(uu, vv, o)
                // A tangent frame with e1 along the radius and (e1, e2, n) right-handed, so the
                // circulation this reads is the component along the surface's own normal.
                val dl = norm(du)
                if (dl < 1e-5f) continue
                e1[0] = du[0] / dl; e1[1] = du[1] / dl; e1[2] = du[2] / dl
                cross(nrm, e1, e2)
                val el = norm(e2)
                if (el < 1e-5f) continue
                e2[0] /= el; e2[1] /= el; e2[2] /= el

                val curlN = curlAt(kit, o[0], o[1], o[2])
                surveyed += curlN * nl * dA
                if (!drawn) continue

                // Warm one way, teal the other. On the sock's flanks whole patches turn over, which
                // is the part of the picture that is easy to miss and hard to argue with.
                val t = (abs(curlN) / CURL_REF).coerceIn(0f, 1f)
                val col = if (curlN >= 0f) SceneParts.WORK else SceneParts.ADDED
                val al = (0.32f + 0.58f * t) * film

                // The wheel swells where the skin has been stretched — the area element against
                // the flat disc's at the same cell, which is TAU R² u exactly.
                val ref = TAU * R * R * uu
                val stretch = sqrt((nl / ref).coerceIn(0.25f, 4f)).coerceIn(0.7f, 1.9f)
                val ringCap = 0.40f * TAU * R * uu / CELL_V
                val rad = (WHEEL * stretch).coerceAtMost(ringCap).coerceAtMost(0.42f * R / CELL_U)

                v = MathMesh.arc(
                    line, v, o[0], o[1], o[2], e1[0], e1[1], e1[2], e2[0], e2[1], e2[2],
                    rad, 0f, TAU, 6, col[0], col[1], col[2], al * 0.7f
                )
                val phase = SPIN * curlN * tSpin
                for (sp in 0 until SPOKES) {
                    val ang = phase + sp * (TAU / SPOKES)
                    val ca = cos(ang) * rad
                    val sa = sin(ang) * rad
                    v = MathMesh.segment(
                        line, v, o[0], o[1], o[2],
                        o[0] + e1[0] * ca + e2[0] * sa,
                        o[1] + e1[1] * ca + e2[1] * sa,
                        o[2] + e1[2] * ca + e2[2] * sa,
                        col[0], col[1], col[2], al
                    )
                }
            }
        }
        kit.flushLines(v, 2f)

        // --- the rim, the push round it, and the gauges -----------------------------------------
        // A second pass at a heavier width. The wire that never moves should be the heaviest thing
        // in the frame, and it is the only thing here that never fades.
        v = 0
        val rimSegs = if (q == 0) 40 else if (q == 1) 26 else 18
        v = MathMesh.arc(
            line, v, g[0], g[1], g[2], g[3], g[4], g[5], g[6], g[7], g[8],
            R, 0f, TAU, rimSegs, SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 1f
        )

        // The circulation, on a sample count that never changes with quality: this number is the
        // answer, and it must not shift when the thermal governor does.
        var circ = 0f
        val ds = TAU * R / RIM_SAMPLES
        for (m in 0 until RIM_SAMPLES) {
            val th = (m + 0.5f) / RIM_SAMPLES * TAU
            val cs = cos(th)
            val sn = sin(th)
            SceneParts.at(g, R * cs, R * sn, 0f, o)
            SceneParts.vec(g, -sn, cs, 0f, du)
            kit.fieldAt(o[0], o[1], o[2], fv)
            circ += dot(fv, du) * ds
        }

        // The blades with no partner to cancel against. Some of them point backwards, which is the
        // whole reason the total is a sum and not a count.
        //
        // The beat is eased back down over the last of the cycle rather than being switched off at
        // the wrap: it drives the bead's glow, and a bead that halves in brightness between two
        // frames reads as a fault in the display rather than as the end of a thought.
        val rimBeat = SceneParts.step(c, DOWN_AT, DOWN_LEN) * (1f - SceneParts.step(c, 0.965f, 0.035f))
        // They are set a hair outside the wire rather than on it: a tangential arrow lying along the
        // rim, at the same line width as the rim, is indistinguishable from the rim. The number on
        // the gauge is still taken on the wire itself, above; these are the picture of it.
        if (q < 2) {
            val stations = if (q == 0) ARROWS else ARROWS - 3
            for (m in 0 until stations) {
                val th = m.toFloat() / stations * TAU
                val cs = cos(th)
                val sn = sin(th)
                SceneParts.at(g, R * ARROW_OUT * cs, R * ARROW_OUT * sn, 0f, o)
                SceneParts.vec(g, -sn, cs, 0f, du)
                kit.fieldAt(o[0], o[1], o[2], fv)
                val push = dot(fv, du) * ARROW_SCALE
                val col = if (push >= 0f) SceneParts.WORK else SceneParts.ADDED
                v = MathMesh.arrow(
                    line, v, o[0], o[1], o[2], du[0] * push, du[1] * push, du[2] * push,
                    o[0] - kit.camX, o[1] - kit.camY, o[2] - kit.camZ,
                    col[0], col[1], col[2], 0.55f + 0.40f * rimBeat, 0.34f
                )
            }
        }

        // The two gauges. The left one is the skin's area against the flat disc's and it dances.
        // The right one is the push round the rim and it does not.
        areaRatio = area / (3.14159265f * R * R)
        totalRim = circ
        measured = true
        v = gauge(kit, line, v, BAR_A_S, areaRatio / AREA_FULL, SceneParts.COOL, 1f)
        val th = (abs(circ) / TOTAL_FULL).coerceIn(0f, 1f)
        v = gauge(kit, line, v, BAR_T_S, th, SceneParts.HOT, 1f)

        // The mark the cap has never left. It is drawn from the same live number as the cap,
        // because there is nothing to remember: pretending to compare against a stored value would
        // be theatre, and the claim is precisely that there is nothing to compare.
        SceneParts.at(g, BAR_T_S - HORN, BAR_BASE + th * BAR_H, 0f, o)
        SceneParts.at(g, BAR_T_S + HORN, BAR_BASE + th * BAR_H, 0f, pa)
        v = MathMesh.segment(line, v, o[0], o[1], o[2], pa[0], pa[1], pa[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.75f)

        // And the surveyed sum over the skin, at full detail only: the same quantity reached the
        // hard way. It sits on the mark and trembles, and the tremble is the thirty-two cells,
        // not the theorem.
        if (q == 0 && film > 0.05f) {
            val sy = (abs(surveyed) / TOTAL_FULL).coerceIn(0f, 1f)
            SceneParts.at(g, BAR_T_S - BAR_W * 0.8f, BAR_BASE + sy * BAR_H, 0f, o)
            SceneParts.at(g, BAR_T_S + BAR_W * 0.8f, BAR_BASE + sy * BAR_H, 0f, pa)
            v = MathMesh.segment(line, v, o[0], o[1], o[2], pa[0], pa[1], pa[2],
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.85f * film)
        }
        kit.flushLines(v, 3.4f)
        kit.flushTris(tv[0])

        // --- the one lap ------------------------------------------------------------------------
        // A whole number of laps per cycle, so the bead is in the same place at both ends of the
        // loop and never jumps. It brightens when the film is gone and it is all that is left.
        val lap = c * LAPS * TAU
        SceneParts.at(g, R * cos(lap), R * sin(lap), 0f, o)
        tint[0] = SceneParts.HOT[0]; tint[1] = SceneParts.HOT[1]
        tint[2] = SceneParts.HOT[2]; tint[3] = 1f
        kit.ball(
            o[0], o[1], o[2], 0.055f, 0.055f, 0.055f, tint, SceneParts.WORK, 1f,
            glow = 0.9f + 1.4f * rimBeat + 2.2f * kit.beat, small = false
        )

        // --- notation ---------------------------------------------------------------------------
        // Everything here names a piece of geometry and sits beside it. Two notes on the choices:
        //
        // ∮ and ∬ are not in the set GlyphBoard guarantees — it rasterises with the platform's own
        // serif face, and those two live outside what an Android device is certain to carry, so a
        // missing one would come out as a tofu box on the glasses and nowhere else. ∫ is
        // guaranteed, and ∫ with ∂S written under it says the same thing with no risk.
        //
        // Both gauge labels hang outboard of the OUTER gauge, stacked, rather than one beside each
        // — a label placed beside the inner gauge has to cross the outer one's frame to be read.
        // Each takes its own gauge's colour, which is what ties it to its bar.
        // ∂S sits on the loop's upper-inboard radial, clear of the push arrows just outside the wire.
        SceneParts.at(g, (R + 0.36f) * cos(0.96f), (R + 0.36f) * sin(0.96f), 0f, o)
        kit.text("∂S", o[0], o[1], o[2], 0.20f, SceneParts.HOT, 1f)

        if (q < 2) {
            SceneParts.at(g, BAR_T_S - BAR_W - 0.15f, BAR_BASE + BAR_H * 0.31f, 0f, o)
            kit.text("∫_{∂S} F·dr", o[0], o[1], o[2], 0.145f, SceneParts.HOT, 1f,
                GlyphBoard.Style.MATH, 1f, anchor = 0.5f)

            // On the sheet, and riding with it: the label stretches where the thing it names does.
            if (film > 0.05f) {
                surf(0.45f, 0.40f, o)
                kit.text("S", o[0], o[1], o[2], 0.19f, SceneParts.COOL, 0.85f * film + 0.15f)
            }
        }

        if (q == 0) {
            SceneParts.at(g, BAR_T_S - BAR_W - 0.15f, BAR_BASE + BAR_H * 0.85f, 0f, o)
            kit.text("∫∫ dA", o[0], o[1], o[2], 0.145f, SceneParts.COOL, 0.95f,
                GlyphBoard.Style.MATH, 1f, anchor = 0.5f)

            // What the wheels are, hung level with the top of the loop and outboard of it, so it
            // is beside the skin rather than over it and clear of both gauge frames.
            SceneParts.at(g, -(R + 0.10f), 1.02f, 0f, o)
            kit.text("(∇×F)·n", o[0], o[1], o[2], 0.145f, SceneParts.WORK, 0.90f,
                GlyphBoard.Style.MATH, 1f, anchor = 0.5f)
        }
    }
}
