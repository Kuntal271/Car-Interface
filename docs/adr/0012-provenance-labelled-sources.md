# ADR-0012: Every data source reports its origin; mock sources are selectable and always labelled 'sim'

Status: Proposed

## Context
Home shows Maps/Radio/Climate tiles and a media card whose real sources (Canbox, MediaSession) do not exist yet; a feature branch adds mock data. The product rule is
"never present invented numbers as live data". Today the only label is `Link.Simulated` -> "OBD sim", and `HomeContent.DEMO` is chosen by a debug flag.

## Decision
- `enum class Origin { Live, Simulated, Unavailable }` and a small `Reading`/slice wrapper per source: powertrain (`VehicleSample`, OBD), body (doors, trunk), climate,
  radar (Canbox), now-playing (MediaSession). Each slice has its own link state and origin.
- Each source is an interface returning a `Flow` of its slice: `VehicleSource` (exists), `BodySource`, `ClimateSource`, `RadarSource`, `MediaSource`. Canbox: a pure
  `CanboxDecoder` in `:vehicle` (port of `tools/canbox_decode.py`, same golden frames) + an Android `CanboxSource` adapter in `:app/platform` (vendor service, broadcast or UART: decided by recon).
  MediaSession: `MediaSessionSource` via a `NotificationListenerService` (user grant), with a `DemoMediaSource` twin.
- A mock twin exists for every real source and sets `Origin.Simulated`. Selection is explicit in Settings > Connections (`Off | Simulator | real`) and resolved in one place
  (`SourceFactory`, replacing `Sources`). A failing real source becomes `Unavailable`, never silently the simulator.
- UI labels come from the data: the status bar item shows "sim" when the slice it represents is simulated; a tile fed by a simulated slice shows a "sim" chip. No UI code reads a debug flag to decide.

## Consequences
+ The feature branch's mock data can land without weakening the rule, and real sources replace it one slice at a time.
- More types than today; justified because there will be five sources, not one. Keep `VehicleSample` powertrain-only.
