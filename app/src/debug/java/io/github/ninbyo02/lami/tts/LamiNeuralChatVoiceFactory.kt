package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

object LamiNeuralChatVoiceFactory {
    @JvmStatic
    fun availability(context: Context, choice: TtsEngineChoice): TtsEngineAvailability = when (choice) {
        TtsEngineChoice.ANDROID -> TtsEngineAvailability(choice, true, "端末のAndroid TTSを使用します。")
        TtsEngineChoice.LAMI_QWEN3 -> {
            val problem = validateQwenBundle(qwenDirectory(context))
            TtsEngineAvailability(choice, problem == null, problem ?: "LAMIかわいい声・Qwen3-TTSモデルを利用できます。")
        }
        TtsEngineChoice.SUPERTONIC3 -> LamiSupertonicVoiceFactory.availability(context)
        TtsEngineChoice.KOKORO -> LamiKokoroScratchVoiceFactory.availability(context)
    }

    /** An explicit choice must never silently select a different voice. */
    @JvmStatic
    fun createSelected(context: Context, choice: TtsEngineChoice): LamiVoiceEngine? {
        if (choice != TtsEngineChoice.KOKORO && !availability(context, choice).available) return null
        return when (choice) {
            TtsEngineChoice.ANDROID -> AndroidTtsController(context.applicationContext)
            TtsEngineChoice.LAMI_QWEN3 -> createQwen(context, selectedForComparison = true)
            TtsEngineChoice.SUPERTONIC3 -> LamiSupertonicVoiceFactory.createSelected(context)
            TtsEngineChoice.KOKORO -> LamiKokoroScratchVoiceFactory.createSelected(context)
        }
    }

    private fun qwenDirectory(context: Context) = context.filesDir.resolve("local_models/lami_tts/prepared_hai")

    /** Availability uses file metadata; synthesis performs the full bundle hash validation. */
    internal fun validateQwenBundle(root: File): String? {
        val manifest = root.resolve("voice-text-bundle.json")
        if (!manifest.isFile || manifest.length() == 0L) return "Qwen3-TTSのLAMI音声モデルが未導入です。"
        return try {
            val data = JSONObject(manifest.readText())
            LamiQwenBundleContract.validate(data)
            val hashes = data.getJSONObject("sha256")
            require(hashes.length() > 0) { "音声モデルのファイル一覧が空です。" }
            val canonicalRoot = root.canonicalFile.toPath()
            for (name in hashes.keys()) {
                require(name.isNotBlank() && !File(name).isAbsolute) { "音声モデルのファイル名が不正です。" }
                val file = root.resolve(name).canonicalFile
                require(file.toPath().startsWith(canonicalRoot) && file.toPath() != canonicalRoot) { "音声モデルの配置が不正です。" }
                require(hashes.getString(name).matches(Regex("[0-9a-fA-F]{64}"))) { "音声モデルのハッシュ形式が不正です。" }
                require(file.isFile && file.length() > 0) { "音声モデルのファイルが不足しています: $name" }
            }
            null
        } catch (failure: Exception) {
            "Qwen3-TTSを利用できません: ${failure.message ?: failure.javaClass.simpleName}"
        }
    }

    private fun createQwen(context: Context, selectedForComparison: Boolean = false): LamiVoiceEngine = LamiNeuralChatVoiceEngine(
        context.applicationContext,
        qwenDirectory(context),
        streamingEnabled = LamiVoiceTrialPolicy.streamingEnabledForSelection(selectedForComparison, context.packageName) {
            context.getSharedPreferences("lami_cute_debug_trial", Context.MODE_PRIVATE)
                .getBoolean("streaming_enabled", false)
        },
    )

    @JvmStatic
    fun createOrNull(context: Context): LamiVoiceEngine? {
        // Explicit emulator development choice: use Android's installed TTS engine.
        if (io.github.ninbyo02.lami.ui.screens.home.EmulatorMainChatBackend.isEmulator() &&
            context.getSharedPreferences(io.github.ninbyo02.lami.ui.screens.home.EmulatorMainChatBackend.PREFS,
                Context.MODE_PRIVATE).getBoolean("android_tts", false)) return null
        LamiSupertonicVoiceFactory.createOrNull(context)?.let { return it }
        LamiKokoroVoiceFactory.createOrNull(context)?.let { return it }
        return if (validateQwenBundle(qwenDirectory(context)) == null) createQwen(context) else null
    }
    /** Explicit diagnostic API. Hashing may be expensive; call off the UI thread.
     * Fingerprints identify the supplied artifacts, not all dynamically loaded libraries.
     */
    @JvmStatic
    fun createDiagnosticOrNull(context: Context, modelArtifact: File, runtimeArtifact: File): LamiVoiceEngine? {
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        if (!root.resolve("voice-text-bundle.json").isFile) return null
        return try {
            fun hash(file: File): String {
                require(file.isFile)
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            val store = LamiSpeechTraceStore(context.filesDir.resolve("diagnostics/speech_traces"),
                hash(modelArtifact), hash(runtimeArtifact), SystemClock::elapsedRealtime)
            LamiNeuralChatVoiceEngine(context.applicationContext, root, traceFactory = store::capture)
        } catch (_: Exception) {
            // Diagnostic setup failure must still permit speech.
            LamiNeuralChatVoiceEngine(context.applicationContext, root)
        }
    }
}
