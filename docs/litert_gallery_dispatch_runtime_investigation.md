# LiteRT Gallery Qualcomm dispatch runtime investigation

Date: 2026-05-15

## Scope

This note records whether Google AI Edge Gallery APKs include the LiteRT Qualcomm NPU dispatch runtime that Lami currently lacks.

This investigation does not enable `Backend.NPU` in Lami and does not copy any Gallery native library into Lami.

## APKs checked

Local APK search under `~/Downloads`, `~/project`, `~/project/lami-android`, and the repository root did not find a Gallery APK.

Official GitHub release assets were checked with `gh`:

- `google-ai-edge/gallery` release `1.0.13`, published 2026-05-05: `ai-edge-gallery.apk`
- `google-ai-edge/gallery` release `1.0.12`, published 2026-04-24: `ai-edge-gallery-sm8750.apk`

`1.0.13` is the latest release found, but it only provides a generic APK. The SM8750-specific APK exists in `1.0.12`, so that APK is the primary dispatch runtime source investigated here.

Downloaded files:

- `/tmp/lami-gallery-apks/1.0.13/ai-edge-gallery.apk`
- `/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk`

## APK metadata

`ai-edge-gallery-sm8750.apk`:

- package: `com.google.ai.edge.gallery`
- versionCode: `29`
- versionName: `1.0.12`
- sdkVersion: `31`
- targetSdkVersion: `35`
- native-code: `arm64-v8a`, `x86_64`

`ai-edge-gallery.apk` from `1.0.13`:

- package: `com.google.ai.edge.gallery`
- versionCode: `30`
- versionName: `1.0.13`
- sdkVersion: `31`
- targetSdkVersion: `35`
- native-code: `arm64-v8a`, `x86_64`

`apktool` was not available in the local PATH, so full apktool decode was not performed.

## Native library contents

`ai-edge-gallery-sm8750.apk` includes these relevant `arm64-v8a` libraries:

- `lib/arm64-v8a/libLiteRt.so`
- `lib/arm64-v8a/libLiteRtDispatch_Qualcomm.so`
- `lib/arm64-v8a/libQnnHtp.so`
- `lib/arm64-v8a/libQnnHtpV79Skel.so`
- `lib/arm64-v8a/libQnnHtpV79Stub.so`
- `lib/arm64-v8a/libQnnSystem.so`
- `lib/arm64-v8a/liblitertlm_jni.so`

`ai-edge-gallery.apk` from `1.0.13` does not include the Qualcomm dispatch runtime. Its relevant `arm64-v8a` native payload is limited to:

- `lib/arm64-v8a/liblitertlm_jni.so`

## Dispatch runtime candidate

Candidate found:

- source APK: `ai-edge-gallery-sm8750.apk`
- APK path: `lib/arm64-v8a/libLiteRtDispatch_Qualcomm.so`
- extracted path: `/tmp/lami-gallery-apks/extracted-sm8750/lib/arm64-v8a/libLiteRtDispatch_Qualcomm.so`
- sha256: `92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777`

`file` result:

```text
ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=643ad77b8ac2f54bd1b61e4133c77b3a, stripped
```

Build ID:

```text
643ad77b8ac2f54bd1b61e4133c77b3a
```

Dynamic dependencies:

```text
NEEDED libLiteRt.so
NEEDED libandroid.so
NEEDED liblog.so
NEEDED libdl.so
NEEDED libc.so
NEEDED libm.so
SONAME libLiteRtDispatch_Qualcomm.so
```

Exported dispatch entry point:

```text
0000000000023f50 T LiteRtDispatchGetApi@@VERS_1.0
```

The runtime also references LiteRT APIs with `VERS_1.0`, such as:

- `LiteRtCreateTensorBufferRequirements`
- `LiteRtFindOpaqueOptionsData`
- `LiteRtGetTensorBufferFastRpcBuffer`
- `LiteRtLockTensorBuffer`
- `LiteRtUnlockTensorBuffer`

Relevant strings include:

- `//third_party/odml/litert/litert/vendors/qualcomm/dispatch:dispatch_api_so`
- `blaze-out/arm64-v8a-opt-android-ST-02e23770d8ba/bin/third_party/odml/litert/litert/vendors/qualcomm/dispatch/libLiteRtDispatch_Qualcomm.so`
- `Qualcomm Dispatch API version %d.%d.%d, QNN API version %d.%d.%d, build id: %s`
- `LiteRT API version (%d.%d.%d) is older than the dispatch api version (%d.%d.%d). An update is recommended.`
- `Incompatible dispatch version. Found LiteRT API version %d.%d.%d, but version <= %d.%d.%d is required.`
- `SM8750`
- `libQnnHtp.so`
- `libQnnDsp.so`
- `libQnnSystem.so`
- `QnnInterface_getProviders`
- `QnnSystemInterface_getProviders`

## Lami LiteRT comparison

Lami debug currently packages `litertlm-android:0.11.0` native libraries.

Lami `libLiteRt.so`:

- path: `app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libLiteRt.so`
- build id: `80fa0688ac32301185275c903cec97bd`
- sha256: `31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24`

Gallery SM8750 `libLiteRt.so`:

- build id: `869121bd7f4b0b77fa581218117a5c14`
- sha256: `146f699ef6822a1e1f9489101a9dc5733e3788643396cab4fc768063cfde346c`

Lami `liblitertlm_jni.so`:

- build id: `c2c27170ba409dbd0bc01820fa738580`
- sha256: `ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f`

Gallery SM8750 `liblitertlm_jni.so`:

- build id: `76e4dccd9c5f9cba468d9cae7becfec0`
- sha256: `607c4af2d405ff53a2a01415b47e202594b4e0dcce7f08f270bdfa7dd900c6d7`

The Gallery dispatch runtime is therefore not proven ABI-identical to the LiteRT stack currently packaged by Lami. It was built and released together with Gallery's own `libLiteRt.so` and `liblitertlm_jni.so`.

## Public LiteRT issue alignment

This finding is consistent with reports that `litertlm-android` exposes `Backend.NPU`, while the public Maven AAR does not include `libLiteRtDispatch_Qualcomm.so`.

It also supports the ABI mismatch concern: a dispatch runtime from another LiteRT build, including public LiteRT HEAD or a Gallery split APK, can export the right `LiteRtDispatchGetApi` entry point but still be version- or ABI-incompatible with Lami's packaged `libLiteRt.so` / `liblitertlm_jni.so`.

## Lami packaging assessment

Current recommendation: `unknown`.

The Gallery SM8750 APK contains a real arm64 Qualcomm dispatch runtime and it is technically an APK packaging candidate. However, Lami should not copy it directly yet because:

- license and redistribution terms for extracting a native library from the Gallery APK need review;
- the Gallery SM8750 `libLiteRt.so` build id differs from Lami's `libLiteRt.so`;
- the Gallery SM8750 `liblitertlm_jni.so` build id differs from Lami's `liblitertlm_jni.so`;
- dispatch strings explicitly include version compatibility checks;
- copying only the dispatch `.so` may reproduce the known ABI mismatch failure mode.

Safe next step:

1. Build or obtain `libLiteRtDispatch_Qualcomm.so` from the same LiteRT/LiteRT-LM revision as Lami's `litertlm-android` AAR, or obtain an officially redistributable package that includes a matched LiteRT stack.
2. If using Gallery as a local experiment only, test it in a separate throwaway branch/device build and preserve GPU fallback.
3. Confirm Lami DEV diagnostics show:
   - `dispatch API status: found`
   - `readiness: npu-prerequisites-present-probe-only`
   - `QNN/NPU試行: no`
   - `selectedPath: gpu`

Still not allowed in this phase:

- connecting `Backend.NPU` to real inference;
- forcing NPU;
- removing GPU fallback;
- changing held engine or official-flow behavior.
