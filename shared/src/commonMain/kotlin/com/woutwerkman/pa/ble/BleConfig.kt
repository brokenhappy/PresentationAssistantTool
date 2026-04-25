package com.woutwerkman.pa.ble

object BleConfig {
    const val SERVICE_UUID = "0000a001-0000-1000-8000-00805f9b34fb"
    const val COMMAND_CHAR_UUID = "0000a002-0000-1000-8000-00805f9b34fb"
    const val STATE_CHAR_UUID = "0000a003-0000-1000-8000-00805f9b34fb"
    const val DEVICE_ID_CHAR_UUID = "0000a004-0000-1000-8000-00805f9b34fb"

    const val RECONNECT_INTERVAL_MS = 30_000L
    const val SCAN_DURATION_MS = 10_000L
}
