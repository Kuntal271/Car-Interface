#!/usr/bin/env python3
"""Scripted UI / system chaos against the Cockpit launcher (B).  Each scenario ends with the same check:
process alive (or restarted by the system), no FATAL/ANR of the app in logcat, the UI still answers a uiautomator dump, and the
persisted settings file parses and holds only values the app accepts.

  python tools/chaos/chaos_ui.py [--only dock,settings,keys,...] [--n 300] [--seed 7]
Scenarios: dock settings keys relaunch screen display fontscale locale time proc trim link_switch
"""
import argparse, random, re, sys, time, os
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

ap = argparse.ArgumentParser()
ap.add_argument("--only", default="")
ap.add_argument("--n", type=int, default=300)
ap.add_argument("--seed", type=int, default=7)
a = ap.parse_args()
R = random.Random(a.seed)

VALID = {"theme": {"Ford", "Midnight"}, "clock": {"H24", "H12"}, "motion": {"Full", "Reduced"}, "speed": {"KMH", "MPH"}, "pressure": {"BAR", "PSI", "KPA"},
         "temp": {"C", "F"}, "source": {"Simulator", "ObdTcp", "ObdBluetooth"}}


def settings_ok():
    """The stored settings must be loadable and in range (the app sanitises on load, but what it WRITES must already be clean)."""
    xml = settings_xml()
    if "<map" not in xml: return True, "no settings file yet"
    vals = dict(re.findall(r'<string name="([^"]+)">([^<]*)</string>', xml))
    bad = [f"{k}={v}" for k, v in vals.items() if k in VALID and v not in VALID[k]]
    for k, lo, hi in [("redline", 3000, 9000), ("tach", 5000, 9000), ("shift", 2000, 9000), ("speedScale", 120, 300), ("tcpPort", 1, 65535)]:
        if k in vals and not (vals[k].lstrip("-").isdigit() and lo <= int(vals[k]) <= hi): bad.append(f"{k}={vals[k]}")
    if int(vals.get("shift", 0) or 0) > int(vals.get("redline", 9999) or 9999): bad.append("shift>redline")
    if int(vals.get("redline", 0) or 0) > int(vals.get("tach", 9999) or 9999): bad.append("redline>tach")
    if len(vals.get("tcpHost", "")) > 253: bad.append("tcpHost too long")
    return not bad, ",".join(bad)


def check(name, expect_alive=True):
    problems = []
    log = logcat_dump(os.path.join(LOGS, f"ui-{name}.logcat"))
    if fatal_in_app(log): problems.append("FATAL/ANR in log")
    if "OutOfMemoryError" in log and PKG in log: problems.append("OOM")
    if expect_alive:
        if not alive():
            start("home"); time.sleep(3)
            problems.append("process was dead (restarted)")
        if ui_texts() is None: problems.append("UI dump failed (frozen or no window)")
    if expect_alive and alive():
        shot = screenshot("check-" + name)
        if looks_blank(shot): problems.append(f"black/blank screen ({shot})")
    ok, why = settings_ok()
    if not ok: problems.append("settings: " + why)
    return Result.add(f"ui/{name}", not problems, "; ".join(problems))


def fresh(screen="home", **kw):
    reset_device(); cold(screen, reset=True, **kw); time.sleep(2.5); logcat_clear()


def sc_dock():
    fresh()
    names = ["Home", "Drive", "Car", "Apps", "Settings"]
    for _ in range(a.n): dock(R.choice(names))
    check("dock")


def sc_settings():
    fresh("settings")
    for i in range(a.n):
        r = R.random()
        if r < .15: settings_cat(R.randrange(4))
        elif r < .25: dock(R.choice(["Home", "Drive", "Settings"]))
        else: tap_design(R.randint(400, 1130), R.randint(95, 770))           # anywhere in the detail card: chips, steppers, source rows, text boxes
        if i % 50 == 0: sh("input keyevent KEYCODE_BACK") if R.random() < .3 else None
    check("settings")


def sc_keys():
    fresh("drive")
    keys = ["KEYCODE_BACK", "KEYCODE_HOME", "KEYCODE_APP_SWITCH", "KEYCODE_POWER", "KEYCODE_MENU", "KEYCODE_DPAD_DOWN", "KEYCODE_TAB", "KEYCODE_ENTER", "KEYCODE_VOLUME_UP", "KEYCODE_SEARCH"]
    for i in range(a.n):
        k = R.choice(keys)
        sh(f"input keyevent {k}")
        if k == "KEYCODE_POWER" and R.random() < .6: sh("input keyevent KEYCODE_WAKEUP")
    sh("input keyevent KEYCODE_WAKEUP"); sh("wm dismiss-keyguard 2>/dev/null")
    sh(f"am start -n {ACT}")
    time.sleep(2)
    check("keys")


def sc_relaunch():
    fresh()
    variants = ["", "--es screen drive", "--es screen settings --es page connections", "--es screen apps", "--es screen bogus --es page 42",
                "--es set 'theme=Midnight,clock=H12'", "--es set 'tcpPort=abc,,=,,x=,=y,tcpHost='", "--es set 'source=ObdTcp,tcpHost=10.0.2.2,tcpPort=1'",
                "--es set 'source=Simulator'", "--ez reset true", "--ez snapshot true", "--ez perf true", "--ei screen 3 --ez reset 5", "--es set '" + "k=v," * 2000 + "'"]
    for _ in range(a.n):
        sh(f"am start -n {ACT} " + R.choice(variants))
        if R.random() < .1: time.sleep(.3)
    time.sleep(3)
    check("relaunch")


def sc_screen():
    fresh("drive")
    for _ in range(40):
        sh("input keyevent KEYCODE_SLEEP"); time.sleep(R.random() * .4)
        sh("input keyevent KEYCODE_WAKEUP"); time.sleep(R.random() * .4)
    sh("wm dismiss-keyguard 2>/dev/null"); time.sleep(2)
    check("screen_onoff")


SIZES = ["800x480", "1920x720", "1024x600", "1280x800", "720x1280", "480x800", "3840x1080", "640x360", "1000x1000", "320x240", "1200x500", "2560x1600", "1280x400", "100x100", "1280x800"]


def sc_display():
    fresh("drive")
    for sz in SIZES:
        sh(f"wm size {sz}"); time.sleep(1.8)
        problems = []
        if not alive(): problems.append("died")
        t = ui_texts()
        if t is None: problems.append("no UI")
        if problems: Result.add(f"ui/display-{sz}", False, ";".join(problems)); start("drive")
        else:
            w, h = map(int, sz.split("x"))
            # the status-bar clock and the dock must lie inside the screen whatever the aspect ratio
            dockb = [b for s_, b in t if s_ == "Home"]
            inside = all(b[2] <= w + 2 and b[3] <= h + 2 for _, b in t)
            Result.add(f"ui/display-{sz}", inside and bool(dockb), "" if inside else "text outside the screen: " + str([(s_, b) for s_, b in t if b[2] > w + 2 or b[3] > h + 2][:3]))
        screenshot(f"disp-{sz}")
    for d in [120, 160, 213, 240, 320, 480, 80, 160]:
        sh(f"wm density {d}"); time.sleep(1.5)
        Result.add(f"ui/density-{d}", alive() and ui_texts() is not None)
    sh("wm size reset; wm density reset"); time.sleep(1.5)
    check("display")


def sc_fontscale():
    fresh("settings", page="vehicle")
    for fs in [0.8, 1.0, 1.3, 1.5, 2.0, 0.85, 1.0]:
        sh(f"settings put system font_scale {fs}"); time.sleep(1.8)
        t = ui_texts() or []
        steps = [b for s_, b in t if s_ == "km/h"]
        Result.add(f"ui/fontscale-{fs}", alive() and t != [] , f"text boxes={len(t)}")
    check("fontscale")


def sc_locale():
    for tag in ["ar-EG", "he-IL", "fa-IR", "hi-IN", "ja-JP", "de-DE", "en-US"]:
        set_locale(tag)
        for scr, pg in [("settings", "vehicle"), ("drive", None), ("apps", None), ("home", None)]:
            cold(scr, page=pg, reset=(scr == "home")); time.sleep(2.5)
            t = ui_texts()
            shot = screenshot(f"locale-{tag}-{scr}")
            ok = alive() and t is not None and not looks_blank(shot)
            msg = ""
            if scr == "settings" and t:
                ctl = [b for s_, b in t if s_ in ("km/h", "mph", "bar", "psi", "kPa", "\u00b0C", "\u00b0F")]
                left = [b for b in ctl if b[0] < 800]
                ok = ok and not left; msg = f"{len(ctl)} unit chips, {len(left)} left of x=800"
            # digits must stay Latin whatever the locale (the status-bar clock and every value use Locale.US)
            if t and any(re.search(r"[\u0660-\u0669\u06f0-\u06f9\u0966-\u096f]", s_) for s_, _ in t):
                ok = False; msg += " non-Latin digits on screen"
            Result.add(f"ui/locale-{tag}-{scr}", ok, msg + f" shot={shot}")
    check("locale")


def sc_time():
    adb("root"); time.sleep(3)                       # `date -u` needs root (userdebug image)
    fresh("home")
    sh("settings put global auto_time 0; settings put global auto_time_zone 0")
    sh("settings put system time_12_24 12"); time.sleep(1.2)
    t12 = [s_ for s_, _ in (ui_texts() or []) if re.fullmatch(r"\d{1,2}:\d\d", s_)]
    sh("settings put system time_12_24 24"); time.sleep(1.2)
    for tz in ["Pacific/Kiritimati", "Pacific/Pago_Pago", "Asia/Kolkata", "Asia/Kathmandu", "America/St_Johns", "UTC"]:
        sh(f"service call alarm 3 s16 {tz}"); time.sleep(.7)
    for stamp in ["010100002099.00", "123123592037.30", "030300001999.00", "022812002000.00", "010100002000.00"]:
        sh(f"date -u {stamp}"); time.sleep(1.5)
        Result.add(f"ui/clock-{stamp}", alive() and ui_texts() is not None)
    # cold start with the clock far in the future / past
    for stamp in ["010100002099.00", "010100001971.00"]:
        sh(f"date -u {stamp}"); cold("drive"); time.sleep(3)
        Result.add(f"ui/coldstart-clock-{stamp}", alive() and ui_texts() is not None)
    import datetime
    now = datetime.datetime.utcnow().strftime("%m%d%H%M%Y.%S")
    sh(f"date -u {now}"); sh("settings put global auto_time 1; settings put global auto_time_zone 1")
    sh("service call alarm 3 s16 Asia/Kolkata")
    check("time")


def sc_proc():
    fresh("drive")
    trims = {"trim-critical": "RUNNING_CRITICAL", "trim-complete": "COMPLETE", "trim-moderate": "MODERATE", "trim-hidden": "HIDDEN"}
    for i in range(14):
        how = R.choice(["kill", "force-stop", "kill9"] + list(trims))
        if how == "kill": sh(f"am kill {PKG}")
        elif how == "force-stop": sh(f"am force-stop {PKG}")
        elif how == "kill9": sh(f"kill -9 $(pidof {PKG})")
        else: sh(f"am send-trim-memory {PKG} {trims[how]}")
        time.sleep(.6)
        sh(f"am start -n {ACT} --es screen " + R.choice(["home", "drive", "settings", "apps"])); time.sleep(1.8)
    # leave for another app, let the system kill the process, come back through HOME
    sh("input keyevent KEYCODE_HOME"); sh("am start -a android.settings.SETTINGS"); time.sleep(2); sh(f"am kill {PKG}"); sh("input keyevent KEYCODE_HOME"); time.sleep(3)
    check("proc_death_restore")


def sc_trim():
    fresh("apps")
    for lvl in ["RUNNING_MODERATE", "RUNNING_LOW", "RUNNING_CRITICAL", "UI_HIDDEN", "BACKGROUND", "MODERATE", "COMPLETE"] * 3:
        sh(f"am send-trim-memory {PKG} {lvl}"); time.sleep(.3)
        if R.random() < .3: dock(R.choice(["Home", "Apps", "Drive"]))
    check("trim")


def sc_link_switch():
    """Theme / unit / source switching while the data link is connecting, connected and failing."""
    import subprocess
    srv = subprocess.Popen([PY, os.path.join(ROOT, "tools", "chaos", "hostile_elm.py"), "--port", str(ELM_PORT), "--mode", "cycle:valid,nodata,garbage,closeloop,valid,unable", "--cycle-s", "4", "--quiet"])
    time.sleep(1)
    fresh("settings", page="connections", sets=f"source=ObdTcp,tcpHost=10.0.2.2,tcpPort={ELM_PORT}")
    pages = {0: "Display", 1: "Connections", 2: "Vehicle"}
    for i in range(a.n):
        page = R.randrange(3)
        settings_cat(page); time.sleep(.05)
        if page == 1:
            tap_design(700, R.choice([194, 282, 370]))                        # Simulator / Bluetooth / Wi-Fi source rows
        else:
            tap_design(R.randint(820, 1120), R.choice([186, 258, 330, 258, 454, 526, 598, 670]))
    stop_proc(srv)
    time.sleep(2)
    check("link_switch")


ALL = {"dock": sc_dock, "settings": sc_settings, "keys": sc_keys, "relaunch": sc_relaunch, "screen": sc_screen, "display": sc_display,
       "fontscale": sc_fontscale, "locale": sc_locale, "time": sc_time, "proc": sc_proc, "trim": sc_trim, "link_switch": sc_link_switch}
want = a.only.split(",") if a.only else list(ALL)
try:
    for n in want:
        print("== scenario", n, flush=True)
        logcat_clear()
        ALL[n]()
finally:
    reset_device()
    start("home")
fails = [r for r in Result.rows if not r[1]]
print(f"\n{len(Result.rows) - len(fails)} passed, {len(fails)} failed")
sys.exit(1 if fails else 0)
