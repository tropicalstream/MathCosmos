package com.rayneo.mathcosmos

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Procedural body ambience + transient cues, rendered on a background thread.
 * No audio files needed. The soundscape follows the tour node:
 *
 *   AIR       slow breath (filtered noise swelling ~13 times a minute)
 *   BLOOD     a resting heartbeat (lub-dub, ~65 bpm) under a flow rush
 *   NEURAL    soft neural crackle (random ticks, alternating sides)
 *   CYTO      deep cytoplasm hum
 *   ATOM      near silence with a glassy shimmer
 *   LOOKBACK  a warm pad
 *   GUT       a wet low gurgle with bubbles (tour II)
 *   MUSCLE    a tension hum swelling with every twitch (tour II)
 *   MOTOR     the whir of the ATP synthase rotor with a proton hiss (tour II)
 *
 * Views add colour: ENGINEERING (2) brings up the scale-drive hum, OBSERVATION
 * (3) a meditative pad. Transients (shrink sweep, drive engage, heartbeat,
 * chime, spark, squelch, alert blip) are the synth fallbacks for missing sfx.
 *
 * The engine is the heartbeat clock: [beatPhaseSec] is published every buffer
 * so the renderer's wall pulse and valve stay phase-locked to the audible beat.
 *
 * Cost matters on the glasses: the whole voice runs on float phase accumulators and a
 * 4096-entry sine table at 22.05 kHz (a few percent of one core), never on per-sample
 * double-precision trig.
 */
class MathAudioEngine {
    @Volatile private var running = false
    /** True only while the audio thread is actually writing (clears on any loop exit). */
    @Volatile private var loopAlive = false
    private var generation = 0
    private val pending = AtomicInteger(0)
    @Volatile private var viewMode = 0
    @Volatile private var amb = 0          // Amb.ordinal of the current stop
    /** 0..1 ambient gain from the audio-mix cycler (smoothed inside the loop). */
    @Volatile var masterGain = 1f

    /**
     * How much of the ambient bed to let through, 0..1. Driven by the renderer: it falls to a
     * whisper whenever the craft is alongside a demonstration.
     *
     * A continuous sound bed is fine while you are travelling and actively harmful while you are
     * being shown something — it is unrelated auditory load competing for the same working memory
     * the explanation needs, and on a head-worn display there is nowhere to look away to. So the
     * room goes quiet when there is something to attend to, and comes back when the craft moves on.
     */
    @Volatile var focus = 1f
    /** Seconds into the current heartbeat, 0..BEAT_PERIOD. */
    @Volatile var beatPhaseSec = 0f
    /** Breath cycle phase 0..1 (0.21 Hz): the airway's airflow visual follows it. */
    @Volatile var breathPhase01 = 0f
    private var audioThread: Thread? = null

    companion object {
        const val BEAT_PERIOD = 0.92f     // ~65 bpm at rest
        private const val TAG = "MCAudio"
        private const val RATE = 22_050
        private const val T_SHRINK = 1
        private const val T_RISE = 2
        private const val T_TAP = 4
        private const val T_THUMP = 8
        private const val T_CHIME = 16
        private const val T_SPARK = 32
        private const val T_SQUELCH = 64
        private const val T_GROW = 128
        private const val T_LYSIS = 256
        private const val TABLE = 4096
        private val SIN = FloatArray(TABLE) { sin(2.0 * PI * it / TABLE).toFloat() }
    }

    fun isRunning(): Boolean = running && loopAlive

    /** 0=bridge, 1=external, 2=engineering, 3=observation (see renderer VIEW_* constants). */
    fun setAmbience(mode: Int) { viewMode = mode }

    /** Ambience family of the current stop (drives the biological sound bed). */
    fun setStage(a: Amb) { amb = a.ordinal }

    fun start() {
        if (running && loopAlive) return
        running = true
        val gen = ++generation
        audioThread = thread(name = "ic-body-audio") { renderLoop(gen) }
    }

    fun stop() {
        running = false
        audioThread?.join(350)
        audioThread = null
    }

    private fun trigger(bit: Int) { pending.getAndUpdate { it or bit } }

    fun shrink() = trigger(T_SHRINK)      // one power-of-ten scale step (descending sweep)
    fun engage() = trigger(T_RISE)        // scale drive winding up (rising sweep)
    fun tap() = trigger(T_TAP)            // alert blip / UI tap
    fun heartbeat() = trigger(T_THUMP)    // one-shot thump + restart the beat clock
    fun chime() = trigger(T_CHIME)        // arrival bell
    fun spark() = trigger(T_SPARK)        // action potential crackle
    fun squelch() = trigger(T_SQUELCH)    // membrane / vesicle squeeze
    fun grow() = trigger(T_GROW)          // one power-of-ten step UP the ladder (ascending sweep)
    fun lysis() = trigger(T_LYSIS)        // a cell bursting: wet crack + low thump

    /** A phase accumulator in table units per sample; [next] returns the sine and advances. */
    private class Osc(hz: Float) {
        var phase = 0f
        var step = hz * TABLE / RATE
        fun next(): Float { val v = SIN[phase.toInt() and (TABLE - 1)]; phase += step; if (phase >= TABLE) phase -= TABLE; return v }
        fun reset() { phase = 0f }
        fun setHz(hz: Float) { step = hz * TABLE / RATE }
    }

    /** All voice state lives in fields so the hot loop is one small, JIT-friendly method. */
    private inner class Synth {
        val breathLfo = Osc(0.21f); val padA = Osc(110f); val padB = Osc(164.81f)
        val humA = Osc(55f); val humB = Osc(82.4f); val shimA = Osc(1760f); val shimB = Osc(2637f)
        val drvA = Osc(65f); val drvB = Osc(130f); val drvC = Osc(195f); val drvLfo = Osc(3.1f)
        val sweep = Osc(70f); val rise = Osc(90f); val tapO = Osc(420f); val thumpO = Osc(48f)
        val chimeA = Osc(880f); val chimeB = Osc(1318.5f); val sparkO = Osc(2200f); val sqO = Osc(60f); val tickO = Osc(1400f)
        val lub = Osc(40f); val dub = Osc(65f)
        val bubO = Osc(90f); val muscLfo = Osc(0.8f); val tensO = Osc(30f); val motLfo = Osc(7f); val growO = Osc(70f); val lysO = Osc(55f)
        var seed = 0x2545F491
        var lp1 = 0f; var lp2 = 0f
        var beatT = 0f
        var tickEnv = 0f; var tickSide = 1f; var sparkSide = 1f
        var shrinkEnv = 0f; var riseEnv = 0f; var tapEnv = 0f; var thumpEnv = 0f
        var chimeEnv = 0f; var sparkEnv = 0f; var squelchEnv = 0f; var bubEnv = 0f; var growEnv = 0f; var lysEnv = 0f
        var breathG = 0f; var heartG = 0f; var neuralG = 0f; var cytoG = 0f; var atomG = 0f
        var lookG = 0f; var engG = 0f; var loungeG = 0f; var gainS = 1f; var gutG = 0f; var muscG = 0f; var motG = 0f
        val dShrink = tau(0.85); val dRise = tau(0.75); val dTap = tau(0.08); val dThump = tau(0.14)
        val dChime = tau(0.55); val dSpark = tau(0.06); val dSquelch = tau(0.16); val dTick = tau(0.025)
        val dBub = tau(0.10); val dGrow = tau(0.85); val dLys = tau(0.22)
        val smooth = 0.00006f
        val dt = 1f / RATE

        private fun tau(sec: Double): Float = exp(-1.0 / (sec * RATE)).toFloat()
        private fun noise(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 16) and 0xFFFF) / 32768f - 1f }
        private fun expf(x: Float): Float = exp(x.toDouble()).toFloat()

        fun applyTriggers(t: Int) {
            if (t and T_SHRINK != 0) { shrinkEnv = 1f; sweep.reset() }
            if (t and T_RISE != 0) { riseEnv = 1f; rise.reset() }
            if (t and T_TAP != 0) tapEnv = 1f
            if (t and T_THUMP != 0) { thumpEnv = 1f; thumpO.reset(); beatT = 0f }
            if (t and T_CHIME != 0) { chimeEnv = 1f; chimeA.reset(); chimeB.reset() }
            if (t and T_SPARK != 0) { sparkEnv = 1f; sparkSide = -sparkSide }
            if (t and T_SQUELCH != 0) { squelchEnv = 1f; sqO.reset() }
            if (t and T_GROW != 0) { growEnv = 1f; growO.reset() }
            if (t and T_LYSIS != 0) { lysEnv = 1f; lysO.reset() }
        }

        fun render(buffer: ShortArray, s: Int, view: Int, gain: Float) {
            // s is Amb.ordinal: COUNT PLANE CURVE LIMIT SUM INFINITE SURFACE FIELD SOLVE LOOKBACK.
            //
            // The nine synthesised beds were written for a body, but they are abstract textures
            // underneath — a tick, a room tone, a held hum, a shimmer, a slow pulse, a pad, a low
            // rumble, a tension hum, a rotor — so each mathematical family takes the bed that
            // already sounds like it rather than a new oscillator bank the glasses cannot afford.
            // COUNT used to take the tick bed as a metronome under the counting. On the glasses
            // that read as a chirp — a random 1.4-2.1 kHz tick alternating between your ears, from
            // the very first stop of the series — and it competed with the crew for exactly the
            // attention the crew were asking for. Counting now gets the same still room tone as the
            // bare plane, and the tick generator is gone from the mix entirely.
            val tBreath = if (s == 0 || s == 1) 1f else 0f   // COUNT and PLANE: a still room
            val tCyto = if (s == 2) 1f else 0f      // CURVE: one held hum
            val tAtom = if (s == 3) 1f else 0f      // LIMIT: a shimmer at the edge of hearing
            val tHeart = if (s == 4) 1f else 0f     // SUM: a slow pulse, one per slab
            val tGut = if (s == 6) 1f else 0f       // SURFACE: the low bed reads as wind over ground
            val tMot = if (s == 7) 1f else 0f       // FIELD: a rotor, moving air with a direction
            val tMusc = if (s == 8) 1f else 0f      // SOLVE: a tension hum
            val tLook = if (s == 5 || s == 9) 1f else 0f   // INFINITE and LOOKBACK share the pad
            val tEng = if (view == 2) 1f else 0f
            val tLounge = if (view == 3) 1f else 0f
            var i = 0
            while (i < buffer.size) {
                breathG += (tBreath - breathG) * smooth
                heartG += (tHeart - heartG) * smooth
                cytoG += (tCyto - cytoG) * smooth
                atomG += (tAtom - atomG) * smooth
                lookG += (tLook - lookG) * smooth
                engG += (tEng - engG) * smooth
                loungeG += (tLounge - loungeG) * smooth
                gutG += (tGut - gutG) * smooth
                muscG += (tMusc - muscG) * smooth
                motG += (tMot - motG) * smooth
                gainS += (gain - gainS) * (if (gain < gainS) 0.0016f else 0.0004f)

                val white = noise()
                lp1 += (white - lp1) * 0.08f
                lp2 += (lp1 - lp2) * 0.02f
                val lfo = breathLfo.next()
                val bEnv = 0.5f + 0.5f * lfo
                val breath = lp1 * bEnv * bEnv * 0.13f * breathG

                // Heartbeat: S1 (lower, longer) then S2 (higher, crisper), plus a flow rush.
                beatT += dt
                if (beatT >= BEAT_PERIOD) beatT -= BEAT_PERIOD
                val lubS = if (beatT < 0.35f) lub.next() * expf(-beatT * 12f) else { lub.reset(); 0f }
                val t2 = beatT - 0.36f
                val dubS = if (t2 in 0f..0.3f) dub.next() * expf(-t2 * 18f) else { dub.reset(); 0f }
                val heart = ((lubS + dubS) * 0.24f + lp2 * (0.05f + 0.09f * expf(-beatT * 4f))) * heartG

                // Neural crackle: random short ticks, alternating sides.
                // The tick is deliberately dead: see the note by the family assignment above.
                val neuralTick = 0f
                val neuralBed = 0f

                val cyto = (humA.next() + 0.5f * humB.next()) * 0.06f * (0.7f + 0.3f * lfo) * cytoG
                val atom = (shimA.next() * 0.014f + shimB.next() * 0.007f) * (0.6f + 0.4f * lfo) * atomG
                val pad = (padA.next() + padB.next() * 0.7f) * 0.055f * (0.6f + 0.4f * lfo) * max(loungeG, lookG)
                val dA = drvA.next(); val dB = drvB.next(); val dC = drvC.next()
                val drive = (dA * 0.6f + dB * 0.3f + dC * 0.15f) * 0.10f * (0.7f + 0.3f * drvLfo.next()) * engG

                // Gut: a wet low gurgle with random bubbles, each gliding down as it pops.
                if (gutG > 0.01f && noise() > 0.99992f) bubEnv = 1f
                bubEnv *= dBub
                bubO.setHz(70f + 160f * bubEnv)
                val gut = (lp2 * 0.16f * (0.6f + 0.4f * lfo) + bubO.next() * bubEnv * 0.16f) * gutG
                // Muscle: a tension hum swelling with every twitch over a low creak.
                val ml = 0.5f + 0.5f * muscLfo.next()
                val musc = (tensO.next() * 0.09f * ml * ml + lp2 * 0.05f * ml) * muscG
                // The motor: a rotor's whir with a fast flutter and a proton hiss.
                val mf = 0.65f + 0.35f * motLfo.next()
                val mot = ((dA * 0.5f + dB * 0.35f + dC * 0.2f) * 0.09f * mf + lp1 * 0.015f) * motG

                // Transients.
                shrinkEnv *= dShrink
                sweep.setHz(70f + 900f * shrinkEnv * shrinkEnv)
                val shrinkS = sweep.next() * shrinkEnv * 0.16f + lp1 * shrinkEnv * 0.09f
                riseEnv *= dRise
                rise.setHz(90f + 820f * (1f - riseEnv))
                val riseS = rise.next() * riseEnv * 0.11f
                tapEnv *= dTap
                val tapS = tapO.next() * tapEnv * 0.12f
                thumpEnv *= dThump
                val thumpS = thumpO.next() * thumpEnv * 0.30f
                chimeEnv *= dChime
                val chimeS = (chimeA.next() + 0.6f * chimeB.next()) * chimeEnv * 0.10f
                sparkEnv *= dSpark
                val sparkS = (sparkO.next() * 0.5f + noise() * 0.5f) * sparkEnv * 0.14f
                squelchEnv *= dSquelch
                sqO.setHz(60f + 260f * squelchEnv)
                val sqS = sqO.next() * squelchEnv * 0.18f
                growEnv *= dGrow
                growO.setHz(70f + 900f * (1f - growEnv) * (1f - growEnv))
                val growS = growO.next() * growEnv * 0.16f + lp1 * growEnv * 0.09f
                lysEnv *= dLys
                lysO.setHz(55f + 120f * lysEnv)
                val lysS = (noise() * 0.35f + lysO.next() * 0.6f) * lysEnv * 0.32f

                val centre = breath + heart + neuralBed + cyto + atom + pad + drive + gut + musc + mot + shrinkS + riseS + tapS + thumpS + chimeS + sqS + growS + lysS
                val tickL = if (tickSide > 0f) 1f else 0.4f; val tickR = if (tickSide > 0f) 0.4f else 1f
                val sparkL = if (sparkSide > 0f) 1f else 0.4f; val sparkR = if (sparkSide > 0f) 0.4f else 1f
                val left = ((centre + neuralTick * tickL + sparkS * sparkL) * gainS).coerceIn(-0.6f, 0.6f)
                val right = ((centre + neuralTick * tickR + sparkS * sparkR) * gainS).coerceIn(-0.6f, 0.6f)
                buffer[i] = (left * 32767f).toInt().toShort()
                buffer[i + 1] = (right * 32767f).toInt().toShort()
                i += 2
            }
        }
    }

    private fun renderLoop(gen: Int) {
        val track: AudioTrack
        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack unavailable; ambience disabled", e)
            return
        }
        val synth = Synth()
        val buffer = ShortArray(1024)
        loopAlive = true
        try {
            while (running && gen == generation) {
                synth.applyTriggers(pending.getAndSet(0))
                synth.render(buffer, amb, viewMode, masterGain * focus)
                beatPhaseSec = synth.beatT
                breathPhase01 = synth.breathLfo.phase / TABLE
                val wrote = track.write(buffer, 0, buffer.size)
                if (wrote < 0) { Log.w(TAG, "AudioTrack write failed: $wrote"); break }
            }
        } finally {
            if (gen == generation) loopAlive = false      // a newer loop keeps its own flag
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}
