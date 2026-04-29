package com.woutwerkman.pa.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.woutwerkman.pa.presentation.PresentationEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServices
import org.hid4java.HidServicesSpecification
import org.slf4j.LoggerFactory
import java.io.File

private val log = LoggerFactory.getLogger(SpotlightManager::class.java)

class SpotlightManager(
    private val scope: CoroutineScope,
    private val onEvent: (PresentationEvent) -> Unit,
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var hidServices: HidServices? = null
    private var device: HidDevice? = null
    private var activeDevIdx: Byte = 0
    private var activeFeatIdx: Byte = 0
    private var presenterCtrlIdx: Byte? = null
    private var vibrateChannel: HidppChannel? = null
    private var pollJob: Job? = null
    private var readJob: Job? = null
    private var nativeMonitor: NativeHidMonitor? = null
    private var btBleActive = false

    fun start() {
        if (pollJob != null) return
        try {
            val spec = HidServicesSpecification()
            spec.setAutoStart(false)
            hidServices = HidManager.getHidServices(spec).also { it.start() }
        } catch (e: Exception) {
            log.warn("Failed to initialize HID services", e)
            return
        }
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (device == null && nativeMonitor == null) tryConnect()
                delay(5.seconds)
            }
        }
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        pollJob?.cancel()
        pollJob = null
        nativeMonitor?.stop()
        nativeMonitor = null
        if (btBleActive) {
            try { bleLib?.spotlight_ble_cleanup() } catch (_: Exception) {}
            btBleActive = false
        }
        vibrateChannel = null
        device?.let { dev ->
            if (activeFeatIdx != 0.toByte()) {
                try { undivertButtons(dev) } catch (_: Exception) {}
            }
            try { dev.close() } catch (_: Exception) {}
        }
        device = null
        presenterCtrlIdx = null
        try { hidServices?.shutdown() } catch (_: Exception) {}
        hidServices = null
        _connected.value = false
    }

    private fun tryConnect() {
        if (tryUsbConnect()) return
        if (nativeMonitor == null) tryBtConnect()
    }

    private fun tryUsbConnect(): Boolean {
        val services = hidServices ?: return false
        val candidates = services.attachedHidDevices.filter { dev ->
            dev.vendorId.unsigned() == LOGITECH_VENDOR &&
                dev.productId.unsigned() == USB_PRODUCT &&
                dev.usagePage.unsigned() >= VENDOR_PAGE_MIN
        }
        for (candidate in candidates) {
            try {
                if (!candidate.open()) continue
                val channel = Hid4JavaChannel(candidate)
                val featIdx = findReprogControls(channel, DEV_WIRELESS)
                if (featIdx != null) {
                    nativeMonitor?.stop()
                    nativeMonitor = null
                    cleanupBtBle()
                    divertButton(candidate, DEV_WIRELESS, featIdx, CID_NEXT)
                    divertButton(candidate, DEV_WIRELESS, featIdx, CID_BACK)
                    presenterCtrlIdx = findFeatureIndex(channel, DEV_WIRELESS, 0x1A, 0x00)
                    device = candidate
                    activeDevIdx = DEV_WIRELESS
                    activeFeatIdx = featIdx
                    vibrateChannel = channel
                    _connected.value = true
                    startReadLoop(candidate, featIdx)
                    return true
                }
                candidate.close()
            } catch (e: Exception) {
                log.debug("Spotlight USB probe failed for candidate", e)
                try { candidate.close() } catch (_: Exception) {}
            }
        }
        return false
    }

    private fun tryBtConnect() {
        val monitor = NativeHidMonitor(
            vendorId = LOGITECH_VENDOR,
            productId = BT_PRODUCT,
            onConnected = { connected ->
                _connected.value = connected
                if (connected) initBtBle() else cleanupBtBle()
            },
            onInput = { usagePage, usage, value ->
                if (usagePage == KEYBOARD_PAGE && value == 1L) {
                    when (usage) {
                        KEY_RIGHT, KEY_PAGE_DOWN -> onEvent(PresentationEvent.Advance)
                        KEY_LEFT, KEY_PAGE_UP -> onEvent(PresentationEvent.GoBack)
                    }
                }
            },
        )
        if (monitor.start()) {
            nativeMonitor = monitor
        }
    }

    private fun initBtBle() {
        try {
            val lib = bleLib ?: return
            lib.spotlight_ble_init()
            btBleActive = true
        } catch (e: Exception) {
            log.warn("Failed to initialize Spotlight BLE", e)
        }
    }

    private fun cleanupBtBle() {
        if (btBleActive) {
            try { bleLib?.spotlight_ble_cleanup() } catch (_: Exception) {}
            btBleActive = false
        }
    }

    private fun findFeatureIndex(ch: HidppChannel, devIdx: Byte, featureHi: Int, featureLo: Int): Byte? {
        val msg = byteArrayOf(devIdx, IROOT_INDEX, swFunc(0), featureHi.toByte(), featureLo.toByte(), 0x00)
        ch.write(msg, 7, REPORT_SHORT)
        repeat(10) {
            val r = ch.readHidpp(2000) ?: return@repeat
            if (r.featIdx == IROOT_INDEX && r.func == 0) {
                val idx = r.params.getOrNull(0) ?: return null
                return if (idx == 0x00.toByte()) null else idx
            }
            if (r.featIdx == ERROR_FEATURE) return null
        }
        return null
    }

    private fun findReprogControls(ch: HidppChannel, devIdx: Byte): Byte? {
        return findFeatureIndex(ch, devIdx, 0x1b, 0x04)
    }

    private fun divertButton(dev: HidDevice, devIdx: Byte, featIdx: Byte, cid: Int) {
        val params = ByteArray(16)
        params[0] = (cid shr 8).toByte()
        params[1] = (cid and 0xFF).toByte()
        params[2] = 0x03 // divert=1, dvalid=1
        val msg = byteArrayOf(devIdx, featIdx, swFunc(3)) + params
        dev.write(msg, 20, REPORT_LONG)
        Hid4JavaChannel(dev).readHidpp(500)
    }

    private fun undivertButtons(dev: HidDevice) {
        for (cid in listOf(CID_NEXT, CID_BACK)) {
            val params = ByteArray(16)
            params[0] = (cid shr 8).toByte()
            params[1] = (cid and 0xFF).toByte()
            params[2] = 0x02 // divert=0, dvalid=1
            val msg = byteArrayOf(activeDevIdx, activeFeatIdx, swFunc(3)) + params
            try { dev.write(msg, 20, REPORT_LONG) } catch (_: Exception) {}
        }
    }

    suspend fun vibrate(duration: Duration) {
        val length = (duration.inWholeMilliseconds / 100).coerceIn(1, 10)
        if (btBleActive) {
            withContext(Dispatchers.IO) {
                try { bleLib?.spotlight_ble_vibrate(length.toByte()) } catch (e: Exception) { log.debug("BLE vibrate failed", e) }
            }
            delay(duration)
            return
        }
        val ch = vibrateChannel ?: return
        val pcIdx = presenterCtrlIdx ?: return
        withContext(Dispatchers.IO) {
            try {
                val params = ByteArray(16)
                params[0] = length.toByte()
                params[1] = 0xE8.toByte()
                params[2] = 0x80.toByte()
                val msg = byteArrayOf(activeDevIdx, pcIdx, swFunc(1)) + params
                ch.write(msg, 20, REPORT_LONG)
            } catch (e: Exception) { log.debug("USB vibrate failed", e) }
        }
        delay(duration)
    }

    private fun startReadLoop(dev: HidDevice, featIdx: Byte) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            var prevNext = false
            var prevBack = false
            try {
                while (isActive) {
                    val buf = ByteArray(64)
                    val n = dev.read(buf, 1000)
                    if (n < 0) error("Device read error")
                    if (n == 0) continue

                    val r = parseHidpp(buf, n) ?: continue
                    if (r.featIdx != featIdx || r.func != 0) continue

                    val pressed = parsePressedButtons(r.params)
                    val nextNow = CID_NEXT in pressed
                    val backNow = CID_BACK in pressed

                    if (nextNow && !prevNext) onEvent(PresentationEvent.Advance)
                    if (backNow && !prevBack) onEvent(PresentationEvent.GoBack)

                    prevNext = nextNow
                    prevBack = backNow
                }
            } catch (e: Exception) {
                log.info("Spotlight read loop ended: {}", e.message)
            }
            handleDisconnect()
        }
    }

    private fun parsePressedButtons(params: ByteArray): Set<Int> {
        val buttons = mutableSetOf<Int>()
        var i = 0
        while (i + 1 < params.size) {
            val cid = ((params[i].toInt() and 0xFF) shl 8) or (params[i + 1].toInt() and 0xFF)
            if (cid != 0) buttons.add(cid)
            i += 2
        }
        return buttons
    }

    private fun handleDisconnect() {
        try { device?.close() } catch (_: Exception) {}
        device = null
        vibrateChannel = null
        presenterCtrlIdx = null
        _connected.value = false
    }

    private interface HidppChannel {
        fun write(message: ByteArray, packetLength: Int, reportId: Byte)
        fun readHidpp(timeoutMs: Int): HidppMsg?
    }

    private class Hid4JavaChannel(private val dev: HidDevice) : HidppChannel {
        override fun write(message: ByteArray, packetLength: Int, reportId: Byte) {
            dev.write(message, packetLength, reportId)
        }

        override fun readHidpp(timeoutMs: Int): HidppMsg? {
            val buf = ByteArray(64)
            val n = dev.read(buf, timeoutMs)
            if (n < 0) error("Device read error")
            if (n == 0) return null
            return parseHidpp(buf, n)
        }
    }

    private class HidppMsg(val featIdx: Byte, val func: Int, val params: ByteArray)

    private fun swFunc(function: Int): Byte = ((function shl 4) or SW_ID).toByte()

    private fun Int.unsigned(): Int = this and 0xFFFF

    @Suppress("FunctionName")
    private interface SpotlightBleLib : Library {
        fun spotlight_ble_init()
        fun spotlight_ble_is_connected(): Boolean
        fun spotlight_ble_is_ready(): Boolean
        fun spotlight_ble_vibrate(duration100ms: Byte): Boolean
        fun spotlight_ble_cleanup()
    }

    companion object {
        private val bleLib: SpotlightBleLib? by lazy {
            try {
                val arch = System.getProperty("os.arch")
                val resPath = "/darwin-$arch/libspotlightble.dylib"
                val stream = SpotlightManager::class.java.getResourceAsStream(resPath) ?: return@lazy null
                val tmp = File.createTempFile("libspotlightble", ".dylib")
                tmp.deleteOnExit()
                stream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                Native.load(tmp.absolutePath, SpotlightBleLib::class.java)
            } catch (_: Exception) {
                null
            }
        }

        private const val LOGITECH_VENDOR = 0x046d
        private const val USB_PRODUCT = 0xc53e
        private const val BT_PRODUCT = 0xb503
        private const val VENDOR_PAGE_MIN = 0xFF00

        private const val KEYBOARD_PAGE = 0x07
        private const val KEY_RIGHT = 0x4F
        private const val KEY_LEFT = 0x50
        private const val KEY_PAGE_DOWN = 0x4E
        private const val KEY_PAGE_UP = 0x4B

        private const val REPORT_SHORT: Byte = 0x10
        private const val REPORT_LONG: Byte = 0x11

        private const val IROOT_INDEX: Byte = 0x00
        private val ERROR_FEATURE: Byte = 0xFF.toByte()

        private const val DEV_WIRELESS: Byte = 0x01

        private const val CID_NEXT = 0x00D9
        private const val CID_BACK = 0x00DB

        private const val SW_ID = 0x07

        private fun parseHidpp(buf: ByteArray, n: Int): HidppMsg? {
            val off = if (buf[0] == REPORT_SHORT || buf[0] == REPORT_LONG) 1 else 0
            if (n - off < 3) return null
            val featIdx = buf[off + 1]
            val fb = buf[off + 2].toInt() and 0xFF
            val paramStart = off + 3
            val params = if (n > paramStart) buf.copyOfRange(paramStart, n) else ByteArray(0)
            return HidppMsg(featIdx, fb shr 4, params)
        }
    }
}
