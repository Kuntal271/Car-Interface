package dev.kuntal.cockpit.ui

import dev.kuntal.cockpit.data.AudioPresets
import dev.kuntal.cockpit.data.AudioState
import dev.kuntal.cockpit.data.CanboxState
import dev.kuntal.cockpit.data.Overlay
import dev.kuntal.cockpit.data.Climate
import dev.kuntal.cockpit.data.CockpitData
import dev.kuntal.cockpit.data.DemoLibrary
import dev.kuntal.cockpit.data.Door
import dev.kuntal.cockpit.data.Headlights
import dev.kuntal.cockpit.data.Ignition
import dev.kuntal.cockpit.data.MediaKind
import dev.kuntal.cockpit.data.MockAudioSettings
import dev.kuntal.cockpit.data.MockMediaSource
import dev.kuntal.cockpit.data.Radar
import dev.kuntal.cockpit.data.TripStore
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Frozen values of the design's reference state for the screens that are not Drive / Home, used only by snapshot mode
 * (`--es screen car --ez snapshot true`) so a screenshot can be diffed against the design render. Never used in normal runs.
 */
object SnapshotFixtures {
    /** "Fri 30 Oct 21:47", the clock the design shows. */
    val NOW_MS: Long = LocalDateTime.of(2026, 10, 30, 21, 47).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val canbox = CanboxState(
        doorsOpen = setOf(Door.RearLeft), headlights = Headlights.Low, fog = false, handbrake = false, driverBelt = true,
        ignition = Ignition.On, steeringDeg = -4, odometerKm = 48_213, outsideC = 31.0,
        climate = Climate(acOn = true, setC = 22.0, fan = 3, face = true, recirc = true),
        tyresBar = listOf(2.3, 2.3, 2.4, 2.2), radar = Radar(front = listOf(0, 1, 1, 0), rear = listOf(1, 2, 3, 1)), batteryV = 14.2,
    )

    /** The Canbox state each screen's design render shows (Car: rear left door open; Parking: reversing, obstacle centre-left at 0.6 m). */
    fun canboxFor(screen: String): CanboxState = when (screen.lowercase()) {
        "standby" -> canbox.copy(doorsOpen = emptySet(), locked = true)
        "parking" -> canbox.copy(doorsOpen = emptySet(), radar = Radar(front = listOf(0, 0, 0, 0), rear = listOf(1, 3, 2, 1)), reverse = true)
        else -> canbox
    }

    /** The overlay each `overlay1`..`overlay8` snapshot shows (the numbering of the design's overlays page). */
    fun overlayFor(screen: String): Overlay? = when (screen.lowercase()) {
        "overlay1" -> Overlay.Volume(18, 30)
        "overlay2" -> Overlay.ClimateChange(22.5)
        "overlay3" -> Overlay.DoorMoving("Rear left door", 12)
        "overlay4" -> Overlay.CoolantHigh(108)
        "overlay5" -> Overlay.LowBattery(11.6)
        "overlay6" -> Overlay.IncomingCall("Aarav Mehta", "AM")
        "overlay7" -> Overlay.ShiftUp(4)
        "overlay8" -> Overlay.Reverse
        else -> null
    }

    /** Media screen: "Midnight City" 1:32 of 4:03, playing, shuffle on, repeat off. */
    val media = DemoLibrary.state(playing = true, positionSec = 92, kind = MediaKind.Bluetooth).copy(shuffle = true, demo = false)

    fun data(real: TripStore) = CockpitData(
        demo = true, realTrips = real, nowMs = NOW_MS,
        media = MockMediaSource(media),
        audio = MockAudioSettings(AudioState(preset = "Punch", bands = listOf(4, 3, 1, 0, -1, 0, 2, 3), balance = -2, fader = 3, subwooferDb = 4, loudness = true, speedVolume = true)),
    )
}
