# ADR-0005: Split screens into a cached static layer and a small live layer

Status: Accepted (implemented in 2bf01f8, 2026-11-07)

## Context
Home has about 100 elements; only a dozen change with telemetry. Re-recording all of them per sample cost app-CPU p99 30.5 ms on the emulator.

## Decision
Content that never changes with telemetry is drawn inside one `graphicsLayer()` render node (`Layer { ... }`), live values in a second one.
`Tach` caches its track/ticks/numerals with `drawWithCache` and only draws the sweep arc per frame.

## Consequences
+ Measured on the emulator: app-cpu p50 5.7 -> 3.8 ms, p99 30.5 -> 12.2 ms with identical pixels (docs/PERF.md). Not yet measured on the unit.
- Two layers per screen to keep in sync; a value placed in the wrong layer is correct but slow, and nothing flags it. A review checklist item
  (and a refactor-plan step) is to name the layers in each screen file.
