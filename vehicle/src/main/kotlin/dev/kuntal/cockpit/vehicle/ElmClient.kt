package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ElmClient(
    private val transport: Transport,
    private val now: () -> Long = System::currentTimeMillis,
) : VehicleSource {
    var supported: Set<Pid> = emptySet()
        private set

    private companion object {
        const val MAX_MISSES = 3          // consecutive unanswered reads before a value stops being shown
        const val MIN_CYCLE_MS = 20L      // at most 50 poll cycles per second; the UI draws at most 20
    }

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
            val misses = HashMap<Pid, Int>()
            var cycle = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val t0 = now()
                for (pid in schedule.pidsForCycle(cycle)) {
                    val v = ElmParser.parse(pid, ask(pid.request))
                    if (v != null) { misses[pid] = 0; s = s.with(pid, v) }
                    // NO DATA / garbage / implausible: keep the last value for a moment (one lost frame is normal), then show nothing.
                    else if (misses.merge(pid, 1, Int::plus)!! >= MAX_MISSES) s = s.with(pid, null)
                }
                emit(s.copy(tsMs = now()))
                cycle++
                // An adapter that answers instantly (or a hostile one) must not turn this into a busy loop; real ones take longer anyway.
                delay((MIN_CYCLE_MS - (now() - t0)).coerceAtLeast(0))
            }
        } finally {
            transport.close()
        }
    }
}
