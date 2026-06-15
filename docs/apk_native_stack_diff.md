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
  --edge-gallery artifacts/external/edge_gallery_apks/split_config.arm64_v8a.apk \
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
- `RuntimeConfig`
- `BackendConstraint`
- `PreferredEngineType`
- `CompiledModelExecutor`
- `nativeGenerateContent`
- `nativeGenerateContentStream`
- `nativeRunPrefill`
- `nativeRunDecode`

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
| `qualcomm_stack_same=no` | Qualcomm dispatch/compiler/QNN/model constraint provider set differs. |

## Promotion Position

This comparison is diagnostic only. It does not relax these blockers:

- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`

Standard GPU promotion remains blocked while Lami GPU raw callbacks corrupt on
long output.
