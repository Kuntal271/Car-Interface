#!/usr/bin/env python3
"""Android `monkey` at scale against the Cockpit launcher.

  python tools/chaos/monkeys.py [--seeds 20] [--events 5000] [--start-seed 1] [--only confined|ignore|leave]

For every seed it varies throttle and the event mix, clears logcat, runs monkey, then greps the log for FATAL EXCEPTION / ANR /
StrictMode / native crashes of the app and records the result in artifacts/logs/monkey-summary.tsv (one line per run).
Three families rotate: confined to the package (stop at first crash), --ignore-crashes/--ignore-timeouts (keep going), and
"leave the app" (whitelist of system packages too).
"""
import argparse, os, subprocess, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

MIXES = {
    "default":  "",
    "touchy":   "--pct-touch 80 --pct-motion 12 --pct-nav 2 --pct-majornav 2 --pct-syskeys 2 --pct-appswitch 1 --pct-anyevent 1",
    "navkeys":  "--pct-touch 15 --pct-motion 5 --pct-nav 35 --pct-majornav 25 --pct-syskeys 15 --pct-appswitch 5",
    "syskeys":  "--pct-touch 20 --pct-motion 5 --pct-nav 5 --pct-majornav 5 --pct-syskeys 55 --pct-appswitch 10",
    "motion":   "--pct-touch 10 --pct-motion 70 --pct-pinchzoom 10 --pct-nav 3 --pct-majornav 3 --pct-syskeys 2 --pct-appswitch 2",
    "any":      "--pct-touch 30 --pct-motion 10 --pct-trackball 10 --pct-nav 10 --pct-majornav 5 --pct-syskeys 5 --pct-appswitch 5 --pct-flip 5 --pct-anyevent 20",
}
THROTTLES = [0, 5, 15, 25]
WHITELIST = ["dev.kuntal.cockpit", "com.android.settings", "com.android.systemui", "com.android.launcher3", "com.google.android.apps.nexuslauncher",
             "com.android.documentsui", "com.android.calculator2", "com.android.dialer", "com.android.contacts", "com.android.permissioncontroller",
             "com.google.android.packageinstaller", "com.android.packageinstaller", "com.android.camera2", "com.android.deskclock", "com.android.messaging"]

ap = argparse.ArgumentParser()
ap.add_argument("--seeds", type=int, default=20)
ap.add_argument("--events", type=int, default=5000)
ap.add_argument("--start-seed", type=int, default=1)
ap.add_argument("--only", default="")
ap.add_argument("--timeout", type=int, default=900)
a = ap.parse_args()

summary = os.path.join(LOGS, "monkey-summary.tsv")
if not os.path.exists(summary):
    open(summary, "w").write("seed\tfamily\tmix\tthrottle\tevents\tseconds\tmonkey_rc\tcrash\tanr\tstrictmode\talive\tnote\n")

for i in range(a.seeds):
    seed = a.start_seed + i
    family = ["confined", "ignore", "confined", "ignore", "leave"][i % 5]
    if a.only and family != a.only: continue
    mix = list(MIXES)[(i + a.start_seed) % len(MIXES)]
    thr = THROTTLES[(i * 3 + i // 4) % len(THROTTLES)]
    flags = "-p dev.kuntal.cockpit" if family != "leave" else ""
    if family == "leave":
        open("/tmp/wl.txt", "w").write("\n".join(WHITELIST) + "\n")
        adb("push", "/tmp/wl.txt", "/sdcard/wl.txt")
        flags = "--pkg-whitelist-file /sdcard/wl.txt"
    if family in ("ignore", "leave"): flags += " --ignore-crashes --ignore-timeouts --ignore-security-exceptions --monitor-native-crashes"
    reset_device()
    cold("home", reset=(i % 2 == 0))
    time.sleep(2)
    logcat_clear()
    ev = a.events           # slow throttles get fewer events so the suite stays time-boxed
    cmd = f"monkey {flags} -s {seed} --throttle {thr} {MIXES[mix]} -v -v {ev}"
    t0 = time.time()
    try:
        out = adb("shell", cmd, timeout=a.timeout)
        rc = "ok"
    except subprocess.TimeoutExpired as e:
        out = (e.stdout or b"").decode() if isinstance(e.stdout, bytes) else (e.stdout or "")
        rc = "TIMEOUT"
        sh("pkill -f com.android.commands.monkey")
    secs = time.time() - t0
    log = logcat_dump(os.path.join(LOGS, f"monkey-{seed}-{family}.logcat"))
    open(os.path.join(LOGS, f"monkey-{seed}-{family}.out"), "w").write(out)
    crash = "CRASH" in out or "// Long Msg" in out or fatal_in_app(log)
    anr = "ANR in dev.kuntal" in log or "// NOT RESPONDING: dev.kuntal.cockpit" in out
    strict = any("StrictMode" in l and PKG in l for l in log.splitlines())
    note = ""
    if "Monkey aborted" in out: note += "aborted;"
    if "Events injected" in out:
        note += [l for l in out.splitlines() if "Events injected" in l][0].strip()
    line = f"{seed}\t{family}\t{mix}\t{thr}\t{ev}\t{secs:.0f}\t{rc}\t{int(crash)}\t{int(anr)}\t{int(strict)}\t{int(alive())}\t{note}"
    open(summary, "a").write(line + "\n")
    print(line, flush=True)
    sh("input keyevent KEYCODE_HOME")
    sh("am force-stop com.android.settings")
reset_device()
