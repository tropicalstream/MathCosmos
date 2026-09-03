package com.rayneo.mathcosmos

import kotlin.math.sin

/**
 * Stop 5 of TOUR III — THE TWO CLOCKS. "If I have a total-so-far function, I never have to add the
 * slabs at all — I just subtract two readings."
 *
 * The evaluation form, ∫ₐᵇ f = F(b) − F(a), staged as an act of bookkeeping rather than a formula.
 * Two gates stand across the corridor. At each one the floor ribbon — the running total the last
 * stop built — is standing as a rod: short at the near gate, tall at the far one. The short rod is
 * taken out of the tall one; the part they share goes dark and is never counted; what is left
 * standing is measured against the wake between the gates, which squashes down into a bar of
 * exactly that length.
 *
 * FOUR DECISIONS WORTH WRITING DOWN.
 *
 * THE SUBTRACTION HAPPENS OFF THE RAIL. The gates belong across the corridor — you fly through
 * them, and that is the whole reason they read as gates. But the director holds the craft between
 * HOLD_LEAD and HOLD_TRAIL of the node for the three minutes this stop lasts, so the near gate is
 * BEHIND the viewer for most of it, and a comparison staged between the two gates would be a
 * comparison you cannot see. So the rods lift off their gates and fly out to a column beside the
 * craft, and the arithmetic is done there, in one flat figure about a unit and a half across that
 * stays in frame for the whole hold. The gates keep a dim ghost of each rod, so where the readings
 * came from is never in doubt.
 *
 * THE LEFTOVER IS THE INTEGRAL BY CONSTRUCTION, NOT BY COINCIDENCE. F(a) is integrated from the
 * datum to the near gate and the gate-to-gate stretch is integrated on its own grid; F(b) is then
 * F(a) plus that stretch. So the lit piece left standing IS the between-gates area, to the last
 * bit, rather than the difference of two independent approximations that agree to two decimals.
 * Engineering says "not roughly as long — exactly", and the code had better mean it.
 *
 * THE BAR'S WIDTH IS FORCED. The squashed wake has to keep the area it had and stand exactly as
 * tall as the leftover piece. Area/width = scale·area has one solution, width = 1/scale, so the
 * bar's width is not a styling choice and is computed, not typed. The units throughout are the
 * tour's own swept units — rail node-units times world height, the same quantity SceneAmbientWake
 * puts on the HUD as SWEPT — so the number beside this figure and the number on the telemetry
 * agree instead of being two unrelated scales for the same thing.
 *
 * THE DATUM MOVES AT THE END. Engineering's realisation at this stop is that it cannot matter
 * where whoever built the ribbon started counting, because both readings carry the same head start
 * and the subtraction eats it. So in the last beat the datum slides: the ribbon and both rods
 * shrink together, and the lit piece and the bar slide down bodily without changing length by a
 * hair. That is the constant of integration, and adding a scalar to both readings is not a fudge
 * standing in for the idea — it is precisely the idea.
 *
 * The compressed wake is drawn as twelve rectangles rather than following the roof exactly; over
 * 3.8 world units of corridor each rectangle is a third of a unit long and the difference is not
 * resolvable, but it IS an approximation and the totals come from the rectangles, so the picture
 * and the arithmetic are the same object.
 */
object SceneTwoClocks : MathScene {

    override val reach = 1.5f
    override val deep = 0.25f

    // ------------------------------------------------------------------ the corridor
    private const val DATUM = -0.50f       // where the ribbon's zero sits, in node units from the stop
    private const val GATE_A = -0.10f      // the near gate
    private const val GATE_B = 0.14f       // the far one
    private const val PERIOD = 28f

    // ------------------------------------------------------------------ the figure
    private const val SIDE = -1.40f        // the whole comparison hangs to one side of the rail
    private const val BASE = -1.60f        // and below it, on the floor the ribbon runs along
    private const val COL_B = -0.20f       // the tall rod's column, in the stage's own coordinates
    private const val COL_A = 0.30f        // the short rod's, which slides onto the tall one
    private const val BAR_S = 0.52f        // the squashed wake's left edge
    private const val TALL = 1.80f         // how long the tall rod is drawn; this sets the scale
    private const val ROD_R = 0.052f

    /** How far the datum swings in the last beat, in swept units. Chosen so nothing goes negative. */
    private const val DATUM_SWING = 0.30f

    private const val STRIPS = 12          // the gate-to-gate stretch, and the bar's slabs
    private const val RIB_A = 24           // ribbon samples from the datum to the near gate
    private const val RIB_N = RIB_A + STRIPS

    private const val TAU = 6.2831855f

    /** What "gone dark" looks like on a waveguide: present, outlined, contributing nothing. */
    private val DARK = floatArrayOf(0.17f, 0.16f, 0.20f, 1f)

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val p1 = FloatArray(3)
    private val p2 = FloatArray(3)
    private val p3 = FloatArray(3)
    private val sO = FloatArray(3)
    private val sU = FloatArray(3)
    private val sV = FloatArray(3)
    private val tO = FloatArray(3)
    private val tU = FloatArray(3)
    private val tV = FloatArray(3)
    private val footA = FloatArray(3)
    private val footB = FloatArray(3)
    private val tip = FloatArray(3)
    private val gUp = FloatArray(3)
    private val gRight = FloatArray(3)
    private val tvi = IntArray(1)
    private val cLo = FloatArray(4)
    private val cHi = FloatArray(4)
    private val cSh = FloatArray(4)

    // Everything below is fixed the moment the trace and the node index are known, so it is
    // computed once and never again — including the two HUD lines, which would otherwise be the
    // only string building in a per-frame path.
    private val fRib = FloatArray(RIB_N + 1)
    private val stripH = FloatArray(STRIPS)
    private var fa = 0f
    private var fb = 0f
    private var diff = 0f
    private var scale = 1f
    private var barW = 0.6f
    private var readA = ""
    private var readB = ""
    private var built = false

    /**
     * The readings and the ribbon, integrated once. The near-gate value and the gate-to-gate
     * stretch are integrated separately and F(b) is their sum, which is what makes the leftover
     * piece exactly the stretch rather than nearly it.
     */
    private fun build(kit: SceneKit, i: Int) {
        if (built) return
        val base = i.toFloat()
        var acc = 0f
        fRib[0] = 0f
        val d1 = (GATE_A - DATUM) / RIB_A
        for (k in 0 until RIB_A) {
            acc += kit.traceHeight(base + DATUM + (k + 0.5f) * d1) * d1
            fRib[k + 1] = acc
        }
        fa = acc
        val d2 = (GATE_B - GATE_A) / STRIPS
        for (k in 0 until STRIPS) {
            val h = kit.traceHeight(base + GATE_A + (k + 0.5f) * d2)
            stripH[k] = h
            acc += h * d2
            fRib[RIB_A + k + 1] = acc
        }
        fb = acc
        diff = fb - fa
        // The tall rod is drawn TALL long whatever the trace turns out to be, and the bar's width
        // then follows from wanting it to keep its area and match the leftover's length.
        scale = TALL / (if (fb > 0.05f) fb else 0.05f)
        barW = 1f / scale
        readA = "F(a) ${two(fa)}   F(b) ${two(fb)}"
        readB = "F(b)−F(a) ${two(diff)}   WAKE ${two(diff)}"
        built = true
    }

    /** Two decimals without a formatter, and without allocating a formatter to get them. */
    private fun two(v: Float): String {
        val neg = v < 0f
        val t = ((if (neg) -v else v) * 100f + 0.5f).toInt()
        val c = t % 100
        return "${if (neg) "−" else ""}${t / 100}.${if (c < 10) "0" else ""}$c"
    }

    /** Node position of ribbon sample [k]: the two legs have different steps, on purpose. */
    private fun ribP(base: Float, k: Int): Float =
        if (k <= RIB_A) base + DATUM + (GATE_A - DATUM) * (k.toFloat() / RIB_A)
        else base + GATE_A + (GATE_B - GATE_A) * ((k - RIB_A).toFloat() / STRIPS)

    private fun lerp3(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
        out[0] = a[0] + (b[0] - a[0]) * t
        out[1] = a[1] + (b[1] - a[1]) * t
        out[2] = a[2] + (b[2] - a[2]) * t
    }

    private fun sub3(a: FloatArray, b: FloatArray, out: FloatArray) {
        out[0] = a[0] - b[0]; out[1] = a[1] - b[1]; out[2] = a[2] - b[2]
    }

    /**
     * A colour blended toward another, with an alpha of its own. The lit shader reads base[3] as an
     * alpha multiplier and kit.rod offers no alpha of its own, so this is how a strut is faded —
     * and it is written into a scratch array, because the palette is shared and must not be mutated.
     */
    private fun mix4(a: FloatArray, b: FloatArray, t: Float, alpha: Float, out: FloatArray) {
        out[0] = a[0] + (b[0] - a[0]) * t
        out[1] = a[1] + (b[1] - a[1]) * t
        out[2] = a[2] + (b[2] - a[2]) * t
        out[3] = alpha
    }

    /** A palette colour copied out at a given alpha, for the same reason [mix4] exists. */
    private fun set4(a: FloatArray, alpha: Float, out: FloatArray) {
        out[0] = a[0]; out[1] = a[1]; out[2] = a[2]; out[3] = alpha
    }

    /** A point [up] above a rod's foot and [across] to one side of it, for the label beside it. */
    private fun beside(f0: FloatArray, up: Float, across: Float, out: FloatArray) {
        out[0] = f0[0] + gUp[0] * up + gRight[0] * across
        out[1] = f0[1] + gUp[1] * up + gRight[1] * across
        out[2] = f0[2] + gUp[2] * up + gRight[2] * across
    }

    override fun readout(kit: SceneKit): String? {
        // Null until the first draw has integrated the ribbon; the renderer only asks the nearest
        // stop, which by then has been drawn at least once.
        if (!built) return null
        return if (SceneParts.cycle(kit.seconds, PERIOD) < 0.56f) readA else readB
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build(kit, i)
        SceneParts.stage(kit, i.toFloat(), SIDE, BASE, f, g)
        // The stage's up and right, held for the whole frame: every rod stands along one and every
        // label steps aside along the other, whether it is out at a gate or home in the figure.
        SceneParts.vec(g, 0f, 1f, 0f, gUp)
        SceneParts.vec(g, 1f, 0f, 0f, gRight)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tvi[0] = 0
        val q = kit.quality
        val base = i.toFloat()
        val pa = base + GATE_A
        val pb = base + GATE_B

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val lift = SceneParts.step(c, 0.14f, 0.14f)     // the rods leave their gates
        val merge = SceneParts.step(c, 0.40f, 0.14f)    // the short one slides onto the tall one
        val squash = SceneParts.step(c, 0.60f, 0.16f)   // the wake between the gates compresses
        // The datum swing: one slow breath out and back, so the loop ends where it started.
        val swing = ((c - 0.80f) / 0.12f).coerceIn(0f, 1f)
        val datum = -DATUM_SWING * sin(swing * 3.1415927f)
        // A short lights-down before the wrap, so the loop restarts as a fade rather than a cut.
        val lights = 1f - SceneParts.step(c, 0.96f, 0.04f)

        // The lit piece is the between-gates area and nothing else, so it is computed from that and
        // the tall rod is built up FROM it. Deriving it instead as tallLen − shortLen would put the
        // scene's one exact claim at the mercy of the guard clamp below.
        val leftover = diff * scale
        val shortLen = ((fa + datum) * scale).coerceAtLeast(0.06f)
        val tallLen = shortLen + leftover

        // Where the two rods are standing this frame — at their gates, in flight, or home in the
        // figure. Settled first, because the tethers, the struts and the labels all hang off them.
        val bow = 4f * lift * (1f - lift) * 0.40f
        val shortA = (1f - ((merge - 0.70f) / 0.30f).coerceIn(0f, 1f)) * lights
        val shortS = COL_A + (COL_B - COL_A) * merge
        kit.pointAt(pb, 0f, BASE, 0f, p1)
        SceneParts.at(g, COL_B, 0f, 0f, p2)
        lerp3(p1, p2, lift, footB)
        kit.pointAt(pa, 0f, BASE, 0f, p1)
        SceneParts.at(g, shortS, 0f, 0f, p2)
        lerp3(p1, p2, lift, footA)
        var bi = 0
        while (bi < 3) { footA[bi] += gUp[bi] * bow; footB[bi] += gUp[bi] * bow; bi++ }

        // ------------------------------------------------------------------ the two gates
        // A ring across the passage at each gate, well inside the wall. You fly through these.
        val segs = if (q == 0) 24 else 14
        val gateAlpha = 0.42f + 0.18f * kit.beat
        var gk = 0
        while (gk < 2) {
            val p = if (gk == 0) pa else pb
            kit.frame(p, f)
            val rr = kit.radius(p) * 0.78f
            v = MathMesh.arc(
                line, v, f[0], f[1], f[2], f[6], f[7], f[8], f[9], f[10], f[11],
                rr, 0f, TAU, segs, SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], gateAlpha
            )
            // The ghost of the rod that stands here: dim, but never absent, so a reading that has
            // flown off to the figure still has a place it was taken from.
            val len = if (gk == 0) shortLen else tallLen
            kit.pointAt(p, 0f, BASE, 0f, p1)
            kit.pointAt(p, 0f, BASE + len, 0f, p2)
            v = MathMesh.segment(
                line, v, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
                SceneParts.WORK_DIM[0], SceneParts.WORK_DIM[1], SceneParts.WORK_DIM[2],
                0.30f, 0.55f
            )
            // and a cross-tick at the top, which is where the ribbon's height was read off
            kit.pointAt(p, -0.16f, BASE + len, 0f, p1)
            kit.pointAt(p, 0.16f, BASE + len, 0f, p3)
            v = MathMesh.segment(
                line, v, p1[0], p1[1], p1[2], p3[0], p3[1], p3[2],
                SceneParts.HOT[0], SceneParts.HOT[1], SceneParts.HOT[2], 0.65f
            )
            gk++
        }

        // ------------------------------------------------------------------ the floor ribbon
        // The total-so-far, running along the floor from its datum up to the far gate. It passes
        // exactly through both rod tops because the rods are read off these very samples.
        val stepK = if (q == 0) 1 else 2
        var have = false
        var k = 0
        while (k <= RIB_N) {
            kit.pointAt(ribP(base, k), 0f, BASE + (fRib[k] + datum) * scale, 0f, p2)
            if (have) v = MathMesh.segment(
                line, v, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], 0.85f
            )
            p1[0] = p2[0]; p1[1] = p2[1]; p1[2] = p2[2]
            have = true
            k += stepK
        }
        // The datum itself, dashed along the floor: the line somebody arbitrarily called zero.
        if (q < 2) {
            val zero = BASE + datum * scale
            k = 0
            while (k + 2 <= RIB_N) {
                kit.pointAt(ribP(base, k), 0f, zero, 0f, p1)
                kit.pointAt(ribP(base, k + 2), 0f, zero, 0f, p2)
                v = MathMesh.segment(
                    line, v, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
                    SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0.28f
                )
                k += 4
            }
        }

        // ------------------------------------------- the wake between the gates, and its bar
        // Twelve rectangles of wake that fly into twelve stacked slabs. Source and target are both
        // parallelograms, so lerping the origin and the two spans keeps every intermediate one a
        // parallelogram too — which is why this can be a morph rather than a cross-fade.
        if (c > 0.575f) {
            val d2 = (GATE_B - GATE_A) / STRIPS
            // The stretch is picked out of the wake before it moves, so the compression starts from
            // something the viewer has already been shown rather than appearing mid-squash.
            val appear = ((c - 0.575f) / 0.025f).coerceIn(0f, 1f)
            val stripAlpha = (0.16f + 0.22f * squash) * appear * lights
            var cum = 0f
            var s = 0
            // A strip count is not a draw count: all twelve go into one flush, so the thermal
            // governor has nothing to gain by halving this loop.
            while (s < STRIPS) {
                val p0 = base + GATE_A + d2 * s
                val h = stripH[s]
                kit.pointAt(p0, 0f, 0f, 0f, sO)
                kit.pointAt(p0 + d2, 0f, 0f, 0f, p2)
                kit.pointAt(p0, 0f, h, 0f, p3)
                sub3(p2, sO, sU)
                sub3(p3, sO, sV)
                val slab = h * d2 / barW
                SceneParts.at(g, BAR_S, shortLen + cum, 0f, tO)
                SceneParts.vec(g, barW, 0f, 0f, tU)
                SceneParts.vec(g, 0f, slab, 0f, tV)
                lerp3(sO, tO, squash, o)
                lerp3(sU, tU, squash, du)
                lerp3(sV, tV, squash, dv)
                tvi[0] = MathMesh.quad(
                    tri, tvi[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                    SceneParts.ADDED[0], SceneParts.ADDED[1], SceneParts.ADDED[2], stripAlpha
                )
                cum += slab
                s++
            }
            // The bar's rim, once it has arrived and is a thing to be measured against.
            if (squash > 0.85f) {
                SceneParts.at(g, BAR_S, shortLen, 0f, o)
                SceneParts.vec(g, barW, 0f, 0f, du)
                SceneParts.vec(g, 0f, leftover, 0f, dv)
                v = SceneParts.edge(
                    line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                    SceneParts.ADDED, 0.90f * lights
                )
            }
        }

        // ----------------------------------------------- the dark part, outlined but uncounted
        if (merge > 0.05f && lift > 0.98f) {
            SceneParts.at(g, COL_B - ROD_R, 0f, 0f, o)
            SceneParts.vec(g, ROD_R * 2f, 0f, 0f, du)
            SceneParts.vec(g, 0f, shortLen, 0f, dv)
            v = SceneParts.edge(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.STEEL, 0.30f * merge * lights
            )
        }

        // ---------------------------------------------------------------- the arms, if they are out
        // This stop is in the tour's armStops, so the probes really are reaching here; the tethers
        // are drawn to the two rods because holding both readings at once is the whole picture.
        if (kit.reach > 0.03f && lift > 0.5f) {
            beside(footB, tallLen, 0f, o)
            v = MathMesh.segment(
                line, v, kit.shipX, kit.shipY, kit.shipZ, o[0], o[1], o[2],
                SceneParts.LAMP[0], SceneParts.LAMP[1], SceneParts.LAMP[2], 0.05f, 0.30f * kit.reach
            )
            if (shortA > 0.02f) {
                beside(footA, shortLen, 0f, o)
                v = MathMesh.segment(
                    line, v, kit.shipX, kit.shipY, kit.shipZ, o[0], o[1], o[2],
                    SceneParts.LAMP[0], SceneParts.LAMP[1], SceneParts.LAMP[2], 0.05f, 0.30f * kit.reach
                )
            }
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tvi[0])

        // ------------------------------------------------------------------ the rods themselves
        // Three struts and no more: the shared part, the part left over, and the short rod while it
        // is still its own object. They stay upright through the flight, because a measuring rod
        // that tumbles on its way across is a rod you no longer believe.
        // the tall rod, in two pieces: the part the readings share, and the part they do not
        mix4(SceneParts.WORK, DARK, merge, (0.95f - 0.35f * merge) * lights, cLo)
        tip[0] = footB[0] + gUp[0] * shortLen
        tip[1] = footB[1] + gUp[1] * shortLen
        tip[2] = footB[2] + gUp[2] * shortLen
        kit.rod(footB[0], footB[1], footB[2], tip[0], tip[1], tip[2], ROD_R, cLo, SceneParts.STEEL, 0.15f)
        mix4(SceneParts.WORK, SceneParts.ADDED, merge, 0.96f * lights, cHi)
        kit.rod(
            tip[0], tip[1], tip[2],
            footB[0] + gUp[0] * tallLen, footB[1] + gUp[1] * tallLen, footB[2] + gUp[2] * tallLen,
            ROD_R, cHi, SceneParts.HOT, 0.20f + 0.60f * merge
        )

        // the short rod, flying in from the near gate and then sliding across
        if (shortA > 0.02f) {
            set4(SceneParts.WORK_DIM, 0.92f * shortA, cSh)
            kit.rod(
                footA[0], footA[1], footA[2],
                footA[0] + gUp[0] * shortLen, footA[1] + gUp[1] * shortLen, footA[2] + gUp[2] * shortLen,
                ROD_R, cSh, SceneParts.STEEL, 0.15f
            )
        }

        // the flash as the bar seats against the leftover
        if (squash > 0.90f && squash < 1f) {
            SceneParts.at(g, BAR_S, shortLen + leftover * 0.5f, 0f, o)
            val fl = (1f - squash) * 10f
            kit.ball(
                o[0], o[1], o[2], 0.09f, 0.09f, 0.09f, SceneParts.HOT, SceneParts.ADDED,
                fl * lights, 0f, 0f, 1f, 0f, 0f, 3f * fl
            )
        }

        // ------------------------------------------------------------------ notation
        // Everything sits BESIDE a column, at the height of the reading it names. The gate letters
        // and the integral sign are secondary — the figure says both without them — so they are the
        // first thing the thermal governor takes away.
        if (q == 0) {
            kit.pointAt(pa, 0.22f, BASE + shortLen * 0.55f, 0f, o)
            kit.text("a", o[0], o[1], o[2], 0.20f, SceneParts.STEEL, 0.85f, GlyphBoard.Style.MATH, 1f, -0.5f)
            kit.pointAt(pb, 0.22f, BASE + tallLen * 0.55f, 0f, o)
            kit.text("b", o[0], o[1], o[2], 0.20f, SceneParts.STEEL, 0.85f, GlyphBoard.Style.MATH, 1f, -0.5f)
        }

        // The two readings, hung off the rods themselves so they are named at the gates, all the
        // way across, and standing side by side — never for a moment an unlabelled pair of sticks.
        if (merge < 0.6f) {
            beside(footB, tallLen * 0.88f, -0.12f, o)
            kit.text("F(b)", o[0], o[1], o[2], 0.19f, SceneParts.WORK, lights, GlyphBoard.Style.MATH, 1f, 0.5f)
            beside(footA, shortLen * 0.82f, 0.14f, o)
            kit.text("F(a)", o[0], o[1], o[2], 0.19f, SceneParts.WORK_DIM, shortA, GlyphBoard.Style.MATH, 1f, -0.5f)
        }

        // The claim, only once the picture has already made it true.
        if (merge > 0.55f) {
            SceneParts.at(g, COL_B - 0.12f, shortLen + leftover * 0.5f, 0f, o)
            kit.text(
                "F(b) − F(a)", o[0], o[1], o[2], 0.17f, SceneParts.ADDED,
                ((merge - 0.55f) / 0.45f).coerceIn(0f, 1f) * lights, GlyphBoard.Style.MATH, 1f, 0.5f
            )
        }
        if (squash > 0.5f && q == 0) {
            SceneParts.at(g, BAR_S + barW + 0.10f, shortLen + leftover * 0.5f, 0f, o)
            kit.text(
                "∫_a^b f", o[0], o[1], o[2], 0.18f, SceneParts.ADDED,
                ((squash - 0.5f) / 0.5f).coerceIn(0f, 1f) * lights, GlyphBoard.Style.MATH, 1f, -0.5f
            )
        }
    }
}
