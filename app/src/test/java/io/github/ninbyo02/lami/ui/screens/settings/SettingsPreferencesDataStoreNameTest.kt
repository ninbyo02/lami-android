package io.github.ninbyo02.lami.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SettingsPreferencesDataStoreNameTest {
    @Test
    fun `base Android instrumentation is not treated as android test`() {
        assertFalse(isAndroidTestInstrumentationClassName("android.app.Instrumentation"))
    }

    @Test
    fun `androidx test instrumentation is treated as android test`() {
        assertTrue(isAndroidTestInstrumentationClassName("androidx.test.runner.MonitoringInstrumentation"))
        assertTrue(isAndroidTestInstrumentationClassName("androidx.test.runner.AndroidJUnitRunner"))
    }

    @Test
    fun `latest misdetected test datastore is selected for migration`() {
        val dir = Files.createTempDirectory("lami-settings-datastore-test").toFile()
        try {
            val older = dir.resolve("ollama_settings_android_test_run_100.preferences_pb")
            val newer = dir.resolve("ollama_settings_android_test_run_200.preferences_pb")
            val ignored = dir.resolve("ollama_settings.preferences_pb")
            older.writeText("older")
            newer.writeText("newer")
            ignored.writeText("normal")
            older.setLastModified(1_000L)
            newer.setLastModified(2_000L)

            assertEquals(newer, findLatestMisdetectedInstrumentationDataStore(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
