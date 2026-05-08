package io.github.ninbyo02.lami.ui.screens.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreferencesDataStoreIsolationTest {
    @Test
    fun instrumentation_usesAndroidTestSettingsDataStore() {
        assertEquals(
            "ollama_settings_android_test",
            resolvedSettingsDataStoreNameForTesting(),
        )
    }
}
