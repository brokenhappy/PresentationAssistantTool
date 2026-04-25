package com.woutwerkman.pa.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

actual class PlatformFileSystem actual constructor(basePath: String) {
    actual val appDataDir: String = basePath

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun readText(path: String): String {
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: ""
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun writeText(path: String, content: String) {
        (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    actual suspend fun exists(path: String): Boolean {
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun ensureParentExists(path: String) {
        val parent = path.substringBeforeLast('/')
        if (!NSFileManager.defaultManager.fileExistsAtPath(parent)) {
            NSFileManager.defaultManager.createDirectoryAtPath(
                parent,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }
}
