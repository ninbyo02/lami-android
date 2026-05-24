# QAIRT244 NPU Gemma Turn-Stop Quality Compare

Date: 2026-05-25

Scope: hidden experimental display-quality tuning after the ChatScreen DEV-only NPU route success. This pass does not change release or standard behavior, does not touch `app/src/main/jniLibs`, does not persist `selectedPath=npu`, and does not connect DB, TTS, Markdown, or streaming.

## Baseline

Known route status before this comparison:

- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- sanitized output removes `<end_of_turn>` and `<start_of_turn>` display artifacts

The remaining issue is display quality, not NPU routing: decode may continue after `<end_of_turn>`, and raw output may include repeated completions, multilingual drift, prompt echo, turn artifacts, or duplicate Japanese completion text.

## Native Stop API Finding

Static inspection result: `native stop not exposed` for this lower-level Android route.

LiteRT-LM runtime has internal metadata-derived stop token handling, including `SessionConfig` stop token ids and a `StopTokenDetector`, but the Android JNI / `DecodeConfig` surface used by this qairt244 lower-level route exposes only `max_output_tokens` for the editable-prompt decode path. `DecodeConfig` exposes `SetConstraint()` and `SetMaxOutputTokens()`, but no per-request stop sequence, stop token, EOS, or `<end_of_turn>` setter. The qairt244 editable prompt native entrypoint creates a default `SessionConfig`, sets `DecodeConfig.SetMaxOutputTokens(max_output_tokens)`, and calls `RunDecode(decode_config)`.

Sampling controls exist in the public `SamplerConfig` bridge for topK, topP, temperature, and seed, but the qairt244 lower-level native smoke / editable prompt entrypoint does not accept a sampler config. No repetition penalty API was found in the inspected sampler parameters.

Conclusion: do not implement a native stop-sequence change in this pass. Treat `stop_sequence_end_of_turn` as `not_run/native_stop_not_exposed`; compare sanitizer-only against lower token caps instead.

## Runner

Script:

```text
scripts/run_qairt244_npu_turn_stop_quality_compare.sh
```

Artifact root:

```text
artifacts/qairt244_npu_turn_stop_quality_compare/<timestamp>/
```

Prompts, one run each per executable case:

- `こんにちは`
- `はじめまして`
- `こんばんは`

Cases:

- `sanitizer_only`: hidden standard run, requested `max_output_tokens=128`
- `lower_max_tokens_64_sanitizer`: hidden standard run, requested `max_output_tokens=64`
- `lower_max_tokens_32_sanitizer`: hidden standard run, requested `max_output_tokens=32`
- `stop_sequence_end_of_turn`: recorded as `not_run/native_stop_not_exposed`
- repetition suppression: not run, because no qairt244 lower-level API is exposed

Per-run timeout remains 30 seconds by default. Requested `max_output_tokens` never exceeds 128.

## Quality Classification

The runner classifies each sanitized output as one of:

- `natural_japanese`
- `template_artifact`
- `repeated_completion`
- `multilingual_drift`
- `prompt_echo`
- `empty_after_sanitize`
- `timeout`
- `crash`

Recorded checks include template token residue, prompt echo, repeated text, multilingual drift, sanitized length, decode elapsed time, NPU evidence, fallback status, timeout status, and fresh crash status.

## Safety Invariants

The comparison must keep these true:

- `fallback_used=false`
- `fresh_crash=false`
- `timeout=false`
- QNN / HTP / FastRPC / NPU evidence present for executed cases
- no DB, TTS, Markdown, or streaming route connection
- `selectedPath=npu` not saved
- no generic or QCS8275 model selected for NPU
- no large `.so`, `.litertlm`, `.apk`, `.zip`, `.tar`, or `.gz` binary staged

Rollback triggers are fresh crash, timeout, missing NPU evidence, empty output after sanitize, sanitizer removing meaning, worse repetition or multilingual drift, DB/TTS/Markdown/streaming ingress, or persisted `selectedPath=npu`.

## Adoption Recommendation

Current minimum safe baseline candidate is sanitizer-only display cleanup. Because native turn-stop is not exposed, do not ship or stage a stop-sequence native patch from this pass.

The next decision should be based on the artifact comparison table:

1. Adopt `sanitizer_only` as the minimum safe display baseline if all three prompts stay `natural_japanese`, `fallback_used=false`, `fresh_crash=false`, `timeout=false`, and `selected_path_npu_saved=false`.
2. Do not adopt `lower_max_tokens_64_sanitizer` unless it stops producing `empty_after_sanitize`.
3. Do not adopt `lower_max_tokens_32_sanitizer` while it can produce adapter failure or timeout.

## Run Result - 2026-05-25

Artifact:

```text
artifacts/qairt244_npu_turn_stop_quality_compare/20260525_065723/
```

Result summary:

- `sanitizer_only`: passed for `こんにちは`, `はじめまして`, and `こんばんは`; all three were classified `natural_japanese`.
- `lower_max_tokens_64_sanitizer`: rejected because `はじめまして` returned `empty_after_sanitize`.
- `lower_max_tokens_32_sanitizer`: rejected because `はじめまして` hit `adapter_failure:LiteRtLmJniException` and `こんばんは` timed out.
- `stop_sequence_end_of_turn`: `not_run/native_stop_not_exposed`.

The adopted hidden experimental display-quality baseline is enhanced sanitizer-only with `max_output_tokens=128`. Lower token caps are not a safe baseline in this run.

Safety result for the adopted case:

- `npu_evidence=true`
- `fallback_used=false`
- `fresh_crash=false`
- `timeout=false`
- `selected_path_npu_saved=false`
- no DB, TTS, Markdown, or streaming connection recorded

The enhanced sanitizer removes quoted Gemma turn continuations such as bare `>` lines, quoted prompt echo, duplicate repeated assistant lines, and leading non-Japanese drift before the first Japanese assistant text. This is display cleanup only; it does not alter native decode, NPU route selection, DB, TTS, Markdown, streaming, release behavior, or standard model selection policy.
