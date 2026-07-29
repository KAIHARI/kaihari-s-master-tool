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

---

## 67 · The cards make room

Dropping a card into the middle of a pane drew a marker on the seam it would
land at. That is software's way of saying it. On a table you do not put a card
*onto* a row — you push two cards apart and slide it between them, and you can
see the slot before you let go.

So now the cards lean. `MakeRoom.shiftFor` gives each card a signed fraction of
its own width to move: the two at the seam lean furthest, the two behind them
half as far, everything else stays put. Together the marker and the parting are a
slot opening rather than a line appearing.

Three things had to be got right and only one of them was obvious.

**Nothing leans off its own row.** Cards 7 and 8 in an eight-wide pane are
neighbours in the list and strangers on the screen, and a card sliding sideways
with nothing beside it reads as a card falling over rather than as room being
made.

**A full row must not drift.** The first version tapered over a fixed reach of
two, which is fine in the middle of a row and wrong one card in from its left
edge: only one card leans left there, two lean right, and the row's last card
gets pushed out past the pane, where it is drawn cut in half. Fixed by giving
both sides the same reach — near an edge the notch keeps its width and loses its
tail. A row with empty space after it is exempt, because it has somewhere to lean
into.

**It is sprung, not snapped.** The seam jumps from one card to the next as a hand
moves; cards that teleported a quarter of their width to follow it would read as
the pane flinching. Held as a `State` and read inside `graphicsLayer`, so the
slide invalidates a layer rather than recomposing forty cards for every frame of
it.

Prototyped first as eight rectangles in a browser, at four seam positions —
middle of a row, one in from the edge, on the row boundary, and appending to a
part-full last row. The drift bug was visible there in about a second, which is
roughly a nine-minute round trip through CI that did not have to happen.

---

## 68 · The stacked view had thrown the gaps away

Turning a pane's density down collapses the copies: three Ash Blossom become one
tile with a 3 on it. The gaps were not drawn in that view at all, which said —
without meaning to — that changing how densely a deck is displayed discards the
arrangement. It does not, and the file proves it, but the screen disagreed.

A stack sits where its first copy sat, so a gap falls before the first stack that
begins at or after it. What that leaves is one genuinely undecided case: a gap
*inside* a run of copies, drawn between the second and third Ash. Once those
three are one tile it has no seam of its own.

I wrote the test first saying it should be dropped — rounding it to a boundary
nobody drew felt like inventing something. Then the implementation disagreed with
the test, which is the useful kind of argument to have, and the implementation
was right: a mark that vanishes when the density is turned down reads exactly
like the arrangement being lost, which is the thing this was fixing. So it moves
forward to the next seam there is, and two gaps landing on the same seam become
one. A gap inside the *last* run has nothing after it at all and is not drawn,
which is the rule `Breaks` already applies at the end of a section.

---

## 69 · The other piece of paper

The siding sheet is read by its owner, in the thirty seconds after a die roll.
The decklist is read by somebody who has never seen the deck, is holding it up
against the cards in a box, and needs to be able to add it up. They are almost
opposites, and the program only had one of them.

So `DecklistSheet`: counts rather than a line per copy, grouped into monsters,
spells and traps, with blanks for player, event and table because the point of
printing it is to fill those in at the venue. Inside each type the deck keeps its
own order — sorting alphabetically would be marginally faster to read and would
throw away the one thing this program exists to preserve, and a judge counts a
block, not a name.

The block that is not on any registration sheet is the one that matters most
here: **NOT RECOGNISED**, written as the complement of the other three rather
than as a fourth rule. A passcode the pool has never heard of still shuffles and
still counts towards forty, and a sheet that quietly left it out would be a sheet
that disagrees with the deck box in front of the judge. Written as a complement,
nothing can fall between the rules — a card the database knows but does not call
a monster, spell or trap lands there too, rather than nowhere.

Rasterised and looked at, which found the thing no test would have: everything
was in the left column and the right half of the page was blank, because the
whole list fit vertically. It reads as a form somebody forgot to finish. Columns
now break at half of what is left to draw rather than at the bottom of the page,
so a short list sits in two balanced columns and a long one still fills the page
and runs over. `pypdfium2` renders it; the check that it says the right things is
`pypdf` reading the text back.

---

## 70 · A word on a card

Every `.ydkx` the original tool ever exported carries
`notes: { cards: {}, pairs: {} }`, and it never put anything in either. The
placeholder has been riding around in people's files for years.

`CardNotes` fills in the first half: a line written against one card, the way you
would put a sticky note on the pile. *Only starter that plays through Ash.*
*Second copy is for the mirror.* It is the sort of thing you know while you are
building and have forgotten by the time you are siding, which is exactly when it
is worth having.

By passcode, so it belongs to the card and not to a position — all three copies
carry it, and cutting one does not take it away. That is the opposite of how
`Breaks` works, for the opposite reason: a gap is about the layout, a note is
about the card.

Two decisions worth their comments. Blank *removes* rather than stores, so
clearing the box is how you delete a note and there is no second gesture to hunt
for. And notes are not swept when a card leaves the deck: taking the last copy
out and putting it back is something that happens constantly while building, and
a note that did not survive that is a note nobody would trust.

The codec is the payload rule one level down. The object under `notes` also
carries `pairs`, so writing `notes` wholesale would delete it — the same argument
as the top-level merge and easier to get wrong, because the key being preserved
looks like one we own rather than like somebody else's unknown.

---

## 71 · The note, on screen

`CardNotes` needed somewhere to be written and something to show it. The sheet
opens from a card's long-press menu with the card beside the box, because a note
about a card you cannot see is a note about a passcode. The mark on the tile is a
folded corner rather than a badge — it is what you would do to the card itself,
and at deck-pane size a badge would be a second number to misread next to the
copy count.

Two things caught while writing it, both of the same kind: a design that sounded
right in prose and was wrong in behaviour.

I had it saving on the way out, with a comment about not queueing a write per
keystroke. Then Escape came up. `Overlay` closes layers by setting their state to
null, which would have thrown away the sentence — and the "hundred writes"
worry was wrong anyway, because `scheduleAutosave` cancels and re-schedules, so a
sentence is one write however long it took to type. It saves as it is typed now,
exactly like the deck's own notes.

Then binding the field straight at the model ate the space bar. A note is stored
trimmed, so `noteOn` gave back the text without its trailing space and the field
put it back a character short every time. The field keeps the raw text and the
deck keeps it tidy.

---

## 72 · The note turns up where the card does

Writing a note and then only being able to read it by opening the sheet you wrote
it in is a filing cabinet, not a note. So it appears wherever a card is being
*considered*: in the inspector, under the name, and under the hover preview — in
the pool as well as the deck, because the moment you point at a card in the pool
wondering whether to run it is the moment last week's answer is worth having.

Set apart by being in somebody's voice rather than by a rule or a box: italic, in
the accent. Everything else on those surfaces is what Konami printed, and this is
the one line that is not.

Not in the siding panel, where it would have been most useful of all — its
thumbnails are thirty-four points wide and a folded corner there is three pixels
that look like a rendering fault. It is the right place for the idea and the
wrong place for this drawing of it.

**Red, and the guard that caught it.** Adding `CARD_NOTE` to `Overlay` broke the
desktop build and nothing else — because the *test* that walks every layer,
opening and dismissing each one, is also an exhaustive `when` over the enum. That
is what it was written for, and it is the second time the enum has paid for
itself: a layer that cannot be dismissed is a layer Escape leaves on screen, and
the compiler now refuses to let one be added without saying how it opens, how it
closes, and how a test opens it.

---

## 73 · The mat was going to be missing from the picture

Found by re-reading the capture wiring rather than by any test, and it would only
ever have shown up in the exported file.

The showcase drew the mat with `Modifier.tableSurface(...)` and then added the
capture modifier beside it in the same chain. Draw modifiers run in chain order,
and a recording only covers what its own `drawContent` reaches — so the mat,
sitting earlier in the chain, would have been drawn to the screen and not into
the layer. On screen: a deck on a mat. In the file: a deck floating on nothing.

The mat is now drawn by a child of the captured box rather than by a sibling
modifier. Children are unambiguously inside `drawContent`, whichever way the
chain semantics fall — which matters more than usual here, because this is a
module that cannot be compiled or run in the environment it was written in, and
"I reasoned it through" is a weaker guarantee than "there is no ordering left to
get wrong".

---

## 74 · A function that turned out to be a wrong idea

`CardNotes.keepingOnly` dropped notes about cards the deck no longer holds,
written to be called when the file was saved. Nothing called it, which in this
repository has always meant the same thing: the API disagrees with the program.

It is not that the call site was missing. There is no moment at which it is the
right thing to do. Autosave *is* writing the file — constantly, seconds after
every edit — so "sweep on save" and "sweep whenever a card is cut" are the same
policy wearing a different hat, and the class documentation already said why that
policy is wrong. Removed, with the reasoning moved into the class where it now
explains an absence rather than a function.

That makes five: `ShootoutRun.undoGame`, `Board.clear`, `TableState.isEmpty`,
`SandboxState.returnToHand`/`discard`, and now this. Every one was a plausible
method nobody needed, and every one was found by noticing nothing called it.

---

## 75 · Eighty-seven megabytes to save a picture, and a proof that lasts

Two things about the compressor, both found by reading it again rather than by
anything failing.

**The match table was as long as the input.** One slot per byte, and a picture of
a deck at tablet resolution is about twenty-two megabytes of filtered scanlines —
so exporting one would allocate another eighty-seven megabytes of `Int`s, on a
tablet, to write a file. Nothing further back than the 32K window is ever
followed, so the table only ever needed to be a window long; positions older than
that alias onto newer ones and are unreachable by construction. 87 MB down to
128 KB, and the test that would find it wrong is the one that repeats a block
from further back than the window.

**The proof was a thing I did once.** Deflate was checked against Python's `zlib`
by hand — every chunk, every scanline, every pixel of a 6.4 MB image, exact — and
that check was worth precisely as much as somebody remembering to run it again.
So `InflateForTest` now reads RFC 1951 back, independently, in `:core`: fixed
Huffman only, its own copy of the length and distance tables so a mistake in
either cannot be shared, and an error rather than a branch for anything the
writer does not emit. Agreement between the two is evidence about the
specification instead of about a shared assumption, and it runs on every push.

The by-hand check still happened, and it still mattered — it is how I knew the
windowed table was right before writing a test for it. What changed is that it no
longer has to happen again.

---

## 76 · A removal that never happened, and the gap it uncovered

Entry 74 said `CardNotes.keepingOnly` had been removed. It had not: the edit
matched on a doc comment that did not match, changed nothing, and the tests
passed because the replacement test never called the function. The commit message
was true about the intent and false about the diff. It is gone now, and the
lesson is the ordinary one — an edit that reports success because nothing failed
is not the same as an edit that happened.

What found it was a sweep for `:core` API with no caller outside its own tests,
run once as a review rather than added as a lint. It turned up two more.

`TableState.sendFromHand` was `play` with the placement left at its default. Two
names for one operation, and the one with the shorter name was the one nothing
used. Removed; its tests now say `play`.

`TableState.toHand` was the opposite problem. Nothing called it because the
sandbox had no gesture for it — and that is a gap, not dead code. Every other way
off the board goes *forwards*: to the graveyard, to banished, to another zone. A
card put down in the wrong place could not be picked back up, so a slip meant
sweeping the table and building it again. Tapping a zone already turns the card;
holding it now takes it back into your hand. The core function was written and
tested a while ago and had simply never been reachable.

---

## 77 · The mat belongs to the deck

The application has four themes and every deck looked exactly like every other
one. Somebody who runs three lists should be able to tell which is open from
across the room, and "the free order of cards is how the player sees their own
deck" applies to the surface it is laid out on as much as to the order.

So the split: **the theme is the program's, the mat is the deck's.** Six cloths —
slate, leather, midnight, baize, wine, bone — plus "as the theme" for a deck
nobody has dressed, written into the `.ydkx` under `look.mat` by the same merging
rule as everything else in that payload. The deck brings its table with it.

Named choices rather than a colour picker, deliberately. Every one of these was
drawn under card art before being kept, because sitting under card art is the
only thing a surface has to do, and a free colour is a way to produce a mat that
fights the cards on it.

**What the prototype changed.** Rendering the six as swatches with cards on them
turned up the thing that would have shipped broken: a deck can now be on cloth
lighter than the application is wearing, and the showcase's caption takes its
colour from the theme. Bone mat, dark theme, and the deck's name is writing on
nothing. So `MatColors` gained an `ink` and an `inkQuiet` — a mat now knows what
colour writing goes on it — and the caption asks the cloth rather than the theme.
Three lines of consequence, and the only reason it was found is that the bone mat
was drawn next to the dark ones instead of being reasoned about.

The bone swatch also came out shouting, because the weave was tuned by looking at
bare cloth. It is drawn *behind* the cards and only really shows at the margins,
so the warp came down by a third.

**And the same mistake once more, in the corner.** The showcase's save tab was
black-backed with theme-coloured writing. It sits *over* the mat, so on the bone
cloth it would have been a black tab with invisible text in it — exactly the bug
the caption had, in the one place the caption is not. It now takes both its
backing and its ink from the cloth, and which way the cloth runs is read off the
ink rather than off the base colour, because the ink is already the answer to
that question and asking the base again is a second chance to disagree with it.

---

## 78 · And the library shows it

A mat that only exists while a deck is open is a mat you have to open a deck to
see. The library already loads each deck's payload — it needs it for the gaps and
the notes anyway — so every tile now draws its own deck on its own cloth.

That is the whole claim made good: three lists you can tell apart before reading
a name. It cost one parameter and one line, because the mat was put in the file
rather than in a setting.

---

## 79 · The arrangement was the least durable thing in the program

`SavedSnapshot` carries what a save wrote, and `SaveTracking.status` compares the
deck in front of you against it. Its own documentation says the rule: *everything
a save writes has to be in here; a field the writer sends but the snapshot
forgets is a field that reports itself saved the moment it changes.*

The snapshot held the deck, the name and the notes. The writer also sends the
`#ydkx-extended` payload — the gaps, the notes on individual cards, and now the
mat. None of those was compared, so all three reported themselves saved the
instant they changed, `shouldAutosave` declined, and nothing was written. Worse,
`toggleBreak` never even started the clock.

So: move a gap on a saved deck, change nothing else, close the app. The gap is
gone. The one thing this program exists to keep was the thing least likely to
survive.

The fix is the rule the file already stated: the payload goes in the snapshot and
into the comparison, and the two gap operations schedule a save like every other
edit. Five tests in `:core` and four in `:ui` — including the one that is easy to
get wrong, taking the *last* gap out, where the payload goes back to null and
"nothing to compare" and "nothing left" have to stay different answers.

Found by reading, again, and prompted by the mat: I went to check that choosing
one would persist, found it would not, and found that gaps had never persisted
either. Adding a feature is a good way to discover that the ground under it was
not solid.

**And the same omission one line further on.** `newDeck` puts the gaps and the
card notes back if you take it back, and did not put the mat back — a list of
three things where the third had just been added. Undo restores the cards, but a
deck is not only its cards: the gaps, the notes and the cloth are what made it
that deck rather than a list of passcodes. There is a test now that takes back a
new deck and checks all three.

---

## 80 · Making it impossible to forget again

Fixing the snapshot stopped the gaps, the card notes and the mat from *reporting*
themselves saved. It did not stop the next writer from forgetting to ask for a
save, and there was already one that had: `recordSiding` writes the plan into the
payload and asked for nothing. Recording a plan usually follows a swap, whose own
autosave has already fired by the time you press the button — so a plan recorded
at the end of a session was a plan that was never written.

Two explicit calls would have fixed the two known cases. What is there instead is
the pattern this file already argues for in the deck's own setter: the four
properties that make up the payload — the gaps, the card notes, the mat, and the
opaque payload itself — each start the clock when they are assigned. Six places
write the gaps; every one of them is covered by one line, and so is the seventh
somebody adds later.

The deck's setter has said why since it was written: *enforced here rather than
at the eight places that write a deck: one of them forgetting would leave...*
Everything about the deck that is not the deck had simply never been given the
same treatment.

---

## 81 · The board is the deck's table too

The sandbox drew its mat from the theme while the deck panes it was opened from
drew theirs from the deck. Laying an opening out on a board is the same deck on
the same table — walking into a differently-coloured room to do it is the sort of
seam that says *these are two screens* when the whole idea is that they are one
piece of furniture.

One parameter, and it composes with everything else the mat already touches: the
builder, the full-deck view, the picture that leaves, and the library tile.

---

## 82 · Recording a plan had no opposite

You could keep a siding plan and never get rid of one. A matchup recorded under
the wrong name, or with the wrong half captured, or recorded twice because the
first attempt was interrupted, stayed in the file forever — and a plan is not a
harmless thing to be stuck with. It prints on the sheet that goes beside the deck
box, and one press applies it to the deck.

Found by looking for verbs without opposites rather than by anything failing,
which is a sweep worth doing more often: *what can this program make that it
cannot unmake?* Gaps could already be closed, notes cleared, decks deleted, the
mat put back to the theme's. Plans could only be added.

Undone through the toast rather than the deck's undo stack, for the same reason
gaps stay off it: that stack is for edits to the cards, and mixing "I cut a card"
with "I threw away a plan" makes the one you wanted two presses further away.

---

## 83 · Turning "I looked at it" into something that runs

The same mistake was made twice in one afternoon and caught both times by
rendering a picture and looking: the showcase's caption, and then its save tab,
were coloured for the theme rather than for the cloth they sit on — which was
fine until a deck could bring its own mat and be lighter than the application.
Both would have shipped as writing the colour of the surface underneath it.

Looking works, once. `Contrast` in `:core` is the relative-luminance formula
every accessibility guideline uses, and a test in `:ui` walks all twenty-eight
combinations of seven mats and four themes, asserting that the ink clears 4.5:1
against its cloth, the quiet ink clears 3:1, the two inks are actually different,
and — the one that is really an assumption made visible — that the ink and the
cloth never run the same way, because the save tab picks its backing by asking
which of them is light.

Nobody is going to open twenty-eight screens one at a time, which is the whole
argument for the test. Everything passes today with room: the tightest is the
bone mat's quiet ink at 4.65:1 against a 3:1 floor.

Worth saying what this is not. It is not an accessibility audit — nothing here
checks the chrome, the chips, the card tiles or anything drawn over card art. It
checks the one surface a *deck* can change, which is the one the program grew a
new way to get wrong.

---

## 84 · What did I change since last week

The question every list gets asked between events, and the only way to answer it
was to open both decks and read them side by side. `DeckDiff.between` does it in
`:core`, and the library's tiles grew a *What changed…* next to *Open*.

Three decisions, all of them about matching how the question is actually asked.

**Counts, not copies.** Three Bonfire becoming two is one line saying `3 → 2`,
not a card leaving and a card arriving. And the line shows both numbers rather
than a signed delta, because `3 → 2` is what happened and `−1` is arithmetic
about what happened.

**Two lists, not one signed one.** *What went out, what came in.* A single list
sorted by delta puts a card that dropped a copy next to one that arrived, which
reads as two unrelated facts.

**The same cards in a different order is not "no change".** Everything else in
this program treats the arrangement as the player's own work; a comparison that
reported those two decks as identical would be contradicting the rest of the
application in the one place somebody went looking for a difference.

The tests include the property worth having: a card is never in both columns. A
count went up or it went down, and a diff that listed a card as both added and
removed is one somebody has to reconcile by hand.

And one cross-check, of the kind that has earned its place twice already. There
are now two answers to "what changed" that a player sees side by side: the siding
panel's swap and the library's comparison. They were written for different
questions — one lists a line per copy and only looks at the Main deck, the other
counts and looks at all three — so a test pins that they never disagree about the
part they share, and that only the comparison notices when the order moved.

---

## 85 · Undoing an import gave you back a deck that never existed

Two places replace the open deck wholesale and offer to take it back: starting a
new one, and importing a file. Both restored the cards and a hand-kept list of
everything else — and section 76 already recorded that list growing a fourth item
the day the mat arrived, in `newDeck`. `importFromFile` had the same list and it
was shorter still.

So: import a `.ydkx` over the deck you were working on, press undo, and you got
your cards back carrying *the imported file's* siding plans, gaps, notes and mat.
A deck that had never existed anywhere.

The fix is the shape the rest of this file keeps arriving at. `openDeck()`
captures everything that says *which* deck is open — name, notes, id, saved
snapshot, payload, gaps, card notes, mat — as one value, and `reopen()` puts it
back in one move. Both callers use both. A ninth thing about a deck is one field
in one place rather than two lists somebody has to remember to keep in step.

That is the third time in this stretch that a hand-kept list of things-about-a-deck
has been wrong, after the saved snapshot and `newDeck`'s undo. The lesson is not
"be careful with lists"; it is that a program with a concept it never named will
keep rediscovering it one field at a time.

So the two remaining places that assign those eight fields — opening a saved deck
and importing a file — now go through the same door. Four hand-kept lists became
one named thing, and there is a test that saves a deck with a gap, a note and a
mat, starts a new one, opens the old one again, and finds all three where they
were. Opening a deck and putting one back turn out to be the same move, which is
the sort of thing that is obvious only once the concept has a name.

---

## 86 · The other half of the placeholder

`notes: { cards: {}, pairs: {} }` has been in every file the original tool ever
exported, and it never put anything in either. Section 70 filled in the first
half. This is the second, and with it the placeholder is finally what it always
said it was.

A pair note is the half of deck building a decklist cannot hold at all. A deck is
not forty cards; it is a handful of two-card openings and thirty-odd cards that
make them more likely — and *Poplar plus any Level 1 is Apollousa* is the sort of
thing you work out once, rely on for a month, and cannot reconstruct after three
weeks away from the deck.

The key is normalised so a pair reads the same from either end, because two ways
to say one thing would mean a note written from one card being invisible from the
other. A card paired with itself is not a pair and is refused rather than stored.

The codec is deliberately a *second* codec rather than an extension of the card
one. They share an object under `notes` and each owns one key and preserves
everything else it finds there — which is the payload rule the whole file
follows, applied one level further in, to two codecs that now have to live
together. The test that matters is that they compose in either order and produce
the same object: neither can destroy the half it does not understand.

**Authoring it needed no new gesture.** The selection already exists — arrows and
shift on a keyboard, "select" and "select through here" on a tablet — and two
cards picked out is exactly the shape of a combo. So the card menu grows one item
when the selection is two, and nothing at all when it is one or three. A note
about three cards is a note about the deck, which already has somewhere to live.

Reading it is the part that decides whether the feature exists at all. A pair note
is listed on *both* cards' sheets, under the card's own note, with the other
card's name — and tapping one opens it. Written from a selection, found from
either end.

Two things caught by reading it back before CI had finished with it.

Two positions in a deck can hold the same card, so selecting two copies of Ash
would have opened a pair sheet for a note that can never be written — it takes
what you type and saves none of it, which is worse than no sheet. It refuses now.

And both note sheets put a text box next to a card in a `Row` and asked the box
to `fillMaxWidth`. Inside a Row that measures against the whole row rather than
what is left of it, so the box would have run out past the card standing beside
it. `weight(1f)` is the answer, and this is the second time in this loop that a
layout has been wrong in a way only arithmetic about what a modifier *means*
would catch — there is no screen here to notice it on.

So it is `tools/check-row-width.py` now, the fourth of these. It reports a
`fillMaxWidth()` whose nearest enclosing layout is a `Row` and which is written
more than four lines after that Row's own head, because `Row(modifier =
Modifier.fillMaxWidth())` is correct and everywhere. Swept the whole module: the
two note sheets were the only instances, and both were mine from an hour ago.
Verified the way the last one was — by putting the bug back and watching it
fire.

---

## 87 · Pick up a card and see what it works with

A pair note that can only be read in the sheet it was typed into is a filing
cabinet. So: hold one card — the keyboard cursor is a one-card selection, and so
is "Select" from the card menu — and the cards it is noted with take a quiet ring
in the section's colour.

That is as close as this program is going to get to drawing a line between two
cards. An actual line was the first idea and it does not survive contact with the
thing this application is about: the cards *move*. A line between two grid cells
has to be re-routed every time somebody rearranges the deck, which is constantly,
and it would have to cross the cards in between. A ring on the partner says the
same thing, costs nothing to draw, and is right wherever the card happens to be.

Only for a single card, on purpose. With two picked out the answer would be about
the selection rather than about a card, and a deck lighting up for a range is
noise. With none it is a lit-up deck for no reason.

The ring is drawn *inside* the selection's own border and thinner, so a card that
is both picked out and connected reads as both rather than as two states fighting
over the same edge.

And `n` writes on whatever is being held. One key rather than two, because it is
one question — *what do I want to remember about this* — and which sheet answers
it depends only on whether the cursor is holding one card or two. Three says
nothing rather than guessing which two were meant. The whole deck could already
be arranged from the keyboard; now it can be annotated from there too, without a
hand leaving it.

---

## 88 · Two things wanted the same corner

Found by reading the showcase end to end rather than by running it, which is the
only way anything gets found in a module that does not compile here.

The save tab is aligned to the bottom-right of the screen. The caption under the
deck ends with the card counts, right-aligned. Those are the same corner. It
would not have looked like a bug in a screenshot of an empty deck, because it
only collides when the deck reaches the bottom — and the deck always reaches the
bottom, by construction: `GridFitter.fitAll` takes the *largest* cards that fit,
so the arrangement fills the height it is given almost exactly. The one view
whose entire argument is that nothing sits on top of your deck would have had a
button sitting on top of it.

A reserved strip fixes it, and reserving it as padding on the `BoxWithConstraints`
rather than as a margin on the tab is what makes it real: the fitter reads its
height *after* the padding, so this is also how the fitter is told the strip
exists. Simulating the fitter against the new height, the strip costs at most one
column — 1600×1000 with 40/15/15 gives 15 columns at 104px, and a 60-card main
gives 17 at 92px, the same numbers as before in every case but one.

---

## 89 · A deck that saves itself has no yesterday

Autosave is a straight improvement with exactly one cost, and it took until now
to see it: every edit is permanent the instant it happens. Open the list you took
to a regional, cut a card, and the sixty you registered no longer exist anywhere.
That is the worst thing this application can do to somebody's work, and it was
introduced by a feature nobody would want reverted — so the fix belongs beside it
rather than in a backlog.

**When a copy is kept.** Not per save; autosave writes constantly and a timeline
of every keystroke is not a record. A copy is kept when the deck is about to
change after standing still for six hours, so what is kept is *the deck as it
stood at the end of the last time you worked on it*. One version per sitting.

**What is let go of.** Deliberately not the oldest — a history that drops its far
end quietly becomes "the last few days" no matter how long you have had the deck.
Dropped instead is whichever version sits closest in time to the ones either side
of it: the one whose absence opens the smallest hole. Repeated until the count
fits. Five versions from one evening collapse to one and three from months apart
all survive. Ties go to the earlier index, which sounds like it would erode the
old end and does not: removing one widens its neighbours' spans, so the next
choice moves elsewhere. That is checked rather than argued — 0..10 thinned to
five keeps 0, 4, 6, 8, 10.

**Going back keeps the present on the way past**, which is why the button asks
nothing first: the thing that undoes an afternoon also undoes itself. And it is
an ordinary edit of the deck rather than a second kind of open document, so the
deck keeps its id, its name and its place in the library, and autosave and export
carry on not knowing it happened.

There is no delete. Versions thin themselves, and a named one stops being kept
the moment it stops being named — so a delete button would be a third way to say
something already sayable, standing next to the one control here that changes a
deck. `deleteVersion` was written, tested, and then removed when the sheet turned
out not to want it. Seventh removal by that rule.

### The guard that was documented and never written

Adding a table meant reading `README.md:145`, which says `MigrationTest` upgrades
a real old database and compares it against a fresh one. It does not exist.
`core/build.gradle.kts` points at it too, as the reason `verifyMigrations` is off.
Two places asserting a safeguard that was never written — and the failure it
describes is invisible on every device used for testing, because a fresh install
is correct whether or not the migration exists.

So it was written first, and then verified the way the four lint scripts were:
`DeckVersion.sq` was added *without* `2.sqm`, and the test went red naming
`deckVersionEntity` in its message. Then the migration, and green. The version 1
schema in it is transcribed from `3fec0b9` and carries a note not to edit it —
it is a record of what is on somebody's tablet, not a copy of the current files,
and editing it to make a failure go away is the one thing that would make it
useless.

The JDBC driver it needed was already declared in `jvmTest` and the source set did
not exist. Which meant `DeckRepository` had never been tested at all — sixteen
tests later it has been, and two of them are about `updatedAtEpochMs` not moving
on a save that changes nothing: it would otherwise restart the clock above, and
the real edit right after a no-op save would go unrecorded.

### And the sheet

Prototyped in HTML first, as everything visual here is. The first version put four
buttons on every row — *what changed*, *go back*, *keep*, *let it go* — sixteen
buttons on screen, and the timeline that was the point of it disappeared behind
them. What survived: the row itself opens the comparison, one star keeps it, one
button goes back to it, and delete does not exist. A named version is a filled
node with the name beside it, which is the thing you scan a history for.

---

## 90 · The program is built for a venue with no signal and looked worst there

Written down in three places: the app keeps an outdated card pool rather than
none, a failed refresh never clears the cache, and the whole thing is offline by
design. And a card whose image had not arrived drew as a grey rectangle with its
name written across the middle. So the state this application was designed for
was also its ugliest — forty cards touching, reading as a wall of labels.

So the card gets drawn. Frame from `frameType` (the same key `isExtraDeck` uses,
so a `fusion_pendulum` is drawn as the Fusion it is summoned as), the name, the
level as stars, the attribute as a dot, ATK and DEF, and an abstract field in the
art window seeded by the passcode. Deterministic, so three copies of a card look
like three copies and the one beside them does not, and so the PNG exported of an
arrangement matches the arrangement. It is not a forgery of the artwork and is
not trying to be.

Two things the prototype settled that reasoning had not:

**The bands are fixed heights, not proportions.** The first version let the name
band grow to fit, so one long-named card stepped every band across its row out of
line with its neighbours. Cards in a pane *touch* — that misalignment reads as
broken rather than as forty cards. The pip row is reserved even on a spell for
the same reason.

**The initials in the middle of the art window went.** "AB" floating in a box is
what a placeholder avatar looks like. The attribute dot replaced it: it is real
information, it uses the seven colours the statistics panel already uses, and it
is quiet.

### The palette was solved, not chosen

`CardFrameContrastTest` was written the way `MatContrastTest` was — and then
checked against the numbers *before pushing*, because `:ui` does not compile
here and a failing assert costs a ten-minute round trip. Nine of eleven frames
failed. The footer sits over the dark end of the frame's gradient, and on the
Effect frame the ink was 2.1:1 there. Gold level stars on the gold Normal frame
were 1.3:1 — invisible, and visible as such in the screenshot once looked for.

The first fix was wrong: solving for 4.5:1 across the gradient flattened it to
nothing on five frames. The actual error was the gradient, not the ink — a real
card frame is one colour with a sheen, not a spotlight. Narrowed to ±16/20% and
every frame clears 4.5:1 on the base and 3.3:1 at both ends, with two of them
(Fusion, Trap) taking light ink because their bases sit in the middle. The stars
are now ringed in the frame's own ink, which is by construction the one colour
that reads on every frame there is — so the star's visibility is carried by an
assertion that already exists rather than by a new one about gold.

---

## 91 · Two nine-card groups were two identical rows

The group odds listed each group by size and nothing else, so a deck with two
nine-card groups showed two rows reading `9`, and the only way to tell which was
which was to count along the deck.

The obvious fix is to let people name a group, and it is the wrong one. `Breaks`
says what it is in its own first paragraph — "not a card, not a label, not a
folder" — because a gap is what somebody does with their hands and costs nothing
to ignore. A typed name is a folder with extra steps, it needs storing, it needs
tracking through every edit that moves the gap it belongs to, and six weeks later
it is wrong and nobody notices.

So the name is *derived*: the archetype at least half the group belongs to, else
the kind of card when they are all one kind, else the card there are most copies
of. Nothing stored, nothing asked for, and it cannot go stale — cut the last
Snake-Eye card out of a group and it stops being called Snake-Eye. Null when the
group has nothing to say for itself, which matters more than the names do: eight
unrelated cards are eight unrelated cards, and inventing a label for them would
be the program claiming to have understood something.

Three tests failed on the first run and two of them were my expectations, not the
code: five monsters with two Snake-Eye among them *are* five monsters, and a
weaker true thing beats nothing. The third was real — a one-card group came out
labelled "Monsters", which says less than the card already visible above it.

The panel's own line still ends "nothing here was tagged or named", and it is
still true.

---

## 92 · Put a pile down, pick a pile up

The gaps have been the groups since the day they were drawn — the statistics
panel measures them, the tidies draw them, the full-deck view separates on them,
and a `.ydkx` carries them. And the only way to *hold* one was to select nine
cards one at a time or drag a careful box round them.

On a table you do not select nine cards. You put your hand round the pile and
lift it. So: shift-`g`, next to the `g` that makes the gap, and "Pick up this
group" on a card's own menu for the tablet with no keyboard. Everything that
already works on a selection — carry it, side it, note two of it — now works on a
group, and none of it needed to know that groups exist.

The one thing worth getting right was the anchor. The focus is derived from the
anchor rather than stored, so anchoring at the near end of the run would put the
cursor at the far end and the next arrow key would jump across the pile you just
picked up. Anchoring at the *far* end leaves it where your hand already was.

The menu item only appears in a section that has gaps in it. Without any, "this
group" is the whole section, and an item that quietly means select-all is one
nobody presses twice.

**And the red build in the middle of this.** The group names went out and CI came
back failing on one `:ui` test, which asserted two groups came out called
"Monsters". They came out called nothing: the test harness deliberately never
seeds the card pool — `addCard` takes the card itself — and a derived name is
read *off the pool*. My assumption, not a defect.

Except it pointed straight at one. Decks arrive from other programs carrying
passcodes this database has never heard of, and a name read off the half of a
group that resolved is a claim about the half that did not. So a group names
itself only when more than half of it is known, and there is now a test for the
case the failure was standing in for.

**And then a second red build, from the fix.** Seeding the pool in that test
meant assigning `state.index`, which has a private setter — correct, because the
pool is loaded and never handed in. Not a warning: a compile error, in the module
that does not compile here, so it cost the whole round trip to learn. The
assertion is gone; what a group is *called* is `:core`'s business and is covered
there against real card shapes, and what `:ui` owns is the wiring.

The mistake became the sixth script under `tools/`. The other five catch a name
that does not resolve; this one catches a name that resolves perfectly and cannot
be written to — the same cost, a different error. Verified the way the others
were, by putting the line back and watching it fire.

---

## 93 · The build was shouting over itself

Two red builds in a row, and both times the *first* thing I did was fetch the end
of the log and find 120 lines of `org.gradle.internal.execution.steps...`. Every
step in the workflow ran with `--stacktrace`, and Gradle prints its internal
trace *after* the thing that actually broke — so the compiler's own
`e: file://...` lines and the failing test names sit hundreds of lines further
back than anything a reader reaches.

That is not a small cost here. This module cannot be compiled in this
environment; reading the end of a CI log is the entire diagnostic path. The flag
has never once helped and has twice made a ten-minute round trip into a
twenty-minute one, the second time by forcing me to *infer* the compile error
from the shape of my own diff rather than read it. The inference happened to be
right, which is not a system worth keeping.

So it is gone from all four steps. Gradle still tells you to re-run with it if a
Gradle-level fault is genuinely the question, which — in every failure this
project has had — it has not been.

---

## 94 · Forty cards, one colour

The drawn faces work, and then a real deck exposed the thing a mixed sample hid:
a Main deck is mostly effect monsters, and every effect monster had the same
orange frame around the same orange field. Three copies of a card *should* look
identical — that is the point — but forty different cards should not.

The card already knows something nothing was drawing: its attribute. So the art
field is pulled towards it. A FIRE monster is warm, a DARK one is not, and the
arrangement becomes readable across the pane in a way the order alone cannot
manage.

Found the ceiling by looking, as usual. At 0.42 a WIND Synchro came out pale
green, which in this program is what *spell* looks like — the tint had started
overruling the frame. At 0.26 the frames all keep their meaning and the wall is
still varied. That number now has a test on it, because the failure it prevents
is one nobody would call a bug: the card would just quietly say the wrong thing.

Spells and traps take no tint at all. They have no attribute, and it means green
stays a reliable answer to "is that a spell" rather than a probable one.

One thing followed from it: the attribute dot in the corner now sits on a window
pulled towards the dot's own colour, so it needed the same ink ring the level
stars needed, for exactly the same reason.

---

## 95 · The one verb that took your work with no way back

Found by reading the restore wiring and following what `onOpenDeck` actually
does. Opening a saved deck from the library calls `load`, and `load` did two
silent things.

**It cancelled a pending autosave rather than writing it.** Edit a saved deck,
go straight to the library, open something else, and the last second and a half
of work is gone — no message, and the deck on disk quietly disagrees with what
was on screen a moment ago. This half turned out to be bigger than `load`: see
below.

**And it replaced a never-saved deck outright.** What makes this one clear rather
than arguable is that `newDeck` has always offered that deck back — it captures
`openDeck()` and puts it in the toast. `load` does exactly the same thing to
exactly the same work and offered nothing. The program already knew this
mattered; one of the two doors just had no handle on the inside.

Both fixed: pending changes are written before the deck is let go of, and a
never-saved deck comes back from the toast with its name, its gaps, its notes and
its cloth, still unsaved, exactly as it was. Only when there is something to lose
— a saved deck is on disk either way, and an empty one is not work, so neither
raises a word.

The frame that found it is worth keeping: **follow a new call all the way down
and ask what the thing at the bottom does to what was already there.** The
restore path was new; `load` was not, and had been quietly doing this since the
library was written.

And the sixth lint paid for itself the same hour it was written. Two of the tests
for this reached for `state.toast = null`, and `toast` has a private setter too.
It caught both before the push — which is the entire point of it, and the first
time one of these scripts has caught a mistake it was not written for.

### The test failed, and it was right to

CI came back with one failure out of 253, and — for the first time — the log said
so in plain words two lines from the end, because `--stacktrace` had just been
taken out. That change paid for itself within one build.

The test that failed was the one about writing the pending save. It failed
because it reaches `load` by way of `newDeck`, and `newDeck` had already thrown
the pending edit away. Which is the actual shape of the defect: it was never
`load`'s. `releaseOpenDeck` is the one place all three swap paths — new deck,
open deck, import — go through to let go of the old deck's id, and it cancelled
the autosave rather than writing it. All three lost the last second and a half.

So the flush lives there, at the choke point, and reads everything it needs
*before* the caller replaces it — the write has to be of the deck as it was when
it was let go of, not of whatever arrived in its place. The fix I had written was
correct and in the wrong place, and a test I wrote to prove the small version
found the large one.

---

## 96 · A deck over months, not over three calls

Every test written for the history so far checks one step. The rules that
actually matter are about the shape of a history after a long time, and none of
them can fail in three steps: that it never grows without bound, that a named
version is still there after sixty more sittings, that the far end does not
quietly fall off, and that going back and forth never loses a deck.

So: sixty simulated sittings — three edits twenty minutes apart, then a twenty
hour gap — and the invariants asserted over the result. Twelve versions, spread
across the whole span rather than clustered in the last week, the registered list
still named and card for card what it was, and six rounds of restore where the
deck being left is provably still reachable afterwards. That last one is the
property the *absence of a confirmation* on "go back to it" rests on, and it had
never been checked more than one round deep.

It failed first time by twenty-four minutes, and the failure was worth having: I
had asserted the oldest version is the deck as it *first* stood. It cannot be. A
copy is only ever of what was there before a change, and a deck's opening state
is written over inside its own first sitting — so the earliest version there can
be is how that sitting was *left*. Correct behaviour, wrong expectation, and now
a stated property instead of an assumption.

---

## 97 · A drawer you could put things into and not open

The history shipped reachable from the library, and the builder got "keep this
one" a push later. Which left the obvious asymmetry: standing in front of the
deck you could *add* to its history and had to walk to the library to see what
was in it.

Both callers want the same six things — the versions, the deck they are read
against, keeping one, naming one, going back to one, reading what changed — and
none of that is about which screen asked. So the wiring moved into
`DeckHistoryHost`, the library got shorter, and the builder's menu got one line.

Two details the second caller forced out into the open. The list is read off what
is *stored*, so `showHistory` writes the pending autosave and opens the sheet
*after* the write rather than racing it — otherwise the top row would claim the
deck as it stands is a second and a half out of date, which is the sort of
nearly-right nobody catches. And restoring from the builder goes back through
`load` rather than setting the deck directly, so it picks up everything `load`
learned this week: it writes what is pending, offers the deck being put down
back, and re-reads the gaps and the cloth off the restored file.

The lamp did not happen. A radial shade across the pane — brighter where the
light falls, deeper in the far corner — reads beautifully in a prototype and puts
a 20% black wash over the bottom-right cards, which walks their name straight
through the 4.5:1 that `CardFrameContrastTest` guarantees. The test would still
pass, because it tests the palette rather than the palette under a lamp, and that
is exactly what makes it the wrong trade: a small visual gain that quietly turns
an existing guarantee into a lie. Closed rather than shipped.

---

## 98 · Reading the database before the write you just started

Found by asking the concurrency question of the path I had just added: restoring
a version ends in `state.load(id)`, and that id is *the deck already open*.

`load` read the stored deck first and let go of the outgoing one second. Letting
go is what writes the pending autosave — so for a while the read was of the
version from before that write landed. When the two ids differ nobody notices.
When they are the same, the screen shows the deck from before your own last edit
while the disk holds the edit, and neither of them is wrong on its own.

Reachable without the restore path, too: tap Open on the deck you are already
editing, from the library.

So the order is inverted — let go, wait for the write it starts, then read — and
`flushPendingSave` hands its job back so a caller about to read the database can
wait for what it just began. Inverting it created a case that did not exist
before: a deck that is not there is now discovered *after* letting go of the one
you had, so a miss takes that back rather than leaving you holding nothing.

The restore path is safe by construction rather than by luck, and it is worth
saying why: the history is a modal sheet, so no edit can happen between
`showHistory` writing and the restore reading. Nothing is pending, the flush is a
no-op, and the read returns the restored deck.

---

## 99 · Which of these broke while I was not looking

A banlist update makes decks illegal without anybody touching them. The builder
has always said so about the deck in front of you — the chip in the top bar reads
"3 issue(s)" and opens the list — and the library, which is where nine decks sit,
said nothing at all. So the answer to *which of mine are still legal* was to open
all nine.

Each tile now runs the same validator the builder does, against the same banlist,
and says so when the answer is bad. Only when it is bad: nine green ticks are
nine things to read and nothing to learn, and the shelf is scanned rather than
read.

Nothing new was written to do it. `DeckValidator` is pure, total and already
tested; the library already had the card pool for the three faces it draws; the
only thing that had to be handed in was which banlist to check against, and that
belongs to the builder because the toggle does.

---

## 100 · A label that was true when it was written

The library tile said "Includes siding data" whenever a deck had an
`#ydkx-extended` payload. When that line was written the payload only ever held
siding, so the two meant the same thing and the shortcut was free.

Then this loop put the gaps in the payload, and the card notes, and the pair
notes, and the mat. Which means a deck built in this app and given a wine cloth
has been announcing siding data it does not have — and the tile that says so is
the one place a player would look to find the deck they can side with.

Nothing broke. No test could have caught it, because the claim was about meaning
rather than behaviour, and the meaning drifted underneath a line nobody had
reason to re-read. It now counts the plans — `SidingCodec.read(...).size`, the
same reader the siding panel uses — and says how many, or nothing.

Worth naming as a class: **a shortcut that is only true because two things
currently coincide.** Every feature added to the payload this loop widened the
gap between "has a payload" and "has siding", and the line that conflated them
sat there getting quietly more wrong. Swept the rest of the codebase for the same
equivalence — three hits, all of them genuinely about whether a payload exists at
all, which is what `isYdkx` means and is correct.

---

## 101 · Undo gave the deck back without its name on the door

Fourth read of the same forty lines, fourth thing found. `newDeck` let go of the
open deck and *then* recorded what it had let go of — so what it recorded was a
deck with its id and its snapshot already cleared, because clearing them is what
letting go means.

Everything visible came back. The cards in their order, the gaps, the notes, the
cloth. What did not come back was the deck's identity: after undo the toolbar
read NOT SAVED, and the next save wrote a *second copy* beside the original
rather than back into it. Two decks called Snake-Eye, one of them a ghost, and
nothing anywhere said so.

`load` and `importFromFile` both capture before they release. `newDeck` was the
odd one out, and the fix is moving one line up.

Which is worth sitting with. This region has now been read four times in a day
and given up something each time — the autosave that was cancelled instead of
written, the read that raced the write it started, and now this. What they share
is an *ordering* between two statements that both look like bookkeeping, where
the wrong order is silent, plausible, and only wrong in a case nobody
demonstrates by hand. The reason the fourth read still found something is that
the first three were looking at behaviour and this one was looking at sequence.

---

## 102 · Two projections nothing projected

The unused-API sweep, run over what this loop added. `DeckVersion` carried a
`mark` that built the `VersionMark` the retention rules take — and nothing ever
called it, because the repository builds one straight off the database row. It
was written on the assumption that the two would meet somewhere and they never
did.

And a `toDocument()`, parallel to `StoredDeck`'s, which one test used to prove a
version writes out as a file. Nothing else. The test above it already asserts the
version holds the right cards, which is the property that mattered; writing them
out through a projection nobody calls proves the projection, not the version.
Eighth and ninth removals by this rule.

`Contrast.READABLE` and `READABLE_LARGE` came up in the same sweep and stay. They
are used only by tests too, and that is not the same thing: contrast in this
program is checked at build time rather than at runtime, so the tests *are* the
consumer. A threshold whose only job is to be asserted against is doing its job.
Worth writing down, because the next sweep will find them again.

`:core` carries the arithmetic for all of it, at **913 tests**, up from 249, plus
**258 in `:ui`** where there were none — a module that cannot even be compiled in
the environment this was written in. Six scripts under `tools/` stand in for the
compiler that is not here, and each of them exists because of a specific mistake
that cost a ten-minute round trip.

Still open, in the order they are worth doing:

**Every card composes a `BoxWithConstraints` — a known, deliberate cost.** The
drawn face needs its own width to size the name and the footer, and that means a
subcomposition per visible card: roughly a hundred of them with three panes and a
pool full of results. It has not been measured and there is no way to measure it
here. The fix — measure at a fixed size, scale with a layer — is a page of blind
Compose whose failure mode is every card rendering at the wrong size, which is
exactly the sort of thing CI cannot see and a screenshot would have caught in a
second. So it is written down rather than attempted, and it is the first place to
look if the tablet ever feels heavy.

**A lamp over the pane — closed, not built.** A radial shade, brighter where the
light falls and deeper in the far corner, reads beautifully in a prototype and
lays a 20% black wash over the bottom-right cards. That walks their names through
the 4.5:1 `CardFrameContrastTest` guarantees — and the test would still pass,
because it checks the palette rather than the palette under a lamp. A small
visual gain that turns an existing guarantee into a lie is the wrong trade.

**Dragging a gap along — closed, not built.** A gap is placed from a card's menu
or with `g` on the card under the cursor, and moving one is two taps. I went
looking for the drag three times and every version needs a hit target: the mark
is two and a half points wide, so grabbing it means claiming a strip of the card
beside it, which takes the leading edge of a card away from the gesture that
picks the *card* up. Long press is taken by the card menu. Two taps already
works, and a gesture that makes an existing one worse is not an improvement. It
goes here rather than in the backlog until somebody has a better idea than these.

**Siding for an opponent whose file does not say how — deliberately not built.**
They side from their own plans when the file carries them (section 57), and a
plain `.ydk` still sits there unchanged. Three shapes were considered and all
three are worse than nothing: cutting from their Main at random dresses a guess
as a measurement; adding their cards without cutting quietly changes every draw
probability; and asking the player to author both halves of a plan for a deck
they do not own is a form nobody would fill in twice. Their deck feeds only what
you *see* across the table — it does not enter the report at all — so the honest
answer is to leave it alone until somebody has a better idea than these.

**A picture of the deck, a decklist for a judge, and a mat that belongs to the
deck** all arrived after that list was first written, and each one turned up a
defect in something already there — the showcase sized for the wrong number of
rows, the capture recording everything except the table, the compressor
allocating eighty-seven megabytes, and the arrangement never being saved at all.
That last one is the pattern worth naming: **adding a feature is the most
reliable way to find out that the ground under it was not solid.** Four of the
five worst things found in this stretch were found while wiring something new on
top of them, and none of them by a test that already existed.

## 103 · The shelf could only be asked what things were called

The library's search field matched deck names and nothing else — a
`contains` against the normalised name, which was the right amount of code the
day nine decks were three months old.

The question it cannot answer is the one actually asked of a shelf. A deck's
name is what you typed months ago, once, in a hurry. Its cards are what you have
been looking at all week. *Which of these still plays Maxx "C"* — after a
banlist, that is four decks to open one at a time. *The one I was testing Droll
in* is not a name at all; it is a memory of a card.

So the same field answers both, and `LibrarySearch` in `:core` decides how. Three
things fell out of writing it:

**A deck called it beats a deck that plays it, regardless of score.** Rank them
together and a card scoring an exact 1000 will outrank a deck name scoring 900,
so typing the name of a deck on the shelf puts three other decks above it. Name
matches sort first as a block, and the card block follows.

**Copies do not reorder decks.** The tempting tiebreak — three copies is more
the answer than one — turns out to be worse than doing nothing, because doing
nothing preserves the order the shelf arrived in, and `SELECT * FROM deckEntity
ORDER BY updatedAtEpochMs DESC` means that order is recency. The deck you touched
yesterday is a better guess at which one you meant than the deck running one more
copy. My first test asserted the opposite and its own name said so: *the deck
with more copies is not promoted over the deck with a better match* — and then
expected exactly that promotion, in a case where both decks matched at the same
tier so nothing but copies could have moved them.

**A passcode the pool has not resolved matches nothing.** Not its digits, not a
partial. An unresolved card means the database has not loaded, and saying "this
deck contains what you typed" about a card nobody can see is a claim made out of
an absence.

Every distinct passcode across the whole shelf is scored once rather than once
per deck: nine decks share most of their handtraps, and the scorer — bounded
Levenshtein against thirteen thousand names — is the expensive part. Below three
characters the cards are not searched at all, the same floor the card search uses
for its text mode, because two letters match most of the pool and a shelf where
every deck reports forty hits is a shelf with no search on it.

The tile says why it is there: `3 × Ash Blossom & Joyous Spring · 1 × Maxx "C"`,
two cards and a `+n more`. The count is the useful half — whether the deck you
are looking for ran three of it or splashed one is most of what you wanted to
know before opening it.

Fourteen tests. Thirteen passed first time and the fourteenth was mine.

## 104 · Three programs in a trench coat

`when (screen)` and nothing else. Tap the library and the table is replaced,
instantly, by a shelf; tap back and the shelf is replaced by the table. Every
card in this program is dealt, carried on a spring, leaned aside to make room —
and then the three screens holding all of that swapped like slides.

It is now an `AnimatedContent`: the arriving screen fades up from 98.5% over
220ms and the departing one goes the same way in 150ms.

Two things decided the numbers, and both were about not competing:

**The deal is the event, not the crossing.** Open a deck from the library and a
moment later forty cards drop onto the table in a wave. That is the animation
that means something. A screen that took half a second to arrive would be sitting
on top of it, and the two would read as a program being pleased with itself. So
the crossing is short enough to be over before the cards land.

**A percent and a half, not five.** These screens are used with a thumb, and a
finger already travelling towards a control does not want the control moving away
from it. At 0.985 on a tablet the frame moves about fifteen pixels — enough to
read as having come forward, not enough to miss anything.

`compose.animation` is now named in `ui/build.gradle.kts` rather than arriving
transitively through `compose.foundation`. It was already on the classpath, which
is exactly the kind of thing that stops being true in a version bump nobody
connects to a screen that stopped dissolving.

The one hazard in `AnimatedContent` is that its content lambda takes the state as
a parameter and the enclosing scope still has the live one in view. Read the live
one and both copies draw the arriving screen, so the crossing animates one thing
into an identical thing and looks like a flicker. The comment sits on the `when`
rather than on the parameter, because the `when` is where somebody would make the
mistake.

## 105 · And the screen you just left still took your taps

Written the same hour as the crossing, because the crossing is what created it.

For 150 milliseconds after leaving a screen, that screen is still composed, still
drawn, and — this is the part — still hit-testable. The arriving screen is on top
of it, but a screen has no obligation to have something clickable under wherever
your finger happens to be, and a press that nothing on top claims falls through
to what is underneath. So a double-tap on a deck tile opens the deck and then,
150ms later, opens it again. Leave the builder for the library and a tap landing
in the gap still adds a card to a deck nobody is looking at any more.

None of this was reachable before, because before, screens swapped between two
frames. **Adding the animation added the window.** Which is the same lesson as
section 102's, from the other end: this time the new thing did not uncover a
defect underneath it, it created one directly above it.

The fix could have gone at each route out — five callbacks, five `if the screen
is still mine` guards. It is at the boundary instead: the departing copy gets a
`Modifier` that consumes every pointer event on the initial pass, which travels
parent to child, so nothing below it ever sees a down it would act on. Five
routes out are five things to remember; the controls behind them are hundreds.

The state it keys off — `showing != screen` — is a snapshot read inside the
retained composition, so the outgoing copy is invalidated and recomposes into its
blocked state the instant the target changes, rather than waiting for a
recomposition it has no other reason to have.

## 106 · The rule was already written down, on the function I copied

Two commits after adding the library search, the second-caller sweep found it:
`LibrarySearch.run` was being called from inside composition, in a
`remember(decks, query, index)`, on every keystroke.

The card search does not do that, and says why on the line where it does not:

> Scoring 13,000 names with a bounded Levenshtein is far too much work for the
> frame thread, and `scope` is the composition's.

The library search is *the same scorer*. Fewer names — a few hundred distinct
cards across a shelf rather than the whole pool — but the same reason, and the
frame thread is not a place where a smaller amount of the wrong work becomes the
right work. It is a `LaunchedEffect` onto `computeDispatcher` now, which is
exactly the shape the pool search has.

It is *not* debounced, and that is a deliberate difference rather than an
omission: a few hundred names is three percent of the pool, each keystroke
cancels the last, and the shelf is what you are staring at while you type. The
comment says which of the two rules it is following and which it is not, because
the next person to compare them will otherwise assume one of them is a mistake.

The rewrite turned up something else, older than any of this. `decks` started as
an empty list, so for the frame between opening the library and the database
answering, the screen said **"No saved decks yet. Build one and hit Save."** to a
player with nine of them. An empty shelf and an unread shelf are not the same
thing and the screen only had one way to say them. Both are nullable now, and
the screen says nothing at all until it knows something — the wait is a SQLite
read, shorter than any sentence about it would take to read.

*A claim made before the thing was looked at.* That is a review frame, and it
generalises: this program says a deck is illegal, a run is not significant yet,
a group is about Snake-Eye. Each of those is worth asking the same question of.

## 107 · The same frame, run over the rest of the program

Section 106 named it — *a claim made before the thing was looked at* — so it got
pointed at everything else. Two more, and the second one is the worst defect
found in a while.

**The version history said "nothing yet" to a deck with a dozen versions.** The
host reads two things: the deck, then its versions. Two suspend calls, two
assignments, and the first assignment is what opens the sheet. Compose does not
wait politely between them: writing `stored` invalidates and the sheet composes
while `versions` is still the empty list it was initialised to. Both reads
happen first now and both writes after, so the thing that opens the sheet cannot
be true before the thing it describes is.

**And the pool.** The search pane draws a notice when the index is empty:
*"No cards downloaded yet. Connect to the internet once to fetch the card
database."* An index that has not been read yet is empty in precisely the way an
index that was never downloaded is empty, and there was nothing to tell them
apart. Reading thirteen thousand cards out of SQLite is the largest read this
application performs — so for as long as it took, on every single launch, a
player holding a tablet with the whole database on it was being told to go and
find some internet. At a venue. Which is the case this program is proudest of
handling, and it was the case it handled worst, on the way in, every time.

`poolRead` now says whether the cache has been *looked at*, which is a different
question from whether it is empty, and the pane says "Opening the card
database…" until it has been. The `:ui` test asserts both halves in one go: false
at construction, true after `start()`, and the index still empty afterwards —
because the fixture has no cards in it, and the flag is about having looked
rather than about having found. That is the distinction the whole defect was.

## 108 · Sixty errors in red, on every cold launch, about a deck that was fine

The same frame again, and this one had been there since the deck check was
written.

`DeckValidator` reports a passcode it cannot resolve as an error, and its own
docstring says why that is right: *"that case is a real error worth surfacing,
not something to silently drop."* Which it is — a `.ydk` full of passcodes the
database has never heard of is a decklist somebody cannot register.

It is also what every passcode looks like to an index nobody has opened yet.

So on every cold launch, in the window between the app reading the last deck and
the app reading thirteen thousand cards out of SQLite, the toolbar said
**"60 issue(s)"** in red about a deck that was legal, and the deck check behind
it listed all sixty: *Passcode 14558127 is not in the card database.* The library
did the same to every deck on the shelf at once, and that was reachable by
tapping the library within about a second of launch.

`validation` is nullable now. Not an empty verdict — an absent one, so neither
reader can spend it by accident and both had to be edited to compile. The chip
stays where it is and says "Checking…", greyed and inert, because a control that
appears a second after the bar does is a bar that rearranges itself under a thumb
already on its way. The library takes the same flag from the same place, so the
two screens cannot disagree about whether a verdict exists.

Three defects in one afternoon, all the same shape, none of them found by a test
that already existed, and every one of them found by asking a single question of
things the program says: **had it looked, when it said that?**

The frame is worth keeping. Everything a program says about a thing it loads
asynchronously has a moment before the load where the sentence is still there
and the thing behind it is not, and the default value of a collection — empty —
is exactly the value that makes the wrong sentence sound reasonable.

## 109 · And measuring one, for the same reason

`statistics` went the same way as `validation` in the commit after it, because
leaving one of them waiting and not the other is a difference a later reader
would have to work out was deliberate — and it would not have been.

A deck measured through an index nobody has opened is forty cards of which none
is a monster, none is a spell and none is a trap, with opening-hand odds
underneath. Not alarming, the way sixty red errors are, which is precisely why it
was the one that would have been left in.

## 110 · The wrong half of the world

This program could open a `.ydk` and nothing else. Which means it could only
accept a decklist from somebody who already had this program — and almost nobody
passes a decklist around as a file. They paste it. In a message, under a
tournament report, out of a column on a website, off the back of a stream.

`DecklistText` reads that. It is not a format, it is a family of habits, so it
takes counts in front (`3 Ash Blossom`, `3x`, `3.`) or behind (`x3`, `(3)`), or
none at all meaning one; skips blank lines and `#`, `//`, `!`; drops trailing
commas; and takes the headings a registration sheet uses — monsters, spells and
traps all meaning the Main deck, because the split inside it is by card type and
this has the cards themselves to work that out. Most pasted lists have no
headings at all, so the Extra deck is found from the frame rather than from a
line that may not be there.

Two things in it are worth writing down.

**A card whose name starts with a number is written in exactly the shape a count
is.** `8-Claws Scorpion`. `7 Completed`. Split those and you get eight Claws
Scorpions. The only thing that tells a count from a name is whether the whole
line is already a card, so the whole line is tried first — and `3 8-Claws
Scorpion` still splits, because the whole of *that* is not one.

**And it never invents a card.** The pool's own search is deliberately forgiving
about typos, which is right when you are watching it type-ahead and can see what
it found. Nothing is watching sixty lines go past at once, and every line that
was never a card at all — a heading nobody recognised, a note somebody left in —
still has a nearest neighbour among thirteen thousand names. So what the search
offers is checked rather than taken: a text match is refused outright, since a
card whose *description* mentions a word is not a card anybody listed, and a name
match has to be the same name typed badly. Two edits across the whole name, or a
prefix of at least six characters, which is what a table that truncates does to
the long ones. `Pot of Greed` does not become `Pot of Prosperity`.

What it cannot place comes back rather than being dropped, because sixty cards
arriving as fifty-eight is worse than being told which two did not — the second
one you can do something about.

Off the frame thread, for the reason the pool search is and the shelf search now
is: sixty lines that miss on the exact name are sixty scans of the whole pool,
which is sixty keystrokes' worth of work in one go.

Sixteen tests. All green on the first run except a call to a method I had named
`defaultSection` and the codebase calls `requiredSection`.

## 111 · What the pile is for, which is the half nobody can read off it

Gaps say *these nine go together*. Section 91 then taught the statistics to read
each pile back off its cards — Snake-Eye, Monsters, Bonfire ×3 — and made a point
of that reading never being able to go stale, because nobody wrote it.

Which is also its limit. It can say what a pile *is made of*; it can never say
what it is *for*. **The engine. What beats Ryzeal. Cut these first.** That is the
sentence the arrangement was made to hold, and it is the one thing only its owner
can put there.

So a name, optional, over a reading that is already there. Empty it and the
reading comes back — which is why there is no delete button: clearing the box is
the delete, and what returns is not nothing.

The whole design turns on one question: **what is a name attached to?**

Not to a card. Cut the third copy of the starter and the pile is still the
engine. Not to a set of cards either, which would make a pile stop being itself
the moment you swapped one out. It is attached to the *gap* — a name is keyed by
the position its pile starts at, which is zero or a break, and it therefore lives
inside `Breaks` and goes through every transformation the gaps already go
through. Insert three cards above it and the name moves down three with its own
gap. Cut one and it comes back up. Close the gap and the name goes with it,
because the pile it started no longer exists — it has been folded into the one
above, and leaving the name behind would put it on a pile nobody named.

Zero is the one special case, and it is a real one rather than an off-by-one:
zero is not a gap, it is the front of the section, and the front of the section
does not move when something is put in front of it.

It rides in the file as a *sibling* key — `arrangementNames` next to
`arrangement` — rather than as a richer shape under the first one, so a file
written this morning still opens in a build from last week, gaps and all, and a
name for a pile that no longer starts anywhere is dropped on the way in. The
gaps are the arrangement; a name is a label on one, and the label never gets to
argue with the thing it is labelling.

In the full-deck view it is drawn over the pile, in the section's colour, at a
**fixed** height — because the fit that decides how big the cards are has to be
told everything that is not a card before it can answer, and a label that took
its own measured height would be a label that pushed the last row of the deck off
the bottom of the picture. Same accounting the group spacing has had since it was
added.

Twenty-one tests across `Breaks`, the codec and the state holder, and the one
they all circle is the third one: *a name follows its own pile when the deck
above it changes.*
