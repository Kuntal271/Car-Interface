# tools/perf: measuring the Cockpit app

Everything here talks to ONE adb device: `export ANDROID_SERIAL=<serial>` first (the scripts refuse to run without it).
Python 3 standard library only (use `tools/.venv/bin/python` if the system one is old). Run from the repo root.

| tool | what it answers |
|------|-----------------|
| `bench.py` | A/B frame cost per screen: interleaved runs, median and spread, "within noise" flag |
| `soak.py` | memory / thread / fd / CPU drift over 20+ minutes (leak check), optional screen cycling |
| `coldstart.py` | `am start -W` cold and warm start under each ART compile state (verify, speed-profile, speed) |
| `overdraw.py` | GPU fill proxy: screenshot with the system overdraw overlay, area per overdraw level |
| `unit-checklist.md` | the ordered list of what to run on the real head unit |

## The numbers and what they mean

* `app-cpu` (FrameMetrics animation + layout/measure + draw + sync, logged by the app under logcat tag `CockpitPerf` with `--ez perf true`)
  is the primary metric: it is the app's own per-frame cost without GPU wait. On a loaded Mac or an emulator the TOTAL duration is dominated
  by GL translation and by other processes; do not compare totals across machines.
* `main ms/frame`, `render ms/frame`: CPU time of the UI thread / RenderThread per rendered frame (from `/proc/<pid>/task/*/stat`).
  RenderThread time is the cost of encoding the draw ops, a proxy for how many ops a frame contains.
* `fps` on a *static* screen should be about 0 (a head unit stays on one screen for hours). `bench.py --scenario static --noperf`.
* `alloc KB/s`, `gc/s`: ART runtime stats (`art.gc.bytes-allocated`, `art.gc.gc-count`) logged next to the frame stats.
* Every run records the host load average (`host load`). Five agents share one Mac in the review; with load above ~8 compare only
  interleaved runs and never claim a difference that sits inside the min..max spread (the table prints "within noise").

## Typical sessions

```bash
export ANDROID_SERIAL=emulator-5554
tools/gb.sh :app:assembleRelease && cp app/build/outputs/apk/release/app-release.apk /tmp/new.apk

# 1. A/B two APKs, simulator data (worst case: 20 Hz, everything moving), governor pinned to Full
tools/perf/bench.py --apk base=/tmp/base.apk --apk new=/tmp/new.apk --screen home,drive --runs 5 --secs 30

# 2. Does Light mode cut work? Same APK, governor pinned to each mode
tools/perf/bench.py --apk full=/tmp/new.apk@full --apk light=/tmp/new.apk@light --screen drive --runs 5

# 3. Idle: frames per second and CPU on a screen that shows nothing new (no data source), without the perf overlay
tools/perf/bench.py --apk new=/tmp/new.apk --scenario static --noperf --screen home,drive,apps,settings --runs 3 --secs 60

# 4. Real protocol path: fake ELM327 adapter on the Mac (port per agent), app connects via 10.0.2.2
tail -f /dev/null | tools/.venv/bin/python -m elm -n 35010 -s car &
tools/perf/bench.py --apk new=/tmp/new.apk --scenario obd --obd-port 35010 --screen drive

# 5. 20-minute soak with a screen change every 90 s
tools/perf/soak.py --apk /tmp/new.apk --minutes 20 --cycle 90

# 6. Start-up under each compile state
tools/perf/coldstart.py --apk /tmp/new.apk --runs 7
```

Dev hooks used by the tools (all inert in normal use): `--ez perf true` (log + overlay), `--es perfmode full|light` (pin PerfGovernor),
`--es set "k=v,..."` (settings, e.g. `source=Simulator`, `source=ObdBluetooth,bt=` for "no source"), `--es screen home|drive|apps|settings`.

Compose compiler reports: `tools/gb.sh -PcomposeReports=true :app:assembleRelease` writes `app/build/compose_compiler/`
(`*-classes.txt` unstable classes, `*-composables.txt` skippable/restartable, `*-module.json` totals).
