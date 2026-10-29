package dev.kuntal.cockpit.vehicle

import org.junit.Assert.*
import org.junit.Test

class ElmParserTest {
    @Test fun rpm() = assertEquals(1726.0, ElmParser.parse(Pid.RPM, "41 0C 1A F8")!!, 0.001)
    @Test fun speedNoSpaces() = assertEquals(50.0, ElmParser.parse(Pid.SPEED, "410D32")!!, 0.001)
    @Test fun coolant() = assertEquals(83.0, ElmParser.parse(Pid.COOLANT_TEMP, "41 05 7B")!!, 0.001)
    @Test fun throttle() = assertEquals(50.196, ElmParser.parse(Pid.THROTTLE, "41 11 80")!!, 0.001)
    @Test fun voltage() = assertEquals(13.548, ElmParser.parse(Pid.MODULE_VOLTAGE, "41 42 34 EC")!!, 0.0001)
    @Test fun skipsSearchingBanner() =
        assertEquals(50.0, ElmParser.parse(Pid.SPEED, "SEARCHING...\r41 0D 32\r")!!, 0.001)
    @Test fun noData() = assertNull(ElmParser.parse(Pid.SPEED, "NO DATA"))
    @Test fun wrongPid() = assertNull(ElmParser.parse(Pid.SPEED, "41 0C 1A F8"))
    @Test fun truncated() = assertNull(ElmParser.parse(Pid.RPM, "41 0C 1A"))
    @Test fun supportedBitmap() {
        val bytes = ElmParser.parseBitmap(0x00, "41 00 BE 3F A8 13")!!
        assertEquals(
            setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                  0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20),
            PidDecoder.supportedFrom(0x00, bytes),
        )
    }
    @Test fun boostIsMapMinusBaro() {
        val s = VehicleSample(tsMs = 0, mapKpa = 150.0, baroKpa = 100.0)
        assertEquals(50.0, s.boostKpa!!, 0.001)
    }
}
