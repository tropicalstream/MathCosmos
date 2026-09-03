# MathCosmos

**A railed stereoscopic tour of mathematics for the RayNeo X3 Pro AR glasses.**
Board the *M.S.V. Caliper*, a survey craft with a pair of measuring jaws, and ride a fixed rail
through the mathematics itself, in real 3D across both lenses, with a talking three-person crew.

The sister project of **InnerCosmos** (a tour of the human body) and **SpaceX3Tour** (Earth to
Pluto). Same hardware, same engine, same crew idea, same Fish Audio voices. InnerCosmos went
inward into the body; this one goes into the abstraction.

> ⚠️ **ALPHA.** All six tours are complete: 76 stops, 76 landmark scenes, 843 spoken lines, flown
> and verified on the glasses at 30 fps and 28 °C.

---

## 🗺 The series

Six tours, about three hours end to end. Every tour ends with the same move — **THE VIEW FROM
OUTSIDE** — and the last stop of the last tour puts the unit rod from the very first stop back in
the craft's jaws.

| # | Tour | Pitch | Length |
| --- | --- | --- | --- |
| **I** | **THE SOLID GROUND** | Algebra is carpentry: every identity you were made to memorise is a shape being rearranged in front of you. | 25 min · 13 stops |
| **II** | **THE APPROACH** | The corridor's roof is a curve, and the craft's two jaws measure how steep it is — then close until they cannot close further. | 13 stops |
| **III** | **THE ACCUMULATION** | Accumulation is the craft's own wake; the Fundamental Theorem is the moment two instruments read the same number and keep reading it. | 12 stops |
| **IV** | **THE INFINITE** | A tower that stops growing, a tower that never does, and a curve you can force to agree with another one term at a time. | 12 stops |
| **V** | **THE OPEN COUNTRY** | The tube goes ghost and you fly a canyon through a landscape whose height is a function of where you stand. | 13 stops |
| **VI** | **THE FIELD AND THE FLOW** | Stop steering. The field carries you, the probes measure what it does to a box and to a wheel, and the last theorem says the inside is decided by the rim. | 13 stops |

Every stop of every tour is specified in [DESIGN.md](DESIGN.md): the sentence the viewer should
walk away saying, the mathematics, and the scene concretely enough to build.

## 📐 Tour I — THE SOLID GROUND, stop by stop

| # | Stop | What you walk away saying | What you see |
| --- | --- | --- | --- |
| 1 | **The Unit** | A number is a length I can pick up and carry | a glowing rod held in the jaws; copies of it laid end to end down the floor as a ruler |
| 2 | **The Rectangle** | Multiplying is making a rectangle, and splitting it is the distributive law | a ruled plate cut in two, the craft flying through the cut |
| 3 | **The Completed Square** | Every quadratic is a square with a corner missing | a strip swings round to the top; the empty corner arrives in its own colour and stays that colour |
| 4 | **The Difference of Squares** | a² − b² is a frame, and a frame unrolls into a strip | a square lifted bodily out of a corner; the L cut once and turned into a plain rectangle |
| 5 | **The Right Angle** | The square on the long side is exactly the other two poured in | nine grains and sixteen grains stream into a third square and fill it exactly |
| 6 | **The Twins** | Doubling a thing quadruples its skin and multiplies its bulk eightfold | the same solid at 1, 2, 3, with 1/4/9 tiles and 1/8/27 cubes counted out — the stack visibly three deep |
| 7 | **The Sliced Cone** | Circle, ellipse, parabola and hyperbola are one cone cut at four angles | a double cone of light and a turning plane, the section solved and drawn live where they meet |
| 8 | **The Focus** | Every point on a parabola is the same distance from one point and one line | twelve beads, each with two struts of equal length; drag one off the curve and they go unequal and red |
| 9 | **The Wheel** | Sine and cosine are just the shadow of a point going round a circle | a bead on a ring, its height left behind down the corridor as the wave unrolls |
| 10 | **The Turn** | Multiplying by i is a quarter turn, and nothing else | an arrow that snaps ninety degrees each time it is tapped; four taps and it is home |
| 11 | **The Stretched Ruler** | A log scale turns multiplying into sliding | the corridor rings re-space themselves ten-to-one; two rulers slide and land on the product |
| 12 | **The Doubling** | Anything growing in proportion to itself beats anything growing by adding | two racers; the adder leads for four rings, then the world has to keep zooming out |
| 13 | **The View from Outside** | All of that was one flat picture; I was inside it | the walls dissolve and the tour hangs in the dark as one thread with thirteen beads |

## 🎮 Controls (right-arm touchpad)

| Action | What it does |
| --- | --- |
| **Tap** (title card) | Board — opens the tour menu |
| **Tap** (in flight) | Switch camera view: Helm · Chase · The Core · The Measuring Deck |
| **Swipe** (eye-line card) | Raise or lower where the material sits |
| **Double tap** | Pause and reopen the stop menu |
| **Swipe forward** | Cycle the audio mix |
| **Swipe back** | Show / hide the telemetry HUD |

Head tracking (IMU) lets you look around freely; the rail is never steered.

**Setting your eye line.** On the way in, once, the craft parks at a real landmark and asks you to
sit the way you mean to sit and swipe until the figure is comfortably in front of you rather than
above or below. Two people wearing the same glasses do not look at the same place — the frames sit
differently on each face and people hold their heads differently — and over half an hour that is
the difference between comfortable and a sore neck. The setting is remembered. To set it again:

```bash
adb -s A06B4A96A733283 shell am broadcast -a com.rayneo.mathcosmos.CONTROL --ez calibrate true
```

At a stop, the three framed views are three vantages on the same subject, so tapping still changes
the view without ever losing what is being presented; **The Core** is the exception, because being
inside the hull is the point of it.

## 🎓 How it teaches

The tours are written as tutoring, not as narration, and the app is built to support that. None of
it is named aloud in the dialogue — a tutor does these things, they do not announce them.

**The view settles, and the room goes quiet.** While the craft is alongside a landmark the hull
stops swaying, the ambient bed ducks to a whisper, and the camera stops flying: it eases out of
whatever the view mode was doing into a fixed, composed three-quarter view of the thing being
presented — far enough back to hold the whole figure, aimed a little above it so it sits clear of
the telemetry at the top of the eye and the caption box at the bottom. That pose is anchored to the
STOP, not to the craft, and it latches, so it does not creep while the craft drifts on through it.
On a head-worn display there is nowhere to look away to; anything moving or sounding that is not
the lesson is competing for the attention the lesson needs.

In the code: `stillness` in the renderer damps the sway and the beat shake and drives a `calmClock`
the camera motions run on; `settleOnSubject` blends to the composed pose, which each scene declares
through `focusSide` / `focusUp` / `focusAhead` / `focusRadius`; `MathAudioEngine.focus` ducks the
bed fast and returns it slowly. The hold drift inherited from the sibling app was halved, and the
counting ambience lost its metronome — a random tick between 1.4 and 2.1 kHz that read as a chirp
over the crew from the very first stop.

**You answer before you are told.** Every stop opens with a question about the *previous* stop that
the viewer answers in their own head, with four to five seconds of real silence to do it in —
retrieval, not review, because being asked to produce an answer is far stronger than being shown
one again. Before each demonstration's key moment someone asks the viewer to commit to a guess, and
then six to eight seconds of silence run while the demonstration plays. Guessing wrong first and
being corrected beats being told first. Across the six tours that is 86 prediction pauses and 80
retrieval pauses, bought with `build_script.py`'s per-line `gapMs`.

> Those silences are load-bearing, so `TourDirector.SILENCE_MS` and `PROTECT_NEXT_MS` are 15 s and
> 9 s. At the sibling app's 10 s and 4 s the silence-filling banter fired straight into every
> scripted pause. Any new scripted gap must stay inside the protect window.

**Nobody talks over the moment itself.** The corner seating, the grains landing, the two arrows
fusing, the bar not moving: the setup lands before, the payoff comes after, and the moment plays in
silence. Every reveal is signalled first — "keep your eye on the red sliver", "watch the far corner,
not the middle" — because an unsignalled reveal on a 640-pixel eye is a missed one.

**Engineering is allowed to be wrong.** His mistaken guesses are the most valuable lines in the
scripts, and they are left standing for a beat before the picture disagrees with him. Twice a tour
he is right and Doc concedes outright.

**One idea per stop, and the stop says when it is done.** Each closes with the single sentence the
viewer should be able to say, and invites them to say it. Those same sentences are attached to the
stops as `TourNode.takeaway` and shown in the stop menu for whichever row is highlighted — so the
menu is also a revision index. Run down the tour, try to recall each stop before you read it, and
jump straight back to any you cannot.

**Concrete before symbol, and backwards connections by sight.** The tile count comes before the
notation, and the notation is named once. Callbacks to earlier tours name what the thing looked
like — "the square with the corner missing", "the wheel", "the rod" — never a stop number.

## 🏗 How it is built

The engine is InnerCosmos's, ported and then reworked where the subject changed. What is new:

| File | What it is |
| --- | --- |
| `GlyphBoard.kt` | **Mathematical notation in 3D.** There is no text rendering in GL ES 2.0, so a label is drawn once with an Android Canvas into a bitmap, cached by its string, and drawn as one additively-blended billboard quad. Understands `x^2`, `e^{-x}`, `a_1`; `∫ Σ √ π ∂ ∇` are literal. This is the capability the sibling app never needed and this one cannot exist without. |
| `MathMesh.kt` | The five things a maths landmark is made of — a plotted curve, a wireframe surface, an arrow, a box, a ruled axis — as allocation-free builders that fill a caller's buffer. |
| `SceneKit.kt` | The narrow contract a landmark scene is allowed to use. Scenes never touch GL and never allocate. |
| `SceneParts.kt` | Panes, unit rulings, stage frames and easing: the carpentry every algebra stop is cut from. |
| `Scene*.kt` | One file per landmark. Stateless objects, registered in `MathScenes.kt`. |
| `app/tools/build_script.py` | Turns a written tour (`script_src/tourN.json`) into the timed JSON the app plays — cue times, keyframes, segments, stable clip ids. `--measure` re-times it from the audio that actually rendered. |

Reworked from the sibling: the ambience families (`Amb`) are now moods of the subject rather than
places; the drifting particles are lattice beads, sample points, slabs and arrows; the HUD ladder
reads the cut rather than metres; the wall can dissolve per stop (`TourNode.wallAlpha`); the inset
is the tour, not a human figure; `TourDirector` no longer assumes thirteen stops; and `CrewVoices`
now shows captions for a line it cannot say, so the ride is watchable before any audio exists.

### Build and run

```bash
cd ~/Projects/MathCosmos && ./gradlew :app:assembleDebug
```

```bash
adb -s A06B4A96A733283 install -r app/build/outputs/apk/debug/MathCosmos-debug.apk
```

The X3 Pro is usually not the only device attached, so always pass `-s`.

### Editing a tour

```bash
python3 app/tools/build_script.py script_src/tour1.json app/src/main/assets/tour1_script.json && python3 app/tools/validate_script.py app/src/main/assets/tour1_script.json
```

`validate_script.py` checks the script against what the app can actually play: sfx names, view
indices, stop count, cue ordering, unique clip ids and unique dialogue, and which voices are
still unrendered. Run it after every script change.

### Demo control over adb

```bash
adb -s A06B4A96A733283 shell am broadcast -a com.rayneo.mathcosmos.CONTROL --ei segment 2
```

Also accepts `--ei tour N`, `--ei view 0..3`, `--ez menu true`, `--ez board true`, `--ez hud false`,
`--ez debug true`, `--ei quality 0..2`, `--ez recenter true`. Launch with `--ez mono true` for a
single flat view when capturing on a phone or emulator.

## 🔊 Voices

The crew are pre-rendered Fish Audio clips, all 879 of them, rendered on the **free developer
tier** — pass `s2.1-pro-free` as the model and the same S2.1 Pro backbone that powers the paid
tier renders at no cost, with the same three reference voices. On identical text the free and paid
renders came back within 4% of each other in length, so nothing about the crew changes.

It is a promotion with an end date, extended twice already. If it stops answering, drop the model
back to `s2.1-pro` and pay. Its Fair Use terms allow requests to be retained and used to improve
the model, which is a fair trade for an app's own dialogue and not somewhere to put anything
confidential.

**The X3 Pro has no speech engine bound**, so a line with no clip is shown as a caption and heard
as nothing — deliberate, so a tour is watchable before its audio exists.

Clip ids carry a digest of the whole line, so editing any part of a line changes its id and forces
a fresh render. Before that, revising the back half of a line left the id alone and the generator
skipped it: thirty-eight clips shipped saying the sentence they had replaced. See
[`app/tools/README.txt`](app/tools/README.txt) for the exact commands, and always follow a render
with `build_script.py --measure`, which re-times the script from the real audio so no line is
talked over by the one behind it.

## 🙏 Credit

Built on the InnerCosmos engine, which was built on SpaceX3Tour's. The mathematics is all several
thousand years old; the only thing this project adds is a body for it.
