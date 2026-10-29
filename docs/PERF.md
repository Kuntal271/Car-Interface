# Performance and latency — targets, physics, and the render-path decision

Owner target (2026-10-30): "<5 ms, peak smoothness, maybe a lower-level language than Kotlin".

## 1. What <5 ms can and cannot mean

- Touch-to-photon (finger moves -> pixels change) cannot be <5 ms on this hardware, in any language. The chain is: touch controller scan (typically 60-120 Hz, i.e. 8-16 ms between samples) -> kernel -> InputDispatcher -> app -> render -> SurfaceFlinger composition -> panel scanout. Each hop that waits for vsync costs up to one whole frame, and a 60 Hz panel's frame is 16.7 ms. Realistic end-to-end on Android is a few frames. (The emulator reports a 60.000 Hz period; the real panel's refresh and touch report rate are measured in recon.)
- What IS achievable and is the right way to get "peak smoothness":
  1. Per-frame cost (app CPU + GPU) p99 <= 5 ms, so every vsync is hit with ~11 ms of headroom for SurfaceFlinger, vendor overlays and a hot-dashboard thermal throttle.
  2. Zero missed vsyncs over a 10-minute drive-cycle run (janky frames < 1%).
  3. Input delivered at the touch sample rate, not batched to vsync (`requestUnbufferedDispatch`), and gesture response computed at render time from the latest event.
  4. No GC or allocation in the frame path; no recomposition per frame.

## 2. Where the time actually goes (language is a small factor)

HWUI/Skia (C++) does the drawing for Views and Compose alike; Kotlin only records the draw commands. ART runs Kotlin ahead-of-time compiled (install, then `adb shell cmd package compile -m speed -f <pkg>`); its GC is concurrent with sub-millisecond pauses, so GC only hurts if we allocate per frame. The usual jank sources on cheap head units are fill rate (1280x800 @ 60 fps with translucent layers), memory pressure (2 GB RAM, vendor services), CPU contention from the vendor stack, and heat. None of those is fixed by switching language; all are fixed by rendering less per frame.

## 3. Render-path options

| id | stack | per-frame control | cost | notes |
|----|-------|-------------------|------|-------|
| A | Kotlin + Compose, draw-phase-only gauges (`drawWithCache` static layer + `drawBehind` dynamic layer, no recomposition per frame) | good | lowest | planned default |
| B | Kotlin + `SurfaceView` with our own render thread, Canvas/GLES, `Choreographer` vsync callback | very good | medium | removes Compose/View overhead from the Drive screen only |
| C | NDK C++ (or Rust) renderer in a `SurfaceView`: GLES 3.x (Vulkan only if the GPU/driver is solid), `AChoreographer` vsync, glyph atlas (SDF), batched draws, own input thread, Swappy-style frame pacing | maximum, deterministic | highest | worth it only if A and B miss the gate on the real unit |

Hybrid is the sensible end state if C is needed: Kotlin/Compose for launcher, lists, settings, media (not latency-critical); a native renderer only for the Drive/gauge surface; vehicle data crosses JNI at <= 60 Hz through a direct ByteBuffer (no per-frame JNI calls, no allocation).

## 4. Decision method: spike on the real unit, not opinions

Build the same Drive screen (tach arc + speed numerals + boost bar, driven by DriveCycle at 60 Hz) as A, B, C. Install each on the unit; run 10 minutes each with the engine running; record:

| metric | how | gate |
|--------|-----|------|
| app frame cost p50/p99 | `adb shell dumpsys gfxinfo <pkg> framestats` (reset first), plus Perfetto trace | p99 <= 5 ms |
| janky frames | same | < 1% |
| missed vsync | `dumpsys SurfaceFlinger --latency <layer>` | 0 over the run |
| input-to-callback | in-app: `SystemClock.uptimeMillis() - event.eventTime` in `onTouchEvent` | p99 <= 3 ms |
| touch-to-photon | 240 fps phone camera filming finger + screen, frame counting | reported, not gated (hardware-bound) |
| memory / heat | `dumpsys meminfo`, 30 min soak in a parked sunny car | PSS < 200 MB, no throttle collapse |

Pick the lightest option that passes. Emulator numbers do not count (host GPU, no thermals).

## 5. Rules that apply whatever we choose

1. Opaque full-screen background; no alpha layers over large areas; no blur, no shadows, no gradients across large areas.
2. Static gauge furniture (ticks, numerals, labels) rendered once to a bitmap/texture; per frame only needles/progress arcs/digits change; digits from a pre-built glyph atlas.
3. Frame loop allocates nothing: pre-allocated Paths/Paints/arrays; value smoothing in primitives.
4. Vehicle data -> UI at <= 20 Hz; interpolate visually between samples in the frame loop.
5. Keep the process light: no background work on the render thread's cores; raise render-thread priority with `Process.setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)`.
6. Install-time: `cmd package compile -m speed -f dev.kuntal.cockpit` after every install; ship a baseline profile for the Compose parts.

## 6. Toolchain if C is needed

NDK + CMake via `sdkmanager "ndk;<version>" "cmake;3.22.1"` (about 1.5-2 GB; the Mac has ~18 GB free), `externalNativeBuild { cmake }` in :app, `minSdk 28`. Rust alternative: `cargo-ndk` + `android-activity`; skip unless the owner prefers it. Nothing is installed yet; the `:vehicle` module stays pure Kotlin because OBD parsing is not on the render path.
