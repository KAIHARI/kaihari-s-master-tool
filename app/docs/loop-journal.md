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
feedback at all, which is indistinguishable from a press that is not working.

Still open: multi-select and carrying several cards at once.

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
