package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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

private val OFFICIAL_TEXT_CANDIDATES = listOf(
    "text",
    "getText",
    "content",
    "getContent",
    "result",
    "getResult",
    "token",
    "getToken",
    "parts",
    "getParts",
    "toString",
)

internal suspend fun tryRunOfficialLiteRtFlowStreaming(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
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
                cacheDirPath = cacheDirPath,
                startElapsedMs = startElapsedMs,
                onPartial = onPartial,
                appendTrace = appendTrace,
            )
        }.onFailure { throwable ->
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_exception"
            fallbackReasonReported = true
            runCatching { onFallbackReason(reasonCode) }
            safeAppendTrace(
                appendTrace = appendTrace,
                message = "UPSTREAM official-flow fallback reason=$reasonCode namespace=${spec.namespace}, error=${throwable.javaClass.simpleName}:${throwable.message}",
            )
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
    cacheDirPath: String,
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
                cacheDirPath = cacheDirPath,
                appendTrace = appendTrace,
            )
        }.onFailure { throwable ->
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_blocking_exception"
            runCatching { onFallbackReason(reasonCode) }
            safeAppendTrace(
                appendTrace = appendTrace,
                message = "UPSTREAM official-blocking fallback reason=$reasonCode namespace=${spec.namespace}, error=${throwable.javaClass.simpleName}:${throwable.message}",
            )
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
    cacheDirPath: String,
    startElapsedMs: Long,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit,
): LocalOfficialFlowStreamingResult? {
    if (spec.namespace == "com.google.ai.edge.litertlm") {
        return runOfficialLiteRtLmDirect(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            startElapsedMs = startElapsedMs,
            onPartial = onPartial,
            appendTrace = appendTrace,
        )
    }
    safeAppendTrace(appendTrace, "UPSTREAM official-flow start namespace=${spec.namespace}")
    val engineClass = runCatching { Class.forName(spec.engineClassName) }.getOrNull() ?: return null
    val conversationClass = runCatching { Class.forName("${spec.namespace}.Conversation") }.getOrNull() ?: return null
    val sendMessageAsyncMethod = if (spec.namespace == "com.google.ai.edge.litertlm") {
        conversationClass.methods.firstOrNull { method ->
            method.name == "sendMessageAsync" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Map::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                Flow::class.java.isAssignableFrom(method.returnType)
        }?.also {
            safeAppendTrace(appendTrace, "UPSTREAM official-flow selectedMethod=sendMessageAsync(String,Map) namespace=${spec.namespace}")
        }
    } else {
        conversationClass.methods.firstOrNull { it.name == "sendMessageAsync" && it.parameterTypes.size == 1 }
    } ?: throw OfficialFlowFallbackException("send_message_async_missing")
    val createConversationMethod =
        engineClass.methods.firstOrNull { it.name == "createConversation" }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
    val engine = if (spec.namespace == "com.google.ai.edge.litertlm") {
        createOfficialLiteRtLmEngineInstance(modelPath = modelPath, appendTrace = appendTrace)
    } else {
        createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath)
    }
        ?: throw OfficialFlowFallbackException("conversation_create_failed")
    var conversation: Any? = null
    try {
        conversation = runCatching {
            if (spec.namespace == "com.google.ai.edge.litertlm") {
                createOfficialLiteRtLmConversation(
                    engine = engine,
                    engineClass = engineClass,
                    appendTrace = appendTrace,
                )
            } else {
                createOfficialConversation(
                    engine = engine,
                    createConversationMethod = createConversationMethod,
                    appendTrace = appendTrace,
                )
            }
        }.getOrElse { throwable ->
            throw OfficialFlowFallbackException("conversation_create_failed", throwable)
        } ?: throw OfficialFlowFallbackException("conversation_create_failed")
        val flowValue = if (spec.namespace == "com.google.ai.edge.litertlm") {
            safeAppendTrace(appendTrace, "UPSTREAM official-flow invoke promptLength=${prompt.length} mapSize=0")
            runCatching {
                sendMessageAsyncMethod.invoke(conversation, prompt, emptyMap<String, Any>())
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_async_missing", throwable)
            }
        } else {
            val sendArgument =
                buildSendMessageArgument(
                    parameterType = sendMessageAsyncMethod.parameterTypes.first(),
                    namespace = spec.namespace,
                    prompt = prompt,
                ) ?: throw OfficialFlowFallbackException("send_message_async_missing")
            runCatching {
                sendMessageAsyncMethod.invoke(conversation, sendArgument)
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_async_missing", throwable)
            }
        }
        safeAppendTrace(appendTrace, "UPSTREAM official-flow flowClass=${flowValue?.javaClass?.name ?: "null"}")
        val flow = flowValue as? Flow<*> ?: throw OfficialFlowFallbackException("send_message_async_missing")
        val builder = StringBuilder()
        var partialCount = 0
        var extractFailureCount = 0
        var firstPartialMs: Long? = null
        var lastPartial: String? = null
        runCatching {
            flow.collect { message ->
                safeAppendTrace(
                    appendTrace = appendTrace,
                    message = "UPSTREAM official-flow chunkClass=${message?.javaClass?.name ?: "null"}",
                )
                val extractedText = extractOfficialMessageTextWithTrace(
                    path = "official-flow",
                    value = message,
                    appendTrace = appendTrace,
                )
                val extracted = extractedText?.trim().orEmpty()
                if (extracted.isBlank()) {
                    extractFailureCount += 1
                    return@collect
                }
                if (extracted.isBlank() || extracted == lastPartial) return@collect
                lastPartial = extracted
                builder.append(extracted)
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
        if (partialCount <= 0) {
            throw OfficialFlowFallbackException(if (extractFailureCount > 0) "message_extract_failed" else "no_partial_emitted")
        }
        val response = builder.toString().trim()
        if (response.isBlank()) throw OfficialFlowFallbackException("blank_response")
        safeAppendTrace(appendTrace, "UPSTREAM official-flow extracted length=${response.length} partialCount=$partialCount namespace=${spec.namespace}")
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
    cacheDirPath: String,
    appendTrace: (String) -> Unit,
): String? {
    if (spec.namespace == "com.google.ai.edge.litertlm") {
        return runOfficialLiteRtLmBlocking(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            appendTrace = appendTrace,
        )
    }
    safeAppendTrace(appendTrace, "UPSTREAM official-blocking start namespace=${spec.namespace}")
    val engineClass = runCatching { Class.forName(spec.engineClassName) }.getOrNull() ?: return null
    val conversationClass = runCatching { Class.forName("${spec.namespace}.Conversation") }.getOrNull() ?: return null
    val sendMethod = if (spec.namespace == "com.google.ai.edge.litertlm") {
        conversationClass.methods.firstOrNull { method ->
            method.name == "sendMessage" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Map::class.java.isAssignableFrom(method.parameterTypes[1])
        }?.also {
            safeAppendTrace(appendTrace, "UPSTREAM official-blocking selectedMethod=sendMessage(String,Map) namespace=${spec.namespace}")
        }
    } else {
        conversationClass.methods.firstOrNull { method ->
            (method.name == "sendMessage" || method.name == "generateResponse") && method.parameterTypes.size == 1
        }
    } ?: return null
    val createConversationMethod =
        engineClass.methods.firstOrNull { it.name == "createConversation" }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
    val engine = if (spec.namespace == "com.google.ai.edge.litertlm") {
        createOfficialLiteRtLmEngineInstance(modelPath = modelPath, appendTrace = appendTrace)
    } else {
        createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath)
    }
        ?: throw OfficialFlowFallbackException("conversation_create_failed")
    var conversation: Any? = null
    try {
        conversation = if (spec.namespace == "com.google.ai.edge.litertlm") {
            createOfficialLiteRtLmConversation(
                engine = engine,
                engineClass = engineClass,
                appendTrace = appendTrace,
            )
        } else {
            createOfficialConversation(
                engine = engine,
                createConversationMethod = createConversationMethod,
                appendTrace = appendTrace,
            )
        }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
        val responseValue = if (spec.namespace == "com.google.ai.edge.litertlm") {
            safeAppendTrace(appendTrace, "UPSTREAM official-blocking invoke promptLength=${prompt.length} mapSize=0")
            runCatching {
                sendMethod.invoke(conversation, prompt, emptyMap<String, Any>())
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_missing", throwable)
            }
        } else {
            val sendArgument = buildSendMessageArgument(
                parameterType = sendMethod.parameterTypes.first(),
                namespace = spec.namespace,
                prompt = prompt,
            ) ?: throw OfficialFlowFallbackException("send_message_missing")
            runCatching {
                sendMethod.invoke(conversation, sendArgument)
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_missing", throwable)
            }
        }
        safeAppendTrace(appendTrace, "UPSTREAM official-blocking returnClass=${responseValue?.javaClass?.name ?: "null"}")
        val responseText = extractOfficialMessageTextWithTrace(
            path = "official-blocking",
            value = responseValue,
            appendTrace = appendTrace,
        )?.trim()?.takeIf { it.isNotBlank() } ?: throw OfficialFlowFallbackException("message_extract_failed")
        safeAppendTrace(appendTrace, "UPSTREAM official-blocking success responseLength=${responseText.length}")
        return responseText
    } finally {
        closeQuietly(conversation)
        closeQuietly(engine)
    }
}

private suspend fun runOfficialLiteRtLmDirect(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    startElapsedMs: Long,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit,
): LocalOfficialFlowStreamingResult? {
    safeAppendTrace(appendTrace, "UPSTREAM official-direct flowStart")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct backend=GPU")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct cacheDirPresent=${cacheDirPath.isNotBlank()}")

    return runCatching {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
            audioBackend = Backend.GPU(),
            maxNumTokens = null,
            cacheDir = cacheDirPath,
        )
        val engine = Engine(engineConfig)

        safeAppendTrace(appendTrace, "UPSTREAM official-direct engineCreated")
        engine.initialize()
        safeAppendTrace(appendTrace, "UPSTREAM official-direct engineInitialized")

        val conversation = engine.createConversation()
        safeAppendTrace(appendTrace, "UPSTREAM official-direct conversationCreated")

        var lastText: String? = null
        var partialCount = 0
        var firstPartialMs: Long? = null
        conversation.sendMessageAsync(prompt).collect { message ->
            val extractedText = message.contents.toString().trim()
                .ifBlank { message.toString().trim() }
            if (extractedText.isNotBlank()) {
                if (firstPartialMs == null) {
                    firstPartialMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
                }
                lastText = extractedText
                partialCount += 1
                onPartial(extractedText)
            }
        }

        safeAppendTrace(appendTrace, "UPSTREAM official-direct resultLength=${lastText?.length ?: -1}")
        val response = lastText?.takeIf { it.isNotBlank() } ?: throw OfficialFlowFallbackException("blank_response")

        LocalOfficialFlowStreamingResult(
            response = response,
            partialCount = partialCount,
            firstNonEmptyPartialElapsedRealtimeMs = firstPartialMs,
        )
    }.getOrElse { throwable ->
        safeAppendTrace(
            appendTrace,
            "UPSTREAM official-direct failed ${throwable.javaClass.simpleName}:${throwable.message}",
        )
        null
    }
}

private fun runOfficialLiteRtLmBlocking(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    appendTrace: (String) -> Unit,
): String? {
    safeAppendTrace(appendTrace, "UPSTREAM official-direct blockingStart")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct backend=GPU")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct cacheDirPresent=${cacheDirPath.isNotBlank()}")

    return runCatching {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
            audioBackend = Backend.GPU(),
            maxNumTokens = null,
            cacheDir = cacheDirPath,
        )
        val engine = Engine(engineConfig)
        safeAppendTrace(appendTrace, "UPSTREAM official-direct engineCreated")
        engine.initialize()
        safeAppendTrace(appendTrace, "UPSTREAM official-direct engineInitialized")

        val conversation = engine.createConversation()
        safeAppendTrace(appendTrace, "UPSTREAM official-direct conversationCreated")

        val message = conversation.sendMessage(prompt)
        val response = message.contents.toString().trim()
            .ifBlank { message.toString().trim() }

        safeAppendTrace(appendTrace, "UPSTREAM official-direct resultLength=${response.length}")

        response.takeIf { it.isNotBlank() }
    }.getOrElse {
        safeAppendTrace(appendTrace, "UPSTREAM official-direct failed ${it.javaClass.simpleName}:${it.message}")
        null
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

private fun createOfficialLiteRtLmEngineInstance(
    modelPath: String,
    appendTrace: (String) -> Unit,
): Any? {
    val engineConfig = runCatching {
        val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
        val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
        val backendValues = backendClass.enumConstants.orEmpty()
        val backend = backendValues.firstOrNull {
            (it as? Enum<*>)?.name == "CPU"
        } ?: backendValues.firstOrNull {
            (it as? Enum<*>)?.name == "CPU_BACKEND"
        } ?: backendValues.firstOrNull() ?: return@runCatching null
        val constructor = engineConfigClass.constructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == 6 &&
                ctor.parameterTypes[0] == String::class.java &&
                ctor.parameterTypes[1] == backendClass &&
                ctor.parameterTypes[2] == backendClass &&
                ctor.parameterTypes[3] == backendClass
        } ?: return@runCatching null
        constructor.newInstance(modelPath, backend, backend, backend, null, null)
    }.getOrElse { throwable ->
        safeAppendTrace(appendTrace, "UPSTREAM official-engine-config create failed ${throwable.javaClass.simpleName}:${throwable.message}")
        null
    } ?: return null
    return runCatching {
        val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
        val constructor = engineClass.constructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == 1 &&
                ctor.parameterTypes[0].name == "com.google.ai.edge.litertlm.EngineConfig"
        } ?: return@runCatching null
        constructor.newInstance(engineConfig)
    }.getOrElse { throwable ->
        safeAppendTrace(appendTrace, "UPSTREAM official-engine create failed ${throwable.javaClass.simpleName}:${throwable.message}")
        null
    }
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

private fun createOfficialLiteRtLmConversation(
    engine: Any,
    engineClass: Class<*>,
    appendTrace: (String) -> Unit,
): Any? {
    val configClassName = "com.google.ai.edge.litertlm.ConversationConfig"
    safeAppendTrace(appendTrace, "UPSTREAM official-conversation configClass=$configClassName")
    return runCatching {
        val configClass = Class.forName(configClassName)
        val config = configClass.getDeclaredConstructor().newInstance()
        safeAppendTrace(appendTrace, "UPSTREAM official-conversation configCreated class=${config.javaClass.name}")
        val createConversationMethod = engineClass.methods.first { method ->
            method.name == "createConversation" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == configClassName
        }
        val conversation = createConversationMethod.invoke(engine, config)
        safeAppendTrace(appendTrace, "UPSTREAM official-conversation created class=${conversation?.javaClass?.name ?: "null"}")
        conversation
    }.getOrElse { throwable ->
        safeAppendTrace(appendTrace, "UPSTREAM official-conversation create failed ${throwable.javaClass.simpleName}:${throwable.message}")
        null
    }
}

private fun createOfficialConversation(
    engine: Any,
    createConversationMethod: Method,
    appendTrace: (String) -> Unit,
): Any? {
    safeAppendTrace(
        appendTrace,
        "UPSTREAM official-conversation createMethod=${createConversationMethod.name}${createConversationMethod.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }}",
    )
    val conversation = when (createConversationMethod.parameterTypes.size) {
        0 -> runCatching { createConversationMethod.invoke(engine) }.getOrNull()
        else -> {
            val arg = createConversationMethod.parameterTypes.firstNotNullOfOrNull { parameterType ->
                buildEmptyByBuilder(parameterType)
            } ?: return null
            runCatching { createConversationMethod.invoke(engine, arg) }.getOrNull()
        }
    }
    safeAppendTrace(
        appendTrace,
        "UPSTREAM official-conversation created class=${conversation?.javaClass?.name ?: "null"}",
    )
    return conversation
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
    val getterNames = OFFICIAL_TEXT_CANDIDATES.filterNot { it == "toString" }
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
    val toStringValue = value.toString()
    return toStringValue.takeIf { isMeaningfulToStringFallback(value, it) }
}

private fun extractOfficialMessageTextWithTrace(
    path: String,
    value: Any?,
    appendTrace: (String) -> Unit,
): String? {
    safeAppendTrace(appendTrace, "UPSTREAM $path returnClass=${value?.javaClass?.name ?: "null"}")
    safeAppendTrace(appendTrace, "UPSTREAM extract-text path=$path candidates=$OFFICIAL_TEXT_CANDIDATES")
    OFFICIAL_TEXT_CANDIDATES.forEach { candidate ->
        val candidateValue = when (candidate) {
            "toString" -> value?.toString()
            else -> value?.javaClass?.methods?.firstOrNull {
                it.name == candidate && it.parameterTypes.isEmpty()
            }?.let { method ->
                runCatching { method.invoke(value) }.getOrNull()
            }
        }
        val extracted = runCatching { extractOfficialMessageText(candidateValue) }.getOrNull()?.trim()
        if (!extracted.isNullOrBlank()) {
            if (candidate == "toString" && value != null && !isMeaningfulToStringFallback(value, extracted)) {
                safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=blank")
                return@forEach
            }
            safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=nonBlank length=${extracted.length}")
            safeAppendTrace(appendTrace, "UPSTREAM $path extracted length=${extracted.length}")
            return extracted
        }
        val resultLabel = if (candidateValue == null) "null" else "blank"
        safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=$resultLabel")
    }
    safeAppendTrace(appendTrace, "UPSTREAM $path extracted length=0")
    return null
}

private fun isMeaningfulToStringFallback(source: Any, value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false
    if (trimmed == source.javaClass.name || trimmed == source.javaClass.simpleName) return false
    val identityPattern = "^${Regex.escape(source.javaClass.name)}@[0-9a-fA-F]+$".toRegex()
    return !identityPattern.matches(trimmed)
}

private fun safeAppendTrace(
    appendTrace: (String) -> Unit,
    message: String,
) {
    runCatching { appendTrace(message) }
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
