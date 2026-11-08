package dev.kuntal.cockpit.data

/*
 * Numbers the Cruise and Boost faces derive from the OBD readings. Everything here is a pure function or a tiny state machine,
 * so it is unit-tested; none of it is mock data. What it estimates is said so on screen.
 */

/** Fuel use by the speed-density method (this car has no fuel-rate PID): air mass from MAP, rpm and intake temperature, fuel at stoich. */
object FuelEstimate {
    const val DISPLACEMENT_M3 = 0.000999        // 1.0 EcoBoost
    const val VOLUMETRIC_EFFICIENCY = 0.85      // a flat guess; real VE moves with rpm and boost, so the figure is an estimate
    const val AFR = 14.7
    const val FUEL_G_PER_L = 745.0
    const val TANK_L = 45.0                     // assumed tank size; to become a setting

    /** Litres per hour, or null when a reading is missing or the engine is not running. */
    fun litresPerHour(rpm: Double?, mapKpa: Double?, intakeC: Double?): Double? {
        if (rpm == null || mapKpa == null || intakeC == null || rpm < 300.0) return null
        val airKgPerS = (mapKpa * 1000.0) * DISPLACEMENT_M3 * (rpm / 120.0) * VOLUMETRIC_EFFICIENCY / (287.05 * (intakeC + 273.15))
        val fuelGPerS = airKgPerS * 1000.0 / AFR
        return fuelGPerS * 3600.0 / FUEL_G_PER_L
    }

    /** Range from the fuel level and the average use; null while either is unknown. */
    fun rangeKm(fuelPct: Double?, avgLPer100: Double?): Double? =
        if (fuelPct == null || avgLPer100 == null || avgLPer100 <= 0.0) null else fuelPct / 100.0 * TANK_L / avgLPer100 * 100.0

    /** Integrates litres and kilometres over time; the average is only offered after half a kilometre. */
    class Meter(private var litres: Double = 0.0, private var km: Double = 0.0) {
        private var lastMs: Long? = null
        fun reset() { litres = 0.0; km = 0.0; lastMs = null }
        fun add(tsMs: Long, speedKmh: Double?, lPerHour: Double?) {
            val prev = lastMs; lastMs = tsMs
            if (prev == null || speedKmh == null) return
            val dtH = ((tsMs - prev).coerceIn(0, 5_000)) / 3_600_000.0
            km += speedKmh * dtH
            if (lPerHour != null) litres += lPerHour * dtH
        }
        val distanceKm get() = km
        fun avgLPer100(): Double? = if (km >= 0.5) litres / km * 100.0 else null
    }
}

/**
 * A 0-100 score for how gently the car is driven: the mean change of acceleration (jerk) over the last window of one-second
 * samples. 100 is perfectly smooth; harsh stop-start driving scores in the 40s.
 */
class Smoothness(private val window: Int = 60) {
    private val speeds = ArrayDeque<Double>()
    fun add(speedKmh: Double?) { if (speedKmh == null) return; speeds.addLast(speedKmh); while (speeds.size > window + 2) speeds.removeFirst() }
    fun score(): Int? {
        if (speeds.size < 12) return null
        val v = speeds.map { it / 3.6 }                         // m/s, one sample per second
        val a = v.zipWithNext { x, y -> y - x }                 // m/s^2
        val jerk = a.zipWithNext { x, y -> Math.abs(y - x) }.average()
        return Math.round(100.0 - 30.0 * jerk).toInt().coerceIn(0, 100)
    }
}

/** Times a 0-100 km/h run: armed at a standstill, running from the first movement, finished at 100 (crossing interpolated). */
class ZeroTo100(startArmed: Boolean = false) {
    enum class Phase { Waiting, Armed, Running }
    var phase = if (startArmed) Phase.Armed else Phase.Waiting; private set
    private var startMs = 0L; private var lastMs = 0L; private var lastKmh = 0.0

    /** Returns the run time in seconds when this sample completes a run. */
    fun accept(tsMs: Long, kmh: Double): Double? {
        var done: Double? = null
        when (phase) {
            Phase.Waiting -> if (kmh < 1.0) phase = Phase.Armed
            Phase.Armed -> if (kmh >= 1.0) { phase = Phase.Running; startMs = if (lastMs > 0) lastMs else tsMs }
            Phase.Running -> when {
                kmh >= 100.0 -> {
                    val f = if (kmh > lastKmh) (100.0 - lastKmh) / (kmh - lastKmh) else 1.0
                    done = (lastMs + (tsMs - lastMs) * f - startMs) / 1000.0
                    phase = Phase.Waiting
                }
                kmh < 1.0 -> phase = Phase.Armed
            }
        }
        lastMs = tsMs; lastKmh = kmh
        return done
    }
    fun elapsedSec(nowMs: Long): Double? = if (phase == Phase.Running) (nowMs - startMs) / 1000.0 else null
}

/** One finished 0-100 run; [daysAgo] 0 is today. */
data class Run(val seconds: Double, val daysAgo: Int = 0)
