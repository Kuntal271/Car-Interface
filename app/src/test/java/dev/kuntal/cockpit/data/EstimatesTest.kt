package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatesTest {
    @Test fun fuelFlowIsPlausibleAtCruise() {
        // 3000 rpm, 50 kPa, 30 C intake: a 1.0 L engine on a light load burns a few litres per hour
        val lph = FuelEstimate.litresPerHour(3000.0, 50.0, 30.0)!!
        assertTrue("got $lph", lph in 2.0..6.0)
    }
    @Test fun fuelFlowNeedsAllReadingsAndARunningEngine() {
        assertNull(FuelEstimate.litresPerHour(null, 50.0, 30.0))
        assertNull(FuelEstimate.litresPerHour(3000.0, null, 30.0))
        assertNull(FuelEstimate.litresPerHour(3000.0, 50.0, null))
        assertNull(FuelEstimate.litresPerHour(120.0, 50.0, 30.0))
    }
    @Test fun moreAirMeansMoreFuel() {
        assertTrue(FuelEstimate.litresPerHour(4000.0, 150.0, 40.0)!! > FuelEstimate.litresPerHour(4000.0, 60.0, 40.0)!!)
    }
    @Test fun meterAveragesOverDistanceAndWaitsForHalfAKilometre() {
        val m = FuelEstimate.Meter()
        // 60 km/h at 4.2 L/h for 1 minute = 1 km, 0.07 L -> 7.0 L/100 km
        for (i in 0..60) m.add(i * 1000L, 60.0, 4.2)
        assertEquals(1.0, m.distanceKm, 0.02)
        assertEquals(7.0, m.avgLPer100()!!, 0.2)
        val fresh = FuelEstimate.Meter(); for (i in 0..10) fresh.add(i * 1000L, 60.0, 4.2)
        assertNull(fresh.avgLPer100())
    }
    @Test fun meterIgnoresHugeGapsAndMissingSpeed() {
        val m = FuelEstimate.Meter()
        m.add(0, 60.0, 4.2); m.add(3_600_000, 60.0, 4.2)         // an hour of silence counts as at most 5 s
        assertTrue(m.distanceKm < 0.1)
        m.add(3_601_000, null, 4.2)
        assertTrue(m.distanceKm < 0.1)
    }
    @Test fun rangeFromLevelAndAverage() {
        assertEquals(FuelEstimate.TANK_L * 0.5 / 6.0 * 100.0, FuelEstimate.rangeKm(50.0, 6.0)!!, 0.01)
        assertNull(FuelEstimate.rangeKm(null, 6.0)); assertNull(FuelEstimate.rangeKm(50.0, null)); assertNull(FuelEstimate.rangeKm(50.0, 0.0))
    }
    @Test fun smoothDrivingScoresHighAndStopStartLow() {
        val smooth = Smoothness(); for (i in 0..40) smooth.add(40.0 + i * 0.5)            // gentle steady acceleration
        val rough = Smoothness(); for (i in 0..40) rough.add(if (i % 2 == 0) 10.0 else 40.0) // lurching
        assertTrue(smooth.score()!! >= 90)
        assertTrue(rough.score()!! <= 40)
    }
    @Test fun smoothnessNeedsEnoughSamples() {
        val s = Smoothness(); repeat(5) { s.add(30.0) }
        assertNull(s.score())
        s.add(null)
        assertNull(s.score())
    }

    @Test fun zeroToHundredFromStandstill() {
        val z = ZeroTo100()
        assertEquals(ZeroTo100.Phase.Waiting, z.phase)
        assertNull(z.accept(0, 0.0)); assertEquals(ZeroTo100.Phase.Armed, z.phase)
        assertNull(z.accept(1_000, 0.0))
        assertNull(z.accept(2_000, 8.0)); assertEquals(ZeroTo100.Phase.Running, z.phase)
        assertNull(z.accept(6_000, 60.0))
        val t = z.accept(10_000, 110.0)!!                      // crosses 100 four fifths of the way through the last step
        assertEquals(8.2, t, 0.05)                             // from the last standstill sample at 1.0 s
        assertEquals(ZeroTo100.Phase.Waiting, z.phase)
    }
    @Test fun rollingStartIsNotTimedAndAbortedRunRearms() {
        val z = ZeroTo100()
        z.accept(0, 30.0)                                       // already moving: waiting for a stop
        assertEquals(ZeroTo100.Phase.Waiting, z.phase)
        assertNull(z.accept(5_000, 120.0)); assertEquals(ZeroTo100.Phase.Waiting, z.phase)
        z.accept(6_000, 0.0); z.accept(7_000, 20.0); assertEquals(ZeroTo100.Phase.Running, z.phase)
        z.accept(8_000, 0.0); assertEquals(ZeroTo100.Phase.Armed, z.phase)
        assertNull(z.elapsedSec(9_000))
    }
    @Test fun elapsedCountsWhileRunning() {
        val z = ZeroTo100(startArmed = true)
        z.accept(1_000, 0.0); z.accept(2_000, 10.0)
        assertNotNull(z.elapsedSec(5_000)); assertEquals(4.0, z.elapsedSec(5_000)!!, 0.01)
    }
    @Test fun historyOfBuildsAFullBuffer() {
        val h = History.of(doubleArrayOf(0.0, 0.5, 1.0), doubleArrayOf(900.0, 3000.0, 5000.0))
        assertEquals(3, h.size); assertEquals(0.0, h.boost(0), 0.0); assertEquals(5000.0, h.rpm(2), 0.0)
    }
}
