# Edge Gallery Executor Selection Static Trace

Scope: static investigation only. This document does not approve GPU promotion,
runtime replacement, callback joining changes, model loading changes, or
production route changes.

## Inputs Reviewed

- Edge Gallery APK splits under `artifacts/external/edge_gallery_apks/`
- Existing static extraction under `artifacts/edge_gallery_static/`
- Current Lami GPU route diagnostics and implementation in:
  - `LocalStreamingRunner.kt`
  - `LocalRouteDiagnostics.kt`
  - `GalleryStackGpuProbeDiagnostics.kt`
- Existing comparison notes:
  - `docs/edge_gallery_vs_lami_gpu_executor_diff.md`
  - `docs/gpu_runtime_decode_fragmentation_root_cause_rank.md`
  - `docs/gallery_parity_config_diff.md`

## Edge Gallery Static Executor Evidence

The strongest Edge Gallery evidence is in `liblitertlm_jni.so`, not in a
visible app-layer Kotlin class.

| Evidence | Static location | Interpretation |
| --- | --- | --- |
| `GPU_ARTISAN`, `CPU_ARTISAN`, `GOOGLE_TENSOR_ARTISAN` | `artifacts/edge_gallery_static/gpu_artisan_access_path.md`, native strings | The runtime contains backend/executor labels beyond Lami's public reflection surface. |
| `. Supported backends are: [CPU, GPU, NPU, GPU_ARTISAN, CPU_ARTISAN, GOOGLE_TENSOR_ARTISAN]` | native strings | Backend validation knows artisan backend values. |
| `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.` | `liblitertlm_jni.so` strings | Runtime can switch from public GPU to an internal GPU_ARTISAN path. |
| `LlmGpuArtisanExecutor::Create with the following config:` | native strings | A native GPU artisan executor exists. |
| `LlmGpuArtisanExecutor::Prefill with the following arguments:` | native strings | The artisan executor has its own prefill entry point. |
| `TF_LITE_PREFILL_DECODE model is expected to exist when not using GPU_ARTISAN backend. But it is null.` | native strings | Non-artisan and artisan executor paths appear to expect different model resources. |
| `backend constraint is matched` / `backend constraint mismatch. Model requires one of [` | native strings | Model/runtime backend constraints participate in executor selection. |
| `GetRuntimeConfig not implemented for backend:` | native strings | RuntimeConfig is an internal execution concept, but not necessarily public. |
| `tflite_gpu_kv_cache` / `tflite_opencl_kv_cache` | existing KV-cache context extraction | GPU decode may select a KV-cache implementation internally. |

Dex-level evidence is weaker:

| Dex finding | Interpretation |
| --- | --- |
| `base.apk:classes2.dex` contains `GPU_ARTISAN` | A backend enum/string may be visible to app bytecode. |
| `LlmGpuArtisanExecutor` is not observed in classes.dex/classes2.dex/classes3.dex | Executor implementation is native/runtime-side, not app-layer source. |
| `RuntimeConfig` is not observed in the app dex hits from the current extraction | RuntimeConfig is likely not a stable public app API in the current artifact. |

## Lami Corresponding Path

Current Lami LiteRT-LM chat construction uses the public LiteRT-LM API:

- `EngineConfig(...)` in `buildLiteRtEngineConfig(...)`
- `Backend.CPU()` / `Backend.GPU()` / disabled NPU fallback selection
- GPU-compatible modality settings:
  - `visionBackend=null` when Edge-Gallery-like GPU compatibility is applied
  - `audioBackend=null` when Edge-Gallery-like GPU compatibility is applied
- `cacheDir` selected by Lami diagnostic experiment mode
- `maxNumTokens` selected by Lami diagnostic experiment mode
- `createConversation(ConversationConfig(...))` when the public class is found
- `SamplerConfig(topK=64, topP=0.95, temperature=1.0)` for the Gallery-like GPU sampler profile

Current Lami diagnostics already expose:

- `gpu_engine_config_*`
- `gpu_conversation_config_*`
- `gpu_sampler_config_*`
- `engine_config_fingerprint`
- `conversation_config_fingerprint`
- `sampler_config_fingerprint`
- `executor_selection_fingerprint`
- `runtime_backend_fingerprint`
- `runtime_executor_fingerprint`
- `runtime_dispatch_fingerprint`
- `runtime_compiled_model_fingerprint`
- `edge_gallery_executor_probe_result`
- `edge_gallery_executor_difference_summary`

What Lami does not currently expose as a public selectable route:

- `GPU_ARTISAN`
- `CPU_ARTISAN`
- `GOOGLE_TENSOR_ARTISAN`
- `RuntimeConfig`
- preferred engine type setters
- backend constraint setters
- direct GPU KV-cache selector

This matches earlier reflection findings where Lami's public backend candidates
were `CPU,GPU,NPU`.

## Static Selection Hypothesis

The current static trace supports this hypothesis:

```text
Edge Gallery may request public GPU, but the Edge Gallery LiteRT-LM runtime can
internally switch to GPU_ARTISAN or another backend-constrained executor.
Lami can request public Backend.GPU and mimic EngineConfig / ConversationConfig
/ SamplerConfig, but it cannot currently prove or force the same internal
executor selection from public APIs.
```

This is a stronger explanation than UI append, markdown repair, sampler-only, or
model identity because:

- Lami raw callback artifacts are already corrupt before UI append.
- `collect_only`, baseline, and no-sampling-acceleration quality matrix runs
  all fail.
- Lami CPU succeeds with the same model family.
- Edge Gallery official GPU succeeds on the same device/model class.
- Edge Gallery native runtime contains internal executor/constraint/KV-cache
  evidence not represented in Lami public reflection.

## Phase 9 Executor Token Trace

`scripts/static_trace_edge_gallery_executor.sh` now also compares Edge Gallery
APK/extracted inputs with the Lami `standardGpuMinimalRuntimeCandidateDebug`
APK and writes executor-selection token artifacts under
`artifacts/apk_native_diff/`:

```text
executor_selection_trace.tsv
executor_selection_trace_summary.txt
edge_only_executor_tokens.txt
lami_only_executor_tokens.txt
common_executor_tokens.txt
```

Latest local trace:

```text
EDGE_GALLERY_EXECUTOR_SELECTION_TRACE_FINGERPRINT=7c7054d0ebcce0c3f39d9a84c691acb1c31cace01f8decbe9577205a80177be0
LAMI_EXECUTOR_SELECTION_TRACE_FINGERPRINT=8b95d5a60d533165f02515015c9da7c3c0f2365f065e603f7c8a8e2a63250700
EXECUTOR_SELECTION_TRACE_DIFF_SUMMARY=different_executor_selection_tokens
EDGE_ONLY_EXECUTOR_TOKENS=none
LAMI_ONLY_EXECUTOR_TOKENS=BackendConstraint,PreferredEngineType
COMMON_EXECUTOR_TOKENS=GPU_ARTISAN,LlmGpuArtisanExecutor,RuntimeConfig,GpuOptions,LrtCreateGpuOptionsFromToml,tflite_gpu_kv_cache,kv_cache,nativeGenerateContentStream,nativeRunPrefill,nativeRunDecode,CompiledModelExecutor,LlmLiteRtCompiledModelExecutor
```

Interpretation:

- The high-value native/runtime tokens `GPU_ARTISAN`,
  `LlmGpuArtisanExecutor`, `RuntimeConfig`, `GpuOptions`,
  `LrtCreateGpuOptionsFromToml`, GPU KV-cache tokens, and native generate/decode
  entry points are visible in both static inputs.
- Exact-token `BackendConstraint` and `PreferredEngineType` were observed only
  in the Lami trace, while the lower-case phrase forms `backend constraint` and
  `preferred engine` were common. This should be treated as a surface/token
  inventory difference, not proof that Lami reaches a better executor path.
- There were no Edge-only executor tokens in this token list. That does not
  clear Lami: the APK/native fingerprint comparison still reports
  `INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface`, and the real
  device executor probe reports `same_sampler_different_executor`.
- Token presence proves availability of strings/symbols only. It does not prove
  which executor Edge Gallery selected during the known-good GPU run.

## Unknowns

Static strings do not prove which executor Edge Gallery selected during the
observed successful run. The following are still unknown without runtime
instrumentation or Edge Gallery log evidence:

- Whether the successful Edge Gallery GPU run selected `GPU_ARTISAN`
- Whether it selected the public compiled-model executor with different
  `RuntimeConfig`
- Whether it selected a GPU KV-cache path different from Lami
- Whether Edge Gallery uses a native callback adapter before app code sees text
- Whether model metadata/backend constraints drive selection at runtime

## Next Diagnostics Keys To Compare

For Lami `standardGpuMinimalRuntimeCandidateDebug`, run the executor probe and
copy compact/details diagnostics. The most important keys are:

- `gpu_output_quality_matrix_mode=edge_gallery_executor_probe`
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
- `edge_gallery_executor_probe_result`
- `edge_gallery_executor_difference_summary`
- `gpu_output_quality_candidate_result`
- `callback_corruption_earliest_stage`
- `gpu_output_source_corruption_stage`
- `gpu_sampler_root_cause_candidate`

Static trace helper:

```bash
scripts/static_trace_edge_gallery_executor.sh \
  --edge-apks artifacts/external/edge_gallery_apks \
  --lami-apk app/build/outputs/apk/standardGpuMinimalRuntimeCandidate/debug/app-standardGpuMinimalRuntimeCandidate-debug.apk \
  --compare-output artifacts/apk_native_diff
```

The helper writes the older Edge-only trace to
`artifacts/static_edge_gallery_executor_trace/` and the Edge-vs-Lami executor
token comparison to `artifacts/apk_native_diff/`. It is safe to run when APK
artifacts are missing; it reports expected paths instead of treating missing
inputs as fatal.
