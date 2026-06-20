# NPU Beta Long Generation Test Plan

## Purpose

`NPU Beta Long Generation Test` is a DEV-only diagnostic for checking whether
the NPU standard route remains stable when `max_output_tokens` is increased
beyond the short one-shot and stability-test defaults.

The test is observation-only. It does not change the NPU route, fallback policy,
persistent engine behavior, custom JNI behavior, UI/TTS/DB delivery, Markdown
delivery, or native token streaming.

DEV diagnostics group: Primary.

## Initial Prompt

```text
こんにちは。日本語で、ローカルAIアシスタントとして自己紹介し、できることを具体例つきで説明してください。
```

## Token Plan

Initial DEV UI plan:

- `32`
- `128`
- `512`

The code keeps `1024` as a supported future value, but the initial UI path does
not run it. The 1024-token case should move into Advanced only after physical
device evidence shows that 32/128/512 are stable.

## Summary Format

Top-level keys:

- `test_name=NPU Beta Long Generation Test`
- `prompt`
- `token_plan=32,128,512`
- `completed_cases`
- `success_count`
- `failed_count`
- `fallback_used_count`
- `timeout_count`
- `fresh_crash_count`
- `run_decode_reached_count`
- `average_tokens_per_second`
- `first_failure_reason`
- `backend_evidence_summary`
- `quality_classification_summary`

Per-case keys:

- `requested_max_output_tokens`
- `effective_max_output_tokens`
- `status`
- `reason`
- `fallback_used`
- `timeout`
- `fresh_crash`
- `run_decode_reached`
- `total_ms`
- `decode_ms`
- `output_tokens`
- `token_count_mode`
- `tokens_per_second`
- `raw_output`
- `sanitized_output`
- `quality_classification`
- `backend_evidence`
- `finish_reason`
- `stop_reason`
- `eos_detected`
- `tokenizer_output_tokens`

Finish and EOS fields must remain `unavailable` when not exposed by the current
NPU route. The test must not infer a successful stop condition from output text.

## Copy Actions

Primary DEV diagnostics now expose two one-tap copy actions next to the Long
Generation runner:

- `Copy Long Summary`
- `Copy Long Full Dump`

`Copy Long Summary` copies only the `[DEV診断: NPU Beta Long Generation summary]`
block. It is intended for quick artifact review and includes aggregate keys such
as `completed_cases`, `success_count`, `fallback_used_count`, `timeout_count`,
`fresh_crash_count`, `run_decode_reached_count`,
`average_tokens_per_second`, backend evidence, selected/requested/effective
backend, route family, and start/finish timestamps.

`Copy Long Full Dump` copies the same summary plus every
`[DEV診断: NPU Beta Long Generation case]` block. Each case includes
`requested_max_output_tokens`, `effective_max_output_tokens`, status/reason,
fallback/timeout/fresh-crash/decode flags, timing, token count mode,
tokens/sec, raw output, sanitized output, quality classification, backend
evidence, and finish/stop/EOS/tokenizer fields. Unexposed values remain
`unavailable`; the copy path does not infer stop or EOS state.

Both copy actions are formatting/UI affordances only. They do not change the
NPU route, token plan, `max_output_tokens`, fallback policy, telemetry meaning,
or output quality classifier.

## Pass Conditions

Initial pass candidate:

- 32/128/512 all complete
- `fallback_used=false` for every case
- `timeout=false` for every case
- `fresh_crash=false` for every case
- `run_decode_reached=true` for every case
- backend evidence contains `QNN_HTP` or `FastRPC`
- `quality_classification` is not `empty_output`, `mojibake`, or a fatal quality
  class

## Hold Conditions

Hold or investigate if any of these appear:

- 512-token case times out
- 512-token case causes `fresh_crash=true`
- `fallback_used=true`
- `run_decode_reached=false`
- output is extremely short for 128/512
- `quality_classification` is unnatural or unsafe
- backend evidence is missing

## Physical Device Procedure

1. Select `NPU Beta` in Settings.
2. Open DEV diagnostics.
3. Run `NPU Beta長文生成テスト開始`.
4. Save the summary artifact from the DEV diagnostics area.

Suggested artifact name:

```text
artifacts/device_runs/npu_beta_long_generation_YYYYMMDD.txt
```

`not run: requires physical NPU device`

## Follow-up

Step 4 should reorganize the DEV diagnostics screen into Primary and Advanced
groups:

- Primary: NPU diagnostic keys, compact copy, NPU Beta Stability Test, NPU Beta
  Long Generation Test.
- Advanced: full dump, memory recovery, legacy QAIRT244 paths, persistent engine,
  custom JNI, GPU root-cause buttons, and future 1024-token long generation.

Step 4 is implemented as a UI-only grouping. The Long Generation runner and
summary format are unchanged by that layout step.
