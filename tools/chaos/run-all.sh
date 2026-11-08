#!/usr/bin/env bash
# Time-boxed (~10 min) regression suite for the defects the monkey/chaos review found.  Prints PASS/FAIL per scenario, exit 1 on any FAIL.
#
#   tools/chaos/run-all.sh              unit tests + build + install + quick chaos scenarios
#   tools/chaos/run-all.sh --no-build   reuse the debug APK that is already built (still installs it)
#   tools/chaos/run-all.sh --full       longer variants of every scenario (about 40 min, no soak)
#
# Needs: an emulator (default emulator-5556; for another one export ANDROID_SERIAL=... CHAOS_ANY_SERIAL=1), a free TCP port for the fake
# adapter (ELM_PORT, default 35020, reachable from the emulator as 10.0.2.2), tools/.venv (Pillow) for the hostile test APKs.
# DESTRUCTIVE to that emulator only: clears app data, changes size/density/font scale/locale/time, installs and removes chaos.* packages.
set -u
cd "$(dirname "$0")/../.."
source tools/env.sh
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5556}"
export ELM_PORT="${ELM_PORT:-35020}"
BUILD=1; FULL=0
for a in "$@"; do case "$a" in --no-build) BUILD=0;; --full) FULL=1;; esac; done
OUT=artifacts/logs/runall; mkdir -p "$OUT"; rm -f "$OUT"/*.out
PY=tools/.venv/bin/python
Q="--quick"; [ $FULL = 1 ] && Q=""
declare -a NAMES RESULTS
T0=$(date +%s)

# run NAME TIMEOUT_S cmd...   : runs with a watchdog; records every [PASS]/[FAIL] line the command prints (or its exit code if it prints none)
run() {
  local name=$1 limit=$2; shift 2
  echo "== $name (limit ${limit}s)"
  "$@" > "$OUT/$name.out" 2>&1 &
  local pid=$!
  ( sleep "$limit"; kill -9 "$pid" 2>/dev/null && echo "[FAIL] $name: timed out after ${limit}s" >> "$OUT/$name.out" ) &
  local wd=$!
  wait "$pid"; local rc=$?
  kill "$wd" 2>/dev/null; wait "$wd" 2>/dev/null
  if grep -qE '^\[(PASS|FAIL)\]' "$OUT/$name.out"; then
    while IFS= read -r line; do
      NAMES+=("${line:7:70}"); [ "${line:1:4}" = "PASS" ] && RESULTS+=(PASS) || RESULTS+=(FAIL)
    done < <(grep -E '^\[(PASS|FAIL)\]' "$OUT/$name.out")
  else
    NAMES+=("$name"); [ $rc = 0 ] && RESULTS+=(PASS) || RESULTS+=(FAIL)
  fi
  [ $rc != 0 ] && ! grep -q '^\[FAIL\]' "$OUT/$name.out" && { NAMES+=("$name (exit $rc)"); RESULTS+=(FAIL); }
  return 0
}

adb devices | grep -q "^$ANDROID_SERIAL" || { echo "emulator $ANDROID_SERIAL not found"; exit 2; }
if [ "$ANDROID_SERIAL" != "emulator-5556" ] && [ "${CHAOS_ANY_SERIAL:-0}" != "1" ]; then
  echo "refusing to run destructive chaos on $ANDROID_SERIAL; set CHAOS_ANY_SERIAL=1 if that really is a throw-away emulator"; exit 2; fi

# 1. pure-logic regression tests (every fix has one)
if [ $BUILD = 1 ]; then
  run unit_tests 420 bash -c 'tools/gb.sh :app:testDebugUnitTest :vehicle:test :app:assembleDebug > artifacts/logs/runall/gradle.log 2>&1; rc=$?; tail -5 artifacts/logs/runall/gradle.log; exit $rc'
fi
APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "no APK at $APK"; exit 2; }
adb install -r "$APK" > "$OUT/install.out" 2>&1 || { cat "$OUT/install.out"; exit 2; }
adb shell cmd package set-home-activity dev.kuntal.cockpit/.MainActivity > /dev/null 2>&1
$PY tools/chaos/mkapks.py --bulk 0 > "$OUT/mkapks.out" 2>&1 || true

# 2. the BACK-key crash (FrameMeter) and the HOME key
run home_back 120 $PY tools/chaos/chaos_home.py

# 3. monkey: the seeds that crashed before the fixes, plus a spread
run monkey 600 $PY tools/chaos/monkeys.py --seeds $([ $FULL = 1 ] && echo 20 || echo 4) --events $([ $FULL = 1 ] && echo 5000 || echo 3000) --start-seed 1 --timeout 300
# 4. hostile adapter
run link 600 $PY tools/chaos/chaos_link.py $Q --secs $([ $FULL = 1 ] && echo 25 || echo 14)
# 5. persistence, input, apps, UI/system chaos
run realelm 300 $PY tools/chaos/chaos_realelm.py --rounds $([ $FULL = 1 ] && echo 5 || echo 2)
run session 200 $PY tools/chaos/chaos_session.py
run persist 300 $PY tools/chaos/chaos_persist.py $Q
run input 300 $PY tools/chaos/chaos_input.py $Q
run apps 360 $PY tools/chaos/chaos_apps.py $Q
if [ $FULL = 1 ]; then run ui 1500 $PY tools/chaos/chaos_ui.py --n 300; else run ui 360 $PY tools/chaos/chaos_ui.py --only dock,keys,relaunch,display,fontscale --n 80; fi
run rtl 200 $PY tools/chaos/chaos_rtl.py

echo; echo "================ chaos regression suite ($(( $(date +%s) - T0 )) s) ================"
FAILS=0
for i in "${!NAMES[@]}"; do printf '%-6s %s\n' "${RESULTS[$i]}" "${NAMES[$i]}"; [ "${RESULTS[$i]}" = FAIL ] && FAILS=$((FAILS+1)); done
echo "---- $(( ${#NAMES[@]} - FAILS )) passed, $FAILS failed (logs: $OUT/)"
adb shell am force-stop dev.kuntal.cockpit; adb shell am start -n dev.kuntal.cockpit/.MainActivity --ez reset true > /dev/null
[ $FAILS = 0 ]
