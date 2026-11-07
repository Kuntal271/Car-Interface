package dev.kuntal.cockpit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.data.throttleLatest
import dev.kuntal.cockpit.perf.Perf
import dev.kuntal.cockpit.source.Sources
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/** Holds the vehicle state for all screens. The source follows the owner's choice live; the UI sees at most 20 updates per second. */
@OptIn(ExperimentalCoroutinesApi::class)
class CockpitViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore.get(app)

    // One upstream per source choice: it restarts only when the connection itself changes (theme and units never reconnect).
    private val upstream = settings.flow
        .distinctUntilChanged { a, b -> a.sourceKey == b.sourceKey }
        .flatMapLatest { Sources.states(it) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Rate-limited downstream (newest value, at most one per period, no idle ticker), so the lighter performance mode changes the rate without restarting the source.
    val state: StateFlow<VehicleUiState> =
        if (DebugHooks.snapshot) MutableStateFlow(DebugHooks.driveState)
        else Perf.mode.flatMapLatest { m -> upstream.throttleLatest(m.telemetryPeriodMs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())
}
