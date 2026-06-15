# GPU Artisan Executor Probe Plan

This is a plan for future DEV-only probes. It does not implement reflection,
does not force internal LiteRT-LM config, and does not change production,
standardDebug, CPU, NPU, or normal GPU behavior.

## Why This Probe Exists

Current device evidence says LAMI and Edge Gallery are no longer separated by
model identity, sampler, max token budget, public `ConversationConfig`, or UI
append behavior. The strongest result is:

```text
GPU_EXECUTOR_PROBE_CLASSIFICATION=same_stack_different_executor
GPU_EXECUTOR_PROBE_REASON=edge_gallery_executor_probe_result=same_sampler_different_executor
GPU_PROMOTION_BLOCKER=true
GPU_ROOT_CAUSE_CANDIDATE=runtime_decode_fragmentation
NEXT_ACTION=inspect_runtime_backend_executor_fingerprints_and_edge_gallery_internal_selector
```

LAMI public `Backend.GPU` can invoke the model but long outputs corrupt at
`raw_callback`. Edge Gallery GPU output is normal. Therefore the next safe
question is whether Edge Gallery reaches an internal GPU executor path such as
`GPU_ARTISAN` / `LlmGpuArtisanExecutor` that LAMI does not reach.

## Promotion Blockers

These remain hard blockers:

- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
- `gpu_output_quality_promotion_blocker=true`

No probe result should be treated as production-ready while these remain true.

## Candidate Probe Order

### 1. API Surface Inventory

Status: safe and implemented by `scripts/inspect_litertlm_api_surface.sh`.

Expected evidence:

- Public AAR exposes only `Backend.CPU/GPU/NPU`, `EngineConfig`,
  `ConversationConfig`, and `SamplerConfig`.
- Internal names appear only in native/static strings.

Success condition:

- Inventory can be regenerated and confirms whether classes/methods are public
  or string-only.

Failure condition:

- Missing AAR/APK artifacts are reported as nonfatal missing inputs.

Rollback:

- Delete generated artifacts under `artifacts/litertlm_api_surface/`.

### 2. GPU_ARTISAN / LlmGpuArtisanExecutor Selection Probe

Status: future DEV-only design candidate.

Expected diagnostics:

- `gpu_artisan_probe_requested`
- `gpu_artisan_probe_surface_available`
- `gpu_artisan_probe_invocation_enabled=false` for initial dry runs
- `gpu_artisan_probe_class_or_symbol_source`
- `gpu_artisan_probe_result`
- `executor_selection_fingerprint`
- `runtime_executor_fingerprint`

Success condition:

- Dry-run evidence identifies a safe, isolated way to observe or request the
  artisan executor without modifying standard behavior.

Failure condition:

- Surface remains native-only or requires unsafe private invocation.

Rollback:

- Clear the DEV property and remove the probe flavor/staging if created.

Risk:

- High if it attempts to apply hidden config. Initial work must be diagnostic
  only.

### 3. RuntimeConfig Probe

Status: future DEV-only dry-run candidate.

Expected diagnostics:

- `runtime_config_probe_surface_available`
- `runtime_config_probe_public_class_present`
- `runtime_config_probe_native_only_evidence`
- `runtime_config_probe_result`

Success condition:

- A public or safely reflectable read-only surface is found.

Failure condition:

- Only native strings exist (`GetRuntimeConfig not implemented for backend:`).

Rollback:

- Disable DEV property; do not persist config.

Risk:

- High if used to mutate config. Read-only inventory only until a separate
  review approves an isolated implementation.

### 4. BackendConstraint Probe

Status: future DEV-only dry-run candidate.

Expected diagnostics:

- `backend_constraint_probe_surface_available`
- `backend_constraint_probe_model_hint`
- `backend_constraint_probe_runtime_hint`
- `backend_constraint_probe_result`

Success condition:

- Probe explains whether model/runtime metadata can trigger GPU to GPU_ARTISAN
  switching.

Failure condition:

- No public class or metadata reader is available.

Rollback:

- Disable DEV property; keep existing model selection unchanged.

Risk:

- Medium-high. It must not reject CPU/GPU model loading in normal routes.

### 5. PreferredEngineType Probe

Status: future DEV-only dry-run candidate.

Expected diagnostics:

- `preferred_engine_type_probe_surface_available`
- `preferred_engine_type_probe_candidate`
- `preferred_engine_type_probe_result`

Success condition:

- Identifies a selector that correlates with `runtime_executor_fingerprint`.

Failure condition:

- No public or read-only surface exists.

Risk:

- Medium-high if it changes executor selection; safe only as inventory first.

### 6. GpuOptions Probe

Status: future DEV-only dry-run candidate.

Expected diagnostics:

- `gpu_options_probe_surface_available`
- `gpu_options_probe_toml_evidence`
- `gpu_options_probe_result`

Success condition:

- Confirms whether `LrtCreateGpuOptionsFromToml` is configurable above native
  code.

Failure condition:

- Native string only; no public Java/Kotlin API.

Risk:

- Medium. Explicit GPU options may alter runtime behavior and must stay in an
  isolated flavor if ever applied.

### 7. GPU KV Cache / Decode Cache Probe

Status: future diagnostic candidate.

Expected diagnostics:

- `gpu_kv_cache_probe_evidence`
- `gpu_decode_cache_probe_evidence`
- `runtime_compiled_model_fingerprint`
- `runtime_executor_fingerprint`

Success condition:

- Fingerprint or logs differentiate Edge Gallery's decode cache path from
  LAMI's public GPU path.

Failure condition:

- No observable signal beyond native strings.

Risk:

- Medium; no direct mutation should be added in this phase.

### 8. Native Generate API Shape Probe

Status: lower priority.

Already observed:

- `edge_gallery_final_response_probe_result=pass`
- `edge_gallery_final_response_probe_difference_summary=last_non_empty_callback_is_delta_not_final_response`

This makes callback final-response semantics less likely than executor/decode
path mismatch.

### 9. Public Sampler / MaxTokens / CacheDir

Status: lowest priority.

Evidence:

- Baseline, collect-only, no sampling acceleration, and max token matrix still
  produced `quality_candidate_fail`.
- Current classifier says `same_sampler_different_executor`.

## Safe Next Implementation Boundary

The next implementation, if requested later, should be limited to:

- A DEV-only property or isolated flavor.
- Read-only class/method inventory in diagnostics.
- Fingerprint comparison before and after the probe.
- No internal selector invocation until the inventory proves a safe surface.

It must not:

- Force `GPU_ARTISAN`.
- Invoke hidden native APIs directly.
- Replace native libraries.
- Change callback join/UI append.
- Alter CPU, NPU, fallback, or production GPU defaults.

## Read-Only Surface Probe

Before any reflection application is considered, run the read-only internal
surface probe:

```bash
adb shell setprop debug.lami.gpu_internal_surface_probe true
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

Keys to copy:

```text
gpu_internal_surface_probe_enabled
gpu_internal_surface_probe_result
gpu_internal_runtime_config_class_present
gpu_internal_backend_constraint_class_present
gpu_internal_preferred_engine_type_class_present
gpu_internal_gpu_options_class_present
gpu_internal_artisan_class_present
gpu_internal_llm_gpu_artisan_executor_symbol_present
gpu_internal_kv_cache_symbol_present
edge_gallery_executor_probe_result
edge_gallery_executor_difference_summary
gpu_output_quality_promotion_blocker
```

Success for this diagnostic phase:

- The probe emits keys without changing inference status.
- Hidden Java/Kotlin class presence is clearly separated from native string
  evidence.
- Exceptions are reported in `gpu_internal_probe_exception_*` and do not change
  the route result.

Failure for this diagnostic phase:

- The probe cannot emit keys in the candidate flavor.
- It changes route status, holder lifecycle, callback streaming, or output text.

Reflection application is still blocked because the current public AAR
inventory shows the likely selector surfaces as native/internal evidence rather
than stable Java/Kotlin APIs. Applying those surfaces without a separate
DEV-only design could accidentally change executor selection, model loading, or
decode behavior in ways that are hard to roll back.

## Manual Diagnostics To Compare

When a future probe exists, compare:

```text
edge_gallery_executor_probe_result
edge_gallery_executor_difference_summary
executor_selection_fingerprint
runtime_backend_fingerprint
runtime_executor_fingerprint
runtime_dispatch_fingerprint
runtime_compiled_model_fingerprint
loaded_native_runtime_stack_fingerprint
gpu_output_quality_candidate_result
callback_corruption_earliest_stage
gpu_output_source_corruption_stage
gpu_sampler_root_cause_candidate
gpu_output_quality_promotion_blocker
```

Expected useful outcome:

- Fingerprints move toward Edge Gallery and quality passes. This would support
  an internal executor mismatch root cause.

Expected blocker outcome:

- Fingerprints change but `raw_callback` corruption remains. This would shift
  suspicion toward decode cache/runtime behavior rather than selector alone.
