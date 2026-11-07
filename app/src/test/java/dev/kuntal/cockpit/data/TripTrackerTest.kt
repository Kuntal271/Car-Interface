package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripTrackerTest {
    private fun s(t: Long, kmh: Double) = VehicleSample(tsMs = t, speedKmh = kmh)

    @Test fun integratesDistance() {
        val t = TripTracker()
        for (sec in 0..60) t.accept(s(sec * 1000L, 60.0))
        assertEquals(1.0, t.snapshot().distanceKm, 0.02)      // 60 km/h for 60 s
    }

    @Test fun tracksTopSpeed() {
        val t = TripTracker()
        listOf(10.0, 80.0, 131.0, 40.0).forEachIndexed { i, v -> t.accept(s(i * 1000L, v)) }
        assertEquals(131.0, t.snapshot().topKmh, 0.0)
    }

    @Test fun averageIsDistanceOverElapsedTime() {
        val t = TripTracker()
        for (sec in 0..3600 step 10) t.accept(s(sec * 1000L, 40.0))
        assertEquals(40.0, t.snapshot().avgKmh, 0.5)
    }

    @Test fun zeroToHundredIsTimedFromStandstill() {
        val t = TripTracker()
        t.accept(s(0, 0.0))
        t.accept(s(1_000, 20.0)); t.accept(s(5_000, 60.0)); t.accept(s(11_000, 99.0)); t.accept(s(12_300, 101.0))
        assertEquals(12.3, t.snapshot().zeroTo100Best!!, 0.7)   // crossing is interpolated between samples
    }

    @Test fun zeroToHundredKeepsTheBestRun() {
        val t = TripTracker()
        fun run(startMs: Long, dur: Long) {
            t.accept(s(startMs, 0.0)); t.accept(s(startMs + dur / 2, 50.0)); t.accept(s(startMs + dur, 105.0))
            t.accept(s(startMs + dur + 5_000, 0.0))
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
}
