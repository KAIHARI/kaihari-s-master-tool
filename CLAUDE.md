# CLAUDE.md

This file guides Claude Code (claude.ai/code) when working with this repository.

## Quick Start

**What this is:** kai's master tool — a Yu-Gi-Oh! deck building and tournament
preparation tool, being built into an immersive, 3D-feeling deck building
simulator.

**The project is the cross-platform app in `app/`.** Kotlin Multiplatform +
Compose Multiplatform, targeting Android (landscape tablets, Samsung Tab
S11-class) and desktop (macOS/Windows/Linux) from one codebase. See
`app/README.md` for architecture and build instructions.

**`legacy/kai master tool.html` is the archived original** — the single-file
web tool the app replaces (~36k lines, plain HTML + Tailwind + vanilla JS).
It is kept as a reference for the original vision and its feature set (siding
patterns, shootout mode, sandbox board, PDF export, the prismatic hover
effects). Open it in a browser from inside `legacy/` if you need to study it.
Do not develop it further; maintenance only if something in it is truly broken.

**Key locations:**
- `app/core/` — pure Kotlin logic (models, deck editing, groups, hand odds,
  motion physics, board domain), all tested in commonTest
- `app/ui/` — Compose Multiplatform UI (androidTarget + jvm("desktop"))
- `app/androidApp/`, `app/desktopApp/` — platform entry points
- `legacy/` — the archived original tool and its assets
- `ydk/`, `lab.ydkx` — sample deck files (YDKX = YDK + `#ydkx-extended` JSON)

---

## Development Workflow — read this before changing anything

1. **All logic goes in `:core` with commonTest tests.** Run locally:
   `cd app && ./gradlew :core:jvmTest`. This is the only module that compiles
   in a default Claude environment.
2. **`:ui`/`:androidApp`/`:desktopApp` compile ONLY in CI** (Google Maven is
   unreachable from most sandboxes; `settings.gradle.kts` auto-skips those
   modules). Pushing to `main` or any `claude/**` branch runs
   `.github/workflows/build-app.yml`, which is the compile check and APK
   build. **Never trust a piped local exit code** — grep the Gradle output for
   `BUILD SUCCESSFUL`.
3. Every gesture ships with both a touch idiom and a pointer/keyboard idiom.
   Keyboard shortcuts are data in `core/input/ShortcutTable.kt` (layer-aware;
   the help sheet renders the table, so it can never drift).
4. **Finish by shipping it.** See *Ship Every Change* below — the user tests on
   the tablet, so work that is only on a branch is work they cannot see.

## Ship Every Change — standing instruction from the user

The device is the only place this app can really be judged, and a debug APK
cannot install over the signed one. So the default end of a piece of work is a
release, not a branch. Standing permission is granted for all of it — do not
stop to ask.

Every time, in this order:

1. Push the work to the `claude/**` branch and wait for `build-app.yml` to go
   green on all three jobs (core tests, desktop, Android APK). A red build is
   the one thing that stops the rest.
2. Fast-forward `main` onto the branch and push it. Releases build from `main`.
3. Read the current version — `get_latest_release`, or the newest `v*` tag —
   and dispatch `release.yml` on `main` with the **next patch** number
   (v1.2.3 → v1.2.4). Bump the minor instead only when the user asks, or when
   the patch would reach 100: the versionCode formula gives 1.2.100 and 1.3.0
   the same code, so the patch digit must stay under it.
4. Confirm the release actually published — the tag exists and the
   `kai-master-tool-<version>.apk` asset is attached — before telling the user
   it is ready. The signing gate can fail the run *after* the APK builds, and
   "dispatched" is not "shipped".

Note in the release notes when a build changes stored preferences, the schema,
or the deck-file payload, so a surprise on the tablet has an explanation
waiting.

Two things to say out loud rather than silently skip: a change that cannot
reach the device (docs, this file, tests only) does not need a release, and a
version number, once published, is spent forever — there is no reissuing one.

## Release Contract — numbers shipped to devices are permanent

`.github/workflows/release.yml` (manual dispatch with a `version` input, or a
`v*` tag) builds the signed APK and publishes the GitHub release that the
in-app updater installs from. Two hard-learned rules:

- **versionCode** is derived from the version name
  (`100000 + major*10000 + minor*100 + patch`). It must only ever go up;
  v1.1.0 shipped as 281 from a commit-count scheme, which is why the floor
  exists. Never revert to commit counts.
- **SQLite schema version is 3** and can never decrease — devices that
  installed v1.1.0 are stamped at `user_version 3`
  (see `core/.../db/migrations/2.sqm`). SQLDelight derives the version from
  the number of `.sqm` files: adding a table means adding a `.sq` change AND a
  new `.sqm`, and `MigrationTest` must prove upgrade == fresh create. Never
  renumber or delete migration files once a signed build has shipped.
- The APK must be signed with the committed key
  (`androidApp/keystore/kai-master-tool.jks`); the workflow hard-gates on its
  SHA-256.
- Android crashes surface through the built-in crash reporter
  (`MainActivity`): the trace persists and is shown, shareable, on next
  launch. Keep that screen theme-free — it must render when the theme cannot.

## Design Identity (locked in with the user)

- **Swiss + prismatic: sharp white on true black.** Colour appears only as
  *meaning* (card types, legality) or as *light* — the six-hue prismatic ramp
  (`MasterToolPalette.Prism`) shown as chromatic-aberration fringes on things
  being interacted with. Light mode is the exact inversion, same colours.
  Primitives live in `ui/theme/Prismatic.kt`; use them sparingly — fringing
  everything reads as decoration, fringing the thing under your finger reads
  as light.
- **Typeface: Archivo** (bundled, OFL; expanded cut for display moments).
- **Tactility lives on things the user interacts with** — tilt, lift, sheen,
  quiet card sounds, haptics. No decorative clutter, no skeuomorphic
  inefficiency. The physical feeling to capture is handling a single card.
- **Motion = springs.** `core/motion/` (`Springs`, `PosePhysics`) with the
  one-`withFrameNanos`-loop recipe (see `EasterEgg.kt` and `GoldfishScreen.kt`
  for the sanctioned perf pattern: bulk state in plain lists, per-object state
  read inside `graphicsLayer`).
- **Fake-3D by design:** perspective via `graphicsLayer`
  (rotationX/Y, cameraDistance) — one tilted parent plane for tables, flat
  overlay springs for anything that lifts off it. No 3D engine, ever.

## Roadmap State

Shipped: the full deck builder (drag-and-drop, exact consistency calculator,
per-card tactility, sound/haptics, 3D card inspect, goldfish table) and the
tested duel-board domain (`core/board/BoardState.kt`).

Two parts of the builder are worth knowing before touching them, because both
replaced an earlier design that looked reasonable and was not:

- **Layout is solved, not negotiated.** `core/layout/DeckFit.kt` sizes all
  three panes in one pass: row widths are the input (main 10, extra/side 15),
  row counts follow from the deck, and card size is the single free variable.
  Per-pane auto-fitting against divider-dragged heights is what put cards out
  of bounds; do not go back to it. Anything the panes spend on chrome must be
  declared to the fitter or the cards pay for it.
- **The breakdown never reorders the deck.** Groups are drawn as gaps in the
  stored order (`DeckBreakdown.slots`), so a grid index is always a deck
  position and a drop always means insert. Assignment is a selection gesture:
  `GroupDraft` in core, tapped out on the deck itself.

Next: the deck **showcase** stage, goldfish polish (deal-origin projection,
stack shuffle/cut), and the **duel table UI** on top of `BoardState`,
inheriting the goldfish stage pattern.

**Explicitly deferred by the user — do not build on the legacy designs:**
siding patterns and shootout mode will be redesigned from scratch in a future
run. The only obligation today is that `YdkCodec` keeps round-tripping the
opaque `#ydkx-extended` payload (it does — `DeckGroupsCodec` preserves
unknown keys byte-for-byte).

## Multi-Team Trigger

When the user starts a prompt with **"mt"** or **"mt:"**, they want a
multi-agent team: strip the prefix, create a team, break the task into 2-4
subtasks, spawn 2-3 general-purpose teammates, coordinate, report back.

## Data Formats (unchanged from the original)

- **Card**: YGOPRODeck API v7 shape (`core/model/Card.kt`); ids are Konami
  passcodes.
- **Deck**: ordered multisets of ids per section (main 40-60, extra/side
  0-15, 3 copies across the whole deck) — order round-trips to `.ydk`.
- **YDKX**: plain YDK + `#ydkx-extended` + one JSON object. The app owns the
  `groups` key; everything else (legacy `sidingPatterns`, `notes`,
  `configurations`) passes through untouched.

## Debugging

- Core logic: write a failing commonTest first; `./gradlew :core:jvmTest`.
- UI on Android: the in-app crash reporter shows and shares the trace.
- Desktop: `./gradlew :desktopApp:run` (needs Google Maven access).
- Preferences are one JSON document in SQLite (`UiPreferences`); adding a
  preference is a field with a default, never a schema migration.
