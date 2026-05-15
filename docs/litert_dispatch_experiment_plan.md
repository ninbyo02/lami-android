# LiteRT Qualcomm dispatch experiment plan

Date: 2026-05-15

## Current state

Lami's normal `gemma-4-E2B-it.litertlm` path runs on GPU and must remain unchanged.

The Qualcomm SM8750 model path is blocked before NPU enablement because the LiteRT Qualcomm dispatch runtime is missing from Lami:

- `Backend.NPU` is available.
- `Backend.NPU(String)` is available.
- External QAIRT / QNN DSP/HTP probing passes.
- V79 HTP capability is present.
- `dispatch API status` is missing.
- `selectedPath` remains `gpu`.
- QNN/NPU attempt remains `no`.

Google AI Edge Gallery `ai-edge-gallery-sm8750.apk` includes `libLiteRtDispatch_Qualcomm.so`, but the Gallery LiteRT native stack differs from Lami's current LiteRT-LM AAR native stack. Copying only the dispatch runtime is therefore risky.

## Why isolate the experiment

Dispatch runtime loading can fail through native aborts if the dispatch API layout, LiteRT API version, QNN version, or compiled model expectations do not match.

Known failure patterns include:

- `No usable Dispatch runtime found`
- `Failed to initialize Dispatch API`
- `Found Dispatch API with an unsupported version`
- `LiteRtDispatchGetApi` / dispatch API layout mismatch
- `LiteRtQualcommOptionsGet` mismatch
- QNN version mismatch
- `SIGABRT`

For that reason, dispatch experiments must not affect the standard debug build, release build, held engine reuse, or GPU fallback behavior.

## Isolated variant

The isolated variant is:

```text
npuExperimentDebug
```

It uses:

```text
app/src/npuExperimentDebug/jniLibs/arm64-v8a/
```

This directory starts empty except `.gitkeep`. It is the only source set where a Gallery dispatch runtime may be staged for detection-only experiments.

The variant is debug-only:

- `standardDebug`: normal debug path
- `standardRelease`: normal release path
- `npuExperimentDebug`: isolated experiment path
- `npuExperimentRelease`: disabled

## Detection-only staging

The next isolated step stages the Gallery SM8750 dispatch runtime only in:

```text
app/src/npuExperimentDebug/jniLibs/arm64-v8a/libLiteRtDispatch_Qualcomm.so
```

Source:

```text
ai-edge-gallery-sm8750.apk
lib/arm64-v8a/libLiteRtDispatch_Qualcomm.so
```

Expected identity:

```text
sha256: 92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777
build id: 643ad77b8ac2f54bd1b61e4133c77b3a
symbol: LiteRtDispatchGetApi@@VERS_1.0
```

This remains detection-only. The ABI mismatch risk remains high because Gallery's `libLiteRt.so` and `liblitertlm_jni.so` build ids differ from Lami's packaged versions.

The staged runtime must not be copied to:

- `app/src/main/jniLibs`
- `app/src/standardDebug`
- `app/src/standard`
- `app/src/release`

The staging helper is:

```bash
bash scripts/stage_gallery_dispatch_for_npu_experiment.sh /tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk
```

It verifies the expected SHA-256, build id, `libLiteRt.so` dependency, and `LiteRtDispatchGetApi` export before copying.

## Safety gates

The following remain prohibited until explicitly changed in a later phase:

- `System.loadLibrary("LiteRtDispatch_Qualcomm")`
- `Runtime.getRuntime().load(...)`
- `dlopen` or equivalent native loading
- automatic dispatch runtime loading
- connecting `Backend.NPU` to real inference
- `selectedPath=npu`
- QNN/NPU attempt `true`
- removing GPU fallback
- changing held engine / official-flow behavior
- adding dispatch runtime to `main`, `standard`, or `release`

## Diagnostic-only checks

The DEV diagnostics expose:

- current flavor
- applicationId
- nativeLibraryDir
- nativeLibraryDir exists
- dispatch runtime present in flavor
- dispatch runtime present in nativeLibraryDir
- dispatch runtime file path / length
- dispatch runtime source
- LiteRT build id
- `litertlm_jni` build id
- dispatch runtime build id
- dispatch runtime SHA-256
- expected SHA-256 match
- ABI compatibility: `unknown`, `likely-mismatch`, or `likely-compatible`
- load policy: `diagnostic-only; no System.loadLibrary; no Backend.NPU apply`

This diagnostic reads ELF build-id bytes from files in `context.applicationInfo.nativeLibraryDir`. It does not load the dispatch runtime.

## Instantiate-only probe

`npuExperimentDebug` also has a guarded instantiate-only probe for:

```text
Backend.NPU(String nativeLibraryDir)
```

This is controlled by:

```text
BuildConfig.CURRENT_FLAVOR == "npuExperiment"
BuildConfig.DEBUG == true
BuildConfig.NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED == true
dispatch runtime present in nativeLibraryDir
```

The probe passes `context.applicationInfo.nativeLibraryDir` to the constructor by reflection and records only the result, object class, and exception/cause chain. The created object is not passed to `EngineConfig`, `LlmInferenceOptions`, `Engine`, `Conversation`, or generation code.

The probe still does not call:

- `System.loadLibrary`
- `Runtime.getRuntime().load`
- `dlopen`
- `Engine.initialize`
- `generateResponse`

The expected path remains:

```text
selectedPath=gpu
QNN/NPU attempt=no
NPU apply status=disabled / probe-only
```

Use the install helper for device-side detection:

```bash
bash scripts/install_npu_experiment_and_check_dispatch.sh
```

The helper installs only `npuExperimentDebug`, verifies the APK contains the dispatch runtime, and captures `AcceleratorProbe` diagnostic logs when a device is connected.

## Safe sequence

1. Build the compatibility matrix with `scripts/compare_native_libs.sh`.
2. Keep the isolated `npuExperimentDebug` variant separate from standard/release.
3. Copy a candidate dispatch runtime only into `app/src/npuExperimentDebug/jniLibs/arm64-v8a/`.
4. Rebuild and confirm diagnostics only:
   - `dispatch API status: found`
   - `readiness: npu-prerequisites-present-probe-only`
   - QNN/NPU attempt remains `no`
   - `selectedPath` remains `gpu`
5. Require explicit opt-in before any runtime load experiment.
6. If a dry-run probe is added later, keep it separate from inference and guard it from native crash risk.
7. Only after ABI compatibility and dry-run evidence, consider a guarded `Backend.NPU` inference experiment with GPU fallback intact.

## Current recommendation

Do not use the Gallery SM8750 dispatch runtime in standard Lami builds.

If it is tested locally, do it only in `npuExperimentDebug`, with no automatic load and no NPU inference path enabled.
