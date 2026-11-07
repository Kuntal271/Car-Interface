package dev.kuntal.cockpit.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePipelineTest {
    /** 60 fps for [seconds]; frames [burstAt, burstAt + burst) cost 20 ms, the rest 6 ms. A report every 5 s (300 frames). */
    private fun run(feed: (Int) -> Double, seconds: Int, report: () -> PerfMode): List<PerfMode> {
        val modes = ArrayList<PerfMode>()
        for (i in 0 until seconds * 60) { feedOne(feed(i)); if ((i + 1) % 300 == 0) modes += report() }
        return modes
    }
    private var feedOne: (Double) -> Unit = {}

    @Test fun aOneOffBurstDoesNotDropToLightOnTheNewPipeline() {
        val p = FramePipeline()
        feedOne = p::record
        val modes = run({ i -> if (i in 600 until 618) 20.0 else 6.0 }, seconds = 40) { p.report().second }
        assertEquals("18 slow frames once in 40 s", List(8) { PerfMode.Full }, modes)
    }

    @Test fun theOldRollingRingJudgedTheSameBurstTwiceAndDroppedToLight() {
        // The behaviour before FramePipeline: the governor was fed summary() of the 600-frame ring, which overlaps the previous report.
        val ring = FrameStats(600); val g = PerfGovernor()
        feedOne = ring::record
        val modes = run({ i -> if (i in 600 until 618) 20.0 else 6.0 }, seconds = 40) { g.update(ring.summary()) }
        assertTrue("the same 18 frames were judged in two consecutive reports", PerfMode.Light in modes)      // the defect FramePipeline fixes
    }

    @Test fun sustainedSlowFramesStillDropToLight() {
        val p = FramePipeline()
        feedOne = p::record
        val modes = run({ i -> if (i % 10 == 0) 20.0 else 6.0 }, seconds = 30) { p.report().second }      // 10 % of frames over 12 ms
        assertEquals(PerfMode.Light, modes.last())
    }

    @Test fun staticScreenFramesAreNotJudgedTwice() {
        val p = FramePipeline()
        repeat(150) { p.record(25.0) }                   // an animation that ended badly, then silence
        p.report()
        repeat(10) { assertEquals(PerfMode.Full, p.report().second) }    // nothing new arrives: nothing is judged
    }

    @Test fun lightRecoversAfterThreeCleanWindows() {
        val p = FramePipeline()
        feedOne = p::record
        run({ if (it % 10 == 0) 20.0 else 6.0 }, seconds = 15) { p.report().second }
        val modes = run({ 6.0 }, seconds = 20) { p.report().second }
        assertEquals(PerfMode.Full, modes.last())
    }
}
