package io.github.ninbyo02.lami.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LamiIdleTimeoutTest {
    @Test
    fun `idle timeout does not refresh timestamp repeatedly`() {
        val idle = LamiUiState(LamiState.Idle, 1000L)
        var current = idle
        for (now in 7000L..61000L step 6000L) {
            current = current.idleAfterTimeout(current.lastInteractionTimeMs, 6000L, now)
            assertSame(idle, current)
        }
    }

    @Test
    fun `thinking never expires while response is pending`() {
        val thinking = LamiUiState(LamiState.Thinking, 1000L)
        assertSame(thinking, thinking.idleAfterTimeout(1000L, 6000L, 61000L))
    }

    @Test
    fun `speaking expires at timeout boundary and stays idle`() {
        val speaking = LamiUiState(LamiState.Speaking(42), 1000L)
        assertSame(speaking, speaking.idleAfterTimeout(1000L, 6000L, 6999L))
        val idle = speaking.idleAfterTimeout(1000L, 6000L, 7000L)
        assertEquals(LamiUiState(LamiState.Idle, 7000L), idle)
        assertSame(idle, idle.idleAfterTimeout(7000L, 6000L, 13000L))
    }

    @Test
    fun `older timeout cannot replace newer response or interaction`() {
        val newer = LamiUiState(LamiState.Speaking(80), 5000L)
        assertSame(newer, newer.idleAfterTimeout(1000L, 6000L, 12000L))
        assertEquals(LamiState.Idle, newer.idleAfterTimeout(5000L, 6000L, 12000L).state)
    }

    @Test
    fun `backward wall clock does not expire speaking early`() {
        val speaking = LamiUiState(LamiState.Speaking(42), 1000L)
        assertSame(speaking, speaking.idleAfterTimeout(1000L, 6000L, 500L))
    }
}
