package io.github.ninbyo02.lami.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.ninbyo02.lami.data.BoxPosition
import io.github.ninbyo02.lami.data.SpriteSheetConfig
import org.junit.Assert.*
import org.junit.Test

class SpriteFrameRepositoryTest {
    private val boxes = (0..5).map { i -> BoxPosition(i, (i % 3) * 96, (i / 3) * 96, 90, 90) }
    private val config = SpriteSheetConfig(2, 3, 96, 96, boxes)

    @Test fun `frame offsets are relative to grid origin on both axes`() {
        val moved = boxes.toMutableList().apply {
            this[1] = this[1].copy(x = 98, y = 6)
            this[3] = this[3].copy(x = 3, y = 98)
        }
        val maps = moved.toFrameMaps(config)
        assertEquals(IntSize(96, 96), maps.frameSize)
        assertEquals(IntOffset(98, 6), maps.offsetMap[1])
        assertEquals(2, maps.toFrameXOffsetPxMap()[1])
        assertEquals(6, maps.toFrameYOffsetPxMap()[1])
        assertEquals(3, maps.toFrameXOffsetPxMap()[3])
        assertEquals(2, maps.toFrameYOffsetPxMap()[3])
    }

    @Test fun `empty override uses configured boxes and invalid indices are ignored`() {
        assertEquals(boxes.toFrameMaps(config), emptyList<BoxPosition>().toFrameMaps(config))
        val result = (boxes + BoxPosition(99, 0, 0, 5, 5)).toFrameMaps(config)
        assertEquals(6, result.offsetMap.size)
    }

    @Test fun `invalid frame geometry is clamped to drawable dimensions`() {
        val maps = listOf(BoxPosition(0, -2, -3, 0, -1)).toFrameMaps(config)
        assertEquals(IntOffset.Zero, maps.offsetMap[0])
        assertEquals(IntSize(1, 1), maps.sizeMap[0])
    }
}
