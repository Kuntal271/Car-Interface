# ADR-0008: A process-scoped VehicleHub plus a foreground service own the vehicle data and the trip counters

Status: Proposed (fixes ARCH-01)

## Context
A launcher is stopped every time the owner opens Maps, Radio or the phone. Today the pipeline lives in `CockpitViewModel` behind
`WhileSubscribed(5_000)` twice, so polling stops about 10 s after `onStop`, the trip counters restart from zero, and driving done while another app
is in front is never counted (pinned by `LauncherBackgroundingTest`). The process can also be killed (about 2 GB RAM, vendor task killers, ACC cycles).

## Decision
1. `VehicleHub` (plain class, owned by `AppContainer`) collects the selected source in an application-scoped coroutine on a confined background
   dispatcher. It owns the `VehicleRepository`/`TripTracker` and publishes `StateFlow<VehicleSnapshot>`. The UI only collects; the 20 Hz sampling stays in the UI layer.
2. The hub's lifetime is the vehicle's, not the screen's: it runs while the ignition is on (adapter answers) and backs off to a slow probe when the engine
   is off, so the car battery is not drained.
3. A foreground service (`VehicleService`, type `connectedDevice`, low-importance notification) raises the process priority while the hub runs. It holds no
   logic: it starts/stops the hub with the process.
4. Trips are checkpointed every few seconds and on engine-off (`TripStore`: one small file), and restored on start. "Trip A" resets only when the owner resets it.
5. Settings changes restart the source only when `sourceKey` changes (unchanged), and a restart keeps the trip.

## Consequences
+ Correct trip counters; instant, non-stale first frame; recovery logic (backoff) keeps running while another app is in front.
+ Testable on the JVM with `FakeVehicleSource` and a test scope (no Android).
- A foreground service needs the `FOREGROUND_SERVICE` permission (normal, install-time), a notification channel and, on targetSdk >= 34, a foreground service
  type + permission. Head units often hide the notification shade; the notification is still required by the platform. Android 12+ limits starting it from
  the background (not an issue while the activity is in front, or from a BOOT_COMPLETED receiver).
- Battery: polling with the key off must stop. Needs an engine-off policy (no answer / rpm = 0 for N minutes -> slow probe), and a test on the car.
- More moving parts than a ViewModel; keep the service empty so it cannot grow logic.
