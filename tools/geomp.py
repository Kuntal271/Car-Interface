#!/usr/bin/env python3
"""Compact print of a geom.py dump: python geomp.py <file> [minY] [maxX]  (skips status bar / dock)"""
import sys, json
def col(s):
    return s.replace("rgb(","").replace("rgba(","").replace(")","").replace(" ","")
for l in open(sys.argv[1]):
    o = json.loads(l); x, y, w, h = o["r"]
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
