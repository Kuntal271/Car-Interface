#!/usr/bin/env bash
# tools/shot.sh <name>  -> docs/shots/<name>.png from the running emulator/device
set -euo pipefail
source "$(dirname "$0")/env.sh"
mkdir -p "$(dirname "$0")/../docs/shots"
adb exec-out screencap -p > "$(dirname "$0")/../docs/shots/${1:?name}.png"
echo "docs/shots/$1.png"
