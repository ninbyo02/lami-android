# APK Native Stack Diff

Scope: static APK/native-library comparison only. This does not change Kotlin
runtime behavior, CPU/GPU/NPU routes, callback joining, UI, or production code.

## Purpose

Compare the native runtime stack shipped in Edge Gallery and Lami APKs before
running the next device-side `edge_gallery_executor_probe`.

The comparison answers:

- Are the target LiteRT-LM runtime libraries present in both APKs?
- Do matching library names have the same SHA-256?
- Is the JNI generate/prefill/decode surface the same?
- Are executor/runtime strings such as `GPU_ARTISAN` and
  `LlmGpuArtisanExecutor` present?
- Are Qualcomm dispatch/compiler/QNN libraries present or different?

## Usage

APK inputs:

```bash
scripts/compare_edge_gallery_and_lami_apk.sh \
  --edge-gallery artifacts/external/edge_gallery_apks/base.apk \
  --lami-apk app/build/outputs/apk/standardGpuMinimalRuntimeCandidate/debug/app-standardGpuMinimalRuntimeCandidate-debug.apk
```

Directory inputs:

```bash
scripts/compare_edge_gallery_and_lami_apk.sh \
  --edge-gallery-dir artifacts/external/edge_gallery_apks \
  --lami-dir app/build/outputs/apk/standardGpuMinimalRuntimeCandidate/debug
```

The directory form accepts either:

- a directory containing APK split files, or
- an extracted directory containing `.so` files.

Default output:

```text
artifacts/apk_native_diff/
```

## Generated Artifacts

| File | Purpose |
| --- | --- |
| `native_lib_inventory.tsv` | Library presence, size, SHA-256, SHA match, and keyword flags. |
| `jni_symbol_diff.tsv` | Visible JNI generate/prefill/decode symbol/string surface comparison. |
| `native_stack_fingerprint.txt` | Runtime, JNI, executor, and Qualcomm fingerprints for each side. |
| `runtime_stack_summary.txt` | High-level same/different summary and high-priority missing/mismatch list. |
| `internal_surface_summary.txt` | Edge Gallery vs Lami internal surface fingerprints and same/different summary. |
| `internal_surface_diff.tsv` | Per-hit internal surface presence comparison. |

## Compared Libraries

High-priority runtime libraries:

- `libLiteRt.so`
- `liblitertlm_jni.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libGemmaModelConstraintProvider.so`

QNN libraries:

- `libQnn*.so`

QNN libraries are visible in the diff because they matter for Qualcomm runtime
packaging, but they are currently lower priority for generic GPU success than
`libLiteRt.so` and `liblitertlm_jni.so`.

## Keywords Checked

The inventory records per-library keyword flags for:

- `GPU_ARTISAN`
- `LlmGpuArtisanExecutor`
- `Artisan`
- `RuntimeConfig`
- `BackendConstraint`
- `PreferredEngineType`
- `GpuOptions`
- `LrtCreateGpuOptionsFromToml`
- `tflite_gpu_kv_cache`
- `tflite_opencl_kv_cache`
- `kv_cache`
- `CompiledModelExecutor`
- `LlmLiteRtCompiledModelExecutor`
- `GetRuntimeConfig`
- `nativeGenerateContent`
- `nativeGenerateContentStream`
- `nativeRunPrefill`
- `nativeRunDecode`

## Latest Comparison

Inputs:

```text
edge_input=artifacts/external/edge_gallery_apks
lami_input=app/build/outputs/apk/standardGpuMinimalRuntimeCandidate/debug/app-standardGpuMinimalRuntimeCandidate-debug.apk
```

Current result:

```text
RUNTIME_STACK_DIFF_SUMMARY=different_runtime_stack
JNI_SYMBOL_DIFF_SUMMARY=different_jni_surface
EXECUTOR_SYMBOL_DIFF_SUMMARY=different_executor_symbols
INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface
QUALCOMM_STACK_DIFF_SUMMARY=different_qualcomm_stack
missing_high_priority_lami_libs=libLiteRtDispatch_Qualcomm.so,
sha_mismatch_high_priority_libs=libLiteRt.so,liblitertlm_jni.so,
```

Internal surface fingerprints:

```text
EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT=34af6f258570c911a9bbb14763e6b267fa33b432324e4c30752502f88af56dd4
LAMI_INTERNAL_SURFACE_FINGERPRINT=3fa6528634ecf90fdec7931523a4b3b0f8050c2f77aaa5ffa72794e9276fb253
INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface
```

## Interpreting Results

### Fingerprints match

If `runtime_stack_same=yes`, `jni_surface_same=yes`, and
`executor_symbol_same=yes`, the packaged native surface appears aligned at this
static level. If Lami still corrupts raw callbacks while Edge Gallery does not,
the next suspects are:

- internal runtime configuration
- executor/backend selection
- model metadata/backend constraints
- callback source semantics below app UI joining

### Fingerprints differ

If any fingerprint differs, inspect:

- `native_lib_inventory.tsv`
- `runtime_stack_summary.txt`
- `jni_symbol_diff.tsv`

Useful interpretations:

| Difference | Meaning |
| --- | --- |
| `runtime_stack_same=no` | At least one target runtime library presence/SHA differs. |
| `jni_surface_same=no` | Generate/prefill/decode JNI surface differs. |
| `executor_symbol_same=no` | Executor/runtime strings or exported symbols differ. |
| `internal_surface_same=no` or `INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface` | The internal executor/config/KV-cache string/symbol surface differs. This strengthens the public API gap / hidden executor path hypothesis. |
| `qualcomm_stack_same=no` | Qualcomm dispatch/compiler/QNN/model constraint provider set differs. |

If `INTERNAL_SURFACE_DIFF_SUMMARY=same_internal_surface` but the device run still
reports `edge_gallery_executor_probe_result=same_sampler_different_executor`,
prioritize runtime selection/config metadata over packaged symbol presence. Same
static surface does not prove that both apps select the same executor.

## Promotion Position

This comparison is diagnostic only. It does not relax these blockers:

- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`

Standard GPU promotion remains blocked while Lami GPU raw callbacks corrupt on
long output.
