package com.woutwerkman.pa.ble

object BleConfig {
    const val SERVICE_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e0f"
    const val COMMAND_CHAR_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e10"
    const val STATE_CHAR_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e11"
    const val DEVICE_ID_CHAR_UUID = "7b3d4a1c-8e6f-4f0a-9c2d-1a3b5c7d9e12"

    const val RECONNECT_INTERVAL_MS = 5_000L
    const val SCAN_DURATION_MS = 10_000L
    const val HEARTBEAT_INTERVAL_MS = 500L
    const val HEARTBEAT_TIMEOUT_MS = 2_000L
}
