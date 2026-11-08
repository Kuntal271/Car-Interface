package dev.kuntal.cockpit.perf

import org.junit.Assert.assertEquals
import org.junit.Test

/** Window.removeOnFrameMetricsAvailableListener throws IllegalArgumentException when the listener is not registered: stop() then release() crashed on BACK. */
class ListenerHandleTest {
    private var adds = 0
    private var removes = 0
    private val h = ListenerHandle({ adds++ }, { removes++ })

    @Test fun detachingTwiceRemovesOnce() { h.attach(); h.detach(); h.detach(); assertEquals(1 to 1, adds to removes) }
    @Test fun detachingWithoutAttachingDoesNothing() { h.detach(); assertEquals(0 to 0, adds to removes) }
    @Test fun attachingTwiceAddsOnce() { h.attach(); h.attach(); assertEquals(1 to 0, adds to removes) }
    @Test fun canBeAttachedAgainAfterDetach() { h.attach(); h.detach(); h.attach(); assertEquals(2 to 1, adds to removes) }
}
