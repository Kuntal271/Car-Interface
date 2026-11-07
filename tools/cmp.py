#!/usr/bin/env python3
"""Compare an app screenshot with the design render. usage: cmp.py <screen>  (uses tools/.venv for Pillow)
Prints % of visibly different pixels per region and writes side-by-side crops to docs/shots/_cmp-<screen>-<region>.png."""
import sys, pathlib
from PIL import Image, ImageChops
root = pathlib.Path(__file__).resolve().parent.parent
screen = sys.argv[1]
ref = Image.open(root / f"docs/design/site/shots/{screen}.png").convert("RGB")
mine = Image.open(root / f"docs/shots/{screen}-snap.png").convert("RGB")
if ref.size != mine.size: ref = ref.resize(mine.size)
bw = ImageChops.difference(ref, mine).convert("L").point(lambda v: 255 if v > 24 else 0)
def pct(b):
    c = bw.crop(b); return 100.0 * c.histogram()[255] / (c.size[0] * c.size[1])
REG = {
  "drive": {"status": (0,0,1280,56), "shift": (0,60,1168,96), "tach": (24,96,624,696), "boost": (652,100,1144,362),
            "tiles": (652,374,1144,686), "trip": (24,698,1144,776), "dock": (1168,56,1280,800)},
}.get(screen, {"all": (0,0,*mine.size)})
print(f"{screen}: overall {pct((0,0,*mine.size)):.2f}% visibly different pixels")
for k, b in REG.items():
    print(f"  {k:8s} {pct(b):5.2f}%")
    a, c = ref.crop(b), mine.crop(b); w, h = a.size
    o = Image.new("RGB", (w*2+12, h), (90,90,90)); o.paste(a, (0,0)); o.paste(c, (w+12,0))
    o.save(root / f"docs/shots/_cmp-{screen}-{k}.png")
