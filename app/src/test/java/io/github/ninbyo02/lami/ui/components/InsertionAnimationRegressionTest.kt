package io.github.ninbyo02.lami.ui.components

import io.github.ninbyo02.lami.ui.screens.settings.InsertionAnimationSettings
import io.github.ninbyo02.lami.ui.screens.settings.InsertionPattern
import io.github.ninbyo02.lami.ui.screens.settings.shouldAttemptInsertion
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class InsertionAnimationRegressionTest {
    private fun settings() = InsertionAnimationSettings(
        true, listOf(InsertionPattern(listOf(1, 2))), 200, 1, 100, 0, false,
    )
    private class Roll(private val roll: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = error("unexpected random operation")
        override fun nextInt(until: Int): Int { require(roll in 0 until until); return roll }
    }

    @Test fun `disabled zero probability and invalid period never insert`() {
        for (s in listOf(settings().copy(enabled = false), settings().copy(probabilityPercent = 0), settings().copy(everyNLoops = 0))) {
            assertFalse(s.shouldAttemptInsertion(1, null, Roll(0)))
            assertFalse(shouldAttemptInsertionDeterministic(s, 1, null, 0))
        }
    }
    @Test fun `period and cooldown boundaries apply to random and synchronized playback`() {
        val s = settings().copy(everyNLoops = 3, cooldownLoops = 4)
        for ((loop, last, expected) in listOf(Triple(2, null, false), Triple(3, null, true), Triple(6, 3, false), Triple(9, 3, true))) {
            assertEquals(expected, s.shouldAttemptInsertion(loop, last, Roll(99)))
            assertEquals(expected, shouldAttemptInsertionDeterministic(s, loop, last, 7))
        }
    }
    @Test fun `probability threshold uses exclusive upper bound`() {
        assertFalse(settings().copy(probabilityPercent = 42).shouldAttemptInsertion(1, null, Roll(42)))
        assertTrue(settings().copy(probabilityPercent = 43).shouldAttemptInsertion(1, null, Roll(42)))
    }
    @Test fun `weighted selector excludes empty zero and negative weight entries`() {
        val patterns = listOf(InsertionPattern(emptyList(), 5), InsertionPattern(listOf(0), 0), InsertionPattern(listOf(0), -1), InsertionPattern(listOf(2), 1))
        assertEquals(3, selectWeightedInsertionPattern(patterns, Roll(0))?.first)
        assertNull(selectWeightedInsertionPattern(patterns.take(3), Roll(0)))
        assertNull(selectWeightedInsertionPattern(emptyList(), Roll(0)))
    }
    @Test fun `weighted selection respects exact cumulative boundaries`() {
        val patterns = listOf(InsertionPattern(listOf(1), 3), InsertionPattern(listOf(2), 1))
        assertEquals(0, selectWeightedInsertionPattern(patterns, Roll(2))?.first)
        assertEquals(1, selectWeightedInsertionPattern(patterns, Roll(3))?.first)
    }
}
