#!/usr/bin/env python3
"""Compact print of a geom.py dump: python geomp.py <file> [minY] [maxX]  (skips status bar / dock)"""
import sys, json
def col(s):
    return s.replace("rgb(","").replace("rgba(","").replace(")","").replace(" ","")
rows = [json.loads(l) for l in open(sys.argv[1])]
out = []; i = 0
while i < len(rows):                     # collapse runs of <i> segment cells into one SEG line
    o = rows[i]
    if o["t"] == "i" and "rad" in o:
        j = i
        while j + 1 < len(rows) and rows[j + 1]["t"] == "i" and rows[j + 1]["r"][1] == o["r"][1]: j += 1
        run = rows[i:j + 1]; lit = sum(1 for r in run if r.get("c") == "l")
        out.append({"seg": True, "n": len(run), "lit": lit, "r": [run[0]["r"][0], run[0]["r"][1], run[-1]["r"][0] + run[-1]["r"][2] - run[0]["r"][0], run[0]["r"][3]], "sw": run[0]["r"][2], "bg": run[0].get("bg", "")})
        i = j + 1; continue
    out.append(o); i += 1
for o in out:
    x, y, w, h = o["r"]
    if o.get("seg"):
        print(f'SEG n={o["n"]} lit={o["lit"]} [{x:g},{y:g} {w:g}x{h:g}] cell={o["sw"]:g}'); continue
    if y < 56 and h < 60 and o["t"] != "svg": continue
    if x >= 1168 and y >= 56 and len(sys.argv) < 3: continue
    t = o["t"]; c = o.get("c","")
    s = f'{t}.{c[:14]} [{x:g},{y:g} {w:g}x{h:g}]'
    if "text" in o: s += f' "{o["text"]}" {o["fs"]} w{o["fw"]} {o["ff"][:14]} {col(o["col"])} ls{o["ls"]} {o["ta"]} lh{o["lh"]}'
    if "bg" in o: s += f' bg={col(o["bg"])}'
    if "bgi" in o and not o["bgi"].startswith("none"): s += f' bgi={o["bgi"][:100]}'
    if "bd" in o: s += f' bd={col(o["bd"])}'
    if "rad" in o: s += f' rad={o["rad"]}'
    if "sh" in o: s += f' sh={o["sh"][:60]}'
    if "fill" in o: s += f' fill={col(o["fill"])} stroke={col(o["stroke"])} sw{o["sw"]} d={o["d"][:60]}'
    if "vb" in o: s += f' vb={o["vb"]}'
    if "op" in o: s += f' op={o["op"]}'
    print(s)
