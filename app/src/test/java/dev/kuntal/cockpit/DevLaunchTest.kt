package dev.kuntal.cockpit

import org.junit.Assert.*
import org.junit.Test

/** MainActivity is exported (it is the HOME activity), so any installed app can start it with any extras. */
class DevLaunchTest {
    private val hostile = mapOf<String, Any>(
        "snapshot" to true, "reset" to true, "perf" to true,
        "set" to "source=ObdTcp,tcpHost=203.0.113.9,tcpPort=35000,theme=Midnight",
    )

    private fun parse(debuggable: Boolean, extras: Map<String, Any> = hostile) =
        DevLaunch.parse(debuggable, { (extras[it] as? Boolean) ?: false }, { extras[it] as? String })

    @Test fun releaseIgnoresEveryStateChangingOrDataFakingExtra() {
        val d = parse(debuggable = false)
        assertFalse("snapshot freezes the UI on fake 'Connected' numbers", d.snapshot)
        assertFalse("reset wipes the owner's settings", d.reset)
        assertTrue("set rewrites persisted settings, e.g. the adapter host", d.set.isEmpty())
    }

    @Test fun debugBuildsKeepTheDeveloperHooks() {
        val d = parse(debuggable = true)
        assertTrue(d.snapshot); assertTrue(d.reset); assertTrue(d.perf)
        assertEquals(mapOf("source" to "ObdTcp", "tcpHost" to "203.0.113.9", "tcpPort" to "35000", "theme" to "Midnight"), d.set)
    }

    @Test fun perfOnlyShowsFrameTimesAndStaysAvailableInRelease() = assertTrue(parse(debuggable = false).perf)

    @Test fun missingWrongTypedOrBrokenExtrasAreHarmless() {
        assertEquals(DevLaunch.NONE, parse(true, emptyMap()))
        assertEquals(DevLaunch.NONE, parse(true, mapOf("snapshot" to "true", "set" to 5, "reset" to 1)))
        val boom = DevLaunch.parse(true, { throw IllegalStateException("BadParcelable") }, { throw IllegalStateException("BadParcelable") })
        assertEquals(DevLaunch.NONE, boom)
    }

    @Test fun setParserSkipsGarbageAndKeepsKeyValuePairs() {
        assertEquals(mapOf("a" to "b=c"), DevLaunch.parseSet("a=b=c,,=,noequals, ,"  ).filterKeys { it == "a" })
        assertTrue(DevLaunch.parseSet(null).isEmpty())
        assertTrue(DevLaunch.parseSet("x".repeat(2_000_000)).isEmpty())
        assertTrue("at most 32 pairs", DevLaunch.parseSet((1..500).joinToString(",") { "k$it=v" }).size <= 32)
    }
}
