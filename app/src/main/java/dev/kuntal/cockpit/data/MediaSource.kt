package dev.kuntal.cockpit.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/*
 * Media: the now-playing model and its controls. The real implementation (a MediaSession listener, plan 3.7) does not exist yet,
 * so [MockMediaSource] stands in behind [MediaSource]; the pure reducer [MediaReducer] holds all the logic and is unit tested.
 */

data class Track(val title: String, val artist: String, val album: String, val durationSec: Int)
enum class RepeatMode { Off, All, One }
enum class MediaKind(val label: String) { Bluetooth("Bluetooth"), Usb("USB"), Radio("Radio"), Streaming("Streaming") }

data class MediaState(
    val kind: MediaKind = MediaKind.Bluetooth,
    val format: String? = null,                  // "FLAC · 44.1 kHz", when the player says
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val positionSec: Int = 0,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val demo: Boolean = false,
) {
    val track: Track? get() = queue.getOrNull(index)
    val remainingSec: Int get() = ((track?.durationSec ?: 0) - positionSec).coerceAtLeast(0)
    val progress: Double get() = track?.let { if (it.durationSec <= 0) 0.0 else (positionSec.toDouble() / it.durationSec).coerceIn(0.0, 1.0) } ?: 0.0
    val nowPlaying: NowPlaying?
        get() = track?.let { NowPlaying(it.title, it.artist, it.album, if (demo) "Demo audio" else kind.label, positionSec, it.durationSec, playing) }
}

interface MediaSource {
    val state: StateFlow<MediaState?>             // null = nothing to show (no player known)
    fun play(); fun pause(); fun next(); fun prev()
    fun seekTo(sec: Int)
    fun select(index: Int)
    fun setShuffle(on: Boolean)
    fun cycleRepeat()
    fun setKind(kind: MediaKind)
}

fun MediaSource.toggle() { if (state.value?.playing == true) pause() else play() }

/** All media rules as pure functions of the state, so the mock, the tests and any future real player share them. */
object MediaReducer {
    fun advance(s: MediaState, dtSec: Int): MediaState {
        if (!s.playing || s.track == null || dtSec <= 0) return s
        var cur = s
        var left = dtSec
        while (left > 0) {
            val remaining = cur.remainingSec
            if (left < remaining) return cur.copy(positionSec = cur.positionSec + left)
            left -= remaining
            cur = ended(cur)
            if (!cur.playing) return cur
        }
        return cur
    }

    /** The current track ran out: repeat one restarts it, otherwise the next one starts; the last track stops (or wraps on repeat all). */
    fun ended(s: MediaState): MediaState = when {
        s.repeat == RepeatMode.One -> s.copy(positionSec = 0)
        s.queue.size > 1 && (s.shuffle || s.index < s.queue.size - 1 || s.repeat == RepeatMode.All) -> next(s)
        else -> s.copy(positionSec = 0, playing = false)
    }

    fun next(s: MediaState): MediaState {
        if (s.queue.isEmpty()) return s
        val i = if (s.shuffle && s.queue.size > 1) shuffled(s) else (s.index + 1) % s.queue.size
        return s.copy(index = i, positionSec = 0)
    }

    /** Previous: past 3 s into a track it restarts it, as every player does. */
    fun prev(s: MediaState): MediaState {
        if (s.queue.isEmpty()) return s
        if (s.positionSec > 3) return s.copy(positionSec = 0)
        return s.copy(index = (s.index - 1 + s.queue.size) % s.queue.size, positionSec = 0)
    }

    fun seek(s: MediaState, sec: Int): MediaState = s.track?.let { s.copy(positionSec = sec.coerceIn(0, it.durationSec)) } ?: s
    fun select(s: MediaState, i: Int): MediaState = if (i in s.queue.indices) s.copy(index = i, positionSec = 0, playing = true) else s

    fun repeatNext(r: RepeatMode) = when (r) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off }

    /** Deterministic "random" next index (never the current one): same queue, same position, same answer. */
    private fun shuffled(s: MediaState): Int {
        val r = Random(s.index * 7919L + s.queue.size).nextInt(s.queue.size - 1)
        return if (r >= s.index) r + 1 else r
    }
}

/** The demo library. Fictional listing, shown only with the Demo data marker. */
object DemoLibrary {
    val QUEUE = listOf(
        Track("Midnight City", "M83", "Hurry Up, We're Dreaming", 243),
        Track("Genesis", "Justice", "Cross", 235),
        Track("Nightcall", "Kavinsky", "OutRun", 258),
        Track("Resonance", "HOME", "Odyssey", 212),
        Track("A Real Hero", "College & Electric Youth", "Drive OST", 270),
        Track("Tech Noir", "Gunship", "Gunship", 322),
        Track("Sunset", "The Midnight", "Endless Summer", 251),
        Track("Neon Lights", "Dance With The Dead", "Loved to Death", 228),
        Track("Turbo Killer", "Carpenter Brut", "Trilogy", 298),
        Track("The Chase", "Com Truise", "Galactic Melt", 244),
        Track("Miami Disco", "Perturbator", "Dangerous Days", 301),
        Track("Rooftops", "Lost Frequencies", "Less Is More", 218),
    )
    fun state(playing: Boolean = true, positionSec: Int = 0, kind: MediaKind = MediaKind.Bluetooth) =
        MediaState(kind = kind, format = formatOf(kind), queue = QUEUE, index = 0, positionSec = positionSec, playing = playing, demo = true)

    fun formatOf(kind: MediaKind): String? = when (kind) {
        MediaKind.Bluetooth -> "FLAC \u00B7 44.1 kHz"; MediaKind.Usb -> "MP3 \u00B7 320 kbps"; MediaKind.Streaming -> "AAC \u00B7 256 kbps"; MediaKind.Radio -> null
    }
}

/** A player that plays nothing: a deterministic in-memory state machine. [tick] advances time by whole seconds (the app calls it once a second). */
class MockMediaSource(initial: MediaState = DemoLibrary.state(playing = true, positionSec = 92)) : MediaSource {
    private val s = MutableStateFlow<MediaState?>(initial)
    override val state: StateFlow<MediaState?> = s.asStateFlow()

    private fun edit(f: (MediaState) -> MediaState) = s.update { it?.let(f) }
    fun tick(dtSec: Int = 1) = edit { MediaReducer.advance(it, dtSec) }

    /** Runs [tick] once a second for as long as the caller's coroutine lives. */
    suspend fun run() { while (true) { delay(1_000); tick(1) } }

    override fun play() = edit { it.copy(playing = it.track != null) }
    override fun pause() = edit { it.copy(playing = false) }
    override fun next() = edit { MediaReducer.next(it) }
    override fun prev() = edit { MediaReducer.prev(it) }
    override fun seekTo(sec: Int) = edit { MediaReducer.seek(it, sec) }
    override fun select(index: Int) = edit { MediaReducer.select(it, index) }
    override fun setShuffle(on: Boolean) = edit { it.copy(shuffle = on) }
    override fun cycleRepeat() = edit { it.copy(repeat = MediaReducer.repeatNext(it.repeat)) }
    override fun setKind(kind: MediaKind) = edit { it.copy(kind = kind, format = DemoLibrary.formatOf(kind)) }
}

/** m:ss, and the "remaining" form with a real minus sign. */
object MediaFmt {
    fun clock(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
    fun remaining(sec: Int): String = "\u2212" + clock(sec)
}
