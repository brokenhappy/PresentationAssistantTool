package com.woutwerkman.pa

import com.woutwerkman.pa.ble.PairedPeer
import com.woutwerkman.pa.ble.PeerStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PeerStorageTest {

    @Test
    fun loadReturnsEmptyWhenNoFileExists() = runTest {
        val storage = PeerStorage(InMemoryFileSystem())
        assertEquals(emptyList(), storage.load())
    }

    @Test
    fun addAndLoadPeer() = runTest {
        val storage = PeerStorage(InMemoryFileSystem())
        val peer = PairedPeer(id = "abc-123", name = "iPhone")

        storage.addPeer(peer)

        assertEquals(listOf(peer), storage.load())
    }

    @Test
    fun addPeerReplacesExistingById() = runTest {
        val storage = PeerStorage(InMemoryFileSystem())
        storage.addPeer(PairedPeer(id = "abc", name = "Old Name"))
        storage.addPeer(PairedPeer(id = "abc", name = "New Name"))

        val peers = storage.load()
        assertEquals(1, peers.size)
        assertEquals("New Name", peers.first().name)
    }

    @Test
    fun removePeer() = runTest {
        val storage = PeerStorage(InMemoryFileSystem())
        storage.addPeer(PairedPeer(id = "abc", name = "iPhone"))
        storage.addPeer(PairedPeer(id = "def", name = "iPad"))

        storage.removePeer("abc")

        val peers = storage.load()
        assertEquals(1, peers.size)
        assertEquals("def", peers.first().id)
    }
}
