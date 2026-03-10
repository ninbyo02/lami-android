package com.sonusid.ollama.viewmodels

// 初回トークン時間はアプリ側計測値。Ollama usage の load_duration とは別指標。
internal fun shouldCaptureFirstAssistantToken(existingLatencyMs: Long?, chunkText: String?): Boolean =
    existingLatencyMs == null && !chunkText.isNullOrBlank()
