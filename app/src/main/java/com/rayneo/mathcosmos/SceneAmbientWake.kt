package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * TOUR III's AMBIENT — THE TRACE, and behind the craft, THE WAKE.
 *
 * Not a stop. This is drawn every frame for the whole of THE ACCUMULATION, wherever the craft
 * happens to be, and it carries the tour's one persistent idea: the area under the curve is not a
 * thing shown to you at a landmark, it is the stuff you have already flown through. So the roof
 * curve runs the whole length of the corridor, ahead and behind, and the sheet from the rail up to
 * that roof exists ONLY behind the craft. Ahead of the craft there is nothing at all. That
 * asymmetry is the entire content of the ambient: accumulation is what you have left behind, and
 * you cannot have accumulated what you have not yet passed.
 *
 * Three decisions worth writing down, because they all went the other way first.
 *
 * ONE SHEET, NOT A SLAB. The design document calls the wake "glowing volume", and a slab a couple
 * of units wide either side of the rail does look more impressive in stereo. It is also a quiet
 * lie: the integral of a single-variable function is an AREA, and a slab promises a double integral
 * that Tour V has not earned yet. So the wake is one sheet in the plane of the rail and the up
 * vector — floor to roof, zero thickness — given body by ribs rather than by width. The rail bends
 * side to side between stops, so the sheet snakes and a good stretch of it swings into view even
 * looking straight ahead; it is not the invisible edge-on plane you would fear.
 *
 * CONSTANT ALPHA PER UNIT OF AREA. It is tempting to make a deep wake more opaque, which makes the
 * picture prettier and the mathematics wrong. At a fixed alpha, twice the height is twice the glow,
 * and the eye adds it up the same way the integral does.
 *
 * NO NOTATION. Twelve stops each hang their own labels in this corridor for thirty-two minutes; an
 * ambient that also captions itself is thirty-two minutes of clutter. The number lives on the HUD,
 * in [readout], where a number can actually be read.
 *
 * The roof curve is written out here rather than delegated to SceneAmbientTrace. The two objects
 * draw the same thing and will drift apart eventually; a scene that could be silently broken by an
 * edit to a different tour's ambient is worse than fifteen duplicated lines.
 */
object SceneAmbientWake : MathScene {

    // The ambient is drawn unconditionally by the renderer, so neither of these is consulted.
    // They are set wide anyway: if this object is ever hung on a stop it should not be culled at
    // its own node while nine stops of wake are still behind the camera.
    override val reach = 99f
    override val deep = 99f

    /** How far back the sheet reaches, in node units. One trace period is about 8.4, so a full
     *  swing of the roof — up and under — is behind you at all times, which is what makes the
     *  gold and the blue comparable by eye at THE SIGNED WAKE. */
    private const val BACK = 9f

    /** How far ahead the roof is drawn. The roof exists whether or not you have flown under it. */
    private const val AHEAD = 5f

    private const val WAKE_ALPHA = 0.20f
    private const val TRACE_ALPHA = 0.95f
    private const val AXIS_ALPHA = 0.22f
    private const val RIB_ALPHA = 0.30f

    /** Ribs every half node unit, anchored to the world and not to the craft. */
    private const val RIB = 0.5f

    /** The settling wave that runs back along the sheet. Long, because it is scenery. */
    private const val WAVE_PERIOD = 22f
    private const val TAU = 6.2831855f

    private val f = FloatArray(12)
    private val rail = FloatArray(3)
    private val top = FloatArray(3)
    private val pRail = FloatArray(3)
    private val pTop = FloatArray(3)
    private val cut = FloatArray(3)
    private val ribA = FloatArray(3)
    private val ribB = FloatArray(3)

    /**
     * What the wake is worth, split by sign: the trace integrated along the rail from the oldest
     * sheet still drawn up to the craft. Gold above the rail, blue below, and the net between them
     * — which over a full swing of this tour's roof comes back to nearly nothing, and that is the
     * whole of THE SIGNED WAKE stated as two numbers.
     *
     * The units are rail units times world height, not metres: this is a quantity to WATCH rather
     * than to convert, and the crew talk about it that way.
     *
     * Note for whoever wires the HUD: the renderer currently asks the nearest STOP for its readout
     * and never asks the ambient, so this line does not appear yet. It is the number the wake would
     * report, computed the same way the sheet is drawn.
     */
    override fun readout(kit: SceneKit): String? {
        if (!kit.hasTrace) return null
        val last = (kit.stopCount - 1).toFloat()
        val p = kit.progress.coerceIn(0f, last)
        val lo = (p - BACK).coerceAtLeast(0f)
        val span = p - lo
        if (span < 0.05f) return "SWEPT 0.0"
        // Midpoint rule, 48 steps. Coarse, but it is measuring a picture, not proving a theorem.
        val n = 48
        val d = span / n
        var gold = 0f
        var blue = 0f
        for (k in 0 until n) {
            val h = kit.traceHeight(lo + (k + 0.5f) * d)
            if (h >= 0f) gold += h * d else blue -= h * d
        }
        val net = gold - blue
        return "SWEPT +${tenths(gold)}  -${tenths(blue)}   NET ${if (net < 0f) "-" else "+"}${tenths(abs(net))}"
    }

    /** One decimal without a formatter. [v] is never negative by the time it gets here. */
    private fun tenths(v: Float): String {
        val t = (v * 10f + 0.5f).toInt()
        return "${t / 10}.${t % 10}"
    }

    /** Amber above the rail, cold blue below it. The sign change is a stop of its own. */
    private fun signCol(h: Float): FloatArray = if (h >= 0f) SceneParts.WORK else SceneParts.COOL

    private fun lerp3(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
        out[0] = a[0] + (b[0] - a[0]) * t
        out[1] = a[1] + (b[1] - a[1]) * t
        out[2] = a[2] + (b[2] - a[2]) * t
    }

    /**
     * One step of the sheet: the trapezoid rail-old, rail-new, trace-new, trace-old.
     *
     * [MathMesh.quad] cannot do this — it builds a parallelogram, and the two ends of a wake step
     * are only the same height where the roof is flat. Two triangles written by hand instead, with
     * the alpha carried per end so the far end of the wake can fade out rather than stop dead.
     */
    private fun panel(
        out: FloatArray, at: Int,
        r0: FloatArray, t0: FloatArray, r1: FloatArray, t1: FloatArray,
        c: FloatArray, a0: Float, a1: Float
    ): Int {
        if ((at + 6) * MathMesh.STRIDE > out.size) return at
        var k = MathMesh.vertex(out, at, r0[0], r0[1], r0[2], c[0], c[1], c[2], a0)
        k = MathMesh.vertex(out, k, r1[0], r1[1], r1[2], c[0], c[1], c[2], a1)
        k = MathMesh.vertex(out, k, t1[0], t1[1], t1[2], c[0], c[1], c[2], a1)
        k = MathMesh.vertex(out, k, r0[0], r0[1], r0[2], c[0], c[1], c[2], a0)
        k = MathMesh.vertex(out, k, t1[0], t1[1], t1[2], c[0], c[1], c[2], a1)
        k = MathMesh.vertex(out, k, t0[0], t0[1], t0[2], c[0], c[1], c[2], a0)
        return k
    }

    /** Half a step, where the roof crosses the rail: a triangle that comes to a point at the cross. */
    private fun wedge(
        out: FloatArray, at: Int,
        r: FloatArray, t: FloatArray, x: FloatArray,
        c: FloatArray, a: Float
    ): Int {
        if ((at + 3) * MathMesh.STRIDE > out.size) return at
        var k = MathMesh.vertex(out, at, r[0], r[1], r[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(out, k, x[0], x[1], x[2], c[0], c[1], c[2], a)
        k = MathMesh.vertex(out, k, t[0], t[1], t[2], c[0], c[1], c[2], a)
        return k
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        if (!kit.hasTrace) return

        val last = (kit.stopCount - 1).toFloat()
        val p = kit.progress.coerceIn(0f, last)
        val lo = (p - BACK).coerceAtLeast(0f)
        val hi = (p + AHEAD).coerceAtMost(last)
        val back = p - lo
        val ahead = hi - p
        if (hi - lo < 0.05f) return

        // The sample grid rides with the craft. The roof is smooth enough at this spacing that the
        // points sliding along it is invisible; the ribs are the one thing that would give the slide
        // away, so those are pinned to the world below instead.
        val nBack = when (kit.quality) { 0 -> 36; 1 -> 18; else -> 12 }
        val nAhead = when (kit.quality) { 0 -> 20; 1 -> 10; else -> 6 }
        val dpB = if (back > 1e-3f) back / nBack else 0f
        val dpA = if (ahead > 1e-3f) ahead / nAhead else 0f

        val line = kit.lineBuf
        val tri = kit.triBuf
        var lv = 0
        var tv = 0

        val ph = SceneParts.cycle(kit.seconds, WAVE_PERIOD) * TAU
        val ribs = kit.quality == 0
        val axis = kit.quality < 2

        // Nothing swept yet at the mouth of the tour: skip the whole back half rather than grind
        // through thirty-six degenerate quads sitting on top of one another.
        val kStart = if (dpB > 0f) 0 else nBack
        val kEnd = nBack + if (dpA > 0f) nAhead else 0

        var prevH = 0f
        var prevA = 0f
        var prevQ = lo
        var have = false

        for (k in kStart..kEnd) {
            val q = if (k <= nBack) lo + dpB * k else p + dpA * (k - nBack)

            // A frame per sample rather than one SceneParts.stage: the wake follows nine node
            // units of a rail that swings from side to side between stops, so there is no single
            // plane to hang it in. Up is taken from the frame, never assumed to be world up.
            kit.frame(q, f)
            rail[0] = f[0]; rail[1] = f[1]; rail[2] = f[2]
            // The roof is drawn exactly where the tour's own function puts it, even where that
            // grazes the passage wall around the second stop. The corridor's roof IS the function;
            // clamping it to stay indoors would be drawing a different function.
            val h = kit.traceHeight(q)
            top[0] = rail[0] + f[9] * h
            top[1] = rail[1] + f[10] * h
            top[2] = rail[2] + f[11] * h

            // How faded this part of the sheet is: young at the craft, thinning away at the far
            // end so the oldest wake dissolves instead of ending in a wall.
            val ageU = if (back > 1e-3f) ((q - lo) / back).coerceIn(0f, 1f) else 1f
            val wave = 0.82f + 0.18f * sin(q * 2.2f - ph)
            val aW = WAKE_ALPHA * (ageU / 0.28f).coerceAtMost(1f) * wave

            if (have) {
                // --- the roof curve ----------------------------------------------------------
                val u0 = (prevQ - lo) / (hi - lo)
                val u1 = (q - lo) / (hi - lo)
                lv = MathMesh.segment(
                    line, lv, pTop[0], pTop[1], pTop[2], top[0], top[1], top[2],
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2],
                    TRACE_ALPHA * MathMesh.taper(u0), TRACE_ALPHA * MathMesh.taper(u1)
                )

                if (k <= nBack) {
                    // --- the sheet -----------------------------------------------------------
                    if (prevH * h < 0f) {
                        // The roof passes through the rail inside this step. Split it there, so
                        // that no single triangle is half amber and half blue and the crossing is
                        // a point rather than a smear.
                        val t = prevH / (prevH - h)
                        lerp3(pRail, rail, t, cut)
                        val am = (prevA + aW) * 0.5f
                        tv = wedge(tri, tv, pRail, pTop, cut, signCol(prevH), (prevA + am) * 0.5f)
                        tv = wedge(tri, tv, rail, top, cut, signCol(h), (aW + am) * 0.5f)
                    } else {
                        // Coloured by the sum of the two ends, not by one of them: a step that
                        // starts exactly on the rail and goes down is a blue step, and h alone
                        // would call the flat end amber.
                        tv = panel(tri, tv, pRail, pTop, rail, top, signCol(prevH + h), prevA, aW)
                    }

                    // --- the axis the sign is measured from ------------------------------------
                    // Without a drawn rail behind the craft, "the roof dipped below us" is a claim
                    // about an invisible line. This is the line.
                    if (axis) {
                        lv = MathMesh.segment(
                            line, lv, pRail[0], pRail[1], pRail[2], rail[0], rail[1], rail[2],
                            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2],
                            AXIS_ALPHA * prevA / WAKE_ALPHA, AXIS_ALPHA * aW / WAKE_ALPHA
                        )
                    }

                    // --- ribs, pinned to whole half-units of rail -----------------------------
                    // A rib every half unit, standing still while the craft moves past it, so the
                    // sheet reads as something laid down rather than something towed. They are also
                    // the slabs of the next stop, drawn faintly a leg early.
                    if (ribs && q > prevQ) {
                        val m = floor(q / RIB) * RIB
                        if (m > prevQ && m <= q) {
                            val t = (m - prevQ) / (q - prevQ)
                            lerp3(pRail, rail, t, ribA)
                            lerp3(pTop, top, t, ribB)
                            val hm = prevH + (h - prevH) * t
                            val cr = signCol(hm)
                            lv = MathMesh.segment(
                                line, lv, ribA[0], ribA[1], ribA[2], ribB[0], ribB[1], ribB[2],
                                cr[0], cr[1], cr[2], RIB_ALPHA * prevA / WAKE_ALPHA
                            )
                        }
                    }
                }
            }

            // --- the emitter: the edge being laid down right now ---------------------------
            // The one bright line in the ambient, and it sits exactly at the craft, because that
            // is where the sweeping is happening. It answers the sound cues.
            if (k == nBack && back > 1e-3f) {
                val hot = 0.55f + 0.35f * kit.beat
                lv = MathMesh.segment(
                    line, lv, rail[0], rail[1], rail[2], top[0], top[1], top[2],
                    SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], hot, hot * 0.6f
                )
            }

            pRail[0] = rail[0]; pRail[1] = rail[1]; pRail[2] = rail[2]
            pTop[0] = top[0]; pTop[1] = top[1]; pTop[2] = top[2]
            prevH = h
            prevA = aW
            prevQ = q
            have = true
        }

        kit.flushTris(tv)
        kit.flushLines(lv, 2.0f)
    }
}
