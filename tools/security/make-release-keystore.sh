#!/usr/bin/env bash
# Create the release signing key OUTSIDE the repository (default ~/.cockpit-signing/release.jks, directory mode 700).
# keytool asks for the passwords itself: nothing is typed into, stored in or printed by this script.
#   tools/security/make-release-keystore.sh [path]
# Then, per release:
#   export COCKPIT_KEYSTORE=~/.cockpit-signing/release.jks COCKPIT_KEY_ALIAS=cockpit
#   read -rs COCKPIT_KEYSTORE_PASSWORD; export COCKPIT_KEYSTORE_PASSWORD      # typed, not in shell history
#   tools/release.sh
# BACK THE FILE UP (password manager / encrypted disk): without it an installed Cockpit can never be updated, only reinstalled.
set -euo pipefail
source "$(dirname "$0")/../env.sh"
KS="${1:-$HOME/.cockpit-signing/release.jks}"
[ -e "$KS" ] && { echo "refusing to overwrite $KS" >&2; exit 2; }
mkdir -p "$(dirname "$KS")" && chmod 700 "$(dirname "$KS")"
keytool -genkeypair -v -keystore "$KS" -alias "${COCKPIT_KEY_ALIAS:-cockpit}" -keyalg RSA -keysize 3072 -validity 9125 \
  -dname "CN=Cockpit (personal sideload), O=Kuntal271"
chmod 600 "$KS"
echo "created $KS"
keytool -list -keystore "$KS" -alias "${COCKPIT_KEY_ALIAS:-cockpit}" | tail -2
