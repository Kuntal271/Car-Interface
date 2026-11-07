package dev.kuntal.cockpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kuntal.cockpit.data.VehicleRepository
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.perf.Perf
import dev.kuntal.cockpit.vehicle.SimSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn

/** Holds the vehicle state for all screens. UI sees at most 20 updates per second; the source may be faster. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CockpitViewModel : ViewModel() {
    // Source is the simulator until the ELM327 transport and the real unit are wired (M4/M5).
    private val repo = VehicleRepository(SimSource(), simulated = true)

    // One upstream, sampled downstream: switching to the lighter mode changes the rate without restarting the source.
    private val upstream = repo.states().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val state: StateFlow<VehicleUiState> =
        if (DebugHooks.snapshot) MutableStateFlow(DebugHooks.driveState)
        else Perf.mode.flatMapLatest { m -> upstream.sample(m.telemetryPeriodMs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())
}
