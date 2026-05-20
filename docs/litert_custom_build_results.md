# LiteRT Custom Build Results

Date: 2026-05-16

## Scope

This was a limited native build pass for selected Android arm64 targets only. The build output was copied to `artifacts/` for static inspection. No app source set, APK packaging rule, runtime path, or device installation was changed.

Forbidden actions that were not performed:

- no app integration
- no `app/src/**/jniLibs` changes
- no `Engine.initialize` dry-run
- no NPU inference
- no `Conversation`, `Session`, or `generateResponse`
- no `selectedPath=npu`

## Source And Environment

| Item | Value |
| --- | --- |
| LiteRT-LM checkout | `/home/sato/project/litert-custom-build/LiteRT-LM` |
| LiteRT-LM commit | `c87189528a758db32ead241f4fc9c64836398ee7` (`v0.11.0`) |
| LiteRT pinned ref | `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` |
| Bazelisk | `/home/sato/.local/bin/bazelisk`, `v1.29.0` |
| Bazel | `7.6.1` |
| Android NDK | `/home/sato/Android/Sdk/ndk/28.2.13676358` / r28c |
| QAIRT local SDK | `/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424` |
| QAIRT overlay used | `/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225 -> 2.46.0.260424` |

The QAIRT overlay is an investigation workaround. It does not prove runtime ABI compatibility with the source tree, which expects QAIRT `2.44.0.260225`.

## Output

```text
artifacts/litert_custom_build/20260516_235244/
```

Important files:

- `build_results.tsv`
- `static_summary.md`
- `static_compare_matrix.tsv`
- `built_libs/`
- `metadata/`
- `symbols/`
- `strings/`
- `build_logs/`

## Target Results

| Target | Result | Notes |
| --- | --- | --- |
| `@litert//litert/c:litert_runtime_c_api_so` | success | Produced `libLiteRt.so`; no separate `libLiteRtRuntimeCApi.so` was found. |
| `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so` | success | Produced `libLiteRtDispatch_Qualcomm.so`. |
| `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni` | success | Succeeded after resolving Android arm64 Git LFS prebuilts. |
| `@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so` | success | Produced `libLiteRtCompilerPlugin_Qualcomm.so`. |

## Built Libraries

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` |
| `libLiteRtDispatch_Qualcomm.so` | `e999216e6d32c2f38702cd8538299e7d` | `2b999e1c56e87d0ae6c65d1613d4b8675cd998297d915d3e55bba248c9e1aefe` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | `310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `9053b81d7cbccdc3b5460c5e7395e293` | `9e1547a45fa31a63ef9fd77e79880f576487035c78d99eb1ecbfa85823d306cb` |

Not built:

- `libLiteRtRuntimeCApi.so`

## Git LFS Resolution

Before resolving Git LFS, `litertlm_jni` failed with:

```text
ld.lld: error: .../libGemmaModelConstraintProvider.so:1: unknown directive: version
>>> version https://git-lfs.github.com/spec/v1
```

Resolution:

- `git-lfs` was not installed system-wide.
- A temporary Git LFS binary was downloaded to `/tmp/git-lfs-3.4.1/git-lfs`.
- `git lfs install --local` was run only in the LiteRT-LM checkout.
- `git lfs pull --include='prebuilt/android_arm64/*' --exclude=''` resolved the Android arm64 prebuilt set.

Resolved file:

```text
prebuilt/android_arm64/libGemmaModelConstraintProvider.so
Build ID: f9e5e73e668032550042319e43012011
SHA-256: 45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6
```

LFS status artifacts:

```text
artifacts/litert_custom_build_lfs/20260516_235237/
```

## Suitability For Next Isolated Test

Classification:

```text
ready-for-isolated-insertion
```

This means the native stack is now complete enough to consider a separate, explicit isolated app test. It does not mean runtime success is expected.

Available same-source/tag candidates:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libGemmaModelConstraintProvider.so` from resolved LFS prebuilt

Remaining risks:

- built artifacts differ from Gallery SM8750 build IDs and sizes
- QAIRT 2.44 expected vs QAIRT 2.46 overlay used
- built `liblitertlm_jni.so` is not stripped and is much larger than Gallery/Maven JNI
- QNN/HTP runtime libraries are not built here and would still need a controlled source
- `libLiteRtRuntimeCApi.so` was not produced as a separate library

Potential next test, only after manual approval:

- isolate in a new one-shot staging path or `galleryStackExperimentDebug`
- stage the built stack together, not dispatch alone
- include `libGemmaModelConstraintProvider.so`
- do not replace only one component in `standardDebug` or `npuExperimentDebug`
- keep `Engine.initialize` explicit opt-in
- still no `Conversation` or `generateResponse`

## Isolated Insertion Phase

Status: implemented for `customBuildExperimentDebug`.

The custom stack is staged only through:

```bash
bash scripts/stage_litert_custom_build_stack_for_experiment.sh \
  artifacts/litert_custom_build/20260516_235244
```

Flavor details:

- flavor: `customBuildExperiment`
- variant: `customBuildExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- dependency: `litertlm-android:0.11.0`
- native path: `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/`
- release: disabled

The probe path remains explicit opt-in and calls only `Engine.initialize`; it does not create `Conversation`/`Session` and does not call `generateResponse`.

### Custom Build Experiment Dry-Run Result

Result date: 2026-05-17

Crash artifact:

```text
artifacts/npu_diagnostics/20260517_005032_customnpu/
```

Observed stages:

- model file exists: `true`
- model length: `3016294400`
- `Backend.NPU(String)`: success
- `EngineConfig`: success
- `Engine(EngineConfig)`: returned
- last stage: `Engine.initialize invoking method=Engine.initialize(): void`
- process after probe: `not-running`
- signal: `SIGABRT`
- likely abort text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- top native frame: `liblitertlm_jni.so` / `DispatchDelegate::CreateDelegateKernelInterface()+312`
- classification: `no-usable-dispatch-runtime`
- confidence: `medium`

Interpretation:

- The custom source-matched stack avoids the earlier Java/native descriptor `SIGSEGV` class.
- It still fails during dispatch delegate kernel creation.
- `Engine.initialize` did not return.
- No `Conversation`, `Session`, `generateResponse`, token generation, or normal UI NPU path was executed.

Current status:

```text
build-success-but-runtime-dispatch-unusable
```

The next investigation should focus on dispatch runtime usability, QAIRT/QNN version/capability expectations, model/runtime schema compatibility, or upstream guidance. The result does not justify wiring NPU into normal inference.

## QNN/QAIRT Coupling Static Analysis

Result date: 2026-05-17

Script:

```bash
bash scripts/analyze_qairt_qnn_coupling.sh
```

Artifact:

```text
artifacts/qairt_qnn_coupling/20260517_012057/
```

No build, install, app launch, `Engine.initialize`, `Conversation`, `Session`, `generateResponse`, or NPU inference was performed.

Key result:

- `customBuildExperimentDebug` packages the custom built LiteRT stack and QNN/HTP libraries.
- The packaged QNN/HTP libraries differ from both Gallery SM8750 QNN libraries and local QAIRT 2.46 libraries.
- The latest customnpu tombstone maps `liblitertlm_jni.so`, `libGemmaModelConstraintProvider.so`, and `libllm_inference_engine_jni.so`; it does not map `libLiteRtDispatch_Qualcomm.so` or QNN libraries in the extracted map lines.
- Dispatch libraries include QNN version mismatch strings and path-related strings for `ADSP_LIBRARY_PATH` / `LD_LIBRARY_PATH`.

Updated interpretation:

```text
runtime-dispatch-unusable; qnn-qairt-coupling-likely
```

The most likely next direction is not another Java/native ABI fix. It is QNN/QAIRT generation alignment or SM8750/V79 dispatch capability validation.

## QAIRT 2.44 Exact-Match Rebuild Check

Result dates: 2026-05-17, updated 2026-05-21

Search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

Exact QAIRT `2.44.0.260225` was not found locally. The matching overlay path is a symlink to QAIRT `2.46.0.260424`, so no exact-match rebuild was performed.

Update 2026-05-21:

- Exact QAIRT `2.44.0.260225` was obtained through QPM and installed at `/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`.
- The limited qairt244 rebuild succeeded and produced `artifacts/litert_custom_build/20260517_230448_qairt244/`.
- The qairt244 stack was staged and packaged only into `customBuildExperimentDebug`; the latest staging artifact is `artifacts/litert_custom_build_stage/20260521_015803/`.
- `customBuildExperimentDebug` packaging and install succeeded.
- First initialize-only dry-run attempt `runId=1779296283194` was skipped by the stale expected Build ID guard: `custom-stack-build-id-mismatch`.
- `Engine.initialize` was not invoked in that attempt. No `Conversation`, `Session`, `generateResponse`, `selectedPath=npu`, or normal UI NPU inference path was used.

Current qairt244 expected custom stack:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` |
| `libLiteRtDispatch_Qualcomm.so` | `a8006da3bd9b4fdf5b7131f8d864b6ee` | `00c26484621ab42bea6e3bee0d7e908451a428cf19cbd1ebfecf4ccee79e1739` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | `310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `443391d4c4348191230b67a3ab8a6037` | `c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` | `45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6` |

Previous 2.46-overlay custom stack values remain recorded above as the 2026-05-16 build output.

### QAIRT 2.44 Initialize-Only Dry-Run Result

Result date: 2026-05-21

- stage artifact: `artifacts/litert_custom_build_stage/20260521_074601/`
- runId: `1779317161924`
- diagnostics artifact: `artifacts/npu_diagnostics/20260521_074641_customnpu/`
- device tombstone: `/data/tombstones/tombstone_11`
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- `Engine.initialize` invoked: yes
- `Engine.initialize` returned: no
- signal: `SIGABRT`
- classification: `no-usable-dispatch-runtime`
- likely abort/register text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- `Conversation`, `Session`, `generateResponse`, `selectedPath=npu`, and normal UI NPU inference were not used.

Prepared acquisition and compare docs:

- `docs/qairt_244_acquisition_notes.md`
- `docs/litert_custom_build_qairt244_compare.md`

Prepared build command for after acquisition:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```
