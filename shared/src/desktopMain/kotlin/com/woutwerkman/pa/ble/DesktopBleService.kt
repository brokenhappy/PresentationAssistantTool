package com.woutwerkman.pa.ble

import com.juul.kable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock
import kotlin.time.Instant
import org.slf4j.LoggerFactory
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = LoggerFactory.getLogger(DesktopBleService::class.java)

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
    private val lastHeartbeat = mutableMapOf<String, Instant>()
    // Serializes multi-chunk writes so concurrent sendMessage calls don't interleave chunks.
    private val writeMutex = Mutex()

    override suspend fun startAdvertisingOrScanning() {
        startAutoReconnect()
    }

    override suspend fun stopAdvertisingOrScanning() {
        scanJob?.cancel()
        scanJob = null
        reconnectJob?.cancel()
        reconnectJob = null
    }

    override suspend fun sendMessage(message: BleMessage) = writeMutex.withLock {
        val chunks = message.encodeChunked()
        val snapshot = synchronized(peripherals) { peripherals.toMap() }
        for ((deviceId, p) in snapshot) {
            try {
                for (chunk in chunks) {
                    // WithResponse is required: iOS stateChar has properties Write+Read (0x0A).
                    // Kable's default WithoutResponse checks for property bit 0x04, which isn't set, causing silent failure.
                    p.write(stateChar, chunk, WriteType.WithResponse)
                }
            } catch (e: Exception) {
                log.warn("Write failed for device {}", deviceId, e)
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
        lastHeartbeat.remove(id)
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
                withTimeout(BleConfig.SCAN_DURATION * 3) {
                    connectToDevice(targetDeviceId)
                }
            } catch (_: TimeoutCancellationException) {
                _error.value = BleError.ScanTimeout(targetDeviceId)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (e: Exception) {
                log.warn("BLE scan failed", e)
                _error.value = BleError.BluetoothUnavailable
            } finally {
                updateConnectionState()
                startAutoReconnect()
            }
        }
    }

    private suspend fun connectToDevice(targetDeviceId: String) {
        connectToAnyDevice(setOf(targetDeviceId))
    }

    private suspend fun connectToAnyDevice(targetDeviceIds: Set<String>) {
        if (synchronized(peripherals) { targetDeviceIds.all { it in peripherals } }) return

        // Only skip identifiers confirmed to be the wrong device (different device ID).
        // Do NOT skip on connection failure — the correct device may just not be ready yet.
        // Skipping on failure causes the scanner to block waiting for a different device that never comes.
        val wrongDeviceIdentifiers = mutableSetOf<Identifier>()

        while (coroutineContext.isActive) {
            val scanner = Scanner {
                filters {
                    match { services = listOf(serviceUuid) }
                }
            }

            val advertisement = scanner.advertisements.first { adv ->
                adv.identifier !in wrongDeviceIdentifiers
            }

            _connectionState.value = BleConnectionState.Connecting
            val p = Peripheral(advertisement)

            try {
                p.connect()
                val deviceIdBytes = p.read(characteristicOf(
                    service = serviceUuid,
                    characteristic = deviceIdCharUuid,
                ))
                val deviceId = deviceIdBytes.decodeToString()

                if (deviceId in targetDeviceIds && !synchronized(peripherals) { peripherals.containsKey(deviceId) }) {
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
                    wrongDeviceIdentifiers.add(advertisement.identifier)
                    try { p.disconnect() } catch (_: Exception) {}
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (e: Exception) {
                log.debug("Connection attempt failed, retrying", e)
                try { p.disconnect() } catch (_: Exception) {}
                delay(1.seconds)
            }
        }
    }

    private fun startAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                val peers = peerStorage.load()
                val connectedIds = synchronized(peripherals) { peripherals.keys.toSet() }
                val disconnectedIds = peers.map { it.id }.filter { it !in connectedIds }.toSet()
                if (disconnectedIds.isNotEmpty()) {
                    try {
                        withTimeout(BleConfig.SCAN_DURATION) {
                            connectToAnyDevice(disconnectedIds)
                        }
                    } catch (_: Exception) {}
                    updateConnectionState()
                }
                delay(BleConfig.RECONNECT_INTERVAL)
            }
        }
    }

    private fun observeMessages(deviceId: String, p: Peripheral) {
        observeJobs[deviceId]?.cancel()
        lastHeartbeat[deviceId] = Clock.System.now()
        observeJobs[deviceId] = scope.launch {
            // Kable's observe() Flow stays active across disconnections by design,
            // so we need these separate monitors to detect disconnection.
            launch {
                p.state.first { it is State.Disconnected }
                handleDisconnect(deviceId)
            }
            launch {
                while (isActive) {
                    delay(BleConfig.HEARTBEAT_TIMEOUT)
                    val last = lastHeartbeat[deviceId] ?: break
                    if ((Clock.System.now() - last) > BleConfig.HEARTBEAT_TIMEOUT) {
                        handleDisconnect(deviceId)
                        break
                    }
                }
            }
            try {
                p.observe(commandChar).collect { bytes ->
                    if (bytes.isNotEmpty() && bytes[0] == HEARTBEAT_BYTE) {
                        lastHeartbeat[deviceId] = Clock.System.now()
                        return@collect
                    }
                    try {
                        val assembler = assemblers[deviceId] ?: return@collect
                        val message = assembler.processChunk(bytes)
                        if (message != null) {
                            _incomingMessages.emit(message)
                        }
                    } catch (e: Exception) {
                        log.debug("Failed to process BLE message from {}", deviceId, e)
                    }
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (e: Exception) {
                log.info("BLE observation ended for {}: {}", deviceId, e.message)
            }
            handleDisconnect(deviceId)
        }
    }

    private fun handleDisconnect(deviceId: String) {
        val oldPeripheral = synchronized(peripherals) {
            peerInfo.remove(deviceId)
            peripherals.remove(deviceId)
        }
        observeJobs.remove(deviceId)?.cancel()
        assemblers.remove(deviceId)
        lastHeartbeat.remove(deviceId)
        // Explicitly disconnect so macOS releases the BLE connection slot immediately.
        if (oldPeripheral != null) {
            scope.launch { try { oldPeripheral.disconnect() } catch (_: Exception) {} }
        }
        updateConnectionState()
        startAutoReconnect()
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
