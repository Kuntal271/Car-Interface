# ADR-0011: Developer hooks live in the debug source set; release builds have inert stubs

Status: Proposed (fixes ARCH-02)

## Context
`MainActivity` reads intent extras `snapshot`, `perf`, `set`, `reset` in every build. The activity is exported (it must be, to be HOME), so any app or
`adb shell am start` can change the data source and host (`set`), restore defaults (`reset`), or freeze the UI on the design's mock numbers (`snapshot`), which
presents invented values as live data. `DebugHooks` is referenced from four production files (`Shell.kt`, `CockpitApp.kt`, `CockpitViewModel.kt`, `MainActivity.kt`) and its strings are in the release dex.

## Decision
`src/debug/.../DebugHooks.kt` keeps today's behaviour; `src/release/.../DebugHooks.kt` is an `object` with the same members as `const`/inert values so the call sites
compile and R8 removes them. The intent-extra handling moves into a `DevIntentHandler` that exists only in `src/debug` (release gets a no-op with the same name). The status bar
and Home read an injected `Environment` (clock, volume, demo content) instead of `DebugHooks`. `tools/snap.sh`/`tools/shot.sh` target the debug APK explicitly.

## Consequences
+ Release has no remote way to alter settings or show mock numbers; fewer bytes; a clearer production code path.
- Two copies of a tiny file; scripts must install the debug APK. `tools/release.sh` output no longer supports `snap.sh` (documented).
