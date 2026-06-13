package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal const val GALLERY_STACK_GPU_PROBE_DEFAULT_MODEL_PATH =
    "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm"
internal const val GALLERY_STACK_GPU_PROBE_MODEL_EXPECTED_NAME = "gemma-4-E2B-it.litertlm"
internal const val GALLERY_STACK_GPU_PROBE_MODEL_EXPECTED_SIZE_BYTES = 2_588_147_712L
internal const val GALLERY_STACK_GPU_PROBE_EDGE_LITERT_SHA256 =
    "1b27b3f8c107c9e9a4c9fcf8f9fe05d33b5bcc941fd5a6030d2d38cfba207aed"
internal const val GALLERY_STACK_GPU_PROBE_EDGE_LITERTLM_JNI_SHA256 =
    "49ca8596e404dab468cbaa493e571f9e26d210815dc95e6bab89c3ee6e9afbb6"
internal const val GALLERY_STACK_GPU_PROBE_ALLOWLIST_TOP_K = 64
internal const val GALLERY_STACK_GPU_PROBE_ALLOWLIST_TOP_P = "0.95"
internal const val GALLERY_STACK_GPU_PROBE_ALLOWLIST_TEMPERATURE = "1.0"
internal const val GALLERY_STACK_GPU_PROBE_ALLOWLIST_MAX_TOKENS = 4000
internal const val GALLERY_STACK_GPU_PROBE_ALLOWLIST_MAX_CONTEXT_LENGTH = 32000

data class GalleryStackGpuProbeRuntimeDiagnostics(
    val flavor: Boolean,
    val enabled: Boolean,
    val applicationId: String,
    val nativeStackSource: String,
    val libLiteRtSha256: String,
    val libLiteRtLmJniSha256: String,
    val libsManifestPresent: String,
    val edgeGalleryModelExpected: String,
    val modelPath: String,
    val modelExists: String,
    val modelSizeBytes: String,
    val modelSha256IfAvailable: String,
    val allowlistConfigApplied: String,
    val runtimeStackAlignmentLevel: String,
    val thinkingApiAvailable: String,
    val speculativeDecodingApiAvailable: String,
    val allowlistAccelerators: String,
    val allowlistVisionAccelerator: String,
    val allowlistTopK: String,
    val allowlistTopP: String,
    val allowlistTemperature: String,
    val allowlistMaxTokens: String,
    val allowlistMaxContextLength: String,
)

internal fun isGalleryStackGpuProbePropertyEnabled(): Boolean {
    if (!BuildConfig.DEBUG || !BuildConfig.GALLERY_STACK_GPU_PROBE) return false
    val localJvm = runCatching {
        System.getProperty("lami.gallery_stack_gpu_probe")?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (localJvm != null) return localJvm.toBooleanStrictOrNull() == true
    val env = runCatching {
        System.getenv("LAMI_GALLERY_STACK_GPU_PROBE")?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (env != null) return env.toBooleanStrictOrNull() == true
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        (method.invoke(null, "debug.lami.gallery_stack_gpu_probe", "") as? String)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toBooleanStrictOrNull() == true
    }.getOrDefault(false)
}

internal fun shouldApplyGalleryStackGpuProbeAllowlistConfig(preferredBackend: String): Boolean =
    BuildConfig.DEBUG &&
        BuildConfig.GALLERY_STACK_GPU_PROBE &&
        isGalleryStackGpuProbePropertyEnabled() &&
        preferredBackend.equals("GPU", ignoreCase = true)

internal fun buildGalleryStackGpuProbeRuntimeDiagnostics(
    selectedModelPath: String?,
    nativeLibraryDir: String? = null,
    preferredBackend: String = "unavailable",
): GalleryStackGpuProbeRuntimeDiagnostics {
    val modelPath = selectedModelPath
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "unknown" && it != "unavailable" }
        ?: GALLERY_STACK_GPU_PROBE_DEFAULT_MODEL_PATH
    val modelFile = File(modelPath)
    val modelExists = modelFile.isFile
    val modelSize = modelFile.takeIf { it.isFile }?.length()
    val nativeDir = nativeLibraryDir
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    val liteRtSha = nativeDir?.resolve("libLiteRt.so")?.takeIf { it.isFile }?.let(::sha256ForGalleryStackProbeFileSafely)
        ?: "unavailable"
    val liteRtLmJniSha = nativeDir?.resolve("liblitertlm_jni.so")?.takeIf { it.isFile }?.let(::sha256ForGalleryStackProbeFileSafely)
        ?: "unavailable"
    val nativeStackStaged =
        liteRtSha.equals(GALLERY_STACK_GPU_PROBE_EDGE_LITERT_SHA256, ignoreCase = true) &&
            liteRtLmJniSha.equals(GALLERY_STACK_GPU_PROBE_EDGE_LITERTLM_JNI_SHA256, ignoreCase = true)
    val modelMatchesExpectedSize = modelSize == GALLERY_STACK_GPU_PROBE_MODEL_EXPECTED_SIZE_BYTES
    val alignmentLevel = when {
        nativeStackStaged && modelMatchesExpectedSize -> "native_stack_and_model"
        nativeStackStaged -> "native_stack_staged"
        modelMatchesExpectedSize -> "model_only"
        BuildConfig.GALLERY_STACK_GPU_PROBE -> "none"
        else -> "unknown"
    }
    val allowlistApplied = shouldApplyGalleryStackGpuProbeAllowlistConfig(preferredBackend)
    return GalleryStackGpuProbeRuntimeDiagnostics(
        flavor = BuildConfig.GALLERY_STACK_GPU_PROBE,
        enabled = isGalleryStackGpuProbePropertyEnabled(),
        applicationId = BuildConfig.APPLICATION_ID,
        nativeStackSource = if (BuildConfig.GALLERY_STACK_GPU_PROBE) {
            BuildConfig.DISPATCH_RUNTIME_SOURCE
        } else {
            "not_gallery_stack_gpu_probe_flavor"
        },
        libLiteRtSha256 = liteRtSha,
        libLiteRtLmJniSha256 = liteRtLmJniSha,
        libsManifestPresent = "artifact_only_not_packaged",
        edgeGalleryModelExpected = "modelId=litert-community/gemma-4-E2B-it-litert-lm;commit=6e5c4f1e395deb959c494953478fa5cec4b8008f;size=$GALLERY_STACK_GPU_PROBE_MODEL_EXPECTED_SIZE_BYTES",
        modelPath = modelPath,
        modelExists = modelExists.toString(),
        modelSizeBytes = modelSize?.toString() ?: "unavailable",
        modelSha256IfAvailable = "script_only_not_computed_on_device",
        allowlistConfigApplied = allowlistApplied.toString(),
        runtimeStackAlignmentLevel = alignmentLevel,
        thinkingApiAvailable = "false",
        speculativeDecodingApiAvailable = "false",
        allowlistAccelerators = "gpu,cpu",
        allowlistVisionAccelerator = "gpu",
        allowlistTopK = GALLERY_STACK_GPU_PROBE_ALLOWLIST_TOP_K.toString(),
        allowlistTopP = GALLERY_STACK_GPU_PROBE_ALLOWLIST_TOP_P,
        allowlistTemperature = GALLERY_STACK_GPU_PROBE_ALLOWLIST_TEMPERATURE,
        allowlistMaxTokens = GALLERY_STACK_GPU_PROBE_ALLOWLIST_MAX_TOKENS.toString(),
        allowlistMaxContextLength = GALLERY_STACK_GPU_PROBE_ALLOWLIST_MAX_CONTEXT_LENGTH.toString(),
    )
}

private fun sha256ForGalleryStackProbeFileSafely(file: File): String =
    runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }
    }.getOrElse {
        "unavailable:${it.javaClass.simpleName}"
    }
