package io.github.ninbyo02.lami.ui.screens.spriteeditor

internal data class SpriteEditorColorHistory(
    val currentColor: Int,
    val recentColors: List<Int>,
) {
    fun select(color: Int): SpriteEditorColorHistory {
        if (color == currentColor) {
            return this
        }
        return SpriteEditorColorHistory(
            currentColor = color,
            recentColors = (listOf(currentColor) + recentColors)
                .filter { it != color }
                .distinct()
                .take(8),
        )
    }
}

internal fun spriteEditorPaletteSelectionRingWidthDp(selected: Boolean): Int? = if (selected) 3 else null

internal fun eyedropperPaletteColorForSample(sampled: Int): Int? {
    return sampled.takeIf { android.graphics.Color.alpha(it) != 0 }
}

internal data class EyedropperSelectionDecision(
    val selectedColor: Int?,
    val activateTapFallback: Boolean,
    val message: String,
)

internal fun decideEyedropperSelectionResult(
    result: UniformSelectionColorResult,
): EyedropperSelectionDecision {
    return when (result.status) {
        UniformSelectionColorStatus.UNIFORM -> {
            val color = result.color
            if (color == null || android.graphics.Color.alpha(color) == 0) {
                EyedropperSelectionDecision(null, false, "Cannot read selection color")
            } else {
                EyedropperSelectionDecision(
                    selectedColor = color,
                    activateTapFallback = false,
                    message = "Color selected from box",
                )
            }
        }
        UniformSelectionColorStatus.TRANSPARENT ->
            EyedropperSelectionDecision(null, false, "Selection is transparent")
        UniformSelectionColorStatus.MIXED ->
            EyedropperSelectionDecision(null, true, "Selection contains multiple colors. Tap a pixel.")
        UniformSelectionColorStatus.CANCELLED ->
            EyedropperSelectionDecision(null, false, "Color scan cancelled")
        UniformSelectionColorStatus.TOO_LARGE ->
            EyedropperSelectionDecision(null, false, "Selection too large")
        UniformSelectionColorStatus.RECYCLED,
        UniformSelectionColorStatus.UNSUPPORTED_CONFIG,
        UniformSelectionColorStatus.READ_FAILED ->
            EyedropperSelectionDecision(null, false, "Cannot read selection color")
    }
}

internal data class SpriteEditorPaletteSwatchSemantics(
    val contentDescription: String,
    val testTag: String,
    val selected: Boolean,
)

internal fun spriteEditorPaletteSwatchSemantics(
    label: String,
    color: Int,
    currentColor: Int,
    testTag: String,
): SpriteEditorPaletteSwatchSemantics {
    return SpriteEditorPaletteSwatchSemantics(
        contentDescription = spriteEditorPaletteSwatchContentDescription(label, color),
        testTag = testTag,
        selected = color == currentColor,
    )
}

internal fun shouldPushHistoryForPaletteBitmapResult(result: PaletteBitmapResult): Boolean {
    return result.changed && !result.rejected
}

internal data class PaletteBitmapApplicationDecision(
    val adopted: Boolean,
    val message: String,
)

internal fun decidePaletteBitmapApplication(
    currentUnchanged: Boolean,
    result: PaletteBitmapResult,
    unchangedMessage: String = "No pixels changed",
    appliedMessage: String,
): PaletteBitmapApplicationDecision {
    return when {
        !currentUnchanged -> PaletteBitmapApplicationDecision(
            adopted = false,
            message = "Sprite changed; operation skipped",
        )

        result.rejected -> PaletteBitmapApplicationDecision(
            adopted = false,
            message = paletteBitmapResultMessage(result),
        )

        !shouldPushHistoryForPaletteBitmapResult(result) -> PaletteBitmapApplicationDecision(
            adopted = false,
            message = unchangedMessage,
        )

        else -> PaletteBitmapApplicationDecision(
            adopted = true,
            message = appliedMessage,
        )
    }
}

internal fun spriteEditorPaletteSwatchContentDescription(label: String, color: Int): String {
    return "$label ${spriteEditorPaletteHexColor(color)}"
}

internal fun spriteEditorPaletteHexColor(color: Int): String {
    return if (android.graphics.Color.alpha(color) == 255) {
        "#%06X".format(color and 0x00FFFFFF)
    } else {
        "#%08X".format(color.toLong() and 0xFFFFFFFFL)
    }
}

private fun paletteBitmapResultMessage(result: PaletteBitmapResult): String {
    return when (result.rejectionReason) {
        PaletteBitmapRejectionReason.NONE -> "No pixels changed"
        PaletteBitmapRejectionReason.TOO_LARGE -> "Image too large for sprite operation (max 4,194,304 pixels)"
        PaletteBitmapRejectionReason.CANCELLED -> "Operation cancelled"
        PaletteBitmapRejectionReason.RECYCLED,
        PaletteBitmapRejectionReason.UNSUPPORTED_CONFIG,
        PaletteBitmapRejectionReason.COPY_FAILED,
        PaletteBitmapRejectionReason.READ_FAILED,
        PaletteBitmapRejectionReason.WRITE_FAILED -> "Sprite operation rejected"
    }
}
