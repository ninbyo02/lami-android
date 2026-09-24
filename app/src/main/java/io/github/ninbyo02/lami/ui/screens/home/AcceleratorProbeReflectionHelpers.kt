package io.github.ninbyo02.lami.ui.screens.home

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Locale

private val dispatchApiLibraryNames = setOf(
    "libLiteRtDispatch_Qualcomm.so",
    "libLiteRtDispatchQualcomm.so",
    "libLiteRtDispatch.so",
    "liblitert_dispatch_qualcomm.so",
    "liblitert_dispatch.so",
)

private val qnnRuntimeLibraryNames = setOf(
    "libQnnSystem.so",
    "libQnnHtp.so",
    "libQnnHtpPrepare.so",
    "libQnnGpu.so",
    "libQnnDsp.so",
)

private val htpSkelStubLibraryNames = setOf(
    "libQnnHtpV79Skel.so",
    "libQnnHtpV79Stub.so",
    "libQnnHtpV75Skel.so",
    "libQnnHtpV75Stub.so",
    "libQnnHtpV73Skel.so",
    "libQnnHtpV73Stub.so",
    "libQnnHtpV69Skel.so",
    "libQnnHtpV69Stub.so",
    "libQnnHtpV68Skel.so",
    "libQnnHtpV68Stub.so",
    "libQnnDspV66Skel.so",
    "libQnnDspV66Stub.so",
)

private val DELEGATE_KEYWORDS = listOf(
    "delegate",
    "backend",
    "gpu",
    "cpu",
    "nnapi",
    "npu",
    "accelerator",
    "acceleration",
    "preferred",
    "hardware",
    "qnn",
    "htp",
    "dsp",
    "hexagon",
    "neural",
    "qualcomm",
)

internal fun closeDisposeMethodRank(name: String): Int {
    return when (name.lowercase(Locale.US)) {
        "close" -> 0
        "dispose" -> 1
        "release" -> 2
        "destroy" -> 3
        "shutdown" -> 4
        else -> 5
    }
}

internal fun liteRtLmNpuApiInventoryClassNames(): List<String> {
    return listOf(
        "com.google.ai.edge.litertlm.Backend",
        "com.google.ai.edge.litertlm.Backend\$NPU",
        "com.google.ai.edge.litertlm.Backend\$GPU",
        "com.google.ai.edge.litertlm.Backend\$CPU",
        "com.google.ai.edge.litertlm.EngineConfig",
        "com.google.ai.edge.litertlm.Engine",
        "com.google.ai.edge.litertlm.LlmInference",
        "com.google.ai.edge.litertlm.LlmInferenceOptions",
        "com.google.ai.edge.litertlm.LlmInferenceOptions\$Builder",
        "com.google.mediapipe.tasks.genai.llminference.LlmInference",
        "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
        "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder",
    )
}

internal fun isAttachDryRunBackendSetterCandidate(method: Method): Boolean {
    if (method.isSynthetic || method.name.indexOf('$') >= 0) return false
    if (method.parameterTypes.size != 1) return false
    val lowerName = method.name.lowercase(Locale.US)
    return lowerName == "setbackend" ||
        lowerName == "setpreferredbackend" ||
        lowerName == "backend" ||
        lowerName == "preferredbackend" ||
        lowerName.contains("backend")
}

internal fun attachDryRunSetterRank(methodName: String): Int {
    return when (methodName) {
        "setBackend" -> 0
        "setPreferredBackend" -> 1
        "backend" -> 2
        "preferredBackend" -> 3
        else -> 4
    }
}

internal fun unwrapInvocationTarget(throwable: Throwable): Throwable {
    var current = throwable
    while (current is InvocationTargetException && current.targetException != null) {
        current = current.targetException
    }
    return current
}

internal fun throwableCauseChain(throwable: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = throwable
    while (current != null && chain.size < 8 && current !in chain) {
        chain += current
        current = if (current is InvocationTargetException && current.targetException != null) {
            current.targetException
        } else {
            current.cause
        }
    }
    return chain
}

internal fun matchesNpuLibraryKeyword(name: String): Boolean {
    return listOf(
        "qnn",
        "htp",
        "hexagon",
        "skel",
        "dispatch",
        "neuro",
        "mediatek",
        "mtk",
    ).any { keyword -> name.contains(keyword, ignoreCase = true) }
}

internal fun isDispatchApiLibraryCandidate(name: String): Boolean {
    if (!name.endsWith(".so")) return false
    val lower = name.lowercase(Locale.US)
    return dispatchApiLibraryNames.any { it.equals(name, ignoreCase = true) } ||
        "dispatch" in lower ||
        "litertdispatch" in lower ||
        (("qualcomm" in lower || "qnn" in lower) && "dispatch" in lower)
}

internal fun isQnnRuntimeLibraryCandidate(name: String): Boolean {
    return qnnRuntimeLibraryNames.any { it.equals(name, ignoreCase = true) }
}

internal fun isHtpSkelStubLibraryCandidate(name: String): Boolean {
    if (!name.endsWith(".so")) return false
    return htpSkelStubLibraryNames.any { it.equals(name, ignoreCase = true) } ||
        ((name.contains("Skel", ignoreCase = true) || name.contains("Stub", ignoreCase = true)) &&
            (name.contains("QnnHtp", ignoreCase = true) || name.contains("QnnDsp", ignoreCase = true)))
}

internal fun containsDelegateKeyword(name: String): Boolean {
    val lowerName = name.lowercase(Locale.US)
    return DELEGATE_KEYWORDS.any(lowerName::contains)
}

internal fun toLikelyFqcnVariants(candidate: String): List<String> {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty()) return emptyList()
    val suffix = trimmed.removePrefix("LlmInference.")
    if (suffix == trimmed) return emptyList()
    return listOf(
        "com.google.mediapipe.tasks.genai.llminference.LlmInference.$suffix",
        "com.google.mediapipe.tasks.genai.llminference.LlmInference\$${suffix.replace('.', '$')}",
    )
}

internal fun matchesPreferredBackendMethod(method: Method): Boolean {
    val name = method.name
    if (name == "setPreferredBackend") return true
    if (name.contains("preferredBackend", ignoreCase = true)) return true
    if (!name.contains("backend", ignoreCase = true)) return false
    return method.parameterTypes.any { parameterType ->
        parameterType.simpleName.contains("Backend", ignoreCase = true) ||
            parameterType.canonicalName.orEmpty().contains("Backend", ignoreCase = true)
    }
}

internal fun formatMethodSignature(clazz: Class<*>, method: Method): String {
    val params = method.parameterTypes.joinToString(", ") { it.simpleName.ifBlank { "Unknown" } }
    val returnType = method.returnType.simpleName.ifBlank { "Unknown" }
    return "${clazz.simpleName}.${method.name}($params): $returnType"
}

internal fun formatConstructorSignature(clazz: Class<*>, constructor: Constructor<*>): String {
    val params = constructor.parameterTypes.joinToString(", ") { type ->
        type.simpleName.ifBlank { type.name.substringAfterLast('.') }
    }
    return "${clazz.simpleName}($params)"
}
