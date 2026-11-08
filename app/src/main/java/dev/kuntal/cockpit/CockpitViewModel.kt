package dev.kuntal.cockpit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kuntal.cockpit.data.CanboxState
import dev.kuntal.cockpit.data.CockpitData
import dev.kuntal.cockpit.data.FileTripStore
import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.MemoryTripStore
import dev.kuntal.cockpit.data.MockMediaSource
import dev.kuntal.cockpit.data.Radar
import dev.kuntal.cockpit.data.TripRecorder
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.data.throttleLatest
import dev.kuntal.cockpit.perf.Perf
import dev.kuntal.cockpit.source.Sources
import dev.kuntal.cockpit.ui.SnapshotFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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

    // ---- mock-backed sources (Canbox, media, sound, trips...): rebuilt when the "Demo data" setting flips ----
    private val realTrips = if (DebugHooks.snapshot) MemoryTripStore() else FileTripStore(File(app.filesDir, "trips.tsv"))

    val data: StateFlow<CockpitData> =
        if (DebugHooks.snapshot) MutableStateFlow(SnapshotFixtures.data(realTrips))
        else settings.flow.map { it.demoData }.distinctUntilChanged().map { CockpitData(it, realTrips) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CockpitData(settings.flow.value.demoData, realTrips))

    val canbox: StateFlow<CanboxState?> =
        if (DebugHooks.snapshot) MutableStateFlow(SnapshotFixtures.canboxFor(DebugHooks.screen))
        else data.flatMapLatest { d -> flow<CanboxState?> { emit(null); d.canbox?.states()?.collect { emit(it) } } }     // null first: a stale mock never outlives "Demo data: off"
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val radar: StateFlow<Radar?> =
        if (DebugHooks.snapshot) MutableStateFlow(SnapshotFixtures.canboxFor(DebugHooks.screen).radar)
        else data.flatMapLatest { d -> flow<Radar?> { emit(null); d.radar?.readings()?.collect { emit(it) } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (!DebugHooks.snapshot) {
            // the demo player advances once a second; cancelled when demo data goes off
            viewModelScope.launch { data.collectLatestMedia() }
            // real trips only: a simulated drive is never written to the trip history
            viewModelScope.launch(Dispatchers.IO) {
                val rec = TripRecorder()
                upstream.collect { s ->
                    val smp = s.sample
                    if (s.link == Link.Connected && smp != null) rec.accept(smp)?.let { realTrips.add(it) }
                    else if (s.link != Link.Connected) rec.flush()?.let { realTrips.add(it) }
                }
            }
        }
    }

    private suspend fun StateFlow<CockpitData>.collectLatestMedia() = collectLatest { d -> (d.media as? MockMediaSource)?.run() }
}
