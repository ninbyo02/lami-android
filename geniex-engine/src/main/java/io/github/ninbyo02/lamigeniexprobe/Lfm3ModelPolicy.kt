package io.github.ninbyo02.lamigeniexprobe

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class ComputeTarget(val value: String) {
    CPU("cpu"), GPU("gpu"), NPU("npu"), HYBRID("hybrid"),
}

internal object Lfm3ModelPolicy {
    const val REFERENCE_NAME = "LFM3-1.7B-QAD-Q4_0.gguf"
    const val REFERENCE_SIZE = 1_049_615_680L
    const val REFERENCE_SHA256 = "43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452"

    fun internalModel(context: Context): File = context.filesDir.resolve("models/$REFERENCE_NAME")

    fun findModel(context: Context): File? {
        val externalModel = context.getExternalFilesDir(null)?.resolve(REFERENCE_NAME)
        return listOfNotNull(internalModel(context), externalModel).firstOrNull(::hasModelSize)
    }

    private fun hasModelSize(model: File): Boolean =
        model.isFile && model.canRead() && model.length() == REFERENCE_SIZE

    suspend fun verify(model: File, checkStopped: () -> Unit) {
        require(hasModelSize(model)) { "GenieX model is missing" }
        model.inputStream().use { checkContent(it, checkStopped = checkStopped) }
    }

    suspend fun importModel(context: Context, source: InputStream) {
        val model = internalModel(context)
        val directory = checkNotNull(model.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create the model directory" }
        val temporary = File.createTempFile("import-", ".gguf", directory)
        try {
            temporary.outputStream().use { target ->
                checkContent(source, copy = target::write)
            }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(model)) { "Cannot install the validated model" }
        } finally {
            temporary.delete()
        }
    }

    private suspend fun checkContent(
        source: InputStream,
        checkStopped: () -> Unit = {},
        copy: (ByteArray, Int, Int) -> Unit = { _, _, _ -> },
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        var total = 0L
        val context = currentCoroutineContext()
        while (true) {
            context.ensureActive()
            checkStopped()
            val read = source.read(buffer)
            if (read < 0) break
            total += read
            require(total <= REFERENCE_SIZE) { "The GGUF is larger than expected" }
            digest.update(buffer, 0, read)
            copy(buffer, 0, read)
        }
        require(total == REFERENCE_SIZE &&
            digest.digest().joinToString("") { "%02x".format(it) } == REFERENCE_SHA256) {
            "The GGUF does not match the supported LFM3 model"
        }
    }
}
