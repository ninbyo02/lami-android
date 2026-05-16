# LiteRT / LiteRT-LM Qualcomm Custom Build Risk Analysis

Date: 2026-05-16

## Main Risk

The current failure occurs even when a real Gallery SM8750 dispatch runtime is present. This means a public HEAD or arbitrary dispatch-only build can easily produce another `.so` that exports the expected symbol but is still not usable by LiteRT-LM.

## Specific Risks

### Public HEAD Dispatch Mismatch

`libLiteRtDispatch_Qualcomm.so` participates in a dispatch API/capability contract with `libLiteRt.so` and the LiteRT-LM JNI. If HEAD changed structs, capabilities, checks, or symbol expectations, a HEAD-built dispatch may fail with the same `No usable Dispatch runtime found` behavior or a worse native abort.

### Matched Native Stack Requirement

The likely compatibility unit is:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- possibly `libLiteRtCompilerPlugin_Qualcomm.so`
- QNN runtime libraries

Replacing only one component can break Java/native descriptors, dispatch API layout, or QNN version negotiation.

### Maven Artifact Provenance

Maven `litertlm-android` artifacts may be produced from a CI/internal source layout that is not trivially reproducible from a public tag. Build IDs observed in Gallery and Maven artifacts do not currently map cleanly to a known source build command.

### Gallery Native Payload Provenance

Gallery SM8750 APK appears to include a special native payload that is not explained by public Gallery Gradle dependency `litertlm-android:0.10.0` alone. Reusing its native libraries outside Gallery may be unsupported or require matching Java/runtime packaging.

### QNN SDK Version Mismatch

The device reports external QAIRT/QNN capability, but the app native payload, local SDK, and model may require a specific QNN generation. Mismatch can surface as:

- failed dispatch compatibility checks
- insufficient capabilities
- HTP backend initialization failure
- skel/stub compatibility failure

### Android 16 / SM8750 / V79 Uncertainty

SM8750 / Hexagon V79 is a new target relative to many public examples. Even with correct files, support may depend on exact runtime/model/compiler generation.

### Licensing And Redistribution

QNN/QAIRT and Gallery APK native libraries may carry redistribution restrictions. A local experiment does not imply the artifacts can be shipped in Lami.

### Crash Risk

`Engine.initialize()` can abort from native code. Kotlin/Java cannot catch SIGABRT or SIGSEGV. All experiments must stay in isolated flavors/applicationIds with stage files and tombstone collection.

## Why Isolated Flavor Is Mandatory

Normal Lami GPU inference is working. The NPU path must not affect:

- `standardDebug`
- release builds
- held engine reuse
- normal model selection
- `selectedPath=gpu`

Only isolated debug flavors may stage experimental native libraries.

## Rollback Plan

If a custom artifact experiment regresses:

1. Remove only files under `app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a/` that were staged for the experiment.
2. Rebuild `standardDebug` and verify no Gallery/custom libs appear in the standard APK.
3. Keep `standardDebug` on `litertlm-android:0.11.0`.
4. Keep normal inference on GPU.
5. Do not reuse failed artifacts in `npuExperimentDebug` or `main`.

## Build Gate

Do not build until all of these are true:

- source/tag candidate selected
- Bazel/Bazelisk installed
- Android NDK configured
- QAIRT/QNN SDK headers/libs verified
- query/cquery target visibility confirmed
- output path is `artifacts/`, not app source
- user explicitly approves moving from investigation to build

## Query/Cquery Findings And Remaining Risks

Query/cquery completed successfully on 2026-05-16 using:

- Bazelisk `v1.29.0`
- Bazel `7.6.1`
- Android NDK r28c (`28.2.13676358`)
- LiteRT-LM `v0.11.0`
- LiteRT commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`

Target visibility is not the current blocker. The relevant targets are visible and resolve under `--config=android_arm64`.

Remaining risks:

- The LiteRT source expects QAIRT `2.44.0.260225`; the local environment has QAIRT `2.46.0.260424`. Query/cquery used an isolated overlay for graph analysis only.
- A successful cquery does not prove a later build artifact will be ABI-compatible with Gallery SM8750 or Maven `litertlm-android:0.11.0`.
- `bazel build` may expose additional compile/link incompatibilities that query/cquery cannot catch.
- QNN SDK license and redistribution status must be checked before shipping or sharing any built runtime.
- If a target later becomes non-visible or build-only constraints fail, do not patch upstream sources in-place. Record the failure and decide whether the source/tag choice is wrong.

No native artifact produced by a future build may be redistributed or staged into the app until licensing and ABI compatibility are explicitly reviewed.

## Limited Build Findings

Limited Android arm64 build was run on 2026-05-16 and wrote artifacts to:

```text
artifacts/litert_custom_build/20260516_232646/
```

Built:

- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`

Failed:

- `liblitertlm_jni.so`

The `litertlm_jni` failure is not a compiler error in JNI code. It is a link failure caused by an unresolved Git LFS prebuilt:

```text
libGemmaModelConstraintProvider.so:1: unknown directive: version
version https://git-lfs.github.com/spec/v1
```

This creates an important new risk: the available built dispatch/runtime pair is not a complete LiteRT-LM native stack. A dispatch-only test against Gallery or Maven JNI can still hit Java/native/runtime compatibility problems.

## QAIRT Overlay Build Risk

The source tree expects QAIRT `2.44.0.260225`, but the local build used an overlay pointing that expected path to QAIRT `2.46.0.260424`.

Consequences:

- build success does not prove ABI compatibility with SM8750 runtime
- build IDs differ from Gallery SM8750
- QNN capability negotiation may differ
- redistribution status remains unclear

## Next Runtime Test Gate

Before any built artifact is staged into an APK:

1. Prefer resolving Git LFS and producing a matched `liblitertlm_jni.so`.
2. If testing without matched JNI, stage only in an isolated debug flavor and document the mismatch.
3. Keep `Engine.initialize` explicit opt-in.
4. Never stage into `standardDebug`, release, or normal UI inference.
5. Verify APK leakage checks before install.

## Git LFS And Completed JNI Build Risks

Git LFS was resolved for LiteRT-LM Android arm64 prebuilts and `litertlm_jni` now builds successfully.

New facts:

- `liblitertlm_jni.so` is available from the same LiteRT-LM `v0.11.0` checkout.
- It is not stripped and is substantially larger than Gallery/Maven JNI.
- It has `NEEDED=libGemmaModelConstraintProvider.so`.
- The required `libGemmaModelConstraintProvider.so` comes from the resolved LFS prebuilt set, not from the Bazel target output.

Risks:

- staging built `liblitertlm_jni.so` without `libGemmaModelConstraintProvider.so` will fail at dynamic load time
- staging only built dispatch remains unsafe because JNI/LiteRT/dispatch must stay generation-matched
- Git LFS prebuilts may have licensing or redistribution constraints
- build success still does not prove SM8750 runtime compatibility
- QAIRT overlay uncertainty remains: source expects `2.44.0.260225`, local overlay points to `2.46.0.260424`

Current classification for custom build readiness:

```text
ready-for-isolated-insertion
```

The insertion target is now a new debug-only flavor:

```text
customBuildExperimentDebug / io.github.ninbyo02.lami.customnpu
```

Risks that remain even with the isolated flavor:

- the built stack uses QAIRT overlay `2.46.0.260424` for a source tree expecting `2.44.0.260225`
- built `liblitertlm_jni.so` depends on `libGemmaModelConstraintProvider.so`
- `libLiteRtCompilerPlugin_Qualcomm.so` is included, but runtime discovery and capability checks are still unproven
- no QNN SDK runtime libraries are copied by the custom staging script
- `Engine.initialize` may still SIGABRT or SIGSEGV, so stage files and tombstone collection remain mandatory

Rollback is limited to removing files under:

```text
app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/
```

No standard, npuExperiment, galleryStackExperiment, main, or release source set should be changed by this experiment.

```text
ready-for-isolated-insertion
```

This readiness applies only to a future explicit debug-only experiment. It does not apply to `standardDebug`, release, or normal GPU inference.

## Custom Build Experiment Runtime Result

The isolated custom stack was staged into:

```text
customBuildExperimentDebug / io.github.ninbyo02.lami.customnpu
```

Dry-run artifact:

```text
artifacts/npu_diagnostics/20260517_005032_customnpu/
```

The probe reached:

- `Backend.NPU(String)` success
- `EngineConfig` success
- `Engine(EngineConfig)` returned
- `Engine.initialize` invoking

Then the process terminated with `SIGABRT`. Tombstone/register evidence is consistent with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

This changes the custom build experiment classification from `ready-for-isolated-insertion` to:

```text
build-success-but-runtime-dispatch-unusable
```

Risk implication:

- the built same-source/tag stack is complete enough to load and reach dispatch delegate creation
- the failure is still below Java/Kotlin and before any generation
- the remaining risk is likely runtime/capability compatibility, QAIRT/QNN version coupling, or model/runtime schema support
- it is still unsafe to wire `Backend.NPU` into normal inference
- no `Conversation`, `Session`, `generateResponse`, token generation, or `selectedPath=npu` was used
