# Performance review (`perf`)

Branch `review/perf`, base `434a2a4`. Emulator `emulator-5554` (Android 11 arm64, 1280x800 @160, 4 cores, 2 GB, host GPU, Apple M2 host shared with four other agents).
Everything below is *emulator evidence*: it shows how much work the app does and avoids, not what the Palm Bay's CPU and GPU will do.

## 1. Summary

**Verdict.** The gate (p99 <= 10 ms, zero missed vsyncs) cannot be certified without the unit. On the emulator the app's own per-frame cost is
small (release, AOT: app-cpu p50 0.6-0.8 ms, p99 3-5 ms on Drive with the simulator at 20 Hz), so a 5-10x slower CPU would sit near the 5 ms stretch goal,
and GPU fill is the open risk (the original drew 4.6-4.8 layers per pixel). The review removed the work that was provably waste: idle frames and wake-ups,
repaint on steady data, redundant full-screen fills, a Light mode that did not lighten Drive, a tight OBD loop, per-keystroke sorting of the app list.
Pixels are unchanged (parity.py, below). Unit tests and `:vehicle:test` are green.

**Counts.** 0 CRITICAL, 0 HIGH (no gate-breaking cost could be shown), 8 MEDIUM (all FIXED: PERF-01..05, 07..09), 4 LOW (PERF-06, 10, 11 FIXED; PERF-14 OPEN), 3 INFO (PERF-12, 13, 15).

**Top five.**
1. PERF-03 (MEDIUM, FIXED) steady data repainted the screen 17 times a second: 95 -> 18 ms/s of cpu, 16.9 -> 0.3 fps.
2. PERF-05 (MEDIUM, FIXED) overdraw 4.6-4.8 -> 2.6-3.4 draws per pixel (80 % of Home had 5 or more).
3. PERF-04 (MEDIUM, FIXED) Light mode did nothing for Drive; now x0.64 frames and cpu.
4. PERF-01 / PERF-02 (MEDIUM, FIXED) an idle screen drew a frame every second and woke the main thread 17.5 times a second; now 0.0 and 2.9.
5. PERF-09 (MEDIUM, FIXED in docs) a sideloaded APK runs un-compiled: cold start 364 ms vs 281 ms after one `adb` command; now in INSTALL.md.

## 2. Scope and method

Done: code read of every file in `app/` and the `:vehicle` polling path; Compose compiler reports; interleaved A/B benchmarks of the release build per screen
(Home, Drive, Apps, Settings) in Full and Light, on moving (simulator), steady (frozen), idle (no source) and protocol (fake ELM327 on port 35010) data; wake-up
and per-thread cpu accounting from `/proc`; overdraw measurement with the platform overlay; pixel parity between builds; start-up under each ART compile state;
a 15 minute soak with screen changes; JVM tests for the app-list search (300 entries), governor windowing, throttling and OBD pacing.
Every fix is one commit with before/after numbers in its message.

Measurement discipline. Five agents shared the Mac (load average 5-40 during the work). Wall-clock frame totals were useless; the primary metric is `app-cpu`
(FrameMetrics animation + layout + draw + sync, logged by the app), plus counts (frames/s, wake-ups/s, draws per pixel), process cpu per thread, and paired
ratios (each repetition's variants ran back to back). Runs at load > 15 inflated cpu by up to 3x, so cpu claims come from low-load runs; the exceptions are stated where they occur (PERF-01's cpu figures and PERF-04's "before" were taken at load 20-40; frame and wake-up counts, which carry the claims there, do not depend on load). The first Drive comparison
(`artifacts/bench/C-sim-drive.jsonl`) is the example: a 60 % "gain" that vanished when the base variant happened to run at low load; the Drive render-node
result (PERF-06) is therefore reported as modest.

NOT done / not possible: no real unit; GPU time cannot be read on API 30 (FrameMetrics GPU_DURATION is API 31) and the emulator's GPU is the host's, so fill rate
is judged by overdraw counts, not by time. Thermal behaviour, a real Bluetooth adapter, and the real display's refresh were not available. No software-GL
emulator (swiftshader) was started, to keep the shared machine usable. A Macrobenchmark-generated baseline profile was not built (no connected-device test
infrastructure offline).

## 3. Findings

### PERF-01 MEDIUM, FIXED (f1a5379): the status bar redrew the whole screen every second
Evidence: `bench.py --scenario static --noperf` (no data source, release, 2 runs x 40 s): frames/s 1.0 on Home, Drive, Apps and Settings (`dumpsys gfxinfo framestats`: frame
intervals 1.00-1.07 s). Root cause: `StatusBar` wrote `LocalDateTime.now()` every second although the face shows hh:mm. Impact: ~1 frame/s forever, 14-18 ms/s of
RenderThread cpu; on a real unit those wake-ups hit a down-clocked CPU and counted as janky (41 % janky frames on an idle Home), polluting the gate.
Fix: state truncated to the minute, timer aimed at the minute boundary, `ACTION_TIME_CHANGED/TIMEZONE_CHANGED` re-aim it, date formatter hoisted.
After: frames/s 0.0-0.1, RenderThread 0.6-1.5 ms/s, process cpu 42 -> 20 / 15 / 10 ms/s (Home/Drive/Apps).

### PERF-02 MEDIUM, FIXED (f1555ff): `Flow.sample` runs a ticker for as long as it is collected
Evidence: idle main-thread wake-ups 17.5/s, all threads 19.6/s with no data source (voluntary context switches from `/proc/<pid>/task/*/status`). Root cause: kotlinx
`sample()` starts a `fixedPeriodTicker` that fires every 50 ms regardless of upstream activity; the view-model collects it for the life of the process.
Fix: `throttleLatest` (conflate + emit + delay): a timer exists only for the period after an emission. Four virtual-time tests.
After: main 2.9/s (x0.17), all threads 5.2/s (x0.26). With flowing data nothing changes (Z vs R in `frozen.jsonl`).

### PERF-03 MEDIUM, FIXED (de39916): steady data repainted every bar at the sample rate
Evidence: new frozen-data scenario (same values, fresh timestamp each 50 ms: parked car or steady cruise): 16.9 fps, 95 ms/s process cpu, 183 wake-ups/s on Home and Drive.
Root cause: `SegBar`'s draw lambda read the vehicle state directly, so every sample invalidated every bar (and, on Home, the live layer) even when no segment changed.
Fix: the four reader lambdas fold into one packed Int behind `derivedStateOf`. After: 0.2-0.4 fps (the trip distance really changes), 18 ms/s, 46 wake-ups/s
(paired ratios fps x0.01-0.02, cpu x0.19, wake-ups x0.25). With moving data: no measurable cost.

### PERF-04 MEDIUM, FIXED (1d9f42e): Light mode did not lighten Drive
Evidence (old build, pinned Full vs Light, 3 runs): Home fps 17.5 -> 10.1, cpu -39 %; **Drive fps 29.9 -> 34.9, cpu 387 -> 501 ms/s** (load 20-40, so read it as "no saving", not as "Light costs more"). Root cause: Light only slowed the data;
`smoothed()` steps on every display frame while a value moves, so Drive stayed at one frame per vsync. Fix: in Light the glide applies on every second frame (the skipped frame writes nothing, so
it draws nothing; the step uses real elapsed time, motion speed unchanged).
After (2 interleaved runs, paired Light/Full): Drive frames x0.64 (27.8 -> 17.7), cpu x0.64 [0.57..0.72] (180 -> 115 ms/s), wake-ups x0.77, allocation x0.63;
Home frames x0.55. 10 Hz data with a 30 fps glide is a visibly coarser needle but numerals only ever changed at 20 Hz; not judged on a real panel.

### PERF-05 MEDIUM, FIXED (19917c9): overdraw 4.6-4.8 draws per pixel
Evidence: `tools/perf/overdraw.py` (platform overlay + per-pixel classification, snapshot mode). Original: Home 4.75 (80.4 % of pixels at 5+ draws), Drive 4.60 (70.2 %), Apps 4.56 (65.6 %),
Settings 4.84 (86.7 %); no pixel was drawn fewer than 3 times. Root cause: window background + `DesignCanvas .background` + frame gradient + three radial glows whose circles are 700-1000 px in radius
though their gradients are already transparent at 70-72 %. Fix: letterbox bars only (nothing on 16:10), window background dropped two frames after start, glows re-expressed over their visible radius.
After: Home 3.25 (18.1 % at 5+), Drive 2.60 (1.2 %), Apps 2.75 (7.2 %), Settings 3.37 (15.1 %). Pixel parity: home/settings/apps 0 pixels differ, drive 5 (<= 15/255); letterboxed 1024x600: 0.
What is left is the design itself (cards with fill + sheen + hairline + highlight, translucent dock/status bar). GPU time itself could not be measured (see section 2).

### PERF-06 LOW (modest gain), FIXED (7876fd0): Drive re-recorded the whole screen on every gauge frame
Root cause: Drive had no render nodes (Home has); the tach arc and the rpm/boost glides invalidated the root node ~60 times a second. Fix: one static node, one for tach arc + shift bar, one for the boost bar,
one for the sample-rate values (same order, same pixels; `Tach` split into `TachFace`/`TachArc`).
Evidence: JIT-only run (`--compile none`, 4 pairs, load ~5): app-cpu p50 0.75 -> 0.60 ms (paired x0.68 [0.57..0.87]), p99 unchanged (3.1 ms), process cpu x0.94 [0.80..1.44] (no change). AOT, low load:
0.8 -> 0.7 ms, process cpu unchanged. So: a small saving in the UI thread's record phase on a fast host; it grows with a slower CPU. The "6x cheaper" first measurement was load noise (section 2).
`StaticLayer` additionally has `--ez flatten true` (static node rendered into a texture): no effect measurable on the emulator, 0.3 % of Drive's pixels differ by <= 15/255; OFF by default (PERF-12).

### PERF-07 MEDIUM, FIXED (2c794c4): Apps search sorted and normalised the whole list per keystroke
Evidence (JVM, 300 synthetic apps, `AppIndexScaleTest` prints it): old `AppIndex.filter` median 6.48 ms, max 124.5 ms per keystroke; prepared search 0.054 ms (max 1.8 ms); one-off build 5.6 ms. On a weak ARM core
multiply by 5-10. Root cause: a Collator sort and a freshly compiled `Regex` per label, twice per label, on the main thread, per keystroke. Fix: `AppIndex.Search` (sort + keys once per list), parity with the old
implementation asserted on 320 queries. Also: synchronous icon-cache lookup (no blank first frame when a tile scrolls back), icon decoding on 2 threads (`Dispatchers.IO` could start 64 on a fling), last app list shown
at once on the next visit. Thread count on the Apps screen with the emulator's 17 apps: 43-45 before, 26-27 after (3 runs each); idle Apps cpu 14 -> 3 ms/s together with PERF-01. The effect on a 300-app list could not be measured here (the emulator has 17 launchable apps); the cap is what bounds it.

### PERF-08 MEDIUM, FIXED (5364e4b): the governor judged overlapping windows twice
`FrameMeter` fed `PerfGovernor` the 600-frame rolling ring every 5 s, so "two consecutive windows" shared half their frames: one burst of 18 slow frames (a screen change, a GC) dropped the app to Light; on a
static screen the stale ring was re-judged for ever. `FramePipeline` keeps the ring for display and a per-report window for the governor. `FramePipelineTest`: the old behaviour reaches Light (asserted), the new one
stays Full; sustained 10 % slow frames still reach Light and recover after three clean windows. The hysteresis constants (2 bad windows in, 3 clean out) are sound; Light cannot flap because recovery needs <0.5 % over 12 ms.

### PERF-09 MEDIUM, FIXED in docs (INSTALL.md): a sideloaded APK is not compiled
Evidence (`coldstart.py`, release, 5 cold starts each, median [min..max], am start -W TotalTime): verify (as installed) 364 [323..418] ms, speed-profile 285, speed 281 [229..332]; at low load: verify 283, speed-profile 234.
The original build gives the same numbers (364 -> 281), so this is the install state, not the code. The APK already contains a merged library baseline profile (3.6 KB) and the profile installer; a hand-written
wildcard profile for `dev.kuntal.cockpit.**` was processed by AGP but made no measurable difference (speed-profile 234 [227..278] without vs 221 [202..255] with) and is not shipped (the experiment file is in `artifacts/`).
Recommendation implemented: INSTALL.md step `adb shell cmd package compile -m speed -f dev.kuntal.cockpit`. The system only compiles during idle maintenance while charging, which a head unit may rarely reach.

### PERF-10 LOW, FIXED (71fdf41): `ElmClient` polled an instant adapter in a tight loop
Evidence: `bench.py --scenario obd` against the fake ELM327 (answers at once): process cpu 701 ms/s, 3433 wake-ups/s (main 814), 3.5 MB/s allocation, 0.8 GC/s, DefaultDispatcher threads 292 ms/s.
Root cause: no pacing between cycles; real adapters pace themselves (30-100 ms per command) but a fast Wi-Fi adapter would not. Fix: at least 40 ms per cycle (25 Hz, above the UI's 20 Hz). The test fails
without the fix (it never returns) and passes with it. The command set is unchanged (`CommandGuard` untouched). After (3 interleaved pairs): process cpu x0.38 (663 -> 251 ms/s), wake-ups x0.15 (4471 -> 660/s), GC x0.06 (1.07 -> 0.07/s), allocation x0.23, DefaultDispatcher threads 323 -> 39 ms/s; frames 55.6 -> 43.2/s (the gauges glide on the 25 Hz data instead of a 1000 Hz one). `ELM_TCP=127.0.0.1:35010 :vehicle:test` (TcpIntegrationTest) passes.

### PERF-11 LOW, FIXED, unmeasured (bec9a7f): icon and Ford-mark paths were re-parsed on every composition
25-40 icons and a 7 kB script path per visit to a screen. Cached per process. Cold start is unchanged within noise (363 vs 364 ms), so no benefit is claimed; it removes work that scales with CPU slowness.

### PERF-12 INFO, opt-in: `--ez flatten true` (static layers as textures)
Parity: drive 3229 px (0.3 %) differ by <= 15/255, home 982 px by <= 2. Emulator: no measurable change (total p50 x0.86, cpu x0.88 [0.55..1.21]; RenderThread unchanged). Whether it helps depends on the unit's GPU; it is
step 5 in `tools/perf/unit-checklist.md`.

### PERF-13 INFO: checked, nothing to fix
* Compose compiler (`-PcomposeReports=true`, `app_release-composables.csv`): 52 named composables, 46 restartable AND skippable; the other 6 are Modifier composables (`card`, `pill`, `tile`, `roundBtn`, `tap`, `smoothed`), which cannot restart by design.
  (The 140 in `module.json` include composable lambdas.) 14 "unstable" classes, none a composable parameter except `VehicleUiState` (only ever inside a `State`, which is stable). Strong skipping is on.
* Recomposition: leaf reads work. With flowing data Home produces ~17 frames/s at 20 Hz (the numerals really change); `LiveTxt` recomposes only when its string changes.
* Number formatting: `FmtCostTest` prints ~7 us per telemetry sample on the JVM (0.13 ms/s at 20 Hz); not a hot spot.
* GC/allocation: 0.0-0.1 GC/s at 440-570 KB/s while driving the simulator (about 1 blocking GC per 25 s), see the soak below. Text layout of changing numerals is the bulk of the allocation.
* R8/resources: minify + shrinkResources on, `proguard-android-optimize`, APK 2.2 MB (classes.dex 1.8 MB, six 100 kB fonts); window background already dark (no flash).
* Threads: 22-25 at idle, 24-28 during the soak; `frame-meter` handler thread is idle without frames.

### PERF-14 LOW, OPEN: proposals not built
* Dock tiles use `clip(RoundedCornerShape(20.dp))` (8 clip layers on every screen) only to round a background fill: `drawBehind { drawRoundRect }` would give the same pixels without clip layers. Parity must be proven; GPU gain unmeasurable here.
* `tap` adds a `graphicsLayer` (RenderNode) per tappable (about 20 on Home); harmless on this host, check on the unit with the Perfetto trace if sync time (SYNC_DURATION) is high.
* A pacing floor was added to the OBD loop; `VehicleRepository` still copies and emits a state for every polled sample (cheap) and drops them in `throttleLatest`. If idle with a connected adapter matters, de-duplicate in the repository
  (but keep the sample timestamp for a future "data is stale" indicator).
* Fallback ladder beyond Light: see `docs/PERF.md` ("Lean", "Still"); governor trial-and-revert for each step.

### PERF-15 INFO: first visit to Apps costs memory, then it settles
Soak sample taken a second after the first entry to Apps: PSS 88 MB (36-42 MB on every other sample), back to 42 MB 30 s later; native heap +3 MB and 4 more threads (icon loading) that then stay. Later visits showed no spike in the 30 s sampling. Not a leak (slopes below); a transient from loading the first icons. Not investigated further.

## 4. Tools and tests added

| path | use |
|------|-----|
| `tools/perf/bench.py` | interleaved A/B per screen; scenarios sim / static / frozen / obd; `label=apk@full\|light\|full+flat`; median [min..max], paired ratios, per-thread cpu, wake-ups, GC/alloc |
| `tools/perf/soak.py` | N-minute memory/thread/cpu drift with optional screen cycling, slope table |
| `tools/perf/coldstart.py` | cold/warm start and first-4-seconds frames under verify / speed-profile / speed |
| `tools/perf/overdraw.py` | draws per pixel per screen from the platform overlay (Pillow) |
| `tools/perf/parity.py` | pixel diff between two builds in snapshot mode (Pillow) |
| `tools/perf/unit-checklist.md`, `README.md` | what to run first on the real unit; how to run everything |
| `--es perfmode full\|light`, `--ez flatten true`, `--ez freeze true` | dev hooks (pin governor, texture cache, frozen data) |
| `-PcomposeReports=true` | Compose compiler reports in `app/build/compose_compiler/` |
| tests | `AppIndexScaleTest`, `FramePipelineTest`, `ThrottleTest`, `FmtCostTest` (prints), `ElmClientTest.anInstantAdapterIsNotPolledInATightLoop` |

## 5. Verification log

* Baseline (original build, pinned Full/Light, 3 runs, load 17-40): `artifacts/bench/base-sim-fulllight.jsonl`. Static 1 fps: `static0.jsonl`, `A-static.jsonl`. Throttle: `throttle-static.jsonl`.
* Drive layers: `C-sim-drive.jsonl` (noisy), `jit-drive.jsonl` (clean). Modes: `modes-sim.jsonl`. Frozen: `frozen.jsonl`. OBD: `obd.jsonl`. Cold start: `artifacts/logs/coldstart-*.log`.
* Parity (original debug build vs final debug build, snapshot mode, `parity.py`): drive 5 pixels differ (max 15/255), home 0, settings 0, apps 0; letterboxed 1024x600 (`wm size`): drive 0, home 0 (gray-level differences). Official project check (`snap.sh` + `cmp.py`, final debug build): **drive 2.81 %** (limit 2.9), **home 3.34 %** (limit 3.4).
* Tests: `tools/gb.sh :app:testDebugUnitTest :vehicle:test` green: 161 tests, 1 skipped (`TcpIntegrationTest` without `ELM_TCP`), 0 failed; with `ELM_TCP=127.0.0.1:35010` the integration test runs (2.1 s) and passes against the fake adapter.
* Clock still ticks per minute: release build, no source, Home: status bar showed 05:45 with the device clock at 05:45:30 and 05:46 at 05:46:03 (`artifacts/appsflash/clock-*.png`).
* Soak (`soak.py`, release, simulator at 20 Hz, 15 min, home/drive/apps/settings cycled every 90 s, 30 s samples): PSS 38-44 MB throughout (slope -120 KB/min over the second half), Java heap flat (-136 KB/min), native +22 KB/min (the step is the first Apps visit; flat afterwards), views 8 and threads 28 constant after the first Apps visit, no crash, process cpu 65 ms/s on average. File descriptors are not readable by the shell user on this image (reported as unavailable).

## 6. Residual risk and recommendations

1. Run `tools/perf/unit-checklist.md` first; the unknown is the GPU and CPU. If total p99 is high while app-cpu is low, it is fill: try `@full+flat`, then Light, then build "Lean".
2. Keep the compile step in the install procedure; re-run after every update.
3. The hysteresis only sees frames: on a static screen there are none, so Light is entered and left only while something moves; that is intended.
4. Overdraw is now 2.6-3.4; the remaining layers are the design (cards, sheens, translucent bars). Any further cut changes pixels and is a design decision.
5. Re-measure PERF-06 and PERF-04 on the unit: both scale with CPU slowness and were measured on a fast host.
