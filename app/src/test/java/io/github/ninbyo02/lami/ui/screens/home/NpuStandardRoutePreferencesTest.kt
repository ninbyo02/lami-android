package io.github.ninbyo02.lami.ui.screens.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class NpuStandardRoutePreferencesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `OFF is saved and restored`() = runTest {
        val preferences = NpuStandardRoutePreferences(createDataStore())

        preferences.setMode(NpuStandardRouteMode.OFF)

        assertEquals(NpuStandardRouteMode.OFF, preferences.getMode())
    }

    @Test
    fun `FULL is saved and restored`() = runTest {
        val preferences = NpuStandardRoutePreferences(createDataStore())

        preferences.setMode(NpuStandardRouteMode.FULL)

        assertEquals(NpuStandardRouteMode.FULL, preferences.getMode())
    }

    @Test
    fun `invalid stored value falls back to OFF`() = runTest {
        val dataStore = createDataStore()
        val preferences = NpuStandardRoutePreferences(dataStore)
        dataStore.edit { mutablePreferences ->
            mutablePreferences[NpuStandardRoutePreferences.npuStandardRouteModeKey] = "invalid"
        }

        assertEquals(NpuStandardRouteMode.OFF, preferences.getMode())
    }

    @Test
    fun `default max output tokens is 128`() = runTest {
        val preferences = NpuStandardRoutePreferences(createDataStore())

        assertEquals(128, preferences.getMaxOutputTokens())
    }

    @Test
    fun `max output tokens is saved and restored`() = runTest {
        val preferences = NpuStandardRoutePreferences(createDataStore())

        preferences.setMaxOutputTokens(512)

        assertEquals(512, preferences.getMaxOutputTokens())
    }

    @Test
    fun `invalid max output tokens falls back to 128`() = runTest {
        val dataStore = createDataStore()
        val preferences = NpuStandardRoutePreferences(dataStore)

        preferences.setMaxOutputTokens(17)

        assertEquals(128, preferences.getMaxOutputTokens())
    }

    private fun createDataStore(): DataStore<Preferences> {
        val dataStoreFile = File(
            temporaryFolder.root,
            "npu_standard_route_${System.nanoTime()}.preferences_pb",
        )
        return PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { dataStoreFile },
        )
    }
}
