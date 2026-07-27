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

### CI

`.github/workflows/build-app.yml` builds the APK on every push and uploads it as
a run artifact — that is the intended way to get a build onto the tablet without
a local Android toolchain.

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

Shipping in v1: deck builder with search and filters, deck library, YDK/YDKX
import, export and share.

Not yet built: siding patterns, shootout mode, the sandbox board simulator,
PDF export, drag-and-drop between deck sections.
