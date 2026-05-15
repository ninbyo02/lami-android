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

## Backend.NPU attach dry-run

The next phase is a guarded attach dry-run. Its only purpose is to observe whether a
`Backend.NPU(String nativeLibraryDir)` object can be passed to an `EngineConfig` or
options builder setter by reflection.

The dry-run is enabled only when all of the following are true:

- `BuildConfig.DEBUG == true`
- `BuildConfig.CURRENT_FLAVOR == "npuExperiment"`
- `BuildConfig.NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED == true`
- the dispatch runtime is present in `context.applicationInfo.nativeLibraryDir`
- the `Backend.NPU(String)` instantiate-only probe succeeded

The probe searches builder candidates such as:

- `com.google.ai.edge.litertlm.EngineConfig`
- `com.google.ai.edge.litertlm.EngineConfig$Builder`
- `LlmInferenceOptions.Builder`
- `LlmInference.LlmInferenceOptions.Builder`

It then looks for backend setter candidates such as `setBackend(Backend)`,
`setPreferredBackend(Backend)`, `backend(Backend)`, and Object-compatible variants.
If a compatible setter is found, the probe invokes only that setter on the builder
object and records success or the exception/cause chain.

For safety, this phase does not call `build()`. The diagnostic reports:

```text
build invoked: no
build result: skipped-build-not-invoked-safety
```

The dry-run still does not create or use:

- `Engine`
- `Conversation`
- `Session`
- `LlmInference.createFromOptions`
- `LlmInference.createFromFile`
- `generateResponse`
- the Qualcomm model NPU path

The standard flavor remains skipped. A normal inference screen running the standard
flavor will show `current flavor: standard` and the NPU probes skipped; the
`npuExperimentDebug` probe activity/file is the place to inspect dispatch-present,
instantiate-success, and attach dry-run results.

## Backend.NPU API inventory phase

The attach dry-run showed that MediaPipe `LlmInferenceOptions.Builder.setPreferredBackend(...)`
is not the correct path for LiteRT-LM `Backend.NPU`. That setter appears to belong to
the MediaPipe preferred backend enum path (`DEFAULT`, `CPU`, `GPU`) and is not
assignable from the LiteRT-LM sealed `Backend.NPU` object.

The next diagnostic phase inventories the LiteRT-LM API surface directly:

- `com.google.ai.edge.litertlm.Backend`
- `Backend.NPU`, `Backend.GPU`, `Backend.CPU`
- `com.google.ai.edge.litertlm.EngineConfig`
- `com.google.ai.edge.litertlm.Engine`
- LiteRT-LM and MediaPipe `LlmInferenceOptions` classes

The diagnostic records class presence, constructors, methods, fields, static methods,
and assignability checks, including:

- `Backend` base class `<- Backend.NPU`
- `EngineConfig` backend constructor parameter `<- Backend.NPU`
- setter parameter type `<- Backend.NPU`

`EngineConfig` is the current likely connection point because official LiteRT-LM
Kotlin API shapes use:

```text
EngineConfig(modelPath: String, backend: Backend, ...)
```

This phase also performs a config-only dry-build, still only in `npuExperimentDebug`,
by constructing an `EngineConfig` with:

```text
modelPath = /dev/null/nonexistent.litertlm
backend = Backend.NPU(nativeLibraryDir)
```

The created `EngineConfig` object is discarded. It is not passed to `Engine`, and no
model load or inference is performed.

Still prohibited in this phase:

- `Engine` construction
- `Engine.initialize`
- `Conversation` construction
- `Session` construction
- `LlmInference.createFromOptions`
- `LlmInference.createFromFile`
- `generateResponse`
- Qualcomm model NPU execution

If `EngineConfig` NPU config-only dry-build succeeds with no native crash while
`selectedPath=gpu` and QNN/NPU attempt remains `no`, the next possible phase is an
isolated `Engine.initialize` dry-run. That phase has not been performed.

## Engine.initialize dry-run phase

`npuExperimentDebug` now has an isolated Engine initialization dry-run scaffold. It is
disabled by default and requires explicit intent opt-in:

```bash
adb shell am start \
  -n io.github.ninbyo02.lami.npu/io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity \
  --ez run_engine_initialize_dry_run true \
  --es model_path "/data/user/0/.../gemma-4-E2B-it_qualcomm_sm8750.litertlm"
```

The helper script wraps install, launch, and diagnostic collection:

```bash
bash scripts/run_npu_engine_initialize_dry_run.sh "/data/user/0/.../gemma-4-E2B-it_qualcomm_sm8750.litertlm"
```

The dry-run remains gated by all of these conditions:

- `BuildConfig.DEBUG == true`
- `BuildConfig.CURRENT_FLAVOR == "npuExperiment"`
- dispatch runtime present in `nativeLibraryDir`
- dispatch SHA-256 matches the staged Gallery SM8750 runtime
- `Backend.NPU(String)` instantiate-only probe succeeded
- `EngineConfig` NPU config-only dry-build succeeded
- model path is explicitly provided
- model filename is a Qualcomm SM8750 `.litertlm`
- explicit opt-in extra is true

The dry-run writes stage progress before dangerous calls to:

```text
files/npu_engine_initialize_dry_run.txt
```

Stages include:

- `started`
- `Backend.NPU creating`
- `Backend.NPU created`
- `EngineConfig creating`
- `EngineConfig created`
- `Engine initialize invoking`
- `Engine initialize returned`
- `close invoking`
- `close returned`
- `done`

This file is intentionally written incrementally so a native `SIGABRT` can still leave
the last reached stage behind.

Risks remain high because Gallery dispatch runtime and Lami's LiteRT-LM native stack
have mismatched build ids. Known failure modes include:

- native `SIGABRT`
- `No usable Dispatch runtime found`
- `Failed to initialize Dispatch API`
- dispatch API capability or version mismatch
- symbol mismatch
- QNN version mismatch

Still forbidden in this phase:

- `Conversation` construction
- `Session` construction
- `generateResponse`
- prompt evaluation or token generation
- wiring NPU into normal app inference
- `selectedPath=npu`
- changing held engine / official-flow behavior
- running in standard flavor

If Engine initialization succeeds, the next phase is only to design an isolated
single-token smoke test. That smoke test has not been implemented or run.

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
6. Run attach dry-run only in `npuExperimentDebug`; require setter success, no native crash, `selectedPath=gpu`, and QNN/NPU attempt `no`.
7. Inventory LiteRT-LM API and verify `EngineConfig` config-only dry-build without Engine initialization.
8. Run Engine.initialize dry-run only with explicit opt-in, staged diagnostics, and no Conversation/generation.
9. Only after ABI compatibility and dry-run evidence, consider a guarded `Backend.NPU` inference experiment with GPU fallback intact.

## Current recommendation

Do not use the Gallery SM8750 dispatch runtime in standard Lami builds.

If it is tested locally, do it only in `npuExperimentDebug`, with no automatic load and no NPU inference path enabled.
