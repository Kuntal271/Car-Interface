package dev.kuntal.cockpit.data

import org.junit.Assert.*
import org.junit.Test

/** Stored settings are parsed from SharedPreferences and (in debug builds) from launch extras: nothing there is trusted. */
class SettingsHardeningTest {
    @Test fun hostIsLimitedToHostnameCharactersAndLength() {
        assertEquals("192.168.0.10", Settings(tcpHost = "192.168.0.10").sanitized().tcpHost)
        assertEquals("obd.local", Settings(tcpHost = "obd.local").sanitized().tcpHost)
        assertEquals("fe80::1%wlan0", Settings(tcpHost = "fe80::1%wlan0").sanitized().tcpHost)
        assertEquals("[::1]", Settings(tcpHost = "[::1]").sanitized().tcpHost)
        assertEquals("1.2.3.4..evil.com", Settings(tcpHost = "1.2.3.4/../evil .com").sanitized().tcpHost)
        assertEquals("a.bscript", Settings(tcpHost = "a.b\r\n\u0000<script>").sanitized().tcpHost)
        assertFalse(Settings(tcpHost = "a\u202Eb\u200B.com").sanitized().tcpHost.any { it.code > 127 })
        assertEquals(253, Settings(tcpHost = "a".repeat(100_000)).sanitized().tcpHost.length)
        assertEquals("only junk falls back to the default", Settings.DEFAULT_HOST, Settings(tcpHost = "\r\n \u0000 / ? #").sanitized().tcpHost)
    }

    @Test fun bluetoothAddressMustBeAMacOrIsDropped() {
        assertEquals("AA:BB:CC:DD:EE:FF", Settings(btAddress = "aa:bb:cc:dd:ee:ff").sanitized().btAddress)
        for (bad in listOf("zz", "AA:BB:CC:DD:EE", "AA:BB:CC:DD:EE:FF:00", "AA-BB-CC-DD-EE-FF", "AA:BB:CC:DD:EE:GG", "AA:BB:CC:DD:EE:F\nF", "x".repeat(5000))) {
            assertEquals(bad, "", Settings(btAddress = bad, source = SourceKind.ObdBluetooth).sanitized().btAddress)
        }
        assertEquals("a bad address means 'no adapter chosen', never the simulator", "none",
            Settings(source = SourceKind.ObdBluetooth, btAddress = "junk").sanitized().sourceKey)
    }

    @Test fun adapterNameIsShortAndPrintable() {
        val rogue = "OBDII\u202E" + "\u0007\r\n" + "x".repeat(500)
        val n = Settings(btName = rogue).sanitized().btName
        assertTrue(n.length <= 64)
        assertTrue(n.none { Character.getType(it) == Character.CONTROL.toInt() || Character.getType(it) == Character.FORMAT.toInt() })
        assertTrue(n.startsWith("OBDII"))
    }

    @Test fun sanitizingIsIdempotentAndSurvivesTheStoredMap() {
        val s = Settings(tcpHost = " Obd.LOCAL ", btAddress = "aa:bb:cc:dd:ee:ff", btName = "  Car  ").sanitized()
        assertEquals(s, s.sanitized())
        assertEquals(s, Settings.fromMap(s.toMap()))
    }

    @Test fun garbageInTheStoredMapNeverThrows() {
        val keys = listOf("theme", "clock", "motion", "speed", "pressure", "temp", "source", "tcpHost", "tcpPort", "bt", "btName", "redline", "tach", "shift", "speedScale", "perfOverlay")
        val junk = listOf("", " ", "\u0000", "-1", "2147483648", "-2147483649", "NaN", "9".repeat(1000), "\uD800", "null", "{}", "😀", "true\r\nfalse")
        for (k in keys) for (j in junk) Settings.fromMap(mapOf(k to j)).also { assertEquals(it, it.sanitized()) }
        val all = keys.associateWith { "9".repeat(50) }
        assertEquals(all.size, Settings.fromMap(all).toMap().size)
    }
}
