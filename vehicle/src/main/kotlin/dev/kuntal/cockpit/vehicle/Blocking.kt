package dev.kuntal.cockpit.vehicle

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.io.Closeable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs blocking socket work so that cancelling the caller returns at once. `withContext(Dispatchers.IO) { socket.read() }` does not:
 * the caller stays suspended until the read returns (up to the read timeout, or forever against an adapter that drips a byte per
 * second), and a flow that is switched to another source waits for the old one to finish ("flatMapLatest" joins it).
 * On cancellation [closeable] is closed, which is also what makes the blocked thread give up; the thread is never leaked beyond
 * the call that was already in flight.
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun <T> cancellableBlocking(closeable: Closeable?, block: () -> T): T {
    val work = GlobalScope.async(Dispatchers.IO) { block() }
    try {
        return work.await()
    } catch (e: CancellationException) {
        runCatching { closeable?.close() }
        throw e
    }
}
