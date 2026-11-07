# ADR-0007: Manual dependency injection through an AppContainer; no Hilt, no Koin

Status: Proposed

## Context
Wiring is done today with global objects: `SettingsStore.get(ctx)`, `Sources`, `Perf`, `InstalledApps`, `AppLauncher`, `DebugHooks`. That makes the
pipeline impossible to test on the JVM (the ViewModel needs an `Application`) and hides dependencies. The graph is tiny: about eight objects.

## Decision
A hand-written `AppContainer` created in `CockpitApplication.onCreate` (lazy members) holds the singletons: settings repository, vehicle hub,
apps repository, clock, dispatchers. ViewModels are created with `viewModelFactory { initializer { ... } }` from the container. Collaborators are
constructor parameters typed as small interfaces (`SettingsRepository`, `VehicleSources`, `Clock`, `DispatcherProvider`).

## Consequences
+ No annotation processor/KSP, no generated code, no reflection, no startup work beyond object construction: build time and cold start stay as
  they are on a weak unit. Fully visible in a debugger.
+ Everything that matters is injectable in tests (`FakeVehicleSource`, `FakeClock`).
- Hand-maintained wiring: acceptable up to about 15 objects. Revisit Hilt only if the graph passes that or a second developer joins.
## Alternatives rejected
Hilt: KSP/kapt step and generated components for ~8 objects, slower clean builds, and `@AndroidEntryPoint` on a single activity. Koin: runtime
resolution and ~300 KB for no compile-time safety; startup cost is small but nonzero on a Cortex-A53. NiA uses Hilt because it has dozens of modules and
screens; this project has neither.
