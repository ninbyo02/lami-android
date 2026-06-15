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
- GPU internal surface probe evidence
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
  internal_surface_summary.txt
  internal_surface_diff.tsv
```

The report handles missing directories gracefully and records them as missing.

## Copy Focused GPU Diagnostics

The inference stats bottom sheet keeps the existing full stats copy action. In
debug builds it also provides focused copy actions for GPU investigation. Use
these when the detail view is too long and important keys may be visually
truncated.

`GPU診断キーをコピー` is the first action to use for overall triage. It copies
the executor probe result, runtime fingerprints, quality gate status, promotion
blocker, root-cause candidate, raw callback corruption state, and performance
slow-path keys.

The copied text starts with:

```text
[GPU diagnostic keys]
```

Representative keys include:

- `selected_backend`, `requested_backend`, `effective_backend`, `route_family`
- `edge_gallery_executor_probe_result`
- `edge_gallery_executor_difference_summary`
- `executor_selection_fingerprint`
- `runtime_backend_fingerprint`
- `runtime_executor_fingerprint`
- `runtime_dispatch_fingerprint`
- `runtime_compiled_model_fingerprint`
- `gpu_internal_surface_probe_enabled`
- `gpu_internal_surface_probe_result`
- `gpu_internal_surface_probe_disabled_reason`
- `gpu_output_quality_promotion_blocker`
- `gpu_sampler_root_cause_candidate`
- `gpu_output_source_corruption_stage`
- `callback_corruption_earliest_stage`
- callback chunk counts, raw/final text head/tail, and GPU perf slow-path keys

`GPU内部surfaceキーをコピー` is the follow-up action for executor-surface
deep-dives. It keeps the copied payload narrow and focused on whether Lami can
see internal LiteRT-LM / GPU executor surfaces without relying on the long detail
view:

```text
[GPU internal surface keys]
```

Representative keys include:

- `gpu_internal_surface_probe_enabled`
- `gpu_internal_surface_probe_result`
- `gpu_internal_surface_probe_disabled_reason`
- `gpu_internal_runtime_config_class_present`
- `gpu_internal_backend_constraint_class_present`
- `gpu_internal_preferred_engine_type_class_present`
- `gpu_internal_gpu_options_class_present`
- `gpu_internal_artisan_class_present`
- `gpu_internal_llm_gpu_artisan_executor_symbol_present`
- `gpu_internal_kv_cache_symbol_present`
- `gpu_internal_runtime_config_methods`
- `gpu_internal_backend_constraint_methods`
- `gpu_internal_gpu_options_methods`
- `gpu_internal_probe_exception_class`
- `gpu_internal_probe_exception_message`

Missing keys are emitted as `unavailable`, so pasted diagnostics can be diffed
without changing shape between runs. For promotion triage, copy GPU diagnostic
keys first; use internal surface keys only when the executor / RuntimeConfig /
BackendConstraint / GPU_ARTISAN evidence needs deeper inspection.

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
| GPU internal surface probe summary | Shows read-only `gpu_internal_*` class/symbol evidence and the public API gap interpretation. |
| Runtime/native stack fingerprint summary | Includes `native_stack_fingerprint.txt` from APK native diff output. |
| APK native diff summary | Includes runtime stack summary, native library inventory, and JNI symbol diff snippets. |
| Internal surface summary artifacts | Included under APK native diff summary when `internal_surface_summary.txt` / `internal_surface_diff.tsv` exist. |
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
| `GPU_INTERNAL_SURFACE_EVIDENCE=runtime_config_class_absent,...,gpu_artisan_symbol_present,kv_cache_symbol_present` | Treat the issue as public API gap plus native/internal executor evidence; compare Edge Gallery and Lami native/internal fingerprints next. |
| `INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface` | Edge Gallery APK and Lami APK expose different hidden executor/config/KV-cache string/symbol surfaces. Keep investigation on native/internal selector differences. |
| `INTERNAL_SURFACE_DIFF_SUMMARY=same_internal_surface` plus `same_sampler_different_executor` | Static capability appears aligned; focus on runtime selector inputs, model metadata, backend constraints, or hidden RuntimeConfig values. |
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
- `GPU Internal Surface Probe Summary` exists when fixture diagnostics include
  `gpu_internal_surface_probe_enabled`
- `Promotion Blocker Status` exists
- missing inputs do not fail report generation

## Saving Copied Diagnostics For Classification

After a device run, paste both copy outputs into one file:

```bash
mkdir -p artifacts/device_runs
cat > artifacts/device_runs/gpu_executor_probe_latest.txt <<'EOF'
[GPU diagnostic keys]
...
[GPU internal surface keys]
...
EOF
```

Then run:

```bash
scripts/classify_gpu_executor_probe_result.sh \
  --input artifacts/device_runs/gpu_executor_probe_latest.txt
```

For the current failing Lami GPU path, expected high-value output is:

```text
GPU_EXECUTOR_PROBE_CLASSIFICATION=same_stack_different_executor
GPU_INTERNAL_SURFACE_EVIDENCE=runtime_config_class_absent,backend_constraint_class_absent,preferred_engine_type_class_absent,gpu_options_class_absent,artisan_class_absent,gpu_artisan_symbol_present,kv_cache_symbol_present
GPU_PROMOTION_BLOCKER=true
GPU_ROOT_CAUSE_CANDIDATE=runtime_decode_fragmentation
NEXT_ACTION=compare_edge_gallery_native_internal_executor_selection_and_public_api_gap
```

Generate the full report after APK/native comparison:

```bash
scripts/render_gpu_investigation_report.sh \
  --device-runs artifacts/device_runs \
  --quality-matrix artifacts/gpu_output_quality_matrix \
  --apk-native-diff artifacts/apk_native_diff \
  --output artifacts/gpu_investigation_report/GPU_INVESTIGATION_REPORT.md
```
