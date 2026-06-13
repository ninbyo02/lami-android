# LiteRT-LM galleryStackGpuProbe plan

## Purpose

`galleryStackGpuProbe` is a DEV-only isolation flavor for checking whether LAMI can run generic `gemma-4-E2B-it.litertlm` on GPU when the model, runtime stack, and API/lifecycle conditions are aligned with Google AI Edge Gallery.

This flavor does not change `standardDebug`, CPU routing, NPU S1, fallback, or production defaults.

## Non-goals

- Do not replace individual `.so` files in `standardDebug`.
- Do not copy Edge Gallery runtime files into production.
- Do not enable GPU silently for normal users.
- Do not change CPU held-official-flow.
- Do not change NPU S1 / SM8750 native route behavior.
- Do not change fallback, DB, TTS, Markdown, or streaming behavior.

## Proposed Flavor

Name:

- `galleryStackGpuProbe`

Application id:

- Base: `io.github.ninbyo02.lami`
- Suffix: `.gallerygpuprobe`
- Result example: `io.github.ninbyo02.lami.gallerygpuprobe`

Install behavior:

- Separate APK and separate app data from `standardDebug`.
- Separate model directory.
- Separate cache directory.
- Explicit developer install target only.
- Gradle task: `./gradlew :app:installGalleryStackGpuProbeDebug`
- Build task: `./gradlew :app:assembleGalleryStackGpuProbeDebug`

Native library handling:

- Use a dedicated source set such as `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/`.
- Treat `libLiteRt.so`, `liblitertlm_jni.so`, LiteRT dispatch/plugin libraries, model constraint provider, and related dependencies as a matched set.
- Never test a single `libLiteRt.so` or `liblitertlm_jni.so` replacement in the standard flavor.
- Stage libraries with `scripts/stage_gallery_stack_gpu_probe_native_libs.sh`.
- Default script mode is report-only. Use `--stage` to copy.
- Generated manifest: `artifacts/gallery_stack_gpu_probe/native_lib_manifest.tsv`.
- Staged `.so` files are intentionally gitignored.

```bash
scripts/stage_gallery_stack_gpu_probe_native_libs.sh
scripts/stage_gallery_stack_gpu_probe_native_libs.sh --stage
```

The script refuses to stage into `app/src/main`, `app/src/debug`, `app/src/standard`, or `app/src/standardDebug`.

## Phase 7 Implementation

Implemented pieces:

- Product flavor `galleryStackGpuProbe`.
- `applicationIdSuffix=".gallerystackgpu"`.
- `versionNameSuffix="-galleryStackGpuProbe"`.
- Debug-only variant; release variant disabled.
- Native lib source dir: `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/`.
- DEV opt-in property: `debug.lami.gallery_stack_gpu_probe=true`.
- Probe diagnostics in compact diagnostics, `LOCAL_ROUTE_DIAG`, and developer inference stats.

The flavor remains safe when the property is false. It does not force GPU, and it does not enable thinking/speculative decoding.

## Model Policy

Preferred manual model path:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Expected Edge Gallery model identity:

- `modelId=litert-community/gemma-4-E2B-it-litert-lm`
- `commitHash=6e5c4f1e395deb959c494953478fa5cec4b8008f`
- `sizeInBytes=2588147712`
- `accelerators=gpu,cpu`
- `visionAccelerator=gpu`
- `topK=64`
- `topP=0.95`
- `temperature=1.0`
- `maxTokens=4000`
- `maxContextLength=32000`
- `capabilities=llm_thinking,speculative_decoding`

LAMI does not bundle this model in git.

On-device diagnostics include model path, existence, and size. SHA-256 is not calculated on the UI path; use script-side hashing for large model files.

## Diagnostics

Added keys:

- `gallery_stack_probe_flavor`
- `gallery_stack_probe_enabled`
- `gallery_stack_probe_application_id`
- `gallery_stack_probe_native_stack_source`
- `gallery_stack_probe_liblitert_sha256`
- `gallery_stack_probe_liblitertlm_jni_sha256`
- `gallery_stack_probe_libs_manifest_present`
- `gallery_stack_probe_edge_gallery_model_expected`
- `gallery_stack_probe_model_path`
- `gallery_stack_probe_model_exists`
- `gallery_stack_probe_model_size_bytes`
- `gallery_stack_probe_model_sha256_if_available`
- `gallery_stack_probe_allowlist_config_applied`
- `gallery_stack_probe_runtime_stack_alignment_level`
- `gallery_stack_probe_thinking_api_available`
- `gallery_stack_probe_speculative_decoding_api_available`
- `gallery_stack_probe_allowlist_accelerators`
- `gallery_stack_probe_allowlist_vision_accelerator`
- `gallery_stack_probe_allowlist_top_k`
- `gallery_stack_probe_allowlist_top_p`
- `gallery_stack_probe_allowlist_temperature`
- `gallery_stack_probe_allowlist_max_tokens`
- `gallery_stack_probe_allowlist_max_context_length`

Alignment levels:

- `none`: neither expected model size nor Edge Gallery native stack SHA pair is observed.
- `model_only`: expected model size is observed, native stack is not aligned.
- `native_stack_staged`: Edge Gallery `libLiteRt.so` and `liblitertlm_jni.so` SHA pair is observed, expected model size is not.
- `native_stack_and_model`: both expected model size and Edge Gallery native SHA pair are observed.
- `unknown`: non-probe flavor or unavailable state.

Allowlist config is applied only when all are true:

- Flavor is `galleryStackGpuProbe`.
- Build is debug.
- `debug.lami.gallery_stack_gpu_probe=true`.
- Selected backend is GPU.

Current public API support:

- `gallery_stack_probe_thinking_api_available=false`
- `gallery_stack_probe_speculative_decoding_api_available=false`

These are not faked even though the Edge Gallery allowlist advertises those capabilities.

## Test Flow

Build/install:

```bash
./gradlew :app:assembleGalleryStackGpuProbeDebug
./gradlew :app:installGalleryStackGpuProbeDebug
```

Set DEV properties:

```bash
adb shell setprop debug.lami.gallery_stack_gpu_probe true
adb shell setprop debug.lami.compare_cpu_gpu_callback true
adb shell setprop debug.lami.gpu_generate_probe_mode raw_callback_only
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

Manual model selection:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Run:

1. CPU backend: `こんにちは`
2. GPU backend: `こんにちは`

Interpretation:

- GPU succeeds: runtime stack/model alignment likely fixed the issue.
- GPU still fails at `runtime/executor/llm_litert_compiled_model_executor.cc:735`: public `Backend.GPU` or inaccessible `GPU_ARTISAN`/internal executor remains the likely blocker.
- GPU fails earlier at load/init: staged native stack is incompatible with this app packaging/dependency graph.

Rollback:

```bash
adb uninstall io.github.ninbyo02.lami.gallerystackgpu
adb shell setprop debug.lami.gallery_stack_gpu_probe false
```

Remove local staged libraries by deleting files under:

```text
app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/*.so
```

Do not remove the `README.md` marker file.

## Gates Before Implementation

1. Confirm Edge Gallery model identity.
   - Exact model filename.
   - File size.
   - SHA-256.
   - Download/source URL or app-data model path.
2. Confirm LAMI model identity.
   - Exact selected model path.
   - File size.
   - SHA-256.
3. Confirm runtime stack provenance.
   - Version/source of LiteRT-LM Android runtime.
   - Version/source of LiteRT core runtime.
   - Build IDs and SHA-256 for all arm64 native libraries.
4. Confirm license and redistribution constraints before staging any external runtime into a local flavor.

## Required Diagnostics

The probe flavor must expose the same local inference failure compact keys as `standardDebug`, plus:

- `gallery_stack_probe_enabled`
- `gallery_stack_probe_app_id`
- `gallery_stack_probe_runtime_stack_id`
- `gallery_stack_probe_model_sha256`
- `gallery_stack_probe_model_size_bytes`
- `gallery_stack_probe_lib_litert_sha256`
- `gallery_stack_probe_lib_litert_build_id`
- `gallery_stack_probe_lib_litertlm_jni_sha256`
- `gallery_stack_probe_lib_litertlm_jni_build_id`
- `gallery_stack_probe_backend_api_candidates`
- `gallery_stack_probe_gpu_artisan_api_available`
- `gallery_stack_probe_executor_selection_hint`
- `gallery_stack_probe_result`

## Future Implementation Steps

1. Add an isolated product flavor/application id suffix without changing `standardDebug`.
2. Add a staging script that verifies a complete runtime stack before copying anything into the flavor source set.
3. Add model import checks that refuse suspicious files and require size/SHA logging.
4. Add a single DEV-only GPU probe screen or adb-property trigger in the isolated app.
5. Compare CPU and GPU callback/generate diagnostics inside the isolated app.
6. Promote no behavior back to `standardDebug` until model identity, runtime stack identity, and API/lifecycle conditions are understood.

## Risk Assessment

| Risk | Level | Mitigation |
| --- | --- | --- |
| Mixed LiteRT / LiteRT-LM native stack ABI mismatch | High | Use a matched stack only, isolated in a separate flavor. |
| False positive from different Edge Gallery model | High | Require model size/SHA/source identity before claiming parity. |
| Native library licensing/provenance issue | High | Do not import external runtime until provenance is documented. |
| Regressing CPU or NPU paths | Low if isolated | Keep separate app id/source set and avoid shared route changes. |
| Confusing users with experimental GPU behavior | Medium | Keep the flavor DEV-only and explicit opt-in. |

## Success Criteria

The isolated flavor is useful only if it can answer one of these questions:

- Same model + same runtime stack + comparable API conditions succeeds on GPU.
- Same model + same runtime stack still fails, making device/runtime GPU compatibility more likely.
- Edge Gallery success depends on a different model or internal executor path that current LAMI public API cannot reach.

Until one of those is proven, LAMI should keep CPU as the stable generic route and keep GPU experimental.
