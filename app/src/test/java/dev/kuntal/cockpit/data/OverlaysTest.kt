package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaysTest {
    @Test fun lowerPriorityNumberWins() {
        val q = OverlayQueue()
        q.show(Overlay.Volume(18, 30), 0); q.show(Overlay.CoolantHigh(108), 100); q.show(Overlay.DoorMoving("Rear left door", 12), 200)
        assertEquals(Overlay.CoolantHigh(108), q.visible(300))
        q.dismiss(300); assertEquals(Overlay.DoorMoving("Rear left door", 12), q.visible(300))
    }

    @Test fun timedOverlaysExpire() {
        val q = OverlayQueue()
        q.show(Overlay.Volume(18, 30), 1_000)
        assertEquals(Overlay.Volume(18, 30), q.visible(2_499)); assertNull(q.visible(2_500)); assertTrue(q.isEmpty())
    }

    @Test fun sameKindReplacesAndRestartsTheClock() {
        val q = OverlayQueue()
        q.show(Overlay.Volume(18, 30), 0); q.show(Overlay.Volume(19, 30), 1_000)
        assertEquals(Overlay.Volume(19, 30), q.visible(2_400)); assertEquals(2_500L, q.nextExpiry())
    }

    @Test fun untimedStayUntilCleared() {
        val q = OverlayQueue(); q.show(Overlay.DoorMoving("x", 9), 0)
        assertEquals(Overlay.DoorMoving("x", 9), q.visible(10_000_000)); q.clear("door"); assertNull(q.visible(0))
    }

    @Test fun coolantHasHysteresis() {
        assertFalse(OverlayRules.coolantHigh(104.0, active = false)); assertTrue(OverlayRules.coolantHigh(105.0, active = false))
        assertTrue(OverlayRules.coolantHigh(102.0, active = true)); assertFalse(OverlayRules.coolantHigh(99.0, active = true)); assertFalse(OverlayRules.coolantHigh(null, true))
    }

    @Test fun doorWarnsOnlyWhenMoving() {
        val open = setOf(Door.RearLeft)
        assertNull(OverlayRules.doorMoving(open, 4.0)); assertNull(OverlayRules.doorMoving(emptySet(), 80.0)); assertNull(OverlayRules.doorMoving(open, null))
        assertEquals(Overlay.DoorMoving("Rear left door", 12), OverlayRules.doorMoving(open, 12.4))
    }

    @Test fun lowBatteryNeedsTheEngineOff() {
        assertTrue(OverlayRules.lowBattery(11.6, 0.0)); assertFalse(OverlayRules.lowBattery(11.6, 2_000.0)); assertFalse(OverlayRules.lowBattery(12.4, 0.0)); assertFalse(OverlayRules.lowBattery(null, 0.0))
    }

    @Test fun lowFuelFiresOnceOnTheCrossing() {
        assertTrue(OverlayRules.lowFuel(12.5, 11.9)); assertFalse(OverlayRules.lowFuel(11.9, 11.5)); assertFalse(OverlayRules.lowFuel(40.0, 39.0)); assertFalse(OverlayRules.lowFuel(null, null))
        assertTrue(OverlayRules.lowFuel(null, 8.0))               // first reading already low
    }

    @Test fun shiftUpNeedsAGearAndTheThreshold() {
        assertEquals(Overlay.ShiftUp(4), OverlayRules.shiftUp(5_300.0, 5_200, 3)); assertNull(OverlayRules.shiftUp(5_100.0, 5_200, 3)); assertNull(OverlayRules.shiftUp(5_300.0, 5_200, null)); assertNull(OverlayRules.shiftUp(6_000.0, 5_200, 6))
    }

    @Test fun coolantIsModal() { assertTrue(Overlay.CoolantHigh(108).modal); assertFalse(Overlay.Volume(1, 2).modal) }
}
