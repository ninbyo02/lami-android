package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class NpuConversationBudgetPolicyTest {
    private fun user(s: String) = LocalConversationTurn(LocalConversationRole.USER, s)
    private fun model(s: String) = LocalConversationTurn(LocalConversationRole.MODEL, s)

    @Test fun reservesLessOutputInsteadOfDroppingShortNameMemory() {
        val history = listOf(user("私の名前は佐藤です。覚えておいてください。"), model("佐藤さんですね。覚えました。"))
        val plan = NpuConversationBudgetPolicy.plan(history, "私の名前は何ですか。名前だけ答えてください。", 1024, totalTokens = 512)
        assertTrue(plan.admitted)
        assertEquals(history, plan.initialTurns)
        assertTrue(plan.reservedOutputTokens in 128 until 256)
        assertTrue(plan.estimatedInputTokens + plan.reservedOutputTokens <= 512)
    }

    @Test fun retainsShortPairFromDeviceSequence() {
        val history = listOf(user("長文後の確認です。2足す3の答えだけ返してください。"), model("5"))
        val plan = NpuConversationBudgetPolicy.plan(history, "停止後の再送信です。4足す5の答えだけ返してください。", 1024, totalTokens = 512)
        assertEquals(history, plan.initialTurns)
        assertTrue(plan.admitted)
    }

    @Test fun longReplyDoesNotExhaustNextQuestion() {
        val plan = NpuConversationBudgetPolicy.plan(listOf(user("物語"), model("夏".repeat(599))), "3足す4", 32, totalTokens = 512)
        assertTrue(plan.admitted)
        assertTrue(plan.initialTurns.isEmpty())
        assertTrue(plan.estimatedInputTokens + plan.reservedOutputTokens <= 512)
        assertEquals(128, plan.reservedOutputTokens)
    }

    @Test fun preservesRecentCompletePairsWithoutDuplicatingCurrentPrompt() {
        val recent = listOf(user("私は佐藤です"), model("こんにちは"))
        val plan = NpuConversationBudgetPolicy.plan(listOf(user("昔"), model("昔".repeat(599))) + recent + user("今の質問"), "今の質問", 32, totalTokens = 512)
        assertEquals(recent, plan.initialTurns)
        assertTrue(plan.admitted)
    }

    @Test fun rejectsOversizedCurrentInputWithoutTruncatingIt() {
        val plan = NpuConversationBudgetPolicy.plan(emptyList(), "あ".repeat(600), 128, totalTokens = 512)
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

    @Test fun defaultCapacityPreservesHistoryThatFitsTheConfiguredEngine() {
        val history = listOf(user("物語"), model("夏".repeat(599)))
        val plan = NpuConversationBudgetPolicy.plan(history, "3足す4", 32)
        assertTrue(plan.admitted)
        assertEquals(history, plan.initialTurns)
        assertTrue(plan.estimatedInputTokens + plan.reservedOutputTokens <= NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT)
    }

    @Test fun currentImagesReserveTheirVisualTokensWithoutTruncation() {
        val plain = NpuConversationBudgetPolicy.plan(emptyList(), "look", 128)
        val images = NpuConversationBudgetPolicy.plan(emptyList(), "look", 128, currentImageCount = 10)
        assertTrue(images.admitted)
        assertEquals(10 * NpuConversationBudgetPolicy.IMAGE_TOKEN_ALLOWANCE,
            images.estimatedInputTokens - plain.estimatedInputTokens)
        assertTrue(images.estimatedInputTokens + images.reservedOutputTokens <= 4096)
    }

    @Test fun imageHistoryIsRetainedWithItsVisualCostForTextFollowups() {
        val history = listOf(user("look").copy(imageFilePaths = List(10) { "/image$it.jpg" }), model("pictures"))
        val plan = NpuConversationBudgetPolicy.plan(history, "Which is blue?", 128)
        assertTrue(plan.admitted)
        assertEquals(history, plan.initialTurns)
        assertTrue(plan.estimatedInputTokens >= 10 * NpuConversationBudgetPolicy.IMAGE_TOKEN_ALLOWANCE)
    }

    @Test fun currentAndHistoricalImagesShareTheTenImageCapacity() {
        val history = listOf(user("look").copy(imageFilePaths = listOf("/old.jpg")), model("blue"))
        val fits = NpuConversationBudgetPolicy.plan(history, "new", 128, currentImageCount = 9)
        assertTrue(fits.admitted)
        assertEquals(history, fits.initialTurns)
        val full = NpuConversationBudgetPolicy.plan(history, "new", 128, currentImageCount = 10)
        assertTrue(full.admitted)
        assertTrue(full.initialTurns.isEmpty())
    }

    @Test fun overlongImageHistoryCannotSilentlyBecomeTextOnly() {
        val history = listOf(user("look").copy(imageFilePaths = List(10) { "/image$it.jpg" }), model("long ".repeat(300)))
        val plan = NpuConversationBudgetPolicy.plan(history, "Which is blue?", 128)
        assertFalse(plan.admitted)
        assertTrue(plan.initialTurns.isEmpty())
    }

    @Test fun imageOnlyHistoryIsNotDroppedAsAnEmptyUserTurn() {
        val history = listOf(user("").copy(imageFilePaths = listOf("/image.jpg")), model("blue"))
        val plan = NpuConversationBudgetPolicy.plan(history, "color?", 128)
        assertTrue(plan.admitted)
        assertEquals(history, plan.initialTurns)
    }

    @Test fun tooManyCurrentImagesAreNotAdmitted() {
        assertFalse(NpuConversationBudgetPolicy.plan(emptyList(), "look", 128, currentImageCount = 11).admitted)
    }
}
