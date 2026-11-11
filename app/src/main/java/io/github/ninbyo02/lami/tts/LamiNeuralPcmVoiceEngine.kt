package io.github.ninbyo02.lami.tts

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock

/** A resident PCM session. Implementations must finish cancellation before returning. */
internal interface LamiResidentPcmSession {
    suspend fun prewarm() = Unit
    suspend fun synthesize(text: String): FloatArray
    suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) { emit(synthesize(text)) }
    fun cancel() = Unit
    suspend fun close()
}

/** Sentence synthesis overlaps playback through the existing bounded LAMI pipeline. */
internal class LamiNeuralPcmVoiceEngine(
    private val scope: CoroutineScope,
    private val session: LamiResidentPcmSession,
    private val playPcm: suspend (FloatArray, () -> Unit) -> Unit,
    private val onEvent: (String, String) -> Unit = { _, _ -> },
    private val streamPcm: (suspend (ReceiveChannel<FloatArray>, () -> Unit, (TtsStreamingProgress) -> Unit) -> Unit)? = null,
    private val splitText: (String) -> List<String> = LamiTtsTextChunks::split,
) : LamiVoiceEngine {
    private val closed = AtomicBoolean(false)
    private val shutdownComplete = CompletableDeferred<Unit>()
    private val speaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = speaking
    private var listener: (Boolean) -> Unit = {}
    @Volatile private var audioStartedListener: () -> Unit = {}
    @Volatile private var errorListener: (String) -> Unit = {}
    @Volatile private var streamingListener: (TtsStreamingProgress) -> Unit = {}
    private val prewarmLock = Any()
    private val prewarmJobs = linkedSetOf<Job>()
    private var currentPrewarm: Job? = null
    private var prewarmReady = false
    private var prewarmEpoch = 0L

    private sealed interface Clip {
        data class Buffered(val text: String, val pcm: FloatArray) : Clip
        data class Streaming(val text: String, val chunks: Channel<FloatArray>,
            val generation: Deferred<Unit>, val playbackStarted: CompletableDeferred<Unit>) : Clip
    }
    private val pipeline = LamiSpeechPipeline<Clip?>(scope, { busy ->
        speaking.value = busy
        listener(busy)
    }, prepare = prepare@{ text ->
        // A cancelled native load can outlive its coroutine's cancellation.
        // FLUSH must finish that ownership before starting a new JNI request.
        awaitPrewarmCleanup()
        if (streamPcm != null) return@prepare prepareStream(text)
        onEvent(text, "synthesis_started")
        try {
            val pcm = session.synthesize(text)
            LamiPcmContract.validate(pcm)
            onEvent(text, "pcm_ready")
            Clip.Buffered(text, pcm)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onEvent(text, "failure_synthesis_${failure.javaClass.simpleName}")
            runCatching { errorListener("音声生成に失敗しました: ${failure.message ?: failure.javaClass.simpleName}") }
            null
        }
    }, play = { clip ->
        if (clip is Clip.Streaming) {
            playStream(clip)
        } else if (clip is Clip.Buffered) try {
            playPcm(clip.pcm) {
                onEvent(clip.text, "play_return")
                runCatching { audioStartedListener() }
            }
            onEvent(clip.text, "playback_complete")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onEvent(clip.text, "failure_playback_${failure.javaClass.simpleName}")
            runCatching { errorListener("音声再生に失敗しました: ${failure.message ?: failure.javaClass.simpleName}") }
        }
    })

    private suspend fun prepareStream(text: String): Clip.Streaming {
        val chunks = Channel<FloatArray>(1)
        val started = CompletableDeferred<Unit>()
        val firstPcmReady = CompletableDeferred<Unit>()
        // Child ownership is important: pipeline.awaitCleanup() also joins native generation.
        val generation = CoroutineScope(currentCoroutineContext()).async<Unit>(start = CoroutineStart.LAZY) {
            onEvent(text, "synthesis_started")
            report(TtsStreamEvent.GENERATION_STARTED)
            var first = true
            try {
                session.stream(text) { pcm ->
                    require(pcm.isNotEmpty() && pcm.all { it.isFinite() && it in -1f..1f })
                    chunks.send(pcm)
                    if (first) {
                        first = false
                        firstPcmReady.complete(Unit)
                        // Backpressure reaches the synchronous native callback: start
                        // the first real audio before generating the remaining waveform.
                        started.await()
                    }
                }
                check(!first) { "Streaming engine returned no PCM" }
                report(TtsStreamEvent.GENERATION_COMPLETED)
                onEvent(text, "generation_complete")
                chunks.close()
            } catch (cancelled: CancellationException) {
                chunks.cancel(cancelled)
                throw cancelled
            } catch (failure: Exception) {
                onEvent(text, "failure_synthesis_${failure.javaClass.simpleName}: ${failure.stackTraceToString()}")
                runCatching { errorListener("音声生成に失敗しました: ${failure.message ?: failure.javaClass.simpleName}") }
                chunks.close(failure)
            } finally {
                // Empty/failed generation must also release preparation. Its
                // closed channel is consumed by the normal playback error path.
                firstPcmReady.complete(Unit)
            }
        }
        generation.start()
        // A prefetched request must not enter the serial native session before
        // the current request has produced its first PCM. Otherwise it can own
        // JNI while awaiting its later playback slot, starving the current one.
        firstPcmReady.await()
        currentCoroutineContext().ensureActive()
        return Clip.Streaming(text, chunks, generation, started)
    }

    private suspend fun playStream(clip: Clip.Streaming) {
        try {
            checkNotNull(streamPcm)(clip.chunks, {
                onEvent(clip.text, "play_return")
                runCatching { audioStartedListener() }
                clip.playbackStarted.complete(Unit)
            }, { runCatching { streamingListener(it) } })
            clip.generation.await()
            onEvent(clip.text, "playback_complete")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            runCatching { errorListener("音声再生に失敗しました: ${failure.message ?: failure.javaClass.simpleName}") }
        } finally {
            clip.chunks.cancel()
            clip.playbackStarted.cancel()
            withContext(NonCancellable) { clip.generation.cancelAndJoin() }
        }
    }

    private fun report(event: TtsStreamEvent) {
        runCatching { streamingListener(TtsStreamingProgress(event, timestampNs = SystemClock.elapsedRealtimeNanos())) }
    }

    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    override fun setOnAudioStarted(listener: () -> Unit) {
        audioStartedListener = listener
    }

    override fun setOnError(listener: (String) -> Unit) {
        errorListener = listener
    }

    override fun setOnStreamingProgress(listener: (TtsStreamingProgress) -> Unit) { streamingListener = listener }

    override fun prewarm() = synchronized(prewarmLock) {
        if (closed.get() || prewarmReady || currentPrewarm != null || speaking.value) return@synchronized
        val epoch = prewarmEpoch
        val previous = prewarmJobs.toList()
        val next = scope.launch(start = CoroutineStart.LAZY) {
            val self = currentCoroutineContext()[Job]
            try {
                previous.forEach { it.join() }
                currentCoroutineContext().ensureActive()
                runCatching { onEvent("", "prewarm_started") }
                session.prewarm()
                currentCoroutineContext().ensureActive()
                synchronized(prewarmLock) {
                    if (epoch == prewarmEpoch && currentPrewarm === self) prewarmReady = true
                }
                runCatching { onEvent("", "prewarm_complete") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Warming is optional. Actual speech retries the normal session
                // load and reports an error only if that request also fails.
                runCatching { onEvent("", "failure_prewarm_${failure.javaClass.simpleName}") }
            }
        }
        currentPrewarm = next
        prewarmJobs.add(next)
        next.invokeOnCompletion {
            synchronized(prewarmLock) {
                prewarmJobs.remove(next)
                if (currentPrewarm === next) currentPrewarm = null
            }
        }
        next.start()
        Unit
    }

    private fun cancelPrewarm() = synchronized(prewarmLock) {
        prewarmEpoch++
        currentPrewarm = null
        // Keep cancelled loads registered until their native work actually exits.
        prewarmJobs.toList().forEach { it.cancel() }
    }

    private suspend fun awaitPrewarmCleanup() {
        val previous = synchronized(prewarmLock) { prewarmJobs.toList() }
        previous.forEach { it.join() }
        currentCoroutineContext().ensureActive()
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
        for (piece in splitText(text)) pipeline.enqueue(piece)
    }

    override fun stop() {
        cancelPrewarm()
        pipeline.stop()
        // The native flow may be blocked inside readNext; coroutine cancellation
        // alone does not guarantee that its completion handler runs promptly.
        session.cancel()
    }

    override suspend fun stopForVoiceInput() {
        stop()
        pipeline.awaitCleanup()
        awaitPrewarmCleanup()
    }

    override fun shutdown() {
        if (!closed.compareAndSet(false, true)) return
        try {
            stop()
        } finally {
            scope.launch {
                try {
                    pipeline.awaitCleanup()
                    awaitPrewarmCleanup()
                    session.close()
                    shutdownComplete.complete(Unit)
                } finally {
                    scope.cancel()
                }
            }.invokeOnCompletion { failure ->
                if (failure != null) shutdownComplete.completeExceptionally(failure)
            }
        }
    }

    override suspend fun shutdownAndAwait() {
        shutdown()
        shutdownComplete.await()
    }
}

/** Keep clauses bounded even when a resumed LLM burst contains no punctuation. */
internal object LamiTtsTextChunks {
    const val MAX_PIECE_CODE_POINTS = 80

    fun split(text: String): List<String> {
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        var currentCodePoints = 0
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val next = i + Character.charCount(codePoint)
            current.appendCodePoint(codePoint)
            currentCodePoints++
            val char = if (codePoint <= Char.MAX_VALUE.code) codePoint.toChar() else '\u0000'
            val decimal = char == '.' && i > 0 && i + 1 < text.length &&
                text[i - 1].isDigit() && text[i + 1].isDigit()
            val sentence = char in "。！？!?\n" || char == '.' && !decimal &&
                (next == text.length || text[next].isWhitespace())
            val softBoundary = char in "、，,;；" || char.isWhitespace()
            val target = if (pieces.isEmpty()) 24 else 80
            val hardBoundary = currentCodePoints >= MAX_PIECE_CODE_POINTS
            if (sentence || softBoundary && currentCodePoints >= target || hardBoundary) {
                // A nearby boundary keeps ordinary Latin words intact. A single
                // overlong word or unpunctuated Japanese span still needs a hard cut.
                val wordBoundary = if (hardBoundary && !sentence && !softBoundary) {
                    current.indexOfLast { it.isWhitespace() || it in "、，,;；" }
                        .takeIf { it > 0 }?.plus(1)
                } else null
                val end = wordBoundary ?: current.length
                current.substring(0, end).trim().takeIf { it.isNotEmpty() }?.let(pieces::add)
                current.delete(0, end)
                currentCodePoints = current.codePointCount(0, current.length)
            }
            i = next
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(pieces::add)
        return pieces
    }
}
