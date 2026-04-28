package com.woutwerkman.pa

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.woutwerkman.pa.ble.IosBlePeripheral
import com.woutwerkman.pa.ble.PeerStorage
import com.woutwerkman.pa.platform.KeepAwakeManager
import com.woutwerkman.pa.platform.PlatformFileSystem
import com.woutwerkman.pa.ui.MobileApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

fun MainViewController() = ComposeUIViewController {
    val deviceId = remember { getOrCreateDeviceId() }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val docDir = remember {
        val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        (paths.firstOrNull() as? NSURL)?.path ?: ""
    }
    val fileSystem = remember { PlatformFileSystem(docDir) }
    val peerStorage = remember { PeerStorage(fileSystem) }
    val bleService = remember { IosBlePeripheral(scope, peerStorage, deviceId) }
    val keepAwake = remember { KeepAwakeManager() }

    MobileApp(
        bleService = bleService,
        deviceId = deviceId,
        onKeepAwakeChanged = { enabled ->
            if (enabled) keepAwake.enable() else keepAwake.disable()
        },
    )
}

// Must persist across app restarts — desktop reconnects by matching this ID against stored peers.
private fun getOrCreateDeviceId(): String {
    val defaults = NSUserDefaults.standardUserDefaults
    val key = "pa_device_id"
    return defaults.stringForKey(key) ?: NSUUID().UUIDString.also {
        defaults.setObject(it, forKey = key)
    }
}
