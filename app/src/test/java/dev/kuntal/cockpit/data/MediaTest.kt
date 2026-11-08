package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTest {
    private val q = DemoLibrary.QUEUE
    private fun st(playing: Boolean = true, pos: Int = 0, idx: Int = 0) = DemoLibrary.state(playing, pos).copy(index = idx)

    @Test fun designNumbersAddUp() {                      // the reference shows 1:32 elapsed and 2:31 remaining of 4:03
        val s = st(pos = 92)
        assertEquals("1:32", MediaFmt.clock(s.positionSec)); assertEquals("\u22122:31", MediaFmt.remaining(s.remainingSec)); assertEquals(243, s.track!!.durationSec)
        assertEquals(12, s.queue.size)
    }

    @Test fun advancePlaysThroughTracks() {
        val s = MediaReducer.advance(st(pos = 240), 5)       // 3 s left, then 2 s into the next
        assertEquals(1, s.index); assertEquals(2, s.positionSec)
    }

    @Test fun pausedDoesNotAdvance() = assertEquals(st(playing = false, pos = 10), MediaReducer.advance(st(playing = false, pos = 10), 30))

    @Test fun lastTrackStopsUnlessRepeatAll() {
        val last = st(pos = q.last().durationSec - 1, idx = q.size - 1)
        val ended = MediaReducer.advance(last, 3)
        assertFalse(ended.playing); assertEquals(0, ended.positionSec); assertEquals(q.size - 1, ended.index)
        assertEquals(0, MediaReducer.advance(last.copy(repeat = RepeatMode.All), 3).index)
    }

    @Test fun repeatOneRestartsTheTrack() {
        val s = MediaReducer.advance(st(pos = 240).copy(repeat = RepeatMode.One), 5)
        assertEquals(0, s.index); assertEquals(2, s.positionSec); assertTrue(s.playing)
    }

    @Test fun prevRestartsAfterThreeSecondsElseGoesBack() {
        assertEquals(0, MediaReducer.prev(st(pos = 20, idx = 3)).positionSec); assertEquals(3, MediaReducer.prev(st(pos = 20, idx = 3)).index)
        assertEquals(2, MediaReducer.prev(st(pos = 2, idx = 3)).index)
        assertEquals(q.size - 1, MediaReducer.prev(st(pos = 0, idx = 0)).index)       // wraps
    }

    @Test fun seekIsClamped() {
        assertEquals(243, MediaReducer.seek(st(), 9_999).positionSec); assertEquals(0, MediaReducer.seek(st(pos = 50), -5).positionSec)
        assertEquals(0, MediaReducer.seek(MediaState(), 30).positionSec)             // nothing loaded
    }

    @Test fun selectStartsPlaying() {
        val s = MediaReducer.select(st(playing = false, pos = 30), 4)
        assertEquals(4, s.index); assertEquals(0, s.positionSec); assertTrue(s.playing)
        assertEquals(st(), MediaReducer.select(st(), 99))
    }

    @Test fun shuffleIsDeterministicAndMoves() {
        val s = st(idx = 2).copy(shuffle = true)
        val a = MediaReducer.next(s); val b = MediaReducer.next(s)
        assertEquals(a, b); assertTrue(a.index != 2)
    }

    @Test fun repeatCycle() = assertEquals(listOf(RepeatMode.All, RepeatMode.One, RepeatMode.Off), listOf(RepeatMode.Off, RepeatMode.All, RepeatMode.One).map { MediaReducer.repeatNext(it) })

    @Test fun mockSourceIsControllable() {
        val m = MockMediaSource(st(playing = false, pos = 5))
        m.play(); assertTrue(m.state.value!!.playing)
        m.tick(10); assertEquals(15, m.state.value!!.positionSec)
        m.next(); assertEquals(1, m.state.value!!.index)
        m.pause(); m.tick(30); assertEquals(0, m.state.value!!.positionSec)
        m.setShuffle(true); assertTrue(m.state.value!!.shuffle)
        m.cycleRepeat(); assertEquals(RepeatMode.All, m.state.value!!.repeat)
        m.setKind(MediaKind.Usb); assertEquals("MP3 \u00B7 320 kbps", m.state.value!!.format)
        m.toggle(); assertTrue(m.state.value!!.playing); m.toggle(); assertFalse(m.state.value!!.playing)
    }

    @Test fun nowPlayingLabelsDemoHonestly() {
        assertEquals("Demo audio", DemoLibrary.state().nowPlaying!!.source)
        assertEquals("Bluetooth", DemoLibrary.state().copy(demo = false).nowPlaying!!.source)
        assertNull(MediaState().nowPlaying)
    }
}
