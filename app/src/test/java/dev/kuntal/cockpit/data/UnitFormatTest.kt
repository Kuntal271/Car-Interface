package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitFormatTest {
    @Test fun speedKmhWhole() = assertEquals("74", Fmt.speed(73.96, SpeedUnit.KMH))
    @Test fun speedMphWhole() = assertEquals("46", Fmt.speed(73.96, SpeedUnit.MPH))
    @Test fun speedMissingIsDash() = assertEquals(Fmt.DASH, Fmt.speed(null, SpeedUnit.MPH))

    @Test fun distanceOneDecimal() { assertEquals("42.8", Fmt.distance(42.8, SpeedUnit.KMH)); assertEquals("26.6", Fmt.distance(42.8, SpeedUnit.MPH)) }

    @Test fun boostBarTwoDecimalsSigned() { assertEquals("+0.95", Fmt.boost(95.0, PressureUnit.BAR)); assertEquals("\u22120.62", Fmt.boost(-62.0, PressureUnit.BAR)) }
    @Test fun boostPsiOneDecimalSigned() { assertEquals("+13.8", Fmt.boost(95.0, PressureUnit.PSI)); assertEquals("\u22129.0", Fmt.boost(-62.0, PressureUnit.PSI)) }
    @Test fun boostKpaWholeSigned() = assertEquals("+95", Fmt.boost(95.0, PressureUnit.KPA))
    @Test fun boostZeroHasNoSign() = assertEquals("0.00", Fmt.boost(0.0, PressureUnit.BAR))

    @Test fun temperatureWhole() { assertEquals("92", Fmt.temp(92.0, TempUnit.C)); assertEquals("198", Fmt.temp(92.0, TempUnit.F)); assertEquals(Fmt.DASH, Fmt.temp(null, TempUnit.F)) }

    @Test fun scaleLabelsFollowTheUnit() {
        assertEquals("\u22121.0", Fmt.boostTick(-100.0, PressureUnit.BAR))
        assertEquals("+0.5", Fmt.boostTick(50.0, PressureUnit.BAR))
        assertEquals("\u221214.5", Fmt.boostTick(-100.0, PressureUnit.PSI))
        assertEquals("+7.3", Fmt.boostTick(50.0, PressureUnit.PSI))
        assertEquals("\u2212100", Fmt.boostTick(-100.0, PressureUnit.KPA))
        assertEquals("0", Fmt.boostTick(0.0, PressureUnit.BAR))
    }

    @Test fun clock24And12() {
        assertEquals("21:07", Fmt.clock(21, 7, ClockFormat.H24)); assertEquals("9:07", Fmt.clock(21, 7, ClockFormat.H12))
        assertEquals("12:00", Fmt.clock(12, 0, ClockFormat.H12)); assertEquals("12:30", Fmt.clock(0, 30, ClockFormat.H12))
    }
}
