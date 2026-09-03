package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 8 of Tour IV — THE PRICE OF AGREEMENT. "The error isn't a vague worry; it's a thickness I
 * can see and bound."
 *
 * The stop before this one built the matching curves; this one puts a solid into the gap they left.
 * One order at a time, the space between the corridor's roof — the true function, drawn by the
 * ambient scene and never by this one — and the Taylor polynomial about this stop is filled with a
 * slab of coloured light whose thickness IS the remainder. It pinches to nothing at the anchor and
 * flares open fore and aft, and because it runs a whole leg of the corridor you pass along the
 * flare rather than looking at a picture of one.
 *
 * Then the order climbs, one beat at a time, and the slab thins everywhere: about half a unit thick
 * at order 0 on this tour's roof curve, a thousandth of one at order 4. The eye reads that as the
 * wedge folding shut. What the eye cannot read is HOW far it has shut once the sheet is thinner
 * than a line, which is exactly why the number is on the HUD: at a station a quarter of a node
 * either side of the anchor the remainder falls 1.1e-1, 8.8e-3, 7.2e-4, 2.9e-5, 1.5e-6 as the order
 * rises. That is this stop's rung, 10⁻¹ down to 10⁻⁶, and those are the five numbers the ladder on
 * the HUD is counting off.
 *
 * The peel marks are the part that keeps working when the sheet has gone. Each is the nearest place
 * to the anchor where the gap first exceeds six thousandths of a unit, and as the order rises the
 * pair marches away down the corridor in both directions and then leaves the figure entirely. That
 * march is the stop's headline and it is legible at every order, including the ones where there is
 * nothing left to see between the ribbon and the roof.
 *
 * Four decisions worth stating, because the crew says all four out loud and the code should agree.
 *
 * First, the slab does not sit in the rail's own vertical plane. It cannot: a flat figure in the
 * plane spanned by the rail and the up vector projects, from a camera on the rail, onto a single
 * vertical line — the wedge would collapse to a bright stripe and show nothing at all. So it is a
 * SLAB rather than a sheet, swept from the rail plane (where its upper edge meets the trace, so the
 * identification needs no explaining) out to one side, where you see it end-on and read its shape.
 * The design asks for a sheet you fly through; you fly alongside and beneath this one, because the
 * roof at this stop is only about a unit overhead.
 *
 * Second, the stretch is 0.95 of a node either way and not more. A slab that reached a full node
 * would grow a bigger, better flare — and would also be standing inside the neighbouring stops'
 * figures while the craft was looking at those. The neighbours win.
 *
 * Third, the polynomial is built from finite differences of the roof curve rather than from a
 * hard-coded formula, so that re-cutting the tour's trace re-cuts this scene with it. Five-point
 * central differences for the first two derivatives, because those two set where the ribbon sits at
 * every order and a one-percent slip in f' is larger than the whole order-4 remainder; the usual
 * stencils above that, good to a few percent, which is invisible against a sheet already too thin
 * to see.
 *
 * Fourth, the bound. The design hangs a plane above the sheet that the sheet never touches; here it
 * is the two-sided Lagrange envelope, roof ± M|t|^{n+1}/(n+1)!, drawn dashed so it reads as a
 * promise rather than as an object. M is the largest (n+1)-th derivative MEASURED at three stations
 * across the stretch, given a quarter's headroom — a working bound taken off the corridor, not a
 * proof. It holds at every order and it hugs: at order 2 the envelope sits about forty per cent
 * above the worst gap. Doc says out loud that it is measured; so does this.
 */
object ScenePriceOfAgreement : MathScene {

    // A corridor-length object has to fade up before its near end arrives and survive being culled
    // while its far end is still ahead of the viewer.
    override val reach = 1.6f
    override val deep = 1.1f

    private const val SPAN = 0.95f          // half the drawn stretch, in node units (~15 world units)
    private const val SIDE_OUT = -1.05f     // the slab is swept from the rail plane out to here
    private const val PROBE = 0.25f         // where the HUD's number is taken, in node units
    private const val TOL = 0.006f          // the tolerance the peel marks are measured against
    private const val FLARE = 0.10f         // gap at which a panel has gone fully to the debt colour
    private const val PERIOD = 26f
    private const val BEAT = 0.165f         // one order per beat; the tail of the cycle is the rest
    private const val ORDERS = 5
    private const val REST = BEAT * ORDERS  // 0.825 — after this the fan of every order opens
    private const val DH = 0.42f            // finite-difference step, node units — see [derivs]
    private const val HEADROOM = 1.25f      // slack on the measured bound constant
    private const val MAXS = 44             // samples along the stretch at full quality
    private const val MAXST = 9             // rail frames queried; everything between is blended

    /**
     * Red for the flat guess, warm for the tangent, then cooling to chalk as the agreement gets
     * expensive and good. A viewer arriving mid-loop can tell the order from the colour alone.
     */
    private val TINT = arrayOf(
        SceneParts.TAKEN, SceneParts.WORK, SceneParts.ADDED, SceneParts.COOL, SceneParts.CHALK
    )
    private val ORDER_TAG = arrayOf("P_0", "P_1", "P_2", "P_3", "P_4")
    private val ERR_TAG = arrayOf(
        "R_0 = f − P_0", "R_1 = f − P_1", "R_2 = f − P_2", "R_3 = f − P_3", "R_4 = f − P_4"
    )
    private const val BOUND_TAG = "|R_n| ≤ M|t|^{n+1}/(n+1)!"
    private val FACT = floatArrayOf(1f, 1f, 2f, 6f, 24f, 120f)

    // ---- scratch. Nothing here survives a frame; all of it exists so draw() allocates nothing ----
    private val H = FloatArray(7)           // the roof sampled at a-3d .. a+3d
    private val dAt = FloatArray(6)         // f, f', f'' … f⁽⁵⁾ at the anchor
    private val dLo = FloatArray(6)         // the same, back along the corridor
    private val dHi = FloatArray(6)         // and forward; the two of them give the bound its M
    private val stf = FloatArray(MAXST * 12)
    private val tmpF = FloatArray(12)
    private val uRoof = FloatArray(MAXS + 1)
    private val uPoly = FloatArray(MAXS + 1)
    private val pR = FloatArray(3); private val pP = FloatArray(3); private val pQ = FloatArray(3)
    private val cR = FloatArray(3); private val cP = FloatArray(3); private val cQ = FloatArray(3)
    private val pG = FloatArray(3); private val cG = FloatArray(3)
    private val pU = FloatArray(3); private val cU = FloatArray(3)
    private val pD = FloatArray(3); private val cD = FloatArray(3)
    private val w0 = FloatArray(3); private val w1 = FloatArray(3)
    private var nst = MAXST                 // stations actually loaded this frame
    private var uMax = 2.4f                 // how high anything may reach before it is in the wall

    // ---- the function, and its Taylor data ---------------------------------------------------

    /**
     * The roof and its first five derivatives at [p], into [out].
     *
     * The step is a deliberate compromise. Small enough that the low derivatives are accurate;
     * large enough that f⁽⁵⁾ — a difference of six numbers that very nearly cancel — does not
     * dissolve into float noise. Four tenths of a node holds both ends of that to a few percent
     * on a trace of this frequency, and every consumer of these numbers is told what they cost.
     */
    private fun derivs(kit: SceneKit, p: Float, out: FloatArray) {
        for (k in 0..6) H[k] = kit.traceHeight(p + (k - 3) * DH)
        val d = DH
        val d2 = d * d; val d3 = d2 * d; val d4 = d3 * d; val d5 = d4 * d
        out[0] = H[3]
        out[1] = (-H[5] + 8f * H[4] - 8f * H[2] + H[1]) / (12f * d)
        out[2] = (-H[5] + 16f * H[4] - 30f * H[3] + 16f * H[2] - H[1]) / (12f * d2)
        out[3] = (H[5] - 2f * H[4] + 2f * H[2] - H[1]) / (2f * d3)
        out[4] = (H[5] - 4f * H[4] + 6f * H[3] - 4f * H[2] + H[1]) / d4
        out[5] = (H[6] - 4f * H[5] + 5f * H[4] - 5f * H[2] + 4f * H[1] - H[0]) / (2f * d5)
    }

    /** The Taylor polynomial of order [m], [t] node units from the anchor. */
    private fun poly(m: Int, t: Float): Float {
        var s = dAt[0]
        var p = 1f
        for (k in 1..m) { p *= t; s += dAt[k] / FACT[k] * p }
        return s
    }

    /** |t| to the (m+1)-th, without a pow() call. */
    private fun power(m: Int, t: Float): Float {
        var p = 1f
        val a = abs(t)
        for (k in 0..m) p *= a
        return p
    }

    /** The bound's constant: the biggest (m+1)-th derivative seen on the stretch, plus headroom. */
    private fun supDeriv(m: Int): Float {
        val k = m + 1
        return HEADROOM * max(abs(dAt[k]), max(abs(dLo[k]), abs(dHi[k])))
    }

    private fun bound(m: Int, t: Float): Float = supDeriv(m) * power(m, t) / FACT[m + 1]

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree with the scene ----

    private fun orderAt(kit: SceneKit): Int =
        (SceneParts.cycle(kit.seconds, PERIOD) / BEAT).toInt().coerceIn(0, ORDERS - 1)

    /** 0..1 through the current beat, and 1 once the cycle has settled into its rest. */
    private fun beatFrac(kit: SceneKit): Float {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        if (c >= REST) return 1f
        val x = c / BEAT
        return x - x.toInt()
    }

    private fun samples(q: Int) = when (q) { 0 -> MAXS; 1 -> 26; else -> 16 }
    private fun stationCount(q: Int) = when (q) { 0 -> MAXST; 1 -> 7; else -> 5 }
    private fun tAt(j: Int, ns: Int) = -SPAN + 2f * SPAN * j / ns

    /**
     * What this stop measures: the remainder at a fixed station either side of the anchor, and the
     * bound promised for it. Two numbers and an inequality, because the inequality is the whole
     * content of the stop and neither number can be read off a sheet this thin.
     *
     * The remainder here is the leading term |c_{n+1}| t^{n+1} and not the drawn gap. At order 4
     * the drawn gap is smaller than the error in the finite differences the ribbon is built from,
     * so measuring it would report the arithmetic rather than the mathematics.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        val a = kit.progress.toInt().toFloat()
        derivs(kit, a, dAt)
        derivs(kit, a - SPAN * 0.55f, dLo)
        derivs(kit, a + SPAN * 0.55f, dHi)
        val m = orderAt(kit)
        val r = abs(dAt[m + 1]) * power(m, PROBE) / FACT[m + 1]
        return "ORDER $m   |R| %.1e < %.1e".format(java.util.Locale.US, r, bound(m, PROBE))
    }

    // ---- placing things -----------------------------------------------------------------------

    /**
     * A world point [t] node units along the corridor from the anchor, [s] to the side and [u]
     * above the rail.
     *
     * The rail frames are queried at nine stations and blended between, the same economy the
     * ambient trace makes: a frame query builds a small object, this slab wants forty-five points
     * of it, and over a fifth of a node the rail turns by well under a degree, so the blend is
     * exact to far less than a pixel. Every point in the scene goes through here, which is what
     * keeps the whole figure built out of the rail's own side and up vectors with no axis assumed.
     */
    private fun world(t: Float, s: Float, u: Float, out: FloatArray) {
        val n = nst - 1
        var x = (t + SPAN) / (2f * SPAN) * n
        if (x < 0f) x = 0f
        if (x > n.toFloat()) x = n.toFloat()
        var k = x.toInt()
        if (k >= n) k = n - 1
        val w = x - k
        val o = k * 12; val q = o + 12
        val cx = stf[o] + (stf[q] - stf[o]) * w
        val cy = stf[o + 1] + (stf[q + 1] - stf[o + 1]) * w
        val cz = stf[o + 2] + (stf[q + 2] - stf[o + 2]) * w
        val sx = stf[o + 6] + (stf[q + 6] - stf[o + 6]) * w
        val sy = stf[o + 7] + (stf[q + 7] - stf[o + 7]) * w
        val sz = stf[o + 8] + (stf[q + 8] - stf[o + 8]) * w
        val ux = stf[o + 9] + (stf[q + 9] - stf[o + 9]) * w
        val uy = stf[o + 10] + (stf[q + 10] - stf[o + 10]) * w
        val uz = stf[o + 11] + (stf[q + 11] - stf[o + 11]) * w
        out[0] = cx + sx * s + ux * u
        out[1] = cy + sy * s + uy * u
        out[2] = cz + sz * s + uz * u
    }

    private fun copy3(from: FloatArray, to: FloatArray) {
        to[0] = from[0]; to[1] = from[1]; to[2] = from[2]
    }

    /**
     * One quadrilateral of the slab, as two triangles with an alpha at each of its four corners.
     * Not [MathMesh.quad], which wants a parallelogram: every panel here is a trapezoid, because
     * the gap it spans is a different size at each end. That is the entire point of the object.
     */
    private fun panel(
        tri: FloatArray, v: Int,
        a0: FloatArray, b0: FloatArray, a1: FloatArray, b1: FloatArray,
        r: Float, g: Float, b: Float,
        aa0: Float, ab0: Float, aa1: Float, ab1: Float
    ): Int {
        if ((v + 6) * MathMesh.STRIDE > tri.size) return v
        var k = MathMesh.vertex(tri, v, a0[0], a0[1], a0[2], r, g, b, aa0)
        k = MathMesh.vertex(tri, k, a1[0], a1[1], a1[2], r, g, b, aa1)
        k = MathMesh.vertex(tri, k, b1[0], b1[1], b1[2], r, g, b, ab1)
        k = MathMesh.vertex(tri, k, a0[0], a0[1], a0[2], r, g, b, aa0)
        k = MathMesh.vertex(tri, k, b1[0], b1[1], b1[2], r, g, b, ab1)
        k = MathMesh.vertex(tri, k, b0[0], b0[1], b0[2], r, g, b, ab0)
        return k
    }

    /** One peel mark: a stroke across the corridor where the agreement gave out. */
    private fun peel(line: FloatArray, v: Int, t: Float, roof: Float, flash: Float): Int {
        val c = SceneParts.TAKEN
        var k = v
        world(t, SIDE_OUT, roof - 0.34f, w0)
        world(t, SIDE_OUT, roof + 0.10f, w1)
        k = MathMesh.segment(line, k, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2], c[0], c[1], c[2], 0.08f, flash)
        world(t, SIDE_OUT - 0.26f, roof, w0)
        world(t, 0f, roof, w1)
        k = MathMesh.segment(line, k, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2], c[0], c[1], c[2], flash, 0.06f)
        return k
    }

    // ---- the landmark -------------------------------------------------------------------------

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // With no roof curve there is nothing to be the price of. Tours I, V and VI never reach
        // this scene, but a proving-ground rail might, and half a landmark is worse than none.
        if (!kit.hasTrace) return

        val a = i.toFloat()
        val q = kit.quality
        val ns = samples(q)
        nst = stationCount(q)

        derivs(kit, a, dAt)
        derivs(kit, a - SPAN * 0.55f, dLo)
        derivs(kit, a + SPAN * 0.55f, dHi)

        // The stations, spread across the drawn stretch rather than laid on whole nodes, so both
        // ends of the slab land exactly on a station and never on an extrapolation.
        for (k in 0 until nst) {
            kit.frame(a - SPAN + 2f * SPAN * k / (nst - 1), tmpF)
            System.arraycopy(tmpF, 0, stf, k * 12, 12)
        }

        // How high anything may reach. The slab is already a unit out to the side, so the ceiling
        // is whatever is left of eight tenths of the passage radius once that is paid for.
        val lid = 0.80f * kit.radius(a)
        uMax = sqrt(max(0.09f, lid * lid - SIDE_OUT * SIDE_OUT))

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val m = orderAt(kit)
        val bf = beatFrac(kit)
        val tint = TINT[m]
        val red = SceneParts.TAKEN

        // ---- what the gap actually is, sampled once -------------------------------------------
        // The roof is read here and nowhere else, and the slab, the ribbon, the peel marks and the
        // bound are all built off these numbers, so no two parts of the picture can disagree.
        var worst = 0f
        for (j in 0..ns) {
            val t = tAt(j, ns)
            uRoof[j] = kit.traceHeight(a + t).coerceIn(-uMax, uMax)
            uPoly[j] = poly(m, t).coerceIn(-uMax, uMax)
            val e = abs(uRoof[j] - uPoly[j])
            if (e > worst) worst = e
        }

        // Where the agreement gives out, against a tolerance we chose rather than one the theorem
        // handed us — and saying which is which is the honest half of this stop.
        val mid = ns / 2
        var jLo = -1
        var jHi = -1
        for (j in mid downTo 0) if (abs(uRoof[j] - uPoly[j]) >= TOL) { jLo = j; break }
        for (j in mid..ns) if (abs(uRoof[j] - uPoly[j]) >= TOL) { jHi = j; break }

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        // ---- the slab, the ribbon, the outgoing ghost and the bound, in one pass ----------------
        val ghostA = if (m == 0) 0f else (1f - bf / 0.45f).coerceIn(0f, 1f) * 0.55f
        for (j in 0..ns) {
            val t = tAt(j, ns)
            world(t, SIDE_OUT, uRoof[j], cR)
            world(t, SIDE_OUT, uPoly[j], cP)
            world(t, 0f, uPoly[j], cQ)

            val e = abs(uRoof[j] - uPoly[j])
            // Past the tolerance a panel bleeds towards the colour of a debt, so the flare is not
            // merely larger than the pinch, it is a different thing.
            val hot = ((e - TOL) / FLARE).coerceIn(0f, 1f)
            val cr = tint[0] + (red[0] - tint[0]) * hot
            val cg = tint[1] + (red[1] - tint[1]) * hot
            val cb = tint[2] + (red[2] - tint[2]) * hot
            val lo = 0.16f + 0.52f * (e / 0.26f).coerceAtMost(1f)

            if (j > 0) {
                val ep = abs(uRoof[j - 1] - uPoly[j - 1])
                val lop = 0.16f + 0.52f * (ep / 0.26f).coerceAtMost(1f)
                // The face you read: bright along the polynomial and fading to nothing as it meets
                // the roof, so the slab hangs FROM the trace without drawing a second trace.
                tv = panel(tri, tv, pP, pR, cP, cR, cr, cg, cb, lop, lop * 0.10f, lo, lo * 0.10f)
                // And its underside, swept back to the rail plane: what you look up at on the pass.
                if (q < 2) tv = panel(tri, tv, pQ, pP, cQ, cP, cr, cg, cb, lop * 0.28f, lop, lo * 0.28f, lo)
                // The ribbon. One bright line, and the only edge of the slab that is stroked at all.
                v = MathMesh.segment(line, v, pP[0], pP[1], pP[2], cP[0], cP[1], cP[2],
                    tint[0], tint[1], tint[2], 0.95f)
            }

            // The order before this one, dying away over the first half of each beat, so the drop
            // in error is a move you watch rather than a state you arrive to find.
            if (ghostA > 0.01f) {
                world(t, SIDE_OUT, poly(m - 1, t).coerceIn(-uMax, uMax), cG)
                if (j > 0) v = MathMesh.segment(line, v, pG[0], pG[1], pG[2], cG[0], cG[1], cG[2],
                    TINT[m - 1][0], TINT[m - 1][1], TINT[m - 1][2], ghostA)
                copy3(cG, pG)
            }

            // The bound: dashed, two-sided, shrinking with the order like everything else here.
            // Faded out as it nears the wall rather than cut off there, because a promise that
            // stops dead at a plane reads as geometry that ran out of buffer.
            if (q == 0) {
                val bd = bound(m, t)
                val up = (uRoof[j] + bd).coerceIn(-uMax, uMax)
                val dn = (uRoof[j] - bd).coerceIn(-uMax, uMax)
                world(t, SIDE_OUT, up, cU)
                world(t, SIDE_OUT, dn, cD)
                if (j > 0 && (j and 1) == 0) {
                    val st = SceneParts.STEEL
                    val ba = 0.34f * (1f - ((abs(up) - uMax * 0.86f) / (uMax * 0.14f)).coerceIn(0f, 1f))
                    v = MathMesh.segment(line, v, pU[0], pU[1], pU[2], cU[0], cU[1], cU[2], st[0], st[1], st[2], ba)
                    v = MathMesh.segment(line, v, pD[0], pD[1], pD[2], cD[0], cD[1], cD[2], st[0], st[1], st[2], 0.34f)
                }
                copy3(cU, pU); copy3(cD, pD)
            }

            copy3(cR, pR); copy3(cP, pP); copy3(cQ, pQ)
        }

        // ---- the rest: every order at once ------------------------------------------------------
        // A stop whose finished state is "there is nothing left to see" needs something to rest on.
        // What it rests on is the family: five ribbons leaving one bead, each peeling away further
        // out than the last. It is the nest from THE MATCHING CURVES, seen again now that the thing
        // between the ribbons has a name and a size.
        val fan = if (c < REST) 0f else SceneParts.step(c, REST, 0.06f) * 0.34f
        if (fan > 0.01f && q < 2) {
            for (k in 0 until ORDERS - 1) {
                val t0 = TINT[k]
                for (j in 0..ns) {
                    val t = tAt(j, ns)
                    world(t, SIDE_OUT, poly(k, t).coerceIn(-uMax, uMax), cG)
                    if (j > 0) v = MathMesh.segment(line, v, pG[0], pG[1], pG[2], cG[0], cG[1], cG[2],
                        t0[0], t0[1], t0[2], fan)
                    copy3(cG, pG)
                }
            }
        }

        // ---- the two peel marks -----------------------------------------------------------------
        val flash = 0.62f + 0.30f * sin(kit.seconds * 4.2f)
        if (jLo >= 0) v = peel(line, v, tAt(jLo, ns), uRoof[jLo], flash)
        if (jHi >= 0) v = peel(line, v, tAt(jHi, ns), uRoof[jHi], flash)

        // ---- the station the HUD's number is taken at ---------------------------------------------
        // Two hairlines a quarter of a node either side of the anchor. By order 4 the gap between
        // them and the roof is a thousandth of a unit and there is nothing to see there, which is
        // the reason the number is on the HUD and not hanging out here in the corridor.
        if (q < 2) {
            val hc = SceneParts.HOT
            for (side in 0..1) {
                val t = if (side == 0) -PROBE else PROBE
                val hr = kit.traceHeight(a + t).coerceIn(-uMax, uMax)
                world(t, SIDE_OUT, hr - 0.52f, w0)
                world(t, SIDE_OUT, hr + 0.06f, w1)
                v = MathMesh.segment(line, v, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2],
                    hc[0], hc[1], hc[2], 0.05f, 0.42f)
            }
        }

        // ---- the anchor's tie back to the trace ----------------------------------------------------
        // The slab's upper edge already ends on the rail plane where the trace runs, but this says
        // it in one stroke: the roof over there and the top of this wedge are the same curve.
        if (q < 2) {
            val hc = SceneParts.HOT
            world(0f, SIDE_OUT, dAt[0], w0)
            world(0f, 0f, dAt[0], w1)
            v = MathMesh.dashed(line, v, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2], 4,
                hc[0], hc[1], hc[2], 0.34f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv)

        // The anchor bead: the one place the agreement is exact, and the pinch the whole wedge
        // opens out of. It breathes with the sound cue, so the stop keeps a pulse of its own.
        world(0f, SIDE_OUT, dAt[0], w0)
        kit.ball(
            w0[0], w0[1], w0[2], 0.075f, 0.075f, 0.075f, SceneParts.HOT, SceneParts.LAMP,
            1f, 0f, 0f, 1f, 0f, 0f, 1.1f + kit.beat * 1.4f
        )

        // ---- notation -------------------------------------------------------------------------
        // All of it hangs outboard of the slab and BELOW the pinch, in the one pocket of the frame
        // that is reliably empty: the HUD owns the top of the eye, the roof and the wedge own the
        // middle of it, and the caption box owns the floor.
        world(0f, SIDE_OUT - 0.55f, dAt[0] - 0.46f, w0)
        kit.text(ORDER_TAG[m], w0[0], w0[1], w0[2], 0.24f, tint, 1f, GlyphBoard.Style.MATH,
            1.2f, anchor = -0.5f)

        // The remainder is named only while there is enough of it to point at. Below that the
        // label would be a caption on an empty stretch of corridor, and the HUD has the number.
        if (q == 0 && worst > 0.05f) {
            val jl = (ns * 0.86f).toInt()
            world(tAt(jl, ns), SIDE_OUT - 0.40f, (uRoof[jl] + uPoly[jl]) * 0.5f, w0)
            kit.text(ERR_TAG[m], w0[0], w0[1], w0[2], 0.18f, tint, 0.92f, GlyphBoard.Style.MATH,
                1f, anchor = -0.5f)
        }

        if (q == 0) {
            world(0f, SIDE_OUT - 0.55f, dAt[0] - 0.78f, w0)
            kit.text(BOUND_TAG, w0[0], w0[1], w0[2], 0.155f, SceneParts.STEEL, 0.78f,
                GlyphBoard.Style.MATH, 0.8f, anchor = -0.5f)
        }
    }
}
