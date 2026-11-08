package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/** A reply that never ends must not grow memory or block forever. */
class StreamBoundsTest {
    // ---- exchangeOverStreams ----------------------------------------------------------------------------------------

    private class Endless(private val b: Int = 'A'.code) : InputStream() {
        var read = 0L
        override fun read(): Int { read++; return b }
    }

    @Test fun aReplyWithoutAPromptIsCutOffInsteadOfGrowingForever() {
        val inp = Endless()
        try { exchangeOverStreams(inp, java.io.ByteArrayOutputStream(), "010C"); fail("must give up") } catch (_: IOException) {}
        assertTrue("read ${inp.read} bytes before giving up", inp.read <= 64 * 1024)
    }

    @Test fun aReplyThatNeverEndsRunsIntoTheDeadlineEvenWhenEveryByteIsInTime() {
        val clock = AtomicLong(0)
        val slow = object : InputStream() { override fun read(): Int { clock.addAndGet(1_500); return 'A'.code } }     // 1.5 s per byte: under any read timeout
        try { exchangeOverStreams(slow, java.io.ByteArrayOutputStream(), "010C", deadlineMs = 10_000, nanoTime = { clock.get() * 1_000_000 }); fail("must give up") } catch (_: IOException) {}
        assertTrue(clock.get() <= 12_000)
    }

}
