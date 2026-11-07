package dev.kuntal.cockpit.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {
    private fun stats(vararg ms: Double, capacity: Int = 600) = FrameStats(capacity).also { s -> ms.forEach { s.record(it) } }

    @Test fun emptyIsAllZero() {
        val s = FrameStats().summary()
        assertEquals(0, s.count); assertEquals(0.0, s.p99, 0.0); assertEquals(0.0, s.over12Pct, 0.0)
    }

    @Test fun nearestRankPercentiles() {
        val s = stats(*DoubleArray(100) { (it + 1).toDouble() }).summary()      // 1..100 ms
        assertEquals(50.0, s.p50, 0.0); assertEquals(95.0, s.p95, 0.0); assertEquals(99.0, s.p99, 0.0); assertEquals(100.0, s.max, 0.0)
    }

    @Test fun percentOverTwelveMs() {
        val s = stats(*(DoubleArray(96) { 6.0 } + doubleArrayOf(13.0, 14.0, 20.0, 30.0))).summary()
        assertEquals(4.0, s.over12Pct, 1e-9)
    }

    @Test fun exactlyTwelveIsNotOver() = assertEquals(0.0, stats(12.0, 12.0).summary().over12Pct, 0.0)

    @Test fun percentMissingTheVsyncBudget() {
        val s = stats(*(DoubleArray(90) { 8.0 } + DoubleArray(10) { 17.0 })).summary()       // 60 Hz budget is 16.67 ms
        assertEquals(10.0, s.missedPct, 1e-9)
    }

    @Test fun ringKeepsOnlyTheLatestFrames() {
        val s = stats(100.0, 100.0, 1.0, 1.0, 1.0, capacity = 3).summary()
        assertEquals(3, s.count); assertEquals(1.0, s.max, 0.0)
    }

    @Test fun passesTheGateWhenP99IsAtOrUnderTenMs() {
        assertTrue(stats(*DoubleArray(200) { 4.0 }).summary().meetsGate())
        assertTrue(!stats(*(DoubleArray(95) { 4.0 } + DoubleArray(5) { 11.0 })).summary().meetsGate())
    }

    @Test fun gateRequiresZeroMissedVsyncs() {
        assertTrue(!stats(*(DoubleArray(199) { 4.0 } + doubleArrayOf(18.0))).summary().meetsGate())
    }

    @Test fun clearResets() { val s = stats(5.0, 6.0); s.clear(); assertEquals(0, s.summary().count) }
}
