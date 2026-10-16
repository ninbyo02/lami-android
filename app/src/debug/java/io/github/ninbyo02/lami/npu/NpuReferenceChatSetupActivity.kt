package io.github.ninbyo02.lami.npu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.MainActivity
import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.ui.screens.settings.InferenceBackendSelection
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Configures only the separate NPU reference app, never the user's main LAMI. */
class NpuReferenceChatSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.DEBUG && packageName == "io.github.ninbyo02.lami.npuvalidation.assistantprobe") {
            "Separate NPU reference package required"
        }
        lifecycleScope.launch {
            val model = File(requireNotNull(Qairt244ModelPathResolver.resolve(applicationContext).path))
            val settings = SettingsPreferences(applicationContext)
            settings.saveLocalBaseModelInfo(model.name, model.absolutePath)
            settings.saveInferenceTarget(InferenceTarget.LOCAL)
            settings.saveInferenceBackendSelection(InferenceBackendSelection.NPU)
            settings.setTtsEnabled(false)
            File(filesDir, "npu_reference_chat_setup.json").writeText(JSONObject()
                .put("status", "configured").put("package", packageName)
                .put("model", model.name).put("tts_enabled", false).toString(2))
            startActivity(Intent(this@NpuReferenceChatSetupActivity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }
}
