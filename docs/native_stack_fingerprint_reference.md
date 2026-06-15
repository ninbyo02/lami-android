# Native Stack Fingerprint Reference

Scope: static fingerprint definitions for APK/native-library investigation.
Fingerprints are diagnostic helpers, not runtime behavior controls.

## Script

```bash
scripts/generate_native_stack_fingerprint.sh --apk path/to/app.apk
scripts/generate_native_stack_fingerprint.sh --dir path/to/extracted-or-apk-dir
```

Example output:

```text
INPUT_RUNTIME_STACK_FINGERPRINT=...
INPUT_NATIVE_STACK_FINGERPRINT=...
INPUT_JNI_SURFACE_FINGERPRINT=...
INPUT_EXECUTOR_SYMBOL_FINGERPRINT=...
INPUT_INTERNAL_SURFACE_FINGERPRINT=...
INPUT_QUALCOMM_STACK_FINGERPRINT=...
INPUT_NATIVE_LIB_COUNT=...
```

Use `--label EDGE` or `--label LAMI` to change the key prefix.

## Fingerprint Definitions

### Runtime Stack Fingerprint

Key:

```text
RUNTIME_STACK_FINGERPRINT
```

Includes:

- `libLiteRt.so`
- `liblitertlm_jni.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libGemmaModelConstraintProvider.so`
- every `libQnn*.so`
- library presence
- size in bytes
- SHA-256

Does not include:

- app Kotlin/Java bytecode
- resources
- model files
- Android manifest metadata
- runtime `/proc/self/maps`

If this changes, suspect a packaged native runtime stack difference.

### JNI Surface Fingerprint

Key:

```text
JNI_SURFACE_FINGERPRINT
```

Includes strings and exported dynamic symbols matching:

- `Java_.*LiteRtLmJni`
- `nativeGenerateContent`
- `nativeGenerateContentStream`
- `nativeRunPrefill`
- `nativeRunDecode`

Primarily sourced from `liblitertlm_jni.so`.

If this changes, suspect a JNI surface or generate/prefill/decode entry-point
difference.

### Executor Symbol Fingerprint

Key:

```text
EXECUTOR_SYMBOL_FINGERPRINT
```

Includes strings and exported dynamic symbols matching:

- `GPU_ARTISAN`
- `LlmGpuArtisanExecutor`
- `RuntimeConfig`
- `BackendConstraint`
- `PreferredEngineType`
- `CompiledModelExecutor`
- `LlmLiteRtCompiledModelExecutor`
- `generateContent`
- `generateContentStream`
- `nativeGenerateContent`
- `nativeGenerateContentStream`
- `nativeRunPrefill`
- `nativeRunDecode`

If this changes, suspect executor/backend selection capability differences or
symbol/string surface changes.

Important limitation: matching executor symbol fingerprints do not prove both
apps select the same executor at runtime. They only prove the same static
evidence is present.

### Internal Surface Fingerprint

Key:

```text
INTERNAL_SURFACE_FINGERPRINT
```

Includes strings and exported dynamic symbols matching:

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
- `nativeGenerateContent`
- `nativeGenerateContentStream`
- `nativeRunPrefill`
- `nativeRunDecode`
- `CompiledModelExecutor`
- `LlmLiteRtCompiledModelExecutor`
- `GetRuntimeConfig`
- `backend constraint`
- `preferred engine`

If this changes, suspect that Edge Gallery and Lami package different native
capabilities or string/symbol surfaces for hidden executor selection, runtime
config, backend constraints, GPU options, or KV-cache/decode routing.

Important limitation: this is still static evidence. A matching internal
surface fingerprint does not prove that runtime selection is the same. A
different internal surface fingerprint strengthens the case for APK/native
surface mismatch, but does not by itself justify reflection, native direct
calls, or promotion.

### Qualcomm Stack Fingerprint

Key:

```text
QUALCOMM_STACK_FINGERPRINT
```

Includes:

- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libGemmaModelConstraintProvider.so`
- every `libQnn*.so`
- library presence
- size in bytes
- SHA-256

If this changes, suspect Qualcomm packaging or dispatch/compiler provider
differences. For the current generic GPU investigation, this is lower priority
than the core LiteRT-LM runtime pair unless executor probe diagnostics point
back to Qualcomm dispatch.

## What Fingerprints Cannot Prove

Fingerprints cannot prove:

- actual runtime executor selection
- actual `RuntimeConfig`
- backend constraint resolution result
- GPU KV-cache selection
- model metadata selected path
- callback source semantics

Use them with runtime diagnostics:

- `executor_selection_fingerprint`
- `runtime_backend_fingerprint`
- `runtime_executor_fingerprint`
- `runtime_dispatch_fingerprint`
- `runtime_compiled_model_fingerprint`
- `edge_gallery_executor_probe_result`

## Decision Mapping

| Changed fingerprint | Most likely meaning |
| --- | --- |
| runtime stack | Packaged native runtime stack differs. |
| JNI surface | LiteRT-LM JNI entry point surface differs. |
| executor symbol | Executor/backend capability surface differs. |
| internal surface | Hidden RuntimeConfig / GPU_ARTISAN / backend constraint / KV-cache capability surface differs. |
| Qualcomm stack | Qualcomm dispatch/compiler/QNN provider set differs. |

If fingerprints are identical but behavior differs, prioritize hidden runtime
selection, model metadata/backend constraints, and runtime diagnostics over
static APK diff.

## Current Edge Gallery vs Lami Result

Latest comparison:

```text
EDGE_GALLERY_NATIVE_STACK_FINGERPRINT=0b6ebb7073fc995195749285d5c7d773bea1eadfb6b20e685abc42dc86a020fd
LAMI_NATIVE_STACK_FINGERPRINT=efc248720a510847952a535108941266a41535d97aa97b9474990a9a8d85cf3f
EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT=34af6f258570c911a9bbb14763e6b267fa33b432324e4c30752502f88af56dd4
LAMI_INTERNAL_SURFACE_FINGERPRINT=3fa6528634ecf90fdec7931523a4b3b0f8050c2f77aaa5ffa72794e9276fb253
INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface
```

This supports keeping the leading hypothesis on runtime/native executor surface
or hidden selector path mismatch. It does not unblock GPU promotion.
