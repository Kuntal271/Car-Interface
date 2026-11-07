# ADR-0010: Stay with :vehicle + :app; enforce layering by package rules, not by more Gradle modules

Status: Proposed

## Context
The code base is about 3,700 Kotlin lines in two modules. `:vehicle` is cleanly isolated (pure JVM, verified by `ArchitectureRulesTest`). Pure vehicle-domain logic
(`TripTracker`, `GearEstimator`, the link state machine) sits in `:app/data` only for historical reasons.

## Decision
No new Gradle modules. Move the pure vehicle-domain logic into `:vehicle` (it is JVM code about `VehicleSample`, tested faster there), keep Android adapters in
`:app/platform`, and keep layering honest with `ArchitectureRulesTest`. Revisit when a boundary becomes real: a Canbox decoder with its own release cadence, a
`:benchmark`/`:baselineprofile` test module (justified the day baseline profiles are generated), or more than about 25,000 lines.

## Consequences
+ No extra configuration time per build, no module graph to maintain for one developer. Incremental Kotlin compilation inside one module is fast at this size.
- Boundaries inside `:app` are a convention backed by one test, not by the compiler.
