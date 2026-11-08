#!/usr/bin/env python3
"""RTL regression: under an RTL locale (ar-EG, he-IL; switched live with lib.set_locale) the right-aligned controls on Settings > Vehicle must stay on
the right of their card.  Before the fix (LocalLayoutDirection forced to Ltr in DesignCanvas) every Arrangement.End row slid to the left edge."""
import os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *


def controls_ok(tag):
    cold("settings", page="vehicle", reset=True); time.sleep(4)
    t = ui_texts()
    shot = screenshot(f"rtl-{tag}")
    if t is None: return False, "no UI dump"
    ctl = [b for s_, b in t if s_ in ("km/h", "mph", "bar", "psi", "kPa", "\u00b0C", "\u00b0F")]
    left = [b for b in ctl if b[0] < 800]                       # in LTR all of them are at x >= 850
    return (len(ctl) >= 6 and not left), f"{len(ctl)} unit chips, {len(left)} left of x=800 (shot {shot})"


try:
    for tag in ("ar-EG", "he-IL"):
        Result.add(f"rtl/locale_switched_{tag}", set_locale(tag))
        sh("am force-stop com.google.android.apps.nexuslauncher")
        ok, why = controls_ok(tag)
        Result.add(f"rtl/settings_vehicle_controls_stay_right_in_{tag}", ok, why)
finally:
    set_locale("en-US")
    start("home", reset=True)
sys.exit(0 if all(r[1] for r in Result.rows) else 1)
