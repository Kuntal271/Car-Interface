package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveVariantsTest {
    @Test fun threeVariantsInOrder() = assertEquals(listOf("Cluster", "Boost", "Cruise"), DriveVariant.entries.map { it.label })

    @Test fun cruiseWordsFollowThresholds() {
        assertEquals("Good", CruiseText.smooth(82)); assertEquals("Fair", CruiseText.smooth(60)); assertEquals("Rough", CruiseText.smooth(10))
        assertEquals("Normal", CruiseText.coolant(92.0)); assertEquals("Warming up", CruiseText.coolant(40.0)); assertEquals("Too hot", CruiseText.coolant(106.0)); assertEquals("", CruiseText.coolant(null))
    }

    @Test fun mockCruiseHintsAShiftNearTheTopOfTheGear() {
        val c = MockCruise()
        assertEquals(4, c.state(74.0, 5_029.0, 3).shiftHintGear); assertNull(c.state(74.0, 2_500.0, 3).shiftHintGear); assertNull(c.state(74.0, 5_029.0, 6).shiftHintGear)
        assertEquals(6.4, c.state(0.0, 0.0, null).avgLPer100!!, 0.0)
    }

    @Test fun historyKeepsTheNewestInOrder() {
        val h = History(5)
        for (i in 1..3) h.add(i.toDouble(), i * 1000.0)
        assertEquals(3, h.size); assertEquals(1.0, h.boost(0), 0.0); assertEquals(3000.0, h.rpm(2), 0.0)
        for (i in 4..8) h.add(i.toDouble(), i * 1000.0)
        assertEquals(5, h.size); assertEquals(4.0, h.boost(0), 0.0); assertEquals(8.0, h.boost(4), 0.0)
    }

    @Test fun historyMissingValuesAreNaNNotZero() { val h = History(3); h.add(null, null); assertTrue(h.boost(0).isNaN()) }
}
