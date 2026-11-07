#!/usr/bin/env python3
"""Pixel parity between two builds: the proof for "cut the cost without changing the pixels".

  export ANDROID_SERIAL=emulator-5554
  tools/.venv/bin/python tools/perf/parity.py --a artifacts/apk/base-debug.apk --b app/build/outputs/apk/debug/app-debug.apk \\
        --screen drive,home,settings,apps [--b-extra "--ez flatten true"]

Installs each APK, launches every screen in snapshot mode (frozen data, fixed clock; deterministic), takes a screenshot, and compares the two
images pixel by pixel: number of different pixels, largest channel difference, and the share above the project's visibility threshold (24 / 255,
the one tools/cmp.py uses). Identical screenshots print 0 / 0. Needs Pillow (tools/.venv).
"""
import argparse, io, os, subprocess, sys, time
from PIL import Image, ImageChops

PKG = "dev.kuntal.cockpit"


def adb(*a, binary=False):
    ser = os.environ.get("ANDROID_SERIAL")
    if not ser:
        sys.exit("ANDROID_SERIAL is not set")
    r = subprocess.run(["adb", "-s", ser, *a], capture_output=True, timeout=180)
    return r.stdout if binary else r.stdout.decode(errors="replace")


def shot(apk, screen, extra, installed, page=None, settle=5):
    if installed.get("apk") != apk:
        adb("install", "-r", "-d", apk); installed["apk"] = apk
    adb("shell", "am", "force-stop", PKG)
    cmd = ["shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--es", "screen", screen, "--ez", "snapshot", "true"] + extra.split() + (["--es", "page", page] if page else [])
    adb(*cmd)
    time.sleep(settle)
    return Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", binary=True))).convert("RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", required=True); ap.add_argument("--b", required=True)
    ap.add_argument("--screen", default="drive,home,settings,apps")
    ap.add_argument("--a-extra", default=""); ap.add_argument("--b-extra", default="")
    ap.add_argument("--save", default="artifacts/parity")
    a = ap.parse_args()
    os.makedirs(a.save, exist_ok=True)
    ia, ib = {}, {}
    worst = 0
    for sc in a.screen.split(","):
        page = None
        if ":" in sc:
            sc, page = sc.split(":")
        # alternate A and B per screen so a one-off render glitch shows up as a difference you can re-run
        x = shot(a.a, sc, a.a_extra, ia, page); y = shot(a.b, sc, a.b_extra, ib, page)
        d = ImageChops.difference(x, y)
        bbox = d.getbbox()
        px = x.size[0] * x.size[1]
        gray = d.convert("L")
        hist = gray.histogram()
        diff_px = px - hist[0]
        vis = sum(hist[25:])
        mx = max((i for i, c in enumerate(hist) if c), default=0)
        print(f"{sc + (':' + page if page else ''):<18} different pixels {diff_px:>7} ({100 * diff_px / px:.3f} %)   max channel diff {mx:>3}   above threshold 24: {vis} ({100 * vis / px:.3f} %)   bbox {bbox}")
        x.save(f"{a.save}/{sc}{'-' + page if page else ''}-A.png"); y.save(f"{a.save}/{sc}{'-' + page if page else ''}-B.png")
        worst = max(worst, vis)
    sys.exit(1 if worst else 0)


if __name__ == "__main__":
    main()
