"""Shared helpers for the Cockpit chaos scripts (tools/chaos/*.py).  Everything goes through ONE emulator: $ANDROID_SERIAL.

Never run these against a device you care about: they clear data, change density/locale/time and kill processes.
"""
import os, re, subprocess, sys, time, random, json, socket

PKG = "dev.kuntal.cockpit"
ACT = PKG + "/.MainActivity"
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5556")
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ART = os.path.join(ROOT, "artifacts")
LOGS = os.path.join(ART, "logs")
os.makedirs(LOGS, exist_ok=True)
SDK = os.environ.get("ANDROID_SDK_ROOT", "/opt/homebrew/share/android-commandlinetools")
ADB = os.path.join(SDK, "platform-tools", "adb")
PY = os.path.join(ROOT, "tools", ".venv", "bin", "python")
ELM_PORT = int(os.environ.get("ELM_PORT", "35020"))
assert SERIAL == "emulator-5556" or os.environ.get("CHAOS_ANY_SERIAL") == "1", "chaos scripts only run on the monkey emulator (emulator-5556)"


def adb(*args, timeout=60, check=False, text=True):
    r = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=text, timeout=timeout)
    if check and r.returncode: raise RuntimeError(r.stderr)
    return r.stdout


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def pid():
    out = sh(f"pidof {PKG}").strip()
    return int(out.split()[0]) if out else None


def alive():
    return pid() is not None


def start(screen=None, page=None, sets=None, reset=False, perf=False, snapshot=False, extra=""):
    a = f"am start -n {ACT}"
    if screen: a += f" --es screen {screen}"
    if page: a += f" --es page {page}"
    if sets: a += f" --es set '{sets}'"
    if reset: a += " --ez reset true"
    if perf: a += " --ez perf true"
    if snapshot: a += " --ez snapshot true"
    return sh(a + " " + extra)


def cold(screen=None, **kw):
    """Force-stop then launch: the only way to make --es screen/page/set take effect (MainActivity ignores onNewIntent)."""
    sh(f"am force-stop {PKG}")
    time.sleep(.4)
    return start(screen, **kw)


def key(code):
    sh(f"input keyevent {code}")


def wm_size():
    m = re.search(r"(?:Override|Physical) size: (\d+)x(\d+)", sh("wm size"))
    return int(m.group(1)), int(m.group(2))


_scale_cache = {}


def design_to_px(x, y):
    w, h = wm_size()
    s = min(w / 1280, h / 800)
    return int((w - 1280 * s) / 2 + x * s), int((h - 800 * s) / 2 + y * s)


def tap(x, y):
    sh(f"input tap {x} {y}")


def tap_design(x, y):
    px, py = design_to_px(x, y)
    sh(f"input tap {px} {py}")


DOCK = {"Home": 0, "Drive": 1, "Car": 2, "Media": 3, "Phone": 4, "Maps": 5, "Apps": 6, "Settings": 7}


def dock_xy(name):
    return 1224, 109 + 91 * DOCK[name]


def dock(name):
    tap_design(*dock_xy(name))


def settings_cat(i):
    tap_design(194, 124 + 76 * i)


def logcat_clear():
    adb("logcat", "-c")
    adb("logcat", "-b", "crash", "-c")


def logcat_dump(path=None):
    out = adb("logcat", "-d", "-v", "threadtime", timeout=120)
    if path:
        with open(path, "w") as f: f.write(out)
    return out


BAD = re.compile(r"FATAL EXCEPTION|ANR in|StrictMode|Fatal signal|SIGSEGV|OutOfMemoryError|am_crash|am_anr|Application Not Responding|Process: dev\.kuntal\.cockpit.*died|java\.lang\.\w*(Exception|Error)", re.I)


def bad_lines(text, ctx=0):
    out = []
    lines = text.splitlines()
    for i, l in enumerate(lines):
        if (PKG in l or "AndroidRuntime" in l or "StrictMode" in l or "ActivityManager" in l or "DEBUG" in l) and BAD.search(l):
            out.append("\n".join(lines[max(0, i - ctx): i + ctx + 1 + 12]))
    return out


def fatal_in_app(text):
    """True when the log holds a fatal exception / ANR / native crash of OUR process."""
    for m in re.finditer(r"FATAL EXCEPTION[^\n]*\n[^\n]*Process: ([\w.]+)", text):
        if m.group(1) == PKG: return True
    if re.search(r"ANR in " + re.escape(PKG), text): return True
    if re.search(r"Fatal signal .*" + re.escape(PKG), text): return True
    return False


def proc_stats():
    """(rss_kb, pss_kb, threads, fds, java_heap_kb, native_heap_kb) of the app, or None."""
    p = pid()
    if not p: return None
    st = sh(f"cat /proc/{p}/status")
    thr = int(re.search(r"Threads:\s+(\d+)", st).group(1)) if "Threads" in st else -1
    rss = int(re.search(r"VmRSS:\s+(\d+)", st).group(1)) if "VmRSS" in st else -1
    fds = len(sh(f"run-as {PKG} ls /proc/{p}/fd 2>/dev/null").split()) if True else -1
    if fds == 0:
        fds = len(sh(f"ls /proc/{p}/fd").split())
    mi = sh(f"dumpsys meminfo {PKG}")
    def g(label):
        m = re.search(label + r"\s+(\d+)", mi)
        return int(m.group(1)) if m else -1
    return dict(rss=rss, pss=g(r"TOTAL PSS:"), threads=thr, fds=fds, java=g(r"Java Heap:"), native=g(r"Native Heap:"))


def settings_xml():
    return sh(f"run-as {PKG} cat shared_prefs/cockpit_settings.xml")


def screenshot(name):
    path = os.path.join(ART, name + ".png")
    with open(path, "wb") as f:
        f.write(subprocess.run([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True, timeout=30).stdout)
    return path


def focus():
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^\s}]+)", sh("dumpsys window | grep mCurrentFocus"))
    return m.group(1) if m else ""


def reset_device():
    """Undo everything the chaos scripts may have changed. Also makes Cockpit the default HOME app, like on the real unit (without
    a default, HOME shows the AOSP ResolverActivity chooser, which itself crashes in system:ui on this emulator image)."""
    sh(f"cmd package set-home-activity {ACT}")
    sh("wm size reset; wm density reset; settings put system font_scale 1.0; settings put system time_12_24 24")
    sh("settings put global auto_time 1; settings put global auto_time_zone 1")
    sh("settings put global airplane_mode_on 0; svc wifi enable; svc data enable")
    sh("cmd locale set-app-locales 2>/dev/null; setprop persist.sys.locale en-US; setprop persist.sys.language en; setprop persist.sys.country US")


class Result:
    """Collects PASS/FAIL per scenario; run-all prints a table and exits non-zero on any FAIL."""
    rows = []

    @classmethod
    def add(cls, name, ok, detail=""):
        cls.rows.append((name, ok, detail))
        print(f"[{'PASS' if ok else 'FAIL'}] {name}  {detail}", flush=True)
        return ok


def free_port_listening(port):
    s = socket.socket()
    s.settimeout(.3)
    try:
        s.connect(("127.0.0.1", port)); return True
    except OSError:
        return False
    finally:
        s.close()


# ---- UI reading (uiautomator sees Compose text as TextViews) ---------------------------------------------------------------
def ui_texts(retries=2):
    """[(text, (l,t,r,b))] of the visible TextViews, or None if the dump failed (UI hung / no window)."""
    for _ in range(retries):
        out = sh("uiautomator dump /sdcard/chaos_ui.xml 2>&1", timeout=40)
        if "dumped" not in out: time.sleep(.5); continue
        xml = sh("cat /sdcard/chaos_ui.xml", timeout=30)
        res = []
        for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
            if m.group(1): res.append((m.group(1), tuple(map(int, m.groups()[1:]))))
        return res
    return None


LINK_TEXTS = ["Connected", "Simulated drive", "Connecting\u2026", "Connection lost, retrying", "No data source selected"]


def link_text():
    """The status line on Settings > Connections (the page must be open), or None."""
    t = ui_texts()
    if t is None: return None
    for s, _ in t:
        if s in LINK_TEXTS: return s
    return "?"


def cpu_pct(secs=3.0):
    """CPU use of the app over `secs`, in percent of one core (from /proc/<pid>/stat ticks)."""
    p = pid()
    if not p: return -1.0
    def ticks():
        f = sh(f"cat /proc/{p}/stat").split()
        return int(f[13]) + int(f[14])
    a = ticks(); t0 = time.time(); time.sleep(secs); b = ticks()
    return 100.0 * (b - a) / 100.0 / (time.time() - t0)


def heap_kb():
    m = re.search(r"Java Heap:\s+(\d+)", sh(f"dumpsys meminfo {PKG}"))
    return int(m.group(1)) if m else -1


def start_hostile(mode, *extra, port=None):
    port = port or ELM_PORT
    p = subprocess.Popen([PY, os.path.join(ROOT, "tools", "chaos", "hostile_elm.py"), "--port", str(port), "--mode", mode, "--quiet", *extra],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(50):
        if free_port_listening(port): return p
        time.sleep(.1)
    raise RuntimeError("hostile_elm did not start")


def stop_proc(p):
    if p and p.poll() is None:
        p.terminate()
        try: p.wait(5)
        except Exception: p.kill()


def point_app_at_hostile(screen="settings", page="connections"):
    cold(screen, page=page, sets=f"source=ObdTcp,tcpHost=10.0.2.2,tcpPort={ELM_PORT}")
    time.sleep(3)


def screen_stats(path):
    """(mean luminance 0..255, share of pixels brighter than 40) of a screenshot, via the venv's Pillow."""
    code = ("import sys;from PIL import Image;im=Image.open(sys.argv[1]).convert('L');px=list(im.getdata());"
            "print(sum(px)/len(px), sum(1 for p in px if p>40)/len(px))")
    out = subprocess.run([PY, "-c", code, path], capture_output=True, text=True).stdout.split()
    return (float(out[0]), float(out[1])) if len(out) == 2 else (None, None)


def looks_blank(path):
    """True for a black / empty frame (a healthy Cockpit screen has glowing cards and text: more than 1 % bright pixels)."""
    mean, bright = screen_stats(path)
    return mean is not None and bright < 0.01


def set_locale(tag):
    """Switch the system locale live (like Settings > Languages), no reboot: a 30-line dex run through app_process as the shell user
    (tools/chaos/SetLocale.java, built on first use with javac + d8).  Also flips the layout direction for ar / he / fa / ur."""
    dex = os.path.join(ART, "setlocale", "classes.dex")
    if not os.path.exists(dex):
        os.makedirs(os.path.dirname(dex), exist_ok=True)
        env = dict(os.environ, JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")
        jar = os.path.join(SDK, "platforms", "android-35", "android.jar")
        subprocess.run([env["JAVA_HOME"] + "/bin/javac", "-source", "8", "-target", "8", "-cp", jar, "-d", os.path.dirname(dex), os.path.join(ROOT, "tools", "chaos", "SetLocale.java")],
                       capture_output=True, env=env, check=True)
        subprocess.run([os.path.join(SDK, "build-tools", "35.0.0", "d8"), "--min-api", "28", "--lib", jar, "--output", os.path.dirname(dex),
                        os.path.join(os.path.dirname(dex), "SetLocale.class")], capture_output=True, env=env, check=True)
    adb("push", dex, "/data/local/tmp/setlocale.dex")
    out = sh(f"CLASSPATH=/data/local/tmp/setlocale.dex app_process /system/bin SetLocale {tag}")
    time.sleep(2.5)
    return "locale ->" in out
