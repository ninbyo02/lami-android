package io.github.ninbyo02.lami.ui.screens.spriteeditor

internal const val MINECRAFT_SKIN_SIZE_PX = 64
internal const val MINECRAFT_HD_SKIN_SIZE_PX = 128

internal data class MinecraftSkinRegion(
    val label: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    fun contains(px: Int, py: Int): Boolean {
        return px >= x && py >= y && px < x + w && py < y + h
    }
}

internal fun minecraftSkinRegions(width: Int, height: Int): List<MinecraftSkinRegion> {
    return when {
        width == MINECRAFT_SKIN_SIZE_PX && height == MINECRAFT_SKIN_SIZE_PX -> MinecraftSkinRegions64
        width == MINECRAFT_HD_SKIN_SIZE_PX && height == MINECRAFT_HD_SKIN_SIZE_PX -> MinecraftSkinRegions128
        else -> emptyList()
    }
}

internal fun minecraftSkinPartLabelAt(
    width: Int,
    height: Int,
    x: Int,
    y: Int,
): String? {
    return minecraftSkinRegions(width, height)
        .firstOrNull { region -> region.contains(x, y) }
        ?.label
}

internal fun minecraftSkinOverlayStrokeWidth(bitmapWidth: Int, renderScale: Float): Float {
    val logical64Scale = renderScale * bitmapWidth / MINECRAFT_SKIN_SIZE_PX.toFloat()
    return (logical64Scale / 3f).coerceIn(1f, 2.5f)
}

private val MinecraftSkinRegions64 = listOf(
    MinecraftSkinRegion("Head Top", 8, 0, 8, 8),
    MinecraftSkinRegion("Head Bottom", 16, 0, 8, 8),
    MinecraftSkinRegion("Head Right", 0, 8, 8, 8),
    MinecraftSkinRegion("Head Front", 8, 8, 8, 8),
    MinecraftSkinRegion("Head Left", 16, 8, 8, 8),
    MinecraftSkinRegion("Head Back", 24, 8, 8, 8),
    MinecraftSkinRegion("Head Overlay Top", 40, 0, 8, 8),
    MinecraftSkinRegion("Head Overlay Bottom", 48, 0, 8, 8),
    MinecraftSkinRegion("Head Overlay Right", 32, 8, 8, 8),
    MinecraftSkinRegion("Head Overlay Front", 40, 8, 8, 8),
    MinecraftSkinRegion("Head Overlay Left", 48, 8, 8, 8),
    MinecraftSkinRegion("Head Overlay Back", 56, 8, 8, 8),

    MinecraftSkinRegion("Right Leg Top", 4, 16, 4, 4),
    MinecraftSkinRegion("Right Leg Bottom", 8, 16, 4, 4),
    MinecraftSkinRegion("Right Leg Right", 0, 20, 4, 12),
    MinecraftSkinRegion("Right Leg Front", 4, 20, 4, 12),
    MinecraftSkinRegion("Right Leg Left", 8, 20, 4, 12),
    MinecraftSkinRegion("Right Leg Back", 12, 20, 4, 12),
    MinecraftSkinRegion("Body Top", 20, 16, 8, 4),
    MinecraftSkinRegion("Body Bottom", 28, 16, 8, 4),
    MinecraftSkinRegion("Body Right", 16, 20, 4, 12),
    MinecraftSkinRegion("Body Front", 20, 20, 8, 12),
    MinecraftSkinRegion("Body Left", 28, 20, 4, 12),
    MinecraftSkinRegion("Body Back", 32, 20, 8, 12),
    MinecraftSkinRegion("Right Arm Top", 44, 16, 4, 4),
    MinecraftSkinRegion("Right Arm Bottom", 48, 16, 4, 4),
    MinecraftSkinRegion("Right Arm Right", 40, 20, 4, 12),
    MinecraftSkinRegion("Right Arm Front", 44, 20, 4, 12),
    MinecraftSkinRegion("Right Arm Left", 48, 20, 4, 12),
    MinecraftSkinRegion("Right Arm Back", 52, 20, 4, 12),

    MinecraftSkinRegion("Right Leg Overlay Top", 4, 32, 4, 4),
    MinecraftSkinRegion("Right Leg Overlay Bottom", 8, 32, 4, 4),
    MinecraftSkinRegion("Right Leg Overlay Right", 0, 36, 4, 12),
    MinecraftSkinRegion("Right Leg Overlay Front", 4, 36, 4, 12),
    MinecraftSkinRegion("Right Leg Overlay Left", 8, 36, 4, 12),
    MinecraftSkinRegion("Right Leg Overlay Back", 12, 36, 4, 12),
    MinecraftSkinRegion("Body Overlay Top", 20, 32, 8, 4),
    MinecraftSkinRegion("Body Overlay Bottom", 28, 32, 8, 4),
    MinecraftSkinRegion("Body Overlay Right", 16, 36, 4, 12),
    MinecraftSkinRegion("Body Overlay Front", 20, 36, 8, 12),
    MinecraftSkinRegion("Body Overlay Left", 28, 36, 4, 12),
    MinecraftSkinRegion("Body Overlay Back", 32, 36, 8, 12),
    MinecraftSkinRegion("Right Arm Overlay Top", 44, 32, 4, 4),
    MinecraftSkinRegion("Right Arm Overlay Bottom", 48, 32, 4, 4),
    MinecraftSkinRegion("Right Arm Overlay Right", 40, 36, 4, 12),
    MinecraftSkinRegion("Right Arm Overlay Front", 44, 36, 4, 12),
    MinecraftSkinRegion("Right Arm Overlay Left", 48, 36, 4, 12),
    MinecraftSkinRegion("Right Arm Overlay Back", 52, 36, 4, 12),

    MinecraftSkinRegion("Left Leg Overlay Top", 4, 48, 4, 4),
    MinecraftSkinRegion("Left Leg Overlay Bottom", 8, 48, 4, 4),
    MinecraftSkinRegion("Left Leg Overlay Right", 0, 52, 4, 12),
    MinecraftSkinRegion("Left Leg Overlay Front", 4, 52, 4, 12),
    MinecraftSkinRegion("Left Leg Overlay Left", 8, 52, 4, 12),
    MinecraftSkinRegion("Left Leg Overlay Back", 12, 52, 4, 12),
    MinecraftSkinRegion("Left Leg Top", 20, 48, 4, 4),
    MinecraftSkinRegion("Left Leg Bottom", 24, 48, 4, 4),
    MinecraftSkinRegion("Left Leg Right", 16, 52, 4, 12),
    MinecraftSkinRegion("Left Leg Front", 20, 52, 4, 12),
    MinecraftSkinRegion("Left Leg Left", 24, 52, 4, 12),
    MinecraftSkinRegion("Left Leg Back", 28, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Top", 36, 48, 4, 4),
    MinecraftSkinRegion("Left Arm Bottom", 40, 48, 4, 4),
    MinecraftSkinRegion("Left Arm Right", 32, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Front", 36, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Left", 40, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Back", 44, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Overlay Top", 52, 48, 4, 4),
    MinecraftSkinRegion("Left Arm Overlay Bottom", 56, 48, 4, 4),
    MinecraftSkinRegion("Left Arm Overlay Right", 48, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Overlay Front", 52, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Overlay Left", 56, 52, 4, 12),
    MinecraftSkinRegion("Left Arm Overlay Back", 60, 52, 4, 12),
)

private val MinecraftSkinRegions128 = MinecraftSkinRegions64.map { region ->
    MinecraftSkinRegion(
        label = region.label,
        x = region.x * 2,
        y = region.y * 2,
        w = region.w * 2,
        h = region.h * 2,
    )
}
