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

    /** Position of [v] within [lo]..[hi] as 0..1 (clamped; missing = 0). */
    fun fraction(v: Double?, lo: Double, hi: Double): Double = if (v == null) 0.0 else ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)

    /** Lit segments of an [n]-segment bar for a 0..1 fraction. */
    fun lit(f: Double, n: Int): Int = Math.round(f * n).toInt().coerceIn(0, n)
}
