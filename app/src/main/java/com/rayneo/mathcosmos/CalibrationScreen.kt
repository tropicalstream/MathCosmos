package com.rayneo.mathcosmos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * SET YOUR EYE LINE — the one calibration step, shown on the way in.
 *
 * A real landmark is already framed and steady behind this card. The viewer sits the way they mean
 * to sit for the next half hour, swipes until the figure is comfortably in front of them rather
 * than above or below, and taps. That is the whole ceremony, and it takes about four seconds.
 *
 * The card deliberately does not dim the world behind it: the thing being calibrated is where the
 * material sits, so the material has to be visible while it is being moved. It draws a reticle at
 * the centre of the lens and a ladder showing how far the framing has been shifted, so there is
 * something to judge "level" against on a display with no horizon of its own.
 *
 * Drawn inside the BinocularSbsLayout like every other overlay, so it is measured at half width and
 * mirrored into both lenses. Backgrounds stay dark-but-not-black; black is transparent out there.
 */
class CalibrationScreen(context: Context) : View(context) {

    /** Called with -1 to lift the material, +1 to drop it. */
    var onNudge: ((Int) -> Unit)? = null
    var onAccept: (() -> Unit)? = null

    private val t0 = SystemClock.uptimeMillis()
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 244, 232); textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(8f, 0f, 0f, Color.rgb(255, 61, 110))
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 214, 160); textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 196, 107); textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val reticle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(120, 220, 190)
    }
    private val rung = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(120, 120, 220, 190)
    }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.rgb(255, 196, 107)
    }
    private val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(190, 10, 6, 14)
    }

    init { isClickable = true; isFocusable = true }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f
        val t = (SystemClock.uptimeMillis() - t0) / 1000f

        // The reticle: where the centre of the lens is, so "comfortably in front" has a reference.
        val r = min(w, h) * 0.055f
        val breathe = 0.5f + 0.5f * sin(t * 1.8f)
        reticle.alpha = (150 + 80 * breathe).toInt()
        canvas.drawCircle(cx, h * 0.5f, r, reticle)
        canvas.drawLine(cx - r * 1.9f, h * 0.5f, cx - r * 1.25f, h * 0.5f, reticle)
        canvas.drawLine(cx + r * 1.25f, h * 0.5f, cx + r * 1.9f, h * 0.5f, reticle)

        // The ladder: how far the framing has been shifted, and which way there is still to go.
        val steps = ((Calibration.MAX - Calibration.MIN) / Calibration.STEP).toInt()
        val ladderX = cx + min(w, h) * 0.30f
        val top = h * 0.30f; val bot = h * 0.70f
        for (k in 0..steps) {
            val y = top + (bot - top) * k / steps
            val wide = if (k % 4 == 0) 9f else 5f
            canvas.drawLine(ladderX - wide, y, ladderX + wide, y, rung)
        }
        val f = (Calibration.aimBias - Calibration.MIN) / (Calibration.MAX - Calibration.MIN)
        canvas.drawCircle(ladderX, top + (bot - top) * f, 5.5f, marker)

        // The instructions, on a plate so they stay legible over whatever the landmark is doing.
        // Five lines of text in the bottom third, on a fixed ladder from the panel's own top edge
        // so nothing lands on the line below it. The last one has to clear the bottom of the lens.
        val panelTop = h * 0.700f
        canvas.drawRect(0f, panelTop, w, h, plate)
        title.textSize = h * 0.060f
        canvas.drawText("SET YOUR EYE LINE", cx, panelTop + h * 0.070f, title)
        body.textSize = h * 0.034f
        canvas.drawText("Sit the way you mean to sit.", cx, panelTop + h * 0.117f, body)
        canvas.drawText("Move the figure until it is comfortably", cx, panelTop + h * 0.154f, body)
        canvas.drawText("in front of you, not above or below.", cx, panelTop + h * 0.191f, body)
        hint.textSize = h * 0.032f
        hint.alpha = (170 + 85 * breathe).toInt()
        canvas.drawText("SWIPE  RAISE / LOWER        TAP  ACCEPT", cx, panelTop + h * 0.243f, hint)
        hint.alpha = 255

        postInvalidateDelayed(40)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downAt = event.eventTime
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val dt = event.eventTime - downAt
                if (dt < 300 && abs(dx) < 46f && abs(dy) < 46f) { onAccept?.invoke(); return true }
                // Whichever axis moved more decides. Forward or up lifts the material toward the
                // top of the lens; back or down drops it.
                val move = if (abs(dx) >= abs(dy)) dx else -dy
                if (abs(move) > 60f) { onNudge?.invoke(if (move > 0f) 1 else -1); invalidate() }
                return true
            }
        }
        return true
    }
}
