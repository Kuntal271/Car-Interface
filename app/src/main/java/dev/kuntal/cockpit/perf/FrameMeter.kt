package dev.kuntal.cockpit.perf

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import dev.kuntal.cockpit.DebugHooks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide performance state, read by the view-model (telemetry rate) and the debug overlay. */
object Perf {
    private val _mode = MutableStateFlow(PerfMode.Full)
    private val _summary = MutableStateFlow(FrameStats.Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
    val mode: StateFlow<PerfMode> = _mode
    val summary: StateFlow<FrameStats.Summary> = _summary
    internal fun publish(s: FrameStats.Summary, m: PerfMode) { _summary.value = s; _mode.value = m }
}

/**
 * Measures what the head unit really spends per frame, via the platform's FrameMetrics (API 24+). The cost is TOTAL_DURATION:
 * from the intended vsync to the buffer swap, so it includes layout, draw, sync, command issue and GPU wait.
 * Every 5 s the window is summarised, handed to the [PerfGovernor], and (when [log]) written to logcat under tag CockpitPerf.
 */
class FrameMeter(private val window: Window, private val log: Boolean) {
    private val pipeline = FramePipeline()          // total cost (vsync to swap): a rolling 600 frames to show, a per-report window for the governor
    private val uiStats = FrameStats(600)           // app-side CPU cost: animation + layout/measure + draw + sync (no GPU wait)
    private val thread = HandlerThread("frame-meter").also { it.start() }
    private val handler = Handler(thread.looper)
    private var lastReport = 0L
    @Volatile private var warmUpUntil = 0L

    private val listener = Window.OnFrameMetricsAvailableListener { _, fm, _ ->
        if (System.nanoTime() < warmUpUntil) return@OnFrameMetricsAvailableListener      // first composition and JIT are not steady-state cost
        pipeline.record(fm.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0)
        uiStats.record((fm.getMetric(FrameMetrics.ANIMATION_DURATION) + fm.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
            fm.getMetric(FrameMetrics.DRAW_DURATION) + fm.getMetric(FrameMetrics.SYNC_DURATION)) / 1_000_000.0)
        val now = System.nanoTime()
        if (now - lastReport >= REPORT_NS) {
            lastReport = now
            val (s, governed) = pipeline.report()
            val mode = DebugHooks.pinMode ?: governed
            Perf.publish(s, mode)
            val u = uiStats.summary()
            if (log) Log.i(TAG, "n=${s.count} total p50=%.1f p99=%.1f max=%.1f over12=%.1f%% missed=%.1f%% | app-cpu p50=%.1f p99=%.1f max=%.1f | gate=%s mode=%s".format(
                s.p50, s.p99, s.max, s.over12Pct, s.missedPct, u.p50, u.p99, u.max, if (s.meetsGate()) "PASS" else "FAIL", mode) + gcStats())
        }
    }

    /** Cumulative ART counters (tools/perf/bench.py differences them): GC count, blocking GCs, bytes allocated. Debug logging only. */
    private fun gcStats() = " | gc=%s blk=%s alloc=%s".format(
        Debug.getRuntimeStat("art.gc.gc-count"), Debug.getRuntimeStat("art.gc.blocking-gc-count"), Debug.getRuntimeStat("art.gc.bytes-allocated"))

    private val registration = ListenerHandle(
        add = { window.addOnFrameMetricsAvailableListener(listener, handler) },
        remove = { window.removeOnFrameMetricsAvailableListener(listener) },
    )

    fun start() { pipeline.clear(); uiStats.clear(); lastReport = System.nanoTime(); warmUpUntil = lastReport + WARMUP_NS; registration.attach() }
    fun stop() = registration.detach()
    fun release() { stop(); thread.quitSafely() }

    private companion object { const val TAG = "CockpitPerf"; const val REPORT_NS = 5_000_000_000L; const val WARMUP_NS = 2_500_000_000L }
}

/**
 * Registers a platform listener at most once and removes it only if it is registered. Window.removeOnFrameMetricsAvailableListener
 * throws IllegalArgumentException for a listener that is not there, and onStop() followed by onDestroy() removed it twice: the app
 * crashed every time the activity was finished (BACK on the launcher).
 */
class ListenerHandle(private val add: () -> Unit, private val remove: () -> Unit) {
    private var attached = false
    fun attach() { if (!attached) { attached = true; add() } }
    fun detach() { if (attached) { attached = false; remove() } }
}
