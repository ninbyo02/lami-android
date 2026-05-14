package io.github.ninbyo02.lami.local

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.util.Locale

data class LocalInferenceFailureDiagnostics(
    val selectedModelFilename: String?,
    val isQualcommModelLikely: Boolean,
    val isSm8750ModelLikely: Boolean,
    val failureStage: String,
    val exceptionClass: String?,
    val exceptionMessage: String?,
    val rootCauseClass: String?,
    val rootCauseMessage: String?,
    val causeChainSummary: String,
    val stackTraceHead: String,
    val suppressedSummary: String,
    val unsatisfiedLinkErrorDetected: Boolean,
    val dlopenFailedDetected: Boolean,
    val noUsableDispatchRuntimeDetected: Boolean,
    val missingLibraryNames: List<String>,
    val dispatchApiMissingLikely: Boolean,
    val nativeLibraryDir: String?,
    val nativeLibraryDirExists: Boolean,
    val nativeLibraryFilesSummary: String,
    val dispatchApiCandidatesFound: List<String>,
    val qnnRuntimeCandidatesFound: List<String>,
    val htpSkelStubCandidatesFound: List<String>,
    val backendNpuConstructorAvailable: Boolean,
    val backendNpuStringConstructorAvailable: Boolean,
    val backendNpuNativeLibraryDirRequired: Boolean,
    val litertLmMainBackendNpuProofStatus: String,
    val selectedFallbackPath: String?,
)

fun buildLocalInferenceFailureDiagnostics(
    context: Context,
    stage: String,
    throwable: Throwable,
    selectedModelName: String?,
    selectedFallbackPath: String? = null,
): LocalInferenceFailureDiagnostics {
    val appContext = context.applicationContext ?: context
    val selectedModelFilename = selectedModelName
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val normalizedModel = selectedModelFilename.orEmpty().lowercase(Locale.US)
    val nativeLibraryDir = appContext.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }
    val nativeLibraryFiles = nativeLibraryDir
        ?.let(::File)
        ?.takeIf { runCatching { it.isDirectory }.getOrDefault(false) }
        ?.listFiles()
        ?.mapNotNull { file -> file.name.takeIf { name -> file.isFile && name.endsWith(".so") } }
        ?.distinct()
        ?.sorted()
        .orEmpty()
    val throwableText = buildThrowableSearchText(throwable)
    val lowerThrowableText = throwableText.lowercase(Locale.US)
    val missingLibraryNames = extractMissingLibraryNames(throwableText)
    val dispatchApiCandidates = nativeLibraryFiles.filter { name ->
        name.contains("dispatch", ignoreCase = true) ||
            name.contains("LiteRtDispatch", ignoreCase = true)
    }
    val qnnRuntimeCandidates = nativeLibraryFiles.filter { name ->
        name in qnnRuntimeLibraryNames ||
            name.contains("Qnn", ignoreCase = true)
    }
    val htpSkelStubCandidates = nativeLibraryFiles.filter { name ->
        (name.contains("Htp", ignoreCase = true) ||
            name.contains("Skel", ignoreCase = true) ||
            name.contains("Stub", ignoreCase = true)) &&
            name.endsWith(".so")
    }
    val backendNpu = inspectBackendNpuConstructors()
    val dispatchMissingByException = listOf(
        "dispatch",
        "no usable dispatch runtime found",
        "litertdispatch",
        "dispatch_delegate",
    ).any(lowerThrowableText::contains)
    val dispatchMissingByKnownLibrary = missingLibraryNames.any { name ->
        name.contains("dispatch", ignoreCase = true) ||
            name in dispatchLibraryNames
    }
    val dispatchApiMissingLikely = dispatchApiCandidates.isEmpty() ||
        dispatchMissingByException ||
        dispatchMissingByKnownLibrary
    val causeChain = causeChain(throwable)
    val root = causeChain.lastOrNull() ?: throwable
    return LocalInferenceFailureDiagnostics(
        selectedModelFilename = selectedModelFilename,
        isQualcommModelLikely = normalizedModel.contains("qualcomm"),
        isSm8750ModelLikely = normalizedModel.contains("sm8750"),
        failureStage = stage.ifBlank { "unknown" },
        exceptionClass = throwable.javaClass.name,
        exceptionMessage = sanitize(throwable.message, MAX_MESSAGE_TEXT),
        rootCauseClass = root.javaClass.name,
        rootCauseMessage = sanitize(root.message, MAX_MESSAGE_TEXT),
        causeChainSummary = causeChain.take(MAX_CAUSE_DEPTH).joinToString(" -> ") {
            val message = sanitize(it.message, 120)
            if (message == null) it.javaClass.simpleName else "${it.javaClass.simpleName}:$message"
        }.ifBlank { "none" },
        stackTraceHead = stackTraceHead(throwable),
        suppressedSummary = suppressedSummary(throwable),
        unsatisfiedLinkErrorDetected = causeChain.any { it is UnsatisfiedLinkError } ||
            lowerThrowableText.contains("unsatisfiedlinkerror"),
        dlopenFailedDetected = lowerThrowableText.contains("dlopen failed"),
        noUsableDispatchRuntimeDetected = lowerThrowableText.contains("no usable dispatch runtime found"),
        missingLibraryNames = missingLibraryNames,
        dispatchApiMissingLikely = dispatchApiMissingLikely,
        nativeLibraryDir = nativeLibraryDir,
        nativeLibraryDirExists = nativeLibraryDir?.let { runCatching { File(it).isDirectory }.getOrDefault(false) } == true,
        nativeLibraryFilesSummary = summarizeNativeLibraryFiles(nativeLibraryFiles),
        dispatchApiCandidatesFound = dispatchApiCandidates,
        qnnRuntimeCandidatesFound = qnnRuntimeCandidates,
        htpSkelStubCandidatesFound = htpSkelStubCandidates,
        backendNpuConstructorAvailable = backendNpu.noArgConstructorAvailable,
        backendNpuStringConstructorAvailable = backendNpu.stringConstructorAvailable,
        backendNpuNativeLibraryDirRequired = backendNpu.stringConstructorAvailable,
        litertLmMainBackendNpuProofStatus = probeLitertLmMainBackendNpuProofStatus(),
        selectedFallbackPath = selectedFallbackPath,
    )
}

fun formatLocalInferenceFailureDiagnosticsForDev(
    diagnostics: LocalInferenceFailureDiagnostics,
): String = buildString {
    appendLine("[Qualcomm model failure diagnostics]")
    appendLine("selected model filename=${diagnostics.selectedModelFilename ?: "unknown"}")
    appendLine("isQualcommModelLikely=${diagnostics.isQualcommModelLikely}")
    appendLine("isSm8750ModelLikely=${diagnostics.isSm8750ModelLikely}")
    appendLine("failure stage=${diagnostics.failureStage}")
    appendLine("exception class=${diagnostics.exceptionClass ?: "none"}")
    appendLine("exception message=${diagnostics.exceptionMessage ?: "none"}")
    appendLine("root cause class=${diagnostics.rootCauseClass ?: "none"}")
    appendLine("root cause message=${diagnostics.rootCauseMessage ?: "none"}")
    appendLine("cause chain summary=${diagnostics.causeChainSummary.ifBlank { "none" }}")
    appendLine("suppressed exceptions summary=${diagnostics.suppressedSummary.ifBlank { "none" }}")
    appendLine("UnsatisfiedLinkError detected=${diagnostics.unsatisfiedLinkErrorDetected}")
    appendLine("dlopen failed detected=${diagnostics.dlopenFailedDetected}")
    appendLine("No usable Dispatch runtime found=${diagnostics.noUsableDispatchRuntimeDetected}")
    appendLine("missing library names extracted=${diagnostics.missingLibraryNames.ifEmpty { listOf("none") }.joinToString(", ")}")
    appendLine("nativeLibraryDir=${diagnostics.nativeLibraryDir ?: "unknown"}")
    appendLine("nativeLibraryDir exists=${diagnostics.nativeLibraryDirExists}")
    appendLine("nativeLibraryDir files summary=${diagnostics.nativeLibraryFilesSummary}")
    appendLine("dispatch api candidate files found=${diagnostics.dispatchApiCandidatesFound.ifEmpty { listOf("none") }.joinToString(", ")}")
    appendLine("dispatch api missing=${diagnostics.dispatchApiMissingLikely}")
    appendLine("qnn runtime files found=${diagnostics.qnnRuntimeCandidatesFound.ifEmpty { listOf("none") }.joinToString(", ")}")
    appendLine("htp/skel/stub files found=${diagnostics.htpSkelStubCandidatesFound.ifEmpty { listOf("none") }.joinToString(", ")}")
    appendLine("backend npu constructor availability=${diagnostics.backendNpuConstructorAvailable}")
    appendLine("Backend.NPU(String) constructor availability=${diagnostics.backendNpuStringConstructorAvailable}")
    appendLine("NPU nativeLibraryDir required=${diagnostics.backendNpuNativeLibraryDirRequired}")
    appendLine("litert_lm_main backend=npu proof status=${diagnostics.litertLmMainBackendNpuProofStatus}")
    appendLine("selected fallback path=${diagnostics.selectedFallbackPath ?: "unknown"}")
    appendLine("stacktrace first 20 lines:")
    appendLine(diagnostics.stackTraceHead.ifBlank { "none" })
}.trimEnd()

fun buildLocalInferenceFailureDiagnosticsText(
    context: Context,
    stage: String,
    throwable: Throwable,
    selectedModelName: String?,
    selectedFallbackPath: String? = null,
): String = formatLocalInferenceFailureDiagnosticsForDev(
    buildLocalInferenceFailureDiagnostics(
        context = context,
        stage = stage,
        throwable = throwable,
        selectedModelName = selectedModelName,
        selectedFallbackPath = selectedFallbackPath,
    ),
)

private fun causeChain(throwable: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = unwrapInvocationTarget(throwable)
    while (current != null && chain.size < MAX_CAUSE_DEPTH && current !in chain) {
        chain += current
        current = unwrapInvocationTarget(current.cause)
    }
    return chain
}

private fun unwrapInvocationTarget(throwable: Throwable?): Throwable? {
    return if (throwable is InvocationTargetException && throwable.targetException != null) {
        throwable.targetException
    } else {
        throwable
    }
}

private fun buildThrowableSearchText(throwable: Throwable): String {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    val messages = causeChain(throwable).joinToString("\n") { cause ->
        listOfNotNull(cause.javaClass.name, cause.message).joinToString(":")
    }
    return "$messages\n${writer}"
}

private fun stackTraceHead(throwable: Throwable): String {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    return writer.toString()
        .lineSequence()
        .take(MAX_STACK_LINES)
        .joinToString("\n") { it.take(MAX_STACK_LINE_TEXT) }
}

private fun suppressedSummary(throwable: Throwable): String {
    return causeChain(throwable)
        .flatMap { cause -> cause.suppressed.map { cause to it } }
        .take(10)
        .joinToString(" | ") { (owner, suppressed) ->
            "${owner.javaClass.simpleName}->${suppressed.javaClass.simpleName}:${sanitize(suppressed.message, 120) ?: "none"}"
        }
}

private fun extractMissingLibraryNames(text: String): List<String> {
    val found = linkedSetOf<String>()
    val libraryNotFoundRegex = Regex("library\\s+\"([^\"]+\\.so)\"\\s+not\\s+found", RegexOption.IGNORE_CASE)
    libraryNotFoundRegex.findAll(text).forEach { match ->
        found += match.groupValues[1]
    }
    val soNameRegex = Regex("""[A-Za-z0-9_+.-]+\.so""")
    soNameRegex.findAll(text).forEach { match ->
        val name = match.value
        if (knownDiagnosticLibraryNames.any { it.equals(name, ignoreCase = true) }) {
            found += name
        }
    }
    val lower = text.lowercase(Locale.US)
    knownDiagnosticLibraryNames.forEach { name ->
        if (lower.contains(name.lowercase(Locale.US))) found += name
    }
    if (lower.contains("dispatch_api_so")) found += "dispatch_api_so"
    return found.toList()
}

private fun summarizeNativeLibraryFiles(files: List<String>): String {
    if (files.isEmpty()) return "none"
    val relevant = files.filter { name ->
        listOf("dispatch", "litert", "qualcomm", "qnn", "htp", "skel", "stub").any { keyword ->
            name.contains(keyword, ignoreCase = true)
        }
    }
    return "count=${files.size}; relevant=${relevant.ifEmpty { listOf("none") }.take(40).joinToString(", ")}"
}

private fun inspectBackendNpuConstructors(): BackendNpuConstructorDiagnostics {
    return runCatching {
        val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
        val npuClass = (backendClass.classes.asList() + backendClass.declaredClasses.asList())
            .firstOrNull { it.simpleName == "NPU" }
            ?: return BackendNpuConstructorDiagnostics()
        val constructors = npuClass.declaredConstructors.asList() + npuClass.constructors.asList()
        BackendNpuConstructorDiagnostics(
            noArgConstructorAvailable = constructors.any { it.parameterTypes.isEmpty() },
            stringConstructorAvailable = constructors.any { constructor ->
                constructor.parameterTypes.size == 1 && constructor.parameterTypes.first() == String::class.java
            },
        )
    }.getOrDefault(BackendNpuConstructorDiagnostics())
}

private fun probeLitertLmMainBackendNpuProofStatus(): String {
    val candidates = listOf(
        "/data/local/tmp/qairt/litert_lm_main",
        "/data/local/tmp/qairt/bin/litert_lm_main",
    )
    return candidates.firstOrNull { path ->
        runCatching {
            val file = File(path)
            file.exists() && file.canRead()
        }.getOrDefault(false)
    }?.let { "candidate-present:$it; backend=npu execution not run in app" }
        ?: "not-run; litert_lm_main not readable from app"
}

private fun sanitize(message: String?, maxLength: Int): String? {
    return message
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { if (it.length > maxLength) it.take(maxLength) + "..." else it }
}

private data class BackendNpuConstructorDiagnostics(
    val noArgConstructorAvailable: Boolean = false,
    val stringConstructorAvailable: Boolean = false,
)

private const val MAX_CAUSE_DEPTH = 10
private const val MAX_STACK_LINES = 20
private const val MAX_MESSAGE_TEXT = 500
private const val MAX_STACK_LINE_TEXT = 260

private val dispatchLibraryNames = setOf(
    "libLiteRtDispatch_Qualcomm.so",
    "libLiteRtDispatch.so",
    "dispatch_api_so",
)

private val qnnRuntimeLibraryNames = setOf(
    "libQnnHtp.so",
    "libQnnSystem.so",
    "libQnnHtpPrepare.so",
)

private val knownDiagnosticLibraryNames = dispatchLibraryNames + qnnRuntimeLibraryNames + setOf(
    "libQnnHtpV79Skel.so",
    "libQnnHtpV79Stub.so",
    "libQnnHtpV73Skel.so",
    "libQnnHtpV73Stub.so",
    "libQnnHtpV69Skel.so",
    "libQnnHtpV69Stub.so",
)
