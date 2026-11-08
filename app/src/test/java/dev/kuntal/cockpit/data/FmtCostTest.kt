package dev.kuntal.cockpit.data

import org.junit.Test

/** Informational: what one telemetry sample costs in number formatting (prints; `gradlew :app:testDebugUnitTest -i`). JVM figures, not the unit's. */
class FmtCostTest {
    private fun usPerCall(n: Int, f: (Int) -> String): Double {
        var sink = 0
        repeat(20_000) { sink += f(it).length }                                  // warm the JIT
        val t = System.nanoTime()
        repeat(n) { sink += f(it).length }
        val us = (System.nanoTime() - t) / 1e3 / n
        if (sink == 42) println()
        return us
    }

    @Test fun formattingCostPerSample() {
        val n = 200_000
        val dec = usPerCall(n) { Fmt.dec(it / 7.0, 1) }
        val signed = usPerCall(n) { Fmt.signed((it % 300) / 100.0 - 1.0, 2) }
        val grouped = usPerCall(n) { Fmt.grouped(it % 7000) }
        val whole = usPerCall(n) { Fmt.whole(it / 3.0) }
        val clock = usPerCall(n) { Fmt.clock(it % 24, it % 60) }
        // one Drive sample re-evaluates ~16 formatted readouts of which about 5 use String.format (boost x2, peak, distance, 0-100)
        println("Fmt JVM us/call: dec %.2f  signed %.2f  grouped %.2f  whole %.3f  clock %.2f  => ~%.0f us per sample (20 Hz: %.2f ms per second); x5-10 on a weak ARM core".format(
            dec, signed, grouped, whole, clock, 5 * signed, 20 * 5 * signed / 1000))
    }
}
