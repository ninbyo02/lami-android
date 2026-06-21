# NPU Persistent Engine Multi-turn Test Plan

## Purpose

`NPU Persistent Engine Multi-turn Test` is a DEV diagnostic for separating
Engine create/destroy pressure from normal multi-turn generation stability.

The test is a DEV-only feasibility probe for keeping one Engine or adapter
alive across multiple short generations. It does not change the normal chat
route, fallback policy, persistent-engine production behavior, custom JNI, DB,
TTS, Markdown, or pseudo streaming.

## Why This Test Exists

`NPU Beta Stability Test` in recreate mode is closer to an Engine recreate
stress test. Current physical-device evidence shows reproducible
`engine_create_failed` near run 7 after six successful decodes, even with
500ms or 2000ms waits.

That pattern suggests short-interval `EngineFactory::CreateDefault` repetition
may hit LiteRT/QNN/HTP resource constraints or delayed release behavior. Normal
chat should not depend on repeatedly creating and destroying the NPU Engine if a
persistent holder/session design is viable.

`NPU Persistent Engine Multi-turn Test` is currently closer to a blocked-state
probe than an executable generation test. The intended question remains: can
one Engine or standard-route adapter be kept alive and then generate ten times?
At the moment, the required persistent standard-route adapter/native decode
entrypoint is not exposed.

## Current Physical-device Finding

The first physical-device run selected the official LiteRT-LM `Session`
`generateContent` API and failed on run 1:

- `selected_api_mode=session`
- `session_api_used=true`
- `first_failure_stage=decode`
- `first_failure_reason=logits_output_not_supported_on_npu_backend:LiteRtLmJniException`
- `first_failure_exception_message=Failed to generate content: UNIMPLEMENTED: Decode for logits output not implemented for backend: LiteRT NPU Compiled Model`
- `logits_output_required=true`
- `logits_output_backend_supported=false`

The existing NPU standard route / S1 repeated runner succeeds through the
native adapter path with `QNN_HTP_V79_FastRPC_native_diag`, so the Persistent
diagnostic must not prefer the official session API on NPU. The default now
blocks session mode before generation and reports the missing persistent
standard-route adapter as explicit follow-up work.

## Current Scope

- UI label: `NPU永続Engine状態確認`
- UI section title: `NPU Persistent Engine Multi-turn Probe (blocked)`
- DEV diagnostics group: Primary
- Summary test name: `test_name=NPU Persistent Engine Multi-turn Test`
- UI action key: `ui_action_label=NPU Persistent Probe状態確認`
- Expected current status: `persistent_probe_status=blocked`
- Expected current run count: `run_count_completed=0`
- Prompt: `こんにちは`
- Run count: `10`
- Wait: `500ms`
- Engine/API: standard-route adapter if persistent access is exposed
- Per-run path: standard-route adapter preferred; session generate content is
  blocked by default on NPU because it requires unsupported logits output
- Holder/session identity: `not_exposed` unless the API exposes it
- Engine reuse observation: `engine_reuse_observed=unavailable` unless a real
  signal exists

The test stops on the first fatal failure. If `engine_create_failed` appears,
the summary must report `restart_app_recommended=true` and
`guard_recommendation=disable_npu_until_app_restart_or_cooldown`.

When session API is the only exposed persistent official API, the test should
stop before generation with:

- `ui_execution_expected=false`
- `ui_blocked_expected=true`
- `ui_blocked_explanation=session_api_blocked_and_standard_route_adapter_not_exposed`
- `user_next_action=copy_persistent_full_dump_or_investigate_standard_route_adapter`
- `persistent_probe_status=blocked`
- `run_count_completed=0`
- `blocked_reason=session_api_logits_output_not_supported_on_npu_backend`
- `session_api_blocked_for_npu=true`
- `session_api_used=false`
- `standard_route_adapter_available=false`
- `standard_route_adapter_reason=needs_native_adapter_work`
- `persistent_standard_route_available=false`
- `persistent_standard_route_reason=needs_native_adapter_work`
- `restart_app_recommended=false`

## Summary Keys

The summary should include:

- `test_name`
- `ui_action_label`
- `ui_execution_expected`
- `ui_blocked_expected`
- `ui_blocked_explanation`
- `user_next_action`
- `persistent_engine_requested`
- `persistent_engine_available`
- `engine_reuse_observed`
- `engine_holder_id` / `holder_identity`
- `provider_instance_id`
- `adapter_instance_id`
- `session_id`
- `run_count_requested`
- `run_count_completed`
- `success_count`
- `failure_count`
- `success_rate`
- `fallback_used_count` / `fallback_rate`
- `timeout_count` / `timeout_rate`
- `fresh_crash_count` / `fresh_crash_rate`
- `engine_create_failed_count`
- `run_decode_reached_count` / `run_decode_reached_rate`
- `average_total_ms`
- `average_decode_ms`
- `average_tokens_per_second`
- `backend_evidence_summary`
- `quality_classification_summary`
- `first_failure_run_index`
- `first_failure_reason`
- `first_failure_native_diag_tail`
- `guard_recommendation`
- `restart_app_recommended`
- `persistent_engine_api_mode`
- `attempted_api_modes`
- `selected_api_mode`
- `api_mode_selection_reason`
- `session_api_available`
- `session_api_used`
- `session_api_blocked_for_npu`
- `session_api_block_reason`
- `standard_route_adapter_available`
- `standard_route_adapter_used`
- `standard_route_adapter_reason`
- `logits_output_required`
- `logits_output_backend_supported`
- `logits_failure_detected`
- `persistent_standard_route_available`
- `persistent_standard_route_reason`

Unavailable or non-exposed fields must be written as `unavailable` or
`not_exposed`. Do not infer `engine_reuse_observed=true`.

## Per-run Details

Each run should show:

- `run_index`
- `prompt`
- `status`
- `reason`
- `run_decode_reached`
- `fallback_used`
- `timeout`
- `fresh_crash`
- `total_ms`
- `decode_ms`
- `tokens_per_second`
- `raw_output`
- `sanitized_output`
- `quality_classification`
- `backend_evidence`
- `holder_identity`
- `provider_instance_id`
- `adapter_instance_id`
- `session_id`
- `native_stage_history`
- `native_or_engine_diag_tail`

## Pass Conditions

Pass candidate:

- `run_count_completed=10`
- `success_count=10`
- `failure_count=0`
- `fallback_used_count=0`
- `timeout_count=0`
- `fresh_crash_count=0`
- `engine_create_failed_count=0`
- `run_decode_reached_count=10`
- backend evidence contains `QNN_HTP` or `FastRPC`
- quality classification is `natural_japanese` or an explicitly reviewed
  candidate-pass category

## Hold Conditions

Hold or investigate if any of these appear:

- `engine_create_failed_count > 0`
- `failure_count > 0`
- `fallback_used_count > 0`
- `timeout_count > 0`
- `fresh_crash_count > 0`
- `run_decode_reached_count < run_count_completed`
- repeated `template_artifact`, `unknown`, or output quality failures
- missing NPU/QNN/HTP/FastRPC backend evidence

## Decision After Collection

If Persistent Multi-turn passes ten runs while recreate mode fails near run 7,
the next design review should consider moving the NPU normal chat route toward
a persistent Engine holder/session strategy.

If session API is blocked with logits unsupported, the next step is not to
retry session mode. Instead, expose or add a persistent standard-route
adapter/native decode API that can reuse the same successful path as NPU Beta
Stability Test and normal NPU standard route.

The current exposure review is documented in
`docs/npu_standard_route_adapter_persistent_exposure_review.md`. That review
concludes that the successful standard-route path is available today only as a
one-shot native adapter call (`nativeRunEditablePrompt` through
`litertlm_jni`). Kotlin can observe native stage diagnostics and backend
evidence, but it cannot hold or reuse the underlying Engine. True persistent
multi-turn execution therefore needs a DEV-only native/JNI holder API before it
can be connected to this probe.

The proposed holder API contract is documented in
`docs/npu_dev_only_persistent_holder_api_design.md`. The default production
wrapper still reports `holder_api_available=false`, but debug builds now expose
DEV-only holder lifecycle/run-gate probes. These probes must continue to report
`persistent_multi_turn_possible=false` until a real persistent holder proves
reuse safely.

As of the DEV-only native create/close pass, Kotlin can call four holder-shaped
JNI functions and receive a native key-value diagnostic summary:

- `nativeCreateStandardRouteAdapterHolder(...)`
- `nativeRunStandardRouteAdapterHolderOnce(...)`
- `nativeCloseStandardRouteAdapterHolder(...)`
- `nativeGetStandardRouteAdapterHolderDiagnostics(...)`

Create/close physical-device coverage passed for
`NPU Persistent Holder Create Close Probe`: create succeeded, close succeeded,
double close was safe, fatal latch stayed false, and Engine/ModelAssets/
EngineSettings/decode/QNN flags stayed false.

This started as `NPU Persistent Holder Create Close Probe` coverage. Create/close
manage one app JNI holder lifecycle record, but create currently stops at
`holder_native_create_level=app_jni_holder_lifecycle_only_pre_engine_create`.
It does not call `EngineFactory::CreateDefault`, `ModelAssets::Create`,
`EngineSettings::CreateDefault`, QNN, LiteRT NPU decode, generate, or normal
NPU chat routing.

The DEV diagnostics UI now exposes this check as
`NPU Persistent Holder Create/Close Probe`, near the blocked persistent Engine
probe. `Run Holder Create/Close Probe` performs create, diagnostics, close,
diagnostics, and a second close safety check. It must not call `runHolderOnce`
or any decode/generate path. The copy actions are:

- `Copy Holder Create/Close Summary`
- `Copy Holder Create/Close Full Dump`

Use the UI result for physical-device triage. Pass requires
`holder_create_called=true`, `holder_close_called=true`,
`npu_decode_called=false`, `generate_called=false`, `qnn_decode_called=false`,
and `holder_fatal_latch=false`. Hold if the fatal latch is set, create/close
fails, or any decode/generate flag becomes true.

Run Once physical-device coverage passed for
`NPU Persistent Holder Run Once Probe`: create succeeded, run once succeeded,
decode was reached, backend evidence reported QNN HTP / FastRPC, no fallback,
timeout, or fresh crash was observed, close succeeded, and the fatal latch
stayed false.

Two-Turn physical-device coverage passed for
`NPU Persistent Holder Two-Turn Probe`: both turns succeeded, both decodes were
reached, backend evidence reported `QNN_HTP_V79_FastRPC_native_diag:2`, quality
classification reported `natural_japanese:2`, fallback/timeout/fresh-crash
counts were zero, and the fatal latch stayed false.

The next DEV-only implementation unit is now
`NPU Persistent Holder Five-Turn Probe`. It performs exactly one create, five
holder-gated decode attempts, and one close:

1. turn 1 prompt: `こんにちは`
2. turn 2 prompt: `あなたは誰ですか`
3. turn 3 prompt: `Pythonとは何ですか`
4. turn 4 prompt: `Androidについて一言で説明して`
5. turn 5 prompt: `ありがとう`

This is still not the 10-turn persistent probe. The native holder gate records
that the same app JNI holder record was open for the calls, while each decode
still uses the existing one-shot standard route adapter path. Normal chat route
connection remains blocked, and `engine_reuse_observed=unavailable` must not be
changed.

Run Once pass requires `holder_create_succeeded=true`,
`run_once_called=true`, `run_once_succeeded=true`,
`run_decode_reached=true`, `fallback_used=false`, `timeout=false`,
`fresh_crash=false`, `holder_close_succeeded=true`,
`holder_fatal_latch=false`, and QNN HTP / FastRPC backend evidence. Hold if
create fails, run once is unsupported or fails, fallback is used, timeout or
fresh crash is observed, close fails, or the fatal latch is set.

Two-Turn pass requires `holder_create_succeeded=true`,
`turn1_run_decode_reached=true`, `turn2_run_decode_reached=true`,
backend evidence containing QNN HTP / FastRPC, `fallback_used_count=0`,
`timeout_count=0`, `fresh_crash_count=0`, `holder_close_succeeded=true`, and
`holder_fatal_latch=false`.

Two-Turn hold conditions are turn 1 failure, turn 2 failure, any fallback, any
timeout, any fresh crash, holder close failure, or `holder_fatal_latch=true`.

Five-Turn pass requires `holder_create_succeeded=true`,
`run_decode_reached_count=5`, backend evidence containing QNN HTP / FastRPC,
generally natural Japanese quality classification summary,
`fallback_used_count=0`, `timeout_count=0`, `fresh_crash_count=0`,
`holder_close_succeeded=true`, and `holder_fatal_latch=false`.

Five-Turn hold conditions are any turn failure, fallback, timeout, fresh crash,
holder close failure, or `holder_fatal_latch=true`. Do not advance directly to
conversation stability or normal chat persistentization from this result; the
next step is the fixed Ten-Turn Probe.

If Persistent Multi-turn also fails through a standard-route adapter, treat the
issue as lower-level NPU native executor / QNN delegate / prompt path
instability and continue native-focused investigation before changing normal
chat behavior.

## Copy Actions

The DEV screen includes:

- `Copy Persistent Summary`: copies only the persistent summary block, including
  API mode selection and block diagnostics.
- `Copy Persistent Full Dump`: copies the summary plus per-run/details blocks
  and native/API diagnostics.

If no generation ran because session API was blocked, the copied artifact still
contains `persistent_probe_status=blocked`, `blocked_reason`, and
`records=empty`. `run_count_completed=0` is expected in this state and is not a
UI execution failure.

## Physical-device Procedure

1. Select `NPU Beta`.
2. Open DEV diagnostics.
3. Press `NPU永続Engine状態確認`.
4. Confirm that the current expected result is
   `persistent_probe_status=blocked` and `run_count_completed=0`.
5. Use `Copy Persistent Full Dump` to share the blocked reason.
6. Run `NPU Beta Stability Test` in recreate mode for comparison.
7. Compare `engine_create_failed_count`, `run_decode_reached_count`,
   `quality_classification_summary`, and backend evidence.

`not run: requires physical NPU device`
