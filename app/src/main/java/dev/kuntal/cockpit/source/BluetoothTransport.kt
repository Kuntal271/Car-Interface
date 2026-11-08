package dev.kuntal.cockpit.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import dev.kuntal.cockpit.vehicle.Transport
import dev.kuntal.cockpit.vehicle.cancellableBlocking
import dev.kuntal.cockpit.vehicle.exchangeOverStreams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * ELM327 over classic Bluetooth (SPP / RFCOMM) to an adapter that is already paired in Android's settings.
 * Does not cover BLE-only adapters. Bluetooth permissions are the install-time ones (targetSdk 30, Android 11 unit).
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(private val address: String, private val timeoutMs: Long = 4000) : Transport {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override suspend fun open() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IOException("This device has no Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth is off")
        val device = try { adapter.getRemoteDevice(address) } catch (e: IllegalArgumentException) { throw IOException("Bad adapter address", e) }
        val s = device.createRfcommSocketToServiceRecord(SPP)
        socket = s                                                 // before connecting, so close() (or a cancel) can abort a connect that is pending
        try {
            cancellableBlocking(s) { adapter.cancelDiscovery(); s.connect() }          // discovery makes connect() slow and flaky
        } catch (e: IOException) { runCatching { s.close() }; throw e }
        input = s.inputStream; output = s.outputStream
    }

    override suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
        Unit
    }

    override suspend fun exchange(cmd: String): String {
        val s = socket; val i = input; val o = output
        if (s == null || i == null || o == null) throw IOException("not open")
        // A blocking read on a Bluetooth socket has no timeout: the socket is closed from a watchdog when the time is up, and by
        // cancellableBlocking when the caller is cancelled (switching source used to wait for a hung adapter for ever).
        return coroutineScope {
            val watchdog = launch(Dispatchers.IO) { delay(timeoutMs); runCatching { s.close() } }
            try { cancellableBlocking(s) { exchangeOverStreams(i, o, cmd) } } finally { watchdog.cancel() }
        }
    }

    private companion object { val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }
}

/** Adapters already paired in Android's Bluetooth settings (name, address). Empty when Bluetooth is off or missing. */
@SuppressLint("MissingPermission")
fun pairedAdapters(): List<Pair<String, String>> = try {
    BluetoothAdapter.getDefaultAdapter()?.bondedDevices.orEmpty().map { (it.name ?: it.address) to it.address }.sortedBy { it.first.lowercase() }
} catch (_: SecurityException) { emptyList() }
