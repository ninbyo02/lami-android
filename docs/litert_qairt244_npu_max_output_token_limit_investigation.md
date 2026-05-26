# QAIRT244 NPU Max Output Token Limit Investigation

Date: 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_token_limit_investigation/20260526_202629/`

Scope: static investigation only. This pass did not change native code, did
not rebuild QAIRT/LiteRT-LM, did not execute NPU generation, did not call
`Engine.initialize`, and did not call `RunDecode`.

## Finding

The observed `max_output_tokens=256` rollback is caused by the custom qairt244
editable-prompt native entrypoint in:

`/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`

The native entrypoint rejects values outside `1..128` before decode setup:

```text
invalid_max_output_tokens value=256 native_max_output_tokens_limit=128
```

`DecodeConfig::CreateDefault()`, `SetMaxOutputTokens(max_output_tokens)`, and
`RunDecode` are downstream of that guard, so the 256 request did not reach the
LiteRT-LM decode API or the QNN runtime.

## Classification

Classification: `A. custom_safety_guard_only`

Static rationale:

- the qairt244 JNI editable-prompt path has an explicit `>128` rejection
  before `DecodeConfig`;
- `WriteQairt244EditablePromptResult` also emits
  `native_max_output_tokens_limit=128`;
- `DecodeConfig::SetMaxOutputTokens` is exposed as an integer setter in
  LiteRT-LM runtime code and no static 128 cap was found in the inspected
  setter path;
- the 256 artifact failed with `empty_after_sanitize` because native returned
  an invalid-token request before decode, not because of fallback, timeout, or
  fresh crash.

This does not prove that the compiled SM8750 model, tokenizer/context window,
KV/cache behavior, QNN/HTP runtime, or device memory are safe above 128. It
only identifies the source of the current rejection.

## Current Decision

Keep the hidden experimental baseline fixed at:

```text
sanitizer_only + max_output_tokens=128
```

`max_output_tokens=256` remains rollback-only until a deliberate native guard
patch is reviewed, rebuilt, installed, and validated in a separate phase.

## Minimal Future Patch Shape

Do not implement this in the current task. The next patch should be small and
reviewable:

- replace the literal 128 in the qairt244 editable-prompt guard with a named
  qairt244 constant;
- raise that constant only to 256 in the first experimental patch;
- ensure the result writer records the requested/actual max output token value
  and the native limit consistently;
- keep the change limited to `customBuildExperimentDebug`/hidden diagnostic
  behavior;
- do not touch release, standard, normal ChatScreen, DB, TTS, Markdown,
  streaming, selected-path persistence, or `app/src/main/jniLibs`.

## Stage Plan Toward 4096

- Phase T0: keep the 128 baseline.
- Phase T1: prepare and review a guard-only 256 patch; no generation required.
- Phase T2: run one 256 prompt once with memory and cleanup evidence.
- Phase T3: run the 256 three-prompt comparison once.
- Phase T4: run one 512 prompt once.
- Phase T5: run one 1024 prompt once.
- Phase T6: run one 2048 prompt once.
- Phase T7: run one 4096 prompt once.

4096 is the final target, not the next runnable step. Do not jump directly from
128 to 4096.

## Gates For Every Expansion Phase

Required:

- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`
- sanitized output is non-empty
- no template artifact after sanitize
- no repeated completion after sanitize
- no multilingual drift after sanitize
- memory before/after/after-10s captured
- cleanup evidence captured
- process-alive and fresh tombstone checks captured
- artifact size remains bounded

Long decode phases also need a separate timeout policy, UI-freeze guard,
sanitizer memory-pressure check, and repetition/drift review.

## Rollback Conditions

Rollback if any phase records timeout, fresh crash, fallback, missing QNN
evidence, empty sanitized output, template artifact after sanitize, worse
repetition, worse multilingual drift, retained memory growth after 10 seconds,
selected-path persistence, DB/TTS/Markdown/streaming flow, normal route
connection, generic/qcs8275 model selection, or stale artifact evidence.
