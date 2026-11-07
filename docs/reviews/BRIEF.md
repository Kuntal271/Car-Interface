# Shared brief for the review agents

Read this first. It applies to every agent. Your own task text adds to it, never overrides the safety rules.

## The product
A Kotlin + Jetpack Compose launcher (package `dev.kuntal.cockpit`) for a Blaupunkt Palm Bay 1000 Android head unit in a 2015 Ford
EcoSport (India), plus a live vehicle dashboard fed by an ELM327 OBD adapter (and later a Canbox). Landscape, drawn on a fixed
1280x800 design canvas that is scaled to whatever the display is. minSdk 28, targetSdk 30, compileSdk 35, Java 17.
Assume the real unit is LOW-END (weak GPU, about 2 GB RAM, unknown Android version, possibly old WebView/GL). It has not been
tested on the unit yet; only on an Android 11 arm64 emulator.

Modules: `:vehicle` (pure Kotlin: ELM327 client, PID parsing, `CommandGuard` read-only whitelist, simulator) and `:app`.
`:app` packages: `data/` (pure logic: Settings, Smoother, Units, TripTracker, VehicleRepository, GearEstimator, Fmt, BoostBar,
AppIndex...), `perf/` (FrameStats, PerfGovernor, FrameMeter), `source/` (Sources, BluetoothTransport), `ui/{theme,components,shell,screens}`.
Docs: README.md, docs/PLAN.md, docs/DESIGN.md, docs/PERF.md, docs/INSTALL.md. Reference design (21 screens, the spec): docs/design/site/.

Hard product rules
- The app is READ-ONLY toward the car: only ELM327 `AT` setup commands and OBD-II mode 01 reads (`CommandGuard`). Never weaken that.
- Performance gate on the real unit: frame cost p99 <= 10 ms (stretch 5 ms), zero missed vsyncs.
- Design rules: palettes only via `LocalPalette` (never hard-coded colours), NO red anywhere, text >= 18 px, touch targets >= 56,
  Barlow / Barlow Condensed only, sentence case, at most one Ford oval per screen.
- Never present invented numbers as live data. Mock data must be labelled (status-bar indicator reads "sim").

## Your sandbox (the machine is shared by five agents: be a good neighbour)
- Work ONLY in your own git worktree `~/ecosport-wt/<name>` on branch `review/<name>` (already created from the same commit).
  Never touch `~/ecosport-cockpit` (the main checkout), never switch branches there, never push, never force-anything.
  Commit small, atomic commits on your branch (message explains the why; each fix comes with a test or a repro script).
- `cd ~/ecosport-wt/<name> && source tools/env.sh`. Run Gradle ONLY through `tools/gb.sh <tasks>` (it queues so at most two
  builds run machine-wide and caps their memory). Read the build log file, do not stream it. `GB_ONLINE=1 tools/gb.sh ...` if you
  genuinely need a new dependency.
- Use ONLY your own emulator: `export ANDROID_SERIAL=<your serial>` in every shell, and never run adb without it. Do not start,
  stop or reset other emulators. Do not run broad `pkill` / `killall`; only stop processes you started yourself.
- Your own ELM327 emulator (a fake adapter) if you need one: `tail -f /dev/null | tools/.venv/bin/python -m elm -n <your port> -s car &`
  (ports: perf 35010, monkey 35020, feature 35030, security 35040). From the Android emulator the Mac is `10.0.2.2`. Port 35000 is the
  lead's; do not use it.
- Keep large artifacts (traces, videos, logs) in `~/ecosport-wt/<name>/artifacts/` (git-ignored by you if needed), never in git.
  Disk is tight (about 18 GB free). Do not create files > 10 MB in the repo.
- Tooling: `tools/snap.sh <screen>` + `tools/.venv/bin/python tools/cmp.py <screen>` compare a screen with the design (drive must
  stay <= 2.9 % different, home <= 3.4 %); `tools/shot.sh <name>` takes a screenshot; `tools/release.sh` builds the release APK.
  `tools/.venv` is a symlink to the lead's Python venv (Pillow, fonttools, ELM327 emulator). Vision is available via the
  `vision_analyze` tool where your toolset has it.
- OFF LIMITS: `~/.hermes/` (credentials live there; do not read it), the Hermes gateway (never restart it), any Claude / Claude Code
  CLI (never launch it), GitHub, anything outside your worktree except your emulator and `~/.gradle`.
- Restraint on fixes: keep diffs MINIMAL and LOCAL. Do NOT move, rename or reformat files you are not fixing: a restructuring pass
  follows after all branches are merged, and drive-by edits would make merging painful.

## Evidence and honesty
- Every claim needs evidence you actually produced: the command and the relevant output lines. Label anything you could not verify.
  Never invent numbers, never report a fix as working unless you ran it. If you could not measure something, say so.
- Distinguish emulator artefacts from real defects (the emulator has no real GPU/CPU of a head unit).

## Report (write it as you go so progress survives; your final message is only the summary)
File: `docs/reviews/<name>.md` in your worktree, committed on your branch. Structure:
1. Summary: verdict in 3 lines, counts by severity, top 5 issues.
2. Scope and method: what you did, what you did NOT do.
3. Findings: one block per finding with ID (`<NAME>-01`...), severity, status, evidence, root cause, impact, fix or proposal.
   Status is one of FIXED (commit hash + proof it works), OPEN (with a concrete proposal), WONTFIX (with justification).
4. Tools and tests you added (paths, how to run them again).
5. Verification log: before/after numbers or outputs.
6. Residual risk and recommendations.

Severity: CRITICAL = unsafe toward the car, remote/other-app exploitation with real impact, crash loop, data loss, unrecoverable state.
HIGH = crash or ANR in normal use, exploitable flaw, gate-breaking performance, correctness error shown as fact.
MEDIUM = degraded behaviour, resource leak, hardening gap. LOW = polish, minor risk. INFO = observation.

## Timebox
Aim to finish in roughly 60 to 90 minutes of work. Prioritise by severity. Stop and report rather than start something you cannot finish.
