package io.github.ninbyo02.lami.debug

import android.app.Activity
import android.os.Bundle
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalApi::class)
class LiteRtMinimalSmokeTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread({
            var engine: Engine? = null
            var conversation: Conversation? = null
            try {
                val modelPathOverride = intent?.getStringExtra(EXTRA_MODEL_PATH)?.trim().orEmpty()
                val backendMode = intent?.getStringExtra(EXTRA_BACKEND_MODE)?.trim().orEmpty().ifBlank { BACKEND_CPU }
                val resolvedModelPath = modelPathOverride.ifBlank { DEFAULT_MODEL_PATH }
                val modelFile = File(resolvedModelPath)
                writeResult(append = false, line = "intentExtras=${intent?.extras?.keySet()?.sorted().orEmpty()}")
                writeResult(
                    append = true,
                    line = "start backendMode=$backendMode modelPathOverride=${modelPathOverride.ifBlank { "none" }} " +
                        "resolvedModelPath=$resolvedModelPath",
                )
                writeResult(
                    append = true,
                    line = "model exists=${modelFile.exists()} canRead=${modelFile.canRead()} size=${modelFile.length()}",
                )
                val backend = when (backendMode) {
                    BACKEND_CPU -> Backend.CPU()
                    BACKEND_GPU -> Backend.GPU()
                    else -> {
                        writeResult(append = true, line = "RESULT=FAILED message=unknown backendMode backendMode=$backendMode")
                        return@Thread
                    }
                }
                val config = EngineConfig(
                    modelPath = resolvedModelPath,
                    backend = backend,
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU(),
                    maxNumTokens = null,
                    cacheDir = cacheDir.absolutePath,
                )
                writeResult(append = true, line = "config-created cacheDir=${config.cacheDir} backendMode=$backendMode")
                writeResult(append = true, line = "backend class=${backend.javaClass.name}")
                engine = Engine(config)
                writeResult(append = true, line = "engine-created")
                writeResult(append = true, line = "engine-initialize-enter")
                engine.initialize()
                writeResult(append = true, line = "engine-initialized initialized=${engine.isInitialized()}")
                conversation = engine.createConversation()
                writeResult(append = true, line = "conversation-created")
                writeResult(append = true, line = "sendMessage-enter")
                val response = conversation.sendMessage("hi")
                writeResult(append = true, line = "response=${response.contents.toString().trim()}")
                writeResult(append = true, line = "RESULT=SUCCESS")
            } catch (throwable: Throwable) {
                writeResult(
                    append = true,
                    line = "RESULT=FAILED class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
                )
                throwable.cause?.let { cause ->
                    writeResult(
                        append = true,
                        line = "CAUSE class=${cause.javaClass.name} message=${cause.message.orEmpty()}",
                    )
                }
                writeResult(append = true, line = "STACK ${throwable.stackTraceToString()}")
            } finally {
                runCatching { conversation?.close() }
                    .onFailure { throwable ->
                        writeResult(
                            append = true,
                            line = "close-error target=conversation class=${throwable.javaClass.name} " +
                                "message=${throwable.message.orEmpty()}",
                        )
                    }
                runCatching { engine?.close() }
                    .onFailure { throwable ->
                        writeResult(
                            append = true,
                            line = "close-error target=engine class=${throwable.javaClass.name} " +
                                "message=${throwable.message.orEmpty()}",
                        )
                    }
                runOnUiThread { finish() }
            }
        }, "LiteRtMinimalSmokeTest").start()
    }

    private fun writeResult(append: Boolean, line: String) {
        val file = File(filesDir, RESULT_NAME)
        FileOutputStream(file, append).use { output ->
            output.write(line.toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            output.flush()
            output.fd.sync()
        }
    }

    private companion object {
        private const val EXTRA_MODEL_PATH = "modelPath"
        private const val EXTRA_BACKEND_MODE = "backendMode"
        private const val BACKEND_CPU = "cpu"
        private const val BACKEND_GPU = "gpu"
        private const val DEFAULT_MODEL_PATH = "/data/local/tmp/qairt/model2.litertlm"
        private const val RESULT_NAME = "litert_minimal_result.txt"
    }
}
