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
| `Copy Long Summary` | `ChatScreen.kt`, `NpuLongGenerationDiagnostics.kt` | `buildNpuLongGenerationSummaryCopyText(...)` | Copy the NPU Beta Long Generation summary only. | KEEP_PRIMARY | Long-generation case output can be large, especially 512+ tokens; summary copy keeps reviewable aggregate keys. | `NpuLongGenerationDiagnosticsTest` (`Copy Long Summary...`) | Keep beside the Long Generation runner. |
| `Copy Long Full Dump` | `ChatScreen.kt`, `NpuLongGenerationDiagnostics.kt` | `buildNpuLongGenerationFullDumpCopyText(...)` | Copy the Long Generation summary plus all per-token-plan case blocks. | KEEP_PRIMARY | Required for detailed 32/128/512 comparison and future 1024-token investigation. | `NpuLongGenerationDiagnosticsTest` (`Copy Long Full Dump...`) | Keep; use for physical-device artifacts when output quality or performance differs by token limit. |
| `Copy Persistent Summary` | `ChatScreen.kt`, `NpuS1PersistentEngineDiagnostics.kt` | `buildNpuPersistentEngineSummaryCopyText(...)` | Copy Persistent Engine Multi-turn summary only. | KEEP_PRIMARY | Persistent diagnostics can now block before generation when the NPU session API would require unsupported logits output; summary copy captures the API mode and block reason in one tap. | `NpuS1PersistentEngineDiagnosticsTest` | Keep beside the Persistent Engine Multi-turn runner. |
| `Copy Persistent Full Dump` | `ChatScreen.kt`, `NpuS1PersistentEngineDiagnostics.kt` | `buildNpuPersistentEngineFullDumpCopyText(...)` | Copy Persistent Engine Multi-turn summary plus detail/native/API diagnostics. | KEEP_PRIMARY | Needed to share the full blocked/session/API evidence or future per-run adapter evidence. | `NpuS1PersistentEngineDiagnosticsTest` | Keep; future persistent adapter artifacts should use this path. |
| `Copy Repeated Summary` | `ChatScreen.kt`, `NpuS1RepeatedRunDiagnostics.kt` | `buildNpuS1RepeatedRunSummaryCopyText(...)` | Legacy/compatibility repeated-run summary copy path. | KEEP_ADVANCED | Retained for existing DEV trace affordances and tests while the Primary label moves to `Copy Stability Summary`. | `NpuS1RepeatedRunDiagnosticsTest` | Keep as compatibility; prefer `Copy Stability Summary` in Primary. |
| `Copy Full Dump` | `ChatScreen.kt` | `npuStandardRouteS1DevFullDumpCopyText` / full dump formatter | Copy full NPU diagnostic dump. | KEEP_ADVANCED | Required for root-cause detail, but too verbose for routine one-shot checks. | `NpuStandardRouteS1ProviderTest` (`Copy Full Dump...`) | Keep advanced; report generator can consume full dumps when needed. |
| `メモリ回復確認` | `ChatScreen.kt`, `LocalMemoryDiagnostics.kt` | `startMemoryRecoveryCheck()` / `captureLocalMemorySnapshot(...)` | Capture current and delayed memory snapshots after local/NPU activity. | KEEP_ADVANCED | Useful for lifecycle/memory regression and repeated-run stabilization; not a primary NPU route gate. | `LocalMemoryDiagnosticsTest` | Keep advanced; include only aggregate memory deltas in stability report. |
| `NPU Beta安定性テスト開始` | `ChatScreen.kt`, `NpuS1RepeatedRunDiagnostics.kt` | `startNpuS1RepeatedRun()` -> `NpuStandardRouteS1Bridge(...).run(...)` | Serial NPU runs with prompt/count/wait/mode controls; records success/fallback/timeout/crash/decode/quality/timing/memory. | KEEP_PRIMARY | Step 2 adds the `NPU Beta Stability Test` label and summary keys while reusing the existing S1 repeated-run runner. The initial primary entry is safe recreate mode, 10 runs, wait 500ms. Internal S1 naming remains for compatibility. | `NpuS1RepeatedRunDiagnosticsTest`, `NpuStandardRouteS1BridgeTest`, `NpuStandardRouteS1ProviderTest` | Step 3 should add a Long Generation Test. Later stability work can enable 50/100 only after explicit safety review. |
| `NPU Beta長文生成テスト開始` | `ChatScreen.kt`, `NpuLongGenerationDiagnostics.kt` | `startNpuLongGenerationTest()` -> `NpuStandardRouteS1Bridge(...).run(maxOutputTokens=32/128/512)` | Compare NPU Beta behavior at larger output-token limits. Records status, fallback, timeout, fresh crash, decode reach, timing, tokens/sec, quality, backend evidence, raw/sanitized output, and non-exposed stop/EOS fields as `unavailable`. | KEEP_PRIMARY | This is the minimal Long Generation Test entry. It uses the existing S1 bridge and does not change NPU route behavior, fallback policy, persistent engine behavior, custom JNI, UI/TTS/DB delivery, or native streaming. | `NpuLongGenerationDiagnosticsTest`, `NpuS1RepeatedRunDiagnosticsTest` | Later add 1024 as Advanced after 32/128/512 physical-device evidence is stable. |
| `キャンセル` under repeated run | `ChatScreen.kt` | `cancelNpuS1RepeatedRun()` | Cancel active repeated-run job. | KEEP_PRIMARY | Required safety control for long-running stability tests. | Cancellation status covered in `NpuS1RepeatedRunDiagnosticsTest`. | Keep with Stability Test. |
| `キャンセル` under Long Generation Test | `ChatScreen.kt` | `cancelNpuLongGenerationTest()` | Cancel active long generation comparison. | KEEP_PRIMARY | Required safety control because 512-token runs can take materially longer than one-shot diagnostics. | Formatter/state cancellation is represented in `NpuLongGenerationDiagnostics.kt`; UI execution requires physical NPU. | Keep with Long Generation Test. |
| `NPU永続Engine状態確認` / `NPU Persistent Engine Multi-turn Probe (blocked)` | `ChatScreen.kt`, `NpuS1PersistentEngineDiagnostics.kt`, `app/src/debug/.../NpuS1PersistentEngineDevProbe.kt` | `startNpuS1PersistentEngineProbe()` -> `NpuS1PersistentEngineProbeRunner.run(...)` | Confirm current persistent-NPU API exposure state and copy the blocked reason. | KEEP_PRIMARY | Current NPU state is expected to block before generation: session API requires unsupported logits output, and the standard-route adapter/native decode path is not yet exposed for persistent multi-turn. `run_count_completed=0` is expected, not a UI failure. | `NpuS1PersistentEngineDiagnosticsTest` | Next work is exposing/investigating the successful standard-route adapter/native decode path for persistent multi-turn. |
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
