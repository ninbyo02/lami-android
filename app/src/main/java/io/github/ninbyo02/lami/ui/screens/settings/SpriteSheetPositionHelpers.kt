package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.runtime.saveable.listSaver
import io.github.ninbyo02.lami.data.SpriteSheetConfig
import io.github.ninbyo02.lami.data.boxesWithInternalIndex

data class BoxPosition(val x: Int, val y: Int)

data class SpriteSheetSnapshot(
    val boxSizePx: Int,
    val boxPositions: List<BoxPosition>,
)

internal const val DEFAULT_BOX_SIZE_PX = 88

internal fun clampPosition(
    position: BoxPosition,
    boxSizePx: Int,
    sheetWidth: Int,
    sheetHeight: Int
): BoxPosition {
    val maxX = (sheetWidth - boxSizePx).coerceAtLeast(0)
    val maxY = (sheetHeight - boxSizePx).coerceAtLeast(0)
    return BoxPosition(
        x = position.x.coerceIn(0, maxX),
        y = position.y.coerceIn(0, maxY)
    )
}

internal fun boxPositionsSaver() = listSaver<List<BoxPosition>, Int>(
    save = { list -> list.flatMap { position -> listOf(position.x, position.y) } },
    restore = { flat ->
        flat.chunked(2).map { (x, y) ->
            BoxPosition(x = x, y = y)
        }
    }
)

internal fun spriteSheetSnapshotSaver() = listSaver<SpriteSheetSnapshot, Int>(
    save = { snapshot ->
        buildList {
            add(snapshot.boxSizePx)
            add(snapshot.boxPositions.size)
            snapshot.boxPositions.forEach { position ->
                add(position.x)
                add(position.y)
            }
        }
    },
    restore = { values ->
        if (values.size < 2) {
            SpriteSheetSnapshot(
                boxSizePx = DEFAULT_BOX_SIZE_PX,
                boxPositions = defaultBoxPositions(),
            )
        } else {
            val restoredBoxSize = values[0]
            val restoredCount = values[1].coerceAtLeast(0)
            val restoredPositions = mutableListOf<BoxPosition>()
            var cursor = 2
            repeat(restoredCount) {
                val x = values.getOrNull(cursor) ?: 0
                val y = values.getOrNull(cursor + 1) ?: 0
                restoredPositions.add(BoxPosition(x, y))
                cursor += 2
            }
            SpriteSheetSnapshot(
                boxSizePx = restoredBoxSize,
                boxPositions = restoredPositions,
            )
        }
    }
)

internal fun defaultBoxPositions(): List<BoxPosition> =
    SpriteSheetConfig.default3x3()
        .boxesWithInternalIndex()
        .sortedBy { it.frameIndex }
            .map { box -> BoxPosition(box.x, box.y) }
