#!/usr/bin/env bash
# Creates (once) an AVD that mimics the Palm Bay 1000 screen: 1280x800 @160dpi, 2 GB RAM, Android 11 arm64.
set -euo pipefail
source "$(dirname "$0")/env.sh"
AVD=palmbay
if ! avdmanager list avd -c | grep -qx "$AVD"; then
  echo no | avdmanager create avd -n "$AVD" -k "system-images;android-30;google_apis;arm64-v8a" -d pixel_c
fi
CFG="$HOME/.android/avd/$AVD.avd/config.ini"
python3 - "$CFG" <<'PY'
import sys
p = sys.argv[1]
want = {"hw.lcd.width": "1280", "hw.lcd.height": "800", "hw.lcd.density": "160", "hw.ramSize": "2048",
        "hw.cpu.ncore": "4", "hw.initialOrientation": "landscape", "hw.keyboard": "yes", "skin.name": "1280x800"}
keep = [l for l in open(p).read().splitlines() if l.split("=")[0].strip() not in list(want) + ["skin.path"]]
open(p, "w").write("\n".join(keep + ["%s=%s" % kv for kv in want.items()]) + "\n")
PY
echo "AVD $AVD ready: $(emulator -list-avds | tr '\n' ' ')"
