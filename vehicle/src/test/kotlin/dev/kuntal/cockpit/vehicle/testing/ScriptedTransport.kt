package dev.kuntal.cockpit.vehicle.testing

import dev.kuntal.cockpit.vehicle.Transport
import java.io.IOException

/**
 * A [Transport] whose replies come from a function of (command, how many commands were sent so far), with open/close counting.
 * Shared by every test that needs an adapter that misbehaves: silent PIDs, garbage, a link that dies after N commands.
 */
class ScriptedTransport(
    private val dieAfter: Int = Int.MAX_VALUE,
    private val reply: (cmd: String, n: Int) -> String,
) : Transport {
    val sent = mutableListOf<String>()
    var opened = 0
        private set
    var closed = 0
        private set

    override suspend fun open() { opened++ }
    override suspend fun close() { closed++ }
    override suspend fun exchange(cmd: String): String {
        sent += cmd
        if (sent.size > dieAfter) throw IOException("link closed after $dieAfter commands")
        return reply(cmd, sent.size)
    }

    companion object {
        /** What a healthy 1.0 EcoBoost answers: supports rpm, speed, coolant, MAP, throttle, load, intake. */
        val HEALTHY = mapOf(
            "0100" to "41 00 BE 3F A8 13", "0120" to "41 20 00 00 00 00",
            "010C" to "41 0C 1A F8", "010D" to "41 0D 32", "0105" to "41 05 7B",
        )
    }
}
