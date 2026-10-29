package dev.kuntal.cockpit.vehicle

/** Tiered polling: rpm+speed every cycle, load group every 3rd, slow group every 10th. */
class PollSchedule(private val supported: Set<Pid>) {
    fun pidsForCycle(n: Int): List<Pid> = buildList {
        addAll(FAST.filter { it in supported })
        if (n % 3 == 0) addAll(MEDIUM.filter { it in supported })
        if (n % 10 == 0) addAll(SLOW.filter { it in supported })
    }

    companion object {
        val FAST = listOf(Pid.RPM, Pid.SPEED)
        val MEDIUM = listOf(Pid.MAP, Pid.THROTTLE, Pid.ENGINE_LOAD)
        val SLOW = listOf(
            Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.FUEL_LEVEL, Pid.MODULE_VOLTAGE,
            Pid.BARO, Pid.AMBIENT_TEMP, Pid.OIL_TEMP,
        )
    }
}
