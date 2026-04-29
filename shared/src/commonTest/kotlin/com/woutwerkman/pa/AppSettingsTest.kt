package com.woutwerkman.pa

import com.woutwerkman.pa.repository.loadAppSettings
import com.woutwerkman.pa.repository.setLastProfilePath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppSettingsTest {

    @Test
    fun loadReturnsDefaultsWhenNoFileExists() = runTest {
        assertNull(loadAppSettings(InMemoryFileSystem()).lastProfilePath)
    }

    @Test
    fun setAndLoadLastProfilePath() = runTest {
        val fs = InMemoryFileSystem()

        setLastProfilePath(fs, "/my/talk.json")

        assertEquals("/my/talk.json", loadAppSettings(fs).lastProfilePath)
    }

    @Test
    fun clearLastProfilePath() = runTest {
        val fs = InMemoryFileSystem()

        setLastProfilePath(fs, "/my/talk.json")
        setLastProfilePath(fs, null)

        assertNull(loadAppSettings(fs).lastProfilePath)
    }
}
