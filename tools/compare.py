#!/usr/bin/env python3
"""Put two contact sheets side by side and say what moved.

    tools/shoot.sh --out=shots/before
    ...change something...
    tools/shoot.sh --out=shots/after
    tools/compare.py shots/before shots/after

Writes `shots/compare/<name>.png` — before on the left, after on the right, and
the difference amplified underneath — and prints a line per shot.

The amplified difference is the part worth having. A change to the light moves
every pixel by two or three levels, which is invisible in a side-by-side and
obvious the moment it is multiplied. A change that moves *nothing* is a change
that did not happen, and the loop should find that out here rather than on a
tablet.

    tools/compare.py before after --zoom 520,680,1100,950
        crops both to that box first, for looking at one card rather than a room.
"""
import sys
import os
from PIL import Image, ImageChops, ImageDraw

AMPLIFY = 12  # a two-level change is the interesting size, so make it visible


def load(path, box):
    im = Image.open(path).convert("RGB")
    return im.crop(box) if box else im


def compare(before, after, out, box):
    a, b = load(before, box), load(after, box)
    if a.size != b.size:
        print(f"  size changed {a.size} -> {b.size}; not comparable")
        return None

    diff = ImageChops.difference(a, b)
    stats = diff.convert("L")
    hist = stats.histogram()
    total = sum(hist)
    changed = total - hist[0]
    mean = sum(i * n for i, n in enumerate(hist)) / max(total, 1)
    peak = max(i for i, n in enumerate(hist) if n) if changed else 0

    amplified = diff.point(lambda v: min(255, v * AMPLIFY))

    w, h = a.size
    sheet = Image.new("RGB", (w * 2 + 12, h * 2 + 18), (24, 24, 24))
    sheet.paste(a, (0, 0))
    sheet.paste(b, (w + 12, 0))
    sheet.paste(amplified, (0, h + 18))
    draw = ImageDraw.Draw(sheet)
    draw.text((8, h + 4), f"before | after  —  difference x{AMPLIFY} below", fill=(200, 200, 200))
    sheet.save(out)

    return changed / max(total, 1) * 100, mean, peak


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    box = None
    for a in sys.argv[1:]:
        if a.startswith("--zoom"):
            nums = a.split("=", 1)[1] if "=" in a else sys.argv[sys.argv.index(a) + 1]
            box = tuple(int(n) for n in nums.split(","))
            args = [x for x in args if x != nums]

    if len(args) < 2:
        print(__doc__)
        return 2

    before, after = args[0], args[1]
    out = args[2] if len(args) > 2 else os.path.join(os.path.dirname(after) or ".", "compare")
    os.makedirs(out, exist_ok=True)

    names = sorted(n for n in os.listdir(after) if n.endswith(".png"))
    if not names:
        print(f"no shots in {after}")
        return 1

    for name in names:
        old = os.path.join(before, name)
        if not os.path.isfile(old):
            print(f"{name:28s} new — nothing to compare against")
            continue
        print(f"{name:28s} ", end="")
        result = compare(old, os.path.join(after, name), os.path.join(out, name), box)
        if result:
            pct, mean, peak = result
            verdict = "identical" if pct == 0 else f"{pct:5.1f}% of pixels moved, mean {mean:5.2f}, peak {peak}"
            print(verdict)

    print(f"\nsheets in {out}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
