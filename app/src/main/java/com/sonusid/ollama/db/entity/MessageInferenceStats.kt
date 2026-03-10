package com.sonusid.ollama.db.entity

import com.sonusid.ollama.ui.model.InferenceStats

/**
 * v6 追加列が導入される前に保存された履歴は、統計列がすべて null のまま残る。
 */
fun Message.isInferenceStatsMissing(): Boolean {
    return completionTokens == null &&
        generationTimeMs == null &&
        evalDurationNs == null &&
        modelName == null &&
        inputTokens == null &&
        totalTokens == null &&
        tokensPerSecond == null &&
        inferenceTimeSec == null
}

/**
 * DB 保存済みの推論統計のみを復元する。旧履歴（stats がすべて null）は null を返す。
 */
fun Message.toInferenceStats(): InferenceStats? {
    if (isInferenceStatsMissing()) {
        return null
    }
    return InferenceStats(
        model = modelName,
        inputTokens = inputTokens,
        outputTokens = completionTokens,
        totalTokens = totalTokens,
        tokensPerSecond = tokensPerSecond,
        inferenceTimeSec = inferenceTimeSec ?: generationTimeMs?.div(1000.0),
        modelLabel = modelName,
        completionTokens = completionTokens,
        generationTimeMs = generationTimeMs,
        evalDurationNs = evalDurationNs,
    )
}
