package dev.kuntal.cockpit.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime

/**
 * A manual wall clock for code that takes `now: () -> Long` (the convention in this code base: `ElmClient`, `SimSource`).
 * Time only moves when the test says so, so there is no sleeping and no flakiness.
 */
class FakeClock(var nowMs: Long = 0L) : () -> Long {
    override fun invoke(): Long = nowMs
    fun advance(ms: Long) { nowMs += ms }
}

/** A clock that follows the coroutine test scheduler's virtual time: `delay(50)` in the code under test moves it by 50 ms. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.virtualClock(): () -> Long = { currentTime }
