package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample

data class TripState(
    val distanceKm: Double = 0.0,
    val avgKmh: Double = 0.0,
    val topKmh: Double = 0.0,
    val zeroTo100Best: Double? = null,
    val peakBoostKpa: Double? = null,
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
    private var peakBoost: Double? = null

    private companion object {
        const val MAX_KMH_PER_SEC = 60.0      // about 1.7 g: harder than any braking or launch, so a bigger step is a bad reading
        const val MAX_GAP_MS = 5_000L         // longer than this the adapter was gone (or the app was paused)
        const val MIN_ZERO_TO_100_S = 3.0     // no road car does it faster
    }

    fun accept(s: VehicleSample) {
        s.boostKpa?.let { b -> if (peakBoost.let { p -> p == null || b > p }) peakBoost = b }
        val v = s.speedKmh ?: return
        val t = s.tsMs
        if (firstT == null) firstT = t
        var prev = lastT
        if (prev != null) {
            val dt = t - prev
            if (dt <= 0) return                                                   // out-of-order or duplicate sample
            if (dt > MAX_GAP_MS) { prev = null; runStartMs = null }                // data was gone: nothing to integrate, a run in flight is void
            else if (Math.abs(v - lastKmh) / (dt / 1000.0) > MAX_KMH_PER_SEC) return   // no car does that: a glitched reading, drop it
        }
        if (prev != null) {
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
                if (dur >= MIN_ZERO_TO_100_S && (best == null || dur < best!!)) best = dur
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
        peakBoostKpa = peakBoost,
    )

    fun reset() {
        lastT = null; lastKmh = 0.0; distanceKm = 0.0; firstT = null; elapsedMs = 0; topKmh = 0.0; best = null; runStartMs = null; peakBoost = null
    }
}
