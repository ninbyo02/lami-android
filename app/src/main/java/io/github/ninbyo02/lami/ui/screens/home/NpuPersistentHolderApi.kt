package io.github.ninbyo02.lami.ui.screens.home

internal const val NPU_PERSISTENT_HOLDER_API_PROBE_TEST_NAME = "NPU Persistent Holder API Probe"
internal const val NPU_PERSISTENT_HOLDER_API_STATUS_NOT_EXPOSED = "not_exposed"
internal const val NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED = "not_implemented"
internal const val NPU_PERSISTENT_HOLDER_API_REASON_NEEDS_NATIVE_JNI_SUPPORT = "needs_native_jni_support"
internal const val NPU_PERSISTENT_HOLDER_API_RECOMMENDED_NEXT_STEP =
    "implement_dev_only_native_holder_api"
internal const val NPU_PERSISTENT_HOLDER_NATIVE_STUB_PROBE_TEST_NAME =
    "NPU Persistent Holder Native Stub Probe"
internal const val NPU_PERSISTENT_HOLDER_NATIVE_STUB_VERSION =
    "dev_only_standard_route_adapter_holder_stub_v1"
internal const val NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON =
    "dev_only_native_holder_stub_no_engine_create"
internal const val NPU_PERSISTENT_HOLDER_NATIVE_STUB_RECOMMENDED_NEXT_STEP =
    "implement_native_create_close_without_decode"

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
    val nativeHolderStubVersion: String = "unavailable",
    val nativeCreateDeclared: Boolean = false,
    val nativeRunDeclared: Boolean = false,
    val nativeCloseDeclared: Boolean = false,
    val nativeDiagnosticsDeclared: Boolean = false,
    val nativeCreateCalled: Boolean = false,
    val nativeRunCalled: Boolean = false,
    val nativeCloseCalled: Boolean = false,
    val nativeDiagnosticsCalled: Boolean = false,
    val engineCreateCalled: Boolean = false,
    val modelAssetsCreateCalled: Boolean = false,
    val npuDecodeCalled: Boolean = false,
    val qnnCalled: Boolean = false,
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
    appendLine("[DEV診断: NPU persistent holder native stub summary]")
    appendLine("test_name=$NPU_PERSISTENT_HOLDER_NATIVE_STUB_PROBE_TEST_NAME")
    appendLine("holder_api_available=${diagnostics.holderApiAvailable}")
    appendLine("native_holder_stub_available=${diagnostics.nativeHolderStubAvailable}")
    appendLine("native_holder_stub_version=${diagnostics.nativeHolderStubVersion}")
    appendLine("native_create_declared=${diagnostics.nativeCreateDeclared}")
    appendLine("native_run_declared=${diagnostics.nativeRunDeclared}")
    appendLine("native_close_declared=${diagnostics.nativeCloseDeclared}")
    appendLine("native_diagnostics_declared=${diagnostics.nativeDiagnosticsDeclared}")
    appendLine("native_create_called=${diagnostics.nativeCreateCalled}")
    appendLine("native_run_called=${diagnostics.nativeRunCalled}")
    appendLine("native_close_called=${diagnostics.nativeCloseCalled}")
    appendLine("native_diagnostics_called=${diagnostics.nativeDiagnosticsCalled}")
    appendLine("holder_create_supported=${diagnostics.holderCreateSupported}")
    appendLine("holder_run_supported=${diagnostics.holderRunSupported}")
    appendLine("holder_close_supported=${diagnostics.holderCloseSupported}")
    appendLine("holder_diagnostics_supported=${diagnostics.holderDiagnosticsSupported}")
    appendLine("engine_create_called=${diagnostics.engineCreateCalled}")
    appendLine("model_assets_create_called=${diagnostics.modelAssetsCreateCalled}")
    appendLine("npu_decode_called=${diagnostics.npuDecodeCalled}")
    appendLine("qnn_called=${diagnostics.qnnCalled}")
    appendLine("status=${diagnostics.status}")
    appendLine("reason=${diagnostics.reason}")
    appendLine("persistent_multi_turn_possible=${diagnostics.persistentMultiTurnPossible}")
    appendLine("engine_reuse_observed=${diagnostics.engineReuseObserved}")
    appendLine("recommended_next_step=${diagnostics.recommendedNextStep}")
}.trimEnd()

internal fun npuPersistentHolderNativeStubDiagnostics(
    nativeCreateCalled: Boolean = false,
    nativeRunCalled: Boolean = false,
    nativeCloseCalled: Boolean = false,
    nativeDiagnosticsCalled: Boolean = false,
): NpuPersistentHolderApiDiagnostics = NpuPersistentHolderApiDiagnostics(
    holderApiAvailable = false,
    holderApiReason = NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
    nativeHolderStubAvailable = true,
    nativeHolderStubVersion = NPU_PERSISTENT_HOLDER_NATIVE_STUB_VERSION,
    nativeCreateDeclared = true,
    nativeRunDeclared = true,
    nativeCloseDeclared = true,
    nativeDiagnosticsDeclared = true,
    nativeCreateCalled = nativeCreateCalled,
    nativeRunCalled = nativeRunCalled,
    nativeCloseCalled = nativeCloseCalled,
    nativeDiagnosticsCalled = nativeDiagnosticsCalled,
    engineCreateCalled = false,
    modelAssetsCreateCalled = false,
    npuDecodeCalled = false,
    qnnCalled = false,
    status = NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED,
    reason = NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
    holderCreateSupported = false,
    holderRunSupported = false,
    holderCloseSupported = false,
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
