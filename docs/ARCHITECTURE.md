# Architecture

How the Cockpit launcher is put together, how data gets from the OBD adapter to pixels, and how to extend it.
Scope: one developer, one embedded head unit (1280x800, about 2 GB RAM, weak GPU), a hard frame budget (p99 <= 10 ms). That is why the
architecture is deliberately small: two Gradle modules, manual wiring, no DI framework. Decisions and their reasons are in
[docs/adr/](adr/); the review that produced the target state is in [docs/reviews/architecture.md](reviews/architecture.md) and the ordered
migration in [docs/REFACTOR-PLAN.md](REFACTOR-PLAN.md).

This file describes the code **as it is**. Parts marked **Target** are not built yet.

## 1. Modules and packages

```
:vehicle   (pure Kotlin/JVM, depends on kotlinx-coroutines only; no Android)
  dev.kuntal.cockpit.vehicle
    Pid, PidDecoder          OBD-II mode 01 PIDs and how to decode them
    ElmParser                reply text -> numbers (tolerant, returns null, never throws)
    CommandGuard             read-only whitelist: ELM `AT` setup + mode 01 reads, nothing else        [ADR-0003]
    Transport                suspend open/close/exchange                      <- TcpTransport, (BluetoothTransport lives in :app)
    StreamExchange           "send cmd\r, read until '>'" shared by byte-stream transports
    ElmClient                VehicleSource over a Transport: ATZ.. handshake, PID discovery, tiered polling (PollSchedule)
    VehicleSample            one snapshot of the powertrain (nullable fields; boost = MAP - baro)
    VehicleSource            interface { fun samples(): Flow<VehicleSample> }      <- ElmClient, SimSource
    DriveCycle, SimSource    deterministic simulator (the "sim" data)

:app       (Android application, one module)
  dev.kuntal.cockpit                 composition root: MainActivity, CockpitApp (nav + wiring), CockpitViewModel,
                                     SettingsStore (SharedPreferences), DebugHooks (dev switches)
  dev.kuntal.cockpit.data            pure logic, JVM-tested, NO android.* imports (enforced by ArchitectureRulesTest)
                                     Settings, Units, Fmt, Smoother, TripTracker, GearEstimator, BoostBar, AppIndex,
                                     VehicleRepository (link state machine + backoff), HomeContent
  dev.kuntal.cockpit.source          Android-side adapters: Sources (settings -> data flow), BluetoothTransport
  dev.kuntal.cockpit.perf            FrameStats, PerfGovernor (pure) + FrameMeter, Perf (Android FrameMetrics)
  dev.kuntal.cockpit.ui.theme        Palette, LocalPalette/LocalSettings, fonts, DesignCanvas (the 1280x800 scaler)    [ADR-0002]
  dev.kuntal.cockpit.ui.components   Txt, card/pill/tile modifiers, SegBar, Tach, Chips, Stepper, smoothed(), Icons
  dev.kuntal.cockpit.ui.shell        Dest (destinations), StatusBar, Dock, AppLauncher, InstalledApps (PackageManager access)
  dev.kuntal.cockpit.ui.screens      HomeScreen, DriveScreen, AppsScreen, SettingsScreen
```

Dependency direction (an arrow means "may import"):

```
ui.screens -> ui.components -> ui.theme
    |             |
    v             v
   data  <----  source / perf / root (composition root)  ---->  :vehicle
    |                                                            ^
    +------------------------------------------------------------+   (data imports vehicle types only)
```

Rules that are checked by `app/src/test/.../architecture/ArchitectureRulesTest.kt` (a ratchet: new violations fail, listed ones can only
shrink): `:vehicle` imports nothing from Android; `data/` imports no Android, no Compose, and nothing from ui/source/perf/root;
no `GlobalScope`; no wall-clock reads in logic; colours only through the palette. Known violations are listed in the test with the
finding that tracks them.

## 2. Data flow, adapter to pixels

```
ELM327 adapter ──(TCP | Bluetooth SPP)──> Transport.exchange("010C")          vehicle/TcpTransport, source/BluetoothTransport
        └ every call hops to Dispatchers.IO, returns the text before '>'
ElmClient.samples(): Flow<VehicleSample>        handshake, supported-PID discovery, PollSchedule (fast: rpm+speed every cycle,
        every command passes CommandGuard       medium every 3rd, slow every 10th), one sample per cycle, transport closed in finally{}
VehicleRepository.states(): Flow<VehicleUiState>  per sample: TripTracker.accept (distance, top, 0-100, peak boost),
        GearEstimator, link = Connected; on upstream failure Lost -> wait 1,2,4,8,10 s -> Connecting
Sources.states(settings)                          picks SimSource / ElmClient(TcpTransport) / ElmClient(BluetoothTransport) from Settings.sourceKey
CockpitViewModel                                  settings.distinctUntilChanged(sourceKey) -> flatMapLatest -> shareIn(WhileSubscribed 5 s)
        └ .sample(PerfMode.telemetryPeriodMs)       20 Hz, or 10 Hz when the PerfGovernor says the unit cannot keep up
        └ .stateIn(WhileSubscribed 5 s) : StateFlow<VehicleUiState>
CockpitApp: vm.state.collectAsStateWithLifecycle() -> passes the **State itself** (not .value) to the screens
Screens: Txt/LiveTxt/SegBar/Tach read `state.value.sample?.rpm` inside text lambdas or draw scopes
        └ a 20 Hz sample therefore recomposes only the strings that changed and repaints only the bars/arc            [ADR-0004]
smoothed(): exponential smoothing per display frame (withFrameNanos) between samples; stops scheduling when settled
```

`VehicleUiState(sample, link, trip, gear)` is the one telemetry aggregate. It is **unstable** for the Compose compiler because
`VehicleSample` comes from `:vehicle`, which is not compiled with the Compose plugin; screens take `State<VehicleUiState>` so this
never matters (see the Compose report numbers in the review).

Provenance rule: simulated data must be labelled. Today `Link.Simulated` makes the status bar read `OBD sim` and Settings says
"Simulated drive"; mock Home content (`HomeContent.DEMO`) is used only in snapshot mode. **Target** (ADR-0012): every datum carries
its origin (Live / Simulated / Unavailable) and the UI label is derived from it, never from a debug flag.

## 3. Threading model

| Work | Where it runs today | Notes |
| --- | --- | --- |
| Composition, layout, draw | main thread, then RenderThread | `Compose` + HWUI |
| `viewModelScope` pipeline (repository, trip, gear, `sample`) | **main thread** (`Dispatchers.Main.immediate`) | cheap per sample, but it shares the UI thread; each command also hops main -> IO -> main |
| Socket / Bluetooth I/O | `Dispatchers.IO` (hard-coded in the transports) | blocking reads; BT has a watchdog that closes the socket on timeout |
| Frame metrics | `HandlerThread("frame-meter")` | `FrameStats` is `@Synchronized` |
| Package list, icon loading | `produceState` + `withContext(Dispatchers.IO)` in composables | no repository, no cache of the list |
| Settings writes | in-memory `StateFlow` immediately, `SharedPreferences.apply()` async | |

**Target** (ADR-0008): the vehicle pipeline runs on one confined background dispatcher inside a process-scoped hub; the UI only
collects a `StateFlow`.

## 4. Lifecycle (and what happens when the owner opens Maps)

`MainActivity` is the HOME activity (`singleTask`, landscape, `configChanges` for density/size so rotation and density changes
do not recreate it). Opening another app stops the activity (`onStop`). Today:

1. `collectAsStateWithLifecycle` cancels its collection in `onStop`.
2. 5 s later `stateIn` (WhileSubscribed 5 s) cancels its upstream; 5 s after that `shareIn` cancels the repository flow;
   `ElmClient`'s `finally` closes the adapter. **About 10 s after the launcher leaves the foreground nothing reads the car.**
3. The `VehicleRepository` and its `TripTracker` are discarded. When the launcher returns, a new repository is created: **Trip A
   restarts at zero**, and the driving done while Maps was in front is never counted.
4. The first frame after returning shows the last value (a `StateFlow` keeps it) labelled Connected, then blanks to Connecting
   while the OBD handshake runs.

This is pinned by `LauncherBackgroundingTest` (finding ARCH-01). If the process is killed (low memory, vendor task killer, ACC cycle)
everything above is lost as well; only `Settings` and the recent-apps list are persisted.

**Target** (ADR-0008, refactor step 6): a process-scoped `VehicleHub` owns the source and the trip counters independent of any
activity, a foreground service keeps the process alive while the ignition is on, and trips are checkpointed to disk.

## 5. State and persistence

- UI state: `rememberSaveable { screen }` in `CockpitApp` (flat navigation, no back stack; ADR-0013), settings as an immutable `Settings`
  data class in a `StateFlow` (`SettingsStore`), telemetry as above.
- `Settings` has a string-map stored form (`toMap`/`fromMap`) with clamping (`sanitized`). It is saved in SharedPreferences file
  `cockpit_settings`; recent apps use a second file `cockpit`. There is no schema version yet (ADR-0009).
- `sourceKey` is the only part of the settings that may restart the connection; theme and units never reconnect.

## 6. Rendering model

- `DesignCanvas` lays every screen out on a 1280 x 800 grid where 1 dp = 1 design px, scales it to fit the display, and overrides
  `LocalDensity` (so system font scale and dpi are ignored on purpose; ADR-0002). Screens place things with `Modifier.at(x, y)`.
- Colours only through `LocalPalette`; typography through `Txt(text, size, lineHeight, ...)`; touch targets >= 56.
- Static/live layers: Home draws everything that never changes with telemetry in one cached `graphicsLayer`, and the live values in a
  second one (ADR-0005). `Tach` has a cached static layer and a per-frame sweep layer.
- Performance is measured in the app: `FrameMeter` -> `FrameStats` -> `PerfGovernor` (drops telemetry to 10 Hz when more than 2 % of
  frames exceed 12 ms twice in a row). The Settings > About page and `--ez perf true` show the verdict.

## 7. How to add things

**A screen.** Add an entry to `Dest` (`ui/shell/Shell.kt`; the dock has room for exactly 8 slots at 91 px: a ninth needs a layout
change), an icon to `Ic`, a composable under `ui/screens/` that takes `State<VehicleUiState>` if it shows telemetry (read the values in
leaf lambdas), and a branch in the `when (screen)` of `CockpitApp`. Compare it with the design: `tools/snap.sh <screen>` and
`tools/.venv/bin/python tools/cmp.py <screen>` (drive <= 2.9 %, home <= 3.4 %).

**A setting.** Add the field with its default to `Settings`, its key to `toMap` and `fromMap`, its clamping to `sanitized` and a test
in `SettingsTest` (the round-trip test fails until all three agree). Add the row in `SettingsScreen` with `SettingRow` + `Chips` or
`Stepper`. If it changes the connection, include it in `sourceKey`; if it must not, make sure it is not.

**A data source.** Implement `VehicleSource` (in `:vehicle` if it is pure protocol logic, otherwise in `:app/source`), add a `SourceKind`
and a `sourceKey` case in `Settings`, a branch in `Sources.states`, a `SourceRow` on the Connections page, and test the layers above it
with `FakeVehicleSource` (`app/src/test/.../testing/`). A real source needs a labelled mock twin that reports its origin as simulated.

**A PID.** `Pid` entry + decoder case in `PidDecoder`, field and `with` case in `VehicleSample`, tier in `PollSchedule`, parser test.
`CommandGuard` already allows every `01xx` read; it never needs to change. If it does, that is a safety review, not a code change.

## 8. Testing approach

| Layer | How | Where |
| --- | --- | --- |
| Pure logic (parsing, units, trip, settings, formatting, perf stats) | JVM unit tests, no mocks | `vehicle/src/test`, `app/src/test/.../data`, `.../perf` |
| Flows with time (repository, backoff, pipeline) | `runTest` + virtual time + `FakeVehicleSource` | `.../data/VehicleRepositoryFakesTest`, `.../lifecycle` |
| Adapter protocol | `ScriptedTransport` (garbage, silence, dying link), env-gated TCP test against the ELM327 emulator | `vehicle/src/test` |
| Architecture rules | text scan of the sources, ratchet | `.../architecture/ArchitectureRulesTest` |
| Pixels vs. design | `tools/snap.sh` + `tools/cmp.py` on the emulator | manual, not in CI |
| Frame cost | in-app `FrameMeter`; emulator numbers are not representative, only the unit counts | `docs/PERF.md` |

Shared test helpers: `FakeClock` / `virtualClock()`, `FakeVehicleSource` (failures, drop-outs, handshake delay, start/active counters),
`sample()` / `cruise()` / `kmCovered()` fixtures, `ScriptedTransport`. Characterisation tests that document a known defect are named
as such and say how to invert them when the defect is fixed.

## 9. Quality gates

```
tools/gb.sh :app:detektDebug :vehicle:detektMain                       static analysis (detekt + Compose rules), baseline mode
tools/gb.sh :app:lintDebug :app:lintRelease                            Android lint, baseline mode
tools/gb.sh :app:testDebugUnitTest :vehicle:test                       tests
tools/gb.sh :vehicle:jacocoTestReport :app:createDebugUnitTestCoverageReport -Pcoverage && python3 tools/coverage-summary.py
tools/gb.sh :app:compileReleaseKotlin -PcomposeReports --rerun         Compose stability report -> app/build/compose_reports
tools/rebaseline.sh                                                    regenerate baselines (after a merge; never to hide a regression)
```

"Baseline mode" means the gate is green on the existing code and fails only on new findings (`config/detekt/baseline-*.xml`,
`config/lint/lint-baseline-app.xml`). CI runs all of it (`.github/workflows/ci.yml`).

## 10. Invariants (never break these)

1. Read-only toward the car: only `AT` setup commands and mode 01 reads (`CommandGuard`, tested).
2. Simulated or demo data is always labelled; the app never falls back silently to the simulator when a real source fails.
3. Frame cost p99 <= 10 ms on the unit, zero missed vsyncs; telemetry is read at the leaves.
4. Design rules: palette tokens only, no red, text >= 18 px, touch targets >= 56, Barlow / Barlow Condensed, one Ford oval per screen.

## 11. Decision records

See [docs/adr/README.md](adr/README.md) for the index.
