package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** The TCP transport really hangs up on cancellation and bounds what it accepts. */
class TcpHangupTest {
    // ---- the TCP transport really hangs up -------------------------------------------------------------------------

    @Test fun cancellingTheSessionHangsUpOnTheAdapterEvenWhileReadingAReply() = runBlocking {
        ServerSocket(0).use { server ->
            val eof = CountDownLatch(1); val accepted = CountDownLatch(1)
            val t = Thread {
                val c = server.accept(); accepted.countDown()
                val i = c.getInputStream(); c.getOutputStream().write("ELM327 v1.5\r>".toByteArray())
                try { while (i.read() >= 0) { } ; eof.countDown() } catch (_: Exception) { eof.countDown() }       // client closes -> EOF or reset
            }.also { it.isDaemon = true; it.start() }
            val job = launch(Dispatchers.Default) { ElmClient(TcpTransport("127.0.0.1", server.localPort, timeoutMs = 20_000)).samples().collect { } }
            assertTrue(accepted.await(5, TimeUnit.SECONDS))
            Thread.sleep(300)                                      // the client now waits for the reply to ATE0
            withTimeout(3_000) { job.cancelAndJoin() }             // before the fix: blocked in read() for the whole 20 s
            assertTrue("adapter never saw the client hang up (socket leaked)", eof.await(3, TimeUnit.SECONDS))
        }
    }

    @Test fun anEndlessStreamFromTheAdapterIsCutOffAfterAFewKilobytes() = runBlocking {
        ServerSocket(0).use { server ->
            val written = AtomicLong(0)
            Thread {
                val c = server.accept(); val o: OutputStream = c.getOutputStream(); c.getInputStream().read()
                val chunk = ByteArray(1024) { 'A'.code.toByte() }
                try { repeat(16 * 1024) { o.write(chunk); written.addAndGet(1024) } } catch (_: Exception) {}       // up to 16 MB, no newline, no prompt
            }.also { it.isDaemon = true; it.start() }
            try { withTimeout(10_000) { ElmClient(TcpTransport("127.0.0.1", server.localPort, timeoutMs = 4_000)).samples().first() }; fail("garbage is not a sample") } catch (_: IOException) {}
            assertTrue("adapter was allowed to push ${written.get()} bytes", written.get() < 2L * 1024 * 1024)
        }
    }
}
