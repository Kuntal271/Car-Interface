#!/usr/bin/env python3
"""The real ELM327 emulator (python-elm, `-s car`) on $ELM_PORT: kill and restart it repeatedly, then SIGSTOP / SIGCONT it (a hung adapter).
The link line on Settings > Connections must go Connected -> Connection lost -> Connected, every time, and the app must not die.

  python3 tools/chaos/chaos_realelm.py [--rounds 4]
"""
import os, signal, subprocess, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 4


def start_elm():
    p = subprocess.Popen(["bash", "-c", f"tail -f /dev/null | exec {PY} -m elm -n {ELM_PORT} -s car"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    for _ in range(80):
        if free_port_listening(ELM_PORT): return p
        time.sleep(.25)
    raise RuntimeError("elm did not start")


def kill_elm(p):
    try: os.killpg(p.pid, signal.SIGKILL)
    except ProcessLookupError: pass
    p.wait()


def elm_pids():
    out = subprocess.run(["pgrep", "-f", f"elm -n {ELM_PORT}"], capture_output=True, text=True).stdout.split()
    return [int(x) for x in out]


def wait_link(want, secs):
    t0 = time.time()
    last = None
    while time.time() - t0 < secs:
        last = link_text()
        if last in want: return True, last, time.time() - t0
    return False, last, time.time() - t0


reset_device()
elm = start_elm()
point_app_at_hostile()
ok, last, dt = wait_link({"Connected"}, 40)
Result.add("realelm/initial_connect", ok, f"{last} after {dt:.0f}s")
logcat_clear()
for r in range(ROUNDS):
    kill_elm(elm)
    ok1, last1, dt1 = wait_link({"Connection lost, retrying", "Connecting\u2026"}, 25)
    elm = start_elm()
    ok2, last2, dt2 = wait_link({"Connected"}, 45)
    Result.add(f"realelm/kill_restart_round_{r + 1}", ok1 and ok2 and alive(), f"lost shown={ok1} ({last1}, {dt1:.0f}s), reconnected={ok2} ({last2}, {dt2:.0f}s)")
for p in elm_pids(): os.kill(p, signal.SIGSTOP)                                 # hung adapter: the socket stays open, nothing answers
ok1, last1, dt1 = wait_link({"Connection lost, retrying", "Connecting\u2026"}, 40)
for p in elm_pids(): os.kill(p, signal.SIGCONT)
ok2, last2, dt2 = wait_link({"Connected"}, 60)
Result.add("realelm/sigstop_sigcont", ok1 and ok2 and alive(), f"hung -> {last1} after {dt1:.0f}s; resumed -> {last2} after {dt2:.0f}s")
log = logcat_dump(os.path.join(LOGS, "realelm.logcat"))
Result.add("realelm/no_fatal", not fatal_in_app(log))
kill_elm(elm)
sh(f"am force-stop {PKG}"); start("home", reset=True)
bad = [r for r in Result.rows if not r[1]]
print(f"{len(Result.rows) - len(bad)} passed, {len(bad)} failed")
sys.exit(1 if bad else 0)
