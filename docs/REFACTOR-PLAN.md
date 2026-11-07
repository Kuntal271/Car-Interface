# Refactor plan (execute AFTER the four review branches are merged)

Author: architecture review, 2026-11-08. Evidence and reasoning: [docs/reviews/architecture.md](reviews/architecture.md). Decisions: [docs/adr/](adr/).
Target size: one developer, roughly 80-100 hours in total, but **every step stands alone** and leaves the app shippable.

## 0. Ground rules

1. Behaviour-preserving steps are separated from behaviour-changing ones. Only S6-S10 change what the app does (and S2 changes the release build);
   each of those inverts or adds tests first.
2. One step = a few small commits. After each commit the safety net (section 3) is green. No step mixes a move with a logic change.
3. Do not start a step because it is on the list: start it because the previous one is done and recorded in the log table at the end.
4. `ArchitectureRulesTest.KNOWN` and the detekt/lint baselines may only shrink. A step that grows them is wrong, or the finding needs its own ADR.

## 1. Target structure

```
:vehicle   pure Kotlin/JVM (unchanged role; gains the pure vehicle logic that sits in :app today)
  dev.kuntal.cockpit.vehicle          Pid, PidDecoder, ElmParser, ElmClient, PollSchedule, CommandGuard, Transport, StreamExchange,
                                      TcpTransport, VehicleSample, VehicleSource, SimSource, DriveCycle            (stay where they are)
  dev.kuntal.cockpit.vehicle.trip     TripTracker, TripState (+ Saved state for checkpoints)                         <- app/data
  dev.kuntal.cockpit.vehicle.derive   GearEstimator                                                                  <- app/data
  dev.kuntal.cockpit.vehicle.link     Link/LinkState, VehicleUiState (later VehicleSnapshot), VehicleRepository      <- app/data
  dev.kuntal.cockpit.vehicle.canbox   CanboxDecoder, frame types, CanboxSource interface                             (S11, new)

:app       one Android module
  dev.kuntal.cockpit                  CockpitApplication, AppContainer, MainActivity, CockpitApp (root composable), CockpitViewModel
  dev.kuntal.cockpit.core             Clock, DispatcherProvider, Logger                                              (S4, new)
  dev.kuntal.cockpit.settings         Settings, Thresholds, SettingsRepository, SharedPreferencesSettingsRepository  <- SettingsStore, data/Settings
  dev.kuntal.cockpit.format           Fmt, Units (+ unit enums), Range                                               <- data/Format, data/Units, data/HomeContent
  dev.kuntal.cockpit.car              VehicleHub, VehicleService, TripStore, SourceFactory, BluetoothTransport,
                                      EnginePolicy, CanboxSource adapter                                             <- source/*, new in S6-S11
  dev.kuntal.cockpit.apps             AppIndex, RecentApps, AppsRepository, InstalledApps                            <- data/AppIndex, ui/shell/InstalledApps
  dev.kuntal.cockpit.media            NowPlaying, MediaSource, DemoMediaSource, MediaSessionSource                   <- data/HomeContent, (S11)
  dev.kuntal.cockpit.platform         AppLauncher, SystemEnvironment (clock tick, volume)                            <- ui/shell/AppLauncher
  dev.kuntal.cockpit.perf             FrameStats, PerfGovernor, FrameMeter, Perf                                     (unchanged)
  dev.kuntal.cockpit.ui.theme         Palette(s), DesignCanvas, fonts, LocalPalette/LocalSettings                    (split Theme.kt in 3 files)
  dev.kuntal.cockpit.ui.components    Txt, modifiers, SegBar, Tach, Chips, Stepper, smoothed, Icons, BoostBar, Smoother (<- data/BoostBar, data/Smoother)
  dev.kuntal.cockpit.ui.nav           Dest, NavState, Dock                                                           <- ui/shell/Shell.kt
  dev.kuntal.cockpit.ui.shell         StatusBar                                                                      <- ui/shell/Shell.kt
  dev.kuntal.cockpit.ui.home|drive|apps|settings    one package per screen (screen + its page/tile files + UiState)  <- ui/screens/*
app/src/debug   DebugHooks (real), DevIntentHandler, snapshot content, PreviewCanvas
app/src/release DebugHooks (inert const stubs), DevIntentHandler (no-op)
```

Dependency direction: `ui -> (settings | format | apps | media | car | platform) -> :vehicle`; nothing in the right-hand groups imports `ui`; `car`
and `platform` are the only packages that touch Android services. `ArchitectureRulesTest` enforces it. Not a new Gradle module in sight
(ADR-0010).

The app-side package is `car`, not `vehicle`, so it does not share a package name with the `:vehicle` module.

## 2. Old path -> new path

| Old (under `app/src/main/java/dev/kuntal/cockpit/` unless noted) | New | Step |
| --- | --- | --- |
| `data/TripTracker.kt` (TripState, TripTracker) | `:vehicle` `.../vehicle/trip/TripTracker.kt` | S3 |
| `data/GearEstimator.kt` | `:vehicle` `.../vehicle/derive/GearEstimator.kt` | S3 |
| `data/VehicleRepository.kt` (Link, VehicleUiState, VehicleRepository) | `:vehicle` `.../vehicle/link/VehicleRepository.kt` (+ `Link.kt`, `VehicleUiState.kt`) | S3 |
| tests `TripTrackerTest`, `GearEstimatorTest`, `VehicleRepositoryTest`, `VehicleRepositoryFakesTest` | `vehicle/src/test/kotlin/.../vehicle/{trip,derive,link}/` (`testing/FakeVehicleSource.kt` moves with them) | S3 |
| `data/Settings.kt` | `settings/Settings.kt` + `settings/Thresholds.kt` (enums `ThemeId`, `ClockFormat`, `MotionMode`, `SourceKind` stay with Settings) | S3 |
| `SettingsStore.kt` | `settings/SettingsRepository.kt` (interface) + `settings/SharedPreferencesSettingsRepository.kt` | S3, S5 |
| `data/Format.kt` | `format/Fmt.kt` | S3 |
| `data/Units.kt` | `format/Units.kt` | S3 |
| `data/HomeContent.kt` | `Range` -> `format/Range.kt`; `NowPlaying` -> `media/NowPlaying.kt`; `Shortcut`, `HomeContent` -> `ui/home/HomeContent.kt` | S3 |
| `data/AppIndex.kt` (AppEntry, AppIndex, RecentApps) | `apps/AppIndex.kt` | S3 |
| `data/Smoother.kt`, `data/BoostBar.kt` | `ui/components/Smoother.kt`, `ui/components/BoostBar.kt` | S3 |
| `ui/shell/InstalledApps.kt` | `apps/InstalledApps.kt` (+ `AppsRepository.kt` in S13) | S3, S13 |
| `ui/shell/AppLauncher.kt` | `platform/AppLauncher.kt` | S3 |
| `ui/shell/Shell.kt` | `ui/nav/Dest.kt`, `ui/nav/Dock.kt`, `ui/shell/StatusBar.kt` | S3 |
| `source/Sources.kt` | `car/SourceFactory.kt` | S3, S10 |
| `source/BluetoothTransport.kt` (+ `pairedAdapters()`) | `car/BluetoothTransport.kt`, `car/PairedAdapters.kt` | S3 |
| `CockpitViewModel.kt` | stays; shrinks to `hub.state.sample(...)` | S6 |
| `DebugHooks.kt` | `src/debug/.../DebugHooks.kt` and `src/release/.../DebugHooks.kt` | S2 |
| `MainActivity.kt` (applyDevSettings) | `MainActivity.kt` + `src/debug/.../DevIntentHandler.kt` / `src/release/...` no-op | S2 |
| `ui/screens/HomeScreen.kt` | `ui/home/HomeScreen.kt` (+ `HomeTiles.kt`, `HomeLiveLayer.kt` when split) | S3, S15 |
| `ui/screens/DriveScreen.kt` | `ui/drive/DriveScreen.kt` | S3 |
| `ui/screens/AppsScreen.kt` | `ui/apps/AppsScreen.kt` (+ `AppsUiState`) | S3, S13 |
| `ui/screens/SettingsScreen.kt` | `ui/settings/SettingsScreen.kt` + `DisplayPage.kt`, `ConnectionsPage.kt`, `VehiclePage.kt`, `AboutPage.kt` | S3, S15 |
| `ui/theme/Theme.kt` | `ui/theme/Palette.kt`, `ui/theme/DesignCanvas.kt`, `ui/theme/Fonts.kt` | S15 |
| `perf/*`, `ui/components/*` (others), `:vehicle` existing files | unchanged | - |
| `app/src/test/.../data/*Test.kt` (rest) | follow their classes (`settings/`, `format/`, `apps/`, `ui/components/`) | S3 |

Use the IDE's Move refactoring or `git mv` plus a sed over `package`/`import` lines, in the commit groups of S3. `git log --follow` keeps history.

## 3. Safety net (must stay green after every commit)

| Net | Command | Pass condition |
| --- | --- | --- |
| JVM tests | `tools/gb.sh :app:testDebugUnitTest :vehicle:test` | all green; test count never drops (moved tests keep their names) |
| Gates | `tools/gb.sh :app:detektDebug :vehicle:detektMain :app:lintDebug :app:lintRelease` | green; baselines did not grow (compare `git diff --stat config/`) |
| Architecture ratchet | part of the JVM tests (`ArchitectureRulesTest`) | `KNOWN` only shrinks |
| Release | `tools/gb.sh :app:assembleRelease` | builds; APK size within +5 % of S0; no R8 "Missing class" warnings |
| Pixels | `tools/snap.sh drive` + `tools/.venv/bin/python tools/cmp.py drive`; same for `home` | drive <= 2.9 %, home <= 3.4 %, and within 0.05 pp of the exact numbers recorded at S0 |
| Frame cost | `--ez perf true`, Drive and Home, 2 min each on the simulator | app-cpu p50/p99 not worse than S0 by more than 10 %; missed vsync share not worse |
| Chaos | `tools/chaos/run-all.sh` (monkey agent, once it exists) | 0 crashes, 0 ANRs, same pass list as S0 |
| Smoke | `adb shell am start -W -n dev.kuntal.cockpit/.MainActivity` x5 | no crash; `TotalTime` median within +10 % of S0 |

Behaviour-changing steps add their own rows (see S6-S8).

## 4. Steps

Effort is one focused person including tests and the net. Risk: L = a compile error is the worst outcome, M = could change behaviour in the field, H = could lose data or break the car link.

| Step | What | Risk | Effort | Files touched | Extra safety |
| --- | --- | --- | --- | --- | --- |
| **S0** | Merge the four branches (suggested order: security, perf, monkey, feature; run the JVM tests after each). Run `ArchitectureRulesTest`; fix or, with a finding ID, add to `KNOWN`. Run `tools/rebaseline.sh`; inspect `git diff --stat config/` (growth = new findings introduced by the branches, record them). Capture and commit under `docs/reviews/`: `coverage-summary.py` output, the exact `cmp.py` numbers, perf-overlay medians, release APK size + sha256, cold-start `TotalTime`, chaos result. Tag `pre-refactor`. | L | 3 h | config/, docs/reviews/ | everything below is judged against these numbers |
| **S1** | Pin what S2-S5 will touch: a golden test of the `Settings.toMap()` key set and values for a non-default `Settings` (so a migration cannot rename a key silently); a test that lists `Dest` and the dock layout (8 slots fit, 9 do not); put `LauncherBackgroundingTest` and the chaos script into the routine. | L | 2 h | new test files only | JVM tests |
| **S2** | Debug/release source sets (ADR-0011). Move `DebugHooks` and the intent-extra handling out of `main`; release gets inert stubs. Keep call sites compiling (they are removed in S14). Update `tools/snap.sh`/`shot.sh` docs: they need the debug APK. | M | 3 h | `DebugHooks.kt` (moves), `MainActivity.kt`, new `src/debug`, `src/release`, docs/INSTALL.md | **release**: `unzip -o app-release.apk 'classes*.dex' && strings -a classes.dex \| grep -c -x -F -e snapshot -e 'Midnight City' -e 'Fri 30 Oct'` is 0 (it is 3 at S0); on the emulator `adb shell am start -n dev.kuntal.cockpit/.MainActivity --es set "source=ObdTcp,tcpHost=1.2.3.4"` does NOT change the Connections page; **debug**: `snap.sh` pixel numbers identical to S0 |
| **S3** | Mechanical moves (section 2) in commit groups: (a) pure vehicle logic + its tests into `:vehicle`; (b) app packages; (c) file splits of Shell.kt, Settings.kt, SettingsScreen.kt. No logic edits. Update `ArchitectureRulesTest` paths; `tools/rebaseline.sh` afterwards (baseline ids contain file names): per-rule counts must be unchanged, only paths differ. | L | 5 h | ~60 files, mostly package/import lines | compile + all tests after each group; pixel diff identical; `coverage-summary.py` unchanged for the moved classes |
| **S4** | Seams and container (ADR-0007): `CockpitApplication` + `AppContainer`; `Clock`, `DispatcherProvider`, `Logger` interfaces; `CockpitViewModel` built by a factory from the container; `Sources` receives its dependencies; `Dispatchers.IO` in `BluetoothTransport`/`TcpTransport`/composables becomes injected. No behaviour change. | M | 5 h | `AndroidManifest.xml` (`android:name`), `CockpitApplication.kt`, `AppContainer.kt`, `CockpitViewModel.kt`, transports, `CockpitApp.kt` | all tests unchanged and green (incl. `LauncherBackgroundingTest`); cold-start `TotalTime` within +10 %; detekt `InjectDispatcher` baseline entries drop |
| **S5** | `SettingsRepository` + `schemaVersion` + atomic `update` (ADR-0009); fold the recents key into the same prefs file with a one-time copy. | M | 4 h | `settings/*`, `InstalledApps.kt`, `MainActivity.kt` | golden migration tests with old maps; **upgrade-in-place on the emulator**: install the `pre-refactor` APK, change theme/units/source, install the new APK over it, settings and recents survive |
| **S6** | `VehicleHub` (ADR-0008 parts 1, 2, 5). The pipeline moves out of the ViewModel into a process-scoped hub on a confined dispatcher; the ViewModel only samples. Staleness: if no sample for 3 s the published link is not `Connected`. **Invert `LauncherBackgroundingTest`**: polling continues with no UI subscriber, Trip A keeps counting, the first frame back is never a stale Connected, a `sourceKey` change still resets the trip. | M-H | 8 h | `car/VehicleHub.kt`, `CockpitViewModel.kt`, `AppContainer.kt`, tests | JVM hub tests with `FakeVehicleSource`; emulator + fake adapter: open another app (`am start -a android.settings.SETTINGS`), wait 60 s, return: adapter log kept receiving requests, distance increased; 20 background/foreground cycles: `dumpsys meminfo` PSS flat (no leak); perf overlay unchanged or better (pipeline left the main thread); chaos script |
| **S7** | `TripStore`: checkpoint every 5 s while moving and on stop, atomic file; restore on start; explicit "reset Trip A" (a chip on Settings > Vehicle: needs a one-line owner decision, the design has none). | M | 4 h | `car/TripStore.kt`, `vehicle/trip/TripTracker.kt` (saved state), Settings vehicle page | tests: round trip, corrupt file, restore + gap rule, reset; emulator: `adb shell am kill dev.kuntal.cockpit` (process death of a cached app) then relaunch: Trip A restored; `am force-stop` documented as the owner's reset |
| **S8** | Foreground service + engine policy (ADR-0008 part 3). `VehicleService` started by the hub when a live source runs; `EnginePolicy` (pure): rpm 0/none for N min -> slow probe (one command per 30 s), link lost for M min -> stop service. Manifest: `FOREGROUND_SERVICE`, `<service foregroundServiceType="connectedDevice">`, notification channel. | H | 6 h | manifest, `car/VehicleService.kt`, `car/EnginePolicy.kt` | `EnginePolicy` tests with `FakeClock`; emulator: `dumpsys activity services dev.kuntal.cockpit` shows the service; `dumpsys activity processes` shows the process as foreground-service while another app is on top; stopping the fake adapter ends the service; **on the car**: PLAN 5.3 item 2 (ACC cycle) + adapter silent with the engine off |
| **S9** | Error model (ARCH-04): `Link` gets a cause (`Lost(cause)`, `Off(reason)`), shown on Settings > Connections ("Bluetooth is off", "timeout"); `Logger` replaces the silent `.catch {}`; a blocked command fails fast in debug and logs in release; `exchangeOverStreams` caps the reply (4 KB); a PID that stops answering becomes null after a per-tier max age instead of freezing. Invert `aPidThatGoesSilentKeepsItsLastValueForever`. | M | 5 h | `vehicle/*`, `link/*`, Settings connections page, StatusBar | `ScriptedTransport` tests (garbage, silence, babble without '>'); pixel diff identical on Drive/Home |
| **S10** | Provenance (ADR-0012): `Origin`, per-source slices, `SourceFactory` replaces `Sources`, explicit mock/real selection, "sim" labels from data; `HomeContent.DEMO` moves to `src/debug` as snapshot content; Home tiles read slices. Needs the feature branch merged. | M | 8 h | `vehicle/*` (small), `car/*`, `ui/home/*`, `ui/shell/StatusBar.kt`, Settings connections page | test: every mock twin reports `Origin.Simulated`, a failing real source is `Unavailable` and never becomes the simulator; UI test of the label text; snapshot pixel numbers unchanged |
| **S11** | Canbox and MediaSession (ADR-0012). Pure `CanboxDecoder` in `:vehicle` against golden frames shared with `tools/test_canbox_decode.py`; `CanboxSource` interface + fake; the Android adapter only after the unit is inspected. `MediaSessionSource` (`NotificationListenerService`, user grant, `adb shell cmd notification allow_listener ...`) + `DemoMediaSource`. | M | 8 h + 8 h | `:vehicle/canbox/*`, `car/*`, `media/*`, manifest | decoder: golden frames + fuzz; media: fake `MediaSource` drives the Home card in a JVM test; real behaviour only on the unit |
| **S12** | `NavState` + `onNewIntent` (Home returns to Home) + `BackHandler` (ADR-0013). | L | 3 h | `ui/nav/*`, `MainActivity.kt`, `CockpitApp.kt` | `NavState` tests; emulator: Home key from Drive, Back from Settings |
| **S13** | `AppsRepository` (cached list, refresh on package add/remove) and `AppsUiState = Loading | Ready(apps, recents)` (fixes "No apps match" flashing while loading). | L | 3 h | repository tests with a fake package source; Apps screen pixel diff |
| **S14** | `SystemEnvironment`: minute-boundary clock tick (the bar shows hh:mm, not seconds), volume from a broadcast/observer, both lifecycle-aware; remove every `DebugHooks` read from production UI (snapshot values come from a debug-only `Environment`). `ArchitectureRulesTest.KNOWN` loses `ui-not-debug-hooks`. | L | 3 h | `ui/shell/StatusBar.kt`, `platform/*`, `CockpitApp.kt` | pixel diff identical in snapshot mode; `dumpsys` shows no 1 Hz wakeups while stopped |
| **S15** | Remaining file splits and previews: `Theme.kt` in three files, `HomeScreen`/`SettingsScreen` pages, `PreviewCanvas` in `src/debug`, `@Preview` for components and each screen (debug-only tooling dependency). | L | 4 h | ui files | compile; previews are not run in CI |
| **S16** | Canvas hardening: expose `LocalDesignScale`, a pure `touchTargetPx` helper with a test for the supported display sizes, a safe-area parameter (default 0), `tap()` takes a label for `semantics`. Document supported sizes in ADR-0002. | L-M | 4 h | `ui/theme/DesignCanvas.kt`, `ui/components/Primitives.kt`, call sites | pixel diff identical on 1280x800; screenshots on 1024x600 and 1920x1080 emulators reviewed by eye |
| **S17** | Versioning and signing: `versionCode` from `git rev-list --count HEAD`, `versionName` = `0.1.0+<sha>`, one release keystore if the APK is ever built anywhere but the owner's Mac (ADR-0006). | L | 2 h | `app/build.gradle.kts` | `tools/release.sh` prints the signer; installs over the previous release |
| **S18** | Dependency upgrade on its **own branch**: AGP, Kotlin, Compose BOM, lifecycle, activity, coroutines (lint reports AGP 9.5.1 and Compose BOM 2026.09.00 as current). Re-run the whole net, especially pixels and frame cost: a Compose upgrade can change rendering and performance. | M-H | 6 h | `gradle/libs.versions.toml`, wrapper | whole net + detekt/lint re-baseline of version rows only |
| **S19** | Only if cold start on the unit is a problem: baseline profile (a test-only `:baselineprofile` module is justified then, ADR-0010). Cheaper first: `adb shell cmd package compile -m speed -f dev.kuntal.cockpit` after install (docs/PERF.md). | M | 6 h | new module | cold-start `TotalTime` on the unit |
| **S20** | Tighten: empty `ArchitectureRulesTest.KNOWN`; shrink the baselines (delete rows as code is fixed); one mechanical Spotless/ktlint formatting commit when no branch is open, then add `spotlessCheck`; JaCoCo verification rules (`:vehicle` line >= 85 %, `settings`, `format`, `apps`, `car` >= 85 %). | L | 3 h | build files, config/ | gates green with the new thresholds |

Total about 100 hours (about 80 without the conditional S11 and S19). Dependencies: S6 needs S4; S7, S8 need S6; S9 before S10; S10 needs the feature branch; S11 needs S10; S14 needs S2; S18 last.

## 5. What to do FIRST, and why

1. **S0.** Nothing else is measurable without the baseline numbers, and the gates will be red after the merge until the baselines are regenerated.
2. **S2.** Small, independent, and it removes the only way another app can alter the launcher's configuration or put mock numbers on the screen
   (the activity is exported). It also makes "debug only" explicit before more tooling is added.
3. **S3-S5** in that order: moves first so that the hub is written in its final place, then seams so that it is testable, then settings.
4. **S6** is the product fix: it is the one defect that changes what the owner experiences (Trip A, polling while Maps is in front). If time is short,
   do S0, S2, S4, S6 and skip S3 until later; only the file paths in this plan change.

## 6. What NOT to do

- No Hilt, Koin or Dagger (ADR-0007). No Navigation-Compose (ADR-0013). No DataStore or Room for settings (ADR-0009). No new Gradle modules except a test-only baseline-profile module if S19 happens (ADR-0010).
- No MVI/Orbit/Redux framework, no "UseCase" classes, no generic `Repository<T>`: the app has one hot stream and a handful of settings.
- No ViewModel-per-screen for telemetry. The `State<VehicleUiState>` leaf-read pattern (ADR-0004) is the performance design; do not "clean it up" into plain UiState parameters.
- No responsive rewrite of the screens. The fixed canvas is right for one 1280x800 display; harden it (S16), do not replace it.
- Do not mix the dependency upgrade (S18) with structural steps; do not reformat before S20; do not move files and change logic in one commit.
- Do not let the foreground service contain logic, poll with the key off, or start a mock source as a fallback when a real source fails.
- Do not touch `CommandGuard` except to add tests; any change to what may be sent is a safety review (ADR-0003).
- Do not invent on-unit performance numbers: only the unit counts (docs/PERF.md).

## 7. Quick wins that were NOT applied (they collide with in-flight branches); apply them during the steps above

| Win | Where | Step | Effect |
| --- | --- | --- | --- |
| Refuse dev intent extras unless the app is debuggable (one `if (applicationInfo.flags and FLAG_DEBUGGABLE == 0) return` in `MainActivity`) | `MainActivity.kt` | stop-gap before S2 | closes ARCH-02 immediately |
| Replace `QUERY_ALL_PACKAGES` by `<queries><intent><action MAIN/><category LAUNCHER/></intent></queries>` | `AndroidManifest.xml` | any | removes a lint error and a privacy-relevant permission; verify the Apps grid still lists everything |
| `SettingsStore.update`: use `MutableStateFlow.update {}` | `SettingsStore.kt` | S5 | removes a lost-update race (theoretical today: every caller is on the main thread) |
| `exchangeOverStreams`: stop at 4 KB without `>` | `vehicle/StreamExchange.kt` | S9 | a babbling adapter cannot grow memory without bound |
| `VehicleRepository`: pass the caught exception to an `onError` callback instead of `catch { }` | `data/VehicleRepository.kt` | S9 | failures become visible |
| Status bar: ticker with `repeatOnLifecycle`/`collectAsStateWithLifecycle`, minute boundary, `mutableIntStateOf` | `ui/shell/Shell.kt` | S14 | no wakeups while stopped; lint info gone |
| `Tach(modifier: Modifier = Modifier)` and size at the call site | `ui/components/Tach.kt` | S15 | lint `ModifierParameter` |
| `AppsScreen`: do not show "No apps match" while the list is still loading | `ui/screens/AppsScreen.kt` | S13 | removes a visible flash |
| `TripTracker`: remove the two `!!` | `data/TripTracker.kt` | S3 | detekt `UnsafeCallOnNullableType` |
| `produceState` lint error: move the icon load to `LaunchedEffect` + `mutableStateOf` | `AppsScreen.kt` | S13 | lint error (probable false positive) gone |
| `org.gradle.configuration-cache=true` | `gradle.properties` | S17 | faster local builds (try it, keep if the build stays green) |
| README: link `docs/ARCHITECTURE.md` and `docs/adr/`, replace the hand-written test counts ("122 JVM tests", "26 tests") by the CI coverage summary | `README.md` | S0 | docs stop drifting; README currently disagrees with the suite (134 + 37 tests after this branch) |
| Log wrapper plus R8 `-assumenosideeffects` for `Log.d/v` | `proguard-rules.pro` | S4 | logging free in release |

## 8. Definition of done: "industry standard for this project"

Proportionate to one developer, one device, a hard frame budget. Not enterprise ceremony.

1. **Correctness.** Trip counters and link recovery are independent of which app is in front (JVM hub tests + the emulator checks above); the process surviving or dying
   loses at most 5 s of trip; nothing simulated is ever unlabelled; nothing about the car can be written.
2. **Boundaries.** `ArchitectureRulesTest.KNOWN` is empty; no `Dispatchers.*`, `System.currentTimeMillis`, `LocalDateTime.now` or singleton `.get(ctx)` in logic; `DebugHooks` is
   absent from the release dex; one composition root (`AppContainer`).
3. **Gates.** detekt, lint, tests, coverage and a release build run in CI on every push; baselines hold only version-bump warnings and the justified UI rows (`MagicNumber`, `MultipleEmitters` in `ui/`);
   the formatter is on.
4. **Coverage (JaCoCo, line):** `:vehicle` >= 85 %, `settings` / `format` / `apps` / `car` >= 85 %, `perf` logic >= 80 %; UI is covered by the pixel diff and a smoke test, not by a percentage.
5. **Tests.** A pyramid with fakes for every external edge (`FakeVehicleSource`, `FakeClock`, `ScriptedTransport`, fake settings/trip/package/media sources), characterisation tests inverted or deleted
   with the defect they described, one emulator smoke test (launch, visit every dock destination, no crash), the chaos script.
6. **Observability.** One `Logger`, a ring buffer visible on Settings > About, link causes shown to the owner; frame metrics as today.
7. **Build.** Version from git, one release keystore if more than one machine builds, dependencies current within one minor cycle with a dated upgrade note, release APK reproducible from a tag.
8. **Docs.** `docs/ARCHITECTURE.md` matches the code, an ADR for every decision above, README test/coverage numbers generated or removed, PLAN.md marked historical where it disagrees with the code.
9. **Performance.** The gate numbers from the real unit are recorded in docs/PERF.md. Until that exists, this item is open no matter what else is done.

## 9. Step log (fill in as steps land)

| Step | Date | Commit(s) | Net green? | Notes |
| --- | --- | --- | --- | --- |
