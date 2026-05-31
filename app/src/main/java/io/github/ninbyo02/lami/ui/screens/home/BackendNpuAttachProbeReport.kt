package io.github.ninbyo02.lami.ui.screens.home

internal data class BackendNpuAttachProbeReportRequest(
    val runId: String,
    val phase: String = BackendNpuAttachProbeReportFormatter.PHASE_INVENTORY,
    val engineInitializeOptIn: Boolean = false,
    val processAliveAfterProbe: String = "unknown-script-checks-pidof",
    val nativeCrashSuspected: String = "unknown-script-checks-logcat",
    val signal: String = "-",
    val abortMessage: String = "-",
    val backtraceHead: String = "-",
)

internal object BackendNpuAttachProbeReportFormatter {
    const val PHASE_INVENTORY = "inventory"
    const val PHASE_ENGINE_INITIALIZE = "engine_initialize"
    const val PHASE_CONVERSATION = "conversation"
    const val PHASE_ONE_TOKEN_DECODE = "one_token_decode"

    fun formatText(
        snapshot: AcceleratorProbeSnapshot,
        request: BackendNpuAttachProbeReportRequest,
    ): String {
        val rows = buildRows(snapshot, request)
        return buildString {
            appendLine("backend_npu_attach_probe_v1")
            rows.forEach { (key, value) ->
                append(key).append('=').append(value).appendLine()
            }
        }
    }

    fun formatMarkdown(
        snapshot: AcceleratorProbeSnapshot,
        request: BackendNpuAttachProbeReportRequest,
    ): String {
        val rows = buildRows(snapshot, request)
        return buildString {
            appendLine("# Backend.NPU Attach Probe")
            appendLine()
            appendLine("This is a dev-only diagnostic dry-run report. It does not connect `Backend.NPU` to the production ChatScreen inference path, does not change fallback policy, and does not add any always-on `System.loadLibrary` call.")
            appendLine()
            appendLine("## Summary")
            appendLine()
            appendLine("| key | value |")
            appendLine("| --- | --- |")
            rows.forEach { (key, value) ->
                append("| ")
                    .append(escapeMarkdownCell(key))
                    .append(" | ")
                    .append(escapeMarkdownCell(value))
                    .appendLine(" |")
            }
            appendLine()
            appendLine("## Phase Boundary")
            appendLine()
            appendLine("- Phase 1: `Backend.NPU` object instantiate and API inventory.")
            appendLine("- Phase 2: `EngineConfig` dry-build with `Backend.NPU`.")
            appendLine("- Phase 3: `Engine` initialize dry-run only when explicit opt-in is true.")
            appendLine("- Phase 4: Conversation/create and one-token decode are reported as not attempted by this minimal probe unless a later explicit probe adds them.")
        }
    }

    fun backendNpuAttachStatus(snapshot: AcceleratorProbeSnapshot): String =
        when {
            snapshot.engineInitializeDryRunInitializeResult == "success" -> "engine-initialize-returned"
            snapshot.engineConfigNpuDryBuildResult == "success" -> "engineconfig-holds-backend-npu"
            snapshot.backendNpuInstantiateResult == "success" -> "backend-npu-instantiated-only"
            snapshot.backendNpuInstantiateProbeSkipReason != null -> "skipped-${snapshot.backendNpuInstantiateProbeSkipReason}"
            else -> "unknown"
        }

    fun shouldRunEngineInitializeDryRun(
        phase: String,
        explicitOptIn: Boolean,
    ): Boolean =
        explicitOptIn &&
            (
                phase == PHASE_ENGINE_INITIALIZE ||
                    phase == PHASE_CONVERSATION ||
                    phase == PHASE_ONE_TOKEN_DECODE
                )

    fun shouldRunConversationDryRun(
        phase: String,
        explicitOptIn: Boolean,
        engineInitializeReturned: Boolean,
    ): Boolean =
        explicitOptIn &&
            (
                phase == PHASE_CONVERSATION ||
                    phase == PHASE_ONE_TOKEN_DECODE
                ) &&
            engineInitializeReturned

    private fun buildRows(
        snapshot: AcceleratorProbeSnapshot,
        request: BackendNpuAttachProbeReportRequest,
    ): List<Pair<String, String>> {
        val dispatchApiLoadError = snapshot.engineInitializeDryRunNoUsableDispatchRuntimeDetected == true ||
            snapshot.engineInitializeDryRunFailedToInitializeDispatchApiDetected == true ||
            snapshot.dispatchRuntimePresentInFlavor == false
        val abiOrBuildIdMismatch = snapshot.dispatchRuntimeAbiCompatibility
            ?.contains("mismatch", ignoreCase = true) == true ||
            snapshot.galleryStackExpectedBuildIdMatch == false ||
            snapshot.customStackExpectedBuildIdMatch == false
        val engineInitializeReturned = snapshot.engineInitializeDryRunInitializeReturned == "yes"

        return listOf(
            "run_id" to request.runId,
            "phase_requested" to request.phase,
            "explicit_engine_initialize_opt_in" to request.engineInitializeOptIn.toString(),
            "backend_npu_attach_status" to backendNpuAttachStatus(snapshot),
            "application_id" to snapshot.applicationId.orUnknown(),
            "current_flavor" to snapshot.currentFlavor.orUnknown(),
            "model_path" to snapshot.engineInitializeDryRunModelPath.orDash(),
            "model_exists" to snapshot.engineInitializeDryRunModelFileExists.asUnknown(),
            "model_length" to snapshot.engineInitializeDryRunModelFileLength.asUnknown(),
            "model_can_read" to snapshot.engineInitializeDryRunModelFileCanRead.asUnknown(),
            "selected_model_kind" to snapshot.engineInitializeDryRunModelKind.orUnknown(),
            "native_library_dir" to (snapshot.engineInitializeDryRunNativeLibraryDir ?: snapshot.dispatchNativeLibraryDir).orUnknown(),
            "native_library_dir_exists" to snapshot.dispatchNativeLibraryDirExists.asUnknown(),
            "dispatch_runtime_present" to snapshot.dispatchRuntimePresentInFlavor.asUnknown(),
            "dispatch_runtime_file" to snapshot.dispatchRuntimeFilePath.orDash(),
            "dispatch_runtime_build_id" to snapshot.dispatchRuntimeBuildId.orUnknown(),
            "dispatch_runtime_sha256" to snapshot.dispatchRuntimeSha256.orUnknown(),
            "dispatch_runtime_expected_sha256_match" to snapshot.dispatchRuntimeExpectedSha256Match.asUnknown(),
            "dispatch_runtime_abi_compatibility" to snapshot.dispatchRuntimeAbiCompatibility.orUnknown(),
            "litert_build_id" to snapshot.liteRtBuildId.orUnknown(),
            "litertlm_jni_build_id" to snapshot.liteRtLmJniBuildId.orUnknown(),
            "gallery_stack_expected_build_id_match" to snapshot.galleryStackExpectedBuildIdMatch.asUnknown(),
            "custom_stack_expected_build_id_match" to snapshot.customStackExpectedBuildIdMatch.asUnknown(),
            "backend_npu_constructor_used" to snapshot.backendNpuInstantiateConstructor.orDash(),
            "backend_npu_object_class" to (snapshot.engineInitializeDryRunBackendNpuObjectClass ?: snapshot.backendNpuInstantiateObjectClass).orDash(),
            "backend_npu_instantiate_result" to snapshot.backendNpuInstantiateResult.orUnknown(),
            "engineconfig_constructor_factory_used" to (snapshot.engineConfigNpuDryBuildSelectedConstructor ?: snapshot.galleryStackEngineConfigSelectedConstructor).orDash(),
            "engineconfig_constructor_args_summary" to snapshot.engineConfigNpuDryBuildConstructorArgsSummary.orDash(),
            "engineconfig_object_class" to (snapshot.engineInitializeDryRunEngineConfigObjectClass ?: snapshot.engineConfigNpuDryBuildCreatedObjectClass).orDash(),
            "engineconfig_backend_getter_result" to snapshot.engineConfigNpuDryBuildBackendGetterResultClass.orDash(),
            "engineconfig_dry_build_result" to snapshot.engineConfigNpuDryBuildResult.orUnknown(),
            "engine_constructor_factory_used" to snapshot.engineInitializeDryRunSelectedEngineConstructorOrFactory.orDash(),
            "engine_initialize_method" to snapshot.engineInitializeDryRunSelectedInitializeMethod.orDash(),
            "engine_initialize_invoked" to snapshot.engineInitializeDryRunInitializeInvoked.orNo(),
            "engine_initialize_returned" to snapshot.engineInitializeDryRunInitializeReturned.orNo(),
            "engine_initialize_result" to snapshot.engineInitializeDryRunInitializeResult.orSkipped(),
            "engine_initialize_skip_reason" to snapshot.engineInitializeDryRunSkipReason.orNone(),
            "conversation_create_invoked" to shouldRunConversationDryRun(
                phase = request.phase,
                explicitOptIn = request.engineInitializeOptIn,
                engineInitializeReturned = engineInitializeReturned,
            ).toString(),
            "conversation_create_returned" to "false",
            "conversation_create_result" to "not-attempted-minimal-dry-run",
            "one_token_decode_invoked" to "false",
            "one_token_decode_returned" to "false",
            "one_token_decode_result" to "not-attempted-minimal-dry-run",
            "exception_class" to firstNonBlank(
                snapshot.engineInitializeDryRunExceptionClass,
                snapshot.engineConfigNpuDryBuildExceptionClass,
                snapshot.backendNpuInstantiateExceptionClass,
            ).orDash(),
            "exception_message" to firstNonBlank(
                snapshot.engineInitializeDryRunExceptionMessage,
                snapshot.engineConfigNpuDryBuildExceptionMessage,
                snapshot.backendNpuInstantiateExceptionMessage,
            ).orDash(),
            "root_cause" to firstNonBlank(
                snapshot.engineInitializeDryRunRootCause,
                snapshot.engineConfigNpuDryBuildRootCause,
                snapshot.backendNpuInstantiateRootCause,
            ).orDash(),
            "cause_chain" to firstNonBlank(
                snapshot.engineInitializeDryRunCauseChain,
                snapshot.engineConfigNpuDryBuildCauseChain,
                snapshot.backendNpuInstantiateCauseChain,
            ).orDash(),
            "unsatisfied_link_error_detected" to snapshot.engineInitializeDryRunUnsatisfiedLinkErrorDetected.asFalse(),
            "dispatch_api_load_error_detected" to dispatchApiLoadError.toString(),
            "symbol_mismatch_suspected" to snapshot.engineInitializeDryRunSymbolMismatchDetected.asFalse(),
            "abi_build_id_mismatch_suspected" to abiOrBuildIdMismatch.toString(),
            "elapsed_ms" to snapshot.engineInitializeDryRunElapsedMs.asUnknown(),
            "process_alive_after_probe" to request.processAliveAfterProbe,
            "native_crash_suspected" to request.nativeCrashSuspected,
            "signal" to request.signal,
            "abort_message" to request.abortMessage,
            "backtrace_head" to request.backtraceHead,
            "last_stage" to snapshot.engineInitializeDryRunLastStage.orDash(),
            "diagnostic_file" to snapshot.engineInitializeDryRunDiagnosticFilePath.orDash(),
            "safety_policy" to "dev-only explicit opt-in; no production ChatScreen wiring; no fallback change; no QAIRT/QNN setting change; no always-on System.loadLibrary",
        )
    }

    private fun escapeMarkdownCell(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("\n", "\\n")

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"

    private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "-"

    private fun String?.orNo(): String = this?.takeIf { it.isNotBlank() } ?: "no"

    private fun String?.orSkipped(): String = this?.takeIf { it.isNotBlank() } ?: "skipped"

    private fun String?.orNone(): String = this?.takeIf { it.isNotBlank() } ?: "none"

    private fun Boolean?.asUnknown(): String = this?.toString() ?: "unknown"

    private fun Boolean?.asFalse(): String = (this ?: false).toString()

    private fun Long?.asUnknown(): String = this?.toString() ?: "unknown"
}
