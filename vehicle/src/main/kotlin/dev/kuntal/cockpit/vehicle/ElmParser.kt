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
                val v = payload.substring(i * 2, i * 2 + 2).toIntOrNull(16)
                if (v == null) { ok = false; break }
                out[i] = v
            }
            if (ok) return out
        }
        return null
    }

    fun parse(pid: Pid, raw: String): Double? =
        bytesAfter("41%02X".format(pid.code), pid.bytes, raw)?.let { PidDecoder.decode(pid, it) }

    fun parseBitmap(base: Int, raw: String): IntArray? = bytesAfter("41%02X".format(base), 4, raw)
}
