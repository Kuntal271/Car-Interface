package dev.kuntal.cockpit.perf

/**
 * What FrameMeter does with each frame cost, without any Android types so it is JVM-testable.
 *
 * Two windows: [rolling] (the last 600 frames) is what the overlay and the log show; [window] holds only the frames since the last
 * [report] and is what the [PerfGovernor] judges. The governor's "two consecutive windows" must really be two DIFFERENT windows:
 * judged on the rolling ring (which overlaps its predecessor by half), a single burst of 18 slow frames out of 600 counted twice,
 * and the app dropped to Light on a one-off hiccup (a screen change, a GC). Clearing [window] on every report also stops stale frames
 * of an animation that ended from being re-judged forever on a static screen.
 */
class FramePipeline(private val governor: PerfGovernor = PerfGovernor(), capacity: Int = 600) {
    private val rolling = FrameStats(capacity)
    private val window = FrameStats(capacity)

    fun record(totalMs: Double) { rolling.record(totalMs); window.record(totalMs) }

    fun clear() { rolling.clear(); window.clear() }

    /** The rolling summary to display, and the governor's mode after judging the frames of the window that just ended. */
    fun report(): Pair<FrameStats.Summary, PerfMode> {
        val shown = rolling.summary()
        val mode = governor.update(window.summary())
        window.clear()
        return shown to mode
    }
}
