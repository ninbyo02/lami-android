package io.github.ninbyo02.lami.ui.screens.home

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import java.io.File

internal object NpuImageInputPolicy {
    const val MAX_IMAGES = 10
    const val DEFAULT_PROMPT = "この画像について説明して。"
    const val FAILURE_MESSAGE = "画像を含むローカル推論に失敗しました。選択中のモデルと画像を確認して、再送してください。"

    fun effectivePrompt(prompt: String, hasImages: Boolean): String =
        prompt.trim().ifBlank { if (hasImages) DEFAULT_PROMPT else "" }

    fun contents(prompt: String, imageFilePaths: List<String>): Contents {
        require(imageFilePaths.size <= MAX_IMAGES) { "Too many images" }
        val text = effectivePrompt(prompt, imageFilePaths.isNotEmpty())
        require(text.isNotBlank()) { "Empty message" }
        return Contents.of(buildList {
            add(Content.Text(text))
            imageFilePaths.forEach { path ->
                val file = File(path)
                require(file.isAbsolute && file.isFile && file.canRead() && file.length() > 0L) {
                    "Attachment image unavailable"
                }
                add(Content.ImageFile(file.absolutePath))
            }
        })
    }
}
