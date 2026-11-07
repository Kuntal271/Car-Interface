#!/usr/bin/env bash
# tools/perf.sh <screen>...  : open each screen live (demo data on, Ford theme) with the frame meter and print the last 5 s window.
#   idle fps = n / 5 (frames drawn in the window); app-cpu = per-frame app thread cost.
cd "$(dirname "$0")/.."; source tools/env.sh
for s in "$@"; do
  adb logcat -c
  adb shell am start -n dev.kuntal.cockpit/.MainActivity --es screen "$s" --es set "theme=Ford" --ez reset true --ez perf true >/dev/null
  sleep "${PERF_WAIT:-16}"
  line=$(adb logcat -d -s CockpitPerf:I | tail -1 | sed -E 's/^.*CockpitPerf: //')
  echo "$s: $line"
done
