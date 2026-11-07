package dev.kuntal.cockpit.data

import java.util.Locale
import kotlin.math.abs

/** Number formatting for the screens. Pure functions, fixed Locale.US so digits and separators never change with the head unit's locale. */
object Fmt {
    const val DASH = "\u2013"
    private const val MINUS = "\u2212"

    fun dec(v: Double, d: Int): String = String.format(Locale.US, "%.${d}f", v)

    /** "+0.95", "\u22120.62", and plain "0.00" when the rounded value is zero. */
    fun signed(v: Double, d: Int): String {
        val body = dec(abs(v), d)
        if (body.toDouble() == 0.0) return body
        return (if (v < 0) MINUS else "+") + body
    }

    fun whole(v: Double?): String = if (v == null) DASH else Math.round(v).toString()
    fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
    fun clock(h: Int, m: Int): String = String.format(Locale.US, "%02d:%02d", h, m)
    fun gear(g: Int?): String = g?.toString() ?: DASH

    // ---- values in the owner's chosen units (the vehicle layer always speaks km/h, kPa and degrees C) ----
    fun speed(kmh: Double?, u: SpeedUnit): String = if (kmh == null) DASH else whole(Units.speed(kmh, u))
    fun distance(km: Double, u: SpeedUnit): String = dec(Units.distance(km, u), 1)
    fun temp(c: Double?, u: TempUnit): String = if (c == null) DASH else whole(Units.temp(c, u))

    /** Signed boost: bar 2 decimals, psi 1, kPa whole. */
    fun boost(kpa: Double, u: PressureUnit): String = signed(Units.boost(kpa, u), if (u == PressureUnit.BAR) 2 else if (u == PressureUnit.PSI) 1 else 0)

    /** Label on the boost bar's scale: "0" at zero, otherwise signed with one decimal (bar, psi) or whole (kPa). */
    fun boostTick(kpa: Double, u: PressureUnit): String = if (kpa == 0.0) "0" else signed(Units.boost(kpa, u), if (u == PressureUnit.KPA) 0 else 1)

    fun clock(h: Int, m: Int, f: ClockFormat): String =
        if (f == ClockFormat.H24) clock(h, m) else String.format(Locale.US, "%d:%02d", if (h % 12 == 0) 12 else h % 12, m)

    /** Position of [v] within [lo]..[hi] as 0..1 (clamped; missing = 0). */
    fun fraction(v: Double?, lo: Double, hi: Double): Double = if (v == null) 0.0 else ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)

    /** Lit segments of an [n]-segment bar for a 0..1 fraction. */
    fun lit(f: Double, n: Int): Int = Math.round(f * n).toInt().coerceIn(0, n)
}
