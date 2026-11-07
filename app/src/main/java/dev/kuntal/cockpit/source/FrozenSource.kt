package dev.kuntal.cockpit.source

import dev.kuntal.cockpit.vehicle.VehicleSample
import dev.kuntal.cockpit.vehicle.VehicleSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Dev/benchmark source (`--ez freeze true`): the same values forever, with a fresh timestamp every [periodMs], like a real adapter
 * answering at 20 Hz while nothing about the car changes (ignition on, engine off, or a steady cruise). Shows that steady data costs no frames.
 */
class FrozenSource(private val sample: VehicleSample, private val periodMs: Long = 50, private val now: () -> Long = System::currentTimeMillis) : VehicleSource {
    override fun samples(): Flow<VehicleSample> = flow { while (true) { emit(sample.copy(tsMs = now())); delay(periodMs) } }
}
