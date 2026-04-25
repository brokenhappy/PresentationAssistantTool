package com.woutwerkman.pa.repository

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.ProfileData
import com.woutwerkman.pa.model.RunRecord
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

    suspend fun addRun(data: ProfileData, run: RunRecord): ProfileData {
        val updated = data.copy(runs = data.runs + run)
        saveProfileData(updated)
        return updated
    }

    suspend fun toggleRunInclusion(data: ProfileData, runId: String): ProfileData {
        val updated = data.copy(
            runs = data.runs.map { run ->
                if (run.id == runId) run.copy(isIncludedInStats = !run.isIncludedInStats)
                else run
            }
        )
        saveProfileData(updated)
        return updated
    }

    private fun profileStoragePath(title: String): String {
        val hash = title.encodeToByteArray()
            .fold(0L) { acc, byte -> acc * 31 + byte.toLong() }
            .let { if (it < 0) -it else it }
            .toString(16)
        return "${fileSystem.appDataDir}/profiles/$hash.json"
    }

    companion object {
        fun computeStats(runs: List<RunRecord>): BulletPointStats {
            val included = runs.filter { it.isIncludedInStats }
            if (included.isEmpty()) {
                return BulletPointStats(
                    averageDurations = emptyMap(),
                    lastThreeAverageDurations = emptyMap(),
                    totalAverage = 0,
                    totalLastThreeAverage = 0,
                    lastRunTotal = null,
                )
            }

            val allKeys = included.flatMap { it.bulletPointDurations.keys }.toSet()

            val averageDurations = allKeys.associateWith { key ->
                val durations = included.mapNotNull { it.bulletPointDurations[key] }
                if (durations.isEmpty()) 0L else durations.average().toLong()
            }

            val lastThree = included.sortedByDescending { it.timestamp }.take(3)
            val lastThreeAverageDurations = allKeys.associateWith { key ->
                val durations = lastThree.mapNotNull { it.bulletPointDurations[key] }
                if (durations.isEmpty()) 0L else durations.average().toLong()
            }

            val totalAverage = included.map { it.totalDuration }.average().toLong()
            val totalLastThreeAverage = lastThree.map { it.totalDuration }.average().toLong()
            val lastRunTotal = included.maxByOrNull { it.timestamp }?.totalDuration

            return BulletPointStats(
                averageDurations = averageDurations,
                lastThreeAverageDurations = lastThreeAverageDurations,
                totalAverage = totalAverage,
                totalLastThreeAverage = totalLastThreeAverage,
                lastRunTotal = lastRunTotal,
            )
        }
    }
}
