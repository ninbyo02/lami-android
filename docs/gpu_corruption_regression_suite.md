# GPU Corruption Regression Suite

Scope: diagnostics-only regression suite for manual device runs. This suite does
not change CPU, NPU, GPU, runtime stack, callback joining, UI, or production
behavior.

## Goal

Quantify where Lami GPU raw callback corruption appears:

- length-dependent
- Markdown-dependent
- list/table-dependent
- Japanese-dependent
- English/mixed-language-dependent
- numeric/symbol-heavy-dependent

Current known blocker:

- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
- Edge Gallery official GPU is clean for long Japanese output.
- Lami GPU long output corrupts at raw callback source.

## Prompt Matrix

Generate prompt templates:

```bash
scripts/generate_gpu_regression_matrix.sh
```

Output:

```text
artifacts/gpu_regression_matrix/
```

### Short

| File | Prompt |
| --- | --- |
| `short_greeting.txt` | `こんにちは` |
| `short_self_intro.txt` | `自己紹介してください。` |

### Medium

| File | Prompt |
| --- | --- |
| `medium_curry.txt` | `カレーの材料をお願いします。` |
| `medium_holiday.txt` | `日本の祝日を教えてください。` |

### Long

| File | Prompt intent |
| --- | --- |
| `long_300chars.txt` | 300-character Japanese explanation |
| `long_500chars.txt` | 500-character Japanese explanation |

### Markdown

| File | Prompt intent |
| --- | --- |
| `markdown_bullets.txt` | 10 bullet items |
| `markdown_numbered.txt` | 10 numbered items |
| `markdown_table.txt` | Markdown table |

### Mixed

| File | Prompt intent |
| --- | --- |
| `mixed_ja_en.txt` | Japanese plus short English phrases |
| `mixed_symbols.txt` | numeric and symbol-heavy ingredient quantities |

## Required Diagnostics

For each generated answer, copy compact/details diagnostics and save them under
`artifacts/device_runs/`. Important keys:

- `selected_backend`
- `route_family`
- `callback_quality_classification`
- `callback_corruption_earliest_stage`
- `gpu_output_source_corruption_stage`
- `gpu_fragmentation_score`
- `gpu_output_quality_candidate_result`
- `gpu_sampler_root_cause_candidate`
- `gpu_output_quality_gate_status`
- `gpu_output_quality_promotion_blocker`
- `gpu_output_suspicious_fragment_detected`
- `gpu_output_suspicious_fragment_reason`
- `gpu_output_suspicious_fragment_position`
- `gpu_output_suspicious_fragment_tail_ratio`

## Expected Result Patterns

| Pattern | Evidence |
| --- | --- |
| short pass, medium/long fail | length-dependent runtime decode fragmentation |
| Markdown-only fail | markdown/list/table shape may trigger decode fragmentation earlier |
| numeric/symbol-only fail | token/number/unit decode boundary sensitivity |
| Japanese-only fail, English pass | language/script-dependent decode behavior |
| CPU pass, GPU fail | GPU-specific runtime/executor/callback source issue |
| CPU and GPU fail | prompt/model content issue or broader route issue |

## Summary

After saving diagnostics, run:

```bash
scripts/summarize_gpu_regression_results.sh --input artifacts/device_runs
```

It writes:

```text
artifacts/device_runs/GPU_CORRUPTION_REGRESSION_SUMMARY.md
```

Summary columns:

- `Prompt`
- `Quality`
- `FragmentScore`
- `RootCause`
- `PromotionBlocker`
