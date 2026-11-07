#!/usr/bin/env bash
# tools/snap.sh <screen>  -> installs nothing; launches the installed app frozen on the design values and saves docs/shots/<screen>-snap.png
set -euo pipefail
cd "$(dirname "$0")/.."; source tools/env.sh
adb shell am force-stop dev.kuntal.cockpit
adb shell am start -n dev.kuntal.cockpit/.MainActivity --es screen "${1:?screen}" --ez snapshot true >/dev/null
sleep "${SNAP_WAIT:-4}"
adb exec-out screencap -p > "docs/shots/$1-snap.png"
echo "docs/shots/$1-snap.png"
