package dev.kuntal.cockpit.source

import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.Settings
import dev.kuntal.cockpit.data.VehicleRepository
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.vehicle.ElmClient
import dev.kuntal.cockpit.vehicle.SimSource
import dev.kuntal.cockpit.vehicle.TcpTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The one place that turns "what the owner chose" into a stream of vehicle state. Each call builds a fresh repository
 * (fresh trip counters); the caller restarts it only when [Settings.sourceKey] changes.
 */
object Sources {
    fun states(s: Settings): Flow<VehicleUiState> = when {
        s.sourceKey == "sim" -> VehicleRepository(SimSource(), simulated = true).states()
        s.sourceKey.startsWith("tcp:") -> VehicleRepository(ElmClient(TcpTransport(s.tcpHost.trim(), s.tcpPort, timeoutMs = 4000)), simulated = false).states()
        s.sourceKey.startsWith("bt:") -> VehicleRepository(ElmClient(BluetoothTransport(s.btAddress)), simulated = false).states()
        else -> flowOf(VehicleUiState(link = Link.Off))
    }
}
