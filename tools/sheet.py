#!/usr/bin/env python3
"""Contact sheet: tools/sheet.py out_name shot1 shot2 shot3 shot4 -> docs/design/site/shots/_out_name.png (2x2 at 640x400)."""
import subprocess, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent / "docs/design/site"
out, names = sys.argv[1], sys.argv[2:]
cells = "".join(f'<div><img src="/shots/{n}.png" width="640" height="400"><span>{n}</span></div>' for n in names)
html = f'<html><body style="margin:0;background:#000;display:grid;grid-template-columns:640px 640px;width:1280px"><style>div{{position:relative}}span{{position:absolute;left:6px;bottom:4px;color:#fff;font:12px monospace;background:#000a}}img{{display:block}}</style>{cells}</body></html>'
(root / "shots" / f"_{out}.html").write_text(html)
chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
rows = (len(names) + 1) // 2
subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--window-size=1280,{400*rows}", "--virtual-time-budget=3000",
                f"--screenshot={root}/shots/_{out}.png", f"http://127.0.0.1:8750/shots/_{out}.html"], capture_output=True)
print(root / "shots" / f"_{out}.png")
