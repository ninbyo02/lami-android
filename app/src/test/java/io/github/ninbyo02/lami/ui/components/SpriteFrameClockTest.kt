package io.github.ninbyo02.lami.ui.components

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpriteFrameClockTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test fun `clock stops in background resumes on epoch and cancels`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = Owner()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            val ticks = mutableListOf<Long>()
            val job = launch { owner.lifecycle.runSpriteFrameClock(0, 200, { currentTime }) { ticks += it } }
            runCurrent()
            advanceTimeBy(50)
            assertTrue(ticks.isEmpty())
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf(50L), ticks)
            advanceTimeBy(150); runCurrent()
            assertEquals(listOf(50L, 200L), ticks)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            runCurrent(); advanceTimeBy(350); runCurrent()
            assertEquals(2, ticks.size)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(550L, ticks.last())
            advanceTimeBy(50); runCurrent()
            assertEquals(600L, ticks.last())
            job.cancel(); runCurrent(); advanceTimeBy(1000); runCurrent()
            assertEquals(4, ticks.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `clock wakes at sprite intervals instead of display refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = Owner()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            val ticks = mutableListOf<Long>()
            val job = launch { owner.lifecycle.runSpriteFrameClock(0, 200, { currentTime }) { ticks += it } }
            runCurrent(); advanceTimeBy(1000); runCurrent()
            assertEquals(listOf(0L, 200L, 400L, 600L, 800L, 1000L), ticks)
            job.cancel()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `delay is positive for invalid fast and future epoch inputs`() {
        assertEquals(16L, nextSpriteFrameDelayMs(100, 0, 0))
        assertEquals(16L, nextSpriteFrameDelayMs(100, 0, -1))
        assertEquals(16L, nextSpriteFrameDelayMs(100, 0, 1))
        assertEquals(75L, nextSpriteFrameDelayMs(125, 0, 100))
        assertEquals(100L, nextSpriteFrameDelayMs(0, 10, 100))
    }
    @Test fun `event clock skips equal frames and resumes a fully sleeping clock`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = Owner()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            val ticks = mutableListOf<Long>()
            var static = false
            val timeline = SpriteEventTimeline(listOf(0, 0, 5, 0), 100)
            val job = launch {
                owner.lifecycle.runSpriteEventClock({ currentTime }) { now ->
                    ticks += now
                    if (static) null else timeline.sample(now % 400, false).delayMs
                }
            }
            runCurrent(); advanceTimeBy(300); runCurrent()
            assertEquals(listOf(0L, 200L, 300L), ticks)
            static = true
            advanceTimeBy(300); runCurrent()
            assertEquals(600L, ticks.last())
            advanceTimeBy(86_400_000); runCurrent()
            assertEquals(4, ticks.size)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            runCurrent()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(5, ticks.size)
            job.cancel(); runCurrent()
            advanceTimeBy(1000); runCurrent()
            assertEquals(5, ticks.size)
        } finally { Dispatchers.resetMain() }
    }

}
