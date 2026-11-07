package dev.kuntal.cockpit.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleTest {
    private fun source(vararg at: Pair<Long, Int>) = flow { var t = 0L; for ((ts, v) in at) { delay(ts - t); t = ts; emit(v) } }

    @Test fun firstValueIsImmediateThenLatestWinsPerPeriod() = runTest {
        val seen = ArrayList<Pair<Long, Int>>()
        val out = source(0L to 1, 10L to 2, 20L to 3, 120L to 4).throttleLatest(50)
        launch { out.collect { seen += currentTime to it } }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(0L to 1, 50L to 3, 120L to 4), seen)
    }

    @Test fun neverMoreThanOneValuePerPeriod() = runTest {
        val seen = ArrayList<Long>()
        launch { source(*(0..99).map { it * 5L to it }.toTypedArray()).throttleLatest(50).collect { seen += currentTime } }
        testScheduler.advanceUntilIdle()
        assertEquals(true, seen.zipWithNext().all { (a, b) -> b - a >= 50 })
    }

    @Test fun lastValueIsNotLost() = runTest {
        assertEquals(3, source(0L to 1, 5L to 2, 10L to 3).throttleLatest(50).toList().last())
    }

    @Test fun aQuietSourceSchedulesNothing() = runTest {
        // after the one emission, the only pending work is the period delay; once it is over the scheduler is idle (sample() ticks forever)
        launch { source(0L to 1).throttleLatest(50).collect { } }
        testScheduler.advanceUntilIdle()
        assertEquals(50L, currentTime)
    }
}
