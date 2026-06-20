package io.github.ninbyo02.lami.ui.screens.home

internal const val NPU_PERSISTENT_HOLDER_API_PROBE_TEST_NAME = "NPU Persistent Holder API Probe"
internal const val NPU_PERSISTENT_HOLDER_API_STATUS_NOT_EXPOSED = "not_exposed"
internal const val NPU_PERSISTENT_HOLDER_API_REASON_NEEDS_NATIVE_JNI_SUPPORT = "needs_native_jni_support"
internal const val NPU_PERSISTENT_HOLDER_API_RECOMMENDED_NEXT_STEP =
    "implement_dev_only_native_holder_api"

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
)

internal data class NpuPersistentHolderApiDiagnostics(
    val holderApiAvailable: Boolean = false,
    val holderApiReason: String = NPU_PERSISTENT_HOLDER_API_REASON_NEEDS_NATIVE_JNI_SUPPORT,
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
