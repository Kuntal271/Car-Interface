package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Shortest time between two polling cycles (25 Hz): above the UI's 20 Hz, so it never makes the display older. */
const val MIN_CYCLE_MS = 40L

class ElmClient(
    private val transport: Transport,
    private val now: () -> Long = System::currentTimeMillis,
    private val minCycleMs: Long = MIN_CYCLE_MS,
) : VehicleSource {
    var supported: Set<Pid> = emptySet()
        private set

    private suspend fun ask(cmd: String): String {
        require(CommandGuard.isAllowed(cmd)) { "blocked command: $cmd" }
        return transport.exchange(cmd)
    }

    /** EcoSport = ISO 15765-4 CAN 11-bit/500k, so ATSP6 skips the slow protocol search. */
    suspend fun connect() {
        transport.open()
        for (c in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP6")) ask(c)
        val found = mutableSetOf<Int>()
        var base = 0x00
        while (base <= 0x60) {
            val bytes = ElmParser.parseBitmap(base, ask("01%02X".format(base))) ?: break
            val set = PidDecoder.supportedFrom(base, bytes)
            found += set
            if ((base + 0x20) !in set) break
            base += 0x20
        }
        supported = Pid.values().filter { it.code in found }.toSet()
    }

    override fun samples(): Flow<VehicleSample> = flow {
        try {
            connect()
            val schedule = PollSchedule(supported)
            var s = VehicleSample(now())
            var cycle = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val started = System.nanoTime()
                for (pid in schedule.pidsForCycle(cycle)) {
                    ElmParser.parse(pid, ask(pid.request))?.let { s = s.with(pid, it) }
                }
                emit(s.copy(tsMs = now()))
                cycle++
                // A slow adapter paces itself (30-100 ms per command); a fast or simulated one would otherwise be polled in a tight loop,
                // burning CPU and bus time for samples the screen (20 Hz at most) never shows.
                val spent = (System.nanoTime() - started) / 1_000_000
                if (spent < minCycleMs) delay(minCycleMs - spent)
            }
        } finally {
            transport.close()
        }
    }
}
