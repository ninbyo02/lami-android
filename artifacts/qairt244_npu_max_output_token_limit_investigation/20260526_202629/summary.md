# QAIRT244 NPU Max Output Token Limit Investigation

Date: 2026-05-26

Scope: static investigation only. No native limit change, QAIRT rebuild,
Engine.initialize, RunDecode, 256 rerun, or NPU generation was performed.

## Result

`native_max_output_tokens_limit=128` is defined in the custom qairt244
editable-prompt JNI entrypoint:

`/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`

The entrypoint rejects `max_output_tokens=256` before `DecodeConfig` is created
or `SetMaxOutputTokens(max_output_tokens)` is called. The observed 256 rollback
is therefore classified as:

`A. custom_safety_guard_only`

## Evidence Files

- `app_rg.txt`: app/scripts/docs search.
- `litertlm_rg.txt`: LiteRT-LM static search.
- `litert_rg.txt`: LiteRT static search.
- `native_limit_source.txt`: focused source finding and classification.
- `external_native_status.txt`: external LiteRT-LM checkout status, captured
  without modification.
- `external_native_diff.patch`: pre-existing external native diff captured as
  evidence only.
- `staged_diff_summary.txt`: local diff summary captured for this task.

## 256 Decision

Do not rerun 256 and do not treat 256 as a display baseline until a separate
native patch intentionally raises the custom guard and the new limit passes
fresh safety gates.

## Expansion Plan

- Phase T0: keep `sanitizer_only + max_output_tokens=128` baseline.
- Phase T1: prepare a guard-only 256 native patch proposal and review it.
- Phase T2: run one 256 prompt once.
- Phase T3: run the 256 three-prompt comparison once.
- Phase T4: run one 512 prompt once.
- Phase T5: run one 1024 prompt once.
- Phase T6: run one 2048 prompt once.
- Phase T7: run one 4096 prompt once.

Every phase must keep `fallback_used=false`, `fresh_crash=false`,
`timeout=false`, QNN/HTP/FastRPC evidence present, `selectedPathSaved=false`,
DB/TTS/Markdown/streaming disconnected, memory-after-10s captured, cleanup
evidence recorded, and sanitized output free of template artifact, repeated
completion, and multilingual drift.
