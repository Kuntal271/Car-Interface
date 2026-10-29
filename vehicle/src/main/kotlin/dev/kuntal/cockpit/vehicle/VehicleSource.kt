package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface VehicleSource { fun samples(): Flow<VehicleSample> }

/** Simulator: replays [DriveCycle] in real time. Default source on the emulator. */
class SimSource(
    private val now: () -> Long = System::currentTimeMillis,
    private val periodMs: Long = 50,
) : VehicleSource {
    override fun samples(): Flow<VehicleSample> = flow {
        val t0 = now()
        while (true) { emit(DriveCycle.at(now() - t0)); delay(periodMs) }
    }
}
