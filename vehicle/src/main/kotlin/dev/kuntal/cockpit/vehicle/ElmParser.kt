package dev.kuntal.cockpit.vehicle

object ElmParser {
    private fun bytesAfter(header: String, n: Int, raw: String): IntArray? {
        for (line in raw.lineSequence()) {
            val h = line.filterNot { it.isWhitespace() }.uppercase()
            if (!h.startsWith(header)) continue
            val payload = h.substring(header.length)
            if (payload.length < n * 2) continue
            val out = IntArray(n)
            var ok = true
            for (i in 0 until n) {
                val hi = Character.digit(payload[i * 2], 16); val lo = Character.digit(payload[i * 2 + 1], 16)       // not toIntOrNull(16): it accepts "-1" and "+F"
                if (hi < 0 || lo < 0 || !payload[i * 2].isAscii() || !payload[i * 2 + 1].isAscii()) { ok = false; break }
                out[i] = hi * 16 + lo
            }
            if (ok) return out
        }
        return null
    }

    private fun Char.isAscii() = code < 128

    /** The decoded reading, or null for no data, a damaged reply, or a value this car cannot produce (see [PidDecoder.plausible]). */
    fun parse(pid: Pid, raw: String): Double? =
        bytesAfter("41%02X".format(pid.code), pid.bytes, raw)?.let { PidDecoder.decode(pid, it) }?.takeIf { PidDecoder.plausible(pid, it) }

    fun parseBitmap(base: Int, raw: String): IntArray? = bytesAfter("41%02X".format(base), 4, raw)
}
