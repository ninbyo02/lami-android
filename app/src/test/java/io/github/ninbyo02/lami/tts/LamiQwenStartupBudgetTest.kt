package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class LamiQwenStartupBudgetTest {
    @Test fun `fast generation keeps existing startup rule without waiting for EOS`() {
        val budget = LamiQwenStartupBudget { false }
        assertTrue(budget.canStart(0, 0))
        assertTrue(budget.canStart(ms(640), 100))
    }

    @Test fun `slow generation cannot start before codec EOS even with a large reserve`() {
        val budget = LamiQwenStartupBudget { true }
        warm(budget)
        assertFalse(budget.canStart(ms(30_000), 10_500))
    }

    @Test fun `without a warm measurement slow generation waits for final PCM`() {
        val budget = LamiQwenStartupBudget { true }
        budget.decodeStarted(frames(2), 0)
        budget.decodeCompleted(frames(2), 10_000)
        budget.codecCompleted(frames(8))
        assertFalse(budget.canStart(ms(30_000), 10_000))
        budget.decodeStarted(frames(8), 10_000)
        assertFalse(budget.canStart(ms(30_000), 10_050))
        budget.decodeCompleted(frames(8), 10_080)
        assertTrue(budget.canStart(ms(640), 10_080))
    }

    @Test fun `cold forward is excluded and unstarted final decode charges full prefix cost`() {
        val budget = LamiQwenStartupBudget { true }
        warm(budget)
        budget.codecCompleted(frames(64))
        // Warm32 frames took320ms: final64 takes640ms; 20%+160=928ms.
        assertFalse(budget.canStart(ms(927), 10_320))
        assertTrue(budget.canStart(ms(928), 10_320))
    }

    @Test fun `final decode already in flight subtracts elapsed work without charging it twice`() {
        val budget = LamiQwenStartupBudget { true }
        warm(budget)
        budget.codecCompleted(frames(64))
        budget.decodeStarted(frames(64), 10_320)
        // (640-200)*1.2+160 =688ms.
        assertFalse(budget.canStart(ms(687), 10_520))
        assertTrue(budget.canStart(ms(688), 10_520))
    }

    @Test fun `inflight intermediate prefix and queued final both contribute to remaining cost`() {
        val budget = LamiQwenStartupBudget { true }
        warm(budget)
        budget.decodeStarted(frames(48), 10_320)
        budget.codecCompleted(frames(64))
        // ((480-100)+640)*1.2+160 =1384ms.
        assertFalse(budget.canStart(ms(1_383), 10_420))
        assertTrue(budget.canStart(ms(1_384), 10_420))
    }

    @Test fun `latest warm rate replaces older measurement and ignores zero-duration samples`() {
        val budget = LamiQwenStartupBudget { true }
        budget.decodeStarted(frames(2), 0)
        budget.decodeCompleted(frames(2), 10_000)
        budget.decodeStarted(frames(16), 10_000)
        budget.decodeCompleted(frames(16), 10_320)
        budget.decodeStarted(frames(32), 10_320)
        budget.decodeCompleted(frames(32), 10_640)
        budget.codecCompleted(frames(64))
        assertTrue(budget.canStart(ms(928), 10_640))

        val unmeasured = LamiQwenStartupBudget { true }
        unmeasured.decodeStarted(frames(2), 0)
        unmeasured.decodeCompleted(frames(2), 1_000)
        unmeasured.decodeStarted(frames(8), 1_000)
        unmeasured.decodeCompleted(frames(8), 1_000)
        unmeasured.codecCompleted(frames(16))
        assertFalse(unmeasured.canStart(ms(30_000), 1_000))
    }

    @Test fun `decoder estimate overrun does not become a false zero-work prediction`() {
        val budget = LamiQwenStartupBudget { true }
        warm(budget)
        budget.codecCompleted(frames(64))
        budget.decodeStarted(frames(64), 10_320)
        assertFalse(budget.canStart(ms(30_000), 11_100))
        budget.decodeCompleted(frames(64), 11_120)
        assertTrue(budget.canStart(ms(2_560), 11_120))
    }

    @Test fun `known short EOS may start once all PCM is decoded without warm history`() {
        val budget = LamiQwenStartupBudget { true }
        budget.codecCompleted(frames(2))
        budget.decodeStarted(frames(2), 0)
        assertFalse(budget.canStart(ms(160), 100))
        budget.decodeCompleted(frames(2), 1_000)
        assertTrue(budget.canStart(ms(160), 1_000))
    }

    @Test fun `invalid frame counts overlap and truncation cannot create an optimistic budget`() {
        val budget = LamiQwenStartupBudget { true }
        assertThrows(IllegalArgumentException::class.java) { budget.codecCompleted(frames(257)) }
        assertThrows(IllegalArgumentException::class.java) { budget.decodeStarted(4_000, 0) }
        budget.decodeStarted(frames(8), 100)
        assertThrows(IllegalArgumentException::class.java) { budget.codecCompleted(frames(4)) }
        assertThrows(IllegalArgumentException::class.java) { budget.decodeStarted(frames(16), 101) }
        assertThrows(IllegalArgumentException::class.java) { budget.decodeCompleted(frames(8), 99) }
        budget.decodeCompleted(frames(8), 200)
        budget.codecCompleted(frames(16))
        assertThrows(IllegalArgumentException::class.java) { budget.codecCompleted(frames(24)) }
        assertThrows(IllegalArgumentException::class.java) { budget.canStart(ms(30_001), 200) }
    }

    private fun warm(budget: LamiQwenStartupBudget) {
        budget.decodeStarted(frames(2), 0)
        budget.decodeCompleted(frames(2), 10_000)
        budget.decodeStarted(frames(32), 10_000)
        budget.decodeCompleted(frames(32), 10_320)
    }

    private fun frames(count: Int) = count * 1_920
    private fun ms(count: Int) = count * 24
}
