package com.woutwerkman.pa.platform

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

class GlobalShortcutManager(private val onTrigger: () -> Unit) {

    private var listener: NativeKeyListener? = null

    var isRegistered: Boolean = false
        private set

    fun register() {
        Logger.getLogger(GlobalScreen::class.java.`package`.name).level = Level.OFF

        try {
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook()
            }
        } catch (_: Exception) {
            // Accessibility permission not granted — global shortcuts won't work
            isRegistered = false
            return
        }

        listener = object : NativeKeyListener {
            override fun nativeKeyPressed(event: NativeKeyEvent) {
                val mods = event.modifiers
                val hasCmd = mods and NativeKeyEvent.META_MASK != 0
                val hasAlt = mods and NativeKeyEvent.ALT_MASK != 0
                val hasShift = mods and NativeKeyEvent.SHIFT_MASK != 0
                if (hasCmd && hasAlt && hasShift && event.keyCode == NativeKeyEvent.VC_P) {
                    onTrigger()
                }
            }
        }

        GlobalScreen.addNativeKeyListener(listener)
        isRegistered = true
    }

    fun unregister() {
        listener?.let { GlobalScreen.removeNativeKeyListener(it) }
        listener = null
        try {
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook()
            }
        } catch (_: Exception) {}
        isRegistered = false
    }
}
