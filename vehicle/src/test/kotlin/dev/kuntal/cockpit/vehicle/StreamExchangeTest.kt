package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class StreamExchangeTest {
    @Test fun returnsTextBeforePromptAndWritesCommandWithCr() {
        val out = ByteArrayOutputStream()
        val reply = exchangeOverStreams(ByteArrayInputStream("41 0C 1A F8\r\r>".toByteArray()), out, "010C")
        assertEquals("41 0C 1A F8", reply)
        assertEquals("010C\r", out.toString())
    }

    @Test fun stopsAtFirstPromptAndLeavesTheRest() {
        val input = ByteArrayInputStream("OK\r>41 0D 32\r>".toByteArray())
        assertEquals("OK", exchangeOverStreams(input, ByteArrayOutputStream(), "ATE0"))
        assertEquals("41 0D 32", exchangeOverStreams(input, ByteArrayOutputStream(), "010D"))
    }

    @Test fun throwsWhenLinkClosesBeforePrompt() {
        assertThrows(IOException::class.java) {
            exchangeOverStreams(ByteArrayInputStream("41 0C".toByteArray()), ByteArrayOutputStream(), "010C")
        }
    }
}
