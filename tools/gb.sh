#!/usr/bin/env bash
# Memory-bounded Gradle for several agents on one machine:
#   - at most $GB_SLOTS builds (default 2) run at once, machine-wide (waits for a free slot)
#   - each build is capped (1.5 GB Gradle, 1 GB Kotlin daemon, 2 workers)
#   - offline by default (everything needed is cached); GB_ONLINE=1 allows downloading new dependencies
# Usage: tools/gb.sh :app:testDebugUnitTest :vehicle:test   (same arguments as ./gradlew)
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/env.sh
SLOTS=${GB_SLOTS:-2}
LOCKROOT=${GB_LOCKROOT:-$HOME/.hermes/cache/gb-locks}
mkdir -p "$LOCKROOT"
SLOT=""
acquire() {
  while true; do
    for i in $(seq 1 "$SLOTS"); do
      d="$LOCKROOT/slot$i"
      if mkdir "$d" 2>/dev/null; then echo $$ > "$d/pid"; SLOT="$d"; return; fi
      pid=$(cat "$d/pid" 2>/dev/null || echo 0)
      if ! kill -0 "$pid" 2>/dev/null; then rm -rf "$d"; fi          # holder died: free the slot
    done
    sleep 3
  done
}
acquire
trap 'rm -rf "$SLOT"' EXIT INT TERM
OFFLINE="--offline"; [ "${GB_ONLINE:-0}" = "1" ] && OFFLINE=""
./gradlew $OFFLINE --max-workers=2 \
  "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" "-Dkotlin.daemon.jvm.options=-Xmx1g" "$@"
