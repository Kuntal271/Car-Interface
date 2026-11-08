package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeContentLiveTest {
    private val playing = NowPlaying("Midnight City", "M83", "Hurry Up, We're Dreaming", "Demo audio", 95, 243, playing = true)
    private val cold = CanboxState(climate = Climate(acOn = true, setC = 22.0, fan = 3, recirc = true))

    @Test fun noSourcesMeansTheHonestPlaceholders() {
        val c = HomeContent.live(HomeContent.EMPTY, media = null, canbox = null)
        assertEquals(HomeContent.EMPTY, c)
        assertEquals("Waiting for Canbox", c.climate.subtitle); assertNull(c.media)
    }

    @Test fun theMediaCardFollowsWhatPlays() = assertEquals(playing, HomeContent.live(HomeContent.EMPTY, playing, null).media)

    @Test fun theClimateTileFollowsTheCanbox() {
        val c = HomeContent.live(HomeContent.EMPTY, null, cold).climate
        assertEquals("22.0", c.value); assertEquals("\u00B0C", c.unit); assertEquals("A/C on", c.title); assertEquals("Fan 3 \u00B7 recirculate", c.subtitle)
    }

    @Test fun climateFollowsTheTemperatureUnit() {
        val c = HomeContent.live(HomeContent.EMPTY, null, cold, TempUnit.F).climate
        assertEquals("71.6", c.value); assertEquals("\u00B0F", c.unit)
    }

    @Test fun acOffAndNoSetPointAreSaidPlainly() {
        val c = HomeContent.live(HomeContent.EMPTY, null, CanboxState(climate = Climate(acOn = false, setC = null, fan = 0))).climate
        assertEquals(Fmt.DASH, c.value); assertNull(c.unit); assertEquals("A/C off", c.title)
    }

    @Test fun aCanboxWithoutClimateKeepsThePlaceholder() =
        assertEquals(HomeContent.EMPTY.climate, HomeContent.live(HomeContent.EMPTY, null, CanboxState()).climate)
}
