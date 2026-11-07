package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TcpTransport(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = 2000,
) : Transport {
    @Volatile private var socket: Socket? = null

    override suspend fun open() {
        // Not `Socket().apply { connect(InetSocketAddress(host, port)) }`: inside apply, `port` resolves to
        // Socket.getPort() (0 while unconnected), not our constructor property -> "Can't assign requested address".
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = timeoutMs
        socket = s                                              // before connecting, so close() can abort a connect that is still pending
        cancellableBlocking(s) { s.connect(InetSocketAddress(host, port), timeoutMs) }     // the name lookup happens here too
    }

    override suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        runCatching { socket?.close() }
        socket = null
        Unit
    }

    override suspend fun exchange(cmd: String): String {
        val s = socket ?: throw IOException("not open")
        return cancellableBlocking(s) { exchangeOverStreams(s.getInputStream(), s.getOutputStream(), cmd) }
    }
}
