# NPU Quality Alignment Review

Scope: scripts and docs only. This review does not change
`quality_classification`, Android runtime behavior, UI, route implementation,
native libraries, or promotion rules.

## Purpose

Current NPU repeatability is close to a standard-route candidate, but readiness
is still blocked by:

```text
REMAINING_BLOCKERS=quality_classification_alignment
```

The issue is not that the promotion gate should be relaxed. The issue is that
`quality_classification` can disagree with the candidate/display output quality:

- `こんにちは`: candidate output is natural after template cleanup, while
  primary classification is `template_artifact`.
- `あなたは誰ですか？`: candidate output is natural Japanese with proper nouns,
  while primary classification is `mixed_language`.
- `カレーの材料をお願いします。`: primary classification is already
  `natural_japanese`.

The quality alignment review records these cases mechanically so the next gate
can decide whether the classifier and candidate gate need alignment work.

## Script

Use:

```bash
scripts/review_npu_quality_alignment.sh --device-runs artifacts/device_runs
```

The script emits:

```text
NPU_QUALITY_ALIGNMENT=...
QUALITY_ALIGNMENT_SCORE=...
PASSED_ALIGNMENTS=...
FAILED_ALIGNMENTS=...
QUALITY_MISMATCHES=...
NEXT_ACTION=...
```

## Alignment Rules

### Natural Japanese

```text
quality_classification=natural_japanese
output_quality_candidate_status=quality_candidate_pass
```

Result:

```text
alignment=aligned
score=100
```

### Template Cleanup Candidate

```text
quality_classification=template_artifact
output_quality_candidate_status=quality_candidate_pass
sanitized_output / actual_display_text / prepared_output is present
```

Result:

```text
alignment=cleanup_alignment_candidate
score=80
QUALITY_MISMATCHES=template_artifact_vs_candidate_pass
```

This is not a promotion candidate. It means the prepared output is usable but
the primary classifier still does not report `natural_japanese`.

### Mixed-Language Proper Noun Candidate

```text
quality_classification=mixed_language
output_quality_candidate_status=quality_candidate_pass
sanitized_output / actual_display_text / prepared_output is present
```

Result:

```text
alignment=proper_noun_alignment_candidate
score=80
QUALITY_MISMATCHES=mixed_language_vs_candidate_pass
```

This covers outputs that contain proper nouns such as `Google DeepMind` or
`Gemma 4` while the Japanese response is otherwise natural. It still does not
relax the promotion gate.

### Quality Failure

```text
quality_classification=unknown
output_quality_candidate_status=quality_candidate_fail
```

Result:

```text
alignment=quality_failure
score=0
```

## Current Repeatability Expectation

For the current three-prompt set:

```text
こんにちは -> template cleanup candidate, score 80
あなたは誰ですか？ -> mixed-language proper noun candidate, score 80
カレーの材料をお願いします。 -> natural_japanese, score 100
```

Expected aggregate:

```text
NPU_QUALITY_ALIGNMENT=classifier_alignment_needed
QUALITY_ALIGNMENT_SCORE=86
PASSED_ALIGNMENTS=template_cleanup_candidate,mixed_language_proper_noun_candidate,natural_japanese
FAILED_ALIGNMENTS=primary_quality_classification_alignment
QUALITY_MISMATCHES=template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass
NEXT_ACTION=review_quality_classifier_alignment_without_relaxing_promotion_gate
```

## Promotion Rule

Full promotion remains blocked unless:

```text
quality_classification=natural_japanese
```

`cleanup_alignment_candidate` and `proper_noun_alignment_candidate` are
success-leaning review states, not promotion states.

## Report Integration

`scripts/render_npu_investigation_report.sh` includes:

```text
## NPU Quality Alignment Summary
```

This section embeds the output of:

```bash
scripts/review_npu_quality_alignment.sh --device-runs <device-runs-dir>
```

Use the report section to decide whether the next task is classifier alignment,
repeatability expansion, or standard-route connection review.

## Decision Review

After generating this alignment review, classify whether the remaining blocker
is a hard quality blocker or a conservative review warning:

```bash
scripts/review_npu_quality_alignment_decision.sh --device-runs artifacts/device_runs
```

Current expected decision:

```text
NPU_ALIGNMENT_DECISION=review_warning
ALIGNMENT_IS_HARD_BLOCKER=false
ALIGNMENT_IS_REVIEW_WARNING=true
CONFIDENCE_SCORE=80
RATIONALE=template_artifact_and_mixed_language_cases_are_explained_by_cleanup_and_proper_nouns
SAFE_NEXT_ACTION=collect_additional_repeatability_data_before_standard_route_connection
```

This is not a promotion gate change. It means the current blocker looks more
like a conservative primary-classifier false positive than a visible output
quality failure, while `READY_FOR_STANDARD_ROUTE` remains false.
