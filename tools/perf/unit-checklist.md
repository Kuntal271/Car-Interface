# What to measure first on the real unit (ordered; stop at the first step that fails the gate)

Gate: frame cost p99 <= 10 ms (stretch 5 ms) and zero missed vsyncs, release build, on every screen, over a 10 minute run.
`adb connect <unit>:5555` (or USB), `export ANDROID_SERIAL=<unit>`, release APK installed (`adb install -r app-release.apk`).

1. **Facts about the unit** (30 s, paste into docs/PERF.md): `adb shell getprop ro.build.version.release ro.product.model ro.hardware ro.product.cpu.abi`,
   `adb shell wm size; wm density`, `adb shell dumpsys SurfaceFlinger | grep -E "refresh-rate|GLES"`, `adb shell cat /proc/meminfo | head -3`,
   `adb shell nproc; adb shell cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq`, `adb shell dumpsys thermalservice | head -30`.
   Is the display really 60 Hz? Is the renderer Skia GL or Vulkan? Which GPU?
2. **Ahead-of-time compile** (the single biggest start-up lever; the installer does NOT do it for a sideloaded APK):
   `adb shell cmd package compile -m speed -f dev.kuntal.cockpit`. Then `tools/perf/coldstart.py --apk app-release.apk --states verify,speed`
   (cold start `verify` = as installed, `speed` = compiled). Expect `speed` to be clearly faster; keep it as an install step.
3. **Idle** (the unit sits on one screen for hours): `tools/perf/bench.py --apk unit=app-release.apk --scenario static --noperf --screen home,drive --runs 2 --secs 120`.
   Pass: `fps` below 0.1, `wakeups/s main` below 5, `proc cpu ms/s` below 20. If not, something redraws or ticks while nothing changes:
   check `adb shell dumpsys gfxinfo dev.kuntal.cockpit framestats` for the interval between frames.
4. **The gate itself** on the moving case: `tools/perf/bench.py --apk unit=app-release.apk --scenario sim --screen home,drive,settings --runs 3 --secs 60 --perfmode full`
   then read `app-cpu p50/p99` (app work) and `total p99` (includes GPU wait). Also the in-app overlay: Settings > About > Frame-time overlay.
   * `app-cpu p99` over 5 ms: CPU-bound in Compose. Look at `main ms/frame`, run `--perfmode light` (halves gauge frames) and compare.
   * `app-cpu` fine but `total p99` over 10 ms: GPU/fill-bound. Run the same with `--perfmode full+flat` (static layers as textures; adds `--ez flatten true`)
     and compare `total p99`. Then look at `adb shell setprop debug.hwui.overdraw show` (restart the app) and
     `adb shell dumpsys gfxinfo dev.kuntal.cockpit framestats` (GPU column exists on Android 12+).
5. **Apps with the real list**: open Apps, fling top to bottom and back twice with the perf overlay on; note p99 and `adb shell ls /proc/$(pidof dev.kuntal.cockpit)/task | wc -l`
   (thread count must stay below ~40). Type three letters in the search field: results must follow without a visible stall.
6. **Real adapter**: source = Bluetooth/Wi-Fi OBD, engine running, 5 minutes on Drive: `tools/perf/bench.py --scenario obd` does not apply (needs TCP);
   run the app by hand and read the overlay, plus `adb shell top -H -n 1 | head -15` to see which thread is hot (OBD thread vs main vs RenderThread).
7. **Soak** (thermal and leaks): `tools/perf/soak.py --minutes 30 --cycle 120` in a parked, sun-warmed car if possible. Pass: PSS slope near 0, threads and fds flat,
   no frames above 16.7 ms appear after the unit warms up (compare the first and last 5 minutes).
8. **If it still misses**: capture one Perfetto trace of the worst screen
   `adb shell perfetto -o /data/misc/perfetto-traces/t.pftrace -t 15s sched freq gfx view input ss binder_driver` and `adb pull` it; open in ui.perfetto.dev.
   Escalation ladder is in docs/PERF.md (Light = 10 Hz data + 30 fps gauges, flatten, lean visuals, SurfaceView gauge).
