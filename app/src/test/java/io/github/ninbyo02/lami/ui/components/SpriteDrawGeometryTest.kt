package io.github.ninbyo02.lami.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class SpriteDrawGeometryTest {
    private fun resolve(
        maps: LamiSpriteFrameMaps? = null,
        crop: Boolean = false,
        offsets: Map<Int, IntOffset> = emptyMap(),
        sizes: Map<Int, IntSize> = emptyMap(),
        x: Int = 0,
        y: Int = 0,
    ) = resolveSpriteDrawGeometry(
        1, SpriteFrameRegion(IntOffset(100, 0), IntSize(100, 80)), IntSize(64, 64), maps,
        offsets, sizes, crop, IntSize(200, 200), IntOffset(3, 4), mapOf(1 to x), mapOf(1 to y),
    )

    @Test fun `sheet region fallback and frame offsets preserve non square scaling`() {
        val frame = requireNotNull(resolve(x = 5, y = -4))
        assertEquals(SpriteFrameRegion(IntOffset(100, 0), IntSize(100, 80)), frame.region)
        assertEquals(IntOffset(13, -6), frame.dstOffset)
    }

    @Test fun `frame maps retain precedence when crop is disabled`() {
        val maps = LamiSpriteFrameMaps(mapOf(1 to IntOffset(120, 10)), mapOf(1 to IntSize(50, 40)), IntSize(100, 80), 3)
        val frame = requireNotNull(resolve(maps, offsets = mapOf(1 to IntOffset(2, 3)), sizes = mapOf(1 to IntSize(20, 10))))
        assertEquals(SpriteFrameRegion(IntOffset(120, 10), IntSize(50, 40)), frame.region)
    }

    @Test fun `crop adjustment and frame offsets use cropped source dimensions`() {
        val maps = LamiSpriteFrameMaps(mapOf(1 to IntOffset(120, 10)), mapOf(1 to IntSize(50, 40)), IntSize(100, 80), 3)
        val frame = requireNotNull(resolve(maps, true, mapOf(1 to IntOffset(2, 3)), mapOf(1 to IntSize(20, 10)), 2, -1))
        assertEquals(SpriteFrameRegion(IntOffset(122, 13), IntSize(20, 10)), frame.region)
        assertEquals(IntOffset(23, -16), frame.dstOffset)
    }

    @Test fun `invalid base geometry remains undrawn`() {
        assertNull(resolve(sizes = mapOf(1 to IntSize(0, 20))))
    }

    @Test fun `geometry cache reuses frames and retains only bounded entries`() {
        var computations = 0
        val cache = SpriteDrawGeometryCache {
            computations++
            SpriteDrawGeometry(SpriteFrameRegion(IntOffset(it, 0), IntSize(10, 10)), IntOffset.Zero)
        }
        val first = cache.get(0)
        repeat(1000) { assertSame(first, cache.get(0)) }
        assertEquals(1, computations)
        for (i in 1..64) cache.get(i)
        assertEquals(65, computations)
        cache.get(0)
        assertEquals(66, computations)
    }

    @Test fun `invalid frames are cached and new configuration starts fresh`() {
        var computations = 0
        val cache = SpriteDrawGeometryCache { computations++; null }
        repeat(10) { assertNull(cache.get(1)) }
        assertEquals(1, computations)
        val changed = SpriteDrawGeometryCache { computations++; resolve(x = 10) }
        assertEquals(IntOffset(23, 4), changed.get(1)?.dstOffset)
        assertEquals(2, computations)
    }
}
