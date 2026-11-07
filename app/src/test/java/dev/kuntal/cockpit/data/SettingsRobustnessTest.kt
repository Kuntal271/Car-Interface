package dev.kuntal.cockpit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Seeded random input into the stored form: whatever is in the preferences file, the app must start with valid settings. */
class SettingsRobustnessTest {
    private val keys = Settings().toMap().keys.toList() + listOf("unknown", "", "theme ")
    private val junk = listOf("", " ", "0", "-1", "65536", "999999999999", "NaN", "null", "true", "Ford", "Midnight", "KMH", "\u0000", "💥", "a".repeat(5000))

    private fun randomMap(r: Random) = keys.shuffled(r).take(r.nextInt(0, keys.size)).associateWith {
        if (r.nextBoolean()) junk.random(r) else r.nextInt(-20_000, 20_000).toString()
    }

    @Test fun anyStoredMapYieldsValidSettings() {
        val r = Random(20261108)
        repeat(3_000) {
            val m = randomMap(r)
            val s = Settings.fromMap(m)                                    // must not throw
            assertEquals("fromMap output is already sanitized: $m", s, s.sanitized())
            assertTrue("shift <= redline <= tach: $s", s.shiftRpm <= s.redlineRpm && s.redlineRpm <= s.tachMaxRpm)
            assertTrue("port: $s", s.tcpPort in 1..65535)
            assertTrue("host: $s", s.tcpHost.isNotBlank())
            assertEquals("stored form round-trips: $s", s, Settings.fromMap(s.toMap()))
        }
    }

    @Test fun everyCombinationOfStepButtonsStaysConsistent() {
        val r = Random(7)
        var s = Settings()
        repeat(2_000) {
            s = when (r.nextInt(4)) {
                0 -> Thresholds.redline(s, if (r.nextBoolean()) 1 else -1)
                1 -> Thresholds.tachMax(s, if (r.nextBoolean()) 1 else -1)
                2 -> Thresholds.shift(s, if (r.nextBoolean()) 1 else -1)
                else -> Thresholds.speedScale(s.copy(speedUnit = SpeedUnit.entries.random(r)), if (r.nextBoolean()) 1 else -1)
            }
            assertTrue("shift <= redline <= tach: $s", s.shiftRpm <= s.redlineRpm && s.redlineRpm <= s.tachMaxRpm)
            assertTrue("speed scale: $s", s.speedScaleKmh in 120..300)
        }
    }
}
