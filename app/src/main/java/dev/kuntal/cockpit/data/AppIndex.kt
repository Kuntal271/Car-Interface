package dev.kuntal.cockpit.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** A launchable app, free of Android types so the search and ordering logic is plain JVM-testable. */
data class AppEntry(val label: String, val pkg: String, val activity: String)

object AppIndex {
    private val collator: Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }       // a = A = a-acute for ordering
    private val order = Comparator<AppEntry> { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg) }

    private val MARKS = Regex("\\p{M}+")          // compiled once: it used to be rebuilt for every label on every keystroke

    /** Lower case, accents stripped: "eclair" finds "Éclair". */
    private fun key(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)

    const val MAX_LABEL = 64
    const val MAX_QUERY = 64

    /** What the grid may show for an app name: no control characters, at most [MAX_LABEL] characters, the package name when nothing is left. */
    fun cleanLabel(raw: String, fallback: String = ""): String =
        raw.filterNot { it.isISOControl() }.trim().take(MAX_LABEL).ifBlank { fallback.take(MAX_LABEL) }

    fun clampQuery(q: String): String = q.take(MAX_QUERY)

    /** Everything except this launcher, sorted by label (case-insensitive), then package. */
    fun visible(apps: List<AppEntry>, own: String): List<AppEntry> =
        apps.filter { it.pkg != own }.sortedWith(order)

    /**
     * An app list prepared for searching: sorted once, search keys computed once. Build it when the list changes (remember(apps)),
     * then call [filter] per keystroke, which is a plain substring scan over precomputed strings (no collator, no Normalizer).
     */
    class Search(apps: List<AppEntry>) {
        private val sorted = apps.sortedWith(order)
        private val keys = sorted.map { key(it.label) }

        /** Case-insensitive substring search. Labels that start with the query come first, then the rest; each group alphabetical. */
        fun filter(query: String): List<AppEntry> {
            val q = key(clampQuery(query).trim())
            if (q.isEmpty()) return sorted
            val starts = ArrayList<AppEntry>(); val inside = ArrayList<AppEntry>()
            for (i in sorted.indices) {
                val at = keys[i].indexOf(q)
                if (at == 0) starts += sorted[i] else if (at > 0) inside += sorted[i]
            }
            return starts + inside
        }
    }

    /** One-shot form of [Search]; prefer [Search] when the same list is searched repeatedly. */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> = Search(apps).filter(query)
}

object RecentApps {
    const val MAX = 6
    fun touch(list: List<String>, pkg: String): List<String> = (listOf(pkg) + list.filterNot { it == pkg }).take(MAX)
    fun serialise(list: List<String>): String = list.joinToString(",")
    fun parse(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)
}
