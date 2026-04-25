package com.woutwerkman.pa.repository

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.ProfileData
import com.woutwerkman.pa.platform.PlatformFileSystem
import kotlinx.serialization.json.Json

class ProfileRepository(private val fileSystem: PlatformFileSystem) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun loadProfileFromFile(path: String): PresentationProfile {
        val text = fileSystem.readText(path)
        return json.decodeFromString<PresentationProfile>(text)
    }

    suspend fun loadProfileData(title: String): ProfileData? {
        val storagePath = profileStoragePath(title)
        if (!fileSystem.exists(storagePath)) return null
        val text = fileSystem.readText(storagePath)
        return json.decodeFromString<ProfileData>(text)
    }

    suspend fun saveProfileData(data: ProfileData) {
        val storagePath = profileStoragePath(data.profile.title)
        fileSystem.ensureParentExists(storagePath)
        fileSystem.writeText(storagePath, json.encodeToString(ProfileData.serializer(), data))
    }

    suspend fun loadOrCreateProfileData(filePath: String): ProfileData {
        val profile = loadProfileFromFile(filePath)
        val existing = loadProfileData(profile.title)
        return existing?.copy(profile = profile) ?: ProfileData(profile = profile)
    }

    private fun profileStoragePath(title: String): String {
        val hash = title.encodeToByteArray()
            .fold(0L) { acc, byte -> acc * 31 + byte.toLong() }
            .let { if (it < 0) -it else it }
            .toString(16)
        return "${fileSystem.appDataDir}/profiles/$hash.json"
    }
}
