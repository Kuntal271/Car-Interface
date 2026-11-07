package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIndexTest {
    private fun app(label: String, pkg: String = "p.${label.lowercase().replace(' ', '.')}") = AppEntry(label, pkg, "$pkg.Main")
    private val apps = listOf(app("Maps"), app("camera"), app("Dash cam"), app("Calculator"), app("Bluetooth"), app("Éclair"), app("Clock"))

    @Test fun emptyQueryReturnsEverythingAlphabeticallyIgnoringCase() {
        assertEquals(listOf("Bluetooth", "Calculator", "camera", "Clock", "Dash cam", "Éclair", "Maps"), AppIndex.filter(apps, "").map { it.label })
    }

    @Test fun matchIsCaseInsensitiveAndTrimmed() = assertEquals(listOf("Maps"), AppIndex.filter(apps, "  MAP ").map { it.label })

    @Test fun prefixMatchesComeBeforeSubstringMatches() {
        assertEquals(listOf("Calculator", "camera", "Dash cam"), AppIndex.filter(apps, "ca").map { it.label })
        assertEquals(listOf("camera", "Dash cam"), AppIndex.filter(apps, "cam").map { it.label })
    }

    @Test fun searchIgnoresAccents() { assertEquals(listOf("Éclair"), AppIndex.filter(apps, "eclair").map { it.label }); assertEquals(listOf("Éclair"), AppIndex.filter(apps, "ÉCL").map { it.label }) }

    @Test fun noMatchIsEmpty() = assertEquals(emptyList<AppEntry>(), AppIndex.filter(apps, "zzz"))

    @Test fun duplicateLabelsKeepBothSortedByPackage() {
        val two = listOf(app("Files", "b.files"), app("Files", "a.files"))
        assertEquals(listOf("a.files", "b.files"), AppIndex.filter(two, "").map { it.pkg })
    }

    @Test fun ownPackageIsExcluded() {
        val list = listOf(app("Cockpit", "dev.kuntal.cockpit"), app("Maps"))
        assertEquals(listOf("Maps"), AppIndex.visible(list, own = "dev.kuntal.cockpit").map { it.label })
    }
}
