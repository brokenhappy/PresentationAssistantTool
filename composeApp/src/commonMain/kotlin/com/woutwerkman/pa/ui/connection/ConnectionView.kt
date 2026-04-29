package com.woutwerkman.pa.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.ble.BleConnectionState
import com.woutwerkman.pa.ble.BleError
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun MobileConnectionView(
    connectionState: BleConnectionState,
    connectedDeviceName: String?,
    deviceId: String,
    bleError: BleError? = null,
    onEnterDemo: () -> Unit = {},
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
                if (bleError != null) {
                    BleErrorBanner(bleError)
                    Spacer(Modifier.height(16.dp))
                } else {
                    Text(
                        text = "Waiting for connection...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                }
                Text(
                    text = "Scan this QR code from the desktop app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                QrCodeImage(data = deviceId)
                DeviceIdWithCopy(deviceId)
                Spacer(Modifier.height(24.dp))
                DemoButton(onEnterDemo)
            }
            else -> {
                if (bleError != null) {
                    BleErrorBanner(bleError)
                } else {
                    Text(
                        text = "Setting up Bluetooth...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
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
                Spacer(Modifier.height(24.dp))
                DemoButton(onEnterDemo)
            }
        }
    }
}

@Composable
private fun DemoButton(onEnterDemo: () -> Unit) {
    OutlinedButton(onClick = onEnterDemo) {
        Text("Try Demo")
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Preview the app without a desktop connection",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BleErrorBanner(error: BleError) {
    val (title, description) = when (error) {
        is BleError.BluetoothDisabled ->
            "Bluetooth is turned off" to "Turn on Bluetooth in your device settings to connect."
        is BleError.BluetoothUnavailable ->
            "Bluetooth unavailable" to "This device does not support Bluetooth Low Energy."
        is BleError.PermissionDenied ->
            "Bluetooth permission required" to "Allow Bluetooth access in Settings to connect."
        is BleError.AdvertisingFailed ->
            "Connection setup failed" to (error.reason ?: "Could not start Bluetooth advertising. Try restarting the app.")
        is BleError.ScanTimeout ->
            "Device not found" to "Make sure the desktop app is open and try again."
        is BleError.ConnectionFailed ->
            "Connection failed" to "Could not connect to the device. Try again."
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
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
