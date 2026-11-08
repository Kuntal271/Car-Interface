package dev.kuntal.cockpit.vehicle

/** OBD-II Mode 01 PIDs we read. `bytes` = payload length of the reply. */
enum class Pid(val code: Int, val bytes: Int) {
    ENGINE_LOAD(0x04, 1), COOLANT_TEMP(0x05, 1), MAP(0x0B, 1), RPM(0x0C, 2), SPEED(0x0D, 1),
    INTAKE_TEMP(0x0F, 1), THROTTLE(0x11, 1), FUEL_LEVEL(0x2F, 1), BARO(0x33, 1),
    MODULE_VOLTAGE(0x42, 2), AMBIENT_TEMP(0x46, 1), OIL_TEMP(0x5C, 1);

    val request: String get() = "01%02X".format(code)
}

object PidDecoder {
    fun decode(pid: Pid, d: IntArray): Double = when (pid) {
        Pid.RPM -> (d[0] * 256 + d[1]) / 4.0
        Pid.SPEED, Pid.MAP, Pid.BARO -> d[0].toDouble()
        Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.AMBIENT_TEMP, Pid.OIL_TEMP -> d[0] - 40.0
        Pid.ENGINE_LOAD, Pid.THROTTLE, Pid.FUEL_LEVEL -> d[0] * 100.0 / 255.0
        Pid.MODULE_VOLTAGE -> (d[0] * 256 + d[1]) / 1000.0
    }

    /**
     * Values a road car can really report. Outside these the frame was damaged or the adapter is lying (0xFFFF rpm is the
     * "not available" marker), and a number on the dashboard would be presented as a measurement.
     */
    fun plausible(pid: Pid, v: Double): Boolean = v.isFinite() && when (pid) {
        Pid.RPM -> v in 0.0..12_000.0
        Pid.SPEED -> v in 0.0..250.0
        Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.OIL_TEMP -> v in -40.0..160.0
        Pid.AMBIENT_TEMP -> v in -40.0..90.0
        Pid.BARO -> v in 40.0..130.0                                // boost = MAP - BARO: a zero here would show every reading as boost
        Pid.MODULE_VOLTAGE -> v in 0.0..32.0
        Pid.MAP, Pid.ENGINE_LOAD, Pid.THROTTLE, Pid.FUEL_LEVEL -> true
    }

    /** 4-byte "supported PIDs" bitmap for the range starting after [base] (0x00, 0x20, 0x40...). */
    fun supportedFrom(base: Int, bytes: IntArray): Set<Int> {
        val out = mutableSetOf<Int>()
        for (i in 0 until 32) if ((bytes[i / 8] shr (7 - i % 8)) and 1 == 1) out += base + i + 1
        return out
    }
}
