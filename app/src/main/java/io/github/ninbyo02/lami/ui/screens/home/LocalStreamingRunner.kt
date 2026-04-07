package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
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
internal data class LocalOfficialConversationApiProbeResult(
    val namespace: String?,
    val conversationClassFound: Boolean,
    val createConversationMethodFound: Boolean,
    val sendMessageAsyncMethodFound: Boolean,
    val sendMessageAsyncReturnsFlow: Boolean,
    val messageClassFound: Boolean,
    val fallbackNamespaceMatched: Boolean,
) {
    val isAvailable: Boolean
        get() =
            conversationClassFound &&
                createConversationMethodFound &&
                sendMessageAsyncMethodFound &&
                messageClassFound
}

internal fun probeLocalOfficialConversationApi(): LocalOfficialConversationApiProbeResult {
    val primary = probeSingleOfficialConversationNamespace(
        namespace = "com.google.ai.edge.litertlm",
        engineClassName = "com.google.ai.edge.litertlm.Engine",
    )
    if (primary.conversationClassFound ||
        primary.createConversationMethodFound ||
        primary.sendMessageAsyncMethodFound ||
        primary.messageClassFound
    ) {
        return primary
    }
    val fallback = probeSingleOfficialConversationNamespace(
        namespace = "com.google.mediapipe.tasks.genai.llminference",
        engineClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInference",
    )
    return fallback.copy(fallbackNamespaceMatched = fallback.isAvailable)
}

private fun probeSingleOfficialConversationNamespace(
    namespace: String,
    engineClassName: String,
): LocalOfficialConversationApiProbeResult {
    val conversationClass = runCatching {
        Class.forName("$namespace.Conversation")
    }.getOrNull()
    val messageClass = runCatching {
        Class.forName("$namespace.Message")
    }.getOrNull()
    val engineClass = runCatching {
        Class.forName(engineClassName)
    }.getOrNull()
    val createConversationMethodFound = engineClass?.methods?.any { it.name == "createConversation" } == true
    val sendMessageAsyncMethod = conversationClass?.methods?.firstOrNull { it.name == "sendMessageAsync" }
    val flowClass = runCatching { Class.forName("kotlinx.coroutines.flow.Flow") }.getOrNull()
    val sendMessageAsyncReturnsFlow = runCatching {
        val returnType = sendMessageAsyncMethod?.returnType ?: return@runCatching false
        when {
            flowClass != null && flowClass.isAssignableFrom(returnType) -> true
            returnType.name == "kotlinx.coroutines.flow.Flow" -> true
            returnType.interfaces.any { it.name == "kotlinx.coroutines.flow.Flow" } -> true
            else -> false
        }
    }.getOrDefault(false)

    return LocalOfficialConversationApiProbeResult(
        namespace = namespace,
        conversationClassFound = conversationClass != null,
        createConversationMethodFound = createConversationMethodFound,
        sendMessageAsyncMethodFound = sendMessageAsyncMethod != null,
        sendMessageAsyncReturnsFlow = sendMessageAsyncReturnsFlow,
        messageClassFound = messageClass != null,
        fallbackNamespaceMatched = false,
    )
}

internal data class LocalOfficialFlowStreamingResult(
    val response: String,
    val partialCount: Int,
    val firstNonEmptyPartialElapsedRealtimeMs: Long?,
)

internal suspend fun tryRunOfficialLiteRtFlowStreaming(
    prompt: String,
    modelPath: String,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit = {},
    onFallbackReason: (String) -> Unit = {},
): LocalOfficialFlowStreamingResult? {
    val startElapsedMs = SystemClock.elapsedRealtime()
    val attempts = listOf(
        LocalOfficialNamespaceSpec(
            namespace = "com.google.ai.edge.litertlm",
            engineClassName = "com.google.ai.edge.litertlm.Engine",
            optionsCandidates = listOf(
                "com.google.ai.edge.litertlm.Engine\$Options",
                "com.google.ai.edge.litertlm.EngineOptions",
            ),
        ),
        LocalOfficialNamespaceSpec(
            namespace = "com.google.mediapipe.tasks.genai.llminference",
            engineClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInference",
            optionsCandidates = listOf(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$Options",
            ),
        ),
    )
    var fallbackReasonReported = false
    attempts.forEach { spec ->
        val result = runCatching {
            runOfficialFlowStreamingSingleNamespace(
                spec = spec,
                prompt = prompt,
                modelPath = modelPath,
                startElapsedMs = startElapsedMs,
                onPartial = onPartial,
                appendTrace = appendTrace,
            )
        }.onFailure { throwable ->
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_exception"
            fallbackReasonReported = true
            runCatching { onFallbackReason(reasonCode) }
            runCatching {
                appendTrace(
                    "UPSTREAM official-flow-streaming fallback reason=$reasonCode namespace=${spec.namespace}, error=${throwable.javaClass.simpleName}:${throwable.message}",
                )
            }
        }.getOrNull()
        if (result != null) return result
    }
    if (!fallbackReasonReported) {
        runCatching { onFallbackReason("no_partial_emitted") }
    }
    return null
}

internal fun tryRunOfficialLiteRtBlockingConversation(
    prompt: String,
    modelPath: String,
    appendTrace: (String) -> Unit = {},
    onFallbackReason: (String) -> Unit = {},
): String? {
    val attempts = listOf(
        LocalOfficialNamespaceSpec(
            namespace = "com.google.ai.edge.litertlm",
            engineClassName = "com.google.ai.edge.litertlm.Engine",
            optionsCandidates = listOf(
                "com.google.ai.edge.litertlm.Engine\$Options",
                "com.google.ai.edge.litertlm.EngineOptions",
            ),
        ),
        LocalOfficialNamespaceSpec(
            namespace = "com.google.mediapipe.tasks.genai.llminference",
            engineClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInference",
            optionsCandidates = listOf(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$Options",
            ),
        ),
    )
    attempts.forEach { spec ->
        val response = runCatching {
            runOfficialBlockingConversationSingleNamespace(
                spec = spec,
                prompt = prompt,
                modelPath = modelPath,
            )
        }.onFailure { throwable ->
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_blocking_exception"
            runCatching { onFallbackReason(reasonCode) }
            runCatching {
                appendTrace(
                    "UPSTREAM official-blocking fallback reason=$reasonCode namespace=${spec.namespace}, error=${throwable.javaClass.simpleName}:${throwable.message}",
                )
            }
        }.getOrNull()
        if (!response.isNullOrBlank()) return response.trim()
    }
    return null
}

private class OfficialFlowFallbackException(
    val reasonCode: String,
    cause: Throwable? = null,
) : RuntimeException(reasonCode, cause)

private data class LocalOfficialNamespaceSpec(
    val namespace: String,
    val engineClassName: String,
    val optionsCandidates: List<String>,
)

private suspend fun runOfficialFlowStreamingSingleNamespace(
    spec: LocalOfficialNamespaceSpec,
    prompt: String,
    modelPath: String,
    startElapsedMs: Long,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit,
): LocalOfficialFlowStreamingResult? {
    val engineClass = runCatching { Class.forName(spec.engineClassName) }.getOrNull() ?: return null
    val conversationClass = runCatching { Class.forName("${spec.namespace}.Conversation") }.getOrNull() ?: return null
    val sendMessageAsyncMethod =
        conversationClass.methods.firstOrNull { it.name == "sendMessageAsync" && it.parameterTypes.size == 1 }
            ?: throw OfficialFlowFallbackException("send_message_async_missing")
    val createConversationMethod =
        engineClass.methods.firstOrNull { it.name == "createConversation" }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
    val engine = createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath)
        ?: throw OfficialFlowFallbackException("conversation_create_failed")
    var conversation: Any? = null
    try {
        conversation = runCatching {
            createOfficialConversation(engine, createConversationMethod)
        }.getOrElse { throwable ->
            throw OfficialFlowFallbackException("conversation_create_failed", throwable)
        } ?: throw OfficialFlowFallbackException("conversation_create_failed")
        val sendArgument =
            buildSendMessageArgument(
                parameterType = sendMessageAsyncMethod.parameterTypes.first(),
                namespace = spec.namespace,
                prompt = prompt,
            ) ?: throw OfficialFlowFallbackException("send_message_async_missing")
        val flowValue = runCatching {
            sendMessageAsyncMethod.invoke(conversation, sendArgument)
        }.getOrElse { throwable ->
            throw OfficialFlowFallbackException("send_message_async_missing", throwable)
        }
        val flow = flowValue as? Flow<*> ?: throw OfficialFlowFallbackException("send_message_async_missing")
        val builder = StringBuilder()
        var partialCount = 0
        var firstPartialMs: Long? = null
        var lastPartial: String? = null
        runCatching {
            flow.collect { message ->
                val extractedText = extractOfficialMessageText(message)
                    ?: throw OfficialFlowFallbackException("message_text_extract_failed")
                val extracted = extractedText.trim()
                if (extracted.isBlank() || extracted == lastPartial) return@collect
                lastPartial = extracted
                builder.append(extractedText)
                partialCount += 1
                if (firstPartialMs == null) {
                    firstPartialMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
                }
                onPartial(builder.toString())
            }
        }.getOrElse { throwable ->
            if (throwable is OfficialFlowFallbackException) throw throwable
            throw OfficialFlowFallbackException("flow_collect_failed", throwable)
        }
        if (partialCount <= 0) throw OfficialFlowFallbackException("no_partial_emitted")
        val response = builder.toString().trim()
        if (response.isBlank()) throw OfficialFlowFallbackException("empty_official_response")
        appendTrace("UPSTREAM official-flow-streaming partial count=$partialCount namespace=${spec.namespace}")
        return LocalOfficialFlowStreamingResult(
            response = response,
            partialCount = partialCount,
            firstNonEmptyPartialElapsedRealtimeMs = firstPartialMs,
        )
    } finally {
        closeQuietly(conversation)
        closeQuietly(engine)
    }
}

private fun runOfficialBlockingConversationSingleNamespace(
    spec: LocalOfficialNamespaceSpec,
    prompt: String,
    modelPath: String,
): String? {
    val engineClass = runCatching { Class.forName(spec.engineClassName) }.getOrNull() ?: return null
    val conversationClass = runCatching { Class.forName("${spec.namespace}.Conversation") }.getOrNull() ?: return null
    val sendMethod = conversationClass.methods.firstOrNull { method ->
        (method.name == "sendMessage" || method.name == "generateResponse") && method.parameterTypes.size == 1
    } ?: return null
    val createConversationMethod =
        engineClass.methods.firstOrNull { it.name == "createConversation" }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
    val engine = createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath)
        ?: throw OfficialFlowFallbackException("conversation_create_failed")
    var conversation: Any? = null
    try {
        conversation = createOfficialConversation(engine, createConversationMethod)
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
        val sendArgument = buildSendMessageArgument(
            parameterType = sendMethod.parameterTypes.first(),
            namespace = spec.namespace,
            prompt = prompt,
        ) ?: throw OfficialFlowFallbackException("send_message_missing")
        val responseValue = runCatching {
            sendMethod.invoke(conversation, sendArgument)
        }.getOrElse { throwable ->
            throw OfficialFlowFallbackException("send_message_missing", throwable)
        }
        return extractOfficialMessageText(responseValue)?.trim()?.takeIf { it.isNotBlank() }
    } finally {
        closeQuietly(conversation)
        closeQuietly(engine)
    }
}

private fun createOfficialEngineInstance(
    engineClass: Class<*>,
    optionClassNames: List<String>,
    modelPath: String,
): Any? {
    val factoryMethod = engineClass.methods.firstOrNull { method ->
        method.name == "createFromOptions" && method.parameterTypes.size == 1
    } ?: return null
    val options = optionClassNames.firstNotNullOfOrNull { optionClassName ->
        val optionClass = runCatching { Class.forName(optionClassName) }.getOrNull() ?: return@firstNotNullOfOrNull null
        buildOptionsObject(optionClass = optionClass, modelPath = modelPath)
    } ?: return null
    return runCatching { factoryMethod.invoke(null, options) }.getOrNull()
}

private fun buildOptionsObject(optionClass: Class<*>, modelPath: String): Any? {
    val builderFactory = optionClass.methods.firstOrNull { method ->
        method.name == "builder" && method.parameterTypes.isEmpty()
    } ?: return null
    val builder = runCatching { builderFactory.invoke(null) }.getOrNull() ?: return null
    val setterNames = listOf("setModelPath", "setModelFilePath", "setModelAssetPath")
    val setter = builder.javaClass.methods.firstOrNull { method ->
        setterNames.contains(method.name) &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes.first() == String::class.java
    } ?: return null
    runCatching {
        setter.invoke(builder, modelPath)
    }.getOrNull() ?: return null
    val buildMethod = builder.javaClass.methods.firstOrNull { method ->
        method.name == "build" && method.parameterTypes.isEmpty()
    } ?: return null
    return runCatching { buildMethod.invoke(builder) }.getOrNull()
}

private fun createOfficialConversation(engine: Any, createConversationMethod: Method): Any? {
    return when (createConversationMethod.parameterTypes.size) {
        0 -> runCatching { createConversationMethod.invoke(engine) }.getOrNull()
        else -> {
            val arg = createConversationMethod.parameterTypes.firstNotNullOfOrNull { parameterType ->
                buildEmptyByBuilder(parameterType)
            } ?: return null
            runCatching { createConversationMethod.invoke(engine, arg) }.getOrNull()
        }
    }
}

private fun buildEmptyByBuilder(type: Class<*>): Any? {
    val builderFactory = type.methods.firstOrNull { it.name == "builder" && it.parameterTypes.isEmpty() } ?: return null
    val builder = runCatching { builderFactory.invoke(null) }.getOrNull() ?: return null
    val buildMethod = builder.javaClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() } ?: return null
    return runCatching { buildMethod.invoke(builder) }.getOrNull()
}

private fun buildSendMessageArgument(
    parameterType: Class<*>,
    namespace: String,
    prompt: String,
): Any? {
    if (parameterType == String::class.java) return prompt
    if (parameterType.name == "$namespace.Message") {
        val fromText = parameterType.methods.firstOrNull { method ->
            (method.name == "fromText" || method.name == "create") &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes.first() == String::class.java
        }
        if (fromText != null) {
            runCatching { fromText.invoke(null, prompt) }.getOrNull()?.let { return it }
        }
        parameterType.constructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == 1 && ctor.parameterTypes.first() == String::class.java
        }?.let { ctor ->
            runCatching { ctor.newInstance(prompt) }.getOrNull()?.let { return it }
        }
    }
    return null
}

private fun extractOfficialMessageText(value: Any?): String? {
    when (value) {
        null -> return null
        is String -> return value
        is CharSequence -> return value.toString()
        is Iterable<*> -> {
            value.forEach { nested ->
                extractOfficialMessageText(nested)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }
    }
    if (value.javaClass.isArray) {
        (value as? Array<*>)?.forEach { nested ->
            extractOfficialMessageText(nested)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    val getterNames = listOf(
        "getText",
        "text",
        "getContent",
        "content",
        "getResult",
        "result",
        "getToken",
        "token",
        "getParts",
        "parts",
    )
    getterNames.forEach { getterName ->
        val method = value.javaClass.methods.firstOrNull { it.name == getterName && it.parameterTypes.isEmpty() } ?: return@forEach
        val extracted = runCatching { extractOfficialMessageText(method.invoke(value)) }.getOrNull()
        if (!extracted.isNullOrBlank()) return extracted
    }
    val loweredMethods = value.javaClass.methods.filter { it.parameterTypes.isEmpty() }.sortedBy { it.name }
    loweredMethods.forEach { method ->
        val lowerName = method.name.lowercase(Locale.ROOT)
        if (!lowerName.contains("text") && !lowerName.contains("content") && !lowerName.contains("part")) return@forEach
        val extracted = runCatching { extractOfficialMessageText(method.invoke(value)) }.getOrNull()
        if (!extracted.isNullOrBlank()) return extracted
    }
    return value.toString().takeIf { it.isNotBlank() }
}

private fun closeQuietly(target: Any?) {
    if (target == null) return
    runCatching {
        when (target) {
            is AutoCloseable -> target.close()
            else -> target.javaClass.methods.firstOrNull {
                it.name == "close" && it.parameterTypes.isEmpty()
            }?.invoke(target)
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
