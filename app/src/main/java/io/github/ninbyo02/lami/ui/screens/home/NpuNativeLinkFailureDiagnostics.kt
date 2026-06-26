package io.github.ninbyo02.lami.ui.screens.home

internal const val NPU_STANDARD_ROUTE_NATIVE_LOAD_ORDER =
    "litertlm_jni>lami_npu_persistent_holder_stub"
internal const val NPU_STANDARD_ROUTE_NATIVE_LINK_FAILURE_REASON =
    "adapter_failure:UnsatisfiedLinkError"

internal data class NpuNativeLinkFailureDiagnostics(
    val detected: Boolean,
    val failedLibraryName: String,
    val javaLibraryPath: String,
    val supportedAbis: String,
    val loadOrder: String = NPU_STANDARD_ROUTE_NATIVE_LOAD_ORDER,
)

internal fun buildNpuNativeLinkFailureDiagnostics(
    throwable: Throwable,
    javaLibraryPath: String? = System.getProperty("java.library.path"),
    supportedAbis: List<String> = emptyList(),
    loadOrder: String = NPU_STANDARD_ROUTE_NATIVE_LOAD_ORDER,
): NpuNativeLinkFailureDiagnostics {
    val chain = npuThrowableChain(throwable)
    val searchText = chain.joinToString("\n") { cause ->
        listOfNotNull(cause.javaClass.name, cause.message).joinToString(":")
    }
    val detected = chain.any { it is UnsatisfiedLinkError } ||
        searchText.contains("UnsatisfiedLinkError", ignoreCase = true)
    return NpuNativeLinkFailureDiagnostics(
        detected = detected,
        failedLibraryName = if (detected) {
            extractNpuFailedNativeLibraryName(searchText).ifBlank { "unknown" }
        } else {
            "unavailable"
        },
        javaLibraryPath = javaLibraryPath?.takeIf { it.isNotBlank() } ?: "unknown",
        supportedAbis = supportedAbis.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "unknown",
        loadOrder = loadOrder,
    )
}

internal fun npuNativeLinkFailureDiagnosticsLines(
    diagnostics: NpuNativeLinkFailureDiagnostics,
): List<String> = listOf(
    "native_link_failure_detected=${diagnostics.detected}",
    "native_link_failure_library=${diagnostics.failedLibraryName}",
    "native_load_order=${diagnostics.loadOrder}",
    "java_library_path=${diagnostics.javaLibraryPath}",
    "supported_abis=${diagnostics.supportedAbis}",
)

internal fun npuNativeLinkFailureReason(
    throwable: Throwable,
): String {
    val diagnostics = buildNpuNativeLinkFailureDiagnostics(throwable)
    return if (diagnostics.detected) {
        NPU_STANDARD_ROUTE_NATIVE_LINK_FAILURE_REASON
    } else {
        throwable.message?.takeIf { it.isNotBlank() } ?: "dev_only_request_failed"
    }
}

private fun npuThrowableChain(throwable: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = throwable
    while (current != null && current !in chain && chain.size < 12) {
        chain += current
        current = current.cause
    }
    return chain
}

private fun extractNpuFailedNativeLibraryName(text: String): String {
    val patterns = listOf(
        Regex("no\\s+([^\"\\s/]+)\\s+in\\s+java\\.library\\.path", RegexOption.IGNORE_CASE),
        Regex("dlopen failed:\\s*library \"([^\"]+)\"", RegexOption.IGNORE_CASE),
        Regex("couldn't find \"([^\"]+)\"", RegexOption.IGNORE_CASE),
    )
    patterns.forEach { pattern ->
        pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
            return normalizeNpuLibraryName(name)
        }
    }
    return NPU_STANDARD_ROUTE_NATIVE_LOAD_ORDER
        .split(">")
        .firstOrNull { lib -> text.contains(lib, ignoreCase = true) }
        ?.let(::normalizeNpuLibraryName)
        .orEmpty()
}

private fun normalizeNpuLibraryName(name: String): String {
    val trimmed = name.trim()
    return when {
        trimmed.startsWith("lib") && trimmed.endsWith(".so") -> trimmed
        trimmed.endsWith(".so") -> "lib$trimmed"
        trimmed.startsWith("lib") -> "$trimmed.so"
        else -> "lib$trimmed.so"
    }
}
