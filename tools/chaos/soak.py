#!/usr/bin/env python3
"""Soak (G): simulator + random input for N minutes while sampling memory / threads / fds / GC / frame stats every minute.

  python tools/chaos/soak.py [--minutes 32] [--out artifacts/logs/soak.csv] [--source Simulator|ObdTcp]

Random input is a throttled `monkey` limited to touches and motion events (no system keys, so it stays in the app).  With --source ObdTcp
a 'valid' fake adapter runs on $ELM_PORT.  The verdict fits a least-squares line to each series over the last two thirds of the run and
fails when Java heap, native heap, threads or fds grow monotonically (heap +20 MB, threads/fds +10 over the window) or the app dies.
"""
import argparse, csv, os, re, subprocess, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

ap = argparse.ArgumentParser()
ap.add_argument("--minutes", type=float, default=32)
ap.add_argument("--out", default=os.path.join(LOGS, "soak.csv"))
ap.add_argument("--source", default="Simulator")
ap.add_argument("--seed", type=int, default=99)
a = ap.parse_args()

reset_device()
srv = None
sets = "source=Simulator"
if a.source == "ObdTcp":
    srv = start_hostile("valid"); sets = f"source=ObdTcp,tcpHost=10.0.2.2,tcpPort={ELM_PORT}"
cold("drive", reset=True, sets=sets, perf=True); time.sleep(4)
logcat_clear()
def monkey(seed):
    return subprocess.Popen([ADB, "-s", SERIAL, "shell", f"monkey -p {PKG} -s {seed} --throttle 200 --ignore-crashes --ignore-timeouts --pct-touch 70 --pct-motion 25 "
                             f"--pct-nav 0 --pct-majornav 0 --pct-syskeys 0 --pct-appswitch 0 --pct-trackball 0 --pct-anyevent 5 2000"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


seed = a.seed
mon = monkey(seed)
rows = []
t0 = time.time()
sh("dumpsys gfxinfo %s reset" % PKG)
while time.time() - t0 < a.minutes * 60:
    if mon.poll() is not None:                                  # one monkey run is 2000 events (about 5 min); keep the input going for the whole soak
        seed += 1; mon = monkey(seed)
    time.sleep(55)
    st = proc_stats()
    if st is None:
        rows.append(dict(min=(time.time() - t0) / 60, alive=0)); print("app dead at", rows[-1]["min"], flush=True); cold("drive", sets=sets); continue
    gfx = sh(f"dumpsys gfxinfo {PKG}")
    jank = re.search(r"Janky frames: \d+ \(([\d.]+)%\)", gfx)
    frames = re.search(r"Total frames rendered: (\d+)", gfx)
    p99 = re.search(r"99th percentile: (\d+)ms", gfx)
    log = adb("logcat", "-d", "-v", "threadtime", timeout=60)
    app_pid = str(pid() or "")
    gcs = len([l for l in log.splitlines() if re.search(r"concurrent copying GC|GC freed|Explicit concurrent", l) and re.match(r"\S+ \S+\s+%s\s" % app_pid, l)])
    with open(os.path.join(LOGS, "soak-strictmode.log"), "a") as f:                 # StrictMode violations of OUR process (build with strictmode.patch)
        f.writelines(l + "\n" for l in log.splitlines() if "StrictMode" in l and app_pid and re.match(r"\S+ \S+\s+%s\s" % app_pid, l))
    logcat_clear()
    row = dict(min=round((time.time() - t0) / 60, 1), alive=1, java_kb=st["java"], native_kb=st["native"], pss_kb=st["pss"], rss_kb=st["rss"], threads=st["threads"], fds=st["fds"],
               gc_lines_last_min=gcs, jank_pct=jank.group(1) if jank else "", frames=frames.group(1) if frames else "", p99_ms=p99.group(1) if p99 else "")
    rows.append(row); print(row, flush=True)
    with open(a.out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=sorted({k for r in rows for k in r})); w.writeheader(); w.writerows(rows)
mon.terminate()
stop_proc(srv)


def median(v):
    v = sorted(v); n = len(v)
    return (v[n // 2] + v[(n - 1) // 2]) / 2


live = [r for r in rows if r.get("alive")]
# A least-squares fit chases the transient spikes (the Settings page can start a connection attempt: fds +20 for a minute), so compare
# the median of the last third with the median of the first third, and report the least-squares slope for information.
third = max(1, len(live) // 3)
for key, limit in [("java_kb", 20 * 1024), ("native_kb", 20 * 1024), ("threads", 10), ("fds", 10)]:
    if len(live) >= 6:
        first, last = median([r[key] for r in live[:third]]), median([r[key] for r in live[-third:]])
        xs = [r["min"] for r in live]; ys = [r[key] for r in live]; mx = sum(xs) / len(xs); my = sum(ys) / len(ys)
        slope = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / (sum((x - mx) ** 2 for x in xs) or 1)
        Result.add(f"soak/{key}_growth", last - first < limit, f"median first third {first:.0f} -> last third {last:.0f} (limit +{limit}); least-squares slope {slope:.1f}/min over {live[-1]['min']:.0f} min")
Result.add("soak/app_survived", all(r.get("alive") for r in rows) and len(rows) >= 3, f"{len(rows)} samples, {sum(1 for r in rows if not r.get('alive'))} dead")
bad = [r for r in Result.rows if not r[1]]
sys.exit(1 if bad else 0)
