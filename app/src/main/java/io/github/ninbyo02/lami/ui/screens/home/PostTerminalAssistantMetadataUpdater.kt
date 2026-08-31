package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface PostTerminalAssistantMetadataStore {
    suspend fun getMessageById(messageId: Int): Message?
    suspend fun updateMessage(message: Message)
}

internal class OllamaViewModelPostTerminalAssistantMetadataStore(
    private val viewModel: OllamaViewModel,
) : PostTerminalAssistantMetadataStore {
    override suspend fun getMessageById(messageId: Int): Message? =
        viewModel.getMessageById(messageId)

    override suspend fun updateMessage(message: Message) {
        viewModel.updateMessage(message)
    }
}

internal enum class PostTerminalAssistantMetadataUpdateOutcome {
    APPLIED,
    UNCHANGED,
    INVALID_MESSAGE_ID,
    MESSAGE_MISSING,
    NOT_ASSISTANT,
    CHAT_MISMATCH,
    TEXT_MISMATCH,
    NOT_TERMINAL,
}

internal data class PostTerminalAssistantMetadataUpdateResult(
    val outcome: PostTerminalAssistantMetadataUpdateOutcome,
    val messageId: Int,
    val status: String? = null,
) {
    val accepted: Boolean
        get() = outcome == PostTerminalAssistantMetadataUpdateOutcome.APPLIED ||
            outcome == PostTerminalAssistantMetadataUpdateOutcome.UNCHANGED
}

internal data class PostTerminalAssistantMetadataPatch(
    val completionTokens: Int? = null,
    val generationTimeMs: Long? = null,
    val generationDurationNs: Long? = null,
    val evalDurationNs: Long? = null,
    val loadDurationNs: Long? = null,
    val promptEvalDurationNs: Long? = null,
    val modelName: String? = null,
    val inputTokens: Int? = null,
    val totalTokens: Int? = null,
    val tokensPerSecond: Double? = null,
    val charsPerSecond: Double? = null,
    val tokenCountMode: String? = null,
    val inferenceNotes: String? = null,
    val inferenceTimeSec: Double? = null,
    val decodeDurationMs: Long? = null,
    val totalDurationMs: Long? = null,
    val finishReason: String? = null,
    val localSourceSummary: String? = null,
    val timeToFirstTokenMs: Long? = null,
    val imageInputCount: Int? = null,
) {
    fun applyTo(message: Message): Message = message.copy(
        completionTokens = completionTokens ?: message.completionTokens,
        generationTimeMs = generationTimeMs ?: message.generationTimeMs,
        generationDurationNs = generationDurationNs ?: message.generationDurationNs,
        evalDurationNs = evalDurationNs ?: message.evalDurationNs,
        loadDurationNs = loadDurationNs ?: message.loadDurationNs,
        promptEvalDurationNs = promptEvalDurationNs ?: message.promptEvalDurationNs,
        modelName = modelName ?: message.modelName,
        inputTokens = inputTokens ?: message.inputTokens,
        totalTokens = totalTokens ?: message.totalTokens,
        tokensPerSecond = tokensPerSecond ?: message.tokensPerSecond,
        charsPerSecond = charsPerSecond ?: message.charsPerSecond,
        tokenCountMode = tokenCountMode ?: message.tokenCountMode,
        inferenceNotes = inferenceNotes ?: message.inferenceNotes,
        inferenceTimeSec = inferenceTimeSec ?: message.inferenceTimeSec,
        decodeDurationMs = decodeDurationMs ?: message.decodeDurationMs,
        totalDurationMs = totalDurationMs ?: message.totalDurationMs,
        finishReason = finishReason ?: message.finishReason,
        localSourceSummary = localSourceSummary ?: message.localSourceSummary,
        timeToFirstTokenMs = timeToFirstTokenMs ?: message.timeToFirstTokenMs,
        imageInputCount = imageInputCount ?: message.imageInputCount,
    )

    companion object {
        fun fromInferenceStats(
            stats: InferenceStats,
            localSourceSummary: String? = stats.localSourceSummary,
            imageInputCount: Int? = stats.imageInputCount,
        ): PostTerminalAssistantMetadataPatch {
            val outputTokens = stats.outputTokens ?: stats.completionTokens
            val totalTokens = stats.totalTokens
                ?: if (stats.inputTokens != null && outputTokens != null) {
                    stats.inputTokens + outputTokens
                } else {
                    null
                }
            return PostTerminalAssistantMetadataPatch(
                completionTokens = outputTokens,
                generationTimeMs = stats.generationTimeMs
                    ?: stats.inferenceTimeSec?.times(1_000.0)?.toLong(),
                generationDurationNs = stats.generationDurationNs,
                evalDurationNs = stats.evalDurationNs,
                loadDurationNs = stats.modelLoadDurationNs,
                promptEvalDurationNs = stats.promptEvalDurationNs,
                modelName = stats.modelName ?: stats.model,
                inputTokens = stats.inputTokens,
                totalTokens = totalTokens,
                tokensPerSecond = stats.tokensPerSecond,
                charsPerSecond = stats.charsPerSecond,
                tokenCountMode = stats.tokenCountMode,
                inferenceNotes = stats.notes,
                inferenceTimeSec = stats.inferenceTimeSec,
                decodeDurationMs = stats.decodeDurationMs,
                totalDurationMs = stats.totalDurationMs,
                finishReason = stats.finishReason,
                localSourceSummary = localSourceSummary,
                timeToFirstTokenMs = stats.timeToFirstTokenMs,
                imageInputCount = imageInputCount,
            )
        }
    }
}

/**
 * Enriches a terminal assistant row without changing its body or lifecycle fields.
 *
 * All post-terminal writes are serialized here. The update is rejected when the
 * assistant identity, chat, persisted body, or terminal lifecycle no longer match.
 */
internal class PostTerminalAssistantMetadataUpdater(
    private val store: PostTerminalAssistantMetadataStore,
) {
    private val updateMutex = Mutex()

    suspend fun update(
        messageId: Int,
        expectedChatId: Int,
        expectedMessage: String,
        patch: PostTerminalAssistantMetadataPatch,
    ): PostTerminalAssistantMetadataUpdateResult = updateMutex.withLock {
        if (messageId <= 0) {
            return@withLock result(
                outcome = PostTerminalAssistantMetadataUpdateOutcome.INVALID_MESSAGE_ID,
                messageId = messageId,
            )
        }
        val current = store.getMessageById(messageId)
            ?: return@withLock result(
                outcome = PostTerminalAssistantMetadataUpdateOutcome.MESSAGE_MISSING,
                messageId = messageId,
            )
        if (current.isSendbyMe) {
            return@withLock result(
                outcome = PostTerminalAssistantMetadataUpdateOutcome.NOT_ASSISTANT,
                messageId = messageId,
                status = current.status,
            )
        }
        if (current.chatId != expectedChatId) {
            return@withLock result(
                outcome = PostTerminalAssistantMetadataUpdateOutcome.CHAT_MISMATCH,
                messageId = messageId,
                status = current.status,
            )
        }
        if (current.message != expectedMessage) {
            return@withLock result(
                outcome = PostTerminalAssistantMetadataUpdateOutcome.TEXT_MISMATCH,
                messageId = messageId,
                status = current.status,
            )
        }
        if (current.status !in MessageStatus.TERMINAL) {
            return@withLock result(
                outcome = PostTerminalAssistantMetadataUpdateOutcome.NOT_TERMINAL,
                messageId = messageId,
                status = current.status,
            )
        }
        val replacement = patch.applyTo(current)
        if (replacement == current) {
            return@withLock result(
                outcome = PostTerminalAssistantMetadataUpdateOutcome.UNCHANGED,
                messageId = messageId,
                status = current.status,
            )
        }
        checkLifecycleFieldsPreserved(current = current, replacement = replacement)
        store.updateMessage(replacement)
        result(
            outcome = PostTerminalAssistantMetadataUpdateOutcome.APPLIED,
            messageId = messageId,
            status = current.status,
        )
    }

    private fun checkLifecycleFieldsPreserved(current: Message, replacement: Message) {
        check(replacement.messageID == current.messageID)
        check(replacement.chatId == current.chatId)
        check(replacement.message == current.message)
        check(replacement.isSendbyMe == current.isSendbyMe)
        check(replacement.attachmentUriString == current.attachmentUriString)
        check(replacement.attachmentUriStringsJson == current.attachmentUriStringsJson)
        check(replacement.createdAtEpochMs == current.createdAtEpochMs)
        check(replacement.status == current.status)
        check(replacement.errorCode == current.errorCode)
        check(replacement.updatedAtEpochMs == current.updatedAtEpochMs)
    }

    private fun result(
        outcome: PostTerminalAssistantMetadataUpdateOutcome,
        messageId: Int,
        status: String? = null,
    ): PostTerminalAssistantMetadataUpdateResult = PostTerminalAssistantMetadataUpdateResult(
        outcome = outcome,
        messageId = messageId,
        status = status,
    )
}
