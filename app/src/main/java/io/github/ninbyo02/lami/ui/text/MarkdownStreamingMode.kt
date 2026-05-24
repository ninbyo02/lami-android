package io.github.ninbyo02.lami.ui.text

enum class MarkdownStreamingMode(
    val storageValue: String,
    val displayLabel: String,
) {
    LAMI_RECOVERY_V1(
        storageValue = "lami_recovery_v1",
        displayLabel = "Lami Recovery v1",
    ),
    EDGE_GALLERY_COMPAT(
        storageValue = "edge_gallery_compat",
        displayLabel = "Edge Gallery compatible",
    );

    companion object {
        val DEFAULT: MarkdownStreamingMode = LAMI_RECOVERY_V1

        fun fromStorage(raw: String?): MarkdownStreamingMode {
            return entries.firstOrNull { mode ->
                mode.storageValue == raw || mode.name == raw
            } ?: DEFAULT
        }
    }
}

fun resolveEffectiveMarkdownStreamingMode(
    storedMode: MarkdownStreamingMode,
    isDebugBuild: Boolean,
): MarkdownStreamingMode {
    return if (isDebugBuild) storedMode else MarkdownStreamingMode.DEFAULT
}

fun processEdgeGalleryCompatibleMarkdown(text: String): String {
    return text.replace("\\n", "\n")
}
