package com.woutwerkman.pa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.woutwerkman.pa.ble.AndroidBlePeripheral
import com.woutwerkman.pa.ble.PeerStorage
import com.woutwerkman.pa.platform.PlatformFileSystem
import com.woutwerkman.pa.ui.MobileApp
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val permissionsGranted = mutableStateOf(false)
    private val permanentlyDenied = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            permissionsGranted.value = true
            permanentlyDenied.value = false
        } else {
            permissionsGranted.value = false
            val denied = results.filter { !it.value }.keys
            permanentlyDenied.value = denied.any { !shouldShowRequestPermissionRationale(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = getOrCreateDeviceId()

        requestBlePermissions()

        setContent {
            AppTheme {
                if (permissionsGranted.value) {
                    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
                    val fileSystem = remember { PlatformFileSystem(filesDir.absolutePath) }
                    val peerStorage = remember { PeerStorage(fileSystem) }
                    val bleService = remember {
                        AndroidBlePeripheral(applicationContext, scope, peerStorage, deviceId)
                    }

                    MobileApp(
                        bleService = bleService,
                        deviceId = deviceId,
                        onKeepAwakeChanged = { keepAwake ->
                            if (keepAwake) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        },
                        onVibrate = { durationMs -> vibrate(durationMs) },
                    )
                } else {
                    PermissionDeniedScreen(
                        isLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                        isPermanentlyDenied = permanentlyDenied.value,
                        onRequestPermission = { requestBlePermissions() },
                        onOpenSettings = {
                            startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            ))
                        },
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(durationMs: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = getSharedPreferences("pa_device", MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    private fun requestBlePermissions() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            ))
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            permissionsGranted.value = true
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
private fun PermissionDeniedScreen(
    isLocationPermission: Boolean,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Bluetooth Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isLocationPermission) {
                    "On this Android version, Location permission is required for Bluetooth scanning. " +
                        "This app does not track your location — it is only used to discover nearby Bluetooth devices."
                } else {
                    "Presentation Assistant needs Bluetooth permission to connect with your desktop computer."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (isPermanentlyDenied) {
                Text(
                    text = "Permission was permanently denied. Please grant it in app settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) {
                    Text("Open Settings")
                }
            } else {
                Button(onClick = onRequestPermission) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
