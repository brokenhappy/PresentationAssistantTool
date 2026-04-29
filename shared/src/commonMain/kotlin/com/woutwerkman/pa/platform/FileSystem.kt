package com.woutwerkman.pa.platform

interface FileSystem {
    val appDataDir: String
    suspend fun readText(path: String): String
    suspend fun writeText(path: String, content: String)
    suspend fun exists(path: String): Boolean
    suspend fun ensureParentExists(path: String)
}
