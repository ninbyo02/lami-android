package io.github.ninbyo02.lami.tts

import android.app.Activity
import android.os.Bundle
import android.system.Os
import java.security.MessageDigest
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
            val skeleton = File(filesDir, "libQnnHtpV79Skel.so")
            require(skeleton.isFile && skeleton.length() == 11491912L) {
                "Missing pinned QAIRT 2.47 DSP skeleton"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            skeleton.inputStream().use { stream ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            require(hash == "dd8987c17928e53877e51ce5739c550d3e74393bd5b1b5ccf311326eddb08612") {
                "DSP skeleton SHA-256 mismatch"
            }
            // Diagnostic process only; Android vendor files and normal LAMI stay untouched.
            Os.setenv("ADSP_LIBRARY_PATH", filesDir.absolutePath + ";/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp", true)
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
