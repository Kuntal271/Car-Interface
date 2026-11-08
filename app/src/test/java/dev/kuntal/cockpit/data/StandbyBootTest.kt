package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyBootTest {
    @Test fun neverWhenOffOrMoving() {
        assertFalse(Standby.shouldSleep(99 * 60_000L, 0, 0.0))
        assertFalse(Standby.shouldSleep(99 * 60_000L, 5, 40.0))
    }
    @Test fun afterTheTimeoutWhenParked() {
        assertFalse(Standby.shouldSleep(4 * 60_000L + 59_000, 5, 0.0)); assertTrue(Standby.shouldSleep(5 * 60_000L, 5, 0.0)); assertTrue(Standby.shouldSleep(5 * 60_000L, 5, null))
    }
    @Test fun settingsChoicesAreLabelled() = assertEquals(listOf("Off", "5 min", "10 min", "30 min"), Standby.CHOICES.map { Standby.label(it) })
    @Test fun driftStaysSmallAndMoves() {
        val all = (0 until 16).map { Standby.driftPx(it) }
        assertTrue(all.all { (x, y) -> Math.abs(x) <= 6 && Math.abs(y) <= 6 }); assertTrue(all.toSet().size > 4)
        assertEquals(Standby.driftPx(3), Standby.driftPx(3 + 8))
    }
    @Test fun bootTimelineIsMonotonicAndEndsAt3600() {
        var prev = 0f
        for (t in 0L..BootAnim.TOTAL_MS step 100) { val v = BootAnim.loadBar(t); assertTrue(v >= prev); prev = v }
        assertEquals(1f, BootAnim.oval(900), 0f); assertEquals(0f, BootAnim.welcome(1_000), 0f); assertEquals(1f, BootAnim.loadBar(BootAnim.TOTAL_MS), 0f)
        assertFalse(BootAnim.done(3_599)); assertTrue(BootAnim.done(3_600))
    }
}
