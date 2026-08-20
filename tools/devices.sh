#!/usr/bin/env bash
# Shoot the play stage at every device geometry that matters.
#
#   tools/devices.sh                       # the whole matrix
#   tools/devices.sh desk-night-seated     # one named shot, on every device
#   tools/devices.sh flat --screen=builder # the deck builder, on every device
#
# Anything after the shot name is passed straight through to shoot.sh, which is
# how --screen=builder reaches the one screen people spend their time in.
#
# Lands in shots/devices/<device>-<shot>.png.
#
# This exists because the app was developed against one tablet and broke on a
# phone in a way nothing here could see: the top bar threw half its controls off
# the right-hand edge at 640dp and the stage rendered perfectly the whole time.
# A contact sheet at one size cannot catch that. This is the same instrument
# pointed at the sizes people actually hold.
#
# Widths and heights are PHYSICAL pixels and the density is the real one, so
# each line reproduces that device's dp box exactly — which is what the layout
# solver actually responds to.
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
shot="${1:-desk-day-table}"
shift || true
passthrough=("$@")

# The portrait rows are not decoration. The app was landscape-locked until the
# builder grew a portrait arrangement, and the whole point of that arrangement
# is a window shaped like these — taller than it is wide, which is the one test
# `Posture.of` makes. A matrix that only holds landscape geometries cannot see
# the layout it is meant to be checking.
#
# name              px w   px h  density   dp box        what it is
DEVICES=(
  "tab-s11          2960   1848  2.0       1480x924      the tablet this is tuned on"
  "tab-a9           1920   1200  1.5       1280x800      a cheap 11-inch tablet"
  "fold-open        2208   1768  2.2       1004x804      a foldable, unfolded"
  "pixel8           2400   1080  2.625     914x411       a current phone, landscape"
  "s24              2340   1080  3.0       780x360       a narrow current phone"
  "phone-small      1920   1080  3.0       640x360       the narrowest thing likely"
  "desktop          1600   1000  1.0       1600x1000     a desktop window"
  "s24-portrait     1080   2340  3.0       360x780       the phone, held upright"
  "pixel8-portrait  1080   2400  2.625     411x914       a roomier phone, upright"
  "small-portrait   1080   1920  3.0       360x640       the narrowest, upright"
  "tab-s11-portrait 1848   2960  2.0       924x1480      the tablet, held upright"
)

mkdir -p "$repo/shots/devices"
for row in "${DEVICES[@]}"; do
  read -r name w h d _box _desc <<<"$row"
  printf '%-14s %sx%s @%s\n' "$name" "$w" "$h" "$d"
  "$repo/tools/shoot.sh" \
    --out="$repo/shots/devices" \
    --width="$w" --height="$h" --density="$d" \
    --settle=140 --shots="$name-$shot" ${passthrough[@]+"${passthrough[@]}"} 2>&1 \
    | grep -E '^\[studio\] [a-z]' || true
done

echo
echo "shots/devices/ — look at every one of them, not just the tablet."
