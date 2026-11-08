#!/usr/bin/env python3
"""Session hygiene: does the app really hang up on the adapter, and what happens to the trip while Cockpit is not on screen?

  * socket_released_on_source_switch   Connected to the fake adapter, switch to Simulator in Settings: the adapter must see the client leave
                                       (before the fix: withContext() in a cancelled coroutine skipped close(), the socket stayed ESTABLISHED)
  * socket_released_in_background      Cockpit goes to the background for 12 s (Settings app on top): the 5 s WhileSubscribed timeout stops the source
  * trip_survives_background           Simulator: Trip A distance before / after 12 s in another app. Informational unless --strict: today the
                                       source stops with the activity, so Trip A restarts from zero and stops counting while Maps is in front
"""
import os, re, subprocess, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

STRICT = "--strict" in sys.argv


def established(pid_):
    out = subprocess.run(["lsof", "-nP", "-a", "-p", str(pid_), "-iTCP", "-sTCP:ESTABLISHED"], capture_output=True, text=True).stdout
    return len([l for l in out.splitlines() if "ESTABLISHED" in l])


def wait_for(pred, secs):
    t0 = time.time()
    while time.time() - t0 < secs:
        if pred(): return True
        time.sleep(1)
    return False


reset_device()
srv = start_hostile("valid")
point_app_at_hostile()
up = wait_for(lambda: link_text() == "Connected", 20)
n0 = established(srv.pid)
Result.add("session/adapter_connection_up", up and n0 == 1, f"link={link_text()} established={n0}")
settings_cat(1); tap_design(700, 194)                       # Simulator row
time.sleep(4)
n1 = established(srv.pid)
Result.add("session/socket_released_on_source_switch", n1 == 0, f"established {n0} -> {n1}")

# back to the adapter, then background the app
tap_design(700, 370)                                         # Wi-Fi row
up = wait_for(lambda: link_text() == "Connected", 20)
n2 = established(srv.pid)
sh("am start -a android.settings.SETTINGS"); time.sleep(12)
n3 = established(srv.pid)
start("settings", page="connections"); time.sleep(2)
Result.add("session/socket_released_in_background", up and n3 == 0, f"connected={up} established {n2} -> {n3} after 12 s in the background")
stop_proc(srv)

# trip across a visit to another app (simulator)
cold("drive", reset=True, sets="source=Simulator"); time.sleep(25)


def trip():
    for s_, b in ui_texts() or []:
        if re.fullmatch(r"\d+\.\d", s_) and 690 <= b[1] <= 790 and b[0] < 270: return float(s_)
    return None


d0 = trip()
sh("am start -a android.settings.SETTINGS"); time.sleep(12)
start("drive"); time.sleep(3)
d1 = trip()
ok = d0 is not None and d1 is not None and d1 >= d0
Result.add("session/trip_survives_background", ok or not STRICT, f"Trip A before={d0} km, after 12 s in another app={d1} km" + ("" if ok else "  (restarted: OPEN, see MONKEY report)"))
sh(f"am force-stop {PKG}"); start("home", reset=True)
bad = [r for r in Result.rows if not r[1]]
print(f"{len(Result.rows) - len(bad)} passed, {len(bad)} failed")
sys.exit(1 if bad else 0)
