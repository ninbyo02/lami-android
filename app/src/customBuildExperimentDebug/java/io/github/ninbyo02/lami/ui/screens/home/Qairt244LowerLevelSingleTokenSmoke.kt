package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.BuildConfig

internal class Qairt244LowerLevelSingleTokenSmoke private constructor() {
    companion object {
        private const val RESULT_FILE_NAME = "qairt244_single_token_smoke_result.txt"
        private const val NATIVE_DIAG_FILE_NAME = "qairt244_native_diag.txt"

        init {
            System.loadLibrary("litertlm_jni")
        }

        @JvmStatic
        fun run(
            context: Context,
            modelPath: String,
            runId: String,
        ): String {
            check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
                "lower-level single-token smoke is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            check(modelPath.isNotBlank()) { "modelPath is required" }

            val appContext = context.applicationContext
            val resultPath = appContext.filesDir.resolve(RESULT_FILE_NAME).absolutePath
            val diagPath = appContext.filesDir.resolve(NATIVE_DIAG_FILE_NAME).absolutePath
            val nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir
            val cacheDir = appContext.cacheDir.absolutePath

            val output = nativeRun(
                modelPath = modelPath,
                nativeLibraryDir = nativeLibraryDir,
                cacheDir = cacheDir,
                resultPath = resultPath,
                diagPath = diagPath,
            )
            return "qairt244_lower_level_single_token_smoke_v1 runId=$runId result=success output=$output"
        }

        @JvmStatic
        private external fun nativeRun(
            modelPath: String,
            nativeLibraryDir: String,
            cacheDir: String,
            resultPath: String,
            diagPath: String,
        ): String
    }
}
