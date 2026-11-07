package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSettingsTest {
    @Test fun presetsHaveEightBands() = AudioPresets.NAMES.forEach { assertEquals(it, AudioState.BANDS, AudioPresets.bands(it)!!.size) }

    @Test fun bandsAreClampedAndMarkThePresetEdited() {
        val a = MockAudioSettings(AudioState(preset = "Punch", bands = AudioPresets.bands("Punch")!!))
        assertFalse(a.state.value.edited)
        a.setBand(0, 99); assertEquals(12, a.state.value.bands[0]); assertTrue(a.state.value.edited)
        a.setBand(1, -99); assertEquals(-12, a.state.value.bands[1])
        a.setBand(42, 3); assertEquals(8, a.state.value.bands.size)           // out of range index is ignored
        a.applyPreset("Punch"); assertFalse(a.state.value.edited)
    }

    @Test fun balanceFaderSubAreClamped() {
        val a = MockAudioSettings()
        a.setBalance(-40); a.setFader(40); a.setSubwoofer(-40)
        assertEquals(-10, a.state.value.balance); assertEquals(10, a.state.value.fader); assertEquals(-10, a.state.value.subwooferDb)
    }

    @Test fun unknownPresetChangesNothing() {
        val a = MockAudioSettings(); a.applyPreset("Disco"); assertEquals("Flat", a.state.value.preset)
    }

    @Test fun togglesStick() {
        val a = MockAudioSettings(); a.setLoudness(true); a.setSpeedVolume(true)
        assertTrue(a.state.value.loudness); assertTrue(a.state.value.speedVolume)
    }
}
