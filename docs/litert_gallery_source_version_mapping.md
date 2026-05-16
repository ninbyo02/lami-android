# Gallery SM8750 LiteRT runtime source/version mapping

Date: 2026-05-16

Related Lami baseline: `d897a6c7 Analyze LiteRT NPU dispatch compatibility`

This investigation is static only. It does not build LiteRT, LiteRT-LM, or `dispatch_api_so`; it does not replace native libraries; and it does not run NPU inference, `Conversation`, `Session`, or `generateResponse`.

## Goal

`npuExperimentDebug` proved that the Gallery SM8750 `libLiteRtDispatch_Qualcomm.so` is present in `nativeLibraryDir`, and that `Backend.NPU(String)` plus `EngineConfig.backend = Backend.NPU(...)` can be created. The isolated `Engine.initialize()` dry-run still aborts with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

The next question is whether Gallery 1.0.12 SM8750 and Lami are using the same LiteRT-LM / LiteRT / QNN runtime generation. The answer is no for the currently compared artifacts.

## Gallery APK metadata

APK: `/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk`

Observed metadata:

| Field | Value |
| --- | --- |
| package | `com.google.ai.edge.gallery` |
| versionName | `1.0.12` |
| versionCode | `29` |
| compileSdkVersion | `37` |
| targetSdkVersion | `35` |
| minSdkVersion | `31` |
| native-code | `arm64-v8a`, `x86_64` |
| label | `Edge Gallery` |
| notable manifest native libraries | `libvndksupport.so`, `libOpenCL.so`, `libcdsprpc.so` as not-required native libraries |

The APK contains string hints such as:

```text
third_party_ai_edge_gallery_Android_src_app_src_main__ai_edge_gallery_app_github_release_sm8750
```

This suggests the SM8750 APK is a specialized/internal release build target. It is not byte-for-byte explainable by the public Gradle project alone.

## Gallery source tag / commit candidates

The public `google-ai-edge/gallery` release tags match the APK version line:

| Gallery release | Tag | Commit | Evidence | Confidence |
| --- | --- | --- | --- | --- |
| 1.0.12 | `1.0.12` | `302f7e463b19f45f51825f4ec2fd30309366cb06` | Source `versionName = "1.0.12"` and `versionCode = 29`, matching the APK. | high for app source |
| 1.0.13 | `1.0.13` | `edbc39fc4f116714fe0f475e8289067ba13e8a11` | Next public release; relevant for comparison, but not the APK version. | low for this APK |

Important mismatch:

| Item | Public Gallery 1.0.12 Gradle source | SM8750 APK |
| --- | --- | --- |
| applicationId / package | `com.google.aiedge.gallery` | `com.google.ai.edge.gallery` |
| namespace | `com.google.ai.edge.gallery` | `com.google.ai.edge.gallery` |
| compileSdk | `35` | `37` |
| versionCode | `29` | `29` |
| versionName | `1.0.12` | `1.0.12` |
| QNN / dispatch packaging | not declared in Gradle source | native payload includes QNN and dispatch libs |

Conclusion: the app code generation likely aligns with public tag `1.0.12`, but the SM8750 APK native runtime is from a specialized release pipeline.

## Gallery public Gradle dependencies

Public Gallery `1.0.12` and `1.0.13` both show the same relevant dependency line:

| Component | Public Gallery source version |
| --- | --- |
| `com.google.ai.edge.litertlm:litertlm-android` | `0.10.0` |
| `com.google.android.gms:play-services-tflite-java` | `16.4.0` |
| `com.google.android.gms:play-services-tflite-gpu` | `16.4.0` |
| `com.google.android.gms:play-services-tflite-support` | `16.4.0` |
| `com.google.mlkit:genai-prompt` | `1.0.0-beta2` |
| `com.google.mediapipe:tasks-genai` | not declared |
| `com.qualcomm.qti:qnn-runtime` | not declared |
| `com.qualcomm.qti:qnn-litert-delegate` | not declared |

The public source uses the expected LiteRT-LM NPU path:

```kotlin
Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
EngineConfig(modelPath = modelPath, backend = preferredBackend, ...)
val engine = Engine(engineConfig)
engine.initialize()
engine.createConversation(...)
```

This confirms Lami's isolated probe is targeting the correct LiteRT-LM API family. It does not prove runtime compatibility.

## Lami dependency comparison

| Component | Lami configuration | Version |
| --- | --- | --- |
| `com.google.ai.edge.litertlm:litertlm-android` | debug | `0.11.0` |
| `com.google.ai.edge.litertlm:litertlm-android` | release | `0.10.0` |
| `com.google.mediapipe:tasks-genai` | main | `0.10.33` |
| `com.qualcomm.qti:qnn-runtime` | main | `2.34.0` |
| `com.qualcomm.qti:qnn-litert-delegate` | main | `2.34.0` |

The failing `npuExperimentDebug` uses Lami debug native libs, so the relevant comparison is Gallery SM8750 APK vs Lami `litertlm-android:0.11.0` plus QNN `2.34.0`.

## Native Build ID matrix

| Library | Gallery SM8750 APK | Lami debug / npuExperimentDebug | Assessment |
| --- | --- | --- | --- |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | same staged Gallery dispatch in `npuExperimentDebug` only | same file, isolated flavor only |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` | `80fa0688ac32301185275c903cec97bd` | different |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` | `c2c27170ba409dbd0bc01820fa738580` | different |
| `liblitertlm_jni.so` from Maven `0.10.0` | not the Gallery APK file | `ecacedccf835d7674c95bd40186d0fde` in local Maven cache | also different from Gallery |
| `libQnnSystem.so` | `0d409cdd664b8b0a` | `94d63184c6b1f968` | different |
| `libQnnHtp.so` | `f2c90c1775a109e1` | `e227353d86be672b` | different |
| `libQnnHtpPrepare.so` | not observed in same Gallery arm64 payload | `9ae62cf17f972404` | only Lami/QNN Maven payload |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` | `c079c75e0fd8ee92` | different |
| `libQnnHtpV79Skel.so` | no GNU Build ID, SHA differs | no GNU Build ID, SHA differs | different |
| `libQnnTFLiteDelegate.so` | not observed in Gallery SM8750 top-level arm64 payload | from `qnn-litert-delegate:2.34.0` | TensorFlow Lite QNN delegate, not LiteRT-LM dispatch |

The public Gallery Gradle version `litertlm-android:0.10.0` does not fully explain the SM8750 APK native payload. Local Maven `0.10.0` has a different `liblitertlm_jni.so` Build ID than the Gallery APK.

## Likely runtime generation

| Runtime area | Likely Gallery SM8750 generation | Confidence | Notes |
| --- | --- | --- | --- |
| App source | `google-ai-edge/gallery` tag `1.0.12` / commit `302f7e463b19f45f51825f4ec2fd30309366cb06` | high | versionName/versionCode match. |
| Public Gradle LiteRT-LM dependency | `litertlm-android:0.10.0` | high for public source | Visible in `libs.versions.toml`. |
| Actual Gallery SM8750 `liblitertlm_jni.so` / `libLiteRt.so` | specialized/internal build near Gallery 1.0.12 | medium | Build IDs do not match local Maven `0.10.0` or Lami debug `0.11.0`; APK string hints mention a `github_release_sm8750` target. |
| Gallery dispatch runtime | same specialized SM8750 native payload | medium | It links against `libLiteRt.so` and shares the Gallery packaging context. |
| QNN runtime | Gallery-packaged QNN/HTP set, not Lami `qnn-runtime:2.34.0` | medium | Build IDs differ for `libQnnSystem`, `libQnnHtp`, and V79 stub/skel. |

## Same-generation dispatch runtime options

1. Find a dispatch runtime that matches Lami debug `litertlm-android:0.11.0`.
   - Preferred path for Lami debug.
   - Requires source/provenance matching `libLiteRt.so` Build ID `80fa0688ac32301185275c903cec97bd` and `liblitertlm_jni.so` Build ID `c2c27170ba409dbd0bc01820fa738580`.

2. Align Lami to Gallery's runtime generation.
   - Public Gallery source says `litertlm-android:0.10.0`, but local Maven `0.10.0` does not match the Gallery APK native Build IDs.
   - Matching Gallery may require the same specialized/internal `github_release_sm8750` native stack, not just changing the Maven coordinate.

3. Use Gallery APK native stack as a matched-stack experiment.
   - Risk: high.
   - Only acceptable in a new isolated debug flavor or separate app id.
   - Do not mix into standard/debug, and do not replace Lami's production native libs in place.

4. Build `dispatch_api_so` from source.
   - Not recommended yet.
   - Only useful after identifying the exact LiteRT/LiteRT-LM source tag or commit for the native stack being targeted.
   - Public HEAD standalone builds are risky because dispatch API layout/capability mismatch is already a known failure mode.

5. Ask upstream with exact evidence.
   - Include device, model, Build IDs, tombstone signal, and the fact that Gallery dispatch is present but not usable with Lami `0.11.0`.

## Independent build decision

Do not build yet.

The useful target is not "any Qualcomm dispatch library"; it is "the dispatch runtime from the same generation as the LiteRT-LM / LiteRT native libraries in the APK". Current evidence says:

- Gallery SM8750 dispatch is same-generation with Gallery's native stack, not Lami debug `0.11.0`.
- Lami debug `0.11.0` needs a matching dispatch runtime for Build IDs `80fa0688ac32301185275c903cec97bd` and `c2c27170ba409dbd0bc01820fa738580`.
- Public Gallery `1.0.12` source helps identify app logic and a `litertlm-android:0.10.0` dependency, but does not expose the full SM8750 native packaging provenance.

## Recommended next actions

1. Search for the Maven/source provenance of `litertlm-android:0.11.0`, especially the LiteRT/LiteRT-LM commit corresponding to the Lami debug Build IDs.
2. Ask Google AI Edge / LiteRT-LM maintainers whether a `libLiteRtDispatch_Qualcomm.so` matching `litertlm-android:0.11.0` is published or can be built from a specific tag.
3. If testing a Gallery-matched native stack, create a new fully isolated flavor or app id and stage Gallery `libLiteRt.so`, `liblitertlm_jni.so`, dispatch, and QNN libs together. Treat this as high risk and keep it outside standard/debug.
4. Keep Lami standard and debug inference on GPU:
   - `selectedPath=gpu`
   - `QNN/NPU attempted=no`
   - no `Conversation`, `Session`, or `generateResponse` in the NPU experiment path.
