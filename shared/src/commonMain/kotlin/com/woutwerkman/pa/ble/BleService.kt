package com.woutwerkman.pa.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class BleConnectionState {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
}

@Serializable
data class PairedPeer(
    val id: String,
    val name: String,
)

interface BleService {
    val connectionState: StateFlow<BleConnectionState>
    val connectedPeers: StateFlow<List<PairedPeer>>
    val incomingMessages: Flow<BleMessage>

    suspend fun startAdvertisingOrScanning()
    suspend fun stopAdvertisingOrScanning()
    suspend fun sendMessage(message: BleMessage)

    suspend fun disconnectPeer(id: String)
    suspend fun getPersistedPeers(): List<PairedPeer>
    suspend fun forgetPeer(id: String)
}
