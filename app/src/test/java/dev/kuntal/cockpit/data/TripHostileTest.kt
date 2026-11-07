package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import org.junit.Assert.*
import org.junit.Test

/** Clock and reading abuse. */
class TripHostileTest {
    private fun s(t: Long, kmh: Double) = VehicleSample(tsMs = t, speedKmh = kmh)

    // ---- trip counters ------------------------------------------------------------------------------------------------

    @Test fun aClockThatStepsBackDoesNotFreezeTheTripCounters() {
        val t = TripTracker()
        for (sec in 0..30) t.accept(s(1_000_000L + sec * 1000, 60.0))
        val before = t.snapshot().distanceKm
        for (sec in 0..30) t.accept(s(1_000_000L - 3_600_000L + sec * 1000, 60.0))          // GPS sync: one hour back
        assertTrue("distance $before -> ${t.snapshot().distanceKm}", t.snapshot().distanceKm > before + 0.4)
    }

    @Test fun negativeOrNonFiniteSpeedsAreIgnored() {
        val t = TripTracker()
        t.accept(s(0, -5.0)); t.accept(s(1000, Double.NaN)); t.accept(s(2000, Double.POSITIVE_INFINITY)); t.accept(s(3000, 30.0)); t.accept(s(4000, 30.0))
        val x = t.snapshot()
        assertTrue(x.distanceKm.isFinite() && x.distanceKm >= 0.0 && x.avgKmh.isFinite() && x.avgKmh >= 0.0)
        assertEquals(30.0, x.topKmh, 0.0)
    }
}
