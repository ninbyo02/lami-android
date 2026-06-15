# GPU Investigation Report Guide

Scope: report generation guide only. The report script reads artifacts and does
not change Android runtime behavior, callback streaming, UI, model loading, or
promotion gates.

## Purpose

After returning home and running device-side GPU diagnostics, generate one
Markdown file that summarizes:

- latest device diagnostics
- GPU output quality matrix
- executor probe classification
- runtime/native stack fingerprints
- APK native diff
- regression suite summary
- promotion blocker status
- root cause ranking
- next actions

## Save Artifacts After Device Runs

Recommended layout:

```text
artifacts/device_runs/
  gpu_executor_probe_2026-06-15.txt
  gpu_short_greeting.txt
  gpu_medium_curry.txt
  gpu_long_500chars.txt
  cpu_long_500chars.txt

artifacts/gpu_output_quality_matrix/
  baseline.txt
  collect_only.txt
  no_sampling_acceleration.txt
  edge_gallery_executor_probe.txt

artifacts/apk_native_diff/
  native_lib_inventory.tsv
  jni_symbol_diff.tsv
  native_stack_fingerprint.txt
  runtime_stack_summary.txt
```

The report handles missing directories gracefully and records them as missing.

## Generate Report

```bash
scripts/render_gpu_investigation_report.sh \
  --device-runs artifacts/device_runs \
  --quality-matrix artifacts/gpu_output_quality_matrix \
  --apk-native-diff artifacts/apk_native_diff \
  --output artifacts/gpu_investigation_report/GPU_INVESTIGATION_REPORT.md
```

Output:

```text
artifacts/gpu_investigation_report/GPU_INVESTIGATION_REPORT.md
```

## Sections

| Section | Meaning |
| --- | --- |
| Overview | Shows which artifact inputs were present or missing. |
| Latest device run summary | Extracts high-value keys from the newest copied diagnostics file. |
| GPU output quality summary | Calls `scripts/summarize_gpu_output_quality_matrix.sh` when matrix artifacts exist. |
| Executor probe classification | Calls `scripts/classify_gpu_executor_probe_result.sh` for the executor probe diagnostics. |
| Runtime/native stack fingerprint summary | Includes `native_stack_fingerprint.txt` from APK native diff output. |
| APK native diff summary | Includes runtime stack summary, native library inventory, and JNI symbol diff snippets. |
| Regression suite summary | Calls `scripts/summarize_gpu_regression_results.sh` when device run artifacts exist. |
| Promotion blocker status | Computes whether latest diagnostics still block standard GPU promotion. |
| Root cause ranking | Captures the current investigation ranking. |
| Next actions | Lists what to do based on the observed classification. |

## Promotion Blocker Interpretation

The report marks GPU promotion as blocked if the latest diagnostics show any of:

- `gpu_output_quality_promotion_blocker=true`
- `gpu_output_quality_gate_status=fail`
- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`

If the report says blocked, do not promote standard GPU.

## Decision Examples

| Report signal | Action |
| --- | --- |
| `same_stack_different_executor` | Focus on internal executor/backend selection, RuntimeConfig, backend constraints, and GPU_ARTISAN evidence. |
| `different_runtime_stack` | Compare APK native stack SHA-256 values and keep experiments isolated. |
| `gpu_only_corrupt` | Collect raw callback artifacts for failing GPU prompts and compare CPU pass diagnostics. |
| `long_text_only_corrupt` | Treat output length/decode accumulation as a stronger trigger. |
| `quality_gate_pass` | Repeat restart, multi-turn, Markdown, long output, and mixed prompt tests before changing any gate. |

## Self Test

```bash
scripts/render_gpu_investigation_report.sh --self-test
```

The self-test creates temporary fixtures and verifies:

- report file is generated
- `Overview` exists
- `Promotion Blocker Status` exists
- missing inputs do not fail report generation
