# GPU Executor Root Cause Next Actions

Scope: investigation planning only. CPU, NPU, GPU runtime behavior, model
loading, callback joining, and production defaults remain unchanged.

## Current Static Conclusion

The strongest static evidence points to an Edge Gallery internal GPU executor or
runtime selection path that Lami's public `Backend.GPU` route cannot currently
prove or force.

The current most likely root cause is:

```text
Edge Gallery reaches a different internal LiteRT-LM GPU executor/runtime decode
path than Lami public Backend.GPU, probably involving GPU_ARTISAN, backend
constraints, RuntimeConfig, preferred engine selection, or GPU KV-cache decode.
```

Current confidence: **72%**.

This is not yet proven at runtime. Static strings prove capability and likely
selector logic, not actual selection in the successful Edge Gallery run.

## Root Cause Ranking

| Rank | Candidate | Confidence | Evidence | Counter-evidence / gap |
| ---: | --- | ---: | --- | --- |
| 1 | Edge Gallery selects `GPU_ARTISAN` / `LlmGpuArtisanExecutor` or another internal executor while Lami selects public compiled-model GPU. | 72% | Edge Gallery native strings include `GPU_ARTISAN`, `LlmGpuArtisanExecutor::Create`, `Prefill`, and `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.` Lami public reflection exposes only `CPU,GPU,NPU`. Lami raw callback corrupts while Edge Gallery GPU does not. | Static strings do not prove the successful Edge Gallery run selected artisan. |
| 2 | Hidden `RuntimeConfig`, backend constraint, or preferred engine type differs. | 66% | Static strings include runtime config and backend constraint matching/mismatch messages. Lami has no public setter for these. | The exact selected values are not visible statically. |
| 3 | GPU KV-cache / decode-cache path differs. | 56% | Static strings include `tflite_gpu_kv_cache` and `tflite_opencl_kv_cache`; Lami failure signature is `runtime_decode_fragmentation`. | No direct runtime KV-cache selector has been observed in Lami public API. |
| 4 | Native runtime stack still differs from Edge Gallery even in minimal candidate. | 46% | `standardDebug` fails invoke; minimal pair invokes but corrupts. Edge Gallery official app may ship a fuller coherent runtime stack or use app/runtime pairing differently. | Minimal pair already uses the two core libs that made GPU invoke succeed, so native stack alone is not sufficient as a simple explanation. |
| 5 | Callback semantics / hidden aggregation layer differs. | 28% | Edge Gallery UI is normal and Lami raw callback is fragmented. | Lami final-response probe indicates last non-empty callback is delta, and accumulated raw callback is already corrupt. |
| 6 | Sampler setting difference. | 18% | Sampler affects decode distribution and Edge Gallery has topK/topP/temp allowlist values. | Baseline, collect-only, and no-sampling-acceleration matrix runs all fail; Lami can mimic topK=64/topP=0.95/temp=1.0. |
| 7 | maxTokens / cacheDir / public ConversationConfig mismatch. | 16% | Historically important config deltas existed. | Current parity modes cover these axes and still show raw callback corruption. |
| 8 | Model file difference. | 8% | Model metadata can drive backend selection. | Edge Gallery model identity has been validated; Lami CPU succeeds with the same model family and Lami GPU short prompts can pass. |

## Confirmed Without Device In This Pass

- Edge Gallery static artifacts contain internal executor/backend evidence.
- The most important strings are in `liblitertlm_jni.so`, not app-level code.
- Lami current implementation uses public `EngineConfig`, `ConversationConfig`,
  `SamplerConfig`, and `Backend.GPU`.
- Lami diagnostics already expose fingerprint keys suitable for the next
  runtime comparison.
- No production or runtime behavior change is needed to continue investigation.

## Latest Device Internal Surface Result

The read-only internal surface probe completed in
`standardGpuMinimalRuntimeCandidateDebug`:

```text
gpu_internal_surface_probe_enabled=true
gpu_internal_surface_probe_result=completed
gpu_internal_surface_probe_disabled_reason=none
gpu_internal_runtime_config_class_present=false
gpu_internal_backend_constraint_class_present=false
gpu_internal_preferred_engine_type_class_present=false
gpu_internal_gpu_options_class_present=false
gpu_internal_artisan_class_present=false
gpu_internal_llm_gpu_artisan_executor_symbol_present=true
gpu_internal_kv_cache_symbol_present=true
```

The same run retained the quality blocker:

```text
edge_gallery_executor_probe_result=same_sampler_different_executor
gpu_output_quality_candidate_result=quality_candidate_fail
gpu_output_quality_promotion_blocker=true
gpu_sampler_root_cause_candidate=runtime_decode_fragmentation
callback_corruption_earliest_stage=raw_callback
```

Interpretation:

- Lami public `Backend.GPU` can load the minimal runtime and invoke GPU decode.
- Long-output corruption is still a raw callback/runtime decode failure.
- Public class surfaces for the hidden selector/config objects are absent.
- Native/internal symbols for `LlmGpuArtisanExecutor` and GPU KV-cache are
  present.
- The next concrete comparison is Edge Gallery APK vs Lami APK native/internal
  surface fingerprints:

```bash
scripts/compare_edge_gallery_and_lami_apk.sh \
  --edge-gallery path/to/edge_gallery.apk \
  --lami-apk path/to/lami.apk
```

Look for:

- `EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT`
- `LAMI_INTERNAL_SURFACE_FINGERPRINT`
- `INTERNAL_SURFACE_DIFF_SUMMARY`

Latest APK comparison:

```text
EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT=34af6f258570c911a9bbb14763e6b267fa33b432324e4c30752502f88af56dd4
LAMI_INTERNAL_SURFACE_FINGERPRINT=3fa6528634ecf90fdec7931523a4b3b0f8050c2f77aaa5ffa72794e9276fb253
INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface
RUNTIME_STACK_DIFF_SUMMARY=different_runtime_stack
JNI_SYMBOL_DIFF_SUMMARY=different_jni_surface
EXECUTOR_SYMBOL_DIFF_SUMMARY=different_executor_symbols
```

This does not prove the exact selected executor, but it narrows the next
investigation to native/internal surface and selector-path differences rather
than sampler, max tokens, public `ConversationConfig`, or UI joining.

## Next Device Actions

### 1. Capture Lami executor probe diagnostics

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

Copy compact/details diagnostics and check:

- `edge_gallery_executor_probe_result`
- `edge_gallery_executor_difference_summary`
- `executor_selection_fingerprint`
- `runtime_backend_fingerprint`
- `runtime_executor_fingerprint`
- `runtime_dispatch_fingerprint`
- `runtime_compiled_model_fingerprint`
- `engine_config_fingerprint`
- `conversation_config_fingerprint`
- `sampler_config_fingerprint`
- `loaded_native_runtime_stack_fingerprint`
- `loaded_native_libs_sha256`
- `gpu_output_quality_candidate_result`
- `callback_corruption_earliest_stage`
- `gpu_sampler_root_cause_candidate`

### 2. Compare copied diagnostics

Save the copied `GPU診断キー` and `GPU内部surfaceキー` output together:

```bash
mkdir -p artifacts/device_runs
cat > artifacts/device_runs/gpu_executor_probe_latest.txt <<'EOF'
[GPU diagnostic keys]
edge_gallery_executor_probe_result=same_sampler_different_executor
edge_gallery_executor_difference_summary=same_sampler_lami_runtime_decode_fragmentation_executor_selection_suspected
gpu_output_quality_candidate_result=quality_candidate_fail
gpu_output_quality_gate_status=fail
gpu_output_quality_promotion_blocker=true
gpu_sampler_root_cause_candidate=runtime_decode_fragmentation
gpu_output_source_corruption_stage=raw_callback
callback_corruption_earliest_stage=raw_callback

[GPU internal surface keys]
gpu_internal_surface_probe_enabled=true
gpu_internal_surface_probe_result=completed
gpu_internal_surface_probe_disabled_reason=none
gpu_internal_runtime_config_class_present=false
gpu_internal_backend_constraint_class_present=false
gpu_internal_preferred_engine_type_class_present=false
gpu_internal_gpu_options_class_present=false
gpu_internal_artisan_class_present=false
gpu_internal_llm_gpu_artisan_executor_symbol_present=true
gpu_internal_kv_cache_symbol_present=true
EOF
```

Classify:

```bash
scripts/classify_gpu_executor_probe_result.sh \
  --input artifacts/device_runs/gpu_executor_probe_latest.txt
```

Expected:

```text
GPU_EXECUTOR_PROBE_CLASSIFICATION=same_stack_different_executor
GPU_INTERNAL_SURFACE_EVIDENCE=runtime_config_class_absent,backend_constraint_class_absent,preferred_engine_type_class_absent,gpu_options_class_absent,artisan_class_absent,gpu_artisan_symbol_present,kv_cache_symbol_present
GPU_PROMOTION_BLOCKER=true
GPU_ROOT_CAUSE_CANDIDATE=runtime_decode_fragmentation
NEXT_ACTION=compare_edge_gallery_native_internal_executor_selection_and_public_api_gap
```

```bash
scripts/compare_runtime_fingerprints.sh --baseline <baseline-diag.txt> --probe <executor-probe-diag.txt>
```

Expected high-value outcomes:

| Output | Meaning |
| --- | --- |
| `RUNTIME_STACK_DIFFERENCE_SUMMARY=different_runtime_stack` | Native stack mismatch remains a strong candidate. |
| `EXECUTOR_DIFFERENCE_SUMMARY=different_executor_fingerprint` | Lami modes are reaching different executor/config fingerprints. |
| `LIKELY_ROOT_CAUSE=runtime_decode_or_executor_selection` | Raw callback corruption still points below app-layer joining. |

### 3. Refresh static trace after APK updates

```bash
scripts/static_trace_edge_gallery_executor.sh
```

Inspect:

- `artifacts/static_edge_gallery_executor_trace/native_string_keyword_hits.txt`
- `artifacts/static_edge_gallery_executor_trace/apk_dex_keyword_hits.txt`
- `artifacts/static_edge_gallery_executor_trace/native_needed_libraries.txt`

### 4. If Edge Gallery logs are available later

Use non-invasive log filtering for native strings already identified statically:

- `Artisan model detected`
- `LlmGpuArtisanExecutor::Create`
- `backend constraint is matched`
- `GetRuntimeConfig`
- `tflite_gpu_kv_cache`
- `tflite_opencl_kv_cache`
- `CompiledModelExecutor`

Do not modify the Edge Gallery APK.

## Promotion Blockers That Remain Active

Standard GPU promotion remains blocked while any of these are true:

- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `gpu_output_quality_promotion_blocker=true`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
- `edge_gallery_parity_difference_summary=edge_gallery_gpu_ok_lami_gpu_raw_callback_decode_fragmentation`
- Edge Gallery official GPU continues to produce clean long output for the same
  prompt/model class while Lami GPU corrupts raw callback source.

## Recommended Decision Path

1. Keep CPU as the stable default.
2. Keep `standardGpuMinimalRuntimeCandidateDebug` as DEV-only.
3. Use `edge_gallery_executor_probe` and runtime fingerprints to determine if
   Lami public GPU can ever select the same internal path.
4. If Edge Gallery runtime selection is confirmed to be artisan/internal-only,
   do not use reflection hacks. Design a separate isolated DEV experiment and
   complete license/packaging review before any standard integration proposal.
5. Do not promote a UI GPU toggle until long-output quality passes without raw
   callback corruption.
