# NPU Quality Classification Alignment Decision Review

Scope: scripts and docs only. This review does not change Android runtime,
Kotlin/UI, route behavior, native libraries, `quality_classification`, the
candidate gate, sanitizer behavior, or promotion gates.

## Purpose

Current NPU evidence:

```text
NPU_PROMOTION_READINESS=near_candidate
NPU_PROMOTION_READINESS_SCORE=80
NPU_QUALITY_ALIGNMENT=classifier_alignment_needed
QUALITY_ALIGNMENT_SCORE=86
NPU_PROMOTION_FINAL_REVIEW=quality_alignment_pending
READY_FOR_STANDARD_ROUTE=false
PROMOTION_SCORE=83
```

The remaining blocker is:

```text
quality_classification_alignment
```

This review decides whether that blocker currently looks like a true output
quality problem or a conservative false positive caused by the primary
classifier and candidate/display gate looking at different text surfaces.

## Current Classification System

### Primary `quality_classification`

Produced by:

```text
app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244OutputUnicodeDiagnostics.kt
```

This classifier is conservative:

- raw template markers such as `<end_of_turn>` classify as `template_artifact`;
- non-Japanese script or unallowlisted Latin terms classify as
  `mixed_language`;
- only clean Japanese text with allowed inline Latin terms becomes
  `natural_japanese`.

### Candidate gate

Produced by:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuS1PersistentCustomJniDiagnostics.kt
```

The candidate evaluator prepares display-safe output by removing safe leading
`>` and safe `<end_of_turn>` variants, then checks for real unsafe output
patterns such as special-token leaks, prompt repetition, assistant repetition,
placeholder leaks, or empty output.

### Mapper and contract

`NpuStandardRouteS1Mapper.kt` mostly preserves the raw primary
`qualityClassification`.

`NpuStandardRouteS1Contract.kt` allows S1 success when:

```text
qualityClassification=natural_japanese
```

or:

```text
output_quality_candidate_status=quality_candidate_pass
```

This is a runtime success criterion, not a production promotion rule.

## Repeatability Evidence

| Prompt | Primary classification | Candidate status | Explanation |
| --- | --- | --- | --- |
| `こんにちは` | `template_artifact` | `quality_candidate_pass` | Raw output contains safe template residue such as leading `>` / `<end_of_turn>`, while sanitized/display output is natural. |
| `あなたは誰ですか？` | `mixed_language` | `quality_candidate_pass` | Display output is natural Japanese, but `Google DeepMind` / `Gemma 4` include Latin proper nouns outside the primary allowlist. |
| `カレーの材料をお願いします。` | `natural_japanese` | `quality_candidate_pass` | Primary and candidate gates align. |

## Decision Candidates

### `hard_blocker`

Use when:

- `output_quality_candidate_status=quality_candidate_fail`;
- `NPU_QUALITY_ALIGNMENT=quality_failure`;
- readiness has timeout / crash / fallback / decode hard failures;
- promotion final review is blocked by rollback or runtime risk.

This means the blocker likely reflects real quality or route failure.

### `review_warning`

Use when:

- readiness is `near_candidate`;
- quality alignment score is high enough, currently `>=80`;
- final review is `quality_alignment_pending`;
- candidate/display outputs pass;
- mismatches are explainable as safe template cleanup and Latin proper nouns.

This means the blocker is valid as a gate, but likely conservative. It should
be treated as a review warning while more repeatability data is collected.

### `needs_more_data`

Use when quality alignment, readiness, or final review inputs are missing.

### `inconclusive`

Use when the inputs are present but do not match either known false-positive
patterns or hard-failure patterns.

## Script

Use device-run artifacts:

```bash
scripts/review_npu_quality_alignment_decision.sh --device-runs artifacts/device_runs
```

Or pass pre-rendered review outputs:

```bash
scripts/review_npu_quality_alignment_decision.sh \
  --quality-alignment-result artifacts/npu_reviews/quality_alignment.txt \
  --readiness-result artifacts/npu_reviews/readiness.txt \
  --promotion-final-result artifacts/npu_reviews/final.txt
```

Output:

```text
NPU_ALIGNMENT_DECISION=...
ALIGNMENT_IS_HARD_BLOCKER=...
ALIGNMENT_IS_REVIEW_WARNING=...
CONFIDENCE_SCORE=...
RATIONALE=...
SAFE_NEXT_ACTION=...
```

## Current Expected Decision

For the current three-prompt repeatability set:

```text
NPU_ALIGNMENT_DECISION=review_warning
ALIGNMENT_IS_HARD_BLOCKER=false
ALIGNMENT_IS_REVIEW_WARNING=true
CONFIDENCE_SCORE=80
RATIONALE=template_artifact_and_mixed_language_cases_are_explained_by_cleanup_and_proper_nouns
SAFE_NEXT_ACTION=collect_additional_repeatability_data_before_standard_route_connection
```

## Promotion Impact

This decision does not connect the standard route and does not relax the
promotion gate.

The practical interpretation is:

- current outputs are not showing clear NPU quality failure;
- the remaining blocker is likely a conservative classifier false positive;
- `READY_FOR_STANDARD_ROUTE` remains `false`;
- collect more repeatability data before standard route connection;
- do not change runtime or route behavior from this decision alone.

## Recommended Policy

Treat current `quality_classification_alignment` as:

```text
review_warning
```

not:

```text
hard_blocker
```

while preserving:

```text
READY_FOR_STANDARD_ROUTE=false
```

until an explicit follow-up changes the standard route connection gate.
