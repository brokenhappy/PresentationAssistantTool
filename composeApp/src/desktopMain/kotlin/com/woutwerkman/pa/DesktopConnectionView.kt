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
            text = "Device Connection",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(16.dp))

        if (connectedPeers.isNotEmpty()) {
            for (peer in connectedPeers) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Connected: ${peer.name}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            scope.launch { bleService.disconnectPeer(peer.id) }
                        }) {
                            Text("Disconnect")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (connectionState == BleConnectionState.Scanning || connectionState == BleConnectionState.Connecting) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                if (connectionState == BleConnectionState.Scanning) "Scanning for device..." else "Connecting...",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
        }

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

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(24.dp))

        if (peers.isNotEmpty()) {
            Text(
                "Paired Devices",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val connectedIds = connectedPeers.map { it.id }.toSet()
            for (peer in peers) {
                if (peer.id in connectedIds) continue
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            peer.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            scope.launch {
                                bleService.forgetPeer(peer.id)
                                peers = bleService.getPersistedPeers()
                            }
                        }) {
                            Text("Forget", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
