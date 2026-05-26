# QAIRT244 NPU Max Output Token Limit Investigation

Date: 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_token_limit_investigation/20260526_202629/`

Scope: static investigation only. This pass did not change native code, did
not rebuild QAIRT/LiteRT-LM, did not execute NPU generation, did not call
`Engine.initialize`, and did not call `RunDecode`.

## Max256 Guard Preflight Update - 2026-05-26

Status: 256 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155/`
- preflight artifact:
  `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300/`

The 256 runner now has a preflight-only guard path. `--preflight-only` exits
before device selection, app launch, NPU generation, `Engine.initialize`, or
`RunDecode`. Non-preflight execution is refused unless supplied static native
artifact evidence shows all of:

- `qairt244_editable_prompt_max256_v1`
- `native_max_output_tokens_limit=256`
- `SetMaxOutputTokens(256)`
- SM8750-only model/selection evidence

No ChatScreen, DB, TTS, Markdown, streaming, selected-path persistence, or
normal route surface is connected by the preflight.

Limited rebuild metadata:

```text
liblitertlm_jni.so build_id=c42e4438f1b39e384ab075b9392831ca
liblitertlm_jni.so sha256=3767332f97ffee57b635fc13e2741714c994f7a2cc94d0fde5d4fbbce9c731ba
```

The preflight passed against the rebuilt artifact. This is not runtime proof:
the next phase still needs separate approval for a single hidden experimental
256 prompt run with timeout, fresh-crash, fallback, QNN evidence, memory, and
sanitizer quality gates.

## Max256 Single Prompt Verification - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046/`

Result: the first hidden experimental `max_output_tokens=256` runtime
verification passed for exactly one prompt, `こんにちは`.

Key evidence:

- `result=success`
- `max_output_tokens=256`
- `run_decode_reached=true`
- native diag reached
  `before RunDecode SetMaxOutputTokens(256) native_max_output_tokens_limit=256`
- `max_output_tokens_limit_marker=qairt244_editable_prompt_max256_v1`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `selected_path_npu_saved=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- sanitized output quality: `natural_japanese`
- sanitized output:
  `こんにちは！何かお手伝いできることはありますか？`

Raw output still contains Gemma turn artifacts and prompt echo, but sanitizer
removed them for display (`removed_template_token_count=2`,
`removed_prompt_echo=true`). This keeps the existing gate interpretation:
raw artifacts are tolerated only when sanitized output is safe.

Memory evidence:

```text
after run TOTAL PSS=298148 KB
after 10s TOTAL PSS=288007 KB
```

Decision: 256 may proceed to a separately approved three-prompt hidden
comparison. Do not promote 256 to the H1 display baseline yet; 128 remains the
adopted baseline.

## Max256 Three-Prompt Hidden Comparison - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

The approved hidden experimental three-prompt comparison ran exactly once per
prompt with `sanitizer_only + max_output_tokens=256`.

Results:

- `こんにちは`: `success`, `quality_classification=natural_japanese`,
  `decode_ms=884`, `elapsed_ms=3000`.
- `Pythonで簡単な電卓コードを書いて`: `success`,
  `quality_classification=useful_code`, `decode_ms=7351`,
  `elapsed_ms=9000`.
- `ラミィのNPU推論について短く説明して`: `success`,
  `quality_classification=natural_japanese`, `decode_ms=4110`,
  `elapsed_ms=6000`.

All rows recorded `RunDecode` reached, `npu_backend=NPU`,
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`fallback_used=false`, `timeout=false`, `fresh_crash=false`,
`selected_path_npu_saved=false`, `standard_route_connected=false`,
`normal_ui_route_connected=false`, and DB/TTS/Markdown/streaming false.

Memory did not remain high after the 10-second cool-down:
`TOTAL PSS=224993 KB` and `Native Heap=34500 KB`.

Decision: 256 is now a hidden experimental baseline candidate, but it is not
adopted for H1 or normal UI. The Python/code prompt is useful, though display
formatting and indentation should be reviewed before any UI-facing baseline
change. Proceed to 512 only through a separate guard/build/preflight and a
single-prompt run first; do not jump to 1024, 2048, or 4096.

Result commit decision: keep 128 as the adopted H1/normal-UI-safe baseline and
record 256 as a hidden experimental baseline candidate only. Any 512 work must
start with native guard/build/preflight evidence, then one single prompt with
`RunDecode` reached, QNN evidence, no timeout, no fresh crash, no fallback,
memory-after-10s recovery, and sanitizer quality review.

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
