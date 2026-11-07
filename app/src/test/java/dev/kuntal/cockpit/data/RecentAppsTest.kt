package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {
    @Test fun touchMovesToFront() = assertEquals(listOf("c", "a", "b"), RecentApps.touch(listOf("a", "b", "c"), "c"))
    @Test fun touchAddsNewAtFront() = assertEquals(listOf("x", "a", "b"), RecentApps.touch(listOf("a", "b"), "x"))
    @Test fun touchNeverDuplicates() = assertEquals(listOf("a", "b"), RecentApps.touch(listOf("a", "b"), "a"))
    @Test fun listIsCappedAtSix() = assertEquals(listOf("g", "a", "b", "c", "d", "e"), RecentApps.touch(listOf("a", "b", "c", "d", "e", "f"), "g"))
    @Test fun serialiseRoundTrip() = assertEquals(listOf("a.b", "c.d"), RecentApps.parse(RecentApps.serialise(listOf("a.b", "c.d"))))
    @Test fun parseIgnoresEmptyAndBlank() { assertEquals(emptyList<String>(), RecentApps.parse("")); assertEquals(listOf("a"), RecentApps.parse(",a,, ")) }
    @Test fun parseCapsAtSix() = assertEquals(6, RecentApps.parse("a,b,c,d,e,f,g,h").size)
}
