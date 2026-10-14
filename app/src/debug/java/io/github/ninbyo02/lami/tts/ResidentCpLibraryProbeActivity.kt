package io.github.ninbyo02.lami.tts

import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig

/** Silent, explicit-only JNI load probe. No QNN context, tensors, audio or model access. */
class ResidentCpLibraryProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.APPLICATION_ID != "io.github.ninbyo02.lami.cpdiagnostic") {
            Log.e("ResidentCpLibraryProbe", "BLOCKED wrong package")
            finish()
            return
        }
        val result = try {
            System.loadLibrary("lami_resident_cp_diag")
            System.loadLibrary("QnnSystem")
            System.loadLibrary("QnnHtpV79Stub")
            System.loadLibrary("QnnHtp")
            "QNN_LIBRARIES_LOAD_OK; CONTEXT_NOT_CREATED; NPU_NOT_EXECUTED"
        } catch (error: LinkageError) {
            "JNI_LIBRARY_LOAD_FAILED: " + error.javaClass.simpleName
        }
        try {
            openFileOutput("resident-cp-library-probe.txt", MODE_PRIVATE).bufferedWriter().use {
                it.write(result)
                it.newLine()
            }
            Log.i("ResidentCpLibraryProbe", result)
        } catch (error: Exception) {
            Log.e("ResidentCpLibraryProbe", "DIAGNOSTIC_WRITE_FAILED", error)
        } finally {
            finish()
        }
    }
}
