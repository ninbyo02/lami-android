package io.github.ninbyo02.lami.asr.foundation.session

import io.github.ninbyo02.lami.asr.foundation.asr.AsrEngine
import io.github.ninbyo02.lami.asr.foundation.asr.AsrEvent
import io.github.ninbyo02.lami.asr.foundation.asr.AsrResult
import io.github.ninbyo02.lami.asr.foundation.asr.AsrSession
import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.foundation.audio.AudioFrameSource
import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import io.github.ninbyo02.lami.asr.foundation.testing.fixtureVerifiedModel
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptOrigin
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptTiming
import io.github.ninbyo02.lami.asr.foundation.vad.VadEngine
import io.github.ninbyo02.lami.asr.foundation.vad.VadEvent
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrSessionCoordinatorTest {
    @Test
    fun `cancel after final transcript publication is linearized before consume gate release`() {
        val operations = mutableListOf<String>()
        val gateBoundaryReached = CountDownLatch(1)
        val releaseGateBoundary = CountDownLatch(1)
        val cancelIntentInstalled = CountDownLatch(1)
        val consumerFailure = AtomicReference<Throwable?>()
        val cancelFailure = AtomicReference<Throwable?>()
        val frame = Pcm16Frame(16_000, ShortArray(320) { 3 })
        val source = RecordingSource(ArrayDeque(listOf(frame)), operations)
        val session = RecordingSession(
            listOf(AsrEvent.Endpoint("private endpoint")),
            AsrResult("private final"),
            operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations)).also {
            it.beforeConsumeGateReleaseForTest = {
                gateBoundaryReached.countDown()
                check(releaseGateBoundary.await(5, TimeUnit.SECONDS))
            }
            it.afterConcurrentCancellationRequestForTest = {
                cancelIntentInstalled.countDown()
            }
        }
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }
        val consumer = Thread {
            try {
                runImmediate { coordinator.consumeNextFrame() }
            } catch (failure: Throwable) {
                consumerFailure.set(failure)
            }
        }
        val canceller = Thread {
            try {
                coordinator.cancelAndDiscard()
            } catch (failure: Throwable) {
                cancelFailure.set(failure)
            }
        }

        consumer.start()
        assertTrue(gateBoundaryReached.await(5, TimeUnit.SECONDS))
        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        canceller.start()
        assertTrue(cancelIntentInstalled.await(5, TimeUnit.SECONDS))
        releaseGateBoundary.countDown()
        consumer.join(5_000)
        canceller.join(5_000)

        assertFalse(consumer.isAlive)
        assertFalse(canceller.isAlive)
        assertNull(cancelFailure.get())
        assertTrue(consumerFailure.get() is CancellationException)
        assertEquals(AsrPhase.READY, coordinator.state.phase)
        assertEquals("", coordinator.state.partialText)
        assertEquals("", coordinator.state.finalText)
        assertEquals(1, session.finishCount)
        assertEquals(0, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `cancel during ASR accept suppresses partial endpoint and final transcript`() {
        val operations = mutableListOf<String>()
        val acceptStarted = CountDownLatch(1)
        val releaseAccept = CountDownLatch(1)
        val sourceStopCalled = CountDownLatch(1)
        val consumerFailure = AtomicReference<Throwable?>()
        val cancelFailure = AtomicReference<Throwable?>()
        var finishCount = 0
        var cancelCount = 0
        var closeCount = 0
        val frame = Pcm16Frame(16_000, ShortArray(320) { 4 })
        val source = object : AudioFrameSource {
            override suspend fun read(): Pcm16Frame = frame
            override fun stop() { sourceStopCalled.countDown() }
            override fun close() = Unit
        }
        val session = object : AsrSession {
            override suspend fun accept(frame: Pcm16Frame): List<AsrEvent> {
                acceptStarted.countDown()
                check(releaseAccept.await(5, TimeUnit.SECONDS))
                return listOf(AsrEvent.Partial("private partial"), AsrEvent.Endpoint("private endpoint"))
            }
            override suspend fun finish(): AsrResult {
                finishCount += 1
                return AsrResult("private final")
            }
            override fun cancel() { cancelCount += 1 }
            override fun close() { closeCount += 1 }
        }
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source, object : VadEngine {
                override fun reset() = Unit
                override fun accept(frame: Pcm16Frame): VadEvent = VadEvent.Speech
                override fun close() = Unit
            })
        }
        val consumer = Thread {
            try {
                runImmediate { coordinator.consumeNextFrame() }
            } catch (failure: Throwable) {
                consumerFailure.set(failure)
            }
        }
        val canceller = Thread {
            try {
                coordinator.cancelAndDiscard()
            } catch (failure: Throwable) {
                cancelFailure.set(failure)
            }
        }

        consumer.start()
        assertTrue(acceptStarted.await(5, TimeUnit.SECONDS))
        canceller.start()
        assertTrue(sourceStopCalled.await(5, TimeUnit.SECONDS))
        releaseAccept.countDown()
        consumer.join(5_000)
        canceller.join(5_000)

        assertFalse(consumer.isAlive)
        assertFalse(canceller.isAlive)
        assertNull(cancelFailure.get())
        assertTrue(consumerFailure.get() is CancellationException)
        assertEquals(0, finishCount)
        assertEquals(1, cancelCount)
        assertEquals(1, closeCount)
        assertEquals(AsrPhase.READY, coordinator.state.phase)
        assertEquals("", coordinator.state.partialText)
        assertEquals("", coordinator.state.finalText)
    }

    @Test
    fun `cancel during VAD processing prevents ASR input and discards`() {
        val operations = mutableListOf<String>()
        val vadStarted = CountDownLatch(1)
        val releaseVad = CountDownLatch(1)
        val sourceStopCalled = CountDownLatch(1)
        val consumerFailure = AtomicReference<Throwable?>()
        val cancelFailure = AtomicReference<Throwable?>()
        val frame = Pcm16Frame(16_000, ShortArray(320) { 6 })
        val source = object : AudioFrameSource {
            override suspend fun read(): Pcm16Frame = frame
            override fun stop() {
                operations += "source.stop"
                sourceStopCalled.countDown()
            }
            override fun close() { operations += "source.close" }
        }
        val session = RecordingSession(emptyList(), AsrResult("must not finish"), operations)
        val vad = object : VadEngine {
            override fun reset() = Unit
            override fun accept(frame: Pcm16Frame): VadEvent {
                operations += "vad.accept"
                vadStarted.countDown()
                check(releaseVad.await(5, TimeUnit.SECONDS))
                return VadEvent.Speech
            }
            override fun close() { operations += "vad.close" }
        }
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source, vad)
        }
        val consumer = Thread {
            try {
                runImmediate { coordinator.consumeNextFrame() }
            } catch (failure: Throwable) {
                consumerFailure.set(failure)
            }
        }
        val canceller = Thread {
            try {
                coordinator.cancelAndDiscard()
            } catch (failure: Throwable) {
                cancelFailure.set(failure)
            }
        }

        consumer.start()
        assertTrue(vadStarted.await(5, TimeUnit.SECONDS))
        canceller.start()
        assertTrue(sourceStopCalled.await(5, TimeUnit.SECONDS))
        releaseVad.countDown()
        consumer.join(5_000)
        canceller.join(5_000)

        assertFalse(consumer.isAlive)
        assertFalse(canceller.isAlive)
        assertEquals(null, cancelFailure.get())
        assertTrue(consumerFailure.get() is CancellationException)
        assertFalse(operations.contains("session.accept"))
        assertEquals(0, session.finishCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(AsrPhase.READY, coordinator.state.phase)
    }

    @Test
    fun `VAD reset failure discards source session and VAD without ASR input`() {
        val operations = mutableListOf<String>()
        val canary = "private VAD reset payload"
        val source = RecordingSource(ArrayDeque(), operations)
        val session = RecordingSession(emptyList(), AsrResult("unused"), operations)
        var vadCloseCount = 0
        val vad = object : VadEngine {
            override fun reset(): Unit = throw IllegalStateException(canary)
            override fun accept(frame: Pcm16Frame): VadEvent = error("must not accept")
            override fun close() { operations += "vad.close"; vadCloseCount += 1 }
        }
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source, vad)
        }

        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("start failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, vadCloseCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `VAD accept failure prevents ASR accept and closes VAD before session discard`() {
        val operations = mutableListOf<String>()
        val canary = "private VAD frame payload"
        val frame = Pcm16Frame(16_000, ShortArray(320) { 8 })
        val source = RecordingSource(ArrayDeque(listOf(frame)), operations)
        val session = RecordingSession(emptyList(), AsrResult("unused"), operations)
        val vad = object : VadEngine {
            override fun reset() = Unit
            override fun accept(frame: Pcm16Frame): VadEvent = throw IllegalStateException(canary)
            override fun close() { operations += "vad.close" }
        }
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        val consumed = runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source, vad)
            coordinator.consumeNextFrame()
        }

        assertFalse(consumed)
        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("vad.accept failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertFalse(operations.contains("session.accept"))
        assertTrue(operations.indexOf("vad.close") < operations.indexOf("session.cancel"))
    }

    @Test
    fun `VAD close failure prevents finish and still discards session payload free`() {
        val operations = mutableListOf<String>()
        val canary = "private VAD close payload"
        val frame = Pcm16Frame(16_000, ShortArray(320) { 3 })
        val source = RecordingSource(ArrayDeque(listOf(frame)), operations)
        val session = RecordingSession(
            listOf(AsrEvent.Endpoint("endpoint payload")),
            AsrResult("must not finish"),
            operations,
        )
        val vad = object : VadEngine {
            override fun reset() = Unit
            override fun accept(frame: Pcm16Frame): VadEvent = VadEvent.Speech
            override fun close(): Unit = throw IllegalStateException(canary)
        }
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source, vad)
            coordinator.consumeNextFrame()
        }

        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("vad.close failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertEquals(0, session.finishCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `each captured frame reaches VAD once before ASR and VAD closes`() {
        val operations = mutableListOf<String>()
        val frame = Pcm16Frame(16_000, ShortArray(320) { 4 })
        val source = RecordingSource(ArrayDeque(listOf(frame)), operations)
        var vadAcceptCount = 0
        var vadResetCount = 0
        var vadCloseCount = 0
        val vad = object : VadEngine {
            override fun accept(frame: Pcm16Frame): VadEvent {
                operations += "vad.accept"
                vadAcceptCount += 1
                return VadEvent.Speech
            }
            override fun reset() { vadResetCount += 1 }
            override fun close() { operations += "vad.close"; vadCloseCount += 1 }
        }
        val session = RecordingSession(
            listOf(AsrEvent.Endpoint("signal only")),
            AsrResult("final"),
            operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source, vad)
            coordinator.consumeNextFrame()
        }

        assertEquals(1, vadResetCount)
        assertEquals(1, vadAcceptCount)
        assertEquals(1, vadCloseCount)
        assertTrue(operations.indexOf("vad.accept") < operations.indexOf("session.accept"))
        assertTrue(operations.indexOf("source.close") < operations.indexOf("vad.close"))
        assertTrue(operations.indexOf("vad.close") < operations.indexOf("session.finish"))
    }

    @Test
    fun `happy path uses finish result as final authority`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(1, 2, 3)))),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = listOf(
                AsrEvent.Partial("こん"),
                AsrEvent.Endpoint("endpoint text must not win"),
            ),
            finishResult = AsrResult("authoritative final"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        val mayConsumeMore = runImmediate {
            coordinator.prepare(fixtureModel())
            assertEquals(AsrPhase.READY, coordinator.state.phase)
            coordinator.start(source)
            assertEquals(AsrPhase.LISTENING, coordinator.state.phase)
            coordinator.consumeNextFrame()
        }

        assertFalse(mayConsumeMore)
        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        assertEquals("authoritative final", coordinator.state.finalText)
        assertEquals("", coordinator.state.partialText)
        assertEquals(1, session.finishCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun `blank finish falls back to endpoint text without losing the utterance`() {
        val operations = mutableListOf<String>()
        val session = RecordingSession(
            acceptEvents = listOf(AsrEvent.Endpoint("endpoint final")),
            finishResult = AsrResult(""),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(
                RecordingSource(
                    ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(1)))),
                    operations,
                ),
            )
            coordinator.consumeNextFrame()
        }

        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        assertEquals("endpoint final", coordinator.state.finalText)
    }

    @Test
    fun `explicit stop finalizes exactly once in ownership order`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(1)))),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("stopped final"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.stopAndFinalize()
        }

        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        assertEquals("stopped final", coordinator.state.finalText)
        assertEquals(
            listOf("source.stop", "source.close", "session.finish", "session.close"),
            operations.takeLast(4),
        )
        assertEquals(1, session.finishCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun `EOF finalizes once and preserves an empty authoritative result`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult(""),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        val mayConsumeMore = runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.consumeNextFrame()
        }

        assertFalse(mayConsumeMore)
        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        assertEquals("", coordinator.state.finalText)
        assertEquals(
            listOf("source.stop", "source.close", "session.finish", "session.close"),
            operations.takeLast(4),
        )
        assertEquals(1, session.finishCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun `cancel discards transcript and releases resources in ownership order`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(1)))),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = listOf(AsrEvent.Partial("private partial")),
            finishResult = AsrResult("must not finish"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.consumeNextFrame()
        }
        assertEquals("private partial", coordinator.state.partialText)

        coordinator.cancelAndDiscard()
        coordinator.cancelAndDiscard()

        assertEquals(AsrUiState(phase = AsrPhase.READY), coordinator.state)
        assertEquals(
            listOf("source.stop", "source.close", "session.cancel", "session.close"),
            operations.takeLast(4),
        )
        assertEquals(0, session.finishCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun `restart after cancel opens a new session with the cached model`() {
        val operations = mutableListOf<String>()
        val firstSession = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val secondSession = RecordingSession(
            acceptEvents = listOf(AsrEvent.Endpoint("signal only")),
            finishResult = AsrResult("second final"),
            operations = operations,
        )
        val engine = RecordingEngine(
            sessions = ArrayDeque(listOf(firstSession, secondSession)),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(engine)
        val firstSource = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(1)))),
            operations = operations,
        )
        val secondSource = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(2)))),
            operations = operations,
        )

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(firstSource)
        }
        coordinator.cancelAndDiscard()
        runImmediate {
            coordinator.start(secondSource)
            coordinator.consumeNextFrame()
        }

        assertEquals(2, engine.openCount)
        assertEquals(listOf("fixture-ja", "fixture-ja"), engine.openedModels.map { it.id })
        assertEquals(
            listOf(fixtureModel().contentFingerprint, fixtureModel().contentFingerprint),
            engine.openedModels.map { it.contentFingerprint },
        )
        assertEquals(1, firstSession.cancelCount)
        assertEquals(0, firstSession.finishCount)
        assertEquals(1, secondSession.finishCount)
        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        assertEquals("second final", coordinator.state.finalText)
    }

    @Test
    fun `model open failure becomes payload free error`() {
        val canary = "private transcript in native exception"
        val engine = object : AsrEngine {
            override suspend fun open(model: VerifiedModel): AsrSession {
                throw IllegalStateException(canary)
            }

            override fun close() = Unit
        }
        val coordinator = AsrSessionCoordinator(engine)

        runImmediate { coordinator.prepare(fixtureModel()) }

        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("prepare failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
    }

    @Test
    fun `source read failure cancels owned session without payload leakage`() {
        val canary = "private frame payload"
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(),
            operations = operations,
            readFailure = IllegalStateException(canary),
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        val mayConsumeMore = runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.consumeNextFrame()
        }

        assertFalse(mayConsumeMore)
        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("consume failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertEquals("", coordinator.state.partialText)
        assertEquals("", coordinator.state.finalText)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(0, session.finishCount)
    }

    @Test
    fun `session accept failure is identified without leaking native payload`() {
        val canary = "private decoder payload"
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(7)))),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
            acceptFailure = IllegalArgumentException(canary),
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        val mayConsumeMore = runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.consumeNextFrame()
        }

        assertFalse(mayConsumeMore)
        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("session.accept failed: IllegalArgumentException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(0, session.finishCount)
    }

    @Test
    fun `finish failure discards partial and cancels the terminal session`() {
        val canary = "private final transcript from native failure"
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(8)))),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = listOf(
                AsrEvent.Partial("private partial"),
                AsrEvent.Endpoint("private endpoint"),
            ),
            finishResult = AsrResult("unused"),
            operations = operations,
            finishFailure = IllegalStateException(canary),
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        val mayConsumeMore = runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.consumeNextFrame()
        }

        assertFalse(mayConsumeMore)
        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("session.finish failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertEquals("", coordinator.state.partialText)
        assertEquals("", coordinator.state.finalText)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.finishCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `source cleanup failures preserve the first failure and still release session`() {
        val primaryCanary = "private stop payload"
        val secondaryCanary = "private close payload"
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(9)))),
            operations = operations,
            stopFailure = IllegalStateException(primaryCanary),
            closeFailure = UnsupportedOperationException(secondaryCanary),
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("must not finish"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.stopAndFinalize()
        }

        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("source.stop failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(primaryCanary))
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(secondaryCanary))
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(0, session.finishCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `close while listening cancels ownership once and closes engine once`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(10)))),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val engine = RecordingEngine(session, operations)
        val coordinator = AsrSessionCoordinator(engine)

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }
        coordinator.close()
        coordinator.close()

        assertEquals(
            listOf(
                "source.stop",
                "source.close",
                "session.cancel",
                "session.close",
                "engine.close",
            ),
            operations.takeLast(5),
        )
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `session close failure after finish is reported once without cancel or reclose`() {
        val canary = "private session close payload"
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(13)))),
            operations = operations,
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("private finished text"),
            operations = operations,
            closeFailure = IllegalStateException(canary),
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
            coordinator.stopAndFinalize()
        }

        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("session.close failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertEquals("", coordinator.state.partialText)
        assertEquals("", coordinator.state.finalText)
        assertEquals(1, session.finishCount)
        assertEquals(0, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `invalid start from reviewing does not acquire source or open session`() {
        val operations = mutableListOf<String>()
        val firstSource = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(14)))),
            operations = operations,
        )
        val offeredSource = RecordingSource(ArrayDeque(), operations)
        val session = RecordingSession(
            acceptEvents = listOf(AsrEvent.Endpoint("trigger only")),
            finishResult = AsrResult("review result"),
            operations = operations,
        )
        val engine = RecordingEngine(session, operations)
        val coordinator = AsrSessionCoordinator(engine)

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(firstSource)
            coordinator.consumeNextFrame()
        }
        val failure = assertThrows(IllegalStateException::class.java) {
            runImmediate { coordinator.start(offeredSource) }
        }

        assertEquals("start requires READY", failure.message)
        assertEquals(1, engine.openCount)
        assertEquals(0, offeredSource.stopCount)
        assertEquals(0, offeredSource.closeCount)
        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        assertEquals("review result", coordinator.state.finalText)
    }

    @Test
    fun `structured final transcript reaches reviewing state`() {
        val operations = mutableListOf<String>()
        val transcript = TranscriptRevision.final(
            text = "構造化された確定結果",
            timing = TranscriptTiming(100, 900),
            confidence = 0.92f,
            origin = TranscriptOrigin("sherpa-onnx-offline", "reazonspeech-k2-v2", "2024-08-01"),
        )
        val session = RecordingSession(
            acceptEvents = listOf(AsrEvent.Endpoint("trigger")),
            finishResult = AsrResult(transcript.text, transcript),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(
                RecordingSource(
                    ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(15)))),
                    operations,
                ),
            )
            coordinator.consumeNextFrame()
        }

        assertEquals(AsrPhase.REVIEWING, coordinator.state.phase)
        assertEquals(transcript, coordinator.state.transcript)
    }

    @Test
    fun `discard review clears transcript and permits a fresh session`() {
        val operations = mutableListOf<String>()
        val firstSession = RecordingSession(
            acceptEvents = listOf(AsrEvent.Endpoint("trigger one")),
            finishResult = AsrResult("private review one"),
            operations = operations,
        )
        val secondSession = RecordingSession(
            acceptEvents = listOf(AsrEvent.Endpoint("trigger two")),
            finishResult = AsrResult("review two"),
            operations = operations,
        )
        val engine = RecordingEngine(
            ArrayDeque(listOf(firstSession, secondSession)),
            operations,
        )
        val coordinator = AsrSessionCoordinator(engine)

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(
                RecordingSource(
                    ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(15)))),
                    operations,
                ),
            )
            coordinator.consumeNextFrame()
        }
        coordinator.discardReview()

        assertEquals(AsrUiState(phase = AsrPhase.READY), coordinator.state)
        runImmediate {
            coordinator.start(
                RecordingSource(
                    ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(16)))),
                    operations,
                ),
            )
            coordinator.consumeNextFrame()
        }
        assertEquals(2, engine.openCount)
        assertEquals("review two", coordinator.state.finalText)
    }

    @Test
    fun `reset after failure returns to idle and permits explicit reprepare`() {
        val operations = mutableListOf<String>()
        val recoveredSession = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        var openCount = 0
        val engine = object : AsrEngine {
            override suspend fun open(model: VerifiedModel): AsrSession {
                openCount += 1
                if (openCount == 1) throw IllegalStateException("private load payload")
                return recoveredSession
            }

            override fun close() = Unit
        }
        val coordinator = AsrSessionCoordinator(engine)

        runImmediate { coordinator.prepare(fixtureModel()) }
        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        coordinator.resetAfterFailure()
        assertEquals(AsrUiState(), coordinator.state)
        runImmediate { coordinator.prepare(fixtureModel()) }

        assertEquals(AsrPhase.READY, coordinator.state.phase)
        assertEquals(2, openCount)
    }

    @Test
    fun `close from reviewing clears retained transcript`() {
        val operations = mutableListOf<String>()
        val session = RecordingSession(
            acceptEvents = listOf(AsrEvent.Endpoint("private trigger")),
            finishResult = AsrResult("private retained review"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(
                RecordingSource(
                    ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(17)))),
                    operations,
                ),
            )
            coordinator.consumeNextFrame()
        }
        assertEquals("private retained review", coordinator.state.finalText)
        coordinator.close()

        assertEquals("", coordinator.state.partialText)
        assertEquals("", coordinator.state.finalText)
    }

    @Test
    fun `blocked cancel survives source stop failure without leaking gate or ownership`() {
        val readStarted = CountDownLatch(1)
        val unblockRead = CountDownLatch(1)
        val consumerFailure = AtomicReference<Throwable?>()
        var closeCount = 0
        val canary = "native stop private details"
        val source = object : AudioFrameSource {
            override suspend fun read(): Pcm16Frame? {
                readStarted.countDown()
                check(unblockRead.await(5, TimeUnit.SECONDS))
                throw CancellationException("raw PCM canary")
            }
            override fun stop() {
                unblockRead.countDown()
                throw IllegalStateException(canary)
            }
            override fun close() { closeCount += 1 }
        }
        val session = RecordingSession(emptyList(), AsrResult("must not finish"), mutableListOf())
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, mutableListOf()))
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }
        val consumer = Thread {
            try {
                runImmediate { coordinator.consumeNextFrame() }
            } catch (failure: Throwable) {
                consumerFailure.set(failure)
            }
        }
        consumer.start()
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))

        coordinator.cancelAndDiscard()
        consumer.join(5_000)

        assertFalse(consumer.isAlive)
        assertTrue(consumerFailure.get() is CancellationException)
        assertTrue(!consumerFailure.get().toString().contains(canary))
        assertEquals(1, closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        coordinator.resetAfterFailure()
        assertEquals(AsrPhase.IDLE, coordinator.state.phase)
    }

    @Test
    fun `blocked cancel times out sanitized when source cannot unblock read`() {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val consumerFailure = AtomicReference<Throwable?>()
        val source = object : AudioFrameSource {
            override suspend fun read(): Pcm16Frame? {
                readStarted.countDown()
                check(releaseRead.await(5, TimeUnit.SECONDS))
                throw CancellationException("private PCM payload")
            }

            override fun stop(): Unit = throw IllegalStateException("private native stop payload")
            override fun close() = Unit
        }
        val session = RecordingSession(emptyList(), AsrResult("must not finish"), mutableListOf())
        val coordinator = AsrSessionCoordinator(
            RecordingEngine(session, mutableListOf()),
            blockedCancelWaitTimeoutMillis = 50,
        )
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }
        val consumer = Thread {
            try {
                runImmediate { coordinator.consumeNextFrame() }
            } catch (failure: Throwable) {
                consumerFailure.set(failure)
            }
        }
        consumer.start()
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))

        val failure = assertThrows(IllegalStateException::class.java) {
            coordinator.cancelAndDiscard()
        }

        assertEquals("cancel timed out", failure.message)
        assertTrue(consumer.isAlive)
        releaseRead.countDown()
        consumer.join(5_000)
        assertFalse(consumer.isAlive)
        assertTrue(consumerFailure.get() is CancellationException)
        assertFalse(consumerFailure.get().toString().contains("private PCM payload"))
    }

    @Test
    fun `cancel during blocked consume stops input then consumer owns discard ordering`() {
        val operations = mutableListOf<String>()
        val readStarted = CountDownLatch(1)
        val unblockRead = CountDownLatch(1)
        val consumeFailure = AtomicReference<Throwable?>()
        val source = object : AudioFrameSource {
            override suspend fun read(): Pcm16Frame? {
                readStarted.countDown()
                check(unblockRead.await(5, TimeUnit.SECONDS)) { "unblock timeout" }
                throw CancellationException("private audio payload")
            }
            override fun stop() {
                operations += "source.stop"
                unblockRead.countDown()
            }
            override fun close() { operations += "source.close" }
        }
        val session = RecordingSession(emptyList(), AsrResult("must not finish"), operations)
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }
        val consumer = Thread {
            try { runImmediate { coordinator.consumeNextFrame() } } catch (failure: Throwable) {
                consumeFailure.set(failure)
            }
        }

        consumer.start()
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))
        coordinator.cancelAndDiscard()
        consumer.join(5_000)

        assertFalse(consumer.isAlive)
        assertTrue(consumeFailure.get() is CancellationException)
        assertEquals("ASR operation cancelled", consumeFailure.get()?.message)
        assertEquals(AsrUiState(phase = AsrPhase.READY), coordinator.state)
        assertEquals(
            listOf("source.stop", "source.stop", "source.close", "session.cancel", "session.close"),
            operations.takeLast(5),
        )
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(0, session.finishCount)
    }

    @Test
    fun `concurrent lifecycle operation is rejected before touching engine`() {
        val operations = mutableListOf<String>()
        val openStarted = CountDownLatch(1)
        val releaseOpen = CountDownLatch(1)
        val backgroundFailure = AtomicReference<Throwable?>()
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        var openCount = 0
        val engine = object : AsrEngine {
            override suspend fun open(model: VerifiedModel): AsrSession {
                openCount += 1
                openStarted.countDown()
                check(releaseOpen.await(5, TimeUnit.SECONDS)) { "test open release timeout" }
                return session
            }

            override fun close() = Unit
        }
        val coordinator = AsrSessionCoordinator(engine)
        val firstPrepare = Thread {
            try {
                runImmediate { coordinator.prepare(fixtureModel()) }
            } catch (failure: Throwable) {
                backgroundFailure.set(failure)
            }
        }

        firstPrepare.start()
        assertTrue(openStarted.await(5, TimeUnit.SECONDS))
        val concurrentFailure = try {
            assertThrows(IllegalStateException::class.java) {
                runImmediate { coordinator.prepare(fixtureModel()) }
            }
        } finally {
            releaseOpen.countDown()
        }
        firstPrepare.join(5_000)

        assertEquals("prepare rejected: operation in progress", concurrentFailure.message)
        assertFalse(firstPrepare.isAlive)
        assertNull(backgroundFailure.get())
        assertEquals(1, openCount)
        assertEquals(AsrPhase.READY, coordinator.state.phase)
    }

    @Test
    fun `state is safely published across lifecycle threads`() {
        val stateField = AsrSessionCoordinator::class.java.getDeclaredField("state")

        assertTrue(Modifier.isVolatile(stateField.modifiers))
    }

    @Test
    fun `operation admission is atomic with the close lifecycle monitor`() {
        val openStarted = CountDownLatch(1)
        val releaseOpen = CountDownLatch(1)
        val backgroundFailure = AtomicReference<Throwable?>()
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = mutableListOf(),
        )
        val engine = object : AsrEngine {
            override suspend fun open(model: VerifiedModel): AsrSession {
                openStarted.countDown()
                check(releaseOpen.await(5, TimeUnit.SECONDS)) { "release open timeout" }
                return session
            }

            override fun close() = Unit
        }
        val coordinator = AsrSessionCoordinator(engine)
        val monitorField = AsrSessionCoordinator::class.java.getDeclaredField("closeMonitor")
        monitorField.isAccessible = true
        val closeMonitor = checkNotNull(monitorField.get(coordinator))
        val prepareThread = Thread {
            try {
                runImmediate { coordinator.prepare(fixtureModel()) }
            } catch (failure: Throwable) {
                backgroundFailure.set(failure)
            }
        }

        synchronized(closeMonitor) {
            prepareThread.start()
            assertFalse(openStarted.await(200, TimeUnit.MILLISECONDS))
        }
        assertTrue(openStarted.await(5, TimeUnit.SECONDS))
        releaseOpen.countDown()
        prepareThread.join(5_000)

        assertFalse(prepareThread.isAlive)
        assertNull(backgroundFailure.get())
        assertEquals(AsrPhase.READY, coordinator.state.phase)
    }

    @Test
    fun `concurrent close waits for the in progress teardown`() {
        val operations = mutableListOf<String>()
        val stopStarted = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val secondCloseFinished = CountDownLatch(1)
        val firstFailure = AtomicReference<Throwable?>()
        val secondFailure = AtomicReference<Throwable?>()
        var stopCount = 0
        var closeCount = 0
        val source = object : AudioFrameSource {
            override suspend fun read(): Pcm16Frame? = null

            override fun stop() {
                stopCount += 1
                stopStarted.countDown()
                check(releaseStop.await(5, TimeUnit.SECONDS)) { "release stop timeout" }
            }

            override fun close() {
                closeCount += 1
            }
        }
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val engine = RecordingEngine(session, operations)
        val coordinator = AsrSessionCoordinator(engine)
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }

        val firstClose = Thread {
            try {
                coordinator.close()
            } catch (failure: Throwable) {
                firstFailure.set(failure)
            }
        }
        val secondClose = Thread {
            try {
                coordinator.close()
            } catch (failure: Throwable) {
                secondFailure.set(failure)
            } finally {
                secondCloseFinished.countDown()
            }
        }
        firstClose.start()
        assertTrue(stopStarted.await(5, TimeUnit.SECONDS))
        secondClose.start()

        assertFalse(secondCloseFinished.await(200, TimeUnit.MILLISECONDS))
        releaseStop.countDown()
        firstClose.join(5_000)
        secondClose.join(5_000)

        assertFalse(firstClose.isAlive)
        assertFalse(secondClose.isAlive)
        assertNull(firstFailure.get())
        assertNull(secondFailure.get())
        assertEquals(1, stopCount)
        assertEquals(1, closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `concurrent close callers observe the same teardown failure`() {
        val operations = mutableListOf<String>()
        val stopStarted = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val firstFailure = AtomicReference<Throwable?>()
        val secondFailure = AtomicReference<Throwable?>()
        var stopCount = 0
        var closeCount = 0
        val source = object : AudioFrameSource {
            override suspend fun read(): Pcm16Frame? = null

            override fun stop() {
                stopCount += 1
                stopStarted.countDown()
                check(releaseStop.await(5, TimeUnit.SECONDS)) { "release stop timeout" }
                throw IllegalStateException("private close failure payload")
            }

            override fun close() {
                closeCount += 1
            }
        }
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val engine = RecordingEngine(session, operations)
        val coordinator = AsrSessionCoordinator(engine)
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }

        val firstClose = Thread {
            try {
                coordinator.close()
            } catch (failure: Throwable) {
                firstFailure.set(failure)
            }
        }
        val secondClose = Thread {
            try {
                coordinator.close()
            } catch (failure: Throwable) {
                secondFailure.set(failure)
            }
        }
        firstClose.start()
        assertTrue(stopStarted.await(5, TimeUnit.SECONDS))
        secondClose.start()
        releaseStop.countDown()
        firstClose.join(5_000)
        secondClose.join(5_000)

        assertFalse(firstClose.isAlive)
        assertFalse(secondClose.isAlive)
        assertEquals("source.stop failed: IllegalStateException", firstFailure.get()?.message)
        assertEquals(firstFailure.get()?.message, secondFailure.get()?.message)
        assertEquals(1, stopCount)
        assertEquals(1, closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `close contains Error cleanup payload until every resource is attempted`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(18)))),
            operations = operations,
            stopFailure = AssertionError("private fatal cleanup payload"),
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val engine = RecordingEngine(session, operations)
        val coordinator = AsrSessionCoordinator(engine)
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            coordinator.close()
        }

        assertEquals("source.stop failed: AssertionError", failure.message)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `cancellation cleans active ownership and propagates without payload`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(),
            operations = operations,
            readFailure = CancellationException("private cancellation payload"),
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }

        val failure = assertThrows(CancellationException::class.java) {
            runImmediate { coordinator.consumeNextFrame() }
        }

        assertEquals("ASR operation cancelled", failure.message)
        assertEquals(AsrUiState(phase = AsrPhase.READY), coordinator.state)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `upstream cancellation cannot impersonate sanitized cancellation`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(),
            operations = operations,
            readFailure = CancellationException("ASR operation cancelled"),
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))
        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }

        val failure = assertThrows(CancellationException::class.java) {
            runImmediate { coordinator.consumeNextFrame() }
        }

        assertEquals("ASR operation cancelled", failure.message)
        assertEquals(AsrUiState(phase = AsrPhase.READY), coordinator.state)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `cancel cleanup failures preserve first failure and attempt every release`() {
        val operations = mutableListOf<String>()
        val source = RecordingSource(
            frames = ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(12)))),
            operations = operations,
            stopFailure = IllegalStateException("private stop"),
            closeFailure = UnsupportedOperationException("private source close"),
        )
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
            cancelFailure = IllegalArgumentException("private cancel"),
            closeFailure = SecurityException("private session close"),
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(source)
        }
        coordinator.cancelAndDiscard()

        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("source.stop failed: IllegalStateException", coordinator.state.errorMessage)
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `operations after close are rejected by a payload free guard`() {
        val operations = mutableListOf<String>()
        val session = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        val coordinator = AsrSessionCoordinator(RecordingEngine(session, operations))
        val source = RecordingSource(ArrayDeque(), operations)
        coordinator.close()

        val prepareFailure = assertThrows(IllegalStateException::class.java) {
            runImmediate { coordinator.prepare(fixtureModel()) }
        }
        val startFailure = assertThrows(IllegalStateException::class.java) {
            runImmediate { coordinator.start(source) }
        }
        val consumeFailure = assertThrows(IllegalStateException::class.java) {
            runImmediate { coordinator.consumeNextFrame() }
        }

        assertEquals("prepare rejected: coordinator closed", prepareFailure.message)
        assertEquals("start rejected: coordinator closed", startFailure.message)
        assertEquals("consume rejected: coordinator closed", consumeFailure.message)
    }

    @Test
    fun `session reopen failure closes the offered source and requires reprepare`() {
        val canary = "private reopen payload"
        val operations = mutableListOf<String>()
        val firstSession = RecordingSession(
            acceptEvents = emptyList(),
            finishResult = AsrResult("unused"),
            operations = operations,
        )
        var openCount = 0
        val engine = object : AsrEngine {
            override suspend fun open(model: VerifiedModel): AsrSession {
                openCount += 1
                if (openCount == 1) return firstSession
                throw IllegalStateException(canary)
            }

            override fun close() = Unit
        }
        val coordinator = AsrSessionCoordinator(engine)
        val firstSource = RecordingSource(
            ArrayDeque(listOf(Pcm16Frame(16_000, shortArrayOf(11)))),
            operations,
        )
        val offeredSource = RecordingSource(ArrayDeque(), operations)

        runImmediate {
            coordinator.prepare(fixtureModel())
            coordinator.start(firstSource)
        }
        coordinator.cancelAndDiscard()
        runImmediate { coordinator.start(offeredSource) }

        assertEquals(AsrPhase.ERROR, coordinator.state.phase)
        assertEquals("start failed: IllegalStateException", coordinator.state.errorMessage)
        assertFalse(coordinator.state.errorMessage.orEmpty().contains(canary))
        assertEquals(1, offeredSource.stopCount)
        assertEquals(1, offeredSource.closeCount)
    }

    private class RecordingSource(
        private val frames: ArrayDeque<Pcm16Frame>,
        private val operations: MutableList<String>,
        private val readFailure: Throwable? = null,
        private val stopFailure: Throwable? = null,
        private val closeFailure: Throwable? = null,
    ) : AudioFrameSource {
        var stopCount = 0
            private set
        var closeCount = 0
            private set

        override suspend fun read(): Pcm16Frame? {
            operations += "source.read"
            readFailure?.let { throw it }
            return frames.removeFirstOrNull()
        }

        override fun stop() {
            stopCount += 1
            operations += "source.stop"
            stopFailure?.let { throw it }
        }

        override fun close() {
            closeCount += 1
            operations += "source.close"
            closeFailure?.let { throw it }
        }
    }

    private class RecordingEngine(
        private val sessions: ArrayDeque<AsrSession>,
        private val operations: MutableList<String>,
    ) : AsrEngine {
        constructor(
            session: AsrSession,
            operations: MutableList<String>,
        ) : this(ArrayDeque(listOf(session)), operations)

        var openCount = 0
            private set
        var closeCount = 0
            private set
        val openedModels = mutableListOf<VerifiedModel>()

        override suspend fun open(model: VerifiedModel): AsrSession {
            openCount += 1
            openedModels += model
            operations += "engine.open"
            return sessions.removeFirst()
        }

        override fun close() {
            closeCount += 1
            operations += "engine.close"
        }
    }

    private class RecordingSession(
        private val acceptEvents: List<AsrEvent>,
        private val finishResult: AsrResult,
        private val operations: MutableList<String>,
        private val acceptFailure: Throwable? = null,
        private val finishFailure: Throwable? = null,
        private val cancelFailure: Throwable? = null,
        private val closeFailure: Throwable? = null,
    ) : AsrSession {
        var finishCount = 0
            private set
        var cancelCount = 0
            private set
        var closeCount = 0
            private set

        override suspend fun accept(frame: Pcm16Frame): List<AsrEvent> {
            operations += "session.accept"
            acceptFailure?.let { throw it }
            return acceptEvents
        }

        override suspend fun finish(): AsrResult {
            finishCount += 1
            operations += "session.finish"
            finishFailure?.let { throw it }
            return finishResult
        }

        override fun cancel() {
            cancelCount += 1
            operations += "session.cancel"
            cancelFailure?.let { throw it }
        }

        override fun close() {
            closeCount += 1
            operations += "session.close"
            closeFailure?.let { throw it }
        }
    }

    private fun fixtureModel() = fixtureVerifiedModel()

    private fun <T> runImmediate(block: suspend () -> T): T {
        var completion: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    completion = result
                }
            },
        )
        return checkNotNull(completion).getOrThrow()
    }
}
