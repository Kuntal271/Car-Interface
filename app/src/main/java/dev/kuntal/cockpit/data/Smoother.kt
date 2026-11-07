package dev.kuntal.cockpit.data

import kotlin.math.abs
import kotlin.math.exp

/**
 * Frame-rate independent exponential smoothing. One call per frame with that frame's dt;
 * two half steps equal one full step, so 30 fps and 60 fps look the same.
 */
object Smoother {
    private const val SNAP = 1e-3f

    fun step(current: Float, target: Float, dtSec: Float, tauSec: Float): Float {
        if (dtSec <= 0f) return current
        val next = current + (target - current) * (1f - exp(-dtSec / tauSec))
        return if (abs(target - next) < SNAP) target else next
    }
}
