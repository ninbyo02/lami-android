package io.github.ninbyo02.lami.tts

import android.speech.tts.TextToSpeech
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AndroidTtsControllerTest {
    private fun activeId(controller: AndroidTtsController): String =
        AndroidTtsController::class.java.getDeclaredField("activeUtteranceId").apply { isAccessible = true }.get(controller) as String

    @Test fun `stop clears queued speech and ignores late completion and start`() {
        val controller = AndroidTtsController(RuntimeEnvironment.getApplication())
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        controller.speakQueued("最初です。")
        val oldId = activeId(controller)
        controller.speakQueued("停止したら読まない文章です。")
        controller.stop()
        engine.utteranceProgressListener.onDone(oldId)
        engine.utteranceProgressListener.onStart(oldId)
        assertTrue(engine.isStopped)
        assertFalse(controller.isSpeaking.value)
        assertEquals(listOf("最初です。"), engine.spokenTextList.toList())
        controller.shutdown()
    }

    @Test fun `stop before engine initialization discards pending speech`() {
        val controller = AndroidTtsController(RuntimeEnvironment.getApplication())
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        controller.speakQueued("まだ準備中です。")
        controller.stop()
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        assertTrue(engine.spokenTextList.isEmpty())
        assertFalse(controller.isSpeaking.value)
        controller.shutdown()
    }

    @Test fun `old completion cannot interrupt replacement playback`() {
        val controller = AndroidTtsController(RuntimeEnvironment.getApplication())
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        controller.speak("古い回答です。")
        val oldId = activeId(controller)
        controller.stop()
        controller.speak("新しい回答です。")
        engine.utteranceProgressListener.onDone(oldId)
        assertTrue(controller.isSpeaking.value)
        assertEquals("新しい回答です。", engine.lastSpokenText)
        controller.shutdown()
    }
}
