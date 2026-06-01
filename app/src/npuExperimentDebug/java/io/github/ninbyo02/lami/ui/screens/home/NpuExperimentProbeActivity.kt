package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig

class NpuExperimentProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("run_backend_npu_attach_probe", false) == true) {
            NpuExperimentProbeLogger.runBackendNpuAttachProbe(
                context = applicationContext,
                runId = intent?.getStringExtra("run_id").orEmpty(),
                phase = intent?.getStringExtra("phase").orEmpty(),
                modelPath = intent?.getStringExtra("model_path"),
                engineConfigVariant = intent?.getStringExtra("engine_config_variant"),
                engineInitializeOptIn = intent?.getBooleanExtra("run_engine_initialize_dry_run", false) == true,
                engineInitializeDiagnosticFilesClearedBeforeRun = intent?.getBooleanExtra("diagnostic_files_cleared_before_run", false) == true,
            )
            finish()
            return
        }
        if (intent?.getBooleanExtra("run_app_jni_smoke", false) == true) {
            NpuExperimentProbeLogger.runAppJniSmokeOnly(
                context = applicationContext,
                runId = intent?.getStringExtra("run_id").orEmpty(),
            )
            finish()
            return
        }
        if (intent?.getBooleanExtra("runLowerLevelSingleTokenSmoke", false) == true) {
            NpuExperimentProbeLogger.runLowerLevelSingleTokenSmokeOnly(
                context = applicationContext,
                modelPath = intent?.getStringExtra("model_path").orEmpty(),
                runId = intent?.getStringExtra("run_id").orEmpty(),
            )
            finish()
            return
        }
        NpuExperimentProbeLogger.logSnapshot(
            context = applicationContext,
            runEngineInitializeDryRun = intent?.getBooleanExtra("run_engine_initialize_dry_run", false) == true,
            engineInitializeDryRunModelPath = intent?.getStringExtra("model_path"),
            engineInitializeDryRunRunId = intent?.getStringExtra("run_id"),
            engineInitializeDiagnosticFilesClearedBeforeRun = intent?.getBooleanExtra("diagnostic_files_cleared_before_run", false) == true,
        )
        finish()
    }
}

internal object NpuExperimentProbeLogger {
    private const val APP_JNI_SMOKE_FILE_NAME = "qairt244_app_jni_smoke.txt"
    private const val SINGLE_TOKEN_SMOKE_FILE_NAME = "qairt244_single_token_smoke_result.txt"
    private const val BACKEND_NPU_ATTACH_PROBE_LATEST_TXT = "backend_npu_attach_probe_latest.txt"
    private const val BACKEND_NPU_ATTACH_PROBE_LATEST_MD = "backend_npu_attach_probe_latest.md"

    fun runBackendNpuAttachProbe(
        context: android.content.Context,
        runId: String,
        phase: String,
        modelPath: String?,
        engineConfigVariant: String?,
        engineInitializeOptIn: Boolean,
        engineInitializeDiagnosticFilesClearedBeforeRun: Boolean,
    ) {
        val normalizedRunId = sanitizeProbeRunId(runId.ifBlank { System.currentTimeMillis().toString() })
        val normalizedPhase = normalizeBackendNpuAttachProbePhase(phase)
        val normalizedVariant = normalizeEngineConfigVariant(engineConfigVariant)
        val runEngineInitializeDryRun = BackendNpuAttachProbeReportFormatter.shouldRunEngineInitializeDryRun(
            phase = normalizedPhase,
            explicitOptIn = engineInitializeOptIn,
        )
        val snapshot = AcceleratorProbe.captureSnapshot(
            context = context.applicationContext,
            forceRefresh = true,
            engineInitializeDryRunOptIn = runEngineInitializeDryRun,
            engineInitializeDryRunModelPath = modelPath,
            engineInitializeDryRunRunId = normalizedRunId,
            engineConfigVariant = normalizedVariant,
            engineInitializeDiagnosticFilesClearedBeforeRun = engineInitializeDiagnosticFilesClearedBeforeRun,
        )
        val applicationInfoNativeLibraryDir = context.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }
        val hardResolvedNativeLibraryDir = snapshot.dispatchNativeLibraryDir?.takeIf { it.isNotBlank() }
        val selectedNativeLibraryDir = applicationInfoNativeLibraryDir ?: hardResolvedNativeLibraryDir
        val trimmedModelPath = modelPath?.trim()?.takeIf { it.isNotBlank() }
        val request = BackendNpuAttachProbeReportRequest(
            runId = normalizedRunId,
            phase = normalizedPhase,
            engineInitializeOptIn = runEngineInitializeDryRun,
            processAliveAfterProbe = "alive-inside-app-before-script-pidof",
            engineConfigVariant = normalizedVariant,
            engineConfigCacheDir = engineConfigCacheDirForVariant(normalizedVariant, context),
            engineConfigMaxNumTokens = engineConfigMaxNumTokensForVariant(normalizedVariant),
            modelCanonicalPath = trimmedModelPath?.let { runCatching { java.io.File(it).canonicalPath }.getOrNull() }.orDashForProbe(),
            modelPathVariant = modelPathVariantForProbe(trimmedModelPath),
            nativeLibraryDirVariant = when {
                selectedNativeLibraryDir == applicationInfoNativeLibraryDir -> "applicationInfo.nativeLibraryDir"
                selectedNativeLibraryDir == hardResolvedNativeLibraryDir -> "hard-resolved-nativeLibraryDir"
                else -> "-"
            },
            applicationInfoNativeLibraryDir = applicationInfoNativeLibraryDir.orDashForProbe(),
            contextApplicationInfoNativeLibraryDir = applicationInfoNativeLibraryDir.orDashForProbe(),
            hardResolvedNativeLibraryDir = hardResolvedNativeLibraryDir.orDashForProbe(),
        )
        val txt = BackendNpuAttachProbeReportFormatter.formatText(snapshot, request)
        val md = BackendNpuAttachProbeReportFormatter.formatMarkdown(snapshot, request)
        val txtFile = context.filesDir.resolve("backend_npu_attach_probe_${normalizedRunId}.txt")
        val mdFile = context.filesDir.resolve("backend_npu_attach_probe_${normalizedRunId}.md")
        txtFile.writeText(txt)
        mdFile.writeText(md)
        context.filesDir.resolve(BACKEND_NPU_ATTACH_PROBE_LATEST_TXT).writeText(txt)
        context.filesDir.resolve(BACKEND_NPU_ATTACH_PROBE_LATEST_MD).writeText(md)
        Log.i(
            LOG_TAG,
            "Backend.NPU attach probe written runId=$normalizedRunId phase=$normalizedPhase txt=${txtFile.absolutePath} md=${mdFile.absolutePath}",
        )
    }

    fun runAppJniSmokeOnly(
        context: android.content.Context,
        runId: String,
    ) {
        val outputFile = context.filesDir.resolve(APP_JNI_SMOKE_FILE_NAME)
        val result = runCatching {
            check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
                "app-jni-smoke is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            val smokeClass = Class.forName(
                "io.github.ninbyo02.lami.ui.screens.home.Qairt244AppJniSmoke",
            )
            val runMethod = smokeClass.getMethod(
                "run",
                android.content.Context::class.java,
                String::class.java,
            )
            runMethod.invoke(null, context.applicationContext, runId) as String
        }.getOrElse { throwable ->
            "qairt244_app_jni_smoke_v1 kotlin failure class=${throwable.javaClass.name} message=${throwable.message ?: "-"}"
        }
        Log.e("QAIRT244_SMOKE", result)
        outputFile.writeText(result + "\n")
    }

    fun runLowerLevelSingleTokenSmokeOnly(
        context: android.content.Context,
        modelPath: String,
        runId: String,
    ) {
        val outputFile = context.filesDir.resolve(SINGLE_TOKEN_SMOKE_FILE_NAME)
        val result = runCatching {
            check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
                "lower-level single-token smoke is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            val smokeClass = Class.forName(
                "io.github.ninbyo02.lami.ui.screens.home.Qairt244LowerLevelSingleTokenSmoke",
            )
            val runMethod = smokeClass.getMethod(
                "run",
                android.content.Context::class.java,
                String::class.java,
                String::class.java,
            )
            runMethod.invoke(null, context.applicationContext, modelPath, runId) as String
        }.getOrElse { throwable ->
            "qairt244_lower_level_single_token_smoke_v1 kotlin failure class=${throwable.javaClass.name} message=${throwable.message ?: "-"}"
        }
        outputFile.appendText("kotlin_result=$result\n")
    }

    fun logSnapshot(
        context: android.content.Context,
        runEngineInitializeDryRun: Boolean = false,
        engineInitializeDryRunModelPath: String? = null,
        engineInitializeDryRunRunId: String? = null,
        engineInitializeDiagnosticFilesClearedBeforeRun: Boolean = false,
    ) {
        val snapshot = AcceleratorProbe.captureSnapshot(
            context = context.applicationContext,
            forceRefresh = true,
            engineInitializeDryRunOptIn = runEngineInitializeDryRun,
            engineInitializeDryRunModelPath = engineInitializeDryRunModelPath,
            engineInitializeDryRunRunId = engineInitializeDryRunRunId,
            engineInitializeDiagnosticFilesClearedBeforeRun = engineInitializeDiagnosticFilesClearedBeforeRun,
        )
        val dispatchLine =
            "Dispatch Runtime Compatibility: " +
                "current flavor=${snapshot.currentFlavor ?: "unknown"}; " +
                "applicationId=${snapshot.applicationId ?: "unknown"}; " +
                "nativeLibraryDir=${snapshot.dispatchNativeLibraryDir ?: "unknown"}; " +
                "nativeLibraryDir exists=${snapshot.dispatchNativeLibraryDirExists ?: "unknown"}; " +
                "dispatch runtime present in nativeLibraryDir=${snapshot.dispatchRuntimePresentInFlavor ?: "unknown"}; " +
                "dispatch runtime file path=${snapshot.dispatchRuntimeFilePath ?: "unknown"}; " +
                "dispatch runtime file length=${snapshot.dispatchRuntimeFileLength ?: "unknown"}; " +
                "dispatch runtime sha256=${snapshot.dispatchRuntimeSha256 ?: "unknown"}; " +
                "expected sha256 match=${snapshot.dispatchRuntimeExpectedSha256Match ?: "unknown"}; " +
                "dispatch runtime build id=${snapshot.dispatchRuntimeBuildId ?: "unknown"}; " +
                "ABI compatibility=${snapshot.dispatchRuntimeAbiCompatibility ?: "unknown"}; " +
                "load policy=diagnostic-only; no System.loadLibrary; no Backend.NPU apply"
        val runtimeVersionLine =
            "LiteRT-LM Runtime Version: " +
                "current flavor=${snapshot.currentFlavor ?: "unknown"}; " +
                "resolved litertlm expected version=${snapshot.liteRtLmExpectedVersion ?: "unknown"}; " +
                "liblitertlm_jni.so build id=${snapshot.liteRtLmJniBuildId ?: "unknown"}; " +
                "libLiteRt.so present=${snapshot.liteRtSoPresent ?: "unknown"}; " +
                "libLiteRt.so build id=${snapshot.liteRtBuildId ?: "unknown"}; " +
                "dispatch runtime build id=${snapshot.dispatchRuntimeBuildId ?: "unknown"}; " +
                "comparison Lami 0.11.0=${snapshot.liteRtLmRuntimeComparisonToLami011 ?: "unknown"}; " +
                "comparison Maven 0.10.0=${snapshot.liteRtLmRuntimeComparisonToMaven010 ?: "unknown"}; " +
                "comparison Gallery SM8750=${snapshot.liteRtLmRuntimeComparisonToGallerySm8750 ?: "unknown"}; " +
                "runtime stack note=${snapshot.liteRtLmRuntimeStackNote ?: "unknown"}"
        val galleryStackLine =
            "Gallery Stack Runtime Compatibility: " +
                "current flavor=${snapshot.currentFlavor ?: "unknown"}; " +
                "applicationId=${snapshot.applicationId ?: "unknown"}; " +
                "nativeLibraryDir=${snapshot.dispatchNativeLibraryDir ?: "unknown"}; " +
                "Gallery stack present=${snapshot.galleryStackExpectedBuildIdMatch ?: false}; " +
                "liblitertlm_jni.so build id=${snapshot.liteRtLmJniBuildId ?: "unknown"}; " +
                "libLiteRt.so build id=${snapshot.liteRtBuildId ?: "unknown"}; " +
                "libLiteRtDispatch_Qualcomm.so build id=${snapshot.dispatchRuntimeBuildId ?: "unknown"}; " +
                "libQnnSystem.so build id=${snapshot.galleryStackQnnSystemBuildId ?: "unknown"}; " +
                "libQnnHtp.so build id=${snapshot.galleryStackQnnHtpBuildId ?: "unknown"}; " +
                "libQnnHtpV79Stub.so build id=${snapshot.galleryStackQnnHtpV79StubBuildId ?: "unknown"}; " +
                "expected Gallery build id match=${snapshot.galleryStackExpectedBuildIdMatch ?: false}; " +
                "standard leakage=false (verified by APK script); " +
                "npuExperiment leakage=false (verified by APK script); " +
                "load policy=diagnostic-only; no automatic Engine.initialize; no Conversation; no generate"
        val galleryJavaNativeApiLine =
            "Gallery Stack Java/Native API Compatibility: " +
                "current flavor=${snapshot.currentFlavor ?: "unknown"}; " +
                "resolved expected Java API version=${snapshot.galleryStackJavaApiExpectedVersion ?: "unknown"}; " +
                "Java side nativeCreateEngine descriptor=${snapshot.galleryStackJavaNativeCreateEngineDescriptor ?: "unknown"}; " +
                "expected Gallery JNI descriptor=${snapshot.galleryStackExpectedJniNativeCreateEngineDescriptor ?: "unknown"}; " +
                "descriptor match=${snapshot.galleryStackNativeCreateEngineDescriptorMatch ?: "unknown"}; " +
                "EngineConfig constructor selected=${snapshot.galleryStackEngineConfigSelectedConstructor ?: "unknown"}; " +
                "EngineConfig constructor expected=${snapshot.galleryStackEngineConfigExpectedConstructor ?: "unknown"}; " +
                "EngineConfig constructor match=${snapshot.galleryStackEngineConfigConstructorMatch ?: "unknown"}; " +
                "liblitertlm_jni.so build id=${snapshot.liteRtLmJniBuildId ?: "unknown"}; " +
                "libLiteRt.so build id=${snapshot.liteRtBuildId ?: "unknown"}; " +
                "libLiteRtDispatch_Qualcomm.so build id=${snapshot.dispatchRuntimeBuildId ?: "unknown"}; " +
                "note=${snapshot.galleryStackJavaNativeApiCompatibilityNote ?: "unknown"}"
        val customBuildStackLine =
            "Custom Build Stack Compatibility: " +
                "current flavor=${snapshot.currentFlavor ?: "unknown"}; " +
                "applicationId=${snapshot.applicationId ?: "unknown"}; " +
                "nativeLibraryDir=${snapshot.dispatchNativeLibraryDir ?: "unknown"}; " +
                "custom stack present=${snapshot.customStackExpectedBuildIdMatch ?: false}; " +
                "liblitertlm_jni.so build id=${snapshot.liteRtLmJniBuildId ?: "unknown"}; " +
                "libLiteRt.so build id=${snapshot.liteRtBuildId ?: "unknown"}; " +
                "libLiteRtDispatch_Qualcomm.so build id=${snapshot.dispatchRuntimeBuildId ?: "unknown"}; " +
                "libLiteRtCompilerPlugin_Qualcomm.so build id=${snapshot.customStackCompilerPluginBuildId ?: "unknown"}; " +
                "libGemmaModelConstraintProvider.so build id=${snapshot.customStackGemmaModelConstraintProviderBuildId ?: "unknown"}; " +
                "expected custom build id match=${snapshot.customStackExpectedBuildIdMatch ?: false}; " +
                "LiteRtDispatchGetApi export expected=true; " +
                "dependency Java API version expected=${snapshot.liteRtLmExpectedVersion ?: "unknown"}; " +
                "EngineConfig constructor expected=EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String); " +
                "load policy=diagnostic-only; Engine.initialize explicit opt-in only; no Conversation; no generateResponse"
        val instantiateLine =
            "Backend.NPU Instantiate Probe: " +
                "enabled=${snapshot.backendNpuInstantiateProbeEnabled ?: "unknown"}; " +
                "reason if skipped=${snapshot.backendNpuInstantiateProbeSkipReason ?: "none"}; " +
                "constructor=${snapshot.backendNpuInstantiateConstructor ?: "Backend.NPU(String)"}; " +
                "nativeLibraryDir argument=${snapshot.backendNpuInstantiateNativeLibraryDirArgument ?: "unknown"}; " +
                "instantiate result=${snapshot.backendNpuInstantiateResult ?: "unknown"}; " +
                "object class=${snapshot.backendNpuInstantiateObjectClass ?: "-"}; " +
                "exception class=${snapshot.backendNpuInstantiateExceptionClass ?: "-"}; " +
                "exception message=${snapshot.backendNpuInstantiateExceptionMessage ?: "-"}; " +
                "root cause=${snapshot.backendNpuInstantiateRootCause ?: "-"}; " +
                "cause chain=${snapshot.backendNpuInstantiateCauseChain ?: "-"}; " +
                "warning=${snapshot.backendNpuInstantiateWarning ?: "instantiate-only; object not passed to engine; no inference"}"
        val attachDryRunLine =
            "Backend.NPU Attach Dry-Run Probe: " +
                "enabled=${snapshot.backendNpuAttachDryRunEnabled ?: "unknown"}; " +
                "skipped reason=${snapshot.backendNpuAttachDryRunSkipReason ?: "none"}; " +
                "npu object class=${snapshot.backendNpuAttachDryRunNpuObjectClass ?: "-"}; " +
                "target builder candidates=${snapshot.backendNpuAttachDryRunTargetBuilderCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none/unknown"}; " +
                "setter candidates=${snapshot.backendNpuAttachDryRunSetterCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none/unknown"}; " +
                "selected setter=${snapshot.backendNpuAttachDryRunSelectedSetter ?: "-"}; " +
                "setter invoke result=${snapshot.backendNpuAttachDryRunSetterInvokeResult ?: "unknown"}; " +
                "build invoked=${snapshot.backendNpuAttachDryRunBuildInvoked ?: "no"}; " +
                "build result=${snapshot.backendNpuAttachDryRunBuildResult ?: "skipped"}; " +
                "exception class=${snapshot.backendNpuAttachDryRunExceptionClass ?: "-"}; " +
                "exception message=${snapshot.backendNpuAttachDryRunExceptionMessage ?: "-"}; " +
                "root cause=${snapshot.backendNpuAttachDryRunRootCause ?: "-"}; " +
                "cause chain=${snapshot.backendNpuAttachDryRunCauseChain ?: "-"}; " +
                "warning=${snapshot.backendNpuAttachDryRunWarning ?: "attach-dry-run only; no Engine; no Conversation; no inference"}; " +
                "note=${snapshot.backendNpuAttachDryRunNote ?: "This setter belongs to MediaPipe LlmInference.Backend enum path and is not assignable from LiteRT-LM Backend.NPU."}"
        val apiInventoryLine =
            "LiteRT-LM NPU API Inventory: " +
                "enabled=${snapshot.liteRtLmNpuApiInventoryEnabled ?: "unknown"}; " +
                "skipped reason=${snapshot.liteRtLmNpuApiInventorySkipReason ?: "none"}; " +
                "classes=${snapshot.liteRtLmNpuApiClassInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "EngineConfig constructors=${snapshot.engineConfigConstructorInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "assignability=${snapshot.liteRtLmNpuApiAssignability.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "backend property=${snapshot.engineConfigBackendPropertyInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "copy=${snapshot.engineConfigCopyMethodInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "componentN=${snapshot.engineConfigComponentMethodInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "json=${snapshot.engineConfigJsonMethodInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}"
        val engineConfigDryBuildLine =
            "EngineConfig NPU Dry-Build Probe: " +
                "enabled=${snapshot.engineConfigNpuDryBuildEnabled ?: "unknown"}; " +
                "skipped reason=${snapshot.engineConfigNpuDryBuildSkipReason ?: "none"}; " +
                "selected constructor=${snapshot.engineConfigNpuDryBuildSelectedConstructor ?: "-"}; " +
                "constructor args summary=${snapshot.engineConfigNpuDryBuildConstructorArgsSummary ?: "-"}; " +
                "npu backend object class=${snapshot.engineConfigNpuDryBuildNpuBackendObjectClass ?: "-"}; " +
                "result=${snapshot.engineConfigNpuDryBuildResult ?: "unknown"}; " +
                "created object class=${snapshot.engineConfigNpuDryBuildCreatedObjectClass ?: "-"}; " +
                "backend getter result class=${snapshot.engineConfigNpuDryBuildBackendGetterResultClass ?: "-"}; " +
                "exception class=${snapshot.engineConfigNpuDryBuildExceptionClass ?: "-"}; " +
                "exception message=${snapshot.engineConfigNpuDryBuildExceptionMessage ?: "-"}; " +
                "root cause=${snapshot.engineConfigNpuDryBuildRootCause ?: "-"}; " +
                "cause chain=${snapshot.engineConfigNpuDryBuildCauseChain ?: "-"}; " +
                "warning=${snapshot.engineConfigNpuDryBuildWarning ?: "config-only; not passed to Engine; no inference"}"
        val connectionCandidateLine =
            "Backend.NPU Connection Candidate: " +
                "preferredBackend enum path=${snapshot.backendNpuConnectionPreferredBackendEnumPath ?: "unknown"}; " +
                "reason=${snapshot.backendNpuConnectionPreferredBackendEnumReason ?: "unknown"}; " +
                "EngineConfig backend path=${snapshot.backendNpuConnectionEngineConfigBackendPath ?: "unknown"}; " +
                "Engine initialize path=${snapshot.backendNpuConnectionEngineInitializePath ?: "not attempted"}; " +
                "recommended next phase=${snapshot.backendNpuConnectionRecommendedNextPhase ?: "unknown"}"
        val engineApiInventoryLine =
            "Engine API Inventory: " +
                "enabled=${snapshot.engineApiInventoryEnabled ?: "unknown"}; " +
                "skipped reason=${snapshot.engineApiInventorySkipReason ?: "none"}; " +
                "class found=${snapshot.engineApiClassFound ?: "unknown"}; " +
                "constructors=${snapshot.engineApiConstructors.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "static/companion factory candidates=${snapshot.engineApiStaticFactoryCandidates.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "initialize method candidates=${snapshot.engineApiInitializeMethodCandidates.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "close/dispose method candidates=${snapshot.engineApiCloseDisposeMethodCandidates.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "create method candidates=${snapshot.engineApiCreateMethodCandidates.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}"
        val engineInitializeDryRunLine =
            "Engine Initialize Dry-Run Probe: " +
                "enabled=${snapshot.engineInitializeDryRunEnabled ?: "unknown"}; " +
                "runId=${snapshot.engineInitializeDryRunRunId ?: "-"}; " +
                "skipped reason=${snapshot.engineInitializeDryRunSkipReason ?: "none"}; " +
                "explicit opt-in=${snapshot.engineInitializeDryRunExplicitOptIn ?: false}; " +
                "model path=${snapshot.engineInitializeDryRunModelPath ?: "-"}; " +
                "model kind=${snapshot.engineInitializeDryRunModelKind ?: "unknown"}; " +
                "model file exists=${snapshot.engineInitializeDryRunModelFileExists ?: "unknown"}; " +
                "model file length=${snapshot.engineInitializeDryRunModelFileLength ?: "unknown"}; " +
                "model file canRead=${snapshot.engineInitializeDryRunModelFileCanRead ?: "unknown"}; " +
                "model parent exists=${snapshot.engineInitializeDryRunModelFileParentExists ?: "unknown"}; " +
                "model parent list count=${snapshot.engineInitializeDryRunModelFileParentListCount ?: "unknown"}; " +
                "model parent list sample=${snapshot.engineInitializeDryRunModelFileParentListSample.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "-"}; " +
                "nativeLibraryDir=${snapshot.engineInitializeDryRunNativeLibraryDir ?: "unknown"}; " +
                "Backend.NPU object class=${snapshot.engineInitializeDryRunBackendNpuObjectClass ?: "-"}; " +
                "EngineConfig object class=${snapshot.engineInitializeDryRunEngineConfigObjectClass ?: "-"}; " +
                "selected Engine constructor/factory=${snapshot.engineInitializeDryRunSelectedEngineConstructorOrFactory ?: "-"}; " +
                "selected initialize method=${snapshot.engineInitializeDryRunSelectedInitializeMethod ?: "-"}; " +
                "last stage=${snapshot.engineInitializeDryRunLastStage ?: "-"}; " +
                "constructor invoked=${snapshot.engineInitializeDryRunConstructorInvoked ?: "no"}; " +
                "constructor returned=${snapshot.engineInitializeDryRunConstructorReturned ?: "no"}; " +
                "initialize invoked=${snapshot.engineInitializeDryRunInitializeInvoked ?: "no"}; " +
                "initialize returned=${snapshot.engineInitializeDryRunInitializeReturned ?: "no"}; " +
                "initialize result=${snapshot.engineInitializeDryRunInitializeResult ?: "skipped"}; " +
                "elapsed ms=${snapshot.engineInitializeDryRunElapsedMs ?: "-"}; " +
                "exception class=${snapshot.engineInitializeDryRunExceptionClass ?: "-"}; " +
                "exception message=${snapshot.engineInitializeDryRunExceptionMessage ?: "-"}; " +
                "root cause=${snapshot.engineInitializeDryRunRootCause ?: "-"}; " +
                "cause chain=${snapshot.engineInitializeDryRunCauseChain ?: "-"}; " +
                "UnsatisfiedLinkError detected=${snapshot.engineInitializeDryRunUnsatisfiedLinkErrorDetected ?: false}; " +
                "No usable Dispatch runtime found detected=${snapshot.engineInitializeDryRunNoUsableDispatchRuntimeDetected ?: false}; " +
                "Failed to initialize Dispatch API detected=${snapshot.engineInitializeDryRunFailedToInitializeDispatchApiDetected ?: false}; " +
                "insufficient capabilities detected=${snapshot.engineInitializeDryRunInsufficientCapabilitiesDetected ?: false}; " +
                "version mismatch detected=${snapshot.engineInitializeDryRunVersionMismatchDetected ?: false}; " +
                "symbol mismatch detected=${snapshot.engineInitializeDryRunSymbolMismatchDetected ?: false}; " +
                "SIGABRT suspected=${snapshot.engineInitializeDryRunSigabrtSuspected ?: false}; " +
                "crash suspected=${snapshot.engineInitializeDryRunCrashSuspected ?: false}; " +
                "process alive after probe=${snapshot.engineInitializeDryRunProcessAliveAfterProbe ?: "unknown-script-checks-pidof"}; " +
                "stale snapshot suspected=${snapshot.engineInitializeDryRunStaleSnapshotSuspected ?: false}; " +
                "diagnostic files cleared before run=${snapshot.engineInitializeDryRunDiagnosticFilesClearedBeforeRun ?: false}; " +
                "close invoked=${snapshot.engineInitializeDryRunCloseInvoked ?: "no"}; " +
                "close result=${snapshot.engineInitializeDryRunCloseResult ?: "skipped"}; " +
                "diagnostic file=${snapshot.engineInitializeDryRunDiagnosticFilePath ?: "unknown"}; " +
                "warning=${snapshot.engineInitializeDryRunWarning ?: "initialize-only; no Conversation; no generateResponse; not wired to app inference"}"
        val safetyLine =
            "NPU safety status: " +
                "selectedPath=${snapshot.qnnNpuSelectedPath ?: "unknown"}; " +
                "QNN/NPU attempted=${if (snapshot.qnnNpuAttempted) "yes" else "no"}; " +
                "fallbackPath=${snapshot.qnnNpuFallbackPath ?: "-"}; " +
                "NPU apply status=disabled / probe-only"

        listOf(dispatchLine, runtimeVersionLine, galleryStackLine, galleryJavaNativeApiLine, customBuildStackLine, instantiateLine, attachDryRunLine, apiInventoryLine, engineConfigDryBuildLine, connectionCandidateLine, engineApiInventoryLine, engineInitializeDryRunLine, safetyLine).forEach { line ->
            Log.i(LOG_TAG, line)
        }
        runCatching {
            context.filesDir.resolve("npu_experiment_probe.txt").writeText(
                listOf(dispatchLine, runtimeVersionLine, galleryStackLine, galleryJavaNativeApiLine, customBuildStackLine, instantiateLine, attachDryRunLine, apiInventoryLine, engineConfigDryBuildLine, connectionCandidateLine, engineApiInventoryLine, engineInitializeDryRunLine, safetyLine).joinToString(separator = "\n", postfix = "\n"),
            )
        }.onFailure { throwable ->
            Log.e(LOG_TAG, "Failed to write probe result: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private fun normalizeBackendNpuAttachProbePhase(phase: String): String =
        when (phase.trim().lowercase()) {
            BackendNpuAttachProbeReportFormatter.PHASE_ENGINE_INITIALIZE,
            "initialize",
            "engine-init",
            "engine_init",
            -> BackendNpuAttachProbeReportFormatter.PHASE_ENGINE_INITIALIZE
            BackendNpuAttachProbeReportFormatter.PHASE_CONVERSATION,
            "conversation-create",
            "conversation_create",
            -> BackendNpuAttachProbeReportFormatter.PHASE_CONVERSATION
            BackendNpuAttachProbeReportFormatter.PHASE_ONE_TOKEN_DECODE,
            "decode",
            "one-token",
            "one_token",
            -> BackendNpuAttachProbeReportFormatter.PHASE_ONE_TOKEN_DECODE
            else -> BackendNpuAttachProbeReportFormatter.PHASE_INVENTORY
        }

    private fun normalizeEngineConfigVariant(value: String?): String =
        when (value?.trim()?.lowercase(java.util.Locale.US)) {
            "cache-files" -> "cache-files"
            "cache-cache" -> "cache-cache"
            "max128" -> "max128"
            "max32" -> "max32"
            "backend-only" -> "backend-only"
            "backend-null-modalities" -> "backend-null-modalities"
            else -> "default"
        }

    private fun engineConfigCacheDirForVariant(
        variant: String,
        context: android.content.Context,
    ): String =
        when (variant) {
            "cache-files" -> context.filesDir.resolve("backend_npu_attach_probe_cache").absolutePath
            "cache-cache" -> context.cacheDir.absolutePath
            else -> "null"
        }

    private fun engineConfigMaxNumTokensForVariant(variant: String): String =
        when (variant) {
            "max128" -> "128"
            "max32" -> "32"
            else -> "null"
        }

    private fun modelPathVariantForProbe(modelPath: String?): String =
        when {
            modelPath == null -> "-"
            modelPath.startsWith("/data/user/0/") -> "/data/user/0"
            modelPath.startsWith("/data/data/") -> "/data/data"
            else -> "as-requested"
        }

    private fun String?.orDashForProbe(): String = this?.takeIf { it.isNotBlank() } ?: "-"

    private fun sanitizeProbeRunId(value: String): String =
        value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank {
            System.currentTimeMillis().toString()
        }

    private const val LOG_TAG = "NpuExperimentProbe"
}
