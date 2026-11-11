package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uses the same real engines as chat and keeps playback-notification timing distinct from sound onset. */
class LamiTtsPreviewController internal constructor(
    scope: CoroutineScope,
    private val available: (TtsEngineChoice) -> TtsEngineAvailability,
    private val create: (TtsEngineChoice) -> LamiVoiceEngine,
    private val nowMs: () -> Long,
    private val availabilityDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context, scope: CoroutineScope) : this(
        scope,
        { LamiVoiceEngineFactory.availability(context.applicationContext, it) },
        { LamiVoiceEngineFactory.createSelected(context.applicationContext, it) },
        SystemClock::elapsedRealtime,
    )

    // Cleanup must survive the composable scope being cancelled on navigation.
    private val workScope = CoroutineScope(scope.coroutineContext.minusKey(Job) + SupervisorJob())
    private val mutableState = MutableStateFlow(TtsPreviewState())
    val state: StateFlow<TtsPreviewState> = mutableState.asStateFlow()
    private var playbackJob: Job? = null
    @Volatile private var active: LamiVoiceEngine? = null
    private var closed = false

    init { refreshAvailability() }

    fun refreshAvailability() {
        if (closed) return
        workScope.launch {
            val availability = withContext(availabilityDispatcher) {
                TtsEngineChoice.entries.associateWith { choice ->
                    runCatching { available(choice) }.getOrElse {
                        TtsEngineAvailability(choice, false, "音声モデルを確認できませんでした")
                    }
                }
            }
            mutableState.update { it.copy(availability = availability) }
        }
    }

    fun play(engine: TtsEngineChoice, text: String) {
        if (closed || state.value.busy || text.isBlank()) return
        val spokenText = text.trim()
        mutableState.update { it.copy(busy = true, stopping = false, activeEngine = engine, error = null) }
        playbackJob = workScope.launch {
            val startedAt = nowMs()
            val firstAudioAt = AtomicLong(-1L)
            val firstPcmAtNs = AtomicLong(-1L)
            val audioStartedAtNs = AtomicLong(-1L)
            val generationCompletedAtNs = AtomicLong(-1L)
            val chunkCount = AtomicInteger()
            val underruns = AtomicInteger(-1)
            var player: LamiVoiceEngine? = null
            var failureMessage: String? = null
            try {
                val readiness = withContext(availabilityDispatcher) { available(engine) }
                check(readiness.available) { readiness.message }
                val completion = CompletableDeferred<Unit>()
                val wasBusy = AtomicBoolean(false)
                player = create(engine)
                active = player
                player.setOnAudioStarted { firstAudioAt.compareAndSet(-1L, nowMs()) }
                player.setOnStreamingProgress { progress ->
                    val timestamp = progress.timestampNs.takeIf { it > 0 } ?: nowMs() * 1_000_000
                    when (progress.event) {
                        TtsStreamEvent.PCM_CHUNK -> {
                            firstPcmAtNs.compareAndSet(-1, timestamp)
                            chunkCount.incrementAndGet()
                        }
                        TtsStreamEvent.PLAYBACK_STARTED -> audioStartedAtNs.compareAndSet(-1, timestamp)
                        TtsStreamEvent.GENERATION_COMPLETED -> generationCompletedAtNs.updateAndGet { maxOf(it, timestamp) }
                        TtsStreamEvent.PLAYBACK_COMPLETED -> underruns.updateAndGet { it.coerceAtLeast(0) + progress.underruns }
                        TtsStreamEvent.GENERATION_STARTED -> Unit
                    }
                }
                player.setOnError { completion.completeExceptionally(IllegalStateException(it)) }
                player.setOnPlaybackStateChanged { busy ->
                    if (busy) wasBusy.set(true)
                    else if (wasBusy.get()) completion.complete(Unit)
                }
                player.speak(JapaneseCodeSpeechText.normalize(SpeechTextBuilder.prepareHeadingsForSpeech(spokenText)))
                completion.await()
            } catch (cancelled: CancellationException) {
                failureMessage = "停止しました"
                throw cancelled
            } catch (failure: Exception) {
                failureMessage = failure.cause?.message ?: failure.message ?: "音声を再生できませんでした"
            } finally {
                val completedAt = nowMs()
                // A sherpa/ORT call may still be running after Job cancellation.
                // Do not permit the next comparison until native work has completed.
                withContext(NonCancellable) {
                    runCatching { player?.stopForVoiceInput() }.onFailure {
                        failureMessage = failureMessage ?: "音声の終了処理に失敗しました"
                    }
                    runCatching { player?.shutdownAndAwait() }.onFailure {
                        failureMessage = failureMessage ?: "音声の終了処理に失敗しました"
                    }
                    if (active === player) active = null
                    val result = TtsPreviewResult(
                        engine = engine,
                        text = spokenText,
                        playbackStartMs = audioStartedAtNs.get().takeIf { it >= 0 }?.let {
                            (it / 1_000_000 - startedAt).coerceAtLeast(0)
                        } ?: firstAudioAt.get().takeIf { it >= 0 }?.let { it - startedAt },
                        totalMs = (completedAt - startedAt).coerceAtLeast(0),
                        error = failureMessage,
                        firstPcmMs = firstPcmAtNs.get().takeIf { it >= 0 }?.let { (it / 1_000_000 - startedAt).coerceAtLeast(0) },
                        generationCompletedMs = generationCompletedAtNs.get().takeIf { it >= 0 }?.let { (it / 1_000_000 - startedAt).coerceAtLeast(0) },
                        audioChunks = chunkCount.get(),
                        streamedBeforeGenerationCompleted = chunkCount.get() >= 2 && audioStartedAtNs.get() >= 0 &&
                            generationCompletedAtNs.get() > audioStartedAtNs.get(),
                        underruns = underruns.get().takeIf { it >= 0 },
                    )
                    mutableState.update {
                        it.copy(busy = false, stopping = false, activeEngine = null,
                            results = (it.results + result).takeLast(12), error = failureMessage)
                    }
                }
            }
        }
    }

    fun stop() {
        if (!state.value.busy || state.value.stopping) return
        mutableState.update { it.copy(stopping = true) }
        runCatching { active?.stop() }
        playbackJob?.cancel()
    }

    fun shutdown() {
        if (closed) return
        closed = true
        stop()
        val pending = playbackJob
        workScope.launch {
            pending?.join()
            workScope.cancel()
        }
    }
}
