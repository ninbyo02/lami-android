package io.github.ninbyo02.lami.tts

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A resident PCM session. Implementations must finish cancellation before returning. */
internal interface LamiKokoroPcmSession {
    suspend fun synthesize(text: String): FloatArray
    fun cancel() = Unit
    suspend fun close()
}

/** Sentence synthesis overlaps playback through the existing bounded LAMI pipeline. */
internal class LamiKokoroVoiceEngine(
    private val scope: CoroutineScope,
    private val session: LamiKokoroPcmSession,
    private val playPcm: suspend (FloatArray, () -> Unit) -> Unit,
    private val onEvent: (String, String) -> Unit = { _, _ -> },
) : LamiVoiceEngine {
    private val closed = AtomicBoolean(false)
    private val speaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = speaking
    private var listener: (Boolean) -> Unit = {}

    private data class Clip(val text: String, val pcm: FloatArray)
    private val pipeline = LamiSpeechPipeline<Clip?>(scope, { busy ->
        speaking.value = busy
        listener(busy)
    }, prepare = { text ->
        onEvent(text, "synthesis_started")
        try {
            val pcm = session.synthesize(text)
            LamiPcmContract.validate(pcm)
            onEvent(text, "pcm_ready")
            Clip(text, pcm)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onEvent(text, "failure_synthesis_${failure.javaClass.simpleName}")
            null
        }
    }, play = { clip ->
        if (clip != null) try {
            playPcm(clip.pcm) { onEvent(clip.text, "play_return") }
            onEvent(clip.text, "playback_complete")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onEvent(clip.text, "failure_playback_${failure.javaClass.simpleName}")
        }
    })

    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    override fun speak(text: String) {
        if (closed.get() || text.isBlank()) return
        stop()
        enqueuePieces(text)
    }

    override fun speakQueued(text: String) {
        if (!closed.get()) enqueuePieces(text)
    }

    private fun enqueuePieces(text: String) {
        for (piece in LamiKokoroTextChunks.split(text)) pipeline.enqueue(piece)
    }

    override fun stop() {
        pipeline.stop()
        // The native flow may be blocked inside readNext; coroutine cancellation
        // alone does not guarantee that its completion handler runs promptly.
        session.cancel()
    }

    override suspend fun stopForVoiceInput() {
        stop()
        pipeline.awaitCleanup()
    }

    override fun shutdown() {
        if (!closed.compareAndSet(false, true)) return
        stop()
        scope.launch {
            try {
                pipeline.awaitCleanup()
                session.close()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                scope.cancel()
            }
        }
    }
}

/** Keep the initial clause short without splitting Latin words or decimal numbers. */
internal object LamiKokoroTextChunks {
    fun split(text: String): List<String> {
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        for ((i, char) in text.withIndex()) {
            current.append(char)
            val decimal = char == '.' && i > 0 && i + 1 < text.length &&
                text[i - 1].isDigit() && text[i + 1].isDigit()
            val sentence = char in "。！？!?\n" || char == '.' && !decimal &&
                (i + 1 == text.length || text[i + 1].isWhitespace())
            val softBoundary = char in "、，,;；" || char.isWhitespace()
            val target = if (pieces.isEmpty()) 24 else 80
            if (sentence || softBoundary && current.length >= target) {
                current.toString().trim().takeIf { it.isNotEmpty() }?.let(pieces::add)
                current.clear()
            }
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(pieces::add)
        return pieces
    }
}
