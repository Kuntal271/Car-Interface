package dev.kuntal.cockpit.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** A launchable app, free of Android types so the search and ordering logic is plain JVM-testable. */
data class AppEntry(val label: String, val pkg: String, val activity: String)

object AppIndex {
    private val collator: Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }       // a = A = a-acute for ordering
    private val order = Comparator<AppEntry> { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg) }

    /** Lower case, accents stripped: "eclair" finds "Éclair". */
    private fun key(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    const val MAX_LABEL = 64
    const val MAX_QUERY = 64

    /** What the grid may show for an app name: no control characters, at most [MAX_LABEL] characters, the package name when nothing is left. */
    fun cleanLabel(raw: String, fallback: String = ""): String =
        raw.filterNot { it.isISOControl() }.trim().take(MAX_LABEL).ifBlank { fallback.take(MAX_LABEL) }

    fun clampQuery(q: String): String = q.take(MAX_QUERY)

    /** Everything except this launcher, sorted by label (case-insensitive), then package. */
    fun visible(apps: List<AppEntry>, own: String): List<AppEntry> =
        apps.filter { it.pkg != own }.sortedWith(order)

    /** Case-insensitive substring search. Labels that start with the query come first, then the rest; each group alphabetical. */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = key(clampQuery(query).trim())
        val sorted = apps.sortedWith(order)
        if (q.isEmpty()) return sorted
        val hits = sorted.filter { key(it.label).contains(q) }
        return hits.filter { key(it.label).startsWith(q) } + hits.filterNot { key(it.label).startsWith(q) }
    }
}

object RecentApps {
    const val MAX = 6
    fun touch(list: List<String>, pkg: String): List<String> = (listOf(pkg) + list.filterNot { it == pkg }).take(MAX)
    fun serialise(list: List<String>): String = list.joinToString(",")
    fun parse(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)
}
