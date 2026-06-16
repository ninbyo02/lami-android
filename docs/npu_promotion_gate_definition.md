# NPU Promotion Gate Definition

Scope: planning and diagnostics only. This document defines the minimum
evidence needed before promoting NPU from hidden / diagnostic route toward a
standard route candidate.

## Minimum Gate

All of these must be true before a standard-route candidate is considered:

```text
status=success
fallback_used=false
fresh_crash=false
timeout=false
standard_route_connected=true
conversation_created=true
generate_response=true
quality_classification=natural_japanese
cleanup_status=success
engine_close_evidence=present
```

Additional backend evidence must show real NPU execution:

```text
npu_backend_evidence=QNN_HTP_V79_FastRPC
```

Equivalent QNN / HTP / NPU evidence is acceptable if the artifact clearly
proves that CPU/GPU fallback was not used.

## Promotion Blockers

Immediate blockers:

```text
fallback_used=true
fresh_crash=true
timeout=true
quality_classification!=natural_japanese
```

`output_quality_candidate_status=quality_candidate_pass` is not sufficient for
promotion when `quality_classification` is still `template_artifact`, `unknown`,
or `mixed_language`. Template cleanup cases are classified as:

```text
NPU_CLASSIFICATION=npu_quality_candidate_pass_with_template_cleanup
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
NPU_PROMOTION_DECISION_REASON=quality_candidate_pass_but_primary_classification_not_natural_japanese
```

The next action is repeatability testing and alignment between the prepared
output quality candidate and the primary quality classifier. Do not relax the
full promotion gate until `quality_classification=natural_japanese` is observed
under the same route and prompt matrix.

Mixed-language candidate-pass cases are classified separately:

```text
NPU_CLASSIFICATION=npu_quality_candidate_pass_with_mixed_language_terms
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
NPU_PROMOTION_DECISION_REASON=mixed_language_classification_with_quality_candidate_pass
```

English proper nouns can make this less severe than a generic quality failure,
but it still requires repeatability testing and quality gate review before
promotion.

Additional blockers:

- `npu_s1_failure_kind=engine_create_failed`
- `last_failure_was_engine_create_failed=true`
- `NPU_CLASSIFICATION=npu_engine_create_failed`
- `NPU_CLASSIFICATION=npu_compiled_model_failure`
- missing NPU/QNN/HTP backend evidence
- `standard_route_connected=false` when testing a standard-route probe
- `conversation_created=false`
- `generate_response=false`
- `cleanup_status` missing or failed
- `engine_close_evidence` missing
- CPU/GPU route contamination
- `selected_path_npu_saved=true` before explicit promotion approval
- DB / TTS / Markdown / streaming connected before isolated route stability
- unknown QAIRT / QNN / LiteRT dispatch / model provenance

Classifier output should map these blockers to:

```text
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
```

For current S1 DEV diagnostics, `npu_engine_create_failed` means route entry and
NPU backend evidence can be present while promotion remains blocked at the
LiteRT NPU compiled model executor layer.

## Promotion Readiness Review

The readiness review script aggregates multiple copied NPU device-run
diagnostics:

```bash
scripts/review_npu_promotion_readiness.sh --device-runs artifacts/device_runs
```

It emits:

```text
NPU_PROMOTION_READINESS=near_candidate
NPU_PROMOTION_READINESS_SCORE=80
PASSED_GATES=status,backend,backend_evidence,decode,native_call_returned,cleanup,no_fallback,no_timeout,no_crash
FAILED_GATES=quality_alignment
REMAINING_BLOCKERS=quality_classification_alignment
NEXT_ACTION=collect_repeatability_matrix_and_review_standard_route_connection
```

Readiness scores:

- `100`: all runs are strict `npu_promotion_candidate` results.
- `80`: hard gates pass, but one or more runs are conditional quality passes
  such as template cleanup or mixed-language proper noun cases.
- `60`: hard gates pass but a generic quality failure remains.
- `40`: hard gate failure or hard blocker classification remains.
- `0`: no device-run diagnostics are available.

Current repeatability expectation for the three known prompts is
`near_candidate`: backend, decode, cleanup, no-fallback, no-timeout, and no-crash
gates pass, while quality alignment remains blocked by
`template_artifact` / `mixed_language` intermediate classifications.

## Quality Alignment Review

Before changing any route behavior, review the mismatch between
`quality_classification` and candidate/display output:

```bash
scripts/review_npu_quality_alignment.sh --device-runs artifacts/device_runs
```

The review emits:

```text
NPU_QUALITY_ALIGNMENT=...
QUALITY_ALIGNMENT_SCORE=...
PASSED_ALIGNMENTS=...
FAILED_ALIGNMENTS=...
QUALITY_MISMATCHES=...
NEXT_ACTION=...
```

Current expected repeatability result:

```text
NPU_QUALITY_ALIGNMENT=classifier_alignment_needed
QUALITY_ALIGNMENT_SCORE=86
QUALITY_MISMATCHES=template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass
NEXT_ACTION=review_quality_classifier_alignment_without_relaxing_promotion_gate
```

This review does not relax promotion. `template_artifact` and `mixed_language`
candidate-pass states remain blockers until the primary
`quality_classification` gate aligns with `natural_japanese` under the same
route and prompt matrix.

## Standard Route Connection Review

Before implementing a DEV-only standard route connection, run:

```bash
scripts/review_npu_standard_route_connection.sh --device-runs artifacts/device_runs
```

The review combines:

- copied NPU device-run diagnostics
- `scripts/classify_npu_diagnostic_result.sh`
- `scripts/review_npu_promotion_readiness.sh`

It emits:

```text
NPU_STANDARD_ROUTE_REVIEW=...
READY_FOR_CONNECTION=...
PASSED_GATES=...
FAILED_GATES=...
ROLLBACK_RISKS=...
NEXT_ACTION=...
```

For the current `near_candidate` repeatability set, the expected review is:

```text
NPU_STANDARD_ROUTE_REVIEW=needs_quality_alignment
READY_FOR_CONNECTION=false
FAILED_GATES=quality_gate_review,standard_route_connected,conversation_created,generate_response,engine_close_evidence
ROLLBACK_RISKS=none
NEXT_ACTION=finish_quality_alignment_before_standard_route_connection
```

This means the DEV route can continue collecting evidence, but standard route
connection is still blocked until the quality gate is aligned.

Rollback risks for a future standard route probe include:

- fallback
- fresh crash
- timeout
- decode not reached
- cleanup failure
- fresh tombstone
- quality regression
- selected path saved before approval
- DB / TTS / Markdown / streaming integration side effects before gate

The detailed checklist is maintained in
`docs/npu_standard_route_connection_review.md`.

## Promotion Final Review

The final pre-connection review combines readiness, quality alignment, and
standard route connection review:

```bash
scripts/review_npu_promotion_final.sh --device-runs artifacts/device_runs
```

It emits:

```text
NPU_PROMOTION_FINAL_REVIEW=...
READY_FOR_STANDARD_ROUTE=...
PROMOTION_SCORE=...
PASSED_REVIEWS=...
FAILED_REVIEWS=...
PROMOTION_BLOCKERS=...
SAFE_NEXT_ACTION=...
```

Current expected state:

```text
NPU_PROMOTION_FINAL_REVIEW=quality_alignment_pending
READY_FOR_STANDARD_ROUTE=false
PROMOTION_SCORE=83
SAFE_NEXT_ACTION=finish_quality_classification_alignment_before_standard_route_connection
```

`PROMOTION_SCORE` averages readiness and quality alignment scores, while
`READY_FOR_CONNECTION` remains a hard go/no-go input. A high score does not
authorize connection while `READY_FOR_STANDARD_ROUTE=false`.

## Gate Levels

### Hidden Route Gate

Allows continued hidden / diagnostic testing only.

Required:

- NPU backend evidence
- no fallback
- no fresh crash
- no timeout
- natural Japanese output
- artifact path and run id captured

### Standard Probe Gate

Allows a DEV-only standard-route candidate probe.

Required:

- Hidden Route Gate passes repeatedly
- route isolation evidence is clean
- cleanup evidence is present
- selected path is not persisted without explicit approval
- CPU route remains stable

### Standard Promotion Gate

Allows discussion of UI-facing NPU route promotion.

Required:

- Standard Probe Gate passes
- multiple prompts pass across short / medium / long outputs
- lifecycle remains stable across restart and repeated runs
- DB / TTS / Markdown / streaming integrations are added and validated one at
  a time

## Safe Stop Line

Stop promotion and keep NPU diagnostic-only if:

- any immediate blocker appears
- backend evidence is ambiguous
- output quality is not natural Japanese
- lifecycle cleanup requires force-stop for the target mode
- route isolation is not provable
