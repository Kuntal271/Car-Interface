# ADR-0013: Keep the flat hand-rolled navigation; add a small state holder, back and home handling

Status: Proposed

## Context
Navigation is `rememberSaveable { screen: Dest }` and a `when`. There are eight destinations, no arguments, no deep links, no back stack: tapping the dock goes
directly to a top-level destination. Navigation-Compose would add a dependency, a NavHost and per-destination animation/transition machinery for none of that.

## Decision
Keep it. Extract a `NavState` holder (current `Dest`, `go(dest)`, `onHome()`), handle `onNewIntent` (pressing Home while the app is on top returns to Home) and add a
`BackHandler` (Back on a non-Home screen goes Home; on Home it is consumed, so the launcher is never finished). Settings sub-page stays local state. Revisit Navigation
only if a destination needs arguments or a real back stack.

## Consequences
+ No dependency, no transitions, trivial to test (`NavState` is a plain class).
- Back/Home behaviour on the real HOME role is unverified (Android 11 `finish()` of a root launcher activity relaunches it); verify on the unit.
