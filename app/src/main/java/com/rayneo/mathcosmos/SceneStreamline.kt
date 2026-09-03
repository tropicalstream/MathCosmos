package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR VI, stop 2 — THE STREAMLINE. "Let go and the field draws my path for me."
 *
 * Stop one hung an arrow at every point. This is the sentence that follows it: an arrow at every
 * point is a set of instructions, and if you stop steering and simply obey them you trace a curve.
 * That curve — the integral curve, the flow line, the streamline — is the whole of the stop, and
 * navigation cuts the drive to draw it.
 *
 * THE HONEST BIT FIRST. The craft does not really fly the flow line. The rail is fixed geometry
 * laid down before the tour starts and no scene is allowed to move it, so what is drawn here is
 * the flow line THROUGH the rail's own point at this stop, growing forward from a little astern of
 * it. Tour VI's field drifts the same way the rail runs, so the thread leaves the tube slowly and
 * the craft does fly along it for most of the pass, which is as near the real thing as a railed
 * ride gets. The crew says "we are being carried"; the geometry says "this is the curve you would
 * be carried along". That gap is a metaphor, and the code should own it rather than hide it.
 *
 * THE GHOST COURSE IS THE ARGUMENT. A curved thread on its own proves nothing — every scene in
 * this app draws curved threads. What makes "let go" mean anything is the dashed straight line
 * beside it: the course that was being held before the drive was cut, run out to EXACTLY the same
 * arc length as the thread. Same distance flown, different place arrived at. One dashed line, and
 * it carries the stop.
 *
 * EQUAL TIME, NOT EQUAL LENGTH. Every step of the walk is the same interval of TIME, so a step's
 * length is the local speed and nothing else. That is why the tick marks are worth their vertices:
 * where the field is fast they stretch apart, where it is slow they crowd, and the inner probe's
 * ticks sitting bunched beside the outer probe's stretched ones is the picture of Δs = |F| Δt with
 * no arithmetic in it. The travelling beads inherit the same property for free — a bead that
 * advances one step per tick covers more ground where the field is strong.
 *
 * MIDPOINT, NOT EULER, and this one is not fussiness. Tour VI's field is a swirl about the rail;
 * plain Euler on a rotation spirals outward by a factor of sqrt(1 + (ωh)²) every step, which over
 * thirty steps is about a tenth of the radius. An outward creep of that size would walk the inner
 * threads into their neighbours and the scene would end up contradicting the one thing it exists
 * to assert. A midpoint step costs a second field evaluation and keeps the helices honest.
 *
 * THE WEAVE AND THE GAP. Flow lines cannot cross, because the field has one value at each point
 * and two curves through the same point would be the same curve. Four threads at four different
 * distances from the swirl's axis sweep across one another constantly in the image and never touch
 * in depth, which is the one thing a stereoscopic display can show and a textbook figure cannot.
 * The nearest approach between any two of them is measured every frame and reported on the HUD; a
 * number that stays stubbornly off zero is the claim, stated in the only place numbers are legible.
 * Be aware it is the least distance between SAMPLED points and so sits a shade above the true
 * separation of the curves — at a fifth of a unit per sample the difference is small, and the
 * assertion is not that it is 0.41 but that it is never nought.
 *
 * SIZE. Tour VI's wall alpha is 0.15 and the tube is a guide-rail, so this is built out into the
 * open on purpose: the threads curl a couple of units past the passage radius and are meant to.
 * A flow that stopped at the wall would be a diagram of a field rather than a field.
 *
 * BUDGET. One flushLines of about four hundred vertices, three lit beads and two labels: six draw
 * calls. The probes are the scene's own rather than the ship's arms — the arm mechanism is the
 * whole apparatus of the next stop and reads better for not having been spent early.
 */
object SceneStreamline : MathScene {

    /** The threads should be standing and flowing before the craft is on them. */
    override val reach = 1.5f

    /** The walk runs about four units downrange against sixteen-unit node spacing. */
    override val deep = 0.4f

    // ---- the clock -----------------------------------------------------------------------------
    // Drive held, drive cut and the thread drawn, three probes away one after another, and then a
    // little over five seconds in which nothing changes but the beads. That rest is the state the
    // crew talks over and the state a viewer arriving late has to be able to simply look at.
    private const val PERIOD = 24f
    private const val CUT = 0.07f
    private const val GROW = 0.20f
    private const val REL = 0.32f          // the first probe leaves
    private const val REL_GAP = 0.07f      // and the others follow it
    private const val REL_LEN = 0.09f      // how long a probe takes to reach its station
    private const val PGROW = 0.16f        // and to draw its own thread
    private const val REST = 0.74f
    // The whole scene dims out over the last of the cycle so that the reset — four threads
    // vanishing at once — happens off camera. A hard cut back to nothing is a bang, not a loop.
    private const val CLEAR_AT = 0.965f

    // ---- the walk ------------------------------------------------------------------------------
    private const val N = 4                // the ship's own thread, and three probes
    private const val STEPS = 30
    private const val STEPS_LOW = 16
    private const val STEPS_MIN = 12
    private const val H = 0.16f            // seconds of flow per step

    /**
     * Where each thread is seeded, in stage coordinates: across, up, and along the rail.
     *
     * The ship's own thread starts a little astern and a little off the rail centre — dead centre
     * would put a bright line through the pilot's eye at the closest point of the pass, and the
     * near fade below would then spend the whole approach switching it off. The three probes go
     * two abeam and one high, so the weave is visible in depth as well as across the frame. What
     * matters for the picture is that the four end up at four clearly different distances from the
     * swirl's axis, which they do whichever way round the rail frame's side vector happens to point.
     */
    private val SEED = floatArrayOf(
        -0.25f, -0.30f, -1.60f,
        -1.55f,  0.30f, -1.20f,
         1.35f, -0.40f, -1.20f,
         0.60f,  1.30f, -1.20f
    )

    // ---- weights -------------------------------------------------------------------------------
    private const val RIDE = 6.5f          // seconds for a bead to run the length of a thread
    private const val BAND = 0.22f         // half-width of the hero thread's travelling brightness
    private const val BAND_PEAK = 0.65f
    private const val LIT_HERO = 0.34f
    private const val LIT_PROBE = 0.24f
    private const val TICK = 3             // an equal-time mark every third step
    private const val TICK_LEN = 0.075f
    private const val BEAD = 0.075f

    // Nothing bright is drawn within arm's reach of the eye: a thread half a metre from the face is
    // a smear across the middle of the display that says nothing about the field. Same numbers as
    // the tour's ambient, so the two agree about where the near distance is.
    private const val R_NEAR = 1.15f
    private const val R_NEAR_FULL = 2.0f

    // Cool where slow, warm where fast — deliberately the ambient's ramp, so a thread and the
    // arrows around it are saying the same thing about the same field in the same colours.
    private const val SLOW = 0.70f
    private const val FAST = 2.10f

    // ---- scratch -------------------------------------------------------------------------------
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val fv = FloatArray(3)
    private val rv = FloatArray(3)         // the readout's own, so it never races a walk in progress
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val pc = FloatArray(3)
    private val tint = FloatArray(3)
    private val grown = FloatArray(N)
    private val pts = FloatArray(N * (STEPS + 1) * 3)
    private val spd = FloatArray(N * (STEPS + 1))

    // ---- what was measured, cached for the HUD ---------------------------------------------------
    // The same arrangement THE COLUMN FIELD uses: the number the HUD reads is the number the last
    // frame actually drew, so the world and the readout can never disagree with one another.
    private var gap = 0f
    private var heroArc = 0f

    /** Two decimal places, without allocating a formatter. */
    private fun fmt(v: Float): String {
        val cents = (abs(v) * 100f + 0.5f).toInt()
        val frac = cents % 100
        return (if (v < -0.005f) "-" else "") + (cents / 100) + "." + (if (frac < 10) "0" else "") + frac
    }

    /** Normalised speed, 0 at [SLOW] and below, 1 at [FAST] and above. */
    private fun norm(s: Float): Float = ((s - SLOW) / (FAST - SLOW)).coerceIn(0f, 1f)

    /** The tour's cool-to-warm ramp at normalised speed [t], into [tint]. */
    private fun tintFor(t: Float) {
        val c = SceneParts.COOL
        val w = SceneParts.WORK
        tint[0] = c[0] + (w[0] - c[0]) * t
        tint[1] = c[1] + (w[1] - c[1]) * t
        tint[2] = c[2] + (w[2] - c[2]) * t
    }

    /**
     * The speed of the field where the craft actually is, and the closest the threads ever came.
     * The first is what the hull is feeling; the second is the theorem.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasField) return null
        kit.fieldAt(kit.shipX, kit.shipY, kit.shipZ, rv)
        val s = sqrt(rv[0] * rv[0] + rv[1] * rv[1] + rv[2] * rv[2])
        if (SceneParts.cycle(kit.seconds, PERIOD) < CUT) return "DRIVE HELD   |F| " + fmt(s)
        if (gap <= 0f) return "|F| " + fmt(s)
        return "|F| " + fmt(s) + "   GAP " + fmt(gap)
    }

    /**
     * Midpoint-step thread [t] forward from the seed already written at its slot 0, filling its
     * points and the speed at each of them. Returns the arc length, which is what the ghost course
     * is measured out to.
     *
     * The walk is always run in full even when only part of it is drawn: the geometry then stands
     * still while the reveal moves over it, rather than wriggling as it grows, and the nearest
     * approach is measured on the finished curves from the first frame.
     */
    private fun walk(kit: SceneKit, t: Int, steps: Int): Float {
        val o = t * (STEPS + 1)
        var x = pts[o * 3]
        var y = pts[o * 3 + 1]
        var z = pts[o * 3 + 2]
        var arc = 0f
        for (st in 1..steps) {
            kit.fieldAt(x, y, z, fv)
            val hx = x + fv[0] * H * 0.5f
            val hy = y + fv[1] * H * 0.5f
            val hz = z + fv[2] * H * 0.5f
            kit.fieldAt(hx, hy, hz, fv)
            val dx = fv[0] * H
            val dy = fv[1] * H
            val dz = fv[2] * H
            x += dx; y += dy; z += dz
            arc += sqrt(dx * dx + dy * dy + dz * dz)
            val k = (o + st) * 3
            pts[k] = x; pts[k + 1] = y; pts[k + 2] = z
            spd[o + st] = sqrt(fv[0] * fv[0] + fv[1] * fv[1] + fv[2] * fv[2])
        }
        // The seed's own speed is the first step's, which saves a field evaluation and is what the
        // colour of the first segment wants anyway.
        spd[o] = spd[o + 1]
        // Pad the tail when the governor has shortened the walk, so the nearest-approach search and
        // the riding beads can never read geometry left over from a hotter frame.
        for (st in steps + 1..STEPS) {
            val k = (o + st) * 3
            pts[k] = x; pts[k + 1] = y; pts[k + 2] = z
            spd[o + st] = spd[o + steps]
        }
        return arc
    }

    /**
     * One thread, revealed as far as [grow], coloured by local speed. [band] is the phase of a
     * travelling brightness along it, or negative for none.
     */
    private fun thread(
        kit: SceneKit, line: FloatArray, v0: Int, t: Int, steps: Int,
        grow: Float, lit: Float, band: Float, ticks: Boolean, dim: Float
    ): Int {
        var v = v0
        val o = t * (STEPS + 1)
        val head = grow * steps
        for (st in 0 until steps) {
            var a = (head - st).coerceIn(0f, 1f)
            if (a <= 0.01f) break
            val i = (o + st) * 3
            val j = i + 3
            val u = (st + 0.5f) / steps
            a *= ((1f - u) * 6f).coerceAtMost(1f)          // the far end is not snipped off
            val ex = pts[i] - kit.camX
            val ey = pts[i + 1] - kit.camY
            val ez = pts[i + 2] - kit.camZ
            val d = sqrt(ex * ex + ey * ey + ez * ez)
            a *= ((d - R_NEAR) / (R_NEAR_FULL - R_NEAR)).coerceIn(0f, 1f)
            if (a <= 0.01f) continue
            var bright = lit
            if (band >= 0f) {
                // Wrapped distance to the band, so the band leaving the tip and the band entering
                // the seed are one band and the loop has no seam.
                var dd = abs(u - band)
                if (dd > 0.5f) dd = 1f - dd
                val rise = (1f - dd / BAND).coerceAtLeast(0f)
                bright += BAND_PEAK * rise * rise
            }
            tintFor(norm(spd[o + st + 1]))
            v = MathMesh.segment(
                line, v, pts[i], pts[i + 1], pts[i + 2], pts[j], pts[j + 1], pts[j + 2],
                tint[0], tint[1], tint[2], (bright * a * dim).coerceAtMost(1f)
            )
            if (ticks && st > 0 && st % TICK == 0) {
                // The mark lies across the flow AND across the line of sight, so an equal-time tick
                // is broadside to the eye instead of pointing at it and vanishing.
                val fx = pts[j] - pts[i]
                val fy = pts[j + 1] - pts[i + 1]
                val fz = pts[j + 2] - pts[i + 2]
                var cx = fy * ez - fz * ey
                var cy = fz * ex - fx * ez
                var cz = fx * ey - fy * ex
                val cl = sqrt(cx * cx + cy * cy + cz * cz)
                if (cl > 1e-5f) {
                    cx = cx / cl * TICK_LEN; cy = cy / cl * TICK_LEN; cz = cz / cl * TICK_LEN
                    v = MathMesh.segment(
                        line, v, pts[i] - cx, pts[i + 1] - cy, pts[i + 2] - cz,
                        pts[i] + cx, pts[i + 1] + cy, pts[i + 2] + cz,
                        tint[0], tint[1], tint[2], 0.55f * a * dim
                    )
                }
            }
        }
        return v
    }

    /** The point [r] of the way along thread [t]'s walk, into [out]. */
    private fun ride(t: Int, steps: Int, r: Float, out: FloatArray) {
        val p = r.coerceIn(0f, 1f) * steps
        var idx = p.toInt()
        if (idx >= steps) idx = steps - 1
        val fr = p - idx
        val i = (t * (STEPS + 1) + idx) * 3
        out[0] = pts[i] + (pts[i + 3] - pts[i]) * fr
        out[1] = pts[i + 1] + (pts[i + 4] - pts[i + 1]) * fr
        out[2] = pts[i + 2] + (pts[i + 5] - pts[i + 2]) * fr
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No field, no flow lines. Every Tour VI scene has to be able to say this.
        if (!kit.hasField) return

        val q = kit.quality
        // Three threads is the floor: two lines that miss each other are a coincidence, three that
        // all miss each other are a rule. So the governor takes steps and decoration first.
        val threads = if (q >= 2) 3 else N
        val steps = if (q == 0) STEPS else if (q == 1) STEPS_LOW else STEPS_MIN

        SceneParts.stage(kit, i.toFloat(), 0f, 0f, f, g)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val dim = if (c > CLEAR_AT) 1f - (c - CLEAR_AT) / (1f - CLEAR_AT) else 1f
        if (dim <= 0.01f) return

        // --- seed and walk every thread, drawn or not ---------------------------------------------
        for (t in 0 until threads) {
            SceneParts.at(g, SEED[t * 3], SEED[t * 3 + 1], SEED[t * 3 + 2], pa)
            val b = t * (STEPS + 1) * 3
            pts[b] = pa[0]; pts[b + 1] = pa[1]; pts[b + 2] = pa[2]
            val arc = walk(kit, t, steps)
            if (t == 0) heroArc = arc
            grown[t] = if (t == 0) {
                SceneParts.step(c, CUT, GROW)
            } else {
                SceneParts.step(c, REL + (t - 1) * REL_GAP + REL_LEN, PGROW)
            }
        }

        val line = kit.lineBuf
        var v = 0

        // --- the course we were holding -------------------------------------------------------
        // Straight out along the rail from the ship's own seed, to the same arc length the thread
        // reaches. Bright while the drive is on, a ghost afterwards: it stays for the rest of the
        // cycle precisely so the departure can be seen against something.
        SceneParts.at(g, SEED[0], SEED[1], SEED[2], pb)
        if (q < 2) {
            val ghost = 0.58f - 0.42f * SceneParts.step(c, CUT, 0.14f)
            v = MathMesh.dashed(
                line, v, pb[0], pb[1], pb[2],
                pb[0] + g[9] * heroArc, pb[1] + g[10] * heroArc, pb[2] + g[11] * heroArc,
                14, SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], ghost * dim
            )
        }

        // --- the threads --------------------------------------------------------------------------
        // The ship's thread gets a travelling brightness instead of a bead, because on that one
        // curve the ship IS the bead and a second marker riding beside it would be a lie.
        val phase = SceneParts.cycle(kit.seconds, RIDE)
        v = thread(kit, line, v, 0, steps, grown[0], LIT_HERO, phase, q == 0, dim)
        for (t in 1 until threads) {
            v = thread(kit, line, v, t, steps, grown[t], LIT_PROBE, -1f, q == 0 && t < 3, dim)
        }

        // --- the claim, measured ------------------------------------------------------------------
        // Two flow lines through one point would be one flow line, so these can never meet. The
        // search is over sampled points and its answer is therefore a shade generous; what it is
        // being asked is not how far apart they are but whether the distance is ever nought.
        var best = 1e9f
        var bi = -1
        var bj = -1
        val stride = if (q == 0) 2 else 3
        for (t1 in 0 until threads) {
            if (grown[t1] < 0.5f) continue
            for (t2 in t1 + 1 until threads) {
                if (grown[t2] < 0.5f) continue
                var s1 = 0
                while (s1 <= steps) {
                    val a = (t1 * (STEPS + 1) + s1) * 3
                    var s2 = 0
                    while (s2 <= steps) {
                        val b = (t2 * (STEPS + 1) + s2) * 3
                        val dx = pts[a] - pts[b]
                        val dy = pts[a + 1] - pts[b + 1]
                        val dz = pts[a + 2] - pts[b + 2]
                        val d2 = dx * dx + dy * dy + dz * dz
                        if (d2 < best) { best = d2; bi = a; bj = b }
                        s2 += stride
                    }
                    s1 += stride
                }
            }
        }
        gap = if (bi >= 0) sqrt(best) else 0f

        // The rung across the narrowest place, during the rest — the gap drawn as an object, so the
        // number on the HUD has something in the world to be the length of.
        if (bi >= 0 && q < 2 && c > REST - 0.06f) {
            val pulse = 0.42f + 0.30f * sin(kit.seconds * 2.2f)
            v = MathMesh.dashed(
                line, v, pts[bi], pts[bi + 1], pts[bi + 2], pts[bj], pts[bj + 1], pts[bj + 2],
                5, SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], pulse * dim
            )
        }

        kit.flushLines(v, 2.4f)

        // --- the probes ---------------------------------------------------------------------------
        // Each one flies out to its station, draws its thread by being the pen, and then rides it
        // over and over. Three lit beads is three draw calls; the governor drops them first.
        if (q < 2) {
            for (t in 1 until threads) {
                val rel = SceneParts.step(c, REL + (t - 1) * REL_GAP, REL_LEN)
                if (rel <= 0.001f) continue
                var alpha = 1f
                if (rel < 0.999f) {
                    SceneParts.at(g, SEED[t * 3], SEED[t * 3 + 1], SEED[t * 3 + 2], pc)
                    pa[0] = pb[0] + (pc[0] - pb[0]) * rel
                    pa[1] = pb[1] + (pc[1] - pb[1]) * rel
                    pa[2] = pb[2] + (pc[2] - pb[2]) * rel
                } else if (grown[t] < 0.999f) {
                    ride(t, steps, grown[t], pa)
                } else {
                    // Riding, on its own clock and its own phase, faded at both ends of the run so
                    // that the jump from tip back to seed happens where it cannot be seen.
                    val r = (phase + t * 0.31f) % 1f
                    ride(t, steps, r, pa)
                    alpha = (r / 0.10f).coerceAtMost(1f) * ((1f - r) / 0.12f).coerceAtMost(1f)
                }
                kit.ball(
                    pa[0], pa[1], pa[2], BEAD, BEAD, BEAD,
                    SceneParts.HOT, SceneParts.ADDED, alpha * dim, glow = 1.6f
                )
            }
        }

        // --- notation -------------------------------------------------------------------------------
        // Beside the threads, out to starboard, where neither the telemetry along the top nor the
        // caption box along the bottom is competing for the same pixels.
        val named = SceneParts.step(c, CUT + 0.06f, 0.10f) * dim
        if (named > 0.02f) {
            SceneParts.at(g, 2.85f, 0.15f, 1.10f, pa)
            kit.text("x'(t) = F(x)", pa[0], pa[1], pa[2], 0.20f, SceneParts.HOT, named)
        }
        // The tick marks named, and only at full quality: this is the secondary line, and it is
        // meaningless without the marks it is about.
        if (q == 0 && grown[2] > 0.4f) {
            SceneParts.at(g, -2.75f, -0.50f, 2.30f, pa)
            kit.text(
                "Δs = |F| Δt", pa[0], pa[1], pa[2], 0.16f,
                SceneParts.COOL, 0.85f * dim, GlyphBoard.Style.SMALL
            )
        }
    }
}
