# Installing Cockpit on the head unit

Cockpit is an ordinary Android app that offers itself as a Home (launcher) app. Installing it changes nothing on the unit's
system, and removing it brings the stock launcher straight back. **No root, no flashing, no firmware change.**

## 1. Build the APK

    tools/release.sh            # runs all unit tests, builds the minified release APK, prints path, size, sha256, signer

The result is `app/build/outputs/apk/release/app-release.apk` (about 2 MB). It is signed with the personal debug key:
fine for sideloading onto your own unit, not for any store. CI also builds a (larger, unminified) debug APK on every push;
download it from the workflow run's *Artifacts* if you do not want to build locally.

## 2. Get it onto the unit (pick what the unit allows)

- **USB stick / SD card:** copy the APK, open it with the unit's file manager, allow "install from this source" when asked.
- **adb over USB or Wi-Fi** (needs Developer options and USB debugging on the unit; whether the Palm Bay exposes them is
  still unknown, see "What I need from you" below):

      adb connect <unit-ip>:5555        # or plug in USB
      adb install -r app/build/outputs/apk/release/app-release.apk

Check that the checksum on the unit (or on the stick) matches the one `tools/release.sh` printed.

## 3. Make it the Home app

Press the unit's Home button. Android asks which launcher to use: choose **Cockpit** and **Always**. Until you do, the stock
launcher stays the default and Cockpit is just an app in the app list.

**Going back to the stock launcher:** Android Settings > Apps > Cockpit > *Clear defaults* (or Uninstall). On the next Home
press the unit asks again. Nothing else was touched, so this always works.

**Untested on your unit:** on some head units the vendor launcher also takes part in the reversing-camera switch or the
hardware buttons. Those normally run in the unit's own services, not in the launcher, but if the camera or a button misbehaves
while Cockpit is Home, clear the default as above and tell me what happened.

## 4. First run on the unit

1. Open **Settings > About**. Note the version line, switch **Frame-time overlay** on.
2. Go to **Drive** and **Home** and watch the line along the bottom of the screen for a minute each:
   `p50 .. p99 .. max .. ms  >12 ms ..%  missed ..%  PASS|FAIL  Full|Light`.
   The gate is **p99 at or under 10 ms and no missed frames**. If more than 2 % of frames run over 12 ms for two windows in a
   row, the app drops live updates from 20 Hz to 10 Hz by itself (`Light`) and returns when it recovers. Write the numbers
   into `docs/PERF.md`: they decide whether Compose is enough or the NDK path is needed.
3. **Settings > Display / Vehicle:** theme, 24 h clock, units, redline and shift-light settings.
4. Everything on the Drive screen is the built-in simulator until you pick a real source (next section).

## 5. Connect to the car

The app only ever sends the adapter's own `AT` setup commands and standard OBD-II **mode 01 reads**. A guard in the code
and its tests refuse anything else, so the app cannot write to the car's modules.

**Settings > Connections:**

- **OBD adapter, Bluetooth:** pair the ELM327 adapter in Android's Bluetooth settings first (button on the page), then choose
  it in the list. Classic Bluetooth (SPP) adapters only; BLE-only adapters are not supported yet.
- **OBD adapter, Wi-Fi:** join the adapter's own Wi-Fi network, then enter its address (most answer on `192.168.0.10`,
  port `35000`). While the unit is on that network it has no internet.
- The status line shows *Connected*, *Connecting...*, *Connection lost, retrying* (amber, retries after 1, 2, 4, 8, then every
  10 s) or *Simulated drive*. The top bar shows `OBD sim` while the simulator runs and plain `OBD` for a real adapter.

Idle-engine and parked checks first; do not fiddle with settings while driving.

## What works and what is not verified

| Area | State |
| --- | --- |
| UI, Settings, Midnight/Ford themes, units | Built and tested on the emulator |
| ELM327 over TCP, loss and automatic recovery | **Verified** against the ELM327 emulator from the Android emulator |
| ELM327 over Bluetooth | Built, **not verified** (needs a real adapter and unit) |
| Frame-time gate on the unit | **Not measured** (emulator timings do not apply) |
| Canbox data (doors, climate, steering wheel, radar) | **Not built**: the protocol dialect of this unit is unknown |
| Stock apps (Radio, Phone, Camera, CarPlay/Android Auto) | Launched from the dock by intent; not tried on the unit |

## What I need from you to continue

- A photo of the unit's **Settings > About** page, the **Canbox version string**, and whether **adb** can be enabled
  (Developer options). That decides the Canbox dialect, which gates the Car screen, doors/climate and the radar.
- The first on-unit frame-time numbers from step 4.
