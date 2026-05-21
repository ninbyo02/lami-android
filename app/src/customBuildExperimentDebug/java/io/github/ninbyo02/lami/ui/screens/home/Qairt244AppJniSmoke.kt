package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

internal class Qairt244AppJniSmoke private constructor() {
    companion object {
        private const val OUTPUT_FILE_NAME = "qairt244_app_jni_smoke.txt"

        init {
            System.loadLibrary("lami_qairt244_smoke")
        }

        @JvmStatic
        fun run(context: Context, runId: String): String {
            val outputPath = context.filesDir.resolve(OUTPUT_FILE_NAME).absolutePath
            return nativeRun(outputPath, runId)
        }

        @JvmStatic
        private external fun nativeRun(outputPath: String, runId: String): String
    }
}
