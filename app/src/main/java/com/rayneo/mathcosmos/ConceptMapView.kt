package com.rayneo.mathcosmos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * THE TOUR MAP — a small inset (top-right) showing the whole ride as one flat drawing, with a
 * marker where the craft is on it.
 *
 * The sibling app put a human figure here, because the body is a thing everyone already has a
 * picture of. Mathematics has no such silhouette, so the inset shows the only honest equivalent:
 * the tour itself — its stops laid out in the order they are visited (TourNode.mapX/mapY, in a
 * 100 x 150 box) and joined by the thread the craft is travelling along.
 *
 * That choice is not merely a fallback. The last stop of every tour dissolves the passage walls
 * and reveals the ride as exactly this drawing, hanging in the dark. For half an hour the viewer
 * has had it in the corner of their eye without knowing what it was; the reveal costs nothing to
 * build because both halves already exist, and it is the series' recurring closing beat.
 *
 * Stops already passed are lit and the thread behind the craft is bright; what is ahead is faint.
 * A ride is a thing you have done part of.
 */
class ConceptMapView(context: Context) : View(context) {
    @Volatile private var progress = 0f
    @Volatile private var map: TourMap = Tours.GROUND
    private val t0 = SystemClock.uptimeMillis()

    private val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.0f; color = Color.rgb(255, 196, 107)
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val threadAhead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.4f; color = Color.argb(80, 255, 196, 107)
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val stopDone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.rgb(255, 196, 107)
    }
    private val stopAhead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.3f; color = Color.argb(120, 255, 196, 107)
    }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(255, 61, 110) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER; color = Color.rgb(255, 196, 107)
        setShadowLayer(4f, 0f, 0f, Color.rgb(255, 61, 110))
    }
    private val done = Path()
    private val ahead = Path()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Rail progress in node units, from the director. */
    fun setProgress(p: Float) {
        val q = p.coerceIn(0f, map.nodes.lastIndex.toFloat())
        if (kotlin.math.abs(q - progress) > 0.002f) { progress = q; postInvalidate() }
    }

    /** The tour whose stops the marker follows. */
    fun setTour(m: TourMap) { map = m; postInvalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // The drawing lives in a 100 x 150 box, scaled to fit above the label.
        val s = min(w / 100f, (h - 26f) / 150f)
        val ox = (w - 100f * s) / 2f
        val oy = 4f
        fun px(x: Float) = ox + x * s
        fun py(y: Float) = oy + y * s
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        val nodes = map.nodes
        if (nodes.size < 2) return

        val p = progress.coerceIn(0f, nodes.lastIndex.toFloat())
        val i = p.toInt().coerceIn(0, nodes.size - 2)
        val f = (p - i).coerceIn(0f, 1f)
        val e = f * f * (3f - 2f * f)
        val mx = px(nodes[i].mapX + (nodes[i + 1].mapX - nodes[i].mapX) * e)
        val my = py(nodes[i].mapY + (nodes[i + 1].mapY - nodes[i].mapY) * e)

        // The thread, in two pieces: what has been flown, and what has not.
        done.reset(); ahead.reset()
        done.moveTo(px(nodes[0].mapX), py(nodes[0].mapY))
        for (k in 1..i) done.lineTo(px(nodes[k].mapX), py(nodes[k].mapY))
        done.lineTo(mx, my)
        ahead.moveTo(mx, my)
        for (k in (i + 1)..nodes.lastIndex) ahead.lineTo(px(nodes[k].mapX), py(nodes[k].mapY))
        canvas.drawPath(ahead, threadAhead)
        canvas.drawPath(done, thread)

        // A bead at every stop: solid once visited, an empty ring until then.
        for (k in nodes.indices) {
            val bx = px(nodes[k].mapX); val by = py(nodes[k].mapY)
            if (k <= i) canvas.drawCircle(bx, by, 1.9f * s * 0.6f + 1.1f, stopDone)
            else canvas.drawCircle(bx, by, 1.7f * s * 0.6f + 1.0f, stopAhead)
        }

        // The craft.
        val pulse = 0.5f + 0.5f * sin(t * 4f)
        for (k in 3 downTo 1) {
            glow.color = Color.argb((28 + 18 * pulse).toInt(), 255, 61, 110)
            canvas.drawCircle(mx, my, (3f + k * 3.2f + pulse * 1.5f) * s * 0.6f + 2f, glow)
        }
        canvas.drawCircle(mx, my, 2.6f * s * 0.6f + 1.5f, marker)

        // What this stop is about, in the shortest form the tour has for it.
        val stop = p.toInt().coerceIn(0, nodes.lastIndex)
        label.textSize = min(11f, w / 9.5f)
        canvas.drawText(nodes[stop].mapLabel, w / 2f, h - 6f, label)

        postInvalidateDelayed(160)   // ~6 Hz is plenty for a pulse; every redraw re-mirrors the whole overlay
    }
}
