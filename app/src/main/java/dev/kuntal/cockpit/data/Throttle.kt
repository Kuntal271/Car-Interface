package dev.kuntal.cockpit.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * At most one value per [periodMs], always the newest one, and NOTHING scheduled while the source is quiet.
 *
 * Replaces `Flow.sample(periodMs)` for the telemetry rate limit. `sample` runs a ticker coroutine that wakes every period for as long
 * as it is collected, whether or not a value arrived: at 20 Hz that was 20 main-thread wake-ups per second on an idle head unit
 * (no data source, engine off, the user reading the screen) for nothing. Here a timer exists only for the [periodMs] after an emission.
 * The first value after a quiet spell is passed on at once (sample made it wait for the next tick, 25 ms on average).
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> {
    require(periodMs > 0) { "period must be positive" }
    return conflate().transform { v -> emit(v); delay(periodMs) }
}
