#!/usr/bin/env bash
#
# The conformance suite proves `src/core/` matches `:core`. Nothing proves the
# app *uses* `src/core/`, and for one release it did not: `main.c` hand-rolled
# the drop as a rectangle test and re-derived the hand with a different step
# cap, both in the one place the golden vectors structurally cannot see.
#
# This is a tripwire and not a proof, and is labelled as one. It catches the
# shape of that mistake - a constant core owns, appearing again in the app
# layer; a core function that used to be called and is not any more - and it
# cannot catch a fresh re-derivation that avoids both. The real guard is that
# there is now exactly one function for each of these to call.
set -euo pipefail

cd "$(dirname "$0")/.."
APP=(src/main.c src/gfx src/ar src/ui src/net)
# The constant check runs against the input and layout layer only. `src/gfx/`
# is vertex coordinates and colour channels by the hundred, and a check that
# reports 0.95 as a hysteresis threshold when it is the white point is a check
# that gets switched off.
GEOMETRY=(src/main.c)
fail=0

note() { printf '  %s\n' "$*"; }
bad()  { printf '\nFAIL  %s\n' "$*"; fail=1; }

# ---- constants that belong to exactly one file --------------------------- #
#
# Each is `<pattern>|<who owns it>`. A number is listed here only when the app
# layer has no legitimate reason to name it: an app that writes 0.74 has
# re-derived the hand's step rather than asked for it.
# Distinctive numbers only, and that is the point rather than a compromise: a
# 0.74 in the app layer is the hand's step and can be nothing else, where a 0.55
# is as likely to be a grey. The hysteresis thresholds are left out because they
# have no life outside a resolver - the positive check below is what proves the
# resolver is the one being asked.
OWNED=(
  '0\.74|MT_HAND_STEP_FRACTION, in mt_handfan.h'
  '1\.06|the old hand step cap, deleted - use mt_hand_step'
  '0\.686|CARD_ASPECT_DEFAULT, in mt_board_layout.h'
  '1\.10|MT_FAN_DEPARTURE, in mt_drop.h'
)

echo "constants the app layer may not re-derive:"
for entry in "${OWNED[@]}"; do
  pattern="${entry%%|*}"
  owner="${entry#*|}"
  if hits=$(grep -rn -- "${pattern}f\?" "${GEOMETRY[@]}" 2>/dev/null); then
    bad "the app layer names ${pattern//\\/} - that is ${owner}"
    printf '%s\n' "$hits" | sed 's/^/      /'
  else
    note "ok  ${pattern//\\/}"
  fi
done

# ---- shapes of a re-derivation ------------------------------------------- #

echo
echo "arithmetic the app layer may not do:"
# Basic regular expressions, where a parenthesis is a parenthesis. Written as
# `\(float\)` these matched a *group* and so matched nothing on earth, which is
# a check that passes for the wrong reason - the failure mode a tripwire is
# least able to notice about itself. Falsified, now, in all three shapes.
SHAPES=(
  '/ *(float)(count - 1)|a hand step - use mt_hand_step'
  '/ *(float)(places - 1)|a hand step - use mt_hand_step'
  'static .*centre_of|the hand geometry - use mt_hand_centre_of'
  'static .*hand_slot|the hand geometry - use mt_hand_centre_of'
  'static .*insert_at|the hand inverse - use mt_hand_insert_at'
  'static .*set_position|which way up - use mt_set_position'
)
for entry in "${SHAPES[@]}"; do
  pattern="${entry%%|*}"
  meaning="${entry#*|}"
  if hits=$(grep -rn -- "$pattern" "${APP[@]}" 2>/dev/null); then
    bad "the app layer computes ${meaning}"
    printf '%s\n' "$hits" | sed 's/^/      /'
  else
    note "ok  ${meaning}"
  fi
done

# ---- the positive half: the app must still call its own core ------------- #
#
# The stronger check of the two. A drop resolver that stops being called does
# not leave a suspicious constant behind - it leaves nothing behind at all.

echo
echo "core the app must still be calling:"
REQUIRED=(
  mt_drop_resolve
  mt_drop_commit
  mt_set_position
  mt_fan_home_seeing
  mt_hand_row
  mt_hand_centre_of
  mt_hand_step
  mt_spring_step
  mt_layout_to_mat
  mt_layout_to_pixels
  mt_board_solve
  mt_board_rect_of
)
for fn in "${REQUIRED[@]}"; do
  if grep -rq -- "${fn}(" "${APP[@]}" 2>/dev/null; then
    note "ok  ${fn}"
  else
    bad "${fn} is ported and the app no longer calls it"
  fi
done

echo
if [ "$fail" -ne 0 ]; then
  echo "app layer has drifted from its own core."
  exit 1
fi
echo "app layer is wired to its core."
