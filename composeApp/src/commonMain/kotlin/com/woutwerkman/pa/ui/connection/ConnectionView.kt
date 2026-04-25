package com.woutwerkman.pa.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.ble.BleConnectionState

@Composable
fun MobileConnectionView(
    connectionState: BleConnectionState,
    connectedDeviceName: String?,
    deviceId: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (connectionState) {
            BleConnectionState.Connected -> {
                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = connectedDeviceName ?: "Desktop",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            BleConnectionState.Scanning -> {
                Text(
                    text = "Waiting for connection...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Scan this QR code from the desktop app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                QrCodeImage(data = deviceId)
            }
            else -> {
                Text(
                    text = "Setting up Bluetooth...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Scan this QR code from the desktop app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                QrCodeImage(data = deviceId)
            }
        }
    }
}
