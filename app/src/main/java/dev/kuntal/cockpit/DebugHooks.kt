package dev.kuntal.cockpit

import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.TripState
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.vehicle.VehicleSample

/**
 * What a launch intent may ask for. MainActivity is exported (it has to be: it is the HOME activity), so any app on the unit can start it
 * with any extras. The hooks that change persisted settings (`set`, `reset`) or freeze the UI on made-up "Connected" numbers (`snapshot`)
 * are therefore honoured only in a debuggable build; a release build ignores them. `perf` only turns on the frame-time overlay and log
 * (no state, no data) and stays available because it is how a release build is measured on the unit. `screen` / `page` are
 * enum-validated navigation in MainActivity. Single entry point: [parse], called once from MainActivity.onCreate.
 */
data class DevLaunch(val snapshot: Boolean, val perf: Boolean, val reset: Boolean, val set: Map<String, String>) {
    companion object {
        val NONE = DevLaunch(snapshot = false, perf = false, reset = false, set = emptyMap())
        private const val MAX_PAIRS = 32
        private const val MAX_RAW = 4_096

        /** [bool] / [str] read an extra; anything that throws (a malformed Bundle from another app) counts as "no extras". */
        fun parse(debuggable: Boolean, bool: (String) -> Boolean, str: (String) -> String?): DevLaunch = try {
            DevLaunch(
                snapshot = debuggable && bool("snapshot"),
                perf = bool("perf"),
                reset = debuggable && bool("reset"),
                set = if (debuggable) parseSet(str("set")) else emptyMap(),
            )
        } catch (_: Exception) { NONE }

        /** "theme=Midnight,source=ObdTcp" -> map; values keep any further '='; bounded in size and count. Keys are validated later by Settings.fromMap. */
        fun parseSet(raw: String?): Map<String, String> {
            if (raw == null || raw.length > MAX_RAW) return emptyMap()
            return raw.split(',').asSequence()
                .mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 && p[0].isNotBlank() }?.let { p -> p[0].trim() to p[1].trim() } }
                .take(MAX_PAIRS).toMap()
        }
    }
}

/**
 * Snapshot mode: `am start -n dev.kuntal.cockpit/.MainActivity --es screen drive --ez snapshot true` freezes the app on the exact
 * values of the design mock-ups (and a fixed clock), so a screenshot can be diffed pixel-for-pixel against the design render.
 * Inert unless that extra is passed.
 */
object DebugHooks {
    @Volatile var snapshot = false
    @Volatile var perf = false          // --ez perf true: log frame statistics to logcat (tag CockpitPerf) and show the overlay

    const val CLOCK = "21:47"
    const val DATE = "Fri 30 Oct"
    const val VOLUME = 18

    /** The sim at t = 25.6 s as the design shows it: 74 km/h, 5,029 rpm, +0.95 bar, coolant 92, intake 44, throttle 78, load 88, fuel 62. */
    val driveState = VehicleUiState(
        sample = VehicleSample(
            tsMs = 25_600, rpm = 5029.0, speedKmh = 73.96,
            coolantC = 92.0, intakeC = 44.0, ambientC = 31.0, baroKpa = 100.0, mapKpa = 195.0,
            throttlePct = 78.0, loadPct = 88.0, fuelPct = 61.7, batteryV = 14.2,
        ),
        link = Link.Connected,
        trip = TripState(distanceKm = 42.8, avgKmh = 38.0, topKmh = 131.0, zeroTo100Best = 12.3, peakBoostKpa = 104.0),
        gear = 3,
    )
}
