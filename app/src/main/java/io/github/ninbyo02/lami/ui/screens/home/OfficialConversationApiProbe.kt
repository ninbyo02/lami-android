package io.github.ninbyo02.lami.ui.screens.home

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
