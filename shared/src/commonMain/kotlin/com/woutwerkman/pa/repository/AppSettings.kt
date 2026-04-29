package com.woutwerkman.pa.repository

import com.woutwerkman.pa.platform.FileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppSettingsData(
    val lastProfilePath: String? = null,
)

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

private fun settingsPath(fileSystem: FileSystem): String =
    "${fileSystem.appDataDir}/settings.json"

suspend fun loadAppSettings(fileSystem: FileSystem): AppSettingsData {
    val path = settingsPath(fileSystem)
    if (!fileSystem.exists(path)) return AppSettingsData()
    return try {
        json.decodeFromString<AppSettingsData>(fileSystem.readText(path))
    } catch (_: Exception) {
        AppSettingsData()
    }
}

suspend fun saveAppSettings(fileSystem: FileSystem, settings: AppSettingsData) {
    val path = settingsPath(fileSystem)
    fileSystem.ensureParentExists(path)
    fileSystem.writeText(path, json.encodeToString(AppSettingsData.serializer(), settings))
}

suspend fun setLastProfilePath(fileSystem: FileSystem, path: String?) {
    val current = loadAppSettings(fileSystem)
    saveAppSettings(fileSystem, current.copy(lastProfilePath = path))
}
