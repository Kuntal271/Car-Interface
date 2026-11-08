# feature: build report (M3 screens over mock sources)

Branch `review/feature`, worktree `~/ecosport-wt/feature`. Emulator `emulator-5558` (Android 11 arm64, software GL, host load 20-30, so timings are indicative only).

## 1. Summary

- **Verdict:** every design screen now exists in the app. 17 screens are app-owned (car, media, trips, parking, diagnostics, sound, quick panel, overlays x8,
  boot, standby, drive-boost, drive-cruise); 4 are in-app **demo stand-ins** for vendor apps that stay stock (phone, phone-call, radio, projection).
  Nothing that needs the car or the Canbox is faked as real: each such screen sits on a small interface with a deterministic mock, is labelled "Demo data",
  and says "Waiting for ..." when Settings -> Connections -> Canbox and media is Off.
- **Safety:** `vehicle/` is byte-identical to the base commit (`git diff 434a2a4 -- vehicle` is empty). `CommandGuard` is untouched and no command was added.
  Diagnostics shows an invented, labelled fault list because reading DTCs is mode 03, which the app does not send.
- **Gates:** `tools/gb.sh :app:testDebugUnitTest :vehicle:test` green, 235 tests (was 122), 0 failures, 1 skipped (the existing `TcpIntegrationTest`, needs a fake adapter).
  Drive 2.81 % and Home 3.34 % unchanged. 15 of the 22 new snapshot comparisons are <= 5 %; the rest are 5.1-9.5 % (reasons in section 3).
- Counts by severity: 0 CRITICAL, 0 HIGH, 2 MEDIUM (1 fixed, 1 open), 4 LOW (3 fixed, 1 open), 4 INFO.
- Top issues: FEATURE-01 (fixed: whole-app recomposition from the overlay controller), FEATURE-02 (open: no real Canbox / media / DSP / DTC source yet),
  FEATURE-03 (open: six screens above the 5 % comparison target), FEATURE-04 (open: frame cost not measured on the real unit), FEATURE-05 (fixed: clipped title).

## 2. Scope and method

Built in priority order, each screen from `docs/design/site/screens/*.html`: geometry dumped from headless Chrome (`tools/geom.py`, `geomp.py`, `geomt.py`),
ported to Compose with the existing primitives (`Modifier.at`, `Txt`, `LiveTxt`, `card`, `pill`, `SegSlider`, `StaticLayer`), checked with `tools/snap.sh` +
`tools/cmp.py` and a side-by-side vision review (`tools/sbs.py`: design | app | diff). Both themes were looked at for a sample of screens (Midnight shots in `docs/shots/m3-midnight-*.png`).

Mock architecture (`app/src/main/java/dev/kuntal/cockpit/data/`): `CanboxSource`/`MockCanbox`, `RadarSource`, `MediaSource`/`MockMediaSource`, `AudioSettings`/`MockAudioSettings`,
`DiagSource`/`MockDiagSource`, `TripStore` (`FileTripStore` real + `MockTripStore` demo, `CompositeTripStore`), `RadioState` (pure reducer), phone demo data.
Mocks are pure functions of time and a seed, so they run in JVM tests and in frozen snapshots. `CockpitData` holds them; with `demoData` off each mock source is `null`.
The setting `demoData` (default on) has a codec, tests and a Connections row. Status bar: OBD reads "OBD sim" for the simulator, Canbox reads "Canbox sim" while demo data is on.
Mock numbers never reach Drive or Home as OBD values.

Not done / out of scope: the real Canbox frame decoder, a real media session, DSP control, DTC reading, a real call/tuner/projection link, measuring on the head unit,
a Midnight pass over every screen (done for car, media, parking, sound, drive-boost, quick only).

## 3. Findings

**FEATURE-01 MEDIUM, FIXED.** The overlay controller kept its queue "version" in Compose state that `CockpitApp` read, so every overlay change recomposed the whole app, and the
coolant rule fired on every temperature change. With the Simulator running, even the Apps screen (no live data) drew 18 fps. Fix: a `MutableSharedFlow` kicker instead of state
(`ui/shell/OverlayHost.kt`). Evidence (`tools/perf.sh apps`): before `fps=18.0 frames=180`, after `fps=1.4 frames=14` over 10 s. Found by measuring, not by reading.

**FEATURE-02 MEDIUM, OPEN.** Car, Parking, Media, Sound, Diagnostics faults, Trips demo rows, Phone, Radio and Projection run on mocks. Replacement points are the interfaces above;
the Canbox frame layout is in `docs/CANBOX-FRAMES.md` and `tools/canbox_decode.py`. Proposal: implement `CanboxSource` over the real serial/USB reader and switch `CockpitData` to it
when the unit exists. Radio/phone/projection are meant to stay vendor apps: demo mode is the only time the stand-ins show; with demo data off the old launch-by-intent behaviour is kept.

**FEATURE-03 LOW, OPEN.** Above the 5 % target (compare numbers in section 5): trips 5.31, diagnostics 5.69, drive-boost 5.13, phone 5.69, radio 6.62, projection 5.35, phone-call 5.08,
and overlay3/5/8 (7.47 / 9.47 / 7.25, almost all of it the design backdrop: Drive in a different live state; the overlay cards match). Reviewed in the diff images: remaining differences are
1-3 px text offsets and the weight difference of Barlow Condensed 800 vs the bundled Bold, plus data that is live on purpose (diagnostics adapter card). No structural gap is known.
Settings (9.55 %) and Apps (7.46 %): Settings gained three rows the design lacks (Start-up splash, Standby clock, Canbox and media); Apps was not touched, its list is the emulator's.

**FEATURE-04 LOW, OPEN.** Frame cost was only measured on the emulator. Idle frame rates are good (section 5) but `app-cpu p99` is 5-35 ms under host load 20-30 and software GL, which says nothing
about the head unit. Needs a run on the real unit with `tools/perf.sh`.

**FEATURE-05 LOW, FIXED.** Media title "Midnight City": the `y` descender was clipped by `TextOverflow.Clip` (line height 61 sp). Found in the Midnight screenshot by vision review; fixed with `overflow = Visible`,
re-checked in `docs/shots/m3-midnight-media.png`.

**FEATURE-06 LOW, FIXED.** Drive-boost: the "Stop to arm" pill overran the card edge. Now right-aligned to the card padding.

**FEATURE-07 LOW, FIXED (caught early).** The demo trip generator used `LocalDate.ofInstant` (API 33+), which crashes on the target API range; the first Car snapshot showed 68 % difference because the app had crashed
(logcat). Replaced by `Instant.atZone(zone).toLocalDate()`.

**FEATURE-08 INFO.** Mock-triggered overlays are deliberately suppressed: with demo data on, a simulated open door or climate change never interrupts a real drive (door and climate overlays only fire from a non-mock Canbox).
The reverse overlay and the incoming-call overlay exist and render, but nothing triggers them yet (no real reverse/call source); the shift-up banner is not auto-triggered because Drive has its own shift light.

**FEATURE-09 INFO.** Quick panel: Wi-Fi, Bluetooth and Hotspot show the real state (Wi-Fi/BT) and open the system page, because a launcher cannot switch them; Night is an in-app dim layer (45 % black); Power menu is a notice,
not a power menu; Screen off starts Standby. Standby sleeps only while parked (speed < 3 km/h or unknown) and is Off by default; "hold to power off" is not implemented.

**FEATURE-10 INFO.** Cruise/Boost faces are real OBD data, not mock. Fuel use is an estimate (speed-density: MAP, rpm, intake temperature, VE 0.85, stoich) and is labelled "estimated"; range assumes a 45 L tank
(`FuelEstimate.TANK_L`, to become a setting). Run history of the 0-100 timer lives for the session only.

## 4. Tools and tests added

- `tools/ship.sh <screen>...` (build, install, snapshot, compare), `tools/b.sh` (debug build + install, prints compile errors), `tools/perf.sh <screen>...` (idle fps, gfx percentiles, app-cpu),
  `tools/geom.py`, `geomp.py`, `geomt.py` (design geometry dumps), `tools/sbs.py` (design | app | diff image).
- New unit tests (JVM): `CanboxTest`, `MediaTest`, `AudioSettingsTest`, `TripsTest`, `DiagnosticsTest`, `OverlaysTest`, `StandbyBootTest`, `DriveVariantsTest`, `EstimatesTest`, `PhoneRadioDemoTest`, settings codec cases in `SettingsTest`.
  Run: `tools/gb.sh :app:testDebugUnitTest :vehicle:test`.
- Snapshots: `adb shell am start -n dev.kuntal.cockpit/.MainActivity --es screen <name> --ez snapshot true` with `<name>` one of car media trips parking diagnostics sound quick standby boot drive-boost drive-cruise
  phone phone-call radio projection overlay1..overlay8. Fixtures are in `ui/SnapshotFixtures.kt`. Live: omit `--ez snapshot true`; add `--ez noboot true` to skip the splash on a plain launch.
- Screenshots: `docs/shots/m3-*.png` (Ford for every screen; Midnight for car, media, parking, sound, drive-boost, quick; demo-off empty states `m3-waiting-*.png`).
- `docs/screens-status.md`: the per-screen table.

## 5. Verification log

Tests: `tools/gb.sh :app:testDebugUnitTest :vehicle:test` -> BUILD SUCCESSFUL; JUnit XML totals: tests 235, failures 0, errors 0, skipped 1 (`TcpIntegrationTest`, pre-existing).

Safety: `git diff 434a2a4 -- vehicle | wc -l` -> 0.

Snapshot comparison (`tools/cmp.py`, % visibly different pixels): drive 2.81, home 3.34, car 3.26, media 3.52, trips 5.31, parking 2.11, diagnostics 5.69, sound 3.55, quick 3.41, standby 4.98, boot 1.39,
overlay1 2.77, overlay2 3.19, overlay3 7.47, overlay4 3.21, overlay5 9.47, overlay6 1.76, overlay7 2.71, overlay8 7.25, drive-boost 5.13, drive-cruise 2.74, phone 5.69, phone-call 5.08, radio 6.62, projection 5.35;
apps 7.46 and settings 9.55 (see FEATURE-03).

Idle frames per second (`tools/perf.sh`, 10 s after a 6 s warm-up, Simulator on, Demo data on): car 2.7, media 2.4, trips 1.6, parking 1.4, sound 1.4, standby 2.0, phone 1.4, radio 1.3, projection 1.4, apps 1.4;
live-data screens redraw by design: home 17.8, diagnostics 18.0, drive-boost 17.6, drive-cruise 18.4, drive 34.9. With no live OBD samples (a dead TCP source) every screen measured 1.1-3.2 fps.
In-app frame meter, same runs, `app-cpu p50` 2.0-5.4 ms (emulator, loaded host: indicative only; p99 5-35 ms, not meaningful against the 10 ms gate).

Demo off (`m3-waiting-car.png`, vision check): status bar Canbox dot is grey and the Car screen shows only "Waiting for Canbox" with no numbers.

## 6. Residual risk and recommendations

1. Replace mocks with real sources as they appear (FEATURE-02); the labelled demo stays useful for bench work.
2. Run `tools/perf.sh` and the 10-minute drive-cycle gate on the real unit (FEATURE-04); the Boost arc, Parking guidelines and Standby clock are the heaviest new draw paths.
3. Decide whether DTC reading (mode 03) should ever be allowed; this branch deliberately does not touch that guard.
4. `FuelEstimate.TANK_L` and the volumetric-efficiency constant should become settings once real fuel data can be compared with it.
5. The Boost card tap and a horizontal swipe are the only ways to reach the Boost and Cruise faces (the Cluster pixel match was kept); a visible switch could be added if the owner accepts a small diff.
