package dev.kuntal.cockpit.perf

import kotlin.math.ceil

/** Rolling window of frame costs in milliseconds. Thread-safe: the platform calls [record] from its own thread. */
class FrameStats(private val capacity: Int = 600) {
    data class Summary(
        val count: Int, val p50: Double, val p95: Double, val p99: Double, val max: Double,
        val over12Pct: Double,        // share of frames slower than 12 ms (the fallback trigger)
        val missedPct: Double,        // share of frames slower than the 60 Hz budget (a missed vsync)
    ) {
        /** The project's gate: p99 <= 10 ms and no missed vsync. */
        fun meetsGate() = count > 0 && p99 <= GATE_P99_MS && missedPct == 0.0
    }

    private val ring = DoubleArray(capacity)
    private var next = 0
    private var size = 0

    @Synchronized fun record(ms: Double) { ring[next] = ms; next = (next + 1) % capacity; if (size < capacity) size++ }
    @Synchronized fun clear() { next = 0; size = 0 }

    @Synchronized fun summary(): Summary {
        if (size == 0) return Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val v = ring.copyOf(size).also { it.sort() }
        fun pct(p: Int) = v[(ceil(p / 100.0 * size).toInt() - 1).coerceIn(0, size - 1)]       // nearest-rank
        return Summary(
            count = size, p50 = pct(50), p95 = pct(95), p99 = pct(99), max = v[size - 1],
            over12Pct = 100.0 * v.count { it > OVER_MS } / size,
            missedPct = 100.0 * v.count { it > VSYNC_60_MS } / size,
        )
    }

    companion object {
        const val GATE_P99_MS = 10.0
        const val OVER_MS = 12.0
        const val VSYNC_60_MS = 1000.0 / 60.0
    }
}
