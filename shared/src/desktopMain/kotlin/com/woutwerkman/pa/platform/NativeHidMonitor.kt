package com.woutwerkman.pa.platform

import com.sun.jna.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NativeHidMonitor(
    private val vendorId: Int,
    private val productId: Int,
    private val onConnected: (Boolean) -> Unit,
    private val onInput: (usagePage: Int, usage: Int, value: Long) -> Unit,
) {
    @Volatile
    private var runLoopRef: Pointer? = null
    private var thread: Thread? = null
    private var inputCallbackRef: Callback? = null
    private var matchCallbackRef: Callback? = null
    private var removeCallbackRef: Callback? = null
    private var eventTapCallbackRef: Callback? = null
    @Volatile
    private var deviceConnected = false

    var isRunning = false
        private set

    fun start(): Boolean {
        if (isRunning) return true

        val ready = CountDownLatch(1)
        val ok = AtomicBoolean(false)

        thread = Thread({
            try {
                val cf = Native.load("CoreFoundation", CFLib::class.java)
                val io = Native.load("IOKit", IOKitLib::class.java)
                val cg = Native.load("CoreGraphics", CGLib::class.java)
                val cfNative = NativeLibrary.getInstance("CoreFoundation")

                val defaultMode = cfNative.getGlobalVariableAddress("kCFRunLoopDefaultMode").getPointer(0)
                val dictKeyCB = cfNative.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks")
                val dictValCB = cfNative.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")

                val manager = io.IOHIDManagerCreate(Pointer.NULL, 0)

                val dict = cf.CFDictionaryCreateMutable(Pointer.NULL, 2, dictKeyCB, dictValCB)
                setDictInt(cf, dict, "VendorID", vendorId)
                setDictInt(cf, dict, "ProductID", productId)
                io.IOHIDManagerSetDeviceMatching(manager, dict)

                val inputCB = object : IOHIDValueCB {
                    override fun callback(ctx: Pointer?, ret: Int, sender: Pointer?, value: Pointer?) {
                        if (value == null) return
                        val el = io.IOHIDValueGetElement(value) ?: return
                        val page = io.IOHIDElementGetUsagePage(el)
                        val usage = io.IOHIDElementGetUsage(el)
                        val intVal = io.IOHIDValueGetIntegerValue(value)
                        onInput(page, usage, intVal)
                    }
                }
                inputCallbackRef = inputCB

                val matchCB = object : IOHIDDeviceCB {
                    override fun callback(ctx: Pointer?, ret: Int, sender: Pointer?, device: Pointer?) {
                        if (!deviceConnected) {
                            deviceConnected = true
                            onConnected(true)
                        }
                    }
                }
                matchCallbackRef = matchCB

                val removeCB = object : IOHIDDeviceCB {
                    override fun callback(ctx: Pointer?, ret: Int, sender: Pointer?, device: Pointer?) {
                        deviceConnected = false
                        onConnected(false)
                    }
                }
                removeCallbackRef = removeCB

                io.IOHIDManagerRegisterInputValueCallback(manager, inputCB, Pointer.NULL)
                io.IOHIDManagerRegisterDeviceMatchingCallback(manager, matchCB, Pointer.NULL)
                io.IOHIDManagerRegisterDeviceRemovalCallback(manager, removeCB, Pointer.NULL)

                val rl = cf.CFRunLoopGetCurrent()
                runLoopRef = rl
                io.IOHIDManagerScheduleWithRunLoop(manager, rl, defaultMode)
                io.IOHIDManagerOpen(manager, 0)

                var tapPointer: Pointer? = null

                val tapCB = object : CGEventTapCB {
                    override fun callback(proxy: Pointer?, type: Int, event: Pointer?, userInfo: Pointer?): Pointer? {
                        if (type < 0) {
                            tapPointer?.let { cg.CGEventTapEnable(it, true) }
                            return event
                        }
                        if (event == null || !deviceConnected) return event
                        if (type != CG_EVENT_KEY_DOWN) return event
                        val sourcePid = cg.CGEventGetIntegerValueField(event, CG_EVENT_SOURCE_UNIX_PID)
                        val keyCode = cg.CGEventGetIntegerValueField(event, CG_KEYBOARD_EVENT_KEYCODE)
                        if (sourcePid > 0) {
                            when (keyCode.toInt()) {
                                KEY_RIGHT_ARROW, KEY_PAGE_DOWN -> onInput(KEYBOARD_PAGE, KEY_RIGHT_USAGE, 1L)
                                KEY_LEFT_ARROW, KEY_PAGE_UP -> onInput(KEYBOARD_PAGE, KEY_LEFT_USAGE, 1L)
                            }
                        }
                        return event
                    }
                }
                eventTapCallbackRef = tapCB

                val eventMask = (1L shl CG_EVENT_KEY_DOWN)
                val tap = cg.CGEventTapCreate(
                    1, 0, 1, eventMask, tapCB, Pointer.NULL
                )
                tapPointer = tap

                if (tap != null) {
                    val rlSrc = cf.CFMachPortCreateRunLoopSource(Pointer.NULL, tap, 0)
                    cf.CFRunLoopAddSource(rl, rlSrc, defaultMode)
                    cg.CGEventTapEnable(tap, true)
                }

                isRunning = true
                ok.set(true)
                ready.countDown()

                cf.CFRunLoopRun()
            } catch (_: Exception) {
                ready.countDown()
            } finally {
                isRunning = false
            }
        }, "NativeHidMonitor").apply { isDaemon = true }
        thread!!.start()

        ready.await(3, TimeUnit.SECONDS)
        return ok.get()
    }

    fun stop() {
        isRunning = false
        runLoopRef?.let {
            try {
                Native.load("CoreFoundation", CFLib::class.java).CFRunLoopStop(it)
            } catch (_: Exception) {}
        }
        thread?.join(2000)
        thread = null
        runLoopRef = null
        inputCallbackRef = null
        matchCallbackRef = null
        removeCallbackRef = null
        eventTapCallbackRef = null
        deviceConnected = false
    }

    private fun setDictInt(cf: CFLib, dict: Pointer, key: String, value: Int) {
        val cfKey = cf.CFStringCreateWithCString(Pointer.NULL, key, 0x08000100)
        val mem = Memory(4)
        mem.setInt(0, value)
        val cfVal = cf.CFNumberCreate(Pointer.NULL, 3, mem)
        cf.CFDictionarySetValue(dict, cfKey, cfVal)
        cf.CFRelease(cfKey)
        cf.CFRelease(cfVal)
    }

    private interface IOHIDValueCB : Callback {
        fun callback(ctx: Pointer?, ret: Int, sender: Pointer?, value: Pointer?)
    }

    private interface IOHIDDeviceCB : Callback {
        fun callback(ctx: Pointer?, ret: Int, sender: Pointer?, device: Pointer?)
    }

    private interface CGEventTapCB : Callback {
        fun callback(proxy: Pointer?, type: Int, event: Pointer?, userInfo: Pointer?): Pointer?
    }

    @Suppress("FunctionName")
    private interface CFLib : Library {
        fun CFRunLoopGetCurrent(): Pointer
        fun CFRunLoopRun()
        fun CFRunLoopStop(runLoop: Pointer)
        fun CFDictionaryCreateMutable(allocator: Pointer?, capacity: Long, keyCallbacks: Pointer?, valueCallbacks: Pointer?): Pointer
        fun CFDictionarySetValue(dict: Pointer, key: Pointer, value: Pointer)
        fun CFStringCreateWithCString(allocator: Pointer?, string: String, encoding: Int): Pointer
        fun CFNumberCreate(allocator: Pointer?, type: Long, valuePtr: Pointer): Pointer
        fun CFRelease(ref: Pointer)
        fun CFMachPortCreateRunLoopSource(allocator: Pointer?, port: Pointer, order: Long): Pointer
        fun CFRunLoopAddSource(rl: Pointer, source: Pointer, mode: Pointer)
    }

    @Suppress("FunctionName")
    private interface IOKitLib : Library {
        fun IOHIDManagerCreate(allocator: Pointer?, options: Int): Pointer
        fun IOHIDManagerSetDeviceMatching(manager: Pointer, matching: Pointer?)
        fun IOHIDManagerRegisterInputValueCallback(manager: Pointer, callback: Callback, context: Pointer?)
        fun IOHIDManagerRegisterDeviceMatchingCallback(manager: Pointer, callback: Callback, context: Pointer?)
        fun IOHIDManagerRegisterDeviceRemovalCallback(manager: Pointer, callback: Callback, context: Pointer?)
        fun IOHIDManagerScheduleWithRunLoop(manager: Pointer, runLoop: Pointer, mode: Pointer)
        fun IOHIDManagerOpen(manager: Pointer, options: Int): Int
        fun IOHIDValueGetElement(value: Pointer): Pointer?
        fun IOHIDValueGetIntegerValue(value: Pointer): Long
        fun IOHIDElementGetUsagePage(element: Pointer): Int
        fun IOHIDElementGetUsage(element: Pointer): Int
    }

    @Suppress("FunctionName")
    private interface CGLib : Library {
        fun CGEventTapCreate(
            tap: Int, place: Int, options: Int,
            eventsOfInterest: Long,
            callback: Callback, userInfo: Pointer?,
        ): Pointer?

        fun CGEventTapEnable(tap: Pointer, enable: Boolean)
        fun CGEventGetIntegerValueField(event: Pointer, field: Int): Long
    }

    companion object {
        private const val CG_EVENT_KEY_DOWN = 10
        private const val CG_KEYBOARD_EVENT_KEYCODE = 9
        private const val CG_EVENT_SOURCE_UNIX_PID = 41

        private const val KEY_RIGHT_ARROW = 124
        private const val KEY_LEFT_ARROW = 123
        private const val KEY_PAGE_DOWN = 121
        private const val KEY_PAGE_UP = 116

        private const val KEYBOARD_PAGE = 0x07
        private const val KEY_RIGHT_USAGE = 0x4F
        private const val KEY_LEFT_USAGE = 0x50
    }
}
