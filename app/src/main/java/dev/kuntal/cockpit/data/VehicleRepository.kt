package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import dev.kuntal.cockpit.vehicle.VehicleSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

enum class Link { Simulated, Connecting, Connected, Lost, Off }     // Off = no data source chosen

data class VehicleUiState(
    val sample: VehicleSample? = null,
    val link: Link = Link.Connecting,
    val trip: TripState = TripState(),
    val gear: Int? = null,                 // estimated from rpm / speed; null when unknown
)

/**
 * Wraps one [VehicleSource] into UI state. A real source that fails goes Lost, waits 1 s, 2 s, 4 s, 8 s, then 10 s
 * between attempts (Connecting), and returns to Connected on the first sample. A simulated source is always Simulated.
 * The collector decides the emission rate (the UI samples at 20 Hz).
 */
class VehicleRepository(
    private val source: VehicleSource,
    private val simulated: Boolean,
    private val trip: TripTracker = TripTracker(),
) {
    fun states(): Flow<VehicleUiState> = flow {
        var state = VehicleUiState(link = if (simulated) Link.Simulated else Link.Connecting)
        emit(state)
        var attempt = 0
        while (true) {
            source.samples()
                .catch { }                               // upstream failures only: a failing collector is never swallowed
                .collect { s ->
                    attempt = 0
                    trip.accept(s)
                    state = state.copy(sample = s, link = if (simulated) Link.Simulated else Link.Connected, trip = trip.snapshot(), gear = GearEstimator.estimate(s.speedKmh, s.rpm))
                    emit(state)
                }
            state = state.copy(link = Link.Lost); emit(state)
            val waitMs = minOf(10_000L, 1_000L shl minOf(attempt, 4))
            attempt++
            delay(waitMs)
            state = state.copy(link = Link.Connecting); emit(state)
        }
    }
}
