package io.github.ninbyo02.lami.asr.foundation.asr

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import io.github.ninbyo02.lami.asr.foundation.audio.OwnedPcm16Frame
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactFile
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole
import io.github.ninbyo02.lami.asr.foundation.testing.fixtureVerifiedModel
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeAsrEngineTest {
    @Test
    fun `frame owns a defensive copy of microphone samples`() {
        val source = shortArrayOf(1, 2, 3)
        val frame = Pcm16Frame(sampleRateHz = 16_000, samples = source)

        source[0] = 99

        assertArrayEquals(shortArrayOf(1, 2, 3), frame.copySamples())
    }

    @Test
    fun `internal ownership transfer is one shot and keeps outward reads defensive`() {
        val source = Pcm16Frame(sampleRateHz = 16_000, samples = shortArrayOf(1, 2, 3))
        val owned = source.copyForOwnershipTransfer()
        val transferred = Pcm16Frame.takeOwnership(owned)

        val outward = transferred.copySamples()
        outward[0] = 99
        assertArrayEquals(shortArrayOf(1, 2, 3), transferred.copySamples())
        assertThrows(IllegalStateException::class.java) { Pcm16Frame.takeOwnership(owned) }
    }

    @Test
    fun `ownership token validates shape and owns a defensive source copy`() {
        assertThrows(IllegalArgumentException::class.java) {
            OwnedPcm16Frame.copyOf(sampleRateHz = 0, samples = shortArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OwnedPcm16Frame.copyOf(sampleRateHz = 16_000, samples = shortArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            OwnedPcm16Frame.copyOf(sampleRateHz = 16_000, samples = ShortArray(16_001))
        }

        val source = shortArrayOf(1, 2, 3)
        val owned = OwnedPcm16Frame.copyOf(sampleRateHz = 16_000, samples = source)
        source[0] = 99
        assertArrayEquals(shortArrayOf(1, 2, 3), Pcm16Frame.takeOwnership(owned).copySamples())
    }

    @Test
    fun `public synthetic constructors cannot bypass ownership capability or validation`() {
        fun assertBridgeRejected(constructor: java.lang.reflect.Constructor<*>, rate: Int, samples: ShortArray) {
            val arguments = arrayOfNulls<Any>(constructor.parameterCount)
            arguments[0] = rate
            arguments[1] = samples
            val failure = assertThrows(InvocationTargetException::class.java) {
                constructor.newInstance(*arguments)
            }
            assertEquals(IllegalArgumentException::class.java, failure.cause?.javaClass)
        }

        val ownedBridge = OwnedPcm16Frame::class.java.constructors.single { it.isSynthetic }
        assertBridgeRejected(ownedBridge, 16_000, shortArrayOf(7))

        val frameBridge = Pcm16Frame::class.java.constructors.single { it.isSynthetic }
        assertBridgeRejected(frameBridge, 0, shortArrayOf())
        assertBridgeRejected(frameBridge, 16_000, shortArrayOf(7))

        val capability = Class.forName(
            "io.github.ninbyo02.lami.asr.foundation.audio.PcmOwnershipCapability",
        )
        assertThrows(NoSuchMethodException::class.java) { capability.getMethod("token") }
        assertThrows(NoSuchMethodException::class.java) {
            capability.getMethod("isValid", Any::class.java)
        }
    }

    @Test
    fun `frame rejects invalid audio shape`() {
        assertThrows(IllegalArgumentException::class.java) {
            Pcm16Frame(sampleRateHz = 0, samples = shortArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pcm16Frame(sampleRateHz = 16_000, samples = shortArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pcm16Frame(sampleRateHz = 16_000, samples = ShortArray(16_001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pcm16Frame(sampleRateHz = 96_000, samples = shortArrayOf(1))
        }
    }

    @Test
    fun `model file rejects an unpinned digest`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelArtifactFile(
                role = ModelFileRole.ENCODER,
                relativePath = "encoder.onnx",
                sizeBytes = 1,
                sha256 = "not-pinned",
            )
        }
    }

    @Test
    fun `fake session emits scripted partial and endpoint then finishes`() {
        val engine = FakeAsrEngine(
            scriptedEvents = listOf(
                AsrEvent.Partial("こん"),
                AsrEvent.Endpoint("こんにちは"),
            ),
        )
        val session = runImmediate {
            engine.open(fixtureModel())
        }

        val events = runImmediate {
            session.accept(Pcm16Frame(16_000, shortArrayOf(1, 2, 3)))
        }
        val result = runImmediate { session.finish() }

        assertEquals(listOf(AsrEvent.Partial("こん"), AsrEvent.Endpoint("こんにちは")), events)
        assertEquals(AsrResult("こんにちは"), result)
    }

    @Test
    fun `fake engine snapshots a mutable script`() {
        val mutableScript = mutableListOf<AsrEvent>(AsrEvent.Endpoint("original"))
        val engine = FakeAsrEngine(mutableScript)
        mutableScript[0] = AsrEvent.Endpoint("mutated")
        val session = runImmediate { engine.open(fixtureModel()) }

        val events = runImmediate {
            session.accept(Pcm16Frame(16_000, shortArrayOf(1)))
        }

        assertEquals(listOf(AsrEvent.Endpoint("original")), events)
    }

    @Test
    fun `cancel is idempotent and rejects later audio`() {
        val session = runImmediate {
            FakeAsrEngine().open(fixtureModel())
        }

        session.cancel()
        session.cancel()

        assertThrows(IllegalStateException::class.java) {
            runImmediate {
                session.accept(Pcm16Frame(16_000, shortArrayOf(1)))
            }
        }
    }

    @Test
    fun `close is idempotent and rejects finish`() {
        val session = runImmediate {
            FakeAsrEngine().open(fixtureModel())
        }

        session.close()
        session.close()

        assertThrows(IllegalStateException::class.java) {
            runImmediate { session.finish() }
        }
    }

    @Test
    fun `closed engine rejects a new session`() {
        val engine = FakeAsrEngine()
        engine.close()
        engine.close()

        assertThrows(IllegalStateException::class.java) {
            runImmediate { engine.open(fixtureModel()) }
        }
    }

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

    private fun fixtureModel() = fixtureVerifiedModel()
}
