package io.github.ninbyo02.lami.asr.platform.sherpa

import io.github.ninbyo02.lami.asr.foundation.asr.AsrEvent
import io.github.ninbyo02.lami.asr.foundation.asr.AsrResult
import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole
import io.github.ninbyo02.lami.asr.foundation.testing.fixtureVerifiedModel
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.Job
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnlineAsrSessionTest {
    @Test
    fun `verified four-role model opens a 16 kHz CPU recognizer`() {
        val native = FakeSherpaNativeApi()
        val engine = SherpaOnlineAsrEngine(nativeApi = native, maxDecodeStepsPerCall = 8)

        val session = runImmediate { engine.open(fixtureVerifiedModel()) }

        val config = checkNotNull(native.createdConfig)
        val model = fixtureVerifiedModel()
        assertEquals(model.fileFor(ModelFileRole.ENCODER), config.encoder)
        assertEquals(model.fileFor(ModelFileRole.DECODER), config.decoder)
        assertEquals(model.fileFor(ModelFileRole.JOINER), config.joiner)
        assertEquals(model.fileFor(ModelFileRole.TOKENS), config.tokens)
        assertEquals(16_000, config.sampleRateHz)
        assertEquals("cpu", config.provider)
        assertTrue(session is SherpaOnlineAsrSession)
    }

    @Test
    fun `accept normalizes PCM16 and drains ready work without duplicate partials`() {
        val native = FakeSherpaNativeApi().apply {
            decodedResults.addAll(
                listOf(
                    NativeResult("こん", endpoint = false),
                    NativeResult("こん", endpoint = false),
                    NativeResult("こんにちは", endpoint = true),
                ),
            )
        }
        val session = openSession(native)

        val events = runImmediate {
            session.accept(Pcm16Frame(16_000, shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE)))
        }

        assertArrayEquals(floatArrayOf(-1.0f, 0.0f, 32767.0f / 32768.0f), native.acceptedSamples, 0.0f)
        assertEquals(
            listOf(AsrEvent.Partial("こん"), AsrEvent.Endpoint("こんにちは")),
            events,
        )
        assertEquals(3, native.decodeCalls)
        assertEquals(0, native.resetCalls)
    }

    @Test
    fun `finish output is authoritative after input-finished drain`() {
        val native = FakeSherpaNativeApi().apply {
            decodedResults += NativeResult("endpoint trigger", endpoint = true)
            resultAfterInputFinished = "authoritative final"
        }
        val session = openSession(native)

        val endpointEvents = runImmediate {
            session.accept(Pcm16Frame(16_000, shortArrayOf(1)))
        }
        val result = runImmediate { session.finish() }

        assertEquals(listOf(AsrEvent.Endpoint("endpoint trigger")), endpointEvents)
        assertEquals(AsrResult("authoritative final"), result)
        assertEquals(1, native.inputFinishedCalls)
    }

    @Test
    fun `closing engine rejects new opens but preserves existing session ownership`() {
        val native = FakeSherpaNativeApi()
        val engine = SherpaOnlineAsrEngine(nativeApi = native, maxDecodeStepsPerCall = 8)
        val existing = runImmediate { engine.open(fixtureVerifiedModel()) }

        engine.close()

        assertThrows(IllegalStateException::class.java) {
            runImmediate { engine.open(fixtureVerifiedModel()) }
        }
        runImmediate { existing.accept(Pcm16Frame(16_000, shortArrayOf(7))) }
        assertEquals(0, native.releaseStreamCalls)
        assertEquals(0, native.releaseRecognizerCalls)

        existing.close()
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `native failure is translated without native payload or cause`() {
        val canary = "private native payload"
        val native = FakeSherpaNativeApi().apply {
            acceptFailure = IllegalArgumentException(canary)
        }
        val session = openSession(native)

        val failure = assertThrows(IllegalStateException::class.java) {
            runImmediate { session.accept(Pcm16Frame(16_000, shortArrayOf(1))) }
        }

        assertFalse(failure.message.orEmpty().contains(canary))
        assertNull(failure.cause)
    }

    @Test
    fun `always-ready native stream is bounded per accept call`() {
        val native = FakeSherpaNativeApi().apply { alwaysReady = true }
        val session = openSession(native, maxDecodeStepsPerCall = 3)

        assertThrows(IllegalStateException::class.java) {
            runImmediate { session.accept(Pcm16Frame(16_000, shortArrayOf(1))) }
        }

        assertEquals(3, native.decodeCalls)
    }

    @Test
    fun `accept rejects non-16-kHz frames before crossing the native boundary`() {
        val sampleCanary = 12_345.toShort()
        val native = FakeSherpaNativeApi()
        val session = openSession(native)

        val failure = assertThrows(RuntimeException::class.java) {
            runImmediate { session.accept(Pcm16Frame(8_000, shortArrayOf(sampleCanary))) }
        }

        assertTrue(failure is IllegalArgumentException || failure is IllegalStateException)
        assertFalse(failure.message.orEmpty().contains(sampleCanary.toString()))
        assertNull(failure.cause)
        assertEquals(0, failure.suppressed.size)
        assertEquals(0, native.acceptCalls)
    }

    @Test
    fun `coroutine cancellation during decode releases resources and stays payload-free`() {
        val cancellationCanary = "private cancellation payload"
        val causeCanary = "private cancellation cause"
        val job = Job()
        val native = FakeSherpaNativeApi().apply {
            alwaysReady = true
            onDecode = {
                if (decodeCalls == 1) {
                    job.cancel(
                        CancellationException(cancellationCanary).apply {
                            initCause(IllegalStateException(causeCanary))
                        },
                    )
                }
            }
        }
        val session = openSession(native, maxDecodeStepsPerCall = 8)

        val failure = assertThrows(CancellationException::class.java) {
            runImmediate(job) {
                session.accept(Pcm16Frame(16_000, shortArrayOf(1)))
            }
        }

        assertFalse(failure.message.orEmpty().contains(cancellationCanary))
        assertFalse(failure.message.orEmpty().contains(causeCanary))
        assertNull(failure.cause)
        assertEquals(0, failure.suppressed.size)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `create-stream failure releases recognizer and stays payload-free`() {
        val canary = "private create-stream payload"
        val native = FakeSherpaNativeApi().apply {
            createStreamFailure = IllegalArgumentException(canary)
        }
        val engine = SherpaOnlineAsrEngine(nativeApi = native, maxDecodeStepsPerCall = 8)

        val failure = assertThrows(IllegalStateException::class.java) {
            runImmediate { engine.open(fixtureVerifiedModel()) }
        }

        assertFalse(failure.message.orEmpty().contains(canary))
        assertNull(failure.cause)
        assertEquals(0, failure.suppressed.size)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `stream cleanup failure still releases recognizer and reports only the first failure`() {
        val streamCanary = "private stream cleanup payload"
        val recognizerCanary = "private recognizer cleanup payload"
        val native = FakeSherpaNativeApi().apply {
            releaseStreamFailure = IllegalArgumentException(streamCanary)
            releaseRecognizerFailure = IllegalStateException(recognizerCanary)
        }
        val session = openSession(native)

        val failure = assertThrows(IllegalStateException::class.java) {
            session.close()
        }

        assertTrue(failure.message.orEmpty().contains("releaseStream"))
        assertFalse(failure.message.orEmpty().contains(streamCanary))
        assertFalse(failure.message.orEmpty().contains(recognizerCanary))
        assertNull(failure.cause)
        assertEquals(0, failure.suppressed.size)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `successful finish rejects further work and close releases each resource once`() {
        val native = FakeSherpaNativeApi().apply {
            resultAfterInputFinished = "final"
        }
        val session = openSession(native)

        assertEquals(AsrResult("final"), runImmediate { session.finish() })
        assertThrows(IllegalStateException::class.java) {
            runImmediate { session.accept(Pcm16Frame(16_000, shortArrayOf(1))) }
        }
        assertThrows(IllegalStateException::class.java) {
            runImmediate { session.finish() }
        }

        session.close()
        session.close()
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `recognizer config string never exposes verified model paths`() {
        val native = FakeSherpaNativeApi()
        val model = fixtureVerifiedModel()
        runImmediate {
            SherpaOnlineAsrEngine(native, maxDecodeStepsPerCall = 8).open(model)
        }

        val rendered = checkNotNull(native.createdConfig).toString()
        ModelFileRole.entries.forEach { role ->
            assertFalse(rendered.contains(model.fileFor(role).toString()))
        }
    }

    @Test
    fun `cancelled context cannot finish or publish final text`() {
        val canary = "private finish cancellation payload"
        val job = Job().apply { cancel(CancellationException(canary)) }
        val native = FakeSherpaNativeApi().apply { resultAfterInputFinished = "must not publish" }
        val session = openSession(native)

        val failure = assertThrows(CancellationException::class.java) {
            runImmediate(job) { session.finish() }
        }

        assertFalse(failure.message.orEmpty().contains(canary))
        assertNull(failure.cause)
        assertEquals(0, failure.suppressed.size)
        assertEquals(0, native.inputFinishedCalls)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `cancellation during finish drain releases ownership and stays payload-free`() {
        val canary = "private finish drain cancellation payload"
        val job = Job()
        val native = FakeSherpaNativeApi().apply {
            alwaysReady = true
            onDecode = {
                if (decodeCalls == 1) job.cancel(CancellationException(canary))
            }
        }
        val session = openSession(native, maxDecodeStepsPerCall = 8)

        val failure = assertThrows(CancellationException::class.java) {
            runImmediate(job) { session.finish() }
        }

        assertFalse(failure.message.orEmpty().contains(canary))
        assertNull(failure.cause)
        assertEquals(1, native.decodeCalls)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `finish decode-limit failure terminally releases session and prevents retry`() {
        val native = FakeSherpaNativeApi().apply { alwaysReady = true }
        val session = openSession(native, maxDecodeStepsPerCall = 2)

        assertThrows(IllegalStateException::class.java) {
            runImmediate { session.finish() }
        }

        assertEquals(1, native.inputFinishedCalls)
        assertEquals(2, native.decodeCalls)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
        assertThrows(IllegalStateException::class.java) {
            runImmediate { session.finish() }
        }
        assertEquals(1, native.inputFinishedCalls)
    }

    @Test
    fun `native accept failure terminally releases session and prevents retry`() {
        val canary = "private terminal accept payload"
        val native = FakeSherpaNativeApi().apply {
            acceptFailure = IllegalArgumentException(canary)
        }
        val session = openSession(native)

        val failure = assertThrows(IllegalStateException::class.java) {
            runImmediate { session.accept(Pcm16Frame(16_000, shortArrayOf(1))) }
        }

        assertFalse(failure.message.orEmpty().contains(canary))
        assertEquals(1, native.acceptCalls)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
        assertThrows(IllegalStateException::class.java) {
            runImmediate { session.accept(Pcm16Frame(16_000, shortArrayOf(2))) }
        }
        assertEquals(1, native.acceptCalls)
    }

    @Test
    fun `cancellation during accept text retrieval prevents transcript publication`() {
        val canary = "private accept text cancellation payload"
        val job = Job()
        val native = FakeSherpaNativeApi().apply {
            decodedResults += NativeResult("must not publish", endpoint = false)
            onText = { job.cancel(CancellationException(canary)) }
        }
        val session = openSession(native)

        val failure = assertThrows(CancellationException::class.java) {
            runImmediate(job) {
                session.accept(Pcm16Frame(16_000, shortArrayOf(1)))
            }
        }

        assertFalse(failure.message.orEmpty().contains(canary))
        assertNull(failure.cause)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `cancellation during final text retrieval prevents final publication`() {
        val canary = "private final text cancellation payload"
        val job = Job()
        val native = FakeSherpaNativeApi().apply {
            resultAfterInputFinished = "must not publish"
            onText = { job.cancel(CancellationException(canary)) }
        }
        val session = openSession(native)

        val failure = assertThrows(CancellationException::class.java) {
            runImmediate(job) { session.finish() }
        }

        assertFalse(failure.message.orEmpty().contains(canary))
        assertNull(failure.cause)
        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    @Test
    fun `session and engine release are idempotent`() {
        val native = FakeSherpaNativeApi()
        val engine = SherpaOnlineAsrEngine(nativeApi = native, maxDecodeStepsPerCall = 8)
        val session = runImmediate { engine.open(fixtureVerifiedModel()) }

        session.close()
        session.close()
        engine.close()
        engine.close()

        assertEquals(1, native.releaseStreamCalls)
        assertEquals(1, native.releaseRecognizerCalls)
    }

    private fun openSession(
        native: FakeSherpaNativeApi,
        maxDecodeStepsPerCall: Int = 8,
    ) = runImmediate {
        SherpaOnlineAsrEngine(native, maxDecodeStepsPerCall).open(fixtureVerifiedModel())
    }

    private fun <T> runImmediate(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend () -> T,
    ): T {
        var completion: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = context

                override fun resumeWith(result: Result<T>) {
                    completion = result
                }
            },
        )
        return checkNotNull(completion).getOrThrow()
    }

    private data class NativeResult(val text: String, val endpoint: Boolean)

    private class FakeSherpaNativeApi : SherpaNativeApi {
        var createdConfig: SherpaOnlineRecognizerConfig? = null
        var acceptedSamples = floatArrayOf()
        val decodedResults = ArrayDeque<NativeResult>()
        var resultAfterInputFinished: String? = null
        var createStreamFailure: RuntimeException? = null
        var acceptFailure: RuntimeException? = null
        var releaseStreamFailure: RuntimeException? = null
        var releaseRecognizerFailure: RuntimeException? = null
        var onDecode: (() -> Unit)? = null
        var onText: (() -> Unit)? = null
        var alwaysReady = false
        var acceptCalls = 0
        var decodeCalls = 0
        var resetCalls = 0
        var inputFinishedCalls = 0
        var releaseStreamCalls = 0
        var releaseRecognizerCalls = 0
        private var currentResult = NativeResult("", endpoint = false)
        private var inputFinished = false

        override fun createRecognizer(config: SherpaOnlineRecognizerConfig): Long {
            createdConfig = config
            return RECOGNIZER
        }

        override fun createStream(recognizerHandle: Long): Long {
            createStreamFailure?.let { throw it }
            assertEquals(RECOGNIZER, recognizerHandle)
            return STREAM
        }

        override fun acceptWaveform(streamHandle: Long, sampleRateHz: Int, samples: FloatArray) {
            acceptCalls += 1
            acceptFailure?.let { throw it }
            assertEquals(STREAM, streamHandle)
            assertEquals(16_000, sampleRateHz)
            acceptedSamples = samples.copyOf()
        }

        override fun isReady(recognizerHandle: Long, streamHandle: Long): Boolean =
            alwaysReady || decodedResults.isNotEmpty()

        override fun decode(recognizerHandle: Long, streamHandle: Long) {
            decodeCalls += 1
            onDecode?.invoke()
            if (decodedResults.isNotEmpty()) currentResult = decodedResults.removeFirst()
        }

        override fun text(recognizerHandle: Long, streamHandle: Long): String {
            onText?.invoke()
            return if (inputFinished) resultAfterInputFinished ?: currentResult.text else currentResult.text
        }

        override fun isEndpoint(recognizerHandle: Long, streamHandle: Long): Boolean =
            currentResult.endpoint

        override fun reset(recognizerHandle: Long, streamHandle: Long) {
            resetCalls += 1
            currentResult = NativeResult("", endpoint = false)
        }

        override fun inputFinished(streamHandle: Long) {
            inputFinishedCalls += 1
            inputFinished = true
        }

        override fun releaseStream(streamHandle: Long) {
            releaseStreamCalls += 1
            releaseStreamFailure?.let { throw it }
        }

        override fun releaseRecognizer(recognizerHandle: Long) {
            releaseRecognizerCalls += 1
            releaseRecognizerFailure?.let { throw it }
        }

        private companion object {
            const val RECOGNIZER = 11L
            const val STREAM = 22L
        }
    }
}
