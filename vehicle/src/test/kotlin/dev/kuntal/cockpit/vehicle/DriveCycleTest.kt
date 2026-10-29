package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test

class DriveCycleTest {
    @Test fun idleAtStart() {
        val s = DriveCycle.at(0)
        assertEquals(0.0, s.speedKmh!!, 0.01)
        assertEquals(850.0, s.rpm!!, 0.01)
    }
    @Test fun cruise() = assertEquals(130.0, DriveCycle.at(50_000).speedKmh!!, 0.01)
    @Test fun boostWhileAccelerating() = assertTrue(DriveCycle.at(20_000).boostKpa!! > 20.0)
    @Test fun vacuumOnOverrun() = assertTrue(DriveCycle.at(65_000).boostKpa!! < 0.0)
    @Test fun rpmStaysInRange() {
        for (ms in 0..100_000 step 100) {
            val r = DriveCycle.at(ms.toLong()).rpm!!
            assertTrue("rpm=$r at $ms", r in 850.0..6500.0)
        }
    }
}
