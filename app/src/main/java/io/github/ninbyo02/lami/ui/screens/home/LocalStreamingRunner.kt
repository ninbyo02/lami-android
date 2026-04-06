package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal interface LocalStreamingRunner<T> {
    suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        onPartial: (String) -> Unit = {},
    ): T?
}

internal class DefaultLocalStreamingRunner<T>(
    private val timeoutMs: Long,
    private val runInference: suspend (
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        onPartial: (String) -> Unit,
    ) -> T,
) : LocalStreamingRunner<T> {
    override suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        onPartial: (String) -> Unit,
    ): T? = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<T> {
            runBlocking {
                runInference(
                    prompt,
                    localBaseModelFilePath,
                    localBaseModelDisplayName,
                    onPartial,
                )
            }
        }
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        } finally {
            future.cancel(true)
            executor.shutdownNow()
        }
    }
}

internal data class LocalRealPartialHookSnapshot(
    val attempted: Boolean = false,
    val attached: Boolean = false,
    val callbackCount: Int = 0,
)

internal class LocalRealPartialHookHandle(
    private val snapshotProvider: () -> LocalRealPartialHookSnapshot,
) {
    fun snapshot(): LocalRealPartialHookSnapshot = snapshotProvider()
}

internal fun tryAttachSingleListenerPartialHook(
    inferenceInstance: Any,
    onPartial: (String) -> Unit,
): LocalRealPartialHookHandle {
    val callbackCount = AtomicInteger(0)
    val lastPartial = AtomicReference<String?>(null)
    var attempted = false
    var attached = false

    runCatching {
        val listenerMethodNames = listOf(
            "setResultListener",
            "setPartialResultListener",
            "setTokenListener",
            "setStreamingListener",
        )
        val listenerMethod = listenerMethodNames.firstNotNullOfOrNull { targetName ->
            inferenceInstance.javaClass.methods.firstOrNull { method ->
                method.name.equals(targetName, ignoreCase = true) && method.parameterTypes.size == 1
            }
        } ?: inferenceInstance.javaClass.methods.firstOrNull { method ->
            val lowerName = method.name.lowercase(Locale.ROOT)
            (lowerName.contains("listener") || lowerName.contains("callback")) && method.parameterTypes.size == 1
        }
        if (listenerMethod == null) {
            return@runCatching
        }
        attempted = true
        val listenerType = listenerMethod.parameterTypes.firstOrNull() ?: return@runCatching
        if (!listenerType.isInterface) {
            return@runCatching
        }
        val listenerProxy = Proxy.newProxyInstance(
            listenerType.classLoader,
            arrayOf(listenerType),
        ) { _, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance when (method.name) {
                    "toString" -> "LocalRealPartialListenerProxy"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> null
                }
            }
            val extracted = extractPartialTextFromListenerArgs(args)
            if (!extracted.isNullOrBlank()) {
                val normalized = extracted.trim()
                val previous = lastPartial.get()
                if (previous != normalized) {
                    lastPartial.set(normalized)
                    val currentCount = callbackCount.incrementAndGet()
                    if (currentCount >= 2) {
                        onPartial(normalized)
                    }
                }
            }
            null
        }
        listenerMethod.isAccessible = true
        listenerMethod.invoke(inferenceInstance, listenerProxy)
        attached = true
    }

    return LocalRealPartialHookHandle(
        snapshotProvider = {
            LocalRealPartialHookSnapshot(
                attempted = attempted,
                attached = attached,
                callbackCount = callbackCount.get(),
            )
        }
    )
}

private fun extractPartialTextFromListenerArgs(args: Array<out Any?>?): String? {
    if (args.isNullOrEmpty()) return null
    args.forEach { value ->
        when (value) {
            is String -> if (value.isNotBlank()) return value
            is CharSequence -> if (value.isNotBlank()) return value.toString()
        }
    }
    args.forEach { value ->
        val text = runCatching {
            value?.javaClass?.methods?.firstNotNullOfOrNull { method ->
                if (method.parameterTypes.isNotEmpty()) return@firstNotNullOfOrNull null
                if (method.returnType != String::class.java &&
                    method.returnType != CharSequence::class.java
                ) {
                    return@firstNotNullOfOrNull null
                }
                val lowerName = method.name.lowercase(Locale.ROOT)
                if (!lowerName.contains("text") && !lowerName.contains("result") && !lowerName.contains("token")) {
                    return@firstNotNullOfOrNull null
                }
                (method.invoke(value) as? CharSequence)?.toString()
            }
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            return text
        }
    }
    return null
}
