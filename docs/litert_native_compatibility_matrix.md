# LiteRT native compatibility matrix

Date: 2026-05-15

## Scope

This matrix compares native libraries from Google AI Edge Gallery SM8750 APK and the current Lami debug native payload.

This is a static ABI investigation only. No dispatch runtime was copied into Lami, no native library was loaded, and `Backend.NPU` was not connected to inference.

## Inputs

Left source:

- APK: `/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk`
- release: `google-ai-edge/gallery` `1.0.12`
- package: `com.google.ai.edge.gallery`
- versionName: `1.0.12`
- native-code: `arm64-v8a`, `x86_64`

Right source:

- Lami standard debug APK: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`
- Lami LiteRT-LM dependency: debug `litertlm-android:0.11.0`

Command:

```bash
bash scripts/compare_native_libs.sh \
  /tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk \
  app/build/outputs/apk/standard/debug/app-standard-debug.apk
```

## Summary

| Library | Gallery SM8750 | Lami debug | ABI risk | Notes |
| --- | --- | --- | --- | --- |
| `libLiteRt.so` | present, build id `869121bd7f4b0b77fa581218117a5c14` | present, build id `80fa0688ac32301185275c903cec97bd` | high | Same SONAME and symbol count, different build id/hash. |
| `liblitertlm_jni.so` | present, build id `76e4dccd9c5f9cba468d9cae7becfec0` | present, build id `c2c27170ba409dbd0bc01820fa738580` | high | Different build id/hash and different NEEDED set. |
| `libLiteRtDispatch_Qualcomm.so` | present, build id `643ad77b8ac2f54bd1b61e4133c77b3a` | missing | high | Do not copy alone without a matched LiteRT stack. |
| `libQnnHtp.so` | present, build id `f2c90c1775a109e1` | present, build id `e227353d86be672b` | medium | QNN runtime differs. |
| `libQnnHtpV79Skel.so` | present, no GNU build id | present, no GNU build id | medium | Both are QUALCOMM DSP6 objects, hashes differ. |
| `libQnnHtpV79Stub.so` | present, build id `10d7ad6f9195411a` | present, build id `c079c75e0fd8ee92` | medium | Stub build differs. |
| `libQnnSystem.so` | present, build id `0d409cdd664b8b0a` | present, build id `94d63184c6b1f968` | medium | QNN system build differs. |
| `libQnnTFLiteDelegate.so` | missing | present, build id `234bcfd44a262b4223beac759500b208a2cca949` | medium | TFLite delegate is not the LiteRT-LM dispatch runtime. |

## Detailed rows

| Source | Library | SHA-256 | Build ID | ELF class | SONAME | NEEDED | Exported symbols | `LiteRtDispatchGetApi` | `LiteRtQualcommOptionsGet` | Risk |
| --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- |
| Gallery | `libLiteRt.so` | `146f699ef6822a1e1f9489101a9dc5733e3788643396cab4fc768063cfde346c` | `869121bd7f4b0b77fa581218117a5c14` | ELF 64-bit ARM aarch64 shared object | `libLiteRt.so` | `libdl.so`, `libGLESv3.so`, `libEGL.so`, `libm.so`, `liblog.so`, `libc.so` | 397 | yes | no | high |
| Lami | `libLiteRt.so` | `31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24` | `80fa0688ac32301185275c903cec97bd` | ELF 64-bit ARM aarch64 shared object | `libLiteRt.so` | `libdl.so`, `libGLESv3.so`, `libEGL.so`, `libm.so`, `liblog.so`, `libc.so` | 397 | yes | no | high |
| Gallery | `liblitertlm_jni.so` | `607c4af2d405ff53a2a01415b47e202594b4e0dcce7f08f270bdfa7dd900c6d7` | `76e4dccd9c5f9cba468d9cae7becfec0` | ELF 64-bit ARM aarch64 shared object | `liblitertlm_jni.so` | `libdl.so`, `libm.so`, `libEGL.so`, `libGLESv2.so`, `libGLESv3.so`, `libandroid.so`, `liblog.so`, `libc.so` | 18 | no | no | high |
| Lami | `liblitertlm_jni.so` | `ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f` | `c2c27170ba409dbd0bc01820fa738580` | ELF 64-bit ARM aarch64 shared object | `liblitertlm_jni.so` | `libLiteRt.so`, `libandroid.so`, `libz.so`, `libGLESv2.so`, `libEGL.so`, `libdl.so`, `libGLESv3.so`, `libm.so`, `liblog.so`, `libc.so` | 22 | no | no | high |
| Gallery | `libLiteRtDispatch_Qualcomm.so` | `92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777` | `643ad77b8ac2f54bd1b61e4133c77b3a` | ELF 64-bit ARM aarch64 shared object | `libLiteRtDispatch_Qualcomm.so` | `libLiteRt.so`, `libandroid.so`, `liblog.so`, `libdl.so`, `libc.so`, `libm.so` | 1 | yes | no | high |
| Lami | `libLiteRtDispatch_Qualcomm.so` | none | none | none | none | none | 0 | no | no | high |
| Gallery | `libQnnHtp.so` | `090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a` | `f2c90c1775a109e1` | ELF 64-bit ARM aarch64 shared object | `libQnnHtp.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 4 | no | no | medium |
| Lami | `libQnnHtp.so` | `3e5592d4a7361082f958aa1534f1d3adb29639a74dd5d47a014a4ce37e9fd927` | `e227353d86be672b` | ELF 64-bit ARM aarch64 shared object | `libQnnHtp.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 4 | no | no | medium |
| Gallery | `libQnnHtpV79Skel.so` | `41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98` | none | ELF 32-bit QUALCOMM DSP6 shared object | `libQnnHtpV79Skel.so` | `libc++.so.1`, `libc++abi.so.1` | 4644 | no | no | medium |
| Lami | `libQnnHtpV79Skel.so` | `5590d6b34efdaef561155b77bc734a1a1e560767c180df9aba2dbceeb7ad28d1` | none | ELF 32-bit QUALCOMM DSP6 shared object | `libQnnHtpV79Skel.so` | `libc++.so.1`, `libc++abi.so.1` | 3775 | no | no | medium |
| Gallery | `libQnnHtpV79Stub.so` | `005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1` | `10d7ad6f9195411a` | ELF 64-bit ARM aarch64 shared object | `libQnnHtpV79Stub.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libcdsprpc.so` | 134 | no | no | medium |
| Lami | `libQnnHtpV79Stub.so` | `610d69e78e9a26e9e6b706dcebc9a199fbb058403dce36b67749715095f68166` | `c079c75e0fd8ee92` | ELF 64-bit ARM aarch64 shared object | `libQnnHtpV79Stub.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libcdsprpc.so` | 133 | no | no | medium |
| Gallery | `libQnnSystem.so` | `7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8` | `0d409cdd664b8b0a` | ELF 64-bit ARM aarch64 shared object | `libQnnSystem.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 1 | no | no | medium |
| Lami | `libQnnSystem.so` | `3bf3cf0841fccd8f14482a520d6c6f21f52e496de10253fa212d84eb06439994` | `94d63184c6b1f968` | ELF 64-bit ARM aarch64 shared object | `libQnnSystem.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` | 1 | no | no | medium |
| Gallery | `libQnnTFLiteDelegate.so` | none | none | none | none | none | 0 | no | no | medium |
| Lami | `libQnnTFLiteDelegate.so` | `92761e895e226a9a6d49ddc593b3a4d91fae86849958533bb31e9b1f736d36a4` | `234bcfd44a262b4223beac759500b208a2cca949` | ELF 64-bit ARM aarch64 shared object | `libQnnTFLiteDelegate.so` | `libm.so`, `liblog.so`, `libdl.so`, `libc.so` | 17 | no | no | medium |

## ABI risk notes

- `libLiteRtDispatch_Qualcomm.so` from Gallery is built with and depends on Gallery's `libLiteRt.so`.
- Lami's `libLiteRt.so` has the same SONAME and exported symbol count as Gallery, but a different build id and hash.
- Lami's `liblitertlm_jni.so` also differs from Gallery's build and dependency set.
- Dispatch strings include compatibility checks such as `Failed to initialize Dispatch API`, `Found Dispatch API with an unsupported version`, and dispatch API version logging.
- Known public issue reports about dispatch API struct mismatch are consistent with this high-risk single-library-copy scenario.

## Recommendation

Do not copy the Gallery dispatch runtime into main or release builds.

Use only the `npuExperimentDebug` isolated debug variant for any future local experiment, and keep the experiment diagnostic-only until the LiteRT stack compatibility is proven.
