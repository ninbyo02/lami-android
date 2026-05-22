# QAIRT 2.44 NPU Dispatch Failure Root Cause Matrix

Date: 2026-05-21

Scope: coordinator synthesis of the tombstone/runtime mapping, Android QNN path
review, LiteRT source trace, CLI proof planning, model schema probe, the
2026-05-21 Android-native logcat dry-run, the JNI sentinel dry-run, the
2026-05-22 app-owned JNI logcat smoke, the 2026-05-22 native file logger
dry-run, the dlopen trace build, the dispatch symbol-resolution experiments,
the QNN provider trace dry-run, the QNN runtime alignment dry-run, the HTP
backend trace dry-run, and the QNN backend log callback dry-run.

## Current Failure Boundary

- flavor: `customBuildExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- runId: latest connected dry-run artifact
- diagnostic artifact: `artifacts/npu_diagnostics/20260522_225623_customnpu/`
- curated artifact: `artifacts/qairt244_htp_log_callback_dry_run/20260522_225623/`
- tombstone: latest collected tombstone in the diagnostic artifact
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- returned: no
- signal: `SIGABRT`
- reconstructed text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- immediate native boundary:
  `HtpBackend::Init -> QnnDevice_create -> libQnnHtpV79Stub.so cannot resolve libcdsprpc.so -> status=14001`

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

## QNN Provider Trace Update

Docs:

```text
docs/litert_qairt244_qnn_provider_trace_result.md
docs/litert_qairt244_qnn_dependency_chain.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_qnn_provider_trace_build/20260522_212620/
artifacts/qairt244_qnn_provider_trace_dry_run/20260522_212949/
artifacts/npu_diagnostics/20260522_212949_customnpu/
```

Result:

- `QnnSystemInterface_getProviders` returned `qnn_status=0`
- provider count was `1`
- selected provider was `SYSTEM_QTI_AISW`, backend ID `0`
- detected QNN System API version was `1.4.0`
- LiteRT expected QNN System API minimum was `1.8.0`
- `ResolveSystemApi` returned `kLiteRtStatusErrorDynamicLoading(502)` with
  `reason=system_minor actual=4 expected_min=8`
- `libQnnHtp.so`, `libQnnHtpPrepare.so`, and V79 stub/skel were not reached
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached

Interpretation: after fixing the missing dispatch `DT_NEEDED [libLiteRt.so]`
edge, the next concrete failure is a QNN System API version mismatch in
`libQnnSystem.so`. The failure occurs before HTP backend loading or capability
checking.

## QNN Runtime Alignment Update

Docs:

```text
docs/litert_qairt244_qnn_runtime_alignment_result.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_qnn_aligned_build/20260522_215238/
artifacts/qairt244_qnn_aligned_dry_run/20260522_215421/
artifacts/npu_diagnostics/20260522_215421_customnpu/
```

Result:

- customBuildExperimentDebug staged QAIRT 2.44 SDK `libQnnSystem.so`,
  `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and V79 skel
- QNN System API advanced from `1.4.0` to `1.8.0`
- `ResolveSystemApi` returned OK
- `libQnnHtp.so` dlopen succeeded
- HTP provider `HTP_QTI_AISW` was selected with core `2.33.0` and backend
  `5.44.0`
- `ResolveApi` returned OK
- `QnnManager::Init` now returns `kLiteRtStatusErrorRuntimeFailure(3)` with
  `reason=HtpBackendInit`
- `libQnnHtpPrepare.so`, V79 stub, V79 skel, and
  `LiteRtDispatchCheckRuntimeCompatibility` are still not reached

Interpretation: the QNN System generation mismatch was a real blocking issue
and is now resolved by QAIRT 2.44 runtime alignment. The current boundary is HTP
backend initialization, before prepare/stub/skel loading is visible and before
LiteRT dispatch compatibility checking.

## HTP Backend Trace Update

Docs:

```text
docs/litert_qairt244_htp_backend_trace_result.md
docs/litert_qairt244_htp_dependency_analysis.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_htp_backend_trace_aligned_build/20260522_222215/
artifacts/qairt244_htp_backend_trace_dry_run/20260522_222434/
artifacts/npu_diagnostics/20260522_222434_customnpu/
```

Result:

- `HtpBackend::Init` was reached
- `QnnBackend_create` succeeded and returned handle `0x1`
- `QnnDevice_getPlatformInfo` succeeded
- runtime SoC detection selected `SM8750`, DSP architecture `79`, and
  VTCM `8` MB
- `QnnDevice_create` failed
- raw `QnnDevice_create` status was `14001`
- `QNN_GET_ERROR_CODE(QnnDevice_create)` was `14001`
- `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and
  `libQnnHtpV79Skel.so` were still not mapped before abort
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached
- the top-level process still aborted at
  `DispatchDelegate::CreateDelegateKernelInterface`

Dependency analysis result:

- aligned build and APK-extracted QNN/HTP libraries are byte-identical
- `libQnnHtp.so` does not statically `DT_NEEDED` prepare/stub/skel; that path
  is runtime-loaded
- direct FastRPC dependency appears at
  `libQnnHtpV79Stub.so -> libcdsprpc.so`
- the device exposes SM8750/CDSP/FastRPC surface and vendor `libcdsprpc.so`
  under rootless read-only inspection

Interpretation: the current immediate failure is specifically
`HtpBackend::Init -> QnnDevice_create -> 14001`, before prepare/stub/skel are
visibly loaded and before LiteRT dispatch compatibility checking.

## QNN Backend Log Callback Update

Docs:

```text
docs/litert_qairt244_qnn_status_14001.md
docs/litert_qairt244_htp_log_callback_result.md
docs/litert_qairt244_htp_pd_fastrpc_config_analysis.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_htp_log_callback_aligned_build/20260522_224734/
artifacts/qairt244_htp_log_callback_dry_run/20260522_225623/
artifacts/npu_diagnostics/20260522_225623_customnpu/
```

Result:

- `14001` is `QNN_DEVICE_ERROR_INVALID_CONFIG`
- QNN backend log callback was captured with marker
  `qairt244_htp_log_callback_v1`
- `QnnDevice_create` attempted to load `libQnnHtpV79Stub.so`
- the absolute and basename stub load attempts both failed because
  `libcdsprpc.so` was not found in Android linker namespace `clns-9`
- QNN logged `loadRemoteSymbols failed with err 4000`
- QNN logged transport creation and skel load failures
- QNN then returned `QnnDevice_create` status `0x36b1` / `14001`
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached
- `Engine.initialize` did not return

Interpretation: the immediate blocker is no longer an opaque config mismatch.
It is Android app namespace resolution of the FastRPC host dependency
`libcdsprpc.so` needed by `libQnnHtpV79Stub.so`.

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
| H1. SM8750/V79 dispatch capability mismatch | `LiteRtDispatchCheckRuntimeCompatibility` is still not reached. Current failure is `HtpBackendInit` before compatibility logic. | low | Defer capability/schema hypotheses until HTP backend init succeeds. |
| H2. Android app nativeLibraryDir QNN/HTP search problem | `SetLitertDispatchLibDir` propagates the app native library directory correctly. Dispatch, `libQnnSystem.so`, and `libQnnHtp.so` now load from the custom APK. | low | Do not change generic nativeLibraryDir behavior; focus on HTP backend init detail. |
| H3. ADSP_LIBRARY_PATH / FastRPC / skel-stub path problem | Backend log shows `libQnnHtpV79Stub.so` load fails because `libcdsprpc.so` is not found in Android linker namespace `clns-9`. Device has vendor `libcdsprpc.so`, but app namespace cannot resolve it. | high | Decide whether to package/provide `libcdsprpc.so` for customBuildExperimentDebug or access vendor FastRPC through a supported namespace path. |
| H4. Qualcomm SM8750 model/runtime schema mismatch | Model directly carries QAIRT 2.44, SM8750, V79, and dispatch/QNN partition markers. That argues against a generic or wrong-SoC model, but context binary compatibility can still fail later. | low-medium | Defer deeper schema decode until dispatch API initialization logs show runtime accepted and invocation context creation is reached. |
| H5. Dispatch runtime registration / capability check failure | `DT_NEEDED [libLiteRt.so]` fixed the first dispatch dynamic-link failure. QAIRT 2.44 QNN runtime alignment fixed the System API mismatch. HTP trace reaches `QnnBackend_create` and fails at `QnnDevice_create` with status `14001`. Capability check is still not reached. | high | Treat dispatch registration as blocked by HTP device creation, not by dispatch API discovery. |
| H6. CLI litert_lm_main works while Android app fails | Not tested. Existing upstream CLI is unsafe because it creates a `Conversation` and sends a prompt. CLI could later isolate linker namespace and explicit `LD_LIBRARY_PATH`/`ADSP_LIBRARY_PATH`. | unknown | First create an initialize-only CLI target that cannot generate, then build/query Android arm64 with explicit SDK/NDK setup. |

## Ranked Next Moves

1. Investigate a customBuildExperimentDebug-only `libcdsprpc.so` visibility
   experiment or supported vendor namespace approach; do not touch
   `app/src/main/jniLibs`.
2. Preserve the backend log callback until the stub loads and the next boundary
   is known.
3. Keep QAIRT 2.44 QNN runtime alignment and the dispatch `DT_NEEDED
   [libLiteRt.so]` fix as required baseline conditions.
4. Implement the non-generating C++ initialize-only CLI target after Android app
   QNN provider initialization is understood.
   Do not execute upstream `litert_lm_main`.
5. Prepare an upstream issue update with the exact `QnnDevice_create=14001`
   boundary and the model's `v2.44.0.260225143659` marker.

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

The strongest current classification is HTP V79 stub/FastRPC dependency
resolution failure inside QNN backend initialization:

```text
HtpBackend::Init -> QnnDevice_create -> libQnnHtpV79Stub.so needs libcdsprpc.so
-> Android linker namespace clns-9 cannot resolve libcdsprpc.so -> status=14001
```

Earlier blockers are now understood and bypassed in the diagnostic baseline:

- dispatch needed a real `DT_NEEDED [libLiteRt.so]`
- packaged QNN System had to be aligned to QAIRT 2.44 so System API reached
  `1.8.0`
- `libQnnHtp.so` now loads and the HTP API resolves

The remaining leading branch is Android linker namespace / FastRPC host library
visibility for `libcdsprpc.so`. Device config is the public QNN error class,
but backend logs show the concrete failure is stub transport dependency
resolution before compatibility checking.

The model appears aligned with QAIRT 2.44 and SM8750/V79, so model generation
mismatch is not the lead hypothesis at this boundary.

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
