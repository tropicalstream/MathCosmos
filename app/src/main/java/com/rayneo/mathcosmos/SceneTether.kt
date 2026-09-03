package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR V, stop 8 — THE TETHER. "The best point along a fence is where the fence just grazes a
 * contour."
 *
 * The flagship of the tour's second half, and the one stop in the series where two pictures that
 * look nothing alike turn out to be the same sentence. A fence is drawn on the ground and a rider
 * is shackled to it; it walks the fence, climbing as it goes, and where it stops is the highest
 * ground the fence reaches. Two things happen there at once, and a viewer sees both: the fence
 * stops CUTTING ACROSS the contour rings and lies along one of them, and the two arrows at the
 * rider's foot — uphill, and square out of the fence — swing into line and become one arrow.
 * Those are not two facts. They are the same fact said twice, and that identity is the theorem.
 *
 * THE ANSWER IS FOUND, NOT WRITTEN DOWN. Where the optimum sits is not a constant in this file. A
 * scan of the terrain around the fence each frame, refined by fitting a parabola through the best
 * sample and its two neighbours, lands within a tenth of a degree of the true argument maximum on
 * a grid of only twenty-four samples. So if the country is ever retuned, or the rail re-cut, the
 * kiss moves to wherever it now belongs and the picture stays true. A stop about optimisation that
 * had its optimum hard-coded would be a stop about nothing.
 *
 * THE FENCE IS A CIRCLE, and that is a choice, not a limitation. The theorem holds for any smooth
 * constraint; a circle earns its place because ∇g is then exactly the outward radius — no
 * differencing, no error to explain — and because a viewer can see the whole of it at once and
 * check that the rider never leaves it. The crew says so aloud: this is the simplest fence, not
 * the only one.
 *
 * ∇g's LENGTH IS MEANINGLESS AND THAT IS THE POINT. For this fence it is 2R, an artefact of how
 * the constraint happens to be written; write g as the square of the radius or its square root and
 * the arrow changes length without the fence moving an inch. Only the DIRECTION carries anything.
 * So the g arrow is drawn at a fixed length, and λ — the multiplier the whole method is named
 * after — is precisely the ratio the fusing animation closes. Which means the fuse animates only
 * length and colour. The directions have already come together on their own by the time it runs,
 * because the rider genuinely walked to the genuine maximum. If the animation had to TURN the
 * arrows to make them agree, this scene would be lying, and it does not.
 *
 * THE CONTOUR IS TRACED LIVE. The level set through the rider is walked out from the rider's own
 * feet by a predictor along the contour direction and a Newton corrector back onto the level —
 * about twenty steps, drift under a thousandth of a unit of height. It is the real level curve of
 * the real terrain, sliding and turning under the rider as it walks, so the grazing at the end is
 * watched rather than asserted. A worthwhile accident falls out of this: on this country the
 * fence's high point sits at height 0.889, and the ambient's lit rings are at fixed heights ±0.3,
 * ±0.9, ±1.5, ±2.1 — so the contour being kissed IS one of the rings already burning on the
 * ground, to within a hundredth of a level. Nothing depends on that; the trace is computed either
 * way. But it is why the kiss reads at a distance.
 *
 * PLACEMENT. The ring is anchored through [SceneKit.pointAt] — the rail's own frame — and then
 * built entirely in the ground's (x, z), because a constraint on a landscape lives in the flat
 * country underneath it and the stage plane that the algebra tours use stands up square to the
 * passage. It is nine units across in a passage four wide, which is Tour V's licence: the walls
 * are ghosts here and the scenery is the open ground. Two placements were worth the arithmetic.
 * The fence sits to port with its nearest point about a unit and a half off the rail, and on this
 * hillside the ground rises towards the rail, so the fence's high point IS its nearest point: the
 * theorem completes right beside the craft and the tether is shortest at exactly that moment. And
 * the walk starts a hundred degrees back round the ring rather than half way round it — further
 * back than that puts the rider near the fence's low point, where |∇f| falls to a twentieth and
 * the uphill arrow's direction jitters about, which looks like a broken instrument rather than
 * flat ground. From a hundred degrees the climb is monotone and the angle between the arrows falls
 * steadily from about a hundred and seven degrees to nothing.
 *
 * The gap between the arrows is drawn as an object — a translucent wedge in the tour's colour for
 * a debt, shrinking to nothing — for the same reason THE COMPLETED SQUARE outlines its missing
 * corner: an absence that is not drawn is an absence nobody notices closing.
 */
object SceneTether : MathScene {

    /** Wide: the ring wants to be seen whole, from well back, before the walk means anything. */
    override val reach = 1.7f

    /** The fence runs six or seven units past the node down the rail; do not cull it at its own stop. */
    override val deep = 0.6f

    private const val TAU = 6.2831853f
    private const val PERIOD = 26f

    // ---- the fence -----------------------------------------------------------------------------
    private const val R_F = 4.6f           // radius, in world units
    private const val C_SIDE = -6.0f       // its centre, to port of the node
    private const val C_AHEAD = 2.0f       // and a little further along the rail
    private const val SPAN = 1.745f        // a hundred degrees of fence walked — see the header
    private const val POST_H = 0.30f       // post height; a fence you can see is a fence you cannot cross

    // ---- measuring ------------------------------------------------------------------------------
    private const val EPS = 0.30f          // central-difference step, as at THE COMPASS
    private const val GAIN = 5.0f          // world units of arrow per unit of |∇f| — same as THE COMPASS,
    private const val G_LEN = 1.25f        // so arrow lengths mean the same thing at both stops
    private const val LIFT = 0.06f         // clear of the ambient mesh, so nothing z-fights
    private const val DS = 0.55f           // one step of the contour trace

    // ---- counts ----------------------------------------------------------------------------------
    private const val RING = 72
    private const val SCAN = 32
    private const val TRACE = 10

    private val o = FloatArray(3)          // the anchor, and general scratch
    private val gf = FloatArray(2)         // ∇f at the rider
    private val gw = FloatArray(2)         // ∇f wherever the contour trace is looking
    private val blend = FloatArray(4)      // the g arrow's colour, easing to the f arrow's

    // The HUD's copy of the reading. [readout] runs on the UI thread and must not call
    // [SceneKit.pointAt] or [SceneKit.frame] — both go through renderer-owned temporaries that the
    // draw thread is using — and everything the HUD wants here hangs off the fence's anchor, which
    // is exactly that call. So draw() leaves three floats behind rather than the HUD racing for
    // them. Volatile because two threads touch them; a torn read would cost one stale HUD line.
    @Volatile private var hudH = 0f
    @Volatile private var hudAng = 0f
    @Volatile private var hudMax = 0f
    @Volatile private var hudReady = false

    // ------------------------------------------------------------------ measuring

    /** Central differences on the terrain, exactly as THE COMPASS takes them. */
    private fun grad(kit: SceneKit, x: Float, z: Float, out: FloatArray) {
        out[0] = (kit.terrainHeight(x + EPS, z) - kit.terrainHeight(x - EPS, z)) / (2f * EPS)
        out[1] = (kit.terrainHeight(x, z + EPS) - kit.terrainHeight(x, z - EPS)) / (2f * EPS)
    }

    /**
     * Where on the fence the ground is highest: a coarse scan, then a parabola through the winner
     * and its two neighbours. The refinement is what makes the scan affordable — on a smooth
     * terrain twenty-four samples plus the parabola beat two hundred samples without it, and the
     * result does not flicker between neighbouring samples from frame to frame because the fence is
     * welded to the ground and the sample angles are fixed.
     */
    private fun bestAngle(kit: SceneKit, cx: Float, cz: Float, n: Int): Float {
        var bi = 0
        var bv = -1e9f
        for (k in 0 until n) {
            val a = k * TAU / n
            val h = kit.terrainHeight(cx + cos(a) * R_F, cz + sin(a) * R_F)
            if (h > bv) { bv = h; bi = k }
        }
        val am = ((bi - 1 + n) % n) * TAU / n
        val ap = ((bi + 1) % n) * TAU / n
        val fm = kit.terrainHeight(cx + cos(am) * R_F, cz + sin(am) * R_F)
        val fp = kit.terrainHeight(cx + cos(ap) * R_F, cz + sin(ap) * R_F)
        val den = fm - 2f * bv + fp
        val d = if (abs(den) < 1e-6f) 0f else (0.5f * (fm - fp) / den).coerceIn(-0.5f, 0.5f)
        return (bi + d) * TAU / n
    }

    private fun wrapTau(a: Float): Float {
        var x = a % TAU
        if (x < 0f) x += TAU
        return x
    }

    /**
     * The number that has to be READ rather than looked at: how high the rider has climbed, how far
     * the two arrows still are from agreeing, and the best the fence has to offer. The last one is
     * the answer to the whole optimisation, and it is a number, so it belongs here and not in the
     * geometry.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTerrain || !hudReady) return null
        return "h %+.2f   ∠(∇f,∇g) %2.0f°   max %+.2f".format(Locale.US, hudH, hudAng, hudMax)
    }

    // ------------------------------------------------------------------ drawing

    /**
     * One arm of the level set through (x0, z0), walked out step by step: a predictor along the
     * contour direction — the gradient turned a quarter turn — and then a single Newton correction
     * back onto the level along the gradient. The correction is clamped because on nearly flat
     * ground the step 1/|∇f|² is enormous and would fling the trace across the county; where the
     * ground is that flat the caller has already declined to trace at all.
     */
    private fun traceLevel(
        kit: SceneKit, line: FloatArray, at: Int, x0: Float, z0: Float,
        level: Float, dir: Float, steps: Int, alpha: Float, ground: Float
    ): Int {
        var k = at
        var x = x0
        var z = z0
        var y = ground + kit.terrainHeight(x, z) + LIFT * 1.4f
        val col = SceneParts.ADDED
        for (s in 0 until steps) {
            grad(kit, x, z, gw)
            var m2 = gw[0] * gw[0] + gw[1] * gw[1]
            if (m2 < 1e-4f) break
            val m = sqrt(m2)
            var nx = x - gw[1] / m * DS * dir
            var nz = z + gw[0] / m * DS * dir
            grad(kit, nx, nz, gw)
            m2 = gw[0] * gw[0] + gw[1] * gw[1]
            if (m2 > 1e-4f) {
                val t = ((level - kit.terrainHeight(nx, nz)) / m2).coerceIn(-0.6f, 0.6f)
                nx += gw[0] * t
                nz += gw[1] * t
            }
            val ny = ground + kit.terrainHeight(nx, nz) + LIFT * 1.4f
            // Fade along the arm, so a contour that runs out of the scene tapers off rather than
            // stopping dead and reading as a wall.
            val a0 = alpha * (1f - s.toFloat() / steps * 0.8f)
            val a1 = alpha * (1f - (s + 1f) / steps * 0.8f)
            k = MathMesh.segment(line, k, x, y, z, nx, ny, nz, col[0], col[1], col[2], a0, a1)
            x = nx; z = nz; y = ny
        }
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No terrain callback means no ground to fence off: this stop is meaningless off Tour V.
        if (!kit.hasTerrain) return

        // Anchored through the rail's own frame, then built in the ground's (x, z). The y from
        // pointAt is deliberately thrown away — the fence lies on the country, not on the rail.
        kit.pointAt(i.toFloat(), C_SIDE, 0f, C_AHEAD, o)
        val cx = o[0]
        val cz = o[2]
        val ground = SceneAmbientCountry.GROUND_Y
        val q = kit.quality

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var tv = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val walk = SceneParts.step(c, 0.06f, 0.56f)
        val fuse = SceneParts.step(c, 0.64f, 0.08f)
        // The rider and everything it carries dim away at the very end of the cycle, so the walk
        // resets in the dark. A bead that teleported back round the fence once every twenty-six
        // seconds would be the one thing on screen arguing against the walk it had just made.
        val live = 1f - SceneParts.step(c, 0.93f, 0.05f)

        // --- the fence's best point, and where the rider is now ------------------------------
        val star = bestAngle(kit, cx, cz, if (q == 0) SCAN else 20)
        val bx = cx + cos(star) * R_F
        val bz = cz + sin(star) * R_F
        val bh = kit.terrainHeight(bx, bz)

        val startAng = star + SPAN
        val ang = star + SPAN * (1f - walk)
        val ux = cos(ang)
        val uz = sin(ang)                          // the outward radius: ∇g, normalised, exactly
        val rx = cx + ux * R_F
        val rz = cz + uz * R_F
        val rh = kit.terrainHeight(rx, rz)
        val ry = ground + rh + LIFT

        grad(kit, rx, rz, gf)
        val m = sqrt(gf[0] * gf[0] + gf[1] * gf[1])
        var fx = ux
        var fz = uz
        if (m > 1e-5f) { fx = gf[0] / m; fz = gf[1] / m }

        // --- the fence itself ------------------------------------------------------------------
        // Drawn in one pass, each segment coloured by whether the rider has been over it: steel
        // ahead, and the tour's working colour behind, which makes the climb a track and not a
        // claim. The posts are what stop the ring reading as another contour ring.
        val ringN = if (q == 0) RING else RING / 2
        val postEvery = if (q < 2) 6 else 0
        val st = SceneParts.STEEL
        val wk = SceneParts.WORK
        // The track's colour is mixed with the fence's rather than replacing it, so that when the
        // rider fades out at the end of the cycle the walked arc goes back to being fence. Faded to
        // nothing instead, it would leave a hundred-degree hole in a ring whose whole job is to be
        // unbroken.
        val tr = st[0] + (wk[0] - st[0]) * live
        val tg = st[1] + (wk[1] - st[1]) * live
        val tb = st[2] + (wk[2] - st[2]) * live
        val ta2 = 0.55f + 0.40f * live
        var qx = 0f
        var qy = 0f
        var qz = 0f
        for (k in 0..ringN) {
            val a = k * TAU / ringN
            val x = cx + cos(a) * R_F
            val z = cz + sin(a) * R_F
            val y = ground + kit.terrainHeight(x, z) + LIFT * 0.5f
            if (k > 0) {
                val d = wrapTau(startAng - a)
                val done = d <= SPAN * walk && d <= SPAN
                v = if (done) MathMesh.segment(line, v, qx, qy, qz, x, y, z, tr, tg, tb, ta2)
                else MathMesh.segment(line, v, qx, qy, qz, x, y, z, st[0], st[1], st[2], 0.55f)
            }
            if (postEvery > 0 && k % postEvery == 0 && k < ringN) {
                v = MathMesh.segment(line, v, x, y, z, x, y + POST_H, z, st[0], st[1], st[2], 0.45f)
            }
            qx = x; qy = y; qz = z
        }

        // --- the contour through the rider -------------------------------------------------------
        // The whole stop in one line of geometry: this curve crosses the fence steeply at the
        // start of the walk and lies along it at the end. The flatness guard never fires on this
        // country — the rider's walk begins well clear of the fence's bottom, where |∇f| falls to
        // about a twentieth — and it is here so that a retuned terrain cannot put the trace on
        // ground with no level direction and have it flung across the county by the corrector.
        if (m > 0.05f) {
            val steps = if (q == 0) TRACE else if (q == 1) 6 else 5
            val ta = 0.85f * live
            v = traceLevel(kit, line, v, rx, rz, rh, 1f, steps, ta, ground)
            v = traceLevel(kit, line, v, rx, rz, rh, -1f, steps, ta, ground)
        }

        // --- the two arrows ------------------------------------------------------------------------
        // ∇f at its true relative length, so the reading agrees with THE COMPASS four stops back;
        // ∇g at a fixed one, because its length is an artefact of how g was written down and λ is
        // what absorbs it. The fuse closes that ratio and the colour with it — and nothing else,
        // because the directions have already met.
        val hot = SceneParts.HOT
        val cl = SceneParts.COOL
        val fLen = (m * GAIN).coerceIn(0.30f, 2.1f)
        val gLen = G_LEN + (fLen - G_LEN) * fuse
        blend[0] = cl[0] + (hot[0] - cl[0]) * fuse
        blend[1] = cl[1] + (hot[1] - cl[1]) * fuse
        blend[2] = cl[2] + (hot[2] - cl[2]) * fuse
        blend[3] = 1f
        v = MathMesh.arrow(
            line, v, rx, ry, rz, ux * gLen, 0f, uz * gLen, 0f, 1f, 0f,
            blend[0], blend[1], blend[2], 0.95f * live
        )
        v = MathMesh.arrow(
            line, v, rx, ry, rz, fx * fLen, 0f, fz * fLen, 0f, 1f, 0f,
            hot[0], hot[1], hot[2], live
        )

        // --- the gap between them, as a thing ---------------------------------------------------------
        // A wedge of the debt colour swept from the fence's normal round to the uphill direction.
        // It lies flat at the rider's own height and so cuts into the hillside on its uphill side;
        // that is the same honest crossing THE COMPASS's card makes, and for the same reason — the
        // ground plane and the ground are two different surfaces.
        var da = atan2(gf[1], gf[0]) - ang
        while (da > 3.14159265f) da -= TAU
        while (da < -3.14159265f) da += TAU
        if (q < 2 && abs(da) > 0.02f && live > 0.01f) {
            val tk = SceneParts.TAKEN
            val wr = (if (fLen < gLen) fLen else gLen) * 0.72f
            val slices = if (q == 0) 10 else 6
            val wy = ry - 0.012f
            var wx = rx + ux * wr
            var wz = rz + uz * wr
            for (s in 1..slices) {
                if ((tv + 3) * MathMesh.STRIDE > tri.size) break
                val aa = ang + da * s / slices
                val ex = rx + cos(aa) * wr
                val ez = rz + sin(aa) * wr
                tv = MathMesh.vertex(tri, tv, rx, wy, rz, tk[0], tk[1], tk[2], 0.32f * live)
                tv = MathMesh.vertex(tri, tv, wx, wy, wz, tk[0], tk[1], tk[2], 0.10f * live)
                tv = MathMesh.vertex(tri, tv, ex, wy, ez, tk[0], tk[1], tk[2], 0.10f * live)
                wx = ex; wz = ez
            }
        }

        // --- the place itself, marked ------------------------------------------------------------------
        // Only once the rider is nearly on it. Drawn from the start it would be the answer given
        // away before the walk that earns it.
        if (walk > 0.55f) {
            val ka = (walk - 0.55f) / 0.45f * live
            v = MathMesh.arc(
                line, v, bx, ground + bh + LIFT * 1.6f, bz, 1f, 0f, 0f, 0f, 0f, 1f,
                0.34f, 0f, TAU, if (q == 0) 14 else 8, hot[0], hot[1], hot[2], ka * 0.8f
            )
        }

        kit.flushLines(v, 2.4f)
        kit.flushTris(tv)

        // --- the solid parts -------------------------------------------------------------------------------
        // The tether. The craft is genuinely attached to the fence — that is the stop's name and its
        // premise — so this is a rod and not a line: it has a body in stereo, it swings through the
        // whole pass, and it is at its shortest at the moment the theorem closes.
        kit.rod(
            kit.shipX, kit.shipY - 0.20f, kit.shipZ, rx, ry + 0.13f, rz,
            0.026f, SceneParts.STEEL, SceneParts.LAMP, 0.22f + kit.beat * 0.5f
        )
        // The shackle, running out along the tether when the arms reach at this stop.
        if (kit.reach > 0.03f) {
            val t = kit.reach
            val sx = kit.shipX + (rx - kit.shipX) * t
            val sy = (kit.shipY - 0.20f) + (ry + 0.13f - (kit.shipY - 0.20f)) * t
            val sz = kit.shipZ + (rz - kit.shipZ) * t
            kit.ball(sx, sy, sz, 0.09f, 0.09f, 0.09f, SceneParts.LAMP, hot, t, 0f, 0f, 1f, 0f, 0f, 1.2f)
        }
        // The rider. Steel with a hot core: a shackle riding a wire, not a light switched on.
        kit.ball(
            rx, ry + 0.13f, rz, 0.13f, 0.13f, 0.13f, SceneParts.STEEL, hot, live,
            0f, 0f, 1f, 0f, 0f, 0.6f + kit.beat * 0.8f, false
        )
        // And the instant of alignment, which is worth one bright brief lamp and no more.
        if (fuse > 0.02f && fuse < 0.98f) {
            val fl = 1f - fuse
            kit.ball(
                rx, ry + 0.13f, rz, 0.15f, 0.15f, 0.15f, hot, SceneParts.ADDED,
                fl * live, 0f, 0f, 1f, 0f, 0f, 3f * fl
            )
        }

        // --- notation ------------------------------------------------------------------------------------------
        // Beside each arrow, out along its own perpendicular in the ground plane and lifted clear of
        // it — which is what BESIDE means for a figure you are looking down at — and on opposite
        // sides of the pair, so the two labels do not collide as the arrows close.
        val fpx = -fz
        val fpz = fx
        val gpx = -uz
        val gpz = ux
        if (fuse > 0.5f) {
            // The theorem, and only once the picture has already made it true. λ is a ratio of two
            // lengths, which is exactly what the fuse just closed.
            kit.text(
                "∇f = λ∇g", rx + fx * fLen + fpx * 0.55f, ry + 0.30f, rz + fz * fLen + fpz * 0.55f,
                0.21f, hot, 1f, GlyphBoard.Style.MATH, 1.2f
            )
        } else {
            kit.text(
                "∇f", rx + fx * fLen + fpx * 0.48f, ry + 0.26f, rz + fz * fLen + fpz * 0.48f,
                0.22f, hot, 1f, GlyphBoard.Style.MATH, 1.2f
            )
            // On the far side of the pair from the ∇f label, so the two do not collide as the
            // arrows close on one another.
            if (q < 2) {
                kit.text(
                    "∇g", rx + ux * gLen - gpx * 0.48f, ry + 0.22f, rz + uz * gLen - gpz * 0.48f,
                    0.20f, blend, 0.9f * live, GlyphBoard.Style.MATH, 1f
                )
            }
        }
        // The fence, named once, on a stretch of it the rider is not standing on.
        if (q == 0) {
            val la = star - 0.55f
            val lx = cx + cos(la) * R_F
            val lz = cz + sin(la) * R_F
            kit.text(
                "g = 0", lx, ground + kit.terrainHeight(lx, lz) + 0.42f, lz,
                0.18f, st, 0.75f, GlyphBoard.Style.MATH, 0.9f
            )
        }

        // --- what the HUD will say -----------------------------------------------------------------------------
        val dot = (ux * fx + uz * fz).coerceIn(-1f, 1f)
        hudH = rh
        hudAng = acos(dot) * 57.29578f
        hudMax = bh
        hudReady = true
    }
}
