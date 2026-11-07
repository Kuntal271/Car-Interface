package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import dev.kuntal.cockpit.vehicle.VehicleSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryHostileTest {
    /** One good sample, then the adapter dies. */
    private class DiesAfterOne : VehicleSource {
        override fun samples(): Flow<VehicleSample> = flow {
            emit(VehicleSample(tsMs = 0, speedKmh = 120.0, rpm = 4000.0))
            throw java.io.IOException("adapter gone")
        }
    }

    @Test fun aLostLinkDoesNotKeepShowingTheLastSpeedAsIfItWereLive() = runTest {
        val seen = mutableListOf<VehicleUiState>()
        val job = launch { VehicleRepository(DiesAfterOne(), simulated = false).states().collect { seen += it } }
        advanceTimeBy(500); runCurrent(); job.cancel()
        assertNotNull(seen.first { it.link == Link.Connected }.sample)
        val lost = seen.first { it.link == Link.Lost }
        assertNull("frozen ${lost.sample}", lost.sample); assertNull(lost.gear)
        assertEquals(120.0, lost.trip.topKmh, 0.0)           // the trip is history, not a live reading: it stays
    }
}
