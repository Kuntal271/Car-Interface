package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs only when ELM_TCP=host:port is set (see tools/elm-emu.sh). */
class TcpIntegrationTest {
    @Test fun readsRpmAndSpeedFromEmulator() = runBlocking {
        val target = System.getenv("ELM_TCP")
        assumeTrue("ELM_TCP not set", target != null)
        val (host, port) = target.split(":")
        val s = ElmClient(TcpTransport(host, port.toInt())).samples().first()
        println("ELM sample: rpm=${s.rpm} speed=${s.speedKmh} coolant=${s.coolantC}")
        assertNotNull(s.rpm)
        assertNotNull(s.speedKmh)
    }
}
