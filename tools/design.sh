#!/usr/bin/env bash
# Serve the design site locally (loopback only) and/or screenshot screens with headless Chrome.
#   tools/design.sh serve            -> http://127.0.0.1:8750/
#   tools/design.sh shot home drive  -> docs/design/site/shots/<name>.png (1280x800)
#   tools/design.sh shotall
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)/docs/design/site"
PORT=8750
SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
case "${1:-}" in
  serve) cd "$ROOT" && exec python3 -m http.server "$PORT" --bind 127.0.0.1 ;;
  shot)  shift; for n in "$@"; do
           "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=1280,800 --virtual-time-budget=4000 \
             --screenshot="$ROOT/shots/$n.png" "http://127.0.0.1:$PORT/screens/$n.html?raw=1&${Q:-noanim=1}" >/dev/null 2>&1 && echo "shot $n"
         done ;;
  shotq) shift; n="$1"; out="$2"; q="$3"   # tools/design.sh shotq boot boot-1.6 "bt=1.6"
         "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=1280,800 --virtual-time-budget=4000 \
           --screenshot="$ROOT/shots/$out.png" "http://127.0.0.1:$PORT/screens/$n.html?raw=1&$q" >/dev/null 2>&1 && echo "shot $out" ;;
  shotall) cd "$ROOT/screens" && for f in *.html; do "$SELF" shot "${f%.html}"; done ;;
  qa)    shift; for n in "$@"; do
           echo "== $n"; "$CHROME" --headless=new --disable-gpu --window-size=1280,800 --virtual-time-budget=4000 \
             --dump-dom "http://127.0.0.1:$PORT/screens/$n.html?raw=1&qa=1&noanim=1${Q:+&$Q}" 2>/dev/null \
             | python3 -c "import sys,re,html; s=sys.stdin.read(); m=re.search(r'<pre id=\"qa-out\">(.*?)</pre>',s,re.S); print(html.unescape(m.group(1)) if m else 'NO-QA-OUTPUT (script error?)')"
         done ;;
  bench) shift; for n in "$@"; do echo "== $n (JS update cost per frame, not raster)"; "$CHROME" --headless=new --disable-gpu --window-size=1280,800 --virtual-time-budget=6000 \
           --dump-dom "http://127.0.0.1:$PORT/screens/$n.html?raw=1&bench=1&noanim=1" 2>/dev/null | python3 -c "import sys,re; m=re.search(r'<pre id=\"bench-out\">(.*?)</pre>',sys.stdin.read(),re.S); print(m.group(1) if m else 'no bench output')"; done ;;
  probe) shift; for n in "$@"; do echo "== $n"; "$CHROME" --headless=new --disable-gpu --window-size=1280,800 --virtual-time-budget=4000 \
           --dump-dom "http://127.0.0.1:$PORT/screens/$n.html?raw=1&probe=1&noanim=1${Q:+&$Q}" 2>/dev/null | python3 "$(dirname "$SELF")/domgrab.py" probe-out; done ;;
  probeall) cd "$ROOT/screens" && for f in *.html; do n="${f%.html}"
              if [ "$n" = overlays ]; then for i in 1 2 3 4 5 6 7 8; do Q="one=$i" "$SELF" probe overlays | sed "s/^== overlays/== overlays#$i/"; done; else "$SELF" probe "$n"; fi; done ;;
  rvshots) mkdir -p "$ROOT/shots/_rv"; cd "$ROOT/screens" && for f in *.html; do n="${f%.html}"
              if [ "$n" = overlays ]; then for i in 1 2 3 4 5 6 7 8; do "$SELF" shotq overlays "_rv/overlays-$i" "one=$i&noanim=1"; done; else "$SELF" shotq "$n" "_rv/$n" "noanim=1"; fi; done ;;
  qaall) cd "$ROOT/screens" && for f in *.html; do "$SELF" qa "${f%.html}"; done ;;
  *) echo "usage: $0 serve | shot | shotq | shotall | qa | qaall | probe | probeall | bench"; exit 2 ;;
esac
