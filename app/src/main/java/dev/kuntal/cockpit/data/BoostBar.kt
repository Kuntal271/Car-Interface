package dev.kuntal.cockpit.data

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Segment layout of the diverging boost bar (44 segments over -1.0 .. +1.5 bar, zero at 40 %). Positive boost lights
 * segments rightwards from the zero point; vacuum lights them leftwards. Segment i is centred at (i + 0.5) / n.
 */
object BoostBar {
    const val N = 44
    const val MIN_BAR = -1.0
    const val MAX_BAR = 1.5

    data class Segs(val litFrom: Int, val litCount: Int, val vacFrom: Int, val vacCount: Int)

    fun segments(bar: Double, n: Int = N): Segs {
        val z = -MIN_BAR / (MAX_BAR - MIN_BAR)
        val v = ((bar - MIN_BAR) / (MAX_BAR - MIN_BAR)).coerceIn(0.0, 1.0)
        val firstPos = ceil(z * n - 0.5).toInt().coerceAtLeast(0)
        val lastVac = floor(z * n - 0.5).toInt()
        return if (v >= z) {
            val last = floor(v * n - 0.5).toInt().coerceAtMost(n - 1)
            Segs(firstPos, (last - firstPos + 1).coerceAtLeast(0), 0, 0)
        } else {
            val from = ceil(v * n - 0.5).toInt().coerceAtLeast(0)
            Segs(firstPos, 0, from, (lastVac - from + 1).coerceAtLeast(0))
        }
    }
}
