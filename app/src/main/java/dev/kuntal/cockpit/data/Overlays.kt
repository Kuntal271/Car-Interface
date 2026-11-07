package dev.kuntal.cockpit.data

/*
 * System overlays: everything that can interrupt the screen (design: overlays.html). A small pure queue decides what is visible;
 * the host composable only draws it. Lower [priority] wins; priority 1 interrupts everything.
 */

sealed class Overlay(val priority: Int, val ttlMs: Long?, val kind: String) {
    data class Volume(val level: Int, val max: Int, val muted: Boolean = false) : Overlay(5, 1_500, "volume")
    data class ClimateChange(val setC: Double) : Overlay(5, 2_000, "climate")
    data class DoorMoving(val door: String, val kmh: Int) : Overlay(3, null, "door")
    data class CoolantHigh(val tempC: Int) : Overlay(1, null, "coolant")
    data class LowBattery(val volts: Double) : Overlay(2, 30_000, "battery")
    data class LowFuel(val pct: Int, val rangeKm: Int?) : Overlay(3, 8_000, "fuel")
    data class IncomingCall(val name: String, val initials: String) : Overlay(2, null, "call")
    data class ShiftUp(val gear: Int) : Overlay(4, 400, "shift")
    data object Reverse : Overlay(1, 1_500, "reverse")
    data class Notice(val text: String) : Overlay(6, 2_500, "notice")

    /** A modal overlay needs a tap to go away (coolant). */
    val modal: Boolean get() = this is CoolantHigh
}

/** Visible overlay = best priority among those not expired; a new overlay of the same kind replaces the old one. */
class OverlayQueue {
    private class Entry(val o: Overlay, val until: Long?)
    private val items = ArrayList<Entry>()

    fun show(o: Overlay, nowMs: Long) {
        items.removeAll { it.o.kind == o.kind }
        items.add(Entry(o, o.ttlMs?.let { nowMs + it }))
    }
    fun clear(kind: String) { items.removeAll { it.o.kind == kind } }
    fun dismiss(nowMs: Long) { visible(nowMs)?.let { clear(it.kind) } }

    fun visible(nowMs: Long): Overlay? {
        items.removeAll { it.until != null && it.until <= nowMs }
        return items.minByOrNull { it.o.priority }?.o
    }
    fun isEmpty() = items.isEmpty()
    fun nextExpiry(): Long? = items.mapNotNull { it.until }.minOrNull()
}

/** When overlays fire: pure comparisons of the previous and the new reading, so the thresholds are testable. */
object OverlayRules {
    const val COOLANT_HIGH_C = 105
    const val COOLANT_CLEAR_C = 100
    const val BATTERY_LOW_V = 11.8
    const val FUEL_LOW_PCT = 12
    const val MOVING_KMH = 5

    /** Hysteresis: raised at 105, cleared only once back under 100, so it cannot flicker. */
    fun coolantHigh(now: Double?, active: Boolean): Boolean = when {
        now == null -> false
        active -> now > COOLANT_CLEAR_C
        else -> now >= COOLANT_HIGH_C
    }
    fun doorMoving(open: Set<Door>, kmh: Double?): Overlay.DoorMoving? =
        if (open.isNotEmpty() && kmh != null && kmh > MOVING_KMH) Overlay.DoorMoving(open.first().label, Math.round(kmh).toInt()) else null
    fun lowBattery(volts: Double?, rpm: Double?): Boolean = volts != null && volts < BATTERY_LOW_V && (rpm ?: 0.0) < 400.0
    /** Fires once when fuel falls through the limit (not every sample). */
    fun lowFuel(prevPct: Double?, nowPct: Double?): Boolean = nowPct != null && nowPct < FUEL_LOW_PCT && (prevPct == null || prevPct >= FUEL_LOW_PCT)
    fun shiftUp(rpm: Double?, shiftRpm: Int, gear: Int?): Overlay.ShiftUp? = if (rpm != null && rpm >= shiftRpm && gear != null && gear in 1..5) Overlay.ShiftUp(gear + 1) else null
}
