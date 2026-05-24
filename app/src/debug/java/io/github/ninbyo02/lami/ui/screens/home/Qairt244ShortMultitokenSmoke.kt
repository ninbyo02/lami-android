package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.BuildConfig

internal class Qairt244ShortMultitokenSmoke private constructor() {
    companion object {
        private const val RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
        private const val NATIVE_DIAG_FILE_NAME = "qairt244_native_diag.txt"
        private val allowedDebugFlavors = setOf("standard", "customBuildExperiment")

        init {
            System.loadLibrary("litertlm_jni")
        }

        @JvmStatic
        fun supportsEditablePromptExecution(): Boolean = true

        @JvmStatic
        fun run(
            context: Context,
            modelPath: String,
            runId: String,
        ): String {
            check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
                "short multi-token smoke is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
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
            return "qairt244_short_multitoken_smoke_v1 runId=$runId result=success output=$output"
        }

        @JvmStatic
        fun runEditablePrompt(
            context: Context,
            modelPath: String,
            runId: String,
            prompt: String,
            maxOutputTokens: Int,
            promptValidationMode: String = NpuDiagnosticPromptValidator.ASCII_DIAGNOSTIC_MODE,
        ): String {
            check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
                "editable prompt smoke is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            check(modelPath.isNotBlank()) { "modelPath is required" }
            val validation = when (promptValidationMode) {
                NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8InternalIntent(prompt)
                NpuDiagnosticPromptValidator.UTF8_HIDDEN_EXPERIMENTAL_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental(prompt)
                NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(prompt)
                else -> NpuDiagnosticPromptValidator.validateAsciiDiagnostic(prompt)
            }
            check(validation.isValid) {
                "editable prompt rejected before native execution: reasonCode=${validation.reasonCode}"
            }

            val appContext = context.applicationContext
            val resultPath = appContext.filesDir.resolve(RESULT_FILE_NAME).absolutePath
            val diagPath = appContext.filesDir.resolve(NATIVE_DIAG_FILE_NAME).absolutePath
            val nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir
            val cacheDir = appContext.cacheDir.absolutePath

            val normalizedPrompt = validation.normalizedPrompt
            val output = nativeRunEditablePrompt(
                modelPath = modelPath,
                nativeLibraryDir = nativeLibraryDir,
                cacheDir = cacheDir,
                resultPath = resultPath,
                diagPath = diagPath,
                prompt = normalizedPrompt,
                promptInputLimitMode = validation.promptInputLimitMode,
                maxOutputTokens = maxOutputTokens,
            )
            return "qairt244_editable_prompt_smoke_v1 runId=$runId result=success actual_prompt=$normalizedPrompt normalized_prompt=$normalizedPrompt output=$output"
        }

        @JvmStatic
        private external fun nativeRun(
            modelPath: String,
            nativeLibraryDir: String,
            cacheDir: String,
            resultPath: String,
            diagPath: String,
        ): String

        @JvmStatic
        private external fun nativeRunEditablePrompt(
            modelPath: String,
            nativeLibraryDir: String,
            cacheDir: String,
            resultPath: String,
            diagPath: String,
            prompt: String,
            promptInputLimitMode: String,
            maxOutputTokens: Int,
        ): String
    }
}
