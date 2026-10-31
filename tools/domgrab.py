#!/usr/bin/env python3
"""Read a headless-Chrome --dump-dom on stdin and print the text of <pre id=ARG>. Usage: ... | domgrab.py qa-out"""
import sys, re, html
pid = sys.argv[1]
m = re.search(r'<pre id="' + re.escape(pid) + r'">(.*?)</pre>', sys.stdin.read(), re.S)
print(html.unescape(m.group(1)) if m else "NO-" + pid.upper())
