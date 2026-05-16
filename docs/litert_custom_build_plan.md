# LiteRT / LiteRT-LM Custom Build Plan

Date: 2026-05-16

This plan prepares a custom build path without building or installing any native artifact yet.

## Goal

Find or produce a Qualcomm dispatch/runtime stack that is generation-compatible with the LiteRT-LM Android Java/JNI/runtime stack used by Lami on SM8750.

The current failure is not a missing file. `libLiteRtDispatch_Qualcomm.so` is present and mapped, but `Engine.initialize()` aborts with evidence consistent with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

## Phase 0: No-Build Investigation

Status: in progress / mostly complete.

Tasks:

- Map source tags and commits.
- Inventory Bazel targets.
- Snapshot local environment readiness.
- Prepare issue report and artifact bundle.

Important prepared issue artifacts:

- Issue body: `docs/google_ai_edge_issue_body_litertlm_sm8750_npu.md`
- Light artifact bundle: `artifacts/npu_issue_bundle/20260516_213710_light.zip`

Official maintainer guidance is recommended before investing in a custom runtime stack, but this plan also prepares a safe local path if the user chooses to continue.

## Phase 1: Source Checkout Only

Allowed:

- Clone or update source repositories.
- Checkout candidate tags/commits.
- Inspect build files.
- Do not build.

Initial source candidates:

- `google-ai-edge/LiteRT-LM` tag `v0.11.0` (`c87189528a758db32ead241f4fc9c64836398ee7`)
- `google-ai-edge/LiteRT` commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` pinned by LiteRT-LM `v0.11.0`
- `google-ai-edge/gallery` tag `1.0.12` (`302f7e463b19f45f51825f4ec2fd30309366cb06`)

## Phase 2: Dry Build Query

Allowed only after Bazel/Bazelisk and Android NDK are configured:

- `bazel query`
- `bazel cquery`
- target visibility and dependency graph checks

No native artifact copy, no app integration.

Query first:

```bash
bazel query '@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so'
bazel query '@litert//litert/c:litert_runtime_c_api_so'
bazel query '@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so'
bazel query '//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni'
```

## Phase 3: Build Isolated Artifact

Only after source/tag confidence is acceptable.

Output location:

```text
artifacts/litert_custom_build/<timestamp>/
```

Do not install into the app automatically.

First build candidates:

1. `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so`
2. `@litert//litert/c:litert_runtime_c_api_so`
3. `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni`

If the issue is dispatch API layout mismatch, building dispatch alone may still fail. Prefer building enough of the matched stack for static comparison before any app test.

## Phase 4: Static Compare

Compare built artifacts against:

- Gallery SM8750 native stack.
- Maven `litertlm-android:0.11.0`.
- Current `galleryStackExperimentDebug` APK.

Required checks:

- SHA-256.
- GNU Build ID.
- `NEEDED`.
- SONAME.
- exported symbols.
- undefined symbols.
- `LiteRtDispatchGetApi`.
- `LiteRtDispatchCheckRuntimeCompatibility`.
- capability/version strings.
- QNN SDK version strings.

## Phase 5: Isolated galleryStackExperiment Replacement

Only after manual approval.

Allowed only in:

```text
app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a/
```

Forbidden:

- `app/src/main/jniLibs`
- `standardDebug`
- existing normal inference path

Validation remains:

- `Backend.NPU(String)` instantiate.
- `EngineConfig` dry-build.
- explicit opt-in `Engine.initialize` dry-run only.
- no `Conversation`.
- no `Session`.
- no `generateResponse`.

Updated direction after same-source/tag stack completion:

- use a new `customBuildExperimentDebug` flavor instead of replacing `galleryStackExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- stage the built stack as a unit:
  - `liblitertlm_jni.so`
  - `libLiteRt.so`
  - `libLiteRtDispatch_Qualcomm.so`
  - `libLiteRtCompilerPlugin_Qualcomm.so`
  - `libGemmaModelConstraintProvider.so`
- do not copy QNN SDK libraries from QAIRT
- run only explicit opt-in `Engine.initialize` dry-run

## Phase 6: If Engine.initialize Succeeds

Do not immediately wire NPU into the app.

Next phase would be designing a fully isolated single-token smoke test:

- separate applicationId/flavor
- explicit opt-in
- model path provided by intent/script
- no normal UI selectedPath change
- no held engine reuse

## Current Recommendation

Before building:

1. Ask maintainers which source tag/native artifact generation matches Gallery SM8750 and `litertlm-android:0.11.0`.
2. Install/configure Bazel/Bazelisk, Android NDK, and QAIRT/QNN SDK paths.
3. Run query/cquery only.
4. Build only into `artifacts/`, then static-compare before any app staging.

## Query/Cquery Phase Result

Result date: 2026-05-16

Artifact:

```text
artifacts/litert_custom_build_query/20260516_225450/
```

Environment now has:

- Bazelisk `v1.29.0`
- Bazel `7.6.1` selected by LiteRT-LM `.bazelversion`
- Android NDK `28.2.13676358` / r28c
- LiteRT-LM checkout `v0.11.0` at `/home/sato/project/litert-custom-build/LiteRT-LM`
- LiteRT checkout at pinned commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`

All targeted query/cquery commands succeeded:

```text
query_litertlm_jni                       0
query_qualcomm_dispatch                  0
query_litert_c                           0
query_qualcomm_compiler                  0
cquery_dispatch_android_arm64            0
cquery_litert_runtime_android_arm64      0
cquery_litertlm_jni_android_arm64        0
```

Visible and Android-arm64-queryable targets include:

- `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so`
- `@litert//litert/c:litert_runtime_c_api_so`
- `@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so`
- `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni`

The next phase can move to build only with explicit approval. Build output must go to an isolated `artifacts/litert_custom_build/<timestamp>/` directory, never directly into app source sets.

Recommended build order if approved:

1. `@litert//litert/c:litert_runtime_c_api_so`
2. `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so`
3. `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni`
4. static compare against Gallery SM8750 and Maven artifacts

Do not stage any result into `galleryStackExperimentDebug` until static compare is complete.

## Custom Build Experiment Phase

Implemented entry points:

- stage: `scripts/stage_litert_custom_build_stack_for_experiment.sh`
- run/probe: `scripts/run_custom_build_stack_probe.sh`
- docs: `docs/litert_custom_build_insertion_experiment.md`

Next decision depends on the dry-run classification:

- if initialize succeeds: design isolated single-token smoke test, still no normal UI NPU path
- if `No usable Dispatch runtime found` continues: investigate QAIRT version/model schema/dispatch capability
- if `UnsatisfiedLinkError`: add only the missing same-stack dependency after review
- if QNN/ADSP path appears: design path-specific experiment without touching standard flavor

Dry-run result on 2026-05-17:

- artifact: `artifacts/npu_diagnostics/20260517_005032_customnpu/`
- `Backend.NPU(String)`: success
- `EngineConfig`: success
- `Engine(EngineConfig)`: returned
- `Engine.initialize`: did not return
- signal: `SIGABRT`
- register fragments: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- classification: `no-usable-dispatch-runtime`
- confidence: `medium`

The next phase is not single-token smoke testing. The custom stack still fails before generation can be considered. Recommended next work:

1. Compare QAIRT/QNN version assumptions between the built stack, model, device runtime, and Gallery payload.
2. Inspect dispatch/runtime capability checks around `DispatchDelegate::CreateDelegateKernelInterface`.
3. Ask upstream maintainers whether additional QNN/HTP runtime setup or model/runtime pairing is required for SM8750.
4. Keep all further experiments in `customBuildExperimentDebug` or a new isolated flavor.

## QNN/QAIRT Coupling Static Pass

Result date: 2026-05-17

Artifact:

```text
artifacts/qairt_qnn_coupling/20260517_012057/
```

Detailed findings:

```text
docs/litert_qnn_qairt_coupling_findings.md
```

The static pass found that `customBuildExperimentDebug` packages QNN/HTP libraries, but those QNN libraries do not match either Gallery SM8750 Build IDs or the local QAIRT 2.46 Build IDs. The latest tombstone does not show the dispatch or QNN libraries mapped before abort; only `liblitertlm_jni.so`, `libGemmaModelConstraintProvider.so`, and `libllm_inference_engine_jni.so` are visible in the extracted map lines.

Next build-oriented options:

1. get exact QAIRT `2.44.0.260225`, rebuild the same limited targets, and static-compare;
2. identify a LiteRT source/ref that expects QAIRT `2.46.0.260424`, then query/build only into `artifacts/`;
3. prepare a separate, explicitly approved QNN-libs alignment experiment for `customBuildExperimentDebug`;
4. investigate a same-source `litert_lm_main` CLI path before any generation smoke test in Lami.

Do not proceed to single-token smoke until `Engine.initialize` returns successfully in an isolated flavor.

## Limited Build Phase Result

Result date: 2026-05-16

Artifact:

```text
artifacts/litert_custom_build/20260516_232646/
```

Limited build targets were executed only for the approved Android arm64 targets:

```text
@litert//litert/c:litert_runtime_c_api_so                         success
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so          success
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni          failed
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so    success
```

Built artifacts:

- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`

Not produced:

- `liblitertlm_jni.so`
- `libLiteRtRuntimeCApi.so`

`litertlm_jni` failed because `libGemmaModelConstraintProvider.so` in the source checkout is a Git LFS pointer rather than an ELF binary. This must be fixed before a source-matched LiteRT-LM JNI can be built.

Static comparison is recorded in:

- `docs/litert_custom_build_static_compare.md`
- `docs/litert_custom_build_results.md`

Next phase options:

1. Fetch/resolve the Git LFS prebuilt needed by `litertlm_jni`, then retry only the same `litertlm_jni` target.
2. If manual runtime testing is approved before JNI is available, test built `libLiteRt.so` and built `libLiteRtDispatch_Qualcomm.so` only in an isolated flavor. This is still risky because the JNI remains Gallery/Maven-derived.
3. Do not replace dispatch alone in `standardDebug`, `npuExperimentDebug`, or release.
4. Do not proceed to `Conversation` or `generateResponse` until an isolated `Engine.initialize` result is clean.

## JNI Build Completion Result

Result date: 2026-05-16

Artifacts:

```text
artifacts/litert_custom_build/20260516_235244/
artifacts/litert_custom_build_lfs/20260516_235237/
```

Git LFS was resolved only for LiteRT-LM Android arm64 prebuilts. After that, all limited Android arm64 targets succeeded:

```text
@litert//litert/c:litert_runtime_c_api_so                         success
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so          success
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni          success
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so    success
```

The same source/tag build now has:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`

Future isolated insertion must also include the resolved `libGemmaModelConstraintProvider.so`, because the built JNI declares it in `NEEDED`.

Next recommended phase, only after explicit approval:

1. create a one-shot isolated staging script for the built stack
2. stage only into a debug-only experimental flavor/applicationId
3. run detection and `Backend.NPU` instantiate first
4. run explicit opt-in `Engine.initialize` dry-run only
5. do not run `Conversation`, `Session`, or `generateResponse`
