package dev.kuntal.cockpit.vehicle

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** No ELM327 reply is longer than a few hundred bytes; anything past this is a broken or hostile adapter, not a longer answer. */
const val MAX_REPLY_BYTES = 8 * 1024

/**
 * Shared by every byte-stream transport (TCP, Bluetooth SPP, USB serial): send "cmd\r", read until the '>' prompt.
 * Bounded twice, because a socket read timeout only covers silence: a reply of more than [MAX_REPLY_BYTES] fails (an endless
 * stream used to grow the buffer until the process ran out of memory), and so does one that takes longer than [deadlineMs] in
 * total (a byte every second never trips a read timeout). [now] is a monotonic millisecond clock.
 */
fun exchangeOverStreams(
    input: InputStream, output: OutputStream, cmd: String,
    deadlineMs: Long = 15_000, now: () -> Long = { System.nanoTime() / 1_000_000 },
): String {
    output.write((cmd + "\r").toByteArray())
    output.flush()
    val sb = StringBuilder()
    val giveUpAt = now() + deadlineMs
    while (true) {
        val b = input.read()
        if (b < 0) throw IOException("link closed")
        if (b == '>'.code) break
        if (sb.length >= MAX_REPLY_BYTES) throw IOException("reply too long without a prompt")
        if (now() > giveUpAt) throw IOException("reply took too long")
        sb.append(b.toChar())
    }
    return sb.toString().trim()
}
