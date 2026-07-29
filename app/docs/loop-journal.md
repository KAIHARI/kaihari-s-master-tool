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

## 32. Two ways of not saying anything

A card added to a pane that is already full landed out of sight, and the only
sign it had worked was a number in the header changing — the same amount of
feedback as nothing having happened. The pane now brings it into view, and shows
the copy just added rather than the first one, because scrolling to the first Ash
Blossom when you added the third answers a question nobody asked.

It does not flash. The card appearing is the confirmation, and a flash on every
add would be a strobe; the flash stays for a card somebody went looking for,
which is the deck check naming a problem. The pane also stopped scrolling when
the card is already in front of you — that was wrong for the deck check too, and
jumping a visible card to the top of the pane is movement that answers nothing.

The other silence: stacked mode turns dragging off, because a stack has no
position and there is nothing coherent for dragging one to mean. True, and
invisible — cards simply stopped moving, which reads as the program having
broken rather than as a mode. The menu entry now says what the mode costs before
it is chosen.

Leaving it at that rather than making stacks draggable was a judgement about
this environment more than about the feature. Making it work means translating
drop positions between stack coordinates and card coordinates through the drag
controller, and that is exactly the kind of multi-file change that cannot be
compiled here and has already cost two red builds today. It is worth doing with
a compiler in the room.

## 33. Testing the room, and the tidy the Extra deck wanted

`DeckLayoutState` had no tests, and it carries the only real arithmetic left in
the UI layer: three panes trading weight across a divider. It is also where a
mistake hides best — a resize that quietly shrinks the pane on the far side of
the drag reads as the drag being imprecise rather than as a bug. Eighteen tests
now, over the parts that are easy to get wrong and impossible to notice: that a
resize preserves the total and never moves the third pane, that hitting a
minimum stops the drag rather than paying for space nobody received, and that
pinning a pane's column count stops having an opinion afterwards — it runs on
every frame of a drag, so a second opinion would overwrite the first mid-gesture.

Then a fourth tidy. Grouping fifteen Extra deck cards by "card type" tells you
they are all monsters; grouping them by *how they are summoned* is how that pane
is already read. It works off the frame rather than the type line — the same
thing `isExtraDeck` reads — and takes the first match, so a Fusion Pendulum
monster files with the Fusions where it belongs.

Worth noting what it cost in the UI: nothing. The menu renders `TidyBy.entries`
and the property test walks them, so a new tidy appears in both by existing. The
same shape as the shortcut table and the overlay enum — three times now that
declaring the list once has meant the second place could not fall behind.

## 34. The same clothes, everywhere

Eight panels — inspector, deck check, statistics, filters, siding, test hands,
notes, help — had each built their own `ModalBottomSheet` with the same three
lines of boilerplate and Material's own looks. So the deck sat on cloth and
every panel that opened over it was a grey slab with a grey pill on top: a
different program's dialog, eight times over.

One wrapper now. The container is the mat, so a panel is the same surface as the
table it slides over; the corner is 8dp rather than Material's 28, which is the
language the 6dp panes and 3dp cards already speak; and the handle is a short
accent bar rather than the pill every Android app has. The sheet state moved
inside, because not one of the eight ever did anything with it except pass it
straight back.

Then the snackbar, which was the last Material surface and is the most-seen
thing in the program — nearly every edit raises one. Same cloth, a 4dp corner,
and a hairline of accent so it reads as lifted off the table rather than printed
on it. "Undo" keeps the bright accent, because it is the only word here that has
to be found in a hurry.

Both of these were cheap because they were *one* change each rather than eight
and one. That is the same lesson as the shortcut table, the overlay enum and the
tidy list, arriving from the visual side: the thing worth doing once is deciding
where a decision lives.

## 35. A shelf you can search

A library of saved decks with no way to search it is a library you scroll.
Names go through the same normaliser the card search uses, so "snake eye" finds
"Snake-Eye" — deck names are typed by hand and remembered approximately, and
that is exactly the case a plain substring match fails.

Small enough to be worth saying why it is here at all: this is the screen you
arrive on, and everything else in the loop assumed you had already found the
deck you meant.

## 36. Picking up where you left off

A deck that saves itself and then greets you with an empty pane next time has
only done half the job. The program now remembers which deck was open and opens
it again.

The interesting part is what "remember" had to mean. Written whenever the open
deck changes rather than on the way out, because there is no reliable moment of
closing on a tablet — the process is reclaimed without ceremony, which is the
case this exists for in the first place.

And it ignores a null id rather than storing one, for two reasons that turned
out to agree. It removes a race I had written in: the restore reads the
preferences, opens a deck asynchronously, and the id only arrives once that load
finishes — so a null written in the meantime erases the very thing being
restored. It is also simply the better behaviour, since a new deck has no id and
cannot be reopened, and forgetting a real deck in order to remember nothing is a
worse thing to find on next launch.

The id lives in the preferences document. Not a layout setting, and there
anyway: that document is what the app remembers about itself between runs, and a
table for one nullable string is a table too many.

## 37. When nothing is called that

`Card.description` had been stored, loaded and never read. Meanwhile "which of
these banishes?" is a real question about a deck with nowhere to ask it.

The shape it took is the part worth recording. Not a mode, not a toggle, not a
`text:` prefix — a *fallback*. The name search runs as it always did, and only
when it comes back with nothing does the pool get asked what the cards say. That
needs no control and no explaining, and it costs nothing in the common case,
because it only runs at the moment somebody was already looking for something
else.

Two deliberate limits. The text match is literal rather than fuzzy: card text is
written in a house style, and the typo tolerance that suits a half-remembered
*name* would turn "banish" into a hundred cards that merely rhyme with it. And
it needs three characters, because every card says "a" and half say "in", so a
two-letter fallback would fire constantly on the way to typing a name.

The pool says so when it happens — "nothing is called that — 12 say it". A
screen that quietly substitutes one question for another is a screen you stop
trusting, and these results really are answering a different one.

## 38. The templates that were not a feature

The plan listed the original's four "siding templates" as work to port. Reading
them settles it: they are hardcoded passcode lists with `// Example:` comments
beside them — Ash Blossom in, Maxx "C" out — which is scaffolding somebody left
in, not a feature. Siding a fixed list of cards into *any* deck is meaningless;
those cards are very likely not in your Side deck at all.

Not ported, deliberately. What is there instead is better and already built:
side by hand, then keep what you did. The plan is recorded from a real swap in a
real deck, so every card in it is one you actually own.

There is a real feature adjacent to it — suggesting a plan from what is in your
Side deck — but that is a different thing with a different name, and inventing
it under cover of "porting the templates" would be the wrong way to arrive at
it.

## 39. The first useful piece of a shootout

Section 31 recorded what shootout mode is — a structured playtest run — and why
starting it late in a loop was a bad idea. This is the piece of it that stands
alone: load the deck you are testing against, and see both opening hands at
once.

The test-hand panel answers "does my list open". This answers the question that
actually decides a match, which is "does my list open *against theirs*". Sitting
them one above the other is most of the answer; you can see whether your
interruption is the one their board cares about before a card is played.

Two decisions worth writing down. The opponent's deck is kept entirely separate
from the one being built, and nothing in the panel can edit either — every other
importer in this program replaces the open deck, and this one shares their file
picker, so there is a test whose whole job is to say your list survived loading
theirs. And the choice offered is *who goes first*, not how many cards each side
draws: one of them going first means the other is going second, and dealing two
five-card hands would be a matchup that cannot happen.

Still not built, and still recorded rather than half-done: the run structure —
a set number of trials, siding for both sides between games, a report at the
end.

## 40. Making it a loop

Two hands side by side answers one question once. Judging each opening the way
the test-hand panel does — playable, or a brick — and dealing the next turns it
into the thing somebody actually does for twenty minutes before a tournament.

The record resets when the opponent changes. A record against one list says
nothing about another, and a tally that quietly carried over would be worse than
having none.

And a third formatter found and removed. The test-hand panel had its own private
copy of the percentage formatting that already existed in `components`, and
truncated where the original rounds — so the same brick rate could read 24.9% in
one panel and 25.0% in the other. That is the fourth time in this loop that a
thing written twice had started to disagree, and every one of them was found by
going to use it somewhere else.

## 41. Which side of the die roll

One tally across a whole run answers the wrong question. The decision a player
actually makes at the start of a match is *whether to go first*, and against some
decks the answer is the opposite of the usual one — a list that bricks going
first and functions going second is exactly the list whose owner needs telling,
because the default is to choose first without thinking.

So the two are never mixed, and the record turns them into an answer rather than
leaving two percentages side by side to be compared by eye. Doing three-quarters
of that work and stopping is the thing worth avoiding.

The interesting part is when it declines to answer. Two hands a side is not
evidence, and a program that answers anyway will confidently recommend going
second off a single unlucky opening — so it stays quiet until five each. A tie
is also silence, because "it does not matter" is a real answer and dressing it as
a preference would be inventing one. Five is not statistics; it is the point past
which somebody would have formed an opinion anyway, and keeping quiet until then
is the honest version of that.

One detail found by writing the test: a verdict is recorded against the side the
hand was actually *dealt* for, not whichever chip is selected when the verdict is
given. Those are the same right up until somebody switches sides mid-judgement.

## 42. A record of a deck that no longer exists

The matchup record had a hole I put there an hour earlier: it survived edits to
the deck it was a record of. Judge twenty hands, swap a card, and the twenty are
still on screen as evidence about a forty that no longer exists.

It matters more here than it would elsewhere, because *siding mid-shootout is
the whole point of a shootout*. The one moment the feature is doing its job is
the moment the record silently becomes a lie.

So every record of how a deck opens is thrown away when the Main deck changes —
the shootout record and the test-hand tally both, since it was the same hole
twice. Extra and Side edits leave it alone, because openings are dealt from the
Main deck and an Extra deck change is not a change to the question being asked.

Losing a twenty-hand record to one swapped card is annoying. Keeping it and
letting it read as evidence about the list now in front of you is worse, and
those were the only two options.

## 43. A shootout is trials, not hands

The last large thing on the list, and the shape of it was recorded back in
section 31 rather than guessed at. What was already built judges loose openings
against a real opponent's list and says how often yours bricks. What was missing
is the structure a tournament actually has: game one off the list you
registered, everything after it off the sided one.

That split is the whole feature. The reason anybody owns a side deck is the gap
between those two numbers, and nothing in the program could measure it, because
nothing knew which version of a list dealt which hand.

The run is a plain value, so undoing a whole trial is a `dropLast`. That case is
real and not the same as undoing a verdict: realise halfway through a match that
you sided against the wrong deck and game one of that trial has stopped being
evidence about anything either.

It declines to answer twice, and the second one took a correction. Under ten
hands from each version it stays quiet. The margin then has to sit *above* what
one hand is worth at that sample — at ten hands a single verdict moves the rate
ten points, so a ten-point margin would report one lucky opening as proof the
side deck works. The first draft had exactly that bug and a test I had written
to catch it passed for the wrong reason.

But "no difference" is printed as loudly as the other two verdicts. A side deck
that measurably changes nothing against a matchup is fifteen cards doing no
work, and that is usually the finding worth acting on.

## 44. The record that survives siding, and the one that cannot

Two days ago every record of how a deck opens was thrown away whenever the Main
deck changed, on the grounds that siding mid-shootout makes a tally a lie. That
was right, and it is wrong for a run, which is the one record that labels each
hand with the version of the list that dealt it. Siding is not what spoils a
run; siding is what it measures.

So a run survives a swap and nothing else does. Telling those apart needed a
test the Main deck alone cannot pass: moving a card in from the Side deck and
adding a card you never registered look identical from the Main deck. Comparing
all three sections by count does it — siding moves cards across the line, it
never changes which sixty you brought.

Whether a hand was post-side is read off the deck rather than assumed from the
trial number, because nothing enforces the swap. A run that assumed compliance
would be confidently wrong about the only thing it exists to measure, and
forgetting to side is now recorded as what it was.

Writing the test found the mistake that would have looked completely normal on
screen. The panel deals the next opening the moment a verdict is given, and
siding happens *after* that — so game two's hand came off the pre-side list and
would have been filed as post-side, every trial, invisibly. The deck moving
during a run now deals your side again.

## 45. Reading a run at a glance

Prototyped in HTML and looked at before any Compose was written, which is the
only way visual work happens here.

The first version marked each opening with a shape: filled for playable, struck
through for a brick, and a notched corner for post-side. At twenty pixels the
notch was invisible. The second version threw the shape away and used position —
game one along the top band, everything post-side beneath it — and the sheet
became readable without a legend, so the legend went away too.

The two rows *are* the two numbers underneath it. A glance down the sheet says
whether the bottom band is greener than the top, which is the entire question.

One thing deliberately removed inside a run: dealing again. Shuffling until the
hand looks good and only then judging it is how a sample stops meaning anything,
and a run is nothing but its sample. Loose judging still offers it, because
there it costs nothing.

## 46. The board, and the one rule a sandbox has to know

The deck builder can say a list opens. It cannot say the opening *does*
anything, and nobody works that out by reading forty lines of text — they lay
the cards out. So: somewhere to lay them out.

The table is one value rather than three, because every operation moves a card
between the board, the hand and the deck, and none of them means anything alone.
That is also what makes undo a stack of whole tables instead of a stack of
inverse operations. The original needed a command factory for this. A table is a
dozen cards and copying one is free.

It knows a card cannot be in two places at once and that a monster zone holds one
monster. It knows nothing else — a sandbox that enforced summoning restrictions
would be a sandbox nobody could use to check the thing they were unsure about.
Decking out is not an error either; it is there being nothing left to draw. The
one rule of the game it does have to know is that the Extra deck is not something
you draw.

One trap worth naming: a *refused* drop must not push an undo snapshot. It would
make the next undo do nothing at all, which reads as undo being broken rather
than as the drop having been refused.

## 47. Prototyped twice before a line of Compose

The fold is the part worth getting right and it comes from the original: the far
half tips away, the near half tips toward you, hinged on the Extra Monster Zone
row between them. It is not decoration. A flat overhead grid is a spreadsheet of
a board; tipping it means your cards face you and theirs recede, which is what
sitting across a table looks like.

The first browser prototype was too flat to read at all — the rotation was there
and simply did not register. The second broke: translating the halves in Z inside
a shared perspective pushed one of them out of the frame entirely. The third
worked, and only because everything sits in one perspective container with one
rotation each and no Z tricks at all.

Which turned out to be exactly what Compose can express. There is no
`transform-style: preserve-3d` — a rotated parent flattens its children before
they rotate themselves — so a half has to be one rigid plane. That is the right
model anyway.

One number in the whole screen is unverified: the camera distance. Its units are
density-relative rather than the CSS pixels the prototype used, and there is no
screen here to look at. Erring long costs a little depth; erring short would bend
the far row into something unreadable, so it errs long. It is marked in the code
as the thing a real screen has to confirm.

## 48. The way you let go says which way the card faces

Nothing asks. Drop a card and it stands up in attack; flick it sideways as you
release and it lies down in defence; hold it a moment first and it goes
face-down. The gesture is the card turning ninety degrees, which is what it
means.

The thresholds were never the hard part — they came from the original's own drop
handler. Deciding *which movement* they apply to is. A card carried from the hand
to the far spell zone has travelled a long way and means nothing by it, and a
careful drag takes longer than a hold, so measuring either from the start of the
drag turns every drop into a set card.

Two things came out of testing it that would never have come out of writing it.

Stillness has to be measured to the *release*, not to the last pointer event: a
hand that has stopped sends nothing at all, so the silence is the entire hold.
And sub-pixel jitter under a fingertip must not count as movement, or holding
would be impossible on a touchscreen.

Then the window length, which took three goes. One window cannot do it. A card
brought quickly down and then flicked reads as a plain drop over ninety
milliseconds, because the approach is vertical and drowns the flick; shortening
the window loses the slower flick, which is the one most people make. Two
windows, and a flick in either counts. The first pair was 45 and 90, and 45 was
still wrong — at sixty frames a second it holds two samples, so a flick in it is
a single interval, which landed exactly on the distance threshold and fell either
side of it depending on nothing at all. Sixty and ninety.

That last one is the clearest case yet for why the arithmetic lives in `:core`
behind tests. It is a bug you could only find by playing with a tablet for an
hour, and there is no tablet here.

## 49. The half of a board that was never in your hand

A modern turn ends in five monsters that were never drawn. A sandbox without the
Extra deck can only ever show an opening, not what the opening builds — which is
the entire question somebody opens a board to ask.

So all four stacks became things you can look through and take from, behind one
sheet. They ask the same question — *which one* — and four differently shaped
answers would be three more things to learn. The Extra deck is taken by index and
never by "the top", because it is a set rather than a stack; the graveyard is the
same, since nothing in this game retrieves the card on top of a graveyard, it
retrieves the one you want.

Tapping a card in a stack picks it up rather than opening a menu. That turned out
to unify four quite different operations — playing, moving, summoning, reviving —
into one thing at the fingertips while keeping them apart in the model: a monster
fished out of the graveyard lands in a zone by exactly the same tap or drag as one
out of the hand, and the gesture still says which way it faces.

Two details that are only obvious once you imagine using it. A *refused*
placement keeps the card picked up — losing it there would mean finding it again
in a stack, which is the opposite of what a refusal should cost. And searching the
deck does not shuffle it, because a search that shuffled would quietly ruin
whatever was set up on top and nothing on screen would say it had.

Then the hole that made the rest of it half-useful: the stacks were somewhere to
look and not somewhere to *put* things, and most of what a combo does is send
cards to the graveyard. They are drop targets now, and banishing from the
graveyard — which decks do every turn — falls out of the same mechanism.

## 50. Play it out

The moment a test hand stops being a hand and becomes a question. "Would I keep
this" is answered by looking; "what does it actually do" is only answered by
playing it, and until now that meant shuffling until the same five came up again.
A hand in the test panel or the shootout is now one button from being a board
with that hand already in it.

Laying a deck out around a hand somebody already holds has two edges worth
naming. Only one copy leaves the deck per copy in hand — a naive `removeAll`
takes all three Ash Blossoms when you are holding one. And a card in the hand
that is not in the list is dealt anyway: it came from somewhere real, and a hand
that silently lost a card is a worse answer than one that is slightly generous.

The build that caught the argument order is worth recording as a lesson rather
than a mistake. `open(deck, random)` became `open(deck, hand, random)`, and every
positional caller silently rebound — handing a random number generator to
something expecting an opening hand. Nothing about that is loud. With a compiler
in the room it is a red squiggle; here it is four minutes and a round trip, and
the rule that follows is to name the argument or append the parameter, never
insert it.

## 51. Gaps, because that is what hands do to a pile

The thing the person who asked for this called most important: *the free order of
cards is how the player sees their own deck, like an art form.* The order has
been theirs since the first day of this loop — nothing sorts without being asked.
But an order alone cannot say *these nine are the engine and those six are the
handtraps*, and on a table that is said by pushing the piles apart.

So a gap. Not a card, not a label, not a folder — space, which is what hands
actually do, and which costs nothing to ignore. Stored as positions rather than
anchored to cards, because a gap belongs to the layout and not to anything in it:
anchoring one to "before the second Ash Blossom" would move it when a copy is
cut, which is not what anybody drew it to mean.

Drawn as space too. The card at a gap gives up nine points of its own width, so a
section with gaps holds the same number of cards per row as one without and
nothing reflows — which is the whole difference between marking up an arrangement
and rearranging it. There is a hairline in the space only because at zero gutter
nine points could be mistaken for the grid breathing.

The tidies produce them, which is what makes the feature findable without being
explained: group by type, and the groups are pushed apart. Except gathering
copies, where forty cards would become twenty gaps — that says nothing and looks
like a fault. A sort takes them away, because a sort decides the whole order from
one property and the arrangement the gaps described is gone.

They go out in the `#ydkx-extended` payload and come back with the file. A plain
`.ydk` has nowhere to put them, which is fine: opened anywhere else it is exactly
the same deck, and a file that loses its gaps still opens.

## 52. Reading the edit instead of being told about it

The hard part was never drawing a gap. It was keeping one in the right place
while a deck is built around it.

The obvious design has every edit report its own index — add, remove, drop,
drag-out, siding, undo, import — and every one of them remember to. Nine places
to keep in step, in a module that cannot be compiled here, and the one that
forgot would leave a gap silently pointing at the wrong card.

So the gaps read the edit off the before and after instead, from inside the one
place a deck is ever assigned. There is nothing to keep in step with. It only has
to recognise the three things a person actually does — something arrived,
something left, something moved — and a change it cannot read as one of those
leaves the gaps alone rather than throwing them away. That is the recoverable
failure of the two: a gap in an odd place is one tap from being moved, and a
deleted gap is gone.

Two edges found by writing the tests. A gap whose group loses its last card has
to come along with the card that moved up, or the two groups silently run
together. And a card dropped exactly *on* a gap has to join the group before it —
either answer is defensible, but that is the one that makes appending to the end
of a section free, and appending is most of what happens to a deck.

## 53. Two red builds, and the third lint

Both from the same cause: writing Compose without a compiler and paying four
minutes to find out.

The first was a parameter inserted into the middle of a signature. Nothing about
that is loud — every positional caller silently rebinds, and here that meant
handing a random number generator to something expecting an opening hand. The
rule that follows is to name the argument or append the parameter, never insert
it.

The second was worse and is now impossible: a block of new members went in
between a property and its `private set`, leaving the accessor dangling after an
unrelated function. That one is mechanically detectable, so `check-accessors.py`
detects it — a lone accessor must never follow a closing brace. Confirmed by
putting the bug back and watching it fire, which is the only way to know a lint
works.

Third lint in this loop, all three written the moment a mistake proved they were
worth having, none of them clever. The environment cannot give me a compiler, so
the answer is to keep taking the specific mistakes it lets through and closing
them one at a time.

## 54. The seam a test was already watching

The gap shortcut went in and CI came back red on one test:
`everyBoundChordHasAKeyThatCanProduceIt`. `:core` says which chords exist and
`:ui` turns a key event into one, and nothing but that test makes the two agree —
a binding whose key is missing from the map is not a compile error, it is a
shortcut that silently does nothing when you press it.

It was written earlier in this loop for exactly this and it earned its keep the
first time somebody added a binding. Worth recording because it is the pattern
the whole loop keeps arriving at: the environment cannot give me a compiler for
half the code, so the answer is a small guard for each specific way that lets a
mistake through.

The board also got its field spell zone, which had been in the model since the
first day and never drawn. It goes where it goes on a mat — the far left of the
spell row — which pushed the banished pile up to the toolbar. That is the right
trade: it is the stack a player looks at least often, and it still says how many
are in it without being opened.

## 55. Tokens, and a board that was showing your cards twice

Tokens because plenty of decks make them and a board that cannot show one cannot
show the turn. A token has no passcode and no art, so it is drawn as a plate —
not a card, not a card back, because it is neither and either would be saying
something untrue about what is in that zone.

It is picked up and put down like everything else, which is the return on having
exactly one idea of "held": the same tap places it and the same gesture decides
which way it faces, and a defence token needed nothing built for it. Sent to a
graveyard or bounced to the hand it stops existing, which is the rule and also
the only sensible reading — a graveyard with a token in it is a graveyard that
would let you bring the token back.

Then a defect found by reading the board again rather than by a test. The far
half was drawing the *same zone ids* as the near half, because there is one set
and it is yours: a card played in front of you appeared across from you as well,
and every drop target was registered twice with whichever bounds composed last.

The far half is now an outline and nothing else. It is there because a mat has
two halves and the fold needs something to fold, and it is inert because there is
no opponent to simulate — a zone you could drop into that belonged to nobody
would be a worse lie than an empty rectangle. The honest version, a second set of
zones with a second player behind them, is a real feature and not this one.

## 56. Writing the PDF instead of depending on one

The last thing the original tool did that this one could not, and the reason it
stayed on the list all loop is that no PDF library resolves in this environment.
The plan said "spike before committing to it". The spike was to notice that a
siding sheet is text in a grid, and that PDF is a text format.

So it is written by hand, in `:core`, in about two hundred lines: two weights of
Helvetica, greyscale, lines and filled rectangles. Nothing else, because a siding
sheet is a thing you print and put next to your deck box between rounds, and
every feature not in that sentence is a feature that can be wrong.

The hard part is not drawing. It is the cross-reference table, which is a list of
byte offsets that must each point exactly at the object it names — and that is
arithmetic, which is testable. It rests on one decision: everything emitted is
ASCII, with characters past Latin-1 becoming `?` and the rest escaped to octal.
That is what makes "string length" and "byte offset" the same number. Escaping
parentheses is not optional either, or a card called *Number 39: Utopia (Assault
Mode)* produces a file no reader will open.

That ASCII promise then paid for itself twice. The sheet goes out through the
same text export the deck itself uses — the bytes survive a round trip through a
string untouched, so no platform code had to learn about binary files.

And it is *verified*, which matters more here than usual because the whole thing
is a format I could get subtly wrong in a way no test I wrote would notice.
Generating a real sheet and reading it back with an independent parser gave: one
A4 page, both matchups whole, per-copy lines, the uneven swap flagged, and an
accented deck name intact. That is the first time in this loop something was
checked by a program that had no idea what I intended.

The sheet itself is shaped by when it is read: thirty seconds after a die roll,
on paper, beside a deck box. One matchup per block, going first above going
second, out on the left and in on the right, a count on each side — because the
one mistake that costs a game loss is siding four out and three in, and nothing
else on the sheet would show it. Copies get a line each rather than a `3x`, since
siding out two of three is a different decision from siding out all three. And a
matchup is never split across a page break, because reading half a plan and
turning the page is exactly what a sheet is meant to save you from.

## 57. Their side of the table sides too

The half of a real match the shootout was still missing. A run sided *your* deck
and knew which version dealt each hand; the deck across the table stayed the deck
you loaded, so their game three looked exactly like their game one.

The answer turned out to already be in the file. A `.ydkx` written by the desktop
tool keeps that deck's own siding plans inside it, so a list downloaded from
somebody who plays the deck often arrives carrying what they actually side —
against you, among others. That is a far better answer than a guess and it cost
nothing to use: `SidingCodec` already reads that payload, and `SidingEngine`
already applies a plan to a deck.

When the file says nothing, the panel says nothing. Inventing their fifteen would
be putting a made-up board across the table and then drawing conclusions from how
your hand fared against it, which is worse than admitting there is nothing to
show.

Two things fell out of the model that was already there. Their hand is dealt
again the moment their deck changes — the same trap that made *your* side
re-deal, and just as invisible, because a hand on the table came off the list
they were playing a second ago. And their list goes back for game one of the next
trial, since game one is pre-side for both decks: leaving them sided would
compare your opening list against their game-two list, which is a match that
never happens.

## 58. A pass spent looking rather than adding

The zone bug in section 55 was found by reading the board again, not by a test,
so I spent a round doing only that. Three things came out of it.

**A card the pool has not downloaded was dropping its modifier.** `DeckCard`
returns early for an unknown card, and the early return did not pass on the
modifier the grid had given it — so in the first seconds after an import, when
every card is unknown, a section lost its gaps and its placement animation and
then had them appear as the pool arrived. Invisible in every test, obvious the
moment you read the two lines together.

**Undoing "new deck" brought the cards back without their gaps.** Undo restores
the order, and an arrangement without its gaps is not the arrangement that was
there. The gaps now go with the deck they described and come back with it.

**Four things I wrote and nothing could reach**: taking back one game in a run, a
board zone clear, a table-is-empty check, and two sandbox actions with no gesture
behind them. Unused API is the same risk as duplicated code — a second way to do
something, kept honest by nobody, waiting to disagree with the way that is
actually used. Discarding from the hand became a drag onto the graveyard, so the
method for it *was* the second way; bouncing a card back to the hand has no path
at all, which is the honest reason to delete it and write it down rather than
leave it looking supported.

And one piece of hardening with a sweep behind it rather than an argument: no
number the PDF writer emits can end in a decimal point, checked across every
value a page coordinate can take. `12.` is not a number, a reader that meets one
stops drawing the page, and it was reachable only through float error — which is
exactly the kind of thing that happens once, on somebody else's deck.

## 59. The other side of the table

Section 55 made the far half an outline and said the honest version was a real
feature. It turned out to be a small one, because the reason to want it is
sharper than "two boards": a single player laying cards out is almost always
asking *they have this, can I break it*, and that question cannot be asked at all
if the other side of the mat is scenery.

So a zone knows which half it is on, defaulted to yours — everything about your
own half was written before theirs existed and should not have to say so. The
`Board` needed no change at all: it is a map keyed by zone, and there are simply
more zones now.

Their spells sit nearest the crease and their monsters behind them, which is what
you are looking at from across a table, and their field spell zone flanks them on
their right. Cards, tokens and face-downs all go over there by the same tap or
drag as anything else, which is the return on there being one idea of "picked
up" — mocking a board is mostly tokens and face-downs, and neither needed
anything built.

Their half reads quieter than yours: both are real and both take cards, but the
board you are trying to break is the backdrop to the one you are building.

The test that pins it is the one that would have caught the original bug — the
two sets of ids are provably distinct, so a card played in front of you can never
appear across from you.

## 60. The prototype that overruled the reasoning

The gaps in a deck pane were drawn by taking nine points out of the width of the
card at the break — the card gives up the space rather than the pane growing, so
nothing reflows. That reasoning is sound and I wrote it into the commit message.

Then I prototyped it at real card size and looked at it, and it is wrong. A grid
where one card is narrower than the others reads as a fault long before it reads
as a gap. It is obvious in the screenshot and it was not obvious in the argument.

So the pane *marks* the arrangement and the showcase *shows* it. In the pane every
card stays exactly the size of every other card, and a group is called out by a
bar with serifs standing between two of them — the same shape the drop mark uses,
because at zero gutter a bare line against a card edge reads as part of the card.
In the showcase, where a deck is looked at rather than worked on, the groups
genuinely separate onto their own rows.

The two views are doing different jobs and it is right that they say it
differently. That is a better answer than the one I set out with, and I would not
have got to it by thinking harder — only by rendering it and looking.

## 61. Small things found by looking at what was already there

No new feature this round, and three things worth more than one.

**A run and the deck across the table had a boundary each way that nobody set.**
Starting a run against a deck left sided from a previous one puts a game-two
board across the table for game one and says nothing about it; abandoning a run
mid-trial leaves their deck sided while loose judging carries on, and loose
judging says nothing about which version of anything it is looking at. Both are
one line, and neither was visible from inside either feature — they only exist
where the two meet.

**The README had been wrong about siding for days.** It still carried a note
saying the app does not implement siding *yet* and therefore round-trips the
extended payload untouched — two entries about the same block of JSON, one of
them stale. The plan for this loop said to keep that file true as behaviour
changed, and it had quietly stopped being true. Folded into the entry that was
right, which now also names the third thing written in there.

**Closing every gap at once had no button.** The state could do it and the sort
path used it, so a section whose grouping had gone stale meant toggling gaps off
one at a time. It belongs with the tidies rather than the sorts, because taking
the gaps away leaves every card exactly where it is.

And one addition that is only a few lines but finishes something: the drag had a
haptic when a card was lifted and none when it landed, so the gesture was felt at
one end and not the other. It fires only on a drop that was accepted — a refused
drop is a card going back where it came from, and a buzz there would be the
program agreeing with something it had just refused.

---

## Where this stands

`:core` carries the arithmetic for all of it, at **679 tests**, up from 249, plus
**184 in `:ui`** where there were none — a module that cannot even be compiled in
the environment this was written in.

Still open, in the order they are worth doing:

**Dragging a gap along.** A gap is placed from a card's menu or with `g` on the
card under the cursor (sections 51-54), and moving one is two taps rather than a
drag. Picking it up and sliding it is the obvious next gesture.

**Siding for an opponent whose file does not say how — deliberately not built.**
They side from their own plans when the file carries them (section 57), and a
plain `.ydk` still sits there unchanged. Three shapes were considered and all
three are worse than nothing: cutting from their Main at random dresses a guess
as a measurement; adding their cards without cutting quietly changes every draw
probability; and asking the player to author both halves of a plan for a deck
they do not own is a form nobody would fill in twice. Their deck feeds only what
you *see* across the table — it does not enter the report at all — so the honest
answer is to leave it alone until somebody has a better idea than these.

---

## 62 · A deck with gaps in it was sized as though it had none

The showcase — press `V` and everything else goes away — fits the whole deck to
the screen by asking `GridFitter.fitAll` for the fewest columns that let every
section fit at one card size. It passed the section *totals*.

That was right until gaps got their own rows in that view. A group ends its last
row wherever it ends, so a forty-card Main split nine / six / twenty-five needs
more rows than forty cards do, and the fitter was sizing the cards for a deck
that packs tighter than the one on screen. The bottom of it fell off.

Fixed by passing the group sizes rather than the section sizes, and by counting
the extra inter-group spacing into the gap budget that comes off the same height.
The property that says it stays fixed is in `:core`: the same forty cards in
three groups never needs fewer columns than forty cards do.

Worth noting how this one was found — not by a test and not in CI, which cannot
run this screen, but by reading the call site while writing something else and
noticing the argument no longer meant what the parameter is named. The rule that
keeps catching things in this repo is that arithmetic in `:core` gets a test and
arithmetic passed *into* `:core` gets read again whenever the thing feeding it
changes shape.

---

## 63 · A picture of the deck, which means writing PNG

The arrangement is the thing this program is *for*, and it has never been able to
leave. A `.ydk` carries the order but nothing opens a `.ydk` except another deck
builder; the siding sheet goes out as a PDF because a PDF is text. What somebody
actually wants to post is a picture of their deck, laid out the way they laid it
out — and every other tool that exports one sorts the list first, which throws
away the only part that was theirs.

So: `core/export/Png.kt`, and under it `core/export/Deflate.kt`, because a PNG
without deflate is not a PNG. Same reasoning as the PDF writer, one step further
along: nothing that compresses resolves here, and `java.util.zip` is not on the
common source set anyway — `:core` is deliberately free of platform APIs so it
runs everywhere.

Fixed Huffman codes and a hash-chain match finder, which is the simple half of
RFC 1951. Dynamic codes would win maybe fifteen per cent more and cost a
code-length tree, a second pass, and a lot of arithmetic that could be quietly
wrong in ways no picture would reveal. The filter is where the real win is: a row
stored as its difference from the row above turns a flat mat into a run of
zeroes, and 160,000 pixels of one colour come to under six kilobytes.

**How it was checked.** Twenty-one tests in `:core`, and then the part that
actually settles it — a scratch harness wrote a real 640×420 image, and Python's
`zlib` inflated it, verified every chunk CRC independently, un-filtered all 420
scanlines by hand and compared them byte for byte against the pixels that went
in. Every one matched. Pillow then opened the file and read back the exact colour
at two positions. A format written from the specification is only as good as a
decoder that never saw the specification agreeing with it.

Known values are in the tests for the same reason: `crc32("123456789")` and
`adler32("Wikipedia")` are what every other implementation gets, so a mistake in
a table shows up here rather than as a file that opens nowhere.

Next: a binary path out of the app — `DeckFileAccess` only speaks strings today,
which is exactly why the PDF had to be all-ASCII — and then capturing the
showcase into pixels.

---

## 64 · The door out only spoke text

`DeckFileAccess` had `exportDeck(name, content: String)` and `shareDeck` the
same, which is why the PDF writer keeps its whole output inside ASCII: the siding
sheet rides the decklist's export, and anything binary would not survive being
decoded and re-encoded on the way. That was a fine constraint for a PDF. It is
not one a picture can meet.

So the interface takes bytes and a type, and two extension functions keep every
existing caller reading exactly the way it did. The PDF now goes out as
`application/pdf` rather than as text pretending — which on Android is the
difference between a share sheet offering *post this* and offering *attach this
to an email*.

Android's `CreateDocument` contract fixes its type at registration, so it is now
registered with a wildcard and the picker takes the type from the suggested name
instead. Three different kinds of file leave by that door now.

The ASCII property in the PDF writer stays, and so does its test — it is still
what makes "string length" and "byte offset" the same number in the
cross-reference table. Only the *reason given for it* was wrong, and a comment
that explains a constraint by pointing at something that has since moved is worse
than no comment.

---

## 65 · The deck leaves as a picture

`V` already draws the thing worth showing somebody: the deck at the largest size
it fits at, in the arrangement its owner made, with the gaps where they put them.
It could not leave the screen.

Now it can. `ScreenCapture` records the showcase into a graphics layer Compose
was going to allocate anyway, reads the layer back as a bitmap, and hands the
pixels to the PNG writer. Not a screenshot in the operating-system sense — no
permission, no other window, nothing outside the composable it wraps. The same
draw pass, kept.

Two things fell out of the shape of it. The save control is *outside* the
captured box, because a save button in the corner of somebody's decklist is not
a thing anybody wants to post — which is also the honest way to keep the
showcase's promise of no controls on top of the deck: it is not on top of it, it
is beside it, and it fades back when nothing is pointing at it. And
`exportPicture` takes the encoded bytes rather than producing them, because what
a deck looks like is not something a state holder knows.

The whole reason this is worth having: every other builder can export a picture
of a decklist, and every one of them sorts it first. What comes out is a picture
of a deck nobody arranged.

Capture and read-back are Compose API that cannot be compiled here, so they are
alone in one small file — if CI disagrees about a name it will say which line.
Everything either side of that line is tested: the PNG writer in `:core`, and
`exportPicture` in `:ui`, including the case where the screen could not be read
at all, because a file of no bytes called `deck.png` is worse than being told it
did not work.

---

## 66 · The gaps turn out to be measurable

A gap says *these nine are the engine and those six are the handtraps*. That is a
claim about opening hands, and until now nothing in the program checked it.

`Hypergeometric` in `:core`: draws without replacement, exactly-k and at-least-k,
and the average. `GroupOdds.forGroups` runs it over the groups `Breaks` already
knows about. Deck statistics grows a block showing, per group, how often a hand
holds one of it, how often two, and how many on average.

What makes it worth having is that it asks for nothing. No tag, no category, no
form — the arrangement somebody made to *look* at is the arrangement that gets
measured. Every other tool that computes this wants you to build a list of
"starters" first, which is the same information typed in a second time.

Two things the tests caught. Every number I wrote from memory was wrong — three
copies in forty opens 33.8% of the time, not 33.8-ish as I had it, and twelve
starters open one 85.1% rather than the 88% I guessed. The reference values came
out of Python's `math.comb`. And the check that actually earns its place is the
one comparing this against `DeckStatistics.openingHandOdds`, which computes the
same thing a completely different way — that one multiplies out 1 − P(none) term
by term, this one sums combinations. Agreement between two independent
derivations is worth more than either being checked against itself.

The picture was drawn twice. The first attempt put "at least two" as a brighter
fill nested inside "at least one", which reads backwards: the eye takes the
brightest segment for the value and the dim remainder for an extension of it. A
tick mark on the bar is unambiguous — the bar is one number, the mark says where
the other falls along it. Caught in the prototype, before any Compose was
written, which is the third time that has paid for itself.
