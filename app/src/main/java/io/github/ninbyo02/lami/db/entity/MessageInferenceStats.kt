package io.github.ninbyo02.lami.db.entity

import io.github.ninbyo02.lami.ui.model.InferenceStats

/**
 * v6 追加列が導入される前に保存された履歴は、統計列がすべて null のまま残る。
 */
fun Message.isInferenceStatsMissing(): Boolean {
    return completionTokens == null &&
        generationTimeMs == null &&
        generationDurationNs == null &&
        evalDurationNs == null &&
        loadDurationNs == null &&
        promptEvalDurationNs == null &&
        modelName == null &&
        inputTokens == null &&
        totalTokens == null &&
        tokensPerSecond == null &&
        charsPerSecond == null &&
        tokenCountMode == null &&
        inferenceNotes == null &&
        inferenceTimeSec == null &&
        decodeDurationMs == null &&
        totalDurationMs == null &&
        finishReason == null &&
        timeToFirstTokenMs == null &&
        imageInputCount == null
}

/**
 * DB 保存済みの推論統計のみを復元する。旧履歴（stats がすべて null）は null を返す。
 *
 * 責務メモ:
 * - finalChunk.model -> Message.modelName -> InferenceStats.modelName
 * - finalChunk.evalCount -> Message.completionTokens -> InferenceStats.outputTokens
 * - Message.inferenceTimeSec (保存値) を優先し、欠損時のみ generationTimeMs から導出
 * - finalChunk.doneReason/finishReason -> Message.finishReason -> InferenceStats.finishReason
 * - 画像入力数は添付画像の枚数。入力トークン(promptEvalCount)とは同義にしない
 */
fun Message.toInferenceStats(): InferenceStats? {
    if (isInferenceStatsMissing()) {
        return null
    }
    return InferenceStats(
        modelName = modelName,
        inputTokens = inputTokens,
        outputTokens = completionTokens,
        totalTokens = totalTokens,
        tokensPerSecond = tokensPerSecond,
        charsPerSecond = charsPerSecond,
        tokenCountMode = tokenCountMode,
        notes = inferenceNotes,
        inferenceTimeSec = inferenceTimeSec ?: generationTimeMs?.div(1000.0),
        generationTimeMs = generationTimeMs,
        decodeDurationMs = decodeDurationMs,
        totalDurationMs = totalDurationMs,
        modelLoadDurationNs = loadDurationNs,
        promptEvalDurationNs = promptEvalDurationNs,
        generationDurationNs = generationDurationNs ?: evalDurationNs,
        evalDurationNs = evalDurationNs,
        finishReason = finishReason,
        localSourceSummary = localSourceSummary,
        timeToFirstTokenMs = timeToFirstTokenMs,
        imageInputCount = imageInputCount,
        // 互換項目（段階移行用）。
        model = modelName,
        modelLabel = modelName,
        completionTokens = completionTokens,
        responseCharCount = message.length,
    )
}
