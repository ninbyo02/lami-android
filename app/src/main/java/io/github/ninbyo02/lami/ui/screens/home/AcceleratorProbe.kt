package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

internal object AcceleratorProbe {
    private const val LOG_TAG = "AcceleratorProbe"
    private const val MAX_DELEGATE_CANDIDATE_COUNT = 12
    private const val EXTERNAL_QAIRT_STAGE_PATH = "/data/local/tmp/qairt"
    private const val EXTERNAL_QAIRT_QNN_NET_RUN_PATH = "/data/local/tmp/qairt/bin/qnn-net-run"
    private const val EXTERNAL_QAIRT_VERIFIED_SDK_VERSION = "v2.46.0.260424121129"
    private const val EXTERNAL_QAIRT_VERIFIED_GPU_BACKEND_STATUS = "passed"
    private const val EXTERNAL_QAIRT_VERIFIED_DSP_CORE = "Hexagon Architecture V79"
    private const val EXTERNAL_QAIRT_VERIFIED_DSP_BACKEND_STATUS = "passed"
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
        val qnnNpuAttemptSnapshot = buildQualcommQnnNpuAttemptSnapshot(
            requirements = npuRequirementsProbeResult,
            packagedLibraries = npuPackagedLibraryProbeResult,
            delegateApiProbeResult = delegateApiProbeResult,
        )

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
            npuProbeHint = delegateApiProbeResult.npuProbeHint,
            npuProbeError = delegateApiProbeResult.npuProbeError,
            externalQairtStagePath = externalQairtStageProbeResult.stagePath,
            externalQairtStageStatus = externalQairtStageProbeResult.stageStatus,
            externalQairtQnnNetRunStatus = externalQairtStageProbeResult.qnnNetRunStatus,
            externalQairtQnnSdkVersion = externalQairtStageProbeResult.qnnSdkVersion,
            externalQairtGpuBackendStatus = externalQairtStageProbeResult.gpuBackendStatus,
            externalQairtDspCore = externalQairtStageProbeResult.dspCore,
            externalQairtDspBackendStatus = externalQairtStageProbeResult.dspBackendStatus,
            externalQairtNote = externalQairtStageProbeResult.note,
        )
    }

    private fun probeExternalQairtStageSafely(): ExternalQairtStageProbeResult {
        return runCatching {
            val stageDir = File(EXTERNAL_QAIRT_STAGE_PATH)
            val stagePresent = runCatching { stageDir.isDirectory }.getOrElse { throw it }
            val qnnNetRunAvailable = runCatching {
                val bin = File(EXTERNAL_QAIRT_QNN_NET_RUN_PATH)
                bin.exists() && bin.canRead()
            }.getOrElse { throw it }
            ExternalQairtStageProbeResult(
                stagePath = EXTERNAL_QAIRT_STAGE_PATH,
                stageStatus = if (stagePresent) "present" else "missing",
                qnnNetRunStatus = if (qnnNetRunAvailable) "available" else "unavailable",
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
                "qualcomm", "mediatek" -> "requires-soc-specific-gemma3-1b-litertlm"
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
                    dispatchLibraryStatus = "unknown-context-unavailable",
                )
            val nativeLibraryFilesFromDir = File(nativeLibraryDir).listFiles()
                ?.mapNotNull { file -> file.name.takeIf { it.endsWith(".so") } }
                ?.sorted()
                .orEmpty()
            val nativeLibraryFiles = (nativeLibraryFilesFromDir + listApkNativeLibraries(context))
                .distinct()
                .sorted()
            val libraryCandidates = nativeLibraryFiles
                .filter { name -> matchesNpuLibraryKeyword(name) }
                .take(MAX_DELEGATE_CANDIDATE_COUNT)
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
            val dispatchLibraryStatus = if (nativeLibraryFiles.any { name ->
                    name.contains("dispatch", ignoreCase = true) &&
                        (name.contains("litert", ignoreCase = true) ||
                            name.contains("qnn", ignoreCase = true) ||
                            name.contains("qualcomm", ignoreCase = true) ||
                            name.contains("mediatek", ignoreCase = true) ||
                            name.contains("mtk", ignoreCase = true))
                }
            ) {
                "candidate-detected"
            } else {
                "missing-dispatch-api-so-candidate"
            }
            PackagedNpuLibraryProbeResult(
                nativeLibraryDir = nativeLibraryDir,
                libraryCandidates = libraryCandidates,
                vendorRuntimeLibraryStatus = vendorRuntimeLibraryStatus,
                dispatchLibraryStatus = dispatchLibraryStatus,
            )
        }.getOrElse { throwable ->
            PackagedNpuLibraryProbeResult(
                vendorRuntimeLibraryStatus = "error-${throwable.javaClass.simpleName}",
                dispatchLibraryStatus = "error-${throwable.javaClass.simpleName}",
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
            if (packagedLibraries.dispatchLibraryStatus != "candidate-detected") {
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
        val backendNpuReady = delegateApiProbeResult.backendNpuClassCandidates.isNotEmpty() ||
            delegateApiProbeResult.backendNpuConstructorSignatures.isNotEmpty()
        val missing = buildList {
            if (!runtimeReady) add("qnn-runtime-libs")
            if (!dispatchReady) add("dispatch-api-so")
            if (!backendNpuReady) add("backend-npu-api")
        }
        return if (missing.isEmpty()) {
            LocalAcceleratorAttemptSnapshot(
                requested = "auto",
                attempted = true,
                available = "available-candidate",
                selectedPath = "qualcomm-qnn-npu-candidate",
                fallbackPath = null,
                stage = "prerequisite-probe",
                errorClass = null,
                errorMessage = null,
                evidence = evidence,
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
        val stageStatus: String = "not_checked",
        val qnnNetRunStatus: String = "not_checked",
        val qnnSdkVersion: String = EXTERNAL_QAIRT_VERIFIED_SDK_VERSION,
        val gpuBackendStatus: String = EXTERNAL_QAIRT_VERIFIED_GPU_BACKEND_STATUS,
        val dspCore: String = EXTERNAL_QAIRT_VERIFIED_DSP_CORE,
        val dspBackendStatus: String = EXTERNAL_QAIRT_VERIFIED_DSP_BACKEND_STATUS,
        val note: String? = null,
    )
}
