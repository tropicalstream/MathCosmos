package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Tour IV, stop 10 — THE WAVE FROM POWERS. "A wave can be built out of nothing but odd powers,
 * if you use enough of them."
 *
 * The corridor's roof is a sine, and this stop builds it out of x, x³, x⁵, x⁷, x⁹ — five ribbons
 * hung under the roof, arriving one at a time, each hugging a longer stretch of the wave than the
 * one before and then flinging off. The craft arrives exactly where the ninth-order ribbon gives
 * up, which is the whole point of siting the landmark here: the failure is as instructive as the
 * success, and you fly through it.
 *
 * WHERE x = 0 IS, AND WHY IT IS FIVE STOPS ASTERN. The series in odd powers only is the expansion
 * about a point where the wave crosses its mean, so the anchor has to be a crossing — and the
 * roof's period is 7.85 node units, about 125 metres of corridor, which is most of the leg. The
 * crossing nearest this stop that leaves any room at all for the high orders to fail before the
 * rail runs out is the one at p = π/W, back at stop four. So the picture is a long one: it is made
 * behind you, and you catch up with it. Order one gave up back at stop five, order three at stop
 * six, five at seven, seven at eight, and nine — the hero — holds through a whole trough and most
 * of a crest and lets go at p = 9.1, a tenth of a stop ahead of this landmark's own node. That
 * marching-outward line of departure marks IS the stop; nothing else has to be explained.
 *
 * WHAT IS MEASURED AND WHAT IS DUPLICATED. The roof is drawn by the ambient trace, not by this
 * scene, so the ribbons have to agree with a curve someone else is drawing. Rather than write the
 * trace's constants down a second time, the scene measures them: the height at the anchor is read
 * straight from [SceneKit.traceHeight], and the amplitude comes from a central difference of the
 * same function, so the order-one ribbon is EXACTLY the tangent to the roof that is on screen.
 * Only the angular rate W is duplicated from TourMap, because it is what fixes where the crossing
 * is. If the trace is ever changed to something that is not this sine, the family degrades
 * gracefully — it stays a Taylor fan matched to the roof in value and slope — but the anchor will
 * want moving.
 *
 * THE FAN IS A DRAWING CONVENTION. Near the anchor all five ribbons agree with the roof and with
 * each other to well under a millimetre, and five coincident curves are one curve with four times
 * the overdraw. So they are spread a hand's width apart across the corridor, ordered by how well
 * they do: order nine nearest the roof, order one furthest out. That spread is not part of the
 * mathematics and the crew says so; what IS part of the mathematics is that consecutive orders
 * fling off in OPPOSITE directions, because the terms alternate in sign and each partial sum
 * overshoots the one before. The truth is bracketed between them, which is worth seeing and costs
 * nothing extra to show. On this leg, where the anchor is a FALLING crossing, that comes out as
 * 1, 5 and 9 diving for the floor and 3 and 7 climbing out through the roof; at a rising crossing
 * it would be the other way about, and the code does not care which, because the sign arrives with
 * the measured slope rather than being written in.
 *
 * ONE HONEST LINE. Nothing here diverges. The sine series converges for every x there is; the
 * previous stop's edge of the world does not apply to it. What fails is stopping early, and the
 * "moment of departure" is a threshold this scene picked — fifteen per cent of the wave's height —
 * not an event in the mathematics. The same threshold stops the geometry and feeds the readout,
 * so the number on the HUD is the number you are looking at.
 */
object SceneWaveFromPowers : MathScene {

    /** The figure is made five stops astern and dies out a stop ahead, so it wants a long lead. */
    override val reach = 2.6f
    override val deep = 1.3f

    // ------------------------------------------------------------------ the wave

    /** The trace's angular rate per node unit. Mirrors TourMap.INFINITE's trace; see the header. */
    private const val W = 0.80f

    /**
     * The spacing of the roof's mean crossings, π / W. Only a crossing will do for x = 0: the
     * expansion in odd powers ONLY exists about a point where the wave passes its mean, which is
     * why this scene cannot simply be anchored at its own node.
     */
    private const val CROSS = 3.9269908f

    /** Half-width of the central difference that measures the wave's amplitude off the roof. */
    private const val PROBE = 0.20f

    private const val N = 5
    private val ORDERS = intArrayOf(1, 3, 5, 7, 9)

    /** Departure and death, as fractions of the wave's own amplitude. */
    private const val EPS_DEP = 0.15f
    private const val EPS_GONE = 1.10f

    /** Most of the passage radius a fleeing ribbon may use before it is cut. */
    private const val CEIL = 0.85f

    // ------------------------------------------------------------------ staging

    private const val PERIOD = 28f
    private const val T0 = 0.02f           // order 1 starts here
    private const val GAP = 0.13f          // and each order follows this far behind the last
    private const val GROW = 0.11f         // how long one ribbon takes to run out from the anchor
    private const val REST = 0.94f         // the finished fan stands from 0.65 to here: eight seconds

    private const val SIDE_NEAR = -0.16f   // where order 9 hangs, just off the roof's own plane
    private const val FAN = -0.13f         // and how far out each poorer order is set from it
    private const val LABEL_OFF = -0.34f
    private const val MARK = 0.055f
    private const val GLYPH = 0.20f
    private const val ASTERN = -2.5f       // no dimming until this far behind the craft
    private const val TAIL = 5.5f          // and gone this much further back again

    private val TINT = arrayOf(
        SceneParts.STEEL, SceneParts.COOL, SceneParts.ADDED, SceneParts.WORK, SceneParts.HOT
    )

    /** What each ribbon cost. The term is the label, because the term is what bought the stretch. */
    private val TERM = arrayOf("x", "− x^3/3!", "+ x^5/5!", "− x^7/7!", "+ x^9/9!")

    // Filled once, by scanning the series itself: where each order first parts company with the
    // sine, and where it has gone so far wrong that drawing it is no longer telling the truth
    // about anything. Constants of the mathematics, not of the corridor, so they can be found
    // here rather than measured every frame — and the readout quotes the same numbers.
    private val T_DEP = FloatArray(N)
    private val T_GONE = FloatArray(N)
    private val READ = Array(N) { "" }

    // ------------------------------------------------------------------ scratch

    private val fA = FloatArray(12)
    private val fB = FloatArray(12)
    private val fr = FloatArray(9)         // the blended centre, side and up at one sample
    private val pt = FloatArray(3)
    private val prev = FloatArray(N * 3)
    private val prevA = FloatArray(N)
    private val front = FloatArray(N)      // how far each ribbon has grown, in x
    private val alpha = FloatArray(N)
    private val live = BooleanArray(N)
    private val marked = BooleanArray(N)
    private val markPt = FloatArray(N * 3)
    private val markSide = FloatArray(N * 3)
    private val anchorPt = FloatArray(3)
    private val anchorSide = FloatArray(3)

    init {
        for (k in 0 until N) {
            val ord = ORDERS[k]
            var t = 0f
            var dep = 0f
            var gone = 0f
            while (t < 8f) {
                val e = abs(poly(t, ord) - sin(t))
                if (dep == 0f && e > EPS_DEP) dep = t
                if (e > EPS_GONE) { gone = t; break }
                t += 0.002f
            }
            T_DEP[k] = dep
            T_GONE[k] = if (gone > 0f) gone else 8f
            READ[k] = "ORDER $ord   TRUE TO x = " + oneDp(dep)
        }
    }

    /** The odd-power partial sum through [order]: x − x³/3! + x⁵/5! − … Built term from term. */
    private fun poly(t: Float, order: Int): Float {
        var term = t
        var s = t
        var sign = -1f
        var k = 3
        while (k <= order) {
            term = term * t * t / ((k - 1) * k)
            s += sign * term
            sign = -sign
            k += 2
        }
        return s
    }

    /** One decimal, without a formatter. Called once per order at class load, never in a frame. */
    private fun oneDp(v: Float): String {
        val n = (v * 10f + 0.5f).toInt()
        return "${n / 10}.${n % 10}"
    }

    /** Where ribbon [k] hangs across the corridor: best nearest the roof, worst furthest out. */
    private fun sideOf(k: Int): Float = SIDE_NEAR + (N - 1 - k) * FAN

    /**
     * Which crossing of the roof this stop's figure is built from.
     *
     * Not a constant, because the thing that actually matters is where order nine LETS GO: that
     * event is the landmark, and it should happen alongside the node the landmark is hung on. So
     * the anchor is the crossing nearest to one order-nine departure back up the corridor. On the
     * tour as written that comes out at p = π/W, five stops astern of stop ten; move the stop, or
     * change the trace's rate, and the figure follows instead of sliding off its own node.
     */
    private fun anchorFor(i: Int): Float {
        val want = i - T_DEP[N - 1] / W
        val m = kotlin.math.round(want / CROSS).coerceAtLeast(1f)
        return m * CROSS
    }

    /**
     * Bright everywhere in front and alongside, dying out a long way astern. The figure is six
     * node units long and most of it is behind the craft by the time the stop is alongside; left
     * at full strength the tail is a bright scratch across the rear of the eye that says nothing.
     */
    private fun weight(d: Float): Float =
        if (d >= ASTERN) 1f else (1f - (ASTERN - d) / TAIL).coerceIn(0f, 1f)

    // ------------------------------------------------------------------ the frame

    /**
     * The rail frame at a sample, blended from the two whole-node stations either side of it.
     * The rail turns by a couple of degrees per node unit, so a linear blend between stations a
     * unit apart is exact to far less than a pixel, and it keeps this scene to nine frame queries
     * instead of two hundred and fifty. Same reasoning as the ambient trace, same numbers.
     */
    private fun blend(u: Float) {
        for (j in 0..2) {
            fr[j] = fA[j] + (fB[j] - fA[j]) * u
            fr[3 + j] = fA[6 + j] + (fB[6 + j] - fA[6 + j]) * u
            fr[6 + j] = fA[9 + j] + (fB[9 + j] - fA[9 + j]) * u
        }
    }

    /** A point [side] across the corridor and [up] above the rail, in the blended frame. */
    private fun place(side: Float, up: Float, out: FloatArray) {
        out[0] = fr[0] + fr[3] * side + fr[6] * up
        out[1] = fr[1] + fr[4] * side + fr[7] * up
        out[2] = fr[2] + fr[5] * side + fr[8] * up
    }

    // ------------------------------------------------------------------ the readout

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        var k = 0
        for (j in 0 until N) if (c >= T0 + j * GAP) k = j
        return READ[k]
    }

    // ------------------------------------------------------------------ the landmark

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No roof, no wave. Tours I, V and VI would hand back zero everywhere and this would draw
        // five straight lines down the middle of the corridor.
        if (!kit.hasTrace) return
        val last = kit.stopCount - 1
        val anchor = anchorFor(i)
        if (anchor < 0.5f || anchor > last - 1.5f) return

        val q = kit.quality
        val h0 = kit.traceHeight(anchor)
        // Amplitude by central difference: the roof's slope at the crossing is A·W, so A falls out.
        // Signed, so a crossing where the wave is falling gives a fan that falls with it and the
        // odd powers keep their own signs rather than being special-cased.
        val amp = (kit.traceHeight(anchor + PROBE) - kit.traceHeight(anchor - PROBE)) / (2f * PROBE * W)
        if (abs(amp) < 0.05f) return

        val cyc = SceneParts.cycle(kit.seconds, PERIOD)
        val fanOut = 1f - SceneParts.step(cyc, REST, 0.05f)
        if (fanOut <= 0.01f) return

        val stepP = if (q == 0) 0.09f else if (q == 1) 0.15f else 0.22f
        val kStep = if (q >= 2) 2 else 1        // at q2 only orders 1, 5 and 9 are built
        val pEnd = min(anchor + T_GONE[N - 1] / W + 0.02f, last - 0.02f)
        if (pEnd < anchor + 0.5f) return

        for (k in 0 until N) {
            front[k] = SceneParts.step(cyc, T0 + k * GAP, GROW) * T_GONE[k]
            // A ribbon is brightest as it arrives and then settles back, and a better order sits
            // brighter than a worse one for good: the fan should read as a ranking at a glance.
            val fresh = 1f - SceneParts.step(cyc, T0 + k * GAP + GROW, 0.10f)
            alpha[k] = min(1f, 0.55f + 0.10f * k + 0.30f * fresh)
            live[k] = true
            marked[k] = false
        }

        val line = kit.lineBuf
        val cap = min(kit.lineCapacity, 1800)
        val prog = kit.progress
        var v = 0

        // --- the anchor sample, which every ribbon starts from --------------------------------
        var s = floor(anchor).toInt()
        kit.frame(s.toFloat(), fA)
        kit.frame((s + 1).toFloat(), fB)
        blend(anchor - s)
        place(0f, h0, anchorPt)
        anchorSide[0] = fr[3]; anchorSide[1] = fr[4]; anchorSide[2] = fr[5]
        val seedA = weight(anchor - prog) * fanOut
        var k0 = 0
        while (k0 < N) {
            place(sideOf(k0), h0, pt)
            prev[k0 * 3] = pt[0]; prev[k0 * 3 + 1] = pt[1]; prev[k0 * 3 + 2] = pt[2]
            prevA[k0] = alpha[k0] * seedA
            k0 += kStep
        }

        // --- the sweep -------------------------------------------------------------------------
        // One pass down the corridor building all five ribbons at once. Five separate passes would
        // be five times the frame queries for the same picture.
        var p = anchor
        while (p < pEnd && v + 12 <= cap) {
            p = min(p + stepP, pEnd)
            while (p > s + 1 && s + 2 <= last) {
                System.arraycopy(fB, 0, fA, 0, 12)
                s++
                kit.frame((s + 1).toFloat(), fB)
            }
            blend(p - s)
            val t = W * (p - anchor)
            val truth = sin(t)
            val wgt = weight(p - prog) * fanOut
            val lid = CEIL * kit.radius(p)

            // A plain while rather than a stepped for, so nothing is built per frame whatever the
            // compiler decides to do with a progression.
            var k = 0
            while (k < N) {
                if (!live[k]) { k += kStep; continue }
                // Beyond its growth front the ribbon does not exist yet; t only ever increases,
                // so it is finished for this pass.
                if (t > front[k]) { live[k] = false; k += kStep; continue }
                val pk = poly(t, ORDERS[k])
                val e = abs(pk - truth)
                val h = h0 + amp * pk
                // Dead either from being wrong, or from having left the passage. Both are the same
                // event as far as a viewer is concerned: it flung off and it is gone.
                if (e > EPS_GONE || abs(h) > lid) { live[k] = false; k += kStep; continue }
                place(sideOf(k), h, pt)
                val j = k * 3
                if (wgt > 0.02f) {
                    // Past its departure the ribbon bleeds toward the colour of a debt and thins
                    // out, so the last stretch reads as a failure and not as more curve.
                    val u = ((e - EPS_DEP) / (EPS_GONE - EPS_DEP)).coerceIn(0f, 1f)
                    val c = TINT[k]
                    val cr = c[0] + (SceneParts.TAKEN[0] - c[0]) * u
                    val cg = c[1] + (SceneParts.TAKEN[1] - c[1]) * u
                    val cb = c[2] + (SceneParts.TAKEN[2] - c[2]) * u
                    val a = alpha[k] * wgt * (1f - 0.55f * u * u)
                    v = MathMesh.segment(
                        line, v, prev[j], prev[j + 1], prev[j + 2], pt[0], pt[1], pt[2],
                        cr, cg, cb, prevA[k], a
                    )
                    prevA[k] = a
                } else {
                    prevA[k] = 0f
                }
                prev[j] = pt[0]; prev[j + 1] = pt[1]; prev[j + 2] = pt[2]
                // The moment of departure, caught on the way past with the frame already blended.
                // Storing it here is what keeps the markers and their labels free of frame queries.
                if (!marked[k] && t >= T_DEP[k]) {
                    marked[k] = true
                    markPt[j] = pt[0]; markPt[j + 1] = pt[1]; markPt[j + 2] = pt[2]
                    markSide[j] = fr[3]; markSide[j + 1] = fr[4]; markSide[j + 2] = fr[5]
                }
                k += kStep
            }
        }
        kit.flushLines(v, if (q == 0) 2.4f else 2.8f)

        // --- where each one let go ------------------------------------------------------------
        // Billboarded diamonds, all five in one triangle call. They are the readable part of this
        // stop: five marks marching away down the corridor, one per order, each further off.
        if (q < 2) {
            val tri = kit.triBuf
            var tvn = 0
            var k = 0
            while (k < N) {
                val j = k * 3
                if (marked[k]) {
                    val a = 0.92f * fanOut * weight(anchor + T_DEP[k] / W - prog)
                    if (a > 0.03f) {
                        val c = TINT[k]
                        val ux = (kit.camRightX + kit.camUpX) * MARK
                        val uy = (kit.camRightY + kit.camUpY) * MARK
                        val uz = (kit.camRightZ + kit.camUpZ) * MARK
                        val vx = (kit.camRightX - kit.camUpX) * MARK
                        val vy = (kit.camRightY - kit.camUpY) * MARK
                        val vz = (kit.camRightZ - kit.camUpZ) * MARK
                        tvn = MathMesh.quad(
                            tri, tvn,
                            markPt[j] - kit.camRightX * MARK,
                            markPt[j + 1] - kit.camRightY * MARK,
                            markPt[j + 2] - kit.camRightZ * MARK,
                            ux, uy, uz, vx, vy, vz, c[0], c[1], c[2], a
                        )
                    }
                }
                k += kStep
            }
            if (tvn > 0) kit.flushTris(tvn)
        }

        // --- x = 0, and the front of whichever ribbon is being run out --------------------------
        val anchorA = weight(anchor - prog) * fanOut
        if (anchorA > 0.04f) {
            kit.ball(
                anchorPt[0], anchorPt[1], anchorPt[2], 0.055f, 0.055f, 0.055f,
                SceneParts.HOT, SceneParts.LAMP, anchorA, glow = 1.8f + 1.4f * kit.beat
            )
        }
        if (q < 2) {
            var kGrow = -1
            var k = 0
            while (k < N) {
                if (front[k] > 0.02f && front[k] < T_GONE[k] * 0.98f) kGrow = k
                k += kStep
            }
            if (kGrow >= 0) {
                val j = kGrow * 3
                kit.ball(
                    prev[j], prev[j + 1], prev[j + 2], 0.045f, 0.045f, 0.045f,
                    SceneParts.HOT, TINT[kGrow], 0.95f, glow = 2.6f
                )
            }
        }

        // --- notation ---------------------------------------------------------------------------
        // Each term is written beside the mark its ribbon reached, further out across the corridor
        // than the fan itself: never above and never below, because the telemetry owns the top of
        // the eye and the caption box the bottom, and this figure already runs along the roof.
        if (q < 2) {
            var k = 0
            while (k < N) {
                val j = k * 3
                if (marked[k]) {
                    val a = 0.95f * fanOut * weight(anchor + T_DEP[k] / W - prog)
                    if (a > 0.05f) {
                        kit.text(
                            TERM[k],
                            markPt[j] + markSide[j] * LABEL_OFF,
                            markPt[j + 1] + markSide[j + 1] * LABEL_OFF,
                            markPt[j + 2] + markSide[j + 2] * LABEL_OFF,
                            GLYPH, TINT[k], a
                        )
                    }
                }
                k += kStep
            }
        }
        if (q == 0 && anchorA > 0.1f) {
            kit.text(
                "sin x",
                anchorPt[0] + anchorSide[0] * LABEL_OFF,
                anchorPt[1] + anchorSide[1] * LABEL_OFF,
                anchorPt[2] + anchorSide[2] * LABEL_OFF,
                GLYPH * 1.15f, SceneParts.HOT, anchorA
            )
        }
    }
}
