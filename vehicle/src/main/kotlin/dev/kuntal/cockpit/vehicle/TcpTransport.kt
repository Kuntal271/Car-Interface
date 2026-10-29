package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TcpTransport(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = 2000,
) : Transport {
    private var socket: Socket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        // Not `Socket().apply { connect(InetSocketAddress(host, port)) }`: inside apply, `port` resolves to
        // Socket.getPort() (0 while unconnected), not our constructor property -> "Can't assign requested address".
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = timeoutMs
        s.connect(InetSocketAddress(host, port), timeoutMs)
        socket = s
        Unit
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { socket?.close() }
        socket = null
        Unit
    }

    override suspend fun exchange(cmd: String): String = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("not open")
        exchangeOverStreams(s.getInputStream(), s.getOutputStream(), cmd)
    }
}
