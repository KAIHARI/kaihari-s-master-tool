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
state read *inside* `graphicsLayer` — see `EasterEgg.kt` and `GoldfishScreen.kt`.
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

The rules above decide it completely, and it took three attempts to see that.

The deck is a **mosaic**: 2dp between cards, near enough to touching that a row
reads as one surface. Turning the lens on does not open every gutter — it opens
only the ones where two groups meet. A card gives way by 4dp on a side that
faces a different group, and on no other side, so the deck cracks along exactly
the lines the groups draw. A group's colour is then drawn **solid** in the space
that opened, and the block reads as one object with a bold edge.

What this design refuses, and why:

- **Rearranging the deck to make prettier blocks.** Rule 2. A group whose cards
  are scattered is drawn as several blocks, because that is where those cards
  are — and that reading is itself useful, since it says the deck is not sorted
  the way it is being thought about.
- **Tinting grouped cards.** Rule in §2: solid, not translucent.
- **Labelling blocks with names.** Names go in the legend; the deck carries
  two-letter marks, one per block.
- **Making the lens cost card size.** The crack is bought from the gutter, which
  the fitter already knows about.

## 10. Anti-patterns

- Colour that is neither meaning nor light.
- A translucent wash over content.
- Rounded corners past 8dp, or a pill.
- A shadow used to separate two things that are both on the table.
- Any animation of something that did not change.
- Chrome that grows without being declared to the fitter.
- Stock Material spacing (48dp targets, 56dp fields) inside the deck column.
- A view that reorders, renames or invents anything about the deck.
