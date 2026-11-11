package io.github.ninbyo02.lami.tts

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LamiSupertonicPcmStreamTest {
    @Test
    fun firstChunkIsConsumedBeforeNativeContinuesGeneratingTheSentence() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { native ->
            val events = mutableListOf<String>()
            val firstConsumed = AtomicBoolean()
            val output = mutableListOf<FloatArray>()
            val value = withTimeout(5_000) {
                streamSupertonicNativePcm(native, generate = { callback ->
                    events += "native-first-window"
                    callback(floatArrayOf(0.1f, 0.2f))
                    check(firstConsumed.get()) { "Native continued before playback acknowledged the first PCM" }
                    events += "native-next-window"
                    callback(floatArrayOf(0.3f))
                    "complete"
                }, consume = { chunk ->
                    events += "consume-${output.size}"
                    output += chunk
                    firstConsumed.set(true)
                })
            }
            assertEquals("complete", value)
            assertEquals(listOf("native-first-window", "consume-0", "native-next-window", "consume-1"), events)
            assertArrayEquals(floatArrayOf(0.1f, 0.2f), output[0], 0f)
            assertArrayEquals(floatArrayOf(0.3f), output[1], 0f)
        }
    }

    @Test
    fun retainedConsumerBufferDoesNotChangeWhenNativeReusesItsArray() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { native ->
            var received: FloatArray? = null
            streamSupertonicNativePcm(native, generate = { callback ->
                val buffer = floatArrayOf(0.25f, -0.5f)
                callback(buffer)
                buffer.fill(0f)
            }, consume = { received = it })
            assertArrayEquals(floatArrayOf(0.25f, -0.5f), received, 0f)
        }
    }

    @Test
    fun cancellationWaitsForTheNativeCallBeforeReturningCleanup() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { native ->
            val consumed = CompletableDeferred<Unit>()
            val nativeBlocked = CountDownLatch(1)
            val allowReturn = CountDownLatch(1)
            val nativeCompleted = AtomicBoolean()
            val task = launch {
                streamSupertonicNativePcm(native, generate = { callback ->
                    callback(floatArrayOf(0.1f))
                    nativeBlocked.countDown()
                    try {
                        check(allowReturn.await(5, TimeUnit.SECONDS))
                    } finally {
                        nativeCompleted.set(true)
                    }
                }, consume = { consumed.complete(Unit); Unit })
            }
            try {
                withTimeout(5_000) { consumed.await() }
                withContext(Dispatchers.IO) { assertTrue(nativeBlocked.await(5, TimeUnit.SECONDS)) }
                val cleanup = launch { task.cancelAndJoin() }
                yield()
                assertFalse(cleanup.isCompleted)
                assertFalse(nativeCompleted.get())
                allowReturn.countDown()
                withTimeout(5_000) { cleanup.join() }
                assertTrue(nativeCompleted.get())
            } finally {
                allowReturn.countDown()
                task.cancelAndJoin()
            }
        }
    }

    @Test
    fun cancellationUnblocksNativeCallbackWaitingForConsumerAcknowledgement() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { native ->
            val consuming = CompletableDeferred<Unit>()
            val callbackReturned = AtomicBoolean()
            val task = launch {
                streamSupertonicNativePcm(native, generate = { callback ->
                    callback(floatArrayOf(0.1f))
                    callbackReturned.set(true)
                    // Closed consumers must also tolerate later JNI callbacks.
                    callback(floatArrayOf(0.2f))
                }, consume = {
                    consuming.complete(Unit)
                    awaitCancellation()
                })
            }
            withTimeout(5_000) { consuming.await() }
            withTimeout(5_000) { task.cancelAndJoin() }
            assertTrue(callbackReturned.get())
        }
    }

    @Test
    fun consumerFailureDoesNotThrowThroughNativeAndWaitsForNativeCompletion() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { native ->
            val completed = AtomicBoolean()
            var consumed = 0
            val result = runCatching {
                withTimeout(5_000) {
                    streamSupertonicNativePcm(native, generate = { callback ->
                        callback(floatArrayOf(0.1f))
                        callback(floatArrayOf(0.2f))
                        completed.set(true)
                    }, consume = {
                        consumed += 1
                        error("player failed")
                    })
                }
            }
            assertEquals("player failed", result.exceptionOrNull()?.message)
            assertEquals(1, consumed)
            assertTrue(completed.get())
        }
    }

    @Test
    fun nativeFailureIsReportedAfterItsCallbackWasConsumed() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { native ->
            var consumed = false
            val result = runCatching {
                withTimeout(5_000) {
                    streamSupertonicNativePcm(native, generate = { callback ->
                        callback(floatArrayOf(0.1f))
                        error("native failed")
                    }, consume = { consumed = true })
                }
            }
            assertTrue(consumed)
            assertEquals("native failed", result.exceptionOrNull()?.message)
        }
    }
}
