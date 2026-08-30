package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal data class ModelOwnedChatTemplateVerification(
    val supported: Boolean,
    val templateSha256: String = "",
    val templateLengthBytes: Int = 0,
    val reason: String,
)

internal object ModelOwnedChatTemplate {
    const val PROFILE = "gemma4_turn_v1"
    const val PROMPT_TAIL_VARIANT = "model_metadata_gemma4_turn_v1"
    const val EVALUATOR = "lami_verified_model_template_renderer"
    const val NATIVE_SYSTEM_INSTRUCTION = "日本語で簡潔に回答。"
    const val SUPPORTED_TEMPLATE_SHA256 =
        "e085010dd7227ccad0f450ea3e5d621b06abe765e8c94e8c63a02be3e7d70b2e"

    private const val SEARCH_WINDOW_BYTES = 8 * 1024 * 1024
    private const val MAX_TEMPLATE_BLOB_BYTES = 64 * 1024
    private val templateHeader = "{%- macro format_parameters".toByteArray()
    private val cache = ConcurrentHashMap<String, ModelOwnedChatTemplateVerification>()

    fun verify(modelPath: String): ModelOwnedChatTemplateVerification {
        val file = File(modelPath)
        if (!file.isFile) {
            return ModelOwnedChatTemplateVerification(false, reason = "model_file_unavailable")
        }
        val cacheKey = listOf(modelPath, file.length(), file.lastModified()).joinToString(":")
        cache[cacheKey]?.let { return it }
        val result = runCatching {
            RandomAccessFile(file, "r").use { input ->
                val offset = findTemplateOffset(input)
                    ?: return@use ModelOwnedChatTemplateVerification(
                        supported = false,
                        reason = "embedded_chat_template_not_found",
                    )
                input.seek(offset)
                val blob = ArrayList<Byte>()
                while (blob.size < MAX_TEMPLATE_BLOB_BYTES) {
                    val value = input.read()
                    if (value < 0 || value == 0) break
                    blob += value.toByte()
                }
                val bytes = blob.toByteArray()
                val digest = sha256(bytes)
                ModelOwnedChatTemplateVerification(
                    supported = digest == SUPPORTED_TEMPLATE_SHA256,
                    templateSha256 = digest,
                    templateLengthBytes = bytes.size,
                    reason = if (digest == SUPPORTED_TEMPLATE_SHA256) {
                        "verified_model_metadata_template"
                    } else {
                        "unsupported_embedded_chat_template"
                    },
                )
            }
        }.getOrElse { throwable ->
            ModelOwnedChatTemplateVerification(
                supported = false,
                reason = "template_read_failed:" + throwable.javaClass.simpleName,
            )
        }
        cache.clear()
        cache[cacheKey] = result
        return result
    }

    fun renderForNativeAdapter(
        contextText: String,
        userPrompt: String,
    ): String {
        val systemInstruction = NATIVE_SYSTEM_INSTRUCTION.takeIf { contextText.isBlank() }.orEmpty()
        val rendered = renderKnownProfile(
            contextText = contextText,
            userPrompt = userPrompt,
            systemInstruction = systemInstruction,
        )
        return if (systemInstruction.isBlank()) {
            rendered.removePrefix("<bos><|turn>system\n<turn|>\n")
        } else {
            rendered.removePrefix("<bos>")
        }
    }

    fun renderKnownProfile(
        contextText: String,
        userPrompt: String,
        systemInstruction: String = LocalConversationPolicy.SYSTEM_INSTRUCTION,
    ): String = buildString {
        append("<bos><|turn>system\n")
        append(systemInstruction.trim())
        append("<turn|>\n")
        parseContext(contextText).forEach { turn ->
            val role = when (turn.role) {
                LocalConversationRole.USER -> "user"
                LocalConversationRole.MODEL -> "model"
            }
            val content = if (turn.role == LocalConversationRole.MODEL) {
                stripThinking(turn.text)
            } else {
                turn.text.trim()
            }
            if (content.isNotBlank()) {
                append("<|turn>")
                append(role)
                append('\n')
                append(content)
                append("<turn|>\n")
            }
        }
        append("<|turn>user\n")
        append(userPrompt.trim())
        append("<turn|>\n<|turn>model\n")
    }

    internal fun parseContext(contextText: String): List<LocalConversationTurn> {
        val turns = mutableListOf<LocalConversationTurn>()
        contextText.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim()
            val roleAndText = when {
                line.startsWith("ユーザー:") ->
                    LocalConversationRole.USER to line.removePrefix("ユーザー:").trim()
                line.startsWith("アシスタント:") ->
                    LocalConversationRole.MODEL to line.removePrefix("アシスタント:").trim()
                else -> null
            }
            if (roleAndText != null) {
                val (role, text) = roleAndText
                if (text.isNotBlank()) turns += LocalConversationTurn(role, text)
            } else if (line.isNotBlank() && turns.isNotEmpty()) {
                val previous = turns.removeAt(turns.lastIndex)
                turns += previous.copy(text = previous.text + "\n" + line)
            }
        }
        return LocalConversationHistoryPolicy.bounded(turns)
    }

    private fun stripThinking(text: String): String = text
        .split("<channel|>")
        .joinToString(separator = "") { part ->
            part.substringBefore("<|channel>")
        }
        .trim()

    private fun findTemplateOffset(input: RandomAccessFile): Long? {
        val fileLength = input.length()
        val starts = listOf(
            0L,
            (fileLength - SEARCH_WINDOW_BYTES).coerceAtLeast(0L),
        ).distinct()
        starts.forEach { start ->
            input.seek(start)
            val size = minOf(SEARCH_WINDOW_BYTES.toLong(), fileLength - start).toInt()
            val window = ByteArray(size)
            input.readFully(window)
            val index = window.indexOf(templateHeader)
            if (index >= 0) return start + index
        }
        return null
    }

    private fun ByteArray.indexOf(target: ByteArray): Int {
        if (target.isEmpty() || size < target.size) return -1
        for (start in 0..size - target.size) {
            var matches = true
            for (offset in target.indices) {
                if (this[start + offset] != target[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
