package dev.kuntal.cockpit.data

enum class ThemeId(val label: String) { Ford("Ford"), Midnight("Midnight") }
enum class ClockFormat(val label: String) { H24("24 h"), H12("12 h") }
enum class MotionMode(val label: String) { Full("Full"), Reduced("Reduced") }
enum class SourceKind { Simulator, ObdTcp, ObdBluetooth }

/**
 * Everything the owner can change. Pure data, no Android types: the stored form is a string map ([toMap] / [fromMap]), so the
 * parsing, clamping and "what counts as a reconnect" rules are plain JVM tests.
 */
data class Settings(
    val theme: ThemeId = ThemeId.Ford,
    val clock: ClockFormat = ClockFormat.H24,
    val motion: MotionMode = MotionMode.Full,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val pressureUnit: PressureUnit = PressureUnit.BAR,
    val tempUnit: TempUnit = TempUnit.C,
    val source: SourceKind = SourceKind.Simulator,
    val tcpHost: String = DEFAULT_HOST,
    val tcpPort: Int = DEFAULT_PORT,
    val btAddress: String = "",
    val btName: String = "",
    val redlineRpm: Int = 6000,
    val tachMaxRpm: Int = 7000,
    val shiftRpm: Int = 5200,
    val speedScaleKmh: Int = 200,
    val perfOverlay: Boolean = false,
) {
    /** Changes only when the connection itself would have to change: theme and units never reconnect. */
    val sourceKey: String
        get() = when (source) {
            SourceKind.Simulator -> "sim"
            SourceKind.ObdTcp -> "tcp:${tcpHost.trim()}:$tcpPort"
            SourceKind.ObdBluetooth -> if (btAddress.isBlank()) "none" else "bt:$btAddress"
        }

    fun sanitized(): Settings {
        val tach = tachMaxRpm.coerceIn(5000, 9000)
        val red = redlineRpm.coerceIn(3000, tach)
        val shift = shiftRpm.coerceIn(2000, red)
        return copy(
            tachMaxRpm = tach, redlineRpm = red, shiftRpm = shift,
            speedScaleKmh = speedScaleKmh.coerceIn(120, 300),
            tcpHost = tcpHost.trim().ifEmpty { DEFAULT_HOST },
            tcpPort = if (tcpPort in 1..65535) tcpPort else DEFAULT_PORT,
        )
    }

    fun toMap(): Map<String, String> = mapOf(
        "theme" to theme.name, "clock" to clock.name, "motion" to motion.name,
        "speed" to speedUnit.name, "pressure" to pressureUnit.name, "temp" to tempUnit.name,
        "source" to source.name, "tcpHost" to tcpHost, "tcpPort" to tcpPort.toString(), "bt" to btAddress, "btName" to btName,
        "redline" to redlineRpm.toString(), "tach" to tachMaxRpm.toString(), "shift" to shiftRpm.toString(), "speedScale" to speedScaleKmh.toString(),
        "perfOverlay" to perfOverlay.toString(),
    )

    companion object {
        const val DEFAULT_HOST = "192.168.0.10"      // what most Wi-Fi ELM327 adapters answer on
        const val DEFAULT_PORT = 35000

        private inline fun <reified E : Enum<E>> Map<String, String>.enum(key: String, default: E): E =
            enumValues<E>().firstOrNull { it.name == this[key] } ?: default

        fun fromMap(m: Map<String, String>): Settings {
            val d = Settings()
            return Settings(
                theme = m.enum("theme", d.theme), clock = m.enum("clock", d.clock), motion = m.enum("motion", d.motion),
                speedUnit = m.enum("speed", d.speedUnit), pressureUnit = m.enum("pressure", d.pressureUnit), tempUnit = m.enum("temp", d.tempUnit),
                source = m.enum("source", d.source),
                tcpHost = m["tcpHost"] ?: d.tcpHost, tcpPort = m["tcpPort"]?.toIntOrNull() ?: d.tcpPort,
                btAddress = m["bt"] ?: d.btAddress, btName = m["btName"] ?: d.btName,
                redlineRpm = m["redline"]?.toIntOrNull() ?: d.redlineRpm, tachMaxRpm = m["tach"]?.toIntOrNull() ?: d.tachMaxRpm,
                shiftRpm = m["shift"]?.toIntOrNull() ?: d.shiftRpm, speedScaleKmh = m["speedScale"]?.toIntOrNull() ?: d.speedScaleKmh,
                perfOverlay = m["perfOverlay"]?.toBooleanStrictOrNull() ?: d.perfOverlay,
            ).sanitized()
        }
    }
}

/** One step of a +/- control, always landing on a valid, mutually consistent set (shift <= redline <= tach scale). */
object Thresholds {
    fun redline(s: Settings, dir: Int) = s.copy(redlineRpm = s.redlineRpm + dir * 100).sanitized()
    fun tachMax(s: Settings, dir: Int) = s.copy(tachMaxRpm = s.tachMaxRpm + dir * 500).sanitized()
    fun shift(s: Settings, dir: Int) = s.copy(shiftRpm = s.shiftRpm + dir * 100).sanitized()
    /** Steps in the unit on screen: 10 km/h, or to the next multiple of 10 mph (stored as km/h). */
    fun speedScale(s: Settings, dir: Int): Settings {
        if (s.speedUnit == SpeedUnit.KMH) return s.copy(speedScaleKmh = s.speedScaleKmh + dir * 10).sanitized()
        val shown = Math.round(Units.speed(s.speedScaleKmh.toDouble(), SpeedUnit.MPH)).toInt()
        val next = if (dir > 0) (shown / 10 + 1) * 10 else ((shown + 9) / 10 - 1) * 10
        return s.copy(speedScaleKmh = Math.round(next * 1.609344).toInt()).sanitized()
    }
}
