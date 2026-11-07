package dev.kuntal.cockpit.data

import kotlin.math.abs
import kotlin.math.ln

/**
 * Gear is not on the OBD bus for this car, so it is inferred from the rpm / speed ratio and always shown as an estimate.
 * RPM_PER_KMH is the simulator's table (rpm per km/h, gears 1..6) until it is calibrated from real logs.
 */
object GearEstimator {
    val RPM_PER_KMH = doubleArrayOf(150.0, 80.0, 55.0, 42.0, 33.0, 28.0)
    private const val MAX_LOG_ERROR = 0.14           // about 15 % off the nearest gear ratio is still that gear

    fun estimate(kmh: Double?, rpm: Double?): Int? {
        if (kmh == null || rpm == null || kmh < 5.0 || rpm < 700.0) return null
        val ratio = rpm / kmh
        var best = -1
        var bestErr = Double.MAX_VALUE
        for (i in RPM_PER_KMH.indices) {
            val err = abs(ln(ratio / RPM_PER_KMH[i]))
            if (err < bestErr) { bestErr = err; best = i }
        }
        return if (bestErr <= MAX_LOG_ERROR) best + 1 else null
    }
}
