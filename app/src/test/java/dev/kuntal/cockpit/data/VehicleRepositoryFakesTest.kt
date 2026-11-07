package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.testing.FakeVehicleSource
import dev.kuntal.cockpit.testing.kmCovered
import dev.kuntal.cockpit.testing.virtualClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repository against the shared [FakeVehicleSource] (the older VehicleRepositoryTest keeps its own private fake). */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleRepositoryFakesTest {

    @Test fun linkGoesLostAndRecoversWhenTheAdapterDropsOnce() = runTest {
        val src = FakeVehicleSource(virtualClock(), speedKmh = { 100.0 }, dropAfterMs = 2_000)
        launch { delay(2_500); src.stayUp = true }                        // the second session stays up
        val links = mutableListOf<Link>()
        val job = launch { VehicleRepository(src, simulated = false).states().collect { if (links.lastOrNull() != it.link) links += it.link } }
        advanceTimeBy(10_000); runCurrent(); job.cancel()
        assertEquals(listOf(Link.Connecting, Link.Connected, Link.Lost, Link.Connecting, Link.Connected), links)
        assertEquals(2, src.starts)
    }

    @Test fun aShortDropoutDoesNotLoseTheTrip() = runTest {
        val src = FakeVehicleSource(virtualClock(), speedKmh = { 100.0 }, dropAfterMs = 2_000)
        launch { delay(2_500); src.stayUp = true }
        var last: VehicleUiState? = null
        val job = launch { VehicleRepository(src, simulated = false).states().collect { last = it } }
        advanceTimeBy(10_000); runCurrent(); job.cancel()
        val driven = kmCovered(100.0, currentTime)
        val counted = last!!.trip.distanceKm
        assertTrue("counted $counted km of $driven km driven (a 1 s gap is bridged, the counter is not reset)", counted > 0.85 * driven)
    }

    @Test fun anAdapterThatIsNotThereYetIsRetriedWithBackoffUntilItAppears() = runTest {
        val src = FakeVehicleSource(virtualClock(), failFirst = 3)
        val links = mutableListOf<Link>()
        val job = launch { VehicleRepository(src, simulated = false).states().collect { if (links.lastOrNull() != it.link) links += it.link } }
        advanceTimeBy(20_000); runCurrent(); job.cancel()
        assertEquals(4, src.starts)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), src.startTimes.zipWithNext { a, b -> b - a })
        assertEquals(Link.Connected, links.last())
    }

    @Test fun cancellingTheCollectorClosesTheAdapterSession() = runTest {
        val src = FakeVehicleSource(virtualClock())
        val job = launch { VehicleRepository(src, simulated = false).states().collect { } }
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, src.active)
        job.cancel(); runCurrent()
        assertEquals("the transport must be closed in finally{}, or the adapter stays busy", 0, src.active)
    }
}
