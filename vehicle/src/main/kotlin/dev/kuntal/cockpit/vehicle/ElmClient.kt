package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException

/** Shortest time between two polling cycles (25 Hz): above the UI's 20 Hz, so it never makes the display older. */
const val MIN_CYCLE_MS = 40L

class ElmClient(
    private val transport: Transport,
    private val now: () -> Long = MonotonicClock,
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
            // A vehicle that answers nothing (ignition off, bench adapter: "UNABLE TO CONNECT") is not a connection. Without PIDs there
            // was nothing to wait for either, so the loop below used to spin and emit empty samples as fast as it could.
            if (supported.none { it in PollSchedule.FAST }) throw IOException("the vehicle did not report rpm or speed")
            val schedule = PollSchedule(supported)
            var s = VehicleSample(now())
            val misses = HashMap<Pid, Int>()
            var cycle = 0
            var silentCycles = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val cycleStart = now()
                var answered = 0
                for (pid in schedule.pidsForCycle(cycle)) {
                    val v = ElmParser.parse(pid, ask(pid.request))
                    if (v != null) { s = s.with(pid, v); misses[pid] = 0; answered++ }
                    // one miss is noise and keeps the last reading; a reading that keeps failing must not stay on screen as if it were live
                    else if ((misses.merge(pid, 1, Int::plus) ?: 0) >= MISSES_BEFORE_DROP) s = s.without(pid)
                }
                silentCycles = if (answered == 0) silentCycles + 1 else 0
                if (silentCycles >= SILENT_CYCLES_BEFORE_LOST) throw IOException("the vehicle stopped answering")
                emit(s.copy(tsMs = now()))
                cycle++
                val spent = now() - cycleStart
                if (spent < minCycleMs) delay(minCycleMs - spent)          // an adapter that answers at once must not pin a core (or the bus) at thousands of cycles a second
            }
        } finally {
            withContext(NonCancellable) { transport.close() }          // a cancelled coroutine cannot suspend: without this the socket was never closed
        }
    }

    private companion object {
        const val MISSES_BEFORE_DROP = 3
        const val SILENT_CYCLES_BEFORE_LOST = 5
    }
}
