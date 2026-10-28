package io.github.ninbyo02.lami.asr.foundation.audio

import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecordFrameSourceTest {
    @Test fun `explicit finish drains captured frames without reopening microphone`() {
        val queued = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val count = AtomicInteger()
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int? = 7
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                val id = count.incrementAndGet()
                if (id > 4) { queued.countDown(); check(stopped.await(5, TimeUnit.SECONDS)); return -1 }
                target.fill(id.toShort(), offset, offset + 320)
                return 320
            }
            override fun stop() { stopped.countDown() }
            override fun release() = Unit
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })
        assertEquals(1.toShort(), runImmediate { source.read() }!!.copySamples().first())
        assertTrue(queued.await(5, TimeUnit.SECONDS))
        source.finish()
        for (id in 2..4) assertEquals(id.toShort(), runImmediate { source.read() }!!.copySamples().first())
        assertEquals(null, runImmediate { source.read() })
        source.close()
    }

    @Test fun `finish before first read never starts capture`() {
        val input = RecordingCaptureInput()
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })
        source.finish()
        assertEquals(null, runImmediate { source.read() })
        assertEquals(0, input.startCount)
        source.close(); assertEquals(1, input.releaseCount)
    }

    @Test
    fun `permission denial remains sanitized when release fails`() {
        var releaseCount = 0
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int? = 7
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int = -1
            override fun stop() = Unit
            override fun release() {
                releaseCount += 1
                throw IllegalStateException("RELEASE-CANARY")
            }
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { false })

        val failure = assertThrows(AudioCaptureException::class.java) {
            runImmediate { source.read() }
        }

        assertEquals(AudioCaptureFailure.PERMISSION_REVOKED, failure.failure)
        assertFalse(failure.toString().contains("RELEASE-CANARY"))
        assertEquals(null, failure.cause)
        assertEquals(1, releaseCount)
    }

    @Test
    fun `permission gate runtime failure is sanitized and releases once`() {
        val input = RecordingCaptureInput()
        val source = AudioRecordFrameSource(
            input,
            MicrophonePermissionGate { throw IllegalStateException("PERMISSION-GATE-CANARY") },
        )

        val failure = assertThrows(AudioCaptureException::class.java) {
            runImmediate { source.read() }
        }

        assertEquals(AudioCaptureFailure.START_FAILED, failure.failure)
        assertFalse(failure.toString().contains("PERMISSION-GATE-CANARY"))
        assertEquals(null, failure.cause)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `initialization getter runtime failure is sanitized and releases once`() {
        var releaseCount = 0
        val input = object : AudioCaptureInput {
            override val isInitialized: Boolean
                get() = throw IllegalStateException("STATE-CANARY")
            override fun routedDeviceId(): Int? = 7
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int = -1
            override fun stop() = Unit
            override fun release() { releaseCount += 1 }
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        val failure = assertThrows(AudioCaptureException::class.java) {
            runImmediate { source.read() }
        }

        assertEquals(AudioCaptureFailure.START_FAILED, failure.failure)
        assertFalse(failure.toString().contains("STATE-CANARY"))
        assertEquals(null, failure.cause)
        assertEquals(1, releaseCount)
    }

    @Test
    fun `fatal capture worker error terminally releases blocked reader before rethrow`() {
        val readEntered = CountDownLatch(1)
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int? = 7
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                readEntered.countDown()
                throw AssertionError("FATAL-CANARY")
            }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })
        val outcome = AtomicReference<Result<Pcm16Frame?>?>()
        val reader = Thread { outcome.set(runCatching { runImmediate { source.read() } }) }

        try {
            reader.start()
            assertTrue(readEntered.await(5, TimeUnit.SECONDS))
            assertTrue(source.awaitWorkerTerminationForTest(5_000))
            reader.join(500)

            assertFalse("blocked reader was not released", reader.isAlive)
            val failure = outcome.get()?.exceptionOrNull() as AudioCaptureException
            assertEquals(AudioCaptureFailure.READ_FAILED, failure.failure)
            assertFalse(failure.toString().contains("FATAL-CANARY"))
            assertEquals(null, failure.cause)
        } finally {
            source.close()
            reader.join(5_000)
        }
    }

    @Test
    fun `route is rechecked after platform read before PCM publication`() {
        val routeChecks = AtomicInteger()
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int = if (routeChecks.incrementAndGet() < 3) 7 else 9
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                ShortArray(320) { 17 }.copyInto(target, offset)
                return 320
            }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        val failure = assertThrows(AudioCaptureException::class.java) {
            runImmediate { source.read() }
        }
        source.close()

        assertEquals(AudioCaptureFailure.ROUTE_CHANGED, failure.failure)
    }

    @Test
    fun `positive samples returned after stop are discarded before publication`() {
        val readStarted = CountDownLatch(1)
        val stopCalled = CountDownLatch(1)
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int = 7
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                readStarted.countDown()
                check(stopCalled.await(5, TimeUnit.SECONDS))
                ShortArray(320) { 23 }.copyInto(target, offset)
                return 320
            }
            override fun stop() { stopCalled.countDown() }
            override fun release() = Unit
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })
        val outcome = AtomicReference<Result<Pcm16Frame?>?>()
        val reader = Thread { outcome.set(runCatching { runImmediate { source.read() } }) }

        reader.start()
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))
        source.stop()
        reader.join(5_000)
        source.close()

        assertFalse(reader.isAlive)
        assertTrue(outcome.get()?.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `permission is rechecked after platform read before PCM publication`() {
        val thirdCheckReached = CountDownLatch(1)
        val releaseThirdCheck = CountDownLatch(1)
        var checks = 0
        val gate = MicrophonePermissionGate {
            checks += 1
            if (checks == 3) {
                thirdCheckReached.countDown()
                check(releaseThirdCheck.await(5, TimeUnit.SECONDS))
                false
            } else {
                true
            }
        }
        val input = RecordingCaptureInput(
            readFrames = ArrayDeque(listOf(ShortArray(320) { 42 })),
        )
        val source = AudioRecordFrameSource(input, gate)
        val outcome = AtomicReference<Result<Pcm16Frame?>?>()
        val reader = Thread {
            outcome.set(runCatching { runImmediate { source.read() } })
        }

        reader.start()
        assertTrue(thirdCheckReached.await(5, TimeUnit.SECONDS))
        releaseThirdCheck.countDown()
        reader.join(5_000)
        source.close()

        assertFalse(reader.isAlive)
        val failure = outcome.get()?.exceptionOrNull()
        assertTrue(failure is AudioCaptureException)
        assertEquals(
            AudioCaptureFailure.PERMISSION_REVOKED,
            (failure as AudioCaptureException).failure,
        )
    }

    @Test
    fun `read after stop is cancelled without starting native capture`() {
        val input = RecordingCaptureInput()
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        source.stop()
        val failure = assertThrows(CancellationException::class.java) {
            runImmediate { source.read() }
        }
        source.close()

        assertEquals("audio capture cancelled", failure.message)
        assertEquals(0, input.startCount)
        assertEquals(0, input.readCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `start failure is sanitized and releases native input exactly once`() {
        val canary = "raw microphone start details"
        val input = object : AudioCaptureInput {
            var releaseCount = 0
            override val isInitialized = true
            override fun routedDeviceId(): Int = 7
            override fun start(): Unit = throw IllegalStateException(canary)
            override fun read(target: ShortArray, offset: Int, length: Int): Int = error("must not read")
            override fun stop() = Unit
            override fun release() { releaseCount += 1 }
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        val failure = assertThrows(AudioCaptureException::class.java) {
            runImmediate { source.read() }
        }
        source.close()

        assertEquals(AudioCaptureFailure.START_FAILED, failure.failure)
        assertEquals("audio capture failed: START_FAILED", failure.message)
        assertEquals(null, failure.cause)
        assertTrue(!failure.toString().contains(canary))
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `permission is rechecked before capture starts`() {
        val input = RecordingCaptureInput()
        val source = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { false },
        )

        val failure = assertThrows(AudioCaptureException::class.java) {
            runImmediate { source.read() }
        }

        assertEquals(AudioCaptureFailure.PERMISSION_REVOKED, failure.failure)
        assertEquals(0, input.startCount)
        assertEquals(0, input.readCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `partial reads are combined without changing sample order`() {
        val first = ShortArray(319) { it.toShort() }
        val last = shortArrayOf(319)
        val input = RecordingCaptureInput(readFrames = ArrayDeque(listOf(first, last)))
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        val frame = runImmediate { source.read() }
        source.close()

        assertTrue(ShortArray(320) { it.toShort() }.contentEquals(frame?.copySamples()))
        assertTrue(input.readCount >= 2)
    }

    @Test
    fun `uninitialized input never starts or reads and releases once`() {
        val input = object : AudioCaptureInput {
            var startCount = 0
            var readCount = 0
            var releaseCount = 0
            override val isInitialized = false
            override fun routedDeviceId(): Int = 1
            override fun start() { startCount += 1 }
            override fun read(target: ShortArray, offset: Int, length: Int): Int { readCount += 1; return length }
            override fun stop() = Unit
            override fun release() { releaseCount += 1 }
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        val failure = assertThrows(AudioCaptureException::class.java) { runImmediate { source.read() } }
        source.close()

        assertEquals(AudioCaptureFailure.NOT_INITIALIZED, failure.failure)
        assertEquals(0, input.startCount)
        assertEquals(0, input.readCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `negative dead object and zero reads become payload free READ_FAILED`() {
        listOf(-6, 0).forEach { result ->
            val canary = "raw read status $result"
            val input = object : AudioCaptureInput {
                override val isInitialized = true
                override fun routedDeviceId(): Int = 7
                override fun start() = Unit
                override fun read(target: ShortArray, offset: Int, length: Int): Int = result
                override fun stop() = Unit
                override fun release() = Unit
                override fun toString(): String = canary
            }
            val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

            val failure = assertThrows(AudioCaptureException::class.java) { runImmediate { source.read() } }
            source.close()

            assertEquals(AudioCaptureFailure.READ_FAILED, failure.failure)
            assertEquals("audio capture failed: READ_FAILED", failure.message)
            assertEquals(null, failure.cause)
            assertTrue(!failure.toString().contains(canary))
        }
    }

    @Test
    fun `321 sample read preserves remainder and order across next frame`() {
        val first = ShortArray(321) { it.toShort() }
        val second = ShortArray(319) { (it + 321).toShort() }
        val input = RecordingCaptureInput(readFrames = ArrayDeque(listOf(first, second)))
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        val frame1 = runImmediate { source.read() }
        val frame2 = runImmediate { source.read() }
        source.close()

        assertTrue(ShortArray(320) { it.toShort() }.contentEquals(frame1?.copySamples()))
        assertTrue(ShortArray(320) { (it + 320).toShort() }.contentEquals(frame2?.copySamples()))
    }

    @Test
    fun `640 sample read emits two ordered frames`() {
        val samples = ShortArray(640) { it.toShort() }
        val input = RecordingCaptureInput(readFrames = ArrayDeque(listOf(samples)))
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        val frame1 = runImmediate { source.read() }
        val frame2 = runImmediate { source.read() }
        source.close()

        assertTrue(samples.copyOfRange(0, 320).contentEquals(frame1?.copySamples()))
        assertTrue(samples.copyOfRange(320, 640).contentEquals(frame2?.copySamples()))
    }

    @Test
    fun `permission revoke discards already queued PCM and reports sanitized terminal failure`() {
        val granted = java.util.concurrent.atomic.AtomicBoolean(true)
        val secondReadStarted = CountDownLatch(1)
        val allowSecondRead = CountDownLatch(1)
        var readCount = 0
        val input = object : AudioCaptureInput {
            var stopCount = 0
            var releaseCount = 0
            override val isInitialized = true
            override fun routedDeviceId(): Int = 7
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                readCount += 1
                if (readCount == 2) {
                    secondReadStarted.countDown()
                    check(allowSecondRead.await(5, TimeUnit.SECONDS))
                }
                ShortArray(320) { readCount.toShort() }.copyInto(target, offset)
                return 320
            }
            override fun stop() { stopCount += 1; allowSecondRead.countDown() }
            override fun release() { releaseCount += 1 }
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { granted.get() })

        assertEquals(1, runImmediate { source.read() }?.copySamples()?.first()?.toInt())
        assertTrue(secondReadStarted.await(5, TimeUnit.SECONDS))
        granted.set(false)
        allowSecondRead.countDown()
        assertTrue(source.awaitWorkerTerminationForTest(5_000))
        val failure = assertThrows(AudioCaptureException::class.java) { runImmediate { source.read() } }
        source.stop()
        source.close()
        source.close()

        assertEquals(AudioCaptureFailure.PERMISSION_REVOKED, failure.failure)
        assertEquals(null, failure.cause)
        assertEquals(1, input.stopCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `route change discards already queued PCM`() {
        val route = java.util.concurrent.atomic.AtomicInteger(7)
        val secondReadStarted = CountDownLatch(1)
        val allowSecondRead = CountDownLatch(1)
        var readCount = 0
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int = route.get()
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                readCount += 1
                if (readCount == 2) {
                    secondReadStarted.countDown()
                    check(allowSecondRead.await(5, TimeUnit.SECONDS))
                }
                ShortArray(320) { readCount.toShort() }.copyInto(target, offset)
                return 320
            }
            override fun stop() { allowSecondRead.countDown() }
            override fun release() = Unit
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })

        assertEquals(1, runImmediate { source.read() }?.copySamples()?.first()?.toInt())
        assertTrue(secondReadStarted.await(5, TimeUnit.SECONDS))
        route.set(9)
        allowSecondRead.countDown()
        assertTrue(source.awaitWorkerTerminationForTest(5_000))
        val failure = assertThrows(AudioCaptureException::class.java) { runImmediate { source.read() } }
        source.close()

        assertEquals(AudioCaptureFailure.ROUTE_CHANGED, failure.failure)
    }

    @Test
    fun `close fails closed without release when capture worker ignores stop timeout`() {
        val readStarted = CountDownLatch(1)
        val allowReadToExit = CountDownLatch(1)
        var stopCount = 0
        var releaseCount = 0
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int = 7
            override fun start() = Unit
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                readStarted.countDown()
                check(allowReadToExit.await(5, TimeUnit.SECONDS))
                return -1
            }
            override fun stop() { stopCount += 1 }
            override fun release() { releaseCount += 1 }
        }
        val source = AudioRecordFrameSource(
            input,
            MicrophonePermissionGate { true },
            workerJoinTimeoutMillis = 50,
        )
        val reader = Thread { runCatching { runImmediate { source.read() } } }
        reader.start()
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))

        val failure = assertThrows(AudioCaptureException::class.java) { source.close() }

        assertEquals(AudioCaptureFailure.WORKER_TERMINATION_FAILED, failure.failure)
        assertEquals(1, stopCount)
        assertEquals(0, releaseCount)
        allowReadToExit.countDown()
        reader.join(5_000)
        source.close()
        assertEquals(1, stopCount)
        assertEquals(1, releaseCount)
    }

    @Test
    fun `stop unblocks a blocked platform read as cancellation before release`() {
        val readStarted = CountDownLatch(1)
        val unblockRead = CountDownLatch(1)
        val operations = mutableListOf<String>()
        val input = object : AudioCaptureInput {
            override val isInitialized = true
            override fun routedDeviceId(): Int = 7
            override fun start() { synchronized(operations) { operations += "start" } }
            override fun read(target: ShortArray, offset: Int, length: Int): Int {
                synchronized(operations) { operations += "read" }
                readStarted.countDown()
                check(unblockRead.await(5, TimeUnit.SECONDS)) { "read unblock timeout" }
                synchronized(operations) { operations += "read.return" }
                return -1
            }
            override fun stop() {
                synchronized(operations) { operations += "stop" }
                unblockRead.countDown()
            }
            override fun release() { synchronized(operations) { operations += "release" } }
        }
        val source = AudioRecordFrameSource(input, MicrophonePermissionGate { true })
        val failure = AtomicReference<Throwable?>()
        val reader = Thread {
            try { runImmediate { source.read() } } catch (caught: Throwable) { failure.set(caught) }
        }

        reader.start()
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))
        source.stop()
        reader.join(5_000)
        source.close()

        assertTrue(failure.get() is CancellationException)
        assertEquals(listOf("start", "read", "stop", "read.return", "release"), synchronized(operations) { operations.toList() })
        assertTrue(!reader.isAlive)
    }

    @Test
    fun `capture emits fixed 16 kHz 20 ms mono PCM16 frames and releases in order`() {
        val samples = ShortArray(320) { it.toShort() }
        val input = RecordingCaptureInput(readFrames = ArrayDeque(listOf(samples)))
        val source = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { true },
        )

        val frame = runImmediate { source.read() }
        source.close()
        source.close()

        assertEquals(16_000, frame?.sampleRateHz)
        assertEquals(320, frame?.sampleCount)
        assertTrue(samples.contentEquals(frame?.copySamples()))
        assertEquals("start", input.operations.first())
        assertTrue(input.operations.indexOf("read") < input.operations.indexOf("stop"))
        assertTrue(input.operations.indexOf("stop") < input.operations.indexOf("release"))
        assertTrue(input.readCount >= 1)
        assertEquals(1, input.startCount)
        assertEquals(1, input.stopCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `recording state interruption and recovery are observable without stopping capture`() {
        val events = Collections.synchronizedList(mutableListOf<AudioCaptureEvent>())
        val input = RecordingCaptureInput(
            readFrames = ArrayDeque(listOf(ShortArray(320) { 700 })),
        )
        val source = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { true },
            observer = AudioCaptureObserver { events.add(it) },
        )
        input.emitState(AudioInputState.SILENCED)

        runImmediate { source.read() }
        input.emitState(AudioInputState.ACTIVE)
        source.close()

        val snapshot = synchronized(events) { events.toList() }
        val interrupted = AudioCaptureEvent.InputInterrupted(
            AudioInputInterruptionReason.CLIENT_SILENCED,
        )
        assertTrue(snapshot.indexOf(AudioCaptureEvent.Started) < snapshot.indexOf(interrupted))
        assertTrue(snapshot.indexOf(interrupted) < snapshot.indexOf(AudioCaptureEvent.InputResumed))
        assertTrue(snapshot.none { it is AudioCaptureEvent.Level })
        assertEquals(1, input.startCount)
        assertEquals(1, input.stopCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `inactive configuration tolerates temporary missing route before recovery`() {
        val input = RecordingCaptureInput(
            readFrames = ArrayDeque(listOf(ShortArray(320) { 900 })),
        )
        val source = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { true },
        )
        input.routeId = null
        input.emitState(AudioInputState.INACTIVE)

        val frame = runImmediate { source.read() }
        input.routeId = 7
        input.emitState(AudioInputState.ACTIVE)
        source.close()

        assertEquals(320, frame?.sampleCount)
        assertEquals(1, input.startCount)
        assertEquals(1, input.stopCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `silenced configuration tolerates temporary missing route before recovery`() {
        val input = RecordingCaptureInput(
            readFrames = ArrayDeque(listOf(ShortArray(320) { 900 })),
        )
        val source = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { true },
        )
        input.routeId = null
        input.emitState(AudioInputState.SILENCED)

        val frame = runImmediate { source.read() }
        input.routeId = 7
        input.emitState(AudioInputState.ACTIVE)
        source.close()

        assertEquals(320, frame?.sampleCount)
        assertEquals(1, input.startCount)
        assertEquals(1, input.stopCount)
        assertEquals(1, input.releaseCount)
    }

    @Test
    fun `observer receives capture levels without changing emitted PCM`() {
        val samples = ShortArray(320) { (it * 31 - 4_000).toShort() }
        val events = Collections.synchronizedList(mutableListOf<AudioCaptureEvent>())
        val input = RecordingCaptureInput(readFrames = ArrayDeque(listOf(samples)))
        val source = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { true },
            observer = AudioCaptureObserver { events.add(it) },
        )

        val frame = runImmediate { source.read() }
        source.close()

        val snapshot = synchronized(events) { events.toList() }
        assertTrue(samples.contentEquals(frame?.copySamples()))
        assertEquals(AudioCaptureEvent.Started, snapshot.first())
        assertTrue(snapshot[1] is AudioCaptureEvent.Level)
        assertEquals(AudioCaptureEvent.Stopped, snapshot.last())
    }

    @Test
    fun `observer runtime failure cannot interrupt capture`() {
        val samples = ShortArray(320) { 1_000 }
        val input = RecordingCaptureInput(readFrames = ArrayDeque(listOf(samples)))
        val source = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { true },
            observer = AudioCaptureObserver { error("observer failure") },
        )

        val frame = runImmediate { source.read() }
        source.close()

        assertTrue(samples.contentEquals(frame?.copySamples()))
        assertEquals(1, input.stopCount)
        assertEquals(1, input.releaseCount)
    }

    private class RecordingCaptureInput(
        private val readFrames: ArrayDeque<ShortArray> = ArrayDeque(),
    ) : AudioCaptureInput {
        val operations = mutableListOf<String>()
        var startCount = 0
        var readCount = 0
        var stopCount = 0
        var releaseCount = 0
        var routeId: Int? = 7
        private val stopped = CountDownLatch(1)
        private var stateObserver = AudioInputStateObserver.NONE

        override val isInitialized: Boolean = true

        override fun routedDeviceId(): Int? = routeId

        override fun setRecordingStateObserver(observer: AudioInputStateObserver) {
            stateObserver = observer
        }

        fun emitState(state: AudioInputState) {
            stateObserver.onStateChanged(state)
        }

        override fun start() {
            operations += "start"
            startCount += 1
        }

        override fun read(target: ShortArray, offset: Int, length: Int): Int {
            operations += "read"
            readCount += 1
            val frame = readFrames.removeFirstOrNull()
            if (frame == null) {
                check(stopped.await(5, TimeUnit.SECONDS)) { "fake read stop timeout" }
                return -1
            }
            frame.copyInto(target, destinationOffset = offset, endIndex = minOf(frame.size, length))
            return minOf(frame.size, length)
        }

        override fun stop() {
            operations += "stop"
            stopCount += 1
            stopped.countDown()
        }

        override fun release() {
            operations += "release"
            releaseCount += 1
        }
    }

    private fun <T> runImmediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return checkNotNull(outcome) { "suspend block did not complete synchronously" }.getOrThrow()
    }
}
