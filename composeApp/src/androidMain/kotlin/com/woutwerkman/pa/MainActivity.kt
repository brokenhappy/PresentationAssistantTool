package com.woutwerkman.pa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.woutwerkman.pa.ble.AndroidBlePeripheral
import com.woutwerkman.pa.ble.PeerStorage
import com.woutwerkman.pa.platform.PlatformFileSystem
import com.woutwerkman.pa.ui.MobileApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {

    private val permissionsGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted.value = results.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "android-unknown"

        requestBlePermissions()

        setContent {
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
                )
            }
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
