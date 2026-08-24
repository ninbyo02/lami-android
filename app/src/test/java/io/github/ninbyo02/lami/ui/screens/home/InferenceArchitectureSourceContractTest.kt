package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceArchitectureSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `ChatScreen delegates lifecycle persistence and stats decisions to extracted owners`() {
        val chat = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()

        val streamingLifecycleBlock = chat
            .substringAfter("suspend fun startStreamingAssistantLifecycleSerialized(")
            .substringBefore("fun sanitizeTextForTts(")
        val terminalLifecycleBlock = chat
            .substringAfter("suspend fun cancelStreamingAssistantLifecycleSerialized(")
            .substringBefore("fun resetStreamingAssistantPlaceholderId(")

        assertTrue(chat.contains("AssistantMessageLifecycleCoordinator("))
        assertTrue(streamingLifecycleBlock.contains("assistantMessageLifecycleCoordinator.upsertPlaceholder("))
        assertTrue(streamingLifecycleBlock.contains("existingMessageId = existingId"))
        assertFalse(streamingLifecycleBlock.contains("STREAM lifecycle start kept existing"))
        assertTrue(streamingLifecycleBlock.contains("assistantMessageLifecycleCoordinator.complete("))
        assertTrue(terminalLifecycleBlock.contains("assistantMessageLifecycleCoordinator.fail("))
        assertTrue(terminalLifecycleBlock.contains("assistantMessageLifecycleCoordinator.cancel("))
        assertTrue(chat.contains("InferenceStatsFactory.fromLocalTrace("))
        assertTrue(chat.contains("InferenceStatsFactory.fromNpuStandardRoute("))
        assertFalse(streamingLifecycleBlock.contains("AssistantMessageLifecycle.planPlaceholder("))
        assertFalse(streamingLifecycleBlock.contains("AssistantMessageLifecycle.planCompletion("))
        assertFalse(streamingLifecycleBlock.contains("AssistantMessageLifecycle.planFailure("))
        assertFalse(streamingLifecycleBlock.contains("viewModel.insertAssistantMessageAndReturnId("))
        assertFalse(streamingLifecycleBlock.contains("viewModel.markAssistantMessageGenerating("))
        assertFalse(streamingLifecycleBlock.contains("viewModel.updateGeneratingAssistantMessageContent("))
        assertFalse(streamingLifecycleBlock.contains("viewModel.completeAssistantMessage("))
        assertFalse(streamingLifecycleBlock.contains("viewModel.failAssistantMessage("))
        assertFalse(terminalLifecycleBlock.contains("viewModel.failAssistantMessage("))
        assertFalse(terminalLifecycleBlock.contains("viewModel.cancelAssistantMessage("))
        assertTrue(streamingLifecycleBlock.contains("streamingAssistantPersistMutex.withLock"))
        assertTrue(terminalLifecycleBlock.contains("streamingAssistantPersistMutex.withLock"))
        assertTrue(chat.contains("val streamingAssistantPersistMutex = remember(effectiveChatId)"))
        assertFalse(chat.contains("private fun buildLocalInferenceStatsFromTrace("))
        assertFalse(chat.contains("private fun buildNpuStandardRouteInferenceStats("))
        assertFalse(chat.contains("internal fun createAssistantMessage("))
    }

    @Test
    fun `normal server NPU and local starts use the lifecycle coordinator`() {
        val chat = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val serverRoute = chat
            .substringAfter("InferenceTarget.SERVER -> {")
            .substringBefore("InferenceTarget.LOCAL -> {")
        val npuBeforeInference = chat
            .substringAfter("beforeInference = { chatId ->")
            .substringBefore("runInference = {")
        val localLifecycleStart = chat
            .substringAfter("if (streamingAssistantMessageId == null) {")
            .substringBefore("localStopRequested = false")
        val remoteUiState = chat
            .substringAfter("is UiState.Error -> {")
            .substringBefore("LaunchedEffect(lamiUiState.lastInteractionTimeMs")

        assertTrue(serverRoute.contains("startStreamingAssistantLifecycleSerialized("))
        assertTrue(npuBeforeInference.contains("startStreamingAssistantLifecycleSerialized("))
        assertTrue(localLifecycleStart.contains("startStreamingAssistantLifecycleSerialized("))
        assertTrue(remoteUiState.contains("finalizeStreamingAssistantFailureSerialized("))
        assertTrue(remoteUiState.contains("upsertStreamingAssistantPlaceholderSerialized("))
        assertTrue(chat.contains("cancelStreamingAssistantLifecycleSerialized(previousId)"))

        for (block in listOf(serverRoute, npuBeforeInference, localLifecycleStart, remoteUiState)) {
            assertFalse(block.contains("viewModel.markAssistantMessageGenerating("))
            assertFalse(block.contains("viewModel.updateGeneratingAssistantMessageContent("))
            assertFalse(block.contains("viewModel.completeAssistantMessage("))
            assertFalse(block.contains("viewModel.failAssistantMessage("))
            assertFalse(block.contains("viewModel.cancelAssistantMessage("))
        }
    }

    @Test
    fun `coordinator owns lifecycle store execution`() {
        val coordinator = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantMessageLifecycleCoordinator.kt",
        ).readText()

        assertTrue(coordinator.contains("interface AssistantMessageLifecycleStore"))
        assertTrue(coordinator.contains("private val persistenceMutex = Mutex()"))
        assertTrue(coordinator.contains("persistenceMutex.withLock"))
        assertTrue(coordinator.contains("AssistantMessageLifecycle.planPlaceholder("))
        assertTrue(coordinator.contains("AssistantMessageLifecycle.planCompletion("))
        assertTrue(coordinator.contains("AssistantMessageLifecycle.planFailure("))
        assertTrue(coordinator.contains("AssistantMessageLifecycle.planCancellation("))
        assertTrue(coordinator.contains("store.insertAssistantMessage("))
        assertTrue(coordinator.contains("store.updateGeneratingAssistantMessageContent("))
        assertTrue(coordinator.contains("store.completeAssistantMessage("))
        assertTrue(coordinator.contains("store.failAssistantMessage("))
        assertTrue(coordinator.contains("store.cancelAssistantMessage("))
        assertTrue(coordinator.contains("store.updateMessage("))
    }

    @Test
    fun `terminal assistant rows have no overwrite payload in lifecycle plans`() {
        val lifecycle = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantMessageLifecycle.kt",
        ).readText()

        assertTrue(lifecycle.contains("existingMessage.status !in MessageStatus.IN_FLIGHT"))
        assertTrue(lifecycle.contains("AssistantMessageLifecycleAction.KEEP_EXISTING"))
        assertTrue(lifecycle.contains("mergePayloadIntoTerminalMessage"))
    }
}
