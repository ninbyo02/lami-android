# LiteRT-LM runtime version matrix

Date: 2026-05-16

Source artifact:

- `artifacts/litert_runtime_compatibility/20260516_101919/`
- Script: `bash scripts/analyze_litert_runtime_compatibility.sh`
- Lami APK: `app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk`
- Standard APK: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`
- Gallery SM8750 APK: `/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk`

This is a static compatibility investigation only. No native library was replaced, no LiteRT or dispatch runtime was built, and no NPU inference path was enabled.

## Gradle dependency versions

| Component | Configuration | Version | AAR path | arm64 native libs |
| --- | --- | --- | --- | --- |
| `com.google.ai.edge.litertlm:litertlm-android` | debug | `0.11.0` | `/home/sato/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litertlm/litertlm-android/0.11.0/d875b71661a00cdd0a2c03b43e1462326deb566a/litertlm-android-0.11.0.aar` | `libLiteRt.so`, `libLiteRtClGlAccelerator.so`, `liblitertlm_jni.so` |
| `com.google.ai.edge.litertlm:litertlm-android` | release | `0.10.0` | `/home/sato/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litertlm/litertlm-android/0.10.0/68c4556aeea478e7a9aad270576d6fc504db8a79/litertlm-android-0.10.0.aar` | `liblitertlm_jni.so` |
| `com.google.mediapipe:tasks-genai` | main | `0.10.33` | `/home/sato/.gradle/caches/modules-2/files-2.1/com.google.mediapipe/tasks-genai/0.10.33/d664be353d890cb3abb5af67989c47bae1875e01/tasks-genai-0.10.33.aar` | `libllm_inference_engine_jni.so` |
| `com.qualcomm.qti:qnn-runtime` | main | `2.34.0` | `/home/sato/.gradle/caches/modules-2/files-2.1/com.qualcomm.qti/qnn-runtime/2.34.0/bae16abb20cac8e7c4af165343f07e276f153951/qnn-runtime-2.34.0.aar` | `libQnnDsp.so`, `libQnnGpu.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so`, V68/V69/V73/V75/V79 skel/stub libs |
| `com.qualcomm.qti:qnn-litert-delegate` | main | `2.34.0` | `/home/sato/.gradle/caches/modules-2/files-2.1/com.qualcomm.qti/qnn-litert-delegate/2.34.0/c3424a4b4a293c004e6736e71b88ea2fcd44373f/qnn-litert-delegate-2.34.0.aar` | `libQnnTFLiteDelegate.so`, `libqnn_delegate_jni.so` |

## Lami Maven AAR native IDs

| Source | Library | SHA-256 | Build ID | NEEDED | Exported symbols | Dispatch API exports |
| --- | --- | --- | --- | --- | ---: | --- |
| `litertlm-android:0.11.0` | `libLiteRt.so` | `31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24` | `80fa0688ac32301185275c903cec97bd` | `libdl.so`, `libGLESv3.so`, `libEGL.so`, `libm.so`, `liblog.so`, `libc.so` | 397 | `LiteRtDispatchGetApiVersion`, `LiteRtDispatchGetCapabilities` |
| `litertlm-android:0.11.0` | `liblitertlm_jni.so` | `ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f` | `c2c27170ba409dbd0bc01820fa738580` | `libLiteRt.so`, `libandroid.so`, `libz.so`, `libGLESv2.so`, `libEGL.so`, `libdl.so`, `libGLESv3.so`, `libm.so`, `liblog.so`, `libc.so` | 22 | none exported |
| `litertlm-android:0.10.0` | `liblitertlm_jni.so` | `e31489778b249ccca66a5af7076aca17f84b6290a7faf8d129d020de3067d8c7` | `ecacedccf835d7674c95bd40186d0fde` | `libEGL.so`, `libGLESv2.so`, `libGLESv3.so`, `libdl.so`, `libm.so`, `libandroid.so`, `liblog.so`, `libc.so` | 18 | none exported |
| `qnn-runtime:2.34.0` | `libQnnSystem.so` | `3bf3cf0841fccd8f14482a520d6c6f21f52e496de10253fa212d84eb06439994` | `94d63184c6b1f968` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 1 | none |
| `qnn-runtime:2.34.0` | `libQnnHtp.so` | `3e5592d4a7361082f958aa1534f1d3adb29639a74dd5d47a014a4ce37e9fd927` | `e227353d86be672b` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 4 | none |
| `qnn-runtime:2.34.0` | `libQnnHtpPrepare.so` | `b178fcb21b68062e7b7aa7a0531a65f194ecc6dcaba9ad9b0b4ef8d54bced21b` | `9ae62cf17f972404` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 6032 | none |
| `qnn-runtime:2.34.0` | `libQnnHtpV79Stub.so` | `610d69e78e9a26e9e6b706dcebc9a199fbb058403dce36b67749715095f68166` | `c079c75e0fd8ee92` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libcdsprpc.so` | 133 | none |
| `qnn-runtime:2.34.0` | `libQnnHtpV79Skel.so` | `5590d6b34efdaef561155b77bc734a1a1e560767c180df9aba2dbceeb7ad28d1` | none | `libc++.so.1`, `libc++abi.so.1` | 3775 | none |

## Gallery SM8750 native IDs

| Source | Library | SHA-256 | Build ID | NEEDED | Exported symbols | Dispatch API exports |
| --- | --- | --- | --- | --- | ---: | --- |
| Gallery SM8750 | `libLiteRt.so` | `146f699ef6822a1e1f9489101a9dc5733e3788643396cab4fc768063cfde346c` | `869121bd7f4b0b77fa581218117a5c14` | `libdl.so`, `libGLESv3.so`, `libEGL.so`, `libm.so`, `liblog.so`, `libc.so` | 397 | `LiteRtDispatchGetApiVersion`, `LiteRtDispatchGetCapabilities` |
| Gallery SM8750 | `liblitertlm_jni.so` | `607c4af2d405ff53a2a01415b47e202594b4e0dcce7f08f270bdfa7dd900c6d7` | `76e4dccd9c5f9cba468d9cae7becfec0` | `libdl.so`, `libm.so`, `libEGL.so`, `libGLESv2.so`, `libGLESv3.so`, `libandroid.so`, `liblog.so`, `libc.so` | 18 | none exported |
| Gallery SM8750 | `libLiteRtDispatch_Qualcomm.so` | `92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777` | `643ad77b8ac2f54bd1b61e4133c77b3a` | `libLiteRt.so`, `libandroid.so`, `liblog.so`, `libdl.so`, `libc.so`, `libm.so` | 1 | `LiteRtDispatchGetApi@@VERS_1.0` |
| Gallery SM8750 | `libQnnSystem.so` | `7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8` | `0d409cdd664b8b0a` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 1 | none |
| Gallery SM8750 | `libQnnHtp.so` | `090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a` | `f2c90c1775a109e1` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 4 | none |
| Gallery SM8750 | `libQnnHtpV79Stub.so` | `005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1` | `10d7ad6f9195411a` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libcdsprpc.so` | 134 | none |
| Gallery SM8750 | `libQnnHtpV79Skel.so` | `41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98` | none | `libc++.so.1`, `libc++abi.so.1` | 4644 | none |

## Same / different / missing summary

| Library | State: Gallery SM8750 vs Lami npuExperimentDebug | Notes |
| --- | --- | --- |
| `libLiteRtDispatch_Qualcomm.so` | same | Lami staged the exact Gallery SM8750 dispatch runtime in `npuExperimentDebug` only. |
| `libLiteRt.so` | different | Same SONAME and exported symbol count, different hash and Build ID. |
| `liblitertlm_jni.so` | different | Different hash, Build ID, NEEDED set, and exported symbol count. |
| `libQnnSystem.so` | different | Gallery and Lami QNN system libs differ. |
| `libQnnHtp.so` | different | Gallery and Lami HTP backend libs differ. |
| `libQnnHtpV79Stub.so` | different | V79 stub differs. |
| `libQnnHtpV79Skel.so` | different | V79 skel hash and exported symbol count differ. |
| `libQnnHtpPrepare.so` | only in Lami APK comparison | Lami receives this from `qnn-runtime:2.34.0`; Gallery SM8750 APK top-level payload did not contain it in the same arm64 location. |
| `libQnnTFLiteDelegate.so` | only in Lami | This is the TensorFlow Lite QNN delegate, not the LiteRT-LM Qualcomm dispatch runtime. |

## Static dispatch/version hints

`libLiteRtDispatch_Qualcomm.so` contains:

- `LiteRtDispatchGetApi@@VERS_1.0`
- `Qualcomm Dispatch API version %d.%d.%d, QNN API version %d.%d.%d, build id: %s`
- `LiteRT API version (%d.%d.%d) is older than the dispatch api version (%d.%d.%d). An update is recommended.`
- QNN version checks for system/backend/library mismatches and unsupported versions.
- Context binary compatibility checks for SDK newer/older/matching cases.
- Build path hint: `third_party/odml/litert/litert/vendors/qualcomm/dispatch`.

Lami `libLiteRt.so` and Gallery `libLiteRt.so` both expose dispatch API support strings and symbols:

- `LiteRtDispatchGetApiVersion@@VERS_1.0`
- `LiteRtDispatchGetCapabilities@@VERS_1.0`
- `Dispatch API capabilities: %d`
- `Dispatch API graph interface not found`
- `Failed to initialize Dispatch API: %s`
- `get_capabilities not found`

Lami `liblitertlm_jni.so` contains the higher-level abort/failure strings:

- `Dispatch API has insufficient capabilities: %d`
- `Failed to get Dispatch API capabilities: %d`
- `Found Dispatch API with an unsupported version`
- `LiteRtDispatchGetApi`

The staged dispatch runtime is present and export-compatible at the symbol-name level, but the stack still aborts with `No usable Dispatch runtime found`; therefore symbol presence alone is not enough to prove dispatch API usability.

## Runtime search notes

On the connected NX733J / SM8750 device, the installed `npuExperimentDebug` app native library directory contained:

- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRt.so`
- `liblitertlm_jni.so`
- `libQnnSystem.so`
- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV79Skel.so`
- `libQnnHtpV79Stub.so`

Hashes matched the packaged Lami APK values. `/data/local/tmp/qairt` also contains a separate QAIRT tree with another `libLiteRtDispatch_Qualcomm.so` and QNN/HTP V79 libs, but the app dry-run uses the APK/nativeLibraryDir payload, not that external tree.
