#!/usr/bin/env python3
"""Apps-screen abuse (F).  Needs artifacts/apks from tools/chaos/mkapks.py.

  * install the hostile apps (hostile labels, no icon, 4096x4096 icons, crashes-on-start, no launcher activity, 120 bulk apps)
  * open Apps, scroll, search, tap tiles very fast (including the crashing one)
  * install / uninstall / disable / enable apps while the grid is open and tap the tile of an app that is gone
  * check: no FATAL/ANR of the launcher, UI still answers, grid shows what the package manager says
"""
import glob, os, random, re, subprocess, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

R = random.Random(11)
APKS = sorted(p for p in glob.glob(os.path.join(ART, "apks", "*.apk")) if not p.endswith(".idsig"))
if not APKS: sys.exit("run tools/chaos/mkapks.py first")


def install(path):
    return adb("install", "-r", path, timeout=120).strip().splitlines()[-1:]


def apps_texts():
    return ui_texts() or []


def fatal(log): return fatal_in_app(log) or "OutOfMemoryError" in log and PKG in log


def search_count(q):
    """How many apps the Apps screen says match q ('N of M'), via the search box (the grid is lazy: only visible tiles are in a UI dump)."""
    if any(re.fullmatch(r"\d+ of \d+", s_) for s_, _ in apps_texts()): tap_design(24 + 520 - 28, 108)      # clear button
    tap_design(24 + 300, 108); time.sleep(.3)
    sh("input text '%s'" % q); time.sleep(.8)
    for s_, _ in apps_texts():
        m = re.fullmatch(r"(\d+) of (\d+)", s_)
        if m: return int(m.group(1))
        if s_.startswith("No apps match"): return 0
    return None


def tap_tiles(n, delay=0.0):
    for _ in range(n):
        col, row = R.randrange(6), R.randrange(4)
        tap_design(24 + col * 189 + 88, 148 + row * 160 + 74)
        if delay: time.sleep(delay)


def uninstall_all():
    for l in sh("pm list packages chaos.").split():
        sh(f"pm uninstall {l.split(':')[1]}")


reset_device()
uninstall_all()
base = [p for p in APKS if not any(x in p for x in ("bulk", "bigicon", "victim", "disable")) and p.endswith(".apk")]
logcat_clear()

# 1. hostile apps present at start
for p in base: install(p)
cold("apps", reset=True); time.sleep(4)
t = apps_texts()
labels = [s for s, _ in t]
n_twin, n_none, n_nolauncher = search_count("twin"), search_count("chaos.label.none"), search_count("no launcher")
Result.add("apps/duplicate_labels_both_listed", n_twin == 2, f"'Twin' matches {n_twin}")
Result.add("apps/app_without_label_shows_package_name", n_none == 1, f"'chaos.label.none' matches {n_none}")
Result.add("apps/huge_label_clamped", not any(len(s) > 80 for s in labels), f"longest text {max([len(s) for s in labels] or [0])}")
Result.add("apps/no_launcher_hidden", n_nolauncher == 0, f"'No launcher' matches {n_nolauncher}")
tap_tiles(60)                                    # includes the crashing app: it opens the system 'keeps stopping' dialog; the launcher must survive
sh("input keyevent KEYCODE_BACK"); time.sleep(1)
start("apps"); time.sleep(1.5)
log = logcat_dump(os.path.join(LOGS, "apps-1.logcat"))
Result.add("apps/tap_storm_with_crashy_app", not fatal(log) and alive() and ui_texts() is not None, "")

# 2. big icons (64 MB decoded each) + bulk apps
for p in [p for p in APKS if "bigicon" in p and p.endswith(".apk")][: (6 if "--quick" in sys.argv else 100)]: install(p)
logcat_clear()
cold("apps"); time.sleep(5)
log = logcat_dump(os.path.join(LOGS, "apps-bigicons.logcat"))
Result.add("apps/12_icons_of_4096px", not fatal(log) and alive(), "java heap %d MB" % (heap_kb() // 1024))
bulk = [p for p in APKS if "bulk" in p and p.endswith(".apk")][: (20 if "--quick" in sys.argv else 1000)]
t0 = time.time()
for p in bulk: install(p)
Result.add("apps/bulk_installed", True, f"{len(bulk)} apps in {time.time() - t0:.0f}s")
logcat_clear()
start("apps"); cold("apps"); time.sleep(6)
for _ in range(25):                                   # scroll the long grid fast
    sh("input swipe 600 700 600 150 60")
    sh("input swipe 600 150 600 700 60") if R.random() < .3 else None
tap_tiles(100)
sh("input keyevent KEYCODE_BACK"); start("apps"); time.sleep(1)
log = logcat_dump(os.path.join(LOGS, "apps-2.logcat"))
st = proc_stats() or {}
Result.add("apps/scroll_and_tap_with_150_apps", not fatal(log) and alive() and ui_texts() is not None, f"threads={st.get('threads')} heap={heap_kb() // 1024}MB")

# 3. change the package set while the grid is open
logcat_clear()
cold("apps"); time.sleep(4)
victim = [p for p in APKS if "victim" in p and p.endswith(".apk")][0]
dis = [p for p in APKS if "disable" in p and p.endswith(".apk")][0]
install(victim); install(dis); time.sleep(1)
sh("input keyevent KEYCODE_BACK") if False else None
# the grid was composed before the install: the new apps only appear after leaving and re-entering Apps (documented behaviour)
sh("pm uninstall chaos.victim")                       # the tile (if it was ever listed) is stale now
install(victim)
dock("Home"); dock("Apps"); time.sleep(2)
nv = search_count("victim")
Result.add("apps/new_app_listed_after_reenter", nv == 1, f"'Victim' matches {nv}")
# tap the victim's tile at once after removing it, many times
for _ in range(10):
    sh("pm uninstall chaos.victim"); sh("pm disable-user --user 0 chaos.disable"); tap_tiles(15); install(victim); sh("pm enable chaos.disable")
    tap_tiles(15)
    dock("Home"); dock("Apps")
log = logcat_dump(os.path.join(LOGS, "apps-3.logcat"))
Result.add("apps/remove_disable_while_open", not fatal(log) and alive() and ui_texts() is not None, "")
# 4. search abuse
for q in ["", "a" * 300, "\u202e\u0645\u0631\u062d\u0628\u0627", "\U0001F600" * 100, "%s%n%x", "' OR 1=1 --", "Twin", "\\", "[", "(", "*"]:
    tap_design(300, 108)
    sh("input text '%s'" % (q[:200].replace("'", "").replace("\\", "").replace("%", "") or "x")) if q else None
    sh("input keyevent KEYCODE_ENTER")
    time.sleep(.3)
    sh("input keyevent KEYCODE_MOVE_HOME; for i in $(seq 1 40); do input keyevent KEYCODE_DEL; done") if False else None
log = logcat_dump(os.path.join(LOGS, "apps-4.logcat"))
Result.add("apps/search_abuse", not fatal(log) and alive() and ui_texts() is not None, "")
uninstall_all()
sh(f"am force-stop {PKG}"); start("home")
bad = [r for r in Result.rows if not r[1]]
print(f"{len(Result.rows) - len(bad)} passed, {len(bad)} failed")
sys.exit(1 if bad else 0)
