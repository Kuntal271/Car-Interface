# ADR-0003: The app is read-only toward the car, enforced in code

Status: Accepted (owner rule in docs/PLAN.md; implemented in e7e7c4c, 2026-10-30)

## Context
The adapter is plugged into the car's OBD-II port. Writing (clearing DTCs, UDS writes, `ATPP` programmable parameters) can change vehicle
behaviour. This is a hobby app with no safety certification.

## Decision
Everything that reaches a `Transport` goes through `CommandGuard.isAllowed`: ELM327 `AT` setup commands (but never `ATPP`) and OBD-II mode 01
reads (`01xx`). `ElmClient.ask` calls `require(...)` before every exchange. Tests prove the commands sent by a full session are all allowed.
Canbox data (ADR-0012) is received only: the app never transmits on the Canbox UART or the car bus.

## Consequences
+ A bug in UI or settings code cannot produce a write; the safety property lives in one small, tested object.
- A blocked command throws `IllegalArgumentException`, which the repository's `catch` currently turns into a silent reconnect loop
  (ARCH-04): it should be loud in debug builds and logged in release.
- Any new transport (BLE, USB) must go through `ElmClient`, never talk to the adapter directly.
