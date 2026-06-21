package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

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
internal const val NPU_PERSISTENT_HOLDER_CREATE_CLOSE_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuPersistentHolderCreateCloseDevProbe"
internal const val NPU_PERSISTENT_HOLDER_CREATE_CLOSE_NO_RESULT =
    "no holder create/close probe result available"
internal const val NPU_PERSISTENT_HOLDER_CREATE_CLOSE_UI_TITLE =
    "NPU Persistent Holder Create/Close Probe"
internal const val NPU_PERSISTENT_HOLDER_CREATE_CLOSE_RUN_LABEL =
    "Run Holder Create/Close Probe"
internal const val NPU_PERSISTENT_HOLDER_CREATE_CLOSE_COPY_SUMMARY_LABEL =
    "Copy Holder Create/Close Summary"
internal const val NPU_PERSISTENT_HOLDER_CREATE_CLOSE_COPY_FULL_DUMP_LABEL =
    "Copy Holder Create/Close Full Dump"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_PROBE_TEST_NAME =
    "NPU Persistent Holder Run Once Probe"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuPersistentHolderRunOnceDevProbe"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_NO_RESULT =
    "no holder run once probe result available"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_UI_TITLE =
    "NPU Persistent Holder Run Once Probe"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_RUN_LABEL =
    "Run Holder Run Once Probe"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_COPY_SUMMARY_LABEL =
    "Copy Holder Run Once Summary"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_COPY_FULL_DUMP_LABEL =
    "Copy Holder Run Once Full Dump"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_PROMPT = "こんにちは"
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS = 32
internal const val NPU_PERSISTENT_HOLDER_RUN_ONCE_RECOMMENDED_NEXT_STEP =
    "review_run_once_device_result_then_implement_two_turn_probe"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_PROBE_TEST_NAME =
    "NPU Persistent Holder Two Turn Probe"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuPersistentHolderTwoTurnDevProbe"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_NO_RESULT =
    "no holder two-turn probe result available"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_UI_TITLE =
    "NPU Persistent Holder Two-Turn Probe"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_RUN_LABEL =
    "Run Holder Two-Turn Probe"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_COPY_SUMMARY_LABEL =
    "Copy Holder Two-Turn Summary"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_COPY_FULL_DUMP_LABEL =
    "Copy Holder Two-Turn Full Dump"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_PROMPT_1 = "こんにちは"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_PROMPT_2 = "あなたは誰ですか"
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_COUNT = 2
internal const val NPU_PERSISTENT_HOLDER_TWO_TURN_RECOMMENDED_NEXT_STEP =
    "review_two_turn_device_result_then_implement_five_turn_probe"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_PROBE_TEST_NAME =
    "NPU Persistent Holder Five Turn Probe"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuPersistentHolderFiveTurnDevProbe"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_NO_RESULT =
    "no holder five-turn probe result available"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_UI_TITLE =
    "NPU Persistent Holder Five-Turn Probe"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_RUN_LABEL =
    "Run Holder Five-Turn Probe"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_COPY_SUMMARY_LABEL =
    "Copy Holder Five-Turn Summary"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_COPY_FULL_DUMP_LABEL =
    "Copy Holder Five-Turn Full Dump"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_1 = "こんにちは"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_2 = "あなたは誰ですか"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_3 = "Pythonとは何ですか"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_4 = "Androidについて一言で説明して"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_5 = "ありがとう"
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_COUNT = 5
internal const val NPU_PERSISTENT_HOLDER_FIVE_TURN_RECOMMENDED_NEXT_STEP =
    "review_five_turn_device_result_then_implement_ten_turn_probe"

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
    val holderOpenBeforeRun: Boolean = false,
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
    val runOnceRequested: Boolean = false,
    val runOnceCalled: Boolean = false,
    val runOnceSupported: Boolean = false,
    val runOnceSucceeded: Boolean = false,
    val runOnceReason: String = "unavailable",
    val runDecodeReached: String = "unavailable",
    val rawOutput: String = "unavailable",
    val sanitizedOutput: String = "unavailable",
    val qualityClassification: String = "unavailable",
    val backendEvidence: String = "unavailable",
    val fallbackUsed: String = "unavailable",
    val timeout: String = "unavailable",
    val freshCrash: String = "unavailable",
    val totalMs: String = "unavailable",
    val decodeMs: String = "unavailable",
    val outputTokens: String = "unavailable",
    val tokensPerSecond: String = "unavailable",
    val finishReason: String = "unavailable",
    val stopReason: String = "unavailable",
    val eosDetected: String = "unavailable",
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

internal interface NpuPersistentHolderCreateCloseProbeRunner {
    suspend fun run(): NpuPersistentHolderCreateCloseProbeState
}

internal interface NpuPersistentHolderRunOnceProbeRunner {
    suspend fun run(): NpuPersistentHolderRunOnceProbeState
}

internal interface NpuPersistentHolderTwoTurnProbeRunner {
    suspend fun run(): NpuPersistentHolderTwoTurnProbeState
}

internal interface NpuPersistentHolderFiveTurnProbeRunner {
    suspend fun run(): NpuPersistentHolderFiveTurnProbeState
}

internal fun createNpuPersistentHolderCreateCloseProbeRunner(
    context: Context,
): NpuPersistentHolderCreateCloseProbeRunner? =
    runCatching {
        Class.forName(NPU_PERSISTENT_HOLDER_CREATE_CLOSE_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuPersistentHolderCreateCloseProbeRunner
    }.getOrNull()

internal fun createNpuPersistentHolderRunOnceProbeRunner(
    context: Context,
): NpuPersistentHolderRunOnceProbeRunner? =
    runCatching {
        Class.forName(NPU_PERSISTENT_HOLDER_RUN_ONCE_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuPersistentHolderRunOnceProbeRunner
    }.getOrNull()

internal fun createNpuPersistentHolderTwoTurnProbeRunner(
    context: Context,
): NpuPersistentHolderTwoTurnProbeRunner? =
    runCatching {
        Class.forName(NPU_PERSISTENT_HOLDER_TWO_TURN_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuPersistentHolderTwoTurnProbeRunner
    }.getOrNull()

internal fun createNpuPersistentHolderFiveTurnProbeRunner(
    context: Context,
): NpuPersistentHolderFiveTurnProbeRunner? =
    runCatching {
        Class.forName(NPU_PERSISTENT_HOLDER_FIVE_TURN_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuPersistentHolderFiveTurnProbeRunner
    }.getOrNull()

internal data class NpuPersistentHolderCreateCloseProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val createResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterCreate: NpuPersistentHolderApiDiagnostics? = null,
    val closeResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterClose: NpuPersistentHolderApiDiagnostics? = null,
    val secondCloseResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterSecondClose: NpuPersistentHolderApiDiagnostics? = null,
    val throwableClass: String = "unavailable",
    val throwableMessage: String = "unavailable",
) {
    val hasResult: Boolean
        get() = createResult != null ||
            diagnosticsAfterCreate != null ||
            closeResult != null ||
            diagnosticsAfterClose != null ||
            secondCloseResult != null ||
            diagnosticsAfterSecondClose != null

    val latestDiagnostics: NpuPersistentHolderApiDiagnostics?
        get() = diagnosticsAfterSecondClose
            ?: secondCloseResult?.diagnostics
            ?: diagnosticsAfterClose
            ?: closeResult?.diagnostics
            ?: diagnosticsAfterCreate
            ?: createResult?.diagnostics
}

internal data class NpuPersistentHolderRunOnceDecodeResult(
    val status: String = "unavailable",
    val reason: String = "unavailable",
    val runDecodeReached: String = "unavailable",
    val rawOutput: String = "unavailable",
    val sanitizedOutput: String = "unavailable",
    val qualityClassification: String = "unavailable",
    val backendEvidence: String = "unavailable",
    val fallbackUsed: String = "unavailable",
    val timeout: String = "unavailable",
    val freshCrash: String = "unavailable",
    val totalMs: String = "unavailable",
    val decodeMs: String = "unavailable",
    val outputTokens: String = "unavailable",
    val tokensPerSecond: String = "unavailable",
    val finishReason: String = "unavailable",
    val stopReason: String = "unavailable",
    val eosDetected: String = "unavailable",
    val fullText: String = "",
)

internal data class NpuPersistentHolderRunOnceProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val prompt: String = NPU_PERSISTENT_HOLDER_RUN_ONCE_PROMPT,
    val maxOutputTokens: Int = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
    val createResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterCreate: NpuPersistentHolderApiDiagnostics? = null,
    val runResult: NpuPersistentHolderApiResult? = null,
    val decodeResult: NpuPersistentHolderRunOnceDecodeResult? = null,
    val closeResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterClose: NpuPersistentHolderApiDiagnostics? = null,
    val throwableClass: String = "unavailable",
    val throwableMessage: String = "unavailable",
) {
    val hasResult: Boolean
        get() = createResult != null ||
            diagnosticsAfterCreate != null ||
            runResult != null ||
            decodeResult != null ||
            closeResult != null ||
            diagnosticsAfterClose != null

    val latestDiagnostics: NpuPersistentHolderApiDiagnostics?
        get() = diagnosticsAfterClose
            ?: closeResult?.diagnostics
            ?: runResult?.diagnostics
            ?: diagnosticsAfterCreate
            ?: createResult?.diagnostics
}

internal data class NpuPersistentHolderTwoTurnRecord(
    val turnIndex: Int,
    val prompt: String,
    val runResult: NpuPersistentHolderApiResult? = null,
    val decodeResult: NpuPersistentHolderRunOnceDecodeResult? = null,
)

internal data class NpuPersistentHolderTwoTurnProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val maxOutputTokens: Int = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
    val createResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterCreate: NpuPersistentHolderApiDiagnostics? = null,
    val turns: List<NpuPersistentHolderTwoTurnRecord> = emptyList(),
    val closeResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterClose: NpuPersistentHolderApiDiagnostics? = null,
    val throwableClass: String = "unavailable",
    val throwableMessage: String = "unavailable",
) {
    val hasResult: Boolean
        get() = createResult != null ||
            diagnosticsAfterCreate != null ||
            turns.isNotEmpty() ||
            closeResult != null ||
            diagnosticsAfterClose != null

    val latestDiagnostics: NpuPersistentHolderApiDiagnostics?
        get() = diagnosticsAfterClose
            ?: closeResult?.diagnostics
            ?: turns.lastOrNull()?.runResult?.diagnostics
            ?: diagnosticsAfterCreate
            ?: createResult?.diagnostics
}

internal data class NpuPersistentHolderFiveTurnProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val maxOutputTokens: Int = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
    val createResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterCreate: NpuPersistentHolderApiDiagnostics? = null,
    val turns: List<NpuPersistentHolderTwoTurnRecord> = emptyList(),
    val closeResult: NpuPersistentHolderApiResult? = null,
    val diagnosticsAfterClose: NpuPersistentHolderApiDiagnostics? = null,
    val throwableClass: String = "unavailable",
    val throwableMessage: String = "unavailable",
) {
    val hasResult: Boolean
        get() = createResult != null ||
            diagnosticsAfterCreate != null ||
            turns.isNotEmpty() ||
            closeResult != null ||
            diagnosticsAfterClose != null

    val latestDiagnostics: NpuPersistentHolderApiDiagnostics?
        get() = diagnosticsAfterClose
            ?: closeResult?.diagnostics
            ?: turns.lastOrNull()?.runResult?.diagnostics
            ?: diagnosticsAfterCreate
            ?: createResult?.diagnostics
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
    appendLine("holder_open_before_run=${diagnostics.holderOpenBeforeRun}")
    appendLine("run_once_requested=${diagnostics.runOnceRequested}")
    appendLine("run_once_called=${diagnostics.runOnceCalled}")
    appendLine("run_once_supported=${diagnostics.runOnceSupported}")
    appendLine("run_once_succeeded=${diagnostics.runOnceSucceeded}")
    appendLine("run_once_reason=${diagnostics.runOnceReason}")
    appendLine("run_decode_reached=${diagnostics.runDecodeReached}")
    appendLine("status=${diagnostics.status}")
    appendLine("reason=${diagnostics.reason}")
    appendLine("persistent_multi_turn_possible=${diagnostics.persistentMultiTurnPossible}")
    appendLine("engine_reuse_observed=${diagnostics.engineReuseObserved}")
    appendLine("restart_app_recommended=${diagnostics.restartAppRecommended}")
    appendLine("recommended_next_step=${diagnostics.recommendedNextStep}")
}.trimEnd()

internal fun formatNpuPersistentHolderCreateCloseSummaryForCopy(
    state: NpuPersistentHolderCreateCloseProbeState,
): String {
    val diagnostics = state.latestDiagnostics
        ?: return "$NPU_PERSISTENT_HOLDER_CREATE_CLOSE_NO_RESULT\n" +
            "test_name=$NPU_PERSISTENT_HOLDER_CREATE_CLOSE_PROBE_TEST_NAME"
    return formatNpuPersistentHolderNativeStubProbeSummary(diagnostics)
}

internal fun formatNpuPersistentHolderCreateCloseFullDumpForCopy(
    state: NpuPersistentHolderCreateCloseProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU persistent holder create close full dump]")
    appendLine("test_name=$NPU_PERSISTENT_HOLDER_CREATE_CLOSE_PROBE_TEST_NAME")
    appendLine("probe_status=${state.status}")
    appendLine("probe_reason=${state.reason}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("throwable_class=${state.throwableClass}")
    appendLine("throwable_message=${state.throwableMessage}")
    if (!state.hasResult) {
        appendLine(NPU_PERSISTENT_HOLDER_CREATE_CLOSE_NO_RESULT)
        return@buildString
    }
    appendHolderResultBlock("create_result", state.createResult)
    appendHolderDiagnosticsBlock("diagnostics_after_create", state.diagnosticsAfterCreate)
    appendHolderResultBlock("close_result", state.closeResult)
    appendHolderDiagnosticsBlock("diagnostics_after_close", state.diagnosticsAfterClose)
    appendHolderResultBlock("second_close_result", state.secondCloseResult)
    appendHolderDiagnosticsBlock("diagnostics_after_second_close", state.diagnosticsAfterSecondClose)
    appendLine()
    appendLine(formatNpuPersistentHolderCreateCloseSummaryForCopy(state))
}.trimEnd()

internal fun formatNpuPersistentHolderRunOnceSummaryForCopy(
    state: NpuPersistentHolderRunOnceProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_PERSISTENT_HOLDER_RUN_ONCE_NO_RESULT\n" +
            "test_name=$NPU_PERSISTENT_HOLDER_RUN_ONCE_PROBE_TEST_NAME"
    }
    val createDiagnostics = state.diagnosticsAfterCreate ?: state.createResult?.diagnostics
    val runDiagnostics = state.runResult?.diagnostics
    val closeDiagnostics = state.diagnosticsAfterClose ?: state.closeResult?.diagnostics
    val latestDiagnostics = state.latestDiagnostics
    val decode = state.decodeResult
    val createRequested = state.createResult != null || createDiagnostics?.holderCreateRequested == true
    val closeRequested = state.closeResult != null || closeDiagnostics?.holderCloseRequested == true
    val runRequested = state.runResult != null || runDiagnostics?.runOnceRequested == true
    val runCalled = runDiagnostics?.runOnceCalled == true || runDiagnostics?.nativeRunCalled == true
    val runSupported = runDiagnostics?.runOnceSupported == true
    val runSucceeded = decode?.status == "success"
    val runReason = decode?.reason
        ?: runDiagnostics?.runOnceReason
        ?: state.runResult?.reason
        ?: "unavailable"
    return buildString {
        appendLine("[DEV診断: NPU persistent holder run once summary]")
        appendLine("test_name=$NPU_PERSISTENT_HOLDER_RUN_ONCE_PROBE_TEST_NAME")
        appendLine("probe_status=${state.status}")
        appendLine("holder_create_requested=$createRequested")
        appendLine("holder_create_called=${createDiagnostics?.holderCreateCalled ?: state.createResult?.diagnostics?.nativeCreateCalled ?: false}")
        appendLine("holder_create_succeeded=${createDiagnostics?.holderCreateSucceeded ?: false}")
        appendLine("holder_id=${state.runResult?.holderId ?: state.createResult?.holderId ?: "unavailable"}")
        appendLine("holder_open_before_run=${createDiagnostics?.holderOpen ?: runDiagnostics?.holderOpenBeforeRun ?: false}")
        appendLine("run_once_requested=$runRequested")
        appendLine("run_once_called=$runCalled")
        appendLine("run_once_supported=$runSupported")
        appendLine("run_once_succeeded=$runSucceeded")
        appendLine("run_once_reason=$runReason")
        appendLine("run_decode_reached=${decode?.runDecodeReached ?: "unavailable"}")
        appendLine("raw_output=${decode?.rawOutput ?: "unavailable"}")
        appendLine("sanitized_output=${decode?.sanitizedOutput ?: "unavailable"}")
        appendLine("quality_classification=${decode?.qualityClassification ?: "unavailable"}")
        appendLine("backend_evidence=${decode?.backendEvidence ?: "unavailable"}")
        appendLine("fallback_used=${decode?.fallbackUsed ?: "unavailable"}")
        appendLine("timeout=${decode?.timeout ?: "unavailable"}")
        appendLine("fresh_crash=${decode?.freshCrash ?: "unavailable"}")
        appendLine("total_ms=${decode?.totalMs ?: "unavailable"}")
        appendLine("decode_ms=${decode?.decodeMs ?: "unavailable"}")
        appendLine("output_tokens=${decode?.outputTokens ?: "unavailable"}")
        appendLine("tokens_per_second=${decode?.tokensPerSecond ?: "unavailable"}")
        appendLine("finish_reason=${decode?.finishReason ?: "unavailable"}")
        appendLine("stop_reason=${decode?.stopReason ?: "unavailable"}")
        appendLine("eos_detected=${decode?.eosDetected ?: "unavailable"}")
        appendLine("holder_close_requested=$closeRequested")
        appendLine("holder_close_called=${closeDiagnostics?.holderCloseCalled ?: state.closeResult?.diagnostics?.nativeCloseCalled ?: false}")
        appendLine("holder_close_succeeded=${closeDiagnostics?.holderCloseSucceeded ?: false}")
        appendLine("holder_fatal_latch=${latestDiagnostics?.holderFatalLatch ?: false}")
        appendLine("holder_fatal_reason=${latestDiagnostics?.holderFatalReason ?: "unavailable"}")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("persistent_multi_turn_possible=false")
        appendLine("restart_app_recommended=${latestDiagnostics?.restartAppRecommended ?: false}")
        appendLine("recommended_next_step=$NPU_PERSISTENT_HOLDER_RUN_ONCE_RECOMMENDED_NEXT_STEP")
    }.trimEnd()
}

internal fun formatNpuPersistentHolderRunOnceFullDumpForCopy(
    state: NpuPersistentHolderRunOnceProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU persistent holder run once full dump]")
    appendLine("test_name=$NPU_PERSISTENT_HOLDER_RUN_ONCE_PROBE_TEST_NAME")
    appendLine("probe_status=${state.status}")
    appendLine("probe_reason=${state.reason}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("prompt=${state.prompt}")
    appendLine("max_output_tokens=${state.maxOutputTokens}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("throwable_class=${state.throwableClass}")
    appendLine("throwable_message=${state.throwableMessage}")
    if (!state.hasResult) {
        appendLine(NPU_PERSISTENT_HOLDER_RUN_ONCE_NO_RESULT)
        return@buildString
    }
    appendHolderResultBlock("create_result", state.createResult)
    appendHolderDiagnosticsBlock("diagnostics_after_create", state.diagnosticsAfterCreate)
    appendHolderResultBlock("run_once_native_gate_result", state.runResult)
    appendHolderRunOnceDecodeBlock(state.decodeResult)
    appendHolderResultBlock("close_result", state.closeResult)
    appendHolderDiagnosticsBlock("diagnostics_after_close", state.diagnosticsAfterClose)
    appendLine()
    appendLine(formatNpuPersistentHolderRunOnceSummaryForCopy(state))
}.trimEnd()

internal fun formatNpuPersistentHolderTwoTurnSummaryForCopy(
    state: NpuPersistentHolderTwoTurnProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_PERSISTENT_HOLDER_TWO_TURN_NO_RESULT\n" +
            "test_name=$NPU_PERSISTENT_HOLDER_TWO_TURN_PROBE_TEST_NAME"
    }
    val createDiagnostics = state.diagnosticsAfterCreate ?: state.createResult?.diagnostics
    val closeDiagnostics = state.diagnosticsAfterClose ?: state.closeResult?.diagnostics
    val latestDiagnostics = state.latestDiagnostics
    val turn1 = state.turns.firstOrNull { it.turnIndex == 1 }
    val turn2 = state.turns.firstOrNull { it.turnIndex == 2 }
    val completed = state.turns.count { it.decodeResult?.status == "success" }
    val decodeReachedCount = state.turns.count { it.decodeResult?.runDecodeReached == "true" }
    val fallbackCount = state.turns.count { it.decodeResult?.fallbackUsed == "true" }
    val timeoutCount = state.turns.count { it.decodeResult?.timeout == "true" }
    val freshCrashCount = state.turns.count { it.decodeResult?.freshCrash == "true" }
    return buildString {
        appendLine("[DEV診断: NPU persistent holder two turn summary]")
        appendLine("test_name=$NPU_PERSISTENT_HOLDER_TWO_TURN_PROBE_TEST_NAME")
        appendLine("probe_status=${state.status}")
        appendLine("holder_create_requested=${state.createResult != null || createDiagnostics?.holderCreateRequested == true}")
        appendLine("holder_create_called=${createDiagnostics?.holderCreateCalled ?: state.createResult?.diagnostics?.nativeCreateCalled ?: false}")
        appendLine("holder_create_succeeded=${createDiagnostics?.holderCreateSucceeded ?: false}")
        appendLine("holder_id=${state.createResult?.holderId ?: "unavailable"}")
        appendLine("run_count_requested=$NPU_PERSISTENT_HOLDER_TWO_TURN_COUNT")
        appendLine("run_count_completed=$completed")
        appendLine("turn1_run_called=${turn1?.runResult?.diagnostics?.runOnceCalled ?: turn1?.runResult?.diagnostics?.nativeRunCalled ?: false}")
        appendLine("turn1_run_succeeded=${turn1?.decodeResult?.status == "success"}")
        appendLine("turn1_run_decode_reached=${turn1?.decodeResult?.runDecodeReached ?: "unavailable"}")
        appendLine("turn2_run_called=${turn2?.runResult?.diagnostics?.runOnceCalled ?: turn2?.runResult?.diagnostics?.nativeRunCalled ?: false}")
        appendLine("turn2_run_succeeded=${turn2?.decodeResult?.status == "success"}")
        appendLine("turn2_run_decode_reached=${turn2?.decodeResult?.runDecodeReached ?: "unavailable"}")
        appendLine("run_decode_reached_count=$decodeReachedCount")
        appendLine("backend_evidence_summary=${summarizeHolderTwoTurnValues(state.turns.map { it.decodeResult?.backendEvidence })}")
        appendLine("quality_classification_summary=${summarizeHolderTwoTurnValues(state.turns.map { it.decodeResult?.qualityClassification })}")
        appendLine("fallback_used_count=$fallbackCount")
        appendLine("timeout_count=$timeoutCount")
        appendLine("fresh_crash_count=$freshCrashCount")
        appendLine("holder_close_requested=${state.closeResult != null || closeDiagnostics?.holderCloseRequested == true}")
        appendLine("holder_close_called=${closeDiagnostics?.holderCloseCalled ?: state.closeResult?.diagnostics?.nativeCloseCalled ?: false}")
        appendLine("holder_close_succeeded=${closeDiagnostics?.holderCloseSucceeded ?: false}")
        appendLine("holder_fatal_latch=${latestDiagnostics?.holderFatalLatch ?: false}")
        appendLine("holder_fatal_reason=${latestDiagnostics?.holderFatalReason ?: "unavailable"}")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("persistent_multi_turn_possible=false")
        appendLine("restart_app_recommended=${latestDiagnostics?.restartAppRecommended ?: false}")
        appendLine("recommended_next_step=$NPU_PERSISTENT_HOLDER_TWO_TURN_RECOMMENDED_NEXT_STEP")
    }.trimEnd()
}

internal fun formatNpuPersistentHolderTwoTurnFullDumpForCopy(
    state: NpuPersistentHolderTwoTurnProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU persistent holder two turn full dump]")
    appendLine("test_name=$NPU_PERSISTENT_HOLDER_TWO_TURN_PROBE_TEST_NAME")
    appendLine("probe_status=${state.status}")
    appendLine("probe_reason=${state.reason}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("max_output_tokens=${state.maxOutputTokens}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("throwable_class=${state.throwableClass}")
    appendLine("throwable_message=${state.throwableMessage}")
    if (!state.hasResult) {
        appendLine(NPU_PERSISTENT_HOLDER_TWO_TURN_NO_RESULT)
        return@buildString
    }
    appendHolderResultBlock("create_result", state.createResult)
    appendHolderDiagnosticsBlock("diagnostics_after_create", state.diagnosticsAfterCreate)
    state.turns.forEach { turn ->
        appendHolderTwoTurnRecordBlock(turn)
    }
    appendHolderResultBlock("close_result", state.closeResult)
    appendHolderDiagnosticsBlock("diagnostics_after_close", state.diagnosticsAfterClose)
    appendLine()
    appendLine(formatNpuPersistentHolderTwoTurnSummaryForCopy(state))
}.trimEnd()

internal fun formatNpuPersistentHolderFiveTurnSummaryForCopy(
    state: NpuPersistentHolderFiveTurnProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_PERSISTENT_HOLDER_FIVE_TURN_NO_RESULT\n" +
            "test_name=$NPU_PERSISTENT_HOLDER_FIVE_TURN_PROBE_TEST_NAME"
    }
    val createDiagnostics = state.diagnosticsAfterCreate ?: state.createResult?.diagnostics
    val closeDiagnostics = state.diagnosticsAfterClose ?: state.closeResult?.diagnostics
    val latestDiagnostics = state.latestDiagnostics
    val completed = state.turns.count { it.decodeResult?.status == "success" }
    val decodeReachedCount = state.turns.count { it.decodeResult?.runDecodeReached == "true" }
    val fallbackCount = state.turns.count { it.decodeResult?.fallbackUsed == "true" }
    val timeoutCount = state.turns.count { it.decodeResult?.timeout == "true" }
    val freshCrashCount = state.turns.count { it.decodeResult?.freshCrash == "true" }
    return buildString {
        appendLine("[DEV診断: NPU persistent holder five turn summary]")
        appendLine("test_name=$NPU_PERSISTENT_HOLDER_FIVE_TURN_PROBE_TEST_NAME")
        appendLine("probe_status=${state.status}")
        appendLine("holder_create_requested=${state.createResult != null || createDiagnostics?.holderCreateRequested == true}")
        appendLine("holder_create_called=${createDiagnostics?.holderCreateCalled ?: state.createResult?.diagnostics?.nativeCreateCalled ?: false}")
        appendLine("holder_create_succeeded=${createDiagnostics?.holderCreateSucceeded ?: false}")
        appendLine("holder_id=${state.createResult?.holderId ?: "unavailable"}")
        appendLine("run_count_requested=$NPU_PERSISTENT_HOLDER_FIVE_TURN_COUNT")
        appendLine("run_count_completed=$completed")
        (1..NPU_PERSISTENT_HOLDER_FIVE_TURN_COUNT).forEach { index ->
            val turn = state.turns.firstOrNull { it.turnIndex == index }
            appendLine("turn${index}_run_decode_reached=${turn?.decodeResult?.runDecodeReached ?: "unavailable"}")
        }
        appendLine("run_decode_reached_count=$decodeReachedCount")
        appendLine("backend_evidence_summary=${summarizeHolderTwoTurnValues(state.turns.map { it.decodeResult?.backendEvidence })}")
        appendLine("quality_classification_summary=${summarizeHolderTwoTurnValues(state.turns.map { it.decodeResult?.qualityClassification })}")
        appendLine("fallback_used_count=$fallbackCount")
        appendLine("timeout_count=$timeoutCount")
        appendLine("fresh_crash_count=$freshCrashCount")
        appendLine("holder_close_requested=${state.closeResult != null || closeDiagnostics?.holderCloseRequested == true}")
        appendLine("holder_close_called=${closeDiagnostics?.holderCloseCalled ?: state.closeResult?.diagnostics?.nativeCloseCalled ?: false}")
        appendLine("holder_close_succeeded=${closeDiagnostics?.holderCloseSucceeded ?: false}")
        appendLine("holder_fatal_latch=${latestDiagnostics?.holderFatalLatch ?: false}")
        appendLine("holder_fatal_reason=${latestDiagnostics?.holderFatalReason ?: "unavailable"}")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("persistent_multi_turn_possible=false")
        appendLine("restart_app_recommended=${latestDiagnostics?.restartAppRecommended ?: false}")
        appendLine("recommended_next_step=$NPU_PERSISTENT_HOLDER_FIVE_TURN_RECOMMENDED_NEXT_STEP")
    }.trimEnd()
}

internal fun formatNpuPersistentHolderFiveTurnFullDumpForCopy(
    state: NpuPersistentHolderFiveTurnProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU persistent holder five turn full dump]")
    appendLine("test_name=$NPU_PERSISTENT_HOLDER_FIVE_TURN_PROBE_TEST_NAME")
    appendLine("probe_status=${state.status}")
    appendLine("probe_reason=${state.reason}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("max_output_tokens=${state.maxOutputTokens}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("throwable_class=${state.throwableClass}")
    appendLine("throwable_message=${state.throwableMessage}")
    if (!state.hasResult) {
        appendLine(NPU_PERSISTENT_HOLDER_FIVE_TURN_NO_RESULT)
        return@buildString
    }
    appendHolderResultBlock("create_result", state.createResult)
    appendHolderDiagnosticsBlock("diagnostics_after_create", state.diagnosticsAfterCreate)
    state.turns.forEach { turn ->
        appendHolderTwoTurnRecordBlock(turn)
    }
    appendHolderResultBlock("close_result", state.closeResult)
    appendHolderDiagnosticsBlock("diagnostics_after_close", state.diagnosticsAfterClose)
    appendLine()
    appendLine(formatNpuPersistentHolderFiveTurnSummaryForCopy(state))
}.trimEnd()

private fun StringBuilder.appendHolderResultBlock(
    label: String,
    result: NpuPersistentHolderApiResult?,
) {
    appendLine()
    appendLine("[$label]")
    if (result == null) {
        appendLine("result=unavailable")
        return
    }
    appendLine("status=${result.status}")
    appendLine("reason=${result.reason}")
    appendLine("holder_id=${result.holderId}")
    appendLine("native_summary_begin")
    appendLine(result.nativeSummary.ifBlank { "unavailable" })
    appendLine("native_summary_end")
}

private fun StringBuilder.appendHolderTwoTurnRecordBlock(
    turn: NpuPersistentHolderTwoTurnRecord,
) {
    val decode = turn.decodeResult
    appendLine()
    appendLine("[turn_${turn.turnIndex}]")
    appendLine("turn_index=${turn.turnIndex}")
    appendLine("prompt=${turn.prompt}")
    appendLine("status=${decode?.status ?: turn.runResult?.status ?: "unavailable"}")
    appendLine("reason=${decode?.reason ?: turn.runResult?.reason ?: "unavailable"}")
    appendLine("run_decode_reached=${decode?.runDecodeReached ?: "unavailable"}")
    appendLine("raw_output=${decode?.rawOutput ?: "unavailable"}")
    appendLine("sanitized_output=${decode?.sanitizedOutput ?: "unavailable"}")
    appendLine("quality_classification=${decode?.qualityClassification ?: "unavailable"}")
    appendLine("backend_evidence=${decode?.backendEvidence ?: "unavailable"}")
    appendLine("fallback_used=${decode?.fallbackUsed ?: "unavailable"}")
    appendLine("timeout=${decode?.timeout ?: "unavailable"}")
    appendLine("fresh_crash=${decode?.freshCrash ?: "unavailable"}")
    appendLine("total_ms=${decode?.totalMs ?: "unavailable"}")
    appendLine("decode_ms=${decode?.decodeMs ?: "unavailable"}")
    appendLine("finish_reason=${decode?.finishReason ?: "unavailable"}")
    appendLine("stop_reason=${decode?.stopReason ?: "unavailable"}")
    appendLine("eos_detected=${decode?.eosDetected ?: "unavailable"}")
    appendHolderResultBlock("turn_${turn.turnIndex}_native_gate_result", turn.runResult)
    appendHolderRunOnceDecodeBlock(decode)
}

private fun StringBuilder.appendHolderRunOnceDecodeBlock(
    decode: NpuPersistentHolderRunOnceDecodeResult?,
) {
    appendLine()
    appendLine("[run_once_decode_result]")
    if (decode == null) {
        appendLine("decode_result=unavailable")
        return
    }
    appendLine("status=${decode.status}")
    appendLine("reason=${decode.reason}")
    appendLine("run_decode_reached=${decode.runDecodeReached}")
    appendLine("raw_output=${decode.rawOutput}")
    appendLine("sanitized_output=${decode.sanitizedOutput}")
    appendLine("quality_classification=${decode.qualityClassification}")
    appendLine("backend_evidence=${decode.backendEvidence}")
    appendLine("fallback_used=${decode.fallbackUsed}")
    appendLine("timeout=${decode.timeout}")
    appendLine("fresh_crash=${decode.freshCrash}")
    appendLine("total_ms=${decode.totalMs}")
    appendLine("decode_ms=${decode.decodeMs}")
    appendLine("output_tokens=${decode.outputTokens}")
    appendLine("tokens_per_second=${decode.tokensPerSecond}")
    appendLine("finish_reason=${decode.finishReason}")
    appendLine("stop_reason=${decode.stopReason}")
    appendLine("eos_detected=${decode.eosDetected}")
    appendLine("display_text_begin")
    appendLine(decode.fullText.ifBlank { "unavailable" })
    appendLine("display_text_end")
}

private fun summarizeHolderTwoTurnValues(values: Iterable<String?>): String {
    val counts = values
        .map { it.orEmpty().ifBlank { "unavailable" } }
        .groupingBy { it }
        .eachCount()
    if (counts.isEmpty()) return "unavailable"
    return counts.entries
        .sortedBy { it.key }
        .joinToString(",") { "${it.key}:${it.value}" }
}

private fun StringBuilder.appendHolderDiagnosticsBlock(
    label: String,
    diagnostics: NpuPersistentHolderApiDiagnostics?,
) {
    appendLine()
    appendLine("[$label]")
    if (diagnostics == null) {
        appendLine("diagnostics=unavailable")
        return
    }
    appendLine(formatNpuPersistentHolderNativeStubProbeSummary(diagnostics))
}

internal fun npuPersistentHolderNativeStubDiagnostics(
    nativeCreateCalled: Boolean = false,
    nativeRunCalled: Boolean = false,
    nativeCloseCalled: Boolean = false,
    nativeDiagnosticsCalled: Boolean = false,
    holderCreateRequested: Boolean = nativeCreateCalled,
    holderCreateSucceeded: Boolean = false,
    holderId: String = "unavailable",
    holderOpen: Boolean = false,
    holderOpenBeforeRun: Boolean = holderOpen,
    holderCloseRequested: Boolean = nativeCloseCalled,
    holderCloseSucceeded: Boolean = false,
    holderDoubleCloseSafe: Boolean = true,
    holderFatalLatch: Boolean = false,
    holderFatalReason: String = "none",
    restartAppRecommended: Boolean = holderFatalLatch,
    status: String = NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED,
    reason: String = NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
    runOnceRequested: Boolean = nativeRunCalled,
    runOnceSupported: Boolean = false,
    runOnceSucceeded: Boolean = false,
    runOnceReason: String = reason,
    runDecodeReached: String = "unavailable",
    rawOutput: String = "unavailable",
    sanitizedOutput: String = "unavailable",
    qualityClassification: String = "unavailable",
    backendEvidence: String = "unavailable",
    fallbackUsed: String = "unavailable",
    timeout: String = "unavailable",
    freshCrash: String = "unavailable",
    totalMs: String = "unavailable",
    decodeMs: String = "unavailable",
    outputTokens: String = "unavailable",
    tokensPerSecond: String = "unavailable",
    finishReason: String = "unavailable",
    stopReason: String = "unavailable",
    eosDetected: String = "unavailable",
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
    holderOpenBeforeRun = holderOpenBeforeRun,
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
    runOnceRequested = runOnceRequested,
    runOnceCalled = nativeRunCalled,
    runOnceSupported = runOnceSupported,
    runOnceSucceeded = runOnceSucceeded,
    runOnceReason = runOnceReason,
    runDecodeReached = runDecodeReached,
    rawOutput = rawOutput,
    sanitizedOutput = sanitizedOutput,
    qualityClassification = qualityClassification,
    backendEvidence = backendEvidence,
    fallbackUsed = fallbackUsed,
    timeout = timeout,
    freshCrash = freshCrash,
    totalMs = totalMs,
    decodeMs = decodeMs,
    outputTokens = outputTokens,
    tokensPerSecond = tokensPerSecond,
    finishReason = finishReason,
    stopReason = stopReason,
    eosDetected = eosDetected,
    restartAppRecommended = restartAppRecommended,
    status = status,
    reason = reason,
    holderCreateSupported = true,
    holderRunSupported = runOnceSupported,
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
        holderOpenBeforeRun = values.any { it.holderOpenBeforeRun },
        holderCloseRequested = values.any { it.holderCloseRequested },
        holderCloseSucceeded = values.any { it.holderCloseSucceeded },
        holderDoubleCloseSafe = values.all { it.holderDoubleCloseSafe },
        holderFatalLatch = values.any { it.holderFatalLatch },
        holderFatalReason = values.lastOrNull { it.holderFatalLatch }?.holderFatalReason ?: "none",
        restartAppRecommended = values.any { it.restartAppRecommended },
        runOnceRequested = values.any { it.runOnceRequested },
        runOnceSupported = values.any { it.runOnceSupported },
        runOnceSucceeded = values.any { it.runOnceSucceeded },
        runOnceReason = values.lastOrNull { it.runOnceReason != "unavailable" }?.runOnceReason
            ?: NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
        runDecodeReached = values.lastOrNull { it.runDecodeReached != "unavailable" }?.runDecodeReached
            ?: "unavailable",
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
        holderOpenBeforeRun = values.booleanValue("holder_open_before_run"),
        holderCloseRequested = values.booleanValue("holder_close_requested"),
        holderCloseSucceeded = values.booleanValue("holder_close_succeeded"),
        holderDoubleCloseSafe = values.booleanValue("holder_double_close_safe"),
        holderFatalLatch = values.booleanValue("holder_fatal_latch"),
        holderFatalReason = values["holder_fatal_reason"] ?: "unavailable",
        restartAppRecommended = values.booleanValue("restart_app_recommended"),
        status = values["status"] ?: NPU_PERSISTENT_HOLDER_API_STATUS_NOT_IMPLEMENTED,
        reason = values["reason"] ?: NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
        runOnceRequested = values.booleanValue("run_once_requested"),
        runOnceSupported = values.booleanValue("run_once_supported"),
        runOnceSucceeded = values.booleanValue("run_once_succeeded"),
        runOnceReason = values["run_once_reason"]
            ?: values["reason"]
            ?: NPU_PERSISTENT_HOLDER_NATIVE_STUB_REASON,
        runDecodeReached = values["run_decode_reached"] ?: "unavailable",
        rawOutput = values["raw_output"] ?: "unavailable",
        sanitizedOutput = values["sanitized_output"] ?: "unavailable",
        qualityClassification = values["quality_classification"] ?: "unavailable",
        backendEvidence = values["backend_evidence"] ?: values["npu_backend_evidence"] ?: "unavailable",
        fallbackUsed = values["fallback_used"] ?: "unavailable",
        timeout = values["timeout"] ?: "unavailable",
        freshCrash = values["fresh_crash"] ?: "unavailable",
        totalMs = values["total_ms"] ?: values["elapsed_ms"] ?: "unavailable",
        decodeMs = values["decode_ms"] ?: values["decode_elapsed_ms"] ?: "unavailable",
        outputTokens = values["output_tokens"] ?: values["output_token_count"] ?: "unavailable",
        tokensPerSecond = values["tokens_per_second"] ?: values["npu_s1_tokens_per_second"] ?: "unavailable",
        finishReason = values["finish_reason"] ?: "unavailable",
        stopReason = values["stop_reason"] ?: "unavailable",
        eosDetected = values["eos_detected"] ?: "unavailable",
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
