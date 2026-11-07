package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** What a hostile or glitching adapter can make the parser and the client believe (docs/SECURITY.md, threat (b)). */
class HostileAdapterTest {
    // ---- parser: only well-formed hex counts ----

    @Test fun signsAreNotHexDigits() {
        // "-1".toInt(16) == -1: used to turn "41 0C -1 -1" into a negative rpm
        assertNull(ElmParser.parse(Pid.RPM, "41 0C -1 -1"))
        assertNull(ElmParser.parse(Pid.RPM, "410C-1-1"))
        assertNull(ElmParser.parse(Pid.SPEED, "41 0D +F"))
        assertNull(ElmParser.parse(Pid.COOLANT_TEMP, "41 05 -A"))
        assertNull(ElmParser.parseBitmap(0x00, "41 00 -1 +1 -1 +1"))
    }

    @Test fun nonAsciiDigitsAreNotHexDigits() {
        assertNull(ElmParser.parse(Pid.SPEED, "41 0D \u0663\u0662"))       // Arabic-indic 32
        assertNull(ElmParser.parse(Pid.SPEED, "41 0D \uFF13\uFF12"))       // full-width 32
        assertNull(ElmParser.parse(Pid.SPEED, "\uFF14\uFF11 0D 32"))       // full-width header
    }

    @Test fun trailingGarbageMakesTheLineInvalid() {
        assertNull(ElmParser.parse(Pid.SPEED, "41 0D 32 ZZ"))
        assertNull(ElmParser.parse(Pid.SPEED, "41 0D 32 <script>"))
        assertNull(ElmParser.parse(Pid.SPEED, "41 0D 3"))
        assertEquals(50.0, ElmParser.parse(Pid.SPEED, "41 0D 32 00 00")!!, 0.0)       // padding bytes are fine
        assertEquals(50.0, ElmParser.parse(Pid.SPEED, "\u0000\u0000 41 0D 32 \u0000")!!, 0.0)   // stray NULs around the line are fine
    }

    @Test fun anotherLineMayStillCarryTheRealAnswer() {
        assertEquals(50.0, ElmParser.parse(Pid.SPEED, "41 0D ZZ\r41 0D 32")!!, 0.0)
    }

    @Test fun absurdButWellFormedValuesAreDroppedNotShown() {
        assertNull("0xFFFF rpm = 16383.75", ElmParser.parse(Pid.RPM, "41 0C FF FF"))
        assertNull("65.5 V on a 12 V system", ElmParser.parse(Pid.MODULE_VOLTAGE, "41 42 FF FF"))
        assertEquals(6000.0, ElmParser.parse(Pid.RPM, "41 0C 5D C0")!!, 0.0)
        assertEquals(14.22, ElmParser.parse(Pid.MODULE_VOLTAGE, "41 42 37 8C")!!, 0.0001)
    }

    @Test fun everyTwoByteReplyDecodesToAFiniteBoundedNumber() {
        for (pid in Pid.values()) for (a in 0..255) for (b in 0..255) {
            val raw = "41 %02X %02X %02X".format(pid.code, a, b)
            val v = ElmParser.parse(pid, raw) ?: continue
            assertTrue("$raw -> $v", v.isFinite() && v > -50.0 && v < 20_000.0)
        }
    }

    // ---- client: NO DATA floods must not leave old numbers on screen as if live ----

    private class Scripted(private val onValue: (String) -> String?) : Transport {
        var calls = 0
        override suspend fun open() {}
        override suspend fun close() {}
        override suspend fun exchange(cmd: String): String { calls++; return onValue(cmd) ?: "NO DATA" }
    }

    @Test fun aValueThatStopsAnsweringIsDroppedAfterThreeMisses() = runTest {
        var rpmReplies = 2
        val t = Scripted { cmd ->
            when (cmd) {
                "0100" -> "41 00 00 18 00 00"            // supports 0x0C (rpm) and 0x0D (speed)
                "010C" -> if (rpmReplies-- > 0) "41 0C 1A F8" else null
                "010D" -> "41 0D 32"
                "0120" -> "41 20 00 00 00 00"
                else -> null
            }
        }
        val samples = ElmClient(t, now = { currentTime }).samples().take(6).toList()
        assertEquals(1726.0, samples[0].rpm!!, 0.001)
        assertEquals(1726.0, samples[1].rpm!!, 0.001)
        assertEquals("miss 1", 1726.0, samples[2].rpm!!, 0.001)
        assertEquals("miss 2", 1726.0, samples[3].rpm!!, 0.001)
        assertNull("miss 3: no longer shown as the live value", samples[4].rpm)
        assertNull(samples[5].rpm)
        assertEquals("speed is still answered", 50.0, samples[5].speedKmh!!, 0.0)
    }

    @Test fun anAdapterThatAnswersInstantlyDoesNotSpinTheLoop() = runTest {
        val t = Scripted { cmd -> if (cmd == "0100") "41 00 00 08 00 00" else if (cmd == "010D") "41 0D 32" else null }   // supports 0x0D only
        ElmClient(t, now = { currentTime }).samples().take(50).toList()
        // virtual time advanced: at least ~20 ms per cycle, i.e. at most about 50 cycles/s
        assertTrue("took only ${currentTime} ms for 50 cycles", currentTime >= 49 * 20)
    }
}
