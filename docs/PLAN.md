# EcoSport Cockpit — head-unit UI/UX revamp (Blaupunkt Palm Bay 1000)

Status: DRAFT v1 (plan only, nothing built). Date: 2026-10-30.
New standalone project. It lives in its own directory and its own git repo: /Users/kuntal/ecosport-cockpit
It shares no code, repo, remote or docs with any other project on this Mac.

## REVISION 2 (2026-10-30, supersedes conflicting text below)

1. Palette and type are LOCKED by the owner: midnight black #05070D background, electric cobalt #2457FF for the lows, acid lime #C8FF00 for highlights; strict three-colour theme; font-specific typography (Big Shoulders Display + Space Grotesk + JetBrains Mono). Full tokens, rules and patterns: docs/DESIGN.md. This replaces the colours/type in task 2.1; the dock is a RIGHT-edge rail (India is right-hand drive). Warnings are inverted-lime blocks, no red (owner may overrule).
2. The car already has a Canbox, so vehicle data has three sources, not one: (a) Canbox via the head unit's vendor service: doors, A/C, parking radar, steering keys, outside temp, and rpm/speed only if the dialect carries them; (b) ELM327 OBD adapter: boost (MAP-baro), coolant, intake, load, throttle, battery volts, fuel — the Canbox can never provide these, so M1/M6 stay; (c) optional UART tap for ground truth. Details: docs/CANBOX-FRAMES.md. New "Car" screen (doors, A/C, radar, outside temp).
3. "Frames first": no screen mocks (M2) until the Canbox questions in docs/CANBOX-FRAMES.md section 7 are answered. Order is now: M0 bootstrap -> M4a step zero (unit/canbox identification, firmware package analysis) -> M1 vehicle engine -> M4b adb recon -> M2 mocks -> M3 app. M4a = ROOT-ASSESSMENT.md steps 0a-0c plus CANBOX-FRAMES.md capture methods A and B (C when hardware is in hand).
4. Root: owner is open to it. Posture unchanged: non-root path first; root only after D1-D8 in docs/ROOT-ASSESSMENT.md and with a tested recovery. Estimated effort band 1/2/3 is decided by step zero.
5. Done so far: repo ~/ecosport-cockpit created; tools/canbox_decode.py (Raise + Hiworld parser, checksum resync, census; 10 unit tests green); docs/CANBOX-FRAMES.md, docs/ROOT-ASSESSMENT.md, docs/DESIGN.md.
6. New owner questions: which Canbox brand/version does Car Info show; does the stock UI show RPM and speed, and which other live values; can you download the firmware package for the unit (the dealer "hard update" file).

## Goal

Replace the stock, generic launcher/UI of the owner's Blaupunkt Palm Bay 1000 Android head unit (2015 Ford EcoSport 1.0 EcoBoost, India) with a modern, glanceable "Cockpit" launcher + live-vehicle dashboard (RPM, speed, boost, coolant, battery, fuel, trip data), developed and previewed fully on the Mac first (Android emulator at the unit's resolution), then installed on the real unit.

## Standing rules for whoever implements this

1. Separate project. Do not read from, write to, or branch off any other repo. Toolchain versions below are simply "known to build on this Mac"; copy the numbers, not files.
2. Autonomous end-to-end execution; no mid-task permission prompts. Final report per item: done+verified / done-unverified / not done, with files changed. Owner steers with terse messages; apply immediately.
3. Do not launch the Claude Code CLI or spend the owner's Claude subscription (the CLI is deliberately logged out). "Another agent" means working with your own tools / sub-agents.
4. Car safety: the app is READ-ONLY toward the vehicle. It may send ELM327 `AT...` configuration commands and OBD Mode 01 reads only. Never Mode 03/04/07/0A (DTC read/clear), never 2x/3x/UDS writes, never `ATPP`. This is enforced in code by CommandGuard (task 1.3) with a test.
5. Anything that changes the head unit's system state (disabling vendor apps, flashing, rooting) needs: a written rollback, the stock firmware/recovery path confirmed, and the owner's explicit go. Sideloading our own APK and setting it as HOME is non-destructive and pre-approved.
6. Features that could interrupt a passenger's viewing or power things off need an owner decision first (use clarify; if unanswered, park the feature and continue). Speed-based input lockout is PARKED for this reason.
7. Git: local commits as you go, one logical change per commit, green tests between commits. No remote is created or pushed until the owner says so. If/when a remote is wanted, it is a PERSONAL-account repo (Kuntal271), pushed via the SSH alias `github-kuntal271`, never the work account.
8. Raw device dumps may contain Wi-Fi/BT identifiers. They go in docs/recon/raw/ which is gitignored. Only the hand-written docs/RECON.md summary is committed.

## Context (what is known, what is not)

Naming: the unit is Blaupunkt (not "Blockpunk") Palm Bay 1000.

Head unit (from retailer listings + reviewer videos; NOT yet verified on the owner's unit):
- 9" (also sold in 10.1") IPS capacitive, 1280x800, 2.5D glass.
- Android 11; octa-core 2.0 GHz (a service video tags the platform "A53C", i.e. probably Cortex-A53 class: modest CPU/GPU); 2GB/32GB or 4GB/64GB variants; BT 5.0; Wi-Fi 2.4/5 GHz.
- Certified wireless Apple CarPlay + Android Auto (vendor app, we do NOT reimplement it).
- 2x rear USB-A, steering-wheel-control input, front/rear camera + 360 input, 8-band EQ/DSP, OTA app, USB "hard update" firmware path (FAT32 stick; vendor warns to match platform + RAM variant + firmware version).
- Sideloading appears possible after a hidden unlock inside Settings that ends in an "unknown apps: allow" toggle (reviewer video; exact steps must be found on the real unit in M4).
- MRP ~Rs 18,990; 2-year warranty (rooting/flashing likely voids it; owner accepted this).

Vehicle (Racelogic CAN database for EcoSport 2013-, which is the same global platform as the India car):
- All signals are reachable on the OBD-II connector, CAN-H pin 6 / CAN-L pin 14, 500 kbit/s.
- Standard OBD-II PIDs for RPM (01 0C) and speed (01 0D) work through any ELM327-class adapter.
- The raw CAN broadcast additionally has: gear, steering angle, TPMS pressures (4), wheel speeds (4), odometer, lateral/longitudinal acceleration, accelerator pedal, fuel level, boost. Those need a passive CAN sniffer, not an ELM327 (stretch goal, M7).

Unknowns that drive the design (all answered by recon, M4):
- U1. How does the unit get RPM/speed TODAY? Candidates: (a) a plug-in OBD adapter + an app on the unit, (b) a CAN-decoder box in the harness feeding a vendor service, (c) the owner's phone. This decides whether we re-use the vendor data path or bring our own OBD adapter.
- U2. Actual Android version (11 vs 13 seen in one video), screen density, SoC/GPU, RAM, root/adbd state, bootloader state.
- U3. Which vendor components must stay reachable (Radio, BT phone, EQ, camera, CarPlay/AA, settings) and their exact component names.

## Approach

A normal Android app that is a HOME (launcher) replacement, written in Kotlin + Jetpack Compose (foundation + ui only, custom design system, no Material3, no blur). It leaves vendor apps in place and launches them via explicit intents (Radio, BT phone, EQ, camera, CarPlay/AA) — those talk to the unit's MCU through proprietary services we cannot safely re-skin. Our value: Home, Drive dashboard with real vehicle data, Media (any app that publishes a MediaSession), Apps, Settings.

Vehicle data comes from a pure-Kotlin `:vehicle` module (JVM, fast TDD on the Mac) behind one interface, `VehicleSource`, with interchangeable implementations: SimSource (deterministic drive cycle, default on emulator), ReplaySource (recorded real drives), ElmClient over a Transport (TCP for the Mac-side ELM327 emulator; Bluetooth-SPP / BLE / USB-serial on the real unit).

Escalation ladder (stop at the first rung that works): 1) plain app + adb-granted permissions; 2) disable/replace vendor launcher with `pm disable-user` (reversible); 3) vendor engineering-mode / `adb root` if the build allows; 4) Magisk-patched boot image (only after M4 shows we are actually blocked; highest brick risk).

Dev loop: emulator "palmbay" AVD (API 30, 1280x800, 160 dpi, 2GB) -> screenshots (`adb exec-out screencap`) -> review -> iterate. The emulator is far faster than the real SoC, so performance is only validated on the real unit (M5 gates).

Toolchain on this Mac (verified present): Apple M2 arm64; JDK17 /opt/homebrew/opt/openjdk@17; Android cmdline-tools at /opt/homebrew/share/android-commandlinetools (build-tools 34/35, platform android-35, platform-tools/adb); NO emulator and NO system images yet (task 0.3); brew gradle; python3 3.9.6; node/npm. Known-good versions: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, coroutines 1.9.0, lifecycle 2.8.7, activity 1.9.3.

## Repo layout (target)

    ecosport-cockpit/
      settings.gradle.kts  build.gradle.kts  gradle.properties  local.properties(ignored)
      gradle/libs.versions.toml  gradle/wrapper/*  gradlew
      vehicle/            pure Kotlin/JVM: Pid, PidDecoder, ElmParser, CommandGuard, Transport,
                          StreamExchange, TcpTransport, ElmClient, PollSchedule, VehicleSample,
                          DriveCycle, SimSource, ReplaySource, TripComputer
      app/                Android: MainActivity (HOME), ui/ (theme, gauges, screens), data/ (repository,
                          settings), platform/ (BT/USB transports, MediaSession, vendor shortcuts)
      tools/              env.sh, avd.sh, elm-emu.sh, install.sh, shot.sh
      docs/               DESIGN.md, RECON.md, design/*.html (mocks), shots/*.png, recon/raw/ (ignored)

## Milestones

  M0 Bootstrap (toolchain, emulator, empty build)         ~half day
  M1 Vehicle engine, TDD, no UI                           ~1 day
  M2 Design mocks in the browser -> owner picks direction ~1 day, in parallel with M1
  M3 Compose app on emulator with simulated data          ~3-4 days   <- the owner's "first look"
  M4 Recon on the real unit (non-destructive)             ~half day when at the car; do it EARLY
  M5 Install on the unit as HOME with simulated data      ~half day
  M6 Real car data over OBD + record/replay               ~2 days + adapter delivery
  M7 Optional: passive CAN sniffer, vendor-service hooks, root   decide after M5/M6

------------------------------------------------------------------------------
## M0 — Bootstrap

### 0.1 Create repo
    mkdir -p ~/ecosport-cockpit && cd ~/ecosport-cockpit && git init -b main
Expected: `Initialized empty Git repository in /Users/kuntal/ecosport-cockpit/.git/`

Create .gitignore:
    build/
    .gradle/
    .idea/
    local.properties
    *.apk
    *.hprof
    tools/.venv/
    docs/recon/raw/
    .DS_Store

Create README.md (3 lines: what it is, "see docs/", "plan: docs/PLAN.md" — copy this file to docs/PLAN.md).
Commit: `git add -A && git commit -m "chore: init repo"`

### 0.2 Environment script — tools/env.sh
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
    export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
    export ANDROID_HOME=$ANDROID_SDK_ROOT
    export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
Verify: `source tools/env.sh && java -version 2>&1 | head -1` -> a line containing `17.`
Also write local.properties: `sdk.dir=/opt/homebrew/share/android-commandlinetools`

### 0.3 Install emulator + Android 11 arm64 image
    source tools/env.sh
    yes | sdkmanager --licenses > /dev/null
    sdkmanager "emulator" "platforms;android-30" "system-images;android-30;google_apis;arm64-v8a"
Verify: `sdkmanager --list_installed | grep -E "emulator|android-30"` shows 3 entries.
If the image name is not offered: `sdkmanager --list | grep "system-images;android-3[01]"` and use the closest google_apis arm64-v8a image (API 30 preferred, 31 acceptable); update tools/avd.sh accordingly.

### 0.4 AVD that matches the unit — tools/avd.sh
    #!/usr/bin/env bash
    set -euo pipefail
    source "$(dirname "$0")/env.sh"
    AVD=palmbay
    if ! avdmanager list avd -c | grep -qx "$AVD"; then
      echo no | avdmanager create avd -n "$AVD" -k "system-images;android-30;google_apis;arm64-v8a" -d pixel_c
    fi
    CFG="$HOME/.android/avd/$AVD.avd/config.ini"
    python3 - "$CFG" <<'PY'
    import sys
    p = sys.argv[1]
    want = {"hw.lcd.width":"1280","hw.lcd.height":"800","hw.lcd.density":"160","hw.ramSize":"2048",
            "hw.cpu.ncore":"4","hw.initialOrientation":"landscape","hw.keyboard":"yes","skin.name":"1280x800"}
    keep = [l for l in open(p).read().splitlines() if l.split("=")[0].strip() not in list(want) + ["skin.path"]]
    open(p, "w").write("\n".join(keep + [f"{k}={v}" for k, v in want.items()]) + "\n")
    PY
Run and verify:
    chmod +x tools/avd.sh && tools/avd.sh
    source tools/env.sh && (emulator -avd palmbay -no-snapshot-save -no-audio > /tmp/emu.log 2>&1 &)
    adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
    adb shell wm size; adb shell wm density
Expected: `Physical size: 1280x800` and `Physical density: 160`. (After M4, replace 160 with the unit's real density.)
Commit: "chore: emulator tooling".

### 0.5 Gradle skeleton
Files (exact):

settings.gradle.kts
    pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories { google(); mavenCentral() }
    }
    rootProject.name = "ecosport-cockpit"
    include(":vehicle")
    // include(":app") is added in task 3.1

build.gradle.kts
    plugins {
        alias(libs.plugins.android.application) apply false
        alias(libs.plugins.kotlin.android) apply false
        alias(libs.plugins.kotlin.compose) apply false
        alias(libs.plugins.kotlin.jvm) apply false
    }

gradle.properties
    org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
    org.gradle.parallel=true
    org.gradle.caching=true
    android.useAndroidX=true
    android.nonTransitiveRClass=true
    kotlin.code.style=official

gradle/libs.versions.toml
    [versions]
    agp = "8.7.3"
    kotlin = "2.1.0"
    composeBom = "2024.12.01"
    activity = "1.9.3"
    lifecycle = "2.8.7"
    coroutines = "1.9.0"
    coreKtx = "1.15.0"
    junit = "4.13.2"
    [libraries]
    androidx-core = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
    compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
    compose-ui = { module = "androidx.compose.ui:ui" }
    compose-foundation = { module = "androidx.compose.foundation:foundation" }
    activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
    lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
    lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
    coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
    coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
    coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
    junit = { module = "junit:junit", version.ref = "junit" }
    [plugins]
    android-application = { id = "com.android.application", version.ref = "agp" }
    kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
    kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
    kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }

vehicle/build.gradle.kts
    plugins { alias(libs.plugins.kotlin.jvm) }
    kotlin { jvmToolchain(17) }
    dependencies {
        implementation(libs.coroutines.core)
        testImplementation(libs.junit)
        testImplementation(libs.coroutines.test)
    }

Bootstrap the wrapper and verify (redirect to a log; never read status through a pipe):
    source tools/env.sh
    mkdir -p vehicle/src/main/kotlin/dev/kuntal/cockpit/vehicle vehicle/src/test/kotlin/dev/kuntal/cockpit/vehicle
    gradle wrapper --gradle-version 8.11.1 --distribution-type bin > /tmp/wrap.log 2>&1; echo EXIT=$?
    ./gradlew --version > /tmp/gv.log 2>&1; grep -E "^Gradle " /tmp/gv.log
    ./gradlew :vehicle:test > /tmp/b.log 2>&1; E=$?; grep -E "BUILD|^e: " /tmp/b.log; echo GRADLE_EXIT=$E
Expected: `Gradle 8.11.1`, then `BUILD SUCCESSFUL` and `GRADLE_EXIT=0` (no tests yet).
Commit: "chore: gradle skeleton + :vehicle module".

------------------------------------------------------------------------------
## M1 — Vehicle engine (pure Kotlin, TDD)

Package: dev.kuntal.cockpit.vehicle. Each task: write the test file -> run -> see it FAIL (compile error "Unresolved reference") -> write the implementation -> run -> PASS -> commit.
Run a single test class:
    ./gradlew :vehicle:test --tests '*ElmParserTest*' > /tmp/t.log 2>&1; E=$?; grep -E "BUILD|FAILED|^e: " /tmp/t.log; echo GRADLE_EXIT=$E

### 1.1 Pid + decoder + parser + sample
Test: vehicle/src/test/kotlin/dev/kuntal/cockpit/vehicle/ElmParserTest.kt
    package dev.kuntal.cockpit.vehicle

    import org.junit.Assert.*
    import org.junit.Test

    class ElmParserTest {
        @Test fun rpm() = assertEquals(1726.0, ElmParser.parse(Pid.RPM, "41 0C 1A F8")!!, 0.001)
        @Test fun speedNoSpaces() = assertEquals(50.0, ElmParser.parse(Pid.SPEED, "410D32")!!, 0.001)
        @Test fun coolant() = assertEquals(83.0, ElmParser.parse(Pid.COOLANT_TEMP, "41 05 7B")!!, 0.001)
        @Test fun throttle() = assertEquals(50.196, ElmParser.parse(Pid.THROTTLE, "41 11 80")!!, 0.001)
        @Test fun voltage() = assertEquals(13.548, ElmParser.parse(Pid.MODULE_VOLTAGE, "41 42 34 EC")!!, 0.0001)
        @Test fun skipsSearchingBanner() =
            assertEquals(50.0, ElmParser.parse(Pid.SPEED, "SEARCHING...\r41 0D 32\r")!!, 0.001)
        @Test fun noData() = assertNull(ElmParser.parse(Pid.SPEED, "NO DATA"))
        @Test fun wrongPid() = assertNull(ElmParser.parse(Pid.SPEED, "41 0C 1A F8"))
        @Test fun truncated() = assertNull(ElmParser.parse(Pid.RPM, "41 0C 1A"))
        @Test fun supportedBitmap() {
            val bytes = ElmParser.parseBitmap(0x00, "41 00 BE 3F A8 13")!!
            assertEquals(
                setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                      0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20),
                PidDecoder.supportedFrom(0x00, bytes),
            )
        }
        @Test fun boostIsMapMinusBaro() {
            val s = VehicleSample(tsMs = 0, mapKpa = 150.0, baroKpa = 100.0)
            assertEquals(50.0, s.boostKpa!!, 0.001)
        }
    }

Implementation, vehicle/src/main/kotlin/dev/kuntal/cockpit/vehicle/Pid.kt:
    package dev.kuntal.cockpit.vehicle

    /** OBD-II Mode 01 PIDs we read. `bytes` = payload length of the reply. */
    enum class Pid(val code: Int, val bytes: Int) {
        ENGINE_LOAD(0x04, 1), COOLANT_TEMP(0x05, 1), MAP(0x0B, 1), RPM(0x0C, 2), SPEED(0x0D, 1),
        INTAKE_TEMP(0x0F, 1), THROTTLE(0x11, 1), FUEL_LEVEL(0x2F, 1), BARO(0x33, 1),
        MODULE_VOLTAGE(0x42, 2), AMBIENT_TEMP(0x46, 1), OIL_TEMP(0x5C, 1);

        val request: String get() = "01%02X".format(code)
    }

    object PidDecoder {
        fun decode(pid: Pid, d: IntArray): Double = when (pid) {
            Pid.RPM -> (d[0] * 256 + d[1]) / 4.0
            Pid.SPEED, Pid.MAP, Pid.BARO -> d[0].toDouble()
            Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.AMBIENT_TEMP, Pid.OIL_TEMP -> d[0] - 40.0
            Pid.ENGINE_LOAD, Pid.THROTTLE, Pid.FUEL_LEVEL -> d[0] * 100.0 / 255.0
            Pid.MODULE_VOLTAGE -> (d[0] * 256 + d[1]) / 1000.0
        }

        /** 4-byte "supported PIDs" bitmap for the range starting after [base] (0x00, 0x20, 0x40...). */
        fun supportedFrom(base: Int, bytes: IntArray): Set<Int> {
            val out = mutableSetOf<Int>()
            for (i in 0 until 32) if ((bytes[i / 8] shr (7 - i % 8)) and 1 == 1) out += base + i + 1
            return out
        }
    }

ElmParser.kt:
    package dev.kuntal.cockpit.vehicle

    object ElmParser {
        private fun bytesAfter(header: String, n: Int, raw: String): IntArray? {
            for (line in raw.lineSequence()) {
                val h = line.filterNot { it.isWhitespace() }.uppercase()
                if (!h.startsWith(header)) continue
                val payload = h.substring(header.length)
                if (payload.length < n * 2) continue
                val out = IntArray(n)
                var ok = true
                for (i in 0 until n) {
                    val v = payload.substring(i * 2, i * 2 + 2).toIntOrNull(16)
                    if (v == null) { ok = false; break }
                    out[i] = v
                }
                if (ok) return out
            }
            return null
        }

        fun parse(pid: Pid, raw: String): Double? =
            bytesAfter("41%02X".format(pid.code), pid.bytes, raw)?.let { PidDecoder.decode(pid, it) }

        fun parseBitmap(base: Int, raw: String): IntArray? = bytesAfter("41%02X".format(base), 4, raw)
    }

VehicleSample.kt:
    package dev.kuntal.cockpit.vehicle

    data class VehicleSample(
        val tsMs: Long,
        val rpm: Double? = null, val speedKmh: Double? = null,
        val coolantC: Double? = null, val intakeC: Double? = null, val ambientC: Double? = null, val oilC: Double? = null,
        val mapKpa: Double? = null, val baroKpa: Double? = null,
        val throttlePct: Double? = null, val loadPct: Double? = null, val fuelPct: Double? = null,
        val batteryV: Double? = null,
    ) {
        /** Gauge boost in kPa (negative = vacuum). Null until both MAP and BARO have been read. */
        val boostKpa: Double? get() = if (mapKpa != null && baroKpa != null) mapKpa - baroKpa else null
    }

    fun VehicleSample.with(pid: Pid, v: Double): VehicleSample = when (pid) {
        Pid.RPM -> copy(rpm = v)
        Pid.SPEED -> copy(speedKmh = v)
        Pid.COOLANT_TEMP -> copy(coolantC = v)
        Pid.INTAKE_TEMP -> copy(intakeC = v)
        Pid.AMBIENT_TEMP -> copy(ambientC = v)
        Pid.OIL_TEMP -> copy(oilC = v)
        Pid.MAP -> copy(mapKpa = v)
        Pid.BARO -> copy(baroKpa = v)
        Pid.THROTTLE -> copy(throttlePct = v)
        Pid.ENGINE_LOAD -> copy(loadPct = v)
        Pid.FUEL_LEVEL -> copy(fuelPct = v)
        Pid.MODULE_VOLTAGE -> copy(batteryV = v)
    }
Expected after implementing: `BUILD SUCCESSFUL`, 11 tests passed (check /tmp/t.log or vehicle/build/reports/tests/test/index.html).
Commit: "feat(vehicle): PID decode, ELM reply parser, VehicleSample".

### 1.2 DriveCycle (simulated data) + SimSource
Test: DriveCycleTest.kt
    package dev.kuntal.cockpit.vehicle

    import org.junit.Assert.*
    import org.junit.Test

    class DriveCycleTest {
        @Test fun idleAtStart() {
            val s = DriveCycle.at(0)
            assertEquals(0.0, s.speedKmh!!, 0.01); assertEquals(850.0, s.rpm!!, 0.01)
        }
        @Test fun cruise() = assertEquals(130.0, DriveCycle.at(50_000).speedKmh!!, 0.01)
        @Test fun boostWhileAccelerating() = assertTrue(DriveCycle.at(20_000).boostKpa!! > 20.0)
        @Test fun vacuumOnOverrun() = assertTrue(DriveCycle.at(65_000).boostKpa!! < 0.0)
        @Test fun rpmStaysInRange() {
            for (ms in 0..100_000 step 100) {
                val r = DriveCycle.at(ms.toLong()).rpm!!
                assertTrue("rpm=$r at $ms", r in 850.0..6500.0)
            }
        }
    }
Implementation DriveCycle.kt (numbers are illustrative, not calibrated to the car):
    package dev.kuntal.cockpit.vehicle

    object DriveCycle {
        const val PERIOD_S = 100.0
        private val rpmPerKmh = doubleArrayOf(150.0, 80.0, 55.0, 42.0, 33.0, 28.0) // gears 1..6
        private val upshift = doubleArrayOf(30.0, 55.0, 80.0, 105.0, 130.0)

        fun speedAt(tSec: Double): Double {
            val t = ((tSec % PERIOD_S) + PERIOD_S) % PERIOD_S
            return when {
                t < 45 -> 130.0 * t / 45.0
                t < 60 -> 130.0
                t < 85 -> 130.0 * (1 - (t - 60) / 25.0)
                else -> 0.0
            }
        }

        fun at(tMs: Long): VehicleSample {
            val t = tMs / 1000.0
            val v = speedAt(t)
            val gearIdx = upshift.count { v >= it }          // 0..5
            val rpm = maxOf(850.0, v * rpmPerKmh[gearIdx])
            val accelerating = speedAt(t + 0.5) - speedAt(t - 0.5) > 0.1
            val baro = 100.0
            val boost = if (accelerating) 60.0 * minOf(1.0, rpm / 3000.0) else -60.0
            return VehicleSample(
                tsMs = tMs, rpm = rpm, speedKmh = v,
                coolantC = minOf(92.0, 45.0 + t * 0.6), intakeC = 38.0, ambientC = 31.0,
                baroKpa = baro, mapKpa = baro + boost,
                throttlePct = if (accelerating) 55.0 else if (v > 0) 8.0 else 0.0,
                loadPct = if (accelerating) 70.0 else 20.0,
                fuelPct = maxOf(5.0, 62.0 - t * 0.01), batteryV = 14.2,
            )
        }
    }
VehicleSource.kt + SimSource.kt:
    package dev.kuntal.cockpit.vehicle

    import kotlinx.coroutines.delay
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.flow

    interface VehicleSource { fun samples(): Flow<VehicleSample> }

    class SimSource(private val now: () -> Long = System::currentTimeMillis, private val periodMs: Long = 50) : VehicleSource {
        override fun samples(): Flow<VehicleSample> = flow {
            val t0 = now()
            while (true) { emit(DriveCycle.at(now() - t0)); delay(periodMs) }
        }
    }
Verify: `--tests '*DriveCycleTest*'` -> 5 passed. Commit: "feat(vehicle): simulated drive cycle".

### 1.3 CommandGuard (read-only enforcement)
Test CommandGuardTest.kt
    package dev.kuntal.cockpit.vehicle

    import org.junit.Assert.*
    import org.junit.Test

    class CommandGuardTest {
        @Test fun allowsConfigAndMode01() {
            listOf("ATZ", "ATE0", "ATSP6", "ATAT1", "0100", "010C", "0120").forEach { assertTrue(it, CommandGuard.isAllowed(it)) }
        }
        @Test fun rejectsEverythingElse() {
            listOf("", "03", "04", "07", "0A", "0902", "1003", "2201F4", "14FFFF", "2EF190", "3101", "ATPP 0C SV 00", "ATPP FF ON", "010C\r04").forEach {
                assertFalse("should reject: $it", CommandGuard.isAllowed(it))
            }
        }
    }
Implementation CommandGuard.kt:
    package dev.kuntal.cockpit.vehicle

    object CommandGuard {
        private val at = Regex("^AT(?!PP)[A-Z0-9 ]{1,12}$")
        private val mode01 = Regex("^01[0-9A-F]{2}$")
        fun isAllowed(cmd: String): Boolean = at.matches(cmd) || mode01.matches(cmd)
    }
Commit: "feat(vehicle): read-only command guard".

### 1.4 Transport, ElmClient, PollSchedule
Test ElmClientTest.kt
    package dev.kuntal.cockpit.vehicle

    import kotlinx.coroutines.flow.take
    import kotlinx.coroutines.flow.toList
    import kotlinx.coroutines.test.runTest
    import org.junit.Assert.*
    import org.junit.Test

    class FakeTransport(private val replies: Map<String, String>) : Transport {
        val sent = mutableListOf<String>()
        override suspend fun open() {}
        override suspend fun close() {}
        override suspend fun exchange(cmd: String): String { sent += cmd; return replies[cmd] ?: "NO DATA" }
    }

    class ElmClientTest {
        private val replies = mapOf(
            "0100" to "41 00 BE 3F A8 13", "0120" to "41 20 00 00 00 00",
            "010C" to "41 0C 1A F8", "010D" to "41 0D 32", "0105" to "41 05 7B",
        )

        @Test fun discoversSupportedPids() = runTest {
            val c = ElmClient(FakeTransport(replies)); c.connect()
            assertEquals(
                setOf(Pid.ENGINE_LOAD, Pid.COOLANT_TEMP, Pid.MAP, Pid.RPM, Pid.SPEED, Pid.INTAKE_TEMP, Pid.THROTTLE),
                c.supported,
            )
        }
        @Test fun emitsDecodedSamples() = runTest {
            val s = ElmClient(FakeTransport(replies), now = { 1L }).samples().take(2).toList()
            assertEquals(1726.0, s[0].rpm!!, 0.001); assertEquals(50.0, s[0].speedKmh!!, 0.001)
            assertEquals(83.0, s[1].coolantC!!, 0.001)  // first cycle (n=0) reads slow group too
        }
        @Test fun onlyEverSendsAllowedCommands() = runTest {
            val t = FakeTransport(replies)
            ElmClient(t).samples().take(3).toList()
            assertTrue(t.sent.all { CommandGuard.isAllowed(it) })
        }
        @Test fun scheduleGroupsByRate() {
            val all = Pid.values().toSet()
            val sched = PollSchedule(all)
            assertEquals(listOf(Pid.RPM, Pid.SPEED), sched.pidsForCycle(1))
            assertTrue(Pid.THROTTLE in sched.pidsForCycle(3) && Pid.COOLANT_TEMP !in sched.pidsForCycle(3))
            assertTrue(Pid.COOLANT_TEMP in sched.pidsForCycle(10))
        }
    }
Implementation Transport.kt:
    package dev.kuntal.cockpit.vehicle

    interface Transport {
        suspend fun open()
        suspend fun close()
        /** Send one command (no CR). Return everything received before the '>' prompt, trimmed. Throws IOException on timeout/disconnect. */
        suspend fun exchange(cmd: String): String
    }
PollSchedule.kt:
    package dev.kuntal.cockpit.vehicle

    class PollSchedule(private val supported: Set<Pid>) {
        fun pidsForCycle(n: Int): List<Pid> = buildList {
            addAll(FAST.filter { it in supported })
            if (n % 3 == 0) addAll(MEDIUM.filter { it in supported })
            if (n % 10 == 0) addAll(SLOW.filter { it in supported })
        }
        companion object {
            val FAST = listOf(Pid.RPM, Pid.SPEED)
            val MEDIUM = listOf(Pid.MAP, Pid.THROTTLE, Pid.ENGINE_LOAD)
            val SLOW = listOf(Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.FUEL_LEVEL, Pid.MODULE_VOLTAGE, Pid.BARO, Pid.AMBIENT_TEMP, Pid.OIL_TEMP)
        }
    }
ElmClient.kt:
    package dev.kuntal.cockpit.vehicle

    import kotlinx.coroutines.currentCoroutineContext
    import kotlinx.coroutines.ensureActive
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.flow

    class ElmClient(private val transport: Transport, private val now: () -> Long = System::currentTimeMillis) : VehicleSource {
        var supported: Set<Pid> = emptySet(); private set

        private suspend fun ask(cmd: String): String {
            require(CommandGuard.isAllowed(cmd)) { "blocked command: $cmd" }
            return transport.exchange(cmd)
        }

        /** EcoSport = ISO 15765-4 CAN 11-bit/500k, so ATSP6 skips the slow protocol search. */
        suspend fun connect() {
            transport.open()
            for (c in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP6")) ask(c)
            val found = mutableSetOf<Int>()
            var base = 0x00
            while (base <= 0x60) {
                val bytes = ElmParser.parseBitmap(base, ask("01%02X".format(base))) ?: break
                val set = PidDecoder.supportedFrom(base, bytes)
                found += set
                if ((base + 0x20) !in set) break
                base += 0x20
            }
            supported = Pid.values().filter { it.code in found }.toSet()
        }

        override fun samples(): Flow<VehicleSample> = flow {
            try {
                connect()
                val schedule = PollSchedule(supported)
                var s = VehicleSample(now())
                var cycle = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    for (pid in schedule.pidsForCycle(cycle)) {
                        ElmParser.parse(pid, ask(pid.request))?.let { s = s.with(pid, it) }
                    }
                    emit(s.copy(tsMs = now()))
                    cycle++
                }
            } finally {
                transport.close()
            }
        }
    }
Verify: `--tests '*ElmClientTest*'` -> 4 passed. Commit: "feat(vehicle): ELM327 client, poll schedule".
Backlog (only if M6 measurement shows polling < ~8 Hz for RPM+speed): multi-PID requests ("010C0D") and the "expected responses" suffix ("010C1").

### 1.5 TCP transport + shared prompt reader (talks to the Mac-side ELM327 emulator)
StreamExchange.kt (DRY: reused later by Bluetooth-SPP and USB-serial transports):
    package dev.kuntal.cockpit.vehicle

    import java.io.IOException
    import java.io.InputStream
    import java.io.OutputStream

    fun exchangeOverStreams(input: InputStream, output: OutputStream, cmd: String): String {
        output.write((cmd + "\r").toByteArray()); output.flush()
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("link closed")
            if (b == '>'.code) break
            sb.append(b.toChar())
        }
        return sb.toString().trim()
    }
TcpTransport.kt:
    package dev.kuntal.cockpit.vehicle

    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext
    import java.io.IOException
    import java.net.InetSocketAddress
    import java.net.Socket

    class TcpTransport(private val host: String, private val port: Int, private val timeoutMs: Int = 2000) : Transport {
        private var socket: Socket? = null
        override suspend fun open() = withContext(Dispatchers.IO) {
            socket = Socket().apply { tcpNoDelay = true; soTimeout = timeoutMs; connect(InetSocketAddress(host, port), timeoutMs) }
            Unit
        }
        override suspend fun close() = withContext(Dispatchers.IO) { runCatching { socket?.close() }; socket = null; Unit }
        override suspend fun exchange(cmd: String): String = withContext(Dispatchers.IO) {
            val s = socket ?: throw IOException("not open")
            exchangeOverStreams(s.getInputStream(), s.getOutputStream(), cmd)
        }
    }
Env-gated integration test (skipped unless ELM_TCP is set) — TcpIntegrationTest.kt:
    package dev.kuntal.cockpit.vehicle

    import kotlinx.coroutines.flow.first
    import kotlinx.coroutines.runBlocking
    import org.junit.Assume.assumeTrue
    import org.junit.Assert.*
    import org.junit.Test

    class TcpIntegrationTest {
        @Test fun readsRpmFromEmulator() = runBlocking {
            assumeTrue(System.getenv("ELM_TCP") != null)
            val (h, p) = System.getenv("ELM_TCP").split(":")
            val s = ElmClient(TcpTransport(h, p.toInt())).samples().first()
            assertNotNull(s.rpm); assertNotNull(s.speedKmh)
        }
    }
tools/elm-emu.sh (dev-only tool; the emulator package is CC-BY-NC-SA, never shipped):
    #!/usr/bin/env bash
    set -euo pipefail
    cd "$(dirname "$0")"
    [ -d .venv ] || python3 -m venv .venv
    .venv/bin/pip install -q ELM327-emulator
    exec .venv/bin/python -m elm -n 35000 -s car
Verify (two terminals / background): start `tools/elm-emu.sh &`, then
    ELM_TCP=127.0.0.1:35000 ./gradlew :vehicle:test --tests '*TcpIntegrationTest*' > /tmp/t.log 2>&1; grep -E "BUILD|FAILED" /tmp/t.log
Expected: BUILD SUCCESSFUL with the test executed (not skipped). If the emulator's `car` scenario lacks a PID the client needs, fix the schedule's tolerance (missing PID => null), not the test. Android emulator reaches the host's emulator at 10.0.2.2:35000.
Commit: "feat(vehicle): TCP transport + ELM emulator tooling".

### 1.6 TripComputer (pure logic)
Spec + test first (TripComputerTest): feed samples at known timestamps, assert: distance = integral of speed (e.g. 3600 s at 36 km/h -> 36.0 km), average speed excludes standing time (speed < 1 km/h), max speed, max boost, 0-100 km/h timer (starts when speed leaves 0 from standstill, stops when >= 100, reset on next standstill), samples with null speed are ignored, gaps > 2 s are not integrated (link dropout). API: `class TripComputer { fun accept(s: VehicleSample); val state: TripState; fun reset() }` with `data class TripState(distanceKm, movingTimeS, avgSpeedKmh, maxSpeedKmh, maxBoostKpa, zeroTo100S: Double?)`.
Commit: "feat(vehicle): trip computer".

### 1.7 ReplaySource + recorder format
CSV columns = every VehicleSample field in declaration order, header row, empty cell = null. `SessionCsv.write(samples)` / `ReplaySource(lines, speedFactor=1.0)` replays with original inter-sample timing. Test: round-trip a DriveCycle sample list through CSV and compare equality. This is what lets a real drive recorded in the car (M6) be replayed on the Mac for UI tuning.
Commit: "feat(vehicle): CSV session recorder/replay".

M1 done-criteria: `./gradlew :vehicle:test` -> BUILD SUCCESSFUL, all tests green; integration test green against the ELM emulator.

------------------------------------------------------------------------------
## M2 — Design mocks (browser first look, runs parallel to M1)

Purpose: cheapest possible "first look" and a design source of truth before Compose work. Static HTML/CSS at exactly 1280x800 (a fixed-size body), viewable with `open docs/design/home.html`. Use the claude-design / popular-web-designs skills for HTML craft if helpful.

### 2.1 Direction + tokens (docs/DESIGN.md)
Defaults to propose to the owner (he overrides; reference screenshots welcome):
- Dark only in v1 (head unit already dims via the car's illumination line; day theme is parked).
- Palette: bg #0B0D10, surface #14181D, surface-2 #1C2229, text #E8ECF1, dim #8A95A3, accent #3D8BFF, warn #FFB020, danger #FF4D4D. One accent; semantic colours only for state.
- Type: one condensed sans bundled as TTF (Barlow Semi Condensed, OFL). Tabular figures for numbers. Min size 18sp for labels, speed readout 200sp.
- Grid at 1280x800 (dp == px at 160 dpi; revise after M4): 24dp safe margin, 56dp status bar, 104dp bottom dock, 16dp gutters.
- Touch targets >= 72dp in the dock, >= 64dp elsewhere. Anything reachable in <= 2 taps. No hamburger menus, no blur, no transparency stacks (weak GPU), motion only to convey state.

### 2.2 Screens to mock (one HTML file each, plus a day-in-the-life sim in JS for Drive)
1. home.html — Left 60%: context card (Now Playing if a MediaSession exists, else last-used app). Right 40%: 3 vital chips (speed, coolant, fuel) always live. Bottom dock: Maps (CarPlay/AA), Media, Phone, Drive, Apps, Settings. Top status: clock, BT, Wi-Fi, OBD link state.
2. drive.html — Left two-thirds: 270-degree tach arc 0-7000 with redline band (starts ~6000; confirm against the car's cluster), centre speed 200sp + "km/h", shift-light bar at top. Right third: vertical boost bar (-1.0 to +1.5 bar, turbo!), coolant, intake temp, battery V, fuel %. Bottom strip: trip km / avg / max / max boost / 0-100 timer. Warning banner: coolant >= 105 C (amber >= 100), battery < 12.0 V running or > 15.0 V.
3. media.html — Now Playing hero, transport controls >= 96dp, source switcher (vendor Radio / USB / BT audio / apps).
4. apps.html — grid, 6 columns, large icons, alphabetical + recents row.
5. settings.html — Units (km/h|mph, C|F, bar|psi), vehicle profile (redline, speed max), OBD adapter picker, data source (Simulator | Adapter), "Open stock launcher" escape hatch.
Deliver: docs/design/*.html + `docs/shots/mock-*.png` (screenshot each via a headless browser or `screencapture`). Owner reviews; iterate until he says go. Commit per iteration.

------------------------------------------------------------------------------
## M3 — Compose app on the emulator (the owner's "first look")

### 3.1 App module + HOME manifest
Add `include(":app")` to settings.gradle.kts. app/build.gradle.kts:
    plugins {
        alias(libs.plugins.android.application)
        alias(libs.plugins.kotlin.android)
        alias(libs.plugins.kotlin.compose)
    }
    android {
        namespace = "dev.kuntal.cockpit"
        compileSdk = 35
        defaultConfig {
            applicationId = "dev.kuntal.cockpit"
            minSdk = 28
            targetSdk = 30     // revisit after M4: match the unit's real Android version
            versionCode = 1
            versionName = "0.1.0"
        }
        buildTypes {
            release {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                signingConfig = signingConfigs.getByName("debug")   // personal sideload only
            }
        }
        buildFeatures { compose = true }
        compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
        kotlinOptions { jvmTarget = "17" }
    }
    dependencies {
        implementation(project(":vehicle"))
        implementation(platform(libs.compose.bom))
        implementation(libs.compose.ui); implementation(libs.compose.foundation)
        implementation(libs.activity.compose); implementation(libs.androidx.core)
        implementation(libs.lifecycle.runtime.compose); implementation(libs.lifecycle.viewmodel.compose)
        implementation(libs.coroutines.android)
    }
AndroidManifest.xml: permissions BLUETOOTH, BLUETOOTH_ADMIN, INTERNET, QUERY_ALL_PACKAGES; one activity `.MainActivity`, exported, launchMode singleTask, screenOrientation landscape, configChanges "orientation|screenSize|keyboardHidden|uiMode|density", with ONE intent-filter containing action MAIN + categories HOME, DEFAULT and LAUNCHER (LAUNCHER lets `am start` and the app list find it). Theme: Theme.Material.NoActionBar-style dark, windowBackground = bg colour (no white flash). MainActivity: edge-to-edge, hide system bars (immersive sticky), `setContent { Text("Cockpit") }`.
Add proguard-rules.pro (empty). Verify:
    source tools/env.sh
    ./gradlew :app:assembleDebug > /tmp/b.log 2>&1; E=$?; grep -E "BUILD|^e: " /tmp/b.log; echo GRADLE_EXIT=$E
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb shell am start -n dev.kuntal.cockpit/.MainActivity
    adb shell dumpsys activity activities | grep mResumedActivity
Expected: `Success`, and mResumedActivity contains `dev.kuntal.cockpit/.MainActivity`.
tools/shot.sh: `adb exec-out screencap -p > "docs/shots/${1:?name}.png"`; view the PNG (vision) to confirm the screen is 1280x800 and shows the text.
Commit: "feat(app): HOME launcher shell".

### 3.2 Design system
app/src/main/java/dev/kuntal/cockpit/ui/theme/: Tokens.kt (colours, spacing, radii from DESIGN.md), Type.kt (bundled font in res/font), `CockpitTheme` composable via CompositionLocals (no Material3). Components: `CockpitButton`, `Chip`, `Card`, `StatTile(label, value, unit, state)`. State = Normal | Warn | Danger. Each has a @Preview-free screenshot test path: render in a scratch screen reachable only in debug builds (route "catalog").

### 3.3 Data layer
- `VehicleRepository` (app/data): holds the selected `VehicleSource`; `val vehicle: StateFlow<VehicleUiState>` where `VehicleUiState(sample: VehicleSample?, link: Link, trip: TripState)` and `Link = Simulated | Connecting | Connected | Lost`. Wrap non-sim sources with retryWhen + exponential backoff 1s..10s capped. UI emission capped at 20 Hz (`.sample(50)`).
- Default source on debug builds = SimSource. Settings (DataStore preferences — add `androidx.datastore:datastore-preferences:1.1.1`) store: units, vehicle profile (rpmMax=7000, redline=6000, speedMax=220, coolantWarn=105), source choice, adapter address.
- `CockpitViewModel` exposes the above; tests for repository retry behaviour with FakeTransport (JVM unit test in app/src/test).

### 3.4 Drive screen (highest value; build first)
Rendering rules (weak GPU): static layer (ring track, ticks, numerals, labels) drawn once with `drawWithCache`; dynamic layer = needle/progress arc only. Needle value smoothed per frame in a `withFrameNanos` loop with `v += (target - v) * (1 - exp(-dt/0.08))`, held in a plain `Float` read ONLY inside `drawBehind` so no recomposition per frame. No `Modifier.blur`, no `shadow` elevation, no `BlendMode` gymnastics, no Lottie.
Acceptance (verify on the emulator with SimSource, screenshots at t=0 s, 20 s, 50 s, 65 s of the cycle):
 - tach arc and speed digits match the sample values (speed shown rounded, tabular figures, no layout jitter as digits change);
 - boost bar shows negative (vacuum) and positive regions; value label in bar (or psi per setting);
 - coolant tile goes Warn at >= 100 C and Danger at >= 105 C (inject via a debug "force value" switch to test);
 - link chip shows "SIM";
 - trip strip numbers advance;
 - `adb shell dumpsys gfxinfo dev.kuntal.cockpit | grep -E "Janky|50th|90th"` on the emulator shows < 5% janky (emulator is fast; the real gate is M5).
Commit: "feat(app): Drive screen".

### 3.5 Home + dock + vendor shortcut registry
- `VendorShortcuts` loads `app/src/main/assets/vendor-shortcuts.json`: `[{"id":"radio","label":"Radio","component":"pkg/.Activity"}, ...]`. Empty/unknown entries hide their tile. Filled in after M4. Launch via `Intent().setComponent(...).addFlags(NEW_TASK)`; on `ActivityNotFoundException` show a toast and hide the tile next time.
- Dock: Maps(CarPlay/AA), Media, Phone, Drive, Apps, Settings. Home context card: Now Playing if available (3.7) else "Resume <last app>".
- HOME behaviour: pressing Home from our app returns to Home screen (single task, `onNewIntent` resets nav stack).
Acceptance: screenshots of Home; dock taps navigate (drive via `adb shell input tap X Y` using coordinates from the mock grid); `mResumedActivity` stays ours.
Commit: "feat(app): Home screen + dock".

### 3.6 Apps grid
`PackageManager.queryIntentActivities(MAIN/LAUNCHER)` excluding ourselves, sorted by label, 6 columns, icons rasterised once into a bounded LruCache (32 MB cap), recents row from UsageStats-free local launch counter (DataStore). Acceptance: emulator shows its stock apps; tapping launches; Back returns to Apps.
Commit: "feat(app): Apps grid".

### 3.7 Media card (any MediaSession)
`MediaListener : NotificationListenerService` + `MediaSessionManager.getActiveSessions`. Needs the listener grant: `adb shell cmd notification allow_listener dev.kuntal.cockpit/dev.kuntal.cockpit.MediaListener` (done by tools/install.sh; on the unit, via adb or the system toggle). Now-Playing hero: title/artist/art, play/pause/next/prev through `MediaController.transportControls`. Vendor Radio/USB players that don't publish a MediaSession remain launchable tiles (M4 will tell which are which).
Acceptance: play audio in any emulator media app; card shows metadata; buttons control playback (verify with `adb shell dumpsys media_session | grep -E "state=PlaybackState"`).
Commit: "feat(app): media card".

### 3.8 Settings + data-source switch
Units, vehicle profile, source (Simulator | ELM over TCP host:port [emulator->10.0.2.2:35000] | later Bluetooth/USB), "Open stock launcher" (explicit intent to vendor launcher from the registry, else system home chooser). Acceptance: switch to ELM-TCP against `tools/elm-emu.sh` and see the Drive screen move with link = Connected; kill the emulator process, link goes Lost then recovers when restarted.
Commit: "feat(app): settings + TCP source".

### 3.9 Review loop
Deliver screenshots of all screens (docs/shots/m3-*.png) plus a short screen-recording (`adb shell screenrecord /sdcard/d.mp4` then `adb pull`) of the Drive cycle. Owner reviews; iterate on his corrections immediately. M3 exit = owner says the look is right.

------------------------------------------------------------------------------
## M4 — Recon on the real unit (non-destructive; do this early, in parallel with M1-M3)

Everything here is read-only. Nothing is installed, disabled or changed except enabling adb.

### 4.1 Get an adb channel
1. In the unit: Settings -> About -> tap Build number (7x) or the vendor's hidden-unlock gesture/password route that reviewers describe for "unknown apps"; enable Developer options -> USB debugging and, on Android 11, Wireless debugging.
2. Put the unit and the Mac on the same Wi-Fi (phone hotspot works; car parked, engine running for stable voltage).
3. `adb pair <ip>:<pair-port>` (code from the unit), then `adb connect <ip>:<port>`; verify `adb devices` lists it.
Fallback if adb is impossible: task 4.3 (on-device recon APK + USB stick).

### 4.2 Recon commands (save each into docs/recon/raw/, gitignored)
    D=docs/recon/raw; mkdir -p $D
    adb shell getprop > $D/getprop.txt
    adb shell wm size > $D/display.txt; adb shell wm density >> $D/display.txt
    adb shell cat /proc/cpuinfo > $D/cpuinfo.txt; adb shell cat /proc/meminfo | head -5 > $D/meminfo.txt
    adb shell dumpsys SurfaceFlinger | grep -iE "GLES|refresh|vsync" > $D/gpu.txt
    adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > $D/home.txt
    adb shell pm list packages -f > $D/packages.txt
    adb shell pm list packages -3 > $D/packages-3p.txt
    adb shell dumpsys activity services > $D/services.txt
    adb shell dumpsys bluetooth_manager | grep -iE "bonded|name" > $D/bt.txt
    adb shell ls -l /dev > $D/dev.txt
    adb shell getevent -lp > $D/input-devices.txt
    adb shell dumpsys sensorservice | grep -iE "light|accel|gyro" > $D/sensors.txt
    adb shell settings list system > $D/settings-system.txt
    adb shell su -c id > $D/su.txt 2>&1          # "not found" is the expected answer on a stock build
    adb shell logcat -d | grep -iE "canbus|obd|can_box|rpm|vehicle|mcu" | head -300 > $D/logcat-vehicle.txt
Also, with the engine running: `adb shell getevent -l` while pressing each steering-wheel button (records whether they arrive as keycodes or vendor broadcasts).
Pull the vendor launcher + vendor apps' APKs for manifest inspection (read-only copy):
    P=$(adb shell pm path <vendor.launcher.pkg> | cut -d: -f2); adb pull "$P" docs/recon/raw/vendor-launcher.apk
    apkanalyzer manifest print docs/recon/raw/vendor-launcher.apk > docs/recon/raw/vendor-launcher-manifest.xml
Answers to extract into docs/RECON.md: Android version/API, density, SoC (ro.board.platform), GPU, RAM/free RAM, vendor HOME package, exported component names for Radio / BT phone / EQ / camera / CarPlay-AA / settings, whether MCU/CAN data reaches Android as intents/services (grep services + logcat for how RPM/speed arrive TODAY = answer to U1), `ro.debuggable`, `ro.secure`, `ro.boot.verifiedbootstate`, `ro.boot.flash.locked`, su present?, how the update/OTA app works.
Then: fill app/src/main/assets/vendor-shortcuts.json, set tools/avd.sh density, set targetSdk, decide U1 branch.

### 4.3 Fallback: on-device ReconActivity (debug build only)
A hidden debug Activity that writes /sdcard/cockpit-recon.json with: Build.* fields, DisplayMetrics, `PackageManager` list of packages with their LAUNCHER/HOME activities and exported activities, sensors list, bonded BT devices, USB devices, and which of `/dev/tty*`/`/dev/can*` exist. Copy to a USB stick, read on the Mac. TDD note: the JSON builder is a pure function of an input struct; unit-test that.

### 4.4 Decision gate after recon (record in docs/RECON.md)
- Is a non-root path enough? (Can we install, become HOME, and keep vendor apps launchable?) If yes, rooting is OFF the table until a concrete feature is blocked.
- Is vendor-side car data accessible to apps (broadcast/service)? If yes, evaluate a `VendorCanSource` instead of buying an adapter.

------------------------------------------------------------------------------
## M5 — Install on the real unit (simulated data), parked car

### 5.1 Build + install
    ./gradlew :app:assembleRelease > /tmp/b.log 2>&1; E=$?; grep -E "BUILD|^e: " /tmp/b.log; echo GRADLE_EXIT=$E
    adb install -r app/build/outputs/apk/release/app-release.apk
    adb shell cmd notification allow_listener dev.kuntal.cockpit/dev.kuntal.cockpit.MediaListener
    adb shell cmd package set-home-activity dev.kuntal.cockpit/.MainActivity
    adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
Verify `adb shell dumpsys activity activities | grep mResumedActivity` shows dev.kuntal.cockpit. If the unit ignores set-home-activity, press the hardware Home key and choose Cockpit -> "Always".

### 5.2 Rollback (write to docs/ROLLBACK.md BEFORE step 5.1 and keep it on the Mac and in a phone photo)
    adb shell cmd package set-home-activity <vendor.launcher/.Activity>     # restore stock home
    adb uninstall dev.kuntal.cockpit
If the unit boots into a broken state: the app always keeps a long-press-on-clock escape to the stock launcher; if the screen is unusable, connect adb over Wi-Fi from the Mac and run the line above; last resort = vendor factory reset / USB hard update (firmware for the exact platform + RAM variant).
If the vendor launcher re-asserts itself after ACC cycles (common with head units), the next rung is `adb shell pm disable-user --user 0 <vendor.launcher.pkg>` (reverse: `pm enable <pkg>`) — needs owner go-ahead (rule 5).

### 5.3 In-car acceptance checklist (parked, engine running, owner or agent via adb read-only)
 1. Cold boot -> Cockpit appears as HOME without manual action; time-to-home measured `adb shell am start -W`.
 2. ACC off/on and key-off for 30+ min: still HOME after resume, process not killed (`adb shell dumpsys activity processes | grep cockpit`).
 3. Reverse gear: reverse camera / 360 view still takes over and returns correctly.
 4. Steering-wheel keys (volume, next/prev, mute) still work.
 5. Wireless CarPlay / Android Auto connects and runs from the Maps dock tile.
 6. Radio, USB audio, BT phone call (incoming + outgoing), EQ/DSP screens reachable.
 7. Perf gate on the Drive screen with SimSource for 10 min: `adb shell dumpsys gfxinfo dev.kuntal.cockpit reset` then after the run `dumpsys gfxinfo dev.kuntal.cockpit` -> janky frames < 10%, 90th percentile frame time < 24 ms; `dumpsys meminfo dev.kuntal.cockpit` TOTAL PSS < 200 MB. If it fails: lower the update rate to 15 Hz, drop the static-layer complexity, then re-measure (do not add features until it passes).
 8. Night glare: brightness tolerable with the unit's own dimming.
Record results in docs/RECON.md ("M5 results"). Anything failing is a bug to fix before M6.

------------------------------------------------------------------------------
## M6 — Real vehicle data (OBD)

### 6.1 Adapter (owner purchase / decision)
Recommended: a genuine ELM327-compatible or STN-based Bluetooth adapter with sleep mode (OBDLink CX/MX+, vLinker MC+/FS). Avoid the cheap "v2.1" clones: they fake commands, hang on multi-PID requests and keep drawing current with the key off. Wi-Fi adapters are out: the unit's Wi-Fi is used by wireless Android Auto/CarPlay. The OBD-II port sits below the steering column (driver side).
If U1 shows the unit already receives car data through a vendor path, do 6.1b first: write a `VendorCanSource` that reads it (no adapter needed) and skip 6.2-6.3.

### 6.2 Android transports (app/platform)
- `BtSppTransport(address)`: `BluetoothDevice.createRfcommSocketToServiceRecord(UUID 00001101-0000-1000-8000-00805F9B34FB)`, streams -> `exchangeOverStreams` (already written in 1.5). Connect on a background coroutine; `cancelDiscovery()` first; reconnect with the repository's backoff.
- `BleTransport` only if the chosen adapter is BLE-only.
- `UsbSerialTransport` (optional, USB ELM cable) using `com.github.mik3y:usb-serial-for-android:3.9.0` (needs jitpack in settings repositories; LGPL-2.1, fine for a personal sideload).
- Adapter picker in Settings lists bonded BT devices; store the MAC.
- Pairing: the unit's BT stack pairs it in vendor Settings (PIN usually 1234/0000); verify bonded via `dumpsys bluetooth_manager`.

### 6.3 First real data
Engine idle, parked, owner present. Settings -> Adapter source. Expected: link Connected within ~5 s; RPM ~800-900 at idle; speed 0; coolant rising; battery ~13.8-14.4 V. Then a short real drive by the owner (passenger operates the screen). Measure the achieved RPM/speed refresh rate (log samples/s). If < 8 Hz, do the backlog item from 1.4.

### 6.4 Record + replay loop
Debug setting "Record session" writes the CSV from 1.7 to /sdcard/Android/data/dev.kuntal.cockpit/files/. Pull with adb; add a "Replay file" data source on the emulator; tune gauge smoothing, warning thresholds and the trip logic offline against real drives. Calibrate the VehicleProfile (real redline, shift-light RPM, estimated-gear table from rpm/speed ratios — gear display is parked until this data exists).

### 6.5 Hardening
ACC-off: close the link, stop polling (no battery drain from the adapter polling the bus); ACC-on: reconnect automatically. Adapter unplugged mid-drive: banner "No vehicle data", gauges dim, trip pauses (gaps > 2 s are not integrated). No crash on malformed replies (fuzz the parser with random strings in a unit test).

------------------------------------------------------------------------------
## M7 — Optional / decide after M5-M6

- 7.1 Passive CAN sniffer (read-only CAN-to-USB/BLE gadget on OBD pins 6/14, 500k) for gear, steering angle, TPMS, wheel speeds, odometer, accelerator pedal at full broadcast rate. Needs hardware choice + a DBC derived from Racelogic's EcoSport 2013- signal list and our own logging. Read-only; never transmit on the bus.
- 7.2 Vendor-service hooks (e.g. steering-key broadcasts, vendor media state) — only what recon shows is reachable without root.
- 7.3 Root. Gate: M4/M5 shows a concrete feature is blocked, the stock firmware package for this exact platform + RAM + version is downloaded and its vendor USB re-flash path is tested to work BEFORE any modification, and the owner gives an explicit go. What root would buy: disabling vendor kill-services, reading vendor CAN service directly, hiding system bars permanently, privileged settings. Method preference: (a) `adb root` / vendor engineering mode if the build allows, (b) Magisk-patched boot image flashed through the vendor update mechanism. Brick risk is real; warranty is void.
- 7.4 Day theme (time-based or light-sensor if recon shows one).

## Validation matrix

 Layer               | How                                                        | Pass condition
 --------------------|------------------------------------------------------------|------------------------------------
 vehicle logic       | `./gradlew :vehicle:test`                                  | BUILD SUCCESSFUL, 0 failures
 ELM plumbing        | tools/elm-emu.sh + ELM_TCP integration test                | test executed and green
 UI on emulator      | tools/shot.sh + visual check, `dumpsys gfxinfo`            | matches mocks, < 5% janky
 HOME behaviour      | `dumpsys activity activities | grep mResumedActivity`     | ours after Home/boot
 Real unit           | M5.3 checklist                                             | 8/8 items pass
 Real data           | M6.3 idle + drive, CSV recorded                            | link Connected, plausible values, >= 8 Hz

## Risks and tradeoffs

- Vendor stack is proprietary: Radio/BT phone/EQ/camera cannot be re-skinned without root + reverse engineering. Mitigation: launch them, don't replace them; make the dock fast and consistent.
- Weak SoC: Compose gauges can stutter. Mitigation: static/dynamic layer split, 20 Hz cap, no blur/shadows, perf gate in M5 before adding features. Fallback: classic Canvas View for the gauge only.
- Head units kill background apps and sleep aggressively on ACC off; reconnect logic must be robust (6.5).
- The unit may re-assert its own launcher after power cycles (rung 2 of the ladder needs owner approval).
- targetSdk/Android-version mismatch (11 vs 13 seen in the wild) changes Bluetooth permission handling; settled in M4.
- ELM327 clones are slow/unreliable; poll rate is bounded by the adapter (~5-15 PID/s). Hence the tiered PollSchedule.
- Rooting/flashing can brick a Rs 19k unit and voids warranty; ladder + gates + rollback doc mitigate. Stock firmware must be in hand first.
- ELM327-emulator is CC-BY-NC-SA: used only as a dev tool, never bundled.
- Emulator speed != real hardware: only M5 numbers count for performance.

## Open questions for the owner (defaults in brackets; none block M0-M3)

 1. How does the unit show RPM/speed today — plug-in OBD dongle, a harness CAN box, or a phone? (default: unknown; M4 will tell)
 2. Do you already own an OBD adapter? Which? (default: buy OBDLink CX or vLinker MC+)
 3. 9" or 10.1", and 2GB/32GB or 4GB/64GB? (default: M4 reads it)
 4. Keep all vendor apps (Radio, phone, camera, CarPlay/AA, EQ) and just launch them from the dock? (default: yes)
 5. Visual taste: any reference screenshots/brands you like (Tesla-like minimal, Polestar, sporty ST-Line, retro analog)? (default: the dark/blue direction in 2.1)
 6. Remote repo under Kuntal271 (personal) now or later? (default: local-only until you say)
 7. Is a speed-based input lockout (no typing while moving) wanted? (default: parked; not built)

## Definition of done per milestone

 M0: emulator "palmbay" boots at 1280x800; `./gradlew :vehicle:test` green.
 M1: all vehicle tests green; ELM emulator integration test green.
 M2: owner has approved mocks (or given corrections that are applied).
 M3: Drive/Home/Media/Apps/Settings run on the emulator, screenshots + recording delivered, owner signs off on the look.
 M4: docs/RECON.md answers U1-U3; vendor-shortcuts.json filled.
 M5: install + rollback proven; 8/8 checklist items pass.
 M6: real RPM/speed/boost on screen from the car; a recorded drive replays on the Mac.
