package com.rayneo.mathcosmos

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Side-by-side stereo renderer for the MathCosmos descent (OpenGL ES 2.0).
 *
 * The world is a single continuous passage: a tube that follows the rail
 * through thirteen stops and changes radius, colour and wall texture as the
 * Mote shrinks (airway → alveolus → capillary → heart → ... → nucleus → atom).
 * Every stop has a procedural landmark (cartilage rings, alveolar bubbles,
 * red cells, a slamming valve, a chasing neutrophil, a firing axon, the lipid
 * bilayer, ATP synthase rotors, the double helix, a ribosome, an electron
 * cloud, and finally a cosmos of cells). Nothing needs textures.
 *
 * Two tours share the machinery (see TourMap): every stop names its scene, ambience family and
 * body-map position, and the passage is rebuilt when the tour changes.
 *
 * The TourDirector owns pacing (setProgress in node units 0..12) and view cuts;
 * this class owns the camera, the ship's sway, the heartbeat clock, the
 * shrink-burst / scale-jump effects and the on-screen telemetry text.
 */
class StereoMathRenderer(
    private val audioEngine: MathAudioEngine,
    private val context: Context? = null
) : GLSurfaceView.Renderer {
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private val normalM = FloatArray(16)
    private val invM = FloatArray(16)
    private val identityM = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    private lateinit var sphere: SphereMesh
    private lateinit var blob: SphereMesh
    private lateinit var tunnel: TubeMesh
    private lateinit var moteMesh: TriMesh
    private lateinit var cockpitMesh: LineMesh
    private lateinit var routeMesh: LineMesh
    private lateinit var routeNodes: PointMesh
    private var plateShader: PlateShader? = null
    /** Chapter III's pictures, loaded from assets/plates on the GL thread; empty if absent. */
    private val plates = HashMap<String, Plate>()
    private lateinit var litShader: LitShader
    private lateinit var colorShader: ColorShader
    private lateinit var wallShader: WallShader
    private val drift = DriftField(150)
    private val air = AirField(96)
    private var airFlow = 0f       // signed airspeed along the rail: + = inhale (deeper), - = exhale
    private val bodies = BodyField(20)
    private val dynTris = DynMesh(24)          // small per-frame triangle work owned by the renderer
    private val dynLines = DynMesh(64)         // small per-frame line work owned by the renderer

    // Notation, and the two buffers every landmark scene builds its geometry in. They are
    // shared and rewritten by each scene in turn, which is why a scene must flush before it
    // asks for the buffer again.
    private val glyphs = GlyphBoard()
    private val sceneLines = DynMesh(SCENE_LINE_VERTS)
    private val sceneTris = DynMesh(SCENE_TRI_VERTS)
    private val kit = Kit()
    // Camera right and up, rebuilt each frame so notation can face the eye.
    private var camRightX = 1f; private var camRightY = 0f; private var camRightZ = 0f
    private var camUpX = 0f; private var camUpY = 1f; private var camUpZ = 0f

    // The tour being rendered: its rail, wall colours, scenes and ambience families. A switch is
    // requested from any thread and applied on the GL thread (the passage meshes are rebuilt there).
    private var map: TourMap = Tours.GROUND
    private var nodes: List<TourNode> = map.nodes
    private val pendingMap = java.util.concurrent.atomic.AtomicReference<TourMap?>(null)
    private var sentinelIdx = nodes.indexOfFirst { MathScenes.of(it.scene)?.chases == true }

    private var width = 1
    private var height = 1
    private var nowSeconds = 0f
    private var fpsFrames = 0
    private var fpsWindowStart = 0L
    @Volatile private var fpsNow = 0f
    private val startNanos = System.nanoTime()
    private var lastFrameNanos = startNanos
    private var routeProgress = 0f
    @Volatile private var railTarget = 0f          // written by the director (10 Hz); followed on the GL thread
    private var viewMode = VIEW_CHASE
    private var prevViewMode = VIEW_CHASE
    private var viewBlend = 1f
    private var craftYaw = 0f
    private var craftPitch = 0f
    private var viewListener: ((Int) -> Unit)? = null
    @Volatile private var scripted = false
    /** Two eye viewports (the X3 Pro) or one full-width view (emulator / phone testing). */
    @Volatile var stereo = true
    /** 0 = full detail, 1 = reduced (fewer bodies, no wall veins), 2 = minimal (thermal throttling). */
    @Volatile var quality = 0
    /** Title-card mode: the Mote idles outside the nose with a slow orbit and a gentle bob. */
    @Volatile var showcase = false
    private var maxLineWidth = 1f

    // Ship: rail position + flow sway, smoothed velocity for heading.
    private var shipX = 0f; private var shipY = 0f; private var shipZ = 0f
    private var velX = 0f; private var velY = 0f; private var velZ = -1f
    private var latX = 0f; private var latY = 0f; private var latZ = 0f
    private var flightInit = false
    private var dirX = 0f; private var dirY = 0f; private var dirZ = -1f
    private var sideX = 1f; private var sideY = 0f; private var sideZ = 0f
    private var upX = 0f; private var upY = 1f; private var upZ = 0f
    private var railCx = 0f; private var railCy = 0f; private var railCz = 0f   // rail centre at routeProgress

    // Camera + fx.
    private var camNowX = 0f; private var camNowY = 0f; private var camNowZ = 1f
    private var lookNowX = 0f; private var lookNowY = 0f; private var lookNowZ = 0f
    private var beat = 0f
    // Scale-drop feel: the world inflates about the ship for a beat while the hull dwindles.
    private var inflateT = 99f
    private var inflate = 1f
    private var shipScale = 1f
    private var growing = false            // the current scale step is a rise (tour II), not a drop
    private var lysisClock = 0f            // the phage stop's burst cycle (seconds)
    private val viewWorld = FloatArray(16)
    private val inflM = FloatArray(16)
    /** Head look-around (IMU), applied to the look direction only. */
    @Volatile var gaze: GazeCamera? = null
    private var shakeX = 0f; private var shakeY = 0f
    private var shakeTX = 0f; private var shakeTY = 0f; private var shakeTimer = 0f
    private var shrinkBurst = 0f
    @Volatile private var jumpOn = false
    private var jumpIntensity = 0f
    private var heartPhase = 0f
    private var heartKick = 0f
    private var wallPulse = 0f
    private val camA = FloatArray(6)
    private val camB = FloatArray(6)
    private val wallCol = FloatArray(3)

    // Arm probes (0 = folded along the hull, 1 = reaching ahead).
    private var armReach = 0f
    private var armKick = 0f
    private val tmpW = FloatArray(3)
    private val tmpS = FloatArray(3)
    private val tmpE = FloatArray(3)
    private val tmpT = FloatArray(3)

    // Alpha multiplier for the landmark being drawn (distance fade-in).
    private var landmarkFade = 1f

    /**
     * How much of a demonstration the craft is currently in front of, 0 in transit to 1 parked at
     * a stop. Everything that moves for atmosphere rather than for meaning is damped by it: the
     * hull's sway, the camera's orbit, the ambient bed.
     *
     * The reason is not aesthetic. While a landmark is doing the one thing it exists to do, the
     * viewer's attention should be on that and on the crew's voice, and a camera that keeps
     * drifting is a second thing to track. In transit the motion comes back, because a corridor
     * that is completely still stops reading as flight.
     */
    private var stillness = 0f
    /** A clock that slows to a stop with [stillness], so damped motions ease rather than snap. */
    private var calmClock = 0f
    // The settled camera: where the view eases to while a demonstration is being presented. Kept
    // between frames so the settle is a movement you can watch rather than a cut.
    private var presX = 0f; private var presY = 0f; private var presZ = 0f
    private var presLX = 0f; private var presLY = 0f; private var presLZ = 0f
    private var presInit = false
    /**
     * How committed the camera is to the composed view, latched rather than tracked.
     *
     * Blending straight off [stillness] looked settled and was not: the craft goes on drifting
     * slowly through a stop, so stillness keeps changing by a few hundredths, the blend follows it,
     * and the frame creeps for the whole of a two-minute demonstration. Past the upper threshold
     * this pins at 1 and the framing stops moving completely; it only lets go once the craft is
     * genuinely on its way out.
     */
    private var presW = 0f

    // Sentinel (neutrophil) chase state.
    private var sentX = 0f; private var sentY = 0f; private var sentZ = 0f
    private var sentInit = false

    private val beaconData = FloatArray(7)
    private val beaconBuf = ByteBuffer.allocateDirect(28).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val flashData = floatArrayOf(
        -1f, -1f, 0f, 1f, 0.55f, 0.45f, 0f,
        1f, -1f, 0f, 1f, 0.55f, 0.45f, 0f,
        1f, 1f, 0f, 1f, 0.55f, 0.45f, 0f,
        -1f, -1f, 0f, 1f, 0.55f, 0.45f, 0f,
        1f, 1f, 0f, 1f, 0.55f, 0.45f, 0f,
        -1f, 1f, 0f, 1f, 0.55f, 0.45f, 0f
    )
    private val flashBuf = ByteBuffer.allocateDirect(flashData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val streakCount = 46
    private val streakSeeds = FloatArray(streakCount * 2) { Math.random().toFloat() }
    private val streakData = FloatArray(streakCount * 2 * 7)
    private val streakBuf = ByteBuffer.allocateDirect(streakData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // ------------------------------------------------------------------ API
    fun setViewListener(listener: (Int) -> Unit) { viewListener = listener }
    fun setScripted(on: Boolean) { scripted = on }
    fun setProgress(p: Float) { railTarget = p.coerceIn(0f, nodes.lastIndex.toFloat()) }
    /** Switch tours (any thread): the passage and route are rebuilt on the GL thread before the next frame. */
    fun setMap(m: TourMap) { if (m !== map) pendingMap.set(m) }
    /** Fired on the GL thread once a new tour's passage is built (the HUD reads the map). */
    @Volatile var mapListener: ((TourMap) -> Unit)? = null
    fun setView(mode: Int) {
        val m = mode.coerceIn(0, VIEW_COUNT - 1)
        if (m != viewMode) { prevViewMode = viewMode; viewBlend = 0f }
        viewMode = m
        viewListener?.invoke(viewMode)
    }
    fun switchView() { setView((viewMode + 1) % VIEW_COUNT) }

    /** Visual beat synced to an SFX cue: brief screen flash + camera shake. */
    fun triggerBeat(intensity: Float) { beat = max(beat, intensity.coerceIn(0f, 1f)) }

    /** One power-of-ten scale step: the world balloons, the hull dwindles, streaks, ~3 s. */
    fun triggerShrink() {
        shrinkBurst = 1f
        inflateT = 0f
        growing = false
        drift.blowOut(shipX, shipY, shipZ, 1f)
        bodies.blowOut(shipX, shipY, shipZ, 1f)
    }

    /** The reverse step (tour II climbs the ladder several times): the world contracts about the ship, the hull swells, particles rush in. */
    fun triggerGrow() {
        shrinkBurst = 1f
        inflateT = 0f
        growing = true
        drift.blowOut(shipX, shipY, shipZ, -0.6f)
        bodies.blowOut(shipX, shipY, shipZ, -0.6f)
    }

    /** The "lysis" cue: the phage stop's infected host bursts now, in step with the sound. */
    fun triggerLysis() { lysisClock = LYSIS_PERIOD * 0.62f; beat = max(beat, 0.6f) }

    /** A scripted heartbeat cue: kick the wall pulse and restart the beat clock. */
    fun triggerHeartbeat() { heartKick = 1f; heartPhase = 0f }

    /** Scale jump (menu): continuous streaks + tremble while the director races the rail. */
    fun setJumping(on: Boolean) { jumpOn = on }

    /** A scripted touch (spark / squelch cues): the arm probes reach out for a few seconds. */
    fun triggerProbe() { armKick = 1f }

    private fun armReachTarget(p: Float): Float {
        var r = 0f
        for (c in map.armStops) { val d = p - c; r = max(r, exp(-(d * d) / 0.02f)) }
        return max(r, armKick)
    }

    fun currentNodeName(): String = nodes[routeProgress.toInt().coerceIn(0, nodes.lastIndex)].name

    /** Extra HUD line with camera / ship geometry (adb: --ez debug true). */
    @Volatile var debugHud = false

    /** HUD text: craft, view, departed/approaching, leg, scale ladder. */
    fun telemetry(): String {
        val dbg = if (!debugHud) "" else {
            val vx = camNowX - shipX; val vy = camNowY - shipY; val vz = camNowZ - shipZ
            val g = gaze
            "\nCAM along %.2f side %.2f up %.2f  yaw %.0f pitch %.0f  blend %.2f prev %d arm %.2f  fps %.0f q%d  infl %.2f ship %.2f still %.2f\nGAZE yaw %.0f pitch %.0f  (raw hdg %.0f el %.0f) %s".format(Locale.US,
                vx * dirX + vy * dirY + vz * dirZ, vx * sideX + vy * sideY + vz * sideZ, vx * upX + vy * upY + vz * upZ,
                craftYaw, craftPitch, viewBlend, prevViewMode, armReach, fpsNow, quality, inflate, shipScale, stillness,
                (g?.yaw ?: 0f) * 57.3f, (g?.pitch ?: 0f) * 57.3f, (g?.rawYaw ?: 0f) * 57.3f, (g?.rawPitch ?: 0f) * 57.3f,
                if (g == null) "no-imu" else if (g.enabled) "on" else "off")
        }
        val floor = routeProgress.toInt().coerceIn(0, nodes.lastIndex)
        val nextIdx = (floor + 1).coerceAtMost(nodes.lastIndex)
        val frac = (routeProgress - floor).coerceIn(0f, 1f)
        val cut = cutAt(routeProgress)
        val unit = nodes[floor].cutUnit
        val mode = VIEW_NAMES.getOrElse(viewMode) { "HELM" }
        val approaching = if (nextIdx == floor) "THE VIEW FROM OUTSIDE" else nodes[nextIdx].name
        // What the current stop is actually measuring, if its scene keeps a number worth showing.
        val read = MathScenes.of(nodes[floor].scene)?.readout(kit)
        val readLine = if (read.isNullOrEmpty()) "" else "\n$read"
        return "M.S.V. CALIPER   ${map.hudTitle}\n" +
            "VIEW $mode   STEREO ACTIVE\n" +
            "DEPARTED ${nodes[floor].name}   APPROACHING $approaching\n" +
            "LEG ${(frac * 100f).toInt()}%   CUT  $unit = ${fmtCut(cut)}" + readLine + "\n" +
            cutLadder(cut) + "\n" +
            "HEADING ${(((craftYaw % 360f) + 360f) % 360f).toInt()} MARK   RAIL ${"%.2f".format(Locale.US, routeProgress)} / ${nodes.lastIndex}" + dbg
    }

    /**
     * How fine the cut is here.
     *
     * This is the sibling app's scale ladder with one word changed, and the change is the whole
     * series: the Mote's length in metres becomes the Caliper's jaw span in units of whatever is
     * varying — the h of a difference quotient, the width of a Riemann slab, the epsilon band, the
     * term index, the grid cell. A breakpoint table (rail progress -> value, log-linear between
     * points) that mirrors where the script's own cuts land.
     */
    private fun cutAt(p: Float): Double {
        val pc = p.coerceIn(map.lengthKeys.first(), map.lengthKeys.last())
        var i = 1
        while (i < map.lengthKeys.size - 1 && map.lengthKeys[i] < pc) i++
        val p0 = map.lengthKeys[i - 1]; val p1 = map.lengthKeys[i]
        val t = if (p1 > p0) ((pc - p0) / (p1 - p0)).toDouble() else 1.0
        val a = log10(map.lengthM[i - 1]); val b = log10(map.lengthM[i])
        return 10.0.pow(a + (b - a) * t)
    }

    /** Two significant figures, and a power of ten once the number stops being readable. */
    private fun fmtCut(v: Double): String {
        if (v <= 0.0) return "0"
        val r = roundSig(v, 2)
        return when {
            r >= 1000.0 -> superscript(log10(r))
            r >= 10.0 -> "%.0f".format(Locale.US, r)
            r >= 0.01 -> "%.3f".format(Locale.US, r).trimEnd('0').trimEnd('.')
            else -> superscript(log10(r))
        }
    }

    private fun roundSig(v: Double, sig: Int): Double {
        if (v <= 0.0) return 0.0
        val digits = floor(log10(v)).toInt() - (sig - 1)
        val unit = 10.0.pow(digits)
        return Math.round(v / unit) * unit
    }

    /** "10" with a Unicode superscript exponent: the only way a power fits on a HUD line. */
    private fun superscript(exp: Double): String {
        val e = Math.round(exp).toInt()
        val digits = "\u2070\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079"
        val sb = StringBuilder("10")
        if (e < 0) sb.append('\u207b')
        for (c in abs(e).toString()) sb.append(digits[c - '0'])
        return sb.toString()
    }

    /** The ladder of cuts, with the current rung bracketed. */
    private fun cutLadder(cut: Double): String {
        val cur = log10(cut.coerceAtLeast(1e-12))
        var best = 0; var bestD = Double.MAX_VALUE
        LADDER_EXP.forEachIndexed { i, e -> val d = abs(e - cur); if (d < bestD) { bestD = d; best = i } }
        val sb = StringBuilder()
        LADDER_LABELS.forEachIndexed { i, l -> sb.append(if (i == best) "[$l]" else " $l ") }
        return sb.toString()
    }

    // ------------------------------------------------------------ GL setup
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.01f, 0f, 0.012f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        // Some drivers only draw 1-px lines; clamp every glLineWidth to what the GPU offers.
        val range = FloatArray(2)
        GLES20.glGetFloatv(GLES20.GL_ALIASED_LINE_WIDTH_RANGE, range, 0)
        maxLineWidth = range[1].coerceAtLeast(1f)

        litShader = LitShader()
        colorShader = ColorShader()
        wallShader = WallShader()
        sphere = SphereMesh(22, 16)
        blob = SphereMesh(12, 8)
        tunnel = TubeMesh(buildTunnel())
        moteMesh = TriMesh(buildMote())
        cockpitMesh = LineMesh(buildCockpitLines())
        routeMesh = LineMesh(buildRouteLines())
        routeNodes = PointMesh(buildRouteNodes())
        loadPlates()
        flashBuf.position(0); flashBuf.put(flashData); flashBuf.position(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    /** GL thread: adopt a new tour — rebuild the passage and the route, forget the old chase state. */
    private fun applyMap(m: TourMap) {
        map = m; nodes = m.nodes
        sentinelIdx = nodes.indexOfFirst { MathScenes.of(it.scene)?.chases == true }
        tunnel.release(); routeMesh.release(); routeNodes.release()
        tunnel = TubeMesh(buildTunnel())
        routeMesh = LineMesh(buildRouteLines())
        routeNodes = PointMesh(buildRouteNodes())
        routeProgress = railTarget.coerceIn(0f, nodes.lastIndex.toFloat())
        sentInit = false; flightInit = false
        drift.reset(); bodies.reset(); air.reset()
        lysisClock = 0f
        glyphs.release()          // the old tour's notation will never be asked for again
        mapListener?.invoke(m)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = now
        val seconds = (now - startNanos) / 1_000_000_000f
        nowSeconds = seconds
        fpsFrames++
        if (now - fpsWindowStart > 1_000_000_000L) {
            fpsNow = fpsFrames * 1e9f / (now - fpsWindowStart).coerceAtLeast(1L)
            fpsFrames = 0; fpsWindowStart = now
        }
        pendingMap.getAndSet(null)?.let { applyMap(it) }
        updateFlight(dt, seconds)
        updateFx(dt)
        updateCamera(dt)
        val node = routeProgress.toInt().coerceIn(0, nodes.lastIndex)
        val amb = nodes[node].amb
        val spread = tunnelRadius(routeProgress) * 0.85f
        // Breathing in the airway: the air (and its dust) moves deeper on the inhale and back
        // toward the nose on the exhale, in step with the breath the ambience is playing.
        val breath = if (audioEngine.isRunning()) audioEngine.breathPhase01 else (seconds * 0.21f) % 1f
        val inAir = routeProgress < map.streamEnd
        airFlow = if (inAir) sin(breath * 2f * PI.toFloat()) * 2.6f else 0f
        // DriftField's flow is along +z (toward the nose on this rail): negative on the inhale.
        val dustFlow = flowSpeed(amb)
        // The room goes quiet in front of a demonstration, and the motes slow with it.
        audioEngine.focus = 1f - 0.62f * stillness
        drift.update(shipX, shipY, shipZ, spread, amb, dustFlow * (1f - 0.55f * stillness), dt)
        bodies.update(shipX, shipY, shipZ, spread, amb, flowSpeed(amb), dt)
        if (inAir) air.update(shipX, shipY, shipZ, dirX, dirY, dirZ, sideX, sideY, sideZ, upX, upY, upZ, spread, airFlow, dt)

        // Fixed FOV: on a head-worn display the rendered field must stay matched to the optics.
        // The shrink burst is a short camera dolly + streaks (see updateCamera), never a zoom.
        if (stereo) {
            val halfWidth = width / 2
            Matrix.perspectiveM(projection, 0, 58f, halfWidth.toFloat() / height.toFloat(), 0.15f, 220f)
            drawEye(0, halfWidth, -EYE_OFFSET, seconds)
            drawEye(halfWidth, width - halfWidth, EYE_OFFSET, seconds)
        } else {
            Matrix.perspectiveM(projection, 0, 58f, width.toFloat() / height.toFloat(), 0.15f, 220f)
            drawEye(0, width, 0f, seconds)
        }
    }

    private fun drawEye(x: Int, viewportWidth: Int, eyeOffset: Float, seconds: Float) {
        GLES20.glViewport(x, 0, viewportWidth, height)
        val ex = camNowX + sideX * eyeOffset; val ey = camNowY + sideY * eyeOffset; val ez = camNowZ + sideZ * eyeOffset
        val lx = lookNowX + sideX * eyeOffset * 0.35f
        val ly = lookNowY + sideY * eyeOffset * 0.35f
        val lz = lookNowZ + sideZ * eyeOffset * 0.35f
        Matrix.setLookAtM(view, 0, ex, ey, ez, lx, ly, lz, 0f, 1f, 0f)

        // The scale drop: the world (not the ship) is scaled about the ship's position, so walls
        // rush outward and everything ahead recedes; identical to the plain view when inflate = 1.
        if (abs(inflate - 1f) > 0.0005f) {
            Matrix.setIdentityM(inflM, 0)
            Matrix.translateM(inflM, 0, shipX, shipY, shipZ)
            Matrix.scaleM(inflM, 0, inflate, inflate, inflate)
            Matrix.translateM(inflM, 0, -shipX, -shipY, -shipZ)
            Matrix.multiplyMM(viewWorld, 0, view, 0, inflM, 0)
            System.arraycopy(view, 0, inflM, 0, 16)          // keep the plain view for the ship
            System.arraycopy(viewWorld, 0, view, 0, 16)
        } else {
            System.arraycopy(view, 0, inflM, 0, 16)
        }
        drawTunnel(seconds)
        drawRoute()
        drawLandmarks(seconds)
        drawBodies(seconds)
        drawDrift()
        drawAir()
        drawBeacon(seconds)
        System.arraycopy(inflM, 0, view, 0, 16)
        when (viewMode) {
            VIEW_BRIDGE -> drawCockpit()
            VIEW_CHASE -> drawMote(seconds)
            VIEW_ENGINEERING -> drawDriveCore(seconds)
            VIEW_OBSERVATION -> drawMote(seconds)
        }
        drawStreaks(seconds)
        drawFlash()
    }

    // ---------------------------------------------------------- simulation
    private fun flowSpeed(amb: Amb): Float = when (amb) {
        Amb.COUNT -> 1.4f
        Amb.PLANE -> 0.9f
        Amb.CURVE -> 2.0f
        Amb.LIMIT -> 0.25f          // almost still: everything is being held near a point
        Amb.SUM -> 2.4f
        Amb.INFINITE -> 1.1f
        Amb.SURFACE -> 0.8f
        Amb.FIELD -> 2.8f
        Amb.SOLVE -> 2.2f
        Amb.LOOKBACK -> 0.6f
    }

    private fun updateFlight(dt: Float, seconds: Float) {
        if (!scripted) {
            // Free drift for testing without the director: ~25 s per node.
            routeProgress = (routeProgress + dt / 25f).coerceIn(0f, nodes.lastIndex.toFloat())
        } else {
            // Follow the director's 10 Hz value with a critically damped lag so the walls glide
            // instead of stepping; a large gap (menu pick, resume) is an intentional teleport.
            val target = railTarget
            if (abs(target - routeProgress) > 0.5f) routeProgress = target
            else routeProgress += (target - routeProgress) * (1f - exp(-dt * 8f))
        }
        val f = frameAt(routeProgress)
        dirX = f.dx; dirY = f.dy; dirZ = f.dz
        sideX = f.sx; sideY = f.sy; sideZ = f.sz
        upX = f.ux; upY = f.uy; upZ = f.uz
        railCx = f.cx; railCy = f.cy; railCz = f.cz
        val node = routeProgress.toInt().coerceIn(0, nodes.lastIndex)
        val amb = nodes[node].amb

        // Flow sway: the Mote is carried, not flown. Two slow sines inside the passage,
        // plus a surge on each heartbeat in the vessels.
        // Alongside a stop the hull settles. The window is a little wider than the script's own
        // hold so the craft is already steady by the time the landmark is worth looking at.
        val toStop = abs(routeProgress - Math.round(routeProgress).toFloat())
        val target = (1f - (toStop / 0.34f)).coerceIn(0f, 1f)
        stillness += (target * target * (3f - 2f * target) - stillness) * (1f - exp(-dt * 1.6f))
        calmClock += dt * (1f - 0.96f * stillness)
        val calm = 1f - 0.94f * stillness

        val r = tunnelRadius(routeProgress)
        val swayA = 0.20f * r * sin(seconds * 0.55f) * calm
        val swayB = 0.14f * r * sin(seconds * 0.83f + 1.3f) * calm
        // A field carries the craft rather than the drive: a gentle unevenness along the rail.
        val surge = if (amb == Amb.FIELD || amb == Amb.SOLVE) 0.07f * sin(seconds * 1.1f) * calm else 0f
        val bob = if (showcase) 0.18f * sin(seconds * 0.8f) else 0f
        val tx = f.sx * swayA + f.ux * (swayB + bob) + f.dx * surge
        val ty = f.sy * swayA + f.uy * (swayB + bob) + f.dy * surge
        val tz = f.sz * swayA + f.uz * (swayB + bob) + f.dz * surge
        val k = 1f - exp(-dt * 1.4f)
        latX += (tx - latX) * k; latY += (ty - latY) * k; latZ += (tz - latZ) * k

        var sx = f.cx + latX; var sy = f.cy + latY; var sz = f.cz + latZ
        // Clearance from the Sentinel while it chases: an eased nudge through the sway offset,
        // never a one-frame snap (the cameras hang off the ship position).
        if (sentInit) {
            val rx = sx - sentX; val ry = sy - sentY; val rz = sz - sentZ
            val d = sqrt(rx * rx + ry * ry + rz * rz)
            val clr = 1.9f
            if (d < clr && d > 1e-3f) {
                val push = (clr - d) * (1f - exp(-dt * 6f))
                val nx = rx / d; val ny = ry / d; val nz = rz / d
                latX += nx * push; latY += ny * push; latZ += nz * push
                sx += nx * push; sy += ny * push; sz += nz * push
            }
        }
        if (flightInit) {
            velX += ((sx - shipX) - velX) * 0.25f
            velY += ((sy - shipY) - velY) * 0.25f
            velZ += ((sz - shipZ) - velZ) * 0.25f
        }
        shipX = sx; shipY = sy; shipZ = sz; flightInit = true

        // Heading: the hull is locked to the rail direction and only leans a little into the
        // flow sway (a craft carried by a current, not one spinning in it).
        // rotateM about +Y maps the nose (0,0,-1) to (-sin yaw, -cos yaw): yaw = atan2(-dx, -dz).
        val railYaw = atan2(-dirX, -dirZ) * 180f / PI.toFloat()
        val vs = velX * sideX + velY * sideY + velZ * sideZ
        val vu = velX * upX + velY * upY + velZ * upZ
        val vd = (velX * dirX + velY * dirY + velZ * dirZ).coerceAtLeast(1e-3f)
        val swayYaw = (atan2(vs, vd) * 180f / PI.toFloat()).coerceIn(-12f, 12f)
        val swayPitch = (atan2(vu, vd) * 180f / PI.toFloat()).coerceIn(-8f, 8f)
        run {
            var delta = (railYaw - swayYaw) - craftYaw
            while (delta > 180f) delta -= 360f
            while (delta < -180f) delta += 360f
            craftYaw += delta * (1f - exp(-dt * 3f))
        }
        val railPitch = atan2(dirY, sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(1e-4f)) * 180f / PI.toFloat()
        craftPitch += ((railPitch + swayPitch) - craftPitch) * (1f - exp(-dt * 3f))

        // Arm probes: fold along the hull, reach out where the crew touches the world.
        armKick = (armKick - dt / 3f).coerceAtLeast(0f)
        val armTarget = if (showcase) 0.22f + 0.16f * sin(seconds * 0.9f) else armReachTarget(routeProgress)
        armReach += (armTarget - armReach) * (1f - exp(-dt * 2f))

        // Heartbeat clock (visual): phase-locked to the audio engine's beat so the wall pulse
        // and the audible lub-dub coincide; free-runs at the same period if audio is stopped.
        if (audioEngine.isRunning()) {
            heartPhase = audioEngine.beatPhaseSec
        } else {
            heartPhase += dt
            if (heartPhase >= HEART_PERIOD) heartPhase -= HEART_PERIOD
        }
        // The wall brightens on a beat when the crew land a point. (In the sibling app this was
        // the heartbeat; here emphasis is the only thing that pulses.)
        heartKick = (heartKick - dt * 2.5f).coerceAtLeast(0f)
        wallPulse = heartKick

        // Sentinel: waits at its node, then chases the Mote through the vessel (tours that have one).
        if (sentinelIdx < 0) return
        val sn = nodes[sentinelIdx]
        val si = sentinelIdx.toFloat()
        if (!sentInit) { sentX = sn.x + 1.2f; sentY = sn.y - 0.6f; sentZ = sn.z + 2f; sentInit = true }
        val chase = routeProgress in (si - 0.45f)..(si + 0.75f)
        val tgtX: Float; val tgtY: Float; val tgtZ: Float
        if (chase) {
            tgtX = shipX + dirX * 3.4f + sideX * (1.3f * sin(seconds * 0.9f)) + upX * (0.5f * sin(seconds * 1.3f))
            tgtY = shipY + dirY * 3.4f + sideY * (1.3f * sin(seconds * 0.9f)) + upY * (0.5f * sin(seconds * 1.3f))
            tgtZ = shipZ + dirZ * 3.4f + sideZ * (1.3f * sin(seconds * 0.9f)) + upZ * (0.5f * sin(seconds * 1.3f))
        } else if (routeProgress > si + 0.75f) {
            // Chase over: it drops behind and hugs the wall, never crossing the Mote's lane.
            tgtX = shipX - dirX * 5f + sideX * 2.4f - upX * 0.6f
            tgtY = shipY - dirY * 5f + sideY * 2.4f - upY * 0.6f
            tgtZ = shipZ - dirZ * 5f + sideZ * 2.4f - upZ * 0.6f
        } else {
            tgtX = sn.x + 1.2f; tgtY = sn.y - 0.6f; tgtZ = sn.z + 2f
        }
        val sk = 1f - exp(-dt * (if (chase) 1.1f else 2.5f))
        sentX += (tgtX - sentX) * sk; sentY += (tgtY - sentY) * sk; sentZ += (tgtZ - sentZ) * sk
    }

    private fun smooth01(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    private fun updateFx(dt: Float) {
        beat = (beat - dt * 3.2f).coerceAtLeast(0f)
        shrinkBurst = (shrinkBurst - dt / SHRINK_SEC).coerceAtLeast(0f)
        // The drop: everything around the ship swells to ~2.6x within half a second, then the
        // camera "catches up" as the swell relaxes over a few seconds under the streaks; the
        // hull itself shrinks to a third in the external view and grows back as we settle.
        inflateT += dt
        val attack = smooth01(inflateT / 0.45f)
        val relax = exp(-(inflateT - 0.45f).coerceAtLeast(0f) / 2.2f)
        val swell = attack * relax
        val dwindle = smooth01(inflateT / 0.5f) * exp(-(inflateT - 0.6f).coerceAtLeast(0f) / 1.3f)
        // A rise (tour II) is the mirror image: the world contracts about the ship and the hull swells.
        inflate = if (growing) 1f / (1f + 1.2f * swell) else 1f + 1.6f * swell
        shipScale = if (growing) 1f + 0.9f * dwindle else 1f - 0.62f * dwindle
        lysisClock += dt
        if (lysisClock >= LYSIS_PERIOD) lysisClock -= LYSIS_PERIOD
        jumpIntensity = if (jumpOn) (jumpIntensity + dt * 2.2f).coerceAtMost(1f) else (jumpIntensity - dt * 2.2f).coerceAtLeast(0f)
        val tremble = max(beat, max(jumpIntensity * 0.45f, sin(shrinkBurst * PI.toFloat()) * 0.35f))
        // Low-passed tremble (new target ~12 times a second, eased), capped small for the HMD.
        shakeTimer += dt
        if (shakeTimer > 1f / 12f) {
            shakeTimer = 0f
            shakeTX = ((Math.random().toFloat() - 0.5f) * tremble * 0.06f).coerceIn(-0.03f, 0.03f)
            shakeTY = ((Math.random().toFloat() - 0.5f) * tremble * 0.06f).coerceIn(-0.03f, 0.03f)
        }
        val k = 1f - exp(-dt * 25f)
        shakeX += (shakeTX - shakeX) * k
        shakeY += (shakeTY - shakeY) * k
    }

    /** Camera position (0..2) and look-at (3..5) for a view mode. */
    private fun camForMode(mode: Int, out: FloatArray) {
        val px = shipX; val py = shipY; val pz = shipZ
        when (mode) {
            VIEW_CHASE -> {
                // A slow orbit around the stern (about 40 s per sweep) with a gentle bob: a
                // lingering stop still reads as a camera move, never a freeze-frame.
                val a = sin(calmClock * (if (showcase) 0.24f else 0.16f)) * 0.62f
                val back = 3.3f * cos(a); val swing = 3.3f * sin(a)
                val lift = 0.95f + 0.22f * sin(calmClock * 0.11f + 1f)
                out[0] = px - dirX * back + sideX * swing + upX * lift
                out[1] = py - dirY * back + sideY * swing + upY * lift
                out[2] = pz - dirZ * back + sideZ * swing + upZ * lift
                out[3] = px + dirX * 2.5f; out[4] = py + dirY * 2.5f; out[5] = pz + dirZ * 2.5f
            }
            VIEW_ENGINEERING -> {   // inside the hull, aft of the core, looking forward through it
                out[0] = px - dirX * 0.34f + upX * 0.05f; out[1] = py - dirY * 0.34f + upY * 0.05f; out[2] = pz - dirZ * 0.34f + upZ * 0.05f
                out[3] = px + dirX * 2.5f; out[4] = py + dirY * 2.5f; out[5] = pz + dirZ * 2.5f
            }
            VIEW_OBSERVATION -> {
                // Lateral offsets scale with the passage so the deck never pokes through a capillary wall.
                val r = tunnelRadius(routeProgress)
                val so = min(1.35f, 0.45f * r); val uo = min(0.55f, 0.18f * r)
                val along = -1.1f + 0.5f * sin(calmClock * 0.07f)          // slow dolly along the hull
                out[0] = px + dirX * along + sideX * so + upX * uo
                out[1] = py + dirY * along + sideY * so + upY * uo
                out[2] = pz + dirZ * along + sideZ * so + upZ * uo
                out[3] = px + dirX * 3.5f; out[4] = py + dirY * 3.5f; out[5] = pz + dirZ * 3.5f
            }
            else -> {   // bridge: behind the porthole (drawn 0.36 behind the ship), looking ahead
                out[0] = px - dirX * 0.60f + upX * 0.16f; out[1] = py - dirY * 0.60f + upY * 0.16f; out[2] = pz - dirZ * 0.60f + upZ * 0.16f
                out[3] = px + dirX * 4f; out[4] = py + dirY * 4f + 0.05f; out[5] = pz + dirZ * 4f
            }
        }
        clampToTube(out)
    }

    /** Keep a camera inside the passage: limit its lateral distance from the rail centre. */
    private fun clampToTube(out: FloatArray) {
        val r = tunnelRadius(routeProgress) * 0.72f
        val vx = out[0] - railCx; val vy = out[1] - railCy; val vz = out[2] - railCz
        val along = vx * dirX + vy * dirY + vz * dirZ
        val lx = vx - along * dirX; val ly = vy - along * dirY; val lz = vz - along * dirZ
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        if (ll > r && ll > 1e-4f) {
            val s = r / ll
            out[0] = railCx + along * dirX + lx * s
            out[1] = railCy + along * dirY + ly * s
            out[2] = railCz + along * dirZ + lz * s
        }
    }

    private fun updateCamera(dt: Float) {
        viewBlend = (viewBlend + dt / VIEW_TRANSITION_SEC).coerceAtMost(1f)
        val t = viewBlend * viewBlend * (3f - 2f * viewBlend)
        camForMode(prevViewMode, camA)
        camForMode(viewMode, camB)
        // A shrink is felt as a short push forward along the rail (plus streaks), not a zoom.
        val dolly = sin(shrinkBurst * PI.toFloat()) * (if (growing) -0.35f else 0.45f)
        // A beat shake in the middle of a demonstration is the camera shouting over the lesson.
        val shakeK = 1f - 0.92f * stillness
        camNowX = camA[0] + (camB[0] - camA[0]) * t + shakeX * shakeK + dirX * dolly
        camNowY = camA[1] + (camB[1] - camA[1]) * t + shakeY * shakeK + dirY * dolly
        camNowZ = camA[2] + (camB[2] - camA[2]) * t + dirZ * dolly
        lookNowX = camA[3] + (camB[3] - camA[3]) * t + dirX * dolly
        lookNowY = camA[4] + (camB[4] - camA[4]) * t + dirY * dolly
        lookNowZ = camA[5] + (camB[5] - camA[5]) * t + dirZ * dolly
        settleOnSubject(dt)
        // Head look-around: rotate the look direction by the gaze offset (yaw about world up,
        // pitch about the camera's right), leaving the camera position and the rail alone.
        val g = gaze
        if (g != null && (abs(g.yaw) > 1e-4f || abs(g.pitch) > 1e-4f)) {
            var fx = lookNowX - camNowX; var fy = lookNowY - camNowY; var fz = lookNowZ - camNowZ
            val len = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-4f)
            fx /= len; fy /= len; fz /= len
            var rx = -fz; var ry = 0f; var rz = fx                       // right = f x up(0,1,0) = (-fz, 0, fx)
            val rl = sqrt(rx * rx + rz * rz).coerceAtLeast(1e-4f); rx /= rl; rz /= rl
            val ux = ry * fz - rz * fy; val uy = rz * fx - rx * fz; val uz = rx * fy - ry * fx   // up = r x f
            val cy = cos(g.yaw); val sy = sin(g.yaw); val cp = cos(g.pitch); val sp = sin(g.pitch)
            val nx = (fx * cy + rx * sy) * cp + ux * sp
            val ny = (fy * cy + ry * sy) * cp + uy * sp
            val nz = (fz * cy + rz * sy) * cp + uz * sp
            lookNowX = camNowX + nx * len; lookNowY = camNowY + ny * len; lookNowZ = camNowZ + nz * len
        }
        // The eye's own basis, for billboarded notation. right = forward x worldUp, up = right x
        // forward — in that order. The reverse is the sign error that once mirrored the whole
        // look-around on this hardware; do not swap them.
        var fx = lookNowX - camNowX; var fy = lookNowY - camNowY; var fz = lookNowZ - camNowZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-4f)
        fx /= fl; fy /= fl; fz /= fl
        var rx = -fz; var ry = 0f; var rz = fx
        val rl = sqrt(rx * rx + rz * rz)
        if (rl < 1e-4f) { rx = 1f; ry = 0f; rz = 0f } else { rx /= rl; rz /= rl }
        camRightX = rx; camRightY = ry; camRightZ = rz
        camUpX = ry * fz - rz * fy; camUpY = rz * fx - rx * fz; camUpZ = rx * fy - ry * fx
    }

    /**
     * Ease the camera into a composed, steady view of whatever the current stop is presenting.
     *
     * The flying cameras are built around the ship — they trail it, orbit it, sit inside it — which
     * is right while travelling and wrong while being taught. A viewer trying to read a figure does
     * not want the frame drifting, and on a head-worn display they cannot look away from it. So as
     * [stillness] comes up, the camera blends from wherever the view mode put it toward a fixed
     * three-quarter view of the stop's subject: standing back far enough to hold the whole figure,
     * a little to the open side of the passage and slightly above, looking straight at it.
     *
     * The pose is anchored to the STOP, not to the craft. The craft keeps drifting slowly through
     * the stop, as the pacing rules require, and simply moves through a frame that does not move.
     */
    private fun settleOnSubject(dt: Float) {
        // Latch: commit once the craft is clearly at the stop, release once it is clearly leaving,
        // and hold whatever we had in between.
        val want = if (stillness > 0.55f) 1f else if (stillness < 0.22f) 0f else presW
        presW += (want - presW) * (1f - exp(-dt * 1.1f))
        if (presW < 0.01f) {
            // In transit the settled pose just shadows the live camera, so there is nothing to
            // snap back from the moment a stop comes into range.
            presX = camNowX; presY = camNowY; presZ = camNowZ
            presLX = lookNowX; presLY = lookNowY; presLZ = lookNowZ
            presInit = true
            return
        }
        // THE CORE is the engine room: its whole point is being somewhere else, inside the hull,
        // so it is the one view that does not reframe onto the subject. Tapping into it is a real
        // change of place, and tapping out returns to a framed view.
        if (viewMode == VIEW_ENGINEERING) {
            presX = camNowX; presY = camNowY; presZ = camNowZ
            presLX = lookNowX; presLY = lookNowY; presLZ = lookNowZ
            return
        }
        val i = Math.round(routeProgress).coerceIn(0, nodes.lastIndex)
        val scene = MathScenes.of(nodes[i].scene) ?: return
        val f = frameAt(i.toFloat())
        val fs = scene.focusSide; val fu = scene.focusUp; val fa = scene.focusAhead
        val subX = f.cx + f.sx * fs + f.ux * fu + f.dx * fa
        val subY = f.cy + f.sy * fs + f.uy * fu + f.dy * fa
        val subZ = f.cz + f.sz * fs + f.uz * fu + f.dz * fa
        // Far enough back that the subject's radius sits inside the 58-degree frustum with margin.
        val dist = (scene.focusRadius / 0.42f).coerceIn(2.4f, 10f)
        // Each view mode is a different vantage on the SAME subject, so a tap still changes the
        // view while the thing being presented stays framed. Without this the settled camera
        // swallowed the view modes whole and tapping appeared to do nothing at a stop.
        val back: Float; val across: Float; val above: Float
        when (viewMode) {
            VIEW_BRIDGE -> { back = 0.95f; across = 0.10f; above = 0.09f }        // from the seat, square on
            VIEW_OBSERVATION -> { back = 0.80f; across = -0.34f; above = 0.15f }  // from the other side, a touch closer
            else -> { back = 0.80f; across = 0.42f; above = 0.22f }               // chase: three-quarter, craft in shot
        }
        val tgtX = subX - f.dx * dist * back + f.sx * dist * across + f.ux * dist * above
        val tgtY = subY - f.dy * dist * back + f.sy * dist * across + f.uy * dist * above
        val tgtZ = subZ - f.dz * dist * back + f.sz * dist * across + f.uz * dist * above
        // Where the subject sits in the field of view. A small default bias keeps it clear of the
        // telemetry at the top of the eye, and the viewer's own calibration moves it from there:
        // people hold their heads differently and one setting does not suit two faces.
        val lift = scene.focusRadius * (0.05f + Calibration.aimBias)
        val aimX = subX + f.ux * lift
        val aimY = subY + f.uy * lift
        val aimZ = subZ + f.uz * lift
        val w = presW
        // Blend between the view mode's camera and the composed one, then ease into that blend so
        // the change of framing is a slow steadying rather than a cut.
        val bx = camNowX + (tgtX - camNowX) * w
        val by = camNowY + (tgtY - camNowY) * w
        val bz = camNowZ + (tgtZ - camNowZ) * w
        val lx = lookNowX + (aimX - lookNowX) * w
        val ly = lookNowY + (aimY - lookNowY) * w
        val lz = lookNowZ + (aimZ - lookNowZ) * w
        if (!presInit) { presX = bx; presY = by; presZ = bz; presLX = lx; presLY = ly; presLZ = lz; presInit = true }
        val k = 1f - exp(-dt * 1.7f)
        presX += (bx - presX) * k; presY += (by - presY) * k; presZ += (bz - presZ) * k
        presLX += (lx - presLX) * k; presLY += (ly - presLY) * k; presLZ += (lz - presLZ) * k
        camNowX = presX; camNowY = presY; camNowZ = presZ
        lookNowX = presLX; lookNowY = presLY; lookNowZ = presLZ
    }

    // The Mote's bow lamp lights the world: just ahead of the ship.
    private fun lampX() = shipX + dirX * 0.7f
    private fun lampY() = shipY + dirY * 0.7f
    private fun lampZ() = shipZ + dirZ * 0.7f

    // ---------------------------------------------------------- draw: world
    private fun drawTunnel(seconds: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        // Time wraps at a common period of every sin(uTime * k) in the shader (k = 1.5, 0.3) so the
        // argument stays small for half-precision GPUs without a visible seam.
        // How solid the passage is here. The last stop of every tour takes this to zero and the
        // corridor becomes a thread hanging in the dark — the view from outside. A transparent wall
        // must also stop writing depth, or it would carve a hole in the star field behind it.
        val wa = nodeLerp(routeProgress) { it.wallAlpha }
        if (wa <= 0.01f) { GLES20.glEnable(GLES20.GL_CULL_FACE); return }
        if (wa < 0.99f) GLES20.glDepthMask(false)
        wallShader.use(mvp, model, lampX(), lampY(), lampZ(), seconds % TIME_WRAP, wallPulse, 0.02f, wa, if (quality == 0) 1f else 0f)
        tunnel.draw(wallShader.positionHandle, wallShader.normalHandle, wallShader.colorHandle)
        if (wa < 0.99f) GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawRoute() {
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.use(mvp, 1f)
        routeMesh.draw(colorShader.positionHandle, colorShader.colorHandle)
        colorShader.use(mvp, 6f, points = true)
        routeNodes.draw(colorShader.positionHandle, colorShader.colorHandle)
    }

    private fun drawDrift() {
        GLES20.glDepthMask(false)
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.use(mvp, 3.2f, points = true)
        drift.draw(colorShader.positionHandle, colorShader.colorHandle)
        GLES20.glDepthMask(true)
    }

    /** Airflow streaks (nodes 0-2), fading out as the ride leaves the lungs. */
    private fun drawAir() {
        val fade = ((map.streamEnd - routeProgress) / 0.5f).coerceIn(0f, 1f)
        if (fade <= 0f) return
        GLES20.glDepthMask(false)
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.globalFade = fade
        colorShader.use(mvp, 1f)
        lineWidth(2f)
        air.draw(colorShader.positionHandle, colorShader.colorHandle)
        lineWidth(1f)
        colorShader.globalFade = 1f
        GLES20.glDepthMask(true)
    }

    private fun drawBodies(seconds: Float) {
        val n = when (quality) { 0 -> bodies.count; 1 -> bodies.count / 2; else -> bodies.count / 3 }
        for (i in 0 until n) {
            val kind = bodies.kind[i]
            // Skip anything already behind the camera.
            if ((bodies.px[i] - camNowX) * dirX + (bodies.py[i] - camNowY) * dirY + (bodies.pz[i] - camNowZ) * dirZ < -1f) continue
            val s = bodies.size[i]
            val spin = seconds * 40f + bodies.spin[i] * 360f
            when (kind) {
                BodyField.LATTICE -> drawSphereAt(bodies.px[i], bodies.py[i], bodies.pz[i], s, s, s, COL_COUNT, COL_LAMP, 1f, 0f, 0f, 1f, 0f, blob, 0f, 0.35f)
                BodyField.SAMPLE -> drawSphereAt(bodies.px[i], bodies.py[i], bodies.pz[i], s, s, s, COL_SAMPLE, COL_LAMP, 0.95f, 0f, 0f, 1f, 0f, blob, 0f, 0.5f)
                BodyField.SLAB -> drawSphereAt(bodies.px[i], bodies.py[i], bodies.pz[i], s, s * 0.06f, s, COL_SLAB, COL_LAMP, 0.75f, spin * 0.2f, 0f, 1f, 0f, blob, 0f, 0.2f)
                BodyField.ARROW -> drawSphereAt(bodies.px[i], bodies.py[i], bodies.pz[i], s * 0.16f, s * 0.16f, s, COL_FIELD, COL_LAMP, 0.9f, spin * 0.15f, 0.3f, 1f, 0f, blob, 0f, 0.35f)
                BodyField.NEEDLE -> drawSphereAt(bodies.px[i], bodies.py[i], bodies.pz[i], s * 0.10f, s * 0.10f, s, COL_CURVE, COL_LAMP, 0.85f, spin * 0.4f, 1f, 0.2f, 0f, blob, 0f, 0.3f)
                BodyField.FLAKE -> drawSphereAt(bodies.px[i], bodies.py[i], bodies.pz[i], s, s * 0.10f, s, COL_SURFACE, COL_LAMP, 0.7f, 0f, 0f, 1f, 0f, blob, 0f, 0.2f)
            }
        }
    }

    private fun drawBeacon(seconds: Float) {
        val idx = (routeProgress.toInt() + 1).coerceIn(0, nodes.lastIndex)
        val frac = routeProgress - routeProgress.toInt()
        if (frac < 0.45f || idx == routeProgress.toInt()) return        // only once we are truly under way
        val b = nodes[idx]
        val pulse = 0.5f + 0.5f * sin(seconds * 3f)
        beaconData[0] = b.x; beaconData[1] = b.y + 0.6f; beaconData[2] = b.z
        beaconData[3] = 1f; beaconData[4] = 0.77f; beaconData[5] = 0.42f; beaconData[6] = 0.10f + 0.22f * pulse
        beaconBuf.position(0); beaconBuf.put(beaconData); beaconBuf.position(0)
        GLES20.glDepthMask(false)
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.use(mvp, 4f + 3f * pulse, points = true)
        beaconBuf.position(0)
        GLES20.glVertexAttribPointer(colorShader.positionHandle, 3, GLES20.GL_FLOAT, false, 28, beaconBuf)
        GLES20.glEnableVertexAttribArray(colorShader.positionHandle)
        beaconBuf.position(3)
        GLES20.glVertexAttribPointer(colorShader.colorHandle, 4, GLES20.GL_FLOAT, false, 28, beaconBuf)
        GLES20.glEnableVertexAttribArray(colorShader.colorHandle)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        GLES20.glDisableVertexAttribArray(colorShader.positionHandle)
        GLES20.glDisableVertexAttribArray(colorShader.colorHandle)
        GLES20.glDepthMask(true)
    }

    // ----------------------------------------------------- draw: landmarks
    /**
     * Every stop's landmark, faded in as the craft approaches it.
     *
     * The scenes themselves live in their own files and reach the renderer only through
     * [SceneKit]; this decides WHICH ones are close enough to be worth drawing, how far faded
     * they are, and hands each the kit. A scene declares its own [MathScene.reach] (how far out
     * it starts to appear) and [MathScene.deep] (how far past its stop its geometry extends), so
     * a graph that runs the length of the passage is not culled at its origin.
     */
    private fun drawLandmarks(seconds: Float) {
        glyphs.beginFrame()
        // The tour's own persistent object — the roof curve, the wake, the open terrain — drawn
        // every frame at full strength wherever the craft is, not tied to any one stop.
        val ambient = map.ambient?.let { MathScenes.of(it) }
        if (ambient != null) {
            val here = routeProgress.toInt().coerceIn(0, nodes.lastIndex)
            landmarkFade = 1f
            colorShader.globalFade = 1f
            ambient.draw(kit, nodes[here], here)
        }
        for (i in nodes.indices) {
            val n = nodes[i]
            val scene = MathScenes.of(n.scene) ?: continue
            val reach = scene.reach - 0.25f * quality
            val fade = ((reach - abs(routeProgress - i)) / 0.5f).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            // Landmarks well behind the camera cost draw calls and show nothing — but a scene may
            // reach a long way past its node, so the test uses its deepest part, not its origin.
            val deep = scene.deep
            val c = if (deep > 0f) frameAt(i + deep) else null
            val ox = c?.cx ?: n.x; val oy = c?.cy ?: n.y; val oz = c?.cz ?: n.z
            if ((ox - camNowX) * dirX + (oy - camNowY) * dirY + (oz - camNowZ) * dirZ < -8f) continue
            landmarkFade = fade
            colorShader.globalFade = fade
            scene.draw(kit, n, i)
        }
        landmarkFade = 1f
        colorShader.globalFade = 1f
    }

    // ---------------------------------------------------------- picture plates
    /**
     * Chapter III shows three real pictures of Bethune. They are the only bitmaps in the whole
     * app — everything else is procedural — so the loader is deliberately forgiving: if the
     * assets are missing (or a device refuses them) the chapter simply runs without them.
     */
    private fun loadPlates() {
        val ctx = context ?: return
        plateShader = PlateShader()
        for (name in PLATE_FILES) {
            try {
                val bmp = ctx.assets.open("plates/$name.jpg").use { BitmapFactory.decodeStream(it) } ?: continue
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                plates[name] = Plate(ids[0], bmp.width.toFloat() / bmp.height.toFloat())
                bmp.recycle()
            } catch (e: Exception) {
                android.util.Log.w("MCPlate", "no plate $name", e)
            }
        }
    }

    private class Plate(val texture: Int, val aspect: Float)

    /**
     * Hang one picture in the passage: a lit frame around it, square to the rail and turned a
     * little toward the lane, so the crew fly past it the way you walk past a picture on a wall.
     */
    private fun drawPlate(name: String, f: Frame, side: Float, up: Float, height: Float, seconds: Float) {
        val plate = plates[name] ?: return
        val sh = plateShader ?: return
        val h = height; val w = height * plate.aspect
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, fx(f, 0f, side, up), fy(f, 0f, side, up), fz(f, 0f, side, up))
        applyFrameRotation(f)
        Matrix.rotateM(model, 0, if (side < 0f) 28f else -28f, 0f, 1f, 0f)   // angled toward the passage
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        // The picture is the point of the stop, so it is drawn over the world rather than into it:
        // in a passage only a few units wide the far half of the plate would otherwise be buried in
        // the wall and the person in it sliced off. Depth is neither tested nor written, so the
        // Mote (drawn later, with depth) still passes in front of it correctly.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        colorShader.use(mvp, 1f)
        val fr = 0.06f + 0.012f * sin(seconds * 1.1f)
        plateFrame(w * 0.5f + fr, h * 0.5f + fr)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        sh.use(mvp, plate.texture, landmarkFade, 0.55f + 0.45f * (0.5f + 0.5f * sin(seconds * 0.7f)))
        plateQuad(w * 0.5f, h * 0.5f, sh)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private val plateBuf = ByteBuffer.allocateDirect(6 * 5 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val frameBuf = ByteBuffer.allocateDirect(8 * 7 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun plateQuad(hw: Float, hh: Float, sh: PlateShader) {
        val d = floatArrayOf(
            -hw, -hh, 0f, 0f, 1f,   hw, -hh, 0f, 1f, 1f,   hw, hh, 0f, 1f, 0f,
            -hw, -hh, 0f, 0f, 1f,   hw, hh, 0f, 1f, 0f,   -hw, hh, 0f, 0f, 0f)
        plateBuf.position(0); plateBuf.put(d); plateBuf.position(0)
        GLES20.glVertexAttribPointer(sh.positionHandle, 3, GLES20.GL_FLOAT, false, 20, plateBuf)
        GLES20.glEnableVertexAttribArray(sh.positionHandle)
        plateBuf.position(3)
        GLES20.glVertexAttribPointer(sh.uvHandle, 2, GLES20.GL_FLOAT, false, 20, plateBuf)
        GLES20.glEnableVertexAttribArray(sh.uvHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(sh.positionHandle)
        GLES20.glDisableVertexAttribArray(sh.uvHandle)
    }

    private fun plateFrame(hw: Float, hh: Float) {
        val c = COL_LAMP
        val d = FloatArray(8 * 7)
        val pts = floatArrayOf(-hw, -hh, hw, -hh, hw, -hh, hw, hh, hw, hh, -hw, hh, -hw, hh, -hw, -hh)
        for (k in 0 until 8) {
            val o = k * 7
            d[o] = pts[k * 2]; d[o + 1] = pts[k * 2 + 1]; d[o + 2] = 0f
            d[o + 3] = c[0]; d[o + 4] = c[1]; d[o + 5] = c[2]; d[o + 6] = 0.85f * landmarkFade
        }
        frameBuf.position(0); frameBuf.put(d); frameBuf.position(0)
        lineWidth(3f)
        GLES20.glVertexAttribPointer(colorShader.positionHandle, 3, GLES20.GL_FLOAT, false, 28, frameBuf)
        GLES20.glEnableVertexAttribArray(colorShader.positionHandle)
        frameBuf.position(3)
        GLES20.glVertexAttribPointer(colorShader.colorHandle, 4, GLES20.GL_FLOAT, false, 28, frameBuf)
        GLES20.glEnableVertexAttribArray(colorShader.colorHandle)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 8)
        GLES20.glDisableVertexAttribArray(colorShader.positionHandle)
        GLES20.glDisableVertexAttribArray(colorShader.colorHandle)
        lineWidth(1f)
    }







    // ------------------------------------------------------- draw: the ship
    /** Local hull coordinates (x right, y up, -z forward) to world, through the hull's yaw and pitch. */
    private fun shipToWorld(lx0: Float, ly0: Float, lz0: Float, out: FloatArray) {
        val lx = lx0 * shipScale; val ly = ly0 * shipScale; val lz = lz0 * shipScale
        val cp = cos(craftPitch * DEG); val sp = sin(craftPitch * DEG)
        val cy = cos(craftYaw * DEG); val sy = sin(craftYaw * DEG)
        val y1 = ly * cp - lz * sp; val z1 = ly * sp + lz * cp        // Rx(pitch)
        val x2 = lx * cy + z1 * sy; val z2 = -lx * sy + z1 * cy       // Ry(yaw)
        out[0] = shipX + x2; out[1] = shipY + y1; out[2] = shipZ + z2
    }

    /** A rod between two world points (an elongated sphere aligned with the segment). */
    private fun drawStrut(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, radius: Float, base: FloatArray, accent: FloatArray, glow: Float = 0f) {
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        val nx = dx / len; val ny = dy / len; val nz = dz / len
        // rotate local +z onto n: axis = z x n = (-ny, nx, 0), angle = acos(nz)
        val axX = -ny; val axY = nx
        val al = sqrt(axX * axX + axY * axY)
        val ang = acos(nz.coerceIn(-1f, 1f)) * 180f / PI.toFloat()
        if (al < 1e-4f) drawSphereAt((ax + bx) * 0.5f, (ay + by) * 0.5f, (az + bz) * 0.5f, radius, radius, len * 0.5f, base, accent, 1f, 0f, 0f, 1f, 0f, blob, 0f, glow)
        else drawSphereAt((ax + bx) * 0.5f, (ay + by) * 0.5f, (az + bz) * 0.5f, radius, radius, len * 0.5f, base, accent, 1f, ang, axX / al, axY / al, 0f, blob, 0f, glow)
    }

    private fun drawMote(seconds: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, shipX, shipY, shipZ)
        Matrix.rotateM(model, 0, craftYaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, craftPitch, 1f, 0f, 0f)
        Matrix.scaleM(model, 0, shipScale, shipScale, shipScale)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.use(mvp, 5.5f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        moteMesh.draw(colorShader.positionHandle, colorShader.colorHandle)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        val pulse = 0.5f + 0.5f * sin(seconds * 4f)
        // Hover pads: six glowing discs under the pontoons.
        for (sgn in floatArrayOf(-1f, 1f)) for (k in 0 until 3) {
            shipToWorld(0.34f * sgn, -0.17f, -0.38f + 0.38f * k, tmpW)
            drawSphereAt(tmpW[0], tmpW[1], tmpW[2], 0.10f, 0.022f, 0.10f, COL_PAD, COL_PAD, 0.95f, craftYaw, 0f, 1f, 0f, blob, 0f, 0.35f + 0.3f * pulse)
        }
        // Bow lamp, twin exhausts, and the drive ring turning around the stern.
        shipToWorld(0f, 0.02f, -0.78f, tmpW)
        drawSphereAt(tmpW[0], tmpW[1], tmpW[2], 0.06f, 0.06f, 0.06f, COL_LAMP, COL_LAMP, 1f, 0f, 0f, 1f, 0f, blob, 0f, 2.5f)
        for (sgn in floatArrayOf(-1f, 1f)) {
            shipToWorld(0.12f * sgn, -0.02f, 0.75f, tmpW)
            drawSphereAt(tmpW[0], tmpW[1], tmpW[2], 0.032f, 0.032f, 0.02f, COL_DRIVE_DIM, COL_DRIVE, 1f, craftYaw, 0f, 1f, 0f, blob, 0f, 0.5f + 0.3f * pulse)
        }
        val spin = seconds * 240f * DEG
        for (k in 0 until 10) {
            val a = 2f * PI.toFloat() * k / 10f + spin
            shipToWorld(cos(a) * 0.20f, sin(a) * 0.14f, 0.68f, tmpW)
            drawSphereAt(tmpW[0], tmpW[1], tmpW[2], 0.014f, 0.014f, 0.014f, COL_DRIVE, COL_DRIVE, 0.9f, 0f, 0f, 1f, 0f, blob, 0f, 0.6f + 0.5f * sin(seconds * 6f + k))
        }
        drawArms(seconds)
    }

    /** Two articulated probes: shoulder at the bow mounts, elbow, and a glowing sensor tip. */
    private fun drawArms(seconds: Float) {
        val r = armReach * armReach * (3f - 2f * armReach)
        val wob = sin(seconds * 1.7f) * 0.03f
        for (sgn in floatArrayOf(-1f, 1f)) {
            val sx = 0.24f * sgn; val sy = -0.06f; val sz = -0.60f
            // folded: back along the pontoon; reaching: forward and outward, tips ahead of the bow
            val ex = sx + lerp(0.06f * sgn, 0.22f * sgn, r); val ey = sy + lerp(-0.02f, 0.05f + wob, r); val ez = sz + lerp(0.33f, -0.27f, r)
            val tx = ex + lerp(0.02f * sgn, 0.04f * sgn, r); val ty = ey + lerp(0f, -0.04f - wob, r); val tz = ez + lerp(0.29f, -0.30f, r)
            shipToWorld(sx, sy, sz, tmpS); shipToWorld(ex, ey, ez, tmpE); shipToWorld(tx, ty, tz, tmpT)
            drawStrut(tmpS[0], tmpS[1], tmpS[2], tmpE[0], tmpE[1], tmpE[2], 0.028f, COL_HULL_DARK, COL_LAMP)
            drawSphereAt(tmpE[0], tmpE[1], tmpE[2], 0.04f, 0.04f, 0.04f, COL_HULL, COL_LAMP, 1f, 0f, 0f, 1f, 0f, blob)
            drawStrut(tmpE[0], tmpE[1], tmpE[2], tmpT[0], tmpT[1], tmpT[2], 0.022f, COL_HULL_DARK, COL_LAMP)
            drawSphereAt(tmpT[0], tmpT[1], tmpT[2], 0.036f, 0.036f, 0.036f, COL_LAMP, COL_LAMP, 1f, 0f, 0f, 1f, 0f, blob, 0f, 0.4f + 1.8f * r)
        }
    }

    /** The engine room: inside the hull, the scale drive core with its rotor ring and stator struts. */
    private fun drawDriveCore(seconds: Float) {
        // The hull around us (its inner faces), without the outboard fittings that would show
        // through the near-plane gap in the roof.
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, shipX, shipY, shipZ)
        Matrix.rotateM(model, 0, craftYaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, craftPitch, 1f, 0f, 0f)
        Matrix.scaleM(model, 0, shipScale, shipScale, shipScale)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.use(mvp, 5.5f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        moteMesh.draw(colorShader.positionHandle, colorShader.colorHandle)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        val pulse = 0.55f + 0.45f * (0.5f + 0.5f * sin(seconds * 3.2f))
        shipToWorld(0f, 0f, -0.12f, tmpW)          // local -z is forward: the core sits just ahead of centre
        val cx = tmpW[0]; val cy = tmpW[1]; val cz = tmpW[2]
        drawSphereAt(cx, cy, cz, 0.07f, 0.07f, 0.07f, floatArrayOf(0.55f * pulse, 0.42f * pulse, 1f, 1f), COL_DRIVE, 1f, 0f, 0f, 1f, 0f, blob, 0f, 0.7f)
        // The rotor: a ring of beads turning like ATP synthase, held by four stator struts.
        val spin = seconds * 300f * DEG
        for (k in 0 until 12) {
            val a = 2f * PI.toFloat() * k / 12f + spin
            val rr = 0.20f
            val ox = sideX * cos(a) + upX * sin(a); val oy = sideY * cos(a) + upY * sin(a); val oz = sideZ * cos(a) + upZ * sin(a)
            drawSphereAt(cx + ox * rr, cy + oy * rr, cz + oz * rr, 0.016f, 0.016f, 0.03f, COL_DRIVE, COL_LAMP, 1f, 0f, 0f, 1f, 0f, blob, 0f, 0.4f)
        }
        for (k in 0 until 4) {
            val a = PI.toFloat() * 0.5f * k
            val ox = sideX * cos(a) + upX * sin(a); val oy = sideY * cos(a) + upY * sin(a); val oz = sideZ * cos(a) + upZ * sin(a)
            drawStrut(cx + ox * 0.10f, cy + oy * 0.10f, cz + oz * 0.10f, cx + ox * 0.30f, cy + oy * 0.30f, cz + oz * 0.30f, 0.012f, COL_STATOR, COL_LAMP, 0.3f)
        }
    }

    private fun drawCockpit() {
        // A head-locked frame: drawn at the eye, facing the heading, proportioned so the porthole
        // (0.55 x 0.40 at 1.0 ahead) and the console sit inside the 58-degree frustum. A stable
        // foreground frame is the comfort anchor the build guide asks for.
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, camNowX - shakeX, camNowY - shakeY, camNowZ)
        // Face the camera's own look direction (not the hull's sway yaw) so the frame stays square.
        val lx = lookNowX - camNowX; val ly = lookNowY - camNowY; val lz = lookNowZ - camNowZ
        val lookYaw = atan2(-lx, -lz) * 180f / PI.toFloat()
        val lookPitch = atan2(ly, sqrt(lx * lx + lz * lz).coerceAtLeast(1e-4f)) * 180f / PI.toFloat()
        Matrix.rotateM(model, 0, lookYaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, lookPitch, 1f, 0f, 0f)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.use(mvp, 3.5f)
        lineWidth(2f)
        cockpitMesh.draw(colorShader.positionHandle, colorShader.colorHandle)
        lineWidth(1f)
    }

    // --------------------------------------------------------- draw: overlays
    private fun drawStreaks(seconds: Float) {
        val burst = sin(shrinkBurst * PI.toFloat())
        val intensity = max(jumpIntensity, burst)
        if (intensity < 0.02f) return
        var k = 0
        for (i in 0 until streakCount) {
            val a = streakSeeds[i * 2] * 6.2832f
            val seed = streakSeeds[i * 2 + 1]
            val rush = (seconds * (1.6f + seed * 2.4f) + seed * 7f) % 1f
            val r0 = 0.08f + rush * 0.9f
            val len = (0.25f + seed * 0.55f) * intensity
            val ca = cos(a); val sa = sin(a)
            val alpha = intensity * (0.25f + 0.55f * seed) * (1f - rush * 0.6f)
            streakData[k++] = ca * r0; streakData[k++] = sa * r0; streakData[k++] = 0f
            streakData[k++] = if (growing) 0.6f else 1f; streakData[k++] = if (growing) 0.95f else 0.72f; streakData[k++] = if (growing) 0.85f else 0.62f; streakData[k++] = alpha
            val r1 = r0 + len
            streakData[k++] = ca * r1; streakData[k++] = sa * r1; streakData[k++] = 0f
            streakData[k++] = if (growing) 0.4f else 0.75f; streakData[k++] = if (growing) 0.7f else 0.45f; streakData[k++] = 0.8f; streakData[k++] = alpha * 0.4f
        }
        streakBuf.position(0); streakBuf.put(streakData); streakBuf.position(0)
        GLES20.glDepthMask(false); GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        lineWidth(3f)
        colorShader.use(identityM, 1f)
        streakBuf.position(0)
        GLES20.glVertexAttribPointer(colorShader.positionHandle, 3, GLES20.GL_FLOAT, false, 28, streakBuf)
        GLES20.glEnableVertexAttribArray(colorShader.positionHandle)
        streakBuf.position(3)
        GLES20.glVertexAttribPointer(colorShader.colorHandle, 4, GLES20.GL_FLOAT, false, 28, streakBuf)
        GLES20.glEnableVertexAttribArray(colorShader.colorHandle)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, streakCount * 2)
        GLES20.glDisableVertexAttribArray(colorShader.positionHandle)
        GLES20.glDisableVertexAttribArray(colorShader.colorHandle)
        lineWidth(1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(true)
    }

    private fun lineWidth(w: Float) = GLES20.glLineWidth(min(w, maxLineWidth))

    private fun drawFlash() {
        if (beat < 0.01f) return
        val a = beat * 0.28f
        var i = 6
        while (i < flashData.size) { flashData[i] = a; i += 7 }
        flashBuf.position(0); flashBuf.put(flashData); flashBuf.position(0)
        GLES20.glDepthMask(false); GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        colorShader.use(identityM, 1f)
        flashBuf.position(0)
        GLES20.glVertexAttribPointer(colorShader.positionHandle, 3, GLES20.GL_FLOAT, false, 28, flashBuf)
        GLES20.glEnableVertexAttribArray(colorShader.positionHandle)
        flashBuf.position(3)
        GLES20.glVertexAttribPointer(colorShader.colorHandle, 4, GLES20.GL_FLOAT, false, 28, flashBuf)
        GLES20.glEnableVertexAttribArray(colorShader.colorHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(colorShader.positionHandle)
        GLES20.glDisableVertexAttribArray(colorShader.colorHandle)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(true)
    }

    // ------------------------------------------------------------- scene kit
    /**
     * The renderer's side of [SceneKit]: the only surface a landmark scene can touch.
     *
     * It is an inner class so it can reach the renderer's private drawing helpers without
     * exposing them, and a single long-lived instance so no scene allocates anything to draw.
     */
    private inner class Kit : SceneKit {
        private val f = FloatArray(12)

        override val seconds: Float get() = nowSeconds
        override val progress: Float get() = routeProgress
        override val quality: Int get() = this@StereoMathRenderer.quality
        override val fade: Float get() = landmarkFade
        override val beat: Float get() = this@StereoMathRenderer.beat
        override val reach: Float get() = armReach
        override val shipX: Float get() = this@StereoMathRenderer.shipX
        override val shipY: Float get() = this@StereoMathRenderer.shipY
        override val shipZ: Float get() = this@StereoMathRenderer.shipZ
        override val camX: Float get() = camNowX
        override val camY: Float get() = camNowY
        override val camZ: Float get() = camNowZ
        override val camRightX: Float get() = this@StereoMathRenderer.camRightX
        override val camRightY: Float get() = this@StereoMathRenderer.camRightY
        override val camRightZ: Float get() = this@StereoMathRenderer.camRightZ
        override val camUpX: Float get() = this@StereoMathRenderer.camUpX
        override val camUpY: Float get() = this@StereoMathRenderer.camUpY
        override val camUpZ: Float get() = this@StereoMathRenderer.camUpZ

        override fun frame(p: Float, out: FloatArray) {
            val fr = frameAt(p)
            out[0] = fr.cx; out[1] = fr.cy; out[2] = fr.cz
            out[3] = fr.dx; out[4] = fr.dy; out[5] = fr.dz
            out[6] = fr.sx; out[7] = fr.sy; out[8] = fr.sz
            out[9] = fr.ux; out[10] = fr.uy; out[11] = fr.uz
        }

        override fun radius(p: Float): Float = tunnelRadius(p)

        override fun traceHeight(p: Float): Float = map.trace?.invoke(p) ?: 0f
        override val hasTrace: Boolean get() = map.trace != null
        override fun terrainHeight(x: Float, z: Float): Float = map.terrain?.invoke(x, z) ?: 0f
        override val hasTerrain: Boolean get() = map.terrain != null
        override fun fieldAt(x: Float, y: Float, z: Float, out: FloatArray) {
            val f = map.field
            if (f == null) { out[0] = 0f; out[1] = 0f; out[2] = 0f } else f(x, y, z, out)
        }
        override val hasField: Boolean get() = map.field != null
        override val stopCount: Int get() = nodes.size
        override val tourTitle: String get() = map.title

        override fun pointAt(p: Float, side: Float, up: Float, ahead: Float, out: FloatArray) {
            frame(p, f)
            out[0] = f[0] + f[6] * side + f[9] * up + f[3] * ahead
            out[1] = f[1] + f[7] * side + f[10] * up + f[4] * ahead
            out[2] = f[2] + f[8] * side + f[11] * up + f[5] * ahead
        }

        override fun ball(
            x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            base: FloatArray, accent: FloatArray, alpha: Float,
            rotDeg: Float, ax: Float, ay: Float, az: Float,
            pattern: Float, glow: Float, small: Boolean
        ) = drawSphereAt(x, y, z, sx, sy, sz, base, accent, alpha, rotDeg, ax, ay, az,
            if (small) blob else sphere, pattern, glow)

        override fun rod(
            ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
            radius: Float, base: FloatArray, accent: FloatArray, glow: Float
        ) = drawStrut(ax, ay, az, bx, by, bz, radius, base, accent, glow)

        override val lineBuf: FloatArray get() = sceneLines.data
        override val lineCapacity: Int get() = SCENE_LINE_VERTS

        override fun flushLines(vertexCount: Int, width: Float) {
            if (vertexCount < 2) return
            Matrix.setIdentityM(model, 0)
            Matrix.multiplyMM(mv, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
            colorShader.use(mvp, 1f)
            lineWidth(width)
            sceneLines.draw(colorShader.positionHandle, colorShader.colorHandle, GLES20.GL_LINES,
                vertexCount.coerceAtMost(SCENE_LINE_VERTS))
            lineWidth(1f)
        }

        override val triBuf: FloatArray get() = sceneTris.data
        override val triCapacity: Int get() = SCENE_TRI_VERTS

        override fun flushTris(vertexCount: Int) {
            if (vertexCount < 3) return
            Matrix.setIdentityM(model, 0)
            Matrix.multiplyMM(mv, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
            colorShader.use(mvp, 1f)
            // Shaded patches read as translucent surfaces, so they neither cull nor occlude.
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glDepthMask(false)
            sceneTris.draw(colorShader.positionHandle, colorShader.colorHandle, GLES20.GL_TRIANGLES,
                vertexCount.coerceAtMost(SCENE_TRI_VERTS))
            GLES20.glDepthMask(true)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
        }

        override fun text(
            s: String, x: Float, y: Float, z: Float, height: Float,
            tint: FloatArray, alpha: Float, style: GlyphBoard.Style,
            glow: Float, anchor: Float, rise: Float
        ) = glyphs.drawBillboard(
            s, style, x, y, z, height,
            camRightX, camRightY, camRightZ, camUpX, camUpY, camUpZ,
            tint, alpha * landmarkFade, glow, anchor, rise, view, projection)

        override fun textWidth(s: String, height: Float, style: GlyphBoard.Style): Float =
            glyphs.label(s, style)?.let { it.aspect * height } ?: 0f
    }

    // ------------------------------------------------------------- helpers
    /** A world point [a] ahead of a rail frame, [s] to its side and [u] above it. */
    private fun fx(f: Frame, a: Float, s: Float, u: Float) = f.cx + f.dx * a + f.sx * s + f.ux * u
    private fun fy(f: Frame, a: Float, s: Float, u: Float) = f.cy + f.dy * a + f.sy * s + f.uy * u
    private fun fz(f: Frame, a: Float, s: Float, u: Float) = f.cz + f.dz * a + f.sz * s + f.uz * u

    private fun drawSphereAt(
        x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
        base: FloatArray, accent: FloatArray, alpha: Float = 1f,
        rotDeg: Float = 0f, ax: Float = 0f, ay: Float = 1f, az: Float = 0f,
        mesh: SphereMesh = sphere, pattern: Float = 0f, glow: Float = 0f
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (rotDeg != 0f) Matrix.rotateM(model, 0, rotDeg, ax, ay, az)
        Matrix.scaleM(model, 0, sx, sy, sz)
        drawLitModel(mesh, base, accent, alpha * landmarkFade, pattern, glow)
    }

    /** Draw [mesh] with the current model matrix through the lit shader. */
    private fun drawLitModel(mesh: SphereMesh, base: FloatArray, accent: FloatArray, alpha: Float, pattern: Float, glow: Float) {
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        // Normal matrix = transpose(inverse(model)). transposeM must not run in place (it would
        // symmetrise the matrix instead of transposing it), so invert into a scratch first.
        if (!Matrix.invertM(invM, 0, model, 0)) Matrix.setIdentityM(invM, 0)
        Matrix.transposeM(normalM, 0, invM, 0)
        litShader.use(mvp, model, normalM, base, accent, alpha, pattern, glow, lampX(), lampY(), lampZ(), camNowX, camNowY, camNowZ)
        mesh.draw(litShader.positionHandle, litShader.normalHandle)
    }

    private fun drawLinesAt(mesh: LineMesh, x: Float, y: Float, z: Float, scale: Float, rotDeg: Float, ax: Float, ay: Float, az: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (rotDeg != 0f) Matrix.rotateM(model, 0, rotDeg, ax, ay, az)
        Matrix.scaleM(model, 0, scale, scale, scale)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        colorShader.use(mvp, 4f)
        mesh.draw(colorShader.positionHandle, colorShader.colorHandle)
    }

    /** Rotate the model matrix so local +x = side, +y = up, +z = -dir (rail forward). */
    private fun applyFrameRotation(f: Frame) {
        val rot = FloatArray(16)
        rot[0] = f.sx; rot[1] = f.sy; rot[2] = f.sz; rot[3] = 0f
        rot[4] = f.ux; rot[5] = f.uy; rot[6] = f.uz; rot[7] = 0f
        rot[8] = -f.dx; rot[9] = -f.dy; rot[10] = -f.dz; rot[11] = 0f
        rot[12] = 0f; rot[13] = 0f; rot[14] = 0f; rot[15] = 1f
        val tmp = FloatArray(16)
        Matrix.multiplyMM(tmp, 0, model, 0, rot, 0)
        System.arraycopy(tmp, 0, model, 0, 16)
    }

    private fun yawOf(f: Frame): Float = atan2(-f.dx, -f.dz) * 180f / PI.toFloat()

    private class Frame(
        val cx: Float, val cy: Float, val cz: Float,
        val dx: Float, val dy: Float, val dz: Float,
        val sx: Float, val sy: Float, val sz: Float,
        val ux: Float, val uy: Float, val uz: Float
    )

    /** Catmull-Rom position on the rail at node-units p. */
    private fun curvePoint(p: Float, out: FloatArray) {
        val n = nodes.size
        val pc = p.coerceIn(0f, (n - 1).toFloat())
        val i = min(pc.toInt(), n - 2)
        val t = pc - i
        val p0 = nodes[max(i - 1, 0)]; val p1 = nodes[i]; val p2 = nodes[i + 1]; val p3 = nodes[min(i + 2, n - 1)]
        fun cr(a: Float, b: Float, c: Float, d: Float): Float =
            0.5f * ((2f * b) + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t * t + (-a + 3f * b - 3f * c + d) * t * t * t)
        out[0] = cr(p0.x, p1.x, p2.x, p3.x); out[1] = cr(p0.y, p1.y, p2.y, p3.y); out[2] = cr(p0.z, p1.z, p2.z, p3.z)
    }

    private val tmpA = FloatArray(3)
    private val tmpB = FloatArray(3)
    private fun frameAt(p: Float): Frame {
        curvePoint(p, tmpA)
        val cx = tmpA[0]; val cy = tmpA[1]; val cz = tmpA[2]
        curvePoint(p - 0.02f, tmpA); curvePoint(p + 0.02f, tmpB)
        var dx = tmpB[0] - tmpA[0]; var dy = tmpB[1] - tmpA[1]; var dz = tmpB[2] - tmpA[2]
        var l = sqrt(dx * dx + dy * dy + dz * dz)
        if (l < 1e-5f) { dx = 0f; dy = 0f; dz = -1f; l = 1f }
        dx /= l; dy /= l; dz /= l
        // side = normalize(cross(d, up)), up2 = cross(side, d)
        var sx = dy * 0f - dz * 1f; var sy = dz * 0f - dx * 0f; var sz = dx * 1f - dy * 0f
        var sl = sqrt(sx * sx + sy * sy + sz * sz)
        if (sl < 1e-4f) { sx = 1f; sy = 0f; sz = 0f; sl = 1f }
        sx /= sl; sy /= sl; sz /= sl
        val ux = sy * dz - sz * dy; val uy = sz * dx - sx * dz; val uz = sx * dy - sy * dx
        return Frame(cx, cy, cz, dx, dy, dz, sx, sy, sz, ux, uy, uz)
    }

    private fun nodeLerp(p: Float, f: (TourNode) -> Float): Float {
        val pc = p.coerceIn(0f, nodes.lastIndex.toFloat())
        val i = min(pc.toInt(), nodes.lastIndex - 1)
        val t = pc - i
        val s = t * t * (3f - 2f * t)
        return f(nodes[i]) + (f(nodes[i + 1]) - f(nodes[i])) * s
    }

    private fun tunnelRadius(p: Float): Float = nodeLerp(p) { it.radius }

    // ------------------------------------------------------ mesh builders
    private fun buildTunnel(): FloatArray {
        val segs = 14
        val step = 0.08f
        val rings = ArrayList<FloatArray>()
        var p = 0f
        while (p <= nodes.lastIndex + 1e-4f) {
            val f = frameAt(p)
            val r = tunnelRadius(p)
            val ring = FloatArray(segs * 10)
            val cr = nodeLerp(p) { it.wall[0] }; val cg = nodeLerp(p) { it.wall[1] }; val cb = nodeLerp(p) { it.wall[2] }
            for (k in 0 until segs) {
                val a = 2f * PI.toFloat() * k / segs
                val ox = f.sx * cos(a) + f.ux * sin(a); val oy = f.sy * cos(a) + f.uy * sin(a); val oz = f.sz * cos(a) + f.uz * sin(a)
                val bump = 1f + 0.07f * sin(k * 3.1f + p * 9.3f) + 0.04f * sin(k * 7.7f + p * 21f)
                val o = k * 10
                ring[o] = f.cx + ox * r * bump; ring[o + 1] = f.cy + oy * r * bump; ring[o + 2] = f.cz + oz * r * bump
                ring[o + 3] = -ox; ring[o + 4] = -oy; ring[o + 5] = -oz
                val shade = 0.9f + 0.1f * sin(k * 2.3f + p * 5f)
                ring[o + 6] = cr * shade; ring[o + 7] = cg * shade; ring[o + 8] = cb * shade; ring[o + 9] = 1f
            }
            rings.add(ring)
            p += step
        }
        val out = FloatArray((rings.size - 1) * segs * 6 * 10)
        var w = 0
        fun put(ring: FloatArray, k: Int) { val o = (k % segs) * 10; for (q in 0 until 10) out[w++] = ring[o + q] }
        for (i in 0 until rings.size - 1) {
            val a = rings[i]; val b = rings[i + 1]
            for (k in 0 until segs) {
                put(a, k); put(b, k); put(b, k + 1)
                put(a, k); put(b, k + 1); put(a, k + 1)
            }
        }
        return out
    }

    private fun buildRouteLines(): FloatArray = buildList {
        val color = floatArrayOf(1f, 0.6f, 0.55f, 0.22f)
        nodes.zipWithNext().forEach { (a, b) -> addLine(a.x, a.y, a.z, b.x, b.y, b.z, color) }
    }.toFloatArray()

    private fun buildRouteNodes(): FloatArray = buildList {
        nodes.forEach { addPoint(it.x, it.y, it.z, 1f, 0.77f, 0.42f, 0.45f) }
    }.toFloatArray()

    // The M.S.V. Mote: an original industrial hovercraft. Faceted, wider-than-tall hull, a raised
    // cockpit pod forward, a dorsal spine with antenna masts, side pontoons that carry the hover
    // pads, an aft engine block, and two arm-probe mounts at the bow. Faces -Z. Length 1.5.
    private fun buildMote(): FloatArray = buildList {
        val top = floatArrayOf(0.64f, 0.67f, 0.74f, 1f)
        val flank = floatArrayOf(0.50f, 0.53f, 0.60f, 1f)
        val belly = floatArrayOf(0.32f, 0.34f, 0.40f, 1f)
        val dark = floatArrayOf(0.30f, 0.32f, 0.38f, 1f)
        val glass = floatArrayOf(0.40f, 0.85f, 0.95f, 1f)
        val rust = floatArrayOf(0.50f, 0.37f, 0.30f, 1f)
        fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, c: FloatArray, shade: Float) {
            addPoint(ax, ay, az, c[0] * shade, c[1] * shade, c[2] * shade, c[3])
            addPoint(bx, by, bz, c[0] * shade, c[1] * shade, c[2] * shade, c[3])
            addPoint(cx, cy, cz, c[0] * shade, c[1] * shade, c[2] * shade, c[3])
        }
        fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, c: FloatArray, shade: Float) {
            tri(ax, ay, az, bx, by, bz, cx, cy, cz, c, shade); tri(ax, ay, az, cx, cy, cz, dx, dy, dz, c, shade)
        }
        fun box(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, col: FloatArray, cap: FloatArray) {
            val x0 = cx - hx; val x1 = cx + hx; val y0 = cy - hy; val y1 = cy + hy; val z0 = cz - hz; val z1 = cz + hz
            quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, cap, 1f)        // front (-z)
            quad(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, col, 0.8f)      // back
            quad(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, col, 0.9f)      // left
            quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, col, 0.9f)      // right
            quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, col, 1.05f)     // top
            quad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, col, 0.65f)     // bottom
        }
        // Hull: an eight-facet lathe, squashed (wider than tall), flat top and belly facets.
        val prof = floatArrayOf(-0.75f, 0.06f, -0.62f, 0.16f, -0.40f, 0.24f, -0.10f, 0.27f, 0.25f, 0.27f, 0.50f, 0.22f, 0.66f, 0.15f, 0.75f, 0.05f)
        val segs = 8
        for (i in 0 until prof.size / 2 - 1) {
            val z0 = prof[i * 2]; val r0 = prof[i * 2 + 1]; val z1 = prof[i * 2 + 2]; val r1 = prof[i * 2 + 3]
            for (k in 0 until segs) {
                val a0 = 2f * PI.toFloat() * (k + 0.5f) / segs; val a1 = 2f * PI.toFloat() * (k + 1.5f) / segs
                val ny = (sin(a0) + sin(a1)) * 0.5f
                val col = if (ny > 0.5f) top else if (ny < -0.5f) belly else flank
                val shade = 0.72f + 0.28f * ((ny + 1f) * 0.5f)
                quad(cos(a0) * r0 * 1.25f, sin(a0) * r0 * 0.72f, z0, cos(a1) * r0 * 1.25f, sin(a1) * r0 * 0.72f, z0,
                     cos(a1) * r1 * 1.25f, sin(a1) * r1 * 0.72f, z1, cos(a0) * r1 * 1.25f, sin(a0) * r1 * 0.72f, z1, col, shade)
            }
        }
        // Cockpit pod (raised, forward) with a glass face; dorsal spine; two antenna masts.
        box(0f, 0.20f, -0.44f, 0.13f, 0.08f, 0.15f, dark, glass)
        box(0f, 0.25f, 0.12f, 0.05f, 0.03f, 0.34f, dark, dark)
        box(0.10f, 0.34f, -0.30f, 0.012f, 0.10f, 0.012f, dark, dark)
        box(-0.14f, 0.32f, 0.04f, 0.012f, 0.08f, 0.012f, dark, dark)
        // Engine block at the stern; side pontoons that carry the hover pads; arm mounts at the bow.
        box(0f, -0.02f, 0.60f, 0.24f, 0.13f, 0.14f, dark, rust)
        box(-0.34f, -0.10f, -0.05f, 0.08f, 0.05f, 0.42f, flank, dark)
        box(0.34f, -0.10f, -0.05f, 0.08f, 0.05f, 0.42f, flank, dark)
        box(-0.24f, -0.06f, -0.60f, 0.05f, 0.05f, 0.06f, rust, dark)
        box(0.24f, -0.06f, -0.60f, 0.05f, 0.05f, 0.06f, rust, dark)
    }.toFloatArray()

    private fun buildCockpitLines(): FloatArray = buildList {
        val glass = floatArrayOf(0.98f, 0.78f, 0.66f, 0.75f)
        // Porthole: an octagonal frame 1.0 ahead of the eye (about 23 x 17 degrees of view).
        for (k in 0 until 8) {
            val a0 = 2f * PI.toFloat() * k / 8f + PI.toFloat() / 8f; val a1 = 2f * PI.toFloat() * (k + 1) / 8f + PI.toFloat() / 8f
            addLine(cos(a0) * 0.42f, 0.02f + sin(a0) * 0.30f, -1.0f, cos(a1) * 0.42f, 0.02f + sin(a1) * 0.30f, -1.0f, glass)
        }
        // Console bar below the window + two struts up to the frame.
        addLine(-0.50f, -0.30f, -0.60f, 0.50f, -0.30f, -0.60f, glass)
        addLine(-0.46f, -0.30f, -0.60f, -0.39f, -0.10f, -1.0f, glass)
        addLine(0.46f, -0.30f, -0.60f, 0.39f, -0.10f, -1.0f, glass)
        // Indicator stubs (rose left, amber right) and a centre reticle.
        addLine(-0.20f, -0.30f, -0.60f, -0.12f, -0.38f, -0.60f, floatArrayOf(1f, 0.36f, 0.48f, 0.8f))
        addLine(0.20f, -0.30f, -0.60f, 0.12f, -0.38f, -0.60f, floatArrayOf(1f, 0.77f, 0.42f, 0.8f))
        addLine(-0.04f, 0.02f, -1.0f, 0.04f, 0.02f, -1.0f, floatArrayOf(1f, 0.77f, 0.42f, 0.55f))
        addLine(0f, -0.02f, -1.0f, 0f, 0.06f, -1.0f, floatArrayOf(1f, 0.77f, 0.42f, 0.55f))
    }.toFloatArray()






















    companion object {
        const val VIEW_COUNT = 4
        const val VIEW_BRIDGE = 0        // the helm, looking ahead through the porthole
        const val VIEW_CHASE = 1         // external camera trailing the Mote
        const val VIEW_ENGINEERING = 2   // beside the scale drive core
        const val VIEW_OBSERVATION = 3   // the observation deck, calm and wide
        val VIEW_NAMES = arrayOf("HELM", "EXTERNAL - CHASE", "THE CORE", "THE MEASURING DECK")
        /** Vertex capacity of the buffers every scene builds into. A curve at 120 samples is 240. */
        private const val SCENE_LINE_VERTS = 4096
        private const val SCENE_TRI_VERTS = 1536
        private const val EYE_OFFSET = 0.035f
        private const val VIEW_TRANSITION_SEC = 1.0f
        private const val SHRINK_SEC = 3.2f
        private const val HEART_PERIOD = 0.92f
        private const val LYSIS_PERIOD = 24f     // the phage stop's burst cycle (seconds)
        private val SIGNS = floatArrayOf(-1f, 1f)
        private val PLATE_FILES = arrayOf("yanan", "portrait")
        private const val TIME_WRAP = (20.0 * PI).toFloat()

        // Ladder rungs (log10 of the Mote's length in metres) and their labels, one per decade
        // the drive can step through (the atom drop passes 1.2 nm and 120 pm on its way to 12 pm).
        /** The rungs of the cut ladder, as powers of ten, coarse to fine. */
        private val LADDER_EXP = doubleArrayOf(0.0, -1.0, -2.0, -3.0, -4.0, -5.0, -6.0, -7.0, -8.0, -9.0)
        private val LADDER_LABELS = arrayOf("1", "10⁻¹", "10⁻²", "10⁻³", "10⁻⁴", "10⁻⁵", "10⁻⁶", "10⁻⁷", "10⁻⁸", "10⁻⁹")

        private const val DEG = (PI / 180.0).toFloat()
        private val COL_LAMP = floatArrayOf(1f, 0.77f, 0.42f, 1f)
        // The drifting objects, one colour per ambience family.
        private val COL_COUNT = floatArrayOf(0.80f, 0.86f, 1f, 1f)
        private val COL_SAMPLE = floatArrayOf(0.95f, 0.95f, 1f, 1f)
        private val COL_SLAB = floatArrayOf(1f, 0.74f, 0.36f, 1f)
        private val COL_FIELD = floatArrayOf(0.45f, 0.85f, 1f, 1f)
        private val COL_CURVE = floatArrayOf(1f, 0.82f, 0.50f, 1f)
        private val COL_SURFACE = floatArrayOf(0.50f, 0.92f, 0.78f, 1f)
        private val COL_HULL = floatArrayOf(0.52f, 0.55f, 0.62f, 1f)
        private val COL_HULL_DARK = floatArrayOf(0.30f, 0.32f, 0.38f, 1f)
        private val COL_PAD = floatArrayOf(0.55f, 0.75f, 1f, 1f)
        private val COL_DRIVE = floatArrayOf(0.62f, 0.5f, 1f, 1f)
        private val COL_DRIVE_DIM = floatArrayOf(0.34f, 0.26f, 0.6f, 1f)
        private val COL_STATOR = floatArrayOf(0.55f, 0.58f, 0.7f, 1f)
        private val COL_BAY = floatArrayOf(1f, 0.85f, 0.7f, 1f)
        private val COL_SKIN = floatArrayOf(0.96f, 0.62f, 0.56f, 1f)
        private val COL_SKIN_DARK = floatArrayOf(0.72f, 0.38f, 0.36f, 1f)
        private val COL_CARTILAGE = floatArrayOf(0.93f, 0.9f, 0.85f, 1f)
        private val COL_ALVEOLUS = floatArrayOf(0.95f, 0.8f, 0.8f, 1f)
        private val COL_RED_CELL = floatArrayOf(0.85f, 0.12f, 0.14f, 1f)
        private val COL_RED_CELL_DARK = floatArrayOf(0.45f, 0.05f, 0.07f, 1f)
        private val COL_PLATELET = floatArrayOf(0.95f, 0.8f, 0.5f, 1f)
        private val COL_DUST = floatArrayOf(0.7f, 0.68f, 0.62f, 1f)
        private val COL_POLLEN = floatArrayOf(0.95f, 0.85f, 0.3f, 1f)
        private val COL_PROTEIN = floatArrayOf(0.35f, 0.8f, 0.75f, 1f)
        private val COL_VESICLE = floatArrayOf(0.75f, 0.9f, 0.95f, 1f)
        private val COL_TRANSMITTER = floatArrayOf(0.8f, 0.7f, 1f, 1f)
        private val COL_WHITE_CELL = floatArrayOf(0.9f, 0.92f, 0.82f, 1f)
        private val COL_WHITE_CELL_DARK = floatArrayOf(0.6f, 0.62f, 0.5f, 1f)
        private val COL_VALVE = floatArrayOf(0.85f, 0.45f, 0.5f, 1f)
        private val COL_VALVE_EDGE = floatArrayOf(1f, 0.75f, 0.7f, 1f)
        private val COL_NEUTROPHIL = floatArrayOf(0.88f, 0.9f, 0.78f, 1f)
        private val COL_NEUTROPHIL_DARK = floatArrayOf(0.55f, 0.5f, 0.7f, 1f)
        private val COL_MACROPHAGE = floatArrayOf(0.78f, 0.82f, 0.7f, 1f)
        private val COL_SOMA = floatArrayOf(0.5f, 0.38f, 0.85f, 1f)
        private val COL_SOMA_LIGHT = floatArrayOf(0.8f, 0.72f, 1f, 1f)
        private val COL_MYELIN = floatArrayOf(0.9f, 0.88f, 0.98f, 1f)
        private val COL_CHANNEL = floatArrayOf(0.95f, 0.6f, 0.35f, 1f)
        private val COL_CRISTAE = floatArrayOf(0.95f, 0.55f, 0.25f, 1f)
        private val COL_ATP_STALK = floatArrayOf(0.9f, 0.9f, 0.7f, 1f)
        private val COL_ATP_HEAD = floatArrayOf(0.55f, 0.9f, 0.85f, 1f)
        private val COL_PORE = floatArrayOf(0.6f, 0.55f, 0.95f, 1f)
        private val COL_NUCLEUS_LIGHT = floatArrayOf(0.85f, 0.8f, 1f, 1f)
        private val COL_POLYMERASE = floatArrayOf(0.95f, 0.75f, 0.35f, 1f)
        private val COL_RIBO_LARGE = floatArrayOf(0.3f, 0.65f, 0.7f, 1f)
        private val COL_RIBO_SMALL = floatArrayOf(0.35f, 0.75f, 0.65f, 1f)
        private val COL_RIBO_LIGHT = floatArrayOf(0.7f, 0.95f, 0.9f, 1f)
        private val COL_TRNA = floatArrayOf(0.95f, 0.55f, 0.6f, 1f)
        private val COL_AMINO_A = floatArrayOf(1f, 0.77f, 0.42f, 1f)
        private val COL_AMINO_B = floatArrayOf(0.5f, 0.9f, 0.8f, 1f)
        private val COL_NUCLEON = floatArrayOf(1f, 0.95f, 0.85f, 1f)
        private val COL_WORLD = floatArrayOf(0.95f, 0.7f, 0.6f, 1f)
        // Tour II palette.
        private val COL_LIP = floatArrayOf(0.85f, 0.42f, 0.45f, 1f)
        private val COL_TOOTH = floatArrayOf(0.97f, 0.95f, 0.88f, 1f)
        private val COL_TONGUE = floatArrayOf(0.9f, 0.45f, 0.5f, 1f)
        private val COL_VILLUS = floatArrayOf(0.95f, 0.58f, 0.6f, 1f)
        private val COL_VILLUS_TIP = floatArrayOf(1f, 0.78f, 0.72f, 1f)
        private val COL_BACTERIUM = floatArrayOf(0.55f, 0.8f, 0.45f, 1f)
        private val COL_BACTERIUM_DARK = floatArrayOf(0.3f, 0.5f, 0.25f, 1f)
        private val COL_CHYLE = floatArrayOf(0.95f, 0.9f, 0.6f, 1f)
        private val COL_PHAGE = floatArrayOf(0.75f, 0.7f, 1f, 1f)
        private val COL_PHAGE_LIGHT = floatArrayOf(0.9f, 0.88f, 1f, 1f)
        private val COL_PHAGE_TAIL = floatArrayOf(0.8f, 0.8f, 0.9f, 1f)
        private val COL_HEPATOCYTE = floatArrayOf(0.72f, 0.3f, 0.25f, 1f)
        private val COL_HEPATOCYTE_DARK = floatArrayOf(0.45f, 0.15f, 0.12f, 1f)
        private val COL_CAPSULE = floatArrayOf(0.9f, 0.75f, 0.7f, 1f)
        private val COL_FILTRATE = floatArrayOf(0.85f, 0.95f, 1f, 1f)
        private val COL_PODOCYTE = floatArrayOf(0.8f, 0.55f, 0.75f, 1f)
        private val COL_ZDISC = floatArrayOf(0.95f, 0.9f, 0.6f, 1f)
        private val COL_ACTIN = floatArrayOf(0.9f, 0.75f, 0.7f, 1f)
        private val COL_MYOSIN = floatArrayOf(0.55f, 0.2f, 0.25f, 1f)
        private val COL_MYOSIN_HEAD = floatArrayOf(0.9f, 0.4f, 0.45f, 1f)
        private val COL_MEGAKARYO = floatArrayOf(0.85f, 0.7f, 0.85f, 1f)
        private val COL_MEGAKARYO_DARK = floatArrayOf(0.5f, 0.35f, 0.6f, 1f)
        private val COL_STEM = floatArrayOf(0.8f, 0.85f, 0.95f, 1f)
        private val COL_STEM_LIGHT = floatArrayOf(0.95f, 0.97f, 1f, 1f)
        private val COL_SEG_V = floatArrayOf(0.95f, 0.45f, 0.6f, 1f)
        private val COL_SEG_D = floatArrayOf(0.5f, 0.9f, 0.55f, 1f)
        private val COL_SEG_J = floatArrayOf(0.5f, 0.7f, 1f, 1f)
        private val COL_SEG_C = floatArrayOf(0.75f, 0.55f, 0.95f, 1f)
        private val COL_THREAD = floatArrayOf(0.7f, 0.65f, 0.9f, 1f)
        private val COL_RAG = floatArrayOf(0.95f, 0.75f, 0.35f, 1f)
        private val COL_RAG_B = floatArrayOf(0.95f, 0.6f, 0.3f, 1f)
        private val COL_KINESIN = floatArrayOf(0.95f, 0.55f, 0.25f, 1f)
        private val COL_KINESIN_LIGHT = floatArrayOf(1f, 0.8f, 0.5f, 1f)
        private val COL_DYNEIN = floatArrayOf(0.6f, 0.75f, 0.95f, 1f)
        private val COL_CARGO = floatArrayOf(0.7f, 0.9f, 1f, 1f)
        private val COL_GOLGI = floatArrayOf(0.85f, 0.7f, 0.35f, 1f)
        private val COL_ER = floatArrayOf(0.45f, 0.7f, 0.75f, 1f)
        private val COL_ATP_HEAD_B = floatArrayOf(0.35f, 0.7f, 0.7f, 1f)
        private val COL_PROTON = floatArrayOf(1f, 0.95f, 0.6f, 1f)
        private val COL_ATP = floatArrayOf(1f, 0.85f, 0.3f, 1f)
        private val COL_CELL = floatArrayOf(0.6f, 0.85f, 0.9f, 1f)
        private val COL_CELL_EDGE = floatArrayOf(0.8f, 0.95f, 1f, 1f)
        private val COL_CHROMOSOME = floatArrayOf(0.55f, 0.35f, 0.85f, 1f)
        private val COL_CHROMOSOME_LIGHT = floatArrayOf(0.85f, 0.75f, 1f, 1f)
        private val COL_CENTROSOME = floatArrayOf(1f, 0.85f, 0.5f, 1f)
        // Chapter III palette.
        private val COL_CAVITY = floatArrayOf(0.06f, 0.03f, 0.04f, 1f)
        private val COL_CASEUM = floatArrayOf(0.92f, 0.87f, 0.72f, 1f)
        private val COL_STEEL = floatArrayOf(0.62f, 0.66f, 0.72f, 1f)
        private val COL_STEEL_BRIGHT = floatArrayOf(0.88f, 0.92f, 0.97f, 1f)
        private val COL_GLASS = floatArrayOf(0.70f, 0.85f, 0.95f, 1f)
        private val COL_COLD = floatArrayOf(0.55f, 0.80f, 1f, 1f)
        private val COL_STORED_CELL = floatArrayOf(0.55f, 0.10f, 0.14f, 1f)
        private val COL_PLASMA = floatArrayOf(0.95f, 0.88f, 0.60f, 1f)
        private val COL_FIBRE = floatArrayOf(0.80f, 0.34f, 0.34f, 1f)
        private val COL_FIBRE_DARK = floatArrayOf(0.48f, 0.18f, 0.20f, 1f)
        private val COL_DEBRIS = floatArrayOf(0.38f, 0.32f, 0.26f, 1f)
        private val COL_CLOT = floatArrayOf(0.42f, 0.06f, 0.09f, 1f)
        private val COL_TISSUE = floatArrayOf(0.88f, 0.48f, 0.46f, 1f)
        private val COL_TISSUE_EDGE = floatArrayOf(1f, 0.72f, 0.68f, 1f)
        private val COL_THREAD_S = floatArrayOf(0.95f, 0.93f, 0.85f, 1f)
        private val COL_MICROBE = floatArrayOf(0.85f, 0.90f, 0.45f, 1f)
        private val COL_MICROBE_DARK = floatArrayOf(0.45f, 0.52f, 0.20f, 1f)
        private val COL_FEVER = floatArrayOf(1f, 0.35f, 0.30f, 1f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun MutableList<Float>.addPoint(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float) {
        add(x); add(y); add(z); add(r); add(g); add(b); add(a)
    }

    private fun MutableList<Float>.addLine(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, c: FloatArray) {
        addPoint(ax, ay, az, c[0], c[1], c[2], c[3])
        addPoint(bx, by, bz, c[0], c[1], c[2], c[3])
    }
}

// =============================================================== meshes

/** Uploads static vertex data once; every draw then binds the VBO instead of copying a client array. */
private fun makeVbo(data: FloatArray): Int {
    val ids = IntArray(1)
    GLES20.glGenBuffers(1, ids, 0)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, data.toFloatBuffer(), GLES20.GL_STATIC_DRAW)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    return ids[0]
}

private class SphereMesh(stacks: Int, slices: Int) {
    private val vbo: Int
    private val vertexCount: Int

    init {
        val data = mutableListOf<Float>()
        for (stack in 0 until stacks) {
            val phi0 = PI.toFloat() * stack / stacks
            val phi1 = PI.toFloat() * (stack + 1) / stacks
            for (slice in 0..slices) {
                val theta = 2f * PI.toFloat() * slice / slices
                // Lower ring first: with phi increasing downward and theta counter-clockwise about +y,
                // this strip is CCW seen from OUTSIDE, which GL_CULL_FACE (front = CCW) requires.
                addSphereVertex(data, phi1, theta)
                addSphereVertex(data, phi0, theta)
            }
        }
        vertexCount = data.size / 6
        vbo = makeVbo(data.toFloatArray())
    }

    fun draw(positionHandle: Int, normalHandle: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun addSphereVertex(data: MutableList<Float>, phi: Float, theta: Float) {
        val x = sin(phi) * cos(theta)
        val y = cos(phi)
        val z = sin(phi) * sin(theta)
        data.add(x); data.add(y); data.add(z)
        data.add(x); data.add(y); data.add(z)
    }
}

/** Static triangle mesh in a VBO: position(3) normal(3) color(4). */
private class TubeMesh(data: FloatArray) {
    private val vbo: Int
    private val count = data.size / 10

    init {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, data.toFloatBuffer(), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun release() = GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)

    fun draw(positionHandle: Int, normalHandle: Int, colorHandle: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 40, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 40, 12)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 40, 24)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}

/** Static coloured vertices (position 3 + colour 4) in a VBO, drawn with one primitive mode. */
private open class ColorVboMesh(data: FloatArray, private val mode: Int) {
    private val vbo = makeVbo(data)
    protected val count = data.size / 7

    fun release() = GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)

    fun draw(positionHandle: Int, colorHandle: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 28, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 28, 12)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glDrawArrays(mode, 0, count)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}

private class PointMesh(data: FloatArray) : ColorVboMesh(data, GLES20.GL_POINTS)
private class TriMesh(data: FloatArray) : ColorVboMesh(data, GLES20.GL_TRIANGLES)
private class LineMesh(data: FloatArray) : ColorVboMesh(data, GLES20.GL_LINES)

/** Small per-frame mesh (position + color) for things rebuilt every frame. */
private class DynMesh(maxVerts: Int) {
    val data = FloatArray(maxVerts * 7)
    private val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun draw(positionHandle: Int, colorHandle: Int, mode: Int, verts: Int) {
        buffer.position(0); buffer.put(data, 0, verts * 7); buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        buffer.position(3)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glDrawArrays(mode, 0, verts)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
}

/** Fine drift: plasma proteins, ions, dust, water — points whose colour follows the node. */
private class DriftField(private val count: Int) {
    private val px = FloatArray(count); private val py = FloatArray(count); private val pz = FloatArray(count)
    private val vx = FloatArray(count); private val vy = FloatArray(count); private val vz = FloatArray(count)
    private val data = FloatArray(count * 7)
    private val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val rnd = java.util.Random(7)
    private var seeded = false

    private fun respawn(i: Int, cx: Float, cy: Float, cz: Float, spread: Float) {
        px[i] = cx + (rnd.nextFloat() - 0.5f) * 2f * spread
        py[i] = cy + (rnd.nextFloat() - 0.5f) * 2f * spread
        pz[i] = cz - 6f - rnd.nextFloat() * 26f
        vx[i] = (rnd.nextFloat() - 0.5f) * 0.4f
        vy[i] = (rnd.nextFloat() - 0.5f) * 0.4f
        vz[i] = 0.6f + rnd.nextFloat() * 1.2f
    }

    /** A scale drop (sign > 0): every mote is flung outward from the ship, as if the world burst open; a rise (sign < 0) pulls them in. */
    fun blowOut(cx: Float, cy: Float, cz: Float, sign: Float) {
        for (i in 0 until count) {
            val dx = px[i] - cx; val dy = py[i] - cy; val dz = pz[i] - cz
            val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.2f)
            vx[i] += dx / d * 6f * sign; vy[i] += dy / d * 6f * sign; vz[i] += (dz / d * 3f + 4f) * sign
        }
    }

    /** A new tour: reseed around the ship on the next update. */
    fun reset() { seeded = false }

    fun update(cx: Float, cy: Float, cz: Float, spread: Float, amb: Amb, flow: Float, dt: Float) {
        if (!seeded) { for (i in 0 until count) respawn(i, cx, cy, cz, spread); seeded = true }
        val r: Float; val g: Float; val b: Float
        when (amb) {
            Amb.COUNT -> { r = 0.80f; g = 0.86f; b = 1.00f }
            Amb.PLANE -> { r = 0.92f; g = 0.90f; b = 0.84f }    // chalk
            Amb.CURVE -> { r = 1.00f; g = 0.82f; b = 0.50f }
            Amb.LIMIT -> { r = 0.90f; g = 0.94f; b = 1.00f }
            Amb.SUM -> { r = 1.00f; g = 0.74f; b = 0.36f }
            Amb.INFINITE -> { r = 0.78f; g = 0.66f; b = 1.00f }
            Amb.SURFACE -> { r = 0.50f; g = 0.92f; b = 0.78f }
            Amb.FIELD -> { r = 0.45f; g = 0.85f; b = 1.00f }
            Amb.SOLVE -> { r = 1.00f; g = 0.62f; b = 0.30f }
            Amb.LOOKBACK -> { r = 1.00f; g = 0.85f; b = 0.75f }
        }
        for (i in 0 until count) {
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * flow * dt
            vx[i] *= 0.985f; vy[i] *= 0.985f                                       // a blow-out settles
            if (pz[i] > cz + 4f || abs(px[i] - cx) > 14f || abs(py[i] - cy) > 14f) respawn(i, cx, cy, cz, spread)
            else if (pz[i] < cz - 34f) { respawn(i, cx, cy, cz, spread); pz[i] = cz + 1f + rnd.nextFloat() * 3f }   // flowing away (inhale): re-enter behind
            val o = i * 7
            data[o] = px[i]; data[o + 1] = py[i]; data[o + 2] = pz[i]
            data[o + 3] = r; data[o + 4] = g; data[o + 5] = b; data[o + 6] = 0.5f + 0.4f * ((i * 37) % 10) / 10f
        }
        buffer.position(0); buffer.put(data); buffer.position(0)
    }

    fun draw(positionHandle: Int, colorHandle: Int) {
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        buffer.position(3)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
}

/**
 * Airflow: motes that ride the breath along the passage, drawn as short streaks whose length
 * and direction follow the signed airspeed (+ = deeper on the inhale, - = out on the exhale).
 * They live in a window around the camera and respawn on the upstream side.
 */
private class AirField(private val count: Int) {
    private val along = FloatArray(count)      // position along the rail, relative to the ship
    private val lu = FloatArray(count); private val lv = FloatArray(count)   // lateral offsets (side, up)
    private val data = FloatArray(count * 2 * 7)
    private val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val rnd = java.util.Random(19)
    private var seeded = false

    private fun respawn(i: Int, spread: Float, upstream: Boolean) {
        val a = rnd.nextFloat() * 2f * PI.toFloat(); val r = spread * sqrt(rnd.nextFloat())
        lu[i] = cos(a) * r; lv[i] = sin(a) * r
        // Upstream band (-5.5, -4]: inside the kill bounds and behind every camera (chase sits at -2.7..-3.3).
        along[i] = if (upstream) -4.0f - rnd.nextFloat() * 1.5f else 14f + rnd.nextFloat() * 14f
    }

    fun reset() { seeded = false }

    fun update(cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, sx: Float, sy: Float, sz: Float,
               ux: Float, uy: Float, uz: Float, spread: Float, flow: Float, dt: Float) {
        if (!seeded) {
            for (i in 0 until count) { respawn(i, spread, false); along[i] = rnd.nextFloat() * 28f }
            seeded = true
        }
        val len = (0.48f * abs(flow)).coerceAtLeast(0.06f)          // streak length follows airspeed
        val bright = (0.35f + 0.5f * abs(flow) / 2.6f)
        for (i in 0 until count) {
            along[i] += flow * dt
            if (along[i] > 30f) respawn(i, spread, true)         // carried deep: re-enter behind us
            else if (along[i] < -6f) respawn(i, spread, false)   // blown out past us: re-enter ahead
            val hx = cx + dx * along[i] + sx * lu[i] + ux * lv[i]
            val hy = cy + dy * along[i] + sy * lu[i] + uy * lv[i]
            val hz = cz + dz * along[i] + sz * lu[i] + uz * lv[i]
            val sgn = if (flow >= 0f) 1f else -1f
            val o = i * 14
            data[o] = hx; data[o + 1] = hy; data[o + 2] = hz
            data[o + 3] = 0.85f; data[o + 4] = 0.95f; data[o + 5] = 1f; data[o + 6] = bright
            data[o + 7] = hx - dx * len * sgn; data[o + 8] = hy - dy * len * sgn; data[o + 9] = hz - dz * len * sgn
            data[o + 10] = 0.7f; data[o + 11] = 0.9f; data[o + 12] = 1f; data[o + 13] = 0f
        }
        buffer.position(0); buffer.put(data); buffer.position(0)
    }

    fun draw(positionHandle: Int, colorHandle: Int) {
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        buffer.position(3)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, count * 2)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
}

/** Coarse drift: red cells, platelets, dust, pollen, proteins, vesicles — drawn as shaded shapes. */
private class BodyField(val count: Int) {
    val px = FloatArray(count); val py = FloatArray(count); val pz = FloatArray(count)
    private val vx = FloatArray(count); private val vy = FloatArray(count); private val vz = FloatArray(count)
    val kind = IntArray(count); val size = FloatArray(count); val spin = FloatArray(count)
    private val rnd = java.util.Random(11)
    private var seeded = false
    private var lastAmb: Amb? = null

    companion object {
        /** A bead on the integer lattice: whole numbers, drifting. */
        const val LATTICE = 0
        /** A sample point of a function. */
        const val SAMPLE = 1
        /** A thin disc: one slab of a sum. */
        const val SLAB = 2
        /** A short arrow that lines up with the flow. */
        const val ARROW = 3
        /** A tangent needle. */
        const val NEEDLE = 4
        /** A flake of a level set, lying flat. */
        const val FLAKE = 5
        const val NONE = 6
    }

    fun reset() { seeded = false; lastAmb = null }

    private fun kindFor(amb: Amb): Int {
        val r = rnd.nextFloat()
        return when (amb) {
            Amb.COUNT -> if (r < 0.85f) LATTICE else NONE
            Amb.PLANE -> if (r < 0.5f) LATTICE else NONE
            Amb.CURVE -> if (r < 0.6f) SAMPLE else if (r < 0.8f) NEEDLE else NONE
            Amb.LIMIT -> if (r < 0.7f) SAMPLE else NONE
            Amb.SUM -> if (r < 0.75f) SLAB else NONE
            Amb.INFINITE -> if (r < 0.7f) SAMPLE else NONE
            Amb.SURFACE -> if (r < 0.6f) FLAKE else NONE
            Amb.FIELD -> if (r < 0.8f) ARROW else NONE
            Amb.SOLVE -> if (r < 0.5f) ARROW else if (r < 0.7f) SAMPLE else NONE
            Amb.LOOKBACK -> NONE
        }
    }

    private fun respawn(i: Int, cx: Float, cy: Float, cz: Float, spread: Float, amb: Amb) {
        kind[i] = kindFor(amb)
        size[i] = when (kind[i]) {
            LATTICE -> 0.10f + rnd.nextFloat() * 0.04f
            SAMPLE -> 0.07f + rnd.nextFloat() * 0.05f
            SLAB -> 0.28f + rnd.nextFloat() * 0.16f
            ARROW -> 0.26f + rnd.nextFloat() * 0.14f
            NEEDLE -> 0.20f + rnd.nextFloat() * 0.10f
            FLAKE -> 0.18f + rnd.nextFloat() * 0.14f
            else -> 0f
        }
        spin[i] = rnd.nextFloat()
        px[i] = cx + (rnd.nextFloat() - 0.5f) * 1.7f * spread
        py[i] = cy + (rnd.nextFloat() - 0.5f) * 1.7f * spread
        pz[i] = cz - 4f - rnd.nextFloat() * 22f
        // A whole number is not anywhere: lattice beads land on the unit grid, which reads as
        // "these are the integers" without a word being said about it.
        if (kind[i] == LATTICE) {
            px[i] = Math.round(px[i]).toFloat(); py[i] = Math.round(py[i]).toFloat()
        }
        vx[i] = (rnd.nextFloat() - 0.5f) * 0.3f
        vy[i] = (rnd.nextFloat() - 0.5f) * 0.3f
        vz[i] = 0.5f + rnd.nextFloat() * 0.9f
    }

    fun blowOut(cx: Float, cy: Float, cz: Float, sign: Float) {
        for (i in 0 until count) {
            val dx = px[i] - cx; val dy = py[i] - cy; val dz = pz[i] - cz
            val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.2f)
            vx[i] += dx / d * 4f * sign; vy[i] += dy / d * 4f * sign; vz[i] += 3f * sign
        }
    }

    fun update(cx: Float, cy: Float, cz: Float, spread: Float, amb: Amb, flow: Float, dt: Float) {
        if (!seeded) { for (i in 0 until count) respawn(i, cx, cy, cz, spread, amb); seeded = true }
        if (amb != lastAmb) {
            lastAmb = amb
            // Inside the atom, the look back and the motor nothing drifts: clear the stragglers.
            // Nothing drifts at the view from outside: clear the stragglers.
            if (amb == Amb.LOOKBACK) for (i in 0 until count) kind[i] = NONE
        }
        for (i in 0 until count) {
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * flow * dt
            vx[i] *= 0.985f; vy[i] *= 0.985f
            if (pz[i] > cz + 3f || abs(px[i] - cx) > 12f || abs(py[i] - cy) > 12f) respawn(i, cx, cy, cz, spread, amb)
        }
    }
}

// ============================================================== shaders

/**
 * Precision header for the fragment shaders. World positions run to z = -194 and the shaders take
 * sin() of multiples of them, which turns to static in fp16; use highp wherever the GPU offers it.
 * The vertex stage (always highp) also pre-computes the lamp/eye vectors so the fragment stage
 * only ever sees small numbers.
 */
private const val FRAG_PRECISION = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
"""

/** Point-lit sphere shader: the Mote's lamp lights everything; rim glow in the accent colour; optional mottling. */
private class LitShader {
    private val program = compileProgram(
        """
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        uniform mat4 uMvp;
        uniform mat4 uModel;
        uniform mat4 uNormal;
        uniform vec3 uLamp;
        uniform vec3 uEye;
        varying vec3 vNormal;
        varying vec3 vToLamp;
        varying vec3 vToEye;
        varying vec3 vLocal;
        void main() {
            vNormal = normalize((uNormal * vec4(aNormal, 0.0)).xyz);
            vec3 world = (uModel * vec4(aPosition, 1.0)).xyz;
            vToLamp = uLamp - world;
            vToEye = uEye - world;
            vLocal = aNormal;
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
        """,
        FRAG_PRECISION + """
        uniform vec4 uBase;
        uniform vec4 uAccent;
        uniform float uAlpha;
        uniform float uPattern;
        uniform float uGlow;
        varying vec3 vNormal;
        varying vec3 vToLamp;
        varying vec3 vToEye;
        varying vec3 vLocal;
        void main() {
            vec3 N = normalize(vNormal);
            vec3 L = vToLamp;
            float d = length(L);
            L /= max(d, 0.001);
            float diffuse = max(dot(N, L), 0.0) / (1.0 + d * d * 0.010);
            vec3 V = normalize(vToEye);
            float rim = pow(1.0 - max(dot(N, V), 0.0), 2.5);
            float spots = smoothstep(0.35, 0.8, sin(vLocal.x * 11.0 + vLocal.y * 7.0) * sin(vLocal.z * 9.0 + vLocal.x * 5.0));
            vec3 color = mix(uBase.rgb, uAccent.rgb, spots * uPattern * 0.6);
            color = color * (0.24 + 0.76 * diffuse) + uAccent.rgb * rim * 0.45 + color * uGlow;
            gl_FragColor = vec4(color, uBase.a * uAlpha);
        }
        """
    )
    val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
    private val mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
    private val modelHandle = GLES20.glGetUniformLocation(program, "uModel")
    private val normalMatrixHandle = GLES20.glGetUniformLocation(program, "uNormal")
    private val baseHandle = GLES20.glGetUniformLocation(program, "uBase")
    private val accentHandle = GLES20.glGetUniformLocation(program, "uAccent")
    private val alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
    private val patternHandle = GLES20.glGetUniformLocation(program, "uPattern")
    private val glowHandle = GLES20.glGetUniformLocation(program, "uGlow")
    private val lampHandle = GLES20.glGetUniformLocation(program, "uLamp")
    private val eyeHandle = GLES20.glGetUniformLocation(program, "uEye")

    fun use(
        mvp: FloatArray, model: FloatArray, normal: FloatArray, base: FloatArray, accent: FloatArray,
        alpha: Float, pattern: Float, glow: Float, lx: Float, ly: Float, lz: Float, ex: Float, ey: Float, ez: Float
    ) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, normal, 0)
        GLES20.glUniform4fv(baseHandle, 1, base, 0)
        GLES20.glUniform4fv(accentHandle, 1, accent, 0)
        GLES20.glUniform1f(alphaHandle, alpha)
        GLES20.glUniform1f(patternHandle, pattern)
        GLES20.glUniform1f(glowHandle, glow)
        GLES20.glUniform3f(lampHandle, lx, ly, lz)
        GLES20.glUniform3f(eyeHandle, ex, ey, ez)
    }
}

/** Passage walls: vertex colour, lit by the lamp with distance fog, a slow organic ripple and a heartbeat pulse. */
private class WallShader {
    private val program = compileProgram(
        """
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        uniform mat4 uMvp;
        uniform mat4 uModel;
        uniform vec3 uLamp;
        uniform float uTime;
        varying vec3 vNormal;
        varying vec3 vWorld;
        varying vec3 vToLamp;
        varying vec4 vColor;
        varying float vRipple;
        void main() {
            vNormal = aNormal;
            vWorld = (uModel * vec4(aPosition, 1.0)).xyz;
            vToLamp = uLamp - vWorld;
            vColor = aColor;
            // The slow organic ripple is smooth enough to evaluate per vertex (highp, cheap).
            vRipple = 0.5 + 0.5 * sin(vWorld.z * 1.7 + uTime * 1.5 + vWorld.x * 0.9 + vWorld.y * 1.3);
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
        """,
        FRAG_PRECISION + """
        uniform float uTime;
        uniform float uPulse;
        uniform float uFog;
        uniform float uAlpha;
        uniform float uDetail;
        varying vec3 vNormal;
        varying vec3 vWorld;
        varying vec3 vToLamp;
        varying vec4 vColor;
        varying float vRipple;
        void main() {
            vec3 L = vToLamp;
            float d = length(L);
            L /= max(d, 0.001);
            float diffuse = max(dot(normalize(vNormal), L), 0.0);
            float att = 1.0 / (1.0 + d * d * uFog);
            vec3 col = vColor.rgb * (0.10 + 0.90 * diffuse * att) * (0.85 + 0.15 * vRipple) * (1.0 + 0.30 * uPulse);
            if (uDetail > 0.5) {
                float veins = smoothstep(0.92, 1.0, sin(vWorld.z * 2.3 + vWorld.x * 1.7) * sin(vWorld.y * 2.1 - uTime * 0.3));
                col += vColor.rgb * veins * 0.35 * att;
            }
            gl_FragColor = vec4(col, vColor.a * uAlpha);
        }
        """
    )
    val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
    val colorHandle = GLES20.glGetAttribLocation(program, "aColor")
    private val mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
    private val modelHandle = GLES20.glGetUniformLocation(program, "uModel")
    private val lampHandle = GLES20.glGetUniformLocation(program, "uLamp")
    private val timeHandle = GLES20.glGetUniformLocation(program, "uTime")
    private val pulseHandle = GLES20.glGetUniformLocation(program, "uPulse")
    private val fogHandle = GLES20.glGetUniformLocation(program, "uFog")
    private val alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
    private val detailHandle = GLES20.glGetUniformLocation(program, "uDetail")

    fun use(mvp: FloatArray, model: FloatArray, lx: Float, ly: Float, lz: Float, time: Float, pulse: Float, fog: Float, alpha: Float, detail: Float) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES20.glUniform3f(lampHandle, lx, ly, lz)
        GLES20.glUniform1f(timeHandle, time)
        GLES20.glUniform1f(pulseHandle, pulse)
        GLES20.glUniform1f(fogHandle, fog)
        GLES20.glUniform1f(alphaHandle, alpha)
        GLES20.glUniform1f(detailHandle, detail)
    }
}

/** The one textured surface in the app: a picture plate for chapter III. */
private class PlateShader {
    private val program = compileProgram(
        """
        attribute vec3 aPosition;
        attribute vec2 aUv;
        uniform mat4 uMvp;
        varying vec2 vUv;
        void main() {
            vUv = aUv;
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
        """,
        """
        precision mediump float;
        uniform sampler2D uTex;
        uniform float uAlpha;
        uniform float uLift;
        varying vec2 vUv;
        void main() {
            vec3 c = texture2D(uTex, vUv).rgb;
            // The waveguides swallow dark tones and there is no white point out here, so the
            // plate is lifted and warmed a little rather than shown flat.
            c = pow(c, vec3(0.85)) * (0.75 + 0.45 * uLift);
            gl_FragColor = vec4(c, uAlpha);
        }
        """
    )
    val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    val uvHandle = GLES20.glGetAttribLocation(program, "aUv")
    private val mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
    private val texHandle = GLES20.glGetUniformLocation(program, "uTex")
    private val alphaHandle = GLES20.glGetUniformLocation(program, "uAlpha")
    private val liftHandle = GLES20.glGetUniformLocation(program, "uLift")

    fun use(mvp: FloatArray, texture: Int, alpha: Float, lift: Float) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(texHandle, 0)
        GLES20.glUniform1f(alphaHandle, alpha)
        GLES20.glUniform1f(liftHandle, lift)
    }
}

private class ColorShader {
    private val program = compileProgram(
        """
        attribute vec3 aPosition;
        attribute vec4 aColor;
        uniform mat4 uMvp;
        uniform float uPointSize;
        varying vec4 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uMvp * vec4(aPosition, 1.0);
            gl_PointSize = uPointSize;
        }
        """,
        """
        precision mediump float;
        uniform float uPoint;
        uniform float uFade;
        varying vec4 vColor;
        void main() {
            vec4 c = vColor;
            c.a *= uFade;
            if (uPoint > 0.5) {
                // Round, soft-edged point sprites instead of hard squares.
                float d = length(gl_PointCoord - vec2(0.5));
                if (d > 0.5) discard;
                c.a *= smoothstep(0.5, 0.12, d);
            }
            gl_FragColor = c;
        }
        """
    )
    val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    val colorHandle = GLES20.glGetAttribLocation(program, "aColor")
    private val mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
    private val pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
    private val pointHandle = GLES20.glGetUniformLocation(program, "uPoint")
    private val fadeHandle = GLES20.glGetUniformLocation(program, "uFade")

    /** Alpha multiplier applied to everything drawn until changed (landmark distance fade). */
    var globalFade = 1f

    /** [points] = true when the next draw is GL_POINTS (enables the round sprite look). */
    fun use(mvp: FloatArray, pointSize: Float, points: Boolean = false) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform1f(pointSizeHandle, pointSize)
        GLES20.glUniform1f(pointHandle, if (points) 1f else 0f)
        GLES20.glUniform1f(fadeHandle, globalFade)
    }
}

private fun FloatArray.toFloatBuffer(): FloatBuffer {
    val buffer = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    buffer.put(this)
    buffer.position(0)
    return buffer
}

private fun List<Float>.toFloatBuffer(): FloatBuffer = toFloatArray().toFloatBuffer()

private fun compileProgram(vertexSource: String, fragmentSource: String): Int {
    val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertex)
    GLES20.glAttachShader(program, fragment)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    require(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(program) }
    GLES20.glDeleteShader(vertex)
    GLES20.glDeleteShader(fragment)
    return program
}

private fun compileShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source.trimIndent())
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    require(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
    return shader
}
