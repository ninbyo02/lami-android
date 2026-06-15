# GPU Corruption Regression Execution Guide

Scope: manual device run guide. Do not change production/default route behavior
while running this suite.

## Prepare Prompt Matrix

```bash
scripts/generate_gpu_regression_matrix.sh
```

Prompts are written to:

```text
artifacts/gpu_regression_matrix/
```

## Common GPU Probe Properties

Use these for `standardGpuMinimalRuntimeCandidateDebug` GPU quality checks:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell setprop debug.lami.gpu_output_quality_matrix_mode baseline
adb shell setprop debug.lami.gpu_output_quality_max_tokens 512
adb shell monkey -p io.github.ninbyo02.lami.gpustandardminimal 1
```

Use the Edge Gallery E2B model and select backend `GPU`.

## CPU Comparison

For CPU, keep GPU-only experimental probes off and select backend `CPU` in the
app:

```bash
adb shell setprop debug.lami.gpu_callback_raw_passthrough false
adb shell setprop debug.lami.compare_cpu_gpu_callback false
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming false
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami.gpustandardminimal 1
```

Save copied diagnostics with names such as:

```text
artifacts/device_runs/cpu_short_greeting.txt
artifacts/device_runs/cpu_long_500chars.txt
```

## GPU Run

Use backend `GPU` and save copied diagnostics with names such as:

```text
artifacts/device_runs/gpu_short_greeting.txt
artifacts/device_runs/gpu_long_500chars.txt
```

## NPU Run

NPU is not the current investigation target. If NPU diagnostics are collected
later, keep them separate and gated. Save copied diagnostics with names such as:

```text
artifacts/device_runs/npu_short_greeting.txt
```

Do not modify NPU S1 route as part of this suite.

## Suggested Run Order

1. `short_greeting.txt`
2. `short_self_intro.txt`
3. `medium_curry.txt`
4. `medium_holiday.txt`
5. `long_300chars.txt`
6. `long_500chars.txt`
7. `markdown_bullets.txt`
8. `markdown_numbered.txt`
9. `markdown_table.txt`
10. `mixed_ja_en.txt`
11. `mixed_symbols.txt`

For each prompt:

1. Copy the prompt text from `artifacts/gpu_regression_matrix/`.
2. Run generation.
3. Open inference stats/details.
4. Copy compact/details diagnostics.
5. Save the diagnostics under `artifacts/device_runs/`.

## Summarize Results

```bash
scripts/summarize_gpu_regression_results.sh --input artifacts/device_runs
```

Output:

```text
artifacts/device_runs/GPU_CORRUPTION_REGRESSION_SUMMARY.md
```

## Expected Classifications

| Classification | Meaning |
| --- | --- |
| `same_behavior_cpu_gpu` | CPU and GPU both fail quality. Suspect prompt/model content before GPU-specific conclusions. |
| `gpu_only_corrupt` | CPU passes while GPU fails. Strong GPU runtime/executor/callback-source evidence. |
| `long_text_only_corrupt` | Long prompts fail while Markdown prompts do not. Length/decode accumulation is likely. |
| `markdown_only_corrupt` | Markdown/list/table prompts fail more selectively. Formatting/token pattern sensitivity is likely. |
| `always_corrupt` | Every GPU prompt fails. Runtime path is broadly unsuitable. |
| `gpu_quality_pass` | GPU passes collected prompts. Repeat with longer and multi-turn prompts before changing promotion status. |

## Promotion Blocker Conditions

Keep standard GPU promotion blocked if any regression run shows:

- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `gpu_output_quality_gate_status=fail`
- `gpu_output_quality_promotion_blocker=true`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
