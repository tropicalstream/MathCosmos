package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stop 7 of TOUR VI — THE BAG AND ITS SKIN. "Everything made inside has to cross the skin to get
 * out."
 *
 * The divergence theorem, staged as a piece of bookkeeping rather than as an identity. A closed
 * lumpy surface — a bag, deliberately not a sphere, because the theorem does not care what shape
 * you use — hangs to port with a source inside it. The making is drawn as a clump of little cells
 * glowing at the middle of the bag, exactly the boxes of stop 3. Then the skin lights facet by
 * facet in proportion to the flux through it, red where the flow leaves, blue where it enters, and
 * two bars fill: what was made inside, and what crossed the skin. They come to the same height.
 *
 * THE SOURCE IS THIS SCENE'S OWN, AND THE CODE SHOULD SAY SO. Tour VI's field is a swirl plus a
 * drift, and its divergence is a few thousandths everywhere — which is exactly why the ambient
 * streaks look so calm, and exactly why a stop about what a SOURCE does has to bring one. So this
 * scene adds a source of its own at the bag's centre and draws the sum. The consequence is honest
 * but worth stating: the ambient's arrow lattice, which keeps drawing the tour's field alone, does
 * not know about it. The crew says out loud that the source is theirs; the geometry here agrees by
 * keeping the source's reach short — its field at the skin is about two fifths of the tour's, and
 * two bag radii out it is a tenth of it, which is below the width of an ambient arrow.
 *
 * THE BAG IS NOT ALL RED, AND THAT IS THE LESSON. The tour's own flow through this bag is several
 * times what the source makes: nineteen of the fifty-four facets are blue, and the craft is
 * looking at a skin with a bright inflow on one side and a brighter outflow on the other. Every
 * bit of what comes in goes out again, and what is left when the ins and outs have cancelled is
 * exactly what was made inside. A bag that glowed red all over would be a picture of a simpler
 * and less true theorem — divergence is a NET, and a stop that hides the gross flow to make the
 * net obvious has taught the wrong thing.
 *
 * The source is not a point but a blob: the divergence is a smooth bump k(1 − (r/A)²)² inside
 * radius A and EXACTLY zero outside it. That compactness is the whole reason the picture is
 * checkable. All the making is in a region the viewer can see and count cells in, nothing is made
 * anywhere else, and outside the blob the field is exactly an inverse square — so the flux through
 * the bag cannot depend on the bag.
 *
 * BOTH SIDES ARE COMPUTED INDEPENDENTLY, WHICH IS THE POINT. It would be easy, and worthless, to
 * work out one number and draw it twice. The bar on the left is a Riemann sum of the divergence
 * over a grid of cells inside the blob; the bar on the right is a sum over the facets of the skin
 * of the flux through each. Nothing connects them but the theorem. They agree to under a per cent,
 * which is why the readout carries a decimal: a viewer who wants to check is entitled to the
 * digits.
 *
 * Two decisions bought that agreement. The tour's field enters each facet's flux as one sample at
 * the facet's centre, which is not an approximation at all for a field that is linear across a
 * facet — and this one, a swirl about the axis, is. The source's part is the solid angle the
 * facet's two triangles subtend at the source, in closed form: four atan2 per facet, against a
 * midpoint sample that would come out about three per cent light and would leave one bar visibly
 * short of its twin. A viewer would read that as the theorem failing rather than as the mesh being
 * coarse, which is a bad trade for a saving of nothing.
 *
 * The same is true of the facet count, so the skin does NOT step down with quality: it is the
 * measurement's resolution, not decoration. What steps down is the carriers, the cells and the
 * secondary notation.
 *
 * (The twenty-seven boxes at the centre are a coarser grid than the tally uses. A 3 × 3 × 3
 * midpoint sum of this blob is about seven per cent light — fine for something to look at and
 * count, wrong for something to measure with. The boxes show the shape of the making; the bar sums
 * the same function on a finer grid of the same kind of cell.)
 *
 * PLACEMENT, AND WHERE THIS DEPARTS FROM THE DESIGN NOTE. The note has the ship fly out through the
 * skin and look back. The rail is not this scene's to move, and a bag big enough to swallow the
 * rail is a bag whose closest pass shows you one facet from the inside — which is the one thing a
 * closed surface must never look like. So the bag hangs a little over two units to port, about
 * two and a half across, with a unit of clearance, and it is the CARRIERS that cross the skin:
 * streaks released at the source and advected through the sum field, which leave in every
 * direction, bend downstream as the drift takes over, and every one of them ends up outside. That
 * is the sentence of the stop, and a streak crossing a lit facet says it better than the hull
 * would, because you can watch it happen from outside instead of being inside it when it does.
 *
 * Budget: one flushLines, one flushTris, one ball, four labels.
 */
object SceneBagAndSkin : MathScene {

    override val reach = 1.5f

    // ------------------------------------------------------------------ the loop
    private const val PERIOD = 26f
    private const val MAKE_AT = 0.06f
    private const val MAKE_LEN = 0.20f
    private const val CROSS_AT = 0.28f
    private const val CROSS_LEN = 0.26f
    private const val TIE_AT = 0.56f
    private const val TIE_LEN = 0.06f
    // The finished state stands from 0.54 to 0.88 — about nine seconds — and then everything fades
    // back to the bare wire, so the state at the wrap is the state at the start and the loop has no
    // seam. A viewer who arrives mid-rest sees the whole claim already made.
    private const val CLEAR_AT = 0.88f
    private const val CLEAR_LEN = 0.09f

    // ------------------------------------------------------------------ the bag
    private const val SIDE = -2.25f
    private const val UP = 0.30f
    private const val R0 = 1.16f             // mean radius; the lump takes it to 0.99 .. 1.30
    private const val NF = 3                 // facets per cube face each way: 6 x 3 x 3 = 54

    // ------------------------------------------------------------------ the source
    private const val A = 0.50f              // radius of the blob of making
    private const val STRENGTH = 14f         // total flux made inside, in field units x area
    private const val FOUR_PI = 12.566371f
    // 4*pi*(8/105) is what the bump integrates to per unit of k and per A cubed; K inverts it, so
    // STRENGTH is the number the scene is actually specified by.
    private const val BUMP = 0.9574378f
    private const val K = STRENGTH / (BUMP * A * A * A)

    // ------------------------------------------------------------------ the cells
    private const val CELLS = 3              // drawn boxes per axis; the eight corners are empty
    private const val C = 2f * A / CELLS
    private const val HC = C * 0.5f
    private const val MAX_CELL_R = C * 1.4142136f
    private const val FINE = 12              // the grid the tally is summed on

    // ------------------------------------------------------------------ the carriers
    private const val SEEDS = 6
    private const val STEPS = 18
    private const val H = 0.15f              // arc length per step, not time
    private const val CONE = 1.24f           // 71 degrees: how wide the seeds spread about the drift
    private const val BAND_PERIOD = 5.2f
    private const val BAND = 0.24f
    private const val CARRY_BASE = 0.14f
    private const val CARRY_PEAK = 0.62f

    // ------------------------------------------------------------------ the bars
    // Far enough apart that each bar's name fits under it without touching its neighbour's, which
    // is the whole constraint on the pair: they are read by comparing two heights, so they must be
    // side by side and they must not be crowded.
    private const val BAR_S1 = -1.55f
    private const val BAR_S2 = -2.30f
    private const val BAR_W = 0.15f
    private const val BAR_BOT = -0.72f
    private const val BAR_LEN = 1.48f

    private const val FLUX_REF = 0.60f       // the flux a facet is drawn at full strength for
    private const val FRONT_A = 0.75f        // the sweep runs from inside the skin to outside it
    private const val FRONT_B = 1.65f
    private const val R_NEAR = 0.60f         // nothing translucent is drawn in the pilot's face
    private const val R_NEAR_FULL = 1.25f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val dw = FloatArray(3)
    private val fv = FloatArray(3)
    private val cen = FloatArray(3)
    private val w0 = FloatArray(3)
    private val w1 = FloatArray(3)
    private val w2 = FloatArray(3)
    private val w3 = FloatArray(3)
    private val n0 = FloatArray(3)
    private val n1 = FloatArray(3)
    private val n2 = FloatArray(3)
    private val n3 = FloatArray(3)
    private val p = FloatArray(3)
    private val q = FloatArray(3)
    private val tv = IntArray(1)

    // draw() and readout() share this scratch. They are both called from the GL thread, one after
    // the other, and each recomputes its frame and its clock from nothing — neither reads what the
    // other left behind, so the only thing sharing costs is the arrays it saves.
    private val cellW = FloatArray(CELLS * CELLS * CELLS)
    private val cellR = FloatArray(CELLS * CELLS * CELLS)
    private var cellWSum = 0f
    private var madeTotal = 1f
    private var built = false

    private fun build() {
        if (built) return
        var k = 0
        var wsum = 0f
        for (a in 0 until CELLS) for (b in 0 until CELLS) for (c in 0 until CELLS) {
            val s = (a - 1) * C
            val u = (b - 1) * C
            val d = (c - 1) * C
            val r = sqrt(s * s + u * u + d * d)
            cellR[k] = r
            cellW[k] = divAt(r)
            wsum += cellW[k]
            k++
        }
        cellWSum = wsum
        // The tally: the same function, summed on a grid fine enough that its own error is smaller
        // than the width of the line the bar is drawn with.
        var t = 0f
        val h = 2f * A / FINE
        for (a in 0 until FINE) for (b in 0 until FINE) for (c in 0 until FINE) {
            val s = -A + (a + 0.5f) * h
            val u = -A + (b + 0.5f) * h
            val d = -A + (c + 0.5f) * h
            t += divAt(sqrt(s * s + u * u + d * d))
        }
        madeTotal = t * h * h * h
        built = true
    }

    /** How much is being made per unit volume at radius [r] from the source. Zero outside the blob. */
    private fun divAt(r: Float): Float {
        if (r >= A) return 0f
        val t = 1f - r * r / (A * A)
        return K * t * t
    }

    /**
     * The strength of the source's own field at radius [r]. Outside the blob this is the inverse
     * square that carries all of STRENGTH; inside it is the part of the making enclosed so far,
     * spread over the sphere at that radius. The two agree at r = A, which they must, or the
     * carriers would kink as they leave.
     */
    private fun sourceMag(r: Float): Float =
        if (r >= A) STRENGTH / (FOUR_PI * r * r)
        else K * r * (0.33333334f - 0.4f * r * r / (A * A) + r * r * r * r / (7f * A * A * A * A))

    /** The field this stop is about: the tour's own, plus the source this scene brought. */
    private fun totalField(kit: SceneKit, x: Float, y: Float, z: Float, out: FloatArray) {
        kit.fieldAt(x, y, z, out)
        val dx = x - g[0]; val dy = y - g[1]; val dz = z - g[2]
        val r = sqrt(dx * dx + dy * dy + dz * dz)
        if (r < 1e-4f) return
        val m = sourceMag(r) / r
        out[0] += dx * m; out[1] += dy * m; out[2] += dz * m
    }

    /** The bag's mean radius scaled: a pouch, not a ball, so no facet can be mistaken for a pole. */
    private fun lump(ds: Float, dux: Float, da: Float): Float =
        1f + 0.18f * ds * dux - 0.09f * dux + 0.12f * da * da

    /**
     * A point on the skin, in world space: the cube-sphere at ([uu], [vv]) on cube face [face],
     * pushed out to the lumped radius. A cube-sphere rather than a lat-long one because a bag with
     * poles has facets that shrink to slivers, and a sliver facet carries a flux the eye cannot
     * compare with its neighbours'.
     */
    private fun corner(face: Int, uu: Float, vv: Float, out: FloatArray) {
        val axis = face shr 1
        val sgn = if (face and 1 == 0) 1f else -1f
        o[0] = 0f; o[1] = 0f; o[2] = 0f
        o[axis] = sgn
        o[(axis + 1) % 3] = uu
        o[(axis + 2) % 3] = vv
        val len = sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
        val r = R0 * lump(o[0] / len, o[1] / len, o[2] / len) / len
        SceneParts.at(g, o[0] * r, o[1] * r, o[2] * r, out)
    }

    /** The unit vector from the source at the bag's centre to a world point. */
    private fun unitFrom(pt: FloatArray, out: FloatArray) {
        val dx = pt[0] - g[0]; val dy = pt[1] - g[1]; val dz = pt[2] - g[2]
        val r = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
        out[0] = dx / r; out[1] = dy / r; out[2] = dz / r
    }

    /**
     * The solid angle a triangle of unit vectors subtends at the origin — van Oosterom and
     * Strackee. This is the exact flux of a unit point source through that triangle, times 4*pi,
     * and the reason the skin's total comes out right however coarse the mesh is.
     */
    private fun omega(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        val num = a[0] * (b[1] * c[2] - b[2] * c[1]) +
            a[1] * (b[2] * c[0] - b[0] * c[2]) +
            a[2] * (b[0] * c[1] - b[1] * c[0])
        val den = 1f +
            (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) +
            (b[0] * c[0] + b[1] * c[1] + b[2] * c[2]) +
            (c[0] * a[0] + c[1] * a[1] + c[2] * a[2])
        return 2f * atan2(num, den)
    }

    /**
     * The flux out through one facet, leaving its four world corners in w0..w3 and its centre in
     * [cen] for the caller to draw with. Both halves are computed the way that is exact for what
     * is actually drawn: the vector area below is the sum of the two triangles the fill writes,
     * and the solid angle is taken over those same two triangles.
     */
    private fun facetFlux(kit: SceneKit, face: Int, i: Int, j: Int): Float {
        val u0 = -1f + 2f * i / NF
        val u1 = -1f + 2f * (i + 1) / NF
        val v0 = -1f + 2f * j / NF
        val v1 = -1f + 2f * (j + 1) / NF
        corner(face, u0, v0, w0)
        corner(face, u1, v0, w1)
        corner(face, u1, v1, w2)
        corner(face, u0, v1, w3)
        cen[0] = (w0[0] + w1[0] + w2[0] + w3[0]) * 0.25f
        cen[1] = (w0[1] + w1[1] + w2[1] + w3[1]) * 0.25f
        cen[2] = (w0[2] + w1[2] + w2[2] + w3[2]) * 0.25f

        val ax = w2[0] - w0[0]; val ay = w2[1] - w0[1]; val az = w2[2] - w0[2]
        val bx = w3[0] - w1[0]; val by = w3[1] - w1[1]; val bz = w3[2] - w1[2]
        var nx = 0.5f * (ay * bz - az * by)
        var ny = 0.5f * (az * bx - ax * bz)
        var nz = 0.5f * (ax * by - ay * bx)
        // Outward is away from the centre. Deciding it geometrically rather than by winding means
        // the six faces of the cube-sphere need no agreement about handedness between them.
        val dx = cen[0] - g[0]; val dy = cen[1] - g[1]; val dz = cen[2] - g[2]
        if (nx * dx + ny * dy + nz * dz < 0f) { nx = -nx; ny = -ny; nz = -nz }

        kit.fieldAt(cen[0], cen[1], cen[2], fv)
        var flux = fv[0] * nx + fv[1] * ny + fv[2] * nz

        unitFrom(w0, n0); unitFrom(w1, n1); unitFrom(w2, n2); unitFrom(w3, n3)
        val sa = omega(n0, n1, n2) + omega(n0, n2, n3)
        flux += STRENGTH / FOUR_PI * abs(sa)
        return flux
    }

    /** Where the wavefront of the sweep has reached, in radius from the source. */
    private fun front(c: Float): Float =
        FRONT_A + (FRONT_B - FRONT_A) * SceneParts.step(c, CROSS_AT, CROSS_LEN)

    /** How much of the making has been lit, as a fraction of all of it. */
    private fun kindled(c: Float): Float {
        if (cellWSum <= 0f) return 0f
        val edge = SceneParts.step(c, MAKE_AT, MAKE_LEN) * (MAX_CELL_R + 0.18f)
        var lit = 0f
        for (k in cellW.indices) {
            if (cellW[k] <= 0f) continue
            lit += cellW[k] * SceneParts.ease((edge - cellR[k]) / 0.18f)
        }
        return lit / cellWSum
    }

    /** The net flux out through the part of the skin the sweep has lit. */
    private fun skinSum(kit: SceneKit, edge: Float, hold: Float): Float {
        var total = 0f
        for (face in 0 until 6) for (i in 0 until NF) for (j in 0 until NF) {
            val flux = facetFlux(kit, face, i, j)
            val dx = cen[0] - g[0]; val dy = cen[1] - g[1]; val dz = cen[2] - g[2]
            val r = sqrt(dx * dx + dy * dy + dz * dz)
            total += flux * SceneParts.ease((edge - r) / 0.30f) * hold
        }
        return total
    }

    /**
     * The two numbers, in the only place they are legible. The whole stop is the claim that these
     * are the same number arrived at from opposite directions, and a bar an inch long cannot say
     * "to within a per cent" — so the digits go here and the bars carry the comparison.
     */
    override fun readout(kit: SceneKit): String? {
        build()
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val hold = 1f - SceneParts.step(c, CLEAR_AT, CLEAR_LEN)
        // The HUD asks the scene of the stop the craft has DEPARTED and hands it no index of its
        // own, so the stop is the floor of the rail position.
        SceneParts.stage(kit, kit.progress.toInt().coerceAtLeast(0).toFloat(), SIDE, UP, f, g)
        val made = madeTotal * kindled(c) * hold
        val crossed = skinSum(kit, front(c), hold)
        return String.format(Locale.US, "MADE %.1f   CROSSED %.1f", made, crossed)
    }

    /** One bar: its empty track always, and its filled part with a bright cap on top. */
    private fun bar(line: FloatArray, lv: Int, tri: FloatArray, s: Float, h: Float, col: FloatArray): Int {
        SceneParts.at(g, s - BAR_W * 0.5f, BAR_BOT, 0f, o)
        SceneParts.vec(g, BAR_W, 0f, 0f, du)
        SceneParts.vec(g, 0f, BAR_LEN, 0f, dv)
        var k = SceneParts.edge(
            line, lv, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.STEEL, 0.20f
        )
        if (h > 0.01f) {
            SceneParts.vec(g, 0f, h, 0f, dv)
            tv[0] = MathMesh.quad(
                tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                col[0], col[1], col[2], 0.42f
            )
            k = MathMesh.segment(
                line, k,
                o[0] + dv[0], o[1] + dv[1], o[2] + dv[2],
                o[0] + du[0] + dv[0], o[1] + du[1] + dv[1], o[2] + du[2] + dv[2],
                col[0], col[1], col[2], 0.95f
            )
        }
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build()
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val hold = 1f - SceneParts.step(c, CLEAR_AT, CLEAR_LEN)
        val edge = front(c)
        val make = SceneParts.step(c, MAKE_AT, MAKE_LEN)

        // ----------------------------------------------------------------- the skin
        // The flux is measured every frame, whatever the clock says; the sweep only decides how
        // much of the measurement has been REVEALED. The numbers are never staged, only their
        // arrival is, which is what lets the second bar be watched filling rather than announced.
        var crossed = 0f
        for (face in 0 until 6) for (a in 0 until NF) for (b in 0 until NF) {
            val flux = facetFlux(kit, face, a, b)
            val dx = cen[0] - g[0]; val dy = cen[1] - g[1]; val dz = cen[2] - g[2]
            val r = sqrt(dx * dx + dy * dy + dz * dz)
            val lit = SceneParts.ease((edge - r) / 0.30f) * hold
            crossed += flux * lit

            val fn = (abs(flux) / FLUX_REF).coerceAtMost(1f)

            // The wire is the surface; the fill is the reading. Keeping them separate is what stops
            // a brightly lit facet reading as a hole in the bag.
            v = MathMesh.segment(
                line, v, w0[0], w0[1], w0[2], w1[0], w1[1], w1[2],
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.22f + 0.40f * fn * lit
            )
            v = MathMesh.segment(
                line, v, w0[0], w0[1], w0[2], w3[0], w3[1], w3[2],
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.22f + 0.40f * fn * lit
            )
            // The far two edges of the last row and column, so the grid closes. The twelve seams of
            // the cube are drawn twice, once by each face that owns them, and read a shade brighter
            // — which is what a seam should do.
            if (a == NF - 1) v = MathMesh.segment(
                line, v, w1[0], w1[1], w1[2], w2[0], w2[1], w2[2],
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.22f + 0.40f * fn * lit
            )
            if (b == NF - 1) v = MathMesh.segment(
                line, v, w3[0], w3[1], w3[2], w2[0], w2[1], w2[2],
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.22f + 0.40f * fn * lit
            )

            if (lit <= 0.02f) continue
            // A facet a metre from the eye is a translucent wash across the middle of the display,
            // and the closest pass brings the near side of the bag inside a unit. Fading it out is
            // cheaper than explaining it.
            val ex = cen[0] - kit.camX; val ey = cen[1] - kit.camY; val ez = cen[2] - kit.camZ
            val near = ((sqrt(ex * ex + ey * ey + ez * ez) - R_NEAR) / (R_NEAR_FULL - R_NEAR))
                .coerceIn(0f, 1f)
            val alpha = (0.05f + 0.36f * fn) * lit * near
            if (alpha > 0.01f && (tv[0] + 6) * MathMesh.STRIDE <= tri.size) {
                // Red out, blue in — the caps of stop 3, in the same two colours, so a viewer who
                // remembers that box knows what this skin is telling them without being told.
                val col = if (flux >= 0f) SceneParts.TAKEN else SceneParts.COOL
                var t = MathMesh.vertex(tri, tv[0], w0[0], w0[1], w0[2], col[0], col[1], col[2], alpha)
                t = MathMesh.vertex(tri, t, w1[0], w1[1], w1[2], col[0], col[1], col[2], alpha)
                t = MathMesh.vertex(tri, t, w2[0], w2[1], w2[2], col[0], col[1], col[2], alpha)
                t = MathMesh.vertex(tri, t, w0[0], w0[1], w0[2], col[0], col[1], col[2], alpha)
                t = MathMesh.vertex(tri, t, w2[0], w2[1], w2[2], col[0], col[1], col[2], alpha)
                t = MathMesh.vertex(tri, t, w3[0], w3[1], w3[2], col[0], col[1], col[2], alpha)
                tv[0] = t
            }
        }

        // ----------------------------------------------------------------- the making
        // The cells kindle outward from the middle, so the making is watched happening rather than
        // switched on. The corner cells of the block are outside the blob and hold nothing at all,
        // which is why only nineteen of the twenty-seven are ever drawn: an empty cell drawn dim
        // still says "a little is made here", and none is.
        val cut = when (kit.quality) { 0 -> 99f; 1 -> C * 1.05f; else -> 0.01f }
        SceneParts.vec(g, HC, 0f, 0f, du)
        SceneParts.vec(g, 0f, HC, 0f, dv)
        SceneParts.vec(g, 0f, 0f, HC, dw)
        val kindleEdge = make * (MAX_CELL_R + 0.18f)
        var k = 0
        for (a in 0 until CELLS) for (b in 0 until CELLS) for (d in 0 until CELLS) {
            val wgt = cellW[k]
            val rr = cellR[k]
            k++
            if (wgt <= 0.004f * K || rr > cut) continue
            val kin = SceneParts.ease((kindleEdge - rr) / 0.18f) * hold
            if (kin <= 0.02f) continue
            SceneParts.at(g, (a - 1) * C, (b - 1) * C, (d - 1) * C, o)
            v = MathMesh.boxEdges(
                line, v, o[0], o[1], o[2],
                du[0], du[1], du[2], dv[0], dv[1], dv[2], dw[0], dw[1], dw[2],
                SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2],
                (0.22f + 0.55f * wgt / K) * kin
            )
        }

        // ----------------------------------------------------------------- the carriers
        // Stepped by arc length rather than by time: the source's field is five times stronger at
        // the blob's rim than at the skin and falls as an inverse square beyond it, so a time step
        // that draws a sensible thread near the middle draws a stub out at the edge — which is
        // exactly the end that has to be seen crossing.
        val carry = SceneParts.step(c, 0.22f, 0.10f) * hold
        val carriers = when (kit.quality) { 0 -> SEEDS; 1 -> SEEDS / 2; else -> 0 }
        if (carry > 0.02f) {
            val band = SceneParts.cycle(kit.seconds, BAND_PERIOD)
            // The carriers are released on a cone about the drift at the bag's middle, not on a
            // fixed star of directions. Where the source and the oncoming flow cancel there is a
            // stagnation point, and a streak released straight into the flow crawls into it and
            // sits there; a cone seventy degrees wide leaves in every direction that reads as "in
            // every direction" and in none that stalls. Measuring the drift rather than assuming
            // it also means this survives the field being retuned, which it will be.
            kit.fieldAt(g[0], g[1], g[2], fv)
            var dl = sqrt(fv[0] * fv[0] + fv[1] * fv[1] + fv[2] * fv[2])
            if (dl < 1e-4f) { fv[0] = g[9]; fv[1] = g[10]; fv[2] = g[11]; dl = 1f }
            du[0] = fv[0] / dl; du[1] = fv[1] / dl; du[2] = fv[2] / dl
            // Two directions across the drift: the stage's side with its along-drift part removed,
            // and the cross product of the two. If the drift happens to run along the side vector,
            // the stage's up serves instead.
            var pick = 3
            if (abs(g[3] * du[0] + g[4] * du[1] + g[5] * du[2]) > 0.9f) pick = 6
            val along = g[pick] * du[0] + g[pick + 1] * du[1] + g[pick + 2] * du[2]
            val d0 = g[pick] - du[0] * along
            val d1 = g[pick + 1] - du[1] * along
            val d2 = g[pick + 2] - du[2] * along
            val el = sqrt(d0 * d0 + d1 * d1 + d2 * d2).coerceAtLeast(1e-5f)
            dv[0] = d0 / el; dv[1] = d1 / el; dv[2] = d2 / el
            dw[0] = du[1] * dv[2] - du[2] * dv[1]
            dw[1] = du[2] * dv[0] - du[0] * dv[2]
            dw[2] = du[0] * dv[1] - du[1] * dv[0]
            val cc = cos(CONE)
            val sc = sin(CONE)
            for (m in 0 until carriers) {
                val th = m.toFloat() / carriers * 6.2831855f
                val ct = cos(th) * sc
                val st2 = sin(th) * sc
                o[0] = du[0] * cc + dv[0] * ct + dw[0] * st2
                o[1] = du[1] * cc + dv[1] * ct + dw[1] * st2
                o[2] = du[2] * cc + dv[2] * ct + dw[2] * st2
                p[0] = g[0] + o[0] * 0.16f; p[1] = g[1] + o[1] * 0.16f; p[2] = g[2] + o[2] * 0.16f
                val phase = (band + m.toFloat() / carriers) % 1f
                for (st in 0 until STEPS) {
                    totalField(kit, p[0], p[1], p[2], fv)
                    val s = sqrt(fv[0] * fv[0] + fv[1] * fv[1] + fv[2] * fv[2])
                    if (s < 1e-3f) break
                    q[0] = p[0] + fv[0] / s * H
                    q[1] = p[1] + fv[1] / s * H
                    q[2] = p[2] + fv[2] / s * H
                    val u = (st + 0.5f) / STEPS
                    var dd = abs(u - phase)
                    if (dd > 0.5f) dd = 1f - dd
                    val rise = (1f - dd / BAND).coerceAtLeast(0f)
                    v = MathMesh.segment(
                        line, v, p[0], p[1], p[2], q[0], q[1], q[2],
                        SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2],
                        ((CARRY_BASE + CARRY_PEAK * rise * rise) * MathMesh.taper(u) * carry)
                            .coerceAtMost(1f)
                    )
                    p[0] = q[0]; p[1] = q[1]; p[2] = q[2]
                }
            }
        }

        // ----------------------------------------------------------------- the two bars
        val made = madeTotal * kindled(c) * hold
        val scale = BAR_LEN / madeTotal
        val h1 = (made * scale).coerceIn(0f, BAR_LEN)
        val h2 = (crossed * scale).coerceIn(0f, BAR_LEN)
        v = bar(line, v, tri, BAR_S1, h1, SceneParts.TAKEN)
        v = bar(line, v, tri, BAR_S2, h2, SceneParts.ADDED)

        // The tie across the two tops. It is drawn only once both bars are up, and it is the one
        // line in the scene that is a claim rather than a measurement — so it is drawn between
        // where the two bars actually got to, and if they ever disagreed it would visibly slope.
        val tie = SceneParts.step(c, TIE_AT, TIE_LEN) * hold
        if (tie > 0.02f && h1 > 0.05f && h2 > 0.05f) {
            SceneParts.at(g, BAR_S1 - BAR_W * 0.5f, BAR_BOT + h1, 0f, o)
            SceneParts.at(g, BAR_S2 + BAR_W * 0.5f, BAR_BOT + h2, 0f, du)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.85f * tie
            )
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // The source itself. One lit bead at the middle of the cells, pulsing with the beat, so the
        // making has a thing at the centre of it and not merely a region.
        val glow = 0.8f + 1.8f * make * hold + 2.2f * kit.beat
        kit.ball(
            g[0], g[1], g[2], 0.10f, 0.10f, 0.10f, SceneParts.HOT, SceneParts.TAKEN,
            0.35f + 0.65f * make * hold, glow = glow, small = false
        )

        // ----------------------------------------------------------------- notation
        // Everything sits between a quarter of a unit above the rail's centreline and two thirds of
        // one below it. The telemetry owns the top of the eye and the caption box the bottom, and
        // this figure is far enough to port that a label level with it is in clear air.
        if (kit.quality < 2) {
            SceneParts.at(g, 1.12f, -0.55f, 0f, o)
            kit.text("∂V", o[0], o[1], o[2], 0.19f, SceneParts.CHALK, 0.95f, anchor = -0.5f)
        }

        // The two bars are named by what they sum, one under each, with the equals sign between
        // them: the theorem laid out as the instrument that measures it, rather than as a caption
        // hung somewhere near it.
        if (kit.quality < 2) {
            SceneParts.at(g, BAR_S1, -0.92f, 0f, o)
            kit.text("∫∇·F dV", o[0], o[1], o[2], 0.12f, SceneParts.TAKEN, 0.95f, GlyphBoard.Style.SMALL)
            SceneParts.at(g, BAR_S2, -0.92f, 0f, o)
            kit.text("∫F·n dA", o[0], o[1], o[2], 0.12f, SceneParts.ADDED, 0.95f, GlyphBoard.Style.SMALL)
            if (kit.quality == 0 && tie > 0.4f) {
                SceneParts.at(g, (BAR_S1 + BAR_S2) * 0.5f, -0.92f, 0f, o)
                kit.text("=", o[0], o[1], o[2], 0.14f, SceneParts.HOT, 0.95f, GlyphBoard.Style.SMALL)
            }
        }
    }
}
