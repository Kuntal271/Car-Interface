package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** A hostile adapter must end in an IOException within fixed bounds, never in unbounded memory or a hung read loop. */
class StreamExchangeLimitsTest {
    private class Endless(private val byte: Int, val onRead: () -> Unit = {}) : InputStream() {
        var served = 0L
        override fun read(): Int { served++; onRead(); return byte }
    }

    @Test fun neverSendingThePromptIsCutOffAtTheSizeCap() {
        val adapter = Endless('A'.code)
        val e = assertThrows(IOException::class.java) { exchangeOverStreams(adapter, ByteArrayOutputStream(), "010C") }
        assertEquals("reply too long", e.message)
        assertEquals("reads stop right after the cap", MAX_REPLY_CHARS + 1L, adapter.served)
    }

    @Test fun anEndlessFloodOfControlCharactersAndNoDataIsCutOffToo() {
        for (b in listOf(0, '\r'.code, '\n'.code, 0xFF, ' '.code, 0x1B)) {
            val adapter = Endless(b)
            assertThrows(IOException::class.java) { exchangeOverStreams(adapter, ByteArrayOutputStream(), "0100") }
            assertTrue(adapter.served <= MAX_REPLY_CHARS + 1L)
        }
        val noData = "NO DATA\r".repeat(10_000).toByteArray()
        assertThrows(IOException::class.java) { exchangeOverStreams(ByteArrayInputStream(noData), ByteArrayOutputStream(), "010D") }
    }

    @Test fun aDripFeedThatKeepsEveryGapShortStillHitsTheDeadline() {
        // each byte arrives "after 1 s" on a fake clock and the prompt never comes: a per-read socket timeout would never fire
        var now = 0L
        val adapter = Endless('1'.code) { now += 1_000_000_000L }
        val e = assertThrows(IOException::class.java) {
            exchangeOverStreams(adapter, ByteArrayOutputStream(), "010C", maxChars = 1_000_000, deadlineMs = 10_000, nanoTime = { now })
        }
        assertEquals("reply too slow", e.message)
        assertTrue("stopped after about 10 bytes, not ${adapter.served}", adapter.served in 10..12)
    }

    @Test fun aReplyExactlyAtTheCapIsAccepted() {
        val body = "B".repeat(MAX_REPLY_CHARS)
        assertEquals(body, exchangeOverStreams(ByteArrayInputStream("$body>".toByteArray()), ByteArrayOutputStream(), "010C"))
        assertThrows(IOException::class.java) {
            exchangeOverStreams(ByteArrayInputStream("${body}B>".toByteArray()), ByteArrayOutputStream(), "010C")
        }
    }

    @Test fun normalRepliesAreUnaffected() {
        val reply = "7E8 06 41 00 BE 3F A8 13\r7E9 06 41 00 98 18 00 01\r\r"
        assertEquals(reply.trim(), exchangeOverStreams(ByteArrayInputStream("$reply>".toByteArray()), ByteArrayOutputStream(), "0100"))
    }

    @Test fun theClientEndsTheSessionOnAHostileAdapterInsteadOfHanging() {
        val client = ElmClient(object : Transport {
            override suspend fun open() {}
            override suspend fun close() {}
            override suspend fun exchange(cmd: String): String = exchangeOverStreams(Endless('X'.code), ByteArrayOutputStream(), cmd)
        })
        val e = assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { client.connect() } }
        assertEquals("reply too long", e.message)
    }
}
