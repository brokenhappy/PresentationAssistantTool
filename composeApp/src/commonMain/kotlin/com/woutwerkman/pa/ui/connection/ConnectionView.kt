package com.woutwerkman.pa.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.ble.BleConnectionState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

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
                DeviceIdWithCopy(deviceId)
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
                DeviceIdWithCopy(deviceId)
            }
        }
    }
}

@Composable
private fun DeviceIdWithCopy(deviceId: String) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2.seconds)
            copied = false
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Or enter this code manually:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = deviceId,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(deviceId))
                copied = true
            },
        ) {
            Text(if (copied) "Copied!" else "Copy")
        }
    }
}
