# LiteRT / LiteRT-LM Custom Build Target Inventory

Date: 2026-05-16

This inventory identifies likely build targets for a future isolated custom build. No target was built in this phase.

## Primary Targets

| Component | Candidate target | Build system | Expected artifact | Source evidence | Notes |
| --- | --- | --- | --- | --- | --- |
| Qualcomm dispatch runtime | `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so` or `//litert/vendors/qualcomm/dispatch:dispatch_api_so` | Bazel | `libLiteRtDispatch_Qualcomm.so` | `LiteRT` commit `47615eb6...`, `litert/vendors/qualcomm/dispatch/BUILD` | Official guide-style target. Requires QAIRT/QNN headers. Must match `libLiteRt.so` dispatch API generation. |
| Qualcomm dispatch runtime filegroup | `//litert/vendors/qualcomm/dispatch:libLiteRtDispatch_Qualcomm.so` | Bazel | `libLiteRtDispatch_Qualcomm.so` | LiteRT integration/device spec references this filegroup | Useful for query/static packaging references. |
| LiteRT runtime C API | `@litert//litert/c:litert_runtime_c_api_so` | Bazel | `libLiteRt.so` | `litert/c/BUILD` | Gallery SM8750 includes `libLiteRt.so`; Maven `0.11.0` also includes one with a different Build ID. |
| LiteRT runtime C API filegroup | `@litert//litert/c:libLiteRt.so` | Bazel | `libLiteRt.so` | `litert/c/BUILD` | Static compare target candidate. |
| Qualcomm compiler plugin | `@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so` | Bazel | `libLiteRtCompilerPlugin_Qualcomm.so` | `litert/vendors/qualcomm/compiler/BUILD` | Official LiteRT Qualcomm integration specs reference a compiler plugin. Gallery SM8750 APK did not show this library, but it may matter for compiled model workflows. |
| LiteRT-LM JNI | `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni` | Bazel | `liblitertlm_jni.so` | `LiteRT-LM v0.11.0`, `kotlin/java/com/google/ai/edge/litertlm/jni/BUILD` | If dispatch-only still mismatches, a matched JNI/runtime/dispatch stack may be required. |
| LiteRT-LM Android library | `//kotlin/java/com/google/ai/edge/litertlm:litertlm-android` | Bazel | Android library/AAR-style output | `LiteRT-LM v0.11.0`, `kotlin/java/com/google/ai/edge/litertlm/BUILD` | May be needed to reproduce Maven Java/native packaging, but target packaging may differ from Maven publication. |
| LiteRT-LM CLI | `//runtime/engine:litert_lm_main` | Bazel | host/device CLI binary | `LiteRT-LM v0.11.0`, `runtime/engine/BUILD` | Useful for independent model/runtime testing later, not for Android app integration first. |
| LiteRT-LM advanced CLI | `//runtime/engine:litert_lm_advanced_main` | Bazel | host/device CLI binary | `LiteRT-LM v0.11.0`, `runtime/engine/BUILD` | Secondary investigation target. |

## Qualcomm / QNN Runtime Inputs

These are not built by LiteRT. They come from QAIRT/QNN SDK or packaged APK artifacts:

| Runtime library | Role | Build source | Current evidence |
| --- | --- | --- | --- |
| `libQnnSystem.so` | QNN system runtime | QAIRT/QNN SDK or Gallery APK | Gallery Build ID `0d409cdd664b8b0a`; loaded in `galleryStackExperimentDebug` tombstone. |
| `libQnnHtp.so` | HTP backend runtime | QAIRT/QNN SDK or Gallery APK | Gallery Build ID `f2c90c1775a109e1`; loaded in `galleryStackExperimentDebug` tombstone. |
| `libQnnHtpPrepare.so` | HTP prepare runtime | QAIRT/QNN SDK or Gallery APK | Gallery Build ID `9ae62cf17f972404`; present in native payload. |
| `libQnnHtpV79Stub.so` | V79 host stub | QAIRT/QNN SDK or Gallery APK | Gallery Build ID `10d7ad6f9195411a`; present in native payload. |
| `libQnnHtpV79Skel.so` | V79 DSP skel | QAIRT/QNN SDK or Gallery APK | Present in Gallery stack, no GNU Build ID. |

## Integration Spec Clues

The LiteRT Qualcomm device integration spec references a richer set than dispatch alone:

- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libQnnHtp.so`
- `libQnnHtpV79Stub.so`
- `libQnnSystem.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV79Skel.so`
- `LD_LIBRARY_PATH` and `ADSP_LIBRARY_PATH` layout

This does not prove the Android LiteRT-LM path requires every item, but it raises the risk that dispatch-only experiments are incomplete.

## First Query Targets

When build tools are ready, run query/cquery before any native build:

```bash
bazel query '@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so'
bazel query '@litert//litert/c:litert_runtime_c_api_so'
bazel query '@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so'
bazel query '//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni'
```

Only after target visibility and dependency generation are understood should any build be attempted.

