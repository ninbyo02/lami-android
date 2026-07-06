package io.github.ninbyo02.lami.viewmodels

import org.json.JSONObject

enum class RemoteProvider(
    val storageValue: String,
    val displayName: String,
    val description: String,
) {
    OLLAMA(
        storageValue = "ollama",
        displayName = "Ollama",
        description = "Ollama の /api/generate と /api/tags を使います。",
    ),
    OPENAI_COMPATIBLE(
        storageValue = "openai_compatible",
        displayName = "OpenAI互換",
        description = "OpenAI互換の /v1/chat/completions と /v1/models を使います。",
    ),
    LEMONADE(
        storageValue = "lemonade",
        displayName = "Lemonade",
        description = "Lemonade Server 向け。例: http://PC-IP:13305/api/v1。APIキーは通常 lemonade で未使用です。",
    );

    internal fun usesOpenAiCompatibleApi(): Boolean = this == OPENAI_COMPATIBLE || this == LEMONADE

    internal fun toOpenAiCompatibleConfig(rawBaseUrl: String): OpenAiCompatibleConfig {
        val normalized = normalizeOpenAiCompatibleBaseUrl(rawBaseUrl, this)
        return OpenAiCompatibleConfig(
            baseUrl = normalized,
            defaultApiKey = if (this == LEMONADE) "lemonade" else null,
        )
    }

    companion object {
        fun fromStorage(raw: String?): RemoteProvider =
            entries.firstOrNull { it.storageValue == raw || it.name == raw } ?: OLLAMA
    }
}

internal data class OpenAiCompatibleConfig(
    val baseUrl: String,
    val defaultApiKey: String? = null,
)

internal data class OpenAiCompatibleStreamChunk(
    val text: String?,
    val reasoningText: String? = null,
    val done: Boolean,
    val finishReason: String? = null,
    val model: String? = null,
)

internal fun parseOpenAiCompatibleModels(response: String): List<ModelInfo> {
    val json = JSONObject(response)
    val data = json.getJSONArray("data")
    val models = mutableListOf<ModelInfo>()
    for (index in 0 until data.length()) {
        val model = data.getJSONObject(index)
        val id = model.optString("id").takeIf { it.isNotBlank() }
        if (id != null) {
            models.add(ModelInfo(id))
        }
    }
    return models
}

internal fun parseOpenAiCompatibleStreamingLine(line: String): OpenAiCompatibleStreamChunk? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("data:")) return null
    val payload = trimmed.removePrefix("data:").trim()
    if (payload == "[DONE]") {
        return OpenAiCompatibleStreamChunk(text = null, done = true)
    }

    val json = JSONObject(payload)
    val choice = json.optJSONArray("choices")?.optJSONObject(0)
    val delta = choice?.optJSONObject("delta")
    val finishReason = choice?.optNullableStringCompat("finish_reason")
    return OpenAiCompatibleStreamChunk(
        text = delta?.optNullableStringCompat("content"),
        reasoningText = delta?.optNullableStringCompat("reasoning_content"),
        done = finishReason != null,
        finishReason = finishReason,
        model = json.optNullableStringCompat("model"),
    )
}

private fun normalizeOpenAiCompatibleBaseUrl(rawBaseUrl: String, provider: RemoteProvider): String {
    val trimmed = rawBaseUrl.trim().trimEnd('/')
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    val withVersionPath = when {
        provider == RemoteProvider.LEMONADE && !withScheme.endsWith("/api/v1") -> "$withScheme/api/v1"
        withScheme.endsWith("/v1") || withScheme.endsWith("/api/v1") -> withScheme
        else -> "$withScheme/v1"
    }
    return "$withVersionPath/"
}

private fun JSONObject.optNullableStringCompat(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
