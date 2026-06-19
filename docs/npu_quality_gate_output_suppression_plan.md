# NPU Quality Gate Output Suppression Plan

Scope: planning only. Do not change Kotlin runtime, route behavior, UI, DB,
TTS, Markdown, streaming, native libraries, sanitizer behavior, or promotion
gates in this phase.

## Problem

The NPU validation matrix passes with a stop-line warning:

```text
VALIDATION_WARNINGS=quality_gate_output_must_not_reach_ui_tts_db
```

The `quality_gate` category intentionally expects rejection:

```text
output_quality_candidate_status=quality_candidate_fail
output_quality_candidate_reason=raw_unexpected_start_turn
quality_classification=template_artifact
```

Observed unsafe output shape:

```text
raw_output=_turn>\n<end_of_turn>\n<start_of_turn>model_turn>\n<end_of_turn>
sanitized_output=_turn>
actual_display_text=_turn>
tts_text=_turn>
```

This should count as a successful validation of the quality gate, but it must
not be treated as successful user-facing output.

## Required Rule

The standard route must enforce:

```text
output_quality_candidate_status=quality_candidate_fail
```

as a hard output suppression gate.

Suppression means:

- no model text appended to UI
- no `tts_text` spoken
- no DB message save
- no Markdown render
- no streaming or pseudo-streaming chunks
- no copied assistant candidate text that came from model output

The app may show a fixed app-authored diagnostic/failure message, but not the
failed model output.

## Candidate Pass

When:

```text
output_quality_candidate_status=quality_candidate_pass
status=success
fallback=false
timeout=false
fresh_crash=false
run_decode_reached=true
native_cleanup_reached=true
```

then the output can become a candidate for the current phase only. Phase 3 may
generate and hold the candidate, Phase 4 may append to UI, Phase 5 may TTS,
Phase 6 may DB save, and Phase 7 may Markdown / pseudo streaming.

## Candidate Fail

When:

```text
output_quality_candidate_status=quality_candidate_fail
```

then the route must set:

```text
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_suppression_reason=quality_candidate_fail
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

If the failed text resembles `_turn>`, `<start_of_turn>`, `<end_of_turn>`, role
markers, or empty-after-sanitize output, use a more specific reason:

```text
npu_standard_route_suppression_reason=quality_candidate_fail_raw_unexpected_start_turn
```

## Surface Rules

### UI

Do not append `actualDisplayText`, `displayText`, `sanitizedOutput`,
`preparedOutput`, or `rawOutput` when candidate status is fail.

Allowed replacement: fixed app-authored failure text such as:

```text
NPU推論の応答生成に失敗しました: quality_check_failed
```

Do not include model output in that failure text.

### TTS

Do not build or speak `ttsText` when candidate status is fail. The observed
`tts_text=_turn>` case must produce:

```text
npu_standard_route_tts_allowed=false
tts=false
```

### DB

Do not persist either model output or derived assistant text when candidate
status is fail. The user message may be handled according to the phase design,
but assistant DB save must be blocked until candidate pass.

### Markdown

Do not pass failed output through Markdown finalization or repair. Markdown
repair must not be used to hide a quality gate failure.

### Streaming

Do not emit pseudo-streaming chunks from failed output. Streaming is especially
risky because it can leak partial unsafe fragments before final gate evaluation.

## Existing Code Boundaries To Reuse

Read-only inspection found useful existing guards:

- `NpuStandardRouteS1Result.successCriteriaMet`
  - rejects `outputQualityCandidateStatus == quality_candidate_fail`
- `NpuStandardRouteS2DbMapper`
  - requires `s1Result.successCriteriaMet`
- `NpuStandardRouteS3MarkdownMapper`
  - requires `s1Result.successCriteriaMet`
- `NpuStandardRouteS4PseudoStreamingMapper`
  - requires `s1Result.successCriteriaMet`
- `NpuStandardRouteS5TtsMapper`
  - requires `s1Result.successCriteriaMet`

Future implementation should preserve these guards and add explicit diagnostics
around the suppression result.

## Diagnostics

Add on every NPU standard-route DEV-gated attempt:

```text
npu_standard_route_quality_gate_passed
npu_standard_route_output_suppressed
npu_standard_route_suppression_reason
npu_standard_route_failed_output_redacted
npu_standard_route_ui_append_allowed
npu_standard_route_tts_allowed
npu_standard_route_db_save_allowed
npu_standard_route_markdown_allowed
npu_standard_route_streaming_allowed
npu_standard_route_rollback_required
npu_standard_route_rollback_reason
```

For candidate fail, expected values:

```text
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_failed_output_redacted=true
npu_standard_route_rollback_required=true
npu_standard_route_rollback_reason=quality_gate_output_must_not_reach_ui_tts_db
```

## Tests For Future Implementation

Unit tests:

- `quality_candidate_pass` -> allowed candidate for active phase
- `quality_candidate_fail` -> suppresses UI/TTS/DB/Markdown/Streaming
- `actual_display_text=_turn>` -> suppresses all downstream surfaces
- `raw_unexpected_start_turn` -> specific suppression reason
- S2/S3/S4/S5 mappers do not produce candidates when S1 criteria fail
- failure message is app-authored and does not contain model output
- DEV gate off -> no standard route attempt
- CPU/GPU routes unaffected

Device tests:

- quality_gate expected rejection should report suppression and no UI/TTS/DB
  leakage
- short template cleanup pass should still allow normal candidate handling in
  the active phase
- medium/long/markdown/mixed-language validation should remain pass

## Stop Line

Do not proceed from Phase 3 to UI append if any of these appear:

```text
npu_standard_route_output_suppressed=false
output_quality_candidate_status=quality_candidate_fail
actual_display_text=_turn>
tts_text=_turn>
db=true
tts=true
markdown=true
streaming=true
```

This plan treats quality-gate expected rejection as evidence that the gate can
detect unsafe output, not evidence that the output is safe.
