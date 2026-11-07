package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BoostBarTest {
    private fun seg(bar: Double) = BoostBar.segments(bar)

    @Test fun designReferencePlus095Lights16Segments() {
        val s = seg(0.95)
        assertEquals(18, s.litFrom); assertEquals(16, s.litCount); assertEquals(0, s.vacCount)
    }

    @Test fun zeroBoostLightsNothing() { val s = seg(0.0); assertEquals(0, s.litCount); assertEquals(0, s.vacCount) }

    @Test fun vacuumFillsLeftwardFromTheZeroPoint() {
        val s = seg(-0.62)
        assertEquals(0, s.litCount); assertEquals(7, s.vacFrom); assertEquals(11, s.vacCount)
        assertEquals(17, s.vacFrom + s.vacCount - 1)          // last vacuum segment is the one just left of zero
    }

    @Test fun fullScalePositive() { val s = seg(1.5); assertEquals(18, s.litFrom); assertEquals(26, s.litCount) }
    @Test fun fullScaleVacuum() { val s = seg(-1.0); assertEquals(0, s.vacFrom); assertEquals(18, s.vacCount) }
    @Test fun beyondScaleIsClamped() { assertEquals(26, seg(3.0).litCount); assertEquals(18, seg(-3.0).vacCount) }
    @Test fun positiveAndVacuumNeverOverlap() {
        for (x in -120..160) { val s = seg(x / 100.0); assert(s.litCount == 0 || s.vacCount == 0) { "overlap at $x" } }
    }
    @Test fun litCountGrowsMonotonicallyWithBoost() {
        var last = -1
        for (x in 0..150) { val c = seg(x / 100.0).litCount; assert(c >= last); last = c }
    }
}
