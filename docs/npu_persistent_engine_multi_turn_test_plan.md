# NPU Persistent Engine Multi-turn Test Plan

## Purpose

`NPU Persistent Engine Multi-turn Test` is a DEV diagnostic for separating
Engine create/destroy pressure from normal multi-turn generation stability.

The test initializes one official LiteRT-LM `Engine` with NPU backend and then
runs multiple short generations while keeping that Engine alive. It does not
change the normal chat route, fallback policy, persistent-engine production
behavior, custom JNI, DB, TTS, Markdown, or pseudo streaming.

## Why This Test Exists

`NPU Beta Stability Test` in recreate mode is closer to an Engine recreate
stress test. Current physical-device evidence shows reproducible
`engine_create_failed` near run 7 after six successful decodes, even with
500ms or 2000ms waits.

That pattern suggests short-interval `EngineFactory::CreateDefault` repetition
may hit LiteRT/QNN/HTP resource constraints or delayed release behavior. Normal
chat should not depend on repeatedly creating and destroying the NPU Engine if a
persistent holder/session design is viable.

`NPU Persistent Engine Multi-turn Test` is closer to the normal-chat stability
question: can one Engine be initialized once and then generate ten times?

## Current Scope

- UI label: `NPU永続Engine複数会話テスト`
- DEV diagnostics group: Primary
- Summary test name: `test_name=NPU Persistent Engine Multi-turn Test`
- Prompt: `こんにちは`
- Run count: `10`
- Wait: `500ms`
- Engine: one official LiteRT-LM `Engine` instance where exposed
- Per-run path: session generate content when available
- Holder/session identity: `not_exposed` unless the API exposes it
- Engine reuse observation: `engine_reuse_observed=unavailable` unless a real
  signal exists

The test stops on the first fatal failure. If `engine_create_failed` appears,
the summary must report `restart_app_recommended=true` and
`guard_recommendation=disable_npu_until_app_restart_or_cooldown`.

## Summary Keys

The summary should include:

- `test_name`
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

If Persistent Multi-turn also fails, treat the issue as lower-level NPU native
executor / QNN delegate / prompt path instability and continue native-focused
investigation before changing normal chat behavior.

## Physical-device Procedure

1. Select `NPU Beta`.
2. Open DEV diagnostics.
3. Run `NPU Persistent Engine Multi-turn Test` for ten turns.
4. Run `NPU Beta Stability Test` in recreate mode for comparison.
5. Compare `engine_create_failed_count`, `run_decode_reached_count`,
   `quality_classification_summary`, and backend evidence.

`not run: requires physical NPU device`
