package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * TOUR V, stop 12 — THE ROUGH PLACE. "Both slopes can exist and the surface can still be a cliff —
 * smooth is more than having slopes."
 *
 * THE PLATE (V-3) run backwards, and the stop that exists to say where the model breaks. Everything
 * of stop 3's apparatus is brought back deliberately — the same patch, the same two needles, the
 * same plate, the same magnification, the same three numbers on the HUD — so that the one thing
 * that behaves differently has nowhere to hide. There the film thinned toward nothing and the plate
 * settled. Here the plate rocks and the gap will not close, at any magnification.
 *
 * THE SHAPE HAD TO CHANGE, AND IT IS WORTH SAYING WHY. The spec asks for a sharp ridge through the
 * stop, and a straight crease is the wrong object for this particular claim. If the crest runs
 * along one axis, the cut ACROSS it kinks and that partial is gone; if it runs at any other angle,
 * both cuts kink and both partials are gone. A crease through the anchor cannot leave two slopes
 * alive — that is not a drawing problem, it is a theorem. The one shape that keeps both slopes and
 * still refuses a plane is a CONE: a surface made of straight lines out of a single point, whose
 * profile round that point is not a sine wave. So the ridge here is a ridge that narrows to a
 * point, z = r·cos 3θ — three straight ridges and three straight valleys meeting at the anchor.
 * Along the x axis it is the straight line z = x; along the y axis it is dead flat. Both cuts are
 * smooth curves, both needles are honest, both partials exist, and there is no tangent plane.
 *
 * THE COUNTRY IS SMOOTH AND THIS SCENE IS NOT. Tour V's terrain is three sines and is
 * differentiable everywhere, so there is no rough place on it to fly to; the scene brings its own.
 * The patch is the true country PLUS the fan, drawn warm over the ambient's cool mesh, and the
 * seam at the patch rim where the two disagree is not hidden. The roughness is the exhibit, not the
 * terrain, and the crew says so out loud.
 *
 * THE INFLATE IS EXACT HERE, AND THAT IS THE WHOLE POINT. Same magnification as stop 3 and for the
 * same reason a scene cannot inflate the world: the patch is sampled from a window m times smaller
 * and drawn m times further from the anchor, which is an isotropic magnification about it. The
 * smooth ground's share of the height flattens toward the tangent plane like 1/m, exactly as it did
 * at stop 3. The fan's share does not move AT ALL — it is homogeneous of degree one, so
 * m·fan(h/m) = fan(h) identically, and ×1 and ×36 are the same drawing, line for line. "It stays a
 * ridge all the way down" is not a claim this scene makes; it is arithmetic it could not avoid.
 *
 * THE PLATE HINGES ON A REAL CONTACT LINE. The candidate plane — the one the two needles determine
 * between them — touches the surface along BOTH axes exactly and nowhere else, and in the four
 * quadrants between, the ground stands above it in two and hangs below it in two. So the plate can
 * rock about the x cut without ever leaving the surface, and every tilt buries one opposite pair of
 * quadrants deeper while freeing the other. That alternation is the reason no plane fits, which is
 * why the gap hairs here are SIGNED: at stop 3 the sign was another stop's business and the film
 * was drawn in one colour, but here the sign IS the argument, so the hairs are two colours and the
 * checkerboard of them is meant to be read.
 *
 * The rocking itself is choreography, not statics. A rigid plate on this surface would in truth sit
 * balanced on a cross of contacts; what is drawn is a plate hunting for a seat it does not have,
 * because that is what the crew describes and because a plate that simply sat there would say the
 * opposite of the truth. Nothing measured depends on the tilt being physical.
 *
 * THE CONE TIP, further on down the rail, is the coarse failure and the control: a plain spike,
 * z = −√(x² + y²) turned upside down, where not even the two slopes survive. The pair is on screen
 * together on purpose. One of them loses the plane and keeps the slopes, the other loses both, and
 * having them side by side is the only way to say which kind of broken is which.
 *
 * WHAT THE HUD IS TOLD: the same three numbers as stop 3, in the same order, and the third one
 * refuses to move. The gap is measured against the plate AS DRAWN, tilt and all, so a viewer can
 * watch it fail to fall for every tilt the plate tries.
 *
 * PLACEMENT. To starboard and below, five and a half units out, where the ground here runs about
 * three under the keel — a three-quarter view of a tilted plate on a spiked hillside. Directly
 * underfoot it would be a square seen from above, and a square says nothing about slope. Tour V's
 * tube is a ghost at 0.22, so the assembly is allowed well outside the passage radius and should be.
 *
 * AXES. Built on world x and z, not on the rail frame, because terrainHeight is a function of world
 * (x, z) and the two cuts ARE those two coordinates held still in turn. Only the anchor comes
 * through the stage frame. The tour writes its country as z = f(x, y) while the engine's up axis is
 * y, so the maths' y is the world's z; the notation follows the maths, as the crew does.
 */
object SceneRoughPlace : MathScene {

    /** Wide, and it reaches down the rail: the cone tip stands seven units ahead of the anchor. */
    override val reach = 1.7f
    override val deep = 0.6f

    // ---- the loop ------------------------------------------------------------------------------
    private const val PERIOD = 28f
    private const val MAG = 6f              // one inflate step, twice — stop 3's ladder, unchanged

    // ---- the assembly --------------------------------------------------------------------------
    private const val SIDE = 5.4f           // to starboard, clear of the ghost tube
    private const val PATCH = 2.9f          // half-width of plate and patch alike, in world units
    private const val EPS = 0.10f           // central-difference step for the smooth ground's slopes
    private const val FAN = 0.28f           // the fan's amplitude: heights run to FAN·r
    private const val HOVER = 2.6f          // how high the plate waits before it is let down
    private const val ROCK = 0.16f          // the rock, as extra slope across the hinge — about 9°
    private const val DROP = 0.045f         // the needles ride just under the plane they belong to
    private const val NMAX = 10             // cells per side of the patch at quality 0
    private const val LAB = PATCH * 1.35f + 0.5f

    // ---- the cone tip --------------------------------------------------------------------------
    private const val AHEAD = 7.2f          // world units further along the rail
    private const val CONE_R = 1.7f
    private const val CONE_H = 1.55f
    private const val SPOKES = 12           // at quality 0
    private const val RUNGS = 3

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val tv = IntArray(1)
    private val off = FloatArray(NMAX + 1)                 // lattice offset from the anchor, drawn
    private val hy = FloatArray((NMAX + 1) * (NMAX + 1))   // drawn surface height at each node
    private val cc = FloatArray(SPOKES + 1)                // the cone's spoke directions
    private val cs = FloatArray(SPOKES + 1)

    // ---- what the HUD is told -------------------------------------------------------------------
    // Measured in draw() and read by readout() from the UI thread, for the same reason stop 3 does
    // it: re-deriving the anchor there would mean racing the draw thread for the renderer's frame
    // temporaries, and the HUD must not be able to disagree with what is on screen.
    private var mag = 1f
    private var cell = PATCH
    private var gap = 0f
    private var measured = false

    /**
     * The country's height at a world (x, z). A rail with no terrain gets a modest one of its own,
     * because a stop about a surface refusing to flatten has nothing to say over a flat plane.
     */
    private fun ground(kit: SceneKit, x: Float, z: Float): Float =
        if (kit.hasTerrain) kit.terrainHeight(x, z)
        else 0.90f * sin(x * 0.34f) + 0.70f * sin(z * 0.09f) + 0.40f * sin(x * 0.15f + z * 0.06f)

    /** The world y the country's zero hangs from — every Tour V scene adds the same constant. */
    private fun datum(kit: SceneKit): Float =
        if (kit.hasTerrain) SceneAmbientCountry.GROUND_Y else -2.2f

    /**
     * The fan: FAN · r · cos 3θ about the anchor, in closed form so no scene node costs an atan2.
     * r·cos 3θ = r(4cos³θ − 3cosθ) = 4dx³/r² − 3dx, and the value at the anchor itself is defined to
     * be zero, which is the one place the formula cannot be evaluated and the one place the whole
     * stop is about.
     *
     * Two properties are worth having in front of you when reading the rest of this file. It is
     * homogeneous of degree one — fan(t·h) = t·fan(h) — so the magnification leaves it exactly
     * alone. And it is LINEAR along both axes: fan(dx, 0) = FAN·dx and fan(0, dz) = 0, which is
     * precisely why both partials survive and both cut curves come out smooth.
     */
    private fun fan(dx: Float, dz: Float): Float {
        val r2 = dx * dx + dz * dz
        if (r2 < 1e-6f) return 0f
        return FAN * (4f * dx * dx * dx / r2 - 3f * dx)
    }

    /**
     * The magnification exponent over the cycle: ×36 at the wrap, recoiling to ×1 while the plate
     * is still in the air, then a whole step at a time back to ×36, with a long rest at the end on
     * the state that matters. Written as a sum of eased steps so it is continuous across the wrap.
     */
    private fun zoomExp(c: Float): Float =
        2f * (1f - SceneParts.step(c, 0.00f, 0.10f)) +
            SceneParts.step(c, 0.40f, 0.12f) + SceneParts.step(c, 0.62f, 0.12f)

    /**
     * The rock: +1, over to −1, back, and back again, with a hold at each extreme so each catch can
     * be seen. Starts and ends at +1, so nothing jumps at the wrap even though the amplitude has
     * already been taken to nothing by then.
     */
    private fun rock(c: Float): Float =
        1f - 2f * SceneParts.step(c, 0.28f, 0.06f) + 2f * SceneParts.step(c, 0.44f, 0.06f) -
            2f * SceneParts.step(c, 0.60f, 0.06f) + 2f * SceneParts.step(c, 0.76f, 0.06f)

    /**
     * Stop 3's line, to the digit, so the two can be compared from memory: the magnification, the
     * window the patch is cut from, and the worst distance between plate and ground anywhere on it.
     * There the third number fell by a factor of six per step. Here it does not fall at all, and
     * that is the entire content of the word "differentiable" seen from the outside.
     */
    override fun readout(kit: SceneKit): String? {
        if (!measured) return null
        return String.format(Locale.US, "×%d   CELL %.3f   GAP %.3f", (mag + 0.5f).toInt(), cell, gap)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // The anchor: beside the rail through the stage frame, then dropped to the ground it finds.
        // Everything after this line is built on world x and z — see the header.
        SceneParts.stage(kit, i.toFloat(), SIDE, 0f, f, g)
        val ax = g[0]
        val az = g[2]
        val base = datum(kit)
        val y0 = base + ground(kit, ax, az)

        // The smooth ground's two slopes, measured rather than written down. The fan adds FAN to
        // the first and nothing to the second, exactly, so these two lines and that one constant
        // are the whole of the plane the two needles promise.
        val fx = (ground(kit, ax + EPS, az) - ground(kit, ax - EPS, az)) / (2f * EPS)
        val fz = (ground(kit, ax, az + EPS) - ground(kit, ax, az - EPS)) / (2f * EPS)

        val q = kit.quality
        val ns = if (q == 0) NMAX else if (q == 1) 6 else 4
        val mid = ns / 2
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val m = MAG.pow(zoomExp(c))

        // Down, rock, and back up: the lift at the end is what keeps the plate from teleporting
        // into the air at the wrap the way stop 3's does.
        val down = SceneParts.step(c, 0.14f, 0.12f)
        val lift = SceneParts.step(c, 0.90f, 0.10f)
        val amp = (down - lift).coerceIn(0f, 1f)
        val hover = (1f - down + lift).coerceIn(0f, 1f) * HOVER
        val lean = ROCK * rock(c) * amp

        // The plate's two slopes. The hinge is the x cut: the tilt is added entirely across it, in
        // z, so the plate turns about the one line along which it and the surface agree exactly.
        val px = fx + FAN
        val pz = fz + lean

        // --- sample the surface under the patch -------------------------------------------------
        // The drawn lattice never moves; magnifying about the anchor changes only what is inside the
        // footprint. Both spans share one offset table because the patch is square.
        for (k in 0..ns) off[k] = PATCH * (2f * k / ns - 1f)
        var worst = 0f
        for (j in 0..ns) {
            val dz = off[j]
            val b = j * (ns + 1)
            val tz = az + dz / m
            for (k in 0..ns) {
                val dx = off[k]
                // The smooth country, magnified about the anchor, plus the fan, which the
                // magnification cannot touch. That second term is the stop.
                val y = y0 + m * (base + ground(kit, ax + dx / m, tz) - y0) + fan(dx, dz)
                hy[b + k] = y
                val e = abs(y - (y0 + px * dx + pz * dz + hover))
                if (e > worst) worst = e
            }
        }
        mag = m
        cell = PATCH / m
        gap = worst
        measured = true

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // --- the rough patch, with the two cuts picked out ----------------------------------------
        // Warm over the ambient's cool country, and the two rows through the anchor drawn hot: those
        // are the stop-2 cuts, they are smooth, and each has an honest tangent. An even cell count
        // guarantees a lattice row lands on each of them.
        val cw = SceneParts.WORK
        val ch = SceneParts.HOT
        for (j in 0..ns) {
            val b = j * (ns + 1)
            val hot = j == mid
            val r = if (hot) ch else cw
            val a = if (hot) 1f else 0.55f
            for (k in 0 until ns) {
                v = MathMesh.segment(
                    line, v, ax + off[k], hy[b + k], az + off[j],
                    ax + off[k + 1], hy[b + k + 1], az + off[j], r[0], r[1], r[2], a
                )
            }
        }
        for (k in 0..ns) {
            val hot = k == mid
            val r = if (hot) ch else cw
            val a = if (hot) 1f else 0.55f
            for (j in 0 until ns) {
                val p = j * (ns + 1) + k
                v = MathMesh.segment(
                    line, v, ax + off[k], hy[p], az + off[j],
                    ax + off[k], hy[p + ns + 1], az + off[j + 1], r[0], r[1], r[2], a
                )
            }
        }

        // --- the gap, signed ----------------------------------------------------------------------
        // Every other node both ways: a hair at every one is a thicket that hides the thing it is
        // measuring. Teal where the ground stands ABOVE the plate, red where it hangs below, and the
        // point of drawing it in two colours is that both are always present, in opposite quadrants,
        // whichever way the plate leans. That is why there is no plane, and it is watchable.
        if (q < 2 && amp > 0.02f) {
            val up = SceneParts.ADDED
            val dn = SceneParts.TAKEN
            var j = 0
            while (j <= ns) {
                val b = j * (ns + 1)
                val dz = off[j]
                var k = 0
                while (k <= ns) {
                    val dx = off[k]
                    val p = y0 + px * dx + pz * dz + hover
                    val r = if (hy[b + k] > p) up else dn
                    v = MathMesh.segment(
                        line, v, ax + dx, p, az + dz, ax + dx, hy[b + k], az + dz,
                        r[0], r[1], r[2], 0.85f * amp
                    )
                    k += 2
                }
                j += 2
            }
        }

        // --- the plate ------------------------------------------------------------------------------
        // Ruled so it reads as one rigid object rather than an outline, and pale, so it is plainly a
        // thing laid ON the country. Its fill has to stay translucent here for a reason stop 3 did
        // not have: the ground comes THROUGH it in two quadrants and that has to be visible.
        val w = 2f * PATCH
        v = SceneParts.pane(
            kit, line, v, tri, tv,
            ax - PATCH, y0 + hover - px * PATCH - pz * PATCH, az - PATCH,
            w, px * w, 0f,
            0f, pz * w, w,
            SceneParts.CHALK, 0.90f, 4, 4
        )

        // --- the cone tip, further on ------------------------------------------------------------
        // The coarse failure, drawn as a radial wireframe rather than a lit solid: a solid would
        // hide the country behind it, and what has to be legible is the straightness of the spokes
        // all the way into the tip.
        SceneParts.at(g, 0f, 0f, AHEAD, o)
        val bx = o[0]
        val bz = o[2]
        val sp = if (q == 0) SPOKES else if (q == 1) 8 else 6
        val cn = SceneParts.TAKEN
        for (k in 0..sp) {
            val a = 6.2831855f * k / sp
            cc[k] = cos(a); cs[k] = sin(a)
        }
        for (k in 0 until sp) {
            for (j in 0 until RUNGS) {
                val r0 = CONE_R * j / RUNGS
                val r1 = CONE_R * (j + 1) / RUNGS
                val x0 = bx + cc[k] * r0; val z0 = bz + cs[k] * r0
                val x1 = bx + cc[k] * r1; val z1 = bz + cs[k] * r1
                val y0c = base + ground(kit, x0, z0) + CONE_H * (1f - r0 / CONE_R)
                val y1c = base + ground(kit, x1, z1) + CONE_H * (1f - r1 / CONE_R)
                v = MathMesh.segment(line, v, x0, y0c, z0, x1, y1c, z1, cn[0], cn[1], cn[2], 0.85f)
                if (q < 2) {
                    val x2 = bx + cc[k + 1] * r1; val z2 = bz + cs[k + 1] * r1
                    val y2c = base + ground(kit, x2, z2) + CONE_H * (1f - r1 / CONE_R)
                    v = MathMesh.segment(line, v, x1, y1c, z1, x2, y2c, z2, cn[0], cn[1], cn[2], 0.45f)
                }
            }
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the two needles ------------------------------------------------------------------------
        // The tangent lines of the two cuts, run out to the plate's edge so the plate is visibly
        // resting on them. They are the honest slopes of a surface that has no plane, and they are
        // drawn exactly as at stop 3 so that the eye is not invited to blame them.
        val nl = PATCH * 0.94f
        kit.rod(
            ax - nl, y0 - px * nl - DROP, az, ax + nl, y0 + px * nl - DROP, az,
            0.042f, SceneParts.ADDED, SceneParts.HOT, 0.7f
        )
        kit.rod(
            ax, y0 - fz * nl - DROP, az - nl, ax, y0 + fz * nl - DROP, az + nl,
            0.042f, SceneParts.ADDED, SceneParts.HOT, 0.7f
        )

        // The anchor: the one point the plate and the country agree at whatever the tilt, and the
        // one point at which the fan cannot be evaluated.
        kit.ball(
            ax, y0, az, 0.085f, 0.085f, 0.085f, SceneParts.HOT, SceneParts.TAKEN,
            1f, glow = 1.2f + kit.beat * 1.5f, small = false
        )

        // The catch. At each extreme of the rock the plate digs into the far side of the z cut, and
        // one lamp marks where — the edge it is caught on, this time round.
        if (q < 2) {
            val caught = ((abs(rock(c)) - 0.70f) / 0.30f).coerceIn(0f, 1f) * amp
            if (caught > 0.02f) {
                val cd = if (lean >= 0f) -PATCH * 0.86f else PATCH * 0.86f
                val cy = y0 + m * (base + ground(kit, ax, az + cd / m) - y0)
                kit.ball(
                    ax, cy, az + cd, 0.11f, 0.11f, 0.11f, SceneParts.HOT, SceneParts.CHALK,
                    caught, glow = 2.4f * caught
                )
            }
        }

        // The cone's tip, which is the whole of the cone's content.
        kit.ball(
            bx, base + ground(kit, bx, bz) + CONE_H, bz, 0.075f, 0.075f, 0.075f,
            SceneParts.TAKEN, SceneParts.HOT, 0.95f, glow = 1.4f
        )

        // --- notation ---------------------------------------------------------------------------
        // Beside the figure and never over or under it: the telemetry block owns the top of the eye
        // and the caption box the bottom. Which END of each needle is named is decided from the
        // rail's own side vector, so both labels come down on the inboard side of the plate whatever
        // heading the rail has here. The glyphs are stop 3's size, because they hang at stop 3's
        // distance and the height on the display has to match.
        if (q < 2) {
            val sgx = if (g[3] >= 0f) 1f else -1f
            val sgz = if (g[5] >= 0f) 1f else -1f
            val e = nl + 0.45f
            kit.text(
                "f_x", ax + sgx * e, y0 + px * sgx * e, az, 0.40f,
                SceneParts.ADDED, 0.95f, GlyphBoard.Style.MATH
            )
            kit.text(
                "f_y", ax, y0 + fz * sgz * e, az + sgz * e, 0.40f,
                SceneParts.ADDED, 0.95f, GlyphBoard.Style.MATH
            )
        }
        if (q == 0) {
            // Two beats on one line, each arriving only after the picture has already made it true:
            // first the surface is named, then the promise stop 3 made about it is withdrawn. The
            // sign stays ≠ for the whole of the second beat and never softens to ≈ — an ≈ would be
            // the claim of stop 3, and here it is false at every magnification.
            val claim = if (c < 0.40f) "z = r cos 3θ" else "Δz ≠ f_x Δx + f_y Δy"
            kit.text(
                claim, ax + g[3] * LAB, y0 + 0.60f, az + g[5] * LAB, 0.36f,
                SceneParts.CHALK, 1f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f
            )
            kit.text(
                "√(x^2 + y^2)", bx + g[3] * (CONE_R + 0.7f),
                base + ground(kit, bx, bz) + CONE_H * 0.75f, bz + g[5] * (CONE_R + 0.7f), 0.32f,
                SceneParts.TAKEN, 0.95f, GlyphBoard.Style.MATH, 1f, anchor = -0.5f
            )
        }
    }
}
