#!/usr/bin/env python3
"""Static checks for the design site: every local href/src resolves, no external URLs, screen registry matches files and thumbnails."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent / "docs/design/site"
bad = []; ext = []; n = 0
for f in list(root.rglob("*.html")) + list(root.rglob("*.css")) + list(root.rglob("*.js")):
    if "shots/_" in str(f): continue
    s = f.read_text()
    for m in re.finditer(r'(?:href|src)="([^"#?]+)', s):
        u = m.group(1)
        if u.startswith(("http://", "https://", "//", "data:", "javascript:")) or "' +" in u or "'" in u: 
            if u.startswith(("http", "//")): ext.append((f.name, u))
            continue
        n += 1
        if not (f.parent / u).resolve().exists(): bad.append((str(f.relative_to(root)), u))
    for m in re.finditer(r'url\(([^)]+)\)', s):
        u = m.group(1).strip("'\"")
        if u.startswith(("data:", "http", "#")): continue
        n += 1
        if not (f.parent / u).resolve().exists(): bad.append((str(f.relative_to(root)), u))
ids = re.findall(r"id: '([^']+)'", (root / "assets/screens.js").read_text())
for i in ids:
    for p in (f"screens/{i}.html", f"shots/{i}.png" if i != "overlays" else "shots/overlays-thumb.png"):
        n += 1
        if not (root / p).exists(): bad.append(("screens.js", p))
files = {p.stem for p in (root / "screens").glob("*.html")}
extra = files - set(ids); missing = set(ids) - files
print(f"checked {n} references, {len(ids)} registry entries, {len(files)} screen files")
print("BROKEN:", bad or "none"); print("EXTERNAL:", ext or "none"); print("unregistered screens:", sorted(extra) or "none", "| registry without file:", sorted(missing) or "none")
sys.exit(1 if bad or ext or missing else 0)
