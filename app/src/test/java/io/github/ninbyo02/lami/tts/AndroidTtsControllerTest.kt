package io.github.ninbyo02.lami.tts

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTtsControllerTest {
    private class Capture(private val context: Context) : AndroidTtsPcmPlayback {
        val ids = mutableListOf<String>()
        val eos = mutableListOf<String>()
        val bytes = mutableListOf<ByteArray>()
        var cleanup = CompletableDeferred(Unit)
        var id: String? = null
        var file: File? = null
        var started = false
        var rejectNextBegin = false
        lateinit var completed: (String, Boolean, String?) -> Unit
        lateinit var audioStarted: (String) -> Unit
        lateinit var progress: (String, TtsStreamingProgress) -> Unit
        override fun setListener(completed: (String, Boolean, String?) -> Unit, started: (String) -> Unit,
            progress: (String, TtsStreamingProgress) -> Unit) {
            this.completed = completed; audioStarted = started; this.progress = progress
        }
        override fun target(): AudioDeviceInfo? = null
        override fun isActive() = id != null
        override fun isUsbActive() = false
        override fun begin(id: String, device: AudioDeviceInfo?): File {
            if (rejectNextBegin) {
                rejectNextBegin = false
                error("capture unavailable")
            }
            stop(); this.id = id; ids += id; started = false
            return File.createTempFile("android-tts-test", ".wav", context.cacheDir).also { file = it }
        }
        override fun beginSynthesis(id: String?, rate: Int, encoding: Int, channels: Int) = this.id == id
        override fun audioAvailable(id: String?, bytes: ByteArray): Boolean {
            if (this.id != id) return false
            this.bytes += bytes.copyOf()
            if (!started) { started = true; audioStarted(checkNotNull(id)) }
            return true
        }
        override fun synthesized(id: String?): Boolean {
            if (this.id != id) return false
            eos += checkNotNull(id)
            return true
        }
        override fun failed(id: String?, reason: String): Boolean {
            if (this.id != id) return false
            finish(checkNotNull(id), false, reason)
            return true
        }
        fun finish(id: String = checkNotNull(this.id), success: Boolean = true, error: String? = null) {
            if (this.id == id) { this.id = null; file?.delete() }
            completed(id, success, error)
        }
        override fun stop() { id = null; file?.delete() }
        override suspend fun awaitCleanup() { cleanup.await() }
        override fun shutdown() { stop() }
    }

    private fun setup(initialized: Boolean = true): Triple<AndroidTtsController, ShadowTextToSpeech, Capture> {
        val context = RuntimeEnvironment.getApplication()
        val capture = Capture(context)
        val controller = AndroidTtsController(context, capture)
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        if (initialized) engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        return Triple(controller, engine, capture)
    }

    @Test fun `captures file synthesis only and first PCM not source start triggers playback notification`() {
        val (controller, engine, capture) = setup()
        try {
            var starts = 0
            controller.setOnAudioStarted { starts++ }
            controller.speakQueued("こんにちは。")
            val id = capture.ids.single()
            assertEquals("こんにちは。", engine.lastSynthesizeToFileText)
            assertTrue("Android engine must never also play the speaker route", engine.spokenTextList.isEmpty())
            engine.utteranceProgressListener.onStart(id)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, starts)
            engine.utteranceProgressListener.onBeginSynthesis(id, 24_000, 2, 1)
            engine.utteranceProgressListener.onAudioAvailable(id, byteArrayOf(1, 0, 2, 0))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, starts)
            assertTrue(capture.eos.isEmpty())
            assertTrue(controller.isSpeaking.value)
        } finally { controller.shutdown() }
    }

    @Test fun `source EOS keeps speech busy and queued text waits for playback drain`() {
        val (controller, engine, capture) = setup()
        try {
            controller.speakQueued("最初です。")
            val first = capture.ids.single()
            controller.speakQueued("続きです。")
            controller.speakQueued("最後です。")
            engine.utteranceProgressListener.onDone(first)
            assertEquals(listOf(first), capture.eos)
            assertEquals(1, capture.ids.size)
            assertTrue(controller.isSpeaking.value)
            assertFalse(controller.lastPlaybackCompletedNormally)
            capture.finish(first)
            assertEquals(2, capture.ids.size)
            assertEquals("続きです。", engine.lastSynthesizeToFileText)
            val second = capture.ids.last()
            // Stale duplicate source/playback completion cannot advance the next sentence.
            engine.utteranceProgressListener.onDone(first)
            capture.finish(first)
            assertEquals(2, capture.ids.size)
            capture.finish(second)
            assertEquals("最後です。", engine.lastSynthesizeToFileText)
            capture.finish()
            assertFalse(controller.isSpeaking.value)
            assertTrue(controller.lastPlaybackCompletedNormally)
        } finally { controller.shutdown() }
    }

    @Test fun `stop clears queue and ignores late PCM source EOS and audio callbacks`() {
        val (controller, engine, capture) = setup()
        try {
            var starts = 0
            controller.setOnAudioStarted { starts++ }
            controller.speakQueued("最初です。")
            val old = capture.ids.single()
            controller.speakQueued("停止したら読まない文章です。")
            controller.stop()
            engine.utteranceProgressListener.onBeginSynthesis(old, 24_000, 2, 1)
            engine.utteranceProgressListener.onAudioAvailable(old, byteArrayOf(1, 0))
            engine.utteranceProgressListener.onDone(old)
            capture.audioStarted(old)
            capture.finish(old)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(engine.isStopped)
            assertFalse(controller.isSpeaking.value)
            assertTrue(capture.bytes.isEmpty())
            assertEquals(1, capture.ids.size)
            assertEquals(0, starts)
        } finally { controller.shutdown() }
    }

    @Test fun `stop before initialization discards pending synthesis`() {
        val (controller, engine, capture) = setup(initialized = false)
        try {
            controller.speakQueued("まだ準備中です。")
            controller.stop()
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)
            assertTrue(capture.ids.isEmpty())
            assertFalse(controller.isSpeaking.value)
        } finally { controller.shutdown() }
    }

    @Test fun `flush invalidates old callbacks and replaces pending audio`() {
        val (controller, engine, capture) = setup()
        try {
            controller.speakQueued("古い回答です。")
            val old = capture.ids.single()
            controller.speakQueued("古い続きです。")
            controller.speak("新しい回答です。")
            val replacement = capture.ids.last()
            engine.utteranceProgressListener.onStop(old, true)
            engine.utteranceProgressListener.onDone(old)
            capture.finish(old)
            assertTrue(controller.isSpeaking.value)
            assertEquals("新しい回答です。", engine.lastSynthesizeToFileText)
            capture.finish(replacement)
            assertEquals(2, capture.ids.size)
            assertFalse(controller.isSpeaking.value)
        } finally { controller.shutdown() }
    }

    @Test fun `silent mode blocks unverified standard speaker capture`() {
        val (controller, _, capture) = setup()
        try {
            val audio = RuntimeEnvironment.getApplication().getSystemService(AudioManager::class.java)
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            assertFalse(controller.canPlayInCurrentMode())
            controller.speak("スピーカーへ出してはいけません。")
            assertTrue(capture.ids.isEmpty())
            assertFalse(controller.isSpeaking.value)
        } finally { controller.shutdown() }
    }

    @Test fun `stop suppresses stale posted busy callbacks`() {
        val (controller, _, _) = setup()
        try {
            val callbacks = mutableListOf<Boolean>()
            controller.setOnPlaybackStateChanged(callbacks::add)
            controller.speakQueued("再生中です。")
            controller.stop()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(false), callbacks)
        } finally { controller.shutdown() }
    }

    @Test fun `microphone barrier waits until captured track cleanup completes`() = runTest {
        val (controller, _, capture) = setup()
        try {
            capture.cleanup = CompletableDeferred()
            controller.speakQueued("再生中です。")
            val microphone = async { controller.stopForVoiceInput() }
            runCurrent()
            assertFalse(microphone.isCompleted)
            capture.cleanup.complete(Unit)
            microphone.await()
        } finally { controller.shutdown() }
    }

    @Test fun `long input is bounded without splitting surrogate pairs and waits for each PCM drain`() {
        val (controller, engine, capture) = setup()
        try {
            val input = "あ".repeat(3999) + "😀" + "い".repeat(4000)
            controller.speak(input)
            val first = checkNotNull(capture.id)
            assertEquals(3999, engine.lastSynthesizeToFileText.length)
            controller.speakQueued("追記です。")
            engine.utteranceProgressListener.onDone(first)
            assertEquals(1, capture.ids.size)
            assertTrue(controller.isSpeaking.value)

            val submitted = drainTexts(engine, capture)
            assertEquals(input + "追記です。", submitted.joinToString(""))
            assertTrue(submitted.all { it.length <= TextToSpeech.getMaxSpeechInputLength() })
            assertTrue(submitted.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
            assertFalse(controller.isSpeaking.value)
        } finally { controller.shutdown() }
    }

    @Test fun `initialization preserves bounded queued requests without joining them above the API limit`() {
        val (controller, engine, capture) = setup(initialized = false)
        try {
            val first = "あ".repeat(4001)
            val second = "い".repeat(4001)
            controller.speakQueued(first)
            controller.speakQueued(second)
            assertTrue(capture.ids.isEmpty())
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)

            val submitted = drainTexts(engine, capture)
            assertEquals(listOf(4000, 1, 4000, 1), submitted.map { it.length })
            assertEquals(first + second, submitted.joinToString(""))
        } finally { controller.shutdown() }
    }

    @Test fun `flush before initialization replaces every old fragment and keeps its queued continuation`() {
        val (controller, engine, capture) = setup(initialized = false)
        try {
            controller.speakQueued("古".repeat(8001))
            val replacement = "新".repeat(4001)
            controller.speak(replacement)
            controller.speakQueued("続きです。")
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)

            assertEquals(listOf("新".repeat(4000), "新", "続きです。"), drainTexts(engine, capture))
        } finally { controller.shutdown() }
    }

    @Test fun `flush during playback discards old long tail and ignores its late completion`() {
        val (controller, engine, capture) = setup()
        try {
            controller.speak("古".repeat(8001))
            val old = checkNotNull(capture.id)
            controller.speak("新".repeat(4001))
            val replacement = checkNotNull(capture.id)
            engine.utteranceProgressListener.onDone(old)
            capture.finish(old)
            assertEquals(replacement, capture.id)
            assertEquals(listOf("新".repeat(4000), "新"), drainTexts(engine, capture))
            assertEquals(3, capture.ids.size)
        } finally { controller.shutdown() }
    }

    @Test fun `stop discards all remaining long fragments and late callbacks cannot resume them`() {
        val (controller, engine, capture) = setup()
        try {
            controller.speak("あ".repeat(8001))
            val old = checkNotNull(capture.id)
            controller.stop()
            engine.utteranceProgressListener.onDone(old)
            capture.finish(old)
            assertEquals(1, capture.ids.size)
            assertNull(capture.id)
            assertFalse(controller.isSpeaking.value)

            controller.speakQueued("次です。")
            assertEquals(listOf("次です。"), drainTexts(engine, capture))
        } finally { controller.shutdown() }
    }

    @Test fun `a fragment synthesis error reports failure and advances to the queued remainder once`() {
        val (controller, engine, capture) = setup()
        try {
            val failures = mutableListOf<String>()
            controller.setOnError(failures::add)
            controller.speak("あ".repeat(4001))
            val first = checkNotNull(capture.id)
            controller.speakQueued("次です。")
            engine.utteranceProgressListener.onError(first, TextToSpeech.ERROR_SYNTHESIS)
            engine.utteranceProgressListener.onError(first, TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, failures.size)
            assertEquals(listOf("あ", "次です。"), drainTexts(engine, capture))
            assertFalse(controller.isSpeaking.value)
        } finally { controller.shutdown() }
    }

    @Test fun `a rejected fragment start does not strand its remainder or queued continuation`() {
        val (controller, engine, capture) = setup()
        try {
            val failures = mutableListOf<String>()
            controller.setOnError(failures::add)
            capture.rejectNextBegin = true
            controller.speak("あ".repeat(4001))
            controller.speakQueued("次です。")
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, failures.size)
            assertEquals(listOf("あ", "次です。"), drainTexts(engine, capture))
            assertFalse(controller.isSpeaking.value)
        } finally { controller.shutdown() }
    }

    private fun drainTexts(engine: ShadowTextToSpeech, capture: Capture): List<String> {
        val texts = mutableListOf<String>()
        while (capture.id != null) {
            assertTrue("Queue did not finish", texts.size < 20)
            texts += engine.lastSynthesizeToFileText
            capture.finish()
        }
        return texts
    }
}
