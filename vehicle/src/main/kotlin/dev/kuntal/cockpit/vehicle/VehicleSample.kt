package dev.kuntal.cockpit.vehicle

/** [tsMs] is milliseconds on a monotonic clock ([MonotonicClock]), only meaningful as a difference: wall time jumps (GPS or network time sync). */
data class VehicleSample(
    val tsMs: Long,
    val rpm: Double? = null, val speedKmh: Double? = null,
    val coolantC: Double? = null, val intakeC: Double? = null, val ambientC: Double? = null, val oilC: Double? = null,
    val mapKpa: Double? = null, val baroKpa: Double? = null,
    val throttlePct: Double? = null, val loadPct: Double? = null, val fuelPct: Double? = null,
    val batteryV: Double? = null,
) {
    /** Gauge boost in kPa (negative = vacuum). Null until both MAP and BARO have been read. */
    val boostKpa: Double? get() = if (mapKpa != null && baroKpa != null) mapKpa - baroKpa else null
}

/** [v] = null clears the field: a value that has stopped being answered must not stay on screen as if it were live. */
fun VehicleSample.with(pid: Pid, v: Double?): VehicleSample = when (pid) {
    Pid.RPM -> copy(rpm = v)
    Pid.SPEED -> copy(speedKmh = v)
    Pid.COOLANT_TEMP -> copy(coolantC = v)
    Pid.INTAKE_TEMP -> copy(intakeC = v)
    Pid.AMBIENT_TEMP -> copy(ambientC = v)
    Pid.OIL_TEMP -> copy(oilC = v)
    Pid.MAP -> copy(mapKpa = v)
    Pid.BARO -> copy(baroKpa = v)
    Pid.THROTTLE -> copy(throttlePct = v)
    Pid.ENGINE_LOAD -> copy(loadPct = v)
    Pid.FUEL_LEVEL -> copy(fuelPct = v)
    Pid.MODULE_VOLTAGE -> copy(batteryV = v)
}

fun VehicleSample.without(pid: Pid): VehicleSample = when (pid) {
    Pid.RPM -> copy(rpm = null)
    Pid.SPEED -> copy(speedKmh = null)
    Pid.COOLANT_TEMP -> copy(coolantC = null)
    Pid.INTAKE_TEMP -> copy(intakeC = null)
    Pid.AMBIENT_TEMP -> copy(ambientC = null)
    Pid.OIL_TEMP -> copy(oilC = null)
    Pid.MAP -> copy(mapKpa = null)
    Pid.BARO -> copy(baroKpa = null)
    Pid.THROTTLE -> copy(throttlePct = null)
    Pid.ENGINE_LOAD -> copy(loadPct = null)
    Pid.FUEL_LEVEL -> copy(fuelPct = null)
    Pid.MODULE_VOLTAGE -> copy(batteryV = null)
}

/** Milliseconds from an arbitrary origin that never steps when the owner or the network sets the clock. */
object MonotonicClock : () -> Long { override fun invoke(): Long = System.nanoTime() / 1_000_000 }
