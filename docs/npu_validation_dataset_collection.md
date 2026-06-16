# NPU Validation Dataset Collection

Scope: scripts and docs only. This guide does not change Android runtime, UI,
NPU route behavior, native libraries, classifier rules, or promotion gates.

## Purpose

The NPU review / readiness / promotion / validation matrix scripts are in
place, but device-run artifacts are still incomplete. The current validation
matrix can report:

```text
NPU_VALIDATION_RESULT=fail
VALIDATION_SCORE=0
```

when the required category artifacts are missing or mixed with older probe
files. This document defines the dataset to collect on device before standard
route connection review.

## Prompt Suite

Each saved artifact should include:

```text
validation_category=<category>
```

This avoids relying on filename or prompt heuristics.

### short

Prompt:

```text
こんにちは
```

Expected classifier target:

```text
npu_quality_candidate_pass_with_template_cleanup_or_natural_japanese
```

### medium

Prompt:

```text
カレーの材料をお願いします。
```

Expected classifier target:

```text
npu_promotion_candidate
```

### long

Prompt:

```text
300〜500文字程度で、健康的な食事の考え方を説明してください。
```

Expected classifier target:

```text
npu_promotion_candidate
```

### markdown

Prompt:

```text
箇条書きで旅行計画を作成してください。
```

Expected classifier target:

```text
npu_promotion_candidate
```

### mixed_language

Prompt:

```text
あなたは誰ですか？
```

Expected classifier target:

```text
npu_quality_candidate_pass_with_mixed_language_terms
```

### quality_gate

Prompt:

```text
こんにちは
```

Purpose: capture a short output that may expose safe leading `>` or
`<end_of_turn>` template cleanup behavior.

Expected classifier target:

```text
npu_quality_candidate_pass_with_template_cleanup
```

## Artifact Naming

Save copied NPU diagnostics under:

```text
artifacts/device_runs/
```

Use these names:

```text
npu_validation_short_YYYYMMDD.txt
npu_validation_medium_YYYYMMDD.txt
npu_validation_long_YYYYMMDD.txt
npu_validation_markdown_YYYYMMDD.txt
npu_validation_mixed_language_YYYYMMDD.txt
npu_validation_quality_gate_YYYYMMDD.txt
```

Each file should include the copied NPU diagnostic keys plus this explicit
category line near the top:

```text
validation_category=short
```

Change the value for each category.

## Manifest Script

Generate the collection manifest:

```bash
scripts/create_npu_validation_manifest.sh
```

Use a fixed date when preparing a run sheet:

```bash
scripts/create_npu_validation_manifest.sh --date 20260617
```

Output columns:

```text
CATEGORY
PROMPT
EXPECTED_CLASSIFIER_TARGET
ARTIFACT_NAME
```

## Collection Flow

1. Generate the manifest.
2. Run each prompt on the NPU DEV route.
3. Copy `NPU診断キー`.
4. Save one artifact per manifest row under `artifacts/device_runs/`.
5. Add `validation_category=<category>` to each artifact.
6. Run:

```bash
scripts/classify_npu_diagnostic_result.sh --input artifacts/device_runs/npu_validation_short_YYYYMMDD.txt
scripts/review_npu_validation_matrix.sh --device-runs artifacts/device_runs
scripts/render_npu_investigation_report.sh --device-runs artifacts/device_runs
```

## Expected Matrix Outcome

Before standard route connection review, the dataset should reach:

```text
NPU_VALIDATION_RESULT=pass
VALIDATION_SCORE=100
PROMOTION_RECOMMENDATION=ready_for_standard_route_review
```

If one category is missing but there are no hard failures, the expected posture
is still not full readiness:

```text
PROMOTION_RECOMMENDATION=candidate
NEXT_ACTION=fill_missing_validation_categories_before_standard_route_review
```

Any fallback, timeout, fresh crash, missing NPU backend evidence, or quality
candidate failure keeps the result at:

```text
PROMOTION_RECOMMENDATION=not_ready
```
