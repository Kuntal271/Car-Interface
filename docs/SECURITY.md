# Security

Cockpit is a personal Home (launcher) app for one head unit in one car. It is installed by sideloading, talks to the car only
through an OBD-II adapter, and lives in a **public** GitHub repository. This page is the threat model, the guarantees the code
makes, the hardening decisions, how to report a problem, and a checklist for each release. The review that produced it, with
evidence, is `docs/reviews/security.md`.

## 1. Assets

| Asset | Why it matters |
| --- | --- |
| The car's data link (OBD-II port, CAN bus behind it) and any CAN-adjacent adapter | A wrong frame is a safety problem. The app must never be a way to write to the car. |
| What the driver sees (rpm, speed, temperatures, link state) | Wrong numbers shown as live are an integrity failure. |
| The owner's settings (adapter host / Bluetooth address, units, thresholds) | They decide where the app connects. |
| The installed-app list and the Home role | The launcher sees every launchable app and is what the Home button opens. |
| The public repository, CI, and the signing key | A poisoned build or a leaked key reaches the unit as an "update". |

## 2. Attackers and what they can try

| # | Attacker | Capability | Main defences | Residual risk |
| --- | --- | --- | --- | --- |
| a | Another app on the unit | Start our exported Home activity with any intent extras; register competing intent filters; read what is world-readable | Release builds ignore the state-changing launch extras (`DevLaunch`); no other exported component; `allowBackup=false`; no content provider, no `PendingIntent`, no clipboard use; private storage | An app can still start the activity on a chosen *screen* (navigation only). A malicious app can claim the same implicit category as the music/maps shortcuts and be launched by the dock. |
| b | A rogue or compromised OBD adapter (Wi-Fi AP or Bluetooth) | Send any bytes back: endless data, no prompt, forged answers, NO DATA, sentinel values | Bounded reply size and time; strict hex parsing; plausibility limits; values that stop being answered are cleared; poll rate capped; reconnect with back-off; and the **command guard**, which keeps the app from ever sending anything but AT setup + mode 01 | The adapter *can* feed believable false numbers (they are well-formed hex). Nothing in the app can tell a lying adapter from a true one. Treat the adapter as part of the trusted car; do not use one you do not control. |
| c | Physical access (valet, passenger, USB stick) | Use the touch screen, plug in USB, adb if the unit exposes it | Settings hold nothing secret; nothing in the app writes to the car; sideloading is a unit setting, not ours | Anyone who can use the screen can change settings or leave Cockpit as Home. A debug build or a stick with a different APK is a full replacement of the app (see signing). |
| d | A network attacker near the car on the adapter's open Wi-Fi | Join the adapter's open network, spoof its address, man-in-the-middle the plain TCP | Same hostile-input defences as (b); the guard still applies to what we send | The ELM327 protocol has no authentication or encryption; there is none to add on our side. Prefer Bluetooth (paired) where the adapter supports it; never use the adapter network for anything else. |
| e | Supply chain: dependencies, GitHub Actions, build signing, the public repo | Poisoned dependency or action, a PR that changes CI, leaked keys, a counterfeit APK | Least-privilege workflow token; APK artifact only from `main`; Gradle wrapper pinned by checksum; secret scan in CI; Dependabot; release key outside the repo | Actions are still referenced by tag (owner action below); the release key is not created yet; branch protection and secret scanning are repository settings only the owner can switch on. |

## 3. Guarantees (and their limits)

1. **Read-only toward the car.** The only bytes the app can put on an adapter link are: the 19 ELM327 `AT` strings in
   `CommandGuard.AT_ALLOWED`, and `01` + one PID byte (OBD-II mode 01 "show current data"). The guard is an exact match on the
   canonical spelling, with no normalisation, and it is enforced twice: in `ElmClient` and again in `exchangeOverStreams`, the one
   function every transport writes through. Tests: `CommandGuardFuzzTest` (about two million random strings, every ELM327 `AT`
   command of the datasheets in many spellings, all 65 536 two-byte requests, look-alike Unicode, control characters, 1 MB inputs).
   *Limit:* it protects the CAN bus from this app, not from the adapter: a hostile adapter can transmit what it likes by itself.
2. **Bounded input.** One adapter reply is at most 2048 characters and 10 s. More ends the session (the repository then
   reconnects after 1, 2, 4, 8, 10 s). Memory and threads do not grow with a hostile adapter (measured, review section 5).
3. **No invented live data.** Only well-formed ASCII hex is decoded; sentinel values (rpm 16 383.75, 65.5 V) are dropped; a
   value whose PID has not been answered three times in a row is cleared to a dash. The simulator is the only source that may
   produce numbers by itself, and the status bar then says `OBD sim`.
4. **Launch extras cannot change state in a release build.** `set`, `reset` and `snapshot` need a debuggable build. `perf`,
   `screen` and `page` stay (frame-time overlay, enum-checked navigation) because a *release* build is what is measured on the unit.
5. **Private storage, no backup.** Settings are in app-private SharedPreferences; `allowBackup="false"`; the release build is
   not debuggable (`run-as` is refused).

## 4. Command guard: what is allowed and what is not

Allowed `AT`: `ATZ` (reset), `ATI` `AT@1` `ATRV` `ATDP` `ATDPN` (read identity, voltage, protocol), `ATE0/1` `ATL0/1` `ATS0/1`
`ATH0/1` (output format), `ATAT0/1/2` (reply-wait timing), `ATSP6` (ISO 15765-4 CAN 11-bit/500 k, the EcoSport's bus), `ATCAF1`
(CAN auto-formatting on, the default). Plus exactly `01xx` in upper case.

Refused, and why (each is a test case):

| Class | Commands | Risk |
| --- | --- | --- |
| Persistent adapter changes | `ATPP`, `ATPPS`, `AT PP ...` (spaces are ignored by the adapter), `ATPB` | Writes the adapter's EEPROM (baud rate, default protocol, power control); survives power cycles |
| Link breakers | `ATBRD`, `ATBRT`, `ATLP`, `ATMA`, `ATMR`, `ATMT` | Baud-rate change that strands the link; low power; monitor modes never return a prompt |
| Choosing what the adapter transmits | `ATSH`, `ATCP`, `ATTA`, `ATSR`, `ATCEA`, `ATCRA`, `ATCF`, `ATCM`, `ATFCSH/SD/SM`, `ATCAF0`, `ATV1`, `ATJE`, `ATJS` | A header/address or raw framing turns "01 xx" into a frame with an arbitrary CAN id |
| Adapter-initiated traffic | `ATWM`, `ATSW`, `ATIB`, `ATIIA`, `ATKW`, `ATSI`, `ATFI`, `ATBI` | Wake-up messages and K-line initialisation sequences |
| Protocol search / change | `ATSP0`, `ATSPA`..., `ATTP`, `ATTPA` | Probes other physical buses |
| Everything not on the list | `ATD`, `ATWS`, `ATST`, `ATAL`, `ATNL`, `ATM`, ... | Not needed; least privilege |
| Every other OBD / UDS service | `03` `04` (clear DTCs) `07` `08` `09` `0A` `10` `11` `14` `22` `27` `28` `2E` `2F` `31` `3B` `3D` `85` | Anything but `01xx` can change or clear ECU state |

Adding a command to the list is a product decision for the owner, and `CommandGuardFuzzTest.allowedSetIsExactlyThePinnedPolicy`
is written to fail until the pinned policy in the test is changed with it.

## 5. Hardening decisions

* **`MainActivity` is exported** (it must be: it is the Home activity). Its dev hooks are gated at one entry point (`DevLaunch.parse`).
* **`QUERY_ALL_PACKAGES` removed** in favour of a `<queries>` entry for launcher activities: the Apps grid lists the same apps,
  without being able to enumerate every package on the unit.
* **No `INTERNET`-facing server, WebView, content provider, receiver, service, `PendingIntent`, clipboard access, file sharing or
  logging of hosts/addresses.** The only log line is the frame-time statistics under `CockpitPerf`.
* **targetSdk 30** is deliberate (Android 11 unit); it means fewer platform protections than a current target (no scoped
  `exported` enforcement, install-time Bluetooth permissions, implicit `PendingIntent` mutability). The manifest already sets
  `exported` explicitly, and the code uses no `PendingIntent`. Raise it when the unit's Android version is known.
* **Signing.** A release is signed with a dedicated key kept outside the repository, passed through the environment
  (`tools/release.sh`, `tools/security/make-release-keystore.sh`). Without it the build falls back to the machine's *debug* key,
  which is shared with every debug-signed app built on this Mac; set `COCKPIT_REQUIRE_RELEASE_KEY=1` to refuse that.
* **CI** runs with `contents: read`, publishes the (debuggable) APK only from `main`, checks the Gradle wrapper jar's checksum, and
  runs `tools/security/scan-secrets.sh`.
* **Dependencies** are listed in `gradle/libs.versions.toml`; the resolved runtime classpath (74 artifacts) has no OSV advisory.
  Build-time tooling (Android Gradle plugin 8.7.3, Kotlin 2.1.0) pulls in libraries with advisories (netty, bouncycastle, ...);
  they run only on the build machine, on files from this repo.

## 6. Reporting a vulnerability

Please do not open a public issue with exploit details. Use GitHub's **Report a vulnerability** button on the repository's
Security tab (private advisory) once the owner has enabled it, or contact the owner through the GitHub profile
`Kuntal271`. Include the build (Settings > About), the steps, and what you observed. This is a one-person hobby project:
expect an answer within days, not hours. There is no bounty.

## 7. Per-release security checklist

```
tools/release.sh                              # tests + minified release APK; read the "signer" line: it must NOT be "Android Debug"
tools/security/scan-secrets.sh --history      # no credentials in any branch, no keystores/.env/apk tracked
python3 -m unittest discover -s tools/security -p 'test_*.py'
BT=$ANDROID_HOME/build-tools/$(ls $ANDROID_HOME/build-tools | sort -V | tail -1)
$BT/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | grep -E "uses-permission|debuggable"
                                              # expect: BLUETOOTH*, INTERNET only; no "application-debuggable"
adb shell run-as dev.kuntal.cockpit ls        # expect: "package not debuggable"
tools/security/dynamic_modes.py               # debug build against the hostile adapter (see its header): no crash, bounded memory
```
Then re-check dependencies against OSV (`curl -s https://api.osv.dev/v1/query -d '{"package":{"name":"group:artifact","ecosystem":"Maven"},"version":"x"}'`)
and the Android security bulletin for the platform version on the unit; record the APK's sha256 next to the release.

## 8. Owner actions (only the repository owner can do these)

1. **GitHub settings > Code security**: enable *Dependabot alerts*, *Dependabot security updates*, *Secret scanning* and *Push protection*, and *Private vulnerability reporting*.
2. **Settings > Branches**: protect `main` (require a pull request and the three CI checks, no force-push). Settings > Actions > General: *Workflow permissions* = read-only; *Fork pull request workflows* = require approval for first-time contributors.
3. **Pin the actions**: `python3 tools/security/pin-actions.py --write` (needs github.com; review and commit the diff). Dependabot then keeps the pins current.
4. **Create the release key**: `tools/security/make-release-keystore.sh`, back the file up, then build with `COCKPIT_KEYSTORE`, `COCKPIT_KEY_ALIAS`, `COCKPIT_KEYSTORE_PASSWORD`. The first install over an existing debug-signed Cockpit needs an uninstall (settings are lost; they are few).
5. **Toolchain**: Kotlin Gradle plugin 2.1.0 has a published advisory (unsafe deserialisation in the Kotlin build cache, fixed in 2.4.x) and AGP 8.7.3 drags in old netty/bouncycastle: schedule an AGP + Kotlin + Gradle upgrade (needs network: `GB_ONLINE=1`).
