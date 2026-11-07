package dev.kuntal.cockpit.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class PerfGovernorTest {
    private fun win(over12: Double, count: Int = 300) = FrameStats.Summary(count, 5.0, 8.0, 11.0, 14.0, over12, over12)

    @Test fun startsInFullMode() = assertEquals(PerfMode.Full, PerfGovernor().mode)

    @Test fun oneBadWindowIsNotEnough() {
        val g = PerfGovernor(); g.update(win(5.0)); assertEquals(PerfMode.Full, g.mode)
    }

    @Test fun twoConsecutiveBadWindowsDropToLight() {
        val g = PerfGovernor(); g.update(win(5.0)); g.update(win(5.0)); assertEquals(PerfMode.Light, g.mode)
    }

    @Test fun aGoodWindowBetweenResetsTheBadStreak() {
        val g = PerfGovernor(); g.update(win(5.0)); g.update(win(0.0)); g.update(win(5.0)); assertEquals(PerfMode.Full, g.mode)
    }

    @Test fun twoPercentExactlyIsNotBad() {
        val g = PerfGovernor(); repeat(5) { g.update(win(2.0)) }; assertEquals(PerfMode.Full, g.mode)
    }

    @Test fun recoveryNeedsThreeCleanWindows() {
        val g = PerfGovernor(); repeat(2) { g.update(win(5.0)) }
        g.update(win(0.0)); g.update(win(0.0)); assertEquals(PerfMode.Light, g.mode)
        g.update(win(0.0)); assertEquals(PerfMode.Full, g.mode)
    }

    @Test fun aBorderlineWindowDoesNotCountAsClean() {
        val g = PerfGovernor(); repeat(2) { g.update(win(5.0)) }
        g.update(win(0.0)); g.update(win(1.0)); g.update(win(0.0)); assertEquals(PerfMode.Light, g.mode)
    }

    @Test fun tooFewFramesChangeNothing() {
        val g = PerfGovernor(); repeat(5) { g.update(win(50.0, count = 20)) }; assertEquals(PerfMode.Full, g.mode)
    }

    @Test fun lightModeUsesALowerTelemetryRate() {
        assertEquals(50L, PerfMode.Full.telemetryPeriodMs); assertEquals(100L, PerfMode.Light.telemetryPeriodMs)
    }
}
