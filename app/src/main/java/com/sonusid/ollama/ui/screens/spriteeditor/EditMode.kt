package com.sonusid.ollama.ui.screens.spriteeditor

enum class EditMode(
    val label: String,
    val gridSize: Int,
) {
    BOX_32(label = "32x32", gridSize = 32),
    SPRITE_96(label = "96x96", gridSize = 96),
    BLOCK_288(label = "288x288", gridSize = 288),
}
