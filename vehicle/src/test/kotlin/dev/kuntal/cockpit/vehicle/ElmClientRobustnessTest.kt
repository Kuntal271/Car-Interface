package dev.kuntal.cockpit.vehicle

import dev.kuntal.cockpit.vehicle.testing.ScriptedTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class ElmClientRobustnessTest {
    private fun healthy() = ScriptedTransport { cmd, _ -> ScriptedTransport.HEALTHY[cmd] ?: "NO DATA" }

    @Test fun theTransportIsClosedWhenTheCollectorStops() = runTest {
        val t = healthy()
        ElmClient(t).samples().take(2).toList()
        assertEquals(1, t.opened)
        assertEquals("closed in finally{}, even though take(2) cancelled the flow", 1, t.closed)
    }

    @Test fun aLinkThatDiesMidSessionSurfacesAsIOExceptionAndStillCloses() = runTest {
        val t = ScriptedTransport(dieAfter = 20) { cmd, _ -> ScriptedTransport.HEALTHY[cmd] ?: "NO DATA" }
        try {
            ElmClient(t).samples().take(1_000).toList()
            fail("expected the dead link to propagate")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("link closed"))
        }
        assertEquals(1, t.closed)
    }

    @Test fun everyCommandSentIsReadOnly() = runTest {
        val t = healthy()
        ElmClient(t).samples().take(30).toList()
        assertTrue("sent: ${t.sent.distinct()}", t.sent.all { CommandGuard.isAllowed(it) })
        assertTrue(t.sent.none { it.startsWith("03") || it.startsWith("04") || it.startsWith("07") || it.startsWith("ATPP") })
    }

    /** Fixed (was ARCH-04 / SECURITY-04 / MONKEY-05): a sensor that stops answering is dropped after a few misses, not shown as live forever. */
    @Test fun aPidThatGoesSilentIsDroppedAfterAFewMisses() = runTest {
        val t = ScriptedTransport { cmd, n -> if (cmd == "0105" && n > 40) "NO DATA" else ScriptedTransport.HEALTHY[cmd] ?: "NO DATA" }
        val samples = ElmClient(t).samples().take(60).toList()
        assertEquals(83.0, samples.first().coolantC!!, 0.001)
        assertNull("coolant has been silent for a long time, so it must no longer be shown", samples.last().coolantC)
    }

    @Test fun garbageFromTheAdapterNeverThrowsInTheParser() {
        val r = Random(327)
        val alphabet = "0123456789ABCDEF \r\n>?.NODATASEARCHING41"
        repeat(5_000) {
            val raw = buildString { repeat(r.nextInt(0, 60)) { append(alphabet.random(r)) } }
            for (pid in Pid.entries) {
                val v = ElmParser.parse(pid, raw)
                if (v != null) assertTrue("finite value for $pid from '$raw'", v.isFinite())
            }
            ElmParser.parseBitmap(0x00, raw)
        }
    }

    /** Fixed (was a characterisation of a spin loop): a vehicle that answers nothing is not a connection; the flow fails so the link goes Lost and backs off. */
    @Test fun aSilentVehicleIsAFailedConnectionNotAStreamOfEmptySamples() = runTest {
        val t = ScriptedTransport { _, _ -> "NO DATA" }
        try {
            ElmClient(t, now = { 5L }).samples().first()
            fail("a vehicle that reports nothing must not look connected")
        } catch (e: IOException) {
            assertTrue(e.message!!, e.message!!.contains("did not report"))
        }
        assertEquals("the adapter is still closed", 1, t.closed)
    }
}
