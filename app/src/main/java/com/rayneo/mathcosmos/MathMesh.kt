package com.rayneo.mathcosmos

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The geometry a mathematics tour is made of.
 *
 * InnerCosmos built its landmarks from anatomy: rings of cartilage, bubbles, a lipid sheet.
 * A maths landmark is built from five things instead — a plotted curve, a surface over a patch of
 * plane, a field of arrows, a stack of bars, and a ruled axis — and all five have to be rebuilt
 * every frame, because the point of every one of them is that it CHANGES: the tangent slides, the
 * bars subdivide, the arrows turn, the surface breathes.
 *
 * So none of these allocate. Each writes into a FloatArray the caller owns and returns the number
 * of vertices written; the caller hands that to a DynMesh. Two vertex layouts are used, both the
 * engine's own:
 *
 *   LINE / TRI:  position(3) + rgba(4)  — 7 floats, ColorShader, GL_LINES or GL_TRIANGLES
 *
 * Everything is written in WORLD space. A scene that wants its graph square to the rail should
 * build its points through the rail frame's side / up / forward vectors, exactly as the anatomy
 * scenes place their geometry.
 */
object MathMesh {

    const val STRIDE = 7

    /** True if [need] more vertices would overflow [out] from [off] — every builder checks this. */
    private fun room(out: FloatArray, off: Int, need: Int) = (off + need) * STRIDE <= out.size

    /** One vertex. Returns the next vertex index. */
    fun vertex(out: FloatArray, at: Int, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float): Int {
        val o = at * STRIDE
        out[o] = x; out[o + 1] = y; out[o + 2] = z
        out[o + 3] = r; out[o + 4] = g; out[o + 5] = b; out[o + 6] = a
        return at + 1
    }

    /** One GL_LINES segment. */
    fun segment(
        out: FloatArray, at: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        r: Float, g: Float, b: Float, a: Float, a2: Float = a
    ): Int {
        if (!room(out, at, 2)) return at
        var k = vertex(out, at, ax, ay, az, r, g, b, a)
        k = vertex(out, k, bx, by, bz, r, g, b, a2)
        return k
    }

    /**
     * A plotted curve, as a GL_LINES strip through [n]+1 samples of [f] over [t0]..[t1].
     * [f] writes a world point into its out array; it is called once per sample and must not
     * allocate. [fade] tapers the alpha toward both ends, which is how a curve that runs out of
     * the passage stops looking like it was cut off with scissors.
     */
    inline fun curve(
        out: FloatArray, at: Int, n: Int, t0: Float, t1: Float,
        r: Float, g: Float, b: Float, a: Float, fade: Boolean = false,
        scratchA: FloatArray, scratchB: FloatArray,
        f: (Float, FloatArray) -> Unit
    ): Int {
        var k = at
        f(t0, scratchA)
        for (i in 1..n) {
            val u = i.toFloat() / n
            f(t0 + (t1 - t0) * u, scratchB)
            val e0 = if (!fade) a else a * taper((i - 1).toFloat() / n)
            val e1 = if (!fade) a else a * taper(u)
            k = segment(out, k, scratchA[0], scratchA[1], scratchA[2], scratchB[0], scratchB[1], scratchB[2], r, g, b, e0, e1)
            scratchA[0] = scratchB[0]; scratchA[1] = scratchB[1]; scratchA[2] = scratchB[2]
        }
        return k
    }

    /** 0 at both ends, 1 across the middle — the alpha taper a curve fades out with. */
    fun taper(u: Float): Float {
        val d = (1f - abs(u * 2f - 1f)) * 3f
        return d.coerceIn(0f, 1f)
    }

    /**
     * An arrow from a point along a vector: the shaft, and a two-stroke head whose barbs lie in
     * the plane spanned by the vector and [upX]..[upZ]. This is the whole vocabulary of a vector
     * field, a gradient, a force, a velocity — every one of them is this call.
     */
    fun arrow(
        out: FloatArray, at: Int,
        x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float,
        upX: Float, upY: Float, upZ: Float,
        r: Float, g: Float, b: Float, a: Float, headFrac: Float = 0.3f
    ): Int {
        val len = sqrt(vx * vx + vy * vy + vz * vz)
        if (len < 1e-5f || !room(out, at, 6)) return at
        val tx = x + vx; val ty = y + vy; val tz = z + vz
        var k = segment(out, at, x, y, z, tx, ty, tz, r, g, b, a * 0.55f, a)
        // barb = -v normalised, swung either way about the up vector
        val nx = vx / len; val ny = vy / len; val nz = vz / len
        var px = ny * upZ - nz * upY; var py = nz * upX - nx * upZ; var pz = nx * upY - ny * upX
        val pl = sqrt(px * px + py * py + pz * pz)
        if (pl < 1e-5f) { px = upX; py = upY; pz = upZ } else { px /= pl; py /= pl; pz /= pl }
        val h = len * headFrac
        val bx = -nx * h; val by = -ny * h; val bz = -nz * h
        val sx = px * h * 0.5f; val sy = py * h * 0.5f; val sz = pz * h * 0.5f
        k = segment(out, k, tx, ty, tz, tx + bx + sx, ty + by + sy, tz + bz + sz, r, g, b, a)
        k = segment(out, k, tx, ty, tz, tx + bx - sx, ty + by - sy, tz + bz - sz, r, g, b, a)
        return k
    }

    /**
     * The twelve edges of a box given its centre and three half-extent vectors. A Riemann
     * rectangle, a cell of a grid, a unit cube of volume: all the same box.
     */
    fun boxEdges(
        out: FloatArray, at: Int,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        wx: Float, wy: Float, wz: Float,
        r: Float, g: Float, b: Float, a: Float
    ): Int {
        if (!room(out, at, 24)) return at
        var k = at
        // eight corners, indexed by the sign bits of (u, v, w)
        for (i in 0 until 8) {
            val su = if (i and 1 == 0) -1f else 1f
            val sv = if (i and 2 == 0) -1f else 1f
            val sw = if (i and 4 == 0) -1f else 1f
            val x = cx + ux * su + vx * sv + wx * sw
            val y = cy + uy * su + vy * sv + wy * sw
            val z = cz + uz * su + vz * sv + wz * sw
            // join to the three neighbours that differ in one bit, but only upward, so each edge is drawn once
            for (bit in 0 until 3) {
                val j = i or (1 shl bit)
                if (j == i) continue
                val tu = if (j and 1 == 0) -1f else 1f
                val tv = if (j and 2 == 0) -1f else 1f
                val tw = if (j and 4 == 0) -1f else 1f
                k = segment(
                    out, k, x, y, z,
                    cx + ux * tu + vx * tv + wx * tw,
                    cy + uy * tu + vy * tv + wy * tw,
                    cz + uz * tu + vz * tv + wz * tw,
                    r, g, b, a
                )
            }
        }
        return k
    }

    /**
     * A filled parallelogram as two triangles — the face of a Riemann bar, a patch of area, the
     * shaded strip under a curve. [shade] is folded into the colour rather than lit, because the
     * lit shader takes one colour for a whole mesh and these are rebuilt every frame anyway.
     */
    fun quad(
        out: FloatArray, at: Int,
        x: Float, y: Float, z: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        r: Float, g: Float, b: Float, a: Float
    ): Int {
        if (!room(out, at, 6)) return at
        var k = vertex(out, at, x, y, z, r, g, b, a)
        k = vertex(out, k, x + ux, y + uy, z + uz, r, g, b, a)
        k = vertex(out, k, x + ux + vx, y + uy + vy, z + uz + vz, r, g, b, a)
        k = vertex(out, k, x, y, z, r, g, b, a)
        k = vertex(out, k, x + ux + vx, y + uy + vy, z + uz + vz, r, g, b, a)
        k = vertex(out, k, x + vx, y + vy, z + vz, r, g, b, a)
        return k
    }

    /**
     * A surface z = f(u, v) over a rectangular patch, as a wireframe of [nu] by [nv] cells.
     * A wireframe rather than a filled sheet on purpose: on the waveguides a filled surface hides
     * whatever is behind it and the passage is only a few units wide, while a mesh of glowing
     * lines reads as a surface AND lets the rest of the scene through. [f] writes a world point.
     */
    inline fun surfaceWire(
        out: FloatArray, at: Int, nu: Int, nv: Int,
        r: Float, g: Float, b: Float, a: Float,
        rowA: FloatArray, rowB: FloatArray, scratch: FloatArray,
        f: (Float, Float, FloatArray) -> Unit
    ): Int {
        var k = at
        // rows of constant v, joined to the row before
        for (j in 0..nv) {
            val v = j.toFloat() / nv
            for (i in 0..nu) {
                val u = i.toFloat() / nu
                f(u, v, scratch)
                rowB[i * 3] = scratch[0]; rowB[i * 3 + 1] = scratch[1]; rowB[i * 3 + 2] = scratch[2]
                if (i > 0) k = segment(
                    out, k, rowB[(i - 1) * 3], rowB[(i - 1) * 3 + 1], rowB[(i - 1) * 3 + 2],
                    rowB[i * 3], rowB[i * 3 + 1], rowB[i * 3 + 2], r, g, b, a
                )
                if (j > 0) k = segment(
                    out, k, rowA[i * 3], rowA[i * 3 + 1], rowA[i * 3 + 2],
                    rowB[i * 3], rowB[i * 3 + 1], rowB[i * 3 + 2], r, g, b, a
                )
            }
            System.arraycopy(rowB, 0, rowA, 0, (nu + 1) * 3)
        }
        return k
    }

    /**
     * One ruled axis: the spine, a barb at the positive end, and a tick at every whole unit.
     * [ticks] is how many units each way; [tickSize] is the half-length of a tick mark, drawn
     * along [px]..[pz]. Returns the vertex count; the caller labels the ticks with a GlyphBoard.
     */
    fun axis(
        out: FloatArray, at: Int,
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        px: Float, py: Float, pz: Float,
        unit: Float, ticks: Int, tickSize: Float,
        r: Float, g: Float, b: Float, a: Float,
        negative: Boolean = true
    ): Int {
        val lo = if (negative) -ticks else 0
        var k = segment(
            out, at,
            ox + dx * unit * lo, oy + dy * unit * lo, oz + dz * unit * lo,
            ox + dx * unit * ticks, oy + dy * unit * ticks, oz + dz * unit * ticks,
            r, g, b, a
        )
        // the barb on the positive end
        val tipX = ox + dx * unit * ticks; val tipY = oy + dy * unit * ticks; val tipZ = oz + dz * unit * ticks
        val h = unit * 0.22f
        k = segment(out, k, tipX, tipY, tipZ, tipX - dx * h + px * h * 0.4f, tipY - dy * h + py * h * 0.4f, tipZ - dz * h + pz * h * 0.4f, r, g, b, a)
        k = segment(out, k, tipX, tipY, tipZ, tipX - dx * h - px * h * 0.4f, tipY - dy * h - py * h * 0.4f, tipZ - dz * h - pz * h * 0.4f, r, g, b, a)
        for (i in lo..ticks) {
            if (i == 0) continue
            val cx = ox + dx * unit * i; val cy = oy + dy * unit * i; val cz = oz + dz * unit * i
            k = segment(
                out, k, cx - px * tickSize, cy - py * tickSize, cz - pz * tickSize,
                cx + px * tickSize, cy + py * tickSize, cz + pz * tickSize, r, g, b, a * 0.7f
            )
        }
        return k
    }

    /** A flat grid of [n] by [n] cells in the plane spanned by u and v, centred on the origin. */
    fun grid(
        out: FloatArray, at: Int,
        ox: Float, oy: Float, oz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        n: Int, r: Float, g: Float, b: Float, a: Float
    ): Int {
        var k = at
        for (i in -n..n) {
            val t = i.toFloat()
            val fade = a * (1f - abs(t) / (n + 1f))
            k = segment(
                out, k,
                ox + ux * t - vx * n, oy + uy * t - vy * n, oz + uz * t - vz * n,
                ox + ux * t + vx * n, oy + uy * t + vy * n, oz + uz * t + vz * n,
                r, g, b, fade
            )
            k = segment(
                out, k,
                ox + vx * t - ux * n, oy + vy * t - uy * n, oz + vz * t - uz * n,
                ox + vx * t + ux * n, oy + vy * t + uy * n, oz + vz * t + uz * n,
                r, g, b, fade
            )
        }
        return k
    }

    /** A circle (or an arc) of [n] segments in the plane spanned by u and v. */
    fun arc(
        out: FloatArray, at: Int,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float,
        radius: Float, from: Float, to: Float, n: Int,
        r: Float, g: Float, b: Float, a: Float
    ): Int {
        var k = at
        var pxx = 0f; var pyy = 0f; var pzz = 0f
        for (i in 0..n) {
            val ang = from + (to - from) * i / n
            val c = kotlin.math.cos(ang) * radius
            val s = kotlin.math.sin(ang) * radius
            val x = cx + ux * c + vx * s
            val y = cy + uy * c + vy * s
            val z = cz + uz * c + vz * s
            if (i > 0) k = segment(out, k, pxx, pyy, pzz, x, y, z, r, g, b, a)
            pxx = x; pyy = y; pzz = z
        }
        return k
    }

    /** A dashed segment — how a construction line is told apart from a real object. */
    fun dashed(
        out: FloatArray, at: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        dashes: Int, r: Float, g: Float, b: Float, a: Float
    ): Int {
        var k = at
        val n = max(1, dashes)
        for (i in 0 until n) {
            val t0 = i.toFloat() / n
            val t1 = t0 + 0.55f / n
            k = segment(
                out, k,
                ax + (bx - ax) * t0, ay + (by - ay) * t0, az + (bz - az) * t0,
                ax + (bx - ax) * t1, ay + (by - ay) * t1, az + (bz - az) * t1,
                r, g, b, a
            )
        }
        return k
    }
}
