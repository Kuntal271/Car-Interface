package dev.kuntal.cockpit.data

/** The three faces of the Drive screen (design: drive, drive-boost, drive-cruise). The tab strip on Boost and Cruise switches between them. */
enum class DriveVariant(val label: String) { Cluster("Cluster"), Boost("Boost"), Cruise("Cruise") }

/** Numbers on the Cruise face that no OBD PID gives us. Mock only (there is no fuel-rate PID); the screen labels it. */
data class CruiseState(val avgLPer100: Double?, val smoothness: Int?, val shiftHintGear: Int?)

interface CruiseSource { fun state(speedKmh: Double?, rpm: Double?, gear: Int?): CruiseState }

class MockCruise : CruiseSource {
    override fun state(speedKmh: Double?, rpm: Double?, gear: Int?): CruiseState =
        CruiseState(avgLPer100 = 6.4, smoothness = 82, shiftHintGear = if ((rpm ?: 0.0) > 4_200 && gear != null && gear in 1..5) gear + 1 else null)
}

/** "Good" / "Fair" / "Rough" for the smoothness score, and the coolant word under the temperature. */
object CruiseText {
    fun smooth(score: Int) = when { score >= 75 -> "Good"; score >= 50 -> "Fair"; else -> "Rough" }
    fun coolant(c: Double?) = when { c == null -> ""; c < 70 -> "Warming up"; c < 100 -> "Normal"; c < 105 -> "Hot"; else -> "Too hot" }
}

/** Last 25 s of boost and rpm for the Boost face: a fixed ring buffer filled at 5 Hz, newest last. Real data only; empty until it fills. */
class History(private val capacity: Int = 125) {
    private val b = DoubleArray(capacity) { Double.NaN }
    private val r = DoubleArray(capacity) { Double.NaN }
    private var n = 0
    fun add(boostBar: Double?, rpm: Double?) { val i = n % capacity; b[i] = boostBar ?: Double.NaN; r[i] = rpm ?: Double.NaN; n++ }
    val size get() = minOf(n, capacity)
    /** Oldest to newest. */
    fun boost(i: Int): Double = b[idx(i)]
    fun rpm(i: Int): Double = r[idx(i)]
    private fun idx(i: Int) = if (n <= capacity) i else (n + i) % capacity
}
