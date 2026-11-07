# ADR-0009: Keep SharedPreferences behind a SettingsRepository; add a schema version; not DataStore yet

Status: Proposed

## Context
Settings are a 16-key map with clamping (`Settings.toMap/fromMap/sanitized`, fully unit-tested). There is no stored version, `SettingsStore.update` is a
non-atomic read-modify-write on a `StateFlow`, recents live in a second prefs file, and the first read is synchronous on the main thread.

## Decision
Keep SharedPreferences, but: hide it behind `SettingsRepository` (interface + `SharedPreferencesSettingsRepository` + an in-memory fake); store
`schemaVersion` and run an ordered, pure `migrate(map)`; use `MutableStateFlow.update {}`; keep one prefs file. Do not adopt Preferences DataStore until a
second structured store exists.

## Consequences
+ The first composition can use the stored theme synchronously (no default-theme flash, which an async DataStore read would cause).
+ Migrations are pure functions tested with old maps.
- SharedPreferences is not Google's current recommendation; its failure modes (ANR on `apply()` waits in old Android, no transactions across keys) do not matter at 16 keys.
- Trip checkpoints and any future trip history are NOT settings: a single JSON/CSV file written atomically (temp file + rename). Room only if the owner wants queryable history.
