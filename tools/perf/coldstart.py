#!/usr/bin/env python3
"""Cold and warm start time under each ART compile state, plus how rough the first seconds are.

  export ANDROID_SERIAL=emulator-5554
  tools/perf/coldstart.py --apk app/build/outputs/apk/release/app-release.apk --runs 7 --states verify,speed-profile,speed

Per state: install the APK, put ART in that state (`cmd package compile -m <state> -f`; for speed-profile the app is run for
--profile-secs first so profileinstaller / the JIT profile saver have written a profile), then N cold starts (`am start -W`: TotalTime = launch
to first frame drawn) and N warm starts, and the frame statistics of the first --first-secs seconds (janky share from dumpsys gfxinfo).
"""
import argparse, os, re, statistics, subprocess, sys, time

PKG = "dev.kuntal.cockpit"
ACT = PKG + "/.MainActivity"


def adb(*a):
    ser = os.environ.get("ANDROID_SERIAL")
    if not ser:
        sys.exit("ANDROID_SERIAL is not set")
    return subprocess.run(["adb", "-s", ser, *a], capture_output=True, text=True, timeout=300).stdout


def sh(c):
    return adb("shell", c)


def start(screen):
    out = sh(f"am start -W -n {ACT} --es screen {screen} --es set 'source=Simulator'")
    def g(k):
        m = re.search(k + r": (\d+)", out)
        return int(m.group(1)) if m else None
    ls = re.search(r"LaunchState: (\w+)", out)
    return g("TotalTime"), g("WaitTime"), (ls.group(1) if ls else "?")


def med(v):
    v = [x for x in v if x is not None]
    return f"{statistics.median(v):.0f} [{min(v)}..{max(v)}]" if v else "-"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--runs", type=int, default=7)
    ap.add_argument("--states", default="verify,speed-profile,speed")
    ap.add_argument("--screen", default="home")
    ap.add_argument("--profile-secs", type=int, default=12)
    ap.add_argument("--first-secs", type=int, default=4)
    a = ap.parse_args()
    rows = []
    for state in a.states.split(","):
        adb("install", "-r", "-d", a.apk)
        if state == "speed-profile":
            sh(f"am force-stop {PKG}"); start(a.screen); time.sleep(a.profile_secs)         # profileinstaller writes the embedded profile ~5 s after start
            sh(f"am force-stop {PKG}")
        print(f"[{state}]", sh(f"cmd package compile -m {state} -f {PKG}").strip().splitlines()[-1:], flush=True)
        cold, wait, jank, p99, frames = [], [], [], [], []
        for i in range(a.runs):
            sh(f"am force-stop {PKG}"); time.sleep(1.5)
            t, w, ls = start(a.screen); cold.append(t); wait.append(w)
            time.sleep(a.first_secs)
            gfx = sh(f"dumpsys gfxinfo {PKG}")
            f = re.search(r"Total frames rendered: (\d+)", gfx); j = re.search(r"Janky frames: (\d+)", gfx); q = re.search(r"99th percentile: (\d+)ms", gfx)
            frames.append(int(f.group(1)) if f else None); jank.append(int(j.group(1)) if j else None); p99.append(int(q.group(1)) if q else None)
        warm = []
        for i in range(a.runs):
            sh("am start -n com.android.settings/.Settings >/dev/null"); time.sleep(1.5)
            t, w, ls = start(a.screen); warm.append(t)
        rows.append((state, med(cold), med(wait), med(warm), med(frames), med(jank), med(p99)))
    print(f"\n{'state':<14}{'cold TotalTime ms':>24}{'WaitTime ms':>20}{'warm ms':>18}{'frames@' + str(a.first_secs) + 's':>16}{'janky':>14}{'gfx p99 ms':>14}")
    for r in rows:
        print(f"{r[0]:<14}{r[1]:>24}{r[2]:>20}{r[3]:>18}{r[4]:>16}{r[5]:>14}{r[6]:>14}")
    print(f"\nload at end: {os.getloadavg()[0]:.1f} (median [min..max] of {a.runs} runs each)")


if __name__ == "__main__":
    main()
