package dev.kuntal.cockpit.perf

enum class PerfMode(val telemetryPeriodMs: Long) {
    Full(50),        // 20 Hz telemetry to the UI
    Light(100),      // 10 Hz: fewer recompositions when the unit cannot keep the frame budget
}

/**
 * Decides when to drop to the lighter mode: more than 2 % of frames over 12 ms for two windows in a row. It only goes back
 * after three clean windows (under 0.5 %), so it does not flap. Windows with too few frames are ignored.
 */
class PerfGovernor(
    private val badPct: Double = 2.0,
    private val goodPct: Double = 0.5,
    private val enterAfter: Int = 2,
    private val leaveAfter: Int = 3,
    private val minFrames: Int = 120,
) {
    var mode: PerfMode = PerfMode.Full
        private set
    private var bad = 0
    private var good = 0

    fun update(s: FrameStats.Summary): PerfMode {
        if (s.count < minFrames) return mode
        if (mode == PerfMode.Full) {
            if (s.over12Pct > badPct) { if (++bad >= enterAfter) { mode = PerfMode.Light; bad = 0; good = 0 } } else bad = 0
        } else {
            if (s.over12Pct < goodPct) { if (++good >= leaveAfter) { mode = PerfMode.Full; bad = 0; good = 0 } } else good = 0
        }
        return mode
    }
}
