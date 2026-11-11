package io.github.ninbyo02.lami.tts

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Serializes preferences, speech and cleanup so changing engines cannot overlap audio. */
internal class LamiSelectedVoiceEngine(
    selections: Flow<TtsEngineChoice>,
    private val create: (TtsEngineChoice) -> LamiVoiceEngine,
    private val scope: CoroutineScope,
) : LamiVoiceEngine {
    private sealed interface Command {
        data class Select(val engine: TtsEngineChoice) : Command
        data class Speak(val text: String, val replace: Boolean) : Command
        data class Busy(val generation: Long, val value: Boolean) : Command
        data class AudioStarted(val generation: Long) : Command
        data class Error(val generation: Long, val message: String) : Command
        data class Streaming(val generation: Long, val progress: TtsStreamingProgress) : Command
        data class PreferenceError(val message: String) : Command
        data class Stop(val completed: CompletableDeferred<Unit>? = null) : Command
        data object Prewarm : Command
        data object Shutdown : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val closed = AtomicBoolean()
    private val terminated = CompletableDeferred<Unit>()
    private val speaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = speaking
    @Volatile private var playbackListener: (Boolean) -> Unit = {}
    @Volatile private var audioListener: () -> Unit = {}
    @Volatile private var errorListener: (String) -> Unit = {}
    @Volatile private var streamingListener: (TtsStreamingProgress) -> Unit = {}
    @Volatile private var delegate: LamiVoiceEngine? = null
    private var selected: TtsEngineChoice? = null
    private var generation = 0L
    private var selectionFailure: String? = null
    private var pendingPrewarm = false
    private val pending = ArrayDeque<Command.Speak>()

    init {
        scope.launch {
            try {
                for (command in commands) {
                    when (command) {
                        is Command.Select -> if (selected != command.engine) {
                            if (selected != null) pending.clear()
                            selectionFailure = runCatching { stopDelegate(dispose = true) }
                                .exceptionOrNull()?.let { it.message ?: "音声の切替に失敗しました" }
                            selected = command.engine
                            selectionFailure?.let(errorListener)
                        }
                        is Command.Speak -> {
                            if (selectionFailure != null) {
                                errorListener(checkNotNull(selectionFailure))
                            } else if (selected == null) {
                                if (command.replace) pending.clear()
                                pending.addLast(command)
                            } else speakSelected(command)
                        }
                        Command.Prewarm -> if (selectionFailure == null) {
                            if (selected == null) pendingPrewarm = true else prewarmSelected()
                        }
                        is Command.Busy -> if (command.generation == generation) publishBusy(command.value)
                        is Command.AudioStarted -> if (command.generation == generation) audioListener()
                        is Command.Error -> if (command.generation == generation) errorListener(command.message)
                        is Command.Streaming -> if (command.generation == generation) streamingListener(command.progress)
                        is Command.PreferenceError -> {
                            pending.clear()
                            pendingPrewarm = false
                            selectionFailure = command.message
                            publishBusy(false)
                            errorListener(command.message)
                        }
                        is Command.Stop -> {
                            pending.clear()
                            pendingPrewarm = false
                            runCatching { stopDelegate(dispose = false) }
                                .onSuccess { command.completed?.complete(Unit) }
                                .onFailure {
                                    errorListener(it.message ?: "音声の停止に失敗しました")
                                    command.completed?.completeExceptionally(it)
                                }
                        }
                        Command.Shutdown -> break
                    }
                    if (selected != null) {
                        if (pendingPrewarm && selectionFailure == null) {
                            pendingPrewarm = false
                            prewarmSelected()
                        }
                        while (pending.isNotEmpty()) speakSelected(pending.removeFirst())
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    runCatching { stopDelegate(dispose = true) }
                    terminated.complete(Unit)
                    commands.close()
                    scope.cancel()
                }
            }
        }
        scope.launch {
            try {
                selections.distinctUntilChanged().collect { commands.send(Command.Select(it)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                commands.send(Command.PreferenceError("読み上げ方式の設定を読み込めませんでした"))
            }
        }
    }

    private fun speakSelected(request: Command.Speak) {
        try {
            selectionFailure?.let { error(it) }
            val engine = selectedDelegate()
            if (request.replace) engine.speak(request.text) else engine.speakQueued(request.text)
        } catch (failure: Exception) {
            publishBusy(false)
            errorListener(failure.cause?.message ?: failure.message ?: "音声を開始できませんでした")
        }
    }

    private fun selectedDelegate(): LamiVoiceEngine = delegate ?: create(checkNotNull(selected)).also {
        delegate = it
        attach(it)
    }

    private fun prewarmSelected() {
        // Speculative preparation may fail; an actual speech request reports its
        // own failure and can retry. Warm-up never changes the playback state.
        runCatching { selectedDelegate().prewarm() }
    }

    private fun attach(engine: LamiVoiceEngine) {
        val token = generation
        engine.setOnPlaybackStateChanged { commands.trySend(Command.Busy(token, it)) }
        engine.setOnAudioStarted { commands.trySend(Command.AudioStarted(token)) }
        engine.setOnError { commands.trySend(Command.Error(token, it)) }
        engine.setOnStreamingProgress { commands.trySend(Command.Streaming(token, it)) }
    }

    private suspend fun stopDelegate(dispose: Boolean) {
        generation += 1
        publishBusy(false)
        val engine = delegate ?: return
        try {
            engine.stopForVoiceInput()
            if (dispose) {
                engine.shutdownAndAwait()
                delegate = null
            }
        } finally {
            if (!dispose) attach(engine)
        }
    }

    private fun publishBusy(value: Boolean) {
        if (speaking.value == value) return
        speaking.value = value
        playbackListener(value)
    }

    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) { playbackListener = listener }
    override fun setOnAudioStarted(listener: () -> Unit) { audioListener = listener }
    override fun setOnError(listener: (String) -> Unit) { errorListener = listener }
    override fun setOnStreamingProgress(listener: (TtsStreamingProgress) -> Unit) { streamingListener = listener }
    override fun prewarm() { if (!closed.get()) commands.trySend(Command.Prewarm) }
    override fun speak(text: String) { if (!closed.get()) commands.trySend(Command.Speak(text, true)) }
    override fun speakQueued(text: String) { if (!closed.get()) commands.trySend(Command.Speak(text, false)) }
    override fun isInCooldown(): Boolean = delegate?.isInCooldown() ?: false
    override fun stop() { if (!closed.get()) commands.trySend(Command.Stop()) }
    override suspend fun stopForVoiceInput() {
        if (closed.get()) { terminated.await(); return }
        val completed = CompletableDeferred<Unit>()
        if (commands.trySend(Command.Stop(completed)).isSuccess) completed.await() else terminated.await()
    }
    override fun shutdown() {
        if (closed.compareAndSet(false, true)) commands.trySend(Command.Shutdown)
    }
}
