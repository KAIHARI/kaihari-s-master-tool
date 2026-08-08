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

2. **Pick one thing.** From the ranked backlog in `docs/FIDELITY.md`, from
   `docs/AAA.md`, or from what the look just showed — in that order of
   preference, because the first two are already argued for. One thing. An
   iteration that lands two changes cannot tell you which one worked.

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
| It is affordable | `FrameProbe` (three taps on the life-point total) still inside budget; no new per-card per-frame allocation |
| It is the house style | `docs/DESIGN.md` — and if the change argues with the handbook, the handbook gets the amendment *first*, in its own commit |

The frame budget is the one that will bite. Sixty cards is the working number,
and a per-card layer or a per-card blur is the class of change that has to be
measured before it enters a signed build, not after.

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
