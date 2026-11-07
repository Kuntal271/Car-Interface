package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripTrackerTest {
    private fun s(t: Long, kmh: Double) = VehicleSample(tsMs = t, speedKmh = kmh)

    /** Samples every [stepMs] while speed moves in a straight line from [from] to [to] over [durMs]; returns the end time. */
    private fun TripTracker.ramp(startMs: Long, durMs: Long, from: Double, to: Double, stepMs: Long = 250): Long {
        var t = 0L
        while (t <= durMs) { accept(s(startMs + t, from + (to - from) * t / durMs)); t += stepMs }
        return startMs + durMs
    }

    @Test fun integratesDistance() {
        val t = TripTracker()
        for (sec in 0..60) t.accept(s(sec * 1000L, 60.0))
        assertEquals(1.0, t.snapshot().distanceKm, 0.02)      // 60 km/h for 60 s
    }

    @Test fun tracksTopSpeed() {
        val t = TripTracker()
        listOf(10.0, 38.0, 66.0, 94.0, 124.0, 131.0, 100.0).forEachIndexed { i, v -> t.accept(s(i * 1000L, v)) }      // at most 30 km/h per second
        assertEquals(131.0, t.snapshot().topKmh, 0.0)
    }

    @Test fun averageIsDistanceOverElapsedTime() {
        val t = TripTracker()
        for (sec in 0..600) t.accept(s(sec * 1000L, 40.0))
        assertEquals(40.0, t.snapshot().avgKmh, 0.5)
    }

    @Test fun zeroToHundredIsTimedFromStandstill() {
        val t = TripTracker()
        t.accept(s(0, 0.0))
        t.ramp(250, 12_300, 2.0, 101.0)                          // 0 -> 101 over about 12.5 s, sampled at 4 Hz
        assertEquals(12.3, t.snapshot().zeroTo100Best!!, 0.7)   // crossing is interpolated between samples
    }

    @Test fun zeroToHundredKeepsTheBestRun() {
        val t = TripTracker()
        fun run(startMs: Long, dur: Long) {
            t.accept(s(startMs, 0.0))
            val end = t.ramp(startMs + 250, dur, 1.0, 105.0)
            t.ramp(end + 250, 4_000, 105.0, 0.0)                 // brake to a stop, which arms the next run
        }
        run(0, 14_000); run(30_000, 11_000); run(70_000, 13_000)
        assertEquals(11.0, t.snapshot().zeroTo100Best!!, 0.7)
    }

    @Test fun rollingStartIsNotATimedRun() {
        val t = TripTracker()
        t.accept(s(0, 40.0)); t.accept(s(4_000, 105.0))
        assertNull(t.snapshot().zeroTo100Best)
    }

    @Test fun ignoresSamplesWithoutSpeed() {
        val t = TripTracker()
        t.accept(VehicleSample(tsMs = 0)); t.accept(VehicleSample(tsMs = 1000))
        assertEquals(0.0, t.snapshot().distanceKm, 0.0)
    }

    @Test fun resetClearsTheTrip() {
        val t = TripTracker()
        for (sec in 0..10) t.accept(s(sec * 1000L, 100.0))
        t.reset()
        assertEquals(0.0, t.snapshot().distanceKm, 0.0); assertEquals(0.0, t.snapshot().topKmh, 0.0)
    }

    @Test fun tracksPeakBoost() {
        val t = TripTracker()
        listOf(-60.0, 40.0, 104.0, 20.0).forEachIndexed { i, b -> t.accept(VehicleSample(tsMs = i * 1000L, speedKmh = 50.0, mapKpa = 100.0 + b, baroKpa = 100.0)) }
        assertEquals(104.0, t.snapshot().peakBoostKpa!!, 1e-9)
    }

    @Test fun peakBoostIsNullWithoutMapAndBaro() {
        val t = TripTracker(); t.accept(s(0, 50.0))
        assertNull(t.snapshot().peakBoostKpa)
    }

    // ---- plausibility: real adapters glitch, and a glitch must not become a record that lasts the whole trip ----
    @Test fun aSpeedSpikeNoCarCouldDoIsIgnored() {
        val t = TripTracker()
        t.accept(s(0, 50.0)); t.accept(s(100, 250.0)); t.accept(s(200, 52.0))
        assertEquals(52.0, t.snapshot().topKmh, 0.0)
    }

    @Test fun anImpossibleJumpFromStandstillIsNotAZeroToHundred() {
        val t = TripTracker()
        t.accept(s(0, 0.0)); t.accept(s(200, 105.0)); t.accept(s(400, 106.0))
        assertNull(t.snapshot().zeroTo100Best)
    }

    @Test fun zeroToHundredUnderThreeSecondsIsRejected() {
        val t = TripTracker()                                   // 50 km/h per second is allowed, but 2 s for 0-100 is not a real car
        for (i in 0..8) t.accept(s(i * 250L, i * 12.5))
        assertNull(t.snapshot().zeroTo100Best)
    }

    @Test fun aRealisticRunStillCounts() {
        val t = TripTracker()
        for (i in 0..12) t.accept(s(i * 1000L, minOf(i * 9.0, 105.0)))
        assertEquals(11.1, t.snapshot().zeroTo100Best!!, 0.2)
    }

    @Test fun aLongGapInTheDataAddsNoDistanceAndCancelsARun() {
        val t = TripTracker()
        t.accept(s(0, 0.0)); t.accept(s(2000, 40.0))            // run armed and under way
        t.accept(s(62_000, 60.0))                               // adapter was gone for a minute
        assertEquals(0.0111, t.snapshot().distanceKm, 0.002)    // only the first 2 s counted
        t.accept(s(63_000, 105.0))
        assertNull(t.snapshot().zeroTo100Best)
    }
}
