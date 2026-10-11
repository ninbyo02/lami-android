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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

internal sealed interface LocalImageInputCapability {
    data object NotChecked : LocalImageInputCapability
    data object Checking : LocalImageInputCapability
    data class Supported(
        val sha256: String,
        val visionBackend: LocalVisionBackend,
        val verifiedForNormalUse: Boolean,
        val note: String,
    ) : LocalImageInputCapability

    data class Unsupported(
        val userMessage: String,
        val sha256: String? = null,
    ) : LocalImageInputCapability
}

internal enum class LocalVisionBackend {
    CPU,
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
): LocalImageInputCapability = withContext(Dispatchers.IO) {
    val modelPath = selectedModelPath?.trim().orEmpty()
    if (modelPath.isBlank()) {
        return@withContext LocalImageInputCapability.Unsupported("ローカル画像入力には画像対応モデルが必要です")
    }
    val file = File(modelPath)
    if (!file.isFile) {
        return@withContext LocalImageInputCapability.Unsupported("選択中のローカルモデルを読み取れません")
    }
    val size = file.length()
    val sha256 = LocalVisionModelSha256Cache.sha256(file)
    if (isKnownGenericGemmaVisionModelFingerprint(sizeBytes = size, sha256 = sha256)) {
        if (!allowUnverifiedGenericVision) {
            return@withContext LocalImageInputCapability.Unsupported(
                userMessage = "このGemma画像モデルは未実機検証です。Developer accessを有効にすると試験送信できます",
                sha256 = sha256,
            )
        }
        return@withContext LocalImageInputCapability.Supported(
            sha256 = sha256,
            visionBackend = LocalVisionBackend.CPU,
            verifiedForNormalUse = false,
            note = "generic-gemma-vision-dev-only-cpu-vision-backend",
        )
    }
    if (!isKnownGemmaVisionModelFingerprint(sizeBytes = size, sha256 = sha256)) {
        return@withContext LocalImageInputCapability.Unsupported(
            userMessage = "このローカルモデルは確認済みの画像対応Gemmaではありません",
            sha256 = sha256,
        )
    }
    if (hasKnownQnnCompiledVisionConstraint(sha256 = sha256)) {
        return@withContext LocalImageInputCapability.Unsupported(
            userMessage = "このGemma画像モデルはQualcomm SM8750/QNN向けです。現在のビルドではvision backend未検証のため送信できません",
            sha256 = sha256,
        )
    }
    LocalImageInputCapability.Supported(
        sha256 = sha256,
        visionBackend = LocalVisionBackend.CPU,
        verifiedForNormalUse = true,
        note = "verified",
    )
}

internal suspend fun prepareLocalImageInputs(
    context: Context,
    attachmentUris: List<Uri>,
): List<LocalImageInput> = withContext(Dispatchers.IO) {
    val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
    attachmentUris.mapIndexed { index, attachmentUri ->
        val bytes = readImageBytesForLocalInput(context, attachmentUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "attachment decode failed" }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sampleSize = 1
        while (longEdge / sampleSize > LOCAL_IMAGE_MAX_LONG_EDGE_PX) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("attachment decode failed")
        val normalizedBitmap = normalizeBitmapForLocalImageInput(bitmap, bytes)
        val jpegBytes = ByteArrayOutputStream().use { output ->
            normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, LOCAL_IMAGE_JPEG_QUALITY, output)
            normalizedBitmap.recycle()
            if (normalizedBitmap !== bitmap) bitmap.recycle()
            output.toByteArray()
        }
        val attachmentFile = File(
            attachmentsDir,
            "local_att_${System.currentTimeMillis()}_$index.jpg",
        )
        attachmentFile.writeBytes(jpegBytes)
        Log.d("LocalImageInput", "Prepared local image input path=${attachmentFile.absolutePath} bytes=${jpegBytes.size}")
        LocalImageInput(
            absolutePath = attachmentFile.absolutePath,
            savedUriString = attachmentFile.toURI().toString(),
        )
    }
}

private fun readImageBytesForLocalInput(
    context: Context,
    attachmentUri: Uri,
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
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= LOCAL_IMAGE_MAX_SOURCE_BYTES) { "attachment too large" }
            output.write(buffer, 0, read)
        }
    } ?: error("attachment read failed")
    return output.toByteArray()
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
    val flattened = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(oriented, 0f, 0f, null)
    }
    if (oriented !== bitmap) oriented.recycle()
    return flattened
}

private fun hasKnownQnnCompiledVisionConstraint(sha256: String): Boolean =
    sha256.equals(GEMMA_4_E2B_VISION_MODEL_SHA256, ignoreCase = true)

private object LocalVisionModelSha256Cache {
    private data class Key(
        val path: String,
        val length: Long,
        val lastModified: Long,
    )

    private val cache = linkedMapOf<Key, String>()

    fun sha256(file: File): String {
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
