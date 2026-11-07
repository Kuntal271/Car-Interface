package dev.kuntal.cockpit.vehicle

/**
 * Turns adapter text into numbers. The adapter is untrusted input: only well-formed upper/lower-case ASCII hex counts
 * (`String.toInt(16)` would also accept "-1", "+F" and non-ASCII digits), and a value that no real vehicle can report is
 * dropped instead of shown (see [PidDecoder.isPlausible]).
 */
object ElmParser {
    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> c - 'a' + 10
        else -> -1
    }

    private fun bytesAfter(header: String, n: Int, raw: String): IntArray? {
        for (line in raw.lineSequence()) {
            // Spaces and tabs between bytes are normal; stray control characters (some clones emit NULs) are tolerated at the ends only.
            val h = line.trim { it.code <= 0x20 }.filterNot { it == ' ' || it == '\t' }
            if (!h.startsWith(header, ignoreCase = true)) continue
            val payload = h.substring(header.length)
            if (payload.length < n * 2 || payload.length % 2 != 0) continue
            if (payload.any { hexVal(it) < 0 }) continue
            val out = IntArray(n) { hexVal(payload[it * 2]) * 16 + hexVal(payload[it * 2 + 1]) }
            return out
        }
        return null
    }

    fun parse(pid: Pid, raw: String): Double? =
        bytesAfter("41%02X".format(pid.code), pid.bytes, raw)?.let { PidDecoder.decode(pid, it) }?.takeIf { PidDecoder.isPlausible(pid, it) }

    fun parseBitmap(base: Int, raw: String): IntArray? = bytesAfter("41%02X".format(base), 4, raw)
}
