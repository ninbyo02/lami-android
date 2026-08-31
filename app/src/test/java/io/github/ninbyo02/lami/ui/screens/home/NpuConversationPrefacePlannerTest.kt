package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuConversationPrefacePlannerTest {
    @Test
    fun keepsOnlyLatestThreeCompletedPairs() {
        val history = (1..5).flatMap { index ->
            listOf(
                LocalConversationTurn(LocalConversationRole.USER, "user-$index"),
                LocalConversationTurn(LocalConversationRole.MODEL, "model-$index"),
            )
        }

        val plan = NpuConversationPrefacePlanner.plan(history, " current ")

        assertEquals(3, plan.completedHistoryPairCount)
        assertEquals(
            listOf("user-3", "model-3", "user-4", "model-4", "user-5", "model-5"),
            plan.initialTurns.map(LocalConversationTurn::text),
        )
        assertEquals("current", plan.currentUserPrompt)
    }

    @Test
    fun dropsIncompleteHistoryInsteadOfInventingModelResponse() {
        val history = listOf(
            LocalConversationTurn(LocalConversationRole.USER, "orphan"),
            LocalConversationTurn(LocalConversationRole.USER, "paired-user"),
            LocalConversationTurn(LocalConversationRole.MODEL, "paired-model"),
            LocalConversationTurn(LocalConversationRole.USER, "pending"),
        )

        val plan = NpuConversationPrefacePlanner.plan(history, "next")

        assertEquals(listOf("paired-user", "paired-model"), plan.initialTurns.map { it.text })
    }

    @Test
    fun defaultMemoryProviderDoesNotExtractOrFabricateFacts() {
        val history = listOf(
            LocalConversationTurn(LocalConversationRole.USER, "私の名字は佐藤です"),
            LocalConversationTurn(LocalConversationRole.MODEL, "了解しました"),
        )

        val plan = NpuConversationPrefacePlanner.plan(history, "私の名字は？")

        assertTrue(plan.verifiedMemoryFacts.isEmpty())
        assertEquals(LocalConversationPolicy.SYSTEM_INSTRUCTION, plan.systemInstruction)
    }

    @Test
    fun includesOnlyExplicitVerifiedMemoryFactsWithinBounds() {
        val provider = NpuConversationMemoryProvider {
            listOf(" 姓=佐藤 ", "色=赤", "色=赤", "x".repeat(100))
        }

        val plan = NpuConversationPrefacePlanner.plan(emptyList(), "質問", provider)

        assertEquals(listOf("姓=佐藤", "色=赤", "x".repeat(80)), plan.verifiedMemoryFacts)
        assertTrue(plan.systemInstruction.contains("検証済み記憶:姓=佐藤、色=赤"))
    }

    @Test
    fun rejectsBlankCurrentPrompt() {
        assertThrows(IllegalArgumentException::class.java) {
            NpuConversationPrefacePlanner.plan(emptyList(), " ")
        }
    }
}
