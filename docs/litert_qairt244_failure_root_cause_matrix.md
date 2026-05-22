# QAIRT 2.44 NPU Dispatch Failure Root Cause Matrix

Date: 2026-05-21

Scope: coordinator synthesis of the tombstone/runtime mapping, Android QNN path
review, LiteRT source trace, CLI proof planning, model schema probe, the
2026-05-21 Android-native logcat dry-run, the JNI sentinel dry-run, the
2026-05-22 app-owned JNI logcat smoke, the 2026-05-22 native file logger
dry-run, the dlopen trace build, and the dispatch symbol-resolution
experiments.

## Current Failure Boundary

- flavor: `customBuildExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- runId: `1779317161924`
- diagnostic artifact: `artifacts/npu_diagnostics/20260521_074641_customnpu/`
- tombstone: `/data/tombstones/tombstone_11`
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- returned: no
- signal: `SIGABRT`
- reconstructed text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`

The dry-run stopped at the allowed initialize boundary. It did not create a
`Conversation` or `Session`, did not call `generateResponse`, did not wire
normal UI inference to NPU, and did not run a single-token smoke test.

## Android Logcat Dry-Run Update

Artifact:

```text
artifacts/qairt244_android_log_build/20260521_210911/
```

Dry-run diagnostics:

```text
artifacts/npu_diagnostics/20260521_211841_customnpu/
```

Result:

- executed exactly one `customBuildExperimentDebug` explicit
  `Engine.initialize` dry-run
- final stage remained `Engine.initialize invoking`
- tombstone top app frame:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone top app BuildId:
  `27bb6eaa5358f3c23f080cdd33023eac`
- no `QAIRT244_DIAG` or `qairt244_android_log_v1` lines were found in collected
  logcat/dropbox/tombstone artifacts
- `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, and QNN/HTP libraries were
  still not mapped in the tombstone

## JNI Sentinel Dry-Run Update

Artifact:

```text
artifacts/qairt244_jni_sentinel_build/20260521_214511/
```

Dry-run diagnostics:

```text
artifacts/npu_diagnostics/20260521_215004_customnpu/
```

Result:

- executed exactly one `customBuildExperimentDebug` explicit
  `Engine.initialize` dry-run
- final stage remained `Engine.initialize invoking`
- tombstone top app frame:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone app BuildId:
  `8faff14dc850b7fb1986a300ac465fa4`
- `nativeCreateEngine` is present in the tombstone:
  `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1992`
- no `QAIRT244_SENTINEL`, `qairt244_jni_entry_v1`, `QAIRT244_DIAG`, or
  `qairt244_android_log_v1` lines were found in collected artifacts
- `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, and QNN/HTP libraries were
  still not mapped in the tombstone

Interpretation: the JNI entry is reached, but app-native log lines are still not
visible in the current collected artifacts.

## App-Owned JNI Logcat Smoke Update

Artifact:

```text
artifacts/qairt244_app_jni_smoke/20260522_071945/
```

Result:

- `customBuildExperimentDebug` APK contains `liblami_qairt244_smoke.so`
- Activity extra `run_app_jni_smoke=true` executed only the smoke path
- no `Backend.NPU`, `Engine.initialize`, LiteRT engine, `Conversation`,
  `Session`, or generation path was used
- app-private `qairt244_app_jni_smoke.txt` contains the native marker
  `qairt244_app_jni_smoke_v1`
- `adb logcat -b all -d -v time` did not contain `QAIRT244_SMOKE` or the marker
- classification: `native-executed-logcat-missing`

Interpretation: the missing `QAIRT244_SENTINEL` / `QAIRT244_DIAG` evidence is
now primarily a logcat capture/visibility problem. The absence of those tags can
no longer be used to infer that the LiteRT-LM logging locations were not reached.

## Native File Logger Update

Build artifact:

```text
artifacts/qairt244_native_file_logger_build/20260522_074639/
```

Dry-run diagnostics:

```text
artifacts/npu_diagnostics/20260522_074944_customnpu/
```

Curated dry-run artifact:

```text
artifacts/qairt244_native_file_logger_dry_run/20260522_074944/
```

Result:

- app-private file logger created `qairt244_native_diag.txt`
- `nativeCreateEngine` was reached
- `ModelAssets::Create` succeeded
- backend enum conversion succeeded
- `EngineSettings::CreateDefault` succeeded
- `SetLitertDispatchLibDir` was called with the app native library directory
- `EngineFactory::CreateDefault` was entered and did not return
- `DispatchDelegate::Initialize` was reached
- `InitializeDispatchApi` was reached
- `LiteRtDispatchInitialize` failed with
  `kLiteRtStatusErrorDynamicLoading(502)`
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached in this log
- QNN manager / QNN `dlopen` was not reached in this log
- `CreateDelegateKernelInterface` then aborted because
  `has_dispatch_runtime_ == false`

Interpretation: the immediate failure is now known to be dispatch runtime
dynamic loading inside `LiteRtDispatchInitialize`, before compatibility checking
and before visible QNN/HTP/skel initialization.

## dlopen Trace Build Update

Build artifact:

```text
artifacts/qairt244_dlopen_trace_build/20260522_083658/
```

The lower-level dynamic loader trace was added with marker
`qairt244_dlopen_trace_v1` at:

```text
litert/runtime/dispatch/litert_dispatch.cc
litert/cc/internal/litert_shared_library.cc
```

It records dispatch library directory propagation, selected candidate path,
raw `dlopen`, raw `dlerror`, `dlsym("LiteRtDispatchGetApi")`, and the dispatch
API version if reached.

The connected-device dry-run was not executed because no adb device was
connected:

```text
List of devices attached
```

Attempt artifact:

```text
artifacts/qairt244_dlopen_trace_dry_run/20260522_083818_no_device/
```

That original no-device run was superseded by the connected-device symbol
resolution experiments below.

## Symbol Resolution Experiment Update

Docs:

```text
docs/litert_qairt244_symbol_resolution_experiment.md
```

RTLD_GLOBAL build and dry-run:

```text
artifacts/qairt244_rtld_global_build/20260522_210118/
artifacts/qairt244_rtld_global_dry_run/20260522_210355/
artifacts/npu_diagnostics/20260522_210355_customnpu/
```

Result:

- sibling `libLiteRt.so` preload with `RTLD_NOW | RTLD_GLOBAL` succeeded
- dispatch `dlopen` with `RTLD_NOW | RTLD_GLOBAL` still failed with unresolved
  `LiteRtGetEnvironmentOptions`
- `LiteRtDispatchGetApi`, compatibility check, and QNN loading were not reached

DT_NEEDED build and dry-run:

```text
artifacts/qairt244_dispatch_needed_build/20260522_210902/
artifacts/qairt244_dispatch_needed_dry_run/20260522_211136/
artifacts/npu_diagnostics/20260522_211136_customnpu/
```

Result:

- dispatch was rebuilt with `DT_NEEDED [libLiteRt.so]`
- dispatch `dlopen` succeeded
- `dlsym("LiteRtDispatchGetApi")` succeeded
- `LiteRtDispatchGetApi` returned API version `0.1.0`
- dispatch runtime version was accepted
- dispatch vendor initialization began
- `libQnnSystem.so` `dlopen` succeeded
- `dlsym("QnnSystemInterface_getProviders")` succeeded
- dispatch vendor initialization returned
  `kLiteRtStatusErrorDynamicLoading(502)`
- `LiteRtDispatchCheckRuntimeCompatibility` was still not reached
- `libQnnHtp.so`, `libQnnHtpPrepare.so`, and V79 stub/skel were still not mapped

Interpretation: the initial unresolved LiteRT symbol boundary is fixed by a real
`DT_NEEDED` edge from Qualcomm dispatch to `libLiteRt.so`. `RTLD_GLOBAL`
preload alone is not sufficient in this Android app path. The new immediate
boundary is Qualcomm dispatch/QNN System provider initialization, before HTP
backend and compatibility checking.

## Cross-Agent Findings

| Agent | Output | Key result |
| --- | --- | --- |
| A | `docs/litert_qairt244_tombstone_runtime_mapping.md` | `liblitertlm_jni.so` and `libGemmaModelConstraintProvider.so` were mapped; Qualcomm dispatch and QNN/HTP libraries were present in metadata but not mapped before abort. |
| B | `docs/litert_qairt244_android_qnn_path_analysis.md` | No adb device was connected for live rootless collection. Existing artifacts show app packaging is not a simple missing-file case, but app-process QNN/CDSP path state remains unrefreshed. |
| C | `docs/litert_dispatch_capability_source_trace.md` | The fatal is emitted when `InitializeDispatchApi()` fails and `has_dispatch_runtime_` becomes false; `.so` presence and `LiteRtDispatchGetApi` export are not sufficient. |
| D | `docs/litert_lm_main_npu_cli_proof_plan.md` | `//runtime/engine:litert_lm_main` exists, but upstream CLI creates a `Conversation` and sends a prompt, so it must not be executed for this task. |
| E | `docs/litert_qualcomm_sm8750_model_schema_probe.md` | Local model starts with `LITERTLM` and contains `DISPATCH_OP`, `qnn_partition_*`, `soc_type=SM8750`, `min_arch=79`, and QAIRT `v2.44.0.260225143659` markers. |

## Hypothesis Matrix

| Hypothesis | Evidence | Confidence | Next action |
| --- | --- | --- | --- |
| H1. SM8750/V79 dispatch capability mismatch | File logger shows `LiteRtDispatchInitialize` fails with dynamic-loading status before `LiteRtDispatchCheckRuntimeCompatibility`; model still declares `soc_type=SM8750` and `min_arch=79`. | low-medium | Defer capability/schema hypotheses until lower-level dispatch loading succeeds or reaches compatibility checking. |
| H2. Android app nativeLibraryDir QNN/HTP search problem | `SetLitertDispatchLibDir` propagates the app native library directory correctly. With `DT_NEEDED [libLiteRt.so]`, dispatch `dlopen` succeeds and `libQnnSystem.so` loads from the app namespace. HTP/Prepare/V79 libs are still not mapped before failure. | medium-high | Add focused file logging around Qualcomm dispatch vendor initialization after `QnnSystemInterface_getProviders`, including provider enumeration and backend library selection. |
| H3. ADSP_LIBRARY_PATH / FastRPC / skel-stub path problem | V79 stub depends on `libcdsprpc.so`; source mutates `ADSP_LIBRARY_PATH`; tombstones contain `vendor_adsprpc_prop`. The NEEDED experiment reaches QNN System, but does not yet map HTP/Prepare/V79 libs, so ADSP/FastRPC remains a later-stage possibility rather than the current proven boundary. | medium-low | Defer ADSP/skel path changes until QNN provider/backend selection logs prove HTP load is attempted. |
| H4. Qualcomm SM8750 model/runtime schema mismatch | Model directly carries QAIRT 2.44, SM8750, V79, and dispatch/QNN partition markers. That argues against a generic or wrong-SoC model, but context binary compatibility can still fail later. | low-medium | Defer deeper schema decode until dispatch API initialization logs show runtime accepted and invocation context creation is reached. |
| H5. Dispatch runtime registration / capability check failure | `RTLD_GLOBAL` preload did not solve unresolved `LiteRtGetEnvironmentOptions`; adding `DT_NEEDED [libLiteRt.so]` did. Dispatch API lookup then succeeds and QNN System provider symbol lookup succeeds, but dispatch vendor initialization still returns `kLiteRtStatusErrorDynamicLoading(502)`. | high | Treat missing `DT_NEEDED [libLiteRt.so]` as confirmed first failure. Next instrument Qualcomm dispatch/QNN manager immediately after QNN System provider lookup. |
| H6. CLI litert_lm_main works while Android app fails | Not tested. Existing upstream CLI is unsafe because it creates a `Conversation` and sends a prompt. CLI could later isolate linker namespace and explicit `LD_LIBRARY_PATH`/`ADSP_LIBRARY_PATH`. | unknown | First create an initialize-only CLI target that cannot generate, then build/query Android arm64 with explicit SDK/NDK setup. |

## Ranked Next Moves

1. Add focused app-private file logging inside Qualcomm dispatch/QNN manager
   after `QnnSystemInterface_getProviders`, including provider count/API choice,
   QNN System init status, backend candidate names, and the next returned
   status.
2. Keep the `DT_NEEDED [libLiteRt.so]` experiment as the active custom build
   baseline for further diagnosis, because it fixes the first dynamic linker
   failure.
3. Design an isolated ADSP/QNN path dry-run only after logs prove HTP/skel load
   is attempted.
4. Implement the non-generating C++ initialize-only CLI target after Android app
   QNN provider initialization is understood.
   Do not execute upstream `litert_lm_main`.
5. Prepare an upstream issue update with the exact QAIRT 2.44 rebuild result and
   the model's `v2.44.0.260225143659` marker.

## Dispatch Logging Update

Result date: 2026-05-21

Artifact:

```text
artifacts/qairt244_dispatch_logging_build/20260521_085251/
```

The logging build adds `QAIRT244_DIAG` lines for dispatch library discovery,
dispatch `dlopen`/`dlsym`, `LiteRtDispatchGetApi`,
`LiteRtDispatchCheckRuntimeCompatibility`, Qualcomm `QnnManager::Create`,
QNN library loading, `ADSP_LIBRARY_PATH`, HTP init, device context creation, and
`has_dispatch_runtime` transitions.

The dry-run was not executed because no adb device was connected, so H1-H6
confidence does not change yet. The next evidence-producing step is the single
allowed connected-device dry-run with this artifact.

## Most Likely Cause

The strongest current classification is dispatch runtime dynamic-loading
failure inside `LiteRtDispatchInitialize`, before a usable dispatch runtime is
registered for delegate kernel creation.

The most likely underlying cause is Android app namespace/path state preventing
the dispatch runtime or one of its direct dependencies from being opened. The
current native file log does not yet reach compatibility checking or visible
QNN/HTP manager initialization, so QNN/HTP/FastRPC remains a second-order branch
rather than the immediate observed failure.

The model appears aligned with QAIRT 2.44 and SM8750/V79, so model generation
mismatch is no longer the lead hypothesis unless later logs show context binary
loading is actually reached.

## No-Run Confirmation

This coordinator pass did not run:

- `generateResponse`
- prompt generation
- `Conversation` creation
- `Session` creation
- `selectedPath=npu`
- normal UI `Backend.NPU` wiring
- single-token smoke test
- more than the single allowed native file logger `Engine.initialize` dry-run
- unsafe native library replacement
