package dev.kuntal.cockpit.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Parking radar readings. Real source: the Canbox radar frames (types 0x22 / 0x23); until then a mock. */
interface RadarSource { fun readings(): Flow<Radar> }

/** The radar of whatever Canbox is plugged in: the same frames the Car screen uses. */
class CanboxRadar(private val canbox: CanboxSource) : RadarSource {
    override fun readings(): Flow<Radar> = canbox.states().map { it.radar ?: Radar() }
}

/** Pure sweep for demos and tests: an obstacle approaches from far to close and goes away again, over [periodMs]. */
object RadarSweep {
    private val FRAMES = listOf(
        listOf(0, 0, 0, 0), listOf(0, 1, 1, 0), listOf(1, 2, 2, 1), listOf(1, 2, 3, 1), listOf(1, 2, 2, 1), listOf(0, 1, 1, 0),
    )
    fun at(tMs: Long, periodMs: Long = 12_000L): List<Int> {
        val step = periodMs / FRAMES.size
        return FRAMES[(((tMs % periodMs) + periodMs) % periodMs / step).toInt().coerceAtMost(FRAMES.size - 1)]
    }
}
