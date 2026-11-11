package io.github.ninbyo02.lami.tts

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object LamiKokoroScratchNative {
    val available: Boolean by lazy {
        runCatching {
            System.loadLibrary("lami_kokoro_scratch")
            version() == "1.27.1"
        }.getOrDefault(false)
    }

    private external fun version(): String
    external fun load(textPath: ByteArray, renderPath: ByteArray, maxFrames: Int): Long
    external fun newRun(): Long
    external fun synthesize(session: Long, run: Long, ids: LongArray): FloatArray
    external fun cancelRun(run: Long)
    external fun releaseRun(run: Long)
    external fun release(session: Long)
}

internal class LamiKokoroScratchFrameLimitException(message: String) : IllegalArgumentException(message)

internal class LamiKokoroScratchSession(private val bundle: LamiKokoroScratchBundle) : LamiResidentPcmSession {
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LamiKokoroScratch").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private val closing = AtomicBoolean()
    private val runLock = Any()
    private var epoch = 0L
    private var activeRun = 0L
    private var session = 0L
    private var vocabulary: LamiKokoroScratchVocabulary? = null

    override suspend fun prewarm() {
        val owner = synchronized(runLock) { epoch }
        withContext(dispatcher) { mutex.withLock { ensureLoaded(owner) } }
    }

    override suspend fun synthesize(text: String): FloatArray = synthesizeRequest(splitOnFrameLimit = true) { vocabulary ->
        vocabulary.encode(LamiKokoroJapaneseFrontend.phonemize(text))
    }

    /** Exact exported phoneme IDs allow testing the graphs independently of text analysis. */
    suspend fun synthesizeDiagnostic(ids: LongArray): FloatArray {
        check(bundle.diagnosticOnly)
        val tokens = ids.copyOf()
        return synthesizeRequest(splitOnFrameLimit = false) { tokens }
    }

    private suspend fun synthesizeRequest(
        splitOnFrameLimit: Boolean,
        tokens: (LamiKokoroScratchVocabulary) -> LongArray,
    ): FloatArray {
        val owner = synchronized(runLock) { epoch }
        return withContext(dispatcher) {
            mutex.withLock {
                checkRequest(owner)
                ensureLoaded(owner)
                checkRequest(owner)
                val dictionary = checkNotNull(vocabulary)
                val ids = tokens(dictionary)
                dictionary.requireValid(ids)
                checkRequest(owner)
                val run = LamiKokoroScratchNative.newRun()
                check(run != 0L) { "Kokoroの音声生成を開始できませんでした。" }
                try {
                    synchronized(runLock) {
                        if (owner != epoch) throw CancellationException("Kokoro request stopped")
                        activeRun = run
                    }
                    val pcm = if (splitOnFrameLimit) {
                        synthesizeBounded(ids, dictionary, run, owner)
                    } else {
                        LamiKokoroScratchNative.synthesize(session, run, ids)
                    }
                    checkRequest(owner)
                    LamiPcmContract.validate(pcm)
                    pcm
                } catch (failure: Exception) {
                    checkRequest(owner)
                    throw failure
                } finally {
                    synchronized(runLock) {
                        if (activeRun == run) activeRun = 0L
                        LamiKokoroScratchNative.releaseRun(run)
                    }
                }
            }
        }
    }

    private suspend fun synthesizeBounded(
        ids: LongArray,
        dictionary: LamiKokoroScratchVocabulary,
        run: Long,
        owner: Long,
    ): FloatArray {
        val remaining = ArrayDeque<LongArray>().apply { add(ids) }
        val chunks = mutableListOf<FloatArray>()
        var samples = 0
        while (remaining.isNotEmpty()) {
            checkRequest(owner)
            val next = remaining.removeFirst()
            val pcm = try {
                LamiKokoroScratchNative.synthesize(session, run, next)
            } catch (failure: LamiKokoroScratchFrameLimitException) {
                val (first, second) = dictionary.splitAtWordBoundary(next) ?: throw failure
                remaining.addFirst(second)
                remaining.addFirst(first)
                continue
            }
            checkRequest(owner)
            LamiPcmContract.validate(pcm)
            require(samples + pcm.size <= LamiPcmContract.SAMPLE_RATE * 30) {
                "Kokoroで一度に読み上げられる音声の長さを超えています。"
            }
            chunks += pcm
            samples += pcm.size
        }
        if (chunks.size == 1) return chunks.single()
        return FloatArray(samples).also { output ->
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
        }
    }

    private suspend fun checkRequest(owner: Long) {
        currentCoroutineContext().ensureActive()
        if (synchronized(runLock) { owner != epoch }) throw CancellationException("Kokoro request stopped")
    }

    private suspend fun ensureLoaded(owner: Long) {
        check(!closing.get()) { "Kokoro session is closed" }
        val context = currentCoroutineContext()
        fun checkLoad() {
            context.ensureActive()
            if (synchronized(runLock) { closing.get() || owner != epoch }) {
                throw CancellationException("Kokoro model load stopped")
            }
        }
        checkLoad()
        if (session != 0L) return
        check(LamiKokoroScratchNative.available) { "このビルドでKokoroの音声生成を起動できません。" }
        val dictionary = bundle.verifyAndReadVocabulary(::checkLoad)
        checkLoad()
        val loaded = try {
            LamiKokoroScratchNative.load(
                bundle.textModel.absolutePath.toByteArray(Charsets.UTF_8),
                bundle.renderModel.absolutePath.toByteArray(Charsets.UTF_8),
                bundle.maxFrames,
            )
        } catch (failure: Exception) {
            checkLoad()
            throw failure
        }
        check(loaded != 0L) { "Kokoroの音声モデルを起動できませんでした。" }
        try {
            synchronized(runLock) {
                checkLoad()
                vocabulary = dictionary
                session = loaded
            }
        } finally {
            // The native call can finish after Stop. Only a handle published
            // under the current request epoch belongs to the resident session.
            if (session != loaded) LamiKokoroScratchNative.release(loaded)
        }
    }

    override fun cancel() {
        synchronized(runLock) {
            epoch++
            if (activeRun != 0L) runCatching { LamiKokoroScratchNative.cancelRun(activeRun) }
        }
    }

    override suspend fun close() {
        if (!closing.compareAndSet(false, true)) return
        cancel()
        try {
            withContext(NonCancellable + dispatcher) {
                mutex.withLock {
                    if (session != 0L) LamiKokoroScratchNative.release(session)
                    session = 0L
                    vocabulary = null
                }
            }
        } finally {
            dispatcher.close()
        }
    }
}
