# Architecture review: Cockpit launcher (`review/architecture`)

Reviewer: `architecture` agent, 2026-11-08. Base commit `434a2a4`. Branch `review/architecture`.
Everything below is from reading all code in `app/` and `vehicle/`, the build files, CI, `docs/PLAN.md`, `DESIGN.md`, `PERF.md`, and from commands I ran
(output quoted). No emulator, no fake adapter and no device were used: anything that needs one is labelled **not verified on a device**.

## 1. Summary

**Verdict.** A small, honest, performance-literate code base: the pure core (`:vehicle`, `data/`) is clean and well tested, the Compose rendering discipline
is better than most production apps, and nothing is over-engineered. The structural weaknesses are all at the seams: the vehicle data lives and dies with a
UI object (a launcher is stopped every time the owner opens Maps), developer hooks ship in release behind an exported activity, and wiring by global objects makes the
live pipeline untestable. All of it is cheap to fix with plain Kotlin; no framework, no new Gradle module.

**Scorecard average (16 areas, section 3): 2.5 / 5 before this branch, 2.8 after** (documentation +1 and CI gates +2; the structural scores do not move until the refactor is executed).

**Counts by severity (19 findings):** CRITICAL 0, HIGH 2, MEDIUM 8, LOW 7, INFO 2. FIXED on this branch: 2 (ARCH-10, ARCH-18) plus the test-infrastructure part of ARCH-13. OPEN: 15 (all HIGH/MEDIUM/LOW except those two).

**Top 5**

1. **ARCH-01 (HIGH)**: vehicle polling and the Trip A counters are owned by `CockpitViewModel` behind two `WhileSubscribed(5_000)`. About 10 s after the owner opens Maps the
   adapter is closed, nothing reads the car, driving is not counted, and Trip A restarts from zero when the launcher returns (pinned by `LauncherBackgroundingTest`).
2. **ARCH-02 (HIGH)**: dev hooks (`snapshot`, `perf`, `set`, `reset` intent extras, `DebugHooks`) are compiled into release and reached through the exported HOME activity.
3. **ARCH-03 (MEDIUM)**: global objects (`SettingsStore.get`, `Sources`, `Perf`, `DebugHooks`, `InstalledApps`) and hard-coded `Dispatchers.IO`: the pipeline cannot be tested without an `Application`.
4. **ARCH-04 (MEDIUM)**: failure is silent and unbounded: `.catch { }`, no cause on `Link`, a blocked command becomes a reconnect loop, a silent PID keeps its last value forever, `exchangeOverStreams` has no size limit.
5. **ARCH-09 (MEDIUM)**: the source model is OBD-shaped; Canbox (doors, climate, radar) and MediaSession need per-source provenance so mock data is always labelled "sim".

## 2. Scope and method

Done: read every Kotlin file (35 + 11 main, 21 + 8 test; 2,660 + 284 main lines), manifests, Gradle files, CI, all docs listed in the brief and `docs/CANBOX-FRAMES.md`; ran
the unit tests, `lintDebug`/`lintRelease`, detekt with type resolution and the Compose rules, JaCoCo, the Compose compiler reports; added gates, tests, docs and ADRs.

Not done: no emulator, device or adapter (by instruction); no on-unit performance or lifecycle measurement; `tools/snap.sh`/`cmp.py` not run (no emulator, and no UI file was touched);
no dependency upgrade; no refactor of `app/src/main` Kotlin (the lead executes it after the merge, see section 11). I did not bisect when lint started to fail.

## 3. Scorecard (1 = harmful, 3 = acceptable for a hobby product, 5 = exemplary for this project's size)

| Area | Score | Evidence (file / command) |
| --- | --- | --- |
| Modularity and layering | **3** | `:vehicle` is genuinely pure (no `android.*` import, checked by `ArchitectureRulesTest`) and `data/` is pure. But pure vehicle logic (`TripTracker`, `GearEstimator`, link state machine) sits in `:app/data`; `ui/screens/SettingsScreen.kt` imports `source.pairedAdapters`; `ui/shell/Shell.kt` imports `DebugHooks`; platform adapters (`InstalledApps`, `AppLauncher`) live in `ui/shell`; `Dest` (navigation) is in `Shell.kt`. |
| State management, unidirectional data flow | **3** | One immutable `Settings` in a `StateFlow`, telemetry as one aggregate, actions as lambdas: sound. Weak: `CockpitApp` keeps ad-hoc `remember` state (`notice`, `recents`), the Apps list is loaded inside a composable branch (`CockpitApp.kt:83`), the vehicle state's lifetime is a UI concern (ARCH-01). |
| ViewModel / UiState design | **2** | One `AndroidViewModel` for everything; exposes the mutable `SettingsStore` (`vm.settings.update`); `VehicleUiState` is unstable for Compose (embeds `:vehicle`'s `VehicleSample`; Compose report: `unstable class VehicleUiState`), worked around by passing `State<>`; no Loading/Error states (Apps shows "No apps match" while loading, `AppsScreen.kt:89`). |
| Dependency injection and testability | **2** | Global objects: `SettingsStore.get(ctx)`, `Sources`, `Perf`, `DebugHooks`, `InstalledApps`, `AppLauncher`; `Dispatchers.IO` hard-coded (detekt `InjectDispatcher`: 8 hits); VM needs an `Application`. Good: `now: () -> Long` in `ElmClient`/`SimSource`, `Transport` and `VehicleSource` interfaces, pure `Settings`. |
| Concurrency and lifecycle | **2** | The whole pipeline runs on `viewModelScope` = main thread, each ELM command hops main -> IO -> main (`Transport` calls `withContext(IO)`); polling stops ~10 s after `onStop`; if the system destroys the activity the ViewModel is cleared and polling stops at once; `StatusBar` ticks every second forever, including a binder call, while the activity is stopped (`Shell.kt:65-69`). Good: `finally { transport.close() }`, Bluetooth watchdog, cancellation-safe `catch`. |
| Error handling and result modelling | **2** | `Link` enum without a cause; `.catch { }` (`VehicleRepository.kt:35`); `require(CommandGuard...)` failure is swallowed into a reconnect loop; stale values carried forward forever (`VehicleSample.with`); `exchangeOverStreams` builds an unbounded `StringBuilder` (`StreamExchange.kt:11`). Good: parser returns `null`, settings are sanitised on read. |
| Persistence | **2.5** | SharedPreferences, all values as strings, no `schemaVersion`, no migration; two prefs files (`cockpit_settings`, `cockpit`); synchronous read on the main thread at ViewModel creation (`SettingsStore.kt:11`); read-modify-write on a `StateFlow` (`:17`). Trips are not persisted at all. Good: `fromMap` + `sanitized` make a corrupt file harmless (tests: `SettingsRobustnessTest`, 3,000 random maps). |
| Navigation | **3** | A flat `rememberSaveable { Dest }` + `when` is the right size for eight argument-free destinations. Missing: `onNewIntent` (Home key while on top), `BackHandler` (grep: zero hits), a state holder; the dock holds exactly 8 slots (a ninth does not fit, 744 px). |
| Compose practices | **3.5** | Strong: leaf-state reads (`LiveTxt`, `SegBar`, `Tach`), `drawWithCache`, static/live `graphicsLayer`s, `derivedStateOf` for the link, frame-clock smoothing that stops when settled. Compose report: 85 of 140 composables skippable, 0 restartable-but-unskippable, `StrongSkipping` on. Weak: 0 `@Preview`, 0 `semantics`/`contentDescription` (grep), 13 `ModifierMissing`, 8 `MultipleEmitters`, `LocalSettings` is a static CompositionLocal that recomposes the whole tree. |
| Fixed 1280x800 design canvas | **3** | Right for one display (ADR-0002). Costs: 101 `.at(x, y)` calls and 95 `Txt(` with literal sizes; font scale and dpi ignored by design (`Theme.kt:105`); touch targets scale down on smaller panels (56 -> 42 px at 1024x600); no safe-area handling if the vendor shows a system bar; letterboxing for other aspect ratios. |
| Debug-only code isolation | **1** | No `src/debug`; `DebugHooks` is referenced from four production files (`Shell.kt`, `CockpitApp.kt`, `CockpitViewModel.kt`, `MainActivity.kt`); intent extras `snapshot`/`perf`/`set`/`reset` are honoured in release on an exported activity (ARCH-02). |
| Build configuration | **3** | Version catalog, JVM toolchain, R8 + resource shrinking, Gradle cache. Against it: release signed with the per-machine debug key, static `versionCode = 1`, empty `proguard-rules.pro`, no dependency locking, dependencies pinned in Dec 2024 (lint on this machine reports AGP 9.5.1 and Compose BOM 2026.09.00 as current), `lintDebug`/`lintRelease` both fail on the base commit (2 errors each). |
| Testing strategy | **3** | 148 JVM tests, all green; fast; data package 90 % line coverage, `:vehicle` 76 %. But `ui`, `source`, `ui.shell`, root: 0 %; no ViewModel/hub test, no Compose or instrumented test, the pixel diff needs an emulator and is not in CI, the TCP integration test is env-gated. Infrastructure added here (fakes, characterisation, ratchet). |
| Logging / observability | **2** | Only `FrameMeter` logs (`CockpitPerf`) and the in-app overlay is excellent for frame cost. Nothing else logs: connection failures, parse failures, blocked commands and settings migrations leave no trace. |
| Documentation | **3 -> 4** | README, PLAN, DESIGN, PERF, INSTALL, CANBOX-FRAMES are detailed and honest about what is unverified. No architecture document or ADRs; PLAN.md disagrees with the code in places (DataStore vs SharedPreferences, Big Shoulders vs Barlow). Added here: `docs/ARCHITECTURE.md`, 14 ADRs. |
| CI quality gates | **2 -> 4** | Before: unit tests + debug APK only. After: detekt (type-resolved, Compose rules), lint, coverage, R8 release build, wrapper validation, report artifacts, all in baseline mode. |

## 4. Findings

Format: ID, severity, status, evidence, root cause, impact, fix.

### ARCH-01 (HIGH, OPEN): the vehicle pipeline and trip counters live and die with the launcher UI

Evidence
- `CockpitViewModel.kt:30` `.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)` and `:36` `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())`.
  The only subscriber is `collectAsStateWithLifecycle()` in `CockpitApp.kt`, which collects while the lifecycle is STARTED.
- `Sources.states()` builds `VehicleRepository(...)` and therefore a **new `TripTracker`** each time the upstream starts (`Sources.kt`; comment says "the caller restarts it only when sourceKey changes", which is not what the operators do).
- `TripTracker.reset()` has no caller (`grep -rn "\.reset()" app/src/main` finds none), so "Trip A" currently resets only by accident, when the pipeline restarts.
- Characterised in `app/src/test/.../lifecycle/LauncherBackgroundingTest.kt` (4 tests, all passing on the unchanged code):
  - adapter session still active 9 s after the launcher stops, **closed by 10.5 s** (5 s + 5 s of `WhileSubscribed`);
  - 10 s of cruising at 100 km/h, 30 s away, 10 s back: the car covered 1.39 km, **Trip A shows about 0.28 km** (less than half) and a second adapter session was started;
  - back inside the grace period: same session, trip keeps counting;
  - after a long absence the **first frame back is the stale last reading labelled Connected**, then Connecting, then Connected (a real handshake takes seconds).

Root cause: the data layer's lifetime is bound to a UI lifecycle owner. A launcher is stopped whenever another app is in front, which is its normal state.

Impact: Trip A, top speed, 0-100 best and peak boost miss everything driven while Maps/Radio/Phone is open (that is most of a drive); the link-recovery logic does not run in the background;
a stale reading is shown as live for a moment; the adapter is reconnected (ATZ, ATSP6, PID discovery) on every return after 10 s, which also adds load to the ELM327.
**Not verified on a device**: the real unit's process priority and whether a vendor task killer removes the process; see section 5.

Fix: process-scoped `VehicleHub` + foreground service + trip checkpoints (ADR-0008, refactor steps S6-S8). Tests to invert: the four above.

### ARCH-02 (HIGH, OPEN): developer hooks are in release and reachable from other apps

Evidence: `MainActivity.kt:20-23,34-38` reads extras `snapshot`, `perf`, `set`, `reset` unconditionally; `AndroidManifest.xml:19` `android:exported="true"` (required for HOME).
`snapshot` freezes the UI on `DebugHooks.driveState` (74 km/h, 5,029 rpm, +0.95 bar), shows `HomeContent.DEMO` (a fake song, "Android Auto Connected") and **resets the persisted settings**;
`set="source=ObdTcp,tcpHost=..."` rewrites and persists the data-source settings; `reset` wipes them. They run in `onCreate`, i.e. whenever the activity is created from a foreign intent
(after process death, an ACC cycle) or by `adb`. `DebugHooks` is read in `Shell.kt`, `CockpitApp.kt`, `CockpitViewModel.kt` and `MainActivity.kt`.
Reading the code, plus the **release dex** built here (`unzip -o app-release.apk 'classes*.dex'; strings -a classes.dex | grep -x -F -e snapshot -e perf -e reset -e screen -e page`): each of the extra names is present as a literal string, and so is the demo content (`Fri 30 Oct`, `21:47`, `Midnight City`, `Hurry Up, We're Dreaming`, the last two also inside the generated `NowPlaying.toString()`).
The hooks were not exercised on a device (no emulator).

Impact: another app can make the dashboard show invented numbers as live data (violates the product rule) and silently change or wipe the owner's settings (selected Bluetooth adapter, host).
It cannot send anything to the car (`CommandGuard`).

Fix: `src/debug` / `src/release` source sets (ADR-0011, S2). Stop-gap (quick win): ignore the extras unless `applicationInfo.flags and FLAG_DEBUGGABLE != 0`.

### ARCH-03 (MEDIUM, OPEN): global objects and hard-coded dispatchers

Evidence: `CockpitViewModel.kt:24` `SettingsStore.get(app)`, `:29` `Sources.states`, `:35` `Perf.mode`; `Perf` is a process-wide `object` mutated by `FrameMeter`; `InstalledApps` and `AppLauncher` are `object`s taking a `Context`;
`Dispatchers.IO` in `BluetoothTransport.kt:27,38,44`, `TcpTransport.kt`, `CockpitApp.kt:83`, `AppsScreen.kt:108` (detekt `InjectDispatcher`, type-resolved run: 5 hits in `:app`, 3 in `:vehicle`);
`LocalDateTime.now()` in the status bar. Compose report: `unstable class CockpitViewModel`, `SettingsStore`, `DebugHooks`, `Perf`.

Impact: the live pipeline cannot be exercised on the JVM except by copying its operator chain (what `LauncherBackgroundingTest` has to do). Time-dependent UI is untestable.
Fix: manual `AppContainer` (ADR-0007, S4). Hilt and Koin are rejected on build time and startup cost for about eight objects.

### ARCH-04 (MEDIUM, OPEN): errors are swallowed, unbounded or unlabelled

Evidence
- `VehicleRepository.kt:35` `.catch { }`: no log, no cause. `Link` has no cause, so Settings can only say "Connection lost, retrying" for "Bluetooth is off", "bad address", a timeout and a protocol bug alike.
- `ElmClient.kt:16` `require(CommandGuard.isAllowed(cmd))` throws `IllegalArgumentException`; the repository turns it into a reconnect loop with backoff, so a safety violation is invisible.
- `VehicleSample.with` keeps the previous value when a PID returns `NO DATA`. Characterised by `ElmClientRobustnessTest.aPidThatGoesSilentKeepsItsLastValueForever`: coolant is silent from command 41 on, yet the 60th sample still carries 83 C.
  A frozen sensor is shown as a live number ("correctness error shown as fact").
- `StreamExchange.kt:11-16` appends bytes until it sees `>`: a babbling adapter that never sends it is bounded only by the per-read socket timeout, which resets with every byte.
- Positive: `ElmParser` never throws (fuzzed: 5,000 random replies, `ElmClientRobustnessTest`), `Settings.fromMap` never throws (3,000 random maps).

Fix: S9 (`LinkState` with cause, `Logger`, fail-fast in debug, 4 KB cap, per-tier max age for fields).

### ARCH-05 (MEDIUM, OPEN): concurrency and lifecycle details

- Pipeline on the main thread: `shareIn(viewModelScope)` uses `Dispatchers.Main.immediate`; `ElmClient`'s loop, parsing, `TripTracker`, `GearEstimator`, `copy()` run there, with two context switches per ELM command.
  Cheap per sample, but it competes with composition and makes polling latency depend on frame work (and the reverse). Not measured; the perf agent owns frame cost.
- `StatusBar` (`Shell.kt:65-69`): `LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); volume = am.getStreamVolume(...); delay(1_000) } }` runs for the lifetime of the composition, which lives until `onDestroy`, so while the activity is only stopped it still wakes once a second for a binder call and a state write
  (Compose pauses the window's frame clock when stopped, so nothing is drawn; **not measured**). While visible it recomposes the bar every second although the bar shows minutes. Lint also reports `AutoboxingStateCreation` for `volume`.
- If the system destroys the stopped HOME activity (low memory, process alive) the `ViewModelStore` is cleared (only configuration changes retain it) and the pipeline stops immediately.
- `MainActivity` has no `onNewIntent`: with `singleTask`, the extras `screen`/`page` and a Home key press while on top are ignored; and no `BackHandler` (grep): what Back does to a root HOME activity on Android 11 is **not verified**.

### ARCH-06 (MEDIUM, OPEN): persistence has no schema version and is split

`SettingsStore.kt` stores every value with `putString`; no `schemaVersion`; recents in a second file (`InstalledApps.kt:39,41`, file `cockpit`); `sp.all` read synchronously when the ViewModel is created;
`update()` is a read-modify-write on a `StateFlow` (all callers are on the main thread today, so the race is theoretical). Renaming an enum entry silently resets that setting (tested as intended fallback in `SettingsTest`).
DataStore is not recommended yet (ADR-0009): async first read would flash the default theme, and 16 keys do not need transactions. Trip persistence needs its own atomic file (S7).

### ARCH-07 (MEDIUM, OPEN): layering: pure vehicle logic is in the wrong module; the UI reaches into adapters and debug globals

`TripTracker`, `GearEstimator`, `VehicleRepository`/`Link`/`VehicleUiState` are pure Kotlin about `VehicleSample`, tested in `:app` (slower, AGP) instead of `:vehicle`. `ui/screens/SettingsScreen.kt` imports `source.pairedAdapters`;
`ui/shell/Shell.kt` imports `DebugHooks`; `InstalledApps` (PackageManager) and `AppLauncher` are in `ui/shell`; `Theme.kt` refers to `data.Settings` by fully qualified name. Two are recorded in `ArchitectureRulesTest.KNOWN`.
Fix: S3 moves (old-to-new mapping in section 11.2), not new Gradle modules (ADR-0010).

### ARCH-08 (MEDIUM, OPEN): the fixed canvas has no accessibility or display-size story

`Theme.kt:102-105`: scale = min(w/1280, h/800), `Density(density = scale, fontScale = 1f)`. Consequences: system font scale ignored (deliberate), touch targets shrink with the display
(56 design px = 42 px on 1024x600, below Android's 48 dp guidance), no safe-area parameter if the vendor system bar stays visible, no semantics for any tap target (`tap()` is `clickable` without label or role), no `@Preview`.
It is the right trade-off for one known display, but the display is not yet confirmed (recon M4 has not happened). Fix: S16 (scale local, touch-target helper + test, safe-area parameter, labels); decide after recon.

### ARCH-09 (MEDIUM, OPEN): the source model cannot take Canbox or MediaSession without breaking the "sim" rule

`VehicleSample` is a Mode-01 powertrain record; `VehicleSource` is one interface; `Link` is one global link state; the only provenance label is `Link.Simulated` -> "OBD sim" (`Shell.kt:85`). Mock Home content is selected by `DebugHooks.snapshot`
(`CockpitApp.kt:61`). Climate and the other Home tiles currently say "Waiting for Canbox" (`HomeContent.EMPTY`). The feature branch adds mock data; without an origin on each datum the label rule depends on every screen remembering to add it.
Design in section 6 and ADR-0012 (S10, S11).

### ARCH-10 (MEDIUM, FIXED `127d4c9`): no static analysis, lint failing, no coverage, thin CI

At the base commit `tools/gb.sh :app:lintDebug :app:lintRelease` fails (online run): **2 errors each variant** -
`QueryAllPackagesPermission` (`AndroidManifest.xml:8`) and `ProduceStateDoesNotAssignValue` (`AppsScreen.kt:108`, which looks like a false positive: the lambda does assign `value`;
**cannot verify the detector's reasoning**), plus 20 warnings (debug) / 14 (release) of which 18 / 12 are version-currency notes. The commit message `a84d005` says "release build passes lint"; I did not bisect when that stopped being true.
CI ran unit tests and the debug build only.
Fixed: detekt + Compose rules, lint, JaCoCo, R8 release build, wrapper validation and report artifacts in `ci.yml`, all in baseline mode (section 8). Verified (section 9): green on the existing code; a deliberately added `data/` file importing `android.util.Log`
fails `ArchitectureRulesTest`; a new magic number in `:vehicle` fails detekt (output quoted in section 9).

### ARCH-11 (LOW, OPEN): navigation is a hand-rolled flat state

See scorecard. Add `NavState`, `onNewIntent`, `BackHandler` (ADR-0013, S12).

### ARCH-12 (LOW, OPEN): Apps screen has no Loading state

`CockpitApp.kt:83` starts with `emptyList()`; `AppsScreen.kt:89` then renders "No apps match “”" until `queryIntentActivities` returns (hundreds of ms on a weak unit). The list is re-queried on every visit and never refreshed on install/uninstall. S13.

### ARCH-13 (LOW, OPEN; infrastructure FIXED `334ebf8`, `3ddb88c`): test gaps

Coverage (JaCoCo, unit tests only, section 9): `:vehicle` 75.0 % instructions / 76.2 % lines before, 86.4 % / 93.1 % after the loopback and simulator tests; `app.data` 90.1 / 88.4; `app.perf` 59.0 / 51.6; `source`, `ui.*`, root: 0 %. No test for the ViewModel, `SettingsStore`, `Sources`, transports on Android, or any composable.
Added: `FakeVehicleSource`, `FakeClock`/`virtualClock()`, fixtures, `ScriptedTransport`, `LoopbackElm` (in-process ELM327 on a loopback socket, so the TCP path runs in CI without the Python emulator), `LauncherBackgroundingTest`, `ArchitectureRulesTest`, `VehicleRepositoryFakesTest`, `SettingsRobustnessTest`, `ElmClientRobustnessTest`, `TcpLoopbackTest`, `SimSourceTest` (+23 tests: 12 app, 11 vehicle).

### ARCH-14 (LOW, OPEN): logging and diagnostics

Only `FrameMeter` logs. No `Logger`, no ring buffer, no log on failures (ARCH-04). Plan: `Logger` (S4) + Settings > About diagnostics (S9); R8 strips `Log.d/v`.

### ARCH-15 (LOW, OPEN): build reproducibility, signing, stale dependencies

`app/build.gradle.kts:24` signs release with `signingConfigs.getByName("debug")`: the key is `~/.android/debug.keystore` per machine, so an APK from CI or another Mac cannot update-install over one from the owner's Mac (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall loses settings).
`versionCode = 1`, no git-derived version, empty `proguard-rules.pro` (acceptable today: no reflection), no dependency locking. Lint (online) lists AGP 9.5.1, Compose BOM 2026.09.00, activity 1.13.0, lifecycle 2.11.0, core-ktx 1.19.1 as current against 8.7.3 / 2024.12.01 / 1.9.3 / 2.8.7 / 1.15.0. S17, S18.

### ARCH-16 (LOW, OPEN): Compose hygiene

detekt Compose rules on `main` (baselined): `ModifierMissing` 13, `MultipleEmitters` 8 (by design of the absolute-position style), `ModifierWithoutDefault` 2, `ComposableParamOrder` 1, `MutableStateAutoboxing` 1; lint `ModifierParameter` on `Tach` (`Tach.kt:37`).
No previews. Static `LocalSettings` recomposes the tree on any setting change (acceptable: rare, documented in `Theme.kt`). S15.

### ARCH-17 (INFO): what is good and should be kept

`CommandGuard` + test that every command sent is allowed; `:vehicle` purity; transports closing in `finally`; Bluetooth read watchdog; settings sanitising; trip glitch rejection;
the leaf-read telemetry design and static/live layers (ADR-0004/0005); frame meter + governor in the product; honest docs that say what is not verified; `tools/snap.sh` + `cmp.py` pixel gate.

### ARCH-18 (LOW, FIXED in this branch's docs commit): no architecture documentation or decision records

Added `docs/ARCHITECTURE.md`, `docs/adr/0001-0014`, `docs/REFACTOR-PLAN.md`.

### ARCH-19 (INFO): lint `QueryAllPackagesPermission`

A launcher legitimately needs to list launchable apps. A `<queries>` intent filter for `MAIN`/`LAUNCHER` would be enough and removes the permission and the lint error (quick win, not applied because it touches the manifest other branches may edit).

## 5. MUST-ANSWER: the vehicle pipeline when the owner opens Maps, Radio or any other app

**What happens today, step by step** (code: `CockpitViewModel.kt`, `CockpitApp.kt`, `VehicleRepository.kt`, `Sources.kt`; behaviour reproduced on the JVM by `LauncherBackgroundingTest`, which copies the operator chain; not run on a device):

| t after the launcher is stopped | Event |
| --- | --- |
| 0 s | `MainActivity.onStop` (the HOME task goes to the background; the activity is not destroyed, the ViewModel survives). `collectAsStateWithLifecycle` cancels its collection. |
| 0 - 5 s | no subscribers, but `WhileSubscribed(5_000)` keeps both stages running: the adapter is still polled. |
| 5 s | the `stateIn` stage stops collecting. `StateFlow.value` keeps the last `VehicleUiState`. |
| 10 s | the `shareIn` stage has no subscribers for 5 s: the repository flow is cancelled, `ElmClient`'s `finally { transport.close() }` closes the socket. **Nothing reads the car; nothing is counted.** |
| any time later | `onStart` -> new collector -> `Sources.states()` builds a **new `VehicleRepository` and `TripTracker`** -> Trip A, top speed, 0-100, peak boost start from zero; the screen first shows the stale last value labelled Connected, then Connecting (handshake), then Connected. |
| activity destroyed by the system (low memory) | `ViewModelStore` cleared -> pipeline stops at once. |
| process killed (low memory, vendor task killer, ACC off) | everything volatile is lost: link state, trip counters. Settings and recent apps are on disk. |

**Correct architecture** (ADR-0008, plan S6-S8):

1. **Singleton data layer, process-scoped.** `VehicleHub` owned by an `AppContainer` created in `Application.onCreate`: it collects the selected source in an application-scoped coroutine on a confined background dispatcher
   (not main), owns the `VehicleRepository`/`TripTracker`, publishes `StateFlow<VehicleUiState>`. Its lifetime is "the vehicle is on", not "a screen is visible". The UI sample rate (20/10 Hz) stays a UI concern; trips integrate every sample.
   The hub only restarts the session when `Settings.sourceKey` changes, and then the trip correctly resets (a simulator trip must not carry into a real one).
2. **Foreground service to keep the process alive**, empty of logic: `VehicleService` started by the hub while a live source runs, stopped when the source is Off/Simulated or after the engine-off policy ends the session.
   Without it the process of a launcher is protected only by the HOME-app priority; with 2 GB RAM and vendor "memory clean" services that is not a guarantee (**not verified on this unit**).
3. **What must survive process death:** Trip A (distance, average inputs, top, 0-100 best, peak boost) and the reset timestamp, checkpointed every ~5 s while moving and at engine-off to one atomic file (temp file + rename). Link state, smoothing, the 20 Hz stream, recents' ordering need not.
   After a restore the gap rule in `TripTracker` (a gap > 5 s is not integrated) already does the right thing.
4. **Engine-off policy** (otherwise an adapter polled with the key off drains the battery; PLAN.md calls cheap clones out for exactly that): no rpm / no answer for N minutes -> one probe every 30 s; adapter gone for M minutes -> stop polling and the service. A pure `EnginePolicy` class, tested with `FakeClock`.
5. **Recovery:** the existing 1/2/4/8/10 s backoff keeps running in the background; on return the UI sees the true link state immediately (hub publishes `Connecting`/`Lost` if no sample for 3 s) instead of a stale `Connected`.

**Permissions and OEM facts that matter on a head unit** (Android documentation and PLAN.md; **not verified on the Palm Bay 1000**):
- `FOREGROUND_SERVICE` is a normal, install-time permission (API 28+); `startForegroundService()` must be followed by `startForeground()` within about 5 s or the app is killed/ANRs; a notification channel and a notification are mandatory.
  Head units usually hide the notification shade, which does not exempt the notification. `android:foregroundServiceType="connectedDevice"` is valid from API 29; from targetSdk 34 the matching `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission and a Bluetooth runtime permission check are required (targetSdk is 30 on purpose, ADR-0006).
- Android 12+ restricts starting foreground services from the background; starting it while the activity is in front (or from a `BOOT_COMPLETED` receiver) is allowed. No receiver is needed: HOME is started at boot anyway.
- Bluetooth: `BLUETOOTH`/`BLUETOOTH_ADMIN` are install-time at targetSdk 30 (already declared); targetSdk >= 31 needs `BLUETOOTH_CONNECT` at runtime, which a launcher without a permission UI must request deliberately.
- Vendor behaviour is the real risk: many units suspend or kill apps on ACC off, run a "one-key clean" service, or re-assert their own launcher. PLAN.md M5 item 2 (process survives 30+ min key-off? `dumpsys activity processes`) decides whether the service is enough.
- Doze/App Standby buckets: a foreground service and the HOME role are exempt in practice; the unit's power model is unknown (recon).

**How to test it**
- JVM (no Android): `VehicleHub` with `FakeVehicleSource`, `virtualClock()`, a fake trip store: polling continues with zero UI subscribers; trip continues; `sourceKey` change resets, theme change does not; restore after a "process death" (new hub, same store); engine-off policy; corrupt checkpoint ignored. Start by inverting `LauncherBackgroundingTest`.
- Emulator with the fake adapter (lead's environment): `adb shell am start -a android.settings.SETTINGS`, wait 60 s, `adb shell input keyevent KEYCODE_HOME`; check the adapter log for continued requests and Trip A increased;
  `adb shell dumpsys activity services dev.kuntal.cockpit` and `adb shell dumpsys activity processes | grep -A3 dev.kuntal.cockpit` (foreground-service state while another app is on top);
  `adb shell am kill dev.kuntal.cockpit` (kills a cached process like the system would) then relaunch: trip restored; `adb shell am send-trim-memory dev.kuntal.cockpit COMPLETE` for memory pressure; `adb shell am set-standby-bucket dev.kuntal.cockpit rare`; 20 background/foreground cycles with `dumpsys meminfo` for leaks.
- Car: PLAN.md 5.3 checklist item 2 extended: Trip A over a 10-minute Maps session; ACC off/on within 30 min; adapter unplugged mid-drive; adapter polled with the key off (log must show silence).

## 6. Canbox data and the future MediaSession feed

- **Source interfaces.** Keep `VehicleSource` for powertrain (`VehicleSample` stays OBD-only). Add one interface per slice returning a `Flow` of an immutable value: body (doors, trunk, handbrake), climate (A/C, set temperatures, outside temp), radar (4 + 4 distances), steering keys (events, not state), now-playing. Each has its own link state, so the status bar's OBD / Canbox / BT / Android Auto items become real instead of the current `on = snap` placeholders (`Shell.kt:86-88`).
- **Canbox.** A pure `CanboxDecoder` in `:vehicle` (port of `tools/canbox_decode.py`: Raise `2E type len data ck`, Hiworld `5A A5 ...`; checksum resync), tested with the same golden frames as the Python tests. A `CanboxSource` adapter in `:app/car` delivers bytes or already-decoded events from whatever recon finds (vendor service/broadcast, or UART tap); the app **never transmits** to the Canbox or the bus (`docs/CANBOX-FRAMES.md` section 6).
  Where Canbox and OBD both provide a value (outside temperature, maybe speed) the hub prefers the one with the better Origin/freshness and says so in Diagnostics; do not blend.
- **MediaSession.** `MediaSource { nowPlaying: StateFlow<NowPlaying?>; controls }`. Real: `MediaSessionManager.getActiveSessions` via a `NotificationListenerService` (user grant: `adb shell cmd notification allow_listener ...` or the system toggle; PLAN.md 3.7), play/pause/next/prev through `MediaController.transportControls`. Progress is `position + elapsed * speed` computed from the frame clock only while playing, never polled at 20 Hz.
- **Mock vs real selection and the "sim" label.** Every source has a mock twin implementing the same interface. Selection is explicit in Settings > Connections per slice (`Off | Simulator | real`), resolved in one `SourceFactory` (replaces `Sources`). Each emitted value carries `Origin { Live, Simulated, Unavailable }`; the UI derives labels from the data:
  status-bar item shows "sim" when its slice is simulated, a tile fed by a simulated slice shows a "sim" chip, the media card source line reads "Demo (sim)". No code path reads a debug flag to decide that. A failing real source becomes `Unavailable` (dash, "no data"), **never** silently the simulator (the existing rule `Settings.sourceKey == "none"` for a Bluetooth source without an adapter is the model).
  Test: a property test that every mock twin yields `Origin.Simulated` only, and a UI-state test per tile.
- **Where mock data may live:** `src/main` mock twins are product features (the Simulator is selectable by the owner); snapshot/demo content for pixel comparison belongs in `src/debug`.

## 7. Comparison with Google's guidance and Now in Android (proportionate)

| Guidance / NiA practice | Here | Verdict |
| --- | --- | --- |
| Layered architecture UI / (domain) / data; unidirectional data flow | UI -> pure logic -> `:vehicle`; `State` flows down, lambdas up | Adopt; fix the lifetime of the data layer (ARCH-01). No domain/UseCase layer: there is no business logic shared by several screens. |
| Single source of truth in a repository, exposed as `Flow` | `VehicleRepository` per session, `SettingsStore` | Adopt; make the repositories process singletons with interfaces. |
| ViewModel per screen with immutable `UiState`, `collectAsStateWithLifecycle` | one ViewModel, `State<VehicleUiState>` passed down for leaf reads | Deviate on purpose for telemetry (frame budget, ADR-0004); adopt for Apps/Settings (Loading/Ready). |
| Hilt for DI | none | Reject: manual container (ADR-0007). |
| Multi-module by feature/layer (NiA has ~30 modules) | 2 modules | Reject more modules (ADR-0010): ~3,000 lines, one developer. Keep the rules by test. |
| DataStore | SharedPreferences | Keep behind an interface; DataStore later if needed (ADR-0009). |
| Navigation Compose | hand-rolled | Keep (ADR-0013). |
| Version catalog, convention plugins | catalog yes, convention plugins no | Catalog is enough; the quality block in the root build file is the only shared config. |
| Baseline profiles, macrobenchmark | none | Only if cold start on the unit is bad (S19); try `cmd package compile -m speed` first. |
| Previews + screenshot tests | none; pixel diff against the design instead | Previews for components (S15); the design diff is the better screenshot test and is kept. |
| Offline-first, sync | n/a | n/a. |
| Test doubles instead of mocking libraries | hand-written fakes | Adopt (now `FakeVehicleSource` etc.). |

## 8. Tools and tests added (paths, how to run again)

| What | Where | Run |
| --- | --- | --- |
| detekt 1.23.7 + Compose rules, type-resolved, baseline mode | `build.gradle.kts` (quality block), `config/detekt/detekt.yml`, `config/detekt/baseline-{app,vehicle}.xml`, `gradle/libs.versions.toml` | `tools/gb.sh :app:detektDebug :vehicle:detektMain` (`GB_ONLINE=1` the first time) |
| Android lint in baseline mode, HTML/XML/SARIF | same block; `config/lint/lint-baseline-app.xml` | `tools/gb.sh :app:lintDebug :app:lintRelease` |
| JaCoCo for both modules (opt-in) + summary | same block; `tools/coverage-summary.py` | `tools/gb.sh :vehicle:jacocoTestReport :app:createDebugUnitTestCoverageReport -Pcoverage && python3 tools/coverage-summary.py` |
| Compose compiler stability report (opt-in) | same block | `tools/gb.sh :app:compileReleaseKotlin -PcomposeReports --rerun` then `app/build/compose_reports/` |
| Re-baseline after a merge | `tools/rebaseline.sh` | `tools/rebaseline.sh` |
| CI | `.github/workflows/ci.yml` | on push/PR |
| `.editorconfig` | repo root | IDE |
| Test fakes | `app/src/test/.../testing/{FakeVehicleSource,FakeClock,Fixtures,FlowTesting}.kt`, `vehicle/src/test/.../testing/{ScriptedTransport,LoopbackElm}.kt` | `tools/gb.sh :app:testDebugUnitTest :vehicle:test` |
| New tests | `lifecycle/LauncherBackgroundingTest` (4), `architecture/ArchitectureRulesTest` (2), `data/VehicleRepositoryFakesTest` (4), `data/SettingsRobustnessTest` (2), `vehicle/ElmClientRobustnessTest` (6), `vehicle/TcpLoopbackTest` (3, uses `testing/LoopbackElm`), `vehicle/SimSourceTest` (2) | same |
| Docs | `docs/ARCHITECTURE.md`, `docs/adr/*`, `docs/REFACTOR-PLAN.md`, this file | - |

## 9. Verification log

Commands run from `~/ecosport-wt/architecture` through `tools/gb.sh` (logs in `artifacts/`, git-ignored). `GB_ONLINE=1` was needed once for lint's androidTest classpath, detekt and JaCoCo.

- **Baseline tests** (`:app:testDebugUnitTest :vehicle:test`): BUILD SUCCESSFUL; app 122 tests, vehicle 26 tests (1 skipped: the env-gated TCP test), 0 failures.
- **Baseline lint** (`:app:lintDebug`, `:app:lintRelease` at `434a2a4`): both `BUILD FAILED`, "Lint found 2 errors, 20 warnings" (debug) and "2 errors, 14 warnings" (release). Errors: `QueryAllPackagesPermission` `AndroidManifest.xml:8`, `ProduceStateDoesNotAssignValue` `AppsScreen.kt:108`. Offline, `lintDebug` additionally fails to resolve the androidTest classpath (`collection-jvm:1.4.0` not cached).
- **After gates:** `:app:lintDebug :app:lintRelease` BUILD SUCCESSFUL; baseline `config/lint/lint-baseline-app.xml` has 23 entries (AGP/dependency version notes 18, `ModifierParameter`, `ProduceStateDoesNotAssignValue`, `DiscouragedApi`, `QueryAllPackagesPermission`, `AutoboxingStateCreation`).
- **detekt raw findings** (type-resolved, main sources, before baselining): `:app` 131 (`MaxLineLength` 46, `MagicNumber` 39, `ModifierMissing` 13, `MultipleEmitters` 8, `MatchingDeclarationName` 6, `InjectDispatcher` 5, `CyclomaticComplexMethod` 4, `ModifierWithoutDefault` 2, `UnsafeCallOnNullableType` 2, one each: `MutableStateAutoboxing`, `ComposableParamOrder`, `LongParameterList`, `TooManyFunctions`, `ComplexCondition`, `UseOrEmpty`);
  `:vehicle` 56 (`MagicNumber` 51, `InjectDispatcher` 3, `LoopWithTooManyJumpStatements` 2). Baseline files hold 118 / 45 distinct ids (detekt de-duplicates identical signatures). Gate: `:app:detektDebug :app:detektRelease :vehicle:detektMain :vehicle:detekt :app:detekt` BUILD SUCCESSFUL.
  **Negative test:** adding `object ZzNegativeProbe { fun f(x: Int) = x * 1234 + 5678 }` to `:vehicle` made `:vehicle:detekt` fail (`MagicNumber` at 3:46 and 3:53, `BUILD FAILED`); removed afterwards.
- **Architecture ratchet negative test:** adding `data/ZzBad.kt` with `import android.util.Log` made `ArchitectureRulesTest` fail with `NEW architecture violations ...: [data-is-pure: data/ZzBad.kt]`; removed afterwards.
- **Compose compiler report** (`:app:compileReleaseKotlin -PcomposeReports --rerun`): 140 composables, 134 restartable, 85 skippable, 0 restartable-but-unskippable (the 6 non-skippable are value-returning composables, not restartable by design: `card`, `pill`, `tile`, `roundBtn`, `tap`, `smoothed`), 23 of 37 classes effectively stable, `StrongSkipping` enabled. Unstable classes: `VehicleUiState` (embeds `VehicleSample`), `CockpitViewModel`, `SettingsStore`, `DebugHooks`, `Perf`, `MainActivity` and other non-UI types.
- **Coverage** (JaCoCo via `-Pcoverage`, unit tests only), before my new tests:

```
== vehicle   instr  75.0%   branch  72.0%   line  76.2%   method  55.9%
== app       instr  10.3%   branch   7.4%   line  16.8%   method  21.6%
   dev.kuntal.cockpit.data           90.1   91.8   88.4  (26 missed / 198 covered lines)
   dev.kuntal.cockpit.perf           59.0   75.0   51.6
   dev.kuntal.cockpit.source, ui.components, ui.theme, ui.shell, ui.screens, root: 0.0
```
- **Coverage after my new tests** (same command; the new tests are mostly structural, so only `:vehicle`, which gained the loopback and simulator tests, moves; app `data` branch coverage 91.8 -> 95.1 %):

```
== vehicle   instr  86.4%   branch  79.6%   line  93.1%   method  76.3%
   package                                                     instr branch   line  lines(missed/covered)
   dev.kuntal.cockpit.vehicle                                  86.4   79.6   93.1  11/149

== app   instr  10.3%   branch   7.6%   line  16.8%   method  21.7%
   package                                                     instr branch   line  lines(missed/covered)
   dev.kuntal.cockpit.data                                     90.2   95.1   88.4  26/198
   dev.kuntal.cockpit.ui.components                             0.0    0.0    0.0  315/0
   dev.kuntal.cockpit.source                                    0.0    0.0    0.0  31/0
   dev.kuntal.cockpit.ui.theme                                  0.0    0.0    0.0  80/0
   dev.kuntal.cockpit.perf                                     59.0   75.0   51.6  30/32
   dev.kuntal.cockpit.ui.shell                                  0.0    0.0    0.0  96/0
   dev.kuntal.cockpit                                           0.0    0.0    0.0  126/0
   dev.kuntal.cockpit.ui.screens                                0.0    0.0    0.0  433/0
```

- **New tests:** `:app:testDebugUnitTest :vehicle:test` BUILD SUCCESSFUL: app 134 tests (+12), vehicle 37 tests (+11, 1 skipped as before), 0 failures; `:vehicle:test --rerun` three times in a row green (loopback sockets are not flaky).
- **Task (e), final tree:** `tools/gb.sh :app:testDebugUnitTest :vehicle:test :app:assembleRelease` (offline) -> BUILD SUCCESSFUL; app 134 tests, vehicle 37 tests (1 skipped), 0 failures; release APK `app/build/outputs/apk/release/app-release.apk` 2,287,285 bytes (2.2 MiB).
- **Gates, final tree, offline:** `tools/gb.sh :app:detektDebug :app:detektRelease :vehicle:detektMain :app:detekt :vehicle:detekt :app:lintDebug :app:lintRelease` -> BUILD SUCCESSFUL.
- **Release dex evidence for ARCH-02** (S0 reference for the refactor): `strings -a classes.dex | grep -c -x -F -e snapshot -e 'Midnight City' -e 'Fri 30 Oct'` = 3.

## 10. Residual risk and recommendations

- Everything about the launcher's behaviour on the real unit is unverified: process priority, vendor task killers, ACC behaviour, Back/Home on the HOME role, display size and system bars, frame cost. Recon (M4) and M5 decide several ADRs; they are written so that the choice is a small edit.
- The gates are in baseline mode: until the baselines shrink they only protect against new problems. Review `git diff config/` in every PR.
- `ArchitectureRulesTest` and the detekt/lint baselines will fail or grow after the four review branches merge. That is intended: triage with `docs/REFACTOR-PLAN.md` S0.
- The hub change (S6-S8) is the only one that can lose data or affect the car link; do it with the fake adapter first, then a parked car, then a drive.
- Dependencies are about two years behind; schedule S18 on its own branch after the structure is settled, never inside it.
- Owner decisions needed: Trip A reset gesture (S7); whether the service may show a notification; the supported display size once recon is done.

## 11. Refactor plan

(Identical to `docs/REFACTOR-PLAN.md`; edit that file, not this section.)

Author: architecture review, 2026-11-08. Evidence and reasoning: [docs/reviews/architecture.md](reviews/architecture.md). Decisions: [docs/adr/](adr/).
Target size: one developer, roughly 80-100 hours in total, but **every step stands alone** and leaves the app shippable.

### 0. Ground rules

1. Behaviour-preserving steps are separated from behaviour-changing ones. Only S6-S10 change what the app does (and S2 changes the release build);
   each of those inverts or adds tests first.
2. One step = a few small commits. After each commit the safety net (section 3) is green. No step mixes a move with a logic change.
3. Do not start a step because it is on the list: start it because the previous one is done and recorded in the log table at the end.
4. `ArchitectureRulesTest.KNOWN` and the detekt/lint baselines may only shrink. A step that grows them is wrong, or the finding needs its own ADR.

### 1. Target structure

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

### 2. Old path -> new path

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

### 3. Safety net (must stay green after every commit)

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

### 4. Steps

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

### 5. What to do FIRST, and why

1. **S0.** Nothing else is measurable without the baseline numbers, and the gates will be red after the merge until the baselines are regenerated.
2. **S2.** Small, independent, and it removes the only way another app can alter the launcher's configuration or put mock numbers on the screen
   (the activity is exported). It also makes "debug only" explicit before more tooling is added.
3. **S3-S5** in that order: moves first so that the hub is written in its final place, then seams so that it is testable, then settings.
4. **S6** is the product fix: it is the one defect that changes what the owner experiences (Trip A, polling while Maps is in front). If time is short,
   do S0, S2, S4, S6 and skip S3 until later; only the file paths in this plan change.

### 6. What NOT to do

- No Hilt, Koin or Dagger (ADR-0007). No Navigation-Compose (ADR-0013). No DataStore or Room for settings (ADR-0009). No new Gradle modules except a test-only baseline-profile module if S19 happens (ADR-0010).
- No MVI/Orbit/Redux framework, no "UseCase" classes, no generic `Repository<T>`: the app has one hot stream and a handful of settings.
- No ViewModel-per-screen for telemetry. The `State<VehicleUiState>` leaf-read pattern (ADR-0004) is the performance design; do not "clean it up" into plain UiState parameters.
- No responsive rewrite of the screens. The fixed canvas is right for one 1280x800 display; harden it (S16), do not replace it.
- Do not mix the dependency upgrade (S18) with structural steps; do not reformat before S20; do not move files and change logic in one commit.
- Do not let the foreground service contain logic, poll with the key off, or start a mock source as a fallback when a real source fails.
- Do not touch `CommandGuard` except to add tests; any change to what may be sent is a safety review (ADR-0003).
- Do not invent on-unit performance numbers: only the unit counts (docs/PERF.md).

### 7. Quick wins that were NOT applied (they collide with in-flight branches); apply them during the steps above

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

### 8. Definition of done: "industry standard for this project"

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

### 9. Step log (fill in as steps land)

| Step | Date | Commit(s) | Net green? | Notes |
| --- | --- | --- | --- | --- |
