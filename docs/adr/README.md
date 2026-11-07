# Architecture decision records

One file per decision: context, decision, consequences. Status is Accepted (already true of the code), Proposed (recommended by the architecture review, not built), or Superseded.

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-compose-over-native-views.md) | Jetpack Compose for the whole UI (no XML views, no NDK renderer yet) | Accepted |
| [0002](0002-fixed-design-canvas.md) | Lay out on a fixed 1280x800 design canvas and scale it | Accepted |
| [0003](0003-read-only-command-guard.md) | The app is read-only toward the car, enforced in code | Accepted |
| [0004](0004-leaf-read-telemetry.md) | Telemetry is read at the leaves; the UI sees at most 20 updates per second | Accepted |
| [0005](0005-static-and-live-layers.md) | Split screens into a cached static layer and a small live layer | Accepted |
| [0006](0006-sideload-with-debug-key.md) | Sideload; release builds are signed with the local debug key | Accepted |
| [0007](0007-manual-di-app-container.md) | Manual dependency injection through an AppContainer; no Hilt, no Koin | Proposed |
| [0008](0008-process-scoped-vehicle-hub.md) | A process-scoped VehicleHub plus a foreground service own the vehicle data and the trip counters | Proposed |
| [0009](0009-settings-persistence.md) | Keep SharedPreferences behind a SettingsRepository; add a schema version; not DataStore yet | Proposed |
| [0010](0010-modules-and-packages.md) | Stay with :vehicle + :app; enforce layering by package rules, not by more Gradle modules | Proposed |
| [0011](0011-debug-release-source-sets.md) | Developer hooks live in the debug source set; release builds have inert stubs | Proposed |
| [0012](0012-provenance-labelled-sources.md) | Every data source reports its origin; mock sources are selectable and always labelled 'sim' | Proposed |
| [0013](0013-hand-rolled-navigation.md) | Keep the flat hand-rolled navigation; add a small state holder, back and home handling | Proposed |
| [0014](0014-quality-gates-baseline-mode.md) | Quality gates: detekt (+ Compose rules), Android lint and JaCoCo, all in baseline mode | Accepted |

New decision: copy the shape of any file here, take the next number, add a row above.
