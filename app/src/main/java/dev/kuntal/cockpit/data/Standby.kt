package dev.kuntal.cockpit.data

import kotlin.math.abs

/**
 * Standby (the clock screen after idling): when to enter it. Never while the car moves, never when the owner turned it off.
 * Waking is any touch. [driftPx] moves the clock a few pixels each minute so a static image never burns into the panel.
 */
object Standby {
    val CHOICES = listOf(0, 5, 10, 30)            // minutes; 0 = off
    const val MOVING_KMH = 3.0

    fun label(min: Int) = if (min == 0) "Off" else "$min min"

    fun shouldSleep(idleMs: Long, timeoutMin: Int, speedKmh: Double?): Boolean =
        timeoutMin > 0 && idleMs >= timeoutMin * 60_000L && (speedKmh == null || speedKmh < MOVING_KMH)

    /** Small walk of the clock around its home position, a function of the minute only. Range -6..+6 px. */
    fun driftPx(minuteOfDay: Int): Pair<Int, Int> {
        val steps = intArrayOf(0, 4, 6, 4, 0, -4, -6, -4)
        val i = abs(minuteOfDay) % steps.size
        return steps[i] to steps[(i + 2) % steps.size]
    }
}

/** The boot splash timeline (design: boot.html, about 3.6 s). Every value is a 0..1 function of time; the screen only draws it. */
object BootAnim {
    const val TOTAL_MS = 3_600L
    fun clamp(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    private fun span(t: Long, a: Long, b: Long) = clamp((t - a).toFloat() / (b - a))
    fun oval(t: Long) = span(t, 0, 800)
    fun ring(t: Long) = span(t, 500, 1_700)
    fun script(t: Long) = span(t, 1_100, 1_900)
    fun welcome(t: Long) = span(t, 1_800, 2_500)
    fun loadBar(t: Long) = span(t, 800, TOTAL_MS)
    fun done(t: Long) = t >= TOTAL_MS
}
