# LiteRT-LM flavor version experiment plan

Date: 2026-05-16

Related baseline: `3346a59c Map Gallery LiteRT runtime versions`

This is a design-only investigation. It does not change app dependencies, replace native libraries, build dispatch, run NPU inference, call `Engine.initialize`, create `Conversation` / `Session`, or call `generateResponse`.

## Current dependency shape

Lami currently chooses LiteRT-LM by build type, not by product flavor:

```kotlin
val liteRtLmAndroidReleaseVersion = "0.10.0"
val liteRtLmAndroidDebugVersion = "0.11.0"

debugImplementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidDebugVersion")
releaseImplementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidReleaseVersion")
```

Current resolved debug classpaths:

| Variant classpath | Resolved `litertlm-android` |
| --- | --- |
| `standardDebugRuntimeClasspath` | `0.11.0` |
| `npuExperimentDebugRuntimeClasspath` | `0.11.0` |
| `standardDebugCompileClasspath` | `0.11.0` |
| `npuExperimentDebugCompileClasspath` | `0.11.0` |

Other relevant direct dependencies are currently shared across flavors:

| Dependency | Version | Scope |
| --- | --- | --- |
| `com.google.mediapipe:tasks-genai` | `0.10.33` | `implementation` |
| `com.qualcomm.qti:qnn-runtime` | `2.34.0` | `implementation` |
| `com.qualcomm.qti:qnn-litert-delegate` | `2.34.0` | `implementation` |

`tasks-genai`, `qnn-runtime`, and `qnn-litert-delegate` are direct roots in the runtime classpath. The QNN TFLite delegate is separate from LiteRT-LM Qualcomm dispatch.

## Can flavor-specific LiteRT-LM versions be split?

Yes, Gradle can split this by variant/flavor, but not by simply adding a lower version to `npuExperimentDebugImplementation`.

If `debugImplementation("...:0.11.0")` remains active, it applies to both `standardDebug` and `npuExperimentDebug`. Adding `npuExperimentDebugImplementation("...:0.10.0")` would put both `0.11.0` and `0.10.0` in the same variant graph, and Gradle conflict resolution is expected to select `0.11.0`.

The safer design is to remove the broad LiteRT-LM `debugImplementation` declaration and replace it with variant-specific declarations:

```kotlin
add("standardDebugImplementation", "com.google.ai.edge.litertlm:litertlm-android:0.11.0")
add("npuExperimentDebugImplementation", "com.google.ai.edge.litertlm:litertlm-android:0.10.0")
releaseImplementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
```

This should keep `standardDebug` on `0.11.0` and allow `npuExperimentDebug` to resolve `0.10.0`. `npuExperimentRelease` is already disabled.

## Implementation options

| Option | Description | Risk | Assessment |
| --- | --- | --- | --- |
| Version catalog split | Add separate aliases such as `litertlmAndroidDebug` and `litertlmAndroid010`. | low | Useful for readability, but does not solve conflict if broad `debugImplementation` remains. |
| Variant-specific dependency declarations | Use `add("standardDebugImplementation", ...)` and `add("npuExperimentDebugImplementation", ...)`. | medium | Recommended first experiment. It scopes the change to one variant and avoids global force. |
| Flavor-level `npuExperimentImplementation` | Set `npuExperimentImplementation("...:0.10.0")`. | medium/high | Conflicts with existing broad `debugImplementation(0.11.0)` unless debug dependency is moved. |
| Variant-specific `resolutionStrategy.force` | Force `0.10.0` only for `npuExperimentDebug*Classpath`. | high | Easy to accidentally affect compile/runtime differently or hide duplicate dependency roots. Use only if variant-specific declarations fail. |
| Separate module | Move LiteRT-LM integration behind a module per runtime generation. | medium/high | Cleaner boundary but larger refactor. Not needed for the first experiment. |
| Separate app module | Build a fully isolated 0.10.x / Gallery-stack experiment app. | high effort, lower blast radius | Best if staging Gallery native stack as a matched set becomes necessary. |

## Recommended first experiment

Do not change dependencies yet. In the next phase, test only this minimal Gradle change:

1. Replace broad `debugImplementation(litertlm-android:0.11.0)` with `standardDebugImplementation(0.11.0)`.
2. Add `npuExperimentDebugImplementation(litertlm-android:0.10.0)`.
3. Keep `releaseImplementation(0.10.0)`.
4. Run dependency reports before building:
   - `standardDebugRuntimeClasspath` must still resolve `0.11.0`.
   - `npuExperimentDebugRuntimeClasspath` must resolve only `0.10.0`.
5. If Gradle still resolves `0.11.0` in `npuExperimentDebug`, stop and inspect conflict roots rather than forcing globally.

## Risks

### Duplicate classes

If both `0.11.0` and `0.10.0` enter the same variant graph, duplicate class errors or silent Gradle conflict selection can occur. The dependency report script checks both compile and runtime classpaths to catch this.

### Native library packaging

The local Maven `litertlm-android:0.10.0` AAR contains only:

```text
liblitertlm_jni.so
```

It does not contain `libLiteRt.so`. Gallery SM8750 APK does contain `libLiteRt.so`, `liblitertlm_jni.so`, `libLiteRtDispatch_Qualcomm.so`, and QNN/HTP libraries. Therefore switching `npuExperimentDebug` to public Maven `0.10.0` alone is not equivalent to Gallery SM8750's native stack. It may also remove Lami debug's packaged `libLiteRt.so`, while the staged Gallery dispatch runtime declares `NEEDED libLiteRt.so`.

### Compile API mismatch

`0.10.0` exposes `Backend.NPU`, `Engine`, and `Engine.initialize`, but `EngineConfig` differs:

- `0.10.0`: `EngineConfig(String, Backend, Backend, Backend, Integer, String)`
- `0.11.0`: `EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)`

Current direct Kotlin calls use named arguments without `maxNumImages`, so the main compile surface looks likely compatible with `0.10.0`. Reflection probes should continue accepting both constructor shapes.

### Standard flavor impact

The first experiment must prove:

- `standardDebug` remains on `0.11.0`.
- `standardDebug` APK native payload remains unchanged.
- normal GPU inference remains `selectedPath=gpu` with `QNN/NPU attempted=no`.

## Decision

Flavor-specific version separation is Gradle-feasible, but public Maven `litertlm-android:0.10.0` is not a complete Gallery SM8750 native stack match. The next safe experiment is still useful, but expectations should be limited:

- It can test whether the `0.10.0` Java/Kotlin API and `liblitertlm_jni.so` behavior changes the dispatch failure.
- It is unlikely to fully match Gallery SM8750 unless a compatible `libLiteRt.so` and QNN/dispatch set are also isolated in a separate experiment.
- Do not proceed to native stack replacement in standard/debug. If a matched Gallery stack is needed, use a separate isolated flavor or app id.
