package dev.kuntal.cockpit.data

/*
 * Demo content for the Phone, Radio and Projection screens. The real phone, radio and projection apps are the unit's vendor apps and
 * stay stock (launched by intent); these in-app screens exist only while Demo data is on, are labelled, and place no real call,
 * tune no real radio and start no real projection.
 */

data class DemoContact(val name: String, val number: String) {
    /** Two letters for the avatar: the first letters of the first two words, or the first two letters of a single word; empty for a bare number. */
    val initials: String get() {
        val head = name.substringBefore('\u00B7').trim()
        if (head.isEmpty() || !head[0].isLetter()) return ""
        val words = head.split(' ').filter { it.isNotBlank() }
        return (if (words.size >= 2) words.take(2).joinToString("") { it.take(1) } else head.take(2)).uppercase()
    }
}

data class DemoCallEntry(val contact: DemoContact, val detail: String, val time: String, val missed: Boolean = false)

object PhoneDemo {
    val RECENTS = listOf(
        DemoCallEntry(DemoContact("Rohan Kapoor", "+91 98111 20001"), "Mobile", "19:42", missed = true),
        DemoCallEntry(DemoContact("Aarav Mehta", "+91 98765 43210"), "Outgoing \u00B7 mobile \u00B7 4 min", "18:05"),
        DemoCallEntry(DemoContact("Home", "+91 11 4000 1234"), "Incoming \u00B7 12 min", "17:30"),
        DemoCallEntry(DemoContact("+91 98123 45678", "+91 98123 45678"), "Incoming \u00B7 1 min", "Yesterday"),
        DemoCallEntry(DemoContact("Sneha Nair", "+91 98222 30003"), "Outgoing \u00B7 7 min", "Yesterday"),
        DemoCallEntry(DemoContact("Garage \u00B7 service desk", "+91 80 4000 5678"), "Incoming \u00B7 3 min", "Wed"),
        DemoCallEntry(DemoContact("Vikram Patel", "+91 98333 40004"), "Mobile", "Wed", missed = true),
    )
    val CONTACTS = RECENTS.map { it.contact }.filter { it.name[0].isLetter() }.distinctBy { it.name }.sortedBy { it.name }
    val SAVED = listOf(DemoContact("Home", "+91 11 4000 1234"), DemoContact("Aarav Mehta", "+91 98765 43210"), DemoContact("Sneha Nair", "+91 98222 30003"))
    const val DIALED = "98765 43210"

    /** The known contact whose number ends with the dialled digits (at least 6), else the bare number. */
    fun contactFor(dialed: String): DemoContact {
        val d = dialed.filter { it.isDigit() }
        return if (d.length >= 6) RECENTS.map { it.contact }.firstOrNull { c -> c.number.filter { it.isDigit() }.endsWith(d) } ?: DemoContact(dialed, dialed) else DemoContact(dialed, dialed)
    }

    /** "00:42" or "1:02:03" from whole seconds. */
    fun timer(sec: Int): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%02d:%02d".format(sec / 60, sec % 60)
    /** A dialled string with the digit added (max 15 digits) or the last digit removed. */
    fun key(dialed: String, k: Char): String = if (k == '<') dialed.dropLast(1).trimEnd() else if (dialed.count { it.isDigit() || it == '+' } >= 15) dialed else dialed + k
}

data class RadioStation(val mhz: Double, val name: String, val now: String)

/** FM tuner state: pure, so seek / tune / save are testable. Frequencies are kept in tenths of MHz to avoid float drift. */
data class RadioState(
    val tenths: Int = 935,
    val presets: List<Int?> = listOf(983, 911, 935, 927, 1040, 943, 950, 1019, 894, null, null, null),
    val stereo: Boolean = true,
) {
    val mhz: Double get() = tenths / 10.0
    val station: RadioStation? get() = RadioDemo.STATIONS.firstOrNull { Math.round(it.mhz * 10).toInt() == tenths }
    val presetIndex: Int? get() = presets.indexOf(tenths).takeIf { it >= 0 }
    /** Signal in dBuV: a known station is strong, empty air is weak. */
    val signalDb: Int get() = if (station != null) 48 else 12
    fun tune(delta: Int) = copy(tenths = (tenths + delta).let { if (it > RadioDemo.MAX) RadioDemo.MIN else if (it < RadioDemo.MIN) RadioDemo.MAX else it })
    /** Next station above (or below) the current frequency, wrapping round the band. */
    fun seek(up: Boolean): RadioState {
        val all = RadioDemo.STATIONS.map { Math.round(it.mhz * 10).toInt() }.sorted()
        val next = if (up) all.firstOrNull { it > tenths } ?: all.first() else all.lastOrNull { it < tenths } ?: all.last()
        return copy(tenths = next)
    }
    fun select(i: Int) = presets.getOrNull(i)?.let { copy(tenths = it) } ?: this
    /** Saves the current frequency in the first empty slot (or does nothing when it is already saved or the list is full). */
    fun save(): RadioState {
        if (presetIndex != null) return this
        val slot = presets.indexOfFirst { it == null }
        return if (slot < 0) this else copy(presets = presets.toMutableList().also { it[slot] = tenths })
    }
}

object RadioDemo {
    const val MIN = 875
    const val MAX = 1080
    val STATIONS = listOf(
        RadioStation(98.3, "Radio Mirchi", "Kesariya \u2014 Arijit Singh"), RadioStation(91.1, "Radio City", "Kal Ho Naa Ho \u2014 Sonu Nigam"),
        RadioStation(93.5, "Red FM", "Tum Hi Ho \u2014 Arijit Singh"), RadioStation(92.7, "Big FM", "Chaiyya Chaiyya \u2014 Sukhwinder Singh"),
        RadioStation(104.0, "Fever", "Senorita \u2014 Farhan Akhtar"), RadioStation(94.3, "Radio One", "Taare \u2014 Ritviz"),
        RadioStation(95.0, "Ishq", "Raabta \u2014 Arijit Singh"), RadioStation(101.9, "Vividh Bharati", "Aap Ki Farmaish"),
        RadioStation(89.4, "", "Station name not sent"),
    )
    fun mhzText(tenths: Int) = "%d.%d".format(tenths / 10, tenths % 10)
}

/** The Projection screen's mock link: which phone is connected and over what. */
data class ProjectionState(val mode: String = "Android Auto", val phone: String = "Pixel 8", val ghz: Int = 5, val mbps: Int = 62, val connected: Boolean = true)
