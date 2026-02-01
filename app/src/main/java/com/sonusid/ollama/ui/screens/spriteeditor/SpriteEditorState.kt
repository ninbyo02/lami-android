package com.sonusid.ollama.ui.screens.spriteeditor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.jvm.ConsistentCopyVisibility
import kotlin.math.min

@ConsistentCopyVisibility
data class RectPx private constructor(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    companion object {
        fun of(x: Int, y: Int, w: Int, h: Int): RectPx {
            return RectPx(x = x, y = y, w = w.coerceAtLeast(1), h = h.coerceAtLeast(1))
        }
    }

    fun moveBy(dx: Int, dy: Int): RectPx = copy(x = x + dx, y = y + dy)

    fun resize(newW: Int, newH: Int): RectPx = copy(w = newW.coerceAtLeast(1), h = newH.coerceAtLeast(1))
}

data class SpriteEditorState(
    val bitmap: Bitmap,
    val imageBitmap: ImageBitmap,
    val selection: RectPx,
    val clipboard: Bitmap?,
    val savedSnapshot: Bitmap?,
    val initialBitmap: Bitmap,
    val widthInput: String,
    val heightInput: String,
) {
    fun withBitmap(newBitmap: Bitmap): SpriteEditorState {
        return copy(
            bitmap = newBitmap,
            imageBitmap = newBitmap.asImageBitmap(),
        )
    }

    fun withSelection(newSelection: RectPx): SpriteEditorState {
        return copy(
            selection = newSelection,
            widthInput = newSelection.w.toString(),
            heightInput = newSelection.h.toString(),
        )
    }

    fun withClipboard(newClipboard: Bitmap?): SpriteEditorState = copy(clipboard = newClipboard)

    fun withSavedSnapshot(newSnapshot: Bitmap?): SpriteEditorState = copy(savedSnapshot = newSnapshot)
}

fun createInitialEditorState(bitmap: Bitmap): SpriteEditorState {
    val safeBitmap = ensureArgb8888(bitmap)
    val startSize = min(32, min(safeBitmap.width.coerceAtLeast(1), safeBitmap.height.coerceAtLeast(1)))
    val selection = rectNormalizeClamp(
        rect = RectPx.of(0, 0, startSize, startSize),
        imageW = safeBitmap.width,
        imageH = safeBitmap.height,
    )
    return SpriteEditorState(
        bitmap = safeBitmap,
        imageBitmap = safeBitmap.asImageBitmap(),
        selection = selection,
        clipboard = null,
        savedSnapshot = null,
        initialBitmap = safeBitmap,
        widthInput = selection.w.toString(),
        heightInput = selection.h.toString(),
    )
}
