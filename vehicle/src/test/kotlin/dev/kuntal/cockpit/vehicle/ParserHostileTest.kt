package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test

/** Garbage that arrives in valid ELM327 framing must become 'no data', never a number. */
class ParserHostileTest {
    // ---- parser ---------------------------------------------------------------------------------------------------

    @Test fun signedHexIsNotAByte() {                       // "-1".toInt(16) == -1 would give a NEGATIVE speed
        assertNull(ElmParser.parse(Pid.SPEED, "410D-1"))
        assertNull(ElmParser.parse(Pid.RPM, "410C+F+F"))
        assertNull(ElmParser.parse(Pid.COOLANT_TEMP, "4105-F"))
    }

    @Test fun impossibleValuesAreNoDataNotNumbers() {
        assertNull("0xFFFF rpm is the 'not available' marker", ElmParser.parse(Pid.RPM, "41 0C FF FF"))
        assertNull("255 km/h", ElmParser.parse(Pid.SPEED, "41 0D FF"))
        assertNull("65.5 V", ElmParser.parse(Pid.MODULE_VOLTAGE, "41 42 FF FF"))
        assertNull("215 C coolant", ElmParser.parse(Pid.COOLANT_TEMP, "41 05 FF"))
        assertNull("0 kPa barometer would turn every MAP reading into 'boost'", ElmParser.parse(Pid.BARO, "41 33 00"))
    }

    @Test fun realisticExtremesStillPass() {
        assertEquals(7000.0, ElmParser.parse(Pid.RPM, "41 0C 6D 60")!!, 0.01)
        assertEquals(180.0, ElmParser.parse(Pid.SPEED, "41 0D B4")!!, 0.01)
        assertEquals(-40.0, ElmParser.parse(Pid.AMBIENT_TEMP, "41 46 00")!!, 0.01)
        assertEquals(14.2, ElmParser.parse(Pid.MODULE_VOLTAGE, "41 42 37 78")!!, 0.01)
        assertEquals(101.0, ElmParser.parse(Pid.BARO, "41 33 65")!!, 0.01)
    }
}
