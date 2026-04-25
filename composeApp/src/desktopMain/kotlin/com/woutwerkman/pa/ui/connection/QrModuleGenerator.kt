package com.woutwerkman.pa.ui.connection

import qrcode.QRCode

actual fun generateQrModules(data: String): Array<BooleanArray>? {
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
