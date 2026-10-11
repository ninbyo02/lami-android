package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantVoicePhase(val promptPlaceholder: String?) {
    IDLE(null),
    PREPARING("音声認識を準備しています…"),
    LISTENING("聞いています…"),
}

sealed interface AssistantVoiceEvent {
    data object Listening : AssistantVoiceEvent

    data class Transcript(
        val text: String,
        val autoSubmit: Boolean = false,
    ) : AssistantVoiceEvent

    data class Failure(val message: String) : AssistantVoiceEvent
}

/** Process-local bridge from the Activity-owned recognizer to the active chat composer. */
object AssistantVoiceEvents {
    private val channel = Channel<AssistantVoiceEvent>(capacity = Channel.BUFFERED)
    private val mutablePhase = MutableStateFlow(AssistantVoicePhase.IDLE)

    val events = channel.receiveAsFlow()
    val phase = mutablePhase.asStateFlow()

    fun prepare() {
        mutablePhase.value = AssistantVoicePhase.PREPARING
    }

    fun publish(event: AssistantVoiceEvent) {
        mutablePhase.value = when (event) {
            AssistantVoiceEvent.Listening -> AssistantVoicePhase.LISTENING
            else -> AssistantVoicePhase.IDLE
        }
        channel.trySend(event)
    }
}
