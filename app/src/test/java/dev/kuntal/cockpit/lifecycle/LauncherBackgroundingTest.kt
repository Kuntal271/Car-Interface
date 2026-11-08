package dev.kuntal.cockpit.lifecycle

import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.Settings
import dev.kuntal.cockpit.data.VehicleRepository
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.perf.PerfMode
import dev.kuntal.cockpit.testing.FakeVehicleSource
import dev.kuntal.cockpit.testing.kmCovered
import dev.kuntal.cockpit.testing.virtualClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHARACTERISATION of finding ARCH-01 (docs/reviews/architecture.md): what the vehicle pipeline does when the launcher
 * activity is stopped because the owner opened Maps, Radio or any other app.
 *
 * [pipeline] is a line-for-line copy of the operator chain in `CockpitViewModel` (the ViewModel itself needs an Application and cannot
 * run on the JVM): settings -> distinctUntilChanged(sourceKey) -> flatMapLatest { new VehicleRepository } -> shareIn(WhileSubscribed(5 s))
 * -> sample(20 Hz) -> stateIn(WhileSubscribed(5 s)). The "screen" is a collector, because `collectAsStateWithLifecycle` cancels its
 * collection in onStop and restarts it in onStart.
 *
 * These tests PASS today and document a defect. When the process-scoped VehicleHub from docs/REFACTOR-PLAN.md (step 6) replaces this
 * chain, they must be INVERTED (polling continues, trips continue, trips survive), not deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LauncherBackgroundingTest {

    private fun pipeline(scope: CoroutineScope, source: FakeVehicleSource): StateFlow<VehicleUiState> {
        val settings = MutableStateFlow(Settings())
        val perfMode = MutableStateFlow(PerfMode.Full)
        val upstream = settings
            .distinctUntilChanged { a, b -> a.sourceKey == b.sourceKey }
            .flatMapLatest { VehicleRepository(source, simulated = false).states() }       // Sources.states(): a NEW repository (new TripTracker) per start
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        return perfMode
            .flatMapLatest { m -> upstream.sample(m.telemetryPeriodMs) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())
    }

    private suspend fun TestScope.passTime(ms: Long) { advanceTimeBy(ms); runCurrent() }

    @Test fun pollingStopsAboutTenSecondsAfterTheLauncherIsStopped() = runTest {
        val src = FakeVehicleSource(virtualClock(), speedKmh = { 100.0 })
        val state = pipeline(backgroundScope, src)

        val screen = launch { state.collect { } }                       // onStart
        passTime(10_000)
        assertEquals("one adapter session while the launcher is visible", 1, src.active)

        screen.cancel()                                                 // onStop: the owner opened Maps
        passTime(9_000)
        assertEquals("inside the 5 s + 5 s grace period the adapter is still polled", 1, src.active)
        passTime(1_500)
        assertEquals("after the grace period the adapter is closed and nothing reads the car", 0, src.active)
    }

    @Test fun tripCountersStartFromZeroWhenTheLauncherComesBackAfterTheGracePeriod() = runTest {
        val src = FakeVehicleSource(virtualClock(), speedKmh = { 100.0 })      // the car cruises at 100 km/h the whole time
        val state = pipeline(backgroundScope, src)

        val screen = launch { state.collect { } }
        passTime(10_000)
        val before = state.value.trip.distanceKm
        assertTrue("trip counted the first 10 s: $before km", before > 0.9 * kmCovered(100.0, 10_000))

        screen.cancel()
        passTime(30_000)                                                // 30 s in Maps: about 0.83 km driven, none of it counted

        val back = launch { state.collect { } }
        passTime(10_000)
        back.cancel()

        val driven = kmCovered(100.0, currentTime)
        val counted = state.value.trip.distanceKm
        assertEquals("a new session was started", 2, src.starts)
        assertTrue("counted $counted km but the car drove $driven km: the Trip A counter restarted from zero", counted < 0.5 * driven)
        assertTrue("and it is back to the first 10 s worth: $counted km", counted < 1.5 * kmCovered(100.0, 10_000))
    }

    @Test fun comingBackInsideTheGracePeriodKeepsTheSameSessionAndTheTrip() = runTest {
        val src = FakeVehicleSource(virtualClock(), speedKmh = { 100.0 })
        val state = pipeline(backgroundScope, src)

        val screen = launch { state.collect { } }
        passTime(10_000)
        val before = state.value.trip.distanceKm
        screen.cancel()
        passTime(4_000)                                                 // a quick look at another app and back

        val back = launch { state.collect { } }
        passTime(2_000)
        back.cancel()

        assertEquals("no reconnect", 1, src.starts)
        assertTrue("trip kept counting: ${state.value.trip.distanceKm} > $before", state.value.trip.distanceKm > before)
    }

    @Test fun afterTheGracePeriodTheScreenFirstShowsTheStaleLastValueThenConnecting() = runTest {
        val src = FakeVehicleSource(virtualClock(), speedKmh = { 100.0 }, connectDelayMs = 1_500)      // a real handshake takes seconds
        val state = pipeline(backgroundScope, src)

        val first = launch { state.collect { } }
        passTime(5_000)
        first.cancel()
        passTime(60_000)
        val staleSampleAt = state.value.sample?.tsMs

        val seen = mutableListOf<VehicleUiState>()
        val back = launch { state.collect { seen += it } }
        runCurrent()
        val firstFrame = seen.first()
        passTime(5_000)
        back.cancel()

        // StateFlow keeps its value while nobody listens: the first frame back is a 60 s old reading labelled Connected ...
        assertEquals(Link.Connected, firstFrame.link)
        assertEquals(staleSampleAt, firstFrame.sample?.tsMs)
        assertTrue("stale by ${currentTime - 5_000 - firstFrame.sample!!.tsMs} ms", firstFrame.sample!!.tsMs < currentTime - 60_000)
        // ... then the dashboard blanks and reconnects in front of the owner.
        val links = seen.map { it.link }.fold(emptyList<Link>()) { acc, l -> if (acc.lastOrNull() == l) acc else acc + l }
        assertEquals(listOf(Link.Connected, Link.Connecting, Link.Connected), links)
    }
}
