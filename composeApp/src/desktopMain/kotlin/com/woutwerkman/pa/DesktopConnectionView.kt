package com.woutwerkman.pa

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.ble.BleConnectionState
import com.woutwerkman.pa.ble.BleError
import com.woutwerkman.pa.ble.DesktopBleService
import com.woutwerkman.pa.ble.PairedPeer
import com.woutwerkman.pa.qrscanner.QrScanResult
import com.woutwerkman.pa.qrscanner.WebcamQrScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun DesktopConnectionView(
    bleService: DesktopBleService,
    connectionState: BleConnectionState,
    connectedPeers: List<PairedPeer>,
    pairedPeers: List<PairedPeer>,
    spotlightConnected: Boolean,
    bleError: BleError? = null,
) {
    var isScanning by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }
    var webcamBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var deviceIdInput by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var peers by remember(pairedPeers) { mutableStateOf(pairedPeers) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { scanJob?.cancel() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Devices",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(16.dp))

        // --- Connected devices ---
        DeviceCard(
            name = "Logitech Spotlight",
            connected = spotlightConnected,
            statusText = if (spotlightConnected) "Connected" else "Not found (auto-detected)",
        )

        for (peer in connectedPeers) {
            DeviceCard(
                name = peer.name,
                connected = true,
                statusText = "Connected",
                onAction = {
                    scope.launch { bleService.disconnectPeer(peer.id) }
                },
                actionText = "Disconnect",
            )
        }

        if (connectionState == BleConnectionState.Scanning || connectionState == BleConnectionState.Connecting) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (connectionState == BleConnectionState.Scanning) "Scanning for device..." else "Connecting...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (bleError != null) {
            Spacer(Modifier.height(12.dp))
            DesktopBleErrorCard(bleError, onRetry = {
                when (bleError) {
                    is BleError.ScanTimeout -> bleService.scanForDeviceId(bleError.targetDeviceId)
                    is BleError.ConnectionFailed -> {}
                    else -> {}
                }
            })
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            "Pair Phone",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // --- Phone pairing ---
        if (isScanning) {
            Text(scanStatus, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            webcamBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "Webcam",
                    modifier = Modifier.size(320.dp, 240.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                scanJob?.cancel()
                isScanning = false
                webcamBitmap = null
            }) {
                Text("Cancel Scan")
            }
        } else {
            Button(onClick = {
                isScanning = true
                scanStatus = "Opening webcam..."
                scanJob = scope.launch {
                    val scanner = WebcamQrScanner()
                    scanner.scanForQrCode().collect { result ->
                        when (result) {
                            is QrScanResult.Scanning -> scanStatus = "Looking for QR code..."
                            is QrScanResult.Frame -> {
                                webcamBitmap = result.image.toComposeImageBitmap()
                            }
                            is QrScanResult.Decoded -> {
                                scanStatus = "QR code found! Connecting..."
                                isScanning = false
                                webcamBitmap = null
                                bleService.scanForDeviceId(result.text)
                            }
                            is QrScanResult.Error -> {
                                scanStatus = "Webcam error: ${result.message}"
                                isScanning = false
                                webcamBitmap = null
                            }
                        }
                    }
                }
            }) {
                Text("Scan QR Code from Phone")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Or enter device ID manually",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = deviceIdInput,
                    onValueChange = { deviceIdInput = it },
                    label = { Text("Device ID") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val id = deviceIdInput.trim()
                        if (id.isNotEmpty()) {
                            bleService.scanForDeviceId(id)
                        }
                    },
                    enabled = deviceIdInput.isNotBlank(),
                ) {
                    Text("Connect")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (peers.isNotEmpty()) {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                "Paired Phones",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val connectedIds = connectedPeers.map { it.id }.toSet()
            for (peer in peers) {
                if (peer.id in connectedIds) continue
                DeviceCard(
                    name = peer.name,
                    connected = false,
                    statusText = "Disconnected",
                    onAction = {
                        scope.launch {
                            bleService.forgetPeer(peer.id)
                            peers = bleService.getPersistedPeers()
                        }
                    },
                    actionText = "Forget",
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    name: String,
    connected: Boolean,
    statusText: String,
    onAction: (() -> Unit)? = null,
    actionText: String? = null,
) {
    Surface(
        color = if (connected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onAction != null && actionText != null) {
                TextButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
private fun DesktopBleErrorCard(error: BleError, onRetry: () -> Unit) {
    val (title, description) = when (error) {
        is BleError.ScanTimeout ->
            "Device not found" to "Make sure the phone app is open and Bluetooth is enabled on both devices."
        is BleError.BluetoothUnavailable ->
            "Bluetooth unavailable" to "Make sure your computer has Bluetooth enabled."
        is BleError.BluetoothDisabled ->
            "Bluetooth is turned off" to "Enable Bluetooth in System Settings."
        is BleError.ConnectionFailed ->
            "Connection failed" to "Found a device but could not connect. Try again."
        is BleError.PermissionDenied ->
            "Bluetooth permission required" to "Grant Bluetooth permission in System Settings."
        is BleError.AdvertisingFailed ->
            "Bluetooth error" to (error.reason ?: "An unexpected Bluetooth error occurred.")
    }

    val showRetry = error is BleError.ScanTimeout || error is BleError.ConnectionFailed

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (showRetry) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
