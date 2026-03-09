package com.sonusid.ollama.db.entity

/**
 * v5 追加列が導入される前に保存された履歴は、統計列がすべて null のまま残る。
 */
fun Message.isInferenceStatsMissing(): Boolean {
    return completionTokens == null && generationTimeMs == null && evalDurationNs == null
}
