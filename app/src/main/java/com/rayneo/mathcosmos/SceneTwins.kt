package com.rayneo.mathcosmos

/**
 * THE TWINS — the same body at 1, 2 and 3, with its skin and its bulk counted out beside it.
 *
 * Three balls stand in a row along the rail, of radius one, two and three of the same unit, and
 * the craft flies past their flank. Beside each one is a ledger drawn on a single horizontal
 * line: ABOVE the line a wall of unit tiles, 1 then 4 then 9; BELOW it a stack of unit cubes,
 * 1 then 8 then 27. Nothing here is asserted. Both quantities are counted into place one item at
 * a time, and the cycle empties and counts them again, because the craft comes past this stop
 * repeatedly and the counting IS the argument.
 *
 * The solid is a ball rather than a box, and that choice is worth defending. A box would make
 * the counting a tautology — a 3-box literally dissects into 27 unit boxes and one of its faces
 * literally rules into 9 unit tiles — and a viewer would leave thinking the law is a fact about
 * boxes. It is not. Scale ANY body by k, a ball or a beetle or a bone, and every area on it
 * scales by k squared and every volume by k cubed, because an area is two lengths multiplied and
 * a volume is three. So the tiles here are a TALLY and not a net: the unit tile is the small
 * twin's own whole skin, and the unit cube is the small twin's own whole bulk. Nine tiles beside
 * the big twin says its skin is nine times the little one's, which is exact for a sphere and for
 * anything else; twenty-seven cubes says its bulk is twenty-seven times, likewise.
 *
 * The stack has to be three deep along the RAIL axis, and that is why this stop belongs in a
 * stereoscopic tour at all. On paper 27 collapses to a 3 by 3 square of nine with a note reading
 * "three deep"; through the waveguides the third layer is simply there behind the second, and
 * the jump from a wall of 9 to a solid of 27 is something the eyes do rather than something the
 * caption claims. So the cubes fill one depth layer at a time — a full k by k face, then another
 * behind it, then another — which is the proof in the order it is drawn: bulk is skin times depth.
 *
 * Why big animals are shaped differently from small ones then follows without being stated: a
 * body that doubles must feed eight times the bulk through four times the skin.
 */
object SceneTwins : MathScene {

    // The row is wide but shallow, so it wants an early fade-in and only a little depth grace.
    override val reach = 1.4f
    override val deep = 0.3f

    // Cool for skin, warm for bulk. Keeping area and volume in permanently different colours is
    // what lets the two ledgers be read apart at a glance from a passing craft.
    private val BODY = floatArrayOf(0.86f, 0.90f, 1.00f, 1f)
    private val BODY_HOT = floatArrayOf(1.00f, 0.97f, 0.88f, 1f)
    private val SKIN = floatArrayOf(0.55f, 0.86f, 1.00f, 1f)
    private val BULK = floatArrayOf(1.00f, 0.74f, 0.38f, 1f)
    private val GROUND = floatArrayOf(0.42f, 0.50f, 0.70f, 1f)
    private val LABEL = floatArrayOf(0.90f, 0.93f, 1.00f, 1f)

    // Layout, in the stage's own (side, up, rail) units. The whole assembly is pushed to one
    // side so the craft flies BESIDE the row rather than through it: seen from alongside, the
    // three bodies line up against each other, which is the comparison the stop is making.
    // Worst case is the big twin's outer flank at about 3.2 from the rail centre, inside the
    // 0.8 x 4.2 = 3.36 the passage allows here.
    private const val SIDE = -1.75f
    private const val UP = -0.15f
    private const val T = 0.34f          // one unit of skin, and the edge of one unit of bulk
    private const val R = 0.22f          // the small twin's radius; the others are 2R and 3R
    private const val BALL_S = -0.72f    // the bodies stand in this column
    private const val COL_S = 0.12f      // the ledgers stand in this one, nearer the rail
    private const val PERIOD = 24f

    // Where each twin stands along the rail, and the cycle fractions at which it is born, at
    // which its tiles begin arriving, and at which its cubes do. The windows lengthen with k
    // because the counts do: nine tiles and twenty-seven cubes need longer than one and one, and
    // a constant per-item rate is what makes the third twin FEEL like the expensive one.
    private val AT = floatArrayOf(-2.4f, 0f, 2.8f)
    private val GROW = floatArrayOf(0.02f, 0.24f, 0.52f)
    private val TILE_AT = floatArrayOf(0.08f, 0.30f, 0.58f)
    private val TILE_LEN = floatArrayOf(0.05f, 0.07f, 0.09f)
    private val CUBE_AT = floatArrayOf(0.13f, 0.37f, 0.67f)
    private val CUBE_LEN = floatArrayOf(0.05f, 0.09f, 0.14f)

    // Every string this scene can ever show, built once. readout() is called every frame, so a
    // formatted count would allocate thirty times a second on a device that reboots when hot;
    // instead the HUD names the totals of whichever twin is currently on the bench.
    private val SCALE = arrayOf("1", "2", "3")
    private val PAIR = arrayOf("1  1", "4  8", "9  27")
    private val READ = arrayOf("SKIN 1   BULK 1", "SKIN 4   BULK 8", "SKIN 9   BULK 27")

    // Scratch. Nothing below allocates.
    private val f = FloatArray(12)
    private val g = FloatArray(12)
    private val p = FloatArray(3)
    private val q = FloatArray(3)
    private val es = FloatArray(3)      // one unit along the stage's side, as a world vector
    private val eu = FloatArray(3)      // one unit up
    private val ea = FloatArray(3)      // one unit along the rail — the depth the stop is about
    private val tv = IntArray(1)        // the triangle cursor SceneParts.pane writes through

    override fun readout(kit: SceneKit): String {
        val c = SceneParts.cycle(kit.seconds, PERIOD)
        return READ[if (c < TILE_AT[1]) 0 else if (c < TILE_AT[2]) 1 else 2]
    }

    override fun draw(kit: SceneKit, n: TourNode, i: Int) {
        SceneParts.stage(kit, i.toFloat(), SIDE, UP, f, g)
        SceneParts.vec(g, T, 0f, 0f, es)
        SceneParts.vec(g, 0f, T, 0f, eu)
        SceneParts.vec(g, 0f, 0f, T, ea)

        val c = SceneParts.cycle(kit.seconds, PERIOD)
        // The row is cleared at the end of the cycle rather than cut, so a viewer who arrives
        // mid-loop sees the counting start again instead of the row blinking out from under them.
        val alive = 1f - SceneParts.step(c, 0.93f, 0.06f)

        val line = kit.lineBuf
        val tri = kit.triBuf
        var v = 0
        tv[0] = 0

        // The datum the three ledgers hang on: skin above it, bulk below it, all three twins
        // standing on the same line so the comparison is between bodies and not between plinths.
        if (kit.quality < 2) {
            SceneParts.at(g, COL_S - 0.06f, 0f, -3.4f, p)
            SceneParts.at(g, COL_S - 0.06f, 0f, 3.9f, q)
            v = MathMesh.segment(
                line, v, p[0], p[1], p[2], q[0], q[1], q[2],
                GROUND[0], GROUND[1], GROUND[2], 0.40f * alive
            )
        }

        for (t in 0 until 3) {
            val k = t + 1
            val a = AT[t]
            val born = SceneParts.step(c, GROW[t], 0.06f)
            if (born <= 0.001f) continue
            val al = born * alive

            // A footing across this twin's station, so the ball is standing on the same line its
            // ledger is drawn on rather than floating beside it.
            if (kit.quality < 2) {
                SceneParts.at(g, BALL_S - k * R - 0.06f, 0f, a, p)
                SceneParts.at(g, COL_S + k * T + 0.08f, 0f, a, q)
                v = MathMesh.segment(
                    line, v, p[0], p[1], p[2], q[0], q[1], q[2],
                    GROUND[0], GROUND[1], GROUND[2], 0.55f * al
                )
            }

            // ---- the skin: a k by k wall of unit tiles, standing on the line ----------------
            // The empty frame arrives with the twin and the tiles are counted into it, so the
            // question "how many" is posed before it is answered.
            // This loop runs nine times at worst and is NOT thinned at reduced quality, unlike
            // the cubes: nine panes are nine handfuls of vertices in a buffer that flushes once,
            // so they cost nothing a governor cares about, and a wall of four tiles labelled 9
            // would be a lie rather than a saving.
            val ta = SceneParts.step(c, TILE_AT[t], TILE_LEN[t])
            SceneParts.at(g, COL_S, 0f, a, p)
            v = SceneParts.pane(
                kit, line, v, tri, tv, p[0], p[1], p[2],
                es[0] * k, es[1] * k, es[2] * k,
                eu[0] * k, eu[1] * k, eu[2] * k,
                SKIN, 0.22f * al, k, k
            )
            var j = 0
            for (row in 0 until k) {
                for (col in 0 until k) {
                    val e = (ta * k * k - j).coerceIn(0f, 1f); j++
                    if (e <= 0.001f) continue
                    // Each tile slides the last third of a unit up into its slot as it lands.
                    SceneParts.at(g, COL_S + col * T, row * T - (1f - e) * T * 0.35f, a, p)
                    v = SceneParts.pane(
                        kit, line, v, tri, tv, p[0], p[1], p[2],
                        es[0], es[1], es[2], eu[0], eu[1], eu[2],
                        SKIN, 0.95f * al * e
                    )
                }
            }

            // ---- the bulk: a k by k by k stack of unit cubes, hanging below the line ---------
            if (kit.quality < 2) {
                val ca = SceneParts.step(c, CUBE_AT[t], CUBE_LEN[t])
                val h = 0.43f                                   // half a cube, minus a hairline gap
                val rowsKept = if (kit.quality == 0) k else 1   // see the note in the row loop
                val total = k * k * rowsKept
                // The empty volume, so the three layers of depth are legible before anything
                // fills them. Only at full quality, where the stack really is k deep.
                if (kit.quality == 0) {
                    SceneParts.at(g, COL_S + k * T * 0.5f, -k * T * 0.5f, a, p)
                    v = MathMesh.boxEdges(
                        line, v, p[0], p[1], p[2],
                        es[0] * k * 0.5f, es[1] * k * 0.5f, es[2] * k * 0.5f,
                        eu[0] * k * 0.5f, eu[1] * k * 0.5f, eu[2] * k * 0.5f,
                        ea[0] * k * 0.5f, ea[1] * k * 0.5f, ea[2] * k * 0.5f,
                        BULK[0], BULK[1], BULK[2], 0.20f * al
                    )
                }
                var m = 0
                // Layer first, so a whole k by k face completes before the next one starts
                // behind it. Layer 0 is the one facing back down the rail, which is the face the
                // approaching craft meets first.
                for (layer in 0 until k) {
                    for (row in 0 until k) {
                        // At quality 1 the stack keeps its DEPTH and loses its height: k by k
                        // cubes lying as a footprint, k wide and k deep. Depth is the one thing
                        // a flat picture cannot show, so it is the last thing to give up.
                        if (kit.quality > 0 && row > 0) continue
                        for (col in 0 until k) {
                            val e = (ca * total - m).coerceIn(0f, 1f); m++
                            if (e <= 0.001f) continue
                            SceneParts.at(
                                g,
                                COL_S + (col + 0.5f) * T,
                                -(row + 0.5f) * T + (1f - e) * T * 0.35f,
                                a + (layer + 0.5f - k * 0.5f) * T, p
                            )
                            v = MathMesh.boxEdges(
                                line, v, p[0], p[1], p[2],
                                es[0] * h, es[1] * h, es[2] * h,
                                eu[0] * h, eu[1] * h, eu[2] * h,
                                ea[0] * h, ea[1] * h, ea[2] * h,
                                BULK[0], BULK[1], BULK[2],
                                0.92f * al * e * (1f - 0.14f * layer)
                            )
                        }
                    }
                }
            }
        }
        kit.flushLines(v, 2.2f)
        kit.flushTris(tv[0])

        // ---- the bodies -----------------------------------------------------------------
        // Drawn after the ledgers because they are the thing being measured, and three lit balls
        // are three draw calls, which is all this scene can afford to spend on solids.
        for (t in 0 until 3) {
            val k = t + 1
            val a = AT[t]
            val born = SceneParts.step(c, GROW[t], 0.06f)
            if (born <= 0.001f) continue
            val al = born * alive
            // It grows UP out of the line rather than swelling about a fixed centre, so its feet
            // stay on the ground and only its size changes — which is the whole comparison.
            val r = k * R * (0.25f + 0.75f * born)
            SceneParts.at(g, BALL_S, r, a, p)
            kit.ball(
                p[0], p[1], p[2], r, r, r, BODY, BODY_HOT, al,
                0f, 0f, 1f, 0f, 0f, 0.30f + 0.45f * kit.beat,
                small = !(kit.quality == 0 && k == 3)
            )
        }

        // ---- notation ---------------------------------------------------------------------
        for (t in 0 until 3) {
            val k = t + 1
            val a = AT[t]
            val born = SceneParts.step(c, GROW[t], 0.06f)
            if (born <= 0.001f) continue
            val al = born * alive
            // The scale, large, over the body's head. This is the only number the stop truly
            // needs: everything else on screen is the consequence of it.
            SceneParts.at(g, BALL_S, 2f * k * R + 0.32f, a, p)
            kit.text(SCALE[t], p[0], p[1], p[2], 0.36f, LABEL, 0.95f * al, GlyphBoard.Style.MATH, 1.2f)

            // The two counts, small, hung just in front of the ledger along the rail so they sit
            // in clear air between the twins rather than over the tiles. Tiles first, cubes
            // second, in the order the ledger reads: above the line, then below it.
            if (kit.quality == 0) {
                SceneParts.at(g, COL_S + k * T * 0.5f, 0f, a - k * T * 0.5f - 0.34f, p)
                kit.text(PAIR[t], p[0], p[1], p[2], 0.23f, LABEL, 0.85f * al, GlyphBoard.Style.SMALL, 1f)
            }
        }
    }
}
