package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** Head units set their clock from GPS / the network shortly after boot; wall time can jump backwards under a running app. */
class ClockJumpTest {
    @Test fun theSimulatorNeverShowsImpossibleValuesWhenTheClockGoesBackwards() = runTest {
        var clock = 1_800_000_000_000L
        val src = SimSource(now = { clock }, periodMs = 1)
        val seen = mutableListOf<VehicleSample>()
        // first sample at "now", then the clock jumps back one hour (NTP/GPS fix), then three more samples
        src.samples().take(4).collect { seen += it; if (seen.size == 1) clock -= 3_600_000L else clock += 50 }
        for (s in seen) {
            assertTrue("coolant ${s.coolantC}", s.coolantC!! in 20.0..130.0)
            assertTrue("fuel ${s.fuelPct}", s.fuelPct!! in 0.0..100.0)
            assertTrue("speed ${s.speedKmh}", s.speedKmh!! >= 0.0)
        }
    }

    @Test fun driveCycleToleratesNegativeTime() {
        val s = DriveCycle.at(-3_600_000L)
        assertTrue("coolant ${s.coolantC}", s.coolantC!! in 20.0..130.0)
        assertTrue("fuel ${s.fuelPct}", s.fuelPct!! in 0.0..100.0)
    }
}
