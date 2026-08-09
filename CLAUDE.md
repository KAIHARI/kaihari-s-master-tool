# CLAUDE.md

This file guides Claude Code (claude.ai/code) when working with this repository.

## Quick Start

**What this is:** kai's master tool — a Yu-Gi-Oh! deck building and tournament
preparation tool, being built into an immersive, 3D-feeling deck building
simulator.

**The project is the cross-platform app in `app/`.** Kotlin Multiplatform +
Compose Multiplatform, targeting Android (landscape tablets, Samsung Tab
S11-class) and desktop (macOS/Windows/Linux) from one codebase. See
`app/README.md` for architecture and build instructions.

**`legacy/kai master tool.html` is the archived original** — the single-file
web tool the app replaces (~36k lines, plain HTML + Tailwind + vanilla JS).
It is kept as a reference for the original vision and its feature set (siding
patterns, shootout mode, sandbox board, PDF export, the prismatic hover
effects). Open it in a browser from inside `legacy/` if you need to study it.
Do not develop it further; maintenance only if something in it is truly broken.

**Key locations:**
- `app/core/` — pure Kotlin logic (models, deck editing, groups, hand odds,
  motion physics, 3D geometry and lighting, haptics, board domain), all tested
  in commonTest
- `app/ui/` — Compose Multiplatform UI (androidTarget + jvm("desktop"))
- `app/androidApp/`, `app/desktopApp/` — platform entry points
- `legacy/` — the archived original tool and its assets
- `ydk/`, `lab.ydkx` — sample deck files (YDKX = YDK + `#ydkx-extended` JSON)

---

## Seeing the play stage without a tablet

`:studio` draws the real `PlayScreen` to a PNG headlessly — real theme, real
dependencies, real card art, a frame clock advanced by hand, no window and no
GPU. It is off by default and ships in nothing (`-Pmastertool.studio=true`), so
CI builds exactly the three modules it always did.

```
tools/shoot.sh                                    # the default contact sheet
tools/shoot.sh --shots=desk-night-seated --keys=n # one shot, after a fresh deal
tools/shoot.sh --budget=120                       # what a frame costs
tools/compare.py shots/before shots/after         # what actually moved
```

A shot's name is its parameters: `desk`/`minimal`, `day`/`night`,
`overhead`/`table`/`seated`. Two runs are bit-identical — the deal is seeded —
so every pixel that moves is a change in the code. It needs the Android SDK
(`:ui` has an `androidTarget`); point `ANDROID_HOME` at one and the script
writes `local.properties` itself.

**`docs/LOOP.md` is the autonomous loop that uses it**: six steps an iteration
takes, four gates a change passes, and a ledger of what has been tried. Read it
before doing fidelity work on the play stage.

## Development Workflow — read this before changing anything

1. **All logic goes in `:core` with commonTest tests.** Run locally:
   `cd app && ./gradlew :core:jvmTest`. This is the only module that compiles
   in a default Claude environment.
2. **`:ui`/`:androidApp`/`:desktopApp` compile ONLY in CI** (Google Maven is
   unreachable from most sandboxes; `settings.gradle.kts` auto-skips those
   modules). Pushing to `main` or any `claude/**` branch runs
   `.github/workflows/build-app.yml`, which is the compile check and APK
   build. **Never trust a piped local exit code** — grep the Gradle output for
   `BUILD SUCCESSFUL`.
3. Every gesture ships with both a touch idiom and a pointer/keyboard idiom.
   Keyboard shortcuts are data in `core/input/ShortcutTable.kt` (layer-aware;
   the help sheet renders the table, so it can never drift).
4. **Finish by shipping it.** See *Ship Every Change* below — the user tests on
   the tablet, so work that is only on a branch is work they cannot see.

## Ship Every Change — standing instruction from the user

The device is the only place this app can really be judged, and a debug APK
cannot install over the signed one. So the default end of a piece of work is a
release, not a branch. Standing permission is granted for all of it — do not
stop to ask.

Every time, in this order:

1. Push the work to the `claude/**` branch and wait for `build-app.yml` to go
   green on all three jobs (core tests, desktop, Android APK). A red build is
   the one thing that stops the rest.
2. Fast-forward `main` onto the branch and push it. Releases build from `main`.
3. Read the current version — `get_latest_release`, or the newest `v*` tag —
   and dispatch `release.yml` on `main` with the **next patch** number
   (v1.2.3 → v1.2.4). Bump the minor instead only when the user asks, or when
   the patch would reach 100: the versionCode formula gives 1.2.100 and 1.3.0
   the same code, so the patch digit must stay under it.
4. Confirm the release actually published — the tag exists and the
   `kai-master-tool-<version>.apk` asset is attached — before telling the user
   it is ready. The signing gate can fail the run *after* the APK builds, and
   "dispatched" is not "shipped".

Note in the release notes when a build changes stored preferences, the schema,
or the deck-file payload, so a surprise on the tablet has an explanation
waiting.

Two things to say out loud rather than silently skip: a change that cannot
reach the device (docs, this file, tests only) does not need a release, and a
version number, once published, is spent forever — there is no reissuing one.

## Release Contract — numbers shipped to devices are permanent

`.github/workflows/release.yml` (manual dispatch with a `version` input, or a
`v*` tag) builds the signed APK and publishes the GitHub release that the
in-app updater installs from. Two hard-learned rules:

- **versionCode** is derived from the version name
  (`100000 + major*10000 + minor*100 + patch`). It must only ever go up;
  v1.1.0 shipped as 281 from a commit-count scheme, which is why the floor
  exists. Never revert to commit counts.
- **SQLite schema version is 3** and can never decrease — devices that
  installed v1.1.0 are stamped at `user_version 3`
  (see `core/.../db/migrations/2.sqm`). SQLDelight derives the version from
  the number of `.sqm` files: adding a table means adding a `.sq` change AND a
  new `.sqm`, and `MigrationTest` must prove upgrade == fresh create. Never
  renumber or delete migration files once a signed build has shipped.
- The APK must be signed with the committed key
  (`androidApp/keystore/kai-master-tool.jks`); the workflow hard-gates on its
  SHA-256.
- Android crashes surface through the built-in crash reporter
  (`MainActivity`): the trace persists and is shown, shareable, on next
  launch. Keep that screen theme-free — it must render when the theme cannot.

## The play stage is now a room — kai's brief, and what it suspends

The DESK scenes are pursuing **photorealism**: *"the entire environment (desk,
window with sun outside, millennium puzzle, floor, bed, bookshelf, etc)
completely photorealistic"*, at the fidelity of a driving simulator. The
constraints below that argue with that goal are suspended **for those scenes**
— true black, colour-only-as-meaning, and no decoration in the room. `docs/LOOP.md`
§"The mandate" is the authority and carries the full list, including what is
*not* suspended (`Scene.MINIMAL`, "nothing idles", the release contract, and a
plain-draw fallback behind every shader).

The playmat is gone: the desk is the playing surface and the zones are routed
into the wood. The board also declines a fifth of the stage's height
(`Scenery.ROOM_ABOVE`) so that there is somewhere for the room to be — it costs
a fifth of the card, on every device, and that trade is the reason a window,
a wall and anything behind them can be seen at all.

## Design Identity (locked in with the user)

**`docs/DESIGN.md` is the handbook — read it before drawing anything.** It holds
the palette, the type scale, the spacing scale, the motion specs, the component
rules and the anti-patterns, with the reasoning attached. What follows here is
the short version; where the two disagree, the handbook is right and this should
be corrected.

- **Swiss + material finish: sharp white on true black.** "Material" means ink,
  card stock and light — never Material Design, whose components are used and
  restyled on the way in.
- **Swiss + prismatic: sharp white on true black.** Colour appears only as
  *meaning* (card types, legality) or as *light* — the six-hue prismatic ramp
  (`MasterToolPalette.Prism`) shown as chromatic-aberration fringes on things
  being interacted with. Light mode is the exact inversion, same colours.
  Primitives live in `ui/theme/Prismatic.kt`; use them sparingly — fringing
  everything reads as decoration, fringing the thing under your finger reads
  as light.
- **Typeface: Archivo** (bundled, OFL; expanded cut for display moments).
- **Tactility lives on things the user interacts with** — tilt, lift, sheen,
  quiet card sounds, haptics. No decorative clutter, no skeuomorphic
  inefficiency. The physical feeling to capture is handling a single card.
- **Motion = springs.** `core/motion/` (`Springs`, `PosePhysics`) with the
  one-`withFrameNanos`-loop recipe (see `EasterEgg.kt` and `ui/play/StageCard.kt`
  for the sanctioned perf pattern: bulk state in plain lists, per-object state
  read inside `graphicsLayer`).
- **A shader seam, since the target became photographic.** kai's brief is the
  fidelity of a driving simulator, and a surface stops reading as a fill colour
  only when its normal varies *per pixel*. `ui/gpu/StageShader.kt` is an
  expect/actual over Android's `RuntimeShader` and desktop Skia's
  `RuntimeEffect`. It compiles to null rather than throwing — Android below 33,
  a refusing driver, a bad shader — so **every caller keeps a drawing that works
  without one**. See `docs/DESIGN.md` §6 for the four rules, including the sign
  convention that made the first one invisible.
- **Real geometry, no engine.** `core/render/` is a small, tested renderer:
  `Rot3` (the same Euler angles `graphicsLayer` rasterises with), `CardSolid`
  (a card has a thickness and six faces), `Shading` (Lambert + Blinn-Phong,
  with a specular pool that *moves*), `Shadows` (cast by projecting every
  corner along the light), `StageRig` (one key, one fill, one eye). It reaches
  the screen through `graphicsLayer` — which is a real perspective-correct
  quad — and a canvas, joined by `StagePlane.flatten`. **No 3D engine, ever:**
  none reaches KMP common code and all of them would cost the desktop target.
- **Height is notation, and `sin(tilt)` is its exchange rate.** Every z on the
  stage reaches the screen multiplied by it, so a physically honest forty-card
  deck is four pixels and a diagram. `CardSolid.pileDepth` exaggerates and
  saturates; a pile's side is ruled into its cards and leans, because those two
  survive the angles its height does not. The mat lies on a table drawn as a
  slab, because gradients on true black are a mat floating in a void.
- **The stage has rooms, and one of them is an exception.** `Scene.MINIMAL` is
  the handbook's stage and does not change. The **Desk** scenes — a desk lit by
  a window in the day and a lamp at night — are a different contract, argued in
  `docs/DESIGN.md` §11 rather than smuggled in: they may hold decoration, they
  are chosen rather than arrived at, and *nothing idles* in either of them. Two
  rules hold the whole thing up. Nothing in a scene may stand over the mat,
  because cards are sorted in the composable tree and the room is painted
  beneath all of them. And night never lowers the rig's ambient below 0.55 —
  see `Tone.veil` for the numbers — so its darkness comes from the room falling
  away rather than from the cards going dim.

## Roadmap State

Shipped: the full deck builder (drag-and-drop, exact consistency calculator,
per-card tactility, sound/haptics, 3D card inspect), the zone duel table, and
the freeform play stage that replaced the goldfish screen.

Two parts of the builder are worth knowing before touching them, because both
replaced an earlier design that looked reasonable and was not:

- **Layout is solved, not negotiated.** `core/layout/DeckFit.kt` sizes all
  three panes in one pass: row widths are the input (main 10, extra/side 15),
  row counts follow from the deck, and card size is the single free variable.
  Per-pane auto-fitting against divider-dragged heights is what put cards out
  of bounds; do not go back to it. Anything the panes spend on chrome must be
  declared to the fitter or the cards pay for it.
- **The breakdown never moves a card.** The deck is a mosaic (2dp gutters) that
  cracks open only where two groups meet — `BreakdownLayout.plan` says which
  sides of a card face another group, `GridRegion` traces the blocks, and the
  group's colour is drawn solid in the space that opened. A grid index is
  always a deck position, so a drop always means insert. Assignment is a
  selection gesture: `GroupDraft` in core, tapped out on the deck itself.
  Rearranging the display to make tidier blocks was tried twice and rejected
  by the user both times; see `docs/DESIGN.md` §9.

The breakdown is now a **lens** (`core/deck/DeckLens.kt`): the partition is a
parameter, so the same machinery draws the user's roles, the deck's archetypes,
its type split, its copy counts and its banlist exposure. Every key reports its
exact opening rate (`core/hand/LensOdds.kt`), and the consistency question is
stored *with the deck* as a `HandGoal` rather than rebuilt in a sheet each time.

Both play surfaces now exist, and they answer different questions:

- **Table** (`ui/table/DuelTableScreen.kt`) is the zone board over
  `core/board/BoardState.kt`. Cards go in the five monster and five
  spell/trap zones, and the question it answers is where a card is *allowed*
  to go. Geometry is solved in core (`core/layout/BoardLayout.kt`), the way
  `DeckFit` is, and not negotiated in the composable.
- **Play** (`ui/play/`) is the freeform table that replaced the goldfish
  screen: cards go anywhere, stack on each other, and can be set face-down.
  Its domain is `core/board/PlayField.kt`, and everything about *what a
  release means* is core and tested — `DropTargets` resolves the finger's
  position to an intent, `DropCommit` carries it out, and the indicator the
  user sees is that same value, so the table cannot promise one thing and
  do another.

Four rules the play stage would be broken without, each of which was a bug
first:

- **One gesture arbiter for the whole mat**, in core
  (`core/mat/MatGestureMachine.kt`), driven by one `pointerInput`. Per-card
  detectors let one finger start a drag on one card while a second starts a
  separate drag on another, and consumption cannot fix that after the fact.
- **Fingers on a card move the card; fingers on the felt move the camera.**
  The whole control scheme, and it has to stay sayable in one line. The split
  is made once, on the press, by `claimForCamera` when the hit test finds
  nothing; from there the gesture cannot become a drag, a peek or a menu. One
  finger orbits, two also pinch. The table has almost no affordances drawn on
  it, so it has a guide — `core/input/MatGuide.kt`, data rather than prose,
  rendered by the button on the bar and held to both-idioms-present by a test.
  The one exception is the two shuffle marks (`core/layout/MatControls.kt`),
  argued for there and in `docs/DESIGN.md` §10; a third needs its own argument
  rather than their precedent.
- **Both of its clocks report to `MatPilot`.** Pointer events and the frame
  loop each produce gesture events, and both must act on the same memory of
  what the press landed on. `onTick` called for its side effects, with its
  return value dropped, is the shape of that bug: peek and the two-finger
  menu compute correctly and then vanish.
- **The gesture belongs to the finger that started it**, but does not end the
  instant that finger lifts — the second finger of a tap is a frame behind.
  It opens a grace window instead; a hand left resting on the mat loses the
  gesture when the window closes.

**Any pile can be searched.** Tap one and it spreads across the board —
`core/layout/PileFan.kt` solves the geometry, and the cards never change size
because a search shows you the cards that are on the table. Take one by
dragging it anywhere (every existing drop rule applies unchanged) or tap it to
send it to your hand. The deck fans in its own order and closing it shuffles,
which is correct by the rules. This was `docs/TABLE.md` §3's "hole", and the
whole of it at the input end was that the hit test returned
`DragOrigin.Pile(slot, 0)` — the domain had taken an arbitrary index since it
was written.

The room is seventeen pieces: a floor, the desk, four wall pieces around a
window opening, the pane, seven of joinery around it, and three for the lamp. `docs/AAA.md` #62 was the brief —
*"there is a room past it. Dark, out of focus, present."* — and #16 and #17 are
what made day and night two rooms rather than two colour grades: a `Light` may
now have a **position**, a **radius** and a **distance**, so shadows diverge
from a point, the far corner of the table is dimmer than the near one, and a
window's edge is soft where a lamp's is hard.

Four things about it are load-bearing, and three of them were bugs first:

- **A light with no place is a no-op to the bit.** Every new term returns a
  literal before touching a float when `position` is null, which is why
  `GoldenStageTest` is green without being re-recorded across a release that
  changed how every surface is lit.
- **The lamp's height is solved, not chosen.** The shipped night key's
  horizontal-to-vertical ratio *is* how long a night shadow is per unit of
  height, so the lamp stands where the ray to the middle of the table has
  exactly that ratio. Its mast is then foreshortened five to one, because an
  honest desk lamp is off the top of the picture — the foot and the light are
  exact and `SceneryTest` pins the compression.
- **Paint order is a topological sort over a separating axis.** Sorting boxes
  by nearest-corner depth puts a 511px wall after a 241px lamp and paints it
  over the top. `ScenePainter` finds an axis that separates each pair — and
  never asserts an order for a pair no ray connects: axes that disagree mean no
  answer, an eye inside the gap means no answer, and pieces that do not overlap
  on screen are not compared. All three matter, and each was a cycle. The order
  itself is a topological walk, because adjacent swaps cannot sort a partial
  order at all.
- **The felt is lit by the rig now.** It was a gradient aimed by a *direction* —
  the one surface that could disagree with the shadows on it. Its highlight is
  the lamp's mirror image, so it slides toward you as you sit down.

One thing was built and cut in the same release, and the reason is worth
knowing before rebuilding it: a **patch of daylight on the desk**. It was drawn
as a multiply, which is correct — but the rig already lights the whole desk from
the window's direction, so the surface was at full brightness before the patch
went on and every ring of it came out *darker* than the wood it landed on. A
stain, not light. Fixing it means splitting the day key into sky and sun and
shading the room with the sky alone, which changes the brightness of the entire
day room. `docs/AAA.md` #61c has it.

**The room has one thing in it that answers a finger.** The Millennium Puzzle
stands on the bare left of the desk, opposite the lamp: a truncated pyramid
apex-down (`CardSolid.slab` gained a trailing `backScale`, bit-identical at 1,
which is why `GoldenStageTest` never moved). Tap it and it turns a third of a
turn and rises. Four things about it are the pattern a second prop inherits:

- **A prop is a pose, not a `ScenePiece`.** Furniture is solved twice a day and
  remembered; a moving thing cannot live in a value that is deliberately
  recomputed. `Puzzle.stirred(layout, turns, lifted)` is a pure function of two
  numbers the screen owns, so what is drawn, what is touched and where it stands
  cannot come apart.
- **It may spin and rise, and may not tumble.** A body hangs along the *stage's*
  z, so a turn about that axis is bit-exactly the turned solid, and a tilt would
  leave the body hanging vertically while the face turned — a fraction of a pixel
  on a card, the entire silhouette on a hand's width of pyramid.
- **Two shapes: a box to sort it, a set of faces to draw it.** It joins
  `ScenePainter`'s order as `Puzzle.reach` and is drawn from its live pose, which
  is what lets the painter stay ignorant of any shape without axes. Painting it
  last instead assumes a camera in front of the table, and yaw is free: past
  about 145° you are behind the room's own wall.
- **Hit-tested where it appears, not where it stands.** Against the flattened
  silhouette: at the table seat the middle of its top face is 102px from its own
  foot, against cards 104px wide.
- **The camera claims the gesture last.** A prop is the third thing that is
  neither a card nor the felt, after a shuffle mark and the inside of an open
  fan, and like both it is taken out before `claimForCamera` — asked last of the
  three, because the table's own affordances outrank an ornament beside it. It is
  deliberately *not* in `MatGuide`: an easter egg's value is that nobody told you.

Next for the room: `docs/AAA.md` #61d is the shadow question — nothing in the
room casts one, on purpose, and that is a decision to revisit as a set rather
than to bolt onto one object. A second prop needs only to share no volume with
anything, which is what `PuzzleTest` measures and what the sort needs.

Next: attaching as material is reachable in the domain and not yet by gesture
(`DropIntent.Attach` needs an idiom that is not already spoken for). After
that: the deck **showcase** stage, and play polish (deal-origin projection,
stack shuffle/cut). `docs/TABLE.md` §5 is the ordered list of everything else,
and its first three — tokens, a scrubbable history, card text on the peek —
are now the cheapest things on it.

**Explicitly deferred by the user — do not build on the legacy designs:**
siding patterns and shootout mode will be redesigned from scratch in a future
run. The only obligation today is that `YdkCodec` keeps round-tripping the
opaque `#ydkx-extended` payload (it does — `DeckGroupsCodec` preserves
unknown keys byte-for-byte).

## Multi-Team Trigger

When the user starts a prompt with **"mt"** or **"mt:"**, they want a
multi-agent team: strip the prefix, create a team, break the task into 2-4
subtasks, spawn 2-3 general-purpose teammates, coordinate, report back.

## Data Formats (unchanged from the original)

- **Card**: YGOPRODeck API v7 shape (`core/model/Card.kt`); ids are Konami
  passcodes.
- **Deck**: ordered multisets of ids per section (main 40-60, extra/side
  0-15, 3 copies across the whole deck) — order round-trips to `.ydk`.
- **YDKX**: plain YDK + `#ydkx-extended` + one JSON object. The app owns the
  `groups` key; everything else (legacy `sidingPatterns`, `notes`,
  `configurations`) passes through untouched.

## Debugging

- Core logic: write a failing commonTest first; `./gradlew :core:jvmTest`.
- UI on Android: the in-app crash reporter shows and shares the trace.
- Desktop: `./gradlew :desktopApp:run` (needs Google Maven access).
- Preferences are one JSON document in SQLite (`UiPreferences`); adding a
  preference is a field with a default, never a schema migration.
