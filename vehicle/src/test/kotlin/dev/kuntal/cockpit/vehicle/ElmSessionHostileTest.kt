package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** What the session reports when the vehicle stops answering. */
class ElmSessionHostileTest {
    // ---- ElmClient session --------------------------------------------------------------------------------------------

    /** Replies from a function of (command, how many commands so far). */
    private class Scripted(val reply: (String, Int) -> String) : Transport {
        var n = 0
        override suspend fun open() {}
        override suspend fun close() {}
        override suspend fun exchange(cmd: String): String { n++; return reply(cmd, n) }
    }

    private val good = mapOf("0100" to "41 00 BE 3F A8 13", "0120" to "41 20 00 00 00 00", "010C" to "41 0C 1A F8", "010D" to "41 0D 32", "0105" to "41 05 7B")

    @Test fun anEcuThatNeverAnswersEndsTheSessionInsteadOfSpinningOnEmptySamples() = runTest {
        // Ignition off / adapter plugged into a bench: 0100 says UNABLE TO CONNECT. Before the fix: no PIDs, no suspension point, an endless
        // stream of all-null samples, and the status bar showing the link as connected.
        val t = Scripted { c, _ -> if (c == "0100") "SEARCHING...\rUNABLE TO CONNECT" else "NO DATA" }
        try { ElmClient(t, now = { 1L }).samples().first(); fail("a vehicle that does not answer is not a connection") } catch (_: IOException) {}
    }

    @Test fun anEcuThatGoesQuietEndsTheSession() = runTest {
        val t = Scripted { c, n -> if (n < 60) good[c] ?: "NO DATA" else "NO DATA" }
        var got = 0
        try { ElmClient(t, now = { 1L }).samples().collect { got++ }; fail("must end") } catch (_: IOException) {}
        assertTrue("emitted $got samples", got in 1..80)
    }

    @Test fun aReadingThatKeepsFailingIsDroppedNotShownForever() = runTest {
        val rpmOk = AtomicBoolean(true)
        val t = Scripted { c, _ -> if (c == "010C" && !rpmOk.get()) "NO DATA" else good[c] ?: "NO DATA" }
        val seen = mutableListOf<VehicleSample>()
        try {
            ElmClient(t, now = { 1L }).samples().collect { s -> seen += s; if (seen.size == 3) rpmOk.set(false); if (seen.size >= 12) throw CancellationExit() }
        } catch (_: CancellationExit) {}
        assertNotNull(seen[3].rpm)                                  // a single miss is noise: keep the last value
        assertNull("stale rpm still shown after repeated misses", seen.last().rpm)
        assertNotNull(seen.last().speedKmh)                         // other readings are untouched
    }

    private class CancellationExit : RuntimeException()
}
