package io.github.ninbyo02.lami.tts

import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
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
        private var tokenizer: Pair<String, LamiQwenTokenizer>? = null

        private var verifiedFiles: List<FileStamp>? = null
        private data class FileStamp(val path: String, val size: Long, val modified: Long)

        /** Reuse hashes only inside this short-lived session while all file stamps match. */
        suspend fun verifyBundle(root: File, hashes: JSONObject, progress: (String) -> Unit) {
            val started = SystemClock.elapsedRealtime()
            val rootPath = root.canonicalPath + File.separator
            val files = hashes.keys().asSequence().toList().sorted().map { name ->
                val file = root.resolve(name).canonicalFile
                require(file.path.startsWith(rootPath) && file.isFile) { "Invalid bundle file: $name" }
                name to file
            }
            fun stamps() = files.map { (_, file) -> FileStamp(file.path, file.length(), file.lastModified()) }
            val before = stamps()
            val reused = verifiedFiles == before
            if (!reused) {
                // File changes also invalidate native mmap modules and the tokenizer.
                close()
                for ((name, file) in files) {
                    currentCoroutineContext().ensureActive()
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().buffered().use { input ->
                        val bytes = ByteArray(1024 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(bytes)
                            if (count < 0) break
                            digest.update(bytes, 0, count)
                        }
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == hashes.getString(name)) { "Model hash mismatch: $name" }
                }
                check(before == stamps()) { "Bundle changed during verification" }
                verifiedFiles = before
            }
            progress("metric=hash_validation reused=$reused ms=${SystemClock.elapsedRealtime() - started}")
        }

        /** Called after the current session has verified the bundle. */
        fun tokenizer(root: File, progress: (String) -> Unit): LamiQwenTokenizer {
            val key = root.canonicalPath
            val existing = tokenizer?.takeIf { it.first == key }?.second
            val started = SystemClock.elapsedRealtime()
            val result = existing ?: LamiQwenTokenizer.load(root).also { tokenizer = key to it }
            progress("metric=tokenizer_load reused=${existing != null} ms=${SystemClock.elapsedRealtime() - started}")
            return result
        }

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
            tokenizer = null
            verifiedFiles = null
        }
    }
}
