package io.github.ninbyo02.lami.asr.foundation.audio

fun interface MicrophonePermissionGate {
    fun isGranted(): Boolean
}

enum class AudioInputState {
    ACTIVE,
    SILENCED,
    INACTIVE,
}

fun interface AudioInputStateObserver {
    fun onStateChanged(state: AudioInputState)

    companion object {
        val NONE = AudioInputStateObserver { }
    }
}

/** Narrow facade around Android AudioRecord so capture ownership is host-testable. */
interface AudioCaptureInput {
    val isInitialized: Boolean

    fun routedDeviceId(): Int?

    fun setRecordingStateObserver(observer: AudioInputStateObserver) = Unit

    fun start()

    fun read(target: ShortArray, offset: Int, length: Int): Int

    /** Must unblock an in-progress [read]. */
    fun stop()

    fun release()
}

/**
 * AudioRecord owner with a single capture worker and a bounded memory-only handoff.
 * The default capacity is 500 ms; callers may provide a larger bounded capacity. Capture starts
 * lazily. [stop] unblocks and joins the worker before cancelling readers; [close] additionally
 * releases the platform input. Both operations are idempotent.
 */
class AudioRecordFrameSource(
    private val input: AudioCaptureInput,
    private val permissionGate: MicrophonePermissionGate,
    private val observer: AudioCaptureObserver = AudioCaptureObserver.NONE,
    private val capacityFrames: Int = CAPACITY_FRAMES,
    private val workerJoinTimeoutMillis: Long = WORKER_JOIN_TIMEOUT_MILLIS,
) : AudioFrameSource {
    private val monitor = Any()
    private val channel = BoundedPcmFrameChannel(capacityFrames)
    private var worker: Thread? = null
    private var pendingInputState: AudioInputState? = null
    private var currentInputState: AudioInputState? = null
    private var initialRouteId: Int? = null
    private var initialRouteEstablished = false
    private var stopRequested = false
    @Volatile private var finishing = false
    private var nativeStopCalled = false
    private var observerStopNotified = false
    private var released = false

    init {
        require(workerJoinTimeoutMillis > 0) { "workerJoinTimeoutMillis must be positive" }
        try {
            input.setRecordingStateObserver(AudioInputStateObserver(::onInputStateChanged))
        } catch (_: RuntimeException) {
            runCatching { input.release() }
            throw AudioCaptureException(AudioCaptureFailure.START_FAILED)
        }
    }

    override suspend fun read(): Pcm16Frame? {
        if (!finishing) {
            try { ensureStarted() } catch (cancelled: AudioCaptureCancellationException) {
                if (!finishing) throw cancelled
            }
        }
        return channel.take()
    }

    private fun ensureStarted() {
        synchronized(monitor) {
            check(!released) { "audio source is closed" }
            if (stopRequested) throw AudioCaptureCancellationException()
            if (worker != null) return
            val preflightFailure = try {
                when {
                    !permissionGate.isGranted() -> AudioCaptureFailure.PERMISSION_REVOKED
                    !input.isInitialized -> AudioCaptureFailure.NOT_INITIALIZED
                    else -> null
                }
            } catch (_: RuntimeException) {
                AudioCaptureFailure.START_FAILED
            }
            if (preflightFailure != null) {
                releaseLockedIgnoringRuntimeFailure()
                throw AudioCaptureException(preflightFailure)
            }
            try {
                input.start()
                initialRouteId = input.routedDeviceId()
                initialRouteEstablished = initialRouteId != null
            } catch (_: RuntimeException) {
                releaseLockedIgnoringRuntimeFailure()
                throw AudioCaptureException(AudioCaptureFailure.START_FAILED)
            }
            val captureWorker = Thread(::captureLoop, "lami-audio-capture")
            worker = captureWorker
            safeNotify(AudioCaptureEvent.Started)
            pendingInputState?.let { safeNotify(it.toCaptureEvent()) }
            pendingInputState = null
            captureWorker.start()
        }
    }

    private fun captureLoop() {
        val readBuffer = ShortArray(READ_BUFFER_SAMPLES)
        val frameBuffer = ShortArray(FRAME_SAMPLES)
        var frameOffset = 0
        var completedFrameCount = 0
        try {
            while (!isStopRequested()) {
                if (!permissionGate.isGranted()) {
                    channel.fail(AudioCaptureFailure.PERMISSION_REVOKED)
                    return
                }
                if (hasUnexpectedRouteChange()) {
                    channel.fail(AudioCaptureFailure.ROUTE_CHANGED)
                    return
                }
                val count = input.read(readBuffer, 0, readBuffer.size)
                if (isStopRequested()) return
                if (!permissionGate.isGranted()) {
                    channel.fail(AudioCaptureFailure.PERMISSION_REVOKED)
                    return
                }
                if (hasUnexpectedRouteChange()) {
                    channel.fail(AudioCaptureFailure.ROUTE_CHANGED)
                    return
                }
                if (count <= 0) {
                    if (isStopRequested()) return
                    channel.fail(AudioCaptureFailure.READ_FAILED)
                    return
                }
                var sourceOffset = 0
                while (sourceOffset < count) {
                    val copied = minOf(FRAME_SAMPLES - frameOffset, count - sourceOffset)
                    readBuffer.copyInto(
                        destination = frameBuffer,
                        destinationOffset = frameOffset,
                        startIndex = sourceOffset,
                        endIndex = sourceOffset + copied,
                    )
                    frameOffset += copied
                    sourceOffset += copied
                    if (frameOffset == FRAME_SAMPLES) {
                        if (isStopRequested()) return
                        if (
                            completedFrameCount % METER_INTERVAL_FRAMES == 0 &&
                            shouldPublishMeterLevel()
                        ) {
                            safeNotify(AudioCaptureEvent.Level(measureAudioLevel(frameBuffer)))
                        }
                        completedFrameCount += 1
                        if (channel.offerPcm16(frameBuffer) != FrameOfferResult.ACCEPTED) {
                            return
                        }
                        frameOffset = 0
                    }
                }
            }
        } catch (failure: Throwable) {
            if (!isStopRequested()) channel.fail(AudioCaptureFailure.READ_FAILED)
            if (failure is Error) throw failure
        } finally {
            readBuffer.fill(0)
            frameBuffer.fill(0)
        }
    }

    internal fun awaitWorkerTerminationForTest(timeoutMillis: Long): Boolean {
        val activeWorker = synchronized(monitor) { worker } ?: return true
        return try {
            activeWorker.join(timeoutMillis)
            !activeWorker.isAlive
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun finish() {
        finishing = true
        stopCapture(preserveFrames = true)
    }

    override fun stop() = stopCapture(preserveFrames = false)

    private fun stopCapture(preserveFrames: Boolean) {
        val activeWorker: Thread?
        val callNativeStop: Boolean
        val notifyObserverStop: Boolean
        synchronized(monitor) {
            stopRequested = true
            activeWorker = worker
            callNativeStop = activeWorker != null && !nativeStopCalled
            notifyObserverStop = activeWorker != null && !observerStopNotified
            if (callNativeStop) nativeStopCalled = true
            if (notifyObserverStop) observerStopNotified = true
        }
        if (callNativeStop) {
            try {
                input.stop()
            } catch (_: RuntimeException) {
                // Continue joining; release is forbidden while the capture worker remains alive.
            }
        }
        if (notifyObserverStop) safeNotify(AudioCaptureEvent.Stopped)
        if (activeWorker != null && activeWorker !== Thread.currentThread()) {
            try {
                activeWorker.join(workerJoinTimeoutMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                channel.fail(AudioCaptureFailure.WORKER_TERMINATION_FAILED)
                throw AudioCaptureException(AudioCaptureFailure.WORKER_TERMINATION_FAILED)
            }
            if (activeWorker.isAlive) {
                channel.fail(AudioCaptureFailure.WORKER_TERMINATION_FAILED)
                throw AudioCaptureException(AudioCaptureFailure.WORKER_TERMINATION_FAILED)
            }
        }
        if (preserveFrames) channel.finish() else channel.cancel()
    }

    override fun close() {
        stop()
        synchronized(monitor) {
            releaseLocked()
        }
    }

    private fun isStopRequested(): Boolean = synchronized(monitor) { stopRequested }

    private fun shouldPublishMeterLevel(): Boolean = synchronized(monitor) {
        currentInputState != AudioInputState.SILENCED &&
            currentInputState != AudioInputState.INACTIVE
    }

    private fun hasUnexpectedRouteChange(): Boolean {
        if (synchronized(monitor) { currentInputState.isTemporarilyUnobservable() }) return false
        val currentRouteId = input.routedDeviceId()
        return synchronized(monitor) {
            if (currentInputState.isTemporarilyUnobservable()) return@synchronized false
            if (!initialRouteEstablished && currentRouteId != null) {
                initialRouteId = currentRouteId
                initialRouteEstablished = true
                false
            } else {
                initialRouteEstablished && currentRouteId != initialRouteId
            }
        }
    }

    private fun onInputStateChanged(state: AudioInputState) {
        val event = synchronized(monitor) {
            if (stopRequested || released) return
            currentInputState = state
            if (worker == null) {
                pendingInputState = state
                return
            }
            state.toCaptureEvent()
        }
        safeNotify(event)
    }

    private fun safeNotify(event: AudioCaptureEvent) {
        try {
            observer.onEvent(event)
        } catch (_: RuntimeException) {
            // Metering is observational and must never alter audio capture.
        }
    }

    private fun releaseLocked() {
        if (released) return
        released = true
        input.release()
    }

    private fun releaseLockedIgnoringRuntimeFailure() {
        try {
            releaseLocked()
        } catch (_: RuntimeException) {
            // The source remains terminal; platform details must not replace the sanitized failure.
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_SAMPLES = 320
        const val READ_BUFFER_SAMPLES = 640
        const val METER_INTERVAL_FRAMES = 3
        const val CAPACITY_FRAMES = 25
        const val WORKER_JOIN_TIMEOUT_MILLIS = 5_000L
    }
}

private fun AudioInputState?.isTemporarilyUnobservable(): Boolean =
    this == AudioInputState.SILENCED || this == AudioInputState.INACTIVE

private fun AudioInputState.toCaptureEvent(): AudioCaptureEvent = when (this) {
    AudioInputState.ACTIVE -> AudioCaptureEvent.InputResumed
    AudioInputState.SILENCED -> AudioCaptureEvent.InputInterrupted(
        AudioInputInterruptionReason.CLIENT_SILENCED,
    )
    AudioInputState.INACTIVE -> AudioCaptureEvent.InputInterrupted(
        AudioInputInterruptionReason.CONFIGURATION_INACTIVE,
    )
}
