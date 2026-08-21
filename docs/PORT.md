# The 3DS port

kai asked for the app on a New 3DS, sideloaded as a `.cia`, redesigned around
the touchscreen, with the fishbowl as the demo — and then, on being told the
outer cameras are a stereo pair, for **marker AR**: point the console at a real
desk and the duel table is on it.

This document is what `docs/LOOP.md` is for the play stage: the shape of the
work, the decisions already taken, and a ledger of what has been tried. Read it
before touching anything under `3ds/`.

---

## 1. It is a rewrite, and saying so is load-bearing

There is no Kotlin/Native or JVM target for Horizon OS. 3DS homebrew is
devkitARM, libctru and citro3d, in C, and the PICA200 has **no programmable
fragment shader** — only a fixed-function fragment-lighting stage driven by
lookup tables. Nothing in `app/` runs here. What crosses over is the *arithmetic*
and the *design*, and the whole risk of the project is that a hand translation
of eleven thousand lines of arithmetic silently becomes a fork.

So it does not cross over by hand-copying. It crosses over **proved**:

- `GoldenVectorExportTest` in `:core`'s jvmTest source set sweeps each ported
  function over a fixed grid and writes the answers to `3ds/test/vectors/`.
- `3ds/test/mt_test.c` compiles `3ds/src/core/` with the *host* compiler and
  asserts against the same files.
- The vectors are **committed**, and CI diffs them. A change to `BoardLayouter`
  that moves a zone shows up in review as a diff in a vector file, next to the
  change that caused it.

`3ds/src/core/` therefore may not include a libctru header, ever. That single
rule is what keeps the port's foundation checkable in one second on any machine,
and it is the direct analogue of `:core` compiling in a sandbox where `:ui`
cannot.

**The suite has been falsified, which is the only reason to trust it.** Drifting
`GAP_FRACTION` from 0.11 to 0.1104 — 0.4%, invisible on a screen — produces 3,952
failures. Swapping two entries in the slot array while leaving every rectangle
correct produces 89, because the insertion order is what `slotAt` resolves
midline ties by. Both mutations were run and both were caught.

---

## 2. What the port deletes

The happiest finding. `StagePlane.flatten` / `raise` / `project` / `unproject`,
`CarryHeight`, `MatInput.handQuad`, `Defocus` and `Outset` exist because Compose
offers a `graphicsLayer` and a 2-D canvas and no way to draw a real perspective
scene. citro3d has actual matrices, and the bottom screen is orthographic on
purpose.

So three of the six load-bearing play-stage rules in CLAUDE.md — everything
about the finger being on the felt while the card is in the air — do not get
ported. They become *vacuous*. `MtMatPoint` is a 0..1 fraction, the bottom
screen's hit test is a multiplication, and the class of bug that produced "a hand
whose top half would not respond", "drops landing a third of a card short" and
"a monster set into the spell/trap row" has no surface to occur on.

`core/mat/MatDesk.kt`'s ten lanes go the same way. A stylus is one pointer, so
the router collapses to one lane — but its *rules* still port, because they were
never about fan-out: the grace window, `stillHolds` and `rebase` are all still
needed the moment a gesture outlives the frame it started on.

## 3. What the port reuses, and one surprise

Ported directly: `PlayField`, `BoardCard`, `DropIntent`/`DropTargets`,
`DropCommit`, `SetPosition`, `BoardLayout`, `HandFan`, `PileFan`, `YdkCodec`,
`Springs`, `PosePhysics`, `CardIndex`, `EffectMatching`.

Reimplemented on the GPU with the Kotlin as the spec: `CardSolid`, `Turned` and
`Rot3` become vertex buffers; `Shading`, `StageRig` and `StageLighting` become
citro3d fragment-lighting LUTs — the PICA200 does per-pixel Lambert,
Blinn-Phong *and a Fresnel LUT* in hardware, which is very nearly `StageRig.lit`'s
graze-gated rim for free. `WoodGrain`, `FeltWeave` and `AgX` become **baked
textures**, rendered by `:studio` at build time. That is the answer to having no
fragment shader: the procedural surfaces become PNGs, produced by the code that
already draws them.

The surprise is `core/render/Homography.kt`. `squareToQuad` was written so a
leaned card's picture would land on its own drawn corners; it is *exactly* the
primitive marker pose estimation needs, and it inverts for fiducial tracking
without modification.

---

## 4. The phases

Each ends in an installable `.cia` built by CI.

| | | state |
|---|---|---|
| **P0** | Skeleton, Makefile, RSF, CIA packaging, CI | in progress |
| **P1** | The domain in C, proved by golden vectors | in progress |

Ported and under vector so far: `mt_types`, `mt_board_layout`
(`BoardLayouter.solve` and `slotAt`, all seventeen slots in the order ties
depend on), `mt_drop` (the intent vocabulary and `SetPosition`), `mt_spring`,
`mt_ydk`, `mt_random`, `mt_playfield`, `mt_handfan`. **39,537 checks.** Still
to port: `DropTargets.resolve`, `DropCommit`, `PileFan`, `CardIndex`,
`EffectMatching`.

**Every unit is falsified before it is believed**, and that has earned its keep
three times. Mutating `PlayField` four ways caught two and missed two — the
script had hardcoded instance ids that a shuffle invalidates, so `stack`,
`attach`, `counter` and `takeFromUnder` were all quietly *refused* and 25,945
checks passed without executing the code they were written for; and piles
recorded ids only, so `lift` clearing counters and `toBanish` setting face-down
were invisible. A third round found `reorderHand`'s index adjustment untested
because the hand was too short for the branches to differ. A suite that has
never failed has not been shown to work.

`mt_random` is the one that is easy to think optional. `PlayField.riffled`
writes Fisher-Yates out by hand precisely so a seed deals the same hand
everywhere — and that argument does not survive a port unless the *generator*
comes over too. An identical shuffle fed by `rand()` is deterministic, correct,
and a different deal. So `kotlin.random.Random` is reproduced exactly: XorWow,
its seeding from a Long, its 64 discarded outputs, its rejection loop.

**P2 has a renderer.** `3ds/src/gfx/` draws the stage with real geometry — a
card is a six-faced solid, the camera is `Mtx_LookAt` from one of the four
seats, and the two eyes differ only in a projection built by
`Mtx_PerspStereoTilt` with the interaxial taken from the 3D slider. The desk,
the felt and the zones are separated in z rather than by paint order, because
there is a depth buffer now and coplanar surfaces z-fight.

Two decisions in it are worth knowing. `CARD_THICK` is 0.020 against a real
card's 0.0129 — nearly honest, where `CardSolid.pileDepth` on the tablet has to
exaggerate *and* saturate because every z there is multiplied by `sin(tilt)`.
And culling is off: a card is a thing you look at from both sides, the depth
buffer already hides what is behind, and a face wound the wrong way in code
written without a device is invisible geometry that looks exactly like a matrix
bug. Turn it on once there is a console to check it against.

Still missing from P2: the fragment-lighting LUTs (`StageRig` is not ported
yet — the per-face shading is a constant in the mesh), the baked wood and felt
textures, the card back, and the room.

| | | |
|---|---|---|
| **P2** | citro3d, card solids, lighting LUTs, baked textures, stereo | in progress |
| **P3** | The bottom-screen control surface — **playable, the demo** | started |
| **P4** | Marker AR: camera, CV, pose, stereo compositing | |
| **P5** | Card art over wi-fi | |
| **P6** | Search and deck editing | |
| **P7** | *(deferred)* QTM head tracking | |

## 5. The bottom screen is a control surface, not a view

320×240, one stylus, and the rule is that it **never shows a perspective view**.
Card text is unreadable at 40×58px regardless, so nothing is lost by admitting
it: a status line, the board as an orthographic map with the ten zones ruled in,
four pile tiles, and the hand as a linear strip along the bottom. `HandFan`'s
lean existed to sell three dimensions; in two, a strip is the honest form.

Buttons carry what buttons are good at, and this is a *third idiom* for
`MatGuide`'s "every gesture ships in every language" rule — `ShortcutTable`'s
`PLAY_*` actions are already an abstract enum, so the button map is a third
binding table beside touch and keyboard.

| | |
|---|---|
| A / B | draw / cancel-back |
| X / Y | shuffle / card reader, at full size on the top screen |
| L held | **set** while dragging — what the two-finger set becomes |
| R held | turn / flip |
| D-pad | phase, turn |
| Circle Pad · C-stick · ZL/ZR | nudge the view · camera · zoom the map |
| Start / Select | menu / room (MINIMAL · DESK · AR) |

## 6. Marker AR

`Scene` is already a persisted enum with a room contract. AR is a third room, and
**the room is reality** — a cheerful shortcut through the eleven phases of
`docs/PHOTOREAL.md`.

A printable ArUco-style sheet lives in `3ds/marker/`; its printed size is a
setting, because it is what sets the table's real-world scale. The pipeline is
capture at 400×240 RGB565 from both ports (both eyes to display, left eye to
track) → 2×2 box downsample to grayscale → adaptive threshold over an integral
image → border-following contour trace → 4-corner approximation → `squareToQuad`
→ sample the 6×6 bit grid → Hamming-decode → pose from the homography with
intrinsics out of `CAMU_GetStereoCameraCalibrationData` → smooth with the ported
`Springs`. CV at 15–30fps, gyro interpolating to 60.

Marker loss is designed rather than crashed: hold the last pose, desaturate,
fall back to gyro drift, and say so on the bottom screen.

Stereo is real — left camera to left eye, right to right, the board rendered
twice at the interaxial the calibration reports. `CameraPose.axisX`/`axisY`, the
existing lens rise-and-fall, is the lens-shift half of a shift stereo rig; the
interaxial eye translation is the one genuinely new term.

---

## 7. The release track is its own, and that is not cosmetic

kai's instruction is that the 3DS build is separate from the Android one. It is:
`build-3ds.yml` builds it, `release-3ds.yml` publishes it, and neither touches
`app/`. Android releases are tagged `v1.2.3`; these are tagged **`3ds-v1.0.0`**.

The prefix is load-bearing. GitHub's `/releases/latest` hands back the most
recent non-prerelease release of *any* tag shape, and that endpoint is exactly
what the Android app's in-app updater reads — so a 3DS release arrives in front
of the Android updater whether or not anybody wanted it to. `AppVersion.parse`
is the only place the two can be told apart, and it could not: `takeWhile(
Char::isDigit)` read `3ds-v1.0.0` as **major version 3** with a pre-release of
`v1.0.0`. Newer than any APK this app has ever shipped, permanently, carrying no
APK to install, and shadowing the real Android release published before it. A
segment must now be *entirely* digits; `AppVersionTest` pins it.

**One ordering consequence, because an installed APK carries the old parser.**
Until a build containing that fix is on the tablet, publishing a 3DS release
will make the existing install report "a newer release exists but carries no
APK". Ship an Android patch first, or accept one confusing notice until the next
one goes out.

## 8. Two numbers that are permanent

The same class of decision as the Android `versionCode` floor.

- **`UniqueId` is `0xFF4D7`, chosen once in `3ds/app.rsf`, and never changed.**
  A different UniqueId is a *different app* to the HOME menu: a second icon, and
  the first one's data orphaned with no way to reach it.
- **The CIA title version only ever goes up** (`3ds/VERSION`, major.minor.micro),
  or FBI reads an install as a downgrade and refuses it.

**And the trap that costs a day:** a `.3dsx` runs inside the Homebrew Launcher's
host process and inherits its permissions; a `.cia` gets exactly what its own
RSF grants. A service missing from `ServiceAccessControl` is not a build error
and not a useful runtime error — it is a black screen after install. Every
service the app will ever call is listed in `app.rsf` *now*, `qtm:u` included,
years ahead of the code that calls it, because adding one later costs everyone a
reinstall.

---

## 9. Risks, and what is actually known about each

**1 · TLS on a 2011 console. Partly answered, and the answer is not comfortable.**
Both `images.ygoprodeck.com` and `db.ygoprodeck.com` return **301 to HTTPS on
plain HTTP**, measured. There is no unencrypted path, so the on-console download
either negotiates TLS with a modern CDN or does not exist.

What is *not* known is whether it can. The development sandbox routes through an
egress proxy that terminates TLS — the certificate observed there is the proxy's,
not ygoprodeck's — so the cipher suites and chain the real endpoint demands
cannot be measured from it. **This must be tested on hardware**, and the test is:
build a `.cia` that does one `httpcOpenContext` against
`https://images.ygoprodeck.com/images/cards_small/89631139.jpg` and prints the
result code, first with certificate verification on and then with
`httpcSetSSLOpt(SSLCOPT_DisableVerify)`.

Because the answer can invalidate P5's design, card art is built behind a source
interface from the start, and a host-side bake (a desktop tool writing tiled
textures to the SD card) stays alive as the guaranteed fallback. A thumbnail is
about 25KB, so a deck is ~1.5MB either way.

**2 · Core 2 on New 3DS. Answered, and it was the wrong knob.** The CV pass
wants a core of its own, and the way to get one is not `AffinityMask` — it is
`CanAccessCore2: true` in the exheader, which `app.rsf` now sets. What remains
for P4 is measuring whether the tracker actually holds 15–30fps there, not
whether it may run there at all. Fallback is unchanged: CV on core 1 at 15fps.

**3 · The CIA toolchain is the most fragile thing in the build.** devkitPro
ships neither `makerom` nor `bannertool`, and bannertool's original upstream was
taken down — `3ds/tools/fetch-cia-tools.sh` uses the `carstene1ns` fork and
builds it from source rather than taking the prebuilt, which needs a newer glibc
than some devkitPro images carry. That script is deliberately the *only* place
this dependency lives.

**4 · CV robustness** under desk lighting. Mitigated by the gyro fallback and an
honest marker-loss state, not by hoping.

**5 · The card database must be streamed, never parsed into RAM.** Thirteen
thousand cards with printed text is ~20MB. A sorted-id index with offsets, and
`fseek`.

**6 · Frame budget** — three render targets plus camera capture plus CV.
Measured in P2 before 60fps is promised. 30fps on the top screen is acceptable
and the bottom screen can run at half rate.
