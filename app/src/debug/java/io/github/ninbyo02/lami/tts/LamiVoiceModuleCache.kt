package io.github.ninbyo02.lami.tts

import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.pytorch.executorch.Module

/** Debug-only, serialized reuse; fresh decoder KV state is allocated for every utterance. */
internal object LamiVoiceModuleCache {
    private const val IDLE_MS = 45_000L
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var expiry: Job? = null
    private var cached: Session? = null

    suspend fun <T> withSession(root: File, block: suspend (Session) -> T): T = mutex.withLock {
        val manifest = root.resolve("voice-text-bundle.json").readBytes()
        val identity = root.canonicalPath + ":" + MessageDigest.getInstance("SHA-256")
            .digest(manifest).joinToString("") { "%02x".format(it) }
        expiry?.cancel()
        if (cached?.identity != identity) {
            cached?.close()
            cached = Session(identity)
        }
        val session = checkNotNull(cached)
        try {
            block(session)
        } catch (error: Throwable) {
            session.close()
            cached = null
            throw error
        } finally {
            if (cached === session) expiry = scope.launch {
                delay(IDLE_MS)
                mutex.withLock {
                    if (cached === session) {
                        session.close()
                        cached = null
                    }
                }
            }
        }
    }

    internal class Session(val identity: String) {
        private val modules = linkedMapOf<String, Module>()
        suspend fun <T> useModule(file: File, progress: (String) -> Unit, block: suspend (Module) -> T): T {
            val key = file.canonicalPath
            val existing = modules[key]
            val started = SystemClock.elapsedRealtime()
            val module = existing ?: Module.load(key, Module.LOAD_MODE_MMAP).also {
                try { it.loadMethod("forward") } catch (error: Throwable) { it.close(); throw error }
                modules[key] = it
            }
            progress("metric=model_load name=${file.name} reused=${existing != null} ms=${SystemClock.elapsedRealtime() - started}")
            return block(module)
        }
        fun close() {
            modules.values.forEach { runCatching { it.close() } }
            modules.clear()
        }
    }
}
