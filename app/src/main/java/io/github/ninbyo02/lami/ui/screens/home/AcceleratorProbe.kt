package io.github.ninbyo02.lami.ui.screens.home

import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.util.Locale

internal object AcceleratorProbe {
    private const val LOG_TAG = "AcceleratorProbe"
    private const val MAX_DELEGATE_CANDIDATE_COUNT = 12
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

    fun captureSnapshot(forceRefresh: Boolean = false): AcceleratorProbeSnapshot {
        if (!forceRefresh) {
            cachedSnapshot?.let { return it }
        }

        val snapshot = captureSnapshotUncached()
        cachedSnapshot = snapshot
        maybeLogOnce(snapshot)
        return snapshot
    }

    private fun captureSnapshotUncached(): AcceleratorProbeSnapshot {
        // NPU/QNN/NNAPI は現時点では候補検出のみを行い、実適用は公式API/Delegateの確認後に扱う。
        // DEFAULT/CPU/GPU の EngineConfig 指定フローとは分離して診断情報のみ提供する。
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
            qnnDelegateCandidates = delegateApiProbeResult.qnnDelegateCandidates,
            nnapiDelegateCandidates = delegateApiProbeResult.nnapiDelegateCandidates,
            npuProbeHint = delegateApiProbeResult.npuProbeHint,
            npuProbeError = delegateApiProbeResult.npuProbeError,
        )
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
}
