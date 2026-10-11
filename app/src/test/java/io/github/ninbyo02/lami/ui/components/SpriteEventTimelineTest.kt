package io.github.ninbyo02.lami.ui.components

import org.junit.Assert.*
import org.junit.Test

class SpriteEventTimelineTest {
    @Test fun `static frames sleep indefinitely unless insertion needs a decision`() {
        val timeline = SpriteEventTimeline(listOf(8, 8, 8, 8), 150)
        assertEquals(SpriteFrameSample(8, null), timeline.sample(175, false))
        assertEquals(SpriteFrameSample(8, 425), timeline.sample(175, true))
    }

    @Test fun `repeated frames sleep across loop boundary without missing next change`() {
        val timeline = SpriteEventTimeline(listOf(0, 0, 5, 0), 100)
        assertEquals(SpriteFrameSample(0, 175), timeline.sample(25, false))
        assertEquals(SpriteFrameSample(5, 75), timeline.sample(225, false))
        assertEquals(SpriteFrameSample(0, 275), timeline.sample(325, false))
        assertEquals(SpriteFrameSample(0, 75), timeline.sample(325, true))
    }

    @Test fun `very long insertion hold does not expand its duration into memory`() {
        val timeline = SpriteEventTimeline(listOf(0, 0, 0, 0), 1, listOf(5, 6), Int.MAX_VALUE.toLong())
        assertEquals(SpriteFrameSample(5, 4), timeline.sample(0, true))
    }

    @Test fun `visible frames match legacy insertion expansion at every millisecond`() {
        val base = listOf(0, 1, 2, 3)
        for (insert in listOf(emptyList(), listOf(5), listOf(5, 6), listOf(5, 6, 7, 8, 9))) {
            for (interval in listOf(1L, 49L, 50L, 100L, 149L, 150L, 250L, 1000L)) {
                for (exclusive in listOf(false, true)) {
                    val expanded = insert.flatMap { frame -> List(((interval + 50) / 100).toInt().coerceAtLeast(1)) { frame } }
                    val expected = base.indices.map { i ->
                        if (expanded.isEmpty()) base[i]
                        else if (exclusive) expanded.getOrElse(i) { expanded.last() }
                        else expanded.getOrElse(i) { base[i] }
                    }
                    val timeline = SpriteEventTimeline(base, 100, insert, interval, exclusive)
                    for (time in 0L until 400L) {
                        val sample = timeline.sample(time, true)
                        assertEquals(expected[(time / 100).toInt()], sample.frame)
                        val deadline = time + requireNotNull(sample.delayMs)
                        for (t in time until deadline) assertEquals(sample.frame, expected[(t / 100).toInt()])
                        if (deadline < 400) assertNotEquals(sample.frame, expected[(deadline / 100).toInt()])
                    }
                }
            }
        }
    }
}
