package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_HELD_ENGINE_REUSE_COUNT = 3

internal data class HeldLocalEngine(
    val modelPath: String,
    val engineInstance: Any,
    val namespace: String?,
    val createdAtElapsedMs: Long,
    var lastUsedAtElapsedMs: Long,
    var useCount: Int,
    val closeEngine: (((String) -> Unit)?) -> Unit,
)

internal class LocalInferenceEngineHolder(
    private val appContext: Context,
) {
    private val mutex = Mutex()
    private var held: HeldLocalEngine? = null

    suspend fun acquire(
        modelPath: String,
        appendTrace: ((String) -> Unit)? = null,
    ): HeldLocalEngine = mutex.withLock {
        val current = held
        if (current != null && current.modelPath == modelPath) {
            if (current.useCount >= MAX_HELD_ENGINE_REUSE_COUNT) {
                val engineClassName = current.engineInstance.javaClass.name
                appendTrace?.invoke(
                    "UPSTREAM held-engine close-start reason=reuse-limit class=$engineClassName useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT modelPathTail=${current.modelPath.substringAfterLast('/')}",
                )
                runCatching { current.closeEngine(appendTrace) }
                held = null
                appendTrace?.invoke(
                    "UPSTREAM held-engine recycle reason=reuse-limit reached useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT modelPathTail=${modelPath.substringAfterLast('/')}",
                )
            } else {
                current.useCount += 1
                current.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
                appendTrace?.invoke(
                    "UPSTREAM held-engine reuse-hit modelPathTail=${modelPath.substringAfterLast('/')} useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
                )
                return@withLock current
            }
        }

        if (current != null && current.modelPath != modelPath) {
            val engineClassName = current.engineInstance.javaClass.name
            appendTrace?.invoke(
                "UPSTREAM held-engine close-start reason=model-changed class=$engineClassName modelPathTail=${current.modelPath.substringAfterLast('/')}",
            )
            runCatching { current.closeEngine(appendTrace) }
            held = null
            appendTrace?.invoke("UPSTREAM held-engine cleared reason=model-changed")
        }

        val created = createReusableLocalInferenceEngine(
            context = appContext,
            modelPath = modelPath,
            appendTrace = appendTrace,
        ) ?: throw IllegalStateException("Failed to create local inference engine. modelPath=$modelPath")
        created.useCount += 1
        created.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
        appendTrace?.invoke(
            "UPSTREAM held-engine create-success modelPathTail=${modelPath.substringAfterLast('/')} useCount=${created.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
        )
        held = created
        created
    }

    suspend fun clear(appendTrace: ((String) -> Unit)? = null) {
        mutex.withLock {
            val current = held ?: return@withLock
            val engineClassName = current.engineInstance.javaClass.name
            appendTrace?.invoke(
                "UPSTREAM held-engine close-start reason=clear class=$engineClassName modelPathTail=${current.modelPath.substringAfterLast('/')}",
            )
            runCatching { current.closeEngine(appendTrace) }
            held = null
        }
    }

    suspend fun clearIfModelChanged(
        newModelPath: String,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        mutex.withLock {
            val current = held ?: return@withLock
            if (current.modelPath == newModelPath) return@withLock
            val engineClassName = current.engineInstance.javaClass.name
            appendTrace?.invoke(
                "UPSTREAM held-engine close-start reason=clear-model-changed class=$engineClassName modelPathTail=${current.modelPath.substringAfterLast('/')}",
            )
            runCatching { current.closeEngine(appendTrace) }
            held = null
        }
    }
}
