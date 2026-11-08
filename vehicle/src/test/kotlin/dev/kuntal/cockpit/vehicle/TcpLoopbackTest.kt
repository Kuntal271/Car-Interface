package dev.kuntal.cockpit.vehicle

import dev.kuntal.cockpit.vehicle.testing.LoopbackElm
import dev.kuntal.cockpit.vehicle.testing.ScriptedTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The TCP path end to end on the JVM, against an in-process adapter: replaces "needs the Python emulator" for CI. */
class TcpLoopbackTest {
    private val healthy = ScriptedTransport.HEALTHY

    @Test fun decodesSamplesOverARealSocketAndSendsOnlyAllowedCommands() = runBlocking {
        LoopbackElm(healthy).use { elm ->
            val samples = withTimeout(10_000) { ElmClient(TcpTransport("127.0.0.1", elm.port, timeoutMs = 2_000), now = { 1L }).samples().take(2).toList() }
            assertEquals(1726.0, samples[0].rpm!!, 0.001)
            assertEquals(50.0, samples[0].speedKmh!!, 0.001)
            assertEquals(83.0, samples[1].coolantC!!, 0.001)
            assertTrue("sent: ${elm.received.distinct()}", elm.received.isNotEmpty() && elm.received.all { CommandGuard.isAllowed(it) })
        }
    }

    @Test fun aConnectionClosedByTheAdapterSurfacesAsIOException() = runBlocking {
        LoopbackElm(healthy, dropAfter = 15).use { elm ->
            try {
                withTimeout(10_000) { ElmClient(TcpTransport("127.0.0.1", elm.port, timeoutMs = 2_000)).samples().take(10_000).toList() }
                fail("expected the dropped link to propagate")
            } catch (e: IOException) {
                assertTrue(e.message, e.message!!.contains("link closed"))
            }
        }
    }

    @Test fun nothingListeningIsAnIOExceptionNotAHang() = runBlocking {
        try {
            withTimeout(10_000) { ElmClient(TcpTransport("127.0.0.1", LoopbackElm.closedPort(), timeoutMs = 1_000)).samples().first() }
            fail("expected connection refused")
        } catch (_: IOException) {
            // expected
        }
    }
}
