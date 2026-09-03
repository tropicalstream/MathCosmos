package com.rayneo.mathcosmos

import kotlin.math.sin

/**
 * Stop 1 — THE UNIT. "A number is a length I can pick up and carry."
 *
 * The tour opens by choosing a unit, because that choice is the one piece of arithmetic that is
 * genuinely arbitrary and never gets said out loud. A single glowing rod hangs across the passage
 * with the craft's jaws at its ends; copies of it detach and lay themselves end to end along the
 * floor, one per ring, until there is a ruler under the ship. Nothing here is a diagram of
 * counting — it is counting, done at arm's length, by putting the same length down repeatedly.
 *
 * The ruler behind the craft stays lit and the floor ahead of it stays blank, so the numbers are
 * visibly something that has been LAID DOWN rather than something that was already there. That
 * asymmetry is the stop's only argument, and it is worth the extra fade term.
 *
 * Stops are 16 world units apart, so a mark at [a] world units along the rail sits at node
 * position i + a/16 — that is how a mark knows whether the craft has passed it yet.
 */
object SceneUnit : MathScene {

    override val reach = 1.5f
    // Frame the rod, not the ruler. The ruler runs seventeen units down the floor and framing all
    // of it would put the camera far enough back to lose the rod entirely — and the rod is the stop.
    override val focusSide = 0f
    override val focusUp = 0.72f
    override val focusRadius = 1.7f
    override val deep = 0.35f

    private const val MARKS = 10
    private const val SPAN = 1.7f          // one unit, in world units
    private const val PERIOD = 22f

    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val p0 = FloatArray(3)
    private val p1 = FloatArray(3)

    override fun readout(kit: SceneKit): String? = "UNIT 1   LAID ${laid(kit)}"

    private fun laid(kit: SceneKit): Int =
        (SceneParts.cycle(kit.seconds, PERIOD) * (MARKS + 1)).toInt().coerceIn(0, MARKS)

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        val at = i.toFloat()
        val r = kit.radius(at)
        val floor = -r * 0.44f
        SceneParts.stage(kit, at, 0f, 0f, f, g)

        val buf = kit.lineBuf
        var v = 0
        val cyc = SceneParts.cycle(kit.seconds, PERIOD)
        val count = laid(kit)

        // --- the ruler on the floor -------------------------------------------------------
        // Marks are laid from five units behind the stop to five ahead, one at a time.
        for (k in 0..MARKS) {
            if (k > count) break
            val a = -MARKS * 0.5f * SPAN + k * SPAN
            // Freshly laid marks flash, then settle. Marks the craft has already passed stay lit;
            // the floor ahead of it is dimmer, so the ruler reads as a trail rather than a track.
            val age = (cyc * (MARKS + 1)) - k
            val flash = if (age in 0f..1f) (1f - age) else 0f
            val passed = kit.progress > at + a / 16f
            val alpha = (if (passed) 0.95f else 0.42f) + flash * 0.6f

            SceneParts.at(g, -0.85f, floor, a, p0)
            SceneParts.at(g, 0.85f, floor, a, p1)
            v = MathMesh.segment(buf, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
                SceneParts.CHALK[0], SceneParts.CHALK[1], SceneParts.CHALK[2], alpha * 0.7f)

            // The tick that separates this unit from the next, standing up off the floor.
            SceneParts.at(g, 0.85f, floor, a - SPAN * 0.5f, p0)
            SceneParts.at(g, 0.85f, floor + 0.48f + flash * 0.6f, a - SPAN * 0.5f, p1)
            v = MathMesh.segment(buf, v, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2],
                SceneParts.WORK[0], SceneParts.WORK[1], SceneParts.WORK[2], alpha)
        }

        // --- the unit itself, held up across the passage ----------------------------------
        // It hovers where the jaws would hold it, breathing very slightly so it reads as held
        // rather than mounted.
        val hold = 0.75f + 0.04f * sin(kit.seconds * 0.9f)
        SceneParts.at(g, -SPAN * 0.5f, hold, 0f, p0)
        SceneParts.at(g, SPAN * 0.5f, hold, 0f, p1)
        kit.flushLines(v, 2.2f)

        val glow = 0.9f + 0.5f * sin(kit.seconds * 1.6f)
        kit.rod(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.085f, SceneParts.HOT, SceneParts.LAMP, glow)
        // Both ends capped, because a length is a thing WITH ENDS and that is the whole idea.
        kit.ball(p0[0], p0[1], p0[2], 0.15f, 0.15f, 0.15f, SceneParts.LAMP, SceneParts.HOT, 1f, 0f, 0f, 1f, 0f, 0f, 1.1f)
        kit.ball(p1[0], p1[1], p1[2], 0.15f, 0.15f, 0.15f, SceneParts.LAMP, SceneParts.HOT, 1f, 0f, 0f, 1f, 0f, 0f, 1.1f)

        // --- notation ---------------------------------------------------------------------
        SceneParts.at(g, 0f, hold + 0.34f, 0f, p0)
        kit.text("1", p0[0], p0[1], p0[2], 0.30f, SceneParts.HOT, 1f, GlyphBoard.Style.MATH, 1.2f)

        if (kit.quality == 0) {
            // The count under the ruler: the marks are numbered as they are laid.
            for (k in 0..count) {
                val a = -MARKS * 0.5f * SPAN + k * SPAN
                SceneParts.at(g, 1.25f, floor + 0.26f, a, p0)
                kit.text(k.toString(), p0[0], p0[1], p0[2], 0.21f, SceneParts.CHALK, 0.8f,
                    GlyphBoard.Style.SMALL, 0.9f)
            }
        }
    }
}
