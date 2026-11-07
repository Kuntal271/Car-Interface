# Helpers for the dynamic security run (source this). Needs the emulator on port 5560 and tools/env.sh.
#   source tools/security/dyn.sh; dyn_install artifacts/apk/base-release.apk; dyn_start --es set "tcpHost=10.0.2.2"
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
export ANDROID_SERIAL=emulator-5560   # forced, never inherited: a shared shell may carry another agent's serial
PKG=dev.kuntal.cockpit
dyn_install() { adb install -r "$1" 2>&1 | tail -1; }
dyn_start()   { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity "$@" | grep -E "Status|Error|Exception" ; }
dyn_pid()     { adb shell pidof $PKG | tr -d '\r'; }
dyn_mem()     { p=$(dyn_pid); [ -z "$p" ] && { echo "NOT RUNNING"; return; }; adb shell dumpsys meminfo $p | grep -E "Java Heap:|TOTAL PSS|TOTAL:" | head -2 | tr -s ' ' | tr '\n' '|'; echo " threads=$(adb shell ls /proc/$p/task | wc -l | tr -d ' ')"; }
dyn_cpu()     { p=$(dyn_pid); [ -z "$p" ] && { echo "cpu: NOT RUNNING"; return; }; adb shell top -b -n 1 -p $p 2>/dev/null | tail -1 | tr -s ' '; }
dyn_texts()   { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | python3 -c "
import sys,re
x=sys.stdin.read()
t=[m for m in re.findall(r'(?:text|content-desc)=\"([^\"]+)\"',x) if m.strip()]
print(' | '.join(t))"; }
dyn_shot()    { adb exec-out screencap -p > "$1"; }
dyn_crash()   { adb logcat -d -s AndroidRuntime:E | grep -E "FATAL|Exception|OutOfMemory" | head -5; }
