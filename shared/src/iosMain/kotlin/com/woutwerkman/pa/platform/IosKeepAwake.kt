package com.woutwerkman.pa.platform

import platform.UIKit.UIApplication

actual class KeepAwakeManager {
    actual fun enable() {
        UIApplication.sharedApplication.idleTimerDisabled = true
    }

    actual fun disable() {
        UIApplication.sharedApplication.idleTimerDisabled = false
    }
}
