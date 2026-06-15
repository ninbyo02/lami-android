package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Locale

internal data class GpuInternalSurfaceProbeDiagnostics(
    val emit: Boolean,
    val enabled: String,
    val result: String,
    val disabledReason: String,
    val runtimeConfigClassPresent: String,
    val backendConstraintClassPresent: String,
    val preferredEngineTypeClassPresent: String,
    val gpuOptionsClassPresent: String,
    val artisanClassPresent: String,
    val llmGpuArtisanExecutorSymbolPresent: String,
    val kvCacheSymbolPresent: String,
    val runtimeConfigMethods: String,
    val backendConstraintMethods: String,
    val gpuOptionsMethods: String,
    val exceptionClass: String,
    val exceptionMessage: String,
)

internal fun buildGpuInternalSurfaceProbeDiagnostics(
    preferredBackend: String,
    nativeLibraryDir: String? = null,
    propertyReader: (String) -> String? = ::readGpuInternalSurfaceProbeProperty,
    debugBuild: Boolean = BuildConfig.DEBUG,
    standardGpuMinimalRuntimeCandidateFlavor: Boolean = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR,
    applicationId: String = BuildConfig.APPLICATION_ID,
    classLoader: ClassLoader = GpuInternalSurfaceProbeDiagnostics::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader(),
): GpuInternalSurfaceProbeDiagnostics {
    val emit = debugBuild
    if (!emit) {
        return GpuInternalSurfaceProbeDiagnostics(
            emit = false,
            enabled = "false",
            result = "not_eligible",
            disabledReason = "not_debug_build",
            runtimeConfigClassPresent = "unavailable",
            backendConstraintClassPresent = "unavailable",
            preferredEngineTypeClassPresent = "unavailable",
            gpuOptionsClassPresent = "unavailable",
            artisanClassPresent = "unavailable",
            llmGpuArtisanExecutorSymbolPresent = "unavailable",
            kvCacheSymbolPresent = "unavailable",
            runtimeConfigMethods = "unavailable",
            backendConstraintMethods = "unavailable",
            gpuOptionsMethods = "unavailable",
            exceptionClass = "none",
            exceptionMessage = "none",
        )
    }

    val propertyEnabled = isGpuInternalSurfaceProbeEnabled(propertyReader)
    val appIdEligible = applicationId == GPU_INTERNAL_SURFACE_PROBE_APPLICATION_ID ||
        standardGpuMinimalRuntimeCandidateFlavor
    val gpuEligible = preferredBackend.equals(PreferredBackendDryRunSetting.GPU.name, ignoreCase = true)
    val disabledReason = when {
        !appIdEligible -> "not_gpustandardminimal_application"
        !gpuEligible -> "not_gpu_backend"
        !propertyEnabled -> "property_off"
        else -> "none"
    }
    if (disabledReason != "none") {
        return GpuInternalSurfaceProbeDiagnostics(
            emit = true,
            enabled = "false",
            result = if (propertyEnabled) "not_eligible" else "disabled",
            disabledReason = disabledReason,
            runtimeConfigClassPresent = "unavailable",
            backendConstraintClassPresent = "unavailable",
            preferredEngineTypeClassPresent = "unavailable",
            gpuOptionsClassPresent = "unavailable",
            artisanClassPresent = "unavailable",
            llmGpuArtisanExecutorSymbolPresent = "unavailable",
            kvCacheSymbolPresent = "unavailable",
            runtimeConfigMethods = "unavailable",
            backendConstraintMethods = "unavailable",
            gpuOptionsMethods = "unavailable",
            exceptionClass = "none",
            exceptionMessage = "none",
        )
    }

    val exceptions = mutableListOf<Throwable>()
    fun inspect(className: String): ReflectedClassSurface =
        inspectClassSurface(className, classLoader).also { surface ->
            surface.exception?.let(exceptions::add)
        }

    val runtimeConfig = inspect("com.google.ai.edge.litertlm.RuntimeConfig")
    val backendConstraint = inspect("com.google.ai.edge.litertlm.BackendConstraint")
    val preferredEngineType = inspect("com.google.ai.edge.litertlm.PreferredEngineType")
    val gpuOptions = inspect("com.google.ai.edge.litertlm.GpuOptions")
    val artisanSurfaces = listOf(
        "com.google.ai.edge.litertlm.LlmGpuArtisanExecutor",
        "com.google.ai.edge.litertlm.Backend\$GPU_ARTISAN",
        "com.google.ai.edge.litertlm.Backend\$GpuArtisan",
    ).map(::inspect)
    val nativeDir = nativeLibraryDir
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?: resolveGpuInternalProbeNativeLibraryDir()
    val llmGpuArtisanSymbol = scanNativeRuntimeSymbols(
        nativeDir = nativeDir,
        tokens = listOf("LlmGpuArtisanExecutor", "GPU_ARTISAN"),
    )
    val kvCacheSymbol = scanNativeRuntimeSymbols(
        nativeDir = nativeDir,
        tokens = listOf("tflite_gpu_kv_cache", "tflite_opencl_kv_cache"),
    )
    return GpuInternalSurfaceProbeDiagnostics(
        emit = true,
        enabled = "true",
        result = when {
            exceptions.isNotEmpty() -> "exception"
            llmGpuArtisanSymbol != "true" || kvCacheSymbol != "true" -> "completed_with_missing_symbols"
            else -> "completed"
        },
        disabledReason = "none",
        runtimeConfigClassPresent = runtimeConfig.present.toString(),
        backendConstraintClassPresent = backendConstraint.present.toString(),
        preferredEngineTypeClassPresent = preferredEngineType.present.toString(),
        gpuOptionsClassPresent = gpuOptions.present.toString(),
        artisanClassPresent = artisanSurfaces.any { it.present }.toString(),
        llmGpuArtisanExecutorSymbolPresent = llmGpuArtisanSymbol,
        kvCacheSymbolPresent = kvCacheSymbol,
        runtimeConfigMethods = runtimeConfig.methodSummary,
        backendConstraintMethods = backendConstraint.methodSummary,
        gpuOptionsMethods = gpuOptions.methodSummary,
        exceptionClass = exceptions.firstOrNull()?.javaClass?.name ?: "none",
        exceptionMessage = exceptions.firstOrNull()?.message?.sanitizeGpuInternalSurfaceProbeValue() ?: "none",
    )
}

private data class ReflectedClassSurface(
    val present: Boolean,
    val methodSummary: String,
    val exception: Throwable? = null,
)

private const val GPU_INTERNAL_SURFACE_PROBE_APPLICATION_ID =
    "io.github.ninbyo02.lami.gpustandardminimal"

private fun inspectClassSurface(
    className: String,
    classLoader: ClassLoader,
): ReflectedClassSurface =
    try {
        val clazz = Class.forName(className, false, classLoader)
        ReflectedClassSurface(
            present = true,
            methodSummary = summarizeClassMethods(clazz),
        )
    } catch (_: ClassNotFoundException) {
        ReflectedClassSurface(
            present = false,
            methodSummary = "class_absent",
        )
    } catch (_: NoClassDefFoundError) {
        ReflectedClassSurface(
            present = false,
            methodSummary = "class_absent",
        )
    } catch (throwable: Throwable) {
        ReflectedClassSurface(
            present = false,
            methodSummary = "probe_exception",
            exception = throwable,
        )
    }

private fun summarizeClassMethods(clazz: Class<*>): String {
    val methods = (clazz.declaredMethods.asSequence() + clazz.methods.asSequence())
        .map(Method::getName)
    val constructors = clazz.declaredConstructors.asSequence()
        .map(Constructor<*>::getParameterCount)
        .map { parameterCount -> "constructor/$parameterCount" }
    val summary = (methods + constructors)
        .distinct()
        .sorted()
        .joinToString(";")
    return summary.ifBlank { "present_no_methods" }.sanitizeGpuInternalSurfaceProbeValue(maxLength = 320)
}

private fun isGpuInternalSurfaceProbeEnabled(propertyReader: (String) -> String?): Boolean {
    val value = propertyReader("debug.lami.gpu_internal_surface_probe")
        ?: propertyReader("lami.gpu_internal_surface_probe")
        ?: return false
    return value.equals("true", ignoreCase = true) || value == "1"
}

private fun readGpuInternalSurfaceProbeProperty(key: String): String? {
    val localJvmKey = key.removePrefix("debug.")
    runCatching {
        System.getProperty(localJvmKey)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()?.let { return it }
    runCatching {
        System.getenv(localJvmKey.uppercase(Locale.US).replace('.', '_'))?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()?.let { return it }
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        (method.invoke(null, key, "") as? String)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun resolveGpuInternalProbeNativeLibraryDir(): File? =
    runCatching {
        System.getProperty("java.library.path")
            ?.split(File.pathSeparator)
            .orEmpty()
            .asSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .map(::File)
            .firstOrNull { dir ->
                dir.resolve("liblitertlm_jni.so").isFile || dir.resolve("libLiteRt.so").isFile
            }
    }.getOrNull()

private fun scanNativeRuntimeSymbols(
    nativeDir: File?,
    tokens: List<String>,
): String {
    if (nativeDir == null || !nativeDir.isDirectory) return "unavailable"
    val files = listOf("liblitertlm_jni.so", "libLiteRt.so", "libLiteRtClGlAccelerator.so")
        .map { nativeDir.resolve(it) }
        .filter { it.isFile }
    if (files.isEmpty()) return "unavailable"
    return files.any { file -> tokens.any { token -> fileContainsAsciiToken(file, token) } }.toString()
}

private fun fileContainsAsciiToken(file: File, token: String): Boolean =
    runCatching {
        val pattern = token.toByteArray(Charsets.UTF_8)
        if (pattern.isEmpty()) return@runCatching false
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024 + pattern.size)
            var carry = 0
            while (true) {
                val read = input.read(buffer, carry, buffer.size - carry)
                if (read <= 0) break
                val limit = carry + read
                if (indexOf(buffer, limit, pattern) >= 0) return@runCatching true
                carry = minOf(pattern.size - 1, limit)
                if (carry > 0) {
                    System.arraycopy(buffer, limit - carry, buffer, 0, carry)
                }
            }
        }
        false
    }.getOrDefault(false)

private fun indexOf(
    buffer: ByteArray,
    limit: Int,
    pattern: ByteArray,
): Int {
    if (pattern.size > limit) return -1
    for (index in 0..(limit - pattern.size)) {
        var matched = true
        for (patternIndex in pattern.indices) {
            if (buffer[index + patternIndex] != pattern[patternIndex]) {
                matched = false
                break
            }
        }
        if (matched) return index
    }
    return -1
}

private fun String.sanitizeGpuInternalSurfaceProbeValue(maxLength: Int = 240): String =
    replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s+"), "_")
        .ifBlank { "unavailable" }
        .let { value -> if (value.length <= maxLength) value else value.take(maxLength) + "...truncated" }
