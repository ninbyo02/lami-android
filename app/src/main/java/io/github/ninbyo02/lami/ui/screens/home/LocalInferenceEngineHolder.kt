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

internal data class HeldEngineAcquireDiagnosticResult(
    val engine: HeldLocalEngine?,
    val failureStage: String?,
    val failureClassName: String?,
    val failureMessage: String?,
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

        val createdDiagnostic = createReusableLocalInferenceEngineWithDiagnostic(
            context = appContext,
            modelPath = modelPath,
            appendTrace = appendTrace,
        )
        val created = createdDiagnostic.engine
            ?: throw IllegalStateException(
                "Failed to create local inference engine. modelPath=$modelPath stage=${createdDiagnostic.stage} class=${createdDiagnostic.className} message=${createdDiagnostic.message}",
            )
        created.useCount += 1
        created.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
        appendTrace?.invoke(
            "UPSTREAM held-engine create-success modelPathTail=${modelPath.substringAfterLast('/')} useCount=${created.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
        )
        held = created
        created
    }

    suspend fun acquireWithDiagnostic(
        modelPath: String,
        appendTrace: ((String) -> Unit)? = null,
    ): HeldEngineAcquireDiagnosticResult = mutex.withLock {
        val modelPathTail = modelPath.substringAfterLast('/')
        appendTrace?.invoke("UPSTREAM held-acquire-diagnostic start modelPathTail=$modelPathTail")
        var failureStage: String? = null
        try {
            val current = held
            if (current != null && current.modelPath == modelPath) {
                if (current.useCount >= MAX_HELD_ENGINE_REUSE_COUNT) {
                    failureStage = "recycle-close"
                    val engineClassName = current.engineInstance.javaClass.name
                    appendTrace?.invoke(
                        "UPSTREAM held-engine close-start reason=reuse-limit class=$engineClassName useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT modelPathTail=${current.modelPath.substringAfterLast('/')}",
                    )
                    current.closeEngine(appendTrace)
                    held = null
                    appendTrace?.invoke(
                        "UPSTREAM held-engine recycle reason=reuse-limit reached useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT modelPathTail=$modelPathTail",
                    )
                } else {
                    current.useCount += 1
                    current.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
                    appendTrace?.invoke(
                        "UPSTREAM held-engine reuse-hit modelPathTail=$modelPathTail useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
                    )
                    appendTrace?.invoke(
                        "UPSTREAM held-acquire-diagnostic success heldHash=${current.hashCode()} useCount=${current.useCount}",
                    )
                    return@withLock HeldEngineAcquireDiagnosticResult(
                        engine = current,
                        failureStage = null,
                        failureClassName = null,
                        failureMessage = null,
                    )
                }
            }

            if (current != null && current.modelPath != modelPath) {
                failureStage = "recycle-close"
                val engineClassName = current.engineInstance.javaClass.name
                appendTrace?.invoke(
                    "UPSTREAM held-engine close-start reason=model-changed class=$engineClassName modelPathTail=${current.modelPath.substringAfterLast('/')}",
                )
                current.closeEngine(appendTrace)
                held = null
                appendTrace?.invoke("UPSTREAM held-engine cleared reason=model-changed")
            }

            failureStage = "create-reusable-engine"
            val createdDiagnostic = createReusableLocalInferenceEngineWithDiagnostic(
                context = appContext,
                modelPath = modelPath,
                appendTrace = appendTrace,
            )
            appendTrace?.invoke(
                "UPSTREAM held-create-diagnostic stage=${createdDiagnostic.stage ?: "unknown"} class=${createdDiagnostic.className ?: "none"} message=${createdDiagnostic.message ?: "none"}",
            )
            val created = createdDiagnostic.engine
            if (created == null) {
                val failStage = createdDiagnostic.stage ?: "create-reusable-engine"
                val failClass = createdDiagnostic.className ?: "ReturnedNull"
                val failMessage = (createdDiagnostic.message ?: "createReusableLocalInferenceEngine returned null").take(200)
                appendTrace?.invoke(
                    "UPSTREAM held-acquire-diagnostic fail stage=$failStage class=$failClass message=$failMessage",
                )
                return@withLock HeldEngineAcquireDiagnosticResult(
                    engine = null,
                    failureStage = failStage,
                    failureClassName = failClass,
                    failureMessage = failMessage,
                )
            }
            created.useCount += 1
            created.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
            appendTrace?.invoke(
                "UPSTREAM held-engine create-success modelPathTail=$modelPathTail useCount=${created.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
            )
            failureStage = "store-held"
            held = created
            appendTrace?.invoke(
                "UPSTREAM held-acquire-diagnostic success heldHash=${created.hashCode()} useCount=${created.useCount}",
            )
            HeldEngineAcquireDiagnosticResult(
                engine = created,
                failureStage = null,
                failureClassName = null,
                failureMessage = null,
            )
        } catch (e: Exception) {
            val resolvedStage = failureStage ?: "unknown"
            val failureClassName = e::class.java.simpleName.ifBlank { e::class.java.name }
            val failureMessage = (e.message ?: "no message").take(200)
            appendTrace?.invoke(
                "UPSTREAM held-acquire-diagnostic fail stage=$resolvedStage class=$failureClassName message=$failureMessage",
            )
            HeldEngineAcquireDiagnosticResult(
                engine = null,
                failureStage = resolvedStage,
                failureClassName = failureClassName,
                failureMessage = failureMessage,
            )
        }
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
