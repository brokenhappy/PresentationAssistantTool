package com.woutwerkman.pa.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBlePeripheral(
    private val context: Context,
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

    private var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private val serviceUuid = UUID.fromString(BleConfig.SERVICE_UUID)
    private val commandCharUuid = UUID.fromString(BleConfig.COMMAND_CHAR_UUID)
    private val stateCharUuid = UUID.fromString(BleConfig.STATE_CHAR_UUID)
    private val deviceIdCharUuid = UUID.fromString(BleConfig.DEVICE_ID_CHAR_UUID)
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val assembler = MessageAssembler()
    private var preparedWriteBuffer = ByteArray(0)

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _connectionState.value = BleConnectionState.Connected
                    val peer = PairedPeer(id = device.address, name = device.name ?: "Desktop")
                    _connectedPeers.value = listOf(peer)
                    scope.launch { peerStorage.addPeer(peer) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    _connectionState.value = BleConnectionState.Disconnected
                    _connectedPeers.value = emptyList()
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            when (characteristic.uuid) {
                deviceIdCharUuid -> {
                    val value = deviceId.encodeToByteArray()
                    val responseValue = if (offset < value.size) {
                        value.copyOfRange(offset, value.size)
                    } else {
                        ByteArray(0)
                    }
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseValue,
                    )
                }
                else -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == stateCharUuid) {
                if (preparedWrite) {
                    // Accumulate prepared write data at the specified offset
                    if (offset == 0) {
                        preparedWriteBuffer = value
                    } else {
                        if (offset > preparedWriteBuffer.size) {
                            val padded = preparedWriteBuffer.copyOf(offset)
                            preparedWriteBuffer = padded + value
                        } else {
                            val before = preparedWriteBuffer.copyOfRange(0, offset)
                            preparedWriteBuffer = before + value
                        }
                    }
                } else {
                    processReceivedBytes(value)
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            if (execute && preparedWriteBuffer.isNotEmpty()) {
                processReceivedBytes(preparedWriteBuffer)
            }
            preparedWriteBuffer = ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            if (descriptor.uuid == cccdUuid) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                )
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == cccdUuid) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }

    private fun processReceivedBytes(bytes: ByteArray) {
        try {
            val message = assembler.processChunk(bytes)
            if (message != null) {
                scope.launch { _incomingMessages.emit(message) }
            }
        } catch (_: Exception) {}
    }

    override suspend fun startAdvertisingOrScanning() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return

        gattServer = manager.openGattServer(context, gattCallback)
        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val cmdChar = BluetoothGattCharacteristic(
            commandCharUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        cmdChar.addDescriptor(BluetoothGattDescriptor(
            cccdUuid,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        ))
        service.addCharacteristic(cmdChar)

        service.addCharacteristic(BluetoothGattCharacteristic(
            stateCharUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ,
        ))

        service.addCharacteristic(BluetoothGattCharacteristic(
            deviceIdCharUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ))

        gattServer?.addService(service)

        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _connectionState.value = BleConnectionState.Scanning
            }
        }

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    override suspend fun stopAdvertisingOrScanning() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return

        advertiseCallback?.let { callback ->
            adapter.bluetoothLeAdvertiser?.stopAdvertising(callback)
        }
        advertiseCallback = null
        gattServer?.close()
        gattServer = null
    }

    @Suppress("DEPRECATION")
    override suspend fun sendMessage(message: BleMessage) {
        val device = connectedDevice ?: return
        val server = gattServer ?: return
        val service = server.getService(serviceUuid) ?: return
        val char = service.getCharacteristic(commandCharUuid) ?: return

        val chunks = message.encodeChunked()
        for (chunk in chunks) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, char, false, chunk)
            } else {
                char.value = chunk
                server.notifyCharacteristicChanged(device, char, false)
            }
        }
    }

    override suspend fun disconnectPeer(id: String) {
        val device = connectedDevice ?: return
        if (device.address == id) {
            gattServer?.cancelConnection(device)
        }
    }

    override fun getPersistedPeers(): List<PairedPeer> {
        return runBlocking { peerStorage.load() }
    }

    override suspend fun forgetPeer(id: String) {
        disconnectPeer(id)
        peerStorage.removePeer(id)
    }
}
