package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import android.util.Log
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val GEMMA_4_E2B_VISION_MODEL_FILE_NAME = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
private const val GEMMA_4_E2B_VISION_MODEL_SIZE_BYTES = 3_016_294_400L
private const val GEMMA_4_E2B_VISION_MODEL_SHA256 =
    "41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e"
private const val GEMMA_4_E2B_GENERIC_VISION_MODEL_SIZE_BYTES = 2_588_147_712L
private const val GEMMA_4_E2B_GENERIC_VISION_MODEL_SHA256 =
    "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
private const val LOCAL_IMAGE_MAX_LONG_EDGE_PX = 1280
private const val LOCAL_IMAGE_JPEG_QUALITY = 85
private const val LOCAL_IMAGE_MAX_SOURCE_BYTES = 48L * 1024L * 1024L
private const val LOCAL_IMAGE_PERSISTENT_DIR = "chat_attachments"

internal sealed interface LocalImageInputCapability {
    data object NotChecked : LocalImageInputCapability
    data object Checking : LocalImageInputCapability
    data class Supported(
        val sha256: String,
        val visionBackend: LocalVisionBackend,
        val verifiedForNormalUse: Boolean,
        val note: String,
        val modelPath: String,
        val backendPlan: LocalImageBackendPlan,
    ) : LocalImageInputCapability

    data class Unsupported(
        val userMessage: String,
        val sha256: String? = null,
    ) : LocalImageInputCapability
}

internal enum class LocalVisionBackend {
    CPU,
    GPU,
}

internal data class LocalImageInput(
    val absolutePath: String,
    val savedUriString: String,
)

internal fun isKnownGemmaVisionModelFingerprint(
    sizeBytes: Long,
    sha256: String,
): Boolean =
    isKnownQualcommGemmaVisionModelFingerprint(sizeBytes, sha256) ||
        isKnownGenericGemmaVisionModelFingerprint(sizeBytes, sha256)

internal fun isKnownQualcommGemmaVisionModelFingerprint(
    sizeBytes: Long,
    sha256: String,
): Boolean =
    sizeBytes == GEMMA_4_E2B_VISION_MODEL_SIZE_BYTES &&
        sha256.equals(GEMMA_4_E2B_VISION_MODEL_SHA256, ignoreCase = true)

internal fun isKnownGenericGemmaVisionModelFingerprint(
    sizeBytes: Long,
    sha256: String,
): Boolean =
    sizeBytes == GEMMA_4_E2B_GENERIC_VISION_MODEL_SIZE_BYTES &&
        sha256.equals(GEMMA_4_E2B_GENERIC_VISION_MODEL_SHA256, ignoreCase = true)

@Suppress("UNUSED_PARAMETER")
internal suspend fun resolveLocalImageInputCapability(
    selectedModelPath: String?,
    selectedModelDisplayName: String?,
    allowUnverifiedGenericVision: Boolean = false,
    preferredBackend: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuRouteAvailable: Boolean = true,
): LocalImageInputCapability = withContext(Dispatchers.IO) {
    val modelPath = selectedModelPath?.trim().orEmpty()
    if (modelPath.isBlank()) {
        return@withContext LocalImageInputCapability.Unsupported("ローカル画像入力には画像対応モデルが必要です")
    }
    val file = File(modelPath)
    if (!file.isFile || !file.canRead()) {
        return@withContext LocalImageInputCapability.Unsupported("選択中のローカルモデルを読み取れません")
    }
    val size = file.length()
    val sha256 = try {
        LocalVisionModelSha256Cache.sha256(file)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        return@withContext LocalImageInputCapability.Unsupported("選択中のローカルモデルを読み取れません")
    }
    val kind = when {
        isKnownGenericGemmaVisionModelFingerprint(size, sha256) -> LocalImageModelKind.GENERIC
        isKnownQualcommGemmaVisionModelFingerprint(size, sha256) -> LocalImageModelKind.QUALCOMM_SM8750
        else -> return@withContext LocalImageInputCapability.Unsupported(
            "このローカルモデルは確認済みの画像対応Gemmaではありません", sha256,
        )
    }
    val plan = localImageBackendPlan(kind, preferredBackend)
        ?: return@withContext LocalImageInputCapability.Unsupported(
            if (kind == LocalImageModelKind.GENERIC) {
                "NPU画像入力にはSM8750版Gemmaモデルを選択してください"
            } else {
                "CPU/GPU画像入力には汎用版Gemma画像モデルを選択してください"
            },
            sha256,
        )
    if (plan.languageBackend == LocalImageLanguageBackend.NPU && !npuRouteAvailable) {
        return@withContext LocalImageInputCapability.Unsupported("NPU画像入力の実行経路を有効にしてください", sha256)
    }
    if (!allowUnverifiedGenericVision) {
        return@withContext LocalImageInputCapability.Unsupported(
            "このGemma画像モデルは未実機検証です。Developer accessを有効にすると試験送信できます", sha256,
        )
    }
    LocalImageInputCapability.Supported(
        sha256 = sha256,
        visionBackend = plan.visionBackend,
        verifiedForNormalUse = false,
        note = "gemma-vision-dev-only-language=${plan.languageBackend}-vision=${plan.visionBackend}",
        modelPath = file.absolutePath,
        backendPlan = plan,
    )
}

internal suspend fun prepareLocalImageInputs(
    context: Context,
    attachmentUris: List<Uri>,
    persistent: Boolean = false,
): List<LocalImageInput> {
    val prepared = mutableListOf<File>()
    return try {
        withContext(Dispatchers.IO) {
            require(attachmentUris.size <= NpuImageInputPolicy.MAX_IMAGES) { "too many attachment images" }
            val attachmentsDir = if (persistent) {
                File(context.filesDir, LOCAL_IMAGE_PERSISTENT_DIR)
            } else {
                File(context.cacheDir, "attachments")
            }.apply { mkdirs() }
            attachmentUris.map { attachmentUri ->
                val ownerContext = currentCoroutineContext()
                ownerContext.ensureActive()
                val bytes = readImageBytesForLocalInput(context, attachmentUri) { ownerContext.ensureActive() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "attachment decode failed" }
                val longEdge = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sampleSize = 1
                while (longEdge / sampleSize > LOCAL_IMAGE_MAX_LONG_EDGE_PX) sampleSize *= 2
                val bitmap = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                ) ?: error("attachment decode failed")
                var normalizedBitmap: Bitmap? = null
                val jpegBytes = try {
                    normalizedBitmap = normalizeBitmapForLocalImageInput(bitmap, bytes)
                    ByteArrayOutputStream().use { output ->
                        check(normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, LOCAL_IMAGE_JPEG_QUALITY, output))
                        output.toByteArray()
                    }
                } finally {
                    normalizedBitmap?.takeIf { it !== bitmap }?.recycle()
                    bitmap.recycle()
                }
                ownerContext.ensureActive()
                val attachmentFile = File.createTempFile("local_att_", ".jpg", attachmentsDir)
                prepared += attachmentFile
                attachmentFile.writeBytes(jpegBytes)
                ownerContext.ensureActive()
                Log.d("LocalImageInput", "Prepared local image input path=${attachmentFile.absolutePath} bytes=${jpegBytes.size}")
                LocalImageInput(attachmentFile.absolutePath, attachmentFile.toURI().toString())
            }
        }
    } catch (failure: Throwable) {
        // Also handles cancellation while dispatching the completed IO result.
        prepared.forEach(File::delete)
        throw failure
    }
}

private fun readImageBytesForLocalInput(
    context: Context,
    attachmentUri: Uri,
    checkCancellation: () -> Unit,
): ByteArray {
    val declaredLength = runCatching {
        context.contentResolver.openAssetFileDescriptor(attachmentUri, "r")?.use { descriptor ->
            descriptor.length
        } ?: -1L
    }.getOrDefault(-1L)
    require(declaredLength <= 0L || declaredLength <= LOCAL_IMAGE_MAX_SOURCE_BYTES) {
        "attachment too large"
    }
    val output = ByteArrayOutputStream()
    context.contentResolver.openInputStream(attachmentUri)?.use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            checkCancellation()
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= LOCAL_IMAGE_MAX_SOURCE_BYTES) { "attachment too large" }
            output.write(buffer, 0, read)
        }
    } ?: error("attachment read failed")
    return output.toByteArray()
}

internal fun deleteUnpersistedLocalImageInputs(inputs: List<LocalImageInput>, persistenceStarted: Boolean): Int {
    if (persistenceStarted) return 0
    return inputs.count { input ->
        !runCatching { File(input.absolutePath).let { !it.exists() || it.delete() } }.getOrDefault(false)
    }
}

private fun normalizeBitmapForLocalImageInput(
    bitmap: Bitmap,
    sourceBytes: ByteArray,
): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(sourceBytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.preScale(-1f, 1f)
            matrix.postRotate(90f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.preScale(-1f, 1f)
            matrix.postRotate(270f)
        }
    }
    val oriented = if (!matrix.isIdentity) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
    if (!oriented.hasAlpha()) return oriented
    var flattened: Bitmap? = null
    try {
        val result = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        flattened = result
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(oriented, 0f, 0f, null)
        }
        return result
    } catch (failure: Throwable) {
        flattened?.recycle()
        throw failure
    } finally {
        if (oriented !== bitmap) oriented.recycle()
    }
}

private object LocalVisionModelSha256Cache {
    private data class Key(
        val path: String,
        val length: Long,
        val lastModified: Long,
    )

    private val cache = linkedMapOf<Key, String>()

    suspend fun sha256(file: File): String {
        val key = Key(
            path = file.absolutePath,
            length = file.length(),
            lastModified = file.lastModified(),
        )
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val value = digest.digest().joinToString("") { "%02x".format(it) }
        synchronized(cache) {
            cache[key] = value
            while (cache.size > 4) {
                val first = cache.keys.firstOrNull() ?: break
                cache.remove(first)
            }
        }
        return value
    }
}
