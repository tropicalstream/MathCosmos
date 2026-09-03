package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR V, stop 3 — THE PLATE. "Close enough in, a smooth landscape is a flat sheet resting on
 * two needles."
 *
 * THE STRAIGHT WORLD (II-6) promoted to two dimensions, and the stop where the tour's first three
 * pieces are bolted together: the country from stop 1, the two cuts from stop 2, and now the one
 * flat sheet those two cuts determine between them. A rigid square plate comes down out of the air
 * onto the two tangent needles and is held by them, and then the ground under it is magnified,
 * twice, until it has settled onto the plate and there is nothing left between the two.
 *
 * THE INFLATE. The spec asks for a world-inflate ×6 twice. A scene cannot inflate the world — that
 * is the renderer's ladder and it belongs to the craft — so this one does it to a square of the
 * country in place: the patch is sampled from a window m times smaller each step and drawn m times
 * further from the point of tangency, which is an isotropic magnification about that point and is
 * arithmetically the same act. The drawn footprint therefore never changes size; only what is
 * inside it does. And the plate is its own image under that magnification, because it is linear and
 * passes through the centre of it — so the plate is NAILED DOWN at every magnification, exactly as
 * the tangent strut is nailed to the corners of the window in THE STRAIGHT WORLD, and the only
 * thing the eye can watch is the ground coming to it.
 *
 * THE CONTROL IS ALREADY ON SCREEN, AND IT IS FREE. The ambient country goes on drawing the true
 * landscape, at true scale, under and around this patch for the whole pass. At ×1 the bright patch
 * lies exactly on that dim mesh, registered with it; by ×36 the patch is a flat sheet and the
 * country around it is still as bent as it ever was. That is the honesty beat the crew speaks
 * aloud, drawn: the landscape is not becoming flat, we are looking at less and less of it. Nothing
 * had to be built for it — no reference panel, no thumbnail — because in this tour the world itself
 * is the reference panel, which is most of the argument for the open-country tours existing.
 *
 * THE FILM is the error, and it is what the stop is actually measuring: at every node of the patch
 * a hair from the plate up (or down) to the ground, and a wall of the same colour round the rim so
 * the gap has a visible thickness rather than being a set of scratches. It is drawn in ONE colour
 * and not signed, because the quantity here is how much the plate gets wrong, not which way — the
 * sign of the curvature is stop 7's subject and it earns a whole stop of its own. Its greatest
 * value across the patch goes to the HUD, where a number can be read.
 *
 * One thing to own up to about the picture. On this stretch of country the ground is convex, so it
 * lies ABOVE the tangent plane and the plate seats into a shallow trough: what the film shows is
 * the ground settling DOWN onto the plate, not rising to meet it. The spec's word for the picture
 * is "rises", and on a summit it would; the sign is whatever the country does under the anchor and
 * bending the drawing to match the sentence would be lying about the one quantity on show. Note too
 * that this terrain barely curves along z at all — the film is nearly a parabolic cylinder, thick
 * along the x edges and a hairline along the z ones. That is not a defect either. It is the two
 * cuts of stop 2 disagreeing about curvature, visible in the shape of the gap.
 *
 * PLACEMENT. To port and below, on the hillside, and NOT underfoot — even though the spec says the
 * ship sits on the plate. A tangent plane's entire content is its two slopes, and slope is
 * invisible from directly above it: a plate seen from the keel is a square, and a square says
 * nothing. Seen in three-quarter view from six units off it is a tilted sheet with a hill under one
 * edge, which is the whole idea in one glance. Six units out and four or so down also keeps the
 * assembly in frame for the entire approach instead of sliding under the craft at the pass.
 *
 * AXES. The patch and the plate are built on world x and z, not on the rail's frame: terrainHeight
 * is a function of world (x, z), the ambient's own lattice is welded to those axes, and the two
 * needles ARE the two cuts, which stop 2 took along them. Only the ANCHOR comes through the stage
 * frame, so the assembly sits beside the rail whichever way the rail is heading. The tour writes
 * its country as z = f(x, y) while the engine's up axis is y, so the maths' y is the world's z; the
 * notation follows the maths, because that is what the crew says out loud.
 *
 * Two smaller decisions. The patch is drawn at its true height with no lift clear of the ambient
 * mesh: a vertical bias would be a bias in exactly the quantity being measured, and it is not
 * needed anyway, since two line grids at different spacings on the same surface interleave rather
 * than z-fight. And the glyphs are half as big again as the corridor tours': those scenes hang
 * their notation a unit and a half from the eye and this one hangs it seven, so the world height
 * goes up to keep the height on the display the same.
 */
object ScenePlate : MathScene {

    /** Wide: the assembly is large and off to one side, and it wants to be seen coming. */
    override val reach = 1.7f

    // ---- the loop ----------------------------------------------------------------------------
    private const val PERIOD = 26f
    private const val MAG = 6f              // one inflate step; two of them, exactly as the spec asks
    private const val HOVER = 2.4f          // how high the plate waits before it is let down

    // ---- the assembly ------------------------------------------------------------------------
    private const val SIDE = -6.0f          // out to port, clear of the ghost tube — see the header
    private const val PATCH = 2.8f          // half-width of plate and patch alike, in world units
    private const val EPS = 0.10f           // central-difference step — see the note on it below
    private const val DROP = 0.045f         // the needles ride just under the plate, so it sits ON them
    private const val NMAX = 10             // cells per side of the patch at quality 0
    private const val LAB = PATCH * 1.42f + 0.5f   // notation stands just clear of the plate's corner

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val tv = IntArray(1)
    private val sx = FloatArray(NMAX + 1)                  // drawn world x of each lattice line
    private val sz = FloatArray(NMAX + 1)
    private val ux = FloatArray(NMAX + 1)                  // the plate's rise across that line, x part
    private val uz = FloatArray(NMAX + 1)                  // and its z part: the plate is separable
    private val hy = FloatArray((NMAX + 1) * (NMAX + 1))   // drawn ground height at each node

    // ---- what the HUD is told -----------------------------------------------------------------
    // Measured in draw() and read by readout(), which the renderer calls from the UI thread when
    // Android feels like rebuilding the telemetry block. That rules out re-deriving the anchor
    // there: kit.frame goes through renderer-owned temporaries and racing the draw thread for them
    // to recover three floats would be a poor trade. Handing over what the picture actually drew
    // also means the HUD cannot disagree with what is on screen.
    private var mag = 1f
    private var cell = PATCH
    private var film = 0f
    private var measured = false

    /**
     * The country's height at a world (x, z). The tour supplies it; a rail with no terrain gets a
     * modest one of its own, because a stop about a landscape flattening has nothing to show over
     * a flat plane and would sit there proving the opposite of its own claim.
     */
    private fun ground(kit: SceneKit, x: Float, z: Float): Float =
        if (kit.hasTerrain) kit.terrainHeight(x, z)
        else 0.90f * sin(x * 0.34f) + 0.70f * sin(z * 0.09f) + 0.40f * sin(x * 0.15f + z * 0.06f)

    /** The world y that the country's zero hangs from — the same datum every Tour V scene adds. */
    private fun datum(kit: SceneKit): Float =
        if (kit.hasTerrain) SceneAmbientCountry.GROUND_Y else -2.2f

    /**
     * The magnification exponent over the cycle: 2 at the wrap, springing back to 0 while the plate
     * is still in the air, then a whole step at a time to 2 again with a long rest on the flattened
     * state. Written as a sum of eased steps so it is continuous across the wrap — a zoom that
     * jumped would read as a dropped frame rather than as the country recoiling.
     */
    private fun zoomExp(c: Float): Float =
        2f * (1f - SceneParts.step(c, 0.00f, 0.12f)) +
            SceneParts.step(c, 0.36f, 0.14f) + SceneParts.step(c, 0.60f, 0.14f)

    /** Magnification. Geometric, because that is the only way a zoom can be interpolated. */
    private fun zoom(c: Float): Float = MAG.pow(zoomExp(c))

    /** Three decimals, without dragging a Formatter in for it. The values here run 0.008 to 3. */
    private fun dec(x: Float): String {
        val t = ((if (x < 0f) -x else x) * 1000f + 0.5f).toInt()
        val r = t % 1000
        return "${t / 1000}." + (if (r < 10) "00" else if (r < 100) "0" else "") + r
    }

    /**
     * The window the patch is cut from — this stop's cell, and the tour's cut falling from a
     * quarter to three hundredths — and the thickest the film gets anywhere on it.
     *
     * Nothing until the first draw has measured the place. The film is second order in the cell, so
     * it loses a factor of six for every factor of six of magnification and never once reaches
     * zero, which is the only reason this line is worth a slot on the HUD.
     */
    override fun readout(kit: SceneKit): String? {
        if (!measured) return null
        return "×${(mag + 0.5f).toInt()}   CELL ${dec(cell)}   FILM ${dec(film)}"
    }

    /**
     * One cell of the film's outer wall: the trapezium between the ground and the plate along one
     * rim edge. Written as two triangles by hand rather than through MathMesh.quad, because the
     * ground rim is not parallel to the plate rim and a parallelogram would quietly mis-draw the
     * gap by the very difference the wall exists to show.
     */
    private fun band(
        tri: FloatArray, at: Int,
        x0: Float, z0: Float, g0: Float, p0: Float,
        x1: Float, z1: Float, g1: Float, p1: Float,
        c: FloatArray, alpha: Float
    ): Int {
        if ((at + 6) * MathMesh.STRIDE > tri.size) return at
        var k = MathMesh.vertex(tri, at, x0, g0, z0, c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, x1, g1, z1, c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, x1, p1, z1, c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, x0, g0, z0, c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, x1, p1, z1, c[0], c[1], c[2], alpha)
        k = MathMesh.vertex(tri, k, x0, p0, z0, c[0], c[1], c[2], alpha)
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // The anchor: beside the rail through the stage frame, then dropped to the ground it finds.
        // Everything after this line is built on world x and z — see the header.
        SceneParts.stage(kit, i.toFloat(), SIDE, 0f, f, g)
        val ax = g[0]
        val az = g[2]
        val base = datum(kit)
        val y0 = base + ground(kit, ax, az)

        // The two slopes, measured rather than written down: central differences over whatever
        // terrain callback the tour is carrying. These two numbers ARE the plate; nothing else
        // about it is chosen. The step is smaller than the compass's, and deliberately: a central
        // difference is wrong by about h²f'''/6, which is a systematic TILT, and by the second
        // inflate the film it is being compared against is a hundredth of a unit. At a tenth of a
        // unit the tilt error is a ten-thousandth and cannot be mistaken for curvature.
        val fx = (ground(kit, ax + EPS, az) - ground(kit, ax - EPS, az)) / (2f * EPS)
        val fz = (ground(kit, ax, az + EPS) - ground(kit, ax, az - EPS)) / (2f * EPS)

        val q = kit.quality
        val ns = if (q == 0) NMAX else if (q == 1) 6 else 4
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val m = zoom(c)
        val land = SceneParts.step(c, 0.14f, 0.12f)
        val hover = (1f - land) * HOVER
        // The film only exists once there is a plate for it to be under. It fades in over the last
        // half of the descent, so it looks like something the plate brings down with it.
        val filmA = SceneParts.ease((land - 0.55f) / 0.45f)
        val seat = SceneParts.step(c, 0.26f, 0.06f)

        // --- sample the country under the patch -------------------------------------------------
        // The drawn lattice never moves: magnifying about the point of tangency leaves the footprint
        // alone and changes only what is inside it. So the x and z of every node are fixed, and the
        // whole of the inflate lives in the one line that computes the height.
        for (k in 0..ns) {
            val t = 2f * k / ns - 1f
            sx[k] = ax + PATCH * t
            sz[k] = az + PATCH * t
            ux[k] = fx * PATCH * t
            uz[k] = fz * PATCH * t
        }
        var worst = 0f
        for (jj in 0..ns) {
            val b = jj * (ns + 1)
            val dz = (sz[jj] - az) / m
            for (ii in 0..ns) {
                val dx = (sx[ii] - ax) / m
                // Isotropic magnification about (ax, y0, az). The plate through that point is its
                // own image under this map, which is why it never has to move.
                val y = y0 + m * (base + ground(kit, ax + dx, az + dz) - y0)
                hy[b + ii] = y
                val e = abs(y - (y0 + ux[ii] + uz[jj]))
                if (e > worst) worst = e
            }
        }
        mag = m
        cell = PATCH / m
        film = worst
        measured = true

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0
        val mid = ns / 2

        // --- the patch, with the two cuts picked out --------------------------------------------
        // The country is drawn cool by the ambient, so the landmark standing on it is warm: the eye
        // separates the specimen from the ground it was cut out of without being told to. The two
        // rows through the anchor are the stop-2 cuts and are drawn hot, so each needle can be seen
        // to be tangent to its own curve — an even number of cells guarantees a row lands on them.
        val cw = SceneParts.WORK
        val ch = SceneParts.HOT
        for (jj in 0..ns) {
            val b = jj * (ns + 1)
            val hot = jj == mid
            val r = if (hot) ch else cw
            val a = if (hot) 1f else 0.55f
            for (ii in 0 until ns) {
                v = MathMesh.segment(
                    line, v, sx[ii], hy[b + ii], sz[jj], sx[ii + 1], hy[b + ii + 1], sz[jj],
                    r[0], r[1], r[2], a
                )
            }
        }
        for (ii in 0..ns) {
            val hot = ii == mid
            val r = if (hot) ch else cw
            val a = if (hot) 1f else 0.55f
            for (jj in 0 until ns) {
                val p = jj * (ns + 1) + ii
                v = MathMesh.segment(
                    line, v, sx[ii], hy[p], sz[jj], sx[ii], hy[p + ns + 1], sz[jj + 1],
                    r[0], r[1], r[2], a
                )
            }
        }

        // --- the film: hairs through it, and a wall round its rim --------------------------------
        val cf = SceneParts.TAKEN
        if (filmA > 0.02f) {
            if (q < 2) {
                // Every other node, both ways: a hair at every one is a thicket that hides the very
                // thing it is measuring, and at quality 1 the patch is coarse enough already.
                var jj = 0
                while (jj <= ns) {
                    val b = jj * (ns + 1)
                    var ii = 0
                    while (ii <= ns) {
                        v = MathMesh.segment(
                            line, v, sx[ii], y0 + ux[ii] + uz[jj], sz[jj], sx[ii], hy[b + ii], sz[jj],
                            cf[0], cf[1], cf[2], 0.85f * filmA
                        )
                        ii += 2
                    }
                    jj += 2
                }
            }
            val fa = 0.34f * filmA
            val bn = ns * (ns + 1)
            for (k in 0 until ns) {
                tv[0] = band(
                    tri, tv[0],
                    sx[k], sz[0], hy[k], y0 + ux[k] + uz[0],
                    sx[k + 1], sz[0], hy[k + 1], y0 + ux[k + 1] + uz[0], cf, fa
                )
                tv[0] = band(
                    tri, tv[0],
                    sx[k], sz[ns], hy[bn + k], y0 + ux[k] + uz[ns],
                    sx[k + 1], sz[ns], hy[bn + k + 1], y0 + ux[k + 1] + uz[ns], cf, fa
                )
                val p0 = k * (ns + 1)
                val p1 = p0 + ns + 1
                tv[0] = band(
                    tri, tv[0],
                    sx[0], sz[k], hy[p0], y0 + ux[0] + uz[k],
                    sx[0], sz[k + 1], hy[p1], y0 + ux[0] + uz[k + 1], cf, fa
                )
                tv[0] = band(
                    tri, tv[0],
                    sx[ns], sz[k], hy[p0 + ns], y0 + ux[ns] + uz[k],
                    sx[ns], sz[k + 1], hy[p1 + ns], y0 + ux[ns] + uz[k + 1], cf, fa
                )
            }
        }

        // --- the plate ----------------------------------------------------------------------------
        // A rigid sheet, ruled so that it reads as one object rather than as an outline, and pale
        // against the warm ground so it is plainly a thing laid ON the country and not part of it.
        val w = 2f * PATCH
        v = SceneParts.pane(
            kit, line, v, tri, tv,
            sx[0], y0 + ux[0] + uz[0] + hover, sz[0],
            w, fx * w, 0f,
            0f, fz * w, w,
            SceneParts.CHALK, 0.92f, 4, 4
        )

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the two needles ------------------------------------------------------------------
        // The tangent lines of the two cuts, run out to the plate's edge so that the sheet is
        // visibly resting on them rather than hovering over a pair of tick marks. They sit one
        // needle-radius below the plane they belong to, which is a lie of four hundredths of a unit
        // and buys the whole "held by them" reading.
        val nl = PATCH * 0.94f
        kit.rod(
            ax - nl, y0 - fx * nl - DROP, az, ax + nl, y0 + fx * nl - DROP, az,
            0.042f, SceneParts.ADDED, SceneParts.HOT, 0.7f
        )
        kit.rod(
            ax, y0 - fz * nl - DROP, az - nl, ax, y0 + fz * nl - DROP, az + nl,
            0.042f, SceneParts.ADDED, SceneParts.HOT, 0.7f
        )

        // The point of tangency: the one place the plate and the country agree exactly, at every
        // magnification, and the point the whole inflate is about.
        kit.ball(
            ax, y0, az, 0.085f, 0.085f, 0.085f, SceneParts.HOT, SceneParts.ADDED,
            1f, glow = 1.2f + kit.beat * 1.5f, small = false
        )
        // The flash as the plate seats on the needles. One lamp, bright and brief.
        if (seat > 0.02f && seat < 0.98f) {
            val fl = 1f - seat
            kit.ball(
                ax, y0 + 0.10f, az, 0.16f, 0.16f, 0.16f, SceneParts.HOT, SceneParts.CHALK,
                fl, glow = 3f * fl
            )
        }

        // --- notation ---------------------------------------------------------------------------
        // Beside the figure and never over or under it: the telemetry block owns the top of the eye
        // and the caption box the bottom. Which END of each needle gets its name is decided from the
        // rail's own side vector, so both labels come down on the inboard side of the plate whatever
        // heading the rail has here, instead of one of them ending up behind it.
        if (q < 2) {
            var hx = g[3]
            var hz = g[5]
            val hl = sqrt(hx * hx + hz * hz)
            if (hl > 1e-4f) { hx /= hl; hz /= hl } else { hx = 1f; hz = 0f }
            val sgx = if (hx >= 0f) 1f else -1f
            val sgz = if (hz >= 0f) 1f else -1f
            val e = nl + 0.45f
            kit.text(
                "f_x", ax + sgx * e, y0 + fx * sgx * e, az, 0.42f,
                SceneParts.ADDED, 0.95f, GlyphBoard.Style.MATH
            )
            kit.text(
                "f_y", ax, y0 + fz * sgz * e, az + sgz * e, 0.42f,
                SceneParts.ADDED, 0.95f, GlyphBoard.Style.MATH
            )
            if (q == 0) {
                // Held at the tangency point's own height rather than in the plate's plane, so a
                // steep hillside cannot swing the line up out of the figure. The ≈ stays for the
                // whole loop and never becomes an =: the film is thinner at every step and is not
                // gone at any of them, which is the entire content of the word "differentiable".
                kit.text(
                    "Δz ≈ f_x Δx + f_y Δy",
                    ax + hx * LAB, y0 + 0.55f, az + hz * LAB, 0.34f,
                    SceneParts.CHALK, 1f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f
                )
            }
        }
    }
}
