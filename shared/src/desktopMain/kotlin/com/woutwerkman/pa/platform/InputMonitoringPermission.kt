package com.woutwerkman.pa.platform

import com.sun.jna.NativeLibrary

object InputMonitoringPermission {
    fun request(): Boolean {
        return try {
            val ioKit = NativeLibrary.getInstance("IOKit")
            val fn = ioKit.getFunction("IOHIDRequestAccess")
            val result = fn.invokeInt(arrayOf(1)) // kIOHIDRequestTypeListenEvent
            val processPath = ProcessHandle.current().info().command().orElse("unknown")
            println("[InputMonitoring] IOHIDRequestAccess(ListenEvent) = $result, process = $processPath")
            result != 0
        } catch (t: Throwable) {
            println("[InputMonitoring] Failed: ${t::class.simpleName}: ${t.message}")
            false
        }
    }
}
