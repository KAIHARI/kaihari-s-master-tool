#!/usr/bin/env python3
"""Synthesises declare.wav — the sound of announcing a card's effect.

A tap on a card is a *declaration*: "I am using this". Duelingbook marks the
same moment with a small bright chime, and it works for a reason worth stating,
because it is the reason this file is not another `lift.wav`. Every other sound
on this table is a **contact** — a card leaving a pile, a card landing, a deck
being riffled — and they are all short, low and dry, so the room stays quiet
under a fast hand. A declaration is not a contact. Nothing physically happened;
you said something. So it is the one sound here with a *pitch relationship* in
it rather than a single voice:

  - two sines a perfect fifth apart (880 Hz and 1320 Hz), struck together,
  - the fifth decaying about twice as fast, so the interval is heard at the
    attack and a single note is what rings on — a bell's behaviour, and what
    keeps two notes from reading as two events,
  - no glide, unlike lift.wav's, because a declaration does not give: it is
    announced, and a pitch that sags reads as something being set down.

Quiet, and shorter than a real chime. It fires on every tap of every card in a
combo line, and a combo line is twenty taps in fifteen seconds; anything with a
tail long enough to overlap itself turns into a chord.

Run: python3 tools/sounds/make_declare.py
"""

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44_100
DURATION_S = 0.115
PEAK = 0.26            # under lift.wav's 0.30: it fires as often and says more
F_ROOT = 880.0
F_FIFTH = 1320.0       # 3:2, exactly — a tempered fifth beats against the root
ATTACK_S = 0.0012
ROOT_TAU = 0.032
FIFTH_TAU = 0.016
FIFTH_MIX = 0.55
FADE_S = 0.012

OUT = Path(__file__).resolve().parents[2] / (
    "app/ui/src/commonMain/composeResources/files/sounds/declare.wav"
)


def render() -> bytes:
    frames = int(SAMPLE_RATE * DURATION_S)
    samples = []
    root_phase = 0.0
    fifth_phase = 0.0

    for n in range(frames):
        t = n / SAMPLE_RATE

        root_phase += 2 * math.pi * F_ROOT / SAMPLE_RATE
        fifth_phase += 2 * math.pi * F_FIFTH / SAMPLE_RATE

        attack = 0.5 - 0.5 * math.cos(math.pi * min(t / ATTACK_S, 1.0))
        root = math.exp(-t / ROOT_TAU)
        fifth = math.exp(-t / FIFTH_TAU)

        value = attack * (
            root * math.sin(root_phase) + FIFTH_MIX * fifth * math.sin(fifth_phase)
        )

        # The same cosine fade `make_lift.py` ends on, and for the same reason:
        # an exponential never quite reaches zero, and the step at the end of
        # the buffer is audible as a tick on every single play.
        remaining = DURATION_S - t
        if remaining < FADE_S:
            value *= 0.5 - 0.5 * math.cos(math.pi * max(remaining, 0.0) / FADE_S)

        samples.append(value)

    loudest = max(abs(s) for s in samples) or 1.0
    scale = PEAK / loudest
    return b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s * scale)) * 32767)) for s in samples)


def main() -> None:
    pcm = render()
    with wave.open(str(OUT), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(pcm)
    print(f"wrote {OUT} ({len(pcm) + 44} bytes, {len(pcm) // 2} frames)")


if __name__ == "__main__":
    main()
