package com.rayneo.mathcosmos

/**
 * Stop 3 — THE HALVING ROOM. "Halve the remaining distance forever and you fill exactly the room,
 * never more."
 *
 * The flagship of Tour IV, and the picture the rest of the tour is built to earn. A room whose
 * length is exactly one. A marker starts at the near end and, on every beat, crosses half of
 * whatever is left. The floor behind it fills with a slab — a half, then a quarter, then an
 * eighth — and the slabs tile the floor with no gap and no overlap, because that is the entire
 * content of 1/2 + 1/4 + 1/8 + ⋯ = 1. The far wall is approached and never touched.
 *
 * The room is a shallow box rather than a flat diagram, which is the one staging decision worth
 * defending. In stereo, a box you look INTO is the difference between "a picture of a series" and
 * "a room whose far end I can see", and the accumulation point only reads as a place if it IS a
 * place: the landing marks on the floor bunch geometrically into the far wall until the last of
 * them are a single bright line. That crowding is the best available picture of convergence, and
 * it costs two dozen line vertices.
 *
 * The whole thing sits to one side of the rail and is about 1.7 units long by 0.9 deep, so the
 * craft passes the room's open long side and looks in along its length, instead of flying through
 * the middle of it and seeing one corner.
 *
 * Two honest approximations, both of which the crew says out loud:
 *
 *   The MARKER IS NOT THE SHIP. The script's honesty beat is that the Caliper does arrive at the
 *   wall, because it never slowed down; it is the sum of the SLABS that is finite, and those are
 *   two different statements. So the cycle ends by sending a second marker across at constant
 *   speed, straight through the wall and out the far side under the rail, laying evenly spaced
 *   ticks behind it. Even ticks against crowding floor marks is the whole distinction, drawn.
 *
 *   The series is truncated at twelve terms. Somewhere past the eighth the slab is thinner than a
 *   pixel at any range the craft ever gets to. The picture runs out before the mathematics does,
 *   which is the truth about every drawing of an infinite sum; the numbers that must be READ go
 *   to the HUD via [readout] instead, where they stay legible all the way down.
 */
object SceneHalvingRoom : MathScene {

    override val reach = 1.5f

    // A room of length exactly 1, in world units. The stop's passage radius is 2.8, so the budget
    // is 2.24 from the rail: the room reaches from side -2.05 out to -0.35, and its top sits
    // barely above the rail. Nothing here is anywhere near the wall.
    private const val ROOM = 1.70f
    private const val DEPTH = 0.90f
    private const val H_MAX = 0.62f
    private const val SIDE = -1.20f
    private const val UP = -0.50f          // the floor hangs below the rail, so you look down into it
    private const val OFF = -ROOM * 0.5f   // figure s = 0 is the near end, so the room centres on the stage

    private const val PERIOD = 26f
    private const val T_LAY = 0.05f        // a beat of empty room first, so the reset is legible
    private const val LAY_SPAN = 0.67f
    private const val T_SHIP = 0.74f
    private const val SHIP_SPAN = 0.13f
    private const val JUMPS = 12
    private const val MARKS = 14

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val o = FloatArray(3)
    private val du = FloatArray(3)
    private val dv = FloatArray(3)
    private val dw = FloatArray(3)
    private val tv = IntArray(1)

    /** 2^-k, exactly, for any k the scene can reach. The one number the whole room is built from. */
    private fun half(k: Int) = 1f / (1 shl k.coerceIn(0, 30))

    private fun jumps(quality: Int) = when (quality) { 0 -> JUMPS; 1 -> 7; else -> 5 }
    private fun marks(quality: Int) = when (quality) { 0 -> MARKS; 1 -> 8; else -> 5 }

    /**
     * The gap is what this stop measures, and it is the tour's own cut unit here. It halves faster
     * than any 3D label could keep up with, which is exactly why it belongs on the HUD.
     */
    override fun readout(kit: SceneKit): String? {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        if (c >= T_SHIP && c < T_SHIP + SHIP_SPAN) return "SLABS 1.000   THE SHIP DOES NOT SLOW"
        val jn = jumps(kit.quality)
        val done = (((c - T_LAY) / LAY_SPAN).coerceIn(0f, 1f) * jn).toInt().coerceAtMost(jn)
        if (done >= jn) return "SUM → 1   GAP → 0"
        val d = 1 shl done
        return "SUM %.5f   GAP %s".format(
            java.util.Locale.US, 1.0 - 1.0 / d, if (done == 0) "1" else "1/$d"
        )
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        SceneParts.stage(kit, at, SIDE, UP, f, g)

        // The room's height is taken from the corridor's own roof rather than from a constant. The
        // ambient scene draws that curve — this scene must not — but reading it means the room can
        // never poke through the ceiling if the trace is ever re-cut, and it stops the box from
        // fighting the one piece of scenery that is always overhead in this tour.
        val roof = kit.traceHeight(at)
        val hh = if (roof > 0.01f) (roof * 0.32f).coerceIn(0.34f, H_MAX) else H_MAX

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        val jn = jumps(kit.quality)
        val lay = ((c - T_LAY) / LAY_SPAN).coerceIn(0f, 1f) * jn
        val done = lay.toInt().coerceAtMost(jn)
        val since = lay - done
        // The crossing fills the BACK of each beat, so the landing falls exactly on the beat and
        // the flash that follows belongs to the slot after it. Land, hold, cross, land: a
        // metronome, which is what the INFINITE ambience is already ticking underneath.
        val frac = if (done >= jn) 0f else SceneParts.ease((since - 0.38f) / 0.62f)
        val pos = 1f - half(done) * (1f - 0.5f * frac)

        // --- the room itself -------------------------------------------------------------------
        // Twelve edges and nothing else. A filled ceiling or a filled near wall would hide the one
        // thing worth seeing, and on a waveguide a wireframe box already reads as an interior.
        SceneParts.at(g, 0f, hh * 0.5f, 0f, o)
        SceneParts.vec(g, ROOM * 0.5f, 0f, 0f, du)
        SceneParts.vec(g, 0f, hh * 0.5f, 0f, dv)
        SceneParts.vec(g, 0f, 0f, DEPTH * 0.5f, dw)
        v = MathMesh.boxEdges(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], dw[0], dw[1], dw[2],
            SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], 0.30f
        )

        // --- the far wall: the thing that is never reached ---------------------------------------
        // It breathes with the sound cue rather than sitting still, because it is the limit and the
        // limit is the only object in the room that never changes.
        val puls = (0.58f + 0.22f * kotlin.math.sin(kit.seconds * 1.6f) + kit.beat * 0.35f).coerceIn(0f, 1f)
        SceneParts.at(g, OFF + ROOM, 0f, -DEPTH * 0.5f, o)
        SceneParts.vec(g, 0f, hh, 0f, du)
        SceneParts.vec(g, 0f, 0f, DEPTH, dv)
        tv[0] = SceneParts.fill(
            tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.HOT, 0.14f
        )
        v = SceneParts.edge(
            line, v, o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
            SceneParts.HOT, puls
        )

        // --- the floor still to be covered -------------------------------------------------------
        // Cool, because it is the remainder: in this tour the passage radius means the same thing.
        if (pos < 0.9995f) {
            SceneParts.at(g, OFF + pos * ROOM, 0.001f, -DEPTH * 0.5f, o)
            SceneParts.vec(g, (1f - pos) * ROOM, 0f, 0f, du)
            SceneParts.vec(g, 0f, 0f, DEPTH, dv)
            tv[0] = SceneParts.fill(
                tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2],
                SceneParts.COOL, 0.20f
            )
        }

        // --- the slabs -----------------------------------------------------------------------------
        // Slab k runs from 1 − 2^-k to 1 − 2^-(k+1); the one being laid runs to wherever the marker
        // has got to, so the ground fills as it is covered rather than appearing after the fact.
        // Alternating warm and dim-warm is the cheapest way to keep neighbours distinguishable once
        // they are narrower than the line that would otherwise bound them.
        for (k in 0..done) {
            if (k >= jn) break
            val a0 = 1f - half(k)
            val a1 = if (k < done) 1f - half(k + 1) else pos
            val w = (a1 - a0) * ROOM
            if (w < 0.0006f) continue
            val col = if (k and 1 == 0) SceneParts.WORK else SceneParts.WORK_DIM
            SceneParts.at(g, OFF + a0 * ROOM, 0.003f, -DEPTH * 0.5f, o)
            SceneParts.vec(g, w, 0f, 0f, du)
            SceneParts.vec(g, 0f, 0f, DEPTH, dv)
            tv[0] = SceneParts.fill(
                tri, tv[0], o[0], o[1], o[2], du[0], du[1], du[2], dv[0], dv[1], dv[2], col, 0.42f
            )
        }

        // --- the landing marks ----------------------------------------------------------------------
        // Every landing point, drawn whether it has been reached yet or not: bright behind the
        // marker, faint ahead of it. They bunch into the far wall until the last few are one line.
        // Infinitely many marks crammed into the last stretch, which is the picture the stop is for.
        val mn = marks(kit.quality)
        for (m in 1..mn) {
            val s = 1f - half(m)
            SceneParts.at(g, OFF + s * ROOM, 0.004f, -DEPTH * 0.5f, o)
            SceneParts.at(g, OFF + s * ROOM, 0.004f, DEPTH * 0.5f, du)
            val hit = m <= done
            val col = if (hit) SceneParts.CHALK else SceneParts.COOL
            v = MathMesh.segment(
                line, v, o[0], o[1], o[2], du[0], du[1], du[2],
                col[0], col[1], col[2], if (hit) 0.75f else 0.22f
            )
        }

        // --- the honesty beat -------------------------------------------------------------------------
        // A second marker at CONSTANT speed, through the wall and out under the rail, with evenly
        // spaced ticks behind it. Set against the crowding on the floor, even ticks are the whole
        // difference between "the journey ends" and "the sum is finite". The room does not change
        // while it passes: the slabs still stop at one.
        var shipA = 0f
        val shipT = (c - T_SHIP) / SHIP_SPAN
        if (shipT > 0f && shipT < 1f) {
            shipA = ((1f - shipT) / 0.18f).coerceIn(0f, 1f)
            val s = shipT * 1.18f
            SceneParts.at(g, OFF, hh * 0.62f, 0f, o)
            SceneParts.at(g, OFF + s * ROOM, hh * 0.62f, 0f, dw)
            v = MathMesh.dashed(
                line, v, o[0], o[1], o[2], dw[0], dw[1], dw[2],
                (s * 11f).toInt().coerceAtLeast(1),
                SceneParts.COOL[0], SceneParts.COOL[1], SceneParts.COOL[2], shipA * 0.75f
            )
        }

        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // --- the marker ---------------------------------------------------------------------------------
        // The landing pulse decays across the beat that follows it. Once the last jump is spent,
        // `since` sticks at zero, so the flash has to be switched off explicitly or the marker
        // sits blazing through the whole rest beat.
        val flash = if (done >= jn) 0f else (1f - since * 4f).coerceAtLeast(0f)
        val r = 0.050f + 0.012f * flash
        SceneParts.at(g, OFF + pos * ROOM, hh * 0.20f, 0f, o)
        kit.ball(
            o[0], o[1], o[2], r, r, r, SceneParts.HOT, SceneParts.WORK, 1f,
            0f, 0f, 1f, 0f, 0f, 1.4f + 3.0f * flash + kit.beat * 1.2f
        )
        if (shipA > 0.01f) {
            kit.ball(
                dw[0], dw[1], dw[2], 0.045f, 0.045f, 0.045f, SceneParts.COOL, SceneParts.CHALK,
                shipA, 0f, 0f, 1f, 0f, 0f, 1.8f
            )
        }

        // --- notation ---------------------------------------------------------------------------------
        // The room IS the unit interval, so its two ends are its two numbers, and they go BESIDE the
        // figure at either end. Nothing goes over it: the room is wide and flat, the telemetry owns
        // the top of the eye and the caption box the bottom, and anything hung above a figure shaped
        // like this one lands in one of them.
        SceneParts.at(g, OFF + ROOM + 0.08f, hh * 0.70f, 0f, o)
        kit.text("1", o[0], o[1], o[2], 0.23f, SceneParts.HOT, 1f, GlyphBoard.Style.PLAIN, 1.2f, anchor = -0.5f)

        if (kit.quality > 0) return

        SceneParts.at(g, OFF - 0.08f, hh * 0.70f, 0f, o)
        kit.text("0", o[0], o[1], o[2], 0.20f, SceneParts.CHALK, 0.85f, GlyphBoard.Style.PLAIN, 1f, anchor = 0.5f)

        // Only the first two tiles are named. The third is already narrower than the string "1/8"
        // would be drawn at a readable height, and letting the lettering run out exactly where the
        // geometry does is a better argument than shrinking the type to keep up with it.
        if (done >= 1) {
            SceneParts.at(g, OFF + 0.25f * ROOM, hh * 0.55f, 0f, o)
            kit.text("1/2", o[0], o[1], o[2], 0.17f, SceneParts.WORK, 0.95f)
        }
        if (done >= 2) {
            SceneParts.at(g, OFF + 0.625f * ROOM, hh * 0.55f, 0f, o)
            kit.text("1/4", o[0], o[1], o[2], 0.17f, SceneParts.WORK_DIM, 0.95f)
        }
        // And the remainder, named only while there is still room to name it.
        if (pos < 0.56f) {
            SceneParts.at(g, OFF + (pos + 1f) * 0.5f * ROOM, hh * 0.30f, 0f, o)
            kit.text("2^{-n}", o[0], o[1], o[2], 0.16f, SceneParts.COOL, 0.90f)
        }
    }
}
