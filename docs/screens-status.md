# Screen status (M3, feature branch)

Measured on the Android 11 arm64 emulator, Ford theme, 1280x800. "Compare" is `tools/snap.sh <screen>` + `tools/cmp.py <screen>`: the share of
pixels that differ visibly from the design render (target <= 5 %; drive <= 2.9 %, home <= 3.4 % must not move). "Idle fps" is frames drawn per second
over 10 s (`tools/perf.sh`, `dumpsys gfxinfo`) with the OBD **Simulator** source running and Demo data on. Screens that show live OBD numbers redraw by
design; a mock-only or static screen should sit near 1 fps (the status-bar clock).

Legend for data: **real** = live OBD / device; **estimate** = derived from real OBD with an assumption, labelled on screen; **mock** = deterministic demo data
behind an interface, labelled "Demo data", and replaced by "Waiting for ..." when Settings -> Connections -> Canbox and media is Off.

| Screen | Owner | Data | Compare | Idle fps | Known gaps |
|---|---|---|---|---|---|
| home | app (existing) | real OBD + mock tiles (labelled) | 3.34 % (unchanged) | 17.8 (live tiles) | - |
| drive (Cluster) | app (existing) | real OBD | 2.81 % (unchanged) | 34.9 (gauges) | tap the Boost card or swipe to reach the other two faces |
| drive-boost | app | real OBD (MAP - BARO), 0-100 timer is computed | 5.13 % | 17.6 (live) | run list is per session only; chart axes follow the pressure unit |
| drive-cruise | app | real OBD + **estimate** (fuel use by speed-density, VE 0.85, tank 45 L assumed), smoothness from jerk | 2.74 % | 18.4 (live) | range uses an assumed 45 L tank; shift hint shows from shift rpm - 400 |
| car | app | **mock** Canbox | 3.26 % | 2.7 | real Canbox frames not decoded yet (interface `CanboxSource`) |
| media | app | **mock** player (`MediaSource`); volume is the real music stream | 3.52 % | 2.4 | no real media session; EQ pill reads the mock `AudioSettings` |
| trips | app | **real** recorded trips (FileTripStore + TripRecorder) plus labelled demo trips | 5.31 % | 1.6 | text 1-3 px off in places; export writes CSV to the app's files dir, not USB |
| parking | app | **mock** radar; no camera (the unit's camera app owns it) | 2.11 % | 1.4 | camera view is a drawn stand-in, "Open the camera app" launches the real one |
| diagnostics | app | real OBD table and live trend; fault list is **mock** | 5.69 % | 18.0 (live table) | **mode 03 is never sent** (CommandGuard untouched): the fault list is invented and says so |
| sound | app | **mock** `AudioSettings` (no DSP access) | 3.55 % | 1.4 | controls change remembered values only; no audible effect |
| quick (panel) | app | real: brightness (window), music volume, Wi-Fi/BT state | 3.41 % | not measured (modal) | Wi-Fi/BT/Hotspot open the system page (a launcher cannot switch them); Night = dim overlay; Power menu is a notice |
| overlays 1-8 | app | volume, coolant, battery, low fuel real; door/climate suppressed in demo; call/reverse have no real trigger yet | 1.76-9.47 % (see below) | n/a | design backdrop is Drive in another live state; overlay cards themselves match |
| boot | app | none | 1.39 % | animates 3.6 s by design | default on for a plain launch; skippable by touch; Settings -> Display -> Start-up splash |
| standby | app | OBD battery, Canbox (mock) outside temp and doors, last trip | 4.98 % | 2.0 | "hold to power off" not implemented; timeout is Off by default (Settings -> Display) |
| phone | **vendor app stays stock**; in-app demo stand-in | **mock** | 5.69 % | 1.4 | demo only: Dock Phone launches the vendor app when Demo data is Off |
| phone-call | vendor; demo stand-in | **mock** | 5.08 % | 1.4 | no audio, no real call |
| radio | vendor; demo stand-in | **mock** tuner (`RadioState`) | 6.62 % | 1.3 | Media -> Radio launches the vendor tuner when Demo data is Off |
| projection | vendor; demo stand-in | **mock** link status | 5.35 % | 1.4 | Dock Maps launches maps when Demo data is Off; "Android Auto" button launches the real app |
| apps | app (existing) | real installed apps | 7.46 % (not touched; the design list differs from the emulator's apps) | 1.4 | - |
| settings | app (existing) | real | 9.55 % (Display page gained Start-up splash and Standby clock rows, Connections gained Canbox and media: none are in the design) | - | - |

Overlay compare numbers: overlay1 2.77, overlay2 3.19, overlay3 7.47, overlay4 3.21, overlay5 9.47, overlay6 1.76, overlay7 2.71, overlay8 7.25 %.

Ways in, besides the dock: Home recent row (Trips, Parking, Diagnose, Sound), Car -> Parking, Media -> Sound / Radio, status bar tap -> Quick panel.
Tools: `tools/ship.sh <screen>...` builds, installs, snapshots and compares; `tools/perf.sh <screen>...` measures idle fps; screenshots are `docs/shots/m3-*.png`.
