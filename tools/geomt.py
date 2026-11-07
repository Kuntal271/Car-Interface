#!/usr/bin/env python3
"""Text-only view of a geom dump: python geomt.py <file>"""
import sys, json
for l in open(sys.argv[1]):
    o = json.loads(l); x, y, w, h = o["r"]
    if "text" not in o: continue
    if y < 56 and h < 60: continue
    if x >= 1168 and y >= 56 and w < 110 and len(sys.argv) < 3: continue
    print(f'[{x:g},{y:g} {w:g}x{h:g}] "{o["text"]}" {o["fs"]} w{o["fw"]} {o["ff"][:8]} {o["col"].replace("rgb(","").replace(")","").replace(" ","")}')
