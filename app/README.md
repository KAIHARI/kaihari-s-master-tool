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

**The `#ydkx-extended` block is preserved verbatim.** `.ydkx` files are plain YDK
text plus a JSON payload holding siding patterns and notes. The app does not
implement siding yet, so it round-trips that payload untouched rather than
dropping it — editing a deck on the tablet must not destroy work done on the
desktop tool.

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

**The `#ydkx-extended` payload is merged, never re-encoded.** `SidingCodec` edits
the keys it understands inside the object that was on disk. The desktop tool also
writes a background gradient, a category, tags and the opponent's decklist in
there; decoding a pattern into a data class and encoding it back would delete all
of it, one level deeper than the promise above.

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
none of the ones used for testing. `MigrationTest` upgrades a real old database
and compares it against a freshly created one; add a case to it whenever the
schema changes.

## Status

Shipping in v1: deck builder with search and filters, drag and drop between the
pool and every deck section, per-section copy steppers and moves, adjustable and
collapsible deck panes with per-section sorting and card density, an inspector
you can page through the results in, deck statistics with opening-hand odds, a
deck-check panel that jumps to the card an issue names, a TCG/OCG toggle, deck
library, YDK/YDKX import, export and share.

Cards in a deck pane touch, on a mat, in one of four surfaces. They catch the
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
grows it, Ctrl carries what it holds. Typing in the search box completes the
card's name.

Siding works end to end. Plans arrive in a `.ydkx`, are applied as one undoable
edit, and are recorded by doing them — side by hand, then keep what you did.

Test hands: shuffle the Main deck, judge each opening as playable or a brick, and
watch the rate over a run of them.

Tidying, which is not sorting: gather stray copies, group by type or archetype.
Each is a stable partition, so the arrangement inside a group survives it.

Search finds cards by name, forgivingly, and completes the name as you type it.
When nothing is *called* what you typed, it tells you so and shows the cards
that *say* it instead.

Notes: whatever you want to remember about the deck, written into it and saved
with it, and shown in the library.

The deck you were working on is the one that opens next time. A deck saved once
keeps itself saved — every edit after the first save is
written without being asked. A deck that has never been saved is never written
on its own, and the toolbar says so. Saved decks show three of their own cards
in the library rather than only a name.

Not yet built: a full shootout against an opponent's deck (a structured
playtest run — trials, siding for both sides between games, a report), the
sandbox board simulator, and PDF export of a siding sheet.
