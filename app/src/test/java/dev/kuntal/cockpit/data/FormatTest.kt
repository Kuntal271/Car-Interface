package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun signedBoostPositive() = assertEquals("+0.95", Fmt.signed(0.95, 2))
    @Test fun signedBoostNegativeUsesMinusSign() = assertEquals("\u22120.62", Fmt.signed(-0.62, 2))
    @Test fun signedZeroHasNoSign() = assertEquals("0.00", Fmt.signed(0.0, 2))
    @Test fun signedTinyNegativeRoundsToZeroWithoutSign() = assertEquals("0.00", Fmt.signed(-0.001, 2))
    @Test fun oneDecimal() = assertEquals("14.2", Fmt.dec(14.2345, 1))
    @Test fun wholeNumberRounds() = assertEquals("93", Fmt.whole(92.5))
    @Test fun wholeNumberNullIsDash() = assertEquals("\u2013", Fmt.whole(null))
    @Test fun thousandsGroupedWithComma() = assertEquals("5,200", Fmt.grouped(5200))
    @Test fun clockPadsMinutes() = assertEquals("07:05", Fmt.clock(7, 5))
    @Test fun gearNullIsDash() = assertEquals("\u2013", Fmt.gear(null))
    @Test fun gearNumber() = assertEquals("3", Fmt.gear(3))
    @Test fun fractionClampsAndScales() {
        assertEquals(0.65, Fmt.fraction(92.0, 40.0, 120.0), 1e-9)
        assertEquals(0.0, Fmt.fraction(10.0, 40.0, 120.0), 1e-9)
        assertEquals(1.0, Fmt.fraction(500.0, 40.0, 120.0), 1e-9)
        assertEquals(0.0, Fmt.fraction(null, 40.0, 120.0), 1e-9)
    }
    @Test fun litSegmentsRoundToNearest() { assertEquals(13, Fmt.lit(0.65, 20)); assertEquals(12, Fmt.lit(0.62, 20)); assertEquals(0, Fmt.lit(0.0, 20)); assertEquals(20, Fmt.lit(1.0, 20)) }
}
