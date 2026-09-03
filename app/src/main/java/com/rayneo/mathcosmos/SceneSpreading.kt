package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TOUR VI, stop 12 — THE SPREADING. "A rule about neighbours, run forward, is why heat smooths out
 * and why waves travel."
 *
 * A square plate of Tour V's country hangs off to port as a heat map. First the rule is applied ONCE,
 * BY HAND, to a single node: five beads — a centre and its four neighbours — with the average of the
 * four marked, and the centre pulled towards that mark while the rest of the plate stands still. Then
 * the whole grid is released and the plate melts: the fine ripple goes almost at once, the broad dish
 * takes the rest of the run, and the colours converge on the middle of the ramp. Then the rule is
 * swapped, a single ridge rises out of the flat plate, and under the wave rule it splits into two
 * travelling ridges that run to the ends, bounce, and meet again in the middle.
 *
 * THE HAND-WORKED STEP IS THE STOP. DESIGN §6.3 is blunt about the failure mode here and it is worth
 * repeating: a melting terrain is a PRETTY visual that teaches nothing, because the viewer sees
 * smoothing and concludes "things smooth out", which they knew before they got on board. What they do
 * not know is that the smoothing is the consequence of one local rule with no view of the whole plate.
 * So the rule is shown being applied once, slowly, to one node, before it is applied everywhere. Take
 * that first eight seconds out and this stop is wallpaper.
 *
 * ONE HONEST OVERSTATEMENT IN THAT STEP, and the crew says it out loud: a single step of the rule moves
 * the centre a FRACTION of the way to its neighbours' average — a few per cent, at the diffusion number
 * used here. Drawn faithfully, nothing would appear to happen. So the bead is taken the whole way. The
 * word the crew uses is "pulled towards", and towards is the honest word; the picture shows the
 * direction of the pull and lies only about its size. The node is then put BACK before the grid is
 * released, so the melt starts from the country as it actually is and not from a plate somebody has
 * already touched.
 *
 * WHY BOTH EQUATIONS ARE HERE. §6.3 flags "two ideas in one stop" as a breach of the series' own rule,
 * with splitting the stop as the fix if there is room. There is not — Tour VI is thirteen stops and the
 * last is the look-back — and the contrast is the whole content anyway: same lattice, same neighbours,
 * one rule reads the neighbours' average and the other reads it twice, and one destroys relief while the
 * other carries it about. The HUD readout is built to make exactly that comparison: it prints the plate's
 * peak-to-trough under both rules, and under the heat rule it falls to nothing while under the wave rule
 * it keeps coming back.
 *
 * WHAT IS PRECOMPUTED, AND WHY THE KEYFRAMES ARE NOT EVENLY SPACED IN TIME. §6.3's first fix was to solve
 * both equations offline and cycle static keyframes rather than step a grid every frame; the survey is
 * unambiguous that per-frame geometry storms are what cook the glasses. Both solves therefore run once,
 * on the first frame the landmark is in range — about six hundred explicit steps over 289 nodes, a few
 * milliseconds — and every frame afterwards is two array lookups and a lerp per node.
 *
 * The heat keyframes are spaced GEOMETRICALLY in simulated time, and that is not a trick to save memory.
 * Diffusion has no single time scale. On this grid the six-cell ripple loses half its amplitude every
 * step and the broad dish loses half in ninety; a clock that ran uniformly would show either a flicker
 * followed by twelve seconds of nothing, or twelve seconds of nothing at all. Walking the keyframe index
 * linearly through logarithmically spaced states is what makes "the rough goes first, the smooth takes
 * for ever" a thing you can watch. It also happens to be the truth about the equation.
 *
 * TWO DIFFERENT BOUNDARY CONDITIONS, FOR TWO DIFFERENT REASONS. The heat solve uses zero flux at the rim
 * — the ghost value outside equals the edge value — which conserves the plate's mean EXACTLY, so the
 * surface relaxes to a level that is genuinely flat rather than drifting off it by an accumulating
 * rounding error. The wave solve reflects the profile evenly about its end nodes, which is what makes the
 * pulse come home upright instead of inverted, and keeps the scheme exact. If those two ever get unified
 * for tidiness, the melt stops landing on flat and the bounce grows a numerical tail.
 *
 * THE WAVE'S BUMP IS A RIDGE, and the comment says so because the crew does. A round bump on a
 * two-dimensional plate does not split into two — it spreads as a ring, which is the truth in two
 * dimensions but is not the sentence this stop exists to say. Made a ridge, constant across the plate,
 * the two-dimensional rule collapses to the one-dimensional one and the bump splits into exactly two
 * travelling halves, by d'Alembert. Since the profile is then constant across the plate, only the profile
 * is stored and stepped: a thirty-third of the arrays for an identical picture.
 *
 * The wave step runs at Courant number one, where the one-dimensional scheme is EXACT on the grid — the
 * pulse translates by precisely one cell per step with no dispersion at all. That is a real property of
 * the difference scheme and not a cosmetic smoothing: it is why the two ridges stay crisp instead of
 * trailing numerical ripples that a viewer would quite reasonably mistake for physics.
 *
 * TWO PLACEMENT DECISIONS. The plate's relief is exaggerated a little over threefold, and it is hung at
 * the tour's ground datum rather than at the country's own local level. Both are free: the heat equation
 * does not care what constant you add to a solution and, being linear, a stretched initial state melts in
 * exactly the same way at exactly the same rate. What is drawn is the country's SHAPE, untouched. Left
 * alone it would be a dish barely a unit deep sitting a unit and a third below the datum, which is a
 * plate you look down at the back of and watch sag by a third of a unit.
 *
 * And this scene extends well past the passage wall, deliberately. Tour VI drops its wall alpha to 0.2
 * so the tube is a guide-rail, and a plate of country trimmed to a four-unit pipe would be a rug.
 *
 * Budget: ONE flushLines of about 1300 vertices, five beads during the hand-worked step only, and at most
 * three lines of notation. No triangles — a filled sheet would hide the ambient field's arrows passing
 * through it, and the corridor tours settled long ago that a mesh of glowing lines reads as a surface and
 * still lets the scene through.
 */
object SceneSpreading : MathScene {

    /** Eight units of plate wants to be there before you are alongside it. */
    override val reach = 1.7f

    /** It runs four units up the rail past its own node; do not cull it at the origin. */
    override val deep = 0.35f

    private const val TAU = 6.2831853f
    private const val PERIOD = 30f

    // ---- the plate ----------------------------------------------------------------------------
    private const val N = 17                     // nodes per side; 289 in all
    private const val NN = N * N
    private const val HALF = 4.2f                // half the plate's span, in world units
    private const val CELL = 2f * HALF / (N - 1)
    private const val PLATE_SIDE = -5.8f         // its centre, out to port of the rail
    private const val GAIN = 3.0f                // relief exaggeration; see the note above
    private const val RIPPLE_A = 0.55f           // the fine texture the rule destroys first
    private const val RIPPLE_L = 6f * CELL       // six cells, so it survives the quality-1 decimation

    // ---- the heat solve ------------------------------------------------------------------------
    private const val HK = 17                    // keyframes, geometrically spaced in time
    private const val R_DIFF = 0.24f             // diffusion number; the explicit scheme needs < 0.25
    private const val HGROW = 1.42f              // each keyframe gap is this much longer than the last

    // ---- the wave solve ------------------------------------------------------------------------
    // 32 steps is exactly one round trip on a 17-node plate at Courant number one, so keyframe 32 is
    // keyframe 0 again and the wave phase can end on the re-formed bump with no seam.
    private const val WK = 33
    private const val WAVE_A = 1.4f
    private const val BUMP = 2f                  // half-width of the initial ridge, in cells
    private const val WSPAN = 1.0f               // colour scale for the wave phase

    // ---- the clock, shared by draw() and readout() so the HUD cannot disagree with the picture ---
    private const val LIGHTS = 0.035f
    private const val RULE_AT = 0.05f
    private const val RULE_LEN = 0.13f
    private const val BEAD_OUT = 0.24f
    private const val BEAD_LEN = 0.05f           // ends exactly at MELT_AT: the node is back before release
    private const val MELT_AT = 0.29f
    private const val MELT_LEN = 0.29f
    private const val SWAP_AT = 0.62f
    private const val SWAP_LEN = 0.05f
    private const val WAVE_AT = 0.67f
    private const val WAVE_LEN = 0.27f

    // ---- how it is drawn -------------------------------------------------------------------------
    private const val GRID_A = 0.72f
    private const val RIM_A = 0.55f

    // ---- everything below is filled once, on the first frame ---------------------------------------
    private var built = false
    private var cX = 0f
    private var cZ = 0f
    private var sX = 1f                          // horizontal side unit vector of the rail here
    private var sZ = 0f
    private var hX = 0f                          // horizontal heading unit vector
    private var hZ = -1f
    private var datumY = 0f                      // the level the plate melts to
    private var hSpan = 1f                       // colour scale for the heat phase
    private var ruleIdx = 0
    private var ruleJ = 0
    private var ruleK = 0
    private var ruleU = 0f                       // the chosen node's own height
    private var ruleAvg = 0f                     // and its four neighbours' average

    private val fr = FloatArray(12)
    private val tint = FloatArray(3)
    private val p0 = FloatArray(3)
    private val p1 = FloatArray(3)
    private val px = FloatArray(NN)              // world x of every node, fixed for the ride
    private val pz = FloatArray(NN)
    private val heat = FloatArray(HK * NN)       // the melt, keyframe by keyframe
    private val wave = FloatArray(WK * N)        // the ridge profile, keyframe by keyframe
    private val hRelief = FloatArray(HK)         // peak-to-trough of each, for the readout
    private val wRelief = FloatArray(WK)
    private val disp = FloatArray(NN)            // this frame's surface, rebuilt from the keyframes
    private val solveA = FloatArray(NN)          // the two working buffers the heat solve steps between
    private val solveB = FloatArray(NN)

    /**
     * Where the plate stands, and both solutions, worked out before the ride sees any of it.
     *
     * The plate is laid out in the RAIL's horizontal side and heading rather than in world x and z —
     * rule seven, and it costs nothing here because the plate never moves. But the country under it is
     * sampled in world (x, z), because that is the domain [SceneKit.terrainHeight] is a function of.
     */
    private fun build(kit: SceneKit, i: Int) {
        if (built) return
        kit.frame(i.toFloat(), fr)
        var ax = fr[6]; var az = fr[8]
        var l = sqrt(ax * ax + az * az)
        if (l > 1e-4f) { ax /= l; az /= l } else { ax = 1f; az = 0f }
        sX = ax; sZ = az
        var bx = fr[3]; var bz = fr[5]
        l = sqrt(bx * bx + bz * bz)
        if (l > 1e-4f) { bx /= l; bz /= l } else { bx = 0f; bz = -1f }
        hX = bx; hZ = bz
        cX = fr[0] + sX * PLATE_SIDE
        cZ = fr[2] + sZ * PLATE_SIDE
        datumY = SceneAmbientCountry.GROUND_Y

        // ---- the country, and the state the rule is set going on ---------------------------------
        var mean = 0f
        var j = 0
        while (j < N) {
            var k = 0
            while (k < N) {
                val a = -HALF + 2f * HALF * k / (N - 1)
                val b = -HALF + 2f * HALF * j / (N - 1)
                val x = cX + sX * a + hX * b
                val z = cZ + sZ * a + hZ * b
                val idx = j * N + k
                px[idx] = x
                pz[idx] = z
                val t = kit.terrainHeight(x, z)
                // The ripple is put there on purpose. Without it the plate is one broad dish, and a
                // rule that destroys everything at the same rate teaches nothing about which rule it is.
                solveA[idx] = t + RIPPLE_A / GAIN * sin(TAU * a / RIPPLE_L) * sin(TAU * b / RIPPLE_L)
                mean += solveA[idx]
                k++
            }
            j++
        }
        mean /= NN
        var hi = 0f
        var idx = 0
        while (idx < NN) {
            val d = (solveA[idx] - mean) * GAIN
            heat[idx] = d
            if (abs(d) > hi) hi = abs(d)
            idx++
        }
        hSpan = if (hi > 1e-3f) hi else 1f

        // ---- the melt ---------------------------------------------------------------------------
        var gap = 1f
        var key = 1
        while (key < HK) {
            System.arraycopy(heat, (key - 1) * NN, solveA, 0, NN)
            val steps = max(1, round(gap).toInt())
            var s = 0
            while (s < steps) {
                heatStep(solveA, solveB)
                System.arraycopy(solveB, 0, solveA, 0, NN)
                s++
            }
            System.arraycopy(solveA, 0, heat, key * NN, NN)
            gap *= HGROW
            key++
        }
        key = 0
        while (key < HK) {
            var lo = 1e9f
            var top = -1e9f
            var m = 0
            while (m < NN) {
                val d = heat[key * NN + m]
                if (d < lo) lo = d
                if (d > top) top = d
                m++
            }
            hRelief[key] = top - lo
            key++
        }

        // ---- the wave ------------------------------------------------------------------------------
        // Keyframe 0 is the ridge; keyframe 1 is the HALF-coefficient first step, which is what "let go
        // from rest" means in a scheme that needs two previous states to get going. Everything after is
        // the plain three-term recurrence at Courant number one.
        var m = 0
        while (m < N) {
            val d = abs(m - (N - 1) * 0.5f)
            wave[m] = if (d <= BUMP) WAVE_A * 0.5f * (1f + cos(3.14159265f * d / BUMP)) else 0f
            m++
        }
        m = 0
        while (m < N) {
            wave[N + m] = 0.5f * (wave[mirrorLo(m)] + wave[mirrorHi(m)])
            m++
        }
        key = 2
        while (key < WK) {
            m = 0
            while (m < N) {
                wave[key * N + m] =
                    wave[(key - 1) * N + mirrorLo(m)] + wave[(key - 1) * N + mirrorHi(m)] -
                        wave[(key - 2) * N + m]
                m++
            }
            key++
        }
        key = 0
        while (key < WK) {
            var lo = 1e9f
            var top = -1e9f
            m = 0
            while (m < N) {
                val d = wave[key * N + m]
                if (d < lo) lo = d
                if (d > top) top = d
                m++
            }
            wRelief[key] = top - lo
            key++
        }

        // ---- which node the rule is demonstrated on --------------------------------------------------
        // The one the rule has most to say about: the largest gap between a node and its neighbours'
        // average, taken over the middle of the plate so the cluster is never out on the rim. Choosing it
        // by hand would mean re-choosing it every time the terrain function is touched.
        var best = -1f
        j = N / 4
        while (j <= 3 * N / 4) {
            var k = N / 4
            while (k <= 3 * N / 4) {
                val ix = j * N + k
                val av = (heat[(j - 1) * N + k] + heat[(j + 1) * N + k] +
                    heat[j * N + k - 1] + heat[j * N + k + 1]) * 0.25f
                val d = abs(av - heat[ix])
                if (d > best) {
                    best = d
                    ruleIdx = ix
                    ruleJ = j
                    ruleK = k
                    ruleU = heat[ix]
                    ruleAvg = av
                }
                k++
            }
            j++
        }
        built = true
    }

    /** One explicit step of the heat rule, with zero flux at the rim so the plate's mean is conserved. */
    private fun heatStep(src: FloatArray, dst: FloatArray) {
        var j = 0
        while (j < N) {
            val jm = if (j > 0) j - 1 else 0
            val jp = if (j < N - 1) j + 1 else N - 1
            var k = 0
            while (k < N) {
                val km = if (k > 0) k - 1 else 0
                val kp = if (k < N - 1) k + 1 else N - 1
                val u = src[j * N + k]
                dst[j * N + k] = u + R_DIFF *
                    (src[jm * N + k] + src[jp * N + k] + src[j * N + km] + src[j * N + kp] - 4f * u)
                k++
            }
            j++
        }
    }

    /** The plate's four corners, counted round: corner c to corner c+1 is one edge of the datum square. */
    private fun cornerA(c: Int) = if (c == 0 || c == 3) -HALF else HALF
    private fun cornerB(c: Int) = if (c < 2) -HALF else HALF

    /** Even reflection about the end nodes: the wave's boundary, and the reason it bounces upright. */
    private fun mirrorLo(m: Int) = if (m > 0) m - 1 else 1
    private fun mirrorHi(m: Int) = if (m < N - 1) m + 1 else N - 2

    /** A sample from a keyframe table, interpolated along the phase's own 0..1 clock. */
    private fun sampleAt(tab: FloatArray, keys: Int, u: Float): Float {
        val f = u * (keys - 1)
        val k = f.toInt().coerceIn(0, keys - 2)
        val t = (f - k).coerceIn(0f, 1f)
        return tab[k] + (tab[k + 1] - tab[k]) * t
    }

    /** A point in the plate's own flat coordinates: [a] across, [b] along the heading. */
    private fun world(a: Float, b: Float, y: Float, out: FloatArray) {
        out[0] = cX + sX * a + hX * b
        out[1] = y
        out[2] = cZ + sZ * a + hZ * b
    }

    /**
     * The number this stop is measuring, where numbers belong.
     *
     * During the hand-worked step it is the one node and its neighbours' average, so a viewer can watch
     * the first close the gap on the second. After that it is the plate's peak-to-trough, which is the
     * whole difference between the two rules stated as one number: the heat rule takes it to nothing and
     * the wave rule keeps handing it back.
     *
     * Nothing until the first draw has surveyed the country — readout() is handed no stop index and
     * cannot place the plate on its own — and it touches no renderer temporary, because it runs on the
     * UI thread whenever Android feels like rebuilding the telemetry block.
     */
    override fun readout(kit: SceneKit): String? {
        if (!built) return null
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        if (c < MELT_AT) {
            val pull = SceneParts.step(c, RULE_AT, RULE_LEN) * (1f - SceneParts.step(c, BEAD_OUT, BEAD_LEN))
            val u = ruleU + (ruleAvg - ruleU) * pull
            return "u %+.2f   AVERAGE %+.2f".format(Locale.US, u, ruleAvg)
        }
        if (c < SWAP_AT) {
            val mel = ((c - MELT_AT) / MELT_LEN).coerceIn(0f, 1f)
            return "RELIEF %.2f   SINKING".format(Locale.US, sampleAt(hRelief, HK, mel))
        }
        val wav = ((c - WAVE_AT) / WAVE_LEN).coerceIn(0f, 1f)
        return "RELIEF %.2f   TRAVELLING".format(Locale.US, sampleAt(wRelief, WK, wav))
    }

    /** The plate's diverging ramp at normalised height [t], into [tint]: cold, through pale, to hot. */
    private fun ramp(t: Float) {
        val u = t.coerceIn(0f, 1f)
        val a: FloatArray
        val b: FloatArray
        val s: Float
        if (u < 0.5f) { a = SceneParts.COOL; b = SceneParts.HOT; s = u * 2f }
        else { a = SceneParts.HOT; b = SceneParts.TAKEN; s = u * 2f - 1f }
        tint[0] = a[0] + (b[0] - a[0]) * s
        tint[1] = a[1] + (b[1] - a[1]) * s
        tint[2] = a[2] + (b[2] - a[2]) * s
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        // No country, no plate of it: this stop is a patch of Tour V's ground being operated on, and
        // a lattice melting over nothing would be a claim with nothing under it.
        if (!kit.hasTerrain) return
        build(kit, i)

        val q = kit.quality
        val line = kit.lineBuf
        var v = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // A short lights-up and lights-down, so the plate's state can be swapped back to the country
        // in the dark and the loop restarts as a fade rather than a cut.
        val lights = SceneParts.ease(c / LIGHTS) * (1f - SceneParts.step(c, 1f - LIGHTS, LIGHTS))
        if (lights <= 0.015f) return

        val pull = SceneParts.step(c, RULE_AT, RULE_LEN)
        val hold = 1f - SceneParts.step(c, BEAD_OUT, BEAD_LEN)
        val mel = ((c - MELT_AT) / MELT_LEN).coerceIn(0f, 1f)
        val rise = ((c - SWAP_AT) / SWAP_LEN).coerceIn(0f, 1f)
        val wav = ((c - WAVE_AT) / WAVE_LEN).coerceIn(0f, 1f)

        // --- this frame's surface ------------------------------------------------------------------
        // Two lookups and a lerp per node. `rise` crossfades the melted plate into the wave's opening
        // ridge, which is why the ridge appears to GROW out of the flat sheet rather than replace it:
        // the melt ends at nothing, so lerping to the ridge is the ridge rising.
        val hf = mel * (HK - 1)
        val hk = hf.toInt().coerceIn(0, HK - 2)
        val ht = (hf - hk).coerceIn(0f, 1f)
        val wf = wav * (WK - 1)
        val wk = wf.toInt().coerceIn(0, WK - 2)
        val wt = (wf - wk).coerceIn(0f, 1f)
        var j = 0
        while (j < N) {
            val w0 = wave[wk * N + j]
            val wv = w0 + (wave[(wk + 1) * N + j] - w0) * wt
            var k = 0
            while (k < N) {
                val idx = j * N + k
                val h0 = heat[hk * NN + idx]
                val hv = h0 + (heat[(hk + 1) * NN + idx] - h0) * ht
                disp[idx] = hv + (wv - hv) * rise
                k++
            }
            j++
        }
        // The one node the rule is worked on by hand, and its return before the grid is released. The
        // guard matters: past the demonstration this must stop writing, or the melt would run with one
        // node nailed to the country while everything around it relaxed.
        if (hold > 0.001f) disp[ruleIdx] = ruleU + (ruleAvg - ruleU) * pull * hold

        val span = hSpan + (WSPAN - hSpan) * rise
        val invSpan = 0.5f / span

        // --- the plate ---------------------------------------------------------------------------
        // Each segment takes the colour of its midpoint's height, which is what makes the melt a heat
        // map going uniform rather than a wireframe getting flatter.
        val sp = if (q == 0) 1 else 2
        val gridA = GRID_A * lights
        j = 0
        while (j < N) {
            var k = 0
            while (k < N) {
                val idx = j * N + k
                val y0 = datumY + disp[idx]
                if (k + sp < N) {
                    val id2 = idx + sp
                    val y1 = datumY + disp[id2]
                    ramp(0.5f + (disp[idx] + disp[id2]) * 0.5f * invSpan)
                    v = MathMesh.segment(
                        line, v, px[idx], y0, pz[idx], px[id2], y1, pz[id2],
                        tint[0], tint[1], tint[2], gridA
                    )
                }
                if (j + sp < N) {
                    val id2 = idx + sp * N
                    val y1 = datumY + disp[id2]
                    ramp(0.5f + (disp[idx] + disp[id2]) * 0.5f * invSpan)
                    v = MathMesh.segment(
                        line, v, px[idx], y0, pz[idx], px[id2], y1, pz[id2],
                        tint[0], tint[1], tint[2], gridA
                    )
                }
                k += sp
            }
            j += sp
        }

        // --- the two ends the ridges bounce off ------------------------------------------------------
        // The plate has real edges and they are drawn as objects, because "bounces off the ends" needs
        // ends. Each end brightens with whatever is standing on it, so the bounce is an event you see
        // arrive rather than a reversal you notice afterwards.
        val steel = SceneParts.STEEL
        var e0 = 0f
        var e1 = 0f
        var k2 = 0
        while (k2 < N) {
            e0 += abs(disp[k2])
            e1 += abs(disp[(N - 1) * N + k2])
            k2++
        }
        e0 = (e0 / N / span).coerceIn(0f, 1f)
        e1 = (e1 / N / span).coerceIn(0f, 1f)
        v = rim(line, v, 0, true, (RIM_A + 0.45f * e0) * lights, steel)
        v = rim(line, v, N - 1, true, (RIM_A + 0.45f * e1) * lights, steel)
        if (q == 0) {
            v = rim(line, v, 0, false, RIM_A * 0.7f * lights, steel)
            v = rim(line, v, N - 1, false, RIM_A * 0.7f * lights, steel)
        }

        // --- the flat it is heading for ----------------------------------------------------------------
        // A dashed square at the datum, up only while the melt is running. "Relaxing towards flat" wants
        // something to be towards; once the plate has arrived it would only be drawing over itself.
        if (q == 0) {
            val da = SceneParts.step(c, MELT_AT, 0.06f) * (1f - rise) * 0.30f * lights
            if (da > 0.02f) {
                val st = SceneParts.CHALK
                var corner = 0
                while (corner < 4) {
                    val nxt = (corner + 1) and 3
                    world(cornerA(corner), cornerB(corner), datumY, p0)
                    world(cornerA(nxt), cornerB(nxt), datumY, p1)
                    v = MathMesh.dashed(
                        line, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 9,
                        st[0], st[1], st[2], da
                    )
                    corner++
                }
            }
        }

        // --- the rule, applied once, by hand ------------------------------------------------------------
        val beadA = hold * lights
        val ra = -HALF + 2f * HALF * ruleK / (N - 1)
        val rb = -HALF + 2f * HALF * ruleJ / (N - 1)
        if (beadA > 0.02f) {
            val ay = datumY + disp[ruleIdx]
            world(ra, rb, ay, p0)
            val added = SceneParts.ADDED
            // The four struts: these, and only these, are the neighbours the rule is allowed to look at.
            var s = 0
            while (s < 4) {
                val nj = ruleJ + (if (s == 0) -1 else if (s == 1) 1 else 0)
                val nk = ruleK + (if (s == 2) -1 else if (s == 3) 1 else 0)
                val nix = nj * N + nk
                v = MathMesh.segment(
                    line, v, p0[0], p0[1], p0[2],
                    px[nix], datumY + disp[nix], pz[nix],
                    added[0], added[1], added[2], 0.85f * beadA
                )
                s++
            }
            // The mark the centre is being pulled to, and how far it still has to go.
            val my = datumY + ruleAvg
            world(ra - CELL * 0.45f, rb, my, p0)
            world(ra + CELL * 0.45f, rb, my, p1)
            v = MathMesh.segment(line, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
                added[0], added[1], added[2], 0.95f * beadA)
            world(ra, rb - CELL * 0.45f, my, p0)
            world(ra, rb + CELL * 0.45f, my, p1)
            v = MathMesh.segment(line, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
                added[0], added[1], added[2], 0.95f * beadA)
            world(ra, rb, ay, p0)
            world(ra, rb, my, p1)
            v = MathMesh.dashed(line, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 5,
                added[0], added[1], added[2], 0.7f * beadA)
        }

        kit.flushLines(v, 2.2f)

        // --- the five beads ------------------------------------------------------------------------------
        // Five draw calls, and the only ones this scene makes. At quality 2 the four neighbours go and the
        // struts carry the relation on their own; the centre stays, because the thing being moved cannot.
        if (beadA > 0.05f) {
            if (q < 2) {
                var s = 0
                while (s < 4) {
                    val nj = ruleJ + (if (s == 0) -1 else if (s == 1) 1 else 0)
                    val nk = ruleK + (if (s == 2) -1 else if (s == 3) 1 else 0)
                    val nix = nj * N + nk
                    kit.ball(
                        px[nix], datumY + disp[nix], pz[nix], 0.085f, 0.085f, 0.085f,
                        SceneParts.COOL, SceneParts.CHALK, beadA, 0f, 0f, 1f, 0f, 0f, 0.3f
                    )
                    s++
                }
            }
            world(ra, rb, datumY + disp[ruleIdx], p0)
            kit.ball(
                p0[0], p0[1], p0[2], 0.13f, 0.13f, 0.13f,
                SceneParts.LAMP, SceneParts.HOT, beadA, 0f, 0f, 1f, 0f, 0f,
                1.1f + kit.beat * 1.2f
            )
            // Stop 12 is not on this tour's arm list, so the boom is normally not out and the hand-worked
            // step has to stand on its own. Drawn anyway if it ever is, rather than silently ignoring it.
            if (kit.reach > 0.03f && q < 2) {
                val t = kit.reach
                val ox = kit.shipX; val oy = kit.shipY - 0.16f; val oz = kit.shipZ
                kit.rod(
                    ox, oy, oz,
                    ox + (p0[0] - ox) * t, oy + (p0[1] - oy) * t, oz + (p0[2] - oz) * t,
                    0.024f, steel, SceneParts.LAMP, 0.3f
                )
            }
        }

        // --- notation -------------------------------------------------------------------------------------
        // The rule the plate is obeying, hung level with the rail just inboard of the plate's near rim, so
        // it reads at the pass without straying into the telemetry block above or the caption box below.
        // One form at a time: the alpha dips through zero at the swap, so the change of rule is a beat
        // rather than two equations overlapping on a 640-wide eye.
        val swap = SceneParts.step(c, SWAP_AT, SWAP_LEN)
        val nAlpha = abs(swap * 2f - 1f) * lights
        world(HALF + 0.6f, 0f, datumY + 2.9f, p0)
        kit.text(
            if (swap < 0.5f) "∂u/∂t = ∇^2 u" else "∂^2u/∂t^2 = c^2 ∇^2 u",
            p0[0], p0[1], p0[2], 0.24f, SceneParts.HOT, nAlpha, GlyphBoard.Style.MATH, 1.15f
        )
        // Secondary, so quality 0 only: what the mark under the bead is. The identity above is the line
        // that has to survive a step-down.
        if (q == 0 && beadA > 0.05f) {
            world(ra - 0.85f, rb, datumY + ruleAvg + 0.10f, p0)
            kit.text("avg", p0[0], p0[1], p0[2], 0.16f, SceneParts.ADDED, 0.9f * beadA, GlyphBoard.Style.SMALL)
        }
    }

    /** One run of the plate's boundary: [end] true for a row of constant j, false for a column. */
    private fun rim(line: FloatArray, at: Int, idx: Int, end: Boolean, alpha: Float, col: FloatArray): Int {
        if (alpha <= 0.02f) return at
        var v = at
        var m = 0
        while (m < N - 1) {
            val i0 = if (end) idx * N + m else m * N + idx
            val i1 = if (end) idx * N + m + 1 else (m + 1) * N + idx
            v = MathMesh.segment(
                line, v, px[i0], datumY + disp[i0], pz[i0], px[i1], datumY + disp[i1], pz[i1],
                col[0], col[1], col[2], alpha
            )
            m++
        }
        return v
    }
}
