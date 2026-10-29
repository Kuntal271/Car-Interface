package dev.kuntal.cockpit.vehicle

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Shared by every byte-stream transport (TCP, Bluetooth SPP, USB serial): send "cmd\r", read until the '>' prompt. */
fun exchangeOverStreams(input: InputStream, output: OutputStream, cmd: String): String {
    output.write((cmd + "\r").toByteArray())
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
