package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GearEstimatorTest {
    // Same ratios the simulator uses (rpm per km/h, gears 1..6). Replace with a calibrated table once real logs exist.
    private fun g(kmh: Double, gear: Int) = GearEstimator.estimate(kmh, kmh * GearEstimator.RPM_PER_KMH[gear - 1])

    @Test fun recognisesEachGear() { for (n in 1..6) assertEquals("gear $n", n, g(60.0, n)) }
    @Test fun toleratesTenPercentRatioNoise() = assertEquals(3, GearEstimator.estimate(70.0, 70.0 * 55.0 * 1.08))
    @Test fun nullWhenStopped() = assertNull(GearEstimator.estimate(0.0, 900.0))
    @Test fun nullBelowFiveKmh() = assertNull(GearEstimator.estimate(4.0, 2500.0))
    @Test fun nullWhenRpmMissingOrLow() { assertNull(GearEstimator.estimate(50.0, null)); assertNull(GearEstimator.estimate(50.0, 400.0)) }
    @Test fun nullWhenSpeedMissing() = assertNull(GearEstimator.estimate(null, 3000.0))
    @Test fun nullWhenRatioIsNowhereNearAGear() = assertNull(GearEstimator.estimate(60.0, 60.0 * 5.0))   // clutch in / coasting at idle
}
