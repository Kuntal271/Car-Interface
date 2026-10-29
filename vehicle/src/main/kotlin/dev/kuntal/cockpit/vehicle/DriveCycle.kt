package dev.kuntal.cockpit.vehicle

/** Deterministic simulated drive (illustrative numbers, not calibrated to the car). 100 s loop. */
object DriveCycle {
    const val PERIOD_S = 100.0
    private val rpmPerKmh = doubleArrayOf(150.0, 80.0, 55.0, 42.0, 33.0, 28.0) // gears 1..6
    private val upshift = doubleArrayOf(30.0, 55.0, 80.0, 105.0, 130.0)

    fun speedAt(tSec: Double): Double {
        val t = ((tSec % PERIOD_S) + PERIOD_S) % PERIOD_S
        return when {
            t < 45 -> 130.0 * t / 45.0
            t < 60 -> 130.0
            t < 85 -> 130.0 * (1 - (t - 60) / 25.0)
            else -> 0.0
        }
    }

    fun at(tMs: Long): VehicleSample {
        val t = tMs / 1000.0
        val v = speedAt(t)
        val gearIdx = upshift.count { v >= it } // 0..5
        val rpm = maxOf(850.0, v * rpmPerKmh[gearIdx])
        val accelerating = speedAt(t + 0.5) - speedAt(t - 0.5) > 0.1
        val baro = 100.0
        val boost = if (accelerating) 60.0 * minOf(1.0, rpm / 3000.0) else -60.0
        return VehicleSample(
            tsMs = tMs, rpm = rpm, speedKmh = v,
            coolantC = minOf(92.0, 45.0 + t * 0.6), intakeC = 38.0, ambientC = 31.0,
            baroKpa = baro, mapKpa = baro + boost,
            throttlePct = if (accelerating) 55.0 else if (v > 0) 8.0 else 0.0,
            loadPct = if (accelerating) 70.0 else 20.0,
            fuelPct = maxOf(5.0, 62.0 - t * 0.01), batteryV = 14.2,
        )
    }
}
