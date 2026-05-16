# LiteRT-LM Gallery Java API Surface Mismatch

Date: 2026-05-16

This is a static API surface comparison only. It does not change Gradle dependencies, does not replace native libraries, does not run `Engine.initialize`, does not run NPU inference, and does not call `Conversation`, `Session`, or `generateResponse`.

## Inputs

Gallery APK:

- path: `/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk`
- package: `com.google.ai.edge.gallery`
- versionName: `1.0.12`
- versionCode: `29`
- compileSdkVersion: `37`
- SHA-256: `cb0eb290c546de29a48864fd3972d8b8a487f5a87e277447f52377ffa60ee5ba`

Comparison artifact:

- `artifacts/litertlm_api_surface_compare/20260516_201159/`

Gallery native Build IDs already staged in `galleryStackExperimentDebug`:

| Library | Build ID |
| --- | --- |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` |
| `libQnnSystem.so` | `0d409cdd664b8b0a` |
| `libQnnHtp.so` | `f2c90c1775a109e1` |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` |

Lami Maven `litertlm-android:0.10.0` native payload:

| Library | Build ID |
| --- | --- |
| `liblitertlm_jni.so` | `ecacedccf835d7674c95bd40186d0fde` |
| `libLiteRt.so` | not packaged |

## Extracted Classes

The script extracted `71` `com.google.ai.edge.litertlm.*` classes from Gallery `classes*.dex`.

The key classes were found:

- `com.google.ai.edge.litertlm.Engine`
- `com.google.ai.edge.litertlm.EngineConfig`
- `com.google.ai.edge.litertlm.Backend`
- `com.google.ai.edge.litertlm.Backend$NPU`
- `com.google.ai.edge.litertlm.Backend$CPU`
- `com.google.ai.edge.litertlm.Backend$GPU`
- `com.google.ai.edge.litertlm.LiteRtLmJni`

The Maven `0.10.0` and `0.11.0` AARs each contain `68` `com.google.ai.edge.litertlm.*` classes. Gallery has extra desugared/synthetic classes such as `ResponseCallback$-CC` and `*ExternalSyntheticBackport0`; those are not the primary ABI issue.

## nativeCreateEngine Descriptor

This is the critical mismatch.

| Source | Descriptor | Match Gallery |
| --- | --- | --- |
| Gallery APK dex | `(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;ZLjava/lang/Boolean;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)J` | yes |
| Maven `0.10.0` AAR | `(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;ZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;II)J` | no |
| Maven `0.11.0` AAR | `(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;ZLjava/lang/Boolean;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)J` | yes |

Human-readable difference:

- Gallery / Maven `0.11.0`: `String, String, String, String, int, int, String, boolean, Boolean, String, String, String, int, int`
- Maven `0.10.0`: `String, String, String, String, int, String, boolean, boolean, String, String, String, int, int`

The Gallery JNI library expects the `0.11.0` Java native method layout, not the public Maven `0.10.0` layout.

## Engine.initialize Difference

`Engine.initialize()` exists in Gallery, Maven `0.10.0`, and Maven `0.11.0`:

```text
initialize(): void
```

The difference is the native call that `Engine.initialize()` eventually performs.

Maven `0.10.0` invokes:

```text
LiteRtLmJni.nativeCreateEngine:
(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;ZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;II)J
```

Maven `0.11.0` invokes:

```text
LiteRtLmJni.nativeCreateEngine:
(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;ZLjava/lang/Boolean;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)J
```

Gallery dex declares the same descriptor as Maven `0.11.0`.

## EngineConfig Difference

Gallery `EngineConfig` constructor:

```text
EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)
```

Maven `0.10.0` constructor:

```text
EngineConfig(String, Backend, Backend, Backend, Integer, String)
```

Maven `0.11.0` constructor:

```text
EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)
```

Gallery `EngineConfig` includes:

- `maxNumTokens: Integer`
- `maxNumImages: Integer`
- `cacheDir: String`

Maven `0.10.0` lacks `maxNumImages`. This aligns with the `nativeCreateEngine` descriptor mismatch: Gallery native expects the extra integer field and nullable Boolean path used by the `0.11.0` API surface.

## Backend.NPU Difference

`Backend.NPU` is compatible across Gallery, Maven `0.10.0`, and Maven `0.11.0` for the constructor surface:

```text
NPU()
NPU(String nativeLibraryDir)
getNativeLibraryDir(): String
```

This explains why `Backend.NPU(String)` instantiate-only succeeded even when `Engine.initialize()` later crashed.

## Crash Alignment

Latest `galleryStackExperimentDebug` crash:

```text
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)
libart.so CheckJNI::GetStringCharsInternal
liblitertlm_jni.so Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+816
com.google.ai.edge.litertlm.Engine.initialize
```

This is strongly consistent with a Java/native method descriptor mismatch:

- Lami `galleryStackExperimentDebug` currently compiles against Maven `litertlm-android:0.10.0` classes.
- Gallery `liblitertlm_jni.so` was staged from the SM8750 APK.
- Gallery `liblitertlm_jni.so` matches the Gallery dex / Maven `0.11.0` `nativeCreateEngine` descriptor.
- Maven `0.10.0` calls the same native function name with a shorter and differently ordered argument list.
- CheckJNI then crashes while the native side treats a mismatched argument slot as a Java `String`.

The current crash is therefore not a reliable dispatch runtime capability result. It happens before the native stack can be trusted as correctly wired.

## Conclusion

Gallery JNI and Maven `0.10.0` Java API surface do not match.

Mixing Gallery native libraries with Maven `litertlm-android:0.10.0` Java/Kotlin classes is not a valid experiment for `Engine.initialize`. The observed CheckJNI SIGSEGV is expected from this mismatch.

Gallery JNI is closer to Maven `litertlm-android:0.11.0` at the Java native method descriptor level, but that does not prove full compatibility because Gallery `libLiteRt.so`, Gallery `liblitertlm_jni.so`, and Gallery dispatch/QNN payload still come from a distinct native generation.

## Next Actions

If Gallery Java API surface and Maven `0.10.0` had matched, the next suspect would have been a null optional String/path or unsupported config value inside `nativeCreateEngine`. They do not match, so that path is not the current priority.

Recommended next step:

1. Do not continue `Engine.initialize` with Gallery native libs plus Maven `0.10.0` classes.
2. Either align `galleryStackExperimentDebug` Java classes to the Gallery/`0.11.0` `nativeCreateEngine` descriptor, or build a tiny isolated launcher from the same Gallery source/API generation.
3. Keep any Java/native alignment experiment under a separate app id or `galleryStackExperimentDebug`; do not affect `standardDebug` or normal GPU inference.
4. Do not build `dispatch_api_so` yet. A dispatch-only build cannot fix a Java/native `LiteRtLmJni.nativeCreateEngine` descriptor mismatch.

Independent build decision:

- If Java/native API surface is mismatched, `dispatch_api_so` alone is insufficient.
- A meaningful build or staged payload must align `liblitertlm_jni.so`, Java/Kotlin classes, `libLiteRt.so`, and dispatch runtime from the same source generation.
