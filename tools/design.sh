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
             --screenshot="$ROOT/shots/$n.png" "http://127.0.0.1:$PORT/screens/$n.html?raw=1" >/dev/null 2>&1 && echo "shot $n"
         done ;;
  shotall) cd "$ROOT/screens" && for f in *.html; do "$SELF" shot "${f%.html}"; done ;;
  qa)    shift; for n in "$@"; do
           echo "== $n"; "$CHROME" --headless=new --disable-gpu --window-size=1280,800 --virtual-time-budget=4000 \
             --dump-dom "http://127.0.0.1:$PORT/screens/$n.html?raw=1&qa=1" 2>/dev/null \
             | python3 -c "import sys,re,html; s=sys.stdin.read(); m=re.search(r'<pre id=\"qa-out\">(.*?)</pre>',s,re.S); print(html.unescape(m.group(1)) if m else 'NO-QA-OUTPUT (script error?)')"
         done ;;
  qaall) cd "$ROOT/screens" && for f in *.html; do "$SELF" qa "${f%.html}"; done ;;
  *) echo "usage: $0 serve | shot <name...> | shotall"; exit 2 ;;
esac
