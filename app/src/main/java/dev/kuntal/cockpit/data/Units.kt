package dev.kuntal.cockpit.data

enum class SpeedUnit { KMH, MPH }
enum class PressureUnit { BAR, PSI, KPA }
enum class TempUnit { C, F }

/** Display conversions. The vehicle layer always speaks km/h, kPa and °C; units are a display concern only. */
object Units {
    private const val KM_PER_MILE = 1.609344
    private const val PSI_PER_KPA = 0.145037738

    fun speed(kmh: Double, u: SpeedUnit) = if (u == SpeedUnit.MPH) kmh / KM_PER_MILE else kmh
    fun distance(km: Double, u: SpeedUnit) = if (u == SpeedUnit.MPH) km / KM_PER_MILE else km

    fun boost(kpa: Double, u: PressureUnit) = when (u) {
        PressureUnit.BAR -> kpa / 100.0
        PressureUnit.PSI -> kpa * PSI_PER_KPA
        PressureUnit.KPA -> kpa
    }

    fun temp(c: Double, u: TempUnit) = if (u == TempUnit.F) c * 9.0 / 5.0 + 32.0 else c

    fun label(u: SpeedUnit) = if (u == SpeedUnit.MPH) "mph" else "km/h"
    fun label(u: PressureUnit) = when (u) { PressureUnit.BAR -> "bar"; PressureUnit.PSI -> "psi"; PressureUnit.KPA -> "kPa" }
    fun label(u: TempUnit) = if (u == TempUnit.F) "°F" else "°C"
}
