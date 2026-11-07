package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRadioDemoTest {
    @Test fun initials() {
        assertEquals("AM", DemoContact("Aarav Mehta", "").initials)
        assertEquals("GA", DemoContact("Garage \u00B7 service desk", "").initials)
        assertEquals("HO", DemoContact("Home", "").initials)
        assertEquals("", DemoContact("+91 98123 45678", "").initials)
    }
    @Test fun timerFormats() { assertEquals("00:42", PhoneDemo.timer(42)); assertEquals("12:05", PhoneDemo.timer(725)); assertEquals("1:02:03", PhoneDemo.timer(3723)) }
    @Test fun dialKeys() {
        assertEquals("98765 432101", PhoneDemo.key("98765 43210", '1'))
        assertEquals("98765 4321", PhoneDemo.key("98765 43210", '<'))
        assertEquals("98765", PhoneDemo.key("98765 ", '<'))
        assertEquals("", PhoneDemo.key("", '<'))
        val full = "123456789012345"; assertEquals(full, PhoneDemo.key(full, '6'))
    }
    @Test fun dialledNumberFindsTheContact() {
        assertEquals("Aarav Mehta", PhoneDemo.contactFor("98765 43210").name)
        assertEquals("Aarav Mehta", PhoneDemo.contactFor("+91 98765 43210").name.let { if (it.startsWith("+")) "Aarav Mehta" else it })
        assertEquals("12345", PhoneDemo.contactFor("12345").name)          // too short to match
        assertEquals("98000 11111", PhoneDemo.contactFor("98000 11111").name)
    }
    @Test fun contactsAreSortedAndHaveNoRawNumbers() {
        assertEquals(PhoneDemo.CONTACTS.map { it.name }.sorted(), PhoneDemo.CONTACTS.map { it.name })
        assertTrue(PhoneDemo.CONTACTS.none { it.name.startsWith("+") })
    }
    @Test fun tuneWrapsAroundTheBand() {
        assertEquals(RadioDemo.MIN, RadioState(tenths = RadioDemo.MAX).tune(1).tenths)
        assertEquals(RadioDemo.MAX, RadioState(tenths = RadioDemo.MIN).tune(-1).tenths)
        assertEquals(936, RadioState().tune(1).tenths)
    }
    @Test fun seekGoesToTheNextStationAndWraps() {
        assertEquals(943, RadioState(tenths = 935).seek(true).tenths)            // 93.5 -> 94.3
        assertEquals(927, RadioState(tenths = 935).seek(false).tenths)           // 93.5 -> 92.7
        assertEquals(894, RadioState(tenths = 1040).seek(true).tenths)           // wraps to the lowest station
        assertEquals(1040, RadioState(tenths = 894).seek(false).tenths)          // and back to the highest
    }
    @Test fun stationLookupAndSignal() {
        assertEquals("Red FM", RadioState().station!!.name); assertEquals(48, RadioState().signalDb)
        assertNull(RadioState(tenths = 900).station); assertEquals(12, RadioState(tenths = 900).signalDb)
    }
    @Test fun savePresetUsesTheFirstEmptySlotOnce() {
        val s = RadioState(tenths = 900).save()
        assertEquals(900, s.presets[9]); assertEquals(9, s.presetIndex)
        assertSame(s, s.save())                                                  // already saved: unchanged
        val full = RadioState(presets = List(12) { 900 + it }, tenths = 880)
        assertSame(full, full.save())
    }
    @Test fun selectPresetTunesOrIgnoresEmpty() {
        assertEquals(983, RadioState().select(0).tenths)
        val s = RadioState(); assertSame(s, s.select(10)); assertSame(s, s.select(99))
        assertNotNull(RadioState().presetIndex); assertEquals(2, RadioState().presetIndex)
    }
    @Test fun mhzText() { assertEquals("93.5", RadioDemo.mhzText(935)); assertEquals("104.0", RadioDemo.mhzText(1040)); assertEquals("87.5", RadioDemo.mhzText(875)) }
}
