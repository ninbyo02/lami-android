package io.github.ninbyo02.lami.npu

import android.app.Activity
import android.os.Bundle
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DevNpuChatScreenToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = SettingsPreferences(applicationContext)
        val requestedWrite = intent.hasExtra(EXTRA_ENABLED)
        val requestedEnabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        var error: String? = null
        val before = runBlocking {
            preferences.devEnableNpuChatScreenRouteFlow.first()
        }
        if (requestedWrite) {
            runCatching {
                runBlocking {
                    preferences.saveDevEnableNpuChatScreenRoute(requestedEnabled)
                }
            }.onFailure { throwable ->
                error = "${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}"
            }
        }
        val after = runBlocking {
            preferences.devEnableNpuChatScreenRouteFlow.first()
        }
        writeStateFile(
            before = before,
            after = after,
            requestedWrite = requestedWrite,
            requestedEnabled = requestedEnabled,
            error = error,
        )
        finish()
    }

    private fun writeStateFile(
        before: Boolean,
        after: Boolean,
        requestedWrite: Boolean,
        requestedEnabled: Boolean,
        error: String?,
    ) {
        val text = buildString {
            appendLine("activity=DevNpuChatScreenToggleActivity")
            appendLine("custom_build_experiment=${BuildConfig.CUSTOM_BUILD_EXPERIMENT}")
            appendLine("key=dev_enable_npu_chatscreen_route")
            appendLine("requested_write=$requestedWrite")
            appendLine("requested_enabled=$requestedEnabled")
            appendLine("before=$before")
            appendLine("after=$after")
            appendLine("error=${error ?: "none"}")
            appendLine("npu_generation=false")
            appendLine("engine_initialize=false")
            appendLine("run_decode=false")
            appendLine("selected_path_npu=false")
        }
        File(filesDir, STATE_FILE_NAME).writeText(text)
    }

    companion object {
        const val EXTRA_ENABLED = "enabled"
        const val STATE_FILE_NAME = "dev_npu_chatscreen_toggle_state.txt"
    }
}
