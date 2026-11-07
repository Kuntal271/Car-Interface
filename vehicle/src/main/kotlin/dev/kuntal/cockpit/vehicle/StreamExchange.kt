package dev.kuntal.cockpit.vehicle

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Shared by every byte-stream transport (TCP, Bluetooth SPP, USB serial): send "cmd\r", read until the '>' prompt.
 * This is the single place where bytes reach the wire, so it enforces [CommandGuard] itself: a transport called directly cannot
 * bypass the client.
 */
fun exchangeOverStreams(input: InputStream, output: OutputStream, cmd: String): String {
    require(CommandGuard.isAllowed(cmd)) { "blocked command" }
    output.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
    output.flush()
    val sb = StringBuilder()
    while (true) {
        val b = input.read()
        if (b < 0) throw IOException("link closed")
        if (b == '>'.code) break
        sb.append(b.toChar())
    }
    return sb.toString().trim()
}
