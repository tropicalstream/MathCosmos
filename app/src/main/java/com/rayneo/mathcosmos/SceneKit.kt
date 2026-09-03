package com.rayneo.mathcosmos

/**
 * Everything a landmark scene is allowed to do.
 *
 * InnerCosmos kept its thirty-odd scenes as private methods of one 3400-line renderer, which was
 * fine when they were written one at a time by one pair of hands. MathCosmos has four tours of
 * them and they are far more alike — nearly all are "some geometry built from a rail frame, some
 * of it moving, some of it labelled" — so they live in their own files instead and reach the
 * renderer only through this interface.
 *
 * The contract is deliberately narrow and allocation-free: a scene may read the ride's state,
 * ask for a rail frame, and draw balls, rods, lines, triangles and notation. It may not touch GL,
 * hold a frame's worth of state it did not build, or allocate per frame. Everything it draws is
 * already faded by the landmark's approach fade unless it says otherwise.
 *
 * World space throughout. A scene that wants to sit square to the passage builds its points out
 * of a frame's side / up / forward vectors, exactly as [MathMesh] expects.
 */
interface SceneKit {

    // ------------------------------------------------------------- the ride

    /** Seconds since the renderer started, wrapped so trig stays accurate. */
    val seconds: Float

    /** Where the craft is on the rail, in node units (stop 3 exactly = 3.0). */
    val progress: Float

    /** 0 = full detail, 1 and 2 = the thermal governor has stepped the scene down. */
    val quality: Int

    /** How far in the landmark is faded up: 0 out of range, 1 alongside it. */
    val fade: Float

    /** The last sound cue's visual kick, 0..1, decaying. Scenes may pulse with it. */
    val beat: Float

    /** How far the arm probes are reaching, 0..1 — a scene may hand something to them. */
    val reach: Float

    val shipX: Float
    val shipY: Float
    val shipZ: Float

    /** The eye. Billboards face this; so should anything meant to be read. */
    val camX: Float
    val camY: Float
    val camZ: Float

    /** Camera right and up, for billboarding. Unit length, perpendicular. */
    val camRightX: Float
    val camRightY: Float
    val camRightZ: Float
    val camUpX: Float
    val camUpY: Float
    val camUpZ: Float

    // ------------------------------------------------------------- the rail

    /**
     * The rail frame at node-units [p], written into [out] as twelve floats:
     * centre (0..2), forward (3..5), side (6..8), up (9..11). All unit length but the centre.
     */
    fun frame(p: Float, out: FloatArray)

    /** Passage radius at node-units [p]: how much room a scene has before it is inside the wall. */
    fun radius(p: Float): Float

    /** Convenience: a point [side] right and [up] above the rail centre at [p], into [out]. */
    fun pointAt(p: Float, side: Float, up: Float, ahead: Float, out: FloatArray)

    // ------------------------------------------------------ the tour's world

    /**
     * The height of the corridor's roof above the rail at node-units [p] — the function the tour
     * is about. Zero for a tour with no roof curve, so a scene can call it unguarded.
     */
    fun traceHeight(p: Float): Float

    /** Whether this tour has a roof curve at all. */
    val hasTrace: Boolean

    /** Ground height at a world (x, z), for the open-country tours. Zero if there is no terrain. */
    fun terrainHeight(x: Float, z: Float): Float

    val hasTerrain: Boolean

    /** The field vector at a world point, into [out] as (vx, vy, vz). Zero if there is no field. */
    fun fieldAt(x: Float, y: Float, z: Float, out: FloatArray)

    val hasField: Boolean

    /** How many stops this tour has. */
    val stopCount: Int

    /** The tour's own name, so one scene can close every tour in the series. */
    val tourTitle: String

    // -------------------------------------------------------------- drawing

    /**
     * A lit ellipsoid. [small] picks the cheap 12x8 mesh over the 22x16 one — use it for anything
     * that is not the centrepiece, and for anything drawn more than about a dozen times.
     */
    fun ball(
        x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
        base: FloatArray, accent: FloatArray, alpha: Float = 1f,
        rotDeg: Float = 0f, ax: Float = 0f, ay: Float = 1f, az: Float = 0f,
        pattern: Float = 0f, glow: Float = 0f, small: Boolean = true
    )

    /** A rod between two points: an axis, a strut, a segment of a linkage. */
    fun rod(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        radius: Float, base: FloatArray, accent: FloatArray, glow: Float = 0f
    )

    /**
     * The shared line buffer. Fill it with [MathMesh] calls starting at vertex 0, then call
     * [flushLines] with the vertex count. Capacity is [lineCapacity] vertices; the MathMesh
     * builders stop writing rather than overflow, so a scene that asks for too much simply
     * draws less.
     */
    val lineBuf: FloatArray
    val lineCapacity: Int
    fun flushLines(vertexCount: Int, width: Float = 2f)

    /** The same, for GL_TRIANGLES: shaded patches, filled bars, area under a curve. */
    val triBuf: FloatArray
    val triCapacity: Int
    fun flushTris(vertexCount: Int)

    /**
     * Mathematical notation hung at a world point, facing the eye.
     * [height] is the glyph height in world units. [anchor] 0 centres it, -0.5 puts its left edge
     * on the point, +0.5 its right edge. [rise] lifts it by that many glyph heights.
     * Markup: `x^2`, `e^{-x}`, `a_1`, `v_{max}`; everything else is literal, so ∫ Σ √ π ∂ ∇ ≈ → work.
     */
    fun text(
        s: String, x: Float, y: Float, z: Float, height: Float,
        tint: FloatArray, alpha: Float = 1f,
        style: GlyphBoard.Style = GlyphBoard.Style.MATH,
        glow: Float = 1f, anchor: Float = 0f, rise: Float = 0f
    )

    /** How wide [s] would be if drawn at [height], so a scene can lay a line of notation out. */
    fun textWidth(s: String, height: Float, style: GlyphBoard.Style = GlyphBoard.Style.MATH): Float
}

/**
 * One landmark. Implementations are stateless objects — every scene in the app is a pure function
 * of the kit's state and the clock — so they can be written, reviewed and swapped independently.
 */
interface MathScene {
    /**
     * How many stops away the landmark starts to fade in. The default keeps the next stop from
     * hanging as a bright target down the passage; raise it only for something genuinely large,
     * like a curve that runs the whole length of a leg.
     */
    val reach: Float get() = 1.3f

    /**
     * How far past its own stop the scene's geometry reaches, in stops. Used only to decide
     * whether the whole landmark is behind the camera and can be skipped, so a long graph is not
     * culled at its origin while half of it is still in front of the viewer.
     */
    val deep: Float get() = 0f

    /**
     * WHERE THE SUBJECT IS, relative to the stop's own rail frame, and how big it is.
     *
     * While the craft is alongside a stop the camera stops flying and settles into a composed view
     * of the thing being presented — held steady against the STOP, not against the ship, so the
     * craft may drift on through the frame while the figure stays exactly where it is. These four
     * numbers are what the camera aims at.
     *
     * The defaults describe where a flat figure is put by convention in this app: a little to one
     * side of the rail, slightly raised, about two units across. A scene that centres its subject
     * on the rail, or hangs it ahead, says so by overriding them.
     */
    val focusSide: Float get() = -1.15f
    val focusUp: Float get() = 0.20f
    val focusAhead: Float get() = 0f
    /** Roughly the radius of the subject, in world units. Sets how far back the camera stands. */
    val focusRadius: Float get() = 1.9f

    /**
     * True if this scene's object pursues the craft, like the neutrophil that chases the Mote in
     * the sibling app. The renderer drives one chaser per tour from its stop.
     */
    val chases: Boolean get() = false

    /**
     * One short line for the HUD, under the cut ladder: whatever this stop is actually measuring
     * — the running total, the partial sum, the work done, the flux through the box. Numbers that
     * need to be READ live here, in 2D, where they are legible; the 3D scene stays geometry.
     * Null for a stop that is not measuring anything.
     */
    fun readout(kit: SceneKit): String? = null

    /**
     * Draw the landmark for stop [i]. [n] is the stop's own data (radius, colours, name).
     * Fade is applied for you: [SceneKit.ball] and [SceneKit.text] scale their alpha by it, and
     * the line and triangle paths are faded by the shader.
     */
    fun draw(kit: SceneKit, n: TourNode, i: Int)
}
