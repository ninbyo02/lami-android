# LiteRT-LM 0.10.x API surface check

Date: 2026-05-16

Artifact source: `artifacts/litertlm_flavor_dependencies/20260516_180112/`

This is a static AAR inspection only. No dependency was changed, no native library was replaced, and no NPU inference path was executed.

## Availability

| Version | Local AAR available | Notes |
| --- | --- | --- |
| `0.10.0` | yes | Present in Gradle cache and already used by Lami `releaseImplementation`. |
| `0.10.1` | no | Not present in local Gradle cache during this run. |
| `0.10.2` | no | Not present in local Gradle cache during this run. |
| `0.11.0` | yes | Present in Gradle cache and used by current debug variants. |

This check did not modify dependencies to force remote resolution of `0.10.1` or `0.10.2`.

## Class presence

`litertlm-android:0.10.0` contains:

| Class | Present |
| --- | --- |
| `com.google.ai.edge.litertlm.Backend` | yes |
| `com.google.ai.edge.litertlm.Backend$NPU` | yes |
| `com.google.ai.edge.litertlm.Backend$GPU` | yes |
| `com.google.ai.edge.litertlm.Backend$CPU` | yes |
| `com.google.ai.edge.litertlm.EngineConfig` | yes |
| `com.google.ai.edge.litertlm.Engine` | yes |
| `com.google.ai.edge.litertlm.Conversation` | yes |

## Backend.NPU

`0.10.0` exposes:

```text
Backend.NPU()
Backend.NPU(String nativeLibraryDir)
getNativeLibraryDir()
```

This matches the constructor needed by the existing instantiate-only probe.

## Engine API

`0.10.0` exposes:

```text
Engine(EngineConfig)
Engine.initialize(): void
Engine.close(): void
Engine.createConversation(ConversationConfig)
Engine.createSession(SessionConfig)
Engine.isInitialized()
```

The isolated dry-run APIs exist in `0.10.0`. This does not mean `Engine.initialize` is safe or compatible with the staged dispatch runtime.

## EngineConfig API difference

`0.10.0`:

```text
EngineConfig(String, Backend, Backend, Backend, Integer, String)
getModelPath()
getBackend()
getVisionBackend()
getAudioBackend()
getMaxNumTokens()
getCacheDir()
```

`0.11.0`:

```text
EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)
getModelPath()
getBackend()
getVisionBackend()
getAudioBackend()
getMaxNumTokens()
getMaxNumImages()
getCacheDir()
```

The extra `maxNumImages` parameter exists in `0.11.0`, not `0.10.0`.

Current direct Lami Kotlin calls use:

```kotlin
EngineConfig(
    modelPath = modelPath,
    backend = ...,
    visionBackend = Backend.GPU(),
    audioBackend = Backend.CPU(),
    maxNumTokens = null,
    cacheDir = cacheDirPath,
)
```

This shape should compile against `0.10.0` because it does not pass `maxNumImages`. Existing reflection probes should continue to support both constructor counts.

## MediaPipe preferredBackend path

This check does not change the earlier conclusion:

- MediaPipe `LlmInferenceOptions.Builder.setPreferredBackend(...)` is an enum path with `DEFAULT`, `CPU`, and `GPU`.
- LiteRT-LM `Backend.NPU` is a separate `com.google.ai.edge.litertlm.Backend` sealed hierarchy.
- The correct NPU connection candidate remains `EngineConfig.backend = Backend.NPU(nativeLibraryDir)`.

## Native libraries

### `litertlm-android:0.10.0`

| Library | SHA-256 | Build ID | NEEDED |
| --- | --- | --- | --- |
| `liblitertlm_jni.so` | `e31489778b249ccca66a5af7076aca17f84b6290a7faf8d129d020de3067d8c7` | `ecacedccf835d7674c95bd40186d0fde` | `libEGL.so`, `libGLESv2.so`, `libGLESv3.so`, `libdl.so`, `libm.so`, `libandroid.so`, `liblog.so`, `libc.so` |

The local `0.10.0` AAR does not include `libLiteRt.so`.

### `litertlm-android:0.11.0`

| Library | SHA-256 | Build ID | NEEDED |
| --- | --- | --- | --- |
| `libLiteRt.so` | `31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24` | `80fa0688ac32301185275c903cec97bd` | `libdl.so`, `libGLESv3.so`, `libEGL.so`, `libm.so`, `liblog.so`, `libc.so` |
| `libLiteRtClGlAccelerator.so` | `9204d082a9fe2deb1061d713deb5cfb85947f5ae848e05c5ff92e95d4e15d1fc` | `7703de4e247a2df3dc180a632027b91a` | `libGLESv3.so`, `libEGL.so`, `libm.so`, `libdl.so`, `liblog.so`, `libc.so` |
| `liblitertlm_jni.so` | `ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f` | `c2c27170ba409dbd0bc01820fa738580` | `libLiteRt.so`, `libandroid.so`, `libz.so`, `libGLESv2.so`, `libEGL.so`, `libdl.so`, `libGLESv3.so`, `libm.so`, `liblog.so`, `libc.so` |

### Gallery SM8750 comparison

| Library | Gallery SM8750 Build ID | Maven `0.10.0` Build ID | Match |
| --- | --- | --- | --- |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` | missing from local Maven `0.10.0` AAR | no |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` | `ecacedccf835d7674c95bd40186d0fde` | no |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | missing from Maven `0.10.0` AAR | no |

## Conclusion

`litertlm-android:0.10.0` is API-compatible enough to consider a scoped compile experiment, but it is not a native Build ID match for Gallery SM8750. It also lacks the `libLiteRt.so` that Gallery dispatch declares as a dependency.

Therefore, switching only `npuExperimentDebug` to Maven `0.10.0` is a useful Gradle isolation test, not a complete runtime-generation match.
