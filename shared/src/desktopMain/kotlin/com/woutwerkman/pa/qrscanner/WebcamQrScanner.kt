package com.woutwerkman.pa.qrscanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage

class WebcamQrScanner {

    fun scanForQrCode(): Flow<QrScanResult> = callbackFlow {
        val grabber = FFmpegFrameGrabber("0")
        grabber.format = "avfoundation"
        grabber.imageWidth = 1280
        grabber.imageHeight = 720
        grabber.frameRate = 30.0

        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )

        try {
            grabber.start()
            val converter = Java2DFrameConverter()
            val reader = MultiFormatReader()
            reader.setHints(hints)

            trySend(QrScanResult.Scanning)

            while (isActive) {
                val frame = grabber.grab()
                if (frame != null && frame.image != null) {
                    val raw = converter.convert(frame)
                    if (raw != null) {
                        val image = BufferedImage(raw.width, raw.height, BufferedImage.TYPE_INT_RGB)
                        image.createGraphics().apply {
                            drawImage(raw, 0, 0, null)
                            dispose()
                        }

                        trySend(QrScanResult.Frame(image))

                        val decoded = tryDecode(reader, image, hints)
                        if (decoded != null) {
                            trySend(QrScanResult.Decoded(decoded))
                            break
                        }
                    }
                }
                delay(100.milliseconds)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trySend(QrScanResult.Error(e.message ?: "Unknown error"))
        } finally {
            try { grabber.stop() } catch (_: Exception) {}
            try { grabber.release() } catch (_: Exception) {}
            close()
        }
    }.flowOn(Dispatchers.IO)

    private fun tryDecode(reader: MultiFormatReader, image: BufferedImage, hints: Map<DecodeHintType, Any>): String? {
        val source = BufferedImageLuminanceSource(image)

        try {
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(bitmap, hints)
            return result.text
        } catch (_: NotFoundException) {}

        try {
            val bitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val result = reader.decode(bitmap, hints)
            return result.text
        } catch (_: NotFoundException) {}

        return null
    }
}

sealed interface QrScanResult {
    data object Scanning : QrScanResult
    data class Frame(val image: BufferedImage) : QrScanResult
    data class Decoded(val text: String) : QrScanResult
    data class Error(val message: String) : QrScanResult
}
