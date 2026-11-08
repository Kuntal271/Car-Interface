#!/usr/bin/env bash
# tools/ship.sh <screen>...  : build + install + snapshot each screen + compare (prints one line per screen)
cd "$(dirname "$0")/.."; source tools/env.sh
tools/b.sh || exit 1
for s in "$@"; do
  SNAP_WAIT=${SNAP_WAIT:-6} tools/snap.sh "$s" >/dev/null
  tools/.venv/bin/python tools/cmp.py "$s" 2>/dev/null | head -1
  tools/.venv/bin/python tools/sbs.py "$s" 0 0 1280 800 >/dev/null
done
