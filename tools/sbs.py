#!/usr/bin/env python3
"""Side by side crop (design | app) plus the diff mask: python sbs.py <screen> x1 y1 x2 y2 [out]  -> docs/shots/_sbs-<screen>.png"""
import sys, pathlib
from PIL import Image, ImageChops
root = pathlib.Path(__file__).resolve().parent.parent
s = sys.argv[1]; box = tuple(int(v) for v in sys.argv[2:6]) if len(sys.argv) >= 6 else (0, 0, 1280, 800)
ref = Image.open(root / f"docs/design/site/shots/{s}.png").convert("RGB"); mine = Image.open(root / f"docs/shots/{s}-snap.png").convert("RGB")
if ref.size != mine.size: ref = ref.resize(mine.size)
a, b = ref.crop(box), mine.crop(box)
d = ImageChops.difference(a, b).convert("L").point(lambda v: 255 if v > 24 else 0).convert("RGB")
w, h = a.size
o = Image.new("RGB", (w * 3 + 24, h), (90, 90, 90)); o.paste(a, (0, 0)); o.paste(b, (w + 12, 0)); o.paste(d, (2 * w + 24, 0))
out = root / (sys.argv[6] if len(sys.argv) > 6 else f"docs/shots/_sbs-{s}.png"); o.save(out); print(out)
