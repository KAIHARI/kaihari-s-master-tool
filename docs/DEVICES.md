# Every device, not just the tablet

This app was built against one Samsung Tab S11 in landscape and it shows. The
first phone it met loaded perfectly and could not be played: the top bar threw
half its controls off the right-hand edge and nothing in the repository could
see it happen.

This is the contract that stops that being a recurring surprise.

---

## 1. Look at all of them

```
tools/devices.sh                     # the whole matrix
tools/devices.sh desk-night-seated   # one shot, every device
```

Seven geometries, in **physical pixels at real densities**, because the dp box
is what the layout solver responds to and it is the product of the two:

| | px | density | dp box |
|---|---|---|---|
| `tab-s11` | 2960×1848 | 2.0 | 1480×924 |
| `tab-a9` | 1920×1200 | 1.5 | 1280×800 |
| `fold-open` | 2208×1768 | 2.2 | 1004×804 |
| `pixel8` | 2400×1080 | 2.625 | 914×411 |
| `s24` | 2340×1080 | 3.0 | 780×360 |
| `phone-small` | 1920×1080 | 3.0 | 640×360 |
| `desktop` | 1600×1000 | 1.0 | 1600×1000 |

**640×360 is the floor.** Below that the app may say it does not fit; above it
the app must be *playable*, which is a stronger claim than *renderable* and is
the distinction this whole document exists for.

Note the shape of the problem: the tablet is 1480dp wide and the narrowest phone
is 640dp. That is a **2.3× range in width and a 2.6× range in height**, and
nothing designed against one end of it survives the other by accident.

## 2. The rules a layout has to keep

**Chrome may never hide a control.** Not off an edge, not under another element.
If it does not fit, it scrolls, wraps, or collapses into something that opens —
but it is always reachable. `PlayTopBar` is the worked example: above
`BAR_FITS_AT` it is the arrangement designed for the tablet, below it the row
scrolls.

**A threshold in the middle of a layout is measured, not chosen.** Find the
width where it actually breaks by shooting it, then round away from the failure.

**Anything compared against a size is in dp, never pixels.** A floor in pixels
is a floor that moves with density: `BoardLayouter.MIN_CARD_WIDTH` is 28
*pixels*, which is 14dp on the tablet and 9dp on a 3× phone — so the "too small
to read" guard fires three times later on the device that needs it most. See §4.

**Chrome that is a fixed dp is a fixed dp that grows as a fraction.** `TOP_BAR`
at 44dp is 4.8% of the tablet's height and 12.2% of a phone's. Fine for a bar;
not fine for anything that stacks.

**A material's scale is a property of the object, not of the viewport.** See §4.

## 3. What the four gates become

`docs/LOOP.md` §4 gains a fifth, and it is cheap now:

> **It survives the matrix.** `tools/devices.sh`, and look at `phone-small` and
> `tab-s11` at minimum. A change that is only checked at one size is a change
> that has only been checked on kai's desk.

Two shots is usually enough — the narrowest and the widest — because almost
every layout failure is monotonic in width.

## 4. Known, not yet fixed

Written down rather than fixed because each is a judgement call kai should have
a say in, and two of them trade against each other.

- **`MIN_CARD_WIDTH` is in pixels.** Making it honest — say 40dp — would be
  correct and would *also* make the play stage refuse to open on a 640dp phone,
  where the card currently lands at about 40dp. So the right fix is not the
  guard; it is giving the board more of the screen on narrow devices. Changing
  the constant alone would trade a small board for no board.
- **The board leaves the screen half empty on a phone.** The solver takes
  `min(byWidth, byHeight)`, and on a 360dp-tall landscape phone height wins hard
  — so the cards shrink to fit three rows plus a hand, and the width goes
  unused. A phone probably wants a different *arrangement* rather than the same
  one scaled down.
- **Material scales are tied to `cardWidth`.** `WoodGrain`'s ring pitch is
  `cardWidth / 0.8`, so the desk's planks are physically narrower on a phone
  than on a tablet — the same desk made of different timber depending on what
  you hold. Grain belongs to the desk; it should be tied to the desk's own size.
- **No quality tiers.** A full-screen per-pixel wood shader at 3× density on a
  midrange phone GPU is untested and is the most likely performance cliff in the
  app. `AAA.md` #98.
- **Portrait is refused** (`android:screenOrientation="userLandscape"`). Right
  for the play stage, probably wrong for the deck builder, and currently one
  decision covering both.
