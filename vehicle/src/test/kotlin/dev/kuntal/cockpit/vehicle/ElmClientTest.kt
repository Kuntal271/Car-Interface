package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class FakeTransport(private val replies: Map<String, String>) : Transport {
    val sent = mutableListOf<String>()
    override suspend fun open() {}
    override suspend fun close() {}
    override suspend fun exchange(cmd: String): String { sent += cmd; return replies[cmd] ?: "NO DATA" }
}

class ElmClientTest {
    private val replies = mapOf(
        "0100" to "41 00 BE 3F A8 13", "0120" to "41 20 00 00 00 00",
        "010C" to "41 0C 1A F8", "010D" to "41 0D 32", "0105" to "41 05 7B",
    )

    @Test fun discoversSupportedPids() = runTest {
        val c = ElmClient(FakeTransport(replies)); c.connect()
        assertEquals(
            setOf(Pid.ENGINE_LOAD, Pid.COOLANT_TEMP, Pid.MAP, Pid.RPM, Pid.SPEED, Pid.INTAKE_TEMP, Pid.THROTTLE),
            c.supported,
        )
    }

    @Test fun emitsDecodedSamples() = runTest {
        val s = ElmClient(FakeTransport(replies), now = { 1L }).samples().take(2).toList()
        assertEquals(1726.0, s[0].rpm!!, 0.001)
        assertEquals(50.0, s[0].speedKmh!!, 0.001)
        assertEquals(83.0, s[1].coolantC!!, 0.001) // slow group is read on cycle 0 and carried forward
    }

    @Test fun onlyEverSendsAllowedCommands() = runTest {
        val t = FakeTransport(replies)
        ElmClient(t).samples().take(3).toList()
        assertTrue(t.sent.all { CommandGuard.isAllowed(it) })
    }

    @Test fun scheduleGroupsByRate() {
        val sched = PollSchedule(Pid.values().toSet())
        assertEquals(listOf(Pid.RPM, Pid.SPEED), sched.pidsForCycle(1))
        assertTrue(Pid.THROTTLE in sched.pidsForCycle(3) && Pid.COOLANT_TEMP !in sched.pidsForCycle(3))
        assertTrue(Pid.COOLANT_TEMP in sched.pidsForCycle(10))
    }

    @Test fun anInstantAdapterIsNotPolledInATightLoop() = runTest {
        // Without pacing this never returns (virtual time does not advance while the loop never suspends); with it, one cycle per 40 ms.
        var cycles = 0
        withTimeoutOrNull(1_000) { ElmClient(FakeTransport(replies)).samples().collect { cycles++ } }
        assertTrue("cycles in 1 s of virtual time: $cycles", cycles in 24..26)
    }
}
