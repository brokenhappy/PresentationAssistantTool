package com.woutwerkman.pa.platform

import com.woutwerkman.pa.presentation.PresentationEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServices
import org.hid4java.HidServicesSpecification

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
    private var pollJob: Job? = null
    private var readJob: Job? = null

    fun start() {
        if (pollJob != null) return
        try {
            val spec = HidServicesSpecification()
            spec.setAutoStart(false)
            hidServices = HidManager.getHidServices(spec).also { it.start() }
        } catch (_: Exception) {
            return
        }
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (device == null) tryConnect()
                delay(5000)
            }
        }
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        pollJob?.cancel()
        pollJob = null
        device?.let { dev ->
            try { undivertButtons(dev) } catch (_: Exception) {}
            try { dev.close() } catch (_: Exception) {}
        }
        device = null
        try { hidServices?.shutdown() } catch (_: Exception) {}
        hidServices = null
        _connected.value = false
    }

    private fun tryConnect() {
        val services = hidServices ?: return
        val candidates = services.attachedHidDevices.filter { dev ->
            dev.vendorId.unsigned() == LOGITECH_VENDOR &&
                dev.productId.unsigned() in SPOTLIGHT_PRODUCTS &&
                dev.usagePage.unsigned() >= VENDOR_PAGE_MIN
        }
        for (candidate in candidates) {
            try {
                if (!candidate.open()) continue
                val devIdx = if (candidate.productId.unsigned() == USB_PRODUCT) DEV_WIRELESS else DEV_CORDED
                val featIdx = findReprogControls(candidate, devIdx)
                if (featIdx != null) {
                    divertButton(candidate, devIdx, featIdx, CID_NEXT)
                    divertButton(candidate, devIdx, featIdx, CID_BACK)
                    device = candidate
                    activeDevIdx = devIdx
                    activeFeatIdx = featIdx
                    _connected.value = true
                    startReadLoop(candidate, featIdx)
                    return
                }
                candidate.close()
            } catch (_: Exception) {
                try { candidate.close() } catch (_: Exception) {}
            }
        }
    }

    private fun findReprogControls(dev: HidDevice, devIdx: Byte): Byte? {
        val msg = byteArrayOf(devIdx, IROOT_INDEX, swFunc(0), 0x1b, 0x04, 0x00)
        dev.write(msg, 7, REPORT_SHORT)
        repeat(10) {
            val r = readHidpp(dev, 2000) ?: return@repeat
            if (r.featIdx == IROOT_INDEX && r.func == 0) {
                val idx = r.params.getOrNull(0) ?: return null
                return if (idx == 0x00.toByte()) null else idx
            }
            if (r.featIdx == ERROR_FEATURE) return null
        }
        return null
    }

    private fun divertButton(dev: HidDevice, devIdx: Byte, featIdx: Byte, cid: Int) {
        val params = ByteArray(16)
        params[0] = (cid shr 8).toByte()
        params[1] = (cid and 0xFF).toByte()
        params[2] = 0x03 // divert=1, dvalid=1
        val msg = byteArrayOf(devIdx, featIdx, swFunc(3)) + params
        dev.write(msg, 20, REPORT_LONG)
        readHidpp(dev, 500)
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
            } catch (_: Exception) {}
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
        _connected.value = false
    }

    private class HidppMsg(val featIdx: Byte, val func: Int, val params: ByteArray)

    private fun readHidpp(dev: HidDevice, timeoutMs: Int): HidppMsg? {
        val buf = ByteArray(64)
        val n = dev.read(buf, timeoutMs)
        if (n < 0) error("Device read error")
        if (n == 0) return null
        return parseHidpp(buf, n)
    }

    private fun parseHidpp(buf: ByteArray, n: Int): HidppMsg? {
        val off = if (buf[0] == REPORT_SHORT || buf[0] == REPORT_LONG) 1 else 0
        if (n - off < 3) return null

        val featIdx = buf[off + 1]
        val fb = buf[off + 2].toInt() and 0xFF
        val paramStart = off + 3
        val params = if (n > paramStart) buf.copyOfRange(paramStart, n) else ByteArray(0)
        return HidppMsg(featIdx, fb shr 4, params)
    }

    private fun swFunc(function: Int): Byte = ((function shl 4) or SW_ID).toByte()

    private fun Int.unsigned(): Int = this and 0xFFFF

    companion object {
        private const val LOGITECH_VENDOR = 0x046d
        private val SPOTLIGHT_PRODUCTS = setOf(0xc53e, 0xb503)
        private const val USB_PRODUCT = 0xc53e
        private const val VENDOR_PAGE_MIN = 0xFF00

        private const val REPORT_SHORT: Byte = 0x10
        private const val REPORT_LONG: Byte = 0x11

        private const val IROOT_INDEX: Byte = 0x00
        private val ERROR_FEATURE: Byte = 0xFF.toByte()

        private const val DEV_WIRELESS: Byte = 0x01
        private val DEV_CORDED: Byte = 0xFF.toByte()

        private const val CID_NEXT = 0x00D9
        private const val CID_BACK = 0x00DB

        private const val SW_ID = 0x01
    }
}
