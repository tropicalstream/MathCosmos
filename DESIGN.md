# MathCosmos — series design

> **Provenance.** This document is the design the project is being built from. It was produced by
> a panel of independent design agents working from a survey of the InnerCosmos engine, under the
> brief "a railed stereoscopic tour of mathematics, algebra to advanced calculus, for the RayNeo
> X3 Pro". The philosophy it commits to — **see the theorem** — is the reason the stop list looks
> the way it does: a topic that could only be shown as marks on a wall is not in here.
>
> **Two deviations from the text below, both deliberate, both since it was written:**
>
> 1. **There IS text rendering now.** The design was written under the constraint that the engine
>    could not draw notation in 3D, and it argues at length (§4.6) for having no formula plates.
>    `GlyphBoard` has since been built and proved legible on the glasses, so scenes DO label their
>    geometry — an axis, a length, a value, the identity beside the shape it describes. The
>    principle behind §4.6 still stands and is enforced: notation names what you are looking at,
>    it never substitutes for it. Numbers that need to be read carefully still live in the 2D HUD.
> 2. **Six tours, not four.** The user asked for the full algebra-to-advanced-calculus arc and was
>    offered it as four tours; the design argues (§1.1) that four forces stops to carry two ideas
>    each, which breaks the "say what you understood in one sentence" rule. The span is identical.
>    Collapsing back to four is a matter of merging tours I+II and V+VI and cutting stops.
>
> **Built so far:** Tour I, all thirteen stops. Tours II–VI are specified here and not yet built.

---

# MATHCOSMOS
### A railed stereoscopic tour of mathematics for the RayNeo X3 Pro — sister ship to InnerCosmos

**Design philosophy, stated once and obeyed everywhere below: SEE THE THEOREM.** Every stop in this document was chosen because it has a body. If a concept could only be shown as marks on a wall, it is not here — I have listed those exclusions plainly in §1.4 rather than smuggling them in as a plate. There is no text in the 3D scene and there are no formula plates anywhere in this series (see §4.6). The vocabulary is geometry, colour, glow, motion, and scale, and that is enough, because most of calculus was geometry before it was notation.

---

# 1. THE SERIES

Six tours. Roughly three hours end to end. Each tour is 12–13 stops and ends with the same closing move — **THE VIEW FROM OUTSIDE** — where the passage walls go transparent and you see, whole and from a distance, the object you have spent half an hour flying inside. That closing stop is the series' signature; it is what makes the inset map and the 3D world the same thing (see §3.3).

| # | Title | Pitch | Length | Span |
|---|---|---|---|---|
| **I** | **THE SOLID GROUND** | Algebra is carpentry: every identity you were made to memorise is a shape being rearranged in front of you. | 33 min · 13 stops | Number as length; distributive law; completing the square; difference of squares; Pythagoras; scaling laws (k², k³); conic sections; parabola as locus; the unit circle; multiplication by i as rotation; logarithms as a re-ruled axis; exponential vs. linear growth |
| **II** | **THE APPROACH** | The corridor's roof is a curve, and the ship's two arms measure how steep it is — then close until they can't be closed any further. | 33 min · 13 stops | Function as machine; ε–δ limit; continuity, removable holes, jumps; average rate of change; the derivative as the limit of secants; local linearity; the derivative as a second curve; chain rule; product rule; e and eˣ; optimisation and the flat spot; Newton's method |
| **III** | **THE ACCUMULATION** | Accumulation is the ship's own wake; the Fundamental Theorem is the moment two instruments read the same number and keep reading it. | 32 min · 12 stops | Area as accumulation; Riemann sums, over/under error; the limit of sums; **the Fundamental Theorem**; the evaluation form F(b)−F(a); signed area; solids of revolution; arc length; improper integrals; substitution; integration by parts; mean value theorem for integrals |
| **IV** | **THE INFINITE** | A tower that stops growing, a tower that never does, and a curve you can force to agree with another one term at a time — until the world ends at a definite ring. | 30 min · 12 stops | Sequences and convergence; partial sums; geometric series; harmonic divergence; comparison/ratio tests; alternating series and error bounds; **Taylor polynomials order by order**; the remainder; radius of convergence; the sine series; the complex exponential as a helix |
| **V** | **THE OPEN COUNTRY** | The tube goes ghost and you are flying a canyon through a landscape whose height is a function of where you stand. | 33 min · 13 stops | Functions of two variables; partial derivatives; tangent plane and differentiability; **gradient**; level sets and perpendicularity; directional derivatives; saddles and the second-derivative test; Lagrange multipliers; double integrals; Fubini; the Jacobian; where smoothness fails |
| **VI** | **THE FIELD AND THE FLOW** | Stop steering. The field carries you, the probes measure what it does to a box and to a wheel, and the last theorem says the inside is decided by the rim. | 34 min · 13 stops | Vector fields; flow lines; **divergence** by probe; **curl** by paddle wheel; conservative fields and potential; Green's theorem; divergence theorem; **Stokes' theorem**; slope fields and first-order ODEs; equilibria and stability; second-order ODEs and the phase plane; a PDE (heat and wave) |

### 1.1 Why six and not three

The ladder (§3) runs coarse→fine→coarse inside each tour. Compressing this into three tours would either make each tour 25 stops (a two-hour ride, thermally and physically impossible on the glasses) or would force stops to carry two ideas. A stop carries one idea. That is the rule that keeps "say what you understood in one sentence" honest.

### 1.2 The prerequisite chain

I → II → III is strictly ordered. IV needs III (it is about the limit of a sum). V needs II (partials are derivatives). VI needs V (the gradient is a vector field) and III (flux is an integral). A viewer can start at V with a nudge, but the design assumes the order.

### 1.3 What recurs, deliberately

Three objects come back across tours, and their return is the argument that mathematics is one subject:

- **The unit rod** from I-1 is put back in your hand at VI-13.
- **The helix** appears at IV-11 as e^{iθ} and returns at VI-11 as the phase portrait of a spring. Same geometry, second meaning, and the crew says so out loud.
- **The landscape** built in V returns in VI-5 as a potential and in VI-12 as a heat map that melts.

### 1.4 What I deliberately leave out, and why

- **All symbolic technique drills** — partial fractions, trigonometric substitution, the integral tables, factoring practice, polynomial long division. These are notation management. They have no body. Showing them would require a formula plate, which this series does not have.
- **Proof by induction, formal logic, set theory.** Genuinely important, genuinely invisible. There is no stereoscopic payoff in a quantifier.
- **Number theory, primes, modular arithmetic.** A prime is not a shape.
- **Probability and statistics.** A separate subject with its own excellent visual language (it deserves its own tour series, not two rushed stops here).
- **ε–δ *proofs*.** The definition is here as a physical funnel (II-2). Writing an actual proof is a text activity.
- **Uniform convergence, Lebesgue integration, measure theory.** The pathologies that motivate them cannot be seen at the resolution of a waveguide display, and pretending otherwise would violate "do not fake it."
- **Linear algebra as a subject** — matrices, eigenvectors, determinants, diagonalisation. This is the painful omission, because eigenvectors are *superbly* three-dimensional (the arrows that don't turn). It is left out because it is a different subject and would double the series. The Jacobian stop (V-11) is the bridge, and the determinant appears there as an area ratio without being named a determinant. **Earmarked as MATHCOSMOS VII: THE TRANSFORMATION.**
- **Complex analysis** beyond IV-11's helix and IV-9's excursion off the real line.
- **Series convergence subtleties** — conditional vs. absolute, rearrangement. One honest line of dialogue at IV-6, no stop.
- **The full quadratic formula as a formula.** I-3 builds it as carpentry and the crew says explicitly that the formula is "the carpentry written down so you don't have to build it each time." The symbols never appear.

---

# 2. EVERY STOP

Format for each: **name** · *[ladder rung]* — the sentence the viewer should walk away saying — the mathematical content — **See:** the concrete scene — **Like:** the structurally analogous InnerCosmos scene.

Node conventions throughout: 12–13 stops, z = 0, −16, −32 … −194, x weaving ±(1.6…2.6), y ±0.4, exactly as `TourMap.kt`. Radius per node is *meaningful* (it is the tolerance — see §4.3), not decorative. All instance counts below assume `quality == 0`; halve at q1, drop decoration entirely at q2.

---

## TOUR I — THE SOLID GROUND
*Wall palette: slate and cold chalk, warming to amber by the log ruler. Ambience: COUNT → PLANE. Passage radius 2.0–4.2 (algebra is roomy; nothing is being pinned down yet).*

**1. THE UNIT** · *rung: 1* — "A number is a length I can pick up and carry."
Number as magnitude; the choice of unit is a choice, not a fact.
**See:** The Caliper opens both jaws until they exactly span one wall-ring spacing, and a single glowing rod snaps between them — the unit. Ten copies of it detach and lay themselves end to end down the corridor floor as a ruler, one per ring, each landing with a lamp-flash. Lattice motes drift past at integer offsets only (the drift field's points are *snapped to a 1-unit grid*, which is instantly legible as "these are whole numbers"). As the ship passes, the ruler behind it stays lit; ahead, unmarked floor.
**Like:** THE THRESHOLD — an establishing stop where the ship's own dimensions calibrate the world.

**2. THE RECTANGLE** · *rung: 1* — "Multiplying is making a rectangle, and splitting the rectangle is the distributive law."
a(b+c) = ab + ac as area.
**See:** A flat lit plate (`blobAt` with sz≈0.02) spanning a × (b+c), standing across the corridor at |s| ≈ 2.6 so the chase camera passes beside it. A vertical cut-line of light slides across it; at the cut the plate cleaves into two plates that separate by half a unit, drift apart, and their two areas are echoed as two stacks of unit tiles on the floor that add to the same count as the original stack. The ship flies along the cut.
**Like:** THE MEMBRANE — a flat sheet you fly along the face of.

**3. THE COMPLETED SQUARE** · *rung: 1* — "Every quadratic is a square with a corner missing."
x² + bx = (x + b/2)² − b²/4. The whole origin of the quadratic formula.
**See:** *The flagship of Tour I.* An x-by-x plate standing upright, with a b-wide strip attached along its right edge. The strip splits down the middle and the lower half **swings 90°** about the plate's corner (a single `Matrix.rotateM` animated over 3 s) to lie along the bottom edge — making a visible L. The L has an obvious empty corner. A small (b/2)² plate descends from above with a lamp-glow and seats into it with a flash and a click in the SFX bed. The shape is now unmistakably a perfect square. The added corner keeps its own colour, so you can see the debt. Ship parks at the corner, arms out, holding the corner square in its jaws before releasing it.
**Like:** THE RIBOSOME — parts arriving and seating into a structure that only makes sense once assembled.

**4. THE DIFFERENCE OF SQUARES** · *rung: 1* — "a² − b² is a frame, and a frame unrolls into a strip."
(a−b)(a+b) as a dissection.
**See:** A large square plate with a smaller square lifted bodily out of one corner (it rises and hangs above, glowing). The remaining L-shape is cut once; the smaller limb slides and rotates 90° to join the longer limb end-on, forming a plain rectangle whose two edges are visibly (a+b) and (a−b) — measured live by the ship's jaws, which extend and clamp each edge in turn.
**Like:** THE VDJ (THE SHUFFLE) — segments cut and recombined into a new valid arrangement.

**5. THE RIGHT ANGLE** · *rung: 1* — "The square on the long side is exactly the other two poured in."
Pythagoras, by conservation of stuff rather than by algebra.
**See:** A right triangle of three struts standing in the corridor, with a square plate erected on each side, each square filled with a point cloud of grains (≈220 and ≈150 points; one `PointMesh` draw for both). On a beat, both small squares **dissolve into their grains**, which stream along two curved rails into the large square and pack it — and the large square fills *exactly*, no grains left over, no gaps. Run it twice. The ship hangs at the right angle, lamp lighting the hypotenuse.
**Like:** THE ALVEOLUS — a transfer of a conserved quantity across a boundary, made of particles.

**6. THE TWINS** · *rung: 1 → 3 (scale ×3)* — "Doubling a thing quadruples its skin and multiplies its bulk eightfold."
Similarity; area scales as k², volume as k³. The reason big animals are shaped differently from small ones — and the reason the ship's own shrinking matters later.
**See:** Three copies of the same simple solid (a hull-shaped blob) at scale 1, 2, 3 standing in a row down the corridor. Beside each, a wall of unit tiles (1, 4, 9 — literally counted out as separate plates) and a stack of unit cubes (1, 8, 27 — `blob` meshes, quality-gated to 1/4/9 at q1). As the ship passes each twin, the tiles and cubes assemble themselves with a ticking count in the audio bed. The stereo does the work: the 27-cube stack is *visibly* three deep, and you can only see that in stereo.
**Like:** THE ATOM's electron shells — nested scales you fly between.

**7. THE SLICED CONE** · *rung: 1* — "Circle, ellipse, parabola and hyperbola are one cone, cut at four angles."
Conic sections as sections.
**See:** *Flagship.* A double cone of light-lines (one `LineMesh`, ~48 generators, built once) standing with its axis across the corridor at |s| ≈ 3.0. A translucent cutting plane (one thin `blobAt` plate at alpha 0.25, depth-mask off) rotates slowly about a horizontal axis. Where it meets the cone, the intersection curve is computed and drawn live into a `DynMesh` (128 verts, `GL_LINE_STRIP`) in a bright accent colour. As the plane tilts past the cone's own slope, you watch the ellipse stretch, become unbounded at exactly the critical angle, and open into a hyperbola's two branches. The ship flies *through* the plane at the moment the parabola forms.
**Like:** THE HEART's valve leaflet — hand-built model matrix, a hinged plane whose angle is the whole point.

**8. THE FOCUS** · *rung: 1* — "Every point on a parabola is the same distance from one point and one line."
The locus definition.
**See:** A single bright bead (the focus) floating off-axis; a straight rail of light along the lower wall (the directrix). Twelve beads sit on the parabola; from each, two struts are drawn — one to the focus, one dropping square to the directrix — and the two struts are drawn as the *same colour and same length*, matched. A thirteenth bead is dragged along the curve by the ship's arm and its two struts stay equal the whole way, visibly stretching together. Where the ship's arm pulls the bead *off* the curve, the struts go unequal and turn red.
**Like:** THE SENTINEL — one tracked object with a live geometric relationship to a fixed point.

**9. THE WHEEL** · *rung: 1 → π/6 per ring* — "Sine and cosine are just the shadow of a point going round a circle."
The unit circle; sine as a projection; the graph as an unrolling.
**See:** A vertical ring of light standing across the corridor (24-segment `DynMesh` `GL_LINES` ring, exactly the action-potential ring's code). A bead runs round it at constant rate. Two struts drop from the bead: one to the horizontal diameter, one to the vertical. **As the ship advances down the corridor, the vertical shadow's height is left behind as a trail** — a sine wave literally unrolls along the wall behind the ship, drawn into a rolling `DynMesh` ribbon. The corridor's along-axis has become the angle, and the wall-rings are now spaced π/6 apart. That re-ruling of the rings is visible.
**Like:** THE NEURON's action-potential ring — same ring geometry, new meaning.

**10. THE TURN** · *rung: 1* — "Multiplying by i is a quarter turn, and nothing else."
Complex multiplication as rotate-and-scale. Seeded here; paid off in IV-11.
**See:** An arrow (strut + blob head) from a bright origin bead, lying in the vertical plane across the corridor. The ship's arm taps it; it snaps 90° counter-clockwise. Tap, tap, tap — four taps and it is home. Then a second arrow of length 1.5 at 30°: the two arrows fuse and the product arrow springs out with visibly the *sum* of the angles and the *product* of the lengths, with two ghost arcs showing the angles being added end to end.
**Like:** THE MOTOR — a rotor whose angular position is the quantity of interest.

**11. THE STRETCHED RULER** · *rung: 1 → ×10 per ring* — "A log scale turns multiplying into sliding, because it turns lengths into exponents."
Logarithms; why a slide rule works; why we plot things on log axes.
**See:** *The passage itself teaches this stop.* Approaching, the wall rings are evenly spaced (as they have been all tour). At the stop, over about four seconds, **the rings re-space themselves** — the ring spacing driven by a per-node uniform in the wall shader — so that each successive ring is ten times further along the underlying quantity, and the ship's speed relative to the *quantity* explodes while its speed relative to the *rings* is unchanged. Two rod-rulers hang alongside; the ship slides one against the other, and the sum of two lengths lands on the product of two numbers, marked by beads that light up.
**Like:** THE HIGHWAY — a stop whose meaning is carried by the geometry of the track itself.

**12. THE DOUBLING** · *rung: 1, world inflate ×1/8* — "Anything that grows in proportion to itself will beat anything that grows by adding, always, eventually."
Linear vs. exponential; sets up e.
**See:** Two beads race down the corridor along the wall. One advances a fixed height per ring. One doubles per ring. For the first six rings the linear bead is ahead and Engineering says so. Then the doubling bead goes through the roof — and rather than losing it, the **world-inflate** kicks in (the world scaled about the ship by 1/2, twice) so that the corridor and the linear bead shrink to nothing while the exponential bead stays in frame. The linear racer becomes a speck on the floor.
**Like:** THE DESCENT's scale-jumps — the inflate mechanism used to keep an unbounded thing visible.

**13. THE VIEW FROM OUTSIDE** · *rung: — (whole tour)* — "All of that was one flat picture; I was inside it."
**See:** Wall alpha ramps to zero over three seconds. The corridor becomes a thin bright thread hanging in a black volume, and every landmark of the tour is visible along it at once, small: the square, the cone, the wheel, the two racers. A 1400-point star field behind (depth test off, exactly `drawLookBack`). The ship pulls back and up; the inset map's little diagram and the thing you are looking at become recognisably the same drawing.
**Like:** THE LOOK BACK, verbatim in structure.

---

## TOUR II — THE APPROACH
*Wall palette: deep indigo brightening to white at the throat. Ambience: CURVE → LIMIT. **THE TRACE** — a persistent glowing ribbon running the entire length of the rail at height f(x) along the upper wall, one `LineMesh`, one draw call, built in `applyMap` — is introduced here and is present in every single-variable tour. **The corridor's roof is the function.** Passage radius = the tolerance currently in play: 3.4 at the start, funnelling to 0.9 at the throat, and back out.*

**1. THE MACHINE** · *rung: Δx = 1* — "A function is a rule: one number goes in, exactly one comes out."
Function, domain, codomain, single-valuedness.
**See:** A gate arch spanning the corridor. Beads enter along the floor rail at evenly spaced x and each **rises to its own height** and joins the TRACE, which is being written just ahead of the ship. The ship's arms catch one bead in mid-rise, hold it, and let it go — it resumes to exactly the same height. Then a bead is fed in that would need two heights: it splits, flickers red, and is rejected by the gate. That rejection is what "exactly one" means.
**Like:** THE FACTORY — a processing station with an input stream and an output stream.

**2. THE NARROWING** · *rung: δ = 1 → 0.1 → 0.01* — "A limit is a promise: tell me how close you want the answer, and I'll tell you how close to stand."
The ε–δ definition, physically.
**See:** *Flagship.* The passage funnels hard into a throat. Two horizontal light-planes close in above and below the target height — that is ε, and **Engineering names it out loud** ("give me a tenth"). The walls answer: two vertical rings, ahead and behind the target x, slide inward until every part of the TRACE between them lies inside the ε planes. The passage radius *is* δ. Then ε halves and the whole thing tightens again, three times. The ship threads the throat, and the throat is genuinely tight enough that you flinch.
**Like:** THE AIRWAY — the tunnel narrowing is the content, not the scenery.

**3. THE HOLE** · *rung: δ = 0.01* — "Continuous means I can fly the whole way without lifting off."
Continuity; removable discontinuity vs. jump.
**See:** Two halves. First, the TRACE has exactly one bead missing at x = a — a visible gap one bead wide, with a hollow ring drawn around the absence and the limit's value marked by a faint ghost bead the ship's arm can pass straight through. Second, further along, the TRACE **steps**: a sheer cliff face is drawn across the corridor wall, and the ship's arms extend to bridge it and cannot — they reach past each other in different planes. The difference between "a missing brick" and "a broken road" is a thing you can see with your hands.
**Like:** THE WOUND — a discontinuity in a surface, drawn as an actual break.

**4. THE CHORD** · *rung: h = 1* — "Slope is rise over run, and I can measure it with two touches."
Average rate of change; the difference quotient.
**See:** The Caliper extends both arms up to the TRACE, one at x and one at x + h, and clamps. A bright strut is drawn between the two contact points — the secant. Hanging below it, a shadow right triangle in two colours: the run strut along the rail, the rise strut vertical. The ship's HUD readout shows rise/run. Fly forward: the arms stay clamped and the secant tilts as the curve changes.
**Like:** THE MARROW's probe arms — the existing `armStops` mechanism doing real work.

**5. THE CLOSING JAW** · *rung: h = 1 → 0.5 → 0.1 → 0.01* — "Shrink the run until the chord stops moving; where it stops is the curve's own direction."
The derivative as the limit of secants.
**See:** *Flagship.* Same clamp. Now h halves, four times. Each time, the ship itself **shortens** (via the ship-length ladder — the Caliper's jaw span *is* h, so the ladder rung and the jaw are the same object) and the world inflates to compensate so the visual scale is constant. The secant rotates a little less each time and settles. Every previous secant is left behind as a fading ghost strut, so a fan of chords converges visibly onto one line. At the end, the tangent strut and the TRACE are drawn in the same colour, touching.
**Like:** THE DESCENT's scale ladder — the ladder is the teaching device, not a caption.

**6. THE STRAIGHT WORLD** · *rung: h = 10⁻⁴* — "Zoom far enough into a smooth curve and it *is* a line — or near enough that I can't tell."
Local linearity; the tangent as the best linear approximation.
**See:** World-inflate ×8, twice, about the ship. The TRACE visibly flattens; the drift dust thins to nothing; the wall rings spread apart until only two are in view. The tangent strut and the curve become one object. **Honesty beat:** the crew states plainly that the curve is not becoming straight — we are getting closer, and the curve is doing what it always did.
**Like:** THE MEMBRANE — an inflate that changes what "surface" means.

**7. THE FIELD OF SLOPES** · *rung: h = 10⁻⁴* — "The steepness is itself a curve, and I can read it off the floor."
The derivative as a function; f′ as a graph.
**See:** A **second ribbon** appears along the corridor floor — the derivative TRACE, drawn live into a rolling `DynMesh` as the ship advances. At every ring, a short needle on the roof shows the tangent's tilt and a bead drops from it to the floor ribbon at the matching height. Where the roof is flat, the floor ribbon crosses zero — and this is visible in three or four places along the leg. The ship flies in the corridor between two curves that are about the same thing.
**Like:** THE HIGHWAY — two parallel tracks with cargo passing between them.

**8. THE HANDOFF** · *rung: h = 10⁻⁴* — "If I move twice as fast through the input, everything downstream happens twice as fast."
The chain rule as composed rates. *(See §6.1 — this stop is one of the three I am least sure of, and this is the corrected version.)*
**See:** A gate marks where the corridor's *own ruling* changes: past it, the ship covers two rings of the inner variable u for every one ring of x. The roof shape as a function of u is unchanged — it is the same curve — but the rate at which the roof rises **as the ship experiences it** doubles, and the floor's derivative ribbon jumps to double height at exactly that gate. Two speedometer bars on the bow: "rings of u per ring of x" and "rise per ring of u". Their product is drawn as a third bar that matches the floor ribbon's height, continuously.
**Like:** THE MUSCLE's sarcomere — nested rates, where the outer motion is the sum of inner ones.

**9. THE GROWING RECTANGLE** · *rung: h = 10⁻²* — "A rectangle growing on both sides gains two strips and a crumb, and the crumb doesn't matter."
The product rule.
**See:** *Flagship.* A rectangle plate u × v standing across the corridor, both sides growing slowly. The two new strips light up in two distinct colours (u·dv in cyan along the top, v·du in amber along the side) and the tiny corner square dv·du flickers in red at the far corner. As dt shrinks (with the ladder), the two strips stay proportionally the same and **the red crumb visibly vanishes into nothing** — it is second order and you watch it lose the race. The ship parks at the growing corner so the crumb is a metre from your face in stereo.
**Like:** THE DIVISION — a structure whose growth mechanism is the whole content.

**10. THE SELF-SLOPE** · *rung: h = 10⁻²* — "There is exactly one growth curve whose steepness equals its height, and that fixes the number e."
e, and why eˣ is its own derivative.
**See:** *Flagship.* The roof TRACE is eˣ. At every ring the ship drops a vertical rod from the TRACE to the rail (the height) and lays a needle along the tangent scaled to its slope (the steepness), and the two are drawn **side by side as a matched pair, same length, every single ring, for eleven rings**. Then the crew swaps the roof for 2ˣ: the needle is now visibly shorter than the rod at every ring. Then 3ˣ: the needle is longer. Then back to e: matched. You have watched a number be cornered by a condition.
**Like:** THE MOTOR — a stop where one quantity being exactly equal to another is the payoff.

**11. THE FLAT SPOT** · *rung: h = 10⁻²* — "The best point is where the ground stops tilting — but 'flat' doesn't always mean 'best'."
Critical points; local vs. global maxima; the inflection that fools you.
**See:** The roof rises to a summit and falls. A heavy bead released on the roof rolls up, slows, and comes to rest exactly at the summit; the tangent needle goes horizontal there and the floor's derivative ribbon crosses zero. Then a second, lower summit further on — the bead rests there too, and Engineering points out it is not the best one. Then a **shelf**: the roof flattens, the needle goes horizontal, the derivative ribbon touches zero and comes back up — and the bead pauses, then rolls on. Three zeros, three different meanings, all visible.
**Like:** THE KIDNEY — a filtration stop where the point is which things pass a test and which only appear to.

**12. THE HUNT** · *rung: error 10⁻¹ → 10⁻⁹* — "Slide down the tangent, guess again, and the guesses collapse onto the answer faster than you'd believe."
Newton's method; quadratic convergence.
**See:** The TRACE crosses the rail somewhere ahead. From a start bead, the tangent strut is drawn all the way down to the rail; where it lands, a new bead. Repeat: four struts making a bright zig-zag staircase down the corridor, each step massively shorter than the last. **The ship jumps forward along the rail once per iteration**, so the iterations are places. The ladder plunges from 10⁻¹ to 10⁻⁹ in four steps and that plunge is the drama. Then the honest failure case: a start bead near a flat spot flings its tangent off down the corridor and out of sight.
**Like:** THE PHAGE — a discrete, staged, repeating process with a visible failure mode.

**13. THE VIEW FROM OUTSIDE** · — "That whole corridor was the space under one curve."
**See:** Walls dissolve; the TRACE hangs as a single bright curve in black with the floor's derivative ribbon under it, both visible whole, the funnel throat visible as a pinch, and the Newton staircase glinting at the far end.

---

## TOUR III — THE ACCUMULATION
*Wall palette: gold and bronze. Ambience: SUM. **New persistent mechanic: THE WAKE.** The ship trails a translucent sheet from the rail up to the TRACE; the corridor behind it is filled with glowing volume, ahead of it empty. Accumulation is literally what the ship leaves behind. One `DynMesh(2048)` in `GL_TRIANGLES`, depth-mask off, rewritten each frame from a ring buffer of the last ~40 rail positions.*

**1. THE WAKE** · *rung: Δx = 0.5* — "The area under a curve is just how much I've swept up so far."
Accumulation as a process, before it is a formula.
**See:** The emitter opens. The sheet unfurls behind the ship, floor to roof, glowing amber. Look back over your shoulder (head tracking, or the observation deck view) and the corridor behind you is *full*; ahead it is empty. A running total bar on the HUD grows. Where the roof is high, the wake is deep. The ship slows and the wake still grows — because it is area, not speed.
**Like:** THE BLOODSTREAM — a continuously-generated volumetric field tied to ship motion.

**2. THE SLABS** · *rung: Δx = 0.5, n = 8* — "Cut it into slabs, add them up, and you can see exactly how wrong you are."
Riemann sums; left/right endpoints; over- and under-estimates.
**See:** Eight tall slab plates stand under the TRACE across the leg, each touching the curve at its left edge. Above each slab, the sliver of curve it fails to reach is filled in **red**. The ship flies down the aisle between the slabs (they sit at |s| = 2.4 and 3.0, two rows), so you pass through the error. Then the crew switches to right endpoints and the red slivers flip to *below* the curve — over-estimate — and the two totals bracket the truth, drawn as two bars with the answer between them.
**Like:** THE GUT's villi — a repeated vertical array you fly between.

**3. THE THINNING** · *rung: Δx = 0.5 → 10⁻⁴* — "Keep halving the slab and the red goes to nothing; what's left is the integral."
The limit of Riemann sums.
**See:** Same slabs, but now each halving is paired with a world-inflate so the slabs stay the same *apparent* width while their true width halves. The ladder reads Δx dropping four times. The red slivers dim, thin, and go black. The two bracketing bars close on one another until they are one bar. The ship's own length shrinks with Δx — again, the ship *is* the resolution.
**Like:** THE DESCENT's ladder — a staged shrink that means something.

**4. THE SWEEP AND THE HEIGHT** · *rung: Δx = 10⁻⁴* — "The speed at which my total grows is exactly the height of the curve above me."
**The Fundamental Theorem of Calculus.** The most important stop in the series.
**See:** *Flagship.* Two things at once, and the whole stop is the fact that they agree. Behind: the wake, filling. On the floor: a **second ribbon**, A(x), the running total, climbing. The ship extends both arms: the left jaw holds a rod cut to the roof height f(x); the right jaw holds a needle set to the tilt of the floor ribbon A′(x). They are drawn in the same colour, and **they are the same length, and they stay the same length for the whole leg** — through a peak (both grow), a trough (both shrink), a flat spot (the ribbon goes level). The crew does not assert this; they measure it eleven times in a row and let you notice.
**Like:** THE HEART — the stop where two rhythms lock and the lock is the content.

**5. THE TWO CLOCKS** · *rung: Δx = 10⁻⁴* — "If I have a total-so-far function, I never have to add the slabs at all — I just subtract two readings."
The evaluation form: ∫ₐᵇ f = F(b) − F(a).
**See:** Two vertical gates across the corridor at a and b. At each gate, the floor ribbon's height is drawn as a solid rod standing on the rail. The shorter rod detaches, floats over, and **subtracts itself from** the taller one — the overlapping part goes dark, and the remaining lit segment is measured against the wake volume between the two gates, which compresses into a bar of exactly that length. Ship parks between the gates with the two rods in its jaws.
**Like:** THE STORED (Bethune's bottle) — a stop about a quantity held, moved, and compared.

**6. THE SIGNED WAKE** · *rung: Δx = 10⁻⁴* — "Below the axis, the sweep pays out instead of taking in."
Signed area; why the integral of a sine over a full period is zero.
**See:** The TRACE dives below the ship's rail and the corridor's roof passes under the floor — you are now flying *above* the curve. The wake changes from gold to cold blue and the running total bar visibly **retreats**. Fly a full sine period: the bar rises, falls, and lands exactly back at zero, and the gold volume behind you and the blue volume behind you are the same size.
**Like:** THE SEPSIS — a stop whose emotional colour flips with a sign change in the underlying quantity.

**7. THE LATHE** · *rung: Δx = 10⁻²* — "Spin a shape and its area becomes a solid you can fly down the middle of."
Solids of revolution; the disc method.
**See:** The TRACE is spun about the rail. A translucent surface of revolution is generated as a swept ring `TriMesh` and **the ship flies down its axis, inside the solid.** Discs stack visibly along the length, each disc's thickness the same, each radius the roof height. The wake is now a *volume of discs* and the running total is a volume. In stereo, being inside a vase you built is the payoff.
**Like:** THE TUNNEL itself — a second tube inside the first, which is exactly what it is.

**8. THE STRING** · *rung: Δx = 0.5 → 10⁻³* — "A curve's length is the sum of tiny straight bits, and the bits are always a little short."
Arc length; the polygon under-estimate.
**See:** A bright cord laid along the TRACE, visibly made of straight chords. On the floor below, the chords are **laid end to end into a straight measuring rod** that grows to their total. Halve the chord length three times: the cord hugs the curve better and the floor rod gets longer each time, converging. Honesty beat: the polygon is always shorter, never longer, and the crew says by how much and why.
**Like:** THE SUTURE's fibrin — a chain of straight segments approximating a curve.

**9. THE HORN** · *rung: radius 1 → 10⁻³, total → finite* — "A thing can be endlessly long and still hold only so much."
Improper integrals; ∫₁^∞ dx/x² converges while ∫₁^∞ dx/x does not.
**See:** *Flagship, and the passage does the teaching.* The corridor's own radius becomes 1/x. It narrows forever; the wall rings keep coming; the far end never arrives. But the running-total bar **stops growing** — you can watch it stall against a ceiling while the corridor visibly continues. Then the crew switches the wall profile to 1/√x and the bar starts climbing again and does not stop. Same-looking corridor, opposite answer, and the only difference is how fast it narrows.
**Like:** THE AIRWAY narrowing plus THE LOOK BACK's sense of unbounded space.

**10. THE RE-RULING** · *rung: Δx = 10⁻²* — "Renaming the axis doesn't change how much is there — it just changes how hard the sum looks."
Substitution / change of variable. *(This stop has no §6 entry; the reference to §6.3 in the original draft was a slip — §6.3 is the Tour VI partial differential equation.)*
**See:** The wall rings re-space themselves (the Tour I log-ruler mechanism, generalised: ring spacing = du/dx). The roof curve, re-drawn against the new ruling, **straightens into something plain** — a curve that was a hard shape becomes nearly a flat roof. Meanwhile a **volume bar hangs in the middle of the corridor and does not move by a single pixel** through the entire transformation. That invariant bar is the whole stop; without it this is just scenery.
**Like:** THE LIVER — a stop about conversion where the conserved quantity must stay visible.

**11. THE PARTS** · *rung: Δx = 10⁻²* — "The product rule read backwards: I traded a sweep I couldn't do for one I could."
Integration by parts as area bookkeeping.
**See:** The growing rectangle from II-9 returns, full size: a rectangle u·v standing across the corridor. Its interior is divided into **two shelves** by a staircase cut following the curve — one shelf is ∫u dv, the other ∫v du. The two shelves lift apart, and their union is measured against the corner rectangle and matches. Then one shelf goes dark ("we can't sweep that one") and the other stays lit ("so sweep this one and subtract"). Ship hangs at the rectangle's corner.
**Like:** THE RIBOSOME again — but here the assembly is a trade rather than a construction.

**12. THE VIEW FROM OUTSIDE** · — "The whole run was one shape, and I have swept it."
**See:** Walls gone. The curve, its full wake as a lit slab of volume, the running-total ribbon underneath, the horn tapering off into the black at one end. The two arms of the ship still holding the rod and the needle, still equal.

---

## TOUR IV — THE INFINITE
*Wall palette: violet and silver. Ambience: INFINITE. **Passage radius = the remainder** — how much is still unaccounted for. It shrinks as the ship advances through a convergent structure and flares where things diverge. The wall rings are no longer evenly spaced: **each ring is one term**, and their spacing follows the terms, so a convergent series has rings that bunch up and stop.*

**1. THE STAIRCASE** · *rung: n = 1 … 20* — "A sequence converges if it eventually stays inside any tube you name, however thin."
Sequences; convergence as an eventual-containment property.
**See:** A line of beads down the corridor at heights a₁, a₂, a₃ … Two horizontal light-planes close in around the limit height — the tolerance band, named out loud. Beyond a definite ring, **every bead is inside the band**, and that ring is marked with a bright collar. Tighten the band; the collar slides further down the corridor but always exists. Then a non-convergent sequence: the band closes and beads keep jumping out of it forever, flashing red.
**Like:** THE NARROWING (II-2) — deliberately the same instrument, applied to a discrete object.

**2. THE TOWER** · *rung: n = 1 … 24* — "A series is a tower built one brick at a time, and the only question is whether it stops growing."
Partial sums; convergence vs. divergence of a series.
**See:** Two towers of bricks beside the rail, left and right, each brick the height of one term, stacked as the ship advances one ring per term. Left: geometric — the bricks shrink fast and the tower **tops out visibly under a ceiling plane**, with a shrinking gap. Right: harmonic — the bricks also shrink, and Engineering says "so that one stops too, right?" — and it does not; it climbs past the ceiling and keeps going. The ship must lift to keep both tops in frame.
**Like:** THE MARROW's bone lattice — a stacked structure you read vertically.

**3. THE HALVING ROOM** · *rung: gap 1 → 10⁻⁶* — "Halve the remaining distance forever and you fill exactly the room, never more."
The geometric series; 1/2 + 1/4 + 1/8 + … = 1.
**See:** *Flagship.* A room whose length is exactly 1, with a far wall. Each beat, the ship flies **half the remaining distance** and the ground it covered fills with a slab: a big slab, then half as long, then a quarter. The slabs tile the floor perfectly and the far wall gets closer and closer without ever being touched. The corridor's own rings compress into an accumulation point at the wall — you can see infinitely many rings crammed into the last stretch, which is the best picture of a convergent series there is. Honesty beat: the crew says plainly that the *ship* does arrive, because it never slowed down; it is the *sum of the slabs* that is finite, and those are two different statements.
**Like:** THE ATOM — geometry that crowds toward a centre.

**4. THE SLOW CLIMB** · *rung: n = 1 … 2⁸* — "Terms that shrink to nothing can still add up to everything."
Harmonic divergence, by the grouping proof.
**See:** The harmonic tower from stop 2, now **bracketed**: 1/3 + 1/4 is drawn as two bricks with a bracket, and beside the bracket a slab of height 1/2 that they exceed. Then the next four terms, bracketed, exceeding another 1/2. Then eight, then sixteen — each bracket wider along the corridor than the last, each contributing the same visible half-height slab. Those half-slabs stack, and stack, and the corridor's roof has to be raised twice to fit them. The ship climbs. This is a *proof* you can look at.
**Like:** THE MARROW → THE SHUFFLE — a stop where a combinatorial regrouping is the mechanism.

**5. THE TEST** · *rung: n = 1 … 24* — "If my bricks all hide under a tower I already trust, I'm safe."
Comparison and ratio tests.
**See:** A translucent wedge — the known geometric envelope — laid over an unknown tower. Where every brick is under the envelope, the section glows green and a "safe" collar closes on the corridor. Then a second tower where, from ring 9, bricks **poke out through the envelope** — those bricks flash red, the collar refuses to close, and the passage flares open. The ratio test is shown as a small gauge on each brick reading its ratio to the one before, with a red line at 1.
**Like:** THE SENTINEL — a stop with a pass/fail judgement rendered as a physical gate.

**6. THE ALTERNATING WALK** · *rung: error = next term* — "Step forward, step back a little less, and I close in — and the next step is my error bar."
Alternating series; the alternating-series error bound.
**See:** *The ship itself performs this.* The Caliper flies forward 1 unit, back 1/2, forward 1/3, back 1/4 — real motion along the rail, decreasing swings, spiralling onto a point. Each swing is drawn as a strut left behind, so the corridor fills with a visible shrinking concertina. And **the passage radius at each instant equals the size of the next term**, so the tube physically closes around the answer at exactly the rate the error bound guarantees. One honest line from Doc: the order of these terms matters, and rearranging them can land you somewhere else entirely — which is why this convergence is called conditional.
**Like:** THE HEART's inflow/outflow — the ship's own motion carrying the content.

**7. THE MATCHING CURVES** · *rung: order 0, 1, 2, 3, 4, 6* — "I can force a polynomial to agree with a curve to any order I like, at one point."
Taylor polynomials, built order by order.
**See:** *Flagship.* One anchor bead on the TRACE. From it, ribbons: a flat line (order 0, agrees at a point), the tangent (order 1, agrees in direction), a parabola (order 2, agrees in bend), a cubic, a quartic — each in its own colour, each nesting inside the last, and **each hugging the true curve over a visibly longer stretch of corridor before peeling away.** As the ship passes the anchor, the orders light up one per two seconds and the peel-away points march outward down the corridor in both directions. In stereo, six ribbons diverging from a single point is genuinely beautiful.
**Like:** THE NUCLEUS's chromatin — nested curve structures at a single site.

**8. THE PRICE OF AGREEMENT** · *rung: remainder 10⁻¹ → 10⁻⁶* — "The error isn't a vague worry; it's a thickness I can see and bound."
The Taylor remainder.
**See:** The gap between each approximation ribbon and the true TRACE is **filled with a coloured sheet whose thickness is the error** — thin near the anchor, flaring as you move away, and the flare is dramatic in stereo because you fly through the wedge. Raise the order: the sheet thins everywhere and the flare retreats. A bound plane hangs above the sheet and the sheet never touches it.
**Like:** THE CAVITY — a stop about a measured gap in a structure.

**9. THE EDGE OF THE WORLD** · *rung: |x−a| = R* — "Every one of these series has a distance beyond which it stops meaning anything at all."
Radius of convergence. *(See §6.2 — one of my three hard parts, with the fix built in.)*
**See:** *Flagship.* First with 1/(1−x): a **wall of blinding light** stands across the corridor at x = 1 — the pole, visible, obvious, a place the function itself blows up. The approximation ribbons hug the curve right up to it and shred outward past it. Everyone understands. **Then** the crew swaps in 1/(1+x²), whose corridor looks completely calm — smooth roof, no wall, nothing wrong anywhere — and yet the ribbons still tear apart at exactly |x| = 1. Engineering asks why, and the honest answer is that the reason is not on this corridor. The ship **rises off the rail into the imaginary direction** (a lateral excursion, the rail unchanged so the frame never degenerates), and there, one unit off to the side, are two bright poles at ±i, with a **sphere of light** around the anchor whose radius reaches exactly to them. The edge of the world was never on the road you were driving.
**Like:** THE LOOK BACK's opening-out, used mid-tour as a reveal.

**10. THE WAVE FROM POWERS** · *rung: order 1, 3, 5, 7, 9* — "A wave can be built out of nothing but odd powers, if you use enough of them."
The sine series.
**See:** The roof TRACE is sin x, running the full leg. Beneath it, polynomial ribbons of order 1, 3, 5, 7, 9 grow one at a time, each hugging more of the wave before flinging off through the roof. At order 9 the ribbon holds through two full humps and then leaves violently. The failure is as instructive as the success, and the ship flies past the moment of departure.
**Like:** THE NEURON — a wave along the corridor with a structure underneath generating it.

**11. THE MEETING** · *rung: θ per ring* — "The exponential and the circle are the same object seen from two sides."
e^{iθ} = cos θ + i sin θ, shown as a projection, not a metaphor.
**See:** A helix runs the length of the corridor (`buildHelix` already exists — reuse it exactly). Two flat light-planes, one vertical wall, one floor. The helix's shadow on the floor is a cosine wave; on the wall, a sine wave — both drawn live as `DynMesh` trails as the ship advances. Look down the corridor's axis from the bridge view and the helix **is a circle** — the Tour I wheel, returned. The crew says explicitly: this is a projection, not an analogy; the helix is what the complex exponential looks like, and the trig functions are its two shadows.
**Like:** THE NUCLEUS's double helix — same mesh, entirely new meaning.

**12. THE VIEW FROM OUTSIDE** · — "Infinitely many pieces, and a definite answer — as long as I stay inside the edge."
**See:** The towers, the nested Taylor ribbons radiating from one anchor, the sphere of convergence hanging in black with its two poles, and the helix threading through all of it.

---

## TOUR V — THE OPEN COUNTRY
*Wall palette: green-teal, and **the tube goes ghost**: per-node `wallAlpha` drops to 0.18–0.25, so the passage becomes a faint guide-tunnel and the real scenery is outside it. Ambience: SURFACE. The world is a wireframe landscape z = f(x,y) built once as a `LineMesh` grid (48 × 48 lines at q0, 24 × 24 at q1) plus a point-sprite dusting on the surface. The rail is a path through the (x,y) plane and the ship flies a canyon through the terrain. This is the biggest stereo payoff in the series and it is one static mesh.*

**1. THE LANDSCAPE** · *rung: cell = 1* — "One number for every place on the ground is a landscape, and I can fly it."
Functions of two variables; graph as surface.
**See:** The walls fade out over four seconds and terrain spreads left and right and ahead — ridges, a valley the rail follows, a summit off to port, a bowl to starboard. Beads dropped from the ship fall to the ground plane and then **rise to their heights**, so you watch the surface being sampled into existence. Head tracking pays off immediately: look left, there is a mountain.
**Like:** THE LOOK BACK — the moment the enclosure opens and the world is bigger than the tube.

**2. THE TWO CUTS** · *rung: cell = 0.25* — "Hold one direction still and I'm back to a single curve I already know how to handle."
Partial derivatives.
**See:** *Flagship.* Two translucent cutting planes stand through the ship, one running along x, one along y. Where each meets the terrain, a bright curve is drawn live into a `DynMesh` — two curves, crossing at the ship. The ship extends **one arm into each plane** and takes a tangent needle from each; the two needles have plainly different tilts, and one can be uphill while the other is downhill. That asymmetry, seen in stereo at arm's length, is the entire concept.
**Like:** THE MEMBRANE — two intersecting sheets with the ship at the crossing.

**3. THE PLATE** · *rung: cell = 0.25 → 0.03* — "Close enough in, a smooth landscape is a flat sheet resting on those two needles."
The tangent plane; differentiability as flatness in the limit.
**See:** A rigid square plate descends and comes to rest on the two tangent needles, held by them. World-inflate ×6, twice: the terrain around the ship flattens and rises to meet the plate. The gap between plate and ground is filled with a thin coloured film whose thickness you can see and which **thins toward nothing** as you zoom. Ship sits on the plate.
**Like:** THE STRAIGHT WORLD (II-6), promoted to two dimensions.

**4. THE COMPASS** · *rung: cell = 0.25* — "The gradient is an arrow lying flat on the ground that points straight uphill, and its length is how steep."
The gradient vector.
**See:** *Flagship.* A bright arrow lies **in the ground plane** (not on the surface — this distinction is stated aloud) at the ship's position, pointing uphill. The ship's belly drum reads it and the arrow swings live as the ship moves, lengthening where the ground is steep and shrinking to a stub in the flat bowl. Beside it, a heavy bead released on the terrain rolls off in exactly the opposite direction, every time, and its track is left glowing.
**Like:** THE SENTINEL's pursuit vector — a live direction attached to the ship.

**5. THE CONTOURS** · *rung: cell = 0.25* — "Walk a contour and you never climb, and the uphill arrow is always dead square to your path."
Level sets; ∇f ⟂ level curve.
**See:** Glowing contour rings drawn on the terrain like tide marks (a `LineMesh`, built once, 12 levels). The ship flies **along one contour**, and the gradient arrow stays rigidly perpendicular to the heading — for the entire leg, through every turn of the contour. It is not asserted; it is watched for ninety seconds. Where the contour rings crowd together, the arrow lengthens; where they spread, it shortens, and the crowding is directly visible ahead.
**Like:** THE HIGHWAY — a track followed with a persistent attached geometric relationship.

**6. THE ANY-DIRECTION** · *rung: cell = 0.25* — "Whatever heading I pick, the steepness I feel is just the gradient's shadow on that heading."
Directional derivatives; D_u f = ∇f · u.
**See:** A dial of sixteen spokes lies in the ground plane at the ship. On each spoke, a needle whose tilt is the slope in that direction. The needle tips trace a closed lobe — a `DynMesh` loop shaped like a cosine rosette — with its longest reach exactly along the gradient, its zero crossings exactly along the contour, and its most negative reach exactly downhill. The ship yaws through headings and you feel each one.
**Like:** THE MOTOR's rotor — a radial arrangement whose profile is the payload.

**7. THE PASS** · *rung: cell = 0.25* — "A place can be a summit one way and a valley the other, and that's a real kind of place."
Saddle points; the second-derivative test.
**See:** The terrain becomes a mountain pass and the ship parks in the middle of it. Two curves are drawn through the ship — one arching over (a maximum along that cut), one dipping under (a minimum along the other). Both tangent needles are horizontal. A ball placed at the saddle **sits perfectly still** — and then a nudge from the ship's arm sends it away, and repeated nudges in different directions send it to different places. Stereo makes the saddle read instantly in a way no textbook drawing does.
**Like:** THE HEART's chamber geometry — a place whose shape is only comprehensible in three dimensions.

**8. THE TETHER** · *rung: cell = 0.1* — "The best point along a fence is where the fence just grazes a contour."
Lagrange multipliers.
**See:** *Flagship.* A fence-line (the constraint curve) is drawn on the terrain, and the ship is tethered to it and rides it. The contour rings are lit. Away from the optimum the fence **cuts across** contours — you climb as you go, and the two gradient arrows (of the height f and of the fence g) point in visibly different directions. As the ship approaches the optimum, the fence's crossing angle flattens until the fence **kisses one contour tangentially**, and at that instant the two arrows swing into exact alignment and fuse into one arrow. That fusing is the theorem, and it is a two-second animation.
**Like:** THE SENTINEL — an alignment condition rendered as two vectors becoming one.

**9. THE COLUMN FIELD** · *rung: cell = 0.5 → 0.06* — "The volume under a landscape is a floor of columns, and I can fly through them."
Double integrals as Riemann sums in two dimensions.
**See:** A grid of square columns rises from the ground plane to meet the terrain — 12 × 12 at q0, 8 × 8 at q1. The ship flies **through the forest**, between columns, and the columns' tops make a stepped version of the surface overhead. Halve the grid twice; the steps smooth into the surface and the running-volume bar converges.
**Like:** THE SLABS (III-2) in two dimensions; visually like THE GUT's villi field.

**10. THE ORDER** · *rung: cell = 0.06* — "Row by row or column by column, I get the same total."
Fubini's theorem.
**See:** Two sweeps of the same column field. First a lit plane travels along x, harvesting whole rows, leaving a gold wake and a growing bar. Reset. Then a plane travels along y, harvesting columns, leaving a blue wake and its own bar. **The two bars are drawn side by side and are exactly the same length.** Cheap, clear, and the kind of thing that is obvious in a picture and opaque in notation.
**Like:** THE KIDNEY — two routes through the same volume yielding the same result.

**11. THE STRETCHED GROUND** · *rung: cell area ratio* — "Bend the coordinate grid and every little cell changes area by a factor I can measure."
The Jacobian; change of variables in two dimensions.
**See:** *Flagship.* The ground's square grid **warps** — over five seconds, into a polar grid (or a sheared/curved one), the whole terrain riding with it. One cell is highlighted throughout, drawn as a bright quadrilateral, and beside the ship its area is displayed as a small plate that grows and shrinks as the cell moves through the warp. Near the origin of a polar warp the cells are slivers; far out they are fat. The area ratio is the Jacobian, and it is drawn as the ratio of two plates side by side. The word "determinant" is never used; the thing is.
**Like:** THE VDJ (THE SHUFFLE) — a controlled rearrangement whose bookkeeping is the point.

**12. THE ROUGH PLACE** · *rung: cell = 0.03* — "Both slopes can exist and the surface can still be a cliff — smooth is more than having slopes."
The honest counterexample: partial derivatives exist but the function is not differentiable.
**See:** The terrain develops a sharp ridge and, further on, a cone tip. The ship brings its tangent plate in to land — and the plate **rocks**, catching on one edge and then the other, unable to rest. Both cutting planes still produce perfectly good curves with perfectly good tangent needles, so the two partials exist; but the plate will not sit. World-inflate: the ridge does not flatten out. It stays a ridge all the way down.
**Like:** THE WOUND — a stop that exists to show where the smooth model breaks, and to say so.

**13. THE VIEW FROM OUTSIDE** · — "That whole country was one function of two numbers."
**See:** Full pull-back. The terrain entire, the contours, the fence with its kiss point marked, the column forest still standing under one quadrant, the ridge scarring the far edge. The inset map's contour diagram and what you are looking at are visibly the same drawing.

---

## TOUR VI — THE FIELD AND THE FLOW
*Wall palette: cyan and warm orange. Wall alpha 0.15 — the ghost tube is a guide-rail only. Ambience: FIELD → SOLVE. **The AirField is generalised**: `flow` becomes a callback `(x,y,z) → (vx,vy,vz)`, and the 96 streaks genuinely advect along the field being studied, at one draw call. The ship's sway low-pass is driven by the field, so you are physically pushed.*

**1. THE ARROW AT EVERY POINT** · *rung: probe = 1* — "A field is an arrow at every place, and I can feel it in the hull."
Vector fields.
**See:** A lattice of small arrows fills the space around the ghost tube — 5×5×5 at q0, 3×3×3 at q1, each a strut plus a `blob` head, placed once per frame from an analytic field. The AirField streaks flow along them. The ship's drift changes as the field changes: in a strong region the hull pushes visibly to one side. Head-track to port and the arrow lattice recedes into depth — this is the stop where stereo tells you a field is a *volume*, not a picture.
**Like:** THE BLOODSTREAM's flow, generalised.

**2. THE STREAMLINE** · *rung: probe = 1* — "Let go and the field draws my path for me."
Integral curves; flow lines never cross.
**See:** Navigation cuts the drive and announces it. The rail ahead is a flow line and the ship simply goes where the field takes it, drawing a bright thread. Three probes are released to port and starboard; each draws its own thread, and the three threads **weave without ever touching**. Where the field is fast the threads stretch; where slow, they bunch.
**Like:** THE BLOOD → THE HEART transit — being carried rather than steering.

**3. THE PROBES OUT** · *rung: box = 0.5* — "Divergence is what a box gains or loses, and my probes can just go and measure it."
Divergence as net outward flux per volume.
**See:** *Flagship, and the existing arm mechanism does it.* The ship deploys six probe arms into a wireframe box around itself, one per face. Each face lights with a cap of colour: blue for inflow, red for outflow, brightness proportional to flux. At a **source**, all six caps are red and a bright bead sits at the box's centre; at a **sink**, all six blue; in a plain uniform flow, one face red and one blue and they cancel to nothing, and the net readout bar sits at zero. Fly through all three in one leg. The crew never says "the divergence is the trace of the Jacobian"; they say "the box is filling up".
**Like:** THE KIDNEY's glomerulus — flux across a boundary, measured.

**4. THE PADDLE WHEEL** · *rung: wheel = 0.3* — "Hold a wheel in the flow; if it spins, there's curl, and the axis it spins about is the arrow."
Curl.
**See:** *Flagship — possibly the single best "feel the theorem" stop in the series.* A six-bladed paddle wheel on a rod extends from the ship's belly into the flow. In a **shear** field it spins, hard, and an arrow along its axis grows with the spin rate. The ship rotates the rod: spin is maximal along one axis and zero at right angles to it, and you can hunt for the axis by hand. Then the honest counterexample the crew insists on: a field whose arrows visibly **curve around in a circle** but which does not spin the wheel at all (v ∝ 1/r), and a field whose arrows are all dead **straight and parallel** but which spins it briskly (a shear). Curl is not "curviness". That correction is worth the whole stop.
**Like:** THE MOTOR's ATP synthase rotor — an actual spinning thing whose rate is the measurement.

**5. THE DOWNHILL FIELD** · *rung: work meter* — "Some fields are just a landscape's uphill arrows, and then a round trip costs exactly nothing."
Conservative fields; potential functions; path independence.
**See:** Tour V's terrain returns as a ghost surface, and the field arrows are visibly its gradient — every arrow lying in the ground plane pointing uphill. The ship flies a **closed loop** while a work meter bar fills and empties, and it returns to exactly zero, twice, by two different loops. Then the crew switches on a field that is *not* a gradient (the shear from stop 4): the same loop returns a surplus, the bar does not come home, and no landscape can be drawn for it — the terrain flickers and fails to render, which is the honest visual for "no potential exists."
**Like:** THE LIVER — a stop about a conserved bookkeeping quantity and what breaks it.

**6. THE LOOP AND THE SHEET** · *rung: cell = 0.25* — "All the spin inside a patch, added up, is exactly the push around its edge — because everything inside cancels with its neighbour."
Green's theorem, shown as its own proof.
**See:** *Flagship.* A flat patch spanned by a wire loop, tiled with 8 × 8 tiny paddle wheels, all turning at their own rates. Then the animation that is the whole point: **each internal shared edge is drawn as two opposed blades, and they dim out in pairs**, sweeping across the patch, until nothing is lit but the boundary. The bar showing the interior total never changes while this happens. You have watched a cancellation argument instead of reading one.
**Like:** THE DIVISION's spindle — many small elements resolving into a boundary structure.

**7. THE BAG AND ITS SKIN** · *rung: box = 0.25* — "Everything made inside has to cross the skin to get out."
The divergence theorem.
**See:** A closed wireframe surface — a bag — around a region containing a source. Inside, the divergence boxes from stop 3 glow red at the source. Outside, the AirField streaks are seen **crossing the skin**, and the skin's facets light in proportion to the flux through them. Two bars: total made inside, total crossing the skin. Equal. The ship flies out through the skin and looks back.
**Like:** THE ALVEOLUS — transfer across a closed surface, in both directions.

**8. THE RIM** · *rung: surface area varies, total fixed* — "Stretch the sheet however you like; only the rim decides the answer."
**Stokes' theorem.** The flagship of the whole series.
**See:** A rigid wire loop, fixed in space, with a surface spanning it — a `DynMesh` (1024 verts, `GL_TRIANGLES`, depth-mask off) that **morphs continuously** between a shallow disc, a tall dome, and a long drooping sock, over about eight seconds each way. The surface is covered in paddle wheels; as it deforms, every wheel changes speed and hundreds appear and vanish. And the total bar **does not move**. Not a pixel, through the entire deformation. The ship flies through the loop and out the other side, and the only thing that never changed is the rim it passed through.
**Like:** THE HEART's valve — a fixed aperture with a changing structure behind it, where the aperture is what matters.

**9. THE SLOPE FIELD** · *rung: h = 0.25* — "A differential equation is a field of little slopes, and a solution is a curve that obeys all of them."
First-order ODEs; slope fields; the family of solutions.
**See:** A lattice of short slope segments fills the corridor, each a strut at the angle dictated by the equation at that point. The ship threads them and **grows a solution curve ahead of itself**, always tangent to the local segment, drawn live into a `DynMesh`. Four probes released at four different starting heights each grow their own curve, and the four curves fill the corridor **without ever crossing** — which is a theorem, and is visible.
**Like:** THE HIGHWAY — parallel tracks whose non-crossing is structural.

**10. THE PULL HOME** · *rung: displacement 1 → 10⁻³* — "Some answers pull you in and some throw you out, and the field tells you which before you solve anything."
Equilibria; stability.
**See:** Two horizontal lines through the slope field. Near the lower one, all the little slopes point toward it and nearby curves converge onto it; near the upper one, they point away and curves flee. **The ship is nudged off each line in turn** — off the stable one, the existing low-passed sway pulls it back and it settles; off the unstable one, the sway diverges and the ship is thrown to the wall and has to power back. You feel stability in your inner ear, which is exactly right for a head-worn display.
**Like:** THE SENTINEL's collision nudge — reusing the ship's own physical response as the teaching instrument.

**11. THE SPRING AND THE CIRCLE** · *rung: t per ring* — "A swing's position and its speed, taken together, travel in a circle."
Second-order linear ODEs; the phase plane; damping.
**See:** *The helix returns with a new meaning.* A mass on a spring oscillates along one wall, visibly. In the middle of the corridor, its phase point — position across, velocity up — runs a closed loop. Because the corridor's along-axis is **time**, the phase point traces a helix down the corridor, and from the bridge view looking straight down the axis, **it is a circle**. Add damping: the helix becomes a tapering spiral and, end-on, a spiral into the origin. The crew names the callback to IV-11 out loud: same shape, different subject, and that is not a coincidence.
**Like:** THE NUCLEUS's helix, thematically returned; structurally like THE MOTOR's phase-locked rotation.

**12. THE SPREADING** · *rung: grid = 1/24, t per ring* — "A rule about neighbours, run forward, is why heat smooths out and why waves travel."
Partial differential equations: the heat equation and the wave equation. *(See §6.3 — one of my three hard parts.)*
**See:** Tour V's terrain returns as a heat map. As the ship advances, **time advances**, and the terrain visibly melts: peaks sink, hollows fill, the whole surface relaxing toward flat. Then the rule is swapped for the wave equation and a single bump **splits into two travelling bumps** that run off in opposite directions and bounce off the corridor's ends. The crew states the neighbour rule in one sentence — "each point is pulled toward the average of the ones beside it" — and the visual is the sentence.
**Like:** THE SEPSIS's spreading glow — a field quantity evolving across a region over the length of a leg.

**13. THE VIEW FROM OUTSIDE** · — "One rod. One cut. One sum. That's all it ever was."
**See:** Everything opens. The field, the wire loop with its ghost surfaces nested inside one another, the solution curves, the terrain, the helix. And then — as the last object in the series — **the unit rod from Tour I stop 1 is put back in the ship's jaws**, one unit long, glowing, exactly as it was three hours ago. Every measurement in six tours was made of that.

---

# 3. THE SPINE

## 3.1 The single organising axis

**How finely are we willing to cut, and what survives the cutting.**

That is the whole series in one line, and it is not a retrofit — it is the actual history of the subject. Algebra measures in whole units and rearranges them. The limit tour cuts until the cut stops mattering. The integral tour cuts and re-adds and discovers the two operations are one. The infinite tour cuts infinitely many times and asks what is left. The multivariable tour cuts in two directions at once. The field tour cuts a volume into boxes and a surface into loops, and finds that the cuts cancel and only the rim survives.

The InnerCosmos spine was *scale* — how big is the Mote in metres. The MATHCOSMOS spine is the direct analogue: **how big is the cut, in units of the thing that is varying.** The machinery ports verbatim (`shipLengthM` → `shipCut`, `LADDER_EXP`/`LADDER_LABELS`, `fmtMag`, the log interpolation between breakpoints, `TourMap.lengthKeys`/`lengthM`, `TourNode.shipLenM`). Not one line of that arithmetic changes; only the labels and the meaning.

## 3.2 The HUD ladder rung

**The rung reads the length of the Caliper's jaw, in units of the current variable — which is the current cut.** This works because the ship's jaw span physically *is* the cut: it is the h in the difference quotient (II-4, II-5), the Δx of a Riemann slab (III-2, III-3), the ε band (II-2), the term index (IV), the grid cell (V), the probe box (VI-3). The ship shrinks and the rung falls together, exactly as the Mote shrinks through the body.

Each stop's rung label is given inline in §2 in italics after the stop name. The scheme by tour:

| Tour | Rung reads | Range | Notes |
|---|---|---|---|
| I | whole units of length | `1` throughout, with `×3` at THE TWINS and `×10 per ring` at THE STRETCHED RULER | Algebra doesn't cut; the flat rung is itself informative, and its *first* fall in Tour II is an event |
| II | `h` / `δ` | `1 → 10⁻⁹` | The tour's whole shape is this fall; it plunges at THE HUNT |
| III | `Δx` and a second readout, the running total | `0.5 → 10⁻⁴` | Two readouts: the cut, and the accumulated quantity |
| IV | `n` (term index) and the remainder | `n = 1 → 2⁸`; remainder `1 → 10⁻⁶` | The only tour whose rung *climbs* as well as falls |
| V | grid cell size, and cell-area ratio at THE STRETCHED GROUND | `1 → 0.03` | Two-dimensional cut |
| VI | probe box edge / wheel radius / step h | `1 → 10⁻³` | And a work meter at THE DOWNHILL FIELD |

Because the rung is a *ratio* rather than a physical length, the format string changes from `fmtLength` (metres, µ, n, p) to a plain two-significant-figure magnitude with a per-node unit label — reusing `fmtMag` exactly as written.

**A second HUD readout line** is added under the ladder: one number, driven by the same `nodeLerp` machinery, showing whatever the current stop is measuring (the running total, the partial sum, the work, the flux). This is where numbers live. **They never appear in the 3D scene.**

## 3.3 The inset map

InnerCosmos's inset is a human figure with a marker: a picture of the whole thing, with you on it. MATHCOSMOS's inset is **the graph** — a flat 2D diagram of the object the tour is about, with a marker at the ship's position, in the same 100 × 150 figure coordinates, driven by the same `mapX`/`mapY`/`mapLabel` fields with no change to `BodyMapView`.

| Tour | Inset shows | Marker |
|---|---|---|
| I | The tour's dissection sequence as a strip of twelve small icons — square, cone, wheel, ruler | which icon you're at |
| II | The curve y = f(x) with its axes | a dot at the current x, and a short tangent tick at that dot |
| III | The same curve, with the area to the left of the ship shaded in | the shading edge is the marker |
| IV | The curve with the anchor point ringed and the convergence interval bracketed | a dot at x, red outside the interval |
| V | A contour map of the terrain, with the fence-line drawn | a dot on the contour map, with a short gradient arrow |
| VI | A 2D slice of the field, with the wire loop drawn | the ship's dot and its trailing flow line |

**And this is the payoff of THE VIEW FROM OUTSIDE:** at the last stop of every tour, the walls dissolve and the 3D world resolves into visibly the same drawing as the inset you have had in the corner of your eye for half an hour. The inset is the outside view; the passage is the inside view; they are one object. That reveal is the series' recurring emotional beat, and it costs nothing to build because both already exist.

---

# 4. THE CRAFT AND THE STAGE

## 4.1 The ship: M.S.V. CALIPER

Same hull as the Mote — an industrial survey hovercraft, 1.5 world units, `buildMote` and `drawMote` unchanged. Same four views, three renamed:

| View | InnerCosmos | MATHCOSMOS |
|---|---|---|
| 0 | BRIDGE | **HELM** — forward through the corridor, the TRACE overhead |
| 1 | CHASE | **CHASE** — external, swinging ±1.9 side, ~1 above |
| 2 | ENGINEERING (drive core) | **THE CORE** — unchanged, the drive is the drive |
| 3 | OBSERVATION | **THE MEASURING DECK** — the view that looks *sideways at the instruments*, used at every flagship stop |

Three fittings carry the whole series, and each maps onto machinery that already exists:

- **The jaws.** Two arms ending in clamps. `armStops` already drives arms reaching out at chosen rail positions; here they clamp two points and the strut between them is drawn. That strut is the secant (II-4), the chord of an arc-length polygon (III-8), the rod-and-needle pair of the Fundamental Theorem (III-4), the two tangent needles of the partials (V-2). **The jaw span is the cut, and the cut is the ladder rung.** One mechanism, one meaning, all series.
- **The belly drum.** A probe that reads the field: the gradient compass (V-4), the six-face flux box (VI-3), the paddle wheel (VI-4). The measurement is a thing the ship physically does, in front of you, at arm's length in stereo. This is what makes divergence and curl *felt* rather than defined.
- **The stern emitter.** Lays the wake (Tour III). The only genuinely new piece of geometry, and it is one `DynMesh`.

The ship's length is the resolution. When the cut halves, **the ship halves**, and the world inflates to compensate so the apparent scale is steady. This is the `inflate` mechanism used exactly as designed — the world scaled about the ship, never a camera zoom, never a change to the 58° frustum.

## 4.2 The crew

Unchanged in role, voice and stereo placement. `CrewVoices.Role` needs no edit; only the HUD label of `SCIENCE` might move from **DOC** to **THEORY** (a one-string change; "Doc" also reads perfectly well for a mathematician and I would probably leave it).

- **NAVIGATION (HELM)** — flies, counts the rings out loud, reports what the instruments read. Never explains. Her job is to make the corridor a real place: "Ring twelve coming up." "Both jaws on the trace." "Bar hasn't moved."
- **SCIENCE (DOC)** — teaches by pointing at what is already happening. Constrained by the house rule: never states a result the viewer cannot see being measured in the same breath. When something is a metaphor, says so (IV-11: "this is a projection, not an analogy"; V-4: "the arrow is in the ground, not on the surface").
- **ENGINEERING** — asks the question the viewer is actually thinking, and is *right to ask it*. His best lines in this series: "You're telling me the tilt of the floor is the height of the ceiling. Those are two different measurements in two different places." (III-4) · "So that one stops too, right?" about the harmonic tower, and being wrong (IV-2) · "Bar should be all over the place. It hasn't moved. Doc, that's wrong." (VI-8) · "So we're short a corner. Where do I get a corner?" (I-3).

He is never made to look stupid, he is answered, and twice per series he is right and Doc concedes.

Line discipline inherited unchanged: 20–45 s apart, 200–400 characters, pre-rendered Fish Audio with captions, emotion tags in square brackets as in the existing script JSON. **House rules that carry over:** never say "decade" for a factor of ten; never recite a formula; every stop's teaching must be sayable by the viewer in one sentence.

## 4.3 The passage — and why it is not a gimmick

**The passage is the domain.**

That is the honest answer, and it is the reason this design works. InnerCosmos flies down the body's tubes because that is where the body's business happens. Mathematics' business happens over a domain — an interval, a region, a set of inputs — and the corridor is that set, flown along. Three consequences, all load-bearing:

1. **The along-axis is the independent variable.** In Tours I–IV it is x (or the angle, or t, or the term index n). In Tour V it is arc length along a path in the plane. In Tour VI it is time, or a flow line. The wall rings are the coordinate ruling — **you can count the rings going past and that is how you feel the variable move.** Re-spacing the rings is therefore a mathematical act, and it is used as one, three times: the log ruler (I-11), the sine's angular ruling (I-9), and substitution (III-10).

2. **The passage radius is the tolerance.** It is not decoration and it is not per-node aesthetics. It is how much room the mathematics currently leaves you. This is what makes four of the best stops in the series work without a word of explanation: the ε–δ throat (II-2) is the passage closing; the radius of convergence (IV-9) is the passage ending; the alternating series' error bound (IV-6) is the passage closing at exactly the rate of the next term; Gabriel's horn (III-9) is the passage narrowing forever while the total stops growing. In each case the *corridor is the theorem*, which is the strongest possible answer to "is the tube a gimmick".

3. **The roof is the function.** The persistent TRACE ribbon along the upper wall in Tours II–IV means the corridor is not a place the mathematics is displayed in — it is the region under the curve, and you are inside it. That is why the wake works, why signed area works (the roof passes under the floor), and why the solid of revolution works (you fly down the axis of the thing you spun).

For Tours V and VI the tube goes to **wall alpha 0.15–0.25** and becomes a faint guide-rail: the domain is now two- or three-dimensional and the honest picture is open country, not a pipe. That transparency is a per-node field and a `uAlpha` uniform that already exists.

## 4.4 Ambience families

Replacing `Amb`. Each drives: the drift particle colour, the synthesised sound bed, `flowSpeed`, and which "bodies" (the drifting objects) spawn.

| Family | Colour (r,g,b) | Flow | Bodies that spawn | Sound bed |
|---|---|---|---|---|
| `COUNT` | 0.80, 0.86, 1.00 | 1.4 | integer lattice beads, snapped to a 1-unit grid | discrete ticking, a metronome under everything |
| `PLANE` | 0.92, 0.90, 0.84 | 0.9 | chalk grains, unit tiles | still air, a room tone |
| `CURVE` | 1.00, 0.82, 0.50 | 2.0 | sample dots, tangent needles | a single held tone that bends with the roof height |
| `LIMIT` | 0.90, 0.94, 1.00 | 0.25 | dust converging inward toward the rail | near-silence, breath held, a rising hairline whine |
| `SUM` | 1.00, 0.74, 0.36 | 2.4 | thin slab discs (`blobAt` with sy ≈ 0.02) | a conveyor, a steady low pulse per slab |
| `INFINITE` | 0.78, 0.66, 1.00 | 1.1 | motes that **halve in size** as they pass | a harmonic ladder, each new partial adding a quieter overtone |
| `SURFACE` | 0.50, 0.92, 0.78 | 0.8 | contour flakes lying flat in level layers | wind over open ground |
| `FIELD` | 0.45, 0.85, 1.00 | 2.8 | unit arrows that align with the local field | moving air with direction, panned |
| `SOLVE` | 1.00, 0.62, 0.30 | 2.2 | probe beads trailing short threads | a current you are riding; the drive note drops out |
| `LOOKBACK` | 1.00, 0.85, 0.75 | 0.6 | none | the InnerCosmos look-back bed, unchanged |

`BodyField`'s kinds become `LATTICE`, `SAMPLE`, `SLAB`, `ARROW`, `NEEDLE`, `FLAKE`, `NONE`, with the spawn-probability table, respawn sizes and the `quality`-gated count machinery all kept verbatim.

## 4.5 The wall

`buildTunnel`'s organic `bump` goes to a **clean cylinder with a coordinate ruling**. The `veins` detail layer in the wall shader is replaced by a grid term: `smoothstep` on the fractional part of the along-coordinate (rings) and of the angular coordinate (longitudinal lines), gated on `uDetail` exactly as the vein layer was, so it costs the same and vanishes above quality 0. Ring spacing becomes a uniform so it can be re-ruled live (I-9, I-11, III-10, II-8). `uPulse` is repurposed from heartbeat to **emphasis**, driven by the narration — the wall brightens on a beat when the crew lands a point. `vRipple` goes constant except in Tour VI, where it becomes a slow travelling wave.

**If any trig coefficient in that shader changes, `TIME_WRAP` must be recomputed** — the survey is explicit about this and it is the kind of bug that shows up as a snap every 63 seconds on the glasses and nowhere else.

## 4.6 No plates

The `PlateShader` path — the one textured-quad mechanism — is **deleted**, along with `loadPlates`, `PLATE_FILES`, `plateQuad`, `plateFrame`, `plateBuf`, `frameBuf`, `drawPlate`. This is a philosophy decision enforced in code: if a stop needs a formula on a wall, the stop is wrong and should be cut. Numbers that genuinely need to be read live in the 2D HUD readout under the ladder, where they belong.

## 4.7 Engine deltas required

Small, and all of them are named in the survey as things a port should do anyway:

1. `DynMesh(64)` is nowhere near enough. Add `dynCurve = DynMesh(1024)` (`GL_LINE_STRIP`, animated curves: conic sections, cutting-plane curves, phase loops, solution curves) and `dynRibbon = DynMesh(2048)` (`GL_TRIANGLES`, the wake sheet and the Stokes surface). **Assert the cap on write** — there is no bounds check and the survey flags this.
2. Fold `landmarkFade` into `drawLitModel` (this is a live bug in the original: the hinged valve leaflet pops in at full opacity).
3. Hoist `applyFrameRotation`'s two `FloatArray(16)` to fields, and return `frameAt` into a preallocated `Frame`. Both are called far more often in this series than in InnerCosmos.
4. Per-node `wallAlpha` (Tours V and VI) and per-node ring spacing.
5. Generalise `AirField.flow` from a scalar to a field callback. This single change turns the existing 96-streak, one-draw-call class into a genuine streamline visualiser, and it is the best asset in the engine for this port.
6. `Amb`, `Scene`, `BodyField.kind`, the `COL_*` palette (keep only `COL_LAMP`, `COL_HULL`, `COL_HULL_DARK`, `COL_PAD`, `COL_DRIVE`, `COL_DRIVE_DIM`, `COL_STATOR`, `COL_STEEL`, `COL_STEEL_BRIGHT`, `COL_GLASS`, `COL_COLD`, `COL_BAY`, `COL_WORLD`), and the 31 `drawXxx` scene functions all go. Delete `drawSentinel`'s pursuit AI, `sentinelIdx`, the collision nudge, `LYSIS_PERIOD`, `lysisClock`, `triggerLysis`, and the heartbeat clock — but **keep `wallPulse`'s plumbing** and re-drive it.
7. Everything else — the rail, `frameAt`, `blobAt`/`strutAt`/`drawLinesAt`, the tube, the drift field, the four cameras, the sway low-pass, `inflate`, the quality gating, the thermal governor, the ladder arithmetic, the map inset, the crew audio — is untouched.

**Budget discipline is unchanged and non-negotiable:** ≤ 30 draw calls per scene, ≤ 150 per eye, `frameDivider ≥ 2`, a `quality` branch on every loop over more than ~6 instances, no allocation in a draw path, no VBO outside `onSurfaceCreated`/`applyMap`, every `glDepthMask(false)` and `lineWidth(n)` restored.

---

# 5. THREE SAMPLE EXCHANGES

## 5.1 Early algebra — Tour I, stop 3: THE COMPLETED SQUARE

**NAVIGATION** — [steady] Holding at the corner, both jaws on the long edge. The plate ahead of us is x by x, Doc — a square whose side we don't know yet. There's a strip running down its right side, b wide, and I can split that strip and swing the lower half round on the corner. Say when.

**SCIENCE** — [warm] Swing it. — There. Now stop looking at the algebra you were taught and look at what the shape wants to be. A square of side x, and two strips half of b wide, laid along two of its sides. That is a bigger square with one corner bitten out, and the bite is exactly half-of-b by half-of-b.

**ENGINEERING** — [flat] So we're short a corner. That's what's wrong with it. It's nearly a square and it's got a hole in it the size of my fist. Where am I supposed to get a corner from, Doc? We can't just invent material and call the sum unchanged.

**SCIENCE** — [gentle] You can, if you're honest about the debt. Drop a square of side half-of-b into the hole — there, it's seated, and the whole thing is now a perfect square of side x plus half-of-b. You added b-squared-over-four to do it, so you owe it back. Subtract it again. Nothing has changed except that the shape is now something you can take the root of.

**ENGINEERING** — [dry] And that's the quadratic formula. The thing I was made to memorise at fifteen. It's a missing corner and a piece of wood the right size to fill it.

**SCIENCE** — [warm] That's the whole of it. Every quadratic anyone has ever solved is this piece of carpentry. The formula is just the carpentry written down so you don't have to build it each time. Look at the corner square while we pass it — it's a different colour, because it's the part you had to borrow.

**NAVIGATION** — [warm] Corner's seated. Square's closed. Ring twelve coming up on the port side.

## 5.2 The heart of calculus — Tour III, stop 4: THE SWEEP AND THE HEIGHT

**NAVIGATION** — [steady] Wake emitter's open and everything behind us is filled — floor to the trace, all the way back to the gate at a. Ahead of us there's nothing. Doc, the ribbon down on the deck started climbing about eleven rings ago and I'd like to know what it thinks it's doing.

**SCIENCE** — [wonder] That ribbon is the total so far. Its height at any ring is exactly how much light is behind us — no more, no less. And here's the thing I want you to see rather than take from me: the steeper that ribbon climbs, the taller the roof is above it right at that moment.

**ENGINEERING** — [flat] Hold on. Say that again, because I think you've made a mistake. You're telling me the tilt of the floor is the height of the ceiling. Those are two different measurements, in two different places, in two different units. Why in the world would they be the same number?

**SCIENCE** — [gentle] Because of what filling means. Go one sliver of rail forward. The wake gains one slab — as tall as the roof, as thin as the sliver. Gain over run: that is a tilt. So the roof's height *is* the rate the total grows. There isn't anything else it could be.

**NAVIGATION** — [steady] Arms out. Left jaw's holding a rod cut to the roof height. Right jaw's holding a needle set to the floor ribbon's tilt. — They're the same length. And they were the same length at the last ring. And the one before.

**ENGINEERING** — [quiet] Every time?

**SCIENCE** — [warm] Every time, all the way down this corridor and every corridor after it. That's the Fundamental Theorem of Calculus, and you just watched it happen instead of being told it. Adding a thing up and finding how steep it is are the same act, run in opposite directions.

## 5.3 The most advanced stop — Tour VI, stop 8: THE RIM

**NAVIGATION** — [steady] Through the rim and holding station. That wire loop is fixed — it does not move, whatever else does. Engineering, take the film out into a dome for me, slowly, and everyone watch the total bar on the measuring deck, not the surface.

**ENGINEERING** — [wary] Stretching. Dome's up past two units and still climbing, and I've got — call it four hundred paddle wheels turning on that skin now, all at different rates, half of them didn't exist ten seconds ago. That bar should be jumping all over the place. — It hasn't moved. Not a hair. Doc, that's wrong.

**SCIENCE** — [warm] It's right, and it's the best thing in this whole passage. Don't stop — pull it the other way. Flatten it to a disc. Now bag it out into a long sock, right down past the ship. Every single wheel on that surface has changed. The total has not. And the one thing you have not touched is the rim.

**ENGINEERING** — [flat] So the inside doesn't matter. Everything I just measured was a waste of time.

**SCIENCE** — [gentle] The inside matters enormously — it's simply that all of it cancels. Two neighbouring wheels share a blade and push it in opposite directions, so that pair contributes nothing. That happens everywhere inside the surface. The only blades left with no partner to cancel against are the ones sitting on the rim.

**NAVIGATION** — [thoughtful] So I could survey that whole skin, wheel by wheel, for an hour. Or I could fly the rim once and read the push off the hull.

**SCIENCE** — [warm] Fly the rim. That is Stokes' theorem, and it is the same sentence we heard on the accumulation run three tours ago: what happens inside a region is settled by what crosses its edge. Learn that sentence and you have most of advanced calculus in your pocket.

---

# 6. THE HARD PARTS

## 6.1 THE HANDOFF — the chain rule (Tour II, stop 8)

**Why I'm unsure.** My first version of this stop was a gear train — two coupled wheels, rates multiply. It is a good picture and it is *wrong for this series*, because a gear train is a metaphor for the chain rule, not the chain rule itself. The house rule says if a visual is a metaphor we say so, and a stop whose entire content is an admitted metaphor has no business in a tour built on "see the theorem." The deeper problem is that composition is genuinely about *reparametrisation*, and reparametrisation is the one thing that is hard to see, because a reparametrised curve looks exactly like the curve.

**What I'd do about it.** Make the ship the outer function. Past a gate, the corridor's own ruling changes so the ship covers two rings of u for every one ring of x. The roof shape as a function of u is untouched — it is literally the same mesh — but the rate the roof rises *as the ship experiences it* doubles, and the floor's derivative ribbon jumps to double height at exactly that gate. Two speedometer bars on the bow, "rings of u per ring of x" and "rise per ring of u", and a third bar showing their product, which tracks the floor ribbon continuously. The theorem is then a fact about the ship's instruments, not an analogy about gears.

**If that still doesn't read in the first build**, my fallback is to cut the stop entirely and teach the chain rule as two lines of dialogue at THE HUNT (Newton's method), where it is doing real work. I would rather have twelve stops that all land than thirteen with one that is a diagram.

## 6.2 THE EDGE OF THE WORLD — radius of convergence (Tour IV, stop 9)

**Why I'm unsure.** This is the stop where "see the theorem" is most at risk of becoming "be told the theorem while pretty things happen." For 1/(1−x) it is fine — there is a genuine pole at x = 1 and I can put a wall of light there. But the honest, important, *interesting* case is 1/(1+x²), which is perfectly smooth and finite along the entire real line and whose series still dies at |x| = 1 for a reason that is nowhere on the corridor. If I only show the easy case I have taught a falsehood by omission ("the series stops where the function blows up"). If I show the hard case I have a stop whose explanation is invisible, which is exactly the failure mode this series exists to avoid.

**What I'd do about it.** Show both, in that order, and use the hard case as the reveal rather than hiding from it. Fly 1/(1−x): wall of light, everyone satisfied, Engineering nodding. Then swap to 1/(1+x²): calm corridor, nothing wrong, and the ribbons tear apart anyway at exactly the same distance. Let Engineering ask why, and let Doc say plainly that the reason is not on this road. Then the ship **leaves the real axis** — a lateral excursion into the imaginary direction, with the rail itself unchanged so `frameAt` never degenerates — and there, one unit off to the side, are two bright poles at ±i, with a sphere of light around the anchor whose radius reaches exactly to them. The thing that limits you was in a direction you weren't looking. That is a better lesson than the one I was originally trying to teach, and Tour I stop 10 exists partly to make the viewer ready for it.

**Residual risk:** this is the most conceptually demanding two minutes in the series and it sits at the end of a tour, when attention is lowest. I would consider moving it earlier in Tour IV and following it with the sine series as a cool-down.

## 6.3 THE SPREADING — a partial differential equation (Tour VI, stop 12)

**Why I'm unsure.** Three separate problems. First, it is two ideas in one stop (heat and wave), which breaks my own rule. Second, it needs a *live simulation* — a grid stepping forward in time — and the survey is unambiguous that per-frame CPU geometry storms are what cooks the glasses; a 24 × 24 grid re-uploaded through a client-side `FloatBuffer` every frame is roughly 57 KB per frame per eye, and while that is probably survivable it is exactly the kind of "probably survivable" that shows up as a thermal step-down twenty minutes into a ride. Third, and worst, a melting terrain is a *pretty* visual that might not actually teach the neighbour rule — the viewer sees smoothing and concludes "things smooth out," which they already knew.

**What I'd do about it.** Three fixes, in order of confidence. (a) **Precompute.** Solve both equations offline at build time, store 24 keyframe states as 24 static `LineMesh` VBOs, and cycle them. Zero per-frame CPU, zero upload, deterministic across launches, and it obeys the engine's own discipline about static geometry. Memory cost is trivial at 24 × 24 grid resolution. (b) **Teach the rule, not the result.** Before the whole surface moves, freeze on **one point and its four neighbours**, drawn as five beads with struts; show the centre bead being pulled toward the average height of the four, once, slowly, by the ship's own arm. Then release the whole grid. The viewer has seen the rule applied by hand before seeing it applied everywhere, which is the difference between understanding and watching. (c) **Split the stop if there's room** — heat gets the melt, the wave gets the splitting bump, and if Tour VI is one stop over budget I cut THE PULL HOME's second half instead, since stability is already half-taught by the slope field.

**Honourable mention:** Green's theorem (VI-6) hinges entirely on whether the internal-edge cancellation animation reads as *cancellation* rather than as *things switching off*. If it doesn't, the fix is to draw the two opposed blades of each shared edge as two arrows meeting nose-to-nose and audibly click as they annihilate in pairs — and if that still doesn't land, to zoom in on four wheels only, cancel their shared edges by hand, and then pull back and let the viewer extrapolate.