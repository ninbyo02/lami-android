# NPU Non-Streaming Repeated Stability Test Plan

## Purpose

`NPU Non-Streaming Repeated Stability Test` measures repeatability of the
existing one-shot NPU decode path before any true Engine persistent reuse work is
re-enabled.

This test is not true Engine reuse. It does not prove persistent Engine reuse,
does not create a held Engine, and does not connect to the normal chat route.

## Scope

The runner uses the DEV-only one-turn conversation entrypoint for 10 fixed
prompts:

- `こんにちは`
- `あなたは誰ですか`
- `Pythonとは何ですか`
- `Androidについて一言で説明して`
- `日本語で短く答えてください`
- `今日の挨拶をしてください`
- `ありがとう`
- `またね`
- `1+1は？`
- `短い俳句を作って`

The first version is intentionally fixed at 10 runs. Future 30/100 variants
should be added only after reviewing physical-device evidence from the 10-run
artifact.

## Physical-device Results: Reproduced Run 7 Engine Create Failure

Two physical-device runs have now reproduced the same failure shape. Both
stopped on the 7th one-shot decode after 6 successful NPU runs:

- `status=stopped`
- `reason=adapter_failure:LiteRtLmJniException`
- `streaming=false`
- `pseudo_streaming=false`
- `tts=false`
- `db=false`
- `markdown=false`
- `fallback_allowed=false`
- `run_count_requested=10`
- `run_count_completed=7`
- `success_count=6`
- `failure_count=1`
- `success_rate=0.86`
- `run_decode_reached_count=6`
- `run_decode_reached_rate=0.86`
- `fallback_used_count=0`
- `timeout_count=0`
- `fresh_crash_count=0`
- `backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:7`
- `quality_classification_summary=natural_japanese:6,unknown:1`
- `first_failure_run_index=7`
- `first_failure_stage=native_call`
- `first_failure_reason=adapter_failure:LiteRtLmJniException`
- `first_failure_exception_class=LiteRtLmJniException`
- `engine_create_failure_detected=true`
- `suspected_failure_area=engine_create`
- `repeated_recreate_suspected=true`
- `streaming_ruled_out=true`
- `pseudo_streaming_ruled_out=true`
- `ui_side_effects_ruled_out=true`
- `true_engine_reuse_investigation_recommended=true`
- `true_engine_probe_blocked_for_startup_safety=true`
- `guard_recommendation=investigate_true_engine_reuse_with_staged_probe`
- `restart_app_recommended=false`
- `true_engine_probe_status=disabled_or_blocked`
- `true_engine_persistent_reuse=false`
- `engine_reuse_observed=unavailable`

The first failure native diagnostic tail included:

- `before ModelAssets::Create`
- `before EngineSettings::CreateDefault`
- `before EngineFactory::CreateDefault`
- `engine-create-failed: INTERNAL`
- `runtime/executor/llm_litert_npu_compiled_model_executor.cc:2725`
- `external/litert/litert/cc/litert_compiled_model.h:1140`

Interpretation:

- `first_failure_run_index=7` is now reproducible across two device checks,
  not a one-off artifact from the first run.
- Streaming, pseudo streaming, UI coroutine updates, TTS, DB writes, and
  markdown rendering are unlikely to be the primary cause because this test
  excludes them and still reproduces the failure.
- App-side timeout and fallback are unlikely to be the primary cause because
  `timeout_count=0`, `fallback_used_count=0`, and `fresh_crash_count=0`.
- The leading suspect is repeated short-interval one-shot NPU recreate:
  each run creates `ModelAssets`, `EngineSettings`, and reaches
  `EngineFactory::CreateDefault`, then the 7th run returns an INTERNAL error.
- The repeated recreate suspicion is stronger because the second run ruled out
  the same non-native side-effect set while `fallback_used_count=0`,
  `timeout_count=0`, and `fresh_crash_count=0` remained unchanged.
- This is the decision point for staged true Engine probing, but the prior
  `true_engine_create_close_only` isolated stack crashed on cold start. The next
  true Engine work must restart with staged, button-only probe phases rather
  than re-enabling create/close directly.
- `trueEngineNpuProbeDebug` native execution remains disabled and blocked while
  this result is being used for design decisions.

## Next True Engine Probe Entry

The current investigation stays staged and adds only a button-only `entrypoint_only` call in `trueEngineNpuProbeDebug`. It still must not add any new `EngineFactory::CreateDefault` call.
Planned phases:

1. Phase 1: `trueEngineNpuProbeDebug` startup stability check only. Execution
   stays disabled and blocked.
2. Phase 2: button-only `entrypoint_only`. Confirm native entrypoint reach only.
   Do not call `ModelAssets::Create`, `EngineSettings::CreateDefault`, or
   `EngineFactory::CreateDefault`. Startup native calls remain forbidden.
3. Phase 3: `model_assets_only`. Stop after `ModelAssets::Create`; do not call
   `EngineSettings::CreateDefault` or `EngineFactory::CreateDefault`.
4. Phase 4: `engine_settings_only`. Stop after
   `EngineSettings::CreateDefault`; do not call `EngineFactory::CreateDefault`.
5. Phase 5: `before_engine_create`. Reach the point immediately before
   `EngineFactory::CreateDefault`; do not call it.
6. Phase 6: `engine_create_only`. Call `EngineFactory::CreateDefault` exactly
   once, with no Session, decode, prefill, generate, or normal route delivery,
   then verify close.
7. Phase 7: held Engine run once. Create count must be 1, then one
   Session/decode, then close. This is future work, not part of the current
   cleanup.

## Safety Contract

The test records:

- `streaming=false`
- `pseudo_streaming=false`
- `tts=false`
- `db=false`
- `markdown=false`
- `fallback_allowed=false`
- `true_engine_persistent_reuse=false`
- `engine_reuse_observed=unavailable`

The runner must not:

- enable `trueEngineNpuProbeDebug` native execution
- revive `true_engine_create_close_only`
- call true Engine create/close
- create a held Engine or Session
- add prefill/decode/generate to the true Engine path
- change the normal NPU chat route
- change fallback policy
- add pseudo streaming
- connect TTS, DB, or markdown

## Diagnostics

Summary keys:

- `test_name=NPU Non-Streaming Repeated Stability Test`
- `route_type=dev_only_one_turn_conversation_non_streaming_repeat`
- `streaming=false`
- `pseudo_streaming=false`
- `tts=false`
- `db=false`
- `markdown=false`
- `fallback_allowed=false`
- `run_count_requested`
- `run_count_completed`
- `success_count`
- `failure_count`
- `success_rate`
- `run_decode_reached_count`
- `run_decode_reached_rate`
- `backend_evidence_summary`
- `quality_classification_summary`
- `fallback_used_count`
- `fallback_rate`
- `timeout_count`
- `timeout_rate`
- `fresh_crash_count`
- `fresh_crash_rate`
- `average_total_ms`
- `average_decode_ms`
- `first_failure_run_index`
- `first_failure_stage`
- `first_failure_reason`
- `first_failure_exception_class`
- `first_failure_native_diag_tail`
- `engine_create_failure_detected`
- `suspected_failure_area`
- `repeated_recreate_suspected`
- `streaming_ruled_out`
- `pseudo_streaming_ruled_out`
- `ui_side_effects_ruled_out`
- `true_engine_reuse_investigation_recommended`
- `true_engine_probe_blocked_for_startup_safety`
- `restart_app_recommended`
- `guard_recommendation`
- `true_engine_probe_status=disabled_or_blocked`
- `true_engine_persistent_reuse=false`
- `engine_reuse_observed=unavailable`

Per-run detail keys:

- `run_index`
- `prompt`
- `status`
- `reason`
- `run_decode_reached`
- `backend_evidence`
- `quality_classification`
- `fallback_used`
- `timeout`
- `fresh_crash`
- `total_ms`
- `decode_ms`
- `raw_output_first_200_chars`
- `sanitized_output`
- `native_stage`
- `native_stage_history`
- `native_error_stage`
- `native_error_class`

## DEV UI

Primary DEV diagnostics includes:

- `Run Non-Streaming Repeat Test`
- `Copy Non-Streaming Repeat Summary`
- `Copy Non-Streaming Repeat Full Dump`

The Run button lazily creates the debug runner. Showing DEV diagnostics or
copying an idle summary must not load `Qairt244ShortMultitokenSmoke` or
`litertlm_jni`.

## Comparison

Compare this artifact with:

- `NPU Beta Stability Test` recreate/reuse summaries
- Persistent Holder Run Once/Two/Five/Ten Turn summaries
- true Engine Holder Create/Close blocked summaries

Use this test to determine whether repeated one-shot native cleanup is stable
before any true Engine create/close or reuse work is resumed.

## Physical-device Procedure

1. `./update.sh update`
2. Open DEV diagnostics.
3. Run `Run Non-Streaming Repeat Test`.
4. Copy `Copy Non-Streaming Repeat Full Dump`.
5. Confirm the current reproduced failure shape or any future change from it:
   - `streaming=false`
   - `pseudo_streaming=false`
   - `run_count_completed=7` for the reproduced artifact
   - `success_count=6` and `failure_count=1` for the reproduced artifact
   - `first_failure_run_index=7`
   - `suspected_failure_area=engine_create`
   - `engine_create_failure_detected=true`
   - `repeated_recreate_suspected=true`
   - `backend_evidence_summary`
   - `fallback_used_count=0`
   - `timeout_count=0`
   - `fresh_crash_count=0`
   - `restart_app_recommended=false`

`not run: requires physical NPU device`

## Current True Engine Probe Follow-up

The repeated run-7 result now feeds the first staged true Engine follow-up:
`trueEngineNpuProbeDebug` enables only button-only `entrypoint_only` with
`TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED=true` and keeps
`TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED=false`. This confirms native
entrypoint reach and immediate return only. It must continue to report
`model_assets_create_reached=false`, `engine_settings_create_reached=false`,
`engine_create_reached=false`, `session_create_count=0`, `decode_count=0`,
and `generate_count=0`.

`standardDebug` remains blocked and receives no isolated native payload.
`true_engine_create_close_only`, `engine_create_only`, and held Engine run once
remain future phases. If the entrypoint artifact succeeds on a physical NPU
device, the next minimum step is button-only `model_assets_only`.
