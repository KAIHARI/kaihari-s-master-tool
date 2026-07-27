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

## Status

Shipping in v1: deck builder with search and filters, per-section copy steppers
and moves, deck statistics with opening-hand odds, a deck-check panel that jumps
to the card an issue names, a TCG/OCG toggle, deck library, YDK/YDKX import,
export and share.

Not yet built: siding patterns, shootout mode, the sandbox board simulator,
PDF export, drag-and-drop between deck sections, keyboard shortcuts.
