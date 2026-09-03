package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Tour II, stop 11 — THE FLAT SPOT. "The best point is where the ground stops tilting — but 'flat'
 * doesn't always mean 'best'."
 *
 * A heavy bead is let go on the roof. It rolls uphill, slows, and stops dead on the summit; the
 * needle standing on the roof goes horizontal and the floor's slope ribbon crosses the rail at that
 * same ring. Three instruments, one place. Then the same thing happens on a second, lower summit —
 * identical readings, and the test has just told you a lie. Then a shelf: needle horizontal, ribbon
 * down to nothing and straight back up without crossing, and the bead rolls on. Three flat spots,
 * three meanings, one identical reading.
 *
 * ---------------------------------------------------------------------------------------------
 * THE COMPROMISE, STATED UP FRONT, BECAUSE IT IS THE FIRST THING ANYONE WILL ASK.
 *
 * The roof profile drawn here is this scene's own, not the corridor's. Tour II's roof function is
 * owned by [TourMap] and drawn by [SceneAmbientTrace], and it has no turning point anywhere within
 * reach of this stop: the flattest the corridor's own roof gets on this whole leg is about +0.09 of
 * a unit per node, at roughly p = 10.06, and it never once reaches zero. A scene may only draw, so
 * this stop cannot put three summits over your head. What it can do is draw the leg the crew is
 * describing on a PROFILE BOARD standing beside the rail — a stretch of corridor plotted on square
 * paper, roof over rail, ruled between them, with the slope ribbon under it — and that is what this
 * file is. It is a chart of a corridor, not a window onto this one, and no dashed leader is run up
 * to the ribbon overhead, because that would be claiming an identity that is not there.
 *
 * The corridor's own roof is not let off, though. It gets the last word on the HUD: at the end of
 * the loop the readout quotes the real slope directly overhead, which is the smallest it gets on the
 * tour and is still not zero. That number is deliberately a NUMBER and not a needle. In this tour
 * one unit of x is one node — sixteen world units of corridor — while one unit of f is one world
 * unit of height, so a needle laid on the real roof at a slope of 0.09 would rise less than a
 * centimetre over its whole length and would look dead level. It would tell the viewer the exact
 * opposite of the truth. Numbers that must be read live on the HUD; the geometry stays geometry.
 *
 * ---------------------------------------------------------------------------------------------
 * THREE THINGS ABOUT THE BOARD.
 *
 * It is plotted ISOTROPICALLY — one unit of x across is one unit of height up, [PW] world units for
 * both — so every angle on it is a true angle of f and the needle's tilt IS the number on the HUD.
 * That costs the roof band a fair amount of height and it is worth every millimetre: the moment the
 * vertical is exaggerated, "the needle has gone flat" stops being a measurement and becomes a
 * drawing of one.
 *
 * The bead is gradient ascent wearing a bead's clothes, and the crew says so. A real marble resting
 * on a summit is at an unstable equilibrium and would roll straight back off; this one stops where
 * the ground stops tilting and stays there, because that is the thing being taught. Its speed is
 * choreographed rather than simulated, for the same reason: pure gradient ascent would get stuck on
 * the shelf for ever, and the whole point of the shelf is that the bead rolls on.
 *
 * There are TWO MORE ZEROS in the picture than the crew counts, and they are left unmarked on
 * purpose. Two summits must have a valley between them, and a valley floor is flat too — so the
 * ribbon crosses the rail at five places, not three. The three the bead visits get a ring, a needle
 * and a plumb line; the two troughs get nothing. That is not tidying up. Engineering's closing line
 * is that Doc looked at what the ribbon did EITHER SIDE, and the troughs are where that habit earns
 * its keep: same reading, ribbon running the other way through it. A viewer who notices the valley
 * floors are flat as well has already learned the stop.
 *
 * Placement follows the usual rule. The board is a flat figure, so it hangs to one side at about
 * two units across rather than spanning the rail — a figure centred on the corridor is one you fly
 * into, and at the closest point of the pass you would have a corner of it in frame and nothing
 * else. Its far corner sits 2.5 units out against a passage radius of 3.6, comfortably clear of
 * the wall.
 */
object SceneFlatSpot : MathScene {

    // Faded up early: three bead runs and a rest take most of a leg to watch through once.
    override val reach = 1.5f

    // ---- the board ---------------------------------------------------------------------------
    private const val PERIOD = 28f          // one full demonstration, with a rest on all three
    private const val PW = 1.90f            // world units per unit of x AND per unit of height
    private const val S0 = -0.95f           // board x = 0, in the stage plane
    private const val SIDE = -1.26f         // the board stands off to one side of the rail
    private const val UP = 0.06f
    private const val DZ = -0.28f           // the slope ribbon's zero axis, below the rail
    private const val DK = 0.115f           // world units per unit of f′ — its own scale, see below
    private const val DCLAMP = 0.19f
    private const val NEEDLE = 0.165f       // half-length of a tangent needle
    private const val RING = 0.055f
    private const val DOT = 0.034f
    private const val TAU = 6.2831855f
    private const val DMAX = 1.5f           // roughly max |f′| on the board, for the bead's glow

    /**
     * The profile, as a cubic Hermite chain: x, height, and the slope demanded at each knot.
     *
     * Hermite rather than a polynomial fitted to the features, because here the slope at a knot is
     * something I get to STATE rather than something I have to solve for — and this stop is entirely
     * about slopes being exactly zero at three named places. Knots 1 and 3 are the two summits, 5 is
     * the shelf, and 2 and 4 are the troughs that must exist between them. Every segment is monotone
     * (checked: no segment's derivative changes sign inside it), so there are exactly five turning
     * features and no accidental sixth.
     *
     * The one thing to know when editing these: the ribbon is f′ of a cubic, so it is piecewise
     * quadratic and has a corner at every knot. Those corners land exactly on the interesting
     * places, and at the shelf the corner is the picture — the ribbon comes down to nothing and goes
     * straight back up in a V without crossing, which is word for word what Navigation calls.
     */
    private val KX = floatArrayOf(0f, 0.215f, 0.385f, 0.545f, 0.700f, 0.845f, 1f)
    private val KY = floatArrayOf(0.190f, 0.430f, 0.300f, 0.360f, 0.262f, 0.375f, 0.470f)
    private val KM = floatArrayOf(0.95f, 0f, 0f, 0f, 0f, 0f, 0.72f)

    /** The three knots the bead visits, and when in the cycle each one's marks are struck. */
    private val FLAT = intArrayOf(1, 3, 5)
    private val REVEAL = floatArrayOf(0.218f, 0.488f, 0.728f)
    private val NUM = arrayOf("1", "2", "3")

    // Where each bead run starts. B and C begin just past a trough, so the bead is always climbing.
    private const val A0 = 0.020f
    private const val B0 = 0.410f
    private const val C0 = 0.720f
    private const val C1 = 0.975f

    // ---- scratch. Nothing below allocates. -----------------------------------------------------
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val sAx = FloatArray(3)         // the board's horizontal, unit length
    private val uAx = FloatArray(3)         // the board's vertical
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val pc = FloatArray(3)
    private val pd = FloatArray(3)
    // The marching pair for the one-pass curve build: rail, roof, ribbon axis, ribbon.
    private val rb0 = FloatArray(3); private val rb1 = FloatArray(3)
    private val rf0 = FloatArray(3); private val rf1 = FloatArray(3)
    private val ax0 = FloatArray(3); private val ax1 = FloatArray(3)
    private val rp0 = FloatArray(3); private val rp1 = FloatArray(3)

    // ---- the profile ---------------------------------------------------------------------------

    /** Which Hermite segment [x] falls in. Six knots; a scan is cheaper than anything cleverer. */
    private fun segOf(x: Float): Int {
        var i = 0
        while (i < KX.size - 2 && x > KX[i + 1]) i++
        return i
    }

    /** Roof height at [x], in board units. */
    private fun roofY(x: Float): Float {
        val xc = x.coerceIn(0f, 1f)
        val i = segOf(xc)
        val l = KX[i + 1] - KX[i]
        val t = (xc - KX[i]) / l
        val t2 = t * t
        val t3 = t2 * t
        return (2f * t3 - 3f * t2 + 1f) * KY[i] + (t3 - 2f * t2 + t) * l * KM[i] +
            (-2f * t3 + 3f * t2) * KY[i + 1] + (t3 - t2) * l * KM[i + 1]
    }

    /**
     * The slope at [x]. Analytic, not a difference — which is why the needle at a flat spot is
     * exactly horizontal rather than very nearly so, and why the ribbon touches its axis dead on.
     */
    private fun roofD(x: Float): Float {
        val xc = x.coerceIn(0f, 1f)
        val i = segOf(xc)
        val l = KX[i + 1] - KX[i]
        val t = (xc - KX[i]) / l
        val t2 = t * t
        return (6f * t2 - 6f * t) * KY[i] / l + (3f * t2 - 4f * t + 1f) * KM[i] +
            (-6f * t2 + 6f * t) * KY[i + 1] / l + (3f * t2 - 2f * t) * KM[i + 1]
    }

    /** The ribbon's height at [x]. Clamped, so a steep flank cannot punch out of the board. */
    private fun ribU(x: Float): Float = DZ + (roofD(x) * DK).coerceIn(-DCLAMP, DCLAMP)

    /** A board point: [x] across in profile units, [u] up in world units. */
    private fun board(x: Float, u: Float, out: FloatArray) {
        SceneParts.at(g, S0 + x * PW, u, 0f, out)
    }

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree with the picture --

    /**
     * Where the bead is at cycle position [c]. Three runs: up the first summit, up the second, and
     * through the shelf with a pause on it. The pause is the second [SceneParts.step] waiting its
     * turn — the bead is choreographed, not simulated, and the doc comment above says why.
     */
    private fun beadX(c: Float): Float = when {
        c < 0.3125f -> A0 + (KX[1] - A0) * SceneParts.step(c, 0.040f, 0.180f)
        c < 0.5925f -> B0 + (KX[3] - B0) * SceneParts.step(c, 0.335f, 0.155f)
        else -> C0 + (KX[5] - C0) * SceneParts.step(c, 0.615f, 0.115f) +
            (C1 - KX[5]) * SceneParts.step(c, 0.800f, 0.090f)
    }

    /** The bead fades out and back in between runs rather than sliding backwards down the roof. */
    private fun beadShow(c: Float): Float = when {
        c < 0.3000f -> SceneParts.step(c, 0.005f, 0.030f)
        c < 0.3125f -> 1f - SceneParts.step(c, 0.3000f, 0.0125f)
        c < 0.5800f -> SceneParts.step(c, 0.3125f, 0.025f)
        c < 0.5925f -> 1f - SceneParts.step(c, 0.5800f, 0.0125f)
        else -> SceneParts.step(c, 0.5925f, 0.025f)
    }

    /**
     * The slope of the CORRIDOR's own roof where the craft is, per node unit. Central difference on
     * the tour's trace function, at a twentieth of a node either side. The renderer asks the floor
     * stop of the rail for its readout, so the floor is this stop's own index whenever this line is
     * on screen.
     */
    private fun hereSlope(kit: SceneKit): Float {
        val at = floor(kit.progress)
        return (kit.traceHeight(at + 0.05f) - kit.traceHeight(at - 0.05f)) * 10f
    }

    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return when {
            c < 0.222f -> String.format(Locale.US, "CLIMBING   f′ %+.2f", roofD(beadX(c)))
            c < 0.3125f -> "FLAT 1 OF 3   f′ 0.00   HIGHEST"
            c < 0.492f -> String.format(Locale.US, "CLIMBING   f′ %+.2f", roofD(beadX(c)))
            c < 0.5925f -> String.format(Locale.US, "FLAT 2 OF 3   f′ 0.00   LOWER BY %.2f", KY[1] - KY[3])
            c < 0.732f -> String.format(Locale.US, "CLIMBING   f′ %+.2f", roofD(beadX(c)))
            c < 0.800f -> "FLAT 3 OF 3   f′ 0.00   NOT A TOP"
            c < 0.890f -> String.format(Locale.US, "ROLLED ON   f′ %+.2f", roofD(beadX(c)))
            c < 0.950f -> "THREE FLAT SPOTS · ONE READING"
            else -> String.format(Locale.US, "ROOF HERE  f′ %+.2f  NOT FLAT", hereSlope(kit))
        }
    }

    /** One trapezoid cell of a filled strip: two points on a baseline, two on the curve above it. */
    private fun cell(
        tri: FloatArray, at: Int,
        b0: FloatArray, b1: FloatArray, t1: FloatArray, t0: FloatArray,
        c0: FloatArray, c1: FloatArray, alpha: Float
    ): Int {
        var k = MathMesh.vertex(tri, at, b0[0], b0[1], b0[2], c0[0], c0[1], c0[2], alpha)
        k = MathMesh.vertex(tri, k, b1[0], b1[1], b1[2], c1[0], c1[1], c1[2], alpha)
        k = MathMesh.vertex(tri, k, t1[0], t1[1], t1[2], c1[0], c1[1], c1[2], alpha)
        k = MathMesh.vertex(tri, k, b0[0], b0[1], b0[2], c0[0], c0[1], c0[2], alpha)
        k = MathMesh.vertex(tri, k, t1[0], t1[1], t1[2], c1[0], c1[1], c1[2], alpha)
        k = MathMesh.vertex(tri, k, t0[0], t0[1], t0[2], c0[0], c0[1], c0[2], alpha)
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // A stop about the roof curve is meaningless on a tour that has no roof. Tours I, V and VI
        // would get a chart of a corridor that is not the one they are flying down.
        if (!kit.hasTrace) return

        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)
        SceneParts.vec(g, 1f, 0f, 0f, sAx)
        SceneParts.vec(g, 0f, 1f, 0f, uAx)

        val q = kit.quality
        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        var t = 0
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // 80 samples is about 690 line vertices and 960 triangle vertices for the whole board,
        // against 4096 and 1536. The fills go first when the governor bites; the curves never do.
        val ns = if (q == 0) 80 else if (q == 1) 44 else 30
        val fills = q < 2

        val chalk = SceneParts.CHALK
        val cool = SceneParts.COOL
        val hot = SceneParts.HOT
        val lamp = SceneParts.LAMP

        // --- the corridor the board is drawing: rail, and the ruling up to the roof --------------
        // Straight from SceneAmbientTrace's ruling, and for the same reason: a stripe with the
        // ground ruled up to it is a roof over a passage, and a stripe on its own is a scratch.
        board(-0.02f, 0f, pa)
        board(1.02f, 0f, pb)
        v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
            chalk[0], chalk[1], chalk[2], 0.42f)
        if (q == 0) {
            for (k in 0..12) {
                val x = k / 12f
                board(x, 0f, pa)
                board(x, roofY(x) * PW, pb)
                v = MathMesh.segment(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                    cool[0], cool[1], cool[2], 0.05f, 0.22f)
            }
        }

        // --- the ribbon's zero axis -------------------------------------------------------------
        board(-0.02f, DZ, pa)
        board(1.02f, DZ, pb)
        v = MathMesh.dashed(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
            if (q == 0) 22 else 11, chalk[0], chalk[1], chalk[2], 0.40f)

        // --- one pass for both curves and both fills ---------------------------------------------
        // The roof and the slope ribbon are the same sweep of x, so they share it: one march, four
        // things written, and roofY/roofD each evaluated once per sample instead of four times.
        board(0f, 0f, rb0)
        board(0f, roofY(0f) * PW, rf0)
        board(0f, DZ, ax0)
        board(0f, ribU(0f), rp0)
        var d0 = roofD(0f)
        for (k in 1..ns) {
            val x = k.toFloat() / ns
            val d1 = roofD(x)
            board(x, 0f, rb1)
            board(x, roofY(x) * PW, rf1)
            board(x, DZ, ax1)
            board(x, ribU(x), rp1)

            v = MathMesh.segment(line, v, rf0[0], rf0[1], rf0[2], rf1[0], rf1[1], rf1[2],
                hot[0], hot[1], hot[2], 1f)

            // The ribbon is coloured by the SIGN of the slope, per vertex, so the colour changes
            // exactly where it crosses. This is the tell Engineering says he should have used: at a
            // summit the ribbon arrives teal and leaves red, at a trough the other way round, and at
            // the shelf it never changes colour at all.
            val s0 = if (d0 >= 0f) SceneParts.ADDED else SceneParts.TAKEN
            val s1 = if (d1 >= 0f) SceneParts.ADDED else SceneParts.TAKEN
            v = MathMesh.vertex(line, v, rp0[0], rp0[1], rp0[2], s0[0], s0[1], s0[2], 0.95f)
            v = MathMesh.vertex(line, v, rp1[0], rp1[1], rp1[2], s1[0], s1[1], s1[2], 0.95f)

            if (fills) {
                // Faint under the roof, so the board reads as ground rather than as a wire; stronger
                // under the ribbon, where the sign of the shaded area is itself the information.
                t = cell(tri, t, rb0, rb1, rf1, rf0, hot, hot, 0.085f)
                t = cell(tri, t, ax0, ax1, rp1, rp0, s0, s1, 0.20f)
            }

            System.arraycopy(rb1, 0, rb0, 0, 3)
            System.arraycopy(rf1, 0, rf0, 0, 3)
            System.arraycopy(ax1, 0, ax0, 0, 3)
            System.arraycopy(rp1, 0, rp0, 0, 3)
            d0 = d1
        }

        // --- the three flat spots, struck one at a time and left standing ------------------------
        // Each one is the three instruments the script names, in one place: a ring on the roof, a
        // needle laid horizontally across it, and a plumb line down through the rail to the point
        // where the ribbon meets its axis. Once struck they stay for the rest of the loop, so the
        // long rest at the end has all three side by side and identical.
        for (j in 0 until 3) {
            val rev = SceneParts.step(c, REVEAL[j], 0.030f)
            if (rev < 0.02f) continue
            val x = KX[FLAT[j]]
            val ru = KY[FLAT[j]] * PW
            board(x, ru, pa)
            board(x, DZ, pb)
            v = MathMesh.dashed(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                if (q == 0) 14 else 7, chalk[0], chalk[1], chalk[2], 0.45f * rev)

            // The needle, double-stroked. Everything on the board goes out in one flushLines at one
            // width, so the only way to give the payoff object more weight than the ruling is to
            // draw it twice a hair apart.
            val len = NEEDLE * rev
            for (s in 0 until 2) {
                val off = if (s == 0) 0.006f else -0.006f
                pb[0] = pa[0] - sAx[0] * len + uAx[0] * off
                pb[1] = pa[1] - sAx[1] * len + uAx[1] * off
                pb[2] = pa[2] - sAx[2] * len + uAx[2] * off
                pc[0] = pa[0] + sAx[0] * len + uAx[0] * off
                pc[1] = pa[1] + sAx[1] * len + uAx[1] * off
                pc[2] = pa[2] + sAx[2] * len + uAx[2] * off
                v = MathMesh.segment(line, v, pb[0], pb[1], pb[2], pc[0], pc[1], pc[2],
                    lamp[0], lamp[1], lamp[2], rev)
            }

            v = MathMesh.arc(line, v, pa[0], pa[1], pa[2],
                sAx[0], sAx[1], sAx[2], uAx[0], uAx[1], uAx[2],
                RING * rev, 0f, TAU, if (q == 0) 14 else 8, lamp[0], lamp[1], lamp[2], 0.85f * rev)

            board(x, DZ, pb)
            v = MathMesh.arc(line, v, pb[0], pb[1], pb[2],
                sAx[0], sAx[1], sAx[2], uAx[0], uAx[1], uAx[2],
                DOT, 0f, TAU, if (q == 0) 12 else 8, hot[0], hot[1], hot[2], rev)
        }

        // --- "that second hill is plainly lower than the first" -----------------------------------
        // A level line carried across from the top of the first summit. The gap it opens above the
        // second one is the whole of Engineering's objection, and it is a length rather than a
        // caption, so it survives the viewer arriving with the audio already past that line.
        val lvl = SceneParts.step(c, 0.500f, 0.050f)
        if (lvl > 0.02f) {
            val top = KY[1] * PW
            board(KX[1], top, pa)
            board(KX[1] + (0.625f - KX[1]) * lvl, top, pb)
            v = MathMesh.dashed(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                if (q == 0) 18 else 9, SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], 0.60f)
            if (lvl > 0.6f) {
                board(KX[3], KY[3] * PW, pc)
                board(KX[3], top, pd)
                v = MathMesh.segment(line, v, pc[0], pc[1], pc[2], pd[0], pd[1], pd[2],
                    SceneParts.TAKEN[0], SceneParts.TAKEN[1], SceneParts.TAKEN[2], 0.90f)
            }
        }

        // --- the board's frame --------------------------------------------------------------------
        // It is what stops the chart reading as three unrelated glowing lines hanging in a corridor.
        if (q < 2) {
            board(-0.045f, DZ - 0.225f, pa)
            SceneParts.vec(g, 1.09f * PW, 0f, 0f, pb)
            SceneParts.vec(g, 0f, KY[6] * PW + 0.060f - (DZ - 0.225f), 0f, pc)
            v = SceneParts.edge(line, v, pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                pc[0], pc[1], pc[2], SceneParts.STEEL, 0.26f)
        }

        // Washes first, then the lines over them. flushTris draws with depth writes off, so nothing
        // it lays down can occlude or z-fight the curves and the ruling that follow it.
        if (fills) kit.flushTris(t)
        kit.flushLines(v, 2.2f)

        // --- the bead, and the needle it carries ---------------------------------------------------
        val show = beadShow(c)
        if (show > 0.02f) {
            val bx = beadX(c)
            val bd = roofD(bx)
            board(bx, roofY(bx) * PW, pa)
            // The live needle is a rod rather than a line: it is the one object on the board with
            // any mass to it, and the whole stop is people watching it come level.
            if (show > 0.6f) {
                val inv = NEEDLE / sqrt(1f + bd * bd)
                pb[0] = pa[0] - (sAx[0] + uAx[0] * bd) * inv
                pb[1] = pa[1] - (sAx[1] + uAx[1] * bd) * inv
                pb[2] = pa[2] - (sAx[2] + uAx[2] * bd) * inv
                pc[0] = pa[0] + (sAx[0] + uAx[0] * bd) * inv
                pc[1] = pa[1] + (sAx[1] + uAx[1] * bd) * inv
                pc[2] = pa[2] + (sAx[2] + uAx[2] * bd) * inv
                kit.rod(pb[0], pb[1], pb[2], pc[0], pc[1], pc[2], 0.026f, lamp, hot, 1.1f)
            }
            // Sat on the curve rather than centred in it, and brightening as the ground under it
            // flattens — so the bead is itself a third reading of the same quantity, and it is
            // burning hardest at exactly the moment it stops.
            pd[0] = pa[0] + uAx[0] * 0.048f
            pd[1] = pa[1] + uAx[1] * 0.048f
            pd[2] = pa[2] + uAx[2] * 0.048f
            val still = (1f - abs(bd) / DMAX).coerceIn(0f, 1f)
            kit.ball(
                pd[0], pd[1], pd[2], 0.062f, 0.062f, 0.062f, SceneParts.WORK, hot,
                show, glow = 0.35f + 1.5f * still * still + 0.5f * kit.beat, small = false
            )
        }

        // --- notation ---------------------------------------------------------------------------
        // Three glyphs, and all three NAME a line that is already drawn: the roof, the ribbon, and
        // the axis the ribbon keeps touching. The numbers are all on the HUD, where they can be read.
        board(0.030f, 0.170f, pa)
        kit.text("f", pa[0], pa[1], pa[2], 0.18f, hot, 0.95f, anchor = -0.5f)
        board(0.030f, DZ - 0.130f, pa)
        kit.text("f′", pa[0], pa[1], pa[2], 0.18f, cool, 0.95f, anchor = -0.5f)
        board(0.995f, DZ - 0.030f, pa)
        kit.text("f′ = 0", pa[0], pa[1], pa[2], 0.16f, chalk, 0.85f, anchor = 0.5f)

        // The three flat spots numbered, so the HUD's "FLAT 2 OF 3" has somewhere to point. Secondary
        // notation, so quality 0 only.
        if (q == 0) {
            for (j in 0 until 3) {
                val rev = SceneParts.step(c, REVEAL[j], 0.030f)
                if (rev < 0.4f) continue
                // Off the LEFT tip of each needle, not the right: the level line carried across from
                // the first summit runs rightwards at exactly the first needle's height, and a
                // numeral sitting on it is a numeral nobody can read.
                board(KX[FLAT[j]], KY[FLAT[j]] * PW, pa)
                pa[0] -= sAx[0] * (NEEDLE + 0.06f)
                pa[1] -= sAx[1] * (NEEDLE + 0.06f)
                pa[2] -= sAx[2] * (NEEDLE + 0.06f)
                kit.text(NUM[j], pa[0], pa[1], pa[2], 0.13f, lamp, rev * 0.9f,
                    GlyphBoard.Style.SMALL, anchor = 0.5f)
            }
        }
    }
}
