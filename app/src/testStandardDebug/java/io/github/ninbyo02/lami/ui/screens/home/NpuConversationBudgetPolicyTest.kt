package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class NpuConversationBudgetPolicyTest {
    private fun user(s: String) = LocalConversationTurn(LocalConversationRole.USER, s)
    private fun model(s: String) = LocalConversationTurn(LocalConversationRole.MODEL, s)

    @Test fun reservesLessOutputInsteadOfDroppingShortNameMemory() {
        val history = listOf(user("私の名前は佐藤です。覚えておいてください。"), model("佐藤さんですね。覚えました。"))
        val plan = NpuConversationBudgetPolicy.plan(history, "私の名前は何ですか。名前だけ答えてください。", 1024)
        assertTrue(plan.admitted)
        assertEquals(history, plan.initialTurns)
        assertTrue(plan.reservedOutputTokens in 128 until 256)
        assertTrue(plan.estimatedInputTokens + plan.reservedOutputTokens <= 512)
    }

    @Test fun retainsShortPairFromDeviceSequence() {
        val history = listOf(user("長文後の確認です。2足す3の答えだけ返してください。"), model("5"))
        val plan = NpuConversationBudgetPolicy.plan(history, "停止後の再送信です。4足す5の答えだけ返してください。", 1024)
        assertEquals(history, plan.initialTurns)
        assertTrue(plan.admitted)
    }

    @Test fun longReplyDoesNotExhaustNextQuestion() {
        val plan = NpuConversationBudgetPolicy.plan(listOf(user("物語"), model("夏".repeat(599))), "3足す4", 32)
        assertTrue(plan.admitted)
        assertTrue(plan.initialTurns.isEmpty())
        assertTrue(plan.estimatedInputTokens + plan.reservedOutputTokens <= 512)
        assertEquals(128, plan.reservedOutputTokens)
    }

    @Test fun preservesRecentCompletePairsWithoutDuplicatingCurrentPrompt() {
        val recent = listOf(user("私は佐藤です"), model("こんにちは"))
        val plan = NpuConversationBudgetPolicy.plan(listOf(user("昔"), model("昔".repeat(599))) + recent + user("今の質問"), "今の質問", 32)
        assertEquals(recent, plan.initialTurns)
        assertTrue(plan.admitted)
    }

    @Test fun rejectsOversizedCurrentInputWithoutTruncatingIt() {
        val plan = NpuConversationBudgetPolicy.plan(emptyList(), "あ".repeat(600), 128)
        assertFalse(plan.admitted)
        assertTrue(plan.initialTurns.isEmpty())
    }

    @Test fun doesNotKeepOrphanAssistantOrCrossMissingTurns() {
        val plan = NpuConversationBudgetPolicy.plan(listOf(model("孤立"), user("失敗した質問"), user("新しい質問"), model("答え")), "次", 128)
        assertEquals(listOf(user("新しい質問"), model("答え")), plan.initialTurns)
    }

    @Test fun supplementaryCharactersAreCountedAsCodePoints() {
        assertEquals(3, NpuConversationBudgetPolicy.estimate("😀😀"))
    }
}
