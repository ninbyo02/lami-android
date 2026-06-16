# NPU Promotion Final Review

Scope: scripts and docs only. This review does not change Android runtime,
Kotlin/UI, CPU/GPU/NPU route behavior, hidden configuration, native libraries,
or promotion requirements.

## Purpose

NPU DEV route evidence currently reports:

```text
NPU_PROMOTION_READINESS=near_candidate
NPU_PROMOTION_READINESS_SCORE=80
NPU_QUALITY_ALIGNMENT=classifier_alignment_needed
QUALITY_ALIGNMENT_SCORE=86
READY_FOR_CONNECTION=false
REMAINING_BLOCKERS=quality_classification_alignment
```

The final review combines the three pre-existing reviews and answers one
question: can the standard route be connected now?

Current answer:

```text
READY_FOR_STANDARD_ROUTE=false
```

## Script

Use device-run artifacts:

```bash
scripts/review_npu_promotion_final.sh --device-runs artifacts/device_runs
```

Or use pre-rendered review outputs:

```bash
scripts/review_npu_promotion_final.sh \
  --readiness-result artifacts/npu_reviews/readiness.txt \
  --quality-alignment-result artifacts/npu_reviews/quality_alignment.txt \
  --standard-route-result artifacts/npu_reviews/standard_route_connection.txt
```

Output:

```text
NPU_PROMOTION_FINAL_REVIEW=...
READY_FOR_STANDARD_ROUTE=...
PROMOTION_SCORE=...
PASSED_REVIEWS=...
FAILED_REVIEWS=...
PROMOTION_BLOCKERS=...
SAFE_NEXT_ACTION=...
```

## Score

`PROMOTION_SCORE` is the average of:

- `NPU_PROMOTION_READINESS_SCORE`
- `QUALITY_ALIGNMENT_SCORE`

The standard route connection review is not averaged into the score. It is a
go/no-go condition. This preserves the current expected score:

```text
(80 + 86) / 2 = 83
```

## Decision Rules

### Quality Alignment Pending

```text
NPU_PROMOTION_READINESS=near_candidate
QUALITY_ALIGNMENT_SCORE>=80
READY_FOR_CONNECTION=false
```

Result:

```text
NPU_PROMOTION_FINAL_REVIEW=quality_alignment_pending
READY_FOR_STANDARD_ROUTE=false
PROMOTION_SCORE=83
SAFE_NEXT_ACTION=finish_quality_classification_alignment_before_standard_route_connection
```

This is the current expected state.

### Promotion Candidate

```text
NPU_PROMOTION_READINESS=ready_candidate
NPU_QUALITY_ALIGNMENT=aligned
QUALITY_ALIGNMENT_SCORE>=90
READY_FOR_CONNECTION=true
```

Result:

```text
NPU_PROMOTION_FINAL_REVIEW=promotion_candidate
READY_FOR_STANDARD_ROUTE=true
SAFE_NEXT_ACTION=prepare_dev_only_standard_route_connection_probe
```

This still means DEV-only standard route connection, not production promotion.

### Blocked

Any timeout, crash, fallback, decode failure, rollback risk, missing device run,
or failed hard gate results in:

```text
NPU_PROMOTION_FINAL_REVIEW=blocked
READY_FOR_STANDARD_ROUTE=false
SAFE_NEXT_ACTION=stop_standard_route_work_and_fix_hard_blocker
```

## Current Expected Output

For the current repeatability set:

```text
NPU_PROMOTION_FINAL_REVIEW=quality_alignment_pending
READY_FOR_STANDARD_ROUTE=false
PROMOTION_SCORE=83
PASSED_REVIEWS=readiness_near_candidate,quality_alignment_score
FAILED_REVIEWS=readiness_not_strict_candidate,quality_alignment,standard_route_connection
PROMOTION_BLOCKERS=quality_classification_alignment,template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass,quality_gate_review,standard_route_connected,conversation_created,generate_response,engine_close_evidence
SAFE_NEXT_ACTION=finish_quality_classification_alignment_before_standard_route_connection
```

## Report Integration

`scripts/render_npu_investigation_report.sh` includes:

```text
## NPU Promotion Final Review
```

This section embeds the output of:

```bash
scripts/review_npu_promotion_final.sh --device-runs <device-runs-dir>
```

Use this section as the final pre-connection stop/go summary.
