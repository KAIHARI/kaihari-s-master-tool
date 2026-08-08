# The fishbowl loop

An autonomous loop pointed at one thing: making the play stage — the fishbowl —
as convincingly physical as a canvas and some arithmetic can make it.

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

2. **Pick one thing.** From `docs/FIDELITY.md` if it exists, then `docs/AAA.md`,
   then whatever the look just showed — in that order, because the first two are
   already argued for. One thing. An iteration that lands two changes cannot
   tell you which one worked.

   (`docs/FIDELITY.md` is the ranked technique backlog: a literature survey of
   what a canvas-only renderer can do about light, shadow, material, optics and
   surface, mapped onto the functions that already exist. Until it lands,
   `AAA.md` is the list and it is a good one.)

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
  them costs the desktop target. The renderer is arithmetic in `:core` reaching
  the screen through one `graphicsLayer` and a canvas. A runtime shader is not
  common code either: it is an `expect`/`actual` seam with a plain-draw
  fallback, and it is a proposal with a cost, not a free assumption
  (`AAA.md` #99).
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

Four gates. A change passes all four or it does not ship.

| Gate | How it is checked |
|---|---|
| It is visible | The before/after shots differ in the way step 3 predicted |
| It is true | A `commonTest` names the claim; `:core:jvmTest` green |
| It is affordable | `tools/shoot.sh --budget=120` before and after — the **ratio**, not the milliseconds; and no new per-card per-frame allocation |
| It is the house style | `docs/DESIGN.md` — and if the change argues with the handbook, the handbook gets the amendment *first*, in its own commit |

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
