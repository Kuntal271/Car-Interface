package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.CommandGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    /** The diagnostics screen shows invented fault codes. Reading real ones is mode 03, which must stay blocked: this test is the tripwire. */
    @Test fun readingFaultCodesRemainsBlockedByTheGuard() {
        listOf("03", "0300", "04", "07", "0A", "1902", "14FFFF").forEach { assertFalse("must stay blocked: $it", CommandGuard.isAllowed(it)) }
    }

    @Test fun mockCodesAreDeterministicAndLabelled() {
        val a = MockDiagSource().faults(); val b = MockDiagSource().faults()
        assertEquals(a, b); assertEquals(3, a.size)
        assertTrue(a.all { it.code.matches(Regex("P[0-3][0-9A-F]{3}")) })
        assertTrue(DiagNote.EXPLAIN.contains("invented")); assertEquals("Mode 03 blocked", DiagNote.BLOCKED)
        assertTrue(a.any { it.freeze == null }); assertTrue(a.any { it.freeze != null })
    }
}
