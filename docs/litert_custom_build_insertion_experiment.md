# LiteRT Custom Build Isolated Insertion Experiment

Date: 2026-05-17

## Scope

This document records the isolated insertion plan and implementation for `customBuildExperimentDebug`.

The goal is to stage the same-source/tag custom LiteRT-LM native stack into a new debug-only flavor and run only the explicit opt-in `Engine.initialize` dry-run probe.

Still forbidden:

- `Conversation`
- `Session`
- `generateResponse`
- token generation
- `selectedPath=npu`
- normal UI inference wiring
- staging built libraries into `standard`, `npuExperiment`, `galleryStackExperiment`, `main`, or release variants

## Flavor

| Item | Value |
| --- | --- |
| Flavor | `customBuildExperiment` |
| Variant | `customBuildExperimentDebug` |
| applicationId | `io.github.ninbyo02.lami.customnpu` |
| suffix | `.customnpu` |
| release variant | disabled |
| Java API dependency | `com.google.ai.edge.litertlm:litertlm-android:0.11.0` |
| source set | shares `src/npuExperimentDebug/java` and manifest |
| jniLibs path | `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/` |

Existing flavors stay unchanged:

- `standardDebug`: `litertlm-android:0.11.0`
- `npuExperimentDebug`: `litertlm-android:0.10.0`
- `galleryStackExperimentDebug`: `litertlm-android:0.11.0`

## Staged Native Stack

Source artifact:

```text
artifacts/litert_custom_build/20260516_235244/
```

Staged only by:

```bash
bash scripts/stage_litert_custom_build_stack_for_experiment.sh \
  artifacts/litert_custom_build/20260516_235244
```

Required files:

| Library | Expected Build ID | Expected SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` |
| `libLiteRtDispatch_Qualcomm.so` | `e999216e6d32c2f38702cd8538299e7d` | `2b999e1c56e87d0ae6c65d1613d4b8675cd998297d915d3e55bba248c9e1aefe` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | `310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `9053b81d7cbccdc3b5460c5e7395e293` | `9e1547a45fa31a63ef9fd77e79880f576487035c78d99eb1ecbfa85823d306cb` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` | `45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6` |

The staging script does not copy QNN SDK libraries and does not copy Gallery libraries.

## Probe

Run diagnostics without Engine initialization:

```bash
bash scripts/run_custom_build_stack_probe.sh \
  artifacts/litert_custom_build/20260516_235244
```

Run explicit Engine.initialize dry-run only:

```bash
bash scripts/run_custom_build_stack_probe.sh \
  artifacts/litert_custom_build/20260516_235244 \
  --engine-dry-run \
  --model-path /data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

The script installs only `customBuildExperimentDebug`, launches `NpuExperimentProbeActivity`, and calls the tombstone collector with:

```bash
bash scripts/collect_npu_tombstone_diagnostics.sh \
  --app-id io.github.ninbyo02.lami.customnpu \
  --label customnpu
```

## Result Classification

Use this classification for `Engine.initialize` dry-run:

- `initialize returned success`: engine init reached native success; next phase is isolated single-token smoke design, not generation in this phase.
- `SIGABRT no usable dispatch runtime`: built stack still cannot provide a usable dispatch runtime.
- `SIGSEGV / CheckJNI`: Java/native ABI mismatch remains.
- `UnsatisfiedLinkError / missing NEEDED`: missing `libGemmaModelConstraintProvider.so` or another dependency.
- `QNN path / ADSP / HTP error`: QNN runtime or skel/stub path issue.
- `model schema / unsupported model`: model/runtime mismatch.
- `unknown`: insufficient evidence.

## Engine.initialize Dry-Run Result

Run date: 2026-05-17

Artifact:

```text
artifacts/npu_diagnostics/20260517_005032_customnpu/
```

Run summary:

- runId: `1778946611930`
- model path: `/data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- model exists: `true`
- model length: `3016294400`
- model canRead: `true`
- dispatch present check: `true`
- `Backend.NPU(String)`: created successfully
- `EngineConfig`: created successfully with `EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)`
- `Engine(EngineConfig)`: constructor returned successfully
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- process after probe: `not-running`
- signal: `SIGABRT`
- explicit abort message line: `not-found`
- register fragments: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- top native frame: `liblitertlm_jni.so` / `DispatchDelegate::CreateDelegateKernelInterface()+312`
- classification: `no-usable-dispatch-runtime`
- confidence: `medium`

The same-source/tag custom native stack reached the same failure class as the earlier Gallery stack investigation. The result did not produce `SIGSEGV` or `CheckJNI`, and it did not return from `Engine.initialize`.

Still not executed:

- `Conversation`
- `Session`
- `generateResponse`
- token generation
- normal UI NPU path
- `selectedPath=npu`

## Safety

`customBuildExperimentDebug` is a separate app id. Normal `standardDebug` GPU inference and existing `npuExperimentDebug` / `galleryStackExperimentDebug` experiments are not modified by this insertion path.
