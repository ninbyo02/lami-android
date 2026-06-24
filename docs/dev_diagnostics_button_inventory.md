# DEV Diagnostics Button Inventory

## Scope

This inventory covers visible DEV diagnostics controls related to local
inference, NPU standard route, legacy QAIRT244 investigation paths, and
diagnostic copy actions. It intentionally does not remove buttons or change
runtime behavior.

Current NPU status:

- User-facing backend label is `NPU Beta`.
- Completed route is Phase 8 / Phase 7B pseudo streaming.
- NPU Beta is explicit user selection, not `Automatic`.
- Legacy `NPU_S1` to `NPU_S5` values remain for developer override and artifact
  compatibility.
- R6 native token streaming remains future work; current Phase 7B streaming is
  pseudo streaming over safe finalized text.

## Button Inventory

| UI label / Display name | Implementation file | Call target / runner | Purpose | Current need | Reason | Related existing tests | Future alternative |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `推論統計をコピー` | `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt` | `buildInferenceStatsFullCopyText(...)` | Copy full inference stats, including DEV sections when developer display is active. | KEEP_PRIMARY | Primary evidence capture for CPU/GPU/NPU runs; low risk and backend-neutral. | `InferenceStatsSheetContentTest`, `NpuS1RepeatedRunDiagnosticsTest`, `NpuStandardRouteS1ProviderTest` | Keep; add a route-specific export bundle only if artifacts become too large. |
| `GPU診断キーをコピー` | `ChatScreen.kt`, `GpuDiagnosticCopyText.kt` | `buildGpuDiagnosticKeysCopyText(...)` | Copy GPU classification keys. | KEEP_ADVANCED | GPU promotion is blocked, but retaining the copy path helps compare future upstream/runtime changes. | `InferenceStatsSheetContentTest` | Move under Advanced diagnostics when DEV screen is folded into Primary/Advanced groups. |
| `GPU内部surfaceキーをコピー` | `ChatScreen.kt`, `GpuInternalSurfaceProbeDiagnostics.kt`, `GpuDiagnosticCopyText.kt` | `buildGpuInternalSurfaceKeysCopyText(...)` | Copy GPU internal executor/surface evidence. | KEEP_ADVANCED | Useful only for GPU root-cause follow-up; not primary NPU health evidence. | `GpuInternalSurfaceProbeDiagnosticsTest`, `InferenceStatsSheetContentTest` | Keep advanced; hide from primary NPU diagnostics. |
| `NPU診断キーをコピー` | `ChatScreen.kt`, `NpuDiagnosticCopyText.kt` | `buildNpuDiagnosticKeysCopyText(...)` | Copy NPU standard-route gate, backend, quality, delivery, rollout, and kill-switch keys. | KEEP_PRIMARY | Main one-shot NPU evidence path. Captures `status`, `fallback`, `fresh_crash`, `timeout`, backend evidence, quality, delivery, and Phase 8 diagnostics. | `InferenceStatsSheetContentTest`, `NpuStandardRouteS1ProviderTest` | Keep as the one-shot diagnostic copy action; document expected artifact naming. |
| `▶ DEV診断を表示` / `▼ DEV診断を隠す` | `ChatScreen.kt` | `NpuStandardRouteDevDiagnosticsBlock(...)` | Toggle expanded DEV diagnostics under an NPU standard-route result. | KEEP_PRIMARY | Necessary to access compact/full dump and repeated-run controls without always crowding normal chat. | `NpuStandardRouteS1ProviderTest` indirectly covers compact/full dump copy content. | Keep; Step 4 should split content into Primary and Advanced subsections. |
| `入力コピー` | `ChatScreen.kt` | `NpuStandardRouteS1DevTraceBlock.onCopyInput` | Copy input prompt for an NPU S1/standard-route diagnostic run. | KEEP_ADVANCED | Useful when reproducing a bad output or quality mismatch. Less important than NPU key copy. | `NpuStandardRouteS1ProviderTest` covers full/compact trace content. | Keep advanced; primary output should prefer diagnostic key copy. |
| `出力コピー` | `ChatScreen.kt` | `NpuStandardRouteS1DevTraceBlock.onCopyOutput` | Copy raw/sanitized output from the DEV trace. | KEEP_ADVANCED | Important for quality investigations and R6 streaming prep, but not a primary health gate. | `NpuStandardRouteS1ProviderTest`, `NpuS1PersistentCustomJniDiagnosticsTest` quality cases. | Keep advanced; consider a single "copy output bundle" later. |
| `Copy Compact` | `ChatScreen.kt` | `npuStandardRouteS1DevCompactCopyText` / compact formatter | Copy compact NPU S1/standard-route diagnostic dump. | KEEP_PRIMARY | Best artifact for one-shot NPU health and rollout sample collection. | `NpuStandardRouteS1ProviderTest` (`Copy Compact uses compact formatter explicitly`) | Keep as primary beside `NPU診断キーをコピー`; align naming to Japanese later. |
| `Copy Stability Summary` | `ChatScreen.kt`, `NpuS1RepeatedRunDiagnostics.kt` | `buildNpuBetaStabilitySummaryCopyText(...)` | Copy the NPU Beta Stability Test aggregate summary only. | KEEP_PRIMARY | Repeated-run evidence can be long; summary copy captures pass/fail/fallback/timeout/crash/decode/timing/backend/quality keys without the detail body. | `NpuS1RepeatedRunDiagnosticsTest` (`Copy Stability Summary...`) | Keep beside the Stability Test runner. |
| `Copy Stability Full Dump` | `ChatScreen.kt`, `NpuS1RepeatedRunDiagnostics.kt` | `buildNpuBetaStabilityFullDumpCopyText(...)` | Copy the current Stability Test diagnostics text, including failure detail blocks when present. | KEEP_PRIMARY | Needed when 10-run stability output needs exact failure context. It does not change the repeated-run runner. | `NpuS1RepeatedRunDiagnosticsTest` (`Copy Stability Full Dump...`) | Keep; future report scripts can consume this artifact. |
| `Copy Non-Streaming Repeat Summary` | `ChatScreen.kt`, `NpuNonStreamingRepeatedStabilityDiagnostics.kt` | `buildNpuNonStreamingRepeatedStabilitySummaryCopyText(...)` | Copy the NPU Non-Streaming Repeated Stability Test aggregate summary only. | KEEP_PRIMARY | Captures 10 fixed one-shot NPU decode attempts without pseudo streaming, TTS, DB, markdown, fallback, holder/session, or true Engine reuse. The first device run completed 6/7 and failed on run 7 near `EngineFactory::CreateDefault` with fallback/timeout/fresh-crash all zero. | `NpuNonStreamingRepeatedStabilityDiagnosticsTest` | Keep beside Stability Test for one-shot native cleanup/repeatability evidence; new summary keys include `engine_create_failure_detected`, `suspected_failure_area`, `repeated_recreate_suspected`, and `true_engine_reuse_investigation_recommended`. |
| `Copy Non-Streaming Repeat Full Dump` | `ChatScreen.kt`, `NpuNonStreamingRepeatedStabilityDiagnostics.kt` | `buildNpuNonStreamingRepeatedStabilityFullDumpCopyText(...)` | Copy the summary plus per-run prompt/status/backend/quality/timing/native-stage details. | KEEP_PRIMARY | Required when a repeat run fails and the first native stage/error tail must be compared with recreate stability, persistent holder, and true Engine blocked artifacts. The current artifact rules out streaming/UI/TTS/DB/markdown as primary causes and points at repeated one-shot recreate pressure. | `NpuNonStreamingRepeatedStabilityDiagnosticsTest` | Keep; report scripts can consume this as the one-shot non-streaming repeat artifact before staged true Engine probes resume. |
| `Copy Long Summary` | `ChatScreen.kt`, `NpuLongGenerationDiagnostics.kt` | `buildNpuLongGenerationSummaryCopyText(...)` | Copy the NPU Beta Long Generation summary only. | KEEP_PRIMARY | Long-generation case output can be large, especially 512+ tokens; summary copy keeps reviewable aggregate keys. | `NpuLongGenerationDiagnosticsTest` (`Copy Long Summary...`) | Keep beside the Long Generation runner. |
| `Copy Long Full Dump` | `ChatScreen.kt`, `NpuLongGenerationDiagnostics.kt` | `buildNpuLongGenerationFullDumpCopyText(...)` | Copy the Long Generation summary plus all per-token-plan case blocks. | KEEP_PRIMARY | Required for detailed 32/128/512 comparison and future 1024-token investigation. | `NpuLongGenerationDiagnosticsTest` (`Copy Long Full Dump...`) | Keep; use for physical-device artifacts when output quality or performance differs by token limit. |
| `Copy Persistent Summary` | `ChatScreen.kt`, `NpuS1PersistentEngineDiagnostics.kt` | `buildNpuPersistentEngineSummaryCopyText(...)` | Copy Persistent Engine Multi-turn summary only. | KEEP_PRIMARY | Persistent diagnostics can now block before generation when the NPU session API would require unsupported logits output; summary copy captures the API mode and block reason in one tap. | `NpuS1PersistentEngineDiagnosticsTest` | Keep beside the Persistent Engine Multi-turn runner. |
| `Copy Persistent Full Dump` | `ChatScreen.kt`, `NpuS1PersistentEngineDiagnostics.kt` | `buildNpuPersistentEngineFullDumpCopyText(...)` | Copy Persistent Engine Multi-turn summary plus detail/native/API diagnostics. | KEEP_PRIMARY | Needed to share the full blocked/session/API evidence or future per-run adapter evidence. | `NpuS1PersistentEngineDiagnosticsTest` | Keep; future persistent adapter artifacts should use this path. |
| `Copy Repeated Summary` | `ChatScreen.kt`, `NpuS1RepeatedRunDiagnostics.kt` | `buildNpuS1RepeatedRunSummaryCopyText(...)` | Legacy/compatibility repeated-run summary copy path. | KEEP_ADVANCED | Retained for existing DEV trace affordances and tests while the Primary label moves to `Copy Stability Summary`. | `NpuS1RepeatedRunDiagnosticsTest` | Keep as compatibility; prefer `Copy Stability Summary` in Primary. |
| `Copy Full Dump` | `ChatScreen.kt` | `npuStandardRouteS1DevFullDumpCopyText` / full dump formatter | Copy full NPU diagnostic dump. | KEEP_ADVANCED | Required for root-cause detail, but too verbose for routine one-shot checks. | `NpuStandardRouteS1ProviderTest` (`Copy Full Dump...`) | Keep advanced; report generator can consume full dumps when needed. |
| `メモリ回復確認` | `ChatScreen.kt`, `LocalMemoryDiagnostics.kt` | `startMemoryRecoveryCheck()` / `captureLocalMemorySnapshot(...)` | Capture current and delayed memory snapshots after local/NPU activity. | KEEP_ADVANCED | Useful for lifecycle/memory regression and repeated-run stabilization; not a primary NPU route gate. | `LocalMemoryDiagnosticsTest` | Keep advanced; include only aggregate memory deltas in stability report. |
| `NPU Beta安定性テスト開始` | `ChatScreen.kt`, `NpuS1RepeatedRunDiagnostics.kt` | `startNpuS1RepeatedRun()` -> `NpuStandardRouteS1Bridge(...).run(...)` | Serial NPU runs with prompt/count/wait/mode controls; records success/fallback/timeout/crash/decode/quality/timing/memory. | KEEP_PRIMARY | Step 2 adds the `NPU Beta Stability Test` label and summary keys while reusing the existing S1 repeated-run runner. The initial primary entry is safe recreate mode, 10 runs, wait 500ms. Internal S1 naming remains for compatibility. | `NpuS1RepeatedRunDiagnosticsTest`, `NpuStandardRouteS1BridgeTest`, `NpuStandardRouteS1ProviderTest` | Step 3 should add a Long Generation Test. Later stability work can enable 50/100 only after explicit safety review. |
| `Run Non-Streaming Repeat Test` | `ChatScreen.kt`, `NpuNonStreamingRepeatedStabilityDevProbe.kt`, `NpuNonStreamingRepeatedStabilityDiagnostics.kt` | `startNpuNonStreamingRepeatedStabilityTest()` -> `DevOnlyNpuOneTurnConversationEntry.run(...)` for 10 fixed prompts | Serial one-shot NPU decode repeat test with no pseudo streaming, no TTS, no DB, no markdown, and fallback disallowed in diagnostics. | KEEP_PRIMARY | Measures existing successful one-shot NPU/QNN/FastRPC/native cleanup repeatability before any true Engine reuse work. It does not create held Engines, sessions, conversations, or normal chat-route messages. Device result: 6 successes, run-7 `EngineFactory::CreateDefault`/`engine-create-failed: INTERNAL`. | `NpuNonStreamingRepeatedStabilityDiagnosticsTest` | Use after a one-shot NPU pass and before re-enabling true Engine create/close. Do not expand to 30/100 until staged true Engine Phase A/B decisions are complete. |
| `NPU Beta長文生成テスト開始` | `ChatScreen.kt`, `NpuLongGenerationDiagnostics.kt` | `startNpuLongGenerationTest()` -> `NpuStandardRouteS1Bridge(...).run(maxOutputTokens=32/128/512)` | Compare NPU Beta behavior at larger output-token limits. Records status, fallback, timeout, fresh crash, decode reach, timing, tokens/sec, quality, backend evidence, raw/sanitized output, and non-exposed stop/EOS fields as `unavailable`. | KEEP_PRIMARY | This is the minimal Long Generation Test entry. It uses the existing S1 bridge and does not change NPU route behavior, fallback policy, persistent engine behavior, custom JNI, UI/TTS/DB delivery, or native streaming. | `NpuLongGenerationDiagnosticsTest`, `NpuS1RepeatedRunDiagnosticsTest` | Later add 1024 as Advanced after 32/128/512 physical-device evidence is stable. |
| `キャンセル` under repeated run | `ChatScreen.kt` | `cancelNpuS1RepeatedRun()` | Cancel active repeated-run job. | KEEP_PRIMARY | Required safety control for long-running stability tests. | Cancellation status covered in `NpuS1RepeatedRunDiagnosticsTest`. | Keep with Stability Test. |
| `キャンセル` under Non-Streaming Repeat Test | `ChatScreen.kt` | `cancelNpuNonStreamingRepeatedStabilityTest()` | Cancel active non-streaming repeat job. | KEEP_PRIMARY | Required safety control because the 10 one-shot native decodes run serially on a physical NPU device. | Cancellation status represented in `NpuNonStreamingRepeatedStabilityDiagnostics.kt`; UI execution requires physical NPU. | Keep with Non-Streaming Repeat Test. |
| `キャンセル` under Long Generation Test | `ChatScreen.kt` | `cancelNpuLongGenerationTest()` | Cancel active long generation comparison. | KEEP_PRIMARY | Required safety control because 512-token runs can take materially longer than one-shot diagnostics. | Formatter/state cancellation is represented in `NpuLongGenerationDiagnostics.kt`; UI execution requires physical NPU. | Keep with Long Generation Test. |
| `NPU永続Engine状態確認` / `NPU Persistent Engine Multi-turn Probe (blocked)` | `ChatScreen.kt`, `NpuS1PersistentEngineDiagnostics.kt`, `app/src/debug/.../NpuS1PersistentEngineDevProbe.kt` | `startNpuS1PersistentEngineProbe()` -> `NpuS1PersistentEngineProbeRunner.run(...)` | Confirm current persistent-NPU API exposure state and copy the blocked reason. | KEEP_PRIMARY | Current NPU state is expected to block before generation: session API requires unsupported logits output, and the standard-route adapter/native decode path is not yet exposed for persistent multi-turn. `run_count_completed=0` is expected, not a UI failure. | `NpuS1PersistentEngineDiagnosticsTest`, `NpuPersistentHolderApiTest` | Next work is the standard-route adapter exposure review and DEV-only holder contract in `docs/npu_standard_route_adapter_persistent_exposure_review.md` and `docs/npu_dev_only_persistent_holder_api_design.md`. |
| `Run Holder Two-Turn Probe` | `ChatScreen.kt`, `NpuPersistentHolderTwoTurnDevProbe.kt` | `startNpuPersistentHolderTwoTurnProbe()` -> `NativeStubNpuPersistentHolderApi` + existing one-shot NPU adapter decode | Create one DEV holder record, run exactly two holder-gated one-shot decodes, then close once. | KEEP_PRIMARY | This is the next smallest persistent-holder exposure step after Run Once passed. It is not 10-turn, not normal chat routing, and not proof of Engine reuse. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | If device evidence is clean, add a fixed Five-Turn Probe before 10-turn persistent testing. |
| `Copy Holder Two-Turn Summary` | `ChatScreen.kt`, `NpuPersistentHolderApi.kt` | `formatNpuPersistentHolderTwoTurnSummaryForCopy(...)` | Copy Two-Turn aggregate keys. | KEEP_PRIMARY | Captures create/run1/run2/close status, backend evidence summary, fallback/timeout/fresh-crash counts, fatal latch, and `engine_reuse_observed=unavailable`. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | Keep as the compact artifact for physical-device pass/hold review. |
| `Copy Holder Two-Turn Full Dump` | `ChatScreen.kt`, `NpuPersistentHolderApi.kt` | `formatNpuPersistentHolderTwoTurnFullDumpForCopy(...)` | Copy create, per-turn, close, diagnostics, and summary blocks for the Two-Turn probe. | KEEP_PRIMARY | Required for device artifact review when a turn fails or backend evidence changes. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | Keep until report scripts consume the Two-Turn artifact directly. |
| `Run Holder Five-Turn Probe` | `ChatScreen.kt`, `NpuPersistentHolderFiveTurnDevProbe.kt` | `startNpuPersistentHolderFiveTurnProbe()` -> `NativeStubNpuPersistentHolderApi` + existing one-shot NPU adapter decode | Create one DEV holder record, run exactly five holder-gated one-shot decodes, then close once. | KEEP_PRIMARY | This is the next smallest persistent-holder exposure step after Two-Turn passed. It is not 10-turn, not normal chat routing, and not proof of Engine reuse. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | If device evidence is clean, add a fixed Ten-Turn Probe. |
| `Copy Holder Five-Turn Summary` | `ChatScreen.kt`, `NpuPersistentHolderApi.kt` | `formatNpuPersistentHolderFiveTurnSummaryForCopy(...)` | Copy Five-Turn aggregate keys. | KEEP_PRIMARY | Captures create/five-run/close status, backend evidence summary, quality summary, fallback/timeout/fresh-crash counts, fatal latch, and `engine_reuse_observed=unavailable`. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | Keep as the compact artifact for Ten-Turn readiness review. |
| `Copy Holder Five-Turn Full Dump` | `ChatScreen.kt`, `NpuPersistentHolderApi.kt` | `formatNpuPersistentHolderFiveTurnFullDumpForCopy(...)` | Copy create, per-turn, close, diagnostics, and summary blocks for the Five-Turn probe. | KEEP_PRIMARY | Required for device artifact review when any turn fails or backend evidence changes across the five turns. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | Keep until report scripts consume the Five-Turn artifact directly. |
| `Run Holder Ten-Turn Probe` | `ChatScreen.kt`, `NpuPersistentHolderTenTurnDevProbe.kt` | `startNpuPersistentHolderTenTurnProbe()` -> `NativeStubNpuPersistentHolderApi` + existing one-shot NPU adapter decode | Create one DEV holder record, run exactly ten holder-gated one-shot decodes, then close once. | KEEP_PRIMARY | This validates ten holder-managed decode calls after Five-Turn passed. It is not normal chat routing and not true Engine persistent reuse. The first device result matched the run-7 failure shape. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | Next work is `docs/npu_true_engine_persistent_reuse_design.md`, then true Engine holder create/close only. |
| `Copy Holder Ten-Turn Summary` | `ChatScreen.kt`, `NpuPersistentHolderApi.kt` | `formatNpuPersistentHolderTenTurnSummaryForCopy(...)` | Copy Ten-Turn aggregate keys. | KEEP_PRIMARY | Captures create/ten-run/close status, success/failure/rate fields, backend evidence summary, quality summary, fallback/timeout/fresh-crash counts/rates, fatal latch, `engine_reuse_observed=unavailable`, and `true_engine_persistent_reuse=false`. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | Keep as the compact artifact for Ten-Turn pass/hold review. |
| `Copy Holder Ten-Turn Full Dump` | `ChatScreen.kt`, `NpuPersistentHolderApi.kt` | `formatNpuPersistentHolderTenTurnFullDumpForCopy(...)` | Copy create, per-turn, close, diagnostics, and summary blocks for the Ten-Turn probe. | KEEP_PRIMARY | Required for device artifact review when any turn fails, any rate is nonzero, or backend evidence changes across the ten turns. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest` | Keep until report scripts consume the Ten-Turn artifact directly. |
| `Run True Engine Holder Create/Close Probe` | `ChatScreen.kt`, `NpuTrueEngineHolderApi.kt`, `NpuTrueEngineHolderCreateCloseDevProbe.kt` | `startNpuTrueEngineHolderCreateCloseProbe()` -> startup-safe blocked result in `standardDebug` and `trueEngineNpuProbeDebug` while startup-crash recovery is active | Temporarily block the native true Engine create/close probe in `standardDebug`, and also block `trueEngineNpuProbeDebug` after the isolated patched stack caused a cold-start crash. | KEEP_PRIMARY | The standard button path does not resolve model paths, load `Qairt244ShortMultitokenSmoke`, or call `litertlm_jni`. It reports `probe_execution_available=false`, `startup_native_call_blocked=true`, zero Session/decode/generate counts, and keeps `true_engine_persistent_reuse=false`. The isolated APK currently reports `probe_execution_available=false` and `probe_execution_block_reason=temporarily_disabled_after_startup_crash`; UI display alone must not class-load `Qairt244ShortMultitokenSmoke`. Non-Streaming Repeat strengthens the reuse hypothesis, but this button remains blocked until Phase A/B staged probes are designed. | `NpuPersistentHolderNativeStubTest`, `NpuPersistentHolderApiTest`, `InferenceStatsSheetContentTest`, `NpuTrueEngineHolderChatScreenStandardDebugBlockedTest` | Keep disabled. Next minimum step is button-only `entrypoint_only` / `model_assets_only` staged probing in `trueEngineNpuProbeDebug`, not direct `true_engine_create_close_only` revival. |
| `Copy True Engine Holder Summary` | `ChatScreen.kt`, `NpuTrueEngineHolderApi.kt` | `formatNpuTrueEngineHolderCreateCloseSummaryForCopy(...)` | Copy startup-safe blocked or create/close-only Engine safety keys. | KEEP_PRIMARY | Initial and blocked summaries are pure Kotlin and include `true_engine_create_close_probe_startup_safe=true`, `native_call_deferred_until_button_click=true`, and `startup_native_call_blocked=true`. The copy button is shown with the standalone `trueEngineNpuProbeDebug` probe entry. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest`, `NpuTrueEngineHolderChatScreenStandardDebugBlockedTest` | Keep as the compact artifact for startup recovery and future held-Engine run-once readiness. |
| `Copy True Engine Holder Full Dump` | `ChatScreen.kt`, `NpuTrueEngineHolderApi.kt` | `formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(...)` | Copy blocked state or future native create/close details plus summary. | KEEP_PRIMARY | In `standardDebug` and the startup-recovery `trueEngineNpuProbeDebug`, this does not include native result files because native execution is blocked. Future re-enabling must still confirm Session/decode/generate stayed at zero. The copy button is shown with the standalone `trueEngineNpuProbeDebug` probe entry. | `NpuPersistentHolderNativeStubTest`, `InferenceStatsSheetContentTest`, `NpuTrueEngineHolderChatScreenStandardDebugBlockedTest` | Keep until a split true Engine holder native diagnostics API replaces the runCount=0 custom JNI wrapper. |
| `キャンセル` under persistent Engine | `ChatScreen.kt` | `cancelNpuS1PersistentEngineProbe()` | Cancel persistent Engine probe. | KEEP_ADVANCED | Safety control while the advanced probe remains visible. | Status/cancel formatting in `NpuS1PersistentEngineDiagnosticsTest`. | Keep with advanced probe until hidden/removed. |
| `NPU S1 persistent custom JNI <mode>` / `Gemma recommended x20` | `ChatScreen.kt`, `NpuS1PersistentCustomJniDiagnostics.kt`, `app/src/debug/.../NpuS1PersistentCustomJniDevProbe.kt` | `startNpuS1PersistentCustomJniProbe()` -> `NpuS1PersistentCustomJniProbeRunner.run(...)` | Custom JNI holder / prompt wrapper / quality profile investigation. | KEEP_ADVANCED | Historically critical for quality and engine-create root cause work. Today it is too specialized for primary health checks. | `NpuS1PersistentCustomJniDiagnosticsTest`, `Qairt244NpuDiagnosticPromptValidatorTest` | Move to Advanced "legacy quality investigation"; retire after NPU Beta quality/stability/long-generation runners cover the same gates. |
| Filter chips under `NPU S1 persistent custom JNI` (`entrypoint_only`, `before_engine_create`, `engine_create_only`, `full_20`, etc.) | `ChatScreen.kt`, `NpuS1PersistentCustomJniDiagnostics.kt` | `NpuS1PersistentCustomJniProbeMode` | Select custom JNI crash/lifecycle probe depth. | KEEP_ADVANCED | Useful only for native/JNI fault isolation. | `NpuS1PersistentCustomJniDiagnosticsTest` | Hide inside Advanced by default; keep until R6/native cleanup decision. |
| Quality prompt chips under `NPU S1 persistent custom JNI` (`Current legacy/failing`, `Gemma recommended x20`, etc.) | `ChatScreen.kt`, `NpuS1PersistentCustomJniDiagnostics.kt` | `NpuS1PersistentCustomJniQualityPromptProfile` | Compare prompt wrappers and quality-classification behavior. | KEEP_ADVANCED | Past quality-alignment work still needs compatibility, but primary NPU Beta acceptance uses standard route artifacts now. | `NpuS1PersistentCustomJniDiagnosticsTest` | Keep advanced; future replacement is a standard-route validation prompt suite. |
| `ローカルエンジンを再作成` | `ChatScreen.kt` | `localInferenceEngineHolder.requestRecreateForDev(...)` | Force local engine holder recreation after preferred backend changes. | KEEP_ADVANCED | Useful when switching backend/phase during DEV work; not a direct NPU health test. | Covered indirectly by local holder diagnostics tests where present. | Keep advanced; gate behind developer mode as now. |
| `Legacy QAIRT244 ChatScreen route` toggle | `Settings.kt`, `SettingsPreferences.kt`, `app/src/debug/.../DevOnlyNpuChatScreenBlockedBranch.kt` | `settingsPreferences.saveDevEnableQairt244Sm8750NpuRoute(...)`; reflected `runForChatScreen(...)` path | Enable old hidden QAIRT244 ChatScreen route. | CANDIDATE_HIDE | Legacy route is explicitly separate from NPU standard route and can confuse NPU Beta users. Keep for artifact/parser compatibility until confirmed obsolete. | `Qairt244DevNpuInferenceStatsSectionTest`, `DevOnlyNpuChatScreenBlockedBranchTest` in custom debug source set. | Hide under Advanced legacy diagnostics; remove only after artifacts/scripts no longer rely on it. |
| `Legacy QAIRT244 prompt template` radio options (`raw`, `simple_ja_chat`, `gemma_it_like`) | `Settings.kt`, `SettingsData.kt`, `SettingsPreferences.kt` | `settingsPreferences.saveHiddenQairt244PromptTemplateMode(...)` | Prompt template selection for legacy hidden QAIRT244 route. | CANDIDATE_HIDE | Applies to legacy route only and not to NPU standard route prompt shaping. | Hidden route/custom debug tests. | Hide with legacy QAIRT244 route; keep parser compatibility. |
| `NPU standard route phase（developer）` options (`DEV: NPU S1` to `DEV: NPU S5`) | `Settings.kt`, `InferenceBackendSelection.kt`, `NpuStandardRoutePreferences.kt` | `settingsPreferences.saveInferenceBackendSelection(selection)` | Developer override for legacy standard-route phases. | KEEP_ADVANCED | Needed for compatibility and targeted phase regression. Not a user-facing backend. | `SettingsNpuRouteUiTest`, `NpuStandardRouteRolloutSelectionTest` | Keep in developer section; do not expose as normal backend. |
| `NPU max output tokens（開発用）` radio options (`32` to `4096`) | `Settings.kt`, `NpuStandardRoutePreferences.kt` | `settingsPreferences.saveNpuStandardRouteMaxOutputTokens(...)` | Configure standard route output-token cap for manual long-output comparison. | KEEP_ADVANCED | Useful foundation for Long Generation Test, but currently not a matrix runner button. | `SettingsNpuRouteUiTest`, `NpuStandardRouteS1ProviderTest` | Step 3: add Long Generation runner/UI that automatically compares 32/128/512/1024 and records stop/EOS fields as available. |
| `DEV Markdown` display section | `ChatScreen.kt` | `markdownStreamingMode.displayLabel` | Shows current markdown streaming mode in Developer stats. | KEEP_ADVANCED | Observability only; useful when comparing markdown/pseudo streaming behavior. | `MarkdownStreamingModeTest`, `ChatBubbleStreamingTest` | Keep as read-only advanced info. |
| DEV whitespace trace copy blocks | `ChatScreen.kt` | `CopyableDebugBlock(...)` for `devWhitespaceTraceText` / `devRunnerWhitespaceTraceText` | Debug whitespace normalization/rendering traces. | CANDIDATE_HIDE | Useful for UI/rendering bugs, not current NPU health gates. | Whitespace behavior is indirectly covered by chat/markdown tests. | Hide unless `DEV_UI_DEBUG_MODE`; already gated. |

## Current Gaps

## Primary / Advanced Layout

Step 4 organizes DEV diagnostics into two groups.

Primary is visible by default and contains only:

- `NPU診断キーをコピー`
- `Copy Compact`
- `Copy Stability Summary`
- `Copy Stability Full Dump`
- `NPU Beta安定性テスト開始`
- `Copy Persistent Summary`
- `Copy Persistent Full Dump`
- `NPU永続Engine状態確認`
- `Copy Long Summary`
- `Copy Long Full Dump`
- `NPU Beta長文生成テスト開始`

Primary starts with a short safety note:

- Safe entry points for NPU Beta validation.
- Recommended order: Stability Test, Persistent Probe state check, then
  Long Generation Test.
- Diagnostics stop on timeout, fallback, crash suspicion, or decode failure.

Advanced is collapsed by default and contains low-level diagnostics:

- GPU diagnostic copy buttons
- memory recovery checks
- route/debug text
- input/output copy
- `Copy Full Dump`
- persistent Engine probes
- custom JNI probes
- legacy route/debug output
- DEV Markdown and manual engine recreate controls

No execution logic changes are part of this layout step.

### One-shot NPU diagnostic

There is no separate "One-shot NPU診断" button. The current one-shot flow is:

1. Select `NPU Beta`.
2. Send prompt `こんにちは`.
3. Open inference stats.
4. Copy `NPU診断キーをコピー`, `Copy Compact`, or `Copy Full Dump`.

This is acceptable for now, but a primary one-shot button could reduce manual
steps if it uses the same Phase 8 completed route and does not bypass quality
suppression.

Required one-shot fields:

- `status`
- `fallback` / `fallback_used`
- `fresh_crash`
- `timeout`
- `run_decode_reached`
- backend evidence such as `QNN_HTP_V79_FastRPC_native_diag`
- `raw_output`, `sanitized_output`, `quality_classification`
- `decode_ms`, `total_ms`, `tokens_per_second`

### Stability Test

`NPU S1 repeated run` now provides the initial `NPU Beta Stability Test` entry.
The implementation intentionally reuses the existing S1 repeated-run runner and
adds Beta Stability summary keys instead of changing the NPU route.

Current Step 2 behavior:

- present as `NPU Beta Stability Test`
- default to 10 runs
- keep guarded Recreate/Reuse modes, 10 runs, and 500ms or 2000ms wait
- aggregate success rate, fallback rate, timeout rate, fresh crash rate,
  average decode time, average total time, and average tokens/sec
- expose Reuse diagnostics (`reuse_gate_allowed`, `engine_reuse_requested`,
  `engine_reused=unavailable`) for EngineFactory::CreateDefault failure
  investigation
- preserve cancel and safety stop behavior
- leave 50/100 disabled by safety policy until a later review

### Persistent Engine Multi-turn Probe

`NPU Persistent Engine Multi-turn Probe` is now a Primary diagnostic. It reuses
the existing persistent Engine DEV probe but presents the current NPU state as a
blocked API exposure check rather than a button that should generate ten turns.

Current behavior:

- prefer the standard-route adapter path if a persistent entrypoint is exposed
- block the official session API on NPU because real-device evidence shows
  `logits_output_not_supported_on_npu_backend`
- report `persistent_standard_route_available=false` and
  `persistent_standard_route_reason=needs_native_adapter_work` when the
  successful native adapter decode path cannot yet be reused persistently
- refer to `docs/npu_standard_route_adapter_persistent_exposure_review.md` for
  the current conclusion: Kotlin can call the successful one-shot native
  adapter, but true Engine reuse needs a DEV-only native/JNI holder API
- use `NpuPersistentHolderApi` / `NotExposedNpuPersistentHolderApi` as the
  default contract stub; it reports `holder_api_available=false`
- a separate debug-only `NativeStubNpuPersistentHolderApi` can call
  `NPU Persistent Holder Create Close Probe`, which reports
  `native_holder_create_close_available=true`
- the native create/close probe only manages one app JNI holder lifecycle
  record; `runOnce` is now a DEV-only open-holder gate for Run Once and
  Two-Turn probes
- the native probe does not create Engine/ModelAssets/EngineSettings, does not
  call QNN or NPU decode/generate inside the holder stub, and is not connected
  to normal chat routing
- DEV diagnostics now exposes `NPU Persistent Holder Create/Close Probe` with
  `Run Holder Create/Close Probe`, `Copy Holder Create/Close Summary`, and
  `Copy Holder Create/Close Full Dump`
- that UI entry runs create, diagnostics, close, diagnostics, and a second close
  safety check only; it does not call `runHolderOnce`
- pass on device requires `holder_create_called=true`,
  `holder_close_called=true`, `npu_decode_called=false`,
  `generate_called=false`, `qnn_decode_called=false`, and
  `holder_fatal_latch=false`
- hold device promotion when `holder_fatal_latch=true`,
  `holder_create_succeeded=false`, `holder_close_succeeded=false`,
  `npu_decode_called=true`, or `generate_called=true`
- Create/Close has passed on device, so DEV diagnostics also exposes
  `NPU Persistent Holder Run Once Probe` with `Run Holder Run Once Probe`,
  `Copy Holder Run Once Summary`, and `Copy Holder Run Once Full Dump`
- Run Once executes only one create -> run once -> close flow with prompt
  `こんにちは`; it is not multi-turn, does not run 10 turns, does not connect
  normal chat routing, and keeps `engine_reuse_observed=unavailable`
- Run Once pass requires `holder_create_succeeded=true`,
  `run_once_called=true`, `run_once_succeeded=true`,
  `run_decode_reached=true`, `fallback_used=false`, `timeout=false`,
  `fresh_crash=false`, `holder_close_succeeded=true`,
  `holder_fatal_latch=false`, and QNN HTP / FastRPC backend evidence
- Run Once hold conditions are create failure, unsupported/failed run once,
  fallback, timeout, fresh crash, close failure, or fatal latch
- DEV diagnostics now exposes `NPU Persistent Holder Two-Turn Probe` with
  `Run Holder Two-Turn Probe`, `Copy Holder Two-Turn Summary`, and
  `Copy Holder Two-Turn Full Dump`
- Two-Turn performs one create, two holder-gated one-shot decodes using
  `こんにちは` and `あなたは誰ですか`, and one close; it is not a 10-turn probe,
  does not connect normal chat routing, and keeps
  `engine_reuse_observed=unavailable`
- Two-Turn pass requires `holder_create_succeeded=true`,
  `turn1_run_decode_reached=true`, `turn2_run_decode_reached=true`, QNN HTP /
  FastRPC backend evidence, `fallback_used_count=0`, `timeout_count=0`,
  `fresh_crash_count=0`, `holder_close_succeeded=true`, and
  `holder_fatal_latch=false`
- Two-Turn hold conditions are turn 1 failure, turn 2 failure, fallback,
  timeout, fresh crash, close failure, fatal latch, or missing backend evidence
- DEV diagnostics now exposes `NPU Persistent Holder Five-Turn Probe` with
  `Run Holder Five-Turn Probe`, `Copy Holder Five-Turn Summary`, and
  `Copy Holder Five-Turn Full Dump`
- Five-Turn performs one create, five holder-gated one-shot decodes, and one
  close; it is not a 10-turn probe, does not connect normal chat routing, and
  keeps `engine_reuse_observed=unavailable`
- Five-Turn pass requires `holder_create_succeeded=true`,
  `run_decode_reached_count=5`, QNN HTP / FastRPC backend evidence, generally
  natural quality summary, `fallback_used_count=0`, `timeout_count=0`,
  `fresh_crash_count=0`, `holder_close_succeeded=true`, and
  `holder_fatal_latch=false`
- Five-Turn hold conditions are any turn failure, fallback, timeout, fresh
  crash, close failure, fatal latch, or missing backend evidence
- DEV diagnostics now exposes `NPU Persistent Holder Ten-Turn Probe` with
  `Run Holder Ten-Turn Probe`, `Copy Holder Ten-Turn Summary`, and
  `Copy Holder Ten-Turn Full Dump`
- Ten-Turn performs one create, ten holder-gated one-shot decodes, and one
  close; it does not connect normal chat routing and is not true Engine
  persistent reuse
- Ten-Turn pass requires `holder_create_succeeded=true`,
  `run_count_completed=10`, `run_decode_reached_count=10`, QNN HTP / FastRPC
  backend evidence, generally natural quality summary,
  `fallback_used_count=0`, `timeout_count=0`, `fresh_crash_count=0`,
  `holder_close_succeeded=true`, and `holder_fatal_latch=false`
- Ten-Turn hold conditions are any turn failure, fallback, timeout, fresh
  crash, close failure, fatal latch, or missing backend evidence
- `engine_reuse_observed=unavailable`, `true_engine_persistent_reuse=false`,
  and `persistent_multi_turn_possible=false` remain mandatory
- a clean Ten-Turn result should be compared with recreate Stability Test
  before true Engine persistent reuse API design
- the first Ten-Turn device result was not clean: `run_count_completed=7`,
  `success_count=6`, `failure_count=1`, no fallback/timeout/fresh crash, and
  `true_engine_persistent_reuse=false`
- next work is design-only review in
  `docs/npu_true_engine_persistent_reuse_design.md`, followed by a native
  true Engine holder create/close PoC
- report `ui_execution_expected=false`, `ui_blocked_expected=true`, and
  `run_count_completed=0` as the expected current state
- run `こんにちは` for 10 turns with 500ms wait only after a safe persistent
  adapter path is exposed
- stop on first fatal failure
- report `engine_reuse_observed=unavailable` unless a real API signal exists
- report `restart_app_recommended=true` when engine-create failure is detected
- provide `Copy Persistent Summary` and `Copy Persistent Full Dump`

### Long Generation Test

The Settings screen has `NPU max output tokens（開発用）` with fixed values
`32,64,128,256,512,1024,2048,4096`. Step 3 adds a primary DEV button that runs
the initial comparison plan `32/128/512`.

Current Step 3 behavior:

- run `32/128/512` in stable order
- record status, fallback, timeout, fresh crash, decode reach, timing, tokens/sec,
  backend evidence, quality classification, raw output, and sanitized output
- collect `EOS`, stop reason, finish reason, raw finish status, and generation
  end source when exposed
- write `unavailable` for non-exposed native token/finish telemetry
- ensure quality_candidate_fail output is suppressed from UI/TTS/DB/Markdown
- leave `1024` for an Advanced follow-up after physical-device evidence

### R6 Native Token Streaming Prep

Current Phase 7B is pseudo streaming. Future R6 needs telemetry points that are
not fully exposed today:

- native chunk/token count
- first token/chunk time
- per-chunk timing
- finish reason / stop reason / raw finish status
- generation end reason source
- whether streaming text exactly matches finalized DB/Markdown text

## Recommended Policy

### Primary

Keep or introduce these as primary DEV diagnostics:

- `NPU診断キーをコピー`
- `Copy Compact`
- `NPU Beta安定性テスト開始`
- `NPU Beta長文生成テスト開始`
- One-shot NPU diagnostic flow, either as documented manual flow or a future
  dedicated button

### Advanced

Keep behind an Advanced/legacy foldout:

- GPU diagnostic copy buttons
- `Copy Full Dump`
- `入力コピー` / `出力コピー`
- `メモリ回復確認`
- `ローカルエンジンを再作成`
- `NPU S1 persistent Engine 20回テスト`
- `NPU S1 persistent custom JNI ...`
- developer phase selector `DEV: NPU S1` to `DEV: NPU S5`
- `NPU max output tokens（開発用）`
- `DEV Markdown`

### Candidate Hide

Hide by default after confirmation:

- `Legacy QAIRT244 ChatScreen route`
- `Legacy QAIRT244 prompt template`
- DEV whitespace trace copy blocks outside `DEV_UI_DEBUG_MODE`

### Candidate Remove After Confirmation

No button should be removed in this step. Future removal candidates require:

- a replacement standard-route diagnostic exists
- scripts/artifact parsers no longer need the legacy output
- at least one release window has kept compatibility
- test coverage proves legacy preference values remain parseable or migrated

## Related Tests Reviewed

- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1RepeatedRunDiagnosticsTest.kt`
- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentEngineDiagnosticsTest.kt`
- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentCustomJniDiagnosticsTest.kt`
- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1ProviderTest.kt`
- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244DevNpuInferenceStatsSectionTest.kt`
- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/InferenceStatsSheetContentTest.kt`
- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsNpuRouteUiTest.kt`
- custom debug tests under `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/`

## Tests To Add Next

- Unit test for a future Primary/Advanced diagnostics grouping model.
- Unit test that a future one-shot NPU diagnostic button uses Phase 8 completed
  route and never enables fallback.
- Stability formatter test for 10/50/100 summary output.
- Long Generation matrix formatter test for `32/128/512/1024`, including
  `unavailable` stop/EOS fields.
- Regression test that legacy QAIRT244 controls are developer/advanced only.

## Proposed Next Steps

### Step 2: Stability Test runner/UI

Implemented as a minimal wrapper around the existing repeated-run diagnostics.
Keep `quality_candidate_fail`, fallback, timeout, fresh crash,
decode-not-reached, and memory-pressure stop lines. The current primary entry is
10 runs in existing safe recreate mode; 50/100 remain future work.

### Step 3: Long Generation Test runner/UI

Implemented as a minimal DEV-only runner for `max_output_tokens=32/128/512`.
Record output quality, decode time, total time, tokens/sec, EOS/stop reason when
exposed, and `unavailable` otherwise. `1024` remains Advanced follow-up work.

### Step 4: DEV diagnostics Primary/Advanced foldout

Implemented as a UI-only reorganization without deleting controls:

- Primary: one-shot copy, compact copy, stability, long generation.
- Advanced: legacy route, persistent Engine, custom JNI, full dump, memory,
  manual recreate, GPU root-cause copy buttons.

### Step 5: R6 native token streaming investigation

Keep pseudo streaming unchanged. Add observation-only probes for native streaming
capability, token/chunk telemetry exposure, finish reason, and safe final text
consistency before any native streaming behavior is attempted.

## Verification Notes

Physical NPU-device runs were not executed in this inventory step.

`not run: requires physical NPU device`

- One-shot NPU diagnostic with prompt `こんにちは`
- Stability repeated run on device
- Long generation matrix on device
- R6 native token/chunk telemetry probes
