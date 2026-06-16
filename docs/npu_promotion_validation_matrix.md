# NPU Promotion Validation Matrix

Scope: scripts and docs only. This matrix does not change Android runtime, UI,
CPU/GPU/NPU route behavior, classifier behavior, sanitizer behavior, native
libraries, or promotion gates.

## Purpose

Current state:

```text
NPU_ALIGNMENT_DECISION=review_warning
PROMOTION_SCORE=83
READY_FOR_STANDARD_ROUTE=false
```

The existing repeatability set has only three prompts. Before standard route
connection, NPU needs a broader validation matrix covering length, Markdown,
mixed-language proper nouns, and quality-gate edge cases.

## Validation Prompt Suite

Each device-run artifact should include either:

```text
validation_category=<category>
```

or enough prompt / filename context for the script to infer the category.

### short

Purpose: short greeting and basic assistant behavior.

Prompts:

```text
こんにちは
自己紹介してください
```

Pass criteria:

- `status=success`
- real NPU backend evidence
- `fallback=false`
- `timeout=false`
- `fresh_crash=false`
- `run_decode_reached=true`
- `quality_classification=natural_japanese` or
  `output_quality_candidate_status=quality_candidate_pass`

### medium

Purpose: normal user requests with moderate output.

Prompts:

```text
カレーの材料をお願いします。
日本の祝日を教えてください。
```

### long

Purpose: longer decode stability before standard route connection.

Prompts:

```text
300文字程度で、睡眠をよくする方法を説明してください。
500文字程度で、健康的な食事の考え方を説明してください。
```

### markdown

Purpose: formatting boundaries before Markdown integration is connected.

Prompts:

```text
箇条書きで10件、朝の習慣を提案してください。
番号付きリストで10件、旅行の準備を教えてください。
表形式で、野菜と保存方法をまとめてください。
```

### mixed_language

Purpose: proper nouns and inline English that should not be confused with
output corruption.

Prompts:

```text
Google DeepMind と Gemma について日本語で説明してください。
日本語と英語を少し混ぜて、AI assistant の特徴を説明してください。
```

### quality_gate

Purpose: verify the alignment cases that currently block final review.

Required cases:

```text
template cleanup candidate: leading > / safe <end_of_turn> cleanup
mixed-language proper noun candidate: Google DeepMind / Gemma 4
strict natural Japanese: no template marker, no Latin proper noun mismatch
```

## Script

Use:

```bash
scripts/review_npu_validation_matrix.sh --device-runs artifacts/device_runs
```

Output:

```text
NPU_VALIDATION_RESULT=...
VALIDATION_SCORE=...
PASSED_CASES=...
FAILED_CASES=...
PROMOTION_RECOMMENDATION=...
NEXT_ACTION=...
```

## Scoring

There are six required categories:

```text
short
medium
long
markdown
mixed_language
quality_gate
```

Each category contributes equally. A category passes when at least one artifact
in that category passes and no artifact in that category hard-fails.

```text
VALIDATION_SCORE = passed_categories * 100 / 6
```

Examples:

- all six categories pass -> `VALIDATION_SCORE=100`
- five categories pass -> `VALIDATION_SCORE=83`
- three current categories only -> at most `VALIDATION_SCORE=50`

## Recommendation

```text
PROMOTION_RECOMMENDATION=ready_for_standard_route_review
```

All categories are present and pass.

```text
PROMOTION_RECOMMENDATION=candidate
```

Score is at least 80 and no hard failures are present, but some category is
still missing.

```text
PROMOTION_RECOMMENDATION=not_ready
```

Score is below 80, hard failures are present, or device runs are missing.

## Current Interpretation

The current three-prompt repeatability evidence is not enough for the full
matrix. It supports the quality-alignment decision review, but standard route
connection should still wait for:

- long prompt coverage
- Markdown prompt coverage
- mixed-language prompt coverage beyond one self-introduction case
- explicit quality-gate edge-case artifacts

Expected posture:

```text
PROMOTION_RECOMMENDATION=not_ready
NEXT_ACTION=collect_missing_validation_categories_before_standard_route_review
```

This does not contradict `NPU_ALIGNMENT_DECISION=review_warning`; it means more
coverage is needed before standard route connection.

## Report Integration

`scripts/render_npu_investigation_report.sh` includes:

```text
## NPU Validation Matrix Summary
```

This section embeds:

```bash
scripts/review_npu_validation_matrix.sh --device-runs <device-runs-dir>
```
