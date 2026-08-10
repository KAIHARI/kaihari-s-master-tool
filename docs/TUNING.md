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

This is the pair a photographer expects, and it took two goes to get there.

**`distance` is where you stand.** Stepping back weakens the perspective *and*
makes the table smaller, because that is what walking backwards does.
`CameraPose.planeFor` welds `cameraDistance` to `zoom` to keep those two
welded. This is the only dial that changes the perspective.

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

The millimetres are a function of the **lens alone** and are honest only
because of that. The proportionality is exact — `f = 18/tan(halfFov)` and the
field of view goes as `atan(1/lens)`, which
`CameraLensTest.theMillimetresBesideTheDialAreTheOnesTheProjectionHas` pins.
The *calibration* is not: 33mm is this projection measured at the home
distance, so moving `HOME_DISTANCE` means re-measuring `StageTuner.HOME_MM`.
That test is the tripwire.
 The first version computed a field of view from
`distance × lens`, so touching the distance slider moved the number beside the
focal-length slider — 36° at distance 1.45, 26° at 2.0, with the lens dial
sitting still. That read, correctly, as one dial resetting the other.

**What the first version actually was.** v1.2.38 put the lens on
`cameraDistance` alone: framing pinned, perspective moving. That is a **dolly
zoom** — a real control, a good one, and not a focal length. It is worth
rebuilding one day as its own row, under its own name.

**What it costs.** Framing. Past `1.0` the board is magnified and can run off
the edges of the screen, exactly as a real zoom crops. Nothing catches it, and
that is deliberate: `CameraFit` corrects by moving the *distance*, and calling
it from here would re-couple the two dials this exists to separate.

**What it stopped costing.** `perspectiveGrowth` no longer moves with the lens —
both terms scale, so the ratio the board was solved against is untouched. The
old warning that a baked-in lens under 0.947 would turn
`StagePlaneTest.theStageSaysHowMuchRoomItsOwnTiltCosts` red is retired.
`CameraEnvelope.minDistanceAt` lost its lens factor for the same reason: the
lens cancels out of `halfDiagonal · zoom · sin ≤ clearance · cameraDistance`,
and zooming cannot bring the table closer to a camera that has not moved.

---

## The distance dial has a floor, and it used to hide it

kai's next report on the same pair: *"the slider can go below 1.4 but it doesn't
show it visually and won't let me get closer."* Both halves of that were true, and
neither was the lens.

`CameraEnvelope.minDistanceAt` solves a floor from the pitch and the mat's own
diagonal, and `CameraRig.placeAt` clamps to it. The panel kept displaying the raw
number, so below the floor the value moved and the picture did not. On a
2800×1607 stage:

| pitch | floor at 0.5 | at 0.68 (shipped) | at 0.95 (the knob's end) |
|---|---|---|---|
| 21° — the table seat | 1.02 | 0.88 | 0.80 |
| 34° — the seated one | 1.28 | 1.09 | 0.93 |
| 40° | **1.37** | 1.17 | 0.99 |

That 1.37 is the "one point four". It is a *low seat* that finds it — which is
exactly the seat you take when you are looking for distortion, so the two
complaints arrived together and looked like one.

**`camera.clearance` is the number that floor is solved against**: how far toward
the lens the nearest corner of the mat may travel. The keystone across the table
goes as `1/(1−this)`, so a half caps it at two to one and 0.68 at about three.
The ends are not taste:

- **It cannot reach one.** At one the corner *is* the lens, `project` clamps
  rather than dividing by zero, and a clamp cannot be inverted — so `unproject`
  stops agreeing with `project` and every pile edge and airborne shadow tears.
  0.95 is the stop.
- **It does not go below what shipped.** Tightening it pushes floors *out*, and
  at 0.42 the floor at thirty-four degrees passes 1.34 — which is the **Seated**
  button, so a seat on the bar becomes somewhere the envelope refuses to let you
  sit. There is nothing under a half worth offering.

And the panel says so now: the distance row prints `· held at 1.17` when the
floor is above the value you dialled, so the dead zone has a reason attached
rather than looking like a broken slider.

**`StagePlane.SAFE_DEPTH` went from 0.5 to 0.9 in the same change**, and it had
to. It is the guard that culls a face close enough to the lens to fly off to a
few hundred thousand pixels — and it had been the same number as the clearance by
coincidence. Letting the camera closer than 0.5 without moving it would have
culled the **desk**, which by construction reaches further toward you than the
mat does. Nine tenths is what that guard was always about: `MIN_GAP` and the
number one, not what is comfortable to look at.

**What `CameraFit` still does.** Dialling in a close distance and then *pinching*
gives it back — the fitter runs when a camera gesture ends and keeps every zone
and pile on the glass, and with the table turned forty-five degrees it holds you
at about 1.29 whatever the envelope allows. Square-on it does not bind: it would
allow 0.93 at a forty-degree pitch, well inside the floor. That is the fitter
doing its job rather than a second bug.

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
