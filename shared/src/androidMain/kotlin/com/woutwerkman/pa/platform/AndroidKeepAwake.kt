package com.woutwerkman.pa.platform

actual class KeepAwakeManager {
    actual fun enable() {
        // Will be wired to Activity.window.addFlags(FLAG_KEEP_SCREEN_ON) via composition local
    }

    actual fun disable() {
        // Will be wired to Activity.window.clearFlags(FLAG_KEEP_SCREEN_ON) via composition local
    }
}
