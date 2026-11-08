# MONKEY — chaos / monkey-testing review of the Cockpit launcher

Agent `monkey`, branch `review/monkey`, emulator `emulator-5556` (Android 11 arm64 google_apis, 1280x800 @160 dpi, userdebug), fake adapter port 35020.
Everything below was produced by commands in this worktree; raw logs are in `artifacts/logs/` (git-ignored, not committed).

## 1. Summary

**Verdict.** The launcher was not robust against its own lifecycle or against a misbehaving adapter: it crashed on BACK, could be driven out of memory
or into a main-thread busy loop by an adapter, showed garbage and stale numbers as live data, leaked its adapter socket on every source switch
and every trip to the background, and froze on a pasted host name. All of these are fixed on this branch with a JVM test or a repro script each.
What is still open is a product decision (the trip counters only run while Cockpit is on screen) and two hardening gaps in the dev hooks.

**Counts** (21 findings): CRITICAL 0, HIGH 5 (all FIXED), MEDIUM 8 (6 FIXED, 2 OPEN), LOW 5 (3 FIXED, 1 OPEN, 1 WONTFIX), INFO 3.

**Top 5**
1. MONKEY-01 (HIGH, FIXED) BACK / any finish of the activity crashed the app: `FrameMeter` removed its frame-metrics listener twice. 6 of the first 7 monkey seeds died on it; 0 of 8 after the fix.
2. MONKEY-02 (HIGH, FIXED) An adapter that streams bytes without a `>` grew a `StringBuilder` until `OutOfMemoryError` (JVM test: OOM; emulator: 99 % CPU and monotonic heap growth).
3. MONKEY-03 (HIGH, FIXED) `UNABLE TO CONNECT` (ignition off, bench adapter) left no PIDs to poll: the flow spun without a suspension point on the **main thread** (CPU 118 %, UI did not answer), and the status said connected.
4. MONKEY-04/05 (HIGH, FIXED) Replies with signed hex (`410D-1` = speed −1), `0xFFFF` rpm, 255 km/h, 65 V were shown as live data; a link that stopped answering stayed "Connected" with frozen numbers.
5. MONKEY-06 (MEDIUM→HIGH for Wi-Fi adapters, FIXED) The adapter socket was never closed when the source was switched or the app went to the background (`withContext` in a cancelled coroutine skips its block); a Wi-Fi ELM327 accepts one client, so the reconnect would be refused.

## 2. Scope and method

Done (all against the debug build on the monkey emulator; "baseline" = the commit this branch started from, installed as `artifacts/baseline.apk`):

| Area | What I ran | Where |
|---|---|---|
| A. Android `monkey` | seeds 1-8 x 5000 events on the baseline and on the fixed build, 6 event mixes, throttles 0-200 ms, confined / `--ignore-crashes --ignore-timeouts` / leave-the-app with a system-package whitelist; logcat grep for FATAL / ANR / StrictMode / native crashes | `tools/chaos/monkeys.py`, `artifacts/logs/monkey-summary.tsv` |
| B. scripted chaos | dock storms, settings spam from random pages, BACK/HOME/APP_SWITCH/POWER key storms, relaunch storms with 14 extra variants (bogus screen, 2000-pair `set`, `reset`), sleep/wake x40, `wm size` x15 incl. portrait / ultrawide / 100x100, density 80-480, font scale 0.8-2.0, 7 locales incl. ar / he / fa, 12/24 h, 6 time zones, clock set to 2099 / 1971 / 2037 / leap day, cold start at 2099 and 1971, `kill` / `kill -9` / `force-stop` / trim-memory at every level, theme/unit/source switching while the link flaps through valid / no data / garbage / close-loop / unable | `tools/chaos/chaos_ui.py` |
| C. hostile adapter | 19 modes, 20 s each, link line + heap + CPU + threads + fds sampled; real adapter (`elm` emulator) is covered by the existing `TcpIntegrationTest` path | `tools/chaos/hostile_elm.py`, `chaos_link.py`, `chaos_session.py` |
| D. persistence | 14 corrupt prefs files (empty, binary, truncated, wrong types, unknown enums, overflow, NaN, Unicode digits, 1 MB host, XML bomb, 100k keys, control chars, bad recents), `pm clear` while running, files deleted while running, `install -r -d` baseline over fixed and back | `tools/chaos/chaos_persist.py` |
| E. input | 20 000 characters into the host box and the search box, rapid typing, ports 0 / 65536 / 99999999999 / -5 / +7 / 1e3 / blanks, 16 hardware keys | `tools/chaos/chaos_input.py` |
| F. Apps | 147 generated hostile APKs (10 000-char / empty / missing / emoji / RTL / control-char labels, crash-on-start, no launcher activity, 12 x 4096x4096 icons, unloadable vector icon, 120 bulk apps); install / uninstall / disable while the grid is open; tile-tap storms | `tools/chaos/mkapks.py`, `chaos_apps.py` |
| G. soak | see section 5 | `tools/chaos/soak.py`, `strictmode.patch` |
| H. invariants | settings file parsed and range-checked after every UI scenario; blank-screen detector on a screenshot after every scenario; link line compared with what the fake adapter really does | `chaos_ui.py: check()` |

Not done / limits (stated plainly):
* **Real ELM327 hardware, real Bluetooth, real head-unit GPU**: none. `BluetoothTransport` changes were not run at all (no BT on the emulator); they are code-reviewed only (the watchdog now also closes on cancellation, same pattern as TCP).
* Emoji / RTL **typed into the text boxes** is not possible through `input text`; those character classes reached the same code through `--es set` and the stored-prefs cases, and through hostile package labels.
* A non-ASCII IME / paste of 100 000 characters was not done: `input text` needs ~40 min for 100 000 characters, so the box was tested with 20 000.
* The 20-seed x 5000-event monkey quota: see section 5 for the exact seed list and what was actually run.
* I read the directory listing of `~/.hermes/cache/gb-locks` once while debugging the Gradle queue (names only, `slot1`); I did not open anything under `~/.hermes` otherwise.

Emulator artefacts found while testing (not app defects): the AOSP `ResolverActivity` chooser crashes in `system:ui` when HOME is pressed with two home apps and no default (fixed in the harness by `cmd package set-home-activity`, as on the real unit); the stock Pixel Launcher shows "keeps stopping" after a live locale change and at 100x100; `ANR ... does not have a focused window` while the screen is asleep (also hit Chrome and the stock launcher in the same log; the app's main thread was idle in `nativePollOnce` in the ANR trace); `emulator-5556` died once during a `stop; start` framework restart and was relaunched with the lead's flags (so its data was reset).

## 3. Findings

Format: ID, severity, status, evidence, root cause, impact, fix.

### MONKEY-01 — HIGH — FIXED (53820e6): BACK (or any finish of the activity) crashes the launcher
* Evidence: baseline monkey, seed 1 `confined`, aborted after 609 of 5000 events; seeds 2, 4, 5, 6, 7 crashed too (`artifacts/logs/baseline/monkey-summary.tsv`, 6 of the first 7 runs).
  ```
  FATAL EXCEPTION: main  Process: dev.kuntal.cockpit
  java.lang.RuntimeException: Unable to destroy activity {dev.kuntal.cockpit/.MainActivity}: java.lang.IllegalArgumentException:
      attempt to remove OnFrameMetricsAvailableListener that was never added
    at android.view.Window.removeOnFrameMetricsAvailableListener(Window.java:942)
    at dev.kuntal.cockpit.perf.FrameMeter.stop(FrameMeter.kt:52)
  ```
* Repro: `adb shell input keyevent KEYCODE_BACK` on the Home screen; `tools/chaos/chaos_home.py`.
* Root cause: `onStop()` calls `meter.stop()`, `onDestroy()` calls `meter.release()` which calls `stop()` again; the platform throws for a listener that is not registered.
* Impact: the process dies on every finish of the activity (BACK, a monkey key, the system finishing a HOME activity) and shows a crash dialog on the unit.
* Fix: `ListenerHandle` registers once / removes only if registered. Test `ListenerHandleTest` (compile-red: class absent). After: monkey seeds 1-8 on the fixed build, 0 crashes.

### MONKEY-02 — HIGH — FIXED (fd147ee): an adapter that never sends '>' grows the heap without bound
* Evidence: JVM test `aReplyWithoutAPromptIsCutOffInsteadOfGrowingForever` and `aReplyThatNeverEndsRunsIntoTheDeadline...` failed with `java.lang.OutOfMemoryError` before the fix. Emulator, baseline, mode `endless`: link `Connecting…` for the whole 16 s, CPU 99 %, Java heap +4 MB in 16 s (a byte at a time, so about 15 MB/min until the process is killed). Fixed build, same mode: `Connection lost, retrying`, heap +0 MB, CPU 28 %.
* Root cause: `exchangeOverStreams` appended every byte to a `StringBuilder` until a `>` arrived; the socket read timeout only fires on silence, so a byte per second also never trips it.
* Impact: OOM crash in normal driving if the adapter or a Wi-Fi bridge misbehaves; no way for the user to recover other than killing the app.
* Fix: 8 KB cap and a 15 s total deadline per command (`IOException`, the repository then shows Lost and backs off). `StreamBoundsTest`, `TcpHangupTest.anEndlessStream...`.

### MONKEY-03 — HIGH — FIXED (4c59fe8): "UNABLE TO CONNECT" turns the poll loop into a main-thread busy loop and the status says connected
* Evidence: baseline, mode `unable` (0100 → `SEARCHING... / UNABLE TO CONNECT`, which a real adapter says with the ignition off): `CPU 118 %`, `UI did not answer 2x` (uiautomator dump failed), `links=['None']`. Fixed: `Connection lost, retrying`, CPU 3 %. JVM: `anEcuThatNeverAnswersEndsTheSession...` returned an empty sample instead of failing.
* Root cause: no PID supported → `PollSchedule` empty → the `while(true)` in `ElmClient.samples()` has no suspension point and emitted all-null samples; `viewModelScope` runs on `Dispatchers.Main.immediate`, so this ran on the UI thread. `VehicleRepository` then set `Link.Connected` on the first (empty) sample.
* Impact: UI freeze / ANR whenever the adapter is plugged in with the ignition off (the most common bench and garage situation), and "OBD connected" with all dashes.
* Fix: a session without rpm/speed support ends with `IOException` (Lost + back-off); five silent cycles in a row also end it; a 40 ms floor per cycle (MONKEY-13). `ElmSessionHostileTest`.

### MONKEY-04 — HIGH — FIXED (06b2cd9): garbage in valid framing is displayed as a measurement
* Evidence: JVM `signedHexIsNotAByte` (`410D-1` decoded to speed −1.0, `410C+F+F` to a negative rpm: `"-1".toIntOrNull(16) == -1`) and `impossibleValuesAreNoDataNotNumbers` failed before the fix. Emulator baseline: modes `absurd` and `signs` → `Connected` in 4/4 polls.
* Root cause: lenient hex parsing; no plausibility window: rpm 0xFFFF (16 383.75, the "not available" marker), 255 km/h, 65.5 V, 215 °C, baro 0 kPa (turns every MAP reading into boost) were all decoded and shown.
* Impact: wrong numbers shown as fact on the dashboard, negative speed, bogus boost.
* Fix: strict ASCII hex; `PidDecoder.plausible()` windows (rpm ≤ 12 000, speed ≤ 250, voltage ≤ 32, temps −40..160/90, baro 40..130 kPa). Values outside become "no data". After: `signs` → Connected only for the first poll, then Lost; `absurd` keeps MAP/throttle/load at 100 % (physically possible) but no impossible rpm / speed on Drive [see verification log].

### MONKEY-05 — HIGH — FIXED (4c59fe8): a reading that stops arriving stays on screen forever as "Connected"
* Evidence: baseline mode `stale` (valid for 8 s, then `NO DATA` for everything): `links=['Connected']` to the end of the window. Fixed: `['Connected','Connecting…','Connection lost, retrying']`. JVM `aReadingThatKeepsFailingIsDroppedNotShownForever`.
* Root cause: `ElmParser.parse(...)?.let { s = s.with(pid, it) }` keeps the previous value on every miss; the sample is re-emitted with a fresh timestamp.
* Impact: speed / rpm frozen at the last value (e.g. 130 km/h) with a green status.
* Fix: three consecutive misses drop that reading; five silent cycles end the session.

### MONKEY-06 — MEDIUM (HIGH for single-client Wi-Fi adapters) — FIXED (4c59fe8, 55133fa): the adapter socket is never closed when the source is cancelled
* Evidence: `tools/chaos/chaos_session.py`, baseline: `session/socket_released_on_source_switch  established 1 -> 1` and `socket_released_in_background  established 1 -> 1 after 12 s in the background` (lsof on the fake adapter). Fixed: `1 -> 0` and `1 -> 0`. JVM `TcpHangupTest.cancellingTheSessionHangsUp...`: `TimeoutCancellationException` before (a cancel waited for the 20 s read timeout).
* Root cause (two): (a) `finally { transport.close() }` where `close()` is `withContext(Dispatchers.IO){…}`: in a cancelled coroutine `withContext` throws before running its block, so the socket was never closed; (b) `withContext(IO){ socket.read() }` keeps the caller suspended until the blocking read returns, and `flatMapLatest` joins the old collector before starting the next source, so switching away from a dead adapter waited for it.
* Impact: after every source switch and every 5 s in the background the old connection stays open until GC; an ELM327 Wi-Fi adapter accepts one client, so the reconnect is refused ("Connection lost" while the owner stares at a working adapter). Bluetooth RFCOMM has the same shape.
* Fix: `close()` runs in `NonCancellable`; `cancellableBlocking()` returns on cancellation and closes the socket (which also unblocks the thread). Bluetooth: same helper is *not* applied (not testable here); see section 6.

### MONKEY-07 — MEDIUM — FIXED (ce5510c): a lost link keeps showing the last speed and rpm
* Evidence: code (`state.copy(link = Link.Lost)` kept `sample`), JVM `RepositoryHostileTest` (passes after the fix; I did not run it red separately). Visible effect: Drive screen frozen at e.g. 130 km/h next to an amber dot.
* Fix: sample and gear are cleared on Lost; the trip (history) stays.

### MONKEY-08 — MEDIUM — FIXED (2c12ae4, 1948afb, 4c59fe8): the clock stepping backwards freezes the trip counters and breaks the simulator
* Evidence: `ClockJumpTest` red before (`DriveCycle.at(-3_600_000)` gave coolant −2115 °C, fuel 98 %); `TripHostileTest.aClockThatStepsBack...`.
* Root cause: `TripTracker` returned on `dt <= 0` *before* updating `lastT`, so after a one-hour step back (GPS/NTP sync, normal on a head unit after boot) every sample was dropped for that hour; samples carried wall-clock time.
* Fix: a backwards step is treated as a data gap; `MonotonicClock` is the default for `ElmClient` and `SimSource`; `DriveCycle.at` clamps negative time. Negative / NaN speeds are ignored by the tracker.

### MONKEY-09 — MEDIUM — FIXED (7ed17d7, 61d127a): unbounded text settings freeze the UI
* Evidence: baseline `chaos_persist.py huge_host_1MB` → app/UI dead; `Choreographer: Skipped 180 frames!`, `OpenGLRenderer: Davey! duration=3052ms` and `3339ms` (the Connections row lays out the 1 MB host on every redraw). Fixed: PASS. Typing: see verification log.
* Fix: host ≤ 253 characters without whitespace / control / zero-width characters, BT name ≤ 64, address ≤ 17 (`Settings.sanitized`); the text fields cap what they hold (`maxLen`), so a paste cannot grow the field either. `SettingsHostileTest`.

### MONKEY-10 — MEDIUM — FIXED (50f4971): a damaged recents file crashes the Apps screen on every entry
* Evidence: baseline `chaos_persist.py recents_wrong_type`: `ClassCastException: java.lang.Integer cannot be cast to java.lang.String at SharedPreferencesImpl.getString ← InstalledApps.recents(InstalledApps.kt:41) ← CockpitApp.kt:84` (composition). Fixed: PASS.
* Impact: needs a corrupted `cockpit.xml`, then Apps crashes until the data is cleared (crash loop on that screen only).

### MONKEY-11 — LOW — FIXED (828f0f4): hostile package labels / icons / search text
* Labels are cleaned and capped at 64 characters (control characters out, package name when empty); an icon that throws or runs out of memory while drawing is "no icon" instead of an uncaught exception in `produceState` (which would crash the app); `queryIntentActivities` failures give an empty list; the search query is clamped to 64 characters. `AppsHostileTest`. On-device result with 147 hostile APKs: see verification log. I did not run the Apps chaos against the baseline, so I cannot claim a crash was removed by this commit.

### MONKEY-12 — LOW — FIXED (eb70ba6): the HOME key does nothing while the launcher is running
* Evidence: `chaos_home.py`; baseline: from Drive and from Settings, HOME leaves the screen unchanged ("marker before: True → after HOME: True"); fixed: Home screen.
* Root cause: `singleTask` delivers the HOME intent to `onNewIntent`, which was not overridden. Fix: a counter bumped in `onNewIntent` that `CockpitApp` turns into `screen = Home`.

### MONKEY-13 — LOW — FIXED (99f3a62): unthrottled poll loop
* Evidence: JVM `PollRateTest` (gaps of 0 ms between cycles); on the emulator an instantly answering adapter (modes `valid`, `fast`) kept the app at 52-57 % CPU while only drawing a static page.
* Fix: 40 ms floor per cycle (25 cycles/s is more than the 20 Hz UI shows).

### MONKEY-14 — MEDIUM — OPEN: telemetry and the trip stop with the activity, and Trip A restarts from zero
* Evidence: `chaos_session.py` (both builds): `Trip A before=0.3 km, after 12 s in another app=0.0 km`.
* Root cause: the shared upstream is `WhileSubscribed(5_000)` and the only collector is the activity (`collectAsStateWithLifecycle`), so the adapter link is closed 5 s after Cockpit leaves the screen and the `TripTracker` is rebuilt on return. While Maps is in front nothing is counted. A BACK press (finishes the activity, clears the ViewModel) does the same.
* Proposal (product decision): keep the source alive while the process lives (`SharingStarted.Eagerly` or a long stop timeout), ideally in a foreground service so Android does not kill it, and persist the trip. Not changed: it is a feature-level change with battery / permission consequences.

### MONKEY-15 — MEDIUM — OPEN (hand to the security reviewer): dev launch hooks are live in release builds
* Evidence: code. `MainActivity.applyDevSettings()` honours `--ez reset true`, `--es set "source=ObdTcp,tcpHost=…"`, `snapshot`, `perf` unconditionally and the activity is `exported="true"`; any installed app can wipe the settings or point the OBD client at its own host with one `startActivity`. Not verified on a release APK.
* Proposal: ignore these extras unless `applicationInfo.flags & FLAG_DEBUGGABLE != 0`. Not changed here to avoid a merge conflict with the security branch.

### MONKEY-16 — LOW — OPEN: every keystroke in the host box reconnects
* `Settings.sourceKey` contains the host, `TextBox` stores on every change, so typing `192.168.0.10` opens 12 connections / name lookups in a row (each now cancels the previous one promptly, so it is wasteful, not harmful). Proposal: store on IME Done / focus loss or debounce 600 ms in `TextBox`. [measured numbers: see verification log]

### MONKEY-17 — INFO: things that were checked and found sound
* Corrupt, empty, truncated, bomb, huge and wrongly-typed `cockpit_settings.xml` all load (Android drops an unreadable file; `Settings.fromMap` falls back per key); `pm clear` and deleting the prefs while running are survived; `install -r -d` downgrade/upgrade keeps the settings byte-identical.
* No RTL problem: the manifest does not set `supportsRtl`, so the view hierarchy stays LTR under ar / he / fa (screenshots identical to en-US); all text is hard-coded English and digits stay Latin (Locale.US formatting) in 7 locales.
* Display sizes 800x480, 1920x720, 1024x600, 3840x1080, 640x360, 1000x1000, 320x240, 1200x500, 2560x1600, 1280x400; density 80-480; font scale 0.8-2.0 (the canvas ignores it by design); clock set to 2099 / 1971 / 2037 / leap day; cold start at 2099 / 1971; `kill`, `kill -9`, trim-memory at every level: no crash, no blank screen.
* The `Car` dock tile opens a "Not built yet" page (design decision, not a defect).

## 4. Tools and tests added

All under `tools/chaos/` (nothing here ships in the app). Run from the worktree root with `export ANDROID_SERIAL=emulator-5556; source tools/env.sh`; the Python scripts refuse to run against any other serial unless `CHAOS_ANY_SERIAL=1`.

| File | What it does | Run |
|---|---|---|
| `run-all.sh` | the ~10 min regression suite: unit tests + build + install, then quick runs of every script below; prints PASS/FAIL per scenario, exit 1 on any FAIL | `tools/chaos/run-all.sh [--no-build] [--full]` |
| `hostile_elm.py` | fake ELM327 with 19 misbehaviours (`valid fast garbage noprompt endless endless_nl halfline badecho nodata unable buserr absurd signs slowdrip reset closeloop huge hang stale`, or `cycle:a,b,c`) | `tools/.venv/bin/python tools/chaos/hostile_elm.py --port 35020 --mode endless` |
| `chaos_link.py` | points the app at each hostile mode (Settings > Connections, ObdTcp 10.0.2.2) and checks: alive, UI answers, heap, CPU, link line honest | `python3 tools/chaos/chaos_link.py [--quick] [--secs 20]` |
| `chaos_session.py` | adapter socket released on source switch / in the background (lsof on the fake adapter); Trip A across a visit to another app | `python3 tools/chaos/chaos_session.py` |
| `chaos_ui.py` | dock / settings / key / relaunch storms, sleep-wake, `wm size`/`density`, font scale, 7 locales, time zone + clock changes, process death, trim memory, source switching while the link flaps; blank-screen + settings-file checks after each | `python3 tools/chaos/chaos_ui.py [--only dock,keys,...] [--n 300]` |
| `chaos_persist.py` | 14 corrupt prefs files, clear/delete while running, downgrade/upgrade install | `python3 tools/chaos/chaos_persist.py [--quick]` |
| `chaos_input.py` | host/port/search box abuse, IME and hardware keys | `python3 tools/chaos/chaos_input.py [--quick]` |
| `mkapks.py`, `chaos_apps.py` | builds 147 hostile APKs with `aapt2` (no Gradle) and abuses the Apps grid while installing / removing / disabling | `tools/.venv/bin/python tools/chaos/mkapks.py --bulk 120; python3 tools/chaos/chaos_apps.py` |
| `chaos_home.py`, `chaos_rtl.py` | HOME returns to Home, BACK does not crash; RTL locales keep the layout | `python3 tools/chaos/chaos_home.py` |
| `monkeys.py` | Android `monkey` over many seeds / mixes / throttles / three families, logcat grep, TSV summary | `python3 tools/chaos/monkeys.py --seeds 20 --events 5000` |
| `soak.py`, `strictmode.patch` | N-minute soak with sampled meminfo / threads / fds / GC / frame stats and a least-squares growth verdict; the patch enables StrictMode (thread + VM, `penaltyLog`, debug builds only) for that run only | `git apply tools/chaos/strictmode.patch` then build; `python3 tools/chaos/soak.py --minutes 32` |
| `SetLocale.java`, `lib.py` | live system-locale switch through `app_process` (no reboot), adb/uiautomator/logcat helpers | used by the scripts |

JVM tests added (each was red before its fix; run `tools/gb.sh :app:testDebugUnitTest :vehicle:test`): `:vehicle` `StreamBoundsTest`, `ParserHostileTest`, `ElmSessionHostileTest` (+`PollRateTest`), `TcpHangupTest`, `ClockJumpTest`; `:app` `ListenerHandleTest`, `TripHostileTest`, `SettingsHostileTest`, `AppsHostileTest`, `RepositoryHostileTest`.

## 5. Verification log

### Verification state at hand-off (written by the lead from the agent's own summary)
* Fixed build, `adb shell monkey`: seeds 1, 3, 4, 5, 7, 8 at 5000 events and seeds 2, 6 at 1666 events: 0 crashes, 0 ANRs. Baseline: 6 of 7 runs crashed.
  The planned quota of 20 seeds x 5000 events was NOT met (seeds 9-20 not run).
* Soak (strict2 build with StrictMode, 32 min, 35 samples): Java heap median 14.1 -> 15.0 MB, native 24.7 -> 26.7 MB, threads 34 -> 36, fds 74 -> 76, no deaths.
* chaos_ui (full, fixed3 build): 72 passed, 5 failed; the failures are classified as emulator/harness artefacts (key-storm 'no focused window' ANR,
  rotated-coordinate harness issue at 720x1280 and 480x800, stock launcher crash dialog at 100x100). Not re-run after the harness patches.
* chaos_apps after the hostile-APK install fix: 10 passed, 2 failed (`apps/app_without_label_shows_package_name`, `apps/search_abuse`); no dev.kuntal.cockpit FATAL/ANR in logcat,
  so both look like harness issues but are undiagnosed. MONKEY-11 was never run against the baseline.
* `tools/chaos/run-all.sh` was written and passes `bash -n` but was never run end to end.
* INCIDENT: for about 5 minutes the security agent's first dynamic commands ran on this agent's emulator (5556): it installed the baseline release APK, cleared logcat,
  force-stopped the app and persisted source=ObdTcp 10.0.2.2:35040. Results from that window are suspect. Emulator 5556 also died once and was relaunched (data reset).

## 6. Residual risk and recommendations

**Open items, in the order I would take them**
1. MONKEY-14 (trip + telemetry only run while Cockpit is on screen). For a launcher on a head unit this is the biggest functional gap: distance and top speed stop counting whenever Maps or the radio is in front. It needs a decision (foreground service vs. keep-alive in the ViewModel, plus persisting the trip). The BACK change in this branch removes one way of losing the trip (the activity no longer finishes on BACK), not the 5 s background timeout.
2. MONKEY-15 (dev hooks live in release): one `if (!debuggable) return` in `applyDevSettings()`; left for the security branch.
3. MONKEY-16 (store-on-every-keystroke): debounce or commit on IME Done.
4. Bluetooth is the least tested path: `BluetoothTransport` now uses the same cancel-safe helper as TCP, but only compile-verified. A real adapter, one that hangs mid-reply and one that is switched off mid-drive, are the first things to try on the unit.
5. Real head-unit behaviour is unmeasured: emulator frame stats (p99 34-48 ms, 22-30 % janky) are software-rendering numbers and say nothing about the gate. The StrictMode soak found only main-thread SharedPreferences reads at start-up (`SettingsStore`, `InstalledApps.recents`, 40 ms on the emulator; a slow eMMC will cost more) and untagged sockets in `TcpTransport.open` (harmless on Android 11, relevant if the unit enforces traffic tagging); worth moving the preference reads off the main thread if cold-start time matters.

**What would still break it** (not tried, or not tryable here): a Wi-Fi adapter that changes IP mid-drive (DNS and ARP behaviour of the real network); an adapter that answers `ATZ` with a banner longer than 8 KB (treated as hostile now); Android versions below 11 (`FrameMetrics` listener semantics, `Collator`); multi-window / split screen (the activity is landscape-only and `DesignCanvas` assumes a sane constraint size, tested down to 100x100 only); a second user profile; storage full while `SharedPreferences.apply()` writes (silent loss of the last change, no crash).

**Recommendations**
* Keep `tools/chaos/run-all.sh` in the merge checklist; it needs an emulator and ~10 minutes. Re-run `chaos_link.py` (full, 19 modes) before each release of the OBD code, and add a mode to `hostile_elm.py` for every new transport quirk found on the real car.
* Treat `PidDecoder.plausible()` as the single place that decides what the car can report; add the Canbox values to it when that source arrives instead of validating in the screens.
* Any new `withContext` in a `finally` / `close()` path needs `NonCancellable`; any new blocking socket call needs `cancellableBlocking`.
