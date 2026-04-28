package com.woutwerkman.pa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.woutwerkman.pa.ui.connection.QrCodeImage
import com.woutwerkman.pa.ui.theme.AppTheme
import java.awt.Desktop
import java.io.File
import java.net.URI

@Composable
fun AttachmentWindow(attachment: String) {
    val isUrl = attachment.startsWith("http://") || attachment.startsWith("https://")
    val windowSize = if (isUrl) DpSize(180.dp, 200.dp) else DpSize(80.dp, 80.dp)

    Window(
        onCloseRequest = {},
        title = "",
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
        state = rememberWindowState(
            size = windowSize,
            position = WindowPosition(Alignment.BottomEnd),
        ),
    ) {
        AppTheme {
            WindowDraggableArea {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openAttachment(attachment) },
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (isUrl) {
                        QrCodeImage(
                            data = attachment,
                            sizeDp = 160,
                            modifier = Modifier.padding(10.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "📎",
                                style = MaterialTheme.typography.displayMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openAttachment(attachment: String) {
    try {
        val isUrl = attachment.startsWith("http://") || attachment.startsWith("https://")
        if (isUrl) {
            Desktop.getDesktop().browse(URI(attachment))
        } else {
            Desktop.getDesktop().open(File(attachment))
        }
    } catch (_: Exception) {
        // Silently ignore if the attachment can't be opened
    }
}
