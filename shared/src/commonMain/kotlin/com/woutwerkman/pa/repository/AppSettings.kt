package com.woutwerkman.pa.repository

import com.woutwerkman.pa.platform.FileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppSettingsData(
    val lastProfilePath: String? = null,
)

class AppSettings(private val fileSystem: FileSystem) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val path get() = "${fileSystem.appDataDir}/settings.json"

    suspend fun load(): AppSettingsData {
        if (!fileSystem.exists(path)) return AppSettingsData()
        return try {
            json.decodeFromString<AppSettingsData>(fileSystem.readText(path))
        } catch (_: Exception) {
            AppSettingsData()
        }
    }

    suspend fun save(settings: AppSettingsData) {
        fileSystem.ensureParentExists(path)
        fileSystem.writeText(path, json.encodeToString(AppSettingsData.serializer(), settings))
    }

    suspend fun setLastProfilePath(path: String?) {
        val current = load()
        save(current.copy(lastProfilePath = path))
    }
}
