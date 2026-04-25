package com.woutwerkman.pa.ble

import com.woutwerkman.pa.platform.PlatformFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersistedPeers(
    val peers: List<PairedPeer> = emptyList(),
)

class PeerStorage(private val fileSystem: PlatformFileSystem) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val path get() = "${fileSystem.appDataDir}/connections/peers.json"

    suspend fun load(): List<PairedPeer> {
        if (!fileSystem.exists(path)) return emptyList()
        return try {
            val text = fileSystem.readText(path)
            json.decodeFromString<PersistedPeers>(text).peers
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(peers: List<PairedPeer>) {
        fileSystem.ensureParentExists(path)
        fileSystem.writeText(path, json.encodeToString(PersistedPeers.serializer(), PersistedPeers(peers)))
    }

    suspend fun addPeer(peer: PairedPeer) {
        val existing = load().toMutableList()
        existing.removeAll { it.id == peer.id }
        existing.add(peer)
        save(existing)
    }

    suspend fun removePeer(id: String) {
        val existing = load().toMutableList()
        existing.removeAll { it.id == id }
        save(existing)
    }
}
