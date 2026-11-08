#!/usr/bin/env bash
# tools/perf.sh <screen>...  : open each screen live (demo data on, Ford theme, boot skipped) and measure it.
#   SETX='source=ObdTcp,tcpHost=10.0.2.2,tcpPort=1' measures the screen with no live OBD samples (the simulator's 20 Hz stream otherwise redraws the status bar)
#   fps      = frames the app drew per second over a 10 s window (dumpsys gfxinfo, reset after a 6 s warm-up) - 0 means truly idle
#   gfx p50/p99 = total frame time from gfxinfo; app-cpu = the in-app frame meter (CockpitPerf), last window
cd "$(dirname "$0")/.."; source tools/env.sh
PKG=dev.kuntal.cockpit
for s in "$@"; do
  adb shell am force-stop $PKG; adb logcat -c
  adb shell am start -n $PKG/.MainActivity --es screen "$s" --es set "theme=Ford${SETX:+,$SETX}" --ez reset true --ez perf true >/dev/null
  sleep 6
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  sleep "${PERF_WAIT:-10}"
  g=$(adb shell dumpsys gfxinfo $PKG)
  total=$(echo "$g" | grep -m1 "Total frames rendered" | sed -E 's/[^0-9]//g')
  p50=$(echo "$g" | grep -m1 "50th percentile" | sed -E 's/[^0-9]//g'); p99=$(echo "$g" | grep -m1 "99th percentile" | sed -E 's/[^0-9]//g')
  cpu=$(adb logcat -d -s CockpitPerf:I | tail -1 | sed -E 's/^.*(app-cpu p50=[0-9.]+ p99=[0-9.]+).*$/\1/')
  echo "$s: fps=$(python3 -c "print(round(${total:-0}/${PERF_WAIT:-10},1))") frames=${total:-0} gfx_p50=${p50:-?}ms gfx_p99=${p99:-?}ms $cpu"
done
