package com.woutwerkman.pa

import com.woutwerkman.pa.platform.FileSystem

class InMemoryFileSystem(
    override val appDataDir: String = "/test",
) : FileSystem {
    private val files = mutableMapOf<String, String>()

    override suspend fun readText(path: String): String =
        files[path] ?: throw IllegalStateException("File not found: $path")

    override suspend fun writeText(path: String, content: String) {
        files[path] = content
    }

    override suspend fun exists(path: String): Boolean = path in files

    override suspend fun ensureParentExists(path: String) {}

    fun putFile(path: String, content: String) {
        files[path] = content
    }
}
