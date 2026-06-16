# LiteRT-LM GPU Internal Executor Surface

This note records what LAMI can currently configure through the visible
LiteRT-LM Android API, what Edge Gallery exposes only through native/static
evidence, and which surfaces are safe to inspect before any DEV-only executor
probe is implemented.

No runtime behavior is changed by this document. The current standard GPU
promotion blocker remains in force.

## Phase 11 Promotion Gate

Current decision:

```text
GPU_PROMOTION_DECISION=blocked
GPU_PROMOTION_DECISION_REASON=raw_callback_corruption_and_public_api_gap
GPU_SAFE_NEXT_ACTION=keep_gpu_experimental_return_focus_to_cpu_stable_and_npu_route
```

GPU remains DEV-only experimental / diagnostics-only. The reason is not simple
invoke failure anymore: Lami minimal GPU invokes, but long output corrupts at
raw callback source with `severe_fragmentation`, while CPU route succeeds and
Edge Gallery GPU does not show the same corruption. The current blocker is the
combination of raw callback quality failure and no safe public selector API for
the internal executor path that Edge Gallery appears to reach.

CPU remains the stable usable local route. NPU route work should resume as the
main acceleration track while GPU waits for upstream public API or additional
safe selector evidence.

## Current Device Evidence

The latest `edge_gallery_executor_probe` run classified the LAMI GPU failure as
an executor/path mismatch rather than sampler or public conversation settings:

```text
edge_gallery_executor_probe_result=same_sampler_different_executor
edge_gallery_executor_difference_summary=same_sampler_lami_runtime_decode_fragmentation_executor_selection_suspected
executor_selection_fingerprint=37afdf11bc85841d
runtime_backend_fingerprint=e494e00a0fa25d03
runtime_executor_fingerprint=2bd1a569c71cc797
runtime_dispatch_fingerprint=25c3ff71a4a977ec
runtime_compiled_model_fingerprint=21598a5915927f45
gpu_output_quality_candidate_result=quality_candidate_fail
callback_corruption_earliest_stage=raw_callback
gpu_output_source_corruption_stage=raw_callback
gpu_sampler_root_cause_candidate=runtime_decode_fragmentation
gpu_output_quality_promotion_blocker=true
```

The classifier result was:

```text
GPU_EXECUTOR_PROBE_CLASSIFICATION=same_stack_different_executor
GPU_PROMOTION_BLOCKER=true
GPU_ROOT_CAUSE_CANDIDATE=runtime_decode_fragmentation
NEXT_ACTION=inspect_runtime_backend_executor_fingerprints_and_edge_gallery_internal_selector
```

This keeps the strongest hypothesis at: LAMI public `Backend.GPU` reaches a
different internal executor/runtime decode path than Edge Gallery.

## API Surface Inventory

Run:

```bash
scripts/inspect_litertlm_api_surface.sh
```

The script writes:

```text
artifacts/litertlm_api_surface/class_inventory.txt
artifacts/litertlm_api_surface/method_inventory.txt
artifacts/litertlm_api_surface/reflection_candidate_methods.txt
artifacts/litertlm_api_surface/gpu_executor_candidate_symbols.txt
artifacts/litertlm_api_surface/api_surface_summary.txt
artifacts/litertlm_api_surface/public_api_inventory.txt
artifacts/litertlm_api_surface/internal_native_token_inventory.txt
artifacts/litertlm_api_surface/public_api_gap_summary.txt
```

The current local inventory found the expected public classes in the Maven AARs:

- `Backend`, with public nested `CPU`, `GPU`, and `NPU`.
- `Engine` and `EngineConfig`.
- `Conversation` and `ConversationConfig`.
- `SamplerConfig`.

Current public API inventory:

```text
PUBLIC_BACKEND_API_AVAILABLE=true
PUBLIC_BACKEND_GPU_API_AVAILABLE=true
PUBLIC_ENGINE_CONFIG_API_AVAILABLE=true
PUBLIC_CONVERSATION_CONFIG_API_AVAILABLE=true
PUBLIC_SAMPLER_CONFIG_API_AVAILABLE=true
PUBLIC_RUNTIME_CONFIG_API_AVAILABLE=false
PUBLIC_BACKEND_CONSTRAINT_API_AVAILABLE=false
PUBLIC_PREFERRED_ENGINE_TYPE_API_AVAILABLE=false
PUBLIC_GPU_OPTIONS_API_AVAILABLE=false
PUBLIC_ARTISAN_API_AVAILABLE=false
```

It did not find Java-visible target classes for the selector/config surfaces:

- `RuntimeConfig`
- `RuntimeConfig$Builder`
- `ExecutorConfig`
- `ExecutorSelection`
- `BackendConstraint`
- `PreferredEngineType`
- `GpuOptions`
- `LlmGpuArtisanExecutor`

Native/internal token inventory still reports those concepts as present:

```text
NATIVE_GPU_ARTISAN_SYMBOL_PRESENT=true
NATIVE_KV_CACHE_SYMBOL_PRESENT=true
NATIVE_RUNTIME_CONFIG_TOKEN_PRESENT=true
NATIVE_BACKEND_CONSTRAINT_TOKEN_PRESENT=true
NATIVE_PREFERRED_ENGINE_TOKEN_PRESENT=true
NATIVE_GPU_OPTIONS_TOKEN_PRESENT=true
NATIVE_GENERATE_STREAM_TOKEN_PRESENT=true
NATIVE_PREFILL_DECODE_TOKEN_PRESENT=true
PUBLIC_API_GAP_SUMMARY=public_selector_api_absent_native_executor_symbols_present
```

The same concepts are visible in native strings/static artifacts, so they should
be treated as native/internal evidence, not a public Kotlin/Java API surface.
The gap is now explicit: Lami can see symbols/tokens for the internal executor
family but cannot safely select it through public Java/Kotlin classes.

## Phase 9 Native/Internal Trace Evidence

The latest APK/native comparison and executor-selection token trace produce two
separate signals:

```text
INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface
EXECUTOR_SELECTION_TRACE_DIFF_SUMMARY=different_executor_selection_tokens
```

The executor token trace found:

```text
EDGE_ONLY_EXECUTOR_TOKENS=none
LAMI_ONLY_EXECUTOR_TOKENS=BackendConstraint,PreferredEngineType
COMMON_EXECUTOR_TOKENS=GPU_ARTISAN,LlmGpuArtisanExecutor,RuntimeConfig,GpuOptions,LrtCreateGpuOptionsFromToml,tflite_gpu_kv_cache,kv_cache,nativeGenerateContentStream,nativeRunPrefill,nativeRunDecode,CompiledModelExecutor,LlmLiteRtCompiledModelExecutor
```

This refines the interpretation:

- Both APK surfaces contain static evidence for `GPU_ARTISAN`,
  `LlmGpuArtisanExecutor`, `RuntimeConfig`, `GpuOptions`, GPU KV-cache strings,
  and native generate/decode entry points.
- Lami runtime diagnostics still report Java/Kotlin class absence for
  `RuntimeConfig`, `BackendConstraint`, `PreferredEngineType`, `GpuOptions`,
  and `Artisan`.
- Native symbol/string presence therefore means the runtime contains capability,
  not that Lami public `Backend.GPU` can select or configure that path.
- `different_internal_surface` remains important because the aggregate
  native/internal fingerprint differs even when many high-value tokens are
  common.
- The strongest next question is not "does the string exist?" but "which
  selector path chooses it during decode?"
- `PUBLIC_API_GAP_SUMMARY=public_selector_api_absent_native_executor_symbols_present`
  is the current expected classifier/report summary for this state.

## LAMI Public GPU Surface

The current LAMI route can configure these public settings:

| Surface | Current LAMI access | Notes |
| --- | --- | --- |
| Backend | `EngineConfig(..., backend = Backend.GPU(), ...)` | Public, stable. |
| Vision/audio backend | Public `EngineConfig` constructor fields | Edge-gallery-like GPU mode can null these where supported. |
| Max tokens | Public `EngineConfig` max token field | Tested across several budgets; not the root cause by itself. |
| Cache dir | Public `EngineConfig` cache dir field | `null` and app-files cache were tested; not sufficient. |
| Sampler | Public `ConversationConfig(SamplerConfig(topK, topP, temperature, seed))` | Edge Gallery allowlist values were tested. |
| Callback streaming | Public `generateResponse` callback path used by LAMI | Raw callback corruption happens before UI append. |

The public AAR surface does not expose a stable way to select
`GPU_ARTISAN`, `LlmGpuArtisanExecutor`, `RuntimeConfig`, backend constraints,
preferred engine type, or GPU options.

## Edge Gallery Internal Evidence

Static Edge Gallery and LiteRT-LM native evidence includes:

- `GPU_ARTISAN`, `CPU_ARTISAN`, and `GOOGLE_TENSOR_ARTISAN`.
- `LlmGpuArtisanExecutor::Create with the following config:`
- `LlmGpuArtisanExecutor::Prefill with the following arguments:`
- `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.`
- `. Supported backends are: [CPU, GPU, NPU, GPU_ARTISAN, CPU_ARTISAN, GOOGLE_TENSOR_ARTISAN]`
- `TF_LITE_PREFILL_DECODE model is expected to exist when not using GPU_ARTISAN backend. But it is null.`
- `GetRuntimeConfig not implemented for backend:`
- `LrtCreateGpuOptionsFromToml`
- `tflite_gpu_kv_cache` / `tflite_opencl_kv_cache`
- JNI exports for `nativeGenerateContentStream`, `nativeRunPrefill`, and
  `nativeRunDecode`.

These strings strongly suggest the native runtime can choose an internal
artisan executor and GPU KV-cache path that are not selectable through LAMI's
current public `Backend.GPU` API.

## Safe And Unsafe Surfaces

Safe now:

- Static API inventory from local AARs/APKs.
- Runtime diagnostics/fingerprints already emitted by DEV-only probes.
- DEV-only class/method presence reporting.
- DEV-only diagnostics that record whether internal classes are absent/present.

Use caution:

- Reflection discovery of constructors/method names without invoking them.
- Dry-run planning for a future probe flavor that would attempt hidden config.

Unsafe for the current phase:

- Forcing `GPU_ARTISAN` through reflection.
- Invoking hidden `RuntimeConfig`, `BackendConstraint`, or
  `PreferredEngineType` setters.
- Constructing hidden `BackendConstraint` / `PreferredEngineType` /
  `GpuOptions` objects and injecting them into the public route.
- Calling native methods directly.
- Replacing native libraries in standardDebug.
- Changing production/default GPU routing.
- Repairing or filtering callback text to hide raw callback corruption.
- Promoting GPU UI/default behavior while `quality_candidate_fail`,
  `severe_fragmentation`, or raw callback corruption remains reproducible.

## DEV-Only Probe Candidate Ranking

1. **GPU_ARTISAN / LlmGpuArtisanExecutor selection**
   - Best fit for the observed `same_stack_different_executor` result.
   - Evidence is native/static only; Java public classes are not visible.
   - Next step: prove whether runtime logs/fingerprints indicate artisan
     selection in Edge Gallery but not LAMI.

2. **RuntimeConfig**
   - Native evidence includes `GetRuntimeConfig`.
   - Public AAR class is not visible, so any future probe must remain isolated.

3. **BackendConstraint**
   - Native strings mention backend support and constraint mismatches.
   - Useful for explaining automatic GPU to GPU_ARTISAN switching.

4. **PreferredEngineType**
   - Could gate compiled model executor selection.
   - No public class found in AAR inventory.

5. **GpuOptions**
   - Native `LrtCreateGpuOptionsFromToml` appears in GPU-related libraries.
   - A public Java `GpuOptions` class was not found.

6. **GPU KV cache / decode cache**
   - `tflite_gpu_kv_cache` and `tflite_opencl_kv_cache` are strong runtime
     decode-path evidence.
   - Probe should remain diagnostic until executor selection is understood.

7. **nativeGenerateContentStream vs generateContent**
   - Already lower priority because final-response probes show callback text
     semantics do not explain the raw decode corruption.

8. **Final response aggregation layer**
   - Lower priority after `last_non_empty_callback_is_delta_not_final_response`
     and raw callback artifacts.

9. **Public sampler / maxTokens / cacheDir**
   - Lowest priority because baseline, collect-only, no sampling acceleration,
     and max token variants still failed with raw callback fragmentation.

## Next Device Diagnostics

For the next real-device run, preserve these keys:

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
- `gpu_output_quality_promotion_blocker`

If a future DEV-only probe only changes an internal selector, these keys are the
minimum set needed to decide whether executor/path changed without relaxing the
quality gate.

## Read-Only Internal Surface Probe

The next diagnostic step is a read-only runtime surface probe guarded by:

```bash
adb shell setprop debug.lami.gpu_internal_surface_probe true
```

The probe is intentionally narrow:

- It always emits presence keys in debug diagnostics, even when disabled or not
  eligible, so missing keys indicate a stale APK or diagnostics merge problem.
- It only emits detailed data in `standardGpuMinimalRuntimeCandidateDebug`.
- It only runs for the GPU backend.
- It uses class presence checks with `Class.forName(..., initialize=false, ...)`.
- It scans already loaded/native library files for string evidence such as
  `LlmGpuArtisanExecutor`, `GPU_ARTISAN`, and `tflite_gpu_kv_cache`.
- It does not instantiate hidden classes.
- It does not set `RuntimeConfig`, `BackendConstraint`, `PreferredEngineType`,
  or `GpuOptions`.
- It does not force `GPU_ARTISAN`.
- It does not call native methods directly.

Expected keys:

```text
gpu_internal_surface_probe_enabled
gpu_internal_surface_probe_result
gpu_internal_surface_probe_disabled_reason
gpu_internal_runtime_config_class_present
gpu_internal_backend_constraint_class_present
gpu_internal_preferred_engine_type_class_present
gpu_internal_gpu_options_class_present
gpu_internal_artisan_class_present
gpu_internal_llm_gpu_artisan_executor_symbol_present
gpu_internal_kv_cache_symbol_present
gpu_internal_runtime_config_methods
gpu_internal_backend_constraint_methods
gpu_internal_gpu_options_methods
gpu_internal_probe_exception_class
gpu_internal_probe_exception_message
```

Manual command:

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

Interpretation:

- `gpu_internal_surface_probe_enabled=false` with
  `gpu_internal_surface_probe_disabled_reason=property_off` means the APK is
  emitting the presence keys but the property is not enabled.
- `gpu_internal_surface_probe_result=not_eligible` means the probe body did not
  run. Check `gpu_internal_surface_probe_disabled_reason`; expected values
  include `not_gpu_backend` and `not_gpustandardminimal_application`.
- `gpu_internal_*_class_present=true` means a Java/Kotlin-visible class exists
  at runtime. That only authorizes a later design review; it does not mean LAMI
  should invoke it.
- `gpu_internal_llm_gpu_artisan_executor_symbol_present=true` with class
  presence false means the evidence is native/internal only.
- `gpu_internal_kv_cache_symbol_present=true` strengthens the decode-cache path

## Device Result: Internal Surface Probe

The latest `standardGpuMinimalRuntimeCandidateDebug` GPU run with
`debug.lami.gpu_internal_surface_probe=true` completed the read-only probe:

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
gpu_internal_runtime_config_methods=class_absent
gpu_internal_backend_constraint_methods=class_absent
gpu_internal_gpu_options_methods=class_absent
gpu_internal_probe_exception_class=none
gpu_internal_probe_exception_message=none
```

The same run still reported:

```text
edge_gallery_executor_probe_result=same_sampler_different_executor
edge_gallery_executor_difference_summary=same_sampler_lami_runtime_decode_fragmentation_executor_selection_suspected
gpu_output_quality_candidate_result=quality_candidate_fail
gpu_output_quality_gate_status=fail
gpu_output_quality_promotion_blocker=true
gpu_output_quality_summary=runtime_callback_source_corruption_suspected
gpu_sampler_root_cause_candidate=runtime_decode_fragmentation
gpu_output_source_corruption_stage=raw_callback
callback_corruption_earliest_stage=raw_callback
callback_quality_classification=severe_fragmentation
```

Interpretation:

- Lami minimal GPU still reaches invocation, but long output corruption starts
  at the raw callback source.
- Public Java/Kotlin class surfaces for `RuntimeConfig`, `BackendConstraint`,
  `PreferredEngineType`, `GpuOptions`, and `Artisan` are absent in the current
  runtime.
- Native/internal evidence for `LlmGpuArtisanExecutor` and GPU KV-cache remains
  present.
- No safe public API surface has been confirmed for selecting the Edge
  Gallery-like internal executor/config path from Lami.
- The next evidence to collect is an Edge Gallery APK vs Lami APK
  native/internal surface fingerprint comparison, not a reflection-based config
  change.

Promotion remains blocked while `gpu_output_quality_promotion_blocker=true` and
`gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`.
  hypothesis but does not identify a safe public configuration surface.
- `gpu_internal_surface_probe_result=exception` should be treated as diagnostic
  noise unless the main route also fails independently. The probe must not
  overwrite inference status.

Do not move to reflection application until all of these are true:

1. The same probe is repeatable across app restart.
2. The class/method surface is visible without native direct calls.
3. A DEV-only isolated flavor/property design exists with rollback.
4. The expected new diagnostics can prove whether executor fingerprints change.
5. The GPU quality blocker remains enforced until output quality passes.

Grep helper after saving copied diagnostics:

```bash
grep -E "gpu_internal_surface_probe|gpu_internal_.*class_present|gpu_internal_.*symbol_present|gpu_internal_.*methods|edge_gallery_executor_probe_result|gpu_output_quality_promotion_blocker" \
  artifacts/device_runs/gpu_internal_surface_probe_2026-06-15.txt
```

## Success Diagnostics Merge Fix

The first device check of this read-only probe produced the expected
`edge_gallery_executor_probe_result` and quality blocker keys, but none of the
`gpu_internal_surface_probe_*` keys in the success details. The probe property
was confirmed with `getprop`, so the issue was treated as diagnostics visibility
rather than an executor/runtime finding.

The success `source_summary=LOCAL_ROUTE_DIAG ...` path now normalizes and emits
presence keys for the internal surface probe in the same route diagnostics as
`edge_gallery_executor_probe_result`:

```text
gpu_internal_surface_probe_enabled
gpu_internal_surface_probe_result
gpu_internal_surface_probe_disabled_reason
```

With `debug.lami.gpu_internal_surface_probe=true`,
`standardGpuMinimalRuntimeCandidateDebug`, and backend `GPU`, expected values are:

```text
gpu_internal_surface_probe_enabled=true
gpu_internal_surface_probe_result=completed
# or completed_with_missing_symbols
gpu_internal_surface_probe_disabled_reason=none
```

If the keys are still absent after reinstalling the candidate APK, assume the
copied diagnostics came from a stale APK or from a UI path that did not include
the local route success details. Do not interpret absent keys as proof that
hidden runtime surfaces are absent.
