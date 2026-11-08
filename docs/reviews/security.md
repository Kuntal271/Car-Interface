# Security review (agent `security`)

Branch `review/security`, base `434a2a4`, 13 commits (`git log --oneline 434a2a4..`). Companion documents: `docs/SECURITY.md` (threat model, guarantees, release checklist),
`tools/security/` (hostile adapter, scanners, dynamic harness). Everything below was produced by commands I ran; what I could not run is labelled.

## 1. Summary

**Verdict.** The app's read-only promise held in practice (in no test did anything but `AT` setup and mode 01 leave the app), but the guard that is
supposed to *enforce* it was porous, the exported Home activity let any app rewrite the data source and fake the dashboard in a release build, and a rogue
adapter could exhaust memory. All three are fixed and tested; the rest is supply-chain hygiene (fixed or needs the owner).

| Severity | Count | IDs |
| --- | --- | --- |
| CRITICAL | 0 | (SECURITY-01 would be, if it could be triggered at will: see its text) |
| HIGH | 4 | 01, 02, 03, 04 |
| MEDIUM | 6 | 05, 06, 07, 08, 09, 10 |
| LOW | 6 | 11, 12, 13, 14, 15, 16 |
| INFO | 4 | 17, 18, 19, 20 |

Status: 11 FIXED (01-07, the workflow part of 09, 11-13); OPEN: 08, 10, 14, 16 and the action-pinning part of 09; WONTFIX: 15 (and 17, an INFO); INFO: 17-20.

**Top 5.**
1. **SECURITY-01** Any app could start the exported Home activity with `--es set "source=ObdTcp,tcpHost=<attacker>"`, `reset` or `snapshot` in a *release* build: persistent re-pointing of the data source and a dashboard frozen on made-up numbers with the status bar saying plain `OBD` (not `OBD sim`). Gated to debuggable builds.
2. **SECURITY-02** `CommandGuard` let `AT PP FF OFF` (persistent adapter EEPROM write), `ATSH`, `ATCAF0`, `ATWM`, `ATSI`, `ATSP0`, `ATBRD`, `ATMA` through. Now an exact 19-string whitelist + `01xx`, also enforced at the single wire function, 2 M-string fuzz.
3. **SECURITY-03** A rogue adapter that never sends `>` grew the app's heap until `OutOfMemoryError` ("Failed to allocate 150994952 bytes", 40 MB consumed in the first 90 s) and a reconnect loop. Now 2048 chars / 10 s per reply; heap flat at 3.9-4.4 MB.
4. **SECURITY-04/05** Old numbers stayed on screen as live after the adapter stopped answering; `41 0C -1 -1` decoded to -64.25 rpm; `FFFF` rpm/voltage sentinels were shown.
5. **SECURITY-08/09/10** Release signed with the machine's well-known debug key (mechanism added, key is the owner's to create), CI had no permission block and published an installable APK for every pull request, build toolchain has published advisories (OPEN).

## 2. Scope and method

Done: manifest/APK inspection of debug and release (aapt2, apkanalyzer, apksigner, dex strings); full code read of `:vehicle` and the trust-boundary parts of `:app`
(about 3 700 lines); CommandGuard fuzz/property suite; scan of all 29 commits of all branches (`git log --all -p`, 3.3 MB) for secrets, tracked file types and
high-entropy strings; CI/workflow/wrapper review; OSV check of the resolved runtime classpath (74 artifacts) and of the build classpath (147 artifacts); dynamic run
on my own emulator (Android 11 arm64, API 30, port 5560) against the baseline and the fixed APKs.

Not done / not verifiable here: the real head unit (Android version, patch level, adb exposure unknown); Bluetooth transport behaviour (no BT on the emulator; it shares
`exchangeOverStreams`, so the bounds apply, but I did not run it); the Android security bulletin for the unit's platform (not consulted; the unit's patch level is unknown,
and Android 11 is, to my knowledge, no longer patched by Google: unverified); anything on github.com (off limits: repository settings, branch protection, secret scanning,
SHA of actions, whether CI is green on these changes); `adb backup` itself (needs on-screen confirmation; I checked the package flags instead); 
the full gate `tools/gb.sh :app:testDebugUnitTest :vehicle:test :app:assembleRelease` was run three times (after the CommandGuard change; after the app-side changes; and on the final code tree, which already contained the manifest, signing and Gradle changes: all `BUILD SUCCESSFUL`), not before each of the 13 commits, because the machine was saturated (load average about 30: one gate took 15 minutes). Before each Kotlin commit I ran `:vehicle:test`, and `:app:testDebugUnitTest` for the app ones; the commits after the last gate only touch docs, `.gitignore`, `tools/` scripts and CI files (the Gradle wrapper property was exercised separately, SECURITY-12).

**Process incident (INC-1, my error).** My first dynamic run used a helper that kept an inherited `ANDROID_SERIAL=emulator-5556` (the `monkey` agent's emulator) instead of
forcing `emulator-5560`. For about 5 minutes (about 04:19 to 04:24) these commands ran on **emulator-5556**: `adb install -r base-release.apk` (replacing whatever Cockpit build was installed there with the baseline release
build of `434a2a4`), `logcat -c`, `am force-stop`, and an `am start` that persisted `source=ObdTcp, tcpHost=10.0.2.2, tcpPort=35040` in that app's settings, plus `meminfo`/`top` reads and a hostile
adapter of mine listening on the Mac's port 35040 (not theirs). When I noticed, I stopped my adapter, ran `am force-stop` + `am start --ez reset true` there once to put the
settings back to defaults, and touched 5556 no more. I did not restore their previous APK (I do not know what it was), so **the monkey agent should reinstall its own build
on emulator-5556 and re-apply its settings**; any result it recorded between about 04:19 and 04:25 on that emulator may be affected. The helper now forces the serial and the
dynamic script refuses to run unless `--serial` equals `$ANDROID_SERIAL`. All numbers in this report come from the re-run on emulator-5560 (a clean `pm clear` state).
Emulator time: about 30 minutes including the lost 5 and a 4-minute second boot for the snapshot comparison.

## 3. Findings

### SECURITY-01 HIGH, FIXED (4b4174f): any app can rewrite settings and fake the dashboard through the exported Home activity
`MainActivity` is exported (it must be: it is the HOME activity) and `onCreate` applied the extras `set`, `reset`, `snapshot`, `perf`, `screen`, `page` in every build; R8 keeps the strings
(`strings classes.dex` of the release APK contains `snapshot`, `perf`, `reset`, `set`, `screen`, `page`, `tcpHost`).
Evidence, baseline **release** APK, `pm clear` state, emulator-5560, command `am start -n dev.kuntal.cockpit/.MainActivity --es set "source=ObdTcp,tcpHost=10.0.2.2,tcpPort=35040" --ez reset true`:
* my adapter log: `connected (#1)`, `cmd 'ATZ'` - the app connected to the attacker-chosen host; Settings > Connections text dump: `OBD adapter · Wi-Fi | 10.0.2.2:35040 | In use`. The setting is persisted (survived a force-stop and a re-install of the fixed build).
* `--ez snapshot true --es screen drive`: Drive screen texts `74 | km/h | Gear | 3 | ... Coolant | 92 | °C ... 21:47 | Fri 30 Oct | OBD | Canbox` - fixed design-mock numbers and a frozen clock, status bar says `OBD`, **not** `OBD sim` (violates "never present invented numbers as live data"). Screenshots (not committed, `artifacts/` is git-ignored): `artifacts/dyn/base-release-snapshot.png` vs `fixed-release-snapshot.png`.
* Reliability caveat (honest): the activity is `singleTask` and ignores `onNewIntent`, so the extras are applied only on a cold start of the activity (observed: "intent has been delivered to currently running top-most instance" and no effect). An attacker app has to wait for, or provoke, a restart of the launcher (low-memory kill, reboot, force-stop); on a 2 GB unit that happens. That is why I rate it HIGH rather than CRITICAL.
* Impact: persistent data-source takeover, loss of the owner's settings, falsified readings labelled live. No effect on the car itself.

Root cause: dev hooks read from an untrusted, exportable entry point. Fix: `DevLaunch.parse` is the single entry point; `snapshot`, `reset`, `set` need `FLAG_DEBUGGABLE`; `set` is also bounded (4 KB, 32 pairs); a throwing accessor (malformed Bundle) counts as no extras.
`perf` (overlay + frame-time log, no state) and the enum-validated `screen`/`page` navigation stay in release **on purpose**: `docs/PERF.md` measures a release build with `--es screen drive --ez perf true`; they cannot change data or settings.
Proof: `DevLaunchTest` (5 tests); dynamic: fixed release, same two attacks, adapter log `+0 connections`, Connections page `Simulator | In use | 192.168.0.10:35000`, Drive texts show the live simulator (`26 km/h`, clock `04:27`, `OBD | sim`); a combined "all hooks at once" extra changed nothing. Debug build keeps all hooks (`run-as ... cat shared_prefs/cockpit_settings.xml` shows the stored result).

### SECURITY-02 HIGH (latent), FIXED (58351b6): `CommandGuard` was porous
Old: `^AT(?!PP)[A-Z0-9 ]{1,12}$` or `^01[0-9A-F]{2}$`. No code path sends a dangerous command today (so this is latent), but the guard is the product's safety net.
Red test (`CommandGuardFuzzTest` against the old regex, 9 of 13 failed): `AT PP FF ON` / `AT PP FF OFF` pass (the ELM327 ignores spaces, so this is ATPPFFOFF: a persistent EEPROM write), as do `ATSH7E0` (choose the CAN id of the next frame), `ATCAF0` (raw framing, so `01xx` becomes an arbitrary-id frame), `ATWM`/`ATSW` (adapter-initiated periodic frames), `ATSI`/`ATFI`/`ATIB` (K-line init), `ATSP0`/`ATTP` (protocol search), `ATBRD` (baud change that strands the link), `ATMA` (monitor: no prompt), `ATPB`, `ATZ ATZ`, `ATH1ATH1`. Mode 04 and the other services were already refused (the `^01[0-9A-F]{2}$` half was fine).
Fix: exact whitelist of 19 canonical strings (`ATZ ATI AT@1 ATRV ATDP ATDPN ATE0/1 ATL0/1 ATS0/1 ATH0/1 ATAT0/1/2 ATSP6 ATCAF1`) plus exactly `01` + two upper-case hex digits; no trimming/case-folding/space-removal, so guard and adapter cannot disagree. Also enforced inside `exchangeOverStreams` (the one function every transport writes through) before a byte is written. The policy and the reason for each refused class are in `docs/SECURITY.md` section 4.
Proof: 13 tests, ~2 000 000 random strings over an adversarial alphabet, the ELM327 datasheet `AT` catalogue (120 command/parameter samples) x about 25 spellings each (case, spaces, tabs, CR/LF/NUL, Unicode look-alikes, 300-char padding), all 65 536 two-byte requests (only `01xx` passes), 1 MB inputs in < 2 s, an adapter model (`adapterWouldRun`) that must see exactly the same single command for anything the guard allows, and "nothing is written for a refused command". Dynamic: in all 13 hostile-adapter runs every command the adapter received was in the whitelist (`NOT-READ-ONLY commands: none`, e.g. 2 267 commands in one run).

### SECURITY-03 HIGH, FIXED (a37a48f): a rogue adapter can exhaust memory and hold a transport thread
`exchangeOverStreams` appended every byte to an unbounded `StringBuilder` until `>`; the TCP socket timeout (4 s) only bounds the gap between two bytes (an adapter streaming or dripping one byte per second never trips it); Bluetooth has a 4 s watchdog but TCP did not.
Evidence, baseline release, `tools/security/hostile_adapter.py --mode endless`: Java heap after GC 6 MB (t=8 s) -> 14 -> 19 -> 32 MB (t=24 s) -> 49 MB (t=44 s) -> 88-92 MB (t=68-80 s); at 04:23:36 `Throwing OutOfMemoryError "Failed to allocate a 150994952 byte allocation ... growth limit 201326592"`; the adapter had received the first connection's whole stream (`total sent 40239104 B`) when the app reconnected (`connected (#2)`): the reconnect loop repeats the cycle (heap regrows 27 -> 85 MB within the next 70 s), with GC pressure on a 2 GB unit. The OOM did not crash the process because `VehicleRepository` swallows upstream `Throwable`.
Fix: at most 2048 characters (`reply too long`) and 10 s (`reply too slow`) per reply, both `IOException`, so the existing 1-2-4-8-10 s back-off applies. Real replies are under 300 characters.
Proof: `StreamExchangeLimitsTest` (6 tests: endless bytes of 6 kinds stop after cap+1 reads, 10 000 x `NO DATA`, drip-feed on a fake clock, exact boundary, normal multi-ECU reply unchanged, `ElmClient.connect()` against an endless adapter). I did not run these red against the old loop (it has no exit on endless input and would hang the build); the red evidence is the dynamic baseline above. Dynamic, fixed release against the same endless adapter: Java heap 4.4 / 3.9 / 3.9 / 3.9 MB at 10/20/30/40 s (flat), threads 23, one reconnect about every 10 s (back-off cap), 4 connections in 40 s.

### SECURITY-04 HIGH, FIXED (0cf16e0): values that stopped being answered stayed on screen as live
`ElmClient` kept the last decoded value of each PID forever; `NO DATA` (ECU asleep, adapter lying, adapter gone quiet but connected) left old rpm/speed/temperatures under a "Connected" link.
Red test: after two good replies and `NO DATA` ever after, miss 3 still returned 1726 rpm. Fix: three consecutive misses clear the value (the UI already draws a dash for null; `VehicleSample.with` accepts null).
Dynamic (fixed debug, `--mode nodata`: 40 valid replies then `NO DATA`): Drive texts `– | km/h`, `Coolant | – | °C`, `Throttle | – | %`. Not run dynamically on the baseline (the unit test is the red evidence).

### SECURITY-05 MEDIUM, FIXED (0cf16e0): parser accepted signs, non-ASCII digits, trailing junk and sentinels
`String.toIntOrNull(16)` accepts `-1`, `+F` and non-ASCII digits: `41 0C -1 -1` decoded to **-64.25 rpm** (red test output), `41 0D ٣٢` to 50 km/h, `41 0D 32 ZZ` to 50; `41 0C FF FF` (16 383.75 rpm) and `41 42 FF FF` (65.5 V) were shown. Fix: ASCII hex only, whole line must be hex bytes (padding bytes and stray NULs at line ends tolerated), rpm > 12 000 and voltage > 32 V dropped. Proof: 8 tests incl. a loop over all 12 PIDs x 65 536 two-byte replies (every decoded value finite and bounded). Dynamic `signs` mode: speed stays `–`.

### SECURITY-06 MEDIUM, FIXED (0cf16e0): the poll loop had no pacing
An adapter that answers instantly drove the loop flat out. Baseline release vs `--mode instant`: **1 119 commands/s, 48.7 % of a core**; fixed: 123 commands/s, 18.9 % (about 50 cycles/s cap, `MIN_CYCLE_MS = 20`; real adapters are slower anyway). Emulator numbers; absolute CPU is not the head unit's.

### SECURITY-07 MEDIUM, FIXED (701214e): settings were stored and loaded without validation
Host (any length/characters), Bluetooth address, adapter name (shown on screen, announced by a remote device). Fix in `Settings.sanitized()` (runs on every load/update): host = `[A-Za-z0-9.-_:[]%]`, max 253, else default; MAC must be canonical or becomes "" (= `Link.Off`, never the simulator); name = no control/format/separator characters, max 64. Proof: `SettingsHardeningTest` (5 tests, a 16-key x 13-junk-value matrix never throws, idempotent). Dynamic (debug, `set` with a 3 000-char host containing `/../<x>&;|` + backticks, `bt=zz`, a name with U+202E and BEL, port 99999): stored `tcpHost` length 253, `bt` empty, `btName` length 64 and `ABCDEFzzz...` (bidi and BEL removed), `tcpPort` 35000.

### SECURITY-08 MEDIUM, OPEN: release APK is signed with the machine's debug key
Evidence: `apksigner verify --print-certs`: `CN=Android Debug`, SHA-256 `968cbbad...4149`, scheme v2 only. That key lives in `~/.android/debug.keystore` with the default password `android`; every debug-signed app built on this Mac shares the signer and can replace Cockpit in place (and the next `tools/release.sh` on another machine produces an APK that cannot update this one).
Done (8bbb54c): `app/build.gradle.kts` uses a `cockpitRelease` config when `COCKPIT_KEYSTORE`, `COCKPIT_KEY_ALIAS`, `COCKPIT_KEYSTORE_PASSWORD` (optional `COCKPIT_KEY_PASSWORD`) are set (v3 scheme enabled for later rotation); `tools/release.sh` warns when it falls back, and `COCKPIT_REQUIRE_RELEASE_KEY=1` refuses; `tools/security/make-release-keystore.sh` creates the key outside the repo (keytool prompts; nothing stored or printed). Verified with a throw-away key (random password, never stored): `Signer #1 certificate DN: CN=Cockpit TEST key`, v3 verified, 3072-bit; `release.sh` exits 2 without a key under `COCKPIT_REQUIRE_RELEASE_KEY=1` and for a missing keystore file.
**Owner steps:** run `tools/security/make-release-keystore.sh`, back the `.jks` up (password manager / encrypted disk), `export COCKPIT_KEYSTORE=... COCKPIT_KEY_ALIAS=cockpit; read -rs COCKPIT_KEYSTORE_PASSWORD; export COCKPIT_KEYSTORE_PASSWORD; tools/release.sh`, check the `signer` line. The first install over a debug-signed build needs an uninstall (settings are lost; there are few).

### SECURITY-09 MEDIUM, FIXED (ed19000) for the workflow, OPEN for action pinning
Before: no `permissions:` block (the token then has whatever the repository default is; I cannot see that setting), every event (including pull requests, which anyone can open on a public repo) uploaded an installable debuggable `cockpit-debug-apk`, and `docs/INSTALL.md` tells the owner to download it; Gradle cache writable from PR runs.
Now: `permissions: contents: read`; `persist-credentials: false`; job timeouts; cache read-only except `main`; the APK is uploaded only for pushes to `main` with its sha256 in the job summary; the wrapper jar's sha256 is checked; `scan-secrets.sh` and the hostile-adapter self-test run; `pull_request` (not `pull_request_target`) is used, so fork runs get a read-only token and no secrets (checked, clean). `.github/dependabot.yml` added (Gradle + Actions).
OPEN: `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`, `actions/setup-python@v5` are still tags. I could not resolve their commit SHAs (github.com is off limits for this review) and will not invent them. `python3 tools/security/pin-actions.py --write` does it (tested offline with a fake resolver, `test_pin_actions.py`; **not run against GitHub**). I could not check that CI is green on these edits (no network to GitHub; YAML parses, the wrapper check command was run locally with `shasum -c`).

### SECURITY-10 MEDIUM, OPEN: build toolchain has published advisories; shipped code has none
OSV (api.osv.dev, `Maven` ecosystem, query by exact version; sanity control `log4j-core 2.14.0` returned 8 vulns): the **74** artifacts of the resolved release runtime classpath (`:app:dependencies --configuration releaseRuntimeClasspath` + `:vehicle` runtime: androidx, Compose 1.7.6, Lifecycle 2.8.7, kotlin-stdlib 2.1.0, coroutines 1.9.0, ...) have **no advisory**.
The **147** artifacts of the Gradle build classpath (AGP 8.7.3, Kotlin plugin 2.1.0, their transitive tooling) include 15 with advisories: `org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0` (GHSA-r937-wjx7-w2jp, CVE-2026-53914, unsafe deserialisation in the Kotlin build cache metadata, fixed in 2.4.20; `org.gradle.caching=true` is on), `io.netty:netty-*:4.1.93.Final` (6 artifacts), `org.bouncycastle:bcprov/bcpkix-jdk18on:1.77`, `com.google.guava:guava:32.0.1-jre`, `com.google.protobuf:protobuf-java:3.22.3`, `commons-io:2.13.0`, `commons-compress:1.21`, `jose4j:0.9.5`, `jdom2:2.0.6`. These run only on the build machine / CI over this repository's own files; none is in the APK. Practical exposure is low; exploiting the Kotlin one needs write access to the build cache.
OPEN: upgrade AGP + Kotlin + Gradle together (needs `GB_ONLINE=1`; I did not attempt it: it changes the whole build and the Compose compiler plugin follows Kotlin). Files: `build.gradle.kts`, `gradle/libs.versions.toml`.

### SECURITY-11 LOW, FIXED (80349f1): `QUERY_ALL_PACKAGES` not needed
Replaced by a `<queries>` entry for `MAIN`/`LAUNCHER` (what `InstalledApps.query` asks). Evidence on the emulator: baseline release (with the permission) and fixed release both show **"17 apps"** and identical text sets on the Apps screen (37 text nodes each); `aapt2 dump badging` of the fixed APK lists no `QUERY_ALL_PACKAGES`. Other permissions: `INTERNET` (Wi-Fi adapter socket), `BLUETOOTH`, `BLUETOOTH_ADMIN` (`cancelDiscovery`), `BLUETOOTH_CONNECT` (not enforced at targetSdk 30; needed if the target is raised) - necessary, checked.

### SECURITY-12 LOW, FIXED (ed19000): Gradle wrapper without checksum
`distributionSha256Sum` was absent; added with the official value `f397b287...51c6`. Verified: I downloaded `gradle-8.11.1-bin.zip` and `shasum -a 256` equals `services.gradle.org/.../gradle-8.11.1-bin.zip.sha256`; a wrapper with a wrong sum on a fresh `GRADLE_USER_HOME` aborts with `Expected checksum: '000...' / Actual checksum: 'f397b287...'` (exit 1). The tracked `gradle-wrapper.jar` (sha256 `3b8a2577...8e98`) is **not** the 8.11.1 wrapper (34 of its 35 entries differ from, or are absent in, the 8.11.1 distribution's jars) but is byte-identical to the official **Gradle 9.8.2** wrapper jar (matches `gradle-9.8.2-wrapper.jar.sha256`; 9.8.2 is the Homebrew Gradle that generated it): authentic, harmless, now pinned in CI. `validateDistributionUrl=false` also present (skips an URL sanity check, LOW).

### SECURITY-13 LOW, FIXED (49edecb, 9265a18): tracked junk and `.gitignore` gaps
`tools/elm.log.1` and `.2` (2 MB, ELM327-emulator traffic) were tracked because `*.log` does not match `*.log.1`. Contain no secrets (scanned). Untracked; `.gitignore` now covers `*.log.*`, keystores, `.env*`, `keystore.properties`, `artifacts/`. They remain in history (rewriting a public history is the owner's call). `docs/PLAN.md` contains the author's local path `/Users/kuntal/...` (INFO).

### SECURITY-14 LOW, OPEN (owner only): repository settings
Cannot be changed or even read from here. Steps (also in `docs/SECURITY.md` section 8): Settings > Code security: Dependabot alerts + security updates, Secret scanning + Push protection, Private vulnerability reporting; Settings > Branches: protect `main` (PR + the three CI checks, no force push); Settings > Actions > General: workflow permissions read-only, approval for first-time contributors' workflows.

### SECURITY-15 LOW, WONTFIX: implicit intents for the dock shortcuts
`AppLauncher` fires `MAIN + CATEGORY_APP_MUSIC / APP_MAPS` and `ACTION_DIAL`; a malicious installed app can declare the same category and be offered/launched. It receives no data from us and Android shows its chooser when several apps match; the alternative (hard-coding the unit's package names) is what the design already plans for after the unit is inspected. `InstalledApps.launch` uses `getLaunchIntentForPackage(pkg)` with packages from the package manager's own list (no injection). Checked, no `PendingIntent`, content provider, FileProvider, clipboard, deep link or exported service/receiver of ours exists (the only other exported component is androidx `ProfileInstallReceiver`, protected by `android.permission.DUMP`, so only adb shell can call it).

### SECURITY-16 LOW, OPEN: the Wi-Fi host text box reconnects (and resolves DNS) on every keystroke
`SettingsScreen.TextBox` stores each non-blank partial value; typing `192.168.0.10` makes connection attempts to `1`, `19`, `192`... and, for a hostname, DNS lookups of the prefixes on the adapter's network. Harmless on an isolated adapter AP, but it leaks typed text to whatever resolver is configured. Not fixed (UI file, outside my brief); suggestion: store on focus loss / a "Use" button, or debounce 800 ms.

### SECURITY-17 INFO, WONTFIX: a lying adapter can show well-formed false values
`echoinject` (forged `41 0D FF` before the real answer) and `absurd` (`0xFF` bytes) made the Drive screen show **255 km/h, Top speed 255, Coolant 215 °C** (valid ranges of the PID encodings). Nothing in the app can tell a lying adapter from a true one; the guarantees that remain are that nothing can be sent to the car, that malformed/sentinel/stale data is not shown, and that the adapter cannot exhaust the app. Treat the adapter as part of the trusted car (`docs/SECURITY.md` section 2b/2d).

### SECURITY-18 INFO, checked clean: manifest, APK, platform settings
Debug and release `aapt2 dump xmltree`: one exported activity (MainActivity, MAIN/HOME/DEFAULT/LAUNCHER); `InitializationProvider` `exported=false`; `ProfileInstallReceiver` exported but `permission=android.permission.DUMP`; one app-defined signature-level permission (`...DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, protectionLevel 0x2, from androidx). `allowBackup=false` in both (dumpsys: `pkgFlags=[HAS_CODE ALLOW_CLEAR_USER_DATA]`, no `ALLOW_BACKUP`); release has no `debuggable` attribute and `run-as dev.kuntal.cockpit` answers `package not debuggable`; the debug APK is `DEBUGGABLE`. No `networkSecurityConfig`/cleartext flag needed: the only network use is a raw TCP socket (no `HttpURLConnection`, OkHttp, WebView, `addJavascriptInterface`, `Runtime.exec`, `DexClassLoader` or `content://` in the release dex; `strings` count 2 hits, both library code: `PendingIntent` class reference and `setPrimaryClip` in Compose). Native code: only `libandroidx.graphics.path.so` (androidx, 4 ABIs). R8: minified and obfuscated (only `MainActivity` keeps its name; `proguard-rules.pro` is empty) - strings stay readable, so obfuscation is not a security control here. `DebugProbesKt.bin` (coroutines) is in the release APK, harmless. Data readable from outside the sandbox: `/data/data/dev.kuntal.cockpit` is `drwx------ u0_a166`; `/sdcard/Android/data/dev.kuntal.cockpit` does not exist (the emulator's adb shell is root, an emulator artefact: on the unit only the sandbox rule matters). Log leaks: after a full session with host 10.0.2.2:35040 and a Bluetooth MAC set, `logcat -d` contains no host, port or MAC line; the only app log is `CockpitPerf` (timings). Fuzz of the exported activity on the fixed release (huge/typed/null/array extras, 30 KB `screen`, odd `screen`/`page`, `ACTION_DELETE`, `VIEW javascript:`, `content://settings/secure`, `file://` with grant flags, implicit HOME): every case handled, process alive, `AndroidRuntime FATAL/OOM/ANR in`: none. (A 100 KB and a 600 KB extra never reached the app: the `adb shell` command line rejected them, not a finding.) targetSdk 30 / minSdk 28: the app gets Android-11 behaviour on a newer OS (no scoped-storage enforcement, install-time Bluetooth permissions, old `PendingIntent` mutability defaults, background-start limits), none of which this app uses; revisit when the unit's Android version is known. Screenshots/recents: the screens show no secret (host and adapter name only); `FLAG_SECURE` not needed.

### SECURITY-19 INFO, checked clean: secrets and history
`git log --all -p` (29 commits, 5 branches, 3.3 MB): no `AKIA`/`ASIA`/`aws_secret`/`BEGIN ... PRIVATE KEY`/`ghp_`/`github_pat_`/`xox`/`AIza`/`sk-`/password-assignment hits; entropy scan of 32+ character added tokens: only test-method names and path fragments. Tracked-file check: no keystore, `.env`, `local.properties`, `.apk`, `.pem` ever committed (history file-type list: `gradle-wrapper.jar`, `drive-cycle.mp4` (3.9 MB), `tools/elm.log*`). Author identities: 3 (two local placeholders, the `noreply` GitHub address). `tools/security/scan-secrets.sh` (working tree) and `--history` both print `clean` on the final tree, and it fails correctly on a planted fake key and a `.jks`. Licence/trademark: `NOTICE.md` and the README notice are in place (the Ford marks are published on the owner's decision; a rights holder's request is the exposure); fonts OFL; the ELM327 emulator package (CC-BY-NC-SA) is dev-only and not bundled.

### SECURITY-20 INFO: not verifiable
Unit's real Android version/patch level, whether adb is exposed on the unit, whether the vendor launcher components (reverse camera, hardware keys) depend on Home; all affect (a) and (c) of the threat model.

## 4. Tools and tests added

| Path | Run |
| --- | --- |
| `vehicle/src/test/.../CommandGuardFuzzTest.kt` (13) | `tools/gb.sh :vehicle:test --tests '*CommandGuardFuzzTest*'` |
| `.../StreamExchangeLimitsTest.kt` (6), `.../HostileAdapterTest.kt` (8) | `tools/gb.sh :vehicle:test` |
| `app/src/test/.../DevLaunchTest.kt` (5), `.../data/SettingsHardeningTest.kt` (5) | `tools/gb.sh :app:testDebugUnitTest` |
| `tools/security/hostile_adapter.py` (13 modes: `--list`) | `python3 tools/security/hostile_adapter.py --mode endless --port 35040` |
| `tools/security/dynamic_modes.py` | own emulator, debug APK installed: `ANDROID_SERIAL=... python3 tools/security/dynamic_modes.py --serial $ANDROID_SERIAL` |
| `tools/security/scan-secrets.sh [--history]` | in CI (working tree); `--history` before a release |
| `tools/security/pin-actions.py` | owner: `python3 tools/security/pin-actions.py --write` (needs github.com) |
| `tools/security/make-release-keystore.sh`, `tools/release.sh` | owner, see SECURITY-08 |
| `tools/security/test_hostile_adapter.py` (8), `test_pin_actions.py` (3) | `cd tools/security && python3 -m unittest -v test_hostile_adapter test_pin_actions` |
| `tools/security/dyn.sh` | helper used for the dynamic run (forces the serial) |
| `docs/SECURITY.md`, `.github/dependabot.yml` | - |

## 5. Verification log

* Unit tests, final tree (`tools/gb.sh :app:testDebugUnitTest :vehicle:test :app:assembleRelease`, `BUILD SUCCESSFUL`): `:app` 132 tests, 0 failures; `:vehicle` 53 tests, 0 failures, 1 skipped (`TcpIntegrationTest`, needs `ELM_TCP`). Baseline had 148 JVM tests; I added 37 (27 in `:vehicle`, 10 in `:app`) and 11 Python tests.
* Red runs recorded: `CommandGuardFuzzTest` against the old guard: 9 of 13 failed (`AT PP FF ON`, `ATH1ATH1`, `ATZ ATZ`, `ATAL`...); `HostileAdapterTest` against the old parser/client: 6 of 8 failed (`-64.25`, unicode digits, trailing junk, `16383.75`, stale value, `took only 0 ms for 50 cycles`).
* APK facts: debug 18.6 MB, release 2.3 MB; manifests above; fixed release `aapt2 dump badging` permissions: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `INTERNET` (+ the androidx signature permission).
* Dynamic matrix, fixed debug build vs `hostile_adapter.py` (emulator-5560, 12 s per mode, `artifacts/dyn/modes-fixed.txt`; heap = Java heap PSS after the harness's own `dumpsys`, KB): noprompt 3 reconnects, heap 11 228; endless 5 reconnects, 11 500; drip 2, 11 176; longline (4 MB line) 5, 11 368; control 1 connection, 15 052, speed `–`; nodata 2 267 commands, 19 156, values `–` after the 40th reply; nodataflood 6 reconnects, 11 500; echoinject speed 255 (SECURITY-17); signs speed `–`; absurd speed 255, coolant 215 (SECURITY-17); instant 123 cmd/s, 24 % CPU (top, first sample); garbage 2 connections; flap 5 connections. Threads 23-25 in every mode, and the pid is the same in the 12 s and +3 s samples of all 13 modes (no restart). Commands the adapter received: always inside the whitelist. I did not collect `AndroidRuntime` for each mode in this run (the script now filters it per mode); the only crash check was the last mode and the activity fuzz (none).
* `tools/snap.sh` + `tools/cmp.py` on the fixed debug build after my MainActivity/DebugHooks edits: **drive 2.81 %**, **home 3.34 %** (limits 2.9 / 3.4; UI code untouched, only the snapshot gate moved).
* Wrapper: negative checksum test aborts (SECURITY-12). Signing: SECURITY-08. Secret scan: SECURITY-19.

## 6. Residual risk and recommendations

1. **Create the release key and stop shipping debug-signed builds** (SECURITY-08), then pin the actions (SECURITY-09) and flip the repository settings (SECURITY-14). These are the three things only the owner can do and they decide whether "an update" can be trusted.
2. **Treat the OBD adapter as trusted hardware.** A cheap Wi-Fi ELM327 clone is an unauthenticated open network device inside the car; the app can no longer be hurt by it and can no longer be made to write through it, but it cannot detect a lie (SECURITY-17). Prefer a paired Bluetooth adapter; do not join the adapter's AP for anything else.
3. Keep the dev hooks debug-only. If a feature later needs an inbound intent (a steering-wheel app, a widget), give it a dedicated, permission-protected component, not the Home activity's extras.
4. Any new ELM327 command or OBD service needs the owner's decision *and* a change to the pinned policy in `CommandGuardFuzzTest` (it fails on purpose until both agree).
5. Toolchain upgrade (SECURITY-10) and a decision on the unit's Android version (targetSdk 30, SECURITY-18/20).
6. Before the first on-unit test: run the section 7 checklist in `docs/SECURITY.md` and `dynamic_modes.py` once more against the exact build, including a Bluetooth adapter if one is used (not covered here).
7. `docs/PERF.md` and `docs/INSTALL.md` mention `--ez perf true` / `--ez snapshot true`: `snapshot` and `set`/`reset` now work only with a debug build (`tools/snap.sh` needs a debug install); `INSTALL.md` also tells the owner to download the CI APK - that is now only produced from `main`, and it is a debug build.
