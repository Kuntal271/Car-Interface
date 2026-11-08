#!/usr/bin/env bash
# tools/b.sh : debug build + install on $ANDROID_SERIAL, prints compile errors only (build log in artifacts/b.log)
cd "$(dirname "$0")/.."; source tools/env.sh
mkdir -p artifacts
tools/gb.sh :app:assembleDebug > artifacts/b.log 2>&1; rc=$?
grep -E "^e: " artifacts/b.log | sed 's#file:///Users/kuntal/ecosport-wt/feature/app/src/main/java/dev/kuntal/cockpit/##' | head -20
if [ $rc -ne 0 ]; then echo "BUILD FAILED"; exit 1; fi
adb install -r app/build/outputs/apk/debug/app-debug.apk | tail -1
