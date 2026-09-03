package com.rayneo.mathcosmos

/**
 * Stop 4 of THE INFINITE — THE SLOW CLIMB. "Terms that shrink to nothing can still add up to
 * everything."
 *
 * The harmonic tower from stop 2 comes back, and this time it is bracketed. A row of bricks runs
 * away down the corridor, brick k the height of 1/k, and under each group of terms — {1/2},
 * {1/3, 1/4}, {1/5..1/8}, {1/9..1/16} — a floor is laid at the height of the SHORTEST brick in
 * that group. Every brick in the group stands at or above its floor, and there are exactly enough
 * bricks that the floor's own area is half a unit block. So each bracket, however far down the
 * corridor it is and however flat it has become, is worth at least a half; a copy of it flies back
 * to a tally column beside the ship and stacks. The column climbs at a constant rate for ever.
 *
 * The one design decision everything else follows from is that the floor is COPIED, not moved. A
 * pour would say the tower is being dismantled to build the column, which is the wrong story: the
 * tower is untouched and the column is a tally of what was found underneath it. Keeping the floor
 * lit in place after its copy has flown is what makes that readable without anyone saying it.
 *
 * The area is held exactly constant through the flight — the block's width is interpolated and its
 * height is then area/width — so the wide flat floor and the narrow tall block in the column are
 * visibly the same amount of stuff. That is the whole proof, and it is the reason this stop can be
 * looked at rather than followed.
 *
 * Scale is taken from the tour's own roof: kit.traceHeight fixes the unit brick so the drawn part
 * of the column stands about two thirds of the way to the ceiling, and the dashed rungs above it —
 * the groups we did not have corridor enough to draw, out to n = 2^8 — carry on through the roof
 * and out of the passage. The design brief asks for the roof to be raised twice to fit the halves;
 * the roof belongs to the ambient scene and cannot move, so the picture makes the honest version of
 * the same point instead: there is no ceiling this column stays under.
 *
 * Sixteen terms only, so the bricks stay countable. Beyond that the argument is a promise, and the
 * ghost rungs and the HUD readout are careful to be drawn and worded as one.
 */
object SceneSlowClimb : MathScene {

    override val reach = 1.5f
    // The tower runs about three world units past the stop; without this the whole landmark is
    // culled at its own node while half of it is still ahead of the viewer.
    override val deep = 0.3f

    private const val PERIOD = 28f

    private const val SIDE = -1.4f         // the figure hangs to port; the rail stays clear
    private const val BASE_U = -1.15f      // the baseline, below the rail, so the column climbs into view
    private const val W = 0.34f            // one term's width along the corridor
    private const val TOWER_A = -2.2f      // the tower starts behind the stop and runs ahead
    private const val STACK_A = TOWER_A - 1.25f   // the tally column, just outboard of the tower

    private const val AT0 = 0.10f          // when the first group is bracketed
    private const val DG = 0.115f          // one group's slice of the cycle

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val tv = IntArray(1)

    /** The unit brick's height, re-derived each frame from the roof. */
    private var unitH = 0.8f

    // Every string this scene can ever hang, so nothing is built in a draw. The second group is
    // the only one whose terms MEET the half rather than beating it, and it says so.
    private val BRACKET = arrayOf("", "= 1/2", "≥ 1/2", "≥ 1/2", "≥ 1/2")
    private val NN = arrayOf("1", "2", "4", "8", "16")
    private val HARM = arrayOf("1.00", "1.50", "2.08", "2.72", "3.38")
    private val BOUND = arrayOf("1.0", "1.5", "2.0", "2.5", "3.0")

    /** The first and last term of group [gi]: {1}, {2}, {3,4}, {5..8}, {9..16}. */
    private fun groupLo(gi: Int) = if (gi == 0) 1 else (1 shl (gi - 1)) + 1
    private fun groupHi(gi: Int) = 1 shl gi

    /** Where group [gi]'s block sits in the column, and how tall it is. */
    private fun blockBase(gi: Int) = BASE_U + (if (gi == 0) 0f else unitH * (0.5f + gi * 0.5f))
    private fun blockH(gi: Int) = if (gi == 0) unitH else unitH * 0.5f

    private fun groups(kit: SceneKit) = if (kit.quality == 0) 5 else 4

    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val ng = groups(kit)
        var landed = 0
        for (gi in 0 until ng) {
            if (SceneParts.step(c, AT0 + gi * DG + DG * 0.55f, DG * 0.42f) > 0.99f) landed++
        }
        if (landed == 0) return "n = 1   Σ = 1.00"
        // Once the ghost rungs are up the claim is about groups we have not drawn, and the number
        // says so plainly rather than pretending sixteen bricks got us there.
        if (kit.quality == 0 && SceneParts.step(c, AT0 + ng * DG, 0.10f) > 0.5f) {
            return "n = 256   Σ = 6.12 ≥ 5.0"
        }
        val k = landed - 1
        return "n = ${NN[k]}   Σ = ${HARM[k]} ≥ ${BOUND[k]}"
    }

    /** A rectangle of the figure: [a] along the rail, [u] above it, [lift] out of the plane. */
    private fun rect(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray,
        a: Float, u: Float, wa: Float, wu: Float,
        c: FloatArray, alpha: Float, lift: Float = 0f
    ): Int {
        SceneParts.at(g, lift, u, a, o)
        SceneParts.vec(g, 0f, 0f, wa, du)
        SceneParts.vec(g, 0f, wu, 0f, dv)
        return SceneParts.pane(
            kit, line, lv, tri, tv, o[0], o[1], o[2],
            du[0], du[1], du[2], dv[0], dv[1], dv[2], c, alpha
        )
    }

    /** One segment in figure coordinates. */
    private fun seg(
        line: FloatArray, v: Int, a0: Float, u0: Float, a1: Float, u1: Float,
        c: FloatArray, alpha: Float
    ): Int {
        SceneParts.at(g, 0f, u0, a0, o)
        SceneParts.at(g, 0f, u1, a1, p)
        return MathMesh.segment(line, v, o[0], o[1], o[2], p[0], p[1], p[2], c[0], c[1], c[2], alpha)
    }

    /** A dashed run in figure coordinates — a marker, never a solid part of the figure. */
    private fun dash(
        line: FloatArray, v: Int, a0: Float, u0: Float, a1: Float, u1: Float,
        dashes: Int, c: FloatArray, alpha: Float
    ): Int {
        SceneParts.at(g, 0f, u0, a0, o)
        SceneParts.at(g, 0f, u1, a1, p)
        return MathMesh.dashed(line, v, o[0], o[1], o[2], p[0], p[1], p[2], dashes,
            c[0], c[1], c[2], alpha)
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // up = 0, so the figure's u is measured from the rail centre and can be compared directly
        // with the roof height the ambient scene is drawing.
        SceneParts.stage(kit, i.toFloat(), SIDE, 0f, f, g)

        val roof = if (kit.hasTrace) kit.traceHeight(i.toFloat()) else 1.9f
        // Sized off the actual ceiling rather than a number tuned once on one stop: the drawn part
        // of the column then reads as "nearly out of headroom" wherever this scene is placed.
        unitH = ((roof - BASE_U) / 4.0f).coerceIn(0.45f, 1.00f)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val ng = groups(kit)
        val nTerm = 1 shl (ng - 1)
        val lay = SceneParts.step(c, 0f, 0.09f)

        // --- the ground the tower stands on ---------------------------------------------------
        v = seg(line, v, TOWER_A, BASE_U, TOWER_A + nTerm * W, BASE_U, SceneParts.CHALK, 0.45f)

        // --- the harmonic terms, laid down left to right --------------------------------------
        // 1, 1/2, 1/3 ... 1/16. By the far end the bricks are slivers, which is the objection the
        // stop exists to answer: they shrink to nothing and it changes nothing.
        for (k in 1..nTerm) {
            val al = (lay * nTerm - (k - 1)).coerceIn(0f, 1f)
            if (al <= 0.01f) continue
            v = rect(kit, line, v, tri, TOWER_A + (k - 1) * W, BASE_U, W, unitH / k,
                SceneParts.CHALK, 0.85f * al)
        }

        // --- the brackets, their floors, and the copies that fly home --------------------------
        var flash = 0f
        var flashU = 0f
        for (gi in 0 until ng) {
            val lo = groupLo(gi)
            val hi = groupHi(gi)
            val a0 = TOWER_A + (lo - 1) * W
            val wa = (hi - lo + 1) * W
            val fh = unitH / hi                     // the shortest brick in the group
            val at = AT0 + gi * DG
            val bra = SceneParts.step(c, at, DG * 0.30f)
            val flo = SceneParts.step(c, at + DG * 0.25f, DG * 0.30f)
            val fly = SceneParts.step(c, at + DG * 0.55f, DG * 0.42f)

            // The bracket, drawn as a pen would draw it: a spine that grows and a tick that
            // travels on its end. Each one is twice the corridor of the one before.
            if (gi > 0 && bra > 0.01f) {
                val bu = BASE_U - 0.13f
                val ax = a0 + wa * bra
                v = seg(line, v, a0, bu, ax, bu, SceneParts.STEEL, 0.80f)
                if (kit.quality < 2) {
                    v = seg(line, v, a0, bu, a0, bu + 0.08f, SceneParts.STEEL, 0.80f)
                    v = seg(line, v, ax, bu, ax, bu + 0.08f, SceneParts.STEEL, 0.80f)
                }
            }

            // The floor: laid at the height of the group's own shortest term, so no brick in the
            // group is below it. Its area is (number of terms) x (shortest term) = half a unit,
            // for every group, for ever — that identity is the entire mechanism of this stop.
            if (flo > 0.01f) {
                v = rect(kit, line, v, tri, a0, BASE_U, wa * flo, fh, SceneParts.WORK, 0.85f)
            }

            // The copy on its way to the tally column. Width is interpolated and height is then
            // area/width, so the block is exactly as much stuff at every instant of the flight as
            // the floor it was taken from; the lift is out of the figure's plane, toward the eye,
            // so it passes in front of the tower rather than through it.
            if (fly > 0.01f) {
                val area = wa * fh
                val w = wa + (W - wa) * fly
                val h = area / w
                val aa = a0 + (STACK_A - a0) * fly
                val uu = BASE_U + (blockBase(gi) - BASE_U) * fly
                val lift = 4f * fly * (1f - fly) * 0.30f
                v = rect(kit, line, v, tri, aa, uu, w, h, SceneParts.WORK, 0.95f, lift)
            }

            val seat = SceneParts.step(c, at + DG * 0.92f, DG * 0.18f)
            val pulse = seat * (1f - seat) * 4f
            if (pulse > flash) {
                flash = pulse
                flashU = blockBase(gi) + blockH(gi) * 0.5f
            }
        }

        val top = blockBase(ng - 1) + blockH(ng - 1)

        // --- and the groups there was no corridor left to draw ---------------------------------
        // Dashed, and fading, and deliberately running out of the passage: the rungs keep the same
        // pitch as the solid blocks because that is the claim — the climb does not slow down.
        val ghosts = when (kit.quality) {
            0 -> 4
            1 -> 2
            else -> 0
        }
        if (ghosts > 0) {
            val gh = SceneParts.step(c, AT0 + ng * DG, 0.10f)
            var ga = 0.55f * gh
            for (q in 1..ghosts) {
                val uq = top + unitH * 0.5f * q
                v = dash(line, v, STACK_A, uq, STACK_A + W, uq, 3, SceneParts.WORK, ga)
                ga *= 0.58f
            }
        }

        // --- where the roof passes the column ---------------------------------------------------
        // Not the roof curve — the ambient scene owns that and draws it the whole length of the
        // rail. This is one short tie at the roof's height beside the column, so that "the stack
        // has run out of ceiling" is a comparison the eye can make instead of a claim.
        if (kit.hasTrace && kit.quality < 2) {
            v = dash(line, v, STACK_A - 0.28f, roof, STACK_A + W + 0.28f, roof, 6, SceneParts.STEEL, 0.40f)
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // The lamp as a block seats. One call, and only for the half second it is worth having.
        if (flash > 0.02f) {
            SceneParts.at(g, 0f, flashU, STACK_A + W * 0.5f, o)
            kit.ball(
                o[0], o[1], o[2], 0.085f, 0.085f, 0.085f, SceneParts.HOT, SceneParts.WORK,
                flash, 0f, 0f, 1f, 0f, 0f, 3f * flash
            )
        }

        // --- notation -----------------------------------------------------------------------
        // Everything here NAMES a piece of the figure. The running totals are on the HUD, where a
        // number can actually be read; nothing in the scene is a readout.
        val gl = 0.20f

        if (kit.quality == 0) {
            // The first term and the last, hung off the ends of the tower rather than over it, so
            // neither one lands in the telemetry strip or the caption box.
            SceneParts.at(g, 0f, BASE_U + unitH * 0.5f, TOWER_A - 0.14f, o)
            kit.text("1", o[0], o[1], o[2], gl, SceneParts.CHALK, 0.90f, GlyphBoard.Style.PLAIN, 1f, 0.5f)
            SceneParts.at(g, 0f, BASE_U + 0.13f, TOWER_A + nTerm * W + 0.14f, o)
            kit.text("1/16", o[0], o[1], o[2], gl * 0.8f, SceneParts.CHALK, 0.80f, GlyphBoard.Style.SMALL, 1f, -0.5f)

            // One label per bracket, at the bracket's own height and just past its far end. They
            // all say the same thing on purpose: the repetition IS the divergence.
            for (gi in 1 until ng) {
                if (SceneParts.step(c, AT0 + gi * DG, DG * 0.30f) < 0.96f) continue
                SceneParts.at(g, 0f, BASE_U - 0.13f, TOWER_A + groupHi(gi) * W + 0.13f, o)
                kit.text(
                    BRACKET[gi], o[0], o[1], o[2], gl * 0.78f, SceneParts.WORK, 0.85f,
                    GlyphBoard.Style.SMALL, 1f, -0.5f
                )
            }
        }

        // The claim, once the column has been built and not before.
        val said = SceneParts.step(c, AT0 + ng * DG + 0.06f, 0.06f)
        if (said > 0.02f) {
            SceneParts.at(g, 0f, top - unitH * 0.45f, STACK_A + W + 0.22f, o)
            kit.text(
                "Σ 1/k → ∞", o[0], o[1], o[2], 0.22f, SceneParts.HOT, said,
                GlyphBoard.Style.MATH, 1.15f, -0.5f
            )
        }
    }
}
