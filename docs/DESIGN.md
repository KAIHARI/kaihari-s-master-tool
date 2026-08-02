# kai's master tool — design handbook

The rules this app is drawn by. When a decision is not covered here, decide it
the way the nearest thing here was decided, then write it down.

One warning before anything else: **"material" in this document means physical
material — ink, card stock, light — and never Material Design.** The app is
built on Material 3 components because they are what Compose ships, and every
one of them is restyled on the way in. If something looks like stock Material,
that is a bug in this handbook's application, not a style choice.

---

## 1. What the app is

A deck builder that behaves like a desk with cards on it. Three convictions
follow from that, and everything else in this document is downstream of them:

1. **The deck is the interface.** Chrome is what you spend to see cards, and
   every dp of it is a dp the cards do not get. Controls earn their space or go
   in a menu.
2. **Nothing lies about the deck.** What is on screen is the deck as it is
   stored. A view may add space, colour or emphasis; it may not reorder,
   rename or invent. If a view would need to rearrange the deck to look good,
   the design is wrong.
3. **Only what you touch is alive.** Tilt, lift, sheen, sound and colour-as-
   light belong to the thing under the finger. Everything else holds still.

## 2. Ink and light

**True black, sharp white.** The stage is `#060608`; surfaces lift by luminance
alone (`#0E0E12`, `#1A1A21`, `#26262F`); separation is a hairline, never a
shadow. Light mode is the exact inversion with the same hues.

Colour appears for exactly two reasons. If a colour is doing neither, delete it.

- **Colour as meaning.** Card type, legality, deck section, group identity.
  Semantic colours are fixed and never decorative: `Danger #FF4D4D` (forbidden,
  illegal), `Warning #FFB020` (limited, caution), `Success #2CE08B`,
  `Info #39B8FF`.
- **Colour as light.** The six-hue prismatic ramp — red, amber, green, cyan,
  violet, magenta — is white light through a lens. It fringes what is being
  interacted with (`ui/theme/Prismatic.kt`), and it is the palette user-defined
  groups draw from. Groups take green, cyan, violet and magenta before red and
  amber, because those two already mean *illegal* and *warning* everywhere else
  and a group wearing them reads as a problem.

**Solid, not translucent.** A tint over a card is a colour you cannot name and a
card you cannot read. When something must be coloured, colour the space around
it — the frame, the gutter, the edge — at full strength. Translucency is for
light effects (sheen, fringe), never for identity.

**A fact gets one colour, forever.** Card type and legality were coloured long
before any lens existed — the statistics panel's type split, the ban badge in
the corner of a card. When a new view shows the same fact it takes the colour
that fact already has (`KeyPaint.Tone`), never a fresh one, or the screen ends
up with two different reds meaning one thing.

**Six hues is a hard limit, so colour never carries a name alone.** Groups also
carry a two-letter mark (`GroupMarks`), rendered identically on the deck, in the
legend and in menus. Reading beats hue-matching at arm's length.

## 3. Type

**Archivo** throughout — a grotesque with punchy caps and solid numerals, which
is most of what this app sets. Bundled, OFL.

**Inter Medium** for the wordmark only, set lower case at natural tracking:
`kai's master tool`. Helvetica Neue is the reference and cannot ship (Linotype's,
and absent on Android), so the closest free neo-grotesque travels with the app
instead. Nothing else uses this family; swapping in a licensed Helvetica Neue is
a one-line change in `wordmarkFamily()`.

Scale is tuned for arm's length on a 14-inch tablet: larger than Material's
defaults, with tight tracking on headings so long card names stay on one line.
Labels are `labelMedium` and below; a control that needs bigger type is a
control that needs less text.

## 4. Geometry

**Square, or nearly.** Radii: 2dp on chips and marks, 4dp on cards, 6dp on
panes, 8dp on sheets. Nothing rounder. Cards are rectangles and a builder full
of pill shapes fights its content.

**Spacing is a scale, not a feeling:** 2, 4, 6, 8, 12, 16, 24. Inside a
component use the small end; between components use the large end.

**The card is 59:86**, always, everywhere, at every size. Never crop, never
letterbox, never stretch.

**Hairlines separate; shadows lift.** A 1dp `#26262F` line is the default
boundary. A shadow means the thing has left the surface and is in your hand.

## 5. Density and the fitter

Layout is solved, not negotiated. `core/layout/DeckFit.kt` sizes all three deck
panes in one pass:

- **Row widths are the input** — ten across for the main deck, fifteen for the
  extra and side, because that is how a decklist is read.
- **Row counts follow from the deck**, with the main deck sized for forty from
  the start so nothing reflows until the deck outgrows it.
- **The one free variable is the width the whole stack is drawn at**, solved so
  the three panes exactly spend the height available. Leftover width is
  negative space *around* the centred stack, never a margin inside one pane.

Two rules follow, and both have been learned the hard way:

- **Anything the layout spends must be declared to the fitter.** A bar that
  appears without being counted is height the cards were promised and did not
  get, and cards end up out of their box.
- **Card size may depend on the window and the deck's size. Nothing else.** Not
  on which mode is open, not on how many groups exist, not on how a partition
  falls. A size that changes when you assign a card is a size that flickers
  while you work.

## 6. Motion

**Springs, never curves.** `core/motion/Spring.kt`: `Snappy` (520) for snaps and
returns, `Bouncy` (380, ζ 0.62) for lifts and lands that should feel like mass,
`Calm` (120) for scene moves. Compose's `spring()` with the same constants for
anything simple enough not to need the frame loop.

**One loop, not one per object.** The sanctioned pattern for many moving things
is a single `withFrameNanos` loop with bulk state in plain lists and per-object
state read *inside* `graphicsLayer` — see `EasterEgg.kt` and `ui/play/StageCard.kt`.
Reading animated state in a composable body recomposes the tree sixty times a
second; reading it in a layer does not.

**Motion explains a change; it never announces one.** If nothing about the
deck changed, nothing moves. Staggers are for showing structure — pieces of a
deck arriving in order — and are measured in tens of milliseconds, not hundreds.

**Fake 3D only.** Perspective via `graphicsLayer` (rotationX/Y, cameraDistance):
one tilted parent plane for tables, flat overlay springs for anything that lifts
off. No 3D engine, ever.

## 7. Materials

The physical feeling to reproduce is handling a single card.

- **Tilt** toward the pointer or the pressing finger, ±7°, on a 480-stiffness
  spring.
- **Lift** with a shadow while held, and a 5% grow — the only place anything
  scales.
- **Sheen**: a soft specular opposite the tilt, screen-blended, drawn only while
  a pointer is on the card.
- **Sound**: short, quiet, and only for things a hand does — lift, set down,
  slide, shuffle, deal. The pickup is a soft pitched tap (55ms, fast attack,
  smooth tail), not a click. Synthesis lives in `tools/sounds/`.
- **Haptics** follow sound, on the same toggle.

Idle things cost nothing: springs run only while touched.

## 8. Components

- **Card tile** — 59:86, 4dp radius, 1dp hairline. The hairline is dropped when
  the card sits inside a drawn region, because the region's edge is the card's
  edge and two lines a millimetre apart read as a printing error. A solid ring
  means *chosen*; the turning prismatic ring means *this is the one you asked
  for* and is reserved for reveals.
- **Pane header** — 28dp, one line: colour bar, name, count against its legal
  bounds, then controls, then collapse. Icon buttons are 26dp, not Material's
  48dp.
- **Bars** (legend, draft editor) — fixed height, declared to the fitter,
  horizontally scrollable rather than wrapping.
- **Chips** — 30dp, 2dp radius, label in `labelMedium`.
- **Sheets** — for things that are about the list rather than about a card.
  Anything requiring you to look at the deck while you do it must not be a
  sheet.

## 9. The breakdown, as a worked example

The rules above decide it completely, and it took four attempts to see that.

**A lens is a partition, and the partition is a parameter.** `core/deck/DeckLens.kt`
hands the layout a `LensKeying`: a list of keys and, index-aligned with the
section, which key each card falls in. Four of them ship — Deck (none), Roles
(the user's own groups), Archetype and Copies — and the drawing code cannot tell
them apart. That split is the point. A breakdown wired to the user's own labels
can only ever show back the bookkeeping they already did; one pointed at facts
the card database already knows has something to say about a list imported ten
seconds ago, which is when a reading is worth most.

**Paint says what kind of difference it is.** A key is painted `Hue` — nominal,
off the prismatic ramp, *these are different* — or `Grey` — ordinal, read as a
scale — or `Tone`, a fact the app already agreed a colour for. Copies is grey,
brightest for singletons, because the singleton is the card you are looking for.
Archetype takes hues 2–5 and never 0 or 1, which mean illegal and warning
everywhere else. Type and Legality are tones.

**A reading is worth drawing because of the number next to it.** "Twelve
starters" is a fact about a list; "twelve starters, 89%" is an argument about a
deck, and the ratio was only ever being chosen to move that number. Every key
carries its exact opening rate, hypergeometric over the same counts the blocks
are drawn from, so the bar can never disagree with the deck beneath it. It never
rounds to a certainty it does not have: 99.7% reads `>99%`, because `100%` is a
promise.

**A question is stated once and kept.** Goals live in the deck file beside the
groups they are written in terms of, keyed by id so renaming a role does not
detach the question from it. They sit pinned right of the keys under every lens
— left is how you are reading, middle is what that reading found, right is
whether the deck does what you asked. A question you have to rebuild from memory
is one you stop asking.

**The deck is a mosaic that cracks.** 2dp between cards, near enough to touching
that a row reads as one surface. A lens does not open every gutter — only the
ones where two keys meet. A card gives way by 4dp on a side that faces a
different key, and on no other side, so the deck cracks along exactly the lines
the lens draws. The key's colour is then drawn **solid** in the space that
opened, and the block reads as one object with a bold edge.

**Cards with no key have no edges.** The unclaimed part of the deck is not a
group with a shape; it is the surface the blocks are lifted out of, and it stays
flush while they go. This is also the reading: what is in no archetype is the
non-engine, and what is in no role is the part not yet thought about.

**The cell is fixed and the card moves inside it.** `core/layout/CardPlacement.kt`
places a card in the cell the fitter solved for: it gives way on cracked sides,
keeps 59:86 exactly, and never grows. The crack was once applied as padding,
which made every item taller than the fitter had promised and pushed the last
row of the main deck out of its pane — §5's first rule, broken by the mode drawn
on top of it.

What this design refuses, and why:

- **Rearranging the deck to make prettier blocks.** Rule 2. A key whose cards
  are scattered is drawn as several blocks, because that is where those cards
  are — and that reading is itself useful, since it says the deck is not sorted
  the way it is being thought about.
- **Tinting keyed cards.** Rule in §2: solid, not translucent.
- **Labelling blocks with names.** Names go in the lens bar; the deck carries
  two-letter marks, one per block.
- **Making a lens cost card size.** The bar is 30dp, always present, declared to
  the fitter whether a lens is on or not; the crack is bought from the cell.
  Nothing about the deck's geometry changes when a lens does.

## 10. The play stage, as a worked example

The freeform table (`ui/play/`) is the second place where the whole design
argument shows up at once, and it is worth reading before touching it.

**One arbiter, not one per card.** Every gesture on the mat is decided by a
single `MatGestureMachine` in core, driven by a single `pointerInput` over the
whole stage. Compose hit-tests each pointer independently, so per-card gesture
detectors let one finger start a drag on one card while a second finger starts
a *separate* drag on another. Consumption cannot fix that: by the time it
happens there are already two gesture loops that each believe they are in
charge. It also means touch and pointer are two producers of one vocabulary
rather than two implementations, which is how "every gesture ships with both
idioms" stays true structurally instead of by discipline.

**The gesture is decided by time as much as by position.** All four two-finger
gestures are spoken for — tap flips, twist turns to defence, drag takes the
pile, hold opens the menu — so the remaining channels are duration and
stillness. A one-finger hold peeks; a *carried* card held still goes underneath
the card it is over rather than on top. Both are driven from the frame loop,
because a finger that has stopped moving stops producing pointer events, which
is precisely the condition being detected.

**The indicator is the intent, not a picture of it.** `DropTargets` resolves
the finger's position to one `DropIntent`, the highlight draws that value, and
the release commits that same value. There is no second decision at release
time that could disagree with what the user was shown. Every threshold in the
resolver is a pair — harder to enter than to leave — because a single number
makes a finger resting on a boundary flicker between two answers several times
a second, and which one you get is luck.

**Fake-3D, still.** One tilted parent plane via `graphicsLayer`, and a flat
overlay for anything that lifts off it, projected by hand through the same
`StagePlane` the renderer uses so there is no seam. A carried card's *position*
is assigned from the finger rather than sprung toward it — any spring between a
finger and the thing it is holding is lag, and lag on a touch drag is the one
thing that makes a simulator feel fake. Its rotations still spring, which is
where the weight comes from instead.

**Every face-down card has a back.** `CardBack` draws it — the brown field with
either the oval or the spiral — and it is a preference, not a constant. Nothing
in the app draws a blank rectangle where a card back belongs.

## 11. Anti-patterns

- Colour that is neither meaning nor light.
- A translucent wash over content.
- Rounded corners past 8dp, or a pill.
- A shadow used to separate two things that are both on the table.
- Any animation of something that did not change.
- Chrome that grows without being declared to the fitter.
- Stock Material spacing (48dp targets, 56dp fields) inside the deck column.
- A view that reorders, renames or invents anything about the deck.
- A gesture detector attached to a card rather than to the mat.
- A drop indicator computed separately from the drop.
- A spring between a finger and the thing it is dragging.
