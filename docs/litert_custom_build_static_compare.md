# LiteRT Custom Build Static Compare

Date: 2026-05-16

Artifact:

```text
artifacts/litert_custom_build/20260516_235244/
```

This comparison covers only isolated Bazel-built artifacts. No built library was copied into any app source set, APK, or device.

## Built Artifacts

| Library | Build ID | SHA-256 | Size | NEEDED | Notes |
| --- | --- | --- | ---: | --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` | 5405080 | `libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so` | Built from pinned LiteRT ref. Exports `LiteRtDispatchGetApiVersion` and `LiteRtDispatchCheckRuntimeCompatibility`. |
| `libLiteRtDispatch_Qualcomm.so` | `e999216e6d32c2f38702cd8538299e7d` | `2b999e1c56e87d0ae6c65d1613d4b8675cd998297d915d3e55bba248c9e1aefe` | 691184 | `libandroid.so,liblog.so,libdl.so,libc.so,libm.so` | Exports `LiteRtDispatchGetApi@@VERS_1.0`. Does not list `libLiteRt.so` in `NEEDED`, unlike Gallery. |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | `310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230` | 55144176 | `libGemmaModelConstraintProvider.so,libdl.so,liblog.so,libandroid.so,libGLESv3.so,libEGL.so,libm.so,libc.so` | Built after resolving Android arm64 Git LFS prebuilts. Contains debug info and is not stripped. |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `9053b81d7cbccdc3b5460c5e7395e293` | `9e1547a45fa31a63ef9fd77e79880f576487035c78d99eb1ecbfa85823d306cb` | 1002320 | `libandroid.so,liblog.so,libdl.so,libc.so,libm.so` | Built from pinned LiteRT ref. Contains QNN/ADSP/LD library path strings. |

Not produced:

- `libLiteRtRuntimeCApi.so`

## Target Results

| Target | Result |
| --- | --- |
| `@litert//litert/c:litert_runtime_c_api_so` | success |
| `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so` | success |
| `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni` | success |
| `@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so` | success |

The previous `litertlm_jni` failure was fixed by resolving Git LFS prebuilts under `prebuilt/android_arm64/`. The blocking file is now an Android arm64 ELF:

```text
prebuilt/android_arm64/libGemmaModelConstraintProvider.so
Build ID: f9e5e73e668032550042319e43012011
SHA-256: 45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6
```

`liblitertlm_jni.so` now links successfully, but it has a new runtime dependency on `libGemmaModelConstraintProvider.so`. Any future isolated insertion must stage that dependency from the same resolved prebuilt set.

## Reference Comparison

| Library | Built | Gallery SM8750 | Maven `litertlm-android:0.11.0` | Finding |
| --- | --- | --- | --- | --- |
| `libLiteRt.so` | `a03032ad...`, 5405080 bytes | `869121bd...`, 4964616 bytes | `80fa0688...`, 5046960 bytes | All differ. Built LiteRT is not byte/build-id compatible with Gallery or Maven. |
| `libLiteRtDispatch_Qualcomm.so` | `e999216e...`, 691184 bytes | `643ad77b...`, 446088 bytes | missing | Built dispatch differs from Gallery and has different `NEEDED`. |
| `liblitertlm_jni.so` | `b78167f...`, 55144176 bytes | `76e4dccd...`, 19063832 bytes | `c2c27170...`, 15370288 bytes | Built JNI is now available, but differs from both Gallery and Maven and is not stripped. |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `9053b81d...` | missing | missing | New candidate artifact, but not currently present in Gallery or Maven references. |
| `libLiteRtRuntimeCApi.so` | missing | missing | missing | The queried target produced `libLiteRt.so`, not a separate Runtime C API library in this build. |

## Dispatch And Capability Evidence

Built `libLiteRt.so` contains:

- `LiteRtDispatchCheckRuntimeCompatibility`
- `Dispatch API has insufficient capabilities: %d`
- `Failed to get Dispatch API capabilities: %d`
- `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- `Failed to initialize Dispatch API: %s`

Built `libLiteRtDispatch_Qualcomm.so` contains:

- `LiteRtDispatchGetApi@@VERS_1.0`
- `Qualcomm Dispatch API version %d.%d.%d, QNN API version %d.%d.%d, build id: %s`
- `ADSP_LIBRARY_PATH`
- `LD_LIBRARY_PATH`
- `libQnnSystem.so`
- `libQnnHtp.so`
- `libQnnDsp.so`
- QNN context and backend initialization messages

Built `liblitertlm_jni.so` contains:

- `LiteRtDispatchGetApi`
- `LiteRtDispatchGetApiVersion`
- the same JNI side needed to connect LiteRT-LM `Engine.initialize()` to the built LiteRT stack
- `NEEDED` entry for `libGemmaModelConstraintProvider.so`

These strings align with the current crash classification, but static presence is not enough to prove runtime compatibility.

## Interpretation

The built JNI, LiteRT, and dispatch artifacts are now from the same LiteRT-LM `v0.11.0` / pinned LiteRT ref build flow, so they are a better candidate for isolated testing than mixing Gallery JNI with a newly built dispatch. However, they still differ materially from Gallery SM8750:

- different build id
- different size
- different `NEEDED`
- built with a QAIRT 2.46 overlay for a source tree expecting QAIRT 2.44
- built `liblitertlm_jni.so` additionally requires `libGemmaModelConstraintProvider.so`

Therefore, replacing only Gallery or Maven dispatch remains risky. The next reasonable experiment is `ready-for-isolated-insertion` only if the built stack is staged together in a separate debug-only flavor with `libGemmaModelConstraintProvider.so` and the existing QNN/HTP runtime set. It must not touch `standardDebug` or `npuExperimentDebug`.
