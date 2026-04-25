package com.woutwerkman.pa.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class PlatformFileSystem actual constructor(basePath: String) {
    actual val appDataDir: String = basePath

    actual suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        File(path).readText()
    }

    actual suspend fun writeText(path: String, content: String) = withContext(Dispatchers.IO) {
        File(path).writeText(content)
    }

    actual suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).exists()
    }

    actual suspend fun ensureParentExists(path: String) = withContext(Dispatchers.IO) {
        File(path).parentFile?.mkdirs()
        Unit
    }
}
