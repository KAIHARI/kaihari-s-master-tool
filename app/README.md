# kai's master tool — cross-platform app

A clean-room rebuild of the deck builder as a single Kotlin codebase targeting
Android tablets, desktop and iOS. Not a port of the HTML tool: the domain rules
were rewritten from the rulebook up, with tests, and the UI was designed for
touch rather than adapted to it.

## Modules

| Module | What it is | Depends on Google Maven |
|---|---|---|
| `core` | Pure Kotlin: models, YDK/YDKX codec, deck rules, search, API client, SQLite | No |
| `ui` | Compose Multiplatform screens shared by every platform | Yes |
| `androidApp` | The APK. Landscape-locked, built for a Tab S11 Ultra | Yes |
| `desktopApp` | JVM app, packaged as `.dmg` / `.msi` / `.deb` | Yes |

`core` deliberately has no Compose and no platform code, so it compiles and its
tests run anywhere — including environments with no Android SDK.

## Building

```bash
# Domain tests. Works with no Android SDK installed.
./gradlew :core:jvmTest -Pmastertool.android=false

# Debug APK -> androidApp/build/outputs/apk/debug/
./gradlew :androidApp:assembleDebug

# Desktop app
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDistributionForCurrentOS
```

### The Android toggle

Every Android artifact — AGP, `androidx.*`, the SDK — is served only from
Google's Maven. `settings.gradle.kts` detects whether an SDK is present and
skips the Android and Compose modules when it is not, so `:core` stays usable in
restricted environments. Android Studio and CI pick everything up automatically.
Force it either way with `-Pmastertool.android=true|false`.

iOS targets are off unless building on a Mac; enable with `-Pmastertool.ios=true`.

### CI and releases

`.github/workflows/build-app.yml` builds a debug APK on every push and uploads
it as a run artifact.

`.github/workflows/release.yml` publishes a signed release. Trigger it by
pushing a `v*` tag, or from the Actions tab with a version number:

```bash
git tag v1.0.1 && git push origin v1.0.1
```

It derives `versionCode` from the commit count (monotonic, reproducible),
verifies the APK carries the committed signing certificate, and attaches
`kai-master-tool-<version>.apk` to the GitHub release.

## Updating from GitHub

The Android app checks this repository's latest release on launch and whenever
you tap the version in the top bar. If a newer version has an APK attached, it
downloads it and hands it to Android's package installer.

- The first update asks you to allow the app to install unknown apps. That is a
  one-time per-app Android setting.
- Pre-releases are ignored by stable builds, so a test release cannot push
  itself onto a normal install.
- An unparseable or older tag never counts as an update.
- The desktop build does not self-update; it opens the release page instead.

This only works because every build is signed with the same **deliberately
public** keystore — Android rejects an update whose signature changed. See
`androidApp/keystore/README.md` for what that key does and does not protect.

## Design decisions worth knowing

**Extra Deck detection keys off `frameType`, not `type`.** A Pendulum Effect
Fusion Monster reads as a Pendulum monster but is summoned from the Extra Deck.
The frame carries the summoning mechanic; the type string does not.

**Alternate artwork passcodes resolve to the same card.** Deck files exported by
other tools frequently reference an alternate printing, whose passcode differs
from the one a database calls canonical. Every known passcode is indexed.

**All deck edits go through `DeckEditor`.** It is pure and total: copy limits,
banlist status and section legality live in one place, so drag, tap and stepper
cannot disagree with each other.

**A failed card-pool refresh never clears the cache.** An outdated pool beats no
pool at a venue with no signal.

**Landscape-locked on Android.** Removes rotation recreation entirely, which is
why the app uses plain remembered state holders instead of ViewModels.

**Sorting a deck is an edit, not a view setting.** The stored order is exactly
what gets written back to `.ydk`, so a sort that only reordered the display would
leave the file disagreeing with the deck from then on. Being an edit also means
undo puts it back.

**Cards touch, and a resize zooms them.** The gutter is zero by default: a gap is
what makes forty cards read as forty tiles in a piece of software rather than as
one arrangement on a table. What keeps them legible at zero is the card's own
printed edge. Dragging a pane divider pins that pane's column count first, so the
cards change size and the grid does not rearrange around them — pulling a stack
towards you makes it bigger, it does not re-deal it.

**Siding writes an arriving card into the slot the departing one left.** Out and
in are paired by position. Appending would be shorter and would quietly destroy
an arrangement its owner chose, which is the thing this application is for.

**The `#ydkx-extended` payload is merged, never re-encoded.** `.ydkx` files are
plain YDK text plus a JSON payload. This app reads and writes three things in it
— siding patterns, notes, and where the gaps in an arrangement fall — by editing
those keys *inside the object that was on disk*. The desktop tool also writes a
background gradient, a category, tags and the opponent's decklist there; decoding
into a data class and encoding back would delete all of it. Editing a deck on the
tablet must not destroy work done on the desktop.

**Six scripts under `tools/` check what the compiler cannot see from here.**
`:ui` does not build in a restricted environment, so a missing import, a modifier
called without importing it, an assignment to a property whose setter is private,
an orphaned property accessor, a test in the wrong source set or a Row child
asking for the whole row's width all cost a CI round trip instead of a red
squiggle. Each was written the day after making the
mistake, and each was verified by putting the mistake back and watching it fire.
None has a hardcoded list of Compose names: the modifier check reads the mapping
out of the codebase's own import lines, so it learns a new one the first time it
is used correctly.

**Anything that can be a number lives in `:core`.** Foil angles, autoscroll
ramps, grid geometry, siding. `androidx` is served only from Google's Maven, so
in a restricted environment `:core` is the only thing that compiles at all — and
keeping the arithmetic there is what makes the Compose layer safe to write when
CI is the only compiler that will ever see it. See `docs/loop-journal.md`.

**Layout settings are one JSON document in the SQLite database.** No DataStore
(Android-only, would need a separate desktop path) and no settings library.
Storing them as a document rather than a column per setting means adding a
preference is a field, never a migration. Everything read back is clamped: a
weight of zero or NaN reaches `Modifier.weight`, which rejects both.

**A new table needs a migration file, and the failure is invisible on a fresh
install.** SQLDelight derives the schema version from the number of `.sqm` files,
and both driver factories hand `MasterToolDatabase.Schema` to the driver. Adding
a table to a `.sq` file alone leaves the version unchanged, so no migration runs
and the first query throws — on every device that already has the app, and on
none of the ones used for testing. `MigrationTest` upgrades a real version 1
database and compares the whole result against a freshly created one, so a table
added without its `.sqm` fails by name. It needs no new case per change — but the
version 1 snapshot inside it is a record of what is on somebody's tablet, not a
copy of the current files, so it must never be edited to make a failure go away.

## Status

Shipping in v1: deck builder with search and filters, drag and drop between the
pool and every deck section, per-section copy steppers and moves, adjustable and
collapsible deck panes with per-section sorting and card density, an inspector
you can page through the results in, deck statistics with opening-hand odds, a
deck-check panel that jumps to the card an issue names, a TCG/OCG toggle, deck
library, YDK/YDKX import, export and share.

A card with no picture is drawn rather than labelled. This program is built for a
venue with no signal — it says so, and it keeps an outdated card pool rather than
none — and then it drew every card as a grey rectangle with the name across it,
so the case it was designed for was the one where forty cards touching read as a
wall of labels. Now the frame is the card's own: orange has an effect, green is a
spell, black is an Xyz, and the art window holds an abstract field seeded by the
passcode, so three copies look like three copies and the card beside them does
not. A monster's field is pulled towards its attribute — a FIRE card is warm, a
DARK one is not — far enough that forty of them are forty cards rather than a
wall of one colour, and not so far that the frame stops meaning what it means.
Spells and traps get none of it, so green still reliably means spell. Each frame carries its own ink, checked against all three ends of its own
gradient, because one of them is nearly black.

Cards in a deck pane touch, on a mat — the deck's own, see below. They catch the
light when you point at one, are dealt onto the table when a deck opens, and
trail your hand on a spring while being carried. Several can be picked up and
moved together. A pane scrolls while you hold a card over its edge, and a long
press fills a ring so you can see it arriving.

An empty section draws the deck that is not there yet: a slot for every card it
will hold, at the size those cards will be. Press `V` and everything else goes
away — the whole deck at the largest size it fits at, no controls on top of it.

On desktop: keyboard shortcuts throughout (press `?` for the list, which is
generated from the table that implements them) and a hover preview on any card.
The whole deck can be arranged from the keyboard — arrows move a cursor, shift
grows it, Ctrl carries what it holds, `g` puts a gap in, shift-`g` picks the whole
group back up, and `n` writes on what is being held: on the card if that is one, on the pair if it is two. Typing in the
search box completes the card's name.

Siding works end to end. Plans arrive in a `.ydkx`, are applied as one undoable
edit, and are recorded by doing them — side by hand, then keep what you did. One
recorded by mistake can be taken back out, which matters more than it sounds: a
plan prints on the sheet and one press applies it to the deck.

Test hands: shuffle the Main deck, judge each opening as playable or a brick, and
watch the rate over a run of them.

Tidying, which is not sorting: gather stray copies, group by type or archetype.
Each is a stable partition, so the arrangement inside a group survives it.

Search finds cards by name, forgivingly, and completes the name as you type it.
When nothing is *called* what you typed, it tells you so and shows the cards
that *say* it instead.

Cards make room. Carry one over a pane and the two either side of where it would
land lean apart, so you see the slot before you let go rather than a line saying
where it would be — near the edge of a row the notch keeps its width and loses
its tail, because a row that drifted would push its last card off the pane.

Gaps: push the piles apart. A deck's order is yours and nothing sorts it without
being asked, but an order alone cannot say *these nine are the engine and those
six are the handtraps* — so put a gap between them from a card's long-press menu
or with `g`. In a pane the gap is a mark, so every card stays the same size; in
the full-deck view the groups separate onto their own rows. Turn the density down
and the copies collapse into stacks — the gaps come with them, onto the seams
that still exist.

The tidies draw their own, so "group by type" separates the groups it made. Gaps
travel with the file in its `#ydkx-extended` payload, and they survive being
built around: append, cut, drag a card across one, and they stay where they mean.

And then they can be read back. Deck statistics measures each group you pushed
apart: how often an opening hand holds one of it, how often two, and how many on
average. Nothing was tagged or named to make that possible — an arrangement made
to be looked at turns out to be an arrangement that can be measured, and "these
nine are the engine" was always a claim about hands. Each row says what its group
is about as well — read back off the cards in it rather than typed, so it cannot
go stale, and left blank when the group has nothing to say for itself.

The mat belongs to the deck, not to the program. The theme is what the
application is wearing; the cloth under the cards is this deck's own, chosen from
six that were each picked to sit *under* card art — slate, leather, midnight,
baize, wine and one bone-coloured one for building in daylight. It rides in the
`.ydkx`, so the deck brings its table with it, and each mat carries its own ink
so the caption is readable whichever way the contrast falls. Three lists on three
cloths are three decks you can tell apart from across the room — and the library
draws each saved deck on its own, so you can tell them apart before reading a
name.

Notes: whatever you want to remember about the deck, written into it and saved
with it, and shown in the library. And a line on a single card — *only starter
that plays through Ash*, *third copy is for the mirror* — from its long-press
menu, marked by a folded corner on the tile. It goes by passcode, so every copy
carries it and cutting one does not take it away, and it turns up wherever you
are looking at the card rather than only where you wrote it: in the inspector,
and under the hover preview in the pool as well as the deck. And a note about *two* cards, written from a
two-card selection and listed on both their sheets — the half of deck building a
decklist cannot hold at all, because a deck is a handful of two-card openings and
thirty-odd cards that make them likelier. And pick one of them up in a pane — a
single card, from the keyboard cursor or the card menu — and the cards it is
noted with light up around it, which is as close as this gets to drawing a line
between two cards without drawing a line between two cards that move. The
original tool wrote an empty `notes: { cards: {}, pairs: {} }` into every file it
ever exported; this is what goes in both halves.

And what it used to be. A deck that saves itself has no yesterday: cut a card
from the list you registered and the sixty you registered stop existing. So
coming back to a deck after a break keeps a copy of how you left it — one per
sitting, not one per keystroke — and the library draws them as a line down the
page, each one saying what came out and what went in to get from there to here.
Go back to one and the deck you are leaving is kept on the way past, which is why
that button asks nothing first. Name one and it is never let go of: *this is the
list I registered* is the thing only you know, so it can also be said from the
builder's menu, in front of the deck, which is where you are when you know it —
and read from there too, since a drawer you can put things into and not open is
not a drawer. The rest thin themselves out, and
what goes is never the oldest — it is whichever version sits closest in time to
the ones either side of it, so an evening's worth collapses to one and both ends
of the deck's life stay put.

The deck you were working on is the one that opens next time. A deck saved once
keeps itself saved — every edit after the first save is written without being
asked, and *edit* includes the things that are about the deck rather than in it:
a gap moved, a note written on a card, a mat chosen, a siding plan recorded. A
deck that has never been saved is never written on its own, and the toolbar says
so. Saved decks show three of their own cards
in the library rather than only a name, and any of them can be held up against
the deck you have open: what came out, what went in, three of a card becoming
two — and, when nothing was cut at all, that the same cards are simply arranged
differently, which this program is not going to call *no change*.

Shootout: load the deck you are testing against and see both openings at once.
Judge them loosely, or run it properly — a set number of trials with game one
off the list you registered and the rest off the sided one, drawn as a score
sheet whose two rows are the two brick rates underneath it. The report says
whether the side deck is working, and stays quiet until there are enough hands
to say. A run survives siding, because that is what it measures; it ends the
moment you change which sixty cards you brought.

Sandbox: lay the opening out on a board and see whether it assembles into
anything. The table is the deck's own cloth, so arriving there is not walking
into a different room. It folds — the far half tips away, the near half toward you,
hinged on the shared zones between them. The way you let go of a card says which
way it faces: drop it and it stands up in attack, flick it sideways and it lies
down in defence, hold it a moment first and it goes face-down. All four stacks
open — deck, Extra deck, graveyard, banished — and a card taken out of one is
picked up the same way a card in your hand is. Drop something on the graveyard
and it goes there, and make a token when the turn needs one. Tap a card to turn
it; hold it to pick it back up into your hand, which is the only way off the
board that goes backwards — everything else carries on forwards, so a slip used
to mean sweeping the table and building it again. Undo puts the whole table back,
hand and deck included.

A hand you like in the test panel or the shootout is one button from being a
board with that hand already in it.

Take a picture of it. The full-deck view is captured from the same draw pass
that put it on screen and written out as a PNG under the deck's own name — the
arrangement, the gaps, the mat, at the size it was being looked at. Every other
builder can export a decklist image and every one of them sorts it first, which
is a picture of a deck nobody arranged.

Print the decklist a judge reads: counts rather than a line per copy, grouped by
monsters, spells and traps the way a registration sheet has always been, with
blanks for player, event and table. Inside each type the deck keeps its own
order, and the two columns balance so it does not read as half a page somebody
forgot to finish.

Print the siding sheet: every matchup on paper, going first above going second,
out on the left and in on the right, with a count on each side so an uneven swap
cannot slip past. It is written directly rather than through a library — the
whole file is ASCII, which is what lets it ride the same export the deck does.

The mat has two halves. Lay out the board you are trying to break on the far
side — cards, tokens, face-downs, all by the same gesture — and then see what
your opening does about it.

Between games of a shootout the deck across the table sides too, when its own
file says how: a `.ydkx` carries that deck's plans, so a downloaded list often
arrives with what its players actually bring in.

Not yet built: dragging a gap along rather than replacing it. Recording what you
*expect* an opponent to side, when their file does not say, is deliberately left
out — every shape it could take dresses a guess as a measurement, and their deck
feeds only what you see across the table, never the report.
