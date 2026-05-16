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
