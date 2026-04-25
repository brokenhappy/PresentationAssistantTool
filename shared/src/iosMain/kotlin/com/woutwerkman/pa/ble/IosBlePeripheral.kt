package com.woutwerkman.pa.ble

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.CoreBluetooth.*
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy

class IosBlePeripheral(
    private val scope: CoroutineScope,
    private val peerStorage: PeerStorage,
    private val deviceId: String,
) : BleService {

    private val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PairedPeer>>(emptyList())
    override val connectedPeers: StateFlow<List<PairedPeer>> = _connectedPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<BleMessage>()
    override val incomingMessages: Flow<BleMessage> = _incomingMessages.asSharedFlow()

    private var peripheralManager: CBPeripheralManager? = null
    private var connectedCentral: CBCentral? = null
    private var commandCharacteristic: CBMutableCharacteristic? = null

    private val assembler = MessageAssembler()
    private val delegate = PeripheralDelegate()

    override suspend fun startAdvertisingOrScanning() {
        peripheralManager = CBPeripheralManager(delegate = delegate, queue = null)
    }

    override suspend fun stopAdvertisingOrScanning() {
        peripheralManager?.stopAdvertising()
        peripheralManager = null
    }

    override suspend fun sendMessage(message: BleMessage) {
        val central = connectedCentral ?: return
        val char = commandCharacteristic ?: return
        val pm = peripheralManager ?: return

        val chunks = message.encodeChunked()
        for (chunk in chunks) {
            val data = chunk.toNSData()
            pm.updateValue(data, forCharacteristic = char, onSubscribedCentrals = listOf(central))
        }
    }

    override suspend fun disconnectPeer(id: String) {
        // iOS peripheral can't forcibly disconnect a central;
        // stopping advertising will cause the central to lose the connection.
        stopAdvertisingOrScanning()
    }

    override suspend fun getPersistedPeers(): List<PairedPeer> {
        return peerStorage.load()
    }

    override suspend fun forgetPeer(id: String) {
        disconnectPeer(id)
        peerStorage.removePeer(id)
    }

    private inner class PeripheralDelegate : NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            if (peripheral.state == CBPeripheralManagerStatePoweredOn) {
                setupService(peripheral)
            }
        }

        private fun setupService(pm: CBPeripheralManager) {
            val serviceUuid = CBUUID.UUIDWithString(BleConfig.SERVICE_UUID)

            val cmdChar = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleConfig.COMMAND_CHAR_UUID),
                properties = CBCharacteristicPropertyNotify or CBCharacteristicPropertyWrite,
                value = null,
                permissions = CBAttributePermissionsWriteable,
            )
            commandCharacteristic = cmdChar

            val stateChar = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleConfig.STATE_CHAR_UUID),
                properties = CBCharacteristicPropertyWrite or CBCharacteristicPropertyRead,
                value = null,
                permissions = CBAttributePermissionsWriteable or CBAttributePermissionsReadable,
            )

            val deviceIdChar = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleConfig.DEVICE_ID_CHAR_UUID),
                properties = CBCharacteristicPropertyRead,
                value = null,
                permissions = CBAttributePermissionsReadable,
            )

            val service = CBMutableService(type = serviceUuid, primary = true)
            service.setValue(listOf(cmdChar, stateChar, deviceIdChar), forKey = "characteristics")
            pm.addService(service)
        }

        override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: platform.Foundation.NSError?) {
            if (error != null) return
            val serviceUuid = CBUUID.UUIDWithString(BleConfig.SERVICE_UUID)
            peripheral.startAdvertising(mapOf(
                CBAdvertisementDataServiceUUIDsKey to listOf(serviceUuid),
                CBAdvertisementDataLocalNameKey to "PA-iOS",
            ))
            _connectionState.value = BleConnectionState.Scanning
        }

        override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {
            if (didReceiveReadRequest.characteristic.UUID == CBUUID.UUIDWithString(BleConfig.DEVICE_ID_CHAR_UUID)) {
                val data = deviceId.encodeToByteArray().toNSData()
                didReceiveReadRequest.setValue(data, forKey = "value")
                peripheral.respondToRequest(didReceiveReadRequest, withResult = CBATTErrorSuccess)
            } else {
                peripheral.respondToRequest(didReceiveReadRequest, withResult = CBATTErrorRequestNotSupported)
            }
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didReceiveWriteRequests: List<*>,
        ) {
            for (request in didReceiveWriteRequests) {
                val req = request as? CBATTRequest ?: continue
                val value = req.value ?: continue
                val bytes = value.toByteArray()

                if (req.characteristic.UUID == CBUUID.UUIDWithString(BleConfig.STATE_CHAR_UUID)) {
                    try {
                        val message = assembler.processChunk(bytes)
                        if (message != null) {
                            scope.launch { _incomingMessages.emit(message) }
                        }
                    } catch (_: Exception) {}
                }

                peripheral.respondToRequest(req, withResult = CBATTErrorSuccess)
            }
        }

        @kotlinx.cinterop.ObjCSignatureOverride
        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            central: CBCentral,
            didSubscribeToCharacteristic: CBCharacteristic,
        ) {
            connectedCentral = central
            _connectionState.value = BleConnectionState.Connected
            val peer = PairedPeer(id = central.identifier.UUIDString, name = "Desktop")
            _connectedPeers.value = listOf(peer)
            scope.launch { peerStorage.addPeer(peer) }
        }

        @kotlinx.cinterop.ObjCSignatureOverride
        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            central: CBCentral,
            didUnsubscribeFromCharacteristic: CBCharacteristic,
        ) {
            connectedCentral = null
            _connectionState.value = BleConnectionState.Disconnected
            _connectedPeers.value = emptyList()
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}
