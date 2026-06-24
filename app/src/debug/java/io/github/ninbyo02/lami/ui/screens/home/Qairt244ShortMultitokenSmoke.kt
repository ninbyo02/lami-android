package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.BuildConfig

internal class Qairt244ShortMultitokenSmoke private constructor() {
    companion object {
        private const val RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
        private const val NATIVE_DIAG_FILE_NAME = "qairt244_native_diag.txt"
        private const val PERSISTENT_PROBE_RESULT_FILE_NAME =
            "qairt244_persistent_custom_jni_probe_result.txt"
        private const val PERSISTENT_PROBE_DIAG_FILE_NAME =
            "qairt244_persistent_custom_jni_probe_diag.txt"
        private val allowedDebugFlavors = setOf("standard", "customBuildExperiment")
        private val allowedTrueEngineCreateCloseFlavors = allowedDebugFlavors + "trueEngineNpuProbe"

        init {
            System.loadLibrary("litertlm_jni")
            System.loadLibrary("lami_npu_persistent_holder_stub")
        }

        @JvmStatic
        fun supportsEditablePromptExecution(): Boolean = true

        @JvmStatic
        fun supportsPersistentCustomJniProbeExecution(): Boolean = true

        @JvmStatic
        fun supportsPersistentHolderNativeStubExecution(): Boolean = true

        @JvmStatic
        fun createStandardRouteAdapterHolder(
            request: NpuPersistentHolderCreateRequest,
        ): String {
            check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
                "persistent holder native stub is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            return nativeCreateStandardRouteAdapterHolder(
                modelPath = request.modelPath,
                nativeLibraryDir = request.nativeLibraryDir,
                cacheDir = request.cacheDir,
                maxTokens = request.maxTokens,
            )
        }

        @JvmStatic
        fun runStandardRouteAdapterHolderOnce(
            request: NpuPersistentHolderRunRequest,
        ): String {
            check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
                "persistent holder native stub is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            return nativeRunStandardRouteAdapterHolderOnce(
                holderId = request.holderId,
                prompt = request.prompt,
                maxOutputTokens = request.maxOutputTokens,
            )
        }

        @JvmStatic
        fun closeStandardRouteAdapterHolder(
            request: NpuPersistentHolderCloseRequest,
        ): String {
            check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
                "persistent holder native stub is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            return nativeCloseStandardRouteAdapterHolder(
                holderId = request.holderId,
                reason = request.reason,
            )
        }

        @JvmStatic
        fun getStandardRouteAdapterHolderDiagnostics(holderId: String): String {
            check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
                "persistent holder native stub is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            return nativeGetStandardRouteAdapterHolderDiagnostics(holderId = holderId)
        }

        @JvmStatic
        fun runTrueEngineHolderCreateCloseProbe(
            context: Context,
            modelPath: String,
            runId: String,
            maxOutputTokens: Int,
            holderKey: String,
        ): NpuTrueEngineHolderNativeResult {
            check(BuildConfig.CURRENT_FLAVOR in allowedTrueEngineCreateCloseFlavors) {
                "true engine holder create/close probe is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            check(modelPath.isNotBlank()) { "modelPath is required" }
            check(holderKey.isNotBlank()) { "holderKey is required" }
            val appContext = context.applicationContext
            val resultFile = appContext.filesDir.resolve(PERSISTENT_PROBE_RESULT_FILE_NAME)
            val diagFile = appContext.filesDir.resolve(PERSISTENT_PROBE_DIAG_FILE_NAME)
            resultFile.delete()
            diagFile.delete()
            val nativeResult = runCatching {
                nativeRunPersistentProbe(
                    modelPath = modelPath,
                    nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
                    cacheDir = appContext.cacheDir.absolutePath,
                    resultPath = resultFile.absolutePath,
                    diagPath = diagFile.absolutePath,
                    prompt = "こんにちは",
                    promptInputLimitMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
                    maxOutputTokens = maxOutputTokens,
                    runCount = 0,
                    holderKey = holderKey,
                    nativeProbeMode = NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NATIVE_PROBE_MODE,
                )
            }
            val throwable = nativeResult.exceptionOrNull()
            return NpuTrueEngineHolderNativeResult(
                nativeReturn = nativeResult.getOrDefault(""),
                resultText = resultFile.takeIf { it.exists() }?.readText().orEmpty(),
                diagText = diagFile.takeIf { it.exists() }?.readText().orEmpty(),
                throwableClass = throwable?.javaClass?.name ?: "unavailable",
                throwableMessage = throwable?.message ?: "unavailable",
            )
        }

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
            unsafeDevBypassPromptLengthGate: Boolean = false,
        ): String {
            check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
                "editable prompt smoke is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            check(modelPath.isNotBlank()) { "modelPath is required" }
            val rawValidation = when (promptValidationMode) {
                NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8InternalIntent(prompt)
                NpuDiagnosticPromptValidator.UTF8_HIDDEN_EXPERIMENTAL_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental(prompt)
                NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(prompt)
                else -> NpuDiagnosticPromptValidator.validateAsciiDiagnostic(prompt)
            }
            val validation = promptLengthGateBypassedValidation(
                validation = rawValidation,
                unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
            )
            val nativePromptInputLimitMode = if (
                unsafeDevBypassPromptLengthGate &&
                validation.promptInputLimitMode == NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_INPUT_LIMIT_MODE
            ) {
                UNSAFE_DEV_BYPASS_HIDDEN_TEMPLATE_INPUT_LIMIT_MODE
            } else {
                validation.promptInputLimitMode
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
                promptInputLimitMode = nativePromptInputLimitMode,
                maxOutputTokens = maxOutputTokens,
            )
            return "qairt244_editable_prompt_smoke_v1 runId=$runId result=success actual_prompt=$normalizedPrompt normalized_prompt=$normalizedPrompt output=$output"
        }

        @JvmStatic
        fun runPersistentProbe(
            context: Context,
            modelPath: String,
            runId: String,
            prompt: String,
            maxOutputTokens: Int,
            runCount: Int,
            holderKey: String,
            nativeProbeMode: String,
            promptValidationMode: String = NpuDiagnosticPromptValidator.ASCII_DIAGNOSTIC_MODE,
            unsafeDevBypassPromptLengthGate: Boolean = false,
        ): Qairt244PersistentProbeResult {
            check(
                BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors ||
                    (
                        BuildConfig.CURRENT_FLAVOR == "trueEngineNpuProbe" &&
                            ((BuildConfig.TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED &&
                                nativeProbeMode == NPU_TRUE_ENGINE_ENTRYPOINT_NATIVE_PROBE_MODE) ||
                                (BuildConfig.TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED &&
                                    nativeProbeMode == NPU_TRUE_ENGINE_MODEL_ASSETS_NATIVE_PROBE_MODE))
                    ),
            ) {
                "persistent custom JNI probe is debug hidden-experimental only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            check(modelPath.isNotBlank()) { "modelPath is required" }
            check(holderKey.isNotBlank()) { "holderKey is required" }
            check(nativeProbeMode.isNotBlank()) { "nativeProbeMode is required" }
            check(runCount in 1..100) { "runCount must be 1..100" }
            val rawValidation = when (promptValidationMode) {
                NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8InternalIntent(prompt)
                NpuDiagnosticPromptValidator.UTF8_HIDDEN_EXPERIMENTAL_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental(prompt)
                NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE ->
                    NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(prompt)
                else -> NpuDiagnosticPromptValidator.validateAsciiDiagnostic(prompt)
            }
            val validation = promptLengthGateBypassedValidation(
                validation = rawValidation,
                unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
            )
            val nativePromptInputLimitMode = if (
                unsafeDevBypassPromptLengthGate &&
                validation.promptInputLimitMode == NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_INPUT_LIMIT_MODE
            ) {
                UNSAFE_DEV_BYPASS_HIDDEN_TEMPLATE_INPUT_LIMIT_MODE
            } else {
                validation.promptInputLimitMode
            }
            check(validation.isValid) {
                "persistent custom JNI probe rejected before native execution: reasonCode=${validation.reasonCode}"
            }

            val appContext = context.applicationContext
            val resultFile = appContext.filesDir.resolve(PERSISTENT_PROBE_RESULT_FILE_NAME)
            val diagFile = appContext.filesDir.resolve(PERSISTENT_PROBE_DIAG_FILE_NAME)
            resultFile.delete()
            diagFile.delete()
            val nativeResult = runCatching {
                if (nativeProbeMode == "editable_engine_create_only_minimal") {
                    nativeRunEditableEngineCreateOnlyMinimal(
                        modelPath = modelPath,
                        nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
                        cacheDir = appContext.cacheDir.absolutePath,
                        resultPath = resultFile.absolutePath,
                        diagPath = diagFile.absolutePath,
                        prompt = validation.normalizedPrompt,
                        promptInputLimitMode = nativePromptInputLimitMode,
                        maxOutputTokens = maxOutputTokens,
                    )
                } else {
                    nativeRunPersistentProbe(
                        modelPath = modelPath,
                        nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
                        cacheDir = appContext.cacheDir.absolutePath,
                        resultPath = resultFile.absolutePath,
                        diagPath = diagFile.absolutePath,
                        prompt = validation.normalizedPrompt,
                        promptInputLimitMode = nativePromptInputLimitMode,
                        maxOutputTokens = maxOutputTokens,
                        runCount = runCount,
                        holderKey = holderKey,
                        nativeProbeMode = nativeProbeMode,
                    )
                }
            }
            val throwable = nativeResult.exceptionOrNull()
            return Qairt244PersistentProbeResult(
                runId = runId,
                nativeReturn = nativeResult.getOrDefault(""),
                resultText = resultFile.takeIf { it.exists() }?.readText().orEmpty(),
                diagText = diagFile.takeIf { it.exists() }?.readText().orEmpty(),
                throwableClass = throwable?.javaClass?.name ?: "unavailable",
                throwableMessage = throwable?.message ?: "unavailable",
            )
        }

        private fun promptLengthGateBypassedValidation(
            validation: NpuDiagnosticPromptValidator.Result,
            unsafeDevBypassPromptLengthGate: Boolean,
        ): NpuDiagnosticPromptValidator.Result {
            if (!unsafeDevBypassPromptLengthGate || !isHiddenPromptLengthGateBlock(validation)) return validation
            return validation.copy(isValid = true)
        }

        private fun isHiddenPromptLengthGateBlock(validation: NpuDiagnosticPromptValidator.Result): Boolean =
            validation.reasonCode == "too_long" &&
                validation.promptInputCodePointLimit == NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH &&
                validation.promptInputLimitMode == NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_INPUT_LIMIT_MODE

        private const val UNSAFE_DEV_BYPASS_HIDDEN_TEMPLATE_INPUT_LIMIT_MODE =
            "unsafe_dev_bypass_hidden_template_experiment"

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

        @JvmStatic
        private external fun nativeRunPersistentProbe(
            modelPath: String,
            nativeLibraryDir: String,
            cacheDir: String,
            resultPath: String,
            diagPath: String,
            prompt: String,
            promptInputLimitMode: String,
            maxOutputTokens: Int,
            runCount: Int,
            holderKey: String,
            nativeProbeMode: String,
        ): String

        @JvmStatic
        private external fun nativeRunEditableEngineCreateOnlyMinimal(
            modelPath: String,
            nativeLibraryDir: String,
            cacheDir: String,
            resultPath: String,
            diagPath: String,
            prompt: String,
            promptInputLimitMode: String,
            maxOutputTokens: Int,
        ): String

        @JvmStatic
        private external fun nativeCreateStandardRouteAdapterHolder(
            modelPath: String,
            nativeLibraryDir: String,
            cacheDir: String,
            maxTokens: Int,
        ): String

        @JvmStatic
        private external fun nativeRunStandardRouteAdapterHolderOnce(
            holderId: String,
            prompt: String,
            maxOutputTokens: Int,
        ): String

        @JvmStatic
        private external fun nativeCloseStandardRouteAdapterHolder(
            holderId: String,
            reason: String,
        ): String

        @JvmStatic
        private external fun nativeGetStandardRouteAdapterHolderDiagnostics(
            holderId: String,
        ): String
    }
}

internal data class Qairt244PersistentProbeResult(
    val runId: String,
    val nativeReturn: String,
    val resultText: String,
    val diagText: String,
    val throwableClass: String,
    val throwableMessage: String,
)
