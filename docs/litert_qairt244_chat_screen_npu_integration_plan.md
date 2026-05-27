# QAIRT 2.44 Guarded ChatScreen NPU Integration Plan

## Max512 Activity Restart Only Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930/`

Activity restart only is not a ChatScreen promotion path. The hidden comparison
kept the app process alive when possible and did not use force-stop between
prompts. The Python code prompt still timed out after native pre-RunDecode
`SetMaxOutputTokens(512)` evidence, with no completed result, cleanup,
`Engine.close`, raw output, or sanitized output. The two Japanese prompts
completed as `natural_japanese`.

ChatScreen plan impact: no normal ChatScreen promotion, assistant-list
insertion, DB, TTS, Markdown renderer, streaming, selectedPath=NPU persistence,
release behavior, or standard behavior change. Sequential and
Activity-restart-only 512 remain non-baseline. 512 remains hidden
per-run-isolated candidate only, 256 remains the hidden experimental candidate,
H1 remains 128-only, and 1024+ remains blocked.

## Max512 Sequential Cleanup/Resource Investigation - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_sequential_cleanup_resource_investigation/20260527_082307/`

The sequential timeout is now tracked as warm-process/resource inheritance, not
as evidence that 512 or the SM8750 NPU path is unsupported. However, this does
not create a ChatScreen integration path. The normal UI would be a sequential
conversation surface, and sequential 512 remains non-baseline.

Decision: no normal ChatScreen promotion, assistant-list insertion, DB, TTS,
Markdown renderer, streaming, selectedPath=NPU persistence, release behavior,
or standard behavior change. 512 remains hidden per-run isolated candidate
only. 256 remains the hidden experimental candidate, H1 remains 128-only, and
1024+ remains blocked. The next single-axis experiment, if approved, should be
Activity restart only between prompts.

## Max512 Per-Run Isolated Gate - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_per_run_isolated_gate/20260527_075622/`

The 512 gate is defined only for hidden `mode=per_run_isolated`: app
force-stop before and after each prompt, `max_output_tokens=512`, RunDecode and
`SetMaxOutputTokens(512)` evidence, no timeout, no fresh crash, no fallback,
QNN/HTP/FastRPC evidence, cleanup/`Engine.close`, no retained-memory condition,
code-aware sanitizer, preserved indentation, closed/completed code fence, and
side-effect flags false.

ChatScreen plan impact: no normal ChatScreen promotion. Sequential 512 remains
non-baseline, and the per-run isolated gate does not authorize assistant-list
insertion, DB, TTS, Markdown renderer, streaming, selectedPath=NPU persistence,
release behavior, or standard behavior. 256 remains the hidden experimental
candidate, H1 remains 128-only, and 1024+ remains blocked.

## Max512 Force-Stop Between Prompts - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/`

The hidden comparison passed when every prompt was isolated by app force-stop
before and after the run. The Python calculator prompt returned `useful_code`
with `decode_ms=12448`, `elapsed_ms=14000`, preserved indentation, and a closed
code fence. All runs recorded QNN/HTP/FastRPC evidence,
cleanup/`Engine.close`, `timeout=false`, `fresh_crash=false`, and
`fallback_used=false`.

ChatScreen plan impact: no normal ChatScreen promotion. The result narrows the
512 issue to sequential/resource-cleanup behavior and supports a possible
hidden per-run isolated mode, but it does not authorize assistant-list
insertion, DB persistence, TTS, Markdown renderer, streaming, selectedPath=NPU
persistence, release behavior, or standard behavior. 256 remains the hidden
experimental candidate and 1024+ remains blocked.

## Max512 Repeated Code Timeout Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_timeout_root_cause_review/20260527_065926/`

The ChatScreen integration plan remains blocked for 512. The Python code prompt
is unstable at 512: it completed in an isolated bounded retry, but timed out
when run second in the code-aware three-prompt comparison. No completed
sanitized code output is available from the sequential run.

Decision: no normal ChatScreen promotion, assistant-list insertion, DB, TTS,
Markdown, streaming, or selectedPath persistence. 256 remains the hidden
experimental candidate; 512 remains extended experimental; 1024+ remains
blocked.

## Max512 Code-Aware Three-Prompt Rerun - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/`

The hidden code-aware rerun remains non-promotable to ChatScreen. `こんにちは`
and the short Lami NPU prompt returned sanitized Japanese responses, but the
Python calculator prompt timed out with no completed sanitized code output.
The artifact therefore does not prove code display quality for 512.

Decision: do not connect 512 to normal ChatScreen, assistant-list insertion,
DB, TTS, Markdown renderer, streaming, or selectedPath persistence. 512 remains
extended experimental; 1024 remains blocked.

## Code-Aware Sanitizer Update - 2026-05-27

Artifact:
`artifacts/qairt244_code_aware_sanitizer_review/20260527_012650/`

The hidden NPU sanitizer now handles fenced code blocks without stripping
indentation and can close a truncated code fence in sanitized display text. This
keeps raw native output out of UI/state/renderer paths and preserves the
existing non-code sanitizer behavior for Gemma turn tokens and prompt echo.

ChatScreen plan impact: no normal ChatScreen promotion. Do not connect this to
assistant-list insertion, DB, TTS, Markdown renderer, streaming, or selectedPath
persistence. The next ChatScreen-relevant evidence would be a separately
approved bounded 512 three-prompt comparison after the sanitizer fix.

## Max512 Code Output Quality Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_output_quality_review/20260527_011217/`

The 512 code prompt bounded retry is not a ChatScreen promotion candidate. The
raw output has useful calculator code and preserved indentation, but sanitized
output strips indentation and leaves an unclosed code fence after token-limit
truncation. This creates a Markdown/code display risk even though NPU safety
signals passed.

Decision: do not connect 512 code output to normal ChatScreen, assistant-list
insertion, DB, TTS, Markdown, streaming, or selectedPath persistence. Keep 512
as extended experimental until a code-aware display sanitizer/gate exists and a
bounded 512 three-prompt comparison passes.

## Max512 Three-Prompt Hidden Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_compare/20260527_003429/`

The hidden receiver executed the three approved prompts once each at
`max_output_tokens=512`. This is not promotable to ChatScreen because the
Python calculator prompt timed out before a completed sanitized code response.
The two Japanese prompts completed with sanitized `natural_japanese` output.

Decision: do not promote 512 to normal ChatScreen or H1. Do not insert the
output into the assistant message list, persist it to DB, route it through
TTS/Markdown/streaming, or save selectedPath as NPU. Keep the 512 result as a
rollback artifact until a separately approved 512 comparison passes all three
prompts, especially the code-generation prompt as `useful_code`.

Timeout review:
`artifacts/qairt244_npu_max_output_512_code_timeout_review/20260527_005112/`
confirms this is not a ChatScreen promotion candidate. The Python prompt
entered the native path and reached pre-RunDecode `SetMaxOutputTokens(512)`
evidence, but no completed result or cleanup evidence was captured before the
runner force-stop. Normal ChatScreen remains disconnected.

Bounded retry:
`artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116/`
completed the same prompt once with `timeout_seconds=60`,
`quality_classification=useful_code`, QNN evidence, and cleanup/`Engine.close`
evidence. ChatScreen plan impact remains none. The retry does not connect
normal ChatScreen, assistant-list insertion, DB, TTS, Markdown, streaming, or
selectedPath persistence, and it does not promote 512. A full 512 comparison
and display-format review are still required before any UI-facing change.

## Max512 Single Hidden Prompt - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_single_prompt/20260527_002303/`

The hidden standardDebug receiver executed one prompt, `こんにちは`, with
`max_output_tokens=512`. It succeeded with `RunDecode` reached,
`npu_backend=NPU`, QNN/HTP/FastRPC evidence, and sanitized natural Japanese
output. The raw output still contained prompt echo and `<end_of_turn>` markers;
sanitizer removed them before display-quality classification.

Decision: proceed only to a 512 three-prompt hidden comparison after separate
approval. Do not promote 512 to normal ChatScreen or H1. Do not insert the
output into the assistant message list, persist it to DB, route it through
TTS/Markdown/streaming, or save selectedPath as NPU.

## Max256 Guard-Only Patch Staged - 2026-05-26

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155/`
- preflight artifact:
  `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300/`

The external LiteRT-LM qairt244 editable-prompt guard has been raised to 256 in
a limited rebuild and the lami preflight passed against that artifact. This
does not change the ChatScreen plan: no normal ChatScreen route, assistant
message list, DB, TTS, Markdown, streaming, selected-path persistence, NPU
generation, `Engine.initialize`, or `RunDecode` was executed or connected in
this phase.

## Max256 Single Hidden Prompt - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046/`

The hidden standardDebug receiver executed one prompt, `こんにちは`, with
`max_output_tokens=256`. It succeeded with `RunDecode` reached and sanitized
natural Japanese output. This remains hidden experimental only: no normal
ChatScreen route, assistant message list insertion, DB, TTS, Markdown,
streaming, or selected-path persistence is connected.

Decision: proceed only to a 256 three-prompt hidden comparison. Do not promote
256 to normal ChatScreen or H1.

## Max256 Three-Prompt Hidden Comparison - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

The hidden receiver ran the three approved prompts once each at
`max_output_tokens=256`. All three succeeded with QNN/HTP/FastRPC evidence,
no fallback, no timeout, no fresh crash, no selected-path persistence, and no
DB/TTS/Markdown/streaming ingress. The code prompt was classified as
`useful_code`; the two Japanese prompts were classified as `natural_japanese`.

ChatScreen plan impact: none. This is hidden experimental evidence only. Do
not insert the 256 output into the assistant message list, do not persist it to
DB, do not route it through TTS/Markdown/streaming, and do not treat it as a
normal ChatScreen baseline.

Result commit decision: 256 is fixed only as a hidden experimental baseline
candidate. Normal ChatScreen remains disconnected. Before considering 512, the
next phase must produce a separate native guard/build/preflight, then one
single-prompt hidden run with `RunDecode` reached, QNN evidence, no timeout, no
fresh crash, no fallback, memory-after-10s recovery, and sanitizer quality
review.

## Native Max Output Token Limit Investigation - 2026-05-26

The 128-token ceiling is currently a custom qairt244 native editable-prompt
guard, not a proven ChatScreen/UI limit. The guard runs before
`DecodeConfig::SetMaxOutputTokens` and before `RunDecode`, so the 256 hidden
compare never exercised LiteRT-LM decode or QNN runtime behavior at 256.

ChatScreen plan impact: none. Do not connect 256, 512, 1024, 2048, or 4096
token requests to normal ChatScreen. Keep H1 and any future handoff work pinned
to sanitized 128-token metadata until a staged native guard patch and hidden
runtime validation pass separately.

## Max Output Tokens 256 Hidden Compare - 2026-05-26

The hidden experimental 256-token comparison was run in
`artifacts/qairt244_npu_max_output_256_quality_compare/20260526_201129/`.
It does not change the ChatScreen integration plan.

Outcome:

- requested `max_output_tokens=256`
- native entrypoint reported `native_max_output_tokens_limit=128`
- native detail: `invalid_max_output_tokens`
- all three prompts returned `empty_after_sanitize`
- `fallback_used=false`, `timeout=false`, `fresh_crash=false`
- `selected_path_npu_saved=false`
- `standard_route_connected=false`, `normal_ui_route_connected=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`

Decision: do not promote 256 into ChatScreen or H1. Keep
`sanitizer_only + max_output_tokens=128` as the hidden display baseline.
Normal ChatScreen, assistant message list, DB, TTS, Markdown, and streaming
remain disconnected from NPU output.

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

## Phase H1 Freshness And Transition Guard

The hidden-to-UI Phase H1 work now has a pure Kotlin pre-ChatScreen reducer for
artifact metadata freshness and transient preview transitions:

- no `ChatScreen` call site is added by this guard
- no NPU generation, `Engine.initialize`, or `RunDecode` is run
- artifact timestamps are accepted as epoch milliseconds through
  `artifact_timestamp_ms`, `artifact_timestamp`, `synced_at`, or `created_at`
- only artifacts within 24 hours are fresh
- stale, missing, or future timestamps hide the preview or map to rollback
- clear events cover new input, navigation away, toggle OFF, failure/rollback,
  and app restart
- refresh re-reads artifact metadata only and reapplies the mapper when fresh
- refresh records `runsNpu=false`, `initializesEngine=false`, and
  `runsDecode=false`
- DB, TTS, Markdown, streaming, and selected-path persistence remain
  disconnected

The Phase H1 metadata boundary now fixes the future ChatScreen read contract:

- read only artifact key-value text, maps, or already-read file content
- require the minimum metadata fields documented in
  `docs/litert_qairt244_npu_phase_h1_transient_ui_surface.md`
- drop `raw_output`, model path, token dumps, full native diagnostics, and
  unknown keys before UI input
- reject missing required fields, invalid booleans, and invalid numbers as
  rollback input
- use the last value for duplicate keys
- when `dev_enable_npu_chatscreen_route=false`, do not read or parse metadata
- when true, still require fresh artifact metadata and promotion gate pass
- no run, retry, fallback, `Engine.initialize`, or `RunDecode` is attached

The metadata-to-presenter integration test now fixes the future transient-card
input/output contract before any ChatScreen wiring:

- valid fresh key-value metadata maps to `DevOnlyNpuPhaseH1UiState.visible=true`
- only `sanitized_output` becomes `outputPreview`
- `raw_output` is not present in input/state output
- fallback, timeout, non-natural quality classification, standard route
  connection, and DB ingress map to hidden rollback state
- side-effect flags stay false for every success and rollback state
- toggle false still means metadata provider is not called

The Phase H1 card view model contract now fixes the read-only display object a
future transient card may receive:

- success card is visible and shows sanitized body only
- rollback/failure cards are hidden with `body=null` and reason summary
- hidden card is invisible with no body and no warnings
- detail lines include max-output, decode-ms, backend evidence, artifact path,
  selected-path false, and DB/TTS/Markdown/streaming false
- raw output is never exposed
- retry, persistence, TTS, Markdown, and streaming controls are always false
- snapshot text is tested before any UI component exists

The Phase H1 preview renderer now fixes the future formatter contract before
any Compose or ChatScreen wiring:

- renderer API is `renderLines(model)` and `renderContractText(model)`
- success lines render badge/title, status, subtitle, output label, sanitized
  body, reason, details, and optional warnings in that order
- rollback and hidden models render no lines because `visible=false`
- raw output and turn-template tokens are absent from rendered text
- retry, persist, TTS, Markdown button, and streaming indicator labels are not
  rendered
- detail lines keep stable order

The first minimal H1 wiring is limited to `NpuDiagnosticChatActivity`:

- no normal ChatScreen conversation route is connected
- no assistant message list insertion
- explicit `dev_enable_npu_chatscreen_route` intent extra defaults false
- false means metadata is not read or parsed
- true reads artifact metadata only and runs mapper/presenter/card/renderer
- fresh gate-passing sanitized output becomes read-only diagnostic text
- rollback, stale, or hidden states render no preview lines
- no retry button, auto fallback, DB, TTS, Markdown, streaming,
  `Engine.initialize`, `RunDecode`, or selected-path persistence

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

## Phase H1 Diagnostic UI Capture Supplement - 2026-05-26

The H1 transient preview remains outside the normal ChatScreen conversation
route. Supplemental artifact
`artifacts/qairt244_phase_h1_transient_preview_ui_capture/20260526_064732`
records the Diagnostic-only UI evidence for the existing wiring:

- representative screenshot/window captured
- `DEV ONLY - DEV NPU transient preview`
- `Status: SUCCESS`
- sanitized Japanese output rendered
- raw output and template tokens absent from the UI
- DB/TTS/Markdown/streaming flags false
- standard and normal UI route flags false
- `npu_generation=false`, `engine_initialize=false`, `run_decode=false`

No ChatScreen assistant message insertion, DB write, TTS, Markdown, streaming,
retry, fallback, or selected-path NPU persistence is introduced by this capture
pass.

## Phase H1 Read-Only Card Wiring - 2026-05-26

The Diagnostic/DEV screen now contains a dedicated read-only transient card for
the H1 preview. The normal ChatScreen conversation route remains disconnected.

The card consumes the same H1 renderer output already covered by tests and
artifact capture. It displays sanitized output and safety metadata only. It does
not insert an assistant message, does not call the standard route, does not save
to DB, does not speak TTS, does not render Markdown, and does not stream.

Rollback, stale, hidden, or gate-failed metadata hides the card and does not
offer retry, fallback, or rerun controls.

## Phase H1 Hidden-State Regression - 2026-05-26

The Diagnostic/DEV read-only card was captured with non-success metadata states
before any normal ChatScreen promotion:

- stale metadata hides the card
- rollback metadata hides the card
- `dev_enable_npu_chatscreen_route=false` skips metadata read and hides the card

The success baseline still renders sanitized output. Hidden captures keep
`standard_route_connected=false`, `normal_ui_route_connected=false`, `db=false`,
`tts=false`, `markdown=false`, `streaming=false`, `engine_initialize=false`, and
`run_decode=false`.

## Phase H1 Compose Adapter Contract - 2026-05-26

`DevOnlyNpuPhaseH1ComposeAdapter` now defines the narrow model a future
Diagnostic-only Compose surface may read.

The adapter explicitly keeps H1 out of the normal conversation path:

- `insertIntoAssistantList=false`
- no DB persistence
- no TTS
- no Markdown rendering
- no streaming
- no retry/fallback buttons
- hidden or rollback card states become `shouldShowSurface=false`

No ChatScreen wiring or Compose UI component is introduced by this step.

## Phase H1 Diagnostic Preview Host Contract - 2026-05-26

The H1 Diagnostic preview host contract fixes the final data shape before any
future UI surface:

- `DevOnlyNpuPhaseH1ComposeModel -> DevOnlyNpuPhaseH1PreviewHostState`
- success produces read-only render text
- stale/rollback/hidden/toggle false produce hidden host state
- host never exposes assistant insertion
- host never exposes DB/TTS/Markdown/streaming actions
- host never exposes run/retry/fallback actions

No ChatScreen normal route, assistant message list insertion, or Compose UI
implementation is added by this contract.

## Phase H1 XML Card / Preview Host Consistency - 2026-05-26

The Diagnostic XML/read-only card display text and preview host render text are
now covered by a shared pure helper and contract tests.

The consistency tests assert:

- success text order and content match
- hidden, stale, rollback, and toggle-false outputs are empty
- raw output and template tokens are absent
- assistant insertion, DB, TTS, Markdown, streaming, retry, and fallback are
  false

No ChatScreen connection or formal Compose UI implementation is included.

## Phase H1 Preview Consistency Contract - 2026-05-26

Before any ChatScreen connection, the Diagnostic-only H1 preview chain is fixed
by a consistency snapshot:

- XML/read-only card helper output
- PreviewRenderer contract text
- PreviewHost render text
- Compose adapter render text and safety contract

The success path must match byte-for-byte across render outputs. Hidden,
rollback, stale, and toggle-false states must remain empty/hidden. The contract
also reasserts no assistant list insertion, no DB/TTS/Markdown/streaming, no
retry/fallback, and no NPU/engine/decode execution.
