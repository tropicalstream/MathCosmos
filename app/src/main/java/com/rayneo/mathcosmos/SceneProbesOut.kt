package com.rayneo.mathcosmos

import java.util.Locale
import kotlin.math.abs

/**
 * Stop 3 of Tour VI — THE PROBES OUT. "Divergence is what a box gains or loses, and my probes can
 * just go and measure it."
 *
 * Divergence is the one piece of vector calculus that is genuinely an instrument reading, so it is
 * built as an instrument. A wireframe box is held out in the flow, a probe reaches to the middle of
 * each of its six faces, and each face lights with a cap of colour: red where the field is leaving,
 * blue where it is arriving, brightness proportional to the flux. The net of the six is a bar. The
 * crew never say "the trace of the Jacobian"; they say the box is filling up, and the box is
 * visibly filling up.
 *
 * WHAT IS ACTUALLY MEASURED. Each face is sampled once, at its centre, and the flux through it is
 * taken as v·n times the face area. One sample per face is exact for a field that is linear across
 * the box and near enough for one that is not, which is why the box is small; and it means the six
 * numbers on screen really are six measurements, not six copies of an answer worked out elsewhere.
 * The sum of the six divided by the box's volume is the divergence, and that is what the readout
 * says. During the source phase it reads +6.00 because the injected field is a uniform expansion of
 * 2 per second in each of three directions — the arithmetic comes out of the probes, not out of a
 * constant, and it agrees with 3q to the last displayed digit. That agreement is the point.
 *
 * WHY A SOURCE HAS TO BE MADE. Tour VI's own field is a swirl about the world axis plus a drift
 * along the rail, and both of those are divergence-free: every opposite pair of faces cancels to
 * the last decimal. There is nothing here for a box to catch. So the rig injects its own expansion
 * at the box's centre for part of the loop, and takes it away again. The honest phase is the last
 * one, where the injection is off and the box is measuring the real field of the tour and getting
 * nothing — which is exactly the case the stop needs a viewer to see, and is why the rest at the
 * end of the cycle is seven seconds long.
 *
 * The injected field is a uniform expansion, q·r, not a point source. That is a deliberate
 * simplification and the comment says so out loud because the picture does: all six probes read the
 * same outward push, which is what constant positive divergence looks like. A true monopole would
 * put nearly all of its flux through whichever face it happened to sit nearest, and the caps would
 * disagree with each other for a reason that has nothing to do with the idea being taught.
 *
 * PLACEMENT. The box is to STARBOARD rather than the usual port, and that is not arbitrary: at this
 * stop the rail sits at x ≈ -2.4, so 1.85 to starboard is where the swirl term (0.45x) very nearly
 * vanishes and the drift along the rail is almost the whole field. That is the "plain uniform flow"
 * the stop's third case asks for — one face red, one face blue, everything else faint, net zero. To
 * port the box would work identically and read worse: the cross-flow would light the up-and-down
 * pair instead, which is true but is not the picture. If the rail is ever re-cut the scene degrades
 * gracefully — the pairs still cancel wherever it ends up, only which pair is lit would change.
 *
 * The box is held out on a boom rather than wrapped around the craft as the design first had it.
 * A box you are inside is a box you cannot see a single face of at the closest point of the pass,
 * and the six caps are the whole content. The boom fades to nothing at the hull end so that at the
 * moment it sweeps past the eye there is nothing there to sweep.
 *
 * Twelve draw calls at quality 0: three buffer flushes, six probe rods, the bead, and two labels.
 * At quality 1 the probes stop being rods and become lines in the buffer that is already being
 * drawn, which is better than halving them — six probes with two missing is a broken instrument.
 */
object SceneProbesOut : MathScene {

    override val reach = 1.4f

    // ------------------------------------------------------------------ the rig
    private const val SIDE = 1.85f          // starboard: see the placement note above
    private const val UPOFF = 0.10f
    private const val H = 0.78f             // half-edge of the box
    private const val AREA = (2f * H) * (2f * H)
    private const val VOL = (2f * H) * (2f * H) * (2f * H)
    private const val CAP = 0.72f           // cap size as a fraction of its face
    private const val ARROW = 0.22f         // world length per unit of field speed

    // The injected expansion. 2 per second is the smallest value that keeps every one of the six
    // caps on the correct side of zero once the tour's own drift is added to it — at 1.5 the face
    // the drift is already leaving through goes ambiguous, which reads as a broken probe.
    private const val Q = 2.0f
    private const val FLUX_REF = 6.0f       // the face flux that saturates a cap

    private const val BAR_S = H + 0.34f
    private const val BAR_W = 0.13f
    private const val BAR_H = 0.46f
    private const val BAR_FULL = 26f        // net flux that fills the bar; the source reaches 0.88

    private const val PERIOD = 24f

    private const val CLAIM_POS = "∇·v > 0"
    private const val CLAIM_NEG = "∇·v < 0"
    private const val CLAIM_NIL = "∇·v ≈ 0"
    // Σ rather than the closed-surface integral sign, because a sum over six faces is literally
    // what the rig computes. The integral arrives at stop 7, when the skin stops being flat.
    private const val FLUX_LABEL = "Σ v·n A"

    // The six outward normals, in the stage's own (side, up, along) coordinates.
    private val NS = floatArrayOf(1f, -1f, 0f, 0f, 0f, 0f)
    private val NU = floatArrayOf(0f, 0f, 1f, -1f, 0f, 0f)
    private val NA = floatArrayOf(0f, 0f, 0f, 0f, 1f, -1f)

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val p = FloatArray(3)
    private val nw = FloatArray(3)
    private val t1 = FloatArray(3)
    private val t2 = FloatArray(3)
    private val fv = FloatArray(3)
    private val tv = IntArray(1)
    private val flux = FloatArray(6)

    // What the probes last read. The readout has no stop index and so cannot build the stage to
    // measure from; recomputing it there would also be sampling a different frame's field from the
    // one on screen. Two floats carried over from the draw are the cheap, honest way to have the
    // HUD report the same measurement the caps are showing.
    private var lastNet = 0f
    private var lastDiv = 0f

    override fun readout(kit: SceneKit): String? =
        String.format(Locale.US, "∇·v %+.2f   NET %+.1f", lastDiv, lastNet)

    /** The field at a world point, plus whatever expansion the rig is injecting at the box. */
    private fun sample(kit: SceneKit, x: Float, y: Float, z: Float, q: Float, out: FloatArray) {
        kit.fieldAt(x, y, z, out)
        if (q > -1e-4f && q < 1e-4f) return
        out[0] += q * (x - g[0])
        out[1] += q * (y - g[1])
        out[2] += q * (z - g[2])
    }

    /** The two in-plane directions of face [k], each of length [len], into [t1] and [t2]. */
    private fun tangents(k: Int, len: Float) {
        when (k / 2) {
            0 -> { SceneParts.vec(g, 0f, len, 0f, t1); SceneParts.vec(g, 0f, 0f, len, t2) }
            1 -> { SceneParts.vec(g, 0f, 0f, len, t1); SceneParts.vec(g, len, 0f, 0f, t2) }
            else -> { SceneParts.vec(g, len, 0f, 0f, t1); SceneParts.vec(g, 0f, len, 0f, t2) }
        }
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), SIDE, UPOFF, f, g)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // Source, then sink, then the field as it really is, and seven seconds of that to look at.
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val src = SceneParts.step(c, 0.10f, 0.09f) - SceneParts.step(c, 0.33f, 0.05f)
        val snk = SceneParts.step(c, 0.42f, 0.09f) - SceneParts.step(c, 0.65f, 0.05f)
        val q = Q * (src - snk)
        val ext = SceneParts.step(c, 0.005f, 0.05f)
        // The arms are on the tour's armStops, so they really are out here; reach lights them
        // rather than driving them, so the scene still reads if the mechanism is stowed.
        val armLit = 0.35f + 0.65f * kit.reach
        val rods = kit.quality == 0 && ext > 0.02f

        // --- the box ---------------------------------------------------------------------------
        SceneParts.vec(g, H, 0f, 0f, t1)
        SceneParts.vec(g, 0f, H, 0f, t2)
        SceneParts.vec(g, 0f, 0f, H, p)
        v = MathMesh.boxEdges(
            line, v, g[0], g[1], g[2],
            t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], p[0], p[1], p[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.55f
        )

        // --- the boom back to the hull, faded out before it reaches the eye ---------------------
        if (kit.quality < 2) {
            SceneParts.at(g, -SIDE * 0.9f, -0.34f, 0.55f, o)
            SceneParts.at(g, -H - 0.04f, 0f, 0f, p)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], p[0], p[1], p[2],
                SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2], 0f, 0.45f * armLit
            )
        }

        // --- six faces, six measurements ---------------------------------------------------------
        var net = 0f
        for (k in 0 until 6) {
            SceneParts.vec(g, NS[k], NU[k], NA[k], nw)
            SceneParts.at(g, NS[k] * H, NU[k] * H, NA[k] * H, p)
            sample(kit, p[0], p[1], p[2], q, fv)
            val fl = (fv[0] * nw[0] + fv[1] * nw[1] + fv[2] * nw[2]) * AREA
            flux[k] = fl
            net += fl
            val col = if (fl >= 0f) SceneParts.TAKEN else SceneParts.COOL
            val mag = (abs(fl) / FLUX_REF).coerceAtMost(1f)

            // the cap: a translucent patch inset on the face, and its rim
            tangents(k, 2f * H * CAP)
            o[0] = p[0] - (t1[0] + t2[0]) * 0.5f
            o[1] = p[1] - (t1[1] + t2[1]) * 0.5f
            o[2] = p[2] - (t1[2] + t2[2]) * 0.5f
            tv[0] = MathMesh.quad(
                tri, tv[0], o[0], o[1], o[2],
                t1[0], t1[1], t1[2], t2[0], t2[1], t2[2],
                col[0], col[1], col[2], 0.10f + 0.48f * mag
            )
            if (kit.quality < 2) {
                v = SceneParts.edge(
                    line, v, o[0], o[1], o[2],
                    t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], col, 0.30f + 0.55f * mag
                )
            }

            // The whole field vector at the face, faint, straddling the face it crosses. It is
            // drawn beside the bold normal component so that the one is visibly a piece of the
            // other: flux is not how fast the field is, it is how much of it goes through.
            if (kit.quality == 0) {
                val ax = fv[0] * ARROW; val ay = fv[1] * ARROW; val az = fv[2] * ARROW
                v = MathMesh.arrow(
                    line, v, p[0] - ax * 0.5f, p[1] - ay * 0.5f, p[2] - az * 0.5f, ax, ay, az,
                    t1[0], t1[1], t1[2],
                    SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.38f, 0.34f
                )
            }

            // the probe reaching from the middle of the box to the middle of its face
            if (rods) {
                kit.rod(
                    g[0], g[1], g[2],
                    g[0] + (p[0] - g[0]) * ext, g[1] + (p[1] - g[1]) * ext, g[2] + (p[2] - g[2]) * ext,
                    0.019f, SceneParts.STEEL, col, 0.15f + 0.85f * mag * armLit
                )
            } else {
                v = MathMesh.segment(
                    line, v, g[0], g[1], g[2],
                    g[0] + (p[0] - g[0]) * ext, g[1] + (p[1] - g[1]) * ext, g[2] + (p[2] - g[2]) * ext,
                    SceneParts.STEEL[0], SceneParts.STEEL[1], SceneParts.STEEL[2],
                    0.20f * armLit, (0.30f + 0.55f * mag) * armLit
                )
            }
        }
        lastNet = net
        val div = net / VOL
        lastDiv = div

        // --- the net, as a level ------------------------------------------------------------------
        val lvl = (net / BAR_FULL).coerceIn(-1f, 1f) * BAR_H
        SceneParts.at(g, BAR_S, -BAR_H, 0f, o)
        SceneParts.vec(g, BAR_W, 0f, 0f, t1)
        SceneParts.vec(g, 0f, 2f * BAR_H, 0f, t2)
        v = SceneParts.edge(
            line, v, o[0], o[1], o[2],
            t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], SceneParts.CHALK, 0.20f
        )
        // The zero line runs proud of the bar on both sides: in the uniform case the fill vanishes
        // entirely and this is the only thing left, which is the correct picture of nothing.
        SceneParts.at(g, BAR_S - 0.07f, 0f, 0f, o)
        SceneParts.at(g, BAR_S + BAR_W + 0.07f, 0f, 0f, p)
        v = MathMesh.segment(
            line, v, o[0], o[1], o[2], p[0], p[1], p[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.85f
        )
        val netCol = if (net >= 0f) SceneParts.TAKEN else SceneParts.COOL
        if (abs(lvl) > 0.004f) {
            SceneParts.at(g, BAR_S, 0f, 0f, o)
            SceneParts.vec(g, BAR_W, 0f, 0f, t1)
            SceneParts.vec(g, 0f, lvl, 0f, t2)
            tv[0] = MathMesh.quad(
                tri, tv[0], o[0], o[1], o[2],
                t1[0], t1[1], t1[2], t2[0], t2[1], t2[2],
                netCol[0], netCol[1], netCol[2], 0.55f
            )
            SceneParts.at(g, BAR_S, lvl, 0f, o)
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], o[0] + t1[0], o[1] + t1[1], o[2] + t1[2],
                netCol[0], netCol[1], netCol[2], 1f
            )
        }

        kit.flushLines(v, 2.2f)

        // --- and the measurement itself, thicker, so it reads over its own scaffolding -------------
        // The normal component only: outward for a face that is losing, inward for one that is
        // gaining. No resampling — these come straight back out of the six numbers just measured.
        var w = 0
        for (k in 0 until 6) {
            val vn = flux[k] / AREA
            if (abs(vn) < 0.02f) continue
            SceneParts.vec(g, NS[k], NU[k], NA[k], nw)
            SceneParts.at(g, NS[k] * H, NU[k] * H, NA[k] * H, p)
            tangents(k, 1f)
            val col = if (flux[k] >= 0f) SceneParts.TAKEN else SceneParts.COOL
            val mag = (abs(flux[k]) / FLUX_REF).coerceAtMost(1f)
            val s = vn * ARROW
            w = MathMesh.arrow(
                line, w, p[0], p[1], p[2], nw[0] * s, nw[1] * s, nw[2] * s,
                t1[0], t1[1], t1[2], col[0], col[1], col[2], 0.55f + 0.45f * mag, 0.36f
            )
        }
        kit.flushLines(w, 3.6f)
        kit.flushTris(tv[0])

        // --- what the rig is injecting, if anything -------------------------------------------------
        // A marker, not a monopole. It grows and glows with the expansion so that "there is a source
        // in there" and "the caps have all gone red" are one event rather than two.
        val amt = (abs(q) / Q).coerceAtMost(1f)
        val bead = 0.045f + 0.075f * amt
        val bcol = if (q > 0.05f) SceneParts.TAKEN else if (q < -0.05f) SceneParts.COOL else SceneParts.STEEL
        kit.ball(
            g[0], g[1], g[2], bead, bead, bead, bcol, SceneParts.HOT,
            0.45f + 0.55f * amt, 0f, 0f, 1f, 0f, 0f,
            0.5f + 2.2f * amt + 0.8f * kit.beat, kit.quality > 0
        )

        // --- notation ---------------------------------------------------------------------------
        // Both labels hang outboard of the bar, which is the thing they name. Nothing goes above or
        // below the figure: the telemetry owns the top of the eye and the caption owns the bottom.
        SceneParts.at(g, BAR_S + BAR_W + 0.10f, 0f, 0f, o)
        val claim = if (div > 0.15f) CLAIM_POS else if (div < -0.15f) CLAIM_NEG else CLAIM_NIL
        val ccol = if (div > 0.15f) SceneParts.TAKEN else if (div < -0.15f) SceneParts.COOL else SceneParts.CHALK
        kit.text(claim, o[0], o[1], o[2], 0.20f, ccol, 1f, GlyphBoard.Style.MATH, 1f, -0.5f, 0.55f)
        if (kit.quality == 0) {
            kit.text(
                FLUX_LABEL, o[0], o[1], o[2], 0.15f, SceneParts.CHALK, 0.75f,
                GlyphBoard.Style.MATH, 1f, -0.5f, -1.05f
            )
        }
    }
}
