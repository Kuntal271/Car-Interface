package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmootherTest {
    @Test fun zeroDtLeavesValueUnchanged() = assertEquals(10f, Smoother.step(10f, 100f, 0f, 0.08f), 1e-6f)

    @Test fun movesTowardsTargetWithoutOvershoot() {
        val v = Smoother.step(0f, 100f, 0.016f, 0.08f)
        assertTrue(v > 0f && v < 100f)
    }

    @Test fun reachesTargetOverManyTimeConstants() = assertEquals(100f, Smoother.step(0f, 100f, 2f, 0.08f), 0.01f)

    @Test fun isFrameRateIndependent() {
        // Two half steps equal one full step: the property that keeps 30 and 60 fps looking identical.
        val one = Smoother.step(0f, 100f, 0.032f, 0.08f)
        val two = Smoother.step(Smoother.step(0f, 100f, 0.016f, 0.08f), 100f, 0.016f, 0.08f)
        assertEquals(one, two, 1e-3f)
    }

    @Test fun snapsWhenCloseEnough() = assertEquals(5f, Smoother.step(4.9995f, 5f, 0.016f, 0.08f), 0f)
}
