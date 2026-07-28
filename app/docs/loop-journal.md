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
