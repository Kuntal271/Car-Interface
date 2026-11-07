package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsTest {
    @Test fun defaultsAreFordMetricSimulator() {
        val s = Settings()
        assertEquals(ThemeId.Ford, s.theme); assertEquals(SpeedUnit.KMH, s.speedUnit); assertEquals(PressureUnit.BAR, s.pressureUnit)
        assertEquals(TempUnit.C, s.tempUnit); assertEquals(SourceKind.Simulator, s.source); assertEquals(ClockFormat.H24, s.clock)
        assertEquals(6000, s.redlineRpm); assertEquals(7000, s.tachMaxRpm); assertEquals(5200, s.shiftRpm); assertEquals(200, s.speedScaleKmh)
        assertEquals(false, s.perfOverlay)       // the diagnostic overlay is opt-in
    }

    @Test fun roundTripThroughTheStoredMap() {
        val s = Settings(
            theme = ThemeId.Midnight, clock = ClockFormat.H12, motion = MotionMode.Reduced, speedUnit = SpeedUnit.MPH,
            pressureUnit = PressureUnit.PSI, tempUnit = TempUnit.F, source = SourceKind.ObdBluetooth,
            tcpHost = "10.0.2.2", tcpPort = 35001, btAddress = "AA:BB:CC:DD:EE:FF", btName = "OBDII",
            redlineRpm = 6200, tachMaxRpm = 8000, shiftRpm = 5500, speedScaleKmh = 240, perfOverlay = true,
        )
        assertEquals(s, Settings.fromMap(s.toMap()))
    }

    @Test fun unknownOrBrokenValuesFallBackToDefaults() {
        val s = Settings.fromMap(mapOf("theme" to "purple", "speed" to "furlongs", "redline" to "abc", "tcpPort" to "-4", "source" to "42"))
        assertEquals(Settings(), s)
    }

    @Test fun missingKeysUseDefaults() = assertEquals(Settings(), Settings.fromMap(emptyMap()))

    @Test fun outOfRangeThresholdsAreClamped() {
        val s = Settings.fromMap(mapOf("tach" to "99999", "redline" to "9000", "shift" to "9000", "speedScale" to "5")).sanitized()
        assertEquals(9000, s.tachMaxRpm); assertEquals(9000, s.redlineRpm); assertEquals(9000, s.shiftRpm); assertEquals(120, s.speedScaleKmh)
    }

    @Test fun blankHostFallsBackAndIsTrimmed() {
        assertEquals("192.168.0.10", Settings(tcpHost = "  ").sanitized().tcpHost)
        assertEquals("10.0.2.2", Settings(tcpHost = " 10.0.2.2 ").sanitized().tcpHost)
    }

    @Test fun portMustBeValid() { assertEquals(35000, Settings(tcpPort = 0).sanitized().tcpPort); assertEquals(35000, Settings(tcpPort = 70000).sanitized().tcpPort) }

    @Test fun sourceKeyChangesOnlyWhenTheConnectionWould() {
        val sim = Settings(); val tcp = Settings(source = SourceKind.ObdTcp, tcpHost = "h", tcpPort = 1)
        assertEquals("sim", sim.sourceKey); assertEquals("tcp:h:1", tcp.sourceKey)
        assertEquals(sim.sourceKey, sim.copy(theme = ThemeId.Midnight, speedUnit = SpeedUnit.MPH).sourceKey)      // theme and units never reconnect
        assertNotEquals(tcp.sourceKey, tcp.copy(tcpPort = 2).sourceKey)
        assertEquals("bt:AA", Settings(source = SourceKind.ObdBluetooth, btAddress = "AA").sourceKey)
    }

    @Test fun bluetoothWithoutAnAdapterChosenIsNoSourceNotTheSimulator() {
        // Never show simulated numbers as if they were the car's: no adapter chosen means no data.
        assertEquals("none", Settings(source = SourceKind.ObdBluetooth, btAddress = "").sourceKey)
    }
}

/** Asserts what the owner sees on screen (rounded text), not an internal truncation. */
class ThresholdsTest {
    @Test fun redlineStepsByOneHundred() = assertEquals(6100, Thresholds.redline(Settings(), +1).redlineRpm)
    @Test fun redlineCannotPassTheTachScale() = assertEquals(7000, Thresholds.redline(Settings(redlineRpm = 7000), +1).redlineRpm)
    @Test fun lowerTachScaleDragsTheRedlineAndShiftDown() {
        val s = Thresholds.tachMax(Settings(tachMaxRpm = 6000, redlineRpm = 6000, shiftRpm = 5800), -1)
        assertEquals(5500, s.tachMaxRpm); assertEquals(5500, s.redlineRpm); assertEquals(5500, s.shiftRpm)
    }
    @Test fun tachScaleStepsByFiveHundredWithinBounds() {
        assertEquals(7500, Thresholds.tachMax(Settings(), +1).tachMaxRpm)
        assertEquals(5000, Thresholds.tachMax(Settings(tachMaxRpm = 5000, redlineRpm = 4500, shiftRpm = 4000), -1).tachMaxRpm)
    }
    @Test fun shiftLightStaysBelowTheRedline() {
        assertEquals(6000, Thresholds.shift(Settings(shiftRpm = 6000), +1).shiftRpm)
        assertEquals(5100, Thresholds.shift(Settings(), -1).shiftRpm)
    }
    @Test fun speedScaleInMilesPerHourStepsToTheNextTen() {
        val mph = Settings(speedUnit = SpeedUnit.MPH)                    // 200 km/h shows as 124 mph
        assertEquals("130", Fmt.speed(Thresholds.speedScale(mph, +1).speedScaleKmh.toDouble(), SpeedUnit.MPH))
        assertEquals("120", Fmt.speed(Thresholds.speedScale(mph, -1).speedScaleKmh.toDouble(), SpeedUnit.MPH))
        val up = Thresholds.speedScale(mph, +1)
        assertEquals("140", Fmt.speed(Thresholds.speedScale(up, +1).speedScaleKmh.toDouble(), SpeedUnit.MPH))   // and again, no sticking
        assertEquals("120", Fmt.speed(Thresholds.speedScale(up, -1).speedScaleKmh.toDouble(), SpeedUnit.MPH))
    }
    @Test fun speedScaleStepsByTenWithinBounds() {
        assertEquals(210, Thresholds.speedScale(Settings(), +1).speedScaleKmh)
        assertEquals(120, Thresholds.speedScale(Settings(speedScaleKmh = 120), -1).speedScaleKmh)
        assertEquals(300, Thresholds.speedScale(Settings(speedScaleKmh = 300), +1).speedScaleKmh)
    }
}
