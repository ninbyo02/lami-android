package io.github.ninbyo02.lami.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal data class SpriteDrawGeometry(val region: SpriteFrameRegion, val dstOffset: IntOffset)

/** Draw-thread local, bounded cache, replaced whenever geometry inputs change. */
internal class SpriteDrawGeometryCache(private val resolve: (Int) -> SpriteDrawGeometry?) {
    private val entries = LinkedHashMap<Int, SpriteDrawGeometry?>()
    fun get(index: Int): SpriteDrawGeometry? {
        if (entries.containsKey(index)) return entries[index]
        val value = resolve(index)
        if (entries.size >= 64) entries.remove(entries.keys.first())
        entries[index] = value
        return value
    }
}

internal fun resolveSpriteDrawGeometry(
    frameIndex: Int,
    sheetRegion: SpriteFrameRegion?,
    defaultFrameSize: IntSize,
    frameMaps: LamiSpriteFrameMaps?,
    frameSrcOffsetMap: Map<Int, IntOffset>,
    frameSrcSizeMap: Map<Int, IntSize>,
    autoCrop: Boolean,
    dstSize: IntSize,
    contentOffset: IntOffset,
    frameXOffsetPxMap: Map<Int, Int>,
    frameYOffsetPxMap: Map<Int, Int>,
): SpriteDrawGeometry? {
    val baseOffset = frameMaps?.offsetMap?.get(frameIndex)
        ?: frameSrcOffsetMap[frameIndex] ?: sheetRegion?.srcOffset ?: IntOffset.Zero
    val baseSize = frameMaps?.sizeMap?.get(frameIndex)
        ?: frameSrcSizeMap[frameIndex] ?: sheetRegion?.srcSize ?: defaultFrameSize
    if (baseSize.width <= 0 || baseSize.height <= 0) return null
    val adjustment = if (autoCrop) frameSrcOffsetMap[frameIndex] ?: IntOffset.Zero else IntOffset.Zero
    val sourceSize = if (autoCrop) frameSrcSizeMap[frameIndex] ?: baseSize else baseSize
    val region = SpriteFrameRegion(baseOffset + adjustment, sourceSize)
    val offset = IntOffset(
        contentOffset.x + ((frameXOffsetPxMap[frameIndex] ?: 0) * (dstSize.width.toFloat() / sourceSize.width.coerceAtLeast(1))).roundToInt(),
        contentOffset.y + ((frameYOffsetPxMap[frameIndex] ?: 0) * (dstSize.height.toFloat() / sourceSize.height.coerceAtLeast(1))).roundToInt(),
    )
    return SpriteDrawGeometry(region, offset)
}
