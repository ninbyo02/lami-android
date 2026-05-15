package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.local.QnnDelegateProbe
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

internal object AcceleratorProbe {
    private const val LOG_TAG = "AcceleratorProbe"
    private const val MAX_DELEGATE_CANDIDATE_COUNT = 12
    private const val MAX_ELF_NOTE_SCAN_BYTES = 1024 * 1024
    private const val EXTERNAL_QAIRT_STAGE_PATH = "/data/local/tmp/qairt"
    private const val EXTERNAL_QAIRT_QNN_NET_RUN_PATH = "/data/local/tmp/qairt/bin/qnn-net-run"
    private const val EXTERNAL_QAIRT_QNN_PLATFORM_VALIDATOR_PATH = "/data/local/tmp/qairt/bin/qnn-platform-validator"
    private const val EXTERNAL_QAIRT_VERIFIED_SDK_VERSION = "v2.46.0.260424121129"
    private const val EXTERNAL_QAIRT_VERIFIED_GPU_BACKEND_STATUS = "passed"
    private const val EXTERNAL_QAIRT_VERIFIED_DSP_CORE = "Hexagon Architecture V79"
    private const val EXTERNAL_QAIRT_VERIFIED_DSP_BACKEND_STATUS = "passed"
    private const val GALLERY_SM8750_DISPATCH_BUILD_ID = "643ad77b8ac2f54bd1b61e4133c77b3a"
    private const val GALLERY_SM8750_LITERT_BUILD_ID = "869121bd7f4b0b77fa581218117a5c14"
    private const val GALLERY_SM8750_LITERTLM_JNI_BUILD_ID = "76e4dccd9c5f9cba468d9cae7becfec0"
    private const val GALLERY_SM8750_DISPATCH_SHA256 = "92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777"
    private val dispatchApiLibraryNames = setOf(
        "libLiteRtDispatch_Qualcomm.so",
        "libLiteRtDispatchQualcomm.so",
        "libLiteRtDispatch.so",
        "liblitert_dispatch_qualcomm.so",
        "liblitert_dispatch.so",
    )
    private val qnnRuntimeLibraryNames = setOf(
        "libQnnSystem.so",
        "libQnnHtp.so",
        "libQnnHtpPrepare.so",
        "libQnnGpu.so",
        "libQnnDsp.so",
    )
    private val htpSkelStubLibraryNames = setOf(
        "libQnnHtpV79Skel.so",
        "libQnnHtpV79Stub.so",
        "libQnnHtpV75Skel.so",
        "libQnnHtpV75Stub.so",
        "libQnnHtpV73Skel.so",
        "libQnnHtpV73Stub.so",
        "libQnnHtpV69Skel.so",
        "libQnnHtpV69Stub.so",
        "libQnnHtpV68Skel.so",
        "libQnnHtpV68Stub.so",
        "libQnnDspV66Skel.so",
        "libQnnDspV66Stub.so",
    )
    private val DELEGATE_KEYWORDS = listOf(
        "delegate",
        "backend",
        "gpu",
        "cpu",
        "nnapi",
        "npu",
        "accelerator",
        "acceleration",
        "preferred",
        "hardware",
        "qnn",
        "htp",
        "dsp",
        "hexagon",
        "neural",
        "qualcomm",
    )

    @Volatile
    private var hasLogged = false

    @Volatile
    private var cachedSnapshot: AcceleratorProbeSnapshot? = null

    fun captureSnapshot(context: Context? = null, forceRefresh: Boolean = false): AcceleratorProbeSnapshot {
        if (!forceRefresh) {
            cachedSnapshot?.let { snapshot ->
                if (context == null || snapshot.npuNativeLibraryDir != null) return snapshot
            }
        }

        val snapshot = captureSnapshotUncached(context)
        cachedSnapshot = snapshot
        maybeLogOnce(snapshot)
        return snapshot
    }

    private fun captureSnapshotUncached(context: Context?): AcceleratorProbeSnapshot {
        // NPU/QNN/NNAPI はここでは安全な候補検出のみを行う。実適用は LocalStreamingRunner 側で行う。
        // この probe は EngineConfig/Engine を生成せず、診断情報のみ提供する。
        var probeError: String? = null
        val nnapiDevices = runCatching {
            fetchNnapiDeviceNamesSafely()
        }.getOrElse {
            probeError = it.javaClass.simpleName
            emptyList()
        }

        val gpuProbeResult = probeGpuInfoSafely()
        val delegateApiProbeResult = probeDelegateApiCandidatesSafely()
        val npuStageProbeResult = probeBackendNpuStageSafely()
        val npuRequirementsProbeResult = probeLiteRtLmNpuRequirementsSafely()
        val npuPackagedLibraryProbeResult = probePackagedNpuLibrariesSafely(
            context = context,
            officialVendor = npuRequirementsProbeResult.officialVendor,
        )
        val externalQairtStageProbeResult = probeExternalQairtStageSafely()
        val dispatchRuntimeCompatibility = probeDispatchRuntimeCompatibilitySafely(npuPackagedLibraryProbeResult)
        val backendNpuInstantiateProbeResult = probeBackendNpuInstantiateOnlySafely(
            context = context,
            packagedLibraries = npuPackagedLibraryProbeResult,
            dispatchRuntimeCompatibility = dispatchRuntimeCompatibility,
        )
        val backendNpuAttachDryRunProbeResult = probeBackendNpuAttachDryRunSafely(
            context = context,
            packagedLibraries = npuPackagedLibraryProbeResult,
            dispatchRuntimeCompatibility = dispatchRuntimeCompatibility,
            instantiateProbeResult = backendNpuInstantiateProbeResult,
            delegateApiProbeResult = delegateApiProbeResult,
        )
        val liteRtLmNpuApiInventoryProbeResult = probeLiteRtLmNpuApiInventorySafely(
            dispatchRuntimeCompatibility = dispatchRuntimeCompatibility,
            instantiateProbeResult = backendNpuInstantiateProbeResult,
        )
        val engineConfigNpuDryBuildProbeResult = probeEngineConfigNpuDryBuildSafely(
            context = context,
            packagedLibraries = npuPackagedLibraryProbeResult,
            dispatchRuntimeCompatibility = dispatchRuntimeCompatibility,
            instantiateProbeResult = backendNpuInstantiateProbeResult,
        )
        val backendNpuConnectionCandidateProbeResult = buildBackendNpuConnectionCandidate(
            apiInventory = liteRtLmNpuApiInventoryProbeResult,
            engineConfigDryBuild = engineConfigNpuDryBuildProbeResult,
        )
        val qnnNpuAttemptSnapshot = buildQualcommQnnNpuAttemptSnapshot(
            requirements = npuRequirementsProbeResult,
            packagedLibraries = npuPackagedLibraryProbeResult,
            delegateApiProbeResult = delegateApiProbeResult,
        )
        val qnnDelegateProbeResult = context?.let { QnnDelegateProbe.probe(it) }

        return AcceleratorProbeSnapshot(
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            deviceBoard = Build.BOARD,
            androidSdk = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            cpuCoreCount = runCatching { Runtime.getRuntime().availableProcessors() }.getOrNull(),
            cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull(),
            gpuVendor = gpuProbeResult.vendor,
            gpuRenderer = gpuProbeResult.renderer,
            gpuVersion = gpuProbeResult.version,
            nnapiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
            nnapiDeprecatedWarning = Build.VERSION.SDK_INT >= 35,
            nnapiDevices = nnapiDevices,
            probeSource = "Build+Runtime+NNAPIReflectionSafeStub",
            probeError = probeError,
            gpuProbeSource = gpuProbeResult.source,
            gpuProbeError = gpuProbeResult.error,
            delegateProbeSource = "reflection-safe",
            delegateProbeError = delegateApiProbeResult.error,
            delegateOptionCandidates = delegateApiProbeResult.optionCandidates,
            delegateBackendCandidates = delegateApiProbeResult.backendCandidates,
            delegateBackendEnumValues = delegateApiProbeResult.backendEnumValues,
            delegateBackendEnumProbeError = delegateApiProbeResult.backendEnumProbeError,
            delegatePreferredBackendSignatures = delegateApiProbeResult.preferredBackendSignatures,
            delegatePreferredBackendSignatureProbeError = delegateApiProbeResult.preferredBackendSignatureProbeError,
            delegateClassCandidates = delegateApiProbeResult.classCandidates,
            delegateSwitchingSupportedHint = delegateApiProbeResult.switchingSupportedHint,
            npuDelegateCandidates = delegateApiProbeResult.npuDelegateCandidates,
            npuBackendCandidates = delegateApiProbeResult.npuBackendCandidates,
            backendNpuClassCandidates = delegateApiProbeResult.backendNpuClassCandidates,
            backendNpuMethodCandidates = delegateApiProbeResult.backendNpuMethodCandidates,
            backendNpuConstructorSignatures = delegateApiProbeResult.backendNpuConstructorSignatures,
            backendNpuNativeLibraryDirRequired = delegateApiProbeResult.backendNpuNativeLibraryDirRequired,
            backendNpuProbeHint = delegateApiProbeResult.backendNpuProbeHint,
            backendNpuProbeError = delegateApiProbeResult.backendNpuProbeError,
            npuStageProbeSource = "reflection-probe-only",
            npuConstructorAvailable = npuStageProbeResult.constructorAvailable,
            npuStringConstructorAvailable = npuStageProbeResult.stringConstructorAvailable,
            npuNativeLibraryDirCandidate = npuStageProbeResult.nativeLibraryDirCandidate,
            npuStageProbeResult = npuStageProbeResult.result,
            npuStageProbeError = npuStageProbeResult.error,
            npuSocManufacturer = npuRequirementsProbeResult.socManufacturer,
            npuSocModel = npuRequirementsProbeResult.socModel,
            npuOfficialVendor = npuRequirementsProbeResult.officialVendor,
            npuOfficialSocSupport = npuRequirementsProbeResult.officialSocSupport,
            npuModelRequirement = npuRequirementsProbeResult.modelRequirement,
            npuRuntimeLibraryRequirement = npuRequirementsProbeResult.runtimeLibraryRequirement,
            npuDispatchLibraryRequirement = npuRequirementsProbeResult.dispatchLibraryRequirement,
            npuCliProofRequirement = npuRequirementsProbeResult.cliProofRequirement,
            npuReadinessSummary = buildNpuReadinessSummary(
                requirements = npuRequirementsProbeResult,
                packagedLibraries = npuPackagedLibraryProbeResult,
            ),
            npuNativeLibraryDir = npuPackagedLibraryProbeResult.nativeLibraryDir,
            npuPackagedLibraryCandidates = npuPackagedLibraryProbeResult.libraryCandidates,
            npuNativeLibraryDirExists = npuPackagedLibraryProbeResult.nativeLibraryDirExists,
            npuDispatchApiCandidates = npuPackagedLibraryProbeResult.dispatchApiCandidates,
            npuDispatchApiExactMatch = npuPackagedLibraryProbeResult.dispatchApiExactMatch,
            npuDispatchApiSelectedCandidate = npuPackagedLibraryProbeResult.dispatchApiSelectedCandidate,
            npuDispatchApiSearchDir = npuPackagedLibraryProbeResult.dispatchApiSearchDir,
            npuDispatchApiSearchError = npuPackagedLibraryProbeResult.dispatchApiSearchError,
            npuQnnRuntimeCandidates = npuPackagedLibraryProbeResult.qnnRuntimeCandidates,
            npuHtpSkelStubCandidates = npuPackagedLibraryProbeResult.htpSkelStubCandidates,
            npuV79SkelStubCandidates = npuPackagedLibraryProbeResult.v79SkelStubCandidates,
            npuVendorRuntimeLibraryStatus = npuPackagedLibraryProbeResult.vendorRuntimeLibraryStatus,
            npuDispatchLibraryStatus = npuPackagedLibraryProbeResult.dispatchLibraryStatus,
            qnnNpuAttemptRequested = qnnNpuAttemptSnapshot.requested,
            qnnNpuAttempted = qnnNpuAttemptSnapshot.attempted,
            qnnNpuAvailable = qnnNpuAttemptSnapshot.available,
            qnnNpuSelectedPath = qnnNpuAttemptSnapshot.selectedPath,
            qnnNpuFallbackPath = qnnNpuAttemptSnapshot.fallbackPath,
            qnnNpuAttemptStage = qnnNpuAttemptSnapshot.stage,
            qnnNpuAttemptErrorClass = qnnNpuAttemptSnapshot.errorClass,
            qnnNpuAttemptErrorMessage = qnnNpuAttemptSnapshot.errorMessage,
            qnnNpuAttemptEvidence = qnnNpuAttemptSnapshot.evidence,
            qnnDelegateCandidates = delegateApiProbeResult.qnnDelegateCandidates,
            nnapiDelegateCandidates = delegateApiProbeResult.nnapiDelegateCandidates,
            qnnDelegateProbeIsSm8750Likely = qnnDelegateProbeResult?.isSm8750Likely,
            qnnDelegateProbeSocHints = qnnDelegateProbeResult?.socHints.orEmpty(),
            qnnDelegateProbeClassFound = qnnDelegateProbeResult?.qnnClassFound,
            qnnDelegateProbeCreated = qnnDelegateProbeResult?.qnnDelegateCreated,
            qnnDelegateProbeHtpBackendRequested = qnnDelegateProbeResult?.htpBackendRequested,
            qnnDelegateProbeNativeLibraryDir = qnnDelegateProbeResult?.nativeLibraryDir,
            qnnDelegateProbeErrorClass = qnnDelegateProbeResult?.errorClass,
            qnnDelegateProbeErrorMessage = qnnDelegateProbeResult?.errorMessage,
            npuProbeHint = delegateApiProbeResult.npuProbeHint,
            npuProbeError = delegateApiProbeResult.npuProbeError,
            externalQairtStagePath = externalQairtStageProbeResult.stagePath,
            externalQairtStageStatus = externalQairtStageProbeResult.stageStatus,
            externalQairtQnnNetRunStatus = externalQairtStageProbeResult.qnnNetRunStatus,
            externalQairtQnnPlatformValidatorStatus = externalQairtStageProbeResult.qnnPlatformValidatorStatus,
            externalQairtQnnSdkVersion = externalQairtStageProbeResult.qnnSdkVersion,
            externalQairtGpuBackendStatus = externalQairtStageProbeResult.gpuBackendStatus,
            externalQairtDspCore = externalQairtStageProbeResult.dspCore,
            externalQairtDspBackendStatus = externalQairtStageProbeResult.dspBackendStatus,
            externalQairtNote = externalQairtStageProbeResult.note,
            currentFlavor = dispatchRuntimeCompatibility.currentFlavor,
            applicationId = dispatchRuntimeCompatibility.applicationId,
            dispatchNativeLibraryDir = dispatchRuntimeCompatibility.nativeLibraryDir,
            dispatchNativeLibraryDirExists = dispatchRuntimeCompatibility.nativeLibraryDirExists,
            dispatchRuntimePresentInFlavor = dispatchRuntimeCompatibility.dispatchRuntimePresent,
            dispatchRuntimeSource = dispatchRuntimeCompatibility.dispatchRuntimeSource,
            dispatchRuntimeFilePath = dispatchRuntimeCompatibility.dispatchRuntimeFilePath,
            dispatchRuntimeFileLength = dispatchRuntimeCompatibility.dispatchRuntimeFileLength,
            liteRtBuildId = dispatchRuntimeCompatibility.liteRtBuildId,
            liteRtLmJniBuildId = dispatchRuntimeCompatibility.liteRtLmJniBuildId,
            dispatchRuntimeBuildId = dispatchRuntimeCompatibility.dispatchRuntimeBuildId,
            dispatchRuntimeSha256 = dispatchRuntimeCompatibility.dispatchRuntimeSha256,
            dispatchRuntimeExpectedSha256Match = dispatchRuntimeCompatibility.dispatchRuntimeExpectedSha256Match,
            dispatchRuntimeAbiCompatibility = dispatchRuntimeCompatibility.abiCompatibility,
            backendNpuInstantiateProbeEnabled = backendNpuInstantiateProbeResult.enabled,
            backendNpuInstantiateProbeSkipReason = backendNpuInstantiateProbeResult.skipReason,
            backendNpuInstantiateNativeLibraryDirArgument = backendNpuInstantiateProbeResult.nativeLibraryDirArgument,
            backendNpuInstantiateConstructor = backendNpuInstantiateProbeResult.constructor,
            backendNpuInstantiateResult = backendNpuInstantiateProbeResult.result,
            backendNpuInstantiateObjectClass = backendNpuInstantiateProbeResult.objectClass,
            backendNpuInstantiateExceptionClass = backendNpuInstantiateProbeResult.exceptionClass,
            backendNpuInstantiateExceptionMessage = backendNpuInstantiateProbeResult.exceptionMessage,
            backendNpuInstantiateRootCause = backendNpuInstantiateProbeResult.rootCause,
            backendNpuInstantiateCauseChain = backendNpuInstantiateProbeResult.causeChain,
            backendNpuInstantiateWarning = backendNpuInstantiateProbeResult.warning,
            backendNpuAttachDryRunEnabled = backendNpuAttachDryRunProbeResult.enabled,
            backendNpuAttachDryRunSkipReason = backendNpuAttachDryRunProbeResult.skipReason,
            backendNpuAttachDryRunNpuObjectClass = backendNpuAttachDryRunProbeResult.npuObjectClass,
            backendNpuAttachDryRunTargetBuilderCandidates = backendNpuAttachDryRunProbeResult.targetBuilderCandidates,
            backendNpuAttachDryRunSetterCandidates = backendNpuAttachDryRunProbeResult.setterCandidates,
            backendNpuAttachDryRunSelectedSetter = backendNpuAttachDryRunProbeResult.selectedSetter,
            backendNpuAttachDryRunSetterInvokeResult = backendNpuAttachDryRunProbeResult.setterInvokeResult,
            backendNpuAttachDryRunBuildInvoked = backendNpuAttachDryRunProbeResult.buildInvoked,
            backendNpuAttachDryRunBuildResult = backendNpuAttachDryRunProbeResult.buildResult,
            backendNpuAttachDryRunExceptionClass = backendNpuAttachDryRunProbeResult.exceptionClass,
            backendNpuAttachDryRunExceptionMessage = backendNpuAttachDryRunProbeResult.exceptionMessage,
            backendNpuAttachDryRunRootCause = backendNpuAttachDryRunProbeResult.rootCause,
            backendNpuAttachDryRunCauseChain = backendNpuAttachDryRunProbeResult.causeChain,
            backendNpuAttachDryRunWarning = backendNpuAttachDryRunProbeResult.warning,
            backendNpuAttachDryRunNote = backendNpuAttachDryRunProbeResult.note,
            liteRtLmNpuApiInventoryEnabled = liteRtLmNpuApiInventoryProbeResult.enabled,
            liteRtLmNpuApiInventorySkipReason = liteRtLmNpuApiInventoryProbeResult.skipReason,
            liteRtLmNpuApiClassInventory = liteRtLmNpuApiInventoryProbeResult.classInventory,
            liteRtLmNpuApiConstructorInventory = liteRtLmNpuApiInventoryProbeResult.constructorInventory,
            liteRtLmNpuApiPublicMethodInventory = liteRtLmNpuApiInventoryProbeResult.publicMethodInventory,
            liteRtLmNpuApiDeclaredMethodInventory = liteRtLmNpuApiInventoryProbeResult.declaredMethodInventory,
            liteRtLmNpuApiFieldInventory = liteRtLmNpuApiInventoryProbeResult.fieldInventory,
            liteRtLmNpuApiStaticMethodInventory = liteRtLmNpuApiInventoryProbeResult.staticMethodInventory,
            liteRtLmNpuApiAssignability = liteRtLmNpuApiInventoryProbeResult.assignability,
            engineConfigConstructorInventory = liteRtLmNpuApiInventoryProbeResult.engineConfigConstructorInventory,
            engineConfigBackendPropertyInventory = liteRtLmNpuApiInventoryProbeResult.engineConfigBackendPropertyInventory,
            engineConfigCopyMethodInventory = liteRtLmNpuApiInventoryProbeResult.engineConfigCopyMethodInventory,
            engineConfigComponentMethodInventory = liteRtLmNpuApiInventoryProbeResult.engineConfigComponentMethodInventory,
            engineConfigJsonMethodInventory = liteRtLmNpuApiInventoryProbeResult.engineConfigJsonMethodInventory,
            engineConfigNpuDryBuildEnabled = engineConfigNpuDryBuildProbeResult.enabled,
            engineConfigNpuDryBuildSkipReason = engineConfigNpuDryBuildProbeResult.skipReason,
            engineConfigNpuDryBuildSelectedConstructor = engineConfigNpuDryBuildProbeResult.selectedConstructor,
            engineConfigNpuDryBuildConstructorArgsSummary = engineConfigNpuDryBuildProbeResult.constructorArgsSummary,
            engineConfigNpuDryBuildNpuBackendObjectClass = engineConfigNpuDryBuildProbeResult.npuBackendObjectClass,
            engineConfigNpuDryBuildResult = engineConfigNpuDryBuildProbeResult.result,
            engineConfigNpuDryBuildCreatedObjectClass = engineConfigNpuDryBuildProbeResult.createdObjectClass,
            engineConfigNpuDryBuildBackendGetterResultClass = engineConfigNpuDryBuildProbeResult.backendGetterResultClass,
            engineConfigNpuDryBuildExceptionClass = engineConfigNpuDryBuildProbeResult.exceptionClass,
            engineConfigNpuDryBuildExceptionMessage = engineConfigNpuDryBuildProbeResult.exceptionMessage,
            engineConfigNpuDryBuildRootCause = engineConfigNpuDryBuildProbeResult.rootCause,
            engineConfigNpuDryBuildCauseChain = engineConfigNpuDryBuildProbeResult.causeChain,
            engineConfigNpuDryBuildWarning = engineConfigNpuDryBuildProbeResult.warning,
            backendNpuConnectionPreferredBackendEnumPath = backendNpuConnectionCandidateProbeResult.preferredBackendEnumPath,
            backendNpuConnectionPreferredBackendEnumReason = backendNpuConnectionCandidateProbeResult.preferredBackendEnumReason,
            backendNpuConnectionEngineConfigBackendPath = backendNpuConnectionCandidateProbeResult.engineConfigBackendPath,
            backendNpuConnectionEngineInitializePath = backendNpuConnectionCandidateProbeResult.engineInitializePath,
            backendNpuConnectionRecommendedNextPhase = backendNpuConnectionCandidateProbeResult.recommendedNextPhase,
        )
    }

    private fun probeDispatchRuntimeCompatibilitySafely(
        packagedLibraries: PackagedNpuLibraryProbeResult,
    ): DispatchRuntimeCompatibilityProbeResult {
        return runCatching {
            val nativeLibraryDir = packagedLibraries.nativeLibraryDir?.takeIf { it.isNotBlank() }
            val nativeLibraryDirectory = nativeLibraryDir?.let(::File)
            val nativeLibraryDirExists = nativeLibraryDirectory?.isDirectory
            val liteRtBuildId = nativeLibraryDirectory?.resolve("libLiteRt.so")?.let(::readElfBuildIdSafely)
            val liteRtLmJniBuildId = nativeLibraryDirectory?.resolve("liblitertlm_jni.so")?.let(::readElfBuildIdSafely)
            val dispatchCandidate = packagedLibraries.dispatchApiSelectedCandidate
                ?: packagedLibraries.dispatchApiCandidates.firstOrNull()
            val dispatchRuntimeFile = dispatchCandidate?.let { nativeLibraryDirectory?.resolve(it) }
            val dispatchRuntimeBuildId = dispatchRuntimeFile?.let(::readElfBuildIdSafely)
            val dispatchRuntimeSha256 = dispatchRuntimeFile?.let(::sha256ForFileSafely)
            val dispatchPresent = dispatchRuntimeFile?.isFile == true
            val abiCompatibility = classifyDispatchRuntimeAbiCompatibility(
                dispatchPresent = dispatchPresent,
                liteRtBuildId = liteRtBuildId,
                liteRtLmJniBuildId = liteRtLmJniBuildId,
                dispatchRuntimeBuildId = dispatchRuntimeBuildId,
            )
            DispatchRuntimeCompatibilityProbeResult(
                currentFlavor = BuildConfig.CURRENT_FLAVOR,
                applicationId = BuildConfig.APPLICATION_ID,
                nativeLibraryDir = nativeLibraryDir,
                nativeLibraryDirExists = nativeLibraryDirExists,
                dispatchRuntimePresent = dispatchPresent,
                dispatchRuntimeSource = if (dispatchPresent) BuildConfig.DISPATCH_RUNTIME_SOURCE else "none",
                dispatchRuntimeFilePath = dispatchRuntimeFile?.absolutePath,
                dispatchRuntimeFileLength = dispatchRuntimeFile?.takeIf { it.isFile }?.length(),
                liteRtBuildId = liteRtBuildId,
                liteRtLmJniBuildId = liteRtLmJniBuildId,
                dispatchRuntimeBuildId = dispatchRuntimeBuildId,
                dispatchRuntimeSha256 = dispatchRuntimeSha256,
                dispatchRuntimeExpectedSha256Match = dispatchRuntimeSha256?.equals(GALLERY_SM8750_DISPATCH_SHA256, ignoreCase = true),
                abiCompatibility = abiCompatibility,
            )
        }.getOrElse {
            DispatchRuntimeCompatibilityProbeResult(
                currentFlavor = BuildConfig.CURRENT_FLAVOR,
                applicationId = BuildConfig.APPLICATION_ID,
                dispatchRuntimePresent = false,
                dispatchRuntimeSource = "probe-error:${it.javaClass.simpleName}",
                abiCompatibility = "unknown",
            )
        }
    }

    private fun classifyDispatchRuntimeAbiCompatibility(
        dispatchPresent: Boolean,
        liteRtBuildId: String?,
        liteRtLmJniBuildId: String?,
        dispatchRuntimeBuildId: String?,
    ): String {
        if (!dispatchPresent) return "unknown"
        if (dispatchRuntimeBuildId.isNullOrBlank() || liteRtBuildId.isNullOrBlank() || liteRtLmJniBuildId.isNullOrBlank()) {
            return "unknown"
        }
        val galleryDispatch = dispatchRuntimeBuildId.equals(GALLERY_SM8750_DISPATCH_BUILD_ID, ignoreCase = true)
        val galleryLiteRt = liteRtBuildId.equals(GALLERY_SM8750_LITERT_BUILD_ID, ignoreCase = true)
        val galleryLiteRtLmJni = liteRtLmJniBuildId.equals(GALLERY_SM8750_LITERTLM_JNI_BUILD_ID, ignoreCase = true)
        if (galleryDispatch && (!galleryLiteRt || !galleryLiteRtLmJni)) return "likely-mismatch"
        if (galleryDispatch && galleryLiteRt && galleryLiteRtLmJni) return "likely-compatible"
        return "unknown"
    }

    private fun probeBackendNpuInstantiateOnlySafely(
        context: Context?,
        packagedLibraries: PackagedNpuLibraryProbeResult,
        dispatchRuntimeCompatibility: DispatchRuntimeCompatibilityProbeResult,
    ): BackendNpuInstantiateProbeResult {
        val nativeLibraryDir = context?.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }
            ?: packagedLibraries.nativeLibraryDir?.takeIf { it.isNotBlank() }
        val warning = "instantiate-only; object not passed to engine; no inference"
        val skipReason = when {
            !BuildConfig.DEBUG -> "not-debug"
            BuildConfig.CURRENT_FLAVOR != "npuExperiment" -> "not-npuExperiment-flavor"
            !BuildConfig.NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED -> "build-config-disabled"
            dispatchRuntimeCompatibility.dispatchRuntimePresent != true -> "dispatch-runtime-not-present"
            nativeLibraryDir.isNullOrBlank() -> "native-library-dir-missing"
            else -> null
        }
        if (skipReason != null) {
            return BackendNpuInstantiateProbeResult(
                enabled = false,
                skipReason = skipReason,
                nativeLibraryDirArgument = nativeLibraryDir,
                result = "skipped",
                warning = warning,
            )
        }
        val nativeLibraryDirArgument = requireNotNull(nativeLibraryDir)
        return runCatching {
            val instance = instantiateBackendNpuForProbe(nativeLibraryDirArgument)
            BackendNpuInstantiateProbeResult(
                enabled = true,
                nativeLibraryDirArgument = nativeLibraryDirArgument,
                constructor = "Backend.NPU(String)",
                result = "success",
                objectClass = instance.instance.javaClass.name,
                warning = warning,
            )
        }.getOrElse { throwable ->
            val unwrapped = unwrapInvocationTarget(throwable)
            val chain = throwableCauseChain(throwable)
            BackendNpuInstantiateProbeResult(
                enabled = true,
                nativeLibraryDirArgument = nativeLibraryDirArgument,
                constructor = "Backend.NPU(String)",
                result = "failed",
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message?.take(240),
                rootCause = "${unwrapped.javaClass.name}:${unwrapped.message.orEmpty().take(240)}",
                causeChain = chain.joinToString(" -> ") { cause ->
                    "${cause.javaClass.simpleName}:${cause.message.orEmpty().take(120)}"
                }.ifBlank { throwable.javaClass.simpleName },
                warning = warning,
            )
        }
    }

    private fun probeBackendNpuAttachDryRunSafely(
        context: Context?,
        packagedLibraries: PackagedNpuLibraryProbeResult,
        dispatchRuntimeCompatibility: DispatchRuntimeCompatibilityProbeResult,
        instantiateProbeResult: BackendNpuInstantiateProbeResult,
        delegateApiProbeResult: DelegateApiProbeResult,
    ): BackendNpuAttachDryRunProbeResult {
        val nativeLibraryDir = context?.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }
            ?: packagedLibraries.nativeLibraryDir?.takeIf { it.isNotBlank() }
        val warning = "attach-dry-run only; no Engine; no Conversation; no inference"
        val skipReason = when {
            !BuildConfig.DEBUG -> "not-debug"
            BuildConfig.CURRENT_FLAVOR != "npuExperiment" -> "not-npuExperiment-flavor"
            !BuildConfig.NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED -> "build-config-disabled"
            dispatchRuntimeCompatibility.dispatchRuntimePresent != true -> "dispatch-runtime-not-present"
            instantiateProbeResult.result != "success" -> "npu-instantiate-failed"
            nativeLibraryDir.isNullOrBlank() -> "native-library-dir-missing"
            else -> null
        }
        if (skipReason != null) {
            return BackendNpuAttachDryRunProbeResult(
                enabled = false,
                skipReason = skipReason,
                setterInvokeResult = "skipped",
                buildInvoked = "no",
                buildResult = "skipped",
                warning = warning,
            )
        }
        val nativeLibraryDirArgument = requireNotNull(nativeLibraryDir)

        return runCatching {
            val npuHandle = instantiateBackendNpuForProbe(nativeLibraryDirArgument)
            val classNames = backendNpuAttachDryRunTargetClassNames(delegateApiProbeResult)
            val targetBuilderCandidates = linkedSetOf<String>()
            val setterCandidates = linkedSetOf<String>()
            var selectedTarget: BackendNpuAttachDryRunTarget? = null
            var selectedMethod: Method? = null

            classNames.forEach { className ->
                val clazz = runCatching { Class.forName(className) }.getOrElse { throwable ->
                    targetBuilderCandidates += "${className.substringAfterLast('.')}:missing-${throwable.javaClass.simpleName}"
                    return@forEach
                }
                val target = instantiateAttachDryRunTarget(clazz)
                if (target == null) {
                    targetBuilderCandidates += "${clazz.simpleName}:no-instantiable-builder"
                    return@forEach
                }
                targetBuilderCandidates += target.label
                val methods = (target.builderClass.methods.asList() + target.builderClass.declaredMethods.asList())
                    .distinctBy { method -> method.name + method.parameterTypes.joinToString("#") { it.name } }
                    .filter(::isAttachDryRunBackendSetterCandidate)
                    .sortedWith(compareBy<Method> { attachDryRunSetterRank(it.name) }.thenBy { it.name })
                methods.forEach { method ->
                    val assignable = method.parameterTypes.singleOrNull()?.isAssignableFrom(npuHandle.npuClass) == true
                    val signature = "${target.label}.${formatMethodSignature(target.builderClass, method)}"
                    setterCandidates += if (assignable) signature else "$signature [not-assignable]"
                    if (selectedMethod == null && assignable) {
                        selectedTarget = target
                        selectedMethod = method
                    }
                }
            }

            val method = selectedMethod
                ?: return@runCatching BackendNpuAttachDryRunProbeResult(
                    enabled = true,
                    npuObjectClass = npuHandle.instance.javaClass.name,
                    targetBuilderCandidates = targetBuilderCandidates.take(MAX_DELEGATE_CANDIDATE_COUNT),
                    setterCandidates = setterCandidates.take(MAX_DELEGATE_CANDIDATE_COUNT),
                    setterInvokeResult = "method-not-found",
                    buildInvoked = "no",
                    buildResult = "skipped",
                    warning = warning,
                )
            val target = requireNotNull(selectedTarget)
            method.isAccessible = true
            method.invoke(target.builder, npuHandle.instance)
            BackendNpuAttachDryRunProbeResult(
                enabled = true,
                npuObjectClass = npuHandle.instance.javaClass.name,
                targetBuilderCandidates = targetBuilderCandidates.take(MAX_DELEGATE_CANDIDATE_COUNT),
                setterCandidates = setterCandidates.take(MAX_DELEGATE_CANDIDATE_COUNT),
                selectedSetter = "${target.label}.${formatMethodSignature(target.builderClass, method)}",
                setterInvokeResult = "success",
                buildInvoked = "no",
                buildResult = "skipped-build-not-invoked-safety",
                warning = warning,
            )
        }.getOrElse { throwable ->
            val unwrapped = unwrapInvocationTarget(throwable)
            val chain = throwableCauseChain(throwable)
            BackendNpuAttachDryRunProbeResult(
                enabled = true,
                npuObjectClass = instantiateProbeResult.objectClass,
                setterInvokeResult = "failed",
                buildInvoked = "no",
                buildResult = "skipped",
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message?.take(240),
                rootCause = "${unwrapped.javaClass.name}:${unwrapped.message.orEmpty().take(240)}",
                causeChain = chain.joinToString(" -> ") { cause ->
                    "${cause.javaClass.simpleName}:${cause.message.orEmpty().take(120)}"
                }.ifBlank { throwable.javaClass.simpleName },
                warning = warning,
            )
        }
    }

    private fun instantiateBackendNpuForProbe(nativeLibraryDir: String): BackendNpuProbeObject {
        val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
        val npuClass = (backendClass.classes.asList() + backendClass.declaredClasses.asList())
            .firstOrNull { it.simpleName == "NPU" }
            ?: throw ClassNotFoundException("Backend.NPU")
        val constructor = (npuClass.declaredConstructors.asList() + npuClass.constructors.asList())
            .firstOrNull { candidate ->
                candidate.parameterTypes.size == 1 && candidate.parameterTypes.first() == String::class.java
            }
            ?: throw NoSuchMethodException("Backend.NPU(String)")
        constructor.isAccessible = true
        return BackendNpuProbeObject(
            instance = constructor.newInstance(nativeLibraryDir),
            backendClass = backendClass,
            npuClass = npuClass,
        )
    }

    private fun probeLiteRtLmNpuApiInventorySafely(
        dispatchRuntimeCompatibility: DispatchRuntimeCompatibilityProbeResult,
        instantiateProbeResult: BackendNpuInstantiateProbeResult,
    ): LiteRtLmNpuApiInventoryProbeResult {
        val skipReason = when {
            !BuildConfig.DEBUG -> "not-debug"
            BuildConfig.CURRENT_FLAVOR != "npuExperiment" -> "not-npuExperiment-flavor"
            !BuildConfig.NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED -> "build-config-disabled"
            dispatchRuntimeCompatibility.dispatchRuntimePresent != true -> "dispatch-runtime-not-present"
            instantiateProbeResult.result != "success" -> "npu-instantiate-failed"
            else -> null
        }
        if (skipReason != null) {
            return LiteRtLmNpuApiInventoryProbeResult(enabled = false, skipReason = skipReason)
        }

        return runCatching {
            val classInventory = linkedSetOf<String>()
            val constructorInventory = linkedSetOf<String>()
            val publicMethodInventory = linkedSetOf<String>()
            val declaredMethodInventory = linkedSetOf<String>()
            val fieldInventory = linkedSetOf<String>()
            val staticMethodInventory = linkedSetOf<String>()
            val assignability = linkedSetOf<String>()
            val engineConfigConstructors = linkedSetOf<String>()
            val engineConfigBackendProperties = linkedSetOf<String>()
            val engineConfigCopyMethods = linkedSetOf<String>()
            val engineConfigComponentMethods = linkedSetOf<String>()
            val engineConfigJsonMethods = linkedSetOf<String>()
            val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
            val npuClass = Class.forName("com.google.ai.edge.litertlm.Backend\$NPU")

            liteRtLmNpuApiInventoryClassNames().forEach { className ->
                val clazz = runCatching { Class.forName(className) }.getOrElse { throwable ->
                    classInventory += "$className: not found (${throwable.javaClass.simpleName})"
                    return@forEach
                }
                classInventory += "$className: found"
                val constructors = (clazz.constructors.asList() + clazz.declaredConstructors.asList())
                    .distinctBy { constructor -> constructor.parameterTypes.joinToString("#") { it.name } }
                constructors.forEach { constructor ->
                    val signature = formatConstructorSignature(clazz, constructor)
                    constructorInventory += signature
                    if (clazz.name == "com.google.ai.edge.litertlm.EngineConfig") {
                        engineConfigConstructors += buildEngineConfigConstructorDetail(constructor, npuClass)
                    }
                }
                clazz.methods
                    .distinctBy { method -> method.name + method.parameterTypes.joinToString("#") { it.name } }
                    .forEach { method ->
                        publicMethodInventory += formatMethodSignature(clazz, method)
                        if (Modifier.isStatic(method.modifiers)) {
                            staticMethodInventory += formatMethodSignature(clazz, method)
                        }
                        collectEngineConfigMethodDetail(clazz, method, engineConfigBackendProperties, engineConfigCopyMethods, engineConfigComponentMethods, engineConfigJsonMethods)
                    }
                clazz.declaredMethods
                    .distinctBy { method -> method.name + method.parameterTypes.joinToString("#") { it.name } }
                    .forEach { method ->
                        declaredMethodInventory += formatMethodSignature(clazz, method)
                        if (Modifier.isStatic(method.modifiers)) {
                            staticMethodInventory += formatMethodSignature(clazz, method)
                        }
                        collectEngineConfigMethodDetail(clazz, method, engineConfigBackendProperties, engineConfigCopyMethods, engineConfigComponentMethods, engineConfigJsonMethods)
                    }
                (clazz.fields.asList() + clazz.declaredFields.asList())
                    .distinctBy { field -> field.name }
                    .forEach { field ->
                        fieldInventory += "${clazz.simpleName}.${field.name}: ${field.type.simpleName}"
                        if (clazz.name == "com.google.ai.edge.litertlm.EngineConfig" && field.name.contains("backend", ignoreCase = true)) {
                            engineConfigBackendProperties += "${clazz.simpleName}.${field.name}: ${field.type.name}"
                        }
                    }
            }

            assignability += "Backend base class <- Backend.NPU object class: ${backendClass.isAssignableFrom(npuClass)}"
            constructorInventory
                .filter { it.startsWith("EngineConfig(") && it.contains("Backend") }
                .forEach { assignability += "EngineConfig backend parameter candidate: $it" }
            liteRtLmNpuApiInventoryClassNames().forEach { className ->
                val clazz = runCatching { Class.forName(className) }.getOrNull() ?: return@forEach
                (clazz.methods.asList() + clazz.declaredMethods.asList())
                    .filter(::isAttachDryRunBackendSetterCandidate)
                    .forEach { method ->
                        val parameterType = method.parameterTypes.singleOrNull() ?: return@forEach
                        val assignable = parameterType.isAssignableFrom(npuClass)
                        assignability += "${formatMethodSignature(clazz, method)} parameter <- Backend.NPU: $assignable"
                    }
            }

            LiteRtLmNpuApiInventoryProbeResult(
                enabled = true,
                classInventory = classInventory.take(48),
                constructorInventory = constructorInventory.take(80),
                publicMethodInventory = publicMethodInventory.take(80),
                declaredMethodInventory = declaredMethodInventory.take(80),
                fieldInventory = fieldInventory.take(80),
                staticMethodInventory = staticMethodInventory.take(40),
                assignability = assignability.take(80),
                engineConfigConstructorInventory = engineConfigConstructors.take(40),
                engineConfigBackendPropertyInventory = engineConfigBackendProperties.take(40),
                engineConfigCopyMethodInventory = engineConfigCopyMethods.take(20),
                engineConfigComponentMethodInventory = engineConfigComponentMethods.take(20),
                engineConfigJsonMethodInventory = engineConfigJsonMethods.take(20),
            )
        }.getOrElse { throwable ->
            LiteRtLmNpuApiInventoryProbeResult(
                enabled = true,
                skipReason = "error-${throwable.javaClass.simpleName}",
                classInventory = listOf("error: ${throwable.javaClass.name}:${throwable.message.orEmpty().take(160)}"),
            )
        }
    }

    private fun probeEngineConfigNpuDryBuildSafely(
        context: Context?,
        packagedLibraries: PackagedNpuLibraryProbeResult,
        dispatchRuntimeCompatibility: DispatchRuntimeCompatibilityProbeResult,
        instantiateProbeResult: BackendNpuInstantiateProbeResult,
    ): EngineConfigNpuDryBuildProbeResult {
        val nativeLibraryDir = context?.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }
            ?: packagedLibraries.nativeLibraryDir?.takeIf { it.isNotBlank() }
        val warning = "config-only; not passed to Engine; no inference"
        val skipReason = when {
            !BuildConfig.DEBUG -> "not-debug"
            BuildConfig.CURRENT_FLAVOR != "npuExperiment" -> "not-npuExperiment-flavor"
            !BuildConfig.NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED -> "build-config-disabled"
            dispatchRuntimeCompatibility.dispatchRuntimePresent != true -> "dispatch-runtime-not-present"
            instantiateProbeResult.result != "success" -> "npu-instantiate-failed"
            nativeLibraryDir.isNullOrBlank() -> "native-library-dir-missing"
            else -> null
        }
        if (skipReason != null) {
            return EngineConfigNpuDryBuildProbeResult(
                enabled = false,
                skipReason = skipReason,
                result = "skipped",
                warning = warning,
            )
        }
        val nativeLibraryDirArgument = requireNotNull(nativeLibraryDir)
        return runCatching {
            val npuHandle = instantiateBackendNpuForProbe(nativeLibraryDirArgument)
            val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            val constructor = selectEngineConfigDryBuildConstructor(engineConfigClass, npuHandle.npuClass)
                ?: return@runCatching EngineConfigNpuDryBuildProbeResult(
                    enabled = true,
                    npuBackendObjectClass = npuHandle.instance.javaClass.name,
                    result = "skipped",
                    skipReason = "engineconfig-backend-constructor-not-found",
                    warning = warning,
                )
            val args = buildEngineConfigDryBuildArgs(
                constructor = constructor,
                npuObject = npuHandle.instance,
                npuClass = npuHandle.npuClass,
                context = context,
            )
            constructor.isAccessible = true
            val config = constructor.newInstance(*args.values.toTypedArray())
            val backendGetterClass = readEngineConfigBackendClassSafely(config)
            EngineConfigNpuDryBuildProbeResult(
                enabled = true,
                selectedConstructor = formatConstructorSignature(engineConfigClass, constructor),
                constructorArgsSummary = args.summary.joinToString(", "),
                npuBackendObjectClass = npuHandle.instance.javaClass.name,
                result = "success",
                createdObjectClass = config.javaClass.name,
                backendGetterResultClass = backendGetterClass,
                warning = warning,
            )
        }.getOrElse { throwable ->
            val unwrapped = unwrapInvocationTarget(throwable)
            val chain = throwableCauseChain(throwable)
            EngineConfigNpuDryBuildProbeResult(
                enabled = true,
                npuBackendObjectClass = instantiateProbeResult.objectClass,
                result = "failed",
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message?.take(240),
                rootCause = "${unwrapped.javaClass.name}:${unwrapped.message.orEmpty().take(240)}",
                causeChain = chain.joinToString(" -> ") { cause ->
                    "${cause.javaClass.simpleName}:${cause.message.orEmpty().take(120)}"
                }.ifBlank { throwable.javaClass.simpleName },
                warning = warning,
            )
        }
    }

    private fun buildBackendNpuConnectionCandidate(
        apiInventory: LiteRtLmNpuApiInventoryProbeResult,
        engineConfigDryBuild: EngineConfigNpuDryBuildProbeResult,
    ): BackendNpuConnectionCandidateProbeResult {
        val preferredBackendEnumReason = apiInventory.assignability
            .firstOrNull { it.contains("setPreferredBackend") && it.contains("false") }
            ?: "MediaPipe LlmInference preferredBackend setter does not accept LiteRT-LM Backend.NPU."
        val engineConfigPath = when (engineConfigDryBuild.result) {
            "success" -> "candidate"
            "failed" -> "failed"
            "skipped" -> if (engineConfigDryBuild.skipReason == "engineconfig-backend-constructor-not-found") "not found" else "skipped"
            else -> "unknown"
        }
        val nextPhase = if (engineConfigDryBuild.result == "success") {
            "next: isolated Engine.initialize dry-run only, no generate"
        } else {
            "next: inspect LiteRT-LM source/API version mismatch"
        }
        return BackendNpuConnectionCandidateProbeResult(
            preferredBackendEnumPath = "incompatible",
            preferredBackendEnumReason = preferredBackendEnumReason,
            engineConfigBackendPath = engineConfigPath,
            engineInitializePath = "not attempted",
            recommendedNextPhase = nextPhase,
        )
    }

    private fun liteRtLmNpuApiInventoryClassNames(): List<String> {
        return listOf(
            "com.google.ai.edge.litertlm.Backend",
            "com.google.ai.edge.litertlm.Backend\$NPU",
            "com.google.ai.edge.litertlm.Backend\$GPU",
            "com.google.ai.edge.litertlm.Backend\$CPU",
            "com.google.ai.edge.litertlm.EngineConfig",
            "com.google.ai.edge.litertlm.Engine",
            "com.google.ai.edge.litertlm.LlmInference",
            "com.google.ai.edge.litertlm.LlmInferenceOptions",
            "com.google.ai.edge.litertlm.LlmInferenceOptions\$Builder",
            "com.google.mediapipe.tasks.genai.llminference.LlmInference",
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder",
        )
    }

    private fun buildEngineConfigConstructorDetail(
        constructor: Constructor<*>,
        npuClass: Class<*>,
    ): String {
        val params = constructor.parameterTypes
        val hasDefaultConstructorMarker = params.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
        val hasModelPathString = params.any { it == String::class.java }
        val backendParamIndexes = params.mapIndexedNotNull { index, type ->
            index.takeIf { type.isAssignableFrom(npuClass) || type.simpleName.contains("Backend", ignoreCase = true) }
        }
        return formatConstructorSignature(constructor.declaringClass, constructor) +
            " count=${params.size}" +
            " defaultMarker=$hasDefaultConstructorMarker" +
            " modelPathString=$hasModelPathString" +
            " backendParamIndexes=${backendParamIndexes.joinToString("|").ifBlank { "none" }}"
    }

    private fun collectEngineConfigMethodDetail(
        clazz: Class<*>,
        method: Method,
        backendProperties: MutableSet<String>,
        copyMethods: MutableSet<String>,
        componentMethods: MutableSet<String>,
        jsonMethods: MutableSet<String>,
    ) {
        if (clazz.name != "com.google.ai.edge.litertlm.EngineConfig") return
        val signature = formatMethodSignature(clazz, method)
        if (method.name.contains("backend", ignoreCase = true)) {
            backendProperties += signature
        }
        if (method.name == "copy" || method.name.startsWith("copy\$")) {
            copyMethods += signature
        }
        if (method.name.startsWith("component")) {
            componentMethods += signature
        }
        if (method.name.contains("json", ignoreCase = true)) {
            jsonMethods += signature
        }
    }

    private fun selectEngineConfigDryBuildConstructor(
        engineConfigClass: Class<*>,
        npuClass: Class<*>,
    ): Constructor<*>? {
        return (engineConfigClass.declaredConstructors.asList() + engineConfigClass.constructors.asList())
            .distinctBy { constructor -> constructor.parameterTypes.joinToString("#") { it.name } }
            .filterNot { constructor -> constructor.parameterTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" } }
            .filter { constructor -> constructor.parameterTypes.any { it.isAssignableFrom(npuClass) } }
            .filter { constructor -> constructor.parameterTypes.any { it == String::class.java } }
            .minWithOrNull(compareBy<Constructor<*>> { it.parameterTypes.size }.thenBy { formatConstructorSignature(engineConfigClass, it) })
    }

    private fun buildEngineConfigDryBuildArgs(
        constructor: Constructor<*>,
        npuObject: Any,
        npuClass: Class<*>,
        context: Context?,
    ): EngineConfigDryBuildArgs {
        var backendAssigned = false
        val values = constructor.parameterTypes.mapIndexed { index, type ->
            when {
                !backendAssigned && type.isAssignableFrom(npuClass) -> {
                    backendAssigned = true
                    npuObject
                }
                type == String::class.java -> "/dev/null/nonexistent.litertlm"
                type == java.lang.Boolean.TYPE -> false
                type == java.lang.Integer.TYPE -> 0
                type == java.lang.Long.TYPE -> 0L
                type == java.lang.Float.TYPE -> 0f
                type == java.lang.Double.TYPE -> 0.0
                type == File::class.java -> context?.cacheDir ?: File("/tmp")
                List::class.java.isAssignableFrom(type) -> emptyList<Any>()
                Map::class.java.isAssignableFrom(type) -> emptyMap<Any, Any>()
                type.isEnum -> type.enumConstants?.firstOrNull()
                else -> null
            }
        }
        val summary = constructor.parameterTypes.mapIndexed { index, type ->
            val value = values[index]
            val valueSummary = when (value) {
                null -> "null"
                npuObject -> "Backend.NPU"
                is String -> "dummy-model-path"
                else -> value.javaClass.simpleName
            }
            "arg$index:${type.simpleName}=$valueSummary"
        }
        return EngineConfigDryBuildArgs(values = values, summary = summary)
    }

    private fun readEngineConfigBackendClassSafely(config: Any): String? {
        val clazz = config.javaClass
        val getterResult = runCatching {
            val getter = (clazz.methods.asList() + clazz.declaredMethods.asList())
                .firstOrNull { method -> method.parameterTypes.isEmpty() && method.name == "getBackend" }
                ?: return@runCatching null
            getter.isAccessible = true
            getter.invoke(config)?.javaClass?.name
        }.getOrNull()
        if (!getterResult.isNullOrBlank()) return getterResult
        return runCatching {
            val field = (clazz.fields.asList() + clazz.declaredFields.asList())
                .firstOrNull { field -> field.name == "backend" }
                ?: return@runCatching null
            field.isAccessible = true
            field.get(config)?.javaClass?.name
        }.getOrNull()
    }

    private fun backendNpuAttachDryRunTargetClassNames(
        delegateApiProbeResult: DelegateApiProbeResult,
    ): List<String> {
        val classNames = linkedSetOf(
            "com.google.ai.edge.litertlm.EngineConfig",
            "com.google.ai.edge.litertlm.EngineConfig\$Builder",
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder",
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
            "com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions\$Builder",
            "com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions",
        )
        delegateApiProbeResult.classCandidates.forEach { candidate ->
            classNames += toLikelyFqcnVariants(candidate)
            if (candidate.contains("LlmInferenceOptions")) {
                classNames += "com.google.mediapipe.tasks.genai.llminference.${candidate.removePrefix("LlmInference.")}"
            }
            if (candidate.contains("EngineConfig")) {
                classNames += "com.google.ai.edge.litertlm.${candidate.substringAfterLast('.')}"
            }
        }
        return classNames.toList()
    }

    private fun instantiateAttachDryRunTarget(clazz: Class<*>): BackendNpuAttachDryRunTarget? {
        instantiateNoArgConstructor(clazz)?.let { instance ->
            return BackendNpuAttachDryRunTarget(label = clazz.simpleName, builder = instance, builderClass = clazz)
        }
        val builderFromStaticMethod = (clazz.methods.asList() + clazz.declaredMethods.asList())
            .firstNotNullOfOrNull { method ->
                if (!Modifier.isStatic(method.modifiers) || method.parameterTypes.isNotEmpty()) return@firstNotNullOfOrNull null
                if (method.name !in setOf("builder", "newBuilder")) return@firstNotNullOfOrNull null
                runCatching {
                    method.isAccessible = true
                    method.invoke(null)
                }.getOrNull()
            }
        if (builderFromStaticMethod != null) {
            return BackendNpuAttachDryRunTarget(
                label = "${clazz.simpleName}.${builderFromStaticMethod.javaClass.simpleName}",
                builder = builderFromStaticMethod,
                builderClass = builderFromStaticMethod.javaClass,
            )
        }
        return null
    }

    private fun instantiateNoArgConstructor(clazz: Class<*>): Any? {
        val constructor = (clazz.declaredConstructors.asList() + clazz.constructors.asList())
            .filterIsInstance<Constructor<*>>()
            .firstOrNull { constructor -> constructor.parameterTypes.isEmpty() }
            ?: return null
        return runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrNull()
    }

    private fun isAttachDryRunBackendSetterCandidate(method: Method): Boolean {
        if (method.isSynthetic || method.name.indexOf('$') >= 0) return false
        if (method.parameterTypes.size != 1) return false
        val lowerName = method.name.lowercase(Locale.US)
        return lowerName == "setbackend" ||
            lowerName == "setpreferredbackend" ||
            lowerName == "backend" ||
            lowerName == "preferredbackend" ||
            lowerName.contains("backend")
    }

    private fun attachDryRunSetterRank(methodName: String): Int {
        return when (methodName) {
            "setBackend" -> 0
            "setPreferredBackend" -> 1
            "backend" -> 2
            "preferredBackend" -> 3
            else -> 4
        }
    }

    private fun unwrapInvocationTarget(throwable: Throwable): Throwable {
        var current = throwable
        while (current is InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current
    }

    private fun throwableCauseChain(throwable: Throwable): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = throwable
        while (current != null && chain.size < 8 && current !in chain) {
            chain += current
            current = if (current is InvocationTargetException && current.targetException != null) {
                current.targetException
            } else {
                current.cause
            }
        }
        return chain
    }

    private fun sha256ForFileSafely(file: File): String? {
        return runCatching {
            if (!file.isFile || file.length() <= 0L) return null
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.getOrNull()
    }

    private fun readElfBuildIdSafely(file: File): String? {
        return runCatching {
            if (!file.isFile || file.length() <= 0L) return null
            val bytes = file.inputStream().use { input ->
                input.readBytes().let { data ->
                    if (data.size <= MAX_ELF_NOTE_SCAN_BYTES) data else data.copyOf(MAX_ELF_NOTE_SCAN_BYTES)
                }
            }
            extractGnuBuildId(bytes)
        }.getOrNull()
    }

    private fun extractGnuBuildId(bytes: ByteArray): String? {
        val gnuName = byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0)
        var index = 12
        while (index <= bytes.size - gnuName.size) {
            if (matchesBytes(bytes, index, gnuName)) {
                val headerOffset = index - 12
                val nameSize = readLittleEndianInt(bytes, headerOffset)
                val descSize = readLittleEndianInt(bytes, headerOffset + 4)
                val type = readLittleEndianInt(bytes, headerOffset + 8)
                if (nameSize == 4 && type == 3 && descSize in 4..64) {
                    val descOffset = index + align4(nameSize)
                    if (descOffset >= 0 && descOffset + descSize <= bytes.size) {
                        return bytes.copyOfRange(descOffset, descOffset + descSize)
                            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                    }
                }
            }
            index += 1
        }
        return null
    }

    private fun matchesBytes(bytes: ByteArray, offset: Int, expected: ByteArray): Boolean {
        if (offset < 0 || offset + expected.size > bytes.size) return false
        return expected.indices.all { index -> bytes[offset + index] == expected[index] }
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun align4(value: Int): Int = (value + 3) and -4

    private fun probeExternalQairtStageSafely(): ExternalQairtStageProbeResult {
        return runCatching {
            val stageDir = File(EXTERNAL_QAIRT_STAGE_PATH)
            val stagePresent = runCatching { stageDir.isDirectory }.getOrElse { throw it }
            val qnnNetRunAvailable = runCatching {
                val bin = File(EXTERNAL_QAIRT_QNN_NET_RUN_PATH)
                bin.exists() && bin.canRead()
            }.getOrElse { throw it }
            val qnnPlatformValidatorAvailable = runCatching {
                val bin = File(EXTERNAL_QAIRT_QNN_PLATFORM_VALIDATOR_PATH)
                bin.exists() && bin.canRead()
            }.getOrElse { throw it }
            ExternalQairtStageProbeResult(
                stagePath = EXTERNAL_QAIRT_STAGE_PATH,
                stageStatus = if (stagePresent) "present" else "missing",
                qnnNetRunStatus = if (qnnNetRunAvailable) "available" else "unavailable",
                qnnPlatformValidatorStatus = if (qnnPlatformValidatorAvailable) "available" else "unavailable",
                qnnSdkVersion = EXTERNAL_QAIRT_VERIFIED_SDK_VERSION,
                gpuBackendStatus = EXTERNAL_QAIRT_VERIFIED_GPU_BACKEND_STATUS,
                dspCore = EXTERNAL_QAIRT_VERIFIED_DSP_CORE,
                dspBackendStatus = EXTERNAL_QAIRT_VERIFIED_DSP_BACKEND_STATUS,
                note = "GPU/DSP status and SDK version are adb-verified external stage facts.",
            )
        }.getOrElse { throwable ->
            val reason = throwable.javaClass.simpleName
            ExternalQairtStageProbeResult(
                stagePath = EXTERNAL_QAIRT_STAGE_PATH,
                stageStatus = "unknown",
                qnnNetRunStatus = "unknown",
                qnnPlatformValidatorStatus = "unknown",
                note = "App-side direct probe was not permitted ($reason). Showing adb-verified external stage facts.",
            )
        }
    }

    private fun maybeLogOnce(snapshot: AcceleratorProbeSnapshot) {
        if (!BuildConfig.DEBUG || hasLogged) return
        synchronized(this) {
            if (hasLogged) return
            val message = String.format(
                Locale.US,
                "sdk=%d abi=%s cpuCores=%s gpuRenderer=%s nnapiAvailable=%s nnapiDevices=%d",
                snapshot.androidSdk,
                snapshot.cpuAbi ?: "unknown",
                snapshot.cpuCoreCount?.toString() ?: "unknown",
                snapshot.gpuRenderer ?: "unknown",
                snapshot.nnapiAvailable,
                snapshot.nnapiDevices.size,
            )
            Log.d(LOG_TAG, message)
            Log.d(
                LOG_TAG,
                "dispatchCompatibility flavor=${snapshot.currentFlavor ?: "unknown"} " +
                    "applicationId=${snapshot.applicationId ?: "unknown"} " +
                    "nativeLibraryDir=${snapshot.dispatchNativeLibraryDir ?: "unknown"} " +
                    "present=${snapshot.dispatchRuntimePresentInFlavor ?: false} " +
                    "buildId=${snapshot.dispatchRuntimeBuildId ?: "unknown"} " +
                    "sha256Match=${snapshot.dispatchRuntimeExpectedSha256Match ?: false} " +
                    "abi=${snapshot.dispatchRuntimeAbiCompatibility ?: "unknown"} " +
                    "loadPolicy=diagnostic-only " +
                    "backendNpuInstantiate=${snapshot.backendNpuInstantiateResult ?: "skipped"} " +
                    "skip=${snapshot.backendNpuInstantiateProbeSkipReason ?: "none"} " +
                    "backendNpuAttachDryRun=${snapshot.backendNpuAttachDryRunSetterInvokeResult ?: "skipped"} " +
                    "attachSkip=${snapshot.backendNpuAttachDryRunSkipReason ?: "none"}",
            )
            hasLogged = true
        }
    }

    private fun fetchNnapiDeviceNamesSafely(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
        return runCatching {
            val nnApiImplClass = Class.forName("android.media.MediaCodecInfo")
            nnApiImplClass
            emptyList<String>()
        }.getOrDefault(emptyList())
    }

    private fun probeGpuInfoSafely(): GpuProbeResult {
        return runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                return GpuProbeResult(source = "egl-pbuffer", error = "eglGetDisplay failed")
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                EGL14.eglTerminate(display)
                return GpuProbeResult(source = "egl-pbuffer", error = "eglInitialize failed")
            }

            var context = EGL14.EGL_NO_CONTEXT
            var surface = EGL14.EGL_NO_SURFACE
            try {
                val configAttribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
                val numConfigs = IntArray(1)
                if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] <= 0) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglChooseConfig failed")
                }
                val config = configs[0] ?: return GpuProbeResult(source = "egl-pbuffer", error = "eglConfig missing")

                val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
                if (context == EGL14.EGL_NO_CONTEXT) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglCreateContext failed")
                }

                val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
                if (surface == EGL14.EGL_NO_SURFACE) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglCreatePbufferSurface failed")
                }

                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    return GpuProbeResult(source = "egl-pbuffer", error = "eglMakeCurrent failed")
                }

                GpuProbeResult(
                    vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                    renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                    version = GLES20.glGetString(GLES20.GL_VERSION),
                    source = "egl-pbuffer",
                )
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }.getOrElse {
            GpuProbeResult(source = "egl-pbuffer", error = it.javaClass.simpleName)
        }
    }

    private data class GpuProbeResult(
        val vendor: String? = null,
        val renderer: String? = null,
        val version: String? = null,
        val source: String? = null,
        val error: String? = null,
    )

    private fun probeDelegateApiCandidatesSafely(): DelegateApiProbeResult {
        return runCatching {
            val classesToInspect = listOf(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder",
                "com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession",
                "com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession\$LlmInferenceSessionOptions",
                "com.google.ai.edge.litertlm.EngineConfig",
                "com.google.ai.edge.litertlm.EngineConfig\$Builder",
            )
            val optionCandidates = linkedSetOf<String>()
            val backendCandidates = linkedSetOf<String>()
            val classCandidates = linkedSetOf<String>()

            classesToInspect.forEach { className ->
                val clazz = runCatching { Class.forName(className) }.getOrNull() ?: return@forEach
                collectDelegateCandidates(clazz, optionCandidates, backendCandidates, classCandidates)
            }
            val optionList = optionCandidates.take(MAX_DELEGATE_CANDIDATE_COUNT)
            val backendList = backendCandidates.take(MAX_DELEGATE_CANDIDATE_COUNT)
            val classList = classCandidates.take(MAX_DELEGATE_CANDIDATE_COUNT)
            val enumProbeResult = probeBackendEnumValues(classList, backendList)
            val signatureProbeResult = probePreferredBackendSignatures(classesToInspect, classList)
            val npuCandidates = collectKeywordCandidates(optionList, backendList, classList, keywords = listOf("npu", "neural", "accelerator", "hardware", "qualcomm"))
            val qnnCandidates = collectKeywordCandidates(optionList, backendList, classList, keywords = listOf("qnn", "htp", "hexagon", "dsp", "qualcomm"))
            val nnapiCandidates = collectKeywordCandidates(optionList, backendList, classList, keywords = listOf("nnapi"))
            val backendNpuProbeResult = probeBackendNpuCandidates(classList)
            val npuHint = inferNpuProbeHint(npuCandidates = npuCandidates, qnnCandidates = qnnCandidates, nnapiCandidates = nnapiCandidates)
            DelegateApiProbeResult(
                optionCandidates = optionList,
                backendCandidates = backendList,
                backendEnumValues = enumProbeResult.values,
                backendEnumProbeError = enumProbeResult.error,
                preferredBackendSignatures = signatureProbeResult.signatures,
                preferredBackendSignatureProbeError = signatureProbeResult.error,
                classCandidates = classList,
                switchingSupportedHint = inferDelegateHint(optionList, backendList, classList),
                npuDelegateCandidates = npuCandidates,
                npuBackendCandidates = collectKeywordCandidates(backendList, classList, keywords = listOf("npu", "neural", "accelerator", "hardware", "qualcomm")),
                backendNpuClassCandidates = backendNpuProbeResult.classCandidates,
                backendNpuMethodCandidates = backendNpuProbeResult.methodCandidates,
                backendNpuConstructorSignatures = backendNpuProbeResult.constructorSignatures,
                backendNpuNativeLibraryDirRequired = backendNpuProbeResult.nativeLibraryDirRequired,
                backendNpuProbeHint = backendNpuProbeResult.hint,
                backendNpuProbeError = backendNpuProbeResult.error,
                qnnDelegateCandidates = qnnCandidates,
                nnapiDelegateCandidates = nnapiCandidates,
                npuProbeHint = npuHint,
            )
        }.getOrElse {
            DelegateApiProbeResult(
                error = it.javaClass.simpleName,
                switchingSupportedHint = "unknown",
                npuProbeError = it.javaClass.simpleName,
                backendNpuProbeError = it.javaClass.simpleName,
            )
        }
    }

    private fun probeBackendNpuCandidates(classCandidates: List<String>): BackendNpuProbeResult {
        return runCatching {
            val backendClasses = classCandidates
                .filter { it.contains("Backend", ignoreCase = true) }
                .mapNotNull { candidate ->
                    val simpleName = candidate.substringAfterLast('.')
                    runCatching { Class.forName("com.google.ai.edge.litertlm.$simpleName") }.getOrNull()
                }
                .distinctBy { it.name }
            val classCandidatesOut = linkedSetOf<String>()
            val methodCandidatesOut = linkedSetOf<String>()
            val constructorSignaturesOut = linkedSetOf<String>()
            var nativeLibraryDirRequired = "unknown"
            backendClasses.forEach { backendClass ->
                (backendClass.classes.asList() + backendClass.declaredClasses.asList()).forEach { nestedClass ->
                    if (!nestedClass.simpleName.contains("NPU", ignoreCase = true)) return@forEach
                    classCandidatesOut += "${backendClass.simpleName}.${nestedClass.simpleName}"
                    nestedClass.methods.forEach { method ->
                        if (method.name.contains("NPU", ignoreCase = true) || method.name.contains("nativeLibraryDir", ignoreCase = true)) {
                            methodCandidatesOut += formatMethodSignature(nestedClass, method)
                        }
                    }
                    nestedClass.declaredMethods.forEach { method ->
                        if (method.name.contains("NPU", ignoreCase = true) || method.name.contains("nativeLibraryDir", ignoreCase = true)) {
                            methodCandidatesOut += formatMethodSignature(nestedClass, method)
                        }
                    }
                    nestedClass.constructors.forEach { constructor ->
                        val params = constructor.parameterTypes.joinToString(", ") { it.simpleName.ifBlank { "Unknown" } }
                        val signature = "${nestedClass.simpleName}($params): ${backendClass.simpleName}"
                        constructorSignaturesOut += signature
                        if (constructor.parameterTypes.any { it.name == "java.lang.String" }) {
                            nativeLibraryDirRequired = "true"
                        }
                    }
                }
            }
            if (nativeLibraryDirRequired != "true" && constructorSignaturesOut.isNotEmpty()) {
                nativeLibraryDirRequired = "false"
            }
            val hint = when {
                constructorSignaturesOut.isNotEmpty() && nativeLibraryDirRequired == "true" -> "npu-backend-native-library-dir-candidate"
                constructorSignaturesOut.isNotEmpty() -> "npu-backend-constructor-detected"
                classCandidatesOut.isNotEmpty() -> "npu-backend-class-detected"
                else -> "not-detected"
            }
            BackendNpuProbeResult(
                classCandidates = classCandidatesOut.take(MAX_DELEGATE_CANDIDATE_COUNT),
                methodCandidates = methodCandidatesOut.take(MAX_DELEGATE_CANDIDATE_COUNT),
                constructorSignatures = constructorSignaturesOut.take(MAX_DELEGATE_CANDIDATE_COUNT),
                nativeLibraryDirRequired = nativeLibraryDirRequired,
                hint = hint,
            )
        }.getOrElse {
            BackendNpuProbeResult(
                error = it.javaClass.simpleName,
            )
        }
    }

    private fun probeBackendNpuStageSafely(): BackendNpuStageProbeResult {
        return runCatching {
            val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
            val npuClass = (backendClass.classes.asList() + backendClass.declaredClasses.asList())
                .firstOrNull { it.simpleName == "NPU" }
                ?: return BackendNpuStageProbeResult(result = "missing")
            val constructors = npuClass.declaredConstructors.asList() + npuClass.constructors.asList()
            val hasNoArgConstructor = constructors.any { it.parameterTypes.isEmpty() }
            val hasStringConstructor = constructors.any { constructor ->
                constructor.parameterTypes.size == 1 && constructor.parameterTypes.first() == String::class.java
            }
            val nativeLibraryDirCandidate = if (hasStringConstructor) {
                "unknown"
            } else {
                null
            }
            BackendNpuStageProbeResult(
                constructorAvailable = hasNoArgConstructor,
                stringConstructorAvailable = hasStringConstructor,
                nativeLibraryDirCandidate = nativeLibraryDirCandidate,
                result = "safe",
            )
        }.getOrElse { throwable ->
            val result = when (throwable) {
                is ClassNotFoundException, is NoClassDefFoundError -> "missing"
                is SecurityException -> "blocked"
                else -> "error"
            }
            BackendNpuStageProbeResult(
                result = result,
                error = throwable.javaClass.simpleName,
            )
        }
    }

    private fun probeLiteRtLmNpuRequirementsSafely(): LiteRtLmNpuRequirementsProbeResult {
        return runCatching {
            val socManufacturer = readBuildStaticString("SOC_MANUFACTURER")
            val socModel = readBuildStaticString("SOC_MODEL")
            val normalizedSoc = socModel?.trim()?.uppercase(Locale.US)
            val officialVendor = when (normalizedSoc) {
                "SM8750", "SM8650", "SM8550" -> "qualcomm"
                "MT6989", "MT6991" -> "mediatek"
                else -> null
            }
            val officialSocSupport = when {
                normalizedSoc.isNullOrBlank() -> "unknown-soc-model"
                officialVendor != null -> "listed-${officialVendor}-soc"
                else -> "not-listed-in-current-litert-lm-npu-table"
            }
            val modelRequirement = when (officialVendor) {
                "qualcomm" -> if (normalizedSoc == "SM8750") {
                    "requires-soc-specific-qualcomm-litertlm-for-sm8750"
                } else {
                    "requires-soc-specific-qualcomm-litertlm"
                }
                "mediatek" -> "requires-soc-specific-vendor-litertlm"
                else -> "requires-supported-soc-specific-litertlm-model"
            }
            val runtimeLibraryRequirement = when (officialVendor) {
                "qualcomm" -> "requires-qairt-libs-libQnnHtp-libQnnSystem-libQnnHtpPrepare-skel"
                "mediatek" -> "requires-neuropilot-runtime-libs"
                else -> "requires-vendor-runtime-libs"
            }
            val dispatchLibraryRequirement = when (officialVendor) {
                "qualcomm" -> "requires-qualcomm-dispatch-api-so"
                "mediatek" -> "requires-mediatek-dispatch-api-so"
                else -> "requires-vendor-dispatch-api-so"
            }
            LiteRtLmNpuRequirementsProbeResult(
                socManufacturer = socManufacturer,
                socModel = socModel,
                officialVendor = officialVendor ?: "unknown",
                officialSocSupport = officialSocSupport,
                modelRequirement = modelRequirement,
                runtimeLibraryRequirement = runtimeLibraryRequirement,
                dispatchLibraryRequirement = dispatchLibraryRequirement,
                cliProofRequirement = "required-litert_lm_main-backend-npu-before-app-enable",
                readinessSummary = if (officialVendor == null) {
                    "blocked-until-supported-soc-model-runtime-libs-dispatch-so-and-cli-proof"
                } else {
                    "blocked-until-matching-model-runtime-libs-dispatch-so-and-cli-proof"
                },
            )
        }.getOrElse { throwable ->
            LiteRtLmNpuRequirementsProbeResult(
                officialVendor = "unknown",
                officialSocSupport = "error",
                modelRequirement = "requires-supported-soc-specific-litertlm-model",
                runtimeLibraryRequirement = "requires-vendor-runtime-libs",
                dispatchLibraryRequirement = "requires-vendor-dispatch-api-so",
                cliProofRequirement = "required-litert_lm_main-backend-npu-before-app-enable",
                readinessSummary = "error-${throwable.javaClass.simpleName}",
            )
        }
    }

    private fun readBuildStaticString(fieldName: String): String? {
        return runCatching {
            Build::class.java.getField(fieldName).get(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun probePackagedNpuLibrariesSafely(
        context: Context?,
        officialVendor: String,
    ): PackagedNpuLibraryProbeResult {
        return runCatching {
            val nativeLibraryDir = context?.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }
                ?: return PackagedNpuLibraryProbeResult(
                    vendorRuntimeLibraryStatus = "unknown-context-unavailable",
                    dispatchLibraryStatus = "native-library-dir-missing",
                    dispatchApiSearchError = "context-or-nativeLibraryDir-unavailable",
                )
            val nativeLibraryDirectory = File(nativeLibraryDir)
            val nativeLibraryDirExists = runCatching { nativeLibraryDirectory.isDirectory }.getOrDefault(false)
            val nativeLibrarySearchError = if (nativeLibraryDirExists) {
                null
            } else {
                "nativeLibraryDir-not-directory"
            }
            val nativeLibraryFilesFromDir = if (nativeLibraryDirExists) {
                runCatching {
                    nativeLibraryDirectory.listFiles()
                        ?.mapNotNull { file -> file.name.takeIf { file.isFile && it.endsWith(".so") } }
                        ?.sorted()
                        .orEmpty()
                }.getOrElse { throwable ->
                    return PackagedNpuLibraryProbeResult(
                        nativeLibraryDir = nativeLibraryDir,
                        nativeLibraryDirExists = nativeLibraryDirExists,
                        vendorRuntimeLibraryStatus = "error-${throwable.javaClass.simpleName}",
                        dispatchLibraryStatus = "unknown-error",
                        dispatchApiSearchDir = nativeLibraryDir,
                        dispatchApiSearchError = throwable.javaClass.simpleName,
                    )
                }
            } else {
                emptyList()
            }
            val nativeLibraryFiles = (nativeLibraryFilesFromDir + listApkNativeLibraries(context))
                .distinct()
                .sorted()
            val libraryCandidates = nativeLibraryFiles
                .filter { name -> matchesNpuLibraryKeyword(name) }
                .take(MAX_DELEGATE_CANDIDATE_COUNT)
            val dispatchApiCandidates = nativeLibraryFiles
                .filter(::isDispatchApiLibraryCandidate)
                .sorted()
            val qnnRuntimeCandidates = nativeLibraryFiles
                .filter(::isQnnRuntimeLibraryCandidate)
                .sorted()
            val htpSkelStubCandidates = nativeLibraryFiles
                .filter(::isHtpSkelStubLibraryCandidate)
                .sorted()
            val v79SkelStubCandidates = htpSkelStubCandidates
                .filter { name -> name.contains("V79", ignoreCase = true) }
                .sorted()
            val dispatchExactMatch = nativeLibraryFiles.any { name ->
                name.equals("libLiteRtDispatch_Qualcomm.so", ignoreCase = true)
            }
            val dispatchSelectedCandidate = dispatchApiCandidates.firstOrNull { name ->
                name.equals("libLiteRtDispatch_Qualcomm.so", ignoreCase = true)
            } ?: dispatchApiCandidates.firstOrNull()
            val vendorRuntimeLibraryStatus = when (officialVendor) {
                "qualcomm" -> buildRequiredLibraryStatus(
                    nativeLibraryFiles = nativeLibraryFiles,
                    requiredExactNames = listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpPrepare.so"),
                    requiredPrefixes = listOf("libQnnHtp", "libQnnHtpV"),
                    label = "qairt",
                )
                "mediatek" -> if (nativeLibraryFiles.any { it.contains("neuro", ignoreCase = true) || it.contains("mediatek", ignoreCase = true) || it.contains("mtk", ignoreCase = true) }) {
                    "candidate-detected-neuropilot"
                } else {
                    "missing-neuropilot-runtime-candidate"
                }
                else -> if (libraryCandidates.isNotEmpty()) "candidate-detected-unknown-vendor" else "missing-vendor-runtime-candidate"
            }
            val dispatchLibraryStatus = when {
                dispatchExactMatch -> "found-exact-libLiteRtDispatch_Qualcomm-so"
                dispatchApiCandidates.isNotEmpty() -> "found-dispatch-candidate"
                !nativeLibraryDirExists -> "native-library-dir-missing"
                nativeLibraryFiles.isEmpty() -> "native-library-dir-empty"
                else -> "missing"
            }
            PackagedNpuLibraryProbeResult(
                nativeLibraryDir = nativeLibraryDir,
                nativeLibraryDirExists = nativeLibraryDirExists,
                libraryCandidates = libraryCandidates,
                dispatchApiCandidates = dispatchApiCandidates,
                dispatchApiExactMatch = dispatchExactMatch,
                dispatchApiSelectedCandidate = dispatchSelectedCandidate,
                dispatchApiSearchDir = nativeLibraryDir,
                dispatchApiSearchError = nativeLibrarySearchError,
                qnnRuntimeCandidates = qnnRuntimeCandidates,
                htpSkelStubCandidates = htpSkelStubCandidates,
                v79SkelStubCandidates = v79SkelStubCandidates,
                vendorRuntimeLibraryStatus = vendorRuntimeLibraryStatus,
                dispatchLibraryStatus = dispatchLibraryStatus,
            )
        }.getOrElse { throwable ->
            PackagedNpuLibraryProbeResult(
                vendorRuntimeLibraryStatus = "error-${throwable.javaClass.simpleName}",
                dispatchLibraryStatus = "unknown-error",
                dispatchApiSearchError = "${throwable.javaClass.simpleName}:${throwable.message.orEmpty().take(120)}",
            )
        }
    }

    private fun listApkNativeLibraries(context: Context): List<String> {
        val applicationInfo = context.applicationInfo
        val apkPaths = listOfNotNull(applicationInfo.sourceDir) + applicationInfo.splitSourceDirs.orEmpty()
        return apkPaths.flatMap { apkPath ->
            runCatching {
                ZipFile(apkPath).use { zipFile ->
                    zipFile.entries().asSequence()
                        .map { it.name }
                        .filter { entryName -> entryName.startsWith("lib/") && entryName.endsWith(".so") }
                        .map { entryName -> entryName.substringAfterLast('/') }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun matchesNpuLibraryKeyword(name: String): Boolean {
        return listOf(
            "qnn",
            "htp",
            "hexagon",
            "skel",
            "dispatch",
            "neuro",
            "mediatek",
            "mtk",
        ).any { keyword -> name.contains(keyword, ignoreCase = true) }
    }

    private fun isDispatchApiLibraryCandidate(name: String): Boolean {
        if (!name.endsWith(".so")) return false
        val lower = name.lowercase(Locale.US)
        return dispatchApiLibraryNames.any { it.equals(name, ignoreCase = true) } ||
            "dispatch" in lower ||
            "litertdispatch" in lower ||
            (("qualcomm" in lower || "qnn" in lower) && "dispatch" in lower)
    }

    private fun isQnnRuntimeLibraryCandidate(name: String): Boolean {
        return qnnRuntimeLibraryNames.any { it.equals(name, ignoreCase = true) }
    }

    private fun isHtpSkelStubLibraryCandidate(name: String): Boolean {
        if (!name.endsWith(".so")) return false
        return htpSkelStubLibraryNames.any { it.equals(name, ignoreCase = true) } ||
            ((name.contains("Skel", ignoreCase = true) || name.contains("Stub", ignoreCase = true)) &&
                (name.contains("QnnHtp", ignoreCase = true) || name.contains("QnnDsp", ignoreCase = true)))
    }

    private fun buildRequiredLibraryStatus(
        nativeLibraryFiles: List<String>,
        requiredExactNames: List<String>,
        requiredPrefixes: List<String>,
        label: String,
    ): String {
        val missingExactNames = requiredExactNames.filterNot { required -> nativeLibraryFiles.contains(required) }
        val missingPrefixes = requiredPrefixes.filterNot { prefix -> nativeLibraryFiles.any { it.startsWith(prefix) } }
        return if (missingExactNames.isEmpty() && missingPrefixes.isEmpty()) {
            "candidate-detected-$label"
        } else {
            "missing-$label:${(missingExactNames + missingPrefixes.map { "$it*" }).joinToString(",")}"
        }
    }

    private fun buildNpuReadinessSummary(
        requirements: LiteRtLmNpuRequirementsProbeResult,
        packagedLibraries: PackagedNpuLibraryProbeResult,
    ): String {
        val blockers = buildList {
            if (!requirements.officialSocSupport.startsWith("listed-")) {
                add("supported-soc-model")
            }
            if (packagedLibraries.vendorRuntimeLibraryStatus?.startsWith("candidate-detected") != true) {
                add("vendor-runtime-libs")
            }
            if (packagedLibraries.dispatchLibraryStatus?.startsWith("found-") != true &&
                packagedLibraries.dispatchLibraryStatus != "candidate-detected"
            ) {
                add("dispatch-api-so")
            }
            add("soc-specific-model")
            add("litert_lm_main-backend-npu-proof")
        }
        return "blocked-until-${blockers.distinct().joinToString("-")}"
    }

    private fun buildQualcommQnnNpuAttemptSnapshot(
        requirements: LiteRtLmNpuRequirementsProbeResult,
        packagedLibraries: PackagedNpuLibraryProbeResult,
        delegateApiProbeResult: DelegateApiProbeResult,
    ): LocalAcceleratorAttemptSnapshot {
        val evidence = buildList {
            add("device=${Build.MANUFACTURER}/${Build.MODEL}/${Build.DEVICE}/${Build.BOARD}")
            add("hardware=${Build.HARDWARE}")
            add("soc=${requirements.socManufacturer ?: "unknown"}/${requirements.socModel ?: "unknown"}")
            add("androidSdk=${Build.VERSION.SDK_INT}")
            add("officialSocSupport=${requirements.officialSocSupport}")
            add("runtimeLibStatus=${packagedLibraries.vendorRuntimeLibraryStatus ?: "unknown"}")
            add("dispatchLibStatus=${packagedLibraries.dispatchLibraryStatus ?: "unknown"}")
            add("backendNpu=${delegateApiProbeResult.backendNpuProbeHint ?: "unknown"}")
            delegateApiProbeResult.backendNpuClassCandidates.takeIf { it.isNotEmpty() }?.let {
                add("foundBackendNpuClasses=${it.joinToString(",")}")
            }
            delegateApiProbeResult.qnnDelegateCandidates.takeIf { it.isNotEmpty() }?.let {
                add("foundQnnCandidates=${it.joinToString(",")}")
            }
            packagedLibraries.libraryCandidates.takeIf { it.isNotEmpty() }?.let {
                add("foundNativeLibCandidates=${it.joinToString(",")}")
            }
        }
        val isQualcommCandidate = requirements.officialVendor == "qualcomm" ||
            listOf(Build.MANUFACTURER, Build.HARDWARE, Build.BOARD, requirements.socManufacturer, requirements.socModel)
                .any { value -> value?.contains("qcom", ignoreCase = true) == true || value?.contains("qualcomm", ignoreCase = true) == true || value?.contains("sm8750", ignoreCase = true) == true }
        if (!isQualcommCandidate) {
            return LocalAcceleratorAttemptSnapshot(
                requested = "auto",
                attempted = false,
                available = "unsupported",
                selectedPath = "gpu",
                fallbackPath = "gpu",
                stage = "soc-probe",
                errorClass = "UnsupportedSoc",
                errorMessage = "Qualcomm QNN/NPU candidate not detected",
                evidence = evidence,
            )
        }
        val runtimeReady = packagedLibraries.vendorRuntimeLibraryStatus?.startsWith("candidate-detected") == true
        val dispatchReady = packagedLibraries.dispatchLibraryStatus == "candidate-detected"
        val dispatchReadyV2 = packagedLibraries.dispatchLibraryStatus?.startsWith("found-") == true
        val backendNpuReady = delegateApiProbeResult.backendNpuClassCandidates.isNotEmpty() ||
            delegateApiProbeResult.backendNpuConstructorSignatures.isNotEmpty()
        val missing = buildList {
            if (!runtimeReady) add("qnn-runtime-libs")
            if (!dispatchReady && !dispatchReadyV2) add("dispatch-api-so")
            if (!backendNpuReady) add("backend-npu-api")
        }
        return if (missing.isEmpty()) {
            LocalAcceleratorAttemptSnapshot(
                requested = "auto",
                attempted = false,
                available = "available-candidate",
                selectedPath = "gpu",
                fallbackPath = "gpu",
                stage = "prerequisite-probe",
                errorClass = null,
                errorMessage = null,
                evidence = evidence + "npuApplyStatus=disabled-probe-only",
            )
        } else {
            LocalAcceleratorAttemptSnapshot(
                requested = "auto",
                attempted = false,
                available = "unsupported",
                selectedPath = "gpu",
                fallbackPath = "gpu",
                stage = "prerequisite-probe",
                errorClass = "MissingPrerequisite",
                errorMessage = missing.joinToString(","),
                evidence = evidence + "missing=${missing.joinToString(",")}",
            )
        }
    }

    private fun collectDelegateCandidates(
        clazz: Class<*>,
        optionCandidates: MutableSet<String>,
        backendCandidates: MutableSet<String>,
        classCandidates: MutableSet<String>,
    ) {
        runCatching {
            (clazz.methods.asList() + clazz.declaredMethods.asList()).forEach { method ->
                if (containsDelegateKeyword(method.name)) {
                    optionCandidates += "${clazz.simpleName}.${method.name}"
                }
            }
        }
        runCatching {
            (clazz.fields.asList() + clazz.declaredFields.asList()).forEach { field ->
                if (containsDelegateKeyword(field.name)) {
                    optionCandidates += "${clazz.simpleName}.${field.name}"
                }
            }
        }
        runCatching {
            (clazz.classes.asList() + clazz.declaredClasses.asList()).forEach { nestedClass ->
                val simpleName = nestedClass.simpleName.orEmpty()
                val qualifiedName = "${clazz.simpleName}.$simpleName"
                if (containsDelegateKeyword(simpleName)) {
                    backendCandidates += qualifiedName
                }
                if (nestedClass.isEnum || simpleName.contains("backend", ignoreCase = true) || simpleName.contains("delegate", ignoreCase = true)) {
                    backendCandidates += qualifiedName
                }
                if (containsDelegateKeyword(simpleName) || simpleName.contains("option", ignoreCase = true) || simpleName.contains("builder", ignoreCase = true)) {
                    classCandidates += qualifiedName
                }
            }
        }
    }

    private fun containsDelegateKeyword(name: String): Boolean {
        val lowerName = name.lowercase(Locale.US)
        return DELEGATE_KEYWORDS.any(lowerName::contains)
    }

    private fun probeBackendEnumValues(
        classCandidates: List<String>,
        backendCandidates: List<String>,
    ): BackendEnumProbeResult {
        val candidateNames = linkedSetOf(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$Backend",
            "com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend",
        )
        classCandidates.forEach { candidateNames += toLikelyFqcnVariants(it) }
        backendCandidates.forEach { candidateNames += toLikelyFqcnVariants(it) }

        var lastError: String? = null
        candidateNames.forEach { className ->
            val clazz = runCatching { Class.forName(className) }.getOrElse {
                lastError = it.javaClass.simpleName
                return@forEach
            }
            if (!clazz.isEnum) {
                lastError = "not-enum"
                return@forEach
            }
            val enumValues = runCatching {
                (clazz.enumConstants ?: emptyArray<Any>())
                    .mapNotNull { constant -> runCatching { constant.toString() }.getOrNull() }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(MAX_DELEGATE_CANDIDATE_COUNT)
            }.getOrElse {
                lastError = it.javaClass.simpleName
                emptyList()
            }
            if (enumValues.isNotEmpty()) {
                return BackendEnumProbeResult(values = enumValues)
            }
        }
        return BackendEnumProbeResult(error = lastError)
    }

    private fun toLikelyFqcnVariants(candidate: String): List<String> {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return emptyList()
        val suffix = trimmed.removePrefix("LlmInference.")
        if (suffix == trimmed) return emptyList()
        return listOf(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference.$suffix",
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$${suffix.replace('.', '$')}",
        )
    }

    private fun probePreferredBackendSignatures(
        classesToInspect: List<String>,
        classCandidates: List<String>,
    ): PreferredBackendSignatureProbeResult {
        val classNames = linkedSetOf<String>()
        classNames += classesToInspect
        classNames += "com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions"
        classNames += "com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions\$Builder"
        classCandidates.forEach { candidate ->
            classNames += toLikelyFqcnVariants(candidate)
            if (candidate.contains("LlmInferenceOptions")) {
                classNames += "com.google.mediapipe.tasks.genai.llminference.${candidate.removePrefix("LlmInference.")}"
            }
        }
        val signatures = linkedSetOf<String>()
        var lastError: String? = null
        classNames.forEach { className ->
            val clazz = runCatching { Class.forName(className) }.getOrElse {
                lastError = it.javaClass.simpleName
                return@forEach
            }
            runCatching {
                (clazz.methods.asList() + clazz.declaredMethods.asList()).forEach { method ->
                    if (matchesPreferredBackendMethod(method)) {
                        signatures += formatMethodSignature(clazz, method)
                    }
                }
            }.onFailure { throwable ->
                lastError = throwable.javaClass.simpleName
            }
        }
        return PreferredBackendSignatureProbeResult(
            signatures = signatures.take(MAX_DELEGATE_CANDIDATE_COUNT),
            error = if (signatures.isEmpty()) lastError else null,
        )
    }

    private fun matchesPreferredBackendMethod(method: java.lang.reflect.Method): Boolean {
        val name = method.name
        if (name == "setPreferredBackend") return true
        if (name.contains("preferredBackend", ignoreCase = true)) return true
        if (!name.contains("backend", ignoreCase = true)) return false
        return method.parameterTypes.any { parameterType ->
            parameterType.simpleName.contains("Backend", ignoreCase = true) ||
                parameterType.canonicalName.orEmpty().contains("Backend", ignoreCase = true)
        }
    }

    private fun formatMethodSignature(clazz: Class<*>, method: java.lang.reflect.Method): String {
        val params = method.parameterTypes.joinToString(", ") { it.simpleName.ifBlank { "Unknown" } }
        val returnType = method.returnType.simpleName.ifBlank { "Unknown" }
        return "${clazz.simpleName}.${method.name}($params): $returnType"
    }

    private fun formatConstructorSignature(clazz: Class<*>, constructor: Constructor<*>): String {
        val params = constructor.parameterTypes.joinToString(", ") { type ->
            type.simpleName.ifBlank { type.name.substringAfterLast('.') }
        }
        return "${clazz.simpleName}($params)"
    }

    private fun inferDelegateHint(
        optionCandidates: List<String>,
        backendCandidates: List<String>,
        classCandidates: List<String>,
    ): String {
        val hasDelegateApiCandidate = optionCandidates.any { candidate ->
            candidate.contains("set", ignoreCase = true) || candidate.contains("preferred", ignoreCase = true)
        }
        if (hasDelegateApiCandidate) return "delegate-api-candidate-detected"
        if (backendCandidates.isNotEmpty()) return "backend-enum-detected"
        if (optionCandidates.isNotEmpty() || classCandidates.isNotEmpty()) return "options-candidate-detected"
        return "not-detected"
    }

    private fun collectKeywordCandidates(
        vararg groups: List<String>,
        keywords: List<String>,
    ): List<String> {
        return groups.asSequence()
            .flatMap { it.asSequence() }
            .filter { candidate -> keywords.any { keyword -> candidate.contains(keyword, ignoreCase = true) } }
            .distinct()
            .take(MAX_DELEGATE_CANDIDATE_COUNT)
            .toList()
    }

    private fun inferNpuProbeHint(
        npuCandidates: List<String>,
        qnnCandidates: List<String>,
        nnapiCandidates: List<String>,
    ): String {
        if (qnnCandidates.isNotEmpty()) return "qnn-candidate-detected"
        if (npuCandidates.isNotEmpty()) return "npu-keyword-candidate-detected"
        if (nnapiCandidates.isNotEmpty()) return "nnapi-only-candidate"
        return "not-detected"
    }

    private data class DelegateApiProbeResult(
        val error: String? = null,
        val optionCandidates: List<String> = emptyList(),
        val backendCandidates: List<String> = emptyList(),
        val backendEnumValues: List<String> = emptyList(),
        val backendEnumProbeError: String? = null,
        val preferredBackendSignatures: List<String> = emptyList(),
        val preferredBackendSignatureProbeError: String? = null,
        val classCandidates: List<String> = emptyList(),
        val switchingSupportedHint: String = "unknown",
        val npuDelegateCandidates: List<String> = emptyList(),
        val npuBackendCandidates: List<String> = emptyList(),
        val backendNpuClassCandidates: List<String> = emptyList(),
        val backendNpuMethodCandidates: List<String> = emptyList(),
        val backendNpuConstructorSignatures: List<String> = emptyList(),
        val backendNpuNativeLibraryDirRequired: String? = null,
        val backendNpuProbeHint: String? = null,
        val backendNpuProbeError: String? = null,
        val qnnDelegateCandidates: List<String> = emptyList(),
        val nnapiDelegateCandidates: List<String> = emptyList(),
        val npuProbeHint: String = "unknown",
        val npuProbeError: String? = null,
    )

    private data class BackendEnumProbeResult(
        val values: List<String> = emptyList(),
        val error: String? = null,
    )

    private data class PreferredBackendSignatureProbeResult(
        val signatures: List<String> = emptyList(),
        val error: String? = null,
    )

    private data class BackendNpuProbeResult(
        val classCandidates: List<String> = emptyList(),
        val methodCandidates: List<String> = emptyList(),
        val constructorSignatures: List<String> = emptyList(),
        val nativeLibraryDirRequired: String = "unknown",
        val hint: String = "not-detected",
        val error: String? = null,
    )

    private data class BackendNpuStageProbeResult(
        val constructorAvailable: Boolean = false,
        val stringConstructorAvailable: Boolean = false,
        val nativeLibraryDirCandidate: String? = null,
        val result: String = "unknown",
        val error: String? = null,
    )

    private data class LiteRtLmNpuRequirementsProbeResult(
        val socManufacturer: String? = null,
        val socModel: String? = null,
        val officialVendor: String = "unknown",
        val officialSocSupport: String = "unknown",
        val modelRequirement: String = "unknown",
        val runtimeLibraryRequirement: String = "unknown",
        val dispatchLibraryRequirement: String = "unknown",
        val cliProofRequirement: String = "unknown",
        val readinessSummary: String = "unknown",
    )

    private data class PackagedNpuLibraryProbeResult(
        val nativeLibraryDir: String? = null,
        val libraryCandidates: List<String> = emptyList(),
        val nativeLibraryDirExists: Boolean? = null,
        val dispatchApiCandidates: List<String> = emptyList(),
        val dispatchApiExactMatch: Boolean? = null,
        val dispatchApiSelectedCandidate: String? = null,
        val dispatchApiSearchDir: String? = null,
        val dispatchApiSearchError: String? = null,
        val qnnRuntimeCandidates: List<String> = emptyList(),
        val htpSkelStubCandidates: List<String> = emptyList(),
        val v79SkelStubCandidates: List<String> = emptyList(),
        val vendorRuntimeLibraryStatus: String? = null,
        val dispatchLibraryStatus: String? = null,
    )

    private data class LocalAcceleratorAttemptSnapshot(
        val requested: String,
        val attempted: Boolean,
        val available: String,
        val selectedPath: String,
        val fallbackPath: String?,
        val stage: String?,
        val errorClass: String?,
        val errorMessage: String?,
        val evidence: List<String>,
    )

    private data class ExternalQairtStageProbeResult(
        val stagePath: String = EXTERNAL_QAIRT_STAGE_PATH,
        val stageStatus: String = "unknown",
        val qnnNetRunStatus: String = "unknown",
        val qnnPlatformValidatorStatus: String = "unknown",
        val qnnSdkVersion: String = EXTERNAL_QAIRT_VERIFIED_SDK_VERSION,
        val gpuBackendStatus: String = EXTERNAL_QAIRT_VERIFIED_GPU_BACKEND_STATUS,
        val dspCore: String = EXTERNAL_QAIRT_VERIFIED_DSP_CORE,
        val dspBackendStatus: String = EXTERNAL_QAIRT_VERIFIED_DSP_BACKEND_STATUS,
        val note: String? = null,
    )

    private data class DispatchRuntimeCompatibilityProbeResult(
        val currentFlavor: String = "unknown",
        val applicationId: String = "unknown",
        val nativeLibraryDir: String? = null,
        val nativeLibraryDirExists: Boolean? = null,
        val dispatchRuntimePresent: Boolean = false,
        val dispatchRuntimeSource: String = "none",
        val dispatchRuntimeFilePath: String? = null,
        val dispatchRuntimeFileLength: Long? = null,
        val liteRtBuildId: String? = null,
        val liteRtLmJniBuildId: String? = null,
        val dispatchRuntimeBuildId: String? = null,
        val dispatchRuntimeSha256: String? = null,
        val dispatchRuntimeExpectedSha256Match: Boolean? = null,
        val abiCompatibility: String = "unknown",
    )

    private data class BackendNpuInstantiateProbeResult(
        val enabled: Boolean = false,
        val skipReason: String? = null,
        val nativeLibraryDirArgument: String? = null,
        val constructor: String? = null,
        val result: String = "skipped",
        val objectClass: String? = null,
        val exceptionClass: String? = null,
        val exceptionMessage: String? = null,
        val rootCause: String? = null,
        val causeChain: String? = null,
        val warning: String = "instantiate-only; object not passed to engine; no inference",
    )

    private data class BackendNpuAttachDryRunProbeResult(
        val enabled: Boolean = false,
        val skipReason: String? = null,
        val npuObjectClass: String? = null,
        val targetBuilderCandidates: List<String> = emptyList(),
        val setterCandidates: List<String> = emptyList(),
        val selectedSetter: String? = null,
        val setterInvokeResult: String = "skipped",
        val buildInvoked: String = "no",
        val buildResult: String = "skipped",
        val exceptionClass: String? = null,
        val exceptionMessage: String? = null,
        val rootCause: String? = null,
        val causeChain: String? = null,
        val warning: String = "attach-dry-run only; no Engine; no Conversation; no inference",
        val note: String = "This setter belongs to MediaPipe LlmInference.Backend enum path and is not assignable from LiteRT-LM Backend.NPU.",
    )

    private data class LiteRtLmNpuApiInventoryProbeResult(
        val enabled: Boolean = false,
        val skipReason: String? = null,
        val classInventory: List<String> = emptyList(),
        val constructorInventory: List<String> = emptyList(),
        val publicMethodInventory: List<String> = emptyList(),
        val declaredMethodInventory: List<String> = emptyList(),
        val fieldInventory: List<String> = emptyList(),
        val staticMethodInventory: List<String> = emptyList(),
        val assignability: List<String> = emptyList(),
        val engineConfigConstructorInventory: List<String> = emptyList(),
        val engineConfigBackendPropertyInventory: List<String> = emptyList(),
        val engineConfigCopyMethodInventory: List<String> = emptyList(),
        val engineConfigComponentMethodInventory: List<String> = emptyList(),
        val engineConfigJsonMethodInventory: List<String> = emptyList(),
    )

    private data class EngineConfigNpuDryBuildProbeResult(
        val enabled: Boolean = false,
        val skipReason: String? = null,
        val selectedConstructor: String? = null,
        val constructorArgsSummary: String? = null,
        val npuBackendObjectClass: String? = null,
        val result: String = "skipped",
        val createdObjectClass: String? = null,
        val backendGetterResultClass: String? = null,
        val exceptionClass: String? = null,
        val exceptionMessage: String? = null,
        val rootCause: String? = null,
        val causeChain: String? = null,
        val warning: String = "config-only; not passed to Engine; no inference",
    )

    private data class BackendNpuConnectionCandidateProbeResult(
        val preferredBackendEnumPath: String,
        val preferredBackendEnumReason: String,
        val engineConfigBackendPath: String,
        val engineInitializePath: String,
        val recommendedNextPhase: String,
    )

    private data class EngineConfigDryBuildArgs(
        val values: List<Any?>,
        val summary: List<String>,
    )

    private data class BackendNpuProbeObject(
        val instance: Any,
        val backendClass: Class<*>,
        val npuClass: Class<*>,
    )

    private data class BackendNpuAttachDryRunTarget(
        val label: String,
        val builder: Any,
        val builderClass: Class<*>,
    )
}
