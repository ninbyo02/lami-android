# GPU Executor Probe Result Classification

Scope: parser and decision support only. This does not change Kotlin routes,
runtime selection, UI streaming, callback joining, model loading, or promotion
policy.

## Purpose

After running `edge_gallery_executor_probe` on device, paste or save the copied
compact/details diagnostics and classify the result mechanically instead of
reading every key by hand.

Classifier:

```bash
scripts/classify_gpu_executor_probe_result.sh --input artifacts/device_runs/executor_probe.txt
```

Optional baseline comparison:

```bash
scripts/classify_gpu_executor_probe_result.sh \
  --baseline artifacts/device_runs/baseline_probe.txt \
  --input artifacts/device_runs/executor_probe.txt
```

Self-test:

```bash
scripts/classify_gpu_executor_probe_result.sh --self-test
```

## Expected Diagnostics Keys

The classifier reads these keys when present:

- `edge_gallery_executor_probe_result`
- `edge_gallery_executor_difference_summary`
- `executor_selection_fingerprint`
- `runtime_backend_fingerprint`
- `runtime_executor_fingerprint`
- `runtime_dispatch_fingerprint`
- `runtime_compiled_model_fingerprint`
- `loaded_native_runtime_stack_fingerprint`
- `gpu_output_quality_candidate_result`
- `callback_corruption_earliest_stage`
- `gpu_output_source_corruption_stage`
- `gpu_sampler_root_cause_candidate`
- `gpu_output_quality_gate_status`
- `gpu_output_quality_promotion_blocker`

The parser accepts both one-key-per-line diagnostics and long summary lines such
as:

```text
source_summary=... edge_gallery_executor_probe_result=same_sampler_different_executor gpu_output_quality_candidate_result=quality_candidate_fail callback_corruption_earliest_stage=raw_callback ...
```

## Output

The script prints:

```text
GPU_EXECUTOR_PROBE_CLASSIFICATION=...
GPU_EXECUTOR_PROBE_REASON=...
GPU_PROMOTION_BLOCKER=...
GPU_ROOT_CAUSE_CANDIDATE=...
NEXT_ACTION=...
```

## Classifications

| Classification | Meaning |
| --- | --- |
| `same_stack_same_executor_raw_callback_corrupt` | Same runtime/executor candidate still corrupts raw callback. Keep promotion blocked and collect raw callback artifacts. |
| `same_stack_different_executor` | Runtime stack appears aligned enough, but executor fingerprint/probe result points to a different executor path. |
| `different_runtime_stack` | `loaded_native_runtime_stack_fingerprint` differs from baseline or probe reports a runtime-stack mismatch. |
| `executor_probe_unavailable` | Required executor probe result is unavailable or unknown. Rerun with the correct matrix mode. |
| `callback_corruption_confirmed` | Quality failed and corruption starts at `raw_callback`. |
| `quality_gate_pass` | Quality gate passed in the provided diagnostics. Repeat stability tests before any promotion discussion. |
| `unknown` | Not enough data to classify. |

## Promotion Blocker Rules

`GPU_PROMOTION_BLOCKER=true` when any of these are present:

- `gpu_output_quality_promotion_blocker=true`
- `gpu_output_quality_gate_status=fail`
- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`

If quality fails at raw callback and
`gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`, the root cause
candidate is reported as:

```text
GPU_ROOT_CAUSE_CANDIDATE=runtime_decode_fragmentation
```

## Device Procedure

Run the probe on `standardGpuMinimalRuntimeCandidateDebug`:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell setprop debug.lami.gpu_output_quality_matrix_mode edge_gallery_executor_probe
adb shell setprop debug.lami.gpu_output_quality_max_tokens 512
adb shell monkey -p io.github.ninbyo02.lami.gpustandardminimal 1
```

Prompt:

```text
カレーの材料をお願いします。
```

Save copied diagnostics, for example:

```text
artifacts/device_runs/gpu_executor_probe_2026-06-15.txt
```

Then classify:

```bash
scripts/classify_gpu_executor_probe_result.sh \
  --input artifacts/device_runs/gpu_executor_probe_2026-06-15.txt
```

If you have a previous baseline diagnostics file, include it:

```bash
scripts/classify_gpu_executor_probe_result.sh \
  --baseline artifacts/device_runs/gpu_executor_baseline_2026-06-15.txt \
  --input artifacts/device_runs/gpu_executor_probe_2026-06-15.txt
```

For a more detailed baseline/probe diff, use:

```bash
scripts/compare_runtime_fingerprints.sh \
  --baseline artifacts/device_runs/gpu_executor_baseline_2026-06-15.txt \
  --probe artifacts/device_runs/gpu_executor_probe_2026-06-15.txt
```

## Decision Criteria

| Result | Next action |
| --- | --- |
| `different_runtime_stack` | Compare loaded library SHA-256 and do not promote standard GPU. |
| `same_stack_different_executor` | Investigate internal executor/backend selection, RuntimeConfig, backend constraints, and GPU_ARTISAN evidence. |
| `callback_corruption_confirmed` | Keep promotion blocked; collect raw callback artifacts and compare against Edge Gallery runtime path. |
| `quality_gate_pass` | Repeat app restart, short/medium/long, and multi-turn stability before any next promotion phase. |
| `executor_probe_unavailable` | Rerun with `debug.lami.gpu_output_quality_matrix_mode=edge_gallery_executor_probe`. |
