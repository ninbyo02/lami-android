# QAIRT 2.44 Guarded ChatScreen NPU Integration Plan

Date: 2026-05-23

Scope: design only. This document defines the conditions for any future
normal `ChatScreen` NPU integration after the Diagnostic Chat milestones. It
does not authorize implementation, NPU generation, `Engine.initialize`,
`RunDecode`, high-level `generateResponse`, or normal `selectedPath=npu`.

## Current Baseline

Proven in `customBuildExperimentDebug` on Nubia Z70S Ultra / SM8750:

- QAIRT 2.44 NPU initialize succeeds.
- QNN HTP / V79 / FastRPC path succeeds.
- lower-level 1-token and 3-token smoke runs succeed.
- Diagnostic Chat guarded UI run succeeds.
- editable prompt `Hello` succeeds with `max_output_tokens=3`.
- one-shot hardening succeeds: DEV checkbox off and Run disabled after
  completion.
- Diagnostic Chat fallback / timeout / recovery passes.
- fresh crash evidence: none.
- normal `ChatScreen`: not connected.
- normal `selectedPath=npu`: not used.

## Integration Phases

### Phase A: DEV Hidden Toggle Only

- Add no visible production UI.
- Gate behind `customBuildExperimentDebug` and a DEV-only hidden toggle.
- Default state remains CPU/GPU behavior.
- NPU route is never selected automatically at app start.
- DEV toggle state is not persisted to normal user settings.

### Phase B: Diagnostic Chat Equivalent Short Prompt

- Keep the same prompt validator used by Diagnostic Chat.
- Prompt limit remains 32 ASCII characters.
- `maxOutputTokens` remains fixed at 3.
- Use the lower-level isolated path only.
- Do not call high-level `generateResponse`.

### Phase C: One Normal ChatScreen Short Run

- Permit one non-streaming short run only after Phase A and Phase B pass.
- Do not save to the normal message DB in the first implementation.
- Record result to a DEV diagnostic artifact first.
- Disable the NPU route immediately after one run until reviewed.

### Phase D: Expansion After Fallback Review

- Consider broader prompts only after timeout, failure, cleanup, memory, and
  rollback behavior are verified in the normal UI shell.
- Automatic CPU/GPU retry remains a later phase.
- Streaming, stop button integration, DB persistence, TTS, and Markdown output
  handling remain separate reviews.

## Enable Conditions

All conditions are required before any normal `ChatScreen` NPU implementation:

- `customBuildExperimentDebug` only.
- Nubia / SM8750 device check passes.
- QAIRT 2.44 aligned runtime libs are staged.
- `libcdsprpc.so` visibility is present through
  `<uses-native-library android:name="libcdsprpc.so" android:required="false" />`.
- native marker `qairt244_editable_prompt_smoke_v1` is present.
- native evidence confirms `DecodeConfig.SetMaxOutputTokens(3)` or a stricter
  cap.
- latest Diagnostic Chat success artifact is fresh.
- Diagnostic Chat fallback / timeout / recovery artifact is present and passes.
- one-shot hardening artifact is present and passes.
- memory cleanup and cold-start force-stop profiles have no fresh crash.
- large binary artifacts are not staged for Git tracking.
- DEV toggle is explicitly enabled for the current run.

## selectedPath=npu Conditions

- Default `selectedPath` remains CPU/GPU.
- `selectedPath=npu` is allowed only inside the DEV-only guarded route.
- It must not be written to normal persisted settings.
- It must not be restored automatically on app launch.
- It must be cleared or ignored after timeout, failure, crash suspicion, or
  Activity recreation.
- If any NPU preflight check fails, the NPU option is disabled for the session.

## Timeout And Failure

- Initial timeout is 30 seconds or less.
- Timeout disables the NPU route for the session.
- Engine/session cleanup is mandatory before the UI leaves running state.
- A timeout must produce a DEV artifact with prompt, cap, elapsed time, and
  cleanup result.
- Crash suspicion or a fresh tombstone disables NPU reuse until a new manual
  Diagnostic Chat verification passes.
- Duplicate success markers or residual `state=started` are treated as failure.

## Fallback Policy

Initial behavior is failure display only:

- Do not automatically retry on GPU/CPU in the same ChatScreen request.
- Do not silently switch backends.
- Show a DEV-visible failure state and artifact path.
- Leave normal chat state unchanged when the NPU route fails before output.
- Automatic GPU retry can be designed only after the failure-display path is
  proven.

## DB / TTS / Markdown / Streaming

Initial normal UI integration must keep these disabled or isolated:

- Message DB: no normal persistence for the first NPU run, or DEV-only log file
  only.
- TTS: disabled.
- Markdown pipeline: do not route NPU output through normal Markdown rendering
  until output shape and escaping are reviewed.
- Streaming: disabled.
- Stop button: not connected in the first implementation.
- Output mode: non-streaming short text only.

## Rollback Conditions

Disable the NPU route and require a new design review if any of the following
occur:

- fresh crash or fresh tombstone
- duplicate success marker
- residual `state=started`
- timeout
- memory growth warning after cleanup
- stale Diagnostic Chat summary used as fresh
- `selectedPath=npu` persists into normal settings
- UI freeze or unresponsive running state
- `Engine.close` / cleanup result is missing or ambiguous
- invalid prompt reaches native execution
- output exceeds the configured token cap

## First Implementation Candidate

Do not wire directly into the existing normal inference path. Add a DEV-only
NPU route adapter with a small surface:

- input: validated 32-character ASCII prompt
- backend: QAIRT 2.44 lower-level isolated NPU path
- token cap: 3 fixed
- output: DEV artifact first, then optional transient UI display
- no DB write
- no TTS
- no Markdown pipeline
- no streaming
- no persisted backend selection

The adapter API boundary is defined separately in:

```text
docs/litert_qairt244_dev_only_npu_route_adapter_plan.md
```

The first candidate call site is the `InferenceTarget.LOCAL` branch in
`ChatScreen.kt`, after prompt capture and before normal message persistence.
The adapter must return before user message insert, assistant message insert,
streaming placeholder updates, TTS, Markdown, and stop button ownership.

Current implementation status:

- `DevOnlyNpuRouteAdapter` schema exists in `customBuildExperimentDebug`.
- `BlockedDevOnlyNpuRouteAdapter` always returns
  `reasonCode=adapter_not_connected`.
- `DevOnlyNpuRouteGate` fixes the DEV hidden toggle and prompt/native support
  gate reasons in pure Kotlin.
- `DevOnlyNpuRoutePlanner` combines the gate and blocked adapter while still
  having no `ChatScreen` call site.
- `NpuDiagnosticChatActivity` shows a display-only
  `Planner Preview (blocked)` section. It calls the planner with a synthetic
  OK gate and the blocked adapter, so the rendered result is
  `adapter_not_connected`.
- Unit tests cover the blocked result and result schema flags.
- Gate tests cover every current failure reason.
- Planner tests cover gate-blocked no-adapter-call behavior and gate-OK
  blocked-adapter behavior.
- `DevOnlyNpuRouteDisplayModel` maps route results into transient UI
  statuses (`SUCCESS`, `BLOCKED`, `TIMEOUT`, `CRASH`, `ERROR`) before any
  normal ChatScreen integration.
- No `ChatScreen` call site exists yet.
- No NPU work is run by the adapter stub.
- The Diagnostic Chat planner preview displays
  `ChatScreen route connected=false`, `selectedPathNpuApplied=false`,
  `npuGeneration=false`, `engineInitialize=false`, and `runDecode=false`.

The transient display model is intentionally not persisted to chat history and
does not enter DB, TTS, Markdown, streaming, stop button, or selected-path
state. It is only a shape for DEV error/result rendering once a guarded
adapter branch exists.

Diagnostic Chat now applies that display model to the existing planner preview.
The current blocked adapter result is shown as:

- `status=BLOCKED`
- `reasonCode=adapter_not_connected`
- `message=NPU route adapter is not connected`

This keeps the future transient error/result appearance fixed without adding a
normal ChatScreen call site.

`DevOnlyNpuTransientPresenter` now defines the next boundary after the display
model. It turns display models into `DevOnlyNpuTransientUiState` for a future
DEV-only branch, while hard-coding:

- `shouldPersistToDb=false`
- `shouldSpeakTts=false`
- `shouldRenderMarkdown=false`
- `shouldStream=false`

Those flags remain false even for `SUCCESS`. This prevents a future initial
ChatScreen experiment from accidentally entering message persistence, TTS,
Markdown, or streaming before the explicit integration phase.

Diagnostic Chat now applies the presenter to the planner preview. The current
blocked adapter preview renders:

- `transient_status=BLOCKED`
- `transient_reasonCode=adapter_not_connected`
- `shouldPersistToDb=false`
- `shouldSpeakTts=false`
- `shouldRenderMarkdown=false`
- `shouldStream=false`

This confirms the transient state shape before any normal ChatScreen branch is
introduced.

## Disabled Blocked Branch

The insertion point now has a disabled blocked branch:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2349
```

Location:

- `InferenceTarget.LOCAL` branch
- after `val requestPrompt = userPrompt`
- after blank-prompt early return
- before input clearing
- before new-chat creation and user message DB insert
- before TTS cleanup, Markdown, streaming, stop-button ownership, and any
  selected-path state changes

Reason:

- The DEV-only route can inspect the captured prompt before normal persistence
  or side effects.
- A blocked-adapter branch can return a transient `DevOnlyNpuTransientUiState`
  without saving messages, speaking TTS, rendering Markdown, streaming, or
  persisting `selectedPath=npu`.

Current status:

- disabled by `DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`
- false toggle preserves the existing LOCAL path exactly
- no direct `io.github.ninbyo02.lami.npu` import in main `ChatScreen`
- branch target is reached only through reflection and only if the guard is
  later enabled
- true path uses `BlockedDevOnlyNpuRouteAdapter`, so the result remains
  `adapter_not_connected`
- true path returns only a transient Snackbar summary and returns before DB,
  TTS, Markdown, streaming, stop-button ownership, or selected-path state
- no NPU generation, `Engine.initialize`, or `RunDecode`

## Implementation Checklist

Before code implementation starts:

- Diagnostic Chat latest success artifact is fresh.
- fallback / timeout / recovery artifact passes.
- one-shot hardening artifact passes.
- memory cleanup profile passes.
- cold-start force-stop cleanup passes.
- native marker and token cap evidence are verified.
- large binary artifacts are not Git-tracked.
- `standard`, `npuExperiment`, `galleryStackExperiment`, and `release` are
  unaffected.
- rollback and artifact locations are documented.

## No-Run Confirmation

This planning pass did not run NPU generation, `Engine.initialize`,
`RunDecode`, high-level `generateResponse`, normal `ChatScreen` NPU routing, or
normal `selectedPath=npu`.

## Disabled Branch Launch Verification

Artifact:

```text
artifacts/qairt244_chat_screen_blocked_branch_disabled_verify/20260523_215825/
```

Result:

- build/install: `customBuildExperimentDebug` succeeded
- launch target: `io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity`
- normal `ChatScreen` launch: succeeded
- current toggle: `DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`
- blocked branch fired: no
- `adapter_not_connected` visible on normal launch: no
- `selectedPath=npu` applied by this path: no evidence
- NPU generation: not run
- `Engine.initialize`: not run
- `RunDecode`: not run

No prompt was sent in this verification. The result only confirms the disabled
branch does not alter normal ChatScreen launch state.

## Normal Send Path Non-Invasive Verification

Artifact:

```text
artifacts/qairt244_chat_screen_normal_send_noninvasive_verify/20260523_221224/
```

Scope:

- toggle stayed `DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`
- no DEV hidden toggle was added
- no prompt was sent
- no LOCAL/GPU/NPU generation was triggered

Static result:

- the blocked branch remains entirely under the false toggle
- disabled flow falls through to the existing `InferenceTarget.LOCAL` path
- the branch is still before DB/TTS/Markdown/streaming locations, but inactive
  in the current build
- main `ChatScreen` still has no direct NPU package import
- `git_diff_summary.txt` is empty for `ChatScreen.kt`, so no code changed in
  this verification

Runtime result:

- normal `MainActivity` / `ChatScreen` launch succeeded
- runtime marker scan was empty for `adapter_not_connected`,
  `DEV NPU blocked`, `Engine.initialize`, `RunDecode`, `selectedPath=npu`,
  QNN, HTP, FastRPC, and `generateResponse`
- normal `selectedPath=npu`: not applied
- NPU generation: not run

This keeps the current verification non-invasive: it confirms the disabled
branch has no launch-time side effect and the static send path still falls
through to the existing implementation when the toggle is false.

## DEV Hidden Toggle Boundary

Artifact:

```text
artifacts/qairt244_dev_hidden_npu_chatscreen_toggle_boundary/20260523_222339/
```

Implementation:

- setting key: `dev_enable_npu_chatscreen_route`
- source: `SettingsPreferences.devEnableNpuChatScreenRouteFlow`
- default: `false`
- storage guard: value is effective only when
  `BuildConfig.CUSTOM_BUILD_EXPERIMENT` is true
- Settings UI: shown only in `customBuildExperimentDebug` DEBUG settings
- ChatScreen guard:
  `BuildConfig.CUSTOM_BUILD_EXPERIMENT && devEnableNpuChatScreenRoute`

Verification:

- the Settings UI shows `DEV: Enable NPU ChatScreen route`
- the switch was observed as `checked=false`
- the switch was not toggled
- no prompt was sent
- blocked branch did not fire
- `adapter_not_connected` did not appear in logcat
- `selectedPath=npu` was not applied
- NPU generation, `Engine.initialize`, and `RunDecode` were not run

This creates the DEV hidden toggle boundary while keeping current runtime
behavior unchanged.
## DEV Toggle ON Blocked Branch Verification (2026-05-23)

- Artifact: `artifacts/qairt244_chat_screen_toggle_on_blocked_branch_verify/20260523_223850/`
- Toggle key: `dev_enable_npu_chatscreen_route`
- Toggle helper: `DevNpuChatScreenToggleActivity`, customBuildExperimentDebug only.
- Precondition reset: `toggle_state_before.txt` records `requested_enabled=false` and `after=false`.
- ON step: `toggle_state_after_on.txt` records `before=false` and `after=true`.
- OFF recovery: `toggle_state_after_off.txt` records `before=true` and `after=false`.
- ChatScreen prompt: `Hello`.
- Result: the DEV-only blocked branch fired and displayed `status=BLOCKED` / `reason=adapter_not_connected`.
- The branch remained transient only: no DB insert, no TTS, no Markdown path, and no streaming path.
- `selectedPath=npu` was not applied or persisted.
- Real NPU execution remained disconnected: no `Engine.initialize`, `RunDecode`, `Backend.NPU`, QNN, HTP, FastRPC, or QAIRT runtime markers were found in the post-run marker scan.

## Real Adapter Preflight Rollback Review (2026-05-24)

Artifact:

```text
artifacts/qairt244_chat_screen_real_adapter_preflight_rollback_review/20260524_082657/
```

This pass is documentation and static preflight only. It does not connect the
real adapter and does not execute NPU generation.

Rollback conditions for the first real-adapter swap:

- fresh crash
- timeout
- duplicate success marker
- missing or unknown `Engine.close` / cleanup result
- `selectedPath=npu` saved or applied to normal route state
- DB/TTS/Markdown/streaming receives NPU output
- `dev_enable_npu_chatscreen_route` does not return OFF after the run
- adapter result reports success while side-effect flags are not all false
- stale artifact or stale summary is used as execution evidence
- after-10s memory remains materially elevated versus the prior baseline
- UI freeze or button/running lock recovery is unclear
- QNN/HTP/FastRPC evidence is missing from a claimed NPU success

Initial real-adapter execution conditions:

- customBuildExperimentDebug only
- Nubia Z70S Ultra / SM8750 only
- `dev_enable_npu_chatscreen_route=true`
- prompt fixed to `Hello`
- `maxOutputTokens=3`
- exactly one run
- timeout at or below 30 seconds
- DB/TTS/Markdown/streaming disabled
- `selectedPath=npu` not saved
- result artifact required
- toggle OFF after success or failure

Allowed next-phase code scope is limited to the customBuildExperimentDebug
adapter implementation, the `DevOnlyNpuChatScreenBlockedBranch` adapter swap
point, runner/script updates, and docs/artifact capture. Broad ChatScreen send
path changes, DB/TTS/Markdown/streaming changes, standard/release changes,
`app/src/main/jniLibs` changes, and selected-path persistence changes remain
forbidden.

## First Real Adapter Attempt From ChatScreen (2026-05-24)

Artifact:

```text
artifacts/qairt244_chat_screen_real_npu_first_run/20260524_084514/
```

The first DEV-only ChatScreen real-adapter attempt was run once with:

- prompt: `Hello`
- `maxOutputTokens=3`
- `dev_enable_npu_chatscreen_route=true`
- customBuildExperimentDebug only

Result:

- result: `failure`
- rollback classification: `rollback-model-file-not-found`
- native detail: `model-file-not-found`
- `Engine.initialize`: not reached
- `RunDecode`: not reached
- QNN/HTP/FastRPC evidence for this specific run: missing in `native_diag.txt`
- DB/TTS/Markdown/streaming: not connected
- `selectedPath=npu`: not saved
- side-effect flags: false
- toggle OFF recovery: confirmed

The fixed model path used by the first adapter implementation did not match the
current app-private model filename. The next step is to resolve the
app-private model path discovery before another real-adapter run. No second run
was performed in this pass.

## Model Path Resolution Attempt From ChatScreen (2026-05-24)

Artifact:

```text
artifacts/qairt244_chat_screen_real_npu_model_path_resolution/20260524_091657/
```

The fixed model path was removed from the customBuildExperimentDebug adapter.
The adapter now resolves `context.filesDir/local_models/*.litertlm`, records the
candidate list and checked file properties, and only enters native execution
when one candidate is selected. The resolved model path is not saved to normal
settings.

Observed result:

- model resolution: `ok`
- resolved model: `/data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/1779578208133_gemma-4-E2B-it.litertlm`
- candidate count: `1`
- checked exists/readable/length: `true` / `true` / `2583085056`
- prompt: `Hello`
- `maxOutputTokens=3`
- result: `failure`
- rollback detail: `engine-create-failed:NOT_FOUND`, `TF_LITE_AUX not found in the model`
- NPU evidence: `QNN_HTP_V79_FastRPC_native_diag`
- DB/TTS/Markdown/streaming: not connected
- `selectedPath=npu`: not saved
- toggle OFF recovery: confirmed

This resolves the previous `model-file-not-found` boundary. The current
rollback boundary is model content compatibility with the QAIRT NPU compiled
model executor, not app-private path discovery.
## 2026-05-25 DEV-only NPU Output Sanitizer

The ChatScreen DEV-only qairt244 route now keeps the SM8750 NPU execution path
unchanged and sanitizes only the transient output shown by the debug route. The
target template for the current output-quality phase is `gemma_it_like`.

Sanitizer scope:

- removes Gemma turn artifacts such as `<start_of_turn>`, `<start_of_turn>user`,
  `<start_of_turn>model`, `<end_of_turn>`, and partial `<end`
- removes a leading user prompt echo line, including the observed
  `>こんにちは` echo
- collapses empty lines and stops before repeated user-turn artifacts
- preserves `raw_output` and writes `sanitized_output` for display
- reports `sanitizer_applied`, `removed_template_token_count`, and
  `removed_prompt_echo`

Real-device evidence:

```text
artifact=artifacts/qairt244_npu_output_sanitizer/20260525_015040
template_mode=gemma_it_like
prompt=こんにちは
raw_output=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
sanitized_output=こんにちは！何かお手伝いできることはありますか？
removed_template_token_count=2
removed_prompt_echo=true
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
```

This does not connect the NPU output to DB, TTS, Markdown, or streaming, and it
does not persist `selectedPath=npu`.

## QAIRT244 Turn-Stop Quality Compare - 2026-05-25

The ChatScreen DEV-only NPU route is treated as route-successful; this phase is display-quality tuning only. The comparison is documented in `docs/litert_qairt244_npu_turn_stop_quality_compare.md` and implemented by `scripts/run_qairt244_npu_turn_stop_quality_compare.sh`.

Static LiteRT-LM inspection found `native stop not exposed` for the qairt244 lower-level Android route. Runtime metadata can carry stop token ids internally, but this JNI path creates a default session config and exposes only `DecodeConfig.SetMaxOutputTokens()` for the editable-prompt run; no per-request stop sequence, stop token, EOS, or `<end_of_turn>` setter is available. Public sampler controls expose topK/topP/temperature/seed, but the qairt244 lower-level native entrypoint does not accept sampler config, and no repetition penalty API was found.

The fixed executable baseline is `enhanced_sanitizer_only_128`. `lower_max_tokens_64_sanitizer` and `lower_max_tokens_32_sanitizer` are rollback-only records, not executable adoption candidates, and `stop_sequence_end_of_turn` is recorded as `not_run/native_stop_not_exposed`. The prompts are `こんにちは`, `はじめまして`, and `こんばんは`; the executable sanitizer-only case uses `max_output_tokens=128` and a 30 second timeout.

The safe adopted baseline from the 2026-05-25 run is enhanced sanitizer-only at `max_output_tokens=128`. Lower caps are not adopted because `64` produced `empty_after_sanitize`, and `32` produced adapter failure / timeout in the comparison artifact. The required evidence remains `QNN_HTP_V79_FastRPC_native_diag`, `fallback_used=false`, sanitizer-only `timeout=false`, `fresh_crash=false`, `selected_path_npu_saved=false`, and no normal UI, DB, TTS, Markdown, or streaming connection.

## NPU Sanitizer Quality Baseline Commit - 2026-05-25

Commit baseline: `sanitizer_only + max_output_tokens=128` is the provisional
hidden experimental display-quality baseline, backed by
`artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`.

Promotion gate: `fallback_used=false`, `fresh_crash=false`, `timeout=false`,
sanitized `quality_classification=natural_japanese`, no template artifact after
sanitize, no repetition or multilingual drift after sanitize, and
`db=false`, `tts=false`, `markdown=false`, `streaming=false`.

Raw native `template_artifact` remains acceptable only as diagnostic evidence;
the displayed sanitized output must be natural Japanese. Native stop sequence /
native turn-stop is not required for this provisional baseline. Standard route
non-connection is covered by `DevOnlyNpuChatScreenBlockedBranchTest`.

The follow-up static investigation is recorded at
`artifacts/qairt244_npu_stop_api_investigation/20260525_214513/`. It found no
public Android/JNI per-run stop sequence, stop token, EOS, or `<end_of_turn>`
API for this qairt244 path, so no native stop comparison is implemented.

## NPU Hidden-To-UI Handoff Plan - 2026-05-25

The next pre-promotion design is documented in
`docs/litert_qairt244_npu_hidden_to_ui_handoff_plan.md`. It keeps
`sanitizer_only + max_output_tokens=128` as the required baseline and does not
implement normal UI promotion.

The first eligible handoff phase is H1 transient preview only: display
`sanitized_output` in a DEV-only transient UI surface, keep `raw_output` in
artifacts only, and keep DB, TTS, Markdown, streaming, selected-path NPU
persistence, and standard route connection disabled. Later phases evaluate
assistant-style temporary display, DB persistence, and TTS/Markdown/streaming
as separate gates.

Phase H1 surface details are fixed in
`docs/litert_qairt244_npu_phase_h1_transient_ui_surface.md`: ChatScreen may use
only a DEV-only transient card/banner/snackbar, outside the assistant message
list, with `sanitized_output`, status, `reasonCode`, `decode_ms`, short backend
evidence, `maxOutputTokens=128`, and short artifact path. It clears on new
input, navigation away, toggle OFF, failure/rollback, app restart, or stale
artifact; refresh may reread artifact metadata only.

Before ChatScreen wiring, Phase H1 is limited to state/display-model/presenter
tests. Those tests must prove sanitized-output-only display, raw-output
exclusion, reason-only failure display, rollback hiding, and
`shouldPersistToDb=false`, `shouldSpeakTts=false`,
`shouldRenderMarkdown=false`, `shouldStream=false`.

The artifact metadata mapper is also tested before ChatScreen wiring. It reads
hidden result key-value metadata, maps only `sanitized_output` into H1 UI input,
discards `raw_output`, and turns promotion-gate mismatches into
rollback/failure input.
