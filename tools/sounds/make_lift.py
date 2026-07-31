#!/usr/bin/env python3
"""Synthesises lift.wav — the sound of a card being picked up.

The first version was a broadband click with a noise transient in it, which
reads as a snap rather than a touch: sharp, a little startling, and tiring at
the rate a deckbuilder picks cards up. This is the rhythm-game answer to the
same problem (osu!'s soft hitnormal is the reference): a short pitched blip
with a fast attack and a smooth exponential tail, no noise, no click at either
end. It lands as *contact* rather than as *impact*.

The shape, deliberately plain:
  - a sine at 880 Hz gliding down to 700 Hz over the first 25 ms, which is the
    "give" of the card leaving the pile,
  - a quiet octave above it for definition, decaying twice as fast so it is a
    tick at the front rather than a tone,
  - a 1.5 ms raised-cosine attack and an exponential decay to silence, so
    neither end of the sample can produce a step and therefore a click.

Run: python3 tools/sounds/make_lift.py
"""

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44_100
DURATION_S = 0.055
PEAK = 0.30           # quiet on purpose: this fires on every card touched
F_START, F_END = 880.0, 700.0
GLIDE_S = 0.025
ATTACK_S = 0.0015
DECAY_TAU = 0.014     # exponential time constant of the body
OCTAVE_MIX = 0.22
OCTAVE_TAU = 0.007

OUT = Path(__file__).resolve().parents[2] / (
    "app/ui/src/commonMain/composeResources/files/sounds/lift.wav"
)


def render() -> bytes:
    frames = int(SAMPLE_RATE * DURATION_S)
    samples = []
    phase = 0.0
    octave_phase = 0.0

    for n in range(frames):
        t = n / SAMPLE_RATE

        # Pitch glide, held flat once the glide is over.
        glide = min(t / GLIDE_S, 1.0)
        freq = F_START + (F_END - F_START) * glide
        phase += 2 * math.pi * freq / SAMPLE_RATE
        octave_phase += 2 * math.pi * (freq * 2) / SAMPLE_RATE

        attack = 0.5 - 0.5 * math.cos(math.pi * min(t / ATTACK_S, 1.0))
        body = math.exp(-t / DECAY_TAU)
        octave = math.exp(-t / OCTAVE_TAU)

        value = attack * (body * math.sin(phase) + OCTAVE_MIX * octave * math.sin(octave_phase))

        # A cosine fade over the last 8 ms guarantees the tail reaches exactly
        # zero; an exponential alone never quite does, and the step at the end
        # of the buffer is audible as a tick on every play.
        remaining = DURATION_S - t
        if remaining < 0.008:
            value *= 0.5 - 0.5 * math.cos(math.pi * max(remaining, 0.0) / 0.008)

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
