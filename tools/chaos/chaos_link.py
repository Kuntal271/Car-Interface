#!/usr/bin/env python3
"""Hostile-adapter scenarios (C).  For each mode of tools/chaos/hostile_elm.py: start the server, point the app at it (Settings > Connections,
ObdTcp 10.0.2.2:$ELM_PORT), watch for --secs, and check the invariants:

  * the app process never dies, never logs FATAL/ANR
  * the UI keeps answering (uiautomator dump succeeds)
  * Java heap does not grow by more than 48 MB
  * the link line says Connected ONLY for modes where the adapter really delivers data (valid, fast)
  * CPU stays under 60 % of one core (a spinning loop would show here)

  python tools/chaos/chaos_link.py [--secs 20] [--modes valid,endless,...] [--apk path]    (--apk: install that build first)
"""
import argparse, sys, time, os
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

GOOD = {"valid", "fast", "badecho"}      # badecho: half of the replies are fine (an echoing adapter), so Connected is right
PARTIAL = {"absurd"}                    # valid framing, extreme values: MAP/throttle/load at 100 % are physically possible, rpm/speed are not -> Connected, but no impossible number on Drive                                    # modes where Connected is the right answer
ALL = ["valid", "fast", "garbage", "noprompt", "endless", "endless_nl", "halfline", "badecho", "nodata", "unable", "buserr", "absurd", "signs",
       "slowdrip", "reset", "closeloop", "huge", "hang", "stale"]

ap = argparse.ArgumentParser()
ap.add_argument("--secs", type=float, default=20)
ap.add_argument("--modes", default=",".join(ALL))
ap.add_argument("--apk", default="")
ap.add_argument("--tag", default="")
ap.add_argument("--quick", action="store_true", help="the modes that exposed real defects, 12 s each")
a = ap.parse_args()
if a.quick: a.modes = "valid,endless,unable,absurd,signs,stale,hang,closeloop"; a.secs = min(a.secs, 14)
if a.apk:
    print(adb("install", "-r", a.apk)); sh(f"cmd package set-home-activity {ACT}")

reset_device()
summary = []
for mode in a.modes.split(","):
    srv = start_hostile(mode, *(["--stale-after", "8"] if mode == "stale" else []))
    logcat_clear()
    point_app_at_hostile()
    h0 = heap_kb()
    links, heaps, ui_fail, max_cpu = [], [], 0, 0.0
    t_end = time.time() + a.secs
    while time.time() < t_end:
        if not alive(): break
        lt = link_text()
        if lt is None: ui_fail += 1
        links.append(lt)
        heaps.append(heap_kb())
        if len(links) == 2: max_cpu = max(max_cpu, cpu_pct(3))
    log = logcat_dump(os.path.join(LOGS, f"link-{mode}{a.tag}.logcat"))
    died = not alive()
    fatal = fatal_in_app(log) or "OutOfMemoryError" in log and PKG in log
    st = proc_stats() or {}
    stop_proc(srv)
    connected_n = sum(1 for l in links if l == "Connected")
    growth = (max(heaps) - h0) // 1024 if heaps else 0
    problems = []
    if died: problems.append("process died")
    if fatal: problems.append("FATAL/ANR/OOM in log")
    if ui_fail > 1: problems.append(f"UI did not answer {ui_fail}x")
    if growth > 48: problems.append(f"java heap +{growth} MB")
    if mode in GOOD and connected_n == 0: problems.append("never reached Connected")
    if mode == "stale":
        if connected_n == 0: problems.append("never reached Connected while the adapter was healthy")
        if links and links[-1] == "Connected": problems.append("still Connected 10+ s after the adapter stopped answering")
    elif mode in PARTIAL:
        dock("Drive"); time.sleep(2.5)
        shown = [t_ for t_, _ in (ui_texts() or [])]
        imp = [t_ for t_ in shown if t_ in ("255", "16,383", "16383", "65.5 V", "215\u00b0", "215")]
        if imp: problems.append(f"impossible values on Drive: {imp}")
        dock("Settings"); settings_cat(1)
    elif mode not in GOOD and connected_n > 0: problems.append(f"showed Connected {connected_n}/{len(links)} polls against a '{mode}' adapter")
    if max_cpu > 60: problems.append(f"cpu {max_cpu:.0f}%")
    ok = not problems
    Result.add(f"link/{mode}", ok, f"links={sorted(set(map(str, links)))} heap+{growth}MB cpu={max_cpu:.0f}% threads={st.get('threads')} fds={st.get('fds')} " + "; ".join(problems))
    summary.append((mode, ok))
sh(f"am force-stop {PKG}")
sys.exit(0 if all(ok for _, ok in summary) else 1)
