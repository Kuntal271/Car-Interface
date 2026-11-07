package dev.kuntal.cockpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kuntal.cockpit.data.VehicleRepository
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.vehicle.SimSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn

/** Holds the vehicle state for all screens. UI sees at most 20 updates per second; the source may be faster. */
@OptIn(FlowPreview::class)
class CockpitViewModel : ViewModel() {
    // Source is the simulator until the ELM327 transport and the real unit are wired (M4/M5).
    private val repo = VehicleRepository(SimSource(), simulated = true)

    val state: StateFlow<VehicleUiState> =
        if (DebugHooks.snapshot) MutableStateFlow(DebugHooks.driveState)
        else repo.states().sample(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())
}
