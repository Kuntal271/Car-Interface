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

/** Whatever arrives (out of order, duplicated, NaN, negative, absurd, huge gaps), the counters stay finite, non-negative and physically bounded. */
class TripFuzzTest {
    @org.junit.Test fun countersNeverGoNegativeNanOrHuge() {
        val rnd = java.util.Random(42)
        repeat(300) { round ->
            val t = TripTracker()
            var ts = rnd.nextInt(1_000_000).toLong()
            var maxTs = ts; var minTs = ts
            repeat(400) {
                ts += when (rnd.nextInt(6)) { 0 -> -rnd.nextInt(5_000_000).toLong(); 1 -> 0L; 2 -> rnd.nextInt(60_000).toLong(); else -> 50L + rnd.nextInt(300) }
                maxTs = maxOf(maxTs, ts); minTs = minOf(minTs, ts)
                val v = when (rnd.nextInt(10)) { 0 -> Double.NaN; 1 -> -rnd.nextDouble() * 500; 2 -> rnd.nextDouble() * 1e9; 3 -> Double.POSITIVE_INFINITY; else -> rnd.nextDouble() * 200 }
                t.accept(dev.kuntal.cockpit.vehicle.VehicleSample(tsMs = ts, speedKmh = v, mapKpa = rnd.nextDouble() * 255, baroKpa = if (rnd.nextBoolean()) 100.0 else null))
                val s = t.snapshot()
                org.junit.Assert.assertTrue("round $round: $s", s.distanceKm.isFinite() && s.distanceKm >= 0 && s.avgKmh.isFinite() && s.avgKmh >= 0 && s.topKmh.isFinite() && s.topKmh in 0.0..400.0)
                org.junit.Assert.assertTrue("round $round avg ${s.avgKmh} top ${s.topKmh}", s.avgKmh <= s.topKmh + 1e-6)
                s.zeroTo100Best?.let { z -> org.junit.Assert.assertTrue("0-100 of $z s", z >= 3.0 && z.isFinite()) }
            }
        }
    }
}
