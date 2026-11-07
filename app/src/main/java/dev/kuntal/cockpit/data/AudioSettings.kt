package dev.kuntal.cockpit.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * Sound: the unit's own DSP (8-band EQ, balance, fader, subwoofer) is a vendor service this app cannot reach yet. The screen is
 * built over [AudioSettings]; the only implementation is [MockAudioSettings], which remembers the values and changes no sound.
 */

data class AudioState(
    val preset: String = "Flat",
    val bands: List<Int> = List(BANDS) { 0 },      // dB, -12..+12, 60 Hz .. 8 kHz
    val balance: Int = 0,                            // -10 (left) .. +10 (right)
    val fader: Int = 0,                              // -10 (rear) .. +10 (front)
    val subwooferDb: Int = 0,                        // -10..+10
    val loudness: Boolean = false,
    val speedVolume: Boolean = false,
) {
    /** True when the bands no longer match the named preset (the design's "Punch · edited"). */
    val edited: Boolean get() = AudioPresets.bands(preset)?.let { it != bands } ?: true

    companion object { const val BANDS = 8 }
}

object AudioPresets {
    val NAMES = listOf("Flat", "Bass", "Punch", "Vocal")
    val FREQ = listOf("60 Hz", "120 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz")
    fun bands(name: String): List<Int>? = when (name) {
        "Flat" -> listOf(0, 0, 0, 0, 0, 0, 0, 0)
        "Bass" -> listOf(6, 5, 3, 1, 0, 0, 0, 0)
        "Punch" -> listOf(4, 3, 1, 0, -1, 0, 2, 3)
        "Vocal" -> listOf(-2, -1, 0, 2, 3, 3, 1, 0)
        else -> null
    }
}

interface AudioSettings {
    val state: StateFlow<AudioState>
    fun setBand(i: Int, db: Int)
    fun applyPreset(name: String)
    fun setBalance(v: Int)
    fun setFader(v: Int)
    fun setSubwoofer(db: Int)
    fun setLoudness(on: Boolean)
    fun setSpeedVolume(on: Boolean)
}

class MockAudioSettings(initial: AudioState = AudioState()) : AudioSettings {
    private val s = MutableStateFlow(initial)
    override val state: StateFlow<AudioState> = s.asStateFlow()

    override fun setBand(i: Int, db: Int) = s.update { st ->
        if (i !in st.bands.indices) st else st.copy(bands = st.bands.toMutableList().also { it[i] = db.coerceIn(-12, 12) })
    }
    override fun applyPreset(name: String) = s.update { st -> AudioPresets.bands(name)?.let { st.copy(preset = name, bands = it) } ?: st }
    override fun setBalance(v: Int) = s.update { it.copy(balance = v.coerceIn(-10, 10)) }
    override fun setFader(v: Int) = s.update { it.copy(fader = v.coerceIn(-10, 10)) }
    override fun setSubwoofer(db: Int) = s.update { it.copy(subwooferDb = db.coerceIn(-10, 10)) }
    override fun setLoudness(on: Boolean) = s.update { it.copy(loudness = on) }
    override fun setSpeedVolume(on: Boolean) = s.update { it.copy(speedVolume = on) }
}
