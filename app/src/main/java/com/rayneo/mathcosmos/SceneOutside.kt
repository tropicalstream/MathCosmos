package com.rayneo.mathcosmos

import kotlin.math.cos
import kotlin.math.sin

/**
 * THE VIEW FROM OUTSIDE — the last stop of EVERY tour in the series. "All of that was one flat picture; I was inside it."
 *
 * The closing move of every tour in the series. The passage wall has already gone to nothing (the
 * stop's own wallAlpha does that, not this scene), and what is left is the rail itself, hanging in
 * a dark volume as one thin bright thread with a bead at every stop the craft has visited. The
 * corridor that felt like a place for half an hour turns out to have been a line on a diagram.
 *
 * It is worth being clear about why this stop earns its place rather than being a victory lap: for
 * the whole tour the inset map in the corner of the eye has shown a flat drawing of the tour, and
 * the viewer has had no reason to connect it to the world outside the window. Here they become
 * visibly the same object. The inset was the outside view all along.
 *
 * The star field is drawn as small crosses in the line buffer rather than as points, because the
 * kit deliberately offers no point path — one draw call for six hundred strokes is cheaper than a
 * second shader state, and on the waveguides a cross reads more like a distant light than a dot.
 */
object SceneOutside : MathScene {

    override val reach = 2.4f          // it is the whole tour: it should appear early
    // The model of the tour hangs ahead of the last stop, so that is where the camera looks.
    override val focusSide = 0f
    override val focusUp = 0.35f
    override val focusAhead = MODEL_AHEAD
    override val focusRadius = 3.2f
    override val deep = 0f

    private const val STARS = 220
    private const val PERIOD = 34f
    /** The model's length across the view, and how far ahead of the craft it hangs. */
    private const val MODEL_LEN = 5.6f
    private const val MODEL_AHEAD = 5.0f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val a = FloatArray(3)

    // A fixed star field, generated once from a plain integer hash so it never changes between
    // frames and never allocates.
    private val sx = FloatArray(STARS)
    private val sy = FloatArray(STARS)
    private val sz = FloatArray(STARS)
    private val sb = FloatArray(STARS)
    private var built = false

    private fun hash(i: Int, salt: Int): Float {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7fffffff).toFloat() / 0x7fffffff.toFloat()
    }

    private fun build() {
        if (built) return
        for (k in 0 until STARS) {
            // A shell around the tour rather than a box, so there is no visible corner to the sky.
            val u = hash(k, 1) * 2f - 1f
            val th = hash(k, 2) * 6.2831853f
            val r = 40f + hash(k, 3) * 55f
            val p = kotlin.math.sqrt((1f - u * u).coerceAtLeast(0f))
            sx[k] = r * p * cos(th)
            sy[k] = r * p * sin(th) * 0.55f          // flattened: the tour is long, not tall
            sz[k] = r * u - 95f                       // centred on the middle of the rail
            sb[k] = 0.25f + hash(k, 4) * 0.6f
        }
        built = true
    }

    override fun readout(kit: SceneKit): String? = "THE WHOLE TOUR   ${kit.stopCount} STOPS"

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        build()
        val line = kit.lineBuf
        var v = 0
        val c = SceneParts.cycle(kit.seconds, PERIOD)

        // --- the sky ------------------------------------------------------------------------
        val stars = if (kit.quality == 0) STARS else STARS / 2
        for (k in 0 until stars) {
            val tw = 0.75f + 0.25f * sin(kit.seconds * (0.6f + sb[k]) + k)
            val s = 0.55f + sb[k] * 0.5f
            val al = sb[k] * tw
            v = MathMesh.segment(line, v, sx[k] - s, sy[k], sz[k], sx[k] + s, sy[k], sz[k],
                0.80f, 0.86f, 1f, al)
            v = MathMesh.segment(line, v, sx[k], sy[k] - s, sz[k], sx[k], sy[k] + s, sz[k],
                0.80f, 0.86f, 1f, al * 0.7f)
        }

        // --- the tour, as a model hanging in front of you --------------------------------
        // The corridor really is behind the craft at this point — the last stop is at the far end
        // of it — so drawing the rail in place would put the whole reveal out of sight of every
        // forward-facing view. Instead the tour is laid out AS A MODEL a few units ahead: its
        // length across the stage's side axis, its weave kept as the vertical wiggle. That is
        // deliberately the same drawing as the inset in the corner of the eye, at last at a size
        // you can look at, which is the entire point of the stop.
        val last = i.toFloat()
        val steps = if (kit.quality == 0) 96 else 48
        SceneParts.stage(kit, last, 0f, 0.35f, this.f, g)
        var px = 0f; var py = 0f; var pz = 0f
        for (k in 0..steps) {
            val t = k.toFloat() / steps
            kit.frame(t * last, this.f)
            // The rail's own world x and y are its weave; scaled down they become the model's.
            SceneParts.at(g, (t - 0.5f) * MODEL_LEN, this.f[1] * 0.5f, MODEL_AHEAD, o)
            if (k > 0) {
                // A brightening runs the length of it, so the whole ride is re-flown in a few seconds.
                val head = ((c * 1.25f) - t + 1f) % 1f
                val glow = if (head < 0.12f) 1f - head / 0.12f else 0f
                v = MathMesh.segment(line, v, px, py, pz, o[0], o[1], o[2],
                    1f, 0.82f + 0.18f * glow, 0.55f + 0.45f * glow, 0.40f + 0.60f * glow)
            }
            px = o[0]; py = o[1]; pz = o[2]
        }
        kit.flushLines(v, 2.4f)

        // --- a bead at every stop -------------------------------------------------------------
        // Thirteen lit spheres is thirteen draw calls, affordable exactly once, here, because
        // nothing else in this scene uses the lit path.
        for (k in 0..i) {
            val t = if (i == 0) 0f else k.toFloat() / i
            kit.frame(k.toFloat(), this.f)
            SceneParts.at(g, (t - 0.5f) * MODEL_LEN, this.f[1] * 0.5f, MODEL_AHEAD, o)
            val pulse = 0.5f + 0.5f * sin(kit.seconds * 1.4f + k * 0.7f)
            kit.ball(o[0], o[1], o[2], 0.075f, 0.075f, 0.075f,
                SceneParts.WORK, SceneParts.HOT, 0.95f, 0f, 0f, 1f, 0f, 0f, 0.5f + 0.7f * pulse)
        }

        // --- notation ---------------------------------------------------------------------------
        // Only the two ends are named. The thread does the rest of the talking, and the labels sit
        // in the clear band: the telemetry owns the top of the eye and the caption box the bottom.
        SceneParts.at(g, -MODEL_LEN * 0.5f, -0.34f, MODEL_AHEAD, o)
        kit.text("START", o[0], o[1], o[2], 0.16f, SceneParts.CHALK, 0.85f,
            GlyphBoard.Style.SMALL, 1f)
        SceneParts.at(g, MODEL_LEN * 0.5f, -0.34f, MODEL_AHEAD, o)
        kit.text("THE VIEW FROM OUTSIDE", o[0], o[1], o[2], 0.16f, SceneParts.CHALK, 0.85f,
            GlyphBoard.Style.SMALL, 1f)
        SceneParts.at(g, 0f, 0.62f, MODEL_AHEAD, o)
        kit.text(kit.tourTitle, o[0], o[1], o[2], 0.26f, SceneParts.HOT, 0.95f,
            GlyphBoard.Style.TITLE, 1.15f)
    }
}
