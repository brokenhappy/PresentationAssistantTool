package com.woutwerkman.pa.platform

expect class PlatformFileSystem(basePath: String) : FileSystem {
    override val appDataDir: String
    override suspend fun readText(path: String): String
    override suspend fun writeText(path: String, content: String)
    override suspend fun exists(path: String): Boolean
    override suspend fun ensureParentExists(path: String)
}
