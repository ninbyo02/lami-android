# LiteRT-LM galleryStackGpuProbe plan

## Purpose

`galleryStackGpuProbe` is a future DEV-only isolation flavor for checking whether LAMI can run generic `gemma-4-E2B-it.litertlm` on GPU when the model, runtime stack, and API/lifecycle conditions are aligned with Google AI Edge Gallery.

This is a plan only. It does not change `standardDebug`, CPU routing, NPU S1, fallback, or production defaults.

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

Native library handling:

- Use a dedicated source set such as `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/`.
- Treat `libLiteRt.so`, `liblitertlm_jni.so`, LiteRT dispatch/plugin libraries, model constraint provider, and related dependencies as a matched set.
- Never test a single `libLiteRt.so` or `liblitertlm_jni.so` replacement in the standard flavor.

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
