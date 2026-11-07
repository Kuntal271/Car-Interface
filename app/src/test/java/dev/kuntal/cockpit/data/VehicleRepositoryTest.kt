package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import dev.kuntal.cockpit.vehicle.VehicleSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleRepositoryTest {
    private class FlakySource(private val failures: Int) : VehicleSource {
        var attempts = 0
        val attemptTimes = mutableListOf<Long>()
        var clock: () -> Long = { 0L }
        override fun samples(): Flow<VehicleSample> = flow {
            attempts++; attemptTimes += clock()
            if (attempts <= failures) throw java.io.IOException("adapter gone")
            var t = 0L
            while (true) { emit(VehicleSample(tsMs = t, speedKmh = 50.0, rpm = 2000.0)); t += 50; delay(50) }
        }
    }

    @Test fun simulatedSourceReportsSimulated() = runTest {
        val repo = VehicleRepository(FlakySource(0), simulated = true)
        val seen = mutableListOf<VehicleUiState>()
        val job = launch { repo.states().collect { seen += it } }
        advanceTimeBy(200); runCurrent(); job.cancel()
        assertTrue(seen.isNotEmpty() && seen.all { it.link == Link.Simulated })
        assertEquals(50.0, seen.last().sample!!.speedKmh!!, 0.0)
    }

    @Test fun realSourceStartsConnectingThenConnected() = runTest {
        val repo = VehicleRepository(FlakySource(0), simulated = false)
        val links = mutableListOf<Link>()
        val job = launch { repo.states().collect { links += it.link } }
        advanceTimeBy(200); runCurrent(); job.cancel()
        assertEquals(Link.Connecting, links.first())
        assertEquals(Link.Connected, links.last())
    }

    @Test fun failureGoesLostThenRecovers() = runTest {
        val src = FlakySource(1).also { it.clock = { currentTime } }
        val repo = VehicleRepository(src, simulated = false)
        val links = mutableListOf<Link>()
        val job = launch { repo.states().collect { if (links.lastOrNull() != it.link) links += it.link } }
        advanceTimeBy(3_000); runCurrent(); job.cancel()
        assertEquals(listOf(Link.Connecting, Link.Lost, Link.Connecting, Link.Connected), links)
    }

    @Test fun reconnectBacksOffExponentiallyAndCapsAtTenSeconds() = runTest {
        val src = FlakySource(failures = 99).also { it.clock = { currentTime } }
        val repo = VehicleRepository(src, simulated = false)
        val job = launch { repo.states().collect { } }
        advanceTimeBy(60_000); runCurrent(); job.cancel()
        val gaps = src.attemptTimes.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L), gaps.take(6))
    }

    @Test fun tripIsCarriedInTheState() = runTest {
        val repo = VehicleRepository(FlakySource(0), simulated = true)
        var last: VehicleUiState? = null
        val job = launch { repo.states().collect { last = it } }
        advanceTimeBy(5_000); runCurrent(); job.cancel()
        assertTrue(last!!.trip.distanceKm > 0.0)
        assertEquals(50.0, last!!.trip.topKmh, 0.0)
    }
}
