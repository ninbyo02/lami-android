package io.github.ninbyo02.lami.tts

import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class LamiVerifiedDecoderPreloadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun preloadStartsOnlyAfterVerificationHookAndFirstDecodeWaitsForIt() = runTest {
        val loaded = CompletableDeferred<Unit>()
        val statuses = mutableListOf<Result<Unit>>()
        var starts = 0
        val preload = LamiVoiceStreamingProbe.VerifiedDecoderPreload(
            { starts++; loaded.await() }, statuses::add)
        val firstDecode = async { preload.awaitBeforeDecode() }
        runCurrent()
        assertEquals(0, starts)
        assertFalse(firstDecode.isCompleted)

        preload.start(this)
        runCurrent()
        assertEquals(1, starts)
        assertFalse(firstDecode.isCompleted)
        loaded.complete(Unit)
        firstDecode.await()
        preload.awaitBeforeDecode()

        assertEquals(1, statuses.size)
        assertTrue(statuses.single().isSuccess)
    }

    @Test fun failedPreloadIsJoinedAndReportedOnceBeforeNormalDecodeCanRetry() = runTest {
        val statuses = mutableListOf<Result<Unit>>()
        val preload = LamiVoiceStreamingProbe.VerifiedDecoderPreload(
            { error("Preload unavailable") }, statuses::add)
        preload.start(this)

        preload.awaitBeforeDecode()
        preload.awaitBeforeDecode()

        assertEquals(1, statuses.size)
        assertEquals("Preload unavailable", statuses.single().exceptionOrNull()?.message)
    }

    @Test fun internalPreloadTimeoutReportsFailureWithoutCancellingGeneration() = runTest {
        val statuses = mutableListOf<Result<Unit>>()
        val preload = LamiVoiceStreamingProbe.VerifiedDecoderPreload(
            { withTimeout(1) { delay(100) } }, statuses::add)
        preload.start(this)
        preload.awaitBeforeDecode()
        assertEquals(1, statuses.size)
        assertTrue(statuses.single().isFailure)
    }

    @Test fun generationCancellationJoinsPreloadBindingCleanup() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val statuses = mutableListOf<Result<Unit>>()
        var released = false
        val preload = LamiVoiceStreamingProbe.VerifiedDecoderPreload({
            try { awaitCancellation() } finally {
                withContext(NonCancellable) { cleanup.await(); released = true }
            }
        }, statuses::add)
        val generation = async {
            coroutineScope {
                preload.start(this)
                awaitCancellation()
            }
        }
        runCurrent()
        val stop = async { generation.cancelAndJoin() }
        runCurrent()
        assertFalse(stop.isCompleted)
        assertFalse(released)

        cleanup.complete(Unit)
        stop.await()
        assertTrue(released)
        assertTrue(statuses.isEmpty())
    }

    @Test fun mismatchedBundleCannotInvokePreloadHook() = runTest {
        val root = temporary.newFolder()
        root.resolve("payload.bin").writeText("not the expected model")
        root.resolve("voice-text-bundle.json").writeText(JSONObject()
            .put("version", 2).put("capacity", 256)
            .put("sha256", JSONObject().put("payload.bin", "0".repeat(64))).toString())
        var invoked = false
        val failure = runCatching {
            LamiPreparedVoiceSynthesizer.generateText(root, "こんにちは。", LamiVoiceModuleCache.Session("invalid"),
                onBundleVerified = { invoked = true })
        }
        assertTrue(failure.isFailure)
        assertFalse(invoked)
    }

    @Test fun verifiedBundleInvokesHookBeforeAnyModelLoading() = runTest {
        val root = temporary.newFolder()
        val payload = "verified payload".toByteArray()
        root.resolve("payload.bin").writeBytes(payload)
        val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        root.resolve("voice-text-bundle.json").writeText(JSONObject()
            .put("version", 2).put("capacity", 256)
            .put("sha256", JSONObject().put("payload.bin", hash)).toString())
        var invoked = false
        val failure = runCatching {
            LamiPreparedVoiceSynthesizer.generateText(root, "こんにちは。", LamiVoiceModuleCache.Session("valid"),
                onBundleVerified = { invoked = true; error("Stop before model load") })
        }
        assertEquals("Stop before model load", failure.exceptionOrNull()?.message)
        assertTrue(invoked)
    }
}
