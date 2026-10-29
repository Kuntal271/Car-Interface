package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test

class CommandGuardTest {
    @Test fun allowsConfigAndMode01() {
        listOf("ATZ", "ATE0", "ATSP6", "ATAT1", "0100", "010C", "0120").forEach {
            assertTrue(it, CommandGuard.isAllowed(it))
        }
    }

    @Test fun rejectsEverythingElse() {
        listOf(
            "", "03", "04", "07", "0A", "0902", "1003", "2201F4", "14FFFF", "2EF190", "3101",
            "ATPP 0C SV 00", "ATPP FF ON", "010C\r04",
        ).forEach { assertFalse("should reject: $it", CommandGuard.isAllowed(it)) }
    }
}
