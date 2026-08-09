#!/usr/bin/env bash
# Shoot the play stage at every device geometry that matters.
#
#   tools/devices.sh                       # the whole matrix
#   tools/devices.sh desk-night-seated     # one named shot, on every device
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

# name              px w   px h  density   dp box        what it is
DEVICES=(
  "tab-s11          2960   1848  2.0       1480x924      the tablet this is tuned on"
  "tab-a9           1920   1200  1.5       1280x800      a cheap 11-inch tablet"
  "fold-open        2208   1768  2.2       1004x804      a foldable, unfolded"
  "pixel8           2400   1080  2.625     914x411       a current phone, landscape"
  "s24              2340   1080  3.0       780x360       a narrow current phone"
  "phone-small      1920   1080  3.0       640x360       the narrowest thing likely"
  "desktop          1600   1000  1.0       1600x1000     a desktop window"
)

mkdir -p "$repo/shots/devices"
for row in "${DEVICES[@]}"; do
  read -r name w h d _box _desc <<<"$row"
  printf '%-14s %sx%s @%s\n' "$name" "$w" "$h" "$d"
  "$repo/tools/shoot.sh" \
    --out="$repo/shots/devices" \
    --width="$w" --height="$h" --density="$d" \
    --settle=140 --shots="$name-$shot" 2>&1 | grep -E '^\[studio\] [a-z]' || true
done

echo
echo "shots/devices/ — look at every one of them, not just the tablet."
