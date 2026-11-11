package io.github.ninbyo02.lami.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.ninbyo02.lami.tts.TtsEngineAvailability
import io.github.ninbyo02.lami.tts.TtsEngineChoice
import io.github.ninbyo02.lami.tts.TtsPreviewResult
import io.github.ninbyo02.lami.tts.TtsPreviewState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TtsEngineSettingsSectionInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachEngineUsesTheSameEditedJapanesePreviewText() {
        var selected by mutableStateOf(TtsEngineChoice.ANDROID)
        var text by mutableStateOf(DEFAULT_TTS_PREVIEW_TEXT)
        val plays = mutableListOf<Pair<TtsEngineChoice, String>>()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TtsEngineSettingsContent(
                        selected, { selected = it }, text, { text = it },
                        TtsPreviewState(availability = availableEngines()),
                        onPlay = { plays += selected to text }, onStop = {},
                    )
                }
            }
        }
        val commonText = "同じ文章で速度と声を比べます。よろしくお願いします。"
        compose.onNodeWithTag("tts-preview-text").performScrollTo().performTextReplacement(commonText)
        for (engine in TtsEngineChoice.entries) {
            compose.onNodeWithTag("tts-engine-${engine.storedValue}").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("tts-preview-play").performScrollTo().performClick()
        }
        compose.runOnIdle {
            assertEquals(TtsEngineChoice.entries.map { it to commonText }, plays)
        }
    }

    @Test
    fun unavailableBusyAndStoppingStatesBlockPlaybackButAllowSelection() {
        var selected by mutableStateOf(TtsEngineChoice.LAMI_QWEN3)
        var state by mutableStateOf(TtsPreviewState(availability = availableEngines().toMutableMap().apply {
            this[TtsEngineChoice.LAMI_QWEN3] = TtsEngineAvailability(TtsEngineChoice.LAMI_QWEN3, false, "音声モデルを準備してください")
            this[TtsEngineChoice.KOKORO] = TtsEngineAvailability(TtsEngineChoice.KOKORO, false, "Kokoroの音声モデルが未導入です。")
        }))
        var stops = 0
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TtsEngineSettingsContent(selected, { selected = it }, DEFAULT_TTS_PREVIEW_TEXT, {}, state,
                        onPlay = {}, onStop = { stops++ })
                }
            }
        }
        compose.onNodeWithTag("tts-preview-play").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("tts-engine-${TtsEngineChoice.KOKORO.storedValue}")
            .performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("tts-preview-play").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("tts-preview-status").assertTextEquals("Kokoroの音声モデルが未導入です。")
        compose.runOnIdle { state = TtsPreviewState(availability = availableEngines(), busy = true, activeEngine = selected) }
        compose.onNodeWithTag("tts-engine-${TtsEngineChoice.SUPERTONIC3.storedValue}")
            .performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("tts-preview-play").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("tts-preview-stop").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, stops)
            state = state.copy(stopping = true)
        }
        compose.onNodeWithTag("tts-preview-play").assertIsNotEnabled()
        compose.onNodeWithTag("tts-preview-stop").assertIsNotEnabled()
    }

    @Test
    fun comparisonHistoryShowsPlaybackNotificationAndTotalTimes() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TtsEngineSettingsContent(
                        TtsEngineChoice.ANDROID, {}, DEFAULT_TTS_PREVIEW_TEXT, {},
                        TtsPreviewState(availability = availableEngines(), results = listOf(
                            TtsPreviewResult(engine = TtsEngineChoice.ANDROID, text = DEFAULT_TTS_PREVIEW_TEXT,
                                playbackStartMs = 120, totalMs = 2500, error = null),
                        )), {}, {},
                    )
                }
            }
        }
        compose.onNodeWithText("再生開始通知まで：120 ms").performScrollTo().assertExists()
        compose.onNodeWithText("再生終了まで（生成・再生を含む）：2500 ms").performScrollTo().assertExists()
    }

    private fun availableEngines() = TtsEngineChoice.entries.associateWith {
        TtsEngineAvailability(it, true, "利用できます")
    }
}
