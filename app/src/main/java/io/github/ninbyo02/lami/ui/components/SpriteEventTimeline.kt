package io.github.ninbyo02.lami.ui.components

internal data class SpriteFrameSample(val frame: Int, val delayMs: Long?)

/** One loop of visible frames; never expands an insertion beyond the base loop. */
internal class SpriteEventTimeline(
    baseFrames: List<Int>,
    private val intervalMs: Long,
    insertionFrames: List<Int> = emptyList(),
    insertionIntervalMs: Long = intervalMs,
    exclusive: Boolean = false,
    maxFrameIndex: Int = Int.MAX_VALUE,
) {
    private val frames: IntArray
    init {
        require(baseFrames.isNotEmpty() && intervalMs > 0)
        val hold = ((insertionIntervalMs.coerceAtLeast(1) + intervalMs / 2) / intervalMs).coerceAtLeast(1)
        frames = IntArray(baseFrames.size) { index ->
            val insertedIndex = index.toLong() / hold
            val frame = when {
                insertionFrames.isEmpty() -> baseFrames[index]
                insertedIndex < insertionFrames.size -> insertionFrames[insertedIndex.toInt()]
                exclusive -> insertionFrames.last()
                else -> baseFrames[index]
            }
            frame.coerceIn(0, maxFrameIndex)
        }
    }

    fun sample(loopElapsedMs: Long, reconsiderAtLoopBoundary: Boolean): SpriteFrameSample {
        val index = ((loopElapsedMs / intervalMs) % frames.size).toInt()
        val frame = frames[index]
        val remainder = loopElapsedMs % intervalMs
        // Search only this finite loop. The next loop may make a new insertion decision.
        val limit = if (reconsiderAtLoopBoundary) frames.size - index else frames.size
        for (distance in 1..limit) {
            val next = (index + distance) % frames.size
            if ((reconsiderAtLoopBoundary && index + distance == frames.size) || frames[next] != frame) {
                return SpriteFrameSample(frame, distance * intervalMs - remainder)
            }
        }
        return SpriteFrameSample(frame, null)
    }
}
