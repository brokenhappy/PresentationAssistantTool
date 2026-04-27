package com.woutwerkman.pa.ble

import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.characteristicOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DesktopBleService(
    private val scope: CoroutineScope,
    private val peerStorage: PeerStorage,
) : BleService {

    private val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PairedPeer>>(emptyList())
    override val connectedPeers: StateFlow<List<PairedPeer>> = _connectedPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<BleMessage>()
    override val incomingMessages: Flow<BleMessage> = _incomingMessages.asSharedFlow()

    private val _error = MutableStateFlow<BleError?>(null)
    override val error: StateFlow<BleError?> = _error.asStateFlow()

    private val peripherals = mutableMapOf<String, Peripheral>()
    private val peerInfo = mutableMapOf<String, PairedPeer>()
    private val observeJobs = mutableMapOf<String, Job>()
    private var scanJob: Job? = null
    private var reconnectJob: Job? = null

    private val serviceUuid = Uuid.parse(BleConfig.SERVICE_UUID)
    private val commandCharUuid = Uuid.parse(BleConfig.COMMAND_CHAR_UUID)
    private val stateCharUuid = Uuid.parse(BleConfig.STATE_CHAR_UUID)
    private val deviceIdCharUuid = Uuid.parse(BleConfig.DEVICE_ID_CHAR_UUID)

    private val commandChar = characteristicOf(
        service = serviceUuid,
        characteristic = commandCharUuid,
    )

    private val stateChar = characteristicOf(
        service = serviceUuid,
        characteristic = stateCharUuid,
    )

    private val assemblers = mutableMapOf<String, MessageAssembler>()

    override suspend fun startAdvertisingOrScanning() {
        startAutoReconnect()
    }

    override suspend fun stopAdvertisingOrScanning() {
        scanJob?.cancel()
        scanJob = null
        reconnectJob?.cancel()
        reconnectJob = null
    }

    override suspend fun sendMessage(message: BleMessage) {
        val chunks = message.encodeChunked()
        val snapshot = synchronized(peripherals) { peripherals.toMap() }
        for ((deviceId, p) in snapshot) {
            try {
                for (chunk in chunks) {
                    p.write(stateChar, chunk)
                }
            } catch (_: Exception) {
                handleDisconnect(deviceId)
            }
        }
    }

    override suspend fun disconnectPeer(id: String) {
        val p = synchronized(peripherals) {
            peerInfo.remove(id)
            peripherals.remove(id)
        }
        observeJobs.remove(id)?.cancel()
        assemblers.remove(id)
        try { p?.disconnect() } catch (_: Exception) {}
        updateConnectionState()
    }

    override suspend fun getPersistedPeers(): List<PairedPeer> {
        return peerStorage.load()
    }

    override suspend fun forgetPeer(id: String) {
        disconnectPeer(id)
        peerStorage.removePeer(id)
    }

    fun scanForDeviceId(targetDeviceId: String) {
        scanJob?.cancel()
        reconnectJob?.cancel()
        _error.value = null
        scanJob = scope.launch {
            _connectionState.value = BleConnectionState.Scanning
            try {
                withTimeout(BleConfig.SCAN_DURATION_MS.milliseconds * 3) {
                    connectToDevice(targetDeviceId)
                }
            } catch (_: TimeoutCancellationException) {
                _error.value = BleError.ScanTimeout(targetDeviceId)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                _error.value = BleError.BluetoothUnavailable
            } finally {
                updateConnectionState()
                startAutoReconnect()
            }
        }
    }

    private suspend fun connectToDevice(targetDeviceId: String) {
        if (synchronized(peripherals) { peripherals.containsKey(targetDeviceId) }) return

        val triedIdentifiers = mutableSetOf<Identifier>()

        while (coroutineContext.isActive) {
            val scanner = Scanner {
                filters {
                    match { services = listOf(serviceUuid) }
                }
            }

            val advertisement = scanner.advertisements.first { adv ->
                adv.identifier !in triedIdentifiers
            }

            triedIdentifiers.add(advertisement.identifier)
            _connectionState.value = BleConnectionState.Connecting
            val p = Peripheral(advertisement)

            try {
                p.connect()
                val deviceIdBytes = p.read(characteristicOf(
                    service = serviceUuid,
                    characteristic = deviceIdCharUuid,
                ))
                val deviceId = deviceIdBytes.decodeToString()

                if (deviceId == targetDeviceId) {
                    _error.value = null
                    val peer = PairedPeer(id = deviceId, name = advertisement.name ?: "Unknown Device")
                    synchronized(peripherals) {
                        peripherals[deviceId] = p
                        peerInfo[deviceId] = peer
                    }
                    assemblers[deviceId] = MessageAssembler()
                    updateConnectionState()
                    peerStorage.addPeer(peer)
                    observeMessages(deviceId, p)
                    return
                } else {
                    try { p.disconnect() } catch (_: Exception) {}
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
            }
        }
    }

    private fun startAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                val peers = peerStorage.load()
                val connectedIds = synchronized(peripherals) { peripherals.keys.toSet() }
                for (peer in peers) {
                    if (peer.id in connectedIds) continue
                    try {
                        withTimeout(BleConfig.SCAN_DURATION_MS.milliseconds) {
                            connectToDevice(peer.id)
                        }
                    } catch (_: Exception) {}
                    updateConnectionState()
                }
                delay(BleConfig.RECONNECT_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun observeMessages(deviceId: String, p: Peripheral) {
        observeJobs[deviceId]?.cancel()
        observeJobs[deviceId] = scope.launch {
            try {
                p.observe(commandChar).collect { bytes ->
                    try {
                        val assembler = assemblers[deviceId] ?: return@collect
                        val message = assembler.processChunk(bytes)
                        if (message != null) {
                            _incomingMessages.emit(message)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {}
            handleDisconnect(deviceId)
        }
    }

    private fun handleDisconnect(deviceId: String) {
        synchronized(peripherals) {
            peripherals.remove(deviceId)
            peerInfo.remove(deviceId)
        }
        observeJobs.remove(deviceId)?.cancel()
        assemblers.remove(deviceId)
        updateConnectionState()
    }

    private fun updateConnectionState() {
        val connected = synchronized(peripherals) { peerInfo.values.toList() }
        _connectedPeers.value = connected
        _connectionState.value = if (connected.isNotEmpty()) {
            BleConnectionState.Connected
        } else {
            BleConnectionState.Disconnected
        }
    }
}
