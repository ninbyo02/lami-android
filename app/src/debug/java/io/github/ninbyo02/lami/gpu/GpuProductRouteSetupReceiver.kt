package io.github.ninbyo02.lami.gpu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/**
 * Registers the benchmark-private generic model with the normal chat route.
 *
 * This receiver is only exported by the isolated GPU candidate debug manifest.
 * It never copies model bytes and is not present in production variants.
 */
class GpuProductRouteSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        executor.execute {
            val appContext = context.applicationContext
            val requestedPath = intent.getStringExtra(EXTRA_MODEL_PATH)
                ?.takeIf { it.isNotBlank() }
                ?: File(appContext.filesDir, DEFAULT_MODEL_RELATIVE_PATH).absolutePath
            val modelFile = File(requestedPath)
            val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
            try {
                require(modelFile.isFile) { "model file does not exist: $requestedPath" }
                require(modelFile.length() > 0L) { "model file is empty: $requestedPath" }
                runBlocking {
                    val settings = SettingsPreferences(appContext)
                    settings.saveLocalGenericModelInfo(
                        displayName = modelFile.name,
                        filePath = modelFile.absolutePath,
                    )
                    settings.saveInferenceTarget(InferenceTarget.LOCAL)
                    settings.savePreferredBackendDryRunSetting(PreferredBackendDryRunSetting.GPU)
                }
                stateFile.writeText(
                    "status=success\n" +
                        "model_path=${modelFile.absolutePath}\n" +
                        "model_length=${modelFile.length()}\n" +
                        "inference_target=LOCAL\n" +
                        "preferred_backend=GPU\n",
                )
            } catch (throwable: Throwable) {
                stateFile.writeText(
                    "status=failure\n" +
                        "model_path=$requestedPath\n" +
                        "error_class=${throwable.javaClass.name}\n" +
                        "error_message=${throwable.message.orEmpty()}\n",
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_MODEL_PATH = "model_path"
        const val STATE_FILE_NAME = "gpu_product_route_setup_state.txt"
        private const val DEFAULT_MODEL_RELATIVE_PATH = "models/model.litertlm"
        private val executor = Executors.newSingleThreadExecutor()
    }
}
