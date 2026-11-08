package dev.kuntal.cockpit.data

/** What the Home media card shows. Null = nothing playing. A real source (MediaSession over Bluetooth) is wired later (M5). */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val source: String,
    val elapsedSec: Int,
    val durationSec: Int,
    val playing: Boolean,
) {
    val progress: Double get() = if (durationSec <= 0) 0.0 else (elapsedSec.toDouble() / durationSec).coerceIn(0.0, 1.0)
}

/** Content of one Home shortcut tile (Maps, Radio, Climate). Values come from sources that do not exist yet, so the defaults say so. */
data class Shortcut(val value: String, val unit: String?, val title: String, val subtitle: String, val accent: Boolean = false)

data class HomeContent(
    val media: NowPlaying?,
    val maps: Shortcut,
    val radio: Shortcut,
    val climate: Shortcut,
) {
    companion object {
        /** Real app, before any source is connected: honest placeholders, no invented values. */
        val EMPTY = HomeContent(
            media = null,
            maps = Shortcut("Launch", null, "Maps", "Opens navigation"),
            radio = Shortcut("FM", null, "Radio", "Opens the radio"),
            climate = Shortcut(Fmt.DASH, null, "Climate", "Waiting for Canbox"),
        )

        /**
         * Home as the sources say it: the media card from whatever plays ([media]), the Climate tile from the Canbox ([canbox]).
         * Anything without a source keeps [base]'s honest placeholder, so a missing Canbox still reads "Waiting for Canbox".
         */
        fun live(base: HomeContent, media: NowPlaying?, canbox: CanboxState?, tempUnit: TempUnit = TempUnit.C): HomeContent {
            val c = canbox?.climate ?: return base.copy(media = media ?: base.media)
            val climate = Shortcut(
                value = c.setC?.let { Fmt.dec(Units.temp(it, tempUnit), 1) } ?: Fmt.DASH,
                unit = c.setC?.let { Units.label(tempUnit) },
                title = if (c.acOn) "A/C on" else "A/C off",
                subtitle = "Fan ${c.fan}" + if (c.recirc) " \u00B7 recirculate" else "",
            )
            return base.copy(media = media ?: base.media, climate = climate)
        }

        /** The design mock-up's values, used only by snapshot mode for pixel comparison. */
        val DEMO = HomeContent(
            media = NowPlaying("Midnight City", "M83", "Hurry Up, We're Dreaming", "Bluetooth audio", 92, 243, playing = true),
            maps = Shortcut("Launch", null, "Android Auto", "Connected", accent = true),
            radio = Shortcut("93.5", "MHz", "Red FM", "Preset 3 · stereo"),
            climate = Shortcut("22.0", "\u00B0C", "A/C on", "Fan 3 · recirculate"),
        )
    }
}

object Range {
    /** Provisional: km of range per % of fuel (about 52 L tank at about 8 L/100 km). Replace with the logged average consumption. */
    const val KM_PER_FUEL_PCT = 6.6
    fun km(fuelPct: Double?): Int? = fuelPct?.let { Math.round(it * KM_PER_FUEL_PCT).toInt() }
}
