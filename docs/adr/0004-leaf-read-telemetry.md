# ADR-0004: Telemetry is read at the leaves; the UI sees at most 20 updates per second

Status: Accepted (implemented in 2bf01f8 and a84d005, 2026-11-07)

## Context
Telemetry changes at the adapter's rate. Recomposing screens per sample would blow the frame budget on a weak CPU.

## Decision
Screens receive the `State<VehicleUiState>` object (not `.value`) and read it only inside text lambdas (`LiveTxt`) and draw scopes
(`SegBar`, `Tach`). The ViewModel samples the stream at 20 Hz (`PerfMode.Full`) or 10 Hz (`PerfMode.Light`, chosen by `PerfGovernor`
from measured frame times). Values glide between samples with `smoothed()` (exponential, per display frame, stops when settled).

## Consequences
+ A sample recomposes only the strings that changed; Compose compiler report: 85 of 140 composables skippable and the telemetry screens
  take a stable `State<>` even though `VehicleUiState` is unstable (it embeds `VehicleSample` from `:vehicle`).
- Unusual signature (`State<VehicleUiState>` instead of a plain UiState): must be documented for every new screen (docs/ARCHITECTURE.md §7).
- Trip counters are fed from the unsampled stream, so UI throttling never loses distance. Keep it that way in the hub (ADR-0008).
