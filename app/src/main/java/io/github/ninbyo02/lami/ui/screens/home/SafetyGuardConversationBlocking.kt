package io.github.ninbyo02.lami.ui.screens.home

import java.util.Locale

internal const val SAFETY_GUARD_BLOCKED_USER_MESSAGE =
    "安全停止後のため、この会話では続けて生成できません。新しい会話で続けてください。"

internal data class SafetyGuardConversationBlock(
    val chatId: Int,
    val blocked: Boolean = true,
    val lastSafetyStage: String = MEMORY_STAGE_SAFETY_GUARD_TRIGGERED,
    val reasonCode: String,
    val failureStage: String,
    val stopReason: String,
)

internal data class GuardedGenerationDecision<T>(
    val generated: Boolean,
    val value: T?,
    val blockedMessage: String? = null,
)

internal enum class ExistingLocalGenerationJobPolicy {
    START_NEW,
    CANCEL_STALE_AND_WAIT,
    ALREADY_RUNNING,
}

internal fun resolveExistingLocalGenerationJobPolicy(
    isLocalInferenceRunning: Boolean,
    existingJobActive: Boolean,
): ExistingLocalGenerationJobPolicy = when {
    existingJobActive -> ExistingLocalGenerationJobPolicy.CANCEL_STALE_AND_WAIT
    isLocalInferenceRunning -> ExistingLocalGenerationJobPolicy.ALREADY_RUNNING
    else -> ExistingLocalGenerationJobPolicy.START_NEW
}

internal fun isSafetyGuardTriggered(
    reasonCode: String,
    failureStage: String = "",
    stopReason: String = "",
): Boolean {
    val reason = reasonCode.lowercase(Locale.ROOT)
    val stage = failureStage.lowercase(Locale.ROOT)
    val stop = stopReason.lowercase(Locale.ROOT)
    return reason.startsWith("invalid_prompt") ||
        reason.startsWith("gate_blocked") ||
        reason == "duplicate_run_blocked" ||
        stage == "prompt_validation" ||
        "guard" in reason ||
        "guard" in stage ||
        "guard" in stop
}

internal fun safetyGuardConversationBlockOrNull(
    chatId: Int?,
    reasonCode: String,
    failureStage: String,
    stopReason: String,
): SafetyGuardConversationBlock? {
    val resolvedChatId = chatId ?: return null
    if (!isSafetyGuardTriggered(
            reasonCode = reasonCode,
            failureStage = failureStage,
            stopReason = stopReason,
        )
    ) {
        return null
    }
    return SafetyGuardConversationBlock(
        chatId = resolvedChatId,
        reasonCode = reasonCode,
        failureStage = failureStage,
        stopReason = stopReason,
    )
}

internal fun isConversationBlockedBySafetyGuard(
    chatId: Int?,
    blockedConversations: Map<Int, SafetyGuardConversationBlock>,
): Boolean {
    val resolvedChatId = chatId ?: return false
    return blockedConversations[resolvedChatId]?.blocked == true
}

internal fun <T> runUnlessConversationBlockedBySafetyGuard(
    chatId: Int?,
    blockedConversations: Map<Int, SafetyGuardConversationBlock>,
    generate: () -> T,
): GuardedGenerationDecision<T> {
    return if (isConversationBlockedBySafetyGuard(chatId, blockedConversations)) {
        GuardedGenerationDecision(
            generated = false,
            value = null,
            blockedMessage = SAFETY_GUARD_BLOCKED_USER_MESSAGE,
        )
    } else {
        GuardedGenerationDecision(
            generated = true,
            value = generate(),
        )
    }
}

internal fun safetyGuardCleanupMemoryStages(): List<String> = listOf(
    MEMORY_STAGE_AFTER_CANCEL,
    MEMORY_STAGE_AFTER_RUNNER_DISPOSE,
    MEMORY_STAGE_AFTER_ENGINE_RECYCLE,
)

internal fun formatSafetyGuardBlockedDiagnostics(
    block: SafetyGuardConversationBlock?,
): String {
    if (block == null || !block.blocked) return ""
    return buildString {
        appendLine("guard state: blocked")
        appendLine("last safety stage: ${block.lastSafetyStage}")
        appendLine("reasonCode=${block.reasonCode.ifBlank { "unknown" }}")
        appendLine("failure_stage=${block.failureStage.ifBlank { "unknown" }}")
        append("stop_reason=${block.stopReason.ifBlank { "unknown" }}")
    }
}
