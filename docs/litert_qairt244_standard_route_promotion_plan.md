# QAIRT244 Standard Route Promotion Plan

Date: 2026-05-30

Scope: design only. This document does not implement code, run runtime probes,
install APKs, or change native code.

## Background

The dev-only NPU one-turn conversation path has passed the current promotion
precheck:

- `raw_dialog_tail_variant_b`
- `max_output_tokens=32`
- 5/5 `status=success`
- 5/5 `run_decode_reached=true`
- 5/5 `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- 5/5 `fallback_used=false`
- 5/5 `timeout=false`
- 5/5 `fresh_crash=false`
- 5/5 `sanitized_output=こんにちは。`
- 5/5 `quality_classification=natural_japanese`

This proves the dev-only route can produce a minimal natural Japanese answer.
It does not yet prove that standard ChatScreen integration is safe.

## Promotion Principles

- Promote one surface at a time.
- Keep `Backend.NPU` persistence disabled until the standard route has a
  separate rollback path.
- Keep the first standard route attempt as a non-streaming, single-response
  path.
- Do not connect DB, TTS, Markdown, or streaming in Phase S1.
- Require visible diagnostics for route selection, NPU evidence, fallback,
  timeout, fresh crash, output quality, and side-effect flags.
- A fallback result must not be presented as NPU success.
- Every phase must be reversible by disabling the phase gate without requiring
  native changes.

## Current Integration Surfaces

Primary files to review before implementation:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/TtsMemoryReleasePolicy.kt`
- `app/src/main/java/io/github/ninbyo02/lami/tts/AndroidTtsController.kt`
- `app/src/main/java/io/github/ninbyo02/lami/tts/SpeechTextBuilder.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/text/MarkdownStreamingMode.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/text/MarkdownCodeRepair.kt`
- `app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel.kt`
- `app/src/main/java/io/github/ninbyo02/lami/db/repository/ChatRepository.kt`
- `app/src/main/java/io/github/ninbyo02/lami/MainActivity.kt`
- `app/src/main/java/io/github/ninbyo02/lami/HeldEngineLifecycleBridge.kt`

Dev-only reference files:

- `app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuOneTurnConversationEntry.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuChatScreenBlockedBranch.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244DevOnlyNpuConversationActivity.kt`

The standard route design should reuse proven behavior conceptually, but it
must not depend on debug-only source sets for production route selection.

## Phase S1: ChatScreen Selects NPU Route, Display Only

Goal: let ChatScreen choose the NPU route for a single prompt and display one
assistant response, with no persistence or downstream side effects.

Required behavior:

- `raw_dialog_tail_variant_b`
- `max_output_tokens=32`
- single response only
- DB connection disabled
- TTS disabled
- Markdown disabled
- streaming disabled
- no `Backend.NPU` preference persistence
- fallback is visible and treated as failure for this phase

Change target files:

- `ChatScreen.kt`
- `LocalStreamingRunner.kt` or a new main-source route adapter near it
- a new main-source NPU route gate/contract file
- tests under the existing unit-test source set for route selection and side
  effect flags

Gate conditions:

- explicit debug/developer gate or runtime flag enables S1
- selected model/device matches the proven QAIRT244 SM8750 path
- NPU model resolution confirms required basename/canonical model
- request uses `raw_dialog_tail_variant_b`
- requested/effective `max_output_tokens=32`
- `standard_route_connected=true`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- result must report `run_decode_reached=true`
- result must report `QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `sanitized_output` is non-empty
- `quality_classification=natural_japanese`

Rollback conditions:

- gate flag off returns ChatScreen to the existing non-NPU route
- fallback, timeout, fresh crash, empty output, or non-Japanese quality disables
  the S1 gate for the current run
- standard route must not write DB rows before S1 passes
- standard route must not persist selected backend state

Test items:

- route gate defaults off
- S1 gate on selects NPU only for the allowed model/device condition
- DB/TTS/Markdown/streaming flags remain false
- result display contract includes NPU evidence and side-effect flags
- fallback result is shown as failure, not success
- timeout/fresh crash result leaves standard route state unchanged
- `raw_dialog_tail_variant_b` and `max_output_tokens=32` are fixed

Blockers:

- any requirement to use debug-only classes from `app/src/debug`
- hidden dependency on `Backend.NPU` persistence
- missing failure UI for fallback/timeout/fresh crash
- inability to render the result without DB insertion
- any standard-route call path that automatically starts TTS or Markdown

S1 Gate ON display check:

- temporary local flag: `ENABLE_NPU_STANDARD_ROUTE_S1=true`;
- installed `standardDebug`;
- launched `MainActivity`;
- sent through the Local ChatScreen path;
- confirmed the transient UI block displayed `NPU STANDARD ROUTE S1`;
- confirmed the visible response text `こんにちは。`;
- rollback restored `ENABLE_NPU_STANDARD_ROUTE_S1=false`;
- reinstalled `standardDebug` after rollback;
- `git status -sb` was clean after rollback.

This confirms the S1 display-only gate can render the bridge result in
ChatScreen. It does not change the Phase S1 boundary: DB, TTS, Markdown,
streaming, `Backend.NPU` persistence, and conversation history save remain
unconnected by design.

RealProvider S1 runtime check:

- app package: `io.github.ninbyo02.lami.customnpu`;
- ChatScreen displayed `NPU STANDARD ROUTE S1`;
- ChatScreen displayed `こんにちは。`;
- `qairt244_short_multitoken_smoke_result.txt` was updated;
- `result=success`;
- `prompt_source=dev_only_conversation`;
- requested/effective `max_output_tokens=32`;
- `run_decode_reached=true`;
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`;
- `fallback_used=false`;
- `timeout=false`;
- `fresh_crash=false`;
- `route_type=dev_only_one_turn_conversation`;
- `sanitized_output=こんにちは。`;
- `quality_classification=natural_japanese`;
- `db=false`;
- `tts=false`;
- `markdown=false`;
- `stream=false`.

This confirms the `customBuildExperimentDebug` path
`standard UI -> S1 Gate -> RealProvider -> DevOnlyEntry -> real NPU -> UI
display` is working. `standardDebug` remains S1 Gate disabled / FixedProvider,
and DB/TTS/Markdown/streaming/Backend persistence remain unconnected.

## Phase S2: DB Connection

Goal: persist the user prompt and final assistant response after a successful
S1-style single response.

Change target files:

- `ChatScreen.kt`
- `OllamaViewModel.kt`
- `ChatRepository.kt`
- message/entity tests around user and assistant insertion

Gate conditions:

- S1 is stable for the target prompt family
- DB writes are delayed until NPU result success
- assistant message stores sanitized final output only
- inference stats include route, backend evidence, max output tokens, quality,
  fallback, timeout, and fresh crash fields
- failed NPU runs do not insert successful assistant messages

Rollback conditions:

- DB gate off reverts to S1 display-only behavior
- failed run deletes or avoids placeholder rows
- partial/placeholder rows are not left behind on timeout/crash
- no migration is required for rollback

Test items:

- success inserts one user message and one assistant message
- failure inserts no assistant success row
- stats are attached to the assistant row
- repeated sends do not duplicate rows
- normal non-NPU ChatScreen DB behavior remains unchanged

Blockers:

- current ChatScreen flow requires placeholder assistant rows before inference
- inability to attach NPU diagnostics to the final assistant row
- failure path leaves stale or empty assistant messages

S2 DB gate ON runtime check:

- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S2_DB=true`;
- used `customBuildExperimentDebug`;
- confirmed the standard ChatScreen displayed the normal chat exchange:
  user `こんにちは` and assistant `こんにちは。`;
- confirmed the `NPU STANDARD ROUTE S1` debug block remained visible;
- confirmed `databases/chat-database-wal` updated at `2026-05-30 14:34`;
- confirmed `databases/app_database-wal` updated at `2026-05-30 14:34`;
- direct SQL inspection was not performed because `sqlite3` was not available
  on the device;
- rollback restored `ENABLE_NPU_STANDARD_ROUTE_S2_DB=false`;
- reinstalled after rollback;
- `git status -sb` was clean after rollback.

This confirms the minimal S2 happy path
`S1 success -> S2 save candidate -> user row -> assistant row`. Failure-row
avoidance remains a separate safety check. Markdown, TTS, streaming, and
`Backend.NPU` persistence remain unconnected.

## Phase S3: Markdown Connection

Goal: allow the final NPU assistant response to pass through the same final
Markdown processing path as other local responses, still non-streaming.

Change target files:

- `ChatScreen.kt`
- `MarkdownStreamingMode.kt`
- `MarkdownCodeRepair.kt`
- markdown rendering tests for final, non-streaming text

Gate conditions:

- S2 DB writes are stable
- Markdown is applied only after final sanitized output
- no streaming Markdown chunk processing in this phase
- plain Japanese output remains unchanged
- code fence repair is recorded if applied

Rollback conditions:

- Markdown gate off stores and displays sanitized plain text
- Markdown processing exception falls back to plain text and records failure
- no DB corruption if Markdown rendering fails

Test items:

- `こんにちは。` remains `こんにちは。`
- Markdown emphasis/list/code cases render or fall back predictably
- `markdown=true`, `streaming=false`
- `repair_applied` is reflected in diagnostics
- failure falls back to plain sanitized output

Blockers:

- Markdown processor assumes streaming chunks
- rendered text differs from persisted text without diagnostics
- Markdown failure can crash ChatScreen

## Phase S4: Streaming Connection

Goal: connect NPU output to the standard streaming UI only after the stable
non-streaming path is proven.

S4-A pseudo streaming gate ON smoke check:

- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=true`;
- used `customBuildExperimentDebug`;
- confirmed install/start succeeded;
- confirmed ChatScreen displayed `NPU STANDARD ROUTE S1`;
- confirmed ChatScreen displayed `こんにちは。`;
- confirmed ChatScreen displayed `NPU STANDARD ROUTE S4-A FINAL`;
- confirmed final S4-A text `こんにちは。`;
- the response was short, so the run was one chunk equivalent;
- captured `/tmp/npu_s4a_gate_on.png`;
- rollback restored `ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=false`;
- reinstalled after rollback;
- `git status -sb` was clean after rollback.

This confirms the S4-A pseudo streaming display gate can render the final NPU
answer through the NPU-specific transient state. It does not promote real token
streaming. TTS and `Backend.NPU` persistence remain unconnected.

Change target files:

- `ChatScreen.kt`
- `LocalStreamingRunner.kt`
- streaming append/normalization helpers in `ChatScreen.kt`
- tests for partial update ordering and stop behavior

Gate conditions:

- S3 is stable
- runner can emit monotonic partial text
- stop button cancels the visible stream and native/decode work safely
- no duplicate partial append
- final text equals the last displayed partial after normalization
- DB placeholder lifecycle is already stable from S2

Rollback conditions:

- streaming gate off returns to S3 non-streaming final insert
- stop request leaves a clearly stopped assistant state
- partial parser failure keeps final non-streaming result as fallback only if
  `fallback_used=false` and NPU decode succeeded

Test items:

- partial callback order
- duplicate partial suppression
- stop before first token
- stop after first token
- timeout during stream
- final DB row update matches final text
- `streaming=true`, Markdown mode recorded accurately

Blockers:

- lower-level NPU route cannot provide partials
- held engine/conversation lifecycle cannot be cancelled safely
- stop behavior risks stale native state or next-run failure
- streaming path requires TTS to be enabled

## Phase S5: TTS Connection

Goal: speak successful final NPU assistant output after DB, Markdown, and
streaming behavior are stable.

S5 TTS candidate gate ON smoke check:

- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true`;
- used `customBuildExperimentDebug`;
- confirmed install/start succeeded;
- confirmed ChatScreen displayed `NPU STANDARD ROUTE S1`;
- confirmed ChatScreen displayed `こんにちは。`;
- no actual speech occurred because this is still the candidate-only phase and
  `ttsController.speak(...)` is not connected;
- no crash occurred;
- rollback restored `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false`;
- reinstalled after rollback;
- `git status -sb` was clean after rollback.

This confirms the S5 candidate gate can be enabled without breaking the current
standard UI NPU path. It does not validate actual TTS playback. TTS playback and
`Backend.NPU` persistence remain unconnected.

S5 TTS speak runtime check:

- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S2_DB=true`;
- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true`;
- installed and started `customBuildExperimentDebug`;
- confirmed the standard ChatScreen displayed user `こんにちは` and assistant
  `こんにちは。`;
- confirmed the diagnostic block displayed `NPU STANDARD ROUTE S1`;
- confirmed the visible S1 text `こんにちは。`;
- confirmed TTS speech by listening on the device;
- `logcat` grep for `NPU_S5_TTS` returned no lines, so trace visibility needs
  improvement;
- no crash occurred;
- rollback restored both S2 and S5 gates to false;
- reinstalled after rollback;
- `git status -sb` was clean after rollback.

This confirms the gated S5 `ttsController.speak(...)` path works through the
custom build experiment route. The S1 through S5 gated roadmap is complete for
the current standard UI promotion track. Remaining tasks are S5 trace
visibility, S4-A long-text chunk confirmation, and permanent gate-on policy.
`Backend.NPU` persistence remains disconnected.

Change target files:

- `ChatScreen.kt`
- `AndroidTtsController.kt`
- `SpeechTextBuilder.kt`
- `TtsMemoryReleasePolicy.kt`
- TTS state/cooldown tests

Gate conditions:

- S4 is stable or explicitly skipped for non-streaming TTS
- speak only sanitized final assistant output
- no TTS for failure, fallback, timeout, fresh crash, or empty output
- cooldown and stop ownership follow existing ChatScreen behavior
- Markdown-to-speech stripping is deterministic

Rollback conditions:

- TTS gate off leaves DB/Markdown/streaming behavior unchanged
- TTS initialization failure does not affect NPU success classification
- stop button stops TTS without changing stored assistant text

Test items:

- successful natural Japanese result speaks once
- failure/fallback/timeout/fresh crash do not speak
- Markdown-stripped speech text matches expected plain text
- cooldown suppresses duplicate speech
- stop clears speaking state and does not cancel completed NPU result

Blockers:

- TTS starts from partial text before final safety classification
- TTS failure is coupled to inference success
- stop ownership cannot distinguish NPU generation from speech playback

## Promotion Exit Criteria

Standard ChatScreen integration should not be enabled broadly until:

- S1 through S5 have explicit gates and rollback behavior;
- each phase has tests for success and failure paths;
- NPU evidence, fallback, timeout, fresh crash, quality, and side-effect flags
  are visible in diagnostics;
- at least one multi-run standard route stability check passes after S1;
- DB/Markdown/streaming/TTS are each enabled only by their own phase gate.
