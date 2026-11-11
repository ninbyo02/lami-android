package io.github.ninbyo02.lami.tts

import io.github.ninbyo02.lami.ui.screens.home.sanitizeAssistantTextForTts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LamiSupertonicJapaneseTextTest {
    @Test
    fun expandsEngineeringQuantitiesAndSignedDecimals() {
        val input = "電圧は１２ Ｖ、電流は2.8 A、出力は+1,250W、残量は85％、温度は−０．５０℃です。"
        val spoken = "電圧は十二ボルト、電流は二点ハチアンペア、出力はプラス千二百五十ワット、残量は八十五パーセント、温度はマイナスゼロ点ゴゼロ度です。"
        assertEquals(spoken, LamiSupertonicJapaneseText.normalize(input))
        assertEquals(spoken, LamiSupertonicJapaneseText.normalize(spoken))
        assertEquals("電流はゼロ点ゴアンペアです。", LamiSupertonicJapaneseText.normalize("電流は.5 Aです。"))
    }

    @Test
    fun readsClockTimesAndMinutesWithJapaneseCounterForms() {
        assertEquals(
            "よじにじゅっぷんに出発し、しちじいっぷんきゅうびょうに計測。21時半まで19分です。",
            LamiSupertonicJapaneseText.normalize("４：２０に出発し、07:01:09に計測。21時半まで19分です。"),
        )
        assertEquals("にじゅうよじぜろふん", LamiSupertonicJapaneseText.normalize("24:00"))
    }

    @Test
    fun checksCalendarDatesBeforeExpandingNumericSeparators() {
        assertEquals(
            "2024年2月29日と2028年4月14日と２０２８年４月１４日。",
            LamiSupertonicJapaneseText.normalize("2024/02/29と2028-04-14と２０２８年４月１４日。"),
        )
        val invalid = "2025/02/29、2028-13-01、2026/08-14、2028年6月31日。"
        assertEquals(invalid, LamiSupertonicJapaneseText.normalize(invalid))
    }

    @Test
    fun preservesCodeUrlsRatiosAndUnrelatedCounters() {
        val source = "USB-12V、12V型、V字、A列、4時間、5時限、3分割、2分の1、8秒、比率は4:20、" +
            "1:20の比、24:03、13:72、v2.4:20、Ver.12V、1..5 V、`12V = 85%`、https://example.org/4:20?x=12V。"
        assertEquals(source, LamiSupertonicJapaneseText.normalize(source))
        assertEquals("カウンター 2を表示します。", LamiSupertonicJapaneseText.normalize("カウンター 2を表示します。"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun normalizesBeforeChunkingBothDirectAndQueuedRequests() = runTest {
        val received = mutableListOf<String>()
        val engine = LamiNeuralPcmVoiceEngine(
            scope = backgroundScope,
            session = object : LamiResidentPcmSession {
                override suspend fun synthesize(text: String): FloatArray {
                    received += text
                    return floatArrayOf(0.25f)
                }

                override suspend fun close() = Unit
            },
            playPcm = { _, started -> started() },
            splitText = LamiSupertonicJapaneseText::split,
        )
        val prefix = "あ".repeat(23)
        val raw = "${prefix}12 Vを加えます。"
        assertEquals(2, LamiTtsTextChunks.split(raw).size)
        engine.speak(sanitizeAssistantTextForTts(raw))
        runCurrent()
        engine.speakQueued(sanitizeAssistantTextForTts("予定は4:20です。"))
        runCurrent()
        assertEquals(listOf("${prefix}十二ボルトを加えます。", "予定はよじにじゅっぷんです。"), received)
        engine.shutdown()
    }
}
