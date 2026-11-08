package dev.kuntal.cockpit.data

import org.junit.Assert.*
import org.junit.Test

/** Hostile package labels and search queries. */
class AppsHostileTest {
    // ---- apps ----------------------------------------------------------------------------------------------------------

    @Test fun hostileAppLabelsAreCleanedForTheGrid() {
        assertEquals(64, AppIndex.cleanLabel("x".repeat(10_000)).length)
        assertEquals("Maps", AppIndex.cleanLabel("Ma\u0000ps\n"))
        assertEquals("com.evil", AppIndex.cleanLabel("", fallback = "com.evil"))
        assertEquals("com.evil", AppIndex.cleanLabel(" \n\t", fallback = "com.evil"))
    }

    @Test fun aHugeSearchQueryIsHarmless() {
        val apps = listOf(AppEntry("Maps", "a", "a.M"), AppEntry("Clock", "b", "b.M"))
        assertEquals(emptyList<AppEntry>(), AppIndex.filter(apps, "q".repeat(200_000)))
        assertEquals(64, AppIndex.clampQuery("q".repeat(200_000)).length)
    }
}
