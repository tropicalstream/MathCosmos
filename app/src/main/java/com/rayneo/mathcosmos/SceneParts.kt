package com.rayneo.mathcosmos

import kotlin.math.cos
import kotlin.math.sin

/**
 * The pieces the algebra stops are cut from.
 *
 * Nearly every stop in Tour I is the same physical act — a flat piece of area is made, cut, moved
 * and re-seated — so the pane is a first-class object here rather than something each scene builds
 * again. A pane is a translucent fill plus a bright edge, which is what reads as "a piece of area"
 * on a waveguide display; a lit solid would hide whatever is behind it in a corridor four units
 * wide, and area is the one thing in this tour that must stay visibly conserved.
 *
 * Everything takes two spanning vectors rather than a rotation, so a piece can be sheared, swung
 * or laid flat without any of the scenes needing a model matrix of its own.
 */
object SceneParts {

    // The tour's working palette. Warm for the thing being built, cool for the ground it is
    // built on, and one distinct colour for a piece that was ADDED, so a debt stays visible.
    // Every colour is four components: MathMesh and kit.text read the first three, but the lit
    // shader reads base[3] as an alpha multiplier, so a three-component colour would crash the
    // moment it was handed to kit.ball.
    val CHALK = floatArrayOf(0.86f, 0.90f, 1.00f, 1f)
    val WORK = floatArrayOf(1.00f, 0.78f, 0.42f, 1f)
    val WORK_DIM = floatArrayOf(0.62f, 0.46f, 0.24f, 1f)
    val ADDED = floatArrayOf(0.55f, 0.95f, 0.85f, 1f)
    val TAKEN = floatArrayOf(1.00f, 0.46f, 0.46f, 1f)
    val COOL = floatArrayOf(0.55f, 0.72f, 1.00f, 1f)
    val HOT = floatArrayOf(1.00f, 0.96f, 0.82f, 1f)
    val LAMP = floatArrayOf(1f, 0.77f, 0.42f, 1f)
    val STEEL = floatArrayOf(0.62f, 0.66f, 0.76f, 1f)

    /** Ease 0..1 with a soft start and stop — every animated move in this tour uses it. */
    fun ease(t: Float): Float { val u = t.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    /** A repeating clock of [period] seconds, as 0..1. Scenes are loops, not one-shots. */
    fun cycle(seconds: Float, period: Float): Float = ((seconds % period) + period) % period / period

    /**
     * Hold at 0, ease to 1, hold at 1: a move that happens once per cycle and is still long
     * enough afterwards to look at. [at] and [len] are fractions of the cycle.
     */
    fun step(c: Float, at: Float, len: Float): Float = ease((c - at) / len)

    /** The translucent face of a piece of area, as two triangles from a corner and two edges. */
    fun fill(
        tri: FloatArray, v: Int,
        ox: Float, oy: Float, oz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        c: FloatArray, alpha: Float
    ): Int = MathMesh.quad(tri, v, ox, oy, oz, ux, uy, uz, vx, vy, vz, c[0], c[1], c[2], alpha)

    /** The bright rim of a piece of area: four segments round the same corner and two edges. */
    fun edge(
        line: FloatArray, v: Int,
        ox: Float, oy: Float, oz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        c: FloatArray, alpha: Float
    ): Int {
        var k = v
        val ax = ox; val ay = oy; val az = oz
        val bx = ox + ux; val by = oy + uy; val bz = oz + uz
        val cx = ox + ux + vx; val cy = oy + uy + vy; val cz = oz + uz + vz
        val dx = ox + vx; val dy = oy + vy; val dz = oz + vz
        k = MathMesh.segment(line, k, ax, ay, az, bx, by, bz, c[0], c[1], c[2], alpha)
        k = MathMesh.segment(line, k, bx, by, bz, cx, cy, cz, c[0], c[1], c[2], alpha)
        k = MathMesh.segment(line, k, cx, cy, cz, dx, dy, dz, c[0], c[1], c[2], alpha)
        k = MathMesh.segment(line, k, dx, dy, dz, ax, ay, az, c[0], c[1], c[2], alpha)
        return k
    }

    /**
     * A piece of area ruled into unit cells, so its size can be COUNTED rather than asserted.
     * This is the whole argument of the first half of Tour I: the identity is true because the
     * same tiles are there before and after.
     */
    fun rule(
        line: FloatArray, v: Int,
        ox: Float, oy: Float, oz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        nu: Int, nv: Int, c: FloatArray, alpha: Float
    ): Int {
        var k = v
        for (i in 1 until nu) {
            val t = i.toFloat() / nu
            k = MathMesh.segment(
                line, k, ox + ux * t, oy + uy * t, oz + uz * t,
                ox + ux * t + vx, oy + uy * t + vy, oz + uz * t + vz, c[0], c[1], c[2], alpha
            )
        }
        for (j in 1 until nv) {
            val t = j.toFloat() / nv
            k = MathMesh.segment(
                line, k, ox + vx * t, oy + vy * t, oz + vz * t,
                ox + vx * t + ux, oy + vy * t + uy, oz + vz * t + uz, c[0], c[1], c[2], alpha
            )
        }
        return k
    }

    /** A whole piece: fill, rim, and optionally its unit ruling. Returns the new line count. */
    fun pane(
        kit: SceneKit, line: FloatArray, lv: Int, tri: FloatArray, tv: IntArray,
        ox: Float, oy: Float, oz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        c: FloatArray, alpha: Float, nu: Int = 0, nv: Int = 0
    ): Int {
        tv[0] = fill(tri, tv[0], ox, oy, oz, ux, uy, uz, vx, vy, vz, c, alpha * 0.22f)
        var k = edge(line, lv, ox, oy, oz, ux, uy, uz, vx, vy, vz, c, alpha)
        if (nu > 0 && nv > 0 && kit.quality == 0) {
            k = rule(line, k, ox, oy, oz, ux, uy, uz, vx, vy, vz, nu, nv, c, alpha * 0.30f)
        }
        return k
    }

    /**
     * The frame at a stop, with the plane every algebra scene works in already picked out:
     * out[0..2] the centre pushed [side] right and [up] high, out[3..5] the plane's right
     * (the rail's side), out[6..8] the plane's up, out[9..11] the rail's forward.
     */
    fun stage(kit: SceneKit, at: Float, side: Float, up: Float, f: FloatArray, out: FloatArray) {
        kit.frame(at, f)
        out[0] = f[0] + f[6] * side + f[9] * up
        out[1] = f[1] + f[7] * side + f[10] * up
        out[2] = f[2] + f[8] * side + f[11] * up
        out[3] = f[6]; out[4] = f[7]; out[5] = f[8]
        out[6] = f[9]; out[7] = f[10]; out[8] = f[11]
        out[9] = f[3]; out[10] = f[4]; out[11] = f[5]
    }

    /** A point in a stage's plane: [s] along its right, [u] along its up, [a] along the rail. */
    inline fun at(g: FloatArray, s: Float, u: Float, a: Float, out: FloatArray) {
        out[0] = g[0] + g[3] * s + g[6] * u + g[9] * a
        out[1] = g[1] + g[4] * s + g[7] * u + g[10] * a
        out[2] = g[2] + g[5] * s + g[8] * u + g[11] * a
    }

    /** A vector in a stage's plane, written into [out]. */
    inline fun vec(g: FloatArray, s: Float, u: Float, a: Float, out: FloatArray) {
        out[0] = g[3] * s + g[6] * u + g[9] * a
        out[1] = g[4] * s + g[7] * u + g[10] * a
        out[2] = g[5] * s + g[8] * u + g[11] * a
    }

    /** A spanning vector turned by [deg] within the stage's plane. */
    fun turn(g: FloatArray, s: Float, u: Float, deg: Float, out: FloatArray) {
        val r = deg * 0.017453292f
        val c = cos(r); val n = sin(r)
        vec(g, s * c - u * n, s * n + u * c, 0f, out)
    }
}
