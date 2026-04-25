package com.woutwerkman.pa.ui.connection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import qrcode.QRCode

private fun generateQrModules(data: String): Array<BooleanArray>? {
    return try {
        val rawData = QRCode(data).rawData
        Array(rawData.size) { row ->
            BooleanArray(rawData[row].size) { col ->
                rawData[row][col].dark
            }
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 250,
) {
    val modules = remember(data) { generateQrModules(data) }

    if (modules != null) {
        Canvas(modifier = modifier.size(sizeDp.dp)) {
            val moduleCount = modules.size
            val quietZone = 4
            val moduleSize = size.width / (moduleCount + quietZone * 2)
            val offset = quietZone * moduleSize

            drawRect(color = Color.White, size = size)

            for (row in modules.indices) {
                for (col in modules[row].indices) {
                    if (modules[row][col]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(offset + col * moduleSize, offset + row * moduleSize),
                            size = Size(moduleSize, moduleSize),
                        )
                    }
                }
            }
        }
    } else {
        androidx.compose.material3.Text(
            text = data,
            modifier = modifier,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
