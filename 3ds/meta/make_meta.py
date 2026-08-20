#!/usr/bin/env python3
"""
The HOME-menu artwork, drawn rather than shipped.

A 48x48 icon and a 256x128 banner, generated with nothing but zlib so that the
repository carries the *drawing* and not two opaque PNGs nobody can diff. That
is the same instinct as `core/input/MatGuide.kt` being data instead of prose:
artwork checked in as a binary is artwork that cannot be reviewed, and the next
person to change the palette has to open an image editor to find out it moved.

The design is the handbook's, in the small: sharp white on true black, colour
only as light. The card motif carries `drawPrismaticInset`'s two hues rather
than the six-hue Prism ramp, because that primitive is the card's own foil and
this is a picture of cards.

    python3 3ds/meta/make_meta.py
"""

import math
import struct
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent

BLACK = (0, 0, 0)
WHITE = (255, 255, 255)
# The two hues drawPrismaticInset swings between - a foil, not a fringe.
CYAN = (90, 210, 255)
MAGENTA = (255, 105, 180)

SS = 4  # supersampling factor, for edges that are not stairs


def write_png(path, width, height, pixels):
    """pixels: list of (r,g,b) rows, top to bottom."""
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBB", *px) for px in row) for row in pixels
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)
    return len(png)


def rounded_rect_sdf(px, py, cx, cy, hw, hh, radius, angle):
    """Signed distance to a rounded rectangle, negative inside."""
    dx, dy = px - cx, py - cy
    ca, sa = math.cos(-angle), math.sin(-angle)
    rx, ry = dx * ca - dy * sa, dx * sa + dy * ca
    qx = abs(rx) - (hw - radius)
    qy = abs(ry) - (hh - radius)
    outside = math.hypot(max(qx, 0.0), max(qy, 0.0))
    inside = min(max(qx, qy), 0.0)
    return outside + inside - radius


class Card:
    """One card: a white face with a foil edge inset four per cent inside it."""

    def __init__(self, cx, cy, hw, angle):
        self.cx, self.cy = cx, cy
        self.hw = hw
        self.hh = hw / 0.686        # a real card's aspect
        self.angle = angle
        self.radius = hw * 0.10

    def sample(self, px, py):
        """Returns (r,g,b) or None where this card is not."""
        d = rounded_rect_sdf(px, py, self.cx, self.cy, self.hw, self.hh,
                             self.radius, self.angle)
        if d > 0:
            return None
        # The foil sits *inside* the card, which is what makes it a foil and
        # not a glow. Which hue depends on where the edge faces, so the two
        # swing as the card leans - the same reason the real one is swung by
        # where the card is rather than by a timer.
        inset = self.hw * 0.16
        if d > -inset:
            t = (d + inset) / inset          # 0 at the inner lip, 1 at the edge
            dx, dy = px - self.cx, py - self.cy
            ca, sa = math.cos(-self.angle), math.sin(-self.angle)
            ry = dx * sa + dy * ca
            hue = CYAN if ry < 0 else MAGENTA
            k = t * t
            return tuple(int(WHITE[i] * (1 - k) + hue[i] * k) for i in range(3))
        return WHITE


def render(width, height, cards):
    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            acc = [0.0, 0.0, 0.0]
            for sy in range(SS):
                for sx in range(SS):
                    px = x + (sx + 0.5) / SS
                    py = y + (sy + 0.5) / SS
                    hit = BLACK
                    # Painted back to front, so the near card wins - the same
                    # invariant the play stage keeps: draw order is depth.
                    for card in cards:
                        got = card.sample(px, py)
                        if got is not None:
                            hit = got
                    for i in range(3):
                        acc[i] += hit[i]
            row.append(tuple(int(c / (SS * SS)) for c in acc))
        rows.append(row)
    return rows


def make_icon():
    # One card, leaned. At 48px anything more is mud.
    cards = [Card(cx=24, cy=24, hw=11.5, angle=math.radians(-13))]
    n = write_png(HERE / "icon.png", 48, 48, render(48, 48, cards))
    print(f"icon.png    48x48    {n} bytes")


def make_banner():
    # Three cards fanned, which is what a hand looks like from across a table.
    cards = [
        Card(cx=104, cy=70, hw=21, angle=math.radians(-24)),
        Card(cx=134, cy=64, hw=21, angle=math.radians(-6)),
        Card(cx=164, cy=70, hw=21, angle=math.radians(12)),
    ]
    n = write_png(HERE / "banner.png", 256, 128, render(256, 128, cards))
    print(f"banner.png  256x128  {n} bytes")


def make_banner_audio():
    """
    Silence.

    The HOME menu loops this while the banner is selected, and 'nothing idles'
    is a house rule (CLAUDE.md, the scene contract). A jingle that plays every
    time the cursor passes over the icon is the loudest possible violation of it.
    """
    rate, seconds = 22050, 3
    frames = rate * seconds
    data = b"\x00\x00" * frames
    header = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVE"
    header += b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, rate, rate * 2, 2, 16)
    header += b"data" + struct.pack("<I", len(data))
    (HERE / "banner.wav").write_bytes(header + data)
    print(f"banner.wav  {seconds}s mono 16-bit silence")


if __name__ == "__main__":
    make_icon()
    make_banner()
    make_banner_audio()
