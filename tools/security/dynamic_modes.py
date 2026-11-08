#!/usr/bin/env python3
"""Dynamic check: point the DEBUG build at tools/security/hostile_adapter.py in every mode and record what the app does.

    export ANDROID_SERIAL=emulator-5560          # YOUR emulator; this script refuses to run on any other serial than $ANDROID_SERIAL
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    python3 tools/security/dynamic_modes.py [--serial emulator-5560] [--port 35040] [--seconds 12] [mode ...]

Per mode it prints: the commands the adapter received (the proof that only AT setup + mode 01 left the app), heap / PSS / threads / CPU
of the app, the texts the Drive screen shows after --seconds, and whether the process crashed. Needs a debuggable build because the
`set` launch extra (which points the app at the adapter) is ignored by release builds (DevLaunch).
"""
import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).parent
PKG = "dev.kuntal.cockpit"
ALLOWED_AT = {"ATZ", "ATI", "AT@1", "ATRV", "ATDP", "ATDPN", "ATE0", "ATE1", "ATL0", "ATL1", "ATS0", "ATS1", "ATH0", "ATH1", "ATAT0", "ATAT1", "ATAT2", "ATSP6", "ATCAF1"}


def adb(serial, *a, timeout=40):
    return subprocess.run(["adb", "-s", serial, *a], capture_output=True, text=True, timeout=timeout).stdout


def texts(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = adb(serial, "shell", "cat", "/sdcard/ui.xml")
    return " | ".join(t for t in re.findall(r'(?:text|content-desc)="([^"]+)"', x) if t.strip())


def mem(serial):
    pid = adb(serial, "shell", "pidof", PKG).strip()
    if not pid:
        return "NOT RUNNING"
    m = adb(serial, "shell", "dumpsys", "meminfo", pid)
    heap = re.search(r"Java Heap:\s+(\d+)", m)
    pss = re.search(r"TOTAL PSS:\s+(\d+)", m)
    thr = len(adb(serial, "shell", "ls", f"/proc/{pid}/task").split())
    cpu = adb(serial, "shell", "top", "-b", "-n", "1", "-p", pid).strip().splitlines()[-1].split()
    cpu_pct = next((c for c in cpu if re.fullmatch(r"\d+(\.\d+)?", c) and "." in c), "?")
    return f"pid={pid} javaHeap={heap and heap.group(1)}KB pss={pss and pss.group(1)}KB threads={thr} cpu%={cpu_pct}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("modes", nargs="*")
    ap.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", ""))
    ap.add_argument("--port", type=int, default=35040)
    ap.add_argument("--seconds", type=int, default=12)
    args = ap.parse_args()
    if not args.serial or args.serial != os.environ.get("ANDROID_SERIAL"):
        sys.exit("set ANDROID_SERIAL to your own emulator and pass the same --serial (never someone else's device)")
    modes = args.modes or ["noprompt", "endless", "drip", "longline", "control", "nodata", "nodataflood", "echoinject", "signs", "absurd", "instant", "garbage", "flap"]
    for mode in modes:
        log = Path(f"artifacts/dyn/mode-{mode}.log")
        log.parent.mkdir(parents=True, exist_ok=True)
        srv = subprocess.Popen([sys.executable, str(HERE / "hostile_adapter.py"), "--mode", mode, "--port", str(args.port)], stdout=open(log, "w"), stderr=subprocess.STDOUT)
        time.sleep(1)
        try:
            adb(args.serial, "logcat", "-c")
            adb(args.serial, "shell", "am", "force-stop", PKG)
            adb(args.serial, "shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity", "--ez", "reset", "true", "--es", "set",
                f"source=ObdTcp,tcpHost=10.0.2.2,tcpPort={args.port}", "--es", "screen", "drive")
            time.sleep(args.seconds)
            m1 = mem(args.serial)
            t = texts(args.serial)
            time.sleep(3)
            m2 = mem(args.serial)
            crash = [l for l in adb(args.serial, "logcat", "-d", "-s", "AndroidRuntime:E").splitlines() if re.search("FATAL|Exception|OutOfMemory", l)][:3]
        finally:
            srv.terminate()
            srv.wait(5)
        cmds = re.findall(r"cmd '([^']*)'", log.read_text())
        bad = sorted({c for c in cmds if c not in ALLOWED_AT and not re.fullmatch(r"01[0-9A-F]{2}", c)})
        print(f"=== {mode}: {len(cmds)} commands received, {len(set(cmds))} distinct, NOT-READ-ONLY commands: {bad or 'none'}")
        print(f"    distinct: {sorted(set(cmds))}")
        print(f"    mem@{args.seconds}s: {m1}\n    mem@+3s: {m2}")
        print(f"    crash: {crash or 'none'}")
        print(f"    screen: {t[:420]}")
        print(f"    connections: {len(re.findall('connected', log.read_text()))}", flush=True)


if __name__ == "__main__":
    main()
