package io.github.ninbyo02.lami.ui.screens.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.tts.TtsEngineChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreferencesTtsEngineInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetPreferences() = runBlocking {
        SettingsPreferences(context).clearAllPreferencesForTest()
    }

    @Test
    fun initialChoiceUsesAndroidSpeech() = runBlocking {
        assertEquals(TtsEngineChoice.ANDROID, withTimeout(5_000) {
            SettingsPreferences(context).ttsEngineFlow.first()
        })
    }

    @Test
    fun everyEngineSelectionSurvivesSettingsRecreation() = runBlocking {
        for (engine in TtsEngineChoice.entries) {
            SettingsPreferences(context).setTtsEngine(engine)
            assertEquals(engine, withTimeout(5_000) {
                SettingsPreferences(context).ttsEngineFlow.first()
            })
        }
    }
}
