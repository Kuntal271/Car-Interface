package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun speedKmhIsIdentity() = assertEquals(100.0, Units.speed(100.0, SpeedUnit.KMH), 1e-9)
    @Test fun speedMph() = assertEquals(62.137, Units.speed(100.0, SpeedUnit.MPH), 0.001)
    @Test fun distanceMiles() = assertEquals(26.8, Units.distance(43.13, SpeedUnit.MPH), 0.05)

    @Test fun boostBar() = assertEquals(0.95, Units.boost(95.0, PressureUnit.BAR), 1e-9)
    @Test fun boostKpa() = assertEquals(95.0, Units.boost(95.0, PressureUnit.KPA), 1e-9)
    @Test fun boostPsi() = assertEquals(13.78, Units.boost(95.0, PressureUnit.PSI), 0.01)

    @Test fun tempC() = assertEquals(92.0, Units.temp(92.0, TempUnit.C), 1e-9)
    @Test fun tempF() = assertEquals(197.6, Units.temp(92.0, TempUnit.F), 0.01)

    @Test fun labels() {
        assertEquals("km/h", Units.label(SpeedUnit.KMH)); assertEquals("mph", Units.label(SpeedUnit.MPH))
        assertEquals("bar", Units.label(PressureUnit.BAR)); assertEquals("psi", Units.label(PressureUnit.PSI)); assertEquals("kPa", Units.label(PressureUnit.KPA))
        assertEquals("°C", Units.label(TempUnit.C)); assertEquals("°F", Units.label(TempUnit.F))
    }
}
