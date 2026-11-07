#!/usr/bin/env bash
# Build the release APK (R8-minified, signed with the personal debug key: this app is sideloaded, not published),
# run the unit tests first, and print what you need to install it: path, size, checksum, version, signer.
#   tools/release.sh            # tests + release build
#   tools/release.sh --offline  # extra arguments go to Gradle
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/env.sh
./gradlew :app:testDebugUnitTest :vehicle:test :app:assembleRelease "$@"
APK=app/build/outputs/apk/release/app-release.apk
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
echo
echo "APK      $PWD/$APK"
echo "size     $(du -h "$APK" | cut -f1)"
echo "sha256   $(shasum -a 256 "$APK" | cut -d' ' -f1)"
"$BT/aapt2" dump badging "$APK" 2>/dev/null | grep -E "^package:|^sdkVersion|^targetSdkVersion" | sed 's/^/badging  /' || true
"$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | grep -E "Signer #1 certificate (DN|SHA-256)" | sed 's/^/signer   /' || true
