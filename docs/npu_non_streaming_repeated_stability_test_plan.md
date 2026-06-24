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
5. Confirm:
   - `streaming=false`
   - `pseudo_streaming=false`
   - `run_count_completed=10`
   - `run_decode_reached_count`
   - `backend_evidence_summary`
   - `fallback_used_count=0`
   - `timeout_count=0`
   - `fresh_crash_count=0`
   - `restart_app_recommended=false`

`not run: requires physical NPU device`
