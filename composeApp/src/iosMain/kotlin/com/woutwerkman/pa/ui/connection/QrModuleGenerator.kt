package com.woutwerkman.pa.ui.connection

actual fun generateQrModules(data: String): Array<BooleanArray>? {
    // CoreImage QR generation requires complex cinterop bindings;
    // the UI falls back to showing the device ID as text.
    return null
}
