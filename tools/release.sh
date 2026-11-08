#!/usr/bin/env bash
# Build the release APK (R8-minified), run the unit tests first, and print what you need to install it: path, size, checksum, version, signer.
#   tools/release.sh            # tests + release build
#   tools/release.sh --offline  # extra arguments go to Gradle
#
# Signing. The release key lives OUTSIDE the repo and is passed through the environment (nothing is stored or printed):
#   COCKPIT_KEYSTORE=/path/to/release.jks  COCKPIT_KEY_ALIAS=cockpit  COCKPIT_KEYSTORE_PASSWORD=...  [COCKPIT_KEY_PASSWORD=...]
# Create one with tools/security/make-release-keystore.sh. Without COCKPIT_KEYSTORE the APK is signed with this machine's debug key
# (fine for a first try, but every debug-signed app on this Mac can then replace it): set COCKPIT_REQUIRE_RELEASE_KEY=1 to refuse that.
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/env.sh
if [ -n "${COCKPIT_KEYSTORE:-}" ]; then
  [ -f "$COCKPIT_KEYSTORE" ] || { echo "COCKPIT_KEYSTORE does not exist: $COCKPIT_KEYSTORE" >&2; exit 2; }
  : "${COCKPIT_KEYSTORE_PASSWORD:?set COCKPIT_KEYSTORE_PASSWORD}" "${COCKPIT_KEY_ALIAS:?set COCKPIT_KEY_ALIAS}"
else
  echo "WARNING: COCKPIT_KEYSTORE is not set: signing with this machine's DEBUG key (see the header of tools/release.sh)." >&2
  [ "${COCKPIT_REQUIRE_RELEASE_KEY:-0}" = "1" ] && { echo "COCKPIT_REQUIRE_RELEASE_KEY=1: refusing." >&2; exit 2; }
fi
./gradlew :app:testDebugUnitTest :vehicle:test :app:assembleRelease "$@"
APK=app/build/outputs/apk/release/app-release.apk
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
echo
echo "APK      $PWD/$APK"
echo "size     $(du -h "$APK" | cut -f1)"
echo "sha256   $(shasum -a 256 "$APK" | cut -d' ' -f1)"
"$BT/aapt2" dump badging "$APK" 2>/dev/null | grep -E "^package:|^sdkVersion|^targetSdkVersion" | sed 's/^/badging  /' || true
"$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | grep -E "Signer #1 certificate (DN|SHA-256)" | sed 's/^/signer   /' || true
