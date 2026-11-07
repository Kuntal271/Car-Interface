package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample

data class TripState(
    val distanceKm: Double = 0.0,
    val avgKmh: Double = 0.0,
    val topKmh: Double = 0.0,
    val zeroTo100Best: Double? = null,
)

/**
 * Trip counters from the speed samples alone: distance by integration, top speed, average over elapsed time,
 * and the best 0-100 km/h run. A run is only timed from a true standstill (rolling starts do not count).
 */
class TripTracker {
    private var lastT: Long? = null
    private var lastKmh = 0.0
    private var distanceKm = 0.0
    private var firstT: Long? = null
    private var elapsedMs = 0L
    private var topKmh = 0.0
    private var best: Double? = null
    private var runStartMs: Long? = null

    fun accept(s: VehicleSample) {
        val v = s.speedKmh ?: return
        val t = s.tsMs
        if (firstT == null) firstT = t
        val prev = lastT
        if (prev != null && t > prev) {
            val dt = t - prev
            distanceKm += (v + lastKmh) / 2.0 * dt / 3_600_000.0
            elapsedMs += dt
        }
        if (v > topKmh) topKmh = v

        // 0-100: arm at standstill, time until the first sample at or above 100 (crossing interpolated).
        if (v < 1.0) runStartMs = t
        else if (runStartMs != null) {
            if (v >= 100.0) {
                val crossT = if (prev != null && v > lastKmh) prev + ((100.0 - lastKmh) / (v - lastKmh) * (t - prev)).toLong() else t
                val dur = (crossT - runStartMs!!) / 1000.0
                if (best == null || dur < best!!) best = dur
                runStartMs = null
            }
        }
        lastT = t; lastKmh = v
    }

    fun snapshot() = TripState(
        distanceKm = distanceKm,
        avgKmh = if (elapsedMs > 0) distanceKm / (elapsedMs / 3_600_000.0) else 0.0,
        topKmh = topKmh,
        zeroTo100Best = best,
    )

    fun reset() {
        lastT = null; lastKmh = 0.0; distanceKm = 0.0; firstT = null; elapsedMs = 0; topKmh = 0.0; best = null; runStartMs = null
    }
}
