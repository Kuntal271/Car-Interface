package dev.kuntal.cockpit.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A launcher on a head unit may list 300 apps. Parity of the prepared [AppIndex.Search] with the original per-call implementation
 * (kept here as the reference) over many random queries, plus a timing printout (`gradlew :app:testDebugUnitTest -i` shows it).
 */
class AppIndexScaleTest {
    private val rnd = Random(42)
    private val words = listOf("Maps", "camera", "Dash cam", "Calculator", "Bluetooth", "Éclair", "Clock", "Música", "Zürich", "Radio FM", "Files", "Settings", "Navi", "Café", "Naïve", "Ångström")
    private fun synthetic(n: Int) = (0 until n).map { i ->
        val label = words[rnd.nextInt(words.size)] + if (rnd.nextInt(3) == 0) " ${rnd.nextInt(99)}" else ""
        AppEntry(label, "com.vendor${i % 40}.app$i", "com.vendor${i % 40}.app$i.Main")
    }

    // ---- the implementation before the change, verbatim ----
    private val collator: Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
    private val order = Comparator<AppEntry> { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg) }
    private fun oldKey(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    private fun oldFilter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = oldKey(query.trim())
        val sorted = apps.sortedWith(order)
        if (q.isEmpty()) return sorted
        val hits = sorted.filter { oldKey(it.label).contains(q) }
        return hits.filter { oldKey(it.label).startsWith(q) } + hits.filterNot { oldKey(it.label).startsWith(q) }
    }

    @Test fun preparedSearchMatchesTheOriginalForManyQueries() {
        val apps = synthetic(300)
        val index = AppIndex.Search(apps)
        val queries = listOf("", " ", "a", "A", "ca", "cam", "CAM ", "eclair", "ÉCL", "cafe", "naive", "angstrom", "zurich", "musica", "9", "12", "radio f", "zzz", "é", " c ")
        for (q in queries) assertEquals("query '$q'", oldFilter(apps, q), index.filter(q))
        repeat(300) {                                           // random prefixes/infixes of real labels
            val l = apps[rnd.nextInt(apps.size)].label
            val a = rnd.nextInt(l.length); val b = a + 1 + rnd.nextInt(l.length - a)
            val q = l.substring(a, b)
            assertEquals("query '$q'", oldFilter(apps, q), index.filter(q))
        }
    }

    @Test fun threeHundredAppsTiming() {
        val apps = synthetic(300)
        fun ms(block: () -> Unit): Double { val t = System.nanoTime(); block(); return (System.nanoTime() - t) / 1e6 }
        repeat(20) { oldFilter(apps, "ca"); AppIndex.Search(apps).filter("ca") }               // warm the JIT
        val oldMs = (0 until 50).map { ms { oldFilter(apps, "ca") } }.sorted()
        val buildMs = (0 until 50).map { ms { AppIndex.Search(apps) } }.sorted()
        val index = AppIndex.Search(apps)
        val newMs = (0 until 50).map { ms { index.filter("ca") } }.sorted()
        println("AppIndex 300 apps, JVM: per keystroke old median %.3f ms (max %.3f) | prepared filter median %.3f ms (max %.3f) | one-off build median %.3f ms".format(
            oldMs[25], oldMs[49], newMs[25], newMs[49], buildMs[25]))
        assertTrue("prepared filter must not be slower than the original", newMs[25] <= oldMs[25])
    }
}
