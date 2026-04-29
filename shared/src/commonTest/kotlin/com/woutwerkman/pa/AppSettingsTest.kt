package com.woutwerkman.pa

import com.woutwerkman.pa.repository.AppSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppSettingsTest {

    @Test
    fun loadReturnsDefaultsWhenNoFileExists() = runTest {
        val settings = AppSettings(InMemoryFileSystem())
        assertNull(settings.load().lastProfilePath)
    }

    @Test
    fun setAndLoadLastProfilePath() = runTest {
        val fs = InMemoryFileSystem()
        val settings = AppSettings(fs)

        settings.setLastProfilePath("/my/talk.json")

        assertEquals("/my/talk.json", settings.load().lastProfilePath)
    }

    @Test
    fun clearLastProfilePath() = runTest {
        val fs = InMemoryFileSystem()
        val settings = AppSettings(fs)

        settings.setLastProfilePath("/my/talk.json")
        settings.setLastProfilePath(null)

        assertNull(settings.load().lastProfilePath)
    }
}
