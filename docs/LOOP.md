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
| Window | exists, and **has never been visible** — see iteration 1 |
| Sun outside | nothing. No sky, no ground, no light shaft |
| Floor | a flat plane, barely in frame |
| Wall | 6px of it visible at the table seat |
| Millennium Puzzle | flat gold facets, no material |
| Lamp | flat cream, no material, casts no shadow |
| Bed | does not exist |
| Bookshelf | does not exist |
| Room shadows | nothing in the room casts one (`AAA.md` #61d) |

Two of those — the window and the wall — are blocked on the same thing, and it
is the first real obstacle: **the board fills the stage vertically**, so there
are six pixels of wall at the table seat and no room for anything behind the
desk. Every item in the second half of that table needs the camera to be able to
*see the room*, which means re-framing. That is the next foundation and it is
bigger than any single object on the list.

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
- **The room is capped at 6px.** See iteration 1. Blocked on kai.
- **The eye can only reach three seats.** `tools/shoot.sh` selects a seat by
  pressing its digit, so every shot is one of 5°, 21° or 34° — and the camera's
  envelope goes to 58°, which is where a procedural surface aliases and where
  iteration 6's whole defect lived. Driving an orbit (a pointer drag on the
  felt, which `MatInput` already understands) would close it, and it is the
  cheapest remaining upgrade to the loop's own perception.
