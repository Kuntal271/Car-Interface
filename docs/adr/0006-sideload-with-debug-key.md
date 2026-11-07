# ADR-0006: Sideload; release builds are signed with the local debug key

Status: Accepted for now; revisit conditions below (docs/INSTALL.md, 2026-11-07)

## Context
Personal app on the owner's own unit, installed from a USB stick or adb. No store, no updates from a server.

## Decision
`release` uses `signingConfigs.getByName("debug")`, minified with R8 and resource shrinking. `targetSdk = 30` on purpose (the unit is Android 11),
so Play's expired-target-SDK lint rule is disabled.

## Consequences
+ Zero key management.
- The debug keystore is per machine (`~/.android/debug.keystore`). An APK built on another machine or in CI has a different signer and Android
  refuses to update over it (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): uninstalling loses the app's settings. Revisit: when the first APK that must be updated
  in place is built anywhere except the owner's Mac, create one release keystore (kept outside git), reference it through `keystore.properties`, and record the
  signer SHA-256 in docs/INSTALL.md. `tools/release.sh` already prints the signer.
- Release builds currently still contain the dev hooks (ARCH-02, ADR-0011).
- Not for any store: the Ford oval and the debug key both rule that out.
