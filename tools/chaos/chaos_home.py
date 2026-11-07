#!/usr/bin/env python3
"""HOME key on the launcher: from Drive / Settings it must return to the Home screen (it used to be dropped by the running singleTask instance),
and BACK must finish the activity without crashing (FrameMeter removed its listener twice)."""
import os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

reset_device()
for scr, marker in [("drive", "Trip A"), ("settings", "Display")]:
    cold(scr, reset=True); time.sleep(3)
    sh("input keyevent KEYCODE_HOME"); time.sleep(1.5)
    after = [s for s, _ in ui_texts() or []]
    Result.add(f"home/home_key_returns_to_home_from_{scr}", marker not in after and "Queue \u203a" in after, "" if marker not in after else f"still on {scr}")
cold("home", reset=True); time.sleep(3); logcat_clear()
for _ in range(4):
    sh("input keyevent KEYCODE_BACK"); time.sleep(1.5)
    start("drive"); time.sleep(2)
log = logcat_dump()
Result.add("crash/back_key_finishes_activity_cleanly", not fatal_in_app(log), "FATAL in log" if fatal_in_app(log) else "")
sh(f"am force-stop {PKG}"); start("home", reset=True)
sys.exit(0 if all(r[1] for r in Result.rows) else 1)
