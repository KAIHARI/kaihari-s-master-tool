# Tuning the play stage

Twenty-seven numbers, live, on the device they will be judged on — and a JSON
export that comes back here to become the default.

**Long-press the life-point number** on the play stage. It is the one element in
that bar that is pure output, which is what makes it the only place a hidden
gesture can go without taking a real one away. Esc closes it, one layer at a
time, below the guide.

It is in the **shipped** build, not a debug one, and that is necessity rather
than preference: a debug APK cannot install over the signed one, so a
debug-only panel is a panel that can never be opened on the tablet.

---

## The round trip

1. Long-press the life points. Drag. The picture moves under your thumb.
2. **Copy JSON**, or **Show JSON** and screenshot it if the clipboard is being
   awkward about leaving the tablet.
3. Paste it back into a conversation. Every field maps to one named constant,
   so baking it in is mechanical rather than interpretive.
4. `tools/shoot.sh --tune=that.json` replays it headlessly, so the change can be
   diffed with `tools/compare.py` before a release is spent on it.

A tuning whose `camera` block has moved **overrides the shot's seat**: the
studio skips the seat keypress rather than pressing `1`/`2`/`3` over the pose it
was just handed. Without that, two tunings that differ only in the camera come
back bit-identical, and the harness looks like it is ignoring the file. Shot
names still carry a seat because they still choose the scene and the light; the
seat part is simply the pose the tuning replaced.

The tuning **persists**. Set it, close the app, come back tomorrow and judge it
again. That is deliberate: a look you like at 1am is not always a look you like
at noon, and the whole reason the panel exists is to stop that question costing
a version number each time. Version numbers are spent forever.

`changed` at the top of the export names only what you moved. Nothing else in
the document is a decision — it is there so the file is a complete, valid set
of defaults rather than four sliders out of context.

---

## What is on the panel

Everything is in the unit of the constant it replaces: degrees, multiples of
the shipped value (`x`), or fractions of a card (`cards`). Never solved pixels
— a number in pixels means a different thing on a phone.

| | what it does | shipped |
|---|---|---|
| `camera.yawDegrees` | round the table | 0° |
| `camera.pitchDegrees` | how far it is laid back | 21° |
| `camera.distance` | how far back you sit, in stage heights. **The perspective dial** | 1.45 |
| `camera.lens` | **focal length** — pure magnification, perspective untouched | 1.0 |
| `camera.clearance` | **how close you are allowed to sit.** The floor under `distance` | 0.68 |
| `focus.strength` | the defocus falloff. **Off by default** | 0 |
| `focus.depth` | where it is sharp, across the board's depth | 0 |
| `focus.fNumber` | how fast it falls away | f/8 |
| `hand.leanDegrees` | how far a hand card stands up | −24° |
| `hand.liftFactor` | a multiplier on the lift the lean solves for | 1.0 |
| `hand.stepFraction` | the gap between hand cards | 0.62 |
| `hand.liftRatio` | how far a card raised to be read comes up | 1.6 |
| `cards.carryLift` | how far a carried card floats | 0.55 |
| `cards.fanLiftRatio` | how far a searched pile floats | 0.85 |
| `cards.peekLift` | how far a held card comes off the table | 1.35 |
| `cards.peekScale` | and how much bigger it gets | 1.9 |
| `room.deskDepth` | how far the desk reaches toward you — **and pushes the wall back by the same amount** | 0.9 |
| `room.deskSpan` | how wide the desk is, as a share of the screen | 1.6 |
| `room.wallBack` | how far past the mat the wall stands, before the depth pushes it | 0.5 |
| `room.windowSpan` | how wide the hole in the wall is | 3.4 |
| `room.windowAt` | where its centre is, across the mat: 0 is the left edge | 0.12 |
| `room.windowSill` | how high the bottom of it is, in card *heights* | 0.24 |
| `room.windowHead` | and the top | 2.05 |
| `room.lampOut` | how far right of the mat the lamp stands. Negative is the other side | 1.15 |
| `room.lampAlong` | and how far down it: 0 is level with the far edge | 0.26 |
| `room.lampScale` | how stout it is — every radius at once | 1.0 |
| `room.lampMast` | the pole: where the top of the shade is drawn | 2.2 |

`camera` is the seat the stage **opens at** — not the live camera, which the
rig owns and which orbiting writes to. **Read camera** is how a pose you
orbited to gets into the panel.

---

## The two camera dials are a position and a lens, and they do different jobs

This is the pair a photographer expects, and it took three goes to get there.

**`distance` is where you stand.** Stepping back weakens the perspective *and*
makes the table smaller, because that is what walking backwards does. This is
the only dial that changes the perspective, and it changes it by exactly as much
as walking does: the keystone across the table goes as `1/distance`.

**`lens` is magnification, and nothing else.** It multiplies `cameraDistance`
and `zoom` by the same amount, so the angle the table subtends —
`zoom · extent / cameraDistance` — does not move at all. The board is drawn
bigger; the perspective across it is bit-identical. `CameraLensTest`
(`theLensDoesNotTouchThePerspectiveAtAll`,
`whereYouStandIsWhatChangesThePerspective`) pins both halves of that.

| lens | 35mm equivalent | what the picture does |
|---|---|---|
| 0.60 | ~20mm | the same room, smaller in the frame |
| 1.00 | ~33mm | shipped |
| 1.50 | ~49mm | half again as big, same perspective |
| 2.00 | ~65mm | twice as big, same perspective |

The millimetres are a function of the **lens alone** and are honest only because
of that. The proportionality is exact — `f = 18/tan(halfFov)` and the field of
view goes as `atan(1/lens)`, which
`CameraLensTest.theMillimetresBesideTheDialAreTheOnesTheProjectionHas` pins. The
*calibration* is a measurement: 33mm is this projection at a lens of one, so
moving `HOME_DISTANCE` means re-measuring `StageTuner.HOME_MM`. That test is the
tripwire.

### Two goes at the same mistake, and the second one lasted five releases

The mistake both times was putting something that is not a lens on the **focal
length**. `cameraDistance` *is* the focal length, in pixels: `StagePlane.project`
divides by `cameraDistance − depth`, and Compose's `graphicsLayer` does the
identical thing. Whatever is on it sets the field of view.

**v1.2.38 put the `lens` there alone.** Framing pinned, perspective moving —
which is a **dolly zoom**, a real control and a good one and not a focal length.
Both dials then changed the same thing, so tuning one appeared to undo the other.
kai's word for it was that it reset. Worth rebuilding one day as its own row,
under its own name.

**The fix put the lens on both terms and left `distance` on the focal length,
where it had been since there was a camera.** So the *other* dial had the same
fault, and nobody looked at it for five releases because the dial that had been
complained about was now correct. Measured on a 1600-wide stage:

| distance | field of view | 35mm equivalent |
|---|---|---|
| 6.0 (back of the envelope) | 15° | ~130mm |
| 1.45 (the Table seat) | 58° | ~33mm |
| 0.5 | 116° | ~9mm |

Every dolly was a dolly zoom. So was every *tilt*, because the pitch moves
`CameraEnvelope.minDistanceAt`, which moved the distance, which moved this — so
a one-finger drag down the felt, the plainest gesture on the stage, quietly
zoomed. And the keystone swung as `1/distance²` rather than `1/distance`, which
is twice as fast in log terms as walking. kai's report was that the perspective
shifted a lot when moving the camera around, and that it did not behave like a
real camera. Both were literally true.

`planeFor` now reads `cameraDistance = lens · HOME_DISTANCE · governing`, with
no `distance` term at all. `CameraLensTest.theFieldOfViewDoesNotMoveWhenYouWalkTowardTheTable`
and `theKeystoneGoesAsTheDistanceAndNotItsSquare` are the two tripwires, and the
second one is a measurement rather than a tolerance: doubling the distance
divides the keystone by about 2.1, and the square law it replaced gives 5.

**What it cost.** One golden recording. At the home distance the corrected
projection is bit-identical to the old one, so `GoldenStageTest`'s `Home` and
`Turned` — both at 1.45 — did not move a digit; `Steep`, at 1.9, did, and had to
be re-recorded and nudged to 1.96 to clear the tie margin.
`CameraLensTest.atTheSeatEverythingWasTunedAtTheOldProjectionIsTheNewOne` is that
claim as arithmetic rather than as a diff.

**What it stopped costing.** `CameraEnvelope.minDistanceAt` collapses from a
square root to a line, because the distance had been on both sides of the
constraint. Every floor anybody can reach is lower for it — see below.

**What framing still costs.** Past a lens of `1.0` the board is magnified and can
run off the edges of the screen, exactly as a real zoom crops. Nothing catches
it, and that is deliberate.

---

## The camera has one limit left, and it is arithmetic

kai, twice. First: *"the slider can go below 1.4 but it doesn't show it visually
and won't let me get closer."* Then, after that was fixed and had not gone far
enough: *"there shouldn't be a limit I want complete freedom and control."*

There were three walls. Two were taste and are gone.

- **A flat `minDistance` of 0.8.** It sat in front of the solved floor and never
  let it speak — below about fifty degrees of pitch the solved answer was
  *under* it, so the wall anybody actually hit was a round number somebody
  chose. It is a twentieth now, and the floor is always the solved one.
- **`CameraFit`, run on every release.** You pinch in, let go, and the table
  slides away from you. It was written for a real problem — a turned table can
  walk its own corners off the screen — but a correction nobody asked for reads
  as the tool refusing, and it was inconsistent besides: the mouse wheel never
  went through the arbiter, so a desktop user could already dolly past where a
  finger was allowed to stop. It is on the **seat buttons** now
  (`StageCameraState.sitAt`), where being put back is what was asked for, and it
  is the way home from anywhere free flight can reach.
- **`camera.clearance`, and the floor it solves.** This one stays, because it is
  not taste. Past the lens plane `project` clamps rather than dividing by zero,
  and a clamp cannot be inverted — so `unproject` stops agreeing with `project`,
  and with it `flatten`, and with that every pile edge and airborne shadow.

`CameraEnvelope.minDistanceAt` solves that floor from the pitch, the clearance,
and the corner of the surface furthest from wherever the camera is **aimed**:

```
distance ≥ reach · sin(pitch) / (clearance · governing)
```

On a 2800×1607 stage, and the whole table is lower than it was:

| pitch | floor at 0.5 | at 0.9 (shipped) | at 0.99 (the knob's end) |
|---|---|---|---|
| 21° — the table seat | 0.72 | 0.40 | 0.36 |
| 34° — the seated one | 1.12 | 0.62 | 0.57 |
| 40° | 1.29 | **0.72** | 0.65 |
| 80° | 1.98 | 1.10 | 1.00 |

Every cell of that is `StageCameraTest.theWholeFloorTableIsWhatTheDocumentSaysItIs`,
because a table of numbers in prose with nothing under it is worth what the last
one was — it said "five to one" about the lamp for two releases and was measured
at 2.92.

That 40°/0.5 row is the old **1.37**, which is what kai measured as "one point
four": same clearance, same screen, and 1.29 now because the projection stopped
putting the distance on the lens. The shipped default moved from 0.68 to 0.9 in
the same change — freedom is what was asked for, and a knob three menus deep is
not an answer to "I want complete control".

The ends of `camera.clearance` are still not taste:

- **It cannot reach one**, for the reason above. 0.99 is the stop, a hundredth
  short of the wall rather than the 0.95 that was a margin of comfort in front
  of it.
- **It does not go below a half.** Tightening it pushes floors *out*, and there
  is nothing under a half anybody wants.

And the panel says so: the distance row prints `· held at 0.72` when the floor is
above the value you dialled, so the dead zone has a reason attached rather than
looking like a broken slider.

**Pitch opens to 0..80 and the far end to 6.** Card text keystones badly past
about sixty degrees, which is why the ceiling used to be fifty-eight; that is now
something you can see happening and pull back from rather than something the tool
refuses on your behalf. Ninety is the one angle that genuinely cannot be drawn.
At eighty the table's **horizon** is on the glass — a quarter of the way down
from the top — and a finger above it is pointing at no table at all, so
`StagePlane.belowHorizon` holds it down to the last row that is looking at one.
Without that, `unprojectAt`'s guard hands back the camera's own target and a tap
on the wall grabs whatever is in the middle of the board.

**`StagePlane.SAFE_DEPTH` went from 0.5 to 0.9** when the clearance first became
a preference, and it had to. It is the guard that culls a face close enough to
the lens to fly off to a few hundred thousand pixels — and it had been the same
number as the clearance by coincidence. Letting the camera closer without moving
it would have culled the **desk**, which by construction reaches further toward
you than the mat does. Nine tenths is what that guard was always about: `MIN_GAP`
and the number one, not what is comfortable to look at.

---

## `camera.panX` / `camera.panY`: what the camera is aimed at

New, and the reason `CameraPose`'s own KDoc spent a paragraph refusing one is
worth reading, because it was half right.

> There is no pan — the camera always looks at the middle of the table — because
> a movable target means the vanishing point stops being the centre of the layer,
> and then the mat's `graphicsLayer` needs a `transformOrigin` and
> `StagePlane.unproject` needs an off-axis inverse.

The `transformOrigin` is real and is two lines. The off-axis inverse was a
description of a **different pan**: sliding the finished picture sideways does
move the vanishing point off the middle of the glass. Moving what the camera
*looks at* does not — the divide stays centred on the pivot and the pivot stays
drawn in the middle — so `unprojectAt` is unchanged but for which point it adds
back at the end, and it stays closed form. That matters more than it sounds:
`flatten` is the projection composed with its own inverse, and every card
thickness, pile edge and airborne shadow on the stage is drawn through it.

Both knobs are multiples of the governing dimension, like `distance`, so a saved
pose frames the same thing on a tablet and on a monitor. Aiming away from the
middle **holds the distance out a little**, because the corner furthest from the
target is further off than the half-diagonal — `minDistanceAt` takes the pan for
exactly that reason, and so does the `· held at` readout.

The gesture is **two fingers on the mat**; on a pointer it is a middle-drag, or
alt and drag for the great many trackpads with no middle button. One finger still
orbits. The leash is two stage-heights in each direction, which is far enough
that the board is long gone; a seat button is the way back.

---

## Defocus is not depth of field, and calling it that would be a lie

There is no blur on this stage. `BlurEffect` is API 31 against a `minSdk` of 26
and degrades to a **silent no-op** below it, and a `renderEffect` per card is
the one shape of change `docs/PHOTOREAL.md` measured and called fatal.

It is also worth far less than it sounds. Measured on this board at 1600×1000:

| seat | board's depth span | circle of confusion at f/8 |
|---|---|---|
| Overhead | 55 mat px | 0 px — the whole board is in focus |
| Table | 251 mat px | 1.6 px on a 102 px card |
| Seated | 425 mat px | 2.7 px |

A render-pass architecture, an API gate and a fallback path, to move under
three pixels.

What defocus destroys first is **micro-contrast**, long before sharpness, and
at two or three pixels that is the entire visible effect. So `Defocus` returns a
contrast falloff — one more rounded rectangle over a card the renderer already
draws two or three of. Zero layers, zero render passes, reaches API 26.

It is mid grey, not black: a wash of mid grey pulls the whites down and the
blacks *up* by the same alpha, so nothing gets darker on average. That is what
makes it atmosphere rather than a dimmer, and it is the argument `docs/DESIGN.md`
§7 accepts for letting it argue with "the highlight moves; the brightness does
not".

**What it looks like:** the far half of the board loses its bite, whites come
toward the felt, the frame lines around distant art go grey. Measured at
strength 0.25, f/2: the hand's contrast falls 16%, the far deck's 11%, and the
room and the felt are bit-identical.

**What it does not look like:** every edge stays razor sharp. No feathering of
a near card over what is behind it, no bokeh. Both need a layer.

It is **zero on `Scene.MINIMAL`** whatever the dial says — a haze over `#060608`
is a grey rectangle marking where the room ends.

---

## The room, and the rule it looks like it breaks

The `room` group moves the desk, the wall, the window and the lamp — which for
two releases this page listed as *deliberately not on the panel*. The rule has
not changed and the room was never covered by it; the entry was wrong.

The rule is: **nothing that re-solves the board.** `Scenery.ROOM_ABOVE` does —
it decides how much of the stage's height the board declines to use, and card
size is the single free variable in that solve, so a slider on it could cross
`BoardLayouter.MIN_CARD_WIDTH`, flip `layout.fits` and replace the whole stage
with "Not enough room to lay a table out here" mid-drag. The desk, the wall, the
window and the lamp do not: they are read *inside* `Scenery.of`, which
`PlayScreen` remembers against the already-solved layout. Moving them rebuilds
the room and leaves the board, the hit boxes and the gesture in flight exactly
where they were.

**The ranges are the widest the geometry survives, not the narrowest that stay
tasteful.** That was kai's ask and it is the right one for an instrument: the
point of moving a number on the device is to find out where it stops working,
and a slider that stops before the answer cannot tell you. What holds the room
together at the ends of them is a set of clamps rather than a set of shorter
sliders, because the limits are relative to the board and the board is not the
same on every device:

- the window is clamped into the wall it is a hole in, on all four sides, and
  its opening is kept at least a glazing bar tall;
- the sill is kept above the bottom rail's own height, or that rail hangs
  through the desk;
- the lamp may not stand over the felt, and it is pushed out by *its own widest
  measured radius* toward whichever side it was already nearer;
- the derived wall distance is floored, because `deskDepth` and `wallBack`
  subtract and a wall in front of the mat's far edge stands over the cards.

`SceneryTest.everyRoomKnobAtEitherEndStillLeavesARoom` walks every one of these
sliders to both ends and asks the room's own invariants about the result. Every
clamp above is there because that sweep went red.

## What is still deliberately not on the panel

`Scenery.ROOM_ABOVE`, the gap fractions, and everything else that re-solves. The
reason is one line in `MatInput`: the mat is a single `pointerInput(layout)`, so
re-solving the layout tears down the gesture arbiter's event stream mid-gesture
while the machine goes on believing the gesture is live. **A slider that
re-solves is a slider that can kill the drag that is moving it.**

Those numbers are in the export's `reference` block instead, read straight off
the live constants so it cannot lie about the build it came from. Changing one
is a code change, and `docs/LOOP.md`'s ledger is where the argument for it goes.

---

## Before baking anything in

**Check the blast radius.** Almost every number on this stage is pinned by a
named test, and the tuning panel is exempt only because nothing it writes is a
default yet. `docs/LOOP.md` §5's iteration notes and the tests themselves are
the list; the ones most likely to go red are:

| baked knob | test |
|---|---|
| `camera.pitchDegrees`, `camera.distance` as new *seats* | `StageCameraTest.everySeatIsInsideTheEnvelope`, `theSeatTheStageOpensAtNeedsNoCorrectionAtAll`, and all three `GoldenStageTest` goldens |
| `camera.lens` at all | `CameraLensTest` — four of its five claims are about the shipped 1.0 |
| `camera.clearance` at all | `StageCameraTest.everySeatIsStillLegalAtEveryClearance`, `theFloorAtALowSeatIsWhereTheReportSaidItWas`, and `StagePlane.SAFE_DEPTH`, which has to stay above it |
| `hand.leanDegrees` without the lift following | `CardShadowTest.aCardLeanedOnItsBottomEdgeKeepsEveryCornerAboveTheTable` |
| `cards.carryLift` and the lifts under it | `CardShadowTest.itSoftensAndFadesWithHeight` |

And the standing rule: **do not re-record a golden and change behaviour in the
same release.** A session that ends by pasting new defaults *and* re-running
`GoldenStageTest` destroys the one instrument that says which change moved the
picture.

---

## Three things that went wrong building it, so they do not go wrong again

**Every slider reset every other slider, for three releases.** A knob's track is
a `pointerInput`, keyed on the knob's path — a key that never changes. So Compose
starts that gesture coroutine once and never restarts it, and it keeps the
lambda it captured the first time. The lambda closed over the *document*, so
every drag wrote `documentAsItWasWhenThePanelOpened.copy(thisOneField)`. Move the
focal length and the distance went back to 1.45; move the distance and the lens
went back to 1. All twenty-seven, and the readout was innocent throughout —
that is an ordinary composition read, so the numbers looked right up until the
moment a second slider was touched.

`rememberUpdatedState` is the local repair, and `ui/dnd/DragSource.kt` has been
making it since the first drag: *"the gesture coroutine below outlives any single
composition, so it must read these through state."* The structural repair is that
a slider now hands back **a knob and a number**, and the read-modify-write happens
inside the preferences transform where the live document is. A stale lambda
cannot carry a stale document if it never carries a document at all.

`StageKnobsIndependenceTest` is what guards the half of this that core can see:
writing one knob moves exactly one knob, pairwise, across all twenty-seven. It
would not have caught the capture — that is a Compose fact and there is no
Compose test target here — but it catches the other way in, which is a
copy-and-paste in a setter lambda.



**A focusable node ate the first key event.** The long-press door was
`combinedClickable`, which makes its node focusable — and adding a newly
focusable node to that bar swallowed the *first* key the stage received
afterwards. In `:studio` that meant the first shot of every run silently came
back at the table seat whatever seat it had asked for. It is a raw
`detectTapGestures` now, which touches no focus.

**A `LaunchedEffect` cost the studio its determinism.** Placing the tuned camera
from a coroutine meant *which frame* it landed on depended on the scheduler, and
two runs of an identical tuning came back 77.7% of pixels apart. It is a
`SideEffect`, which runs synchronously after composition and cannot straddle a
frame. For the same reason the studio writes the tuning **once, before the
settle**, rather than per shot beside the room — a preferences write is a
coroutine and the seat press is not, so the two raced.

Both failures looked like the tool being wrong rather than the harness, which is
the expensive kind.
