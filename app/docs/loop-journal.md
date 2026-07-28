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

## 22. Somewhere to look at the deck

Press V and everything else goes away: the deck at the largest size it fits at,
dealt in one pass, with no header, no stepper and no count badge on top of it —
and nothing to press, because anything pressed closes it.

This is the point of the free ordering, and it was missing. An arrangement
nobody can look at is not an arrangement, and until now there was nowhere in the
program that showed a *deck* rather than a deck editor. Every other builder has
the same hole; they all show you a decklist.

The three sections share one card size, which is why the fitter grew `fitAll`.
Sized separately, the Side deck's fifteen cards come out at a different scale
from the Main's forty and it stops reading as one deck. It is a generalisation
of the existing fitter rather than a second one, and a test says so by making
the two agree on a single section.

## 23. One client

Card data, release checks and card art each built their own `HttpClient` —
three connection pools and three thread pools for a program that talks to two
hosts, and not one of them ever closed. The graph now carries one.

Worth recording for the reason rather than the fix. Closing it on desktop is not
about reclaiming memory from a process that is ending: Ktor's engine keeps
non-daemon threads, so a window that has gone but a process that has not is the
failure this prevents. Android's belongs to the Application, outlives every
Activity recreation, and correctly never closes. And Ktor became an `api`
dependency of `:ui` rather than an `implementation`, because the client is now a
property of `AppDependencies` and every module that assembles the graph has to
be able to see the type — the same classpath rule that bit the first `:ui` test,
seen from the other side.

## 24. A deck that looks like a deck

The library listed saved decks by name, which is to say it listed filenames,
and a player with nine of them was reading nine filenames to find the one they
meant. Each tile now shows three of its own cards.

Which three is a judgement, so it went in `:core` with tests rather than being
decided inline: most copies first, because three copies of a card is the
strongest statement a decklist makes about what it is trying to do, and ties go
to whichever was put first. That tie-break earns its keep — the order is
authored in this program, so moving a card to the front of the Main deck is a
way of saying it is the one that matters, and the face follows. There is a test
that states exactly that, because it is a behaviour somebody could otherwise
mistake for a bug.

The cards are fanned rather than butted together, which is the one place in this
program where cards deliberately do not touch. A deck pane is an arrangement;
this is a portrait of one, and three rectangles in a row reads as a contact
sheet. The browser prototype is what showed that — it was not obvious in the
code, and it was immediate in a screenshot.

## 25. The thing that would have been unforgivable

Saving was entirely manual. Build for an hour on a tablet, have the OS reclaim
the process, and the deck is gone. Every other item in this journal is about how
the program feels; this one is about it not losing your work, which outranks all
of them.

A deck saved once now keeps itself saved. A deck never saved is still never
written on its own, because autosaving everything fills a library with Untitled
Decks somebody then has to delete — the price of that choice being that the
program owes a standing answer to "would I lose this", which the toolbar now
gives. And nothing at all for an empty deck: saying "unsaved" before anything
has happened is crying wolf.

The autosave hangs off the deck's own setter, the same choke point that clears
the selection, for the same reason and with more at stake — an edit that did not
schedule a save is an edit that can be lost, and nothing notices until it
matters.

That made assignment order load-bearing in three places, which is the part worth
remembering. New deck, open deck and import all replace the open deck, and
assigning the deck schedules a save — so letting go of the old deck's id has to
come *first*, or the new contents land in the old deck's row. A saved deck
silently replaced by a different one is the worst thing this program could do,
so it happens in one named place and three tests each try to make it happen.

CI caught one of them failing, and it turned out to be the test fixture rather
than the code: `newDeckId` returned the same string every time, so "two
different decks" was not expressible and the test quietly made one deck out of
two. The same shape as the bug it was written to catch, which is worth noticing
— a fixture can have the defect it is testing for.

## 26. Three days ago

A timestamp is a fact and "3 days ago" is an answer, and the second one is what
anybody asks of a saved deck. The question only became worth answering once
decks saved themselves — before that, "last modified" meant "last time you
remembered to press save", which is not information about the deck.

Deliberately coarse, and with no calendar, no time zone and no formatter:
elapsed milliseconds are all it needs, which is why the whole range can be
walked in a test without a clock. So "yesterday" means *about a day ago* rather
than yesterday's date — the right simplification, since a deck saved at 00:30
was not "yesterday" at 01:00 in any sense a person means. A clock that went
backwards says "just now", because "in 3 hours" would be true and useless.

The clock is read once when the screen opens rather than once per tile. Nine
decks saved in the same minute then agree with each other, and a recomposition
triggered by something else entirely does not make the numbers twitch — which
reads as a glitch rather than as time passing.

## 27. The thing nobody could write down

Deck notes were stored, loaded and shown in the library since the repository was
written, and nothing in the program could set them. That is worse than a missing
feature, because `save` defaults the notes column to the empty string — so every
save from the builder was quietly clearing whatever was in there.

Now the deck carries them, writes them, loads them back, and there is somewhere
to type them. They are the one thing in this program that is neither a card nor
a number: why the third copy came out, what to do against the matchup that keeps
beating you. That is most of what somebody actually knows about their own deck,
and none of it fits in a decklist.

No save button on the panel — the text goes into the deck and the deck saves
itself, which is the only behaviour that makes sense for something written in a
hurry between rounds. The saved snapshot grew a notes field to match, because a
field the writer sends but the snapshot forgets is a field that reports itself
saved the moment it changes.

## 28. Writing down the trick

The fastest way to build a deck in this program — type three letters, press
Enter three times, three copies in the deck without a hand leaving the keyboard
— was documented in a code comment, which is to say nowhere. The same for Tab
completing a name.

They cannot go in the shortcut table, because the table resolves keys globally
and a global binding for Tab or Enter would fire everywhere; that is exactly
what makes them useful inside a field and useless outside one. So they are a
second, smaller list in `:core` beside the table, and the help sheet renders
both. The sheet claims to be the whole keyboard story, so it has to be.

## 29. Two small things about looking

The drop mark was a plain bar down the leading edge of the card a drop would
land before. With the gutter closed the cards touch, so every seam already looks
like a line — and a plain bar on a seam is indistinguishable from a card's own
edge, which is the one reading it cannot afford. It now has serifs top and
bottom, and light spilling right onto the card being displaced.

The browser prototype settled it. The glow alone was not enough; the serifs were
the difference between a line and a mark. The version that read best of all was
the cards *parting* to make a gap — rejected, because that means a layout change
on every pointer move during a drag, which is exactly what the deal animation and
the spring drag are careful not to do.

And the inspector's art was a fixed 200dp — the same size on a narrow sheet and
on a 1600dp window, in the one place in the program whose entire purpose is
looking at a card. It now takes a share of whatever room there is, clamped at
both ends. Its height comes from the card aspect ratio rather than a second
hardcoded number; the old 200×292 was very nearly the right ratio and not
exactly it.

## 30. One list instead of two

Two lists said which layers cover the builder: one that silences the shortcuts,
one that Escape closes. Nothing made them agree, and I had edited them
separately twice in a day. A layer missing from the first leaks its keys through
to the builder underneath; one missing from the second cannot be closed by
Escape at all. Both fail quietly, which is the only reason they had not been
noticed.

There is now one enum in the order the layers stack, and both questions are
answered from it. The `when`s over it are exhaustive — enforced since Kotlin 1.7
rather than merely warned about — so a new layer will not compile until it says
how it opens and closes. The test walks every member and its own `when` is
exhaustive too, so it cannot quietly stop covering all of them.

The first attempt at that used `val x: Unit = when (...)` to force
exhaustiveness, which does not compile: assignments are not expressions in
Kotlin. It was also unnecessary — the language already insists.

## 31. What shootout mode actually is

Read rather than guessed, from the original's `ShootoutManager`, because it is
the last large thing on the list and it was worth knowing its shape before
starting it.

It is not "deal a hand and judge it" — that is the test-hand panel, which is
built. It is a structured playtest *run*: load an opponent's deck from a `.ydk`,
choose a mode and a number of trials, then work through them one at a time with
opening hands for both decks, siding for **both sides** between games, undo of a
whole trial, notes, and a report at the end that can be exported.

So it composes almost everything already here — `HandSimulator`, `SidingEngine`,
the deck repository, the siding panel — plus a run structure and a report that
do not exist yet. That makes it a genuinely good next feature and a bad thing to
start in the last hour of a loop: half a run structure is worse than none, and
the person who asked for this wants to review before the next build.

Deliberately not started. Recorded instead.

---

## Where this stands

`:core` carries the arithmetic for all of it, at **492 tests**, up from 249, plus
**59 in `:ui`** where there were none.

Still open, in the order they are worth doing:

**Shootout mode.** A structured playtest run against an opponent's deck —
trials, siding for both sides between games, a report. Composes almost
everything already built, and section 31 records its shape.

**The sandbox board.** Where the best idea in the original lives: the gesture
*is* the orientation — quick drop is attack, a horizontal flick is defense, a
hold is face-down. Nothing on the market does this.

**PDF export of a siding sheet.** The original's actual deliverable. Needs a PDF
writer that resolves from Maven Central; spike before committing to it.
