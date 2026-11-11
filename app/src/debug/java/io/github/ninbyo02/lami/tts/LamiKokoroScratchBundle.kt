package io.github.ninbyo02.lami.tts

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

internal class LamiKokoroScratchBundle private constructor(
    private val files: Map<String, Asset>,
    val diagnosticOnly: Boolean,
    val identity: String,
    val maxFrames: Int,
) {
    private data class Asset(val file: File, val bytes: Long, val sha256: String)

    val textModel: File get() = files.getValue("text.onnx").file
    val renderModel: File get() = files.getValue("render.onnx").file

    /** Call on the synthesis worker before opening either graph. */
    fun verifyAndReadVocabulary(checkActive: () -> Unit = {}): LamiKokoroScratchVocabulary {
        for ((name, asset) in files) {
            checkActive()
            require(asset.file.length() == asset.bytes && hash(asset.file, checkActive) == asset.sha256) {
                "Kokoroのモデルファイルを確認できませんでした: $name"
            }
        }
        checkActive()
        return LamiKokoroScratchVocabulary.read(files.getValue("frontend.json").file)
    }

    companion object {
        const val DIRECTORY = "local_models/kokoro_scratch"
        private val REQUIRED_FILES = listOf("frontend.json", "render.onnx", "text.onnx")
        private val HASH = Regex("[0-9a-f]{64}")

        // An approval binds the trained checkpoint to the exact exported graphs
        // and phoneme vocabulary. A manifest's quality flag alone is insufficient.
        private val approvedIdentities = setOf(
            // Random-origin 14000-step trial, verified with normal Android PCM
            // playback/stop/retry and 23 untrained Japanese utterances.
            "f106bec603d885c059df1d551e85f90779dd4e6eaafae72cbe7a9e2b44dd35fe",
            // Random-origin 15661-step pronunciation trial with additional LAMI teacher audio.
            "af86d9ad0cf96cd4df65aedfa383057045476c57ddb66ecdb7ff834f057243d0",
            // Random-origin 16186-step trial with verified full-utterance phoneme targets.
            "f30dd4f9f21dec0fc14e4942bea09fb98b38dcbb816a971262d62700f992648d",
            // Random-origin 16836-step trial with corrected labels and additional reviewed LAMI audio.
            "d734860447744591f62e3b899945d2b7f5313fba1e80d0e23a3bdbc476883313",
            // Random-origin 17336-step trial with expanded verified phoneme supervision.
            "8ccce42e9e4b20d19ceb1b4b4fb54ff479f7461ebaa4b25824eb8d39843250f4",
        )

        fun read(directory: File, diagnostic: Boolean = false): LamiKokoroScratchBundle {
            val root = directory.canonicalFile
            val manifest = root.resolve("manifest.json").canonicalFile
            require(manifest.parentFile == root && manifest.isFile && manifest.length() in 1L..1_048_576L) {
                "Kokoroの音声モデルが未導入です。"
            }
            val data = JSONObject(manifest.readText())
            val maxFrames = data.getInt("max_frames")
            require(data.getInt("format_version") == 1 && data.getString("runtime") == "onnxruntime-cpu" &&
                data.getInt("opset") == 17 && data.getString("origin") == "all-weights-random-initialized" &&
                data.get("teacher_forced") == false && data.get("strict_state_reload") == true &&
                data.getInt("sample_rate") == LamiPcmContract.SAMPLE_RATE && data.getInt("channels") == 1 &&
                data.getInt("samples_per_frame") == 600 && maxFrames in 1..600 &&
                data.getInt("harmonics") == 9 && data.getInt("acoustic_channels") == 512 &&
                data.getInt("prosody_channels") == 640) { "Kokoroの音声モデル形式に対応していません。" }
            val checkpoint = data.getString("checkpoint_sha256")
            require(HASH.matches(checkpoint)) { "Kokoroのモデル識別情報が不正です。" }
            val fileEntries = data.getJSONObject("files")
            require(fileEntries.keys().asSequence().toSet() == REQUIRED_FILES.toSet()) {
                "Kokoroの音声モデルの構成が不正です。"
            }
            val assets = REQUIRED_FILES.associateWith { name ->
                val entry = fileEntries.getJSONObject(name)
                val file = root.resolve(name).canonicalFile
                val bytes = entry.getLong("bytes")
                val digest = entry.getString("sha256")
                require(file.parentFile == root && file.isFile && bytes > 0 && file.length() == bytes && HASH.matches(digest)) {
                    "Kokoroのモデルファイルを確認できませんでした: $name"
                }
                Asset(file, bytes, digest)
            }
            val fingerprint = buildString {
                appendLine(checkpoint)
                appendLine("max_frames:$maxFrames")
                for ((name, asset) in assets) appendLine("$name:${asset.bytes}:${asset.sha256}")
            }
            val identity = hex(MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8)))
            if (diagnostic) {
                require(data.get("diagnostic_only") == true && data.get("quality_verified") == false) {
                    "Kokoroの診断用バンドルが必要です。"
                }
            } else {
                require(data.get("diagnostic_only") == false && data.get("quality_verified") == true &&
                    data.get("android_device_verified") == true && identity in approvedIdentities) {
                    "発音を確認したKokoroの音声モデルがまだ準備されていません。"
                }
            }
            return LamiKokoroScratchBundle(assets, diagnostic, identity, maxFrames)
        }

        private fun hash(file: File, checkActive: () -> Unit): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}

internal class LamiKokoroScratchVocabulary private constructor(private val ids: Map<String, Long>) {
    private val knownIds = ids.values.toSet()

    fun encode(phonemes: String): LongArray {
        val count = phonemes.codePointCount(0, phonemes.length)
        require(count in 1..(MAX_TOKENS - 2)) { "Kokoroで読み上げられる文章の長さを超えています。" }
        val tokens = LongArray(count + 2)
        var offset = 0
        for (index in 1..count) {
            val width = Character.charCount(phonemes.codePointAt(offset))
            val symbol = phonemes.substring(offset, offset + width)
            tokens[index] = requireNotNull(ids[symbol]) { "Kokoroが読めない音を含む文章です。" }
            offset += width
        }
        return tokens
    }

    fun requireValid(tokens: LongArray) {
        require(tokens.size in 3..MAX_TOKENS && tokens.first() == 0L && tokens.last() == 0L &&
            (1 until tokens.lastIndex).all { tokens[it] in knownIds }) { "Kokoroの音素IDが不正です。" }
    }

    fun splitAtWordBoundary(tokens: LongArray): Pair<LongArray, LongArray>? {
        val space = ids[" "] ?: return null
        val boundary = (2 until tokens.lastIndex - 1)
            .filter { tokens[it] == space && tokens[it - 1] != space && tokens[it + 1] != space }
            .minByOrNull { kotlin.math.abs(it - tokens.size / 2) } ?: return null
        val first = LongArray(boundary + 1)
        val second = LongArray(tokens.size - boundary)
        tokens.copyInto(first, endIndex = boundary)
        tokens.copyInto(second, destinationOffset = 1, startIndex = boundary + 1)
        return first to second
    }

    companion object {
        private const val MAX_TOKENS = 512

        fun read(file: File): LamiKokoroScratchVocabulary {
            require(file.length() in 1L..65_536L) { "Kokoroの音素辞書が不正です。" }
            val data = JSONObject(file.readText())
            require(data.getInt("bos_id") == 0 && data.getInt("eos_id") == 0 &&
                data.getInt("min_tokens") == 3 && data.getInt("max_tokens") == MAX_TOKENS) {
                "Kokoroの音素辞書形式に対応していません。"
            }
            val vocabulary = data.getJSONObject("vocab")
            require(vocabulary.length() in 1..512) { "Kokoroの音素辞書が不正です。" }
            val ids = vocabulary.keys().asSequence().associateWith { symbol ->
                val id = vocabulary.get(symbol)
                require(symbol.codePointCount(0, symbol.length) == 1 && id is Number &&
                    id.toLong() in 1L..65_535L && id.toDouble() == id.toLong().toDouble()) {
                    "Kokoroの音素辞書が不正です。"
                }
                id.toLong()
            }
            return LamiKokoroScratchVocabulary(ids)
        }
    }
}
