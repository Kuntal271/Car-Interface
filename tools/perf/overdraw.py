#!/usr/bin/env python3
"""Overdraw per screen: a GPU fill-rate proxy that does not need a GPU profiler.

  export ANDROID_SERIAL=emulator-5554
  tools/.venv/bin/python tools/perf/overdraw.py --screen home,drive,apps,settings [--extra "--ez flatten true"]

For each screen (snapshot mode: frozen data, deterministic) it takes a normal screenshot and one with the platform overlay
(`debug.hwui.overdraw show`: a pixel drawn 2x is tinted blue, 3x green, 4x light red, 5x or more dark red; a pixel drawn once is untouched),
finds the tint of every pixel by comparing the two, and prints the share of the screen per level and the mean number of draws per pixel
(= fill-rate cost relative to a perfectly opaque single pass). Needs Pillow (tools/.venv). Always resets the property.
"""
import argparse, io, os, subprocess, sys, time
from PIL import Image

PKG = "dev.kuntal.cockpit"
TINTS = [(0x00, 0x00, 0xFF, 0x2F), (0x00, 0xFF, 0x00, 0x2F), (0xFF, 0x00, 0x00, 0x3F), (0xFF, 0x00, 0x00, 0x7F)]      # AOSP overdraw colours for 2, 3, 4, 5+ draws


def adb(*a, binary=False):
    ser = os.environ.get("ANDROID_SERIAL")
    if not ser:
        sys.exit("ANDROID_SERIAL is not set")
    r = subprocess.run(["adb", "-s", ser, *a], capture_output=True, timeout=180)
    return r.stdout if binary else r.stdout.decode(errors="replace")


def shot(screen, extra, prop):
    adb("shell", "setprop", "debug.hwui.overdraw", prop or "false")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--es", "screen", screen, "--ez", "snapshot", "true", *extra.split())
    time.sleep(5)
    return Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", binary=True))).convert("RGB")


def classify(n, o):
    counts = [0] * 5               # draws: 1 (or untouched), 2, 3, 4, 5+
    for pn, po in zip(n.getdata(), o.getdata()):
        if pn == po:
            counts[0] += 1; continue
        best, bi = None, 0
        for i, (r, g, b, a) in enumerate(TINTS):
            al = a / 255.0
            pred = (pn[0] * (1 - al) + r * al, pn[1] * (1 - al) + g * al, pn[2] * (1 - al) + b * al)
            err = abs(pred[0] - po[0]) + abs(pred[1] - po[1]) + abs(pred[2] - po[2])
            if best is None or err < best:
                best, bi = err, i
        counts[bi + 1] += 1
    return counts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--screen", default="home,drive,apps,settings")
    ap.add_argument("--extra", default="")
    a = ap.parse_args()
    try:
        for sc in a.screen.split(","):
            n = shot(sc, a.extra, ""); o = shot(sc, a.extra, "show")
            c = classify(n, o); tot = sum(c)
            mean = sum((i + 1) * x for i, x in enumerate(c)) / tot
            print(f"{sc:<9} " + "  ".join(f"{k}: {100 * x / tot:5.1f} %" for k, x in zip(["1x", "2x", "3x", "4x", "5x+"], c)) + f"   mean draws per pixel {mean:.2f}")
            o.save(f"artifacts/overdraw-{sc}.png")
    finally:
        adb("shell", "setprop", "debug.hwui.overdraw", "false")


if __name__ == "__main__":
    main()
