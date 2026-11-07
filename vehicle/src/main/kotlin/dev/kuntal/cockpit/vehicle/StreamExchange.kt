package dev.kuntal.cockpit.vehicle

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Longest reply we accept between a command and the '>' prompt. Real replies are under 300 characters (a multi-ECU 0100 with headers). */
const val MAX_REPLY_CHARS = 2048

/** Longest we wait for one complete reply, however slowly the bytes arrive (the socket timeout only bounds the gap between two bytes). */
const val REPLY_DEADLINE_MS = 10_000L

/**
 * Shared by every byte-stream transport (TCP, Bluetooth SPP, USB serial): send "cmd\r", read until the '>' prompt.
 *
 * This is the single place where bytes reach the wire, so it enforces [CommandGuard] itself (a transport called directly cannot
 * bypass the client), and it treats the adapter as untrusted: the reply is bounded in size ([maxChars]) and in time
 * ([deadlineMs]); an adapter that never sends '>' or streams without end ends in an [IOException], never in unbounded memory or a hung thread.
 */
fun exchangeOverStreams(
    input: InputStream,
    output: OutputStream,
    cmd: String,
    maxChars: Int = MAX_REPLY_CHARS,
    deadlineMs: Long = REPLY_DEADLINE_MS,
    nanoTime: () -> Long = System::nanoTime,
): String {
    require(CommandGuard.isAllowed(cmd)) { "blocked command" }
    output.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
    output.flush()
    val start = nanoTime()
    val limitNs = deadlineMs * 1_000_000L
    val sb = StringBuilder()
    while (true) {
        val b = input.read()
        if (b < 0) throw IOException("link closed")
        if (b == '>'.code) break
        if (sb.length >= maxChars) throw IOException("reply too long")
        if (nanoTime() - start > limitNs) throw IOException("reply too slow")
        sb.append(b.toChar())
    }
    return sb.toString().trim()
}
