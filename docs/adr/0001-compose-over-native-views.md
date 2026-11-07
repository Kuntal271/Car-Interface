# ADR-0001: Jetpack Compose for the whole UI (no XML views, no NDK renderer yet)

Status: Accepted (owner decision 2026-10-30, docs/PERF.md)

## Context
The unit is a Blaupunkt Palm Bay 1000: about 2 GB RAM, probably Cortex-A53 class CPU, weak GPU. The owner wants "peak smoothness" and
mentioned a lower-level language. `docs/PERF.md` shows the cost per frame is dominated by fill rate, memory pressure and vendor-stack CPU
contention, not by the language: HWUI/Skia (C++) draws Views and Compose alike, Kotlin only records draw commands.

## Decision
Kotlin + Jetpack Compose (foundation + ui only: no Material3, no blur, no shadows) with a custom design system. Gate on the real unit:
per-frame cost p99 <= 10 ms (stretch 5 ms) and zero missed vsyncs. Held in reserve and built only if the gate is missed on the unit:
(B) a Kotlin `SurfaceView` for the Drive screen, then (C) an NDK GLES renderer for it. `:vehicle` stays pure Kotlin either way.

## Consequences
+ One language and toolchain, fast iteration, JVM-testable logic, Compose's draw-phase state reads give recomposition-free gauges (ADR-0004).
+ Escape ladder exists and is measured, not guessed.
- Compose runtime and first-composition cost on a cold JIT: needs a baseline profile and `cmd package compile -m speed` after install.
- The gate has NOT been measured on the unit yet; emulator numbers do not count.

## Alternatives rejected
Native Views (more boilerplate, no better frame cost), NDK first (weeks of work for an unmeasured problem), Flutter/WebView (extra engine, old WebView risk).
