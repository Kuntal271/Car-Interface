package dev.kuntal.cockpit.vehicle.testing

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A tiny in-process ELM327 on a loopback socket: reads "cmd\r", answers "<reply>\r\r>" like the real chip.
 * Lets the TCP path (`TcpTransport` + `exchangeOverStreams` + `ElmClient`) run in plain JVM tests and in CI, without the Python emulator.
 *
 *  - [replies]: command -> reply text; unknown `AT...` gets "OK", unknown PIDs "NO DATA"
 *  - [dropAfter]: after that many commands the adapter closes the connection (unplugged mid-drive)
 */
class LoopbackElm(
    private val replies: Map<String, String>,
    private val dropAfter: Int = Int.MAX_VALUE,
) : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val received = CopyOnWriteArrayList<String>()
    private val worker = thread(isDaemon = true, name = "loopback-elm") { serve() }

    private fun serve() {
        try {
            server.accept().use { s ->
                val input = s.getInputStream()
                val out = s.getOutputStream()
                val line = StringBuilder()
                var n = 0
                while (true) {
                    val b = input.read()
                    if (b < 0) return
                    if (b != '\r'.code) { line.append(b.toChar()); continue }
                    val cmd = line.toString().also { line.setLength(0) }
                    received += cmd
                    if (++n > dropAfter) return
                    val reply = replies[cmd] ?: if (cmd.startsWith("AT")) "OK" else "NO DATA"
                    out.write("$reply\r\r>".toByteArray())
                    out.flush()
                }
            }
        } catch (_: IOException) {
            // the test closed the server while the client was connected
        }
    }

    override fun close() {
        runCatching { server.close() }
        worker.interrupt()
    }

    companion object {
        /** A port nothing listens on: for "connection refused". */
        fun closedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    }
}
