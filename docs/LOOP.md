# The fishbowl loop

An autonomous loop pointed at one thing: **making the room real.**

Not the cards — the room. kai's brief, in his words: the fidelity of a driving
simulator, and *"until the entire environment (desk, window with sun outside,
millennium puzzle, floor, bed, bookshelf, etc) is completely photorealistic and
done"*. The play stage is a place now, and the cards happen to be lying on the
desk in it.

## The mandate, and what it suspends

kai has lifted the constraints that were holding the room back. Written down
here rather than left implicit, because half of `DESIGN.md` was built around the
opposite goal and a future iteration reading it cold would put them back.

**Suspended, for the DESK scenes only:**
- *True black, sharp white.* The room is lit by a window and a lamp and it is
  allowed to look like it. Black was a stage; this is an interior.
- *Colour is meaning or light, never decoration.* Wood is brown because wood is
  brown.
- *Nothing in a `SceneModel` may hold decoration.* A bookshelf is decoration.
  That is now the point.
- *No shaders.* Already lifted (`AAA.md` #99); see `DESIGN.md` §6.

**Still standing, and not up for revision by an iteration:**
- **`Scene.MINIMAL` does not change.** It is the handbook's stage, it is what
  kai falls back to, and it is the control against which the room is judged.
- **Nothing idles.** A room that breathes is a screensaver. Light moves when the
  hour moves; nothing moves because time passed.
- **The release contract.** versionCode only rises, migrations are never
  renumbered, the APK is signed with the committed key. None of that is
  ambition; it is what stops a device being bricked by an update.
- **Every shader has a plain-draw fallback.** `minSdk` is 26.

## The list, and what "done" would mean

Named objects, because "photorealistic" is not a state anything can be tested
against but *"the bookshelf exists and has books in it"* is.

| | state |
|---|---|
| Desk top | **wood**, per-pixel: ring bands, fine grain, pores, latewood gloss |
| Playmat | **gone.** The desk is the surface; zones are routed into it |
| Zones | outlines. Should be inlay or engraving, and lit as such |
| Window | **a window**: four panes, a frame, a graded sky |
| Sun outside | not possible through this window — iteration 8 has the numbers |
| Floor | a flat plane, barely in frame |
| Wall | **a fifth of the screen**, and painted rather than void |
| Millennium Puzzle | **turned, chamfered, propped, with a bail.** No mark on it — see iteration 12 |
| Lamp | **a turned lamp**: brass foot, tapered stem, a shade that is a shell and is lit from inside |
| Bed | does not exist |
| Bookshelf | does not exist |
| Room shadows | nothing in the room casts one (`AAA.md` #61d) |
| Night | **the room falls away from the lamp**; the table does not |

That obstacle is gone. It was the first real one — **the board filled the stage
vertically**, so there were six pixels of wall at the table seat and no room for
anything behind the desk — and iterations 7 and 8 cleared it. `roomAbove` keeps
a fifth of the stage's height for the room, at a cost of a fifth of the card,
and everything left on the list is now somewhere a camera can look.

This document is the loop. It says what one iteration is, what it may not do,
and what it has already done. An agent picking this up cold should be able to
run an iteration from this page and `docs/AAA.md` alone.

---

## 1. Why this can be a loop at all

Because the stage can now be *looked at* without a tablet.

`:studio` composes the real `PlayScreen` into an `ImageComposeScene` and writes
a PNG. Real theme, real dependencies, real card art off the network, a frame
clock advanced by hand, no window and no GPU:

```
tools/shoot.sh                                   # the default contact sheet
tools/shoot.sh --shots=desk-night-seated         # one named shot
tools/shoot.sh --shots=a,b,c --keys=n --settle=200
tools/shoot.sh --density=2 --width=2960 --height=1848   # tablet metrics
```

A shot name is parsed for its room, its hour and its seat: `desk`/`minimal`,
`day`/`night`, `overhead`/`table`/`seated`. `--keys` presses the stage's own
shortcuts before shooting, so a script can deal, draw or change the room the
way a hand would.

That is the whole reason this stopped being a wish list. Before it, every
visual idea cost a signed release to evaluate and the evaluation was somebody
squinting at a tablet. The very first picture it took had no cards in it, and
that turned out to be the app rather than the harness — see §5, iteration 0.

**A loop without an organ of perception is not a loop, it is a random walk.**
Anything that makes the next look cheaper or truer is therefore always in
scope, and outranks the feature it was going to be spent on.

---

## 2. One iteration

Six steps. Every one of them, every time.

1. **Look.** Shoot the contact sheet against the current tree and actually read
   the pictures. Name what is wrong in a sentence a person would say out loud —
   *"the cards look like stickers"*, not *"shadow term is under-weighted"*.

2. **Pick one thing.** From `docs/PHOTOREAL.md`, then `docs/FIDELITY.md`, then
   `docs/AAA.md`, then whatever the look just showed — in that order, because
   each is argued for and the earlier ones supersede the later. One thing. An
   iteration that lands two changes cannot tell you which one worked.

   `PHOTOREAL.md` is the staged plan to a photographic table, written after kai
   moved the target to driving-simulator fidelity and after the shader seam
   opened; it says which parts of `FIDELITY.md` it supersedes, and it is honest
   about where the ceiling is. `FIDELITY.md` is the canvas-only backlog and is
   still right about everything a shader does not touch. `AAA.md` is the
   hundred-item menu and outranks neither on scope.

3. **Say what will change.** Before writing code, write the line that will be
   true of the *picture* afterwards. If that line cannot be written, the item
   is not understood well enough to build and the iteration is research.

4. **Build it in `:core`, wire it in `:ui`.** Arithmetic goes in `core/render`,
   `core/scene`, `core/motion` with a `commonTest` that names a *claim* — not a
   golden. `:ui` only turns solved values into paint. `./gradlew :core:jvmTest`.

5. **Look again, and compare.** Same shots, before and after. If the picture
   does not show the line from step 3, the change is wrong even if the maths is
   right. Revert or fix; do not ship a change whose effect you cannot see.

6. **Ship it.** Push, CI green on all three jobs, fast-forward `main`, dispatch
   `release.yml` with the next patch, confirm the APK is attached. `CLAUDE.md`
   §*Ship Every Change* is the authority and it is not optional: kai judges this
   on the tablet, and work on a branch is work he cannot see.

Then write the iteration into §5 — one paragraph, including what did **not**
work. A loop that only records its wins relearns its losses.

---

## 3. What the loop may not do

Four of these come from `docs/DESIGN.md` and are restated because a long
autonomous run will drift into all of them.

- **No engine.** Ever. No 3D engine reaches KMP common code and every one of
  them costs the desktop target. Geometry is arithmetic in `:core`, reaching the
  screen through one `graphicsLayer` and a canvas.
- **A shader colours; it never draws.** The seam is open (`AAA.md` #99 is done,
  `DESIGN.md` §6 has the rules) and it does not relax the line above: nothing in
  SkSL holds a scene, owns a transform or decides where anything is. And
  `compileStageShader` returns null — Android below 33, a refusing driver — so
  **every caller ships a drawing that works without one**. That fallback is not
  a degraded mode to tolerate; `minSdk` is 26.
- **Nothing idles.** No breathing cards, no drifting light, no ambience. Motion
  explains a change; it never announces one. This is the single rule a fidelity
  loop is most likely to break, because idle motion is the cheapest way to look
  alive and the fastest way to look like a screensaver.
- **Colour is meaning or light, never decoration.** The prismatic ramp is for
  the thing under your finger and for reveals. Swiss: sharp white on true black.
- **Do not re-record a golden and change behaviour in the same release.**
  `GoldenStageTest` earns its place by being the one recording in the
  repository. A release that moves it is a release that has lost the ability to
  say which change moved it. Split them.
- **Do not silently widen scope.** Siding patterns and shootout mode are
  deferred by kai. The near edge of the table, the daylight patch and shadows in
  the room are each *decisions* recorded in `AAA.md` (#61, #61c, #61d), not
  oversights to fix on the way past.

And one that is this loop's own:

- **Never ship a picture you have not looked at.** The studio makes looking
  cheap. There is no longer an excuse.

---

## 4. What "better" means, so the loop can tell

Five gates. A change passes all five or it does not ship.

| Gate | How it is checked |
|---|---|
| It is visible | The before/after shots differ in the way step 3 predicted |
| It is true | A `commonTest` names the claim; `:core:jvmTest` green |
| It is affordable | `tools/shoot.sh --budget=120` before and after — the **ratio**, not the milliseconds; and no new per-card per-frame allocation |
| It is the house style | `docs/DESIGN.md` — and if the change argues with the handbook, the handbook gets the amendment *first*, in its own commit |
| It survives the matrix | `tools/devices.sh` — `phone-small` and `tab-s11` at minimum. See `docs/DEVICES.md` |

The frame budget is the one that will bite. Sixty cards is the working number,
and a per-card layer or a per-card blur is the class of change that has to be
measured before it enters a signed build, not after.

`--budget=N` deals a hand and times N rasters of the whole scene, twice, and
reports the second — the first pass is JIT and layer allocation and showed up as
a p95 nine times the median. It is **not** the tablet: it is software Skia on a
container's CPU, and the absolute figure means little. The ratio means a great
deal. The baseline at 1600×1000, six cards in the air, is *median 92ms, p95
101ms*; a term that takes that to 180 will not be free on a tablet either.
`FrameProbe` on the device is still the honest number and still the last word.

**And for shader work there is no honest local answer at all.** The studio has
no GPU, so it runs every SkSL pixel on the CPU, and `ImageComposeScene` redraws
the whole scene every frame where a device skips a surface entirely when nothing
about it moved. Iteration 5's weave read +111ms for ~40 ALU over ~0.7Mpx; treat
that as the studio's calibration constant, use the ratio only to catch
order-of-magnitude mistakes, and get `FrameProbe` off the tablet before any
per-pixel change enters a signed build.

---

## 5. The ledger

Newest last. One paragraph each: what was tried, what the picture showed, what
shipped, and what was learned that the next iteration should not have to
rediscover.

### Iteration 0 — an eye, and the first thing it saw

Built `:studio` so the stage can be drawn to a PNG headlessly, and pointed it at
the play screen. The first shot came back with a room, a mat, two shuffle marks
and **no cards at all**. That was the app. `PlayScreen`'s single frame loop was
`LaunchedEffect(Unit)` while `cards` and `pilot` are both `remember`ed *against
the deck*, so a deck arriving under an already-composed stage left the loop
stepping the previous, empty map — every card in the new one stayed parked at
the pose it was dealt from, the opening five included, stacked invisibly on the
deck they came out of. The camera and the puzzle kept moving the whole time,
because those two are remembered against nothing, which is exactly why it read
as *the hand is missing* rather than as *the stage is frozen*. Keyed the loop on
`cards` and `pilot`. Shipped in v1.2.29.

The lesson worth keeping: **ordinary use never hit this**, because you pick a
deck and *then* open the table. The studio hit it on its first run, before any
fidelity work at all, because a harness starts the screen in states a person
would have to work to reach. That is most of the argument for having built it.

### Iteration 1 — the window nobody has ever seen

Then made the eye trustworthy: `tools/compare.py` for before-and-after with the
difference multiplied twelve times, and a **fixed shuffle** — `PlayScreen` takes
an optional seed, null still meaning a fresh deal — because two pictures of two
different hands are not a comparison. Two studio runs are now bit-identical, so
every pixel that moves is a change in the code.

The first thing the contact sheet showed was a pale wedge lying across the top
bar with seven of its controls printed on it at about 1.2:1. It is the room's
**window**, and the stage's box is padded down by `TOP_BAR` precisely so this
cannot happen — but the mat's `graphicsLayer` is a rotation, and a rotation does
not clip, so the back of the room projects up out of its own box and onto the
chrome. Most of the time what lands there is the dark wall and nobody noticed.

Measuring it is what made the iteration worth having. At 1600×1000 the pane
occupies rows 0..36 against a 44-tall bar: **every row of the window is inside
the chrome, and none of it has ever been visible as a window.** Behind it the
wall itself gets 6px of open air at the table seat, 25 seated and 26 overhead.
`AAA.md` #62 already argued the window down as low as it could go to be seen at
all; the measurement says it did not go far enough, and could not have.

Shipped the half that is not a matter of taste: the bar stands on Ink, opaque
rather than a scrim, because a translucent wash over content is the second line
of `DESIGN.md` §12 and an 82%-opaque bar is a bar that fails on the one
background it needed to survive. The ground behind "Overhead" went from
luminance 223 to 6. Exactly 4.4% of pixels moved — 44 rows of 1000, the bar and
nothing else, which is what a deterministic studio buys you.

The half that **is** a matter of taste is left for kai and written down here so
it is not quietly decided by the next iteration: a room whose back wall is a
6px hairline cannot hold a window, a lit patch, or anything else #61c and #62
want, and giving it more means re-framing a stage he has tuned. Until that is
answered, the room's ceiling is real and the fidelity work belongs on the felt,
the cards and the light — which is where the backlog points anyway.

### Iteration 2 — nothing ever lands square

`AAA.md` #52, and #47 for the graveyard. The line written before the code: *the
five cards in the hand stop reading as one printed strip.* They were dead level,
evenly stepped, tops on a perfect horizontal — five copies of one card rather
than five cards.

`core/motion/Settle.kt` answers "where did instance N come to rest", as a turn
in degrees and a slip in fractions of a card's width. Three things about it are
the design rather than the implementation. It is a **hash of the instance id**,
not a random number: there are two hundred levels of undo here and #38 is
explicit that anything unreplayable breaks them, and a hash needs no storage, no
undo entry and no migration — it is the same answer forever. It is **three
independent channels**, because deriving the turn and both slips from one hash
by shifting gives a hand where every card that leans left is also low and left,
which is a pattern rather than an unevenness. And the amount is a **property of
how the card got there** — `SQUARED` for a deck tapped against the table,
`PLACED` for a card set down, `THROWN` for a graveyard swept into a heap —
because a board where the discard pile is as tidy as the deck is a board nobody
built by hand.

Frame cost: nothing. It is three float operations per card, evaluated when the
board changes rather than per frame.

Two things learned. The first test written said *no dealt hand leans entirely
one way*, which is false and was rightly red on the first run: five fair coins
agree once in sixteen, so a hand that happens to lean is the feature working.
The claim that actually matters is that **consecutive instance ids are
uncorrelated** — a dealt hand is five consecutive ids, and that is precisely the
input a cheap `id * prime` hash fails, fanning them out in an even ramp that
reads as drawn. The second: `--budget` is noisy across runs when anything else
is using the container, so measure it back-to-back or not at all.

Shipped in v1.2.30.

### Iteration 3 — the deck has been printing its own count backwards

Not from the backlog. From zooming in on the deck pile — the most prominent
physical object on the table — while looking for something else.

`▤34` on the deck was drawn as its own mirror image, and so was `▤14` on the
extra deck, and so is the count of any face-down stack on the mat. The one
number a player checks most, reversed, in every shipped build.

The cause was already solved five lines away. A card's map takes its rectangle
to its four real flattened corners, and for a back those corners are wound the
other way, so everything inside the card's box comes out mirrored —
which is *correct* for the printed side and is why `CardFace` carries
`if (!faceUp) scaleX = -1f` with a comment explaining it. The badge is a
Compose child of the same box and never got the same line.

Read live from `motion.pose.rotY` rather than from `seat.faceUp`, because a card
*turning over* is the case that matters: the glyphs have to flip on the same
frame the printed side does, which is the frame the card is edge-on and neither
is visible.

The compare said `0.0% of pixels moved, peak 215` — a few hundred pixels changed
completely and nothing else changed at all, which for a 50×24 badge in a
1600×1000 frame is exactly right. That number is worth remembering as the
signature of a surgical fix: a high peak against a near-zero area.

The lesson: **zoom in.** Three iterations found three defects and two of them
were invisible at 1:1. A contact sheet says whether the room reads; a 4x crop of
one object is what says whether the object does.

### Iteration 4 — a penumbra the same width on all four sides

`docs/FIDELITY.md` landed between iterations, and F3 is the first thing on it.
The line: *a card's shadow stops being fatter above and below than it is at the
sides.*

`StageRender.polygon` grew a shape by pushing every corner **away from the
shape's centre**, which is not an offset. On a 59:86 card the corners sit 52.1
half-units out while the long edges sit at 43 and the short at 29.5, so a growth
of `w` moved the long edges by `0.825w` and the short ones by `0.566w` — every
shadow on the table 1.46 times wider top and bottom than at its sides, for no
physical reason at all. A disc light throws the same penumbra on every edge of a
rectangle. The same line took the half-*diagonal* as its inset guard, so the
umbra was allowed to eat one and a half times the half-width it was protecting.

`core/render/Outset.kt` does it the textbook way: push each *edge* out along its
own normal and intersect the neighbours. No angle formula to get the wrong way
round — and the bisector form is `w / sin(θ/2)`, which the survey wrote with
`cos`, which is exactly the kind of thing this avoids by not needing it. The
guard now measures the distance to the nearest **edge**.

Four gates. Visible: 0.7% of pixels moved and the amplified difference is
concentric penumbra rings and the two pile shadows, with nothing on any card
face. True: the test asserts each of a card's four edges moves out by exactly
`w`, that no inset up to 80px ever folds the shape, and that a quad seen almost
edge-on — which every card passes through twice per flip — cannot grow a
whisker. Affordable: 53.6ms against 52.8ms back-to-back, which is noise.

Learned: `--budget` measured while Gradle is running reports 117ms median and a
424ms p95 against a true 53ms. The note in §4 about measuring back-to-back is
not a nicety; the number is worthless otherwise.

### Iteration 5 — the mat becomes cloth, and the stage gets a shader

kai changed the target: the fishbowl should reach the fidelity of a driving
simulator. That answers `AAA.md` #99, which had been marked *[your call]* since
it was written, so the handbook was amended first and in its own commit — §6 of
`DESIGN.md` now carries the seam and its four rules.

**Proved it before building on it.** `:studio` runs on a raster surface with
`skiko.renderApi=SOFTWARE` and no GL context at all, so the sharp question was
not "does SkSL exist" but "does it raster without a GPU" — because a shader the
loop's own eye cannot see would put every per-pixel change back behind a signed
release. `:studio:spikeShader` compiled a weave and drew it: 47 levels of
luminance modulation on a near-black base, no GPU. That answer is the reason
everything below was worth writing.

`ui/gpu/StageShader.kt` is the seam — `RuntimeShader` on Android 33+, Skia's
`RuntimeEffect` on desktop, `null` everywhere else. `FeltWeave` is its first
use: plain weave, warp over weft, normals from central differences because
neither platform's runtime shaders have `dFdx`, composited in `BlendMode.Overlay`
so the identity is mid grey and a bug in it cannot blank the mat.

**The bug worth remembering.** The first version compiled, ran, cost a full pass
and changed *nothing* — 0.0% of pixels, peak 1. `Light.direction` is the way the
light **travels**; the key is `(0.30, 0.45, -0.84)`, heading down onto the table,
and every dot product wants the vector pointing back at the lamp. Handed the
travel direction, `max(dot(n, l), 0)` is zero on every thread of an upward-facing
surface, so the shader returned overlay's identity everywhere. It is
indistinguishable from a shader that failed to load, and the only thing that told
them apart was a one-line probe on `compile() != null`.

**The gate that could not be answered here.** Frame cost went 53.6ms → 165ms,
and that number should not be believed. The studio has no GPU, so it runs every
pixel of SkSL on the CPU, and `ImageComposeScene` redraws the whole scene every
frame where a device skips the felt entirely while the camera is still. This is
the one case so far where the studio's ratio is a bad proxy, because the change
moves work *from* CPU paths *to* a GPU fragment program. `FrameProbe` on the
tablet — three taps on the life-point total — is the only honest answer, and it
is asked of kai rather than assumed.

### Iteration 6 — the cloth stops being able to crawl

`docs/PHOTOREAL.md` landed — nine stages from here to a photographic table — and
the first thing its §1 says is that iteration 5 shipped a defect. It is right,
and this is the loop working: **a survey of the code caught what looking at
three seats could not.**

The weave's threads are 3.06 mat px. Screen density falls as `cosTilt · scale²`
with depth, so at the envelope's steepest legal angle the far half of the mat
reaches 0.97 cycles per pixel — twice Nyquist. Past Nyquist a procedural texture
does not soften, it *aliases*, and an aliased texture **crawls as the camera
orbits**, which breaks "nothing idles" by accident rather than by choice.

The measurable half was visible at the shipped seats: per-pixel contrast fell
from 2.17 near to 1.34 far, the weave quietly losing energy as it compressed. The
part that actually bites is at 58° and the studio can only reach the three seats,
which is a real gap in the eye and is now in §6.

`StagePlane.jacobian(x, y)` answers how far a mat unit travels on the glass — the
projection's exact derivative, closed form, because `project` at z = 0 is closed
form in four cached trig values. Four claims, and the load-bearing one is that it
agrees with a finite difference of `project` itself across six poses and five
points. Everything downstream — the band-limit here, level of detail (`AAA.md`
#97) — stands on that agreement.

The weave is rewritten around it: a basket weave in closed form returning height
*and* both slopes, rather than four samples of a height at an epsilon nobody
could defend. The relief fades out as the cloth approaches half a cycle per
pixel, per axis, and the glint the fade removes is returned as a broadened lobe
so the far felt does not read as a different material from the near felt.

**Proved the fade rather than trusting it.** The three seats are all below
Nyquist, so the path could not be photographed — so the threads were temporarily
made twelve times too fine. Per-pixel contrast collapsed from 3.11 to **0.15**:
the cloth dissolves into a flat surface instead of breaking into moiré. Restored
afterwards.

And it is *cheaper*: 165ms → 120ms in the studio, because two transcendentals
beat six. A correctness fix that also pays for itself is worth noticing — the
first version's expense was buying an epsilon, not an answer.

### Iteration 7 — the board stops using the whole stage

The foundation the table above had been waiting on, and the smallest change in
this ledger by line count. `BoardLayouter.solve` gains `roomAbove`: a fraction
of the stage's height it declines to use. `Scenery.ROOM_ABOVE` is a fifth and
only the desk pays it; minimal passes zero and comes out bit-identical, which is
the mandate's requirement and is checked rather than assumed.

It costs card size and there is no arrangement where it does not — the board is
height-constrained on every device this ships to, so the reserve comes straight
off the card: 107dp down to 85dp on the tablet, 115px down to 92px on the
narrowest phone against a floor of 28. That is the trade, and it is written into
the KDoc rather than left for somebody to discover.

The picture: 80% of pixels moved, and above the desk's far edge there is a wall
with a window in it instead of wood to every corner. Which immediately showed
that the wall was `#191B22` — the colour of the void behind the desk, chosen
when it was a six-pixel hairline.

### Iteration 8 — the window, and two bugs in the floor under it

The line written first: *the window stops being a bright rectangle cut in a
black band.* It is now a four-pane sash — two stiles, two rails, a cross of
glazing bars, on their own `Surface.FRAME` — with a graded sky in it and a
painted wall behind it.

**There is no sun in it, and that is measured rather than conceded.** The room's
vertical axis is drawn compressed: a wall four and a half card widths tall
standing under an eye eleven and a half card widths up. Trace the day key back
from the eye and it crosses this wall's plane about 2 700 pixels above a head
that is at 232, at every seat the camera can reach. Bringing the disc into the
opening would need light arriving from *below* the eye — so a `Sky` that placed
the sun honestly was built, found to compute something invisible, and deleted.
kai's *"window with sun outside"* is answered by the light in the room and by
which side of the sky is brighter, not by a disc. Lowering the day key to a
golden-hour elevation would change every shadow in the room and is his call.

**And the room's paint order was never an order.** The joinery took the room
from ten pieces to seventeen and a glazing bar drew straight through the
Millennium Puzzle. Two separate defects, both already shipped:

`behind` was cyclic. It finds an axis that separates two boxes, a pair can be
separated on several, and both earlier versions *picked between* disagreeing
axes — the first that separated, then the one the camera was most nearly looking
along. Either choice loops: A above B, B left of C, C nearer than A, one axis
each. Fourteen of sixteen pieces in a single cycle at the overhead seat, and the
ten-piece room had them too. The fix is to stop asserting an order for any pair
no ray connects: disagreeing axes mean *no answer* (disagreement is the proof
that no ray hits both), an eye inside the gap between two boxes means no answer,
and pairs that do not overlap on screen are never compared.

And `order` bubbled, which cannot topologically sort a partial order at all — an
adjacent swap cannot move a piece past a *third* piece that has no opinion, and
no-opinion is the common case. It is a topological walk now. `ScenePainterTest`
holds both claims at 432 cameras.

The lesson: **the room's own tests could not see the camera.** `PuzzleTest` swept
360 degrees of yaw while handing the eye a fixed `(0, 0, 1)`, which turns the
*projection* and leaves the camera parked over the middle of the table — so
seventy-two poses asked one question, and every cycle lived in the ones it never
asked.

Frame cost 346ms to 356ms back to back. Both shipped in v1.2.36.

### Iteration 9 — night stops being day with the lights down

The line: *at night the wall goes dark away from the lamp, so the only reason
anything above the desk is lit is the lamp standing on it.*

The two desk rooms differed by an ambient of 0.76 against 0.55 and sRGB flattens
that to nine levels — the wall came back **67 by day and 68 at night**. Every
cue that says night lived in the one term nothing was allowed to touch, because
`NIGHT_FLOOR` protects `Tone.veil`'s approximation on card art.

`StageRig.room` is the seam that lets both be true. The table keeps `face` and
keeps the floor; the room gets `Light.roomReach` — the ambient as what it
physically is, which is bounce, off a lamp, from over there. Three things about
it were each a wrong first attempt:

- **Dimming the ambient alone left the far wall at half its daylight
  brightness.** The rim was supplying a quarter of the light on a vertical
  surface, undimmed, from a lamp that does not exist after dark. Ambient, fill
  and rim are all *the room*; only the key's beam is the lamp.
- **A square root, not the attenuation.** Scattered light falls off far more
  gently than the beam it came from. Falling like the beam gives a wall that is
  black a card's width from the lamp.
- **And a face needs more than one answer.** `room` shades a face at its centre
  and the wall is fifteen hundred pixels wide, so the whole correct calculation
  came out as one flat grey stepping to another at a seam — the same defect one
  level up, and the shot after the first two fixes was almost unchanged.
  `StageRig.wash` samples along whichever of a quad's two axes the light runs
  along; the renderer draws it as a gradient, exactly as it already draws
  `LightPool` on the mat. Null under two levels of 255, so daylight allocates no
  brush.

The wall now runs 52 beside the lamp to 34 at the far end, against 67 by day.
Day and minimal shots are bit-identical, which is the mandate. 371ms against
379ms back to back.

Two tests were wrong before the code was. *"Night is dimmer than day
everywhere"* is false — a bulb a hand's width from a wall is the brightest thing
that ever happens to that wall, and the claim that holds is about the far end.
And *"a glazing bar is too small for a gradient"* is false twice over: a bar is
eight pixels thick and a hundred and seventy long, and a patch forty pixels tall
varies by more than the threshold on its other axis.

### Iteration 10 — an instrument, and a camera dial that was lying about its name

Not a fidelity iteration. kai asked for a tuning tool — *"find tune the camera
(angle, FOV, depth of field, focal length) and position of everything … it will
export a copy paste json for me to feed you back to make the default"* — so this
one builds the thing that makes the next ten cheaper. `docs/TUNING.md` is the
handbook; only what the loop should remember is here.

**Fifteen numbers, and the line that decides which fifteen.** Nothing that
re-solves the board may go on the panel: the mat is a single
`pointerInput(layout)`, so a slider that re-solves tears down the gesture
arbiter mid-drag — including the drag moving the slider. Card size is the free
variable in that solve, so a slider could also cross `MIN_CARD_WIDTH` and
replace the stage with "Not enough room to lay a table out here". The room, the
window, `ROOM_ABOVE` and the gap fractions ride in the export's `reference`
block instead, read off live constants so a paste cannot lie about its build.

**The dial was wrong before the tool was.** kai: *"touching the distance slider
resets the focal length and vice versa, it's not working as I envisioned."* The
values were not resetting — the model was. `lens` multiplied `cameraDistance`
alone, which pins the framing and swings the perspective: a **dolly zoom**, and
not a focal length. Both dials therefore changed the same thing, and the mm
readout was computed from `distance × lens`, so walking backwards moved the
number beside the lens slider from 36° to 26°. It now multiplies `zoom` too, so
the subtended angle is untouched and the lens is pure magnification. It cost the
`minDistanceAt` lens factor (it cancels) and it retired the caveat that a lens
under 0.947 turns `StagePlaneTest` red. It buys back nothing that catches a zoom
running off the edge of the screen, which is what a zoom does.

Three harness bugs, each of which looked like the tool being broken:

- **`combinedClickable` on the life-point number ate the first key event** by
  making the node focusable, so the first shot of every studio run came back at
  the table seat whatever it had asked for. `detectTapGestures` touches no focus.
- **A `LaunchedEffect` placing the tuned camera cost determinism** — 77.7% of
  pixels apart between two runs of an identical tuning, because *which frame* it
  landed on was the scheduler's. `SideEffect` runs after composition and cannot
  straddle one. For the same reason the studio writes the tuning once before the
  settle: a preferences write is a coroutine and the seat press is not.
- **The shot's seat press overrode the tuned camera**, so two tunings differing
  only in the camera compared as identical and the file looked ignored. A
  non-default `camera` block now skips the press.

The whole thing is inert at its defaults — shot with and without the working
tree via `git stash -u`, room and felt max difference **0** — which is what let
it ship ahead of anything that moves a number.

### Iteration 11 — the two objects with no material get one

kai, on the two things left in the frame that were still placeholders: *"the
millennium puzzle and lamp both look extremely rudimentary and completely
lackluster."* The contact sheet agreed in one sentence each. **The lamp is a
cardboard box on a matchstick** — a cuboid shade, a grey stick, a flat tile,
and at night the shade came back *darker* than the same cloth by day. **The
puzzle is a Post-it note** — one mustard quadrilateral, no highlight, no seam,
nothing to say which pendant it was.

Both were the shapes core could express. Until this iteration `core/render` had
exactly two mesh constructors — a quad and a slab — so the room was boxes and
the table was cards, which is the right pair of primitives for a card table and
the wrong pair for the things standing beside one.

**`core/render/Turned.kt`** is a lathe: a profile of rings, spun, capped, posed.
Three things about it are the design. Every vertex goes through `Rot3.place`,
where a slab hangs its body down the *stage's* z whatever the pose says — which
is what later bought the puzzle a lean it could not otherwise have had. A band's
normal is *solved from the profile* rather than crossed from its corners, which
keeps it exact at an apex; the exact answer is short by `cos(pi / sides)` in its
radial half, because a chord is shorter than the arc it spans, and `TurnedTest`
measures the normal each face carries against the vector area its corners
really have. And the side count is derived from the sagitta at half a pixel, and
forced **even**, because an even polygon is centrally symmetric and therefore
has a bounding box centred on its own axis — which is what the room sorts,
measures and lights every fixture through.

The lamp is four turned pieces stacked in z, because `ScenePiece.mesh` holds one
*convex* solid and a cove and an overhanging shade are not. The shade is a
**shell** — up the outside, across the opening, back down the inside — and the
picture is what found that: a solid frustum has a lid, and a lid at this camera
is a bright disc in the middle of the rim, so it came out as a drum with a plate
on it.

Then the material, and the room had none: `StageRig.lit` is ambient plus lambert
plus a graze-gated rim with **no specular term anywhere in it**. Three new terms,
all *beside* the rig rather than inside it, because `GoldenStageTest` records
what `lit` returns and §3 forbids re-recording a golden and changing behaviour in
one release. `gleam` is the lamp mirrored in a surface, additive. `spill` grades
a fixture down its own height. `Face.smooth` lets a facet carry the normals of
the curve it stands for.

**Six defects, and five of them only a picture could have found.**

1. *Every shared edge came out as a hairline of background.* Two antialiased
   fills each cover about half the pixels along the edge they share, and half
   over half is three quarters. Four faint lines on a box nobody mentioned in
   two years; twenty-six on a turned shade is a wireframe. Each facet now strokes
   its own outline in its own paint — and **not** on `Scene.MINIMAL`, because
   that also grows the silhouette by half a pixel and minimal is the control.
2. *An additive pass must not close its own seam.* Painting a seam twice is free
   when the paint replaces what is under it and is a doubling when it adds, so
   the seamed `gleam` drew a bright spoke along every edge of the lamp's foot.
3. *The lit shade came out grey.* Four tenths of the bulb is the honest
   transmission of linen and completely wrong here — everything the renderer does
   with a colour is a multiply, so that is four tenths of `#E4DAC6`.
4. *The gold blew out to white.* A highlight is the source reflected, and the day
   key is a window twenty-two degrees across; gold's own sharpness of thirty-six
   applied to it turns a flat face into a white rectangle, because a large flat
   surface under a directional lamp has **one** alignment over the whole of it.
   The lobe is widened by the source's angular radius now, in quadrature, and the
   same two numbers give a broad whisper by day and a hard glint at night.
5. *The starburst, three times.* A facet is not a point (iteration 9's lesson, one
   level down); the gradient's endpoints must be the midpoints of **opposite**
   edges, not adjacent ones; and a curved face may not choose its gradient axis by
   brightness, because that chooses *per facet* and two neighbours that choose
   differently cannot meet at their shared edge. Measured at 0.074 of the
   surface's light — eighteen levels of 255. Grading along the axis the normal
   turns is the same choice for every facet of one turn.
6. *An inverted pyramid balanced on its point shows nothing but its base.* From
   anywhere above every flank slopes inward and away and is occluded by the top
   face's own edge — twelve pixels of visible strip at the seat this stage opens
   at, and a flat gold square everywhere else. The puzzle is propped back
   thirty-four degrees now, which is what makes the flanks, the chamfer and the
   bail read at all.

And one that is arithmetic rather than taste: a homography onto a facet's own
corners **crushes anything drawn on it toward the narrow end**, because a
projective map's midline is where the projective midpoint is and this flank
tapers 1:0.07. Anything cast into a facet of a cone or a pyramid wants its own
patch, cut out of the face rather than taken as the whole of it.

Two things were wrong before the code was, and both were tests rather than
pictures. `rest` dropped the solid to the desk once, but `Rot3` spins about the
object's own axis *before* tipping it, so which corner is lowest changes with the
turn and the puzzle sank three quarters of a pixel into the wood on every nudge.
And `reach` was the square's own diagonal, written by hand and true right up
until the shape changed underneath it.

`minimal-day-table` is bit-identical throughout, which is the mandate. Frame cost
238–292ms against a 273ms baseline — inside the run-to-run spread.

**And the loop got its eye back on this machine.** The Android SDK installs from
`dl.google.com` inside this container, so `tools/shoot.sh` and `tools/compare.py`
both run here now: `sdkmanager` into `/opt/android-sdk`, `sdk.dir` in
`app/local.properties`, `pip install Pillow`. `tools/crop.py` is new and is the
other half of iteration 3's lesson — a contact sheet says whether the room reads,
a 6x crop of one object says whether the object does. Every one of the six
defects above was found in a crop.

---

### Iteration 12 — the eye is cut

kai, on the release: *"while the lamp is alright, the millennium eye is
completely unsatisfactory. I think the move here is to just delete it entirely."*
So it is deleted — `core/scene/Wdjat.kt`, its test, `Puzzle.eyeQuad` and the
renderer's `drawMark` — and this paragraph is the whole of what is left of it,
because a loop that only records its wins relearns its losses.

**What was actually wrong with it, so the next attempt does not start here.** The
drawing was fine in the flat: rendered into a square it is a recognisable wedjat.
What failed is everything between that square and the object. A hundred pixels of
facet, seen at a slant, through a perspective map, engraved as two flat fills a
pixel apart — the six marks come out as about four pixels of stroke each, and at
four pixels a tapered ribbon is a scratch and a spiral is a blob. It is the same
class of problem as `AAA.md` #61c's daylight patch: the idea was right, the thing
it had to be drawn *through* was not able to carry it.

Three things a second attempt would need, and none of them is a better outline:

- **Relief, not fill.** A cast mark reads because its groove wall catches the key
  and its far wall does not. Two flat fills offset by a pixel is a decal of that,
  and at this size the pixel is the whole mark. It wants a normal, which means it
  wants the shader seam — `docs/PHOTOREAL.md` stage 4's per-pixel path, with the
  mark as a height field rather than a path.
- **Or a bigger object.** The puzzle is a card wide because it has to clear the
  mat and stay off the lens. Nothing about the mark works until the facet it is
  on is two or three times the size, and that is a framing decision of the kind
  `docs/LOOP.md` iteration 1 left open, not a rendering one.
- **Or no mark at all**, which is where it is now, and which is not obviously
  wrong: the object reads as a gold pyramid with a bail, propped on a desk, and
  `docs/DESIGN.md` §11's whole argument for it is that an easter egg's value is
  that nobody told you.

**What survived, and why.** The lean. It went in so the flanks could carry the
eye, and it turns out to be the thing that makes the object read at all: balanced
on its point an inverted pyramid presents nothing but its base, and the shot
without the lean is a flat gold square with a ring lying on it. Shot both ways
before deciding, which is the only reason this paragraph is a claim rather than
a preference.

---

## 6. Seen, not yet done

Things a look has already found, so the next iteration does not have to find
them again. Not a backlog — `AAA.md` and `FIDELITY.md` are that — just a list of
what the eye has caught and the hand has not reached.

- **A 35-card deck is drawn as about seven thick slabs.** The pile's ruled side
  is evenly banded at a spacing that does not follow the count, so a deck reads
  as corrugated card rather than as a stack of thin ones. `CardSolid.pileDepth`
  and whatever draws the side. This is the biggest object on the table and the
  most obviously wrong thing left in the frame.
- **A shadow on true black has nowhere to go.** The hand's cast shadows are
  drawn and correct and invisible, because the felt they land on is `#0E0E12`.
  `AAA.md` #18 (shadows are not black) and #65 (the felt has a weave) are one
  item, not two: neither is worth doing without the other.
- **Nothing on a card catches the light.** `Shading.of` puts a specular pool on
  the stock and at these sizes it is not reaching the picture. Worth measuring
  before assuming it needs to be stronger — the handbook is explicit that the
  highlight *moves* rather than brightens (§7).
- **The first run against a cold art cache is not comparable.** Iteration 8's
  before-shot showed 4.3% of the *minimal* stage moving, which no change in the
  tree could have caused; re-shooting the previous commit gave a bit-identical
  picture. Coil serves a lower-resolution decode until the art lands, and the
  shot that comes first in a cold run gets it. Warm the cache with a throwaway
  shot before recording a before.
- **The eye can only reach three seats.** `tools/shoot.sh` selects a seat by
  pressing its digit, so every shot is one of 5°, 21° or 34° — and the camera's
  envelope goes to 58°, which is where a procedural surface aliases and where
  iteration 6's whole defect lived. Driving an orbit (a pointer drag on the
  felt, which `MatInput` already understands) would close it, and it is the
  cheapest remaining upgrade to the loop's own perception.
