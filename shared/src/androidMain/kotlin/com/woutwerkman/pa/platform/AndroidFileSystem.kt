package com.woutwerkman.pa.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class PlatformFileSystem actual constructor(basePath: String) : FileSystem {
    actual override val appDataDir: String = basePath

    actual override suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        File(path).readText()
    }

    actual override suspend fun writeText(path: String, content: String) = withContext(Dispatchers.IO) {
        File(path).writeText(content)
    }

    actual override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).exists()
    }

    actual override suspend fun ensureParentExists(path: String) = withContext(Dispatchers.IO) {
        File(path).parentFile?.mkdirs()
        Unit
    }
}
