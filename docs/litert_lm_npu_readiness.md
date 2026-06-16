# LiteRT-LM NPU readiness

This note tracks the current Qualcomm LiteRT-LM NPU readiness state for Lami.

## Current blocker

- Nubia Z70S Ultra / Snapdragon 8 Elite / SM8750 is treated as a candidate device.
- Current readiness is `blocked-dispatch-api-so-missing`.
- Lami must keep `selected path: gpu` and `NPU apply status: disabled / blocked` until the dispatch API `.so` is present and a CLI proof passes.

## Phase 11 Focus After GPU Investigation

GPU investigation is closed for now as experimental/diagnostics-only:

- Lami minimal GPU can invoke but still corrupts long output at raw callback
  source.
- The GPU classifier decision is
  `GPU_PROMOTION_DECISION=blocked`.
- The blocker reason is
  `raw_callback_corruption_and_public_api_gap`.
- CPU route is restored and remains the stable usable local inference route.

Development focus should return to the NPU safety/promotion track:

- Keep GPU probes for diagnostics only.
- Keep CPU as stable fallback / usable route.
- Continue NPU through the existing minimum safe route and standard promotion
  gates.
- Use `docs/npu_return_to_standard_route_plan.md` as the current return-to-NPU
  summary and implementation planning index.
- Reuse the GPU investigation patterns where helpful: focused copy keys,
  compact diagnostics, report generation, artifact summaries, and explicit
  promotion blockers.
- Do not use GPU callback repair or hidden executor hacks as a shortcut around
  the NPU plan.

## Evidence from Lami runtime diagnostics

- External QAIRT GPU diagnostics are passed.
- External QAIRT DSP/HTP diagnostics are passed.
- The observed QNN DSP core is Hexagon Architecture V79.
- LiteRT-LM `Backend.NPU(String)` is detected.
- QNN runtime libraries and V79 skel/stub libraries are detected in the app-side native library search.
- Dispatch API candidates are not detected in `context.applicationInfo.nativeLibraryDir`.
- The current blocker is missing LiteRT Qualcomm dispatch API `.so`, not lack of QNN/HTP device capability.

## Model compatibility

- `gemma-4-E2B-it.litertlm` is treated as generic GPU-compatible LiteRT-LM.
- A generic `.litertlm` model is not assumed to be an NPU SoC-specific model.
- A filename containing `qualcomm`, `sm8750`, `qcs`, `qnn`, or `htp` is treated only as a Qualcomm SoC-specific candidate.

## Evidence from local script

Run:

```bash
scripts/check_litert_npu_dispatch.sh
```

The script checks Gradle caches, build intermediates, APK contents, Android SDK paths, QAIRT paths, `local_sdks`, and `third_party` for these candidates:

- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtDispatchQualcomm.so`
- `liblitert_dispatch_qualcomm.so`
- `libLiteRtDispatch.so`
- `liblitert_dispatch.so`
- `*dispatch*.so`
- `*Dispatch*.so`

The May 15, 2026 local run found no dispatch API `.so` in:

- `~/.gradle/caches`
- `app/build`
- root `build`
- Android SDK paths
- APK contents
- QAIRT paths configured in the environment
- repo-local `workspace/sdk/qairt`, `local_sdks`, and `third_party`

The script result was `NOT FOUND: LiteRT Qualcomm dispatch API .so`.

If a dispatch API `.so` is found later, it still needs packaging under `jniLibs/arm64-v8a` or dependency packaging before app-side NPU can be considered.

## Dependency and AAR inspection result

Lami currently depends on:

- `com.google.ai.edge.litertlm:litertlm-android:0.11.0` for debug
- `com.google.ai.edge.litertlm:litertlm-android:0.10.0` for release
- `com.qualcomm.qti:qnn-runtime:2.34.0`
- `com.qualcomm.qti:qnn-litert-delegate:2.34.0`
- `com.google.mediapipe:tasks-genai:0.10.33`

Gradle cache inspection found these relevant AARs:

- `litertlm-android-0.10.0.aar`
- `litertlm-android-0.11.0.aar`
- `tasks-genai-0.10.33.aar`
- `qnn-runtime-2.34.0.aar`
- `qnn-litert-delegate-2.34.0.aar`

Native library contents observed:

- `litertlm-android-0.10.0.aar`: `liblitertlm_jni.so`
- `litertlm-android-0.11.0.aar`: `libLiteRt.so`, `libLiteRtClGlAccelerator.so`, `liblitertlm_jni.so`
- `tasks-genai-0.10.33.aar`: `libllm_inference_engine_jni.so`
- `qnn-litert-delegate-2.34.0.aar`: `libQnnTFLiteDelegate.so`, `libqnn_delegate_jni.so`
- `qnn-runtime-2.34.0.aar`: `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnGpu.so`, `libQnnDsp.so`, and HTP/DSP skel/stub libraries including V79

No inspected AAR contains `libLiteRtDispatch_Qualcomm.so` or another dispatch-named `.so`.

Both `litertlm-android-0.10.0` and `0.11.0` expose `com.google.ai.edge.litertlm.Backend.NPU` with constructors including `NPU()` and `NPU(String)`. This only proves the Kotlin API is present; it does not prove the Qualcomm dispatch runtime is packaged.

## APK inspection result

The debug APK currently includes LiteRT-LM JNI, LiteRT GPU/OpenCL, QNN runtime, QNN HTP skel/stub including V79, and QNN TFLite delegate libraries. It does not include any dispatch-named `.so`.

Observed APK native libraries include:

- `libLiteRt.so`
- `libLiteRtClGlAccelerator.so`
- `liblitertlm_jni.so`
- `libllm_inference_engine_jni.so`
- `libQnnSystem.so`
- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnGpu.so`
- `libQnnDsp.so`
- `libQnnHtpV79Skel.so`
- `libQnnHtpV79Stub.so`
- `libQnnTFLiteDelegate.so`
- `libqnn_delegate_jni.so`
- `libqnn_direct_probe_debug.so`

The APK inspection currently supports the same conclusion as runtime diagnostics: QNN/HTP libraries are packaged, but LiteRT Qualcomm dispatch API is not.

## Google AI Edge Gallery comparison

Google AI Edge Gallery source uses `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` when the selected accelerator is NPU or TPU, while its default accelerator path remains GPU unless a model/user configuration selects NPU.

Gallery main currently uses `com.google.ai.edge.litertlm:litertlm-android:0.11.0`, matching Lami debug's LiteRT-LM version. The Maven dependency match alone does not explain NPU readiness, because local inspection of the same Maven AAR shows no Qualcomm dispatch `.so`.

Gallery release notes for `1.0.12` state that Gemma3 1B NPU support was added for Qualcomm SoCs, that Play Store installs bundle the correct libraries automatically for supported SoCs, and that release APKs are split by SoC such as `ai-edge-gallery-sm8750.apk`. This suggests Gallery's working NPU path depends on SoC-specific app packaging beyond the public `litertlm-android` Maven AAR alone.

References:

- Google AI Edge LiteRT-LM NPU guide: https://ai.google.dev/edge/litert/next/litert_lm_npu
- Gallery LiteRT-LM helper: https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt
- Gallery LiteRT-LM dependency version: https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml
- Gallery releases: https://github.com/google-ai-edge/gallery/releases

## Known upstream issues

- `google-ai-edge/LiteRT#6889` requests publishing a prebuilt `libLiteRtDispatch_Qualcomm.so` alongside `litertlm-android`. The issue reports that `litertlm-android:0.10.0` exposes `Backend.NPU()` but does not include the required Qualcomm dispatch library, and that missing or incompatible dispatch can lead to native abort: https://github.com/google-ai-edge/LiteRT/issues/6889
- `google-ai-edge/LiteRT-LM#1121` documents a Qualcomm NPU CLI setup where the user pushes QAIRT libraries, `prebuilt/android_arm64/*.so`, `litert_lm_main`, and `bazel-bin/external/litert/litert/vendors/qualcomm/dispatch/libLiteRtDispatch_Qualcomm.so`: https://github.com/google-ai-edge/LiteRT-LM/issues/1121
- `google-ai-edge/LiteRT-LM#1377`, `#1979`, and `#2079` show related Qualcomm NPU setup failures where dispatch and QNN library combinations matter. These issues reinforce that finding a dispatch `.so` is necessary but not sufficient; version compatibility and CLI proof still matter:
  - https://github.com/google-ai-edge/LiteRT-LM/issues/1377
  - https://github.com/google-ai-edge/LiteRT-LM/issues/1979
  - https://github.com/google-ai-edge/LiteRT-LM/issues/2079

## Hypotheses

- `litertlm-android` may expose `Backend.NPU` but may not package Qualcomm dispatch runtime in the public Maven AAR.
- Qualcomm dispatch API may need to be built from a LiteRT source revision that is ABI-compatible with the LiteRT-LM AAR, or obtained from an official SoC-specific Gallery/package artifact if licensing permits.
- QAIRT Maven/runtime artifacts provide QNN runtime and HTP skel/stub libraries, but may not provide the LiteRT dispatch API bridge.
- SoC-specific model packages may provide `.litertlm` files but may not provide the app-side dispatch `.so`.
- App-side NPU enablement should remain blocked until the dispatch API, QAIRT/QNN libraries, model, and `litert_lm_main --backend=npu` proof are all aligned.

## Next actions

1. Run `scripts/check_litert_npu_dispatch.sh` after every dependency or SDK packaging change.
2. Inspect any Google AI Edge Gallery SoC-specific APK that is legally available for the target SoC and compare `lib/arm64-v8a` contents.
3. Identify an official source for `libLiteRtDispatch_Qualcomm.so` that is compatible with the exact `litertlm-android` AAR version.
4. Do not copy unknown-license binaries into `jniLibs`.
5. Verify with `litert_lm_main --backend=npu` on the target device before enabling app NPU.
6. Only after CLI proof, add a guarded app-side NPU path with GPU fallback kept intact.

## Safety rule

Do not apply `Backend.NPU` from the app while the dispatch API `.so` is missing.

Before any app-side NPU enablement:

1. Package a compatible Qualcomm LiteRT dispatch API `.so`.
2. Verify the Qualcomm model with `litert_lm_main --backend=npu`.
3. Keep GPU fallback enabled.
4. Enable app NPU only after the CLI proof and app-side guard review.
