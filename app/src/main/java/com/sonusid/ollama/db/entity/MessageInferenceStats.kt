package com.sonusid.ollama.db.entity

import com.sonusid.ollama.ui.model.InferenceStats

/**
 * v5 追加列が導入される前に保存された履歴は、統計列がすべて null のまま残る。
 */
fun Message.isInferenceStatsMissing(): Boolean {
    return completionTokens == null && generationTimeMs == null && evalDurationNs == null
}

/**
 * DB 保存済みの推論統計のみを復元する。旧履歴（stats がすべて null）は null を返す。
 */
fun Message.toInferenceStats(): InferenceStats? {
    if (isInferenceStatsMissing()) {
        return null
    }
    if ((completionTokens ?: 0) <= 0 && (generationTimeMs ?: 0L) <= 0L && (evalDurationNs ?: 0L) <= 0L) {
        return null
    }
    return InferenceStats(
        outputTokens = completionTokens,
        completionTokens = completionTokens,
        inferenceTimeSec = generationTimeMs?.div(1000.0),
        generationTimeMs = generationTimeMs,
        evalDurationNs = evalDurationNs,
    )
}
