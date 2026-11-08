package dev.kuntal.cockpit.data

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanboxTest {
    @Test fun mockIsAPureFunctionOfTimeAndSeed() {
        val a = MockCanbox(seed = 7); val b = MockCanbox(seed = 7)
        for (t in 0L..180_000L step 1_500L) assertEquals("t=$t", a.at(t), b.at(t))
        assertEquals(a.at(5_000), a.at(5_000 + MockCanbox.LOOP_MS))          // the scenario loops
    }

    @Test fun seedOnlyChangesTyresAndOdometer() {
        val a = MockCanbox(1).at(10_000); val b = MockCanbox(5).at(10_000)
        assertNotEquals(a.tyresBar, b.tyresBar); assertNotEquals(a.odometerKm, b.odometerKm)
        assertEquals(a.doorsOpen, b.doorsOpen); assertEquals(a.climate, b.climate); assertEquals(a.radar, b.radar)
    }

    @Test fun rearLeftDoorOpensAndCloses() {
        val m = MockCanbox()
        assertTrue(m.at(5_000).doorsOpen.isEmpty())
        assertEquals(setOf(Door.RearLeft), m.at(8_000).doorsOpen)
        assertEquals("Rear left door open", m.at(8_000).doorSummary)
        assertTrue(m.at(14_000).doorsOpen.isEmpty()); assertNull(m.at(14_000).doorSummary)
    }

    @Test fun climateChangesInTheScript() {
        val m = MockCanbox()
        assertEquals(22.0, m.at(10_000).climate!!.setC!!, 0.0); assertEquals(21.5, m.at(22_000).climate!!.setC!!, 0.0)
        assertEquals(4, m.at(28_000).climate!!.fan); assertEquals(3, m.at(40_000).climate!!.fan)
    }

    @Test fun reverseSweepApproachesThenClears() {
        val m = MockCanbox()
        assertFalse(m.at(30_000).reverse)
        val peak = m.at(46_000)
        assertTrue(peak.reverse); assertEquals(3, peak.radar!!.rearMax); assertEquals(0.6, peak.radar!!.nearestRearM!!, 0.0)
        assertFalse(m.at(53_000).reverse); assertEquals(0, m.at(53_000).radar!!.rearMax); assertNull(m.at(53_000).radar!!.nearestRearM)
    }

    @Test fun doorSummaryCountsSeveral() {
        assertEquals("2 doors open", CanboxState(doorsOpen = setOf(Door.FrontLeft, Door.Trunk)).doorSummary)
        assertNull(CanboxState().doorSummary)
    }

    @Test fun radarLevelsMapToDistancesAndPlaces() {
        assertEquals(listOf(null, 1.8, 1.2, 0.6), (0..3).map { Radar.distanceM(it) })
        val r = Radar(rear = listOf(1, 2, 3, 1))
        assertEquals(2, r.rearIndex); assertEquals("Centre-right", Radar.place(r.rearIndex))
        assertNull(Radar().rearIndex); assertEquals("Clear", Radar.place(null))
        assertEquals(1.8, Radar(front = listOf(0, 1, 1, 0)).nearestFrontM!!, 0.0)
    }

    @Test fun absentFieldsStayNull() {                  // a field the car never sends must not turn into a zero
        val s = CanboxState()
        assertNull(s.odometerKm); assertNull(s.outsideC); assertNull(s.climate); assertNull(s.tyresBar); assertNull(s.steeringDeg); assertNull(s.radar)
    }

    @Test fun flowEmitsOnlyChanges() = runTest {
        val list = MockCanbox(tickMs = 1_000).states().take(3).toList()
        assertEquals(3, list.size); assertNotEquals(list[0], list[1]); assertNotEquals(list[1], list[2])    // distinctUntilChanged: no repeats
    }

    @Test fun radarSweepIsPeriodic() {
        assertEquals(RadarSweep.at(1_000), RadarSweep.at(13_000))
        assertTrue((0L until 12_000L step 2_000L).any { RadarSweep.at(it).max() == 3 })
    }
}
