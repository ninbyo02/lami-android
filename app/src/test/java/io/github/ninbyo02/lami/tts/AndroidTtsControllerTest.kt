package io.github.ninbyo02.lami.tts

import android.os.Looper
import android.speech.tts.TextToSpeech
import io.github.ninbyo02.lami.ui.screens.home.ResponseSpeechSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AndroidTtsControllerTest {
    private fun activeId(controller: AndroidTtsController): String =
        AndroidTtsController::class.java.getDeclaredField("activeUtteranceId").apply { isAccessible = true }.get(controller) as String

    @Test fun `streamed response and final tail play once in order on Android fallback`() {
        val controller = AndroidTtsController(RuntimeEnvironment.getApplication())
        try {
            val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)
            val session = ResponseSpeechSession().apply { begin() }
            fun receive(text: String, final: Boolean = false) {
                session.take(text, final)?.let(controller::speakQueued)
            }

            receive("こんにちは")
            assertTrue(engine.spokenTextList.isEmpty())
            receive("こんにちは。続き")
            val firstId = activeId(controller)
            receive("こんにちは。続き")
            receive("こんにちは。続きです。最後")
            receive("こんにちは。続きです。最後", final = true)
            receive("こんにちは。続きです。最後", final = true)
            assertEquals(listOf("こんにちは。"), engine.spokenTextList.toList())

            engine.utteranceProgressListener.onDone(firstId)
            val secondId = activeId(controller)
            assertTrue(controller.isSpeaking.value)
            assertEquals(listOf("こんにちは。", "続きです。"), engine.spokenTextList.toList())

            // A duplicate completion for the first sentence must not skip the second.
            engine.utteranceProgressListener.onDone(firstId)
            assertEquals(secondId, activeId(controller))
            assertEquals(2, engine.spokenTextList.size)
            engine.utteranceProgressListener.onDone(secondId)
            val tailId = activeId(controller)
            assertTrue(controller.isSpeaking.value)
            assertEquals(listOf("こんにちは。", "続きです。", "最後"), engine.spokenTextList.toList())

            engine.utteranceProgressListener.onDone(tailId)
            assertFalse(controller.isSpeaking.value)
            assertTrue(controller.lastPlaybackCompletedNormally)
        } finally {
            controller.shutdown()
        }
    }

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

    @Test fun `old interrupted stop cannot interrupt replacement playback`() {
        val controller = AndroidTtsController(RuntimeEnvironment.getApplication())
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        controller.speakQueued("古い回答です。")
        val oldId = activeId(controller)
        controller.stop()
        controller.speakQueued("新しい回答です。")

        engine.utteranceProgressListener.onStop(oldId, true)

        assertTrue(controller.isSpeaking.value)
        assertEquals("新しい回答です。", engine.lastSpokenText)
        assertEquals(listOf("古い回答です。", "新しい回答です。"), engine.spokenTextList.toList())
        controller.shutdown()
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    fun `stop suppresses stale queued playback callback`() {
        val controller = AndroidTtsController(RuntimeEnvironment.getApplication())
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        val callbacks = mutableListOf<Boolean>()
        controller.setOnPlaybackStateChanged(callbacks::add)

        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        controller.speakQueued("再生中です。")
        val stoppedId = activeId(controller)
        controller.stop()
        shadowOf(Looper.getMainLooper()).idle()

        engine.utteranceProgressListener.onStart(stoppedId)
        engine.utteranceProgressListener.onDone(stoppedId)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(false), callbacks)
        assertFalse(controller.isSpeaking.value)
        controller.shutdown()
    }

    @Test fun `interrupted stop clears queued speech and ignores late completion`() {
        val controller = AndroidTtsController(RuntimeEnvironment.getApplication())
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        controller.speakQueued("再生中です。")
        val interruptedId = activeId(controller)
        controller.speakQueued("割り込み後に読まない文章です。")

        engine.utteranceProgressListener.onStop(interruptedId, true)
        engine.utteranceProgressListener.onDone(interruptedId)

        assertFalse(controller.isSpeaking.value)
        assertEquals(listOf("再生中です。"), engine.spokenTextList.toList())
        controller.shutdown()
    }
}
