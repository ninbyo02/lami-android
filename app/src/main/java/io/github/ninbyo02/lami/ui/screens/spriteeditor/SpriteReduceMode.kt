package io.github.ninbyo02.lami.ui.screens.spriteeditor

import java.util.Collections
import java.util.Locale

internal enum class SpriteReduceMode {
    ImageAdaptive,
    FixedPaletteV3,
    FixedPaletteV2,
    LegacyFixedPaletteV1,
}

internal enum class SpriteReduceChoice(
    val label: String,
    val mode: SpriteReduceMode?,
) {
    ImageAdaptive("Image Adaptive", SpriteReduceMode.ImageAdaptive),
    FixedPalette("Fixed Palette", SpriteReduceMode.FixedPaletteV3),
    Cancel("Cancel", null),
}

internal val SPRITE_REDUCE_CHOICES: List<SpriteReduceChoice> = Collections.unmodifiableList(
    listOf(
        SpriteReduceChoice.ImageAdaptive,
        SpriteReduceChoice.FixedPalette,
        SpriteReduceChoice.Cancel,
    ),
)

internal data class SpriteReduceRepeat(
    val mode: SpriteReduceMode,
    val rgbAnchors: List<Int> = emptyList(),
) {
    companion object {
        fun create(mode: SpriteReduceMode, rgbAnchors: List<Int> = emptyList()): SpriteReduceRepeat {
            if (mode != SpriteReduceMode.ImageAdaptive) {
                return SpriteReduceRepeat(mode)
            }
            val validated = validateSpriteReduceAnchors(rgbAnchors) ?: emptyList()
            return SpriteReduceRepeat(mode, validated)
        }
    }
}

internal fun saveSpriteReduceRepeat(repeat: SpriteReduceRepeat): List<String> {
    val normalized = SpriteReduceRepeat.create(repeat.mode, repeat.rgbAnchors)
    return buildList {
        add(if (normalized.mode == SpriteReduceMode.FixedPaletteV3) SPRITE_REDUCE_V3_TOKEN else SPRITE_REDUCE_V2_TOKEN)
        add(normalized.mode.name)
        if (normalized.mode == SpriteReduceMode.ImageAdaptive) {
            normalized.rgbAnchors.forEach { color ->
                add(String.format(Locale.ROOT, "%08X", color))
            }
        }
    }
}

internal fun restoreSpriteReduceRepeat(data: List<String>): SpriteReduceRepeat? {
    return when (data.firstOrNull()) {
        SPRITE_REDUCE_LEGACY_TOKEN -> SpriteReduceRepeat(SpriteReduceMode.LegacyFixedPaletteV1)
        SPRITE_REDUCE_V2_TOKEN -> restoreSpriteReduceV2(data)
        SPRITE_REDUCE_V3_TOKEN -> restoreSpriteReduceV3(data)
        else -> null
    }
}

private fun restoreSpriteReduceV2(data: List<String>): SpriteReduceRepeat? {
    val mode = data.getOrNull(1)?.let { name ->
        runCatching { SpriteReduceMode.valueOf(name) }.getOrNull()
    } ?: return null
    if (mode == SpriteReduceMode.FixedPaletteV3) return null
    if (mode != SpriteReduceMode.ImageAdaptive) {
        return SpriteReduceRepeat(mode)
    }
    val encodedAnchors = data.drop(2)
    if (encodedAnchors.isEmpty()) {
        return SpriteReduceRepeat(mode)
    }
    val decoded = ArrayList<Int>(encodedAnchors.size)
    for (encoded in encodedAnchors) {
        if (encoded.length != 8) {
            return SpriteReduceRepeat(mode)
        }
        val color = encoded.toLongOrNull(16)?.toInt() ?: return SpriteReduceRepeat(mode)
        decoded.add(color)
    }
    return SpriteReduceRepeat.create(mode, decoded)
}

private fun restoreSpriteReduceV3(data: List<String>): SpriteReduceRepeat? {
    if (data.size != 2 || data[1] != SpriteReduceMode.FixedPaletteV3.name) return null
    return SpriteReduceRepeat(SpriteReduceMode.FixedPaletteV3)
}

private fun validateSpriteReduceAnchors(anchors: List<Int>): List<Int>? {
    if (anchors.size > SPRITE_REDUCE_MAX_ANCHORS) return null
    val seen = HashSet<Int>(anchors.size)
    val validated = ArrayList<Int>(anchors.size)
    for (color in anchors) {
        if ((color ushr 24) != 0xFF || !seen.add(color and 0x00FFFFFF)) {
            return null
        }
        validated.add(color)
    }
    return Collections.unmodifiableList(validated)
}

private const val SPRITE_REDUCE_MAX_ANCHORS = 256
private const val SPRITE_REDUCE_LEGACY_TOKEN = "ReduceTo256Colors"
private const val SPRITE_REDUCE_V2_TOKEN = "ReduceTo256ColorsV2"
private const val SPRITE_REDUCE_V3_TOKEN = "ReduceTo256ColorsV3"
