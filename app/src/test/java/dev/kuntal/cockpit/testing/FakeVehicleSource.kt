package dev.kuntal.cockpit.testing

import dev.kuntal.cockpit.vehicle.VehicleSample
import dev.kuntal.cockpit.vehicle.VehicleSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * A scriptable [VehicleSource] for tests of everything above the adapter (repository, pipeline, future hub).
 *
 *  - emits a sample every [periodMs] with the speed/rpm given by [speedKmh] / [rpm] as a function of time since it was started
 *  - the first [failFirst] starts throw [IOException] immediately (an adapter that is not there yet)
 *  - [dropAfterMs] > 0 makes every start throw after that long (an adapter that drops out), until [stayUp] is set
 *  - [connectDelayMs] models the ELM327 handshake (ATZ, ATSP6, PID discovery take seconds on a real adapter) before the first sample
 *  - counts how often it was started and how many collectors are alive right now ([active]), so a test can prove that polling
 *    really stopped when nobody listens (the adapter is closed in `finally`, exactly like `ElmClient.samples()`)
 *
 * [now] stamps the samples; pass `virtualClock()` inside `runTest` so sample time and `delay` agree.
 */
class FakeVehicleSource(
    private val now: () -> Long,
    private val periodMs: Long = 50,
    private val speedKmh: (sinceStartMs: Long) -> Double? = { 50.0 },
    private val rpm: (sinceStartMs: Long) -> Double? = { 2000.0 },
    private val failFirst: Int = 0,
    private val dropAfterMs: Long = 0,
    private val connectDelayMs: Long = 0,
) : VehicleSource {
    var starts = 0
        private set
    var active = 0
        private set
    var emitted = 0
        private set
    val startTimes = mutableListOf<Long>()
    var stayUp = false

    override fun samples(): Flow<VehicleSample> = flow {
        starts++
        startTimes += now()
        if (starts <= failFirst) throw IOException("adapter not there (start $starts)")
        active++
        try {
            if (connectDelayMs > 0) delay(connectDelayMs)
            val t0 = now()
            while (true) {
                val since = now() - t0
                if (dropAfterMs > 0 && !stayUp && since >= dropAfterMs) throw IOException("adapter dropped after $since ms")
                emit(VehicleSample(tsMs = now(), speedKmh = speedKmh(since), rpm = rpm(since)))
                emitted++
                delay(periodMs)
            }
        } finally {
            active--
        }
    }
}
