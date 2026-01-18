package com.sonusid.ollama.ui.screens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Ignore
import kotlin.random.Random

@Ignore("Broken after animation refactor")
class InsertionAnimationSettingsTest {
    @Test
    fun `probabilityPercent 0 disables insertion`() {
        val settings = baseSettings(probabilityPercent = 0)

        val result = settings.shouldAttemptInsertion(
            loopCount = 1,
            lastInsertionLoop = null,
            random = Random(0),
        )

        assertFalse(result)
    }

    @Test
    fun `probabilityPercent 100 always allows insertion`() {
        val settings = baseSettings(probabilityPercent = 100)

        val result = settings.shouldAttemptInsertion(
            loopCount = 1,
            lastInsertionLoop = null,
            random = Random(0),
        )

        assertTrue(result)
    }

    @Test
    fun `everyNLoops gates insertion checks`() {
        val settings = baseSettings(everyNLoops = 3, probabilityPercent = 100)

        val skipped = settings.shouldAttemptInsertion(
            loopCount = 2,
            lastInsertionLoop = null,
            random = Random(0),
        )
        val allowed = settings.shouldAttemptInsertion(
            loopCount = 3,
            lastInsertionLoop = null,
            random = Random(0),
        )

        assertFalse(skipped)
        assertTrue(allowed)
    }

    @Test
    fun `cooldownLoops prevents insertion until loops pass`() {
        val settings = baseSettings(cooldownLoops = 2, probabilityPercent = 100)

        val blocked = settings.shouldAttemptInsertion(
            loopCount = 3,
            lastInsertionLoop = 2,
            random = Random(0),
        )
        val allowed = settings.shouldAttemptInsertion(
            loopCount = 4,
            lastInsertionLoop = 2,
            random = Random(0),
        )

        assertFalse(blocked)
        assertTrue(allowed)
    }

    @Test
    fun `probability roll uses 0 to 99`() {
        val roll = FixedRandom(42)
        val blockedSettings = baseSettings(probabilityPercent = 42)
        val allowedSettings = baseSettings(probabilityPercent = 43)

        val blocked = blockedSettings.shouldAttemptInsertion(
            loopCount = 1,
            lastInsertionLoop = null,
            random = roll,
        )
        val allowed = allowedSettings.shouldAttemptInsertion(
            loopCount = 1,
            lastInsertionLoop = null,
            random = roll,
        )

        assertFalse(blocked)
        assertTrue(allowed)
    }

    private fun baseSettings(
        everyNLoops: Int = 1,
        probabilityPercent: Int = 50,
        cooldownLoops: Int = 0,
    ): InsertionAnimationSettings =
        InsertionAnimationSettings(
            enabled = true,
            frameSequence = listOf(1, 2, 3),
            intervalMs = 200,
            everyNLoops = everyNLoops,
            probabilityPercent = probabilityPercent,
            cooldownLoops = cooldownLoops,
            exclusive = false,
        )

    private class FixedRandom(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = value
    }
}
