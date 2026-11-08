package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimSourceTest {
    @Test fun replaysTheDriveCycleInRealTime() = runTest {
        val src = SimSource(now = { testScheduler.currentTime }, periodMs = 50)
        val seen = mutableListOf<VehicleSample>()
        val job = launch { src.samples().collect { seen += it } }
        advanceTimeBy(45_100); runCurrent(); job.cancel()
        assertEquals("starts standing still", 0.0, seen.first().speedKmh!!, 0.001)
        assertTrue("reaches cruising speed at the end of the ramp: ${seen.last().speedKmh}", seen.last().speedKmh!! > 125.0)
        assertTrue("timestamps strictly increase", seen.zipWithNext().all { (a, b) -> b.tsMs > a.tsMs })
        assertEquals("one sample per period", 50L, seen[1].tsMs - seen[0].tsMs)
    }

    @Test fun theSimulatorNeverReportsFieldsThePidSetCannotProvide() = runTest {
        val s = DriveCycle.at(10_000)
        assertTrue(s.boostKpa != null && s.rpm!! >= 850.0 && s.speedKmh!! in 0.0..130.0)
    }
}
