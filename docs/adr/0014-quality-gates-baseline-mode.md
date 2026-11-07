# ADR-0014: Quality gates: detekt (+ Compose rules), Android lint and JaCoCo, all in baseline mode

Status: Accepted (implemented)

## Context
There was no static analysis, `lintDebug`/`lintRelease` failed on the current commit (2 errors), CI built only the debug APK and ran the unit tests, and coverage was unknown.
Other work is in flight on separate branches, so reformatting or fixing existing files was not an option.

## Decision
- detekt 1.23.7 with `buildUponDefaultConfig`, type resolution (`detektDebug`, `detektMain`), the Compose rules plugin, one baseline per module (`config/detekt/`).
  Chosen over ktlint because it analyses (complexity, coroutines, Compose stability and modifiers) and has a first-class baseline; ktlint would flag formatting on almost every
  line of the current dense style and the baseline would be noise. A formatter (Spotless/ktlint) is a later, single mechanical commit when no branches are open.
- Android lint with `abortOnError`, baseline `config/lint/lint-baseline-app.xml`, HTML/XML/SARIF reports.
- JaCoCo for `:vehicle` (Gradle plugin) and `:app` (AGP unit-test coverage), opt-in with `-Pcoverage`, summarised by `tools/coverage-summary.py`.
- CI runs all gates, the release (R8) build, and uploads the reports. `tools/rebaseline.sh` regenerates baselines after a merge.

## Consequences
+ Green today, red for any new finding; the baseline can only shrink as the refactor proceeds (review `git diff config/` on every change).
- New code from parallel branches needs one rebaseline after the merge. Type-resolved detekt compiles the module, so the CI job is a few minutes longer.
