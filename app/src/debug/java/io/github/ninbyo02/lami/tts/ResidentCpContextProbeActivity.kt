package io.github.ninbyo02.lami.tts

import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.io.File

/**
 * Explicit-only silent QNN create/release probe in an isolated process.
 * Never invoked by normal LAMI/TTS. No audio or CP steps are executed.
 */
class ResidentCpContextProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.APPLICATION_ID != "io.github.ninbyo02.lami.cpdiagnostic") {
            finish()
            return
        }
        val result = try {
            val context = File(filesDir, "mtp-qnn-context.bin")
            val spec = ResidentCpContextSpec(
                "1ebaf036a376c886fe135afbf5f87dfb651be2804dc8675f1035be9ca7a92136",
                1024, 16, 32, "qairt247-fp32-sm8750"
            )
            ResidentCpRustApplicationApi.create(applicationContext, spec).use { api ->
                val handle = api.loadContext(context.absolutePath)
                try {
                    "QNN_CONTEXT_OPEN_OK; QNN_STEPS_NOT_EXECUTED"
                } finally {
                    api.release(handle)
                }
            }
        } catch (failure: Exception) {
            "QNN_CONTEXT_OPEN_FAILED: " + failure.javaClass.simpleName +
                " " + (failure.message ?: "").take(180)
        } catch (failure: LinkageError) {
            "QNN_CONTEXT_LINK_FAILED: " + failure.javaClass.simpleName
        }
        try {
            openFileOutput("resident-cp-context-probe.txt", MODE_PRIVATE).bufferedWriter().use {
                it.write(result)
                it.newLine()
                // Read the native bounded receipt after the probe failed/succeeded.
                val qnnLog = runCatching { ResidentCpRustJni.diagnosticLog() }.getOrDefault("")
                if (qnnLog.isNotBlank()) {
                    it.write(qnnLog.take(8192))
                    it.newLine()
                }
            }
            Log.i("ResidentCpContextProbe", result)
        } catch (failure: Exception) {
            Log.e("ResidentCpContextProbe", "RESULT_WRITE_FAILED", failure)
        }
        finish()
    }
}
