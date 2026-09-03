package com.rayneo.mathcosmos

import android.content.Context

/**
 * Where this viewer wants the material to sit.
 *
 * Two people wearing the same glasses do not look at the same place. The frames sit differently on
 * each face, and more importantly people hold their heads differently: some settle with their chin
 * a little down, some a little up, and over half an hour that difference is the difference between
 * comfortable and a sore neck. A framing that is dead centre for the person who tuned it is
 * noticeably high or low for the next person, and on a head-worn display they cannot simply shift
 * in their seat to fix it.
 *
 * So the app asks once, on the way in, and remembers. [aimBias] shifts where a presentation sits in
 * the field of view, in units of the subject's own radius: negative lifts the material up the
 * screen, positive drops it. It is applied to the aim point rather than to the camera, so the
 * subject moves in frame without the framing distance changing.
 *
 * It is deliberately one number. A full six-axis rig would be more precise and nobody would finish
 * it; one swipe up or down until it looks right takes about four seconds.
 */
object Calibration {

    private const val PREFS = "mathcosmos"
    private const val KEY_AIM = "aimBias"
    private const val KEY_DONE = "calibrated"

    /** Steps of a subject radius per swipe: fine enough to land on it, coarse enough to be quick. */
    const val STEP = 0.08f
    const val MIN = -0.75f
    const val MAX = 0.75f

    @Volatile var aimBias = 0f
        private set

    /** True once the viewer has been through the calibration step at least once. */
    @Volatile var calibrated = false
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        aimBias = p.getFloat(KEY_AIM, 0f).coerceIn(MIN, MAX)
        calibrated = p.getBoolean(KEY_DONE, false)
    }

    /** Nudge while the calibration card is up. Returns the new value. */
    fun nudge(context: Context, delta: Float): Float {
        aimBias = (aimBias + delta).coerceIn(MIN, MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_AIM, aimBias).apply()
        return aimBias
    }

    /** The viewer accepted the current setting. */
    fun accept(context: Context) {
        calibrated = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_AIM, aimBias).putBoolean(KEY_DONE, true).apply()
    }

    /** Back to the middle, for someone handing the glasses to somebody else. */
    fun reset(context: Context) {
        aimBias = 0f
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_AIM, 0f).putBoolean(KEY_DONE, false).apply()
        calibrated = false
    }
}
