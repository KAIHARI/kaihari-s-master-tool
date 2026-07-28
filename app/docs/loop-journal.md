# Loop journal

A running record of an autonomous improvement pass on `app/`, working towards a deck
builder that feels like arranging real cards on a table rather than editing a list.

Each entry says what changed, why, and how it was verified. Newest last.

## Why this file exists

`androidx.*` is served only from Google's Maven, which is unreachable from the environment
this work is being done in. `:core` compiles and its tests run locally; `:ui`,
`:desktopApp` and `:androidApp` do not compile at all. **CI is the only compiler that ever
sees Compose code here.**

Two working rules follow from that, and they are the reason the commits are shaped the way
they are:

1. **Arithmetic lives in `:core`.** Foil angles, selection ranges, autoscroll curves, grid
   geometry — anything that can be a number is a tested pure function, so the Compose layer
   only ever binds values that are already known to be right.
2. **Push small and often, and never start the next thing while CI is red.** A large
   never-compiled Compose diff is the failure mode all of this exists to avoid.

## Verifying locally

```bash
cd app
./gradlew :core:jvmTest -Pmastertool.android=false
```

---

## 0. Baseline

`:core:jvmTest` green — 249 tests across 19 classes. Working from
`claude/deck-builder-autonomous-loop-demcdc` at `8448e92`.

Confirmed by probe, not assumption:

| Host | Result |
|---|---|
| `repo.maven.apache.org`, `plugins.gradle.org` | reachable |
| `dl.google.com` | 403 at the proxy — and `androidx.*` is not mirrored on Maven Central |
| `db.ygoprodeck.com` | 403 — no live card data, so tests use `TestCards.kt` fixtures |

CI turns a push around in under a minute, which is fast enough to treat as the
compiler for everything in `:ui`.

---

## 1. The table

Cards in a deck pane touch. The tool this replaces had a grid gap of zero and a
3px card radius, and that is most of why its decks read as an arrangement rather
than as a grid of tiles.

Four HTML mockups settled the edge treatment before any Kotlin was written: at
zero gutter and no border the card frames merge into a slab, so the separation
comes from the card's own printed edge — a near-black hairline — plus a lit top
line, which is what makes a block of them read as objects under a light rather
than as a mosaic.

Resizing a pane now pins its grid first, so the cards change size and the layout
does not rearrange around them. Panes are drawn as a mat: woven grain, vignette,
top sheen, and a binding in the section's own colour, which became load-bearing
once no background showed between cards.

**Learned:** closing the gutter can cost a column. Wider cards are taller, and
past about thirteen columns that growth outweighs the row gaps removed, so a
near-full deck needs one more column with no gutter than with one. The opposite
of the invariant first written, and now a named test.

## 2. Foil

The signature effect from the original, rebuilt: a holographic band that swings
with the pointer, a specular highlight opposite it, and a hue running pink to
cyan with the tilt.

The arithmetic is `core/visual/FoilMath.kt` with 13 tests, which is what makes
it safe to bind blind — the constants come from a stylesheet and a `mousemove`
handler, and a sign error would put the reflection under the cursor instead of
across from it. Tilt had to be renormalised on the way over: the original scaled
it by raw pixel offsets, so a 280dp preview would have tilted four times as far
as a 68px deck tile.

Touch has no hover, so a moving light — the same function walked by a virtual
pointer — carries the effect where there is nothing to follow. Deck tiles are
deliberately excluded: forty animating layers to show an effect too small to read.

## 3. Drag

Edge autoscroll, which was the last gap `README.md` admitted to, on a squared
ramp so entering the band while aiming at the last row does not drag the pane out
from under you. It is driven by a frame clock rather than by pointer movement,
because holding a card still against an edge is the gesture.

The long press now shows a filling ring. It was four hundred milliseconds of no
feedback at all, which is indistinguishable from a press that is not working. It
fires a haptic on completion but opens nothing — the menu still opens on release,
which is what keeps holding-then-dragging possible.

Several cards can be picked up and carried together, entered from the long-press
menu rather than a modifier key so it exists at all on a tablet. Both kinds of
range are offered: a reading-order run, and the rectangle, which is what you want
when the thing you arranged is a column of ratios.

The invariant behind it is enforced by construction — assigning a deck clears the
selection, because a selection is a set of positions into that deck. Eight places
write a deck; one forgetting would leave a selection pointing at different cards
than the highlighted ones, and the next thing done to it moves real cards.

## 4. Type

Instrument Sans, JetBrains Mono, Tektur — all OFL, all bundled. The original's
stylesheet asked for `'Helvetica Neue Haas Grotesk'` and never loaded it, so
every install fell back to Inter, which this project's own guidelines rule out
by name. Six candidates were set in real deck-builder chrome and compared before
picking; Instrument Sans won on being tight, which matters when card names run to
"Original Sinful Spoils — Snake-Eye" and a Main pane is thirteen columns wide.

**Stale comment found:** `Theme.kt` said a font would mean taking back the
Compose resources dependency. That dependency had already come back for the chibi.

## 5. Surfaces

Four themes, each a Material scheme *plus a mat* — cloth colour, two thread
colours, and lighting. That second half is why the original's Classic theme never
became the table it was reaching for: it was six hex values and no surface.

Daylight inverts the *lighting*, not the palette. Sheen up an order of magnitude,
vignette nearly off, because a bright surface is lit from the room; a dark theme's
numbers negated gives grey paper with a dirty rim.

Semantic colours stay constants. A deck over sixty cards is wrong in every theme.

## 6. Motion

Cards move now instead of appearing somewhere else. The fix was not an
animation, it was the key: tiles were keyed by position, so inserting a card at
index three changed the key of every tile after it and the grid saw the whole
tail replaced rather than moved. Keying by *which copy* a tile is leaves every
other identity untouched.

The dragged card casts a contact shadow — a radial gradient rather than
`Modifier.blur`, which is a no-op below Android 12 and this app runs from 26.

## 6b. The one red build

CI went red once, on the commit that made twenty-three theme-blind colours
follow the theme: `CardTile` read `LocalMasterToolColors` and never imported it.
One line, four minutes, and entirely avoidable.

`tools/check-imports.py` now catches that class of mistake locally. Getting it
*quiet* was the whole job — the first version reported thirty-eight suspects,
all wrong, because "the Main Deck" in a KDoc paragraph looks exactly like a use
of `Deck`. It now ignores comments and string bodies, names the file binds
itself, anything after a dot, and anything already imported under that name from
elsewhere. Removing the import again makes it report that and nothing else.

**Run it before every push:**

```bash
cd app && python3 tools/check-imports.py
```

## 7. Siding

Both halves of the domain, ahead of any UI.

The engine pairs out and in by position and writes the arriving card at the index
the departing one occupied. Appending would have been shorter and would quietly
destroy an arrangement its owner chose — which is the premise of everything else
here. A plan that no longer fits still produces a deck and names what it could
not do, because there is a clock running between rounds.

The codec **merges** rather than re-encodes. The `#ydkx-extended` payload is not
this app's document: the desktop tool also writes a background gradient, a
category, tags, a last-used stamp and the opponent's full decklist there, and
decoding to a data class and back would have deleted all of it.

## 8. Everything that was already there

Four things the code knew how to do and nothing called.

**Enter adds the top match**, leaving the query alone — so three presses is
three copies without a hand leaving the keyboard. The fastest way to build a
deck either program has, and the original had it.

**The pool has a density control.** `searchColumns` had been stored, clamped and
read since the pane was written; nothing ever set it, so the only reachable
value was the default.

**The statistics chips browse the pool.** Every one was an `AssistChip` with an
empty `onClick` — the exact pattern the card inspector had already been fixed
for, in the one panel that was not.

`CardIndex.suggest` was deleted rather than wired up: the pool below the search
box is already the live result list, so a dropdown of the same cards would be
the same query drawn twice.

## 9. Weight

A dragged card pinned exactly to the pointer is where the illusion breaks —
nothing with mass tracks a hand perfectly. It trails on a spring now and swings
into the direction of travel, both from the same number so there is no second
opinion about how fast the drag is going.

Critically damped, with damping derived from stiffness rather than a second
dial, because a card that oscillated around your finger would be worse than one
that never lagged. The timestep is capped: a spring given a large enough step
does not lose accuracy, it *diverges*, and backgrounding the app mid-drag would
have thrown the card off screen permanently. Tested at 30fps as well as 60 —
desktop and a tablet do not agree, and a spring tuned at one that rings at the
other is a bug nobody would reproduce.

## 10. Dealing

Opening a saved list put forty cards on screen at once, the one moment here that
could not happen at a table. They arrive in a wave now, dropped onto their places
rather than faded in — a card that arrives by becoming opaque has not come from
anywhere.

One animation per pane; each card derives its own progress from its index. The
property worth testing is that *every* card has landed by the end: get that
wrong and the last few snap into place when the animation stops, which is worse
than not animating at all.

## 11. The pool

The last undesigned third of the screen. Same cloth as the mat, turned over:
shadowed along the top instead of lit there, because the panes are a surface
things are laid on and the pool is a box things come out of. Its gutter closes
to four rather than to zero — cards in a box have gaps, cards in an arrangement
do not, and that difference is what stops it reading as a fourth deck section.

## 12. Testing the untestable module

`:ui` had no tests and is the module that does not compile in this environment
at all — everything in it was written without a compiler and verified only by CI
agreeing that it parsed.

It turns out not to need a UI harness. The state holders are plain objects over
Compose's snapshot system, which runs with no window and no composition, so
`DeckBuilderState` can be constructed against a real in-memory SQLite database
and driven directly. Fifteen tests, running in CI on the desktop target, over
exactly the things most recently written blind: selection ranges and blocks,
that editing a deck drops its selection, that a group moves as one and is one
undo, and that importing a `.ydkx`, siding, and exporting still carries the keys
this app does not understand.

**Three reds to get there, each worth writing down:**

1. `:core` exposes Ktor as `implementation`, not `api` — so naming an
   `HttpClient` in a `:ui` test asks for a class that is not on its classpath.
2. `runTest`'s scope queues work on a scheduler that only advances when the test
   says so, so every assertion arrived before the thing it asserted about. The
   state holder gets an unconfined dispatcher instead.
3. **The best one.** `repeat(12) { addCard(sameCard) }` does not build a deck of
   twelve cards — the copy limit stops it at three. A test that wanted a
   four-column grid was working with a single row. The app was right and the test
   was wrong, which is the outcome you want from a first test run.

## 13. Recording a plan by doing it

Siding was read-only. The obvious completion is an editor with four lists and a
card picker; this is the other one.

Nobody writes a swap down and then performs it. They move cards between the Main
and Side decks until the matchup feels right, and *that* is the plan — so
recording it is a diff, not a form, and everything it needs is already true
before you open the panel.

Compared by count and only on the Main deck. Two decks holding the same cards in
a different order are the same decklist as far as siding goes; the arrangement is
a separate thing, and a plan recording "moved card seven to slot twelve" would be
recording the wrong edit. Cards that left the Main deck went to the Side by
definition, so comparing both would let the two halves contradict each other.

The property that makes it worth anything is tested twice — in `:core` and again
through the state holder: what the diff produces must be a plan that reproduces
what was done.

## 14. Shuffle up and look

The statistics panel answers half the ratio question. Opening-hand odds say what
the numbers are; a test hand says what they feel like, and that is the half that
changes a list — nobody moves a card because a percentage told them to.

So it asks for the one judgement a player is already making, playable or brick,
keeps the tally, and reports the rate. Judging deals the next hand immediately,
because anything that made you press deal again would halve how many hands you
ever look at. It claims nothing more: no simulation, no win rate, because
neither would be true.

The shuffle takes a `Random` rather than reaching for the global one, which is
the only reason any of it is testable.

## 15. The keyboard seam

`H` deals a test hand and `P` opens the siding plans, both in the table the help
sheet renders — so pressing `?` lists them without anyone writing them twice.

The interesting part is underneath. `:core` says which chords exist and `:ui`
turns a key event into one, and nothing made those agree: a binding whose key was
missing from the map was not a compile error, it was a shortcut that silently did
nothing — indistinguishable, from outside, from having misread the help. The map
is now named rather than private, and three tests hold it against the table.

## 16. The last form field

The deck's name was a labelled text box, which says "fill this in". It is the
name of the thing you are looking at, so it reads as a heading until you touch
it. Editing still uses a real text field, because that is what keeps focus
reporting working — without it, typing "side" into a deck name opens the
statistics panel and the deck check on the way past.

## 17. Finishing the word

The search box ranks fuzzily, which is right for finding a card and wrong for
finishing one. So the completion ignores scoring entirely: it offers a name only
when that name literally continues the characters already typed, which makes
accepting one identical to having typed faster.

That rule pays for itself twice. Results are debounced, so for a moment after
each keystroke the names being consulted are the *previous* query's — and a
completion that must literally continue the query can only under-offer, never
mis-offer. There is no staleness bug to fix because there is no staleness bug to
have.

The field is hand-built to carry it. Ghost text has to line up with real text
character for character, and the only way to guarantee that is to draw both with
the same style at the same origin — which means owning the padding rather than
inheriting `OutlinedTextField`'s. The ghost is one `Text` holding the whole name
with the typed prefix drawn transparent: the invisible run occupies exactly the
right width because it *is* the text it is standing in for.

## 18. Tidying, which is not sorting

A sort decides the whole order from one property, and that is why the sort button
gets pressed once and never again by anyone who arranged their deck on purpose.
Every other builder offers only that.

A tidy is a stable partition instead — cards collect into groups, groups lay end
to end, and inside a group nothing moves. Three are on offer, and the discipline
was that each had to be something `DeckSorter` cannot express, because two menu
entries differing by a subtlety is a worse offer than one that is clear about
what it costs. Gathering copies has no sort equivalent at all. Grouping by type
brings the monsters together without re-levelling them on the way past. Grouping
by archetype leaves the engines in the order somebody put them in, where a sort
would alphabetise and silently undo that decision.

Two things fell out of building it. `gatherCopies` turned out to be `groupBy`
keyed on the card itself, so there is one implementation rather than two that
could drift. And because a tidy hands over a whole list rather than editing by
index, it cannot be trusted the way the other edits can — so it goes through a
new `rearrange`, which refuses any order that is not a permutation of what was
already there.

## 19. Arranging without a mouse

Arrows move a cursor, shift grows it, Ctrl carries what it holds. The design
decision that made the rest fall out: there is no cursor object. The selection
*is* the cursor, so the keyboard and the mouse are one feature rather than two
that have to be kept looking alike, and every drag behaviour already built —
carrying a group, keeping it selected where it lands — came along for free.

Three pieces underneath, each shaped by refusing to guess. `GridNavigation`
returns null at an edge rather than clamping, so a caller can tell "there is
nowhere to go" from "you went nowhere". The focus of a range is *derived* from
the anchor and the ends rather than stored, because a stored one would be
another field every selection had to keep right. And `carry` takes a signed
delta instead of a destination, since an arrow key only knows "one further that
way" — and one further has to mean one place in the deck, not one index in a
list the cards have already been lifted out of.

Carrying clamps at the edges rather than refusing, because the key repeats, and
a clamped move that changes nothing does not go on the undo stack. It is also
the one deck edit that says nothing at all: a toast per repeat would bury the
thing being arranged.

## 20. The four-minute compiler, and a lint for it

Two red builds in a row, both from mistakes no amount of care catches by
reading: an elvis with a bare `0` against a nullable `Long`, and ten tests that
forgot to be `runTest` so the `TestScope` receiver `builderState()` needs was
missing. Neither is subtle once a compiler says it. The problem is that the
first compiler to see this module is the one in CI, four minutes after the push.

So the second half of the fix was `tools/check-test-scopes.py`, which fires on a
`@Test` that reaches for something only the receiver provides and is not a
`runTest`. Narrow on purpose — verified by putting the bug back and watching it
complain, then taking it out again and watching it go quiet. It joins
`check-imports.py`: two small scripts that between them cover the two ways this
environment lets a non-compiling change reach a push.

## 21. What an empty pane is for

An empty section was a line of grey text — which is what every other builder
shows, and is also the first thing anybody sees on a new deck.

It is now the shape of the deck about to be built: one slot for every card the
section holds, pressed into the same cloth the cards will sit on, at exactly the
size the cards will be. That last part is why `cardWidth` moved into
`GridFitter` — the fitter uses it to decide whether a section fits and the empty
pane uses it to place slots, and two copies of the formula would have shown up
as ghost outlines the first real card does not sit inside.

Prototyped in the browser first, which earned its keep twice. The slots are
drawn *inset* even though the real grid has no gutter at all: cards can touch
because they have art to tell them apart, and empty outlines that touch stop
being cards and become graph paper — obvious in a screenshot, invisible in the
code. And the rows fade out downward, because forty hard-edged rectangles is a
form to fill in rather than a table to work at. The first slot is ringed in the
section's colour, which is what turns the whole thing from decoration into an
answer to "where does the next one go".

---

## Where this stands

`:core` carries the arithmetic for all of it, at **451 tests**, up from 249, plus
**47 in `:ui`** where there were none.

Still open: the sandbox board, a full shootout mode with an opponent's deck, and
PDF export of a siding sheet.
