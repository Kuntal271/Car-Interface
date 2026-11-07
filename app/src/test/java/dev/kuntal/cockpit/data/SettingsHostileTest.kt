package dev.kuntal.cockpit.data

import org.junit.Assert.*
import org.junit.Test

/** Pasted megabytes, control characters and RTL junk in the text boxes / stored settings. */
class SettingsHostileTest {
    // ---- settings text --------------------------------------------------------------------------------------------------

    @Test fun aHugeHostIsCappedToTheLongestValidName() {
        assertTrue(Settings(tcpHost = "a".repeat(100_000)).sanitized().tcpHost.length <= 253)
        assertTrue(Settings.fromMap(mapOf("tcpHost" to "b".repeat(1_000_000))).tcpHost.length <= 253)
    }

    @Test fun controlCharactersAndWhitespaceNeverReachTheSocket() {
        assertEquals("10.0.2.2", Settings(tcpHost = " 10.0.\n2.2\u0000\t ").sanitized().tcpHost)
        assertEquals(Settings.DEFAULT_HOST, Settings(tcpHost = "\u0000\n \u200B").sanitized().tcpHost)
    }

    @Test fun adapterNameAndAddressAreCapped() {
        val s = Settings(btName = "n".repeat(50_000), btAddress = "A".repeat(50_000)).sanitized()
        assertTrue(s.btName.length <= 64 && s.btAddress.length <= 17)
    }
}
