package io.github.ninbyo02.lami.ui.screens.home

internal const val NPU_PERSISTENT_HOLDER_API_PROBE_TEST_NAME = "NPU Persistent Holder API Probe"
internal const val NPU_PERSISTENT_HOLDER_API_STATUS_NOT_EXPOSED = "not_exposed"
internal const val NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED = "not_implemented"
internal const val NPU_PERSISTENT_HOLDER_API_REASON_NEEDS_NATIVE_JNI_SUPPORT = "needs_native_jni_support"
internal const val NPU_PERSISTENT_HOLDER_API_RECOMMENDED_NEXT_STEP =
    "implement_dev_only_native_holder_api"
internal const val NPU_PERSISTENT_HOLDER_CREATE_CLOSE_PROBE_TEST_NAME =
    "NPU Persistent Holder Create Close Probe"
internal const val NPU_PERSISTENT_HOLDER_NATIVE_STUB_VERSION =
    "dev_only_standard_route_adapter_holder_create_close_v1"
internal const val NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON =
    "dev_only_native_holder_stub_no_engine_create"
internal const val NPU_PERSISTENT_HOLDER_NATIVE_STUB_RECOMMENDED_NEXT_STEP =
    "review_create_close_device_result_then_implement_run_once_without_multi_turn"

internal data class NpuPersistentHolderCreateRequest(
    val modelPath: String,
    val nativeLibraryDir: String,
    val cacheDir: String,
    val maxTokens: Int,
)

internal data class NpuPersistentHolderRunRequest(
    val holderId: String,
    val prompt: String,
    val maxOutputTokens: Int,
)

internal data class NpuPersistentHolderCloseRequest(
    val holderId: String,
    val reason: String,
)

internal data class NpuPersistentHolderApiResult(
    val status: String = NPU_PERSISTENT_HOLDER_API_STATUS_NOT_EXPOSED,
    val reason: String = NPU_PERSISTENT_HOLDER_API_REASON_NEEDS_NATIVE_JNI_SUPPORT,
    val holderId: String = "unavailable",
    val diagnostics: NpuPersistentHolderApiDiagnostics = NpuPersistentHolderApiDiagnostics(),
    val nativeSummary: String = "",
)

internal data class NpuPersistentHolderApiDiagnostics(
    val holderApiAvailable: Boolean = false,
    val holderApiReason: String = NPU_PERSISTENT_HOLDER_API_REASON_NEEDS_NATIVE_JNI_SUPPORT,
    val nativeHolderStubAvailable: Boolean = false,
    val nativeHolderCreateCloseAvailable: Boolean = false,
    val nativeHolderStubVersion: String = "unavailable",
    val nativeCreateDeclared: Boolean = false,
    val nativeRunDeclared: Boolean = false,
    val nativeCloseDeclared: Boolean = false,
    val nativeDiagnosticsDeclared: Boolean = false,
    val nativeCreateCalled: Boolean = false,
    val nativeRunCalled: Boolean = false,
    val nativeCloseCalled: Boolean = false,
    val nativeDiagnosticsCalled: Boolean = false,
    val holderCreateRequested: Boolean = false,
    val holderCreateCalled: Boolean = false,
    val holderCreateSucceeded: Boolean = false,
    val holderId: String = "unavailable",
    val holderOpen: Boolean = false,
    val holderCloseRequested: Boolean = false,
    val holderCloseCalled: Boolean = false,
    val holderCloseSucceeded: Boolean = false,
    val holderDoubleCloseSafe: Boolean = false,
    val holderFatalLatch: Boolean = false,
    val holderFatalReason: String = "unavailable",
    val engineFactoryCreateCalled: Boolean = false,
    val engineCreateCalled: Boolean = false,
    val modelAssetsCreateCalled: Boolean = false,
    val engineSettingsCreateCalled: Boolean = false,
    val npuDecodeCalled: Boolean = false,
    val generateCalled: Boolean = false,
    val qnnDecodeCalled: Boolean = false,
    val qnnCalled: Boolean = false,
    val runOnceSupported: Boolean = false,
    val restartAppRecommended: Boolean = false,
    val status: String = NPU_PERSISTENT_HOLDER_API_STATUS_NOT_EXPOSED,
    val reason: String = NPU_PERSISTENT_HOLDER_API_REASON_NEEDS_NATIVE_JNI_SUPPORT,
    val holderCreateSupported: Boolean = false,
    val holderRunSupported: Boolean = false,
    val holderCloseSupported: Boolean = false,
    val holderDiagnosticsSupported: Boolean = false,
    val persistentMultiTurnPossible: Boolean = false,
    val engineReuseObserved: String = "unavailable",
    val sessionApiSupportedForNpu: Boolean = false,
    val sessionApiBlockReason: String =
        NPU_S1_PERSISTENT_ENGINE_SESSION_API_NPU_BLOCK_REASON,
    val standardRouteAdapterDecodeSuccessKnown: Boolean = true,
    val standardRouteBackendEvidence: String =
        NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
    val engineLifecycleVisibility: String = "partial",
    val requiredNativeApi: String =
        "create_holder,run_holder_once,close_holder,get_holder_diagnostics",
    val recommendedNextStep: String = NPU_PERSISTENT_HOLDER_API_RECOMMENDED_NEXT_STEP,
)

internal interface NpuPersistentHolderApi {
    fun createHolder(request: NpuPersistentHolderCreateRequest): NpuPersistentHolderApiResult

    fun runOnce(request: NpuPersistentHolderRunRequest): NpuPersistentHolderApiResult

    fun closeHolder(request: NpuPersistentHolderCloseRequest): NpuPersistentHolderApiResult

    fun getDiagnostics(holderId: String): NpuPersistentHolderApiDiagnostics
}

internal object NotExposedNpuPersistentHolderApi : NpuPersistentHolderApi {
    override fun createHolder(request: NpuPersistentHolderCreateRequest): NpuPersistentHolderApiResult =
        NpuPersistentHolderApiResult()

    override fun runOnce(request: NpuPersistentHolderRunRequest): NpuPersistentHolderApiResult =
        NpuPersistentHolderApiResult(holderId = request.holderId)

    override fun closeHolder(request: NpuPersistentHolderCloseRequest): NpuPersistentHolderApiResult =
        NpuPersistentHolderApiResult(holderId = request.holderId)

    override fun getDiagnostics(holderId: String): NpuPersistentHolderApiDiagnostics =
        NpuPersistentHolderApiDiagnostics()
}

internal fun formatNpuPersistentHolderApiProbeSummary(
    diagnostics: NpuPersistentHolderApiDiagnostics = NotExposedNpuPersistentHolderApi.getDiagnostics(
        holderId = "unavailable",
    ),
): String = buildString {
    appendLine("[DEV診断: NPU persistent holder API summary]")
    appendLine("test_name=$NPU_PERSISTENT_HOLDER_API_PROBE_TEST_NAME")
    appendLine("holder_api_available=${diagnostics.holderApiAvailable}")
    appendLine("holder_api_reason=${diagnostics.holderApiReason}")
    appendLine("native_holder_stub_available=${diagnostics.nativeHolderStubAvailable}")
    appendLine("native_holder_create_close_available=${diagnostics.nativeHolderCreateCloseAvailable}")
    appendLine("native_holder_stub_version=${diagnostics.nativeHolderStubVersion}")
    appendLine("holder_create_supported=${diagnostics.holderCreateSupported}")
    appendLine("holder_run_supported=${diagnostics.holderRunSupported}")
    appendLine("holder_close_supported=${diagnostics.holderCloseSupported}")
    appendLine("holder_diagnostics_supported=${diagnostics.holderDiagnosticsSupported}")
    appendLine("persistent_multi_turn_possible=${diagnostics.persistentMultiTurnPossible}")
    appendLine("engine_reuse_observed=${diagnostics.engineReuseObserved}")
    appendLine("session_api_supported_for_npu=${diagnostics.sessionApiSupportedForNpu}")
    appendLine("session_api_block_reason=${diagnostics.sessionApiBlockReason}")
    appendLine("standard_route_adapter_decode_success_known=${diagnostics.standardRouteAdapterDecodeSuccessKnown}")
    appendLine("standard_route_backend_evidence=${diagnostics.standardRouteBackendEvidence}")
    appendLine("engine_lifecycle_visibility=${diagnostics.engineLifecycleVisibility}")
    appendLine("required_native_api=${diagnostics.requiredNativeApi}")
    appendLine("recommended_next_step=${diagnostics.recommendedNextStep}")
}.trimEnd()

internal fun formatNpuPersistentHolderNativeStubProbeSummary(
    diagnostics: NpuPersistentHolderApiDiagnostics = npuPersistentHolderNativeStubDiagnostics(),
): String = buildString {
    appendLine("[DEV診断: NPU persistent holder create close summary]")
    appendLine("test_name=$NPU_PERSISTENT_HOLDER_CREATE_CLOSE_PROBE_TEST_NAME")
    appendLine("holder_api_available=${diagnostics.holderApiAvailable}")
    appendLine("native_holder_stub_available=${diagnostics.nativeHolderStubAvailable}")
    appendLine("native_holder_create_close_available=${diagnostics.nativeHolderCreateCloseAvailable}")
    appendLine("native_holder_stub_version=${diagnostics.nativeHolderStubVersion}")
    appendLine("native_create_declared=${diagnostics.nativeCreateDeclared}")
    appendLine("native_run_declared=${diagnostics.nativeRunDeclared}")
    appendLine("native_close_declared=${diagnostics.nativeCloseDeclared}")
    appendLine("native_diagnostics_declared=${diagnostics.nativeDiagnosticsDeclared}")
    appendLine("native_create_called=${diagnostics.nativeCreateCalled}")
    appendLine("native_run_called=${diagnostics.nativeRunCalled}")
    appendLine("native_close_called=${diagnostics.nativeCloseCalled}")
    appendLine("native_diagnostics_called=${diagnostics.nativeDiagnosticsCalled}")
    appendLine("holder_create_requested=${diagnostics.holderCreateRequested}")
    appendLine("holder_create_called=${diagnostics.holderCreateCalled}")
    appendLine("holder_create_succeeded=${diagnostics.holderCreateSucceeded}")
    appendLine("holder_id=${diagnostics.holderId}")
    appendLine("holder_open=${diagnostics.holderOpen}")
    appendLine("holder_close_requested=${diagnostics.holderCloseRequested}")
    appendLine("holder_close_called=${diagnostics.holderCloseCalled}")
    appendLine("holder_close_succeeded=${diagnostics.holderCloseSucceeded}")
    appendLine("holder_double_close_safe=${diagnostics.holderDoubleCloseSafe}")
    appendLine("holder_fatal_latch=${diagnostics.holderFatalLatch}")
    appendLine("holder_fatal_reason=${diagnostics.holderFatalReason}")
    appendLine("holder_create_supported=${diagnostics.holderCreateSupported}")
    appendLine("holder_run_supported=${diagnostics.holderRunSupported}")
    appendLine("holder_close_supported=${diagnostics.holderCloseSupported}")
    appendLine("holder_diagnostics_supported=${diagnostics.holderDiagnosticsSupported}")
    appendLine("engine_factory_create_called=${diagnostics.engineFactoryCreateCalled}")
    appendLine("engine_create_called=${diagnostics.engineCreateCalled}")
    appendLine("model_assets_create_called=${diagnostics.modelAssetsCreateCalled}")
    appendLine("engine_settings_create_called=${diagnostics.engineSettingsCreateCalled}")
    appendLine("npu_decode_called=${diagnostics.npuDecodeCalled}")
    appendLine("generate_called=${diagnostics.generateCalled}")
    appendLine("qnn_decode_called=${diagnostics.qnnDecodeCalled}")
    appendLine("qnn_called=${diagnostics.qnnCalled}")
    appendLine("run_once_supported=${diagnostics.runOnceSupported}")
    appendLine("status=${diagnostics.status}")
    appendLine("reason=${diagnostics.reason}")
    appendLine("persistent_multi_turn_possible=${diagnostics.persistentMultiTurnPossible}")
    appendLine("engine_reuse_observed=${diagnostics.engineReuseObserved}")
    appendLine("restart_app_recommended=${diagnostics.restartAppRecommended}")
    appendLine("recommended_next_step=${diagnostics.recommendedNextStep}")
}.trimEnd()

internal fun npuPersistentHolderNativeStubDiagnostics(
    nativeCreateCalled: Boolean = false,
    nativeRunCalled: Boolean = false,
    nativeCloseCalled: Boolean = false,
    nativeDiagnosticsCalled: Boolean = false,
    holderCreateRequested: Boolean = nativeCreateCalled,
    holderCreateSucceeded: Boolean = false,
    holderId: String = "unavailable",
    holderOpen: Boolean = false,
    holderCloseRequested: Boolean = nativeCloseCalled,
    holderCloseSucceeded: Boolean = false,
    holderDoubleCloseSafe: Boolean = true,
    holderFatalLatch: Boolean = false,
    holderFatalReason: String = "none",
    restartAppRecommended: Boolean = holderFatalLatch,
    status: String = NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED,
    reason: String = NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
): NpuPersistentHolderApiDiagnostics = NpuPersistentHolderApiDiagnostics(
    holderApiAvailable = true,
    holderApiReason = NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
    nativeHolderStubAvailable = true,
    nativeHolderCreateCloseAvailable = true,
    nativeHolderStubVersion = NPU_PERSISTENT_HOLDER_NATIVE_STUB_VERSION,
    nativeCreateDeclared = true,
    nativeRunDeclared = true,
    nativeCloseDeclared = true,
    nativeDiagnosticsDeclared = true,
    nativeCreateCalled = nativeCreateCalled,
    nativeRunCalled = nativeRunCalled,
    nativeCloseCalled = nativeCloseCalled,
    nativeDiagnosticsCalled = nativeDiagnosticsCalled,
    holderCreateRequested = holderCreateRequested,
    holderCreateCalled = nativeCreateCalled,
    holderCreateSucceeded = holderCreateSucceeded,
    holderId = holderId,
    holderOpen = holderOpen,
    holderCloseRequested = holderCloseRequested,
    holderCloseCalled = nativeCloseCalled,
    holderCloseSucceeded = holderCloseSucceeded,
    holderDoubleCloseSafe = holderDoubleCloseSafe,
    holderFatalLatch = holderFatalLatch,
    holderFatalReason = holderFatalReason,
    engineFactoryCreateCalled = false,
    engineCreateCalled = false,
    modelAssetsCreateCalled = false,
    engineSettingsCreateCalled = false,
    npuDecodeCalled = false,
    generateCalled = false,
    qnnDecodeCalled = false,
    qnnCalled = false,
    runOnceSupported = false,
    restartAppRecommended = restartAppRecommended,
    status = status,
    reason = reason,
    holderCreateSupported = true,
    holderRunSupported = false,
    holderCloseSupported = true,
    holderDiagnosticsSupported = true,
    persistentMultiTurnPossible = false,
    engineReuseObserved = "unavailable",
    recommendedNextStep = NPU_PERSISTENT_HOLDER_NATIVE_STUB_RECOMMENDED_NEXT_STEP,
)

internal fun mergeNpuPersistentHolderNativeStubDiagnostics(
    diagnostics: Iterable<NpuPersistentHolderApiDiagnostics>,
): NpuPersistentHolderApiDiagnostics {
    val values = diagnostics.toList()
    return npuPersistentHolderNativeStubDiagnostics(
        nativeCreateCalled = values.any { it.nativeCreateCalled },
        nativeRunCalled = values.any { it.nativeRunCalled },
        nativeCloseCalled = values.any { it.nativeCloseCalled },
        nativeDiagnosticsCalled = values.any { it.nativeDiagnosticsCalled },
        holderCreateRequested = values.any { it.holderCreateRequested },
        holderCreateSucceeded = values.any { it.holderCreateSucceeded },
        holderId = values.lastOrNull { it.holderId != "unavailable" }?.holderId ?: "unavailable",
        holderOpen = values.lastOrNull()?.holderOpen == true,
        holderCloseRequested = values.any { it.holderCloseRequested },
        holderCloseSucceeded = values.any { it.holderCloseSucceeded },
        holderDoubleCloseSafe = values.all { it.holderDoubleCloseSafe },
        holderFatalLatch = values.any { it.holderFatalLatch },
        holderFatalReason = values.lastOrNull { it.holderFatalLatch }?.holderFatalReason ?: "none",
        restartAppRecommended = values.any { it.restartAppRecommended },
    )
}

internal fun parseNpuPersistentHolderNativeStubResult(
    nativeSummary: String,
    fallbackHolderId: String = "unavailable",
): NpuPersistentHolderApiResult {
    val values = nativeSummary
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && "=" in it }
        .associate { line ->
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim()
            key to value
        }
    val diagnostics = npuPersistentHolderNativeStubDiagnostics(
        nativeCreateCalled = values.booleanValue("native_create_called"),
        nativeRunCalled = values.booleanValue("native_run_called"),
        nativeCloseCalled = values.booleanValue("native_close_called"),
        nativeDiagnosticsCalled = values.booleanValue("native_diagnostics_called"),
        holderCreateRequested = values.booleanValue("holder_create_requested"),
        holderCreateSucceeded = values.booleanValue("holder_create_succeeded"),
        holderId = values["holder_id"] ?: fallbackHolderId,
        holderOpen = values.booleanValue("holder_open"),
        holderCloseRequested = values.booleanValue("holder_close_requested"),
        holderCloseSucceeded = values.booleanValue("holder_close_succeeded"),
        holderDoubleCloseSafe = values.booleanValue("holder_double_close_safe"),
        holderFatalLatch = values.booleanValue("holder_fatal_latch"),
        holderFatalReason = values["holder_fatal_reason"] ?: "unavailable",
        restartAppRecommended = values.booleanValue("restart_app_recommended"),
        status = values["status"] ?: NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED,
        reason = values["reason"] ?: NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
    )
    return NpuPersistentHolderApiResult(
        status = values["status"] ?: NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED,
        reason = values["reason"] ?: NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
        holderId = values["holder_id"] ?: fallbackHolderId,
        diagnostics = diagnostics,
        nativeSummary = nativeSummary,
    )
}

private fun Map<String, String>.booleanValue(key: String): Boolean =
    this[key]?.equals("true", ignoreCase = true) == true
