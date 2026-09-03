package com.rayneo.mathcosmos

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.TextView
import kotlin.math.abs

/**
 * START-DEPTH MENU — a full-screen overlay listing the tour's stops on the
 * powers-of-ten ladder so a guest can begin anywhere from the first stop to
 * the final Look Back. A last row goes back to the tour menu.
 *
 * Temple-pad controls (screen touches mirror them):
 *   SWIPE forward/back (or up/down)  move the highlight
 *   TAP                              start the tour from that depth
 *
 * Start times come from the tour's script ("segments"), set a few seconds
 * before each stop's narration cue so the approach line still plays.
 */
class SegmentMenu(context: Context) : TextView(context) {

    var segments: List<TourDirector.Segment> = TourDirector.DEFAULT_SEGMENTS
        private set

    /** The cut at each node (per tour), shown beside the stop name. */
    var scaleLabels: List<String> = Tours.GROUND.nodes.map { it.scaleLabel }
        set(value) { field = value; render() }

    /**
     * The one sentence each stop is for. Shown for the highlighted row only, so the menu doubles
     * as a revision list: run down it, try to recall each stop before you read it, and jump back
     * to any you cannot.
     */
    var takeaways: List<String> = Tours.GROUND.nodes.map { it.takeaway }
        set(value) { field = value; render() }

    /** Highlight: 0..segments.lastIndex are stops; segments.size is the "other tour" row. */
    var index = 0
        private set
    var onPick: ((TourDirector.Segment) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    init {
        setBackgroundColor(Color.argb(218, 8, 3, 10))
        setTextColor(Color.rgb(255, 196, 107))
        setShadowLayer(10f, 0f, 0f, Color.rgb(255, 77, 109))
        typeface = Typeface.MONOSPACE
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(40, 32, 40, 32)
        isClickable = true
        isFocusable = true
        render()
    }

    fun setSegments(list: List<TourDirector.Segment>) {
        if (list.isEmpty()) return
        segments = list
        index = index.coerceIn(0, segments.lastIndex)
        render()
    }

    /** Preselect the segment the tour is currently in (for reopening). */
    fun selectByTime(elapsedMs: Long) {
        index = segments.indexOfLast { it.startMs <= elapsedMs }.coerceAtLeast(0)
        render()
    }

    fun moveBy(delta: Int) {
        val rows = segments.size + 1
        index = (index + delta + rows) % rows
        render()
    }

    private fun render() {
        val sb = StringBuilder("◄  SELECT A DEPTH  ►\n")
            .append("SWIPE: CHOOSE   TAP: ENGAGE SCALE DRIVE\n\n")
        // Every row is the same length (padded columns, equal-width markers) so the centred
        // monospace block lines up as a table.
        segments.forEachIndexed { i, s ->
            val scale = scaleLabels.getOrElse(s.node) { "" }
            val line = String.format("%-22s %-15s", s.label.take(22), scale)
            sb.append(if (i == index) "▶ $line" else "  $line").append('\n')
        }
        val back = String.format("%-38s", "◄  CHOOSE ANOTHER TOUR")
        sb.append('\n').append(if (index == segments.size) "▶ $back" else "  $back").append('\n')
        // The highlighted stop's one sentence, wrapped by hand: the block is centred monospace at
        // half the panel width, and letting the TextView wrap it ragged breaks the table above it.
        val say = if (index < segments.size) takeaways.getOrElse(segments[index].node) { "" } else ""
        if (say.isNotEmpty()) {
            sb.append('\n')
            var line = StringBuilder()
            for (w in say.split(' ')) {
                if (line.length + w.length + 1 > 46) { sb.append(line).append('\n'); line = StringBuilder() }
                if (line.isNotEmpty()) line.append(' ')
                line.append(w)
            }
            if (line.isNotEmpty()) sb.append(line).append('\n')
        }
        sb.append("\nDOUBLE-TAP DURING THE TOUR TO REOPEN")
        text = sb.toString()
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
                if (dt < 300 && abs(dx) < 46f && abs(dy) < 46f) {
                    if (index >= segments.size) onBack?.invoke() else onPick?.invoke(segments[index])   // tap = begin here
                    return true
                }
                // whichever axis moved more decides direction; +right/+down = next
                val move = if (abs(dx) >= abs(dy)) dx else dy
                if (abs(move) > 70f) moveBy(if (move > 0f) 1 else -1)
                return true
            }
        }
        return true
    }
}
