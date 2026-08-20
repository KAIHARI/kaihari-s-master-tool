#!/usr/bin/env bash
#
# Puts `makerom` and `bannertool` on the PATH.
#
# devkitPro ships neither: `dkp-pacman -S 3ds-dev` gives you the compiler,
# libctru, citro3d and 3dsxtool, and stops short of everything needed to turn an
# ELF into an installable title. So the .cia half of this build depends on two
# third-party tools, and this script is the *only* place that dependency lives -
# when it breaks, this is the file to fix and nothing else has to change.
#
# It is also the most fragile part of the build, for a reason worth writing
# down: bannertool's original upstream (Steveice10) was taken down, and what is
# used here is a maintained fork. Both a prebuilt binary and a source build are
# attempted, in that order, because the prebuilt releases are linked against a
# newer glibc than some devkitPro container images carry.
#
#   ./fetch-cia-tools.sh [destdir]     default: /opt/cia-tools
#
set -euo pipefail

DEST="${1:-/opt/cia-tools}"
MAKEROM_VERSION="${MAKEROM_VERSION:-v0.18.4}"
BANNERTOOL_REF="${BANNERTOOL_REF:-master}"

mkdir -p "$DEST"
cd "$(mktemp -d)"

say() { printf '\n== %s\n' "$*"; }

# ---------------------------------------------------------------- makerom ----
say "makerom $MAKEROM_VERSION"
if command -v makerom >/dev/null 2>&1; then
    echo "already on PATH: $(command -v makerom)"
else
    ok=0
    for asset in \
        "makerom-${MAKEROM_VERSION}-ubuntu_x86_64.zip" \
        "makerom-${MAKEROM_VERSION}-linux_x86_64.zip"
    do
        url="https://github.com/3DSGuy/Project_CTR/releases/download/makerom-${MAKEROM_VERSION}/${asset}"
        echo "trying $url"
        if curl -fsSL -o makerom.zip "$url"; then
            unzip -o -j makerom.zip -d "$DEST" >/dev/null
            ok=1
            break
        fi
    done
    if [ "$ok" -ne 1 ]; then
        echo "FAILED to download makerom."
        echo "Check the asset names at https://github.com/3DSGuy/Project_CTR/releases"
        echo "and update MAKEROM_VERSION or the asset list in this script."
        exit 1
    fi
    chmod +x "$DEST/makerom"
fi

# ------------------------------------------------------------- bannertool ----
say "bannertool ($BANNERTOOL_REF)"
if command -v bannertool >/dev/null 2>&1; then
    echo "already on PATH: $(command -v bannertool)"
else
    # Source build first, not second. The prebuilt Linux releases need
    # glibc 2.39+, which is newer than several devkitPro images; building takes
    # about a minute and does not care.
    if git clone --depth 1 --branch "$BANNERTOOL_REF" \
            https://github.com/carstene1ns/3ds-bannertool.git bannertool-src; then
        cmake -S bannertool-src -B bannertool-build \
              -DCMAKE_BUILD_TYPE=Release >/dev/null
        cmake --build bannertool-build --parallel "$(nproc)" >/dev/null
        found="$(find bannertool-build -type f -name bannertool -perm -u+x | head -1)"
        if [ -z "$found" ]; then
            echo "FAILED: built bannertool but found no binary."
            exit 1
        fi
        install -m755 "$found" "$DEST/bannertool"
    else
        echo "FAILED to clone bannertool."
        echo "Upstream (Steveice10/bannertool) was removed; this uses the"
        echo "carstene1ns fork. If that is gone too, Epicpkmn11/bannertool is"
        echo "the other maintained one."
        exit 1
    fi
fi

say "verifying"
export PATH="$DEST:$PATH"
command -v makerom
command -v bannertool
# Both tools exit non-zero on a bare invocation, so the check is that they run
# at all rather than what they return.
makerom 2>&1 | head -2 || true
bannertool 2>&1 | head -2 || true

echo
echo "add to PATH: $DEST"
