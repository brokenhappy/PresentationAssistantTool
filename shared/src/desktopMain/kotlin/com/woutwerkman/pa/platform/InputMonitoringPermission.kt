package com.woutwerkman.pa.platform

import com.sun.jna.NativeLibrary

object InputMonitoringPermission {
    fun request(): Boolean {
        return try {
            val ioKit = NativeLibrary.getInstance("IOKit")
            val fn = ioKit.getFunction("IOHIDRequestAccess")
            fn.invokeInt(arrayOf(1)) != 0
        } catch (_: Throwable) {
            false
        }
    }
}
