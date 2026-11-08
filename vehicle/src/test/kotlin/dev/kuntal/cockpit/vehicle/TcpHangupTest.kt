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

    /**
     * The client cuts an endless reply off and hangs up. The exact byte cap is pinned deterministically in StreamExchangeLimitsTest; here the
     * contract is observed on a real socket: the exchange fails with IOException, and the adapter's writes start failing (it was hung up on)
     * before it could push everything it had. How many bytes the OS had already buffered by then differs per kernel (macOS a few hundred KB,
     * Linux loopback several MB), so that number is NOT asserted tightly.
     */
    @Test fun anEndlessStreamFromTheAdapterIsCutOffAndTheClientHangsUp() = runBlocking {
        ServerSocket(0).use { server ->
            val total = 64L * 1024 * 1024
            val written = AtomicLong(0)
            val hungUp = CountDownLatch(1)
            Thread {
                val c = server.accept(); val o: OutputStream = c.getOutputStream(); c.getInputStream().read()
                val chunk = ByteArray(1024) { 'A'.code.toByte() }
                try { while (written.get() < total) { o.write(chunk); written.addAndGet(1024) } } catch (_: Exception) { hungUp.countDown() }   // no newline, no prompt
            }.also { it.isDaemon = true; it.start() }
            try { withTimeout(10_000) { ElmClient(TcpTransport("127.0.0.1", server.localPort, timeoutMs = 4_000)).samples().first() }; fail("garbage is not a sample") } catch (_: IOException) {}
            assertTrue("the adapter was never hung up on (wrote ${written.get()} bytes)", hungUp.await(10, TimeUnit.SECONDS))
            assertTrue("the adapter managed to push everything (${written.get()} bytes): nothing cut it off", written.get() < total)
        }
    }
}
