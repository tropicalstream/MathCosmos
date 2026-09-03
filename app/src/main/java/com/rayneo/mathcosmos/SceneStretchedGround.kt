package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tour V, stop 11 — THE STRETCHED GROUND. "Bend the coordinate grid and every little cell changes
 * area by a factor I can measure."
 *
 * A square net is painted on the country beside the rail. Over seven seconds it BENDS — the rows
 * fanning out about a point, the columns bowing into arcs — until it is the polar net, and every
 * cell in it has changed size on the way. One cell is picked out in warm light throughout, and
 * beside the craft two plates stand side by side: the cell as it was, and the cell as it is now.
 * The right-hand plate swells to three times the left by the outer ring and shrinks to half of it
 * at the inner one. That ratio is the whole stop. The word for it is never said and never needed.
 *
 * WHAT WARPS, AND WHAT DOES NOT. The brief asks for the terrain to ride along with the grid. It
 * cannot here — the country belongs to [SceneAmbientCountry] and a landmark may not reach into it
 * — and on reflection it should not, because the honest picture is the other way round. A change
 * of variable does not move the landscape; it re-rules the paper you are reading it on. So the
 * ground stays exactly where it was and the NET slides over it, which is both what a scene is
 * allowed to do and what the mathematics actually says. The crew can say that sentence out loud.
 *
 * THE MAP, AND WHY IT NEVER FOLDS. The net is the image of a chart rectangle (r, θ) under
 *
 *     M_t(r, θ) = (1 − t)·(r, θ)  +  t·(r cos θ, r sin θ)
 *
 * a straight blend from the identity to polar. Its determinant works out as
 * (1 − t)² + t(1 − t)(r + 1)cos θ + t² r, and every term of that is positive while |θ| < π/2, so
 * the net is a genuine one-to-one re-ruling at EVERY instant of the warp and not just at its two
 * ends. That is why the angular range stops at ±1.5 radians and not at ±π/2: a fold, even a
 * momentary one halfway through, would put a crease in the picture and a lie in the argument.
 * At t = 1 the determinant is exactly r, which is the ratio the plates are showing.
 *
 * WHY THE AREA IS MEASURED AND NOT COMPUTED. Nothing here evaluates a formula for the ratio. The
 * cell's area is taken by shoelace round the very boundary that is drawn, sampled at the very same
 * points, so the number on the HUD is the area of the quadrilateral in front of you rather than a
 * claim about it. The two disagree by a fraction of a per cent — an inscribed polygon is always a
 * shade smaller than its arc — and reporting the polygon is the version a viewer can check.
 *
 * THE LETTERS DO NOT CHANGE. The flat net is labelled r and θ from the first frame, before any
 * bending, because it already IS the (r, θ) chart — drawn with one radian to one unit so that the
 * two pictures can be compared as areas at all. Only the picture changes; the names on it stay put.
 * That is the point of the stop and it is worth not spoiling by relabelling anything midway.
 *
 * THE PLATES ARE BILLBOARDED. A flat gauge square to the rail goes edge-on at exactly the closest
 * point of the pass, which is the moment it is most wanted. These face the eye instead. Their
 * common scale is a convention of ours — they are a gauge, not a tracing of the cell — and only
 * the ratio of their areas means anything.
 *
 * GLYPH SIZE. Same reasoning as [SceneContours]: the net lies on the ground four to eight units
 * out, where the tour's usual 0.22 world height renders at a handful of pixels, so heights are
 * derived from the distance to the label and clamped at both ends. The plates are at arm's length
 * and land on the clamp.
 */
object SceneStretchedGround : MathScene {

    /** Open-country geometry: it wants to be on the ground well before the craft arrives. */
    override val reach = 1.6f

    /** The fan runs about three units past the stop; do not cull it at its own node. */
    override val deep = 0.4f

    // ---- the patch of plane being re-ruled ----------------------------------------------------
    private const val R0 = 0.25f            // inner radius in chart units: small enough for slivers,
    private const val R1 = 3.30f            // big enough that the innermost arc is still an arc
    private const val NR = 6                // radial cells. This is the ratio ladder itself, so it
                                            // is NOT stepped down with quality — see draw().
    private const val T0 = -1.50f           // 86 degrees each way. Not π/2: see the fold argument.
    private const val T1 = 1.50f
    private const val NT0 = 6               // angular cells at quality 0
    private const val SUB0 = 3              // samples per angular cell along a bowed ruling
    private const val SCALE = 1.15f         // world units per chart unit AND per radian, so that
                                            // the flat cell and the bent one are comparable areas
    private const val AHEAD = -0.60f        // the chart's origin, half a unit behind the stop
    private const val ASIDE = 4.00f         // and four to port, clear of the rail
    private const val LIFT = 0.09f          // proud of the ambient's mesh (0.03) and its rings (0.06)

    // ---- staging ------------------------------------------------------------------------------
    private const val PERIOD = 30f
    private const val GRID_A = 0.50f
    private const val HI_FILL = 0.32f
    private const val PLATE = 0.34f         // world side of the gauge plate for the unwarped cell
    private const val GAUGE_SIDE = 1.5f     // where the gauge hangs beside the rail
    private const val GAUGE_UP = 0.30f      // lifted, so it floats clear of the net three units down
    private const val GAP = 0.10f           // between the two plates
    private const val GLYPH = 0.058f        // glyph height as a fraction of its distance (~3.3 deg)

    // ---- the net, rebuilt every frame ---------------------------------------------------------
    // It has to be: the net is moving AND it is draped on ground the scene does not own, so there
    // is nothing here worth caching. 133 nodes at quality 0 is 133 terrainHeight calls a frame,
    // which on this terrain is four hundred sines — beneath notice next to the ambient's own grid.
    private const val MAXT = NT0 * SUB0
    private val node = FloatArray((NR + 1) * (MAXT + 1) * 3)

    // ---- scratch --------------------------------------------------------------------------------
    private val fr = FloatArray(12)
    private val m2 = FloatArray(2)
    // readout() gets its own two floats rather than borrowing draw()'s. The HUD's string is built
    // beside the frame and it costs eight bytes to never have to think about which thread that is.
    private val ms = FloatArray(2)
    private val tv = IntArray(1)

    // The stop's own ground frame, flattened. Written at the top of every draw and read by the
    // helpers below; they are per-frame working values, not state the scene remembers.
    private var ox = 0f
    private var oz = 0f
    private var hx = 0f                     // heading, projected into the ground plane
    private var hz = 0f
    private var px = 0f                     // that heading turned a quarter to port
    private var pz = 0f

    // ============================================================ the chart and its warp

    private fun bands(q: Int) = if (q == 0) NT0 else if (q == 1) 4 else 3
    private fun subs(q: Int) = if (q == 0) SUB0 else 2

    /** The blend map, chart (r, θ) to plane (along, across), in world units. */
    private fun chart(t: Float, r: Float, th: Float, out: FloatArray) {
        out[0] = ((1f - t) * r + t * r * cos(th)) * SCALE
        out[1] = ((1f - t) * th + t * r * sin(th)) * SCALE
    }

    /**
     * How far through the bend we are. It warps, rests, is walked over, rests again long enough to
     * be looked at, and then un-bends in the last two seconds rather than snapping at the wrap —
     * a viewer arriving in the middle of the loop should never see the picture jump.
     */
    private fun warpAt(c: Float): Float =
        if (c < 0.93f) SceneParts.step(c, 0.06f, 0.24f)
        else 1f - SceneParts.step(c, 0.93f, 0.07f)

    /**
     * Which ring the highlighted cell is on. It starts in the middle, walks in to the innermost
     * ring where the cells are slivers, then out to the outermost where they are fat, and rests
     * there. Rounded to a whole index rather than slid, because a coordinate cell is a cell: it
     * should hop from one to the next, not smear between them.
     */
    private fun cellIndex(c: Float): Int {
        val a = when {
            c < 0.44f -> 3f
            c < 0.60f -> 3f - 3f * SceneParts.ease((c - 0.44f) / 0.16f)
            c < 0.80f -> 5f * SceneParts.ease((c - 0.60f) / 0.20f)
            c < 0.93f -> 5f
            else -> 5f - 2f * SceneParts.ease((c - 0.93f) / 0.07f)
        }
        return a.roundToInt().coerceIn(0, NR - 1)
    }

    /**
     * The cell's footprint on the plan, by shoelace round the boundary as it is drawn: along the
     * inner arc, out, back along the outer arc, in. Footprint and not area-on-the-hillside — the
     * Jacobian is a fact about the plane, and the country underneath is only what the net happens
     * to be painted on.
     */
    private fun cellArea(t: Float, ci: Int, cj: Int, nt: Int, sub: Int, s2: FloatArray): Float {
        val dr = (R1 - R0) / NR
        val dth = (T1 - T0) / nt
        val rLo = R0 + dr * ci
        val rHi = rLo + dr
        val thLo = T0 + dth * cj
        var acc = 0f
        var ax = 0f; var az = 0f
        var firstX = 0f; var firstZ = 0f
        var seen = false
        for (s in 0..sub) {
            chart(t, rLo, thLo + dth * (s.toFloat() / sub), s2)
            if (seen) acc += ax * s2[1] - s2[0] * az else { firstX = s2[0]; firstZ = s2[1]; seen = true }
            ax = s2[0]; az = s2[1]
        }
        for (s in sub downTo 0) {
            chart(t, rHi, thLo + dth * (s.toFloat() / sub), s2)
            acc += ax * s2[1] - s2[0] * az
            ax = s2[0]; az = s2[1]
        }
        acc += ax * firstZ - firstX * az
        return abs(acc) * 0.5f
    }

    /**
     * The two areas and what one is of the other. This is the stop's whole measurement, and it
     * belongs on the HUD in two dimensions where it can actually be read — the plates beside the
     * craft say the same thing as a picture, which is the half a viewer will remember.
     */
    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val nt = bands(kit.quality)
        val sub = subs(kit.quality)
        val ci = cellIndex(c)
        val cj = nt / 2
        val flat = cellArea(0f, ci, cj, nt, sub, ms)
        if (flat < 1e-6f) return null
        val now = cellArea(warpAt(c), ci, cj, nt, sub, ms)
        return String.format(Locale.US, "CELL %.2f → %.2f   ×%.2f", flat, now, now / flat)
    }

    // ============================================================ drawing

    /** Index of node (a, j) in [node], in floats. */
    private fun ni(a: Int, j: Int, nj: Int) = (a * (nj + 1) + j) * 3

    /** Every node of the net, mapped through the warp and then dropped onto the country. */
    private fun fillNodes(kit: SceneKit, t: Float, nt: Int, sub: Int) {
        val nj = nt * sub
        val dr = (R1 - R0) / NR
        val dth = (T1 - T0) / nj
        var k = 0
        for (a in 0..NR) {
            val r = R0 + dr * a
            for (j in 0..nj) {
                chart(t, r, T0 + dth * j, m2)
                val x = ox + hx * (AHEAD + m2[0]) + px * (ASIDE + m2[1])
                val z = oz + hz * (AHEAD + m2[0]) + pz * (ASIDE + m2[1])
                node[k] = x
                node[k + 1] = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(x, z) + LIFT
                node[k + 2] = z
                k += 3
            }
        }
    }

    /** One face of the gauge, billboarded: a translucent fill and a bright rim, standing on a base. */
    private fun plate(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        bx: Float, by: Float, bz: Float, from: Float, side: Float,
        col: FloatArray, alpha: Float
    ): Int {
        val qx = bx + kit.camRightX * from
        val qy = by + kit.camRightY * from
        val qz = bz + kit.camRightZ * from
        val ux = kit.camRightX * side; val uy = kit.camRightY * side; val uz = kit.camRightZ * side
        val wx = kit.camUpX * side; val wy = kit.camUpY * side; val wz = kit.camUpZ * side
        tv[0] = MathMesh.quad(tri, tv[0], qx, qy, qz, ux, uy, uz, wx, wy, wz,
            col[0], col[1], col[2], alpha * 0.26f)
        return SceneParts.edge(line, lv, qx, qy, qz, ux, uy, uz, wx, wy, wz, col, alpha)
    }

    /** Glyph height that holds a constant angular size from wherever the eye happens to be. */
    private fun glyph(kit: SceneKit, x: Float, y: Float, z: Float): Float {
        val dx = x - kit.camX; val dy = y - kit.camY; val dz = z - kit.camZ
        return (sqrt(dx * dx + dy * dy + dz * dz) * GLYPH).coerceIn(0.17f, 0.80f)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val q = kit.quality
        // The angular bands halve with the thermal step and the bowed rulings are sampled coarser;
        // the six radial rings never do, because the walk from the inner ring to the outer one IS
        // the argument and a shorter ladder would be a weaker one for no meaningful saving.
        val nt = bands(q)
        val sub = subs(q)
        val nj = nt * sub

        // --- the stop's ground frame ------------------------------------------------------------
        // Flattened into the horizontal plane, because terrainHeight is a function of world (x, z)
        // and a net that tilted with the rail would hover off the dirt at one end.
        kit.frame(i.toFloat(), fr)
        ox = fr[0]; oz = fr[2]
        var ax = fr[3]; var az = fr[5]
        val al = sqrt(ax * ax + az * az)
        if (al < 1e-4f) { ax = 0f; az = -1f } else { ax /= al; az /= al }
        hx = ax; hz = az
        px = az; pz = -ax                    // a quarter turn: port

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val t = warpAt(c)
        val ci = cellIndex(c)
        val cj = nt / 2                      // the highlighted cell rides the band beside the axis

        fillNodes(kit, t, nt, sub)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // --- the net ------------------------------------------------------------------------------
        // Blue over the ambient's teal country, so it reads as something laid ON the ground rather
        // than as more of it. Rulings of constant r first: these are the ones that bow.
        val gc = SceneParts.COOL
        for (a in 0..NR) {
            for (j in 0 until nj) {
                val p0 = ni(a, j, nj); val p1 = ni(a, j + 1, nj)
                v = MathMesh.segment(
                    line, v, node[p0], node[p0 + 1], node[p0 + 2],
                    node[p1], node[p1 + 1], node[p1 + 2], gc[0], gc[1], gc[2], GRID_A
                )
            }
        }
        // And the rulings of constant θ. These stay dead straight for every t — a blend of two maps
        // that are both linear in r is linear in r — so they need no sampling at all; they are cut
        // at each ring only so the lattice reads as cells and not as a bundle of long spokes.
        for (jj in 0..nt) {
            val j = jj * sub
            for (a in 0 until NR) {
                val p0 = ni(a, j, nj); val p1 = ni(a + 1, j, nj)
                v = MathMesh.segment(
                    line, v, node[p0], node[p0 + 1], node[p0 + 2],
                    node[p1], node[p1 + 1], node[p1 + 2], gc[0], gc[1], gc[2], GRID_A
                )
            }
        }

        // --- the one cell -------------------------------------------------------------------------
        // Filled from its own corners rather than through MathMesh.quad's parallelogram, because
        // once the warp bites this cell is not a parallelogram and the whole stop is about how much
        // of one it is not. MathMesh.vertex does not bounds-check, so the room is checked once here.
        val hc = SceneParts.WORK
        if ((tv[0] + sub * 6) * MathMesh.STRIDE <= tri.size) {
            var k = tv[0]
            for (s in 0 until sub) {
                val j0 = cj * sub + s
                val a0 = ni(ci, j0, nj); val a1 = ni(ci, j0 + 1, nj)
                val b0 = ni(ci + 1, j0, nj); val b1 = ni(ci + 1, j0 + 1, nj)
                k = MathMesh.vertex(tri, k, node[a0], node[a0 + 1], node[a0 + 2], hc[0], hc[1], hc[2], HI_FILL)
                k = MathMesh.vertex(tri, k, node[b0], node[b0 + 1], node[b0 + 2], hc[0], hc[1], hc[2], HI_FILL)
                k = MathMesh.vertex(tri, k, node[b1], node[b1 + 1], node[b1 + 2], hc[0], hc[1], hc[2], HI_FILL)
                k = MathMesh.vertex(tri, k, node[a0], node[a0 + 1], node[a0 + 2], hc[0], hc[1], hc[2], HI_FILL)
                k = MathMesh.vertex(tri, k, node[b1], node[b1 + 1], node[b1 + 2], hc[0], hc[1], hc[2], HI_FILL)
                k = MathMesh.vertex(tri, k, node[a1], node[a1 + 1], node[a1 + 2], hc[0], hc[1], hc[2], HI_FILL)
            }
            tv[0] = k
        }
        // Its rim, over the top of the net's own lines so the cell is unmistakably picked out.
        for (s in 0 until sub) {
            val j0 = cj * sub + s
            val a0 = ni(ci, j0, nj); val a1 = ni(ci, j0 + 1, nj)
            val b0 = ni(ci + 1, j0, nj); val b1 = ni(ci + 1, j0 + 1, nj)
            v = MathMesh.segment(
                line, v, node[a0], node[a0 + 1], node[a0 + 2],
                node[a1], node[a1 + 1], node[a1 + 2], hc[0], hc[1], hc[2], 1f
            )
            v = MathMesh.segment(
                line, v, node[b0], node[b0 + 1], node[b0 + 2],
                node[b1], node[b1 + 1], node[b1 + 2], hc[0], hc[1], hc[2], 1f
            )
        }
        val eIn = ni(ci, cj * sub, nj); val eOut = ni(ci + 1, cj * sub, nj)
        v = MathMesh.segment(
            line, v, node[eIn], node[eIn + 1], node[eIn + 2],
            node[eOut], node[eOut + 1], node[eOut + 2], hc[0], hc[1], hc[2], 1f
        )
        val fIn = ni(ci, (cj + 1) * sub, nj); val fOut = ni(ci + 1, (cj + 1) * sub, nj)
        v = MathMesh.segment(
            line, v, node[fIn], node[fIn + 1], node[fIn + 2],
            node[fOut], node[fOut + 1], node[fOut + 2], hc[0], hc[1], hc[2], 1f
        )

        // --- the gauge ------------------------------------------------------------------------------
        // Two squares on a common baseline beside the craft: the cell flat, and the cell now. Squares
        // and not bars, because the quantity being compared is an area and a viewer should be able to
        // see one as so many of the other without being told a scale.
        val flat = cellArea(0f, ci, cj, nt, sub, m2)
        val now = cellArea(t, ci, cj, nt, sub, m2)
        val ratio = if (flat > 1e-6f) now / flat else 1f
        val side1 = PLATE * sqrt(ratio)
        val gx = fr[0] + px * GAUGE_SIDE + fr[9] * GAUGE_UP
        val gy = fr[1] + fr[10] * GAUGE_UP
        val gz = fr[2] + pz * GAUGE_SIDE + fr[11] * GAUGE_UP
        v = plate(kit, line, v, tri, gx, gy, gz, -GAP - PLATE, PLATE, SceneParts.STEEL, 0.85f)
        v = plate(kit, line, v, tri, gx, gy, gz, GAP, side1, hc, 1f)

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the place the cells go to nothing ---------------------------------------------------
        // One ball at the chart's origin, and only once the bend has begun: before it, that point is
        // just an empty spot off the near corner of a square and means nothing.
        val cx0 = ox + hx * AHEAD + px * ASIDE
        val cz0 = oz + hz * AHEAD + pz * ASIDE
        val cy0 = SceneAmbientCountry.GROUND_Y + kit.terrainHeight(cx0, cz0) + LIFT
        if (t > 0.05f) {
            kit.ball(
                cx0, cy0 + 0.08f, cz0, 0.13f, 0.13f, 0.13f,
                SceneParts.LAMP, SceneParts.HOT, t, 0f, 0f, 1f, 0f, 0f, 1.2f * t
            )
        }

        // --- notation ---------------------------------------------------------------------------
        // Every label sits beside the thing it names and half a unit off the ground: the net is
        // already fifteen degrees below the horizon and the caption box owns the bottom of the eye,
        // so a name left lying in the dirt would be read through it.
        if (q <= 1) {
            val lx = gx + kit.camRightX * (-GAP - PLATE - 0.07f) + kit.camUpX * PLATE * 0.5f
            val ly = gy + kit.camRightY * (-GAP - PLATE - 0.07f) + kit.camUpY * PLATE * 0.5f
            val lz = gz + kit.camRightZ * (-GAP - PLATE - 0.07f) + kit.camUpZ * PLATE * 0.5f
            kit.text("dr dθ", lx, ly, lz, glyph(kit, lx, ly, lz) * 0.85f,
                SceneParts.STEEL, 0.9f, anchor = 0.5f)

            val rx = gx + kit.camRightX * (GAP + side1 + 0.07f) + kit.camUpX * side1 * 0.5f
            val ry = gy + kit.camRightY * (GAP + side1 + 0.07f) + kit.camUpY * side1 * 0.5f
            val rz = gz + kit.camRightZ * (GAP + side1 + 0.07f) + kit.camUpZ * side1 * 0.5f
            kit.text("dA", rx, ry, rz, glyph(kit, rx, ry, rz) * 0.85f, hc, 1f, anchor = -0.5f)

            // The sentence, once the net has finished making it true, beside the cell it is about.
            if (t > 0.9f) {
                val a0 = ni(ci, cj * sub, nj); val b1 = ni(ci + 1, (cj + 1) * sub, nj)
                var mx = (node[a0] + node[b1]) * 0.5f
                var mz = (node[a0 + 2] + node[b1 + 2]) * 0.5f
                var dx = mx - cx0; var dz = mz - cz0
                val dl = sqrt(dx * dx + dz * dz)
                if (dl > 1e-4f) { dx /= dl; dz /= dl }
                mx += dx * 0.6f; mz += dz * 0.6f
                val my = (node[a0 + 1] + node[b1 + 1]) * 0.5f + 0.55f
                kit.text("dA = r dr dθ", mx, my, mz, glyph(kit, mx, my, mz) * 0.9f, hc, 1f, anchor = -0.5f)
            }
        }

        // The chart's own two names, on the net from the first frame and never changed: the flat
        // picture is already the (r, θ) chart, and that is exactly the thing the stop is saying.
        if (q == 0) {
            val pr = ni(NR, nj / 2, nj)
            val pq = ni(NR - 1, nj / 2, nj)
            var dx = node[pr] - node[pq]; var dz = node[pr + 2] - node[pq + 2]
            var dl = sqrt(dx * dx + dz * dz)
            if (dl > 1e-4f) { dx /= dl; dz /= dl }
            val ax2 = node[pr] + dx * 0.5f
            val az2 = node[pr + 2] + dz * 0.5f
            val ay2 = node[pr + 1] + 0.45f
            kit.text("r", ax2, ay2, az2, glyph(kit, ax2, ay2, az2), gc, 0.95f)

            val pt = ni(NR, nj * 3 / 4, nj)
            dx = node[pt] - cx0; dz = node[pt + 2] - cz0
            dl = sqrt(dx * dx + dz * dz)
            if (dl > 1e-4f) { dx /= dl; dz /= dl }
            val bx2 = node[pt] + dx * 0.5f
            val bz2 = node[pt + 2] + dz * 0.5f
            val by2 = node[pt + 1] + 0.45f
            kit.text("θ", bx2, by2, bz2, glyph(kit, bx2, by2, bz2), gc, 0.95f)
        }
    }
}
