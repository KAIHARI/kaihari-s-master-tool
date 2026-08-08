#!/usr/bin/env bash
# Draw the play stage to PNG files without opening a window.
#
#   tools/shoot.sh                                  # the default contact sheet
#   tools/shoot.sh --shots=desk-night-seated        # one named shot
#   tools/shoot.sh --out=/tmp/x --density=2         # tablet metrics
#
# Shots land in shots/ at the repository root unless --out says otherwise.
# See app/studio/src/jvmMain/.../Studio.kt for the whole flag list.
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The studio needs :ui, which needs the Android SDK even for the desktop target.
if [[ ! -f "$repo/app/local.properties" && -d "${ANDROID_HOME:-/nonexistent}" ]]; then
  echo "sdk.dir=$ANDROID_HOME" > "$repo/app/local.properties"
fi

args="$*"
if [[ "$args" != *"--out="* ]]; then
  mkdir -p "$repo/shots"
  args="--out=$repo/shots $args"
fi

cd "$repo/app"
exec ./gradlew --quiet -Pmastertool.android=true -Pmastertool.studio=true \
  :studio:shoot -Pshot.args="$args"
