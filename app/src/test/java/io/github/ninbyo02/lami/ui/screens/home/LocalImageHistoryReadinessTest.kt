package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalImageHistoryReadinessTest {
    private fun availability(target: InferenceTarget = InferenceTarget.LOCAL, ready: Boolean, running: Boolean = false) =
        resolveChatSendAvailability(target, "remote", "/model.litertlm", "http://localhost:11434", true, false, running,
            localChatHistoryReady = ready)

    @Test fun localFollowupWaitsForPersistedHistory() {
        val result = availability(ready = false)
        assertFalse(result.enabled)
        assertEquals(ChatSendBlockedReason.LOCAL_HISTORY_LOADING, result.blockedReason)
    }

    @Test fun loadedLocalHistoryAllowsSending() {
        assertTrue(availability(ready = true).enabled)
    }

    @Test fun stopRemainsAvailableWhileHistoryLoads() {
        assertTrue(availability(ready = false, running = true).enabled)
    }

    @Test fun serverSendingDoesNotDependOnLocalHistory() {
        assertTrue(availability(InferenceTarget.SERVER, ready = false).enabled)
    }

    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }
    private fun source(name: String) = File(root, "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/$name.kt").readText()

    @Test fun chatCollectionRestartsWithEmptyLoadingStateForEachChat() {
        val chat = source("ChatScreen")
        val collection = chat.substringAfter("val allChatsState = effectiveChatId?.let {").substringBefore("val allChatsOrNull")
        assertTrue(collection.contains("key(it)"))
        assertTrue(collection.contains("collectAsState(initial = null)"))
        assertTrue(chat.contains("effectiveChatId == null || allChatsOrNull != null"))
        assertEquals(2, Regex("localChatHistoryReady = localChatHistoryReady").findAll(chat).count())
    }

    @Test fun firstSubmissionIsNotConsumedBeforeItsNewChatHistoryLoads() {
        val effect = source("ChatScreen")
            .substringAfter("LaunchedEffect(pendingFirstChatSubmission, effectiveChatId, localChatHistoryReady) {")
            .substringBefore("LaunchedEffect(")
        val guard = effect.indexOf("if (effectiveChatId == null || !localChatHistoryReady) return@LaunchedEffect")
        assertTrue(guard >= 0)
        assertTrue(effect.indexOf("pendingFirstChatSubmission = null") > guard)
    }

    @Test fun npuRechecksCancellationAfterSynchronousImagePrefillBeforeSending() {
        val afterCreate = source("NpuKotlinConversationProductRoute")
            .substringAfter("conversation = activeEngine.createConversation(")
            .substringBefore("activeConversation.sendMessageAsync(")
        assertTrue(afterCreate.contains("currentCoroutineContext().ensureActive()"))
    }

    @Test fun cpuGpuRecheckCancellationAfterSynchronousImagePrefillBeforeSending() {
        val direct = source("LocalStreamingRunner").substringAfter("private suspend fun runOfficialLiteRtLmDirect(")
        val afterCreate = direct.substringAfter("conversation = engine.createConversation(")
            .substringBefore("conversation.sendMessageAsync(")
        assertTrue(afterCreate.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(direct.contains("if (throwable is CancellationException) throw throwable"))
    }

    @Test fun comparisonOnlyHistoryAndSamplerAreExplicitlyRecorded() {
        val benchmark = File(root,
            "app/src/androidTest/java/io/github/ninbyo02/lami/ui/screens/home/LocalMultiImageBenchmarkInstrumentedTest.kt").readText()
        assertTrue(benchmark.contains("getString(\"imageHistoryMode\", \"product\")"))
        assertTrue(benchmark.contains("if (historyMode == \"fixed\")"))
        assertTrue(benchmark.contains("put(\"restored_history_model_text\", historyReply)"))
        assertTrue(benchmark.contains("put(\"seed\", LocalConversationPolicy.SAMPLER_SEED)"))
        assertTrue(benchmark.contains("put(\"native_per_generation_output_cap_enforced\", false)"))
        assertTrue(benchmark.contains("put(\"trial\", trial)"))
    }
}
