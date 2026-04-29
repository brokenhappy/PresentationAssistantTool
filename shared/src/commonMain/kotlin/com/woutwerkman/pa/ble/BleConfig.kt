package com.woutwerkman.pa.ble

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object BleConfig {
    const val SERVICE_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e0f"
    const val COMMAND_CHAR_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e10"
    const val STATE_CHAR_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e11"
    const val DEVICE_ID_CHAR_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e12"

    val RECONNECT_INTERVAL: Duration = 5.seconds
    val SCAN_DURATION: Duration = 10.seconds
    // Mobile sends heartbeat notifications so desktop detects disconnection faster than
    // the BLE supervision timeout (which can be 30s+). Timeout must be > 2× interval.
    val HEARTBEAT_INTERVAL: Duration = 500.milliseconds
    val HEARTBEAT_TIMEOUT: Duration = 2.seconds
}
