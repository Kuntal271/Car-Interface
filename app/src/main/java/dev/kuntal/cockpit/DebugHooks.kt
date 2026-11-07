package dev.kuntal.cockpit

import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.TripState
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.vehicle.VehicleSample

/**
 * Snapshot mode: `am start -n dev.kuntal.cockpit/.MainActivity --es screen drive --ez snapshot true` freezes the app on the exact
 * values of the design mock-ups (and a fixed clock), so a screenshot can be diffed pixel-for-pixel against the design render.
 * Inert unless that extra is passed.
 */
object DebugHooks {
    @Volatile var snapshot = false

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
